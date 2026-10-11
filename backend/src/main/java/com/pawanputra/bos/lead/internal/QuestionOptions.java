package com.pawanputra.bos.lead.internal;

import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes the JSON columns of the qualification tables. */
@Component
public class QuestionOptions {

    /**
     * One possible answer to a question.
     *
     * @param value        what is stored as the answer
     * @param points       added to (or, if negative, taken from) the score of a lead that chooses it
     * @param disqualifies choosing it rules the lead out
     */
    public record Option(String value, String label, int points, boolean disqualifies) {
    }

    private static final TypeReference<List<Option>> OPTIONS = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    QuestionOptions(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public List<Option> of(QualificationQuestion question) {
        return jsonMapper.readValue(question.getOptionsJson(), OPTIONS);
    }

    String write(Object value) {
        return jsonMapper.writeValueAsString(value);
    }
}
