package reproduction.dynamiccallbackidentification.eclipseinterfaces;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.codeinspect.soot.CIScene;
import de.codeinspect.soot.callgraph.summaries.VirtualEdgeWriter;
import reproduction.dynamiccallbackidentification.eclipseinterfaces.DCIDFactory.CallbackCandidate;
import soot.Local;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdge;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeSource;
import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class DebugDynamicCallback implements Tag {

	public static final String ID = "DynamicCallback";
	private Set<CallbackCandidate> candidates = new HashSet<>();
	private MultiMap<CallbackCandidate, VirtualEdge> successfulCandidates = new HashMultiMap<>();

	private static Logger logger = LogManager.getLogger(DCIDDefaultBinDirContributor.class);

	@Override
	public String getName() {
		return ID;
	}

	@Override
	public byte[] getValue() throws AttributeValueException {
		return null;
	}

	public static DebugDynamicCallback v() {
		DebugDynamicCallback cb = (DebugDynamicCallback) CIScene.v().getTag(DebugDynamicCallback.ID);
		if (cb == null) {
			cb = new DebugDynamicCallback();
			CIScene.v().addTag(cb);
		}
		return cb;
	}

	public void addCandidates(Collection<CallbackCandidate> callbackCandidates) {
		candidates.addAll(callbackCandidates);
	}

	public void candidateTriggered(Stmt srcStmt, VirtualEdgeSource source, int argIdx, VirtualEdge edge) {
		CallbackCandidate cd;
		if (argIdx == -1)
			cd = new CallbackCandidate(srcStmt, (Local) ((InstanceInvokeExpr) srcStmt).getBase());
		else {
			Value i = srcStmt.getInvokeExpr().getArg(argIdx);
			if (i instanceof Local)
				cd = new CallbackCandidate(srcStmt, argIdx, (Local) i);
			else
				return;
		}
		successfulCandidates.put(cd, edge);
	}

	@Override
	public String toString() {
		CallbackCandidateGroup allCandidates = new CallbackCandidateGroup(candidates);
		CallbackCandidateGroup successCandidates = new CallbackCandidateGroup(successfulCandidates.keySet()) {
			@Override
			public void printCallbackCandidate(StringBuilder sb, CallbackCandidate c) {
				super.printCallbackCandidate(sb, c);
				Set<VirtualEdge> sc = successfulCandidates.get(c);
				for (VirtualEdge o : sc) {
					sb.append("\t* " + o.toString() + "\n");
				}
				VirtualEdgeWriter wr = new VirtualEdgeWriter(sc);
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				try {
					wr.serialize(bos);
				} catch (IOException e) {
					logger.error("Could not write edges", e);
				}
				try {
					sb.append("\t#").append(new String(bos.toByteArray(), "UTF-8")).append("\n");
				} catch (UnsupportedEncodingException e) {
					logger.error("Could not write edges", e);
				}
			}
		};

		return "All candidates:\n" + allCandidates + "\n\nSuccessful candidates:\n" + successCandidates
				+ "\n[Statistics: " + successfulCandidates.size() + "/" + candidates.size() + "]";
	}

	public boolean isEmpty() {
		return candidates.isEmpty();
	}

}
