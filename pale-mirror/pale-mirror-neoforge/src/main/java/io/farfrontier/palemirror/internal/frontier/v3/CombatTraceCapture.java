package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.*;
import jdk.jfr.*;
import jdk.jfr.consumer.RecordingFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded diagnostic recording only. All disk/JFR lifecycle work runs off the server thread. */
final class CombatTraceCapture {
    private static final Gson JSON = new Gson();
    private static final int MAX_EVENTS = 50_000;
    private static final long MAX_EXPORT_BYTES = 128L * 1024 * 1024;
    private static final ThreadPoolExecutor FILES = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), task -> { var thread=new Thread(task,"pm-combat-trace-files");thread.setDaemon(true);return thread; });
    static { FILES.allowCoreThreadTimeOut(true); }
    @Name("pale_mirror.CombatTrace") @Label("Combat diagnostic observation") @Category("Pale Mirror") @StackTrace(false)
    static final class Sample extends Event {
        String capture, kind, payload;
        long sequence, observedNanos;
    }
    final String id = UUID.randomUUID().toString();
    final Path json, jfr;
    private final JsonObject header;
    private final CompletableFuture<Recording> opened;
    private CompletableFuture<Path> saved;
    private long sequence;
    private boolean closing;
    CombatTraceCapture(Path directory,int seconds,JsonObject metadata) {
        if(seconds<1 || seconds>600)throw new IllegalArgumentException("trace duration must be 1..600 seconds");
        json=directory.resolve("combat-"+id+".jsonl");jfr=directory.resolve("combat-"+id+".jfr");
        header=metadata.deepCopy();header.addProperty("schema",1);header.addProperty("kind","capture_start");header.addProperty("capture",id);
        header.addProperty("requestedAt",Instant.now().toString());header.addProperty("durationSeconds",seconds);
        opened=CompletableFuture.supplyAsync(()->{
            try {
                Files.createDirectories(directory);
                try(var files=Files.list(directory)) {
                    if(files.filter(p->p.getFileName().toString().matches("combat-[0-9a-f-]{36}\\.jfr")).count()>=16)
                        throw new IOException("16 combat captures retained; archive completed captures before starting another");
                }
                var recording=new Recording();
                try {
                    recording.setName("PM combat "+id);recording.setToDisk(true);recording.setMaxSize(32L*1024*1024);
                    recording.setDumpOnExit(true);
                    recording.setDuration(Duration.ofSeconds(seconds));recording.enable(Sample.class).withoutStackTrace();
                    recording.enable("jdk.DataLoss");recording.start();return recording;
                } catch(RuntimeException failure) {recording.close();throw failure;}
            } catch(IOException failure) {throw new UncheckedIOException(failure);}
        },FILES);
    }
    boolean running() {return !closing && opened.isDone() && !opened.isCompletedExceptionally() && opened.join().getState()==RecordingState.RUNNING;}
    boolean expired() {return !closing && opened.isDone() && !opened.isCompletedExceptionally() && opened.join().getState()==RecordingState.STOPPED;}
    boolean full() {return sequence>=MAX_EVENTS;}
    boolean finished() {return saved!=null && saved.isDone() || opened.isCompletedExceptionally();}
    void record(String kind,JsonObject payload) {
        if(!running() || full())return;
        String text=JSON.toJson(payload);
        if(text.length()>8192)throw new IllegalArgumentException("oversized combat diagnostic row");
        var event=new Sample();event.capture=id;event.kind=kind;event.payload=text;
        event.sequence=++sequence;event.observedNanos=System.nanoTime();event.commit();
    }
    void finish(String reason) {
        if(closing)return;
        closing=true;long expected=sequence;
        saved=opened.thenApplyAsync(recording->{
            try(recording) {
                if(recording.getState()==RecordingState.RUNNING)recording.stop();
                recording.dump(jfr);export(expected,reason);return json;
            } catch(IOException failure) {throw new UncheckedIOException(failure);}
        },FILES);
    }
    String status() {
        if(opened.isCompletedExceptionally() || saved!=null && saved.isCompletedExceptionally()) {
            try {if(saved!=null)saved.join();else opened.join();}
            catch(CompletionException failure) {return "FAILED: "+failure.getCause();}
        }
        return (saved!=null?saved.isDone()?"SAVED":"EXPORTING":opened.isDone()?"RECORDING":"STARTING")
                +" capture="+id+" events="+sequence+" json="+json+" jfr="+jfr;
    }
    CompletionStage<Void> readiness() {return opened.thenApply(recording->null);}
    CompletionStage<Path> completion() {if(saved==null)throw new IllegalStateException("capture still active");return saved;}
    String awaitShutdown() {
        try {if(saved!=null)saved.get(10,TimeUnit.SECONDS);}
        catch(InterruptedException interrupted) {Thread.currentThread().interrupt();return "save interrupted; inspect raw JFR: "+jfr;}
        catch(ExecutionException | TimeoutException failure) {return "save incomplete: "+failure;}
        return status();
    }
    private void export(long expected,String reason)throws IOException {
        long rows=0,bytes=0;boolean dataLoss=false,limited=false;long first=Long.MAX_VALUE,last=0;
        try(var input=new RecordingFile(jfr);var output=Files.newBufferedWriter(json,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            output.write(JSON.toJson(header));output.newLine();
            while(input.hasMoreEvents()) {
                var event=input.readEvent();
                if(event.getEventType().getName().equals("jdk.DataLoss")){dataLoss=true;continue;}
                if(!event.getEventType().getName().equals("pale_mirror.CombatTrace") || !event.getString("capture").equals(id))continue;
                var row=JsonParser.parseString(event.getString("payload")).getAsJsonObject();
                long ordinal=event.getLong("sequence");row.addProperty("sequence",ordinal);row.addProperty("capture",id);
                row.addProperty("kind",event.getString("kind"));row.addProperty("at",event.getStartTime().toString());
                row.addProperty("observedNanos",event.getLong("observedNanos"));
                String line=JSON.toJson(row);bytes+=line.getBytes(StandardCharsets.UTF_8).length+1;
                if(bytes>MAX_EXPORT_BYTES){limited=true;break;}
                output.write(line);output.newLine();rows++;first=Math.min(first,ordinal);last=Math.max(last,ordinal);
            }
            var end=new JsonObject();end.addProperty("kind","capture_end");end.addProperty("capture",id);end.addProperty("reason",reason);
            end.addProperty("expectedEvents",expected);end.addProperty("writtenEvents",rows);end.addProperty("dataLoss",dataLoss);
            end.addProperty("exportLimit",limited);
            boolean cleanEnd=!Set.of("CAPTURE_ERROR","OPEN_FAILED","EVENT_LIMIT","TEST_FAILED").contains(reason);
            end.addProperty("complete",cleanEnd && !limited && !dataLoss && rows==expected && (rows==0 || first==1 && last==expected));
            output.write(JSON.toJson(end));output.newLine();
        }
    }
}
