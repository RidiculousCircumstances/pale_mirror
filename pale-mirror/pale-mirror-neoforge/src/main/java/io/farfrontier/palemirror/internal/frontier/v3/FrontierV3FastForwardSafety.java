package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.function.Predicate;

/** Prevents test-only canonical time travel from skipping a physically executable v3 effect. */
final class FrontierV3FastForwardSafety {
    private FrontierV3FastForwardSafety() { }

    /**
     * COLD intents outside naturally loaded chunks cannot physically execute, so they do not
     * obstruct operator time. A loaded effect or an active scene stops at its next exact tick.
     */
    static boolean requiresPhysicalStep(ServerLevel level, FrontierWorldState state) {
        Objects.requireNonNull(level, "physical level"); Objects.requireNonNull(state, "frontier state");
        return requiresPhysicalStep(state.physicalIntents().values(), state.sceneLeases().values(), intent -> affectedAreaLoaded(level, intent));
    }

    /** Combines canonical physical work with an executor-owned in-flight physical cursor. */
    static boolean requiresPhysicalStep(boolean canonicalPhysicalStep, boolean ownedProjectionPending) {
        return canonicalPhysicalStep || ownedProjectionPending;
    }

    /** Preserves the canonical blocker while retaining the exact executor cursor when present. */
    static String blockingDescription(String canonicalBlocker, String ownedProjectionBlocker) {
        if (ownedProjectionBlocker == null || ownedProjectionBlocker.isBlank()) return canonicalBlocker;
        if (canonicalBlocker == null || canonicalBlocker.isBlank() || canonicalBlocker.equals("none")) return ownedProjectionBlocker;
        return canonicalBlocker + ";" + ownedProjectionBlocker;
    }

    static boolean requiresPhysicalStep(Collection<PhysicalIntent> intents, Collection<SceneLease> sceneLeases,
                                        Predicate<PhysicalIntent> affectedAreaLoaded) {
        Objects.requireNonNull(intents, "physical intents"); Objects.requireNonNull(sceneLeases, "scene leases");
        Objects.requireNonNull(affectedAreaLoaded, "affected area loader");
        return intents.stream().filter(intent -> canExecutePhysicalEffect(intent, sceneLeases))
                        .anyMatch(affectedAreaLoaded)
                || sceneLeases.stream().anyMatch(lease -> lease.status() == SceneLeaseStatus.PREPARED
                        || lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.DRAINING);
    }

    /**
     * Read-only companion to the admission predicate.  A queued COLD interval must report the
     * exact owned physical boundary which stopped it; a generic busy state is not enough for an
     * operator to distinguish a normal live effect from a stale scene lease.
     */
    static String blockingDescription(ServerLevel level, FrontierWorldState state) {
        Objects.requireNonNull(level, "physical level"); Objects.requireNonNull(state, "frontier state");
        return blockingDescription(state.physicalIntents().values(), state.sceneLeases().values(), intent -> affectedAreaLoaded(level, intent));
    }

    static String blockingDescription(Collection<PhysicalIntent> intents, Collection<SceneLease> sceneLeases,
                                      Predicate<PhysicalIntent> affectedAreaLoaded) {
        Objects.requireNonNull(intents, "physical intents"); Objects.requireNonNull(sceneLeases, "scene leases");
        Objects.requireNonNull(affectedAreaLoaded, "affected area loader");
        String blockedIntents = intents.stream().filter(intent -> canExecutePhysicalEffect(intent, sceneLeases))
                .filter(affectedAreaLoaded).sorted(Comparator.comparing(intent -> intent.id().value()))
                .map(intent -> "intent=" + intent.id().value() + ":" + intent.kind() + ":" + intent.status()).collect(Collectors.joining(","));
        String blockedScenes = sceneLeases.stream().filter(lease -> lease.status() == SceneLeaseStatus.PREPARED
                        || lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.DRAINING)
                .sorted(Comparator.comparing(lease -> lease.id().value()))
                .map(lease -> "scene=" + lease.id().value() + ":" + lease.status()).collect(Collectors.joining(","));
        if (blockedIntents.isBlank() && blockedScenes.isBlank()) return "none";
        return blockedIntents.isBlank() ? blockedScenes : blockedScenes.isBlank() ? blockedIntents : blockedIntents + ";" + blockedScenes;
    }

    /**
     * A PREPARED harvest reserves its immutable job but deliberately has no loaded-world effect:
     * only its later HOT scene may turn it RUNNING and mutate crops/output.  Treating that
     * inactive reservation as an executable effect made a spawn-loaded field permanently block
     * genuine COLD continuation. Every other PREPARED intent remains a physical boundary.
     */
    static boolean canExecutePhysicalEffect(PhysicalIntent intent) {
        Objects.requireNonNull(intent, "physical intent");
        return intent.status() == PhysicalIntentStatus.RUNNING
                || intent.status() == PhysicalIntentStatus.PREPARED && intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST;
    }

    /**
     * A loaded chunk alone never owns a harvest.  A RUNNING harvest can require physical work
     * only while its exact typed scene is still active; after that scene's fenced release the
     * same intent is COLD-owned even during Minecraft's ordinary post-player chunk grace.
     */
    private static boolean canExecutePhysicalEffect(PhysicalIntent intent, Collection<SceneLease> sceneLeases) {
        if (!canExecutePhysicalEffect(intent)) return false;
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST || intent.status() != PhysicalIntentStatus.RUNNING) return true;
        return sceneLeases.stream().filter(lease -> lease.status() == SceneLeaseStatus.PREPARED
                        || lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.DRAINING)
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .map(FrontierSceneBehaviors::resourceSiteHarvest)
                .anyMatch(cause -> intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.JOB).equals(cause.jobId()));
    }

    private static boolean affectedAreaLoaded(ServerLevel level, PhysicalIntent intent) {
        int x = fixedBlock(intent.origin().x()); int y = fixedBlock(intent.origin().y()); int z = fixedBlock(intent.origin().z());
        int radius = intent.radiusBlocks();
        for (int currentX = x - radius; currentX <= x + radius; currentX = nextChunkEdge(currentX)) {
            for (int currentZ = z - radius; currentZ <= z + radius; currentZ = nextChunkEdge(currentZ)) {
                if (level.hasChunkAt(new BlockPos(currentX, y, currentZ))) return true;
            }
        }
        return false;
    }

    private static int fixedBlock(FixedScalar value) {
        return Math.toIntExact(Math.floorDiv(value.raw(), FixedScalar.SCALE));
    }

    private static int nextChunkEdge(int coordinate) {
        return Math.addExact(coordinate, 16 - Math.floorMod(coordinate, 16));
    }
}
