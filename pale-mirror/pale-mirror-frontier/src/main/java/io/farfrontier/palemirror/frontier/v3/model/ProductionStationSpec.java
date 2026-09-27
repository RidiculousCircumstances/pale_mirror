package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Explicit, persisted machine capability and physical custody port for one facility. */
public record ProductionStationSpec(SubjectId id, SubjectId facilityId, SubjectId containerId,
                                    Capability capability, SurfaceAnchor workerStation,
                                    SurfaceAnchor socketSurface, int inputSlot, int outputSlot) {
    public enum Capability {
        BAKING(1);

        private final int wireTag;
        Capability(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Capability fromWireTag(int tag) {
            for (Capability value : values()) if (value.wireTag == tag) return value;
            throw new IllegalArgumentException("unknown production station capability: " + tag);
        }
    }

    public ProductionStationSpec {
        Objects.requireNonNull(id, "station id");
        Objects.requireNonNull(facilityId, "station facility");
        Objects.requireNonNull(containerId, "station container");
        Objects.requireNonNull(capability, "station capability");
        Objects.requireNonNull(workerStation, "station worker surface");
        Objects.requireNonNull(socketSurface, "station machine socket");
        if (inputSlot < 0 || outputSlot < 0 || inputSlot == outputSlot ||
                Math.abs(workerStation.x() - socketSurface.x())
                        + Math.abs(workerStation.z() - socketSurface.z()) != 1
                || workerStation.y() != socketSurface.y()) {
            throw new IllegalArgumentException("station needs distinct adjacent worker and machine surfaces and slots");
        }
    }

    /** Genesis producer for the current six-building graybox; later providers declare their own specs. */
    public static ProductionStationSpec grayboxBakery(SettlementStructure workshop) {
        Objects.requireNonNull(workshop, "bakery facility");
        if (workshop.kind() != StructureKind.WORKSHOP)
            throw new IllegalArgumentException("graybox bakery requires its declared workshop facility");
        SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(workshop);
        String suffix = workshop.id().value().replace(':', '-');
        SurfaceAnchor machine = workshop.facing().step(port.workStation(), -1);
        if (!SettlementStructureFootprint.supportSurfaces(workshop).contains(machine))
            throw new IllegalArgumentException("bakery machine socket has no facility support");
        return new ProductionStationSpec(new SubjectId("station:" + suffix + "-bakery"), workshop.id(),
                new SubjectId("container:" + suffix + "-bakery-station"), Capability.BAKING,
                port.workStation(), machine, 0, 1);
    }
}
