package reproduce.callbackevaluation.telemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.codeinspect.base.projects.ICIProject;
import de.codeinspect.base.telemetry.ITelemetryData;
import de.codeinspect.base.telemetry.ITelemetryReceiver;
import de.codeinspect.base.telemetry.SimpleTimingData;
import de.codeinspect.instrumentation.InstrumentationTelemetryData;
import reproduction.dynamiccallbackidentification.util.IntegerDataRow;
import reproduction.dynamiccallbackidentification.util.Util;

/**
 * Simple telemetry receiver
 */
public class TelemetryReceiver implements ITelemetryReceiver {

	private static final String INSTRUMENTATION_STEPS_STATS = "InstrumentationSteps";

	private final static Logger logger = LogManager.getLogger(TelemetryReceiver.class);

	private final static Map<String, IntegerDataRow> telemetryResults = new HashMap<>();

	@Override
	public void receiveTelemetry(ICIProject project, String provider, ITelemetryData data) {
		if (data instanceof SimpleTimingData) {
			synchronized (telemetryResults) {
				SimpleTimingData dt = (SimpleTimingData) data;

				add(provider, (int) dt.getMillis());

				if (data instanceof InstrumentationTelemetryData) {
					InstrumentationTelemetryData idt = (InstrumentationTelemetryData) data;
					add(INSTRUMENTATION_STEPS_STATS, idt.getNumSteps());
				}
				for (Entry<String, IntegerDataRow> d : telemetryResults.entrySet()) {
					IntegerDataRow stats = d.getValue();
					double scale = 1;
					if (d.getKey().equals(INSTRUMENTATION_STEPS_STATS))
						scale = 1;
					else
						scale = 1 / 1000D; // seconds
					Util.writeLaTeX("Max" + d.getKey(), stats.getMaxValue() * scale);
					Util.writeLaTeX("Min" + d.getKey(), stats.getMinValue() * scale);
					Util.writeLaTeX("Median" + d.getKey(), stats.getMedian() * scale);
					Util.writeLaTeX("Avg" + d.getKey(), stats.getArithmeticMean() * scale);

				}
			}
		}
	}

	private void add(String provider, int res) {
		telemetryResults.putIfAbsent(provider, new IntegerDataRow());
		telemetryResults.get(provider).add(res);
	}

}
