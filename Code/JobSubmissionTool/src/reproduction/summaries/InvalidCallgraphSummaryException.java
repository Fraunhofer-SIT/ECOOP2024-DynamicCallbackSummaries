package reproduction.summaries;

/**
 * Exception that is thrown when an invalid or unknown callgraph summary is
 * encountered
 * 
 * 
 *
 */
public class InvalidCallgraphSummaryException extends RuntimeException {

	private static final long serialVersionUID = 2974157389372241449L;

	public InvalidCallgraphSummaryException() {
		super();
	}

	public InvalidCallgraphSummaryException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public InvalidCallgraphSummaryException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidCallgraphSummaryException(String message) {
		super(message);
	}

	public InvalidCallgraphSummaryException(Throwable cause) {
		super(cause);
	}

}
