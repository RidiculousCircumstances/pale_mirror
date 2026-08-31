package io.farfrontier.palemirror.frontier.v3.model;

/** Durable vocational affinity; it is neither a current task nor a combat class. */
public enum ResidentProfession {
    AGRICULTURAL_WORKER(HumanCapability.AGRICULTURE),
    EXTRACTOR(HumanCapability.EXTRACTION),
    INDUSTRIAL_WORKER(HumanCapability.INDUSTRY),
    ENGINEER(HumanCapability.ENGINEERING),
    LOGISTICIAN(HumanCapability.LOGISTICS),
    MEDICAL_WORKER(HumanCapability.MEDICINE),
    SECURITY_WORKER(HumanCapability.SECURITY),
    CIVIC_WORKER(HumanCapability.CIVIC);

    private final HumanCapability primaryCapability;

    ResidentProfession(HumanCapability primaryCapability) { this.primaryCapability = primaryCapability; }

    public HumanCapability primaryCapability() { return primaryCapability; }

    /** Deterministic migration for the provisional six-value bootstrap affinity. */
    public static ResidentProfession fromBootstrapAffinity(ResidentRole role) {
        return switch (role) {
            case FARMER -> AGRICULTURAL_WORKER;
            case BUILDER -> ENGINEER;
            case CRAFTER -> INDUSTRIAL_WORKER;
            case GUARD -> SECURITY_WORKER;
            case MEDIC -> MEDICAL_WORKER;
            case HAULER -> LOGISTICIAN;
        };
    }

    public int wireTag() { return FrontierWireTags.tag(this); }
}
