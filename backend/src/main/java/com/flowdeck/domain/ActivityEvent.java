package com.flowdeck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An append-only audit-log row: "who did what, where, when." Powers activity
 * feeds at the workspace, board, and card level.
 *
 * <p>{@link #boardId} and {@link #cardId} are plain UUID columns, not
 * {@code @ManyToOne} associations to {@link Board} / {@link Card}. Both of
 * those entities carry {@code @SQLRestriction("deleted_at IS NULL")}, which
 * Hibernate applies to single-row association fetches as well as collection
 * queries — a real {@code @ManyToOne} here would silently fail to resolve for
 * an event referencing a card or board that has since been soft-deleted,
 * breaking exactly the history this table exists to preserve. Rendering a
 * feed entry for a deleted card just means doing the lookup yourself (and
 * tolerating "not found").
 *
 * <p>{@link #workspaceId} and {@link #actorId} ARE real associations:
 * {@link Workspace} and {@link User} are never soft-deleted, so no such risk
 * applies, and the FK gives referential integrity for free.
 */
@Entity
@Table(name = "activity_events")
@Getter
@Setter
@NoArgsConstructor
public class ActivityEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    /** See class Javadoc — intentionally not a {@code @ManyToOne}. Null for workspace-level events. */
    @Column(name = "board_id")
    private UUID boardId;

    /** See class Javadoc — intentionally not a {@code @ManyToOne}. Null for board- or workspace-level events. */
    @Column(name = "card_id")
    private UUID cardId;

    /** Null for system-generated events with no human actor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private ActivityEventType eventType;

    /**
     * Small JSON payload with event-specific detail (e.g. old/new list for a
     * {@code CARD_MOVED} event). Stored as {@code TEXT} rather than a mapped
     * {@code jsonb} type to avoid pulling in a JSON type converter for what
     * is, so far, a write-once/read-and-display-verbatim field; revisit if
     * this ever needs to be queried by content.
     */
    @Column(length = 4000)
    private String metadata;
}
