package io.farfrontier.palemirror.internal.world;

import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.RouteContractStatus;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.effect.EffectLeaseState;
import io.farfrontier.palemirror.internal.integration.vanilla.VanillaMinecartRailAdapter;
import io.farfrontier.palemirror.internal.integration.vanilla.VanillaMinecartSegmentPlan;
import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.internal.materialization.JobState;
import io.farfrontier.palemirror.internal.materialization.MaterializationGateway;
import io.farfrontier.palemirror.internal.materialization.MaterializationJob;
import io.farfrontier.palemirror.internal.materialization.MaterializationJobClass;
import io.farfrontier.palemirror.internal.materialization.MaterializationOperation;
import io.farfrontier.palemirror.internal.materialization.MaterializationOperationType;
import io.farfrontier.palemirror.internal.materialization.OperationState;
import io.farfrontier.palemirror.internal.materialization.ParcelRecord;
import io.farfrontier.palemirror.internal.materialization.SemanticCellRecord;
import io.farfrontier.palemirror.internal.materialization.SemanticSlotRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ChunkPos;

/**
 * Core-owned executor for the early freight profile. It never asks Minecraft
 * to load corridor chunks, never changes canonical stock, and blocks visibly
 * on an observed provenance mismatch.
 */
public final class VanillaMinecartRouteRuntime {
    private static final int BUILD_SEGMENT_BUDGET = 6;
    private static final int VERIFY_CELL_BUDGET = 32;

    private VanillaMinecartRouteRuntime() { }

    public static boolean hasPendingWork(PaleMirrorSavedData data) {
        return data.vanillaMinecartRoutes().values().stream().anyMatch(record ->
                record.status() == VanillaMinecartRouteStatus.PLANNED
                        || record.status() == VanillaMinecartRouteStatus.BUILDING
                        || record.status() == VanillaMinecartRouteStatus.VERIFYING);
    }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands) {
        boolean changed = ensureRecords(server.overworld(), data);
        VanillaMinecartRailAdapter adapter = AdapterRegistry.vanillaMinecartRail();
        for (VanillaMinecartRouteRecord record : data.vanillaMinecartRoutes().values()) {
            if (!record.dimensionId().equals(server.overworld().dimension().location().toString())) continue;
            changed |= advance(server.overworld(), data, commands, adapter, record);
        }
        return changed;
    }

    public static boolean observeChunkLoad(PaleMirrorSavedData data, ServerLevel level, ChunkPos chunk) {
        boolean changed = false;
        String dimension = level.dimension().location().toString();
        for (VanillaMinecartRouteRecord record : data.vanillaMinecartRoutes().values()) {
            if (record.dimensionId().equals(dimension)) changed |= record.markTopologyChunkDirty(chunk);
        }
        return changed;
    }

    public static boolean observeBlockChange(PaleMirrorSavedData data, ServerLevel level, BlockPos position) {
        boolean changed = false;
        String dimension = level.dimension().location().toString();
        for (VanillaMinecartRouteRecord record : data.vanillaMinecartRoutes().values()) {
            if (record.dimensionId().equals(dimension)) changed |= record.markTopologyDirty(position);
        }
        return changed;
    }

    private static boolean ensureRecords(ServerLevel level, PaleMirrorSavedData data) {
        boolean changed = false;
        for (CampaignRegionRecord region : data.campaignRegions().values()) {
            if (region.status() != CampaignRegionPresentationStatus.MATERIALIZED || region.primaryMineAnchor() == null
                    || data.vanillaMinecartRoutes().containsKey(region.id())) continue;
            var living = data.worldState().livingRegion(region.id()).orElse(null);
            if (living == null || !data.worldState().routeContract(living.primaryRouteId())
                    .map(route -> route.provider() == RouteProvider.VANILLA_MINECART).orElse(false)) continue;
            BlockPos start = region.primaryMineAnchor().offset(0, 1, -3);
            BlockPos target = region.layoutVersion() >= 2 ? region.receivingTerminalAnchor()
                    : railTarget(level, region.settlementAnchor(), start);
            data.vanillaMinecartRoutes().put(region.id(), VanillaMinecartRouteRecord.planned(region.id(),
                    region.dimensionId(), living.primaryRouteId().value(), start, target));
            changed = true;
        }
        return changed;
    }

    private static BlockPos railTarget(ServerLevel level, BlockPos settlementAnchor, BlockPos mineStart) {
        int dx = mineStart.getX() - settlementAnchor.getX();
        int dz = mineStart.getZ() - settlementAnchor.getZ();
        int x = settlementAnchor.getX();
        int z = settlementAnchor.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) x += Integer.signum(dx) * 64;
        else z += Integer.signum(dz) * 64;
        BlockPos column = new BlockPos(x, settlementAnchor.getY(), z);
        int y = level.hasChunkAt(column) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                : settlementAnchor.getY();
        return new BlockPos(x, y, z);
    }

    private static boolean advance(ServerLevel level, PaleMirrorSavedData data, DomainCommandExecutor commands,
                                   VanillaMinecartRailAdapter adapter, VanillaMinecartRouteRecord record) {
        MaterializationJob job = ensureJob(data, record);
        if (job.state() == JobState.PLANNED) { job.start(); return true; }
        boolean changed = switch (record.status()) {
            case PLANNED -> { record.begin(); yield true; }
            case BUILDING -> buildLoadedSegments(level, data, commands, adapter, record);
            case VERIFYING -> verifyAndActivate(level, data, commands, adapter, record);
            case ACTIVE -> refreshActiveRoute(level, data, commands, adapter, record);
            case SUSPENDED -> recoverSuspended(level, data, commands, adapter, record);
            case BLOCKED, LEGACY -> false;
        };
        if (record.status() == VanillaMinecartRouteStatus.ACTIVE && job.state() != JobState.COMPLETED) {
            MaterializationOperation operation = job.nextOperation();
            if (operation != null) { operation.start(); operation.complete(); job.advanceOperation(); }
            job.complete(); changed = true;
        } else if (record.status() == VanillaMinecartRouteStatus.BLOCKED && job.state() != JobState.BLOCKED) {
            job.block(record.diagnostic()); changed = true;
        }
        return changed;
    }

    private static boolean buildLoadedSegments(ServerLevel level, PaleMirrorSavedData data,
                                                DomainCommandExecutor commands,
                                                VanillaMinecartRailAdapter adapter,
                                                VanillaMinecartRouteRecord record) {
        boolean changed = false;
        int budget = BUILD_SEGMENT_BUDGET;
        for (int index = 0; index < record.segmentCount() && budget > 0; index++) {
            if (record.isComplete(index)) continue;
            BlockPos rail = record.railPosition(index);
            if (!level.hasChunkAt(rail)) continue;
            VanillaMinecartSegmentPlan plan = adapter.planSegment(level, rail,
                    index == 0 ? record.direction(0) : record.direction(index - 1), record.direction(index),
                    record.nextRailY(index), record.previousRailY(index), index,
                    index == record.segmentCount() - 1);
            if (record.worldgenAuthored()) {
                if (!adapter.postcondition(level, plan)) {
                    // The canonical authored topology is optimistic only while its
                    // chunks are unknown. Once material evidence is loaded, absence
                    // of the expected segment must immediately stop abstract flow.
                    changed |= validateCanonicalRoute(data, commands, record, 0);
                    continue;
                }
                captureWorldgenSegment(level, data, adapter, record, plan, index);
                record.complete(index);
                budget--;
                changed = true;
                continue;
            }
            PreflightResult preflight = preflight(level, data, adapter, record, plan, index);
            if (preflight == PreflightResult.BLOCKED) return true;
            if (preflight == PreflightResult.CAPTURED) {
                // Persist one exact baseline before any neighbouring segment can
                // alter an overlapping support/platform cell. The next tick can
                // then apply this segment against that durable snapshot.
                return true;
            }
            SemanticSlotKey slot = routeSlot(record, index);
            MaterializationGateway gateway = new MaterializationGateway(level, data.semanticSlots(), data.parcels());
            for (var write : plan.writes().entrySet()) {
                var result = gateway.setBlock(slot, write.getKey(), write.getValue(), 3,
                        observed -> adapter.matchesProvenance(observed, adapter.signature(write.getValue())));
                if (result.status() == io.farfrontier.palemirror.api.GuardedWorldAccess.Status.BLOCKED) {
                    record.block(result.diagnostic()); return true;
                }
            }
            if (!adapter.postcondition(level, plan)) {
                record.block("Vanilla minecart postcondition failed at " + rail.toShortString() + ": "
                        + adapter.postconditionDiagnostic(level, plan));
                return true;
            }
            plan.writes().forEach((position, ignored) -> record.approve(position,
                    adapter.signature(level.getBlockState(position))));
            record.complete(index);
            budget--;
            changed = true;
        }
        if (record.allSegmentsComplete()) {
            record.verify();
            return true;
        }
        return changed;
    }

    private static void captureWorldgenSegment(ServerLevel level, PaleMirrorSavedData data,
                                                VanillaMinecartRailAdapter adapter,
                                                VanillaMinecartRouteRecord record,
                                                VanillaMinecartSegmentPlan plan, int index) {
        for (BlockPos position : plan.writes().keySet()) {
            String observed = adapter.signature(level.getBlockState(position));
            record.capture(position, observed);
            record.approve(position, observed);
        }
        registerSlot(level, data, record, plan, index);
    }

    private static PreflightResult preflight(ServerLevel level, PaleMirrorSavedData data, VanillaMinecartRailAdapter adapter,
                                              VanillaMinecartRouteRecord record, VanillaMinecartSegmentPlan plan, int index) {
        boolean captured = false;
        for (Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState> entry : plan.writes().entrySet()) {
            BlockPos position = entry.getKey();
            VanillaMinecartMutableCell existing = record.cell(position);
            if (existing == null) {
                if (adapter.protectedInitialCell(level, position)) {
                    record.block("Vanilla minecart route encountered protected cell at " + position.toShortString());
                    return PreflightResult.BLOCKED;
                }
                record.capture(position, adapter.signature(level.getBlockState(position)));
                captured = true;
            } else if (!existing.accepts(adapter.signature(level.getBlockState(position)))) {
                existing.conflict();
                record.block("Vanilla minecart route conflict at " + position.toShortString());
                return PreflightResult.BLOCKED;
            }
        }
        registerSlot(level, data, record, plan, index);
        return captured ? PreflightResult.CAPTURED : PreflightResult.READY;
    }

    private static void registerSlot(ServerLevel level, PaleMirrorSavedData data, VanillaMinecartRouteRecord record,
                                     VanillaMinecartSegmentPlan plan, int index) {
        SemanticSlotKey key = routeSlot(record, index);
        if (data.semanticSlots().find(key).isPresent()) return;
        BlockPos min = plan.writes().keySet().stream().reduce((a, b) -> new BlockPos(Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()))).orElseThrow();
        BlockPos max = plan.writes().keySet().stream().reduce((a, b) -> new BlockPos(Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))).orElseThrow();
        String parcelId = record.routeId() + ":parcel:segment_" + index;
        if (data.parcels().find(parcelId).isEmpty()) data.parcels().register(new ParcelRecord(parcelId,
                record.regionId(), record.dimensionId(), min, max, "baseline_rail", ParcelKind.PUBLIC_INFRASTRUCTURE,
                null, 0, ""));
        List<SemanticCellRecord> cells = plan.writes().keySet().stream().map(position -> {
            var state = level.getBlockState(position); return new SemanticCellRecord(position, state, state);
        }).toList();
        SemanticSlotRegistration.register(data.semanticSlots(), data.parcels(), key, parcelId, record.dimensionId(),
                ParcelKind.PUBLIC_INFRASTRUCTURE, cells);
    }

    private static SemanticSlotKey routeSlot(VanillaMinecartRouteRecord record, int index) {
        return new SemanticSlotKey(record.routeId(), "segment_" + index, "public_infrastructure");
    }

    private static MaterializationJob ensureJob(PaleMirrorSavedData data, VanillaMinecartRouteRecord record) {
        MaterializationJob current = data.materializationJobs().activeFor(record.routeId(), "baseline_rail").orElse(null);
        if (current != null) return current;
        String id = "pm:job:baseline_rail:" + Integer.toUnsignedString(record.routeId().hashCode(), 36) + ":1";
        MaterializationOperation operation = new MaterializationOperation(id + ":corridor", id + ":corridor",
                MaterializationOperationType.ENSURE_ROUTE_SEGMENT, record.regionId(), OperationState.PENDING, 0, "");
        boolean historical = record.status() == VanillaMinecartRouteStatus.ACTIVE
                || record.status() == VanillaMinecartRouteStatus.SUSPENDED;
        if (historical) operation.complete();
        MaterializationJob job = new MaterializationJob(id, record.routeId(), "baseline_rail",
                MaterializationJobClass.CAPABILITY, 1, "pale_mirror:baseline_freight_graph",
                VanillaMinecartRouteRecord.POLICY_VERSION, historical ? JobState.COMPLETED : JobState.PLANNED,
                List.of(operation), historical ? 1 : 0, 0, "");
        data.materializationJobs().put(job); return job;
    }

    private static boolean verifyAndActivate(ServerLevel level, PaleMirrorSavedData data, DomainCommandExecutor commands,
                                             VanillaMinecartRailAdapter adapter, VanillaMinecartRouteRecord record) {
        if (!verifyLoadedProvenance(level, adapter, record)) return true;
        if (!validateCanonicalRoute(data, commands, record)) {
            var region = data.worldState().livingRegion(record.regionId()).orElse(null);
            var route = region == null ? null : data.worldState().routeContract(region.primaryRouteId()).orElse(null);
            if (route == null || route.provider() != RouteProvider.VANILLA_MINECART) return false;
        }
        record.initializeAuthoredTopology();
        record.activate();
        return true;
    }

    private static boolean refreshActiveRoute(ServerLevel level, PaleMirrorSavedData data, DomainCommandExecutor commands,
                                              VanillaMinecartRailAdapter adapter, VanillaMinecartRouteRecord record) {
        boolean changed = VanillaRailTopologyRuntime.refresh(level, adapter, record, VERIFY_CELL_BUDGET);
        if (record.status() != VanillaMinecartRouteStatus.ACTIVE) {
            changed |= validateCanonicalRoute(data, commands, record, 0);
            return true;
        }
        changed |= validateCanonicalRoute(data, commands, record);
        changed |= ensureRepresentativeCart(level, data, adapter, record);
        changed |= updateRepresentativeCart(level, data, adapter, record);
        return changed;
    }

    private static boolean verifyLoadedProvenance(ServerLevel level, VanillaMinecartRailAdapter adapter,
                                                   VanillaMinecartRouteRecord record) {
        var slice = record.verificationSlice(VERIFY_CELL_BUDGET);
        boolean unchanged = true;
        for (VanillaMinecartMutableCell cell : slice) {
            if (!level.hasChunkAt(cell.position())) continue;
            if (adapter.matchesProvenance(level.getBlockState(cell.position()), cell.lastAppliedState())) {
                if (cell.conflicted()) { cell.clearConflict(); unchanged = false; }
                continue;
            }
            if (!adapter.matchesProvenance(level.getBlockState(cell.position()), cell.lastAppliedState())) {
                cell.conflict();
                if (adapter.criticalInfrastructure(cell.lastAppliedState())) {
                    record.suspend(cell.position(), "Vanilla minecart route changed at " + cell.position().toShortString());
                    unchanged = false;
                    continue;
                }
                record.decorativeConflict("Vanilla minecart decoration changed at " + cell.position().toShortString());
                unchanged = false;
            }
        }
        record.advanceVerificationCursor(slice.size());
        return unchanged;
    }

    private static boolean validateCanonicalRoute(PaleMirrorSavedData data, DomainCommandExecutor commands,
                                                  VanillaMinecartRouteRecord record) {
        var region = data.worldState().livingRegion(record.regionId()).orElse(null);
        if (region == null) return false;
        var route = data.worldState().routeContract(region.primaryRouteId()).orElse(null);
        return route != null && validateCanonicalRoute(data, commands, record, route.nominalCapacity());
    }

    private static boolean validateCanonicalRoute(PaleMirrorSavedData data, DomainCommandExecutor commands,
                                                   VanillaMinecartRouteRecord record, int capacity) {
        var region = data.worldState().livingRegion(record.regionId()).orElse(null);
        if (region == null) return false;
        var route = data.worldState().routeContract(region.primaryRouteId()).orElse(null);
        if (route == null || route.provider() != RouteProvider.VANILLA_MINECART) return false;
        int boundedCapacity = Math.min(route.nominalCapacity(), capacity);
        RouteContractStatus expectedStatus = boundedCapacity > 0
                ? RouteContractStatus.VALIDATED : RouteContractStatus.BLOCKED;
        if (route.provider().hasPersistentTopologyEvidence()
                && route.status() == expectedStatus
                && route.validatedCapacity() == boundedCapacity) return false;
        String observationId = "vanilla-minecart:" + record.routeId() + ":" + capacity + ":"
                + data.worldState().simulationStep() + ":" + record.status();
        if (observationId.equals(route.lastObservationId())) return false;
        return !commands.execute(data.worldState(), new DomainCommand.ValidateRouteContract(route.id(),
                capacity, data.worldState().simulationStep(), observationId,
                "vanilla-minecart:" + record.regionId())).isEmpty();
    }

    private static boolean recoverSuspended(ServerLevel level, PaleMirrorSavedData data, DomainCommandExecutor commands,
                                            VanillaMinecartRailAdapter adapter,
                                            VanillaMinecartRouteRecord record) {
        boolean changed = validateCanonicalRoute(data, commands, record, 0);
        changed |= VanillaRailTopologyRuntime.refresh(level, adapter, record, VERIFY_CELL_BUDGET);
        if (record.status() == VanillaMinecartRouteStatus.ACTIVE)
            changed |= validateCanonicalRoute(data, commands, record);
        else changed |= parkExistingRepresentative(level, adapter, record);
        return changed;
    }

    private static boolean ensureRepresentativeCart(ServerLevel level, PaleMirrorSavedData data,
                                                     VanillaMinecartRailAdapter adapter, VanillaMinecartRouteRecord record) {
        int expectedIndex = Math.max(0, Math.min(record.travelPathSize() - 1, (int) Math.round(record.cartProgress())));
        BlockPos expected = record.travelPosition(expectedIndex);
        net.minecraft.world.entity.Entity registered = record.representativeCartId() == null ? null
                : level.getEntity(record.representativeCartId());
        boolean reconcile = record.representativeCartId() == null || registered == null;
        if (reconcile && level.hasChunkAt(record.representativeCartId() == null ? record.start() : expected)) {
            var reconciled = adapter.reconcileRepresentatives(level, record.routeId(), record.representativeCartId(),
                    record.representativeCargoId());
            if (reconciled.keeper() != null) {
                boolean changed = reconciled.removedEntities() > 0
                        || !reconciled.keeper().cartId().equals(record.representativeCartId())
                        || !reconciled.keeper().cargoId().equals(record.representativeCargoId());
                record.observeRepresentativeCart(reconciled.keeper().cartId(), reconciled.keeper().cargoId());
                return changed;
            }
            if (record.representativeCartId() != null) {
                record.forgetMissingRepresentativeCart();
                return true;
            }
        }
        if (record.representativeCartId() != null) return false;
        if (!level.hasChunkAt(record.start())) return false;
        String leaseId = "pm:vanilla-minecart:carrier:" + record.regionId() + ":" + record.routeId()
                + ":" + java.util.UUID.randomUUID();
        if (record.cartLeaseId().isBlank()) {
            record.reserveCartLease(leaseId);
            return true;
        }
        if (!record.cartLeaseDispatched()) {
            record.dispatchCartLease();
            return true;
        }
        var existing = data.effectLeases().find(record.cartLeaseId()).orElse(null);
        if (existing != null && existing.state() != EffectLeaseState.PLANNED) return false;
        AtomicReference<io.farfrontier.palemirror.internal.integration.vanilla.VanillaMinecartRailAdapter.VisualCart> spawned = new AtomicReference<>();
        boolean executed = ControlledEffectExecutor.executeOnce(data, EffectLease.planned(record.cartLeaseId(),
                record.cartLeaseId(), "vanilla", record.routeId(), "representative", "minecart_carrier",
                level.getGameTime(), level.getGameTime() + 1_200L), level.getGameTime(),
                () -> spawned.set(adapter.spawnRepresentativeCart(level, record.start(), record.routeId())));
        if (executed && spawned.get() != null) record.observeRepresentativeCart(spawned.get().cartId(), spawned.get().cargoId());
        return executed;
    }

    private static boolean updateRepresentativeCart(ServerLevel level, PaleMirrorSavedData data,
                                                     VanillaMinecartRailAdapter adapter,
                                                     VanillaMinecartRouteRecord record) {
        if (record.representativeCartId() == null) return false;
        net.minecraft.world.entity.Entity cart = level.getEntity(record.representativeCartId());
        if (cart == null) return false;
        var region = data.worldState().livingRegion(record.regionId()).orElse(null);
        if (region == null) return false;
        var facility = data.worldState().facility(region.primaryFacilityId()).orElse(null);
        var route = data.worldState().routeContract(region.primaryRouteId()).orElse(null);
        if (facility == null || facility.status() != io.farfrontier.palemirror.domain.FacilityStatus.OPERATIONAL
                || route == null || route.transferableCapacity(data.worldState().simulationStep()) <= 0) {
            boolean changed = record.parkCartAtOrigin();
            if (level.hasChunkAt(record.start())) adapter.stabilizeRepresentativeCart(cart, record.start());
            else adapter.stabilizeRepresentativeCart(cart, cart.getX(), cart.getY(), cart.getZ());
            return changed;
        }
        double proposed = record.proposedCartProgress(0.08D);
        int lowerIndex = (int) Math.floor(proposed);
        int upperIndex = Math.min(record.travelPathSize() - 1, lowerIndex + 1);
        BlockPos lower = record.travelPosition(lowerIndex);
        BlockPos upper = record.travelPosition(upperIndex);
        if (!level.hasChunkAt(lower) || !level.hasChunkAt(upper)) return false;
        double fraction = proposed - lowerIndex;
        double x = net.minecraft.util.Mth.lerp(fraction, lower.getX() + 0.5D, upper.getX() + 0.5D);
        double y = net.minecraft.util.Mth.lerp(fraction, lower.getY() + 0.1D, upper.getY() + 0.1D);
        double z = net.minecraft.util.Mth.lerp(fraction, lower.getZ() + 0.5D, upper.getZ() + 0.5D);
        record.moveCart(proposed);
        adapter.stabilizeRepresentativeCart(cart, x, y, z);
        return level.getGameTime() % 20L == 0L;
    }

    private static boolean parkExistingRepresentative(ServerLevel level, VanillaMinecartRailAdapter adapter,
                                                       VanillaMinecartRouteRecord record) {
        if (record.representativeCartId() == null || !level.hasChunkAt(record.start())) return false;
        var reconciled = adapter.reconcileRepresentatives(level, record.routeId(), record.representativeCartId(),
                record.representativeCargoId());
        if (reconciled.keeper() == null) return false;
        boolean parked = record.parkCartAtOrigin();
        boolean changed = reconciled.removedEntities() > 0 || parked
                || !reconciled.keeper().cartId().equals(record.representativeCartId())
                || !reconciled.keeper().cargoId().equals(record.representativeCargoId());
        record.observeRepresentativeCart(reconciled.keeper().cartId(), reconciled.keeper().cargoId());
        net.minecraft.world.entity.Entity cart = level.getEntity(reconciled.keeper().cartId());
        if (cart != null) adapter.stabilizeRepresentativeCart(cart, record.start());
        return changed;
    }

    private enum PreflightResult { CAPTURED, READY, BLOCKED }
}
