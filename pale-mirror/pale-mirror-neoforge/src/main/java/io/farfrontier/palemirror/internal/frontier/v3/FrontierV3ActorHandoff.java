package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

/**
 * Unsaved same-body owner transitions. Every retained declaration may still be
 * present in an entity region; only a confirmed save of current() retires them.
 * No pose, inventory, job progress or simulation authority is copied here.
 */
record FrontierV3ActorHandoff(List<FrontierV3ActorOwnerBinding> bindings) {
    static final int MAX_DECLARATIONS = 64;
    private static final int FORMAT = 2;

    FrontierV3ActorHandoff {
        bindings = List.copyOf(bindings);
        var declarations = bindings.stream().map(FrontierV3ActorOwnerBinding::declaration).toList();
        if (declarations.size() < 2 || declarations.size() > MAX_DECLARATIONS)
            throw new IllegalArgumentException("actor handoff history outside bound");
        var first = declarations.getFirst();
        var revisions = new EnumMap<Owner, Long>(Owner.class);
        for (var declaration : declarations) {
            if (!sameBody(first, declaration) || declaration.representation() != Representation.LIVE_BODY)
                throw new IllegalArgumentException("actor handoff changes physical identity");
            var previous = revisions.put(declaration.owner(), declaration.authorityRevision());
            if (previous != null && declaration.authorityRevision() <= previous)
                throw new IllegalArgumentException("actor handoff repeats or reverses an owner revision");
        }
    }

    static FrontierV3ActorHandoff begin(FrontierV3ActorOwnerBinding from, FrontierV3ActorOwnerBinding to) {
        return new FrontierV3ActorHandoff(List.of(from, to));
    }
    List<Declaration> declarations() { return bindings.stream().map(FrontierV3ActorOwnerBinding::declaration).toList(); }
    FrontierV3ActorOwnerBinding currentBinding() { return bindings.getLast(); }
    Declaration current() { return currentBinding().declaration(); }
    boolean contains(FrontierV3ActorOwnerBinding observed) { return bindings.contains(observed); }

    /**
     * An older serialized body may resume an already durable transfer to its
     * exact current target. It may not branch that history to a different target.
     */
    FrontierV3ActorHandoff extend(FrontierV3ActorOwnerBinding from, FrontierV3ActorOwnerBinding to) {
        Objects.requireNonNull(from); Objects.requireNonNull(to);
        if (currentBinding().equals(to) && contains(from)) return this;
        if (!currentBinding().equals(from)) throw new IllegalArgumentException("actor handoff source is not current");
        var next = new ArrayList<>(bindings); next.add(to);
        return new FrontierV3ActorHandoff(next);
    }

    private static boolean sameBody(Declaration first, Declaration next) {
        return first.actorId().equals(next.actorId()) && first.entityId().equals(next.entityId())
                && first.kind() == next.kind() && first.epoch() == next.epoch();
    }

    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("format", FORMAT);
        var rows = new ListTag();
        bindings.forEach(value -> rows.add(value.save()));
        tag.put("declarations", rows); return tag;
    }
    static FrontierV3ActorHandoff load(CompoundTag tag) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != FORMAT
                || !(tag.get("declarations") instanceof ListTag rows)
                || rows.getElementType() != Tag.TAG_COMPOUND || rows.size() < 2 || rows.size() > MAX_DECLARATIONS)
            throw new IllegalStateException("invalid actor handoff inventory");
        try {
            var declarations = new ArrayList<FrontierV3ActorOwnerBinding>(rows.size());
            rows.forEach(row -> declarations.add(FrontierV3ActorOwnerBinding.load((CompoundTag) row)));
            return new FrontierV3ActorHandoff(declarations);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("invalid actor handoff declaration", invalid);
        }
    }
}
