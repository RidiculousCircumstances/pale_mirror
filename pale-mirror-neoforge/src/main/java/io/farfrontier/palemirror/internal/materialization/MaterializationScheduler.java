package io.farfrontier.palemirror.internal.materialization;

import java.util.ArrayList;
import java.util.List;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.ScenarioInstance;
import io.farfrontier.palemirror.internal.content.EncounterDefinitions;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.EncounterRecord;
import io.farfrontier.palemirror.internal.world.EncounterState;
import io.farfrontier.palemirror.internal.observation.MaterializationPostconditionObserved;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
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
            if (facility == null || !eligibleForMaterialization(data, mine, facility)) continue;
            ServerLevel level = levelFor(server, mine);
            if (level == null || !level.hasChunkAt(mine.anchor()) || !playerIsNearby(server, level, mine)) continue;

            MaterializationJob job = mine.job();
            ScenarioInstance scenario = selectedEncounterScenario(data, mine);
            EncounterProfile profile = encounterProfile(scenario);
            MaterializationPlan plan = translator.translate(facility, profile, mine.encounter());
            if (job == null || !job.isFor(facility.desiredRevision(), plan.policyId(), plan.policyVersion())) {
                String jobId = "pm:job:" + mine.id().value() + ":" + facility.desiredRevision();
                prepareEncounter(mine, facility, jobId, scenario, profile);
                plan = translator.translate(facility, profile, mine.encounter());
                mine.setJob(new MaterializationJob(jobId,
                        facility.desiredRevision(), plan.policyId(), plan.policyVersion(), JobState.PLANNED,
                        plan.operations(), 0, 0, ""));
                data.setDirty();
                continue;
            }
            boolean wasCompleted = job.state() == JobState.COMPLETED;
            boolean completed = executor.executeNext(level, mine, job);
            data.setDirty();
            if (!wasCompleted && completed) observations.add(new MaterializationPostconditionObserved(
                    "materialization:" + job.jobId(), mine.id(), job.desiredRevision(), job.jobId()));
        }
        return List.copyOf(observations);
    }

    private static boolean eligibleForMaterialization(PaleMirrorSavedData data, TestMineRecord mine, FacilityState facility) {
        return facility.status() != FacilityStatus.INFECTED || data.worldState().scenarios().stream().anyMatch(scenario ->
                scenario.target().equals(mine.id()) && scenario.status() == ScenarioStatus.RECOVER);
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
                && value.status() == ScenarioStatus.RECOVER).findFirst().orElse(null);
    }

    private static EncounterProfile encounterProfile(ScenarioInstance scenario) {
        if (scenario == null || scenario.encounterProfileId().isBlank()
                || "unavailable".equals(scenario.encounterProfileVersion())) return null;
        EncounterProfile profile = EncounterDefinitions.current().get(net.minecraft.resources.ResourceLocation.parse(scenario.encounterProfileId()));
        return profile != null && Integer.toString(profile.version()).equals(scenario.encounterProfileVersion()) ? profile : null;
    }

    private static void prepareEncounter(TestMineRecord mine, FacilityState facility, String jobId,
                                         ScenarioInstance scenario, EncounterProfile profile) {
        if (facility.status() != FacilityStatus.INFECTED) return;
        if (scenario == null || scenario.encounterProfileId().isBlank()) {
            mine.setEncounter(EncounterRecord.none());
            return;
        }
        if (profile == null) {
            mine.setEncounter(new EncounterRecord("", "", jobId, facility.desiredRevision(), List.of(), EncounterState.DEGRADED,
                    "Encounter profile is unavailable or changed after the scenario was offered"));
            return;
        }
        List<EncounterActorRef> actors = profile.actors().stream().map(actor -> new EncounterActorRef(actor.id(),
                actor.entityType().toString(), null, EncounterActorRef.Status.MISSING)).toList();
        mine.setEncounter(new EncounterRecord(profile.id(), Integer.toString(profile.version()), jobId,
                facility.desiredRevision(), actors, EncounterState.NONE, ""));
    }
}
