package io.farfrontier.palemirror.frontier.reference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The sole source-order application coordinator for one Python reference day.
 *
 * <p>It deliberately composes existing owners rather than duplicating their
 * state. The order below is a behavioural contract: changing it changes what
 * a settlement can observe, buy, or plan on a particular deterministic day.</p>
 */
public final class ReferenceSimulationEngine {
    private static final int THREAT_RADIUS = 4;
    private static final double CONTAINMENT_THREAT_THRESHOLD = .14d;
    private static final double CONTAINMENT_AMMO_BASE = 1.0d;
    private static final double CONTAINMENT_AMMO_PER_FORTIFICATION = 1.2d;
    private static final double CONTAINMENT_MINIMUM_AMMO = .2d;
    private static final double CONTAINMENT_STRENGTH_BASE = .015d;
    private static final double CONTAINMENT_STRENGTH_PER_AMMO = .009d;
    private static final double CONTAINMENT_STRENGTH_MAXIMUM = .075d;
    private static final double CONTAINMENT_RADIUS = 3.0d;
    private static final int TRADE_HISTORY_DAYS = 30;
    private static final double BREAKER_BREACH_FRACTION = .34d;
    private static final double RAIDER_PERSONNEL_PRESSURE = .36d;
    private static final double RAIDER_LINE_MITIGATION = .55d;
    private static final double RAIDER_SCOUT_MITIGATION = .30d;
    private static final double BREAKER_ENGINEER_MITIGATION = .55d;
    private static final double MEDIC_DEATH_TO_WOUND_FRACTION = .55d;

    private final ReferenceHiveDirector hiveDirector;

    public ReferenceSimulationEngine() {
        this(new ReferenceHiveDirector());
    }

    ReferenceSimulationEngine(ReferenceHiveDirector hiveDirector) {
        this.hiveDirector = Objects.requireNonNull(hiveDirector, "hiveDirector");
    }

    /** Advance canonical state through exactly one complete source simulation day. */
    public void tick(ReferenceWorld world) {
        runPhases(world);
    }

    /**
     * Port of {@code simulation.engine.SimulationEngine.run_phases}.
     *
     * <p>The legacy Python strategist is not yet a Java owner. Refusing that
     * profile is intentional: silently dropping a planner would manufacture a
     * plausible but non-reference world.</p>
     */
    public void runPhases(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) {
            throw new IllegalStateException("legacy ReferenceWorld strategy is not ported; V2 is required for daily advancement");
        }
        required.day(required.day() + 1);

        // 1. Physical ecology and tissue precede every strategic decision.
        required.infection().ecologyStep(required.resourceSites().values(), required.day());
        required.v2().refreshTerritory(required);

        // 2. Existing motion resolves before agents issue new work.
        required.operations().step(required);
        List<ReferenceAttackEvent> attacks = required.infection().advanceBioforms(required);
        required.operations().detectAndIntercept(required);
        required.field().detectSwarms(required);
        updateThreats(required);
        updateRouteInfection(required);
        resolveAttacks(required, attacks);
        required.operations().syncInfectionSwarms(required);
        required.field().step(required);
        required.v2().refreshTerritory(required);
        required.v2().advanceFrontier(required);

        // 3. Perception is local; the civic result constrains today's market.
        required.v2().refreshTerritory(required);
        required.v2().observe(required);
        required.v2().updateCivics(required);

        // 4. The company economy clears before biological trade effects.
        refreshMarketEcologyProjections(required);
        List<ReferenceTradeRecord> trades = required.microeconomy().runDay(required.marketWorld());
        commitHumanExtraction(required);
        required.v2().updateCompanyStates(required);
        required.v2().issueProcurement(required);
        required.microeconomy().syncCompatibility(required.marketWorld());

        // 5. Trade reports and infection jumps settle after production.
        logTradeSummary(required, trades);
        int jumps = required.infection().afterTrade(required.settlements(), trades);
        if (jumps > 0) required.marketWorld().event("D" + required.day() + ": infection crossed " + jumps + " trade route(s) as spores");

        // 6. New human and hive work is planned only after this day's facts.
        localContainment(required);
        required.v2().planHumans(required);
        required.v2().advanceHiveLifecycle(required);
        required.v2().stepHive(required, hiveDirector);

        // 7. Irreversible demographic collapse commits last.
        for (ReferenceSettlement settlement : required.settlements().values()) {
            boolean wasAlive = settlement.alive();
            settlement.endOfDayDemography();
            if (wasAlive && !settlement.alive()) {
                orphanResourceSites(required, settlement.id());
                required.infection().settlementDestroyed(settlement);
                required.marketWorld().event("D" + required.day() + ": " + settlement.name() + " collapsed and is gone permanently");
            }
        }
        required.microeconomy().reconcileIndividualEmployment(required.marketWorld());
        updateThreats(required);
        updateRouteInfection(required);
        required.recordHistory();
        required.assertProfileInvariants();
    }

    private static void updateRouteInfection(ReferenceWorld world) {
        for (ReferenceRoute route : world.trade().routes()) {
            double[] control = world.field().routeControl(world, route.a(), route.b());
            double infection = route.sectorInfection();
            route.infection(infection * control[0]);
            route.checkpointCapacityMultiplier(control[1]);
        }
    }

    private static void updateThreats(ReferenceWorld world) {
        for (ReferenceSettlement settlement : world.settlements().values()) {
            settlement.threat(settlement.alive()
                    ? world.infection().pressureAt(settlement.x(), settlement.y(), THREAT_RADIUS)
                    : 1.0d);
        }
    }

    /**
     * Projects current physical ecology into the market for this day only.
     * Site ownership stays with the world and the company economy owns its
     * warehouses; this boundary mirrors Python's direct InfectionModel reads.
     */
    private static void refreshMarketEcologyProjections(ReferenceWorld world) {
        for (ReferenceResourceSite site : world.marketWorld().resourceSites().values()) {
            world.marketWorld().siteOutputFactor(site.id(), world.infection().ecosystem()
                    .humanOutputFactor(site.kind().name().toLowerCase(Locale.ROOT), site.x(), site.y()));
            ReferenceSettlement owner = site.ownerId() == null ? null : world.settlements().get(site.ownerId());
            world.marketWorld().siteHaulInfection(site.id(), owner == null ? 0.0d
                    : world.infection().routeInfection(owner.x(), owner.y(), site.x(), site.y()));
        }
    }

    /** Commit the current market's finite farm/forest withdrawal to ecology exactly once. */
    private static void commitHumanExtraction(ReferenceWorld world) {
        for (Map.Entry<Integer, Double> entry : world.marketWorld().drainHumanExtractions().entrySet()) {
            ReferenceResourceSite site = world.marketWorld().resourceSites().get(entry.getKey());
            if (site == null) throw new IllegalStateException("market recorded extraction for missing site " + entry.getKey());
            world.infection().ecosystem().humanExtract(site.kind().name().toLowerCase(Locale.ROOT), site.x(), site.y(), entry.getValue());
        }
    }

    private static void resolveAttacks(ReferenceWorld world, List<ReferenceAttackEvent> attacks) {
        for (ReferenceAttackEvent attack : attacks) {
            ReferenceSettlement settlement = world.settlements().get(attack.settlementId());
            if (settlement == null || !settlement.alive()) continue;
            double support = world.operations().supportFor(settlement.id())
                    + world.field().supportFor(settlement.id(), settlement.x(), settlement.y());
            Map<ReferenceHumanUnitKind, Double> roles = world.operations().rolesSupporting(settlement.id());
            Map<ReferenceBioformKind, Double> composition = attack.composition().isEmpty()
                    ? Map.of(attack.kind(), 1.0d) : attack.composition();
            double raiderRatio = ReferenceFormations.compositionRatio(composition, ReferenceBioformKind.RAIDER);
            double breakerRatio = ReferenceFormations.compositionRatio(composition, ReferenceBioformKind.BREAKER);
            double rolesTotal = roles.values().stream().mapToDouble(Double::doubleValue).sum();
            double denominator = Math.max(1.0d, rolesTotal);
            double lineRatio = roles.getOrDefault(ReferenceHumanUnitKind.LINE, 0.0d) / denominator;
            double scoutRatio = roles.getOrDefault(ReferenceHumanUnitKind.SCOUT, 0.0d) / denominator;
            double engineerRatio = roles.getOrDefault(ReferenceHumanUnitKind.ENGINEER, 0.0d) / denominator;
            double medicRatio = roles.getOrDefault(ReferenceHumanUnitKind.MEDIC, 0.0d) / denominator;
            double structuralBreach = attack.power() * breakerRatio * BREAKER_BREACH_FRACTION
                    * Math.max(.20d, 1.0d - engineerRatio * BREAKER_ENGINEER_MITIGATION);
            double personnelPressure = composition.size() > 1 ? attack.power() * raiderRatio * RAIDER_PERSONNEL_PRESSURE : 0.0d;
            personnelPressure *= Math.max(.20d, 1.0d - lineRatio * RAIDER_LINE_MITIGATION - scoutRatio * RAIDER_SCOUT_MITIGATION);
            ReferenceSwarmAttackResolution result = settlement.resolveSwarmAttack(attack.power(), support, structuralBreach,
                    personnelPressure, medicRatio * MEDIC_DEATH_TO_WOUND_FRACTION);
            world.infection().recordAttackHarvest(attack, settlement, result.populationLoss(), result.destroyed(), world.day());
            String compactComposition = ReferenceFormations.compactComposition(composition);
            world.recordCombat(new ReferenceCombatReceipt(world.day(), attack.swarmId(), settlement.id(), settlement.x(), settlement.y(),
                    attack.power(), compactComposition, attack.phase(), result.defence(), support, structuralBreach, result.damage(), result.destroyed()));
            world.marketWorld().event("D" + world.day() + ": swarm " + attack.swarmId() + " attacked " + settlement.name()
                    + "; composition=" + compactComposition + ", phase=" + attack.phase().id() + "; power=" + rounded(attack.power(), 0)
                    + ", defence=" + rounded(result.defence(), 0) + ", support=" + rounded(support, 0)
                    + ", damage=" + rounded(result.damage(), 1));
            if (result.destroyed()) {
                orphanResourceSites(world, settlement.id());
                world.infection().settlementDestroyed(settlement);
                world.marketWorld().event("D" + world.day() + ": " + settlement.name() + " was destroyed permanently");
            }
        }
    }

    private static void localContainment(ReferenceWorld world) {
        for (ReferenceSettlement settlement : world.settlements().values()) {
            if (!settlement.alive() || settlement.threat() < CONTAINMENT_THREAT_THRESHOLD) continue;
            double ammo = Math.min(settlement.amount(ReferenceResource.AMMO), CONTAINMENT_AMMO_BASE
                    + settlement.facilities().fortification() * CONTAINMENT_AMMO_PER_FORTIFICATION);
            if (ammo <= CONTAINMENT_MINIMUM_AMMO) continue;
            settlement.remove(ReferenceResource.AMMO, ammo);
            double strength = Math.min(CONTAINMENT_STRENGTH_MAXIMUM, CONTAINMENT_STRENGTH_BASE + ammo * CONTAINMENT_STRENGTH_PER_AMMO);
            double removed = world.infection().suppressArea(settlement.x(), settlement.y(), CONTAINMENT_RADIUS, strength);
            world.infection().recordDamage("containment", removed);
            world.recordContainment(new ReferenceContainmentReceipt(world.day(), settlement.id(), settlement.x(), settlement.y(),
                    CONTAINMENT_RADIUS, strength, ammo, removed));
        }
    }

    private static void orphanResourceSites(ReferenceWorld world, int settlementId) {
        boolean changed = false;
        for (ReferenceResourceSite site : world.marketWorld().resourceSites().values()) {
            if (Integer.valueOf(settlementId).equals(site.ownerId())) {
                site.ownerId(null);
                site.operatorCompanyId(null);
                changed = true;
            }
        }
        if (changed) world.refreshPrimaryCapacity();
    }

    private static void logTradeSummary(ReferenceWorld world, List<ReferenceTradeRecord> trades) {
        if (trades.isEmpty()) return;
        double total = trades.stream().mapToDouble(ReferenceTradeRecord::value).sum();
        ReferenceTradeRecord biggest = trades.stream().max((left, right) -> Double.compare(left.value(), right.value())).orElseThrow();
        ReferenceSettlement seller = world.settlements().get(biggest.sellerId());
        ReferenceSettlement buyer = world.settlements().get(biggest.buyerId());
        world.marketWorld().event("D" + world.day() + ": " + trades.size() + " trades, value=" + rounded(total, 0)
                + "; largest " + biggest.resource().name().toLowerCase(Locale.ROOT) + " " + seller.name() + "->" + buyer.name()
                + " x" + rounded(biggest.delivered(), 1) + " @ " + rounded(biggest.unitPrice(), 2));
    }

    private static String rounded(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_EVEN).toPlainString();
    }
}
