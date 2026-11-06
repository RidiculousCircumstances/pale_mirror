package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;
import java.util.*;

/** Immutable admitted site works. The same exact cells feed genesis, COLD geometry and physical execution. */
public record BuildingSitePlan(BuildingPlacement placement, int groundY, Map<BlockPosition, GrayboxCell> preparation,
                               int removedBlocks, int placedBlocks) {
    public BuildingSitePlan { preparation = Map.copyOf(preparation); }
    public static final class GeometryPending extends IllegalArgumentException {
        public GeometryPending(String reason) { super(reason); }
    }
    private static final int MAX_COLUMNS = 4096, MAX_WORK_CELLS = 65536, MAX_RELIEF = 8;
    private static final ImmutableResultView<SettlementStructure, BuildingSitePlan> CACHE = new ImmutableResultView<>(1024);
    private static final StructureMaterial DIRT = new StructureMaterial.Authored(new NavigationBlockState("minecraft:dirt", Map.of()), "minecraft:dirt");
    private static final StructureMaterial AIR = new StructureMaterial.Authored(new NavigationBlockState("minecraft:air", Map.of()), "minecraft:air");
    public static BuildingSitePlan compile(TerrainSurfacePlan terrain, SettlementStructure structure) {
        return CACHE.get(List.of(terrain), structure, () -> compile(BuildingSiteSurvey.terrain(terrain), structure));
    }
    public static BuildingSitePlan compile(BuildingSiteSurvey survey, SettlementStructure structure) {
        var definition = structure.definition(); var placement = structure.placement();
        var box = placement.volume(definition.fabric());
        long columns = (long) (box.max().x() - box.min().x()) * (box.max().z() - box.min().z());
        if (columns > MAX_COLUMNS) throw new IllegalArgumentException("building site exceeds bounded survey");
        int grade = structure.anchor().y() + definition.site().groundY() - definition.origin().y();
        var uniform = survey.uniformClearSite(box);
        var cells = new LinkedHashMap<BlockPosition, GrayboxCell>(); int cut = 0, fill = 0;
        var target = new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE, structure.id());
        if (uniform.isEmpty() || uniform.getAsInt() != grade)
          for (int x = box.min().x(); x < box.max().x(); x++) for (int z = box.min().z(); z < box.max().z(); z++) {
            int natural = survey.groundAt(x,z).orElseThrow(() -> new GeometryPending("site ground is not observed")).y();
            for (int y = Math.min(natural,grade); y < Math.max(box.max().y(), natural+1); y++) {
                var actual = survey.cellAt(new BlockPosition(x,y,z)).orElseThrow(() -> new GeometryPending("site volume is not observed"));
                if (!actual.dry()) throw new IllegalArgumentException("site preparation enters an unsafe medium");
                if (y > Math.max(grade,natural) && !actual.shape().equals(io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationShape.EMPTY))
                    throw new IllegalArgumentException("site volume has an undeclared overhead obstruction");
            }
            if (Math.abs(natural - grade) > MAX_RELIEF) throw new IllegalArgumentException("building site requires excessive earthwork");
            for (int y = natural + 1; y <= grade; y++) {
                var p = new BlockPosition(x,y,z); cells.put(p, new GrayboxCell(p,target,DIRT,GrayboxSemanticPart.FOUNDATION)); fill++;
            }
            for (int y = grade + 1; y <= natural; y++) {
                var p = new BlockPosition(x,y,z); cells.put(p,new GrayboxCell(p,target,AIR,GrayboxSemanticPart.FOUNDATION)); cut++;
            }
        }
        // A clear outdoor plane says nothing about the required underground void.
        // Inspect exact source AIR only: an excavation's bounding box cannot erase
        // its retained walls, stairs or a neighbouring exterior terrain pocket.
        if (!definition.site().excavations().isEmpty()) for (var entry : definition.blocks().entrySet()) {
            var local = entry.getKey();
            if (local.y() > definition.site().groundY() || !entry.getValue().state().kind().equals("minecraft:air")) continue;
            if (definition.site().excavations().stream().noneMatch(volume -> volume.contains(local)))
                throw new IllegalArgumentException("below-grade void lacks an explicit site declaration");
            var position = placement.world(local);
            var actual = survey.cellAt(position).orElseThrow(() -> new GeometryPending("underground site volume is not observed"));
            if (!actual.dry()) throw new IllegalArgumentException("underground site preparation enters an unsafe medium");
            if (!actual.shape().equals(io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationShape.EMPTY)) {
                if (cells.put(position,new GrayboxCell(position,target,AIR,GrayboxSemanticPart.FOUNDATION)) == null) cut++;
            }
        }
        if (cells.size() > MAX_WORK_CELLS) throw new IllegalArgumentException("building site work exceeds bounded plan");
        return new BuildingSitePlan(placement, grade, cells, cut, fill);
    }
}
