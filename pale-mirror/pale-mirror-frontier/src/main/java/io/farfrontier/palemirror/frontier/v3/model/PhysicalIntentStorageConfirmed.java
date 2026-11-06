package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
/** One receipt for an owned physical consequence and its exact observed storage successor. Command only. */
public record PhysicalIntentStorageConfirmed(PhysicalIntent expected,PhysicalEffectObservation observation,
        PhysicalCustodyLease storage,String fingerprint,String provenance) implements FrontierPayload {
    public static final String TYPE="frontier.physical_intent_storage_confirmed";
    public PhysicalIntentStorageConfirmed {
        Objects.requireNonNull(expected);Objects.requireNonNull(observation);Objects.requireNonNull(storage);
        Objects.requireNonNull(fingerprint);Objects.requireNonNull(provenance);
        if(!expected.id().equals(observation.intentId())
                || !expected.roles().require(PhysicalIntentSubjectRole.RESOURCE_CONTAINER).equals(storage.objectId())
                || !storage.providerId().equals(ReferenceContainerCustody.PROVIDER_ID)
                || !storage.scopeId().equals(ReferenceContainerCustody.scopeId(storage.objectId()))
                || storage.status()!=PhysicalCustodyLeaseStatus.ACQUIRED && storage.status()!=PhysicalCustodyLeaseStatus.CHECKPOINTED
                || fingerprint.isBlank() || fingerprint.length()>4096 || provenance.length()>4096)
            throw new IllegalArgumentException("physical storage receipt lacks its complete declared intent/container/provider/epoch");
    }
    public String type(){return TYPE;}
    public boolean requiresDurableBeforeEffect(){return true;}
}
