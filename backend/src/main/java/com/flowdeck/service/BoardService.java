package com.flowdeck.service;

import com.flowdeck.domain.Board;
import com.flowdeck.domain.BoardList;
import com.flowdeck.domain.Workspace;
import com.flowdeck.repository.BoardRepository;
import com.flowdeck.repository.WorkspaceRepository;
import com.flowdeck.util.RankGenerator;
import com.flowdeck.web.dto.BoardDtos.BoardDetailResponse;
import com.flowdeck.web.dto.BoardDtos.BoardSummaryResponse;
import com.flowdeck.web.dto.BoardDtos.CreateBoardRequest;
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
public class BoardService {

    /** Lists every new board starts with. */
    private static final List<String> DEFAULT_LISTS = List.of("Backlog", "In progress", "Done");

    private final BoardRepository boardRepository;
    private final WorkspaceRepository workspaceRepository;
    private final BoardMapper boardMapper;

    @Transactional(readOnly = true)
    public List<BoardSummaryResponse> listActiveBoards(UUID workspaceId) {
        return boardMapper.toSummaries(
                boardRepository.findAllByWorkspaceIdAndArchivedFalseOrderByNameAsc(workspaceId));
    }

    @Transactional(readOnly = true)
    public BoardDetailResponse getBoard(UUID workspaceId, String boardKey) {
        return boardRepository
                .findWithListsAndCardsByWorkspaceIdAndBoardKey(workspaceId, boardKey)
                .map(boardMapper::toDetail)
                .orElseThrow(() -> new BoardNotFoundException(boardKey));
    }

    @Transactional
    public BoardDetailResponse createBoard(UUID workspaceId, CreateBoardRequest request) {
        if (boardRepository.existsByWorkspaceIdAndBoardKey(workspaceId, request.boardKey())) {
            throw new DuplicateBoardKeyException(request.boardKey());
        }
        Workspace workspace =
                workspaceRepository
                        .findById(workspaceId)
                        .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));

        Board board = new Board();
        board.setWorkspace(workspace);
        board.setBoardKey(request.boardKey());
        board.setName(request.name());
        board.setDescription(request.description());

        // Each list's rank is generated "after" the previous one, so the
        // seeded order (Backlog, In progress, Done) is what ORDER BY rank
        // returns with no further bookkeeping.
        String rank = null;
        for (String listName : DEFAULT_LISTS) {
            rank = RankGenerator.between(rank, null);
            BoardList list = new BoardList();
            list.setName(listName);
            list.setRank(rank);
            board.addList(list);
        }

        Board saved = boardRepository.save(board);
        log.info(
                "Created board {} ({}) in workspace {}",
                saved.getBoardKey(),
                saved.getId(),
                workspaceId);
        return boardMapper.toDetail(saved);
    }
}
