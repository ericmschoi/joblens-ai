package com.joblens.analysis.model;

import java.util.List;

/** What to prepare, drawn from the resume and the posting rather than invented. */
public record InterviewPreparation(
        List<LikelyQuestion> likelyQuestions,
        List<String> talkingPoints,
        List<GapToExplain> gapsToExplain,
        List<String> questionsToAsk) {

    public record LikelyQuestion(String question, String whyAsked, List<String> evidenceToUse) {
        public LikelyQuestion {
            evidenceToUse = List.copyOf(evidenceToUse);
        }
    }

    public record GapToExplain(String gap, String suggestedFraming) {}

    public InterviewPreparation {
        likelyQuestions = List.copyOf(likelyQuestions);
        talkingPoints = List.copyOf(talkingPoints);
        gapsToExplain = List.copyOf(gapsToExplain);
        questionsToAsk = List.copyOf(questionsToAsk);
    }
}
