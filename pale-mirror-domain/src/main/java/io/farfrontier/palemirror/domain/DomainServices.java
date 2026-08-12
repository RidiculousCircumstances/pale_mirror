package io.farfrontier.palemirror.domain;

/** Composition root for cohesive domain services; callers do not construct ad-hoc event factories. */
public final class DomainServices {
    private final DomainEventFactory events = new DomainEventFactory();
    private final SimulationEngine simulation;
    private final ThreatLifecycle threats = new ThreatLifecycle(events);
    private final Narrator narrator = new Narrator(events);
    private final ScenarioRuntime scenarios = new ScenarioRuntime(events);
    private final SettlementCrisisRuntime settlementCrises = new SettlementCrisisRuntime(events);
    private final ResourceFlowSimulation resources = new ResourceFlowSimulation(events);
    private final SettlementDecisionEngine settlementDecisions = new SettlementDecisionEngine(events);
    private final SettlementEmergencyRuntime settlementEmergencies = new SettlementEmergencyRuntime(events);
    private final JourneySimulation journeys = new JourneySimulation(events);
    private final SettlementDevelopmentEngine settlementDevelopment = new SettlementDevelopmentEngine(events);
    private final DomainCommandProcessor commands;

    public DomainServices() { this(ThreatTierPolicy.DEFAULT); }

    public DomainServices(ThreatTierPolicy tierPolicy) {
        simulation = new SimulationEngine(events, tierPolicy);
        commands = new DomainCommandProcessor(simulation, resources, settlementDecisions, settlementEmergencies,
                journeys, settlementDevelopment,
                threats, narrator, scenarios, settlementCrises, events);
    }

    public SimulationEngine simulation() { return simulation; }
    public ThreatLifecycle threats() { return threats; }
    public Narrator narrator() { return narrator; }
    public ScenarioRuntime scenarios() { return scenarios; }
    public SettlementCrisisRuntime settlementCrises() { return settlementCrises; }
    public ResourceFlowSimulation resources() { return resources; }
    public SettlementDecisionEngine settlementDecisions() { return settlementDecisions; }
    public SettlementEmergencyRuntime settlementEmergencies() { return settlementEmergencies; }
    public JourneySimulation journeys() { return journeys; }
    public SettlementDevelopmentEngine settlementDevelopment() { return settlementDevelopment; }
    public DomainCommandProcessor commands() { return commands; }
    public void setThreatTierPolicy(ThreatTierPolicy policy) { simulation.setTierPolicy(policy); }
}
