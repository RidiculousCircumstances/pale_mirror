package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.effect.EffectLeaseState;
import java.util.Arrays;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** GameTest coverage for the NeoForge-only NBT carrier around complete source state. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxStateNbtGameTests {
    private SourceGrayboxStateNbtGameTests() { }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void completeSourceDocumentSurvivesNbtRoundTrip(GameTestHelper helper) {
        ReferenceGrayboxSimulation source = ReferenceGrayboxSimulation.create(42L);
        source.tick();
        ReferenceGrayboxSimulation restored = SourceGrayboxStateNbt.read(SourceGrayboxStateNbt.write(source));

        helper.assertTrue(Arrays.equals(source.save(), restored.save()),
                "NBT must retain the complete source-shaped document, not a projection cache");
        source.tick();
        restored.tick();
        helper.assertValueEqual(restored.snapshot(), source.snapshot(),
                "a restored source world must continue with the same visible state");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void incompleteSourceEnvelopeFailsClosed(GameTestHelper helper) {
        CompoundTag corrupt = new CompoundTag();
        corrupt.putInt("format", 1);
        corrupt.putByteArray("document", new byte[] {1, 2, 3});
        boolean rejected = false;
        try {
            SourceGrayboxStateNbt.read(corrupt);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a corrupt source document must not recreate state from genesis");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void savedDataPreflightRejectsAnUnhydratableCanonicalRecord(GameTestHelper helper) {
        CompoundTag corrupt = new CompoundTag();
        corrupt.putInt("schemaVersion", 19);
        corrupt.put("sourceState", new CompoundTag());
        boolean rejected = false;
        try {
            SourceGrayboxSavedData.assertHydratable(corrupt);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "startup preflight must reject bad source data before DimensionDataStorage can replace it with a fresh world");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void savedDataRejectsAMissingPhysicalScarLedger(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        CompoundTag incomplete = source.save(new CompoundTag(), null);
        incomplete.remove("physicalScars");
        boolean rejected = false;
        try {
            SourceGrayboxSavedData.load(incomplete, null);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a source save without retained real-world damage evidence must fail closed, not regenerate a blank scar ledger");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void savedDataRejectsAMissingCargoLedger(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        CompoundTag incomplete = source.save(new CompoundTag(), null);
        incomplete.remove("cargoLedger");
        boolean rejected = false;
        try {
            SourceGrayboxSavedData.load(incomplete, null);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a source save without exact physical cargo custody must fail closed instead of recreating blank field containers");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void presentationLedgerPreflightRejectsAnOldOrIncompleteConflictRecord(GameTestHelper helper) {
        CompoundTag obsolete = new CompoundTag();
        obsolete.putInt("format", 2);
        boolean rejected = false;
        try {
            SourceGrayboxPresentationLedger.assertHydratable(obsolete);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "startup preflight must retain old conflict evidence as a visible failure instead of replacing it with a fresh presentation ledger");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void savedDataRestoresTheClockAndDeduplicatesPhysicalFacts(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        source.activate(1_200L);
        int initialDay = source.snapshot().day();
        List<ReferenceGrayboxSnapshot> dueBoundaries = source.advanceDueDaySnapshots(49_200L, 24);
        helper.assertValueEqual(dueBoundaries.size(), 2,
                "a delayed server tick must retain every due source-day boundary instead of exposing only the last one");
        helper.assertValueEqual(dueBoundaries.getFirst().day(), initialDay + 1,
                "the first retained boundary must be the first missed source day");
        helper.assertValueEqual(dueBoundaries.getLast(), source.snapshot(),
                "the final retained boundary must remain the canonical source state after catch-up");
        var beforeObservation = source.snapshot();
        String resident = beforeObservation.residents().getFirst().id();
        var applied = source.observe(io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation.killed(
                "gametest:source-graybox:resident", beforeObservation.stateRevision(), resident));
        helper.assertTrue(applied.applied(), "a current typed resident death must enter the source owner once");

        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(source.save(new CompoundTag(), null), null);
        helper.assertValueEqual(restored.snapshot(), source.snapshot(),
                "SavedData must restore the complete source world and its clock, not regenerate it");
        helper.assertValueEqual(restored.observe(io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation.killed(
                        "gametest:source-graybox:resident", applied.stateRevision(), resident)).status(),
                io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                "a persisted physical fact must be rejected before it can replay after restart");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void freshGameplayClockAndExplicitRateChangeHaveExactBoundaries(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        helper.assertValueEqual(source.clockProfile(), SourceGrayboxClockProfile.GAMEPLAY,
                "a fresh living world must default to the twenty-minute gameplay day");
        source.activate(1_000L);
        int initialDay = source.snapshot().day();
        helper.assertValueEqual(source.advanceDueDaySnapshots(24_999L, 24).size(), 0,
                "a gameplay day must not commit one tick before its durable boundary");
        helper.assertValueEqual(source.advanceDueDaySnapshots(25_000L, 24).size(), 1,
                "a gameplay day must commit exactly at 24,000 elapsed game ticks");

        helper.assertTrue(source.changeClockProfile(SourceGrayboxClockProfile.FAST_GRAYBOX, 26_000L),
                "an explicit operator transition must select the retained fast calibration profile");
        helper.assertValueEqual(source.advanceDueDaySnapshots(27_199L, 24).size(), 0,
                "the changed profile must rebase instead of consuming ticks accumulated before the transition");
        helper.assertValueEqual(source.advanceDueDaySnapshots(27_200L, 24).size(), 1,
                "the explicit fast profile must retain its exact 1,200-tick day");

        helper.assertTrue(source.changeClockProfile(SourceGrayboxClockProfile.GAMEPLAY, 28_000L),
                "a live profile return must also be explicit and durable");
        helper.assertValueEqual(source.advanceDueDaySnapshots(51_999L, 24).size(), 0,
                "the return to gameplay must not fabricate an immediate day from fast-profile ticks");
        helper.assertValueEqual(source.advanceDueDaySnapshots(52_000L, 24).size(), 1,
                "the returned gameplay profile must again require a complete 24,000-tick day");
        helper.assertValueEqual(source.snapshot().day(), initialDay + 3,
                "only the three completed durable boundaries may mutate the source world");

        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(source.save(new CompoundTag(), null), null);
        helper.assertValueEqual(restored.clockProfile(), SourceGrayboxClockProfile.GAMEPLAY,
                "the selected profile must survive a restart with the canonical clock state");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void warehouseContainerHandOffSurvivesSourceSavedDataRoundTrip(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxWarehouseLedger.Binding binding = new SourceGrayboxWarehouseLedger.Binding("settlement:1:warehouse:ore:0", 1,
                io.farfrontier.palemirror.frontier.reference.ReferenceResource.ORE, 10, 65, 12, 47,
                SourceGrayboxWarehouseLedger.State.ACTIVE);
        helper.assertTrue(source.warehouseLedger().put(binding), "a materialized container must have one durable physical hand-off record");
        source.markWarehouseLedgerDirty();

        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(source.save(new CompoundTag(), null), null);
        helper.assertValueEqual(restored.warehouseLedger().binding(binding.id()), binding,
                "a restart must preserve the exact barrel identity and acknowledged item count, not reconstruct it from terrain");
        var snapshot = restored.snapshot();
        var receipt = restored.observe(new io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxWarehouseObservation(
                io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxWarehouseObservation.VERSION, "warehouse-binding:once",
                snapshot.stateRevision(), 1, io.farfrontier.palemirror.frontier.reference.ReferenceResource.ORE, 1));
        helper.assertTrue(receipt.applied(), "one physical container receipt must be accepted by the canonical stock owner");
        helper.assertValueEqual(restored.observe(new io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxWarehouseObservation(
                        io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxWarehouseObservation.VERSION, "warehouse-binding:once",
                        receipt.stateRevision(), 1, io.farfrontier.palemirror.frontier.reference.ReferenceResource.ORE, 1)).status(),
                io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                "a durable warehouse receipt may not replay after a restart or retransmission");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void cargoContainerHandOffSurvivesSourceSavedDataRoundTrip(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxCargoLedger.Binding binding = new SourceGrayboxCargoLedger.Binding("cargo-container:operation:991:cargo:food",
                "operation:991:cargo:food", "operation", 991, io.farfrontier.palemirror.frontier.reference.ReferenceResource.FOOD,
                10, 65, 12, 47, SourceGrayboxCargoLedger.State.ACTIVE);
        helper.assertTrue(source.cargoLedger().put(binding), "a materialized field container must retain one exact durable hand-off record");
        source.markCargoLedgerDirty();

        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(source.save(new CompoundTag(), null), null);
        helper.assertValueEqual(restored.cargoLedger().binding(binding.id()), binding,
                "a restart must preserve the exact cargo owner, resource and observed item count instead of reconstructing it from terrain");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void relocatingCargoCustodySurvivesRoundTripAndMissingTargetFailsClosed(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxCargoLedger.Binding moving = new SourceGrayboxCargoLedger.Binding("cargo-container:operation:991:cargo:food",
                "operation:991:cargo:food", "operation", 991, io.farfrontier.palemirror.frontier.reference.ReferenceResource.FOOD,
                10, 65, 12, 26, 65, 12, 47, SourceGrayboxCargoLedger.State.RELOCATING);
        helper.assertTrue(source.cargoLedger().put(moving), "a moving cargo hand-off must retain both custody coordinates");
        source.markCargoLedgerDirty();

        CompoundTag persisted = source.save(new CompoundTag(), null);
        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(persisted.copy(), null);
        helper.assertValueEqual(restored.cargoLedger().binding(moving.id()), moving,
                "restart must preserve the old container and the unmaterialized target instead of inventing a second cargo copy");

        CompoundTag corrupt = persisted.copy();
        CompoundTag encoded = corrupt.getList("cargoLedger", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        encoded.remove("targetX");
        boolean rejected = false;
        try {
            SourceGrayboxSavedData.load(corrupt, null);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a saved relocation without an exact target must fail startup rather than forget old physical custody");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void actorExecutionLeaseSurvivesSavedDataRoundTripAndRejectsStaleRecovery(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        String actor = source.snapshot().residents().getFirst().id();
        helper.assertTrue(source.prepareActor(actor, "gametest:chunk:0_0", 20L), "a source resident must get one preparation lease");
        String lease = source.actorExecution().actor(actor).orElseThrow().leaseId();
        helper.assertTrue(source.activateActor(actor, lease, "gametest:chunk:0_0", 21L), "the matching lease may become hot");

        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(source.save(new CompoundTag(), null), null);
        helper.assertTrue(restored.enterActorRecovery(22L), "restart must make unfinished physical ownership explicit");
        helper.assertTrue(!restored.recoverActorHot(actor, lease, "gametest:other", 23L),
                "a stale actor cannot be silently adopted after restart");
        helper.assertTrue(restored.recoverActorCold(actor, lease, "gametest:chunk:0_0", 23L),
                "the original lease may settle to cold after a failed physical inspection");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void v23ThroughV25MigrationsPreserveTheirHistoricalFastClock(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        for (int legacySchema : List.of(23, 24, 25)) {
            CompoundTag legacy = source.save(new CompoundTag(), null);
            legacy.putInt("schemaVersion", legacySchema);
            legacy.remove("clockProfile");
            CompoundTag execution = legacy.getCompound("actorExecution");
            if (legacySchema < 25) {
                execution.putInt("format", SourceGrayboxActorExecutionNbt.LEGACY_GLOBAL_REVISION_FORMAT);
                CompoundTag firstActor = execution.getList("actors", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
                firstActor.putString("revision", "0".repeat(64));
            }

            SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(legacy, null);
            String actor = restored.actorExecution().actors().getFirst().id();
            helper.assertValueEqual(restored.actorExecution().actors().getFirst().sourceRevision(), actorRevision(restored.snapshot(), actor),
                    "a v" + legacySchema + " migration may rebind only after the retained source actor identity was inspected");
            helper.assertValueEqual(restored.clockProfile(), SourceGrayboxClockProfile.FAST_GRAYBOX,
                    "a v" + legacySchema + " world must preserve the real historical 1,200-tick pacing instead of silently rescaling time");
            helper.assertTrue(restored.isDirty(),
                    "a successful v" + legacySchema + " migration must request a world save even when no later simulation event happens");
            CompoundTag upgraded = restored.save(new CompoundTag(), null);
            helper.assertValueEqual(upgraded.getInt("schemaVersion"), 26,
                    "a successful legacy migration must durably record the strict v26 envelope");
            helper.assertValueEqual(upgraded.getString("clockProfile"), "fast_graybox",
                    "the upgraded document must make its preserved historical pacing explicit");
            helper.assertValueEqual(upgraded.getCompound("actorExecution").getInt("format"), SourceGrayboxActorExecutionNbt.FORMAT,
                    "the migrated execution ledger must no longer retain the global-revision envelope");
            SourceGrayboxSavedData.load(upgraded, null);
        }

        CompoundTag missingActor = source.save(new CompoundTag(), null);
        missingActor.putInt("schemaVersion", 23);
        CompoundTag missingExecution = missingActor.getCompound("actorExecution");
        missingExecution.putInt("format", SourceGrayboxActorExecutionNbt.LEGACY_GLOBAL_REVISION_FORMAT);
        missingExecution.put("actors", new net.minecraft.nbt.ListTag());
        boolean rejected = false;
        try {
            SourceGrayboxSavedData.load(missingActor, null);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a legacy migration must reject a missing exact source person rather than recreating an execution ledger");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void currentSchemaRejectsMissingOrUnknownClockProfile(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        CompoundTag missing = source.save(new CompoundTag(), null);
        missing.remove("clockProfile");
        boolean missingRejected = false;
        try {
            SourceGrayboxSavedData.load(missing, null);
        } catch (IllegalStateException expected) {
            missingRejected = true;
        }
        helper.assertTrue(missingRejected,
                "a current source document without its pacing contract must fail closed instead of guessing a rate");

        CompoundTag invalid = source.save(new CompoundTag(), null);
        invalid.putString("clockProfile", "warp_speed");
        boolean invalidRejected = false;
        try {
            SourceGrayboxSavedData.load(invalid, null);
        } catch (IllegalStateException expected) {
            invalidRejected = true;
        }
        helper.assertTrue(invalidRejected,
                "an unknown persisted clock profile must stop startup before it can rescale canonical time");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void currentExecutionRevisionMismatchStillFailsClosed(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        CompoundTag corrupt = source.save(new CompoundTag(), null);
        corrupt.getCompound("actorExecution").getList("actors", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .putString("revision", "f".repeat(64));
        boolean rejected = false;
        try {
            SourceGrayboxSavedData.load(corrupt, null);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "only the explicit legacy migration may rebind an old global revision; a corrupted current execution ledger still stops startup");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void currentSchemaRejectsTheLegacyGlobalRevisionEnvelope(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        CompoundTag corrupt = source.save(new CompoundTag(), null);
        corrupt.getCompound("actorExecution").putInt("format", SourceGrayboxActorExecutionNbt.LEGACY_GLOBAL_REVISION_FORMAT);
        boolean rejected = false;
        try {
            SourceGrayboxSavedData.load(corrupt, null);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a current schema must not silently re-enter the legacy global-revision migration path");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void interruptedSourceMeleeRemainsUnknownAfterRestartAndCannotReplay(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        String actor = source.snapshot().residents().getFirst().id();
        helper.assertTrue(source.prepareActor(actor, "gametest:chunk:0_0", 20L), "the actor must have an exact physical lease");
        String lease = source.actorExecution().actor(actor).orElseThrow().leaseId();
        helper.assertTrue(source.activateActor(actor, lease, "gametest:chunk:0_0", 21L), "the actor must be hot before a local action");
        var action = source.reserveActorCombat(actor, lease, "gametest:chunk:0_0", 22L, 20L).orElseThrow();
        source.effectLeases().plan(EffectLease.planned(action.id(), action.id(), "reference-graybox", source.snapshot().profileId(),
                actor, "melee", 22L, 23L));
        helper.assertTrue(source.effectLeases().begin(action.id()), "the action must become physically in-flight before its interruption");

        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(source.save(new CompoundTag(), null), null);
        helper.assertTrue(restored.recoverEffectLeases(24L), "restart must classify an in-flight physical hit instead of replaying it");
        helper.assertValueEqual(restored.effectLeases().find(action.id()).orElseThrow().state(), EffectLeaseState.UNKNOWN_AFTER_RESTART,
                "the interrupted effect must remain a durable unknown receipt");
        helper.assertTrue(!restored.effectLeases().begin(action.id()), "a terminal unknown action may never begin a second time");
        helper.assertValueEqual(restored.actorExecution().actor(actor).orElseThrow().nextCombatAtGameTick(), 42L,
                "the actor's durable cooldown must survive independently of the unknown effect receipt");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void completedPhysicalEffectReceiptSurvivesTheSourceSavedDataRoundTrip(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        String id = "gametest:source-graybox:effect:1";
        java.util.UUID target = java.util.UUID.nameUUIDFromBytes("source-graybox-effect-target".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        helper.assertTrue(ControlledEffectExecutor.executeOnceWithReceipt(source,
                EffectLease.planned(id, id, "reference-graybox", source.snapshot().profileId(), "resident:1:1", "melee", 12L, 13L),
                12L, target, () -> "target=" + target + ";beforeHealth16=320;afterHealth16=288;landed=true"),
                "the source effect ledger must authorize one completed physical receipt");

        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(source.save(new CompoundTag(), null), null);
        var receipt = restored.effectLeases().find(id).orElseThrow();
        helper.assertValueEqual(receipt.state(), EffectLeaseState.COMPLETED, "the terminal physical action must persist");
        helper.assertValueEqual(receipt.nativeReference(), target, "the inspected physical target must persist with the action");
        helper.assertTrue(receipt.receipt().contains("afterHealth16=288"), "the exact post-impact receipt must survive restart hydration");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void savedDataRejectsAnActorExecutionLedgerThatForgetsASourcePerson(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        CompoundTag corrupt = source.save(new CompoundTag(), null);
        CompoundTag execution = corrupt.getCompound("actorExecution");
        execution.put("actors", new net.minecraft.nbt.ListTag());
        corrupt.put("actorExecution", execution);
        boolean rejected = false;
        try {
            SourceGrayboxSavedData.load(corrupt, null);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a partial physical execution ledger must fail startup rather than silently recreating source people");
        helper.succeed();
    }

    private static String actorRevision(ReferenceGrayboxSnapshot snapshot, String actorId) {
        return io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState.descriptors(snapshot).stream()
                .filter(descriptor -> descriptor.id().equals(actorId))
                .findFirst()
                .orElseThrow()
                .sourceRevision();
    }
}
