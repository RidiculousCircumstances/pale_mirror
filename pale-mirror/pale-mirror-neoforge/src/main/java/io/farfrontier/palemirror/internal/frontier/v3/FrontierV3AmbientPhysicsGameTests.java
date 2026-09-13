package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.StateValidator;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.*;
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
import net.minecraft.world.level.block.Blocks;
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
                FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(resident);
                FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(bioform);
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
            helper.succeed();
        });
    }

    /**
     * Production admission/cadence proof for the support-loss defect.  Unlike the primitive
     * fixture above, this creates only canonical residents/bioforms and ordinary player demand:
     * the ambient executor owns PREPARED -> HOT admission, the retained UUID bodies, and every
     * subsequent ordinary-physics turn.
     */
    @GameTest(batch = "pm-frontier-v3-ambient-physics", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 300)
    public static void demandedManagedResidentAndBioformFallThroughExecutorCadence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos residentSupport = helper.absolutePos(new BlockPos(2, 10, 2));
        BlockPos bioformSupport = helper.absolutePos(new BlockPos(5, 10, 2));
        prepareFallColumn(level, residentSupport); prepareFallColumn(level, bioformSupport);
        Fixture fixture = fixture(residentSupport, bioformSupport);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(fixture.state());
        ServerPlayer observer = helper.makeMockServerPlayerInLevel();
        observer.setPos(residentSupport.getX() + 6.5D, residentSupport.getY() + 1.0D, residentSupport.getZ() + 6.5D);
        double[] initialY = { Double.NaN, Double.NaN };
        boolean[] exactHotBodies = { false }, movingBeforeLoss = { false }, idleBeforeLoss = { false };
        BodyPosition[] landed = new BodyPosition[2];

        for (int tick = 1; tick <= 285; tick++) {
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
                    if (turn < 24) movingBeforeLoss[0] |= FrontierV3ControlledMobMotion.trace(bioform).stream()
                            .anyMatch(sample -> sample.horizontalVelocity() > 0.0D);
                }
                if (turn == 24) {
                    helper.assertTrue(exactHotBodies[0], "ordinary player demand must admit the canonical resident and active bioform through their exact HOT leases");
                    // Freeze the real admitted resident at the normal retained-body authority
                    // boundary while the separately admitted bioform remains the observed moving
                    // case.  No fixture entity is substituted or manually repositioned.
                    FrontierV3ControlledMobMotion.stop(resident);
                    idleBeforeLoss[0] = resident.getDeltaMovement().horizontalDistanceSqr() == 0.0D;
                    helper.assertTrue(idleBeforeLoss[0] && movingBeforeLoss[0],
                            "support loss must cover both an idle managed resident and a moving managed bioform through the ordinary executor cadence");
                    // This is the ordinary player input boundary, not a fixture world edit:
                    // vanilla's server game mode performs both breaks while the executor owns
                    // the two retained HOT UUIDs.
                    helper.assertTrue(observer.gameMode.destroyBlock(residentSupport) && observer.gameMode.destroyBlock(bioformSupport),
                            "the ordinary observer must be able to remove each physical support");
                }
                if (turn == 65) {
                    helper.assertTrue(resident != null && bioform != null, "the exact HOT bodies must remain observable through their fall and landing");
                    landed[0] = FrontierV3AmbientActorExecutor.observedBody(resident);
                    landed[1] = FrontierV3AmbientActorExecutor.observedBody(bioform);
                    helper.assertTrue(!resident.isNoGravity() && !bioform.isNoGravity()
                                    && resident.getY() < initialY[0] - 2.0D && bioform.getY() < initialY[1] - 2.0D
                                    && Math.abs(resident.getY() - (residentSupport.getY() - 2.0D)) < 1.0E-6D
                                    && Math.abs(bioform.getY() - (bioformSupport.getY() - 2.0D)) < 1.0E-6D,
                            "ordinary collision must land the moving managed bodies without hover, reset, teleport, or a second route");
                    observer.connection.disconnect(Component.literal("ambient support-loss fixture complete"));
                }
            });
        }
        helper.runAtTickTime(286, () -> {
            FrontierWorldState state = runtime.decodedState().orElseThrow();
            helper.assertTrue(state.ambientLeases().get(fixture.resident()).status() == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED
                            && state.ambientLeases().get(fixture.bioform()).status() == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED,
                    "ordinary demand loss must type the existing HOT leases through one bounded release rather than retain a hovering authority");
            helper.assertTrue(state.actorLocations().get(fixture.resident()).body().equals(landed[0])
                            && state.actorLocations().get(fixture.bioform()).body().equals(landed[1]),
                    "the typed release must canonically retain the collision-observed landing bodies, never reset either actor to its removed support");
            helper.assertTrue(managed(level, runtime, fixture.resident()) == null && managed(level, runtime, fixture.bioform()) == null,
                    "the released exact bodies must be discarded once, leaving no second route or duplicate HOT body");
            runtime.shutdown(); helper.succeed();
        });
    }

    private static void prepareFallColumn(ServerLevel level, BlockPos support) {
        level.setBlock(support.below(3), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void drive(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        FrontierV3AmbientActorExecutor.tick(level, runtime);
        runtime.decodedState().orElseThrow().ambientLeases().keySet().stream()
                .map(actor -> level.getEntity(FrontierV3AmbientActorExecutor.entityId(runtime.decodedState().orElseThrow(), actor)))
                .filter(Mob.class::isInstance).map(Mob.class::cast).forEach(FrontierV3ServerLifecycle::advanceControlledMob);
    }

    private static Mob managed(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId actor) {
        return Optional.ofNullable(level.getEntity(FrontierV3AmbientActorExecutor.entityId(runtime.decodedState().orElseThrow(), actor)))
                .filter(Mob.class::isInstance).map(Mob.class::cast).orElse(null);
    }

    private static Fixture fixture(BlockPos residentSupport, BlockPos bioformSupport) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ambient-executor-support-loss"), 97L);
        FrontierWorldState source = base.initialState();
        SubjectId sourceResident = source.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        BlockPosition sourceFloor = source.actorLocations().get(sourceResident).supportingSurface().support();
        FrontierWorldState initial = FrontierWorldState.initial(translatedBootstrap(source.bootstrap(),
                residentSupport.getX() - sourceFloor.x(), residentSupport.getY() - sourceFloor.y(), residentSupport.getZ() - sourceFloor.z()));
        SubjectId resident = initial.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId bioform = initial.bootstrap().hive().bioforms().stream().map(form -> form.id()).sorted().findFirst().orElseThrow();
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, new ActorLocation(BodyPosition.above(new SurfaceAnchor(block(residentSupport))), actors.get(resident).condition()));
        actors.put(bioform, new ActorLocation(BodyPosition.above(new SurfaceAnchor(block(bioformSupport))), actors.get(bioform).condition()));
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(initial.hiveColony().bioformLifecycles());
        BioformLifecycle original = lifecycles.get(bioform);
        lifecycles.put(bioform, original == null ? BioformLifecycle.activeWithoutHome() : BioformLifecycle.active(original.homeSlot().orElseThrow()));
        return new Fixture(initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .hiveColony(initial.hiveColony().withBioformLifecycles(lifecycles))), resident, bioform);
    }

    private static BlockPosition block(BlockPos position) { return new BlockPosition(position.getX(), position.getY(), position.getZ()); }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(FrontierWorldState state) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), state.bootstrap().seed());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(state.bootstrap()),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
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

    private record Fixture(FrontierWorldState state, SubjectId resident, SubjectId bioform) { }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, Revision coveredRevision) { throw new UnsupportedOperationException("GameTest does not compact"); }
    }
}
