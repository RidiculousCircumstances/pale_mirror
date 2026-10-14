package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.WeakHashMap;

/** Rebuildable proof of one accepted native path, never a geometry or movement authority.
 * Immutable coordinates/scope are reused; current nearby chunks/fluids are still observed.
 * Native in-place path edits invalidate the proof before use, including edits of future nodes. */
final class FrontierV3PhysicalPathInspection {
    private static final java.util.Map<Mob, Proof> PROOFS = new WeakHashMap<>();
    private record Proof(java.lang.ref.WeakReference<ServerLevel> level, Path path, FrontierV3NavigationScope scope, BlockPos[] nodes, double[] remaining) {
        boolean matches(Path candidate, FrontierV3NavigationScope currentScope) {
            if (path != candidate || nodes.length != candidate.getNodeCount() || !scope.equals(currentScope)) return false;
            for (int i = 0; i < nodes.length; i++) {
                var node = candidate.getNode(i);
                if (nodes[i].getX() != node.x || nodes[i].getY() != node.y || nodes[i].getZ() != node.z) return false;
            }
            return true;
        }
    }
    private FrontierV3PhysicalPathInspection() { }
    static Optional<FrontierV3PhysicalPathPolicy.Rejection> inspect(ServerLevel level, Mob actor, Path path,
                                                                  FrontierV3NavigationScope scope) {
        var proof = PROOFS.get(actor);
        if (path == null || proof == null || proof.level().get() != level || !proof.matches(path, scope)) {
            var rejected = FrontierV3PhysicalPathPolicy.reject(level, path, scope);
            if (rejected.isPresent()) { PROOFS.remove(actor); return rejected; }
            if (!FrontierV3PathClearance.path(level, actor, path)) return Optional.of(new FrontierV3PhysicalPathPolicy.Rejection(
                    FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE, "body-clearance"));
            proof = retain(level, path, scope); PROOFS.put(actor, proof);
        }
        if (!path.canReach()) return Optional.of(new FrontierV3PhysicalPathPolicy.Rejection(
                FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE, "unreachable"));
        // The future immutable scope proof does not imply that a chunk stays loaded.
        // Reobserve the next two steps and destination before ordinary physical integration.
        int first = path.getNextNodeIndex(), end = Math.min(proof.nodes().length, first + 2);
        for (int index = first; index < end; index++) {
            var rejected = currentNode(level, proof.nodes()[index], index);
            if (rejected.isPresent()) return rejected;
            if (index > 0 && !FrontierV3PathClearance.edge(level, actor, proof.nodes()[index - 1], proof.nodes()[index]))
                return Optional.of(new FrontierV3PhysicalPathPolicy.Rejection(
                        FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE, "edge-clearance:" + index));
        }
        return currentNode(level, proof.nodes()[proof.nodes().length - 1], proof.nodes().length - 1);
    }
    private static Optional<FrontierV3PhysicalPathPolicy.Rejection> currentNode(ServerLevel level, BlockPos feet, int index) {
        if (!level.hasChunkAt(feet)) return Optional.of(new FrontierV3PhysicalPathPolicy.Rejection(
                FrontierV3GoalNavigation.BlockReason.TARGET_CHUNK_UNLOADED, "unloaded-node:" + index));
        if (!level.getFluidState(feet).isEmpty()) return Optional.of(new FrontierV3PhysicalPathPolicy.Rejection(
                FrontierV3GoalNavigation.BlockReason.UNSUPPORTED_MEDIUM, "fluid-node:" + index));
        return Optional.empty();
    }
    private static Proof retain(ServerLevel level, Path path, FrontierV3NavigationScope scope) {
        var nodes = new BlockPos[path.getNodeCount()]; var remaining = new double[nodes.length];
        for (int index = 0; index < nodes.length; index++) nodes[index] = path.getNode(index).asBlockPos();
        for (int index = nodes.length - 2; index >= 0; index--) remaining[index] = remaining[index + 1]
                + Math.hypot((double) nodes[index + 1].getX() - nodes[index].getX(), (double) nodes[index + 1].getZ() - nodes[index].getZ());
        return new Proof(new java.lang.ref.WeakReference<>(level), path, scope, nodes, remaining);
    }
    static double remainingDistance(Mob actor, Path path, Vec3 position) {
        var proof = PROOFS.get(actor);
        if (proof == null || proof.level().get() != actor.level() || proof.path() != path || !proof.matches(path, proof.scope()))
            return FrontierV3MinecraftGoalNavigation.remainingDistance(path, position);
        int next = path.getNextNodeIndex();
        if (next >= proof.nodes().length) return 0;
        var node = proof.nodes()[next];
        return Math.hypot(node.getX() + .5 - position.x, node.getZ() + .5 - position.z) + proof.remaining()[next];
    }
    static void forget(Mob actor) { PROOFS.remove(actor); }
}
