package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.Gson;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CanonicalPublicationObserver;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3EconomyProjection.row;

/** Optional host observation. One writer, bounded evidence, no blocking queue or filesystem on ticks. */
final class FrontierV3EconomyObservatory implements CanonicalPublicationObserver<FrontierWorldState>, AutoCloseable {
    static final String PROPERTY="pale_mirror.frontier_v3.economy_analytics";
    private static final Map<FrontierV3ServerRuntime<?,?>,FrontierV3EconomyObservatory> ACTIVE=new IdentityHashMap<>();
    private static final int MAX_QUEUE=16, MAX_FILES=128, MAX_BATCH_BYTES=8*1024*1024;
    private static final long MAX_SPOOL_BYTES=64L*1024*1024;
    private record Cut(FrontierWorldState state,long tick,long revision,String status) { }
    private record Transition(TransactionRecord tx,FrontierWorldState before,FrontierWorldState after) { }
    private final ArrayBlockingQueue<Transition> queue=new ArrayBlockingQueue<>(MAX_QUEUE);
    private final AtomicReference<Cut> latest=new AtomicReference<>();
    private final AtomicLong lost=new AtomicLong();
    private final Path directory;
    private final String session=UUID.randomUUID().toString();
    private final Thread worker;
    private volatile boolean stopping;
    private long sequence;
    private FrontierV3EconomyObservatory(Path directory) {
        this.directory=directory;
        worker=new Thread(this::run,"pm-economy-observation");worker.setDaemon(true);
    }
    static void attach(FrontierV3ServerRuntime<FrontierWorldState,?> runtime,Path world) {
        if(!Boolean.getBoolean(PROPERTY)) return;
        var observer=new FrontierV3EconomyObservatory(world.resolve("economy-analytics"));
        runtime.canonicalState().ifPresent(c->observer.latest.set(new Cut(c.state(),c.instant().ticks(),c.revision().value(),runtime.status().kind().name())));
        ACTIVE.put(runtime,observer);runtime.observePublications(observer);observer.worker.start();
    }
    static void detach(FrontierV3ServerRuntime<FrontierWorldState,?> runtime) {
        var observer=ACTIVE.remove(runtime);if(observer==null)return;
        runtime.observePublications(null);
        runtime.canonicalState().ifPresent(c->observer.latest.set(new Cut(c.state(),c.instant().ticks(),c.revision().value(),runtime.status().kind().name())));
        observer.latest.updateAndGet(c->c==null?null:new Cut(c.state(),c.tick(),c.revision(),runtime.status().kind()==FrontierV3RuntimeStatus.Kind.ACTIVE?"STOPPED":runtime.status().kind().name()));
        observer.close();
    }
    @Override public void published(TransactionRecord tx,FrontierWorldState before,FrontierWorldState after) {
        latest.set(new Cut(after,tx.instant().ticks(),tx.revision().value(),"ACTIVE"));
        try {
            if(FrontierV3EconomyFacts.relevant(tx,before,after) && !queue.offer(new Transition(tx,before,after))) lost.incrementAndGet();
        } catch(RuntimeException error) { lost.incrementAndGet(); }
    }
    private void run() {
        var gson=new Gson();String world=null;long lastLog=0, nextFlush=0;boolean snapshotAdded=false;
        var pending=new ArrayList<Map<String,Object>>();
        while(true) {
            try {
                Files.createDirectories(directory);
                if(world==null) {
                    Path identity=directory.resolve("world-id");
                    if(Files.exists(identity)) world=UUID.fromString(Files.readString(identity).trim()).toString();
                    else {world=UUID.randomUUID().toString();Files.writeString(identity,world,StandardOpenOption.CREATE_NEW);}
                }
                if(!snapshotAdded && pending.size()<2048) {
                    for(int i=0;i<MAX_QUEUE;i++) {var transition=queue.poll();if(transition==null)break;
                        try {pending.addAll(FrontierV3EconomyFacts.facts(transition.tx(),transition.before(),transition.after()));}
                        catch(RuntimeException error){lost.incrementAndGet();}
                    }
                }
                if(!stopping && System.nanoTime()<nextFlush) {
                    try {Thread.sleep(25);}catch(InterruptedException stop){/* close flushes */}
                    continue;
                }
                nextFlush=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
                if(!snapshotAdded) {
                    var cut=latest.get();
                    if(cut!=null) {
                        long dropped=lost.getAndSet(0);
                        if(dropped>0) pending.add(row("id",session+":gap:"+sequence,"kind","gap","tick",cut.tick(),"revision",cut.revision(),
                                "reason","observation_overflow_or_projection_failure","lostTransactions",dropped));
                        var snapshot=FrontierV3EconomyProjection.snapshot(cut.state(),cut.tick(),cut.revision(),cut.status());
                        snapshot.put("id",session+":snapshot:"+sequence);pending.add(snapshot);snapshotAdded=true;
                    }
                }
                if(!pending.isEmpty()) {
                    long count=0,bytes=0;
                    try(var paths=Files.newDirectoryStream(directory,"batch-*.json")) {
                        for(var path:paths) {count++;try{bytes+=Files.size(path);}catch(NoSuchFileException consumed){/* collector committed it */}}
                    }
                    if(count>=MAX_FILES || bytes>=MAX_SPOOL_BYTES) throw new IllegalStateException("analytics spool full; collector is behind");
                    byte[] body=gson.toJson(row("schema",1,"world",world,"name",directory.getParent().getFileName().toString(),
                            "session",session,"records",pending)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    if(body.length>MAX_BATCH_BYTES) {
                        lost.incrementAndGet();pending.clear();snapshotAdded=false;throw new IllegalStateException("analytics batch size exceeded");
                    }
                    String name=String.format(java.util.Locale.ROOT,"batch-%013d-%s-%06d.json",System.currentTimeMillis(),session,sequence++);
                    Path temporary=directory.resolve("pending-"+session+".tmp");Files.write(temporary,body);
                    Files.move(temporary,directory.resolve(name),StandardCopyOption.ATOMIC_MOVE);
                    pending.clear();snapshotAdded=false;
                }
            } catch(Exception error) {
                long now=System.nanoTime();if(now-lastLog>TimeUnit.SECONDS.toNanos(30)) {
                    PaleMirrorMod.LOGGER.warn("Economy analytics unavailable; canonical simulation continues: {}",error.toString());lastLog=now;
                }
                if(stopping) break;
            }
            if(stopping) break;
            try {Thread.sleep(25);}catch(InterruptedException stop){/* bounded close */}
        }
    }
    @Override public void close() {
        stopping=true;worker.interrupt();
        try {worker.join(2000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
}
