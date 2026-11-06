#!/usr/bin/env python3
"""Reproducible private Medieval Spruce adaptation; no source download or game writes.

Input sources and their SHA256, local entrances/mounts and explicit functional ports
are declared in settlement_sources.json. Requires nbtlib==2.0.4.
"""
import argparse, base64, gzip, hashlib, io, json
from pathlib import Path
import nbtlib
from nbtlib import Compound, List, Int, String

DIRECTIONS={'NORTH':(0,-1),'SOUTH':(0,1),'WEST':(-1,0),'EAST':(1,0)}
def adapt(state):
 name=state['Name']; props=dict(state.get('Properties',{}))
 if name in {'minecraft:air','structurize:blocksubstitution','structurize:blocksolidsubstitution'} or name.startswith('minecolonies:blockhut'):return None
 if not name.startswith(('minecraft:','domum_ornamentum:')) and name!='minecolonies:blockminecoloniesrack':
  raise ValueError('undeclared source dependency '+name)
 return name,props

def visual_data(tile):
 kind=str(tile['id'])
 if kind=='domum_ornamentum:materially_retexturable':return 1,Compound({'textureData':tile['textureData']})
 if kind in {'minecraft:sign','minecraft:hanging_sign'}:
  return 2,Compound({k:v for k,v in tile.items() if k in {'front_text','back_text','is_waxed'}})
 if kind=='minecraft:banner' and ('Patterns' in tile or 'patterns' in tile):
  return 3,Compound({k:v for k,v in tile.items() if k in {'Patterns','patterns'}})
 return None

def build(spec,source,out):
 raw=source.read_bytes()
 if hashlib.sha256(raw).hexdigest()!=spec['sha256']:raise ValueError('unpinned source '+str(source))
 n=nbtlib.load(source);sx,sy,sz=[int(n[k]) for k in ('size_x','size_y','size_z')];pal=[v.unpack() for v in n['palette']];ids=[]
 for w in n['blocks']:w=int(w)&0xffffffff;ids.extend((w>>16,w&65535))
 if len(ids)<sx*sy*sz or len(ids)>sx*sy*sz+1:raise ValueError('invalid source dimensions')
 floor=spec['floor'];cut=0;cells={};data={}
 for i,v in enumerate(ids[:sx*sy*sz]):
  x=i%sx;z=i//sx%sz;y=i//(sx*sz)
  if y<cut:continue
  value=adapt(pal[v])
  if value:cells[x,y,z]=value
 for tile in n['tile_entities']:
  pos=tuple(int(tile[k]) for k in 'xyz'); value=visual_data(tile)
  if value is not None:
   if pos not in cells:continue
   if value[0]==1 and not cells[pos][0].startswith('domum_ornamentum:'):continue # Stale source tile on a replaced vanilla bed, not an architectural material.
   data[pos]=(cells[pos][0],value[0],int(n['mcversion']),value[1])
 # Explicit adaptation: a narrow supported entrance and named stations. No
 # runtime inference/paste, inventories or foreign block-entity data are imported.
 door=tuple(spec['door']);dx,dz=DIRECTIONS[spec['front']]
 rise=floor-spec['groundY']
 path=[(door[0]+dx*i,floor-min(max(i,0),rise),door[1]+dz*i) for i in (3,2,1,0,-1)]
 extra=[]
 if 'secondaryDoor' in spec:
  ex,ez=spec['secondaryDoor'];extra=[(ex+dx*i,floor-min(max(i,0),rise),ez+dz*i) for i in (3,2,1,0,-1)]
 easement=list(path)+extra
 for at in [path[0]]+([extra[0]] if extra else []):
  while -1<=at[0]<=sx and -1<=at[2]<=sz:
   at=(at[0]+dx,spec['groundY'],at[2]+dz);easement.append(at)
 for x,y,z in easement:
  cells.setdefault((x,y,z),('minecraft:cobblestone',{}))
  for h in (1,2):
   p=(x,y+h,z)
   if p in cells and not cells[p][0].endswith('_door'):cells.pop(p)
 anchors={k:tuple(p) for k,p in spec.get('anchors',{}).items()}
 works=spec.get('works',[])+([spec['work']] if 'work' in spec else []);clear=[];mounts=[]
 for work in works:
  socket=tuple(work['socket']);wx,wz=DIRECTIONS[work['front']];worker=(socket[0]+wx,socket[1],socket[2]+wz);inp=tuple(work['input'])
  for p in (socket,worker,inp):
   cells.setdefault(p,('minecraft:cobblestone',{}))
   for h in (1,):cells.pop((p[0],p[1]+h,p[2]),None)
  clear.extend((worker,inp));mounts.append((work,socket,worker,inp))
 for key,p in anchors.items():
  cells.setdefault(p,('minecraft:cobblestone',{}))
  if key=='pantry':cells.pop((p[0],p[1]+1,p[2]),None)
 # Explicit content-owned access and standing areas, independent of function or resource.
 for points in spec.get('walkways',[]):
  at=tuple(points[0])
  for end in map(tuple,points[1:]):
   if at[1]!=end[1]:raise ValueError('declared aisle must retain its surveyed floor')
   while True:
    cells[at]=('minecraft:cobblestone',{})
    for h in (1,2):cells.pop((at[0],at[1]+h,at[2]),None)
    if at==end:break
    at=(at[0]+(1 if end[0]>at[0] else -1) if at[0]!=end[0] else at[0],at[1],
        at[2] if at[0]!=end[0] else at[2]+(1 if end[2]>at[2] else -1))
 for area in spec.get('clearAreas',[]):
  lo,hi=area['min'],area['max']
  for x in range(lo[0],hi[0]):
   for z in range(lo[2],hi[2]):
    cells[x,lo[1]-1,z]=('minecraft:cobblestone',{})
    for y in range(lo[1],hi[1]):cells.pop((x,y,z),None)
 # Preserve explicitly authored excavation below the entry floor. Placeholders retain
 # terrain; only actual source AIR is a void. Higher air is supplied by this flat
 # genesis provider, avoiding a voxel entry for every empty sky cell.
 minx0,minz0=min(p[0] for p in cells),min(p[2] for p in cells)
 maxx0,maxz0=max(p[0] for p in cells),max(p[2] for p in cells)
 for i,v in enumerate(ids[:sx*sy*sz]):
  x=i%sx;z=i//sx%sz;y=i//(sx*sz)
  excavate=any(all(area['min'][j]<=p<area['max'][j] for j,p in enumerate((x,y,z))) for area in spec['excavations'])
  if y<=floor and (y>spec['groundY'] or excavate) and minx0<=x<=maxx0 and minz0<=z<=maxz0 and pal[v]['Name']=='minecraft:air':
   cells.setdefault((x,y,z),('minecraft:air',{}))
 # Translate to positive native coordinates, preserving a one-block foundation.
 minx=min(p[0] for p in cells);minz=min(p[2] for p in cells)
 def point(p):return p[0]-minx,p[1]-cut,p[2]-minz
 data={point(p):v for p,v in data.items() if p in cells and cells[p][0]==v[0]};cells={point(p):v for p,v in cells.items()};path=[point(p) for p in path]
 maxp=tuple(max(p[i] for p in cells)+1 for i in range(3));origin=point((door[0],floor,door[1]))
 prop={'format':'4','site.groundY':str(spec['groundY']),'site.excavations':','.join('void_'+str(i) for i in range(len(spec['excavations']))),'id':'pale_mirror:medieval_spruce/'+spec['name'],'version':'1','functions':spec['kind'],'representation':'AUTHORED','origin':','.join(map(str,origin)),'front':spec['front'],'fabric.min':'0,0,0','fabric.max':','.join(map(str,maxp)),'parcel.min':'-2,-1,-2','parcel.max':','.join(map(str,(maxp[0]+2,maxp[1]+2,maxp[2]+2))), 'rooms':'main','room.main.min':'-1,1,-1','room.main.max':','.join(map(str,maxp)),'zones':'','passages':'front','primaryEntrance':'front','primaryWorkplace':'','passage.front.from.kind':'EXTERIOR','passage.front.from.room':'','passage.front.to.kind':'ROOM','passage.front.to.room':'main','passage.front.public':'true','passage.front.threshold':'3','passage.front.supports':';'.join(','.join(map(str,p)) for p in path),'workplaces':'','anchors':','.join(anchors),'beds':''}
 for i,area in enumerate(spec['excavations']):
  for bound in ('min','max'):prop['site.excavation.void_'+str(i)+'.'+bound]=','.join(map(str,point(area[bound])))
 for key,p in anchors.items():prop['anchor.'+key]=','.join(map(str,point(p)))
 if extra:
  prop['passages']='front,side'
  for key in ['from.kind','from.room','to.kind','to.room','public','threshold']:prop['passage.side.'+key]=prop['passage.front.'+key]
  prop['passage.side.supports']=';'.join(','.join(map(str,point(p))) for p in extra)
 for work,socket,worker,inp in mounts:
  socket=point(socket);worker=point(worker);inp=point(inp);name=work.get('id','work');prop['workplaces']+=(',' if prop['workplaces'] else '')+name
  if not prop['primaryWorkplace']:prop['primaryWorkplace']=name
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
  part='FOUNDATION' if pos[1]<=floor-cut and name!='minecraft:air' else 'WALL'
  if pos in path:part='PUBLIC_ACCESS_SURFACE'
  if any(pos==point(w) for _,_,w,_ in mounts):part='WORKSHOP_PROCESS_STATION'
  if any(pos==point(i) for _,_,_,i in mounts):part='WORKSHOP_INPUT_STATION'
  repair_name=('minecraft:flower_pot' if name.startswith('minecraft:potted_') else name).replace('_wall_hanging_sign','_hanging_sign').replace('_wall_sign','_sign').replace('_wall_banner','_banner')
  repair={'minecraft:dirt_path':'minecraft:dirt','minecraft:coarse_dirt':'minecraft:dirt','minecraft:wall_torch':'minecraft:torch','minecraft:water_cauldron':'minecraft:cauldron','minecraft:lava_cauldron':'minecraft:cauldron','minecraft:water':'minecraft:bucket','minecraft:tripwire':'minecraft:string'}.get(name,repair_name)
  material='2|'+name+'|'+repair+'|'+','.join(k+'='+v for k,v in sorted(props.items()))
  if pos in data:
   _,kind,version,payload=data[pos]
   material='3'+material[1:]+'|'+str(kind)+'|'+str(version)+'|'+base64.urlsafe_b64encode(payload.snbt().encode()).decode().rstrip('=')
  lines.append(','.join(map(str,pos))+'\t'+material+'\t'+part)
  block_tags.append(Compound({'pos':List[Int](pos),'state':Int(lookup[(name,tuple(sorted(props.items())))]) ,**({'nbt':data[pos][3]} if pos in data else {})}))
 native=nbtlib.File({'DataVersion':Int(3955),'size':List[Int](maxp),'palette':List[Compound]([Compound({'Name':String(name),**({'Properties':Compound({k:String(v) for k,v in props})} if props else {})}) for name,props in states]),'blocks':List[Compound](block_tags),'entities':List[Compound]([])})
 stream=io.BytesIO();native.write(stream);out.mkdir(parents=True,exist_ok=True);base=out/spec['name']
 base.with_suffix('.nbt').write_bytes(gzip.compress(stream.getvalue(),mtime=0));base.with_suffix('.blocks').write_text('\n'.join(lines)+'\n');base.with_suffix('.properties').write_text('\n'.join(k+'='+v for k,v in sorted(prop.items()))+'\n')
 # Certificates are produced by the registered Minecraft shape compiler, never fabricated.
 geometry=base.with_suffix('.geometry')
 if not geometry.exists():geometry.write_text('# Native collision certificate pending authoring compilation.\n')
 base.with_suffix('.provenance.json').write_text(json.dumps({'sourceRef':'a9b03edfbbd5359b148c85284ea038066d689866','source':spec['source'],'sourceSha256':spec['sha256'],'license':'private test derivative; upstream ARR','adaptations':['original native blocks, shapes and materials retained with pinned dependencies','terrain placeholders and colony controllers omitted; complete source foundation retained; surveyed ground datum and explicit excavation volumes distinguish terrain from source air','supported open entrance','declared machine/operator aisle and service points','no entities, inventories or foreign controllers; typed visual data retained'],'blocks':len(cells),'beds':len(beds)},indent=2)+'\n')
 print(spec['name'],len(cells),'beds',len(beds))

def main():
 ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--sources',type=Path,required=True);ap.add_argument('--housing',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);ap.add_argument('--name');a=ap.parse_args()
 for spec in json.loads(Path(__file__).with_name('settlement_sources.json').read_text()):
  if a.name and spec['name']!=a.name:continue
  source=(a.housing if spec['kind']=='HOUSING' else a.sources)/Path(spec['source']).name
  build(spec,source,a.output)
if __name__=='__main__':main()
