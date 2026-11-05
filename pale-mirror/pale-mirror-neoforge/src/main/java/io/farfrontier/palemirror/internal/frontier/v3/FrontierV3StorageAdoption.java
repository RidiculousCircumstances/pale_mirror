package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
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
            for (var block : chunk.getBlockEntities().values().stream().flatMap(entity -> FrontierV3FixedContainerProviders.candidate(entity).stream())
                    .sorted(Comparator.comparingLong(candidate -> candidate.entity().getBlockPos().asLong())).toList()) {
                if (admit(level, runtime, state, block)) return;
            }
        }
    }
    private static boolean admit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 FrontierWorldState state, FrontierV3FixedContainerProviders.StorageCandidate chest) {
        var data = chest.entity().getPersistentData(); var p = position(chest.entity().getBlockPos());
        if (data.getBoolean(EXCLUDED_KEY) || !data.getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY).isBlank()
                || !data.getString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY).isBlank()
                || state.inventory().surfaces().values().stream().filter(ContainerSurface::fixed)
                    .filter(s -> s.status() != ContainerSurfaceStatus.RETIRED).anyMatch(s -> s.position().equals(p))) return false;
        var home = SettlementStorageTerritory.owner(state, p).orElse(null);
        if (home == null || !plain(chest)) return false;
        var access = access(level, state, home, chest.entity().getBlockPos());
        if (access == null) return false; // Storage exists; interaction waits for an admissible shared-geometry port.
        // A readable world-local identity stamped by this producer; nobody dispatches by its spelling.
        var firstId = new SubjectId("container:observed-" + p.x() + "_" + p.y() + "_" + p.z());
        var id = state.inventory().containers().containsKey(firstId)
                ? new SubjectId("container:observed-" + UUID.randomUUID()) : firstId;
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, access.station(), access.facility(), access.exterior());
        var zone = io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingStorageZones.at(home,p);
        var filter = zone.map(io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingStorageZones.Selection::filter).orElse(ResourceStorageFilter.all());
        boolean diningStorage = access.facility().isPresent() && ResidentDiningPlaces.in(state, home.id()).stream()
                .anyMatch(place -> place.facilityId().equals(access.facility().orElseThrow()));
        if (!diningStorage) filter = filter.excluding(state.bootstrap().ruleset().residentLife().foods().foods().keySet());
        var policy = new ResourceStoragePolicy(endpoint, filter,
                zone.map(io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingStorageZones.Selection::priority).orElse(0), ResourceStorageRole.WAREHOUSE);
        var container = ContainerRecord.storage(id, home.id(), chest.inventory().getContainerSize(), chest.kind(), policy);
        if (!FrontierV3FixedContainerProviders.acceptsBlock(container, chest.entity().getBlockState())) return false;
        var lots = new ArrayList<ResourceLot>();
        for (int slot = 0; slot < chest.inventory().getContainerSize(); slot++) {
            var item = chest.inventory().getItem(slot); if (item.isEmpty()) continue;
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
        var candidate = FrontierV3FixedContainerProviders.candidate(level.getBlockEntity(pos)).orElse(null);
        var physical = FrontierV3PhysicalContainer.inspect(level, state, surface.containerId()).orElse(null);
        if (candidate == null || physical == null || !plain(candidate)) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, surface.containerId()); return;
        }
        var declared = physical.declaration().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY);
        if (!declared.isBlank() && !physical.declared()
                || !FrontierV3ContainerSurfaceExecutor.matchesCanonicalSlots(physical.inventory(), state, surface.containerId())) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, surface.containerId()); return;
        }
        physical.stampPreparedProjection();
        FrontierV3CommandSubmission.submit(runtime, "storage-adoption-confirm", surface.containerId().value(),
                new ContainerSurfaceTransition(surface.containerId(), ContainerSurfaceStatus.ACTIVE));
    }
    private record Access(SurfaceAnchor station, Optional<SubjectId> facility, SurfaceAnchor exterior) { }
    private static Access access(ServerLevel level, FrontierWorldState state, Settlement home, BlockPos container) {
        var position = position(container);
        // Discovery binds the actual authored room once. Continuations retain this exact facility.
        var containing = home.structures().stream().filter(structure -> structure.definition().fabric()
                .contains(structure.placement().local(position))).toList();
        if (containing.size() > 1) throw new IllegalArgumentException("ambiguous ordinary storage facility");
        var facility = containing.stream().findFirst();
        var passages = facility.map(structure -> List.of(new KnownPedestrianRouteKnowledge.SettlementPassage(
                home.id(), new KnownPedestrianRouteKnowledge.Passage(structure, KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS))))
                .orElse(List.of());
        var knowledge = KnownPedestrianRouteKnowledge.forPublicJourney(state, home.id(), passages);
        var candidates = knowledge.interactionStations(position,
                io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN).stream()
                .filter(knowledge::clearStation).sorted(Comparator.comparingLong(surface -> Math.abs((long) surface.x() - position.x()) + Math.abs((long) surface.y() - position.y()) + Math.abs((long) surface.z() - position.z()))).toList();
        for (var surface : candidates) {
            var support = surface.support(); var pos = new BlockPos(support.x(), support.y(), support.z());
            if (!FrontierV3LoadedWorld.ready(level, pos) || FrontierV3StandingPosition.aboveExactFloor(level, pos) == null) continue;
            var exterior = facility.map(structure -> io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSpatialPlan
                    .of(structure).primaryEntrance().exteriorApproach().getFirst()).orElse(surface);
            return new Access(surface, facility.map(SettlementStructure::id), exterior);
        }
        return null;
    }
    private static boolean plain(FrontierV3FixedContainerProviders.StorageCandidate chest) {
        for (int slot = 0; slot < chest.inventory().getContainerSize(); slot++) {
            var item = chest.inventory().getItem(slot);
            if (!item.isEmpty() && (!item.getComponentsPatch().isEmpty() || item.getCount() > 64)) return false;
        }
        return true;
    }
    private static BlockPosition position(BlockPos p) { return new BlockPosition(p.getX(), p.getY(), p.getZ()); }
}
