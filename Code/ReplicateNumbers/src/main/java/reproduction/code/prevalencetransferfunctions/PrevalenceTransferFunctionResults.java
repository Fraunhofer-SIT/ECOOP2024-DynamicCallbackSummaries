package reproduction.code.prevalencetransferfunctions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import reproduction.code.Constants;
import reproduction.utils.CounterMap;
import reproduction.utils.FileUtil;
import reproduction.utils.IntegerDataRow;
import reproduction.utils.ParallelBase;
import soot.Scene;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.DirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.IndirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.InstanceinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.StaticinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdge;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeTarget;
import soot.util.HashMultiMap;

public class PrevalenceTransferFunctionResults {
	private static int neededTransfer = 0;
	private static int direct = 0;

	private static boolean addAllIndirect(HashMultiMap<String, VirtualEdge> subsigsearch2, VirtualEdge r) {

		VirtualEdgeSource s = r.getSource();
		if (s instanceof StaticinvokeSource) {
			StaticinvokeSource t = (StaticinvokeSource) s;
			subsigsearch2.put(t.getSignature(), r);
		} else {
			InstanceinvokeSource c = (InstanceinvokeSource) s;
			subsigsearch2.put("<" + c.getDeclaringType().getClassName() + ": " + c.getSubSignature() + ">", r);

		}
		boolean needsTransfer = false;
		ArrayDeque<IndirectTarget> targetQueue = new ArrayDeque<>();
		for (VirtualEdgeTarget p : r.getTargets()) {
			if (p instanceof IndirectTarget) {
				needsTransfer = true;
				targetQueue.add((IndirectTarget) p);
			} else {
				direct++;
				if (needsTransfer)
					neededTransfer++;
			}
		}
		IndirectTarget c;
		while ((c = targetQueue.poll()) != null) {

			for (VirtualEdgeTarget d : c.getTargets()) {
				if (d instanceof IndirectTarget) {
					targetQueue.add(((IndirectTarget) d));
				} else {
					direct++;
					if (needsTransfer)
						neededTransfer++;
				}
			}

		}
		return false;

	}

	final static HashMultiMap<String, VirtualEdge> subsigsearch = new HashMultiMap<>();

	public static void main(String[] args) throws Exception {
		FileUtil.assertCorrectWorkingDir();
		VirtualEdgesSummaries a = new VirtualEdgesSummaries(new File(Constants.CGMINER_SUMMARY));
		for (VirtualEdge d1 : a.getAllVirtualEdges()) {
			addAllIndirect(subsigsearch, d1);
		}
		File[] f = new File(Constants.RQ4_PREVALENCE_TRANSFER_FUNCTIONS_RESULTS).listFiles();
		if (f == null) {
			System.err.println("Could not find Results dir, check for current working dir");
			System.exit(2);
		}

		ConcurrentLinkedQueue<CounterMap<VirtualEdge>> results = new ConcurrentLinkedQueue<>();
		ParallelBase.For(Arrays.asList(f), new ParallelBase.ParallelOperation<File>() {

			@Override
			public void perform(File pParameter) {
				if (!pParameter.getName().endsWith("-sources"))
					return;
				CounterMap<VirtualEdge> r = run(pParameter);
				results.add(r);
			}
		});
		int noneTf = 0;
		int hasTf = 0;
		IntegerDataRow differentVE = new IntegerDataRow();
		IntegerDataRow multCalls = new IntegerDataRow();

		CounterMap<VirtualEdge> mostUsedEdges = new CounterMap<>();
		for (CounterMap<VirtualEdge> i : results) {
			if (i.getCountedObjects() == 0) {
				noneTf++;
			} else {
				hasTf++;
			}
			differentVE.add(i.numberOfDifferentObjects());
			multCalls.add(i.getCountedObjects());

			for (VirtualEdge p : i.keyIterator()) {
				mostUsedEdges.count(p);
			}
		}

		System.out.println("*** RQ 4: Prevalence of transfer functions ***");
		if (Constants.VERBOSE) {
			System.out.println("Out of " + results.size() + " apps, " + hasTf + " have transfer functions and " + noneTf
					+ " have none.");
			System.out.println("Different virtual edges per app: \n" + differentVE.toDebugString());
			System.out.println("Total virtual edges per app: \n" + multCalls.toDebugString());
		}
		System.out.println("Table 1 Code (Top 10 edges):");

		Iterator<Entry<VirtualEdge, Integer>> it = mostUsedEdges.sortedValues().iterator();
		int got = 0;
		while (it.hasNext()) {
			Entry<VirtualEdge, Integer> d = it.next();
			String s = createLatex(d.getKey());
			if (s == null)
				continue;
			System.out.println(d.getValue() + " & " + s + "\\\\");
			got++;
			if (got == 10)
				break;
		}

		System.out.println();

		DecimalFormat df = new java.text.DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));

		System.out.println("Variables from text: ");
		writeCommand("TFtotalApps", results.size());
		writeCommand("TFtotalAppsWithTF", hasTf);
		writeCommand("TFAppsWithtfPercent", df.format(100D * hasTf / results.size()));
		writeCommand("TFDifferentTFAvgPerApp", df.format(differentVE.getArithmeticMean()));
		writeCommand("TFnumSourcesPerApp", df.format(multCalls.getArithmeticMean()));
		writeCommand("TFrequiresTransferFunctions", neededTransfer);
		writeCommand("TFrequiresTransferFunctionsAvgPercent", df.format(100 * neededTransfer / direct));

	}

	private static String createLatex(VirtualEdge key) {
		VirtualEdgeSource src = key.getSource();
		String sr;
		if (src instanceof StaticinvokeSource) {
			sr = ((StaticinvokeSource) src).getSignature();
			String cl = Scene.signatureToClass(sr);
			String subsig = Scene.signatureToSubsignature(sr);
			sr = getLatexClassName(cl) + "." + getLatexMethodName(subsig);
		} else {
			InstanceinvokeSource is = (InstanceinvokeSource) src;
			sr = getLatexClassName(is.getDeclaringType().getClassName()) + "."
					+ getLatexMethodName(is.getSubSignature().getMethodName());
		}
		StringBuilder sb = new StringBuilder();
		sb.append("$").append(sr);

		IndirectTarget next = null;
		for (VirtualEdgeTarget d : key.getTargets()) {
			if (d instanceof IndirectTarget && use(d)) {
				next = (IndirectTarget) d;
			} else
				continue;
		}
		if (next == null)
			return null;
		n: while (true) {
			sb.append("\\rightarrow\\langle " + getLatexClassName(next.getTargetType().getClassName()));
			sb.append(".").append(getLatexMethodName(next.getTargetMethod().getMethodName())).append(", ")
					.append(next.getArgIndex()).append("\\rangle ");
			for (VirtualEdgeTarget d : next.getTargets()) {
				DirectTarget dt = null;
				if (d instanceof IndirectTarget) {
					IndirectTarget t = (IndirectTarget) d;
					for (VirtualEdgeTarget p : t.getTargets()) {
						if (p instanceof IndirectTarget && use(p)) {
							next = (IndirectTarget) p;
							continue n;
						}
						dt = (DirectTarget) p;
					}
				}

				if (dt == null)
					dt = (DirectTarget) d;
				sb.append("\\rightarrow\\langle " + getLatexClassName(dt.getTargetType().getClassName()));
				sb.append(".").append(getLatexMethodName(dt.getTargetMethod().getMethodName())).append(", ")
						.append(dt.getArgIndex()).append("\\rangle$");
				return sb.toString();
			}
		}
	}

	private static boolean use(VirtualEdgeTarget ps) {
		if (ps instanceof IndirectTarget) {
			IndirectTarget r = (IndirectTarget) ps;
			ArrayDeque<IndirectTarget> targetQueue = new ArrayDeque<>();
			if (r.getTargets().isEmpty())
				return false;
			for (VirtualEdgeTarget p : r.getTargets()) {
				if (p instanceof IndirectTarget) {
					targetQueue.add((IndirectTarget) p);
				}
			}
			IndirectTarget c;
			while ((c = targetQueue.poll()) != null) {
				for (VirtualEdgeTarget d : c.getTargets()) {
					if (d instanceof IndirectTarget)
						targetQueue.add(((IndirectTarget) d));
				}
				if (c.getTargets().isEmpty())
					return false;

			}
		}
		return true;
	}

	private static String getLatexClassName(String className) {
		return className.substring(className.lastIndexOf(".") + 1).replace("$", "\\$");
	}

	private static String getLatexMethodName(String methodName) {
		if (methodName.equals("<init>"))
			return "cons";
		else
			return methodName;
	}

	private static void writeCommand(String string, Object i) {
		System.out.println("\\newcommand{\\" + string + "}{" + i + "}");
	}

	private static CounterMap<VirtualEdge> run(File pParameter) {
		CounterMap<VirtualEdge> e = new CounterMap<>();
		try (BufferedReader br = new BufferedReader(new FileReader(pParameter))) {
			while (true) {
				String l = br.readLine();
				if (l == null)
					break;

				Set<VirtualEdge> s = subsigsearch.get(l);
				VirtualEdge sel = select(s);
				if (sel != null)
					e.count(sel);
			}
			return e;
		} catch (Throwable ex) {
			ex.printStackTrace();
			return e;
		}
	}

	private static VirtualEdge select(Set<VirtualEdge> s) {
		int b = -1;
		VirtualEdge use = null;
		for (VirtualEdge p : s) {
			String sig;
			if (p.getSource() instanceof StaticinvokeSource)
				sig = ((StaticinvokeSource) p.getSource()).getSignature();
			else
				sig = ((InstanceinvokeSource) p.getSource()).getSubSignature().toString();
			if (sig.length() > b) {
				b = sig.length();
				use = p;
			}
		}
		return use;
	}

}
