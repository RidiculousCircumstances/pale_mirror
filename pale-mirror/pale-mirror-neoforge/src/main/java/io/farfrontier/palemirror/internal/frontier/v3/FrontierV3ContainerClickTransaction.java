package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import java.util.*;

/** A single persisted cause fences every explicitly ordered physical member. */
record FrontierV3ContainerClickTransaction(List<FrontierV3DepotClickWitness> members) {
    FrontierV3ContainerClickTransaction {
        members = List.copyOf(members);
        if (members.isEmpty() || members.size() > 54
                || members.stream().map(FrontierV3DepotClickWitness::containerId).distinct().count() != members.size())
            throw new IllegalArgumentException("invalid composed click membership");
        var first = members.getFirst();
        if (members.stream().anyMatch(m -> !m.playerId().equals(first.playerId()) || !m.interactionId().equals(first.interactionId())
                || m.after().isPresent() != first.after().isPresent() || !m.returnSource().equals(first.returnSource())
                || !m.returnedRemaining().equals(first.returnedRemaining())))
            throw new IllegalArgumentException("composed click lacks one cause and complete postimages");
    }
    SubjectId id() { return members.getFirst().containerId(); }
    boolean contains(SubjectId id) { return members.stream().anyMatch(m -> m.containerId().equals(id)); }
    CompoundTag save() {
        var tag = new CompoundTag(); var entries = new ListTag();
        members.forEach(m -> entries.add(m.save())); tag.put("members", entries); return tag;
    }
    static FrontierV3ContainerClickTransaction read(CompoundTag tag) {
        var entries = tag.getList("members", Tag.TAG_COMPOUND);
        if (entries.size() > 54) throw new IllegalArgumentException("unbounded composed click");
        var members = new ArrayList<FrontierV3DepotClickWitness>();
        for (int i = 0; i < entries.size(); i++) members.add(FrontierV3DepotClickWitness.read(entries.getCompound(i)));
        return new FrontierV3ContainerClickTransaction(members);
    }
}
