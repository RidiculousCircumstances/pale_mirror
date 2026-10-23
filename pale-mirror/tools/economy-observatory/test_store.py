import tempfile
import unittest
from pathlib import Path
from store import Store

class Observations(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.store=Store(Path(self.tmp.name)/'test.sqlite')
    def batch(self,records,session='one'):
        return dict(schema=1,world='world',name='Test',session=session,records=records)
    def sample(self,tick=100,revision=1):
        return dict(id='snapshot:'+str(revision),kind='snapshot',tick=tick,revision=revision,status='ACTIVE',settlements=[],stocks=[])
    def sale(self):
        return dict(id='accepted-1',kind='sale',tick=99,revision=1,seller='a',buyer='b',item='minecraft:bread',quantity=3,goodsUnitRaw=2500000,deliveryUnitRaw=500000)
    def test_replay_after_commit_and_process_restart_is_not_a_second_sale(self):
        batch=self.batch([self.sample(),self.sale()]);self.store.ingest(batch);self.store.ingest(batch)
        self.store=Store(self.store.path);self.store.ingest(batch)
        result=self.store.view({});self.assertEqual(result['matrix'][0]['quantity'],3)
        self.assertEqual(result['matrix'][0]['goods_raw'],7500000)
        self.assertEqual(result['matrix'][0]['delivery_raw'],1500000)
        self.assertEqual(result['daily'][0]['receipts'],1)
        self.assertEqual(self.store.view({'town':'absent'})['matrix'],[])
        self.store.ingest(self.batch([self.sample(120,2)],'two'))
        self.assertEqual(self.store.view({})['gaps'][0]['reason'],'process_restart')
    def test_invalid_batch_is_atomic(self):
        bad=self.sale();bad['quantity']=-1
        with self.assertRaises(ValueError):self.store.ingest(self.batch([self.sample(),bad]))
        self.assertEqual(self.store.view({})['worlds'],[])
    def test_pruned_receipt_cannot_reinflate_daily_aggregate(self):
        batch=self.batch([self.sample(),self.sale()]);self.store.ingest(batch)
        self.store.ingest(self.batch([self.sample(24000*31,2)]));self.store.prune()
        self.store.ingest(batch)
        with self.store.connect() as db:
            self.assertEqual(db.execute('SELECT sum(receipts) FROM trade_days').fetchone()[0],1)
        self.assertEqual(self.store.view({})['sales'],[])
    def test_direction_filters_intersect_with_town_and_resource_before_aggregation(self):
        first=self.sale()
        reverse={**first,'id':'reverse','seller':'b','buyer':'a','quantity':10}
        other={**first,'id':'other','seller':'a','buyer':'c','quantity':20}
        self.store.ingest(self.batch([self.sample(),first,reverse,other]))
        view=self.store.view({'seller':'a','buyer':'b','town':'b','item':'minecraft:bread'})
        self.assertEqual([s['id'] for s in view['sales']],['accepted-1'])
        self.assertEqual(view['matrix'][0]['quantity'],3)
        self.assertEqual(view['daily'][0]['quantity'],3)
        self.assertEqual(self.store.view({'seller':'a','buyer':'b','town':'c'})['matrix'],[])
if __name__=='__main__': unittest.main()
