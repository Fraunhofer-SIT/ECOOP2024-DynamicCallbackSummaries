package reproduction.summaries;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import soot.Kind;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.AbstractParameterMapping;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.DirectParameterMapping;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.DirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.IndirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.InstanceinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.StaticinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdge;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeTarget;

/**
 * Class for writing virtual edge summaries into an XML file
 * 
 * 
 *
 */
public class VirtualEdgeWriter {

	private final Collection<VirtualEdge> virtualEdges;

	/**
	 * Creates a new instance of the {@link VirtualEdgeWriter} class based on an
	 * existing set of summary edges
	 * 
	 * @param summaries The summary edges
	 */
	public VirtualEdgeWriter(VirtualEdgesSummaries summaries) {
		this.virtualEdges = summaries.getAllVirtualEdges();
	}

	/**
	 * Creates a new instance of the {@link VirtualEdgeWriter} class based on a
	 * {@link VirtualEdgeManager}. This constructor is usually invoked after
	 * inferring new edges in a static or dynamic analysis.
	 * 
	 * @param edgeManager The edge manager
	 */
	public VirtualEdgeWriter(VirtualEdgeManager edgeManager) {
		this.virtualEdges = edgeManager.getEdges();
	}

	/**
	 * Creates a new instance of the {@link VirtualEdgeWriter} class based on a
	 * collection of virtual edges.
	 * 
	 * @param virtualEdges The virtual edges
	 */
	public VirtualEdgeWriter(Collection<VirtualEdge> virtualEdges) {
		this.virtualEdges = virtualEdges;
	}

	/**
	 * Serializes the virtual edges into XML data in UTF-8 encoding
	 * 
	 * @param os The stream that will receive the XML data
	 */
	public void serialize(OutputStream os) throws IOException {
		try {
			XMLOutputFactory factory = XMLOutputFactory.newInstance();
			XMLStreamWriter writer = factory.createXMLStreamWriter(os, "UTF-8");

			writer.writeStartDocument("UTF-8", "1.0");
			writer.writeStartElement(XmlFileConstants.Tags.root);

			// Write out the individual edges
			for (VirtualEdge edge : virtualEdges) {
				serializeEdge(writer, edge);
			}

			writer.writeEndElement();
			writer.writeEndDocument();
			writer.close();
		} catch (XMLStreamException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Serializes the given edge into the XML document
	 * 
	 * @param writer The writer to use for serialization
	 * @param edge   The edge to serialize
	 */
	private void serializeEdge(XMLStreamWriter writer, VirtualEdge edge) throws XMLStreamException {
		writer.writeStartElement(XmlFileConstants.Tags.edge);

		// Write out the edge type
		String edgeType = "";
		if (edge.getEdgeType() == Kind.THREAD)
			edgeType = XmlFileConstants.EdgeTypes.thread;
		else if (edge.getEdgeType() == Kind.EXECUTOR)
			edgeType = XmlFileConstants.EdgeTypes.executor;
		else if (edge.getEdgeType() == Kind.HANDLER)
			edgeType = XmlFileConstants.EdgeTypes.handler;
		else if (edge.getEdgeType() == Kind.ASYNCTASK)
			edgeType = XmlFileConstants.EdgeTypes.asyncTask;
		else if (edge.getEdgeType() == Kind.PRIVILEGED)
			edgeType = XmlFileConstants.EdgeTypes.privileged;
		else if (edge.getEdgeType() == Kind.GENERIC_FAKE)
			edgeType = XmlFileConstants.EdgeTypes.genericFake;
		if (edgeType != null && !edgeType.isEmpty())
			writer.writeAttribute(XmlFileConstants.Attributes.type, edgeType);

		// Write out the edge source
		VirtualEdgeSource edgeSource = edge.getSource();
		if (edgeSource instanceof InstanceinvokeSource)
			serializeInstanceEdgeSource(writer, (InstanceinvokeSource) edgeSource);
		else if (edge.getSource() instanceof StaticinvokeSource)
			serializeStaticEdgeSource(writer, (StaticinvokeSource) edgeSource);
		else
			throw new InvalidCallgraphSummaryException(
					String.format("Unsuppported edge source type %s", edge.getSource().getClass().getName()));

		// Write out the targets
		writer.writeStartElement(XmlFileConstants.Tags.targets);
		for (VirtualEdgeTarget target : edge.getTargets()) {
			if (target instanceof DirectTarget)
				serializeDirectEdgeTarget(writer, (DirectTarget) target);
			else if (target instanceof IndirectTarget)
				serializeIndirectEdgeTarget(writer, (IndirectTarget) target);
		}
		writer.writeEndElement();

		writer.writeEndElement();
	}

	/**
	 * Serializes the data of an edge target that models a reference to another
	 * method call that may either receive the callback, or that might again point
	 * at the next method
	 * 
	 * @param writer     The writer to use for serialization
	 * @param edgeTarget The edge target to serialize
	 * @throws XMLStreamException
	 */
	private void serializeIndirectEdgeTarget(XMLStreamWriter writer, IndirectTarget edgeTarget)
			throws XMLStreamException {
		writer.writeStartElement(XmlFileConstants.Tags.indirect);
		serializeCommonEdgeProperties(writer, edgeTarget);

		// Write out the targets
		for (VirtualEdgeTarget target : edgeTarget.getTargets()) {
			if (target instanceof DirectTarget)
				serializeDirectEdgeTarget(writer, (DirectTarget) target);
			else if (target instanceof IndirectTarget)
				serializeIndirectEdgeTarget(writer, (IndirectTarget) target);
		}

		writer.writeEndElement();
	}

	/**
	 * Serializes the data of an edge target that models a direct invocation
	 * 
	 * @param writer     The writer to use for serialization
	 * @param edgeTarget The edge target to serialize
	 */
	private void serializeDirectEdgeTarget(XMLStreamWriter writer, DirectTarget edgeTarget) throws XMLStreamException {
		writer.writeStartElement(XmlFileConstants.Tags.direct);
		serializeCommonEdgeProperties(writer, edgeTarget);
		List<AbstractParameterMapping> pm = edgeTarget.getParameterMappings();
		if (pm != null && !pm.isEmpty()) {
			writer.writeStartElement(XmlFileConstants.Tags.parameterMappings);
			for (AbstractParameterMapping i : pm) {
				if (i instanceof DirectParameterMapping) {
					DirectParameterMapping dt = (DirectParameterMapping) i;
					writer.writeStartElement(XmlFileConstants.Tags.direct);
					writer.writeAttribute("sourceIdx", String.valueOf(dt.getSourceIndex()));
					writer.writeAttribute("targetIdx", String.valueOf(dt.getTargetIndex()));
					writer.writeEndElement();
				} else
					throw new RuntimeException("Unsupported type: " + i.getClass());
			}
			writer.writeEndElement();
		}
		writer.writeEndElement();
	}

	/**
	 * Serializes the common properties of all target edges
	 * 
	 * @param writer     The writer to use for serialization
	 * @param edgeTarget The edge target to serialize
	 * @throws XMLStreamException
	 */
	protected void serializeCommonEdgeProperties(XMLStreamWriter writer, VirtualEdgeTarget edgeTarget)
			throws XMLStreamException {
		writer.writeAttribute(XmlFileConstants.Attributes.subsignature, edgeTarget.getTargetMethod().toString());
		writer.writeAttribute(XmlFileConstants.Attributes.index, Integer.toString(edgeTarget.getArgIndex()));

		String targetPosition = edgeTarget.isBase() ? "base" : "argument";
		writer.writeAttribute(XmlFileConstants.Attributes.targetPosition, targetPosition);
	}

	/**
	 * Serializes the data of an edge source that models an instance invocation
	 * 
	 * @param writer     The writer to use for serialization
	 * @param edgeSource The edge source to serialize
	 */
	private void serializeInstanceEdgeSource(XMLStreamWriter writer, InstanceinvokeSource edgeSource)
			throws XMLStreamException {
		writer.writeStartElement(XmlFileConstants.Tags.source);
		writer.writeAttribute(XmlFileConstants.Attributes.invokeType, XmlFileConstants.InvokeTypes.instanceInvoke);
		writer.writeAttribute(XmlFileConstants.Attributes.subsignature, edgeSource.getSubSignature().toString());
		writer.writeEndElement();
	}

	/**
	 * Serializes the data of an edge source that models an instance invocation
	 * 
	 * @param writer     The writer to use for serialization
	 * @param edgeSource The edge source to serialize
	 */
	private void serializeStaticEdgeSource(XMLStreamWriter writer, StaticinvokeSource edgeSource)
			throws XMLStreamException {
		writer.writeStartElement(XmlFileConstants.Tags.source);
		writer.writeAttribute(XmlFileConstants.Attributes.invokeType, XmlFileConstants.InvokeTypes.staticInvoke);
		writer.writeAttribute(XmlFileConstants.Attributes.signature, edgeSource.getSignature());
		writer.writeEndElement();
	}

}
