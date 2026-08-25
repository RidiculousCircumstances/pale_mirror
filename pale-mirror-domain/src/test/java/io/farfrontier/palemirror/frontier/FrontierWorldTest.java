package io.farfrontier.palemirror.frontier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrontierWorldTest {
    @Test
    void grayboxProfileHasAOneToOnePopulationAndChunkAlignedKilometreWorld() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 9031746258841137206L);

        assertEquals(1_024, state.profile().widthBlocks());
        assertEquals(1_024, state.profile().heightBlocks());
        assertEquals(10, state.settlements().size());
        assertEquals(state.residents().size(), state.residents().stream().map(FrontierResident::id).distinct().count());
        assertTrue(state.settlements().stream().allMatch(value -> state.alivePopulation(value.id()) >= 20
                && state.alivePopulation(value.id()) <= 32));
        assertTrue(state.settlements().stream().allMatch(value -> value.facilityIds().size() == FrontierFacilityKind.values().length));
        assertEquals(state.facilities().size(), state.operations().size());
        assertTrue(state.operations().stream().allMatch(value -> value.state() == FrontierOperation.State.RUNNING));
        assertTrue(state.settlements().stream().allMatch(value -> value.residentIds().size() == state.alivePopulation(value.id())));
    }

    @Test
    void referenceProfileRetainsItsTwelveSettlementGeometry() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.REFERENCE_64X44, 7L);

        assertEquals(12, state.settlements().size());
        assertEquals(12, state.settlements().stream().map(FrontierSettlement::center).distinct().count());
    }

    @Test
    void confirmedVillagerDeathChangesExactlyOneCanonicalPersonAndCannotReplay() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 41L);
        FrontierResident resident = state.residents().iterator().next();
        int before = state.alivePopulation(resident.settlementId());
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierPhysicalObservation.ResidentDeath death = new FrontierPhysicalObservation.ResidentDeath("death:1", resident.id(),
                resident.materializationId(), resident.revision(), "player:test");

        assertTrue(commands.execute(state, new FrontierCommand.ApplyObservation(death)).accepted());
        assertFalse(resident.alive());
        assertEquals(before - 1, state.alivePopulation(resident.settlementId()));
        assertFalse(commands.execute(state, new FrontierCommand.ApplyObservation(death)).accepted());
        assertEquals(before - 1, state.alivePopulation(resident.settlementId()));
    }

    @Test
    void staleOrForeignPhysicalFactFailsClosedWithoutDamagingTheFacility() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 42L);
        FrontierFacility facility = state.facilities().iterator().next();
        FrontierCommandProcessor commands = new FrontierCommandProcessor();

        FrontierCommandOutcome outcome = commands.execute(state, new FrontierCommand.ApplyObservation(
                new FrontierPhysicalObservation.FacilityDamage("damage:foreign", facility.id(), "foreign", 0, "player:test")));

        assertFalse(outcome.accepted());
        assertEquals(FrontierFacility.State.OPERATIONAL, facility.state());
        assertEquals(FrontierEvent.Type.OBSERVATION_REJECTED, outcome.events().getFirst().type());
    }

    @Test
    void identicalSeedProducesStableWorldIdentityAndDailyEventOrder() {
        FrontierWorldState left = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 99L);
        FrontierWorldState right = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 99L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(left, new FrontierCommand.AdvanceDays(3, "test:advance"));
        commands.execute(right, new FrontierCommand.AdvanceDays(3, "test:advance"));

        assertEquals(left.settlements().stream().map(FrontierSettlement::name).toList(),
                right.settlements().stream().map(FrontierSettlement::name).toList());
        assertEquals(left.residents().stream().map(FrontierResident::id).toList(),
                right.residents().stream().map(FrontierResident::id).toList());
        assertEquals(left.events(), right.events());
    }

    @Test
    void snapshotIsImmutableAndCannotBecomeASecondSourceOfTruth() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 100L);
        FrontierSnapshot before = FrontierSnapshots.snapshot(state);
        FrontierResident resident = state.residents().iterator().next();
        new FrontierCommandProcessor().execute(state, new FrontierCommand.ApplyObservation(
                new FrontierPhysicalObservation.ResidentDeath("death:snapshot", resident.id(), resident.materializationId(),
                        resident.revision(), "player:test")));

        assertTrue(before.residents().stream().filter(value -> value.id().equals(resident.id())).findFirst().orElseThrow().alive());
        assertFalse(FrontierSnapshots.snapshot(state).residents().stream().filter(value -> value.id().equals(resident.id()))
                .findFirst().orElseThrow().alive());
    }

    @Test
    void hydrationPreservesThePhysicalDeduplicationBoundaryAfterRestart() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 101L);
        FrontierResident resident = state.residents().iterator().next();
        FrontierPhysicalObservation.ResidentDeath death = new FrontierPhysicalObservation.ResidentDeath("death:restart", resident.id(),
                resident.materializationId(), resident.revision(), "player:test");
        new FrontierCommandProcessor().execute(state, new FrontierCommand.ApplyObservation(death));

        FrontierWorldState restored = FrontierStateHydration.restore(state.profile(), state.seed(), state.day(),
                state.residents().stream().map(value -> new FrontierStateHydration.ResidentState(value.id(), value.alive(), value.revision())).toList(),
                state.facilities().stream().map(value -> new FrontierStateHydration.FacilityState(value.id(), value.state(), value.revision())).toList(),
                state.settlements().stream().map(value -> new FrontierStateHydration.SettlementState(value.id(), value.stocks(), value.netCredit())).toList(),
                state.operations().stream().map(value -> new FrontierStateHydration.OperationState(value.id(), value.state(), value.revision())).toList(),
                state.cargo().stream().map(value -> new FrontierStateHydration.CargoState(value.id(), value.routeId(),
                        value.sourceSettlementId(), value.destinationSettlementId(), value.resource(), value.amount(),
                        value.dispatchedDay(), value.revision())).toList(),
                state.hives().stream().map(value -> new FrontierStateHydration.HiveState(value.id(), value.biomass(),
                        value.state(), value.revision())).toList(),
                state.hiveOrgans().stream().map(value -> new FrontierStateHydration.HiveOrganState(value.id(), value.state(),
                        value.revision())).toList(),
                state.bioforms().stream().map(value -> new FrontierStateHydration.BioformState(value.id(), value.hiveId(),
                        value.kind(), value.bornDay(), value.birthOrdinal(), value.alive(), value.deathDay(), value.revision())).toList(),
                state.processedObservationIds());

        assertFalse(restored.resident(resident.id()).orElseThrow().alive());
        assertFalse(new FrontierCommandProcessor().execute(restored, new FrontierCommand.ApplyObservation(death)).accepted());
    }

    @Test
    void dailyEconomyProducesConsumesAndClearsTradeWithoutBreakingCreditBalance() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 102L);
        FrontierCommandOutcome outcome = new FrontierCommandProcessor().execute(state,
                new FrontierCommand.AdvanceDays(30, "test:economy"));

        assertEquals(30, state.day());
        assertTrue(outcome.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.RESOURCE_PRODUCED));
        assertTrue(state.settlements().stream().mapToLong(FrontierSettlement::netCredit).sum() == 0);
        assertTrue(state.settlements().stream().allMatch(value -> value.stock(FrontierResource.FOOD) >= 0));
        assertTrue(state.settlements().stream().allMatch(value -> value.stock(FrontierResource.AMMO) > 0),
                "workshop operations must visibly convert the mining/forestry/power chain into ammunition");
        assertTrue(state.settlements().stream().allMatch(value -> value.stock(FrontierResource.WEAPONS) > 0),
                "the field-operation supply requirement must have a separate material weapons chain");
        assertTrue(state.settlements().stream().allMatch(value -> value.stock(FrontierResource.POWER) >= 0),
                "operations may not consume a resource that the canonical settlement does not own");
    }

    @Test
    void hiveBiomassIsFedByFiniteEcologyAndScorchingCutsItsDigestiveYield() {
        FrontierWorldState fertile = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 102_1L);
        FrontierWorldState scorched = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 102_1L);
        FrontierHive fertileHive = fertile.hives().iterator().next();
        FrontierHive scorchedHive = scorched.hives().iterator().next();
        FrontierHiveOrgan digestive = scorched.hiveOrgans().stream()
                .filter(value -> value.hiveId().equals(scorchedHive.id()) && value.kind() == FrontierHiveOrganKind.DIGESTIVE_POOL)
                .findFirst().orElseThrow();
        FrontierEcology.Cell untouched = scorched.ecology().cell(digestive.position().x(), digestive.position().z());
        scorched.ecology().scorch(digestive.position().x(), digestive.position().z());
        FrontierEcology.Cell damaged = scorched.ecology().cell(digestive.position().x(), digestive.position().z());

        new FrontierCommandProcessor().execute(fertile, new FrontierCommand.AdvanceDays(1, "test:ecology-fertile"));
        new FrontierCommandProcessor().execute(scorched, new FrontierCommand.AdvanceDays(1, "test:ecology-scorched"));

        assertTrue(damaged.flora() < untouched.flora() && damaged.scar() > untouched.scar(),
                "scorching must deny living substrate and leave a canonical scar");
        assertTrue(fertileHive.biomass() - 72 > scorchedHive.biomass() - 72,
                "the hive may not receive the same magic income from a scorched digestive cell");
        assertTrue(scorched.ecology().cell(digestive.position().x(), digestive.position().z()).organicMass() < damaged.organicMass(),
                "digestive intake must remove actual local organic matter after the daily regeneration pass");
        assertEquals(4_096, FrontierSnapshots.snapshot(scorched).ecology().size(),
                "every graybox simulation cell must remain observable through the immutable snapshot");
    }

    @Test
    void ecologyRestorationRejectsMissingOrDuplicatedCellsRatherThanInventingAWorld() {
        FrontierEcology ecology = FrontierEcology.genesis(FrontierProfile.GRAYBOX_10, 1L);
        assertThrows(IllegalArgumentException.class, () -> FrontierEcology.restore(64, 64, ecology.cells().subList(1, 4_096)),
                "a partial ecology cannot be silently regenerated during a restart");
        java.util.ArrayList<FrontierEcology.Cell> duplicate = new java.util.ArrayList<>(ecology.cells());
        duplicate.set(1, duplicate.getFirst());
        assertThrows(IllegalArgumentException.class, () -> FrontierEcology.restore(64, 64, duplicate),
                "two persisted values for one cell must fail closed");
    }

    @Test
    void disconnectedBroodTissueLosesSignalAndCannotLaunchAnAssault() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 102_2L);
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan core = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == FrontierHiveOrganKind.CORE).findFirst().orElseThrow();
        FrontierHiveOrgan brood = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == FrontierHiveOrganKind.BROOD_SAC).findFirst().orElseThrow();
        assertTrue(state.hiveSignal(hive.id(), brood.position()) >= 320 && state.hiveCanAct(hive.id(), FrontierHiveOrganKind.BROOD_SAC),
                "genesis brood must have an actual connected core/synapse path before it can act");

        java.util.List<FrontierHiveTissueCell> severed = state.hiveTissue().stream()
                .filter(value -> !value.hiveId().equals(hive.id()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        // Below the network threshold this deliberately cannot regrow during the
        // six-day raid cadence. It proves a damaged topology cannot silently
        // regain command authority from a coloured organ cube alone.
        severed.add(new FrontierHiveTissueCell(hive.id(), core.position(), FrontierHiveTissueCell.NETWORK_THRESHOLD - 1));
        state.restoreHiveTissue(severed);

        assertEquals(0, state.hiveSignal(hive.id(), brood.position()),
                "a live coloured brood cube without a tissue path must not retain invisible command authority");
        assertFalse(state.hiveCanAct(hive.id(), FrontierHiveOrganKind.BROOD_SAC));
        new FrontierCommandProcessor().execute(state, new FrontierCommand.AdvanceDays(6, "test:severed-brood"));
        assertTrue(state.assaults().stream().noneMatch(value -> value.hiveId().equals(hive.id())),
                "the disconnected hive must not launch a raid merely because its physical organs still exist");
    }

    @Test
    void hiveTissueRestorationRejectsDuplicateTopologyCells() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 102_3L);
        java.util.ArrayList<FrontierHiveTissueCell> duplicate = new java.util.ArrayList<>(state.hiveTissue());
        duplicate.add(duplicate.getFirst());
        assertThrows(IllegalArgumentException.class, () -> state.restoreHiveTissue(duplicate),
                "a restart must reject duplicate tissue topology instead of picking an arbitrary network path");
    }

    @Test
    void tradeCreatesOneMaterializableCargoPerRouteAndDeliversItOnTheFollowingDay() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 103L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();

        FrontierCommandOutcome firstDay = commands.execute(state, new FrontierCommand.AdvanceDays(1, "test:cargo"));
        assertTrue(firstDay.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.CARGO_DISPATCHED));
        assertTrue(state.cargo().stream().allMatch(value -> value.dispatchedDay() == 1));
        assertTrue(state.cargo().stream().map(FrontierCargo::routeId).distinct().count() == state.cargo().size());

        FrontierCommandOutcome secondDay = commands.execute(state, new FrontierCommand.AdvanceDays(1, "test:cargo"));
        assertTrue(secondDay.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.CARGO_DELIVERED));
    }

    @Test
    void destroyedPhysicalCargoCancelsOnlyItsOwnCreditAndCannotReplay() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 104L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(1, "test:cargo-loss"));
        FrontierCargo cargo = state.cargo().iterator().next();
        long sourceBefore = state.settlement(cargo.sourceSettlementId()).orElseThrow().netCredit();
        long destinationBefore = state.settlement(cargo.destinationSettlementId()).orElseThrow().netCredit();
        FrontierPhysicalObservation.CargoLost loss = new FrontierPhysicalObservation.CargoLost("cargo-loss:1", cargo.id(),
                cargo.materializationId(), cargo.revision(), "player:test");

        assertTrue(commands.execute(state, new FrontierCommand.ApplyObservation(loss)).accepted());
        assertFalse(state.cargo(cargo.id()).isPresent());
        assertEquals(sourceBefore - cargo.creditValue(), state.settlement(cargo.sourceSettlementId()).orElseThrow().netCredit());
        assertEquals(destinationBefore + cargo.creditValue(), state.settlement(cargo.destinationSettlementId()).orElseThrow().netCredit());
        assertFalse(commands.execute(state, new FrontierCommand.ApplyObservation(loss)).accepted());
        assertEquals(0, state.settlements().stream().mapToLong(FrontierSettlement::netCredit).sum());
    }

    @Test
    void twoSeededHivesExposeAllFunctionalOrgansAndOneToOneZombieBioforms() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 105L);

        assertEquals(2, state.hives().size());
        assertEquals(2 * FrontierHiveOrganKind.values().length, state.hiveOrgans().size());
        assertEquals(12, state.bioforms().size());
        assertTrue(state.hives().stream().allMatch(value -> value.state() == FrontierHive.State.ACTIVE));
        assertTrue(state.hiveOrgans().stream().allMatch(value -> value.state() == FrontierHiveOrgan.State.ALIVE));
    }

    @Test
    void deadZombieAndDestroyedCoreAreBothDeduplicatedCanonicalHiveEvents() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 106L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierBioform bioform = state.bioforms().iterator().next();
        FrontierPhysicalObservation.BioformDeath death = new FrontierPhysicalObservation.BioformDeath("bioform:death:1", bioform.id(),
                bioform.materializationId(), bioform.revision(), "player:test");
        FrontierHiveOrgan core = state.hiveOrgans().stream().filter(value -> value.kind() == FrontierHiveOrganKind.CORE).findFirst().orElseThrow();
        FrontierPhysicalObservation.HiveOrganDestroyed destroyed = new FrontierPhysicalObservation.HiveOrganDestroyed("hive:core:1", core.id(),
                core.materializationId(), core.revision(), "player:test");

        assertTrue(commands.execute(state, new FrontierCommand.ApplyObservation(death)).accepted());
        assertFalse(bioform.alive());
        assertFalse(commands.execute(state, new FrontierCommand.ApplyObservation(death)).accepted());
        assertTrue(commands.execute(state, new FrontierCommand.ApplyObservation(destroyed)).accepted());
        assertEquals(FrontierHive.State.DECAPITATED, state.hive(core.hiveId()).orElseThrow().state());
        assertFalse(commands.execute(state, new FrontierCommand.ApplyObservation(destroyed)).accepted());
    }

    @Test
    void activeHiveUsesBroodBiomassToSpawnAStableNewBioform() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 107L);
        new FrontierCommandProcessor().execute(state, new FrontierCommand.AdvanceDays(3, "test:brood"));

        assertTrue(state.bioforms().size() > 12);
        assertTrue(state.bioforms().stream().anyMatch(value -> value.bornDay() == 3));
    }

    @Test
    void activeHiveRaidHasAStableLifecycleAndAllPhysicalDeathsAbortIt() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 108L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();

        FrontierCommandOutcome launch = commands.execute(state, new FrontierCommand.AdvanceDays(6, "test:assault"));
        assertTrue(launch.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.ASSAULT_LAUNCHED));
        assertEquals(2, state.assaults().size(), "one bounded raid may be active for each seeded hive");
        FrontierAssault assault = state.assaults().iterator().next();
        assertEquals(FrontierAssault.State.ASSEMBLING, assault.state());
        assertEquals(3, assault.participantIds().size());
        assertEquals(assault.id(), FrontierSnapshots.snapshot(state).assaults().stream()
                .filter(value -> value.id().equals(assault.id())).findFirst().orElseThrow().id());

        FrontierCommandOutcome finalDeath = null;
        for (String participantId : assault.participantIds()) {
            FrontierBioform participant = state.bioform(participantId).orElseThrow();
            finalDeath = commands.execute(state, new FrontierCommand.ApplyObservation(new FrontierPhysicalObservation.BioformDeath(
                    "test:assault-death:" + participantId, participant.id(), participant.materializationId(),
                    participant.revision(), "player:test")));
            assertTrue(finalDeath.accepted());
        }
        assertEquals(FrontierAssault.State.ABORTED, state.assault(assault.id()).orElseThrow().state());
        assertTrue(finalDeath.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.ASSAULT_RESOLVED
                && value.subjectId().equals(assault.id())),
                "the final physical kill must resolve the raid before any later simulation day");
    }

    @Test
    void civicPolicyMakesAnIncomingRaidAndItsRationingAVisibleCanonicalSettlementFact() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 108_1L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(6, "test:civic-launch"));
        FrontierAssault assault = state.assaults().iterator().next();
        FrontierSettlement target = state.settlement(assault.targetSettlementId()).orElseThrow();

        assertEquals(FrontierCivicState.EMERGENCY, target.civicState(),
                "an assembling raid must create a settlement emergency before it can become a cosmetic zombie cluster");
        assertEquals(920, target.rationPermille(), "the emergency food issue must be an explicit policy output");
        FrontierSnapshot.SettlementView emergency = FrontierSnapshots.snapshot(state).settlements().stream()
                .filter(value -> value.id().equals(target.id())).findFirst().orElseThrow();
        assertEquals(FrontierCivicState.EMERGENCY, emergency.civicState());
        assertEquals(920, emergency.rationPermille());

        commands.execute(state, new FrontierCommand.AdvanceDays(assault.transitDays() + 1, "test:civic-siege"));
        assertEquals(FrontierCivicState.SIEGE, target.civicState(),
                "engaging zombies must escalate the same target to a visible siege, rather than only changing combat math");
        assertEquals(820, target.rationPermille());
    }

    @Test
    void defendedAssaultTravelsEngagesAndKeepsItsHumanResponseCanonical() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 109L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(6, "test:assault-travel"));
        FrontierAssault assault = state.assaults().iterator().next();
        FrontierSettlement target = state.settlement(assault.targetSettlementId()).orElseThrow();
        FrontierFieldOperation response = state.fieldOperations().stream()
                .filter(value -> value.targetAssaultId().equals(assault.id())).findFirst().orElseThrow();
        assertEquals(FrontierFieldOperation.State.ASSEMBLING, response.state());
        assertTrue(response.participantIds().stream().allMatch(id -> state.resident(id).orElseThrow().role() == FrontierResidentRole.GUARD));

        FrontierCommandOutcome outcome = commands.execute(state, new FrontierCommand.AdvanceDays(assault.transitDays() + 3,
                "test:assault-travel"));

        assertTrue(outcome.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.ASSAULT_STATE_CHANGED
                && value.subjectId().equals(assault.id())));
        assertTrue(outcome.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.FIELD_OPERATION_STATE_CHANGED
                && value.subjectId().equals(response.id())));
        assertTrue(state.fieldOperation(response.id()).orElseThrow().state() == FrontierFieldOperation.State.RETURNING
                        || state.fieldOperation(response.id()).orElseThrow().terminal(),
                "the human response must return from the same canonical raid that drove it, not linger as visual debris");
        assertTrue(FrontierSnapshots.snapshot(state).fieldOperations().stream().anyMatch(value -> value.id().equals(response.id())
                && value.livingParticipants() == response.participantIds().size()));
        assertTrue(state.assault(assault.id()).orElseThrow().state() == FrontierAssault.State.RETURNING
                || state.assault(assault.id()).orElseThrow().terminal());
    }

    @Test
    void physicalDeathsOfEveryDefenderImmediatelyAbortTheSameFieldOperation() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 113L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(6, "test:field-death"));
        FrontierFieldOperation response = state.fieldOperations().iterator().next();

        for (String participantId : response.participantIds()) {
            FrontierResident participant = state.resident(participantId).orElseThrow();
            FrontierCommandOutcome outcome = commands.execute(state, new FrontierCommand.ApplyObservation(
                    new FrontierPhysicalObservation.ResidentDeath("test:field-death:" + participant.id(), participant.id(),
                            participant.materializationId(), participant.revision(), "player:test")));
            assertTrue(outcome.accepted());
        }

        assertEquals(FrontierFieldOperation.State.ABORTED, state.fieldOperation(response.id()).orElseThrow().state());
        assertTrue(state.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.FIELD_OPERATION_RESOLVED
                && value.subjectId().equals(response.id())),
                "the accepted physical event, not a later daily tick, must resolve an empty response");
    }

    @Test
    void scaledDefenceRequiresEveryVisibleSupplyKindBeforeItCanLaunch() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 114L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(5, "test:field-supply"));
        state.settlements().forEach(value -> value.removeStock(FrontierResource.WEAPONS,
                value.stock(FrontierResource.WEAPONS)));

        FrontierCommandOutcome outcome = commands.execute(state, new FrontierCommand.AdvanceDays(1, "test:field-supply"));

        assertTrue(outcome.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.ASSAULT_LAUNCHED));
        assertTrue(state.fieldOperations().isEmpty(),
                "the defence must not materialize a cosmetic guard group when the canonical weapons supply is absent");
        assertEquals(1, FrontierBalance.defendPersonnel(FrontierProfile.GRAYBOX_10, 20));
        assertEquals(1, FrontierBalance.defendFood(FrontierProfile.GRAYBOX_10));
        assertEquals(1, FrontierBalance.defendMedicine(FrontierProfile.GRAYBOX_10));
        assertEquals(1, FrontierBalance.defendWeapons(FrontierProfile.GRAYBOX_10));
        assertEquals(1, FrontierBalance.defendAmmo(FrontierProfile.GRAYBOX_10));
        assertEquals(11, FrontierBalance.humanTravelDays(new FrontierPoint(0, 0), new FrontierPoint(16, 0)));
        assertEquals(25, FrontierBalance.hiveTravelDays(new FrontierPoint(0, 0), new FrontierPoint(16, 0)));
    }

    @Test
    void terminalSpawnedBioformsCompactWithoutWeakeningGenesisIdentityChecks() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 110L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(3, "test:bioform-retention"));
        FrontierBioform spawned = state.bioforms().stream().filter(value -> value.bornDay() == 3).findFirst().orElseThrow();
        assertTrue(commands.execute(state, new FrontierCommand.ApplyObservation(new FrontierPhysicalObservation.BioformDeath(
                "test:retained-death", spawned.id(), spawned.materializationId(), spawned.revision(), "player:test"))).accepted());
        for (FrontierHiveOrgan synapse : state.hiveOrgans().stream()
                .filter(value -> value.kind() == FrontierHiveOrganKind.SYNAPSE).toList()) {
            commands.execute(state, new FrontierCommand.ApplyObservation(new FrontierPhysicalObservation.HiveOrganDestroyed(
                    "test:disable-assault:" + synapse.id(), synapse.id(), synapse.materializationId(), synapse.revision(), "player:test")));
        }

        commands.execute(state, new FrontierCommand.AdvanceDays(31, "test:bioform-retention"));

        assertTrue(state.bioform(spawned.id()).isEmpty(), "a terminal non-genesis form must not grow SavedData forever");
        assertEquals(12, state.bioforms().stream().filter(value -> value.bornDay() == 0).count(),
                "the deterministic genesis carriers remain present for fail-closed hydration");
    }

    @Test
    void destroyingAnAssaultingHiveCoreImmediatelyAbortsItsPhysicalOperation() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 111L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(6, "test:core-abort"));
        FrontierAssault assault = state.assaults().iterator().next();
        FrontierHiveOrgan core = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(assault.hiveId()))
                .filter(value -> value.kind() == FrontierHiveOrganKind.CORE).findFirst().orElseThrow();

        FrontierCommandOutcome outcome = commands.execute(state, new FrontierCommand.ApplyObservation(
                new FrontierPhysicalObservation.HiveOrganDestroyed("test:core-abort", core.id(), core.materializationId(),
                        core.revision(), "player:test")));

        assertEquals(FrontierAssault.State.ABORTED, state.assault(assault.id()).orElseThrow().state());
        assertTrue(outcome.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.ASSAULT_RESOLVED
                && value.subjectId().equals(assault.id())), "core destruction must publish the operation consequence immediately");
    }

    @Test
    void physicalFacilityDamageImmediatelyChangesItsFunctionalOperation() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 112L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        FrontierFacility facility = state.facilities().stream().filter(value -> value.kind() == FrontierFacilityKind.WORKSHOP)
                .findFirst().orElseThrow();
        FrontierOperation operation = state.operations().stream().filter(value -> value.facilityId().equals(facility.id()))
                .findFirst().orElseThrow();

        FrontierCommandOutcome outcome = commands.execute(state, new FrontierCommand.ApplyObservation(
                new FrontierPhysicalObservation.FacilityDamage("test:workshop-damage", facility.id(),
                        "frontier:facility:" + facility.id(), facility.revision(), "player:test")));

        assertEquals(FrontierFacility.State.DAMAGED, facility.state());
        assertEquals(FrontierOperation.State.DAMAGED, operation.state(),
                "a black materialized facility cannot continue to claim that it is running until tomorrow");
        assertTrue(outcome.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.OPERATION_STATE_CHANGED
                && value.subjectId().equals(operation.id())));
    }
}
