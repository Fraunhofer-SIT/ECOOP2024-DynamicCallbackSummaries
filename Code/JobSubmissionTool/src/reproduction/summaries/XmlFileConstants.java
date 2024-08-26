package reproduction.summaries;

/**
 * Class with constants for the tag and attribute names in
 * <code>virtualedges.xml</code>
 * 
 * 
 *
 */
class XmlFileConstants {

	/**
	 * Class with constants for the tag names in <code>virtualedges.xml</code>
	 * 
	 * 
	 *
	 */
	static class Tags {

		public static final String root = "virtualedges";
		public static final String edge = "edge";
		public static final String source = "source";
		public static final String targets = "targets";
		public static final String direct = "direct";
		public static final String indirect = "indirect";
		public static final String parameterMappings = "parameterMappings";

	}

	/**
	 * Class with constants for the attribute names in <code>virtualedges.xml</code>
	 * 
	 * 
	 *
	 */
	static class Attributes {

		public static final String type = "type";
		public static final String invokeType = "invoketype";
		public static final String subsignature = "subsignature";
		public static final String signature = "signature";
		public static final String targetPosition = "target-position";
		public static final String index = "index";

	}

	/**
	 * Class with constants for the edge types in <code>virtualedges.xml</code>
	 * 
	 * 
	 *
	 */
	static class EdgeTypes {

		public static final String thread = "THREAD";
		public static final String executor = "EXECUTOR";
		public static final String handler = "HANDLER";
		public static final String asyncTask = "ASYNCTASK";
		public static final String privileged = "PRIVILEGED";
		public static final String genericFake = "GENERIC_FAKE";

	}

	/**
	 * Class with constants for the invocation types supported in callgraph
	 * summaries
	 * 
	 * 
	 *
	 */
	static class InvokeTypes {

		public static final String instanceInvoke = "instance";
		public static final String staticInvoke = "static";

	}

}
