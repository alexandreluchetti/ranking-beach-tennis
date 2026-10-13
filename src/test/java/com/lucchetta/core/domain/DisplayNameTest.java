package com.lucchetta.core.domain;

import com.lucchetta.core.exception.InvalidDisplayNameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisplayNameTest {

    @Test
    void normalizesWhitespace() {
        assertThat(new DisplayName("  Ana   Paula ").value()).isEqualTo("Ana Paula");
    }

    @Test
    void acceptsBoundaryLengths() {
        assertThat(new DisplayName("Jo").value()).hasSize(2);
        assertThat(new DisplayName("x".repeat(40)).value()).hasSize(40);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "A", " B "})
    void rejectsMissingOrTooShort(String value) {
        assertThatThrownBy(() -> new DisplayName(value)).isInstanceOf(InvalidDisplayNameException.class);
    }

    @Test
    void rejectsTooLong() {
        assertThatThrownBy(() -> new DisplayName("x".repeat(41)))
                .isInstanceOf(InvalidDisplayNameException.class);
    }
}
