package com.pawanputra.bos.customer.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normalised forms of the values duplicate detection compares, so that the same phone number or
 * company written two ways still matches. A {@code null} key means "nothing usable to compare".
 */
final class CustomerKeys {

    /** Words that say what kind of company it is, not which company. Dropped from the end of a name. */
    private static final Set<String> COMPANY_SUFFIXES = Set.of(
            "pvt", "private", "ltd", "limited", "llp", "llc", "inc", "incorporated", "co", "company",
            "corp", "corporation");

    private static final int NATIONAL_NUMBER_LENGTH = 10;
    private static final int MIN_PHONE_DIGITS = 7;

    private CustomerKeys() {
    }

    /**
     * Digits only, without a country code or trunk prefix: {@code "+91 98765-43210"}, {@code "09876543210"}
     * and {@code "9876543210"} all give {@code "9876543210"}.
     */
    static String phone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < MIN_PHONE_DIGITS) {
            return null;
        }
        // Indian mobile and landline numbers are 10 digits; anything before that is +91 / 0 / 0091.
        return digits.length() > NATIONAL_NUMBER_LENGTH
                ? digits.substring(digits.length() - NATIONAL_NUMBER_LENGTH)
                : digits;
    }

    /** Trimmed and lower-cased; this is also how emails are stored. */
    static String email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Lower-case letters and digits only, with legal-form words removed from the end:
     * {@code "Acme Solar Pvt. Ltd."}, {@code "ACME SOLAR"} and {@code "Acme-Solar Private Limited"}
     * all give {@code "acmesolar"}.
     */
    static String company(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.toLowerCase(Locale.ROOT).replace("&", " and ").replaceAll("[^a-z0-9]+", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        List<String> words = new ArrayList<>(List.of(cleaned.split(" ")));
        // Keep at least one word: a company actually called "The Company" must still have a key.
        while (words.size() > 1 && COMPANY_SUFFIXES.contains(words.getLast())) {
            words.removeLast();
        }
        return String.join("", words);
    }
}
