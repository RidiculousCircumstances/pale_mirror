package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLeaseStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierReleased;
import io.farfrontier.palemirror.frontier.v3.model.ContractStatus;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3CargoCarrierGameTests.*;

/** Cargo-retirement and save-footprint GameTests retain their original batches. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3CargoRetirementGameTests {
    private FrontierV3CargoRetirementGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void successfulSaveWithoutBirthHistoryCannotAcknowledgeRetainedCart(GameTestHelper helper) {
        var level = helper.getLevel(); var runtime = runtime("frontier:missing-birth-ack");
        var initial = state(runtime);
        var lease = FrontierV3GameTestSceneLeases.exact(initial, runtime.checkpointImage().orElseThrow(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:missing-birth-ack"));
        FrontierV3CommandSubmission.submit(runtime, "missing-birth-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        var position = helper.absolutePos(new BlockPos(1, 2, 1)); prepareSupport(level, position.below());
        var cart = addOwnedCarrier(helper, level, state(runtime), lease, position);
        helper.assertTrue(FrontierV3CargoFootprintObserver.prepareBirth(level, lease,
                FrontierV3CargoCarrierAuthority.currentEpoch(state(runtime).fencedRecovery(), lease).orElseThrow(), cart), "birth is initially durable");
        FrontierV3CommandSubmission.submit(runtime, "missing-birth-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(2L, () -> {
            try {
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.RELEASED, "ordinary player handoff");
                FrontierV3CommandSubmission.submit(runtime, "missing-birth-close", lease.id().value(), new SceneLeaseReleased(lease.id(),
                        lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(), lease.memberPosition(member.actorId()))).toList()));
                var closed = state(runtime); var retirement = closed.fencedRecovery().cargoRetirements().pending().get(cart.getUUID());
                FrontierV3CargoCarrierExecutor.cleanRetired(level, closed, retirement);
                var entity = new net.minecraft.nbt.CompoundTag(); helper.assertTrue(cart.save(entity), "serialize real retained cart");
                helper.assertTrue(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(entity), "cart is clean");
                var chunk = cart.chunkPosition(); var entities = new net.minecraft.nbt.ListTag(); entities.add(entity);
                var stored = new net.minecraft.nbt.CompoundTag(); stored.put("Entities", entities);
                stored.putIntArray("Position", new int[]{chunk.x, chunk.z});
                // Inject missing historical evidence, not a domain outcome. A successful
                // current-column save cannot establish anything about unknown old columns.
                var archive = FrontierV3CargoFootprintArchive.at(level, closed.bootstrap().worldId());
                for (var footprint : archive.inventory()) archive.forgetExact(footprint);
                FrontierV3CargoCleanupPersistence.observeWrite(level, runtime, chunk, stored, java.util.concurrent.CompletableFuture.completedFuture(null));
                FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, true, () -> java.util.concurrent.CompletableFuture.completedFuture(null));
                helper.runAfterDelay(1L, () -> {
                    try {
                        helper.assertValueEqual(state(runtime).fencedRecovery().cargoRetirements().pending().get(cart.getUUID()),
                                retirement, "missing history retains the exact terminal obligation despite successful save");
                        helper.assertValueEqual(state(runtime).inventory(), closed.inventory(), "no inventory reconstruction or transfer");
                        helper.assertFalse(cart.isRemoved(), "player cart is preserved");
                        helper.succeed();
                    } finally { cart.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); }
                });
            } catch (java.io.IOException | RuntimeException failure) {
                cart.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown();
                throw new IllegalStateException(failure);
            }
        });
    }

    static void footprintAcknowledgement(GameTestHelper helper, boolean destroy, boolean restore) {
        footprintAcknowledgement(helper, destroy, restore, false);
    }

    static void footprintAcknowledgement(GameTestHelper helper, boolean destroy, boolean restore, boolean retryRemoval) {
        // Registered canonical commands + real entity/archive, with controlled provider IO
        // futures. This tests composition/failed-write retry, NOT a real region-file crash.
        var level = helper.getLevel(); var runtime = runtime("frontier:footprint-ack-" + destroy + "-" + restore + "-" + retryRemoval);
        var initial = state(runtime);
        var lease = FrontierV3GameTestSceneLeases.exact(initial, runtime.checkpointImage().orElseThrow(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:footprint-ack-" + destroy + "-" + restore + "-" + retryRemoval));
        FrontierV3CommandSubmission.submit(runtime, "footprint-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        var position = helper.absolutePos(new BlockPos(1, 2, 1)); prepareSupport(level, position.below());
        var original = addOwnedCarrier(helper, level, state(runtime), lease, position);
        var carrier = new java.util.concurrent.atomic.AtomicReference<MinecartChest>(original);
        helper.assertTrue(FrontierV3CargoFootprintObserver.prepareBirth(level, lease,
                FrontierV3CargoCarrierAuthority.currentEpoch(state(runtime).fencedRecovery(), lease).orElseThrow(), original), "birth must be durable");
        FrontierV3CommandSubmission.submit(runtime, "footprint-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        Runnable exercise = () -> {
            var cart = carrier.get();
            try {
                if (!restore) {
                    FrontierV3CommandSubmission.submit(runtime, "footprint-staged-drain", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
                    helper.assertTrue(FrontierV3CargoDepartureObserver.prepareRelease(level, state(runtime), state(runtime).sceneLeases().get(lease.id())),
                            "prepare original REMOVE witness before intervening player handoff");
                    // Component input: closure did not commit, then recovery returns this
                    // same attempt to HOT. Player interaction itself only admits HOT custody.
                    FrontierV3CommandSubmission.submit(runtime, "footprint-staged-unknown", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
                    FrontierV3CommandSubmission.submit(runtime, "footprint-staged-return", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
                }
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        restore ? FrontierV3ServerLifecycle.CargoCarrierInteraction.NOT_MANAGED
                                : FrontierV3ServerLifecycle.CargoCarrierInteraction.RELEASED, "handoff commits exactly once");
                FrontierV3CommandSubmission.submit(runtime, "footprint-close", lease.id().value(), new SceneLeaseReleased(lease.id(),
                        lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(), lease.memberPosition(member.actorId()))).toList()));
                var closed = state(runtime); var retirement = closed.fencedRecovery().cargoRetirements().pending().get(cart.getUUID());
                helper.assertTrue(retirement != null, "closed scene retains exact cleanup obligation");
                helper.assertTrue(FrontierV3SceneDiagnosticJson.render(FrontierSceneBehaviors.logistics(lease).operationId().value(), runtime.checkpointImage().orElseThrow(),
                        closed, Optional.empty()).contains("\"cargoCleanupPending\":true"), "diagnostic exposes pending cleanup, not just CLOSED history");
                var inventory = closed.inventory();
                var history = new java.util.LinkedHashMap<>(closed.sceneLeases()); history.remove(lease.id());
                var withoutHistory = closed.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(history));
                FrontierV3CargoCarrierExecutor.cleanRetired(level, withoutHistory, retirement);
                helper.assertFalse(cart.getPersistentData().contains(FrontierV3CargoFootprintObserver.KEY), "terminal owner strips cleanup identity before clean save");
                var chunk = cart.chunkPosition();
                net.minecraft.nbt.CompoundTag stored;
                if (destroy) {
                    var removalArchive = FrontierV3CargoFootprintArchive.at(level, closed.bootstrap().worldId());
                    var exactHistory = removalArchive.read(cart.getUUID(), retirement.authorization().retiredEpoch()).orElseThrow();
                    if (retryRemoval) removalArchive.forgetExact(exactHistory);
                    cart.discard();
                    FrontierV3CargoFootprintObserver.observeRemoval(level, closed.bootstrap().worldId(), cart, retirement);
                    if (retryRemoval) {
                        boolean fenced = false;
                        try { FrontierV3CargoFootprintObserver.beforeWrite(level, chunk, null); }
                        catch (java.io.IOException expected) { fenced = true; }
                        helper.assertTrue(fenced, "missing removal evidence blocks even an empty entity-column write");
                        removalArchive.retain(exactHistory); // Restore original fixture bytes, never inferred history.
                        FrontierV3CargoFootprintObserver.beforeWrite(level, chunk, null);
                        helper.assertTrue(removalArchive.read(cart.getUUID(), retirement.authorization().retiredEpoch()).orElseThrow().removalChunk().isPresent(),
                                "retained event retries before the deletion write is permitted");
                    }
                    stored = null;
                } else {
                    var entity = new net.minecraft.nbt.CompoundTag(); helper.assertTrue(cart.save(entity), "serialize retained cart");
                    helper.assertTrue(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(entity), "clean entity has no obsolete metadata");
                    var entities = new net.minecraft.nbt.ListTag(); entities.add(entity);
                    stored = new net.minecraft.nbt.CompoundTag(); stored.put("Entities", entities); stored.putIntArray("Position", new int[]{chunk.x, chunk.z});
                }
                var failed = new java.util.concurrent.CompletableFuture<Void>();
                FrontierV3CargoCleanupPersistence.observeWrite(level, runtime, chunk, stored, failed);
                FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, true, () -> java.util.concurrent.CompletableFuture.completedFuture(null));
                helper.assertTrue(state(runtime).fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID()), "pending write cannot acknowledge cleanup");
                failed.completeExceptionally(new IllegalStateException("injected entity write failure"));
                helper.assertTrue(state(runtime).fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID()), "failed write cannot acknowledge cleanup");
                FrontierV3CargoCleanupPersistence.observeWrite(level, runtime, chunk, stored, java.util.concurrent.CompletableFuture.completedFuture(null));
                FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, true, () -> java.util.concurrent.CompletableFuture.completedFuture(null));
                helper.runAfterDelay(1L, () -> {
                    try {
                        helper.assertFalse(state(runtime).fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID()), "successful exact retry acknowledges cleanup");
                        helper.assertTrue(FrontierV3SceneDiagnosticJson.render(FrontierSceneBehaviors.logistics(lease).operationId().value(), runtime.checkpointImage().orElseThrow(),
                                state(runtime), Optional.empty()).contains("\"cargoCleanupPending\":false"), "diagnostic reflects acknowledged cleanup");
                        helper.assertValueEqual(state(runtime).inventory(), inventory, "cleanup does not invent item transfers or losses");
                        var archive = FrontierV3CargoFootprintArchive.at(level, closed.bootstrap().worldId());
                        helper.assertTrue(archive.inventory().isEmpty() && archive.savedInventory().isEmpty(), "acknowledged metadata is compacted");
                        // A second ordinary pass compacts the queued, now obsolete pre-handoff witness.
                        // Parallel GameTests share a ServerLevel but not a canonical runtime:
                        // announce this pass's provider write so its exact runtime index is current.
                        FrontierV3CargoCleanupPersistence.observeWrite(level, runtime, chunk, stored, java.util.concurrent.CompletableFuture.completedFuture(null));
                        FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, true, () -> java.util.concurrent.CompletableFuture.completedFuture(null));
                        var cleanup = FrontierV3CargoCleanupArchive.at(level, closed.bootstrap().worldId());
                        helper.assertTrue(cleanup.observation(cart.getUUID()).isEmpty() && cleanup.savedWitnesses().isEmpty(),
                                "RETAIN acknowledgement also compacts rejected REMOVE preparation without touching inventory");
                        helper.succeed();
                    } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                    finally { if (!cart.isRemoved()) cart.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); }
                });
            } catch (java.io.IOException | RuntimeException failure) {
                if (!cart.isRemoved()) cart.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown();
                throw new IllegalStateException(failure);
            }
        };
        if (!restore) helper.runAfterDelay(2L, exercise);
        else helper.runAfterDelay(2L, () -> {
            try {
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, original, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.RELEASED, "handoff precedes injected unsaved removal");
                var saved = new net.minecraft.nbt.CompoundTag(); helper.assertTrue(original.save(saved), "retain actual pre-removal entity snapshot");
                original.discard();
                FrontierV3CargoFootprintObserver.observeRemoval(level, initial.bootstrap().worldId(), original, null);
                helper.runAfterDelay(1L, () -> {
                    try {
                        var returned = (MinecartChest) EntityType.loadEntityRecursive(saved, level, entity -> entity);
                        helper.assertTrue(returned != null && level.addFreshEntity(returned), "restore actual prior entity NBT, not inventory reconstruction");
                        carrier.set(returned); exercise.run();
                    } catch (RuntimeException failure) {
                        carrier.get().discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); throw failure;
                    }
                });
            } catch (RuntimeException failure) {
                original.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); throw failure;
            }
        });
    }
}
