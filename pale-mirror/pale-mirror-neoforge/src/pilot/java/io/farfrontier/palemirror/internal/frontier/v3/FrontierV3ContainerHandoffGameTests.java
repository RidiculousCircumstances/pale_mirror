package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Regressions for the live R223 provider mismatch and asynchronous handoff loss. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ContainerHandoffGameTests {
    private FrontierV3ContainerHandoffGameTests() { }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void confirmedProjectionSurvivesDelayedCommitWithoutAnotherPhysicalVisit(GameTestHelper helper) throws Exception {
        try (var fixture = fixture(helper, "delayed-confirmation", false)) {
            var runtime = fixture.runtime();
            var depot = fixture.depot();
            var preparation = FrontierV3CommandSubmission.submit(runtime, "test-preparation", depot.value(),
                    new io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReferenceProjectionPrepared(depot, 7011L, 0L, "", ""));
            helper.assertTrue(!(preparation instanceof CommandResult.Rejected), "preparation rejected: " + preparation);
            fixture.flush(); runtime.runPersistenceTurn(() -> { });
            var prepared = runtime.decodedState().orElseThrow();
            var lease = prepared.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot));
            var chest = FrontierV3ReferenceContainerCustodyGameTests.chest(helper, fixture.position(), depot);
            FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, prepared, depot);
            fixture.delayed.set(true);
            runtime.runPersistenceTurn(() -> helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor
                    .reconcilePreparedProjection(helper.getLevel(), runtime, prepared, lease, chest), "confirmation awaits the exact disk receipt"));
            helper.assertTrue(runtime.awaitingCanonicalDurability(), "the regression must exercise Pending, not synchronous acceptance");
            fixture.flush();
            runtime.runPersistenceTurn(() -> { });
            var confirmed = runtime.decodedState().orElseThrow();
            var acquired = confirmed.replicaCustody().custodyByScope().get(lease.scopeId());
            helper.assertTrue(acquired.status() == PhysicalCustodyLeaseStatus.ACQUIRED, "confirmed custody published");
            var account = FungibleResourceCustodySupport.accountAtContainer(confirmed, depot).orElseThrow();
            FrontierV3FungibleResourceObservationExecutor.observe(helper.getLevel(), runtime, confirmed, account, chest,
                    acquired.authorityEpoch());
            fixture.flush(); runtime.runPersistenceTurn(() -> { });
            confirmed = runtime.decodedState().orElseThrow();
            helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.retainsUnobservedRestartFungibleHot(
                    helper.getLevel(), confirmed, acquired), "the actual confirmation callback remembers the witness without discovery/revisit");
            helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.retainsUnobservedRestartFungibleHot(
                    Map.of(), confirmed.inventory().fungibleResources(), acquired), "an actually unseen restart still retains the same HOT bindings");
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void doubleTrappedChestRemainsItsDeclaredProviderAndMissingProviderWaitsForDisk(GameTestHelper helper) throws Exception {
        try (var fixture = fixture(helper, "trapped-provider", true)) {
            var runtime = fixture.runtime(); var depot = fixture.depot(); var level = helper.getLevel();
            var pos = fixture.position();
            level.setBlock(pos, Blocks.TRAPPED_CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
            level.setBlock(pos.east(), Blocks.TRAPPED_CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
            var chest = (ChestBlockEntity) level.getBlockEntity(pos);
            var state = runtime.decodedState().orElseThrow();
            chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, depot.value());
            chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY,
                    ReferenceContainerCustody.provenance(depot));
            FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, state, depot);
            helper.assertTrue(FrontierV3PhysicalContainer.loaded(level, state, depot).isPresent(), "trapped half uses the shared chest provider");
            helper.assertTrue(FrontierV3FixedContainerProviders.acceptsBlock(state.inventory().containers().get(depot),
                    level.getBlockState(pos.east())), "the other double-chest half has the same provider contract");
            helper.assertTrue(!FrontierV3FixedContainerProviders.acceptsBlock(state.inventory().containers().get(depot),
                    Blocks.BARREL.defaultBlockState()), "unsupported providers cannot borrow chest authority");
            FrontierV3ReferenceContainerCustodyExecutor.reconcile(level, runtime, state, state.inventory().surfaces().get(depot));
            fixture.flush(); runtime.runPersistenceTurn(() -> { });
            state = runtime.decodedState().orElseThrow();
            helper.assertTrue(state.replicaCustody().replicas().containsKey(depot), "declaration must publish: " + runtime.status());
            helper.assertTrue(state.inventory().surfaces().get(depot).status() == ContainerSurfaceStatus.ACTIVE, "no false provider loss");
            // A true missing-provider observation must isolate locally even when the
            // required status transition completes asynchronously.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            var missing = state;
            FrontierV3ReferenceContainerCustodyExecutor.reconcile(level, runtime, missing, missing.inventory().surfaces().get(depot));
            helper.runAfterDelay(3, () -> {
                fixture.delayed.set(true);
                runtime.runPersistenceTurn(() -> FrontierV3ReferenceContainerCustodyExecutor.reconcile(level, runtime,
                        missing, missing.inventory().surfaces().get(depot)));
                helper.assertTrue(runtime.awaitingCanonicalDurability(), "missing surface transition is pending, not failed: "
                        + runtime.status() + " replica=" + missing.replicaCustody().replicas().get(depot)
                        + " fence=" + FrontierV3ContainerEffectFence.pending(level, missing, depot));
                fixture.flush(); runtime.runPersistenceTurn(() -> { });
                helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE
                        && runtime.decodedState().orElseThrow().inventory().surfaces().get(depot).status() == ContainerSurfaceStatus.CONFLICT,
                        "only the missing container is isolated; the world remains active");
                fixture.close(); helper.succeed();
            });
            fixture.retained = true;
        }
    }

    private static Fixture fixture(GameTestHelper helper, String name, boolean activated) throws Exception {
        var world = new WorldId("frontier:r223-" + name);
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        // Existing projection tests share this ServerLevel. Keep container identities
        // distinct too, so their observation/debounce maps cannot alter this fixture.
        var settlements = initial.bootstrap().settlements();
        var depot = FrontierWorldState.depotId(settlements.get(settlements.size() - (activated ? 1 : 2)).id());
        var pos = helper.absolutePos(new BlockPos(2, 2, 2)); var original = initial.inventory().surfaces().get(depot).position();
        var translated = FrontierWorldState.initial(
                FrontierV3BootstrapGameTestFixtures.translatedBootstrap(initial.bootstrap(), pos.getX() - original.x(),
                        pos.getY() - original.y(), pos.getZ() - original.z()));
        var active = activated ? FrontierV3ReferenceContainerCustodyGameTests.activated(translated, depot) : translated;
        var state = active.withInventory(active.inventory().withFungibleResources(translated.inventory().fungibleResources()));
        if (activated) {
            var container = state.inventory().containers().get(depot);
            var surface = translated.inventory().surfaces().get(depot);
            var recovery = FencedRecoveryContainerSupport.transition(state.fencedRecovery(), container, surface,
                    ContainerSurfaceStatus.PREPARED);
            recovery = FencedRecoveryContainerSupport.transition(recovery, container,
                    surface.transitionTo(ContainerSurfaceStatus.PREPARED), ContainerSurfaceStatus.ACTIVE);
            state = state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(recovery));
        }
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var config = new FrontierEngineConfiguration<>(world, state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), new FrontierWorldStateCodec(state.bootstrap()), base.projectionMapper(), base.limits(), List.of(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var tasks = new ArrayDeque<Runnable>();
        var delayed = new java.util.concurrent.atomic.AtomicBoolean();
        var disk = new FrontierFileStore(java.nio.file.Files.createTempDirectory("pm-r223-"), FrontierWorldRuntimeDefinition.payloadCodecs(),
                action -> { if (delayed.get()) tasks.add(action); else action.run(); });
        var delegate = new FrontierStoreTransactionCommitter(disk);
        TransactionCommitter committer = new TransactionCommitter() {
            public void commit(TransactionRecord record, Durability ignored) { delegate.commit(record, Durability.DURABLE_BEFORE_EFFECT); }
            public Confirmation prepare(TransactionRecord record, Durability ignored) { return delegate.prepare(record, Durability.DURABLE_BEFORE_EFFECT); }
        };
        return new Fixture(FrontierV3ServerRuntime.startRecovered(config, disk, disk.recover(world), 10_000,
                DiagnosticRuntimeIdentity.unavailable(), committer), disk, tasks, delayed, depot, pos);
    }

    private static final class Fixture implements AutoCloseable {
        private final FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime;
        private final FrontierFileStore disk; private final ArrayDeque<Runnable> tasks;
        private final java.util.concurrent.atomic.AtomicBoolean delayed;
        private final SubjectId depot; private final BlockPos position; private boolean retained;
        Fixture(FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime, FrontierFileStore disk,
                ArrayDeque<Runnable> tasks, java.util.concurrent.atomic.AtomicBoolean delayed, SubjectId depot, BlockPos position) {
            this.runtime = runtime; this.disk = disk; this.tasks = tasks; this.delayed = delayed; this.depot = depot; this.position = position;
        }
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime() { return runtime; }
        SubjectId depot() { return depot; } BlockPos position() { return position; }
        void flush() { delayed.set(false); while (!tasks.isEmpty()) tasks.remove().run(); }
        public void close() { if (retained) { retained = false; return; } flush(); runtime.runPersistenceTurn(() -> { }); runtime.shutdown(); disk.close(); }
    }
}
