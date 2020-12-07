package de.upb.mike.amt.soot;

import java.io.File;
import java.util.Iterator;
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
		for (final Iterator<SootClass> iterator = Scene.v().getApplicationClasses().iterator(); iterator.hasNext();) {
			final SootClass sc = iterator.next();
			if (!sc.isPhantom()) {
				Data.getInstance().receiveClass(sc, this.appFile);
			} else {
				Log.log("Ignoring class: " + sc.toString(), Log.LOG_LEVEL_DETAILED);
			}
		}
	}
}
