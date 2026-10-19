package dev.example.soledom;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads files bundled in the plugin jar (src/main/resources) for tests. */
public final class TestResources {

    private TestResources() {
    }

    /** Asserts that some message contains {@code fragment}, printing all messages otherwise. */
    public static void assertHas(java.util.List<String> messages, String fragment) {
        org.junit.jupiter.api.Assertions.assertTrue(messages.stream().anyMatch(m -> m.contains(fragment)),
                () -> "expected a message containing \"" + fragment + "\" in " + messages);
    }

    public static String read(String path) throws IOException {
        try (InputStream in = TestResources.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
