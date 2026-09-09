package io.dataroots.savingstreak.points;

import java.time.Instant;

/**
 * One slice of a move of points from one customer to another: how many points came out of a single
 * batch, and the moment they were originally earned.
 *
 * <p>A list of these is the shape of what a move did, because a move is not one thing. Points are
 * drawn oldest first and a move larger than the oldest batch takes part of the next one along, so a
 * move of forty points can be three slices of different ages — and the ages are the load-bearing
 * half: each slice arrives in the receiving pot dated at the moment given here, so a chain of moves
 * cannot restart anybody's twelve months.
 *
 * <p>The moment and the number, and nothing else. Which batch a slice came off of, what reason wrote
 * it and what was left in it afterwards are the ledger's own business — they are in its DEBUG line
 * and not in this record, because a caller that could read a batch identifier would be a caller that
 * had learned points are kept as batches at all.
 */
public record MovedPoints(long points, Instant earnedAt) {
}
