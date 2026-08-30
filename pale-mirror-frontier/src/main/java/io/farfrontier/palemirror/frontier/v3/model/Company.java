package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** One named legal organization. Its matching financial account has the same stable ID. */
public record Company(SubjectId id, SubjectId settlementId, SubjectId founderId, CompanyPurpose purpose,
                      CompanyStatus status, long registeredAtTick) {
    public Company {
        Objects.requireNonNull(id, "company id"); Objects.requireNonNull(settlementId, "company settlement");
        Objects.requireNonNull(founderId, "company founder"); Objects.requireNonNull(purpose, "company purpose");
        Objects.requireNonNull(status, "company status");
        if (!id.value().startsWith("company:")) throw new IllegalArgumentException("company id must use company: namespace");
        if (registeredAtTick < 0L) throw new IllegalArgumentException("company registration tick must not be negative");
    }
}
