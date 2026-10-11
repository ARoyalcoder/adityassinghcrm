package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.lead.api.LeadFacts;
import com.pawanputra.bos.lead.api.LeadScore;
import com.pawanputra.bos.lead.api.LeadScore.Category;
import com.pawanputra.bos.lead.api.LeadScore.Factor;
import com.pawanputra.bos.lead.api.LeadScorer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The deterministic scorer: a fixed list of rules, each of which looks at the facts and may add one
 * reason with its points. The same facts and settings always give the same score, and the score is
 * exactly the sum of the reasons (limited to 0..100), so it can always be explained.
 *
 * <p>No model, no randomness, no clock: "now" is part of the facts.
 */
@Component
public class RuleBasedLeadScorer implements LeadScorer {

    public static final String ENGINE = "rules-v1";

    /** One rule: looks at the facts and adds at most a few reasons. */
    @FunctionalInterface
    interface Rule {
        void apply(LeadFacts facts, LeadScoringProperties settings, List<Factor> reasons);
    }

    /** The rules, in the order their reasons are listed. Adding a rule is adding a line here. */
    private static final List<Rule> RULES = List.of(
            RuleBasedLeadScorer::estimatedValue,
            RuleBasedLeadScorer::qualificationAnswers,
            RuleBasedLeadScorer::activity,
            RuleBasedLeadScorer::contactDetails);

    private final LeadScoringProperties settings;

    public RuleBasedLeadScorer(LeadScoringProperties settings) {
        this.settings = settings;
    }

    @Override
    public String engine() {
        return ENGINE;
    }

    @Override
    public LeadScore score(LeadFacts facts) {
        List<Factor> reasons = new ArrayList<>();
        for (Rule rule : RULES) {
            rule.apply(facts, settings, reasons);
        }
        // A reason worth nothing explains nothing.
        reasons.removeIf(reason -> reason.points() == 0);
        return LeadScore.of(reasons, ENGINE);
    }

    // ------------------------------------------------------------------ the rules

    private static void estimatedValue(LeadFacts facts, LeadScoringProperties settings, List<Factor> reasons) {
        BigDecimal value = facts.estimatedValue();
        if (value == null) {
            return;
        }
        if (value.compareTo(settings.highValueAmount()) >= 0) {
            reasons.add(new Factor("HIGH_VALUE", Category.VALUE,
                    "High estimated value (" + rupees(settings.highValueAmount()) + " or more)",
                    settings.highValuePoints()));
        } else if (value.compareTo(settings.mediumValueAmount()) >= 0) {
            reasons.add(new Factor("MEDIUM_VALUE", Category.VALUE,
                    "Medium estimated value (" + rupees(settings.mediumValueAmount()) + " or more)",
                    settings.mediumValuePoints()));
        }
    }

    /** Each answer is worth what the administrator gave its option. */
    private static void qualificationAnswers(LeadFacts facts, LeadScoringProperties settings, List<Factor> reasons) {
        for (LeadFacts.Answer answer : facts.answers()) {
            reasons.add(new Factor("QUESTION:" + answer.questionKey(), Category.QUALIFICATION,
                    answer.questionLabel() + ": " + answer.optionLabel(), answer.points()));
        }
    }

    private static void activity(LeadFacts facts, LeadScoringProperties settings, List<Factor> reasons) {
        Instant lastActivity = facts.lastActivityAt();
        // With nothing logged yet, the silence is counted from when the lead arrived.
        long silentDays = daysBetween(lastActivity != null ? lastActivity : facts.createdAt(), facts.asOf());

        // "Within two days" means two days at most, to the minute: 2 days and 23 hours is not recent.
        boolean recent = lastActivity != null && !Duration.between(lastActivity, facts.asOf())
                .minus(Duration.ofDays(settings.recentActivityDays())).isPositive();

        if (recent) {
            reasons.add(new Factor("RECENT_ACTIVITY", Category.ENGAGEMENT,
                    "Recent activity (" + ago(silentDays) + ")", settings.recentActivityPoints()));
        } else if (silentDays >= settings.inactiveAfterDays()) {
            boolean cold = silentDays >= settings.coldAfterDays();
            String label = lastActivity != null
                    ? "No activity for " + days(silentDays)
                    : "No activity since the lead was created " + days(silentDays) + " ago";
            reasons.add(new Factor(cold ? "GONE_COLD" : "NO_ACTIVITY", Category.ENGAGEMENT, label,
                    cold ? settings.coldPoints() : settings.inactivePoints()));
        }
    }

    private static void contactDetails(LeadFacts facts, LeadScoringProperties settings, List<Factor> reasons) {
        if (facts.hasPhone() && facts.hasEmail()) {
            reasons.add(new Factor("COMPLETE_CONTACT", Category.PROFILE, "Phone number and email address known",
                    settings.completeContactPoints()));
        }
    }

    // ------------------------------------------------------------------ wording

    /** Whole days between two moments; never negative. */
    static long daysBetween(Instant from, Instant to) {
        return Math.max(0, Duration.between(from, to).toDays());
    }

    static String days(long count) {
        return count == 1 ? "1 day" : count + " days";
    }

    private static String ago(long days) {
        return days == 0 ? "today" : days == 1 ? "yesterday" : days + " days ago";
    }

    /** 500000 as ₹5,00,000: Indian digit grouping (the last three digits, then pairs). */
    static String rupees(BigDecimal amount) {
        BigDecimal plain = amount.stripTrailingZeros();
        String text = (plain.scale() < 0 ? plain.setScale(0) : plain).toPlainString();
        int point = text.indexOf('.');
        String whole = point < 0 ? text : text.substring(0, point);
        StringBuilder grouped = new StringBuilder(whole.length() > 3 ? whole.substring(whole.length() - 3) : whole);
        for (int end = whole.length() - 3; end > 0; end -= 2) {
            grouped.insert(0, whole.substring(Math.max(0, end - 2), end) + ",");
        }
        return "₹" + grouped + (point < 0 ? "" : text.substring(point));
    }
}
