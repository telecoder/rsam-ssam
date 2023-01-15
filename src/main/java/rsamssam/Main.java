package rsamssam;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.config.Config;
import rsamssam.config.Names;
import rsamssam.web.WebServer;
import rsamssam.periodic.PeriodicJob;

/**
 * Entry point for rsam-ssam.
 *
 * @author Julian Peña.
 */
public class Main {

    // vertx logging stuff
    static {
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");
        System.setProperty("logback.configurationFile", "logback.xml");
    }

    /**
     * The vertx instance. Public access is only required for the quartz
     * scheduler.
     */
    public static Vertx VERTX;

    /**
     * Our logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger("Main");
    
    public static void main(String[] args) {

        LOG.info("Starting");
        LOG.info("Thread pool size: {}", Config.getThreadPoolSize());

        VERTX = Vertx.vertx();

        // if the output folder does not exists, then create it
        VERTX.fileSystem().mkdir(Names.OUTPUT_DIR);

        VERTX
                .deployVerticle(new WebServer())
                .onFailure(f -> {
                    LOG.error("Failed to start web server");
                    LOG.error("Exiting");
                    System.exit(1);
                });

        schedulePeriodicGraphs();
    }

    private static Future schedulePeriodicGraphs() {

        LOG.info("Scheduling periodic graph generation");

        Promise promise = Promise.promise();

        int replotInterval = Config.getReplotInterval();
        LOG.info("Replot interval: {} minutes", replotInterval);

        try {

            SchedulerFactory factory = new StdSchedulerFactory();
            Scheduler scheduler = factory.getScheduler();
            JobDetail job = JobBuilder
                    .newJob(PeriodicJob.class)
                    .withIdentity("job", "group")
                    .build();

            //Trigger every replotInterval minutes
            String cron = "0 0/" + replotInterval + " * 1/1 * ? *";

            CronTrigger trigger = TriggerBuilder
                    .newTrigger()
                    .withIdentity("trigger", "group")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                    .build();

            scheduler.scheduleJob(job, trigger);
            scheduler.start();

            promise.complete();

        } catch (SchedulerException ex) {
            LOG.error("Failed to start quartz scheduler");
            LOG.error(ex.getMessage());
            promise.fail(ex.getMessage());
        }

        return promise.future();
    }

}
