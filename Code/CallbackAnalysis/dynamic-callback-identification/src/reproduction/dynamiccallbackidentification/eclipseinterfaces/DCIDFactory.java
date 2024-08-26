package reproduction.dynamiccallbackidentification.eclipseinterfaces;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.codeinspect.base.Config;
import de.codeinspect.base.projects.ICIProject;
import de.codeinspect.base.telemetry.SimpleTimingData;
import de.codeinspect.base.telemetry.TelemetryManager;
import de.codeinspect.collections.CIArrayList;
import de.codeinspect.collections.CIArrays;
import de.codeinspect.collections.CIHashSet;
import de.codeinspect.collections.CISet;
import de.codeinspect.dynamicvalues.extraction.IRuntimeValueNotificationFactory;
import de.codeinspect.dynamicvalues.extraction.RuntimeValueNotifier;
import de.codeinspect.instrumentation.IInstrumentationStep;
import de.codeinspect.instrumentation.instrumentations.AddApplicationClassInstrumentation;
import de.codeinspect.instrumentation.instrumentations.AddFieldInstrumentation;
import de.codeinspect.instrumentation.instrumentations.AddLocalInstrumentation;
import de.codeinspect.instrumentation.instrumentations.InsertAfterInstrumentation;
import de.codeinspect.instrumentation.instrumentations.InsertBeforeInstrumentation;
import de.codeinspect.instrumentation.instrumentations.ReplaceNewObjectTypeInstrumentation;
import de.codeinspect.instrumentation.instrumentations.ReplaceValueBoxValue;
import de.codeinspect.platforms.jvm.dependencies.DependencyFinderUtils;
import de.codeinspect.platforms.soot.callgraph.ReflectionUtils;
import de.codeinspect.platforms.soot.values.LoggingPoint;
import de.codeinspect.platforms.soot.values.requests.IValueRequest;
import de.codeinspect.platforms.soot.values.requests.LazySootFieldValueRequest;
import de.codeinspect.platforms.soot.values.requests.SootValueRequest;
import de.codeinspect.soot.CIJimple;
import de.codeinspect.soot.CIJimpleBody;
import de.codeinspect.soot.CIJimpleLocal;
import de.codeinspect.soot.CIScene;
import de.codeinspect.soot.CISootClass;
import de.codeinspect.soot.CISootField;
import de.codeinspect.soot.CISootFieldRef;
import de.codeinspect.soot.CISootMethod;
import de.codeinspect.soot.CISootMethodRef;
import de.codeinspect.soot.SootInstanceBase;
import de.codeinspect.soot.constants.BooleanConstant;
import de.codeinspect.soot.jimple.expressions.CIStaticInvokeExpr;
import de.codeinspect.soot.jimple.statements.CIIdentityStmt;
import de.codeinspect.soot.jimple.statements.CIIfStmt;
import de.codeinspect.soot.jimple.statements.CINopStmt;
import de.codeinspect.soot.jimple.statements.CIReturnStmt;
import de.codeinspect.soot.sigs.MethodSignatureArray;
import de.codeinspect.soot.taintTracking.dynamic.TaintSource;
import de.codeinspect.soot.utils.AnalysisUtils;
import de.codeinspect.soot.utils.TypeUtils;
import de.codeinspect.values.constants.network.retrofit2.RetrofitUtils;
import gnu.trove.map.hash.TCustomHashMap;
import gnu.trove.strategy.HashingStrategy;
import reproduction.dynamiccallbackidentification.loggingpoints.DCIDCallbackCalledLoggingPoint;
import reproduction.dynamiccallbackidentification.logic.DCIDCallbackCalledExtractionHandler;
import reproduction.dynamiccallbackidentification.util.Util;
import soot.AnySubType;
import soot.ArrayType;
import soot.Body;
import soot.BooleanType;
import soot.FastHierarchy;
import soot.Hierarchy;
import soot.IntType;
import soot.Local;
import soot.Modifier;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.UnitPatchingChain;
import soot.Value;
import soot.VoidType;
import soot.jimple.AssignStmt;
import soot.jimple.ClassConstant;
import soot.jimple.Constant;
import soot.jimple.GotoStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LongConstant;
import soot.jimple.NewExpr;
import soot.jimple.NullConstant;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.ThisRef;
import soot.jimple.infoflow.android.entryPointCreators.AndroidEntryPointUtils;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.internal.JEqExpr;
import soot.options.Options;
import soot.tagkit.IntegerConstantValueTag;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

/**
 *  This class generates instrumentation steps that log trail
 *         events. furthermore, it generates logging points to transmit events
 *         back to the host at a central location (not per instrumentation)
 */
/**
 * 
 *
 */
public class DCIDFactory implements IRuntimeValueNotificationFactory {

	private static WrappingSelectionMode SELECTION_MODE;
	private Set<SootClass> wrappedConcreteClasses;

	public enum WrappingSelectionMode {
		ONLY_KNOWN, ALL_INTERFACES, ALL_ABSTRACT
	}

	public enum WrappingMode {
		/**
		 * Wraps direct as well as stuff like Collections.singleton(Set)
		 */
		DIRECT_AND_KNOWN_INDIRECT(true, false),
		/**
		 * Wraps direct as well as *all* indirect stuff. Might cause problems.
		 * 
		 */
		ALL(true, true),

		/**
		 * Do not wrap at all
		 */
		NONE(false, false);

		private boolean indirectSelected;
		private boolean allIndirect;

		WrappingMode(boolean indirectSelected, boolean allIndirect) {
			this.indirectSelected = indirectSelected;
			this.allIndirect = allIndirect;
			if (allIndirect && !indirectSelected)
				throw new RuntimeException("Inconsistent");
		}

		public boolean isIndirectSelected() {
			return indirectSelected;
		}

		public boolean isAllIndirect() {
			return allIndirect;
		}

	}

	public static final WrappingMode MODE;

	static {
		switch (Config.getString("CallbackGeneration.WrappingMode", false, "").toLowerCase().replace("_", "")) {
		default:
		case "all":
			MODE = WrappingMode.ALL;
			break;
		case "none":
			MODE = WrappingMode.NONE;
			break;
		case "directandvalidindirect":
			MODE = WrappingMode.DIRECT_AND_KNOWN_INDIRECT;
			break;

		}
		switch (Config.getString("CallbackGeneration.WrappingSelectionMode", false, "").toLowerCase().replace("_",
				"")) {
		case "onlyknown":
			SELECTION_MODE = WrappingSelectionMode.ONLY_KNOWN;
			break;
		case "allinterfaces":
			SELECTION_MODE = WrappingSelectionMode.ALL_INTERFACES;
			break;
		default:
		case "allabstract":
			SELECTION_MODE = WrappingSelectionMode.ALL_ABSTRACT;
			break;

		}
		LogManager.getLogger(DCIDFactory.class).info("Callback mode: " + MODE);

	}

	private final static Logger logger = LogManager.getLogger(DCIDFactory.class);

	private static final String FIELD_PREFIX_CALLSITE_ID = "CALLBACK_CALLSITE_ID";
	private static final String FIELD_PREFIX_CALLSITE_ARG_INDEX = "CALLBACK_CALLSITE_ARG_INDEX";
	private static final String FIELD_PREFIX_CALLSITE_THREAD = "CALLBACK_CALLSITE_THREAD";

	private static final CISet<String> BLACKLISTED_CALLBACKS = new CIHashSet<>(
			new String[] { "void onClick(android.view.View)", "void onDraw(android.graphics.Canvas)",
					"boolean equals(java.lang.Object)", "void finalize()" });
	private static final String WRAPPER_PCK = "reproduction.wrapped.";
	private static final boolean CHECK_TOPOLOGICAL_SORTING = false;

	public final boolean BASE_CALLBACKS = Config.getBoolean("CallbackGeneration.UseBaseCallbackCandidates", false);

	private int nextCallsiteId = 1;
	private int id = 0;
	private int idConverter = 0;
	private Map<SootClass, Integer> idxClass = new HashMap<>();

	private int getNextCallsiteId() {
		return nextCallsiteId++;
	}

	private static class ClassInstrumentationInfo {
		CISootFieldRef callsiteIdField;
		CISootFieldRef callsiteArgIndexField;
		CISootFieldRef callsiteThreadField;

		public ClassInstrumentationInfo(CISootFieldRef callsiteIdFieldRef, CISootFieldRef callsiteArgIndexFieldRef,
				CISootFieldRef callsiteThreadField) {
			this.callsiteIdField = callsiteIdFieldRef;
			this.callsiteArgIndexField = callsiteArgIndexFieldRef;
			this.callsiteThreadField = callsiteThreadField;
		}
	}

	/**
	 * A candidate for a callback identified in the code. This could be an object
	 * passed to as a parameter, where the callback is on this object.
	 * Alternatively, it could be a callback on the base object.
	 * 
	 * 
	 *
	 */
	public static class CallbackCandidate {

		private final int paramIdx;
		private final Local local;
		private final Stmt s;

		/**
		 * Creates a new callback candidate on the base object
		 * 
		 * @param s the statement
		 * @param l The base local
		 */
		public CallbackCandidate(Stmt s, Local l) {
			this.s = s;
			this.paramIdx = -1;
			this.local = l;
		}

		/**
		 * Creates a new callback candidate on a parameter
		 * 
		 * @param paramIdx The parameter that could be a candidate
		 * @param l        The argument local
		 */
		public CallbackCandidate(Stmt s, int paramIdx, Local l) {
			this.s = s;
			this.paramIdx = paramIdx;
			this.local = l;
		}

		/**
		 * Returns the statement
		 * 
		 * @return the statement
		 */
		public Stmt getStatement() {
			return s;
		}

		/**
		 * Gets the index of the parameter that receives the object that could
		 * potentially be a callback
		 * 
		 * @return The parameter index of the parameter index
		 */
		public int getParamIdx() {
			return paramIdx;
		}

		/**
		 * Gets the local that references the potential callback object
		 * 
		 * @return The local that references the potential callback object
		 */
		public Local getLocal() {
			return local;
		}

		/**
		 * Gets whether this callback candidate is on the base local
		 * 
		 * @return True if this callback candidate is on the base local, false otherwise
		 */
		public boolean isBase() {
			return paramIdx == -1;
		}

		/**
		 * Gets whether this callback candidate is on an argument passed to a method
		 * call
		 * 
		 * @return True if this callback candidate is on a parameter, false otherwise
		 */
		public boolean isParameter() {
			return paramIdx >= 0;
		}

		@Override
		public String toString() {
			if (isBase())
				return "Base local";
			if (isParameter())
				return String.format("Parameter %d", paramIdx);
			return "<Unknown candidate>";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((local == null) ? 0 : local.hashCode());
			result = prime * result + paramIdx;
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
			CallbackCandidate other = (CallbackCandidate) obj;
			if (local == null) {
				if (other.local != null)
					return false;
			} else if (!local.equals(other.local))
				return false;
			if (paramIdx != other.paramIdx)
				return false;
			return true;
		}

	}

	private Map<SootClass, ClassInstrumentationInfo> instrumentedClasses;

	private Set<SootClass> concreteWrappers;
	private Set<SootClass> concreteWrappersBase;

	private RefType objectType = Scene.v().getObjectType();
	private SootClass converterClass;
	private SootClass wrapperInterface;
	private Set<SootClass> wrappedClassesBase;
	private Map<RefType, SootMethod> converterMethods = new HashMap<>();

	/**
	 * 
	 * @param sc
	 * @return whether register functions from this class should be ignored
	 */
	public boolean isClassBlacklisted(SootClass sc) {
		String pckg = sc.getPackageName();
		if (concreteWrappers != null && concreteWrappersBase != null)
			if (concreteWrappers.contains(sc) || concreteWrappersBase.contains(sc)
					|| sc.getName().startsWith(WRAPPER_PCK))
				return false;
		return pckg.startsWith("de.codeinspect."); // || sc.getName().equals("android.content.Context");
	}

	private static String[] KNOWN_BASES = new String[] { "java.util.List", "java.util.Set", "java.util.Collection",
			"java.util.Map", "java.util.Iterator", "java.util.ListIterator", "java.util.Enumeration"

	};

	private static String[] KNOWN_INDIRECT = new String[] {

			"<java.util.Collections: java.util.Set singleton(java.lang.Object)>",

			"<java.util.Collections: java.util.List singletonList(java.lang.Object)>",

			"<java.util.Collections: java.util.Map singletonMap(java.lang.Object,java.lang.Object)>",

			"<java.util.Collections: java.util.Collection synchronizedCollection(java.util.Collection)>",

			"<java.util.Collections: java.util.List synchronizedList(java.util.List)>",

			"<java.util.Collections: java.util.Map synchronizedMap(java.util.Map)>",

			"<java.util.Collections: java.util.NavigableSet synchronizedNavigableSet(java.util.NavigableSet)>",

			"<java.util.Collections: java.util.SortedSet synchronizedSortedSet(java.util.SortedSet)>",

			"<java.util.Collections: java.util.Set synchronizedSet(java.util.Set)>",

			"<java.util.Collections: java.util.Collection unmodifiableCollection(java.util.Collection)>",

			"<java.util.Collections: java.util.List unmodifiableList(java.util.List)>",

			"<java.util.Collections: java.util.Map unmodifiableMap(java.util.Map)>",

			"<java.util.Collections: java.util.Set unmodifiableSet(java.util.Set)>",
			"<java.util.Arrays: java.util.List asList(java.lang.Object[])>",

			// a little bit less safe, but should still be alright:
			"<java.util.Map: java.util.Set keySet()>", "<java.util.Map: java.util.Collection values()>",
			"<java.util.List: java.util.Iterator iterator()>",
			"<java.util.List: java.util.ListIterator listIterator()>",
			"<java.util.List: java.util.ListIterator listIterator(int)>",
			"<java.util.Enumeration: java.util.Iterator asIterator()>",
			"<java.util.Collection: java.util.Iterator iterator()>",
			"<java.lang.Iterable: java.util.Iterator iterator()>", };

	private void writeDebug(String f) {
		CIScene.v().allowLocalNames = true;
		String s = Options.v().output_dir();
		int p = Options.v().output_format();
		Options.v().set_output_format(Options.output_format_jimple);
		new File(f).mkdirs();
		try {
			FileUtils.deleteDirectory(new File(f));
		} catch (IOException e) {
			e.printStackTrace();
		}
		Options.v().set_output_dir(new File(f).getAbsolutePath());
		soot.PackManager.v().writeOutput();
		Options.v().set_output_format(p);
		;
		Options.v().set_output_dir(s);
	}

	@Override
	public synchronized RuntimeValueNotifier createRuntimeValueNotifier(ICIProject project) {
		logger.info("Starting DCID Instrumentation");
		long millisBefore = System.currentTimeMillis();
		AndroidEntryPointUtils entryPointUtils = new AndroidEntryPointUtils();
		List<Unit> instrumentations = new ArrayList<>();

		instrumentedClasses = new HashMap<>();
		concreteWrappers = new HashSet<>();
		concreteWrappersBase = new HashSet<>();
		nextCallsiteId = 1;
		Set<LoggingPoint> loggingPoints = new HashSet<LoggingPoint>();
		Set<IInstrumentationStep> instrumentation = new LinkedHashSet<IInstrumentationStep>();
		String classI = WRAPPER_PCK + "Converter";
		SootClass o = Scene.v().getSootClassUnsafe(classI);
		if (o != null)
			Scene.v().removeClass(o);

		Set<SootMethod> instrumentedCallbackMethods = new HashSet<>();

		// could use array as indexes are incremental, but hashmap is safer
		HashMap<Integer, Unit> callsites = new HashMap<>();
		/*
		 * Global Instrumentation: create utility class containing identityhashmap
		 * methods registercallbackinstance
		 * 
		 */
		/*
		 * 1. Iterate through all user methods.
		 * 
		 * 2. Look for invoke expressions
		 * 
		 * 3. Iterate through parameter values types: Filter types that subclass a
		 * library class but are not library types themselves.
		 * 
		 * 4. If such a value is passed:
		 * 
		 * 4.1 Instrument each overridden method in the value's class with a logging
		 * point indicating the invoked method and the parameter index.
		 * 
		 * 4.2 Add a logging point to the method invocation, indicating the method
		 * signature.
		 */
		Set<SootMethod> wrappedMethods = new HashSet<>();
		FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();

		wrapperInterface = Scene.v().getSootClass("reproduction.dynamiccallbackidentification.logic.DCIDWrapped");
		nextClass: for (SootClass c : Scene.v().getApplicationClasses()) {
			if (c.getName().startsWith("de.codeinspect."))
				continue;
			if (c.getName().startsWith("reproduction."))
				continue;
			// Application classes itself do not need to be wrapped again, because we
			// already can instrument them directly.
			for (SootMethod m : c.getMethods()) {

				/*
				 * Retrofit disallows any inheritance on their interfaces:
				 * java.lang.IllegalArgumentException: API interfaces must not extend other
				 * interfaces.
				 */
				if (RetrofitUtils.isRetrofitMethod(m))
					continue nextClass;
			}
			c.addInterface(wrapperInterface);
		}
		wrappedConcreteClasses = new LinkedHashSet<>();
		wrappedClassesBase = new LinkedHashSet<>();

		Set<SootClass> usedInApp = getAbstractUsedInApp();

		Iterable<SootClass> unavail = getUnavailableClassesIterator();
		switch (SELECTION_MODE) {
		case ONLY_KNOWN:
			for (String i : KNOWN_BASES) {
				wrappedClassesBase.add(Scene.v().getSootClass(i));
			}
			break;
		case ALL_ABSTRACT:
			for (SootClass i : unavail) {
				boolean intf = i.isInterface();
				if (i.isPublic()) {
					if ((i.isAbstract() || intf) && usedInApp.contains(i)) {
						if (intf)
							wrappedClassesBase.add(i);
						else {
							SootMethod im = i.getMethodUnsafe("void <init>()");
							if (im != null && im.isPublic())
								wrappedClassesBase.add(i);
						}
					}
				}
			}
			wrappedClassesBase.add(Scene.v().getSootClassUnsafe("java.lang.CharSequence"));
			break;
		case ALL_INTERFACES:
			for (SootClass i : unavail) {
				if (i.isInterface() && usedInApp.contains(i) && i.isPublic())
					wrappedClassesBase.add(i);
			}
			break;
		}
		// there are some funky casts going on there
		wrappedClassesBase.remove(Scene.v().getSootClassUnsafe("org.w3c.dom.Element"));

		for (SootClass c : unavail) {
			if (c.isAbstract())
				continue;
			if (c.isFinal())
				continue;
			if (usedInApp.contains(c)) {
				for (SootClass i : wrappedClassesBase) {
					if (fh.canStoreClass(c, i)) {
						wrappedConcreteClasses.add(c);
					}
				}
			}
		}

		for (String oo : KNOWN_INDIRECT) {
			SootMethod co = Scene.v().grabMethod(oo);
			if (co != null)
				wrappedMethods.add(co);
		}
		for (SootClass i : wrappedClassesBase) {
			getWrappedBaseClass(instrumentation, i);
		}
		for (SootClass i : wrappedConcreteClasses) {
			getWrappedConcreteClass(instrumentation, RefType.v(i));
		}

		MethodSignatureArray reflection = new MethodSignatureArray(
				"<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>",
				"<java.lang.reflect.Constructor: java.lang.Object newInstance(java.lang.Object[])>",
				"<java.lang.Class: java.lang.Object newInstance()>");
		SootClass str = getWrappedConcreteClass(instrumentation, RefType.v("java.lang.CharSequence"));
		SootMethod mWrapCharSequence = new CISootMethod("wrapCharSequence",
				Arrays.asList(RefType.v("java.lang.CharSequence")), RefType.v("java.lang.CharSequence"),
				Modifier.STATIC | Modifier.PUBLIC);
		str.getOrAddMethod(mWrapCharSequence);
		CIJimple j = CIJimple.v();
		JimpleBody bds = j.newBody(mWrapCharSequence);
		mWrapCharSequence.setActiveBody(bds);
		bds.insertIdentityStmts();
		Local l = j.newLocal("l", str.getType());
		bds.getLocals().add(l);
		bds.getUnits().add(j.newAssignStmt(l, j.newNewExpr(str.getType())));
		bds.getUnits()
				.add(j.newInvokeStmt(
						j.newSpecialInvokeExpr(l, str.getMethodUnsafe("void <init>(java.lang.CharSequence)").makeRef(),
								Arrays.asList(bds.getParameterLocal(0)))));
		bds.getUnits().add(j.newReturnStmt(l));
		bds.validate();

		for (SootClass sc : Scene.v().getClasses()) {
			if (skip(sc))
				continue;

			for (SootMethod sm : sc.getMethods()) {
				if (!sm.isConcrete())
					continue;
				Body body = sm.retrieveActiveBody();
				Local locWrapped = null;
				for (Unit u : body.getUnits()) {
					Stmt s = (Stmt) u;
					if (s instanceof AssignStmt) {
						AssignStmt ass = ((AssignStmt) s);
						Value rop = ass.getRightOp();
						if (rop instanceof NewExpr) {
							NewExpr newE = (NewExpr) rop;
							RefType bt = newE.getBaseType();
							if (wrappedConcreteClasses.contains(bt.getSootClass())) {
								IInstrumentationStep.doImmediately(new ReplaceNewObjectTypeInstrumentation(newE,
										getWrappedConcreteClass(instrumentation, bt).getType()));
							}
						}
					}
					if (!s.containsInvokeExpr())
						continue;

					InvokeExpr invoke = s.getInvokeExpr();
					boolean c = false;

					if (!MODE.isAllIndirect() && MODE.isIndirectSelected()
							&& ReflectionUtils.mightCall(u, wrappedMethods))
						c = true;
					if (c || MODE.isAllIndirect()) {
						SootClass declClass = invoke.getMethod().getDeclaringClass();
						if (s instanceof AssignStmt && (declClass.isPhantom() || declClass.isLibraryClass()
								|| invoke.getMethod().isNative())) {
							boolean ok = c;
							if (MODE.isAllIndirect()) {
								Type t = ((AssignStmt) s).getRightOp().getType();
								if (t instanceof RefType) {
									for (SootClass i : wrappedClassesBase) {
										if (fh.canStoreType(t, i.getType())) {
											ok = true;
											break;
										}
									}
								}
							}

							if (reflection.contains(invoke.getMethod()))
								ok = true;
							if (ok) {
								AssignStmt assign = (AssignStmt) s;
								Value lop = assign.getLeftOp();
								if (locWrapped == null) {
									locWrapped = CIJimple.v().newLocal("lcl", objectType);
									IInstrumentationStep.doImmediately(new AddLocalInstrumentation(body, locWrapped));
								}
								IInstrumentationStep
										.doImmediately(new ReplaceValueBoxValue(assign.getLeftOpBox(), locWrapped));
								CIStaticInvokeExpr staticinv = j.newStaticInvokeExpr(
										createConverterMethodFirstStep(instrumentation, (RefType) lop.getType())
												.makeRef(),
										locWrapped);
								List<Unit> ll = Arrays.asList(j.newAssignStmt(locWrapped, staticinv),
										j.newAssignStmt(lop, j.newCastExpr(locWrapped, invoke.getType())));
								IInstrumentationStep.doImmediately(new InsertAfterInstrumentation(body, ll, s));
							}
							continue;
						}
					}
					if (invoke instanceof SpecialInvokeExpr) {
						SpecialInvokeExpr sp = (SpecialInvokeExpr) invoke;
						if (sp.getMethodRef().getName().equals("<init>")) {
							if (wrappedConcreteClasses.contains(sp.getMethod().getDeclaringClass())) {
								if (sp.getMethod().getDeclaringClass() == sc.getSuperclassUnsafe()
										&& body.getMethod().isConstructor()) {
									// Possibly the <init> call to the super implementation.
									// Do nothing instead.
									Set<Local> thisObjs = new HashSet<>();
									thisObjs.add(body.getThisLocal());
									Unit crt = body.getUnits().getFirst();
									Set<Unit> seen = new HashSet<>();
									while (true) {
										if (!seen.add(crt))
											break;
										crt = body.getUnits().getSuccOf(crt);
										if (crt == s || crt == null)
											break;
										if (crt instanceof AssignStmt) {
											AssignStmt assign = (AssignStmt) crt;
											if (assign.getLeftOp() instanceof Local) {
												Local ll = (Local) assign.getLeftOp();
												if (thisObjs.contains(assign.getRightOp()))
													thisObjs.add(ll);
												else
													thisObjs.remove(ll);
											}
										} else if (crt instanceof GotoStmt) {
											crt = ((GotoStmt) crt).getTarget();
										}
									}
									if (thisObjs.contains(sp.getBase()))
										continue;
								}
								SootClass cl = getWrappedConcreteClass(instrumentation,
										sp.getMethod().getDeclaringClass().getType());
								SootMethod m = cl.getMethod(sp.getMethod().getSubSignature());
								IInstrumentationStep.doImmediately(new ReplaceValueBoxValue(s.getInvokeExprBox(),
										CIJimple.v().newSpecialInvokeExpr((Local) sp.getBase(), m.makeRef(),
												sp.getArgs())));
							}
						}
					}
				}
				replaceCharSequences(body, mWrapCharSequence);
			}
		}
		CIScene.v().releaseCallGraph();
		CIScene.v().releaseActiveHierarchy();
		CIScene.v().releaseFastHierarchy();
		CIScene.v().releasePointsToAnalysis();
		CIScene.v().releaseReachableMethods();

		for (SootClass sc : Scene.v().getClasses()) {
			if (isLibraryClass(sc, true))
				continue;
			if (sc.getName().startsWith("de.codeinspect.") || sc.getName().startsWith("reproduction.")
					|| SystemClassHandler.v().isClassInSystemPackage(sc))
				continue;
			if (sc.isPhantom())
				continue;

			for (SootMethod sm : sc.getMethods()) {
				if (!sm.isConcrete())
					continue;
				Body body = sm.retrieveActiveBody();
				for (Unit u : body.getUnits()) {
					Stmt s = (Stmt) u;
					if (!s.containsInvokeExpr())
						continue;

					InvokeExpr invoke = s.getInvokeExpr();
					if (ReflectionUtils.isReflectiveMethodCall(s))
						continue;
					SootClass targetClass = invoke.getMethod().getDeclaringClass();

					if (isClassBlacklisted(targetClass))
						continue;
					if (!isLibraryClass(targetClass, true))
						continue;

					if (ReflectionUtils.isReflectiveMethodCall(s) || ReflectionUtils.isReflectiveFieldAccess(s))
						continue;

					// Check whether a potential callback is passed as a parameter
					List<CallbackCandidate> callbackCandidates = new ArrayList<>();
					{
						SootMethod declaredCallee = invoke.getMethod();

						for (int argIndex = 0; argIndex < invoke.getArgCount(); argIndex++) {
							Value arg = invoke.getArg(argIndex);
							Type declaredType = declaredCallee.getParameterType(argIndex);
							if (isPotentialCallback(arg, declaredType, sm, declaredCallee, instrumentation,
									instrumentedCallbackMethods, loggingPoints, entryPointUtils)) {
								callbackCandidates.add(new CallbackCandidate(s, argIndex, (Local) arg));
							}
						}
					}

					// Check whether a call on the base object may invoke a callback
					if (invoke instanceof InstanceInvokeExpr && BASE_CALLBACKS) {
						InstanceInvokeExpr iiexpr = (InstanceInvokeExpr) invoke;
						SootMethod declaredCallee = invoke.getMethod();
						if (isPotentialCallback(iiexpr.getBase(), declaredCallee.getDeclaringClass().getType(), sm,
								declaredCallee, instrumentation, instrumentedCallbackMethods, loggingPoints,
								entryPointUtils)) {

							callbackCandidates.add(new CallbackCandidate(s, (Local) iiexpr.getBase()));
						}
					}

					if (!callbackCandidates.isEmpty()) {
						int callsiteId = getNextCallsiteId();
						generateCallsiteInstrumentation(body, u, invoke, callsiteId, callbackCandidates,
								instrumentation);
						callsites.put(callsiteId, u);
						Local ignore = null;
						if (((Stmt) u).containsInvokeExpr()) {
							InvokeExpr e = ((Stmt) u).getInvokeExpr();
							if (e instanceof SpecialInvokeExpr) {
								// We have a problem, because we may not access the local until *after* the
								// <init> call.
								if (e.getMethod().isConstructor()) {
									SpecialInvokeExpr si = (SpecialInvokeExpr) e;
									ignore = (Local) si.getBase();

								}
							}
						}
						for (CallbackCandidate candidate : callbackCandidates) {
							if (isEquivalentAt(u, ignore, candidate.getLocal()))
								continue;
							boolean isAfter = false;
							Stmt ss = (Stmt) u;
							if (ss.containsInvokeExpr() && ss instanceof InstanceInvokeExpr
									&& ((InstanceInvokeExpr) ss).getBase() == candidate.local)
								isAfter = true;
							CIScene.v().getDynamicTaintManager()
									.registerSource(new TaintSource((Stmt) u, candidate.getLocal(), isAfter));
						}

						DebugDynamicCallback.v().addCandidates(callbackCandidates);
					}
				}
			}
		}
		Map<Unit, InsertBeforeInstrumentation> insertbefores = new HashMap<>();
		for (Iterator<IInstrumentationStep> i = instrumentation.iterator(); i.hasNext();) {
			IInstrumentationStep step = i.next();
			if (step instanceof InsertBeforeInstrumentation) {
				InsertBeforeInstrumentation ibi = (InsertBeforeInstrumentation) step;
				InsertBeforeInstrumentation other = insertbefores.get(ibi.pointToInsert);
				if (other == null) {
					insertbefores.put(ibi.pointToInsert, ibi);
				} else {
					other.toAdd = CIArrays.concatenate(other.toAdd, ibi.toAdd);
					i.remove();
				}
			}
		}

		createConverterMethodSecondStep(instrumentation);

		SootClass scRuntimeUtils = Scene.v()
				.getSootClassUnsafe("reproduction.dynamiccallbackidentification.logic.DCIDRuntimeUtils");
		SootField f = scRuntimeUtils.getField("int idCount");
		f.removeTag(IntegerConstantValueTag.NAME);
		IntegerConstantValueTag it = new IntegerConstantValueTag(id + 1);
		f.addTag(it);
		SootMethod clinit = scRuntimeUtils.getMethod("void <clinit>()");
		for (Unit u : clinit.getActiveBody().getUnits()) {
			if (u instanceof AssignStmt) {
				Value lop = ((AssignStmt) u).getLeftOp();
				if (lop instanceof StaticFieldRef) {
					if (((StaticFieldRef) lop).getField() == f) {
						clinit.getActiveBody().getUnits().remove(u);
						break;
					}
				}
			}
		}

		// Clean up
		converterClass = null;
		wrappedConcreteClasses = null;
		wrapperInterface = null;
		wrappedClassesBase = null;
		converterMethods = new HashMap<>();
		concreteWrappersBase.clear();
		concreteWrappers.clear();
		instrumentedClasses.clear();
		idConverter = 0;
		id = 0;

		TelemetryManager.reportTelemetry(project, "DCID",
				new SimpleTimingData(System.currentTimeMillis() - millisBefore));
		return new DCIDListener(loggingPoints, instrumentation, instrumentations,
				SootInstanceBase.getCurrentSootInstance(), callsites);
	}

	private void replaceCharSequences(Body body, SootMethod mWrapString) {
		Local l = null, l2 = null;
		for (Unit u : new ArrayList<>(body.getUnits())) {
			Stmt s = (Stmt) u;
			if (s.containsInvokeExpr()) {
				InvokeExpr iexpr = s.getInvokeExpr();
				for (int i = 0; i < iexpr.getArgCount(); i++) {
					Value v = iexpr.getArg(i);
					if (v instanceof StringConstant) {
						if (iexpr.getMethodRef().getParameterType(i) == RefType.v("java.lang.CharSequence")) {
							if (l == null) {
								l = Jimple.v().newLocal("chr", RefType.v("java.lang.CharSequence"));
								body.getLocals().add(l);
							}
							body.getUnits()
									.insertBefore(Jimple.v().newAssignStmt(l,
											Jimple.v().newStaticInvokeExpr(mWrapString.makeRef(), Arrays.asList(v))),
											s);
							iexpr.setArg(i, l);
						}
					}
				}
			}
		}
	}

	public boolean isEquivalentAt(Unit u, Local l1, Local l2) {
		SimpleLocalDefs ld = new SimpleLocalDefs(new ExceptionalUnitGraph(AnalysisUtils.getBody(u)));
		List<Unit> df1 = ld.getDefsOfAt(l2, u);
		List<Unit> df2 = ld.getDefsOfAt(l1, u);
		for (Unit l : df2) {
			if (df1.contains(l))
				return true;
		}
		return false;
	}

	private Set<SootClass> getAbstractUsedInApp() {
		Set<SootClass> usedInApp = new HashSet<>();
		for (SootClass sc : Scene.v().getClasses()) {
			if (skip(sc))
				continue;

			for (SootMethod sm : sc.getMethods()) {
				if (!sm.isConcrete())
					continue;
				Body body = sm.retrieveActiveBody();
				for (Unit u : body.getUnits()) {
					Stmt s = (Stmt) u;
					if (s instanceof AssignStmt) {
						Value rop = ((AssignStmt) s).getRightOp();
						if (rop instanceof NewExpr) {
							if (rop.getType() instanceof RefType) {
								RefType rt = (RefType) rop.getType();
								addHierarchy(usedInApp, rt.getSootClass());
							}
						}
					}
					if (s.containsInvokeExpr()) {
						for (Type e : s.getInvokeExpr().getMethodRef().getParameterTypes()) {
							if (e instanceof RefType) {
								RefType t = (RefType) e;
								SootClass scl = t.getSootClass();
								if (scl.isAbstract() || scl.isInterface()) {
									addHierarchy(usedInApp, scl);
								}
							}
						}
					}
				}
			}
		}
		return usedInApp;
	}

	private void addHierarchy(Set<SootClass> usedInApp, SootClass scl) {
		if (scl.isInterface()) {
			usedInApp.addAll(Scene.v().getActiveHierarchy().getSuperinterfacesOfIncluding(scl));
		} else {
			for (SootClass s : Scene.v().getActiveHierarchy().getSuperclassesOfIncluding(scl)) {
				for (SootClass ifc : s.getInterfaces()) {
					if (ifc.isInterface())
						usedInApp.addAll(Scene.v().getActiveHierarchy().getSuperinterfacesOfIncluding(ifc));
				}
				usedInApp.add(s);
			}
		}
	}

	private boolean skip(SootClass sc) {
		if (sc == null)
			return true;
		if (isLibraryClass(sc, true))
			return true;
		if (sc.getName().startsWith("de.codeinspect.") || sc.getName().startsWith("reproduction.")
				|| SystemClassHandler.v().isClassInSystemPackage(sc) || sc.getName().startsWith(WRAPPER_PCK))
			return true;
		if (sc.isPhantom())
			return true;
		return false;
	}

	private Iterable<SootClass> getUnavailableClassesIterator() {
		Set<SootClass> res = new LinkedHashSet<>();
		Set<SootClass> processed = new LinkedHashSet<>();
		Hierarchy ah = Scene.v().getActiveHierarchy();
		Stack<SootClass> stack = new Stack<>();
		stack.addAll(Scene.v().getLibraryClasses());
		// stack.addAll(Scene.v().getPhantomClasses());
		while (!stack.isEmpty()) {
			SootClass c = stack.peek();
			if (res.contains(c) || processed.contains(c)) {
				stack.pop();
				continue;
			}
			boolean added = false;
			if (c.resolvingLevel() < SootClass.SIGNATURES)
				Scene.v().forceResolve(c.getName(), SootClass.SIGNATURES);
			List<SootClass> impl = new ArrayList<>();
			if (c.isInterface()) {
				for (SootClass i : ah.getDirectSubinterfacesOf(c)) {
					if (!res.contains(i) && !processed.contains(i)) {
						stack.push(i);
						added = true;
					}
				}
				if (added)
					continue;
				impl = ah.getImplementersOf(c);
			} else {
				impl = ah.getDirectSubclassesOf(c);
			}
			for (SootClass o : impl) {
				if (!o.isLibraryClass())
					continue;
				if (!res.contains(o) && !processed.contains(o)) {
					stack.push(o);
					added = true;
				}
			}
			if (added)
				continue;
			stack.remove(c);
			if (use(c))
				res.add(c);
			else
				processed.add(c);
		}

		if (CHECK_TOPOLOGICAL_SORTING) {
			List<SootClass> test = new ArrayList<>(res);
			for (int b = 0; b < res.size(); b++) {
				for (int a = 0; a < b; a++) {
					SootClass ca = test.get(a);
					SootClass cb = test.get(b);
					if (Scene.v().getFastHierarchy().canStoreClass(cb, ca))
						throw new RuntimeException("Wrong order: " + ca + ", " + cb);
				}
			}
		}
		return res;
	}

	private boolean use(SootClass c) {
		boolean use = true;
		if (c.isInnerClass() || c.getName().contains("$"))
			use = false;
		switch (c.getName()) {
		case "java.io.Serializable":
		case "android.os.Parcelable":
		case "android.os.IBinder":
			// case "android.content.Context":
			use = false;
		}
		if (c.getName().startsWith("java.lang."))
			use = false;
		return use;

	}

	private <T> SootMethod getWrappedBaseClass(Set<IInstrumentationStep> instrumentation, SootClass i) {
		RefType t = RefType.v(i);
		String cl = WRAPPER_PCK + i;

		SootClass wrappedAbstractClass = Scene.v().getSootClassUnsafe(cl);
		if (wrappedAbstractClass != null) {
			return wrappedAbstractClass.getMethod("<init>", Collections.singletonList(t));
		}
		wrappedAbstractClass = new CISootClass(cl, Modifier.PUBLIC);
		if (i.isInterface()) {
			wrappedAbstractClass.setSuperclass(objectType.getSootClass());
			wrappedAbstractClass.addInterface(t.getSootClass());
		} else
			wrappedAbstractClass.setSuperclass(t.getSootClass());
		wrappedAbstractClass.addInterface(wrapperInterface);
		addClass(instrumentation, wrappedAbstractClass);
		SootField fieldWrapped = new CISootField("inner", t);
		wrappedAbstractClass.getOrAddField(fieldWrapped);

		CIJimple j = CIJimple.v();
		SootMethod wrap = new CISootMethod("<init>", Collections.singletonList(t), VoidType.v(), Modifier.PUBLIC);
		JimpleBody bdWrap = j.newBody(wrap);
		wrap.setActiveBody(bdWrap);
		wrappedAbstractClass.getOrAddMethod(wrap);
		bdWrap.insertIdentityStmts();
		bdWrap.getUnits().addLast(j.newInvokeStmt(j.newSpecialInvokeExpr(bdWrap.getThisLocal(),
				objectType.getSootClass().getMethod("<init>", Collections.emptyList()).makeRef())));
		bdWrap.getUnits().addLast(j.newAssignStmt(j.newInstanceFieldRef(bdWrap.getThisLocal(), fieldWrapped.makeRef()),
				bdWrap.getParameterLocal(0)));
		bdWrap.getUnits().addLast(j.newReturnVoidStmt());
		Deque<SootClass> worklist = new ArrayDeque<>();
		worklist.add(t.getSootClass());
		TCustomHashMap<SootMethod, Type> types = new TCustomHashMap<>(new HashingStrategy<SootMethod>() {

			private static final long serialVersionUID = 1L;

			@Override
			public int computeHashCode(SootMethod object) {
				return Objects.hash(object.getParameterTypes(), object.getName());
			}

			@Override
			public boolean equals(SootMethod o1, SootMethod o2) {
				return o1.getParameterTypes().equals(o2.getParameterTypes()) && o1.getName().equals(o2.getName());
			}
		});
		FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
		while (!worklist.isEmpty()) {
			SootClass l = worklist.pop();
			worklist.addAll(l.getInterfaces());
			if (l.hasSuperclass())
				worklist.add(l.getSuperclassUnsafe());

			for (SootMethod mm : l.getMethods()) {
				if (mm.isPrivate() || mm.isStatic())
					continue;
				if (mm.isFinal())
					continue;
				// The base class wraps an existing instance, so only one constructor is
				// supported.
				if (mm.isConstructor())
					continue;

				Type tt = types.get(mm);
				if (tt == null || fh.canStoreType(mm.getReturnType(), tt)) {
					// This return type is more specific, so take it
					types.put(mm, mm.getReturnType());
				}

			}
		}
		for (SootMethod mm : types.keySet()) {
			if (mm.isNative())
				continue;
			JimpleBody wrapped = addMethod(j, wrappedAbstractClass, mm, mm.getModifiers() & ~Modifier.ABSTRACT);
			Local wr = new CIJimpleLocal("wrapped", fieldWrapped.getType());
			wrapped.getLocals().add(wr);
			wrapped.getUnits().addLast(
					j.newAssignStmt(wr, j.newInstanceFieldRef(wrapped.getThisLocal(), fieldWrapped.makeRef())));
			InvokeExpr invForward;
			if (mm.getDeclaringClass().isInterface())
				invForward = j.newInterfaceInvokeExpr(wr, mm.makeRef(), wrapped.getParameterLocals());
			else
				invForward = j.newVirtualInvokeExpr(wr, mm.makeRef(), wrapped.getParameterLocals());
			if (mm.getReturnType() instanceof VoidType) {
				wrapped.getUnits().addLast(j.newInvokeStmt(invForward));
				wrapped.getUnits().addLast(j.newReturnVoidStmt());
			} else {
				Local ret = new CIJimpleLocal("ret", mm.getReturnType());
				wrapped.getLocals().add(ret);
				wrapped.getUnits().addLast(j.newAssignStmt(ret, invForward));
				wrapped.getUnits().addLast(j.newReturnStmt(ret));
			}
		}
		concreteWrappersBase.add(wrappedAbstractClass);
		return wrap;
	}

	static class SubSigWithoutReturnType {
		private final List<Type> paramTypes;
		private final String name;

		public SubSigWithoutReturnType(SootMethod m) {
			name = m.getName();
			paramTypes = m.getParameterTypes();
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, paramTypes);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SubSigWithoutReturnType other = (SubSigWithoutReturnType) obj;
			return Objects.equals(name, other.name) && Objects.equals(paramTypes, other.paramTypes);
		}

	}

	/**
	 * This method returns a wrapper class, for java.util.HashMap it returns a
	 * de.codeinspect.java.util.ArrayList which extends java.util.HashMap and has
	 * all the original constructors.
	 * 
	 * We can also safely wrap methods such as keySet(), valueSet() and entrySet().
	 * 
	 * @param instrumentation
	 * @param bt
	 * @return
	 */
	private SootClass getWrappedConcreteClass(Set<IInstrumentationStep> instrumentation, RefType bt) {
		SootClass baseClass = bt.getSootClass();
		String classI = WRAPPER_PCK + bt.getClassName();
		SootClass c = Scene.v().getSootClassUnsafe(classI);
		CIJimple j = CIJimple.v();
		if (c == null) {
			c = new CISootClass(classI, baseClass.getModifiers() | Modifier.PUBLIC);
			c.setSuperclass(baseClass);
			c.addInterface(wrapperInterface);
			SootClass current = baseClass;
			boolean firstIteration = true;
			Set<SubSigWithoutReturnType> finalMethods = new HashSet<>();
			while (current != null) {
				for (SootMethod m : current.getMethods()) {
					if (m.isStatic())
						continue;
					if (c.declaresMethod(m.getName(), m.getParameterTypes()))
						continue;
					if (m.isProtected() || m.isPublic()) {
						if (finalMethods.contains(new SubSigWithoutReturnType(m)))
							continue;
						if (m.isFinal()) {
							finalMethods.add(new SubSigWithoutReturnType(m));
							continue;
						}
						boolean ctor = m.isConstructor();
						if (ctor && !firstIteration)
							continue;
						Type retType = m.getReturnType();

						JimpleBody bd = addMethod(j, c, m, m.getModifiers() & ~Modifier.NATIVE);
						if (ctor) {
							bd.getUnits().addLast(j.newInvokeStmt(
									j.newSpecialInvokeExpr(bd.getThisLocal(), m.makeRef(), bd.getParameterLocals())));
							bd.getUnits().addLast(j.newReturnVoidStmt());
						} else {
							if (retType instanceof VoidType) {
								bd.getUnits().addLast(j.newInvokeStmt(j.newSpecialInvokeExpr(bd.getThisLocal(),
										m.makeRef(), bd.getParameterLocals())));
								bd.getUnits().addLast(j.newReturnVoidStmt());
							} else {

								if (retType instanceof PrimType || retType instanceof ArrayType) {
									// Primitives do not need to be converted
									Local ret = j.newLocal("Ret", retType);
									bd.getLocals().add(ret);
									bd.getUnits().addLast(j.newAssignStmt(ret, j.newSpecialInvokeExpr(bd.getThisLocal(),
											m.makeRef(), bd.getParameterLocals())));
									bd.getUnits().addLast(j.newReturnStmt(ret));

								} else {
									Local ret = j.newLocal("Ret", retType);
									Local obj = j.newLocal("Obj", objectType);
									bd.getLocals().add(ret);
									bd.getLocals().add(obj);
									bd.getUnits().addLast(j.newAssignStmt(ret, j.newSpecialInvokeExpr(bd.getThisLocal(),
											m.makeRef(), bd.getParameterLocals())));
									bd.getUnits()
											.addLast(j.newAssignStmt(obj,
													j.newStaticInvokeExpr(
															createConverterMethodFirstStep(instrumentation,
																	(RefType) retType).makeRef(),
															Collections.singletonList(ret))));
									bd.getUnits().addLast(j.newAssignStmt(obj, j.newCastExpr(ret, retType)));
									bd.getUnits().addLast(j.newReturnStmt(ret));
								}
							}
						}
					}
				}
				current = current.getSuperclassUnsafe();
				firstIteration = false;
			}
			concreteWrappers.add(c);
			addClass(instrumentation, c);
		}
		return c;
	}

	private void addClass(Set<IInstrumentationStep> instrumentation, SootClass c) {
		IInstrumentationStep.doImmediately(new AddApplicationClassInstrumentation(c));
	}

	/**
	 * Returns a converter method, which tries to wrap objects. Note that this can
	 * break semantics of the original code. Therefore, we have to be careful: e.g.
	 * when someone calls Collections.singleton(), we can safely wrap the result.
	 * 
	 * @param instrumentation
	 * @param bt
	 * @return
	 */
	private SootMethod createConverterMethodFirstStep(Set<IInstrumentationStep> instrumentation, RefType type) {
		String classI = WRAPPER_PCK + "Converter";
		SootClass c = converterClass;
		String mName = "convert" + type.getSootClass().getShortJavaStyleName().replace("$", "") + idConverter++;
		RefType ot = objectType;
		if (c == null) {
			c = new CISootClass(classI, Modifier.PUBLIC);
			converterClass = c;
			c.setSuperclass(ot.getSootClass());
			IInstrumentationStep.doImmediately(new AddApplicationClassInstrumentation(c));
		}
		SootMethod m = converterMethods.get(type);
		if (m == null) {
			m = new CISootMethod(mName, Collections.singletonList(ot), ot, Modifier.PUBLIC | Modifier.STATIC);
			JimpleBody bd = CIJimple.v().newBody(m);
			m.setActiveBody(bd);
			c.getOrAddMethod(m);
			bd.insertIdentityStmts();
			converterMethods.put(type, m);
		}
		return m;
	}

	private void createConverterMethodSecondStep(Set<IInstrumentationStep> instrumentation) {
		SootClass c = converterClass;
		if (c == null)
			return;
		FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();

		Set<SootMethod> emptyMethods = new HashSet<>();
		for (Entry<RefType, SootMethod> entry : converterMethods.entrySet()) {
			boolean hasAny = false;
			for (SootClass i : concreteWrappers) {
				if (fh.canStoreClass(i.getSuperclass(), entry.getKey().getSootClass())) {
					hasAny = true;
					break;
				}
			}
			for (SootClass i : concreteWrappersBase) {
				if (fh.canStoreClass(i, entry.getKey().getSootClass())) {
					hasAny = true;
					break;
				}
			}

			if (hasAny)
				create(instrumentation, fh, entry);
			else {
				emptyMethods.add(entry.getValue());
			}
		}
		if (!emptyMethods.isEmpty()) {
			for (SootClass i : Scene.v().getApplicationClasses()) {
				for (SootMethod m : i.getMethods()) {
					if (m.hasActiveBody()) {
						Body b = m.getActiveBody();
						for (Unit u : b.getUnits()) {
							if (u instanceof AssignStmt) {
								AssignStmt assign = (AssignStmt) u;
								if (assign.getRightOp() instanceof StaticInvokeExpr) {
									StaticInvokeExpr e = (StaticInvokeExpr) assign.getRightOp();
									if (emptyMethods.contains(e.getMethod())) {
										assign.setRightOp(e.getArg(0));
									}
								}
							}
						}
					}
				}
			}
			for (SootMethod m : emptyMethods) {
				m.getDeclaringClass().removeMethod(m);
			}
		}
	}

	private void create(Set<IInstrumentationStep> instrumentation, FastHierarchy fh, Entry<RefType, SootMethod> entry) {
		RefType baseType = entry.getKey();
		SootMethod m = entry.getValue();
		CIJimpleBody bd = (CIJimpleBody) m.getActiveBody();
		UnitPatchingChain u = bd.getUnits();
		bd.getUnits().clear();
		bd.getLocals().clear();
		bd.insertIdentityStmts();
		CIJimple j = CIJimple.v();
		Local bool = j.newLocal("bool", BooleanType.v());
		Local obj = j.newLocal("obj", objectType);
		bd.getLocals().add(bool);
		bd.getLocals().add(obj);
		Local param = bd.getParameterLocal(0);
		CIReturnStmt retIdentity = j.newReturnStmt(param);

		final Scene scene = Scene.v();
		SootClass scRuntimeUtils = scene
				.getSootClassUnsafe("reproduction.dynamiccallbackidentification.logic.DCIDRuntimeUtils");
		SootMethodRef runtimeMethodRef = scRuntimeUtils.getMethodByName("isEquivalent").makeRef();

		// Check whether we have already wrapped
		u.addLast(j.newAssignStmt(bool, j.newInstanceOfExpr(param, RefType.v(wrapperInterface))));
		u.addLast(j.newIfStmt(j.newEqExpr(bool, BooleanConstant.v(true)), retIdentity));

		for (SootClass i : concreteWrappers) {
			if (!fh.canStoreClass(i.getSuperclass(), baseType.getSootClass()))
				continue;
			SootMethodRef wrapRef = null;
			for (SootMethod mm : i.getMethods()) {
				if (mm.isConstructor() && mm.isPublic() && mm.getParameterCount() == 1) {
					Type tt = mm.getParameterType(0);
					if (!(tt instanceof RefType)) {
						continue;
					}
					RefType ttt = (RefType) tt;
					if (!fh.canStoreType(i.getSuperclass().getType(), ttt))
						continue;
					wrapRef = mm.makeRef();
					break;
				}
			}
			if (wrapRef == null) {
				logger.warn("Could not find a wrapper for " + i.getName());
				continue;
			}
			// u.addLast(j.newAssignStmt(bool, j.newInstanceOfExpr(param,
			// i.getSuperclass().getType())));

			RefType t = i.getSuperclass().getType();
			u.addLast(j.newAssignStmt(bool, j.newStaticInvokeExpr(runtimeMethodRef,
					Arrays.<Value>asList(param, ClassConstant.fromType(t), IntConstant.v(getID(t))))));
			final Stmt next = j.newNopStmt();
			Local paramCast = j.newLocal("pCast", wrapRef.getParameterType(0));
			bd.getLocals().add(paramCast);
			u.addLast(j.newIfStmt(j.newEqExpr(bool, BooleanConstant.v(false)), next));
			u.addLast(j.newAssignStmt(obj, j.newNewExpr(RefType.v(i.getName()))));
			u.addLast(j.newAssignStmt(paramCast, j.newCastExpr(param, wrapRef.getParameterType(0))));
			u.addLast(j.newInvokeStmt(j.newSpecialInvokeExpr(obj, wrapRef, Collections.singletonList(paramCast))));
			u.addLast(j.newReturnStmt(obj));
			u.addLast(next);
		}

		// This can be problematic in case there are implementations at runtime we do
		// not know about
		for (SootClass o : wrappedClassesBase) {
			RefType tt = RefType.v(o);
			if (!tt.getSootClass().isInterface()) {
				logger.warn("Unsupported: " + tt + ", needs to be an interface! Ignoring.");
				continue;
			}
			if (!fh.canStoreClass(tt.getSootClass(), baseType.getSootClass()))
				continue;
			// u.addLast(j.newAssignStmt(bool, j.newInstanceOfExpr(param, t)));
			u.addLast(j.newAssignStmt(bool, j.newStaticInvokeExpr(runtimeMethodRef,
					Arrays.<Value>asList(param, ClassConstant.fromType(tt), IntConstant.v(getID(tt))))));

			Stmt next = j.newNopStmt();
			u.addLast(j.newIfStmt(j.newEqExpr(bool, BooleanConstant.v(false)), next));
			SootMethod wrap = getWrappedBaseClass(instrumentation, o);
			u.addLast(j.newAssignStmt(obj, j.newNewExpr(RefType.v(wrap.getDeclaringClass().getName()))));

			u.addLast(j.newInvokeStmt(j.newSpecialInvokeExpr(obj, wrap.makeRef(), Collections.singletonList(param))));
			u.addLast(j.newReturnStmt(obj));
			u.addLast(next);

		}

		u.addLast(retIdentity);
	}

	private int getID(RefType tt) {
		Integer o = idxClass.get(tt.getSootClass());
		if (o == null) {
			id++;
			idxClass.put(tt.getSootClass(), id);
			return id;
		}
		return o;
	}

	private JimpleBody addMethod(CIJimple j, SootClass clazz, SootMethod m, int mod) {
		try {
			SootMethod newM = new CISootMethod(m.getName(), m.getParameterTypes(), m.getReturnType(), mod);
			JimpleBody bd = j.newBody(newM);
			newM.setActiveBody(bd);
			clazz.getOrAddMethod(newM);
			bd.insertIdentityStmts();
			return bd;
		} catch (Exception e) {
			throw new RuntimeException(m.getSignature() + ": " + m.getModifiers() + ", isPhantom: " + m.isPhantom()
					+ ", new modifiers: " + mod, e);
		}
	}

	public boolean isPotentialCallback(Value val, Type declaredType, SootMethod callerMethod, SootMethod declaredCallee,
			Set<IInstrumentationStep> instrumentation, Set<SootMethod> instrumentedCallbackMethods,
			Set<LoggingPoint> loggingPoints, AndroidEntryPointUtils entryPointUtils) {
		// Filter out calls to lifecycle methods
		if (entryPointUtils.isEntryPointMethod(declaredCallee))
			return false;

		// Obfuscated methods are useless
		if (declaredCallee.getName().length() < 3)
			return false;

		// Blacklist some Android methods that have nothing to do with our analysis
		if (BLACKLISTED_CALLBACKS.contains(declaredCallee.getSubSignature()))
			return false;

		// Get the type of the local that is a potential callback object
		Type valType = val.getType();
		if (!(valType instanceof RefType))
			return false;

		if (val instanceof Local) {
			Local local = (Local) val;
			if (declaredType.equals(objectType))
				return false;
			Set<Type> possibleTypes = Scene.v().getPointsToAnalysis().reachingObjects(local).possibleTypes();

			// If we don't have type information, we use the declared type of the variable
			if (possibleTypes.isEmpty()) {
				RefType actualType = (RefType) valType;
				return processPotentialCallback(actualType, declaredType, callerMethod, declaredCallee, instrumentation,
						instrumentedCallbackMethods, loggingPoints, entryPointUtils);
			}

			// Look at all the possible types
			boolean retVal = false;
			for (Type tp : possibleTypes) {
				if (tp instanceof AnySubType) {
					AnySubType anySubType = (AnySubType) tp;
					Set<SootClass> subclasses = TypeUtils.getTypeHierarchyFrom(anySubType.getBase().getSootClass());
					for (SootClass subclass : subclasses) {
						retVal |= processPotentialCallback(subclass.getType(), declaredType, callerMethod,
								declaredCallee, instrumentation, instrumentedCallbackMethods, loggingPoints,
								entryPointUtils);
					}
				} else if (tp instanceof RefType) {
					retVal |= processPotentialCallback((RefType) tp, declaredType, callerMethod, declaredCallee,
							instrumentation, instrumentedCallbackMethods, loggingPoints, entryPointUtils);
				}
			}
			return retVal;
		}

		return false;
	}

	private boolean processPotentialCallback(RefType actualType, Type declaredType, SootMethod callerMethod,
			SootMethod declaredCallee, Set<IInstrumentationStep> instrumentation,
			Set<SootMethod> instrumentedCallbackMethods, Set<LoggingPoint> loggingPoints,
			AndroidEntryPointUtils entryPointUtils) {
		SootClass argumentClass = actualType.getSootClass();

		String n = argumentClass.getName();

		// Only instrument custom classes
		// the passed class must be a *subclass* of the parameter type
		if (actualType.equals(declaredType))
			return false;

		// ignore java.lang.Object
		if (declaredType.equals(objectType))
			return false;

		if (declaredType instanceof RefType) {
			RefType t = (RefType) declaredType;
			SootClass scDeclared = t.getSootClass();
			if (scDeclared != null && !scDeclared.isPhantom()) {
				if (scDeclared.isAbstract() || scDeclared.isInterface())
					;
				else
					// When the declared type is not abstract or an interface, this is probably
					// not meant to be a callback.
					return false;

				// Do not allow stuff such as android.content.Context as callbacks
				if (isClassBlacklisted(scDeclared))
					return false;
			}
		}

		// we are looking for custom subclasses
		if (isLibraryClass(argumentClass, true) && !concreteWrappers.contains(argumentClass)
				&& !concreteWrappersBase.contains(argumentClass) && !argumentClass.getName().startsWith(WRAPPER_PCK))
			return false;
		if (isSystem(n))
			return false;

		// inner and anonymous classes are instantiated with a reference to the parent
		// class
		/*
		 * if (!declaredCallee.getDeclaringClass().getName().startsWith(WRAPPER_PCK) &&
		 * declaredCallee.isConstructor() &&
		 * argumentClass.equals(callerMethod.getDeclaringClass())) { if
		 * (declaredCallee.getDeclaringClass().getName().contains("$") ||
		 * declaredCallee.getDeclaringClass().isInnerClass()) return false; }
		 */

		boolean classHasBeenInstrumented = instrumentedClasses.containsKey(argumentClass);
		boolean hasInstrumentedMethod = false;
		final Hierarchy h = Scene.v().getActiveHierarchy();
		for (SootMethod callbackMethod : argumentClass.getMethods()) {
			if (callbackMethod.isConstructor())
				continue;
			if (BLACKLISTED_CALLBACKS.contains(callbackMethod.getSubSignature()))
				continue;

			SootClass declaringSuperClass = Util.getDefiningClass(callbackMethod, h);
			if (declaringSuperClass == argumentClass)
				continue;

			// the argument class overrides a library method. instrument class and call
			// site once
			if (!classHasBeenInstrumented) {
				generateClassInstrumentation(argumentClass, instrumentation);
				classHasBeenInstrumented = true;
				logger.info("Instrumenting suspected callback class " + argumentClass.getName() + " "
						+ declaringSuperClass.getName());
				logger.info("Passed to " + callbackMethod);
			}

			// make sure to only instrument a method once
			if (!instrumentedCallbackMethods.contains(callbackMethod)) {
				// then instrument all method overrides
				DCIDCallbackCalledLoggingPoint lp = instrumentMethod(argumentClass, callbackMethod, entryPointUtils);
				if (lp != null) {
					loggingPoints.add(lp);
					lp.overriddenMethod = declaringSuperClass.getMethod(callbackMethod.getNumberedSubSignature());
				}
				instrumentedCallbackMethods.add(callbackMethod);
			}
			hasInstrumentedMethod = true;
		}
		return hasInstrumentedMethod;
	}

	private static boolean isSystem(String n) {
		return n.startsWith("javax.") || n.startsWith("java.") || n.startsWith("android.") || n.startsWith("androidx.");
	}

	private void generateCallsiteInstrumentation(Body body, Unit callsite, InvokeExpr invokeExpr, int callsiteId,
			List<CallbackCandidate> callbackCandidates, Set<IInstrumentationStep> instrumentation) {
		// Get the references to the runtime component
		final Scene scene = Scene.v();
		SootClass scRuntimeUtils = scene
				.getSootClassUnsafe("reproduction.dynamiccallbackidentification.logic.DCIDRuntimeUtils");
		CISootMethodRef runtimeMethodRef = new CISootMethodRef(scRuntimeUtils, "handleCallSite",
				new CIArrayList<Type>(scene.getObjectType(), IntType.v(), IntType.v()), VoidType.v(), true);

		// 2wo units per argument, one if and one nop
		Unit[] units = new Unit[callbackCandidates.size() * 4];
		int i = 0;
		final CIJimple jimple = CIJimple.v();
		SootMethod m = Scene.v()
				.getMethod("<" + DCIDCallbackCalledExtractionHandler.class.getName() + ": void <clinit>()>");
		SootMethod mRegister = Scene.v().getMethod("<" + DCIDCallbackCalledExtractionHandler.class.getName()
				+ ": void addClass(int,int,java.lang.Object)>");
		UnitPatchingChain unitsInitMap = m.retrieveActiveBody().getUnits();

		for (CallbackCandidate candidate : callbackCandidates) {
			Value arg = candidate.getLocal();
			Type type = arg.getType();
			RefType rt = (RefType) type;

			Constant c;
			String n = rt.getSootClass().getName();
			if (isSystem(n)) {
				c = ClassConstant.fromType(rt);
			} else
				c = StringConstant.v(n);
			unitsInitMap
					.insertBefore(
							jimple.newInvokeStmt(
									jimple.newStaticInvokeExpr(mRegister.makeRef(),
											(List<? extends Value>) Arrays.asList(IntConstant.v(callsiteId),
													IntConstant.v(candidate.getParamIdx()), c))),
							unitsInitMap.getLast());

			// Skip the assignment if the value is null
			Value condition = new JEqExpr(arg, NullConstant.v());
			CINopStmt nop = jimple.newNopStmt();
			CIIfStmt ifStmt = jimple.newIfStmt(condition, nop);

			// If the declared class was modified, we can directly assign the callsite ID
			// and index. Otherwise, we need to call the runtime component, which uses
			// reflection.
			/*
			 * if (instrumentedClasses.containsKey(classRef)) { ClassInstrumentationInfo
			 * info = instrumentedClasses.get(classRef); Unit id =
			 * jimple.newAssignStmt(jimple.newInstanceFieldRef(arg, info.callsiteIdField),
			 * IntConstant.v(callsiteId)); Unit idx =
			 * jimple.newAssignStmt(jimple.newInstanceFieldRef(arg,
			 * info.callsiteArgIndexField), IntConstant.v(candidate.getParamIdx())); //TODO_
			 * AtomicInteger units[i * 4 + 0] = ifStmt; units[i * 4 + 1] = id; units[i * 4 +
			 * 2] = idx; units[i * 4 + 3] = nop; } else {
			 */
			units[i * 4 + 0] = ifStmt;
			units[i * 4 + 1] = jimple.newInvokeStmt(jimple.newStaticInvokeExpr(runtimeMethodRef, arg,
					IntConstant.v(callsiteId), IntConstant.v(candidate.getParamIdx())));
			units[i * 4 + 2] = jimple.newNopStmt();
			units[i * 4 + 3] = nop;
			// }
			i++;
		}

		instrumentation.add(new InsertBeforeInstrumentation(body, units, callsite));

	}

	private void generateClassInstrumentation(SootClass clazz, Set<IInstrumentationStep> steps) {
		int counter = 0;
		String fieldNameId = FIELD_PREFIX_CALLSITE_ID;
		String fieldNameArgIndex = FIELD_PREFIX_CALLSITE_ARG_INDEX;
		String fieldThread = FIELD_PREFIX_CALLSITE_THREAD;
		while (clazz.declaresFieldByName(fieldNameId) || clazz.declaresFieldByName(fieldNameArgIndex)) {
			fieldNameId = FIELD_PREFIX_CALLSITE_ID + "_" + counter;
			fieldNameArgIndex = FIELD_PREFIX_CALLSITE_ARG_INDEX + "_" + counter;
			fieldThread = FIELD_PREFIX_CALLSITE_THREAD + "_" + counter;
			counter++;
		}

		CISootField callsiteIdField = new CISootField(fieldNameId, soot.IntType.v(), Modifier.PUBLIC);
		CISootFieldRef callsiteIdFieldRef = new CISootFieldRef(clazz, fieldNameId, IntType.v(), false);
		CISootField callsiteArgIndexField = new CISootField(fieldNameArgIndex, soot.IntType.v(), Modifier.PUBLIC);
		CISootFieldRef callsiteArgIndexFieldRef = new CISootFieldRef(clazz, fieldNameArgIndex, IntType.v(), false);
		CISootField callsiteThreadField = new CISootField(fieldThread,
				RefType.v("java.util.concurrent.atomic.AtomicLong"), Modifier.PUBLIC);
		CISootFieldRef callsiteThreadFieldRef = new CISootFieldRef(clazz, fieldThread,
				RefType.v("java.util.concurrent.atomic.AtomicLong"), false);
		steps.add(new AddFieldInstrumentation(clazz, callsiteIdField));
		steps.add(new AddFieldInstrumentation(clazz, callsiteArgIndexField));
		steps.add(new AddFieldInstrumentation(clazz, callsiteThreadField));
		for (SootMethod i : clazz.getMethods()) {
			if (i.isConstructor() && i.isConcrete()) {
				Body body = i.retrieveActiveBody();
				Unit superCtorCall = null;
				for (Unit u : body.getUnits()) {
					if (u instanceof GotoStmt)
						u = ((GotoStmt) u).getTarget();
					if (u instanceof IdentityStmt)
						continue;
					Stmt s = (Stmt) u;
					if (s.containsInvokeExpr() && s.getInvokeExpr().getMethodRef().getName().equals("<init>")) {
						superCtorCall = s;
						break;
					}
				}
				CIJimple j = CIJimple.v();
				Local lcl = j.newLocal("atomicThread", callsiteThreadField.getType());
				steps.add(new AddLocalInstrumentation(body, lcl));
				steps.add(new InsertAfterInstrumentation(body, Arrays.asList(
						j.newAssignStmt(lcl, j.newNewExpr(RefType.v("java.util.concurrent.atomic.AtomicLong"))),
						j.newInvokeStmt(j.newSpecialInvokeExpr(lcl,
								RefType.v("java.util.concurrent.atomic.AtomicLong").getSootClass()
										.getMethod("void <init>(long)").makeRef(),
								Arrays.<Value>asList(LongConstant.v(-1)))),
						j.newAssignStmt(j.newInstanceFieldRef(body.getThisLocal(), callsiteThreadFieldRef), lcl)),
						superCtorCall));
			}

		}
		instrumentedClasses.put(clazz,
				new ClassInstrumentationInfo(callsiteIdFieldRef, callsiteArgIndexFieldRef, callsiteThreadFieldRef));
	}

	private DCIDCallbackCalledLoggingPoint instrumentMethod(SootClass classToBeInstrumented, SootMethod sm,
			AndroidEntryPointUtils entryPointUtils) {
		if (!sm.hasActiveBody() || sm.isStatic() || entryPointUtils.isEntryPointMethod(sm)
				|| isLibraryClass(sm.getDeclaringClass(), false) || sm.isConstructor())
			return null;

		// Obfuscated methods do not provide useful insights into library callbacks
		if (sm.getName().length() < 3)
			return null;

		ClassInstrumentationInfo info = instrumentedClasses.get(classToBeInstrumented);
		if (info == null)
			return null;

		JimpleBody body = (JimpleBody) sm.getActiveBody();
		LazySootFieldValueRequest callsiteIdRequest = new LazySootFieldValueRequest(body.getThisLocal(),
				info.callsiteIdField);
		LazySootFieldValueRequest callsiteArgIndexRequest = new LazySootFieldValueRequest(body.getThisLocal(),
				info.callsiteArgIndexField);

		List<IValueRequest> valueRequests = new ArrayList<>(3);
		valueRequests.add(callsiteIdRequest);
		valueRequests.add(callsiteArgIndexRequest);

		// get this value
		for (Unit u : body.getUnits()) {
			if (u instanceof CIIdentityStmt) {
				CIIdentityStmt ass = (CIIdentityStmt) u;
				if (ass.getRightOp() instanceof ThisRef) {

					ThisRef tr = (ThisRef) ass.getRightOp();
					if (tr.getType() == classToBeInstrumented.getType()) {
						valueRequests.add(new SootValueRequest(ass.getLeftOp()));
						break;
					}
				}
			}
		}

		return new DCIDCallbackCalledLoggingPoint(((JimpleBody) sm.getActiveBody()).getFirstNonIdentityStmt(),
				valueRequests, true);
	}

	public static boolean isLibraryClass(SootClass cs) {
		return isLibraryClass(cs, true);
	}

	public static boolean isLibraryClass(SootClass cs, boolean includeWrapped) {
		String pck = cs.getPackageName();
		if (pck != null && (pck.startsWith(("org.asynchttpclient")) || isSystem(pck)
				|| (includeWrapped ? pck.startsWith("reproduction.wrapped.") : false) || pck.contains(".fakelibrary")))
			return true;
		return DependencyFinderUtils.isLibraryClass(cs);
	}

	@Override
	public boolean doesOnlyInsertNewStatements() {
		return true;
	}

}
