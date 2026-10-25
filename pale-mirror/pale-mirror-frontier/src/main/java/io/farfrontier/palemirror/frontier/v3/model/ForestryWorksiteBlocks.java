package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.util.*;

/** Forest-owned exact log addresses for the shared worksite witness/projection protocol.
 * Untouched forest is queried regionally, never flattened into a persistent log index. */
public final class ForestryWorksiteBlocks {
    private static final BlockExtraction.Block AIR = new BlockExtraction.Block("minecraft:air", Map.of());
    private ForestryWorksiteBlocks() { }
    public static WorksiteBlock cell(ForestTree tree, int log) {
        if (log < 0 || log >= tree.declaration().logs().size()) throw new IllegalArgumentException("foreign forest worksite log");
        return new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.FOREST_TREE, ForestrySourceCustody.object(tree.reference()),
                WorksiteBlock.Role.RESOURCE, log + 1), tree.declaration().logs().position(tree.declaration().origin(), log), tree.revision(),
                tree.extracted(log) ? AIR : tree.declaration().logs().block(log));
    }
    public static ForestTree tree(FrontierWorldState state, WorksiteBlock declaration) {
        if (declaration.key().family() != CellMutationKey.OwnerFamily.FOREST_TREE || declaration.key().role() != WorksiteBlock.Role.RESOURCE)
            throw new IllegalArgumentException("forest worksite carries a foreign nominal family/role");
        var tree = ForestKnowledge.touching(state, ForestRegion.containing(declaration.position())).stream()
                .filter(candidate -> ForestrySourceCustody.object(candidate.reference()).equals(declaration.key().owner()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("forest worksite lost its exact declared source object"));
        int log = Math.toIntExact(declaration.key().cell() - 1);
        if (log < 0 || log >= tree.declaration().logs().size()
                || !tree.declaration().logs().position(tree.declaration().origin(), log).equals(declaration.position()))
            throw new IllegalArgumentException("forest worksite log identity does not match its declaration");
        return tree;
    }
    public static Optional<WorksiteBlock> at(FrontierWorldState state, BlockPosition position) {
        WorksiteBlock found = null;
        for (var tree : ForestKnowledge.touching(state, ForestRegion.containing(position))) {
            int log = tree.declaration().logs().indexOf(tree.declaration().origin(), position);
            if (log < 0 || !tree.present(log) && !tree.extracted(log)) continue;
            if (found != null) throw new IllegalArgumentException("competing managed tree generations");
            found = cell(tree, log);
        }
        return Optional.ofNullable(found);
    }
}
