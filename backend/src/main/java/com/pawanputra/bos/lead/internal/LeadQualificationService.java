package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.lead.api.LeadFacts;
import com.pawanputra.bos.lead.api.LeadScore;
import com.pawanputra.bos.lead.api.LeadScorer;
import com.pawanputra.bos.lead.api.LeadStatus;
import com.pawanputra.bos.lead.internal.QuestionOptions.Option;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.FieldValidationException.Violation;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Qualifying a lead: gathering the facts about it, having the scorer score them, deriving
 * priority, health and qualification status, and keeping the stored assessment up to date.
 *
 * <p>The scorer is chosen once, at start-up, by {@code app.leads.scoring.engine}. Nothing else in
 * this class knows or cares which scorer it is.
 */
@Service
@Transactional(readOnly = true)
@EnableConfigurationProperties(LeadScoringProperties.class)
public class LeadQualificationService {

    private static final int RESCORE_BATCH = 200;

    private final LeadRepository leads;
    private final LeadActivityRepository activities;
    private final QualificationQuestionRepository questions;
    private final LeadQualificationAnswerRepository answers;
    private final QuestionOptions options;
    private final LeadScoringProperties settings;
    private final LeadScorer scorer;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LeadQualificationService(
            LeadRepository leads,
            LeadActivityRepository activities,
            QualificationQuestionRepository questions,
            LeadQualificationAnswerRepository answers,
            QuestionOptions options,
            LeadScoringProperties settings,
            List<LeadScorer> scorers,
            JdbcTemplate jdbc,
            Clock clock) {
        this.leads = leads;
        this.activities = activities;
        this.questions = questions;
        this.answers = answers;
        this.options = options;
        this.settings = settings;
        this.scorer = scorers.stream().filter(candidate -> candidate.engine().equals(settings.engine())).findFirst()
                .orElseThrow(() -> new IllegalStateException("app.leads.scoring.engine is '" + settings.engine()
                        + "', but the available scorers are "
                        + scorers.stream().map(LeadScorer::engine).toList()));
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** A question as someone answering it sees it: no points, so the answer is not steered by them. */
    public record QuestionView(
            UUID id, String key, String label, String helpText, boolean required, List<OptionView> options,
            String answer) {
    }

    public record OptionView(String value, String label) {
    }

    /** A lead's assessment together with the questions and the answers given so far. */
    public record Evaluation(LeadAssessment assessment, List<QuestionView> questions) {
    }

    public record HistoryEntry(
            int score, String priority, String health, String qualificationStatus, String engine, Instant computedAt) {
    }

    // ------------------------------------------------------------------ reading

    /** The lead as it stands right now, with every reason. Nothing is stored. */
    public Evaluation evaluation(UUID organizationId, UUID leadId) {
        Lead lead = load(organizationId, leadId);
        Gathered gathered = gather(lead);
        return new Evaluation(assess(gathered), gathered.views());
    }

    public List<HistoryEntry> history(UUID organizationId, UUID leadId) {
        load(organizationId, leadId);
        return jdbc.query(
                "SELECT score, priority, health, qualification_status, engine, computed_at FROM lead_score_history "
                        + "WHERE lead_id = ? ORDER BY computed_at DESC, id DESC LIMIT 50",
                (row, index) -> new HistoryEntry(row.getInt(1), row.getString(2), row.getString(3), row.getString(4),
                        row.getString(5), row.getTimestamp(6).toInstant()),
                leadId);
    }

    // ------------------------------------------------------------------ writing

    /**
     * Records the lead's answers and reassesses it.
     *
     * @param given question key to the chosen option's value; {@code null} or blank clears an answer.
     *              Questions that are not mentioned keep their answers
     */
    @Transactional
    public Evaluation answer(UUID organizationId, UUID leadId, Map<String, String> given) {
        Lead lead = load(organizationId, leadId);
        List<QualificationQuestion> active =
                questions.findAllByOrganizationIdAndActiveTrueOrderByDisplayOrderAscLabelAsc(organizationId);
        Map<String, QualificationQuestion> byKey = new HashMap<>();
        active.forEach(question -> byKey.put(question.getQuestionKey(), question));
        Map<UUID, LeadQualificationAnswer> existing = new HashMap<>();
        answers.findAllByLeadId(leadId).forEach(answer -> existing.put(answer.getQuestionId(), answer));

        List<Violation> violations = new ArrayList<>();
        for (Map.Entry<String, String> entry : (given == null ? Map.<String, String>of() : given).entrySet()) {
            QualificationQuestion question = byKey.get(entry.getKey());
            if (question == null) {
                violations.add(new Violation(entry.getKey(), "is not a qualification question"));
                continue;
            }
            String chosen = entry.getValue();
            LeadQualificationAnswer stored = existing.get(question.getId());
            if (chosen == null || chosen.isBlank()) {
                if (stored != null) {
                    answers.delete(stored);
                }
            } else if (options.of(question).stream().noneMatch(option -> option.value().equals(chosen))) {
                violations.add(new Violation(entry.getKey(), "must be one of the question's options"));
            } else if (stored != null) {
                stored.setOptionValue(chosen);
            } else {
                answers.save(new LeadQualificationAnswer(leadId, question.getId(), chosen));
            }
        }
        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations);
        }
        answers.flush();
        Gathered gathered = gather(lead);
        return new Evaluation(store(lead, assess(gathered)), gathered.views());
    }

    /** Assesses the lead and stores the result if it differs from what is stored. Call after any change to a lead. */
    @Transactional
    public LeadAssessment reassess(Lead lead) {
        return store(lead, assess(gather(lead)));
    }

    /** For the scheduled job: one lead, in its own transaction. Returns whether anything changed. */
    @Transactional
    public boolean rescore(UUID leadId) {
        return leads.findById(leadId).map(lead -> {
            LeadAssessment assessment = assess(gather(lead));
            boolean changed = differs(lead, assessment);
            store(lead, assessment);
            return changed;
        }).orElse(false);
    }

    /** After the questions change, every open lead of the organization may score differently. */
    @Transactional
    public void rescoreOpenLeads(UUID organizationId) {
        int page = 0;
        Slice<Lead> slice;
        do {
            slice = leads.findAllByOrganizationIdAndStatusOrderById(
                    organizationId, LeadStatus.OPEN, PageRequest.of(page++, RESCORE_BATCH));
            slice.getContent().forEach(this::reassess);
        } while (slice.hasNext());
    }

    // ------------------------------------------------------------------ the work

    /** The facts about a lead, and the questions as they are shown, read together so they agree. */
    private record Gathered(LeadFacts facts, List<QuestionView> views, int requiredQuestions, int requiredAnswered) {
    }

    private Gathered gather(Lead lead) {
        Map<UUID, String> chosen = new HashMap<>();
        answers.findAllByLeadId(lead.getId()).forEach(answer -> chosen.put(answer.getQuestionId(), answer.getOptionValue()));

        List<LeadFacts.Answer> answered = new ArrayList<>();
        List<QuestionView> views = new ArrayList<>();
        int required = 0;
        int requiredAnswered = 0;
        // Only active questions count: an answer to a question that was switched off or removed is ignored.
        for (QualificationQuestion question
                : questions.findAllByOrganizationIdAndActiveTrueOrderByDisplayOrderAscLabelAsc(lead.getOrganizationId())) {
            List<Option> available = options.of(question);
            // An answer whose option has since been withdrawn no longer counts either.
            Option answer = available.stream()
                    .filter(option -> option.value().equals(chosen.get(question.getId()))).findFirst().orElse(null);
            if (question.isRequired()) {
                required++;
                requiredAnswered += answer != null ? 1 : 0;
            }
            if (answer != null) {
                answered.add(new LeadFacts.Answer(question.getQuestionKey(), question.getLabel(), answer.value(),
                        answer.label(), answer.points(), answer.disqualifies()));
            }
            views.add(new QuestionView(question.getId(), question.getQuestionKey(), question.getLabel(),
                    question.getHelpText(), question.isRequired(),
                    available.stream().map(option -> new OptionView(option.value(), option.label())).toList(),
                    answer == null ? null : answer.value()));
        }

        // A closed lead is judged as it stood when it was closed: time stops counting against it.
        Instant asOf = lead.getStatus().isClosed() && lead.getClosedAt() != null ? lead.getClosedAt() : clock.instant();
        LeadFacts facts = new LeadFacts(
                lead.getId(), lead.getServiceId(), lead.getStatus(), lead.getEstimatedValue(),
                lead.getPhone() != null, lead.getEmail() != null, lead.getCreatedAt(),
                activities.lastActivityAt(lead.getId()), (int) activities.countByLeadId(lead.getId()), answered, asOf);
        return new Gathered(facts, views, required, requiredAnswered);
    }

    private LeadAssessment assess(Gathered gathered) {
        LeadScore score = scorer.score(gathered.facts());
        return LeadAssessment.of(gathered.facts(), score, gathered.requiredQuestions(), gathered.requiredAnswered(), settings);
    }

    private static boolean differs(Lead lead, LeadAssessment assessment) {
        return lead.getScoredAt() == null
                || lead.getScore() != assessment.score().score()
                || lead.getPriority() != assessment.priority()
                || lead.getHealth() != assessment.health()
                || lead.getQualificationStatus() != assessment.qualificationStatus();
    }

    private LeadAssessment store(Lead lead, LeadAssessment assessment) {
        if (!differs(lead, assessment)) {
            return assessment;
        }
        leads.storeAssessment(lead.getId(), assessment.score().score(), assessment.priority().name(),
                assessment.health().name(), assessment.qualificationStatus().name(), assessment.computedAt(),
                assessment.score().engine());
        jdbc.update(
                "INSERT INTO lead_score_history (lead_id, score, priority, health, qualification_status, engine, "
                        + "factors, computed_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                lead.getId(), assessment.score().score(), assessment.priority().name(), assessment.health().name(),
                assessment.qualificationStatus().name(), assessment.score().engine(),
                options.write(assessment.score().factors()), Timestamp.from(assessment.computedAt()));
        return assessment;
    }

    private Lead load(UUID organizationId, UUID leadId) {
        return leads.findByIdAndOrganizationId(leadId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));
    }
}
