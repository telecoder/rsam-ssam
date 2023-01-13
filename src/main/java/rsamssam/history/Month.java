package rsamssam.history;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a single month in the rsam and ssam history. It
 * contains the list of days that actually have graphs.
 *
 * @author Julian Peña.
 */
public class Month {

    private final String name;
    private final List<Day> days = new ArrayList<>();

    public Month(String name) {
        this.name = name;
    }

    /**
     * Return the month's name.
     *
     * @return the month's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the list of days in the month.
     *
     * @return the list of days in the month.
     */
    public List<Day> getDays() {
        return days;
    }

    /**
     * Adds a day to the month.
     *
     * @param day the day name.
     */
    public void addDay(Day day) {
        days.add(day);
    }

    /**
     * Returns the day index in the month.
     *
     * @param dayName the day's name.
     * @return the day index.
     */
    public int getDayIndex(String dayName) {
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).getName().equalsIgnoreCase(dayName)) {
                return i;
            }
        }
        // this should never happen
        return -1;
    }

    @Override
    public String toString() {
        return name;
    }
}
