package de.upb.mike.amt.soot;

import java.io.File;
import java.util.Map;

import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;

public class ParseTransformer extends SceneTransformer {
	private File appFile;

	public ParseTransformer(String appFile) {
		this.appFile = new File(appFile);
	}

	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			if (!sc.isPhantom()) {
				Data.getInstance().receiveClass(sc, appFile);
			} else {
				Log.log("Ignoring class: " + sc.toString(), Log.LOG_LEVEL_DETAILED);
			}
		}
	}
}
