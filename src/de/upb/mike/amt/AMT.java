package de.upb.mike.amt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import de.foellix.aql.datastructure.Answer;
import de.foellix.aql.datastructure.Flow;
import de.foellix.aql.datastructure.Flows;
import de.foellix.aql.datastructure.handler.AnswerHandler;
import de.foellix.aql.helper.EqualsHelper;
import de.foellix.aql.helper.EqualsOptions;
import de.upb.mike.amt.aql.AQLHandler;
import de.upb.mike.amt.evaluation.Comparison;
import de.upb.mike.amt.helper.FileManager;
import de.upb.mike.amt.manifest.MergeManifest;
import de.upb.mike.amt.manifest.ParseManifest;
import de.upb.mike.amt.soot.SootObject;

public class AMT {
	private static boolean OPTION_CHECK = false;
	private static boolean OPTION_COMPARISON = false;

	private String launcherAppPath;
	private int appNumber;

	public static void main(String[] args) {
		// Launch parameters
		int counter = 0;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-l") || args[i].equals("-loglevel") || args[i].equals("-d")
					|| args[i].equals("-debug")) {
				if (args[i + 1].equals("error") || args[i + 1].equals(String.valueOf(Log.LOG_LEVEL_ERROR))) {
					Log.loglevel = Log.LOG_LEVEL_ERROR;
				} else if (args[i + 1].equals("warning") || args[i + 1].equals(String.valueOf(Log.LOG_LEVEL_WARNING))) {
					Log.loglevel = Log.LOG_LEVEL_WARNING;
				} else if (args[i + 1].equals("debug") || args[i + 1].equals(String.valueOf(Log.LOG_LEVEL_DEBUG))) {
					Log.loglevel = Log.LOG_LEVEL_DEBUG;
				} else if (args[i + 1].equals("detailed")
						|| args[i + 1].equals(String.valueOf(Log.LOG_LEVEL_DETAILED))) {
					Log.loglevel = Log.LOG_LEVEL_DETAILED;
				} else if (args[i + 1].equals("verbose") || args[i + 1].equals(String.valueOf(Log.LOG_LEVEL_VERBOSE))) {
					Log.loglevel = Log.LOG_LEVEL_VERBOSE;
				} else {
					Log.loglevel = Log.LOG_LEVEL_NORMAL;
				}
				counter += 2;
				i++;
			} else if (args[i].equals("-check")) {
				OPTION_CHECK = true;
				counter++;
			} else if (args[i].equals("-comparison")) {
				OPTION_CHECK = true;
				OPTION_COMPARISON = true;
				counter++;
			} else if (args[i].equals("-c") || args[i].equals("-cfg") || args[i].equals("-config")) {
				final File cfgFile = new File(args[i + 1]);
				if (cfgFile != null && cfgFile.exists()) {
					Config.getInstance().setAqlConfig(cfgFile);
				} else {
					Log.log("Could not find config file: " + cfgFile.getAbsolutePath(), Log.LOG_LEVEL_ERROR);
				}
				counter += 2;
				i++;
			}
		}
		final List<File> apkFiles = new ArrayList<>();
		for (int i = counter; i < args.length; i++) {
			String tempFilename = args[i];
			if (tempFilename.endsWith(" ")) {
				tempFilename = tempFilename.substring(0, tempFilename.length() - 1);
			}
			if (tempFilename.endsWith(",")) {
				tempFilename = tempFilename.substring(0, tempFilename.length() - 1);
			}
			for (final String tempFinalFilename : tempFilename.replaceAll(" ", "").split(",")) {
				final File temp = new File(tempFinalFilename);
				if (temp.exists()) {
					if (temp.isDirectory()) {
						for (final File temp2 : FileManager.findApksInFolder(temp)) {
							apkFiles.add(temp2);
						}
					} else {
						apkFiles.add(temp);
					}
				}
			}
		}
		Config.getInstance().setAppPathList(apkFiles);

		// Read properties file
		Config.getInstance().setAndroidPlatformPath(
				new File(Properties.i().getProperty(Properties.ANDROID_PLATFORMS)).getAbsolutePath());
		Config.getInstance().setSootOutputPath(new File("sootOutput").getAbsolutePath());
		Config.getInstance()
				.setApktoolPath(new File(Properties.i().getProperty(Properties.APKTOOLPATH)).getAbsolutePath());
		Config.getInstance().setApktoolJar(Properties.i().getProperty(Properties.APKTOOLJAR));
		Config.getInstance().setAqlQuery(Properties.i().getProperty(Properties.AQLQUERY));
		Config.getInstance().setComparisonAqlQuery(Properties.i().getProperty(Properties.CPOMPARISON_AQLQUERY));
		Config.getInstance().setOutputFolder(new File(Properties.i().getProperty(Properties.OUTPUTFOLDER)));

		// Launch AMT
		if (FileManager.deleteDir(Config.getInstance().getSootOutputPath())) {
			if (!apkFiles.isEmpty()) {
				new AMT().start();
			} else {
				Log.log("No input apk given. Please provide one.", Log.LOG_LEVEL_ERROR);
			}
		} else {
			Log.log("Could not delete Soot output folder: " + Config.getInstance().getSootOutputPath(),
					Log.LOG_LEVEL_ERROR);
		}
	}

	private void start() {
		int steps = 5;
		if (OPTION_COMPARISON) {
			steps = 7;
		} else if (OPTION_CHECK) {
			steps = 6;
		}

		final Timer amtTimer = new Timer().start();
		final Timer mergingTimer = new Timer().start();

		this.appNumber = Config.getInstance().getAppPathList().size();
		StringBuilder sb = new StringBuilder("AMT started! (Merging " + this.appNumber + " apps"
				+ (OPTION_CHECK ? " + Check" : "") + (OPTION_COMPARISON ? " + Comparison" : "") + ")\n");
		int inputCounter = 0;
		for (final File file : Config.getInstance().getAppPathList()) {
			sb.append("\t" + ++inputCounter + ". " + file.getAbsolutePath() + "\n");
		}
		sb.append("\n");
		Log.log(sb.toString(), Log.LOG_LEVEL_NORMAL);

		Log.log("*** Step 1/" + steps + ": Parsing Manifest ***", Log.LOG_LEVEL_NORMAL);
		for (final File file : Config.getInstance().getAppPathList()) {
			final ParseManifest parseManifest = new ParseManifest(file);
			Data.getInstance().getPackageMap().put(file.getAbsolutePath(), parseManifest.getPackageName());
			if (SootObject.getInstance().getLauncherActivity() == null && parseManifest.containsLauncherActivity()) {
				SootObject.getInstance().setLauncherActivity(parseManifest.getLauncherActivity());
				this.launcherAppPath = file.getAbsolutePath();
				Log.log("Launcher-Activity found: " + SootObject.getInstance().getLauncherActivity() + " ("
						+ this.launcherAppPath + ")", Log.LOG_LEVEL_DEBUG);
			}
		}
		Log.log("successful!\n\n", Log.LOG_LEVEL_NORMAL);

		Log.log("*** Step 2/" + steps + ": Parsing Apps ***", Log.LOG_LEVEL_NORMAL);
		for (final File apkFile : Config.getInstance().getAppPathList()) {
			Data.getInstance().putOrGetMapEntry(apkFile);
			if (!apkFile.getAbsolutePath().equals(this.launcherAppPath)) {
				SootObject.getInstance().parseApp(apkFile.getAbsolutePath(), SootObject.FLAG_READ_NON_LAUNCHER_APPS);
			}
		}
		// Data.getInstance().rebuildSootClass();
		Log.log("successful!\n\n", Log.LOG_LEVEL_NORMAL);

		Log.log("*** Step 3/" + steps + ": Instrumenting Classes ***", Log.LOG_LEVEL_NORMAL);
		for (final File apkFile : Config.getInstance().getAppPathList()) {
			if (apkFile.getAbsolutePath().equals(this.launcherAppPath)) {
				SootObject.getInstance().parseApp(apkFile.getAbsolutePath(), SootObject.FLAG_MERGE1);
			}
		}
		Log.log("successful!\n\n", Log.LOG_LEVEL_NORMAL);

		Log.log("*** Step 4/" + steps + ": Build Merged App ***", Log.LOG_LEVEL_NORMAL);
		SootObject.getInstance().parseApp(null, SootObject.FLAG_MERGE2);
		Log.log("successful!\n\n", Log.LOG_LEVEL_NORMAL);

		Log.log("*** Step 5/" + steps + ": Run Apktool (Merge Manifests, Copy Dex) ***", Log.LOG_LEVEL_NORMAL);
		final MergeManifest mergeManifest = new MergeManifest();
		mergeManifest.mergeManifest();
		Log.log("successful!\n\n", Log.LOG_LEVEL_NORMAL);
		mergingTimer.stop();

		if (OPTION_CHECK) {
			// CHECK
			Log.log("*** Step 6/" + steps + ": Merging Flows ***", Log.LOG_LEVEL_NORMAL);

			// Run both analysis
			final AQLHandler aqlHandler = new AQLHandler();
			final Timer singleAnalysisTimer = new Timer().start();
			final Answer unified = aqlHandler.runAQL1();
			singleAnalysisTimer.stop();
			final Timer mergedAnalysisTimer = new Timer().start();
			final Answer analyzed = aqlHandler.runAQL2();
			mergedAnalysisTimer.stop();
			Log.log("successful!\n\n", Log.LOG_LEVEL_NORMAL);

			// Output
			sb = new StringBuilder("Check-Result:\n");
			evaluate(sb, unified, analyzed);
			sb.append("Analysis time consumed to analyze all input apps:\n\tone by one: "
					+ singleAnalysisTimer.getTime(Timer.FORMAT_S) + "s\n\tmerged: "
					+ mergedAnalysisTimer.getTime(Timer.FORMAT_S) + "s");
			final int speedup = (100 - (int) Math.round((Double.valueOf(mergedAnalysisTimer.getTime()).doubleValue()
					/ Double.valueOf(singleAnalysisTimer.getTime()).doubleValue()) * 100d));
			sb.append(
					"\nSpeedUp: " + speedup + "%\n(Time for merging: " + mergingTimer.getTime(Timer.FORMAT_S) + "s)\n");

			// COMPARISON
			if (OPTION_COMPARISON) {
				Log.log("*** Step 7/" + steps + ": Comparing to ApkCombiner ***", Log.LOG_LEVEL_NORMAL);

				final Comparison comparison = new Comparison();
				comparison.compare(sb, unified, analyzed, mergingTimer.getTime() + mergedAnalysisTimer.getTime());
				Log.log("successful!\n\n", Log.LOG_LEVEL_NORMAL);
			}

			Log.log(sb.toString(), Log.LOG_LEVEL_NORMAL);
		}

		final File outputFolder = FileManager.moveToOutput();
		Log.log("Stored in: " + outputFolder.getAbsolutePath() + "\n\n", Log.LOG_LEVEL_NORMAL);
		amtTimer.stop();
		Log.log("AMT finished! (" + amtTimer.getTime(Timer.FORMAT_S) + "s)", Log.LOG_LEVEL_NORMAL);

		try (PrintWriter out = new PrintWriter(new File(outputFolder, "log.txt"))) {
			out.println(Log.getLog());
		} catch (final FileNotFoundException e) {
			Log.log("Could not write logfile. (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")",
					Log.LOG_LEVEL_ERROR);
		}
	}

	private void evaluate(StringBuilder sb, Answer answerHaystack, Answer answerNeedle) {
		final EqualsOptions options = EqualsOptions.DEFAULT.setOption(EqualsOptions.IGNORE_APP, true);
		if (EqualsHelper.equals(answerHaystack, answerNeedle, options)) {
			sb.append("Success (Answers are equal!)\n");
		} else {
			final Answer missingFlows = new Answer();
			missingFlows.setFlows(new Flows());
			final Answer neverFoundFlows = new Answer();
			neverFoundFlows.setFlows(new Flows());
			neverFoundFlows.getFlows().getFlow().addAll(answerNeedle.getFlows().getFlow());
			boolean contained = true;
			if (answerHaystack.getFlows() != null && !answerHaystack.getFlows().getFlow().isEmpty()) {
				for (final Flow needle : answerHaystack.getFlows().getFlow()) {
					boolean found = false;
					if (answerNeedle.getFlows() != null && !answerNeedle.getFlows().getFlow().isEmpty()) {
						for (final Flow candidate : answerNeedle.getFlows().getFlow()) {
							if (EqualsHelper.equals(needle, candidate, options)) {
								found = true;
								neverFoundFlows.getFlows().getFlow().remove(candidate);
								break;
							}
						}
					}
					if (!found) {
						contained = false;
						missingFlows.getFlows().getFlow().add(needle);
					}
				}
				if (!missingFlows.getFlows().getFlow().isEmpty()) {
					final File outputMissing = new File(
							Config.getInstance().getSootOutputPath() + File.separator + "check_missing.xml");
					AnswerHandler.createXML(missingFlows, outputMissing);
				}
				if (!neverFoundFlows.getFlows().getFlow().isEmpty()) {
					final File outputNeverFound = new File(
							Config.getInstance().getSootOutputPath() + File.separator + "check_new.xml");
					AnswerHandler.createXML(neverFoundFlows, outputNeverFound);
				}
			}
			if (contained) {
				sb.append("Partial-Success (Answer is contained!)\n");
			} else {
				sb.append("Failed (" + missingFlows.getFlows().getFlow().size() + " of "
						+ answerHaystack.getFlows().getFlow().size() + " flows missing!)\n");
			}
		}
	}
}
