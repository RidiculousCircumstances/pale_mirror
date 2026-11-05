#!/usr/bin/env python3
"""Reproducible private Medieval Spruce adaptation; no source download or game writes.

Input sources and their SHA256, local entrances/mounts and permitted substitutions
are declared in settlement_sources.json. Requires nbtlib==2.0.4.
"""
import argparse, gzip, hashlib, io, json
from pathlib import Path
import nbtlib
from nbtlib import Compound, List, Int, String

DIRECTIONS={'NORTH':(0,-1),'SOUTH':(0,1),'WEST':(-1,0),'EAST':(1,0)}
SUBSTITUTIONS={
 'domum_ornamentum:plain':'minecraft:white_terracotta',
 'domum_ornamentum:side_framed':'minecraft:spruce_planks',
 'domum_ornamentum:double_crossed':'minecraft:white_terracotta',
 'domum_ornamentum:blockbarreldeco_standing':'minecraft:barrel',
 'domum_ornamentum:blockbarreldeco_onside':'minecraft:barrel',
 'domum_ornamentum:one_crossed_lr':'minecraft:white_terracotta',
 'domum_ornamentum:one_crossed_rl':'minecraft:white_terracotta',
 'domum_ornamentum:horizontal_plain':'minecraft:spruce_planks',
 'domum_ornamentum:framed':'minecraft:spruce_planks',
 'domum_ornamentum:brown_bricks':'minecraft:bricks',
 'minecolonies:blockminecoloniesrack':'minecraft:barrel',
}

def adapt(state):
 name=state['Name']; props=dict(state.get('Properties',{}))
 if name in {'minecraft:air','structurize:blocksubstitution','structurize:blocksolidsubstitution'} or name.startswith('minecolonies:blockhut'):return None
 if name=='minecraft:water':return 'minecraft:coarse_dirt',{}
 if name in {'minecraft:poppy','minecraft:fern','minecraft:red_tulip','minecraft:blue_orchid','minecraft:azure_bluet','minecraft:allium','minecraft:oxeye_daisy','minecraft:cornflower','minecraft:white_tulip','minecraft:orange_tulip','minecraft:pink_tulip','minecraft:lily_of_the_valley','minecraft:dandelion'}:return name.replace('minecraft:','minecraft:potted_'),{}
 if name in SUBSTITUTIONS:
  target=SUBSTITUTIONS[name]
  return target,({'facing':props['facing'],'open':'false'} if target=='minecraft:barrel' else {})
 if name in {'domum_ornamentum:shingle','domum_ornamentum:vanilla_stairs_compat'}:return 'minecraft:spruce_stairs',props
 if name=='domum_ornamentum:vanilla_wall_compat':return 'minecraft:cobblestone_wall',props
 if name=='domum_ornamentum:shingle_slab':return 'minecraft:spruce_slab',{'type':'bottom','waterlogged':'false'}
 if name=='domum_ornamentum:panel':return 'minecraft:spruce_trapdoor',{**{k:v for k,v in props.items() if k!='type'},'powered':'false'}
 if not name.startswith('minecraft:'):raise ValueError('undeclared source dependency '+name)
 if name.endswith('_door'):props['open']='true'
 return name,props

def build(spec,source,out):
 raw=source.read_bytes()
 if hashlib.sha256(raw).hexdigest()!=spec['sha256']:raise ValueError('unpinned source '+str(source))
 n=nbtlib.load(source);sx,sy,sz=[int(n[k]) for k in ('size_x','size_y','size_z')];pal=[v.unpack() for v in n['palette']];ids=[]
 for w in n['blocks']:w=int(w)&0xffffffff;ids.extend((w>>16,w&65535))
 if len(ids)<sx*sy*sz or len(ids)>sx*sy*sz+1:raise ValueError('invalid source dimensions')
 floor=spec['floor'];cut=floor-1;cells={}
 for i,v in enumerate(ids[:sx*sy*sz]):
  x=i%sx;z=i//sx%sz;y=i//(sx*sz)
  if y<cut:continue
  value=adapt(pal[v])
  if value:cells[x,y,z]=value
 # Explicit adaptation: a narrow supported entrance and named stations. No
 # runtime inference/paste, inventories or foreign block-entity data are imported.
 door=tuple(spec['door']);dx,dz=DIRECTIONS[spec['front']]
 path=[(door[0]+dx*i,floor,door[1]+dz*i) for i in (3,2,1,0,-1)]
 extra=[]
 if 'secondaryDoor' in spec:
  ex,ez=spec['secondaryDoor'];extra=[(ex+dx*i,floor,ez+dz*i) for i in (3,2,1,0,-1)]
 easement=list(path)+extra
 for at in [path[0]]+([extra[0]] if extra else []):
  while -1<=at[0]<=sx and -1<=at[2]<=sz:
   at=(at[0]+dx,floor,at[2]+dz);easement.append(at)
 for x,y,z in easement:
  cells[x,y,z]=('minecraft:cobblestone',{})
  for h in (1,2):
   p=(x,y+h,z)
   if p in cells and not cells[p][0].endswith('_door'):cells.pop(p)
 anchors={k:tuple(p) for k,p in spec.get('anchors',{}).items()}
 work=spec.get('work');clear=[]
 if work:
  socket=tuple(work['socket']);wx,wz=DIRECTIONS[work['front']];worker=(socket[0]+wx,socket[1],socket[2]+wz);inp=tuple(work['input'])
  for p in (socket,worker,inp):
   cells[p]=('minecraft:cobblestone',{})
   for h in (1,2,3):cells.pop((p[0],p[1]+h,p[2]),None)
  # Declared narrow staff aisle from entrance to work; the source yard/building
  # keeps all other furniture and architecture.
  at=path[-1]
  while at[0]!=worker[0] or at[2]!=worker[2]:
   ax,ay,az=at
   at=(ax+(1 if worker[0]>ax else -1) if ax!=worker[0] else ax,ay,
       az if ax!=worker[0] else az+(1 if worker[2]>az else -1))
   if ay!=worker[1]:raise ValueError('workplace needs a declared same-level aisle')
   cells[at]=('minecraft:cobblestone',{})
   for h in (1,2):
    p=(at[0],at[1]+h,at[2])
    if p!= (socket[0],socket[1]+1,socket[2]):cells.pop(p,None)
  clear.extend((worker,inp))
 for key,p in anchors.items():
  cells[p]=('minecraft:cobblestone',{})
  for h in (1,2):cells.pop((p[0],p[1]+h,p[2]),None)
 # Translate to positive native coordinates, preserving a one-block foundation.
 minx=min(p[0] for p in cells);minz=min(p[2] for p in cells)
 def point(p):return p[0]-minx,p[1]-cut,p[2]-minz
 cells={point(p):v for p,v in cells.items()};path=[point(p) for p in path]
 maxp=tuple(max(p[i] for p in cells)+1 for i in range(3));origin=point((door[0],floor,door[1]))
 prop={'format':'3','id':'pale_mirror:medieval_spruce/'+spec['name'],'version':'1','functions':spec['kind'],'representation':'AUTHORED','origin':','.join(map(str,origin)),'front':spec['front'],'fabric.min':'0,0,0','fabric.max':','.join(map(str,maxp)),'parcel.min':'-2,-1,-2','parcel.max':','.join(map(str,(maxp[0]+2,maxp[1]+2,maxp[2]+2))), 'rooms':'main','room.main.min':'-1,1,-1','room.main.max':','.join(map(str,maxp)),'zones':'','passages':'front','primaryEntrance':'front','primaryWorkplace':'','passage.front.from.kind':'EXTERIOR','passage.front.from.room':'','passage.front.to.kind':'ROOM','passage.front.to.room':'main','passage.front.public':'true','passage.front.threshold':'3','passage.front.supports':';'.join(','.join(map(str,p)) for p in path),'workplaces':'','anchors':','.join(anchors),'beds':''}
 for key,p in anchors.items():prop['anchor.'+key]=','.join(map(str,point(p)))
 if extra:
  prop['passages']='front,side'
  for key in ['from.kind','from.room','to.kind','to.room','public','threshold']:prop['passage.side.'+key]=prop['passage.front.'+key]
  prop['passage.side.supports']=';'.join(','.join(map(str,point(p))) for p in extra)
 if work:
  socket=point(socket);worker=point(worker);inp=point(inp);name='work';prop.update({'workplaces':name,'primaryWorkplace':name})
  p='workplace.'+name
  for key,value in {'room':'main','socket':socket,'worker':worker,'input':inp,'equipmentType':work['type'],'facing':work['front'],'equipment.min':(socket[0],socket[1]+1,socket[2]),'equipment.max':(socket[0]+1,socket[1]+3,socket[2]+1),'maintenance.min':(worker[0],worker[1]+1,worker[2]),'maintenance.max':(worker[0]+1,worker[1]+3,worker[2]+1)}.items():prop[p+'.'+key]=value if isinstance(value,str) else ','.join(map(str,value))
 beds=[]
 for head,(name,props) in sorted(cells.items()):
  if not name.endswith('_bed') or props.get('part')!='head':continue
  vx,vz=DIRECTIONS[props['facing'].upper()];foot=(head[0]-vx,head[1],head[2]-vz)
  if cells.get(foot,('',''))[0]!=name:raise ValueError('incomplete source bed')
  bid='bed_'+str(len(beds)+1);beds.append(bid)
  for key,p in [('head',head),('foot',foot)]:prop['bed.'+bid+'.'+key]=','.join(map(str,p))
 prop['beds']=','.join(beds)
 for zone in spec.get('zones',[]):
  name=zone['id'];prop['zones']+=(',' if prop['zones'] else '')+name
  for key,value in {'room':'main','use':zone['use'],'min':point(zone['min']),'max':point(zone['max']),'categories':','.join(zone.get('categories',[])),'priority':str(zone.get('priority',0))}.items():
   prop['zone.'+name+'.'+key]=value if isinstance(value,str) else ','.join(map(str,value))
 states=sorted(set((n,tuple(sorted(p.items()))) for n,p in cells.values()));lookup={v:i for i,v in enumerate(states)}
 block_tags=[];lines=['# Pinned Medieval Spruce private adaptation; no block NBT or entities.']
 for pos,(name,props) in sorted(cells.items()):
  part='FOUNDATION' if pos[1]<=1 else 'WALL'
  if pos in path:part='PUBLIC_ACCESS_SURFACE'
  if work and pos==worker:part='WORKSHOP_PROCESS_STATION'
  if work and pos==inp:part='WORKSHOP_INPUT_STATION'
  repair_name=('minecraft:flower_pot' if name.startswith('minecraft:potted_') else name).replace('_wall_hanging_sign','_hanging_sign').replace('_wall_sign','_sign').replace('_wall_banner','_banner')
  repair={'minecraft:dirt_path':'minecraft:dirt','minecraft:coarse_dirt':'minecraft:dirt','minecraft:wall_torch':'minecraft:torch','minecraft:water_cauldron':'minecraft:cauldron','minecraft:lava_cauldron':'minecraft:cauldron','minecraft:water':'minecraft:bucket','minecraft:tripwire':'minecraft:string'}.get(name,repair_name)
  lines.append(','.join(map(str,pos))+'\t2|'+name+'|'+repair+'|'+','.join(k+'='+v for k,v in sorted(props.items()))+'\t'+part)
  block_tags.append(Compound({'pos':List[Int](pos),'state':Int(lookup[(name,tuple(sorted(props.items())))])}))
 native=nbtlib.File({'DataVersion':Int(3955),'size':List[Int](maxp),'palette':List[Compound]([Compound({'Name':String(name),**({'Properties':Compound({k:String(v) for k,v in props})} if props else {})}) for name,props in states]),'blocks':List[Compound](block_tags),'entities':List[Compound]([])})
 stream=io.BytesIO();native.write(stream);out.mkdir(parents=True,exist_ok=True);base=out/spec['name']
 base.with_suffix('.nbt').write_bytes(gzip.compress(stream.getvalue(),mtime=0));base.with_suffix('.blocks').write_text('\n'.join(lines)+'\n');base.with_suffix('.properties').write_text('\n'.join(k+'='+v for k,v in sorted(prop.items()))+'\n')
 # Certificates are produced by the registered Minecraft shape compiler, never fabricated.
 geometry=base.with_suffix('.geometry')
 if not geometry.exists():geometry.write_text('# Native collision certificate pending authoring compilation.\n')
 base.with_suffix('.provenance.json').write_text(json.dumps({'sourceRef':'a9b03edfbbd5359b148c85284ea038066d689866','source':spec['source'],'sourceSha256':spec['sha256'],'license':'private test derivative; upstream ARR','adaptations':['explicit vanilla dependency substitutions','cropped buried foundation and terrain placeholders; graybox environmental water replaced by coarse dirt','supported open entrance','declared machine/operator aisle and service points','no entities or block-entity NBT'],'blocks':len(cells),'beds':len(beds)},indent=2)+'\n')
 print(spec['name'],len(cells),'beds',len(beds))

def main():
 ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--sources',type=Path,required=True);ap.add_argument('--housing',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);a=ap.parse_args()
 for spec in json.loads(Path(__file__).with_name('settlement_sources.json').read_text()):
  source=(a.housing if spec['kind']=='HOUSING' else a.sources)/Path(spec['source']).name
  build(spec,source,a.output)
if __name__=='__main__':main()
