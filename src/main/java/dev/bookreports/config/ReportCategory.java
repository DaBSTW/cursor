package dev.bookreports.config;

import dev.bookreports.storage.model.Priority;
import java.util.List;
import java.util.Objects;

/** A report category as declared under {@code report.categories} in {@code config.yml}. */
public record ReportCategory(String id, String display, Priority priority, List<String> subReasons) {

    public ReportCategory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(priority, "priority");
        subReasons = List.copyOf(Objects.requireNonNull(subReasons, "subReasons"));
    }

    public boolean hasSubReasons() {
        return !subReasons.isEmpty();
    }
}
