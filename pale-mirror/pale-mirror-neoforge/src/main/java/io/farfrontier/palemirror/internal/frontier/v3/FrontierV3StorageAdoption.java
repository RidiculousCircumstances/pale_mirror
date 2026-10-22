package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.*;

/** Bounded naturally-loaded discovery. Admission is durable before tagging, never a slot writer. */
final class FrontierV3StorageAdoption {
    static final String EXCLUDED_KEY = "pale_mirror:storage_excluded";
    private static final int CHUNKS_PER_TURN = 8;
    private static final Map<ServerLevel, Discovery> WINDOWS = new WeakHashMap<>();
    private record Chunk(int x, int z) { }
    private static final class Discovery {
        List<SettlementStorageTerritory.Area> areas = List.of(); List<Chunk> chunks = List.of(); int cursor;
        void refresh(FrontierWorldState state) {
            var next = SettlementStorageTerritory.areas(state);
            if (areas.equals(next)) return;
            var targets = new LinkedHashSet<Chunk>();
            for (var area : next)
                for (int x = area.minX() >> 4; x <= area.maxX() >> 4; x++)
                    for (int z = area.minZ() >> 4; z <= area.maxZ() >> 4; z++) targets.add(new Chunk(x, z));
            chunks = List.copyOf(targets); cursor = 0; areas = next;
        }
    }
    private FrontierV3StorageAdoption() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || !FrontierV3PhysicalWorld.isPhysical(level)) return;
        for (var surface : state.inventory().surfaces().values().stream()
                .filter(s -> s.status() == ContainerSurfaceStatus.ADOPTING).sorted(Comparator.comparing(ContainerSurface::containerId)).toList()) {
            if (state.inventory().containers().get(surface.containerId()).purpose() != ContainerPurpose.RESOURCE_STORAGE) continue;
            var p = surface.position(); var pos = new BlockPos(p.x(), p.y(), p.z());
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
            finish(level, runtime, state, surface); return;
        }
        var window = WINDOWS.computeIfAbsent(level, ignored -> new Discovery()); window.refresh(state);
        for (int i = 0; i < Math.min(CHUNKS_PER_TURN, window.chunks.size()); i++) {
            var target = window.chunks.get(window.cursor);
            window.cursor = (window.cursor + 1) % window.chunks.size();
            var chunk = level.getChunkSource().getChunkNow(target.x(), target.z());
            if (chunk == null) continue;
            for (var block : chunk.getBlockEntities().values().stream().filter(ChestBlockEntity.class::isInstance)
                    .map(ChestBlockEntity.class::cast).sorted(Comparator.comparingLong(chest -> chest.getBlockPos().asLong())).toList()) {
                if (admit(level, runtime, state, block)) return;
            }
        }
    }
    private static boolean admit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 FrontierWorldState state, ChestBlockEntity chest) {
        var data = chest.getPersistentData(); var p = position(chest.getBlockPos());
        if (data.getBoolean(EXCLUDED_KEY) || !data.getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY).isBlank()
                || !data.getString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY).isBlank()
                || state.inventory().surfaces().values().stream().filter(ContainerSurface::fixed)
                    .filter(s -> s.status() != ContainerSurfaceStatus.RETIRED).anyMatch(s -> s.position().equals(p))) return false;
        var home = SettlementStorageTerritory.owner(state, p).orElse(null);
        if (home == null || !plain(chest)) return false;
        var station = station(level, state, chest.getBlockPos());
        if (station == null) return false; // Storage exists; interaction waits for an admissible shared-geometry port.
        // A readable world-local identity stamped by this producer; nobody dispatches by its spelling.
        var firstId = new SubjectId("container:observed-" + p.x() + "_" + p.y() + "_" + p.z());
        var id = state.inventory().containers().containsKey(firstId)
                ? new SubjectId("container:observed-" + UUID.randomUUID()) : firstId;
        Optional<SubjectId> facility = home.structures().stream().filter(structure ->
                SettlementStructureFootprint.supportSurfaces(structure).contains(station)).map(SettlementStructure::id).findFirst();
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, station, facility, station);
        var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE);
        var container = new ContainerRecord(id, home.id(), chest.getContainerSize(), ContainerPurpose.RESOURCE_STORAGE,
                Optional.empty(), Optional.of(policy));
        if (!FrontierV3FixedContainerProviders.acceptsBlock(container, chest.getBlockState())) return false;
        var lots = new ArrayList<ResourceLot>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            var item = chest.getItem(slot); if (item.isEmpty()) continue;
            var kind = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            // Old managed player custody without a causal return cannot be duplicated as unknown stock.
            if (state.inventory().fungibleResources().accounts().values().stream()
                    .filter(account -> account.custody() instanceof ResourceCustody.Player)
                    .anyMatch(account -> account.lotQuantities().keySet().stream().anyMatch(lot ->
                        state.inventory().fungibleResources().lots().get(lot).itemKind().equals(kind)))) return false;
            lots.add(new ResourceLot(new SubjectId("lot:adopt-" + UUID.randomUUID()), home.id(), kind,
                    item.getCount(), "observed-territorial-storage", List.of()));
        }
        var event = new ResourceStorageAdopted(new ResourceStorageDeclared(container, p), lots);
        var result = FrontierV3CommandSubmission.submit(runtime, "storage-adopt", id.value(), event);
        if (!(result instanceof CommandResult.Accepted)) return false;
        var admitted = runtime.decodedState().orElseThrow(); finish(level, runtime, admitted, admitted.inventory().surfaces().get(id));
        PaleMirrorMod.LOGGER.info("PMV3_STORAGE_ADOPTED container={} settlement={} position={} stacks={}", id.value(), home.id().value(), p, lots.size());
        return true;
    }
    private static void finish(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                               FrontierWorldState state, ContainerSurface surface) {
        var p = surface.position(); var pos = new BlockPos(p.x(), p.y(), p.z());
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest) || !plain(chest)) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, surface.containerId()); return;
        }
        var physical = FrontierV3PhysicalContainer.observedChest(surface.containerId(), chest);
        var declared = physical.declaration().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY);
        if (!declared.isBlank() && !physical.declared()
                || !FrontierV3ContainerSurfaceExecutor.matchesCanonicalSlots(chest, state, surface.containerId())) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, surface.containerId()); return;
        }
        physical.stampPreparedProjection();
        FrontierV3CommandSubmission.submit(runtime, "storage-adoption-confirm", surface.containerId().value(),
                new ContainerSurfaceTransition(surface.containerId(), ContainerSurfaceStatus.ACTIVE));
    }
    private static SurfaceAnchor station(ServerLevel level, FrontierWorldState state, BlockPos chest) {
        var ground = KnownPedestrianGround.forFrontier(state);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            var surface = ground.at(chest.getX() + dx, chest.getZ() + dz);
            if (surface == null) continue;
            var support = surface.support(); var pos = new BlockPos(support.x(), support.y(), support.z());
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                    && FrontierV3StandingPosition.aboveExactFloor(level, pos) != null
                    && io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(surface, position(chest)))
                return surface;
        }
        return null;
    }
    private static boolean plain(ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            var item = chest.getItem(slot);
            if (!item.isEmpty() && (!item.getComponentsPatch().isEmpty() || item.getCount() > 64)) return false;
        }
        return true;
    }
    private static BlockPosition position(BlockPos p) { return new BlockPosition(p.getX(), p.getY(), p.getZ()); }
}
