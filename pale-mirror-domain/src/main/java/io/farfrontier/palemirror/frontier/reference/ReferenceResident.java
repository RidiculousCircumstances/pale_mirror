package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/**
 * One concrete person owned by a {@link ReferenceResidentLedger}.
 *
 * <p>State-changing methods intentionally have package visibility: population,
 * operation and migration ports must share the same canonical person, while
 * callers outside the reference domain may inspect but cannot manufacture an
 * alternate custody state.</p>
 */
public final class ReferenceResident {
    private final String id;
    private int homeSettlementId;
    private final String occupation;
    private final String economicClass;
    private Integer employerCompanyId;
    private ReferenceResidentLocation location = ReferenceResidentLocation.SETTLEMENT;
    private Integer locationRef;
    private ReferenceResidentCondition condition = ReferenceResidentCondition.ACTIVE;
    private String deploymentRole;

    ReferenceResident(String id, int homeSettlementId, String occupation, String economicClass) {
        this.id = Objects.requireNonNull(id, "id");
        this.homeSettlementId = homeSettlementId;
        this.occupation = Objects.requireNonNull(occupation, "occupation");
        this.economicClass = Objects.requireNonNull(economicClass, "economicClass");
    }

    public String id() {
        return id;
    }

    public int homeSettlementId() {
        return homeSettlementId;
    }

    public String occupation() {
        return occupation;
    }

    public String economicClass() {
        return economicClass;
    }

    public Integer employerCompanyId() {
        return employerCompanyId;
    }

    public ReferenceResidentLocation location() {
        return location;
    }

    public Integer locationRef() {
        return locationRef;
    }

    public ReferenceResidentCondition condition() {
        return condition;
    }

    public String deploymentRole() {
        return deploymentRole;
    }

    public boolean available() {
        return condition == ReferenceResidentCondition.ACTIVE
                && location == ReferenceResidentLocation.SETTLEMENT;
    }

    void assignEmployer(Integer companyId) {
        employerCompanyId = companyId;
    }

    void wound() {
        condition = ReferenceResidentCondition.WOUNDED;
        deploymentRole = null;
    }

    void recover() {
        condition = ReferenceResidentCondition.ACTIVE;
        deploymentRole = null;
    }

    void moveToOperation(int operationId, String role) {
        location = ReferenceResidentLocation.OPERATION;
        locationRef = operationId;
        deploymentRole = role;
    }

    void moveToSettlement() {
        location = ReferenceResidentLocation.SETTLEMENT;
        locationRef = null;
        clearDeploymentRole();
    }

    void moveToFieldPost(int postId) {
        location = ReferenceResidentLocation.FIELD_POST;
        locationRef = postId;
        if (condition == ReferenceResidentCondition.WOUNDED) {
            deploymentRole = null;
        }
    }

    void moveWoundedToOperation(int operationId) {
        location = ReferenceResidentLocation.OPERATION;
        locationRef = operationId;
        clearDeploymentRole();
    }

    void acceptIntoSettlement(int settlementId) {
        homeSettlementId = settlementId;
        moveToSettlement();
        employerCompanyId = null;
    }

    void clearDeploymentRole() {
        deploymentRole = null;
    }
}
