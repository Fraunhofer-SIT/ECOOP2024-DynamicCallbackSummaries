package reproduction.dcidsubmit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;

/**
 * Tool for running FlowDroid's callgraph analysis
 * 
 * 
 *
 */
public class FlowDroidRunner extends AbstractFlowDroidStep {

	public static void main(String[] args) throws InterruptedException {
		initializeLogging();

		// We need proper parameters
		final HelpFormatter formatter = new HelpFormatter();
		if (args.length == 0) {
			formatter.printHelp("java -jar JobSubmissionTool.jar [OPTIONS]", options);
			return;
		}

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmd = parser.parse(options, args);

			// Parse the command-line options
			String platformDir = cmd.getOptionValue(OPTION_PLATFORM_DIR);
			String callgraphTimeout = cmd.getOptionValue(OPTION_CALLGRAPH_TIMEOUT);
			String dataflowTimeout = cmd.getOptionValue(OPTION_DATAFLOW_TIMEOUT);
			File targetDir = new File(cmd.getOptionValue(OPTION_TARGET_DIR));
			String libraryFile = cmd.getOptionValue(OPTION_LIBRARY_FILE);
			String sourcesSinksFile = cmd.getOptionValue(OPTION_SOURCES_SINKS);
			String parallel = cmd.getOptionValue(OPTION_MAX_PARALLEL);
			String heapspace = cmd.getOptionValue(OPTION_HEAPSPACE);
			String maxcores = cmd.getOptionValue(OPTION_MAXCORES);
			if (parallel == null) {
				parallel = "1";
			}

			// Get the listing of app files
			String appFiles = cmd.getOptionValue(OPTION_APK_FILE);
			if (appFiles == null || appFiles.isEmpty()) {
				logger.error("No APK file listing specified");
				return;
			}

			// Launch the individual processes
			List<String> apkFiles = Files.readAllLines(Paths.get(appFiles));
			final AtomicInteger ID = new AtomicInteger();
			ExecutorService executionService = Executors.newFixedThreadPool(Math.max(1, Integer.parseInt(parallel)),
					new ThreadFactory() {

						@Override
						public Thread newThread(final Runnable r) {
							Thread thr = new Thread(r);
							thr.setName("Parallel Thread #" + ID.incrementAndGet());
							thr.setDaemon(true);
							return thr;
						}
					});

			for (String apkFile : apkFiles) {
				executionService.execute(new Runnable() {

					@Override
					public void run() {
						List<String> arguments = new ArrayList<>();
						arguments.add("java");
						if (heapspace != null)
							arguments.add("-Xmx" + heapspace);
						arguments.add("-cp");
						arguments.add("JobSubmissionTool-jar-with-dependencies.jar");
						arguments.add("reproduction.dcidsubmit.FlowDroidSecondStep");
						arguments.add("-p");
						arguments.add(platformDir);
						arguments.add("-a");
						arguments.add(apkFile);
						if (callgraphTimeout != null && !callgraphTimeout.isEmpty()) {
							arguments.add("-c");
							arguments.add(callgraphTimeout);
						}
						if (dataflowTimeout != null && !dataflowTimeout.isEmpty()) {
							arguments.add("-m");
							arguments.add(dataflowTimeout);
						}
						arguments.add("-t");
						arguments.add(targetDir.getAbsolutePath());
						if (maxcores != null) {
							arguments.add("--" + OPTION_MAXCORES);
							arguments.add(maxcores);
						}
						if (libraryFile != null && !libraryFile.isEmpty()) {
							arguments.add("-l");
							arguments.add(libraryFile);
						}
						if (sourcesSinksFile != null && !sourcesSinksFile.isEmpty()) {
							arguments.add("-r");
							arguments.add(sourcesSinksFile);
						}

						ProcessBuilder pb = new ProcessBuilder(arguments.toArray(i -> new String[i]));
						logger.info("Running command {}", pb.command().toString());
						try {
							String name = new File(apkFile).getName();
							pb.redirectError(new File(targetDir, "stderr-" + name));
							pb.redirectOutput(new File(targetDir, "stdout-" + name));
							Process e = pb.start();
							e.waitFor();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
			executionService.shutdown();
			executionService.awaitTermination(100, TimeUnit.HOURS);

		} catch (ParseException ex) {
			formatter.printHelp("java -jar JobSubmissionTool.jar [OPTIONS]", options);
			return;
		} catch (IOException e) {
			logger.error("IO error during correlation analysis", e);
		}
	}

}
