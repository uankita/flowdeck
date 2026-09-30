package com.flowdeck.service;

import com.flowdeck.domain.Board;
import com.flowdeck.domain.Label;
import com.flowdeck.repository.BoardRepository;
import com.flowdeck.repository.LabelRepository;
import com.flowdeck.web.dto.LabelDtos.CreateLabelRequest;
import com.flowdeck.web.dto.LabelDtos.LabelResponse;
import com.flowdeck.web.dto.LabelDtos.UpdateLabelRequest;
import com.flowdeck.web.dto.PageResponse;
import com.flowdeck.web.mapper.LabelMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabelService {

    private final LabelRepository labelRepository;
    private final BoardRepository boardRepository;
    private final LabelMapper labelMapper;

    @Transactional
    public LabelResponse createLabel(UUID workspaceId, String boardKey, CreateLabelRequest request) {
        Board board = loadBoardInWorkspace(workspaceId, boardKey);
        if (labelRepository.existsByBoardIdAndName(board.getId(), request.name())) {
            throw new DuplicateLabelNameException(request.name());
        }

        Label label = new Label();
        label.setBoard(board);
        label.setName(request.name());
        label.setColor(request.color());

        Label saved = labelRepository.save(label);
        log.info("Created label {} ({}) on board {}", saved.getName(), saved.getId(), boardKey);
        return labelMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<LabelResponse> listLabels(UUID workspaceId, String boardKey, Pageable pageable) {
        Board board = loadBoardInWorkspace(workspaceId, boardKey);
        Page<LabelResponse> page = labelRepository.findByBoardId(board.getId(), pageable).map(labelMapper::toResponse);
        return PageResponse.of(page);
    }

    @Transactional
    public LabelResponse updateLabel(UUID workspaceId, String boardKey, UUID labelId, UpdateLabelRequest request) {
        Label label = loadLabelInBoard(workspaceId, boardKey, labelId);
        boolean renaming = !label.getName().equals(request.name());
        if (renaming && labelRepository.existsByBoardIdAndName(label.getBoard().getId(), request.name())) {
            throw new DuplicateLabelNameException(request.name());
        }

        label.setName(request.name());
        label.setColor(request.color());
        return labelMapper.toResponse(labelRepository.save(label));
    }

    /** Hard delete — cascades to {@code card_labels} for every card carrying this label. */
    @Transactional
    public void deleteLabel(UUID workspaceId, String boardKey, UUID labelId) {
        Label label = loadLabelInBoard(workspaceId, boardKey, labelId);
        labelRepository.delete(label);
        log.info("Deleted label {} from board {}", labelId, boardKey);
    }

    private Board loadBoardInWorkspace(UUID workspaceId, String boardKey) {
        return boardRepository
                .findByWorkspaceIdAndBoardKey(workspaceId, boardKey)
                .orElseThrow(() -> new BoardNotFoundException(boardKey));
    }

    private Label loadLabelInBoard(UUID workspaceId, String boardKey, UUID labelId) {
        Label label = labelRepository.findById(labelId).orElseThrow(() -> new LabelNotFoundException(labelId));
        Board board = label.getBoard();
        if (!board.getWorkspace().getId().equals(workspaceId) || !board.getBoardKey().equals(boardKey)) {
            throw new LabelNotFoundException(labelId);
        }
        return label;
    }
}
