package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** Pure, player-readable names for an exact HOT actor or cargo batch. */
public final class FrontierSceneLabels {
    private FrontierSceneLabels() { }

    public static String actor(FrontierWorldState state, SubjectId actorId, boolean bioform) {
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        if (resident != null) {
            Settlement settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(resident.settlementId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("resident settlement is missing: " + actorId.value()));
            HumanTacticalFunction tactical = HumanTacticalFunctionProjection.derive(state, resident.id());
            String label = settlement.displayName() + " " + words(tactical == HumanTacticalFunction.CIVILIAN ? resident.profession().name() : tactical.name());
            return SettlementDefenderReadinessProjection.owningActiveAssault(state, resident.id())
                    .map(assault -> label + "\nUNIT " + words(SettlementDefenderReadinessProjection.derive(state, assault).status().name()))
                    .orElse(label);
        }
        return Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> value.id().equals(actorId)).findFirst()
                .map(value -> "HIVE " + words(value.role().name()))
                // Presentation must never turn an internal identity into a player-visible name.
                // Authoritative scene admission/ownership still validates the canonical actor separately.
                .orElse(bioform ? "HIVE BIOFORM" : "FRONTIER RESIDENT");
    }

    /**
     * Ambient civilians keep their identity available but their nameplate quiet. Mobilized
     * residents and hive bioforms are operational information a player must be able to read.
     */
    public static boolean ambientActorNameVisible(FrontierWorldState state, SubjectId actorId, boolean bioform) {
        return bioform || HumanTacticalFunctionProjection.derive(state, actorId) != HumanTacticalFunction.CIVILIAN;
    }

    public static String cargo(FrontierWorldState state, CargoBatch cargo) {
        ExactItemStack stack = cargo.itemIds().stream().map(state.inventory().items()::get).filter(java.util.Objects::nonNull)
                .min(Comparator.comparing(ExactItemStack::id))
                .orElseThrow(() -> new IllegalStateException("cargo has no exact presentable stack: " + cargo.id().value()));
        String material = stack.itemKind().substring(stack.itemKind().indexOf(':') + 1).replace('_', ' ').toUpperCase(Locale.ROOT);
        String owner = state.bootstrap().settlements().stream().filter(settlement -> settlement.id().equals(cargo.ownerId()))
                .map(Settlement::displayName).findFirst().orElse(cargo.ownerId().value().startsWith("hive:") ? "HIVE" : "FRONTIER");
        // A physical carrier is often seen from the side at a distance.  Two short semantic
        // lines make its affiliation and exact visible load legible without creating a HUD,
        // exposing an internal ID, or introducing a second operation object.
        return owner.toUpperCase(Locale.ROOT) + " CARAVAN\n" + material + " ×" + stack.count();
    }

    private static String words(String enumName) { return enumName.replace('_', ' ').toUpperCase(Locale.ROOT); }
}
