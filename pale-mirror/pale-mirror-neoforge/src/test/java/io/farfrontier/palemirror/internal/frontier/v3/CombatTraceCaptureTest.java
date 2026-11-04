package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class CombatTraceCaptureTest {
    @TempDir Path directory;
    @Test void recordingExportsExactObservedRowsAndClosesWithoutAcceptingLaterEvents()throws Exception {
        var metadata=new JsonObject();metadata.addProperty("playerName","тест");
        var capture=new CombatTraceCapture(directory,30,metadata);
        capture.readiness().toCompletableFuture().get(15,TimeUnit.SECONDS);
        var position=new JsonObject();position.addProperty("x",1.25);position.addProperty("uuid","observed-body");
        capture.record("position",position);position.addProperty("x",7.5);capture.record("damage_applied",position);
        capture.finish("OPERATOR_STOP");capture.record("must_not_appear",position);
        var path=capture.completion().toCompletableFuture().get(15,TimeUnit.SECONDS);
        var rows=Files.readAllLines(path).stream().map(line->JsonParser.parseString(line).getAsJsonObject()).toList();
        assertEquals(4,rows.size());assertEquals("тест",rows.getFirst().get("playerName").getAsString());
        assertEquals(1.25,rows.get(1).get("x").getAsDouble());assertEquals(1,rows.get(1).get("sequence").getAsInt());
        assertEquals("damage_applied",rows.get(2).get("kind").getAsString());assertEquals(7.5,rows.get(2).get("x").getAsDouble());
        assertTrue(rows.getLast().get("complete").getAsBoolean());assertEquals("OPERATOR_STOP",rows.getLast().get("reason").getAsString());
        assertTrue(Files.size(capture.jfr)>0);assertTrue(capture.status().startsWith("SAVED"));
        capture.finish("DUPLICATE_STOP");assertEquals(rows.size(),Files.readAllLines(path).size());
    }
    @Test void durationStopsTheActualRecorderAndStillProducesATerminalCompletenessReceipt()throws Exception {
        var capture=new CombatTraceCapture(directory,1,new JsonObject());
        capture.readiness().toCompletableFuture().get(15,TimeUnit.SECONDS);
        capture.record("position",new JsonObject());
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),()->{
            while(!capture.expired())java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        });
        capture.finish("DURATION");var path=capture.completion().toCompletableFuture().get(15,TimeUnit.SECONDS);
        var lines=Files.readAllLines(path);var end=JsonParser.parseString(lines.getLast()).getAsJsonObject();
        assertTrue(end.get("complete").getAsBoolean());assertEquals(1,end.get("writtenEvents").getAsInt());
    }
}
