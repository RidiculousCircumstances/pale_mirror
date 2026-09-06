package io.farfrontier.palemirror.frontier;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fail-closed reconstruction of a persisted Frontier aggregate. */
public final class FrontierStateHydration {
    public record ResidentState(String id, boolean alive, long revision) { }
    public record FacilityState(String id, FrontierFacility.State state, long revision) { }
    public record OperationState(String id, FrontierOperation.State state, long revision) { }
    public record CargoState(String id, String routeId, String sourceSettlementId, String destinationSettlementId,
                             FrontierResource resource, long amount, long creditValue, long dispatchedDay, long revision) {
        public CargoState(String id, String routeId, String sourceSettlementId, String destinationSettlementId,
                          FrontierResource resource, long amount, long dispatchedDay, long revision) {
            this(id, routeId, sourceSettlementId, destinationSettlementId, resource, amount, amount, dispatchedDay, revision);
        }
    }
    public record HiveState(String id, long biomass, long geneticMaterial, FrontierHive.State state, long revision) {
        public HiveState(String id, long biomass, FrontierHive.State state, long revision) {
            this(id, biomass, 0, state, revision);
        }
    }
    /** Dynamic organs carry structural identity; the short form remains for old in-memory fixtures only. */
    public record HiveOrganState(String id, String hiveId, FrontierHiveOrganKind kind, FrontierPoint position,
                                 long bornDay, FrontierHiveOrgan.State state, long revision) {
        public HiveOrganState(String id, FrontierHiveOrgan.State state, long revision) {
            this(id, null, null, null, -1, state, revision);
        }
    }
    public record MorphogenesisProjectState(String id, String hiveId, String sourceOrganId, FrontierHiveOrganKind kind,
                                            FrontierPoint position, long startedDay, int remainingDays, long revision) { }
    public record HarvesterRunState(String id, String hiveId, String sourceOrganId, String bioformId,
                                    FrontierPoint origin, FrontierPoint foragePosition, FrontierPoint position, String receiverOrganId,
                                    FrontierHarvesterRun.State state, long startedDay, int outboundDays,
                                    int transitProgress, int returnDays, long cargo, long geneticCargo,
                                    long finishedDay, long revision) { }
    public record PropagationRunState(String id, String hiveId, String sourceOrganId, String bioformId,
                                FrontierPoint origin, FrontierPoint target, FrontierPoint position, FrontierPropagationRun.State state,
                                long startedDay, int transitDays, int transitProgress, String latentColonyId,
                                long finishedDay, long revision) { }
    public record LatentColonyState(String id, String hiveId, String sourceOrganId, FrontierPoint position,
                                    long depositedDay, long propagules, long strength, boolean cleared, long revision) { }
    public record AdaptationState(Map<FrontierAdaptation, Integer> levels, Map<FrontierDamageKind, Long> damageMemory) {
        public AdaptationState { levels = Map.copyOf(levels); damageMemory = Map.copyOf(damageMemory); }
    }
    public record BioformState(String id, String hiveId, FrontierBioformKind kind, long bornDay, int birthOrdinal,
                               boolean alive, long deathDay, long revision) { }
    public record AssaultState(String id, String hiveId, String targetSettlementId, FrontierAssault.Kind kind,
                               FrontierAssault.State state, FrontierPoint position, Collection<String> participantIds,
                               long startedDay, int transitDays, int transitProgress, int engagementDays,
                               long finishedDay, long revision) {
        public AssaultState { participantIds = List.copyOf(participantIds); }
    }
    public record FieldOperationState(String id, String settlementId, String targetAssaultId,
                                      FrontierFieldOperation.Kind kind, FrontierFieldOperation.State state,
                                      FrontierPoint position, Collection<String> participantIds, long startedDay,
                                      int transitDays, int transitProgress, int stationDays, long finishedDay,
                                      long revision) {
        public FieldOperationState { participantIds = List.copyOf(participantIds); }
    }
    public record CampaignState(String id, String leaderSettlementId, String targetHiveId, String targetOrganId,
                                FrontierCampaign.Kind kind, FrontierCampaign.Phase phase, FrontierPoint position,
                                Collection<String> contributorSettlementIds, Collection<String> participantIds,
                                long startedDay, int transitDays, int transitProgress, int phaseDays,
                                int supplyRiskPermille, int supplyReadinessPermille, long finishedDay, long revision) {
        public CampaignState { contributorSettlementIds = List.copyOf(contributorSettlementIds); participantIds = List.copyOf(participantIds); }
    }
    public record SettlementState(String id, Map<FrontierResource, Long> stocks, long netCredit,
                                  FrontierCivicState civicState, int threatPermille, int foodReserveDaysMilli,
                                  int rationPermille, long civicRevision) {
        public SettlementState { stocks = Map.copyOf(stocks); }
        /** Short form remains for focused domain fixtures that predate civic policy persistence. */
        public SettlementState(String id, Map<FrontierResource, Long> stocks, long netCredit) {
            this(id, stocks, netCredit, FrontierCivicState.NORMAL, 0, 0, 1_000, 0);
        }
    }

    private FrontierStateHydration() { }

    public static FrontierWorldState restore(FrontierProfile profile, long seed, long day,
                                             Collection<ResidentState> residentStates,
                                             Collection<FacilityState> facilityStates,
                                             Collection<SettlementState> settlementStates,
                                             Collection<OperationState> operationStates,
                                             Collection<CargoState> cargoStates,
                                             Collection<HiveState> hiveStates,
                                             Collection<HiveOrganState> hiveOrganStates,
                                             Collection<BioformState> bioformStates,
                                             Collection<String> processedObservations) {
        return restore(profile, seed, day, residentStates, facilityStates, settlementStates, operationStates,
                cargoStates, hiveStates, hiveOrganStates, bioformStates, FrontierEcology.genesis(profile, seed).cells(),
                initialTissue(profile, seed), List.of(), List.of(), List.of(), List.of(), new AdaptationState(Map.of(), Map.of()), List.of(), List.of(), List.of(), processedObservations);
    }

    /** Compatibility overload for callers persisting assaults before human field operations existed. */
    public static FrontierWorldState restore(FrontierProfile profile, long seed, long day,
                                             Collection<ResidentState> residentStates,
                                             Collection<FacilityState> facilityStates,
                                             Collection<SettlementState> settlementStates,
                                             Collection<OperationState> operationStates,
                                             Collection<CargoState> cargoStates,
                                             Collection<HiveState> hiveStates,
                                             Collection<HiveOrganState> hiveOrganStates,
                                             Collection<BioformState> bioformStates,
                                             Collection<AssaultState> assaultStates,
                                             Collection<String> processedObservations) {
        return restore(profile, seed, day, residentStates, facilityStates, settlementStates, operationStates,
                cargoStates, hiveStates, hiveOrganStates, bioformStates, FrontierEcology.genesis(profile, seed).cells(),
                initialTissue(profile, seed), List.of(), List.of(), List.of(), List.of(), new AdaptationState(Map.of(), Map.of()), assaultStates, List.of(), List.of(), processedObservations);
    }

    public static FrontierWorldState restore(FrontierProfile profile, long seed, long day,
                                             Collection<ResidentState> residentStates,
                                             Collection<FacilityState> facilityStates,
                                             Collection<SettlementState> settlementStates,
                                             Collection<OperationState> operationStates,
                                             Collection<CargoState> cargoStates,
                                             Collection<HiveState> hiveStates,
                                             Collection<HiveOrganState> hiveOrganStates,
                                             Collection<BioformState> bioformStates,
                                             Collection<FrontierEcology.Cell> ecologyCells,
                                             Collection<FrontierHiveTissueCell> hiveTissue,
                                             Collection<MorphogenesisProjectState> morphogenesisProjectStates,
                                             Collection<HarvesterRunState> harvesterRunStates,
                                             Collection<PropagationRunState> propagationRunStates,
                                             Collection<LatentColonyState> latentColonyStates,
                                             AdaptationState adaptationState,
                                             Collection<AssaultState> assaultStates,
                                             Collection<FieldOperationState> fieldOperationStates,
                                             Collection<CampaignState> campaignStates,
                                             Collection<String> processedObservations) {
        Objects.requireNonNull(profile, "profile");
        FrontierWorldState state = FrontierWorldFactory.create(profile, seed);
        state.restoreDay(day);
        state.restoreEcology(FrontierEcology.restore(profile.widthCells(), profile.heightCells(), List.copyOf(ecologyCells)));
        state.restoreHiveTissue(List.copyOf(hiveTissue));
        Map<String, ResidentState> residents = residentStates.stream().collect(java.util.stream.Collectors.toMap(
                ResidentState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate resident " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, FacilityState> facilities = facilityStates.stream().collect(java.util.stream.Collectors.toMap(
                FacilityState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate facility " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, SettlementState> settlements = settlementStates.stream().collect(java.util.stream.Collectors.toMap(
                SettlementState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate settlement " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, OperationState> operations = operationStates.stream().collect(java.util.stream.Collectors.toMap(
                OperationState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate operation " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, HiveState> hives = hiveStates.stream().collect(java.util.stream.Collectors.toMap(
                HiveState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate hive " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, HiveOrganState> organs = hiveOrganStates.stream().collect(java.util.stream.Collectors.toMap(
                HiveOrganState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate hive organ " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, MorphogenesisProjectState> morphogenesisProjects = morphogenesisProjectStates.stream()
                .collect(java.util.stream.Collectors.toMap(MorphogenesisProjectState::id, value -> value, (left, right) -> {
                    throw new IllegalArgumentException("duplicate morphogenesis project " + left.id());
                }, java.util.LinkedHashMap::new));
        Map<String, HarvesterRunState> harvesterRuns = harvesterRunStates.stream().collect(java.util.stream.Collectors.toMap(
                HarvesterRunState::id, value -> value, (left, right) -> {
                    throw new IllegalArgumentException("duplicate harvester run " + left.id());
                }, java.util.LinkedHashMap::new));
        Map<String, PropagationRunState> propagationRuns = propagationRunStates.stream().collect(java.util.stream.Collectors.toMap(
                PropagationRunState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate propagation " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, LatentColonyState> latentColonies = latentColonyStates.stream().collect(java.util.stream.Collectors.toMap(
                LatentColonyState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate latent colony " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, BioformState> bioforms = bioformStates.stream().collect(java.util.stream.Collectors.toMap(
                BioformState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate bioform " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, AssaultState> assaults = assaultStates.stream().collect(java.util.stream.Collectors.toMap(
                AssaultState::id, value -> value, (left, right) -> { throw new IllegalArgumentException("duplicate assault " + left.id()); },
                java.util.LinkedHashMap::new));
        Map<String, FieldOperationState> fieldOperations = fieldOperationStates.stream().collect(java.util.stream.Collectors.toMap(
                FieldOperationState::id, value -> value, (left, right) -> {
                    throw new IllegalArgumentException("duplicate field operation " + left.id());
                }, java.util.LinkedHashMap::new));
        Map<String, CampaignState> campaigns = campaignStates.stream().collect(java.util.stream.Collectors.toMap(
                CampaignState::id, value -> value, (left, right) -> {
                    throw new IllegalArgumentException("duplicate campaign " + left.id());
                }, java.util.LinkedHashMap::new));
        if (!residents.keySet().equals(state.residents().stream().map(FrontierResident::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))) {
            throw new IllegalArgumentException("persisted resident identity does not match deterministic genesis");
        }
        if (!facilities.keySet().equals(state.facilities().stream().map(FrontierFacility::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))) {
            throw new IllegalArgumentException("persisted facility identity does not match deterministic genesis");
        }
        if (!settlements.keySet().equals(state.settlements().stream().map(FrontierSettlement::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))) {
            throw new IllegalArgumentException("persisted settlement identity does not match deterministic genesis");
        }
        if (!operations.keySet().equals(state.operations().stream().map(FrontierOperation::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))) {
            throw new IllegalArgumentException("persisted operation identity does not match deterministic genesis");
        }
        if (!hives.keySet().equals(state.hives().stream().map(FrontierHive::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))) {
            throw new IllegalArgumentException("persisted hive identity does not match deterministic genesis");
        }
        Map<String, FrontierHiveOrgan> genesisOrgans = state.hiveOrgans().stream().collect(java.util.stream.Collectors.toMap(
                FrontierHiveOrgan::id, value -> value, (left, right) -> { throw new IllegalStateException("duplicate genesis organ"); },
                java.util.LinkedHashMap::new));
        if (!organs.keySet().containsAll(genesisOrgans.keySet())) {
            throw new IllegalArgumentException("persisted hive organ identity is missing deterministic genesis");
        }
        state.settlements().forEach(value -> {
            SettlementState saved = settlements.get(value.id());
            value.restoreEconomy(saved.stocks(), saved.netCredit());
            value.restoreCivic(saved.civicState(), saved.threatPermille(), saved.foodReserveDaysMilli(),
                    saved.rationPermille(), saved.civicRevision());
        });
        state.residents().forEach(value -> {
            ResidentState saved = residents.get(value.id());
            value.restore(saved.alive(), saved.revision());
        });
        state.facilities().forEach(value -> {
            FacilityState saved = facilities.get(value.id());
            value.restore(saved.state(), saved.revision());
        });
        state.operations().forEach(value -> {
            OperationState saved = operations.get(value.id());
            value.restore(saved.state(), saved.revision());
        });
        for (HiveOrganState saved : organs.values()) {
            FrontierHiveOrgan genesis = genesisOrgans.get(saved.id());
            if (genesis != null) {
                if ((saved.hiveId() != null && !saved.hiveId().equals(genesis.hiveId()))
                        || (saved.kind() != null && saved.kind() != genesis.kind())
                        || (saved.position() != null && !saved.position().equals(genesis.position()))
                        || (saved.bornDay() >= 0 && saved.bornDay() != genesis.bornDay())) {
                    throw new IllegalArgumentException("persisted genesis organ does not match " + saved.id());
                }
                genesis.restore(saved.state(), saved.revision());
                continue;
            }
            if (saved.hiveId() == null || saved.kind() == null || saved.position() == null || saved.bornDay() < 1
                    || saved.bornDay() > day || !state.hive(saved.hiveId()).isPresent()
                    || !FrontierHiveOrgan.dynamicIdFor(saved.hiveId(), saved.kind(), saved.position(), saved.bornDay()).equals(saved.id())) {
                throw new IllegalArgumentException("invalid persisted dynamic hive organ " + saved.id());
            }
            FrontierHiveOrgan restored = new FrontierHiveOrgan(saved.id(), saved.hiveId(), saved.kind(), saved.position(), saved.bornDay());
            restored.restore(saved.state(), saved.revision());
            state.putHiveOrgan(restored);
        }
        state.hives().forEach(value -> {
            HiveState saved = hives.get(value.id());
            boolean coreAlive = state.hiveOrgans().stream().anyMatch(organ -> organ.hiveId().equals(value.id())
                    && organ.kind() == FrontierHiveOrganKind.CORE && organ.state() == FrontierHiveOrgan.State.ALIVE);
            boolean organAlive = state.hiveOrgans().stream().anyMatch(organ -> organ.hiveId().equals(value.id())
                    && organ.state() == FrontierHiveOrgan.State.ALIVE);
            FrontierHive.State expected = coreAlive ? FrontierHive.State.ACTIVE
                    : organAlive ? FrontierHive.State.DECAPITATED : FrontierHive.State.ERADICATED;
            if (saved.state() != expected) throw new IllegalArgumentException("inconsistent persisted hive state " + value.id());
            value.restore(saved.biomass(), saved.geneticMaterial(), saved.state(), saved.revision());
        });
        state.adaptationLedger().restore(adaptationState.levels(), adaptationState.damageMemory());
        for (MorphogenesisProjectState saved : morphogenesisProjects.values()) {
            if (saved.hiveId() == null || saved.sourceOrganId() == null || saved.kind() == null || saved.position() == null
                    || saved.startedDay() > day
                    || !FrontierMorphogenesisProject.idFor(saved.hiveId(), saved.kind(), saved.position(), saved.startedDay())
                    .equals(saved.id())) {
                throw new IllegalArgumentException("invalid persisted morphogenesis project " + saved.id());
            }
            FrontierMorphogenesisProject project = new FrontierMorphogenesisProject(saved.id(), saved.hiveId(), saved.sourceOrganId(),
                    saved.kind(), saved.position(), saved.startedDay());
            project.restore(saved.remainingDays(), saved.revision());
            state.putMorphogenesisProject(project);
        }
        java.util.LinkedHashSet<String> initialBioformIds = state.bioforms().stream().map(FrontierBioform::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!bioforms.keySet().containsAll(initialBioformIds)) {
            throw new IllegalArgumentException("persisted bioform identity is missing deterministic genesis");
        }
        for (BioformState saved : bioforms.values()) {
            if (saved.bornDay() > day || !state.hive(saved.hiveId()).isPresent()
                    || !FrontierBioform.idFor(saved.hiveId(), saved.bornDay(), saved.birthOrdinal()).equals(saved.id())) {
                throw new IllegalArgumentException("invalid persisted bioform " + saved.id());
            }
            FrontierBioform existing = state.bioform(saved.id()).orElse(null);
            if (existing != null) {
                if (!existing.hiveId().equals(saved.hiveId()) || existing.kind() != saved.kind()
                        || existing.bornDay() != saved.bornDay() || existing.birthOrdinal() != saved.birthOrdinal()) {
                    throw new IllegalArgumentException("persisted genesis bioform does not match " + saved.id());
                }
                existing.restore(saved.alive(), saved.deathDay(), saved.revision());
            } else {
                FrontierBioform restored = new FrontierBioform(saved.id(), saved.hiveId(), saved.kind(), saved.bornDay(), saved.birthOrdinal());
                restored.restore(saved.alive(), saved.deathDay(), saved.revision());
                state.putBioform(restored);
            }
        }
        for (HarvesterRunState saved : harvesterRuns.values()) {
            FrontierHive hive = state.hive(saved.hiveId()).orElseThrow(() ->
                    new IllegalArgumentException("harvester run references unknown hive"));
            FrontierHiveOrgan source = state.hiveOrgan(saved.sourceOrganId()).orElseThrow(() ->
                    new IllegalArgumentException("harvester run references unknown source organ"));
            FrontierBioform bioform = state.bioform(saved.bioformId()).orElseThrow(() ->
                    new IllegalArgumentException("harvester run references unknown bioform"));
            if (!source.hiveId().equals(hive.id()) || source.kind() != FrontierHiveOrganKind.BROOD_SAC
                    || !bioform.hiveId().equals(hive.id()) || bioform.kind() != FrontierBioformKind.HARVESTER
                    || saved.startedDay() > day || !source.position().equals(saved.origin()) || !inBounds(profile, saved.origin())
                    || !inBounds(profile, saved.foragePosition()) || !inBounds(profile, saved.position())
                    || !FrontierHarvesterRun.idFor(saved.hiveId(), saved.bioformId(), saved.startedDay()).equals(saved.id())) {
                throw new IllegalArgumentException("invalid persisted harvester run " + saved.id());
            }
            FrontierHarvesterRun run = new FrontierHarvesterRun(saved.id(), saved.hiveId(), saved.sourceOrganId(), saved.bioformId(),
                    source.position(), saved.foragePosition(), saved.startedDay(), saved.outboundDays());
            run.restore(saved.state(), saved.position(), saved.receiverOrganId(), saved.transitProgress(), saved.returnDays(),
                    saved.cargo(), saved.geneticCargo(), saved.finishedDay(), saved.revision());
            state.harvesters().put(state, run);
        }
        for (PropagationRunState saved : propagationRuns.values()) {
            FrontierHive hive = state.hive(saved.hiveId()).orElseThrow(() -> new IllegalArgumentException("propagation references unknown hive"));
            FrontierHiveOrgan source = state.hiveOrgan(saved.sourceOrganId()).orElseThrow(() ->
                    new IllegalArgumentException("propagation references unknown source organ"));
            FrontierBioform bioform = state.bioform(saved.bioformId()).orElseThrow(() ->
                    new IllegalArgumentException("propagation references unknown bioform"));
            if (!source.hiveId().equals(hive.id()) || source.kind() != FrontierHiveOrganKind.SPORULATOR
                    || !bioform.hiveId().equals(hive.id()) || bioform.kind() != FrontierBioformKind.PROPAGULE_CARRIER
                    || saved.startedDay() > day || !source.position().equals(saved.origin()) || !inBounds(profile, saved.origin())
                    || !inBounds(profile, saved.target()) || !inBounds(profile, saved.position())
                    || (saved.state() == FrontierPropagationRun.State.DEPLOYED && !latentColonies.containsKey(saved.latentColonyId()))
                    || !FrontierPropagationRun.idFor(saved.hiveId(), saved.bioformId(), saved.startedDay()).equals(saved.id())) {
                throw new IllegalArgumentException("invalid persisted propagation " + saved.id());
            }
            FrontierPropagationRun run = new FrontierPropagationRun(saved.id(), saved.hiveId(), saved.sourceOrganId(), saved.bioformId(),
                    saved.origin(), saved.target(), saved.startedDay(), saved.transitDays());
            run.restore(saved.state(), saved.position(), saved.transitProgress(), saved.latentColonyId(), saved.finishedDay(), saved.revision());
            state.propagations().putRun(state, run);
        }
        for (LatentColonyState saved : latentColonies.values()) {
            if (!state.hive(saved.hiveId()).isPresent() || !state.hiveOrgan(saved.sourceOrganId()).isPresent()
                    || saved.depositedDay() > day || saved.propagules() < 1 || saved.strength() < 1 || saved.strength() > 1_000
                    || saved.cleared() || !inBounds(profile, saved.position())) {
                throw new IllegalArgumentException("invalid persisted latent colony " + saved.id());
            }
            FrontierLatentColony colony = new FrontierLatentColony(saved.id(), saved.hiveId(), saved.sourceOrganId(), saved.position(),
                    saved.depositedDay(), saved.propagules(), saved.strength());
            colony.restore(saved.propagules(), saved.strength(), saved.cleared(), saved.revision());
            state.propagations().putColony(state, colony);
        }
        for (AssaultState saved : assaults.values()) {
            FrontierHive hive = state.hive(saved.hiveId()).orElseThrow(() -> new IllegalArgumentException("assault references unknown hive"));
            if (!state.settlement(saved.targetSettlementId()).isPresent()
                    || !FrontierAssault.idFor(saved.hiveId(), saved.startedDay()).equals(saved.id())
                    || saved.startedDay() > day || saved.position().x() < 0 || saved.position().x() >= profile.widthCells()
                    || saved.position().z() < 0 || saved.position().z() >= profile.heightCells()) {
                throw new IllegalArgumentException("invalid persisted assault " + saved.id());
            }
            for (String participantId : saved.participantIds()) {
                FrontierBioform participant = state.bioform(participantId).orElseThrow(() ->
                        new IllegalArgumentException("assault references unknown bioform " + participantId));
                if (!participant.hiveId().equals(saved.hiveId())) {
                    throw new IllegalArgumentException("assault participant belongs to another hive " + participantId);
                }
            }
            FrontierAssault assault = new FrontierAssault(saved.id(), saved.hiveId(), saved.targetSettlementId(), saved.kind(),
                    List.copyOf(saved.participantIds()), saved.startedDay(), saved.transitDays(), hive.anchor());
            assault.restore(saved.state(), saved.position(), saved.transitProgress(), saved.engagementDays(),
                    saved.finishedDay(), saved.revision());
            state.putAssault(assault);
        }
        for (FieldOperationState saved : fieldOperations.values()) {
            FrontierSettlement settlement = state.settlement(saved.settlementId()).orElseThrow(() ->
                    new IllegalArgumentException("field operation references unknown settlement"));
            if (!state.assault(saved.targetAssaultId()).isPresent()
                    || !FrontierFieldOperation.idFor(saved.settlementId(), saved.targetAssaultId(), saved.startedDay()).equals(saved.id())
                    || saved.startedDay() > day || saved.position().x() < 0 || saved.position().x() >= profile.widthCells()
                    || saved.position().z() < 0 || saved.position().z() >= profile.heightCells()) {
                throw new IllegalArgumentException("invalid persisted field operation " + saved.id());
            }
            for (String participantId : saved.participantIds()) {
                FrontierResident participant = state.resident(participantId).orElseThrow(() ->
                        new IllegalArgumentException("field operation references unknown resident " + participantId));
                if (!participant.settlementId().equals(saved.settlementId()) || participant.role() != FrontierResidentRole.GUARD) {
                    throw new IllegalArgumentException("field operation participant is not a local guard " + participantId);
                }
            }
            FrontierFieldOperation operation = new FrontierFieldOperation(saved.id(), saved.settlementId(), saved.targetAssaultId(),
                    saved.kind(), List.copyOf(saved.participantIds()), saved.startedDay(), saved.transitDays(), settlement.center());
            operation.restore(saved.state(), saved.position(), saved.transitProgress(), saved.stationDays(), saved.finishedDay(),
                    saved.revision());
            state.putFieldOperation(operation);
        }
        for (CampaignState saved : campaigns.values()) {
            FrontierSettlement leader = state.settlement(saved.leaderSettlementId()).orElseThrow(() ->
                    new IllegalArgumentException("campaign references unknown leader"));
            FrontierHive hive = state.hive(saved.targetHiveId()).orElseThrow(() ->
                    new IllegalArgumentException("campaign references unknown hive"));
            FrontierHiveOrgan target = state.hiveOrgan(saved.targetOrganId()).orElseThrow(() ->
                    new IllegalArgumentException("campaign references unknown target organ"));
            if (!target.hiveId().equals(hive.id()) || target.kind() != FrontierHiveOrganKind.CORE || saved.startedDay() > day
                    || !FrontierCampaign.idFor(leader.id(), hive.id(), saved.startedDay()).equals(saved.id())
                    || !inBounds(profile, saved.position())) {
                throw new IllegalArgumentException("invalid persisted campaign " + saved.id());
            }
            for (String contributorId : saved.contributorSettlementIds()) {
                if (!state.settlement(contributorId).isPresent()) throw new IllegalArgumentException("campaign contributor is unknown");
            }
            for (String participantId : saved.participantIds()) {
                FrontierResident participant = state.resident(participantId).orElseThrow(() ->
                        new IllegalArgumentException("campaign references unknown resident"));
                if (participant.role() != FrontierResidentRole.GUARD || !saved.contributorSettlementIds().contains(participant.settlementId())) {
                    throw new IllegalArgumentException("campaign participant is not a coalition guard");
                }
            }
            FrontierCampaign campaign = new FrontierCampaign(saved.id(), saved.leaderSettlementId(), saved.targetHiveId(), saved.targetOrganId(),
                    saved.kind(), List.copyOf(saved.contributorSettlementIds()), List.copyOf(saved.participantIds()), saved.startedDay(),
                    saved.transitDays(), leader.center());
            campaign.restore(saved.phase(), saved.position(), saved.transitProgress(), saved.phaseDays(), saved.supplyRiskPermille(),
                    saved.supplyReadinessPermille(), saved.finishedDay(), saved.revision());
            state.putCampaign(campaign);
        }
        java.util.LinkedHashSet<String> cargoIds = new java.util.LinkedHashSet<>();
        for (CargoState saved : cargoStates) {
            if (!cargoIds.add(saved.id())) throw new IllegalArgumentException("duplicate cargo " + saved.id());
            FrontierRoute route = state.routes().stream().filter(value -> value.id().equals(saved.routeId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("cargo references unknown route " + saved.routeId()));
            boolean endpointsMatch = (route.leftSettlementId().equals(saved.sourceSettlementId())
                    && route.rightSettlementId().equals(saved.destinationSettlementId()))
                    || (route.rightSettlementId().equals(saved.sourceSettlementId())
                    && route.leftSettlementId().equals(saved.destinationSettlementId()));
            if (!endpointsMatch || saved.dispatchedDay() > day
                    || !FrontierCargo.idFor(saved.routeId(), saved.dispatchedDay()).equals(saved.id())) {
                throw new IllegalArgumentException("invalid persisted cargo " + saved.id());
            }
            state.putCargo(new FrontierCargo(saved.id(), saved.routeId(), saved.sourceSettlementId(), saved.destinationSettlementId(),
                    saved.resource(), saved.amount(), saved.creditValue(), saved.dispatchedDay(), saved.revision()));
        }
        processedObservations.forEach(state::recordObservation);
        state.assertCreditBalanced();
        return state;
    }

    private static Collection<FrontierHiveTissueCell> initialTissue(FrontierProfile profile, long seed) {
        return FrontierWorldFactory.create(profile, seed).hiveTissue();
    }
    private static boolean inBounds(FrontierProfile profile, FrontierPoint point) {
        return point != null && point.x() >= 0 && point.x() < profile.widthCells()
                && point.z() >= 0 && point.z() < profile.heightCells();
    }
}
