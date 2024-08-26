package reproduction.dynamiccallbackidentification.eclipseinterfaces;

import de.codeinspect.dynamicvalues.dynamiccallgraph.config.AbstractDynamicCallGraphConfiguration;

/**
 * Specific configurations that the analysis needs in the dynamic callgraph
 * 
 * 
 *
 */
public class DynamicCallGraphConfiguration extends AbstractDynamicCallGraphConfiguration {

	@Override
	public boolean instrumentLibraryMethods() {
		return true;
	}

}
