package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mutable aggregate used only on the server thread. Persistence adapters own serialization. */
public final class WorldState {
    private final Map<WorldObjectId, FacilityState> facilities = new LinkedHashMap<>();
    private final Map<String, ScenarioInstance> scenarios = new LinkedHashMap<>();
    private final List<DomainEvent> history = new ArrayList<>();
    private long schemaVersion = 1;
    private long simulationStep;
    private long eventSequence;

    public long schemaVersion() { return schemaVersion; }
    public void setSchemaVersion(long schemaVersion) { this.schemaVersion = schemaVersion; }
    public long simulationStep() { return simulationStep; }
    public void setSimulationStep(long value) { simulationStep = value; }
    public long nextEventSequence() { return ++eventSequence; }
    public void setEventSequence(long value) { eventSequence = value; }
    public Collection<FacilityState> facilities() { return facilities.values(); }
    public Collection<ScenarioInstance> scenarios() { return scenarios.values(); }
    public List<DomainEvent> history() { return history; }
    public Optional<FacilityState> facility(WorldObjectId id) { return Optional.ofNullable(facilities.get(id)); }
    public Optional<ScenarioInstance> scenario(String id) { return Optional.ofNullable(scenarios.get(id)); }
    public void putFacility(FacilityState facility) { facilities.put(facility.id(), facility); }
    public void putScenario(ScenarioInstance scenario) { scenarios.put(scenario.id(), scenario); }
    public void addEvent(DomainEvent event) { history.add(event); }
    public boolean hasScenarioForSource(String sourceEventId, StoryAudienceId audience) {
        return scenarios.values().stream().anyMatch(value -> value.sourceEventId().equals(sourceEventId) && value.audience().equals(audience));
    }
}
