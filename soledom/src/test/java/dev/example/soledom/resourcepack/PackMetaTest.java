package dev.example.soledom.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class PackMetaTest {

    @Test
    void rangeSpanningOldAndNewClientsWritesBothFieldSets() {
        JsonObject pack = new PackMeta("Soledom", 34, 34, 97).toJson().getAsJsonObject("pack");
        assertEquals("Soledom", pack.get("description").getAsString());
        assertEquals(34, pack.get("pack_format").getAsInt());
        assertEquals(34, pack.getAsJsonObject("supported_formats").get("min_inclusive").getAsInt());
        assertEquals(97, pack.getAsJsonObject("supported_formats").get("max_inclusive").getAsInt());
        assertEquals(34, pack.get("min_format").getAsInt());
        assertEquals(97, pack.get("max_format").getAsInt());
    }

    @Test
    void modernOnlyRangeOmitsLegacyFields() {
        JsonObject pack = new PackMeta("x", 84, 84, 97).toJson().getAsJsonObject("pack");
        assertFalse(pack.has("pack_format"));
        assertFalse(pack.has("supported_formats"));
        assertEquals(84, pack.get("min_format").getAsInt());
    }

    @Test
    void legacyOnlyRangeOmitsNewFields() {
        JsonObject pack = new PackMeta("x", 46, 34, 46).toJson().getAsJsonObject("pack");
        assertTrue(pack.has("pack_format"));
        assertFalse(pack.has("min_format"));
        assertFalse(pack.has("max_format"));
    }

    @Test
    void packFormatOutsideRangeFallsBackToMinimumAndInvalidRangesFail() {
        assertEquals(34, new PackMeta("x", 10, 34, 97).packFormat());
        assertThrows(IllegalArgumentException.class, () -> new PackMeta("x", 34, 50, 40));
    }
}
