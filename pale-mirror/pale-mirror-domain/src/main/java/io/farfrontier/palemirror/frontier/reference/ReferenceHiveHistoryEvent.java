package io.farfrontier.palemirror.frontier.reference;

/** Minimal typed equivalent of a source project-history row. */
public record ReferenceHiveHistoryEvent(int day, String kind, int sourceOrganId, Integer organId, int x, int y) { }
