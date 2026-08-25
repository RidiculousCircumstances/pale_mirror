package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded owner for return-trip identities. FrontierWorldState remains the aggregate authority;
 * this registry keeps lifecycle validation and terminal cleanup together rather than growing that
 * aggregate into a second hidden harvester simulation.
 */
final class FrontierHarvesterRegistry {
    private static final int COMMAND_SIGNAL_THRESHOLD = 320;
    private static final int MAX_ACTIVE_PER_HIVE = 1;
    private static final long DISPATCH_BIOMASS_CEILING = 80;
    private static final long LAUNCH_BIOMASS = 18;
    private final Map<String, FrontierHarvesterRun> runs = new LinkedHashMap<>();

    Collection<FrontierHarvesterRun> values() { return List.copyOf(runs.values()); }
    Optional<FrontierHarvesterRun> find(String id) { return Optional.ofNullable(runs.get(id)); }

    void put(FrontierWorldState state, FrontierHarvesterRun value) {
        FrontierHive hive = state.hive(value.hiveId()).orElse(null);
        FrontierHiveOrgan source = state.hiveOrgan(value.sourceOrganId()).orElse(null);
        FrontierBioform bioform = state.bioform(value.bioformId()).orElse(null);
        if (hive == null || source == null || bioform == null || !source.hiveId().equals(hive.id())
                || source.kind() != FrontierHiveOrganKind.BROOD_SAC || !bioform.hiveId().equals(hive.id())
                || bioform.kind() != FrontierBioformKind.HARVESTER || !state.inBounds(value.origin())
                || !source.position().equals(value.origin()) || !state.inBounds(value.foragePosition())
                || !state.inBounds(value.position())
                || !FrontierHarvesterRun.idFor(value.hiveId(), value.bioformId(), value.startedDay()).equals(value.id())
                || value.startedDay() > state.day()) throw new IllegalArgumentException("invalid harvester run " + value.id());
        if (!value.terminal() && (hive.state() != FrontierHive.State.ACTIVE || source.state() != FrontierHiveOrgan.State.ALIVE
                || !bioform.alive() || state.hiveSignal(hive.id(), source.position()) < COMMAND_SIGNAL_THRESHOLD
                || activeForHive(hive.id()) || state.bioformAssignedToAssault(bioform.id()))) {
            throw new IllegalArgumentException("invalid active harvester run " + value.id());
        }
        if (runs.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate harvester run " + value.id());
    }

    void remove(String id) { runs.remove(id); }

    List<FrontierHarvesterRun> abortForHive(FrontierWorldState state, String hiveId) {
        return abort(state, runs.values().stream().filter(value -> value.hiveId().equals(hiveId)).toList());
    }
    List<FrontierHarvesterRun> abortForSource(FrontierWorldState state, String sourceOrganId) {
        return abort(state, runs.values().stream().filter(value -> value.sourceOrganId().equals(sourceOrganId)).toList());
    }
    List<FrontierHarvesterRun> abortForReceiver(FrontierWorldState state, String receiverOrganId) {
        return abort(state, runs.values().stream().filter(value -> receiverOrganId.equals(value.receiverOrganId())).toList());
    }
    List<FrontierHarvesterRun> abortForBioform(FrontierWorldState state, String bioformId) {
        return abort(state, runs.values().stream().filter(value -> value.bioformId().equals(bioformId)).toList());
    }
    private static List<FrontierHarvesterRun> abort(FrontierWorldState state, List<FrontierHarvesterRun> candidates) {
        List<FrontierHarvesterRun> aborted = new ArrayList<>();
        for (FrontierHarvesterRun run : candidates) {
            long cargo = run.cargo();
            FrontierPoint position = run.position();
            if (run.abort(state.day())) {
                if (cargo > 0) state.ecology().addDetritus(position.x(), position.z(), cargo);
                aborted.add(run);
            }
        }
        return List.copyOf(aborted);
    }

    void compactTerminal(long currentDay, long retentionDays) {
        if (retentionDays < 0) throw new IllegalArgumentException("retentionDays must not be negative");
        runs.values().stream().filter(FrontierHarvesterRun::terminal).filter(value -> currentDay - value.finishedDay() > retentionDays)
                .map(FrontierHarvesterRun::id).toList().forEach(runs::remove);
    }
    boolean assigned(String bioformId) {
        return runs.values().stream().anyMatch(value -> !value.terminal() && value.bioformId().equals(bioformId));
    }

    Optional<FrontierHarvesterRun> start(FrontierWorldState state, String hiveId, String sourceOrganId, String bioformId,
                                         FrontierPoint foragePosition) {
        FrontierHive hive = state.hive(hiveId).orElse(null);
        FrontierHiveOrgan source = state.hiveOrgan(sourceOrganId).orElse(null);
        FrontierBioform bioform = state.bioform(bioformId).orElse(null);
        if (hive == null || hive.state() != FrontierHive.State.ACTIVE || source == null || !source.hiveId().equals(hiveId)
                || source.kind() != FrontierHiveOrganKind.BROOD_SAC || source.state() != FrontierHiveOrgan.State.ALIVE
                || bioform == null || !bioform.hiveId().equals(hiveId) || bioform.kind() != FrontierBioformKind.HARVESTER
                || !bioform.alive() || assigned(bioform.id()) || !state.inBounds(foragePosition)
                || hive.biomass() > DISPATCH_BIOMASS_CEILING
                || state.hiveSignal(hiveId, source.position()) < COMMAND_SIGNAL_THRESHOLD
                || activeForHive(hiveId) || state.bioformAssignedToAssault(bioform.id())) return Optional.empty();
        String id = FrontierHarvesterRun.idFor(hiveId, bioformId, state.day());
        if (runs.containsKey(id) || !hive.spendBiomass(LAUNCH_BIOMASS)) return Optional.empty();
        FrontierHarvesterRun run = new FrontierHarvesterRun(id, hiveId, sourceOrganId, bioformId, source.position(),
                foragePosition, state.day(), FrontierHarvesterRun.travelDays(source.position(), foragePosition));
        put(state, run);
        return Optional.of(run);
    }

    private boolean activeForHive(String hiveId) {
        return runs.values().stream().filter(value -> !value.terminal() && value.hiveId().equals(hiveId)).count() >= MAX_ACTIVE_PER_HIVE;
    }
}
