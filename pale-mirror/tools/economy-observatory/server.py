#!/usr/bin/env python3
"""Read-only v3 economic dashboard. SQLite and HTTP never run inside Minecraft."""
import argparse
import csv
import io
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from store import Store
from performance import Performance
from players import Players

WEB = Path(__file__).with_name('web')
MAX_BATCH_BYTES = 8*1024*1024

def observation_directory(source):
    properties=source/'server.properties'
    if not properties.exists(): return source
    fields=dict(line.split('=',1) for line in properties.read_text().splitlines() if '=' in line and not line.startswith('#'))
    name=fields.get('level-name','world')
    world=(source/name).resolve()
    if not world.is_relative_to(source.resolve()): raise ValueError('world is outside runtime')
    return world/'economy-analytics'

def collect(source, store, stopping):
    iteration=0
    while not stopping.is_set():
        try:
            directory=observation_directory(source)
            paths=sorted(directory.glob('batch-*.json'))[:64] if directory.exists() else []
            for path in paths:
                if path.stat().st_size>MAX_BATCH_BYTES:
                    raise ValueError('analytics batch exceeds size bound: '+path.name)
                store.ingest(json.loads(path.read_text()))
                path.unlink()  # Only after SQLite commit; replay is idempotent after a crash here.
            if iteration % 30 == 0: store.prune()
            store.last_error=None
        except Exception as exc:
            store.last_error=type(exc).__name__+': '+str(exc)
        iteration+=1
        stopping.wait(1)

class Server(ThreadingHTTPServer):
    daemon_threads=True
    request_queue_size=16
    def __init__(self, address, store, performance, players):
        self.store=store
        self.performance=performance
        self.players=players
        self.requests=threading.BoundedSemaphore(8)
        super().__init__(address,Handler)
    def process_request(self, request, client_address):
        if not self.requests.acquire(blocking=False):
            request.close(); return
        try: super().process_request(request,client_address)
        except Exception:
            self.requests.release(); raise
    def process_request_thread(self, request, client_address):
        try: super().process_request_thread(request,client_address)
        finally: self.requests.release()

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args): pass
    def do_GET(self):
        self.connection.settimeout(10)
        url=urlsplit(self.path)
        try:
            if url.path == '/api/performance':
                self.send(json.dumps(self.server.performance.view(),separators=(',',':')).encode(),'application/json')
            elif url.path == '/api/players':
                self.send(json.dumps(self.server.players.view(),separators=(',',':')).encode(),'application/json')
            elif url.path in ('/api/view','/api/export.csv'):
                params={k:v[-1] for k,v in parse_qs(url.query).items()}
                data=self.server.store.view(params)
                if url.path.endswith('.csv'):
                    table=params.get('table','matrix')
                    if table not in ('matrix','sales','flows','daily'): raise ValueError('unsupported table')
                    rows=data.get(table,[]); output=io.StringIO()
                    if rows:
                        writer=csv.DictWriter(output,fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)
                    self.send(output.getvalue().encode(),'text/csv; charset=utf-8')
                else: self.send(json.dumps(data,separators=(',',':')).encode(),'application/json')
            elif url.path in ('/','/index.html','/app.js','/map.js','/style.css'):
                name='index.html' if url.path=='/' else url.path[1:]
                self.send((WEB/name).read_bytes(),{'html':'text/html; charset=utf-8','js':'text/javascript; charset=utf-8','css':'text/css; charset=utf-8'}[name.split('.')[-1]])
            else: self.send(b'Not found','text/plain',404)
        except (ValueError,KeyError) as exc: self.send(str(exc).encode(),'text/plain; charset=utf-8',400)
        except (BrokenPipeError,ConnectionResetError): pass
        except Exception as exc: self.send(str(exc).encode(),'text/plain; charset=utf-8',503)
    def send(self,body,kind,status=200):
        self.send_response(status)
        self.send_header('Content-Type',kind); self.send_header('Content-Length',str(len(body)))
        self.send_header('Cache-Control','no-store'); self.send_header('X-Content-Type-Options','nosniff')
        self.end_headers(); self.wfile.write(body)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',required=True,type=Path)
    parser.add_argument('--database',required=True,type=Path)
    parser.add_argument('--bind',default='127.0.0.1'); parser.add_argument('--port',type=int,default=8093)
    args=parser.parse_args(); store=Store(args.database); stop=threading.Event()
    worker=threading.Thread(target=collect,args=(args.source,store,stop),daemon=True,name='economic-collector');worker.start()
    performance=Performance(args.source)
    monitor=threading.Thread(target=performance.collect,args=(stop,),daemon=True,name="performance-collector");monitor.start()
    players=Players(args.source)
    positions=threading.Thread(target=players.collect,args=(stop,),daemon=True,name="player-position-collector");positions.start()
    server=Server((args.bind,args.port),store,performance,players)
    try: server.serve_forever()
    finally: stop.set();server.server_close();worker.join(timeout=3);monitor.join(timeout=3);positions.join(timeout=3)
if __name__=='__main__': main()
