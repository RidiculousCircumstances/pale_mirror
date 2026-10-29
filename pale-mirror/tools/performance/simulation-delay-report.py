#!/usr/bin/env python3
"""One read-only JFR -> causal graph, exclusive wall report and local timeline entry point."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
from simulation_delay.model import Trace
from simulation_delay.report import build,perfetto


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recording",type=Path)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--java",default=str(Path(os.environ["JAVA_HOME"])/"bin/java") if "JAVA_HOME" in os.environ else "java")
    parser.add_argument("--normalized",action="store_true",help="consume normalized fixture/receipt instead of JFR")
    args=parser.parse_args()
    if args.output.exists() and any(args.output.iterdir()):
        parser.error("output must be empty/new; preserve earlier evidence and avoid stale success artifacts")
    args.output.mkdir(parents=True,exist_ok=True)
    status=args.output/"status.json"
    status.write_text(json.dumps({"status":"building"})+"\n")
    try:
        source=args.recording
        if not args.normalized:
            source=args.output/"trace.ndjson"
            subprocess.run([args.java,str(Path(__file__).with_name("SimulationTraceExport.java")),str(args.recording),str(source)],check=True)
        if source.stat().st_size>512<<20: raise ValueError("normalized trace exceeds512MiB; no partial success")
        with source.open() as f: trace=Trace(json.loads(line) for line in f if line.strip())
        result=build(trace)
        with args.recording.open("rb") as f: digest=hashlib.file_digest(f,"sha256").hexdigest()
        result["input"]={"path":str(args.recording.resolve()),"sha256":digest}
        capture=args.recording.parent/"capture.json"
        if capture.exists():
            evidence=json.loads(capture.read_text())
            if evidence.get("recordingSha256")==digest:result["capture"]=evidence
        def write(name,value):
            with (args.output/name).open("w") as f:json.dump(value,f,ensure_ascii=True,separators=(",",":"))
        write("report.json",result);write("timeline.perfetto.json",perfetto(result))
        template=Path(__file__).with_name("simulation_delay")/"timeline.html"
        data=json.dumps(result,ensure_ascii=True,separators=(",",":")).replace("<","\\u003c")
        (args.output/"timeline.html").write_text(template.read_text().replace("/*TRACE_DATA*/null",data))
        summary={"status":"reported","quality":result["quality"],"worlds":[{k:v for k,v in w.items() if k!="partition"} for w in result["worlds"]]}
        status.write_text(json.dumps(summary,indent=2)+"\n")
        display={**summary,"worlds":[{**{k:v for k,v in w.items() if k!="blockingTasksNs"},
                    "topBlockingTasksNs":dict(sorted(w.get("blockingTasksNs",{}).items(),key=lambda e:-e[1])[:10])} for w in summary["worlds"]]}
        print(json.dumps(display,indent=2))
        print("Timeline:",args.output/"timeline.html")
    except Exception as error:
        status.write_text(json.dumps({"status":"failed","error":str(error)})+"\n")
        raise

if __name__=="__main__":main()
