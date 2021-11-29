package de.upb.mike.amt;

import java.io.FileInputStream;
import java.io.InputStream;

public class Properties {
	private static final String PROPERTIES_FILE = "amt.properties";

	public static final String ANDROID_PLATFORMS = "androidPlatforms";
	public static final String SOOT_OUTPUTPATH = "sootOutputPath";
	public static final String APKTOOLPATH = "apktoolPath";
	public static final String APKTOOLJAR = "apktoolJar";
	public static final String AQLQUERY = "aqlQuery";
	public static final String CPOMPARISON_AQLQUERY = "comparisonAqlQuery";
	public static final String OUTPUTFOLDER = "outputFolder";
	public static final String DEFAULT_EXCLUDES = "defaultExcludes";

	private static Properties i = new Properties();

	private java.util.Properties properties;

	private Properties() {
		this.properties = new java.util.Properties();
		try {
			final InputStream in = new FileInputStream(PROPERTIES_FILE);
			this.properties.load(in);
			in.close();
		} catch (final Exception e) {
			Log.log("Could not read properties file: " + PROPERTIES_FILE, Log.LOG_LEVEL_ERROR);
		}
	}

	public static Properties getInstance() {
		return i;
	}

	public String getProperty(String name) {
		return this.properties.getProperty(name);
	}
}