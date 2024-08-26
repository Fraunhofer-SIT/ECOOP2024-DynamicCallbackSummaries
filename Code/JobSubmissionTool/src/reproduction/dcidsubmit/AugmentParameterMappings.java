package reproduction.dcidsubmit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;

import reproduction.summaries.VirtualEdgeWriter;
import soot.MethodSubSignature;
import soot.RefType;
import soot.Scene;
import soot.Type;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.util.SootMethodRepresentationParser;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.DirectParameterMapping;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.DirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.IndirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.InstanceinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.StaticinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdge;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeTarget;

public class AugmentParameterMappings {

	public static void main(String[] args) throws IOException {
		augment("Results/Summaries/edgeminer-annotated.xml",
				"Results/Summaries/edgeminer-annotated-with-parammappings.xml");
		augment("Results/Summaries/virtualedgesummaries-dcid-complete-annotated.xml",
				"Results/Summaries/virtualedgesummaries-dcid-complete-annotated-with-parammappings.xml");
	}

	private static void augment(String src, String dst) throws IOException {
		File s = new File(src);
		File d = new File(dst);
		VirtualEdgesSummaries a = new VirtualEdgesSummaries(s);
		for (VirtualEdge d1 : a.getAllVirtualEdges()) {
			containsAnyTarget(d1.getSource(), d1);
		}
		VirtualEdgeWriter w = new VirtualEdgeWriter(a);
		try (FileOutputStream ddfos = new FileOutputStream(d)) {
			w.serialize(ddfos);
		}

	}

	private static void containsAnyTarget(VirtualEdgeSource virtualEdgeSource, VirtualEdge r) {

		ArrayDeque<IndirectTarget> targetQueue = new ArrayDeque<>();
		for (VirtualEdgeTarget p : r.getTargets()) {
			if (p instanceof DirectTarget) {
				run(virtualEdgeSource, (DirectTarget) p);
			} else
				targetQueue.add((IndirectTarget) p);
		}
		IndirectTarget c;
		while ((c = targetQueue.poll()) != null) {
			for (VirtualEdgeTarget d : c.getTargets()) {
				if (d instanceof IndirectTarget)
					targetQueue.add(((IndirectTarget) d));
				else if (d instanceof DirectTarget) {
					DirectTarget dt = (DirectTarget) d;
					run(virtualEdgeSource, dt);
				}
			}

		}

	}

	private static void run(VirtualEdgeSource virtualEdgeSource, DirectTarget p) {
		SootMethodRepresentationParser smp = SootMethodRepresentationParser.v();
		SootMethodAndClass smc;
		MethodSubSignature ms;

		if (virtualEdgeSource instanceof StaticinvokeSource) {
			StaticinvokeSource src = (StaticinvokeSource) virtualEdgeSource;
			smc = smp.parseSootMethodString(src.getSignature());
			ms = new MethodSubSignature(Scene.v().getSubSigNumberer().findOrAdd(smc.getSubSignature()));
		} else {
			InstanceinvokeSource inv = (InstanceinvokeSource) virtualEdgeSource;
			ms = inv.getSubSignature();
		}
		List<Type> pmSourceList = ms.getParameterTypes();
		List<Type> pmTargetList = p.getTargetMethod().parameterTypes;
		for (int isrc = 0; isrc < pmSourceList.size(); isrc++) {
			for (int itgt = 0; itgt < pmTargetList.size(); itgt++) {
				Type sr = pmSourceList.get(isrc);
				Type tg = pmTargetList.get(itgt);
				if (sr instanceof RefType && tg instanceof RefType) {
					if (sr == tg) {
						p.getParameterMappings().add(new DirectParameterMapping(isrc, itgt));
					}
				}

			}
		}
	}
}
