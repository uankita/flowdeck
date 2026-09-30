package com.flowdeck.domain;

/** Not part of the requested model — kept from the original single-board
 * prototype because it costs nothing and every Kanban board needs a triage
 * signal. Safe to drop if unwanted. */
public enum CardPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
