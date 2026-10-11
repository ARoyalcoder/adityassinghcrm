package com.pawanputra.bos.catalog.api;

import java.util.Optional;

/**
 * The company's service verticals. This is a closed set of exactly six; every module that is scoped
 * to a vertical references this enum. Constant names are persisted and exposed in the API, so they
 * must never be renamed. Mirrored by the CHECK constraint on {@code service_verticals.code}.
 */
public enum ServiceVerticalCode {

    CCTV_SECURITY,
    DIGITAL_MARKETING,
    INTERIOR_DESIGN,
    ARCHITECTURE_TECH,
    SOLAR,
    IT_SUPPORT;

    public static Optional<ServiceVerticalCode> fromCode(String code) {
        for (ServiceVerticalCode value : values()) {
            if (value.name().equals(code)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
