package com.flowdeck.domain;

/**
 * What happened, for {@link ActivityEvent}. Not requested explicitly, but
 * {@code ActivityEvent.eventType} needs *some* controlled vocabulary rather
 * than a free-text string every producer spells differently — kept small and
 * additive; extend as new event producers show up.
 */
public enum ActivityEventType {
    BOARD_CREATED,
    BOARD_ARCHIVED,
    BOARD_DELETED,
    LIST_CREATED,
    LIST_RENAMED,
    LIST_DELETED,
    CARD_CREATED,
    CARD_MOVED,
    CARD_UPDATED,
    CARD_DELETED,
    CARD_ASSIGNED,
    CARD_UNASSIGNED,
    COMMENT_ADDED,
    LABEL_ADDED,
    LABEL_REMOVED,
    MEMBER_ADDED,
    MEMBER_ROLE_CHANGED,
    MEMBER_REMOVED
}
