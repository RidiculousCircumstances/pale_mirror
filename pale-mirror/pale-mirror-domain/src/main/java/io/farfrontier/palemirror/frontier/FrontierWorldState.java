package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mutable only through {@link FrontierCommandProcessor}; Minecraft is never a second state owner. */
public final class FrontierWorldState {
    private static final int MAX_EVENTS = 4_096;
    private static final int MAX_OBSERVATIONS = 8_192;
    private static final int MAX_ORGANS_PER_HIVE = 32;
    private static final int MAX_MORPHOGENESIS_PROJECTS_PER_HIVE = 1;
    private static final long HARVESTER_MINIMUM_LOAD = 2;
    /** Python's 14-point minimum, represented at ×100 to retain the 2.2 distance cost exactly. */
    private static final long HARVESTER_MINIMUM_TARGET_SCORE = 1_400;
    private static final int COMMAND_SIGNAL_THRESHOLD = 320;
    private final FrontierProfile profile;
    private final long seed;
    private FrontierEcology ecology;
    private final Map<String, FrontierSettlement> settlements = new LinkedHashMap<>();
    private final Map<String, FrontierResident> residents = new LinkedHashMap<>();
    private final Map<String, FrontierFacility> facilities = new LinkedHashMap<>();
    private final Map<String, FrontierRoute> routes = new LinkedHashMap<>();
    private final Map<String, FrontierOperation> operations = new LinkedHashMap<>();
    private final Map<String, FrontierCargo> cargo = new LinkedHashMap<>();
    private final Map<String, FrontierHive> hives = new LinkedHashMap<>();
    private final Map<String, FrontierHiveOrgan> hiveOrgans = new LinkedHashMap<>();
    private final Map<String, Map<FrontierPoint, Integer>> hiveTissue = new LinkedHashMap<>();
    private final Map<String, FrontierMorphogenesisProject> morphogenesisProjects = new LinkedHashMap<>();
    private final Map<String, FrontierBioform> bioforms = new LinkedHashMap<>();
    private final FrontierHarvesterRegistry harvesterRuns = new FrontierHarvesterRegistry();
    private final FrontierPropagationRegistry propagations = new FrontierPropagationRegistry();
    private final FrontierAdaptationLedger adaptations = new FrontierAdaptationLedger();
    private final Map<String, FrontierAssault> assaults = new LinkedHashMap<>();
    private final Map<String, FrontierFieldOperation> fieldOperations = new LinkedHashMap<>();
    private final FrontierCampaignRegistry campaigns = new FrontierCampaignRegistry();
    private final List<FrontierEvent> events = new ArrayList<>();
    private final LinkedHashSet<String> processedObservations = new LinkedHashSet<>();
    private long day;
    private long sequence;
    FrontierWorldState(FrontierProfile profile, long seed) {
        this.profile = profile;
        this.seed = seed;
        this.ecology = FrontierEcology.genesis(profile, seed);
    }
    public FrontierProfile profile() { return profile; }
    public long seed() { return seed; }
    public long day() { return day; }
    /** Canonical finite biological substrate; callers receive its read-only domain API only. */
    public FrontierEcology ecology() { return ecology; }
    public Collection<FrontierSettlement> settlements() { return List.copyOf(settlements.values()); }
    public Collection<FrontierResident> residents() { return List.copyOf(residents.values()); }
    public Collection<FrontierFacility> facilities() { return List.copyOf(facilities.values()); }
    public Collection<FrontierRoute> routes() { return List.copyOf(routes.values()); }
    public Collection<FrontierOperation> operations() { return List.copyOf(operations.values()); }
    public Collection<FrontierCargo> cargo() { return List.copyOf(cargo.values()); }
    public Collection<FrontierHive> hives() { return List.copyOf(hives.values()); }
    public Collection<FrontierHiveOrgan> hiveOrgans() { return List.copyOf(hiveOrgans.values()); }
    /** At most one active project per hive, retained only until completion or cancellation. */
    public Collection<FrontierMorphogenesisProject> morphogenesisProjects() { return List.copyOf(morphogenesisProjects.values()); }
    public Collection<FrontierHiveTissueCell> hiveTissue() {
        return hiveTissue.entrySet().stream().flatMap(entry -> entry.getValue().entrySet().stream()
                        .map(cell -> new FrontierHiveTissueCell(entry.getKey(), cell.getKey(), cell.getValue())))
                .sorted(java.util.Comparator.comparing(FrontierHiveTissueCell::hiveId)
                        .thenComparing(value -> value.position().z()).thenComparing(value -> value.position().x())).toList();
    }
    public Collection<FrontierBioform> bioforms() { return List.copyOf(bioforms.values()); }
    /** Active and retained-terminal biological return trips; terminal entries are compacted deterministically. */
    public Collection<FrontierHarvesterRun> harvesterRuns() { return harvesterRuns.values(); }
    public Collection<FrontierPropagationRun> propagationRuns() { return propagations.runs(); }
    public Collection<FrontierLatentColony> latentColonies() { return propagations.colonies(); }
    public Map<FrontierAdaptation, Integer> adaptations() { return adaptations.levels(); }
    public Map<FrontierDamageKind, Long> adaptationMemory() { return adaptations.memory(); }
    public Collection<FrontierAssault> assaults() { return List.copyOf(assaults.values()); }
    public Collection<FrontierFieldOperation> fieldOperations() { return List.copyOf(fieldOperations.values()); }
    public Collection<FrontierCampaign> campaigns() { return campaigns.values(); }
    public Optional<FrontierSettlement> settlement(String id) { return Optional.ofNullable(settlements.get(id)); }
    public List<FrontierEvent> events() { return List.copyOf(events); }
    public Collection<String> processedObservationIds() { return List.copyOf(processedObservations); }
    public Optional<FrontierResident> resident(String id) { return Optional.ofNullable(residents.get(id)); }
    public Optional<FrontierFacility> facility(String id) { return Optional.ofNullable(facilities.get(id)); }
    public Optional<FrontierOperation> operation(String id) { return Optional.ofNullable(operations.get(id)); }
    public Optional<FrontierCargo> cargo(String id) { return Optional.ofNullable(cargo.get(id)); }
    public Optional<FrontierHive> hive(String id) { return Optional.ofNullable(hives.get(id)); }
    public Optional<FrontierHiveOrgan> hiveOrgan(String id) { return Optional.ofNullable(hiveOrgans.get(id)); }
    public Optional<FrontierMorphogenesisProject> morphogenesisProject(String id) { return Optional.ofNullable(morphogenesisProjects.get(id)); }
    public Optional<FrontierBioform> bioform(String id) { return Optional.ofNullable(bioforms.get(id)); }
    public Optional<FrontierHarvesterRun> harvesterRun(String id) { return harvesterRuns.find(id); }
    public Optional<FrontierAssault> assault(String id) { return Optional.ofNullable(assaults.get(id)); }
    public Optional<FrontierFieldOperation> fieldOperation(String id) { return Optional.ofNullable(fieldOperations.get(id)); }
    public Optional<FrontierCampaign> campaign(String id) { return campaigns.find(id); }
    public Optional<FrontierCargo> cargoForRoute(String routeId) {
        return cargo.values().stream().filter(value -> value.routeId().equals(routeId)).findFirst();
    }
    public int alivePopulation(String settlementId) {
        return settlements.get(settlementId).residentIds().stream().map(residents::get).filter(FrontierResident::alive).toList().size();
    }
    public long alivePopulationTotal() { return residents.values().stream().filter(FrontierResident::alive).count(); }
    void putSettlement(FrontierSettlement value) { settlements.put(value.id(), value); }
    void putResident(FrontierResident value) { residents.put(value.id(), value); }
    void putFacility(FrontierFacility value) { facilities.put(value.id(), value); }
    void putRoute(FrontierRoute value) { routes.put(value.id(), value); }
    void putOperation(FrontierOperation value) {
        if (operations.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate operation " + value.id());
        if (operations.values().stream().filter(operation -> operation.facilityId().equals(value.facilityId())).count() != 1) {
            operations.remove(value.id());
            throw new IllegalArgumentException("facility already has an operation " + value.facilityId());
        }
    }
    void putCargo(FrontierCargo value) {
        if (cargo.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate cargo " + value.id());
        if (cargoForRoute(value.routeId()).filter(existing -> !existing.id().equals(value.id())).isPresent()) {
            cargo.remove(value.id());
            throw new IllegalArgumentException("route already has in-transit cargo " + value.routeId());
        }
    }
    void removeCargo(String id) { cargo.remove(id); }
    void putHive(FrontierHive value) {
        if (hives.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate hive " + value.id());
        hiveTissue.put(value.id(), new LinkedHashMap<>());
    }
    void putHiveOrgan(FrontierHiveOrgan value) {
        if (!hives.containsKey(value.hiveId())) throw new IllegalArgumentException("organ references unknown hive " + value.hiveId());
        if (!inBounds(value.position())) throw new IllegalArgumentException("organ position is outside Frontier profile");
        if (hiveOrgans.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate hive organ " + value.id());
        if (hiveOrgans.values().stream().filter(organ -> organ.hiveId().equals(value.hiveId())).count() > MAX_ORGANS_PER_HIVE
                || hiveOrgans.values().stream().filter(organ -> organ.hiveId().equals(value.hiveId())
                && organ.position().equals(value.position())).count() != 1) {
            hiveOrgans.remove(value.id());
            throw new IllegalArgumentException("invalid hive organ capacity or position " + value.id());
        }
    }
    void putMorphogenesisProject(FrontierMorphogenesisProject value) {
        FrontierHiveOrgan source = hiveOrgans.get(value.sourceOrganId());
        if (!hives.containsKey(value.hiveId()) || source == null || !source.hiveId().equals(value.hiveId())
                || source.state() != FrontierHiveOrgan.State.ALIVE
                || (source.kind() != FrontierHiveOrganKind.CORE && source.kind() != FrontierHiveOrganKind.SYNAPSE)
                || !inBounds(value.position())
                || value.startedDay() > day || !FrontierMorphogenesisProject.idFor(value.hiveId(), value.kind(),
                value.position(), value.startedDay()).equals(value.id())
                || hiveTissue.getOrDefault(value.hiveId(), Map.of()).getOrDefault(value.position(), 0)
                < value.requiredTissueStrength()
                || hiveOrgans.values().stream().anyMatch(organ -> organ.hiveId().equals(value.hiveId())
                && organ.position().equals(value.position()))
                || morphogenesisProjects.values().stream().anyMatch(project -> project.hiveId().equals(value.hiveId()))) {
            throw new IllegalArgumentException("invalid morphogenesis project " + value.id());
        }
        if (morphogenesisProjects.putIfAbsent(value.id(), value) != null) {
            throw new IllegalArgumentException("duplicate morphogenesis project " + value.id());
        }
        if (morphogenesisProjects.values().stream().filter(project -> project.hiveId().equals(value.hiveId())).count()
                > MAX_MORPHOGENESIS_PROJECTS_PER_HIVE) {
            morphogenesisProjects.remove(value.id());
            throw new IllegalArgumentException("hive already has a growing morphogenesis project " + value.hiveId());
        }
    }
    void removeMorphogenesisProject(String id) { morphogenesisProjects.remove(id); }
    List<FrontierMorphogenesisProject> abortMorphogenesisForHive(String hiveId) {
        List<FrontierMorphogenesisProject> aborted = morphogenesisProjects.values().stream()
                .filter(value -> value.hiveId().equals(hiveId)).toList();
        aborted.forEach(value -> morphogenesisProjects.remove(value.id()));
        return List.copyOf(aborted);
    }
    List<FrontierMorphogenesisProject> abortMorphogenesisForSource(String sourceOrganId) {
        List<FrontierMorphogenesisProject> aborted = morphogenesisProjects.values().stream()
                .filter(value -> value.sourceOrganId().equals(sourceOrganId)).toList();
        aborted.forEach(value -> morphogenesisProjects.remove(value.id()));
        return List.copyOf(aborted);
    }
    Optional<FrontierMorphogenesisProject> startMorphogenesis(String hiveId, String sourceOrganId,
                                                                FrontierHiveOrganKind kind, FrontierPoint position) {
        FrontierHive hive = hives.get(hiveId);
        FrontierHiveOrgan source = hiveOrgans.get(sourceOrganId);
        FrontierMorphogenesisProject.Specification specification = FrontierMorphogenesisProject.specification(kind);
        if (hive == null || hive.state() != FrontierHive.State.ACTIVE || source == null || !source.hiveId().equals(hiveId)
                || source.state() != FrontierHiveOrgan.State.ALIVE
                || (source.kind() != FrontierHiveOrganKind.CORE && source.kind() != FrontierHiveOrganKind.SYNAPSE)
                || !inBounds(position)
                || morphogenesisProjects.values().stream().anyMatch(value -> value.hiveId().equals(hiveId))
                || hiveOrgans.values().stream().filter(value -> value.hiveId().equals(hiveId)).count() >= MAX_ORGANS_PER_HIVE
                || hiveOrgans.values().stream().anyMatch(value -> value.hiveId().equals(hiveId) && value.position().equals(position))
                || hiveTissue.getOrDefault(hiveId, Map.of()).getOrDefault(position, 0) < specification.requiredTissueStrength()
                || hiveSignal(hiveId, source.position()) < COMMAND_SIGNAL_THRESHOLD) {
            return Optional.empty();
        }
        String id = FrontierMorphogenesisProject.idFor(hiveId, kind, position, day);
        if (morphogenesisProjects.containsKey(id) || !hive.spendBiomass(specification.biomassCost())) return Optional.empty();
        FrontierMorphogenesisProject project = new FrontierMorphogenesisProject(id, hiveId, sourceOrganId, kind, position, day);
        putMorphogenesisProject(project);
        return Optional.of(project);
    }
    void putHiveTissue(FrontierHiveTissueCell value) {
        if (!hives.containsKey(value.hiveId()) || !inBounds(value.position())) {
            throw new IllegalArgumentException("hive tissue references an invalid hive or position");
        }
        Map<FrontierPoint, Integer> cells = hiveTissue.get(value.hiveId());
        if (cells.putIfAbsent(value.position(), value.strength()) != null) {
            throw new IllegalArgumentException("duplicate hive tissue cell " + value.position());
        }
    }
    void restoreHiveTissue(Collection<FrontierHiveTissueCell> values) {
        Map<String, Map<FrontierPoint, Integer>> replacement = new LinkedHashMap<>();
        hives.keySet().forEach(id -> replacement.put(id, new LinkedHashMap<>()));
        for (FrontierHiveTissueCell value : values) {
            if (!replacement.containsKey(value.hiveId()) || !inBounds(value.position())
                    || replacement.get(value.hiveId()).putIfAbsent(value.position(), value.strength()) != null) {
                throw new IllegalArgumentException("invalid persisted hive tissue");
            }
        }
        hiveTissue.clear();
        hiveTissue.putAll(replacement);
    }
    void putBioform(FrontierBioform value) {
        if (!hives.containsKey(value.hiveId())) throw new IllegalArgumentException("bioform references unknown hive " + value.hiveId());
        if (bioforms.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate bioform " + value.id());
    }
    FrontierHarvesterRegistry harvesters() { return harvesterRuns; }
    boolean bioformAssignedToAssault(String bioformId) {
        return assaults.values().stream().anyMatch(value -> !value.terminal() && value.participantIds().contains(bioformId));
    }
    FrontierPropagationRegistry propagations() { return propagations; }
    FrontierAdaptationLedger adaptationLedger() { return adaptations; }
    void compactDeadBioforms(long currentDay, long retentionDays) {
        if (retentionDays < 0) throw new IllegalArgumentException("retentionDays must not be negative");
        bioforms.values().stream().filter(value -> !value.alive() && value.bornDay() > 0
                        && currentDay - value.deathDay() > retentionDays)
                .filter(value -> assaults.values().stream().noneMatch(assault -> !assault.terminal()
                        && assault.participantIds().contains(value.id())))
                .map(FrontierBioform::id).toList().forEach(bioforms::remove);
    }
    void putAssault(FrontierAssault value) {
        if (!hives.containsKey(value.hiveId()) || !settlements.containsKey(value.targetSettlementId())) {
            throw new IllegalArgumentException("assault references an unknown hive or settlement");
        }
        if (value.participantIds().stream().anyMatch(valueId -> harvesterRuns.assigned(valueId) || propagations.assigned(valueId))) {
            throw new IllegalArgumentException("harvester bioform cannot also join an assault " + value.id());
        }
        if (assaults.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate assault " + value.id());
        if (!value.terminal() && assaults.values().stream().filter(existing -> !existing.terminal()
                && existing.hiveId().equals(value.hiveId())).count() != 1) {
            assaults.remove(value.id());
            throw new IllegalArgumentException("hive already has an active assault " + value.hiveId());
        }
    }
    void removeAssault(String id) { assaults.remove(id); }
    List<FrontierAssault> abortAssaultsForHive(String hiveId) {
        List<FrontierAssault> aborted = assaults.values().stream().filter(value -> value.hiveId().equals(hiveId))
                .filter(value -> value.abort(day)).toList();
        return List.copyOf(aborted);
    }
    List<FrontierAssault> abortAssaultsWithoutLivingParticipants() {
        return assaults.values().stream().filter(value -> !value.terminal())
                .filter(value -> value.participantIds().stream().map(bioforms::get).noneMatch(FrontierBioform::alive))
                .filter(value -> value.abort(day)).toList();
    }
    void putFieldOperation(FrontierFieldOperation value) {
        if (!settlements.containsKey(value.settlementId()) || !assaults.containsKey(value.targetAssaultId())) {
            throw new IllegalArgumentException("field operation references an unknown settlement or assault");
        }
        for (String participantId : value.participantIds()) {
            FrontierResident participant = residents.get(participantId);
            if (participant == null || !participant.settlementId().equals(value.settlementId())
                    || participant.role() != FrontierResidentRole.GUARD || residentAssignedToFieldOperation(participantId)) {
                throw new IllegalArgumentException("field operation has an invalid guard participant " + participantId);
            }
        }
        if (fieldOperations.putIfAbsent(value.id(), value) != null) {
            throw new IllegalArgumentException("duplicate field operation " + value.id());
        }
        if (!value.terminal() && fieldOperations.values().stream().filter(existing -> !existing.terminal()
                && existing.settlementId().equals(value.settlementId())
                && existing.targetAssaultId().equals(value.targetAssaultId())).count() != 1) {
            fieldOperations.remove(value.id());
            throw new IllegalArgumentException("settlement already has an active response to assault " + value.targetAssaultId());
        }
    }
    void removeFieldOperation(String id) { fieldOperations.remove(id); }
    int livingFieldParticipants(FrontierFieldOperation operation) {
        return (int) operation.participantIds().stream().map(residents::get).filter(FrontierResident::alive).count();
    }
    boolean residentIsDeployed(String residentId) {
        return campaigns.deployed(residentId) || fieldOperations.values().stream().anyMatch(operation -> !operation.terminal()
                && operation.participantIds().contains(residentId)
                && operation.state() != FrontierFieldOperation.State.ASSEMBLING);
    }
    boolean residentAssignedToFieldOperation(String residentId) {
        return fieldOperations.values().stream().anyMatch(operation -> !operation.terminal()
                && operation.participantIds().contains(residentId));
    }
    List<FrontierFieldOperation> abortFieldOperationsWithoutLivingParticipants() {
        return fieldOperations.values().stream().filter(value -> !value.terminal())
                .filter(value -> livingFieldParticipants(value) == 0).filter(value -> value.abort(day)).toList();
    }
    List<FrontierFieldOperation> beginReturnForAssault(String assaultId) {
        return fieldOperations.values().stream().filter(value -> value.targetAssaultId().equals(assaultId))
                .filter(value -> value.beginReturn()).toList();
    }
    void putCampaign(FrontierCampaign value) { campaigns.put(this, value); }
    FrontierCampaignRegistry campaignRegistry() { return campaigns; }
    List<FrontierCampaign> beginWithdrawForHive(String hiveId) { return campaigns.beginWithdrawForHive(hiveId); }
    List<FrontierCampaign> beginWithdrawForHiveExcept(String hiveId, String exemptCampaignId) {
        return campaigns.beginWithdrawForHiveExcept(hiveId, exemptCampaignId);
    }
    void assertCreditBalanced() { long total = settlements.values().stream().mapToLong(FrontierSettlement::netCredit).sum(); if (total != 0) throw new IllegalStateException("Frontier credit must net to zero, was " + total); }
    void advanceDay() { day++; }
    void restoreDay(long value) { if (value < 0) throw new IllegalArgumentException("day must not be negative"); day = value; }
    void restoreEcology(FrontierEcology value) {
        if (value.width() != profile.widthCells() || value.height() != profile.heightCells()) {
            throw new IllegalArgumentException("persisted ecology dimensions do not match Frontier profile");
        }
        ecology = value;
    }
    boolean reconcileOperation(FrontierOperation operation) {
        FrontierFacility facility = facility(operation.facilityId()).orElseThrow(() ->
                new IllegalStateException("operation references unknown facility " + operation.facilityId()));
        boolean staffed = alivePopulation(operation.settlementId()) > 0;
        return operation.reconcile(facility, staffed);
    }
    boolean reconcileHive(FrontierHive hive) {
        boolean coreAlive = hiveOrgans.values().stream().anyMatch(value -> value.hiveId().equals(hive.id())
                && value.kind() == FrontierHiveOrganKind.CORE && value.state() == FrontierHiveOrgan.State.ALIVE);
        boolean organAlive = hiveOrgans.values().stream().anyMatch(value -> value.hiveId().equals(hive.id())
                && value.state() == FrontierHiveOrgan.State.ALIVE);
        return hive.reconcile(coreAlive, organAlive);
    }
    /** Signal is derived from live organs and one connected tissue component, never a cached flag. */
    int hiveSignal(String hiveId, FrontierPoint position) {
        FrontierHive hive = hive(hiveId).orElseThrow(() -> new IllegalArgumentException("unknown hive " + hiveId));
        return hiveOrgans.values().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == FrontierHiveOrganKind.CORE)
                .filter(value -> value.state() == FrontierHiveOrgan.State.ALIVE)
                .mapToInt(core -> hiveSignalFromCore(hiveId, core, position)).max().orElse(0);
    }
    private int hiveSignalFromCore(String hiveId, FrontierHiveOrgan core, FrontierPoint position) {
        if (!connectedTissue(hiveId, core.position(), position)) return 0;
        List<FrontierHiveOrgan> signalOrgans = hiveOrgans.values().stream().filter(value -> value.hiveId().equals(hiveId))
                .filter(value -> value.state() == FrontierHiveOrgan.State.ALIVE)
                .filter(value -> value.kind() == FrontierHiveOrganKind.CORE || value.kind() == FrontierHiveOrganKind.SYNAPSE)
                .filter(value -> connectedTissue(hiveId, core.position(), value.position())).toList();
        if (signalOrgans.isEmpty()) return 0;
        // Reference calibration: every connected command source provides thirteen simulation cells;
        // distance then loses 75 / 1000 signal per cell.  It remains derived, never persisted.
        int radius = 13 * signalOrgans.size();
        int distance = signalOrgans.stream().mapToInt(value -> chebyshev(value.position(), position)).min().orElse(Integer.MAX_VALUE);
        int base = Math.max(0, Math.min(1_000, 1_000 - Math.max(0, distance - radius) * 75));
        return Math.min(1_000, base * adaptations.multiplier(FrontierAdaptation.SYNAPTIC_REDUNDANCY, 1_350) / 1_000);
    }
    boolean hiveCanAct(String hiveId, FrontierHiveOrganKind sourceKind) {
        return hiveOrgans.values().stream().filter(value -> value.hiveId().equals(hiveId))
                .filter(value -> value.kind() == sourceKind).filter(value -> value.state() == FrontierHiveOrgan.State.ALIVE)
                .anyMatch(source -> hiveSignal(hiveId, source.position()) >= COMMAND_SIGNAL_THRESHOLD);
    }
    int broodSignal(String hiveId) {
        return hiveOrgans.values().stream().filter(value -> value.hiveId().equals(hiveId))
                .filter(value -> value.kind() == FrontierHiveOrganKind.BROOD_SAC)
                .filter(value -> value.state() == FrontierHiveOrgan.State.ALIVE)
                .mapToInt(value -> hiveSignal(hiveId, value.position())).max().orElse(0);
    }
    void advanceHiveTissue(FrontierHive hive) {
        Map<FrontierPoint, Integer> cells = hiveTissue.get(hive.id());
        if (cells == null || cells.isEmpty()) return;
        if (hive.state() != FrontierHive.State.ACTIVE) {
            cells.replaceAll((position, strength) -> strength - 100);
            cells.entrySet().removeIf(value -> value.getValue() < FrontierHiveTissueCell.NETWORK_THRESHOLD);
            return;
        }
        List<FrontierPoint> candidates = new ArrayList<>();
        for (Map.Entry<FrontierPoint, Integer> entry : cells.entrySet()) {
            if (entry.getValue() < FrontierHiveTissueCell.NETWORK_THRESHOLD) continue;
            for (FrontierPoint point : neighbours(entry.getKey())) {
                if (point.x() >= 0 && point.z() >= 0 && point.x() < profile.widthCells() && point.z() < profile.heightCells()
                        && !cells.containsKey(point)) candidates.add(point);
            }
        }
        FrontierPoint target = candidates.stream().distinct().max(java.util.Comparator
                .comparingLong((FrontierPoint point) -> ecology.cell(point.x(), point.z()).organicMass() - ecology.cell(point.x(), point.z()).scar())
                .thenComparingInt(FrontierPoint::z).thenComparingInt(FrontierPoint::x)).orElse(null);
        if (target != null && hive.spendBiomass(2)) {
            ecology.consume(target.x(), target.z(), 10);
            cells.put(target, 500);
        }
    }
    private boolean connectedTissue(String hiveId, FrontierPoint start, FrontierPoint target) {
        Map<FrontierPoint, Integer> cells = hiveTissue.get(hiveId);
        if (cells == null || cells.getOrDefault(start, 0) < FrontierHiveTissueCell.NETWORK_THRESHOLD
                || cells.getOrDefault(target, 0) < FrontierHiveTissueCell.NETWORK_THRESHOLD) return false;
        ArrayDeque<FrontierPoint> queue = new ArrayDeque<>();
        LinkedHashSet<FrontierPoint> visited = new LinkedHashSet<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            FrontierPoint current = queue.removeFirst();
            if (current.equals(target)) return true;
            for (FrontierPoint next : neighbours(current)) {
                if (cells.getOrDefault(next, 0) >= FrontierHiveTissueCell.NETWORK_THRESHOLD && visited.add(next)) queue.addLast(next);
            }
        }
        return false;
    }
    private static List<FrontierPoint> neighbours(FrontierPoint point) {
        return List.of(new FrontierPoint(point.x() - 1, point.z()), new FrontierPoint(point.x() + 1, point.z()),
                new FrontierPoint(point.x(), point.z() - 1), new FrontierPoint(point.x(), point.z() + 1));
    }
    private static int chebyshev(FrontierPoint left, FrontierPoint right) {
        return Math.max(Math.abs(left.x() - right.x()), Math.abs(left.z() - right.z()));
    }
    boolean inBounds(FrontierPoint point) {
        return point.x() >= 0 && point.z() >= 0 && point.x() < profile.widthCells() && point.z() < profile.heightCells();
    }
    Optional<FrontierPoint> morphogenesisTarget(String hiveId, FrontierHiveOrganKind kind) {
        FrontierHive hive = hives.get(hiveId);
        if (hive == null) return Optional.empty();
        int requiredStrength = FrontierMorphogenesisProject.specification(kind).requiredTissueStrength();
        return hiveTissue.getOrDefault(hiveId, Map.of()).entrySet().stream()
                .filter(value -> value.getValue() >= requiredStrength)
                .map(Map.Entry::getKey).filter(position -> chebyshev(hive.anchor(), position) >= 1)
                .filter(position -> hiveOrgans.values().stream().noneMatch(organ -> organ.hiveId().equals(hiveId)
                        && organ.position().equals(position)))
                .max(java.util.Comparator.comparingLong((FrontierPoint point) -> ecology.cell(point.x(), point.z()).organicMass()
                        - ecology.cell(point.x(), point.z()).scar()).thenComparingInt(FrontierPoint::z)
                        .thenComparingInt(FrontierPoint::x));
    }
    /** Reference-calibrated frontier forage: rich living cover outside the hive's mature tissue. */
    Optional<FrontierPoint> harvesterTarget(String hiveId, FrontierHiveOrgan source) {
        if (!source.hiveId().equals(hiveId) || source.kind() != FrontierHiveOrganKind.BROOD_SAC) return Optional.empty();
        return ecology.cells().stream().map(cell -> new FrontierPoint(cell.x(), cell.z()))
                .filter(this::inBounds).filter(point -> {
                    int distance = chebyshev(source.position(), point);
                    return distance >= 2 && distance <= 12 && ecology.cell(point.x(), point.z()).organicMass() >= 52
                            && harvesterTargetScore(hiveId, source, point) >= HARVESTER_MINIMUM_TARGET_SCORE;
                })
                .max(java.util.Comparator.comparingLong((FrontierPoint point) -> harvesterTargetScore(hiveId, source, point))
                        .thenComparingInt(FrontierPoint::z).thenComparingInt(FrontierPoint::x));
    }
    boolean canMatureLatentColony(FrontierLatentColony colony) {
        return colony.propagules() >= 550 && colony.strength() >= 340 && hiveOrgans.values().stream()
                .noneMatch(organ -> chebyshev(organ.position(), colony.position()) < 20)
                && !hiveTissue.getOrDefault(colony.hiveId(), Map.of()).containsKey(colony.position())
                && hiveOrgans.values().stream().filter(organ -> organ.hiveId().equals(colony.hiveId())).count() < MAX_ORGANS_PER_HIVE;
    }
    private long harvesterTargetScore(String hiveId, FrontierHiveOrgan source, FrontierPoint point) {
        long organic = ecology.cell(point.x(), point.z()).organicMass();
        long tissue = hiveTissue.getOrDefault(hiveId, Map.of()).getOrDefault(point, 0);
        // `organic * (1 - infection * .82) - distance * 2.2`, represented ×100
        // to preserve the reference's fractional distance cost without floating state.
        return organic * (1_000L - Math.min(1_000L, tissue) * 82L / 100L) / 10L
                - chebyshev(source.position(), point) * 220L;
    }
    Optional<FrontierHiveOrgan> harvesterReceiver(FrontierHarvesterRun run) {
        FrontierHiveOrgan source = hiveOrgans.get(run.sourceOrganId());
        if (source == null || source.state() != FrontierHiveOrgan.State.ALIVE) return Optional.empty();
        return hiveOrgans.values().stream().filter(value -> value.hiveId().equals(run.hiveId()))
                .filter(value -> value.state() == FrontierHiveOrgan.State.ALIVE)
                .filter(value -> value.kind() == FrontierHiveOrganKind.CORE || value.kind() == FrontierHiveOrganKind.DIGESTIVE_POOL)
                .filter(value -> connectedTissue(run.hiveId(), source.position(), value.position()))
                .min(java.util.Comparator.comparingInt((FrontierHiveOrgan value) -> chebyshev(run.position(), value.position()))
                        .thenComparing(FrontierHiveOrgan::id));
    }
    boolean harvesterPayloadIsUsable(FrontierEcology.Harvest harvest) {
        return harvest.biomass() >= HARVESTER_MINIMUM_LOAD;
    }
    long aliveBioformCount(String hiveId) {
        return bioforms.values().stream().filter(value -> value.hiveId().equals(hiveId) && value.alive()).count();
    }
    int nextBioformOrdinal(String hiveId, long bornDay) {
        return Math.toIntExact(bioforms.values().stream().filter(value -> value.hiveId().equals(hiveId)
                && value.bornDay() == bornDay).count() + 1);
    }
    List<FrontierResident> killResidents(String settlementId, int maximum) {
        if (maximum < 0) throw new IllegalArgumentException("maximum must not be negative");
        List<FrontierResident> victims = settlements.get(settlementId).residentIds().stream().map(residents::get)
                .filter(FrontierResident::alive)
                .sorted(java.util.Comparator.comparing((FrontierResident value) -> value.role() == FrontierResidentRole.CIVILIAN ? 0 : 1)
                        .thenComparing(FrontierResident::id))
                .limit(maximum).toList();
        victims.forEach(FrontierResident::kill);
        return victims;
    }
    FrontierFacility damageFirstOperationalFacility(String settlementId) {
        return settlements.get(settlementId).facilityIds().stream().map(facilities::get)
                .filter(value -> value.state() == FrontierFacility.State.OPERATIONAL)
                .sorted(java.util.Comparator.comparing(FrontierFacility::id)).findFirst().orElse(null);
    }
    FrontierEvent event(FrontierEvent.Type type, String subject, String causation) {
        FrontierEvent event = new FrontierEvent(++sequence, day, type, subject, causation);
        events.add(event);
        if (events.size() > MAX_EVENTS) events.removeFirst();
        return event;
    }
    boolean seenObservation(String id) { return processedObservations.contains(id); }
    void recordObservation(String id) {
        processedObservations.add(id);
        if (processedObservations.size() > MAX_OBSERVATIONS) processedObservations.removeFirst();
    }
}
