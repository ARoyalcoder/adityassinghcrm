package com.pawanputra.bos.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerKeysTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "9876543210", "+91 98765 43210", "+91-9876543210", "09876543210", "0091 9876543210", "(98765) 43210",
        "98765-43210"
    })
    void theSameMobileNumberWrittenAnyWayGivesTheSameKey(String phone) {
        assertThat(CustomerKeys.phone(phone)).isEqualTo("9876543210");
    }

    @Test
    void differentNumbersGiveDifferentKeys() {
        assertThat(CustomerKeys.phone("9876543210")).isNotEqualTo(CustomerKeys.phone("9876543211"));
    }

    @Test
    void shortLandlineNumbersAreKeptWhole() {
        assertThat(CustomerKeys.phone("0522 400123")).isEqualTo("0522400123");
        assertThat(CustomerKeys.phone("400-1234")).isEqualTo("4001234");
    }

    @Test
    void somethingThatIsNotAPhoneNumberHasNoKey() {
        assertThat(CustomerKeys.phone(null)).isNull();
        assertThat(CustomerKeys.phone("")).isNull();
        assertThat(CustomerKeys.phone("12345")).isNull();
        assertThat(CustomerKeys.phone("call me")).isNull();
    }

    @Test
    void emailKeyIgnoresCaseAndSurroundingSpace() {
        assertThat(CustomerKeys.email("  Asha.Verma@Example.COM ")).isEqualTo("asha.verma@example.com");
        assertThat(CustomerKeys.email(null)).isNull();
        assertThat(CustomerKeys.email("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Acme Solar", "ACME SOLAR", "acme solar pvt ltd", "Acme Solar Pvt. Ltd.", "Acme-Solar Private Limited",
        "  Acme   Solar  LLP ", "Acme Solar Co.", "Acme Solar, Inc."
    })
    void theSameCompanyWrittenAnyWayGivesTheSameKey(String name) {
        assertThat(CustomerKeys.company(name)).isEqualTo("acmesolar");
    }

    @Test
    void differentCompaniesGiveDifferentKeys() {
        assertThat(CustomerKeys.company("Acme Solar")).isNotEqualTo(CustomerKeys.company("Acme Interiors"));
        // "and Sons", "Enterprises", "India" are part of the name, not a legal form.
        assertThat(CustomerKeys.company("Sharma and Sons")).isNotEqualTo(CustomerKeys.company("Sharma"));
        assertThat(CustomerKeys.company("Tata India")).isNotEqualTo(CustomerKeys.company("Tata"));
    }

    @Test
    void ampersandAndTheWordAndAreTheSame() {
        assertThat(CustomerKeys.company("Verma & Gupta")).isEqualTo(CustomerKeys.company("Verma and Gupta"));
    }

    @Test
    void aNameMadeOnlyOfLegalWordsKeepsOneOfThem() {
        assertThat(CustomerKeys.company("The Company")).isEqualTo("the");
        assertThat(CustomerKeys.company("Company")).isEqualTo("company");
        assertThat(CustomerKeys.company("Private Limited")).isEqualTo("private");
    }

    @Test
    void nothingUsableHasNoKey() {
        assertThat(CustomerKeys.company(null)).isNull();
        assertThat(CustomerKeys.company("  ")).isNull();
        assertThat(CustomerKeys.company("---")).isNull();
    }
}
