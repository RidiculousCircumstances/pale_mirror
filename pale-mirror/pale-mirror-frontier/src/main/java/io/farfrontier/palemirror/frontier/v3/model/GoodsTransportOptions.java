package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.expedition.*;
import io.farfrontier.palemirror.frontier.v3.model.market.DeliveryCost;
import java.util.*;

/** Bounded transport offers, not reservations. The logistics owner revalidates
 * exact workers, asset, route and supplies when it admits the selected offer. */
public final class GoodsTransportOptions {
    private record Query(GoodsParticipant organizer, long duration, int parcels, int workers) { }
    private static final ImmutableInputView<BoundedValueMemo<Query, List<Option>>> OPTIONS = new ImmutableInputView<>();
    public record Option(int workers, Optional<TransportAsset> animal, DeliveryCost cost) {
        public Option {
            Objects.requireNonNull(animal); Objects.requireNonNull(cost);
            if (workers < 1 || animal.isPresent() && workers != 1)
                throw new IllegalArgumentException("invalid transport crew");
        }
        public long economicCost(long labourValuePerTick) {
            return Math.addExact(cost.resourceExpense().raw(), Math.multiplyExact(cost.workerTicks(), labourValuePerTick));
        }
    }
    public static List<Option> forLoad(FrontierWorldState state, GoodsParticipant organizer,
            long duration, int parcels, int availableWorkers) {
        if (duration < 0 || parcels < 1 || parcels > state.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumTripCargoStacks()
                || availableWorkers < 0) throw new IllegalArgumentException("invalid bounded transport offer");
        if (availableWorkers == 0) return List.of();
        return OPTIONS.get(List.of(state), () -> new BoundedValueMemo<Query, List<Option>>(256))
                .get(new Query(organizer, duration, parcels, availableWorkers),
                        () -> calculate(state, organizer, duration, parcels, availableWorkers));
    }
    private static List<Option> calculate(FrontierWorldState state, GoodsParticipant organizer,
            long duration, int parcels, int availableWorkers) {
        var options = new ArrayList<Option>();
        if (parcels <= availableWorkers) options.add(new Option(parcels, Optional.empty(),
                GoodsTransportValuation.estimate(state, organizer, duration, parcels)));
        for (var asset : state.transportFleet().assets().values()) {
            if (parcels <= Math.min(asset.stackSlots(), state.bootstrap().ruleset().expedition().packAnimalStackSlots())
                    && TransportAssetAdmission.available(state, asset, organizer.settlementId()))
                options.add(new Option(1, Optional.of(asset), GoodsTransportValuation.estimate(state, organizer, duration, 1)));
        }
        long labour = state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing()
                .map(io.farfrontier.palemirror.frontier.v3.model.supply.SourcingRules::labourValuePerTick).orElse(0L);
        return options.stream().sorted(Comparator.comparingLong((Option o) -> o.economicCost(labour))
                .thenComparingInt(Option::workers).thenComparing(o -> o.animal().isPresent())
                .thenComparing(o -> o.animal().map(a -> a.actorId().value()).orElse(""))).toList();
    }
    /** Price advice uses an upper crew envelope, never claims that those workers
     * are already assigned. Exact eligible staff is supplied by the logistics owner. */
    public static Optional<Option> forecast(FrontierWorldState state, GoodsParticipant organizer, long duration, int parcels) {
        return forLoad(state, organizer, duration, parcels,
                state.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumTripCargoStacks()).stream().findFirst();
    }
    public static io.farfrontier.palemirror.frontier.v3.model.supply.LotTransportCost costCurve(
            FrontierWorldState state, GoodsParticipant organizer, long duration, String item) {
        int size = parcelSize(state, item), maximum = maximumQuantity(state, item);
        long labour = state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing()
                .map(io.farfrontier.palemirror.frontier.v3.model.supply.SourcingRules::labourValuePerTick).orElse(0L);
        var options = forecastOptions(state, organizer, duration);
        return new io.farfrontier.palemirror.frontier.v3.model.supply.LotTransportCost(options.stream().map(option -> {
            int capacity = option.animal().map(asset -> Math.min(maximum,
                    Math.multiplyExact(size, Math.min(asset.stackSlots(), state.bootstrap().ruleset().expedition().packAnimalStackSlots()))))
                    .orElse(maximum);
            return new io.farfrontier.palemirror.frontier.v3.model.supply.LotTransportCost.Option(capacity,
                    option.animal().isPresent() ? capacity : size, option.economicCost(labour), 0);
        }).toList());
    }
    private static List<Option> forecastOptions(FrontierWorldState state, GoodsParticipant organizer, long duration) {
        return forLoad(state, organizer, duration, 1, state.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumTripCargoStacks());
    }
    public static int parcelSize(FrontierWorldState state, String item) {
        return switch (GoodsStockSpecification.forKind(state.bootstrap().ruleset().goodsTrade(), item).representation()) {
            case INDIVIDUAL -> 1;
            case FUNGIBLE -> state.bootstrap().ruleset().goodsTrade().maximumBatch();
        };
    }
    public static int maximumQuantity(FrontierWorldState state, String item) {
        return Math.multiplyExact(parcelSize(state, item), state.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumTripCargoStacks());
    }
    private GoodsTransportOptions() { }
}
