package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** Pure, player-readable names for an exact HOT actor or cargo batch. */
public final class FrontierSceneLabels {
    private FrontierSceneLabels() { }

    public static String actor(FrontierWorldState state, SubjectId actorId, boolean bioform) {
        var declaration = state.actorLocations().get(actorId);
        if (declaration != null && declaration.kind() == ActorKind.PACK_ANIMAL) {
            var asset = state.transportFleet().require(actorId);
            return FrontierWorldStateSupport.settlement(state.bootstrap(), asset.homeSettlementId()).displayName()
                    + " PACK DONKEY";
        }
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        if (resident != null) {
            Settlement settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(resident.settlementId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("resident settlement is missing: " + actorId.value()));
            String label = resident.name();
            return SettlementDefenderReadinessProjection.owningActiveAssault(state, resident.id())
                    .map(assault -> label + "\nUNIT " + words(SettlementDefenderReadinessProjection.derive(state, assault).status().name()))
                    .orElse(label);
        }
        return Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> value.id().equals(actorId)).findFirst()
                .map(value -> "HIVE " + words(value.chassis().name()) + " · " + words(value.assignment().name())
                        + (value.mutations().isEmpty() ? "" : " · " + words(value.mutations().stream().sorted(java.util.Comparator.comparing(Enum::name)).findFirst().orElseThrow().name())))
                // Presentation must never turn an internal identity into a player-visible name.
                // Authoritative scene admission/ownership still validates the canonical actor separately.
                .orElse(bioform ? "HIVE BIOFORM" : "FRONTIER RESIDENT");
    }

    /**
     * Idle civilians keep their identity available but their nameplate quiet. Active actors
     * remain legible independently of which presentation scope currently observes their body.
     */
    public static boolean ambientActorNameVisible(FrontierWorldState state, SubjectId actorId, boolean bioform) {
        var declaration = state.actorLocations().get(actorId);
        if (declaration != null && declaration.kind() == ActorKind.PACK_ANIMAL) return true;
        var execution = state.actorExecutions().actors().get(actorId);
        boolean active = execution != null && execution.current().filter(id ->
                id.activityKind() != io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE).isPresent();
        return active || bioform || HumanTacticalFunctionProjection.derive(state, actorId) != HumanTacticalFunction.CIVILIAN;
    }

    public static String cargo(FrontierWorldState state, CargoBatch cargo) {
        CargoPresentation cargoPresentation = cargo.fungibleContents() ? fungibleCargo(state, cargo) : exactCargo(state, cargo);
        String owner = state.bootstrap().settlements().stream().filter(settlement -> settlement.id().equals(cargo.ownerId()))
                .map(Settlement::displayName).findFirst().orElse(cargo.ownerId().value().startsWith("hive:") ? "HIVE" : "FRONTIER");
        // A physical carrier is often seen from the side at a distance.  Two short semantic
        // lines make its affiliation and exact visible load legible without creating a HUD,
        // exposing an internal ID, or introducing a second operation object.
        return owner.toUpperCase(Locale.ROOT) + " CARAVAN\n" + cargoPresentation.material() + " ×" + cargoPresentation.quantity();
    }

    private static CargoPresentation exactCargo(FrontierWorldState state, CargoBatch cargo) {
        ExactItemStack stack = cargo.itemIds().stream().map(state.inventory().items()::get).filter(java.util.Objects::nonNull)
                .min(Comparator.comparing(ExactItemStack::id))
                .orElseThrow(() -> new IllegalStateException("cargo has no exact presentable stack: " + cargo.id().value()));
        return new CargoPresentation(material(stack.itemKind()), stack.count());
    }

    private static CargoPresentation fungibleCargo(FrontierWorldState state, CargoBatch cargo) {
        CustodyAccount account = state.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody() instanceof ResourceCustody.Cargo carried && carried.cargoId().equals(cargo.id()))
                .findFirst().orElse(null);
        if (account == null || account.lotQuantities().isEmpty()) {
            throw new IllegalStateException("cargo has no presentable fungible account: " + cargo.id().value());
        }
        var lots = account.lotQuantities().keySet().stream().map(state.inventory().fungibleResources().lots()::get)
                .filter(java.util.Objects::nonNull).toList();
        String itemKind = lots.stream().map(ResourceLot::itemKind).distinct().reduce((left, right) -> "").orElse("");
        if (itemKind.isEmpty()) throw new IllegalStateException("cargo has mixed or missing fungible presentation lots: " + cargo.id().value());
        return new CargoPresentation(material(itemKind), account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum());
    }

    private static String material(String itemKind) {
        return itemKind.substring(itemKind.indexOf(':') + 1).replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private record CargoPresentation(String material, int quantity) { }

    private static String words(String enumName) { return enumName.replace('_', ' ').toUpperCase(Locale.ROOT); }
}
