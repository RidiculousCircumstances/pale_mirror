package io.farfrontier.palemirror.frontier;

import java.util.List;
import java.util.Map;

/** Immutable projection source. It deliberately contains no Minecraft types or mutable domain records. */
public record FrontierSnapshot(String profileId, long seed, long day,
                               List<SettlementView> settlements,
                               List<ResidentView> residents,
                               List<FacilityView> facilities,
                               List<OperationView> operations,
                               List<CargoView> cargo,
                               List<HiveView> hives,
                               List<HiveOrganView> hiveOrgans,
                               List<BioformView> bioforms,
                               List<HarvesterRunView> harvesterRuns,
                               List<AssaultView> assaults,
                               List<FieldOperationView> fieldOperations,
                               List<EcologyCellView> ecology,
                               List<MorphogenesisProjectView> morphogenesisProjects,
                               List<PropagationRunView> propagationRuns, List<LatentColonyView> latentColonies,
                               List<CampaignView> campaigns,
                               Map<FrontierAdaptation, Integer> adaptations) {
    public FrontierSnapshot {
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

    public record SettlementView(String id, String name, FrontierSettlementFocus focus, FrontierPoint center,
                                 int alivePopulation, Map<FrontierResource, Long> stocks, long netCredit,
                                 FrontierCivicState civicState, int threatPermille, int foodReserveDaysMilli,
                                 int rationPermille, long civicRevision) {
        public SettlementView { stocks = Map.copyOf(stocks); }
        public SettlementView(String id, String name, FrontierSettlementFocus focus, FrontierPoint center,
                              int alivePopulation, Map<FrontierResource, Long> stocks, long netCredit) {
            this(id, name, focus, center, alivePopulation, stocks, netCredit, FrontierCivicState.NORMAL,
                    0, 0, 1_000, 0);
        }
    }
    public record ResidentView(String id, String settlementId, FrontierResidentRole role,
                               boolean alive, String materializationId, long revision) { }
    public record FacilityView(String id, String settlementId, FrontierFacilityKind kind,
                               FrontierPoint position, FrontierFacility.State state, long revision) { }
    public record OperationView(String id, String settlementId, String facilityId, FrontierOperationKind kind,
                                FrontierOperation.State state, String materializationId, long revision) { }
    public record CargoView(String id, String routeId, String sourceSettlementId, String destinationSettlementId,
                            FrontierResource resource, long amount, long creditValue, long dispatchedDay,
                            String materializationId, long revision) { }
    public record HiveView(String id, FrontierPoint anchor, long biomass, long geneticMaterial, int broodSignal, FrontierHive.State state,
                           String materializationId, long revision) { }
    public record HiveOrganView(String id, String hiveId, FrontierHiveOrganKind kind, FrontierPoint position,
                                FrontierHiveOrgan.State state, String materializationId, long revision) { }
    /** A visible in-progress growth, retained only until the final organ or abort event. */
    public record MorphogenesisProjectView(String id, String hiveId, String sourceOrganId, FrontierHiveOrganKind kind,
                                           FrontierPoint position, long startedDay, int remainingDays, int requiredDays,
                                           String materializationId, long revision) { }
    public record BioformView(String id, String hiveId, FrontierBioformKind kind, long bornDay, int birthOrdinal,
                              boolean alive, String materializationId, long revision) { }
    /** One physical harvesting zombie and its visible biological cargo lifecycle. */
    public record HarvesterRunView(String id, String hiveId, String sourceOrganId, String bioformId,
                                   FrontierPoint foragePosition, FrontierPoint position, String receiverOrganId,
                                   FrontierHarvesterRun.State state, long startedDay, int outboundDays, int transitProgress,
                                   int returnDays, long cargo, long geneticCargo, long finishedDay,
                                   String materializationId, long revision) { }
    /** A physical carrier that deposits, rather than magically creates, a distant foothold. */
    public record PropagationRunView(String id, String hiveId, String sourceOrganId, String bioformId,
                               FrontierPoint target, FrontierPoint position, FrontierPropagationRun.State state,
                               long startedDay, int transitDays, int transitProgress, String latentColonyId,
                               long finishedDay, String materializationId, long revision) { }
    /** A destructible deposited colony, retained only until clearing, decay, or Core maturation. */
    public record LatentColonyView(String id, String hiveId, String sourceOrganId, FrontierPoint position,
                                   long depositedDay, long propagules, long strength, String materializationId, long revision) { }
    public record AssaultView(String id, String hiveId, String targetSettlementId, FrontierAssault.Kind kind,
                              FrontierAssault.State state, FrontierPoint position, List<String> participantIds,
                              long startedDay, int transitDays, int engagementDays, long finishedDay,
                              String materializationId, long revision) {
        public AssaultView { participantIds = List.copyOf(participantIds); }
    }
    public record FieldOperationView(String id, String settlementId, String targetAssaultId,
                                     FrontierFieldOperation.Kind kind, FrontierFieldOperation.State state,
                                     FrontierPoint position, List<String> participantIds, int livingParticipants,
                                     long startedDay, int transitDays, long finishedDay,
                                     String materializationId, long revision) {
        public FieldOperationView { participantIds = List.copyOf(participantIds); }
    }
    /** A supplied coalition whose individual participants retain normal resident identity. */
    public record CampaignView(String id, String leaderSettlementId, String targetHiveId, String targetOrganId,
                               FrontierCampaign.Kind kind, FrontierCampaign.Phase phase, FrontierPoint position,
                               List<String> contributorSettlementIds, List<String> participantIds, int livingParticipants,
                               long startedDay, int transitDays, int phaseDays, int supplyRiskPermille,
                               int supplyReadinessPermille, long finishedDay, String materializationId, long revision) {
        public CampaignView { contributorSettlementIds = List.copyOf(contributorSettlementIds); participantIds = List.copyOf(participantIds); }
    }
    /** One finite simulation cell; the visual layer uses this only as a read-only ground marker. */
    public record EcologyCellView(int x, int z, long flora, long fauna, long detritus, long nutrients,
                                  long moisture, long scar) {
        public EcologyCellView {
            if (x < 0 || z < 0 || flora < 0 || fauna < 0 || detritus < 0 || nutrients < 0 || moisture < 0
                    || scar < 0 || scar > 1_000) throw new IllegalArgumentException("invalid ecology view");
        }
        public long organicMass() { return flora + fauna + detritus; }
    }
}
