package de.upb.mike.amt.aql;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.foellix.aql.datastructure.Answer;
import de.foellix.aql.datastructure.Hash;
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
		this.aqlResultList = new ArrayList<>();

		if (Log.loglevel < Log.LOG_LEVEL_DETAILED) {
			de.foellix.aql.Log.setLogLevel(de.foellix.aql.Log.NONE);
		} else if (Log.loglevel == Log.LOG_LEVEL_DETAILED) {
			de.foellix.aql.Log.setShorten(true);
		} else if (Log.loglevel == Log.LOG_LEVEL_VERBOSE) {
			de.foellix.aql.Log.setLogLevel(de.foellix.aql.Log.DEBUG_DETAILED);
		}

		this.outputUnified = new File(Config.getInstance().getSootOutputPath() + File.separator + "unified.xml");
		this.outputOverapproximated = new File(
				Config.getInstance().getSootOutputPath() + File.separator + "overapproximated.xml");
		this.outputAnalyzed = new File(Config.getInstance().getSootOutputPath() + File.separator + "analyzed.xml");
	}

	public Answer runAQL1() {
		for (final File apkFile : Config.getInstance().getAppPathList()) {
			final String query = new String(Config.getInstance().getAqlQuery()).replaceAll("%APP_APK%",
					apkFile.getAbsolutePath().replaceAll("\\\\", "/"));
			final AQLRunner aqlObject = new AQLRunner(query);
			this.aqlResultList.add(resolveChanges(aqlObject.parseApp(), apkFile));
		}

		AQLRunner.mergeFlows(this.aqlResultList, this.outputOverapproximated, true);
		return AQLRunner.mergeFlows(this.aqlResultList, this.outputUnified, false);
	}

	public Answer runAQL2() {
		final File mergedApk = new File(Config.getInstance().getSootOutputPath() + "/merged.apk");
		if (mergedApk.exists()) {
			final String query = new String(Config.getInstance().getAqlQuery()).replaceAll("%APP_APK%",
					mergedApk.getAbsolutePath().replaceAll("\\\\", "/"));
			final AQLRunner aqlObject = new AQLRunner(query);
			final Answer analyzed = aqlObject.parseApp();

			AnswerHandler.createXML(analyzed, this.outputAnalyzed);
			return analyzed;
		}
		return new Answer();
	}

	private Answer resolveChanges(Answer answer, File appFile) {
		final Hash appHash = new Hash();
		appHash.setType(HashHelper.HASH_TYPE_SHA1);
		appHash.setValue(HashHelper.sha1Hash(appFile));

		for (final Reference ref : Helper.getAllReferences(answer, true)) {
			final SootObjHashed appSootClass = new SootObjHashed(ref.getClassname(), appHash);
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