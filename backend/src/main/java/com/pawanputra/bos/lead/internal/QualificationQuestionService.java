package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.lead.internal.QuestionOptions.Option;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.FieldValidationException.Violation;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration of an organization's qualification questions. Changing a question changes what
 * leads score, so every change ends by rescoring the organization's open leads.
 */
@Service
@Transactional(readOnly = true)
public class QualificationQuestionService {

    static final int MAX_QUESTIONS = 30;
    static final int MIN_OPTIONS = 2;
    static final int MAX_OPTIONS = 20;

    private final QualificationQuestionRepository questions;
    private final QuestionOptions options;
    private final LeadQualificationService qualification;
    private final Clock clock;

    public QualificationQuestionService(
            QualificationQuestionRepository questions, QuestionOptions options, LeadQualificationService qualification,
            Clock clock) {
        this.questions = questions;
        this.options = options;
        this.qualification = qualification;
        this.clock = clock;
    }

    /** @param displayOrder {@code null} on create puts the question last */
    public record Details(String label, String helpText, List<Option> options, boolean required, Integer displayOrder) {
    }

    /** A question as an administrator sees it, with what each option is worth. */
    public record View(
            UUID id, String key, String label, String helpText, List<Option> options, boolean required,
            int displayOrder, boolean active, long version) {
    }

    public List<View> list(UUID organizationId) {
        return questions.findAllByOrganizationIdOrderByDisplayOrderAscLabelAsc(organizationId).stream()
                .map(this::view).toList();
    }

    @Transactional
    public View create(UUID organizationId, String key, Details details) {
        if (questions.existsByOrganizationIdAndQuestionKey(organizationId, key)) {
            throw new ConflictException("There is already a question with the key '" + key + "'");
        }
        if (questions.countByOrganizationId(organizationId) >= MAX_QUESTIONS) {
            throw new BusinessRuleException("At most " + MAX_QUESTIONS + " qualification questions");
        }
        validate(details);
        QualificationQuestion question = new QualificationQuestion(organizationId, key);
        int last = questions.findAllByOrganizationIdOrderByDisplayOrderAscLabelAsc(organizationId).stream()
                .mapToInt(QualificationQuestion::getDisplayOrder).max().orElse(0);
        apply(question, details, last + 10);
        return changed(organizationId, questions.saveAndFlush(question));
    }

    @Transactional
    public View update(UUID organizationId, UUID id, Details details, long expectedVersion) {
        QualificationQuestion question = load(organizationId, id);
        if (question.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "This question was changed by someone else. Reload it and apply your changes again.");
        }
        validate(details);
        apply(question, details, question.getDisplayOrder());
        return changed(organizationId, questions.saveAndFlush(question));
    }

    /** A question that is switched off is no longer asked, and answers to it no longer count. */
    @Transactional
    public View setActive(UUID organizationId, UUID id, boolean active) {
        QualificationQuestion question = load(organizationId, id);
        question.setActive(active);
        return changed(organizationId, questions.saveAndFlush(question));
    }

    /** Soft delete: answers already given are kept but no longer count, and the key becomes available again. */
    @Transactional
    public void delete(UUID organizationId, UUID id) {
        QualificationQuestion question = load(organizationId, id);
        question.markDeleted(clock.instant());
        changed(organizationId, questions.saveAndFlush(question));
    }

    private View changed(UUID organizationId, QualificationQuestion question) {
        qualification.rescoreOpenLeads(organizationId);
        return view(question);
    }

    private QualificationQuestion load(UUID organizationId, UUID id) {
        return questions.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Question", id));
    }

    private void apply(QualificationQuestion question, Details details, int defaultDisplayOrder) {
        question.setLabel(details.label());
        question.setHelpText(details.helpText());
        question.setOptionsJson(options.write(details.options()));
        question.setRequired(details.required());
        question.setDisplayOrder(details.displayOrder() == null ? defaultDisplayOrder : details.displayOrder());
    }

    private View view(QualificationQuestion question) {
        return new View(question.getId(), question.getQuestionKey(), question.getLabel(), question.getHelpText(),
                options.of(question), question.isRequired(), question.getDisplayOrder(), question.isActive(),
                question.getVersion());
    }

    /** Rules about the options as a set (rules about one option are on the request). */
    static void validate(Details details) {
        List<Option> given = details.options() == null ? List.of() : details.options();
        List<Violation> violations = new ArrayList<>();
        if (given.size() < MIN_OPTIONS) {
            violations.add(new Violation("options", "Give at least " + MIN_OPTIONS + " options to choose from"));
        } else if (given.size() > MAX_OPTIONS) {
            violations.add(new Violation("options", "At most " + MAX_OPTIONS + " options"));
        }
        Set<String> values = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (Option option : given) {
            if (!values.add(option.value())) {
                violations.add(new Violation("options", "The option value '" + option.value() + "' is used twice"));
            }
            if (!labels.add(option.label().toLowerCase(Locale.ROOT))) {
                violations.add(new Violation("options", "The option '" + option.label() + "' is listed twice"));
            }
        }
        if (!given.isEmpty() && given.stream().allMatch(Option::disqualifies)) {
            violations.add(new Violation("options", "At least one option must not rule the lead out"));
        }
        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations);
        }
    }
}
