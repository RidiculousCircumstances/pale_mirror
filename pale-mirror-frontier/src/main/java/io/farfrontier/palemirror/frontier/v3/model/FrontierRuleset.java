package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable balance and cadence contract for one Frontier world lineage.
 *
 * <p>This is deliberately data rather than a process-local bag of constants: its selector is
 * persisted with every recovery image and its content digest is part of the bootstrap manifest.
 * Limits which merely protect memory or bounded algorithms do not belong here.</p>
 */
public record FrontierRuleset(String id, int schemaVersion, Cadence cadence, Spatial spatial, Rates rates,
                              FacilityCapacity facilityCapacity, Combat combat) {
    public FrontierRuleset {
        if (id == null || id.isBlank() || !id.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("ruleset id must be a stable lowercase identifier");
        }
        if (schemaVersion <= 0) throw new IllegalArgumentException("ruleset schema version must be positive");
        cadence = Objects.requireNonNull(cadence, "cadence");
        spatial = Objects.requireNonNull(spatial, "spatial");
        rates = Objects.requireNonNull(rates, "rates");
        facilityCapacity = Objects.requireNonNull(facilityCapacity, "facility capacity");
        combat = Objects.requireNonNull(combat, "combat");
    }

    /** SHA-256 of the complete stable selector and canonical field values. */
    public String contentSha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalText().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private String canonicalText() {
        return id + '|' + schemaVersion + '|' + cadence.canonicalText() + '|' + spatial.canonicalText() + '|' + rates.canonicalText()
                + '|' + facilityCapacity.canonicalText() + '|' + combat.canonicalText();
    }

    /** All elapsed-time choices used by the canonical process layer. */
    public record Cadence(long resourceInitialPreparationTick, long resourceGrowthStageInterval, long resourceHarvestRetryInterval,
                          long companyFoundationReviewInterval, long strategicReviewInterval, long populationBirthReviewInterval,
                          long populationBirthCompletionDelay, long provisionInitialReviewTick, long provisionReviewInterval,
                          long migrationReviewInterval, long migrationStepInterval, long humanHealthProgressionDelay,
                          long marketRetryInterval, long marketDemandLifetime, long hiveInfectionPulseInterval,
                          long hiveScoutPatrolInterval, long hiveNutrientTransferStepInterval, long hivePerceptionRefreshInterval,
                          long hiveRouteEngagementStepInterval, long hiveRouteEngagementCombatInterval,
                          long hiveSettlementAssaultStepInterval, long hiveSettlementAssaultCombatInterval,
                          long routePatrolStepInterval, long terminalLogisticsReviewInterval,
                          long decontaminationScanInterval, long structuralRepairScanInterval, long routeConstructionScanInterval,
                          long structuralRepairInitialScanTick, long routeConstructionInitialScanTick, long decontaminationInitialScanTick,
                          long settlementStrategicInitialReviewTick, long populationBirthInitialReviewTick, long companyFoundationInitialReviewTick,
                          long populationMigrationInitialReviewTick, long terminalLogisticsInitialReviewTick, long hiveScoutInitialPatrolTick,
                          long hiveScoutInitialStagger, long hiveStrategicInitialReviewTick, long settlementInitialStagger,
                          long hiveTerritoryKnowledgeMaxAge, long hiveSettlementKnowledgeMaxAge) {
        public Cadence {
            requirePositive(resourceInitialPreparationTick, "resource initial preparation tick");
            requirePositive(resourceGrowthStageInterval, "resource growth stage interval");
            requirePositive(resourceHarvestRetryInterval, "resource harvest retry interval");
            requirePositive(companyFoundationReviewInterval, "company foundation review interval");
            requirePositive(strategicReviewInterval, "strategic review interval");
            requirePositive(populationBirthReviewInterval, "population birth review interval");
            requirePositive(populationBirthCompletionDelay, "population birth completion delay");
            requirePositive(provisionInitialReviewTick, "provision initial review tick");
            requirePositive(provisionReviewInterval, "provision review interval");
            requirePositive(migrationReviewInterval, "migration review interval");
            requirePositive(migrationStepInterval, "migration step interval");
            requirePositive(humanHealthProgressionDelay, "human health progression delay");
            requirePositive(marketRetryInterval, "market retry interval");
            requirePositive(marketDemandLifetime, "market demand lifetime");
            requirePositive(hiveInfectionPulseInterval, "hive infection pulse interval");
            requirePositive(hiveScoutPatrolInterval, "hive scout patrol interval");
            requirePositive(hiveNutrientTransferStepInterval, "hive nutrient transfer step interval");
            requirePositive(hivePerceptionRefreshInterval, "hive perception refresh interval");
            requirePositive(hiveRouteEngagementStepInterval, "hive route engagement step interval");
            requirePositive(hiveRouteEngagementCombatInterval, "hive route engagement combat interval");
            requirePositive(hiveSettlementAssaultStepInterval, "hive settlement assault step interval");
            requirePositive(hiveSettlementAssaultCombatInterval, "hive settlement assault combat interval");
            requirePositive(routePatrolStepInterval, "route patrol step interval");
            requirePositive(terminalLogisticsReviewInterval, "terminal logistics review interval");
            requirePositive(decontaminationScanInterval, "decontamination scan interval");
            requirePositive(structuralRepairScanInterval, "structural repair scan interval");
            requirePositive(routeConstructionScanInterval, "route construction scan interval");
            requirePositive(structuralRepairInitialScanTick, "structural repair initial scan tick");
            requirePositive(routeConstructionInitialScanTick, "route construction initial scan tick");
            requirePositive(decontaminationInitialScanTick, "decontamination initial scan tick");
            requirePositive(settlementStrategicInitialReviewTick, "settlement strategic initial review tick");
            requirePositive(populationBirthInitialReviewTick, "population birth initial review tick");
            requirePositive(companyFoundationInitialReviewTick, "company foundation initial review tick");
            requirePositive(populationMigrationInitialReviewTick, "population migration initial review tick");
            requirePositive(terminalLogisticsInitialReviewTick, "terminal logistics initial review tick");
            requirePositive(hiveScoutInitialPatrolTick, "hive scout initial patrol tick");
            requirePositive(hiveScoutInitialStagger, "hive scout initial stagger");
            requirePositive(hiveStrategicInitialReviewTick, "hive strategic initial review tick");
            requirePositive(settlementInitialStagger, "settlement initial stagger");
            requirePositive(hiveTerritoryKnowledgeMaxAge, "hive territory knowledge max age");
            requirePositive(hiveSettlementKnowledgeMaxAge, "hive settlement knowledge max age");
        }
        private String canonicalText() {
            return resourceInitialPreparationTick + "," + resourceGrowthStageInterval + "," + resourceHarvestRetryInterval + "," + companyFoundationReviewInterval
                    + "," + strategicReviewInterval + "," + populationBirthReviewInterval + "," + populationBirthCompletionDelay + "," + provisionInitialReviewTick
                    + "," + provisionReviewInterval + "," + migrationReviewInterval + "," + migrationStepInterval + "," + humanHealthProgressionDelay
                    + "," + marketRetryInterval + "," + marketDemandLifetime + "," + hiveInfectionPulseInterval + "," + hiveScoutPatrolInterval
                    + "," + hiveNutrientTransferStepInterval + "," + hivePerceptionRefreshInterval + "," + hiveRouteEngagementStepInterval + "," + hiveRouteEngagementCombatInterval
                    + "," + hiveSettlementAssaultStepInterval + "," + hiveSettlementAssaultCombatInterval + "," + routePatrolStepInterval + "," + terminalLogisticsReviewInterval
                    + "," + decontaminationScanInterval + "," + structuralRepairScanInterval + "," + routeConstructionScanInterval
                    + "," + structuralRepairInitialScanTick + "," + routeConstructionInitialScanTick + "," + decontaminationInitialScanTick
                    + "," + settlementStrategicInitialReviewTick + "," + populationBirthInitialReviewTick + "," + companyFoundationInitialReviewTick
                    + "," + populationMigrationInitialReviewTick + "," + terminalLogisticsInitialReviewTick + "," + hiveScoutInitialPatrolTick
                    + "," + hiveScoutInitialStagger + "," + hiveStrategicInitialReviewTick + "," + settlementInitialStagger
                    + "," + hiveTerritoryKnowledgeMaxAge + "," + hiveSettlementKnowledgeMaxAge;
        }
    }

    /** World-scale distances and COLD movement strides used by the canonical process layer. */
    public record Spatial(int decontaminationResponseRadius, int settlementInfectionRadius, int hivePerceptionRadius,
                          int hiveTerritoryHeartRadius, int hiveTerritoryScoutRadius, int hiveSettlementSightRadius,
                          int hiveScoutPatrolRadius, int hiveScoutPatrolStep, int hiveRouteEngagementColdStepBlocks,
                          int hiveSettlementAssaultTerritoryRadius, int hiveSettlementAssaultColdStepBlocks) {
        public Spatial {
            requirePositive(decontaminationResponseRadius, "decontamination response radius");
            requirePositive(settlementInfectionRadius, "settlement infection radius");
            requirePositive(hivePerceptionRadius, "hive perception radius");
            requirePositive(hiveTerritoryHeartRadius, "hive territory heart radius");
            requirePositive(hiveTerritoryScoutRadius, "hive territory scout radius");
            requirePositive(hiveSettlementSightRadius, "hive settlement sight radius");
            requirePositive(hiveScoutPatrolRadius, "hive scout patrol radius");
            requirePositive(hiveScoutPatrolStep, "hive scout patrol step");
            requirePositive(hiveRouteEngagementColdStepBlocks, "hive route engagement COLD step");
            requirePositive(hiveSettlementAssaultTerritoryRadius, "hive settlement assault territory radius");
            requirePositive(hiveSettlementAssaultColdStepBlocks, "hive settlement assault COLD step");
        }
        private String canonicalText() {
            return decontaminationResponseRadius + "," + settlementInfectionRadius + "," + hivePerceptionRadius + "," + hiveTerritoryHeartRadius
                    + "," + hiveTerritoryScoutRadius + "," + hiveSettlementSightRadius + "," + hiveScoutPatrolRadius + "," + hiveScoutPatrolStep
                    + "," + hiveRouteEngagementColdStepBlocks + "," + hiveSettlementAssaultTerritoryRadius + "," + hiveSettlementAssaultColdStepBlocks;
        }
    }

    /** Fixed-point gains belong to the world contract for the same reason as cadence does. */
    public record Rates(FixedScalar hiveInfectionPulseGain, FixedScalar decontaminationReduction,
                        FixedScalar initialSettlementTreasury, FixedScalar worksJobPrice) {
        public Rates {
            hiveInfectionPulseGain = Objects.requireNonNull(hiveInfectionPulseGain, "hive infection pulse gain");
            decontaminationReduction = Objects.requireNonNull(decontaminationReduction, "decontamination reduction");
            initialSettlementTreasury = Objects.requireNonNull(initialSettlementTreasury, "initial settlement treasury");
            worksJobPrice = Objects.requireNonNull(worksJobPrice, "works job price");
            if (hiveInfectionPulseGain.compareTo(FixedScalar.ZERO) <= 0 || hiveInfectionPulseGain.compareTo(FixedScalar.ONE) > 0) {
                throw new IllegalArgumentException("hive infection pulse gain must be in (0, 1]");
            }
            if (decontaminationReduction.compareTo(FixedScalar.ZERO) <= 0 || decontaminationReduction.compareTo(FixedScalar.ONE) > 0) {
                throw new IllegalArgumentException("decontamination reduction must be in (0, 1]");
            }
            if (initialSettlementTreasury.compareTo(FixedScalar.ZERO) < 0 || worksJobPrice.compareTo(FixedScalar.ZERO) <= 0) {
                throw new IllegalArgumentException("economic rates must be non-negative treasury and positive price");
            }
        }
        private String canonicalText() {
            return hiveInfectionPulseGain.raw() + "," + decontaminationReduction.raw() + "," + initialSettlementTreasury.raw() + "," + worksJobPrice.raw();
        }
    }

    /** Structure output is world balance, while the capability projection remains pure code. */
    public record FacilityCapacity(int intactHousingBeds, int damagedHousingBeds, int intactHallWorkCapacity,
                                   int intactFarmWorkCapacity, int intactWorkshopWorkCapacity, int intactDepotWorkCapacity,
                                   int intactInfirmaryWorkCapacity, int damagedWorkCapacity) {
        public FacilityCapacity {
            requirePositive(intactHousingBeds, "intact housing beds");
            requirePositive(damagedHousingBeds, "damaged housing beds");
            requirePositive(intactHallWorkCapacity, "intact hall work capacity");
            requirePositive(intactFarmWorkCapacity, "intact farm work capacity");
            requirePositive(intactWorkshopWorkCapacity, "intact workshop work capacity");
            requirePositive(intactDepotWorkCapacity, "intact depot work capacity");
            requirePositive(intactInfirmaryWorkCapacity, "intact infirmary work capacity");
            requirePositive(damagedWorkCapacity, "damaged work capacity");
        }
        private String canonicalText() {
            return intactHousingBeds + "," + damagedHousingBeds + "," + intactHallWorkCapacity + "," + intactFarmWorkCapacity
                    + "," + intactWorkshopWorkCapacity + "," + intactDepotWorkCapacity + "," + intactInfirmaryWorkCapacity + "," + damagedWorkCapacity;
        }
    }

    /** Per-role COLD combat output; physical combat remains Minecraft-authoritative while HOT. */
    public record Combat(FixedScalar hiveGuardDamage, FixedScalar hiveWorkerScoutDamage, FixedScalar hiveBomberDamage,
                         FixedScalar residentGuardDamage, FixedScalar residentWorkerDamage) {
        public Combat {
            hiveGuardDamage = positive(hiveGuardDamage, "hive guard damage");
            hiveWorkerScoutDamage = positive(hiveWorkerScoutDamage, "hive worker/scout damage");
            hiveBomberDamage = positive(hiveBomberDamage, "hive bomber damage");
            residentGuardDamage = positive(residentGuardDamage, "resident guard damage");
            residentWorkerDamage = positive(residentWorkerDamage, "resident worker damage");
        }
        private String canonicalText() {
            return hiveGuardDamage.raw() + "," + hiveWorkerScoutDamage.raw() + "," + hiveBomberDamage.raw() + ","
                    + residentGuardDamage.raw() + "," + residentWorkerDamage.raw();
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0L) throw new IllegalArgumentException(name + " must be positive");
    }

    private static FixedScalar positive(FixedScalar value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.compareTo(FixedScalar.ZERO) <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
