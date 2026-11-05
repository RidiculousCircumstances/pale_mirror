package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.*;

/** Explicit, persisted machine capability and physical custody port for one facility. */
public record ProductionStationSpec(SubjectId id, SubjectId facilityId, SubjectId containerId,
                                    ProductionEquipment.Id equipmentType,
                                    Capability capability, SurfaceAnchor workerStation,
                                    SurfaceAnchor socketSurface, List<Integer> inputSlots, int outputSlot, OptionalInt fuelSlot,
                                    OptionalInt maintenanceSlot) {
    public ProductionStationSpec(SubjectId id, SubjectId facilityId, SubjectId containerId,
            ProductionEquipment.Id equipmentType, Capability capability, SurfaceAnchor workerStation,
            SurfaceAnchor socketSurface, int inputSlot, int outputSlot, int fuelSlot) {
        this(id, facilityId, containerId, equipmentType, capability, workerStation, socketSurface,
                List.of(inputSlot), outputSlot, OptionalInt.of(fuelSlot), OptionalInt.empty());
    }
    public ProductionStationSpec(SubjectId id, SubjectId facilityId, SubjectId containerId,
            ProductionEquipment.Id equipmentType, Capability capability, SurfaceAnchor workerStation,
            SurfaceAnchor socketSurface, int inputSlot, int outputSlot, int fuelSlot, OptionalInt maintenanceSlot) {
        this(id, facilityId, containerId, equipmentType, capability, workerStation, socketSurface,
                List.of(inputSlot), outputSlot, OptionalInt.of(fuelSlot), maintenanceSlot);
    }
    public enum Capability {
        BAKING(1), SMELTING(2), FABRICATION(3), WOODWORKING(4);

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
        Objects.requireNonNull(equipmentType, "declared equipment type");
        Objects.requireNonNull(capability, "station capability");
        Objects.requireNonNull(workerStation, "station worker surface");
        Objects.requireNonNull(socketSurface, "station machine socket");
        inputSlots = List.copyOf(Objects.requireNonNull(inputSlots, "declared input inventory"));
        Objects.requireNonNull(fuelSlot, "declared optional fuel port");
        Objects.requireNonNull(maintenanceSlot, "declared maintenance port");
        var slots = new HashSet<Integer>();
        if (inputSlots.isEmpty() || inputSlots.size() > 9) throw new IllegalArgumentException("station needs a bounded input inventory");
        var declared = new ArrayList<>(inputSlots); declared.add(outputSlot);
        fuelSlot.ifPresent(declared::add); maintenanceSlot.ifPresent(declared::add);
        for (int slot : declared) if (slot < 0 || slot >= ContainerRecord.MAX_SLOTS || !slots.add(slot))
            throw new IllegalArgumentException("station port slots overlap or exceed capacity");
        if (slots.size() != 1 + Collections.max(slots)
                || Math.abs(workerStation.x() - socketSurface.x()) + Math.abs(workerStation.z() - socketSurface.z()) != 1
                || workerStation.y() != socketSurface.y())
            throw new IllegalArgumentException("station needs complete ports and adjacent worker/machine surfaces");
    }

    /** A declared semantic port, never a caller-selected container packing order. */
    public int slot(ActorContainerItemOrder.StationPort port) {
        return switch (Objects.requireNonNull(port)) {
            case INPUT -> singleInputSlot();
            case OUTPUT -> outputSlot;
            case FUEL -> fuelSlot.orElseThrow(() -> new IllegalArgumentException("station has no fuel port"));
            case MAINTENANCE_TARGET -> maintenanceSlot.orElseThrow(
                    () -> new IllegalArgumentException("station has no maintenance port"));
        };
    }
    public int singleInputSlot() {
        if (inputSlots.size() != 1) throw new IllegalArgumentException("station requires input-inventory placement");
        return inputSlots.getFirst();
    }
    public List<Integer> slots(ActorContainerItemOrder.StationPort port) {
        return port == ActorContainerItemOrder.StationPort.INPUT ? inputSlots : List.of(slot(port));
    }
    public ContainerSectionId section(ActorContainerItemOrder.StationPort port) {
        return switch (port) {
            case INPUT -> ContainerSectionId.INPUT;
            case OUTPUT -> ContainerSectionId.OUTPUT;
            case FUEL -> ContainerSectionId.FUEL;
            case MAINTENANCE_TARGET -> ContainerSectionId.MAINTENANCE;
        };
    }
    public ResourceCustody.Container custody(ActorContainerItemOrder.StationPort port) {
        slots(port); // Absent capabilities cannot acquire a logical account.
        return new ResourceCustody.Container(containerId, section(port));
    }
    public ContainerCompartmentLayout compartmentLayout() {
        var sections = new LinkedHashMap<ContainerSectionId, Set<Integer>>();
        sections.put(ContainerSectionId.INPUT, Set.copyOf(inputSlots));
        sections.put(ContainerSectionId.OUTPUT, Set.of(outputSlot));
        fuelSlot.ifPresent(slot -> sections.put(ContainerSectionId.FUEL, Set.of(slot)));
        maintenanceSlot.ifPresent(slot -> sections.put(ContainerSectionId.MAINTENANCE, Set.of(slot)));
        return new ContainerCompartmentLayout(inventorySize(), sections);
    }
    /** A full inventory witness may retain other ports beside the transferred portion. */
    public boolean ownsSlot(int slot) { return slot >= 0 && slot < inventorySize(); }
    public int inventorySize() {
        return 1 + Math.max(Collections.max(inputSlots), Math.max(outputSlot,
                Math.max(fuelSlot.orElse(-1), maintenanceSlot.orElse(-1))));
    }

    /** Genesis producer for the current six-building graybox; later providers declare their own specs. */
    public static ProductionStationSpec grayboxBakery(SettlementStructure workshop) {
        Objects.requireNonNull(workshop, "bakery facility");
        if (workshop.kind() != StructureKind.WORKSHOP)
            throw new IllegalArgumentException("graybox bakery requires its declared workshop facility");
        SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(workshop);
        String suffix = workshop.id().value().replace(':', '-');
        SurfaceAnchor machine = workshop.placement().support(workshop.definition().primaryWorkplaceDefinition().socket());
        if (!SettlementStructureFootprint.supportSurfaces(workshop).contains(machine))
            throw new IllegalArgumentException("bakery machine socket has no facility support");
        return new ProductionStationSpec(new SubjectId("station:" + suffix + "-bakery"), workshop.id(),
                new SubjectId("container:" + suffix + "-bakery-station"), ProductionEquipment.BENCH, Capability.BAKING,
                port.workStation(), machine, java.util.stream.IntStream.range(0, 9).boxed().toList(), 9, OptionalInt.of(10), OptionalInt.empty());
    }
}
