package reproduction.dcidsubmit;

import java.io.File;

import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.spi.LoggerContext;

/**
 * Abstract base class for the individual analysis steps that run FlowDroid
 * 
 * 
 *
 */
public abstract class AbstractFlowDroidStep {

	protected static Logger logger;

	protected static final String OPTION_APK_FILE = "a";
	protected static final String OPTION_PLATFORM_DIR = "p";
	protected static final String OPTION_CALLGRAPH_TIMEOUT = "c";
	protected static final String OPTION_DATAFLOW_TIMEOUT = "m";
	protected static final String OPTION_TARGET_DIR = "t";
	protected static final String OPTION_LIBRARY_FILE = "l";
	protected static final String OPTION_SOURCES_SINKS = "r";
	protected static final String OPTION_MAX_PARALLEL = "parallel";
	protected static final String OPTION_HEAPSPACE = "heapspace";
	protected static final String OPTION_MAXCORES = "maxcores";

	protected static final Options options = new Options();

	static {
		initializeCommandLineOptions();
	}

	/**
	 * Initializes the set of available command-line options
	 */
	protected static void initializeCommandLineOptions() {
		options.addOption(OPTION_APK_FILE, "apkfiles", true, "The file with the listing of APK files to analyze");
		options.addOption(OPTION_PLATFORM_DIR, "platformdir", true,
				"The directory that contains the Android platform JARs");
		options.addOption(OPTION_CALLGRAPH_TIMEOUT, "callgraphtimeout", true,
				"The timeout for the callgraph analysis in seconds");
		options.addOption(OPTION_DATAFLOW_TIMEOUT, "dataflowtimeout", true,
				"The timeout for the data flow analysis in seconds");
		options.addOption(OPTION_TARGET_DIR, "targetdir", true,
				"The target directory in which to save the callgraph files");
		options.addOption(OPTION_LIBRARY_FILE, "libraryfile", true, "The file with the library package names");
		options.addOption(OPTION_SOURCES_SINKS, "sourcessinks", true, "The source/sink definitions file");

		options.addOption(null, OPTION_MAXCORES, true, "The maximum number of cores for each instance");
		options.addOption(null, OPTION_HEAPSPACE, true, "The heap space");
		options.addOption(null, OPTION_MAX_PARALLEL, true, "The maximum parallel count");
	}

	/**
	 * Initializes the logging framework
	 */
	protected static void initializeLogging() {
		// Explicitly load log configuration
		File logConfigFile = new File("log4j2.properties");
		if (logConfigFile.exists()) {
			System.out.println(String.format("Loading log configuration from %s", logConfigFile.getAbsolutePath()));
			LoggerContext context = Configurator.initialize(null, logConfigFile.toURI().toString());
			if (context == null)
				System.err.println("Could not load log configuration file");
			else
				logger = context.getLogger(AbstractFlowDroidStep.class);
		}
		if (logger == null)
			logger = LogManager.getLogger(AbstractFlowDroidStep.class);
	}

}
