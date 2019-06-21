package de.upb.mike.amt.conflicresolution;

import java.util.HashMap;
import java.util.Map;

import de.foellix.aql.datastructure.Hash;
import de.upb.mike.amt.SootObjHashed;
import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import soot.RefType;
import soot.SootClass;
import soot.util.Chain;

public class ConflictResolution {
	private static int counter = 0;

	private Chain<SootClass> sootClasses;

	private Map<SootObjHashed, String> outerClassMap;

	public ConflictResolution(Chain<SootClass> initSootClasses) {
		this.sootClasses = initSootClasses;
		this.outerClassMap = new HashMap<>();
	}

	public void resolveConflict(SootClass sootClass, Hash currentAppHash) {
		String originalName = sootClass.getName();

		// Probably rename outer class
		String rename = null;
		if (sootClass.hasOuterClass()) {
			String outerClassOriginalName = outerClassMap
					.get(new SootObjHashed(sootClass.getOuterClass().getName(), currentAppHash));
			rename = Data.getInstance().getClassesChanged()
					.get(new SootObjHashed(outerClassOriginalName, currentAppHash));
			if (rename != null) {
				sootClass.setName(sootClass.getName().replace(outerClassOriginalName, rename));
				sootClass.setRefType(RefType.v(sootClass.getName()));
			}
		}

		// Resolve conflict (if existent)
		if (isConflicted(sootClass.getName())) {
			sootClass.setName(makeUnique(sootClass.getName()));
			sootClass.setRefType(RefType.v(sootClass.getName()));
			Data.getInstance().getClassesChanged().put(new SootObjHashed(originalName, currentAppHash),
					sootClass.getName());
			outerClassMap.put(new SootObjHashed(sootClass.getName(), currentAppHash), originalName);
			Log.log("Class " + originalName + " renamed to " + sootClass.getName() + " (Innerclass mode)",
					Log.LOG_LEVEL_DETAILED);
		} else if (rename != null) {
			Data.getInstance().getClassesChanged().put(new SootObjHashed(originalName, currentAppHash),
					sootClass.getName());
			Log.log("Class " + originalName + " renamed to " + sootClass.getName() + " (Outerclass mode)",
					Log.LOG_LEVEL_DETAILED);
		}
		sootClasses.add(sootClass);
	}

	private String makeUnique(String name) {
		String nameUnique = name;
		do {
			counter++;
			nameUnique = name + "_" + counter;
		} while (isConflicted(nameUnique));
		return nameUnique;
	}

	private boolean isConflicted(String sootClassName) {
		for (SootClass conflictClass : sootClasses) {
			String conflictClassName = conflictClass.getName();
			if (sootClassName.equals(conflictClassName)) {
				return true;
			}
		}
		return false;
	}
}