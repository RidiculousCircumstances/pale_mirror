package io.farfrontier.palemirror.domain;

/** Composition root for cohesive domain services; callers do not construct ad-hoc event factories. */
public final class DomainServices {
    private final DomainEventFactory events = new DomainEventFactory();
    private final SimulationEngine simulation;
    private final ThreatLifecycle threats = new ThreatLifecycle(events);
    private final Narrator narrator = new Narrator(events);
    private final ScenarioRuntime scenarios = new ScenarioRuntime(events);
    private final SettlementSimulation settlements = new SettlementSimulation(events);
    private final DomainCommandProcessor commands;

    public DomainServices() { this(ThreatTierPolicy.DEFAULT); }

    public DomainServices(ThreatTierPolicy tierPolicy) {
        simulation = new SimulationEngine(events, tierPolicy);
        commands = new DomainCommandProcessor(simulation, settlements, threats, narrator, scenarios, events);
    }

    public SimulationEngine simulation() { return simulation; }
    public ThreatLifecycle threats() { return threats; }
    public Narrator narrator() { return narrator; }
    public ScenarioRuntime scenarios() { return scenarios; }
    public SettlementSimulation settlements() { return settlements; }
    public DomainCommandProcessor commands() { return commands; }
    public void setThreatTierPolicy(ThreatTierPolicy policy) { simulation.setTierPolicy(policy); }
}
