package com.flowdeck.repository;

import com.flowdeck.domain.Card;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

    /**
     * A list's live cards in display order — the rebalance read: recompute
     * evenly-spaced ranks for exactly this set with
     * {@code RankGenerator.spacedRanks(size)} and reassign in order.
     * {@code ListId} traverses the {@code list} association; Card has no such
     * column itself.
     */
    List<Card> findByListIdOrderByRankAsc(UUID listId);

    /** Paginated variant of the same query, sort order fixed to match — see {@code CardController}. */
    Page<Card> findByListId(UUID listId, Pageable pageable);

    /** The current last card in a list — append-a-card's anchor for {@code RankGenerator.between(lastRank, null)}. */
    Optional<Card> findFirstByListIdOrderByRankDesc(UUID listId);
}
