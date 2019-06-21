package de.upb.mike.amt;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;

import de.foellix.aql.datastructure.Hash;
import de.foellix.aql.datastructure.KeywordsAndConstants;
import de.foellix.aql.helper.HashHelper;
import soot.SootClass;

public class Data {
	private static Data instance = new Data();

	private Map<SootClass, Hash> sootClassMap = new HashMap<>();
	private Map<Document, File> docMap = new HashMap<>();
	private Map<String, String> lifecycleMethodSignaturesChanged = new HashMap<>();
	private Map<SootObjHashed, String> classesChanged = new HashMap<>();
	private Map<File, Hash> hashMap = new HashMap<>();
	private Map<String, String> packageMap = new HashMap<>();

	private Data() {
	}

	public static Data getInstance() {
		return instance;
	}

	public void receiveClass(SootClass sc, File appFile) {
		if (!sootClassMap.containsKey(sc) && !sc.isPhantom()) {
			sootClassMap.put(sc, putOrGetMapEntry(appFile));
		}
	}

	public Hash putOrGetMapEntry(File appFile) {
		Hash appHash;
		if (hashMap.containsKey(appFile)) {
			appHash = hashMap.get(appFile);
		} else {
			appHash = new Hash();
			appHash.setType(KeywordsAndConstants.HASH_TYPE_SHA1);
			appHash.setValue(HashHelper.sha1Hash(appFile));
			hashMap.put(appFile, appHash);
		}
		return appHash;
	}

	public Map<SootClass, Hash> getSootClassMap() {
		return sootClassMap;
	}

	public Map<Document, File> getDocMap() {
		return docMap;
	}

	public Map<String, String> getLifecycleMethodSignaturesChanged() {
		return lifecycleMethodSignaturesChanged;
	}

	public Map<SootObjHashed, String> getClassesChanged() {
		return classesChanged;
	}

	public Map<String, String> getPackageMap() {
		return packageMap;
	}

	public Map<File, Hash> getHashMap() {
		return hashMap;
	}
}