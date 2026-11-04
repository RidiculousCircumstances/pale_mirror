package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Scoped bounded observations, independent of JVM-wide JFR retention. Only the worker touches disk. */
final class CombatTraceCapture {
    private static final Gson JSON = new Gson();
    private static final int MAX_EVENTS = 50_000;
    private static final long MAX_EXPORT_BYTES = 128L * 1024 * 1024;
    private static final ThreadPoolExecutor FILES = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), task -> { var thread=new Thread(task,"pm-combat-trace-files");thread.setDaemon(true);return thread; });
    static { FILES.allowCoreThreadTimeOut(true); }
    private record Observation(String line,int bytes) { }
    private record Closure(long expected,String reason) { }
    final String id = UUID.randomUUID().toString();
    final Path json;
    private final ArrayBlockingQueue<Observation> observations=new ArrayBlockingQueue<>(1024);
    private final CompletableFuture<Void> opened=new CompletableFuture<>();
    private final CompletableFuture<Path> saved;
    private volatile long deadline=Long.MAX_VALUE,sequence;
    private volatile Closure closure;
    private volatile boolean complete;
    CombatTraceCapture(Path directory,int seconds,JsonObject metadata) {
        if(seconds<1 || seconds>600)throw new IllegalArgumentException("trace duration must be 1..600 seconds");
        json=directory.resolve("combat-"+id+".jsonl");
        var header=metadata.deepCopy();header.addProperty("schema",1);header.addProperty("kind","capture_start");header.addProperty("capture",id);
        header.addProperty("requestedAt",Instant.now().toString());header.addProperty("durationSeconds",seconds);
        saved=CompletableFuture.supplyAsync(()->write(directory,seconds,header),FILES);
        saved.whenComplete((path,failure)->{if(failure!=null)opened.completeExceptionally(failure);});
    }
    boolean running() {return opened.isDone() && !opened.isCompletedExceptionally() && closure==null && !saved.isDone();}
    boolean expired() {return System.nanoTime()>=deadline;}
    boolean full() {return sequence>=MAX_EVENTS;}
    boolean finished() {return saved.isDone();}
    String reason() {var end=closure;return end==null?"OPEN_FAILED":end.reason();}
    synchronized void record(String kind,JsonObject payload) {
        if(!running() || full())return;
        var row=payload.deepCopy();row.addProperty("sequence",++sequence);row.addProperty("capture",id);row.addProperty("kind",kind);
        row.addProperty("at",Instant.now().toString());row.addProperty("observedNanos",System.nanoTime());
        String line=JSON.toJson(row);
        if(line.length()>8192)throw new IllegalArgumentException("oversized combat diagnostic row");
        if(!observations.offer(new Observation(line,line.getBytes(StandardCharsets.UTF_8).length+1)))finish("QUEUE_LIMIT");
        else if(full())finish("EVENT_LIMIT");
    }
    synchronized void finish(String reason) {if(closure==null)closure=new Closure(sequence,reason);}
    String status() {
        if(saved.isCompletedExceptionally()) {
            try {saved.join();}catch(CompletionException failure){return "FAILED: "+failure.getCause();}
        }
        return (saved.isDone()?complete?"SAVED":"INCOMPLETE":closure!=null?"EXPORTING":opened.isDone()?"RECORDING":"STARTING")
                +" capture="+id+" events="+sequence+" json="+json;
    }
    CompletionStage<Void> readiness() {return opened;}
    CompletionStage<Path> completion() {return saved;}
    String awaitShutdown() {
        try {saved.get(10,TimeUnit.SECONDS);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return "save interrupted: "+json;}
        catch(ExecutionException | TimeoutException failure){return "save incomplete: "+failure;}
        return status();
    }
    private Path write(Path directory,int seconds,JsonObject header) {
        try {
            Files.createDirectories(directory);
            try(var files=Files.list(directory)) {
                if(files.filter(p->p.getFileName().toString().matches("combat-[0-9a-f-]{36}\\.jsonl")).count()>=16)
                    throw new IOException("16 combat captures retained; archive completed captures before starting another");
            }
            try(var output=Files.newBufferedWriter(json,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                header.addProperty("startedAt",Instant.now().toString());output.write(JSON.toJson(header));output.newLine();output.flush();
                deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);opened.complete(null);
                long rows=0,bytes=0;boolean limited=false;
                while(closure==null || !observations.isEmpty()) {
                    if(expired())finish("DURATION");
                    var observation=observations.poll(50,TimeUnit.MILLISECONDS);if(observation==null)continue;
                    bytes+=observation.bytes();
                    if(bytes>MAX_EXPORT_BYTES){limited=true;finish("EXPORT_LIMIT");continue;}
                    output.write(observation.line());output.newLine();rows++;
                }
                var end=Objects.requireNonNull(closure);var footer=new JsonObject();
                footer.addProperty("kind","capture_end");footer.addProperty("capture",id);footer.addProperty("reason",end.reason());
                footer.addProperty("expectedEvents",end.expected());footer.addProperty("writtenEvents",rows);
                footer.addProperty("dataLoss",rows!=end.expected());footer.addProperty("exportLimit",limited);
                boolean cleanEnd=!Set.of("CAPTURE_ERROR","OPEN_FAILED","EVENT_LIMIT","QUEUE_LIMIT","EXPORT_LIMIT","TEST_FAILED").contains(end.reason());
                complete=cleanEnd && !limited && rows==end.expected();footer.addProperty("complete",complete);
                output.write(JSON.toJson(footer));output.newLine();
            }
            return json;
        }catch(IOException failure){throw new UncheckedIOException(failure);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("combat export interrupted",interrupted);}
    }
}
