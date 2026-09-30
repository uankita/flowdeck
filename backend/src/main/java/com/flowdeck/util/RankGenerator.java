package com.flowdeck.util;

/**
 * Generates lexicographically-sortable rank strings for ordering
 * {@code Card}s within a {@code BoardList} (and {@code BoardList}s within a
 * {@code Board}) — see {@code Card.rank} for the full rationale for using a
 * string rank instead of an integer position.
 *
 * <h2>How it works</h2>
 *
 * A rank is treated as a base-36 fraction: each character is a "digit" from
 * {@link #ALPHABET}, and a shorter string sorts as if padded with the lowest
 * digit ({@code '0'}) forever. {@link #between} finds a string that sorts
 * strictly between two given ranks by walking digit-by-digit until it finds a
 * position with room for a new value between the two inputs' digits at that
 * position, appending that midpoint digit, and stopping. This is the same
 * technique behind LexoRank / Trello-style position strings.
 *
 * <ul>
 *   <li>{@code between(null, next)} — rank for inserting before everything (drag to top).</li>
 *   <li>{@code between(prev, null)} — rank for inserting after everything (drag to bottom, or the first card in an empty list).</li>
 *   <li>{@code between(prev, next)} — rank for inserting between two existing siblings.</li>
 * </ul>
 *
 * <h2>Limits</h2>
 *
 * Ranks are never renumbered by this class — every insert only ever writes
 * the one row being placed. Repeatedly inserting at the exact same point
 * (e.g. always dragging to the very top) makes each new rank one character
 * longer than the last; a rank column with real headroom (this schema uses
 * {@code VARCHAR(255)}) absorbs many thousands of such inserts before a
 * rebalance would ever be needed, and rebalancing — rewriting a list's ranks
 * to short, evenly-spaced values — is an operational concern for whoever
 * implements the "move card" endpoint, not something this generator does
 * itself.
 */
public final class RankGenerator {

    /**
     * Ascending digit order. Digits before letters matters: it means plain
     * ASCII/byte string comparison (what {@code ORDER BY} on a Postgres
     * {@code text}/{@code varchar} column does under the default "C"-like
     * collation for this alphabet) agrees with the intended rank order.
     */
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    /** One past the last valid digit index — stands in for "infinity" when a bound is absent. */
    private static final int INFINITY = BASE;

    private static final int MAX_LENGTH = 200;

    private RankGenerator() {}

    /** Rank for the first item ever inserted into an empty list. */
    public static String initial() {
        return between(null, null);
    }

    /**
     * A rank that sorts strictly after {@code prev} and strictly before
     * {@code next}.
     *
     * @param prev the preceding sibling's rank, or {@code null} for "insert at the start"
     * @param next the following sibling's rank, or {@code null} for "insert at the end"
     * @throws IllegalArgumentException if {@code prev} is not strictly less than {@code next}
     */
    public static String between(String prev, String next) {
        if (prev != null && next != null && prev.compareTo(next) >= 0) {
            throw new IllegalArgumentException(
                    "prev ('%s') must sort before next ('%s')".formatted(prev, next));
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < MAX_LENGTH) {
            int lo = i < lengthOf(prev) ? digitAt(prev, i) : 0;
            int hi = i < lengthOf(next) ? digitAt(next, i) : INFINITY;

            if (hi - lo > 1) {
                // Room for a digit strictly between lo and hi at this position — place it and stop.
                result.append(ALPHABET.charAt(lo + (hi - lo) / 2));
                return result.toString();
            }

            // No room yet: carry the shared/lower digit and keep going deeper.
            result.append(ALPHABET.charAt(lo));
            i++;
        }
        // Astronomically unlikely with real drag-and-drop usage; a caller that hits
        // this should rebalance the list's ranks rather than keep appending forever.
        throw new IllegalStateException(
                "Could not find a rank between '%s' and '%s' within %d characters — rebalance this list's ranks"
                        .formatted(prev, next, MAX_LENGTH));
    }

    private static int lengthOf(String s) {
        return s == null ? 0 : s.length();
    }

    private static int digitAt(String s, int index) {
        int value = ALPHABET.indexOf(s.charAt(index));
        if (value < 0) {
            throw new IllegalArgumentException(
                    "Rank '%s' contains a character outside the alphabet '%s'".formatted(s, ALPHABET));
        }
        return value;
    }
}
