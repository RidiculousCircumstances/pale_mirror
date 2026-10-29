"""Exact accounting/causal semantics, no elapsed-time or performance assertions."""
import unittest
from simulation_delay.model import Trace
from simulation_delay.report import build


def event(name,start,end=None,**fields):
    return {"event":name if name.startswith("jdk.") else "pale_mirror."+name,"start":start,"end":start if end is None else end,
            "tid":1,"thread":"owner","fields":fields}

def turn(identifier,start,end,gate="NO_DEBT",dependency=0):
    return event("ClockTurn",start,end,id=identifier,world="w",beforeTick=start,afterTick=end,debt=1,gate=gate,dependency=dependency)

def point(identifier,at,phase,kind="EXACT",**fields):
    return event("DelayPoint",at,id=identifier,phase=phase,type=kind,world="w",**fields)

class DelayAnalysisTest(unittest.TestCase):
    def test_overlapping_work_is_not_added_to_wall_and_refilled_budget_ends_wait(self):
        rows=[turn(1,0,10,"EXACT_CALCULATION",2),turn(3,50,60,"BUDGET"),turn(4,90,100),
              point(2,5,"QUEUED"),point(2,15,"RUNNING"),point(2,30,"READY"),
              event("ClockState",70,world="w",gate="READY_DISPATCH",debt=1,dependency=0),
              event("ExecutionStage",0,10,world="w",stage="SCHEDULE_PLAN",kind="outer"),
              event("ExecutionStage",3,7,world="w",stage="VALIDATION",kind="inner"),
              event("ExecutionStage",50,60,world="w",stage="TRANSACTION",kind="commit"),
              event("ExecutionStage",90,100,world="w",stage="SCHEDULE_ALLOCATION",kind="next")]
        result=build(Trace(reversed(rows)));w=result["worlds"][0];gate=w["exclusiveWallNs"]
        self.assertEqual(100,w["accountedNs"]);self.assertEqual(10,gate["BUDGET"])
        self.assertEqual(5,gate["EXACT_QUEUED"]);self.assertEqual(15,gate["EXACT_RUNNING"])
        self.assertEqual(20,gate["EXACT_RESULT_READY_WAITING_OWNER"])
        self.assertEqual(30,gate["OWNER_STAGE"]);self.assertEqual(20,w["unexplainedNs"])
        self.assertEqual(4,sum(r["end"]-r["start"] for r in w["partition"] if "inner" in r["detail"]))

    def test_shared_dependency_counts_clock_once_and_retains_all_observers(self):
        rows=[turn(1,0,5,"EXACT_CALCULATION",8),turn(2,40,45,"EXACT_CALCULATION",8),turn(3,80,85),
              point(8,-1,"QUEUED"),point(8,10,"RUNNING"),point(8,70,"READY"),
              point(8,41,"OWNER_COMPLETION_BEGIN",observedBy=2,observedAdvance=20),
              point(8,81,"OWNER_COMPLETION_BEGIN",observedBy=3,observedAdvance=30)]
        result=build(Trace(rows));self.assertEqual(1,sum(n["id"]=="8" for n in result["graph"]["nodes"]))
        self.assertTrue(any(e["from"]=="8" and e["to"]=="2" for e in result["graph"]["edges"]))
        self.assertTrue(any(e["from"]=="8" and e["to"]=="3" for e in result["graph"]["edges"]))
        self.assertEqual(85,result["worlds"][0]["accountedNs"])

    def test_missing_capture_dependency_and_data_loss_never_claim_complete(self):
        rows=[turn(1,0,10,"EXACT_CALCULATION",999),turn(2,90,100),event("jdk.DataLoss",30)]
        result=build(Trace(rows));w=result["worlds"][0]
        self.assertEqual(80,w["exclusiveWallNs"]["EXACT_UNRESOLVED_DEPENDENCY"])
        self.assertIn("999",result["graph"]["missingNodes"])
        self.assertEqual("trace_incomplete",w["status"]);self.assertEqual(1,result["quality"]["jfrDataLossEvents"])

    def test_retry_and_cancellation_use_actual_latest_lifecycle_not_first_phase(self):
        rows=[turn(1,0,1,"EXACT_CALCULATION",8),turn(2,99,100),point(8,-1,"QUEUED"),
              point(8,10,"QUEUE_BUSY"),point(8,20,"QUEUED"),point(8,30,"RUNNING"),point(8,40,"CANCELLED")]
        gate=build(Trace(rows))["worlds"][0]["exclusiveWallNs"]
        self.assertEqual(19,gate["EXACT_QUEUED"]);self.assertEqual(10,gate["EXACT_SUBMISSION_BACKPRESSURE"])
        self.assertEqual(10,gate["EXACT_RUNNING"]);self.assertEqual(59,gate["EXACT_CANCELLED"])

    def test_gc_pause_is_exclusive_only_on_owner_path_and_not_concurrent_gc_duration(self):
        rows=[turn(1,0,100),event("ExecutionStage",0,100,world="w",stage="TRANSACTION",kind="commit"),
              event("jdk.GCPhasePause",20,30),event("jdk.GarbageCollection",0,100)]
        gate=build(Trace(rows))["worlds"][0]["exclusiveWallNs"]
        self.assertEqual(10,gate["OWNER_GC_PAUSE"]);self.assertEqual(90,gate["OWNER_STAGE"])

    def test_stalled_clock_with_no_completed_owner_turn_is_visible(self):
        rows=[event("ClockState",0,world="w",tick=10,debt=1,gate="EXACT_CALCULATION",dependency=8,reason="HOST_TICK"),
              event("ClockState",100,world="w",tick=10,debt=2,gate="EXACT_CALCULATION",dependency=8,reason="HOST_TICK"),
              point(8,-1,"RUNNING")]
        result=build(Trace(rows));w=result["worlds"][0]
        self.assertEqual(100,w["exclusiveWallNs"]["EXACT_RUNNING"]);self.assertEqual(0,w["ticksPerSecond"])
        self.assertEqual(0,w["debtBalanceDifferenceTicks"])

    def test_failed_result_readiness_is_not_relabelled_as_successful_computation(self):
        rows=[turn(1,0,1,"EXACT_CALCULATION",8),turn(2,99,100),point(8,-1,"RUNNING"),
              point(8,10,"FAILED"),point(8,20,"READY"),point(8,30,"NOTIFY_BEGIN")]
        gate=build(Trace(rows))["worlds"][0]["exclusiveWallNs"]
        self.assertEqual(10,gate["EXACT_FAILED"]);self.assertEqual(79,gate["EXACT_FAILED_RESULT_READY_WAITING_OWNER"])

    def test_cpu_time_does_not_include_worker_queue_or_nested_owner_twice(self):
        rows=[turn(1,0,100),turn(2,20,40),point(8,0,"QUEUED"),point(8,30,"RUNNING",cpuNanos=5),
              point(8,60,"COMPUTED",cpuNanos=15)]
        rows[0]["fields"].update(startedCpuNanos=10,endedCpuNanos=40)
        rows[1]["fields"].update(startedCpuNanos=15,endedCpuNanos=20)
        result=build(Trace(rows));self.assertEqual(30,result["worlds"][0]["ownerCpuNs"])
        node=next(n for n in result["graph"]["nodes"] if n["id"]=="8")
        self.assertEqual(10,node["workerCpuNs"])

    def test_wal_members_use_world_and_sequence_and_require_observed_receipt(self):
        rows=[point(0,0,"APPENDED","TRANSACTION",reference="tx:a",value=17),
              point(8,1,"QUEUED","WAL"),point(8,2,"MEMBER","WAL",reference="w",value=17),
              point(8,3,"MEMBER","WAL",reference="other-world",value=17),point(8,4,"COMPUTED","WAL")]
        before=build(Trace(rows));self.assertFalse(before["wal"]["transactions"][0]["receiptObserved"])
        self.assertEqual(1,len(before["wal"]["missingAppends"]))
        after=build(Trace(rows+[point(8,5,"RECEIPT_OBSERVED","WAL",reference="w",value=17)]))
        self.assertTrue(after["wal"]["transactions"][0]["receiptObserved"])

if __name__=="__main__":unittest.main()
