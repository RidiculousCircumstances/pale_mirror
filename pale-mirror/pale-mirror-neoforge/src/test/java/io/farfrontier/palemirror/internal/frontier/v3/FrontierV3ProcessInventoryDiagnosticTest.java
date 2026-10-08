package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ProcessInventoryDiagnosticTest {
    @Test void realConcurrentFieldSupportsExactSiteSelectionAndRejectsInvalidCursors() {
        var configuration = FrontierV3FixtureCatalog.concurrentFieldHarvestConfiguration(new WorldId("frontier:inventory-page"), 91L);
        var checkpoint = FrontierEngines.create(configuration).checkpoint();
        var state = configuration.initialState();
        var json = JsonParser.parseString(FrontierV3ProcessInventoryDiagnostic.render("site:1-wheat-field", checkpoint, state)).getAsJsonObject();
        assertEquals("ok", json.get("status").getAsString());
        assertEquals(2, json.get("count").getAsInt());
        assertEquals(2, json.getAsJsonArray("entries").size());
        assertTrue(json.get("complete").getAsBoolean());
        assertTrue(json.get("nextId").isJsonNull());
        for (String selector : java.util.List.of("settlements@-1", "settlements@", "settlements@9999999999999999", "site:1-wheat-field@2"))
            assertEquals("invalid_page", JsonParser.parseString(FrontierV3ProcessInventoryDiagnostic.render(selector, checkpoint, state))
                    .getAsJsonObject().get("status").getAsString());
        assertEquals("not_found", JsonParser.parseString(FrontierV3ProcessInventoryDiagnostic.render("site:missing", checkpoint, state))
                .getAsJsonObject().get("status").getAsString());
        assertEquals(state, configuration.initialState());
    }

    @Test void oversizedMultibyteInventoryPagesRemainBoundedOrderedAndExplicitlyIncomplete() {
        var configuration = FrontierV3FixtureCatalog.concurrentFieldHarvestConfiguration(new WorldId("frontier:inventory-page-bound"), 91L);
        var checkpoint = FrontierEngines.create(configuration).checkpoint();
        var entries = new ArrayList<FrontierV3ProcessInventoryDiagnostic.Entry>();
        // Rendering fixture only: multibyte labels expose byte-vs-character mistakes without inventing domain work.
        for (int index = 0; index < 36; index++) entries.add(new FrontierV3ProcessInventoryDiagnostic.Entry(index % 2 == 0,
                "{\"row\":" + index + ",\"label\":\"" + "пшеница".repeat(90) + "\"}"));
        int offset = 0; int pages = 0;
        do {
            String id = offset == 0 ? "settlements" : "settlements@" + offset;
            String result = FrontierV3ProcessInventoryDiagnostic.renderPage(id, "settlements", offset, checkpoint, entries);
            assertTrue((FrontierV3DiagnosticJson.PREFIX + result).getBytes(StandardCharsets.UTF_8).length <= FrontierV3DiagnosticJson.MAX_BYTES);
            var page = JsonParser.parseString(result).getAsJsonObject();
            assertEquals("ok", page.get("status").getAsString());
            assertEquals(36, page.get("count").getAsInt());
            assertEquals(18, page.get("coldEligible").getAsInt());
            assertEquals(offset, page.get("offset").getAsInt());
            assertEquals(checkpoint.revision().value(), page.get("revision").getAsLong());
            var rows = page.getAsJsonArray("entries");
            assertFalse(rows.isEmpty());
            assertEquals(rows.size(), page.get("returnedCount").getAsInt());
            for (var row : rows) assertEquals(offset++, row.getAsJsonObject().get("row").getAsInt());
            if (offset == entries.size()) {
                assertTrue(page.get("complete").getAsBoolean());
                assertTrue(page.get("nextId").isJsonNull());
            } else {
                assertFalse(page.get("complete").getAsBoolean());
                assertEquals("settlements@" + offset, page.get("nextId").getAsString());
            }
            pages++;
        } while (offset < entries.size());
        assertTrue(pages > 1);
    }
}
