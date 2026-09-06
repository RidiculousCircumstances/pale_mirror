package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateSettlementsTest {
    @Test
    void restoresEveryNamedResidentAndSettlementFieldWithoutAdvancingTheSharedRng() {
        ReferenceWorld source = thirtyDayWorld();
        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.object(document.get("reference_state"), "reference state");

        ReferenceWorld restored = new ReferenceWorld(source.config());
        restored.populationRng().restore(source.populationRng().state());
        restored.marketWorld().settlements().clear();
        ReferenceGrayboxStateSettlements.restore(reference.get("settlements"), restored.profile(), restored.populationRng())
                .values().forEach(restored.marketWorld()::addSettlement);

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateSettlements.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateSettlements.capture(restored)));
        assertArrayEquals(source.populationRng().state().words(), restored.populationRng().state().words());
    }

    @Test
    void rejectsAResidentRosterWhoseAggregatePopulationWasTampered() {
        ReferenceWorld source = thirtyDayWorld();
        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = mutableMap(document.get("reference_state"));
        Map<String, Object> settlements = mutableMap(reference.get("settlements"));
        List<Object> pairs = mutableList(settlements.get("$map"));
        List<Object> pair = mutableList(pairs.getFirst());
        Map<String, Object> typed = mutableMap(pair.get(1));
        Map<String, Object> fields = mutableMap(typed.get("fields"));
        fields.put("population", 999.0d);
        typed.put("fields", fields);
        pair.set(1, typed);
        pairs.set(0, pair);
        settlements.put("$map", pairs);
        reference.put("settlements", settlements);

        ReferenceWorld target = new ReferenceWorld(source.config());
        target.populationRng().restore(source.populationRng().state());
        assertThrows(IllegalArgumentException.class, () ->
                ReferenceGrayboxStateSettlements.restore(reference.get("settlements"), target.profile(), target.populationRng()));
    }

    private static ReferenceWorld thirtyDayWorld() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        for (int day = 0; day < 30; day++) world.tick();
        return world;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Object value) {
        Map<?, ?> source = (Map<?, ?>) value;
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, nested) -> result.put((String) key, mutableCopy(nested)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> mutableList(Object value) {
        List<?> source = (List<?>) value;
        List<Object> result = new ArrayList<>(source.size());
        source.forEach(nested -> result.add(mutableCopy(nested)));
        return result;
    }

    private static Object mutableCopy(Object value) {
        if (value instanceof Map<?, ?>) return mutableMap(value);
        if (value instanceof List<?>) return mutableList(value);
        return value;
    }
}
