package com.flowdeck.repository;

import com.flowdeck.domain.CardLabel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardLabelRepository extends JpaRepository<CardLabel, UUID> {

    boolean existsByCardIdAndLabelId(UUID cardId, UUID labelId);

    /** Join-fetches {@code label} — the whole point of this query is rendering a card's label chips. */
    @EntityGraph(attributePaths = "label")
    List<CardLabel> findByCardId(UUID cardId);

    long deleteByCardIdAndLabelId(UUID cardId, UUID labelId);
}
