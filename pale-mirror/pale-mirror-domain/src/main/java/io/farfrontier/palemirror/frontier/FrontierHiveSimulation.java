package io.farfrontier.palemirror.frontier;

import java.util.Comparator;
import java.util.List;

/** Deterministic bounded hive metabolism and brood production sourced only from local ecology. */
final class FrontierHiveSimulation {
    private static final long DAILY_DIGESTIVE_CONSUMPTION = 40;
    private static final long DAILY_SURVIVAL_BIOMASS = 1;
    private static final long DAILY_MAINTENANCE = 1;
    private static final long BIOFORM_COST = 24;
    private static final long MAX_ALIVE_BIOFORMS = 12;
    private static final int MORPHOGENESIS_CADENCE_DAYS = 6;
    private static final int HARVESTER_CADENCE_DAYS = 6;
    private static final int PROPAGATION_CADENCE_DAYS = 6;
    private static final int TERMINAL_HARVESTER_RETENTION_DAYS = 7;
    private static final int TERMINAL_PROPAGATION_RETENTION_DAYS = 7;
    private static final long RETURN_ASSIMILATION_PERCENT = 68;

    void advance(FrontierWorldState state, String causationId, List<FrontierEvent> events) {
        state.adaptationLedger().advance(state, causationId, events);
        advanceLatentColonies(state, causationId, events);
        for (FrontierHive hive : state.hives().stream().sorted(Comparator.comparing(FrontierHive::id)).toList()) {
            if (state.reconcileHive(hive)) {
                events.add(state.event(FrontierEvent.Type.HIVE_STATE_CHANGED, hive.id(), causationId));
                if (hive.state() != FrontierHive.State.ACTIVE) {
                    for (FrontierHarvesterRun run : state.harvesters().abortForHive(state, hive.id())) {
                        events.add(state.event(FrontierEvent.Type.HARVESTER_ABORTED, run.id(), causationId));
                    }
                    for (FrontierPropagationRun run : state.propagations().abortForHive(state, hive.id())) {
                        events.add(state.event(FrontierEvent.Type.PROPAGATION_RUN_ABORTED, run.id(), causationId));
                    }
                }
            }
            state.advanceHiveTissue(hive);
            if (hive.state() != FrontierHive.State.ACTIVE) continue;
            advanceMorphogenesis(state, hive, causationId, events);
            List<FrontierHiveOrgan> digestive = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id())
                    && value.kind() == FrontierHiveOrganKind.DIGESTIVE_POOL && value.state() == FrontierHiveOrgan.State.ALIVE)
                    .sorted(Comparator.comparing(FrontierHiveOrgan::id)).toList();
            long growth = digestive.isEmpty() ? DAILY_SURVIVAL_BIOMASS : 0;
            for (FrontierHiveOrgan organ : digestive) {
                long localOrganic = state.ecology().cell(organ.position().x(), organ.position().z()).organicMass();
                long harvested = state.ecology().consume(organ.position().x(), organ.position().z(), DAILY_DIGESTIVE_CONSUMPTION);
                // A digestive pool can only turn living cover into biomass while it is actually
                // there.  The capped yield avoids a high-resource cell creating unbounded forms.
                growth += harvested == 0 ? 0 : Math.min(4, Math.max(1, localOrganic / 350));
            }
            if (!digestive.isEmpty() && growth == 0) state.adaptationLedger().record(FrontierDamageKind.STARVATION);
            if (growth > 0) {
                hive.addBiomass(growth);
                events.add(state.event(FrontierEvent.Type.HIVE_BIOMASS_GROWN, hive.id(), causationId));
            }
            hive.spendBiomass(Math.min(DAILY_MAINTENANCE, hive.biomass()));
            advanceHarvesters(state, hive, causationId, events);
            advancePropagationRuns(state, hive, causationId, events);
            planHarvester(state, hive, causationId, events);
            planPropagation(state, hive, causationId, events);
            planMorphogenesis(state, hive, causationId, events);
            List<FrontierHiveOrgan> broods = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id())
                    && value.kind() == FrontierHiveOrganKind.BROOD_SAC && value.state() == FrontierHiveOrgan.State.ALIVE)
                    .sorted(Comparator.comparing(FrontierHiveOrgan::id)).toList();
            for (FrontierHiveOrgan brood : broods) {
                if (state.day() % 3 != 0 || state.aliveBioformCount(hive.id()) >= MAX_ALIVE_BIOFORMS
                        || state.hiveSignal(hive.id(), brood.position()) < 320 || !hive.spendBiomass(BIOFORM_COST)) continue;
                int ordinal = state.nextBioformOrdinal(hive.id(), state.day());
                FrontierBioformKind[] kinds = FrontierBioformKind.values();
                FrontierBioformKind kind = kinds[Math.floorMod((int) state.day() + ordinal, kinds.length)];
                FrontierBioform bioform = new FrontierBioform(FrontierBioform.idFor(hive.id(), state.day(), ordinal), hive.id(),
                        kind, state.day(), ordinal);
                state.putBioform(bioform);
                events.add(state.event(FrontierEvent.Type.BIOFORM_SPAWNED, bioform.id(), causationId));
            }
        }
        state.harvesters().compactTerminal(state.day(), TERMINAL_HARVESTER_RETENTION_DAYS);
        state.propagations().compactRuns(state.day(), TERMINAL_PROPAGATION_RETENTION_DAYS);
    }

    private static void advanceHarvesters(FrontierWorldState state, FrontierHive hive, String causationId,
                                          List<FrontierEvent> events) {
        for (FrontierHarvesterRun run : state.harvesterRuns().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> !value.terminal()).sorted(Comparator.comparing(FrontierHarvesterRun::id)).toList()) {
            FrontierHiveOrgan source = state.hiveOrgan(run.sourceOrganId()).orElse(null);
            FrontierBioform bioform = state.bioform(run.bioformId()).orElse(null);
            if (source == null || source.state() != FrontierHiveOrgan.State.ALIVE || bioform == null || !bioform.alive()
                    || hive.state() != FrontierHive.State.ACTIVE || state.hiveSignal(hive.id(), source.position()) < 320) {
                state.harvesters().abortForBioform(state, run.bioformId()).forEach(value ->
                        events.add(state.event(FrontierEvent.Type.HARVESTER_ABORTED, value.id(), causationId)));
                continue;
            }
            switch (run.state()) {
                case OUTBOUND -> {
                    if (run.advanceOutbound()) events.add(state.event(FrontierEvent.Type.HARVESTER_STATE_CHANGED, run.id(), causationId));
                }
                case FORAGING -> {
                    long demand = 32L * state.adaptationLedger().multiplier(FrontierAdaptation.RAPID_DIGESTION, 1_300) / 1_000;
                    FrontierEcology.Harvest harvest = state.ecology().harvest(run.foragePosition().x(), run.foragePosition().z(), demand);
                    if (!state.harvesterPayloadIsUsable(harvest)) {
                        run.abort(state.day());
                        events.add(state.event(FrontierEvent.Type.HARVESTER_ABORTED, run.id(), causationId));
                        continue;
                    }
                    FrontierHiveOrgan receiver = state.harvesterReceiver(run).orElse(null);
                    if (receiver == null) {
                        // The animal has already taken the mass.  If command/receiver fails before
                        // it can leave, that mass stays at the visible forage location as detritus.
                        state.ecology().addDetritus(run.position().x(), run.position().z(), harvest.biomass());
                        run.abort(state.day());
                        events.add(state.event(FrontierEvent.Type.HARVESTER_ABORTED, run.id(), causationId));
                        continue;
                    }
                    run.beginReturn(receiver.id(), receiver.position(), harvest);
                    events.add(state.event(FrontierEvent.Type.HARVESTER_STATE_CHANGED, run.id(), causationId));
                }
                case RETURNING -> {
                    FrontierHiveOrgan receiver = state.harvesterReceiver(run)
                            .filter(value -> value.id().equals(run.receiverOrganId())).orElse(null);
                    if (receiver == null) {
                        state.harvesters().abortForBioform(state, run.bioformId()).forEach(value ->
                                events.add(state.event(FrontierEvent.Type.HARVESTER_ABORTED, value.id(), causationId)));
                        continue;
                    }
                    if (run.advanceReturn(receiver.position(), state.day())) {
                        if (run.state() == FrontierHarvesterRun.State.COMPLETED) {
                            long efficiency = RETURN_ASSIMILATION_PERCENT
                                    * state.adaptationLedger().multiplier(FrontierAdaptation.RAPID_DIGESTION, 1_380) / 1_000;
                            hive.addBiomass(run.cargo() * efficiency / 100);
                            hive.addGeneticMaterial(run.geneticCargo());
                            events.add(state.event(FrontierEvent.Type.HARVESTER_RETURNED, run.id(), causationId));
                        } else {
                            events.add(state.event(FrontierEvent.Type.HARVESTER_STATE_CHANGED, run.id(), causationId));
                        }
                    }
                }
                case COMPLETED, ABORTED -> { }
            }
        }
    }

    private static void advancePropagationRuns(FrontierWorldState state, FrontierHive hive, String causationId,
                                         List<FrontierEvent> events) {
        for (FrontierPropagationRun run : state.propagationRuns().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> !value.terminal()).sorted(Comparator.comparing(FrontierPropagationRun::id)).toList()) {
            FrontierHiveOrgan source = state.hiveOrgan(run.sourceOrganId()).orElse(null);
            FrontierBioform carrier = state.bioform(run.bioformId()).orElse(null);
            if (source == null || source.state() != FrontierHiveOrgan.State.ALIVE || carrier == null || !carrier.alive()
                    || hive.state() != FrontierHive.State.ACTIVE || state.hiveSignal(hive.id(), source.position()) < 400) {
                state.propagations().abortForBioform(state, run.bioformId()).forEach(value ->
                        events.add(state.event(FrontierEvent.Type.PROPAGATION_RUN_ABORTED, value.id(), causationId)));
                continue;
            }
            state.propagations().advance(state, run).ifPresent(colony ->
                    events.add(state.event(FrontierEvent.Type.PROPAGATION_RUN_DEPLOYED, colony.id(), causationId)));
            if (!run.terminal()) events.add(state.event(FrontierEvent.Type.PROPAGATION_RUN_STATE_CHANGED, run.id(), causationId));
        }
    }

    private static void planHarvester(FrontierWorldState state, FrontierHive hive, String causationId,
                                      List<FrontierEvent> events) {
        if (state.day() % HARVESTER_CADENCE_DAYS != 0 || hive.biomass() > 80
                || state.harvesterRuns().stream().anyMatch(value -> !value.terminal() && value.hiveId().equals(hive.id()))) return;
        FrontierHiveOrgan brood = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == FrontierHiveOrganKind.BROOD_SAC && value.state() == FrontierHiveOrgan.State.ALIVE)
                .filter(value -> state.hiveSignal(hive.id(), value.position()) >= 320)
                .sorted(Comparator.comparing(FrontierHiveOrgan::id)).findFirst().orElse(null);
        if (brood == null) return;
        FrontierBioform harvester = state.bioforms().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == FrontierBioformKind.HARVESTER && value.alive())
                .filter(value -> !state.harvesters().assigned(value.id()) && !state.propagations().assigned(value.id())
                        && !state.bioformAssignedToAssault(value.id()))
                .sorted(Comparator.comparing(FrontierBioform::id)).findFirst().orElse(null);
        if (harvester == null) return;
        state.harvesterTarget(hive.id(), brood).flatMap(target -> state.harvesters().start(state, hive.id(), brood.id(), harvester.id(), target))
                .ifPresent(run -> events.add(state.event(FrontierEvent.Type.HARVESTER_LAUNCHED, run.id(), causationId)));
    }

    private static void planPropagation(FrontierWorldState state, FrontierHive hive, String causationId, List<FrontierEvent> events) {
        if (state.day() % PROPAGATION_CADENCE_DAYS != 0 || state.propagationRuns().stream()
                .anyMatch(value -> !value.terminal() && value.hiveId().equals(hive.id()))) return;
        FrontierHiveOrgan sporulator = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == FrontierHiveOrganKind.SPORULATOR && value.state() == FrontierHiveOrgan.State.ALIVE)
                .filter(value -> state.hiveSignal(hive.id(), value.position()) >= 400)
                .sorted(Comparator.comparing(FrontierHiveOrgan::id)).findFirst().orElse(null);
        if (sporulator == null) return;
        FrontierBioform carrier = state.bioforms().stream().filter(value -> value.hiveId().equals(hive.id()) && value.alive())
                .filter(value -> value.kind() == FrontierBioformKind.PROPAGULE_CARRIER).filter(value -> !state.propagations().assigned(value.id()))
                .filter(value -> !state.harvesters().assigned(value.id()) && !state.bioformAssignedToAssault(value.id()))
                .sorted(Comparator.comparing(FrontierBioform::id)).findFirst().orElse(null);
        if (carrier == null) return;
        state.propagations().targetFor(state, hive.id(), sporulator).flatMap(target -> state.propagations().start(state, hive.id(),
                sporulator.id(), carrier.id(), target)).ifPresent(run ->
                events.add(state.event(FrontierEvent.Type.PROPAGATION_RUN_LAUNCHED, run.id(), causationId)));
    }

    private static void advanceLatentColonies(FrontierWorldState state, String causationId, List<FrontierEvent> events) {
        for (FrontierLatentColony colony : state.propagations().matureOrDecay(state)) {
            String organId = FrontierHiveOrgan.dynamicIdFor(colony.hiveId(), FrontierHiveOrganKind.CORE, colony.position(), state.day());
            state.putHiveTissue(new FrontierHiveTissueCell(colony.hiveId(), colony.position(), FrontierHiveTissueCell.MAX_STRENGTH));
            state.putHiveOrgan(new FrontierHiveOrgan(organId, colony.hiveId(), FrontierHiveOrganKind.CORE, colony.position(), state.day()));
            state.propagations().removeColony(colony.id());
            events.add(state.event(FrontierEvent.Type.LATENT_COLONY_MATURED, colony.id(), causationId));
        }
    }

    private static void advanceMorphogenesis(FrontierWorldState state, FrontierHive hive, String causationId,
                                             List<FrontierEvent> events) {
        for (FrontierMorphogenesisProject project : state.morphogenesisProjects().stream()
                .filter(value -> value.hiveId().equals(hive.id())).sorted(Comparator.comparing(FrontierMorphogenesisProject::id)).toList()) {
            FrontierHiveOrgan source = state.hiveOrgan(project.sourceOrganId()).orElse(null);
            if (source == null || source.state() != FrontierHiveOrgan.State.ALIVE || hive.state() != FrontierHive.State.ACTIVE
                    || state.hiveSignal(hive.id(), source.position()) < 320) {
                state.removeMorphogenesisProject(project.id());
                events.add(state.event(FrontierEvent.Type.MORPHOGENESIS_ABORTED, project.id(), causationId));
                continue;
            }
            if (!project.advanceDay()) continue;
            int tissue = state.hiveTissue().stream().filter(value -> value.hiveId().equals(hive.id()))
                    .filter(value -> value.position().equals(project.position())).mapToInt(FrontierHiveTissueCell::strength)
                    .findFirst().orElse(0);
            if (tissue < project.requiredTissueStrength()) {
                state.removeMorphogenesisProject(project.id());
                events.add(state.event(FrontierEvent.Type.MORPHOGENESIS_ABORTED, project.id(), causationId));
                continue;
            }
            String organId = FrontierHiveOrgan.dynamicIdFor(hive.id(), project.kind(), project.position(), state.day());
            try {
                state.putHiveOrgan(new FrontierHiveOrgan(organId, hive.id(), project.kind(), project.position(), state.day()));
                state.removeMorphogenesisProject(project.id());
                events.add(state.event(FrontierEvent.Type.MORPHOGENESIS_COMPLETED, project.id(), causationId));
            } catch (IllegalArgumentException invalid) {
                state.removeMorphogenesisProject(project.id());
                events.add(state.event(FrontierEvent.Type.MORPHOGENESIS_ABORTED, project.id(), causationId));
            }
        }
    }

    private static void planMorphogenesis(FrontierWorldState state, FrontierHive hive, String causationId,
                                          List<FrontierEvent> events) {
        if (state.day() % MORPHOGENESIS_CADENCE_DAYS != 0
                || state.morphogenesisProjects().stream().anyMatch(value -> value.hiveId().equals(hive.id()))) return;
        FrontierHiveOrganKind kind = nextGrowthKind(state, hive.id());
        FrontierHiveOrgan source = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.state() == FrontierHiveOrgan.State.ALIVE)
                .filter(value -> value.kind() == FrontierHiveOrganKind.CORE || value.kind() == FrontierHiveOrganKind.SYNAPSE)
                .filter(value -> state.hiveSignal(hive.id(), value.position()) >= 320)
                .sorted(Comparator.comparing(FrontierHiveOrgan::id)).findFirst().orElse(null);
        if (source == null) return;
        state.morphogenesisTarget(hive.id(), kind).flatMap(target -> state.startMorphogenesis(hive.id(), source.id(), kind, target))
                .ifPresent(project -> events.add(state.event(FrontierEvent.Type.MORPHOGENESIS_STARTED, project.id(), causationId)));
    }

    private static FrontierHiveOrganKind nextGrowthKind(FrontierWorldState state, String hiveId) {
        long synapses = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hiveId)
                && value.kind() == FrontierHiveOrganKind.SYNAPSE && value.state() == FrontierHiveOrgan.State.ALIVE).count();
        if (synapses < 2) return FrontierHiveOrganKind.SYNAPSE;
        long digestive = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hiveId)
                && value.kind() == FrontierHiveOrganKind.DIGESTIVE_POOL && value.state() == FrontierHiveOrgan.State.ALIVE).count();
        if (digestive < 2) return FrontierHiveOrganKind.DIGESTIVE_POOL;
        long broods = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hiveId)
                && value.kind() == FrontierHiveOrganKind.BROOD_SAC && value.state() == FrontierHiveOrgan.State.ALIVE).count();
        return broods < 2 ? FrontierHiveOrganKind.BROOD_SAC : FrontierHiveOrganKind.SPORULATOR;
    }
}
