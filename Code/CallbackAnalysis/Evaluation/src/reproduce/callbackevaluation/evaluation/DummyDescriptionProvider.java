package reproduce.callbackevaluation.evaluation;

import de.codeinspect.analyses.base.xmlparsers.FindingDescription;
import de.codeinspect.analyses.base.xmlparsers.Text;
import de.codeinspect.analyses.base.xmlparsers.TextType;

public class DummyDescriptionProvider
		implements de.codeinspect.analyses.base.descriptionproviders.findings.IFindingDescriptionProvider {

	@Override
	public FindingDescription getDescription() {
		FindingDescription desc = new FindingDescription();
		Text text = new Text();
		text.setType(TextType.TEXT);
		text.setValue("Dummy description");
		desc.setLongDescription(text);
		desc.setShortDescription(text);
		desc.setMitigation(text);
		return desc;
	}

}
