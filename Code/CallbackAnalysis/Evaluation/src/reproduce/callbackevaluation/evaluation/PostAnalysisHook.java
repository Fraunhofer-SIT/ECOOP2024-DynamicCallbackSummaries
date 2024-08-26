package reproduce.callbackevaluation.evaluation;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;

import de.codeinspect.base.projects.AbstractCIProject;
import de.codeinspect.dynamicvalues.analysisserver.hooks.IPostAnalysisHook;
import de.codeinspect.soot.CIScene;
import de.codeinspect.soot.callgraph.summaries.VirtualEdgeManager;
import reproduction.dynamiccallbackidentification.util.IntegerDataRow;
import reproduction.dynamiccallbackidentification.util.Util;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.DirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.IndirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdge;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeTarget;

/**
 * Hook for writing out the summary edges even if the scene gets lost afterwards
 * 
 * 
 *
 */
public class PostAnalysisHook implements IPostAnalysisHook {

	private static final Logger logger = LogManager.getLogger(PostAnalysisHook.class);
	private static final VirtualEdgeManager combined = new VirtualEdgeManager();

	private static final IntegerDataRow edgeStats = new IntegerDataRow();
	private static final IntegerDataRow edgeLDepthStats = new IntegerDataRow();

	private static final List<Integer> edgeCounts = new ArrayList<>();

	@Override
	public void onDynamicAnalysisCompleted(AbstractCIProject project) {
		synchronized (combined) {
			final VirtualEdgeManager edgeManager = CIScene.v().getVirtualEdgeManager();
			logger.info("We have {} edge summaries", edgeManager.getEdges().size());
			for (VirtualEdge i : edgeManager.getEdges()) {
				combined.addEdge(i);
			}

			int cappedges = edgeManager.getEdges().size();

			if (!edgeManager.isEmpty()) {
				edgeStats.add(cappedges);

				// Write out the callback summaries
				VirtualEdgesSummaries summaries = new VirtualEdgesSummaries(combined.getEdges());
				System.out.println("Summaries:");
				try {
					System.out.println(getVirtualEdgeSummaries(summaries.toXMLDocument()));
				} catch (Exception e) {
					logger.error("Could not write out edges", e);
				}
				int maxEdgeLength = getMaxEdgeDepth(summaries);
				edgeLDepthStats.add(maxEdgeLength);

				Util.writeLaTeX("NumberOfApps", edgeStats.getSize());
				Util.writeLaTeX("NumberOfSummaries", edgeStats.getSize());
				Util.writeLaTeX("MaxNumberEdges", edgeStats.getMaxValue());
				Util.writeLaTeX("MinNumberEdges", edgeStats.getMinValue());
				Util.writeLaTeX("MedianNumberEdges", edgeStats.getMedian());
				Util.writeLaTeX("AvgNumberEdges", edgeStats.getArithmeticMean());

				Util.writeLaTeX("MaxDepthEdges", edgeLDepthStats.getMaxValue());
				Util.writeLaTeX("MinDepthEdges", edgeLDepthStats.getMinValue());
				Util.writeLaTeX("MedianDepthEdges", edgeLDepthStats.getMedian());
				Util.writeLaTeX("AvgDepthEdges", edgeLDepthStats.getArithmeticMean());

				System.out.println(
						"\\begin{tikzpicture}\n" + "	\\begin{axis}[\n" + "			xlabel={Number of edges},\n"
								+ "			ylabel={Number of apps},\n" + "			xmajorgrids=true,\n"
								+ "			ymajorgrids=true,\n" + "			width=.7\\linewidth\n" + "			]\n"
								+ "		\\addplot[black, scatter] coordinates {");

				edgeCounts.add(cappedges);
				Collections.sort(edgeCounts);
				Collections.reverse(edgeCounts);
				int x = 1;
				for (int d : edgeCounts) {
					System.out.println("(" + x + "," + d + ")");
				}

				System.out.println("\n" + "	\\end{axis}\n" + "\\end{tikzpicture}");
			}
		}

	}

	private int getMaxEdgeDepth(VirtualEdgesSummaries summaries) {
		int mx = 0;
		for (VirtualEdge d : summaries.getAllVirtualEdges()) {
			mx = Math.max(mx, getMaxEdgeDepth(d.getTargets()));
		}
		return mx;
	}

	private int getMaxEdgeDepth(Collection<VirtualEdgeTarget> targets) {
		int mx = 0;
		for (VirtualEdgeTarget d : targets) {
			if (d instanceof DirectTarget)
				mx = Math.max(mx, 1);
			else if (d instanceof IndirectTarget) {
				IndirectTarget dt = (IndirectTarget) d;
				mx = Math.max(mx, getMaxEdgeDepth(dt.getTargets()) + 1);
			}
		}
		return mx;
	}

	private String getVirtualEdgeSummaries(Document doc) throws ParserConfigurationException,
			TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException {
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		StreamResult result = new StreamResult(new StringWriter());
		DOMSource source = new DOMSource(doc);
		transformer.transform(source, result);
		String xmlString = result.getWriter().toString();
		logger.info("Callbacks: " + xmlString);
		return xmlString;
	}

}
