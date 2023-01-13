package rsamssam.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class used to get values from configuration.properties file.
 *
 * @author Julian Peña.
 */
public abstract class Config {

    /**
     * Our logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger("Config");

    private static final Properties PROPERTIES;

    static {
        PROPERTIES = new Properties();
        try {
            PROPERTIES.load(new FileInputStream(Names.CONFIG_FILE));
        } catch (FileNotFoundException ex) {
            LOG.error(ex.getMessage());
            LOG.error("Missing config.properties file?");
            LOG.warn("Using default values");
        } catch (IOException ex) {
            LOG.error(ex.getMessage());
            LOG.error("Couldn't open config.properties");
            LOG.warn("Using default values");
        }
    }

    /**
     * Returns the periodic interval for recreating graphs.
     *
     * @return
     */
    public static int getReplotInterval() {
        int value = getInt(Names.REPLOT_INTERVAL, Defaults.REPLOT_INTERVAL);
        if (value < Defaults.REPLOT_INTERVAL) {
            value = Defaults.REPLOT_INTERVAL;
            LOG.warn("replot interval too short. Using default value");
        }
        return value;
    }

    /**
     * Returns the default query type for queries made via the web interface.
     *
     * @return
     */
    public static String getDefaultQueryType() {
        return getString(Names.DEFAULT_QUERY_TYPE, Defaults.QUERY_TYPE);
    }

    /**
     * Default timeout for a service.
     *
     * @return
     */
    public static int getTimeout() {
        int value = getInt(Names.TIMEOUT, Defaults.TIMEOUT);
        if (value < Defaults.TIMEOUT) {
            value = Defaults.TIMEOUT;
            LOG.warn("timeout too short. Using default value");
        }
        return value;
    }

    /**
     * Default FDSN timeout;
     *
     * @return
     */
    public static int getFdsnTimeout() {
        int value = getInt(Names.FDSN_TIMEOUT, Defaults.FDSN_TIMEOUT);
        if (value < Defaults.WINSTON_TIMEOUT) {
            value = Defaults.WINSTON_TIMEOUT;
            LOG.warn("Winston timeout too short. Using default value");
        }
        return value;
    }

    /**
     * Returns the FDSN server configured (if any).
     *
     * @return The IP address of the server or null if no value was configured.
     */
    public static String getFdsnServer() {
        return Config.getString(Names.FDSN_SERVER, null);
    }

    /**
     * Returns the FDSN server port configured of the default value.
     *
     * @return
     */
    public static int getFdsnServerPort() {
        int value = Config.getInt(Names.FDSN_PORT, Defaults.FDSN_PORT);
        if (value < 0 || value > 65535) {
            LOG.warn("FDSN port {} is invalid. Using {}", Defaults.FDSN_PORT);
            value = Defaults.FDSN_PORT;
        }
        return value;
    }

    /**
     * Returns the FDSN Dataselect URL configured (if any).
     *
     * @return The URL of the Dataselect service or null if no value was
     * configured.
     */
    public static String getFdsnDataselectURL() {
        return Config.getString(Names.FDSN_DATASELECT_URL, null);
    }

    /**
     * Returns the SeedLink server configured (if any).
     *
     * @return The IP address of the server or null if no value was configured.
     */
    public static String getSeedLinkServer() {
        return Config.getString(Names.SEEDLINK_SERVER, null);
    }

    /**
     * Returns the FDSN server port configured of the default value.
     *
     * @return
     */
    public static int getSeedLinkServerPort() {
        int value = Config.getInt(Names.SEEDLINK_PORT, Defaults.SEEDLINK_PORT);
        if (value < 0 || value > 65535) {
            LOG.warn("SeedLink port {} is invalid. Using {}", Defaults.SEEDLINK_PORT);
            value = Defaults.SEEDLINK_PORT;
        }
        return value;
    }

    /**
     * Returns the Winston server configured (if any).
     *
     * @return The IP address of the server or null if no value was configured.
     */
    public static String getWinstonServer() {
        return Config.getString(Names.WINSTON_SERVER, null);
    }

    /**
     * Returns the FDSN server port configured of the default value.
     *
     * @return
     */
    public static int getWinstonServerPort() {
        int value = Config.getInt(Names.WINSTON_PORT, Defaults.WINSTON_PORT);
        if (value < 0 || value > 65535) {
            LOG.warn("Winston port {} is invalid. Using {}", value);
            value = Defaults.WINSTON_PORT;
        }
        return value;
    }

    /**
     * Returns the graph width configured.
     *
     * @return
     */
    public static int getGraphWidth() {
        int value = getInt(Names.GRAPH_WIDTH, Defaults.GRAPH_WIDTH);
        if (value < 200) {
            LOG.info("Graph width {} too small. Using {}", Defaults.GRAPH_WIDTH);
            value = Defaults.GRAPH_WIDTH;
        }
        return value;
    }

    /**
     * Returns the graph height configured.
     *
     * @return
     */
    public static int getGraphHeight() {
        int value = getInt(Names.GRAPH_HEIGHT, Defaults.GRAPH_HEIGHT);
        if (value < 200) {
            LOG.info("Graph height {} too small. Using {}", Defaults.GRAPH_HEIGHT);
            value = Defaults.GRAPH_HEIGHT;
        }
        return value;
    }

    /**
     * Returns the RSAM average window in minutes.
     *
     * @return
     */
    public static int getRSAMAverageWindow() {
        int value = getInt(Names.RSAM_AVERAGE_WINDOW, Defaults.RSAM_AVERAGE_WINDOW);
        if (value < 1) {
            LOG.info("RSAM average window is too small. Using {}", Defaults.RSAM_AVERAGE_WINDOW);
            value = Defaults.RSAM_AVERAGE_WINDOW;
        }
        return value;
    }

    /**
     * Returns the zero tolerance (maximum number of consecutive zeros allowed).
     *
     * @return
     */
    public static int getZeroTolerance() {
        int value = getInt(Names.ZERO_TOLERANCE, Defaults.ZERO_TOLERANCE);
        if (value < 1) {
            LOG.info("zeroTolerance is too small. Using {}", Defaults.ZERO_TOLERANCE);
            value = Defaults.ZERO_TOLERANCE;
        }
        return value;
    }

    /**
     * Returns the web server port.
     *
     * @return
     */
    public static int getWebPort() {
        int value = getInt(Names.WEB_PORT, Defaults.WEB_PORT);
        if (value < 1 || value > 65535) {
            LOG.info("Invalid web port. Using {}", Defaults.WEB_PORT);
            value = Defaults.WEB_PORT;
        }
        return value;
    }

    /**
     * Returns the thread pool size.
     *
     * @return
     */
    public static int getThreadPoolSize() {
        int value = getInt(Names.THREAD_POOL_SIZE, Defaults.THREAD_POOL_SIZE);
        if (value < 2) {
            LOG.info("Setting thread pool size to", Defaults.THREAD_POOL_SIZE);
            value = Defaults.THREAD_POOL_SIZE;
        }
        return value;
    }

    private static int getInt(String property, int defaultValue) {

        String stringProperty = Config.PROPERTIES.getProperty(property);
        if (stringProperty == null || stringProperty.length() < 1) {
            LOG.warn("Empty or invalid {}, using {}", property, defaultValue);
            return defaultValue;
        }

        try {
            return Integer.parseInt(stringProperty);
        } catch (NumberFormatException ex) {
            LOG.error("Non numeric {}, using {}", property, defaultValue);
            return defaultValue;
        }
    }

    private static String getString(String property, String defaultValue) {
        String stringProperty = Config.PROPERTIES.getProperty(property);
        if (stringProperty == null || stringProperty.length() < 1) {
            LOG.warn("Empty or invalid {}, using {}", property, defaultValue);
            return defaultValue;
        } else {
            return stringProperty;
        }

    }

}
