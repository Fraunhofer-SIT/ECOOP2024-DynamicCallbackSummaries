package reproduction.dynamiccallbackidentification.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.codeinspect.collections.CIArrayList;
import soot.Hierarchy;
import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;
import soot.util.Chain;
import soot.util.NumberedString;

public final class Util {

	private Util() {
	}

	public static void writeLaTeX(String name, Object value) {
		if (value instanceof Double) {
			double d = (double) value;
			System.out.println("\\newcommand{\\" + name + "Rounded}{" + String.valueOf((int) d) + "}");

			// make sure this works anywhere
			DecimalFormat df = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US));
			value = String.valueOf(df.format(d));
		}
		String s = value.toString();
		System.out.println("\\newcommand{\\" + name + "}{" + s + "}");
	}

	/**
	 * We want to cache the defining class
	 * 
	 */
	public static class DefiningClassTag implements Tag {

		public static final String TAG_NAME = "DC";

		public SootClass result;

		@Override
		public String getName() {
			return TAG_NAME;
		}

		@Override
		public byte[] getValue() throws AttributeValueException {
			return null;
		}

	}

	/**
	 * @param sm
	 * @param h
	 * @return the highest superclass or interface defining a method with sm's
	 *         subsignature
	 */
	public static SootClass getDefiningClass(SootMethod sm, Hierarchy h) {
		DefiningClassTag ct = (DefiningClassTag) sm.getTag(DefiningClassTag.TAG_NAME);
		if (ct != null) {
			return ct.result;
		}
		SootClass originalClass = sm.getDeclaringClass();
		List<SootClass> worklist = new CIArrayList<>(originalClass);
		Set<SootClass> doneSet = new HashSet<>();
		NumberedString numberedSubSignature = sm.getNumberedSubSignature();
		ct = new DefiningClassTag();
		while (!worklist.isEmpty()) {
			SootClass cl = worklist.remove(0);
			if (doneSet.add(cl)) {
				// Did we find the method in a superclass or interface?
				if (cl != originalClass && cl.declaresMethod(numberedSubSignature)) {
					ct.result = cl;
					sm.addTag(ct);
					return cl;
				}

				// Obtain all superclasses
				if (!cl.isInterface()) {
					List<SootClass> superclassesOf = h.getSuperclassesOf(cl);
					worklist.addAll(superclassesOf);
				}

				// Obtain all interfaces and their superinterfaces
				Chain<SootClass> interfaces = cl.getInterfaces();
				for (SootClass intf : interfaces) {
					if (intf.isInterface()) {
						List<SootClass> superinterfaces = h.getSuperinterfacesOfIncluding(intf);
						worklist.addAll(superinterfaces);
					}
				}
			}
		}

		// Return the original class, since we don't have any closer match
		ct.result = originalClass;
		sm.addTag(ct);
		return originalClass;
	}

}
