package reproduction.util;

import java.util.Collection;

import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.DirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.IndirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdge;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeTarget;

/**
 * Helper methods for dealing with callback summaries
 * 
 * 
 *
 */
public class EdgeUtils {

	/**
	 * Counts the number of edges in the given edge set. If there are multiple
	 * targets for one source, they need to be counted as individual edges.
	 * 
	 * @param edges The edges to count
	 * @return The number of individual edges in the given set
	 */
	public static int countEdges(Collection<VirtualEdge> edges) {
		if (edges.isEmpty())
			return 0;

		int count = 0;
		for (VirtualEdge ve : edges)
			count += countEdgeTargets(ve.getTargets());
		return count;
	}

	/**
	 * Counts the number of edges in the given list of edge targets
	 * 
	 * @param edgeTargets The edge targets
	 * @return The number of final targets, i.e., callback methods, referenced by
	 *         the given list of edge targets
	 */
	public static int countEdgeTargets(Collection<VirtualEdgeTarget> edgeTargets) {
		if (edgeTargets.isEmpty())
			return 0;

		int count = 0;
		for (VirtualEdgeTarget vet : edgeTargets) {
			if (vet instanceof IndirectTarget) {
				IndirectTarget it = (IndirectTarget) vet;
				count += countEdgeTargets(it.getTargets());
			} else if (vet instanceof DirectTarget)
				count++;
		}
		return count;
	}

	/**
	 * Gets the edge length from source to final target
	 * 
	 * @param target The next intermediate target
	 * @return The number of edges from source to final target
	 */
	public static int getEdgeLength(VirtualEdgeTarget target) {
		if (target instanceof DirectTarget)
			return 0;
		else if (target instanceof IndirectTarget) {
			IndirectTarget indirectTarget = (IndirectTarget) target;
			int length = 1;
			for (VirtualEdgeTarget nextTarget : indirectTarget.getTargets())
				length = Math.max(length, getEdgeLength(nextTarget));
			return length;
		}
		throw new IllegalArgumentException(String.format("Invalid edge type %s", target.getClass().getName()));
	}

}
