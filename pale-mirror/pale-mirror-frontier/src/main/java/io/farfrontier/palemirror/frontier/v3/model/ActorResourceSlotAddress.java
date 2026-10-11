package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Common physical address validation for an explicitly selected resident inventory slot. */
final class ActorResourceSlotAddress {
    private ActorResourceSlotAddress() { }
    static boolean admits(SubjectId actorId, ActorItemSlot slot, ResourceCustody custody, PhysicalStackAddress address) {
        if (!(custody instanceof ResourceCustody.Actor actor) || !actor.actorId().equals(actorId)) return false;
        return switch (slot) {
            case ActorItemSlot.Hand declared -> address instanceof PhysicalStackAddress.ActorHand hand
                    && hand.actorId().equals(actorId) && hand.hand() == declared.hand();
            case ActorItemSlot.Pocket declared -> address instanceof PhysicalStackAddress.ActorPocket pocket
                    && pocket.actorId().equals(actorId) && pocket.slot() == declared.index();
            case ActorItemSlot.AttachedStorage ignored -> false;
        };
    }
}
