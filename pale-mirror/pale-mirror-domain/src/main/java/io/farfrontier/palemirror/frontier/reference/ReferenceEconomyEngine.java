package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Source-port of Python {@code EconomyEngine}. Trade, companies and contracts
 * remain later Wave 1 owners; this class never reads or writes Minecraft state.
 */
public final class ReferenceEconomyEngine {
    private static final double FOOD_PER_PERSON = 0.065d;
    private static final double MEDICINE_PER_PERSON = 0.0007d;
    private static final double MEDICINE_THREAT_MULTIPLIER = 9.0d;
    private static final double ENERGY_PER_PERSON = 0.003d;
    private static final double ENERGY_PER_FORTIFICATION = 0.6d;
    private static final double MINIMUM_LABOUR = 0.35d;
    private static final double TIMBER_MINIMUM = 0.5d;
    private static final double TIMBER_POPULATION_DIVISOR = 600.0d;
    private static final double WEAPONS_MINIMUM = 0.25d;
    private static final double WEAPONS_PER_PERSON = 0.0008d;
    private static final double WEAPONS_THREAT_MULTIPLIER = 5.0d;
    private static final double AMMO_MINIMUM = 1.0d;
    private static final double AMMO_PER_PERSON = 0.004d;
    private static final double AMMO_THREAT_MULTIPLIER = 6.0d;
    private static final double CONTAMINATION_OUTPUT_PENALTY = 0.72d;
    private static final double HAUL_LOSS_PER_INFECTION = 0.16d;
    private static final double DEFENCE_THREAT_MULTIPLIER = 2.0d;
    private static final double SELLABLE_TARGET_MULTIPLIER = 1.05d;
    private static final double MINIMUM_STOCK_TARGET_FRACTION = 0.025d;
    private static final double MINIMUM_REFERENCE_FRACTION = 0.12d;
    private static final double MAXIMUM_REFERENCE_FRACTION = 30.0d;
    private static final int INVESTMENT_COOLDOWN_DAYS = 24;
    private static final double FORTIFICATION_BASE_SCORE = 0.6d;
    private static final double FORTIFICATION_THREAT_SCORE = 7.5d;
    private static final double FORTIFICATION_INTEGRITY_DIVISOR = 25.0d;
    private static final double ARMORY_THREAT_BONUS = 2.5d;
    private static final double CLINIC_THREAT_BONUS = 1.5d;
    private static final double DIMINISHING_RETURNS = 0.22d;
    private static final double DEFENCE_THREAT_THRESHOLD = 0.45d;
    private static final double DEFENCE_PRIORITY = 1.8d;
    private static final double TIMBER_MATERIAL_WEIGHT = 0.02d;
    private static final double ORE_MATERIAL_WEIGHT = 0.025d;
    private static final double TOOLS_MATERIAL_WEIGHT = 0.08d;
    private static final double INVESTMENT_MINIMUM_SCORE = 0.1d;

    private final ReferenceSimulationProfile profile;

    public ReferenceEconomyEngine(ReferenceSimulationProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public ReferenceSimulationProfile profile() {
        return profile;
    }

    public double foodPerPerson() {
        return FOOD_PER_PERSON;
    }

    public double humanAmount(double sourceAmount) {
        return profile.humanAmountFromSource(sourceAmount);
    }

    public ReferenceResourceRule resourceRule(ReferenceResource resource) {
        ReferenceResourceRule source = sourceResourceRule(resource);
        return new ReferenceResourceRule(
                source.referenceValue(), source.reserveDays(), source.scarcityElasticity(),
                humanAmount(source.minimumTarget()));
    }

    public void resetDailyFlows(ReferenceSettlement settlement) {
        settlement.resetDailyFlows();
    }

    public double siteProductionPhase(ReferenceSettlement settlement, ReferenceResourceSite site, double ecologyFactor) {
        if (!settlement.alive() || !site.operational() || !Objects.equals(site.ownerId(), settlement.id())) return 0.0d;
        SiteSpec spec = siteSpec(site.kind());
        double planned = spec.yield() * site.capacity() * site.quality()
                * boundedUnit(site.condition())
                * (1.0d - site.contamination() * CONTAMINATION_OUTPUT_PENALTY)
                * settlement.laborFactor() * boundedUnit(ecologyFactor);
        return produceAtSite(settlement, site, spec, planned);
    }

    public double haulSiteOutput(ReferenceSettlement settlement, ReferenceResourceSite site, double routeInfection) {
        if (!settlement.alive() || !Objects.equals(site.ownerId(), settlement.id())) return 0.0d;
        double loss = Math.min(0.85d, nonNegative(routeInfection) * HAUL_LOSS_PER_INFECTION);
        double shipped = site.remove(site.resource(), site.haulCapacity());
        double delivered = shipped * (1.0d - loss);
        settlement.add(site.resource(), delivered);
        return delivered;
    }

    public void productionPhase(ReferenceSettlement settlement) {
        if (!settlement.alive()) return;
        double labour = settlement.laborFactor();
        ReferenceFacilities facilities = settlement.facilities();
        produceWithInputs(settlement, ReferenceResource.TOOLS, 9.0d * facilities.workshop() * labour,
                inputs(ReferenceResource.ORE, 1.2d, ReferenceResource.ENERGY, 0.65d));
        produceWithInputs(settlement, ReferenceResource.MEDICINE, 12.0d * facilities.clinic() * labour,
                inputs(ReferenceResource.FOOD, 0.4d, ReferenceResource.ENERGY, 0.25d));
        double armoryFactor = facilities.armory() * labour;
        produceWithInputs(settlement, ReferenceResource.WEAPONS, 2.2d * armoryFactor,
                inputs(ReferenceResource.ORE, 2.5d, ReferenceResource.TOOLS, 0.2d, ReferenceResource.ENERGY, 1.1d));
        produceWithInputs(settlement, ReferenceResource.AMMO, 12.0d * armoryFactor,
                inputs(ReferenceResource.ORE, 0.22d, ReferenceResource.ENERGY, 0.1d));
    }

    public void consumptionPhase(ReferenceSettlement settlement) {
        if (!settlement.alive()) return;
        double foodNeed = settlement.population() * FOOD_PER_PERSON;
        double food = settlement.remove(ReferenceResource.FOOD, foodNeed);
        settlement.recordConsumption(ReferenceResource.FOOD, food);
        settlement.foodFulfillment(foodNeed <= 0.0d ? 1.0d : Math.min(1.0d, food / foodNeed));
        double medicineNeed = settlement.population() * MEDICINE_PER_PERSON
                * (1.0d + MEDICINE_THREAT_MULTIPLIER * settlement.threat());
        double medicine = settlement.remove(ReferenceResource.MEDICINE, medicineNeed);
        settlement.recordConsumption(ReferenceResource.MEDICINE, medicine);
        settlement.medicineFulfillment(medicineNeed <= 0.0d ? 1.0d : Math.min(1.0d, medicine / medicineNeed));
        double energyNeed = settlement.population() * ENERGY_PER_PERSON
                + settlement.facilities().fortification() * ENERGY_PER_FORTIFICATION;
        settlement.recordConsumption(ReferenceResource.ENERGY, settlement.remove(ReferenceResource.ENERGY, energyNeed));
    }

    public double expectedDailyDemand(ReferenceSettlement settlement, ReferenceResource resource) {
        double labour = Math.max(MINIMUM_LABOUR, settlement.laborFactor());
        ReferenceFacilities c = settlement.facilities();
        return switch (resource) {
            case FOOD -> settlement.population() * FOOD_PER_PERSON + c.clinic() * 12.0d * 0.4d;
            case SEEDS -> settlement.primaryCapacity("farm") * 1.8d;
            case ENERGY -> (settlement.population() * ENERGY_PER_PERSON + c.fortification() * ENERGY_PER_FORTIFICATION
                    + siteInput(settlement, "farm", 0.006d) + siteInput(settlement, "mine", 0.28d)
                    + siteInput(settlement, "forest", 0.1d) + c.workshop() * 9.0d * 0.65d
                    + c.clinic() * 12.0d * 0.25d + c.armory() * (2.2d * 1.1d + 12.0d * 0.1d)) * labour;
            case TOOLS -> (siteInput(settlement, "farm", 0.002d) + siteInput(settlement, "mine", 0.045d)
                    + siteInput(settlement, "forest", 0.025d) + c.armory() * 2.2d * 0.2d) * labour;
            case ORE -> (c.workshop() * 9.0d * 1.2d + c.armory() * (2.2d * 2.5d + 12.0d * 0.22d)) * labour;
            case TIMBER -> Math.max(humanAmount(TIMBER_MINIMUM), settlement.population() / TIMBER_POPULATION_DIVISOR);
            case MEDICINE -> settlement.population() * MEDICINE_PER_PERSON
                    * (1.0d + MEDICINE_THREAT_MULTIPLIER * settlement.threat());
            case WEAPONS -> Math.max(humanAmount(WEAPONS_MINIMUM), settlement.population() * WEAPONS_PER_PERSON
                    * (1.0d + WEAPONS_THREAT_MULTIPLIER * settlement.threat()));
            case AMMO -> Math.max(humanAmount(AMMO_MINIMUM), settlement.population() * AMMO_PER_PERSON
                    * (1.0d + AMMO_THREAT_MULTIPLIER * settlement.threat()));
        };
    }

    public double targetStock(ReferenceSettlement settlement, ReferenceResource resource) {
        ReferenceResourceRule rule = resourceRule(resource);
        double target = expectedDailyDemand(settlement, resource) * rule.reserveDays();
        if (resource == ReferenceResource.WEAPONS || resource == ReferenceResource.AMMO || resource == ReferenceResource.MEDICINE) {
            target *= 1.0d + DEFENCE_THREAT_MULTIPLIER * settlement.threat();
        }
        return Math.max(rule.minimumTarget(), target);
    }

    public double coverageDays(ReferenceSettlement settlement, ReferenceResource resource) {
        double demand = expectedDailyDemand(settlement, resource);
        return demand <= 1.0e-9d ? Double.POSITIVE_INFINITY : settlement.amount(resource) / demand;
    }

    public double localValue(ReferenceSettlement settlement, ReferenceResource resource) {
        ReferenceResourceRule rule = resourceRule(resource);
        double target = targetStock(settlement, resource);
        double stock = Math.max(settlement.amount(resource), target * MINIMUM_STOCK_TARGET_FRACTION);
        double value = rule.referenceValue() * Math.pow(target / stock, rule.scarcityElasticity());
        return Math.max(rule.referenceValue() * MINIMUM_REFERENCE_FRACTION,
                Math.min(value, rule.referenceValue() * MAXIMUM_REFERENCE_FRACTION));
    }

    public double sellableQuantity(ReferenceSettlement settlement, ReferenceResource resource) {
        return settlement.alive() ? Math.max(0.0d, settlement.amount(resource) - targetStock(settlement, resource)
                * SELLABLE_TARGET_MULTIPLIER) : 0.0d;
    }

    public double desiredImport(ReferenceSettlement settlement, ReferenceResource resource) {
        return settlement.alive() ? Math.max(0.0d, targetStock(settlement, resource) - settlement.amount(resource)) : 0.0d;
    }

    /**
     * Apply a completed civic site project. Ecological effects deliberately do
     * not scale with the population profile; only material inputs do.
     */
    public boolean siteProject(ReferenceSettlement settlement, ReferenceResourceSite site, String action) {
        SiteProjectSpec project = siteProjectSpec(action);
        if (project == null || !canPaySiteMaterials(settlement, project.materials())) return false;
        paySiteMaterials(settlement, project.materials());
        switch (action) {
            case "upgrade" -> site.capacity(site.capacity() + humanAmount(0.25d));
            case "repair" -> site.condition(Math.min(1.0d, site.condition() + 0.32d));
            case "cleanse" -> {
                site.contamination(Math.max(0.0d, site.contamination() - project.effect()));
                site.substrate(site.substrate() * (1.0d - project.effect()));
            }
            case "scorch" -> {
                site.contamination(Math.max(0.0d, site.contamination() - project.effect()));
                site.condition(Math.max(0.0d, site.condition() - project.conditionLoss()));
                site.substrate(site.substrate() * (1.0d - project.effect()));
            }
            case "restore" -> { /* Python consumes the listed materials but changes no site field. */ }
            default -> throw new IllegalStateException("checked site project was not handled: " + action);
        }
        return true;
    }

    public boolean canStartClaim(ReferenceSettlement settlement) {
        return canPaySiteMaterials(settlement, siteProjectSpec("claim").materials());
    }

    public void payClaimMaterials(ReferenceSettlement settlement) {
        paySiteMaterials(settlement, siteProjectSpec("claim").materials());
    }

    /**
     * Select at most one real-material investment. Internal cash transfers
     * cancel at settlement scope, exactly as in the Python aggregate model.
     */
    public String invest(
            ReferenceSettlement settlement,
            int day,
            Map<ReferenceResource, Double> accessibleValues,
            Map<String, Double> doctrineWeights
    ) {
        Objects.requireNonNull(accessibleValues, "accessibleValues");
        if (!settlement.alive() || day - settlement.lastInvestmentDay() < INVESTMENT_COOLDOWN_DAYS) return null;
        ReferenceInvestmentSpec chosen = null;
        double chosenScore = Double.NEGATIVE_INFINITY;
        for (ReferenceInvestmentSpec spec : investmentSpecs()) {
            if (!canAffordMaterials(settlement, spec)) continue;
            double score = investmentScore(settlement, spec, accessibleValues, doctrineWeights);
            if (score > chosenScore) {
                chosenScore = score;
                chosen = spec;
            }
        }
        if (chosen == null || chosenScore < INVESTMENT_MINIMUM_SCORE) return null;
        consumeInvestmentMaterials(settlement, chosen);
        addFacilityCapacity(settlement.facilities(), chosen.capacityAttribute(), chosen.baseGain());
        settlement.lastInvestmentDay(day);
        return chosen.capacityAttribute();
    }

    private double produceAtSite(ReferenceSettlement settlement, ReferenceResourceSite site, SiteSpec spec, double planned) {
        return produceWithInputs(settlement, spec.resource(), planned,
                inputs(ReferenceResource.TOOLS, spec.toolsPerOutput(), ReferenceResource.ENERGY, spec.energyPerOutput()), site);
    }

    private double produceWithInputs(ReferenceSettlement settlement, ReferenceResource output, double planned, Map<ReferenceResource, Double> inputs) {
        return produceWithInputs(settlement, output, planned, inputs, null);
    }

    private double produceWithInputs(
            ReferenceSettlement settlement,
            ReferenceResource output,
            double planned,
            Map<ReferenceResource, Double> inputs,
            ReferenceResourceSite site
    ) {
        if (planned <= 0.0d) return 0.0d;
        double factor = 1.0d;
        for (Map.Entry<ReferenceResource, Double> input : inputs.entrySet()) {
            double required = planned * input.getValue();
            if (required > 0.0d) factor = Math.min(factor, settlement.amount(input.getKey()) / required);
        }
        double actual = Math.max(0.0d, planned * factor);
        for (Map.Entry<ReferenceResource, Double> input : inputs.entrySet()) {
            settlement.recordConsumption(input.getKey(), settlement.remove(input.getKey(), actual * input.getValue()));
        }
        if (site == null) settlement.add(output, actual); else site.add(output, actual);
        settlement.recordProduction(output, actual);
        return actual;
    }

    private static double siteInput(ReferenceSettlement settlement, String kind, double perOutput) {
        return settlement.primaryCapacity(kind) * switch (kind) {
            case "farm" -> 400.0d;
            case "mine" -> 34.0d;
            case "forest" -> 38.0d;
            case "power" -> 50.0d;
            default -> throw new IllegalArgumentException("unknown site kind " + kind);
        } * perOutput;
    }

    private static EnumMap<ReferenceResource, Double> inputs(Object... values) {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (int index = 0; index < values.length; index += 2) result.put((ReferenceResource) values[index], (Double) values[index + 1]);
        return result;
    }

    private static double boundedUnit(double value) { return Math.max(0.0d, Math.min(1.0d, value)); }
    private static double nonNegative(double value) { return value > 0.0d ? value : 0.0d; }

    private List<ReferenceInvestmentSpec> investmentSpecs() {
        List<ReferenceInvestmentSpec> scaled = new ArrayList<>(4);
        scaled.add(new ReferenceInvestmentSpec("workshop", ReferenceResource.TOOLS,
                humanAmount(24.0d), humanAmount(35.0d), humanAmount(18.0d), humanAmount(0.25d)));
        scaled.add(new ReferenceInvestmentSpec("armory", ReferenceResource.WEAPONS,
                humanAmount(20.0d), humanAmount(48.0d), humanAmount(26.0d), humanAmount(0.25d)));
        scaled.add(new ReferenceInvestmentSpec("clinic", ReferenceResource.MEDICINE,
                humanAmount(18.0d), humanAmount(12.0d), humanAmount(18.0d), humanAmount(0.25d)));
        scaled.add(new ReferenceInvestmentSpec("fortification", null,
                humanAmount(40.0d), humanAmount(45.0d), humanAmount(24.0d), humanAmount(0.20d)));
        return scaled;
    }

    private double investmentScore(
            ReferenceSettlement settlement,
            ReferenceInvestmentSpec spec,
            Map<ReferenceResource, Double> accessibleValues,
            Map<String, Double> doctrineWeights
    ) {
        double score;
        if (spec.capacityAttribute().equals("fortification")) {
            score = FORTIFICATION_BASE_SCORE + settlement.threat() * FORTIFICATION_THREAT_SCORE
                    + (100.0d - settlement.integrity()) / FORTIFICATION_INTEGRITY_DIVISOR;
            if (doctrineWeights != null && !doctrineWeights.isEmpty()) score *= doctrineWeights.get("defence");
        } else if (spec.output() != null) {
            Double market = accessibleValues.get(spec.output());
            double marketValue = market == null ? localValue(settlement, spec.output()) : market;
            double operationalBonus = switch (spec.capacityAttribute()) {
                case "workshop" -> 1.0d;
                case "armory" -> 1.0d + ARMORY_THREAT_BONUS * settlement.threat();
                case "clinic" -> 1.0d + CLINIC_THREAT_BONUS * settlement.threat();
                default -> 1.0d;
            };
            double diminishing = 1.0d / (1.0d + facilityCapacity(settlement.facilities(), spec.capacityAttribute()) * DIMINISHING_RETURNS);
            score = marketValue * operationalBonus * diminishing;
            if (doctrineWeights != null && !doctrineWeights.isEmpty()) {
                score *= switch (spec.capacityAttribute()) {
                    case "workshop" -> doctrineWeights.get("site");
                    case "armory" -> doctrineWeights.get("defence");
                    case "clinic" -> doctrineWeights.get("cleanse");
                    default -> 1.0d;
                };
            }
            if ((spec.output() == ReferenceResource.WEAPONS || spec.output() == ReferenceResource.MEDICINE)
                    && settlement.threat() > DEFENCE_THREAT_THRESHOLD) score *= DEFENCE_PRIORITY;
        } else {
            throw new IllegalArgumentException("unsupported investment " + spec.capacityAttribute());
        }
        double materialWeight = spec.timber() * TIMBER_MATERIAL_WEIGHT + spec.ore() * ORE_MATERIAL_WEIGHT
                + spec.tools() * TOOLS_MATERIAL_WEIGHT;
        return score / Math.max(1.0d, materialWeight);
    }

    private static boolean canAffordMaterials(ReferenceSettlement settlement, ReferenceInvestmentSpec spec) {
        return settlement.amount(ReferenceResource.TIMBER) >= spec.timber()
                && settlement.amount(ReferenceResource.ORE) >= spec.ore()
                && settlement.amount(ReferenceResource.TOOLS) >= spec.tools();
    }

    private static void consumeInvestmentMaterials(ReferenceSettlement settlement, ReferenceInvestmentSpec spec) {
        settlement.remove(ReferenceResource.TIMBER, spec.timber());
        settlement.remove(ReferenceResource.ORE, spec.ore());
        settlement.remove(ReferenceResource.TOOLS, spec.tools());
    }

    private static boolean canPaySiteMaterials(ReferenceSettlement settlement, Map<ReferenceResource, Double> materials) {
        return materials.entrySet().stream().allMatch(entry -> settlement.amount(entry.getKey()) + 1.0e-9d >= entry.getValue());
    }

    private static void paySiteMaterials(ReferenceSettlement settlement, Map<ReferenceResource, Double> materials) {
        for (Map.Entry<ReferenceResource, Double> entry : materials.entrySet()) settlement.remove(entry.getKey(), entry.getValue());
    }

    private Map<ReferenceResource, Double> materials(Object... entries) {
        return inputs(entries);
    }

    private SiteProjectSpec siteProjectSpec(String action) {
        if (action == null) return null;
        return switch (action) {
            case "upgrade" -> new SiteProjectSpec(materials(ReferenceResource.TIMBER, humanAmount(18.0d), ReferenceResource.ORE,
                    humanAmount(14.0d), ReferenceResource.TOOLS, humanAmount(14.0d)), 0.0d, 0.0d);
            case "repair" -> new SiteProjectSpec(materials(ReferenceResource.TIMBER, humanAmount(8.0d), ReferenceResource.ORE,
                    humanAmount(4.0d), ReferenceResource.TOOLS, humanAmount(8.0d)), 0.0d, 0.0d);
            case "claim" -> new SiteProjectSpec(materials(ReferenceResource.TIMBER, humanAmount(12.0d), ReferenceResource.ORE,
                    humanAmount(8.0d), ReferenceResource.TOOLS, humanAmount(8.0d)), 0.0d, 0.0d);
            case "cleanse" -> new SiteProjectSpec(materials(ReferenceResource.FOOD, humanAmount(8.0d), ReferenceResource.MEDICINE,
                    humanAmount(2.0d), ReferenceResource.TOOLS, humanAmount(4.0d)), 0.28d, 0.0d);
            case "scorch" -> new SiteProjectSpec(materials(ReferenceResource.AMMO, humanAmount(12.0d)), 0.62d, 0.28d);
            case "restore" -> new SiteProjectSpec(materials(ReferenceResource.FOOD, humanAmount(10.0d), ReferenceResource.TIMBER,
                    humanAmount(8.0d), ReferenceResource.TOOLS, humanAmount(6.0d)), 1.0d, 0.0d);
            default -> null;
        };
    }

    private static double facilityCapacity(ReferenceFacilities facilities, String attribute) {
        return switch (attribute) {
            case "workshop" -> facilities.workshop();
            case "armory" -> facilities.armory();
            case "clinic" -> facilities.clinic();
            case "fortification" -> facilities.fortification();
            default -> throw new IllegalArgumentException("unknown facility " + attribute);
        };
    }

    private static void addFacilityCapacity(ReferenceFacilities facilities, String attribute, double amount) {
        switch (attribute) {
            case "workshop" -> facilities.workshop(facilities.workshop() + amount);
            case "armory" -> facilities.armory(facilities.armory() + amount);
            case "clinic" -> facilities.clinic(facilities.clinic() + amount);
            case "fortification" -> facilities.fortification(facilities.fortification() + amount);
            default -> throw new IllegalArgumentException("unknown facility " + attribute);
        }
    }

    private static ReferenceResourceRule sourceResourceRule(ReferenceResource resource) {
        return switch (resource) {
            case FOOD -> new ReferenceResourceRule(1.0d, 14.0d, 0.85d, 80.0d);
            case SEEDS -> new ReferenceResourceRule(2.1d, 30.0d, 0.75d, 18.0d);
            case TIMBER -> new ReferenceResourceRule(1.2d, 30.0d, 0.45d, 80.0d);
            case ORE -> new ReferenceResourceRule(1.6d, 24.0d, 0.55d, 60.0d);
            case ENERGY -> new ReferenceResourceRule(0.55d, 5.0d, 0.65d, 40.0d);
            case TOOLS -> new ReferenceResourceRule(4.5d, 35.0d, 0.7d, 35.0d);
            case MEDICINE -> new ReferenceResourceRule(7.0d, 5.0d, 0.95d, 8.0d);
            case WEAPONS -> new ReferenceResourceRule(12.0d, 60.0d, 0.9d, 20.0d);
            case AMMO -> new ReferenceResourceRule(1.8d, 45.0d, 0.9d, 80.0d);
        };
    }

    private static SiteSpec siteSpec(ReferenceSiteKind kind) {
        return switch (kind) {
            case FARM -> new SiteSpec(ReferenceResource.FOOD, 400.0d, 0.002d, 0.006d);
            case MINE -> new SiteSpec(ReferenceResource.ORE, 34.0d, 0.045d, 0.28d);
            case FOREST -> new SiteSpec(ReferenceResource.TIMBER, 38.0d, 0.025d, 0.1d);
            case POWER -> new SiteSpec(ReferenceResource.ENERGY, 50.0d, 0.0d, 0.0d);
        };
    }

    private record SiteSpec(ReferenceResource resource, double yield, double toolsPerOutput, double energyPerOutput) { }

    private record SiteProjectSpec(Map<ReferenceResource, Double> materials, double effect, double conditionLoss) { }
}
