package dev.pregen.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RadiusInputTest {

    @ParameterizedTest
    @CsvSource({"500,500", "32c,512", "2r,1024", "1.5k,1500", "100b,100", " 64 ,64", "10C,160"})
    void parsesAbsoluteValuesWithUnits(String raw, double expected) {
        RadiusInput input = RadiusInput.parse(raw).orElseThrow();
        assertEquals(RadiusInput.Mode.ABSOLUTE, input.mode());
        assertEquals(expected, input.blocks(), 1e-9);
    }

    @Test
    void appliesRelativeChanges() {
        assertEquals(Optional.of(600.0), RadiusInput.parse("+100").orElseThrow().applyTo(500));
        assertEquals(Optional.of(244.0), RadiusInput.parse("-16c").orElseThrow().applyTo(500));
    }

    @Test
    void rejectsRelativeChangesThatLeaveNoRadius() {
        assertTrue(RadiusInput.parse("-500").orElseThrow().applyTo(500).isEmpty());
        assertTrue(RadiusInput.parse("-1k").orElseThrow().applyTo(500).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "0", "+", "-", "c", "1..2", "-0", "1e5", "NaN", "Infinity", "31000000",
            "5x"})
    void rejectsInvalidInput(String raw) {
        assertTrue(RadiusInput.parse(raw).isEmpty(), raw);
    }

    @Test
    void rejectsNull() {
        assertTrue(RadiusInput.parse(null).isEmpty());
    }
}
