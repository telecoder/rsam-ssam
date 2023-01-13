package rsamssam.config;

/**
 * Some useful defaults.
 *
 * @author Julian Peña.
 */
public class Defaults {

    /**
     * Default replot interval in minutes.
     */
    public static final int REPLOT_INTERVAL = 1;

    /**
     * Default averaging interval for rsam.
     */
    public static final int RSAM_AVERAGE_WINDOW = 5;

    /**
     * Default zero tolerance (how many consecutive zeros are we willing to take
     * as real trace?.
     */
    public static final int ZERO_TOLERANCE = 10;

    /**
     * Default service timeout in seconds. This refers to the individual
     * services that are part of the rsam-ssam calculations.
     */
    public static final int TIMEOUT = 60;

    /**
     * Default Winston server port.
     */
    public static final int WINSTON_PORT = 16022;

    /**
     * Default winston timeout in seconds.
     */
    public static final int WINSTON_TIMEOUT = 120;

    /**
     * Default fdsn timeout in seconds.
     */
    public static final int FDSN_TIMEOUT = 120;

    /**
     * Default fdsn server port.
     */
    public static final int FDSN_PORT = 8080;

    /**
     * Default seedlink server port.
     */
    public static final int SEEDLINK_PORT = 18000;

    /**
     * Default web server port.
     */
    public static final int WEB_PORT = 19090;

    /**
     * Default output graph width.
     */
    public static final int GRAPH_WIDTH = 1200;

    /**
     * Default output graph height.
     */
    public static final int GRAPH_HEIGHT = 1200;

    /**
     * Default thread pool size.
     */
    public static final int THREAD_POOL_SIZE = 10;

    /**
     * Default query type for web interface queries and queries without a type
     * specifically set.
     */
    public static final String QUERY_TYPE = "fdsn";
}
