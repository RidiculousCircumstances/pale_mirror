package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.*;

/** Actual level-owned composition and flush; no world/canonical mutation or test player. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SharedStorageGameTests {
    private static final class SaveProbe extends net.minecraft.world.level.saveddata.SavedData implements FrontierV3NativeAutosave.Target {
        int submitted, forced;
        @Override public void submitAutosave(java.io.File file, net.minecraft.core.HolderLookup.Provider registries) { submitted++; }
        @Override public void save(java.io.File file, net.minecraft.core.HolderLookup.Provider registries) { forced++; }
        @Override public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) { return tag; }
    }
    @GameTest(batch="pm-frontier-v3-shared-storage",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=40)
    public static void implicitAutosaveCannotReplaceAnExplicitDurableSave(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var root = java.nio.file.Files.createTempDirectory("native-autosave-port-");
        var storage = new net.minecraft.world.level.storage.DimensionDataStorage(root.toFile(), level.getServer().getFixerUpper(), level.registryAccess());
        var probe = new SaveProbe(); storage.set("probe", probe);
        FrontierV3NativeAutosave.automatic(() -> { storage.save(); return true; });
        helper.assertTrue(probe.submitted == 1 && probe.forced == 0, "actual native storage must enqueue its implicit journal save");
        storage.save();
        helper.assertTrue(probe.forced == 1, "an explicit native save retains the synchronous owner boundary");
        try { FrontierV3NativeAutosave.automatic(() -> { throw new IllegalStateException("failed native save"); }); }
        catch (IllegalStateException expected) { }
        helper.assertTrue(!FrontierV3NativeAutosave.active(), "failed native saves cannot leak implicit permission");
        helper.succeed();
    }
    @GameTest(batch="pm-frontier-v3-shared-storage",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=40)
    public static void scheduledNativeSuffixRetainsItsOriginalOrderAndPersistentChunkOwner(GameTestHelper helper) {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var allowance = new FrontierV3EnvironmentalAdmission(clock::get); allowance.begin(1);
        var ticks = new net.minecraft.world.ticks.LevelTicks<String>(chunk -> true,
                () -> net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        var chunk = new net.minecraft.world.ticks.LevelChunkTicks<String>();
        ticks.addContainer(new net.minecraft.world.level.ChunkPos(0, 0), chunk);
        var first = new net.minecraft.world.ticks.ScheduledTick<>("first", new net.minecraft.core.BlockPos(1, 63, 1),
                0L, net.minecraft.world.ticks.TickPriority.HIGH, 3L);
        var second = new net.minecraft.world.ticks.ScheduledTick<>("second", new net.minecraft.core.BlockPos(2, 63, 1),
                0L, net.minecraft.world.ticks.TickPriority.NORMAL, 4L);
        var future = new net.minecraft.world.ticks.ScheduledTick<>("future", new net.minecraft.core.BlockPos(3, 63, 1),
                100L, net.minecraft.world.ticks.TickPriority.NORMAL, 5L);
        ticks.schedule(first); ticks.schedule(second); ticks.schedule(future);
        var executed = new ArrayList<String>();
        java.util.function.BiConsumer<net.minecraft.core.BlockPos, String> callback = (position, type) -> {
            executed.add(type); clock.addAndGet(2);
            if (type.equals("first")) ticks.schedule(new net.minecraft.world.ticks.ScheduledTick<>("second", second.pos(),
                    50L, net.minecraft.world.ticks.TickPriority.NORMAL, 6L));
        };
        FrontierV3ScheduledNativeTicks.within(allowance::scheduledAvailable, allowance::scheduled,
                () -> ticks.tick(1L, 256, callback));
        helper.assertTrue(executed.equals(List.of("first")), "one admitted callback finishes, suffix must not execute");
        helper.assertTrue(chunk.getAll().toList().containsAll(List.of(second, future)) && chunk.count() == 2,
                "deferred exact record including its deadline/priority/order belongs to vanilla's saved chunk queue");
        helper.assertTrue(!FrontierV3ScheduledNativeTicks.active(), "scheduler allowance cannot escape its native batch");
        allowance.begin(1);
        FrontierV3ScheduledNativeTicks.within(allowance::scheduledAvailable, allowance::scheduled,
                () -> ticks.tick(1L, 256, callback));
        helper.assertTrue(executed.equals(List.of("first", "second")), "original due tick must not be postponed by its later duplicate");
        ticks.tick(100L, 256, callback);
        helper.assertTrue(executed.equals(List.of("first", "second", "future")), "ordinary native scheduling remains intact");
        helper.succeed();
    }
    private record Entry(FrontierV3JournaledSavedData ledger, Function<CompoundTag,FrontierV3JournaledSavedData> decode) { }
    @GameTest(batch="pm-frontier-v3-shared-storage",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=40)
    public static void everyPhysicalStoreUsesCompleteRecoveryAndSharedFlush(GameTestHelper helper) {
        var level=helper.getLevel();
        var entries=List.of(
            new Entry(FrontierV3ResourceSiteLedger.get(level),tag -> FrontierV3ResourceSiteLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3GrayboxLedger.get(level),tag -> FrontierV3GrayboxLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3DepotClickLedger.get(level),tag -> FrontierV3DepotClickLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3ObjectBoardLedger.get(level),tag -> FrontierV3ObjectBoardLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3HopperCarrierLedger.get(level),tag -> FrontierV3HopperCarrierLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3PhysicalObservationLedger.get(level),tag -> FrontierV3PhysicalObservationLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3ManagedExplosionLedger.get(level),tag -> FrontierV3ManagedExplosionLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3InfectionOverlayLedger.get(level),tag -> FrontierV3InfectionOverlayLedger.load(tag,level.registryAccess())));
        FrontierV3NativeAutosave.automatic(() -> { level.getDataStorage().save(); return true; });
        FrontierV3PhysicalStores.flush(level);
        for(var entry:entries) {
            var ledger=entry.ledger; var kind=ledger.storeKind(); var empty=ledger.metadata();
            for(var table:kind.tables) empty.put(table.name(),new ListTag());
            var recovered=FrontierV3JournaledSavedData.readFile(FrontierV3JournaledSavedData.storageFile(level,kind),
                    FrontierV3PhysicalWorld.WORLD_ID.value(),level.dimension().location().toString(),
                    () -> entry.decode.apply(empty),(tag,registries) -> entry.decode.apply(tag),level.registryAccess());
            helper.assertValueEqual(recovered.save(new CompoundTag(),level.registryAccess()),
                    ledger.save(new CompoundTag(),level.registryAccess()),"shared flush must persist each real adapter including its tail: "+kind);
            helper.assertTrue(!ledger.isDirty(),"a durable flush clears dirty state only after receipt: "+kind);
        }
        helper.assertTrue(FrontierV3PhysicalStores.diagnostic(level).contains("\"kind\":\"FIELDS\""),
                "common diagnostics include the non-actor stores");
        helper.succeed();
    }
}
