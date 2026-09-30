package com.flowdeck.repository;

import com.flowdeck.domain.Label;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabelRepository extends JpaRepository<Label, UUID> {

    Page<Label> findByBoardId(UUID boardId, Pageable pageable);

    boolean existsByBoardIdAndName(UUID boardId, String name);
}
