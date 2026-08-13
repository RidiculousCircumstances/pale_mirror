package io.farfrontier.palemirror.api;

/** Source-neutral desired appearance. Provider names never enter the domain. */
public record VisualStateProjection(String objectId, long desiredRevision, long projectionRevision, String operation,
                                    String integrity, String economy, String crisis,
                                    String development, String threatStage, String alternateDispatch) {
    public VisualStateProjection(String objectId, long desiredRevision, long projectionRevision, String operation,
                                 String integrity, String economy, String crisis,
                                 String development, String threatStage) {
        this(objectId, desiredRevision, projectionRevision, operation, integrity, economy, crisis,
                development, threatStage, "UNKNOWN");
    }
}
