package com.flowdeck.service;

import com.flowdeck.domain.Board;
import com.flowdeck.domain.BoardList;
import com.flowdeck.domain.Card;
import com.flowdeck.domain.CardLabel;
import com.flowdeck.domain.Label;
import com.flowdeck.repository.BoardListRepository;
import com.flowdeck.repository.CardLabelRepository;
import com.flowdeck.repository.CardRepository;
import com.flowdeck.repository.LabelRepository;
import com.flowdeck.util.RankExhaustionException;
import com.flowdeck.util.RankGenerator;
import com.flowdeck.web.dto.BoardDtos.CardResponse;
import com.flowdeck.web.dto.CardDtos.CreateCardRequest;
import com.flowdeck.web.dto.CardDtos.MoveCardRequest;
import com.flowdeck.web.dto.CardDtos.UpdateCardRequest;
import com.flowdeck.web.dto.LabelDtos.LabelResponse;
import com.flowdeck.web.dto.PageResponse;
import com.flowdeck.web.mapper.BoardMapper;
import com.flowdeck.web.mapper.LabelMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardRepository cardRepository;
    private final BoardListRepository boardListRepository;
    private final LabelRepository labelRepository;
    private final CardLabelRepository cardLabelRepository;
    private final BoardMapper boardMapper;
    private final LabelMapper labelMapper;

    @PersistenceContext private EntityManager entityManager;

    @Transactional
    public CardResponse createCard(UUID workspaceId, UUID listId, CreateCardRequest request) {
        BoardList list = loadListInWorkspace(listId, workspaceId);
        String lastRank =
                cardRepository.findFirstByListIdOrderByRankDesc(listId).map(Card::getRank).orElse(null);

        Card card = new Card();
        card.setList(list);
        card.setTitle(request.title());
        card.setDescription(request.description());
        if (request.priority() != null) {
            card.setPriority(request.priority());
        }
        card.setDueAt(request.dueAt());
        card.setRank(RankGenerator.between(lastRank, null));

        Card saved = cardRepository.save(card);
        log.info("Created card {} ({}) in list {}", saved.getTitle(), saved.getId(), listId);
        return boardMapper.toCardResponse(saved);
    }

    @Transactional(readOnly = true)
    public CardResponse getCard(UUID workspaceId, UUID cardId) {
        return boardMapper.toCardResponse(loadCardInWorkspace(cardId, workspaceId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CardResponse> listCardsForList(UUID workspaceId, UUID listId, Pageable pageable) {
        loadListInWorkspace(listId, workspaceId); // 404/membership check even for an empty result
        Page<CardResponse> page = cardRepository.findByListId(listId, pageable).map(boardMapper::toCardResponse);
        return PageResponse.of(page);
    }

    /**
     * Full-replace update of a card's editable fields, guarded by optimistic
     * locking: {@code request.version()} must match the card's current
     * version.
     *
     * <p>Checked twice, for two different race windows. First, explicitly,
     * against the freshly-loaded card, before touching anything — this is
     * what makes the common case (two sequential edits, the second one based
     * on stale data) a clean, immediate 409. Second, implicitly, by
     * {@code @Version} itself at flush time via {@link #saveCardOrThrowConflict}
     * — the backstop for a genuine race, where two requests both pass the
     * first check because they both read the same version before either
     * wrote. Either path throws the same {@link CardVersionConflictException},
     * carrying the card's current state.
     */
    @Transactional
    public CardResponse updateCard(UUID workspaceId, UUID cardId, UpdateCardRequest request) {
        Card card = loadCardInWorkspace(cardId, workspaceId);
        if (card.getVersion() != request.version()) {
            throw new CardVersionConflictException(boardMapper.toCardResponse(card));
        }

        card.setTitle(request.title());
        card.setDescription(request.description());
        card.setPriority(request.priority());
        card.setDueAt(request.dueAt());

        return boardMapper.toCardResponse(saveCardOrThrowConflict(card));
    }

    @Transactional
    public void deleteCard(UUID workspaceId, UUID cardId) {
        Card card = loadCardInWorkspace(cardId, workspaceId);
        card.setDeletedAt(Instant.now());
        cardRepository.save(card);
        log.info("Soft-deleted card {}", cardId);
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> listLabelsForCard(UUID workspaceId, UUID cardId) {
        loadCardInWorkspace(cardId, workspaceId);
        return cardLabelRepository.findByCardId(cardId).stream()
                .map(CardLabel::getLabel)
                .map(labelMapper::toResponse)
                .toList();
    }

    /** Idempotent: attaching an already-attached label just succeeds again, no error. */
    @Transactional
    public void attachLabel(UUID workspaceId, UUID cardId, UUID labelId) {
        Card card = loadCardInWorkspace(cardId, workspaceId);
        Label label = loadLabelInWorkspace(labelId, workspaceId);
        if (!cardLabelRepository.existsByCardIdAndLabelId(cardId, labelId)) {
            CardLabel cardLabel = new CardLabel();
            cardLabel.setCard(card);
            cardLabel.setLabel(label);
            cardLabelRepository.save(cardLabel);
        }
    }

    /** Idempotent: detaching a label that isn't attached just succeeds again, no error. */
    @Transactional
    public void detachLabel(UUID workspaceId, UUID cardId, UUID labelId) {
        loadCardInWorkspace(cardId, workspaceId); // 404/membership check
        cardLabelRepository.deleteByCardIdAndLabelId(cardId, labelId);
    }

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
     * @throws CardVersionConflictException if this card was concurrently
     *     modified between being loaded and this move committing
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
        return boardMapper.toCardResponse(saveCardOrThrowConflict(card));
    }

    /**
     * Flushes the write immediately (rather than deferring to end-of-transaction
     * commit) specifically so a genuine {@code @Version} conflict throws
     * {@code here}, inside this method's try/catch, instead of later at
     * commit time — outside any catch block this class controls, where it
     * would surface as a raw, unmapped 500 instead of a clean 409.
     */
    private Card saveCardOrThrowConflict(Card card) {
        try {
            return cardRepository.saveAndFlush(card);
        } catch (ObjectOptimisticLockingFailureException e) {
            // NOT cardRepository.findById(card.getId()): `card` is already
            // managed in this transaction's persistence context, so findById
            // would just return this SAME instance from the first-level
            // cache — still carrying our failed, unflushed edit, not the
            // database's true current row. refresh() re-syncs it in place.
            entityManager.refresh(card);
            throw new CardVersionConflictException(boardMapper.toCardResponse(card));
        } catch (DataIntegrityViolationException e) {
            // Only reachable from moveCard, via uq_cards_board_list_rank: the
            // caller's previous/next neighbours didn't reflect the list's
            // true current adjacency — e.g. a third card already sits
            // between them that the caller's view didn't know about — so
            // the computed rank collided with an existing one.
            throw new InvalidCardMoveException(
                    "The requested position conflicts with another card — your view of this list may be out of date");
        }
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

    private Label loadLabelInWorkspace(UUID labelId, UUID workspaceId) {
        Label label = labelRepository.findById(labelId).orElseThrow(() -> new LabelNotFoundException(labelId));
        if (!belongsToWorkspace(label.getBoard(), workspaceId)) {
            throw new LabelNotFoundException(labelId);
        }
        return label;
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
