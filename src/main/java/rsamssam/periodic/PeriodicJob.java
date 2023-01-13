package rsamssam.periodic;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

import rsamssam.Main;

/**
 * Triggers the generation of rsam-ssam graphs for all queries in the
 * queries.json file.
 *
 * @author Julian Peña.
 */
public class PeriodicJob implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        PeriodicGrapher periodicGrapher = new PeriodicGrapher(Main.VERTX);
        periodicGrapher.makeGraphs();
    }
}
