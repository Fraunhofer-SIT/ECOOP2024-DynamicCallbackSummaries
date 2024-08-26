package reproduce.callbackevaluation.evaluation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.codeinspect.platforms.soot.analyses.SootApplicationAnalysis;
import de.codeinspect.soot.utils.BaseSootMainUtils;
import reproduction.dynamiccallbackidentification.util.IntegerDataRow;
import reproduction.dynamiccallbackidentification.util.Util;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

public class Evaluation extends SootApplicationAnalysis {

	private static final Logger logger = LogManager.getLogger(Evaluation.class);

	private static final IntegerDataRow classes = new IntegerDataRow();
	private static final IntegerDataRow methods = new IntegerDataRow();
	private static final IntegerDataRow units = new IntegerDataRow();

	private static final Object lock = new Object();

	@Override
	public void start() throws Throwable {
		int classesC = 0, methodsC = 0, unitsC = 0;
		for (SootClass i : Scene.v().getApplicationClasses()) {
			if (BaseSootMainUtils.isCodeInspectRuntimePackage(i.getName()) || i.getName().startsWith("reproduce."))
				continue;
			classesC++;

			for (SootMethod m : i.getMethods()) {
				methodsC++;
				if (m.isConcrete()) {
					Body b = m.retrieveActiveBody();
					unitsC += b.getUnits().size();
				}
			}
		}
		synchronized (lock) {
			classes.add(classesC);
			methods.add(methodsC);
			units.add(unitsC);

			Util.writeLaTeX("MaxClasses", classes.getMaxValue());
			Util.writeLaTeX("MinClasses", classes.getMinValue());
			Util.writeLaTeX("MedianClasses", classes.getMedian());
			Util.writeLaTeX("AvgClasses", classes.getArithmeticMean());

			Util.writeLaTeX("MaxMethods", methods.getMaxValue());
			Util.writeLaTeX("MinMethods", methods.getMinValue());
			Util.writeLaTeX("MedianMethods", methods.getMedian());
			Util.writeLaTeX("AvgMethods", methods.getArithmeticMean());

			Util.writeLaTeX("MaxUnits", units.getMaxValue());
			Util.writeLaTeX("MinUnits", units.getMinValue());
			Util.writeLaTeX("MedianUnits", units.getMedian());
			Util.writeLaTeX("AvgUnits", units.getArithmeticMean());
		}

	}

}
