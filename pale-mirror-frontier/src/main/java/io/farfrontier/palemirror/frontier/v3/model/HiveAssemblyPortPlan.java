package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable semantic departure port for a task-owned hive group.
 *
 * <p>A Ganglion has four exterior throats in the graybox grammar.  This plan
 * chooses exactly one from the retained target, then assigns distinct outside
 * staging surfaces.  Consumers use the retained surfaces, never an anchor
 * offset or a fresh nearest-target lookup.</p>
 */
public final class HiveAssemblyPortPlan {
    private HiveAssemblyPortPlan() { }

    public static Port compile(FrontierBootstrap bootstrap, HiveColony colony, HiveMobilization mobilization) {
        Objects.requireNonNull(bootstrap, "hive assembly bootstrap");
        Objects.requireNonNull(colony, "hive assembly colony");
        Objects.requireNonNull(mobilization, "hive assembly mobilization");
        HiveOrgan ganglion = java.util.stream.Stream.concat(bootstrap.hive().organs().stream(), colony.addedOrgans().values().stream())
                .filter(organ -> organ.nestId().equals(mobilization.nestId()) && organ.kind() == HiveOrganKind.GANGLION)
                .sorted(Comparator.comparing(HiveOrgan::id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hive assembly needs one same-nest Ganglion"));
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(mobilization.settlementId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("hive assembly target settlement is absent"));
        Direction direction = Direction.towards(ganglion.anchor(), settlement.anchor());
        Map<SubjectId, SurfaceAnchor> slots = new LinkedHashMap<>();
        List<Integer> lateral = lateralOffsets(mobilization.memberIds().size());
        for (int index = 0; index < mobilization.memberIds().size(); index++) {
            SubjectId member = mobilization.memberIds().get(index);
            int x = ganglion.anchor().x() + direction.dx * 3 + direction.lateralX * lateral.get(index);
            int z = ganglion.anchor().z() + direction.dz * 3 + direction.lateralZ * lateral.get(index);
            if (!bootstrap.bounds().contains(new BlockPosition(x, ganglion.anchor().y(), z))) {
                throw new IllegalArgumentException("hive assembly staging surface is outside world bounds");
            }
            slots.put(member, SurfaceAnchor.at(x, bootstrap.terrain().supportYAt(x, z), z));
        }
        return new Port(ganglion.id(), slots);
    }

    private static List<Integer> lateralOffsets(int count) {
        if (count < 1 || count > HiveTaskAssembly.MAX_MEMBERS) throw new IllegalArgumentException("hive staging count is invalid");
        List<Integer> offsets = new ArrayList<>(count);
        for (int offset = 0; offsets.size() < count; offset++) {
            offsets.add(offset);
            if (offset != 0 && offsets.size() < count) offsets.add(-offset);
        }
        return List.copyOf(offsets);
    }

    public record Port(SubjectId ganglionId, Map<SubjectId, SurfaceAnchor> memberStagingSurfaces) {
        public Port {
            ganglionId = Objects.requireNonNull(ganglionId, "hive assembly Ganglion");
            Map<SubjectId, SurfaceAnchor> copy = new LinkedHashMap<>();
            Objects.requireNonNull(memberStagingSurfaces, "hive assembly staging surfaces").forEach((member, surface) -> {
                if (copy.put(Objects.requireNonNull(member, "hive staging member"), Objects.requireNonNull(surface, "hive staging surface")) != null) {
                    throw new IllegalArgumentException("duplicate hive staging member");
                }
            });
            if (copy.isEmpty() || copy.size() > HiveTaskAssembly.MAX_MEMBERS
                    || copy.values().stream().distinct().count() != copy.size()) {
                throw new IllegalArgumentException("hive assembly staging surfaces are invalid");
            }
            memberStagingSurfaces = Map.copyOf(copy);
        }
    }

    private enum Direction {
        NORTH(0, -1, 1, 0), EAST(1, 0, 0, 1), SOUTH(0, 1, 1, 0), WEST(-1, 0, 0, 1);
        private final int dx, dz, lateralX, lateralZ;
        Direction(int dx, int dz, int lateralX, int lateralZ) {
            this.dx = dx; this.dz = dz; this.lateralX = lateralX; this.lateralZ = lateralZ;
        }
        private static Direction towards(BlockPosition source, BlockPosition target) {
            long dx = (long) target.x() - source.x(), dz = (long) target.z() - source.z();
            if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? EAST : WEST;
            return dz >= 0 ? SOUTH : NORTH;
        }
    }
}
