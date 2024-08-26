package reproduction.dynamiccallbackidentification.eclipseinterfaces;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.codeinspect.collections.CIArrayList;
import de.codeinspect.collections.CIHashSet;
import de.codeinspect.collections.CIList;
import de.codeinspect.collections.CISet;
import de.codeinspect.dynamicvalues.base.ValueTraceEvent;
import de.codeinspect.dynamicvalues.base.extractionHandlers.IExtractionHandlerData;
import de.codeinspect.dynamicvalues.extraction.LoggingPointTraceEventAssoc;
import de.codeinspect.dynamicvalues.extraction.Run;
import de.codeinspect.dynamicvalues.extraction.RuntimeValueNotifier;
import de.codeinspect.instrumentation.IInstrumentationStep;
import de.codeinspect.platforms.soot.values.LoggingPoint;
import de.codeinspect.soot.CIScene;
import de.codeinspect.soot.SootInstanceBase;
import de.codeinspect.soot.SootInstanceBase.SootOperation;
import de.codeinspect.soot.callgraph.dynamic.DynamicCallgraph;
import de.codeinspect.soot.callgraph.dynamic.Trace;
import de.codeinspect.soot.taintTracking.dynamic.DynamicTaintEvent;
import de.codeinspect.soot.taintTracking.dynamic.DynamicTaintLocation;
import de.codeinspect.soot.taintTracking.dynamic.DynamicTaintManager;
import de.codeinspect.soot.taintTracking.dynamic.TaintPath;
import de.codeinspect.soot.taintTracking.dynamic.TaintPredicate;
import de.codeinspect.soot.taintTracking.dynamic.TaintSource;
import de.codeinspect.soot.utils.AnalysisUtils;
import reproduction.dynamiccallbackidentification.logic.DCIDCallbackExtractionData;
import soot.Kind;
import soot.MethodSubSignature;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.entryPointCreators.AndroidEntryPointConstants;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.DirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.IndirectTarget;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.InstanceinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.StaticinvokeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdge;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeSource;
import soot.jimple.toolkits.callgraph.VirtualEdgesSummaries.VirtualEdgeTarget;

public class DCIDListener extends RuntimeValueNotifier {

	private static final Logger logger = LogManager.getLogger(DCIDListener.class);

	public static final int CALLBACK_CHAIN_CUTOFF = 20;

	protected static final int CUTOFF = 100;

	private SootInstanceBase si;
	private Map<Integer, Unit> callsites;

	public DCIDListener(Set<LoggingPoint> lps, Set<IInstrumentationStep> steps, List<Unit> instrumentations,
			SootInstanceBase si, Map<Integer, Unit> callsites) {
		super(lps, Collections.emptySet(), steps);
		this.si = si;
		this.callsites = callsites;
	}

	@Override
	public void notify(Run run, List<LoggingPointTraceEventAssoc> lpvte) {
		for (LoggingPointTraceEventAssoc event : lpvte) {
			if (event.getTraceEvent() instanceof ValueTraceEvent) {
				for (IExtractionHandlerData data : ((ValueTraceEvent) event.getTraceEvent())
						.getExtractionHandlerData()) {
					if (data instanceof DCIDCallbackExtractionData) {
						DCIDCallbackExtractionData ed = (DCIDCallbackExtractionData) data;
						handleCallbackExtractionData(event, ed);
					}
				}
			}
		}
	}

	private static class Cached {
		LoggingPoint event;
		DCIDCallbackExtractionData ed;

		public Cached(LoggingPointTraceEventAssoc event, DCIDCallbackExtractionData ed) {
			this.event = event.getLoggingPoint();
			this.ed = ed;
		}

		@Override
		public int hashCode() {
			return Objects.hash(ed, event.getStmt());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Cached other = (Cached) obj;
			return Objects.equals(ed, other.ed) && Objects.equals(event, other.event);
		}

	}

	Set<Cached> cached = new HashSet<>();

	protected void handleCallbackExtractionData(LoggingPointTraceEventAssoc event, DCIDCallbackExtractionData ed) {
		if (!cached.add(new Cached(event, ed)))
			return;

		Unit cs = callsites.get(ed.getCallsiteId());
		logger.info(String.format("Callback with index %d called at callsite %d %s", ed.getCallbackIndex(),
				ed.getCallsiteId(), cs));

		// Identify the source of this callback invocation
		try {
			si.doSootOperation(new SootOperation() {

				@Override
				public void doSootOperation() throws Exception {
					final CIScene scene = CIScene.v();
					Stmt srcStmt = (Stmt) callsites.get(ed.getCallsiteId());
					if (srcStmt == null)
						logger.error("Unknown callsite ID {}", ed.getCallsiteId());
					else {
						if (!srcStmt.containsInvokeExpr())
							logger.error("Source statement is not an invocation");
						else {
							InvokeExpr iexpr = srcStmt.getInvokeExpr();

							// Get the correct edge source type
							VirtualEdgeSource source = null;
							// VirtualEdge veryShortEdge = null;
							SootMethod targetMethod = iexpr.getMethod();
							if (iexpr instanceof StaticInvokeExpr)
								source = new StaticinvokeSource(targetMethod.getSignature());
							else if (iexpr instanceof InstanceInvokeExpr)
								source = new InstanceinvokeSource(targetMethod.getDeclaringClass().getType(),
										targetMethod.getSubSignature());

							// Get the edge target
							VirtualEdgeTarget target = null;
							SootMethod callbackMethod = event.getLoggingPoint().getSootMethod();
							int argIdx = ed.getCallbackIndex();
							if (argIdx < 0)
								target = new DirectTarget(
										getTargetType(callbackMethod.getDeclaringClass().getType(), callbackMethod),
										new MethodSubSignature(callbackMethod.getNumberedSubSignature()));
							else
								target = new DirectTarget(
										getTargetType(callbackMethod.getDeclaringClass().getType(), callbackMethod),
										new MethodSubSignature(callbackMethod.getNumberedSubSignature()), argIdx);

							if (source == null)
								logger.error("Invalid source expression type {}", iexpr.getClass().getName());
							else {
								// Get the edge statements that have transitively invoked the callback method
								Set<Stmt> edgeCallSites = getLastUserCodeCallSite(event.getLoggingPoint().getStmt(),
										true);
								if (edgeCallSites == null || edgeCallSites.isEmpty())
									edgeCallSites = Collections.singleton(null);

								for (Stmt edgeCallSite : edgeCallSites) {
									VirtualEdge edge = null;
									DynamicTaintManager taintManager = scene.getDynamicTaintManager();

									// Get the last statement in the library that called our callback and find the
									// taint path between the construction of the callback and the position where it
									// is called.
									boolean isFakeDispatch = false;
									Stmt dispatchStmt = getLastLibraryCodeCallSite(event.getLoggingPoint().getStmt());
									if (dispatchStmt == null) {
										// If we don't have a path from the callgraph, but the taint path is unique, we
										// take that one instead
										CISet<TaintPath> pathsFromSource = taintManager.getPathsForSource(srcStmt);
										if (pathsFromSource.size() == 1) {
											dispatchStmt = pathsFromSource.getElement().getStmtsOnPath().getLast();
											isFakeDispatch = true;
										}
									} else {
										if (AnalysisUtils.getMethod(dispatchStmt).getSubSignature()
												.equals("void finalize()"))
											// filter out
											return;
									}

									// If the dispatch statement cannot possibly call the supposed callback method,
									// something is wrong
									if (dispatchStmt == null || isFakeDispatch
											|| isValidDispatch(callbackMethod, dispatchStmt)) {
										// For each statement, check whether we have a taint transfer
										CIList<Stmt> userCodeCallSites = new CIArrayList<>();
										userCodeCallSites.add(srcStmt);
										Set<TaintPath> unfilteredsources = taintManager.getPathsBetween(srcStmt,
												dispatchStmt);
										Set<TaintPath> sources = taintManager.getPathsBetween(srcStmt, dispatchStmt,
												new TaintPredicate<DynamicTaintEvent>() {
													Set<Integer> seenIDs = new HashSet<>();

													public boolean acceptsNewTaintSource(TaintSource ts) {
														seenIDs = new HashSet<>();
														return true;
													}

													@Override
													public boolean test(DynamicTaintEvent t) {
														if (!t.isTransfer())
															return false;
														// we are only interested in new taints
														boolean use = seenIDs.add(t.getTaintedId());
														return use;
													}
												});
										for (TaintPath tp : sources) {
											List<DynamicTaintLocation> l = tp.getPath();
											if (l.size() > CUTOFF)
												continue;
											for (int i = l.size() - 1; i >= 0; i--) {
												DynamicTaintLocation loc = l.get(i);
												// Get the API call from the user code that triggered this taint
												// transfer
												CISet<Stmt> locCallSites = getLastUserCodeCallSite(loc.getStmt(),
														false);
												Stmt userCodeCallSite = locCallSites.getElement();
												if (AnalysisUtils.getMethod(loc.getStmt()).getSubSignature()
														.equals("void finalize()"))
													// filter out
													return;

												if (loc.getStmt().containsInvokeExpr()) {
													InvokeExpr locExpr = loc.getStmt().getInvokeExpr();
													if (locExpr.getMethod().getDeclaringClass().getName()
															.equals("java.lang.StringBuilder"))
														return;
												}

												// If there was no call in user code, the current statement may have
												// transferred taint via a StubDroid summary
												if (userCodeCallSite == null) {
													if (loc.getStmt().containsInvokeExpr()) {
														InvokeExpr locExpr = loc.getStmt().getInvokeExpr();
														if (!locExpr.getMethod().hasActiveBody()) {
															userCodeCallSite = loc.getStmt();
														}
													}
												}

												if (userCodeCallSite != null
														&& !userCodeCallSites.contains(userCodeCallSite))
													// we want the edge to only consist of calls from the app to a
													// library.
													if (!DCIDFactory.isLibraryClass(AnalysisUtils
															.getMethod(userCodeCallSite).getDeclaringClass())) {

														userCodeCallSites.add(1, userCodeCallSite);
													}
											}
										}

										edge = createEdge(srcStmt, target, edgeCallSite, userCodeCallSites,
												unfilteredsources);
									}

									// Create the edge
									if (isValidEdge(edge)) {
										String[] stackTrace = ed.getStacktrace();
										if (stackTrace == null || stackTrace.length == 0 || check(stackTrace, edge)) {
											DebugDynamicCallback.v().candidateTriggered(srcStmt, source, argIdx, edge);
											scene.getVirtualEdgeManager().addEdge(edge);
											logger.info("We have a callback edge: {}", edge);
										}
									}
								}
							}
						}
					}

				}

				private VirtualEdge createEdge(Stmt srcStmt, VirtualEdgeTarget target, Stmt edgeCallSite,
						CIList<Stmt> userCodeCallSites, Set<TaintPath> sources) {
					// We have an indirect edge
					VirtualEdgeTarget indirectTarget = target;
					for (int i = 0; i < userCodeCallSites.size() - 1; i++) {
						Stmt callSite = userCodeCallSites.get(i);
						Stmt nextCallSite = userCodeCallSites.get(i + 1);

						DynamicTaintLocation taintedObj = findTaintedObject(sources, nextCallSite);
						IndirectTarget nextTarget;
						SootMethod mr = callSite.getInvokeExpr().getMethod();
						SootClass type = callSite.getInvokeExpr().getMethodRef().declaringClass();
						if (taintedObj == null) {
							// So it can happen that we find the last user call, which seems necessary
							// because
							// in its call tree there is at least one taint transfer happening.
							// However, it can only be on the base object (or not have any object at all in
							// case
							// we have a static method call, which uses a list to call all callback
							// implementations)
							if (callSite.getInvokeExpr() instanceof StaticInvokeExpr)
								nextTarget = new IndirectTarget(getTargetType(type.getType(), mr),
										new MethodSubSignature(callSite), -2);
							else
								nextTarget = new IndirectTarget(getTargetType(type.getType(), mr),
										new MethodSubSignature(callSite), -1);
						} else if (taintedObj.isBase())
							nextTarget = new IndirectTarget(getTargetType(type.getType(), mr),
									new MethodSubSignature(callSite));
						else
							nextTarget = new IndirectTarget(getTargetType(type.getType(), mr),
									new MethodSubSignature(callSite), taintedObj.getArgIdx());
						nextTarget.addTarget(indirectTarget);
						indirectTarget = nextTarget;

						if (i > CALLBACK_CHAIN_CUTOFF) {
							logger.warn("Exceeded callback chain cutoff: " + srcStmt);
							return null;
						}
					}
					if (edgeCallSite != null && userCodeCallSites.size() == 1) {
						// use base
						Stmt cs = userCodeCallSites.get(0);
						IndirectTarget nextTarget = new IndirectTarget(
								getTargetType(cs.getInvokeExpr().getMethodRef().getDeclaringClass().getType(),
										cs.getInvokeExpr().getMethod()),
								new MethodSubSignature(cs));
						nextTarget.addTarget(indirectTarget);
						indirectTarget = nextTarget;
					}

					VirtualEdgeSource lastSource = new InstanceinvokeSource(
							edgeCallSite == null ? userCodeCallSites.getLast() : edgeCallSite);

					return new VirtualEdge(Kind.GENERIC_FAKE, lastSource, indirectTarget);
				}

				private boolean check(String[] stackTrace, VirtualEdge edge) {
					for (int i = 0; i < stackTrace.length; i++) {
						String s = stackTrace[i];
						if (s.startsWith("de.codeinspect.dynamicvalues.android.TraceEventHandler.doReport")) {
							s = stackTrace[i + 1];
							s = s.substring(0, s.lastIndexOf("("));
							if (s.startsWith("reproduction.wrapped."))
								s = s.substring("reproduction.wrapped.".length());
							String className = s.substring(0, s.lastIndexOf("."));
							String methodName = s.substring(s.lastIndexOf(".") + 1);
							Collection<VirtualEdgeTarget> e = edge.getTargets();
							while (true) {
								if (e.size() != 1)
									throw new RuntimeException("Illegal assertion: " + e + ", " + e.size());
								VirtualEdgeTarget vt = e.iterator().next();
								if (vt instanceof IndirectTarget) {
									e = ((IndirectTarget) vt).getTargets();
								} else
									break;
							}
							DirectTarget tgt = (DirectTarget) e.iterator().next();
							if (!tgt.getTargetMethod().getMethodName().equals(methodName)) {
								logger.warn(
										"Illegal edge. Expected: " + className + ": " + methodName + "; Edge: " + edge);
								return false;
							}
							return true;
						}
					}
					logger.warn("No doReport method found in " + Arrays.toString(stackTrace));
					return false;
				}

				/**
				 * Checks whether the given relationship between callback method and dispatch
				 * statement is valid
				 * 
				 * @param callbackMethod The callback method
				 * @param dispatchStmt   The dispatch statement that supposedly calls the
				 *                       callback
				 * @return True if the given dispatch statement can invoke the given callback
				 *         method
				 */
				protected boolean isValidDispatch(SootMethod callbackMethod, Stmt dispatchStmt) {
					final CIScene scene = CIScene.v();

					// The dispatch statement must call the callback
					if (scene.getDynamicCallgraph().findEdge(dispatchStmt, callbackMethod) != null)
						return true;

					return false;
				}

			});
		} catch (Exception e) {
			logger.error("Could not post-process callback data", e);
		}
	}

	public static RefType getTargetType(RefType type, SootMethod method) {
		// so... we are looking for a superinterface or -class (interface is preferred)
		// where the method is defined and which is in a library
		Queue<SootClass> workList = new ArrayDeque<>();
		Set<SootClass> candidates = new LinkedHashSet<>();
		workList.add(type.getSootClass());
		while (true) {
			SootClass sc = workList.poll();
			if (sc == null)
				break;

			if (sc.declaresMethod(method.getNumberedSubSignature())) {
				if (DCIDFactory.isLibraryClass(sc)) {
					candidates.add(sc);
				}
			}

			workList.addAll(sc.getInterfaces());
			if (sc.hasSuperclass())
				workList.add(sc.getSuperclass());

		}
		RefType take = null;

		for (SootClass ss : candidates) {
			if (ss.isInterface()) {
				take = ss.getType();
			}
		}
		if (take == null && !candidates.isEmpty())
			take = candidates.iterator().next().getType();
		if (candidates.size() > 1) {
			logger.error("Multiple candidates for " + type + ", take " + take + "; possible: " + candidates);
		} else if (take != null)
			return take;

		logger.error("Does not have a library candidate superclass or interface for " + type + " (superclass: "
				+ type.getSootClass().getSuperclassUnsafe() + "; superinterfaces: "
				+ type.getSootClass().getInterfaces() + ")");
		return type;
	}

	/**
	 * Checks whether the given edge is valid
	 * 
	 * @param edge The edge to check
	 * @return True if the given edge is valid, false otherwise
	 */
	public static boolean isValidEdge(VirtualEdge edge) {
		if (edge == null)
			return false;

		VirtualEdgeSource source = edge.getSource();
		Set<VirtualEdgeTarget> targets = edge.getTargets();
		if (source instanceof InstanceinvokeSource) {
			InstanceinvokeSource instanceSource = (InstanceinvokeSource) source;

			// If the source is a lifecycle method, we filter it out
			if (AndroidEntryPointConstants.isLifecycleSubsignature(instanceSource.getSubSignature().toString()))
				return false;

			// We filter out obfuscated method names
			if (instanceSource.getSubSignature().getMethodName().length() < 3)
				return false;

			// Filter out edges that are much too deep to still make sense
			if (getEdgeDepth(edge) > 10)
				return false;

			if (targets.size() == 1) {
				VirtualEdgeTarget target = targets.iterator().next();
				if (target instanceof DirectTarget) {
					DirectTarget directTarget = (DirectTarget) target;

					// A callback can never be a constructor on the base object if the source is not
					// a constructor
					if (!instanceSource.getSubSignature().getMethodName().equals("<init>")
							&& directTarget.getTargetMethod().getMethodName().equals("<init>"))
						return false;

					// If we have a direct connection between a method and itself (a->a), this edge
					// is invalid
					if (directTarget.getTargetMethod().equals(instanceSource.getSubSignature()))
						return false;

					// We filter out obfuscated method names
					if (directTarget.getTargetMethod().getMethodName().length() < 3)
						return false;

					// If the target is a lifecycle method, the edge is invalid as well
					if (AndroidEntryPointConstants.isLifecycleSubsignature(directTarget.getTargetMethod().toString()))
						return false;
					// the last target should be within a library class.
					if (!DCIDFactory.isLibraryClass(directTarget.getTargetType().getSootClass()))
						return false;
				}
			}
		}

		// We have no reason to believe that this edge is invalid
		return true;
	}

	/**
	 * Gets the maximum depth of nested edges inside the given edge
	 * 
	 * @param edge The starting edge
	 * @return The maximum number of edges into which one can descend from the given
	 *         starting edge
	 */
	private static int getEdgeDepth(VirtualEdge edge) {
		int max = 0;
		for (VirtualEdgeTarget target : edge.getTargets())
			max = Math.max(max, getEdgeDepth(target));
		return max;
	}

	/**
	 * Gets the maximum depth of nested edges inside the given edge
	 * 
	 * @param target The starting edge target
	 * @return The maximum number of edges into which one can descend from the given
	 *         starting edge
	 */
	private static int getEdgeDepth(VirtualEdgeTarget target) {
		int max = 0;
		if (target instanceof IndirectTarget) {
			IndirectTarget indirectTarget = (IndirectTarget) target;
			for (VirtualEdgeTarget nextTarget : indirectTarget.getTargets()) {
				max = Math.max(max, getEdgeDepth(nextTarget));
			}
		} else
			max = 1;
		return max;
	}

	/**
	 * Gets the tainted object at the given call site
	 * 
	 * @param taintPaths The taint paths in which to look for the given call site
	 * @param callSite   The call site
	 * @return The tainted object at the given call site or <code>null</code> if no
	 *         such object was found
	 */
	private DynamicTaintLocation findTaintedObject(Set<TaintPath> taintPaths, Stmt callSite) {
		for (TaintPath tp : taintPaths) {
			for (DynamicTaintLocation loc : tp.getPath()) {
				if (loc.getStmt() == callSite)
					return loc;
			}
		}
		return null;
	}

	/**
	 * Gets the last call site in user code before the given statement was reached.
	 * The given statement may be assumed to be back in user code, i.e., the method
	 * analyzes calls from user code into a library and back into user code through
	 * a callback. The given statement should be a statement in the callback if
	 * <code>inUserCode</code> is <code>true</code>; otherwise it is assumed to be
	 * still inside the library.
	 * 
	 * @param stmt       A statement in the callback
	 * @param inUserCode True if the given statement is already back in user code,
	 *                   false otherwise
	 * @return The statement with the first call into the library
	 */
	private CISet<Stmt> getLastUserCodeCallSite(Stmt stmt, boolean inUserCode) {
		// Obtain all traces towards the callee code
		DynamicCallgraph cg = CIScene.v().getDynamicCallgraph();
		Set<Trace> traces = cg.getTracesTo(stmt);

		CISet<Stmt> callSites = new CIHashSet<>();
		for (Trace t : traces) {
			// Exclude the statement immediately inside the callback
			for (int i = inUserCode ? 2 : 1; i < t.size(); i++) {
				Stmt calleeStmt = t.getStmt(i - 1);
				Stmt callSite = t.getStmt(i);
				if (calleeStmt != null && callSite != null) {
					SootMethod callee = AnalysisUtils.getMethod(calleeStmt);
					SootMethod caller = AnalysisUtils.getMethod(callSite);
					if (DCIDFactory.isLibraryClass(callee.getDeclaringClass())
							&& !DCIDFactory.isLibraryClass(caller.getDeclaringClass())) {
						// We have a context switch from application code to library code
						callSites.add(callSite);
						break;
					}
				}
			}
		}

		return callSites;
	}

	/**
	 * Gets the last call site inside the library code before the callback was
	 * called
	 * 
	 * @param stmt A statement in the callback
	 * @return The statement that is the last call site before the callback was
	 *         invoked
	 */
	private Stmt getLastLibraryCodeCallSite(Stmt stmt) {
		// Obtain all traces towards the callee code
		DynamicCallgraph cg = CIScene.v().getDynamicCallgraph();
		Set<Trace> traces = cg.getTracesTo(stmt);

		for (Trace t : traces) {
			for (int i = 1; i < t.size(); i++) {
				Stmt calleeStmt = t.getStmt(i - 1);
				Stmt callSite = t.getStmt(i);
				if (calleeStmt != null && callSite != null) {
					SootMethod callee = AnalysisUtils.getMethod(calleeStmt);
					SootMethod caller = AnalysisUtils.getMethod(callSite);
					if (!DCIDFactory.isLibraryClass(callee.getDeclaringClass())
							&& DCIDFactory.isLibraryClass(caller.getDeclaringClass())) {
						// We have a context switch from library code to application code
						return callSite;
					}
				}
			}
		}

		return null;
	}

	@Override
	protected void initialize() {
		//
	}

}
