package io.farfrontier.palemirror.frontier.reference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;

/** Source V2 firm distress, emergency procurement and documented seizure policy. */
final class ReferenceV2WarEconomy {
    private static final double PAYMENT_EPSILON = 1.0e-9d;
    private static final List<ReferenceResource> CRITICAL_RESOURCES = List.of(
            ReferenceResource.FOOD, ReferenceResource.MEDICINE, ReferenceResource.AMMO, ReferenceResource.WEAPONS);

    private ReferenceV2WarEconomy() { }

    static void updateCompanyStates(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        for (ReferenceCompany company : required.microeconomy().mutableCompanies().values()) {
            ReferenceSettlement settlement = require(required.settlements(), company.homeSettlementId(), "company home settlement");
            double wageDays = Math.max(0.01d, company.wageBill() != 0.0d
                    ? company.wageBill() : company.wageOffer() * Math.max(1.0d, company.employees()));
            String previous = company.v2State();
            if (company.cash() < -wageDays * ReferenceV2Rules.COMPANY_INSOLVENCY_CASH_DAYS) {
                company.v2State("insolvent");
            } else if (company.cash() < wageDays * ReferenceV2Rules.COMPANY_STRESS_CASH_DAYS
                    || settlement.threat() >= ReferenceV2Rules.EMERGENCY_THREAT) {
                company.v2State("stressed");
            } else if (company.ownerKind().equals("municipal_receiver")) {
                company.v2State("receivership");
            } else if (!company.status().equals("closed")) {
                company.v2State("operating");
            }
            if (!company.v2State().equals(previous)) {
                required.marketWorld().event("D" + required.day() + ": " + company.name() + " is " + company.v2State());
            }
        }
    }

    static void issueProcurement(ReferenceWorld world, Map<Integer, ReferenceCivicLedger> civics,
                                 Map<Integer, ReferenceSettlementDoctrine> doctrines,
                                 Map<Integer, ReferenceProcurementOrder> procurements,
                                 Map<Integer, ReferenceCompensationClaim> compensation,
                                 IntSupplier nextProcurementId, IntSupplier nextClaimId) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        for (ReferenceSettlement settlement : required.settlements().values()) {
            ReferenceCivicLedger civic = civics.get(settlement.id());
            if (civic == null || (civic.state() != ReferenceCivicState.EMERGENCY && civic.state() != ReferenceCivicState.SIEGE)) continue;
            double reserveDays = civic.state() == ReferenceCivicState.SIEGE
                    ? ReferenceV2Rules.SIEGE_RESERVE_DAYS : ReferenceV2Rules.EMERGENCY_RESERVE_DAYS;
            for (ReferenceResource resource : CRITICAL_RESOURCES) {
                double target = resource == ReferenceResource.FOOD
                        ? settlement.population() * required.economy().foodPerPerson() * reserveDays
                        : required.economy().targetStock(settlement, resource);
                double deficit = Math.max(0.0d, target - settlement.amount(resource));
                if (deficit <= ReferenceV2Rules.PROCUREMENT_MINIMUM_QUANTITY) continue;
                ReferenceCompany company = firstLocalProducer(required.microeconomy(), settlement.id(), resource);
                if (company == null) continue;
                double available = Math.max(0.0d, company.amount(resource) * ReferenceV2Rules.PROCUREMENT_FRACTION);
                double quantity = Math.min(deficit, available);
                if (quantity <= ReferenceV2Rules.PROCUREMENT_MINIMUM_QUANTITY) continue;
                double price = required.economy().localValue(settlement, resource)
                        * (civic.state() == ReferenceCivicState.SIEGE
                        ? ReferenceV2Rules.SIEGE_PRICE_MULTIPLIER : ReferenceV2Rules.EMERGENCY_PRICE_MULTIPLIER);
                double cost = quantity * price;
                if (settlement.treasury() + PAYMENT_EPSILON < cost) {
                    new ReferenceMarketFinance(required.microeconomy()).borrowSettlement(required.marketWorld(), settlement.id(),
                            cost - settlement.treasury(), "emergency procurement");
                }
                if (settlement.treasury() + PAYMENT_EPSILON < cost) {
                    requisition(required, civics, doctrines, compensation, nextClaimId, settlement, company, resource, quantity, price);
                    continue;
                }
                company.subtract(resource, quantity);
                required.microeconomy().mutablePublicInventory(settlement.id()).merge(resource, quantity, Double::sum);
                settlement.treasury(settlement.treasury() - cost);
                company.cash(company.cash() + cost);
                int id = nextProcurementId.getAsInt();
                procurements.put(id, new ReferenceProcurementOrder(id, settlement.id(), resource, quantity, price,
                        required.day(), quantity, "fulfilled"));
                required.marketWorld().event("D" + required.day() + ": " + settlement.name() + " procured "
                        + formatOneDecimal(quantity) + " " + resource.name().toLowerCase(java.util.Locale.ROOT)
                        + " from " + company.name());
            }
        }
        settleCompensation(required, compensation);
    }

    static void requisition(ReferenceWorld world, Map<Integer, ReferenceCivicLedger> civics,
                            Map<Integer, ReferenceSettlementDoctrine> doctrines,
                            Map<Integer, ReferenceCompensationClaim> compensation, IntSupplier nextClaimId,
                            ReferenceSettlement settlement, ReferenceCompany company, ReferenceResource resource,
                            double quantity, double price) {
        ReferenceCivicLedger civic = require(civics, settlement.id(), "civic ledger");
        if (civic.state() != ReferenceCivicState.SIEGE || quantity <= ReferenceV2Rules.PROCUREMENT_MINIMUM_QUANTITY) return;
        company.subtract(resource, quantity);
        world.microeconomy().mutablePublicInventory(settlement.id()).merge(resource, quantity, Double::sum);
        int id = nextClaimId.getAsInt();
        compensation.put(id, new ReferenceCompensationClaim(id, settlement.id(), company.id(), quantity * price,
                world.day() + ReferenceV2Rules.COMPENSATION_DELAY_DAYS,
                "siege requisition of " + formatOneDecimal(quantity) + " "
                        + resource.name().toLowerCase(java.util.Locale.ROOT)));
        ReferenceSettlementDoctrine doctrine = require(doctrines, settlement.id(), "settlement doctrine");
        doctrine.legitimacy(Math.max(ReferenceV2Rules.MINIMUM_LEGITIMACY,
                doctrine.legitimacy() - ReferenceV2Rules.REQUISITION_LEGITIMACY_COST));
        world.marketWorld().event("D" + world.day() + ": " + settlement.name() + " requisitioned "
                + formatOneDecimal(quantity) + " " + resource.name().toLowerCase(java.util.Locale.ROOT)
                + "; compensation claim " + id + " issued");
    }

    static void settleCompensation(ReferenceWorld world, Map<Integer, ReferenceCompensationClaim> compensation) {
        for (ReferenceCompensationClaim claim : compensation.values()) {
            if (!claim.status().equals("pending") || world.day() < claim.dueDay()) continue;
            ReferenceSettlement settlement = world.settlements().get(claim.settlementId());
            ReferenceCompany company = world.microeconomy().mutableCompanies().get(claim.companyId());
            if (settlement == null || company == null) {
                claim.status("void");
                continue;
            }
            if (settlement.treasury() + PAYMENT_EPSILON < claim.amount()) continue;
            settlement.treasury(settlement.treasury() - claim.amount());
            company.cash(company.cash() + claim.amount());
            claim.status("paid");
            world.marketWorld().event("D" + world.day() + ": " + settlement.name() + " paid compensation claim " + claim.id());
        }
    }

    private static ReferenceCompany firstLocalProducer(ReferenceMarketEconomy market, int settlementId, ReferenceResource resource) {
        List<ReferenceCompany> candidates = new ArrayList<>();
        for (ReferenceCompany company : market.mutableCompanies().values()) {
            if (company.homeSettlementId() == settlementId && company.output() == resource) candidates.add(company);
        }
        return candidates.stream().min(Comparator.comparingInt(ReferenceCompany::id)).orElse(null);
    }

    private static <K, V> V require(Map<K, V> source, K key, String description) {
        V result = source.get(key);
        if (result == null) throw new IllegalStateException(description + " is absent: " + key);
        return result;
    }

    /** CPython's fixed-point float formatting rounds the binary64 value half-even. */
    private static String formatOneDecimal(double value) {
        return new BigDecimal(value).setScale(1, RoundingMode.HALF_EVEN).toPlainString();
    }
}
