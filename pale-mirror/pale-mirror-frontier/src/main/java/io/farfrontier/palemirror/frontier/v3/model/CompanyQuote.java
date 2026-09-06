package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** A deterministic seller offer. It cannot reserve buyer money or start physical work. */
public record CompanyQuote(SubjectId id, SubjectId demandId, SubjectId sellerId, int itemCount,
                    FixedScalar totalPrice, long quotedAtTick, long expiresAtTick) {
    public CompanyQuote {
        Objects.requireNonNull(id, "market quote id"); Objects.requireNonNull(demandId, "market quote demand");
        Objects.requireNonNull(sellerId, "market quote seller"); Objects.requireNonNull(totalPrice, "market quote total price");
        if (!id.value().startsWith("quote:") || !sellerId.value().startsWith("company:") || itemCount <= 0
                || totalPrice.raw() <= 0L || quotedAtTick < 0L || expiresAtTick < quotedAtTick) {
            throw new IllegalArgumentException("market quote must retain a named seller, positive exact offer and valid expiry");
        }
    }
}
