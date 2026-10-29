"""Exclusive owner-clock wall accounting; overlapping worker work is never summed."""
from collections import Counter, defaultdict
import heapq

PHASE = {"QUEUED":"QUEUED", "RUNNING":"RUNNING", "COMPUTED":"COMPUTED_NOT_PUBLISHED",
         "READY":"RESULT_READY_WAITING_OWNER", "NOTIFY_BEGIN":"RESULT_READY_WAITING_OWNER",
         "DELIVERED":"DELIVERED", "CANCELLED":"CANCELLED", "FAILED":"FAILED",
         "QUEUE_BUSY":"SUBMISSION_BACKPRESSURE", "OWNER_COMPLETION_BEGIN":"OWNER_COMPLETION",
         "OWNER_COMPLETION_END":"OWNER_COMPLETED", "RECEIPT_OBSERVED":"RECEIPT_OBSERVED"}


def analyze_world(trace, world, data):
    turns = sorted(data["turns"], key=lambda e:(e["start"],e["end"]))
    observations=[]
    for e in turns:
        f=e["fields"]
        observations.extend([(e["start"],f["beforeTick"],f.get("beforeDebt",f.get("debt"))), (e["end"],f["afterTick"],f.get("debt"))])
    for e in data["states"]: observations.append((e["start"],e["fields"].get("tick",0),e["fields"].get("debt")))
    if len(observations)<2:
        return {"world":world,"status":"trace_incomplete","issues":["NO_CLOCK_COVERAGE"],"partition":[]}
    observations.sort(); observed_start, first_tick, before_debt = observations[0]
    observed_end, last_tick, last_debt = observations[-1]
    metadata=getattr(trace,"metadata",{})
    start=min(observed_start,metadata.get("recordingFirstNs",observed_start))
    end=max(observed_end,metadata.get("recordingLastNs",observed_end))
    tids = {e["tid"] for e in turns} | {e["tid"] for e in data["states"]}
    changes = defaultdict(list)
    def span(e, kind):
        a,b = max(start,e["start"]),min(end,e["end"])
        if b > a:
            changes[a].append(("open",kind,e)); changes[b].append(("close",kind,e))
    explicit_turn_end=any(s["fields"].get("reason")=="TURN_END" for s in data["states"])
    for e in turns:
        span(e,"turn")
        if not explicit_turn_end:
            changes[e["end"]].append(("state",None,{"fields":e["fields"]}))
    for e in data["states"]:
        if start <= e["start"] <= end: changes[e["start"]].append(("state",None,e))
    # Stage world can be empty outside an action context; owner thread identity
    # and actual interval overlap are explicit, but do not create a domain edge.
    for e in trace.events:
        name = e["event"]
        if name == "pale_mirror.ExecutionStage" and e["tid"] in tids:
            if e["fields"].get("world","") in ("",world): span(e,"stage")
        elif name == "jdk.GCPhasePause": span(e,"gc")
        elif e["tid"] in tids and name in ("jdk.ZAllocationStall","jdk.ThreadPark","jdk.JavaMonitorEnter","jdk.ThreadSleep"):
            span(e,"stall" if name == "jdk.ZAllocationStall" else "park")
    for identifier,node in trace.nodes.items():
        if node["type"] in ("TRANSACTION","ClockTurn","ClockAttempt","ACTION"): continue
        for e in node["points"]:
            if e["fields"].get("phase") in PHASE and start <= e["start"] <= end:
                changes[e["start"]].append(("phase",identifier,e["fields"]["phase"]))
    changes[start]; changes[end]
    active = {k:{} for k in ("turn","stage","gc","stall","park")}
    stages, task_phase, state, previous, partition = [], {}, {}, start, []
    def set_phase(identifier, phase):
        old=task_phase.get(identifier,"")
        task_phase[identifier]="FAILED_RESULT_READY_WAITING_OWNER" if old.startswith("FAILED") and phase in ("READY","NOTIFY_BEGIN") else PHASE[phase]
    for identifier,node in trace.nodes.items():
        before = [e for e in node["points"] if e["start"] < start and e["fields"].get("phase") in PHASE]
        for e in sorted(before,key=lambda e:e["start"]): set_phase(identifier,e["fields"]["phase"])
    def key(e): return (e["start"],e["end"],e["tid"],e["fields"].get("sequence",0))
    for at in sorted(changes):
        if at > previous:
            category, detail, dependency = "UNKNOWN_CLOCK_STATE", "", str(state.get("dependency",0))
            cause = dict(gate=state.get("gate",""),dependency=dependency,budgetCause=state.get("budgetCause",""))
            owner = bool(active["turn"])
            if owner:
                while stages and stages[0][2] not in active["stage"]: heapq.heappop(stages)
                stage = active["stage"].get(stages[0][2]) if stages else None
                category = "OWNER_STAGE" if stage else "OWNER_UNINSTRUMENTED"
                if stage:
                    f=stage["fields"]; detail=f.get("stage","")+" / "+f.get("kind","");cause["action"]=f.get("action","");cause["subject"]=f.get("subject","")
                if active["gc"]: category="OWNER_GC_PAUSE"
                elif active["stall"]: category="OWNER_ALLOCATION_STALL"
                elif active["park"]: category="OWNER_PARK_OR_SLEEP"
            else:
                gate=state.get("gate","")
                if gate in ("EXACT_CALCULATION","COMMIT_CONFIRMATION","WAL_TURN"):
                    prefix="EXACT" if gate=="EXACT_CALCULATION" else "WAL"
                    category=prefix+"_"+task_phase.get(dependency,"UNRESOLVED_DEPENDENCY")
                    node=trace.nodes.get(dependency)
                    if node: detail=node["fields"].get("kind","");cause["action"]=node["fields"].get("action","");cause["subject"]=node["fields"].get("subject","")
                elif gate=="READY_DISPATCH":
                    host=[e for e in active["stage"].values() if e["fields"].get("stage")=="HOST_TICK"]
                    category="HOST_OTHER_WORK" if host else "RUNNABLE_UNATTRIBUTED_DISPATCH"
                    if active["gc"]: category="DISPATCH_GC_PAUSE"
                    elif active["stall"]: category="DISPATCH_ALLOCATION_STALL"
                    elif active["park"]: category="DISPATCH_PARK_OR_SLEEP"
                elif gate: category="OWNER_INCOMPLETE_CAPTURE" if gate=="OWNER_RUNNING" else gate
            if previous>=observed_end: category="UNKNOWN_CAPTURE_EDGE"
            row={"start":previous,"end":at,"category":category,"detail":detail,"cause":cause,"debt":state.get("debt"),
                 "gcOverlap":bool(active["gc"]),"owner":owner,
                 "conditions":{k:state.get(k) for k in ("conditionsKnown","allowance","exactPending","commitPending","walPending")}}
            if partition and all(partition[-1][k]==row[k] for k in ("category","detail","cause","debt","gcOverlap","owner","conditions")):
                partition[-1]["end"]=at
            else: partition.append(row)
        for op,kind,event in changes[at]:
            if op=="state": state=event["fields"]
            elif op=="phase": set_phase(kind,event)
            else:
                identity=key(event)
                if op=="open":
                    active[kind][identity]=event
                    if kind=="stage" and event["fields"].get("stage") not in ("HOST_TURN","HOST_TICK"):
                        heapq.heappush(stages,(-event["start"],event["end"],identity))
                else: active[kind].pop(identity,None)
        previous=at
    buckets=Counter();unknown=0;blocking=Counter()
    for row in partition:
        ns=row["end"]-row["start"];buckets[row["category"]]+=ns
        if any(k in row["category"] for k in ("UNKNOWN","UNRESOLVED","UNATTRIBUTED","UNINSTRUMENTED","INCOMPLETE")): unknown+=ns
        if row["cause"]["dependency"]!="0": blocking[row["cause"]["dependency"]]+=ns

    issues=[]
    if trace.loss: issues.append("JFR_DATA_LOSS")
    if trace.missing: issues.append("MISSING_CAUSAL_NODES_OR_CAPTURE_EDGES")
    if unknown: issues.append("UNEXPLAINED_WALL_TIME")
    if not data["states"]: issues.append("NO_EXPLICIT_CLOCK_STATE_CHANGES")
    owner_cpu=0; cpu_complete=True; covered_end=start
    for e in sorted(turns,key=lambda e:(e["start"],-e["end"])):
        if e["end"]<=covered_end: continue
        f=e["fields"]; a=f.get("startedCpuNanos",-1);b=f.get("endedCpuNanos",-1)
        if e["start"]<covered_end or a<0 or b<a: cpu_complete=False
        else: owner_cpu+=b-a
        covered_end=e["end"]
    arrivals=sum(e["fields"].get("reason")=="HOST_TICK" and observed_start<e["start"]<=observed_end for e in data["states"])
    balance=None if before_debt is None or before_debt<0 else (last_debt-before_debt)-(arrivals-(last_tick-first_tick))
    if any(e["fields"].get("reason")=="HOST_TICK" for e in data["states"]) and balance: issues.append("CLOCK_DEBT_BALANCE_DIFFERENCE")
    accounted=sum(buckets.values());window=end-start
    if accounted!=window: raise AssertionError("wall-time partition is not complete")
    return {"world":world,"status":"trace_incomplete" if issues else "accounted","issues":issues,
            "start":start,"end":end,"windowNs":window,"accountedNs":accounted,"unexplainedNs":unknown,
            "unexplainedFraction":unknown/window if window else 1,
            "firstTick":first_tick,"lastTick":last_tick,"observedClockStart":observed_start,"observedClockEnd":observed_end,
            "ticksPerSecond":(last_tick-first_tick)/((observed_end-observed_start)/1e9) if observed_end>observed_start else 0,
            "firstDebt":before_debt,"lastDebt":last_debt,"hostTickArrivals":arrivals,"debtBalanceDifferenceTicks":balance,
            "ownerCpuNs":owner_cpu if cpu_complete else None,"ownerCpuComplete":cpu_complete,
            "exclusiveWallNs":dict(buckets),"blockingTasksNs":dict(blocking),"partition":partition}
