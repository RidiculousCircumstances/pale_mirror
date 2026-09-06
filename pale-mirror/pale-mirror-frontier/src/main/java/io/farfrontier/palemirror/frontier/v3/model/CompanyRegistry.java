package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/** Bounded legal/institution register. It deliberately holds neither money nor physical item custody. */
public record CompanyRegistry(Map<SubjectId, Company> companies, Map<SubjectId, EmploymentContract> employmentContracts,
                              MarketOrderBook market) {
    public static final int MAX_COMPANIES = 1_024;
    public static final int MAX_EMPLOYMENT_CONTRACTS = 4_096;

    public CompanyRegistry {
        companies = Map.copyOf(companies);
        employmentContracts = Map.copyOf(employmentContracts);
        Objects.requireNonNull(market, "market order book");
        if (companies.size() > MAX_COMPANIES) throw new IllegalArgumentException("company registry retention limit exceeded");
        if (employmentContracts.size() > MAX_EMPLOYMENT_CONTRACTS) throw new IllegalArgumentException("employment contract retention limit exceeded");
        for (Map.Entry<SubjectId, Company> entry : companies.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("company registry key must match company identity");
        }
        Set<SubjectId> currentlyEmployed = new HashSet<>();
        for (Map.Entry<SubjectId, EmploymentContract> entry : employmentContracts.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("employment contract key must match contract identity");
            if (!companies.containsKey(entry.getValue().companyId())) throw new IllegalArgumentException("employment contract must name a registered company");
            if (entry.getValue().status() != EmploymentContractStatus.TERMINATED && !currentlyEmployed.add(entry.getValue().residentId())) {
                throw new IllegalArgumentException("resident cannot retain multiple current employment contracts");
            }
        }
    }

    public CompanyRegistry(Map<SubjectId, Company> companies, Map<SubjectId, EmploymentContract> employmentContracts) {
        this(companies, employmentContracts, MarketOrderBook.empty());
    }
    public CompanyRegistry(Map<SubjectId, Company> companies) { this(companies, Map.of(), MarketOrderBook.empty()); }
    public static CompanyRegistry empty() { return new CompanyRegistry(Map.of(), Map.of(), MarketOrderBook.empty()); }

    public CompanyRegistry register(Company company) {
        Objects.requireNonNull(company, "company");
        if (companies.containsKey(company.id())) throw new IllegalArgumentException("company identity already exists: " + company.id().value());
        if (companies.values().stream().anyMatch(existing -> existing.settlementId().equals(company.settlementId())
                && existing.purpose() == company.purpose() && existing.status() != CompanyStatus.DISSOLVED)) {
            throw new IllegalArgumentException("settlement already has an active company for purpose: " + company.purpose());
        }
        Map<SubjectId, Company> next = new LinkedHashMap<>(companies); next.put(company.id(), company);
        return new CompanyRegistry(next, employmentContracts, market);
    }

    public CompanyRegistry openEmployment(EmploymentContract contract) {
        Objects.requireNonNull(contract, "employment contract");
        Company company = companies.get(contract.companyId());
        if (company == null || company.status() != CompanyStatus.ACTIVE) throw new IllegalArgumentException("employment needs an active registered company");
        if (!company.settlementId().value().startsWith("settlement:")) throw new IllegalArgumentException("employment company has an invalid settlement");
        if (employmentContracts.containsKey(contract.id())) throw new IllegalArgumentException("employment contract identity already exists: " + contract.id().value());
        if (employmentContracts.values().stream().anyMatch(existing -> existing.residentId().equals(contract.residentId())
                && existing.status() != EmploymentContractStatus.TERMINATED)) {
            throw new IllegalArgumentException("resident already has a current employment contract");
        }
        Map<SubjectId, EmploymentContract> next = new LinkedHashMap<>(employmentContracts); next.put(contract.id(), contract);
        return new CompanyRegistry(companies, next, market);
    }

    public CompanyRegistry settle(SubjectId contractId) {
        EmploymentContract contract = employmentContracts.get(Objects.requireNonNull(contractId, "employment contract id"));
        if (contract == null) throw new IllegalArgumentException("unknown employment contract");
        Map<SubjectId, EmploymentContract> next = new LinkedHashMap<>(employmentContracts); next.put(contractId, contract.settleOneCommittedJob());
        return new CompanyRegistry(companies, next, market);
    }

    public CompanyRegistry terminate(SubjectId contractId) {
        EmploymentContract contract = employmentContracts.get(Objects.requireNonNull(contractId, "employment contract id"));
        if (contract == null) throw new IllegalArgumentException("unknown employment contract");
        Map<SubjectId, EmploymentContract> next = new LinkedHashMap<>(employmentContracts); next.put(contractId, contract.terminate());
        return new CompanyRegistry(companies, next, market);
    }

    public CompanyRegistry withMarket(MarketOrderBook nextMarket) {
        return new CompanyRegistry(companies, employmentContracts, nextMarket);
    }

    public Optional<Company> activeWorksCompany(SubjectId settlementId) {
        return companies.values().stream().filter(company -> company.settlementId().equals(settlementId)
                && company.purpose() == CompanyPurpose.WORKS && company.status() == CompanyStatus.ACTIVE)
                .sorted(Comparator.comparing(Company::id)).findFirst();
    }

    public Optional<EmploymentContract> activeEmployment(SubjectId companyId, SubjectId residentId) {
        return employmentContracts.values().stream().filter(contract -> contract.companyId().equals(companyId)
                && contract.residentId().equals(residentId) && contract.status() == EmploymentContractStatus.ACTIVE)
                .sorted(Comparator.comparing(EmploymentContract::id)).findFirst();
    }
}
