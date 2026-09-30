package com.flowdeck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Identity and auditing fields shared by every entity.
 *
 * <p>Deliberately does NOT carry {@code @Version} or a {@code deletedAt}
 * column — those are opt-in per entity ({@link Card} and {@link BoardList}
 * declare their own version; {@link Board} and {@link Card} their own
 * {@code deletedAt}) rather than forced on the whole model. Most rows here
 * (memberships, comments, activity events, join rows) are either immutable or
 * hard-deleted, so optimistic locking and soft delete would be dead weight.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
