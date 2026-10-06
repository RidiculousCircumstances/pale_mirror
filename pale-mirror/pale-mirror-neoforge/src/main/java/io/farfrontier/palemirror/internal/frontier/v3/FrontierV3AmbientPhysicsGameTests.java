package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Native collision proof for retained managed-body physics after an ordinary support removal. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3AmbientPhysicsGameTests {
    private FrontierV3AmbientPhysicsGameTests() { }

    @GameTest(batch = "pm-frontier-v3-ambient-physics", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void savedAirborneReturnPassesJoinAndFallsWithoutGrantingStationArrival(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(2, 4, 2));
        prepareFallArena(level, support);
        Fixture fixture = fixture(support, helper.absolutePos(new BlockPos(5, 4, 2)));
        var initial = ActorBodyAuthority.demand(fixture.state(), fixture.resident());
        initial = ActorBodyAuthority.running(initial, ActorBodyAuthority.current(initial, fixture.resident()));
        var runtime = runtime(level, initial);
        var state = runtime.decodedState().orElseThrow();
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, fixture.resident(),
                FrontierV3AmbientActorExecutor.entityId(state, fixture.resident()),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                ActorBodyAuthority.current(state, fixture.resident()).physicalEpoch());
        var binding = FrontierV3ActorOwnerBinding.body(declaration);
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        helper.assertTrue(ledger.beginFirstAdmission(binding), "fixture retains exact initial lifetime");
        helper.assertTrue(ledger.acknowledgeFirstAdmission(ledger.firstAdmission(fixture.resident()).orElseThrow(), binding),
                "fixture has established physical history");
        Villager savedBody = EntityType.VILLAGER.create(level);
        if (savedBody == null) throw new IllegalStateException("test saved villager creation failed");
        savedBody.setUUID(declaration.entityId());
        binding.stamp(savedBody);
        savedBody.getPersistentData().putLong(FrontierV3ActorBodyController.RESIDENCE_KEY, ledger.beginBodyResidence(declaration));
        savedBody.setNoAi(true);
        savedBody.setPos(support.getX() + .5, support.getY() + 4.0, support.getZ() + .5);
        savedBody.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, 2));
        var departure = FrontierV3ActorBodyController.captureReturnedBody(level, state, savedBody).orElseThrow(
                () -> new IllegalStateException("the exact saved fixture body must have a capturable residence"));
        helper.assertTrue(FrontierV3SupportedBodyCapture.observe(level, savedBody).isEmpty(), "returned pose is really airborne");
        helper.assertTrue(ledger.recordBodyDeparture(departure), "fixture retains exact departure");
        helper.assertTrue(ledger.fence(departure.identity(), declaration.epoch(), 0), "fixture retains inactive fence");
        var saved = savedBody.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        Villager returned = EntityType.VILLAGER.create(level);
        if (returned == null) throw new IllegalStateException("test returned villager creation failed");
        returned.load(saved);
        var proof = FrontierV3ServerLifecycle.observeSourceJoin(level, runtime, returned);
        helper.assertTrue(proof.verifiedV3Carrier(), "the ordinary join composition recognizes an exact airborne return");
        helper.assertTrue(!ledger.hasBodyDeparture(fixture.resident()) && !ledger.hasCarrier(fixture.resident()),
                "only exact return proof consumes the old departure/fence");
        helper.assertTrue(FrontierV3SupportedBodyCapture.observe(level, returned).isEmpty(),
                "lifetime admission has not fabricated grounded work arrival");
        helper.assertTrue(level.addFreshEntity(returned), "Minecraft indexes the returned body instead of canceling it");
        for (int tick = 1; tick < 15; tick++) helper.runAtTickTime(tick, () ->
                FrontierV3ControlledMobMotion.advanceAtEntityBoundary(returned));
        helper.runAtTickTime(16, () -> {
            FrontierV3AmbientPendingAdmissions.reclaimProjected(runtime, runtime.decodedState().orElseThrow());
            helper.assertTrue(level.getEntity(returned.getUUID()) == returned && returned.getY() < support.getY() + 4.0,
                    "the same indexed identity falls under ordinary physics");
            helper.assertTrue(FrontierV3AmbientPendingAdmissions.get(runtime, returned.getUUID()) == null,
                    "indexed body confirmation ends the temporary join bridge");
            returned.discard(); runtime.shutdown(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-ambient-physics", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void canceledJoinWithdrawsOnlyItsExactPendingObject(GameTestHelper helper) {
        var fixture = fixture(helper.absolutePos(new BlockPos(2, 4, 2)), helper.absolutePos(new BlockPos(5, 4, 2)));
        var runtime = runtime(helper.getLevel(), fixture.state());
        var retained = EntityType.VILLAGER.create(helper.getLevel());
        var duplicate = EntityType.VILLAGER.create(helper.getLevel());
        if (retained == null || duplicate == null) throw new IllegalStateException("join fixtures unavailable");
        duplicate.setUUID(retained.getUUID());
        FrontierV3AmbientPendingAdmissions.retain(runtime, retained, fixture.resident());
        helper.assertTrue(!FrontierV3AmbientPendingAdmissions.rejectJoin(runtime, duplicate), "rejected duplicate cannot discard the incumbent");
        helper.assertTrue(FrontierV3AmbientPendingAdmissions.get(runtime, retained.getUUID()) == retained, "incumbent survives");
        helper.assertTrue(FrontierV3AmbientPendingAdmissions.rejectJoin(runtime, retained), "canceling the exact join clears its bridge");
        helper.assertTrue(FrontierV3AmbientPendingAdmissions.get(runtime, retained.getUUID()) == null, "a never-indexed nonremoved object cannot linger");
        runtime.shutdown(); helper.succeed();
    }

    /**
     * A canonical actor has one exact body column.  A live occupant is a local physical
     * obstruction, not permission to place a second body, select an apron cell, or change the
     * actor's durable location.  Clearing that observed occupant permits the same exact body.
     */
    @GameTest(batch = "pm-frontier-v3-ambient-physics", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void freshAmbientAdmissionDefersForOccupiedExactBodyColumn(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(2, 4, 2));
        BlockPos otherSupport = helper.absolutePos(new BlockPos(5, 4, 2));
        prepareFallArena(helper.getLevel(), support);
        prepareFallArena(helper.getLevel(), otherSupport);
        Fixture fixture = fixture(support, otherSupport);
        var runtime = runtime(helper.getLevel(), ActorBodyAuthority.demand(fixture.state(), fixture.resident()));
        var prepared = runtime.decodedState().orElseThrow();
        BodyPosition expected = prepared.actorLocations().get(fixture.resident()).body();
        Villager occupant = EntityType.VILLAGER.create(helper.getLevel());
        if (occupant == null) throw new IllegalStateException("test body column occupant could not be created");
        occupant.setNoAi(true);
        occupant.setPos(support.getX() + .5D, support.getY() + 1.0D, support.getZ() + .5D);
        helper.getLevel().addFreshEntity(occupant);

        // GameTest registers spawned fixtures on the next server turn, which is the same
        // physical observation point at which ordinary ambient admission runs.
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(occupant.isAlive() && helper.getLevel().getEntity(occupant.getUUID()) == occupant,
                    "the ordinary physical occupant must be visible before admission is evaluated");
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(helper.getLevel(), prepared, fixture.resident(), expected),
                    FrontierV3AmbientActorExecutor.Result.DEFERRED,
                    "a live body occupying the canonical column must defer exact admission rather than create an overlapping managed actor");
            helper.assertTrue(helper.getLevel().getEntity(FrontierV3AmbientActorExecutor.entityId(prepared, fixture.resident())) == null,
                    "a deferred exact admission must leave no replacement or duplicate UUID body");
            occupant.discard();
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(helper.getLevel(), prepared, fixture.resident(), expected),
                    FrontierV3AmbientActorExecutor.Result.APPLIED,
                    "the unchanged canonical body must materialize once the observed local obstruction clears");
            Mob admitted = (Mob) helper.getLevel().getEntity(FrontierV3AmbientActorExecutor.entityId(prepared, fixture.resident()));
            helper.assertTrue(admitted != null && admitted.blockPosition().equals(support.above()),
                    "the admitted body must retain its exact canonical column rather than an alternate physical placement");
            admitted.discard();
            runtime.shutdown();
            helper.succeed();
        });
    }

    /** A recovered grounded body must not turn a serialized historical fall counter into a new death. */
    @GameTest(batch = "pm-frontier-v3-ambient-physics", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void groundedRecoveredBodyClearsHistoricalFallDistanceBeforePhysics(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(2, 4, 2));
        prepareFallArena(helper.getLevel(), support);
        Villager recovered = EntityType.VILLAGER.create(helper.getLevel());
        if (recovered == null) throw new IllegalStateException("test recovered body could not be created");
        recovered.setNoAi(true);
        recovered.setPos(support.getX() + .5D, support.getY() + 1.0D, support.getZ() + .5D);
        recovered.fallDistance = 64.0F;
        helper.getLevel().addFreshEntity(recovered);
        helper.runAfterDelay(1, () -> {
            FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(recovered);
            FrontierV3ControlledMobMotion.advanceAtEntityBoundary(recovered);
            helper.assertTrue(recovered.isAlive() && recovered.getHealth() == recovered.getMaxHealth() && recovered.fallDistance == 0.0F,
                    "a grounded recovered body must clear historical fall state before ordinary physics can apply a new fall: health="
                            + recovered.getHealth() + " fallDistance=" + recovered.fallDistance + " position=" + recovered.position());
            recovered.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-ambient-physics", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void airborneRecoveredBodyRetainsOrdinaryFallEvidence(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(2, 4, 2));
        prepareFallArena(helper.getLevel(), support);
        Villager recovered = EntityType.VILLAGER.create(helper.getLevel());
        if (recovered == null) throw new IllegalStateException("test recovered body could not be created");
        recovered.setNoAi(true);
        recovered.setNoGravity(true);
        recovered.setPos(support.getX() + .5D, support.getY() + 4.0D, support.getZ() + .5D);
        recovered.fallDistance = 6.0F;
        helper.getLevel().addFreshEntity(recovered);
        helper.runAfterDelay(1, () -> {
            FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(recovered);
            helper.assertTrue(recovered.fallDistance == 6.0F,
                    "an unsupported recovered body must retain real fall evidence rather than masking an in-flight physical outcome");
            recovered.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-ambient-physics", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void retainedResidentAndBioformFallAfterSupportRemovalWithoutCoordinateReset(GameTestHelper helper) {
        // Raise the temporary supports above the bastion template's own floor.  The lower owned
        // floor leaves an unambiguous three-block ordinary fall rather than a one-block fixture
        // settle against template scenery.
        BlockPos residentSupport = helper.absolutePos(new BlockPos(2, 4, 2));
        BlockPos bioformSupport = helper.absolutePos(new BlockPos(5, 4, 2));
        // A lower ordinary floor makes landing/collision observable after the player-equivalent
        // support removal; neither body is moved by the test after its initial materialization.
        helper.getLevel().setBlock(residentSupport.below(3), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(bioformSupport.below(3), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(residentSupport, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(bioformSupport, Blocks.STONE.defaultBlockState(), 3);
        Villager resident = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 5.0D, 2.5D));
        Zombie bioform = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5D, 5.0D, 2.5D));
        resident.setNoGravity(true); bioform.setNoGravity(true);
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(resident);
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(bioform);
        double residentInitialY = resident.getY(), bioformInitialY = bioform.getY();

        helper.runAtTickTime(2, () -> {
            helper.getLevel().setBlock(residentSupport, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(bioformSupport, Blocks.AIR.defaultBlockState(), 3);
        });
        // Mirror the normal HOT executor cadence.  The bridge owns only NoAI's missing vertical
        // travel half; neither test body receives an X/Z target or a coordinate reset.
        for (int tick = 3; tick < 30; tick++) {
            helper.runAtTickTime(tick, () -> {
                FrontierV3ControlledMobMotion.advanceAtEntityBoundary(resident);
                FrontierV3ControlledMobMotion.advanceAtEntityBoundary(bioform);
            });
        }
        helper.runAtTickTime(30, () -> {
            helper.assertFalse(resident.isNoGravity() || bioform.isNoGravity(),
                    "a retained managed body must clear legacy no-gravity before ordinary physics");
            helper.assertTrue(resident.getY() < residentInitialY - 2.0D && bioform.getY() < bioformInitialY - 2.0D,
                    "resident and bioform must fall after their support is removed rather than hover or snap back: resident="
                            + resident.position() + " bioform=" + bioform.position());
            helper.assertTrue(Math.abs(resident.getY() - (residentSupport.getY() - 2.0D)) < 1.0E-6D
                            && Math.abs(bioform.getY() - (bioformSupport.getY() - 2.0D)) < 1.0E-6D,
                    "ordinary collision must retain both managed bodies exactly on the declared lower support: resident="
                            + resident.position() + " bioform=" + bioform.position());
            helper.assertTrue(resident.fallDistance == 0.0F && bioform.fallDistance == 0.0F,
                    "a completed managed landing must not retain fall distance into a later HOT/restart turn: resident="
                            + resident.fallDistance + " bioform=" + bioform.fallDistance);
            helper.succeed();
        });
    }

    /**
     * Production admission/cadence proof for the support-loss defect.  Unlike the primitive
     * fixture above, this creates only canonical residents/bioforms and ordinary player demand:
     * the ambient executor owns PREPARED -> HOT admission, the retained UUID bodies, and every
     * subsequent ordinary-physics turn.
     */
    @GameTest(batch = "pm-frontier-v3-ambient-physics", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 400)
    public static void demandedManagedResidentAndBioformFallThroughExecutorCadence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Keep the complete local fall surface inside this test's structure. The admitted
        // scout may pursue its retained local target before the player removes a support, so a
        // single launch column would test the template floor rather than the actor's actual
        // physical collision path.
        BlockPos residentSupport = helper.absolutePos(new BlockPos(12, 8, 12));
        BlockPos bioformSupport = residentSupport.offset(0, 0, 10);
        Fixture fixture = fixture(residentSupport, bioformSupport);
        // Both launch columns use the translated world's uniform ground datum. The scout's
        // real goal must have that same support height; a floor one block lower would never
        // satisfy exact target admission and would test an impossible fixture, not physics.
        prepareFallCorridor(level, residentSupport, fixture.residentWorkTarget());
        prepareFallCorridor(level, bioformSupport, AmbientActorProcess.nextLease(fixture.state(), fixture.bioform(),
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO).goalBody().supportingSurface().support());
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(level, fixture.state());
        ServerPlayer observer = helper.makeMockServerPlayerInLevel();
        observer.setPos(residentSupport.getX() + 6.5D, residentSupport.getY() + 1.0D, residentSupport.getZ() + 6.5D);
        double[] initialY = { Double.NaN, Double.NaN };
        boolean[] exactHotBodies = { false }, movingBeforeLoss = { false }, idleBeforeLoss = { false };
        int[] lossTurn = { -1 };
        BodyPosition[] landed = new BodyPosition[2]; BlockPos[] supportsAtLoss = new BlockPos[2];

        for (int tick = 1; tick <= 365; tick++) {
            int turn = tick;
            helper.runAtTickTime(tick, () -> {
                drive(level, runtime);
                Mob resident = managed(level, runtime, fixture.resident());
                Mob bioform = managed(level, runtime, fixture.bioform());
                if (resident != null && bioform != null) {
                    exactHotBodies[0] = runtime.decodedState().orElseThrow().ambientLeases().get(fixture.resident()).status()
                            == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT
                            && runtime.decodedState().orElseThrow().ambientLeases().get(fixture.bioform()).status()
                            == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT;
                    if (Double.isNaN(initialY[0])) { initialY[0] = resident.getY(); initialY[1] = bioform.getY(); }
                    if (lossTurn[0] < 0) movingBeforeLoss[0] |= bioform.getDeltaMovement().horizontalDistanceSqr() > 0.0D;
                }
                // Begin the intervention only after real common-body admission and
                // native motion. A bounded probe may admit this exact pair later
                // than a fixture's arbitrary twenty-fourth callback.
                if (lossTurn[0] < 0 && exactHotBodies[0] && movingBeforeLoss[0]) {
                    helper.assertTrue(exactHotBodies[0], "ordinary player demand must admit the canonical resident and active bioform through their exact HOT leases");
                    // Freeze the real admitted resident at the normal retained-body authority
                    // boundary while the separately admitted bioform remains the observed moving
                    // case.  No fixture entity is substituted or manually repositioned.
                    var current = runtime.decodedState().orElseThrow();
                    FrontierV3AmbientActuation.capture(current, runtime, resident,
                            current.ambientLeases().get(fixture.resident()))
                            .ifPresent(actuation -> FrontierV3GoalNavigation.stop(resident, actuation));
                    idleBeforeLoss[0] = resident.getDeltaMovement().horizontalDistanceSqr() == 0.0D;
                    helper.assertTrue(idleBeforeLoss[0] && movingBeforeLoss[0]
                                    && FrontierV3ControlledMobMotion.ordinaryPhysicsRegistered(resident)
                                    && FrontierV3ControlledMobMotion.ordinaryPhysicsRegistered(bioform),
                            "support loss must cover both an idle managed resident and a moving managed bioform through the ordinary executor cadence: idle="
                                    + idleBeforeLoss[0] + " moving=" + movingBeforeLoss[0]
                                    + " residentPhysics=" + FrontierV3ControlledMobMotion.ordinaryPhysicsRegistered(resident)
                                    + " bioformPhysics=" + FrontierV3ControlledMobMotion.ordinaryPhysicsRegistered(bioform));
                    // This is the ordinary player input boundary, not a fixture world edit:
                    // vanilla's server game mode performs both breaks while the executor owns
                    // the two retained HOT UUIDs.
                    List<BlockPos> residentFootprint = supportingBlocks(resident);
                    List<BlockPos> bioformFootprint = supportingBlocks(bioform);
                    supportsAtLoss[0] = residentFootprint.getFirst(); supportsAtLoss[1] = bioformFootprint.getFirst();
                    helper.assertTrue(residentFootprint.stream().allMatch(observer.gameMode::destroyBlock)
                                    && bioformFootprint.stream().allMatch(observer.gameMode::destroyBlock),
                            "the ordinary observer must be able to remove every physical support under each exact body footprint");
                    helper.assertTrue(residentFootprint.stream().allMatch(position -> level.getBlockState(position).isAir())
                                    && bioformFootprint.stream().allMatch(position -> level.getBlockState(position).isAir()),
                            "the ordinary player break must clear each exact physical footprint before HOT physics continues");
                    lossTurn[0] = turn;
                }
                if (lossTurn[0] >= 0 && landed[0] == null && turn >= lossTurn[0] + 40) {
                    helper.assertTrue(resident != null && bioform != null, "the exact HOT bodies must remain observable through their fall and landing");
                    landed[0] = FrontierV3AmbientActorExecutor.observedBody(resident);
                    landed[1] = FrontierV3AmbientActorExecutor.observedBody(bioform);
                    helper.assertTrue(!resident.isNoGravity() && !bioform.isNoGravity()
                                    && resident.getY() < initialY[0] - 2.0D && bioform.getY() < initialY[1] - 2.0D
                            && Math.abs(resident.getY() - (supportsAtLoss[0].getY() - 2.0D)) < 1.0E-6D
                            && Math.abs(bioform.getY() - (supportsAtLoss[1].getY() - 2.0D)) < 1.0E-6D,
                            "ordinary collision must land the moving managed bodies without hover, reset, teleport, or a second route: resident="
                                    + resident.position() + " support=" + supportsAtLoss[0] + " bioform=" + bioform.position()
                                    + " support=" + supportsAtLoss[1]);
                    observer.connection.disconnect(Component.literal("ambient support-loss fixture complete"));
                    // GameTest's mock connection records the ordinary disconnect but does not
                    // run the dedicated-server player-list removal loop. Remove that already
                    // disconnected mock from this level so the following bounded HOT release
                    // observes the same no-player world that a real server tick exposes.
                    observer.setGameMode(GameType.SPECTATOR);
                    observer.discard();
                }
            });
        }
        helper.runAtTickTime(366, () -> {
            helper.assertTrue(lossTurn[0] >= 0 && landed[0] != null,
                    "the actual pair must reach admitted movement, support removal and landing within the finite fixture bound");
            FrontierWorldState state = runtime.decodedState().orElseThrow();
            helper.assertTrue(state.ambientLeases().get(fixture.resident()).status() == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED,
                    "the disconnected exact observer must type the resident's collision-observed HOT lease through one bounded release");
            // GameTest batches share a physical level, so independently-created mock players may
            // still be within the bioform's safety radius. That is a real HOT demand condition,
            // not an authority this fixture can erase. In either case its collision landing is
            // canonical; a separate no-player native carrier owns global demand-loss evidence.
            helper.assertTrue(state.ambientLeases().get(fixture.bioform()).status() == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED
                            || managed(level, runtime, fixture.bioform()) != null,
                    "a concurrently demanded bioform must retain its exact landed body rather than hover, reset or duplicate");
            helper.assertTrue(state.actorLocations().get(fixture.resident()).body().equals(landed[0]),
                    "the released resident must canonically retain its collision-observed landing body, never reset it to the removed support");
            if (state.ambientLeases().get(fixture.bioform()).status() == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED) {
                helper.assertTrue(state.actorLocations().get(fixture.bioform()).body().equals(landed[1]),
                        "a released bioform must canonically retain its collision-observed landing body, never reset it to the removed support");
            } else {
                Mob retainedBioform = managed(level, runtime, fixture.bioform());
                helper.assertTrue(retainedBioform != null && !retainedBioform.isNoGravity()
                                && retainedBioform.getY() <= landed[1].y(),
                        "a concurrently demanded bioform must retain the exact ordinary-physics body below its removed support; HOT motion is not a canonical release observation");
            }
            Mob retainedResident = managed(level, runtime, fixture.resident());
            helper.assertTrue(retainedResident != null
                            && FrontierV3AmbientActorExecutor.observedBody(retainedResident).equals(landed[0])
                            && ActorBodyAuthority.retainsPhysicalCustody(state, fixture.resident())
                            && !ActorExecutionCoordinator.coldAvailable(state, fixture.resident()),
                    "closing presentation must retain the exact loaded body and exclude a competing COLD writer; only natural body unload releases physical custody");
            Mob retainedBioform = managed(level, runtime, fixture.bioform());
            runtime.shutdown();
            retainedResident.discard();
            if (retainedBioform != null) retainedBioform.discard();
            helper.succeed();
        });
    }

    private static void prepareFallArena(ServerLevel level, BlockPos support) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos column = support.offset(dx, 0, dz);
            level.setBlock(column.below(3), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(column.below(2), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(column.below(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(column, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(column.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(column.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void prepareFallCorridor(ServerLevel level, BlockPos start, BlockPosition target) {
        if (start.getY() != target.y()) {
            throw new IllegalArgumentException("flat support-loss fixture disagrees with its declared ground: "
                    + start + " -> " + target);
        }
        int minX = Math.min(start.getX(), target.x()) - 1, maxX = Math.max(start.getX(), target.x()) + 1;
        int minZ = Math.min(start.getZ(), target.z()) - 1, maxZ = Math.max(start.getZ(), target.z()) + 1;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            BlockPos column = new BlockPos(x, start.getY(), z);
            level.setBlock(column.below(3), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(column.below(2), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(column.below(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(column, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(column.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(column.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** A body may straddle two cells; breaking only its feet column leaves a real collision ledge. */
    private static List<BlockPos> supportingBlocks(Mob body) {
        AABB box = body.getBoundingBox(); int supportY = (int) Math.floor(body.getY() - .01D);
        List<BlockPos> positions = new java.util.ArrayList<>();
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX - 1.0E-8D); x++) {
            for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ - 1.0E-8D); z++) positions.add(new BlockPos(x, supportY, z));
        }
        return List.copyOf(positions);
    }

    private static void drive(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        var before = runtime.decodedState().orElseThrow();
        for (var actor : before.ambientLeases().keySet()) {
            var body = managed(level, runtime, actor);
            if (body != null && !FrontierV3ActorBodyController.readyForExecution(level, before,
                    List.of(ActorBodyAuthority.current(before, actor)))) {
                FrontierV3ServerLifecycle.observeSourceJoin(level, runtime, body);
            }
        }
        FrontierV3AmbientPendingAdmissions.reclaimProjected(runtime, runtime.decodedState().orElseThrow());
        FrontierV3AmbientActorExecutor.tick(level, runtime);
        runtime.decodedState().orElseThrow().ambientLeases().keySet().stream()
                .map(actor -> level.getEntity(FrontierV3AmbientActorExecutor.entityId(runtime.decodedState().orElseThrow(), actor)))
                .filter(Mob.class::isInstance).map(Mob.class::cast).forEach(FrontierV3MobMotionLifecycle::advanceAtEntityBoundary);
    }

    private static Mob managed(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId actor) {
        return Optional.ofNullable(level.getEntity(FrontierV3AmbientActorExecutor.entityId(runtime.decodedState().orElseThrow(), actor)))
                .filter(Mob.class::isInstance).map(Mob.class::cast).orElse(null);
    }

    private static Fixture fixture(BlockPos residentSupport, BlockPos bioformSupport) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ambient-executor-support-loss-"
                        + residentSupport.getX() + "-" + residentSupport.getZ()), 97L);
        FrontierWorldState source = base.initialState();
        SubjectId sourceResident = source.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId sourceSettlement = source.humanPopulation().resident(sourceResident).settlementId();
        ResourceSite sourceSite = FrontierResourceSitePlan.compile(source.bootstrap()).values().stream()
                .filter(site -> site.settlementId().equals(sourceSettlement)).reduce((left, right) -> {
                    throw new IllegalStateException("fixture farmer has ambiguous resource sites");
                }).orElseThrow();
        // Translate the complete immutable world from the same terminal station that the
        // farmer actually owns.  This keeps the live body, the post-harvest WORK target and
        // the prepared ordinary collision corridor in one coordinate frame.
        BlockPosition sourceFloor = sourceSite.cropSlots().getLast().offset(0, -1, 0);
        FrontierWorldState initial = FrontierWorldState.initial(translatedBootstrap(source.bootstrap(),
                residentSupport.getX() - sourceFloor.x(), residentSupport.getY() - sourceFloor.y(), residentSupport.getZ() - sourceFloor.z()));
        SubjectId resident = initial.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId bioform = initial.bootstrap().hive().bioforms().stream().filter(Bioform::isScout)
                .map(Bioform::id).sorted().findFirst().orElseThrow();
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, new ActorLocation(BodyPosition.above(new SurfaceAnchor(block(residentSupport))), actors.get(resident).condition(), actors.get(resident).kind()));
        actors.put(bioform, new ActorLocation(BodyPosition.above(new SurfaceAnchor(block(bioformSupport))), actors.get(bioform).condition(), actors.get(bioform).kind()));
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(initial.hiveColony().bioformLifecycles());
        BioformLifecycle original = lifecycles.get(bioform);
        lifecycles.put(bioform, original == null ? BioformLifecycle.activeWithoutHome() : BioformLifecycle.active(original.homeSlot().orElseThrow()));
        FrontierWorldState fixture = initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .hiveColony(initial.hiveColony().withBioformLifecycles(lifecycles)));
        ResourceSite translatedSite = FrontierResourceSitePlan.compile(fixture.bootstrap()).values().stream()
                .filter(site -> site.settlementId().equals(fixture.humanPopulation().resident(resident).settlementId()))
                .reduce((left, right) -> { throw new IllegalStateException("fixture farmer has ambiguous translated resource sites"); })
                .orElseThrow();
        return new Fixture(fixture, resident, bioform, ResourceSiteHarvestTraversal.workReturnSurface(fixture.bootstrap(), translatedSite).support());
    }

    private static BlockPosition block(BlockPos position) { return new BlockPosition(position.getX(), position.getY(), position.getZ()); }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(ServerLevel level, FrontierWorldState state) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(state.bootstrap()),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var store = new EphemeralStore();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, state, store.recover(state.bootstrap().worldId()),
                () -> ledger.persist(level, state.bootstrap().worldId()));
        return FrontierV3ServerRuntime.start(configuration, store, 10_000);
    }

    /** GameTest coordinates are far from origin; translate the complete authored topology, not just its bounds. */
    private static FrontierBootstrap translatedBootstrap(FrontierBootstrap source, int dx, int dy, int dz) {
        java.util.function.Function<BlockPosition, BlockPosition> translate = position -> new BlockPosition(position.x() + dx, position.y() + dy, position.z() + dz);
        List<Settlement> settlements = source.settlements().stream().map(settlement -> new Settlement(settlement.id(), settlement.displayName(),
                translate.apply(settlement.anchor()), settlement.residents().stream().map(resident -> new Resident(resident.id(), resident.settlementId(),
                resident.role(), translate.apply(resident.home()))).toList(), settlement.structures().stream().map(structure -> new SettlementStructure(
                structure.id(), structure.settlementId(), structure.kind(), translate.apply(structure.anchor()), structure.facing())).toList())).toList();
        List<HiveNest> nests = source.hive().seedNests().stream().map(nest -> new HiveNest(nest.id(), nest.hiveId(), translate.apply(nest.anchor()))).toList();
        List<HiveOrgan> organs = source.hive().organs().stream().map(organ -> new HiveOrgan(organ.id(), organ.hiveId(), organ.nestId(), organ.kind(),
                translate.apply(organ.anchor()), organ.containerId())).toList();
        List<Bioform> bioforms = source.hive().bioforms().stream().map(bioform -> new Bioform(bioform.id(), bioform.hiveId(), bioform.nestId(),
                bioform.chassis(), bioform.mutations(), bioform.assignment(), translate.apply(bioform.position()))).toList();
        Map<TerrainColumn, Integer> surveyed = new LinkedHashMap<>();
        source.terrain().surveyedSupportY().forEach((column, supportY) -> surveyed.put(new TerrainColumn(column.x() + dx, column.z() + dz), supportY + dy));
        return new FrontierBootstrap(source.worldId(), source.seed(), new WorldBounds(source.bounds().minX() + dx, source.bounds().minZ() + dz,
                source.bounds().width(), source.bounds().depth()), settlements, new Hive(source.hive().id(), nests, organs, bioforms), source.ruleset(),
                new TerrainSurfacePlan(source.terrain().baselineSupportY() + dy, surveyed));
    }

    private record Fixture(FrontierWorldState state, SubjectId resident, SubjectId bioform, BlockPosition residentWorkTarget) { }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, Revision coveredRevision) { throw new UnsupportedOperationException("GameTest does not compact"); }
    }
}
