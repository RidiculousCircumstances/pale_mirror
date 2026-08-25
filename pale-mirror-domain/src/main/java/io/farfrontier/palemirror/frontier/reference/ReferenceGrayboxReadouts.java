package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only graybox dashboards for source facts which have no spatial body of their own. */
final class ReferenceGrayboxReadouts {
    private ReferenceGrayboxReadouts() { }

    static List<ReferenceGrayboxSnapshot.Readout> from(ReferenceWorld world,
                                                         Map<Integer, ReferenceGrayboxLayout.Rectangle> settlementAreas) {
        List<ReferenceGrayboxSnapshot.Readout> result = new ArrayList<>();
        Map<Integer, Integer> nextSlot = new LinkedHashMap<>();
        sorted(world.settlements().values(), ReferenceSettlement::id).forEach(settlement -> {
            ReferenceCivicLedger civic = world.v2().civics().get(settlement.id());
            ReferenceHouseholdLedger household = world.microeconomy().households().get(settlement.id());
            if (civic == null || household == null) throw new IllegalStateException("settlement lacks source dashboard owner: " + settlement.id());
            add(result, nextSlot, settlementAreas, settlement.id(), "civic", "CIVIC", "state=" + civic.state().id()
                    + " doctrine=" + settlement.doctrine() + " legitimacy=" + number(civic.legitimacy()) + " quarantineTo="
                    + civic.quarantineUntil() + " warBudget=" + number(civic.warBudget()) + " reason=" + civic.reason(), "readout.civic");
            add(result, nextSlot, settlementAreas, settlement.id(), "market", "MARKET", "cash=" + number(settlement.cash())
                    + " stock=" + stockText(settlement) + " householdCash=" + number(household.cash()) + " foodCoverage="
                    + number(household.foodCoverage()), "readout.market");
        });
        for (ReferenceCompany company : sorted(world.microeconomy().companies().values(), ReferenceCompany::id)) {
            add(result, nextSlot, settlementAreas, company.homeSettlementId(), "company:" + company.id(), "COMPANY", "#"
                    + company.id() + " " + company.name() + " sector=" + company.sector().name().toLowerCase(Locale.ROOT) + " " + company.status() + "/"
                    + company.v2State() + " cash=" + number(company.cash()) + " debt=" + number(company.debt()) + " cap="
                    + number(company.capacity()) + " workers=" + number(company.employees()), "readout.company");
        }
        for (ReferenceSettlement settlement : sorted(world.settlements().values(), ReferenceSettlement::id)) {
            List<ReferenceContract> contracts = world.microeconomy().contracts().values().stream()
                    .filter(item -> item.buyerSettlementId() == settlement.id()).toList();
            if (!contracts.isEmpty()) add(result, nextSlot, settlementAreas, settlement.id(), "contract-summary:" + settlement.id(), "CONTRACT",
                    "count=" + contracts.size() + " active=" + contracts.stream().filter(item -> item.status().equals("active")).count()
                            + " daily=" + number(contracts.stream().mapToDouble(ReferenceContract::dailyQuantity).sum()) + " delivered="
                            + number(contracts.stream().mapToDouble(ReferenceContract::delivered).sum()) + " breached="
                            + number(contracts.stream().mapToDouble(ReferenceContract::breachedQuantity).sum()), "readout.contract");
            List<ReferenceCreditPosition> credits = world.microeconomy().credits().values().stream()
                    .filter(item -> creditSettlement(world, item) == settlement.id()).toList();
            if (!credits.isEmpty()) add(result, nextSlot, settlementAreas, settlement.id(), "credit-summary:" + settlement.id(), "CREDIT",
                    "count=" + credits.size() + " principal=" + number(credits.stream().mapToDouble(ReferenceCreditPosition::principal).sum())
                            + " performing=" + credits.stream().filter(item -> item.status().equals("performing")).count(), "readout.credit");
        }
        ReferenceGrayboxLayout.Point hive = world.infection().organs().isEmpty() ? ReferenceGrayboxLayout.centre(0, 0)
                : ReferenceGrayboxLayout.centre(world.infection().organs().values().iterator().next().x(), world.infection().organs().values().iterator().next().y());
        result.add(new ReferenceGrayboxSnapshot.Readout("hive:adaptation", "HIVE", "genome=" + world.infection().genome()
                + " damageMemory=" + world.infection().damageMemory() + " biomassHarvested=" + number(world.infection().harvestedBiomass())
                + " genetic=" + number(world.infection().harvestedGeneticMaterial()), hive, "readout.hive"));
        return List.copyOf(result);
    }

    private static void add(List<ReferenceGrayboxSnapshot.Readout> values, Map<Integer, Integer> nextSlot,
                            Map<Integer, ReferenceGrayboxLayout.Rectangle> areas, int settlementId, String id,
                            String category, String text, String colour) {
        ReferenceGrayboxLayout.Rectangle area = areas.get(settlementId);
        if (area == null) throw new IllegalStateException("dashboard settlement is absent: " + settlementId);
        int slot = nextSlot.merge(settlementId, 1, Integer::sum) - 1;
        values.add(new ReferenceGrayboxSnapshot.Readout(id, category, text, ReferenceGrayboxLayout.settlementReadout(area, slot), colour));
    }

    private static String stockText(ReferenceSettlement settlement) {
        return java.util.Arrays.stream(ReferenceResource.values()).map(resource -> resource.name().toLowerCase(Locale.ROOT) + "="
                + number(settlement.amount(resource))).collect(java.util.stream.Collectors.joining(","));
    }

    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    private static int creditSettlement(ReferenceWorld world, ReferenceCreditPosition credit) {
        if (credit.borrowerKind().equals("settlement")) return credit.borrowerId();
        ReferenceCompany company = world.microeconomy().companies().get(credit.borrowerId());
        if (company == null) throw new IllegalStateException("credit borrower has no company: " + credit.id());
        return company.homeSettlementId();
    }

    private static <T, U extends Comparable<? super U>> List<T> sorted(Iterable<T> values, java.util.function.Function<T, U> key) {
        List<T> result = new ArrayList<>();
        values.forEach(result::add);
        result.sort(Comparator.comparing(key));
        return result;
    }
}
