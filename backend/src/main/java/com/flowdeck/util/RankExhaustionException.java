package com.flowdeck.util;

/**
 * Thrown by {@link RankGenerator#between} when no rank within
 * {@link RankGenerator#MAX_RANK_LENGTH} characters sorts between the two
 * given ranks. Not a data problem — see {@link RankGenerator}'s class
 * Javadoc "Rank exhaustion and rebalancing" section. The caller should
 * rebalance the affected list (via {@link RankGenerator#spacedRanks}) and
 * retry the original {@code between} call against the freshly spaced
 * neighbours.
 */
public class RankExhaustionException extends RuntimeException {

    public RankExhaustionException(String prev, String next) {
        super(
                "No rank fits between '%s' and '%s' within %d characters — rebalance this list's ranks"
                        .formatted(prev, next, RankGenerator.MAX_RANK_LENGTH));
    }
}
