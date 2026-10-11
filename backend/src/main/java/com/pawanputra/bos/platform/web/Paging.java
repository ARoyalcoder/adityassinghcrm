package com.pawanputra.bos.platform.web;

import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Turns the standard list parameters ({@code page}, {@code size}, {@code sort=field,asc|desc}) into a
 * {@link Pageable}. Each endpoint passes the fields it allows sorting by, mapped to entity
 * properties, so clients can neither sort by arbitrary columns nor learn the entity's structure.
 */
public final class Paging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    /**
     * @param sortableFields API field name to entity property path, e.g. {@code "vertical" -> "category.vertical.displayOrder"}
     * @param defaultSort    used when no {@code sort} parameter is given
     */
    public static Pageable of(
            Integer page, Integer size, String sort, Map<String, String> sortableFields, Sort defaultSort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            throw invalid("page must be 0 or greater");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw invalid("size must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(pageNumber, pageSize, parseSort(sort, sortableFields, defaultSort));
    }

    private static Sort parseSort(String sort, Map<String, String> sortableFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String property = sortableFields.get(parts[0].trim());
        if (property == null) {
            throw invalid("sort must be one of " + sortableFields.keySet().stream().sorted().toList());
        }
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase() : "asc";
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw invalid("sort direction must be asc or desc");
        }
        Sort.Order order = direction.equals("desc") ? Sort.Order.desc(property) : Sort.Order.asc(property);
        // Always finish with the id so pages are stable when the sort field has duplicates.
        return Sort.by(order).and(Sort.by("id"));
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.MALFORMED_REQUEST, message);
    }
}
