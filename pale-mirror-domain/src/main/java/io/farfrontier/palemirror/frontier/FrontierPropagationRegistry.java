package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Owns bounded carrier and deposited-colony identities; WorldState remains the aggregate authority. */
final class FrontierPropagationRegistry {
    private static final int COMMAND_SIGNAL_THRESHOLD = 400;
    private static final long LAUNCH_BIOMASS = 30;
    private static final long INITIAL_PROPAGULES = 800;
    private static final long INITIAL_STRENGTH = 460;
    private final Map<String, FrontierPropagationRun> runs = new LinkedHashMap<>();
    private final Map<String, FrontierLatentColony> colonies = new LinkedHashMap<>();

    Collection<FrontierPropagationRun> runs() { return List.copyOf(runs.values()); }
    Collection<FrontierLatentColony> colonies() { return List.copyOf(colonies.values()); }
    Optional<FrontierPropagationRun> run(String id) { return Optional.ofNullable(runs.get(id)); }
    Optional<FrontierLatentColony> colony(String id) { return Optional.ofNullable(colonies.get(id)); }
    boolean assigned(String bioformId) { return runs.values().stream().anyMatch(value -> !value.terminal() && value.bioformId().equals(bioformId)); }

    Optional<FrontierPropagationRun> start(FrontierWorldState state, String hiveId, String sourceOrganId, String bioformId,
                                           FrontierPoint target) {
        FrontierHive hive = state.hive(hiveId).orElse(null);
        FrontierHiveOrgan source = state.hiveOrgan(sourceOrganId).orElse(null);
        FrontierBioform bioform = state.bioform(bioformId).orElse(null);
        if (hive == null || hive.state() != FrontierHive.State.ACTIVE || source == null || !source.hiveId().equals(hiveId)
                || source.kind() != FrontierHiveOrganKind.SPORULATOR || source.state() != FrontierHiveOrgan.State.ALIVE
                || bioform == null || !bioform.hiveId().equals(hiveId) || bioform.kind() != FrontierBioformKind.PROPAGULE_CARRIER
                || !bioform.alive() || assigned(bioformId) || !targetAllowed(state, hiveId, source, target)
                || state.hiveSignal(hiveId, source.position()) < COMMAND_SIGNAL_THRESHOLD
                || runs.values().stream().anyMatch(value -> !value.terminal() && value.hiveId().equals(hiveId))) return Optional.empty();
        String id = FrontierPropagationRun.idFor(hiveId, bioformId, state.day());
        if (runs.containsKey(id) || !hive.spendBiomass(LAUNCH_BIOMASS)) return Optional.empty();
        FrontierPropagationRun run = new FrontierPropagationRun(id, hiveId, sourceOrganId, bioformId, source.position(), target, state.day(),
                FrontierPropagationRun.travelDays(source.position(), target));
        runs.put(id, run);
        return Optional.of(run);
    }
    Optional<FrontierPoint> targetFor(FrontierWorldState state, String hiveId, FrontierHiveOrgan source) {
        if (!source.hiveId().equals(hiveId) || source.kind() != FrontierHiveOrganKind.SPORULATOR) return Optional.empty();
        return state.ecology().cells().stream().map(cell -> new FrontierPoint(cell.x(), cell.z()))
                .filter(point -> targetAllowed(state, hiveId, source, point)).max(Comparator
                        .comparingLong((FrontierPoint point) -> targetScore(state, source, point))
                        .thenComparingInt(FrontierPoint::z).thenComparingInt(FrontierPoint::x));
    }
    void putRun(FrontierWorldState state, FrontierPropagationRun run) {
        FrontierHive hive = state.hive(run.hiveId()).orElse(null);
        FrontierHiveOrgan source = state.hiveOrgan(run.sourceOrganId()).orElse(null);
        FrontierBioform bioform = state.bioform(run.bioformId()).orElse(null);
        boolean active = run.state() == FrontierPropagationRun.State.OUTBOUND;
        if (hive == null || source == null || source.kind() != FrontierHiveOrganKind.SPORULATOR || !source.hiveId().equals(run.hiveId())
                || bioform == null || bioform.kind() != FrontierBioformKind.PROPAGULE_CARRIER || !bioform.hiveId().equals(run.hiveId())
                || !source.position().equals(run.origin()) || !state.inBounds(run.origin()) || !state.inBounds(run.target())
                || !state.inBounds(run.position()) || run.startedDay() > state.day()
                || !FrontierPropagationRun.idFor(run.hiveId(), run.bioformId(), run.startedDay()).equals(run.id())
                || (active && (hive.state() != FrontierHive.State.ACTIVE || source.state() != FrontierHiveOrgan.State.ALIVE
                || !bioform.alive() || state.hiveSignal(run.hiveId(), source.position()) < COMMAND_SIGNAL_THRESHOLD
                || runs.values().stream().anyMatch(value -> !value.terminal()
                && (value.hiveId().equals(run.hiveId()) || value.bioformId().equals(run.bioformId())))))
                || runs.putIfAbsent(run.id(), run) != null) throw new IllegalArgumentException("invalid propagation " + run.id());
    }
    void putColony(FrontierWorldState state, FrontierLatentColony colony) {
        FrontierHiveOrgan source = state.hiveOrgan(colony.sourceOrganId()).orElse(null);
        if (!state.hive(colony.hiveId()).isPresent() || source == null || !source.hiveId().equals(colony.hiveId())
                || source.kind() != FrontierHiveOrganKind.SPORULATOR || colony.cleared() || !state.inBounds(colony.position())
                || colonies.putIfAbsent(colony.id(), colony) != null) {
            throw new IllegalArgumentException("invalid latent colony " + colony.id());
        }
    }
    List<FrontierPropagationRun> abortForHive(FrontierWorldState state, String hiveId) { return abort(state, runs.values().stream()
            .filter(value -> value.hiveId().equals(hiveId)).toList()); }
    List<FrontierPropagationRun> abortForSource(FrontierWorldState state, String sourceId) { return abort(state, runs.values().stream()
            .filter(value -> value.sourceOrganId().equals(sourceId)).toList()); }
    List<FrontierPropagationRun> abortForBioform(FrontierWorldState state, String bioformId) { return abort(state, runs.values().stream()
            .filter(value -> value.bioformId().equals(bioformId)).toList()); }
    private static List<FrontierPropagationRun> abort(FrontierWorldState state, List<FrontierPropagationRun> candidates) {
        List<FrontierPropagationRun> aborted = new ArrayList<>();
        for (FrontierPropagationRun run : candidates) if (run.abort(state.day())) aborted.add(run);
        return List.copyOf(aborted);
    }
    Optional<FrontierLatentColony> advance(FrontierWorldState state, FrontierPropagationRun run) {
        if (run.state() != FrontierPropagationRun.State.OUTBOUND) return Optional.empty();
        String colonyId = FrontierLatentColony.idFor(run.hiveId(), run.id(), run.target(), state.day());
        boolean moved = run.advance(state.day(), colonyId);
        if (!moved || run.state() != FrontierPropagationRun.State.DEPLOYED) return Optional.empty();
        FrontierLatentColony colony = new FrontierLatentColony(colonyId, run.hiveId(), run.sourceOrganId(), run.target(), state.day(),
                INITIAL_PROPAGULES, INITIAL_STRENGTH);
        putColony(state, colony);
        state.bioform(run.bioformId()).orElseThrow().kill(state.day());
        return Optional.of(colony);
    }
    List<FrontierLatentColony> matureOrDecay(FrontierWorldState state) {
        List<FrontierLatentColony> mature = new ArrayList<>();
        for (FrontierLatentColony colony : colonies.values().stream().sorted(Comparator.comparing(FrontierLatentColony::id)).toList()) {
            if (colony.depositedDay() >= state.day()) continue;
            if (!colony.decay()) { colonies.remove(colony.id()); continue; }
            if (colony.mature() && state.canMatureLatentColony(colony)) mature.add(colony);
        }
        return List.copyOf(mature);
    }
    boolean clear(String colonyId) { return Optional.ofNullable(colonies.get(colonyId)).map(FrontierLatentColony::clear).orElse(false); }
    void removeColony(String colonyId) { colonies.remove(colonyId); }
    void compactRuns(long day, long retentionDays) {
        runs.values().stream().filter(FrontierPropagationRun::terminal).filter(value -> day - value.finishedDay() > retentionDays)
                .map(FrontierPropagationRun::id).toList().forEach(runs::remove);
    }
    private static boolean targetAllowed(FrontierWorldState state, String hiveId, FrontierHiveOrgan source, FrontierPoint point) {
        if (!source.hiveId().equals(hiveId) || !state.inBounds(point) || point.x() == 0 || point.z() == 0
                || point.x() == state.profile().widthCells() - 1 || point.z() == state.profile().heightCells() - 1) return false;
        return distance(source.position(), point) >= 20 && state.hiveOrgans().stream().noneMatch(organ -> distance(organ.position(), point) < 20);
    }
    private static long targetScore(FrontierWorldState state, FrontierHiveOrgan source, FrontierPoint point) {
        FrontierEcology.Cell cell = state.ecology().cell(point.x(), point.z());
        long nearestSettlement = state.settlements().stream().filter(value -> state.alivePopulation(value.id()) > 0)
                .mapToLong(value -> distance(point, value.center())).min().orElse(20);
        return cell.organicMass() * cell.moisture() / 1_000 - distance(source.position(), point) * 35 / 100
                + Math.min(8, nearestSettlement) * 45 / 100;
    }
    private static int distance(FrontierPoint left, FrontierPoint right) {
        return Math.max(Math.abs(left.x() - right.x()), Math.abs(left.z() - right.z()));
    }
}
