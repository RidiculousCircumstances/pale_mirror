"""Disposable analytical read models. Never opens Minecraft's canonical save files."""
from contextlib import contextmanager
import json
import sqlite3
import time
from pathlib import Path

SCHEMA = '''
CREATE TABLE IF NOT EXISTS events(world TEXT, id TEXT, tick INTEGER, kind TEXT, PRIMARY KEY(world,id));
CREATE TABLE IF NOT EXISTS worlds(world TEXT PRIMARY KEY, name TEXT, first_tick INTEGER, last_tick INTEGER, revision INTEGER, received REAL, session TEXT, status TEXT);
CREATE TABLE IF NOT EXISTS samples(world TEXT, revision INTEGER, tick INTEGER, body TEXT, PRIMARY KEY(world,revision));
CREATE INDEX IF NOT EXISTS samples_time ON samples(world,tick);
CREATE TABLE IF NOT EXISTS sales(world TEXT, id TEXT, tick INTEGER, seller TEXT, buyer TEXT, item TEXT, quantity INTEGER, goods_raw INTEGER, delivery_raw INTEGER, PRIMARY KEY(world,id));
CREATE INDEX IF NOT EXISTS sales_time ON sales(world,tick);
CREATE TABLE IF NOT EXISTS trade_days(world TEXT, day INTEGER, seller TEXT, buyer TEXT, item TEXT, quantity INTEGER, goods_raw INTEGER, delivery_raw INTEGER, receipts INTEGER, PRIMARY KEY(world,day,seller,buyer,item));
CREATE TABLE IF NOT EXISTS gaps(world TEXT, id TEXT, tick INTEGER, reason TEXT, body TEXT, PRIMARY KEY(world,id));
CREATE TABLE IF NOT EXISTS flows(world TEXT, id TEXT, tick INTEGER, town TEXT, item TEXT, category TEXT, quantity INTEGER, PRIMARY KEY(world,id));
CREATE INDEX IF NOT EXISTS flows_time ON flows(world,tick);
'''

class Store:
    def __init__(self, path):
        self.path = str(path)
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.executescript(SCHEMA)
            db.execute('PRAGMA journal_mode=WAL')
            db.execute('PRAGMA max_page_count=262144')  # 1 GiB at the SQLite default 4096-byte page size
        self.last_error = None
        self.imported = 0

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=2)
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA busy_timeout=2000')
        try:
            with db: yield db
        finally:
            db.close()

    def ingest(self, batch):
        if batch.get('schema') != 1 or not isinstance(batch.get('records'), list):
            raise ValueError('unsupported analytics batch')
        world = batch['world']
        session = batch['session']
        with self.connect() as db:
            watermark = db.execute('SELECT last_tick FROM worlds WHERE world=?', (world,)).fetchone()
            floor = max(0,watermark['last_tick']-24000*30) if watermark else 0
            for row in batch['records']:
                tick, revision = int(row['tick']), int(row['revision'])
                identity, kind = row['id'], row['kind']
                # A stale replay outside dedup coverage must not increment aggregates again.
                if tick < floor: continue
                if min(tick, revision) < 0:
                    raise ValueError('negative canonical watermark')
                inserted = db.execute('INSERT OR IGNORE INTO events VALUES (?,?,?,?)', (world,identity,tick,kind)).rowcount
                if not inserted:
                    continue
                if kind == 'snapshot':
                    old = db.execute('SELECT * FROM worlds WHERE world=?', (world,)).fetchone()
                    # No fabricated continuity across process crashes/restarts: bounded tail
                    # import can restore sales, but cannot prove all other observations.
                    if old and old['session'] != session:
                        db.execute('INSERT OR IGNORE INTO gaps VALUES (?,?,?,?,?)',
                                   (world,'session:'+session,tick,'process_restart',json.dumps({'previousRevision':old['revision'],'nextRevision':revision})))
                    db.execute('INSERT INTO samples VALUES (?,?,?,?) ON CONFLICT(world,revision) DO UPDATE SET body=excluded.body', (world,revision,tick,json.dumps(row,separators=(',',':'))))
                    if not old or revision >= old['revision']:
                        db.execute('INSERT INTO worlds VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(world) DO UPDATE SET last_tick=excluded.last_tick,revision=excluded.revision,received=excluded.received,session=excluded.session,status=excluded.status',
                                   (world,batch.get('name',world),tick,tick,revision,time.time(),session,row.get('status','ACTIVE')))
                elif kind == 'sale':
                    quantity = int(row['quantity'])
                    goods, delivery = int(row['goodsUnitRaw'])*quantity, int(row['deliveryUnitRaw'])*quantity
                    if quantity <= 0 or goods <= 0 or delivery < 0:
                        raise ValueError('invalid settled sale')
                    db.execute('INSERT INTO sales VALUES (?,?,?,?,?,?,?,?,?)', (world,identity,tick,row['seller'],row['buyer'],row['item'],quantity,goods,delivery))
                    db.execute('INSERT INTO trade_days VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(world,day,seller,buyer,item) DO UPDATE SET quantity=quantity+excluded.quantity,goods_raw=goods_raw+excluded.goods_raw,delivery_raw=delivery_raw+excluded.delivery_raw,receipts=receipts+1',
                               (world,tick//24000,row['seller'],row['buyer'],row['item'],quantity,goods,delivery,1))
                elif kind == 'gap':
                    db.execute('INSERT OR IGNORE INTO gaps VALUES (?,?,?,?,?)',(world,identity,tick,row['reason'],json.dumps(row)))
                elif kind == 'flow':
                    db.execute('INSERT INTO flows VALUES (?,?,?,?,?,?,?)',(world,identity,tick,row['town'],row['item'],row['category'],int(row['quantity'])))
                else:
                    raise ValueError('unknown observation kind '+str(kind))
            self.imported += len(batch['records'])

    def prune(self):
        # Exact interval queries expose their coverage. Daily trade aggregates survive
        # detailed receipts; dedup IDs remain for the retained bounded spool replay range.
        with self.connect() as db:
            for world in db.execute('SELECT world,last_tick FROM worlds').fetchall():
                identity, tick = world['world'], world['last_tick']
                for table in ('sales','flows','events'):
                    db.execute(f'DELETE FROM {table} WHERE world=? AND tick<?',(identity,tick-24000*30))
                for table in ('sales','flows'):
                    db.execute(f'DELETE FROM {table} WHERE world=? AND id NOT IN (SELECT id FROM {table} WHERE world=? ORDER BY tick DESC LIMIT 100000)',(identity,identity))
                    if db.execute('SELECT changes()').fetchone()[0]:
                        db.execute('INSERT INTO gaps VALUES (?,?,?,?,?) ON CONFLICT(world,id) DO UPDATE SET tick=excluded.tick,body=excluded.body',
                                   (identity,'retention:'+table,tick,'detail_row_retention_limit',json.dumps({'table':table,'limit':100000})))
                db.execute('DELETE FROM samples WHERE world=? AND revision NOT IN (SELECT revision FROM samples WHERE world=? ORDER BY revision DESC LIMIT 4096)',(identity,identity))
                db.execute('DELETE FROM trade_days WHERE world=? AND day<?',(identity,tick//24000-365))
                db.execute('DELETE FROM gaps WHERE world=? AND tick<?',(identity,tick-24000*365))
            # Bound inactive disposable worlds as well as one running world's history.
            obsolete = db.execute('SELECT world FROM worlds ORDER BY received DESC LIMIT -1 OFFSET 8').fetchall()
            for row in obsolete:
                for table in ('events','samples','sales','trade_days','gaps','flows','worlds'):
                    db.execute(f'DELETE FROM {table} WHERE world=?',(row['world'],))

    def view(self, params):
        with self.connect() as db:
            worlds = [dict(r) for r in db.execute('SELECT * FROM worlds ORDER BY received DESC')]
            world = params.get('world') or (worlds[0]['world'] if worlds else None)
            info = next((w for w in worlds if w['world']==world),None)
            if not info:
                return {'worlds':worlds,'latest':None,'collectorError':self.last_error}
            end = min(int(params.get('to') or info['last_tick']), info['last_tick'])
            start = max(0,int(params.get('from') or max(0,end-24000)))
            if start>end:
                raise ValueError('start exceeds end')
            condition, values = 'world=? AND tick>=? AND tick<=?', [world,start,end]
            item, town = params.get('item',''), params.get('town','')
            if item: condition += ' AND item=?'; values.append(item)
            if town: condition += ' AND (seller=? OR buyer=?)'; values.extend((town,town))
            for key in ('seller','buyer'):
                if params.get(key): condition+=' AND '+key+'=?'; values.append(params[key])
            sales = [dict(r) for r in db.execute('SELECT * FROM sales WHERE '+condition+' ORDER BY tick DESC,id LIMIT 500',values)]
            matrix = [dict(r) for r in db.execute('SELECT seller,buyer,item,sum(quantity) quantity,sum(goods_raw) goods_raw,sum(delivery_raw) delivery_raw,count(*) receipts FROM sales WHERE '+condition+' GROUP BY seller,buyer,item ORDER BY goods_raw DESC LIMIT 2048',values)]
            daily_condition, daily_values = 'world=? AND day>=? AND day<=?', [world,start//24000,end//24000]
            if item: daily_condition+=' AND item=?'; daily_values.append(item)
            if town: daily_condition+=' AND (seller=? OR buyer=?)'; daily_values.extend((town,town))
            for key in ('seller','buyer'):
                if params.get(key): daily_condition+=' AND '+key+'=?'; daily_values.append(params[key])
            daily = [dict(r) for r in db.execute('SELECT * FROM trade_days WHERE '+daily_condition+' ORDER BY day DESC LIMIT 4096',daily_values)]
            snapshot=db.execute('SELECT body FROM samples WHERE world=? ORDER BY revision DESC LIMIT 1',(world,)).fetchone()
            latest=json.loads(snapshot['body']) if snapshot else None
            sample_rows=db.execute('SELECT tick,revision FROM samples WHERE world=? AND tick>=? AND tick<=? ORDER BY revision',(world,start,end)).fetchall()
            history=[]
            step=max(1,(len(sample_rows)+239)//240)
            for r in sample_rows[::step]:
                sample=db.execute('SELECT body FROM samples WHERE world=? AND revision=?',(world,r['revision'])).fetchone()
                data=json.loads(sample['body']); history.append({'tick':r['tick'],'settlements':[s for s in data['settlements'] if not town or s['id']==town],
                    'stocks':[s for s in data['stocks'] if (not town or s['town']==town) and (not item or s['item']==item)]})
            gaps=[dict(r) for r in db.execute('SELECT * FROM gaps WHERE world=? ORDER BY tick DESC LIMIT 100',(world,))]
            flow_condition, flow_values='world=? AND tick>=? AND tick<=?',[world,start,end]
            if item: flow_condition+=' AND item=?'; flow_values.append(item)
            if town: flow_condition+=' AND town=?'; flow_values.append(town)
            flows=[dict(r) for r in db.execute('SELECT town,item,category,sum(quantity) quantity FROM flows WHERE '+flow_condition+' GROUP BY town,item,category LIMIT 2048',flow_values)]
            return {'worlds':worlds,'world':info,'latest':latest,'sales':sales,'matrix':matrix,'daily':daily,'history':history,'flows':flows,'gaps':gaps,
                    'from':start,'to':end,'detailFrom':max(info['first_tick'],info['last_tick']-24000*30, db.execute('SELECT COALESCE(min(tick),0) FROM sales WHERE world=?',(world,)).fetchone()[0]),'ageSeconds':max(0,time.time()-info['received']),
                    'collectorError':self.last_error,'limits':{'sales':500,'matrix':2048,'samples':240,'detailDays':30,'dailyDays':365}}
