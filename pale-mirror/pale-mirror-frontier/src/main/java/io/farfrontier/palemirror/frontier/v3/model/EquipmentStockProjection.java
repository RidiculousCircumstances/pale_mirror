package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Read-only equipment coverage includes retained production promises exactly once. Inventory owns reality. */
public final class EquipmentStockProjection {
    public record Unit(SubjectId id, String kind, ItemCondition condition) { }
    public record Deficit(EquipmentWorkDemand demand, int missingUnits, long missingWear) {
        public boolean immediate() { return missingUnits > 0; }
    }
    public static List<Unit> units(FrontierWorldState state, SubjectId home) {
        var units = new TreeMap<SubjectId, Unit>(); var catalog = state.bootstrap().ruleset().tools();
        var sold = state.companies().goodsTrade().contracts().values().stream()
                .filter(c -> !c.terminal() && c.seller().id().equals(home))
                .flatMap(c -> c.individualAllocations().values().stream()).map(IndividualResourceAllocation::itemId)
                .collect(java.util.stream.Collectors.toSet());
        for (var item : state.inventory().items().values()) {
            if (!item.economicOwnerId().equals(home) || sold.contains(item.id()) || catalog.find(item.itemKind()).isEmpty()) continue;
            boolean available = switch (item.custody()) {
                case InventoryCustody.ContainerSlot slot -> {
                    var record = state.inventory().containers().get(slot.containerId());
                    yield record != null && record.ownerId().equals(home)
                            && state.inventory().surfaces().get(record.id()).status() != ContainerSurfaceStatus.RETIRED
                            && state.inventory().surfaces().get(record.id()).status() != ContainerSurfaceStatus.CONFLICT;
                }
                case InventoryCustody.Actor actor -> state.humanPopulation().residents().containsKey(actor.actorId())
                        && state.humanPopulation().resident(actor.actorId()).settlementId().equals(home)
                        && state.actorLocations().get(actor.actorId()).condition().status() == ActorLifeStatus.ALIVE;
                default -> false;
            };
            if (available) units.put(item.id(), new Unit(item.id(), item.itemKind(), item.condition()));
        }
        for (var job : state.productionJobs().values()) {
            if (!job.rights().resourceOwner().id().equals(home) || job.stationWork().isEmpty()
                    || job.stationWork().orElseThrow().phase() == StationWorkState.Phase.DELIVERED) continue;
            var definition = catalog.find(job.outputItemKind()); if (definition.isEmpty()) continue;
            var recipe = state.bootstrap().ruleset().production().require(job.stationWork().orElseThrow().recipeId());
            if (recipe.outcome() instanceof ProductionRecipeOutcome.Repair repair) {
                var item = state.inventory().items().get(job.outputItemId());
                if (item != null) units.put(item.id(), new Unit(item.id(), item.itemKind(), StationWorkCargo.output(job)
                        ? item.condition() : item.condition().restore(Math.min(item.condition().damage(), repair.restoredDurability()))));
            } else if (recipe.outcome() instanceof ProductionRecipeOutcome.Individual)
                units.put(job.outputItemId(), new Unit(job.outputItemId(), job.outputItemKind(), definition.orElseThrow().pristine()));
        }
        for (var contract : state.companies().goodsTrade().contracts().values()) {
            if (contract.terminal() || !contract.buyer().id().equals(home)) continue;
            for (var allocation : contract.individualAllocations().values())
                if (catalog.find(allocation.itemKind()).isPresent()) units.put(allocation.itemId(),
                        new Unit(allocation.itemId(), allocation.itemKind(), allocation.condition()));
        }
        for (var shipment : state.shipments().shipments().values()) {
            if (shipment.authorization().kind() != ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT
                    || !(shipment.cargo() instanceof ShipmentCargo.Individual item) || !item.ownerId().equals(home)
                    || shipment.terminal() && shipment.reception().isEmpty() || catalog.find(item.itemKind()).isEmpty()) continue;
            units.put(item.itemId(), new Unit(item.itemId(), item.itemKind(), item.condition()));
        }
        return List.copyOf(units.values());
    }
    /** Select the actual productive units to retain. Market quality and storage filtering remain in the trade adapter. */
    public static Set<SubjectId> retainedItems(FrontierWorldState state, SubjectId home) {
        var retained = new HashSet<SubjectId>(); var catalog = state.bootstrap().ruleset().tools();
        var units = state.inventory().items().values().stream()
                .filter(item -> item.economicOwnerId().equals(home) && catalog.find(item.itemKind()).isPresent())
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && state.inventory().containers().get(slot.containerId()).ownerId().equals(home)
                        && state.inventory().surfaces().get(slot.containerId()).status() != ContainerSurfaceStatus.RETIRED
                        && state.inventory().surfaces().get(slot.containerId()).status() != ContainerSurfaceStatus.CONFLICT
                        || item.custody() instanceof InventoryCustody.Actor actor && state.humanPopulation().residents().containsKey(actor.actorId())
                            && state.humanPopulation().resident(actor.actorId()).settlementId().equals(home)
                            && state.actorLocations().get(actor.actorId()).condition().status() == ActorLifeStatus.ALIVE)
                .map(item -> new Unit(item.id(), item.itemKind(), item.condition())).sorted(Comparator
                .comparing((Unit unit) -> state.inventory().items().containsKey(unit.id())
                        && state.inventory().items().get(unit.id()).custody() instanceof InventoryCustody.ContainerSlot)
                .thenComparing(Comparator.comparingInt((Unit unit) -> unit.condition().remaining()).reversed())
                .thenComparing(Unit::id)).toList();
        for (var demand : EquipmentDemandComposition.read(state, home)) {
            int count = 0; long wear = 0;
            for (var unit : units) {
                if (count >= demand.concurrentUnits() && wear >= demand.plannedWear()) break;
                long usable = demand.usableWear(catalog.require(unit.kind()), unit.condition());
                if (usable == 0 || retained.contains(unit.id())) continue;
                retained.add(unit.id()); count++; wear = Math.addExact(wear, usable);
            }
        }
        return Set.copyOf(retained);
    }
    public static List<Deficit> deficits(FrontierWorldState state, SubjectId home) {
        var catalog = state.bootstrap().ruleset().tools(); var units = units(state, home); var used = new HashSet<SubjectId>();
        var result = new ArrayList<Deficit>();
        for (var demand : EquipmentDemandComposition.read(state, home)) {
            int count = 0; long wear = 0;
            for (var unit : units) {
                if (count >= demand.concurrentUnits() && wear >= demand.plannedWear()) break;
                var definition = catalog.require(unit.kind());
                long remaining = demand.usableWear(definition, unit.condition());
                if (remaining == 0 || used.contains(unit.id())) continue;
                used.add(unit.id()); count++; wear = Math.addExact(wear, remaining);
            }
            int missingUnits = Math.max(0, demand.concurrentUnits() - count);
            long missingWear = Math.max(0, demand.plannedWear() - wear);
            if (missingUnits > 0 || missingWear > 0) result.add(new Deficit(demand, missingUnits, missingWear));
        }
        return List.copyOf(result);
    }
    private EquipmentStockProjection() { }
}
