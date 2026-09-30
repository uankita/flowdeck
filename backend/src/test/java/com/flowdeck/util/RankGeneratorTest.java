package com.flowdeck.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
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
}
