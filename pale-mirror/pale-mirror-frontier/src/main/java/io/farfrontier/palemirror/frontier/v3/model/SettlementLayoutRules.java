package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinitionFormat;

import java.util.*;

/** Content policy pinned into the ruleset digest, not a per-tick mutable planner setting. */
public record SettlementLayoutRules(
        int streetExtent,
        int streetHalfWidth,
        int plotGap,
        int spareBeds,
        int smallHouseCount,
        int departureLength,
        int globalRoadInset) {
    private static final byte[] SOURCE = read();
    private static final SettlementLayoutRules STANDARD = parse();

    public SettlementLayoutRules {
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

    private static SettlementLayoutRules parse() {
        var p = BuildingDefinitionFormat.properties(SOURCE);
        return new SettlementLayoutRules(
                Integer.parseInt(p.get("streetExtent")),
                Integer.parseInt(p.get("streetHalfWidth")),
                Integer.parseInt(p.get("plotGap")),
                Integer.parseInt(p.get("spareBeds")),
                Integer.parseInt(p.get("smallHouseCount")),
                Integer.parseInt(p.get("departureLength")),
                Integer.parseInt(p.get("globalRoadInset")));
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
