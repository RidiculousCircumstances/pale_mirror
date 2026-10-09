package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.List;
import java.util.Set;

/** Explicit live adoption inventory. No inferred kind or permissive legacy/default provider. */
public final class ActorExecutionComposition {
    public static final ActorPresencePolicies PRESENCE_POLICIES = new ActorPresencePolicies(
            Set.of(ActorKind.RESIDENT, ActorKind.BIOFORM, ActorKind.PACK_ANIMAL), List.of(
            new ActorPresencePolicies.Registration(ActorKind.RESIDENT,
                    (state, actorId) -> state.humanPopulation().residents().containsKey(actorId)),
            new ActorPresencePolicies.Registration(ActorKind.BIOFORM, HivePresencePolicy::permits),
            new ActorPresencePolicies.Registration(ActorKind.PACK_ANIMAL,
                    (state, actorId) -> state.transportFleet().assets().containsKey(actorId))));
    public static final ActorActivityCapabilities CAPABILITIES = new ActorActivityCapabilities(
            Set.of(ActorActivityKind.PRESENCE, ActorActivityKind.MEAL, ActorActivityKind.SERVICE_EXIT,
                    ActorActivityKind.FIELD_HARVEST, ActorActivityKind.PRODUCTION, ActorActivityKind.TRANSIT,
                    ActorActivityKind.SCOUT_PATROL,
                    ActorActivityKind.ROUTE_PATROL, ActorActivityKind.HIVE_TASK_ASSEMBLY, ActorActivityKind.HIVE_TASK_RETURN,
                    ActorActivityKind.SETTLEMENT_ASSAULT, ActorActivityKind.ENGINEERING_ASSEMBLY, ActorActivityKind.ENGINEERING_WORK,
                    ActorActivityKind.SETTLEMENT_SERVICE, ActorActivityKind.MEDICAL_TREATMENT, ActorActivityKind.COURIER, ActorActivityKind.GROUP_MEMBER, ActorActivityKind.EXTRACTION),
            List.of(new PresenceActivityCapability(PRESENCE_POLICIES), new MealActivityCapability(), new ServiceExitActivityCapability(),
                    new HarvestActivityCapability(), new ProductionActivityCapability(), new TransitActivityCapability(),
                    new ScoutPatrolActivityCapability(), RoutePatrolExecutionAuthority.capability(),
                    HiveAssemblyExecutionAuthority.capability(), HiveReturnExecutionAuthority.capability(),
                    SettlementAssaultExecutionAuthority.capability(),
                    EngineeringExecutionAuthority.capability(ActorActivityKind.ENGINEERING_ASSEMBLY),
                    EngineeringExecutionAuthority.capability(ActorActivityKind.ENGINEERING_WORK),
                    SettlementServiceExecutionAuthority.capability(), MedicalExecutionAuthority.capability(),
                    new ShipmentExecutionCapability(), new GroupMemberActivityCapability(), new ExtractionActivityCapability()));
    public static final ActorExecutionLifecycle LIFECYCLE = new ActorExecutionLifecycle(CAPABILITIES,
            (state, execution) -> ActorInventoryInteractionFences.pendingOwner(state, execution.actorId()));
    private ActorExecutionComposition() { }
}
