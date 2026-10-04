package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PilotDiagnosticMatcherTest {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    @Test void exactConfirmedHarvestDoesNotDependOnWholeFieldEpochOrAnotherWorker() {
        JsonObject site = json("""
                {"id":"site:field","status":"ok","phase":"HARVESTING","growthEpoch":1,"terminalHarvest":[
                 {"job":"job:harvest","worker":"resident:farmer","intent":"intent:harvest",
                  "outputItem":"item:wheat","intentStatus":"CONFIRMED", "physicalReceiptResolved":true,
                  "physicalReceiptConfirmed":true}]}
                """);
        JsonObject intent = json("""
                {"id":"intent:harvest","causeSubject":"site:field","status":"ok",
                 "intentStatus":"CONFIRMED","intentKind":"RESOURCE_SITE_HARVEST",
                 "receiptId":"receipt:harvest","subjects":["job:harvest","resident:farmer","site:field"]}
                """);
        assertTrue(FrontierV3PilotDiagnosticMatcher.harvestComplete(site, intent, "item:wheat"));
        assertFalse(FrontierV3PilotDiagnosticMatcher.harvestComplete(site, intent, "item:other"));
        intent.addProperty("id", "intent:other");
        assertFalse(FrontierV3PilotDiagnosticMatcher.harvestComplete(site, intent, "item:wheat"));
        intent.addProperty("id", "intent:harvest");
        intent.addProperty("causeSubject", "site:other");
        assertFalse(FrontierV3PilotDiagnosticMatcher.harvestComplete(site, intent, "item:wheat"));
        intent.addProperty("causeSubject", "site:field");
        intent.add("subjects", json("{\"subjects\":[\"job:harvest\",\"resident:other\"]}").get("subjects"));
        assertFalse(FrontierV3PilotDiagnosticMatcher.harvestComplete(site, intent, "item:wheat"));
        intent.add("subjects", json("{\"subjects\":[\"job:harvest\",\"resident:farmer\",\"site:field\"]}").get("subjects"));
        site.getAsJsonArray("terminalHarvest").get(0).getAsJsonObject().addProperty("physicalReceiptConfirmed", false);
        assertFalse(FrontierV3PilotDiagnosticMatcher.harvestComplete(site, intent, "item:wheat"));
        site.getAsJsonArray("terminalHarvest").get(0).getAsJsonObject().addProperty("physicalReceiptConfirmed", true);
        intent.addProperty("intentStatus", "PREPARED");
        assertFalse(FrontierV3PilotDiagnosticMatcher.harvestComplete(site, intent, "item:wheat"));
    }

    @Test void exactAndObservedFungibleStacksAreBothAdmissibleButStalePhysicalCustodyIsNot() {
        JsonObject action = json("""
                {"item":"minecraft:wheat","count":63,"slot":0}
                """);
        JsonObject exact = json("""
                {"status":"ok","occupied":[{"slot":0,"itemKind":"minecraft:wheat","count":63}]}
                """);
        assertTrue(FrontierV3PilotDiagnosticMatcher.containerContains(exact, action));

        JsonObject fungible = json("""
                {"status":"ok","occupied":[],"fungibleOccupied":[{"slot":0,"itemKind":"minecraft:wheat","count":63}],
                 "replica":{"state":"OBSERVED_CURRENT"},"physicalSocket":{"chest":"OWNED","slots":"CURRENT"}}
                """);
        assertTrue(FrontierV3PilotDiagnosticMatcher.containerContains(fungible, action));
        assertFalse(FrontierV3PilotDiagnosticMatcher.containerContains(fungible,
                json("""
                        {"item":"minecraft:wheat","count":64,"slot":0}
                        """)));
        assertFalse(FrontierV3PilotDiagnosticMatcher.containerContains(fungible,
                json("""
                        {"item":"minecraft:wheat","count":63,"slot":1}
                        """)));

        fungible.getAsJsonObject("replica").addProperty("state", "CONFLICT");
        assertFalse(FrontierV3PilotDiagnosticMatcher.containerContains(fungible, action));
        fungible.getAsJsonObject("replica").addProperty("state", "OBSERVED_CURRENT");
        fungible.getAsJsonObject("physicalSocket").addProperty("slots", "DIVERGED");
        assertFalse(FrontierV3PilotDiagnosticMatcher.containerContains(fungible, action));
    }
}
