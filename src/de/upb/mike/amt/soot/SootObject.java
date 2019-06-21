package de.upb.mike.amt.soot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.upb.mike.amt.Config;
import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import soot.PackManager;
import soot.Printer;
import soot.SootClass;
import soot.Transform;
import soot.options.Options;

public class SootObject {
	public static final int FLAG_READ_NON_LAUNCHER_APPS = 1;
	public static final int FLAG_MERGE1 = 2;
	public static final int FLAG_MERGE2 = 3;

	private static SootObject instance = new SootObject();

	private String launcherActivity;

	private SootObject() {
	}

	public static SootObject getInstance() {
		return instance;
	}

	public void parseApp(String filePath, int flag) {
		if (filePath != null) {
			Log.log("Running Soot: " + filePath + " (Mode: " + flag + ")", Log.LOG_LEVEL_DEBUG);
		} else {
			Log.log("Running Soot to merge the app. (Mode: " + flag + ")", Log.LOG_LEVEL_DEBUG);
		}
		Log.silence(true);

		soot.G.reset();

		Options.v().set_allow_phantom_refs(true);
		if (Log.loglevel > Log.LOG_LEVEL_DEBUG) {
			Options.v().set_debug(true);
		} else {
			Options.v().set_debug(false);
		}
		if (Log.loglevel > Log.LOG_LEVEL_VERBOSE) {
			Options.v().set_verbose(true);
		} else {
			Options.v().set_verbose(false);
		}
		Options.v().set_whole_program(true);
		Options.v().set_android_jars(Config.getInstance().getAndroidPlatformPath());

		if (filePath != null) {
			List<String> excludeList = new ArrayList<>();
			excludeList.addAll(
					Arrays.asList(new String[] { Data.getInstance().getPackageMap().get(filePath) + ".BuildConfig",
							Data.getInstance().getPackageMap().get(filePath) + ".R",
							Data.getInstance().getPackageMap().get(filePath) + ".R$*" }));
			if (flag != FLAG_MERGE1) {
				excludeList.addAll(Arrays.asList(new String[] { "android.support.*", "com.google.*" }));
			}
			Log.log("Excluding: " + excludeList.toString(), Log.LOG_LEVEL_DETAILED);
			Options.v().set_exclude(excludeList);
		}

		if (flag == FLAG_MERGE2) {
			Options.v().set_src_prec(Options.src_prec_jimple);
			Options.v().set_process_dir(Collections.singletonList(Config.getInstance().getSootOutputPath()));
			Options.v().set_output_format(Options.output_format_dex);
			Options.v().set_no_writeout_body_releasing(false);
		} else {
			Options.v().set_src_prec(Options.src_prec_apk);
			Options.v().set_process_dir(Collections.singletonList(filePath));
			Options.v().set_output_format(Options.output_format_none);
			Options.v().set_no_writeout_body_releasing(true);
		}

		if (flag == FLAG_READ_NON_LAUNCHER_APPS) {
			PackManager.v().getPack("wjtp").add(new Transform("wjtp.ParseTransformer", new ParseTransformer(filePath)));
		} else if (flag == FLAG_MERGE1) {
			PackManager.v().getPack("wjtp")
					.add(new Transform("wjtp.InstrumentationTransformer", new InstrumentationTransformer()));
		}

		soot.Main.main(new String[] { "-pp" });

		Log.silence(false);
		Log.log("done!", Log.LOG_LEVEL_DEBUG);
	}

	public String getLauncherActivity() {
		return launcherActivity;
	}

	public void setLauncherActivity(String launcherActivity) {
		this.launcherActivity = launcherActivity;
	}

	public static void print(SootClass sc) {
		PrintWriter writerOut = new PrintWriter(System.out);
		Printer.v().printTo(sc, writerOut);
		writerOut.flush();
	}

	public static void print(SootClass sc, File outputFile) {
		try (FileWriter fw = new FileWriter(outputFile)) {
			PrintWriter writerOut = new PrintWriter(fw);
			Printer.v().printTo(sc, writerOut);
			writerOut.flush();
		} catch (IOException e) {
			Log.log("Cannot write File: " + outputFile.getAbsolutePath() + " (" + e.getClass().getSimpleName() + ": "
					+ e.getMessage() + ")", Log.LOG_LEVEL_ERROR);
		}
	}
}