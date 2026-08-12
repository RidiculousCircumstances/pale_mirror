package io.farfrontier.palemirror.api;

/** Source-neutral desired appearance. Provider names never enter the domain. */
public record VisualStateProjection(String objectId, long desiredRevision, String operation,
                                    String integrity, String economy, String crisis,
                                    String development, String threatStage) { }
