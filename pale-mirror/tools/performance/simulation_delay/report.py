"""Artifact composition; all causal joins live in model and wall accounting in analysis."""
from collections import Counter, defaultdict
from bisect import bisect_right
from .analysis import analyze_world

TERMINAL={"OWNER_COMPLETION_END","ACCEPTED","REJECTED","CANCELLED","FAILED","FINISHED","RECEIPT_OBSERVED"}


def lifecycle(trace):
    spans=[]
    for node in trace.nodes.values():
        if node["type"] in ("ClockTurn","ClockAttempt","TRANSACTION","ACTION"): continue
        points=sorted(node["points"],key=lambda e:(e["start"],e["fields"].get("sequence",0)))
        previous=None; node["terminal"]=False
        for e in points:
            phase=e["fields"].get("phase","")
            if phase in ("COALESCED","MEMBER","NOTIFY_BEGIN"): continue
            if previous:
                old=previous["fields"].get("phase","")
                if e["start"]>previous["start"]:
                    # RUNNING belongs to its actual worker; queue/ready phases
                    # retain their emitting thread, never inferred from names.
                    a=previous["fields"].get("cpuNanos",-1);b=e["fields"].get("cpuNanos",-1)
                    if old in ("RUNNING","OWNER_COMPLETION_BEGIN") and a>=0 and b>=a and previous["tid"]==e["tid"]:
                        field="workerCpuNs" if old=="RUNNING" else "ownerCompletionCpuNs"
                        node[field]=node.get(field,0)+b-a
                    spans.append({"start":previous["start"],"end":e["start"],"node":node["id"],"phase":old,
                                  "tid":previous["tid"],"thread":previous["thread"],"world":node["fields"].get("world","")})
            previous=e
            if phase in TERMINAL: node["terminal"]=True
        node["phaseCount"]=len(points)
    return spans


def samples(trace, spans):
    by_thread=defaultdict(list)
    for row in spans:
        if row["phase"]=="RUNNING": by_thread[row["tid"]].append(row)
    indices={}
    for tid,rows in by_thread.items():
        rows.sort(key=lambda r:r["start"]);indices[tid]=[r["start"] for r in rows]
    aggregate=defaultdict(Counter);counts=Counter()
    for e in trace.events:
        if e["event"] not in ("jdk.ExecutionSample","jdk.NativeMethodSample"): continue
        counts[e["thread"]]+=1; rows=by_thread.get(e["tid"],[])
        at=bisect_right(indices.get(e["tid"],[]),e["start"])-1
        if at>=0 and rows[at]["start"]<=e["start"]<rows[at]["end"]:
            node=trace.nodes[rows[at]["node"]]
            node["cpuSamples"]=node.get("cpuSamples",0)+1
        if e.get("stack"): aggregate[e["thread"]][e["stack"][0]]+=1
    return {thread:{"sampleCount":counts[thread],"leafSamples":dict(rows.most_common(12))} for thread,rows in aggregate.items()}


def build(trace):
    trace.join_wal()
    worlds=[analyze_world(trace,name,data) for name,data in sorted(trace.worlds.items()) if name]
    phases=lifecycle(trace); cpu=samples(trace,phases)
    graph=[]
    for node in trace.nodes.values():
        row={k:v for k,v in node.items() if k!="points"}
        row["phases"]=[{"at":e["start"],"phase":e["fields"].get("phase",""),"tid":e["tid"],
                        "observedBy":e["fields"].get("observedBy",0),"value":e["fields"].get("value",0),
                        "reference":e["fields"].get("reference","")} for e in node["points"]]
        graph.append(row)
    correlations=bool(worlds) and all(e["fields"].get("id",0)>0 for w in trace.worlds.values() for e in w["turns"]+w["attempts"])
    states=all(bool(w["states"]) for w in trace.worlds.values() if w["turns"] or w["attempts"] or w["states"])
    return {"schema":2,"clockUnit":"canonical_tick","timeUnit":"nanosecond","worlds":worlds,
            "wal":trace.wal,"graph":{"nodes":graph,"edges":[{"from":a,"to":b,"relation":kind} for a,b,kind in sorted(trace.edges)],
                     "missingNodes":trace.missing},"phaseSpans":phases,"cpuSamples":cpu,
            "quality":{"jfrDataLossEvents":trace.loss,"graphReferencesClosed":not trace.missing,"clockCorrelationsPresent":correlations,
                       "causalClosureComplete":not trace.missing and not trace.loss and correlations and states,
                       "eventCount":len(trace.events),"clockCoverage":bool(worlds)},
            "limits":["Window includes normalized recording bounds; unobserved capture edges are explicit unknown time.",
                      "Container/observer edges describe scope and joins; they are not all execution precedence edges.",
                      "Exclusive wall partition is measured; CPU samples are separate statistical context.",
                      "GC overlaps are shown but concurrent GC duration is not declared a stop-the-world pause.",
                      "Business holds and future deadlines are not clock-blocking dependencies without an observed gate.",
                      "A complete arithmetic partition does not imply complete causal explanation."]}


def perfetto(result):
    events=[]
    def add(name,start,end,lane,args):
        if end>start: events.append({"name":name,"ph":"X","ts":start/1000,"dur":(end-start)/1000,"pid":1,"tid":lane,"args":args})
    for w in result["worlds"]:
        for row in w["partition"]:
            add(row["category"],row["start"],row["end"],"clock / "+w["world"],{"detail":row["detail"],**row["cause"]})
    for row in result["phaseSpans"]:
        add(row["phase"],row["start"],row["end"],str(row["tid"])+" / "+row["thread"],{"node":row["node"]})
    return {"traceEvents":events,"displayTimeUnit":"ms","metadata":{"schema":2,"limits":result["limits"]}}
