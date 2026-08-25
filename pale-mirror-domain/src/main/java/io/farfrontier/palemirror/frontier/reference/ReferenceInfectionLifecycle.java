package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.List;

/** Source-exact latent-colony, organ-project and destruction lifecycle. */
final class ReferenceInfectionLifecycle {
    private static final double SIGNAL_THRESHOLD = 0.32d;
    private static final double LATENT_DECAY = 0.035d;
    private static final double SPORE_MATURATION_MIN = 5.5d;
    private static final double SPORE_MATURATION_TISSUE = 0.34d;
    private static final double SPORE_CORE_BIOMASS = 38.0d;
    private static final double SPORE_CORE_MIN_DISTANCE = 20.0d;
    private static final double DAMAGE_MEMORY_DECAY = 0.94d;

    private ReferenceInfectionLifecycle() { }

    static boolean startMorphogenesis(ReferenceInfectionModel model, ReferenceHiveOrgan source, ReferenceOrganKind kind,
                                      int targetX, int targetY, int day) {
        double biomass = projectBiomass(kind);
        if (model.signalAt(source.x(), source.y()) < SIGNAL_THRESHOLD || source.biomass() < biomass
                || model.nestProjects.stream().anyMatch(project -> project.x() == targetX && project.y() == targetY)) return false;
        source.biomass(source.biomass() - biomass);
        source.lastProjectDay(day);
        model.nestProjects.add(new ReferenceNestProject(source.id(), targetX, targetY, projectDays(kind), kind, biomass * 0.72d));
        model.projectHistory.add(new ReferenceHiveHistoryEvent(day, "morphogenesis:" + kind.name().toLowerCase(), source.id(), null, targetX, targetY));
        return true;
    }

    static void advanceProjects(ReferenceInfectionModel model, int day) {
        List<ReferenceNestProject> pending = new ArrayList<>();
        for (ReferenceNestProject project : model.nestProjects) {
            ReferenceHiveOrgan source = model.organs.get(project.sourceOrganId());
            if (source == null || model.signalAt(source.x(), source.y()) < SIGNAL_THRESHOLD) {
                model.projectHistory.add(new ReferenceHiveHistoryEvent(day, "aborted_morphogenesis:" + project.kind().name().toLowerCase(),
                        project.sourceOrganId(), null, project.x(), project.y()));
                continue;
            }
            project.daysRemaining(project.daysRemaining() - 1);
            if (project.daysRemaining() > 0) { pending.add(project); continue; }
            if (model.level[project.y()][project.x()] < minimumTissue(project.kind())) continue;
            ReferenceHiveOrgan organ = model.createOrgan(project.x(), project.y(), project.committedBiomass(), null, source.id(), project.kind());
            model.projectHistory.add(new ReferenceHiveHistoryEvent(day, "organ:" + project.kind().name().toLowerCase(), source.id(), organ.id(), project.x(), project.y()));
        }
        model.nestProjects.clear();
        model.nestProjects.addAll(pending);
    }

    static void decayLatentColonies(ReferenceInfectionModel model) {
        List<ReferenceLatentColony> surviving = new ArrayList<>();
        for (ReferenceLatentColony latent : model.latentColonies) {
            latent.spores(latent.spores() * (1.0d - LATENT_DECAY));
            latent.strength(latent.strength() * (1.0d - LATENT_DECAY));
            if (latent.spores() <= 0.3d) continue;
            model.level[latent.y()][latent.x()] = Math.max(model.level[latent.y()][latent.x()], latent.strength());
            boolean distant = model.organs.values().stream().allMatch(organ -> Math.hypot(organ.x() - latent.x(), organ.y() - latent.y()) >= SPORE_CORE_MIN_DISTANCE);
            if (latent.sourceOrganId() != null && latent.spores() >= SPORE_MATURATION_MIN
                    && model.level[latent.y()][latent.x()] >= SPORE_MATURATION_TISSUE && distant) {
                ReferenceHiveOrgan core = model.createOrgan(latent.x(), latent.y(), SPORE_CORE_BIOMASS, 0.0d,
                        latent.sourceOrganId(), ReferenceOrganKind.CORE);
                model.projectHistory.add(new ReferenceHiveHistoryEvent(-1, "spore_maturation:core", latent.sourceOrganId(), core.id(), latent.x(), latent.y()));
                continue;
            }
            surviving.add(latent);
        }
        model.latentColonies.clear();
        model.latentColonies.addAll(surviving);
    }

    static void cullDestroyedOrgans(ReferenceInfectionModel model) {
        List<ReferenceHiveOrgan> destroyed = model.organs.values().stream().filter(organ -> organ.biomass() <= 0.01d || organ.vitality() <= 0.0d).toList();
        for (ReferenceHiveOrgan organ : destroyed) {
            model.ecosystem.addDetritus(organ.x(), organ.y(), organ.biomass() * 0.35d);
            model.organs.remove(organ.id());
            model.projectHistory.add(new ReferenceHiveHistoryEvent(-1, "destroyed:" + organ.kind().name().toLowerCase(), organ.id(), null, organ.x(), organ.y()));
        }
    }

    static void decayDamageMemory(ReferenceInfectionModel model) {
        for (String key : List.copyOf(model.damageMemory.keySet())) {
            double value = model.damageMemory.get(key) * DAMAGE_MEMORY_DECAY;
            if (value < 0.03d) model.damageMemory.remove(key); else model.damageMemory.put(key, value);
        }
    }

    private static double projectBiomass(ReferenceOrganKind kind) { return switch (kind) { case CORE -> 165.0d; case SYNAPSE, DIGESTIVE_POOL -> 55.0d; case BROOD_SAC -> 58.0d; case SPORULATOR -> 60.0d; }; }
    private static int projectDays(ReferenceOrganKind kind) { return switch (kind) { case CORE -> 12; case SYNAPSE, BROOD_SAC -> 4; case DIGESTIVE_POOL, SPORULATOR -> 5; }; }
    private static double minimumTissue(ReferenceOrganKind kind) { return switch (kind) { case CORE -> 0.72d; case SYNAPSE -> 0.42d; case DIGESTIVE_POOL -> 0.35d; case BROOD_SAC -> 0.34d; case SPORULATOR -> 0.38d; }; }
}
