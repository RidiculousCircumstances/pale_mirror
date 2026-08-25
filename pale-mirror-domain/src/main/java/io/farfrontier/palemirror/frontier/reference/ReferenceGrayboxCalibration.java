package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bounded behavioural measurements for the individual {@code graybox_1_40}
 * profile.
 *
 * <p>This intentionally reports observable world outcomes rather than a
 * canonical-state hash. {@code tools/frontier/generate_graybox_calibration_envelope.py}
 * produces the matching Python evidence and declares the acceptance bands.
 * It is not read by canonical rules and cannot influence a simulation run.</p>
 */
final class ReferenceGrayboxCalibration {
    private ReferenceGrayboxCalibration() { }

    static Run run(long seed, int days) {
        if (days < 1) throw new IllegalArgumentException("calibration days must be positive");
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(seed));
        double initialPopulation = world.settlements().values().stream().mapToDouble(ReferenceSettlement::population).sum();
        world.run(days);
        List<ReferenceDailyWorldHistory> history = world.history();
        if (history.isEmpty()) throw new IllegalStateException("graybox calibration recorded no history");
        ReferenceDailyWorldHistory finalRow = history.getLast();
        double peakIllness = world.settlementHistory().values().stream().flatMap(List::stream)
                .mapToDouble(ReferenceDailySettlementHistory::illnessBurden).max().orElse(0.0d);
        int destroyedOrgans = (int) world.infection().projectHistory().stream()
                .filter(item -> item.kind().startsWith("destroyed:")).count();
        List<ReferenceFrontCampaign> campaigns = new ArrayList<>(world.v2().frontCampaigns().values());
        return new Run(seed, world.day(), finalRow.alive(), finalRow.population() / initialPopulation,
                finalRow.trade30d(), history.stream().mapToDouble(ReferenceDailyWorldHistory::infection).max().orElse(0.0d),
                peakIllness, history.stream().mapToInt(ReferenceDailyWorldHistory::nests).max().orElse(0),
                history.stream().mapToDouble(ReferenceDailyWorldHistory::bioforms).max().orElse(0.0d),
                history.stream().mapToInt(ReferenceDailyWorldHistory::contaminatedSites).max().orElse(0),
                history.stream().mapToDouble(ReferenceDailyWorldHistory::hiveBiomass).max().orElse(0.0d),
                finalRow.ecologyScar(), destroyedOrgans,
                (int) world.infection().organs().values().stream().filter(item -> item.kind() == ReferenceOrganKind.CORE).count(),
                campaigns.size(), (int) campaigns.stream().filter(item -> item.terminalOutcome() == ReferenceFrontPhase.COMPLETE).count(),
                (int) campaigns.stream().filter(item -> item.terminalOutcome() == ReferenceFrontPhase.FAILED).count());
    }

    static Summary summarize(List<Run> runs) {
        List<Run> required = List.copyOf(runs);
        if (required.isEmpty()) throw new IllegalArgumentException("graybox calibration needs at least one run");
        return new Summary(required.size(), required.stream().mapToInt(Run::aliveSettlements).min().orElseThrow(),
                (int) required.stream().filter(item -> item.recentTradeValue() > 0.0d).count(),
                median(required.stream().map(Run::populationRatio).toList()),
                median(required.stream().map(item -> (double) item.peakActiveNests()).toList()),
                median(required.stream().map(Run::peakActiveBioforms).toList()),
                median(required.stream().map(item -> (double) item.destroyedOrgans()).toList()),
                median(required.stream().map(Run::finalEcologicalScar).toList()),
                median(required.stream().map(item -> (double) item.frontCampaignsStarted()).toList()),
                median(required.stream().map(item -> (double) item.frontCampaignsCompleted()).toList()),
                (int) required.stream().filter(item -> item.frontCampaignsFailed() > 0).count());
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0d : sorted.get(middle);
    }

    record Run(
            long seed,
            int daysSimulated,
            int aliveSettlements,
            double populationRatio,
            double recentTradeValue,
            double peakInfection,
            double peakIllnessBurden,
            int peakActiveNests,
            double peakActiveBioforms,
            int peakContaminatedSites,
            double peakHiveBiomass,
            double finalEcologicalScar,
            int destroyedOrgans,
            int remainingCores,
            int frontCampaignsStarted,
            int frontCampaignsCompleted,
            int frontCampaignsFailed
    ) { }

    record Summary(
            int runs,
            int minimumAliveSettlementsPerRun,
            int tradeActiveRuns,
            double medianPopulationRatio,
            double medianPeakActiveNests,
            double medianPeakActiveBioforms,
            double medianDestroyedOrgans,
            double medianFinalEcologicalScar,
            double medianFrontCampaignsStarted,
            double medianFrontCampaignsCompleted,
            int frontCampaignFailedRuns
    ) { }
}
