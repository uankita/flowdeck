package com.flowdeck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tag defined at board scope (e.g. "Bug", "Design"), applied to cards via
 * {@link CardLabel}. Board-scoped rather than workspace-scoped so each board
 * can curate its own label set without a shared vocabulary leaking across
 * unrelated boards.
 */
@Entity
@Table(name = "labels")
@Getter
@Setter
@NoArgsConstructor
public class Label extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(nullable = false, length = 60)
    private String name;

    /** Hex color, e.g. {@code #4f46e5}. */
    @Column(nullable = false, length = 7)
    private String color;
}
