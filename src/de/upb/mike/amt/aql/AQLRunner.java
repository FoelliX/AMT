package de.upb.mike.amt.aql;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.foellix.aql.datastructure.Answer;
import de.foellix.aql.datastructure.Flow;
import de.foellix.aql.datastructure.Flows;
import de.foellix.aql.datastructure.Reference;
import de.foellix.aql.datastructure.handler.AnswerHandler;
import de.foellix.aql.helper.EqualsHelper;
import de.foellix.aql.helper.Helper;
import de.foellix.aql.helper.KeywordsAndConstantsHelper;
import de.foellix.aql.system.AQLSystem;
import de.foellix.aql.system.Options;
import de.foellix.aql.system.defaulttools.operators.DefaultUnifyOperator;
import de.upb.mike.amt.Config;
import de.upb.mike.amt.Log;

public class AQLRunner {
	private AQLSystem aqlSystem;
	private String query;

	public AQLRunner(String query) {
		this.query = query;
		this.aqlSystem = new AQLSystem(new Options().setConfig(Config.getInstance().getAqlConfig()));
	}

	public Answer parseApp() {
		Log.log("Issuing AQL-Query: \"" + this.query + "\"", Log.LOG_LEVEL_DEBUG);
		Log.silence(true);
		final Collection<Object> answerList = this.aqlSystem.queryAndWait(this.query);
		Log.silence(false);

		if (answerList != null && answerList.iterator().hasNext()) {
			return (Answer) answerList.iterator().next();
		} else {
			return new Answer();
		}
	}

	public static Answer mergeFlows(List<Answer> resultList, File outputFile, boolean overapproximate) {
		// Apply unify operator
		Answer mergedAnswer = new Answer();
		for (final Answer answer : resultList) {
			mergedAnswer = new DefaultUnifyOperator().unify(mergedAnswer, answer);
		}
		if (mergedAnswer.getFlows() == null) {
			mergedAnswer.setFlows(new Flows());
		}

		// Over-approximate
		if (overapproximate) {
			final Collection<Flow> flowsToAdd = new ArrayList<>();
			for (final Flow flow : mergedAnswer.getFlows().getFlow()) {
				final Reference from = Helper.getFrom(flow.getReference());
				final Reference toExists = Helper.getTo(flow.getReference());
				for (final Reference to : Helper.getAllReferences(mergedAnswer)) {
					if (to.getType().equals(KeywordsAndConstantsHelper.REFERENCE_TYPE_TO)
							&& !EqualsHelper.equals(toExists, to)) {
						final Flow flowToAdd = new Flow();
						flowToAdd.getReference().add(from);
						flowToAdd.getReference().add(to);
						flowsToAdd.add(flowToAdd);
					}
				}
			}
			mergedAnswer.getFlows().getFlow().addAll(flowsToAdd);
		}

		// Write to file
		AnswerHandler.createXML(mergedAnswer, outputFile);

		// Return merged answer
		return mergedAnswer;
	}
}
