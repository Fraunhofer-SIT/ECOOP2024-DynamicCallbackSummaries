package reproduction.code;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import reproduction.utils.CounterMap;
import reproduction.utils.FileUtil;

public class CompareEdgeminerCGminerResults {

	private final static Map<String, String> variableValues = new LinkedHashMap<>();

	private final static Map<String, String> sigToLib = new HashMap<>();

	public static class EdgeSet {
		public List<String> correctEdges = new ArrayList<>();
		public List<String> incompleteEdges = new ArrayList<>();
		public List<String> fpEdges = new ArrayList<>();
		private String name;

		public EdgeSet(String name) {
			this.name = name;
		}

		public void calculate() {
			setVariable("Correct", correctEdges.size());
			setVariable("CorrectRatePercent", getCorrectPercent());
			setVariable("FP", fpEdges.size());
			setVariable("FPRatePercent", getFPPercent());
			setVariable("Incomplete", incompleteEdges.size());
			setVariable("IncompleteRatePercent", getIncPercent());
			setVariable("EdgeCount", allEdgeCount());

		}

		public void calculateSetterAndInit() {
			calcUsingEdgeFilter(" set", "Setter");
			calcUsingEdgeFilter("<init>", "Constructor");
		}

		private void calcUsingEdgeFilter(String filter, String name) {
			EdgeSet esFiltered = new EdgeSet(this.name + "-" + name);

			for (String s : getAllKnownEdges()) {
				String p = s.substring(0, s.indexOf(" =>"));
				if (p.contains(filter)) {
					if (correctEdges.contains(s)) {
						esFiltered.correctEdges.add(s);
					} else if (incompleteEdges.contains(s)) {
						esFiltered.incompleteEdges.add(s);
					} else if (fpEdges.contains(s)) {
						esFiltered.fpEdges.add(s);
					} else
						throw new RuntimeException("Not valid: " + p);
				}
			}

			esFiltered.calculate();

		}

		private void setVariable(String name, Object value) {
			if (value instanceof Double) {
				double d = (double) value;
				//we need some rounded down values for our abstract.
				variableValues.put(this.name + "-" + name + "Rounded", String.valueOf((int) d));

				//make sure this works anywhere
				DecimalFormat df = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US));
				value = String.valueOf(df.format(d));
			}
			String s = value.toString();
			variableValues.put(this.name + "-" + name, s);
		}

		private double getFPPercent() {
			return (100D / allEdgeCount() * fpEdges.size());
		}

		private double getCorrectPercent() {
			return (100D / allEdgeCount() * correctEdges.size());
		}

		private double getIncPercent() {
			return (100D / allEdgeCount() * incompleteEdges.size());
		}

		private int allEdgeCount() {
			return correctEdges.size() + incompleteEdges.size() + fpEdges.size();
		}

		public String getTable() {
			CounterMap<String> correct = new CounterMap<>();
			CounterMap<String> fp = new CounterMap<>();
			CounterMap<String> inc = new CounterMap<>();
			for (String i : correctEdges) {
				correct.count(sigToLib.get(i));
			}
			for (String i : fpEdges) {
				fp.count(sigToLib.get(i));
			}
			for (String i : incompleteEdges) {
				inc.count(sigToLib.get(i));
			}

			String s = "";
			for (Entry<String, Integer> d : correct.sortedValues()) {
				s += d.getKey() + " & " + d.getValue() + " & ";
				s += fp.getCount(d.getKey()) + " & ";
				s += inc.getCount(d.getKey());
				s += "\\\\ \n";
			}
			return s;
		}

		public Set<String> getAllKnownEdges() {
			Set<String> allknown = new HashSet<>();
			allknown.addAll(correctEdges);
			allknown.addAll(incompleteEdges);
			allknown.addAll(fpEdges);
			return allknown;
		}

		public String getConstructorStats() {

			int correct = 0, incomplete = 0, fp = 0;
			for (String s : getAllKnownEdges()) {
				String p = s.substring(0, s.indexOf(" =>"));
				if (p.contains("<init>")) {
					if (correctEdges.contains(s)) {
						correct++;
					}
					if (incompleteEdges.contains(s)) {
						incomplete++;
					}
					if (fpEdges.contains(s)) {
						fp++;
					}
				}
			}
			return "Correct: " + correct + "\nIncomplete: " + incomplete + "\n" + fp;
		}
	}

	public static void main(String[] args) throws Exception {
		FileUtil.assertCorrectWorkingDir();
		
		EdgeSet edgeminer = read("Edgeminer", new File(Constants.EDGEMINER_SUMMARY));
		EdgeSet cgminer = read("Cgminer", new File(Constants.CGMINER_SUMMARY));

		calc(edgeminer);

		calc(cgminer);


		System.out.println("*** RQ 5: Comparison with EdgeMiner ***");
		for (Entry<String, String> i : variableValues.entrySet()) {
			String latex = "\\newcommand{\\" + i.getKey().replace("-", "") + "}{" + i.getValue() + "}";
			System.out.println(latex);
		}

	}

	private static void calc(EdgeSet set) {
		set.calculate();
		set.calculateSetterAndInit();
		System.out.println("Library edge set for " + set.name + ":");
		System.out.println(set.getTable());
	}

	private static EdgeSet read(String name, File r) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		try {

			DocumentBuilder db = dbf.newDocumentBuilder();

			Document doc = db.parse(r);

			// get <staff>
			NodeList list = doc.getElementsByTagName("edge");
			EdgeSet es = new EdgeSet(name);
			for (int temp = 0; temp < list.getLength(); temp++) {

				org.w3c.dom.Node node = list.item(temp);

				if (node.getNodeType() == Node.ELEMENT_NODE) {

					Element element = (Element) node;

					org.w3c.dom.Node src = element.getElementsByTagName("source").item(0);
					org.w3c.dom.Node tgt = element.getElementsByTagName("targets").item(0);

					resolve(es, getSig(src), tgt, isfp(node) || isfp(src) || isfp(tgt),
							isincomplete(node) || isincomplete(src) || isincomplete(tgt));
				}
			}


			return es;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	
	private static String getSig(org.w3c.dom.Node src) {
		Node decl = src.getAttributes().getNamedItem("declaringclass");
		if (decl == null)
			return src.getAttributes().getNamedItem("signature").getTextContent();
		Node subsig = src.getAttributes().getNamedItem("subsignature");
		return "<" + decl.getTextContent() + ": "
				+ subsig.getTextContent() + ">";
	}

	private static void resolve(EdgeSet es, String sig, org.w3c.dom.Node tgt, boolean fp, boolean inc) {
		NodeList cn = tgt.getChildNodes();
		for (int x = 0; x < cn.getLength(); x++) {
			org.w3c.dom.Node dd = cn.item(x);
			if (dd.getNodeName().equals("indirect")) {
				resolve(es, sig, dd, fp || isfp(dd), inc || isincomplete(dd));
			} else if (dd.getNodeName().equals("direct")) {
				boolean isfp = isfp(dd) || fp;
				boolean isinc = isincomplete(dd) || inc;
				if (isfp && isinc)
					isfp = false;

				String cs = classify(sig);
				if (cs == null) {
					throw new RuntimeException("We missed something");
				}
				String edge = sig + " => " + getSig(dd);

				sigToLib.put(edge, cs);
				if (!isfp && !isinc) {
					es.correctEdges.add(edge);
				} else if (isfp) {
					es.fpEdges.add(edge);
				} else if (isinc) {
					es.incompleteEdges.add(edge);
				} else
					throw new RuntimeException();

			}
		}
	}

	//we wanted to be independent of VUSC here, so we implemented these hard-coded 
	//library mappings.
	private static String classify(String osig) {
		String sig = osig.substring(1);
		if (sig.startsWith("java.") || sig.startsWith("javax.") || sig.startsWith("org.w3c."))
			return "Java";
		if (sig.startsWith("org.apache.http.client.") || sig.startsWith("org.apache.http.conn.ssl.")
				|| sig.startsWith("org.apache.http.conn.scheme.") || sig.startsWith("org.apache.http.impl.client"))
			return "Apache HttpClient";
		if (sig.startsWith("android.") || sig.startsWith("dalvik.") || sig.startsWith("androidx.")
				|| sig.startsWith("org.json"))
			return "Android";
		if (sig.startsWith("org.xmlpull.") || sig.startsWith("org.xml."))
			return "Xml Pull Parser";
		if (sig.startsWith("org.apache.html."))
			return "Android";
		if (sig.startsWith("kotlinl."))
			return "Kotlin";
		if (sig.startsWith("org.apache.http.params."))
			return "Apache HttpCore";
		if (sig.startsWith("com.google.android.gms.tasks"))
			return "play-services-basement";
		if (sig.startsWith("com.google.android.gms.maps"))
			return "play-services-maps";
		if (sig.startsWith("com.google.gson") || sig.startsWith("com.google.protobuf"))
			return "Gson";
		if (sig.startsWith("kotlin"))
			return "kotlin";
		if (sig.startsWith("io.reactivex"))
			return "Rxjava";
		if (sig.startsWith("com.google.android.gms.ads"))
			return "play-services-ads-lite";
		if (sig.startsWith("com.google.common"))
			return "Google common";
		if (sig.startsWith("org.cocos2dx"))
			return "Cocos2dx";
		if (sig.startsWith("com.censivn.C3DEngine"))
			return "C3DEngine";
		if (sig.startsWith("com.google.firebase"))
			return "Firebase";
		if (sig.startsWith("org.andengine.opengl.view"))
			return "AndEngine";
		return "Other";
	}

	private static boolean isfp(org.w3c.dom.Node dd) {
		return dd.getAttributes().getNamedItem("fp") != null
				&& dd.getAttributes().getNamedItem("fp").getTextContent().equalsIgnoreCase("true");
	}

	private static boolean isincomplete(org.w3c.dom.Node dd) {
		return dd.getAttributes().getNamedItem("missingEdge") != null
				&& dd.getAttributes().getNamedItem("missingEdge").getTextContent().equalsIgnoreCase("true");
	}

}
