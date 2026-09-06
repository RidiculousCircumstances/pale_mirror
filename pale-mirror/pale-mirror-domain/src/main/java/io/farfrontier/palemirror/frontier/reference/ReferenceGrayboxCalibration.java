package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source-shaped yearly measurements for the individual {@code graybox_1_40}
 * profile. This class owns no policy: the source-generated Python fixture
 * declares both the expected measurements and their allowed drift.
 */
final class ReferenceGrayboxCalibration {
    private static final List<ReferenceResource> SHORTAGE_RESOURCES = List.of(
            ReferenceResource.FOOD, ReferenceResource.MEDICINE, ReferenceResource.AMMO);

    private ReferenceGrayboxCalibration() { }

    static Run run(long seed, int days) {
        if (days < 1) throw new IllegalArgumentException("calibration days must be positive");
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(seed));
        double initialPopulation = world.settlements().values().stream().mapToDouble(ReferenceSettlement::population).sum();
        world.run(days);

        List<ReferenceDailyWorldHistory> history = world.history();
        if (history.isEmpty()) throw new IllegalStateException("graybox calibration recorded no history");
        ReferenceDailyWorldHistory finalRow = history.getLast();
        List<ReferenceTradeRecord> recentTrades = world.trade().history().stream()
                .filter(record -> record.day() > world.day() - 30).toList();
        List<ReferenceHiveHistoryEvent> destroyed = world.infection().projectHistory().stream()
                .filter(item -> item.kind().startsWith("destroyed:")).toList();
        List<ReferenceFrontCampaign> campaigns = new ArrayList<>(world.v2().frontCampaigns().values());
        List<ReferenceOperation> operations = new ArrayList<>(world.operations().active());
        operations.addAll(world.operations().completed());

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("alive_settlements", (double) world.settlements().values().stream().filter(ReferenceSettlement::alive).count());
        values.put("population_ratio", survivingPopulation(world) / initialPopulation);
        values.put("recent_trade_value", recentTrades.stream().mapToDouble(ReferenceTradeRecord::value).sum());
        values.put("recent_trade_count", (double) recentTrades.size());
        for (ReferenceResource resource : SHORTAGE_RESOURCES) {
            values.put("peak_shortage_" + resource.name().toLowerCase(), peakShortage(world, resource));
        }
        values.put("peak_infection", history.stream().mapToDouble(ReferenceDailyWorldHistory::infection).max().orElse(0.0d));
        values.put("peak_illness_burden", world.settlementHistory().values().stream().flatMap(List::stream)
                .mapToDouble(ReferenceDailySettlementHistory::illnessBurden).max().orElse(0.0d));
        values.put("peak_active_nests", (double) history.stream().mapToInt(ReferenceDailyWorldHistory::nests).max().orElse(0));
        values.put("peak_active_bioforms", (double) history.stream().mapToInt(item -> (int) item.bioforms()).max().orElse(0));
        values.put("peak_contaminated_sites", (double) history.stream().mapToInt(ReferenceDailyWorldHistory::contaminatedSites).max().orElse(0));
        values.put("peak_hive_biomass", history.stream().mapToDouble(ReferenceDailyWorldHistory::hiveBiomass).max().orElse(0.0d));
        values.put("final_ecological_scar", finalRow.ecologyScar());
        values.put("harvested_biomass", world.infection().harvestedBiomass());
        values.put("destroyed_organs", (double) destroyed.size());
        values.put("destroyed_cores", (double) destroyed.stream().filter(item -> item.kind().equals("destroyed:core")).count());
        values.put("remaining_cores", (double) world.infection().organs().values().stream()
                .filter(item -> item.kind() == ReferenceOrganKind.CORE).count());
        values.put("refugee_groups", (double) world.events().stream().filter(item -> item.contains("refugees left")).count());
        values.put("wounded_personnel", world.settlements().values().stream().mapToDouble(ReferenceSettlement::woundedPersonnel).sum());
        values.put("operations_formed", (double) operations.size());
        values.put("operations_completed", (double) world.operations().completed().size());
        values.put("field_posts_built", (double) world.field().posts().size());
        values.put("field_posts_lost", (double) world.field().posts().values().stream()
                .filter(item -> item.status() == ReferenceFieldPostStatus.ABANDONED
                        || item.status() == ReferenceFieldPostStatus.OVERRUN
                        || item.status() == ReferenceFieldPostStatus.DISMANTLED)
                .count());
        values.put("field_engagements", (double) (world.field().engagements().size() + world.field().completedEngagements().size()));
        values.put("front_campaigns_started", (double) campaigns.size());
        values.put("front_campaigns_completed", (double) campaigns.stream()
                .filter(item -> item.terminalOutcome() == ReferenceFrontPhase.COMPLETE).count());
        values.put("front_campaigns_withdrawn", (double) campaigns.stream()
                .filter(item -> item.terminalOutcome() == ReferenceFrontPhase.WITHDRAW).count());
        values.put("front_campaigns_failed", (double) campaigns.stream()
                .filter(item -> item.terminalOutcome() == ReferenceFrontPhase.FAILED).count());
        return new Run(seed, world.day(), values);
    }

    static Summary summarize(List<Run> runs) {
        List<Run> required = List.copyOf(runs);
        if (required.isEmpty()) throw new IllegalArgumentException("graybox calibration needs at least one run");
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("minimum_alive_settlements_per_run", required.stream().mapToDouble(item -> item.metric("alive_settlements")).min().orElseThrow());
        values.put("trade_active_runs", (double) required.stream().filter(item -> item.metric("recent_trade_count") > 0.0d).count());
        values.put("median_population_ratio", median(required.stream().map(item -> item.metric("population_ratio")).toList()));
        values.put("median_peak_active_nests", median(required.stream().map(item -> item.metric("peak_active_nests")).toList()));
        values.put("median_peak_active_bioforms", median(required.stream().map(item -> item.metric("peak_active_bioforms")).toList()));
        values.put("median_destroyed_organs", median(required.stream().map(item -> item.metric("destroyed_organs")).toList()));
        values.put("median_final_ecological_scar", median(required.stream().map(item -> item.metric("final_ecological_scar")).toList()));
        values.put("median_front_campaigns_started", median(required.stream().map(item -> item.metric("front_campaigns_started")).toList()));
        values.put("median_front_campaigns_completed", median(required.stream().map(item -> item.metric("front_campaigns_completed")).toList()));
        values.put("front_campaign_failed_runs", (double) required.stream()
                .filter(item -> item.metric("front_campaigns_failed") > 0.0d).count());
        return new Summary(values);
    }

    private static double survivingPopulation(ReferenceWorld world) {
        return world.settlements().values().stream().filter(ReferenceSettlement::alive)
                .mapToDouble(ReferenceSettlement::population).sum();
    }

    private static double peakShortage(ReferenceWorld world, ReferenceResource resource) {
        return world.settlementHistory().values().stream().flatMap(List::stream)
                .filter(ReferenceDailySettlementHistory::alive)
                .map(ReferenceDailySettlementHistory::resources)
                .map(values -> values.get(resource))
                .filter(value -> value != null && value.target() > 0.0d)
                .mapToDouble(value -> 1.0d - Math.min(1.0d, value.amount() / value.target()))
                .max().orElse(0.0d);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0d : sorted.get(middle);
    }

    record Run(long seed, int daysSimulated, Map<String, Double> metrics) {
        Run {
            metrics = Map.copyOf(metrics);
            if (metrics.isEmpty() || metrics.values().stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException("graybox calibration metrics must be finite and non-empty");
            }
        }

        double metric(String name) {
            Double value = metrics.get(name);
            if (value == null) throw new IllegalArgumentException("unknown graybox calibration metric: " + name);
            return value;
        }
    }

    record Summary(Map<String, Double> metrics) {
        Summary {
            metrics = Map.copyOf(metrics);
        }

        double metric(String name) {
            Double value = metrics.get(name);
            if (value == null) throw new IllegalArgumentException("unknown graybox calibration summary metric: " + name);
            return value;
        }
    }
}
