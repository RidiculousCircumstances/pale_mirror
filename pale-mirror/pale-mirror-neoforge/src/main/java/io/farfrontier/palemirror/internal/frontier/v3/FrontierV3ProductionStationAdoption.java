package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.internal.production.ProductionBenchBlockEntity;
import io.farfrontier.palemirror.internal.production.PaleMirrorProductionStations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Adopts ordinary player placement at declared production sockets. No chunk loading or inventory writes. */
final class FrontierV3ProductionStationAdoption {
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || !FrontierV3PhysicalWorld.isPhysical(level)) return;
        for (var surface : state.inventory().surfaces().values().stream().filter(surface -> surface.status() == ContainerSurfaceStatus.ADOPTING)
                .sorted(Comparator.comparing(ContainerSurface::containerId)).toList()) {
            var record = state.inventory().containers().get(surface.containerId());
            if (record.purpose() != ContainerPurpose.PRODUCTION_STATION) continue;
            var p = surface.position(); var pos = new BlockPos(p.x(), p.y(), p.z());
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
            finish(level, runtime, state, record); return;
        }
        for (var home : state.bootstrap().settlements()) for (var facility : home.structures()) {
            if (!ProductionFacilityLayout.supports(facility.kind()) || state.structureConditions().get(facility.id()) != StructureCondition.INTACT) continue;
            var placement = ProductionFacilityLayout.station(facility);
            var p = placement.socketSurface().support().offset(0, 1, 0); var pos = new BlockPos(p.x(), p.y(), p.z());
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                    || state.inventory().surfaces().values().stream().filter(ContainerSurface::fixed)
                        .anyMatch(surface -> surface.status() != ContainerSurfaceStatus.RETIRED && surface.position().equals(p))
                    || !level.getBlockState(pos).is(FrontierV3FixedContainerProviders.equipmentBlock(placement.equipmentType()))
                    || !(level.getBlockEntity(pos) instanceof ProductionBenchBlockEntity bench)
                    || !bench.getPersistentData().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY).isBlank()
                    || !bench.getPersistentData().getString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY).isBlank()) continue;
            var containerId = new SubjectId("container:installed-" + UUID.randomUUID());
            var spec = new ProductionStationSpec(new SubjectId("station:installed-" + UUID.randomUUID()), facility.id(), containerId,
                    placement.equipmentType(), placement.capability(), placement.workerStation(), placement.socketSurface(),
                    ProductionBenchBlockEntity.INPUT, ProductionBenchBlockEntity.OUTPUT, ProductionBenchBlockEntity.FUEL);
            var slots = new HashMap<Integer, FungibleContainerLayout.Stack>(); var lots = new ArrayList<ResourceLot>(); boolean admissible = true;
            for (int slot = 0; slot < bench.getContainerSize(); slot++) {
                var item = bench.getItem(slot); if (item.isEmpty()) continue;
                var kind = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
                if (!item.getComponentsPatch().isEmpty() || item.getCount() > 64
                        || state.inventory().fungibleResources().accounts().values().stream()
                            .filter(account -> account.custody() instanceof ResourceCustody.Player)
                            .anyMatch(account -> account.lotQuantities().keySet().stream().anyMatch(id ->
                                state.inventory().fungibleResources().lots().get(id).itemKind().equals(kind)))) { admissible = false; break; }
                try { if (ProductionStationRecipe.portForItemKind(state.bootstrap().ruleset().production(), spec, kind) != slot) { admissible = false; break; } }
                catch (IllegalArgumentException unsupported) { admissible = false; break; }
                slots.put(slot, new FungibleContainerLayout.Stack(kind, item.getCount()));
                lots.add(new ResourceLot(new SubjectId("lot:installed-" + UUID.randomUUID()), home.id(), kind, item.getCount(), "observed-installed-equipment", List.of()));
            }
            if (!admissible) continue;
            var record = new ContainerRecord(containerId, home.id(), 3, ContainerPurpose.PRODUCTION_STATION, Optional.of(spec), Optional.empty(),
                    new FungibleContainerLayout(slots));
            if (FrontierV3CommandSubmission.submit(runtime, "station-install", spec.id().value(), new ProductionStationInstalled(record, p, lots))
                    instanceof CommandResult.Accepted) {
                finish(level, runtime, runtime.decodedState().orElseThrow(), record); return;
            }
        }
    }
    private static void finish(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, ContainerRecord record) {
        var physical = FrontierV3PhysicalContainer.inspect(level, state, record.id()).orElse(null);
        if (physical == null || !physical.declaration().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY).isBlank() && !physical.declared()
                || !FrontierV3ContainerSurfaceExecutor.matchesCanonicalSlots(physical.inventory(), state, record.id())) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, record.id()); return;
        }
        physical.stampPreparedProjection();
        FrontierV3CommandSubmission.submit(runtime, "station-install-confirm", record.id().value(), new ContainerSurfaceTransition(record.id(), ContainerSurfaceStatus.ACTIVE));
    }
    private FrontierV3ProductionStationAdoption() { }
}
