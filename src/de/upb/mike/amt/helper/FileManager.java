package de.upb.mike.amt.helper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.io.Files;

import de.foellix.aql.helper.HashHelper;
import de.foellix.aql.helper.Helper;
import de.upb.mike.amt.Config;
import de.upb.mike.amt.Log;

public class FileManager {
	public static boolean deleteDir(String path) {
		return deleteDir(new File(path));
	}

	public static boolean deleteDir(File file) {
		if (!file.exists()) {
			return true;
		}
		if (file.isDirectory()) {
			for (final File temp : file.listFiles()) {
				if (!deleteDir(temp)) {
					Log.log("Failed to delete " + temp.getAbsolutePath(), Log.LOG_LEVEL_ERROR);
				}
			}
		}
		Log.log("Deleting directory/file: " + file.getAbsolutePath(), Log.LOG_LEVEL_VERBOSE);
		return file.delete();
	}

	public static boolean moveFile(String startPath, String endPath) {
		final File startFile = new File(startPath);
		File endFile = new File(endPath);
		if (endFile.isDirectory()) {
			endFile = new File(endPath, startFile.getName());
		}
		return moveFile(startFile, endFile);
	}

	public static boolean moveFile(File startFile, File endFile) {
		if (endFile.exists()) {
			endFile.delete();
		}
		if (!startFile.renameTo(endFile)) {
			Log.log("File could not be moved from " + startFile.getAbsolutePath() + " to " + endFile.getAbsolutePath(),
					Log.LOG_LEVEL_ERROR);
			return false;
		}
		Log.log("Moved file from " + startFile.getAbsolutePath() + " to " + endFile.getAbsolutePath(),
				Log.LOG_LEVEL_VERBOSE);
		return true;
	}

	public static void copyFile(String startPath, File endPath) {
		final File startFile = new File(startPath);
		final File endFile = new File(endPath, startFile.getName());
		copyFile(startFile, endFile);
	}

	public static void copyFile(File startFile, File endFile) {
		if (endFile.exists()) {
			endFile.delete();
		}
		try {
			Files.copy(startFile, endFile);
			Log.log("Copied file from " + startFile.getAbsolutePath() + " to " + endFile.getAbsolutePath(),
					Log.LOG_LEVEL_VERBOSE);
		} catch (final IOException e) {
			Log.log("File could not be copied from " + startFile.getAbsolutePath() + " to " + endFile.getAbsolutePath(),
					Log.LOG_LEVEL_ERROR);
		}
	}

	public static File moveToOutput() {
		final StringBuilder sb = new StringBuilder();
		for (final File file : Config.getInstance().getAppPathList()) {
			sb.append((sb.length() > 0 ? "_" : "") + file.getName().replace(".apk", ""));
		}
		final File targetFolder = new File(Config.getInstance().getOutputFolder(), HashHelper.sha1Hash(sb.toString()));
		if (deleteDir(targetFolder) && targetFolder.mkdirs()) {
			final File sootOutputFolder = new File(Config.getInstance().getSootOutputPath());
			for (final File file : sootOutputFolder.listFiles()) {
				if (file.getName().endsWith(".xml")) {
					file.renameTo(new File(targetFolder, file.getName()));
				} else if (file.getName().endsWith(".apk")) {
					final StringBuilder tempSB = new StringBuilder();
					for (final File tempFile : Config.getInstance().getAppPathList()) {
						tempSB.append(tempFile.getAbsolutePath() + ", ");
					}
					final File copy = new File(targetFolder.getParentFile(),
							Helper.getMultipleApkName(tempSB.toString()) + "_merged.apk");
					try {
						if (!copy.exists() || (copy.exists() && copy.delete())) {
							Files.copy(file, copy);
						} else {
							throw new IOException("Could not access file.");
						}
					} catch (final IOException e) {
						Log.log("Copying result to " + copy.getAbsolutePath() + " failed. ("
								+ e.getClass().getSimpleName() + ": " + e.getMessage() + ")", Log.LOG_LEVEL_WARNING);
					}
					file.renameTo(new File(targetFolder, "merged.apk"));
				}
			}
			deleteDir(Config.getInstance().getSootOutputPath());
		}
		return targetFolder;
	}

	public static List<File> findApksInFolder(File folder) {
		final List<File> apkFiles = new ArrayList<>();
		for (final File file : folder.listFiles()) {
			if (file.isDirectory()) {
				apkFiles.addAll(findApksInFolder(file));
			} else if (file.getName().endsWith(".apk")) {
				apkFiles.add(file);
			}
		}
		return apkFiles;
	}
}