package rsamssam.history;

/**
 * This class represents a single day in the rsam and ssam history.
 *
 * @author Julian Peña.
 */
public class Day {

    private final String name;

    public Day(String name) {
        this.name = name;
    }

    /**
     * Return the day's name.
     *
     * @return the day's name.
     */
    public String getName() {
        return name;
    }

}
