package rsamssam.core;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.config.Config;
import rsamssam.config.Names;
import rsamssam.query.Query;

/**
 * This plotter uses Gnuplot for making the rsam-ssam graphs. Gnuplot MUST be
 * available in the underlaying OS, or, ideally, be packaged along this
 * application inside a Docker container. Gnuplot 5 or newer should be fine.
 *
 * @author Julian Peña.
 */
public class Plotter {

    /**
     * Graph width.
     */
    private final int width;

    /**
     * Graph height.
     */
    private final int height;

    /**
     * This plotter's query.
     */
    private final Query query;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("Plotter");

    // Gnuplot commands and parameter names
    private static final String BASH = "/bin/bash";
    private static final String BASH_ARG = "-c";

    private static final String GNUPLOT = "gnuplot -e";
    private static final String GNUPLOT_ARGS = "\"title ='#title'; "
            + "plotname='#plotname'; "
            + "fftWindow='#fftWindow'; "
            + "cutoff='#cutoff'; "
            + "averagingFactor='#averagingFactor'; "
            + "maxPower='#maxPower'; "
            + "broadbandScale='#broadbandScale'; "
            + "imageWidth='#imageWidth'; imageHeight='#imageHeight'; "
            + "ssam='#ssam'; average_ssam='#average_ssam'; "
            + "rsam='#rsam'; maxFreqs='#maxFreqs'; "
            + "maxFreqsOutput='#maxFreqsOutput'; "
            + "output='#output'\"";

    private static final String SCRIPTS_PATH = "../../../../gnuplot_scripts/";
    private static final String SVG_SCRIPT = "plot_svg";
    private static final String PNG_SCRIPT = "plot_png";

    /**
     * Initializes this plotter instance.
     *
     * @param query
     */
    public Plotter(Query query) {

        this.query = query;

        if (query.isWebQuery()) {
            width = query.getGraphWidth();
            height = query.getGraphHeight();
        } else {
            width = Config.getGraphWidth();
            height = Config.getGraphHeight();
        }
    }

    /**
     * Calls gnuplot in order to create the graph for the given query. This is a
     * blocking and potentially long method that must be run on a separate
     * thread.
     *
     * @param query A Query object.
     * @return True if the graph was created, false otherwise.
     */
    public Boolean plot(Query query) {

        String command = getGnuplotCommand();

        var processBuilder = new ProcessBuilder(BASH, BASH_ARG, command);
        processBuilder.directory(new File(query.getOutputPath()));
        processBuilder.inheritIO();

        try {

            LOG.info("{} Calling gnuplot ...", query.getId());

            Process gnuplot = processBuilder.start();
            int resultCode = gnuplot.waitFor();

            if (resultCode == 0) {
                LOG.info("{} {} done", query.getId(), query.getGraphFileName());
                return true;
            }

            LOG.error("{} Gnuplot exit code: {}", query.getId(), resultCode);

        } catch (IOException | InterruptedException ex) {
            LOG.error("{} Failed to create graph with gnuplot", query.getId());
            LOG.error(ex.getMessage());
        }

        return false;
    }

    /**
     * Makes the actual gnuplot command with all it's arguments. Almost all
     * argument names are self explanatory. ssam, average_ssam and rsam are the
     * paths to the files with the corresponding results, output is the name of
     * the resulting graph.
     *
     *
     * @param query A query object
     * @return A String with the gnuplot command.
     */
    private String getGnuplotCommand() {

        String args = GNUPLOT_ARGS
                .replaceFirst("#output", query.getGraphFileName())
                .replaceFirst("#maxFreqsOutput", query.getMaxFreqsGraphFileName())
                .replaceFirst("#plotname", query.getNSCL())
                .replaceFirst("#imageWidth", width + "")
                .replaceFirst("#imageHeight", height + "")
                .replaceFirst("#title", query.getGraphTitle())
                .replaceFirst("#ssam", query.getSSAMFileName())
                .replaceFirst("#average_ssam", query.getAverageSSAMFileName())
                .replaceFirst("#rsam", query.getRSAMFileName())
                .replaceFirst("#maxFreqs", query.getMaxFreqsFileName())
                .replaceFirst("#fftWindow", query.getCutoffWindowSize().get() + "")
                .replaceFirst("#cutoff", query.getCutoffFrequency() + "")
                .replaceFirst("#averagingFactor", query.getAveraging() + "")
                .replaceFirst("#maxPower", query.getMaxPower() + "");

        // if the station is broadband then activate the log scale for the
        // average ssam graph. Are 'H' and 'B' prefixes a standard  indicator of
        // a broadband sensor? ... probably not ... anyway
        if (query.getC().startsWith("H") || query.getC().startsWith("B")) {
            args = args.replaceFirst("#broadbandScale", "true");
        } else {
            args = args.replaceFirst("#broadbandScale", "false");
        }

        String command = GNUPLOT + " " + args + " ";

        switch (query.getGraphFormat()) {
            default:
            case Names.GRAPH_FORMAT_PNG:
                command += SCRIPTS_PATH + PNG_SCRIPT;
                break;
            case Names.GRAPH_FORMAT_SVG:
                command += SCRIPTS_PATH + SVG_SCRIPT;
                break;
        }

        return command;
    }

}
