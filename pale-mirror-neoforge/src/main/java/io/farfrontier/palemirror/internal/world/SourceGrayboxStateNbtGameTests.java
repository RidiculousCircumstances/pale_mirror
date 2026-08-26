package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import java.util.Arrays;
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
        corrupt.putInt("schemaVersion", 17);
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
    public static void savedDataRestoresTheClockAndDeduplicatesPhysicalFacts(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        source.activate(1_200L);
        source.advanceDueDays(3_600L, 1_200L, 24);
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
}
