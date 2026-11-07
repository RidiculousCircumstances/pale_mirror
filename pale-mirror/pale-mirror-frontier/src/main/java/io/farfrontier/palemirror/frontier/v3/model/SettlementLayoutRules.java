package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinitionFormat;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingCatalog;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingStyleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.*;

/** Content policy pinned into the ruleset digest, not a per-tick mutable planner setting. */
public record SettlementLayoutRules(
        int streetExtent,
        int streetHalfWidth,
        int plotGap,
        int spareBeds,
        int smallHouseCount,
        int departureLength,
        int globalRoadInset,
        Map<SubjectId, BuildingStyleId> settlementStyles) {
    private static final byte[] SOURCE = read();
    private static final SettlementLayoutRules STANDARD = parse();

    public SettlementLayoutRules {
        settlementStyles = Map.copyOf(settlementStyles);
        if (settlementStyles.size() > SimulationCapacity.MAX_SETTLEMENTS)
            throw new IllegalArgumentException("unbounded settlement content selections");
        if (streetExtent < 32
                || streetExtent > 160
                || streetHalfWidth < 1
                || streetHalfWidth > 4
                || plotGap < 1
                || plotGap > 8
                || spareBeds < 0
                || spareBeds > 16
                || smallHouseCount < 1
                || smallHouseCount > 8
                || departureLength < 4
                || departureLength > 32
                || globalRoadInset < 2
                || globalRoadInset > 32)
            throw new IllegalArgumentException("invalid settlement layout policy");
    }

    public static SettlementLayoutRules standard() {
        return STANDARD;
    }

    public static String canonicalText() {
        return new String(SOURCE, java.nio.charset.StandardCharsets.UTF_8);
    }

    public BuildingStyleId styleFor(SubjectId settlement) {
        return settlementStyles.getOrDefault(settlement, BuildingCatalog.defaultStyle());
    }

    private static SettlementLayoutRules parse() {
        var p = BuildingDefinitionFormat.properties(SOURCE);
        var styles = new HashMap<SubjectId, BuildingStyleId>();
        for (var selection : p.get("settlementStyles").split(",")) {
            if (selection.isBlank()) continue;
            var parts = selection.split("@", -1);
            if (parts.length != 2 || styles.putIfAbsent(new SubjectId(parts[0]),
                    new BuildingStyleId(parts[1])) != null)
                throw new IllegalArgumentException("invalid settlement style selection");
        }
        return new SettlementLayoutRules(
                Integer.parseInt(p.get("streetExtent")),
                Integer.parseInt(p.get("streetHalfWidth")),
                Integer.parseInt(p.get("plotGap")),
                Integer.parseInt(p.get("spareBeds")),
                Integer.parseInt(p.get("smallHouseCount")),
                Integer.parseInt(p.get("departureLength")),
                Integer.parseInt(p.get("globalRoadInset")), styles);
    }

    private static byte[] read() {
        try (var in =
                SettlementLayoutRules.class.getResourceAsStream(
                        "/io/farfrontier/palemirror/frontier/v3/buildings/settlement-layout.properties")) {
            return Objects.requireNonNull(in, "missing settlement layout policy").readAllBytes();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
