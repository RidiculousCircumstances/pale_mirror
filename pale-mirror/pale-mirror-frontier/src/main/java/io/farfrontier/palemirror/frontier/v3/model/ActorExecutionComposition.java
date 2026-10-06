package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.List;
import java.util.Set;

/** Explicit live adoption inventory. No inferred kind or permissive legacy/default provider. */
public final class ActorExecutionComposition {
    public static final ActorPresencePolicies PRESENCE_POLICIES = new ActorPresencePolicies(
            Set.of(ActorKind.RESIDENT, ActorKind.BIOFORM), List.of(
            new ActorPresencePolicies.Registration(ActorKind.RESIDENT,
                    (state, actorId) -> state.humanPopulation().residents().containsKey(actorId)),
            new ActorPresencePolicies.Registration(ActorKind.BIOFORM, HivePresencePolicy::permits)));
    public static final ActorActivityCapabilities CAPABILITIES = new ActorActivityCapabilities(
            Set.of(ActorActivityKind.PRESENCE, ActorActivityKind.MEAL, ActorActivityKind.SERVICE_EXIT,
                    ActorActivityKind.FIELD_HARVEST, ActorActivityKind.PRODUCTION, ActorActivityKind.TRANSIT,
                    ActorActivityKind.SCOUT_PATROL, ActorActivityKind.OPERATION_ASSEMBLY, ActorActivityKind.LOGISTICS,
                    ActorActivityKind.ROUTE_PATROL, ActorActivityKind.HIVE_TASK_ASSEMBLY, ActorActivityKind.HIVE_TASK_RETURN,
                    ActorActivityKind.SETTLEMENT_ASSAULT, ActorActivityKind.ENGINEERING_ASSEMBLY, ActorActivityKind.ENGINEERING_WORK,
                    ActorActivityKind.SETTLEMENT_SERVICE, ActorActivityKind.MEDICAL_TREATMENT, ActorActivityKind.ROUTE_INTERCEPTION, ActorActivityKind.COURIER, ActorActivityKind.GROUP_MEMBER),
            List.of(new PresenceActivityCapability(PRESENCE_POLICIES), new MealActivityCapability(), new ServiceExitActivityCapability(),
                    new HarvestActivityCapability(), new ProductionActivityCapability(), new TransitActivityCapability(),
                    new ScoutPatrolActivityCapability(), OperationExecutionAuthority.assemblyCapability(),
                    OperationExecutionAuthority.logisticsCapability(), RoutePatrolExecutionAuthority.capability(),
                    HiveAssemblyExecutionAuthority.capability(), HiveReturnExecutionAuthority.capability(),
                    SettlementAssaultExecutionAuthority.capability(),
                    EngineeringExecutionAuthority.capability(ActorActivityKind.ENGINEERING_ASSEMBLY),
                    EngineeringExecutionAuthority.capability(ActorActivityKind.ENGINEERING_WORK),
                    SettlementServiceExecutionAuthority.capability(), MedicalExecutionAuthority.capability(),
                    RouteEngagementExecutionAuthority.capability(), new ShipmentExecutionCapability(), new GroupMemberActivityCapability()));
    public static final ActorExecutionLifecycle LIFECYCLE = new ActorExecutionLifecycle(CAPABILITIES);
    private ActorExecutionComposition() { }
}
