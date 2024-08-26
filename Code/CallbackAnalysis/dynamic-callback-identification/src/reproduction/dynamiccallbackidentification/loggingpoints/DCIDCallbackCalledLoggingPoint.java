package reproduction.dynamiccallbackidentification.loggingpoints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import de.codeinspect.platforms.soot.values.LoggingPoint;
import de.codeinspect.platforms.soot.values.requests.IValueRequest;
import reproduction.dynamiccallbackidentification.logic.DCIDCallbackCalledExtractionHandler;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;

public class DCIDCallbackCalledLoggingPoint extends LoggingPoint {

	public SootMethod overriddenMethod;
	public List<Edge> callers = new ArrayList<>();

	public DCIDCallbackCalledLoggingPoint(Stmt stmt, List<IValueRequest> valueRequests, boolean after) {
		super(stmt, valueRequests, after,
				Collections.singletonList(DCIDCallbackCalledExtractionHandler.class.getCanonicalName()));
	}

	public String getDescription() {
		return getStmt().toString();
	}

	@Override
	public boolean isBaseObjectReferenced() {
		return false;
	}

	@Override
	public boolean isParameterReferenced(int paramIdx) {
		return false;
	}

	@Override
	public boolean isReturnValueReferenced() {
		return false;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(callers, overriddenMethod);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}

}
