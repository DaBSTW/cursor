package dev.pregen.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pregen.config.PluginSettings;
import dev.pregen.config.SettingsLoader;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ThrottleControllerTest {

    private double mspt = 20;
    private final AtomicInteger players = new AtomicInteger();

    private ThrottleController controller(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new AssertionError(e);
        }
        PluginSettings settings = SettingsLoader.load(config, warning -> {
            throw new AssertionError(warning);
        });
        return new ThrottleController(settings, () -> OptionalDouble.of(mspt), players::get);
    }

    @Test
    void backsOffWhenTicksAreSlowAndRecoversWhenFast() {
        ThrottleController throttle = controller("generation: {max-concurrent-chunks: 32}");
        mspt = 80;
        throttle.sample();
        assertEquals(24, throttle.limit());
        for (int i = 0; i < 20; i++) {
            throttle.sample();
        }
        assertEquals(4, throttle.limit());
        mspt = 10;
        throttle.sample();
        assertEquals(6, throttle.limit());
        for (int i = 0; i < 30; i++) {
            throttle.sample();
        }
        assertEquals(32, throttle.limit());
    }

    @Test
    void holdsSteadyInsideTheComfortBand() {
        ThrottleController throttle = controller("generation: {max-concurrent-chunks: 32}");
        mspt = 40;
        throttle.sample();
        assertEquals(32, throttle.limit());
    }

    @Test
    void usesTheMaximumWhenDisabledOrWithoutProbe() {
        ThrottleController disabled = controller(
                "generation: {max-concurrent-chunks: 16, adaptive-throttle: {enabled: false}}");
        mspt = 500;
        disabled.sample();
        assertEquals(16, disabled.limit());

        ThrottleController folia = new ThrottleController(SettingsLoader.load(new YamlConfiguration(), warning -> {
        }), OptionalDouble::empty, () -> 0);
        folia.sample();
        assertFalse(folia.isProbeAvailable());
        assertEquals(32, folia.limit());
    }

    @Test
    void holdsEverythingWhilePlayersAreOnlineWhenConfigured() {
        ThrottleController throttle = controller("generation: {pause-when-players-online: true}");
        players.set(1);
        assertEquals(0, throttle.limit());
        assertEquals(0, throttle.limitPerTask(2));
        players.set(0);
        assertTrue(throttle.limit() > 0);
    }

    @Test
    void splitsTheLimitAcrossTasksWithAtLeastOneEach() {
        ThrottleController throttle = controller("generation: {max-concurrent-chunks: 10}");
        assertEquals(10, throttle.limitPerTask(1));
        assertEquals(3, throttle.limitPerTask(3));
        assertEquals(1, throttle.limitPerTask(50));
    }
}
