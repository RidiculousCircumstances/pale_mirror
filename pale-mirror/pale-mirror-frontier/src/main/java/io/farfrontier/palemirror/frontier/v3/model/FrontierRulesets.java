package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

import java.util.Map;

/** Installed immutable ruleset catalog. Selection is always exact by id, schema and content digest. */
public final class FrontierRulesets {
    /**
     * R4 keeps the retained pedestrian edge at the server-turn cadence.  R3's five-turn
     * semantic checkpoint left a HOT farmer visibly pausing at every grid cell even though the
     * motion actuator was continuous.  Crop work remains a slower, distinct boundary.
     */
    private static final FrontierRuleset PREVIOUS_PRODUCTION_R14 = ruleset("frontier-v3-production-r14", 17, 1L, 20L, 20L);
    private static final FrontierRuleset PRODUCTION = caravanPace("frontier-v3-production-r15", PREVIOUS_PRODUCTION_R14, 15L);
    // Named deterministic precondition profile; it shares the adopted production expedition policy.
    private static final FrontierRuleset PREVIOUS_EXPEDITION_CANDIDATE_R1 = ruleset("frontier-v3-expedition-candidate-r1", 17, 1L, 20L, 20L);
    private static final FrontierRuleset EXPEDITION_CANDIDATE = caravanPace("frontier-v3-expedition-candidate-r2", PREVIOUS_EXPEDITION_CANDIDATE_R1, 15L);
    private static final FrontierRuleset PREVIOUS_TRADE_PLAYTEST_R3 = new FrontierRuleset("frontier-v3-trade-playtest-r3", 17,
            PREVIOUS_PRODUCTION_R14.cadence(), PREVIOUS_PRODUCTION_R14.spatial(), PREVIOUS_PRODUCTION_R14.rates(), PREVIOUS_PRODUCTION_R14.facilityCapacity(),
            PREVIOUS_PRODUCTION_R14.combat(), PREVIOUS_PRODUCTION_R14.hiveCommand(), PREVIOUS_PRODUCTION_R14.residentLife(),
            PREVIOUS_PRODUCTION_R14.resourceHarvestColdTravelTicksPerEdge(), PREVIOUS_PRODUCTION_R14.workCatalog(), PREVIOUS_PRODUCTION_R14.goodsTrade(),
            java.util.List.of(new InitialSettlementStock(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("settlement:7"), "minecraft:bread", 256)));
    private static final FrontierRuleset TRADE_PLAYTEST = caravanPace("frontier-v3-trade-playtest-r4", PREVIOUS_TRADE_PLAYTEST_R3, 15L);
    private static final FrontierRuleset PREVIOUS_QUARRY_GRAYBOX = new FrontierRuleset("frontier-v3-quarry-graybox-r1", 18,
            TRADE_PLAYTEST.cadence(), TRADE_PLAYTEST.spatial(), TRADE_PLAYTEST.rates(), TRADE_PLAYTEST.facilityCapacity(),
            TRADE_PLAYTEST.combat(), TRADE_PLAYTEST.hiveCommand(), TRADE_PLAYTEST.residentLife(),
            TRADE_PLAYTEST.resourceHarvestColdTravelTicksPerEdge(), TRADE_PLAYTEST.workCatalog().withDefinition(
                    new WorkCatalog.Definition("minecraft:stone", WorkOperation.EXTRACT, HumanCapability.EXTRACTION, 120)),
            TRADE_PLAYTEST.goodsTrade().withCommodity(GoodsPolicyKind.PUBLIC_SETTLEMENT,
                    new GoodsTradeRules.Commodity("minecraft:cobblestone", 0,
                            io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionRules.graybox().reserveItems(),
                            io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ONE, io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(2))),
            TRADE_PLAYTEST.initialSettlementStocks(), TRADE_PLAYTEST.labour().with(ResidentWorkKind.EXTRACTION,
                    new SettlementLabourRules.Entry(2, 1, 2)), TRADE_PLAYTEST.expedition(),
            io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionRules.graybox());
    private static final FrontierRuleset PREVIOUS_QUARRY_GRAYBOX_R2 = populationExecution("frontier-v3-quarry-graybox-r2", PREVIOUS_QUARRY_GRAYBOX);
    private static final FrontierRuleset PREVIOUS_QUARRY_GRAYBOX_R3 = expandingQuarry(PREVIOUS_QUARRY_GRAYBOX_R2);
    private static final FrontierRuleset QUARRY_GRAYBOX = developingQuarry(PREVIOUS_QUARRY_GRAYBOX_R3);
    private static final FrontierRuleset TERRACED_QUARRY_GRAYBOX = terracedQuarry(QUARRY_GRAYBOX);
    private static final FrontierRuleset AVAILABLE_FRONT_QUARRY_GRAYBOX = availableFrontQuarry(TERRACED_QUARRY_GRAYBOX);
    private static final FrontierRuleset HIGH_RESERVE_QUARRY_GRAYBOX = quarryReserve(AVAILABLE_FRONT_QUARRY_GRAYBOX);
    private static final FrontierRuleset FUELLED_QUARRY_GRAYBOX = fuelledQuarry(HIGH_RESERVE_QUARRY_GRAYBOX);
    private static final FrontierRuleset SUPPLY_QUARRY_GRAYBOX = supplyQuarry(FUELLED_QUARRY_GRAYBOX);
    private static final FrontierRuleset PREVIOUS_MARKET_QUARRY_GRAYBOX = marketQuarry(SUPPLY_QUARRY_GRAYBOX);
    private static final FrontierRuleset MARKET_QUARRY_GRAYBOX = longFuelReserve(PREVIOUS_MARKET_QUARRY_GRAYBOX);
    /** Existing worlds retain their exact selector and timing; they are never silently retuned. */
    private static final FrontierRuleset PREVIOUS_PRODUCTION_R6 = ruleset("frontier-v3-production-r6", 8, 1L, 20L, 1L);
    private static final FrontierRuleset PREVIOUS_PRODUCTION_R4 = ruleset("frontier-v3-production-r4", 6, 1L, 20L, 1L);
    /** Exact decoder for the accepted R3 world; it is never selected for a new world. */
    private static final FrontierRuleset PREVIOUS_PRODUCTION_R3 = ruleset("frontier-v3-production-r3", 5, 5L, 20L, 5L);
    /** Exact decoder for the accepted R2 world; it is never selected for a new world. */
    private static final FrontierRuleset PREVIOUS_PRODUCTION_R2 = ruleset("frontier-v3-production-r2", 4, 200L, 20L, 200L);
    /**
     * Explicit anchor for snapshots written before selector persistence existed. It is not a
     * current default: decoding old bytes is a named compatibility migration with fixed data.
     */
    private static final FrontierRuleset LEGACY_PRE_RULESET_R79 = ruleset("frontier-v3-legacy-pre-ruleset-r79", 3, 200L, 100L, 200L);
    private static final Map<String, FrontierRuleset> INSTALLED = Map.ofEntries(
            Map.entry(MARKET_QUARRY_GRAYBOX.id(), MARKET_QUARRY_GRAYBOX),
            Map.entry(PREVIOUS_MARKET_QUARRY_GRAYBOX.id(), PREVIOUS_MARKET_QUARRY_GRAYBOX),
            Map.entry(SUPPLY_QUARRY_GRAYBOX.id(), SUPPLY_QUARRY_GRAYBOX),
            Map.entry(TERRACED_QUARRY_GRAYBOX.id(), TERRACED_QUARRY_GRAYBOX),
            Map.entry(AVAILABLE_FRONT_QUARRY_GRAYBOX.id(), AVAILABLE_FRONT_QUARRY_GRAYBOX),
            Map.entry(HIGH_RESERVE_QUARRY_GRAYBOX.id(), HIGH_RESERVE_QUARRY_GRAYBOX),
            Map.entry(FUELLED_QUARRY_GRAYBOX.id(), FUELLED_QUARRY_GRAYBOX),
            Map.entry(QUARRY_GRAYBOX.id(), QUARRY_GRAYBOX),
            Map.entry(PREVIOUS_QUARRY_GRAYBOX_R3.id(), PREVIOUS_QUARRY_GRAYBOX_R3),
            Map.entry(PREVIOUS_QUARRY_GRAYBOX_R2.id(), PREVIOUS_QUARRY_GRAYBOX_R2),
            Map.entry(PREVIOUS_QUARRY_GRAYBOX.id(), PREVIOUS_QUARRY_GRAYBOX),
            Map.entry(PRODUCTION.id(), PRODUCTION), Map.entry(TRADE_PLAYTEST.id(), TRADE_PLAYTEST),
            Map.entry(EXPEDITION_CANDIDATE.id(), EXPEDITION_CANDIDATE),
            Map.entry(PREVIOUS_PRODUCTION_R14.id(), PREVIOUS_PRODUCTION_R14),
            Map.entry(PREVIOUS_TRADE_PLAYTEST_R3.id(), PREVIOUS_TRADE_PLAYTEST_R3),
            Map.entry(PREVIOUS_EXPEDITION_CANDIDATE_R1.id(), PREVIOUS_EXPEDITION_CANDIDATE_R1),
            Map.entry(PREVIOUS_PRODUCTION_R6.id(), PREVIOUS_PRODUCTION_R6),
            Map.entry(PREVIOUS_PRODUCTION_R4.id(), PREVIOUS_PRODUCTION_R4),
            Map.entry(PREVIOUS_PRODUCTION_R3.id(), PREVIOUS_PRODUCTION_R3),
            Map.entry(PREVIOUS_PRODUCTION_R2.id(), PREVIOUS_PRODUCTION_R2),
            Map.entry(LEGACY_PRE_RULESET_R79.id(), LEGACY_PRE_RULESET_R79));

    private FrontierRulesets() { }

    public static FrontierRuleset production() { return PRODUCTION; }
    public static FrontierRuleset fuelledQuarryGraybox() { return FUELLED_QUARRY_GRAYBOX; }
    public static FrontierRuleset supplyQuarryGraybox() { return SUPPLY_QUARRY_GRAYBOX; }
    public static FrontierRuleset marketQuarryGraybox() { return MARKET_QUARRY_GRAYBOX; }

    /** New finite-stock profile: the graybox has one shared baking station per settlement.
     * Two bakers cannot run that single station concurrently. Historical R11 is unchanged. */
    private static FrontierRuleset longFuelReserve(FrontierRuleset base) {
        int reserve = Math.toIntExact(Math.multiplyExact(64L, Math.ceilDiv(ProductionFuelReserve.requiredItems(
                base.production(), base.workCatalog(), BakeryRecipes.BREAD, "minecraft:coal", 64, 1, 100_000L), 64L)));
        var stocks = base.initialSettlementStocks().stream().map(stock -> stock.itemKind().equals("minecraft:coal")
                ? new InitialSettlementStock(stock.settlementId(), stock.itemKind(), reserve) : stock).toList();
        var priorTrade = base.goodsTrade();
        var goods = new java.util.EnumMap<GoodsPolicyKind, java.util.List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        goods.putAll(priorTrade.policies());
        goods.put(GoodsPolicyKind.PUBLIC_SETTLEMENT, goods.get(GoodsPolicyKind.PUBLIC_SETTLEMENT).stream().map(value ->
                value.itemKind().equals("minecraft:coal") ? new GoodsTradeRules.Commodity(value.itemKind(), 0, reserve,
                        value.minimumSellPrice(), value.maximumBuyPrice()) : value).toList());
        var trade = new GoodsTradeRules(priorTrade.reviewInterval(), priorTrade.orderLifetime(), priorTrade.maximumBatch(),
                priorTrade.maximumPairReviews(), goods, priorTrade.supplyPlanning(), priorTrade.pricing());
        var policies = new java.util.LinkedHashMap<>(trade.supplyPlanning().commodities());
        var coal = policies.get("minecraft:coal");
        policies.put("minecraft:coal", new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPolicy(
                coal.reorderPermille(), 32, 64, coal.leadTicks(), coal.maximumWaitTicks(), coal.essential()));
        var planning = trade.supplyPlanning();
        trade = trade.withSupplyPlanning(new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanningRules(
                policies, planning.maximumOrderItems(), planning.workCargoSlots(), planning.personalFoodItems(), planning.maximumTripCargoStacks()));
        return new FrontierRuleset("frontier-v3-quarry-graybox-r12", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), trade, stocks, base.labour(), base.expedition(), base.extraction(), base.execution(), base.production(), base.fields());
    }

    private static FrontierRuleset marketQuarry(FrontierRuleset base) {
        var trade = base.goodsTrade().withPricing(io.farfrontier.palemirror.frontier.v3.model.market.MarketPricingRules.initial())
                .withPriceSeeds(GoodsPolicyKind.PUBLIC_SETTLEMENT, "minecraft:bread", FixedScalar.whole(4), FixedScalar.whole(6))
                .withPriceSeeds(GoodsPolicyKind.OWN_ACCOUNT_COMPANY, "minecraft:bread", FixedScalar.whole(4), FixedScalar.whole(6));
        return new FrontierRuleset("frontier-v3-quarry-graybox-r11", 27, base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), trade,
                base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(), base.execution(), base.production(), base.fields());
    }

    private static FrontierRuleset supplyQuarry(FrontierRuleset base) {
        var planning = new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanningRules(Map.of(
                "minecraft:bread", new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPolicy(500, 32, 64, 12000, 12000, true),
                "minecraft:wheat", new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPolicy(500, 32, 64, 12000, 12000, false),
                "minecraft:coal", new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPolicy(500, 4, 8, 12000, 12000, false),
                "minecraft:cobblestone", new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPolicy(500, 64, 128, 12000, 12000, false)),
                512, 1, 8, 4);
        var expedition = base.expedition();
        var bounded = new io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionRules(expedition.personalStackSlots(), planning.personalFoodItems(),
                6, expedition.ticksPerRouteEdge(), expedition.destinationWorkTicks(),
                expedition.durationMarginPermille(), expedition.replenishmentBudget(), expedition.formationSpacing(), expedition.maxFormationStretch());
        return new FrontierRuleset("frontier-v3-quarry-graybox-r10", 26, base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade().withSupplyPlanning(planning), base.initialSettlementStocks(), base.labour(),
                bounded, base.extraction(), base.execution(), base.production(), base.fields());
    }

    private static FrontierRuleset fuelledQuarry(FrontierRuleset base) {
        var coal = new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Definition(
                "pale_mirror:coal_extraction", new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block("minecraft:coal_ore", Map.of()),
                new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block("minecraft:air", Map.of()),
                base.extraction().source().toolKind(), "minecraft:blocks/coal_ore",
                java.util.List.of(new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Output("minecraft:coal", 1)));
        var extraction = base.extraction().withPocket(new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPocketProfile(2, 1, 2, 2, 2, coal));
        var stocks = new java.util.ArrayList<InitialSettlementStock>();
        for (int home = 1; home <= 12; home++) {
            var id = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("settlement:" + home);
            stocks.add(new InitialSettlementStock(id, "minecraft:bread", 128));
            stocks.add(new InitialSettlementStock(id, "minecraft:coal", 4));
        }
        var trade = base.goodsTrade().withCommodity(GoodsPolicyKind.PUBLIC_SETTLEMENT,
                new GoodsTradeRules.Commodity("minecraft:coal", 0, 8, FixedScalar.whole(2), FixedScalar.whole(3)));
        return new FrontierRuleset("frontier-v3-quarry-graybox-r9", 25, base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog().withDefinition(new WorkCatalog.Definition("minecraft:coal_ore", WorkOperation.EXTRACT, HumanCapability.EXTRACTION, 140)),
                trade, stocks, base.labour(), base.expedition(), extraction, base.execution(), ProductionCatalog.fuelledGraybox(), new FieldBalance(16, 8));
    }

    private static FrontierRuleset expandingQuarry(FrontierRuleset base) {
        return new FrontierRuleset("frontier-v3-quarry-graybox-r3", 20, base.cadence(), base.spatial(), base.rates(), base.facilityCapacity(),
                base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(),
                io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionRules.expandingGraybox(), base.execution());
    }
    private static FrontierRuleset developingQuarry(FrontierRuleset base) {
        return new FrontierRuleset("frontier-v3-quarry-graybox-r4", 21, base.cadence(), base.spatial(), base.rates(), base.facilityCapacity(),
                base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(),
                io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionRules.developingGraybox(), base.execution());
    }
    private static FrontierRuleset terracedQuarry(FrontierRuleset base) {
        return new FrontierRuleset("frontier-v3-quarry-graybox-r6", 23, base.cadence(), base.spatial(), base.rates(), base.facilityCapacity(),
                base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog().withDefinition(new WorkCatalog.Definition("minecraft:grass_block", WorkOperation.EXTRACT,
                        HumanCapability.EXTRACTION, 40)), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(),
                io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionRules.terracedGraybox(), base.execution());
    }
    private static FrontierRuleset availableFrontQuarry(FrontierRuleset base) {
        return new FrontierRuleset("frontier-v3-quarry-graybox-r7", 24, base.cadence(), base.spatial(), base.rates(), base.facilityCapacity(),
                base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(),
                io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionRules.availableFrontGraybox(), base.execution());
    }

    /** Balance-only selector: production and public trade share the same larger stone target. */
    private static FrontierRuleset quarryReserve(FrontierRuleset base) {
        var extraction = base.extraction().withReserveItems(Math.multiplyExact(base.extraction().reserveItems(), 3));
        var priorTrade = base.goodsTrade();
        var policies = new java.util.EnumMap<GoodsPolicyKind, java.util.List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        policies.putAll(priorTrade.policies());
        var publicGoods = policies.get(GoodsPolicyKind.PUBLIC_SETTLEMENT);
        if (publicGoods.stream().noneMatch(value -> value.itemKind().equals("minecraft:cobblestone")))
            throw new IllegalArgumentException("quarry balance requires its declared stone commodity");
        policies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT, publicGoods.stream().map(value ->
                value.itemKind().equals("minecraft:cobblestone") ? new GoodsTradeRules.Commodity(value.itemKind(),
                        value.targetPerResident(), extraction.reserveItems(), value.minimumSellPrice(), value.maximumBuyPrice()) : value).toList());
        var trade = new GoodsTradeRules(priorTrade.reviewInterval(), priorTrade.orderLifetime(),
                priorTrade.maximumBatch(), priorTrade.maximumPairReviews(), policies);
        return new FrontierRuleset("frontier-v3-quarry-graybox-r8", base.schemaVersion(), base.cadence(), base.spatial(),
                base.rates(), base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(),
                base.resourceHarvestColdTravelTicksPerEdge(), base.workCatalog(), trade, base.initialSettlementStocks(),
                base.labour(), base.expedition(), extraction, base.execution());
    }

    private static FrontierRuleset populationExecution(String id, FrontierRuleset base) {
        return new FrontierRuleset(id, 19, base.cadence(), base.spatial(), base.rates(), base.facilityCapacity(),
                base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(),
                base.extraction(), FrontierRuleset.Execution.population());
    }

    /** A new pinned balance selector; old worlds retain their exact expedition clock and digest. */
    private static FrontierRuleset caravanPace(String id, FrontierRuleset base, long ticksPerEdge) {
        var prior = base.expedition();
        var expedition = new io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionRules(
                prior.personalStackSlots(), prior.personalFoodItems(), prior.packAnimalStackSlots(), ticksPerEdge,
                prior.destinationWorkTicks(), prior.durationMarginPermille(), prior.replenishmentBudget(),
                prior.formationSpacing(), prior.maxFormationStretch());
        return new FrontierRuleset(id, base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(),
                base.resourceHarvestColdTravelTicksPerEdge(), base.workCatalog(), base.goodsTrade(),
                base.initialSettlementStocks(), base.labour(), expedition);
    }

    /** Explicit installed configuration, including its complete pinned digest on persistence. */
    public static FrontierRuleset installed(String id) {
        var selected = INSTALLED.get(id);
        if (selected == null) throw new IllegalArgumentException("unavailable Frontier ruleset: " + id);
        return selected;
    }

    public static FrontierRuleset legacyForSnapshotVersion(int version) {
        if (version < 41 || version > 79) throw new IllegalArgumentException("no explicit legacy ruleset for state version " + version);
        return LEGACY_PRE_RULESET_R79;
    }

    public static FrontierRuleset require(String id, int schemaVersion, String contentSha256) {
        FrontierRuleset ruleset = INSTALLED.get(id);
        if (ruleset == null || ruleset.schemaVersion() != schemaVersion || !ruleset.contentSha256().equals(contentSha256)) {
            throw new IllegalArgumentException("unavailable or incompatible Frontier ruleset: " + id);
        }
        return ruleset;
    }

    private static FrontierRuleset ruleset(String id, int schemaVersion, long resourceHarvestTraversalInterval,
                                           long routePatrolStepInterval, long coldTravelTicksPerEdge) {
        return new FrontierRuleset(id, schemaVersion,
                new FrontierRuleset.Cadence(1L, 3_000L, resourceHarvestTraversalInterval, 200L,
                        24_000L, 400L, 24_000L, 200L, 24_000L, 24_000L, 1_200L, 20L, 1_200L, 400L, 24_000L, 600L, 100L,
                        20L, 800L, 20L, 20L, routePatrolStepInterval, 6_000L, 200L, 200L, 100L, 800L, 900L, 1_000L, 2_000L,
                        6_000L, 1_000L, 8_000L, 1_600L, 20L, 3_200L, 100L, 2_400L, 2_400L),
                new FrontierRuleset.Spatial(160, 160, 32, 48, 64, 128, 16, 64, 12),
                new FrontierRuleset.Rates(new FixedScalar(125_000L), new FixedScalar(250_000L), FixedScalar.whole(100L), FixedScalar.whole(2L)),
                new FrontierRuleset.FacilityCapacity(48, 16, 4, 8, 4, 2, 3, 1),
                new FrontierRuleset.Combat(FixedScalar.whole(4), FixedScalar.whole(2), FixedScalar.whole(6), FixedScalar.whole(3), FixedScalar.ONE),
                new FrontierRuleset.HiveCommand(6, 1, 2, 1, 200L, 100L), FrontierRuleset.ResidentLife.initial(),
                coldTravelTicksPerEdge);
    }
}
