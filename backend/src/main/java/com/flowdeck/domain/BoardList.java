package com.flowdeck.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * A vertical lane on a board ("Todo", "In progress", …), holding an ordered
 * set of {@link Card}s.
 *
 * <p>Named {@code BoardList} (Trello's term) rather than {@code Column} to
 * avoid colliding with {@link jakarta.persistence.Column}, and to leave room
 * for an actual "swimlane" concept later without a confusing name clash.
 *
 * <p>{@link #version} matters here: two people dragging cards between lists
 * at the same moment both touch the source and destination list's card
 * collections, so a lost update should fail loudly (409, retry) rather than
 * silently drop one person's move.
 */
@Entity
@Table(name = "board_lists")
@Getter
@Setter
@NoArgsConstructor
public class BoardList extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(nullable = false, length = 120)
    private String name;

    /**
     * Lexicographic rank string ordering this list among its siblings on the
     * board — see {@link Card#getRank()} for the full rationale, which
     * applies identically here (lists get reordered by drag-and-drop too).
     */
    @Column(nullable = false, length = 255)
    private String rank;

    /** Optional work-in-progress limit; {@code null} means unlimited. */
    @Column(name = "wip_limit")
    private Integer wipLimit;

    @Version
    @Column(nullable = false)
    private long version;

    /**
     * Batch-loaded rather than join-fetched alongside {@code Board.lists}:
     * fetching two {@code List}-typed collections in one query throws
     * Hibernate's {@code MultipleBagFetchException}. See
     * {@code BoardRepository#findWithListsAndCardsByBoardKey}.
     */
    @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    @BatchSize(size = 100)
    private List<Card> cards = new ArrayList<>();

    public void addCard(Card card) {
        cards.add(card);
        card.setList(this);
    }
}
