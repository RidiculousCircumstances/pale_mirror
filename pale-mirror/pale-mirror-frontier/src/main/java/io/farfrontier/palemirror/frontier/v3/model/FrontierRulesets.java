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
    private static final FrontierRuleset PREVIOUS_STOCKED_QUARRY_GRAYBOX = longFuelReserve(PREVIOUS_MARKET_QUARRY_GRAYBOX);
    private static final FrontierRuleset PREVIOUS_DEPOSIT_QUARRY_GRAYBOX = longFuelDeposits(PREVIOUS_MARKET_QUARRY_GRAYBOX);
    private static final FrontierRuleset DYNAMIC_RESERVE_QUARRY_GRAYBOX = dynamicReserves(PREVIOUS_DEPOSIT_QUARRY_GRAYBOX);
    private static final FrontierRuleset DISTRIBUTED_QUARRY_GRAYBOX = distributedDeposits(DYNAMIC_RESERVE_QUARRY_GRAYBOX);
    private static final FrontierRuleset TERRITORIAL_QUARRY_GRAYBOX = territorialAgriculture(DISTRIBUTED_QUARRY_GRAYBOX);
    private static final FrontierRuleset PREVIOUS_ECONOMIC_QUARRY_GRAYBOX = economicPlanning(TERRITORIAL_QUARRY_GRAYBOX);
    private static final FrontierRuleset PREVIOUS_FUEL_TRADE_QUARRY_GRAYBOX = balancedFuelTrade(PREVIOUS_ECONOMIC_QUARRY_GRAYBOX);
    private static final FrontierRuleset PREVIOUS_PRODUCTIVE_QUARRY_GRAYBOX = productiveMiningTerritories(PREVIOUS_FUEL_TRADE_QUARRY_GRAYBOX);
    private static final FrontierRuleset MARKET_QUARRY_GRAYBOX = coherentEconomy(PREVIOUS_PRODUCTIVE_QUARRY_GRAYBOX);
    private static final FrontierRuleset PREVIOUS_DYNAMIC_FARMING_GRAYBOX = dynamicFarming(MARKET_QUARRY_GRAYBOX);
    private static final FrontierRuleset DYNAMIC_FARMING_GRAYBOX = higherFuelDemand(PREVIOUS_DYNAMIC_FARMING_GRAYBOX);
    private static final FrontierRuleset MINERAL_DEPOSITS_GRAYBOX = ironDeposits(DYNAMIC_FARMING_GRAYBOX);
    private static final FrontierRuleset SMELTING_GRAYBOX = smelting(MINERAL_DEPOSITS_GRAYBOX);
    private static final FrontierRuleset TOOL_GRAYBOX = GrayboxToolProduction.extend(SMELTING_GRAYBOX);
    private static final FrontierRuleset PREVIOUS_DISTANT_TOOL_GRAYBOX = distant(TOOL_GRAYBOX);
    private static final FrontierRuleset DISTANT_TOOL_GRAYBOX = operatingPrices(PREVIOUS_DISTANT_TOOL_GRAYBOX);
    /** New-world startup references match current recipes; historical profiles stay immutable. */
    private static FrontierRuleset operatingPrices(FrontierRuleset base) {
        var trade = base.goodsTrade().withPriceSeeds(GoodsPolicyKind.PUBLIC_SETTLEMENT,
                "minecraft:coal", FixedScalar.whole(2), FixedScalar.whole(3));
        return new FrontierRuleset("frontier-v3-quarry-graybox-r27", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), trade, base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(),
                base.execution(), base.production(), base.fields(), base.tools(), base.worldLayout());
    }
    private static FrontierRuleset distant(FrontierRuleset base) {
        var layout = FrontierWorldLayout.distant();
        var fields = new FieldBalance(base.fields().ordinarySide(), base.fields().extractiveSide(),
                java.util.Optional.of(io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture.forLayout(layout)),
                base.fields().cultivation());
        return new FrontierRuleset("frontier-v3-quarry-graybox-r26", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(),
                base.execution(), base.production(), fields, base.tools(), layout);
    }
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
            Map.entry(TOOL_GRAYBOX.id(), TOOL_GRAYBOX),
            Map.entry(DISTANT_TOOL_GRAYBOX.id(), DISTANT_TOOL_GRAYBOX),
            Map.entry(PREVIOUS_DISTANT_TOOL_GRAYBOX.id(), PREVIOUS_DISTANT_TOOL_GRAYBOX),
            Map.entry(MINERAL_DEPOSITS_GRAYBOX.id(), MINERAL_DEPOSITS_GRAYBOX),
            Map.entry(SMELTING_GRAYBOX.id(), SMELTING_GRAYBOX),
            Map.entry(DYNAMIC_FARMING_GRAYBOX.id(), DYNAMIC_FARMING_GRAYBOX),
            Map.entry(PREVIOUS_DYNAMIC_FARMING_GRAYBOX.id(), PREVIOUS_DYNAMIC_FARMING_GRAYBOX),
            Map.entry(MARKET_QUARRY_GRAYBOX.id(), MARKET_QUARRY_GRAYBOX),
            Map.entry(PREVIOUS_PRODUCTIVE_QUARRY_GRAYBOX.id(), PREVIOUS_PRODUCTIVE_QUARRY_GRAYBOX),
            Map.entry(PREVIOUS_FUEL_TRADE_QUARRY_GRAYBOX.id(), PREVIOUS_FUEL_TRADE_QUARRY_GRAYBOX),
            Map.entry(PREVIOUS_ECONOMIC_QUARRY_GRAYBOX.id(), PREVIOUS_ECONOMIC_QUARRY_GRAYBOX),
            Map.entry(TERRITORIAL_QUARRY_GRAYBOX.id(), TERRITORIAL_QUARRY_GRAYBOX),
            Map.entry(DISTRIBUTED_QUARRY_GRAYBOX.id(), DISTRIBUTED_QUARRY_GRAYBOX),
            Map.entry(DYNAMIC_RESERVE_QUARRY_GRAYBOX.id(), DYNAMIC_RESERVE_QUARRY_GRAYBOX),
            Map.entry(PREVIOUS_DEPOSIT_QUARRY_GRAYBOX.id(), PREVIOUS_DEPOSIT_QUARRY_GRAYBOX),
            Map.entry(PREVIOUS_MARKET_QUARRY_GRAYBOX.id(), PREVIOUS_MARKET_QUARRY_GRAYBOX),
            Map.entry(PREVIOUS_STOCKED_QUARRY_GRAYBOX.id(), PREVIOUS_STOCKED_QUARRY_GRAYBOX),
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
    public static FrontierRuleset dynamicFarmingGraybox() { return DYNAMIC_FARMING_GRAYBOX; }
    public static FrontierRuleset mineralDepositsGraybox() { return MINERAL_DEPOSITS_GRAYBOX; }
    public static FrontierRuleset smeltingGraybox() { return SMELTING_GRAYBOX; }
    public static FrontierRuleset toolGraybox() { return TOOL_GRAYBOX; }

    private static FrontierRuleset smelting(FrontierRuleset base) {
        var id = new ProductionRecipe.Id("pale_mirror:iron_smelting");
        var recipes = new java.util.ArrayList<>(base.production().recipes());
        recipes.add(new ProductionRecipe(id, ProductionStationSpec.Capability.SMELTING,
                new ProductionRecipe.Ingredient(java.util.Set.of("minecraft:raw_iron"), 1),
                new ProductionRecipe.Product("minecraft:iron_ingot", 1), 200, ProductionRecipe.ProcessingMode.AUTOMATIC));
        var equipment = new java.util.ArrayList<>(base.production().equipment()); equipment.add(ProductionEquipment.smelter());
        var production = new ProductionCatalog(recipes, base.production().fuels(), equipment,
                java.util.List.of(new ProductionCatalog.AutomaticPolicy(id, 64, 8)));
        var policies = new java.util.EnumMap<GoodsPolicyKind, java.util.List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        policies.putAll(base.goodsTrade().policies());
        var commodities = new java.util.ArrayList<>(policies.get(GoodsPolicyKind.PUBLIC_SETTLEMENT));
        commodities.add(new GoodsTradeRules.Commodity("minecraft:iron_ingot", 0, 0, FixedScalar.whole(3), FixedScalar.whole(5)));
        policies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT, java.util.List.copyOf(commodities));
        var prior = base.goodsTrade().supplyPlanning(); var supplies = new java.util.HashMap<>(prior.commodities());
        supplies.put("minecraft:iron_ingot", new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPolicy(500, 64, 64, 12000, 12000, false));
        var planning = new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanningRules(supplies,
                prior.maximumOrderItems(), prior.workCargoSlots(), prior.personalFoodItems(), prior.maximumTripCargoStacks(),
                prior.reserves(), prior.sourcing(), prior.economics());
        var trade = new GoodsTradeRules(base.goodsTrade().reviewInterval(), base.goodsTrade().orderLifetime(),
                base.goodsTrade().maximumBatch(), base.goodsTrade().maximumPairReviews(), policies, planning, base.goodsTrade().pricing());
        return new FrontierRuleset("frontier-v3-quarry-graybox-r24", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog().withDefinition(new WorkCatalog.Definition("minecraft:iron_ingot", WorkOperation.PRODUCE,
                        HumanCapability.INDUSTRY, 200)), trade, base.initialSettlementStocks(), base.labour(), base.expedition(),
                base.extraction(), base.execution(), production, base.fields());
    }

    /** Finite iron sources, not a stock grant or a reason to manufacture demand.
     * Existing coal installations remain unchanged; every second mining town
     * also knows a separate iron-bearing installation. */
    private static FrontierRuleset ironDeposits(FrontierRuleset base) {
        var iron = new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Definition(
                "pale_mirror:iron_extraction",
                new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block("minecraft:iron_ore", Map.of()),
                new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block("minecraft:air", Map.of()),
                new io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement(
                        io.farfrontier.palemirror.frontier.v3.model.tooling.ToolAbilities.MINE, 1, 2), "minecraft:blocks/iron_ore",
                java.util.List.of(new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Output("minecraft:raw_iron", 1)));
        var pockets = new java.util.ArrayList<>(base.extraction().additionalPockets());
        pockets.add(new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPocketProfile(2, 2, 16, 1, 6, iron));
        var policies = new java.util.EnumMap<GoodsPolicyKind, java.util.List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        policies.putAll(base.goodsTrade().policies());
        var commodities = new java.util.ArrayList<>(policies.get(GoodsPolicyKind.PUBLIC_SETTLEMENT));
        commodities.add(new GoodsTradeRules.Commodity("minecraft:raw_iron", 0, 0, FixedScalar.whole(2), FixedScalar.whole(3)));
        policies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT, java.util.List.copyOf(commodities));
        var prior = base.goodsTrade().supplyPlanning();
        var supplies = new java.util.HashMap<>(prior.commodities());
        supplies.put("minecraft:raw_iron", new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPolicy(500, 64, 64, 12000, 12000, false));
        var planning = new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanningRules(supplies,
                prior.maximumOrderItems(), prior.workCargoSlots(), prior.personalFoodItems(), prior.maximumTripCargoStacks(),
                prior.reserves(), prior.sourcing(), prior.economics());
        var trade = new GoodsTradeRules(base.goodsTrade().reviewInterval(), base.goodsTrade().orderLifetime(),
                base.goodsTrade().maximumBatch(), base.goodsTrade().maximumPairReviews(), policies, planning, base.goodsTrade().pricing());
        return new FrontierRuleset("frontier-v3-quarry-graybox-r23", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog().withDefinition(new WorkCatalog.Definition("minecraft:iron_ore", WorkOperation.EXTRACT,
                        HumanCapability.EXTRACTION, 210)), trade, base.initialSettlementStocks(), base.labour(), base.expedition(),
                base.extraction().withAdditionalPockets(pockets), base.execution(), base.production(), base.fields());
    }

    /** Explicit balance trial: eightfold baking heat and fourfold finite ore volume.
     * Prices, fuel energy, loot, population and initial warehouse stocks stay unchanged. */
    private static FrontierRuleset higherFuelDemand(FrontierRuleset base) {
        var catalog = base.production();
        var production = new ProductionCatalog(catalog.recipes().stream().map(recipe ->
                recipe.id().equals(BakeryRecipes.BREAD)
                        ? new ProductionRecipe(recipe.id(), recipe.capability(), recipe.ingredient(), recipe.product(),
                                Math.multiplyExact(recipe.heatPerBatch(), 8L), recipe.processingMode())
                        : recipe).toList(), catalog.fuels(), catalog.equipment());
        java.util.function.UnaryOperator<io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPocketProfile> larger =
                pocket -> new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPocketProfile(
                        pocket.producerPeriod(), pocket.firstDepth(), Math.multiplyExact(pocket.depthCells(), 2),
                        pocket.firstRow(), Math.multiplyExact(pocket.rowCells(), 2), pocket.material());
        var extraction = base.extraction().withPocket(larger.apply(base.extraction().pocket().orElseThrow()))
                .withAdditionalPockets(base.extraction().additionalPockets().stream().map(larger).toList());
        return new FrontierRuleset("frontier-v3-quarry-graybox-r22", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(), extraction,
                base.execution(), production, base.fields());
    }

    private static FrontierRuleset dynamicFarming(FrontierRuleset base) {
        return new FrontierRuleset("frontier-v3-quarry-graybox-r21", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(),
                base.execution(), base.production(), new FieldBalance(base.fields().ordinarySide(), base.fields().extractiveSide(),
                        base.fields().agriculture(), java.util.Optional.of(io.farfrontier.palemirror.frontier.v3.model.agriculture.CultivationRules.graybox())));
    }

    private static FrontierRuleset coherentEconomy(FrontierRuleset base) {
        var prior = base.expedition();
        var expedition = new io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionRules(prior.personalStackSlots(),
                prior.personalFoodItems(), prior.packAnimalStackSlots(), prior.ticksPerRouteEdge(), prior.destinationWorkTicks(),
                prior.durationMarginPermille(), io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO,
                prior.formationSpacing(), prior.maxFormationStretch());
        return new FrontierRuleset("frontier-v3-quarry-graybox-r20", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade().withPricing(io.farfrontier.palemirror.frontier.v3.model.market.MarketPricingRules.adaptive()),
                base.initialSettlementStocks(), base.labour(), expedition, base.extraction(), base.execution(), base.production(), base.fields());
    }

    /** Mining towns have small fields: their export opportunity must be in the
     * finite terrain, not an initial stock grant or a guaranteed buyer. */
    private static FrontierRuleset productiveMiningTerritories(FrontierRuleset base) {
        java.util.function.UnaryOperator<io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPocketProfile> everyProducer =
                pocket -> new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPocketProfile(1,
                        pocket.firstDepth(), pocket.depthCells(), pocket.firstRow(), pocket.rowCells(), pocket.material());
        var extraction = base.extraction().withPocket(everyProducer.apply(base.extraction().pocket().orElseThrow()))
                .withAdditionalPockets(base.extraction().additionalPockets().stream().map(everyProducer).toList());
        return new FrontierRuleset("frontier-v3-quarry-graybox-r19", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(), extraction,
                base.execution(), base.production(), base.fields());
    }

    /** A price seed, not a guaranteed sale or subsidy. One coal heats twenty
     * bread (sixty wheat); the old two-unit seed made fuel exports negligible
     * against a mining settlement's recurring grain imports. Recipes and cash
     * stay unchanged, and the existing market still adapts both quotes. */
    private static FrontierRuleset balancedFuelTrade(FrontierRuleset base) {
        var trade = base.goodsTrade().withPriceSeeds(GoodsPolicyKind.PUBLIC_SETTLEMENT,
                "minecraft:coal", FixedScalar.whole(20), FixedScalar.whole(30));
        return new FrontierRuleset("frontier-v3-quarry-graybox-r18", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), trade, base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(),
                base.execution(), base.production(), base.fields());
    }

    private static FrontierRuleset economicPlanning(FrontierRuleset base) {
        var prior = base.goodsTrade().supplyPlanning();
        var planning = new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanningRules(prior.commodities(),
                prior.maximumOrderItems(), prior.workCargoSlots(), prior.personalFoodItems(), prior.maximumTripCargoStacks(),
                prior.reserves(), prior.sourcing(), java.util.Optional.of(
                    new io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlanningRules(4, 4, 256)));
        return new FrontierRuleset("frontier-v3-quarry-graybox-r17", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade().withSupplyPlanning(planning), base.initialSettlementStocks(), base.labour(), base.expedition(),
                base.extraction(), base.execution(), base.production(), base.fields());
    }

    private static FrontierRuleset territorialAgriculture(FrontierRuleset base) {
        var prior = base.goodsTrade().supplyPlanning();
        var planning = new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanningRules(prior.commodities(),
                prior.maximumOrderItems(), prior.workCargoSlots(), prior.personalFoodItems(), prior.maximumTripCargoStacks(),
                prior.reserves(), java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.model.supply.SourcingRules(10000, 100, 1500)));
        return new FrontierRuleset("frontier-v3-quarry-graybox-r16", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade().withSupplyPlanning(planning), base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(),
                base.execution(), base.production(), new FieldBalance(base.fields().ordinarySide(), base.fields().extractiveSide(),
                    java.util.Optional.of(io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture.rules())));
    }

    private static FrontierRuleset distributedDeposits(FrontierRuleset base) {
        var prior = base.extraction().pocket().orElseThrow();
        var extraction = base.extraction().withAdditionalPockets(java.util.List.of(
                new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPocketProfile(
                    prior.producerPeriod(), 4, 16, 0, 12, prior.material())));
        return new FrontierRuleset("frontier-v3-quarry-graybox-r15", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), base.initialSettlementStocks(), base.labour(), base.expedition(), extraction,
                base.execution(), base.production(), base.fields());
    }

    private static FrontierRuleset dynamicReserves(FrontierRuleset base) {
        var prior = base.goodsTrade().supplyPlanning();
        var planning = new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanningRules(prior.commodities(),
                prior.maximumOrderItems(), prior.workCargoSlots(), prior.personalFoodItems(), prior.maximumTripCargoStacks(),
                java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.model.supply.ReservePolicy(
                        base.goodsTrade().reviewInterval(), 500)));
        return new FrontierRuleset("frontier-v3-quarry-graybox-r14", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade().withSupplyPlanning(planning), base.initialSettlementStocks(), base.labour(),
                base.expedition(), base.extraction(), base.execution(), base.production(), base.fields());
    }

    /** Coal stays finite geology, not a warehouse grant. Ordinary mining and trade obtain it. */
    private static FrontierRuleset longFuelDeposits(FrontierRuleset base) {
        var prior = base.extraction().pocket().orElseThrow();
        var extraction = base.extraction().withPocket(new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPocketProfile(
                prior.producerPeriod(), 0, 32, 2, 21, prior.material()));
        var stocks = base.initialSettlementStocks().stream().filter(stock -> !stock.itemKind().equals("minecraft:coal")).toList();
        return new FrontierRuleset("frontier-v3-quarry-graybox-r13", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog(), base.goodsTrade(), stocks, base.labour(), base.expedition(), extraction, base.execution(), base.production(), base.fields());
    }

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
                base.extraction().source().toolRequirement(), "minecraft:blocks/coal_ore",
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
