package de.upb.mike.amt.evaluation;

import java.io.File;

import de.foellix.aql.datastructure.Answer;
import de.foellix.aql.datastructure.handler.AnswerHandler;
import de.upb.mike.amt.Config;
import de.upb.mike.amt.Timer;
import de.upb.mike.amt.aql.AQLRunner;

public class Comparison {
	private File outputCombined;

	public void compare(StringBuilder sb, Answer unified, Answer analyzed, long mergedAnalysisTime) {
		this.outputCombined = new File(Config.getInstance().getSootOutputPath() + File.separator + "combined.xml");
		final Timer combinedAnalysisTimer = new Timer().start();
		final Answer combined = runAQL();
		combinedAnalysisTimer.stop();

		final int speedup = (100 - (int) Math.round((Double.valueOf(mergedAnalysisTime).doubleValue()
				/ Double.valueOf(combinedAnalysisTimer.getTime()).doubleValue()) * 100d));

		sb.append("\nApkCombiner compared to unified (Combining + Analysis time: "
				+ combinedAnalysisTimer.getTime(Timer.FORMAT_S) + "s):\n");
		evaluate(sb, unified, combined);
		sb.append("\nAMT compared to ApkCombiner (SpeedUp: " + speedup + "%):\n");
		evaluate(sb, combined, analyzed);
	}

	public Answer runAQL() {
		final StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (final File file : Config.getInstance().getAppPathList()) {
			if (!first) {
				sb.append(", ");
			} else {
				first = false;
			}
			sb.append(file.getAbsolutePath());
		}
		final String query = Config.getInstance().getComparisonAqlQuery().replaceAll("%APP_APK%",
				sb.toString().replaceAll("\\\\", "/"));
		final AQLRunner aqlObject = new AQLRunner(query);
		final Answer combined = aqlObject.parseApp();

		AnswerHandler.createXML(combined, this.outputCombined);
		return combined;
	}

	private void evaluate(StringBuilder sb, Answer answerHaystack, Answer answerNeedle) {
		if (answerHaystack == null && answerNeedle == null) {
			sb.append("Success (Both answers are null)\n");
		} else if ((answerHaystack != null && answerNeedle == null)
				|| (answerHaystack == null && answerNeedle != null)) {
			sb.append("Failed (One answer is null while the other one is not)\n");
		} else if ((answerHaystack.getFlows() == null && answerNeedle.getFlows() == null)
				|| ((answerHaystack.getFlows() == null || answerHaystack.getFlows().getFlow().isEmpty())
						&& (answerNeedle.getFlows() == null || answerNeedle.getFlows().getFlow().isEmpty()))) {
			sb.append("Success (Both answers are empty)\n");
		} else if ((answerHaystack.getFlows() == null
				&& (answerNeedle != null && !answerNeedle.getFlows().getFlow().isEmpty()))
				|| (answerNeedle.getFlows() == null
						&& (answerHaystack != null && !answerHaystack.getFlows().getFlow().isEmpty()))) {
			sb.append("Failed (One answer is empty while the other one is not)\n");
		} else if (answerHaystack.getFlows().getFlow().size() == answerNeedle.getFlows().getFlow().size()) {
			sb.append("Possible Success (Number of flows: " + answerNeedle.getFlows().getFlow().size() + " equal to "
					+ answerHaystack.getFlows().getFlow().size() + ")\n");
		} else {
			sb.append("Failed (Number of flows: " + answerNeedle.getFlows().getFlow().size() + " unequal to "
					+ answerHaystack.getFlows().getFlow().size() + ")\n");
		}
	}
}
