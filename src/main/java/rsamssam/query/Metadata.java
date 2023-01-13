package rsamssam.query;

/**
 * This record holds the metadata associated with a Query's results.
 *
 * @param sps samples per second.
 * @param start Real start time (could be different from what was requested).
 *
 * @author Julian Peña.
 */
public record Metadata(int sps, long start) {
};
