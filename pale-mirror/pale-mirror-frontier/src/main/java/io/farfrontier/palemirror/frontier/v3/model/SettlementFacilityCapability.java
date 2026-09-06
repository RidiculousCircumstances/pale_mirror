package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Pure operational capability derived from the one canonical structure condition.
 *
 * <p>This is deliberately not a second mutable facility register. A settlement building's
 * identity and physical aftermath already belong to the bootstrap/structure-damage model;
 * the capacity available to people is a deterministic consequence of that state. The profile
 * is explicit so economy, AI and the player-facing board use the same limits.</p>
 */
public final class SettlementFacilityCapability {
    private SettlementFacilityCapability() { }

    public static Capability forStructure(FrontierWorldState state, SettlementStructure structure) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(structure, "structure");
        StructureCondition condition = state.structureConditions().get(structure.id());
        if (condition == null) throw new IllegalArgumentException("unknown canonical structure: " + structure.id().value());
        return forCondition(state.bootstrap().ruleset(), structure.kind(), condition);
    }

    public static Capability forCondition(FrontierRuleset ruleset, StructureKind kind, StructureCondition condition) {
        Objects.requireNonNull(ruleset, "ruleset"); Objects.requireNonNull(kind, "structure kind"); Objects.requireNonNull(condition, "structure condition");
        FrontierRuleset.FacilityCapacity capacity = ruleset.facilityCapacity();
        int factor = switch (condition) {
            case INTACT -> 1;
            case DAMAGED -> 0;
            case DESTROYED -> -1;
        };
        if (kind == StructureKind.HOUSING) {
            return new Capability(kind, condition, condition == StructureCondition.INTACT ? capacity.intactHousingBeds()
                    : condition == StructureCondition.DAMAGED ? capacity.damagedHousingBeds() : 0, 0);
        }
        // Work capacity is intentionally a capability, not an assigned worker count. Later
        // allocation processes reserve named residents against it; no building invents people.
        return new Capability(kind, condition, 0, factor < 0 ? 0 : factor == 0 ? capacity.damagedWorkCapacity() : switch (kind) {
            case HALL -> capacity.intactHallWorkCapacity();
            case FARM -> capacity.intactFarmWorkCapacity();
            case WORKSHOP -> capacity.intactWorkshopWorkCapacity();
            case DEPOT -> capacity.intactDepotWorkCapacity();
            case INFIRMARY -> capacity.intactInfirmaryWorkCapacity();
            case HOUSING -> throw new IllegalStateException("housing handled above");
        });
    }

    public static int housingCapacity(FrontierWorldState state, SubjectId settlementId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(settlementId, "settlement id");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        return settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.HOUSING)
                .mapToInt(structure -> forStructure(state, structure).residentCapacity()).sum();
    }

    public static int livingResidents(FrontierWorldState state, SubjectId settlementId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(settlementId, "settlement id");
        return Math.toIntExact(state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE).count());
    }

    public record Capability(StructureKind kind, StructureCondition condition, int residentCapacity, int workCapacity) {
        public Capability {
            Objects.requireNonNull(kind, "structure kind"); Objects.requireNonNull(condition, "structure condition");
            if (residentCapacity < 0 || workCapacity < 0) throw new IllegalArgumentException("facility capability cannot be negative");
            if (kind == StructureKind.HOUSING && workCapacity != 0) throw new IllegalArgumentException("housing has no work capacity");
            if (kind != StructureKind.HOUSING && residentCapacity != 0) throw new IllegalArgumentException("only housing has resident capacity");
        }

        public boolean operational() { return condition != StructureCondition.DESTROYED && (residentCapacity > 0 || workCapacity > 0); }
    }
}
