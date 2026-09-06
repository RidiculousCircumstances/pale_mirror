package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.FrontierCommand;
import io.farfrontier.palemirror.frontier.FrontierCommandProcessor;
import io.farfrontier.palemirror.frontier.FrontierPhysicalObservation;
import io.farfrontier.palemirror.frontier.FrontierProfile;
import io.farfrontier.palemirror.frontier.FrontierWorldFactory;
import io.farfrontier.palemirror.internal.world.FrontierStateCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierPersistenceGameTests {
    private FrontierPersistenceGameTests() { }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void confirmedPhysicalDeathSurvivesNbtRestartAndCannotReplay(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 44L);
        var resident = state.residents().iterator().next();
        var death = new FrontierPhysicalObservation.ResidentDeath("frontier:death:restart", resident.id(),
                resident.materializationId(), resident.revision(), "gametest:player");
        new FrontierCommandProcessor().execute(state, new FrontierCommand.ApplyObservation(death));
        var restored = FrontierStateCodec.read(FrontierStateCodec.write(state));

        helper.assertTrue(!restored.resident(resident.id()).orElseThrow().alive(),
                "a confirmed physical death must survive a SavedData round trip");
        helper.assertTrue(!new FrontierCommandProcessor().execute(restored, new FrontierCommand.ApplyObservation(death)).accepted(),
                "a persisted observation id must prevent death replay after restart");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void economyAndMutualCreditSurviveNbtRestart(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 45L);
        new FrontierCommandProcessor().execute(state, new FrontierCommand.AdvanceDays(12, "gametest:economy"));
        var restored = FrontierStateCodec.read(FrontierStateCodec.write(state));

        helper.assertTrue(state.settlements().stream().allMatch(source -> {
                    var target = restored.settlement(source.id()).orElseThrow();
                    return source.stocks().equals(target.stocks()) && source.netCredit() == target.netCredit();
                }), "stocks and mutual-credit positions must survive the Frontier SavedData round trip");
        helper.assertTrue(restored.settlements().stream().mapToLong(value -> value.netCredit()).sum() == 0,
                "restart must retain the mutual-credit zero invariant");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void settlementCivicLedgerSurvivesRestartAndMissingRationFailsClosed(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 45_1L);
        new FrontierCommandProcessor().execute(state, new FrontierCommand.AdvanceDays(6, "gametest:civic"));
        var settlement = state.settlement(state.assaults().iterator().next().targetSettlementId()).orElseThrow();
        CompoundTag serialized = FrontierStateCodec.write(state);
        var restored = FrontierStateCodec.read(serialized);
        var saved = restored.settlement(settlement.id()).orElseThrow();
        helper.assertValueEqual(saved.civicState(), settlement.civicState(),
                "restart must retain the settlement's actual crisis regime");
        helper.assertValueEqual(saved.rationPermille(), settlement.rationPermille(),
                "restart must retain the exact civilian food policy, not recreate normal rations");
        serialized.getList("settlements", Tag.TAG_COMPOUND).getCompound(0).remove("rationPermille");
        boolean rejected = false;
        try {
            FrontierStateCodec.read(serialized);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a partial civic ledger must fail closed rather than silently restoring normal rations");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void finiteEcologySurvivesRestartAndPartialStateFailsClosed(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 451L);
        new FrontierCommandProcessor().execute(state, new FrontierCommand.AdvanceDays(5, "gametest:ecology"));
        var digestive = state.hiveOrgans().stream()
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.FrontierHiveOrganKind.DIGESTIVE_POOL)
                .findFirst().orElseThrow();
        var before = state.ecology().cell(digestive.position().x(), digestive.position().z());
        CompoundTag serialized = FrontierStateCodec.write(state);
        var restored = FrontierStateCodec.read(serialized);
        helper.assertValueEqual(restored.ecology().cell(before.x(), before.z()), before,
                "restart must retain exact organic pools and scar instead of regenerating a clean ecology");

        serialized.getList("ecology", Tag.TAG_COMPOUND).remove(0);
        boolean rejected = false;
        try {
            FrontierStateCodec.read(serialized);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a partial ecological save must fail closed rather than inventing missing substrate");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hiveTissueTopologySurvivesRestartAndPartialStateFailsClosed(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 452L);
        new FrontierCommandProcessor().execute(state, new FrontierCommand.AdvanceDays(3, "gametest:hive-tissue"));
        CompoundTag serialized = FrontierStateCodec.write(state);
        var restored = FrontierStateCodec.read(serialized);
        helper.assertValueEqual(restored.hiveTissue(), state.hiveTissue(),
                "restart must retain exact hive tissue strengths and topology, rather than deriving a fresh network");

        serialized.getList("hiveTissue", Tag.TAG_COMPOUND).remove(0);
        boolean rejected = false;
        try {
            FrontierStateCodec.read(serialized);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a partial hive-tissue save must fail closed rather than reconnecting a command network from genesis");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void morphogenesisProjectAndCompletedDynamicOrganSurviveRestartOrFailClosed(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 453L);
        var commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(1, "gametest:morphogenesis-seed"));
        var hive = state.hives().iterator().next();
        var core = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.FrontierHiveOrganKind.CORE)
                .findFirst().orElseThrow();
        var target = state.hiveTissue().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.strength() >= 420)
                .map(io.farfrontier.palemirror.frontier.FrontierHiveTissueCell::position)
                .filter(point -> state.hiveOrgans().stream().noneMatch(organ -> organ.hiveId().equals(hive.id())
                        && organ.position().equals(point))).findFirst().orElseThrow();
        helper.assertTrue(commands.execute(state, new FrontierCommand.StartMorphogenesis(hive.id(), core.id(),
                        io.farfrontier.palemirror.frontier.FrontierHiveOrganKind.SYNAPSE, target,
                        "gametest:morphogenesis-start")).accepted(),
                "a connected core plus strong tissue must create a durable growth commitment");
        var project = state.morphogenesisProjects().iterator().next();
        CompoundTag serialized = FrontierStateCodec.write(state);
        var restored = FrontierStateCodec.read(serialized);
        var savedProject = restored.morphogenesisProject(project.id()).orElseThrow();
        helper.assertValueEqual(savedProject.position(), project.position(),
                "restart must preserve an in-progress growth target rather than choosing a new random cell");
        helper.assertValueEqual(savedProject.remainingDays(), project.remainingDays(),
                "restart must preserve committed growth progress exactly");

        serialized.getList("morphogenesisProjects", Tag.TAG_COMPOUND).remove(0);
        boolean rejected = false;
        try {
            FrontierStateCodec.read(serialized);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a partial morphogenesis list must fail closed rather than forgetting a committed build");

        commands.execute(restored, new FrontierCommand.AdvanceDays(savedProject.requiredDays(), "gametest:morphogenesis-finish"));
        var dynamic = restored.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.position().equals(target)
                        && value.kind() == io.farfrontier.palemirror.frontier.FrontierHiveOrganKind.SYNAPSE)
                .findFirst().orElseThrow();
        var restartedAfterCompletion = FrontierStateCodec.read(FrontierStateCodec.write(restored));
        helper.assertTrue(restartedAfterCompletion.hiveOrgan(dynamic.id()).isPresent(),
                "a completed organ has a durable dynamic identity and cannot disappear on the following restart");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void returningHarvesterCargoSurvivesRestartOrFailsClosed(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 454L);
        var commands = new FrontierCommandProcessor();
        var hive = state.hives().iterator().next();
        var brood = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.FrontierHiveOrganKind.BROOD_SAC)
                .findFirst().orElseThrow();
        var harvester = state.bioforms().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.FrontierBioformKind.HARVESTER)
                .findFirst().orElseThrow();
        var target = new io.farfrontier.palemirror.frontier.FrontierPoint(brood.position().x() + 3, brood.position().z());
        helper.assertTrue(commands.execute(state, new FrontierCommand.LaunchHarvester(hive.id(), brood.id(), harvester.id(), target,
                        "gametest:harvester-launch")).accepted(),
                "a connected low-reserve brood must be able to launch its real harvesting Zombie");
        var run = state.harvesterRuns().iterator().next();
        commands.execute(state, new FrontierCommand.AdvanceDays(run.outboundDays() + 1, "gametest:harvester-forage"));
        helper.assertValueEqual(run.state().name(), "RETURNING", "the SavedData proof must capture actual in-transit cargo");
        CompoundTag serialized = FrontierStateCodec.write(state);
        var restored = FrontierStateCodec.read(serialized);
        var saved = restored.harvesterRun(run.id()).orElseThrow();
        helper.assertValueEqual(saved.state().name(), "RETURNING", "restart must not turn a loaded harvester into hidden hive biomass");
        helper.assertValueEqual(saved.cargo(), run.cargo(), "restart must retain the exact carried organic payload");
        helper.assertValueEqual(saved.position(), run.position(), "restart must retain the visible physical Zombie position");

        serialized.getList("harvesterRuns", Tag.TAG_COMPOUND).remove(0);
        boolean rejected = false;
        try {
            FrontierStateCodec.read(serialized);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a partial harvester list must fail closed rather than losing an in-flight delivery");

        commands.execute(restored, new FrontierCommand.AdvanceDays(saved.returnDays(), "gametest:harvester-return"));
        helper.assertValueEqual(restored.harvesterRun(saved.id()).orElseThrow().state().name(), "COMPLETED",
                "the restored trip must still finish through the same canonical return path");
        helper.assertTrue(FrontierStateCodec.read(FrontierStateCodec.write(restored)).harvesterRun(saved.id()).isPresent(),
                "the terminal receipt remains bounded but restart-visible until compaction");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void deployedSporeCarrierAndLatentColonySurviveRestartOrFailClosed(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 455L);
        var commands = new FrontierCommandProcessor();
        var hive = state.hives().iterator().next();
        var sporulator = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.FrontierHiveOrganKind.SPORULATOR)
                .findFirst().orElseThrow();
        var carrier = state.bioforms().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.FrontierBioformKind.PROPAGULE_CARRIER)
                .findFirst().orElseThrow();
        var target = new io.farfrontier.palemirror.frontier.FrontierPoint(32, 52);
        helper.assertTrue(commands.execute(state, new FrontierCommand.LaunchPropagationRun(hive.id(), sporulator.id(), carrier.id(), target,
                        "gametest:spore-launch")).accepted(),
                "the durable proof begins with a validated physical carrier, not a remote infection counter");
        var run = state.propagationRuns().iterator().next();
        commands.execute(state, new FrontierCommand.AdvanceDays(run.transitDays(), "gametest:spore-deploy"));
        var colony = state.latentColonies().stream().filter(value -> value.id().equals(run.latentColonyId())).findFirst().orElseThrow();

        CompoundTag serialized = FrontierStateCodec.write(state);
        var restored = FrontierStateCodec.read(serialized);
        var savedRun = restored.propagationRuns().stream().filter(value -> value.id().equals(run.id())).findFirst().orElseThrow();
        var savedColony = restored.latentColonies().stream().filter(value -> value.id().equals(colony.id())).findFirst().orElseThrow();
        helper.assertValueEqual(savedRun.position(), target,
                "restart must retain the delivered carrier's exact canonical target rather than choosing a new infection cell");
        helper.assertValueEqual(savedColony.propagules(), colony.propagules(),
                "restart must retain the visible colony strength that a player may still clear");

        serialized.getList("latentColonies", Tag.TAG_COMPOUND).remove(0);
        boolean rejected = false;
        try {
            FrontierStateCodec.read(serialized);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a partial latent-colony save must fail closed rather than allowing a deployed carrier to lose its player-clearable target");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void inTransitCargoSurvivesNbtRestartAndPhysicalLossCancelsItsCredit(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 46L);
        var commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(1, "gametest:cargo"));
        var cargo = state.cargo().iterator().next();
        CompoundTag serialized = FrontierStateCodec.write(state);
        var restored = FrontierStateCodec.read(serialized);
        var savedCargo = restored.cargo(cargo.id()).orElseThrow();
        helper.assertValueEqual(savedCargo.creditValue(), cargo.creditValue(),
                "a restart must preserve the exact mutual-credit value that physical loss later reverses");
        serialized.getList("cargo", Tag.TAG_COMPOUND).getCompound(0).remove("creditValue");
        boolean rejected = false;
        try {
            FrontierStateCodec.read(serialized);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "missing cargo credit value must fail closed rather than turn a priced load into a free shipment");
        var loss = new FrontierPhysicalObservation.CargoLost("frontier:cargo:lost", savedCargo.id(), savedCargo.materializationId(),
                savedCargo.revision(), "gametest:player");

        helper.assertTrue(commands.execute(restored, new FrontierCommand.ApplyObservation(loss)).accepted(),
                "a current physical cargo loss must reconcile through the canonical command port");
        helper.assertTrue(restored.cargo(savedCargo.id()).isEmpty(), "lost cargo must no longer be materialized after reconciliation");
        helper.assertTrue(restored.settlements().stream().mapToLong(value -> value.netCredit()).sum() == 0,
                "cargo loss must cancel, rather than corrupt, mutual credit");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hiveBioformAndOrganDamageSurviveNbtRestart(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 47L);
        var commands = new FrontierCommandProcessor();
        var bioform = state.bioforms().iterator().next();
        var organ = state.hiveOrgans().iterator().next();
        commands.execute(state, new FrontierCommand.ApplyObservation(new FrontierPhysicalObservation.BioformDeath("frontier:bioform:restart",
                bioform.id(), bioform.materializationId(), bioform.revision(), "gametest:player")));
        commands.execute(state, new FrontierCommand.ApplyObservation(new FrontierPhysicalObservation.HiveOrganDestroyed("frontier:organ:restart",
                organ.id(), organ.materializationId(), organ.revision(), "gametest:player")));
        var restored = FrontierStateCodec.read(FrontierStateCodec.write(state));

        helper.assertTrue(!restored.bioform(bioform.id()).orElseThrow().alive(), "a killed physical zombie must remain dead after restart");
        helper.assertTrue(restored.hiveOrgan(organ.id()).orElseThrow().state() == io.farfrontier.palemirror.frontier.FrontierHiveOrgan.State.DESTROYED,
                "a destroyed organ must remain absent after restart");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeHiveAssaultSurvivesNbtRestartWithItsPhysicalParticipantIdentities(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 48L);
        new FrontierCommandProcessor().execute(state, new FrontierCommand.AdvanceDays(6, "gametest:assault"));
        var assault = state.assaults().iterator().next();
        var restored = FrontierStateCodec.read(FrontierStateCodec.write(state));
        var saved = restored.assault(assault.id()).orElseThrow();

        helper.assertValueEqual(saved.state().name(), "ASSEMBLING", "a restart must retain an unlaunched hive operation");
        helper.assertValueEqual(saved.participantIds(), assault.participantIds(),
                "the same managed zombies must remain attached to the restored assault");
        helper.assertValueEqual(saved.position(), assault.position(), "the materialization anchor must not jump after NBT restart");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeHumanResponseSurvivesNbtRestartAndStillReactsToPhysicalDeaths(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 49L);
        var commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(6, "gametest:field-operation"));
        var response = state.fieldOperations().iterator().next();
        var restored = FrontierStateCodec.read(FrontierStateCodec.write(state));
        var saved = restored.fieldOperation(response.id()).orElseThrow();

        helper.assertValueEqual(saved.participantIds(), response.participantIds(),
                "restart must retain the same individual Villagers assigned to the response");
        helper.assertValueEqual(saved.state().name(), "ASSEMBLING", "the response lifecycle cannot jump at restart");
        for (String participantId : saved.participantIds()) {
            var resident = restored.resident(participantId).orElseThrow();
            helper.assertTrue(commands.execute(restored, new FrontierCommand.ApplyObservation(
                    new FrontierPhysicalObservation.ResidentDeath("frontier:field:restart:" + participantId, participantId,
                            resident.materializationId(), resident.revision(), "gametest:player"))).accepted(),
                    "each current managed defender death must remain valid after restart");
        }
        helper.assertValueEqual(restored.fieldOperation(saved.id()).orElseThrow().state().name(), "ABORTED",
                "the persisted response must resolve immediately when all of its physical carriers are gone");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void suppliedCampaignSurvivesRestartAndMissingLedgerFailsClosed(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 49_1L);
        var commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(38, "gametest:campaign"));
        var campaign = state.campaigns().iterator().next();
        CompoundTag serialized = FrontierStateCodec.write(state);
        var restored = FrontierStateCodec.read(serialized);
        var saved = restored.campaign(campaign.id()).orElseThrow();

        helper.assertValueEqual(saved.phase(), campaign.phase(), "restart must retain the campaign's actual front phase");
        helper.assertValueEqual(saved.participantIds(), campaign.participantIds(),
                "restart must retain the same individual Villagers in the coalition");
        helper.assertValueEqual(saved.supplyReadinessPermille(), campaign.supplyReadinessPermille(),
                "restart must retain the explicit supply result instead of recalculating a different route");
        serialized.remove("campaigns");
        boolean rejected = false;
        try {
            FrontierStateCodec.read(serialized);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a missing campaign ledger must fail closed rather than silently sending Villagers home");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void corruptFieldOperationAssaultReferenceFailsClosedOnRestart(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 50L);
        new FrontierCommandProcessor().execute(state, new FrontierCommand.AdvanceDays(6, "gametest:field-corruption"));
        CompoundTag serialized = FrontierStateCodec.write(state);
        CompoundTag operation = serialized.getList("fieldOperations", Tag.TAG_COMPOUND).getCompound(0);
        operation.putString("assault", "frontier:assault:missing");
        boolean rejected = false;
        try {
            FrontierStateCodec.read(serialized);
        } catch (IllegalStateException expected) {
            rejected = true;
        }

        helper.assertTrue(rejected, "a field operation cannot silently recover from a missing canonical assault");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void finalPhysicalRaiderDeathImmediatelyResolvesRaidAndSendsDefendersHome(GameTestHelper helper) {
        var state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 51L);
        var commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(6, "gametest:raid-final-death"));
        var assault = state.assaults().iterator().next();
        var response = state.fieldOperations().stream().filter(value -> value.targetAssaultId().equals(assault.id()))
                .findFirst().orElseThrow();
        for (String participantId : assault.participantIds()) {
            var bioform = state.bioform(participantId).orElseThrow();
            commands.execute(state, new FrontierCommand.ApplyObservation(new FrontierPhysicalObservation.BioformDeath(
                    "frontier:raid:physical:" + participantId, participantId, bioform.materializationId(),
                    bioform.revision(), "gametest:player")));
        }

        helper.assertValueEqual(state.assault(assault.id()).orElseThrow().state().name(), "ABORTED",
                "the final current Zombie death must resolve its canonical raid immediately");
        helper.assertValueEqual(state.fieldOperation(response.id()).orElseThrow().state().name(), "RETURNING",
                "the linked defence group must receive the same-event return command");
        helper.succeed();
    }
}
