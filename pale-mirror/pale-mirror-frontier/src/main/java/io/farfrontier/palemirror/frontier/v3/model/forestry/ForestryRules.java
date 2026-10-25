package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement;
import java.util.*;

/** Hashed content and policy. No timers, stock, people or generator instances. */
public record ForestryRules(List<Patch> patches, int reserveItems, int resumeBelowItems,
                            int workersPerSettlement, int cargoItems, long reviewTicks,
                            ToolRequirement tool, List<TreeHarvestProfile> harvests) {
    public record Patch(String id, SubjectId knownBy, int innerRadius, int outerRadius,
                        int spacing, int densityPermille, long salt) {
        public Patch {
            Objects.requireNonNull(knownBy);
            if (id == null || !id.matches("[a-z0-9_]+:[a-z0-9_./-]+") || innerRadius < 32
                    || outerRadius <= innerRadius || outerRadius > 256 || spacing < 5 || spacing > 32
                    || densityPermille < 1 || densityPermille > 1000)
                throw new IllegalArgumentException("invalid authored forest patch");
        }
        String canonicalText() { return id + ":" + knownBy.value() + ":" + innerRadius + ":" + outerRadius
                + ":" + spacing + ":" + densityPermille + ":" + salt; }
    }
    public ForestryRules {
        patches = List.copyOf(patches); Objects.requireNonNull(tool); harvests = List.copyOf(harvests);
        if (patches.size() > 32 || patches.stream().map(Patch::id).distinct().count() != patches.size()
                || reserveItems < 1 || reserveItems > 65536 || resumeBelowItems < 1 || resumeBelowItems > reserveItems
                || workersPerSettlement < 1 || workersPerSettlement > 8 || cargoItems < 1 || cargoItems > 64
                || reviewTicks < 1 || harvests.isEmpty() || harvests.size() > 32
                || harvests.stream().map(TreeHarvestProfile::id).distinct().count() != harvests.size()
                || harvests.stream().map(profile -> profile.species() + "|" + profile.logKind()).distinct().count() != harvests.size())
            throw new IllegalArgumentException("invalid bounded forestry policy");
    }
    public boolean enabled() { return !patches.isEmpty(); }
    public static ForestryRules disabled() {
        return new ForestryRules(List.of(), 256, 192, 2, 64, 200,
                new ToolRequirement("pale_mirror:chop", 1, 1), initialHarvests());
    }
    public static ForestryRules graybox() {
        return new ForestryRules(List.of(
                new Patch("pale_mirror:clearwater_forest", new SubjectId("settlement:7"), 64, 144, 7, 680, 103),
                new Patch("pale_mirror:dawnbridge_forest", new SubjectId("settlement:3"), 72, 128, 9, 530, 211),
                new Patch("pale_mirror:hearthvale_forest", new SubjectId("settlement:6"), 80, 152, 8, 610, 307)),
                256, 192, 2, 64, 20, new ToolRequirement("pale_mirror:chop", 1, 1), initialHarvests());
    }
    private static List<TreeHarvestProfile> initialHarvests() {
        return List.of(new TreeHarvestProfile("pale_mirror:oak_felling_v1", "minecraft:oak", "minecraft:oak_log",
                "minecraft:blocks/oak_log", "minecraft:oak_log"));
    }
    public TreeHarvestProfile requireHarvest(String species, String logKind) {
        return harvests.stream().filter(profile -> profile.species().equals(species) && profile.logKind().equals(logKind))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("tree material has no declared harvest capability"));
    }
    public String canonicalText() {
        return patches.stream().sorted(Comparator.comparing(Patch::id)).map(Patch::canonicalText)
                .collect(java.util.stream.Collectors.joining(";")) + "|" + reserveItems + ":" + resumeBelowItems
                + ":" + workersPerSettlement + ":" + cargoItems + ":" + reviewTicks + ":" + tool.canonicalText() + "|"
                + harvests.stream().sorted(Comparator.comparing(TreeHarvestProfile::id)).map(TreeHarvestProfile::canonicalText)
                    .collect(java.util.stream.Collectors.joining(";"));
    }
}
