package de.upb.mike.amt;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {
	private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("MM/dd/yyyy - hh:mm:ss");

	public static final int LOG_LEVEL_ERROR = -2;
	public static final int LOG_LEVEL_WARNING = -1;
	public static final int LOG_LEVEL_NORMAL = 1;
	public static final int LOG_LEVEL_DEBUG = 2;
	public static final int LOG_LEVEL_DETAILED = 3;
	public static final int LOG_LEVEL_VERBOSE = 4;

	private static StringBuilder log = new StringBuilder();

	public static int loglevel = LOG_LEVEL_NORMAL;
	public static boolean silence = false;

	private static PrintStream out = System.out;
	private static PrintStream err = System.err;
	private static PrintStream dummy = new PrintStream(new OutputStream() {
		@Override
		public void write(int b) throws IOException {
			// do nothing
		}
	});

	public static void log(String msg, int loglevel) {
		if (loglevel <= Log.loglevel) {
			msg = DATE_TIME_FORMAT.format(new Date()) + " AMT-Log> " + msg;
			log.append(msg + "\n");
			Log.out.println(msg);
		}
	}

	public static String getLog() {
		return log.toString();
	}

	public static void silence(boolean on) {
		silence = on;
		if (on && loglevel < LOG_LEVEL_DETAILED) {
			System.setOut(dummy);
			System.setErr(dummy);
		} else {
			System.setOut(out);
			System.setErr(err);
		}
	}
}