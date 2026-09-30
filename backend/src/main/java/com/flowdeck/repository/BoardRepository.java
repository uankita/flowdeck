package com.flowdeck.repository;

import com.flowdeck.domain.Board;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardRepository extends JpaRepository<Board, UUID> {

    /** {@code WorkspaceId} traverses the {@code workspace} association — Board has no such column itself. */
    Optional<Board> findByWorkspaceIdAndBoardKey(UUID workspaceId, String boardKey);

    boolean existsByWorkspaceIdAndBoardKey(UUID workspaceId, String boardKey);

    List<Board> findAllByWorkspaceIdAndArchivedFalseOrderByNameAsc(UUID workspaceId);

    /**
     * Loads a board with its lists and cards.
     *
     * <p>Only {@code lists} is join-fetched. Adding {@code lists.cards} to the
     * graph would fetch two {@code List} collections in one query, which
     * Hibernate rejects with {@code MultipleBagFetchException}. Cards are
     * instead loaded by the {@code @BatchSize} on
     * {@link com.flowdeck.domain.BoardList#getCards()}, costing one extra
     * query for the whole board rather than one per list.
     */
    @EntityGraph(attributePaths = "lists")
    Optional<Board> findWithListsAndCardsByWorkspaceIdAndBoardKey(UUID workspaceId, String boardKey);
}
