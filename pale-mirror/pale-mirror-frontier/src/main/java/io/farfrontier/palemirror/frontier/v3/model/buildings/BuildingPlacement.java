package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;

import java.util.*;

/**
 * Sole local-to-world transform for blocks, annotations and block-state orientation. No world
 * access.
 */
public record BuildingPlacement(
        BlockPosition anchor,
        BlockPosition localOrigin,
        FacilityFacing authoredFacing,
        FacilityFacing facing) {
    public BuildingPlacement {
        Objects.requireNonNull(anchor);
        Objects.requireNonNull(localOrigin);
        Objects.requireNonNull(authoredFacing);
        Objects.requireNonNull(facing);
    }

    public int turns() {
        return Math.floorMod(directionIndex(facing) - directionIndex(authoredFacing), 4);
    }

    public BlockPosition world(BlockPosition local) {
        int x = local.x() - localOrigin.x(), z = local.z() - localOrigin.z();
        for (int i = 0; i < turns(); i++) {
            int prior = x;
            x = -z;
            z = prior;
        }
        return anchor.offset(x, local.y() - localOrigin.y(), z);
    }

    public BlockPosition local(BlockPosition world) {
        int x = world.x() - anchor.x(), z = world.z() - anchor.z();
        for (int i = 0; i < turns(); i++) {
            int prior = x;
            x = z;
            z = -prior;
        }
        return localOrigin.offset(x, world.y() - anchor.y(), z);
    }

    public BuildingVolume volume(BuildingVolume local) {
        int minX = Integer.MAX_VALUE,
                minY = Integer.MAX_VALUE,
                minZ = Integer.MAX_VALUE,
                maxX = Integer.MIN_VALUE,
                maxY = Integer.MIN_VALUE,
                maxZ = Integer.MIN_VALUE;
        for (int x : new int[] {local.min().x(), local.max().x() - 1})
            for (int y : new int[] {local.min().y(), local.max().y() - 1})
                for (int z : new int[] {local.min().z(), local.max().z() - 1}) {
                    var p = world(new BlockPosition(x, y, z));
                    minX = Math.min(minX, p.x());
                    maxX = Math.max(maxX, p.x());
                    minY = Math.min(minY, p.y());
                    maxY = Math.max(maxY, p.y());
                    minZ = Math.min(minZ, p.z());
                    maxZ = Math.max(maxZ, p.z());
                }
        return new BuildingVolume(
                new BlockPosition(minX, minY, minZ),
                new BlockPosition(maxX + 1, maxY + 1, maxZ + 1));
    }

    public SurfaceAnchor support(BlockPosition localSupport) {
        return new SurfaceAnchor(world(localSupport));
    }

    public FacilityFacing direction(FacilityFacing local) {
        return FacilityFacing.valueOf(rotate(local.name().toLowerCase(Locale.ROOT)).toUpperCase(Locale.ROOT));
    }

    public FacilityFacing localDirection(FacilityFacing world) {
        return FacilityFacing.values()[Math.floorMod(world.ordinal() - turns(), 4)];
    }

    public NavigationBlockState state(NavigationBlockState local) {
        var result = new TreeMap<String, String>();
        local.properties()
                .forEach(
                        (key, value) -> {
                            String rotatedKey = isDirection(key) ? rotate(key) : key;
                            String rotatedValue =
                                    key.equals("facing") && isDirection(value)
                                            ? rotate(value)
                                            : key.equals("axis")
                                                            && turns() % 2 == 1
                                                            && Set.of("x", "z").contains(value)
                                                    ? (value.equals("x") ? "z" : "x")
                                                    : key.equals("rotation")
                                                            ? Integer.toString(
                                                                    Math.floorMod(
                                                                            Integer.parseInt(value)
                                                                                    + 4 * turns(),
                                                                            16))
                                                            : key.equals("shape") ? rotatedShape(value) : value;
                            result.put(rotatedKey, rotatedValue);
                        });
        return new NavigationBlockState(local.kind(), result);
    }

    private String rotatedShape(String value) {
        for(int i=0;i<turns();i++)value=switch(value) {
            case "north_south" -> "east_west"; case "east_west" -> "north_south";
            case "ascending_north" -> "ascending_east";case "ascending_east" -> "ascending_south";
            case "ascending_south" -> "ascending_west";case "ascending_west" -> "ascending_north";
            case "south_east" -> "south_west";case "south_west" -> "north_west";
            case "north_west" -> "north_east";case "north_east" -> "south_east";
            default -> value;
        };
        return value;
    }

    private String rotate(String direction) {
        return List.of("north", "east", "south", "west")
                .get(
                        Math.floorMod(
                                List.of("north", "east", "south", "west").indexOf(direction)
                                        + turns(),
                                4));
    }

    private static boolean isDirection(String value) {
        return Set.of("north", "east", "south", "west").contains(value);
    }

    private static int directionIndex(FacilityFacing facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
        };
    }
}
