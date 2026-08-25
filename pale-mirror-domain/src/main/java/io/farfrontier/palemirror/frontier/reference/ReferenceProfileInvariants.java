package io.farfrontier.palemirror.frontier.reference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact profile-level ownership checks required before accepting physical observations. */
final class ReferenceProfileInvariants {
    private ReferenceProfileInvariants() { }

    static void assertValid(ReferenceWorld world) {
        if (!world.profile().discretePeople()) return;
        Map<ResidentCustodyKey, String> custodians = new HashMap<>();
        validateOperations(world, custodians);
        validateFieldPosts(world, custodians);
        validateV2Campaigns(world, custodians);
        validateSettlements(world, custodians);
        validateCompanies(world);
        validateSwarms(world);
    }

    private static void validateOperations(ReferenceWorld world, Map<ResidentCustodyKey, String> custodians) {
        for (ReferenceOperation operation : world.operations().active()) {
            if (operation.side() != ReferenceAgentKind.SETTLEMENT) continue;
            for (Map.Entry<Integer, List<String>> entry : operation.residentIdsBySettlement().entrySet()) {
                claimResidents(world, custodians, entry.getKey(), entry.getValue(), ReferenceResidentLocation.OPERATION, operation.id(),
                        ReferenceResidentCondition.ACTIVE, "operation " + operation.id());
                if (operation.personnelBySettlement().getOrDefault(entry.getKey(), 0.0d) != entry.getValue().size()) {
                    throw new IllegalStateException("operation " + operation.id() + " has a stale active-personnel projection");
                }
            }
            for (Map.Entry<Integer, List<String>> entry : operation.woundedResidentIdsBySettlement().entrySet()) {
                claimResidents(world, custodians, entry.getKey(), entry.getValue(), ReferenceResidentLocation.OPERATION, operation.id(),
                        ReferenceResidentCondition.WOUNDED, "operation " + operation.id());
                if (operation.evacuatedWoundedBySettlement().getOrDefault(entry.getKey(), 0.0d) != entry.getValue().size()) {
                    throw new IllegalStateException("operation " + operation.id() + " has a stale wounded-personnel projection");
                }
            }
        }
    }

    private static void validateFieldPosts(ReferenceWorld world, Map<ResidentCustodyKey, String> custodians) {
        for (ReferenceFieldPost post : world.field().posts().values()) {
            for (Map.Entry<Integer, List<String>> entry : post.residentIdsBySettlement().entrySet()) {
                claimResidents(world, custodians, entry.getKey(), entry.getValue(), ReferenceResidentLocation.FIELD_POST, post.id(),
                        ReferenceResidentCondition.ACTIVE, "field post " + post.id());
                if (post.garrisonBySettlement().getOrDefault(entry.getKey(), 0.0d) != entry.getValue().size()) {
                    throw new IllegalStateException("field post " + post.id() + " has a stale active-garrison projection");
                }
            }
            for (Map.Entry<Integer, List<String>> entry : post.woundedResidentIdsBySettlement().entrySet()) {
                claimResidents(world, custodians, entry.getKey(), entry.getValue(), ReferenceResidentLocation.FIELD_POST, post.id(),
                        ReferenceResidentCondition.WOUNDED, "field post " + post.id());
                if (post.woundedBySettlement().getOrDefault(entry.getKey(), 0.0d) != entry.getValue().size()) {
                    throw new IllegalStateException("field post " + post.id() + " has a stale wounded-garrison projection");
                }
            }
        }
    }

    private static void validateV2Campaigns(ReferenceWorld world, Map<ResidentCustodyKey, String> custodians) {
        if (!world.v2Enabled()) return;
        for (ReferenceFrontCampaign campaign : world.v2().frontCampaigns().values()) {
            for (Map.Entry<Integer, List<String>> entry : campaign.residentIdsBySettlement().entrySet()) {
                claimResidents(world, custodians, entry.getKey(), entry.getValue(), ReferenceResidentLocation.OPERATION,
                        1_000_000 + campaign.id(), ReferenceResidentCondition.ACTIVE, "front campaign " + campaign.id());
                if (campaign.personnelBySettlement().getOrDefault(entry.getKey(), 0.0d) != entry.getValue().size()) {
                    throw new IllegalStateException("front campaign " + campaign.id() + " has a stale personnel projection");
                }
            }
        }
    }

    private static void validateSettlements(ReferenceWorld world, Map<ResidentCustodyKey, String> custodians) {
        for (ReferenceSettlement settlement : world.marketWorld().settlements().values()) {
            if (settlement.residents() == null) throw new IllegalStateException("graybox settlement has no resident ledger: " + settlement.id());
            settlement.residents().assertValid();
            if (settlement.population() != settlement.residents().size()) {
                throw new IllegalStateException("settlement population projection is stale: " + settlement.id());
            }
            if (settlement.woundedPersonnel() != settlement.residents().woundedIds().size()) {
                throw new IllegalStateException("settlement wounded projection is stale: " + settlement.id());
            }
            for (String residentId : settlement.residents().livingIds()) {
                ReferenceResident resident = settlement.residents().resident(residentId);
                ResidentCustodyKey key = new ResidentCustodyKey(settlement.id(), residentId);
                if (resident.location() == ReferenceResidentLocation.SETTLEMENT) {
                    if (resident.locationRef() != null) {
                        throw new IllegalStateException("resident " + residentId + " has a settlement location reference");
                    }
                    if (custodians.containsKey(key)) {
                        throw new IllegalStateException("resident " + residentId + " is both home and owned by " + custodians.get(key));
                    }
                } else if (!custodians.containsKey(key)) {
                    throw new IllegalStateException("resident " + residentId + " is at " + resident.location() + ":"
                            + resident.locationRef() + " without a custodian");
                }
            }
        }
    }

    private static void validateCompanies(ReferenceWorld world) {
        for (ReferenceCompany company : world.microeconomy().companies().values()) {
            ReferenceSettlement settlement = world.marketWorld().settlements().get(company.homeSettlementId());
            if (settlement == null || settlement.residents() == null) {
                throw new IllegalStateException("company " + company.id() + " has no discrete home settlement");
            }
            if (company.employees() != company.employeeIds().size()) {
                throw new IllegalStateException("company " + company.id() + " has a stale employee-count projection");
            }
            for (String residentId : company.employeeIds()) {
                ReferenceResident resident = settlement.residents().resident(residentId);
                if (resident == null || !resident.economicClass().equals("worker") || !Integer.valueOf(company.id()).equals(resident.employerCompanyId())) {
                    throw new IllegalStateException("company " + company.id() + " retains invalid employee " + residentId);
                }
            }
        }
    }

    private static void validateSwarms(ReferenceWorld world) {
        for (ReferenceSwarm swarm : world.infection().swarms()) {
            if (swarm.composition().isEmpty()) throw new IllegalStateException("swarm " + swarm.id() + " has no biological composition");
            if (swarm.composition().values().stream().anyMatch(amount -> amount <= 0.0d || amount != Math.rint(amount))) {
                throw new IllegalStateException("swarm " + swarm.id() + " contains a fractional biological entity");
            }
            swarm.assertDiscreteBioformInvariants();
        }
    }

    private static void claimResidents(
            ReferenceWorld world,
            Map<ResidentCustodyKey, String> custodians,
            int settlementId,
            List<String> residentIds,
            ReferenceResidentLocation location,
            int reference,
            ReferenceResidentCondition condition,
            String owner
    ) {
        ReferenceSettlement settlement = world.marketWorld().settlements().get(settlementId);
        if (settlement == null || settlement.residents() == null) {
            throw new IllegalStateException(owner + " references non-discrete settlement " + settlementId);
        }
        for (String residentId : residentIds) {
            ResidentCustodyKey key = new ResidentCustodyKey(settlementId, residentId);
            String previous = custodians.putIfAbsent(key, owner);
            if (previous != null) {
                throw new IllegalStateException("resident " + residentId + " from settlement " + settlementId
                        + " has two custodians: " + previous + " and " + owner);
            }
            ReferenceResident resident = settlement.residents().resident(residentId);
            if (resident == null) throw new IllegalStateException(owner + " retains dead or unknown resident " + residentId);
            if (resident.location() != location || !Integer.valueOf(reference).equals(resident.locationRef()) || resident.condition() != condition) {
                throw new IllegalStateException(owner + " disagrees with resident " + residentId + ": expected "
                        + location + ":" + reference + "/" + condition + ", got " + resident.location() + ":"
                        + resident.locationRef() + "/" + resident.condition());
            }
        }
    }

    private record ResidentCustodyKey(int settlementId, String residentId) { }
}
