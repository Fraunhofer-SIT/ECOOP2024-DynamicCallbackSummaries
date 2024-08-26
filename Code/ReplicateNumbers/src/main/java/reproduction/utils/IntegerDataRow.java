package reproduction.utils;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a row of integer data and offers some statistical functions.
 */
public class IntegerDataRow {

	private Queue<Integer> row = new ConcurrentLinkedDeque<>();
	private AtomicInteger max = new AtomicInteger(Integer.MIN_VALUE), min = new AtomicInteger(Integer.MAX_VALUE);
	private CounterMap<Integer> map;
	private AtomicLong cumulativeSum = new AtomicLong(0);
	private AtomicLong length = new AtomicLong(0);

	public String toDebugString() {
		StringBuilder sb = new StringBuilder();
		Map<String, String> stats = getStats(NumberFormat.getIntegerInstance(Locale.US));
		for (Entry<String, String> p : stats.entrySet()) {
			sb.append(p.getKey()).append(": ").append(p.getValue()).append("\n");
		}
		return sb.toString();
	}

	Map<String, String> getStats(NumberFormat numberFormat) {
		Map<String, String> stats = new HashMap<>();
		stats.put("# of data points", numberFormat.format(getSize()));
		stats.put("Min", numberFormat.format(getMinValue()));
		stats.put("Max", numberFormat.format(getMaxValue()));
		stats.put("Arithmetic Mean", numberFormat.format(getArithmeticMean()));
		stats.put("Cumulative Sum", numberFormat.format(getCumulativeSum()));
		// stats.put("Median", numberFormat.format(getMedian()));
		// stats.put("Mode", numberFormat.format(getMode()));
		return stats;

	}

	public IntegerDataRow() {
	}

	public IntegerDataRow(List<Integer> input) {
		for (int i : input)
			add(i);
	}

	public IntegerDataRow(int... input) {
		for (int c : input)
			add(c);
	}

	@Override
	public IntegerDataRow clone() {
		IntegerDataRow r = new IntegerDataRow();
		for (int i : row)
			r.add(i);
		return r;
	}

	public CounterMap<Integer> getCounts() {
		if (map == null) {
			CounterMap<Integer> m = new CounterMap<>();
			for (int p : row)
				m.add(p, 1);
			map = m;
		}
		return map;
	}

	/**
	 * Returns the most frequent value in the data set
	 * 
	 * @return the most frequent value in the data set
	 */
	public int getMode() {
		return getHighestOccurring().getKey();
	}

	public Entry<Integer, Integer> getHighestOccurring() {
		return getCounts().sortedValues().iterator().next();
	}

	public void add(int c) {
		long newC = cumulativeSum.addAndGet(c);
		if (newC < 0)
			throw new RuntimeException("Overflow!");
		computeMinMax(max, c, true);
		computeMinMax(min, c, false);
		row.add(c);
		length.incrementAndGet();
		if (map != null) {
			synchronized (map) {
				map.add(c, 1);
			}
		}
	}

	private void computeMinMax(AtomicInteger m, int c, boolean max) {
		while (true) {
			int prev = m.get();
			int d;
			if (max)
				d = Math.max(prev, c);
			else
				d = Math.min(prev, c);
			if (prev == d)
				return;
			if (m.compareAndSet(prev, d))
				return;
		}
	}

	public long getSize() {
		return length.get();
	}

	public long getCumulativeSum() {
		return cumulativeSum.get();
	}

	public double getArithmeticMean() {
		return (double) cumulativeSum.get() / getSize();
	}

	public Queue<Integer> getRawData() {
		return row;
	}

	public int getMaxValue() {
		return max.get();
	}

	public int getMinValue() {
		return min.get();
	}

	public float getMedian() {
		List<Integer> row = new ArrayList<>(this.row);
		Collections.sort(row);
		if (row.size() % 2 == 0) { 
			int x = row.size() / 2; 
			int r1 = row.get(x - 1);
			int r2 = row.get(x);
			return (r1 + r2) / 2F; 
		} else { 
			return  row.get(row.size() / 2); 
		}

	}

	@Override
	public String toString() {
		return toDebugString();
	}

}
