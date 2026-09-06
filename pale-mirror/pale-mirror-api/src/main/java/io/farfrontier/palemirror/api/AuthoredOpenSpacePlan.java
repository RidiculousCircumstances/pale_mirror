package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

public record AuthoredOpenSpacePlan(String id, OpenSpaceKind kind, VisualBounds bounds,
                                    List<VisualPort> ports) {
    public AuthoredOpenSpacePlan {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("open-space id is required");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(bounds, "bounds");
        ports = List.copyOf(ports);
        if (ports.stream().map(VisualPort::id).distinct().count() != ports.size()) {
            throw new IllegalArgumentException("open-space port ids must be unique within " + id);
        }
        if (ports.stream().anyMatch(port -> !bounds.contains(port.position()))) {
            throw new IllegalArgumentException("open-space port escaped " + id);
        }
    }
}
