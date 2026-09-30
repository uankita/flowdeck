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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A Kanban board within a {@link Workspace}, containing an ordered set of
 * {@link BoardList}s.
 *
 * <p>Soft-deletable: {@link #deletedAt} marks a board as gone without losing
 * its history (cards, comments, activity). {@code @SQLRestriction} makes
 * every normal query — {@code findById}, derived finders, JPQL without an
 * explicit predicate — silently exclude soft-deleted rows, the same way a
 * {@code deleted_at IS NULL} clause hand-written on every query would. A
 * background purge job that actually needs deleted rows should use a native
 * query, which bypasses this filter.
 */
@Entity
@Table(name = "boards")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Board extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    /** Short human-facing identifier used in URLs, e.g. {@code FLOW}. Unique per workspace, not globally. */
    @Column(name = "board_key", nullable = false, length = 16)
    private String boardKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean archived = false;

    /** Soft-delete marker. {@code null} means "not deleted" — see class Javadoc. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    private List<BoardList> lists = new ArrayList<>();

    public void addList(BoardList list) {
        lists.add(list);
        list.setBoard(this);
    }
}
