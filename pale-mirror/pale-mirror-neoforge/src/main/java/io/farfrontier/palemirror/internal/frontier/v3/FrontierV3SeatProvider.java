package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSeat;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;
import java.util.function.*;

/** Closed declared furniture family; unavailable integrations fail closed. */
interface FrontierV3SeatProvider {
    record Contact(BooleanSupplier ready, Supplier<FrontierV3NativeEffect.Outcome> change) { }
    Map<BuildingSeat.Kind, FrontierV3SeatProvider> PROVIDERS = new EnumMap<>(BuildingSeat.Kind.class);
    static void register(BuildingSeat.Kind kind, FrontierV3SeatProvider provider) {
        if (PROVIDERS.putIfAbsent(kind, provider) != null) throw new IllegalStateException("duplicate seat provider");
    }
    static Optional<FrontierV3SeatProvider> find(BuildingSeat.Kind kind) { return Optional.ofNullable(PROVIDERS.get(kind)); }
    Optional<Contact> contact(ServerLevel level, FrontierWorldState state, SeatUse use, Mob actor, ActorActuationId authority, SeatUse.Action action);
    boolean seated(FrontierWorldState state, SeatUse use, Mob actor);
    Optional<Boolean> available(ServerLevel level, SeatPlace place, Mob actor);
    boolean furniturePresent(ServerLevel level, SeatPlace place);
}
