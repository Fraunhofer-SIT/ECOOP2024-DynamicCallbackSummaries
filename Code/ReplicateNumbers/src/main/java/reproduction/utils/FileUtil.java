package reproduction.utils;

import java.io.File;

import reproduction.code.Constants;

public class FileUtil {
	public static void assertCorrectWorkingDir() {
		if (!new File(Constants.CGMINER_SUMMARY).exists())
		{
			System.err.println("Make sure you are in the correct working directory.");
			System.err.println(Constants.CGMINER_SUMMARY + " should exist relative to the directory.");
			System.err.println("Current working dir: " + new File("").getAbsolutePath());
			System.exit(2);
		}
	}
}
