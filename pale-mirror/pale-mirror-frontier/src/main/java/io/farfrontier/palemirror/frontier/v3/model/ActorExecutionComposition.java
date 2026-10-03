package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.List;
import java.util.Set;

/** Explicit live adoption inventory. No inferred kind or permissive legacy/default provider. */
public final class ActorExecutionComposition {
    public static final ActorActivityCapabilities CAPABILITIES = new ActorActivityCapabilities(
            Set.of(ActorActivityKind.PRESENCE, ActorActivityKind.MEAL, ActorActivityKind.SERVICE_EXIT,
                    ActorActivityKind.FIELD_HARVEST, ActorActivityKind.PRODUCTION, ActorActivityKind.TRANSIT,
                    ActorActivityKind.SCOUT_PATROL, ActorActivityKind.OPERATION_ASSEMBLY, ActorActivityKind.LOGISTICS),
            List.of(new PresenceActivityCapability(), new MealActivityCapability(), new ServiceExitActivityCapability(),
                    new HarvestActivityCapability(), new ProductionActivityCapability(), new TransitActivityCapability(),
                    new ScoutPatrolActivityCapability(), OperationExecutionAuthority.assemblyCapability(),
                    OperationExecutionAuthority.logisticsCapability()));
    public static final ActorExecutionLifecycle LIFECYCLE = new ActorExecutionLifecycle(CAPABILITIES);
    private ActorExecutionComposition() { }
}
