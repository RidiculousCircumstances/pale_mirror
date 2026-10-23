"use strict";
const $ = (id) => document.getElementById(id),
  esc = (x) =>
    String(x ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
const n = (x) =>
    Number(x || 0).toLocaleString("ru-RU", { maximumFractionDigits: 2 }),
  money = (x) =>
    (Number(x || 0) / 1e6).toLocaleString("ru-RU", {
      maximumFractionDigits: 6,
    }),
  short = (x) => String(x ?? "").replace(/^minecraft:/, "");
let performanceData = null;
let data = null,
  tab = "map",
  busy = false,
  refreshPending = false;
const sorts = new Map();
const collator = new Intl.Collator("ru", {
  numeric: true,
  sensitivity: "base",
});
const tabs = {
  map: "Карта",
  towns: "Поселения",
  stocks: "Запасы",
  market: "Заявки",
  trade: "Торговля",
  missions: "Караваны",
  production: "Производство",
  history: "История",
  gaps: "Покрытие",
  performance: "Производительность",
};
$("tabs").innerHTML = Object.entries(tabs)
  .map(([k, v]) => `<button data-tab="${k}">${v}</button>`)
  .join("");
$("tabs").onclick = (e) => {
  if (e.target.dataset.tab) {
    tab = e.target.dataset.tab;
    refresh();
  }
};
const params = () =>
  new URLSearchParams(
    [
      "world",
      "town",
      "item",
      "from",
      "to",
      ...(tab === "trade" ? ["seller", "buyer"] : []),
    ].flatMap((k) => ($(k).value ? [[k, $(k).value]] : [])),
  );
function options(id, rows, empty) {
  const current = $(id).value;
  $(id).innerHTML =
    (empty ? `<option value="">${empty}</option>` : "") +
    rows
      .map(([k, v]) => `<option value="${esc(k)}">${esc(v)}</option>`)
      .join("");
  if (rows.some((r) => r[0] === current)) $(id).value = current;
}
function table(title, rows, cols, exportName) {
  const sort = sorts.get(title);
  if (sort) {
    const get = cols[sort.column][2] || cols[sort.column][1];
    rows = [...rows].sort((a, b) => {
      const x = get(a),
        y = get(b);
      return (
        sort.direction *
        (typeof x === "number" && typeof y === "number"
          ? x - y
          : collator.compare(String(x ?? ""), String(y ?? "")))
      );
    });
  }
  return (
    `<h2>${esc(title)}</h2>${exportName ? `<p><a href="/api/export.csv?${esc(params())}&table=${exportName}">Скачать CSV (тот же период и лимит)</a></p>` : ""}` +
    (rows.length
      ? `<div class="table-scroll" tabindex="0" role="region" aria-label="${esc(title)}"><table><thead><tr>${cols
          .map((c, i) => {
            const active = sort?.column === i;
            return `<th scope="col" aria-sort="${active ? (sort.direction === 1 ? "ascending" : "descending") : "none"}"><button class="sort" data-table="${esc(title)}" data-column="${i}">${esc(c[0])} <span aria-hidden="true">${active ? (sort.direction === 1 ? "↑" : "↓") : "↕"}</span></button></th>`;
          })
          .join(
            "",
          )}</tr></thead><tbody>${rows.map((r) => `<tr>${cols.map((c) => `<td>${esc(c[1](r))}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`
      : '<p class="empty">Нет данных в выбранном периоде</p>')
  );
}
$("content").addEventListener("click", (event) => {
  if (event.target.closest("[data-refresh-performance]")) {
    refresh();
    return;
  }
  const button = event.target.closest("button[data-column]");
  if (!button) return;
  const key = button.dataset.table,
    column = Number(button.dataset.column),
    previous = sorts.get(key);
  sorts.set(key, {
    column,
    direction: previous?.column === column ? -previous.direction : 1,
  });
  render();
  [...document.querySelectorAll("button[data-column]")]
    .find((b) => b.dataset.table === key && Number(b.dataset.column) === column)
    ?.focus({ preventScroll: true });
});
const name = (id) =>
  data?.latest?.settlements?.find((s) => s.id === id)?.name || id;
const filtered = (rows, townKey = "town") =>
  rows.filter(
    (r) =>
      (!$("town").value || r[townKey] === $("town").value) &&
      (!$("item").value || r.item === $("item").value),
  );
function chart(
  title,
  series,
  points = data.history || [],
  formatTime = n,
  axis = "тики",
) {
  const colors = [
    "#83d5b2",
    "#f1bd75",
    "#88bafa",
    "#d39bda",
    "#f28088",
    "#b4c983",
  ];
  if (points.length < 2)
    return `<h2>${esc(title)}</h2><p class="empty">Нужно минимум два снимка</p>`;
  const a = points[0].tick,
    b = points.at(-1).tick;
  let max = 1;
  for (const s of series)
    for (const p of points) max = Math.max(max, s.value(p));
  return `<h2>${esc(title)}</h2><p class="muted">Максимум шкалы ${n(max)} · ${axis} ${formatTime(a)}–${formatTime(b)}. Между снимками состояние неизвестно.</p><svg viewBox="0 0 1000 230" preserveAspectRatio="none">${series.map((s, i) => `<polyline fill="none" stroke="${colors[i % colors.length]}" stroke-width="2" points="${points.map((p) => `${20 + (960 * (p.tick - a)) / Math.max(1, b - a)},${210 - (190 * s.value(p)) / max}`).join(" ")}"/>`).join("")}</svg><div class="legend">${series.map((s, i) => `<span style="color:${colors[i % colors.length]}">${esc(s.label)}</span>`).join("")}</div>`;
}
function setContent(html) {
  const positions = new Map(
    [...$("content").querySelectorAll(".table-scroll")].map((region) => [
      region.getAttribute("aria-label"),
      [region.scrollTop, region.scrollLeft],
    ]),
  );
  $("content").innerHTML = html;
  for (const region of $("content").querySelectorAll(".table-scroll")) {
    const previous = positions.get(region.getAttribute("aria-label"));
    if (previous) {
      region.scrollTop = previous[0];
      region.scrollLeft = previous[1];
    }
  }
}
function render() {
  $("trade-filters").hidden = tab !== "trade";
  $("filters").hidden = tab === "performance";
  $("summary").hidden = tab === "performance";
  $("coverage").hidden = tab === "performance";
  document
    .querySelectorAll("[data-tab]")
    .forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
  if (tab === "performance") {
    setContent(performanceView());
    return;
  }
  if (!data?.latest) {
    setContent('<p class="empty">Ожидается первый снимок сервера</p>');
    return;
  }
  const s = data.latest;
  const towns = s.settlements.filter(
    (t) => !$("town").value || t.id === $("town").value,
  );
  $("summary").innerHTML = [
    ["Жителей", towns.reduce((a, t) => a + t.residents, 0)],
    ["Голодны", towns.reduce((a, t) => a + t.hungry, 0)],
    ["Деньги", money(towns.reduce((a, t) => a + t.balanceRaw, 0))],
    ["Оплаченных приёмок", data.matrix.reduce((a, t) => a + t.receipts, 0)],
  ]
    .map(
      ([k, v]) =>
        `<div class="card"><strong>${esc(v)}</strong><span>${k}</span></div>`,
    )
    .join("");
  if (tab === "map") {
    if (!$("content").querySelector(".world-map")) setContent('<div class="world-map"></div>');
    WorldMap.mount($("content").firstElementChild, data, {
      town: $("town").value, item: $("item").value,
      selectTown: (id) => { $("town").value = id; refresh(); },
    });
    return;
  }
  let html = "";
  if (tab === "towns")
    html = table("Текущее состояние", towns, [
      ["Поселение", (r) => r.name],
      ["Жителей", (r) => r.residents],
      ["Голодны", (r) => r.hungry],
      ["Баланс", (r) => money(r.balanceRaw), (r) => r.balanceRaw],
      ["Зарезервировано", (r) => money(r.reservedRaw), (r) => r.reservedRaw],
      ["Бюджеты", (r) => money(r.budgetsRaw), (r) => r.budgetsRaw],
      ["Можно потратить", (r) => money(r.availableRaw), (r) => r.availableRaw],
      ["Получено", (r) => money(r.receivedRaw), (r) => r.receivedRaw],
      ["Уплачено", (r) => money(r.paidRaw), (r) => r.paidRaw],
    ]);
  if (tab === "stocks")
    html =
      '<p class="muted">Собственность и место хранения разделены. В резерве — требования к партиям, а не дополнительный запас. ¹ Для обычных партий: незарезервированное содержимое действующих складов. Индивидуальные инструменты не включены. Складские данные не гарантируют физическую доступность входа.</p>' +
      table("Ресурсы сейчас", filtered(s.stocks), [
        ["Поселение", (r) => name(r.town)],
        ["Ресурс", (r) => short(r.item)],
        ["Всего у владельца", (r) => r.total],
        ["В контейнерах", (r) => r.stored],
        ["У акторов / в пути", (r) => r.carried],
        ["На земле / прочее", (r) => r.other],
        ["Заявлено в резерв", (r) => r.claimed],
        ["Свободно на складах¹", (r) => r.unclaimedWarehouse],
      ]);
  if (tab === "market")
    html =
      '<p class="muted">Заявки адресованы контрагентам. Их суммы могут представлять альтернативы одной потребности — не суммарный независимый спрос.</p>' +
      table("Открытые заявки", filtered(s.orders), [
        ["Поселение", (r) => name(r.town)],
        ["Контрагент", (r) => name(r.counterparty)],
        ["Сторона", (r) => r.side],
        ["Ресурс", (r) => short(r.item)],
        ["Доступно", (r) => r.quantity],
        ["Цена / ед.", (r) => money(r.priceRaw), (r) => r.priceRaw],
        ["Истекает, тик", (r) => r.expires],
      ]);
  if (tab === "trade")
    html =
      table(
        "Торговые связи за период",
        data.matrix,
        [
          ["Продавец", (r) => name(r.seller)],
          ["Покупатель", (r) => name(r.buyer)],
          ["Товар", (r) => short(r.item)],
          ["Количество", (r) => r.quantity],
          ["Стоимость товара", (r) => money(r.goods_raw), (r) => r.goods_raw],
          ["Доставка", (r) => money(r.delivery_raw), (r) => r.delivery_raw],
          [
            "Средняя цена товара",
            (r) => money(r.goods_raw / r.quantity),
            (r) => r.goods_raw / r.quantity,
          ],
          ["Приёмок", (r) => r.receipts],
        ],
        "matrix",
      ) +
      table(
        "Последние подтверждённые приёмки (до 500)",
        data.sales,
        [
          ["Тик", (r) => r.tick],
          ["Продавец", (r) => name(r.seller)],
          ["Покупатель", (r) => name(r.buyer)],
          ["Товар", (r) => short(r.item)],
          ["Количество", (r) => r.quantity],
          [
            "Цена / ед.",
            (r) => money(r.goods_raw / r.quantity),
            (r) => r.goods_raw / r.quantity,
          ],
          ["Доставка", (r) => money(r.delivery_raw), (r) => r.delivery_raw],
        ],
        "sales",
      ) +
      table(
        "Активные договоры",
        s.contracts.filter(
          (r) =>
            (!$("town").value ||
              r.seller === $("town").value ||
              r.buyer === $("town").value) &&
            (!$("item").value || r.item === $("item").value) &&
            (!$("seller").value || r.seller === $("seller").value) &&
            (!$("buyer").value || r.buyer === $("buyer").value),
        ),
        [
          ["Договор", (r) => r.id],
          ["Продавец", (r) => name(r.seller)],
          ["Покупатель", (r) => name(r.buyer)],
          ["Товар", (r) => short(r.item)],
          ["Заказано", (r) => r.quantity],
          ["Принято", (r) => r.accepted],
          ["Потеряно / отменено", (r) => r.disposed],
        ],
      );
  if (tab === "missions")
    html = table(
      "Текущие перевозки",
      s.missions.filter(
        (r) =>
          (!$("town").value ||
            r.town === $("town").value ||
            r.source === $("town").value ||
            r.destination === $("town").value) &&
          (!$("item").value || r.items.includes($("item").value)),
      ),
      [
        ["Рейс", (r) => r.id],
        ["Дом", (r) => name(r.town)],
        ["Откуда", (r) => name(r.source)],
        ["Куда", (r) => name(r.destination)],
        ["Стадия", (r) => r.stage],
        ["Участников", (r) => r.members],
        ["Грузов", (r) => r.shipments],
        ["Транспорт", (r) => r.asset || "Пешком"],
      ],
    );
  if (tab === "production")
    html =
      '<p class="muted">Только подтверждённые добыча, преобразования и потребление. Перенос между сундуками не является производством. Пропуски наблюдения указаны во вкладке покрытия.</p>' +
      table(
        "Потоки за период",
        data.flows,
        [
          ["Поселение", (r) => name(r.town)],
          ["Ресурс", (r) => short(r.item)],
          ["Операция", (r) => r.category],
          ["Количество", (r) => r.quantity],
        ],
        "flows",
      ) +
      table("Работы сейчас", filtered(s.jobs), [
        ["Работа", (r) => r.id],
        ["Поселение", (r) => name(r.town)],
        ["Вид", (r) => r.kind],
        ["Стадия", (r) => r.phase],
        ["Работник", (r) => r.actor],
      ]);
  if (tab === "history") {
    const chosen = towns.slice(0, 12);
    html = chart(
      "Денежные балансы",
      chosen.map((t) => ({
        label: t.name,
        value: (p) =>
          (p.settlements.find((s) => s.id === t.id)?.balanceRaw || 0) / 1e6,
      })),
    );
    if ($("item").value)
      html += chart(
        "Запас " + short($("item").value),
        chosen.map((t) => ({
          label: t.name,
          value: (p) =>
            p.stocks.find((s) => s.town === t.id && s.item === $("item").value)
              ?.total || 0,
        })),
      );
    else
      html +=
        "<p>Выберите ресурс для истории запасов. История цен сделок доступна в таблице приёмок.</p>";
  }
  if (tab === "history" && $("item").value) {
    const days = new Map();
    for (const r of data.daily) {
      const v = days.get(r.day) || [0, 0];
      v[0] += r.goods_raw;
      v[1] += r.quantity;
      days.set(r.day, v);
    }
    const points = [...days].sort((a, b) => a[0] - b[0]);
    html +=
      '<h2>Средняя цена товара по торговым дням</h2><p class="muted">Взвешено по количеству, доставка исключена. Дни без сделок отсутствуют; граничные дни учитываются целиком.</p>';
    html += table(
      "Подтверждённые рыночные цены",
      points.map(([day, v]) => ({ day, price: v[0] / v[1], quantity: v[1] })),
      [
        ["День", (r) => r.day],
        ["Цена / ед.", (r) => money(r.price), (r) => r.price],
        ["Объём", (r) => r.quantity],
      ],
    );
  }
  if (tab === "gaps")
    html =
      `<p>Подробные сделки: ${data.detailFrom}–${data.to}. Снимков графика: ${data.history.length} (максимум 240). Дневные торговые агрегаты хранятся 365 игровых дней, подробности — 30 дней; до 8 миров. График не доказывает отсутствие кратковременных сбоев.</p>` +
      table("Разрывы наблюдения", data.gaps, [
        ["Тик", (r) => r.tick],
        ["Причина", (r) => r.reason],
        ["Детали", (r) => r.body],
      ]);
  setContent(html);
}
$("content").addEventListener("change", (event) => {
  if (event.target.matches("[data-performance-auto]"))
    $("auto").checked = event.target.checked;
});
function performanceView() {
  const value = performanceData;
  const controls = `<div class="performance-controls"><button data-refresh-performance>Обновить показатели</button><label class="check"><input type="checkbox" data-performance-auto ${$("auto").checked ? "checked" : ""}>Автообновление</label></div>`;
  if (!value?.latest)
    return (
      controls +
      `<p class="empty">Метрики пока недоступны: ${esc(value?.error || "ожидание первого снимка")}</p>`
    );
  const d = value.latest,
    last = value.history.at(-1),
    ms = (x) => (x == null ? "—" : n(x / 1e6));
  const field = (x) => (x == null ? "—" : x);
  const values = (object, keys) =>
    keys.map(([label, key]) => ({ label, value: field(object?.[key]) }));
  return (
    controls +
    `<p class="${value.error || value.ageSeconds > 25 ? "warn" : "muted"}">Весь сервер · ${esc(value.world)} · данные ${n(value.ageSeconds)} с назад · интервал ${value.intervalSeconds} с${value.error ? " · " + esc(value.error) : ""}. Фильтры экономики здесь не применяются.</p>
    <p class="muted">Время тика — измеренная часть до обработчика Pale Mirror, не полный MSPT Minecraft. Тики/с вычислены по разнице счётчика между снимками, не по времени обработки. История — до часа с запуска сборщика; при перезапуске сервера начинается заново.</p>` +
    table(
      "Сервер сейчас",
      [
        {
          label: "Наблюдаемые тики/с",
          value: last?.tps == null ? "—" : n(last.tps),
        },
        {
          label: "Среднее время тика, мс (64 последних)",
          value: last?.meanMs == null ? "—" : n(last.meanMs),
        },
        {
          label: "Пиковое время тика, мс (64 последних)",
          value: last?.maxMs == null ? "—" : n(last.maxMs),
        },
        {
          label: "Обычные тики в ожидании",
          value: field(d.pendingOrdinaryTicks),
        },
        {
          label: "Перемотка: осталось тиков",
          value: field(d.fastForwardRemaining),
        },
        { label: "Ошибка перемотки", value: d.fastForwardFailure || "Нет" },
        { label: "Снимок симуляции, тик", value: field(d.instant) },
      ],
      [
        ["Показатель", (r) => r.label],
        ["Значение", (r) => r.value],
      ],
    ) +
    table(
      "Очередь событий",
      values(d.scheduleQueue, [
        ["Готовы", "ready"],
        ["Ожидают условия", "held"],
        ["Будущие", "future"],
        ["Проверяются", "checking"],
        ["Всего", "total"],
        ["Отставание готовых, тики", "oldestReadyLagTicks"],
      ]),
      [
        ["Показатель", (r) => r.label],
        ["Значение", (r) => r.value],
      ],
    ) +
    table(
      "Маршрутизация",
      values(d.pedestrianPlanning, [
        ["В очереди", "waiting"],
        ["Активные поиски", "activeSearches"],
        ["Работающие потоки", "runningWorkers"],
        ["Размер пула", "workerPoolSize"],
        ["Ожидающие порции расчёта", "queuedWorkerSlices"],
        ["Завершённые маршруты", "completedSearches"],
        ["Максимальное ожидание, шаги планировщика", "oldestWaitPlanningTurns"],
      ]),
      [
        ["Показатель", (r) => r.label],
        ["Значение", (r) => r.value],
      ],
    ) +
    table(
      "Запись канонического журнала",
      [
        ...values(d.canonicalWal, [
          ["Записей в буфере", "bufferedRecords"],
          ["Байт в буфере", "bufferedBytes"],
        ]),
        {
          label: "Ожидается подтверждение диска",
          value:
            d.canonicalWal?.awaitingDurability == null
              ? "—"
              : d.canonicalWal.awaitingDurability
                ? "Да"
                : "Нет",
        },
        {
          label: "Текущее ожидание, мс",
          value: ms(d.canonicalWal?.pendingNanos),
        },
        {
          label: "Последняя запись, мс",
          value: ms(d.canonicalWal?.lastFlushNanos),
        },
        {
          label: "Максимальная запись с запуска, мс",
          value: ms(d.canonicalWal?.maxFlushNanos),
        },
      ],
      [
        ["Показатель", (r) => r.label],
        ["Значение", (r) => r.value],
      ],
    ) +
    chart(
      "Время серверного тика, мс",
      [
        { label: "Среднее", value: (p) => p.meanMs ?? 0 },
        { label: "Максимум", value: (p) => p.maxMs ?? 0 },
      ],
      value.history
        .filter((p) => p.meanMs != null)
        .map((p) => ({ ...p, tick: p.at })),
      (time) => new Date(time * 1000).toLocaleTimeString("ru-RU"),
      "время",
    ) +
    '<p class="muted">Паузы сбора не доказывают отсутствие пиков.</p>'
  );
}
async function refresh() {
  if (busy) {
    refreshPending = true;
    return;
  }
  const query = params().toString();
  const requestedTab = tab;
  busy = true;
  try {
    const res = await fetch("/api/view?" + query);
    if (!res.ok) throw new Error(await res.text());
    const received = await res.json();
    let performanceReceived = null;
    if (requestedTab === "performance") {
      const metrics = await fetch("/api/performance");
      if (!metrics.ok) throw new Error(await metrics.text());
      performanceReceived = await metrics.json();
    }
    if (query !== params().toString() || requestedTab !== tab) {
      refreshPending = true;
      return;
    }
    data = received;
    if (performanceReceived) performanceData = performanceReceived;
    options(
      "world",
      data.worlds.map((w) => [w.world, w.name]),
    );
    if (data.latest) {
      options(
        "town",
        data.latest.settlements.map((t) => [t.id, t.name]),
        "Все поселения",
      );
      for (const key of ["seller", "buyer"])
        options(
          key,
          data.latest.settlements.map((t) => [t.id, t.name]),
          "Все поселения",
        );
      options(
        "item",
        [
          ...new Set(
            data.latest.stocks
              .map((s) => s.item)
              .concat(data.latest.orders.map((o) => o.item)),
          ),
        ]
          .sort()
          .map((i) => [i, short(i)]),
        "Все ресурсы",
      );
      $("health").textContent =
        `${data.latest.status} · тик ${data.latest.tick} · данные ${Math.round(data.ageSeconds)} с назад`;
      $("health").className =
        data.ageSeconds > 15 || data.collectorError ? "warn" : "";
      $("coverage").textContent =
        `Период ${data.from}–${data.to} · ревизия ${data.latest.revision} · разрывов: ${data.gaps.length}${data.collectorError ? " · Сборщик: " + data.collectorError : ""}`;
    }
    render();
  } catch (e) {
    $("health").textContent = "Нет данных: " + e.message;
    $("health").className = "warn";
  } finally {
    busy = false;
    if (refreshPending) {
      refreshPending = false;
      refresh();
    }
  }
}
$("filters").onsubmit = (e) => {
  e.preventDefault();
  refresh();
};
$("filters").addEventListener("change", (event) => {
  if (event.target.id !== "auto") refresh();
});
$("reset-filters").onclick = () => {
  for (const key of ["town", "seller", "buyer", "item", "from", "to"])
    $(key).value = "";
  refresh();
};
setInterval(() => {
  if ($("auto").checked) refresh();
}, 5000);
refresh();
