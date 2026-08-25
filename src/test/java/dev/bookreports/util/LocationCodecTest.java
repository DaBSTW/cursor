package dev.bookreports.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import java.util.Optional;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocationCodecTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void encodeThenDecodeRoundTripsExactly() {
        Location original = new Location(world, 100.5, 64.0, -200.25, 90.0f, -45.0f);

        String encoded = LocationCodec.encode(original);
        Location decoded = LocationCodec.decode(encoded).orElseThrow();

        assertEquals(original.getWorld(), decoded.getWorld());
        assertEquals(original.getX(), decoded.getX());
        assertEquals(original.getY(), decoded.getY());
        assertEquals(original.getZ(), decoded.getZ());
        assertEquals(original.getYaw(), decoded.getYaw());
        assertEquals(original.getPitch(), decoded.getPitch());
    }

    @Test
    void decodeIsEmptyForNullOrBlankInput() {
        assertTrue(LocationCodec.decode(null).isEmpty());
        assertTrue(LocationCodec.decode("").isEmpty());
        assertTrue(LocationCodec.decode("   ").isEmpty());
    }

    @Test
    void decodeIsEmptyForAMissingWorld() {
        String encoded = "no-such-world;0.0;64.0;0.0;0.0;0.0";

        assertTrue(LocationCodec.decode(encoded).isEmpty());
    }

    @Test
    void decodeIsEmptyForMalformedInput() {
        assertTrue(LocationCodec.decode("world;not-a-number;64.0;0.0;0.0;0.0").isEmpty());
        assertTrue(LocationCodec.decode("world;0.0;64.0").isEmpty());
    }

    @Test
    void describeFormatsAHumanReadableSummary() {
        String encoded = LocationCodec.encode(new Location(world, 100.9, 64.0, -200.1, 0.0f, 0.0f));

        Optional<String> description = LocationCodec.describe(encoded);

        // getBlockX/Z() floor toward negative infinity, so -200.1 becomes block -201.
        assertEquals(Optional.of("world (100, 64, -201)"), description);
    }
}
