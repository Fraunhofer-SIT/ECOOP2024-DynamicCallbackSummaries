package reproduction.summaries;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import soot.Kind;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdge;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeSource;

/**
 * Class for handling a set of virtual edges
 * 
 * 
 *
 */
public class VirtualEdgeManager {

	protected final Map<EdgeKey, VirtualEdge> edges = new HashMap<>();

	/**
	 * Key for quickly accessing edges inside a map
	 * 
	 * 
	 *
	 */
	private static class EdgeKey {

		Kind edgeType;
		VirtualEdgeSource source;

		/**
		 * Constructs a new {@link EdgeKey} object based on an existing call edge
		 * summary
		 * 
		 * @param edge The existing call edge summary
		 */
		public EdgeKey(VirtualEdge edge) {
			this.edgeType = edge.getEdgeType();
			this.source = edge.getSource();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((edgeType == null) ? 0 : edgeType.hashCode());
			result = prime * result + ((source == null) ? 0 : source.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			EdgeKey other = (EdgeKey) obj;
			if (edgeType == null) {
				if (other.edgeType != null)
					return false;
			} else if (!edgeType.equals(other.edgeType))
				return false;
			if (source == null) {
				if (other.source != null)
					return false;
			} else if (!source.equals(other.source))
				return false;
			return true;
		}

	}

	/**
	 * Constructs a new, empty instance of the {@link VirtualEdgeManager}
	 */
	public VirtualEdgeManager() {
	}

	/**
	 * Constructs a new instance of the {@link VirtualEdgeManager} based on an
	 * existing set of edges
	 * 
	 * @param edges The existing set of edges
	 */
	public VirtualEdgeManager(Collection<VirtualEdge> edges) {
		addEdges(edges);
	}

	/**
	 * Adds the given edges to this edge manager
	 * 
	 * @param edges The edges to add
	 */
	public void addEdges(Collection<VirtualEdge> edges) {
		if (edges != null && !edges.isEmpty()) {
			for (VirtualEdge edge : edges) {
				addEdge(edge);
			}
		}
	}

	/**
	 * Adds the given edge to this edge manager
	 * 
	 * @param edge The edge to add
	 */
	public void addEdge(VirtualEdge edge) {
		VirtualEdge originalEdge = this.edges.computeIfAbsent(new EdgeKey(edge), k -> edge);
		originalEdge.addTargets(edge.getTargets());
	}

	/**
	 * Gets all summary edges registered in this manager
	 * 
	 * @return The summary edges registered in this manager
	 */
	public Collection<VirtualEdge> getEdges() {
		return edges.values();
	}

	/**
	 * Gets whether the edge manager is empty, i.e., does not contain any edges
	 * 
	 * @return True if this edge manager is empty, false otherwise
	 */
	public boolean isEmpty() {
		return edges.isEmpty();
	}

}
