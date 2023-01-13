package rsamssam.query;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import rsamssam.config.Names;

/**
 * This class is the main abstraction for this application, it represents a
 * query for a rsam and ssam graph for an individual seismological station in
 * some time range. The station is defined by a NSCL.
 *
 * @author Julian Peña.
 */
public class Query {

    /**
     * Default window size;
     */
    public static final int DEFAULT_WINDOW_SIZE = 4096;

    /**
     * Default window function;
     */
    public static final String DEFAULT_WINDOW_FUNCTION = "hann";

    /**
     * Default cutoff frequency in HZ;
     */
    public static final int DEFAULT_CUTOFF_FREQUENCY = 25;

    /**
     * Default max Power in dB;
     */
    public static final int DEFAULT_MAX_POWER = 120;

    /**
     * Default graph format;
     */
    public static final String DEFAULT_GRAPH_FORMAT = "svg";

    /**
     * Default graph width;
     */
    public static final int DEFAULT_GRAPH_WIDTH = 1200;

    /**
     * Default graph height;
     */
    public static final int DEFAULT_GRAPH_HEIGHT = 600;

    /**
     * Internal identification for this query. Only relevant for debugging the
     * logs file.
     */
    private final String id = UUID.randomUUID().hashCode() + "";

    /**
     * Data source type.
     */
    private QueryType type = QueryType.fdsn;

    /**
     * Network identifier.
     */
    private final String N;

    /**
     * Station identifier.
     */
    private final String S;

    /**
     * Channel identifier.
     */
    private final String C;

    /**
     * Location code.
     */
    private final String L;

    /**
     * Desired start time for the query.
     */
    private long from;

    /**
     * Desired end time for the query.
     */
    private long to;

    /**
     * rsam-ssam graph output format.
     */
    private String graphFormat = Names.GRAPH_FORMAT_PNG;

    /**
     * rsam-ssam graph width.
     */
    private int graphWidth = 1200;

    /**
     * rsam-ssam graph height.
     */
    private int graphHeight = 600;

    /**
     * Max power for the output graph. This is, the maximum power associated
     * with the top color in the scale.
     */
    private int maxPower = 120;

    /**
     * Window function for pre-processing sample bins before performing the FFT.
     */
    private String windowFunction = DEFAULT_WINDOW_FUNCTION;

    /**
     * Window size for performing rsam and FFT computations. This size
     * determines the output frequency and time resolutions. This value MUST be
     * a power of 2.
     */
    private int windowSize = DEFAULT_WINDOW_SIZE;

    /**
     * Desired cutoff frequency for the graphs. Default is 25Hz.
     */
    private int cutoffFrequency = DEFAULT_CUTOFF_FREQUENCY;

    /**
     * Ideally, the raw samples inside the miniseed packets must be corrected by
     * applying a response factor. Default is 1.
     */
    private double responseFactor = 1;

    /**
     * is this a query made via the web interface?.
     */
    private boolean webQuery = false;

    /**
     * Query's metadata (sps and effective start time of the samples). This data
     * is not known at query creation time, but after the wave server start
     * sending the data.
     */
    private Metadata metadata;

    /**
     * Constructor. Default values are as follows: from (the start time for the
     * query): The current day at 00:00:00 AM. to (the end time for the query):
     * The current day at midnight minus 1 second (23:59:59 PM). imageFormat:
     * png. imageWidth: 1200 px. ImageHeight: 600 px. windowFunction: Hann.
     * windowSize: 8192. sps (samples per second): 100. webQuery (is this a web
     * query?): false. responseFactor: 1. id: this is a random value
     *
     * @param S Station identifier.
     * @param C Channel identifier.
     * @param N Networks identifier.
     * @param L Location code.
     */
    public Query(String S, String C, String N, String L) {

        this.N = N;
        this.S = S;
        this.C = C;
        this.L = L;

        LocalDateTime now = LocalDateTime
                .now(ZoneId.of(ZoneOffset.UTC.getId()))
                .truncatedTo(ChronoUnit.DAYS);

        this.from = now.toInstant(ZoneOffset.UTC).toEpochMilli();
        this.to = now.plusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * Returns this query's metadata, if present.
     *
     * @return
     */
    public Optional<Metadata> getMetadata() {
        return Optional.ofNullable(metadata);
    }

    /**
     * Sets the metadata for this query.
     *
     * @param metadata
     */
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Does this query has metadata?.
     *
     * @return
     */
    public boolean hasMetadata() {
        return metadata != null;
    }

    /**
     * Returns the id of the query.
     *
     * @return the id of the query.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the data source type for this query.
     *
     * @return
     */
    public QueryType getType() {
        return type;
    }

    /**
     * Sets the data source type for this query.
     *
     * @param type
     */
    public void setType(QueryType type) {
        this.type = type;
    }

    /**
     * Returns the S value of the query (the station code).
     *
     * @return The S value of the query (the station code).
     */
    public String getS() {
        return S;
    }

    /**
     * Returns the C value of the query (the station component).
     *
     * @return The C value of the query (the station component).
     */
    public String getC() {
        return C;
    }

    /**
     * Returns the N value of the query (the station network).
     *
     * @return The N value of the query (the station network).
     */
    public String getN() {
        return N;
    }

    /**
     * Returns the L value of the query (the station location).
     *
     * @return The L value of the query (the station location).
     */
    public String getL() {
        return L;
    }

    /**
     *
     * @return The cutoff frequency. If sps information is already available for
     * this query, then this method verifies that the cutoff frequency is
     * consistent with Nyquist, if not, then Nyquist frequency will be returned.
     */
    public int getCutoffFrequency() {

        if (metadata == null) {
            return cutoffFrequency;
        }

        int nyquist = metadata.sps() / 2;
        if (cutoffFrequency > nyquist) {
            cutoffFrequency = nyquist;
        }

        return cutoffFrequency;
    }

    /**
     *
     * @return The maximum power for the output ssam graph.
     */
    public int getMaxPower() {
        return maxPower;
    }

    /**
     *
     * @return Given the window size, return the corresponding cutoff window
     * size. If the query's associated metadata is not set yet then an empty
     * optional is returned.
     */
    public Optional<Integer> getCutoffWindowSize() {

        if (metadata == null) {
            return Optional.empty();
        }

        return Optional.of(getCutoffFrequency() * windowSize / metadata.sps());
    }

    /**
     * Return the averaging factor for this query (how many bins should be
     * reduce to 1). By default, this is equal to the number of days between the
     * 'from' and 'to' values.
     *
     * @return
     */
    public long getAveraging() {

        if (durationInDays() > 1) {
            return durationInDays();
        }

        return 1;
    }

    /**
     * The time step to be used in the resulting rsam-ssam graph. This value
     * depends on the sps value within the query's metadata, thus, if this query
     * has no metadata yet then an empty optional is returned.
     *
     * @return
     */
    public Optional<Long> getTimestep() {

        if (!hasMetadata()) {
            return Optional.empty();
        }

        return Optional.of(
                Math.round(1000d * getAveraging() * windowSize / metadata.sps())
        );

    }

    /**
     * Sets the query's cutoff frequency.
     *
     * @param cutoffFrequency the query's cutoff frequency.
     * @return A reference to self.
     */
    public Query setCutoffFrequency(int cutoffFrequency) {
        this.cutoffFrequency = cutoffFrequency;
        return this;
    }

    /**
     * Sets the query's desired maximum power in the ssam graph.
     *
     * @param maxPower the query's maximum power for the ssam graph.
     * @return A reference to self.
     */
    public Query setMaxPower(int maxPower) {
        this.maxPower = maxPower;
        return this;
    }

    /**
     * Sets the desired start time for the query (the actual data can start at a
     * different time). This is expressed as milliseconds since epoch.
     *
     * @param from the query's start time.
     * @return A reference to self.
     */
    public Query setFrom(long from) {
        this.from = from;
        return this;
    }

    /**
     * Returns the query's from (the desired start time) expressed as
     * milliseconds since epoch.
     *
     * @return The query's from (the desired start time).
     */
    public long getFrom() {
        return from;
    }

    /**
     * Sets the desired end time for the query (the actual data can end at a
     * different time).This is expressed as milliseconds since epoch.
     *
     * @param to the desired query's end time.
     * @return A reference to self.
     */
    public Query setTo(long to) {
        this.to = to;
        return this;
    }

    /**
     * Returns the query's to (the desired end time) expressed as milliseconds
     * since epoch.
     *
     * @return The query's to (the desired end time).
     */
    public long getTo() {
        return to;
    }

    /**
     * Change the type of the query.
     *
     * @param webQuery True is the query originated from a web browser, false if
     * the query is defined in the queries.json file.
     * @return A reference to self.
     */
    public Query setWebQuery(boolean webQuery) {
        this.webQuery = webQuery;
        return this;
    }

    /**
     * Indicates wheter the query originated from a web browser or not.
     *
     * @return True if this is a web query, false otherwise.
     */
    public boolean isWebQuery() {
        return webQuery;
    }

    /**
     * Sets the desired image format for the output graph.
     *
     * @param graphFormat The image format.
     * @return A reference to self.
     */
    public Query setGraphFormat(String graphFormat) {
        if (null != graphFormat) {
            this.graphFormat = graphFormat;
        }
        return this;
    }

    /**
     * Returns the desired output graph format.
     *
     * @return The desired output graph format.
     */
    public String getGraphFormat() {
        return graphFormat;
    }

    /**
     * Returns the desired output graph width in pixels.
     *
     * @return The desired output graph width in pixels.
     */
    public int getGraphWidth() {
        return graphWidth;
    }

    /**
     * Sets the desired image width for the output graph.
     *
     * @param graphWidth The image width.
     * @return A reference to self.
     */
    public Query setGraphWidth(int graphWidth) {
        this.graphWidth = graphWidth;
        return this;
    }

    /**
     * Returns the desired output graph height in pixels.
     *
     * @return The desired output graph height in pixels.
     */
    public int getGraphHeight() {
        return graphHeight;
    }

    /**
     * Sets the desired image height for the output graph.
     *
     * @param graphHeight The image height.
     * @return A reference to self.
     */
    public Query setGraphHeight(int graphHeight) {
        this.graphHeight = graphHeight;
        return this;
    }

    /**
     * Returns the query's window function for the fft transform.
     *
     * @return The query's window function for the fft transform.
     */
    public String getWindow() {
        return windowFunction;
    }

    /**
     * Sets the window function for the fft transform.
     *
     * @param windowFunction The window function name.
     * @return A reference to this query.
     */
    public Query setWindowFunction(String windowFunction) {
        if (windowFunction != null) {
            this.windowFunction = windowFunction;
        }
        return this;
    }

    /**
     * Return the query's window size for the fft transform.
     *
     * @return
     */
    public int getWindowSize() {
        return windowSize;
    }

    /**
     * Sets the window size for the fft transform.
     *
     * @param windowSize The window function size.
     * @return A reference to this query.
     */
    public Query setWindowSize(Integer windowSize) {
        if (windowSize != null) {
            this.windowSize = windowSize;
        }
        return this;
    }

    /**
     * Returns the query's response factor (instrumental response factor).
     *
     * @return
     */
    public double getResponseFactor() {
        return responseFactor;
    }

    /**
     * Sets the query's response factor (instrumental response factor),
     *
     * @param responseFactor
     */
    public void setResponseFactor(double responseFactor) {
        this.responseFactor = responseFactor;
    }

    /**
     * Returns the SCNL concatenated with underscores.
     *
     * @return
     */
    public String getNSCL() {
        String NSCL = N + "_" + S + "_" + C;
        if (L != null & !L.isBlank()) {
            NSCL += "_" + L;
        }
        return NSCL;
    }

    /**
     * Returns the SCNL concatenated with spaces.
     *
     * @return
     */
    public String getNSCLPretty() {
        String NSCL = N + " " + S + " " + C;
        if (L != null & !L.isBlank()) {
            NSCL += " " + L;
        }
        return NSCL;
    }

    /**
     * Helper method. Given an epoch date, return the LocalDateTime equivalent
     * object.
     *
     * @param date milliseconds since epoch.
     * @return LocalDataTime for the given date.
     */
    private LocalDateTime getLocalDateTime(long date) {
        Instant instant = Instant.ofEpochMilli(date);
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * Helper method. Given a date returns it in yyyy-MM-dd HH:mm:ss format.
     *
     * @param date milliseconds since epoch.
     * @return A String date.
     */
    private String getDateAsString(long date) {
        //var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime localDateTime = getLocalDateTime(date);
        return localDateTime.format(formatter);
    }

    /**
     * Returns name of the file with the ssam results.
     *
     * @return
     */
    public String getSSAMFileName() {
        return getNSCLPretty()
                + " " + getDateAsString(from)
                + " " + Names.SSAM_FILENAME_SUFIX;
    }

    /**
     * Returns name of the file with the rsam results.
     *
     * @return
     */
    public String getRSAMFileName() {
        return getNSCLPretty()
                + " " + getDateAsString(from)
                + " " + Names.RSAM_FILENAME_SUFIX;
    }

    /**
     * Returns name of the file with the average ssam result.
     *
     * @return
     */
    public String getAverageSSAMFileName() {
        return getNSCLPretty()
                + " " + getDateAsString(from)
                + " " + Names.AVERAGE_SSAM_FILENAME_SUFIX;
    }

    /**
     * Returns the file name for the maximum frequencies results.
     *
     * @return
     */
    public String getMaxFreqsFileName() {
        return getNSCLPretty()
                + " " + getDateAsString(from)
                + " " + Names.MAX_FREQ_FILENAME_SUFIX;
    }

    /**
     * Returns the title for the rsam-ssam graph.
     *
     * @return
     */
    public String getGraphTitle() {

        String title = getNSCLPretty() + "  |  ";

        if (hasMetadata()) {
            title += metadata.sps() + " Hz  |  ";
        }

        title += getDateAsString(from);

        if (durationInDays() > 1) {
            title += "  -  " + getDateAsString(to);
        }

        return title + "  UTC";
    }

    /**
     * Return path for this query's output files.
     *
     * @return
     */
    public String getOutputPath() {

        if (webQuery) {
            return Names.WEB_DIRECTORY + "/"
                    + getNSCLPretty() + "/"
                    + getDateAsString(from);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        LocalDateTime localDateTime = getLocalDateTime(from);

        return Names.OUTPUT_DIR + "/" + localDateTime.format(formatter);
    }

    /**
     * Returns the path of the rsam-ssam graph.
     *
     * @return
     */
    public String getGraphPath() {
        return getOutputPath() + "/" + getGraphFileName();
    }

    /**
     * Returns the file name for the rsam-ssam graph.
     *
     * @return
     */
    public String getGraphFileName() {

        String filename = getNSCLPretty() + " " + getDateAsString(from);

        if (durationInDays() > 1) {
            filename += " " + getDateAsString(to) + " ";
        }

        switch (graphFormat) {
            default:
            case Names.GRAPH_FORMAT_SVG:
                filename += ".svg";
                break;
            case Names.GRAPH_FORMAT_PNG:
                filename += ".png";
                break;
        }

        return filename;
    }

    /**
     * Returns the file name for the rsam-ssam graph.
     *
     * @return
     */
    public String getMaxFreqsGraphFileName() {
        String plotFileName = getNSCLPretty()
                + " " + getDateAsString(from)
                + " " + Names.MAX_FREQS;
        switch (graphFormat) {
            default:
            case Names.GRAPH_FORMAT_SVG:
                plotFileName += ".svg";
                break;
            case Names.GRAPH_FORMAT_PNG:
                plotFileName += ".png";
                break;
        }
        return plotFileName;
    }

    /**
     * A valid Query must be targeted at a single channel and have well defined
     * time boundaries.
     *
     * @return True if the query is valid, false otherwise.
     */
    public boolean isValid() {

        if (N == null || N.isBlank() || N.contains("*") || N.contains("?")) {
            return false;
        }

        if (S == null || S.isBlank() || S.contains("*") || S.contains("?")) {
            return false;
        }

        if (C == null || C.isBlank() || C.contains("*") || C.contains("?")) {
            return false;
        }

        if (L != null && !L.isBlank()) {
            if (L.contains("*") || L.contains("?")) {
                return false;
            }
        }

        return to > from;
    }

    /**
     * Does this query needs the downloaded data to be decompressed?
     *
     * @return
     */
    public boolean needsDecompression() {
        return type != QueryType.winston;
    }

    public long durationInDays() {
        return Duration.of(to - from, ChronoUnit.MILLIS).toDays();
    }

    @Override
    public String toString() {
        return getNSCLPretty()
                + " " + type.name()
                + " " + Instant.ofEpochMilli(from)
                + " " + Instant.ofEpochMilli(to);
    }

}
