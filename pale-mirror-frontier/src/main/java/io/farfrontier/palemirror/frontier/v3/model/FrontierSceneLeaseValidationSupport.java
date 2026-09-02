package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Aggregate validation shared by all typed scene causes. */
final class FrontierSceneLeaseValidationSupport {
    private FrontierSceneLeaseValidationSupport() { }

    static void validate(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors, Map<SubjectId, StructureCondition> structures,
                         Map<SubjectId, RouteOperation> operations, Map<SubjectId, RouteConstruction> constructions,
                         Map<SubjectId, RouteMaintenance> maintenances, StrategicPlanState plans, Map<SceneLeaseId, SceneLease> leases,
                         Set<SubjectId> activeAmbientActors) {
        Set<SubjectId> leasedActors = new HashSet<>(), leasedOperations = new HashSet<>();
        for (Map.Entry<SceneLeaseId, SceneLease> entry : leases.entrySet()) {
            SceneLease lease = entry.getValue();
            if (!entry.getKey().equals(lease.id()) || !bootstrap.worldId().equals(lease.worldId())) {
                throw new IllegalArgumentException("scene lease identity or world is invalid");
            }
            Set<SubjectId> expected = FrontierSceneBehaviors.expectedMembers(bootstrap, population, actors, structures, operations, constructions, maintenances, plans, lease, leasedOperations);
            Set<SubjectId> members = lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet());
            if (!members.equals(expected)) throw new IllegalArgumentException("scene lease members must exactly match its canonical scene actors");
            for (SubjectId actor : members) {
                if (lease.status() != SceneLeaseStatus.CLOSED && !leasedActors.add(actor)) {
                    throw new IllegalArgumentException("actor cannot belong to multiple active scene leases");
                }
                if (lease.status() != SceneLeaseStatus.CLOSED && activeAmbientActors.contains(actor)) {
                    throw new IllegalArgumentException("actor cannot have both scene and ambient execution leases");
                }
            }
        }
    }

}
