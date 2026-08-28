package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Resolves operational exact hive stores across bootstrap and growth state. */
final class HiveStorageSupport {
    private HiveStorageSupport() { }

    static boolean isOperationalStore(FrontierWorldState state, SubjectId containerId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .anyMatch(organ -> organ.kind() == HiveOrganKind.STORE && organ.containerId().equals(java.util.Optional.of(containerId))
                        && state.isHiveOrganOperational(organ.id()));
    }
}
