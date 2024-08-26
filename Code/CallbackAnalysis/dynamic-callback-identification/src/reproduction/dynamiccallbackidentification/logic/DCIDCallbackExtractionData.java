package reproduction.dynamiccallbackidentification.logic;

import java.util.Arrays;
import java.util.Objects;

import de.codeinspect.dynamicvalues.base.extractionHandlers.IExtractionHandlerData;

public class DCIDCallbackExtractionData implements IExtractionHandlerData {

	private static final long serialVersionUID = 1111L;

	private int callsiteId;
	private int callbackIndex;
	private String[] stacktrace;

	private int instanceId;

	public DCIDCallbackExtractionData() {

	}

	public DCIDCallbackExtractionData(int callsiteId, int callbackIndex, String[] stacktrace, int instanceId) {
		this.callsiteId = callsiteId;
		this.callbackIndex = callbackIndex;
		this.stacktrace = stacktrace;
		this.instanceId = instanceId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(stacktrace);
		result = prime * result + Objects.hash(callbackIndex, callsiteId, instanceId);
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
		DCIDCallbackExtractionData other = (DCIDCallbackExtractionData) obj;
		return callbackIndex == other.callbackIndex && callsiteId == other.callsiteId && instanceId == other.instanceId
				&& Arrays.equals(stacktrace, other.stacktrace);
	}

	public int getCallsiteId() {
		return callsiteId;
	}

	public int getCallbackIndex() {
		return callbackIndex;
	}

	public String[] getStacktrace() {
		return stacktrace;
	}

	public int getInstanceId() {
		return instanceId;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

}
