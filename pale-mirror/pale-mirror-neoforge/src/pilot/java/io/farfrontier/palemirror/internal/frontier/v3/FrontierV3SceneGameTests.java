package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.world.SourceGrayboxEntityAdmission;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;
import io.farfrontier.palemirror.frontier.v3.model.ResidentRole;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.TraversalKind;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopology;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopologyId;
import io.farfrontier.palemirror.frontier.v3.model.TransportAnchor;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Materialized ownership and conflict evidence for exact v3 HOT scene bodies. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SceneGameTests {
    private FrontierV3SceneGameTests() { }


    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void declaredPhysicalExecutionOrderIsBoundedAndReadOnly(GameTestHelper helper) {
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:physical-execution-plan"), 91L),
                        new EphemeralStore(), 20_000);
        try {
            String execution = FrontierV3PhysicalExecutionDiagnostic.render(runtime.checkpointImage().orElseThrow());
            helper.assertTrue(execution.contains("\"kind\":\"execution\"")
                            && execution.contains("\"id\":\"physical-observation\"")
                            && execution.contains("\"id\":\"scenes\""),
                    "the physical execution diagnostic must expose the closed registered plan");
            helper.assertTrue(execution.indexOf("\"id\":\"physical-observation\"") < execution.indexOf("\"id\":\"scenes\""),
                    "the read-only plan must preserve observation before scene execution");
            helper.assertTrue(execution.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 8_192,
                    "the execution plan diagnostic must remain bounded");
        } finally {
            runtime.shutdown();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotSceneWaitsForStableAbsenceAndSafeDistanceBeforeColdHandoff(GameTestHelper helper) {
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:scene-hysteresis-game-test"), 91L), new EphemeralStore(), 20_000);
        SceneLeaseId leaseId = new SceneLeaseId("lease:scene-hysteresis-game-test");
        try {
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 1L, demand(false), false),
                    "the first absent-demand tick must retain a HOT scene instead of abruptly despawning it");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 200L, demand(false), false),
                    "the final tick before the bounded 200-tick hand-off window must remain HOT");
            helper.assertTrue(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 201L, demand(false), false),
                    "only sustained absent demand may permit a COLD hand-off");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 202L, demand(true), false),
                    "returning player demand must cancel the pending drain rather than leaving a latent despawn");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 203L, demand(false), false),
                    "a fresh departure begins a new bounded hysteresis interval");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 403L, demand(false), true),
                    "an otherwise absent player near an exact scene body prevents unsafe capture");
            helper.assertTrue(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 404L, demand(false), false),
                    "once safely distant after the hysteresis, the next durable release may proceed");
        } finally {
            FrontierV3SceneExecutor.forget(runtime);
            runtime.shutdown();
        }
        helper.succeed();
    }


    private static FrontierV3SceneDemand.Snapshot demand(boolean active) {
        return new FrontierV3SceneDemand.Snapshot(true, active
                ? java.util.Set.of(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")) : java.util.Set.of());
    }

    @GameTest(batch = "pm-frontier-v3-scene-restart-reclaim", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void restartRecoveryWaitsForBoundedEntityRegistrationAfterNaturalLoad(GameTestHelper helper) {
        long completeLoadTick = 40L;
        helper.assertFalse(FrontierV3SceneExecutor.restartRecoveryObservationReady(completeLoadTick, completeLoadTick),
                "the first naturally loaded tick is not yet evidence that saved entity UUIDs are absent");
        helper.assertFalse(FrontierV3SceneExecutor.restartRecoveryObservationReady(completeLoadTick, 59L),
                "recovery must not turn a short entity-registration delay into a durable missing-body fact");
        helper.assertTrue(FrontierV3SceneExecutor.restartRecoveryObservationReady(completeLoadTick, 60L),
                "after the fixed complete-load observation barrier, a missing owned UUID is durable recovery evidence");
        helper.succeed();
    }


    @GameTest(batch = "pm-frontier-v3-scene-first-admission", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void newBodiesHydrateHealthForBothKindsAndOwners(GameTestHelper helper) {
        var condition = new io.farfrontier.palemirror.frontier.v3.model.ActorCondition(
                ActorLifeStatus.ALIVE, new FixedScalar(7_250_000L));
        for (var kind : ActorKind.values()) {
            for (var owner : FrontierV3ActorCarrierComposition.Owner.values()) {
                var declaration = new FrontierV3ActorCarrierComposition.Declaration(
                        new SubjectId(("actor:health-" + kind.name() + "-" + owner.name()).toLowerCase(java.util.Locale.ROOT)), kind, owner,
                        java.util.UUID.randomUUID(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 1L);
                var producer = FrontierV3ActorCarrierComposition.InventoryEntry.ACTOR_BODY;
                var body = FrontierV3ActorCarrierFactory.create(producer, helper.getLevel(), declaration, condition);
                helper.assertValueEqual(body.getHealth(), 7.25F, "new body must retain canonical injury");
                var saved = new CompoundTag();
                body.saveWithoutId(saved);
                helper.assertValueEqual(saved.getFloat("Health"), 7.25F, "entity NBT must retain hydrated injury");
                body.discard();
            }
        }
        helper.succeed();
    }


    @GameTest(batch = "pm-frontier-v3-ambient-actors", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ambientActorsKeepExactIdAndUseVillagerOrZombieKind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:ambient-test"), 91L));
        SubjectId resident = new SubjectId("resident:1-1"); SubjectId bioform = new SubjectId("bioform:west-0");
        state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.demand(state, resident);
        state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.demand(state, bioform);
        bootstrapFirstAdmissions(level, state);
        BlockPos residentSpot = helper.absolutePos(new BlockPos(2, 8, 2)); BlockPos bioformSpot = helper.absolutePos(new BlockPos(4, 8, 2));
        prepareFloor(level, residentSpot); prepareFloor(level, bioformSpot);
        helper.assertValueEqual(FrontierV3BodyAdmissionGameTestFixture.ambient(level, state, resident,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an exact resident receives one owned Villager body");
        helper.assertValueEqual(FrontierV3BodyAdmissionGameTestFixture.ambient(level, state, bioform,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(bioformSpot.getX(), bioformSpot.getY(), bioformSpot.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an exact hive bioform receives one owned Zombie body");
        helper.assertTrue(level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, resident)) instanceof Villager, "resident identity maps to Villager");
        helper.assertTrue(level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, bioform)) instanceof net.minecraft.world.entity.monster.Zombie, "bioform identity maps to Zombie");
        helper.assertTrue(((Zombie) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, bioform))).hasEffect(MobEffects.FIRE_RESISTANCE),
                "an ambient graybox bioform must survive daylight without becoming an unaccounted vanilla death");
        helper.assertTrue(!((Zombie) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, bioform))).getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                "an ambient bioform has a physical role marker that suppresses vanilla daylight flames");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, resident,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "a repeated loaded-chunk pass never duplicates the exact resident");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, new SubjectId("resident:unknown"),
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.CONFLICT,
                "an unknown canonical identity is never converted into a new Villager body");
        level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, resident)).discard();
        level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, bioform)).discard();
        helper.succeed();
    }


    @GameTest(batch = "pm-frontier-v3-ambient-restart-reclaim", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void restoredOwnedBodyReclaimsUnknownAmbientLeaseWithoutDuplication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0)); prepareFloor(level, origin);
        WorldId world = new WorldId("frontier:ambient-reclaim-game-test");
        SubjectId resident = new SubjectId("resident:1-1");
        var config = FrontierV3AmbientActorGameTests.configurationAt(helper, world, 91L, resident);
        var store = new EphemeralStore();
        FrontierV3AmbientActorGameTests.initializeAdmission(level, config, store);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(config, store, 10_000);
        FrontierWorldState initial = state(runtime);
        AmbientActorLease lease = AmbientActorProcess.nextLease(initial, resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "ambient-game-test-prepare", resident.value(), new AmbientLeasePrepared(lease));
        helper.assertValueEqual(FrontierV3BodyAdmissionGameTestFixture.ambient(level, state(runtime), resident, lease.handoffBody()),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "common producer admits the original body before recovery input");
        Entity original = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
        helper.assertTrue(original instanceof Villager, "the original common body must be indexed");
        FrontierV3ActorBodyController.confirmPresent(level, runtime, original);
        FrontierV3CommandSubmission.submit(runtime, "ambient-game-test-hot", resident.value(),
                new io.farfrontier.palemirror.frontier.v3.model.AmbientBodyConfirmed(resident, lease.revision(),
                        io.farfrontier.palemirror.frontier.v3.model.AmbientBodyConfirmed.Boundary.ADMISSION,
                        lease.handoffBody(), lease.handoffBody(),
                        io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state(runtime), resident)));
        helper.assertValueEqual(FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                "restart recovery must make an active ambient lease UNKNOWN before any body is accepted");

        // Actual vanilla serialization supplies declaration/residence/equipment.
        // This component test is not a region-file crash/restart acceptance claim.
        var saved = new net.minecraft.nbt.CompoundTag();
        helper.assertTrue(original.save(saved), "the real common body must serialize");
        original.discard();
        Entity restored = EntityType.loadEntityRecursive(saved, level, entity -> entity);
        helper.assertTrue(restored instanceof Villager && restored.getUUID().equals(original.getUUID()),
                "vanilla NBT restores the same declared body identity");

        FrontierV3ServerLifecycle.JoinFirewallProof restoredProof = FrontierV3ServerLifecycle.observeSourceJoin(level, runtime, restored);
        helper.assertValueEqual(restoredProof.lifecycleAdmission(), FrontierV3ServerLifecycle.EntityJoinAdmission.RETAINED,
                "the exact restored body must enter lifecycle admission before Minecraft publishes its UUID index");
        helper.assertTrue(restoredProof.verifiedV3Carrier()
                        && !SourceGrayboxEntityAdmission.rejectsSourceMob(restoredProof, true, false),
                "the ordinary source firewall must preserve only the exact deferred V3 body");
        helper.assertTrue(FrontierV3GrayboxExecutor.admissionProvider(runtime, runtime.decodedState().orElseThrow()).isEmpty(),
                "the bridge must not obtain a default or synchronous global provider before projection");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.UNKNOWN_AFTER_RESTART,
                "the pre-projection join must not mutate the durable UNKNOWN lease");
        helper.assertTrue(level.addFreshEntity(restored),
                "only the accepted ordinary lifecycle/firewall result may let the restored body enter the loaded world");

        helper.runAfterDelay(2L, () -> {
        FrontierV3ServerLifecycle.observeSourceJoin(level, runtime, restored);
        FrontierV3AmbientPendingAdmissions.reclaimProjected(runtime, state(runtime));
        helper.assertTrue(FrontierV3ActorBodyController.readyForExecution(level, state(runtime), List.of(
                        io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state(runtime), resident))),
                "the common owner confirms indexed same-incarnation presence before activity recovery");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.UNKNOWN_AFTER_RESTART,
                "indexed body confirmation cannot restore an activity scope");
        FrontierV3AftermathOwnerComposition.projection(level, runtime);
        // The bounded round-robin probe need not select this resident on its first
        // turn (the complete roster contains hundreds of other actors). Observe
        // the actual owner transition, not a one-callback scheduling coincidence.
        helper.succeedWhen(() -> {
            FrontierV3AmbientActorExecutor.tick(level, runtime);
            helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                    "the next compatible projection must permit exactly the ordinary reclaim handoff");
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                            new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(origin.getX(), origin.getY(), origin.getZ())), FrontierV3AmbientActorExecutor.Result.CURRENT,
                    "reclaim must retain the existing body instead of creating another one");
            helper.assertTrue(level.getEntity(restored.getUUID()) == restored, "the observed restored body remains the sole UUID owner");
            FrontierV3ServerLifecycle.releaseRuntime(runtime);
            restored.discard();
        });
        });
    }

    @GameTest(batch = "pm-frontier-v3-ambient-restart-reclaim", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void foreignSourceMobIsImmediatelyRejectedBeforeFirstProjection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:foreign-before-projection-game-test"), 91L), new EphemeralStore(), 10_000);
        Zombie foreign = EntityType.ZOMBIE.create(level);
        helper.assertTrue(foreign != null, "the foreign-mob fixture must be constructible");
        FrontierV3ServerLifecycle.JoinFirewallProof foreignProof = FrontierV3ServerLifecycle.observeSourceJoin(runtime, foreign);
        helper.assertValueEqual(foreignProof.lifecycleAdmission(), FrontierV3ServerLifecycle.EntityJoinAdmission.NOT_MANAGED,
                "a foreign source mob must not acquire the deferred V3 bridge");
        helper.assertTrue(!foreignProof.verifiedV3Carrier()
                        && SourceGrayboxEntityAdmission.rejectsSourceMob(foreignProof, true, false),
                "the ordinary source firewall must still reject a foreign mob immediately");
        foreign.discard(); FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); helper.succeed();
    }


    private static void bootstrapFirstAdmissions(ServerLevel level, FrontierWorldState state) {
        WorldId world = state.bootstrap().worldId();
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, state,
                new RecoveryImage(world, Optional.empty(), List.of()), () -> ledger.persist(level, world));
    }


    /** Constructor fixture only: demand is not an acknowledgement of insertion or HOT readiness. */
    private static FrontierWorldState demandFixtureBodies(FrontierWorldState state, List<SceneMember> members) {
        for (var member : members) state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.demand(state, member.actorId());
        return state;
    }
    /** Explicit test-cell projection; never submitted as a canonical physical observation. */
    private static java.util.Map<SubjectId, BodyPosition> localFixtureBodies(SceneLease lease, BlockPos origin) {
        var bodies = new java.util.LinkedHashMap<SubjectId, BodyPosition>();
        for (int index = 0; index < lease.members().size(); index++)
            bodies.put(lease.members().get(index).actorId(), new BodyPosition(origin.getX() + index * 2, origin.getY(), origin.getZ()));
        return java.util.Map.copyOf(bodies);
    }
    private static SceneMember member(WorldId world, String actorId) {
        SubjectId actor = new SubjectId(actorId);
        return new SceneMember(actor, SceneLease.deterministicEntityId(world, actor));
    }
    private static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
    }
    static void prepareFloorWithinTemplate(GameTestHelper helper, ServerLevel level, AABB templateBounds, BlockPos position) {
        requireBlockWithinTemplate(helper, templateBounds, position.below(), "the fixture support-block write must remain inside the authored envelope");
        requireBlockWithinTemplate(helper, templateBounds, position, "the fixture body-cell write must remain inside the authored envelope");
        requireBlockWithinTemplate(helper, templateBounds, position.above(), "the fixture headroom write must remain inside the authored envelope");
        prepareFloor(level, position);
    }
    private static void requireBlockWithinTemplate(GameTestHelper helper, AABB templateBounds, BlockPos position, String message) {
        helper.assertTrue(templateBounds.contains(Vec3.atCenterOf(position)), message);
    }
    static void requireEntityWithinTemplate(GameTestHelper helper, AABB templateBounds, Entity entity, String message) {
        AABB body = entity.getBoundingBox();
        helper.assertTrue(templateBounds.contains(new Vec3(body.minX, body.minY, body.minZ))
                        && templateBounds.contains(new Vec3(body.maxX, body.maxY, body.maxZ)), message);
    }
    private static BlockPos cargoPosition(BlockPos anchor, SceneLease lease) {
        return anchor;
    }
    /** Local GameTest representation of the one already-canonical bounded cargo stack. */


    static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElseThrow(() -> new IllegalStateException("the v3 GameTest runtime must remain active: "
                + runtime.status().detail().orElse(runtime.status().kind().name())));
    }
    private static String admissionDetail(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneMember member, Entity body) {
        if (body == null) return "missing body for " + member.actorId() + " expectedUuid=" + member.entityId();
        String lease = body.getPersistentData().getString(FrontierV3SceneExecutor.LEASE_KEY);
        String actor = body.getPersistentData().getString(FrontierV3SceneExecutor.ACTOR_KEY);
        long revision = body.getPersistentData().getLong(FrontierV3SceneExecutor.REVISION_KEY);
        return "actor=" + member.actorId() + ", expectedUuid=" + member.entityId() + ", actualUuid=" + body.getUUID()
                + ", type=" + body.getType() + ", removed=" + body.isRemoved() + ", lease=" + lease
                + ", actorTag=" + actor + ", revision=" + revision + ", canonicalRevision="
                + state(runtime).sceneLeases().values().stream().filter(value -> value.id().value().equals(lease))
                .map(value -> value.revision() + "/" + value.status()).findFirst().orElse("missing");
    }
    /** GameTest-only memory port: the filesystem restart proof lives in FrontierV3ServerRuntimeTest. */
    static class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }

}
