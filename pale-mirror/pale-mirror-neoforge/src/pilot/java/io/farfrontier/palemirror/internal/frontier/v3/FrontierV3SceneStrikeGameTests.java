package io.farfrontier.palemirror.internal.frontier.v3;
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
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3SceneGameTests.*;

/** Physical strike receipts and restart behavior in owned settlement scenes. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SceneStrikeGameTests {
    private FrontierV3SceneStrikeGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-strikes", templateNamespace = "pale_mirror_visuals", template = "temperate/residence_1", timeoutTicks = 40)
    public static void localSettlementAssaultStrikeRetainsAnIndependentExactReceipt(GameTestHelper helper) {
        localSettlementAssaultStrike(helper, StrikeScenario.ORDINARY);
    }

    @GameTest(batch = "pm-frontier-v3-scene-strikes", templateNamespace = "pale_mirror_visuals", template = "temperate/residence_1", timeoutTicks = 40)
    public static void conflictedSettlementStrikeCannotApplyDamageAgain(GameTestHelper helper) {
        localSettlementAssaultStrike(helper, StrikeScenario.CONFLICT);
    }

    @GameTest(batch = "pm-frontier-v3-scene-strikes", templateNamespace = "pale_mirror_visuals", template = "temperate/residence_1", timeoutTicks = 80)
    public static void failedStrikeConfirmationUsesTargetWitnessWithoutAnotherHit(GameTestHelper helper) {
        localSettlementAssaultStrike(helper, StrikeScenario.RECOVERY);
    }

    @GameTest(batch = "pm-frontier-v3-scene-strikes", templateNamespace = "pale_mirror_visuals", template = "temperate/residence_1", timeoutTicks = 80)
    public static void missingStrikeWitnessEndsInBoundedLocalDrainWithoutInventingHit(GameTestHelper helper) {
        localSettlementAssaultStrike(helper, StrikeScenario.MISSING);
    }

    @GameTest(batch = "pm-frontier-v3-scene-strikes", templateNamespace = "pale_mirror_visuals", template = "temperate/residence_1", timeoutTicks = 80)
    public static void malformedRunningWitnessCannotTrapDrainingScene(GameTestHelper helper) {
        localSettlementAssaultStrike(helper, StrikeScenario.MALFORMED);
    }

    @GameTest(batch = "pm-frontier-v3-scene-strikes", templateNamespace = "pale_mirror_visuals", template = "temperate/residence_1", timeoutTicks = 80)
    public static void removedDeadTargetDoesNotRetainAnUnresolvedHitForever(GameTestHelper helper) {
        localSettlementAssaultStrike(helper, StrikeScenario.REMOVED_DEAD);
    }

    @GameTest(batch = "pm-frontier-v3-scene-strikes", templateNamespace = "pale_mirror_visuals", template = "temperate/residence_1", timeoutTicks = 80)
    public static void missingLivingTargetDelegatesToSceneRecoveryWithoutInventingAbsence(GameTestHelper helper) {
        localSettlementAssaultStrike(helper, StrikeScenario.MISSING_LIVING);
    }

    private enum StrikeScenario { ORDINARY, CONFLICT, RECOVERY, MISSING, MALFORMED, REMOVED_DEAD, MISSING_LIVING }

    @GameTest(batch = "pm-frontier-v3-scene-strikes", templateNamespace = "pale_mirror_visuals", template = "temperate/residence_1", timeoutTicks = 100)
    public static void recordedMissingMemberIsReinspectedWithoutDuplicateCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var runtime = FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.settlementAssaultConfiguration(
                new WorldId("frontier:reinspect-" + origin.getX() + "-" + origin.getZ()), 91L), new EphemeralStore(), 20_000);
        var initial = state(runtime);
        var candidate = initial.coldSettlementAssaultSceneCandidates().getFirst();
        var canonical = settlementAssaultLease(runtime, initial, candidate, new SceneLeaseId("lease:reinspect"));
        FrontierV3CommandSubmission.submit(runtime, "reinspect-prepare", canonical.id().value(),
                new io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneLeasePrepared(canonical));
        FrontierV3CommandSubmission.submit(runtime, "reinspect-hot", canonical.id().value(), new SceneLeaseTransition(canonical.id(), SceneLeaseStatus.HOT));
        var positions = new java.util.LinkedHashMap<SubjectId, BodyPosition>();
        for (int index = 0; index < canonical.members().size(); index++) {
            BlockPos cell = origin.offset(index % 6, 0, index / 6);
            prepareFloorWithinTemplate(helper, level, helper.getBounds(), cell);
            positions.put(canonical.members().get(index).actorId(), new BodyPosition(cell.getX(), cell.getY(), cell.getZ()));
        }
        // Component-only coordinate projection; canonical commands still bind the original lease.
        var local = canonical.withMemberPositions(positions).withHandoffPosition(new BlockPosition(origin.getX(), origin.getY(), origin.getZ()));
        for (var member : local.members()) {
            var body = positions.get(member.actorId());
            addOwnedBody(helper, level, state(runtime), local, member, new BlockPos(body.x(), body.y(), body.z()));
        }
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.getX(), origin.getY(), origin.getZ());
        helper.runAfterDelay(2L, () -> {
            FrontierV3CommandSubmission.submit(runtime, "reinspect-unknown", local.id().value(), new SceneLeaseTransition(local.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
            var member = local.members().getFirst();
            Entity body = level.getEntity(member.entityId());
            var declaration = body.getPersistentData().copy();
            body.getPersistentData().remove(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY);
            FrontierV3SceneExecutor.reclaim(level, runtime, state(runtime), local.withStatus(SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
            helper.runAfterDelay(22L, () -> {
                FrontierV3SceneExecutor.reclaim(level, runtime, state(runtime), local.withStatus(SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
                var unresolved = state(runtime).sceneLeases().get(local.id());
                helper.assertTrue(unresolved.recoveryEvidence().isPresent(), "unrecognized exact UUID must retain missing-member evidence");
                var inspected = local.withStatus(SceneLeaseStatus.UNKNOWN_AFTER_RESTART).withRecoveryEvidence(unresolved.recoveryEvidence().orElseThrow());
                long revision = runtime.canonicalState().orElseThrow().revision().value();
                FrontierV3SceneExecutor.reclaim(level, runtime, state(runtime), inspected);
                helper.runAfterDelay(22L, () -> {
                    FrontierV3SceneExecutor.reclaim(level, runtime, state(runtime), inspected);
                    helper.assertValueEqual(runtime.canonicalState().orElseThrow().revision().value(), revision,
                            "repeated missing-member inspection must not append duplicate diagnostic commands");
                    body.getPersistentData().merge(declaration);
                    FrontierV3SceneExecutor.reclaim(level, runtime, state(runtime), inspected);
                    helper.runAfterDelay(22L, () -> {
                        FrontierV3SceneExecutor.reclaim(level, runtime, state(runtime), inspected);
                        helper.assertValueEqual(state(runtime).sceneLeases().get(local.id()).status(), SceneLeaseStatus.HOT,
                                "retained absence evidence must not latch out a later exact observed body set");
                        helper.assertTrue(level.getEntity(member.entityId()) == body, "reclaim must keep the original entity object");
                        helper.assertTrue(state(runtime).sceneLeases().get(local.id()).recoveryEvidence().isEmpty(), "successful reclaim clears old absence evidence");
                        local.members().forEach(value -> { var entity = level.getEntity(value.entityId()); if (entity != null) entity.discard(); });
                        player.discard(); FrontierV3SceneExecutor.forget(runtime); runtime.shutdown(); helper.succeed();
                    });
                });
            });
        });
    }

    private static void localSettlementAssaultStrike(GameTestHelper helper, StrikeScenario scenario) {
        boolean conflictBeforeDamage = scenario == StrikeScenario.CONFLICT;
        boolean recoverAfterDamage = scenario == StrikeScenario.RECOVERY;
        boolean missingWitness = scenario == StrikeScenario.MISSING;
        boolean malformedWitness = scenario == StrikeScenario.MALFORMED;
        // This component proof has no claim about natural player demand. It owns the decoded
        // pale_mirror_visuals:temperate/residence_1 [10, 7, 9] template and proves every fixture
        // write and observed body remains inside the actual GameTest envelope.
        ServerLevel level = helper.getLevel(); AABB templateBounds = helper.getBounds();
        helper.assertTrue(templateBounds.getXsize() == 10.0D && templateBounds.getYsize() == 7.0D
                        && templateBounds.getZsize() == 9.0D,
                "the authored settlement-assault component template must retain its [10, 7, 9] envelope");
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        String fixture = "settlement-assault-strike-" + origin.getX() + "-" + origin.getZ();
        var replayStore = new StrikeReplayStore();
        var configuration = FrontierV3FixtureCatalog.settlementAssaultConfiguration(new WorldId("frontier:" + fixture), 91L);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(configuration, recoverAfterDamage ? replayStore : new EphemeralStore(), 20_000);
        FrontierWorldState initial = state(runtime); SettlementAssaultSceneCandidate candidate = initial.coldSettlementAssaultSceneCandidates().getFirst();
        SceneLease canonical = settlementAssaultLease(runtime, initial, candidate, new SceneLeaseId("lease:" + fixture));
        FrontierV3CommandSubmission.submit(runtime, "local-assault-strike-prepare", canonical.id().value(), new io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneLeasePrepared(canonical));
        FrontierV3CommandSubmission.submit(runtime, "local-assault-strike-hot", canonical.id().value(), new SceneLeaseTransition(canonical.id(), SceneLeaseStatus.HOT));
        SceneLease local = FrontierV3GameTestSceneLeases.projectedIntoFixture(canonical, new BodyPosition(origin.getX(), origin.getY() + 1, origin.getZ()));
        int fixtureColumns = 6, fixtureRows = 6;
        helper.assertTrue(local.members().size() <= fixtureColumns * fixtureRows,
                "the authored template reserves a bounded 6 by 6 local body grid");
        for (int index = 0; index < local.members().size(); index++) {
            BlockPos position = origin.offset(index % fixtureColumns, 0, index / fixtureColumns); prepareFloorWithinTemplate(helper, level, templateBounds, position);
            Entity body = addOwnedBody(helper, level, state(runtime), local, local.members().get(index), position);
            requireEntityWithinTemplate(helper, templateBounds, body, "every owned fixture body must enter inside the authored envelope");
        }
        helper.runAfterDelay(2L, () -> {
            try {
                helper.assertValueEqual(local.members().stream().filter(member -> FrontierV3SceneExecutor.owned(level.getEntity(member.entityId()), state(runtime), local, member)).count(),
                        (long) local.members().size(), "the template-local component fixture must retain every exact production body before the strike");
                SettlementAssault assault = state(runtime).strategicPlans().settlementAssaults().get(candidate.assaultId());
                helper.assertTrue(assault != null && local.members().stream().map(SceneMember::actorId).anyMatch(assault.combatantAttackerIds()::contains)
                                && local.members().stream().map(SceneMember::actorId).anyMatch(assault.defenderIds()::contains),
                        "the local fixture must retain the exact live attacker and target populations selected by production");
                rejectForeignCurrentCauseThroughProductionConsumer(helper, runtime, canonical, assault, origin);
                for (int index = 0; index < local.members().size(); index++) {
                    Entity body = level.getEntity(local.members().get(index).entityId());
                    body.setPos(origin.getX() + 0.25D + (index % 3) * 0.4D, origin.getY(), origin.getZ() + 0.25D + (index / 3) * 0.4D);
                    requireEntityWithinTemplate(helper, templateBounds, body, "every strike-position write must remain inside the authored envelope");
                }
                FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), local,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
                FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), local,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
                PhysicalIntent prepared = onlyStrike(state(runtime));
                Entity target = level.getEntity(local.members().stream().filter(member ->
                        member.actorId().equals(prepared.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.TARGET))).findFirst().orElseThrow().entityId());
                if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) throw new IllegalStateException("local exact target did not materialize");
                requireEntityWithinTemplate(helper, templateBounds, living, "the health-observed strike target must remain inside the authored envelope");
                FixedScalar before = fixed(living.getHealth());
                if (scenario == StrikeScenario.MISSING_LIVING) {
                    FrontierV3CommandSubmission.submit(runtime, "missing-living-unknown", prepared.id().value(),
                            new io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition(prepared.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
                    var actorsBefore = state(runtime).actorLocations();
                    living.discard(); // Missing physical input, not an observed death.
                    FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), local.withStatus(SceneLeaseStatus.HOT),
                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
                    helper.assertValueEqual(state(runtime).sceneLeases().get(local.id()).status(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART,
                            "missing live target delegates to ordinary scene recovery");
                    helper.assertValueEqual(onlyStrike(state(runtime)).status(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                            "scene handoff cannot settle the unobserved effect");
                    helper.assertValueEqual(state(runtime).actorLocations(), actorsBefore, "absence cannot invent death or replacement");
                    helper.assertTrue(state(runtime).physicalObservations().isEmpty(), "absence cannot invent hit evidence");
                    helper.assertValueEqual(state(runtime).fencedRecovery().current().get(
                            io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhysicalIntentSupport.bindingId(prepared)).recoveryAttempts(), 1,
                            "lack of a loaded target is not a failed physical inspection");
                    helper.succeed(); return;
                }
                if (scenario == StrikeScenario.REMOVED_DEAD) {
                    living.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                    helper.assertTrue(!living.isAlive(), "physical input must actually kill the exact target");
                    var deadActor = prepared.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.TARGET);
                    // This component template is translated outside canonical bounds. Feed
                    // the actual observed death at its canonical fixture station; this does
                    // not claim the production dimension's event-to-coordinate bridge.
                    FrontierV3CommandSubmission.submit(runtime, "fixture-observed-death", deadActor.value(),
                            new io.farfrontier.palemirror.frontier.v3.model.ActorDied(local.id(), deadActor,
                                    canonical.memberPosition(deadActor), "fixture-observed-physical-death"));
                    helper.assertValueEqual(state(runtime).sceneLeases().get(local.id()).status(), SceneLeaseStatus.DRAINING,
                            "registered death must itself drain the scene atomically");
                    living.discard();
                    var deadActors = state(runtime).actorLocations();
                    for (int attempt = 0; attempt <= io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryBinding.MAX_RECOVERY_ATTEMPTS; attempt++) {
                        FrontierV3SceneExecutor.release(level, runtime, state(runtime).sceneLeases().get(local.id()));
                    }
                    helper.assertValueEqual(onlyStrike(state(runtime)).status(), PhysicalIntentStatus.CONFLICTED,
                            "committed death permits bounded abandonment after physical target removal");
                    helper.assertTrue(state(runtime).physicalObservations().isEmpty(), "unrelated death cannot fabricate hit attribution");
                    helper.assertValueEqual(state(runtime).actorLocations(), deadActors, "inspection cannot resurrect or change actors");
                    helper.assertValueEqual(runtime.status().kind(), FrontierV3RuntimeStatus.Kind.ACTIVE, "death recovery stays local");
                    helper.succeed(); return;
                }
                if (malformedWitness) {
                    living.getPersistentData().putString(FrontierV3SceneStrikeReceipt.KEY, "malformed-provider-input");
                    FrontierV3CommandSubmission.submit(runtime, "malformed-strike-drain", local.id().value(),
                            new SceneLeaseTransition(local.id(), SceneLeaseStatus.DRAINING));
                    for (int attempt = 0; attempt <= io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryBinding.MAX_RECOVERY_ATTEMPTS; attempt++) {
                        FrontierV3SceneExecutor.release(level, runtime, state(runtime).sceneLeases().get(local.id()));
                    }
                    helper.assertValueEqual(onlyStrike(state(runtime)).status(), PhysicalIntentStatus.CONFLICTED,
                            "malformed RUNNING witness must enter bounded inspection through ordinary drain");
                    helper.assertValueEqual(fixed(living.getHealth()), before, "inspection cannot replay damage");
                    helper.assertTrue(state(runtime).physicalObservations().isEmpty(), "invalid witness cannot invent hit evidence");
                    helper.assertValueEqual(runtime.status().kind(), FrontierV3RuntimeStatus.Kind.ACTIVE, "local conflict must not quarantine world");
                    helper.succeed(); return;
                }
                if (missingWitness) {
                    FrontierV3CommandSubmission.submit(runtime, "strike-missing-unknown", prepared.id().value(),
                            new io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition(prepared.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
                    for (int attempt = 0; attempt <= io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryBinding.MAX_RECOVERY_ATTEMPTS; attempt++) {
                        FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), local.withStatus(SceneLeaseStatus.HOT),
                                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
                    }
                    helper.assertValueEqual(onlyStrike(state(runtime)).status(), PhysicalIntentStatus.CONFLICTED,
                            "bounded failed inspection must retire unknown strike locally");
                    helper.assertValueEqual(state(runtime).sceneLeases().get(local.id()).status(), SceneLeaseStatus.DRAINING,
                            "conflicted strike must permit scene drain, not permanent HOT waiting");
                    helper.assertValueEqual(fixed(living.getHealth()), before, "no witness cannot authorize replayed damage");
                    helper.assertTrue(state(runtime).physicalObservations().isEmpty(), "abandonment must not invent an observed hit");
                    helper.assertValueEqual(runtime.status().kind(), FrontierV3RuntimeStatus.Kind.ACTIVE, "local ambiguity cannot quarantine world");
                    helper.succeed(); return;
                }
                if (recoverAfterDamage) {
                    replayStore.failNextAppend = true;
                    boolean interrupted = false;
                    try {
                        FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), local,
                                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
                    } catch (IllegalStateException expected) { interrupted = true; }
                    helper.assertTrue(interrupted && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.QUARANTINED,
                            "injected confirmation storage failure must stop canonical mutation after actual hurt");
                    var afterHit = fixed(living.getHealth());
                    helper.assertTrue(afterHit.compareTo(before) < 0, "production must actually damage the target before failed confirmation");
                    var savedTarget = new net.minecraft.nbt.CompoundTag();
                    helper.assertTrue(living.save(savedTarget), "serialize actual health and producer-written witness together");
                    living.load(savedTarget); // Provider serialization boundary, not an OS/process restart claim.
                    var recovered = FrontierV3ServerRuntime.start(configuration, replayStore, 20_000);
                    try {
                        helper.assertValueEqual(onlyStrike(state(recovered)).status(), PhysicalIntentStatus.RUNNING,
                                "failed confirmation must be absent from recovered committed WAL");
                        FrontierV3CommandSubmission.submit(recovered, "strike-recovery-unknown", prepared.id().value(),
                                new io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition(prepared.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
                        living.setHealth(living.getHealth() - 1.0F); // Separate divergent physical input.
                        var changed = fixed(living.getHealth());
                        FrontierV3SceneExecutor.executeStrike(level, recovered, state(recovered), local,
                                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
                        helper.assertValueEqual(onlyStrike(state(recovered)).status(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                                "external health drift cannot become exact old hit evidence");
                        helper.assertValueEqual(fixed(living.getHealth()), changed, "unknown inspection must not hurt or restore health");
                        FrontierV3SceneExecutor.executeStrike(level, recovered, state(recovered), local,
                                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
                        helper.assertValueEqual(state(recovered).fencedRecovery().current().get(
                                        io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhysicalIntentSupport.bindingId(prepared)).nextAction(),
                                io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryDisposition.ABANDON,
                                "inspection limit proposes abandonment but has not committed it");
                        living.load(savedTarget); // Restore separate exact serialized input, not production repair.
                        FrontierV3CommandSubmission.submit(recovered, "strike-recovery-drain", local.id().value(),
                                new SceneLeaseTransition(local.id(), SceneLeaseStatus.DRAINING));
                        FrontierV3SceneExecutor.release(level, recovered, state(recovered).sceneLeases().get(local.id()));
                        helper.assertValueEqual(onlyStrike(state(recovered)).status(), PhysicalIntentStatus.CONFIRMED,
                                "ordinary draining release must inspect and confirm the retained hit before releasing bodies");
                        helper.assertValueEqual(state(recovered).sceneLeases().get(local.id()).status(), SceneLeaseStatus.DRAINING,
                                "hit inspection is its own bounded turn before any scene release");
                        helper.assertValueEqual(fixed(living.getHealth()), afterHit, "confirmation must not apply a second hit");
                        helper.assertFalse(living.getPersistentData().contains(FrontierV3SceneStrikeReceipt.KEY),
                                "accepted confirmation retires its exact witness");
                    } finally { recovered.shutdown(); }
                    helper.succeed(); return;
                }
                if (conflictBeforeDamage) {
                    FrontierV3CommandSubmission.submit(runtime, "local-strike-conflict", prepared.id().value(),
                            new io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition(prepared.id(), PhysicalIntentStatus.CONFLICTED, Optional.empty()));
                    for (int attempt = 0; attempt < 2; attempt++) {
                        FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), local,
                                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
                        helper.assertValueEqual(fixed(living.getHealth()), before, "terminally conflicted strike cannot damage the real target");
                        helper.assertValueEqual(onlyStrike(state(runtime)).status(), PhysicalIntentStatus.CONFLICTED,
                                "conflict remains terminal without replay or invented confirmation");
                    }
                    helper.succeed(); return;
                }
                FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), local,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
                PhysicalIntent confirmed = onlyStrike(state(runtime));
                SceneStrikeObservation receipt = (SceneStrikeObservation) state(runtime).physicalObservations().get(confirmed.postconditionObservationId().orElseThrow());
                FixedScalar after = fixed(living.getHealth());
                requireEntityWithinTemplate(helper, templateBounds, living, "the post-strike health observation must remain inside the authored envelope");
                FrontierV3SettlementAssaultReceiptOracle.requireObservedHealthTransition(confirmed, receipt, before, after);
                helper.assertTrue(FrontierV3SettlementAssaultReceiptBinding.belongsToLease(state(runtime), canonical, confirmed),
                        "the production receipt must bind this exact canonical world, cause, lease identity and revision");
                assertReceiptBindingControls(helper, canonical, confirmed);
                try {
                    FrontierV3SettlementAssaultReceiptOracle.requireObservedHealthTransition(confirmed,
                            new SceneStrikeObservation(receipt.id(), receipt.intentId(), receipt.attackerId(), receipt.targetId(), before,
                                    new FixedScalar(before.raw() - FixedScalar.SCALE)), before, before);
                    throw new IllegalStateException("a decreasing forged receipt without production hurt must fail its independent samples");
                } catch (IllegalArgumentException expected) { }
                helper.succeed();
            } finally {
                local.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
                runtime.shutdown();
            }
        });
    }

    /** Exercises the real physical command consumer before this fixture manufactures its expected receipt. */
    private static void rejectForeignCurrentCauseThroughProductionConsumer(GameTestHelper helper,
                                                                             FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                             SceneLease canonical, SettlementAssault assault, BlockPos origin) {
        FrontierWorldState current = state(runtime);
        long epoch = SettlementAssaultCauseIdentity.hotEpoch(assault, current.physicalIntents().values());
        boolean hiveTurn = (epoch & 1L) == 0L;
        List<SubjectId> attackers = (hiveTurn ? assault.combatantAttackerIds() : assault.defenderIds()).stream()
                .filter(actor -> current.actorLocations().get(actor).condition().status() == ActorLifeStatus.ALIVE).sorted().toList();
        List<SubjectId> targets = (hiveTurn ? assault.defenderIds() : assault.combatantAttackerIds()).stream()
                .filter(actor -> current.actorLocations().get(actor).condition().status() == ActorLifeStatus.ALIVE).sorted().toList();
        SubjectId attacker = attackers.get(Math.floorMod(epoch, attackers.size()));
        SubjectId target = targets.get(Math.floorMod(epoch, targets.size()));
        SubjectId cause = SettlementAssaultCauseIdentity.strike(assault.id(), attacker, epoch);
        SceneLease foreign = receiptLease(canonical, new SceneLeaseId(canonical.id().value() + "-foreign"), canonical.revision());
        PhysicalIntent foreignIntent = new PhysicalIntent(FrontierV3SettlementAssaultReceiptBinding.intentId(canonical.worldId(), cause, foreign.id(), foreign.revision()),
                PhysicalIntentKind.SCENE_STRIKE, PhysicalIntentStatus.PREPARED, cause, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.assaultSceneStrike(attacker, target, foreign.id(), foreign.revision()),
                new io.farfrontier.palemirror.frontier.v3.api.FixedPosition(FixedScalar.whole(origin.getX()), FixedScalar.whole(origin.getY()), FixedScalar.whole(origin.getZ())),
                0, io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition.SCENE_STRIKE_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
        int before = current.physicalIntents().size(); boolean rejected = false;
        try {
            FrontierV3CommandSubmission.submit(runtime, "foreign-assault-strike-prepare", foreign.id().value(),
                    new io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared(foreignIntent));
        } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "a plausible current-cause strike from another lease must fail at the production physical consumer");
        helper.assertValueEqual(state(runtime).physicalIntents().size(), before,
                "the rejected foreign strike must not become pending, executable, or this lease's replay fence");
    }

    /** Builds the immutable canonical assault lease; only its observed bodies are projected into this template. */
    private static SceneLease settlementAssaultLease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                     SettlementAssaultSceneCandidate candidate, SceneLeaseId id) {
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("local strike fixture runtime must remain active"));
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), actor))).toList();
        return SceneLease.forCause(id, checkpoint.worldId(), new io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause(candidate.assaultId(), candidate.settlementId()),
                candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, members,
                SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), Set.of(), Optional.empty());
    }

    /** Cheap discriminators for the framed production receipt fence; none relies on a native carrier. */
    private static void assertReceiptBindingControls(GameTestHelper helper, SceneLease canonical, PhysicalIntent confirmed) {
        var oldCollisionLeft = FrontierV3SettlementAssaultReceiptBinding.intentId(new WorldId("frontier:a-b"), new SubjectId("cause:c"), canonical.id(), canonical.revision());
        var oldCollisionRight = FrontierV3SettlementAssaultReceiptBinding.intentId(new WorldId("frontier:a"), new SubjectId("b:cause-c"), canonical.id(), canonical.revision());
        helper.assertFalse(oldCollisionLeft.equals(oldCollisionRight), "framed receipt inputs must reject the old world/cause delimiter collision");
        SceneLease foreignIdentity = receiptLease(canonical, new SceneLeaseId(canonical.id().value() + "-foreign"), canonical.revision());
        SceneLease foreignRevision = receiptLease(canonical, canonical.id(), canonical.revision() + 1L);
        helper.assertFalse(FrontierV3SettlementAssaultReceiptBinding.intentId(canonical.worldId(), confirmed.causeSubjectId(), foreignIdentity.id(), foreignIdentity.revision())
                        .equals(confirmed.id()), "a foreign lease identity must not share the exactly-once receipt");
        helper.assertFalse(FrontierV3SettlementAssaultReceiptBinding.intentId(canonical.worldId(), confirmed.causeSubjectId(), foreignRevision.id(), foreignRevision.revision())
                        .equals(confirmed.id()), "a stale lease revision must not share the exactly-once receipt");
        helper.assertFalse(FrontierV3SettlementAssaultReceiptBinding.intentId(canonical.worldId(), new SubjectId("cause:foreign-receipt"), canonical.id(), canonical.revision())
                        .equals(confirmed.id()), "a foreign current-revision cause must not share the exactly-once receipt");
    }

    private static SceneLease receiptLease(SceneLease source, SceneLeaseId id, long revision) {
        return SceneLease.forCause(id, source.worldId(), source.cause(), source.handoffPosition(), source.handoffInstant(), revision,
                source.status(), source.members(), source.memberPositions(), source.ambientHandoffActorIds(), source.recoveryEvidence());
    }
    private static PhysicalIntent onlyStrike(FrontierWorldState state) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE).reduce((left, right) -> right)
                .orElseThrow(() -> new IllegalStateException("the HOT strike executor did not retain a physical intent"));
    }
    private static FixedScalar fixed(float health) { return new FixedScalar(Math.max(0L, Math.round(health * FixedScalar.SCALE))); }
    /** Fault-injection port retaining only successful transactions; no fabricated effect receipt. */
    private static final class StrikeReplayStore extends EphemeralStore {
        private final java.util.ArrayList<TransactionRecord> transactions = new java.util.ArrayList<>();
        boolean failNextAppend;
        @Override public RecoveryImage recover(WorldId worldId) {
            return new RecoveryImage(worldId, Optional.empty(), List.copyOf(transactions));
        }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            if (failNextAppend) {
                failNextAppend = false;
                throw new IllegalStateException("injected strike confirmation storage interruption");
            }
            transactions.add(transaction);
            return super.append(transaction, durability);
        }
    }
}
