package de.upb.mike.amt;

import java.io.File;
import java.util.List;

public class Config {
	private static Config instance = new Config();

	private String androidPlatformPath;
	private String sootOutputPath;
	private String apktoolJar;
	private File apktoolPathFile;
	private File aqlConfig;
	private File outputFolder;
	private String aqlQuery;
	private String comparisonAqlQuery;
	private List<File> appPathList;

	private Config() {
	}

	public static Config getInstance() {
		return instance;
	}

	public String getAndroidPlatformPath() {
		return this.androidPlatformPath;
	}

	public void setAndroidPlatformPath(String androidPlatformPath) {
		this.androidPlatformPath = androidPlatformPath;
	}

	public String getSootOutputPath() {
		return this.sootOutputPath;
	}

	public void setSootOutputPath(String sootOutputPath) {
		this.sootOutputPath = sootOutputPath;
	}

	public File getApktoolPath() {
		return this.apktoolPathFile;
	}

	public void setApktoolPath(String apktoolPath) {
		this.apktoolPathFile = new File(apktoolPath);
	}

	public String getApktoolJar() {
		return this.apktoolJar;
	}

	public void setApktoolJar(String apktoolJar) {
		this.apktoolJar = apktoolJar;
	}

	public List<File> getAppPathList() {
		return this.appPathList;
	}

	public void setAppPathList(List<File> appPathList) {
		this.appPathList = appPathList;
	}

	public File getAqlConfig() {
		return this.aqlConfig;
	}

	public void setAqlConfig(File aqlConfig) {
		this.aqlConfig = aqlConfig;
	}

	public void setOutputFolder(File outputFolder) {
		this.outputFolder = outputFolder;
	}

	public File getOutputFolder() {
		return this.outputFolder;
	}

	public String getAqlQuery() {
		return this.aqlQuery;
	}

	public void setAqlQuery(String aqlQuery) {
		this.aqlQuery = aqlQuery;
	}

	public String getComparisonAqlQuery() {
		return this.comparisonAqlQuery;
	}

	public void setComparisonAqlQuery(String comparisonAqlQuery) {
		this.comparisonAqlQuery = comparisonAqlQuery;
	}
}