package com.flowdeck.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RankGeneratorTest {

    @Test
    void insertingAtTheEndRepeatedlyStaysInOrder() {
        List<String> ranks = new ArrayList<>();
        ranks.add(RankGenerator.initial());
        for (int i = 0; i < 50; i++) {
            ranks.add(RankGenerator.between(ranks.get(ranks.size() - 1), null));
        }
        assertSorted(ranks);
    }

    @Test
    void insertingAtTheStartRepeatedlyStaysInOrder() {
        List<String> ranks = new ArrayList<>();
        ranks.add(RankGenerator.initial());
        for (int i = 0; i < 50; i++) {
            ranks.add(0, RankGenerator.between(null, ranks.get(0)));
        }
        assertSorted(ranks);
    }

    @Test
    void repeatedlyInsertingBetweenTheSameTwoNeighboursConverges() {
        // The classic worst case for a naive midpoint scheme: always splitting
        // the same gap, e.g. always dragging a card to just after the first one.
        String prev = "a";
        String next = "b";
        List<String> ranks = new ArrayList<>(List.of(prev, next));
        for (int i = 0; i < 30; i++) {
            String mid = RankGenerator.between(prev, next);
            ranks.add(1, mid);
            next = mid; // keep narrowing the same gap
        }
        assertSorted(ranks);
    }

    @Test
    void bothBoundsAbsentReturnsAUsableStartingRank() {
        String rank = RankGenerator.between(null, null);
        assertThat(rank).isNotBlank();
    }

    @Test
    void rejectsBoundsInTheWrongOrder() {
        assertThatThrownBy(() -> RankGenerator.between("m", "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RankGenerator.between("m", "m"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "9", "a", "z", "abc123"})
    void everyGeneratedRankSortsStrictlyBetweenItsInputs(String seed) {
        String next = RankGenerator.between(seed, null);
        String mid = RankGenerator.between(seed, next);
        assertThat(mid).isGreaterThan(seed);
        assertThat(mid).isLessThan(next);
    }

    private static void assertSorted(List<String> ranks) {
        List<String> sorted = new ArrayList<>(ranks);
        sorted.sort(String::compareTo);
        assertThat(ranks).containsExactlyElementsOf(sorted);
        assertThat(ranks).doesNotHaveDuplicates();
    }

    // ── spacedRanks ──────────────────────────────────────────────────────

    @Test
    void spacedRanksOfZeroIsEmpty() {
        assertThat(RankGenerator.spacedRanks(0)).isEmpty();
    }

    @Test
    void spacedRanksOfOneIsASingleUsableRank() {
        List<String> ranks = RankGenerator.spacedRanks(1);
        assertThat(ranks).hasSize(1);
        assertThat(ranks.get(0)).isNotBlank();
    }

    @Test
    void spacedRanksRejectsANegativeCount() {
        assertThatThrownBy(() -> RankGenerator.spacedRanks(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 5, 20, 100, 1000})
    void spacedRanksAreSortedAndDistinct(int count) {
        assertSorted(RankGenerator.spacedRanks(count));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 5, 20, 100, 1000, 10_000})
    void spacedRanksStayShortEvenForLargeLists(int count) {
        // The whole point of bisecting instead of filling sequentially: every
        // rank should cost O(log base-36 of count) characters, not O(count).
        // 1000 items still fits comfortably inside MAX_RANK_LENGTH; this is a
        // regression guard against silently reverting to linear growth.
        List<String> ranks = RankGenerator.spacedRanks(count);
        int longest = ranks.stream().mapToInt(String::length).max().orElse(0);
        assertThat(longest)
                .as("longest of %d spaced ranks", count)
                .isLessThanOrEqualTo(RankGenerator.MAX_RANK_LENGTH);
    }

    @Test
    void spacedRanksLeavesRoomToInsertBeforeAndAfterEveryEntry() {
        // A rebalance is pointless if the fresh ranks are so tightly packed
        // that the very next ordinary insert immediately re-exhausts. Confirm
        // between() still works around, before, and after every spaced rank.
        List<String> ranks = RankGenerator.spacedRanks(10);
        assertThat(RankGenerator.between(null, ranks.get(0))).isLessThan(ranks.get(0));
        assertThat(RankGenerator.between(ranks.get(9), null)).isGreaterThan(ranks.get(9));
        for (int i = 0; i < ranks.size() - 1; i++) {
            String mid = RankGenerator.between(ranks.get(i), ranks.get(i + 1));
            assertThat(mid).isGreaterThan(ranks.get(i)).isLessThan(ranks.get(i + 1));
        }
    }

    // ── exhaustion + rebalance ───────────────────────────────────────────

    @Test
    void betweenThrowsOnceTheGapIsRepeatedlySqueezedPastTheLimit() {
        SqueezeResult squeezed = squeezeSameGapUntilExhausted();

        assertThat(squeezed.iterationsBeforeExhaustion)
                .as("consecutive same-gap inserts survived before exhaustion")
                .isPositive()
                .isLessThan(200); // sanity bound; see class Javadoc for the ~55 estimate at MAX_RANK_LENGTH=12

        assertThatThrownBy(() -> RankGenerator.between(squeezed.prev, squeezed.next))
                .isInstanceOf(RankExhaustionException.class)
                .hasMessageContaining("rebalance");
    }

    @Test
    void everyRankProducedBeforeExhaustionRespectsTheLengthLimit() {
        SqueezeResult squeezed = squeezeSameGapUntilExhausted();
        assertThat(squeezed.allGeneratedRanks)
                .allSatisfy(rank -> assertThat(rank.length()).isLessThanOrEqualTo(RankGenerator.MAX_RANK_LENGTH));

        // Descending, not ascending: prev ("a") is fixed and next shrinks
        // toward it every iteration, so each new mid is pulled below the
        // last one, approaching "a" from above rather than climbing away
        // from it. (Contrast repeatedlyInsertingBetweenTheSameTwoNeighboursConverges
        // above, which inserts at a fixed array slot and asserts ascending.)
        List<String> descending = new ArrayList<>(squeezed.allGeneratedRanks);
        descending.sort(Comparator.reverseOrder());
        assertThat(squeezed.allGeneratedRanks).containsExactlyElementsOf(descending);
        assertThat(squeezed.allGeneratedRanks).doesNotHaveDuplicates();
    }

    @Test
    void rebalancingAnExhaustedGapMakesItInsertableAgain() {
        // The actual recovery path: hit exhaustion between two neighbours,
        // rebalance the (small) list they belong to, then confirm a normal
        // between() call against the freshly spaced neighbours works fine —
        // this is exactly what CardService does when between() throws.
        SqueezeResult squeezed = squeezeSameGapUntilExhausted();
        assertThatThrownBy(() -> RankGenerator.between(squeezed.prev, squeezed.next))
                .isInstanceOf(RankExhaustionException.class);

        List<String> rebalanced = RankGenerator.spacedRanks(2);
        String newPrev = rebalanced.get(0);
        String newNext = rebalanced.get(1);

        String mid = RankGenerator.between(newPrev, newNext);
        assertThat(mid).isGreaterThan(newPrev).isLessThan(newNext);
        assertThat(mid.length()).isLessThanOrEqualTo(RankGenerator.MAX_RANK_LENGTH);
    }

    private record SqueezeResult(String prev, String next, int iterationsBeforeExhaustion, List<String> allGeneratedRanks) {}

    /**
     * Repeatedly narrows the gap between a fixed {@code prev} and a shrinking
     * {@code next} — the worst case described in {@link RankGenerator}'s
     * class Javadoc — until the next squeeze would exceed
     * {@link RankGenerator#MAX_RANK_LENGTH}, without actually triggering the
     * exception (so callers can assert on that final call themselves).
     * Bounded at 500 iterations so a regression that removed the length
     * check entirely fails this helper loudly instead of hanging.
     */
    private static SqueezeResult squeezeSameGapUntilExhausted() {
        String prev = "a";
        String next = "b";
        List<String> generated = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            String mid;
            try {
                mid = RankGenerator.between(prev, next);
            } catch (RankExhaustionException e) {
                return new SqueezeResult(prev, next, i, generated);
            }
            generated.add(mid);
            next = mid;
        }
        throw new IllegalStateException("Expected exhaustion within 500 same-gap squeezes but never hit it");
    }
}
