package de.upb.mike.amt.soot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.upb.mike.amt.Config;
import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.Transform;
import soot.options.Options;
import soot.toDex.DexPrinter;

public class SootObject {
	public static final int FLAG_READ_NON_LAUNCHER_APPS = 1;
	public static final int FLAG_MERGE = 2;

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
		Options.v().set_allow_phantom_refs(true);
		Options.v().set_ignore_resolution_errors(true);
		Options.v().set_prepend_classpath(true);
		Options.v().set_process_multiple_dex(true);

		final List<String> excludeList = new ArrayList<>();
		excludeList
				.addAll(Arrays.asList(new String[] { Data.getInstance().getPackageMap().get(filePath) + ".BuildConfig",
						Data.getInstance().getPackageMap().get(filePath) + ".R",
						Data.getInstance().getPackageMap().get(filePath) + ".R$*" }));
		if (flag != FLAG_MERGE) {
			excludeList.addAll(Config.getInstance().getDefaultExcludes());
		}
		Log.log("Excluding: " + excludeList.toString(), Log.LOG_LEVEL_DETAILED);
		Options.v().set_exclude(excludeList);

		Options.v().set_process_dir(Collections.singletonList(filePath));
		Options.v().set_src_prec(Options.src_prec_apk);
		if (flag == FLAG_READ_NON_LAUNCHER_APPS) {
			Options.v().set_output_format(Options.output_format_none);
			Options.v().set_no_writeout_body_releasing(true);
			PackManager.v().getPack("wjtp").add(new Transform("wjtp.ParseTransformer", new ParseTransformer(filePath)));
		} else if (flag == FLAG_MERGE) {
			Options.v().set_output_format(Options.output_format_dex);
			Options.v().set_no_writeout_body_releasing(false);
			PackManager.v().getPack("wjtp")
					.add(new Transform("wjtp.InstrumentationTransformer", new InstrumentationTransformer()));
		}
		Scene.v().loadNecessaryClasses();
		PackManager.v().runPacks();
		if (flag == FLAG_MERGE) {
			Log.log("Writing APK!", Log.LOG_LEVEL_NORMAL);
			final DexPrinter dp = new DexPrinter();
			for (final SootClass c : Scene.v().getApplicationClasses()) {
				try {
					dp.add(c);
				} catch (final RuntimeException e) {
					Log.log("Class \"" + c + "\" could not be written to APK. Reason: Invalid code contained! ("
							+ e.getClass().getSimpleName() + ": " + e.getLocalizedMessage() + ")", Log.LOG_LEVEL_ERROR);
				}
			}
			dp.print();
		}

		Log.silence(false);
		Log.log("Soot: done!", Log.LOG_LEVEL_DEBUG);
	}

	public String getLauncherActivity() {
		return this.launcherActivity;
	}

	public void setLauncherActivity(String launcherActivity) {
		this.launcherActivity = launcherActivity;
	}
}