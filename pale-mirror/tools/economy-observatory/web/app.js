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
  money = (x) => (Number(x || 0) / 1e6).toLocaleString("ru-RU", { maximumFractionDigits: 6 }),
  short = (x) => String(x ?? "").replace(/^minecraft:/, "");
let data = null,
  tab = "towns",
  busy = false;
const tabs = {
  towns: "Поселения",
  stocks: "Запасы",
  market: "Заявки",
  trade: "Торговля",
  missions: "Караваны",
  production: "Производство",
  history: "История",
  gaps: "Покрытие",
};
$("tabs").innerHTML = Object.entries(tabs)
  .map(([k, v]) => `<button data-tab="${k}">${v}</button>`)
  .join("");
$("tabs").onclick = (e) => {
  if (e.target.dataset.tab) {
    tab = e.target.dataset.tab;
    render();
  }
};
const params = () =>
  new URLSearchParams(
    ["world", "town", "item", "from", "to"].flatMap((k) =>
      $(k).value ? [[k, $(k).value]] : [],
    ),
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
  return (
    `<h2>${esc(title)}</h2>${exportName ? `<p><a href="/api/export.csv?${esc(params())}&table=${exportName}">Скачать CSV (тот же период и лимит)</a></p>` : ""}` +
    (rows.length
      ? `<table><thead><tr>${cols.map((c) => `<th>${esc(c[0])}</th>`).join("")}</tr></thead><tbody>${rows.map((r) => `<tr>${cols.map((c) => `<td>${esc(c[1](r))}</td>`).join("")}</tr>`).join("")}</tbody></table>`
      : '<p class="empty">Нет данных в выбранном периоде</p>')
  );
}
const name = (id) =>
  data?.latest?.settlements?.find((s) => s.id === id)?.name || id;
const filtered = (rows, townKey = "town") =>
  rows.filter(
    (r) =>
      (!$("town").value || r[townKey] === $("town").value) &&
      (!$("item").value || r.item === $("item").value),
  );
function chart(title, series) {
  const colors = [
    "#83d5b2",
    "#f1bd75",
    "#88bafa",
    "#d39bda",
    "#f28088",
    "#b4c983",
  ];
  const points = data.history || [];
  if (points.length < 2)
    return `<h2>${esc(title)}</h2><p class="empty">Нужно минимум два снимка</p>`;
  const a = points[0].tick,
    b = points.at(-1).tick;
  let max = 1;
  for (const s of series)
    for (const p of points) max = Math.max(max, s.value(p));
  return `<h2>${esc(title)}</h2><p class="muted">Максимум шкалы ${n(max)} · тики ${a}–${b}. Между снимками состояние неизвестно.</p><svg viewBox="0 0 1000 230" preserveAspectRatio="none">${series.map((s, i) => `<polyline fill="none" stroke="${colors[i % colors.length]}" stroke-width="2" points="${points.map((p) => `${20 + (960 * (p.tick - a)) / Math.max(1, b - a)},${210 - (190 * s.value(p)) / max}`).join(" ")}"/>`).join("")}</svg><div class="legend">${series.map((s, i) => `<span style="color:${colors[i % colors.length]}">${esc(s.label)}</span>`).join("")}</div>`;
}
function render() {
  if (!data?.latest) {
    $("content").innerHTML =
      '<p class="empty">Ожидается первый снимок сервера</p>';
    return;
  }
  const s = data.latest;
  document
    .querySelectorAll("[data-tab]")
    .forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
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
  let html = "";
  if (tab === "towns")
    html = table("Текущее состояние", towns, [
      ["Поселение", (r) => r.name],
      ["Жителей", (r) => r.residents],
      ["Голодны", (r) => r.hungry],
      ["Баланс", (r) => money(r.balanceRaw)],
      ["Зарезервировано", (r) => money(r.reservedRaw)],
      ["Бюджеты", (r) => money(r.budgetsRaw)],
      ["Можно потратить", (r) => money(r.availableRaw)],
      [
        "Получено / уплачено",
        (r) => money(r.receivedRaw) + " / " + money(r.paidRaw),
      ],
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
        ["Цена / ед.", (r) => money(r.priceRaw)],
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
          ["Стоимость товара", (r) => money(r.goods_raw)],
          ["Доставка", (r) => money(r.delivery_raw)],
          ["Средняя цена товара", (r) => money(r.goods_raw / r.quantity)],
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
          ["Цена / ед.", (r) => money(r.goods_raw / r.quantity)],
          ["Доставка", (r) => money(r.delivery_raw)],
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
            (!$("item").value || r.item === $("item").value),
        ),
        [
          ["Договор", (r) => r.id],
          ["Продавец", (r) => name(r.seller)],
          ["Покупатель", (r) => name(r.buyer)],
          ["Товар", (r) => short(r.item)],
          ["Заказано / принято", (r) => r.quantity + " / " + r.accepted],
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
        ["Цена / ед.", (r) => money(r.price)],
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
  $("content").innerHTML = html;
}
async function refresh() {
  if (busy) return;
  busy = true;
  try {
    const res = await fetch("/api/view?" + params());
    if (!res.ok) throw new Error(await res.text());
    data = await res.json();
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
  }
}
$("filters").onsubmit = (e) => {
  e.preventDefault();
  refresh();
};
setInterval(() => {
  if ($("auto").checked) refresh();
}, 5000);
refresh();
