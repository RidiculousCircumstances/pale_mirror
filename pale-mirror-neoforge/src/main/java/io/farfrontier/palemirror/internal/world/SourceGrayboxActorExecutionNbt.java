package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** NeoForge NBT carrier for the pure-domain graybox actor execution ledger. */
final class SourceGrayboxActorExecutionNbt {
    static final int FORMAT = 4;
    static final int LEGACY_GLOBAL_REVISION_FORMAT = 3;

    private SourceGrayboxActorExecutionNbt() { }

    static CompoundTag write(ReferenceGrayboxActorExecutionState state) {
        Objects.requireNonNull(state, "state");
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", FORMAT);
        ListTag actors = new ListTag();
        for (ReferenceGrayboxActorExecutionState.ActorState actor : state.actors()) {
            CompoundTag encoded = new CompoundTag();
            encoded.putString("id", actor.id());
            encoded.putString("kind", actor.kind().name());
            encoded.putString("mode", actor.mode().name());
            encoded.putString("revision", actor.sourceRevision());
            encoded.putInt("anchorX16", actor.anchorXSixteenths());
            encoded.putInt("anchorZ16", actor.anchorZSixteenths());
            encoded.putInt("actualX16", actor.actualXSixteenths());
            encoded.putInt("actualZ16", actor.actualZSixteenths());
            encoded.putLong("epoch", actor.leaseEpoch());
            encoded.putString("lease", actor.leaseId());
            encoded.putString("holder", actor.holder());
            encoded.putLong("changedAt", actor.changedAtGameTick());
            encoded.putLong("demandedAt", actor.demandedAtGameTick());
            encoded.putLong("combatEpoch", actor.combatActionEpoch());
            encoded.putLong("nextCombatAt", actor.nextCombatAtGameTick());
            actors.add(encoded);
        }
        tag.put("actors", actors);
        return tag;
    }

    static ReferenceGrayboxActorExecutionState read(CompoundTag tag, boolean legacyGlobalRevision) {
        CompoundTag required = Objects.requireNonNull(tag, "tag");
        int expectedFormat = legacyGlobalRevision ? LEGACY_GLOBAL_REVISION_FORMAT : FORMAT;
        if (required.getInt("format") != expectedFormat || !required.contains("actors", Tag.TAG_LIST)) {
            throw new IllegalStateException("incompatible source graybox actor execution envelope");
        }
        ListTag actors = required.getList("actors", Tag.TAG_COMPOUND);
        if (actors.size() > ReferenceGrayboxActorExecutionState.MAX_ACTORS) {
            throw new IllegalStateException("source graybox actor execution exceeds its bound");
        }
        List<ReferenceGrayboxActorExecutionState.ActorState> restored = new ArrayList<>(actors.size());
        try {
            for (Tag raw : actors) {
                CompoundTag value = (CompoundTag) raw;
                restored.add(new ReferenceGrayboxActorExecutionState.ActorState(value.getString("id"),
                        ReferenceGrayboxActorExecutionState.ActorKind.valueOf(value.getString("kind")),
                        ReferenceGrayboxActorExecutionState.Mode.valueOf(value.getString("mode")), value.getString("revision"),
                        value.getInt("anchorX16"), value.getInt("anchorZ16"), value.getInt("actualX16"), value.getInt("actualZ16"),
                        value.getLong("epoch"), value.getString("lease"), value.getString("holder"), value.getLong("changedAt"),
                        value.getLong("demandedAt"), value.getLong("combatEpoch"), value.getLong("nextCombatAt")));
            }
            return ReferenceGrayboxActorExecutionState.restore(restored);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("source graybox actor execution state is invalid", invalid);
        }
    }
}
