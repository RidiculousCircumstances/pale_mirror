package io.farfrontier.palemirror.frontier.reference;

/** One reporting-only daily MarketEconomy snapshot from the Python history list. */
public record ReferenceMarketHistoryEntry(
        int day,
        String season,
        int companies,
        int activeContracts,
        double credit,
        double employment,
        int projects
) { }
