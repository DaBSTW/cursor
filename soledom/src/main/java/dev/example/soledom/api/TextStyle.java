package dev.example.soledom.api;

import org.jetbrains.annotations.Nullable;

/**
 * Extra options for {@link PartType#TEXT_DISPLAY} parts.
 *
 * @param background ARGB background colour, or {@code null} for the client default
 * @param lineWidth  maximum line width in pixels
 * @param shadowed   draw text shadow
 * @param seeThrough render through blocks
 * @param alignment  {@code CENTER}, {@code LEFT} or {@code RIGHT}
 */
public record TextStyle(@Nullable Integer background, int lineWidth, boolean shadowed, boolean seeThrough,
        String alignment) {

    public static final TextStyle DEFAULT = new TextStyle(null, 200, false, false, "CENTER");
}
