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

    static HiveNest operationalNestForStore(FrontierWorldState state, SubjectId containerId) {
        HiveOrgan store = java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.kind() == HiveOrganKind.STORE && organ.containerId().equals(java.util.Optional.of(containerId)))
                .filter(organ -> state.isHiveOrganOperational(organ.id())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("container is not an operational hive store: " + containerId.value()));
        return state.bootstrap().hive().seedNests().stream().filter(nest -> nest.id().equals(store.nestId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hive store has an unknown nest: " + containerId.value()));
    }
}
