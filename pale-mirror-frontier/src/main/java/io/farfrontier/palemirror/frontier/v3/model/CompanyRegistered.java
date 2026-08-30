package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Atomic legal registration and account opening fact. */
public record CompanyRegistered(Company company) implements FrontierPayload {
    public CompanyRegistered { Objects.requireNonNull(company, "company"); }
    @Override public String type() { return "frontier.company_registered"; }
}
