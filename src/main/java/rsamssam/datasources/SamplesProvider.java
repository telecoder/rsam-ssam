package rsamssam.datasources;

/**
 * Interface for data clients who provide samples is ASCII format.
 *
 * @author Julian Peña.
 */
public interface SamplesProvider {

    /**
     * Special value that MUST be returned once there is no more samples.
     */
    public static final double POISON = Double.NaN;

    /**
     * Returns the oldest sample already downloaded.
     * <p>
     * IMPORTANT: To signal that the download has ended a special value MUST be
     * returned (a poison pill).
     *
     * @return
     */
    public double getSample();
}
