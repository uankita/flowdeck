package com.flowdeck.service;

import com.flowdeck.domain.Board;
import com.flowdeck.domain.BoardList;
import com.flowdeck.repository.BoardListRepository;
import com.flowdeck.repository.BoardRepository;
import com.flowdeck.util.RankExhaustionException;
import com.flowdeck.util.RankGenerator;
import com.flowdeck.web.dto.BoardDtos.BoardListResponse;
import com.flowdeck.web.dto.BoardListDtos.CreateBoardListRequest;
import com.flowdeck.web.dto.BoardListDtos.MoveBoardListRequest;
import com.flowdeck.web.dto.BoardListDtos.UpdateBoardListRequest;
import com.flowdeck.web.mapper.BoardMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors {@code CardService}'s create/update/move patterns one level up the hierarchy. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BoardListService {

    private final BoardListRepository boardListRepository;
    private final BoardRepository boardRepository;
    private final BoardMapper boardMapper;

    @PersistenceContext private EntityManager entityManager;

    @Transactional
    public BoardListResponse createList(UUID workspaceId, String boardKey, CreateBoardListRequest request) {
        Board board = loadBoardInWorkspace(workspaceId, boardKey);
        String lastRank =
                boardListRepository.findFirstByBoardIdOrderByRankDesc(board.getId()).map(BoardList::getRank).orElse(null);

        BoardList list = new BoardList();
        list.setBoard(board);
        list.setName(request.name());
        list.setWipLimit(request.wipLimit());
        list.setRank(RankGenerator.between(lastRank, null));

        BoardList saved = boardListRepository.save(list);
        log.info("Created list {} ({}) on board {}", saved.getName(), saved.getId(), boardKey);
        return boardMapper.toListResponse(saved);
    }

    /** Same optimistic-locking contract as {@code CardService#updateCard} — see that Javadoc. */
    @Transactional
    public BoardListResponse updateList(
            UUID workspaceId, String boardKey, UUID listId, UpdateBoardListRequest request) {
        BoardList list = loadListInBoard(workspaceId, boardKey, listId);
        if (list.getVersion() != request.version()) {
            throw new BoardListVersionConflictException(boardMapper.toListResponse(list));
        }

        list.setName(request.name());
        list.setWipLimit(request.wipLimit());
        return boardMapper.toListResponse(saveListOrThrowConflict(list));
    }

    /** Hard delete — no soft-delete on lists (see the migration); cascades to its cards. */
    @Transactional
    public void deleteList(UUID workspaceId, String boardKey, UUID listId) {
        BoardList list = loadListInBoard(workspaceId, boardKey, listId);
        boardListRepository.delete(list);
        log.info("Deleted list {} from board {}", listId, boardKey);
    }

    @Transactional
    public BoardListResponse moveList(
            UUID workspaceId, String boardKey, UUID listId, MoveBoardListRequest request) {
        if (listId.equals(request.previousListId()) || listId.equals(request.nextListId())) {
            throw new InvalidListMoveException("A list cannot be moved relative to itself");
        }

        BoardList list = loadListInBoard(workspaceId, boardKey, listId);
        UUID boardId = list.getBoard().getId();

        String newRank;
        try {
            newRank =
                    RankGenerator.between(
                            resolveNeighborRank(request.previousListId(), boardId),
                            resolveNeighborRank(request.nextListId(), boardId));
        } catch (RankExhaustionException e) {
            log.info("Rank space exhausted on board {} while moving list {} — rebalancing", boardId, listId);
            rebalance(boardId);
            newRank =
                    RankGenerator.between(
                            resolveNeighborRank(request.previousListId(), boardId),
                            resolveNeighborRank(request.nextListId(), boardId));
        }

        list.setRank(newRank);
        return boardMapper.toListResponse(saveListOrThrowConflict(list));
    }

    /** Same rationale — including the {@code entityManager.refresh}, not {@code findById} — as {@code CardService#saveCardOrThrowConflict}. */
    private BoardList saveListOrThrowConflict(BoardList list) {
        try {
            return boardListRepository.saveAndFlush(list);
        } catch (ObjectOptimisticLockingFailureException e) {
            entityManager.refresh(list);
            throw new BoardListVersionConflictException(boardMapper.toListResponse(list));
        } catch (DataIntegrityViolationException e) {
            // Same reasoning as CardService's equivalent catch, for
            // uq_board_lists_board_rank instead.
            throw new InvalidListMoveException(
                    "The requested position conflicts with another list — your view of this board may be out of date");
        }
    }

    private Board loadBoardInWorkspace(UUID workspaceId, String boardKey) {
        return boardRepository
                .findByWorkspaceIdAndBoardKey(workspaceId, boardKey)
                .orElseThrow(() -> new BoardNotFoundException(boardKey));
    }

    private BoardList loadListInBoard(UUID workspaceId, String boardKey, UUID listId) {
        BoardList list =
                boardListRepository.findById(listId).orElseThrow(() -> new BoardListNotFoundException(listId));
        Board board = list.getBoard();
        if (!board.getWorkspace().getId().equals(workspaceId) || !board.getBoardKey().equals(boardKey)) {
            throw new BoardListNotFoundException(listId);
        }
        return list;
    }

    private String resolveNeighborRank(UUID neighborListId, UUID boardId) {
        if (neighborListId == null) {
            return null;
        }
        BoardList neighbor =
                boardListRepository.findById(neighborListId).orElseThrow(() -> new BoardListNotFoundException(neighborListId));
        if (!neighbor.getBoard().getId().equals(boardId)) {
            throw new InvalidListMoveException("List '%s' is not on the same board".formatted(neighborListId));
        }
        return neighbor.getRank();
    }

    /** Same pattern as {@code CardService#rebalance} — see that Javadoc. */
    private void rebalance(UUID boardId) {
        List<BoardList> lists = boardListRepository.findByBoardIdOrderByRankAsc(boardId);
        List<String> freshRanks = RankGenerator.spacedRanks(lists.size());
        for (int i = 0; i < lists.size(); i++) {
            lists.get(i).setRank(freshRanks.get(i));
        }
    }
}
