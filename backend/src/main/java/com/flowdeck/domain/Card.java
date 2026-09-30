package com.flowdeck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A single unit of work on a {@link BoardList}.
 *
 * <h2>Why a rank string instead of an integer position</h2>
 *
 * An integer position requires renumbering every following row when a card
 * is inserted or moved — O(n) writes, and a burst of concurrent drags on the
 * same list turns into a stampede of conflicting updates. A rank string
 * (LexoRank-style: see {@code com.flowdeck.util.RankGenerator}) is a
 * lexicographically-sortable string chosen to sit strictly between its two
 * neighbours' ranks. Moving a card is then a single-row write of a freshly
 * computed rank — no other card's row is touched, so concurrent drags on
 * different cards never conflict, and {@code ORDER BY rank} gives the
 * display order directly with no renumbering pass ever required. The
 * trade-off is that ranks can theoretically run out of room between two
 * adjacent strings after many inserts at the same point; that's handled by
 * occasionally rebalancing a list's ranks, not by the schema.
 *
 * <h2>Why {@code @Version} here</h2>
 *
 * Two people can drag the same card at once, or one person edits a card's
 * title while another moves it. Optimistic locking turns the loser's stale
 * write into a 409 the client can retry against fresh data, instead of
 * silently discarding one of the two changes.
 *
 * <h2>Why soft-deletable</h2>
 *
 * Deleting a card should not orphan its {@link Comment}s, {@link ActivityEvent}
 * history, or assignment/label rows, and "restore card" is a reasonable
 * feature to want later. See {@link Board} for how {@code @SQLRestriction}
 * applies the {@code deletedAt} filter.
 */
@Entity
@Table(name = "cards")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Card extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_list_id", nullable = false)
    private BoardList list;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 8000)
    private String description;

    /** Lexicographic rank among sibling cards in {@link #list}. See class Javadoc. */
    @Column(nullable = false, length = 255)
    private String rank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CardPriority priority = CardPriority.MEDIUM;

    @Column(name = "due_at")
    private Instant dueAt;

    /** Soft-delete marker. {@code null} means "not deleted" — see class Javadoc. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;
}
