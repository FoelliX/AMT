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
		this.containsLauncherActivity = false;
		parse();
	}

	private void parse() {
		String manifestAsString = null;
		try {
			manifestAsString = ApkParsers.getManifestXml(this.appFile);
			Log.log("Manifest (" + this.appFile.getAbsolutePath() + ")\n" + manifestAsString, Log.LOG_LEVEL_VERBOSE);
		} catch (final IOException e) {
			Log.log("Could not read manifest of " + this.appFile.getAbsolutePath() + " (" + e.getClass().getSimpleName()
					+ ": " + e.getMessage() + ")", Log.LOG_LEVEL_ERROR);
		}

		try {
			final ByteArrayInputStream input = new ByteArrayInputStream(manifestAsString.getBytes("UTF-8"));
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder builder = factory.newDocumentBuilder();
			final Document doc = builder.parse(input);
			Data.getInstance().getDocMap().put(doc, this.appFile);

			// Package
			this.packageName = doc.getElementsByTagName("manifest").item(0).getAttributes().getNamedItem("package")
					.getNodeValue();
			Log.log("Package identified: " + this.packageName, Log.LOG_LEVEL_DETAILED);

			// Launcher Activity
			final NodeList intentFilters = doc.getElementsByTagName("intent-filter");
			for (int i = 0; i < intentFilters.getLength(); i++) {
				final Node intentFilter = intentFilters.item(i);
				final NodeList children = intentFilter.getChildNodes();

				boolean actionFound = false;
				boolean categoryFound = false;
				for (int j = 0; j < children.getLength(); j++) {
					final Node element = children.item(j);
					if (element.hasAttributes()) {
						final Node attr = element.getAttributes().getNamedItem("android:name");
						if (attr != null) {
							final String name = attr.getNodeValue();
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
					if (intentFilter.getParentNode().getNodeName().equals("activity-alias")) {
						this.launcherActivity = intentFilter.getParentNode().getAttributes()
								.getNamedItem("android:targetActivity").getNodeValue();
					} else {
						this.launcherActivity = intentFilter.getParentNode().getAttributes()
								.getNamedItem("android:name").getNodeValue();
					}
					if (this.launcherActivity.startsWith(".")) {
						this.launcherActivity = this.packageName + this.launcherActivity;
					}
					this.containsLauncherActivity = true;
					Log.log("Launcher-Activity found: " + this.launcherActivity + " (" + this.appFile.getAbsolutePath()
							+ ")", Log.LOG_LEVEL_DETAILED);

					break;
				}
			}
		} catch (final Exception e) {
			Log.log("Could not parse manifest of " + this.appFile.getAbsolutePath() + " ("
					+ e.getClass().getSimpleName() + ": " + e.getMessage() + ")", Log.LOG_LEVEL_ERROR);
			e.printStackTrace();
		}
	}

	public String getPackageName() {
		return this.packageName;
	}

	public String getLauncherActivity() {
		return this.launcherActivity;
	}

	public boolean containsLauncherActivity() {
		return this.containsLauncherActivity;
	}
}