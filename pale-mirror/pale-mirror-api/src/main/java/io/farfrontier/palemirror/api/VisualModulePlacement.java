package io.farfrontier.palemirror.api;

import java.util.Objects;
import java.util.List;

public record VisualModulePlacement(String instanceId, String templateId, String variantId, String role,
                                    VisualPoint origin, int quarterTurns, VisualBounds footprint,
                                    String foundationId, String visualStateProfile, List<VisualPort> ports) {
    public VisualModulePlacement {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        if (templateId == null || templateId.isBlank()) throw new IllegalArgumentException("templateId is required");
        if (variantId == null || variantId.isBlank()) throw new IllegalArgumentException("variantId is required");
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(footprint, "footprint");
        if (foundationId == null || foundationId.isBlank()) throw new IllegalArgumentException("foundationId is required");
        if (visualStateProfile == null || visualStateProfile.isBlank()) {
            throw new IllegalArgumentException("visualStateProfile is required");
        }
        ports = List.copyOf(ports);
        if (ports.stream().map(VisualPort::id).distinct().count() != ports.size()) {
            throw new IllegalArgumentException("visual port ids must be unique within a module");
        }
        if (ports.stream().anyMatch(port -> !footprint.contains(port.position()))) {
            throw new IllegalArgumentException("visual ports must be inside the module footprint");
        }
        quarterTurns = Math.floorMod(quarterTurns, 4);
    }

    public VisualModulePlacement(String templateId, String role, VisualPoint origin, int quarterTurns,
                                 VisualBounds footprint) {
        this(templateId.replace(':', '_').replace('/', '_') + "_" + origin.x() + "_" + origin.z(),
                templateId, "default", role, origin, quarterTurns, footprint, "legacy", "frontier_default",
                List.of(new VisualPort("entrance", VisualPortKind.PUBLIC_ENTRANCE,
                        new VisualPoint(origin.x(), origin.y() + 1, origin.z()), quarterTurns)));
    }
}
