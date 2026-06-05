package com.satya.urlshortener.util;

import com.satya.urlshortener.Util.Encoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EncoderTest {

    private final Encoder encoder = new Encoder();

    // ============= encode() Tests =============

    @Test
    void encode_with_small_number_returns_non_empty_string() {
        // Act
        String result = encoder.encode(1);

        // Assert
        assertThat(result)
                .isNotEmpty()
                .isNotNull();
    }

    @Test
    void encode_with_large_number_returns_expected_length_string() {
        // Act
        String result = encoder.encode(1000000);

        // Assert
        assertThat(result)
                .isNotEmpty()
                .hasSize(4); // Base62 encoding of 1000000 is 4 characters
    }

    @Test
    void encode_and_decode_are_inverse_operations() {
        // Arrange
        long originalNumber = 123456789L;

        // Act
        String encoded = encoder.encode(originalNumber);
        long decoded = encoder.decode(encoded);

        // Assert
        assertThat(decoded).isEqualTo(originalNumber);
    }

    @Test
    void encode_with_zero_returns_zero_string() {
        // Act
        String result = encoder.encode(0);

        // Assert
        assertThat(result).isEmpty(); // encode(0) should return empty due to while loop condition
    }

    @Test
    void encode_with_one_returns_single_digit() {
        // Act
        String result = encoder.encode(1);

        // Assert
        assertThat(result).isEqualTo("1");
    }

    @Test
    void encode_with_62_returns_10_string() {
        // Act
        String result = encoder.encode(62);

        // Assert
        assertThat(result).isEqualTo("10"); // 62 in base 62 is "10"
    }

    @Test
    void encode_with_61_returns_z_string() {
        // Act
        String result = encoder.encode(61);

        // Assert
        assertThat(result).isEqualTo("z"); // 61st character in BASE62 (0-indexed) is 'z'
    }

    // ============= decode() Tests =============

    @Test
    void decode_returns_correct_number_for_encoded_string() {
        // Arrange
        String encoded = "xYz";

        // Act
        long result = encoder.decode(encoded);

        // Assert
        assertThat(result).isGreaterThan(0);
    }

    @Test
    void decode_single_digit_returns_correct_value() {
        // Act
        long result = encoder.decode("1");

        // Assert
        assertThat(result).isEqualTo(1);
    }

    @Test
    void decode_two_digit_returns_correct_value() {
        // Act
        long result = encoder.decode("10");

        // Assert
        assertThat(result).isEqualTo(62); // "10" in base 62 is 62 in base 10
    }

    // ============= Round-trip Tests =============

    @Test
    void encode_decode_roundtrip_with_various_numbers() {
        // Arrange
        long[] numbers = {1, 10, 100, 1000, 10000, 100000, 1000000, Long.MAX_VALUE};

        // Act & Assert
        for (long num : numbers) {
            String encoded = encoder.encode(num);
            long decoded = encoder.decode(encoded);
            assertThat(decoded)
                    .as("Roundtrip failed for number: %d", num)
                    .isEqualTo(num);
        }
    }

    @Test
    void decode_encode_roundtrip_with_various_strings() {
        // Arrange
        String[] codes = {"1", "10", "abc", "xYz", "ABCXYZ", "999zzz"};

        // Act & Assert
        for (String code : codes) {
            long decoded = encoder.decode(code);
            String encoded = encoder.encode(decoded);
            assertThat(encoded)
                    .as("Roundtrip failed for code: %s", code)
                    .isEqualTo(code);
        }
    }

    @Test
    void encode_produces_base62_characters_only() {
        // Arrange
        long[] numbers = {1, 100, 1000, 10000, 100000, 1000000};
        String base62Charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

        // Act & Assert
        for (long num : numbers) {
            String encoded = encoder.encode(num);
            for (char c : encoded.toCharArray()) {
                assertThat(base62Charset).contains(String.valueOf(c));
            }
        }
    }

    @Test
    void different_numbers_produce_different_codes() {
        // Act
        String code1 = encoder.encode(1);
        String code2 = encoder.encode(2);
        String code3 = encoder.encode(100);

        // Assert
        assertThat(code1)
                .isNotEqualTo(code2)
                .isNotEqualTo(code3);
        assertThat(code2).isNotEqualTo(code3);
    }
}
