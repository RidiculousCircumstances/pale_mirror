package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Resolves one exact actor-held stack before Vanilla removes a managed body on death.
 *
 * <p>A matching physical hand becomes one tagged world drop only after the canonical custody
 * transfer is durable. A missing or altered hand is an observed loss instead; it is never
 * reconstructed into the world. This first boundary intentionally supports the single visible
 * hand surface used by the current graybox equipment contract.</p>
 */
final class FrontierV3ActorEquipmentDeathExecutor {
    private FrontierV3ActorEquipmentDeathExecutor() { }

    static boolean resolve(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, SubjectId actor) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(entity).orElse(null);
        if (state == null || declaration == null || !declaration.actorId().equals(actor)
                || !state.actorLocations().containsKey(actor) || declaration.kind() != state.actorLocations().get(actor).kind()
                || !io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor).equals(entity.getUUID()))
            return false;
        List<ExactItemStack> items = state.inventory().actorItems(actor).stream()
                .filter(item -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())).toList();
        if (items.isEmpty()) return false;
        if (items.size() != 1) {
            throw new IllegalStateException("current graybox actor equipment contract permits one exact held weapon");
        }
        if (!(entity instanceof Mob mob)) throw new IllegalArgumentException("managed equipment requires a Mob hand");
        return FrontierV3ExactHeldItemDeath.resolve(level, runtime, mob, items.getFirst(),
                FrontierV3DiagnosticTrace.defenderEquipmentCorrelation(items.getFirst().id()), "defender_equipment");
    }
}
