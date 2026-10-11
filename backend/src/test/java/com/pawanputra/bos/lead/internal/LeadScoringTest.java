package com.pawanputra.bos.lead.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawanputra.bos.lead.api.LeadFacts;
import com.pawanputra.bos.lead.api.LeadFacts.Answer;
import com.pawanputra.bos.lead.api.LeadHealth;
import com.pawanputra.bos.lead.api.LeadPriority;
import com.pawanputra.bos.lead.api.LeadScore;
import com.pawanputra.bos.lead.api.LeadScore.Category;
import com.pawanputra.bos.lead.api.LeadScore.Factor;
import com.pawanputra.bos.lead.api.LeadScorer;
import com.pawanputra.bos.lead.api.LeadStatus;
import com.pawanputra.bos.lead.api.QualificationStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The deterministic rules: what a lead scores, and that the score is exactly its explanation. */
class LeadScoringTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    /** The defaults of application.yml. */
    private static final LeadScoringProperties SETTINGS = new LeadScoringProperties(
            "rules-v1", new BigDecimal("500000"), 20, new BigDecimal("100000"), 10, 2, 10, 3, -10, 7, -20, 5, 60, 30);

    private static final RuleBasedLeadScorer SCORER = new RuleBasedLeadScorer(SETTINGS);

    private static final Answer DECISION_MAKER =
            new Answer("decision_maker", "Decision maker identified", "YES", "Yes", 15, false);
    private static final Answer NO_BUDGET = new Answer("budget", "Budget", "NONE", "No budget", -15, true);

    /** A lead builder: every test states only what matters to it. */
    private static final class LeadBuilder {
        private BigDecimal value;
        private boolean phone = true;
        private boolean email;
        private Instant createdAt = NOW;
        private Instant lastActivityAt;
        private List<Answer> answers = List.of();
        private LeadStatus status = LeadStatus.OPEN;
        private Instant asOf = NOW;

        LeadBuilder value(String amount) {
            this.value = new BigDecimal(amount);
            return this;
        }

        LeadBuilder createdDaysAgo(long days) {
            this.createdAt = NOW.minus(Duration.ofDays(days));
            return this;
        }

        LeadBuilder lastActivityDaysAgo(long days) {
            this.lastActivityAt = NOW.minus(Duration.ofDays(days));
            return this;
        }

        LeadBuilder lastActivityHoursAgo(long hours) {
            this.lastActivityAt = NOW.minus(Duration.ofHours(hours));
            return this;
        }

        LeadBuilder answers(Answer... given) {
            this.answers = List.of(given);
            return this;
        }

        LeadBuilder phoneAndEmail() {
            this.phone = true;
            this.email = true;
            return this;
        }

        LeadBuilder closedDaysAgo(long days) {
            this.status = LeadStatus.WON;
            this.asOf = NOW.minus(Duration.ofDays(days));
            return this;
        }

        LeadFacts facts() {
            return new LeadFacts(UUID.randomUUID(), UUID.randomUUID(), status, value, phone, email, createdAt,
                    lastActivityAt, lastActivityAt == null ? 0 : 1, answers, asOf);
        }
    }

    private static LeadBuilder lead() {
        return new LeadBuilder();
    }

    private static List<String> reasons(LeadScore score) {
        return score.factors().stream()
                .map(factor -> (factor.points() > 0 ? "+" : "") + factor.points() + " " + factor.label()).toList();
    }

    private static LeadAssessment assess(LeadBuilder lead, int required, int requiredAnswered) {
        LeadFacts facts = lead.facts();
        return LeadAssessment.of(facts, SCORER.score(facts), required, requiredAnswered, SETTINGS);
    }

    // ------------------------------------------------------------------ the score and its explanation

    @Test
    void theExampleFromTheBrief() {
        // High value, the decision maker is known, and someone spoke to them yesterday.
        LeadScore hot = SCORER.score(lead().value("750000").answers(DECISION_MAKER).lastActivityDaysAgo(1).facts());

        assertThat(reasons(hot)).containsExactly(
                "+20 High estimated value (₹5,00,000 or more)",
                "+15 Decision maker identified: Yes",
                "+10 Recent activity (yesterday)");
        assertThat(hot.score()).isEqualTo(45);

        // The same lead, left alone for three days.
        LeadScore cooling = SCORER.score(lead().value("750000").answers(DECISION_MAKER).lastActivityDaysAgo(3).facts());

        assertThat(reasons(cooling)).containsExactly(
                "+20 High estimated value (₹5,00,000 or more)",
                "+15 Decision maker identified: Yes",
                "-10 No activity for 3 days");
        assertThat(cooling.score()).isEqualTo(25);
    }

    @Test
    void theScoreIsAlwaysExactlyTheSumOfItsReasons() {
        List<LeadBuilder> leads = List.of(
                lead(),
                lead().value("120000").phoneAndEmail(),
                lead().value("900000").answers(DECISION_MAKER).lastActivityHoursAgo(3).phoneAndEmail(),
                lead().answers(NO_BUDGET).createdDaysAgo(20),
                lead().value("99999.99").createdDaysAgo(5).lastActivityDaysAgo(4));
        for (LeadBuilder lead : leads) {
            LeadScore score = SCORER.score(lead.facts());
            int sum = score.factors().stream().mapToInt(Factor::points).sum();
            assertThat(score.total()).isEqualTo(sum);
            assertThat(score.score()).isEqualTo(Math.max(0, Math.min(100, sum)));
            assertThat(score.factors()).allSatisfy(factor -> {
                assertThat(factor.points()).isNotZero();
                assertThat(factor.label()).isNotBlank();
                assertThat(factor.code()).isNotBlank();
            });
            assertThat(score.engine()).isEqualTo("rules-v1");
        }
    }

    @Test
    void theSameFactsAlwaysGiveTheSameScore() {
        LeadFacts facts = lead().value("600000").answers(DECISION_MAKER).lastActivityDaysAgo(1).phoneAndEmail().facts();

        assertThat(SCORER.score(facts)).isEqualTo(SCORER.score(facts));
        assertThat(new RuleBasedLeadScorer(SETTINGS).score(facts)).isEqualTo(SCORER.score(facts));
    }

    @Test
    void aLeadNothingIsKnownAboutScoresNothingAndSaysNothing() {
        LeadScore score = SCORER.score(lead().facts());

        assertThat(score.score()).isZero();
        assertThat(score.factors()).isEmpty();
    }

    // ------------------------------------------------------------------ each rule

    @Test
    void estimatedValueHasTwoBands() {
        assertThat(reasons(SCORER.score(lead().value("500000").facts())))
                .containsExactly("+20 High estimated value (₹5,00,000 or more)");
        assertThat(reasons(SCORER.score(lead().value("499999.99").facts())))
                .containsExactly("+10 Medium estimated value (₹1,00,000 or more)");
        assertThat(reasons(SCORER.score(lead().value("100000").facts())))
                .containsExactly("+10 Medium estimated value (₹1,00,000 or more)");
        assertThat(SCORER.score(lead().value("99999.99").facts()).factors()).isEmpty();
        assertThat(SCORER.score(lead().facts()).factors()).isEmpty(); // not estimated yet
        assertThat(SCORER.score(lead().value("750000").facts()).factors().getFirst())
                .isEqualTo(new Factor("HIGH_VALUE", Category.VALUE, "High estimated value (₹5,00,000 or more)", 20));
    }

    @Test
    void anAnswerIsWorthWhatItsOptionSays() {
        Answer timeline = new Answer("timeline", "When do they want it", "THIS_MONTH", "This month", 20, false);
        Answer undecided = new Answer("authority", "Who decides", "UNKNOWN", "Not known yet", 0, false);

        LeadScore score = SCORER.score(lead().answers(DECISION_MAKER, timeline, NO_BUDGET, undecided).facts());

        assertThat(reasons(score)).containsExactly(
                "+15 Decision maker identified: Yes",
                "+20 When do they want it: This month",
                "-15 Budget: No budget"); // an answer worth nothing is not listed
        assertThat(score.factors()).extracting(Factor::code)
                .containsExactly("QUESTION:decision_maker", "QUESTION:timeline", "QUESTION:budget");
        assertThat(score.factors()).extracting(Factor::category).containsOnly(Category.QUALIFICATION);
        assertThat(score.score()).isEqualTo(20);
    }

    @Test
    void recentActivityCountsOnlyIfSomethingWasActuallyLogged() {
        assertThat(reasons(SCORER.score(lead().lastActivityHoursAgo(2).facts()))).containsExactly("+10 Recent activity (today)");
        assertThat(reasons(SCORER.score(lead().lastActivityDaysAgo(1).facts()))).containsExactly("+10 Recent activity (yesterday)");
        assertThat(reasons(SCORER.score(lead().lastActivityDaysAgo(2).facts()))).containsExactly("+10 Recent activity (2 days ago)");
        // A lead that arrived a minute ago has had no activity: that is not "recent activity".
        assertThat(SCORER.score(lead().facts()).factors()).isEmpty();
    }

    @Test
    void silenceCountsAgainstTheLeadFromThreeDaysAndMoreFromSeven() {
        // Still inside the three days: neither for nor against.
        assertThat(SCORER.score(lead().createdDaysAgo(10).lastActivityHoursAgo(71).facts()).factors()).isEmpty();

        assertThat(reasons(SCORER.score(lead().createdDaysAgo(10).lastActivityDaysAgo(3).facts())))
                .containsExactly("-10 No activity for 3 days");
        assertThat(reasons(SCORER.score(lead().createdDaysAgo(10).lastActivityDaysAgo(6).facts())))
                .containsExactly("-10 No activity for 6 days");
        LeadScore cold = SCORER.score(lead().createdDaysAgo(30).lastActivityDaysAgo(7).facts());
        assertThat(reasons(cold)).containsExactly("-20 No activity for 7 days");
        assertThat(cold.factors().getFirst().code()).isEqualTo("GONE_COLD");
    }

    @Test
    void aLeadNobodyEverTouchedIsJudgedFromWhenItArrived() {
        assertThat(SCORER.score(lead().createdDaysAgo(2).facts()).factors()).isEmpty();
        assertThat(reasons(SCORER.score(lead().createdDaysAgo(4).facts())))
                .containsExactly("-10 No activity since the lead was created 4 days ago");
        assertThat(reasons(SCORER.score(lead().createdDaysAgo(9).facts())))
                .containsExactly("-20 No activity since the lead was created 9 days ago");
    }

    @Test
    void amountsAreWrittenTheIndianWay() {
        assertThat(RuleBasedLeadScorer.rupees(new BigDecimal("500"))).isEqualTo("₹500");
        assertThat(RuleBasedLeadScorer.rupees(new BigDecimal("5000"))).isEqualTo("₹5,000");
        assertThat(RuleBasedLeadScorer.rupees(new BigDecimal("100000"))).isEqualTo("₹1,00,000");
        assertThat(RuleBasedLeadScorer.rupees(new BigDecimal("500000.00"))).isEqualTo("₹5,00,000");
        assertThat(RuleBasedLeadScorer.rupees(new BigDecimal("12345678.5"))).isEqualTo("₹1,23,45,678.5");
    }

    @Test
    void knowingBothPhoneAndEmailIsWorthALittle() {
        assertThat(reasons(SCORER.score(lead().phoneAndEmail().facts())))
                .containsExactly("+5 Phone number and email address known");
        assertThat(SCORER.score(lead().facts()).factors()).isEmpty(); // phone only
    }

    @Test
    void theScoreStaysBetweenZeroAndAHundredButTheTotalIsKept() {
        Answer huge = new Answer("fit", "Fit", "PERFECT", "Perfect", 100, false);
        LeadScore top = SCORER.score(lead().value("900000").answers(huge, DECISION_MAKER).lastActivityDaysAgo(0).facts());
        assertThat(top.total()).isEqualTo(145);
        assertThat(top.score()).isEqualTo(100);

        LeadScore bottom = SCORER.score(lead().answers(NO_BUDGET).createdDaysAgo(30).facts());
        assertThat(bottom.total()).isEqualTo(-35);
        assertThat(bottom.score()).isZero();
        assertThat(reasons(bottom)).containsExactly(
                "-15 Budget: No budget", "-20 No activity since the lead was created 30 days ago");
    }

    @Test
    void aClosedLeadIsJudgedAsItStoodWhenItClosed() {
        // Last touched 21 days ago, won 20 days ago: when it closed, the activity was a day old.
        LeadScore score = SCORER.score(lead().createdDaysAgo(40).lastActivityDaysAgo(21).closedDaysAgo(20).facts());

        assertThat(reasons(score)).containsExactly("+10 Recent activity (yesterday)");
    }

    @Test
    void thePointsAndThresholdsComeFromSettings() {
        LeadScoringProperties generous = new LeadScoringProperties(
                "rules-v1", new BigDecimal("50000"), 40, new BigDecimal("10000"), 25, 1, 30, 2, -5, 4, -50, 15, 80, 50);
        RuleBasedLeadScorer scorer = new RuleBasedLeadScorer(generous);

        assertThat(reasons(scorer.score(lead().value("60000").phoneAndEmail().lastActivityDaysAgo(1).facts())))
                .containsExactly("+40 High estimated value (₹50,000 or more)", "+30 Recent activity (yesterday)",
                        "+15 Phone number and email address known");
        assertThat(reasons(scorer.score(lead().createdDaysAgo(9).lastActivityDaysAgo(2).facts())))
                .containsExactly("-5 No activity for 2 days");
        assertThat(reasons(scorer.score(lead().createdDaysAgo(9).lastActivityDaysAgo(4).facts())))
                .containsExactly("-50 No activity for 4 days");
    }

    @Test
    void settingsThatContradictThemselvesAreRejected() {
        assertThatThrownBy(() -> new LeadScoringProperties(
                "rules-v1", new BigDecimal("100"), 20, new BigDecimal("100"), 10, 2, 10, 3, -10, 7, -20, 5, 60, 30))
                .hasMessageContaining("medium-value-amount");
        assertThatThrownBy(() -> new LeadScoringProperties(
                "rules-v1", new BigDecimal("500"), 20, new BigDecimal("100"), 10, 2, 10, 7, -10, 7, -20, 5, 60, 30))
                .hasMessageContaining("cold-after-days");
        assertThatThrownBy(() -> new LeadScoringProperties(
                "rules-v1", new BigDecimal("500"), 20, new BigDecimal("100"), 10, 2, 10, 3, -10, 7, -20, 5, 30, 30))
                .hasMessageContaining("medium-priority-score");
    }

    // ------------------------------------------------------------------ priority

    @Test
    void priorityFollowsTheScoreBands() {
        Answer worth = new Answer("fit", "Fit", "GOOD", "Good", 40, false);

        LeadAssessment high = assess(lead().value("600000").answers(worth), 0, 0);
        assertThat(high.score().score()).isEqualTo(60);
        assertThat(high.priority()).isEqualTo(LeadPriority.HIGH);
        assertThat(high.priorityReason()).isEqualTo("Score 60 is 60 or more");

        LeadAssessment medium = assess(lead().value("600000").answers(DECISION_MAKER), 0, 0);
        assertThat(medium.score().score()).isEqualTo(35);
        assertThat(medium.priority()).isEqualTo(LeadPriority.MEDIUM);
        assertThat(medium.priorityReason()).isEqualTo("Score 35 is between 30 and 59");

        LeadAssessment low = assess(lead().value("600000").phoneAndEmail(), 0, 0);
        assertThat(low.score().score()).isEqualTo(25);
        assertThat(low.priority()).isEqualTo(LeadPriority.LOW);
        assertThat(low.priorityReason()).isEqualTo("Score 25 is below 30");
    }

    @Test
    void aDisqualifiedLeadIsLowPriorityWhateverItScores() {
        Answer worth = new Answer("fit", "Fit", "GOOD", "Good", 80, false);
        Answer outOfArea = new Answer("area", "Service area", "OUTSIDE", "Outside our area", 0, true);

        LeadAssessment assessment = assess(lead().value("900000").answers(worth, outOfArea), 0, 0);

        assertThat(assessment.score().score()).isEqualTo(100);
        assertThat(assessment.priority()).isEqualTo(LeadPriority.LOW);
        assertThat(assessment.priorityReason()).isEqualTo("The lead is disqualified");
    }

    // ------------------------------------------------------------------ health

    @Test
    void healthIsAboutHowLongTheLeadHasBeenLeftAlone() {
        assertThat(assess(lead(), 0, 0)).satisfies(a -> {
            assertThat(a.health()).isEqualTo(LeadHealth.GOOD);
            assertThat(a.healthReason()).isEqualTo("The lead is new");
        });
        assertThat(assess(lead().createdDaysAgo(20).lastActivityDaysAgo(1), 0, 0)).satisfies(a -> {
            assertThat(a.health()).isEqualTo(LeadHealth.GOOD);
            assertThat(a.healthReason()).isEqualTo("Last activity was yesterday");
        });
        assertThat(assess(lead().createdDaysAgo(20).lastActivityDaysAgo(3), 0, 0)).satisfies(a -> {
            assertThat(a.health()).isEqualTo(LeadHealth.NEEDS_ATTENTION);
            assertThat(a.healthReason()).isEqualTo("No activity for 3 days (3 or more needs a follow-up)");
        });
        assertThat(assess(lead().createdDaysAgo(20).lastActivityDaysAgo(7), 0, 0)).satisfies(a -> {
            assertThat(a.health()).isEqualTo(LeadHealth.AT_RISK);
            assertThat(a.healthReason()).isEqualTo("No activity for 7 days (7 or more puts a lead at risk)");
        });
        assertThat(assess(lead().createdDaysAgo(5), 0, 0)).satisfies(a -> {
            assertThat(a.health()).isEqualTo(LeadHealth.NEEDS_ATTENTION);
            assertThat(a.healthReason()).startsWith("No activity since the lead was created 5 days ago");
        });
    }

    @Test
    void aValuableLeadCanStillBeAtRisk() {
        Answer worth = new Answer("fit", "Fit", "GOOD", "Good", 70, false);

        LeadAssessment assessment = assess(lead().value("900000").answers(worth).createdDaysAgo(30).lastActivityDaysAgo(10), 0, 0);

        assertThat(assessment.score().score()).isEqualTo(70); // 20 + 70 - 20
        assertThat(assessment.priority()).isEqualTo(LeadPriority.HIGH);
        assertThat(assessment.health()).isEqualTo(LeadHealth.AT_RISK); // the two say different things, on purpose
    }

    // ------------------------------------------------------------------ qualification status

    @Test
    void qualificationStatusFollowsTheQuestions() {
        assertThat(assess(lead(), 2, 0)).satisfies(a -> {
            assertThat(a.qualificationStatus()).isEqualTo(QualificationStatus.NOT_STARTED);
            assertThat(a.qualificationReason()).isEqualTo("No qualification question has been answered yet");
        });
        assertThat(assess(lead().answers(DECISION_MAKER), 2, 1)).satisfies(a -> {
            assertThat(a.qualificationStatus()).isEqualTo(QualificationStatus.IN_PROGRESS);
            assertThat(a.qualificationReason()).isEqualTo("1 of 2 required questions answered");
        });
        assertThat(assess(lead().answers(DECISION_MAKER), 1, 1)).satisfies(a -> {
            assertThat(a.qualificationStatus()).isEqualTo(QualificationStatus.QUALIFIED);
            assertThat(a.qualificationReason()).isEqualTo("All 1 required questions answered, and no answer rules the lead out");
        });
        // Only optional questions exist: any answer that does not rule the lead out qualifies it.
        assertThat(assess(lead().answers(DECISION_MAKER), 0, 0).qualificationStatus()).isEqualTo(QualificationStatus.QUALIFIED);
    }

    @Test
    void oneDisqualifyingAnswerRulesTheLeadOutAndSaysWhich() {
        LeadAssessment assessment = assess(lead().answers(DECISION_MAKER, NO_BUDGET), 2, 2);

        assertThat(assessment.qualificationStatus()).isEqualTo(QualificationStatus.DISQUALIFIED);
        assertThat(assessment.qualificationReason()).isEqualTo("Ruled out by the answer to \"Budget\": No budget");
    }

    @Test
    void qualificationIsNotAboutTheScore() {
        // Every required question answered well, but an old, small, neglected lead: qualified, low priority.
        LeadAssessment assessment = assess(lead().answers(DECISION_MAKER).createdDaysAgo(30), 1, 1);

        assertThat(assessment.qualificationStatus()).isEqualTo(QualificationStatus.QUALIFIED);
        assertThat(assessment.score().score()).isZero(); // 15 - 20
        assertThat(assessment.priority()).isEqualTo(LeadPriority.LOW);
        assertThat(assessment.health()).isEqualTo(LeadHealth.AT_RISK);
    }

    // ------------------------------------------------------------------ the seam for other scorers

    @Test
    void anyScorerCanSupplyTheScoreAndTheRestStillFollows() {
        // Stands in for a future model-backed scorer: it must explain itself in the same terms.
        LeadScorer model = new LeadScorer() {
            @Override
            public String engine() {
                return "model-test";
            }

            @Override
            public LeadScore score(LeadFacts facts) {
                return LeadScore.of(List.of(new Factor("SIMILAR_WON", Category.PROFILE, "Similar leads were won", 72)), engine());
            }
        };
        LeadFacts facts = lead().createdDaysAgo(4).facts();

        LeadAssessment assessment = LeadAssessment.of(facts, model.score(facts), 0, 0, SETTINGS);

        assertThat(assessment.score().engine()).isEqualTo("model-test");
        assertThat(assessment.score().score()).isEqualTo(72);
        assertThat(assessment.priority()).isEqualTo(LeadPriority.HIGH);
        assertThat(assessment.health()).isEqualTo(LeadHealth.NEEDS_ATTENTION);
    }
}
