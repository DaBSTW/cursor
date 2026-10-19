package dev.example.soledom.resourcepack;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.NotNull;

/** Generates Soledom's own placeholder images at runtime, so the pack never ships third-party art. */
public final class PlaceholderTexture {

    private PlaceholderTexture() {
    }

    /** 16x16 magenta/dark checkerboard, the classic "missing texture" look, drawn from scratch. */
    public static byte @NotNull [] checkerboard() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean light = ((x >> 3) + (y >> 3)) % 2 == 0;
                image.setRGB(x, y, light ? 0xFFF800F8 : 0xFF1E1E2A);
            }
        }
        return png(image);
    }

    /** 64x64 pack icon: a simple sun-and-horizon motif in flat colours. */
    public static byte @NotNull [] packIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int dx = x - 32;
                int dy = y - 34;
                int color;
                if (y >= 44) {
                    color = 0xFF2B2F4A;
                } else if (dx * dx + dy * dy <= 14 * 14) {
                    color = 0xFFFFC857;
                } else {
                    color = 0xFF6C8EBF;
                }
                image.setRGB(x, y, color);
            }
        }
        return png(image);
    }

    private static byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
