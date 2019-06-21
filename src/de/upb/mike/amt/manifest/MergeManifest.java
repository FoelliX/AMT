package de.upb.mike.amt.manifest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.upb.mike.amt.Config;
import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import de.upb.mike.amt.SootObjHashed;
import de.upb.mike.amt.helper.FileManager;

public class MergeManifest {
	private static final String ANDROID_NAME = "android:name";

	private String apktoolPath;
	private String apktoolJar;
	private String launcherApp;

	public MergeManifest(String apktoolPath, String apktoolJar) {
		this.apktoolPath = apktoolPath;
		this.apktoolJar = apktoolJar;
		this.launcherApp = Config.getInstance().getAppPathList().get(0).getName();
	}

	public void mergeManifest() {
		// Copy apk
		FileManager.copyFile(Config.getInstance().getAppPathList().get(0).getAbsolutePath(),
				Config.getInstance().getApktoolPath());

		// Modify Manifest
		modifyManifest();

		// Copy back
		FileManager
				.moveFile(
						Config.getInstance().getApktoolPath() + "/"
								+ launcherApp.substring(0, launcherApp.lastIndexOf(".")) + "/dist/" + launcherApp,
						Config.getInstance().getSootOutputPath() + "/merged.apk");

		// Delete temporary files and folders of ApkTool
		FileManager.deleteDir(
				Config.getInstance().getApktoolPath() + "/" + launcherApp.substring(0, launcherApp.lastIndexOf(".")));
		FileManager.deleteDir(Config.getInstance().getApktoolPath() + "/" + launcherApp);
	}

	private void modifyManifest() {
		executeCmdCommand("java -jar " + apktoolJar + " d " + launcherApp, apktoolPath);

		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			File manifestFile = new File(Config.getInstance().getApktoolPath() + "/"
					+ launcherApp.substring(0, launcherApp.lastIndexOf(".")) + "/AndroidManifest.xml");
			Document doc = dBuilder.parse(manifestFile);
			Node manifestNode = doc.getElementsByTagName("manifest").item(0);
			Node applicationNode = doc.getElementsByTagName("application").item(0);
			NodeList permissionNodes = doc.getElementsByTagName("uses-permission");
			List<String> usesPermissions = new ArrayList<>();
			for (int i = 0; i < permissionNodes.getLength(); i++) {
				if (permissionNodes.item(i).hasAttributes()
						&& permissionNodes.item(i).getAttributes().getNamedItem(ANDROID_NAME) != null) {
					usesPermissions
							.add(permissionNodes.item(i).getAttributes().getNamedItem(ANDROID_NAME).getNodeValue());
				}
			}
			NodeList activityNodes = doc.getElementsByTagName("activity");

			for (Document inputDoc : Data.getInstance().getDocMap().keySet()) {
				if (!Data.getInstance().getDocMap().get(inputDoc).getAbsolutePath()
						.equals(Config.getInstance().getAppPathList().get(0).getAbsolutePath())) {
					// Add permissions
					permissionNodes = inputDoc.getElementsByTagName("uses-permission");
					for (int i = 0; i < permissionNodes.getLength(); i++) {
						if (permissionNodes.item(i).hasAttributes()
								&& permissionNodes.item(i).getAttributes().getNamedItem(ANDROID_NAME) != null) {
							String permissionStr = permissionNodes.item(i).getAttributes().getNamedItem(ANDROID_NAME)
									.getNodeValue();
							if (!usesPermissions.contains(permissionStr)) {
								usesPermissions.add(permissionStr);
								manifestNode.insertBefore(doc.importNode(permissionNodes.item(i), true),
										manifestNode.getFirstChild());
								Log.log("Permission added to manifest: " + permissionStr, Log.LOG_LEVEL_DETAILED);
							}
						}
					}
					// Add activities
					activityNodes = inputDoc.getElementsByTagName("activity");
					for (int i = 0; i < activityNodes.getLength(); i++) {
						if (activityNodes.item(i).hasAttributes()
								&& activityNodes.item(i).getAttributes().getNamedItem(ANDROID_NAME) != null) {
							String activityStr = activityNodes.item(i).getAttributes().getNamedItem(ANDROID_NAME)
									.getNodeValue();
							if (activityStr.startsWith(".")) {
								activityStr = Data.getInstance().getPackageMap().get(
										Data.getInstance().getDocMap().get(inputDoc).getAbsolutePath()) + activityStr;
							}
							SootObjHashed asc = new SootObjHashed(activityStr,
									Data.getInstance().getHashMap().get(Data.getInstance().getDocMap().get(inputDoc)));
							if (Data.getInstance().getClassesChanged().containsKey(asc)) {
								activityStr = Data.getInstance().getClassesChanged().get(asc);
							}

							Node importNode = doc.importNode(activityNodes.item(i), true);
							importNode.getAttributes().getNamedItem(ANDROID_NAME).setNodeValue(activityStr);
							applicationNode.appendChild(importNode);

							// Delete intent-filter, if launcher
							NodeList intentFilters = importNode.getChildNodes();
							for (int j = 0; j < intentFilters.getLength(); j++) {
								NodeList children = intentFilters.item(j).getChildNodes();
								boolean actionFound = false;
								boolean categoryFound = false;
								for (int k = 0; k < children.getLength(); k++) {
									Node element = children.item(k);
									if (element.hasAttributes()) {
										Node attr = element.getAttributes().getNamedItem(ANDROID_NAME);
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
									importNode.removeChild(intentFilters.item(j));
									j--;
								}
							}

							Log.log("Activity added to manifest: " + activityStr, Log.LOG_LEVEL_DETAILED);
						}
					}
					// Add everything else
					NodeList other = inputDoc.getElementsByTagName("application").item(0).getChildNodes();
					for (int i = 0; i < other.getLength(); i++) {
						if (!other.item(i).getNodeName().equals("activity")
								&& !other.item(i).getNodeName().equals("#text")) {

							Node importedNode = doc.importNode(other.item(i), true);
							if (importedNode.hasAttributes()
									&& importedNode.getAttributes().getNamedItem(ANDROID_NAME) != null) {
								String nodeNameStr = importedNode.getAttributes().getNamedItem(ANDROID_NAME)
										.getNodeValue();
								if (nodeNameStr.startsWith(".")) {
									nodeNameStr = Data.getInstance().getPackageMap()
											.get(Data.getInstance().getDocMap().get(inputDoc).getAbsolutePath())
											+ nodeNameStr;
								}
								importedNode.getAttributes().getNamedItem(ANDROID_NAME).setNodeValue(nodeNameStr);
							}
							applicationNode.appendChild(importedNode);

							Log.log(importedNode.getNodeName().substring(0, 1).toUpperCase()
									+ importedNode.getNodeName().substring(1) + " added to manifest"
									+ (importedNode.hasAttributes()
											&& importedNode.getAttributes().getNamedItem(ANDROID_NAME) != null
													? ": " + importedNode.getAttributes().getNamedItem(ANDROID_NAME)
															.getNodeValue()
													: "."),
									Log.LOG_LEVEL_DETAILED);
						}
					}
				}
			}

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.transform(new DOMSource(doc), new StreamResult(manifestFile));
		} catch (Exception e) {
			Log.log("Could not merge manifests! (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")",
					Log.LOG_LEVEL_ERROR);
			if (Log.loglevel >= Log.LOG_LEVEL_DEBUG) {
				e.printStackTrace();
			}
		}

		// Replace classes
		File dexFile = new File(Config.getInstance().getSootOutputPath()
				+ (Config.getInstance().getSootOutputPath().endsWith("/") ? "" : "/") + "classes.dex");
		File moveTo = new File(apktoolPath, launcherApp.substring(0, launcherApp.lastIndexOf(".")) + "/classes.dex");
		FileManager.moveFile(dexFile, moveTo);

		// build modified apk
		executeCmdCommand("java -jar " + apktoolJar + " b " + launcherApp.substring(0, launcherApp.lastIndexOf(".")),
				apktoolPath);
	}

	private void executeCmdCommand(String command, String workDir) {
		try {
			List<String> commands = new ArrayList<String>();
			commands.add("cmd.exe");
			commands.add("/c");
			commands.add(command);
			ProcessBuilder pb = new ProcessBuilder(commands);
			pb.directory(new File(apktoolPath + "/"));
			Log.log("Executing Apktool: " + commands, Log.LOG_LEVEL_DEBUG);
			Process process = pb.start();
			process.waitFor();

			InputStream in = process.getInputStream();

			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String line = br.readLine();
			while (line != null) {
				Log.log(line, Log.LOG_LEVEL_DETAILED);
				line = br.readLine();
			}
		} catch (IOException | InterruptedException e) {
			Log.log("Error while running \"" + command + "\" in \"" + workDir + "\". (" + e.getClass().getSimpleName()
					+ ": " + e.getMessage() + ")", Log.LOG_LEVEL_ERROR);
			if (Log.loglevel >= Log.LOG_LEVEL_DEBUG) {
				e.printStackTrace();
			}
		}
	}
}
