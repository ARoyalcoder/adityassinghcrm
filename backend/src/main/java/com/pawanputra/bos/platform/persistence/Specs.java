package com.pawanputra.bos.platform.persistence;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks for the optional filters of list endpoints. A {@code null} filter value means "no filter". */
public final class Specs {

    private static final char ESCAPE = '\\';

    private Specs() {
    }

    /**
     * Case-insensitive "contains" over several properties (OR). {@code %} and {@code _} typed by the
     * user are matched literally.
     *
     * @param properties property paths, dots allowed: {@code "name"}, {@code "category.name"}
     */
    public static <T> Specification<T> search(String term, String... properties) {
        if (term == null || term.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + escape(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            List<Predicate> matches = new ArrayList<>();
            for (String property : properties) {
                matches.add(cb.like(cb.lower(path(root, property)), pattern, ESCAPE));
            }
            return cb.or(matches.toArray(Predicate[]::new));
        };
    }

    public static <T> Specification<T> equalTo(String property, Object value) {
        return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(path(root, property), value);
    }

    /** Case-insensitive equality, for free-text columns such as city and state. */
    public static <T> Specification<T> equalIgnoreCase(String property, String value) {
        if (value == null || value.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(path(root, property)), wanted);
    }

    @SuppressWarnings("unchecked")
    private static <Y> Path<Y> path(Path<?> root, String property) {
        Path<?> current = root;
        for (String part : property.split("\\.")) {
            current = current.get(part);
        }
        return (Path<Y>) current;
    }

    private static String escape(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
