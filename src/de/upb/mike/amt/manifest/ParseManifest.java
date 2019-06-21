package de.upb.mike.amt.manifest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import net.dongliu.apk.parser.ApkParsers;

public class ParseManifest {
	private File appFile;

	private String packageName;
	private String launcherActivity;
	private boolean containsLauncherActivity;

	public ParseManifest(File appFile) {
		this.appFile = appFile;
		containsLauncherActivity = false;
		parse();
	}

	private void parse() {
		String manifestAsString = null;
		try {
			manifestAsString = ApkParsers.getManifestXml(appFile);
			Log.log("Manifest (" + appFile.getAbsolutePath() + ")\n" + manifestAsString, Log.LOG_LEVEL_VERBOSE);
		} catch (IOException e) {
			Log.log("Could not read manifest of " + appFile.getAbsolutePath() + " (" + e.getClass().getSimpleName()
					+ ": " + e.getMessage() + ")", Log.LOG_LEVEL_ERROR);
		}

		try {
			ByteArrayInputStream input = new ByteArrayInputStream(manifestAsString.getBytes("UTF-8"));
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(input);
			Data.getInstance().getDocMap().put(doc, appFile);

			// Package
			packageName = doc.getElementsByTagName("manifest").item(0).getAttributes().getNamedItem("package")
					.getNodeValue();
			Log.log("Package identified: " + packageName, Log.LOG_LEVEL_DETAILED);

			// Launcher Activity
			NodeList intentFilters = doc.getElementsByTagName("intent-filter");
			for (int i = 0; i < intentFilters.getLength(); i++) {
				Node intentFilter = intentFilters.item(i);
				NodeList children = intentFilter.getChildNodes();

				boolean actionFound = false;
				boolean categoryFound = false;
				for (int j = 0; j < children.getLength(); j++) {
					Node element = children.item(j);
					if (element.hasAttributes()) {
						Node attr = element.getAttributes().getNamedItem("android:name");
						if (attr != null) {
							String name = attr.getNodeValue();
							if (element.getNodeName().equals("action")) {
								if (name.equals("android.intent.action.MAIN")) {
									actionFound = true;
								}
							} else if (element.getNodeName().equals("category")) {
								if (name.equals("android.intent.category.LAUNCHER")) {
									categoryFound = true;
								}
							}
							if (actionFound && categoryFound) {
								break;
							}
						}
					}
				}

				if (actionFound && categoryFound) {
					launcherActivity = intentFilter.getParentNode().getAttributes().getNamedItem("android:name")
							.getNodeValue();
					if (launcherActivity.startsWith(".")) {
						launcherActivity = packageName + launcherActivity;
					}
					containsLauncherActivity = true;
					Log.log("Launcher Activity found: " + launcherActivity, Log.LOG_LEVEL_DETAILED);

					break;
				}
			}
		} catch (Exception e) {
			Log.log("Could not parse manifest of " + appFile.getAbsolutePath() + " (" + e.getClass().getSimpleName()
					+ ": " + e.getMessage() + ")", Log.LOG_LEVEL_ERROR);
			e.printStackTrace();
		}
	}

	public String getPackageName() {
		return packageName;
	}

	public String getLauncherActivity() {
		return launcherActivity;
	}

	public boolean containsLauncherActivity() {
		return containsLauncherActivity;
	}
}