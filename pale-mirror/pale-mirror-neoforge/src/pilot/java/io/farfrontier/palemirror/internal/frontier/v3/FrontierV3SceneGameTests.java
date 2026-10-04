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
import io.farfrontier.palemirror.frontier.v3.model.OperationTravel;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeasePrepared;
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
    public static void hotTravelTargetRetainsTheSurveyedOneBlockGrade(GameTestHelper helper) {
        SubjectId hauler = new SubjectId("resident:grade-hauler");
        TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:scene-grade"), 1L,
                new SubjectId("route:frontier-network"), TraversalKind.PEDESTRIAN,
                java.util.Set.of(TraversalCapability.PEDESTRIAN, TraversalCapability.GROUND_BIOFORM),
                List.of(SurfaceAnchor.at(4, 63, 8), SurfaceAnchor.at(5, 64, 8)));
        OperationTravel travel = new OperationTravel(topology, 0, java.util.Map.of(hauler, new BodyPosition(4, 65, 9)),
                TransportAnchor.atLegacySupport(new BlockPosition(4, 63, 7)));

        helper.assertValueEqual(FrontierV3SceneExecutor.operationTravelTargetPosition(travel, hauler), new BodyPosition(5, 66, 9),
                "the HOT adapter must retain the next canonical grade instead of flattening its target Y");
        helper.succeed();
    }

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

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void twoRealObserversShareOneDemandLeaseWithoutProgressAcceleration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos anchor = helper.absolutePos(new BlockPos(0, 8, 0));
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        first.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        second.setPos(anchor.getX() + 1.5D, anchor.getY(), anchor.getZ() + 0.5D);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3SceneBodyGameTestFixture.start(helper, new WorldId("frontier:two-observer-demand"), 91L, new EphemeralStore());
        SceneLeaseId leaseId = new SceneLeaseId("lease:two-observer-demand");
        try {
            SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst();
            var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("two-observer fixture runtime must be active"));
            SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
            FrontierV3CommandSubmission.submit(runtime, "two-observer-lease-prepare", leaseId.value(), new SceneLeasePrepared(lease));
            FrontierV3SceneBodyGameTestFixture.materializeAndObserve(helper, runtime, lease);
            FrontierV3CommandSubmission.submit(runtime, "two-observer-lease-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
            var beforeObservers = runtime.checkpointImage().orElseThrow();
            var both = FrontierV3SceneExecutor.demandSnapshot(helper.getLevel(), new BlockPosition(anchor.getX(), anchor.getY(), anchor.getZ()));
            helper.assertValueEqual(both.observerIds(), Set.of(first.getUUID(), second.getUUID()),
                    "two actual observers must be one bounded demand aggregate, never two physical leases");
            helper.assertValueEqual(state(runtime).sceneLeases().entrySet().stream().filter(entry -> entry.getKey().equals(leaseId)).count(), 1L,
                    "both observers must retain exactly one canonical lease record");
            helper.assertValueEqual(lease.members().stream().map(SceneMember::entityId).filter(id -> level.getEntity(id) != null).count(), (long) lease.members().size(),
                    "both observers must see the one retained deterministic body set, not one set per player");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 1L, both, false),
                    "aggregate demand must not release a HOT lease");
            helper.assertValueEqual(runtime.checkpointImage().orElseThrow(), beforeObservers,
                    "observer count is read-only demand and may not accelerate canonical progress or schedules");

            first.setPos(anchor.getX() + FrontierV3SceneDemand.RADIUS_BLOCKS + 8.5D, anchor.getY(), anchor.getZ() + 0.5D);
            var one = FrontierV3SceneExecutor.demandSnapshot(helper.getLevel(), new BlockPosition(anchor.getX(), anchor.getY(), anchor.getZ()));
            helper.assertValueEqual(one.observerIds(), Set.of(second.getUUID()), "first departure must retain the exact shared demand");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 201L, one, false),
                    "one remaining observer prevents premature drain of the exact retained HOT lease");
            helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).status(), SceneLeaseStatus.HOT,
                    "the first departure may not drain the physical lease while the other observer remains");
            helper.assertValueEqual(runtime.checkpointImage().orElseThrow(), beforeObservers,
                    "a join/leave change may not add schedule work or advance the process while demand remains aggregate-active");

            second.setPos(anchor.getX() + FrontierV3SceneDemand.RADIUS_BLOCKS + 9.5D, anchor.getY(), anchor.getZ() + 0.5D);
            var none = FrontierV3SceneExecutor.demandSnapshot(helper.getLevel(), new BlockPosition(anchor.getX(), anchor.getY(), anchor.getZ()));
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 202L, none, false),
                    "final departure begins one bounded hysteresis window, not an immediate release");
            helper.assertTrue(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 402L, none, false),
                    "only zero aggregate demand through the full window permits the one final physical release");
            FrontierV3CommandSubmission.submit(runtime, "two-observer-lease-drain", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.DRAINING));
            long revisionBeforeRelease = runtime.canonicalState().orElseThrow().revision().value();
            FrontierV3CommandSubmission.submit(runtime, "two-observer-lease-release", leaseId.value(), new SceneLeaseReleased(leaseId,
                    lease.members().stream().map(member -> new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(
                            member.actorId(), lease.memberBody(state(runtime).actorLocations(), member.actorId()))).toList()));
            helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).status(), SceneLeaseStatus.CLOSED,
                    "the exact canonical lease must close once, after its final aggregate-demand release");
            helper.assertValueEqual(runtime.canonicalState().orElseThrow().revision().value(), revisionBeforeRelease + 1L,
                    "one and only one canonical transition records the final physical release");
        } finally {
            var scope = state(runtime).sceneLeases().get(leaseId);
            if (scope != null) scope.members().forEach(member -> {
                Entity entity = level.getEntity(member.entityId()); if (entity != null) entity.discard();
            });
            FrontierV3SceneExecutor.forget(runtime);
            runtime.shutdown();
            helper.getLevel().getServer().getPlayerList().remove(first);
            helper.getLevel().getServer().getPlayerList().remove(second);
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

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void logisticsSceneNeverAcquiresCombatAuthorityWithoutAnEngagement(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(8, 8, 0));
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scene-combat-authority-game-test"), 91L));
        SceneLease logistics = lease(state, origin);
        SceneLease engagement = fixtureLease(new SceneLeaseId("lease:scene-combat-authority-game-test"), logistics.worldId(),
                FrontierSceneBehaviors.logistics(logistics).operationId(), FrontierSceneBehaviors.logistics(logistics).cargoId(),
                logistics.handoffPosition(), logistics.handoffInstant(), logistics.revision(), logistics.status(), Optional.of(new SubjectId("engagement:scene-combat-authority-game-test")), logistics.members());
        helper.assertFalse(FrontierV3SceneExecutor.combatEnabled(logistics),
                "a HOT route carrier may move exact cargo but must not invent combat against its own escort");
        helper.assertTrue(FrontierV3SceneExecutor.combatEnabled(engagement),
                "only a coupled canonical engagement lease may unlock the durable combat/effect executor");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneCreatesOnlyItsExactVillagerBodies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0));
        prepareFloor(level, origin); prepareFloor(level, origin.east(2));
        // A route deck may physically occupy the strategic hand-off height.
        level.setBlock(origin, Blocks.GRAY_CARPET.defaultBlockState(), 3);
        WorldId world = new WorldId("frontier:scene-body-test");
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        List<SceneMember> members = state.humanPopulation().residents().keySet().stream().sorted().limit(2)
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))).toList();
        state = demandFixtureBodies(state, members);
        SceneLease lease = SceneLease.atExactPositions(new SceneLeaseId("lease:frontier-v3-game-test"), world,
                new SubjectId("operation:frontier-v3-game-test"), new SubjectId("cargo:frontier-v3-game-test"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), new BlockPosition(origin.getX() + 3, origin.getY() - 1, origin.getZ()),
                SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED, Optional.empty(), members);

        helper.assertFalse(FrontierV3SceneExecutor.entityStorageReady(true, false),
                "a production scene must wait for saved entity storage before admitting deterministic body UUIDs");
        bootstrapFirstAdmissions(level, state);
        var fixtureBodies = new java.util.LinkedHashMap<>(localFixtureBodies(lease, origin));
        // The carpet is the actual upper collision support, not the stone beneath it.
        fixtureBodies.put(members.getFirst().actorId(), new BodyPosition(origin.getX(), origin.getY() + 1, origin.getZ()));
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease, fixtureBodies), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a loaded thin route surface must materialize each deterministic Villager body exactly once");
        for (SceneMember member : lease.members()) {
            Villager body = (Villager) level.getEntity(member.entityId());
            helper.assertTrue(body != null, "each leased actor must have its deterministic Villager body");
            helper.assertValueEqual(FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow().owner(),
                    FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                    "a scene participant retains independent body ownership");
            helper.assertValueEqual(body.getPersistentData().getString(FrontierV3SceneExecutor.ACTOR_KEY), member.actorId().value(),
                    "materialized body must carry its canonical actor identity");
            helper.assertTrue(body.isNoAi(), "a HOT body must not retain uncontrolled vanilla AI or combat authority");
            if (member.equals(lease.members().getFirst())) {
                helper.assertTrue(body.getY() > origin.getY(), "a scene body must stand on the actual carpet collision top, never inside it");
            }
            body.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void activeSceneBodiesCarryStrictGrayboxAdmissionProof(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(2, 8, 2));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3SceneBodyGameTestFixture.start(helper, new WorldId("frontier:scene-admission-proof"), 91L, new EphemeralStore());
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst();
        SceneLeaseId leaseId = new SceneLeaseId("lease:scene-admission-proof");
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("the admission fixture runtime must remain active"));
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
        FrontierV3CommandSubmission.submit(runtime, "scene-admission-proof-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        var admittedBodies = FrontierV3SceneBodyGameTestFixture.materializeAndObserve(helper, runtime, lease);
        // An isolated runtime receives the same actual source joins after UUID indexing;
        // insertion and scope preparation alone never synthesize physical acknowledgement.
        helper.runAfterDelay(2L, () -> {
            try {
                for (int index = 0; index < lease.members().size(); index++) {
                    SceneMember member = lease.members().get(index);
                    Entity body = level.getEntity(member.entityId());
                    helper.assertTrue(body == admittedBodies.get(index) && FrontierV3ServerLifecycle.observeSourceJoin(level, runtime, body).verifiedV3Carrier()
                                    && FrontierV3SceneExecutor.recognizes(runtime, body),
                            "only indexed bodies with actual common admission provenance may participate: "
                                    + admissionDetail(runtime, member, body));
                }
                FrontierV3AmbientPendingAdmissions.reclaimProjected(runtime, state(runtime));
                Zombie foreign = EntityType.ZOMBIE.create(level);
                helper.assertTrue(foreign != null && !FrontierV3SceneExecutor.recognizes(runtime, foreign),
                        "an untagged native mob must not acquire a scene admission proof");
                FrontierV3CommandSubmission.submit(runtime, "scene-admission-proof-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
                FrontierV3CommandSubmission.submit(runtime, "scene-admission-proof-drain", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.DRAINING));
                FrontierV3CommandSubmission.submit(runtime, "scene-admission-proof-close", leaseId.value(), new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased(leaseId,
                        lease.members().stream().map(member -> new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(member.actorId(), lease.memberBody(state(runtime).actorLocations(), member.actorId()))).toList()));
                Entity formerBody = level.getEntity(lease.members().getFirst().entityId());
                helper.assertTrue(formerBody != null && !FrontierV3SceneExecutor.recognizes(runtime, formerBody),
                        "a stale body from a closed scene must be denied rather than retained as a permanent exception");
                var retainedMember = lease.members().getFirst();
                var tombstone = state(runtime).fencedRecovery().tombstones().get(
                        ActorBodyId.recoveryBindingId(retainedMember.actorId()));
                helper.assertTrue(tombstone == null && FrontierV3ActorBodyController.readyForExecution(level, state(runtime),
                                List.of(io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state(runtime), retainedMember.actorId()))),
                        "closing an activity scope must not tombstone or retire its independently confirmed living body");
                FrontierV3SceneExecutor.cleanClosedBodies(level, state(runtime));
                helper.assertTrue(level.getEntity(lease.members().getFirst().entityId()) == formerBody
                                && !FrontierV3SceneExecutor.recognizes(runtime, formerBody)
                                && FrontierV3ActorBodyController.recognizes(state(runtime), formerBody),
                        "scope cleanup retains the same physical object under its common owner without retaining scene authority");
                helper.succeed();
            } finally {
                lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
                runtime.shutdown();
            }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-conflict", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneRefusesForeignBodyWithItsExpectedUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0));
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scene-conflict-test"), 91L));
        SceneLease lease = lease(state, origin);
        Villager foreign = EntityType.VILLAGER.create(level);
        helper.assertTrue(foreign != null, "the foreign body fixture must be constructible");
        foreign.setUUID(lease.members().getFirst().entityId());
        foreign.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        helper.assertTrue(level.addFreshEntity(foreign), "the foreign body fixture must enter the loaded world");

        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.CONFLICT,
                "an unowned body with a leased UUID is a visible conflict, never a body the executor claims");
        helper.assertTrue(level.getEntity(foreign.getUUID()) == foreign, "the foreign body must remain untouched");
        foreign.discard();
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-first-admission", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void firstResidentSceneConsumesOnlyItsIssuedPermission(GameTestHelper helper) {
        verifyFirstSceneAdmission(helper, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-first-admission", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void firstBioformSceneConsumesOnlyItsIssuedPermission(GameTestHelper helper) {
        verifyFirstSceneAdmission(helper, true);
    }

    @GameTest(batch = "pm-frontier-v3-scene-first-admission", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void recoveredResidentSceneResumesUnstartedAdmission(GameTestHelper helper) {
        verifyFirstSceneAdmission(helper, false, true);
    }

    @GameTest(batch = "pm-frontier-v3-scene-first-admission", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void recoveredBioformSceneResumesUnstartedAdmission(GameTestHelper helper) {
        verifyFirstSceneAdmission(helper, true, true);
    }

    private static void verifyFirstSceneAdmission(GameTestHelper helper, boolean bioform) {
        verifyFirstSceneAdmission(helper, bioform, false);
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

    private static void verifyFirstSceneAdmission(GameTestHelper helper, boolean bioform, boolean recovered) {
        var level = helper.getLevel(); var feet = helper.absolutePos(new BlockPos(0, 1, 0));
        prepareFloor(level, feet);
        var world = new WorldId((bioform ? "frontier:first-scene-bioform" : "frontier:first-scene-resident")
                + (recovered ? "-recovered" : ""));
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        var actor = new SubjectId(bioform ? "bioform:west-1" : "resident:1-1");
        var member = new SceneMember(actor, SceneLease.deterministicEntityId(world, actor));
        var state = demandFixtureBodies(initial, List.of(member));
        var lease = fixtureLease(new SceneLeaseId("lease:first-scene"), world,
                new SubjectId("operation:first-scene"), new SubjectId("cargo:first-scene"),
                new BlockPosition(feet.getX(), feet.getY(), feet.getZ()), SimInstant.ZERO, 1L,
                recovered ? SceneLeaseStatus.UNKNOWN_AFTER_RESTART : SceneLeaseStatus.PREPARED, Optional.empty(), List.of(member));
        var ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        helper.assertTrue(!FrontierV3SceneExecutor.canResumeUnstartedBodyAdmissions(level, state, lease),
                "missing physical history cannot authorize restart creation");
        var fixtureBodies = localFixtureBodies(lease, feet);
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease, fixtureBodies),
                FrontierV3SceneExecutor.BodyMaterialization.CONFLICT, "empty column without history is not creation permission");
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, state,
                new RecoveryImage(world, Optional.empty(), List.of()), () -> ledger.persist(level, world));
        helper.assertValueEqual(FrontierV3SceneExecutor.canResumeUnstartedBodyAdmissions(level, state, lease), recovered,
                "only an unknown scene with explicit unused permission can resume initial admission");
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease, fixtureBodies),
                FrontierV3SceneExecutor.BodyMaterialization.COMPLETE, "issued first permit must admit the exact scene member");
        helper.assertValueEqual(ledger.firstAdmission(actor).orElseThrow().phase(),
                FrontierV3ActorFirstAdmission.Phase.PENDING, "insertion alone is not a saved-body acknowledgement");
        helper.runAfterDelay(1L, () -> {
            var body = level.getEntity(member.entityId());
            helper.assertTrue(bioform ? body instanceof Zombie : body instanceof Villager, "canonical actor kind must be retained");
            ((Mob) body).setHealth(9.0F);
            helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease, fixtureBodies),
                    FrontierV3SceneExecutor.BodyMaterialization.COMPLETE, "repeat must reuse the same scene body");
            helper.assertTrue(level.getEntity(member.entityId()) == body, "repeat cannot replace the indexed body");
            helper.assertValueEqual(((Mob) body).getHealth(), 9.0F, "existing body must not be rehydrated from stale canonical health");
            body.discard();
            helper.runAfterDelay(1L, () -> {
                helper.assertTrue(!FrontierV3SceneExecutor.canResumeUnstartedBodyAdmissions(level, state, lease),
                        "absence after an attempted insertion must never authorize another initial body");
                helper.succeed();
            });
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneUsesCanonicalBioformIdentityForZombieBodies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 8, 2)); prepareFloor(level, origin);
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scene-bioform-test"), 91L));
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-bioform-test");
        SubjectId bioform = new SubjectId("bioform:west-0");
        SceneLease lease = fixtureLease(id, state.bootstrap().worldId(), new SubjectId("operation:frontier-v3-bioform-test"), new SubjectId("cargo:frontier-v3-bioform-test"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED, Optional.empty(),
                List.of(new SceneMember(bioform, SceneLease.deterministicEntityId(state.bootstrap().worldId(), bioform))));

        state = demandFixtureBodies(state, lease.members());
        bootstrapFirstAdmissions(level, state);
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease, localFixtureBodies(lease, origin)), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a canonical hive participant must materialize as its graybox Zombie, never as a Villager");
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        helper.assertTrue(entity instanceof Zombie, "the scene body must retain the canonical bioform kind");
        helper.assertTrue(((Zombie) entity).hasEffect(MobEffects.FIRE_RESISTANCE),
                "a graybox hive bioform must survive daylight without becoming an unaccounted vanilla death");
        helper.assertTrue(((Zombie) entity).getItemBySlot(EquipmentSlot.HEAD).is(Items.LIME_WOOL)
                        && !((Zombie) entity).getItemBySlot(EquipmentSlot.HEAD).isDamageableItem(),
                "a WORKER bioform must carry its unbreakable lime role marker to prevent vanilla daylight ignition");
        entity.discard();
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedEngagementSceneKeepsBothSidesAsExactBodies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(2, 8, 2)); prepareFloor(level, origin); prepareFloor(level, origin.east(2));
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:engagement-scene-bodies"), 91L));
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-engagement-bodies"); SubjectId resident = new SubjectId("resident:1-1"), bioform = new SubjectId("bioform:west-0");
        SceneLease lease = fixtureLease(id, state.bootstrap().worldId(), new SubjectId("operation:frontier-v3-engagement-bodies"), new SubjectId("cargo:frontier-v3-engagement-bodies"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED,
                Optional.of(new SubjectId("engagement:frontier-v3-game-test")), List.of(new SceneMember(resident, SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident)),
                        new SceneMember(bioform, SceneLease.deterministicEntityId(state.bootstrap().worldId(), bioform))));
        state = demandFixtureBodies(state, lease.members());
        bootstrapFirstAdmissions(level, state);
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease, localFixtureBodies(lease, origin)), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a loaded engagement scene must materialize both exact human and hive members without a second actor set");
        helper.assertTrue(level.getEntity(lease.members().getFirst().entityId()) instanceof Villager, "engagement resident remains one Villager");
        helper.assertTrue(level.getEntity(lease.members().getLast().entityId()) instanceof Zombie, "engagement bioform remains one Zombie");
        lease.members().forEach(member -> level.getEntity(member.entityId()).discard()); helper.succeed();
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
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, resident,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an exact resident receives one owned Villager body");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, bioform,
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

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void sceneScopeReusesTheSameIndependentlyOwnedBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(2, 8, 2)); prepareFloor(level, origin);
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scene-common-body-test"), 91L));
        SubjectId resident = new SubjectId("resident:1-1");
        state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.demand(state, resident);
        bootstrapFirstAdmissions(level, state);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, resident,
                        new BodyPosition(origin.getX(), origin.getY(), origin.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the common producer inserts the exact physical incarnation before any scene adopts it");
        Entity original = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, resident));
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(original).orElseThrow();
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-common-body");
        SceneLease lease = fixtureLease(id, state.bootstrap().worldId(), new SubjectId("operation:frontier-v3-common-body"),
                new SubjectId("cargo:frontier-v3-common-body"), new BlockPosition(origin.getX(), origin.getY(), origin.getZ()),
                SimInstant.ZERO, 2L, SceneLeaseStatus.PREPARED, Optional.empty(),
                List.of(new SceneMember(resident, SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident))));
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease),
                FrontierV3SceneExecutor.BodyMaterialization.COMPLETE, "a scene references the already owned body without a transfer");
        Entity referenced = level.getEntity(declaration.entityId());
        helper.assertTrue(referenced == original, "the scene keeps the exact indexed Java object and UUID");
        helper.assertValueEqual(FrontierV3ActorCarrierComposition.declaredBy(referenced).orElseThrow(), declaration,
                "activity scope cannot restamp the physical owner, epoch or identity");
        helper.assertValueEqual(declaration.owner(), FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                "only the activity-independent body lifecycle owns the entity");
        helper.assertTrue(((Villager) referenced).isNoAi(), "the retained body remains under common controlled execution");
        referenced.discard(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-restart-reclaim", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
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
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident, lease.handoffBody()),
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
        FrontierV3AmbientActorExecutor.tick(level, runtime);
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                "the next compatible projection must permit exactly the ordinary reclaim handoff");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(origin.getX(), origin.getY(), origin.getZ())), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "reclaim must retain the existing body instead of creating another one");
        helper.assertTrue(level.getEntity(restored.getUUID()) == restored, "the observed restored body remains the sole UUID owner");
        FrontierV3ServerLifecycle.releaseRuntime(runtime);
        restored.discard();
        helper.succeed();
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

    @GameTest(batch = "pm-frontier-v3-scene-restart-reclaim", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void completeOwnedSceneReclaimsAfterRestartAndMissingBodyStaysUnknown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3SceneBodyGameTestFixture.start(helper, new WorldId("frontier:scene-reclaim-game-test"), 91L, new EphemeralStore());
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst(); SceneLeaseId leaseId = new SceneLeaseId("lease:scene-reclaim-game-test");
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("the scene reclaim fixture runtime must remain active"));
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
        FrontierV3CommandSubmission.submit(runtime, "scene-reclaim-lease-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        FrontierV3SceneBodyGameTestFixture.materializeAndObserve(helper, runtime, lease);
        FrontierV3CommandSubmission.submit(runtime, "scene-reclaim-lease-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        BlockPos cargo = cargoPosition(origin, lease); prepareFloor(level, cargo);
        MinecartChest carrier = addOwnedCarrier(helper, level, state(runtime), lease, cargo);
        helper.runAfterDelay(1L, () -> {
        helper.assertValueEqual(FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                "restart recovery must first retain the active scene as UNKNOWN");
        helper.assertTrue(FrontierV3SceneExecutor.completeLoadedSceneSet(level, state(runtime), lease),
                "no-demand recovery may recognize the same complete loaded body/cart set");
        helper.assertTrue(FrontierV3SceneExecutor.reclaimObservedBodies(level, runtime, state(runtime), lease),
                "the loaded complete exact scene body set must be eligible for reclaim after demand admission");
        helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).status(), SceneLeaseStatus.HOT,
                "only the complete exact owned scene body set may reclaim HOT");
        for (SceneMember member : lease.members()) helper.assertTrue(level.getEntity(member.entityId()) != null,
                "scene reclaim must retain each original UUID instead of creating a substitute body");

        helper.assertValueEqual(FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                "the reclaimed scene must return to UNKNOWN exactly once on a later restart");
        Entity missing = level.getEntity(lease.members().getLast().entityId()); helper.assertTrue(missing != null, "fixture must retain a body to remove"); missing.discard();
        helper.assertFalse(FrontierV3SceneExecutor.completeLoadedSceneSet(level, state(runtime), lease),
                "a partial loaded set cannot bypass whole-scene recovery");
        helper.assertTrue(!FrontierV3SceneExecutor.reclaimObservedBodies(level, runtime, state(runtime), lease),
                "a partial observed body set must be ineligible for scene reclaim");
        helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).status(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART,
                "a missing exact scene body must remain visible UNKNOWN and never be recreated during reclaim");
        lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
        if (!carrier.isRemoved()) carrier.discard();
        runtime.shutdown(); helper.succeed();
        });
    }


    @GameTest(batch = "pm-frontier-v3-scene-explosion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotBomberMaterializesOneOwnedTntAndDoesNotReplayItsDisappearance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0));
        // GameTest cells run concurrently in one Minecraft level. Every physical UUID derives
        // from canonical identity, so this fixture must derive its world/lease identity from
        // its own stable cell instead of competing with another TNT scene for the same body.
        // Keep physical bodies in that same cell: the former +48 offset crossed into a
        // neighbouring concurrent TNT fixture and let its real blast remove this fixture's
        // bomber before the durable-effect assertion ran.
        String fixture = "scene-explosion-game-test-" + origin.getX() + "-" + origin.getZ();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3SceneBodyGameTestFixture.start(helper, new WorldId("frontier:" + fixture), 91L, new EphemeralStore());
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst(); SceneLeaseId leaseId = new SceneLeaseId("lease:" + fixture);
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("the explosion fixture runtime must remain active"));
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
        FrontierV3CommandSubmission.submit(runtime, "scene-explosion-lease-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        FrontierV3SceneBodyGameTestFixture.materializeAndObserve(helper, runtime, lease);
        FrontierV3CommandSubmission.submit(runtime, "scene-explosion-lease-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        SubjectId bomber = lease.members().stream().map(SceneMember::actorId).filter(actor -> state(runtime).bootstrap().hive().bioforms().stream()
                .anyMatch(bioform -> bioform.id().equals(actor) && bioform.isExplosiveAssaulter())).findFirst().orElseThrow();
        // Entity indexing can trail the first GameTest callback when the core suite starts
        // many cells together.  Wait for the same two ordinary ticks as the real-TNT
        // fixture below, so this assertion observes the owned body rather than that
        // incidental admission race.
        helper.runAfterDelay(2L, () -> {
            Entity bomberBody = level.getEntity(lease.members().stream().filter(member -> member.actorId().equals(bomber)).findFirst().orElseThrow().entityId());
            helper.assertTrue(bomberBody != null, "the exact HOT bomber body must be materialized");
            bomberBody.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            lease.members().stream().filter(member -> member.actorId().value().startsWith("resident:")).findFirst().map(SceneMember::entityId).map(level::getEntity)
                    .ifPresent(body -> body.setPos(origin.getX() + 1.25D, origin.getY(), origin.getZ() + 0.5D));
            helper.assertTrue(FrontierV3SceneExecutor.executeExplosion(level, runtime, state(runtime), lease,
                    io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.HIVE_MOBILIZATION), "a nearby owned HOT bomber must prepare a blast instead of inventing a non-durable hit");
            PhysicalIntent intent = state(runtime).physicalIntents().values().stream().filter(value -> value.kind() == PhysicalIntentKind.EXPLOSION).findFirst()
                    .orElseThrow(() -> new IllegalStateException("the hot bomber did not retain its explosion intent"));
            helper.assertValueEqual(intent.status(), PhysicalIntentStatus.PREPARED, "the blast must be durable before Minecraft receives it");
            helper.assertValueEqual(intent.causeSubjectId(), bomber, "the receipt must retain the exact canonical bomber identity");
            helper.assertValueEqual(FrontierV3SceneExecutor.explosionCause(level, state(runtime), intent).orElseThrow(), bomberBody,
                    "only the matching tagged HOT zombie may become the Minecraft explosion source");
            FrontierV3ExplosionExecutor.tick(level, runtime);
            PhysicalIntent running = state(runtime).physicalIntents().get(intent.id());
            helper.assertValueEqual(running.status(), PhysicalIntentStatus.RUNNING, "the durable intent must run before its physical TNT body enters the world");
            Entity physicalBomb = level.getEntity(FrontierV3BomberBomb.entityId(intent.id()));
            helper.assertTrue(physicalBomb instanceof net.minecraft.world.entity.item.PrimedTnt
                            && FrontierV3BomberBomb.isCurrent(physicalBomb, running),
                    "the exact intent must materialize one tagged ordinary PrimedTnt body, never an immediate synthetic blast");
            helper.assertValueEqual(FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(runtime, level), 0,
                    "restart recovery must retain the exact loaded owned TNT for postcondition inspection instead of replaying or quarantining it");
            physicalBomb.discard();
            helper.assertValueEqual(FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(runtime, level), 1,
                    "a missing bomb in an already loaded chunk must become visible unknown at restart, never be recreated");
            helper.assertValueEqual(state(runtime).physicalIntents().get(intent.id()).status(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                    "a missing loaded-world bomb becomes visible unknown evidence and is never spawned or exploded again");
            lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
            runtime.shutdown(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-explosion-live", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void hotBomberBlastUsesRealTntEventAndRetainsPostImpactInspection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(2, 8, 2));
        // The dedicated server runs fixture cells in parallel.  Scene-body UUIDs deliberately
        // derive from WorldId + actor, so a fixed fixture WorldId would collide with another
        // concurrent test using the same bootstrap actors.  The physical cell is stable for
        // this run and makes the fixture identity isolated without changing production IDs.
        String fixture = "scene-real-explosion-game-test-" + origin.getX() + "-" + origin.getZ();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3SceneBodyGameTestFixture.start(helper, new WorldId("frontier:" + fixture), 91L, new EphemeralStore());
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst(); SceneLeaseId leaseId = new SceneLeaseId("lease:" + fixture);
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("the real-blast fixture runtime must remain active"));
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
        FrontierV3CommandSubmission.submit(runtime, "scene-real-explosion-lease-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        FrontierV3SceneBodyGameTestFixture.materializeAndObserve(helper, runtime, lease);
        FrontierV3CommandSubmission.submit(runtime, "scene-real-explosion-lease-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        // The regular graybox is flat; the bare GameTest template is not.  Give the ballistic
        // vanilla TNT the same supported ground instead of allowing it to fall out of the fixture.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) prepareFloor(level, origin.offset(x, 0, z));
        BlockPos blastTarget = origin.east(); prepareFloor(level, blastTarget); level.setBlock(blastTarget, Blocks.STONE.defaultBlockState(), 3);
        AtomicBoolean active = new AtomicBoolean(true), captured = new AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<String> detonationSource = new java.util.concurrent.atomic.AtomicReference<>("no detonation event");
        Consumer<ExplosionEvent.Detonate> listener = event -> {
            if (active.get() && event.getLevel() == level && FrontierV3ExplosionExecutionScope.currentIntent().isPresent()) {
                Entity direct = event.getExplosion().getDirectSourceEntity();
                detonationSource.set(direct == null ? "null" : direct.getType().toString() + ":" + direct.getUUID() + ":" + direct.getPersistentData().getString(FrontierV3BomberBomb.INTENT_KEY));
                captured.set(FrontierV3ServerLifecycle.observeExplosion(level, runtime, event.getExplosion(), event.getAffectedBlocks(), event.getAffectedEntities()));
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ExplosionEvent.Detonate.class, listener);
        // In the full parallel suite entity indexing can lag one GameTest callback.  This
        // fixture uses a deterministic UUID and needs the real indexed body, so wait two
        // ordinary server ticks instead of asserting an incidental one-tick race.
        helper.runAfterDelay(2L, () -> {
            try {
                SubjectId bomber = lease.members().stream().map(SceneMember::actorId).filter(actor -> state(runtime).bootstrap().hive().bioforms().stream()
                        .anyMatch(bioform -> bioform.id().equals(actor) && bioform.isExplosiveAssaulter())).findFirst().orElseThrow();
                Entity bomberBody = level.getEntity(lease.members().stream().filter(member -> member.actorId().equals(bomber)).findFirst().orElseThrow().entityId());
                helper.assertTrue(bomberBody != null, "the real TNT fixture must retain its exact bomber body"); bomberBody.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
                lease.members().stream().filter(member -> member.actorId().value().startsWith("resident:")).findFirst().map(SceneMember::entityId).map(level::getEntity)
                        .ifPresent(body -> body.setPos(origin.getX() + 1.25D, origin.getY(), origin.getZ() + 0.5D));
                helper.assertTrue(FrontierV3SceneExecutor.executeExplosion(level, runtime, state(runtime), lease,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.HIVE_MOBILIZATION), "the hot scene must durably prepare its real blast");
                PhysicalIntent intent = state(runtime).physicalIntents().values().stream().filter(value -> value.kind() == PhysicalIntentKind.EXPLOSION).findFirst().orElseThrow();
                FrontierV3ExplosionExecutor.tick(level, runtime);
                Entity bomb = level.getEntity(FrontierV3BomberBomb.entityId(intent.id()));
                helper.assertTrue(bomb instanceof net.minecraft.world.entity.item.PrimedTnt && FrontierV3BomberBomb.isCurrent(bomb, state(runtime).physicalIntents().get(intent.id())),
                        "the hot bomber must release a visible normal TNT entity before it detonates");
                helper.assertFalse(captured.get(), "TNT admission itself is not an invented detonation observation");
                helper.assertValueEqual(state(runtime).physicalIntents().get(intent.id()).status(), PhysicalIntentStatus.RUNNING, "the blast stays running until real-world reconciliation completes");
                ((net.minecraft.world.entity.item.PrimedTnt) bomb).setFuse(1);
                ((net.minecraft.world.entity.item.PrimedTnt) bomb).tick();
                FrontierV3ManagedExplosionLedger retained = FrontierV3ManagedExplosionLedger.get(level);
                helper.assertTrue(captured.get(), "the normal TNT tick must enter the v3 observation bridge; source=" + detonationSource.get());
                helper.assertTrue(retained.has(intent.id()), "post-impact inspection must be persisted before receipt confirmation");
                helper.runAfterDelay(2L, () -> {
                    try {
                        var pendingEntity = retained.nextEntity(intent.id(), level.getGameTime());
                        helper.assertTrue(pendingEntity.isPresent(), "a real unloaded post-impact entity remains explicit inspection work, never a guessed death");
                        helper.assertValueEqual(state(runtime).physicalIntents().get(intent.id()).status(), PhysicalIntentStatus.RUNNING,
                                "the out-of-profile test world cannot fabricate canonical confirmation");
                        helper.assertTrue(!level.getBlockState(blastTarget).equals(Blocks.STONE.defaultBlockState()), "ordinary TNT geometry must alter an unprotected nearby block");
                        lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
                        active.set(false); NeoForge.EVENT_BUS.unregister(listener); runtime.shutdown(); helper.succeed();
                    } catch (RuntimeException failure) {
                        active.set(false); NeoForge.EVENT_BUS.unregister(listener); runtime.shutdown(); throw failure;
                    }
                });
            } catch (RuntimeException failure) {
                active.set(false); NeoForge.EVENT_BUS.unregister(listener); runtime.shutdown(); throw failure;
            }
        });
    }

    private static void bootstrapFirstAdmissions(ServerLevel level, FrontierWorldState state) {
        WorldId world = state.bootstrap().worldId();
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, state,
                new RecoveryImage(world, Optional.empty(), List.of()), () -> ledger.persist(level, world));
    }

    private static SceneLease lease(FrontierWorldState state, BlockPos origin) {
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-game-test");
        WorldId world = state.bootstrap().worldId();
        List<SceneMember> members = state.humanPopulation().residents().keySet().stream().sorted().limit(2)
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))).toList();
        return fixtureLease(id, world, new SubjectId("operation:frontier-v3-game-test"), new SubjectId("cargo:frontier-v3-game-test"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED, Optional.empty(), members);
    }
    private static SceneLease fixtureLease(SceneLeaseId id, WorldId world, SubjectId operation, SubjectId cargo, BlockPosition handoff,
                                           SimInstant instant, long revision, SceneLeaseStatus status, Optional<SubjectId> engagement,
                                           List<SceneMember> members) {
        return SceneLease.atExactPositions(id, world, operation, cargo, handoff, handoff, instant, revision, status, engagement, members);
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
    private static MinecartChest addOwnedCarrier(GameTestHelper helper, ServerLevel level, FrontierWorldState state,
                                                 SceneLease lease, BlockPos position) {
        MinecartChest cart = EntityType.CHEST_MINECART.create(level);
        helper.assertTrue(cart != null, "the HOT cargo carrier fixture must be constructible");
        cart.setUUID(FrontierV3CargoCarrierExecutor.id(lease)); cart.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.LEASE_KEY, lease.id().value());
        cart.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.REVISION_KEY, lease.revision());
        cart.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY,
                FrontierV3CargoCarrierAuthority.currentEpoch(state.fencedRecovery(), lease).orElseThrow());
        cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.CARGO_KEY, FrontierSceneBehaviors.logistics(lease).cargoId().value());
        CustodyAccount account = state.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody() instanceof ResourceCustody.Cargo cargo
                        && cargo.cargoId().equals(FrontierSceneBehaviors.logistics(lease).cargoId())).findFirst().orElseThrow();
        String kind = account.lotQuantities().keySet().stream().map(state.inventory().fungibleResources().lots()::get)
                .map(lot -> lot.itemKind()).distinct().reduce((left, right) -> "").orElse("");
        int quantity = account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(kind)).orElse(null);
        helper.assertTrue(item != null && quantity > 0 && quantity <= 64, "the cargo fixture requires one bounded canonical fungible stack");
        cart.setItem(0, new ItemStack(item, quantity));
        helper.assertTrue(level.addFreshEntity(cart), "the canonical HOT cargo carrier fixture must enter the loaded world");
        return cart;
    }
    private static PhysicalIntent pendingStrike(FrontierWorldState state, SceneLease lease) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.causeSubjectId().equals(FrontierSceneBehaviors.logistics(lease).operationId()) && intent.status() == PhysicalIntentStatus.PREPARED).findFirst()
                .orElseThrow(() -> new IllegalStateException("the HOT scene did not prepare its exact next strike"));
    }
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
