package reproduce.callbackcodegenerationfromlibs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;

import soot.ArrayType;
import soot.Body;
import soot.ClassSource;
import soot.DoubleType;
import soot.FloatType;
import soot.FoundFile;
import soot.G;
import soot.IntegerType;
import soot.Local;
import soot.LongType;
import soot.Modifier;
import soot.PhaseOptions;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SourceLocator;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.asm.AsmClassSource;
import soot.dexpler.Util;
import soot.jimple.DoubleConstant;
import soot.jimple.DynamicInvokeExpr;
import soot.jimple.FloatConstant;
import soot.jimple.GotoStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LongConstant;
import soot.jimple.NopStmt;
import soot.jimple.NullConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.options.Options;

public class GenerateAPKsFromLibraries {

	private static final int MAX_DEPTH_CHAIN = 1;
	static List<SootClass> addedClasses = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		File fApk = new File("../CallbackCodeGenerationApp/app/build/outputs/apk/debug/app-debug.apk");
		if (!fApk.exists())
			throw new IOException(fApk.getAbsolutePath());
		File libFolder = new File("libs");
		List<String> libs = new ArrayList<>();
		libs.add("com.squareup.okhttp3:okhttp:4.9.2");
		/*
		 * libs.add("junit:junit:4.13-beta-3");
		 * libs.add("org.scala-lang:scala-library:2.13.0");
		 * libs.add("org.slf4j:slf4j-api:2.0.0-alpha0");
		 * libs.add("com.google.guava:guava:31.0.1-android");
		 * libs.add("ch.qos.logback:logback-classic:1.3.0-alpha4");
		 * libs.add("org.mockito:mockito-core:2.28.2");
		 * libs.add("com.fasterxml.jackson.core:jackson-databind:2.9.9");
		 * libs.add("log4j:log4j:1.2.15");
		 * libs.add("org.apache.commons:commons-lang3:3.9");
		 * libs.add("commons-io:commons-io:20030203.000550");
		 * libs.add("javax.servlet:javax.servlet-api:4.0.1");
		 * libs.add("org.apache.httpcomponents:httpclient:4.5.9");
		 */
		for (String lib : libs) {
			File libD = new File("libs/" + lib.replace(":", "_"));
			if (libD.exists())
				continue;
			run("mvn", "-Dmaven.repo.local=" + libD.getAbsolutePath(),
					"org.apache.maven.plugins:maven-dependency-plugin:2.8:get", "-Dartifact=" + lib + ":jar");

		}
		for (File lib : libFolder.listFiles()) {
			Collection<File> allJars = FileUtils.listFiles(lib, new SuffixFileFilter(".jar", IOCase.INSENSITIVE),
					new NotFileFilter(new PrefixFileFilter(".")));
			G.reset();
			addedClasses.clear();
			Options.v().set_src_prec(Options.src_prec_apk_c_j);
			Options.v().set_allow_phantom_refs(true);
			allJars.add(fApk);
			Options.v().set_process_dir(allJars.stream().map(a -> a.getAbsolutePath()).collect(Collectors.toList()));
			Options.v().set_force_android_jar("/home/marc/Android/Sdk/platforms/android-27/android.jar");
			Options.v().set_process_multiple_dex(true);
			Scene.v().loadNecessaryClasses();
			Options.v().set_output_format(Options.output_format_dex);
			PhaseOptions.v().setPhaseOption("jb", "model-lambdametafactory:true");
			PhaseOptions.getBoolean(PhaseOptions.v().getPhaseOptions("jb"), "model-lambdametafactory");
			Set<SootClass> potentialCallbackClasses = new HashSet<>();
			SootClass ma = Scene.v()
					.getSootClass("reproduce.automaticcallbacktesting.callbackcodegeneration.MainActivity");
			addedClasses.add(ma);
			SootMethod mm = ma.getMethodByName("getMyPackageName");
			Body bb = mm.retrieveActiveBody();
			for (Unit u : bb.getUnits()) {
				if (u instanceof ReturnStmt) {
					ReturnStmt s = (ReturnStmt) u;
					s.setOp(StringConstant.v(lib.getName()));
				}
			}

			Options.v().set_android_api_version(24);
			for (SootClass i : Scene.v().getApplicationClasses()) {
				if (skipClass(i))
					continue;

				if (!i.isPublic())
					continue;
				if (i.isFinal())
					continue;

				if (i.isInterface() || i.isAbstract()) {
					boolean hasAtLeastOneOverridableMethod = false;
					for (SootMethod m : i.getMethods()) {
						if (m.isPublic() || m.isProtected())
							;
						else
							continue;
						if (m.isStatic() || m.isConstructor() || m.isFinal())
							continue;
						hasAtLeastOneOverridableMethod = true;
					}

					// callback!
					if (hasAtLeastOneOverridableMethod)
						potentialCallbackClasses.add(i);
				} else
					continue;

			}
			int midx = 0;
			for (SootClass i : new ArrayList<>(Scene.v().getApplicationClasses())) {
				if (skipClass(i))
					continue;

				if (!i.isPublic())
					continue;
				if (i.isAbstract())
					continue;
				SootMethod ctorCallbackCaller = null;
				for (SootMethod o : i.getMethods()) {
					if (o.isConstructor()) {
						if (o.isPublic()) {
							if (o.getParameterCount() == 0)
								ctorCallbackCaller = o;
							if (ctorCallbackCaller == null)
								ctorCallbackCaller = o;
						}
					}

				}
				if (ctorCallbackCaller == null)
					continue;

				for (SootMethod callbackCallerInLibrary : i.getMethods()) {
					if (!callbackCallerInLibrary.isPublic())
						continue;

					if (!callbackCallerInLibrary.isConcrete())
						continue;

					for (int param = 0; param < callbackCallerInLibrary.getParameterCount(); param++) {
						Type p = callbackCallerInLibrary.getParameterType(param);
						if (p instanceof RefType) {
							SootClass scAbstractCallback = ((RefType) p).getSootClass();

							boolean create = potentialCallbackClasses.contains(scAbstractCallback);

							if (create) {

								SootMethod ctorCallbackParent = null;

								for (SootMethod o : scAbstractCallback.getMethods()) {
									if (o.isConstructor()) {
										if (o.isPublic()) {
											if (ctorCallbackParent == null)
												ctorCallbackParent = o;
											else if (ctorCallbackParent.getParameterCount() > o.getParameterCount())
												ctorCallbackParent = o;
										}
									}

								}
								if (ctorCallbackParent == null)
									continue;

								SootMethod instrumentedMethod = new SootMethod("callback" + midx,
										Collections.emptyList(), VoidType.v());
								instrumentedMethod.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
								// we found a callback!
								createCallback(ma, scAbstractCallback, callbackCallerInLibrary, instrumentedMethod,
										ctorCallbackCaller, ctorCallbackParent, midx, param);
								midx++;
							}
						}
					}
				}
			}
			if (midx == 0) {
				System.out.println(lib.getName() + " has no callbacks");
				continue;
			}
			System.out.println(lib.getName() + " has " + midx + " callbacks to test");
			File f = new File("generated-" + lib.getName() + ".apk");
			FileUtils.deleteDirectory(new File("sootOutput"));
			File apkdebug = new File("sootOutput/app-debug.apk");
			f.delete();
			Options.v().set_force_overwrite(true);
			Options.v().set_src_prec(Options.src_prec_apk);

			for (SootClass i : new ArrayList<>(Scene.v().getApplicationClasses())) {
				for (SootMethod m : i.getMethods()) {
					if (m.isConcrete()) {
						Body b = m.retrieveActiveBody();
						for (Unit u : b.getUnits()) {
							Stmt s = (Stmt) u;
							if (s.containsInvokeExpr()) {
								if (s.getInvokeExpr() instanceof DynamicInvokeExpr) {
									b.getUnits().clear();
									b.getLocals().clear();
									Util.emptyBody(b);
									Util.addExceptionAfterUnit(b, "java.lang.RuntimeException", b.getUnits().getLast(),
											"DynamicInvoke is not supported in Soot's DexPrinter");
									break;
								}
							}
						}
					}
				}
			}
			try {
				Options.v().set_output_format(Options.output_format_dex);
				soot.PackManager.v().writeOutput();

			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
			System.out.println(f.getAbsolutePath());
			Process e = Runtime.getRuntime().exec(new String[] { "java", "-jar", "uber-apk-signer-1.2.1.jar",
					"--overwrite", "-a", apkdebug.getAbsolutePath() });
			if (e.waitFor() != 0) {
				System.err.println("Signature failed");
				String s = IOUtils.toString(e.getErrorStream(), "UTF-8");
				System.err.println(s);
			}
			apkdebug.renameTo(f);

			System.out.println("Find output in " + f.getAbsolutePath());
		}
	}

	private static void run(String... args) throws Exception {
		Process e = Runtime.getRuntime().exec(args);

		Thread thr = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					BufferedReader rd = new BufferedReader(new InputStreamReader(e.getInputStream()));
					while (true) {
						String l = rd.readLine();
						if (l == null)
							return;
						System.out.println(l);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		thr.start();
		if (e.waitFor() != 0) {
			String s = IOUtils.toString(e.getErrorStream(), "UTF-8");
			System.err.println(s);
			System.exit(1);
		}
	}

	private static void createCallback(SootClass ma, SootClass scAbstractCallback,
			SootMethod callbackRegistrationInLibrary, SootMethod instrumentedMethod, SootMethod ctorCallbackCaller,
			SootMethod ctorCallbackParent, int midx, int param) {
		SootMethod addFoundCallback = ma.getMethodByName("addFoundCallback");
		Jimple j = Jimple.v();
		SootClass callback = new SootClass("Callback" + midx, Modifier.PUBLIC);
		callback.getOrAddMethod(instrumentedMethod);
		addedClasses.add(callback);
		callback.setApplicationClass();
		if (scAbstractCallback.isInterface())
			callback.addInterface(scAbstractCallback);
		else
			callback.setSuperclass(scAbstractCallback);
		SootMethod callbackCtor = new SootMethod("<init>", Collections.emptyList(), VoidType.v(), Modifier.PUBLIC);
		callback.getOrAddMethod(callbackCtor);
		JimpleBody cbCtor = j.newBody(callbackCtor);
		callbackCtor.setActiveBody(cbCtor);
		cbCtor.insertIdentityStmts();
		if (scAbstractCallback.isInterface())
			cbCtor.getUnits().add(j.newInvokeStmt(j.newSpecialInvokeExpr(cbCtor.getThisLocal(),
					Scene.v().grabMethod("<java.lang.Object: void <init>()>").makeRef(), Collections.emptyList())));
		else {
			if (ctorCallbackParent == null)
				throw new RuntimeException("Found no suitable constructor for " + scAbstractCallback);
			List<Value> ctorParams = new ArrayList<>();
			for (Type i : ctorCallbackParent.getParameterTypes())
				ctorParams.add(generateValue(i));

			cbCtor.getUnits().add(j.newInvokeStmt(
					j.newSpecialInvokeExpr(cbCtor.getThisLocal(), ctorCallbackParent.makeRef(), ctorParams)));
		}
		cbCtor.getUnits().add(j.newReturnVoidStmt());

		if (param == -1)
			throw new IllegalArgumentException(
					"No callback in " + callbackRegistrationInLibrary.getSignature() + " for " + scAbstractCallback);
		for (SootMethod m : scAbstractCallback.getMethods()) {
			if (m.isPublic() || m.isProtected())
				;
			else
				continue;
			if (m.isStatic() || m.isConstructor() || m.isFinal())
				continue;

			// We found a potential callback method to override
			SootMethod mPotentialCallback = new SootMethod(m.getName(), m.getParameterTypes(), m.getReturnType());
			callback.getOrAddMethod(mPotentialCallback);
			JimpleBody b = j.newBody(mPotentialCallback);
			mPotentialCallback.setActiveBody(b);
			b.insertIdentityStmts();
			String target = m.getSubSignature();
			String source = callbackRegistrationInLibrary.getSubSignature();

			b.getUnits().add(j.newInvokeStmt(j.newStaticInvokeExpr(addFoundCallback.makeRef(), StringConstant.v(source),
					StringConstant.v(target), IntConstant.v(param), IntConstant.v(midx))));

			if (m.getReturnType() instanceof VoidType)
				b.getUnits().add(j.newReturnVoidStmt());
			else
				b.getUnits().add(j.newReturnStmt(generateValue(m.getReturnType())));
			b.validate();
		}

		JimpleBody b = j.newBody(instrumentedMethod);
		instrumentedMethod.setActiveBody(b);
		b.insertIdentityStmts();
		Local lclCallback = j.newLocal("lclCallback", callback.getType());
		b.getLocals().add(lclCallback);
		RefType rtT = RefType.v("java.lang.Throwable");
		Local throwable = j.newLocal("lclThrowable", rtT);
		b.getLocals().add(throwable);
		b.getUnits().add(j.newAssignStmt(lclCallback, j.newNewExpr(callback.getType())));
		b.getUnits().add(j.newInvokeStmt(j.newSpecialInvokeExpr(lclCallback, callbackCtor.makeRef())));

		List<Value> callCallbackInvocation = new ArrayList<>();
		int i = 0;
		for (Type t : callbackRegistrationInLibrary.getParameterTypes()) {
			if (i == param) {
				if (!Scene.v().getOrMakeFastHierarchy().canStoreType(scAbstractCallback.getType(), t))
					System.err.println("Err");
				callCallbackInvocation.add(lclCallback);
			} else
				callCallbackInvocation.add(generateValue(t));
			i++;
		}
		Local lclLibraryCallback = null;
		if (!callbackRegistrationInLibrary.isStatic()) {
			List<Value> ctorCallbackCallerArgs = new ArrayList<>();
			for (Type t : ctorCallbackCaller.getParameterTypes())
				ctorCallbackCallerArgs.add(generateValue(t));
			lclLibraryCallback = j.newLocal("lclLibraryCallback",
					callbackRegistrationInLibrary.getDeclaringClass().getType());
			b.getLocals().add(lclLibraryCallback);
			b.getUnits().add(j.newAssignStmt(lclLibraryCallback,
					j.newNewExpr(ctorCallbackCaller.getDeclaringClass().getType())));
			if (callbackRegistrationInLibrary.isConstructor())
				b.getUnits().add(j.newInvokeStmt(j.newSpecialInvokeExpr(lclLibraryCallback,
						callbackRegistrationInLibrary.makeRef(), callCallbackInvocation)));
			else {
				b.getUnits().add(j.newInvokeStmt(j.newSpecialInvokeExpr(lclLibraryCallback,
						ctorCallbackCaller.makeRef(), ctorCallbackCallerArgs)));
				b.getUnits().add(j.newInvokeStmt(j.newVirtualInvokeExpr(lclLibraryCallback,
						callbackRegistrationInLibrary.makeRef(), callCallbackInvocation)));
			}
		} else {
			b.getUnits().add(j.newInvokeStmt(
					j.newStaticInvokeExpr(callbackRegistrationInLibrary.makeRef(), callCallbackInvocation)));
		}

		// Alright. Registration took place.
		Map<Type, Local> locals = new HashMap<>();
		createCalls(callbackRegistrationInLibrary.getDeclaringClass(), callbackRegistrationInLibrary, j, b, rtT,
				throwable, lclLibraryCallback, locals, 0);

		b.getUnits().add(j.newReturnVoidStmt());
		b.validate();

	}

	private static void createCalls(SootClass classToCall, SootMethod callbackRegistrationInLibrary, Jimple j,
			JimpleBody b, RefType rtT, Local throwable, Local lclLibraryCallback, Map<Type, Local> locals, int depth) {
		if (classToCall.getName().equals("java.lang.String") || classToCall.getName().equals("java.lang.CharSequence"))
			return;
		for (SootClass cii : Scene.v().getActiveHierarchy().getSuperclassesOfIncluding(classToCall)) {
			if (cii.getType() == Scene.v().getObjectType())
				continue;
			if (cii.resolvingLevel() < SootClass.SIGNATURES)
				continue;
			for (SootMethod im : cii.getMethods()) {
				if (!im.isPublic())
					continue;
				if (im.isConstructor() || im.isStaticInitializer())
					continue;
				if (im == callbackRegistrationInLibrary)
					continue;
				List<Value> params = new ArrayList<>();
				for (Type imm : im.getParameterTypes())
					params.add(generateValue(imm, locals));
				Unit added;
				InvokeExpr inv;
				if (im.isStatic()) {
					inv = j.newStaticInvokeExpr(im.makeRef(), params);
				} else {
					if (lclLibraryCallback == null) {
						continue;
					}
					inv = j.newVirtualInvokeExpr(lclLibraryCallback, im.makeRef(), params);
				}
				NopStmt nop = j.newNopStmt();
				GotoStmt gotos = j.newGotoStmt(nop);
				Local vAddedLocal = null;
				if (im.isStatic()) {
					added = j.newInvokeStmt(inv);
					b.getUnits().add(added);
				} else {
					if (!(inv.getType() instanceof VoidType)) {
						vAddedLocal = locals.get(inv.getType());
						if (vAddedLocal == null) {
							vAddedLocal = j.newLocal("lclRet" + inv.getType().toString().replace(".", "_"),
									inv.getType());
							locals.put(inv.getType(), vAddedLocal);
							b.getLocals().add(vAddedLocal);
						}
						added = j.newAssignStmt(vAddedLocal, inv);
						b.getUnits().add(added);
						if (depth < MAX_DEPTH_CHAIN) {
							if (vAddedLocal.getType() instanceof RefType) {
								RefType t = (RefType) vAddedLocal.getType();
								if (!t.getSootClass().isInterface())
									createCalls(t.getSootClass(), inv.getMethod(), j, b, rtT, throwable, vAddedLocal,
											locals, depth + 1);
							}
						}
					} else {
						added = j.newInvokeStmt(inv);
						b.getUnits().add(added);
					}
				}
				b.getUnits().add(gotos);
				IdentityStmt id = j.newIdentityStmt(throwable, j.newCaughtExceptionRef());
				b.getUnits().add(id);
				b.getUnits().add(nop);
				b.getTraps().add(j.newTrap(rtT.getSootClass(), added, gotos, id));
				if (vAddedLocal != null)
					locals.remove(vAddedLocal.getType(), vAddedLocal);
			}
		}
	}

	static Value generateValue(Type type, Map<Type, Local> p) {
		if (type instanceof RefType) {
			SootClass sc = ((RefType) type).getSootClass();
			if (!sc.isInterface()) {
				for (SootClass t : Scene.v().getActiveHierarchy().getSuperclassesOfIncluding(sc)) {
					Local c = p.get(t.getType());
					if (c != null)
						return c;
				}
			}
		}

		return generateValue(type);

	}

	static Value generateValue(Type type) {
		if (type instanceof FloatType)
			return FloatConstant.v(0);
		else if (type instanceof DoubleType)
			return DoubleConstant.v(0);
		else if (type instanceof LongType)
			return LongConstant.v(0);
		else if (type instanceof IntegerType)
			return IntConstant.v(0);
		else if (type instanceof ArrayType)
			return NullConstant.v();
		else if (type instanceof RefType) {
			if (((RefType) type).getSootClass().getName().equals("java.lang.String"))
				return StringConstant.v("");
			else
				return NullConstant.v();
		} else
			throw new IllegalArgumentException();
	}

	private static boolean skipClass(SootClass i) throws NoSuchFieldException, SecurityException,
			IllegalArgumentException, IllegalAccessException, IOException {
		if (i.getName().startsWith("reproduce.") || i.getName().startsWith("android.support."))
			return true;
		ClassSource sf = SourceLocator.v().getClassSource(i.getName());
		if (!(sf instanceof AsmClassSource))
			return true;
		AsmClassSource asm = (AsmClassSource) sf;
		Field f = AsmClassSource.class.getDeclaredField("foundFile");
		f.setAccessible(true);
		FoundFile fi = (FoundFile) f.get(asm);
		File libs = new File("libs").getCanonicalFile();
		File r = fi.getFile().getParentFile().getCanonicalFile();
		while (true) {
			if (r.equals(libs))
				return false;
			r = r.getParentFile();
			if (r == null)
				return true;
		}
	}

}
