package com.pawanputra.bos.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ServiceVerticalCodeTest {

    @Test
    void theProductHasExactlyTheseSixVerticals() {
        assertThat(Arrays.stream(ServiceVerticalCode.values()).map(Enum::name))
                .containsExactly(
                        "CCTV_SECURITY",
                        "DIGITAL_MARKETING",
                        "INTERIOR_DESIGN",
                        "ARCHITECTURE_TECH",
                        "SOLAR",
                        "IT_SUPPORT");
    }

    @Test
    void realEstateIsNotAVertical() {
        assertThat(ServiceVerticalCode.fromCode("REAL_ESTATE")).isEmpty();
    }

    @Test
    void fromCodeIsExactMatchOnly() {
        assertThat(ServiceVerticalCode.fromCode("SOLAR")).contains(ServiceVerticalCode.SOLAR);
        assertThat(ServiceVerticalCode.fromCode("solar")).isEmpty();
        assertThat(ServiceVerticalCode.fromCode(null)).isEmpty();
    }
}
