package de.upb.mike.amt;

public class Timer {
	public static final int FORMAT_MS = 0;
	public static final int FORMAT_S = 1;
	public static final int FORMAT_M = 2;
	public static final int FORMAT_H = 3;

	long start;
	long end;

	public Timer() {
		start = 0;
		end = start;
	}

	public Timer start() {
		start = System.currentTimeMillis();
		return this;
	}

	public Timer stop() {
		end = System.currentTimeMillis();
		return this;
	}

	public long getTime() {
		return getTime(FORMAT_MS);
	}

	public long getTime(int format) {
		if (start > 0 && end > 0) {
			long time = end - start;
			if (format >= FORMAT_S) {
				time = time / 1000;
			}
			if (format >= FORMAT_M) {
				time = time / 60;
			}
			if (format >= FORMAT_H) {
				time = time / 60;
			}
			return time;
		} else {
			return -1;
		}
	}
}