package de.upb.mike.amt.aql;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.foellix.aql.datastructure.Answer;
import de.foellix.aql.datastructure.Hash;
import de.foellix.aql.datastructure.KeywordsAndConstants;
import de.foellix.aql.datastructure.Reference;
import de.foellix.aql.datastructure.handler.AnswerHandler;
import de.foellix.aql.helper.HashHelper;
import de.foellix.aql.helper.Helper;
import de.upb.mike.amt.Config;
import de.upb.mike.amt.Data;
import de.upb.mike.amt.Log;
import de.upb.mike.amt.SootObjHashed;

public class AQLHandler {
	private List<Answer> aqlResultList;
	private File outputUnified, outputOverapproximated, outputAnalyzed;

	public AQLHandler() {
		aqlResultList = new ArrayList<Answer>();

		if (Log.loglevel < Log.LOG_LEVEL_DETAILED) {
			de.foellix.aql.Log.setLogLevel(de.foellix.aql.Log.NONE);
		} else if (Log.loglevel == Log.LOG_LEVEL_DETAILED) {
			de.foellix.aql.Log.setShorten(true);
		} else if (Log.loglevel == Log.LOG_LEVEL_VERBOSE) {
			de.foellix.aql.Log.setLogLevel(de.foellix.aql.Log.DEBUG_DETAILED);
		}

		outputUnified = new File(Config.getInstance().getSootOutputPath() + File.separator + "unified.xml");
		outputOverapproximated = new File(
				Config.getInstance().getSootOutputPath() + File.separator + "overapproximated.xml");
		outputAnalyzed = new File(Config.getInstance().getSootOutputPath() + File.separator + "analyzed.xml");
	}

	public Answer runAQL1() {
		for (File apkFile : Config.getInstance().getAppPathList()) {
			String query = new String(Config.getInstance().getAqlQuery()).replaceAll("%APP_APK%",
					apkFile.getAbsolutePath().replaceAll("\\\\", "/"));
			AQLRunner aqlObject = new AQLRunner(query);
			aqlResultList.add(resolveChanges(aqlObject.parseApp(), apkFile));
		}

		AQLRunner.mergeFlows(aqlResultList, outputOverapproximated, true);
		return AQLRunner.mergeFlows(aqlResultList, outputUnified, false);
	}

	public Answer runAQL2() {
		File mergedApk = new File(Config.getInstance().getSootOutputPath() + "/merged.apk");
		if (mergedApk.exists()) {
			String query = new String(Config.getInstance().getAqlQuery()).replaceAll("%APP_APK%",
					mergedApk.getAbsolutePath().replaceAll("\\\\", "/"));
			AQLRunner aqlObject = new AQLRunner(query);
			Answer analyzed = aqlObject.parseApp();

			AnswerHandler.createXML(analyzed, outputAnalyzed);
			return analyzed;
		}
		return new Answer();
	}

	private Answer resolveChanges(Answer answer, File appFile) {
		Hash appHash = new Hash();
		appHash.setType(KeywordsAndConstants.HASH_TYPE_SHA1);
		appHash.setValue(HashHelper.sha1Hash(appFile));

		for (Reference ref : Helper.getAllReferences(answer, true)) {
			SootObjHashed appSootClass = new SootObjHashed(ref.getClassname(), appHash);
			if (Data.getInstance().getClassesChanged().containsKey(appSootClass)) {
				ref.setMethod(ref.getMethod().replace(ref.getClassname(),
						Data.getInstance().getClassesChanged().get(appSootClass)));
				ref.setClassname(Data.getInstance().getClassesChanged().get(appSootClass));
			}
			if (Data.getInstance().getLifecycleMethodSignaturesChanged().containsKey(ref.getMethod())) {
				ref.setMethod(Data.getInstance().getLifecycleMethodSignaturesChanged().get(ref.getMethod()));
			}
		}
		return answer;
	}
}