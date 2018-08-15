package org.phoebus.alarm.logging;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.phoebus.util.shell.CommandShell;

@SuppressWarnings("nls")
public class AlarmLoggingService {

    /** Alarm system logger */
    public static final Logger logger = Logger.getLogger(AlarmLoggingService.class.getPackageName());
    private static final ExecutorService Scheduler = Executors.newScheduledThreadPool(4);

    private static void help()
    {
        // http://patorjk.com/software/taag/#p=display&f=Epic&t=Alarm%20Logger
        System.out.println(" _______  _        _______  _______  _______    _        _______  _______  _______  _______  _______ ");
        System.out.println("(  ___  )( \\      (  ___  )(  ____ )(       )  ( \\      (  ___  )(  ____ \\(  ____ \\(  ____ \\(  ____ )");
        System.out.println("| (   ) || (      | (   ) || (    )|| () () |  | (      | (   ) || (    \\/| (    \\/| (    \\/| (    )|");
        System.out.println("| (___) || |      | (___) || (____)|| || || |  | |      | |   | || |      | |      | (__    | (____)|");
        System.out.println("|  ___  || |      |  ___  ||     __)| |(_)| |  | |      | |   | || | ____ | | ____ |  __)   |     __)");
        System.out.println("| (   ) || |      | (   ) || (\\ (   | |   | |  | |      | |   | || | \\_  )| | \\_  )| (      | (\\ (   ");
        System.out.println("| )   ( || (____/\\| )   ( || ) \\ \\__| )   ( |  | (____/\\| (___) || (___) || (___) || (____/\\| ) \\ \\__");
        System.out.println("|/     \\|(_______/|/     \\||/   \\__/|/     \\|  (_______/(_______)(_______)(_______)(_______/|/   \\__/");
        System.out.println();
        System.out.println("Command-line arguments:");
        System.out.println();
        System.out.println("-help                                    - This text");
        System.out.println("-topics   Accelerator                    - Alarm topics to be logged, they can be defined as a comma separated list");
        System.out.println("-es_host  localhost                      - elastic server host");
        System.out.println("-es_port  9200                           - elastic server port");
        System.out.println("-bootstrap.servers localhost:9092        - Kafka server address");
        System.out.println("-properties /opt/alarm_logger.propertier - Properties file to be used (instead of command line arguments)");
        System.out.println("-logging logging.properties              - Load log settings");
        System.out.println();
    }

    private static final String COMMANDS =
            "Commands:\n" +
            "\thelp             - Show help.\n" +
            "\tshutdown         - Shut alarm logger down and exit.\n";

    private static final CountDownLatch done = new CountDownLatch(1);

    private static boolean handleShellCommands(final String... args) throws Throwable
    {
        if (args.length == 1  &&  args[0].startsWith("shut"))
        {
            done.countDown();
            return true;
        }
        return false;
    }

    public static void main(final String[] original_args) throws Exception {
        LogManager.getLogManager().readConfiguration(AlarmLoggingService.class.getResourceAsStream("/alarm_logger_logging.properties"));

        // load the default properties
        final Properties properties = PropertiesHelper.getProperties();

        // Handle arguments
        final List<String> args = new ArrayList<>(List.of(original_args));
        final Iterator<String> iter = args.iterator();
        try {
            while (iter.hasNext()) {

                final String cmd = iter.next();
                if (cmd.startsWith("-h")) {
                    help();
                    return;
                } else if (cmd.equals("-properties")) {
                    if (!iter.hasNext())
                        throw new Exception("Missing -properties properties file");
                    iter.remove();
                    try(FileInputStream file = new FileInputStream(iter.next());){
                        properties.load(file);
                    } catch(FileNotFoundException e) {
                        System.out.println();
                        e.printStackTrace();
                    }
                    iter.remove();
                } else if (cmd.equals("-topics")) {
                    if (!iter.hasNext())
                        throw new Exception("Missing -topics topic name");
                    iter.remove();
                    properties.put("alarm_topics",iter.next());
                    iter.remove();
                } else if (cmd.equals("-es_host")) {
                    if (!iter.hasNext())
                        throw new Exception("Missing -es_host hostname");
                    iter.remove();
                    properties.put("es_host",iter.next());
                    iter.remove();
                } else if (cmd.equals("-es_port")) {
                    if (!iter.hasNext())
                        throw new Exception("Missing -es_port port number");
                    iter.remove();
                    properties.put("es_port",iter.next());
                    iter.remove();
                } else if (cmd.equals("-bootstrap.servers")) {
                    if (!iter.hasNext())
                        throw new Exception("Missing -bootstrap.servers kafaka server addresss");
                    iter.remove();
                    properties.put("bootstrap.servers",iter.next());
                    iter.remove();
                } else if (cmd.equals("-logging"))
                {
                    if (! iter.hasNext())
                        throw new Exception("Missing -logging file name");
                    iter.remove();
                    final String filename = iter.next();
                    iter.remove();
                    LogManager.getLogManager().readConfiguration(new FileInputStream(filename));
                } else
                    throw new Exception("Unknown option " + cmd);
            }
        } catch (Exception ex) {
            help();
            System.out.println();
            ex.printStackTrace();
            return;
        }

        logger.info("Alarm Logging Service (PID " + ProcessHandle.current().pid() + ")");

        logger.info("Properties:");
        properties.forEach((k, v) -> { logger.info(k + ":" + v); });

        // Read list of Topics
        final List<String> topicNames = Arrays.asList(properties.getProperty("alarm_topics").split(","));
        logger.info("Starting logger for '..State': " + topicNames);

        // Check all the topic index already exist.
        if (topicNames.stream().allMatch(topic -> {
            return ElasticClientHelper.getInstance().indexExists(topic.toLowerCase() + "_alarms");
        })) {
            logger.info("found elastic indexes for all alarm topics");
        } else {
            logger.severe("ERROR: elastic index missing for the configured topics.");
        }

        // Start a new stream consumer for each topic
        topicNames.forEach(topic -> {
            Scheduler.execute(new AlarmStateLogger(topic));
        });

        // Wait in command shell until closed
        final CommandShell shell = new CommandShell(COMMANDS, AlarmLoggingService::handleShellCommands);
        shell.start();
        done.await();
        shell.stop();

        System.out.println("\nDone.");
        // TODO Shut AlarmStateLoggers down?
        System.exit(0);
    }
}
