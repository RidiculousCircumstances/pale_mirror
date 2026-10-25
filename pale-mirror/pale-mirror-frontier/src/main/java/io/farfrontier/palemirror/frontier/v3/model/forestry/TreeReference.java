package io.farfrontier.palemirror.frontier.v3.model.forestry;

import java.util.Objects;

/** Exact natural generation plus declared storage address, not a settlement ownership claim. */
public record TreeReference(TreeId id, long generation, ForestRegion region) {
    public TreeReference { Objects.requireNonNull(id); Objects.requireNonNull(region);
        if (generation < 1) throw new IllegalArgumentException("tree reference needs its explicit generation"); }
    public static TreeReference declaredBy(TreeDescriptor tree) {
        return new TreeReference(tree.id(), tree.generation(), tree.owningRegion());
    }
    public boolean matches(TreeDescriptor tree) {
        return id.equals(tree.id()) && generation == tree.generation() && region.equals(tree.owningRegion());
    }
}
