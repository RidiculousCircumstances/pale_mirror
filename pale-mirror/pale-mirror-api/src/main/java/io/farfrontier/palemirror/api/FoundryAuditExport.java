package io.farfrontier.palemirror.api;

/** Result of an explicit non-canonical diagnostic export. */
public record FoundryAuditExport(FoundryAuditReport report, String directory) {
    public FoundryAuditExport {
        if (report == null) throw new IllegalArgumentException("report is required");
        if (directory == null || directory.isBlank()) throw new IllegalArgumentException("directory is required");
    }
}
