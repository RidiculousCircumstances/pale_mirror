package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockGeometry;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;
import java.util.Objects;

/** A land proposal carries actual soil and clearance predecessors; it is not a completed till operation. */
public record FieldDevelopmentCell(SurfaceAnchor support, KnownBlockGeometry.Sample<NavigationBlockState> soil,
                                   KnownBlockGeometry.Sample<NavigationBlockState> cropSpace) {
    public FieldDevelopmentCell {
        Objects.requireNonNull(support); Objects.requireNonNull(soil); Objects.requireNonNull(cropSpace);
        if (soil.permission() != KnownBlockGeometry.Sample.Permission.PUBLIC
                || cropSpace.permission() != KnownBlockGeometry.Sample.Permission.PUBLIC
                || !cropSpace.block().kind().equals("minecraft:air"))
            throw new IllegalArgumentException("cultivation cannot overwrite protected or occupied land");
        soilKind(soil.block());
    }
    public ResourceFieldCycle.Soil condition() { return soilKind(soil.block()); }
    public BlockPosition crop() { return support.support().offset(0, 1, 0); }
    private static ResourceFieldCycle.Soil soilKind(NavigationBlockState block) {
        return switch (block.kind()) {
            case "minecraft:dirt" -> ResourceFieldCycle.Soil.DIRT;
            case "minecraft:grass_block" -> ResourceFieldCycle.Soil.GRASS;
            case "minecraft:farmland" -> ResourceFieldCycle.Soil.FARMLAND;
            default -> throw new IllegalArgumentException("ground is not declared cultivable soil");
        };
    }
}
