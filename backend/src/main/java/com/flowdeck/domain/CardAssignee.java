package com.flowdeck.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join row assigning a {@link User} to a {@link Card}. Modeled as its own
 * entity (rather than a plain {@code @ManyToMany}) so "who assigned whom,
 * and when" is queryable via {@link BaseEntity#getCreatedAt()} — a plain
 * many-to-many join table would need the same columns anyway, just without
 * an entity to hang them on. Multiple assignees per card are allowed; see the
 * migration for the constraint that prevents assigning the same user twice.
 *
 * <p>Unlike {@link ActivityEvent}, {@link #card} here is a real association:
 * an assignment is meaningless once its card is gone, so it's fine — arguably
 * correct — that navigating it resolves to nothing once the card is
 * soft-deleted (see {@link Card}'s {@code @SQLRestriction}).
 */
@Entity
@Table(name = "card_assignees")
@Getter
@Setter
@NoArgsConstructor
public class CardAssignee extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
