package reproduction.code;

import java.io.File;

import reproduction.utils.FileUtil;

public class FlowDroidClientAnalysis {

	static class FlowDroidResults {
		int count;

		public long compareRelativeTo(FlowDroidResults base) {
			//How many percent more than base
			double dpercent = (100D * count / base.count) - 100;
			return Math.round(dpercent);
		}
	}
	
	public static void main(String[] args) {
		FileUtil.assertCorrectWorkingDir();
		File baseDir = new File(Constants.RQ7_FLOWDROID_RESULTS);
		FlowDroidResults base = readFlowDroidResults(new File(baseDir, "flowdroid-baseline"));
		FlowDroidResults edgeminer = readFlowDroidResults(new File(baseDir, "flowdroid-edgeminer"));
		FlowDroidResults cgminer = readFlowDroidResults(new File(baseDir, "flowdroid-dcid"));
		
		System.out.println("*** RQ 7: FlowDroid client analysis ***");

		writeCommand("FlowDroidBaseLineFlows", base.count);

		performStats("Edgeminer", base, edgeminer);
		performStats("Cgminer", base, cgminer);

		FlowDroidResults edgeminerWithDataMappings = readFlowDroidResults(new File(baseDir, "fd-with-parametermappings-edgeminer"));
		FlowDroidResults cgminerWithDataMappings = readFlowDroidResults(new File(baseDir, "fd-with-parametermappings-dcid"));
		performStats("EdgeminerParamMapping", base, edgeminerWithDataMappings);
		performStats("CgminerParamMapping", base, cgminerWithDataMappings);

		
	}

	private static void performStats(String str, FlowDroidResults base, FlowDroidResults results) {
		writeCommand("FlowDroid" + str +"Flows", results.count);
		writeCommand("FlowDroid" + str + "MoreThanBaseline", results.compareRelativeTo(base));
		
	}

	private static FlowDroidResults readFlowDroidResults(File file) {
		FlowDroidResults res = new FlowDroidResults();
		for (File app : file.listFiles()) {
			if (app.isDirectory()) {
				for (File d : app.listFiles()) {
					if (d.getName().startsWith("DF"))
						res.count++;
				}
			}
		}
		return res;
	}

	private static void writeCommand(String string, Object i) {
		System.out.println("\\newcommand{\\" + string + "}{" + i + "}");
	}
}
