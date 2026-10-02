package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** A read-only, immediately executable family proposal; refusal creates no resident assignment. */
public record ResidentWorkOffer<T>(ResidentWorkKind kind, SubjectId residentId, T execution,
                                    List<WorkReservationClaim> claims) {
    public ResidentWorkOffer {
        Objects.requireNonNull(kind); Objects.requireNonNull(residentId); Objects.requireNonNull(execution);
        claims = List.copyOf(claims);
        if (claims.isEmpty() || claims.size() > 128 || claims.stream().distinct().count() != claims.size())
            throw new IllegalArgumentException("work offer needs bounded distinct exact admission claims");
    }
}
