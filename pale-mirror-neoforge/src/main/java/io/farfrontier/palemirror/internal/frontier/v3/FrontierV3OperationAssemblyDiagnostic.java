package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssembly;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only physical explanation of retained logistics-assembly cursors.
 *
 * <p>This belongs beside the operation boundary, not in the ambient body executor: it observes
 * already-loaded evidence only and can neither move a body nor submit a canonical command.</p>
 */
final class FrontierV3OperationAssemblyDiagnostic {
    private FrontierV3OperationAssemblyDiagnostic() { }

    static Optional<Readiness> readiness(ServerLevel level, FrontierWorldState state, SubjectId operationId) {
        RouteOperation operation = state.operations().get(operationId);
        if (operation == null || operation.activeAssembly().isEmpty()) return Optional.empty();
        List<MemberReadiness> members = operation.activeAssembly().orElseThrow().members().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(entry -> member(level, state, entry.getKey(), entry.getValue())).toList();
        return Optional.of(new Readiness(members));
    }

    private static MemberReadiness member(ServerLevel level, FrontierWorldState state, SubjectId actorId,
                                          OperationAssembly.Member member) {
        BlockPosition next = member.arrived() ? null : member.nextSurface().support();
        Entity body = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, actorId));
        BlockPosition observed = body == null ? null : new BlockPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ());
        FrontierV3AmbientActorExecutor.ObservedPosition observedExact = body == null ? null
                : new FrontierV3AmbientActorExecutor.ObservedPosition(body.getX(), body.getY(), body.getZ());
        if (next == null) return new MemberReadiness(actorId, member.currentSurface().support(), null, observed, observedExact,
                "ARRIVED", "", "", "", "", List.of());
        BlockPos target = new BlockPos(next.x(), next.y(), next.z());
        if (!level.hasChunkAt(target)) return new MemberReadiness(actorId, member.currentSurface().support(), next, observed, observedExact,
                "UNLOADED", "", "", "", "", List.of());
        List<String> occupants = level.getEntities((Entity) null, new AABB(target.getX(), target.getY(), target.getZ(),
                        target.getX() + 1.0D, target.getY() + 3.0D, target.getZ() + 1.0D), entity -> entity != body).stream()
                .sorted(java.util.Comparator.comparing(entity -> entity.getUUID().toString())).limit(4).map(FrontierV3OperationAssemblyDiagnostic::occupantKind).toList();
        String status = !FrontierV3StandingPosition.hasExactStandingColumn(level, next) ? "BLOCKED" : occupants.isEmpty() ? "CLEAR" : "OCCUPIED";
        return new MemberReadiness(actorId, member.currentSurface().support(), next, observed, observedExact, status,
                blockKind(level, target), blockKind(level, target.below()), blockKind(level, target.above()), blockKind(level, target.above(2)), occupants);
    }

    private static String blockKind(ServerLevel level, BlockPos position) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString();
    }

    private static String occupantKind(Entity entity) {
        String actorId = entity.getPersistentData().getString(FrontierV3AmbientActorExecutor.ACTOR_KEY);
        return actorId.isBlank() ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() : "frontier_actor";
    }

    record Readiness(List<MemberReadiness> members) {
        Readiness { members = List.copyOf(members); }
    }

    record MemberReadiness(SubjectId actorId, BlockPosition current, BlockPosition next, BlockPosition observed,
                           FrontierV3AmbientActorExecutor.ObservedPosition observedExact, String targetStatus,
                           String floorBlock, String supportBlock, String bodyBlock, String headBlock, List<String> occupants) {
        MemberReadiness { occupants = List.copyOf(occupants); }
    }
}
