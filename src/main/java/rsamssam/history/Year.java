package rsamssam.history;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a year in the graphs history. In contains only the
 * months which have days that have graphs.
 *
 * @author Julian Peña.
 */
public class Year {

    private final String name;
    private final List<Month> months = new ArrayList<>();

    public Year(String name) {
        this.name = name;
    }

    /**
     * Returns the year's name.
     *
     * @return the year's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Return all the months for this year.
     *
     * @return A list of months.
     */
    public List<Month> getMonths() {
        return months;
    }

    /**
     * Adds a month.
     *
     * @param month A month object.
     */
    public void addMonth(Month month) {
        months.add(month);
    }

    /**
     * Returns a month.
     *
     * @param name The month's name.
     * @return The month.
     */
    public Month getMonth(String name) {
        return months.get(getMonthIndex(name));
    }

    /**
     * Returns a month's index.
     *
     * @param name The name of the month.
     * @return The month's index.
     */
    public int getMonthIndex(String name) {
        for (int i = 0; i < months.size(); i++) {
            if (months.get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        
        // this should never happen
        return -1;
    }

}
