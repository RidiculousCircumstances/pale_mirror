package io.farfrontier.palemirror.frontier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrontierSporeAndAdaptationTest {
    @Test
    void carrierCreatesAReadableLatentColonyThenMaturesIntoARealCore() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 330_1L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan sporulator = organ(state, hive.id(), FrontierHiveOrganKind.SPORULATOR);
        FrontierBioform carrier = carrier(state, hive.id());
        FrontierPoint target = state.propagations().targetFor(state, hive.id(), sporulator).orElseThrow();

        FrontierCommandOutcome launched = commands.execute(state, new FrontierCommand.LaunchPropagationRun(hive.id(), sporulator.id(),
                carrier.id(), target, "test:spore-launch"));
        assertTrue(launched.accepted());
        FrontierPropagationRun run = state.propagationRuns().iterator().next();
        assertEquals(carrier.id(), FrontierSnapshots.snapshot(state).propagationRuns().getFirst().bioformId(),
                "the projection must retain the actual physical carrier, not an aggregate infection counter");

        FrontierCommandOutcome deployed = commands.execute(state,
                new FrontierCommand.AdvanceDays(run.transitDays(), "test:spore-deploy"));
        assertEquals(FrontierPropagationRun.State.DEPLOYED, run.state());
        assertFalse(carrier.alive(), "delivery consumes the same carrier rather than inventing a hidden remote infection");
        FrontierLatentColony colony = state.latentColonies().stream().filter(value -> value.id().equals(run.latentColonyId()))
                .findFirst().orElseThrow();
        assertEquals(target, colony.position());
        assertTrue(deployed.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.PROPAGATION_RUN_DEPLOYED
                        && value.subjectId().equals(colony.id())),
                "arrival must be an explicit materializable lifecycle event");
        assertTrue(FrontierSnapshots.snapshot(state).latentColonies().stream().anyMatch(value -> value.id().equals(colony.id())),
                "the exposed latent colony gives a tester one full simulation day to inspect or clear it");

        FrontierCommandOutcome matured = commands.execute(state, new FrontierCommand.AdvanceDays(1, "test:spore-mature"));
        assertTrue(state.latentColonies().stream().noneMatch(value -> value.id().equals(colony.id())));
        assertTrue(state.hiveOrgans().stream().anyMatch(value -> value.hiveId().equals(hive.id())
                        && value.kind() == FrontierHiveOrganKind.CORE && value.position().equals(target)
                        && value.state() == FrontierHiveOrgan.State.ALIVE),
                "only mature, still-present infection may create a canonical Core at its announced location");
        assertTrue(matured.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.LATENT_COLONY_MATURED
                        && value.subjectId().equals(colony.id())));
    }

    @Test
    void physicalCarrierDeathAbortsTheMissionAndCannotDepositLater() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 330_2L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan sporulator = organ(state, hive.id(), FrontierHiveOrganKind.SPORULATOR);
        FrontierBioform carrier = carrier(state, hive.id());
        assertTrue(commands.execute(state, new FrontierCommand.LaunchPropagationRun(hive.id(), sporulator.id(), carrier.id(),
                state.propagations().targetFor(state, hive.id(), sporulator).orElseThrow(), "test:spore-death-launch")).accepted());
        FrontierPropagationRun run = state.propagationRuns().iterator().next();
        commands.execute(state, new FrontierCommand.AdvanceDays(1, "test:spore-death-progress"));

        FrontierCommandOutcome died = commands.execute(state, new FrontierCommand.ApplyObservation(
                new FrontierPhysicalObservation.BioformDeath("test:spore-carrier-death", carrier.id(),
                        carrier.materializationId(), carrier.revision(), "player:test")));
        assertTrue(died.accepted());
        assertEquals(FrontierPropagationRun.State.ABORTED, run.state(),
                "a real player kill must terminate the exact outbound carrier transaction immediately");
        assertTrue(died.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.PROPAGATION_RUN_ABORTED
                        && value.subjectId().equals(run.id())));

        FrontierCommandOutcome afterDeath = commands.execute(state,
                new FrontierCommand.AdvanceDays(run.transitDays() + 2, "test:spore-death-after"));
        assertTrue(state.latentColonies().isEmpty(), "an aborted carrier must not leave a delayed hidden colony");
        assertFalse(afterDeath.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.PROPAGATION_RUN_DEPLOYED
                        && value.subjectId().equals(run.id())));
    }

    @Test
    void playerClearingTheVisibleColonyPreventsAStaleCoreFromAppearing() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 330_3L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan sporulator = organ(state, hive.id(), FrontierHiveOrganKind.SPORULATOR);
        FrontierBioform carrier = carrier(state, hive.id());
        assertTrue(commands.execute(state, new FrontierCommand.LaunchPropagationRun(hive.id(), sporulator.id(), carrier.id(),
                state.propagations().targetFor(state, hive.id(), sporulator).orElseThrow(), "test:colony-launch")).accepted());
        FrontierPropagationRun run = state.propagationRuns().iterator().next();
        commands.execute(state, new FrontierCommand.AdvanceDays(run.transitDays(), "test:colony-deploy"));
        FrontierLatentColony colony = state.latentColonies().stream().filter(value -> value.id().equals(run.latentColonyId()))
                .findFirst().orElseThrow();

        FrontierCommandOutcome cleared = commands.execute(state, new FrontierCommand.ApplyObservation(
                new FrontierPhysicalObservation.LatentColonyCleared("test:colony-cleared", colony.id(),
                        colony.materializationId(), colony.revision(), "player:test")));
        assertTrue(cleared.accepted());
        assertTrue(cleared.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.LATENT_COLONY_CLEARED
                        && value.subjectId().equals(colony.id())));
        commands.execute(state, new FrontierCommand.AdvanceDays(2, "test:colony-cleared-after"));
        assertTrue(state.latentColonies().isEmpty());
        assertFalse(state.hiveOrgans().stream().anyMatch(value -> value.hiveId().equals(hive.id())
                        && value.kind() == FrontierHiveOrganKind.CORE && value.position().equals(colony.position())
                        && value.bornDay() > 0),
                "a removed blue colony cube must never turn into a Core after the player has cleared it");
    }

    @Test
    void accumulatedPhysicalPressureSpendsFiniteGenesOnADeterministicAdaptation() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 330_4L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierHive hive = state.hives().iterator().next();
        hive.addGeneticMaterial(30_000L);
        FrontierBioform bioform = state.bioforms().stream().filter(value -> value.hiveId().equals(hive.id())).findFirst().orElseThrow();
        assertTrue(commands.execute(state, new FrontierCommand.ApplyObservation(new FrontierPhysicalObservation.BioformDeath(
                "test:adaptation-combat", bioform.id(), bioform.materializationId(), bioform.revision(), "player:test"))).accepted());

        FrontierCommandOutcome outcome = commands.execute(state, new FrontierCommand.AdvanceDays(10, "test:adaptation-cadence"));
        assertEquals(1, state.adaptations().get(FrontierAdaptation.ARMORED_CARAPACE));
        assertTrue(hive.geneticMaterial() < 30_000L, "adaptation must pay from finite harvested genes, never mint a free trait");
        assertTrue(outcome.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.ADAPTATION_ACQUIRED
                        && value.subjectId().equals(FrontierAdaptation.ARMORED_CARAPACE.name())));
    }

    private static FrontierHiveOrgan organ(FrontierWorldState state, String hiveId, FrontierHiveOrganKind kind) {
        return state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hiveId)).filter(value -> value.kind() == kind)
                .findFirst().orElseThrow();
    }

    private static FrontierBioform carrier(FrontierWorldState state, String hiveId) {
        return state.bioforms().stream().filter(value -> value.hiveId().equals(hiveId))
                .filter(value -> value.kind() == FrontierBioformKind.PROPAGULE_CARRIER).findFirst().orElseThrow();
    }
}
