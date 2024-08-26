package reproduction.code.prevalencetransferfunctions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;

import reproduction.code.Constants;
import reproduction.utils.FileUtil;
import reproduction.utils.ParallelBase;
import soot.MethodSubSignature;
import soot.Scene;
import soot.SootMethodRef;
import soot.Type;
import soot.dexpler.DexBody;
import soot.dexpler.DexFileProvider;
import soot.dexpler.DexFileProvider.DexContainer;
import soot.dexpler.DexType;
import soot.dexpler.instructions.MethodInvocationInstruction;
import soot.jimple.MethodHandle.Kind;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.DirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.IndirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.InstanceinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.StaticinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdge;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeTarget;
import soot.util.HashMultiMap;

public class PrevalenceOfTransferFunctionsResultGenerator {


	private static boolean addAllIndirect(HashMultiMap<String, MethodSubSignature> subsigsearch2, VirtualEdge r) {
		VirtualEdgeSource s = r.getSource();
		if (s instanceof StaticinvokeSource) {
			StaticinvokeSource t = (StaticinvokeSource) s;
			subsigsearchDirect.put(Scene.signatureToClass(t.getSignature()), new MethodSubSignature(
					Scene.v().getSubSigNumberer().findOrAdd(Scene.signatureToSubsignature(t.getSignature()))));
		} else {
			InstanceinvokeSource c = (InstanceinvokeSource) s;
			subsigsearchDirect.put(c.getDeclaringType().getClassName(), c.getSubSignature());

		}
		ArrayDeque<IndirectTarget> targetQueue = new ArrayDeque<>();
		for (VirtualEdgeTarget p : r.getTargets()) {
			if (p instanceof IndirectTarget) {
				targetQueue.add((IndirectTarget) p);
			} else {
				// addDirect(p);
			}
		}
		IndirectTarget c;
		while ((c = targetQueue.poll()) != null) {
			subsigsearch2.put(c.getTargetType().getClassName(), c.getTargetMethod());
			for (VirtualEdgeTarget d : c.getTargets()) {
				if (d instanceof IndirectTarget)
					targetQueue.add(((IndirectTarget) d));
				// else
				// addDirect(d);
			}

		}
		return false;

	}

	private static void addDirect(VirtualEdgeTarget p) {
		if (p instanceof DirectTarget) {
			DirectTarget tt = (DirectTarget) p;
			subsigsearchDirect.put(tt.getTargetType().getClassName(), tt.getTargetMethod());
		}
	}

	final static HashMultiMap<String, MethodSubSignature> subsigsearch = new HashMultiMap<>();

	final static HashMultiMap<String, MethodSubSignature> subsigsearchDirect = new HashMultiMap<>();

	public static boolean hasResults() {
		File f = new File("Results/RQ4-TransferFunctionsPrevalence/");
		File[] l = f.listFiles();
		if (l != null && l.length > 0)
			return true;
		else
			return false;
	}

	public static void main(String[] args) throws Exception {
		FileUtil.assertCorrectWorkingDir();
		VirtualEdgesSummaries a = new VirtualEdgesSummaries(new File(Constants.CGMINER_SUMMARY));
		for (VirtualEdge d1 : a.getAllVirtualEdges()) {
			addAllIndirect(subsigsearch, d1);
		}
		List<String> f = IOUtils.readLines(new FileInputStream(new File(Constants.RQ4_PREVALENCE_TRANSFER_FUNCTIONS_SUBSET)));

		ParallelBase.For(f, new ParallelBase.ParallelOperation<String>() {

			@Override
			public void perform(String pParameter) {
				run(pParameter);
			}
		});

	}

	private static void run(String file) {

		File f = new File(file);
		File base = new File(Constants.RQ4_PREVALENCE_TRANSFER_FUNCTIONS_RESULTS);
		File fOutDirect = new File(
				base,
				f.getName() + "-sources");
		base.mkdirs();
		
		StringBuilder str = new StringBuilder();
		StringBuilder strDirect = new StringBuilder();
		try {
			List<DexContainer<? extends DexFile>> d = DexFileProvider.v().getDexFromSource(new File(file));
			for (DexContainer<? extends DexFile> x : d) {
				for (ClassDef c : x.getBase().getDexFile().getClasses()) {
					String cl = soot.dexpler.Util.dottedClassName(c.getType());
					for (Method m : c.getMethods()) {
						MethodImplementation impl = m.getImplementation();
						if (impl != null) {
							Type ret = DexType.toSoot(m.getReturnType());
							String callingSig = "<" + cl + ": " + ret + " " + m.getName() + "(";
							if (m.getParameterTypes() != null) {
								boolean first = true;
								for (CharSequence t : m.getParameterTypes()) {
									if (first)
										first = false;
									else
										callingSig += ",";
									callingSig += (DexType.toSoot(t.toString()));
								}
							}
							callingSig += ")>";
							int address = 0;
							for (Instruction inst : impl.getInstructions()) {
								Kind k = null;
								switch (inst.getOpcode()) {
								case INVOKE_VIRTUAL:
								case INVOKE_VIRTUAL_RANGE:
									k = Kind.REF_INVOKE_VIRTUAL;
									break;

								case INVOKE_INTERFACE:
								case INVOKE_INTERFACE_RANGE:
									k = Kind.REF_INVOKE_INTERFACE;
									break;

								case INVOKE_DIRECT:
								case INVOKE_DIRECT_RANGE:
								case INVOKE_SUPER:
								case INVOKE_SUPER_RANGE:
									k = Kind.REF_INVOKE_VIRTUAL;
									break;

								case INVOKE_STATIC:
								case INVOKE_STATIC_RANGE:
									k = Kind.REF_INVOKE_STATIC;
									break;

								/*
								 * case EXECUTE_INLINE: case EXECUTE_INLINE_RANGE: return new
								 * ExecuteInlineInstruction(instruction, codeAddress);
								 */

								case INVOKE_POLYMORPHIC:
								case INVOKE_POLYMORPHIC_RANGE:
									k = Kind.REF_INVOKE_VIRTUAL;
									break;

								case INVOKE_CUSTOM:
								case INVOKE_CUSTOM_RANGE:
									break;
								}

								if (k != null) {
									final Kind kk = k;
									// DexlibAbstractInstruction l = InstructionFactory.fromInstruction(inst,
									// address);
									MethodInvocationInstruction miv = new MethodInvocationInstruction(inst, address) {

										@Override
										public void jimplify(DexBody body) {
											SootMethodRef mr = getNormalSootMethodRef(kk);

											if (subsigsearch.get(mr.getDeclaringClass().getName())
													.contains(new MethodSubSignature(mr))) {
												str.append(mr.getSignature()).append("\n");
											}
											if (subsigsearchDirect.get(mr.getDeclaringClass().getName())
													.contains(new MethodSubSignature(mr))) {
												strDirect.append(mr.getSignature()).append("\n");
											}

										}
									};
									miv.jimplify(null);

								}
								address += inst.getCodeUnits();
							}
						}
					}
				}
			}

			try (FileWriter w = new FileWriter(fOutDirect)) {
				w.write(strDirect.toString());
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private static boolean isAndroid(String n) {
		if (n.startsWith("android.") || n.startsWith("androidx.") || n.startsWith("javax.") || n.startsWith("java.")) {
			if (!n.startsWith("android.support")) {
				return true;
			}
		}
		return false;
	}

}
