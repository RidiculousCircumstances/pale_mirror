package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.EngineStatus;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit Wave 4 exit-gate evidence; excluded from ordinary fast tests by its annual-audit tag. */
@Tag("annual-audit")
class FrontierV3AnnualAutonomyAuditTest {
    private static final long NOMINAL_DAY_TICKS = 24_000L;
    // Population ages use the same 360-day civil year; this is a simulation-year, not a wall-clock benchmark.
    private static final long YEAR_TICKS = Math.multiplyExact(360L, NOMINAL_DAY_TICKS);
    private static final long MID_YEAR_TICKS = YEAR_TICKS / 2L;
    private static final List<Long> SEEDS = List.of(41L, 71L, 31_337L, 9_031_746_258_841_137_206L);
    private static final WorkBudget BUDGET = new WorkBudget(4_096, 4_096);

    @Test
    void twelveSettlementWorldCompletesTheSameAutonomousYearTwiceForFourSeeds() {
        for (long seed : auditedSeeds()) {
            AnnualResult first = run(seed, "a", Optional.empty()); AnnualResult second = run(seed, "b", Optional.empty());
            assertEquals(first, second, "seed " + seed + " must reproduce the same annual canonical result");
            System.out.println("PMV3_ANNUAL_AUDIT " + first);
        }
    }

    @Test
    void annualColdAutonomySurvivesMidYearSnapshotRecoveryWithoutChangingTheCanonicalResult() {
        long seed = 41L;
        AnnualResult uninterrupted = run(seed, "uninterrupted", Optional.empty());
        AnnualResult recovered = run(seed, "recovered", Optional.of(MID_YEAR_TICKS));
        assertEquals(uninterrupted, recovered,
                "a recovered annual COLD run must end at the same canonical state as uninterrupted execution");
        System.out.println("PMV3_ANNUAL_RECOVERY " + recovered);
    }

    /** Allows one full-year seed to be diagnosed locally without weakening the default four-seed gate. */
    private static List<Long> auditedSeeds() {
        String override = System.getProperty("pale_mirror.frontier_v3.annual_audit.seeds");
        if (override == null || override.isBlank()) return SEEDS;
        List<Long> selected = java.util.Arrays.stream(override.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .map(Long::parseLong).distinct().toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("annual audit seed override is empty");
        return selected;
    }

    private static AnnualResult run(long seed, String run, Optional<Long> recoveryAt) {
        WorldId world = new WorldId("frontier:annual-" + seed);
        var configuration = FrontierWorldRuntimeDefinition.configuration(world, seed);
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(configuration);
        long compactedAt = 0L;
        SimInstant current = SimInstant.ZERO;
        boolean recovered = false;
        while (current.ticks() < YEAR_TICKS) {
            long target = nextTarget(engine.nextScheduledInstantAfter(current), YEAR_TICKS);
            var result = engine.advanceTo(new SimInstant(target), BUDGET);
            if (result.status().kind() != EngineStatus.Kind.ACTIVE) {
                throw new AssertionError(annualFailure(seed, engine.checkpoint(), result));
            }
            current = result.instant();
            if (result.revision().value() - compactedAt >= 1_024L) {
                engine.compact(result.revision()); compactedAt = result.revision().value();
            }
            if (!recovered && recoveryAt.isPresent() && current.ticks() >= recoveryAt.orElseThrow()) {
                CheckpointImage beforeRecovery = engine.checkpoint();
                engine = FrontierEngines.recover(configuration, new RecoveryImage(world,
                        Optional.of(new SnapshotRecord(beforeRecovery, beforeRecovery.revision().value())), List.of()));
                CheckpointImage afterRecovery = engine.checkpoint();
                assertEquals(beforeRecovery.revision(), afterRecovery.revision(), "recovery must retain exact canonical revision");
                assertEquals(beforeRecovery.instant(), afterRecovery.instant(), "recovery must retain exact canonical time");
                assertEquals(beforeRecovery.schedules(), afterRecovery.schedules(), "recovery must retain all due autonomous work");
                assertEquals(new FrontierWorldStateCodec().decode(beforeRecovery.canonicalState()),
                        new FrontierWorldStateCodec().decode(afterRecovery.canonicalState()),
                        "recovery must retain exact autonomous canonical state");
                recovered = true;
            }
        }
        assertEquals(recoveryAt.isPresent(), recovered, "the requested annual recovery split must execute exactly once");
        CheckpointImage checkpoint = engine.checkpoint();
        FrontierWorldState state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
        assertEquals(YEAR_TICKS, checkpoint.instant().ticks());
        assertEquals(12, state.bootstrap().settlements().size());
        assertEquals(12, state.resourceSites().sites().size());
        assertTrue(state.resourceSites().sites().values().stream().allMatch(site -> site.growthEpoch() >= 2L), "every settlement farm must have completed a COLD crop cycle");
        assertFalse(checkpoint.schedules().isEmpty(), "recurring autonomous work may not disappear");
        long unresolved = state.physicalIntents().values().stream().filter(intent -> intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED
                || intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING).count();
        assertEquals(0L, unresolved, "unloaded autonomous work may not stall behind a materialization-only physical intent");
        return new AnnualResult(seed, checkpoint.revision().value(), state.humanPopulation().residents().size(), state.bootstrap().hive().bioforms().size() + state.hiveColony().spawnedBioforms().size(),
                state.inventory().items().size(), state.contracts().size(), state.operations().size(), state.infection().size(), digest(checkpoint));
    }

    private static long nextTarget(java.util.Optional<SimInstant> nextDue, long year) {
        return Math.min(nextDue.map(SimInstant::ticks).orElse(year), year);
    }

    private static String annualFailure(long seed, CheckpointImage checkpoint, io.farfrontier.palemirror.frontier.v3.api.AdvanceResult result) {
        FrontierWorldState state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
        String operations = state.operations().values().stream().sorted(java.util.Comparator.comparing(RouteOperation::id)).map(operation -> {
            String actors = operation.participantIds().stream().map(actor -> actor.value() + "=" + state.actorLocations().get(actor).position()).collect(java.util.stream.Collectors.joining(","));
            String travel = operation.activeTravel().map(value -> "cursor=" + value.cursor() + "/" + (value.corridor().size() - 1)
                    + " formation=" + value.formation()).orElse("none");
            return operation.id().value() + "[" + operation.stage() + ",route=" + operation.routeIndex() + "," + travel + ",actors=" + actors + "]";
        }).collect(java.util.stream.Collectors.joining("; "));
        return "seed " + seed + " quarantined at " + result.instant() + ": " + result.status().failureDetail().orElse("no detail")
                + " | operations=" + operations;
    }

    private static String digest(CheckpointImage checkpoint) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256"); hash.update(checkpoint.canonicalState());
            checkpoint.schedules().forEach(action -> hash.update((action.id().value() + ":" + action.dueAt().ticks() + ":" + action.priority()
                    + ":" + action.subject().value() + ":" + action.kind() + ":" + action.weight() + "\n").getBytes(StandardCharsets.UTF_8)));
            return java.util.HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record AnnualResult(long seed, long revision, int residents, int bioforms, int itemStacks, int contracts, int operations,
                                int infectedCells, String stateDigest) { }
}
