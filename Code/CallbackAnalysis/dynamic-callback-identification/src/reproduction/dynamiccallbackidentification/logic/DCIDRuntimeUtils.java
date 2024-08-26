package reproduction.dynamiccallbackidentification.logic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import de.codeinspect.dynamicvalues.android.LogUtils;

/**
 * Runtime component for the DCID analysis
 * 
 * 
 *
 */
public class DCIDRuntimeUtils {
	private static int idCount = 1;
	private static Map<Class<?>, Boolean>[] cachedEquivalence = new Map[idCount];

	public static void test(String a, Object o) {
		LogUtils.e("CI-DBG: " + a + ": " + o + " (" + o.getClass() + ")");
	}

	static class NameParameterCountPair {
		Method m;

		public NameParameterCountPair(Method m) {
			this.m = m;
		}

		@Override
		public int hashCode() {
			return m.getParameterCount() ^ m.getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof NameParameterCountPair) {
				Method c = ((NameParameterCountPair) obj).m;
				if (c.getParameterCount() != m.getParameterCount())
					return false;
				if (!c.getName().equals(m.getName()))
					return false;
				return true;
			}
			return super.equals(obj);
		}

		@Override
		public String toString() {
			return DCIDRuntimeUtils.toString(m);
		}
	}

	public static String toString(Method m) {
		return m.getName() + "(" + Arrays.toString(m.getParameterTypes()) + ")";
	}

	/**
	 * Checks whether the class has the same methods as the class
	 * 
	 * @param o
	 * @param originalWrapped e.g. java.util.List
	 */
	@SuppressWarnings("unchecked")
	public static boolean isEquivalent(Object o, Class<?> originalWrapped, int id) {
		if (o != null) {
			Class<? extends Object> tobewrapped = o.getClass();
			if (tobewrapped == originalWrapped)
				return true;
			Map<Class<?>, Boolean> mCached = cachedEquivalence[id];
			if (mCached == null) {
				mCached = new HashMap<>();
				cachedEquivalence[id] = mCached;
			}
			Boolean b = mCached.get(tobewrapped);
			if (b != null)
				return b;
			try {
				if (originalWrapped.isAssignableFrom(tobewrapped)) {
					for (Class<?> interf : tobewrapped.getInterfaces()) {
						if (!originalWrapped.isAssignableFrom(interf)) {
							b = false;
							return b;
						}
					}
					Map<NameParameterCountPair, Set<Method>> m = new HashMap<>();
					add(originalWrapped, m, originalWrapped.getMethods());
					// add(originalWrapped, m, originalWrapped.getDeclaredMethods());
					methodFound: for (Method t : tobewrapped.getMethods()) {
						if ((t.getModifiers() & Modifier.STATIC) != 0)
							continue;
						NameParameterCountPair c = new NameParameterCountPair(t);
						Set<Method> l = m.get(c);
						if (l != null) {
							tryNextMethod: for (Method q : l) {
								if (!q.getReturnType().isAssignableFrom(t.getReturnType()))
									continue tryNextMethod;
								for (int i = 0; i < t.getParameterCount(); i++) {
									Class<?> actualObjParameter = t.getParameterTypes()[i];
									Class<?> wrappedObjParameter = q.getParameterTypes()[i];
									// the actual object may be more specific than the wrapped method. This is ok,
									// but not the other way around.
									/*
									 * if (wrappedObjParameter.isAssignableFrom(actualObjParameter)) { ; } else {
									 * continue tryNextMethod; }
									 */
									// Actually, the parameter types must match exactly.
									if (!wrappedObjParameter.equals(actualObjParameter))
										continue;

								}
								// if we reach this it is fine.
								continue methodFound;
							}
						}
						if (t.getDeclaringClass() == Object.class)
							continue;
						b = false;
						return b;
					}
					System.out.println("CIType-Checking " + o.getClass() + " and " + originalWrapped + ": true ");
					b = true;
					return b;
				}
				b = false;
				return b;
			} finally {
				mCached.put(tobewrapped, b);
			}
		}
		return false;
	}

	private static void add(Class originalWrapped, Map<NameParameterCountPair, Set<Method>> m, Method[] methods) {
		for (Method c : methods) {
			if ((c.getModifiers() & Modifier.PUBLIC) == 0)
				continue;
			if ((c.getModifiers() & Modifier.STATIC) != 0)
				continue;
			NameParameterCountPair p = new NameParameterCountPair(c);
			Set<Method> ex = m.get(p);
			if (ex == null) {
				ex = new HashSet<>();
				m.put(p, ex);
			}
			ex.add(c);
		}
	}

	/**
	 * Sets the callback ID and the argument index in the given target object whose
	 * class may potentially implement a callback.
	 * 
	 * @param target      The target object
	 * @param callsiteId  The unique ID of the call site
	 * @param argumentIdx The argument of the potential callback object at the call
	 *                    site
	 */
	public static void handleCallSite(Object target, int callsiteId, int argumentIdx) {
		Class<?> targetClass = target.getClass();
		if (!(target instanceof DCIDWrapped)) {
			LogUtils.w("Unwrapped: " + targetClass);
			return;
		}
		try {
			Field f = targetClass.getField("CALLBACK_CALLSITE_THREAD");
			Field f2 = targetClass.getField("CALLBACK_CALLSITE_ID");
			if (f != null) {
				long id = Thread.currentThread().getId();
				AtomicLong intThread = (AtomicLong) f.get(target);
				if (!intThread.compareAndSet(id, id)) {
					if (!intThread.compareAndSet(-1, id)) {
						LogUtils.w("Callback " + targetClass + " used in multiple threads.");
						if (f2 != null)
							f2.set(target, -1);
						return;
					}
				}
			}
			if (f2 != null)
				f2.set(target, callsiteId);
			else
				LogUtils.e("Could not find CALLBACK_CALLSITE_ID in " + targetClass);
			f = targetClass.getField("CALLBACK_CALLSITE_ARG_INDEX");
			if (f != null)
				f.set(target, argumentIdx);
			else
				LogUtils.e("Could not find CALLBACK_CALLSITE_ARG_INDEX in " + targetClass);
			LogUtils.i("Set callsiteid=" + callsiteId + ", argument=" + argumentIdx + " for " + targetClass
					+ " identityhc: " + System.identityHashCode(target));

		} catch (Exception e) {
			LogUtils.e("Could not set DCID data via reflection: " + targetClass, e);
		}
	}

}
