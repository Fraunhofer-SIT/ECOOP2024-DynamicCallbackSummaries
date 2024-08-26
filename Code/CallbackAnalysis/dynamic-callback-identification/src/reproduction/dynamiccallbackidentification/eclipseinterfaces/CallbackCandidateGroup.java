package reproduction.dynamiccallbackidentification.eclipseinterfaces;

import java.util.Set;

import de.codeinspect.soot.utils.AnalysisUtils;
import reproduction.dynamiccallbackidentification.eclipseinterfaces.DCIDFactory.CallbackCandidate;
import soot.jimple.Stmt;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class CallbackCandidateGroup {

	private MultiMap<Stmt, CallbackCandidate> groups = new HashMultiMap<>();

	public CallbackCandidateGroup(Set<CallbackCandidate> cand) {
		for (CallbackCandidate c : cand) {
			groups.put(c.getStatement(), c);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Stmt k : groups.keySet()) {
			sb.append(AnalysisUtils.getMethod(k).getSignature() + ": " + k);
			sb.append("\n");
			for (CallbackCandidate c : groups.get(k)) {
				printCallbackCandidate(sb, c);
			}
		}
		return sb.toString();
	}

	public void printCallbackCandidate(StringBuilder sb, CallbackCandidate c) {
		sb.append("- " + c + "\n");
	}

}
