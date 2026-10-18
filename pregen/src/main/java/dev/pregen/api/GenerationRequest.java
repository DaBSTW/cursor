package dev.pregen.api;

/**
 * Parameters for starting a generation task through {@link PregenApi#start(GenerationRequest)}.
 *
 * @param worldName world to generate; must be loaded
 * @param centerX center x in blocks
 * @param centerZ center z in blocks
 * @param radiusX radius along x in blocks; must be positive
 * @param radiusZ radius along z in blocks; ignored by single-radius shapes such as {@code square} and {@code circle}
 * @param shape shape id as used by {@code /pregen shape}, e.g. {@code square} or {@code circle}
 * @param pattern pattern id as used by {@code /pregen pattern}, e.g. {@code region}; {@code csv} is not supported
 *        through the API
 */
public record GenerationRequest(String worldName, double centerX, double centerZ, double radiusX, double radiusZ,
        String shape, String pattern) {
}
