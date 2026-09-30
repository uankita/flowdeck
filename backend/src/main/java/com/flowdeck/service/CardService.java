package com.flowdeck.service;

import com.flowdeck.domain.Board;
import com.flowdeck.domain.BoardList;
import com.flowdeck.domain.Card;
import com.flowdeck.repository.BoardListRepository;
import com.flowdeck.repository.CardRepository;
import com.flowdeck.util.RankExhaustionException;
import com.flowdeck.util.RankGenerator;
import com.flowdeck.web.dto.BoardDtos.CardResponse;
import com.flowdeck.web.dto.CardDtos.MoveCardRequest;
import com.flowdeck.web.mapper.BoardMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardRepository cardRepository;
    private final BoardListRepository boardListRepository;
    private final BoardMapper boardMapper;

    /**
     * Moves {@code cardId} into {@code request.targetListId()}, landing it
     * between whichever two cards {@code request.previousCardId()} and
     * {@code request.nextCardId()} identify (either or both may be
     * {@code null} — see {@link MoveCardRequest}'s Javadoc).
     *
     * <p>Writes exactly one row: the moved card's. {@code targetListId} is
     * set even when it's unchanged from the card's current list (a same-list
     * reorder), and the target {@code BoardList} itself is only ever read,
     * never saved — its own {@code @Version} is untouched by a move. The one
     * exception is {@link #rebalance}, which runs — still inside this same
     * transaction — only on the rare path where {@link RankGenerator#between}
     * has no room left between the two neighbours; that legitimately rewrites
     * every card in the list, atomically with the move that triggered it.
     *
     * @throws CardNotFoundException if {@code cardId}, {@code previousCardId},
     *     or {@code nextCardId} doesn't identify a card in this workspace
     * @throws BoardListNotFoundException if {@code targetListId} doesn't
     *     identify a list in this workspace
     * @throws InvalidCardMoveException if a neighbour is the card being
     *     moved, or doesn't belong to {@code targetListId}
     */
    @Transactional
    public CardResponse moveCard(UUID workspaceId, UUID cardId, MoveCardRequest request) {
        if (cardId.equals(request.previousCardId()) || cardId.equals(request.nextCardId())) {
            throw new InvalidCardMoveException("A card cannot be moved relative to itself");
        }

        Card card = loadCardInWorkspace(cardId, workspaceId);
        BoardList targetList = loadListInWorkspace(request.targetListId(), workspaceId);

        String newRank;
        try {
            newRank =
                    RankGenerator.between(
                            resolveNeighborRank(request.previousCardId(), targetList.getId()),
                            resolveNeighborRank(request.nextCardId(), targetList.getId()));
        } catch (RankExhaustionException e) {
            log.info(
                    "Rank space exhausted in list {} while moving card {} — rebalancing",
                    targetList.getId(),
                    cardId);
            rebalance(targetList.getId());
            // The neighbours' ranks changed under us — re-resolve them.
            // Same persistence context, so this re-reads the just-mutated,
            // still-managed Card instances rather than stale values.
            newRank =
                    RankGenerator.between(
                            resolveNeighborRank(request.previousCardId(), targetList.getId()),
                            resolveNeighborRank(request.nextCardId(), targetList.getId()));
        }

        card.setList(targetList);
        card.setRank(newRank);
        // Dirty checking would flush this at commit regardless; save() here
        // is for a clear "this is the write" reading and to hand back the
        // definitive persisted state. Either way, exactly one UPDATE.
        Card moved = cardRepository.save(card);
        return boardMapper.toCardResponse(moved);
    }

    private Card loadCardInWorkspace(UUID cardId, UUID workspaceId) {
        Card card = cardRepository.findById(cardId).orElseThrow(() -> new CardNotFoundException(cardId));
        if (!belongsToWorkspace(card.getList().getBoard(), workspaceId)) {
            // A card outside the caller's workspace is indistinguishable
            // from one that doesn't exist — same reasoning as
            // BoardController's 403-not-404 handling of an unknown
            // workspace: don't let the response confirm the card exists
            // somewhere the caller has no access to.
            throw new CardNotFoundException(cardId);
        }
        return card;
    }

    private BoardList loadListInWorkspace(UUID listId, UUID workspaceId) {
        BoardList list =
                boardListRepository.findById(listId).orElseThrow(() -> new BoardListNotFoundException(listId));
        if (!belongsToWorkspace(list.getBoard(), workspaceId)) {
            throw new BoardListNotFoundException(listId);
        }
        return list;
    }

    private boolean belongsToWorkspace(Board board, UUID workspaceId) {
        return board.getWorkspace().getId().equals(workspaceId);
    }

    private String resolveNeighborRank(UUID neighborCardId, UUID targetListId) {
        if (neighborCardId == null) {
            return null;
        }
        Card neighbor =
                cardRepository.findById(neighborCardId).orElseThrow(() -> new CardNotFoundException(neighborCardId));
        if (!neighbor.getList().getId().equals(targetListId)) {
            throw new InvalidCardMoveException(
                    "Card '%s' is not in the target list".formatted(neighborCardId));
        }
        return neighbor.getRank();
    }

    /**
     * Rewrites every live card in a list to short, evenly-spaced ranks. The
     * one operation in this class that legitimately touches every row in a
     * list rather than just the moved card's — reached only from the
     * {@link RankExhaustionException} branch of {@link #moveCard}, in the
     * same transaction as the move that triggered it, so a rebalance and the
     * move it made room for commit — or fail — together.
     */
    private void rebalance(UUID listId) {
        List<Card> cards = cardRepository.findByListIdOrderByRankAsc(listId);
        List<String> freshRanks = RankGenerator.spacedRanks(cards.size());
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setRank(freshRanks.get(i));
        }
        // cards are managed entities loaded in this same @Transactional
        // method; mutating them is enough for dirty checking to flush the
        // updates at commit — no explicit saveAll() needed.
    }
}
