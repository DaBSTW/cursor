package dev.bookreports.storage.dao;

import dev.bookreports.storage.model.ReportNote;
import java.util.List;

public interface ReportNoteDao {

    ReportNote insert(ReportNote note);

    /** Oldest first — reads like a running log of everything staff have observed about this report. */
    List<ReportNote> findByReport(long reportId);
}
