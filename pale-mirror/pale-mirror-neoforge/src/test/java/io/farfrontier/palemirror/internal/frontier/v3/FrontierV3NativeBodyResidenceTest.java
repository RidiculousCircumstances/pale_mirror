package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.FullChunkStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3NativeBodyResidenceTest {
    @Test void inaccessibleTerrainAllowsOnlyNativeUnloadRequest() {
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(false, FullChunkStatus.INACCESSIBLE));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(true, FullChunkStatus.INACCESSIBLE));
    }

    @Test void pendingOrLiveNativeHolderCannotBeReclassifiedFromObserverAbsence() {
        for (var status : FullChunkStatus.values()) if (status != FullChunkStatus.INACCESSIBLE)
            assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(false, status));
    }

    @Test void loadedNonTickingHaloCanUseTheSameNativeSaveUnloadProtocol() {
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(true, FullChunkStatus.FULL, false, false));
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(true, FullChunkStatus.BLOCK_TICKING, false, false));
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(true, FullChunkStatus.ENTITY_TICKING, false, false),
                "view-distance tickets do not override vanilla's independent simulation-distance range");
        assertTrue(FrontierV3NativeBodyResidence.needsNativeUnload(false, FullChunkStatus.INACCESSIBLE, false, false));
    }

    @Test void anObserverOrNativeTickingPreventsColumnEviction() {
        for (var status : FullChunkStatus.values()) {
            assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(true, status, false, true));
            assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(true, status, true, false));
        }
        assertFalse(FrontierV3NativeBodyResidence.needsNativeUnload(false, FullChunkStatus.FULL, false, false));
    }

    @Test void returnUsesRealTickingRangeWithoutWaitingForANominalHolderTransition() {
        assertTrue(FrontierV3NativeBodyResidence.needsNativeRestore(true, FullChunkStatus.ENTITY_TICKING, true));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeRestore(true, FullChunkStatus.ENTITY_TICKING, false));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeRestore(false, FullChunkStatus.ENTITY_TICKING, true));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeRestore(true, FullChunkStatus.BLOCK_TICKING, true));
    }

    @Test void vanillaLateArrivalNeedsASecondUnloadEvenThoughItLeftTheVisibleUuidIndex() {
        var hidden = new net.minecraft.world.level.ChunkPos(0, 0);
        var visible = new net.minecraft.world.level.ChunkPos(1, 0);
        var saved = new java.util.ArrayList<java.util.UUID>();
        var storage = new net.minecraft.world.level.entity.EntityPersistentStorage<Resident>() {
            public java.util.concurrent.CompletableFuture<net.minecraft.world.level.entity.ChunkEntities<Resident>> loadEntities(
                    net.minecraft.world.level.ChunkPos chunk) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        new net.minecraft.world.level.entity.ChunkEntities<>(chunk, java.util.List.of()));
            }
            public void storeEntities(net.minecraft.world.level.entity.ChunkEntities<Resident> chunk) {
                chunk.getEntities().forEach(body -> saved.add(body.getUUID()));
            }
            public void flush(boolean sync) { }
        };
        var callback = new net.minecraft.world.level.entity.LevelCallback<Resident>() {
            public void onCreated(Resident body) { }
            public void onDestroyed(Resident body) { }
            public void onTickingStart(Resident body) { }
            public void onTickingEnd(Resident body) { }
            public void onTrackingStart(Resident body) { }
            public void onTrackingEnd(Resident body) { }
            public void onSectionChange(Resident body) { }
        };
        var manager = new net.minecraft.world.level.entity.PersistentEntitySectionManager<>(Resident.class, callback, storage);
        manager.updateChunkStatus(hidden, FullChunkStatus.ENTITY_TICKING);
        manager.updateChunkStatus(visible, FullChunkStatus.ENTITY_TICKING);
        manager.tick();
        var first = new Resident(1, new net.minecraft.core.BlockPos(15, 64, 0));
        var following = new Resident(2, new net.minecraft.core.BlockPos(16, 64, 0));
        assertTrue(manager.addNewEntity(first)); assertTrue(manager.addNewEntity(following));
        manager.updateChunkStatus(hidden, FullChunkStatus.INACCESSIBLE);
        manager.tick();
        assertTrue(first.removed); assertTrue(saved.contains(first.getUUID()));
        following.move(new net.minecraft.core.BlockPos(15, 64, 0));
        manager.tick(); manager.tick();
        assertNull(manager.getEntityGetter().get(following.getUUID()));
        assertTrue(manager.isLoaded(following.getUUID()), "hidden is not physically removed");
        assertFalse(following.removed); assertFalse(saved.contains(following.getUUID()));
        assertTrue(FrontierV3NativeBodyResidence.needsNativeRedrain(true, false, false));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeRedrain(true, true, false));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeRedrain(true, false, true));
        assertFalse(FrontierV3NativeBodyResidence.needsNativeRedrain(false, false, false));
        manager.updateChunkStatus(hidden, FullChunkStatus.INACCESSIBLE);
        manager.tick(); manager.tick();
        assertTrue(saved.contains(following.getUUID())); assertTrue(following.removed);
        assertFalse(manager.isLoaded(following.getUUID()));
    }

    /** Isolated native entity-manager algorithm fixture, not live-world acceptance. */
    private static final class Resident implements net.minecraft.world.level.entity.EntityAccess {
        private final int id;
        private final java.util.UUID uuid = java.util.UUID.randomUUID();
        private net.minecraft.core.BlockPos feet;
        private net.minecraft.world.level.entity.EntityInLevelCallback callback = net.minecraft.world.level.entity.EntityInLevelCallback.NULL;
        private boolean removed;
        Resident(int id, net.minecraft.core.BlockPos feet) { this.id = id; this.feet = feet; }
        void move(net.minecraft.core.BlockPos position) { feet = position; callback.onMove(); }
        public int getId() { return id; }
        public java.util.UUID getUUID() { return uuid; }
        public net.minecraft.core.BlockPos blockPosition() { return feet; }
        public net.minecraft.world.phys.AABB getBoundingBox() { return new net.minecraft.world.phys.AABB(feet); }
        public void setLevelCallback(net.minecraft.world.level.entity.EntityInLevelCallback value) { callback = value; }
        public java.util.stream.Stream<Resident> getSelfAndPassengers() { return java.util.stream.Stream.of(this); }
        public java.util.stream.Stream<Resident> getPassengersAndSelf() { return java.util.stream.Stream.of(this); }
        public void setRemoved(net.minecraft.world.entity.Entity.RemovalReason reason) { removed = true; callback.onRemove(reason); }
        public boolean shouldBeSaved() { return true; }
        public boolean isAlwaysTicking() { return false; }
    }
}
