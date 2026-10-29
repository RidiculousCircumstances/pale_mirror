"""Typed-by-producer causal nodes. No timestamp-nearness or thread-name joins."""
from collections import defaultdict

CUSTOM = "pale_mirror."
MAX_EVENTS, MAX_NODES = 1_000_000, 150_000

class Trace:
    def __init__(self, records):
        self.nodes, self.edges, self.events, self.references = {}, set(), [], set()
        self.worlds = defaultdict(lambda: {"turns": [], "attempts": [], "states": [], "stages": []})
        self.loss = 0
        self.wal = {"missingAppends": [], "transactions": []}
        for i, event in enumerate(records):
            if i >= MAX_EVENTS:
                raise ValueError("event capacity exceeded; no partial success")
            if event["event"] == "trace.metadata":
                self.metadata = event
                continue
            if event["end"] < event["start"]:
                raise ValueError("negative event duration")
            self.events.append(event)
            self.add(event)
        self.missing = sorted((self.references | {n for a,b,_ in self.edges for n in (a,b)}) - self.nodes.keys())

    def node(self, key, kind, event):
        if key not in self.nodes:
            if len(self.nodes) >= MAX_NODES:
                raise ValueError("causal node capacity exceeded")
            self.nodes[key] = {"id":key, "type":kind, "points":[], "fields":{}, "start":event["start"], "end":event["end"], "tid":event["tid"], "thread":event["thread"]}
        node = self.nodes[key]
        node["start"] = min(node["start"],event["start"])
        node["end"] = max(node["end"],event["end"])
        # Later acknowledgements may omit origin context; absence never erases it.
        node["fields"].update({k:v for k,v in event["fields"].items() if v not in ("",None) and k not in ("parent","observedBy","causedBy","turn","advance","previousCause","previousTurn","previousAdvance")})
        return node

    def edge(self, parent, child, relation):
        if parent and child and str(parent) != str(child):
            self.edges.add((str(parent),str(child),relation))

    def add(self, event):
        name, f = event["event"], event["fields"]
        if name == "jdk.DataLoss":
            self.loss += 1
        if not name.startswith(CUSTOM):
            return
        kind, world = name[len(CUSTOM):], f.get("world", "")
        if kind == "ClockTurn":
            self.worlds[world]["turns"].append(event)
        elif kind == "ClockAttempt":
            self.worlds[world]["attempts"].append(event)
        elif kind == "ClockState":
            self.worlds[world]["states"].append(event)
            if f.get("dependency"): self.references.add(str(f["dependency"]))
            self.edge(f.get("turn"),f.get("dependency"),"blocked_on")
        elif kind == "ExecutionStage":
            self.worlds[world]["stages"].append(event)
        identifier = f.get("id",0)
        if identifier and kind in ("DelayPoint","ClockTurn","ClockAttempt"):
            node = self.node(str(identifier), f.get("type",kind), event)
            node["points"].append(event)
            self.edge(f.get("parent"),identifier,"created_by")
            if kind == "ClockTurn": self.edge(identifier,f.get("dependency"),"blocked_on")
            if kind == "DelayPoint":
                if f.get("phase") == "COALESCED":
                    self.edge(f.get("causedBy"),identifier,"coalesced_notification")
                if f.get("phase") in ("OBSERVED","ACCEPTED","REJECTED","OWNER_COMPLETION_BEGIN","RECEIPT_OBSERVED"):
                    self.edge(identifier,f.get("observedBy"),"observed_by")
                    self.edge(identifier,f.get("observedAdvance"),"delivered_to_advance")
                    self.edge(identifier,f.get("observedTurn"),"observed_by_turn")
        if kind == "DelayPoint" and f.get("type") == "TRANSACTION" and f.get("reference"):
            key = "transaction:" + world + ":" + f["reference"]
            self.node(key,"TRANSACTION",event)["points"].append(event)
            self.edge(f.get("parent") or f.get("causedBy"),key,"transaction_origin")

    def join_wal(self):
        appended = {}
        for node in list(self.nodes.values()):
            if node["type"] != "TRANSACTION":
                continue
            for e in node["points"]:
                f = e["fields"]
                if f.get("phase") == "APPENDED":
                    appended[(f.get("world"),f.get("value"))] = node["id"]
        for node in list(self.nodes.values()):
            if node["type"] != "WAL":
                continue
            for e in node["points"]:
                f = e["fields"]
                if f.get("phase") == "MEMBER":
                    source = appended.get((f.get("reference"),f.get("value")))
                    if source:
                        self.edge(source,node["id"],"wal_prefix_member")
                        observed = any(p["fields"].get("phase")=="RECEIPT_OBSERVED" and p["fields"].get("world")==f.get("reference")
                                       and p["fields"].get("value",-1)>=f.get("value",0) for p in node["points"])
                        self.wal["transactions"].append({"transaction":source,"flush":node["id"],"world":f.get("reference"),"sequence":f.get("value"),"receiptObserved":observed})
                    else:
                        self.wal["missingAppends"].append({"flush":node["id"],"world":f.get("reference"),"sequence":f.get("value")})
        self.missing = sorted((self.references | {n for a,b,_ in self.edges for n in (a,b)}) - self.nodes.keys())
