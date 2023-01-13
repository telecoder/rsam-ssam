package rsamssam.history;

/**
 * This class represents a single graph.
 *
 * @author Julian Peña.
 */
public class Graph {

    private final String path;

    public Graph(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return path;
    }
}
