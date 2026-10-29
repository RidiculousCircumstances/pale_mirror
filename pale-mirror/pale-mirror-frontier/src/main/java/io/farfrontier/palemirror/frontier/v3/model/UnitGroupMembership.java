package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState;
import java.util.*;

/** Current declared active membership only. The roster owner retains uniqueness;
 * this read grants no work, scene, movement or body authority. */
final class UnitGroupMembership {
    private static final ImmutableGroupView<SubjectId, UnitGroup, SubjectId> MEMBERS = new ImmutableGroupView<>();
    private UnitGroupMembership() { }
    static boolean active(UnitGroupState state, SubjectId actor) {
        return !MEMBERS.unorderedRows(UnitGroupMembership.class, state.groups(), group ->
                group.phase() == UnitGroup.Phase.CLOSED ? Set.of() : group.members().stream()
                        .map(UnitGroup.Member::actorId).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                Objects.requireNonNull(actor)).isEmpty();
    }
}
