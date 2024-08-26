package reproduction.dcidsubmit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;
import org.xmlpull.v1.XmlPullParserException;

import soot.Body;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.PathBuildingAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.PathReconstructionMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.config.SootConfigForAndroid;
import soot.jimple.infoflow.cfg.DefaultBiDiICFGFactory;
import soot.jimple.infoflow.methodSummary.data.provider.EagerSummaryProvider;
import soot.jimple.infoflow.methodSummary.taintWrappers.SummaryTaintWrapper;
import soot.jimple.infoflow.results.DataFlowResult;
import soot.jimple.infoflow.results.InfoflowPerformanceData;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.options.Options;

/**
 * Main class for the second step in the FlowDroid-based callgraph analysis
 * 
 * 
 *
 */
public class FlowDroidSecondStep extends AbstractFlowDroidStep {

	protected static IInfoflowCFG icfg;

	public static void main(String[] args) throws URISyntaxException {
		// while (true)
		{
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
				File targetFile = new File(cmd.getOptionValue(OPTION_TARGET_DIR));
				String sourcesSinksFile = cmd.getOptionValue(OPTION_SOURCES_SINKS);
				String libraryFile = cmd.getOptionValue(OPTION_LIBRARY_FILE);
				String maxcores = cmd.getOptionValue(OPTION_MAXCORES);

				// Construct the callgraphs
				String appFile = cmd.getOptionValue(OPTION_APK_FILE);
				if (appFile == null || appFile.isEmpty()) {
					logger.error("No APK file specified");
					return;
				}

				int numFlows = 0;
				int dataFlowSeconds = 0;
				long callgraphSeconds = 0;
				int numSources = 0;
				int numSinks = 0;
				long numEdges = 0;

				// Initialize FlowDroid
				SetupApplication setupApp = initializeFlowDroid(platformDir, appFile, libraryFile, targetFile,
						callgraphTimeout);

				// Run the data flow analysis
				InfoflowAndroidConfiguration config = setupApp.getConfig();
				if (maxcores != null)
					config.setMaxThreadNum(Integer.parseInt(maxcores));
				if (sourcesSinksFile != null && !sourcesSinksFile.isEmpty())
					config.getAnalysisFileConfig().setSourceSinkFile(sourcesSinksFile);
				if (dataflowTimeout != null && !dataflowTimeout.isEmpty())
					config.setDataFlowTimeout(Integer.valueOf(dataflowTimeout));
				try {
					config.setTaintAnalysisEnabled(true);
					long beforeDF = System.currentTimeMillis();
					InfoflowResults results = setupApp.runInfoflow();
					dataFlowSeconds = (int) ((System.currentTimeMillis() - beforeDF) / 1000);
					if (results != null) {
						numFlows = results.numConnections();
						InfoflowPerformanceData performanceData = results.getPerformanceData();
						if (performanceData != null) {
							callgraphSeconds = performanceData.getCallgraphConstructionSeconds();
							dataFlowSeconds = performanceData.getTaintPropagationSeconds();
							numEdges = performanceData.getEdgePropagationCount();
						}

						Set<Stmt> sources = setupApp.getCollectedSources();
						numSources = sources == null ? 0 : sources.size();
						Set<Stmt> sinks = setupApp.getCollectedSinks();
						numSinks = sinks == null ? 0 : sinks.size();

						if (targetFile != null && results != null && results.getResultSet() != null) {
							File f = new File(targetFile, "DFResults-" + FilenameUtils.getName(appFile));
							f.mkdirs();
							int i = 1;
							for (DataFlowResult r : results.getResultSet()) {
								try (PrintWriter pw = new PrintWriter(new FileOutputStream(new File(f, "DF" + i)))) {
									Stmt s = r.getSource().getStmt();
									printMethod(f, icfg.getMethodOf(s));
									pw.append("Source: ").append(r.getSource().toString()).append("\n");
									pw.append(s + " in " + icfg.getMethodOf(s).getSignature());
									pw.append("\n");
									pw.append("Sink: ").append(r.getSink().getStmt() + " in "
											+ icfg.getMethodOf(r.getSink().getStmt()).getSignature());
									pw.append("\n\n");
									Stmt[] stmts = r.getSource().getPath();
									for (Stmt d : stmts) {
										SootMethod mm = icfg.getMethodOf(d);
										pw.append(mm.getSignature() + ": " + d);
										pw.append("\n");
										printMethod(f, mm);
									}
								}

								i++;
							}
						}
					}
				} catch (XmlPullParserException e) {
					logger.error("Strange parser error in FlowDroid", e);
				}

			} catch (ParseException ex) {
				formatter.printHelp("java -jar JobSubmissionTool.jar [OPTIONS]", options);
				return;
			} catch (IOException e) {
				logger.error("Could not read APK file", e);
			}
		}
	}

	private static void printMethod(File f, SootMethod methodOf) {
		if (!methodOf.isConcrete())
			return;
		File p = new File(f, methodOf.getSignature());
		try (FileOutputStream fos = new FileOutputStream(p)) {
			Body bd = methodOf.retrieveActiveBody();
			fos.write(bd.toString().getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a new instance of the FlowDroid analyzer for Android
	 * 
	 * @param platformDir       The Android platform directory
	 * @param appFile           The APK file to analyze
	 * @param libraryFile       The file with the listing of the package names that
	 *                          are part of known libraries
	 * @param callbackDirectory The directory in which to store the serialized
	 *                          callback
	 * @param callgraphTimeout  The timeout for the callgraph analysis
	 * @return The FlowDroid instance
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	private static SetupApplication initializeFlowDroid(String platformDir, String appFile, String libraryFile,
			File callbackDirectory, String callgraphTimeout) throws URISyntaxException, IOException {
		SetupApplication setupApp = new SetupApplication(platformDir, appFile);
		setupApp.setTaintWrapper(new SummaryTaintWrapper(new EagerSummaryProvider("summariesManual")));

		InfoflowAndroidConfiguration config = setupApp.getConfig();
		config.setMergeDexFiles(true);
		config.setTaintAnalysisEnabled(false);
		config.getAnalysisFileConfig().setSourceSinkFile("unused.txt");
		config.setLogSourcesAndSinks(true);
		config.getPathConfiguration().setPathBuildingAlgorithm(PathBuildingAlgorithm.ContextSensitive);
		config.getPathConfiguration().setPathReconstructionMode(PathReconstructionMode.Fast);

		CallbackConfiguration callbackConfig = config.getCallbackConfig();
		if (callgraphTimeout != null && !callgraphTimeout.isEmpty())
			callbackConfig.setCallbackAnalysisTimeout(Integer.valueOf(callgraphTimeout));
		callbackConfig.setSerializeCallbacks(true);
		File callbackFile = new File(callbackDirectory, FilenameUtils.getName(appFile));
		callbackConfig.setCallbacksFile(callbackFile.getAbsolutePath());

		if (libraryFile != null && !libraryFile.isEmpty()) {
			setupApp.setSootConfig(new SootConfigForAndroid() {

				@Override
				public void setSootOptions(Options options, InfoflowConfiguration config) {
					super.setSootOptions(options, config);

					try {
						List<String> libraries = Files.readAllLines(new File(libraryFile).toPath());
						List<String> excludedLibs = options.exclude();
						for (String lib : libraries)
							excludedLibs.add(lib + ".*");
					} catch (IOException e) {
						logger.error("Could not read library definition file", e);
					}
				}

			});
		}

		setupApp.setIcfgFactory(new DefaultBiDiICFGFactory() {
			@Override
			public IInfoflowCFG buildBiDirICFG(CallgraphAlgorithm callgraphAlgorithm, boolean enableExceptions) {
				IInfoflowCFG icfg = super.buildBiDirICFG(callgraphAlgorithm, enableExceptions);
				FlowDroidSecondStep.icfg = icfg;
				return icfg;
			}
		});
		return setupApp;
	}

}
