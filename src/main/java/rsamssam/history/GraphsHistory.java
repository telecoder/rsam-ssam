package rsamssam.history;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import rsamssam.config.Names;

/**
 * This class represents a graph history which contains all the graphs
 * available.
 *
 * @author Julian Peña.
 */
public class GraphsHistory {

    /**
     * Filesystem helper.
     */
    private final transient FileSystem fs;

    /**
     * Root list for the history containing Year objects. This has to be public
     * in order for the jade template engine to access it ... I know.
     */
    public final List<Year> years = new ArrayList<>();

    public GraphsHistory(Vertx vertx) {

        fs = vertx.fileSystem();

        List<String> yearFolders = ls(Names.OUTPUT_DIR)
                .stream()
                .filter(folderName -> !folderName.endsWith("web"))
                .collect(Collectors.toList());

        if (yearFolders.isEmpty()) {
            return;
        }

        yearFolders.forEach(path -> populateYear(path));

        sortEverything();
    }

    /**
     * Given a path, return the files and folders within it.
     *
     * @param path
     * @return
     */
    private List<String> ls(String path) {
        return fs.readDirBlocking(path);
    }

    /**
     * Given a year folder, scans all folders and sub-folders within it and
     * populates the folder tree (month and days).
     *
     * @param yearPath
     */
    private void populateYear(String yearPath) {

        Year year = new Year(nameFromPath(yearPath));

        List<String> monthPaths = ls(yearPath);
        monthPaths.forEach(path -> populateMonth(year, path));

        years.add(year);
    }

    /**
     * Given a year and month folder, scans all folders within it and populates
     * the folder tree (days).
     *
     * @param year
     * @param monthPath
     */
    private void populateMonth(Year year, String monthPath) {
        Month month = new Month(nameFromPath(monthPath));
        List<String> daysPaths = ls(monthPath);
        daysPaths
                .forEach(path -> {
                    Day day = new Day(nameFromPath(path));
                    month.addDay(day);
                });
        year.addMonth(month);
    }

    /**
     * Scans the given path and removes all but the last part of the path (the
     * actual file name).
     *
     * @param path The path of the file.
     * @return The file name.
     */
    private String nameFromPath(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Sorts the history in chronological order.
     */
    private void sortEverything() {
        years.sort(Comparator.comparing(Year::getName));
        years
                .forEach(year -> {
                    year.getMonths().sort(Comparator.comparing(Month::getName));
                    year.getMonths().forEach(month -> {
                        month.getDays().sort(Comparator.comparing(Day::getName));
                    });
                });
    }

    /**
     * Returns the index of the most recent year in the history.
     *
     * @return The most recent year index.
     */
    public int getLatestYearIndex() {
        return years.size() - 1;
    }

    /**
     * Return the most recent year in the history.
     *
     * @return The most recent year.
     */
    private Year getLatestYear() {
        return years.get(getLatestYearIndex());
    }

    /**
     * Returns the index of the most recent month in the history.
     *
     * @return The most recent month index.
     */
    public int getLatestMonthIndex() {
        return getLatestYear().getMonths().size() - 1;
    }

    /**
     * Return the most recent month in the history.
     *
     * @return The most recent month.
     */
    private Month getLatestMonth() {
        return getLatestYear().getMonths().get(getLatestMonthIndex());
    }

    /**
     * Returns the index of the most recent day in the history.
     *
     * @return The most recent day index.
     */
    public int getLatestDayIndex() {
        return getLatestMonth().getDays().size() - 1;
    }

    private Day getLatestDay() {
        return getLatestMonth().getDays().get(getLatestDayIndex());
    }

    /**
     * Return the most recent graphs available.
     *
     * @return
     */
    public List<String> latestGraphs() {

        if (years.isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        return graphsForDate(
                getLatestYear().getName(),
                getLatestMonth().getName(),
                getLatestDay().getName(),
                null
        );
    }

    /**
     * Return the graphs for a particular data, and filter them if a filter is
     * provided.
     *
     * @param year
     * @param month
     * @param day
     * @param filters
     * @return
     */
    public List<String> graphsForDate(String year, String month, String day,
            String filters) {
        return filter(filters, graphsForDate(year, month, day));
    }

    /**
     * Return the graphs for a particular dates.
     *
     * @param year
     * @param month
     * @param day
     * @return
     */
    private List<String> graphsForDate(String year, String month, String day) {
        String path = Names.OUTPUT_DIR + "/" + year + "/" + month + "/" + day;
        List<String> graphs = fs.readDirBlocking(path, "^.*\\.(svg|png)$");
        Collections.sort(graphs);
        return graphs;
    }

    /**
     * Given a list of graphs, filters them with the provided filter.
     *
     * @param filters
     * @param graphs
     * @return
     */
    private List<String> filter(String filters, List<String> graphs) {

        if (graphs.isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        graphs = graphs
                .stream()
                .filter(graph -> !graph.contains(Names.MAX_FREQS))
                .collect(Collectors.toList());

        if (filters == null || filters.isBlank()) {
            return graphs;
        }

        return graphs
                .stream()
                .map(graph -> nameFromPath(graph).toLowerCase())
                .filter(graph -> {
                    for (String filter : filters.split(",")) {
                        if (graph.contains(filter.toLowerCase())) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Return the indexes for the given year, month and day. This indexes are
     * used by the web interface.
     *
     * @param year
     * @param month
     * @param day
     * @return A Map containing the indexes for year, month and day.
     */
    public Map<String, String> indexes(String year, String month, String day) {

        Map<String, String> indexes = new HashMap<>();

        Year y = null;
        for (int i = 0; i < years.size(); i++) {
            if (years.get(i).getName().equalsIgnoreCase(year)) {
                y = years.get(i);
                indexes.put("year", i + "");
            }
        }

        indexes.put("month", y.getMonthIndex(month) + "");
        Month m = y.getMonth(month);
        indexes.put("day", m.getDayIndex(day) + "");

        return indexes;
    }
}
