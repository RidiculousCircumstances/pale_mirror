package io.farfrontier.palemirror.frontier.reference;

/**
 * Exact scale-policy port of Python {@code simulation.profiles}.
 *
 * <p>Only a profile may translate an extensive source-human quantity. Domain
 * rules receive the translated amount and never infer a hidden cohort ratio
 * from a materialized Villager or Zombie.</p>
 */
public record ReferenceSimulationProfile(
        String id,
        int personScale,
        boolean discretePeople,
        int minimumSurvivingSettlement
) {
    public static final ReferenceSimulationProfile SOURCE_V2 = new ReferenceSimulationProfile("source_v2", 1, false, 1);
    public static final ReferenceSimulationProfile GRAYBOX_1_40 = new ReferenceSimulationProfile("graybox_1_40", 40, true, 2);

    public ReferenceSimulationProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("profile id is required");
        }
        if (personScale < 1) {
            throw new IllegalArgumentException("person scale must be positive");
        }
        if (minimumSurvivingSettlement < 1) {
            throw new IllegalArgumentException("minimum surviving settlement must be positive");
        }
    }

    /** Python {@code people_from_source}: half-up, never banker's rounding. */
    public double peopleFromSource(double sourcePeople) {
        requireFiniteNonNegative(sourcePeople, "source people");
        if (!discretePeople) {
            return sourcePeople;
        }
        return (double) roundedHalfUp(sourcePeople / personScale);
    }

    /** Converts a source population into a count of individual graybox records. */
    public int individualPeopleFromSource(double sourcePeople) {
        if (!discretePeople) {
            throw new IllegalStateException("the continuous source profile has no individual record count");
        }
        return (int) peopleFromSource(sourcePeople);
    }

    /** Python {@code human_amount_from_source}. */
    public double humanAmountFromSource(double sourceAmount) {
        if (!Double.isFinite(sourceAmount)) {
            throw new IllegalArgumentException("source human amount must be finite");
        }
        return discretePeople ? sourceAmount / personScale : sourceAmount;
    }

    /** Python {@code collapse_population_from_source}. */
    public double collapsePopulationFromSource(double sourcePopulation) {
        requireFiniteNonNegative(sourcePopulation, "source collapse population");
        if (!discretePeople) {
            return sourcePopulation;
        }
        return (double) Math.max(minimumSurvivingSettlement, roundedHalfUp(sourcePopulation / personScale));
    }

    /** Converts the graybox collapse threshold into its integral comparison boundary. */
    public int individualCollapsePopulationFromSource(double sourcePopulation) {
        if (!discretePeople) {
            throw new IllegalStateException("the continuous source profile has no individual collapse boundary");
        }
        return (int) collapsePopulationFromSource(sourcePopulation);
    }

    private static int roundedHalfUp(double value) {
        if (value > Integer.MAX_VALUE - 0.5) {
            throw new IllegalArgumentException("scaled person count exceeds Java int range");
        }
        return (int) Math.floor(value + 0.5d);
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
