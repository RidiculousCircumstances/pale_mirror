package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierWireTagsTest {
    @Test
    void explicitTagsPreserveHistoricCodesWithoutEnumDeclarationOrder() {
        assertEquals(0, ActorLifeStatus.ALIVE.wireTag());
        assertEquals(1, ActorLifeStatus.DEAD.wireTag());
        assertEquals(5, SceneLeaseStatus.CONFLICT.wireTag());
        assertEquals(13, PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL.wireTag());
        assertEquals(3, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART.wireTag());
        assertEquals(13, PhysicalPostcondition.HIVE_NUTRIENT_ARRIVED_OBSERVED.wireTag());
        assertEquals(10, HiveOrganKind.GANGLION.wireTag());
        assertEquals(11, HiveOrganKind.RELAY.wireTag());
        assertThrows(IllegalArgumentException.class,
                () -> FrontierWireTags.require(HiveOrganKind.class, 0),
                "the retired HEART byte must never become GANGLION");
        assertEquals(RouteEngagementStatus.UNKNOWN_AFTER_RESTART,
                FrontierWireTags.require(RouteEngagementStatus.class, 5));
        assertEquals(PhysicalIntentKind.CARGO_LOADING,
                FrontierWireTags.require(PhysicalIntentKind.class, 10));
        assertThrows(IllegalArgumentException.class,
                () -> FrontierWireTags.require(SceneLeaseStatus.class, 127));
    }

    @Test
    void everyRegisteredEnumValueRoundTripsThroughItsExplicitTag() {
        for (Class<?> type : FrontierWireTags.types()) assertRoundTrip(type);
    }

    @Test
    void historicCompanyPayloadBytesDecodeWithTheirOriginalTags() {
        Company company = new Company(new SubjectId("company:legacy"), new SubjectId("settlement:legacy"),
                new SubjectId("resident:legacy"), CompanyPurpose.WORKS, CompanyStatus.DISSOLVED, 42L);
        byte[] historic = FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, company.id());
            FrontierWorldPayloadCodecs.writeSubject(output, company.settlementId());
            FrontierWorldPayloadCodecs.writeSubject(output, company.founderId());
            output.writeByte(0); // historic CompanyPurpose.WORKS tag
            output.writeByte(2); // historic CompanyStatus.DISSOLVED tag
            output.writeLong(company.registeredAtTick());
        });

        assertEquals(new CompanyRegistered(company), FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                "frontier.company_registered", historic));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertRoundTrip(Class<?> type) {
        for (Object candidate : type.getEnumConstants()) {
            Enum<?> value = (Enum<?>) candidate;
            assertEquals(value, FrontierWireTags.require((Class) type, FrontierWireTags.tag(value)));
        }
    }
}
