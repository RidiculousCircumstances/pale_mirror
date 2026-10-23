"use strict";
/* Observational X/Z map. All positions belong to the supplied immutable snapshot.
   Pan/zoom are local presentation; no route planning or game requests originate here. */
const WorldMap = (() => {
  const esc = (x) => String(x ?? "").replace(/[&<>"']/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[c]);
  const num = (x) => Number(x ?? 0).toLocaleString("ru-RU", {maximumFractionDigits: 1});
  const money = (x) => (Number(x ?? 0) / 1e6).toLocaleString("ru-RU", {maximumFractionDigits: 3});
  const itemName = (x) => String(x ?? "").replace(/^minecraft:/, "");
  const phases = {PREPARING:"Подготовка",TO_SOURCE:"К поставщику",LOADING:"Погрузка",OUTBOUND:"Доставка",UNLOADING:"Разгрузка",RETURNING:"Возвращение",RECOVERING:"Восстановление",COMPLETE:"Завершён"};
  let root, data, filters, world, view, initial, selected = null, hovered = null, drag = null;
  let showTrade = true, showCaravans = true, paintUnits = 1, height = null, resizeObserver;
  let objects = new Map(), towns = new Map(), missions = [];
  const name = (id) => towns.get(id)?.name || id;
  const valid = (p) => p && Number.isFinite(p.x) && Number.isFinite(p.z);
  const summary = (rows) => rows.length ? rows.map(([k,v]) => `<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join("") : "";
  const shell = () => `<section class="map-panel"><div class="map-toolbar">
    <button data-map="zoom-in" aria-label="Приблизить карту">＋</button><button data-map="zoom-out" aria-label="Отдалить карту">−</button>
    <button data-map="fit">Весь мир</button><button data-map="expand" aria-pressed="false">Развернуть карту</button><button data-map="focus">К выбранному</button>
    <label class="check"><input type="checkbox" data-layer="trade" ${showTrade?"checked":""}>Торговые связи</label>
    <label class="check"><input type="checkbox" data-layer="caravans" ${showCaravans?"checked":""}>Караваны</label>
    <span class="map-clock"></span></div>
    <div class="map-layout"><div class="map-viewport">
    <svg class="world-map-svg" tabindex="0" role="group" aria-label="Интерактивная карта поселений. Колесо — масштаб, перетаскивание — перемещение, стрелки — сдвиг."></svg>
    <svg class="map-grid-labels" aria-hidden="true" preserveAspectRatio="none"></svg><div class="map-tooltip" role="tooltip" hidden></div>
    <div class="map-axis">Север −Z ↑ · Восток +X →</div></div>
    <aside class="map-sidebar"><div class="map-details"></div><h3>Караваны сейчас</h3><div class="map-missions"></div></aside></div>
    <div class="map-resize" role="separator" tabindex="0" aria-label="Высота карты: перетащите или используйте стрелки" aria-orientation="horizontal"></div><p class="map-note muted"></p><div class="map-legend"><span>● Поселение</span><span class="map-warning">● Есть голодные</span>
    <span class="map-gold">◆ Караван — центр группы</span><span>→ Оплаченные поставки за период; толщина — стоимость товара</span></div></section>`;
  function mount(host, value, options) {
    data = value; filters = options; root = host;
    const map = data.latest?.map;
    if (map?.schema !== 1) {
      root.innerHTML = '<p class="empty">В этом снимке ещё нет координат карты. Для текущего мира требуется серверная сборка с экспортом карты. Старые снимки не пересчитываются.</p>';
      return;
    }
    if (world !== data.world?.world) {
      world = data.world?.world; view = null; selected = null;
    }
    const b = map.bounds;
    initial = {x:b.minX-b.width*.035,z:b.minZ-b.depth*.035,w:b.width*1.07,h:b.depth*1.07};
    if (!view) view = {...initial};
    towns = new Map(data.latest.settlements.filter(valid).map(t => [t.id,t]));
    const spatial = new Map(map.missions.map(m => [m.id,m]));
    missions = (data.latest.missions || []).filter(m => spatial.has(m.id) &&
      (!filters.town || [m.town,m.source,m.destination].includes(filters.town)) &&
      (!filters.item || m.items.includes(filters.item))).map(m => ({...m, spatial:spatial.get(m.id)}));
    if (!root.querySelector("svg")) {
      root.innerHTML = shell(); bind();
    }
    root.querySelector(".map-clock").textContent = `Снимок: тик ${num(data.latest.tick)} · ${Math.round(data.ageSeconds)} с назад`;
    root.querySelector(".map-note").textContent = `Поселения и караваны — последний снимок; связи — приёмки за ${num(data.from)}–${num(data.to)} тиков. Линии связей не являются дорогами. Пунктир выбранного каравана — сохранённый маршрут, до 128 точек. Позиции COLD вычислены общим механизмом движения, HOT — последние подтверждённые.${map.activeCount > map.shownCount ? ` Ограничение карты: показано ${map.shownCount} из ${map.activeCount} активных караванов.` : ""}${data.ageSeconds > 15 || data.collectorError ? " Данные устарели или сборщик недоступен; перемещения не дорисовываются." : ""}`;
    paint();
  }
  function marker(key, attrs, shape, label) {
    return `<g data-object="${esc(key)}" tabindex="0" role="button" aria-label="${esc(label)}" ${attrs}>${shape}</g>`;
  }
  function paint() {
    if (!root?.isConnected) return;
    const svg = root.querySelector(".world-map-svg");
    if (!svg || !svg.clientWidth || !svg.clientHeight) return;
    svg.setAttribute("viewBox", `${view.x} ${view.z} ${view.w} ${view.h}`);
    const box = svg.getBoundingClientRect(), transform = svg.getScreenCTM();
    paintUnits = 1 / transform.a;
    const u = paintUnits, left = (box.left - transform.e) * u, top = (box.top - transform.f) * u;
    const right = left + box.width * u, bottom = top + box.height * u;
    objects = new Map();
    let out = `<defs><marker id="map-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10Z" fill="#62afad"/></marker></defs>`;
    const b = data.latest.map.bounds;
    out += `<rect x="${b.minX}" y="${b.minZ}" width="${b.width}" height="${b.depth}" fill="#10252c" stroke="#34505b" stroke-width="${u}"/>`;
    // SVG meet letterboxing changes the visible world bounds. Grid lines use
    // those bounds; labels live in a separate pixel overlay, fixed to the viewport.
    const target = Math.max((right-left) / 8, u * 90);
    const magnitude = Math.pow(10, Math.floor(Math.log10(target)));
    const grid = [1,2,5,10].find(step => step*magnitude >= target)*magnitude;
    let labels = `<rect width="${box.width}" height="23" fill="#0d1c23"/><rect width="55" height="${box.height}" fill="#0d1c23"/>`;
    for(let x=Math.ceil(left/grid)*grid; x<=right; x+=grid) {
      out += `<path d="M${x} ${top} V${bottom}" stroke="#243b43" stroke-width="${u*.6}"/>`;
      const px = (x-left)/u;
      if(px >= 62 && px <= box.width-32) labels += `<text class="map-grid-x" x="${px}" y="16" text-anchor="middle">${num(x)}</text>`;
    }
    for(let z=Math.ceil(top/grid)*grid; z<=bottom; z+=grid) {
      out += `<path d="M${left} ${z} H${right}" stroke="#243b43" stroke-width="${u*.6}"/>`;
      const py = (z-top)/u;
      if(py >= 36 && py <= box.height-40) labels += `<text class="map-grid-z" x="6" y="${py}" dominant-baseline="middle">${num(z)}</text>`;
    }
    const axes = root.querySelector(".map-grid-labels");
    axes.setAttribute("viewBox", `0 0 ${box.width} ${box.height}`); axes.innerHTML = labels;
    if (showTrade) {
      const links = new Map();
      for (const r of data.matrix || []) {
        if (!towns.has(r.seller) || !towns.has(r.buyer)) continue;
        const key = JSON.stringify([r.seller,r.buyer]);
        if (!links.has(key)) links.set(key,{seller:r.seller,buyer:r.buyer,rows:[],value:0});
        const link = links.get(key); link.rows.push(r); link.value+=r.goods_raw;
      }
      const maximum = Math.max(1,...[...links.values()].map(l=>l.value));
      for (const link of links.values()) {
        const a=towns.get(link.seller),b=towns.get(link.buyer),dx=b.x-a.x,dz=b.z-a.z,length=Math.hypot(dx,dz)||1;
        const path=`M${a.x+dx/length*11*u} ${a.z+dz/length*11*u} Q${(a.x+b.x)/2-dz/length*20*u} ${(a.z+b.z)/2+dx/length*20*u} ${b.x-dx/length*12*u} ${b.z-dz/length*12*u}`;
        const key=`link:${JSON.stringify([link.seller,link.buyer])}`; objects.set(key,{type:"link",...link,x:(a.x+b.x)/2,z:(a.z+b.z)/2});
        out+=marker(key,'class="map-link"',`<path d="${path}" fill="none" stroke="transparent" stroke-width="${12*u}"/><path d="${path}" fill="none" stroke="#62afad" opacity=".65" stroke-width="${(1+4*Math.sqrt(link.value/maximum))*u}" marker-end="url(#map-arrow)"/>`,`${name(link.seller)} → ${name(link.buyer)}`);
      }
    }
    // Routes and individual members appear only for the selected group, limiting clutter.
    const chosen=missions.find(m=>`mission:${m.id}`===selected);
    if (showCaravans && chosen) {
      if(chosen.spatial.route.length) out+=`<polyline class="map-route" points="${chosen.spatial.route.map(p=>`${p.x},${p.z}`).join(" ")}" fill="none" stroke="#efbf7e" stroke-dasharray="${6*u} ${4*u}" stroke-width="${2*u}"/>`;
      for(const member of chosen.spatial.members) out+=`<circle cx="${member.x}" cy="${member.z}" r="${3*u}" fill="#fff2d5"/>`;
    }
    for(const t of towns.values()) {
      const key=`town:${t.id}`; objects.set(key,{type:"town",...t});
      const muted=filters.town && filters.town!==t.id, color=t.hungry>0?"#f08e83":"#83d5b2";
      const r=(selected===key?11:8)*u;
      out+=marker(key,`class="map-town${selected===key?" selected":""}" opacity="${muted ? .45 : 1}"`,
        `<circle cx="${t.x}" cy="${t.z}" r="${r}" fill="${color}" stroke="#10252c" stroke-width="${2*u}"/><text x="${t.x+13*u}" y="${t.z-9*u}" font-size="${12*u}" font-weight="600" fill="#e2eeee" paint-order="stroke" stroke="#10252c" stroke-width="${3*u}">${esc(t.name)}</text>`,t.name);
    }
    let count=0;
    for(const mission of missions) {
      const members=mission.spatial.members.filter(valid); if(!members.length) continue;
      const x=members.reduce((s,p)=>s+p.x,0)/members.length,z=members.reduce((s,p)=>s+p.z,0)/members.length;
      const key=`mission:${mission.id}`, label=`К${++count}`;
      objects.set(key,{type:"mission",...mission,x,z,label});
      if(!showCaravans) continue;
      const r=(selected===key?10:7)*u;
      out+=marker(key,`class="map-caravan${selected===key?" selected":""}"`,
        `<path d="M${x} ${z-r} L${x+r} ${z} L${x} ${z+r} L${x-r} ${z}Z" fill="#f1bd75" stroke="#17232b" stroke-width="${2*u}"/><text x="${x+10*u}" y="${z+4*u}" font-size="${10*u}" fill="#f9d9a5" paint-order="stroke" stroke="#10252c" stroke-width="${3*u}">${label}</text>`,`${name(mission.source)} → ${name(mission.destination)}: ${phases[mission.stage]||mission.stage}`);
    }
    svg.innerHTML=out;
    if(selected && !objects.has(selected)) selected=null;
    renderDetails();
    root.querySelector(".map-missions").innerHTML=missions.length ? missions.map(m=> {
      const key=`mission:${m.id}`, object=objects.get(key);
      return `<button class="map-mission${selected===key?" active":""}" data-select="${esc(key)}" ${!object?"disabled":""}>
        <strong>${esc(object?.label||"?")} · ${esc(name(m.source))} → ${esc(name(m.destination))}</strong>
        <span>${esc(phases[m.stage]||m.stage)} · ${m.members} участн.${m.asset?" · осёл":""}${!object?" · координат нет":""}</span></button>`;
    }).join("") : '<p class="empty">Нет активных караванов по выбранным фильтрам</p>';
    if(hovered && objects.has(hovered)) tooltip(hovered); else hideTooltip();
  }
  function details(object, compact = false) {
    if(object.type==="town") {
      const stocks=(data.latest.stocks||[]).filter(s=>s.town===object.id && (!filters.item||s.item===filters.item));
      const jobs=(data.latest.jobs||[]).filter(j=>j.town===object.id).length;
      return `<h3>${esc(object.name)}</h3><dl>${summary([
        ["Координаты X / Y / Z",`${object.x} / ${object.y} / ${object.z}`],
        ["Жителей / голодны",`${object.residents} / ${object.hungry}`],["Баланс",money(object.balanceRaw)],
        ["Свободные деньги",money(object.availableRaw)],["Заданий",jobs]])}</dl>
        <h4>На складах / свободно¹</h4><dl>${summary(stocks.slice(0,compact ? 4 : 12).map(s=>[itemName(s.item),`${num(s.stored)} / ${num(s.unclaimedWarehouse)}`]))}</dl>
        <small>¹ Незарезервированные обычные ресурсы в действующих складах; инструменты не включены во второе число.</small>`;
    }
    if(object.type==="mission") {
      const cargo=new Map(); for(const c of object.spatial.cargo) if(c.quantity>0) cargo.set(c.item,(cargo.get(c.item)||0)+c.quantity);
      return `<h3>${esc(name(object.source))} → ${esc(name(object.destination))}</h3><dl>${summary([
        ["Этап",phases[object.stage]||object.stage],["Организатор",name(object.town)],
        ["Состав",`${object.members} участн.${object.asset?" · осёл":""}`],
        ["Центр группы X / Z",`${num(object.x)} / ${num(object.z)}`],
        ["Маршрут",object.spatial.route.length?(object.spatial.routeSampled?"Упрощённая линия текущего участка":"Текущий сохранённый участок"):"Не сохранён на этом этапе"]])}</dl>
        <h4>В незавершённых партиях</h4><dl>${summary([...cargo].map(([k,v])=>[itemName(k),num(v)]))||"<p>Груз уже выгружен</p>"}</dl>
        <small>Количество включает ожидающее погрузки; этап показывает, везёт ли его караван. Точки вдоль линии — участники, ромб — их средняя позиция.</small>`;
    }
    return `<h3>${esc(name(object.seller))} → ${esc(name(object.buyer))}</h3><p>Подтверждённые приёмки за выбранный период</p><dl>${summary(object.rows.map(r=>[itemName(r.item),`${num(r.quantity)} шт. · ${money(r.goods_raw)} мон. · ${r.receipts} приёмок`]))}${summary([["Товар, всего",money(object.value)],["Доставка",money(object.rows.reduce((s,r)=>s+r.delivery_raw,0))]])}</dl>`;
  }
  function renderDetails() {
    const object=objects.get(selected);
    root.querySelector(".map-details").innerHTML=object ? details(object)+(object.type==="town"?`<button data-filter-town="${esc(object.id)}">Фильтр по поселению</button>`:"") : '<h3>Карта мира</h3><p>Наведите на поселение, караван или торговую связь. Нажмите, чтобы закрепить сведения и увидеть маршрут каравана.</p><p class="muted">Колесо — масштаб; перетаскивание — перемещение. Данные обновляются без сброса выбранного масштаба.</p>';
  }
  function tooltip(key,event) {
    const object=objects.get(key); if(!object) return;
    hovered=key; const tip=root.querySelector(".map-tooltip");tip.innerHTML=details(object, true);tip.hidden=false;
    if(event) {
      const box=root.querySelector(".map-viewport").getBoundingClientRect();
      tip.style.left=`${Math.max(6,Math.min(event.clientX-box.left+16,box.width-tip.offsetWidth-8))}px`;
      tip.style.top=`${Math.max(6,Math.min(event.clientY-box.top+16,box.height-tip.offsetHeight-8))}px`;
    }
  }
  function hideTooltip() {hovered=null; if(root) root.querySelector(".map-tooltip")?.setAttribute("hidden","");}
  function point(event) {
    const svg=root.querySelector("svg"),p=svg.createSVGPoint(); p.x=event.clientX;p.y=event.clientY;
    return p.matrixTransform(svg.getScreenCTM().inverse());
  }
  function zoom(factor, p={x:view.x+view.w/2,y:view.z+view.h/2}) {
    const w=Math.max(initial.w/80,Math.min(initial.w*2,view.w*factor)),scale=w/view.w;
    view={x:p.x-(p.x-view.x)*scale,z:p.y-(p.y-view.z)*scale,w,h:view.h*scale}; hideTooltip();paint();
  }
  function select(key) {selected=objects.has(key)?key:null;hideTooltip();paint();root.querySelector(".world-map-svg").focus({preventScroll:true});}
  function bind() {
    const svg=root.querySelector(".world-map-svg"), panel=root.querySelector(".map-panel"), handle=root.querySelector(".map-resize");
    const setHeight=(value)=>{
      height=Math.round(Math.max(240,Math.min(window.innerHeight-24,value)));
      panel.style.setProperty("--map-height",`${height}px`);
      handle.setAttribute("aria-valuemin","240"); handle.setAttribute("aria-valuemax",String(window.innerHeight-24));
      handle.setAttribute("aria-valuenow",String(height));
    };
    setHeight(height ?? window.innerHeight*.72);
    let resizing=null;
    handle.onpointerdown=(e)=>{if(e.button!==0)return;e.preventDefault();resizing={y:e.clientY,height};handle.setPointerCapture(e.pointerId);};
    handle.onpointermove=(e)=>{if(resizing)setHeight(resizing.height+e.clientY-resizing.y);};
    handle.onpointerup=handle.onpointercancel=()=>{resizing=null;};
    handle.onkeydown=(e)=>{if(["ArrowUp","ArrowDown","Home","End"].includes(e.key)){e.preventDefault();setHeight(e.key==="Home"?240:e.key==="End"?window.innerHeight-24:height+(e.key==="ArrowDown"?40:-40));}};
    const expand=(active)=>{
      panel.classList.toggle("expanded",active);
      const button=panel.querySelector('[data-map="expand"]');button.setAttribute("aria-pressed",String(active));
      button.textContent=active?"Свернуть карту":"Развернуть карту"; hideTooltip();
    };
    panel.onkeydown=(e)=>{if(e.key==="Escape" && panel.classList.contains("expanded")){expand(false);e.stopPropagation();}};
    resizeObserver?.disconnect();resizeObserver=new ResizeObserver(()=>{if(svg.isConnected)paint();});resizeObserver.observe(svg);
    root.onclick=(event)=> {
      const selection=event.target.closest("[data-select],[data-object]");
      if(selection) {select(selection.dataset.select||selection.dataset.object);return;}
      const filter=event.target.closest("[data-filter-town]");if(filter){filters.selectTown(filter.dataset.filterTown);return;}
      const action=event.target.closest("[data-map]")?.dataset.map;
      if(action==="zoom-in") zoom(.7); if(action==="zoom-out") zoom(1/.7);
      if(action==="fit") {view={...initial};paint();}
      if(action==="expand") expand(!panel.classList.contains("expanded"));
      if(action==="focus") {const p=objects.get(selected)||towns.get(filters.town);if(p){view={...view,x:p.x-view.w/2,z:p.z-view.h/2};paint();}}
    };
    root.onchange=(event)=>{if(event.target.dataset.layer==="trade")showTrade=event.target.checked;if(event.target.dataset.layer==="caravans")showCaravans=event.target.checked;paint();};
    svg.addEventListener("wheel",(event)=>{event.preventDefault();zoom(Math.exp(Math.max(-1,Math.min(1,event.deltaY*.002))),point(event));},{passive:false});
    svg.onpointerdown=(event)=>{if(event.button!==0||event.target.closest("[data-object]"))return;event.preventDefault();svg.focus({preventScroll:true});drag={point:point(event),view:{...view}};svg.setPointerCapture(event.pointerId);hideTooltip();};
    svg.onpointermove=(event)=>{
      if(drag){const p=point(event);view={...view,x:view.x+drag.point.x-p.x,z:view.z+drag.point.y-p.y};paint();return;}
      const hit=event.target.closest("[data-object]");if(hit)tooltip(hit.dataset.object,event);else hideTooltip();
    };
    svg.onpointerup=svg.onpointercancel=()=>{drag=null;};svg.onpointerleave=()=>{if(!drag)hideTooltip();};
    svg.onkeydown=(event)=>{
      const hit=event.target.closest("[data-object]");
      if(hit && ["Enter"," "].includes(event.key)){event.preventDefault();select(hit.dataset.object);return;}
      if(event.key==="Escape"){selected=null;hideTooltip();paint();return;}
      const shifts={ArrowLeft:[-.1,0],ArrowRight:[.1,0],ArrowUp:[0,-.1],ArrowDown:[0,.1]};
      if(shifts[event.key]){event.preventDefault();const [x,z]=shifts[event.key];view.x+=x*view.w;view.z+=z*view.h;paint();}
      if(["+","="].includes(event.key)){event.preventDefault();zoom(.7);}if(event.key==="-"){event.preventDefault();zoom(1/.7);}
    };
  }
  return {mount};
})();
