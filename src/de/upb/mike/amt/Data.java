package de.upb.mike.amt;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Document;

import de.foellix.aql.datastructure.Hash;
import de.foellix.aql.datastructure.KeywordsAndConstants;
import de.foellix.aql.helper.HashHelper;
import soot.SootClass;

public class Data {
	private static Data instance = new Data();

	private Map<SootClass, SootObjHashed> sootClassMap = new ConcurrentHashMap<>();
	private Map<Document, File> docMap = new HashMap<>();
	private Map<String, String> lifecycleMethodSignaturesChanged = new ConcurrentHashMap<>();
	private Map<SootObjHashed, String> classesChanged = new ConcurrentHashMap<>();
	private Map<File, Hash> hashMap = new ConcurrentHashMap<>();
	private Map<String, String> packageMap = new HashMap<>();

	private Data() {
	}

	public static Data getInstance() {
		return instance;
	}

	public void receiveClass(SootClass sc, File appFile) {
		if (!this.sootClassMap.containsKey(sc) && !sc.isPhantom()) {
			this.sootClassMap.put(sc, new SootObjHashed(sc.getName(), putOrGetMapEntry(appFile)));
		}
	}

	public Hash putOrGetMapEntry(File appFile) {
		Hash appHash;
		if (this.hashMap.containsKey(appFile)) {
			appHash = this.hashMap.get(appFile);
		} else {
			appHash = new Hash();
			appHash.setType(KeywordsAndConstants.HASH_TYPE_SHA1);
			appHash.setValue(HashHelper.sha1Hash(appFile));
			this.hashMap.put(appFile, appHash);
		}
		return appHash;
	}

	public Map<SootClass, SootObjHashed> getSootClassMap() {
		return this.sootClassMap;
	}

	public Map<Document, File> getDocMap() {
		return this.docMap;
	}

	public Map<String, String> getLifecycleMethodSignaturesChanged() {
		return this.lifecycleMethodSignaturesChanged;
	}

	public Map<SootObjHashed, String> getClassesChanged() {
		return this.classesChanged;
	}

	public Map<String, String> getPackageMap() {
		return this.packageMap;
	}

	public Map<File, Hash> getHashMap() {
		return this.hashMap;
	}
}