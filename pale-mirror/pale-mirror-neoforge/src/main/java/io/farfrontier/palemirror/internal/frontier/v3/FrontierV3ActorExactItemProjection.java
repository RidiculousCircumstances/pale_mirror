package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;

/** Exact held inventory is projected only at common private-body admission.
 * No activity may repair an already-live hand from canonical state. */
final class FrontierV3ActorExactItemProjection {
    private FrontierV3ActorExactItemProjection() { }
    static boolean prepareNew(FrontierWorldState state, SubjectId actor, Mob body) {
        var items = state.inventory().actorItems(actor);
        if (items.isEmpty()) return true;
        // Current exact Actor custody has one physical main-hand slot. Never choose
        // an arbitrary item when declarations exceed that capacity.
        if (items.size() != 1) return false;
        var item = items.getFirst();
        var actual = body.getMainHandItem();
        if (!actual.isEmpty()) return FrontierV3ExactItemPresentation.exactMatch(actual, item);
        body.setItemSlot(EquipmentSlot.MAINHAND, FrontierV3ExactItemPresentation.materializedStack(item));
        return FrontierV3ExactItemPresentation.exactMatch(body.getMainHandItem(), item);
    }
}
