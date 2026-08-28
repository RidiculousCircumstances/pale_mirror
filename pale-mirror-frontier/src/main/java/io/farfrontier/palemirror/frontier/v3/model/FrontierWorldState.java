package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable canonical mutable state for the fresh v3 profile. Bootstrap defines identity and
 * ownership; this state owns every mutable actor location, structure condition and infection cell.
 */
public record FrontierWorldState(
        FrontierBootstrap bootstrap,
        Map<SubjectId, ActorLocation> actorLocations,
        Map<SubjectId, StructureCondition> structureConditions,
        Map<InfectionCell, FixedRatio> infection,
        ExactInventory inventory
) {
    private static final FixedRatio ZERO_INFECTION = new FixedRatio(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO);

    public FrontierWorldState {
        Objects.requireNonNull(bootstrap, "bootstrap");
        actorLocations = immutableMap(actorLocations, "actor locations");
        structureConditions = immutableMap(structureConditions, "structure conditions");
        infection = immutableMap(infection, "infection");
        Objects.requireNonNull(inventory, "inventory");
        Set<SubjectId> expectedActors = actorIds(bootstrap);
        if (!expectedActors.equals(actorLocations.keySet())) throw new IllegalArgumentException("actor location index must own every and only bootstrap actor");
        for (ActorLocation location : actorLocations.values()) requirePosition(bootstrap.bounds(), location.position());
        Set<SubjectId> expectedStructures = structureIds(bootstrap);
        if (!expectedStructures.equals(structureConditions.keySet())) throw new IllegalArgumentException("structure condition index must own every and only bootstrap structure");
        for (Map.Entry<InfectionCell, FixedRatio> entry : infection.entrySet()) {
            if (entry.getValue().equals(ZERO_INFECTION)) throw new IllegalArgumentException("sparse infection index must not retain zero cells");
            requirePosition(bootstrap.bounds(), entry.getKey().originAtY(0));
        }
    }

    public static FrontierWorldState initial(FrontierBootstrap bootstrap) {
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> settlement.residents().forEach(resident -> actors.put(resident.id(), new ActorLocation(resident.home()))));
        bootstrap.hive().bioforms().forEach(bioform -> actors.put(bioform.id(), new ActorLocation(bioform.position())));
        Map<SubjectId, StructureCondition> structures = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().forEach(structure -> structures.put(structure.id(), StructureCondition.INTACT)));
        Map<SubjectId, ContainerRecord> containers = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> containers.put(new SubjectId("container:" + settlement.id().value().substring("settlement:".length()) + "-depot"),
                new ContainerRecord(new SubjectId("container:" + settlement.id().value().substring("settlement:".length()) + "-depot"), settlement.id(), 27)));
        Map<InfectionCell, FixedRatio> infection = new LinkedHashMap<>();
        bootstrap.hive().seedNests().forEach(nest -> infection.put(InfectionCell.at(nest.anchor()), new FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(500_000L))));
        return new FrontierWorldState(bootstrap, actors, structures, infection, new ExactInventory(containers, Map.of(), Map.of(), Map.of()));
    }

    public FrontierWorldState withActorLocation(SubjectId actor, BlockPosition position) {
        Objects.requireNonNull(actor, "actor");
        requirePosition(bootstrap.bounds(), position);
        if (!actorLocations.containsKey(actor)) throw new IllegalArgumentException("unknown actor: " + actor.value());
        Map<SubjectId, ActorLocation> next = new LinkedHashMap<>(actorLocations);
        next.put(actor, new ActorLocation(position));
        return new FrontierWorldState(bootstrap, next, structureConditions, infection, inventory);
    }

    public FrontierWorldState withStructureCondition(SubjectId structure, StructureCondition condition) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(condition, "structure condition");
        if (!structureConditions.containsKey(structure)) throw new IllegalArgumentException("unknown structure: " + structure.value());
        Map<SubjectId, StructureCondition> next = new LinkedHashMap<>(structureConditions);
        next.put(structure, condition);
        return new FrontierWorldState(bootstrap, actorLocations, next, infection, inventory);
    }

    public FrontierWorldState withInfection(InfectionCell cell, FixedRatio intensity) {
        Objects.requireNonNull(cell, "infection cell");
        Objects.requireNonNull(intensity, "infection intensity");
        requirePosition(bootstrap.bounds(), cell.originAtY(0));
        Map<InfectionCell, FixedRatio> next = new LinkedHashMap<>(infection);
        if (intensity.equals(ZERO_INFECTION)) next.remove(cell); else next.put(cell, intensity);
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, next, inventory);
    }

    public FrontierWorldState withInventory(ExactInventory nextInventory) {
        return new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, nextInventory);
    }

    private static Set<SubjectId> actorIds(FrontierBootstrap bootstrap) {
        Set<SubjectId> ids = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> settlement.residents().forEach(resident -> ids.add(resident.id())));
        bootstrap.hive().bioforms().forEach(bioform -> ids.add(bioform.id()));
        return ids;
    }
    private static Set<SubjectId> structureIds(FrontierBootstrap bootstrap) {
        Set<SubjectId> ids = new HashSet<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().forEach(structure -> ids.add(structure.id())));
        return ids;
    }
    private static void requirePosition(WorldBounds bounds, BlockPosition position) {
        if (!bounds.contains(position)) throw new IllegalArgumentException("canonical state position is outside frontier bounds");
    }
    private static <K, V> Map<K, V> immutableMap(Map<K, V> input, String label) {
        Objects.requireNonNull(input, label);
        LinkedHashMap<K, V> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(Objects.requireNonNull(key, label + " key"), Objects.requireNonNull(value, label + " value")));
        return Map.copyOf(copy);
    }
}
