package io.farfrontier.palemirror.internal.world;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.effect.EffectLeaseState;
import io.farfrontier.palemirror.internal.integration.vanilla.VanillaMinecartRailAdapter;
import io.farfrontier.palemirror.internal.integration.vanilla.VanillaMinecartSegmentPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Core-owned executor for the early freight profile. It never asks Minecraft
 * to load corridor chunks, never changes canonical stock, and blocks visibly
 * on an observed provenance mismatch.
 */
public final class VanillaMinecartRouteRuntime {
    private static final int BUILD_SEGMENT_BUDGET = 6;
    private static final int VERIFY_CELL_BUDGET = 32;

    private VanillaMinecartRouteRuntime() { }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        boolean changed = ensureRecords(server.overworld(), data);
        VanillaMinecartRailAdapter adapter = AdapterRegistry.vanillaMinecartRail();
        for (VanillaMinecartRouteRecord record : data.vanillaMinecartRoutes().values()) {
            if (!record.dimensionId().equals(server.overworld().dimension().location().toString())) continue;
            changed |= advance(server.overworld(), data, commands, adapter, record);
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

    private static boolean advance(ServerLevel level, PaleMirrorSavedData data, DomainCommandProcessor commands,
                                   VanillaMinecartRailAdapter adapter, VanillaMinecartRouteRecord record) {
        return switch (record.status()) {
            case PLANNED -> { record.begin(); yield true; }
            case BUILDING -> buildLoadedSegments(level, adapter, record);
            case VERIFYING -> verifyAndActivate(level, data, commands, adapter, record);
            case ACTIVE -> refreshActiveRoute(level, data, commands, adapter, record);
            case SUSPENDED -> recoverSuspended(level, data, commands, adapter, record);
            case BLOCKED, LEGACY -> false;
        };
    }

    private static boolean buildLoadedSegments(ServerLevel level, VanillaMinecartRailAdapter adapter,
                                                VanillaMinecartRouteRecord record) {
        boolean changed = false;
        int budget = BUILD_SEGMENT_BUDGET;
        for (int index = 0; index < record.segmentCount() && budget > 0; index++) {
            if (record.isComplete(index)) continue;
            BlockPos rail = record.railPosition(index);
            if (!level.hasChunkAt(rail)) continue;
            VanillaMinecartSegmentPlan plan = adapter.planSegment(level, rail, record.direction(),
                    record.nextRailY(index), record.previousRailY(index), index,
                    index == record.segmentCount() - 1);
            PreflightResult preflight = preflight(level, adapter, record, plan);
            if (preflight == PreflightResult.BLOCKED) return true;
            if (preflight == PreflightResult.CAPTURED) {
                changed = true;
                continue;
            }
            plan.writes().forEach((position, state) -> level.setBlock(position, state, 3));
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

    private static PreflightResult preflight(ServerLevel level, VanillaMinecartRailAdapter adapter,
                                              VanillaMinecartRouteRecord record, VanillaMinecartSegmentPlan plan) {
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
        return captured ? PreflightResult.CAPTURED : PreflightResult.READY;
    }

    private static boolean verifyAndActivate(ServerLevel level, PaleMirrorSavedData data, DomainCommandProcessor commands,
                                             VanillaMinecartRailAdapter adapter, VanillaMinecartRouteRecord record) {
        if (!verifyLoadedProvenance(level, adapter, record)) return true;
        if (!validateCanonicalRoute(data, commands, record)) {
            var region = data.worldState().livingRegion(record.regionId()).orElse(null);
            var route = region == null ? null : data.worldState().routeContract(region.primaryRouteId()).orElse(null);
            if (route == null || route.provider() != RouteProvider.VANILLA_MINECART) return false;
        }
        record.activate();
        return true;
    }

    private static boolean refreshActiveRoute(ServerLevel level, PaleMirrorSavedData data, DomainCommandProcessor commands,
                                              VanillaMinecartRailAdapter adapter, VanillaMinecartRouteRecord record) {
        boolean changed = !verifyLoadedProvenance(level, adapter, record);
        if (record.status() != VanillaMinecartRouteStatus.ACTIVE) {
            changed |= validateCanonicalRoute(data, commands, record, 0);
            return true;
        }
        changed |= validateCanonicalRoute(data, commands, record);
        changed |= ensureRepresentativeCart(level, data, adapter, record);
        changed |= moveRepresentativeCart(level, data, record);
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

    private static boolean validateCanonicalRoute(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                                  VanillaMinecartRouteRecord record) {
        var region = data.worldState().livingRegion(record.regionId()).orElse(null);
        if (region == null) return false;
        var route = data.worldState().routeContract(region.primaryRouteId()).orElse(null);
        return route != null && validateCanonicalRoute(data, commands, record, route.nominalCapacity());
    }

    private static boolean validateCanonicalRoute(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                                   VanillaMinecartRouteRecord record, int capacity) {
        var region = data.worldState().livingRegion(record.regionId()).orElse(null);
        if (region == null) return false;
        var route = data.worldState().routeContract(region.primaryRouteId()).orElse(null);
        if (route == null || route.provider() != RouteProvider.VANILLA_MINECART) return false;
        String observationId = "vanilla-minecart:" + record.routeId() + ":" + capacity + ":"
                + data.worldState().simulationStep() + ":" + record.status();
        if (observationId.equals(route.lastObservationId())) return false;
        return !commands.execute(data.worldState(), new DomainCommand.ValidateRouteContract(route.id(),
                capacity, data.worldState().simulationStep(), observationId,
                "vanilla-minecart:" + record.regionId())).isEmpty();
    }

    private static boolean recoverSuspended(ServerLevel level, PaleMirrorSavedData data, DomainCommandProcessor commands,
                                            VanillaMinecartRailAdapter adapter,
                                            VanillaMinecartRouteRecord record) {
        boolean changed = validateCanonicalRoute(data, commands, record, 0);
        // A long line can have breaks in several independently loaded chunks. Keep
        // discovering them while suspended instead of revealing one repair at a time.
        int knownDamage = record.damagedCriticalCellCount();
        verifyLoadedProvenance(level, adapter, record);
        changed |= knownDamage != record.damagedCriticalCellCount();
        for (long packed : record.damagedCriticalCells()) {
            BlockPos position = BlockPos.of(packed);
            if (!level.hasChunkAt(position)) continue;
            VanillaMinecartMutableCell cell = record.cell(position);
            if (cell != null && adapter.matchesProvenance(level.getBlockState(position), cell.lastAppliedState())) {
                changed |= record.repaired(position);
            }
        }
        if (record.repairComplete()) {
            record.resume();
            changed = true;
            changed |= validateCanonicalRoute(data, commands, record);
        }
        return changed;
    }

    private static boolean ensureRepresentativeCart(ServerLevel level, PaleMirrorSavedData data,
                                                     VanillaMinecartRailAdapter adapter, VanillaMinecartRouteRecord record) {
        if (record.representativeCartId() != null) {
            int expectedIndex = Math.max(0, Math.min(record.segmentCount() - 1, (int) Math.round(record.cartProgress())));
            BlockPos expected = record.railPosition(expectedIndex);
            if (!level.hasChunkAt(expected) || level.getEntity(record.representativeCartId()) != null) return false;
            if (record.representativeCargoId() != null) {
                net.minecraft.world.entity.Entity orphan = level.getEntity(record.representativeCargoId());
                if (orphan != null && VanillaMinecartRailAdapter.isRepresentative(orphan)) orphan.discard();
            }
            record.forgetMissingRepresentativeCart();
            return true;
        }
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

    private static boolean moveRepresentativeCart(ServerLevel level, PaleMirrorSavedData data,
                                                   VanillaMinecartRouteRecord record) {
        if (record.representativeCartId() == null) return false;
        var region = data.worldState().livingRegion(record.regionId()).orElse(null);
        if (region == null) return false;
        var facility = data.worldState().facility(region.primaryFacilityId()).orElse(null);
        var route = data.worldState().routeContract(region.primaryRouteId()).orElse(null);
        if (facility == null || facility.status() != io.farfrontier.palemirror.domain.FacilityStatus.OPERATIONAL
                || route == null || route.transferableCapacity(data.worldState().simulationStep()) <= 0) return false;
        net.minecraft.world.entity.Entity cart = level.getEntity(record.representativeCartId());
        if (cart == null) return false;
        double proposed = record.proposedCartProgress(0.08D);
        int lowerIndex = (int) Math.floor(proposed);
        int upperIndex = Math.min(record.segmentCount() - 1, lowerIndex + 1);
        BlockPos lower = record.railPosition(lowerIndex);
        BlockPos upper = record.railPosition(upperIndex);
        if (!level.hasChunkAt(lower) || !level.hasChunkAt(upper)) return false;
        double fraction = proposed - lowerIndex;
        double x = net.minecraft.util.Mth.lerp(fraction, lower.getX() + 0.5D, upper.getX() + 0.5D);
        double y = net.minecraft.util.Mth.lerp(fraction, lower.getY() + 0.1D, upper.getY() + 0.1D);
        double z = net.minecraft.util.Mth.lerp(fraction, lower.getZ() + 0.5D, upper.getZ() + 0.5D);
        record.moveCart(proposed);
        cart.moveTo(x, y, z, cart.getYRot(), cart.getXRot());
        cart.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        return level.getGameTime() % 20L == 0L;
    }

    private enum PreflightResult { CAPTURED, READY, BLOCKED }
}
