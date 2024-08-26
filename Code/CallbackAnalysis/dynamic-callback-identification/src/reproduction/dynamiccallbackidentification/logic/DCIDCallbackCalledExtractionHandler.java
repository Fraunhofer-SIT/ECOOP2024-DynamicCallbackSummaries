package reproduction.dynamiccallbackidentification.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.codeinspect.dynamicvalues.android.LogUtils;
import de.codeinspect.dynamicvalues.base.extractionHandlers.IExtractionHandler;
import de.codeinspect.dynamicvalues.base.extractionHandlers.IExtractionHandlerData;;

public class DCIDCallbackCalledExtractionHandler implements IExtractionHandler {
	private static final Map<Long, Boolean> sent = new ConcurrentHashMap<>();
	private static final Map<Long, Object> indexToClass = new HashMap<>();
	private static final boolean NEEDS_STACK_TRACE = false;

	public static void addClass(int idx, int pm, Object c) {
		long id = getId(idx, pm);
		LogUtils.i("Registered " + c + " for callsite id " + idx + " and parameter " + pm + ": " + id);
		indexToClass.put(id, c);
	}

	@Override
	public IExtractionHandlerData performExtraction(long lpid, Object baseObject, Object[] params, Object returnValue,
			Object[] additionalValues, boolean explicitRequest) {
		if (!explicitRequest)
			return null;

		final int cbID = (Integer) additionalValues[0];
		if (cbID == -1)
			// Used in another thread.
			return null;
		if (cbID == 0) {
			// Not used as a callback
			return null;
		}

		final int cbParam = (Integer) additionalValues[1];
		if (additionalValues.length == 3) {
			long cid = getId(cbID, cbParam);
			Object s = indexToClass.get(cid);
			if (s == null) {
				LogUtils.e("Could not find combined id " + cid);
			}
			Class c = null;
			String l = null;
			if (s != null && s.getClass() == String.class) {
				try {
					l = s.toString();
					c = Class.forName(l);
				} catch (ClassNotFoundException e) {
				}
			} else
				c = (Class) s;
			Class actual = additionalValues[2].getClass();
			if (c == null) {
				LogUtils.e("CI-Callback: Could not resolve class " + s + " combined id: " + cid);
			} else if (c.isAssignableFrom(actual)) {
				LogUtils.i("CI-Callback: Expected " + c + ", got " + actual + ", which is OK.");
			} else {
				LogUtils.e("CI-Callback: Invalid callback: " + c + " " + actual + " for " + cbID + ", " + cbParam);
				return null;
			}
		}
		long l = cbID ^ cbParam ^ lpid;

		if (sent.putIfAbsent(l, true) != null)
			// already seen.
			return null;

		String[] strStacktrace;
		if (NEEDS_STACK_TRACE) {
			// get call hierarchy
			StackTraceElement[] st = Thread.currentThread().getStackTrace();
			strStacktrace = new String[st.length];
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < st.length; i++) {
				strStacktrace[i] = st[i].toString();
				sb.append(st[i]).append("\n");
			}
			LogUtils.i("CI-Callback: " + sb.toString());
		} else
			strStacktrace = new String[0];
		final int idc = additionalValues.length == 3 ? System.identityHashCode(additionalValues[2]) : 0;
		// System.out.println("Callback called in " + Arrays.toString(strStacktrace));
		return new DCIDCallbackExtractionData((Integer) additionalValues[0], (Integer) additionalValues[1],
				strStacktrace, idc);
	}

	public static long getId(int cbID, int cbParam) {
		return ((long) cbID) << 32 + cbParam;
	}

	@Override
	public String getName() {
		return "DCID Callback Called";
	}

	@Override
	public IExtractionHandlerData performExtraction(Object baseObject, Object[] params, Object returnValue,
			Object[] additionalValues, boolean explicitRequest) {
		return null;
	}

}
