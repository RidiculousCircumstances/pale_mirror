package io.farfrontier.palemirror.internal.frontier.v3;


import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.AmbientBodyConfirmed;
import io.farfrontier.palemirror.internal.world.SourceGrayboxEntityAdmission;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseRestartAbsenceObserved;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3AmbientActorGameTests.*;

/** Loaded ambient-body movement and admission proofs. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3AmbientMotionGameTests {
    private FrontierV3AmbientMotionGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-harvest-support", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void indexedCommonBodyConfirmationPrecedesAnyActivityReadiness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(2, 8, 2));
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:common-indexed-admission"), 91L);
        var actor = new SubjectId("resident:1-1");
        var origin = base.initialState().actorLocations().get(actor).body();
        var bootstrap = FrontierV3CargoLoadingGameTests.translatedBootstrap(base.initialState().bootstrap(),
                feet.getX() - origin.x(), feet.getY() - origin.y(), feet.getZ() - origin.z());
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(bootstrap), new EphemeralStore(), 10_000);
        var initial = state(runtime);
        var lease = AmbientActorProcess.nextLease(initial, actor, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "common-admission-prepare", actor.value(), new AmbientLeasePrepared(lease));
        var prepared = state(runtime);
        var id = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(prepared, actor);
        var ledger = FrontierV3AmbientCarrierLedger.get(level, bootstrap.worldId());
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, initial,
                new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(bootstrap.worldId(), java.util.Optional.empty(), java.util.List.of()),
                () -> ledger.persist(level, bootstrap.worldId()));
        prepareFloor(level, feet);
        helper.assertFalse(FrontierV3ActorBodyController.readyForExecution(level, prepared, java.util.List.of(id)),
                "prepared demand cannot grant activity readiness before its body exists");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, prepared, actor, lease.handoffBody()),
                FrontierV3AmbientActorExecutor.Result.APPLIED, "the common body producer must insert its exact declared incarnation");
        helper.assertFalse(FrontierV3ActorBodyController.readyForExecution(level, state(runtime), java.util.List.of(id)),
                "insertion alone does not confirm a body in an isolated runtime");
        helper.runAfterDelay(2, () -> {
            var body = level.getEntity(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(bootstrap.worldId(), actor));
            helper.assertTrue(body instanceof Villager, "the real object must be indexed before common observation");
            var proof = FrontierV3ServerLifecycle.observeSourceJoin(level, runtime, body);
            helper.assertTrue(proof.verifiedV3Carrier(), "the actual common join firewall must accept exact provenance");
            FrontierV3AmbientPendingAdmissions.reclaimProjected(runtime, state(runtime));
            helper.assertTrue(FrontierV3ActorBodyController.readyForExecution(level, state(runtime), java.util.List.of(id)),
                    "only the common observation makes the indexed same-incarnation body ready");
            helper.assertTrue(FrontierV3AmbientPendingAdmissions.get(runtime, body.getUUID()) == null,
                    "durable body confirmation releases the join bridge without an activity transfer");
            helper.assertValueEqual(state(runtime).ambientLeases().get(actor).status(), AmbientLeaseStatus.PREPARED,
                    "physical confirmation must not activate a presentation scope");
            var stale = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(actor, id.physicalEpoch() + 1L);
            helper.assertFalse(FrontierV3ActorBodyController.readyForExecution(level, state(runtime), java.util.List.of(stale)),
                    "an older/newer incarnation cannot borrow readiness from this indexed body");
            body.discard(); FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-ambient-local-brain", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 1)
    public static void semanticArrivalRequiresExactSupportAndDeclaredGroundMedium(GameTestHelper helper) {
        SurfaceAnchor surface = SurfaceAnchor.at(0, 8, 0);
        SemanticTraversalArrival.Contract contract = new SemanticTraversalArrival.Contract(surface, Set.of(TraversalCapability.PEDESTRIAN), 2);
        helper.assertValueEqual(SemanticTraversalArrival.evaluate(new SemanticTraversalArrival.Observation(surface,
                SemanticTraversalArrival.Medium.AIR, true, true), contract), SemanticTraversalArrival.Disposition.ARRIVED,
                "only the exact named support is a pedestrian arrival");
        helper.assertValueEqual(SemanticTraversalArrival.evaluate(new SemanticTraversalArrival.Observation(surface,
                SemanticTraversalArrival.Medium.WATER, true, true), contract), SemanticTraversalArrival.Disposition.BLOCKED_MEDIUM,
                "ground work may not acquire implicit swimming from a loaded water cell");
        helper.assertValueEqual(SemanticTraversalArrival.evaluate(new SemanticTraversalArrival.Observation(SurfaceAnchor.at(1, 8, 0),
                SemanticTraversalArrival.Medium.AIR, true, true), contract), SemanticTraversalArrival.Disposition.OFF_CONTRACT,
                "a neighbouring support is not a proximity arrival or route rewrite");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-prepared-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedAmbientLeaseSurvivesRecoveryAndMaterializesOneInertBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(12, 8, 0)); prepareFloor(level, origin);
        WorldId world = new WorldId("frontier:ambient-prepared-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState initial = state(runtime);
        AmbientActorLease lease = AmbientActorProcess.nextLease(initial, resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "ambient-prepared-game-test", resident.value(), new AmbientLeasePrepared(lease));

        helper.assertValueEqual(FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime), 0,
                "restart must retain a before-effect PREPARED lease for loaded-chunk inspection");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.PREPARED,
                "prepared recovery must not turn an unacknowledged body into UNKNOWN");
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                        bodyAt(origin)), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "a retained PREPARED lease may create exactly its expected loaded-world body");
        Villager body = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
        helper.assertTrue(body != null && body.isNoAi(), "the before-HOT body must stay inert across the acknowledgement window");
        helper.assertTrue(FrontierV3AmbientActorExecutor.recognizes(runtime, body),
                "the shared graybox boundary must accept only the exact prepared V3 carrier");
        Villager forged = new Villager(net.minecraft.world.entity.EntityType.VILLAGER, level);
        forged.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, resident.value());
        forged.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        helper.assertFalse(FrontierV3AmbientActorExecutor.recognizes(runtime, forged),
                "a copied V3 tag without the canonical UUID is never an admissible carrier");
        FrontierV3CommandSubmission.submit(runtime, "ambient-prepared-game-test-hot", resident.value(),
                new AmbientBodyConfirmed(resident, lease.revision(), AmbientBodyConfirmed.Boundary.ADMISSION, lease.handoffBody(), lease.handoffBody(),
                        io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state(runtime), resident)));
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                        bodyAt(origin)), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "the durable HOT acknowledgement retains the one existing UUID rather than duplicating it");
        SubjectId carpetResident = new SubjectId("resident:1-2"); BlockPos carpet = origin.offset(4, 0, 0);
        level.setBlock(carpet.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(carpet, Blocks.RED_CARPET.defaultBlockState(), 3);
        level.setBlock(carpet.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(carpet.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), carpetResident,
                        bodyAt(carpet.above())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an owned infection carpet is a physical surface, not a reason to strand the canonical actor");
        Villager carpetBody = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), carpetResident));
        SurfaceAnchor carpetSurface = SurfaceAnchor.at(carpet.getX(), carpet.getY(), carpet.getZ());
        helper.assertTrue(carpetBody != null
                        && FrontierV3BodyObservation.capture(carpetBody).support().filter(carpetSurface::equals).isPresent()
                        && level.noCollision(carpetBody, carpetBody.getBoundingBox())
                        && level.getBlockState(carpet).is(Blocks.RED_CARPET),
                "the exact body must stand on the actual carpet collision top without replacing it: body="
                        + (carpetBody == null ? "missing" : carpetBody.position()) + " carpet=" + carpet);
        helper.assertFalse(FrontierV3AmbientActorExecutor.observeLeave(runtime, body, false),
                "an ordinary EntityLeave callback is too late to close a serialized HOT body into COLD");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                "unexpected departure retains the exact saved UUID for later recovery instead of permitting a duplicate");
        helper.assertFalse(FrontierV3AmbientActorExecutor.observeLeave(runtime, body, true),
                "server teardown must not release a saved HOT body into COLD");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                "a graceful shutdown retains the HOT lease for exact UUID recovery after restart");
        body.discard(); carpetBody.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-prepared-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void freshAmbientBodyRehydratesExactEngineeringToolFromActorCustody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(20, 8, 0)); prepareFloor(level, origin);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ambient-engineering-tool-hydration"), 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1"), tool = new SubjectId("item:bootstrap-1-engineering-tool-1");
        FrontierWorldState initial = state(runtime);
        var exactTool = initial.inventory().items().get(tool);
        FrontierWorldState equipped = initial.withInventory(initial.inventory().moveObservedItem(tool, exactTool.custody(),
                new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.Actor(resident)));

        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, equipped, resident, bodyAt(origin)),
                FrontierV3AmbientActorExecutor.Result.APPLIED,
                "a fresh actor body must materialize only its one canonical identity");
        Villager body = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(equipped, resident));
        helper.assertTrue(body != null && FrontierV3CargoHandoffExecutor.exactMatch(
                        body.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND), exactTool),
                "canonical engineering-tool custody must survive fresh COLD/restart materialization as the same tagged physical stack");
        body.discard(); FrontierV3AmbientActorExecutor.forget(runtime); runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-prepared-recovery", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void unindexedManagedJoinDefersAdmissionUntilItsExactUuidIsPublished(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldId world = new WorldId("frontier:ambient-unindexed-join-game-test");
        var config = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var store = new EphemeralStore();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, config.initialState(), store.recover(world),
                () -> ledger.persist(level, world));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(config, store, 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState before = state(runtime);
        FrontierV3CommandSubmission.submit(runtime, "ambient-unindexed-prepare", resident.value(),
                new AmbientLeasePrepared(AmbientActorProcess.nextLease(before, resident, runtime.checkpointImage().orElseThrow().instant())));
        FrontierWorldState prepared = state(runtime);
        Villager joining = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (joining == null) throw new IllegalStateException("game test could not create resident body");
        joining.setUUID(FrontierV3AmbientActorExecutor.entityId(prepared, resident));
        BlockPos observed = helper.absolutePos(new BlockPos(4, 8, 4)); joining.setPos(observed.getX() + 0.5D, observed.getY(), observed.getZ() + 0.5D);
        joining.setNoAi(true); joining.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, resident.value());
        joining.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        joining.getPersistentData().putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, 1L);
        FrontierV3ActorCarrierComposition.stamp(joining, FrontierV3AmbientActorExecutor.carrierDeclaration(prepared, resident, joining.getUUID(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L));
        helper.assertTrue(ledger.beginFirstAdmission(FrontierV3ActorOwnerBinding.body(FrontierV3AmbientActorExecutor.carrierDeclaration(prepared, resident,
                                joining.getUUID(),
                                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                                1L))),
                "the unindexed body requires its retained before-effect first-admission attempt");
        ledger.persist(level, world);
        FrontierV3ServerLifecycle.JoinFirewallProof joiningProof = FrontierV3ServerLifecycle.observeSourceJoin(runtime, joining);
        helper.assertValueEqual(joiningProof.lifecycleAdmission(), FrontierV3ServerLifecycle.EntityJoinAdmission.RETAINED,
                "an exact PREPARED managed body must be retained while its UUID is not yet indexed");
        helper.assertTrue(joiningProof.verifiedV3Carrier()
                        && !SourceGrayboxEntityAdmission.rejectsSourceMob(joiningProof, true, false),
                "the ordinary source composition must preserve the exact retained bridge before its first projection");
        FrontierV3AmbientAdmissionDiagnostic diagnostic = FrontierV3AmbientActorExecutor.admissionDiagnostic(level, runtime, prepared, resident);
        helper.assertValueEqual(diagnostic.status(), "PENDING_UNINDEXED",
                "the diagnostic must expose the pre-index bridge instead of claiming the actor is ready");
        helper.assertTrue(diagnostic.pending(), "the exact body must retain the bounded pending identity bridge");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, prepared, resident, prepared.actorLocations().get(resident).body()),
                FrontierV3AmbientActorExecutor.Result.PENDING,
                "a pending exact UUID must defer admission rather than create a second managed body");
        Villager duplicate = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (duplicate == null) throw new IllegalStateException("game test could not create duplicate resident body");
        duplicate.setUUID(joining.getUUID()); duplicate.setPos(joining.position()); duplicate.setNoAi(true);
        duplicate.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, resident.value());
        duplicate.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        duplicate.getPersistentData().putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, 1L);
        FrontierV3ActorCarrierComposition.stamp(duplicate, FrontierV3AmbientActorExecutor.carrierDeclaration(prepared, resident, duplicate.getUUID(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L));
        FrontierV3ServerLifecycle.JoinFirewallProof duplicateProof = FrontierV3ServerLifecycle.observeSourceJoin(runtime, duplicate);
        helper.assertValueEqual(duplicateProof.lifecycleAdmission(), FrontierV3ServerLifecycle.EntityJoinAdmission.DUPLICATE_UNINDEXED,
                "a second unindexed body with the same exact UUID must be rejected before Minecraft admits it");
        helper.assertTrue(SourceGrayboxEntityAdmission.rejectsSourceMob(duplicateProof, true, false),
                "the ordinary source composition must still cancel a duplicate retained UUID");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, prepared, resident, prepared.actorLocations().get(resident).body()),
                FrontierV3AmbientActorExecutor.Result.PENDING,
                "rejecting the later duplicate must retain the first exact body as the only pending admission");
        helper.assertTrue(FrontierV3AmbientActorExecutor.retainsPendingJoin(runtime, joining),
                "the exact unindexed body must remain strongly retained until lifecycle cleanup or strict handoff");
        runtime.quarantine(new IllegalStateException("focused pending-admission cleanup"));
        FrontierV3ServerLifecycle.releaseRuntime(runtime);
        helper.assertFalse(FrontierV3AmbientActorExecutor.retainsPendingJoin(runtime, joining),
                "the shared quarantine/stop cleanup must release the pending Entity reference");
        duplicate.discard();
        joining.discard(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-restart-absence", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 20)
    public static void retainedCarrierPermitsFreshAdmissionButLoadedAbsenceAloneDoesNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ambient-restart-absence-game-test"), 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState initial = state(runtime);
        BodyPosition anchor = initial.actorLocations().get(resident).body();
        prepareFloor(level, new BlockPos(anchor.x(), anchor.y(), anchor.z()));
        // Keep the body under this GameTest's entity-ticking structure ticket;
        // the canonical anchor remains the independently tested restart witness.
        BodyPosition localBody = bodyAt(helper.absolutePos(new BlockPos(4, 8, 4)));
        prepareFloor(level, new BlockPos(localBody.x(), localBody.y(), localBody.z()));
        AmbientActorLease lease = new AmbientActorLease(resident, anchor, runtime.checkpointImage().orElseThrow().instant(), 1L,
                AmbientLeaseStatus.PREPARED, AmbientGoalKind.WORK, anchor);
        FrontierV3CommandSubmission.submit(runtime, "ambient-restart-absence-prepare", resident.value(), new AmbientLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "ambient-restart-absence-hot", resident.value(),
                new AmbientBodyConfirmed(resident, lease.revision(), AmbientBodyConfirmed.Boundary.ADMISSION, lease.handoffBody(), lease.handoffBody(),
                        io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state(runtime), resident)));
        helper.assertValueEqual(FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                "an active body becomes explicitly unknown at restart");
        FrontierWorldState unknown = state(runtime);
        helper.assertFalse(FrontierV3AmbientActorExecutor.restartAbsenceIsObserved(level, unknown, resident, unknown.ambientLeases().get(resident)),
                "an empty anchor cannot manufacture missing durable custody");
        var ledger = FrontierV3AmbientCarrierLedger.get(level, unknown.bootstrap().worldId());
        var inactive = FrontierV3AmbientActorExecutor.carrierDeclaration(unknown, resident, FrontierV3AmbientActorExecutor.entityId(unknown, resident), FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 1L);
        helper.assertTrue(ledger.fence(inactive, lease.revision(), lease.revision()),
                "fixture supplies an explicit retained release fence, not inferred absence");
        helper.assertTrue(FrontierV3AmbientActorExecutor.restartCustodyIsRetained(unknown, resident,
                unknown.ambientLeases().get(resident), ledger), "same-generation retained custody allows cancellation");
        FrontierV3CommandSubmission.submit(runtime, "ambient-restart-absence-observed", resident.value(),
                new AmbientLeaseRestartAbsenceObserved(resident, anchor));
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.CLOSED,
                "absence closes the failed HOT hand-off without inferring a death");
        AmbientActorLease fresh = AmbientActorProcess.nextLease(state(runtime), resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "ambient-restart-absence-fresh", resident.value(), new AmbientLeasePrepared(fresh));
        publishProjectionBeforeManagedJoin(helper, level, runtime);
        helper.assertFalse(FrontierV3AmbientActorExecutor.mayCreateFreshBody(true, false, true),
                "production admission must defer while Minecraft has loaded blocks but is still restoring entity storage");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, runtime, state(runtime), resident, localBody), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the isolated fixture may admit its known-empty test chunk exactly once");
        helper.runAfterDelay(1L, () -> {
            try {
                Entity recovered = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
                helper.assertTrue(recovered instanceof Villager && FrontierV3AmbientActorExecutor.recognizes(runtime, recovered),
                        "re-materialization retains the one exact UUID and normal ownership proof");
                recovered.discard(); helper.succeed();
            } finally { FrontierV3AmbientActorExecutor.forget(runtime); }
        });
    }

    @GameTest(batch = "pm-frontier-v3-ambient-local-brain", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 70)
    public static void postHarvestWorkLeaseMovesTheSameFarmerAwayFromTheFinalFieldStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos finalStation = helper.absolutePos(new BlockPos(0, 8, 0));
        prepareSquareFloor(level, finalStation, 6);
        var source = FrontierWorldState.initial(io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper.create(
                new WorldId("frontier:ambient-post-harvest-return"), 91L));
        var sourceBody = source.actorLocations().get(new SubjectId("resident:1-1")).body();
        var bootstrap = FrontierV3CargoLoadingGameTests.translatedBootstrap(source.bootstrap(),
                finalStation.getX() - sourceBody.x(), finalStation.getY() - sourceBody.y(),
                finalStation.getZ() - sourceBody.z());
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(bootstrap), new EphemeralStore(), 10_000);
        FrontierWorldState state = state(runtime); SubjectId farmer = new SubjectId("resident:1-1");
        BodyPosition handoff = bodyAt(finalStation);
        // This fixture is the physical half of ResourceSiteHarvestTraversal.workReturnSurface:
        // one same-level, four-cell declared field-edge departure, not a synthetic relocation
        // into a farm centre or a vanilla-AI wander goal.
        AmbientActorLease lease = new AmbientActorLease(farmer, handoff, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, handoff.offset(4, 0, 0));
        Villager body = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("game test could not create terminal farmer");
        body.setPos(finalStation.getX() + 0.5D, finalStation.getY(), finalStation.getZ() + 0.5D);
        body.setNoAi(true); body.setPersistenceRequired();
        helper.assertTrue(level.addFreshEntity(body), "the exact terminal farmer must enter the naturally loaded fixture once");
        helper.runAfterDelay(1L, () -> drivePursuit(helper, level, runtime, state, farmer, body, lease, 40, () -> {
            helper.assertTrue(body.isNoAi() && body.getX() > finalStation.getX() + 3.25D,
                    "the same terminal farmer must visibly leave the crop station along its retained WORK goal instead of colliding idle at crop 63");
            helper.assertFalse(body.swinging, "post-harvest travel must not retain the crop-work gesture");
            body.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
        }));
    }

    @GameTest(batch = "pm-frontier-v3-scout-patrol-cursor", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 140)
    public static void hotScoutFollowsItsLeasedCanonicalPatrolStepRatherThanASeparateLocalCircle(GameTestHelper helper) {
        // Keep this footprint inside the stock template's isolated test cell: the full suite
        // runs many templates in parallel, whereas this proof needs only one four-block step.
        ServerLevel level = helper.getLevel(); BlockPos feet = helper.absolutePos(new BlockPos(3, 8, 3)); prepareMotionArena(level, feet, 5, 3);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:hot-scout-cursor"), 91L), new EphemeralStore(), 10_000);
        FrontierWorldState state = state(runtime); SubjectId scout = new SubjectId("bioform:west-1");
        BodyPosition handoff = bodyAt(feet);
        BodyPosition target = handoff.offset(4, 0, 0);
        AmbientActorLease lease = new AmbientActorLease(scout, handoff, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.SCOUT_PATROL, target);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, scout, handoff), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the exact scout body must materialize at its current patrol cursor");
        // Entity insertion is visible only on the following server tick in a full parallel
        // GameTest run.  Do not inspect/move the pre-index body object as if that were a
        // materialization acknowledgement.
        helper.runAfterDelay(1L, () -> {
            Entity entity = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, scout));
            helper.assertTrue(entity instanceof Zombie, "the exact Scout must be indexed before its HOT patrol step");
            Zombie body = (Zombie) entity;
            drivePursuit(helper, level, runtime, state, scout, body, lease, 100, () -> {
                BlockPos physicalTarget = new BlockPos(target.x(), target.y(), target.z());
                helper.assertTrue(body.isNoAi()
                                && body.distanceToSqr(physicalTarget.getX() + 0.5D, physicalTarget.getY(), physicalTarget.getZ() + 0.5D) < 2.25D,
                        "the HOT scout must approach its one canonical next cursor without vanilla AI or a local substitute; body="
                                + body.position() + " target=" + target);
                body.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
            });
        });
    }

    @GameTest(batch = "pm-frontier-v3-assembly-headroom", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void exactAssemblyTargetRejectsLoadedObstructionWithoutClimbingToAnotherFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos floor = helper.absolutePos(new BlockPos(4, 8, 0));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
        BlockPosition anchor = new BlockPosition(floor.getX(), floor.getY(), floor.getZ());
        helper.assertTrue(FrontierV3StandingPosition.hasExactHeadroom(level, anchor),
                "a canonical floor with two clear body cells admits its exact assembly cursor");
        helper.assertTrue(FrontierV3StandingPosition.hasExactStandingColumn(level, anchor),
                "a retained cursor needs both its exact support and those two clear body cells");
        helper.assertValueEqual(FrontierV3StandingPosition.aboveExactFloor(level, anchor), floor.above(),
                "the exact cursor resolves only to the feet cell directly above its retained support");
        level.setBlock(floor.above(), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3StandingPosition.hasExactHeadroom(level, anchor),
                "a player block at the exact feet cell is a loaded-world deferral, not an invitation to climb it");
        helper.assertTrue(FrontierV3StandingPosition.aboveExactFloor(level, anchor) == null,
                "an obstruction at the exact feet cell may not be reinterpreted as a higher floor");
        level.setBlock(floor.above(), Blocks.LIME_CARPET.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3StandingPosition.hasExactStandingColumn(level, anchor),
                "the owned thin infection/route overlay is traversable at the same retained surface, not a higher route");
        helper.assertValueEqual(FrontierV3StandingPosition.aboveExactFloor(level, anchor), floor.above(),
                "a thin overlay preserves the exact canonical feet cell rather than changing its floor");
        level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3StandingPosition.hasExactHeadroom(level, anchor),
                "headroom remains a narrow volume observation independent from a floor claim");
        helper.assertFalse(FrontierV3StandingPosition.hasExactStandingColumn(level, anchor),
                "a removed retained support is a loaded-world blocker, not an invitation to fall or choose another floor");
        helper.succeed();
    }

    private static void prepareSquareFloor(ServerLevel level, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) prepareFloor(level, center.offset(x, 0, z));
    }
    /** Keeps a parallel GameTest fixture inside its own stock-template rectangle. */
    private static void prepareMotionArena(ServerLevel level, BlockPos origin, int length, int width) {
        for (int x = 0; x <= length; x++) for (int z = 0; z < width; z++) prepareFloor(level, origin.offset(x, 0, z));
    }
    private static void drivePursuit(GameTestHelper helper, ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                     FrontierWorldState state, SubjectId actor, net.minecraft.world.entity.Mob body, AmbientActorLease lease,
                                     int remaining, Runnable complete) {
        if (remaining == 0) { complete.run(); return; }
        FrontierV3AmbientActorExecutor.pursueLocalGoal(level, runtime, state, actor, body, lease);
        helper.runAfterDelay(1L, () -> {
            FrontierV3ControlledMobMotion.advance(body);
            drivePursuit(helper, level, runtime, state, actor, body, lease, remaining - 1, complete);
        });
    }
}
