package de.upb.mike.amt.conflicresolution;

import java.util.Set;

import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import soot.SootClass;
import soot.util.Chain;

public class ConflictResolution {
	private static int counter = 0;

	private Chain<SootClass> sootClasses;

	public ConflictResolution(Chain<SootClass> initSootClasses) {
		this.sootClasses = initSootClasses;
	}

	public void resolveConflictOuter(SootClass sootClass, Set<SootClass> innerClasses) {
		// Resolve conflicts
		if (isConflicted(sootClass.getName())) {
			final String oldName = sootClass.getName();
			final String newName = makeUnique(sootClass.getName());
			resolveConflictInner(innerClasses, oldName, newName);
			rename(sootClass, oldName, newName, false);
		}

		// Add classes
		this.sootClasses.add(sootClass);
		for (final SootClass innerClass : innerClasses) {
			this.sootClasses.add(innerClass);
		}
	}

	private void resolveConflictInner(Set<SootClass> innerClasses, String outestClassOldName,
			String outestClassNewName) {
		for (final SootClass sootClass : innerClasses) {
			final String oldName = new String(sootClass.getName());
			final String newName = sootClass.getName().replace(outestClassOldName, outestClassNewName);
			rename(sootClass, oldName, newName, true);
		}
	}

	private void rename(SootClass sootClass, String oldName, String newName, boolean innerClass) {
		sootClass.rename(newName);
		Data.getInstance().getClassesChanged().put(Data.getInstance().getSootClassMap().get(sootClass), newName);
		Log.log("Class \"" + oldName + "\" renamed to \"" + newName + "\" (" + (innerClass ? "inner" : "outer")
				+ " class)", Log.LOG_LEVEL_DETAILED);
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
		for (final SootClass conflictClass : this.sootClasses) {
			final String conflictClassName = conflictClass.getName();
			if (sootClassName.equals(conflictClassName)) {
				return true;
			}
		}
		return false;
	}
}