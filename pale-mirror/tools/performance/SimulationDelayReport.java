import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;

/** Read-only bounded consumer of Frontier causal JFR events. Java 21 source launcher, no game dependency. */
public class SimulationDelayReport {
    record Span(long start, long end, RecordedEvent event) { }
    static final class Task {
        String type, kind, action, subject;
        final Map<String, Long> phases = new HashMap<>();
        void add(RecordedEvent e) {
            type = s(e,"type"); kind = s(e,"kind"); action = s(e,"action"); subject = s(e,"subject");
            phases.merge(s(e,"phase"), n(e,"monotonicNanos"), Math::min);
        }
        long at(String phase) { return phases.getOrDefault(phase, Long.MAX_VALUE); }
    }
    static final class World {
        final List<Span> turns = new ArrayList<>();
        final Map<String, Long> gates = new TreeMap<>(), gateKinds = new TreeMap<>();
        long attempts, transactions, actions, weight, classifications, executions;
        long pressureCuts, maxReady, maxHeld, maxChecking, maxReadyLag, maxDeadlineLag;
    }
    static final int MAX_EVENTS=1_000_000, MAX_TASKS=100_000;
    static final Map<Long,Task> tasks = new HashMap<>();
    static final Map<String,World> worlds = new TreeMap<>();
    static final Map<String,Long> phases = new TreeMap<>(), stages = new TreeMap<>();
    static long customEvents, dataLoss;
    static String s(RecordedEvent e,String field) { var value=e.getString(field); return value==null?"":value; }
    static long n(RecordedEvent e,String field) { return e.getLong(field); }
    static void add(Map<String,Long> values,String key,long value) { values.merge(key,value,Long::sum); }
    static void gap(World w,Span previous,long until) {
        long from=previous.end; if (until<=from) return;
        var e=previous.event; String gate=s(e,"gate"); Task task=tasks.get(n(e,"dependency"));
        if (gate.equals("EXACT_CALCULATION") || gate.equals("COMMIT_CONFIRMATION") || gate.equals("WAL_TURN")) {
            String prefix=gate.equals("EXACT_CALCULATION")?"EXACT":"WAL";
            if (task==null) { add(w.gates,prefix+"_UNRESOLVED_DEPENDENCY",until-from); return; }
            var cuts=new TreeSet<Long>(List.of(from,until));
            for(var time:task.phases.values()) if(time>from&&time<until) cuts.add(time);
            Long last=null;
            for(var end:cuts) {
                if(last!=null) {
                    String phase=last>=task.at("COMPUTED")?"RESULT_READY_WAITING_OWNER"
                        :last>=task.at("RUNNING")?"RUNNING":last>=task.at("QUEUED")?"QUEUED":"MISSING_START";
                    if(last>=task.at("CANCELLED") || last>=task.at("FAILED")) phase="CANCELLED_OR_FAILED";
                    add(w.gates,prefix+"_"+phase,end-last);
                    add(w.gateKinds,prefix+"_"+phase+" / "+task.kind,end-last);
                }
                last=end;
            }
        } else add(w.gates,gate.equals("READY_DISPATCH")?"RUNNABLE_UNATTRIBUTED_DISPATCH":gate,until-from);
    }
    static void report(String name,World w) {
        w.turns.sort(Comparator.comparingLong(Span::start).thenComparingLong(Span::end));
        if(w.turns.isEmpty()) { System.out.println("WORLD "+name+" NO_CLOCK_COVERAGE"); return; }
        Span outer=null; long busyStart=0,busyEnd=0;
        for(var turn:w.turns) {
            if(outer==null) { outer=turn; busyStart=turn.start;busyEnd=turn.end;continue; }
            if(turn.start<=busyEnd) {
                if(turn.end>=busyEnd) { busyEnd=turn.end; outer=turn; }
            } else {
                add(w.gates,"OWNER_TURN",busyEnd-busyStart);
                gap(w,outer,turn.start); outer=turn; busyStart=turn.start;busyEnd=turn.end;
            }
        }
        add(w.gates,"OWNER_TURN",busyEnd-busyStart);
        var first=w.turns.getFirst(); var last=w.turns.stream().max(Comparator.comparingLong(Span::end)).orElseThrow();
        long covered=last.end-first.start, accounted=w.gates.values().stream().mapToLong(Long::longValue).sum();
        double seconds=covered/1e9;
        System.out.printf(Locale.ROOT,"WORLD %s covered=%.6fs accounted=%.6fs difference=%dns turns=%d\n",name,seconds,accounted/1e9,covered-accounted,w.turns.size());
        long firstTick=n(first.event,"beforeTick"),lastTick=n(last.event,"afterTick");
        System.out.printf(Locale.ROOT,"CLOCK ticks=%d->%d rate=%.6f/s debt=%d->%d (canonical ticks)\n",firstTick,lastTick,(lastTick-firstTick)/seconds,n(first.event,"debt"),n(last.event,"debt"));
        System.out.printf("WORK attempts=%d committedTransactions=%d actionPasses=%d weight=%d classifications=%d executions=%d\n",w.attempts,w.transactions,w.actions,w.weight,w.classifications,w.executions);
        System.out.printf("PRESSURE cuts=%d maxReady=%d maxHeld=%d maxChecking=%d maxReadyLag=%d maxDeadlineLag=%d\n",w.pressureCuts,w.maxReady,w.maxHeld,w.maxChecking,w.maxReadyLag,w.maxDeadlineLag);
        System.out.println("EXCLUSIVE_WALL_GATES (owner spans unioned; workers are not added to this total)");
        w.gates.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).forEach(v->System.out.printf(Locale.ROOT,"  %.6fs %6.2f%% %s\n",v.getValue()/1e9,100.0*v.getValue()/covered,v.getKey()));
        System.out.println("BLOCKING_KINDS (subset of gates, not an additional total)");
        w.gateKinds.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(25).forEach(v->System.out.printf(Locale.ROOT,"  %.6fs %s\n",v.getValue()/1e9,v.getKey()));
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Usage: java SimulationDelayReport.java recording.jfr");
        try(var recording=new RecordingFile(Path.of(args[0]))) {
            while(recording.hasMoreEvents()) {
                var e=recording.readEvent(); String type=e.getEventType().getName();
                if(type.equals("jdk.DataLoss")) dataLoss++;
                if(!type.startsWith("pale_mirror.")) continue;
                if(++customEvents>MAX_EVENTS) throw new IllegalStateException("Trace exceeds bounded analyzer capacity; shorten recording, no partial success");
                switch(type) {
                    case "pale_mirror.DelayPoint" -> {
                        add(phases,s(e,"type")+" / "+s(e,"phase"),1);
                        long id=n(e,"id"); if(id!=0&&!s(e,"type").equals("ACTION")) {
                            if(!tasks.containsKey(id)&&tasks.size()>=MAX_TASKS) throw new IllegalStateException("Task capacity exceeded");
                            tasks.computeIfAbsent(id,ignored->new Task()).add(e);
                        }
                    }
                    case "pale_mirror.ClockTurn" -> worlds.computeIfAbsent(s(e,"world"),ignored->new World()).turns.add(new Span(n(e,"startedNanos"),n(e,"endedNanos"),e));
                    case "pale_mirror.ClockAttempt" -> {
                        var w=worlds.computeIfAbsent(s(e,"world"),ignored->new World());
                        w.attempts++;w.transactions+=n(e,"transactions");w.actions+=n(e,"actions");w.weight+=n(e,"weight");w.classifications+=n(e,"classifications");w.executions+=n(e,"executions");
                    }
                    case "pale_mirror.SchedulePressure" -> {
                        var w=worlds.computeIfAbsent(s(e,"world"),ignored->new World());w.pressureCuts++;
                        w.maxReady=Math.max(w.maxReady,n(e,"ready"));w.maxHeld=Math.max(w.maxHeld,n(e,"held"));w.maxChecking=Math.max(w.maxChecking,n(e,"checking"));
                        w.maxReadyLag=Math.max(w.maxReadyLag,n(e,"readyLag"));w.maxDeadlineLag=Math.max(w.maxDeadlineLag,n(e,"deadlineLag"));
                    }
                    case "pale_mirror.ExecutionStage" -> add(stages,s(e,"stage")+" / "+s(e,"kind"),n(e,"endedNanos")-n(e,"startedNanos"));
                    default -> { }
                }
            }
        }
        System.out.printf("TRACE events=%d tasks=%d JFR_DATA_LOSS_EVENTS=%d\n",customEvents,tasks.size(),dataLoss);
        System.out.println("Coverage excludes recording edges outside first/last completed owner turns; in-flight or missing dependencies stay explicit. READY_DISPATCH does not distinguish Minecraft, GC, other host work or idle dispatch. Stage durations and task work may overlap and must not be summed with gates.");
        worlds.forEach(SimulationDelayReport::report);
        System.out.println("LIFECYCLE_COUNTS");phases.forEach((k,v)->System.out.println("  "+v+" "+k));
        System.out.println("INCLUSIVE_OWNER_STAGE_SECONDS (nested, not additive)");
        stages.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(30).forEach(v->System.out.printf(Locale.ROOT,"  %.6fs %s\n",v.getValue()/1e9,v.getKey()));
        System.out.println("ASYNC_PHASE_ELAPSED (overlapping, not additive to wall gates)");
        var totals = new TreeMap<String,Long>();
        for(var t:tasks.values()) {
            for(var pair:List.of(new String[]{"QUEUED","RUNNING"},new String[]{"RUNNING","COMPUTED"},new String[]{"COMPUTED","OWNER_COMPLETION_BEGIN"},new String[]{"QUEUED","DELIVERED"})) {
                long start=t.at(pair[0]),end=t.at(pair[1]);
                if(start!=Long.MAX_VALUE&&end!=Long.MAX_VALUE&&end>=start) add(totals,t.type+" / "+pair[0]+" -> "+pair[1],end-start);
            }
        }
        totals.forEach((k,v)->System.out.printf(Locale.ROOT,"  %.6fs %s\n",v/1e9,k));
        System.out.println("LONGEST_EXACT_TASKS (parallel elapsed work, not global blocking)");
        tasks.entrySet().stream().filter(e->e.getValue().type.equals("EXACT")&&e.getValue().at("COMPUTED")!=Long.MAX_VALUE&&e.getValue().at("RUNNING")!=Long.MAX_VALUE)
            .sorted(Comparator.comparingLong((Map.Entry<Long,Task> e)->e.getValue().at("COMPUTED")-e.getValue().at("RUNNING")).reversed()).limit(20)
            .forEach(e->{var t=e.getValue();System.out.printf(Locale.ROOT,"  id=%d compute=%.6fs queue=%.6fs apply=%.6fs kind=%s action=%s subject=%s\n",e.getKey(),(t.at("COMPUTED")-t.at("RUNNING"))/1e9,(t.at("RUNNING")-t.at("QUEUED"))/1e9,t.at("OWNER_COMPLETION_END")==Long.MAX_VALUE?Double.NaN:(t.at("OWNER_COMPLETION_END")-t.at("OWNER_COMPLETION_BEGIN"))/1e9,t.kind,t.action,t.subject);});
    }
}
