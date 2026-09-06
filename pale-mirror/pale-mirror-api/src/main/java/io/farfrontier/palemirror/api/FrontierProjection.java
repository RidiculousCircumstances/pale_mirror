package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only, server-authored projection of the autonomous Frontier simulation. */
public record FrontierProjection(String profileId, long seed, long day,
                                 List<Settlement> settlements, List<Resident> residents,
                                 List<Facility> facilities, List<Operation> operations, List<Cargo> cargo,
                                 List<Hive> hives, List<HiveOrgan> hiveOrgans, List<Bioform> bioforms,
                                 List<HarvesterRun> harvesterRuns,
                                 List<Assault> assaults, List<FieldOperation> fieldOperations, List<EcologyCell> ecology,
                                 List<MorphogenesisProject> morphogenesisProjects,
                                 List<PropagationRun> propagationRuns, List<LatentColony> latentColonies,
                                 List<Campaign> campaigns, Map<String, Integer> adaptations) {
    public FrontierProjection {
        required(profileId, "profileId");
        if (day < 0) throw new IllegalArgumentException("day must not be negative");
        settlements = List.copyOf(settlements);
        residents = List.copyOf(residents);
        facilities = List.copyOf(facilities);
        operations = List.copyOf(operations);
        cargo = List.copyOf(cargo);
        hives = List.copyOf(hives);
        hiveOrgans = List.copyOf(hiveOrgans);
        bioforms = List.copyOf(bioforms);
        harvesterRuns = List.copyOf(harvesterRuns);
        assaults = List.copyOf(assaults);
        fieldOperations = List.copyOf(fieldOperations);
        ecology = List.copyOf(ecology);
        morphogenesisProjects = List.copyOf(morphogenesisProjects);
        propagationRuns = List.copyOf(propagationRuns);
        latentColonies = List.copyOf(latentColonies);
        campaigns = List.copyOf(campaigns);
        adaptations = Map.copyOf(adaptations);
    }

    /** Compatibility constructor for callers that predate explicit coalition campaigns. */
    public FrontierProjection(String profileId, long seed, long day,
                              List<Settlement> settlements, List<Resident> residents,
                              List<Facility> facilities, List<Operation> operations, List<Cargo> cargo,
                              List<Hive> hives, List<HiveOrgan> hiveOrgans, List<Bioform> bioforms,
                              List<HarvesterRun> harvesterRuns, List<Assault> assaults, List<FieldOperation> fieldOperations,
                              List<EcologyCell> ecology, List<MorphogenesisProject> morphogenesisProjects,
                              List<PropagationRun> propagationRuns, List<LatentColony> latentColonies,
                              Map<String, Integer> adaptations) {
        this(profileId, seed, day, settlements, residents, facilities, operations, cargo, hives, hiveOrgans, bioforms,
                harvesterRuns, assaults, fieldOperations, ecology, morphogenesisProjects, propagationRuns, latentColonies,
                List.of(), adaptations);
    }

    /** Compatibility constructor for projections written before observable morphogenesis existed. */
    public FrontierProjection(String profileId, long seed, long day,
                              List<Settlement> settlements, List<Resident> residents,
                              List<Facility> facilities, List<Operation> operations, List<Cargo> cargo,
                              List<Hive> hives, List<HiveOrgan> hiveOrgans, List<Bioform> bioforms,
                              List<Assault> assaults, List<FieldOperation> fieldOperations, List<EcologyCell> ecology) {
        this(profileId, seed, day, settlements, residents, facilities, operations, cargo, hives, hiveOrgans, bioforms,
                List.of(), assaults, fieldOperations, ecology, List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    /** Compatibility constructor for projections written before harvester runs became explicit. */
    public FrontierProjection(String profileId, long seed, long day,
                              List<Settlement> settlements, List<Resident> residents,
                              List<Facility> facilities, List<Operation> operations, List<Cargo> cargo,
                              List<Hive> hives, List<HiveOrgan> hiveOrgans, List<Bioform> bioforms,
                              List<Assault> assaults, List<FieldOperation> fieldOperations, List<EcologyCell> ecology,
                              List<MorphogenesisProject> morphogenesisProjects) {
        this(profileId, seed, day, settlements, residents, facilities, operations, cargo, hives, hiveOrgans, bioforms,
                List.of(), assaults, fieldOperations, ecology, morphogenesisProjects, List.of(), List.of(), List.of(), Map.of());
    }

    /** Compatibility constructor for visual providers built before ecology became an explicit projection layer. */
    public FrontierProjection(String profileId, long seed, long day,
                              List<Settlement> settlements, List<Resident> residents,
                              List<Facility> facilities, List<Operation> operations, List<Cargo> cargo,
                              List<Hive> hives, List<HiveOrgan> hiveOrgans, List<Bioform> bioforms,
                              List<Assault> assaults, List<FieldOperation> fieldOperations) {
        this(profileId, seed, day, settlements, residents, facilities, operations, cargo, hives, hiveOrgans, bioforms,
                List.of(), assaults, fieldOperations, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    /** Compatibility constructor for providers that do not yet render dynamic operations. */
    public FrontierProjection(String profileId, long seed, long day,
                              List<Settlement> settlements, List<Resident> residents,
                              List<Facility> facilities, List<Operation> operations, List<Cargo> cargo,
                              List<Hive> hives, List<HiveOrgan> hiveOrgans, List<Bioform> bioforms) {
        this(profileId, seed, day, settlements, residents, facilities, operations, cargo, hives, hiveOrgans, bioforms,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    /** Compatibility constructor retained for callers that provide raids but not human field responses. */
    public FrontierProjection(String profileId, long seed, long day,
                              List<Settlement> settlements, List<Resident> residents,
                              List<Facility> facilities, List<Operation> operations, List<Cargo> cargo,
                              List<Hive> hives, List<HiveOrgan> hiveOrgans, List<Bioform> bioforms,
                              List<Assault> assaults) {
        this(profileId, seed, day, settlements, residents, facilities, operations, cargo, hives, hiveOrgans, bioforms,
                List.of(), assaults, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    public record Settlement(String id, String name, String focus, int cellX, int cellZ, int alivePopulation,
                             Map<String, Long> stocks, long netCredit, String civicState, int threatPermille,
                             int foodReserveDaysMilli, int rationPermille, long civicRevision) {
        public Settlement {
            required(id, "id"); required(name, "name"); required(focus, "focus"); required(civicState, "civicState");
            if (alivePopulation < 0 || threatPermille < 0 || threatPermille > 1_000 || foodReserveDaysMilli < 0
                    || rationPermille < 1 || rationPermille > 1_000 || civicRevision < 0 || stocks == null
                    || stocks.values().stream().anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException("invalid settlement projection");
            }
            stocks = Map.copyOf(stocks);
        }
        /** Compatibility constructor for projections produced before civic policy became observable. */
        public Settlement(String id, String name, String focus, int cellX, int cellZ, int alivePopulation,
                          Map<String, Long> stocks, long netCredit) {
            this(id, name, focus, cellX, cellZ, alivePopulation, stocks, netCredit, "NORMAL", 0, 0, 1_000, 0);
        }
    }
    public record Resident(String id, String settlementId, String role, boolean alive,
                           String materializationId, long revision) {
        public Resident { required(id, "id"); required(settlementId, "settlementId"); required(role, "role"); required(materializationId, "materializationId"); if (revision < 0) throw new IllegalArgumentException("revision must not be negative"); }
    }
    public record Facility(String id, String settlementId, String kind, int cellX, int cellZ,
                           String state, long revision) {
        public Facility { required(id, "id"); required(settlementId, "settlementId"); required(kind, "kind"); required(state, "state"); if (revision < 0) throw new IllegalArgumentException("revision must not be negative"); }
    }
    public record Operation(String id, String settlementId, String facilityId, String kind, String state,
                            String materializationId, long revision) {
        public Operation {
            required(id, "id"); required(settlementId, "settlementId"); required(facilityId, "facilityId");
            required(kind, "kind"); required(state, "state"); required(materializationId, "materializationId");
            if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        }
    }
    public record Cargo(String id, String routeId, String sourceSettlementId, String destinationSettlementId,
                        String resource, long amount, long creditValue, long dispatchedDay, String materializationId, long revision) {
        public Cargo {
            required(id, "id"); required(routeId, "routeId"); required(sourceSettlementId, "sourceSettlementId");
            required(destinationSettlementId, "destinationSettlementId"); required(resource, "resource");
            required(materializationId, "materializationId");
            if (amount < 1 || creditValue < 1 || dispatchedDay < 1 || revision < 0) throw new IllegalArgumentException("invalid cargo projection");
        }
        public Cargo(String id, String routeId, String sourceSettlementId, String destinationSettlementId,
                     String resource, long amount, long dispatchedDay, String materializationId, long revision) {
            this(id, routeId, sourceSettlementId, destinationSettlementId, resource, amount, amount, dispatchedDay, materializationId, revision);
        }
    }
    public record Hive(String id, int cellX, int cellZ, long biomass, long geneticMaterial, int broodSignal, String state, String materializationId, long revision) {
        public Hive {
            required(id, "id"); required(state, "state"); required(materializationId, "materializationId");
            if (biomass < 0 || geneticMaterial < 0 || broodSignal < 0 || broodSignal > 1_000 || revision < 0) throw new IllegalArgumentException("invalid hive projection");
        }
        /** Compatibility constructor for a provider which cannot yet show the command signal. */
        public Hive(String id, int cellX, int cellZ, long biomass, String state, String materializationId, long revision) {
            this(id, cellX, cellZ, biomass, 0, 0, state, materializationId, revision);
        }
        /** Compatibility constructor for providers which predate carried genetic material. */
        public Hive(String id, int cellX, int cellZ, long biomass, int broodSignal, String state, String materializationId, long revision) {
            this(id, cellX, cellZ, biomass, 0, broodSignal, state, materializationId, revision);
        }
    }
    public record HiveOrgan(String id, String hiveId, String kind, int cellX, int cellZ, String state,
                            String materializationId, long revision) {
        public HiveOrgan {
            required(id, "id"); required(hiveId, "hiveId"); required(kind, "kind"); required(state, "state");
            required(materializationId, "materializationId");
            if (revision < 0) throw new IllegalArgumentException("invalid hive organ projection");
        }
    }
    /** Read-only growing-organ projection; a label, not an unowned temporary cube, materializes it. */
    public record MorphogenesisProject(String id, String hiveId, String sourceOrganId, String kind, int cellX, int cellZ,
                                       String state, long startedDay, int remainingDays, int requiredDays,
                                       String materializationId, long revision) {
        public MorphogenesisProject {
            required(id, "id"); required(hiveId, "hiveId"); required(sourceOrganId, "sourceOrganId"); required(kind, "kind");
            required(state, "state"); required(materializationId, "materializationId");
            if (startedDay < 0 || remainingDays < 0 || requiredDays < 1 || remainingDays > requiredDays || revision < 0) {
                throw new IllegalArgumentException("invalid morphogenesis projection");
            }
        }
    }
    public record Bioform(String id, String hiveId, String kind, long bornDay, int birthOrdinal, boolean alive,
                          String materializationId, long revision) {
        public Bioform {
            required(id, "id"); required(hiveId, "hiveId"); required(kind, "kind"); required(materializationId, "materializationId");
            if (bornDay < 0 || birthOrdinal < 1 || revision < 0) throw new IllegalArgumentException("invalid bioform projection");
        }
    }
    /** One type-coloured Zombie's autonomous forage/return operation. */
    public record HarvesterRun(String id, String hiveId, String sourceOrganId, String bioformId,
                               int forageCellX, int forageCellZ, int cellX, int cellZ, String receiverOrganId,
                               String state, long startedDay, int outboundDays, int transitProgress, int returnDays,
                               long cargo, long geneticCargo, long finishedDay, String materializationId, long revision) {
        public HarvesterRun {
            required(id, "id"); required(hiveId, "hiveId"); required(sourceOrganId, "sourceOrganId"); required(bioformId, "bioformId");
            required(state, "state"); required(materializationId, "materializationId");
            if (receiverOrganId != null && receiverOrganId.isBlank()) throw new IllegalArgumentException("invalid harvester receiver");
            if (startedDay < 0 || outboundDays < 1 || transitProgress < 0 || returnDays < 0 || cargo < 0 || geneticCargo < 0
                    || finishedDay < -1 || revision < 0) throw new IllegalArgumentException("invalid harvester projection");
        }
    }
    /** One non-combat coloured Zombie en route to leave a distant deposited foothold. */
    public record PropagationRun(String id, String hiveId, String sourceOrganId, String bioformId,
                           int targetCellX, int targetCellZ, int cellX, int cellZ, String state,
                           long startedDay, int transitDays, int transitProgress, String latentColonyId,
                           long finishedDay, String materializationId, long revision) {
        public PropagationRun {
            required(id, "id"); required(hiveId, "hiveId"); required(sourceOrganId, "sourceOrganId"); required(bioformId, "bioformId");
            required(state, "state"); required(materializationId, "materializationId");
            if (latentColonyId != null && latentColonyId.isBlank()) throw new IllegalArgumentException("invalid propagation colony");
            if (startedDay < 0 || transitDays < 1 || transitProgress < 0 || transitProgress > transitDays || finishedDay < -1 || revision < 0) {
                throw new IllegalArgumentException("invalid propagation projection");
            }
        }
    }
    /** Physical graybox marker for a deposited colony. */
    public record LatentColony(String id, String hiveId, String sourceOrganId, int cellX, int cellZ,
                               long depositedDay, long propagules, long strength, String materializationId, long revision) {
        public LatentColony {
            required(id, "id"); required(hiveId, "hiveId"); required(sourceOrganId, "sourceOrganId"); required(materializationId, "materializationId");
            if (depositedDay < 1 || propagules < 1 || strength < 1 || strength > 1_000 || revision < 0) {
                throw new IllegalArgumentException("invalid latent colony projection");
            }
        }
    }
    /** A visible biological field group. Participants retain their separate Bioform identity. */
    public record Assault(String id, String hiveId, String targetSettlementId, String kind, String state,
                          int cellX, int cellZ, List<String> participantIds, long startedDay,
                          int transitDays, int engagementDays, long finishedDay,
                          String materializationId, long revision) {
        public Assault {
            required(id, "id"); required(hiveId, "hiveId"); required(targetSettlementId, "targetSettlementId");
            required(kind, "kind"); required(state, "state"); required(materializationId, "materializationId");
            if (participantIds == null || participantIds.isEmpty() || participantIds.size() > 12
                    || new java.util.LinkedHashSet<>(participantIds).size() != participantIds.size()
                    || startedDay < 1 || transitDays < 1 || engagementDays < 0 || finishedDay < -1 || revision < 0) {
                throw new IllegalArgumentException("invalid assault projection");
            }
            participantIds = List.copyOf(participantIds);
        }
    }
    /** A settlement-owned response whose members retain their individual Resident identities. */
    public record FieldOperation(String id, String settlementId, String targetAssaultId, String kind, String state,
                                 int cellX, int cellZ, List<String> participantIds, int livingParticipants,
                                 long startedDay, int transitDays, long finishedDay,
                                 String materializationId, long revision) {
        public FieldOperation {
            required(id, "id"); required(settlementId, "settlementId"); required(targetAssaultId, "targetAssaultId");
            required(kind, "kind"); required(state, "state"); required(materializationId, "materializationId");
            if (participantIds == null || participantIds.isEmpty() || participantIds.size() > 6
                    || new java.util.LinkedHashSet<>(participantIds).size() != participantIds.size()
                    || livingParticipants < 0 || livingParticipants > participantIds.size() || startedDay < 1
                    || transitDays < 1 || finishedDay < -1 || revision < 0) {
                throw new IllegalArgumentException("invalid field operation projection");
            }
            participantIds = List.copyOf(participantIds);
        }
    }
    /** A supplied multi-settlement campaign; each participant remains an ordinary projected Villager. */
    public record Campaign(String id, String leaderSettlementId, String targetHiveId, String targetOrganId,
                           String kind, String phase, int cellX, int cellZ, List<String> contributorSettlementIds,
                           List<String> participantIds, int livingParticipants, long startedDay, int transitDays,
                           int phaseDays, int supplyRiskPermille, int supplyReadinessPermille, long finishedDay,
                           String materializationId, long revision) {
        public Campaign {
            required(id, "id"); required(leaderSettlementId, "leaderSettlementId"); required(targetHiveId, "targetHiveId");
            required(targetOrganId, "targetOrganId"); required(kind, "kind"); required(phase, "phase");
            required(materializationId, "materializationId");
            if (contributorSettlementIds == null || contributorSettlementIds.size() < 2 || contributorSettlementIds.size() > 3
                    || new java.util.LinkedHashSet<>(contributorSettlementIds).size() != contributorSettlementIds.size()
                    || participantIds == null || participantIds.size() < 2 || participantIds.size() > 12
                    || new java.util.LinkedHashSet<>(participantIds).size() != participantIds.size() || livingParticipants < 0
                    || livingParticipants > participantIds.size() || startedDay < 1 || transitDays < 1 || phaseDays < 0
                    || supplyRiskPermille < 0 || supplyRiskPermille > 1_000 || supplyReadinessPermille < 0
                    || supplyReadinessPermille > 1_000 || finishedDay < -1 || revision < 0) {
                throw new IllegalArgumentException("invalid campaign projection");
            }
            contributorSettlementIds = List.copyOf(contributorSettlementIds);
            participantIds = List.copyOf(participantIds);
        }
    }
    /** Read-only ecological cell.  Its coordinates are simulation cells, never Minecraft block positions. */
    public record EcologyCell(int cellX, int cellZ, long flora, long fauna, long detritus, long nutrients,
                               long moisture, long scar) {
        public EcologyCell {
            if (cellX < 0 || cellZ < 0 || flora < 0 || fauna < 0 || detritus < 0 || nutrients < 0 || moisture < 0
                    || scar < 0 || scar > 1_000) throw new IllegalArgumentException("invalid ecology projection");
        }
        public long organicMass() { return flora + fauna + detritus; }
    }
    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
