package dev.bookreports.config;

/** How far back, and how much, to look up in CoreProtect for automatic evidence (SPECS.md §11-style integration). */
public record CoreProtectSettings(boolean enabled, int lookbackSeconds, int maxEntries) {
}
