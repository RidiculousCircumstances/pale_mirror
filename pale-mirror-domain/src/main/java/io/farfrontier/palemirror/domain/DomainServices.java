package io.farfrontier.palemirror.domain;

/** Composition root for cohesive domain services; callers do not construct ad-hoc event factories. */
public final class DomainServices {
    private final DomainEventFactory events = new DomainEventFactory();
    private final SimulationEngine simulation = new SimulationEngine(events);
    private final ThreatLifecycle threats = new ThreatLifecycle(events);
    private final Narrator narrator = new Narrator(events);
    private final ScenarioRuntime scenarios = new ScenarioRuntime(events);
    private final DomainCommandProcessor commands = new DomainCommandProcessor(simulation, threats, narrator, scenarios, events);

    public SimulationEngine simulation() { return simulation; }
    public ThreatLifecycle threats() { return threats; }
    public Narrator narrator() { return narrator; }
    public ScenarioRuntime scenarios() { return scenarios; }
    public DomainCommandProcessor commands() { return commands; }
}
