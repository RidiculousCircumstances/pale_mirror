package io.farfrontier.palemirror.internal.materialization;

import java.util.ArrayList;
import java.util.List;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.ScenarioInstance;
import io.farfrontier.palemirror.domain.SourceGateStatus;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.SourceGateLayout;
import io.farfrontier.palemirror.internal.adapter.SourceGatePartSpec;
import io.farfrontier.palemirror.internal.content.EncounterDefinitions;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.EncounterRecord;
import io.farfrontier.palemirror.internal.world.EncounterState;
import io.farfrontier.palemirror.internal.observation.MaterializationPostconditionObserved;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.GatePresentationRecord;
import io.farfrontier.palemirror.internal.world.SourceGatePartRef;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Bounded server-thread scheduler: it never force-loads chunks and performs at
 * most one persisted operation per eligible mine on each invocation.
 */
public final class MaterializationScheduler {
    private static final double ACTIVATION_RANGE_SQUARED = 64.0D * 64.0D;

    private final TestMineMaterializer executor = new TestMineMaterializer();
    private final TestMineMaterializationTranslator translator = new TestMineMaterializationTranslator();

    public List<MaterializationPostconditionObserved> schedule(MinecraftServer server, PaleMirrorSavedData data) {
        List<MaterializationPostconditionObserved> observations = new ArrayList<>();
        for (TestMineRecord mine : data.testMines().values()) {
            FacilityState facility = data.worldState().facility(mine.id()).orElse(null);
            if (facility == null) continue;
            ServerLevel level = levelFor(server, mine);
            if (level == null || !level.hasChunkAt(mine.anchor()) || !playerIsNearby(server, level, mine)) continue;

            MaterializationJob job = data.materializationJobs().activeFor(mine.id().value(), "threat").orElse(null);
            ScenarioInstance scenario = selectedEncounterScenario(data, mine);
            EncounterProfile profile = encounterProfile(scenario);
            boolean encounterEnabled = scenario != null;
            MaterializationPlan plan = translator.translate(facility, profile, mine.encounter(), mine.gate(), encounterEnabled);
            if (job == null || !job.isFor(facility.desiredRevision(), plan.policyId(), plan.policyVersion())
                    || !job.matchesOperations(plan.operations())) {
                String planIdentity = plan.policyId() + "|" + plan.policyVersion() + "|"
                        + plan.operations().stream().map(MaterializationOperation::idempotencyKey)
                        .collect(java.util.stream.Collectors.joining("|"));
                String jobId = "pm:job:" + mine.id().value() + ":" + facility.desiredRevision() + ":"
                        + Integer.toUnsignedString(planIdentity.hashCode(), 36);
                if (encounterEnabled) {
                    prepareEncounter(mine, facility, jobId, scenario, profile, server.overworld().getSeed());
                    prepareSourceGate(mine, facility);
                }
                plan = translator.translate(facility, profile, mine.encounter(), mine.gate(), encounterEnabled);
                if (job != null) job.cancel("Superseded by desired revision " + facility.desiredRevision());
                data.materializationJobs().put(new MaterializationJob(jobId, mine.id().value(), "threat",
                        MaterializationJobClass.CAPABILITY, facility.desiredRevision(), plan.policyId(), plan.policyVersion(), JobState.PLANNED,
                        plan.operations(), 0, 0, ""));
                data.setDirty();
                continue;
            }
            boolean wasCompleted = job.state() == JobState.COMPLETED;
            boolean completed = executor.executeNext(level, data, mine, facility, job);
            data.setDirty();
            if (!wasCompleted && completed) observations.add(new MaterializationPostconditionObserved(
                    "materialization:" + job.jobId(), mine.id(), job.desiredRevision(), job.jobId()));
        }
        return List.copyOf(observations);
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord mine) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(mine.dimensionId())) return level;
        }
        return null;
    }

    private static boolean playerIsNearby(MinecraftServer server, ServerLevel level, TestMineRecord mine) {
        return server.getPlayerList().getPlayers().stream().anyMatch(player -> player.serverLevel() == level
                && player.distanceToSqr(mine.anchor().getX() + 0.5D, mine.anchor().getY() + 0.5D,
                mine.anchor().getZ() + 0.5D) <= ACTIVATION_RANGE_SQUARED);
    }

    private static ScenarioInstance selectedEncounterScenario(PaleMirrorSavedData data, TestMineRecord mine) {
        return data.worldState().scenarios().stream().filter(value -> value.target().equals(mine.id())
                && value.status() == ScenarioStatus.RECOVER).findFirst().orElseGet(() ->
                data.worldState().scenarios().stream().filter(value ->
                        value.archetype() == ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS
                                && value.status() == ScenarioStatus.RESPOND
                                && data.worldState().livingRegions().stream().anyMatch(region ->
                                region.primaryFacilityId().equals(mine.id())
                                        && region.communityId().equals(value.target())))
                        .findFirst().orElse(null));
    }

    private static EncounterProfile encounterProfile(ScenarioInstance scenario) {
        if (scenario == null || scenario.encounterProfileId().isBlank()
                || "unavailable".equals(scenario.encounterProfileVersion())) return null;
        EncounterProfile profile = EncounterDefinitions.current().get(net.minecraft.resources.ResourceLocation.parse(scenario.encounterProfileId()));
        return profile != null && Integer.toString(profile.version()).equals(scenario.encounterProfileVersion()) ? profile : null;
    }

    private static void prepareEncounter(TestMineRecord mine, FacilityState facility, String jobId,
                                         ScenarioInstance scenario, EncounterProfile profile, long worldSeed) {
        if (facility.status() != FacilityStatus.INFECTED) return;
        if (scenario == null || scenario.encounterProfileId().isBlank()) {
            mine.setEncounter(EncounterRecord.none());
            return;
        }
        if (profile == null) {
            if (mine.encounter().actors().isEmpty()) {
                mine.setEncounter(new EncounterRecord("", "", jobId, facility.desiredRevision(), List.of(), EncounterState.DEGRADED,
                        "Encounter profile is unavailable or changed after the scenario was offered"));
            } else {
                mine.encounter().degrade("Encounter profile is unavailable or changed after the scenario was offered; "
                        + "existing PM actor references are retained for cleanup");
            }
            return;
        }
        EncounterProfile.Composition composition = profile.selectComposition(facility.threatTier(), worldSeed,
                facility.id().value(), facility.desiredRevision());
        String compositionId = composition == null ? "" : composition.id();
        List<EncounterProfile.ActorSlot> requestedActors = composition == null ? profile.actorsFor(facility.threatTier()) : composition.actors();
        List<EncounterActorRef> actors = requestedActors.stream().map(actor -> {
            EncounterActorRef previous = mine.encounter().actor(actor.id()).orElse(null);
            if (previous != null && previous.actorProfileId().equals(actor.actorProfileId())
                    && (previous.status() == EncounterActorRef.Status.ACTIVE
                    || previous.status() == EncounterActorRef.Status.DEFEATED)) {
                return new EncounterActorRef(actor.id(), actor.actorProfileId(), previous.entityTypeId(), previous.entityId(),
                        previous.status(), previous.nextRuntimeTick(), previous.actionCounter(), previous.combatHitPoints(),
                        previous.nextMovementTick(), previous.routeCursor());
            }
            return new EncounterActorRef(actor.id(), actor.actorProfileId(), "", null, EncounterActorRef.Status.MISSING);
        }).toList();
        mine.setEncounter(new EncounterRecord(profile.id(), Integer.toString(profile.version()), jobId,
                facility.desiredRevision(), compositionId, actors, EncounterState.NONE, ""));
    }

    /** Builds only the current pinned source phase; earlier defeated parts remain durable provenance. */
    private static void prepareSourceGate(TestMineRecord mine, FacilityState facility) {
        if (facility.status() != FacilityStatus.INFECTED || facility.gate().status() != SourceGateStatus.ACTIVE) return;
        SourceGateLayout layout = AdapterRegistry.sourceAdapter(facility.infectionSource()).gateLayout(mine, facility).orElse(null);
        if (layout == null || facility.gate().plan().filter(plan -> plan.id().equals(layout.planId())
                && plan.version().equals(layout.planVersion())).isEmpty()
                || !facility.gate().currentPhase().orElseThrow().requiredPartIds().equals(
                layout.parts().stream().map(SourceGatePartSpec::slotId).toList())) {
            mine.gate().degrade("Source adapter did not provide the exact pinned gate phase layout");
            return;
        }
        List<SourceGatePartRef> parts = new ArrayList<>(mine.gate().parts());
        for (SourceGatePartSpec spec : layout.parts()) {
            SourceGatePartRef previous = mine.gate().part(spec.slotId()).orElse(null);
            SourceGatePartRef next = previous != null && previous.profileId().equals(spec.profileId())
                    && previous.position().equals(spec.position()) ? previous
                    : new SourceGatePartRef(spec.slotId(), spec.profileId(), spec.position(), null, SourceGatePartRef.Status.MISSING);
            if (previous == null) parts.add(next);
            else for (int index = 0; index < parts.size(); index++) if (parts.get(index).slotId().equals(spec.slotId())) {
                parts.set(index, next);
                break;
            }
        }
        mine.setGate(new GatePresentationRecord(layout.planId(), layout.planVersion(), facility.desiredRevision(), parts,
                mine.gate().diagnostic()));
    }
}
