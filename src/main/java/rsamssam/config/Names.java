package rsamssam.config;

/**
 * Utility class for storing constants.
 *
 * @author Julian Peña.
 */
public abstract class Names {

    /**
     * Configuration file.
     */
    public static final String CONFIG_FILE = "conf/config.properties";

    /**
     * Json file for periodic queries.
     */
    public static final String QUERIES_FILE = "conf/queries.json";

    /**
     * Folder for output graphs a result files.
     */
    public static final String OUTPUT_DIR = "output";

    /**
     * Output for graphs requested via the web interface.
     */
    public static final String WEB_DIRECTORY = OUTPUT_DIR + "/web";

    /**
     * Folder with internal web files (html, js, css, etc.).
     */
    public static final String WEB_ASSETS = "web";

    /**
     * Property name for the port on which the web server listen.
     */
    public static final String WEB_PORT = "webServerPort";

    /**
     * Property name for the rsam average window.
     */
    public static final String RSAM_AVERAGE_WINDOW = "RSAMAverageWindow";

    /**
     * Property name for the zero tolerance.
     */
    public static final String ZERO_TOLERANCE = "zeroTolerance";

    /**
     * Property name for the replot interval.
     */
    public static final String REPLOT_INTERVAL = "replotInterval";

    /**
     * Property name for the thread pool size.
     */
    public static final String THREAD_POOL_SIZE = "threadPoolSize";

    /**
     * Property name for the default query type.
     */
    public static final String DEFAULT_QUERY_TYPE = "defaultQueryType";

    /**
     * Property name for the FDSN server.
     */
    public static final String FDSN_SERVER = "fdsnServer";

    /**
     * Property name for the FDSN server port.
     */
    public static final String FDSN_PORT = "fdsnPort";

    /**
     * Property name for the FDSN server dataselect prefix.
     */
    public static final String FDSN_DATASELECT_URL = "dataselectPrefix";

    /**
     * Property name for the FDSN timeout.
     */
    public static final String FDSN_TIMEOUT = "fdsnTimeout";

    /**
     * Property name for the SeedLink server.
     */
    public static final String SEEDLINK_SERVER = "seedlinkServer";

    /**
     * Property name for the SeedLink server port.
     */
    public static final String SEEDLINK_PORT = "seedlinkPort";

    /**
     * Property name for the Winston server.
     */
    public static final String WINSTON_SERVER = "winstonServer";

    /**
     * Property name for the Winston server port.
     */
    public static final String WINSTON_PORT = "winstonPort";

    /**
     * Name for the configuration option serviceTimeout.
     */
    public static final String TIMEOUT = "serviceTimeout";
    

    // SCNL PARAMETERS NAMES
    public static final String QUERY_TYPE = "type";
    public static final String STATION = "S";
    public static final String COMPONENT = "C";
    public static final String NETWORK = "N";
    public static final String LOCATION = "L";
    public static final String FROM = "from";
    public static final String FROM_TIME = "fromTime";
    public static final String TO = "to";
    public static final String TO_TIME = "toTime";

    // QUERY PARAMETER NAMES
    public static final String WINDOW_SIZE = "windowSize";
    public static final String WINDOW_FUNCTION = "windowFunction";
    public static final String WINDOW_UNIFORM = "uniform";
    public static final String WINDOW_HANN = "hann";

    public static final String RESPONSE_FACTOR = "responseFactor";
    public static final String CUTOFF_FREQUENCY = "cutoff";
    public static final String MAX_POWER = "maxPower";
    public static final String GRAPH_FORMAT = "graphFormat";
    public static final String GRAPH_FORMAT_SVG = "svg";
    public static final String GRAPH_FORMAT_PNG = "png";
    public static final String GRAPH_WIDTH = "graphWidth";
    public static final String GRAPH_HEIGHT = "graphHeight";
    public static final String WEB_QUERY = "webQuery";

    public static final String SSAM_MAGNITUDES = "ssam";
    public static final String RSAM = "rsam";
    public static final String AVERAGE_SSAM = "averageSSAM";
    public static final String MAX_FREQS = "maxfreqs";

    // OUTPUT FILES NAMES
    public static final String SSAM_FILENAME_SUFIX = "ssam.csv";
    public static final String RSAM_FILENAME_SUFIX = "rsam.csv";
    public static final String AVERAGE_SSAM_FILENAME_SUFIX = "average_ssam.csv";
    public static final String MAX_FREQ_FILENAME_SUFIX = "max_freqs.csv";

    public static final String GRAPH = "graph";

}
