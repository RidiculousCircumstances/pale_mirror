package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real action boundary, without a Minecraft client or fabricated native receipt. */
class FrontierV3HarvestObservationScopeTest {
    @Test void completingAnActionDropsPriorHarvestPassAndIdentity() throws Exception {
        Class<?> client = FrontierV3TestPilotClient.class;
        Field oracle = field(client, "harvestSemanticOracle");
        Field setup = field(client, "setup");
        Field running = field(client, "runningSetup");
        Field index = field(client, "index");
        Object oldOracle = oracle.get(null), oldSetup = setup.get(null);
        boolean oldRunning = running.getBoolean(null); int oldIndex = index.getInt(null);
        var type = oracle.getType();
        var constructor = type.getDeclaredConstructor(com.google.gson.JsonObject.class);
        constructor.setAccessible(true);
        var advance = client.getDeclaredMethod("advance", String.class); advance.setAccessible(true);
        try {
            // Stay in setup so unrelated frame/connection-completion effects are not invoked.
            var steps = new JsonArray(); steps.add("first"); steps.add("second"); steps.add("remaining");
            setup.set(null, steps); running.setBoolean(null, true); index.setInt(null, 0);
            for (int cycle = 0; cycle < 2; cycle++) {
                var observer = constructor.newInstance(JsonParser.parseString("""
                        {"siteId":"site:7-wheat-field","durationTicks":12000,
                         "sampleEveryTicks":20,"maxCanonicalStallTicks":1200}
                        """).getAsJsonObject());
                field(type, "passed").setBoolean(observer, true);
                field(type, "jobId").set(observer, "job:previous-cycle-" + cycle);
                oracle.set(null, observer);
                advance.invoke(null, "observe_harvest_semantics");
                assertNull(oracle.get(null), "the next observation must not inherit PASS or the prior job");
            }
        } finally {
            oracle.set(null, oldOracle); setup.set(null, oldSetup);
            running.setBoolean(null, oldRunning); index.setInt(null, oldIndex);
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field value = owner.getDeclaredField(name); value.setAccessible(true); return value;
    }
}
