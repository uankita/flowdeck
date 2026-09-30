package com.flowdeck.repository;

import com.flowdeck.domain.Card;
import java.util.List;
import java.util.UUID;
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
}
