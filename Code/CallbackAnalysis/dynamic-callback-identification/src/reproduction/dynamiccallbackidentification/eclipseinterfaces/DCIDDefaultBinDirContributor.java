package reproduction.dynamiccallbackidentification.eclipseinterfaces;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.osgi.framework.Bundle;

import de.codeinspect.base.projects.ICIProject;
import de.codeinspect.collections.streams.CICollectors;
import de.codeinspect.soot.ISootProcessDirContributor;
import reproduction.dynamiccallbackidentification.Activator;

public class DCIDDefaultBinDirContributor implements ISootProcessDirContributor {
	Logger logger = LogManager.getLogger(DCIDDefaultBinDirContributor.class);

	private static final String ENV_VAR_BIN_DIR = "DCID_PROCESS_DIR";

	@Override
	public File[] getAdditionalSootProcessDirs(ICIProject project) {
		String manualBinDir = System.getenv(ENV_VAR_BIN_DIR);
		if (manualBinDir != null) {
			logger.info(String.format("got soot process dir from environment variable: ", manualBinDir));
			return new File[] { new File(manualBinDir) };
		}

		Bundle defaultBundle = Activator.getDefault().getBundle();
		URL[] classesDir = new URL[] { defaultBundle.getEntry("/bin"), defaultBundle.getEntry("/target/classes") };
		File[] classFiles = Arrays.stream(classesDir).map(u -> urlToFile(u)).filter(f -> f != null)
				.collect(CICollectors.toArray(File.class));
		if (classFiles != null && classFiles.length > 0)
			return classFiles;

		logger.debug("Couldn't find bin directory. Trying to load jar.");

		// try to get jar path

		try {
			File jarFile = new File(
					DCIDDefaultBinDirContributor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (jarFile.exists()) {
				logger.debug(String.format("Found jar file at %s", jarFile.getAbsolutePath()));
				return new File[] { jarFile };
			}
		} catch (URISyntaxException e) {
			logger.error(e);
		}
		logger.error(
				String.format("could not get additional sources for instrumentation!. use environment variable %s.",
						ENV_VAR_BIN_DIR));

		return new File[] {};
	}

	private File urlToFile(URL u) {
		if (u != null) {
			try {
				URL fileURL = org.eclipse.core.runtime.FileLocator.toFileURL(u);
				if (fileURL != null) {
					File f = new File(fileURL.getFile());
					if (f.exists())
						return f;
				}
			} catch (IOException e) {

			}
		}
		return null;
	}
}
