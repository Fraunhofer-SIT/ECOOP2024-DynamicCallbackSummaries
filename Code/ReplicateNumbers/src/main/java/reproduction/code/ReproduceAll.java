package reproduction.code;

import reproduction.code.prevalencetransferfunctions.PrevalenceOfTransferFunctionsResultGenerator;
import reproduction.code.prevalencetransferfunctions.PrevalenceTransferFunctionResults;
import reproduction.utils.FileUtil;

public class ReproduceAll {
	public static void main(String[] args) throws Exception {
		FileUtil.assertCorrectWorkingDir();
		//Generates results for RQ4
		//we have attached the results so that the raw data set is not necessarily
		//needed in order to generate the data.
		if (!PrevalenceOfTransferFunctionsResultGenerator.hasResults()) {
			PrevalenceOfTransferFunctionsResultGenerator.main(args);
		}
		
		//RQ4
		PrevalenceTransferFunctionResults.main(args);
		//RQ5
		CompareEdgeminerCGminerResults.main(args);
		//RQ7
		FlowDroidClientAnalysis.main(args);
	}
}
