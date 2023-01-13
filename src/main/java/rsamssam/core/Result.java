package rsamssam.core;

/**
 *
 * A Result record contains a rsam value, an array with spectra magnitudes and
 * an array containing the frequency with the highest energy in the spectra
 * along with the energy value.
 *
 * @author Julian Peña.
 */
public record Result(double rsam, double[] spectra, double[] maxFreq) {

}
