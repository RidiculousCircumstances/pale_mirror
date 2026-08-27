package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceResource;
import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Recovery proofs for exact actor and active-scene hand-offs across COLD and restart boundaries. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxHotColdRecoveryGameTests {
    private static final String HOLDER = "gametest:hot-cold";

    private SourceGrayboxHotColdRecoveryGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void coldReturnAndRestartReuseOneExactBodyAtTheCapturedHandOff(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        String residentId = baseline.residents().getFirst().id();
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.fixture(anchor, baseline, residentId, 1.0d, "hot-cold");
        ReferenceGrayboxActorExecutionState execution = ReferenceGrayboxActorExecutionState.bootstrap(snapshot);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        Map<String, Entity> admitted = new LinkedHashMap<>();
        double handOffX = anchor.getX() + 15.5d;
        double handOffZ = anchor.getZ() + 15.5d;

        helper.assertTrue(execution.prepare(residentId, HOLDER, 1L), "the exact actor must acquire the first physical lease");
        String firstLease = execution.actor(residentId).orElseThrow().leaseId();
        materializer.apply(helper.getLevel(), snapshot, execution, admitted);
        helper.assertTrue(execution.activate(residentId, firstLease, HOLDER, 2L), "the materialized body must become HOT");
        Villager firstBody = resident(helper, anchor, residentId);
        firstBody.setPos(handOffX, ReferenceGrayboxLayout.GROUND_Y + 1, handOffZ);
        helper.assertTrue(execution.capture(residentId, firstLease, HOLDER, sixteenths(handOffX), sixteenths(handOffZ), 3L),
                "COLD hand-off must start from the observed Minecraft position, not the source marker");
        helper.assertTrue(execution.beginDrain(residentId, firstLease, HOLDER, 4L), "leaving the HOT scene must begin an explicit drain");
        firstBody.discard();
        admitted.remove(SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT"));
        helper.assertTrue(execution.settleCold(residentId, firstLease, HOLDER, 5L), "the drained body must return to COLD source custody");
        SourceGrayboxPresentationLedger.get(helper.getLevel()).releaseEntity(SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT"));

        ReferenceGrayboxActorExecutionState returning = SourceGrayboxActorExecutionNbt.read(SourceGrayboxActorExecutionNbt.write(execution), false);
        var cold = returning.actor(residentId).orElseThrow();
        helper.assertValueEqual(cold.mode(), ReferenceGrayboxActorExecutionState.Mode.COLD, "the durable hand-off must retain COLD ownership");
        helper.assertValueEqual(cold.actualXSixteenths(), sixteenths(handOffX), "COLD must retain the exact captured x hand-off");
        helper.assertValueEqual(cold.actualZSixteenths(), sixteenths(handOffZ), "COLD must retain the exact captured z hand-off");

        helper.assertTrue(returning.prepare(residentId, HOLDER, 6L), "returning HOT demand receives one fresh lease, never revives the old one");
        String returningLease = returning.actor(residentId).orElseThrow().leaseId();
        SourceGrayboxMaterializer returningMaterializer = new SourceGrayboxMaterializer();
        Map<String, Entity> returningEntities = new LinkedHashMap<>();
        returningMaterializer.apply(helper.getLevel(), snapshot, returning, returningEntities);
        helper.assertTrue(returning.activate(residentId, returningLease, HOLDER, 7L), "the returned physical body must activate only through the fresh lease");
        Villager returnedBody = resident(helper, anchor, residentId);
        helper.assertValueEqual(returnedBody.getX(), handOffX, "returning must use the captured HOT x hand-off, not snap to a source slot");
        helper.assertValueEqual(returnedBody.getZ(), handOffZ, "returning must use the captured HOT z hand-off, not snap to a source slot");

        ReferenceGrayboxActorExecutionState restarted = SourceGrayboxActorExecutionNbt.read(SourceGrayboxActorExecutionNbt.write(returning), false);
        helper.assertTrue(restarted.enterRecovery(8L), "restart must make the unfinished physical lease explicit before adoption");
        var recovering = restarted.actor(residentId).orElseThrow();
        SourceGrayboxMaterializer restartMaterializer = new SourceGrayboxMaterializer();
        Map<String, Entity> restartEntities = new LinkedHashMap<>();
        helper.assertValueEqual(restartMaterializer.actorEntity(helper.getLevel(), restartEntities, recovering), returnedBody,
                "restart must inspect the existing exact UUID body before considering any new materialization");
        helper.assertTrue(restarted.recoverHot(residentId, recovering.leaseId(), HOLDER, 9L),
                "only the retained pre-restart lease may re-adopt the observed body");
        restartMaterializer.apply(helper.getLevel(), snapshot, restarted, restartEntities);
        helper.assertValueEqual(residents(helper, anchor, residentId), 1,
                "COLD return and restart must leave exactly one managed Villager, never a duplicate");
        helper.assertValueEqual(returnedBody.getX(), handOffX, "restart recovery must keep the observed x hand-off");
        helper.assertValueEqual(returnedBody.getZ(), handOffZ, "restart recovery must keep the observed z hand-off");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void runtimeDrainReleasesTheExactReservationBeforeLaterHotReadmission(GameTestHelper helper) {
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(helper.getLevel());
        var initial = data.actorExecution().actors().stream()
                .filter(actor -> actor.kind() == ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT)
                .filter(actor -> zone.safeToDrain(position(actor)))
                .findFirst().orElseThrow(() -> new IllegalStateException("GameTest fixture has no resident outside the drain safety radius"));
        String residentId = initial.id();
        BlockPos position = position(initial);
        // GameTest setup creates this fixture chunk. The runtime under test only
        // observes its loaded status and never performs this load itself.
        helper.getLevel().getChunkAt(position);
        TestBorder border = TestBorder.openAround(helper.getLevel().getWorldBorder(), position);
        try {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                helper.getLevel().setBlock(position.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(position.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(position.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
            SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
            Map<String, Entity> admitted = new LinkedHashMap<>();
            helper.assertTrue(data.prepareActor(residentId, SourceGrayboxActorExecutionRuntime.HOLDER, 1L),
                    "the loaded fixture actor needs one exact preparation lease");
            String lease = data.actorExecution().actor(residentId).orElseThrow().leaseId();
            materializer.apply(helper.getLevel(), data.snapshot(), data.actorExecution(), admitted);
            helper.assertTrue(data.activateActor(residentId, lease, SourceGrayboxActorExecutionRuntime.HOLDER, 2L),
                    "the prepared fixture body must become HOT");
            helper.assertTrue(data.beginActorDrain(residentId, lease, SourceGrayboxActorExecutionRuntime.HOLDER, 3L),
                    "leaving HOT must create a durable draining lease");
            String key = SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT");
            helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed(key),
                    "a materialized actor begins with one duplicate-prevention reservation");
            helper.assertTrue(helper.getLevel().hasChunkAt(position),
                    "the runtime may only hand off this naturally resident GameTest fixture chunk");
            helper.assertTrue(SourceGrayboxHotZone.from(helper.getLevel()).safeToDrain(position),
                    "the fixture must model a player-free body before testing its normal DRAINING hand-off");

            helper.assertTrue(new SourceGrayboxActorExecutionRuntime().reconcileLoadedActors(helper.getLevel(), data, materializer, admitted),
                    "the loaded runtime must settle the explicit drain without a snapshot rebuild");
            helper.assertValueEqual(data.actorExecution().actor(residentId).orElseThrow().mode(), ReferenceGrayboxActorExecutionState.Mode.COLD,
                    "runtime drain must return the exact actor to COLD source custody");
            helper.assertTrue(!SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed(key),
                    "a drained body must release its reservation so COLD readmission cannot be blocked by its own old lease");

            long readmissionTick = data.actorExecutionGameTime(helper.getLevel().getGameTime());
            helper.assertTrue(data.prepareActor(residentId, SourceGrayboxActorExecutionRuntime.HOLDER, readmissionTick),
                    "a later HOT demand must reserve a fresh lease after drain");
            materializer.apply(helper.getLevel(), data.snapshot(), data.actorExecution(), admitted);
            helper.assertValueEqual(residents(helper, position, residentId), 1,
                    "the later HOT admission must create one body after the reservation release, not zero or a duplicate");
        } finally {
            border.restore();
        }
        helper.succeed();
    }

    // Uses production-stable resident UUIDs.  Keep this recovery fixture out
    // of the parallel materializer batch, otherwise another fixture can claim
    // the same canonical body and turn a recovery assertion into a harness
    // UUID collision.
    @GameTest(batch = "pm-source-graybox-missing-hot-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void runtimeMissingLoadedHotBodyReleasesItsPhantomClaimBeforeOneFreshAdmission(GameTestHelper helper) {
        // Other fixtures intentionally use seed 42 and may leave a canonical
        // production UUID in the shared GameTest ServerLevel.  This recovery
        // proof needs a distinct canonical actor, not an accidental lookup of
        // that unrelated body.
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42_991L);
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(helper.getLevel());
        var initial = data.actorExecution().actors().stream()
                .filter(actor -> actor.kind() == ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT)
                .filter(actor -> zone.safeToDrain(position(actor)))
                .findFirst().orElseThrow(() -> new IllegalStateException("GameTest fixture has no resident outside the drain safety radius"));
        String residentId = initial.id();
        BlockPos position = position(initial);
        helper.getLevel().getChunkAt(position);
        TestBorder border = TestBorder.openAround(helper.getLevel().getWorldBorder(), position);
        try {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                helper.getLevel().setBlock(position.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(position.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(position.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
            SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
            Map<String, Entity> admitted = new LinkedHashMap<>();
            discardPriorCanonicalResident(helper, residentId);
            long gameTick = data.actorExecutionGameTime(helper.getLevel().getGameTime());
            helper.assertTrue(data.prepareActor(residentId, SourceGrayboxActorExecutionRuntime.HOLDER, gameTick),
                    "the fixture actor needs a real first admission before its body can disappear");
            String lostLease = data.actorExecution().actor(residentId).orElseThrow().leaseId();
            materializer.apply(helper.getLevel(), data.snapshot(), data.actorExecution(), admitted);
            helper.assertTrue(data.activateActor(residentId, lostLease, SourceGrayboxActorExecutionRuntime.HOLDER, gameTick),
                    "the first body must become HOT before loaded-scene recovery can inspect it");
            String key = SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT");
            resident(helper, position, residentId).discard();
            admitted.remove(key);
            var hot = data.actorExecution().actor(residentId).orElseThrow();
            helper.assertTrue(materializer.actorEntity(helper.getLevel(), admitted, hot) == null,
                    "the recovery test must observe no exact body, not merely a stale map entry");
            helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed(key),
                    "the missing HOT body starts with its old duplicate-prevention reservation");
            helper.assertTrue(new SourceGrayboxActorExecutionRuntime().reconcileLoadedActors(helper.getLevel(), data, materializer, admitted),
                    "a loaded absence must change durable executor state rather than leave HOT forever");
            helper.assertValueEqual(data.actorExecution().actor(residentId).orElseThrow().mode(), ReferenceGrayboxActorExecutionState.Mode.COLD,
                    "the phantom HOT lease must settle to source custody");
            helper.assertTrue(!SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed(key),
                    "a missing body must release its old reservation before any later admission");
            helper.assertValueEqual(residents(helper, position, residentId), 0,
                    "recovery must not create a replacement in the same reconciliation turn");

            long nextTick = data.actorExecutionGameTime(helper.getLevel().getGameTime());
            helper.assertTrue(data.prepareActor(residentId, SourceGrayboxActorExecutionRuntime.HOLDER, nextTick),
                    "a later HOT request must receive a fresh lease after the missing body settled");
            helper.assertTrue(!lostLease.equals(data.actorExecution().actor(residentId).orElseThrow().leaseId()),
                    "the replacement body must not inherit the lost lease identity");
            materializer.apply(helper.getLevel(), data.snapshot(), data.actorExecution(), admitted);
            helper.assertValueEqual(residents(helper, position, residentId), 1,
                    "the next explicit admission must restore exactly one body after the old claim was released");
        } catch (RuntimeException | Error failure) {
            border.restore();
            throw failure;
        }
        border.restore();
        helper.succeed();
    }

    // This fixture uses production-stable actor UUIDs.  Give it a distinct
    // sequential batch: concurrent fixture worlds share one ServerLevel, and
    // a duplicate canonical UUID there would test the harness collision
    // rather than restart recovery.
    @GameTest(batch = "pm-source-graybox-restart-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void restartRecoveryReleasesOnlyAnAbsentBodyReservationBeforeFreshAdmission(GameTestHelper helper) {
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(helper.getLevel());
        var initial = data.actorExecution().actors().stream()
                .filter(actor -> actor.kind() == ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT)
                .filter(actor -> zone.safeToDrain(position(actor)))
                .findFirst().orElseThrow(() -> new IllegalStateException("GameTest fixture has no resident outside the drain safety radius"));
        String residentId = initial.id();
        BlockPos position = position(initial);
        helper.getLevel().getChunkAt(position);
        TestBorder border = TestBorder.openAround(helper.getLevel().getWorldBorder(), position);
        try {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                helper.getLevel().setBlock(position.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(position.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(position.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
            SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
            Map<String, Entity> admitted = new LinkedHashMap<>();
            long gameTick = data.actorExecutionGameTime(helper.getLevel().getGameTime());
            helper.assertTrue(data.prepareActor(residentId, SourceGrayboxActorExecutionRuntime.HOLDER, gameTick),
                    "the restart fixture needs a first exact executor lease");
            String lease = data.actorExecution().actor(residentId).orElseThrow().leaseId();
            materializer.apply(helper.getLevel(), data.snapshot(), data.actorExecution(), admitted);
            helper.assertTrue(data.activateActor(residentId, lease, SourceGrayboxActorExecutionRuntime.HOLDER, gameTick),
                    "the first physical body must become HOT before restart recovery");
            String key = SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT");
            resident(helper, position, residentId).discard();
            admitted.remove(key);
            helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed(key),
                    "the lost pre-restart body begins with its durable duplicate-prevention reservation");
            helper.assertTrue(data.enterActorRecovery(gameTick + 1L),
                    "restart must mark the unfinished HOT lease as unknown before re-admission");

            helper.assertTrue(!new SourceGrayboxActorExecutionRuntime().prepareForLoadedExecution(helper.getLevel(), data, materializer, admitted),
                    "recovery without player demand settles custody but must not fabricate a same-turn replacement");
            helper.assertValueEqual(data.actorExecution().actor(residentId).orElseThrow().mode(), ReferenceGrayboxActorExecutionState.Mode.COLD,
                    "an absent post-restart body must settle to COLD source custody");
            helper.assertTrue(!SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed(key),
                    "the absent body's old reservation must be released so a later PREPARING lease cannot strand");

            long nextTick = data.actorExecutionGameTime(helper.getLevel().getGameTime());
            helper.assertTrue(data.prepareActor(residentId, SourceGrayboxActorExecutionRuntime.HOLDER, nextTick),
                    "a later player demand must receive a fresh post-restart executor lease");
            materializer.apply(helper.getLevel(), data.snapshot(), data.actorExecution(), admitted);
            helper.assertValueEqual(residents(helper, position, residentId), 1,
                    "the released reservation must admit exactly one fresh post-restart body");
        } finally {
            border.restore();
        }
        helper.succeed();
    }

    // See the missing-HOT fixture above: retirement also deliberately starts
    // from the production UUID, so it needs an isolated sequential batch.
    @GameTest(batch = "pm-source-graybox-retirement-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void unindexedAdmissionObjectCannotActivateAnActor(GameTestHelper helper) {
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        var actor = data.actorExecution().actors().stream()
                .filter(value -> value.kind() == ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT)
                .findFirst().orElseThrow();
        long gameTick = data.actorExecutionGameTime(helper.getLevel().getGameTime());
        helper.assertTrue(data.prepareActor(actor.id(), SourceGrayboxActorExecutionRuntime.HOLDER, gameTick),
                "the ghost-admission fixture needs one PREPARING lease");
        actor = data.actorExecution().actor(actor.id()).orElseThrow();
        Entity retained = helper.getLevel().getEntity(SourceGrayboxMaterializer.uuid("resident", actor.id()));
        if (retained != null) retained.discard();
        Villager unindexed = new Villager(EntityType.VILLAGER, helper.getLevel());
        unindexed.setUUID(SourceGrayboxMaterializer.uuid("resident", actor.id()));
        unindexed.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ID, actor.id());
        unindexed.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_KIND, "RESIDENT");
        unindexed.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_REVISION, data.snapshot().stateRevision());
        unindexed.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ACTOR_REVISION, actor.sourceRevision());
        Map<String, Entity> admitted = new LinkedHashMap<>();
        admitted.put(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()), unindexed);

        new SourceGrayboxActorExecutionRuntime().reconcileLoadedActors(helper.getLevel(), data,
                new SourceGrayboxMaterializer(), admitted);

        helper.assertValueEqual(data.actorExecution().actor(actor.id()).orElseThrow().mode(), ReferenceGrayboxActorExecutionState.Mode.PREPARING,
                "an unindexed Java object is only a pending join and must never make a source actor HOT");
        helper.assertTrue(!SourceGrayboxActorMaterializer.hasObservedActorBody(helper.getLevel(), admitted, actor),
                "the exact UUID must be present in ServerLevel before it counts as a physical actor body");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void indexedButUnaddedBodyCannotActivateAnActor(GameTestHelper helper) {
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        var actor = data.actorExecution().actors().stream()
                .filter(value -> value.kind() == ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT)
                .findFirst().orElseThrow();
        long gameTick = data.actorExecutionGameTime(helper.getLevel().getGameTime());
        helper.assertTrue(data.prepareActor(actor.id(), SourceGrayboxActorExecutionRuntime.HOLDER, gameTick),
                "the stale-index fixture needs one PREPARING lease");
        actor = data.actorExecution().actor(actor.id()).orElseThrow();
        Villager stale = new Villager(EntityType.VILLAGER, helper.getLevel());
        stale.setUUID(SourceGrayboxMaterializer.uuid("resident", actor.id()));
        stale.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ID, actor.id());
        stale.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_KIND, "RESIDENT");
        stale.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_REVISION, data.snapshot().stateRevision());
        stale.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ACTOR_REVISION, actor.sourceRevision());
        helper.assertTrue(helper.getLevel().addFreshEntity(stale),
                "the fixture must first create the UUID index entry");
        stale.onRemovedFromLevel();
        helper.assertTrue(helper.getLevel().getEntity(stale.getUUID()) == stale,
                "the fixture must retain the stale UUID entry after addition is revoked");
        helper.assertTrue(!stale.isAddedToLevel(),
                "the fixture must distinguish an indexed reference from a body admitted to the Minecraft level");
        Map<String, Entity> admitted = new LinkedHashMap<>();
        admitted.put(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()), stale);

        new SourceGrayboxActorExecutionRuntime().reconcileLoadedActors(helper.getLevel(), data,
                new SourceGrayboxMaterializer(), admitted);

        helper.assertValueEqual(data.actorExecution().actor(actor.id()).orElseThrow().mode(), ReferenceGrayboxActorExecutionState.Mode.PREPARING,
                "a stale UUID lookup entry is not a physical body and must never make a source actor HOT");
        helper.assertTrue(!SourceGrayboxActorMaterializer.hasObservedActorBody(helper.getLevel(), admitted, actor),
                "the observed-body predicate requires active Minecraft-level admission, not only a UUID-map reference");
        helper.assertTrue(SourceGrayboxActorMaterializer.existingActor(helper.getLevel(),
                        SourceGrayboxPresentationLedger.get(helper.getLevel()), admitted, actor.id(), "RESIDENT", stale.getUUID()) == null,
                "a stale UUID entry must not be reused as a materializer body and renew a phantom reservation");
        helper.assertTrue(stale.isRemoved(),
                "the exact stale PM carrier must be discarded before a later COLD actor may obtain a fresh body");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void runtimeRetirementReleasesTheExactReservationAfterAcknowledgement(GameTestHelper helper) {
        // Keep a separate canonical identity from the other seed-42 fixtures
        // that share a GameTest ServerLevel.
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42_992L);
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(helper.getLevel());
        var initial = data.actorExecution().actors().stream()
                .filter(actor -> actor.kind() == ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT)
                .filter(actor -> zone.safeToDrain(position(actor)))
                .findFirst().orElseThrow(() -> new IllegalStateException("GameTest fixture has no resident outside the drain safety radius"));
        String residentId = initial.id();
        BlockPos position = position(initial);
        helper.getLevel().getChunkAt(position);
        TestBorder border = TestBorder.openAround(helper.getLevel().getWorldBorder(), position);
        try {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                helper.getLevel().setBlock(position.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(position.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(position.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
            SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
            Map<String, Entity> admitted = new LinkedHashMap<>();
            discardPriorCanonicalResident(helper, residentId);
            helper.assertTrue(data.prepareActor(residentId, SourceGrayboxActorExecutionRuntime.HOLDER, 1L),
                    "the source actor must have one runtime-owned executor before retirement");
            String lease = data.actorExecution().actor(residentId).orElseThrow().leaseId();
            materializer.apply(helper.getLevel(), data.snapshot(), data.actorExecution(), admitted);
            helper.assertTrue(data.activateActor(residentId, lease, SourceGrayboxActorExecutionRuntime.HOLDER, 2L),
                    "the physical executor must be HOT before its source actor disappears");
            helper.assertTrue(data.actorExecution().reconcile(withoutResident(data.snapshot(), residentId)),
                    "the canonical departure must retain an explicit RETIRED executor until runtime acknowledgement");
            String key = SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT");
            helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed(key),
                    "retirement starts with the exact existing duplicate-prevention reservation");
            helper.assertTrue(new SourceGrayboxActorExecutionRuntime().reconcileLoadedActors(helper.getLevel(), data, materializer, admitted),
                    "runtime must acknowledge the removed source actor only after discarding its observed body");
            helper.assertTrue(data.actorExecution().actor(residentId).isEmpty(),
                    "the acknowledged retired actor must no longer retain a source execution lease");
            helper.assertTrue(!SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed(key),
                    "a retired actor must release its duplicate-prevention reservation after acknowledgement");
            helper.assertValueEqual(residents(helper, position, residentId), 0,
                    "retirement acknowledgement must leave no orphaned physical body");
        } finally {
            border.restore();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeSceneEffectCarrierAndScarHandOffsSurviveOneSavedDataRestart(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        String actorId = source.snapshot().residents().getFirst().id();
        helper.assertTrue(source.prepareActor(actorId, HOLDER, 20L), "the active scene must retain one exact actor lease");
        String lease = source.actorExecution().actor(actorId).orElseThrow().leaseId();
        helper.assertTrue(source.activateActor(actorId, lease, HOLDER, 21L), "the actor must be HOT before its restart hand-off");
        helper.assertTrue(source.captureActor(actorId, lease, HOLDER, 4_321, -876, 22L), "the active scene needs an exact fixed-point actor hand-off");
        String effectId = "gametest:hot-cold:effect";
        helper.assertTrue(ControlledEffectExecutor.executeOnceWithReceipt(source,
                EffectLease.planned(effectId, effectId, "reference-graybox", source.snapshot().profileId(), actorId, "melee", 22L, 23L),
                22L, () -> "beforeHealth16=320;afterHealth16=288;landed=true"),
                "a completed local effect must have a terminal receipt before restart");
        SourceGrayboxOperationCargoCarrierLedger.Binding carrier = new SourceGrayboxOperationCargoCarrierLedger.Binding(
                "operation-carrier:operation:991:cargo:food", "operation:991:cargo:food", 991, ReferenceResource.FOOD, 64,
                4_321, -876, SourceGrayboxOperationCargoCarrierLedger.Mode.HOT);
        helper.assertTrue(source.operationCarrierLedger().put(carrier), "the real operation carrier needs one durable HOT hand-off");
        source.markOperationCarrierLedgerDirty();
        BlockPos scarPosition = new BlockPos(12, ReferenceGrayboxLayout.GROUND_Y + 3, -4);
        helper.assertTrue(source.physicalScars().record("gametest:hot-cold:breach", scarPosition,
                Blocks.OAK_PLANKS.defaultBlockState(), Blocks.AIR.defaultBlockState()),
                "a real physical consequence must remain a bounded scar rather than a presentation repair request");
        source.markPhysicalScarsDirty();

        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(source.save(new CompoundTag(), null), null);
        var actor = restored.actorExecution().actor(actorId).orElseThrow();
        helper.assertValueEqual(actor.mode(), ReferenceGrayboxActorExecutionState.Mode.HOT, "restart encoding must retain the unfinished actor mode for recovery");
        helper.assertValueEqual(actor.actualXSixteenths(), 4_321, "restart encoding must retain exact actor x custody");
        helper.assertValueEqual(actor.actualZSixteenths(), -876, "restart encoding must retain exact actor z custody");
        helper.assertValueEqual(restored.effectLeases().find(effectId).orElseThrow().receipt(), "beforeHealth16=320;afterHealth16=288;landed=true",
                "a completed live effect must not become replayable after restart");
        helper.assertValueEqual(restored.operationCarrierLedger().binding(carrier.id()), carrier,
                "the carrier's exact item count, fixed-point position and HOT/COLD mode must survive restart together");
        helper.assertTrue(restored.physicalScars().scars().stream().anyMatch(scar -> scar.effectId().equals("gametest:hot-cold:breach")
                        && scar.x() == scarPosition.getX() && scar.y() == scarPosition.getY() && scar.z() == scarPosition.getZ()),
                "restart must retain actual terrain aftermath without treating it as a missing source block");
        helper.succeed();
    }

    private static Villager resident(GameTestHelper helper, BlockPos anchor, String residentId) {
        return helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(32), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).stream().findFirst().orElseThrow();
    }

    private static int residents(GameTestHelper helper, BlockPos anchor, String residentId) {
        return helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(32), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).size();
    }

    /**
     * GameTest structures share one ServerLevel.  A preceding fixture may
     * have deliberately materialized the same production actor ID, while
     * this fixture must prove its own admission/recovery path.  Remove only
     * that exact stale PM carrier and its matching presentation reservation.
     */
    private static void discardPriorCanonicalResident(GameTestHelper helper, String residentId) {
        String key = SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT");
        Entity prior = helper.getLevel().getEntity(SourceGrayboxMaterializer.uuid("resident", residentId));
        if (prior != null && SourceGrayboxMaterializer.identityMatches(prior, residentId, "RESIDENT")) prior.discard();
        SourceGrayboxPresentationLedger.get(helper.getLevel()).releaseEntity(key);
    }

    private static int sixteenths(double value) {
        return Math.toIntExact(Math.round(value * ReferenceGrayboxActorExecutionState.POSITION_SCALE));
    }

    private static BlockPos position(ReferenceGrayboxActorExecutionState.ActorState actor) {
        return new BlockPos(Math.floorDiv(actor.actualXSixteenths(), ReferenceGrayboxActorExecutionState.POSITION_SCALE),
                ReferenceGrayboxLayout.GROUND_Y + 1, Math.floorDiv(actor.actualZSixteenths(), ReferenceGrayboxActorExecutionState.POSITION_SCALE));
    }

    private static ReferenceGrayboxSnapshot withoutResident(ReferenceGrayboxSnapshot snapshot, String residentId) {
        return new ReferenceGrayboxSnapshot(snapshot.day(), snapshot.profileId(), snapshot.stateRevision(), snapshot.bounds(), snapshot.cells(),
                snapshot.settlements(), snapshot.facilities(), snapshot.resourceSites(), snapshot.routes(), snapshot.hiveOrgans(), snapshot.bioforms(),
                snapshot.residents().stream().filter(resident -> !resident.id().equals(residentId)).toList(), snapshot.fieldPosts(), snapshot.fieldLinks(),
                snapshot.activities(), snapshot.cargoes(), snapshot.interactions(), snapshot.sectors(), snapshot.chrysalises(), snapshot.events());
    }

    private record TestBorder(WorldBorder border, double centerX, double centerZ, double size) {
        static TestBorder openAround(WorldBorder border, BlockPos position) {
            TestBorder prior = new TestBorder(border, border.getCenterX(), border.getCenterZ(), border.getSize());
            border.setCenter(position.getX() + .5d, position.getZ() + .5d);
            border.setSize(64.0d);
            return prior;
        }

        void restore() {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }
}
