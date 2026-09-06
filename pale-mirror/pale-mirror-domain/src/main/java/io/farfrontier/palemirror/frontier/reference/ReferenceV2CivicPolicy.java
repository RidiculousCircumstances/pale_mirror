package io.farfrontier.palemirror.frontier.reference;

import java.util.Map;
import java.util.Objects;

/** Source V2 civic pass; {@link ReferenceV2State} retains every mutable ledger. */
final class ReferenceV2CivicPolicy {
    private ReferenceV2CivicPolicy() { }

    static void update(ReferenceWorld world, Map<Integer, ReferenceCivicLedger> civics,
                       Map<Integer, ReferenceSettlementDoctrine> doctrines, Map<Integer, ReferenceV2RationPlan> rationPlans,
                       Map<Integer, ReferenceEmergencyRegime> emergencyRegimes,
                       Map<Integer, ReferenceCoalitionCharter> charters,
                       Map<ReferenceRouteKey, ReferenceRouteInsurance> routeInsurance) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        for (ReferenceSettlement settlement : required.settlements().values()) {
            if (!settlement.alive()) continue;
            ReferenceCivicLedger civic = require(civics, settlement.id(), "civic ledger");
            ReferenceSettlementDoctrine doctrine = require(doctrines, settlement.id(), "settlement doctrine");
            civic.foodReserveDays(settlement.amount(ReferenceResource.FOOD)
                    / Math.max(1.0d, settlement.population() * required.economy().foodPerPerson()));
            double threat = settlement.threat();
            ReferenceCivicState old = civic.state();
            ReferenceCivicState state = civicState(settlement, old, threat);
            String reason = civicReason(state);
            civic.state(state);
            civic.reason(reason);
            if (old != state) {
                civic.enteredDay(required.day());
                required.marketWorld().event("D" + required.day() + ": " + settlement.name() + " entered " + state.id() + ": " + reason);
            }
            civic.rationFraction(rationFraction(state));
            ReferenceV2RationPlan plan = require(rationPlans, settlement.id(), "ration plan");
            plan.fraction(civic.rationFraction());
            plan.issuedDay(required.day());
            plan.reason(reason);
            civic.warBudget(Math.max(0.0d, settlement.treasury() * (ReferenceV2Rules.WAR_BUDGET_BASE
                    + doctrine.militancy() * ReferenceV2Rules.WAR_BUDGET_MILITANCY
                    + (isCrisis(state) ? ReferenceV2Rules.WAR_BUDGET_CRISIS : 0.0d))));
            double pressure = Math.max(0.0d, threat - 0.20d) + Math.max(0.0d, 0.8d - settlement.foodFulfillment());
            doctrine.legitimacy(Math.max(ReferenceV2Rules.MINIMUM_LEGITIMACY, Math.min(1.0d, doctrine.legitimacy()
                    - pressure * ReferenceV2Rules.REQUISITION_LEGITIMACY_COST
                    + (state == ReferenceCivicState.NORMAL ? ReferenceV2Rules.NORMAL_LEGITIMACY_RECOVERY : 0.0d))));
            civic.legitimacy(doctrine.legitimacy());
            if (isCrisis(state) && doctrine.quarantineWillingness() >= ReferenceV2Rules.QUARANTINE_MINIMUM_WILLINGNESS) {
                civic.quarantineUntil(Math.max(civic.quarantineUntil(), required.day() + ReferenceV2Rules.QUARANTINE_DAYS));
            }
            if (isCrisis(state)) {
                ReferenceEmergencyRegime regime = emergencyRegimes.get(settlement.id());
                if (regime == null) {
                    emergencyRegimes.put(settlement.id(), new ReferenceEmergencyRegime(settlement.id(), state, required.day(),
                            civic.warBudget(), civic.quarantineUntil()));
                } else {
                    regime.state(state);
                    regime.warBudget(civic.warBudget());
                    regime.quarantineUntil(civic.quarantineUntil());
                }
            } else {
                ReferenceEmergencyRegime ended = emergencyRegimes.remove(settlement.id());
                if (ended != null) ended.status("ended");
            }
        }
        updateRouteInsurance(required, civics, routeInsurance);
        reviewCharters(required, civics, doctrines, charters);
        for (ReferenceCompany company : required.microeconomy().companies().values()) {
            ReferenceSettlement settlement = require(required.settlements(), company.homeSettlementId(), "company home settlement");
            ReferenceCivicLedger civic = require(civics, settlement.id(), "company civic ledger");
            if (!isCrisis(civic.state())) continue;
            double target = ReferenceV2Rules.COMPANY_BASE_WAGE * ReferenceV2CompanyMind.wageMultiplier(civic, settlement.threat());
            company.wageOffer(Math.max(target, company.wageOffer() * ReferenceV2Rules.EMERGENCY_WAGE_DECAY));
        }
    }

    private static ReferenceCivicState civicState(ReferenceSettlement settlement, ReferenceCivicState old, double threat) {
        if (threat >= ReferenceV2Rules.SIEGE_THREAT || settlement.integrity() < ReferenceV2Rules.SIEGE_INTEGRITY) {
            return ReferenceCivicState.SIEGE;
        }
        if (threat >= ReferenceV2Rules.EMERGENCY_THREAT || settlement.illnessBurden() >= ReferenceV2Rules.MEDICAL_EMERGENCY_BURDEN) {
            return ReferenceCivicState.EMERGENCY;
        }
        if (threat >= ReferenceV2Rules.WATCH_THREAT) return ReferenceCivicState.WATCH;
        if ((old == ReferenceCivicState.EMERGENCY || old == ReferenceCivicState.SIEGE || old == ReferenceCivicState.WATCH)
                && threat <= ReferenceV2Rules.RECOVERY_THREAT) return ReferenceCivicState.RECOVERY;
        return ReferenceCivicState.NORMAL;
    }

    private static String civicReason(ReferenceCivicState state) {
        return switch (state) {
            case SIEGE -> "direct breach risk";
            case EMERGENCY -> "infection or medical emergency";
            case WATCH -> "frontier threat observed";
            case RECOVERY -> "threat receding";
            case NORMAL -> "routine civic order";
        };
    }

    private static void updateRouteInsurance(ReferenceWorld world, Map<Integer, ReferenceCivicLedger> civics,
                                             Map<ReferenceRouteKey, ReferenceRouteInsurance> routeInsurance) {
        for (ReferenceRoute route : world.trade().routes()) {
            ReferenceSettlement a = require(world.settlements(), route.a(), "route endpoint");
            ReferenceSettlement b = require(world.settlements(), route.b(), "route endpoint");
            boolean emergency = (a.alive() && isCrisis(require(civics, a.id(), "route civic ledger").state()))
                    || (b.alive() && isCrisis(require(civics, b.id(), "route civic ledger").state()));
            if (!emergency) continue;
            double risk = Math.max(route.infection(), route.risk());
            double premium = (1.0d + risk * ReferenceV2Rules.ROUTE_INSURANCE_RISK_MULTIPLIER)
                    * ReferenceV2Rules.ROUTE_INSURANCE_RATE;
            routeInsurance.put(route.key(), new ReferenceRouteInsurance(route.key(), a.id(), premium,
                    Math.max(0.0d, 1.0d - risk), world.day() + ReferenceV2Rules.ROUTE_INSURANCE_DAYS));
        }
    }

    private static void reviewCharters(ReferenceWorld world, Map<Integer, ReferenceCivicLedger> civics,
                                       Map<Integer, ReferenceSettlementDoctrine> doctrines,
                                       Map<Integer, ReferenceCoalitionCharter> charters) {
        for (ReferenceCoalitionCharter charter : charters.values()) {
            if (!charter.status().equals("active")) continue;
            if (world.day() > charter.expiresDay()) {
                charter.status("expired");
                world.marketWorld().event("D" + world.day() + ": coalition charter " + charter.id() + " expired");
                continue;
            }
            Integer endangered = null;
            for (int settlementId : charter.members()) {
                ReferenceSettlement settlement = require(world.settlements(), settlementId, "charter settlement");
                ReferenceCivicLedger civic = require(civics, settlementId, "charter civic ledger");
                if (!settlement.alive() || civic.state() == ReferenceCivicState.SIEGE || settlement.foodFulfillment() < 0.80d) {
                    endangered = settlementId;
                    break;
                }
            }
            if (endangered == null) continue;
            charter.status("withdrawn");
            for (int member : charter.members()) {
                if (member != endangered) {
                    ReferenceSettlementDoctrine doctrine = require(doctrines, member, "charter settlement doctrine");
                    doctrine.solidarity(Math.max(0.0d, doctrine.solidarity() - ReferenceV2Rules.CHARTER_BREACH_TRUST_LOSS));
                }
            }
            world.marketWorld().event("D" + world.day() + ": coalition charter " + charter.id()
                    + " withdrew after member " + endangered + " entered crisis");
        }
    }

    private static boolean isCrisis(ReferenceCivicState state) {
        return state == ReferenceCivicState.EMERGENCY || state == ReferenceCivicState.SIEGE;
    }

    private static double rationFraction(ReferenceCivicState state) {
        return switch (state) {
            case EMERGENCY -> 0.92d;
            case SIEGE -> 0.82d;
            case NORMAL, WATCH, RECOVERY -> 1.0d;
        };
    }

    private static <K, V> V require(Map<K, V> source, K key, String description) {
        V result = source.get(key);
        if (result == null) throw new IllegalStateException(description + " is absent: " + key);
        return result;
    }
}
