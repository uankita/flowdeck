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
 * Join row applying a {@link Label} to a {@link Card}. See
 * {@link CardAssignee} both for why this is a full entity rather than a bare
 * join table, and for why {@link #card} being a real association (rather
 * than a raw UUID, contrast {@link ActivityEvent}) is the right call here.
 */
@Entity
@Table(name = "card_labels")
@Getter
@Setter
@NoArgsConstructor
public class CardLabel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "label_id", nullable = false)
    private Label label;
}
