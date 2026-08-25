package io.farfrontier.palemirror.frontier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class FrontierHarvesterTest {
    @Test
    void harvesterMustCarryFiniteForageBackToAConnectedDigestivePool() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 204_1L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan brood = organ(state, hive.id(), FrontierHiveOrganKind.BROOD_SAC);
        FrontierBioform harvester = harvester(state, hive.id());
        FrontierPoint target = state.harvesterTarget(hive.id(), brood).orElseThrow();
        long beforeOrganic = state.ecology().cell(target.x(), target.z()).organicMass();

        FrontierCommandOutcome launched = commands.execute(state, new FrontierCommand.LaunchHarvester(hive.id(), brood.id(),
                harvester.id(), target, "test:harvester-launch"));
        assertTrue(launched.accepted());
        FrontierHarvesterRun run = state.harvesterRuns().iterator().next();
        assertEquals(FrontierHarvesterRun.State.OUTBOUND, run.state());
        assertEquals(harvester.id(), FrontierSnapshots.snapshot(state).harvesterRuns().getFirst().bioformId(),
                "the immutable projection must retain the real Zombie identity, not an aggregate forage counter");

        commands.execute(state, new FrontierCommand.AdvanceDays(run.outboundDays() + 1, "test:harvester-forage"));
        assertEquals(FrontierHarvesterRun.State.RETURNING, run.state(),
                "the worker must visibly leave the forage cell carrying a canonical payload before income is credited");
        assertTrue(run.cargo() >= 2);
        assertTrue(state.ecology().cell(target.x(), target.z()).organicMass() < beforeOrganic,
                "a harvester must remove finite biological matter from its actual forage cell");
        String receiver = run.receiverOrganId();
        assertTrue(state.hiveOrgan(receiver).orElseThrow().kind() == FrontierHiveOrganKind.DIGESTIVE_POOL
                        || state.hiveOrgan(receiver).orElseThrow().kind() == FrontierHiveOrganKind.CORE,
                "cargo can return only to a connected biological receiver");

        FrontierCommandOutcome returned = commands.execute(state,
                new FrontierCommand.AdvanceDays(run.returnDays(), "test:harvester-return"));
        assertEquals(FrontierHarvesterRun.State.COMPLETED, run.state());
        assertTrue(returned.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.HARVESTER_RETURNED
                        && value.subjectId().equals(run.id())),
                "biomass must enter the hive only through an explicit return event");
        assertTrue(harvester.alive(), "a completed return leaves the same physical worker available at its hive instead of faking a new carrier");
    }

    @Test
    void physicalHarvesterDeathDropsCargoAndCannotProduceALaterReturn() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 204_2L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan brood = organ(state, hive.id(), FrontierHiveOrganKind.BROOD_SAC);
        FrontierBioform harvester = harvester(state, hive.id());
        FrontierPoint target = state.harvesterTarget(hive.id(), brood).orElseThrow();
        assertTrue(commands.execute(state, new FrontierCommand.LaunchHarvester(hive.id(), brood.id(), harvester.id(), target,
                "test:harvester-death-launch")).accepted());
        FrontierHarvesterRun run = state.harvesterRuns().iterator().next();
        commands.execute(state, new FrontierCommand.AdvanceDays(run.outboundDays() + 1, "test:harvester-death-forage"));
        assertEquals(FrontierHarvesterRun.State.RETURNING, run.state());
        long cargo = run.cargo();
        long detritus = state.ecology().cell(run.position().x(), run.position().z()).detritus();

        FrontierCommandOutcome died = commands.execute(state, new FrontierCommand.ApplyObservation(
                new FrontierPhysicalObservation.BioformDeath("test:harvester-physical-death", harvester.id(),
                        harvester.materializationId(), harvester.revision(), "player:test")));

        assertTrue(died.accepted());
        assertEquals(FrontierHarvesterRun.State.ABORTED, run.state(),
                "the current physical Zombie death must abort the cargo contract in the accepting transaction");
        assertEquals(detritus + cargo, state.ecology().cell(run.position().x(), run.position().z()).detritus(),
                "a killed returning worker leaves its payload in finite ecology, not in hidden hive income");
        FrontierCommandOutcome afterDeath = commands.execute(state,
                new FrontierCommand.AdvanceDays(run.returnDays() + 2, "test:harvester-death-after"));
        assertFalse(afterDeath.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.HARVESTER_RETURNED
                        && value.subjectId().equals(run.id())),
                "an aborted carrier must never deliver stale cargo from a later timer");
    }

    @Test
    void receiverDestructionImmediatelyAbortsReturningCargo() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 204_3L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan brood = organ(state, hive.id(), FrontierHiveOrganKind.BROOD_SAC);
        FrontierBioform harvester = harvester(state, hive.id());
        assertTrue(commands.execute(state, new FrontierCommand.LaunchHarvester(hive.id(), brood.id(), harvester.id(),
                state.harvesterTarget(hive.id(), brood).orElseThrow(), "test:receiver-launch")).accepted());
        FrontierHarvesterRun run = state.harvesterRuns().iterator().next();
        commands.execute(state, new FrontierCommand.AdvanceDays(run.outboundDays() + 1, "test:receiver-forage"));
        FrontierHiveOrgan receiver = state.hiveOrgan(run.receiverOrganId()).orElseThrow();
        long detritus = state.ecology().cell(run.position().x(), run.position().z()).detritus();

        FrontierCommandOutcome destroyed = commands.execute(state, new FrontierCommand.ApplyObservation(
                new FrontierPhysicalObservation.HiveOrganDestroyed("test:receiver-destroyed", receiver.id(),
                        receiver.materializationId(), receiver.revision(), "player:test")));

        assertEquals(FrontierHarvesterRun.State.ABORTED, run.state());
        assertEquals(detritus + run.cargo(), state.ecology().cell(run.position().x(), run.position().z()).detritus());
        assertTrue(destroyed.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.HARVESTER_ABORTED
                        && value.subjectId().equals(run.id())),
                "destroying the receiving cube must report the failed return immediately instead of silently rerouting cargo");
    }

    @Test
    void harvesterRejectsExhaustedFullyInfectedTargetsBelowReferenceScore() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 204_4L);
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan brood = organ(state, hive.id(), FrontierHiveOrganKind.BROOD_SAC);
        LinkedHashMap<FrontierPoint, Integer> tissue = new LinkedHashMap<>();
        for (FrontierHiveTissueCell cell : state.hiveTissue()) {
            if (cell.hiveId().equals(hive.id())) tissue.put(cell.position(), cell.strength());
        }
        for (int z = 0; z < state.profile().heightCells(); z++) for (int x = 0; x < state.profile().widthCells(); x++) {
            FrontierPoint point = new FrontierPoint(x, z);
            int distance = Math.max(Math.abs(point.x() - brood.position().x()), Math.abs(point.z() - brood.position().z()));
            if (distance < 2 || distance > 12) continue;
            long organic = state.ecology().cell(x, z).organicMass();
            state.ecology().consume(x, z, organic - 52);
            tissue.put(point, 1_000);
        }
        ArrayList<FrontierHiveTissueCell> saturated = new ArrayList<>();
        tissue.forEach((point, strength) -> saturated.add(new FrontierHiveTissueCell(hive.id(), point, strength)));
        state.restoreHiveTissue(saturated);

        assertTrue(state.harvesterTarget(hive.id(), brood).isEmpty(),
                "the source minimum score must reject low organic cover already saturated by hive tissue");
    }

    private static FrontierHiveOrgan organ(FrontierWorldState state, String hiveId, FrontierHiveOrganKind kind) {
        return state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hiveId)).filter(value -> value.kind() == kind)
                .findFirst().orElseThrow();
    }

    private static FrontierBioform harvester(FrontierWorldState state, String hiveId) {
        return state.bioforms().stream().filter(value -> value.hiveId().equals(hiveId))
                .filter(value -> value.kind() == FrontierBioformKind.HARVESTER).findFirst().orElseThrow();
    }
}
