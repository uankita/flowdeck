package com.flowdeck.repository;

import com.flowdeck.domain.BoardList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardListRepository extends JpaRepository<BoardList, UUID> {

    /** The current last list on a board — append-a-list's anchor for {@code RankGenerator.between(lastRank, null)}. */
    Optional<BoardList> findFirstByBoardIdOrderByRankDesc(UUID boardId);

    /** A board's lists in display order — the rebalance read, same pattern as {@code CardRepository}. */
    List<BoardList> findByBoardIdOrderByRankAsc(UUID boardId);
}
