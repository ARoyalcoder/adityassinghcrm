package com.pawanputra.bos.audit.internal;

import com.pawanputra.bos.platform.persistence.BaseEntity;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.proxy.HibernateProxy;

/** Turns arbitrary Java values into plain JSON-friendly ones, so an audit row never fails to serialise. */
final class AuditValues {

    private static final int MAX_TEXT = 2000;
    private static final int MAX_ITEMS = 50;

    private AuditValues() {
    }

    /** Strings, numbers, booleans, lists and maps of those; everything else becomes text. Entities become their id. */
    static Object plain(Object value) {
        if (value == null || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof CharSequence text) {
            return truncate(text.toString(), MAX_TEXT);
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof UUID || value instanceof TemporalAccessor) {
            return value.toString();
        }
        if (value instanceof HibernateProxy proxy) {
            // Read the id without loading the row.
            return String.valueOf(proxy.getHibernateLazyInitializer().getIdentifier());
        }
        if (value instanceof BaseEntity entity) {
            return entity.getId() == null ? null : entity.getId().toString();
        }
        if (value instanceof Map<?, ?> map) {
            return plainMap(map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> items = new ArrayList<>();
            for (Object item : collection) {
                if (items.size() == MAX_ITEMS) {
                    items.add("… " + (collection.size() - MAX_ITEMS) + " more");
                    break;
                }
                items.add(plain(item));
            }
            return items;
        }
        return truncate(value.toString(), 200);
    }

    static Map<String, Object> plainMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), plain(value)));
        return result;
    }

    /** The fields whose value differs between the two snapshots: {@code [{field, from, to}]}. */
    static List<Map<String, Object>> changes(Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (before == null || after == null) {
            return changes;
        }
        Map<String, Object> fields = new LinkedHashMap<>(before);
        after.forEach(fields::putIfAbsent);
        for (String field : fields.keySet()) {
            Object from = before.get(field);
            Object to = after.get(field);
            if (!Objects.equals(from, to)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("field", field);
                change.put("from", from);
                change.put("to", to);
                changes.add(change);
            }
        }
        return changes;
    }

    static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }
}
