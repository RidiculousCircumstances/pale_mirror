#!/usr/bin/env python3
"""Bounded read-only capture against one exact service-owned JVM; no reset or domain command."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time


def run(*args): return subprocess.check_output(args,text=True).strip()
def sha(path,algorithm="sha256"):
    with path.open("rb") as f:return hashlib.file_digest(f,algorithm).hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--service",required=True);p.add_argument("--runtime",type=Path,required=True)
    p.add_argument("--artifact",type=Path,required=True);p.add_argument("--source-ref",required=True)
    p.add_argument("--source-repo",type=Path,required=True)
    p.add_argument("--seconds",type=int,default=30);p.add_argument("--output",type=Path,required=True)
    p.add_argument("--jdk",type=Path,default=Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else None)
    a=p.parse_args();runtime=a.runtime.resolve();out=a.output.resolve()
    if not 10<=a.seconds<=120:p.error("capture must be bounded to10–120seconds")
    if out.exists() and any(out.iterdir()):p.error("output must be new/empty")
    cg=run("systemctl","--user","show",a.service,"-p","ControlGroup","--value")
    if not cg.startswith("/user.slice/"):raise ValueError("not a task-owned user service")
    pids=[]
    for pid in Path("/sys/fs/cgroup",cg.lstrip("/"),"cgroup.procs").read_text().split():
        proc=Path("/proc")/pid
        try:
            if (proc/"exe").resolve().name=="java" and (proc/"cwd").resolve()==runtime:pids.append(pid)
        except FileNotFoundError:pass
    if len(pids)!=1:raise ValueError("expected one exact runtime-owned Java process")
    jars=[f for f in (runtime/"mods").glob("pale_mirror*.jar") if "visuals" not in f.name]
    if len(jars)!=1 or sha(jars[0],"sha512")!=sha(a.artifact,"sha512"):raise ValueError("installed artifact identity mismatch")
    ref=run("git","-C",str(a.source_repo),"rev-parse",a.source_ref)
    if run("git","-C",str(a.source_repo),"rev-parse","HEAD")!=ref or run("git","-C",str(a.source_repo),"status","--porcelain","--untracked-files=no"):
        raise ValueError("source must be the clean exact frozen ref")
    if not a.artifact.resolve().is_relative_to(a.source_repo.resolve()):raise ValueError("artifact is outside frozen source")
    out.mkdir(parents=True,exist_ok=True);pid=pids[0];jdk=a.jdk
    java=str(jdk/"bin/java") if jdk else "java";jcmd=str(jdk/"bin/jcmd") if jdk else "jcmd"
    config=Path(__file__).with_name("simulation-delay.jfc").resolve()
    def cpu():
        t=(Path("/proc")/pid/"stat").read_text().rsplit(")",1)[1].split()
        return {"monotonicNs":time.monotonic_ns(),"cpuTicks":int(t[11])+int(t[12]),"birthTicks":int(t[19])}
    before=cpu();identity={"schema":2,"service":a.service,"runtime":str(runtime),"pid":pid,
        "sourceRef":ref,"artifactSha512":sha(a.artifact,"sha512"),"configSha256":sha(config),
        "requestedSeconds":a.seconds,"before":before,"cpuTicksPerSecond":os.sysconf("SC_CLK_TCK"),
        "javaExecutable":str((Path("/proc")/pid/"exe").resolve()),
        "runtimeInputSha256":{str(f.relative_to(runtime)):sha(f) for f in [runtime/"user_jvm_args.txt",runtime/"server.properties",runtime/"config/pale-mirror-server.toml"] if f.is_file()}}
    (out/"capture.json").write_text(json.dumps(identity,indent=2)+"\n")
    try:
        name="pm_causal_"+str(time.time_ns())
        receipt=run(jcmd,pid,"JFR.start","name="+name,"settings="+str(config),"duration="+str(a.seconds)+"s","maxsize=128m","filename="+str(out/"recording.jfr"))
        (out/"jfr-start.txt").write_text(receipt+"\n");print(receipt,flush=True)
        deadline=time.monotonic()+a.seconds+3
        while time.monotonic()<deadline:time.sleep(min(10,max(.01,deadline-time.monotonic())))
        after=cpu()
        if after["birthTicks"]!=before["birthTicks"]:raise ValueError("captured process identity changed")
        identity["after"]=after;identity["processCpuSeconds"]=(after["cpuTicks"]-before["cpuTicks"])/identity["cpuTicksPerSecond"]
        identity["recordingSha256"]=sha(out/"recording.jfr");identity["status"]="captured";(out/"capture.json").write_text(json.dumps(identity,indent=2)+"\n")
        subprocess.run(["python3",str(Path(__file__).with_name("simulation-delay-report.py")),str(out/"recording.jfr"),"--java",java,"--output",str(out/"report")],check=True)
    except Exception as error:
        identity.update(status="failed",error=str(error));(out/"capture.json").write_text(json.dumps(identity,indent=2)+"\n");raise

if __name__=="__main__":main()
