package reproduction.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;

public class CounterMap<T> implements Iterable<Map.Entry<T, Integer>> {
	private Map<T, Integer> count = new ConcurrentHashMap<T, Integer>();
	private int countedObj;

	public CounterMap() {
	}

	public CounterMap(int size) {
		count = new ConcurrentHashMap<T, Integer>(size);
	}

	public int count(T o) {
		countedObj++;
		int r = count.getOrDefault(o, 0) + 1;
		count.put(o, r);
		return r;
	}

	public int getCountedObjects() {
		return countedObj;
	}

	public int getCount(Object o) {
		return count.getOrDefault(o, 0);
	}

	@Override
	public Iterator<Entry<T, Integer>> iterator() {
		return count.entrySet().iterator();
	}

	public Integer numberOfDifferentObjects() {
		return count.size();
	}

	public Set<T> getCountedSet() {
		return count.keySet();
	}

	public Iterable<Entry<T, Integer>> sortedValues() {
		SortedMap<T, Integer> sorted = new TreeMap<>(new Comparator<Object>() {

			@Override
			public int compare(Object o1, Object o2) {
				int i1 = count.get(o1);
				int i2 = count.get(o2);
				if (i1 == i2 && o1 != o2)
					return -1;
				return -Integer.compare(i1, i2);
			}
		});
		sorted.putAll(count);

		return sorted.entrySet();
	}

	public Iterable<Entry<T, Integer>> sortedKeys() {
		SortedMap<T, Integer> sorted = new TreeMap<T, Integer>();
		sorted.putAll(count);

		return sorted.entrySet();
	}

	public Collection<T> keyIterator() {
		return count.keySet();
	}

	public void add(T o, int n) {
		countedObj += n;
		count.put(o, count.getOrDefault(o, 0) + n);
	}

	public void addAll(CounterMap<T> add) {
		for (Entry<T, Integer> entry : add) {
			add(entry.getKey(), entry.getValue());
		}
	}

	public static CounterMap<String> readInCounterMap(Collection<String> readLines, int size) {
		CounterMap<String> counter = new CounterMap<String>(readLines.size());
		for (String s : readLines) {
			String num = s.substring(0, s.indexOf(','));
			int n = Integer.parseInt(num);
			String word = s.substring(num.length() + 1);
			counter.add(word, n);

		}
		return counter;
	}

	public static CounterMap<String> readInCounterMap(Collection<String> readLines) {
		return readInCounterMap(readLines, readLines.size());
	}

	public void save(File file) throws IOException {
		try (OutputStream out = new FileOutputStream(file)) {
			writeInt(out, count.size());
			for (Entry<T, Integer> entry : count.entrySet()) {
				String s = entry.getKey().toString();
				byte[] b = s.getBytes(StandardCharsets.UTF_8);
				writeInt(out, b.length);
				writeInt(out, entry.getValue());
				out.write(b);
			}
		}
	}

	private static void writeInt(OutputStream o, int x) throws IOException {
		o.write(new byte[] { (byte) (x >> 24), (byte) (x >> 16), (byte) (x >> 8), (byte) (x >> 0) });
	}

	public static CounterMap<String> readInCounterMapSerialized(File map) throws IOException {
		if (map.length() > 1024 * 1024 * 100)
			return new CounterMap<String>();
		try (FileInputStream fi = new FileInputStream(map)) {
			byte[] r = IOUtils.toByteArray(fi, map.length());
			ByteBuffer buffer = ByteBuffer.wrap(r);
			int size = buffer.getInt();
			CounterMap<String> results = new CounterMap<String>((int) (size * 1.25F));

			int countedObj = 0;
			while (buffer.hasRemaining()) {
				int lenStr = buffer.getInt();
				int count = buffer.getInt();
				byte[] b = new byte[lenStr];
				buffer.get(b);
				results.count.put(new String(b, "UTF-8"), count);
				countedObj += count;
			}

			results.countedObj = countedObj;
			return results;
		}
	}

	@Override
	public String toString() {
		return count.toString();
	}

	public String toMultiLineString() {
		StringBuilder b = new StringBuilder();
		for (Entry<T, Integer> s : count.entrySet()) {
			b.append(s.getKey()).append(": ").append(s.getValue()).append("\n");
		}
		if (b.length() > 0)
			b.setLength(b.length() - 1);
		return b.toString();
	}

	public boolean contains(String api) {
		return count.getOrDefault(api, 0) > 0;
	}

	public void clear() {
		count.clear();
	}

	public void set(T key, int val) {
		count.put(key, val);
	}

}
