package io.farfrontier.palemirror.api;

import java.util.Set;

public record AdapterHealth(Status status, String detail, Set<Capability> capabilities) {
    public enum Status { AVAILABLE, DEGRADED, BLOCKED, ABSENT }
}
