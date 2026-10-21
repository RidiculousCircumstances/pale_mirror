package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Set;

/** Field-owned hand presentation. Retained carry proof permits binding, never re-issuance.
 * The common native operation owns the one mutation attempt and durable outcome. */
final class FrontierV3HarvestHandOperation {
    record Binding(FrontierV3OperationReceipt.Declaration declaration, List<FrontierV3MinecraftOperation.Participant> ports) { }
    private FrontierV3HarvestHandOperation() { }

    static FrontierV3MinecraftOperation.Result apply(ServerLevel level, FrontierWorldState state,
            FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        var binding = binding(state, witness, body);
        return FrontierV3MinecraftOperation.apply(level, binding.declaration(), binding.ports(),
                () -> before(state, witness, body),
                () -> switch (witness.mode()) {
                    case BIND_RETAINED -> retained(state, witness, body);
                    case MATERIALIZE_COLD -> FrontierV3VillagerHandMutation.setOffhand(body, stack(witness));
                }, () -> after(state, witness, body));
    }

    static Binding binding(FrontierWorldState state, FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        if (!body.getUUID().equals(witness.entityId()))
            throw new IllegalArgumentException("field hand operation has a foreign body");
        var participant = FrontierV3OperationEntityParticipant.current(body,
                ActorBodyAuthority.current(state, witness.workerId()).physicalEpoch());
        var declaration = new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.FIELD_HAND_PROJECTION, witness.jobId(),
                FrontierV3OperationFingerprint.of(witness.write()), FrontierV3OperationFingerprint.of(witness.write()),
                Set.of(participant.identity()));
        return new Binding(declaration, List.of(participant));
    }

    static boolean after(FrontierWorldState state, FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        return ItemStack.matches(body.getOffhandItem(), stack(witness))
                && (witness.mode() != FrontierV3ResourceSiteHandProjectionWitness.Mode.BIND_RETAINED || retained(state, witness, body));
    }

    private static boolean before(FrontierWorldState state, FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        return switch (witness.mode()) {
            case MATERIALIZE_COLD -> body.getOffhandItem().isEmpty();
            case BIND_RETAINED -> retained(state, witness, body);
        };
    }

    private static boolean retained(FrontierWorldState state, FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        return FrontierV3ActorCarryProjection.witnessed(state, witness.workerId(), witness.actorAccountId(), body);
    }

    private static ItemStack stack(FrontierV3ResourceSiteHandProjectionWitness witness) {
        return new ItemStack(Items.WHEAT, witness.quantity());
    }
}
