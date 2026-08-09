package io.farfrontier.palemirror.domain;

/** Composition root for cohesive domain services; callers do not construct ad-hoc event factories. */
public final class DomainServices {
    private final DomainEventFactory events = new DomainEventFactory();

    public SimulationEngine simulation() { return new SimulationEngine(events); }
    public ThreatLifecycle threats() { return new ThreatLifecycle(events); }
    public Narrator narrator() { return new Narrator(events); }
    public ScenarioRuntime scenarios() { return new ScenarioRuntime(events); }
}
