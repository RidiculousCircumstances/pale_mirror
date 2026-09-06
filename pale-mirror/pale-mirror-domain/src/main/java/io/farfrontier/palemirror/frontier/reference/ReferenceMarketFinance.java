package io.farfrontier.palemirror.frontier.reference;

/** Source-order credit, household consumption and firm settlement. */
final class ReferenceMarketFinance {
    private static final double FOOD_PER_PERSON = 0.065d;
    private static final double MEDICINE_PER_PERSON = 0.0007d;
    private static final double MEDICINE_THREAT_MULTIPLIER = 9.0d;
    private static final double ENERGY_PER_PERSON = 0.003d;
    private static final double ENERGY_PER_FORTIFICATION = 0.6d;
    private static final double FOOD_BUDGET_SHARE = 0.68d;
    private static final double BASE_RATE_PER_DAY = 0.002d;
    private static final double RISK_RATE_PER_THREAT = 0.015d;
    private static final double SETTLEMENT_CREDIT_LIMIT_PER_PERSON = 80.0d;
    private static final double COMPANY_CREDIT_ASSET_MULTIPLE = 1.35d;
    private static final double LICENCE_RENT_PER_CAPACITY = 0.11d;
    private static final double DEPRECIATION_PER_CAPACITY = 0.0015d;
    private static final double PROFIT_TAX = 0.08d;
    private static final double PROFIT_DIVIDEND_SHARE = 0.28d;
    private static final double DEBT_RESTRUCTURING_FRACTION = 0.55d;
    private static final int CRITICAL_RECEIVERSHIP_DAYS = 30;

    private final ReferenceMarketEconomy market;
    private final ReferenceEconomyEngine economy;

    ReferenceMarketFinance(ReferenceMarketEconomy market) {
        this.market = market;
        economy = market.economy();
    }

    void accrueCredit() {
        for (ReferenceCreditPosition credit : market.mutableCredits().values()) {
            if (!credit.status().equals("performing")) continue;
            credit.principal(credit.principal() + credit.principal() * credit.ratePerDay());
            if (credit.borrowerKind().equals("company")) {
                ReferenceCompany company = market.mutableCompanies().get(credit.borrowerId());
                if (company != null) company.debt(credit.principal());
            }
        }
    }

    double borrowSettlement(ReferenceMarketWorld world, int settlementId, double amount, String purpose) {
        ReferenceSettlement settlement = world.settlements().get(settlementId);
        double existing = market.mutableCredits().values().stream()
                .filter(credit -> credit.status().equals("performing") && credit.borrowerKind().equals("settlement")
                        && credit.borrowerId() == settlementId)
                .mapToDouble(ReferenceCreditPosition::principal).sum();
        double issued = Math.max(0.0d, Math.min(amount, settlement.population() * SETTLEMENT_CREDIT_LIMIT_PER_PERSON - existing));
        if (issued <= 0.0d) return 0.0d;
        double rate = BASE_RATE_PER_DAY + settlement.threat() * RISK_RATE_PER_THREAT;
        int id = market.nextCreditId();
        market.mutableCredits().put(id, new ReferenceCreditPosition(id, "settlement", settlementId, issued, rate, purpose, world.day()));
        settlement.cash(settlement.cash() + issued);
        return issued;
    }

    double borrowCompany(ReferenceMarketWorld world, ReferenceCompany company, double amount, String purpose) {
        double existing = market.mutableCredits().values().stream()
                .filter(credit -> credit.status().equals("performing") && credit.borrowerKind().equals("company")
                        && credit.borrowerId() == company.id())
                .mapToDouble(ReferenceCreditPosition::principal).sum();
        double issued = Math.max(0.0d, Math.min(amount, company.assets() * COMPANY_CREDIT_ASSET_MULTIPLE - existing));
        if (issued <= 0.0d) return 0.0d;
        ReferenceSettlement settlement = world.settlements().get(company.homeSettlementId());
        double rate = BASE_RATE_PER_DAY + settlement.threat() * RISK_RATE_PER_THREAT;
        int id = market.nextCreditId();
        market.mutableCredits().put(id, new ReferenceCreditPosition(id, "company", company.id(), issued, rate, purpose, world.day()));
        company.cash(company.cash() + issued);
        company.debt(company.debt() + issued);
        return issued;
    }

    void householdConsumption(ReferenceMarketWorld world) {
        for (ReferenceSettlement settlement : world.settlements().values()) {
            if (!settlement.alive()) continue;
            ReferenceHouseholdLedger household = market.mutableHouseholds().get(settlement.id());
            double foodNeed = settlement.population() * FOOD_PER_PERSON;
            double issue = world.rationAuthority() == null ? foodNeed
                    : world.rationAuthority().issue(world, settlement.id(), foodNeed);
            double got = settlement.remove(ReferenceResource.FOOD, issue);
            settlement.recordConsumption(ReferenceResource.FOOD, got);
            settlement.foodFulfillment(foodNeed == 0.0d ? 1.0d : Math.min(1.0d, got / foodNeed));
            household.foodCoverage(settlement.foodFulfillment());
            double payment = Math.min(household.cash() * FOOD_BUDGET_SHARE, got * economy.localValue(settlement, ReferenceResource.FOOD));
            household.cash(household.cash() - payment);
            ReferenceCompany localFarm = market.mutableCompanies().values().stream()
                    .filter(company -> company.homeSettlementId() == settlement.id() && company.sector() == ReferenceCompanySector.AGRICULTURE)
                    .findFirst().orElse(null);
            if (localFarm == null) settlement.cash(settlement.cash() + payment);
            else localFarm.cash(localFarm.cash() + payment);
            double medicineNeed = settlement.population() * MEDICINE_PER_PERSON
                    * (1.0d + MEDICINE_THREAT_MULTIPLIER * settlement.threat());
            double medicine = settlement.remove(ReferenceResource.MEDICINE, medicineNeed);
            settlement.recordConsumption(ReferenceResource.MEDICINE, medicine);
            settlement.medicineFulfillment(medicineNeed == 0.0d ? 1.0d : Math.min(1.0d, medicine / medicineNeed));
            double energyNeed = settlement.population() * ENERGY_PER_PERSON
                    + settlement.facilities().fortification() * ENERGY_PER_FORTIFICATION;
            settlement.recordConsumption(ReferenceResource.ENERGY, settlement.remove(ReferenceResource.ENERGY, energyNeed));
        }
    }

    void settleCompanies(ReferenceMarketWorld world) {
        for (ReferenceCompany company : market.mutableCompanies().values()) {
            ReferenceSettlement settlement = world.settlements().get(company.homeSettlementId());
            double rent = (company.capacity() + company.siteIds().size() * 0.5d) * LICENCE_RENT_PER_CAPACITY;
            company.cash(company.cash() - rent);
            settlement.cash(settlement.cash() + rent);
            double profit = company.cash() - company.assets() * DEPRECIATION_PER_CAPACITY - company.debt() * BASE_RATE_PER_DAY;
            company.lastProfit(profit);
            if (profit > 0.0d) {
                double tax = profit * PROFIT_TAX;
                double dividend = (profit - tax) * PROFIT_DIVIDEND_SHARE;
                // Python's ``cash -= tax + dividend`` first rounds the two
                // outflows together, then subtracts that result.  Preserving
                // the grouping is part of the binary64 source contract.
                company.cash(company.cash() - (tax + dividend));
                settlement.cash(settlement.cash() + tax);
                ReferenceHouseholdLedger household = market.mutableHouseholds().get(settlement.id());
                household.cash(household.cash() + dividend);
                household.dividendIncome(household.dividendIncome() + dividend);
            }
            if (company.cash() < -economy.humanAmount(25.0d)) restructure(world, company);
            else if (company.ownerKind().equals("municipal_receiver") && world.day() >= company.receiverUntil() && company.cash() > 0.0d) {
                company.ownerKind("private");
                company.status("operating");
            }
        }
    }

    private void restructure(ReferenceMarketWorld world, ReferenceCompany company) {
        company.debt(company.debt() * DEBT_RESTRUCTURING_FRACTION);
        company.cash(Math.max(0.0d, company.cash()));
        if (company.critical()) {
            company.ownerKind("municipal_receiver");
            company.receiverUntil(world.day() + CRITICAL_RECEIVERSHIP_DAYS);
            company.status("operating");
            world.event("D" + world.day() + ": " + company.name() + " entered municipal receivership");
        } else {
            company.ownerKind("creditors");
            company.status("operating");
            world.event("D" + world.day() + ": creditors restructured " + company.name());
        }
    }
}
