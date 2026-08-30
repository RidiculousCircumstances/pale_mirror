package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded legal register. It deliberately holds neither money nor physical item custody. */
public record CompanyRegistry(Map<SubjectId, Company> companies) {
    public static final int MAX_COMPANIES = 1_024;

    public CompanyRegistry {
        companies = Map.copyOf(companies);
        if (companies.size() > MAX_COMPANIES) throw new IllegalArgumentException("company registry retention limit exceeded");
        for (Map.Entry<SubjectId, Company> entry : companies.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("company registry key must match company identity");
        }
    }

    static CompanyRegistry empty() { return new CompanyRegistry(Map.of()); }

    CompanyRegistry register(Company company) {
        Objects.requireNonNull(company, "company");
        if (companies.containsKey(company.id())) throw new IllegalArgumentException("company identity already exists: " + company.id().value());
        if (companies.values().stream().anyMatch(existing -> existing.settlementId().equals(company.settlementId())
                && existing.purpose() == company.purpose() && existing.status() != CompanyStatus.DISSOLVED)) {
            throw new IllegalArgumentException("settlement already has an active company for purpose: " + company.purpose());
        }
        Map<SubjectId, Company> next = new LinkedHashMap<>(companies); next.put(company.id(), company);
        return new CompanyRegistry(next);
    }
}
