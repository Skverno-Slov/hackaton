This file is a merged representation of a subset of the codebase, containing specifically included files and files not matching ignore patterns, combined into a single document by Repomix.

# File Summary

## Purpose
This file contains a packed representation of a subset of the repository's contents that is considered the most important context.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.

## File Format
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  a. A header with the file path (## File: path/to/file)
  b. The full contents of the file in a code block

## Usage Guidelines
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.

## Notes
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Only files matching these patterns are included: frontend/src/**/*.ts, frontend/src/**/*.tsx, frontend/src/**/*.css, frontend/index.html, frontend/vite.config.ts, frontend/package.json, frontend/tsconfig*.json
- Files matching these patterns are excluded: **/node_modules/**, **/dist/**, **/*.png, **/*.jpg, **/*.jpeg, **/*.svg, **/*.gif, **/*.webp, **/*.ico, **/*.lock, **/package-lock.json, **/pnpm-lock.yaml, **/yarn.lock, **/*.tsbuildinfo
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)

# Directory Structure
```
frontend/
  src/
    api/
      client.ts
    components/
      DataOrders.tsx
      dataPanel.css
      DataPanel.tsx
      DispatchMap.tsx
      GanttChart.tsx
      KpiStrip.tsx
      LateRiskList.tsx
      OrdersTable.tsx
      scenarios.css
      ScenariosPanel.tsx
      stats.css
      StatsPanel.tsx
      TopBar.tsx
      ui.tsx
      UnassignedList.tsx
      WindowsMenu.tsx
    dock/
      dock.css
      DockPanels.tsx
      layout.ts
    hooks/
      usePlan.ts
      useScrollToSelected.ts
      useStaffDirectory.ts
    mobile/
      mobile.css
      MobileApp.tsx
    theme/
      tokens.css
    types/
      api.ts
    App.tsx
    format.ts
    main.tsx
    planModel.ts
    styles.css
  index.html
  package.json
  tsconfig.json
  vite.config.ts
```

# Files

## File: frontend/src/api/client.ts
```typescript
import type {
  CancelOrderResult,
  CreateOrderInput,
  CreateOrderResult,
  DemoLoadResult,
  DemoStatus,
  DemoStepResult,
  EmployeeMarkResult,
  EmployeeRoute,
  EmployeeStaffList,
  EquipmentBulkInput,
  EquipmentBulkResult,
  EquipmentResult,
  EquipmentScope,
  ErrorResponse,
  HistoryVersions,
  MarkKind,
  OrderCatalog,
  Plan,
  Scenario,
  StaffCutResult,
  StaffCutStrategy,
  StaffDirectoryList,
  StaffProfileInput,
  StaffProfileSaved,
  TrafficResult,
  UiDayOrders,
  UiReference,
} from '../types/api';

export class ApiFailure extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let resp: Response;
  try {
    resp = await fetch(path, init);
  } catch {
    throw new ApiFailure(0, 'NETWORK', 'Сервер планирования недоступен — запустите backend на порту 8080');
  }
  const text = await resp.text();
  const body = text ? JSON.parse(text) : null;
  if (!resp.ok) {
    const err = (body as ErrorResponse | null)?.error;
    throw new ApiFailure(resp.status, err?.code ?? 'HTTP_' + resp.status, err?.message ?? resp.statusText);
  }
  return body as T;
}

export const api = {
  getPlan: (date: string) => request<Plan>(`/api/v1/days/${date}/plan`),
  getOrders: (date: string) => request<UiDayOrders>(`/ui/day/${date}/orders`),
  getReference: () => request<UiReference>('/ui/reference'),
  demoStatus: () => request<DemoStatus>('/ui/demo/status'),
  demoLoad: (scenario: Scenario, date?: string) => {
    const q = new URLSearchParams({ scenario });
    if (date) q.set('date', date);
    return request<DemoLoadResult>(`/ui/demo/load?${q}`);
  },
  demoStep: (count: number) => request<DemoStepResult>(`/ui/demo/step?count=${count}`, { method: 'POST' }),
  scenarioStaffCut: (body: { date?: string; remove_count: number; strategy: StaffCutStrategy; seed?: number; budget_sec?: number }) =>
    request<StaffCutResult>('/ui/scenario/staff-cut', json(body)),
  scenarioEquipment: (body: { date?: string; mode: 'no_restock'; budget_sec?: number }) =>
    request<EquipmentResult>('/ui/scenario/equipment', json(body)),
  scenarioTraffic: (body: { date?: string; budget_sec?: number }) =>
    request<TrafficResult>('/ui/scenario/traffic', json(body)),
  employeeStaff: () => request<EmployeeStaffList>('/ui/employee/staff'),
  employeeRoute: (staffId: string) => request<EmployeeRoute>(`/ui/employee/${encodeURIComponent(staffId)}/route`),
  employeeMark: (staffId: string, body: { order_id?: string; kind: MarkKind | 'UNAVAILABLE'; reason?: string; timestamp?: string; until?: string }) =>
    request<EmployeeMarkResult>(`/ui/employee/${encodeURIComponent(staffId)}/marks`, json(body)),
  directoryStaff: () => request<StaffDirectoryList>('/ui/directory/staff'),
  directoryUpdate: (staffId: string, body: StaffProfileInput) =>
    request<StaffProfileSaved>(`/ui/directory/staff/${encodeURIComponent(staffId)}`, { ...json(body), method: 'PUT' }),
  directoryCreate: (body: StaffProfileInput) => request<StaffProfileSaved>('/ui/directory/staff', json(body)),
  directoryEquipmentReset: (staffId: string) =>
    request<StaffProfileSaved>(`/ui/directory/staff/${encodeURIComponent(staffId)}/equipment-override`, { method: 'DELETE' }),
  directoryEquipmentBulk: (body: EquipmentBulkInput) =>
    request<EquipmentBulkResult>('/ui/directory/staff/equipment/bulk', json(body)),
  directoryEquipmentBulkReset: (profile: EquipmentScope) =>
    request<EquipmentBulkResult>('/ui/directory/staff/equipment/bulk/reset', json({ profile })),
  ordersCatalog: (params: { q?: string; work_type?: string }) => {
    const q = new URLSearchParams();
    if (params.q) q.set('q', params.q);
    if (params.work_type) q.set('work_type', params.work_type);
    return request<OrderCatalog>(`/ui/orders/catalog?${q}`);
  },
  ordersCreate: (body: CreateOrderInput) => request<CreateOrderResult>('/ui/orders/create', json(body)),
  ordersCancel: (orderId: string) =>
    request<CancelOrderResult>(`/ui/orders/${encodeURIComponent(orderId)}/cancel`, { method: 'POST' }),
  historyVersions: (date: string) => request<HistoryVersions>(`/ui/history/${date}/versions`),
};

function json(body: unknown): RequestInit {
  return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}
```

## File: frontend/src/components/DataOrders.tsx
```typescript
/*
 * Вкладка «Наряды» панели «Данные»: наряды дня (поиск, фильтр по статусу, отмена с
 * подтверждением) и форма нового наряда (адрес, тип работ, окно, пожелание по исполнителю).
 * Сервер: /ui/orders/* — наряд уходит в план событием NEW_ORDER, отмена — ORDER_CANCELLED.
 */
import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Badge, Button, SegmentedControl } from './ui';
import { api } from '../api/client';
import { ACTION, hhmm, REASON, STATUS, window2, WORK_TYPE_SHORT } from '../format';
import type {
  CreateOrderResult,
  DemoStepItem,
  OrderCatalog,
  OrderCatalogBuilding,
  OrderCatalogStaff,
  UiOrder,
  UiOrderStatus,
  WorkTypeCode,
} from '../types/api';

interface Props {
  orders: UiOrder[];
  day?: string;
  asOf?: string;
  onPlanEvent: (item: DemoStepItem) => Promise<boolean>;
  onNotice: (text: string) => void;
}

const errText = (e: unknown) => (e instanceof Error ? e.message : String(e));

export function OrdersTab({ orders, day, asOf, onPlanEvent, onNotice }: Props) {
  const [view, setView] = useState<'list' | 'new'>('list');
  if (!day) return <p className="empty">День ещё не загружен.</p>;
  if (view === 'new') {
    return <NewOrderForm asOf={asOf} onPlanEvent={onPlanEvent} onBack={() => setView('list')} />;
  }
  return <OrdersList orders={orders} asOf={asOf} onPlanEvent={onPlanEvent} onNotice={onNotice} onAdd={() => setView('new')} />;
}

/* ---------------------------------------------------------------- список */

const STATUS_FILTERS: { value: 'ALL' | UiOrderStatus; label: string }[] = [
  { value: 'ALL', label: 'Все статусы' },
  { value: 'ROUTED', label: STATUS.ROUTED.label },
  { value: 'UNASSIGNED', label: STATUS.UNASSIGNED.label },
  { value: 'PGP', label: STATUS.PGP.label },
  { value: 'CANCELLED', label: STATUS.CANCELLED.label },
];

/** Почему наряд нельзя отменить (null — можно). */
function cancelBlock(o: UiOrder, asOf?: string): string | null {
  if (o.status === 'CANCELLED') return 'Наряд уже отменён';
  if (o.stop_status === 'DONE') return 'Визит уже выполнен — отменить нельзя';
  if (o.stop_status === 'IN_PROGRESS' || (o.planned_start && asOf && o.planned_start < asOf)) {
    return 'Визит уже начат — по контракту отменяется только наряд, визит по которому не начат';
  }
  return null;
}

function OrdersList({
  orders,
  asOf,
  onPlanEvent,
  onNotice,
  onAdd,
}: {
  orders: UiOrder[];
  asOf?: string;
  onPlanEvent: (item: DemoStepItem) => Promise<boolean>;
  onNotice: (text: string) => void;
  onAdd: () => void;
}) {
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<'ALL' | UiOrderStatus>('ALL');
  const [confirm, setConfirm] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<{ id: string; text: string } | null>(null);

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return orders
      .filter((o) => status === 'ALL' || o.status === status)
      .filter(
        (o) => !q || [o.order_id, o.address, o.staff_name, o.staff_id, o.work_type_name].some((v) => (v ?? '').toLowerCase().includes(q)),
      )
      .sort((a, b) => a.slot.start.localeCompare(b.slot.start) || a.order_id.localeCompare(b.order_id));
  }, [orders, query, status]);
  const filtered = query.trim() !== '' || status !== 'ALL';

  const cancel = async (o: UiOrder) => {
    setBusy(o.order_id);
    setError(null);
    try {
      const res = await api.ordersCancel(o.order_id);
      setConfirm(null);
      onNotice(`${res.summary}. План v${res.plan_version}.`);
      await onPlanEvent(res.event);
    } catch (e) {
      setError({ id: o.order_id, text: errText(e) });
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="dpanel__body">
      <div className="dpanel__tools">
        <input
          className="input"
          type="search"
          placeholder="Номер, адрес или исполнитель"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Поиск наряда"
        />
        <Button size="s" label="+ Новый наряд" onClick={onAdd} />
      </div>
      <div className="dpanel__filters">
        <select className="select" value={status} onChange={(e) => setStatus(e.target.value as 'ALL' | UiOrderStatus)} aria-label="Статус">
          {STATUS_FILTERS.map((f) => (
            <option key={f.value} value={f.value}>
              {f.label}
            </option>
          ))}
        </select>
        <span className="dpanel__count num">{filtered ? `${rows.length} из ${orders.length}` : `${orders.length} нарядов`}</span>
      </div>

      <div className="scroll">
        {!rows.length ? (
          <p className="empty">{orders.length ? 'Ничего не нашли. Измените поиск или фильтр.' : 'Нарядов на день нет.'}</p>
        ) : (
          <ul className="dord">
            {rows.map((o) => {
              const st = STATUS[o.status];
              const block = cancelBlock(o, asOf);
              const started = o.stop_status === 'IN_PROGRESS' ? 'в работе' : o.stop_status === 'DONE' ? 'выполнен' : null;
              return (
                <li key={o.order_id} className="dord__row">
                  <div className="dord__main">
                    <span className="dord__top">
                      <span className="dord__id num">{o.order_id.slice(-4)}</span>
                      <span className="dord__slot num">{window2(o.slot)}</span>
                      <span className="dord__wt">{WORK_TYPE_SHORT[o.work_type_code] ?? o.work_type_name}</span>
                    </span>
                    <span className="dord__addr" title={o.address}>
                      {o.address.replace(/^г\. Екатеринбург,\s*/, '')}
                    </span>
                    <span className="dord__who">
                      {o.staff_name ? (
                        <>
                          {o.staff_name}
                          {o.planned_start && <span className="num"> · {hhmm(o.planned_start)}</span>}
                        </>
                      ) : o.status === 'UNASSIGNED' && o.reason_code ? (
                        REASON[o.reason_code]
                      ) : (
                        '—'
                      )}
                    </span>
                  </div>
                  <div className="dord__side">
                    <Badge size="2xs" variant="secondary" colorScheme={st.scheme} label={started ?? st.label} />
                    {o.status !== 'CANCELLED' && (
                      <span title={block ?? 'Отменить наряд (по просьбе абонента)'}>
                        <Button
                          size="s"
                          variant="ghost"
                          colorScheme="neutral"
                          label="Отменить"
                          disabled={!!block || busy !== null}
                          onClick={() => {
                            setConfirm(o.order_id);
                            setError(null);
                          }}
                        />
                      </span>
                    )}
                  </div>
                  {confirm === o.order_id && (
                    <div className="dord__confirm" role="alertdialog" aria-label={`Отмена наряда ${o.order_id}`}>
                      <span>
                        Отменить {o.order_id}? Абонент отказался от визита; маршрут будет пересчитан.
                      </span>
                      <span className="dord__confirm-actions">
                        <Button
                          size="s"
                          label={busy === o.order_id ? 'Отменяем…' : 'Да, отменить'}
                          disabled={busy !== null}
                          onClick={() => void cancel(o)}
                        />
                        <Button size="s" variant="ghost" colorScheme="neutral" label="Нет" disabled={busy !== null} onClick={() => setConfirm(null)} />
                      </span>
                    </div>
                  )}
                  {error?.id === o.order_id && (
                    <p className="dform__error dord__error" role="alert">
                      {error.text}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------- форма */

const SLOT_HOURS = [9, 11, 13, 15, 17, 19];
const toMin = (hm: string) => {
  const [h, m] = hm.split(':').map(Number);
  return h * 60 + m;
};
const pad = (n: number) => String(n).padStart(2, '0');

/** Ближайшее стандартное окно (как на сервере: начало не раньше чем через 30 мин). */
function nearestSlot(asOf?: string): string | null {
  if (!asOf) return null;
  const now = toMin(hhmm(asOf));
  const h = SLOT_HOURS.find((x) => x * 60 - now >= 30);
  return h === undefined ? null : `${pad(h)}:00–${pad(h + 2)}:00`;
}

const shortAddr = (a: string) => a.replace(/^г\. Екатеринбург,\s*/, '');

function NewOrderForm({
  asOf,
  onPlanEvent,
  onBack,
}: {
  asOf?: string;
  onPlanEvent: (item: DemoStepItem) => Promise<boolean>;
  onBack: () => void;
}) {
  const [catalog, setCatalog] = useState<OrderCatalog | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [building, setBuilding] = useState<OrderCatalogBuilding | null>(null);
  const [workType, setWorkType] = useState<WorkTypeCode | ''>('');
  const [repeat, setRepeat] = useState(false);
  const [slotMode, setSlotMode] = useState<'nearest' | 'manual'>('nearest');
  const [slotStart, setSlotStart] = useState('');
  const [slotEnd, setSlotEnd] = useState('');
  const [preferred, setPreferred] = useState<OrderCatalogStaff | null>(null);
  const [touched, setTouched] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CreateOrderResult | null>(null);

  useEffect(() => {
    let alive = true;
    api
      .ordersCatalog({})
      .then((c) => alive && setCatalog(c))
      .catch((e) => alive && setLoadError(errText(e)));
    return () => {
      alive = false;
    };
  }, []);

  const wt = catalog?.work_types.find((w) => w.code === workType) ?? null;
  const nearest = nearestSlot(asOf);
  const errors: Partial<Record<'building' | 'workType' | 'slot', string>> = {};
  if (!building) errors.building = 'Выберите адрес из списка';
  if (!workType) errors.workType = 'Выберите тип работ';
  else if (building && !building.work_types.includes(workType)) errors.workType = 'Этот тип работ не выполняется по адресу';
  if (slotMode === 'manual') {
    if (!slotStart || !slotEnd) errors.slot = 'Укажите начало и конец окна';
    else if (toMin(slotEnd) <= toMin(slotStart)) errors.slot = 'Конец окна позже начала';
  } else if (!nearest) errors.slot = 'Стандартных окон на сегодня не осталось — задайте вручную';
  const shown = touched ? errors : {};

  const reset = () => {
    setResult(null);
    setBuilding(null);
    setWorkType('');
    setRepeat(false);
    setPreferred(null);
    setTouched(false);
    setError(null);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length || saving || !building || !workType) return;
    setSaving(true);
    setError(null);
    try {
      const res = await api.ordersCreate({
        building_id: building.building_id,
        work_type: workType,
        slot: slotMode === 'manual' ? { start: slotStart, end: slotEnd } : null,
        is_repeat: !!wt?.repeat_allowed && repeat,
        preferred_staff_id: preferred?.staff_id ?? null,
      });
      setResult(res);
      await onPlanEvent(res.event);
    } catch (err) {
      setError(errText(err));
    } finally {
      setSaving(false);
    }
  };

  if (result) {
    return (
      <div className="dpanel__body">
        <div className="scroll dform__scroll">
          <OrderResult res={result} />
        </div>
        <div className="dform__foot">
          <div className="dform__actions">
            <Button size="m" label="Ещё наряд" onClick={reset} />
            <Button size="m" variant="ghost" colorScheme="neutral" label="К списку" onClick={onBack} />
          </div>
        </div>
      </div>
    );
  }

  return (
    <form className="dpanel__body dform" onSubmit={submit} noValidate>
      <div className="dform__nav">
        <Button size="s" variant="ghost" colorScheme="neutral" label="Назад к списку" onClick={onBack} />
      </div>
      <div className="dform__hero">
        <div>
          <h3 className="dform__title">Новый наряд</h3>
          <p className="dform__sub">
            Поступит в план в модельное время <span className="num">{hhmm(asOf)}</span>, номер присвоится сам
          </p>
        </div>
      </div>

      <div className="scroll dform__scroll">
        {loadError && <p className="dform__error">Справочники не загрузились: {loadError}</p>}
        <fieldset className="dform__group">
          <legend>Что и где</legend>
          <OField label="Адрес" required wide error={shown.building}>
            <BuildingPicker
              value={building}
              onChange={(b) => {
                setBuilding(b);
                setError(null);
                if (b && workType && !b.work_types.includes(workType)) setWorkType('');
              }}
              total={catalog?.buildings_count}
            />
          </OField>
          <OField label="Тип работ" required wide error={shown.workType}>
            <select
              className="select"
              value={workType}
              onChange={(e) => {
                setWorkType(e.target.value as WorkTypeCode);
                setError(null);
              }}
              disabled={!catalog}
            >
              <option value="">Выберите</option>
              {catalog?.work_types.map((w) => {
                const off = !!building && !building.work_types.includes(w.code);
                return (
                  <option key={w.code} value={w.code} disabled={off}>
                    {w.name} · {w.duration_min} мин{off ? ' (не для этого адреса)' : ''}
                  </option>
                );
              })}
            </select>
          </OField>
          {wt?.repeat_allowed && (
            <label className="dord__check dfield--wide">
              <input type="checkbox" checked={repeat} onChange={(e) => setRepeat(e.target.checked)} />
              Повторное обращение (сокращённый контрольный срок, повышенный приоритет)
            </label>
          )}
        </fieldset>

        <fieldset className="dform__group">
          <legend>Окно визита</legend>
          <div className="dfield dfield--wide">
            <SegmentedControl
              size="s"
              value={slotMode}
              onChange={(v) => setSlotMode(v as 'nearest' | 'manual')}
              items={[
                { index: 'nearest', label: 'Ближайшее окно' },
                { index: 'manual', label: 'Вручную' },
              ]}
            />
            {slotMode === 'nearest' && nearest && (
              <span className="dfield__hint">
                <span className="num">{nearest}</span> — первое стандартное окно, до которого не меньше 30 мин
              </span>
            )}
            {shown.slot && <span className="dfield__msg">{shown.slot}</span>}
          </div>
          {slotMode === 'manual' && (
            <>
              <OField label="С">
                <input className="input num" type="time" value={slotStart} onChange={(e) => setSlotStart(e.target.value)} />
              </OField>
              <OField label="До">
                <input className="input num" type="time" value={slotEnd} onChange={(e) => setSlotEnd(e.target.value)} />
              </OField>
            </>
          )}
        </fieldset>

        <fieldset className="dform__group">
          <legend>Исполнитель</legend>
          <OField
            label="Предпочтительный исполнитель"
            wide
            hint="Пожелание, а не приказ: учтём, если сотрудник подходит и успевает без срыва своих визитов"
          >
            <StaffPicker staff={catalog?.staff ?? []} value={preferred} onChange={setPreferred} />
          </OField>
        </fieldset>
      </div>

      <div className="dform__foot">
        {error && (
          <p className="dform__error" role="alert">
            {error}
          </p>
        )}
        <div className="dform__actions">
          <button type="submit" className="btn btn--m btn--primary btn--accent" disabled={saving || !catalog}>
            {saving ? 'Планируем…' : 'Создать и назначить'}
          </button>
          <Button size="m" variant="ghost" colorScheme="neutral" label="Отмена" onClick={onBack} />
        </div>
      </div>
    </form>
  );
}

function OField({
  label,
  required,
  error,
  hint,
  wide,
  children,
}: {
  label: string;
  required?: boolean;
  error?: string;
  hint?: string;
  wide?: boolean;
  children: ReactNode;
}) {
  return (
    <div className={`dfield${wide ? ' dfield--wide' : ''}${error ? ' dfield--error' : ''}`}>
      <span className="dfield__label">
        {label}
        {required && <span className="dfield__req" aria-hidden> *</span>}
      </span>
      {children}
      {error ? <span className="dfield__msg">{error}</span> : hint ? <span className="dfield__hint">{hint}</span> : null}
    </div>
  );
}

/* ---- адрес: поиск по подстроке на сервере (≤ 20 вариантов) */

function BuildingPicker({
  value,
  onChange,
  total,
}: {
  value: OrderCatalogBuilding | null;
  onChange: (b: OrderCatalogBuilding | null) => void;
  total?: number;
}) {
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<OrderCatalogBuilding[]>([]);
  const [found, setFound] = useState(0);
  const [loading, setLoading] = useState(false);
  const seq = useRef(0);

  useEffect(() => {
    if (value || q.trim().length < 2) {
      setItems([]);
      setFound(0);
      return;
    }
    const n = ++seq.current;
    setLoading(true);
    const t = setTimeout(() => {
      api
        .ordersCatalog({ q: q.trim() })
        .then((c) => {
          if (n !== seq.current) return;
          setItems(c.buildings);
          setFound(c.buildings_total);
        })
        .catch(() => n === seq.current && setItems([]))
        .finally(() => n === seq.current && setLoading(false));
    }, 250);
    return () => clearTimeout(t);
  }, [q, value]);

  if (value) {
    return (
      <div className="dord__picked">
        <span>
          <b>{shortAddr(value.address)}</b>
          <span className="dord__picked-meta">
            {value.building_type === 'MKD' ? 'МКД' : value.overhead_line ? 'частный дом, ВЛС' : 'частный дом'} ·{' '}
            {value.technologies.join('/')} · {value.building_id}
          </span>
        </span>
        <Button size="s" variant="ghost" colorScheme="neutral" label="Изменить" onClick={() => onChange(null)} />
      </div>
    );
  }
  return (
    <div className="dord__combo">
      <input
        className="input"
        type="search"
        placeholder={total ? `Улица и дом — ${total} адресов в зоне` : 'Улица и дом'}
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        aria-label="Поиск адреса"
        autoComplete="off"
      />
      {open && q.trim().length >= 2 && (
        <ul className="dord__options" role="listbox">
          {loading && !items.length ? (
            <li className="dord__option dord__option--muted">Ищем…</li>
          ) : !items.length ? (
            <li className="dord__option dord__option--muted">Адрес не найден</li>
          ) : (
            items.map((b) => (
              <li key={b.building_id} role="option" aria-selected={false}>
                <button
                  type="button"
                  className="dord__option"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => {
                    onChange(b);
                    setOpen(false);
                  }}
                >
                  {shortAddr(b.address)}
                  <span className="dord__option-meta">
                    {b.building_type === 'MKD' ? 'МКД' : 'частный'} · {b.technologies.join('/')}
                  </span>
                </button>
              </li>
            ))
          )}
          {found > items.length && <li className="dord__option dord__option--muted">ещё {found - items.length} — уточните запрос</li>}
        </ul>
      )}
    </div>
  );
}

/* ---- предпочтительный исполнитель: выпадающий список с поиском по ФИО */

function StaffPicker({
  staff,
  value,
  onChange,
}: {
  staff: OrderCatalogStaff[];
  value: OrderCatalogStaff | null;
  onChange: (s: OrderCatalogStaff | null) => void;
}) {
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const items = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return staff
      .filter((s) => !needle || [s.display_name, s.staff_id].some((v) => (v ?? '').toLowerCase().includes(needle)))
      .sort((a, b) => Number(b.on_shift) - Number(a.on_shift) || (a.display_name ?? a.staff_id).localeCompare(b.display_name ?? b.staff_id, 'ru'))
      .slice(0, 30);
  }, [staff, q]);

  if (value) {
    return (
      <div className="dord__picked">
        <span>
          <b>{value.display_name ?? value.staff_id}</b>
          <span className="dord__picked-meta">
            {value.staff_id} · {value.profiles.join('/')} · {value.technologies.join('/')}
            {value.height_permit ? ' · допуск к высоте' : ''}
          </span>
        </span>
        <Button size="s" variant="ghost" colorScheme="neutral" label="Убрать" onClick={() => onChange(null)} />
      </div>
    );
  }
  return (
    <div className="dord__combo">
      <input
        className="input"
        type="search"
        placeholder="Не важно — назначит планировщик"
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        aria-label="Поиск сотрудника по ФИО"
        autoComplete="off"
      />
      {open && (
        <ul className="dord__options" role="listbox">
          {!items.length ? (
            <li className="dord__option dord__option--muted">Никого не нашли</li>
          ) : (
            items.map((s) => (
              <li key={s.staff_id} role="option" aria-selected={false}>
                <button
                  type="button"
                  className="dord__option"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => {
                    onChange(s);
                    setQ('');
                    setOpen(false);
                  }}
                >
                  {s.display_name ?? s.staff_id}
                  <span className="dord__option-meta">
                    {s.profiles.join('/')} · {s.staff_id}
                    {s.on_shift ? '' : ' · не на смене'}
                  </span>
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}

/* ---- итог создания */

function OrderResult({ res }: { res: CreateOrderResult }) {
  const o = res.order;
  const a = res.assignment;
  const u = res.unassigned;
  return (
    <div className="dres">
      <div className={`dres__card dres__card--${res.outcome === 'ASSIGNED' ? 'ok' : res.outcome === 'PGP' ? 'info' : 'bad'}`}>
        {a ? (
          <>
            <p className="dres__title">
              Назначен: {a.staff_name ?? a.staff_id}, ETA <span className="num">{hhmm(a.eta)}</span>
            </p>
            <p className="dres__line num">
              Визит {hhmm(a.planned_start)}–{hhmm(a.planned_end)} · {a.staff_id}
            </p>
          </>
        ) : res.pgp ? (
          <>
            <p className="dres__title">Групповое повреждение (ПГП) {res.pgp.group_id}</p>
            <p className="dres__line">{res.pgp.message}</p>
            {res.pgp.comment && <p className="dres__line">{res.pgp.comment}</p>}
          </>
        ) : (
          <>
            <p className="dres__title">Не удалось назначить{u ? `: ${REASON[u.reason_code] ?? u.reason_code}` : ''}</p>
            {u && <p className="dres__line">{u.reason_text}</p>}
            {u && (
              <p className="dres__line">
                Что делать: {ACTION[u.suggested_action] ?? u.suggested_action}
                {u.proposed_slot && <span className="num"> · предложен перенос на {u.proposed_slot.start.slice(8, 10)}.{u.proposed_slot.start.slice(5, 7)} {window2(u.proposed_slot)}</span>}
              </p>
            )}
          </>
        )}
      </div>

      {res.preferred && (
        <p className={`dres__pref dres__pref--${res.preferred.honored ? 'ok' : 'warn'}`}>{res.preferred.message}</p>
      )}

      <dl className="dres__facts">
        <dt>Наряд</dt>
        <dd className="num">{res.order_id}</dd>
        <dt>Адрес</dt>
        <dd>{shortAddr(o.address)}</dd>
        <dt>Тип работ</dt>
        <dd>{res.work_type_name}</dd>
        <dt>Окно</dt>
        <dd className="num">
          {window2(o.slot)}, крайнее начало {hhmm(o.latest_start)}
        </dd>
        <dt>План</dt>
        <dd className="num">версия {res.plan_version}</dd>
      </dl>

      {res.warnings.length > 0 && (
        <div className="dres__warn">
          <p className="dres__warn-title">Затронуло другие наряды</p>
          <ul>
            {res.warnings.slice(0, 4).map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
```

## File: frontend/src/components/dataPanel.css
```css
/* Боковая панель «Данные»: выезжает справа поверх рабочей области, токены Атомаро. */

.dpanel-root {
  position: fixed;
  inset: 0;
  z-index: 3000;
  pointer-events: none;
}
.dpanel-scrim {
  position: absolute;
  inset: 0;
  background: rgba(31, 29, 43, 0.16);
  pointer-events: auto;
  animation: dpanel-fade 160ms ease-out;
}
.dpanel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(560px, 100vw);
  display: flex;
  flex-direction: column;
  background: var(--atmr-bg-surface1);
  border-left: 3px solid var(--atmr-accent);
  box-shadow: -12px 0 32px rgba(56, 20, 120, 0.14);
  pointer-events: auto;
  outline: none;
  animation: dpanel-in 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
  font: var(--atmr-font-body-m);
  color: var(--atmr-text-primary);
}
@keyframes dpanel-in {
  from {
    transform: translateX(32px);
    opacity: 0;
  }
}
@keyframes dpanel-fade {
  from {
    opacity: 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .dpanel,
  .dpanel-scrim {
    animation: none;
  }
}

.dpanel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--atmr-spacing-4x) var(--atmr-spacing-4x) var(--atmr-spacing-1x);
}
.dpanel__title {
  margin: 0;
  font: var(--atmr-font-heading-h4);
}
.dpanel__close {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: var(--atmr-border-radius-s);
  background: none;
  color: var(--atmr-text-secondary);
  cursor: pointer;
}
.dpanel__close:hover {
  background: var(--atmr-bg-surface3);
  color: var(--atmr-text-primary);
}
.dpanel :focus-visible {
  outline: 2px solid var(--atmr-accent);
  outline-offset: 1px;
}

.dpanel__notice {
  margin: var(--atmr-spacing-3x) var(--atmr-spacing-4x) 0;
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-status-success-muted);
  color: var(--atmr-status-success);
  font: var(--atmr-font-body-s);
}

.dpanel__body {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}
.dpanel__tools {
  display: flex;
  gap: var(--atmr-spacing-2x);
  padding: var(--atmr-spacing-3x) var(--atmr-spacing-4x) var(--atmr-spacing-2x);
}
.dpanel__tools .input {
  min-width: 0;
}
.dpanel__filters {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  padding: 0 var(--atmr-spacing-4x) var(--atmr-spacing-3x);
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
}
.dpanel__filters .select {
  height: 28px;
  padding: 0 var(--atmr-spacing-2x);
  color: var(--atmr-text-secondary);
}
.dpanel__count {
  margin-left: auto;
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
}
.dpanel__state {
  display: grid;
  justify-items: start;
  gap: var(--atmr-spacing-3x);
  padding: var(--atmr-spacing-6x) var(--atmr-spacing-4x);
  color: var(--atmr-status-error);
  font: var(--atmr-font-body-s);
}
.dpanel__soon {
  padding: var(--atmr-spacing-6x) var(--atmr-spacing-4x);
  color: var(--atmr-text-secondary);
  font: var(--atmr-font-body-s);
}
.dpanel__soon p {
  margin: 0 0 var(--atmr-spacing-1x);
}
.dpanel__soon-title {
  font: var(--atmr-font-heading-h5);
  color: var(--atmr-text-primary);
}

/* ---- монограмма: фиолетовая у ЛТП-3, оранжевая у инсталляторов */
.dmono {
  flex-shrink: 0;
  display: inline-grid;
  place-items: center;
  border-radius: 50%;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.dmono--s {
  width: 32px;
  height: 32px;
  font-size: 12px;
}
.dmono--l {
  width: 52px;
  height: 52px;
  font-size: 18px;
}
.dmono--ltp {
  background: var(--atmr-accent-muted);
  color: var(--atmr-accent);
}
.dmono--install {
  background: var(--atmr-brand-orange-muted);
  color: var(--atmr-brand-orange);
}

/* ---- список сотрудников */
.dstaff {
  list-style: none;
  margin: 0;
  padding: var(--atmr-spacing-1x) 0 var(--atmr-spacing-4x);
}
.dstaff__row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-3x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-4x);
  border: 0;
  background: none;
  text-align: left;
  font: inherit;
  color: inherit;
  cursor: pointer;
}
.dstaff__row:hover {
  background: var(--atmr-bg-surface2);
}
.dstaff__who {
  flex: 1 1 auto;
  min-width: 0;
  display: grid;
}
.dstaff__name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dstaff__meta {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dstaff__ids {
  flex-shrink: 0;
  text-align: right;
  font: var(--atmr-font-body-s);
  color: var(--atmr-text-secondary);
}

/* ---- форма */
.dform__nav {
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-2x) 0;
}
.dform__hero {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-3x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-4x) var(--atmr-spacing-3x);
}
.dform__title {
  margin: 0;
  font: var(--atmr-font-heading-h5);
}
.dform__sub {
  margin: 0;
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
}
.dform__warn {
  margin: 0 var(--atmr-spacing-4x) var(--atmr-spacing-3x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border-left: 3px solid var(--atmr-brand-orange);
  border-radius: var(--atmr-border-radius-xs);
  background: var(--atmr-brand-orange-muted);
  font: var(--atmr-font-body-s);
}
.dform__scroll {
  border-top: var(--atmr-border-width-s) solid var(--atmr-border-default);
}
.dform__group {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--atmr-spacing-3x);
  margin: 0;
  padding: var(--atmr-spacing-4x);
  border: 0;
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-bg-surface3);
}
.dform__group legend {
  float: left;
  grid-column: 1 / -1;
  padding: 0;
  font: var(--atmr-font-heading-h5);
  font-size: 14px;
  color: var(--atmr-text-secondary);
}
.dfield {
  display: grid;
  gap: var(--atmr-spacing-1x);
  min-width: 0;
}
.dfield--wide {
  grid-column: 1 / -1;
}
.dfield__label {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
}
.dfield__req {
  color: var(--atmr-brand-orange);
}
.dfield .input,
.dfield .select {
  width: 100%;
  min-width: 0;
  height: 36px;
  font: var(--atmr-font-body-m);
}
.dfield .select:disabled {
  background: var(--atmr-bg-surface2);
  color: var(--atmr-text-secondary);
}
.dfield .dform__note {
  height: auto;
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  resize: vertical;
}
.dfield--error .input,
.dfield--error .select {
  border-color: var(--atmr-status-error);
}
.dfield__msg {
  font: var(--atmr-font-description-s);
  color: var(--atmr-status-error);
}
.dfield__hint {
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-tertiary);
}
.dform__foot {
  display: grid;
  gap: var(--atmr-spacing-2x);
  padding: var(--atmr-spacing-3x) var(--atmr-spacing-4x) var(--atmr-spacing-4x);
  border-top: var(--atmr-border-width-s) solid var(--atmr-border-default);
}
.dform__actions {
  display: flex;
  gap: var(--atmr-spacing-2x);
}
.dform__error {
  margin: 0;
  color: var(--atmr-status-error);
  font: var(--atmr-font-body-s);
}
.dform__hint {
  margin: 0;
  color: var(--atmr-text-tertiary);
  font: var(--atmr-font-description-m);
}

/* ---- баннер «пересоберите день» */
.dpanel__replan {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--atmr-spacing-3x);
  margin: var(--atmr-spacing-3x) var(--atmr-spacing-4x) 0;
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-2x) var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border-left: 3px solid var(--atmr-brand-orange);
  border-radius: var(--atmr-border-radius-xs);
  background: var(--atmr-brand-orange-muted);
  font: var(--atmr-font-body-s);
  font-weight: 500;
}

/* ---- рюкзак АО: сетка старт/максимум × ONT, роутер, STB */
.deq {
  display: block;
}
.deq legend {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  margin-bottom: var(--atmr-spacing-2x);
}
.deq__badge {
  display: inline-flex;
}
.deq__grid {
  display: grid;
  grid-template-columns: max-content repeat(3, minmax(56px, 88px));
  align-items: center;
  gap: var(--atmr-spacing-1x) var(--atmr-spacing-2x);
}
.deq__col {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
  text-align: center;
}
.deq__row {
  padding-right: var(--atmr-spacing-2x);
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
}
.deq__row--data,
.deq__data {
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-tertiary);
}
.deq__data {
  text-align: center;
}
.deq__grid .deq__input {
  width: 100%;
  min-width: 0;
  height: 32px;
  text-align: center;
}
.deq__grid .deq__input--error {
  border-color: var(--atmr-status-error);
}
.deq__msg,
.deq__hint,
.deq__rec {
  display: block;
  margin: var(--atmr-spacing-2x) 0 0;
}
.deq__rec {
  font: var(--atmr-font-body-s);
  color: var(--atmr-text-secondary);
}
.deq__rec b {
  color: var(--atmr-accent);
  font-weight: 600;
}
.deq__actions {
  margin-top: var(--atmr-spacing-2x);
}

/* ---- «Задать всем» */
.dbulk {
  margin: 0 var(--atmr-spacing-4x) var(--atmr-spacing-3x);
  padding: var(--atmr-spacing-3x);
  border: var(--atmr-border-width-s) solid var(--atmr-border-default);
  border-top: 3px solid var(--atmr-accent);
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-bg-surface2);
}
.dbulk__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--atmr-spacing-2x);
  margin-bottom: var(--atmr-spacing-3x);
}
.dbulk__title {
  font: var(--atmr-font-heading-h5);
  font-size: 14px;
}
.dbulk__link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--atmr-accent);
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.dbulk__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--atmr-spacing-2x);
  margin-top: var(--atmr-spacing-3x);
}

@media (max-width: 480px) {
  .dpanel {
    border-left: 0;
  }
  .dpanel__filters {
    flex-wrap: wrap;
  }
  .dstaff__ids {
    display: none;
  }
}

/* ---- вкладка «Наряды»: список */
.dord {
  list-style: none;
  margin: 0;
  padding: var(--atmr-spacing-1x) 0 var(--atmr-spacing-4x);
}
.dord__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--atmr-spacing-1x) var(--atmr-spacing-3x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-4x);
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
}
.dord__row:hover {
  background: var(--atmr-bg-surface2);
}
.dord__main {
  display: grid;
  min-width: 0;
}
.dord__top {
  display: flex;
  align-items: baseline;
  gap: var(--atmr-spacing-2x);
  font: var(--atmr-font-body-s);
  color: var(--atmr-text-secondary);
}
.dord__id {
  font-weight: 600;
  color: var(--atmr-accent);
}
.dord__wt {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dord__addr {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dord__who {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dord__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--atmr-spacing-1x);
}
.dord__confirm {
  grid-column: 1 / -1;
  display: grid;
  gap: var(--atmr-spacing-2x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-brand-orange-muted);
  font: var(--atmr-font-body-s);
}
.dord__confirm-actions {
  display: flex;
  gap: var(--atmr-spacing-2x);
}
.dord__error {
  grid-column: 1 / -1;
  margin: 0;
}

/* ---- форма нового наряда */
.dord__check {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  font: var(--atmr-font-body-s);
  color: var(--atmr-text-secondary);
}
.dord__combo {
  position: relative;
}
.dord__combo .input {
  width: 100%;
}
.dord__options {
  position: absolute;
  z-index: 5;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 260px;
  overflow-y: auto;
  margin: 0;
  padding: var(--atmr-spacing-1x) 0;
  list-style: none;
  background: var(--atmr-bg-surface1);
  border: var(--atmr-border-width-s) solid var(--atmr-border-default);
  border-radius: var(--atmr-border-radius-s);
  box-shadow: 0 8px 24px rgba(56, 20, 120, 0.14);
}
.dord__option {
  width: 100%;
  display: flex;
  justify-content: space-between;
  gap: var(--atmr-spacing-2x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border: 0;
  background: none;
  text-align: left;
  font: var(--atmr-font-body-s);
  color: inherit;
  cursor: pointer;
}
button.dord__option:hover {
  background: var(--atmr-accent-muted);
}
.dord__option--muted {
  color: var(--atmr-text-tertiary);
  cursor: default;
}
.dord__option-meta {
  flex-shrink: 0;
  color: var(--atmr-text-tertiary);
}
.dord__picked {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--atmr-spacing-2x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-accent-muted);
}
.dord__picked > span {
  display: grid;
  min-width: 0;
}
.dord__picked-meta {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
}

/* ---- итог создания наряда */
.dres {
  display: grid;
  gap: var(--atmr-spacing-3x);
  padding: var(--atmr-spacing-4x);
}
.dres__card {
  padding: var(--atmr-spacing-3x) var(--atmr-spacing-4x);
  border-radius: var(--atmr-border-radius-m);
  border-left: 4px solid;
}
.dres__card--ok {
  background: var(--atmr-status-success-muted);
  border-color: var(--atmr-status-success);
}
.dres__card--bad {
  background: var(--atmr-status-error-muted);
  border-color: var(--atmr-status-error);
}
.dres__card--info {
  background: var(--atmr-accent-muted);
  border-color: var(--atmr-accent);
}
.dres__title {
  margin: 0 0 var(--atmr-spacing-1x);
  font: var(--atmr-font-heading-h5);
}
.dres__line {
  margin: 0;
  font: var(--atmr-font-body-s);
  color: var(--atmr-text-secondary);
}
.dres__pref {
  margin: 0;
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border-radius: var(--atmr-border-radius-s);
  font: var(--atmr-font-body-s);
}
.dres__pref--ok {
  background: var(--atmr-status-success-muted);
  color: var(--atmr-status-success);
}
.dres__pref--warn {
  background: var(--atmr-brand-orange-muted);
  color: var(--atmr-text-primary);
}
.dres__facts {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: var(--atmr-spacing-1x) var(--atmr-spacing-3x);
  margin: 0;
  font: var(--atmr-font-body-s);
}
.dres__facts dt {
  color: var(--atmr-text-tertiary);
}
.dres__facts dd {
  margin: 0;
}
.dres__warn {
  font: var(--atmr-font-body-s);
  color: var(--atmr-text-secondary);
}
.dres__warn-title {
  margin: 0 0 var(--atmr-spacing-1x);
  font-weight: 500;
  color: var(--atmr-text-primary);
}
.dres__warn ul {
  margin: 0;
  padding-left: var(--atmr-spacing-4x);
}
```

## File: frontend/src/components/DataPanel.tsx
```typescript
/*
 * Боковая панель «Данные» (slide-over справа): справочник сотрудников с ФИО.
 * Закрывается Esc, кликом по затемнению и крестиком. Вкладка «Наряды» — components/DataOrders.tsx.
 * Данные и сохранение — через StaffDirectoryContext (hooks/useStaffDirectory).
 * «Рюкзак АО» (старт/максимум ONT, роутер, STB): в карточке и «Задать всем» в списке;
 * применяется при пересборке дня (баннер с кнопкой «Пересобрать»).
 */
import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Badge, Button, SegmentedControl, TabsGroup } from './ui';
import { PROFILE_SHORT } from '../planModel';
import { useStaffDirectoryContext } from '../hooks/useStaffDirectory';
import { OrdersTab } from './DataOrders';
import type {
  DemoStepItem,
  UiOrder,
  Equipment,
  EquipmentScope,
  Profile,
  RecommendedBackpack,
  ServiceCenter,
  StaffProfile,
  StaffProfileInput,
  StaffProfileSaved,
} from '../types/api';
import './dataPanel.css';

type Tab = 'staff' | 'orders';
type View = { kind: 'list' } | { kind: 'edit'; id: string } | { kind: 'new' };

interface Props {
  serviceCenters: ServiceCenter[];
  onClose: () => void;
  /** Наряды дня со статусами (вкладка «Наряды»). */
  orders: UiOrder[];
  day?: string;
  /** Модельное время плана. */
  asOf?: string;
  /** Событие отправлено с панели — обновить план и строку итога. */
  onPlanEvent: (item: DemoStepItem) => Promise<boolean>;
  /** Перезагрузка демо-дня (рюкзаки АО применяются только так); true — успешно. */
  onReplan: () => Promise<boolean>;
  replanBusy: boolean;
}

const PROFILES: Profile[] = ['LTP3', 'INSTALL'];

type Kind = keyof Equipment;
const KINDS: { k: Kind; label: string }[] = [
  { k: 'ONT', label: 'ONT' },
  { k: 'ROUTER', label: 'Роутер' },
  { k: 'STB', label: 'STB' },
];
const EQ_MAX = 99;
const SYNTH_HINT = 'Значения в данных синтетические; для настоящих данных берутся из staff.json.';

export function DataPanel({ serviceCenters, onClose, orders, day, asOf, onPlanEvent, onReplan, replanBusy }: Props) {
  const [tab, setTab] = useState<Tab>('staff');
  const [view, setView] = useState<View>({ kind: 'list' });
  const [notice, setNotice] = useState<string | null>(null);
  const dir = useStaffDirectoryContext();
  const panelRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    panelRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      prev?.focus?.();
    };
  }, [onClose]);

  // Подтверждение сохранения гаснет само.
  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), 5000);
    return () => clearTimeout(t);
  }, [notice]);

  const scName = useMemo(() => new Map(serviceCenters.map((s) => [s.service_center_id, s.name])), [serviceCenters]);

  return (
    <div className="dpanel-root">
      <div className="dpanel-scrim" onClick={onClose} aria-hidden />
      <aside className="dpanel" role="dialog" aria-label="Данные" tabIndex={-1} ref={panelRef}>
        <header className="dpanel__head">
          <h2 className="dpanel__title">Данные</h2>
          <button type="button" className="dpanel__close" onClick={onClose} aria-label="Закрыть панель" title="Закрыть (Esc)">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden>
              <path d="M3.5 3.5l9 9M12.5 3.5l-9 9" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
          </button>
        </header>
        <TabsGroup
          size="s"
          value={tab}
          onChange={(v) => setTab(v as Tab)}
          items={[
            { index: 'staff', label: dir?.items.length ? `Сотрудники ${dir.items.length}` : 'Сотрудники' },
            { index: 'orders', label: orders.length ? `Наряды ${orders.length}` : 'Наряды' },
          ]}
        />

        {notice && (
          <p className="dpanel__notice" role="status">
            {notice}
          </p>
        )}

        {dir?.equipmentPending && (
          <div className="dpanel__replan" role="status">
            <span>Рюкзаки изменены — пересоберите день</span>
            <Button
              size="s"
              label={replanBusy ? 'Пересобираем…' : 'Пересобрать'}
              disabled={replanBusy}
              onClick={() =>
                void onReplan().then((ok) => ok && setNotice('День пересобран: план построен с новыми рюкзаками АО.'))
              }
            />
          </div>
        )}

        {tab === 'orders' && (
          <OrdersTab orders={orders} day={day} asOf={asOf} onPlanEvent={onPlanEvent} onNotice={setNotice} />
        )}

        {tab === 'staff' && !dir && <p className="empty">Справочник сотрудников недоступен.</p>}

        {tab === 'staff' && dir && view.kind === 'list' && (
          <StaffList
            items={dir.items}
            loading={dir.loading}
            error={dir.error}
            onRetry={() => void dir.reload()}
            serviceCenters={serviceCenters}
            scName={scName}
            onOpen={(id) => setView({ kind: 'edit', id })}
            onAdd={() => setView({ kind: 'new' })}
            onNotice={setNotice}
          />
        )}

        {tab === 'staff' && dir && view.kind !== 'list' && (
          <StaffForm
            key={view.kind === 'edit' ? view.id : 'new'}
            profile={view.kind === 'edit' ? dir.items.find((p) => p.staff_id === view.id) ?? null : null}
            isNew={view.kind === 'new'}
            serviceCenters={serviceCenters}
            scName={scName}
            persisted={dir.persisted}
            recommended={dir.recommended}
            onBack={() => setView({ kind: 'list' })}
            onResetEquipment={async (id) => {
              const saved = await dir.resetEquipment(id);
              setNotice(`Рюкзак ${saved.display_name} сброшен к данным.`);
              return saved;
            }}
            onSave={async (body) => {
              if (view.kind === 'edit') {
                const saved = await dir.update(view.id, body);
                setNotice(
                  saved.applies_from_next_replan
                    ? `Сохранено: ${saved.display_name}. Рюкзак АО применится при пересборке дня.`
                    : `Сохранено: ${saved.display_name}`,
                );
              } else {
                const saved = await dir.create(body);
                setNotice(`Добавлен ${saved.display_name} (${saved.staff_id}). В план попадёт при следующей пересборке дня.`);
                setView({ kind: 'edit', id: saved.staff_id });
              }
            }}
          />
        )}
      </aside>
    </div>
  );
}

/* ---------------------------------------------------------------- список */

function initials(p: Pick<StaffProfile, 'first_name' | 'last_name'>): string {
  return ((p.last_name?.[0] ?? '') + (p.first_name?.[0] ?? '')).toUpperCase() || '?';
}

function Monogram({ p, size = 's' }: { p: Pick<StaffProfile, 'first_name' | 'last_name' | 'profile'>; size?: 's' | 'l' }) {
  return (
    <span className={`dmono dmono--${size} dmono--${p.profile === 'INSTALL' ? 'install' : 'ltp'}`} aria-hidden>
      {initials(p)}
    </span>
  );
}

function StaffList({
  items,
  loading,
  error,
  onRetry,
  serviceCenters,
  scName,
  onOpen,
  onAdd,
  onNotice,
}: {
  items: StaffProfile[];
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  serviceCenters: ServiceCenter[];
  scName: Map<string, string>;
  onOpen: (id: string) => void;
  onAdd: () => void;
  onNotice: (text: string) => void;
}) {
  const [query, setQuery] = useState('');
  const [bulkOpen, setBulkOpen] = useState(false);
  const [profile, setProfile] = useState<'ALL' | Profile>('ALL');
  const [sc, setSc] = useState('ALL');

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return items
      .filter((p) => profile === 'ALL' || p.profiles.includes(profile))
      .filter((p) => sc === 'ALL' || p.service_center_id === sc)
      .filter(
        (p) =>
          !q ||
          [p.full_name, p.staff_id, p.personnel_number, p.phone].some((v) => (v ?? '').toLowerCase().includes(q)),
      )
      .sort((a, b) => a.full_name.localeCompare(b.full_name, 'ru'));
  }, [items, query, profile, sc]);

  const filtered = query.trim() !== '' || profile !== 'ALL' || sc !== 'ALL';

  return (
    <div className="dpanel__body">
      <div className="dpanel__tools">
        <input
          className="input"
          type="search"
          placeholder="ФИО, табельный номер или телефон"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Поиск сотрудника"
        />
        <Button
          size="s"
          variant={bulkOpen ? 'secondary' : 'outline'}
          label="Задать всем"
          title="Рюкзак АО для всех или одного профиля"
          onClick={() => setBulkOpen((v) => !v)}
        />
        <Button size="s" label="+ Добавить" onClick={onAdd} />
      </div>
      {bulkOpen && (
        <BulkEquipment
          overridden={items.filter((p) => p.equipment_override).length}
          onDone={(text) => {
            onNotice(text);
            setBulkOpen(false);
          }}
          onCancel={() => setBulkOpen(false)}
        />
      )}
      <div className="dpanel__filters">
        <select className="select" value={profile} onChange={(e) => setProfile(e.target.value as 'ALL' | Profile)} aria-label="Профиль">
          <option value="ALL">Все профили</option>
          {PROFILES.map((p) => (
            <option key={p} value={p}>
              {PROFILE_SHORT[p]}
            </option>
          ))}
        </select>
        <select className="select" value={sc} onChange={(e) => setSc(e.target.value)} aria-label="Сервисный центр">
          <option value="ALL">Все СЦ</option>
          {serviceCenters.map((s) => (
            <option key={s.service_center_id} value={s.service_center_id}>
              {s.name}
            </option>
          ))}
        </select>
        {filtered && (
          <span className="dpanel__count num">
            {rows.length} из {items.length}
          </span>
        )}
      </div>

      <div className="scroll">
        {error && !items.length ? (
          <div className="dpanel__state">
            <p>Справочник не загрузился: {error}</p>
            <Button size="s" variant="outline" label="Повторить" onClick={onRetry} />
          </div>
        ) : loading && !items.length ? (
          <p className="empty">Загружаем справочник…</p>
        ) : !rows.length ? (
          <p className="empty">{items.length ? 'Никого не нашли. Измените поиск или фильтры.' : 'В справочнике пока никого нет. Добавьте первого сотрудника.'}</p>
        ) : (
          <ul className="dstaff">
            {rows.map((p) => (
              <li key={p.staff_id}>
                <button type="button" className="dstaff__row" onClick={() => onOpen(p.staff_id)}>
                  <Monogram p={p} />
                  <span className="dstaff__who">
                    <span className="dstaff__name">{p.full_name || p.staff_id}</span>
                    <span className="dstaff__meta">
                      {p.position ?? (p.profile ? PROFILE_SHORT[p.profile] : '')}
                      {p.service_center_id ? `, ${scName.get(p.service_center_id) ?? p.service_center_id}` : ''}
                    </span>
                  </span>
                  <span className="dstaff__ids num">
                    {p.staff_id}
                    {p.personnel_number && <span className="sub">таб. {p.personnel_number}</span>}
                  </span>
                  {p.equipment_override && <Badge size="2xs" variant="secondary" colorScheme="info" label="рюкзак изменён" />}
                  {!p.in_planner && <Badge size="2xs" variant="secondary" colorScheme="warning" label="не в плане" />}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------- форма */

interface FormState {
  last_name: string;
  first_name: string;
  middle_name: string;
  phone: string;
  position: string;
  personnel_number: string;
  note: string;
  profile: Profile | '';
  service_center_id: string;
}

const fromProfile = (p: StaffProfile | null): FormState => ({
  last_name: p?.last_name ?? '',
  first_name: p?.first_name ?? '',
  middle_name: p?.middle_name ?? '',
  phone: p?.phone ?? '',
  position: p?.position ?? '',
  personnel_number: p?.personnel_number ?? '',
  note: p?.note ?? '',
  profile: p?.profile ?? '',
  service_center_id: p?.service_center_id ?? '',
});

function validate(f: FormState, isNew: boolean): Partial<Record<keyof FormState, string>> {
  const e: Partial<Record<keyof FormState, string>> = {};
  if (!f.last_name.trim()) e.last_name = 'Укажите фамилию';
  if (!f.first_name.trim()) e.first_name = 'Укажите имя';
  if (f.phone.trim()) {
    const digits = f.phone.replace(/\D/g, '');
    const ok = digits.length === 10 || (digits.length === 11 && /^[78]/.test(digits));
    if (!ok) e.phone = 'Формат: +7 (9xx) xxx-xx-xx';
  }
  if (isNew && !f.profile) e.profile = 'Выберите профиль';
  if (isNew && !f.service_center_id) e.service_center_id = 'Выберите СЦ';
  return e;
}

function StaffForm({
  profile,
  isNew,
  serviceCenters,
  scName,
  persisted,
  recommended,
  onBack,
  onSave,
  onResetEquipment,
}: {
  profile: StaffProfile | null;
  isNew: boolean;
  serviceCenters: ServiceCenter[];
  scName: Map<string, string>;
  persisted: boolean;
  recommended: RecommendedBackpack | null;
  onBack: () => void;
  onSave: (body: StaffProfileInput) => Promise<void>;
  onResetEquipment: (staffId: string) => Promise<StaffProfileSaved>;
}) {
  const [form, setForm] = useState<FormState>(() => fromProfile(profile));
  const [eq, setEq] = useState<EqForm>(() => eqFrom(profile?.equipment_start, profile?.equipment_capacity));
  const [eqResetting, setEqResetting] = useState(false);
  const [touched, setTouched] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const firstRef = useRef<HTMLInputElement>(null);

  useEffect(() => firstRef.current?.focus(), []);

  if (!isNew && !profile) {
    return (
      <div className="dpanel__body">
        <div className="dpanel__state">
          <p>Сотрудник не найден в справочнике.</p>
          <Button size="s" variant="outline" label="К списку" onClick={onBack} />
        </div>
      </div>
    );
  }

  const hasEq = !isNew && !!profile?.equipment_start && !!profile.equipment_capacity;
  const eqInitial = eqFrom(profile?.equipment_start, profile?.equipment_capacity);
  const eqDirty = hasEq && !eqSame(eq, eqInitial);
  const eqErrors = hasEq ? validateEq(eq) : {};
  const errors = validate(form, isNew);
  const shown = touched ? errors : {};
  const locked = !isNew && !!profile?.in_planner; // профиль и СЦ задаёт справочник планировщика
  const initial = fromProfile(profile);
  const dirty = isNew || eqDirty || (Object.keys(form) as (keyof FormState)[]).some((k) => form[k] !== initial[k]);
  const set = (k: keyof FormState) => (e: { target: { value: string } }) => {
    setForm((f) => ({ ...f, [k]: e.target.value }));
    setError(null);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length || Object.keys(eqErrors).length || saving) return;
    const body: StaffProfileInput = {
      last_name: form.last_name.trim(),
      first_name: form.first_name.trim(),
      middle_name: form.middle_name.trim() || null,
      phone: form.phone.trim() || null,
      position: form.position.trim() || null,
      personnel_number: form.personnel_number.trim() || null,
      note: form.note.trim() || null,
    };
    if (!locked) {
      if (form.profile) body.profile = form.profile;
      if (form.service_center_id) body.service_center_id = form.service_center_id;
    }
    if (eqDirty) {
      body.equipment_start = eqNumbers(eq.start);
      body.equipment_capacity = eqNumbers(eq.cap);
    }
    setSaving(true);
    try {
      await onSave(body);
      setTouched(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  };

  const preview = {
    first_name: form.first_name,
    last_name: form.last_name,
    profile: (form.profile || null) as Profile | null,
  };
  const title = [form.last_name, form.first_name, form.middle_name].map((s) => s.trim()).filter(Boolean).join(' ');

  return (
    <form className="dpanel__body dform" onSubmit={submit} noValidate>
      <div className="dform__nav">
        <Button size="s" variant="ghost" colorScheme="neutral" label="Назад к списку" onClick={onBack} />
      </div>

      <div className="dform__hero">
        <Monogram p={preview} size="l" />
        <div>
          <h3 className="dform__title">{title || (isNew ? 'Новый сотрудник' : profile?.staff_id)}</h3>
          <p className="dform__sub">
            {isNew ? 'Номер ST-xxx присвоится при сохранении' : profile?.staff_id}
            {!isNew && profile?.service_center_id && `, ${scName.get(profile.service_center_id) ?? profile.service_center_id}`}
          </p>
        </div>
      </div>

      {(isNew || (profile && !profile.in_planner)) && (
        <p className="dform__warn">Новый сотрудник попадёт в план при следующей пересборке дня.</p>
      )}

      <div className="scroll dform__scroll">
        <fieldset className="dform__group">
          <legend>ФИО</legend>
          <Field label="Фамилия" required error={shown.last_name}>
            <input ref={firstRef} className="input" value={form.last_name} onChange={set('last_name')} maxLength={60} autoComplete="off" />
          </Field>
          <Field label="Имя" required error={shown.first_name}>
            <input className="input" value={form.first_name} onChange={set('first_name')} maxLength={60} autoComplete="off" />
          </Field>
          <Field label="Отчество">
            <input className="input" value={form.middle_name} onChange={set('middle_name')} maxLength={60} autoComplete="off" />
          </Field>
        </fieldset>

        <fieldset className="dform__group">
          <legend>Работа</legend>
          <Field label="Профиль" required={isNew} error={shown.profile} hint={locked ? 'Задаётся планировщиком' : undefined}>
            <select className="select" value={form.profile} onChange={set('profile')} disabled={locked}>
              {!form.profile && <option value="">Выберите</option>}
              {PROFILES.map((p) => (
                <option key={p} value={p}>
                  {PROFILE_SHORT[p]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Сервисный центр" required={isNew} error={shown.service_center_id} hint={locked ? 'Задаётся планировщиком' : undefined}>
            <select className="select" value={form.service_center_id} onChange={set('service_center_id')} disabled={locked}>
              {!form.service_center_id && <option value="">Выберите</option>}
              {serviceCenters.map((s) => (
                <option key={s.service_center_id} value={s.service_center_id}>
                  {s.name}
                </option>
              ))}
              {form.service_center_id && !scName.has(form.service_center_id) && (
                <option value={form.service_center_id}>{form.service_center_id}</option>
              )}
            </select>
          </Field>
          <Field label="Должность" hint={isNew ? 'По умолчанию — по профилю' : undefined}>
            <input className="input" value={form.position} onChange={set('position')} maxLength={80} />
          </Field>
          <Field label="Табельный номер" hint={isNew ? 'Присвоится сам, если не указать' : undefined}>
            <input className="input num" value={form.personnel_number} onChange={set('personnel_number')} maxLength={20} />
          </Field>
        </fieldset>

        <fieldset className="dform__group">
          <legend>Связь</legend>
          <Field label="Телефон" error={shown.phone}>
            <input
              className="input num"
              type="tel"
              inputMode="tel"
              placeholder="+7 (9xx) xxx-xx-xx"
              value={form.phone}
              onChange={set('phone')}
              maxLength={24}
            />
          </Field>
          <Field label="Заметка" wide>
            <textarea className="input dform__note" value={form.note} onChange={set('note')} maxLength={500} rows={3} />
          </Field>
        </fieldset>

        {hasEq && profile && (
          <EquipmentBlock
            profile={profile}
            eq={eq}
            errors={eqErrors}
            recommended={recommended}
            resetting={eqResetting}
            onChange={(next) => {
              setEq(next);
              setError(null);
            }}
            onReset={async () => {
              if (!profile.equipment_override) {
                // переопределения нет — просто вернуть поля к данным
                setEq(eqFrom(profile.equipment_start_data, profile.equipment_capacity_data));
                return;
              }
              setEqResetting(true);
              try {
                const saved = await onResetEquipment(profile.staff_id);
                setEq(eqFrom(saved.equipment_start, saved.equipment_capacity));
              } catch (err) {
                setError(err instanceof Error ? err.message : String(err));
              } finally {
                setEqResetting(false);
              }
            }}
          />
        )}
      </div>

      <div className="dform__foot">
        {error && (
          <p className="dform__error" role="alert">
            {error}
          </p>
        )}
        {!persisted && <p className="dform__hint">Сервер хранит правки только до перезапуска.</p>}
        <div className="dform__actions">
          <button type="submit" className="btn btn--m btn--primary btn--accent" disabled={saving || !dirty}>
            {saving ? 'Сохраняем…' : isNew ? 'Добавить сотрудника' : 'Сохранить'}
          </button>
          <Button size="m" variant="ghost" colorScheme="neutral" label="Отмена" onClick={onBack} />
        </div>
      </div>
    </form>
  );
}

function Field({
  label,
  required,
  error,
  hint,
  wide,
  children,
}: {
  label: string;
  required?: boolean;
  error?: string;
  hint?: string;
  wide?: boolean;
  children: ReactNode;
}) {
  return (
    <label className={`dfield${wide ? ' dfield--wide' : ''}${error ? ' dfield--error' : ''}`}>
      <span className="dfield__label">
        {label}
        {required && <span className="dfield__req" aria-hidden> *</span>}
      </span>
      {children}
      {error ? <span className="dfield__msg">{error}</span> : hint ? <span className="dfield__hint">{hint}</span> : null}
    </label>
  );
}

/* ---------------------------------------------------------------- рюкзак АО */

type EqRow = Record<Kind, string>;
interface EqForm {
  start: EqRow;
  cap: EqRow;
}
type EqErrors = Partial<Record<Kind, string>>;

const eqRow = (e: Equipment | null | undefined): EqRow => ({
  ONT: e ? String(e.ONT) : '',
  ROUTER: e ? String(e.ROUTER) : '',
  STB: e ? String(e.STB) : '',
});
const eqFrom = (start: Equipment | null | undefined, cap: Equipment | null | undefined): EqForm => ({
  start: eqRow(start),
  cap: eqRow(cap),
});
const eqSame = (a: EqForm, b: EqForm) => KINDS.every(({ k }) => a.start[k] === b.start[k] && a.cap[k] === b.cap[k]);
const eqNumbers = (row: EqRow): Equipment => ({ ONT: Number(row.ONT), ROUTER: Number(row.ROUTER), STB: Number(row.STB) });

/** Целое 0…99 из строки поля; null — пусто или не число. */
function parseCount(v: string): number | null {
  const t = v.trim();
  if (!/^\d{1,2}$/.test(t)) return null;
  const n = Number(t);
  return n <= EQ_MAX ? n : null;
}

function validateEq(eq: EqForm): EqErrors {
  const e: EqErrors = {};
  for (const { k, label } of KINDS) {
    const s = parseCount(eq.start[k]);
    const c = parseCount(eq.cap[k]);
    if (s === null || c === null) e[k] = `${label}: целое от 0 до ${EQ_MAX}`;
    else if (c < s) e[k] = `${label}: максимум меньше старта`;
  }
  return e;
}

function formatEq(e: Partial<Equipment>): string {
  return KINDS.filter(({ k }) => e[k] !== undefined)
    .map(({ k, label }) => `${label} ${e[k]}`)
    .join(' · ');
}

function recommendedFor(rec: RecommendedBackpack | null, profile: Profile | null | undefined): Partial<Equipment> | null {
  if (!rec || !profile) return null;
  const r = rec.by_profile[profile];
  return r && Object.keys(r).length ? r : null;
}

/** Сетка «Старт / Максимум» × ONT, роутер, STB; data — строка «В данных» (только в карточке). */
function EqGrid({
  eq,
  errors = {},
  placeholder,
  data,
  onChange,
}: {
  eq: EqForm;
  errors?: EqErrors;
  placeholder?: string;
  data?: EqForm;
  onChange: (row: 'start' | 'cap', k: Kind, value: string) => void;
}) {
  const rows: { row: 'start' | 'cap'; label: string }[] = [
    { row: 'start', label: 'Старт' },
    { row: 'cap', label: 'Максимум' },
  ];
  return (
    <div className="deq__grid" role="group" aria-label="Стартовое и максимальное количество АО">
      <span />
      {KINDS.map(({ k, label }) => (
        <span key={k} className="deq__col">
          {label}
        </span>
      ))}
      {rows.map(({ row, label: rowLabel }) => [
        <span key={row} className="deq__row">
          {rowLabel}
        </span>,
        ...KINDS.map(({ k, label }) => (
          <input
            key={`${row}-${k}`}
            className={`input num deq__input${errors[k] ? ' deq__input--error' : ''}`}
            inputMode="numeric"
            placeholder={placeholder}
            value={eq[row][k]}
            onChange={(e) => onChange(row, k, e.target.value)}
            maxLength={2}
            aria-label={`${label}: ${rowLabel.toLowerCase()}`}
          />
        )),
      ])}
      {data && (
        <>
          <span className="deq__row deq__row--data">В данных</span>
          {KINDS.map(({ k }) => (
            <span key={k} className="deq__data num" title="старт / максимум в staff.json">
              {data.start[k]} / {data.cap[k]}
            </span>
          ))}
        </>
      )}
    </div>
  );
}

function EquipmentBlock({
  profile,
  eq,
  errors,
  recommended,
  resetting,
  onChange,
  onReset,
}: {
  profile: StaffProfile;
  eq: EqForm;
  errors: EqErrors;
  recommended: RecommendedBackpack | null;
  resetting: boolean;
  onChange: (next: EqForm) => void;
  onReset: () => void;
}) {
  const data = eqFrom(profile.equipment_start_data, profile.equipment_capacity_data);
  const differs = !eqSame(eq, data);
  const rec = recommendedFor(recommended, profile.profile);
  const errorList = Object.values(errors);

  return (
    <fieldset className="dform__group deq">
      <legend>
        Рюкзак АО
        {profile.equipment_override && (
          <span className="deq__badge">
            <Badge size="2xs" variant="secondary" colorScheme="info" label="изменено" />
          </span>
        )}
      </legend>
      <EqGrid
        eq={eq}
        errors={errors}
        data={data}
        onChange={(row, k, value) => onChange({ ...eq, [row]: { ...eq[row], [k]: value } })}
      />
      {errorList.length > 0 && <p className="dfield__msg deq__msg">{errorList.join('; ')}</p>}
      {rec && (
        <p className="deq__rec">
          Рекомендуемый старт (p{recommended?.percentile ?? 90} расхода за смену, сценарий «Рюкзак АО»):{' '}
          <b className="num">{formatEq(rec)}</b>
        </p>
      )}
      <p className="dfield__hint deq__hint">{SYNTH_HINT} Новые значения применяются при пересборке дня.</p>
      {(profile.equipment_override || differs) && (
        <div className="deq__actions">
          <Button
            size="s"
            variant="ghost"
            colorScheme="neutral"
            label={resetting ? 'Сбрасываем…' : 'Сбросить к данным'}
            disabled={resetting}
            onClick={onReset}
          />
        </div>
      )}
    </fieldset>
  );
}

const SCOPES: { index: EquipmentScope; label: string; reset: string }[] = [
  { index: 'ALL', label: 'Всем', reset: 'Сбросить у всех' },
  { index: 'INSTALL', label: 'Инсталляторам', reset: 'Сбросить у инсталляторов' },
  { index: 'LTP3', label: 'Только ЛТП', reset: 'Сбросить у ЛТП' },
];

/** «Задать всем»: старт/максимум (пусто — не менять) для всех или одного профиля. */
function BulkEquipment({
  overridden,
  onDone,
  onCancel,
}: {
  overridden: number;
  onDone: (notice: string) => void;
  onCancel: () => void;
}) {
  const dir = useStaffDirectoryContext();
  const [scope, setScope] = useState<EquipmentScope>('ALL');
  const [eq, setEq] = useState<EqForm>(() => eqFrom(null, null));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!dir) return null;

  const pick = (row: EqRow): Partial<Equipment> | null => {
    const out: Partial<Equipment> = {};
    for (const { k } of KINDS) {
      const n = parseCount(row[k]);
      if (n !== null) out[k] = n;
    }
    return Object.keys(out).length ? out : null;
  };
  const invalid = KINDS.some(({ k }) => [eq.start[k], eq.cap[k]].some((v) => v.trim() !== '' && parseCount(v) === null));
  const start = pick(eq.start);
  const cap = pick(eq.cap);
  const crossed = KINDS.filter(({ k }) => {
    const s = start?.[k];
    const c = cap?.[k];
    return s !== undefined && c !== undefined && c < s;
  });
  const canApply = !busy && !invalid && !crossed.length && !!(start || cap);
  const rec = scope === 'ALL' ? null : recommendedFor(dir.recommended, scope);
  const scopeInfo = SCOPES.find((s) => s.index === scope) ?? SCOPES[0];

  const act = async (fn: () => Promise<string>) => {
    setBusy(true);
    setError(null);
    try {
      onDone(await fn());
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="dbulk" aria-label="Рюкзак АО для группы сотрудников">
      <div className="dbulk__head">
        <span className="dbulk__title">Рюкзак АО</span>
        <SegmentedControl
          size="s"
          value={scope}
          onChange={(v) => setScope(v as EquipmentScope)}
          items={SCOPES.map(({ index, label }) => ({ index, label }))}
        />
      </div>
      <EqGrid
        eq={eq}
        placeholder="—"
        onChange={(row, k, value) => {
          setEq((f) => ({ ...f, [row]: { ...f[row], [k]: value } }));
          setError(null);
        }}
      />
      <p className="dfield__hint deq__hint">
        Пустое поле — не менять. {SYNTH_HINT}
        {overridden > 0 && ` Сейчас изменено у ${overridden} сотр.`}
      </p>
      {rec && (
        <p className="deq__rec">
          Рекомендуемый старт (p{dir.recommended?.percentile ?? 90}): <b className="num">{formatEq(rec)}</b>{' '}
          <button
            type="button"
            className="dbulk__link"
            onClick={() =>
              setEq((f) => ({
                ...f,
                start: { ...f.start, ...Object.fromEntries(Object.entries(rec).map(([k, v]) => [k, String(v)])) },
              }))
            }
          >
            подставить
          </button>
        </p>
      )}
      {(invalid || crossed.length > 0) && (
        <p className="dfield__msg deq__msg">
          {invalid
            ? `Целые числа от 0 до ${EQ_MAX}`
            : `Максимум меньше старта: ${crossed.map((c) => c.label).join(', ')}`}
        </p>
      )}
      {error && (
        <p className="dform__error" role="alert">
          {error}
        </p>
      )}
      <div className="dbulk__actions">
        <Button
          size="s"
          label={busy ? 'Применяем…' : 'Применить'}
          disabled={!canApply}
          onClick={() =>
            void act(async () => {
              const r = await dir.bulkEquipment({ start: start ?? undefined, capacity: cap ?? undefined, profile: scope });
              return `Рюкзак АО задан: ${r.updated ?? 0} сотр. Применится при пересборке дня.`;
            })
          }
        />
        <Button
          size="s"
          variant="outline"
          colorScheme="neutral"
          label={scopeInfo.reset}
          disabled={busy}
          onClick={() =>
            void act(async () => {
              const r = await dir.bulkResetEquipment(scope);
              return r.reset ? `Рюкзак сброшен к данным: ${r.reset} сотр.` : 'Изменённых рюкзаков нет — сбрасывать нечего.';
            })
          }
        />
        <Button size="s" variant="ghost" colorScheme="neutral" label="Отмена" onClick={onCancel} />
      </div>
    </section>
  );
}
```

## File: frontend/src/components/DispatchMap.tsx
```typescript
import { useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { Route, ServiceCenter, UiOrder } from '../types/api';
import { REASON, STATUS, hhmm, window2 } from '../format';
import { SegmentedControl } from './ui';
import {
  PROFILE_SHORT,
  jointMainOf,
  locationIndex,
  routeColorValues,
  routeVar,
  stopLatLon,
  type LatLon,
  type RouteMode,
  type Selection,
  type StaffInfo,
} from '../planModel';

const EKB: L.LatLngTuple = [56.84, 60.61];

const esc = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string);

function scPopup(sc: ServiceCenter): string {
  return (
    `<div class="map-popup"><strong>${esc(sc.name)}</strong><br>${esc(sc.address)}<br>` +
    `Режим работы: ${esc(sc.opening_hours.open)}–${esc(sc.opening_hours.close)}<br>` +
    `Пополнение: ${sc.restock_duration_min} мин</div>`
  );
}

/** Категория маркера: риск опоздания выделяется отдельно от «в маршруте». */
type Cat = 'LATE' | 'ROUTED' | 'UNASSIGNED' | 'PGP' | 'CANCELLED';
const CATS: { key: Cat; label: string }[] = [
  { key: 'LATE', label: 'Риск опоздания' },
  { key: 'ROUTED', label: 'В маршруте' },
  { key: 'UNASSIGNED', label: 'Не распределён' },
  { key: 'PGP', label: 'ПГП' },
  { key: 'CANCELLED', label: 'Отменён' },
];
/** Порядок отрисовки на canvas: последние — сверху. */
const DRAW_ORDER: Cat[] = ['CANCELLED', 'UNASSIGNED', 'ROUTED', 'PGP', 'LATE'];

const catOf = (o: UiOrder): Cat => {
  if (o.status === 'ROUTED') return o.late_risk ? 'LATE' : 'ROUTED';
  if (o.status === 'PGP') return 'PGP';
  if (o.status === 'CANCELLED') return 'CANCELLED';
  return 'UNASSIGNED';
};

/** Canvas не понимает var(--…), поэтому значения токенов читаем из CSS. */
function palette() {
  const cs = getComputedStyle(document.documentElement);
  const v = (n: string) => cs.getPropertyValue(n).trim();
  return {
    accent: v('--atmr-accent'),
    halo: v('--atmr-bg-surface1'),
    neutral: v('--atmr-status-neutral'),
    pgp: v('--atmr-map-pgp'),
    late: v('--atmr-map-late'),
    cancelled: v('--atmr-border-strong'),
    ink: v('--atmr-text-primary'),
  };
}
type Palette = ReturnType<typeof palette>;

function style(cat: Cat, p: Palette): L.CircleMarkerOptions {
  switch (cat) {
    case 'LATE':
      return { radius: 7, color: p.halo, weight: 2, fillColor: p.late, fillOpacity: 1 };
    case 'ROUTED':
      return { radius: 5, color: p.halo, weight: 1.5, fillColor: p.accent, fillOpacity: 1 };
    case 'UNASSIGNED':
      return { radius: 5, color: p.neutral, weight: 2, fillColor: p.halo, fillOpacity: 1 };
    case 'PGP':
      return { radius: 5.5, color: p.halo, weight: 1.5, fillColor: p.pgp, fillOpacity: 1 };
    case 'CANCELLED':
      return { radius: 4, color: p.cancelled, weight: 1, opacity: 0.7, fillColor: p.cancelled, fillOpacity: 0.35 };
  }
}

function orderPopup(o: UiOrder, cat: Cat): string {
  const rows: string[] = [
    `<strong>${esc(o.order_id)}</strong> · ${esc(STATUS[o.status].label)}`,
    esc(o.work_type_name),
    esc(o.address),
    `Слот: ${window2(o.slot)}`,
  ];
  if (o.status === 'ROUTED') {
    rows.push(`Исполнитель: ${esc(o.staff_name ?? o.staff_id ?? '—')}`);
    if (o.partner_staff_id) rows.push(`Совместный наряд, напарник: ${esc(o.partner_staff_name ?? o.partner_staff_id)}`);
    rows.push(`План: прибытие ${hhmm(o.planned_arrival)}, работа ${hhmm(o.planned_start)}–${hhmm(o.planned_end)}`);
  }
  if (cat === 'LATE' && o.late_risk) {
    const m = o.late_risk.lateness_min;
    rows.push(`<span class="map-popup__warn">Риск опоздания${m != null ? `: +${Math.round(m)} мин` : ''}</span>`);
  }
  if (o.status === 'UNASSIGNED' || o.status === 'UNKNOWN') {
    const r = o.reason_text ?? (o.reason_code ? REASON[o.reason_code] : null);
    if (r) rows.push(`Причина: ${esc(r)}`);
  }
  if (o.status === 'PGP' && o.group_id) rows.push(`Группа ПГП: ${esc(o.group_id)}`);
  if (o.status === 'CANCELLED' && o.cancelled_at) rows.push(`Отменён в ${hhmm(o.cancelled_at)}`);
  return `<div class="map-popup">${rows.join('<br>')}</div>`;
}

/* Иконки остановок маршрута (inline SVG, цвет — currentColor). */
const ICON_RESTOCK =
  '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 1.5 14 4.5v7L8 14.5 2 11.5v-7z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M2.4 4.7 8 7.5l5.6-2.8M8 7.5v6.6" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>';
const ICON_BREAK =
  '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M2.5 6h9v3.5a4 4 0 0 1-4 4h-1a4 4 0 0 1-4-4z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M11.5 7.2h.8a1.8 1.8 0 0 1 0 3.6h-1M5.5 1.8v2M8.5 1.8v2" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>';

/** Угол сегмента на экране (Меркатор конформный — угол не зависит от зума). */
function bearingDeg(a: LatLon, b: LatLon): number {
  const pa = L.CRS.EPSG3857.latLngToPoint(L.latLng(a), 0);
  const pb = L.CRS.EPSG3857.latLngToPoint(L.latLng(b), 0);
  return (Math.atan2(pb.y - pa.y, pb.x - pa.x) * 180) / Math.PI;
}

/**
 * Состояние карты, которое переживает перемонтирование панели (закрыли/открыли окно,
 * сброс раскладки). Живёт в App, карта читает его при создании и обновляет сама.
 */
export interface MapMemory {
  center: L.LatLngTuple | null;
  zoom: number | null;
  /** Для какого дня и справочника уже сделан fitBounds (сравнение по ссылке). */
  fittedDay: string | undefined;
  fittedSc: ServiceCenter[] | null;
  /** Последние обработанные выборы: на перемонтировании старый выбор не центрирует карту повторно. */
  orderNonce: number;
  staffNonce: number;
  hidden: string[];
}
export const createMapMemory = (): MapMemory => ({
  center: null,
  zoom: null,
  fittedDay: undefined,
  fittedSc: null,
  orderNonce: 0,
  staffNonce: 0,
  hidden: [],
});

interface Props {
  memory: MapMemory;
  serviceCenters: ServiceCenter[];
  orders: UiOrder[];
  routes: Route[];
  staff: Map<string, StaffInfo>;
  /** Дата дня: смена даты или справочника = первая загрузка дня → fitBounds. */
  day?: string;
  staffSel: Selection | null;
  orderSel: Selection | null;
  routeMode: RouteMode;
  onRouteMode: (m: RouteMode) => void;
  onSelectStaff: (id: string | null, fit: boolean) => void;
  onSelectOrder: (id: string) => void;
}

export function DispatchMap({
  memory,
  serviceCenters,
  orders,
  routes,
  staff,
  day,
  staffSel,
  orderSel,
  routeMode,
  onRouteMode,
  onSelectStaff,
  onSelectOrder,
}: Props) {
  const boxRef = useRef<HTMLDivElement>(null);
  const legendRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const scLayerRef = useRef<L.LayerGroup | null>(null);
  const orderLayerRef = useRef<L.LayerGroup | null>(null);
  const routeLayerRef = useRef<L.LayerGroup | null>(null);
  const selLayerRef = useRef<L.LayerGroup | null>(null);
  const rendererRef = useRef<L.Canvas | null>(null);
  const markersRef = useRef(new Map<string, L.CircleMarker>());
  const openIdRef = useRef<string | null>(null);
  const ordersRef = useRef(orders);
  ordersRef.current = orders;
  const cbRef = useRef({ onSelectStaff, onSelectOrder });
  cbRef.current = { onSelectStaff, onSelectOrder };
  const [hidden, setHidden] = useState<Set<Cat>>(() => new Set(memory.hidden as Cat[]));
  const memRef = useRef(memory);
  memRef.current = memory;
  /** Действие с видом карты, отложенное до момента, когда у контейнера появится размер
   *  (панель только что открыли или она во вкладке, которая ещё не показана). */
  const pendingViewRef = useRef<((map: L.Map) => void) | null>(null);
  const withView = (fn: (map: L.Map) => void) => {
    const map = mapRef.current;
    const box = boxRef.current;
    if (!map || !box) return;
    if (box.clientWidth > 0 && box.clientHeight > 0) {
      map.invalidateSize();
      pendingViewRef.current = null;
      fn(map);
    } else {
      pendingViewRef.current = fn;
    }
  };

  const selectedStaff = staffSel?.id ?? null;
  const ordersById = useMemo(() => new Map(orders.map((o) => [o.order_id, o])), [orders]);

  /** Точки маршрутов (СЦ → визиты → СЦ) с остановками для иконок. */
  const routeGeo = useMemo(() => {
    const loc = locationIndex(orders, serviceCenters);
    return routes.map((r) => {
      const pts: LatLon[] = [];
      const marks: { kind: 'RESTOCK' | 'BREAK'; at: LatLon; text: string }[] = [];
      const visits: { at: LatLon; n: number; id: string }[] = [];
      let n = 0;
      for (const s of r.stops) {
        const at = stopLatLon(s, r.service_center_id, loc, ordersById) ?? pts[pts.length - 1];
        if (!at) continue;
        const last = pts[pts.length - 1];
        if (!last || last[0] !== at[0] || last[1] !== at[1]) pts.push(at);
        if (s.type === 'VISIT' && s.order_id) visits.push({ at, n: ++n, id: jointMainOf(s.order_id) ?? s.order_id });
        if (s.type === 'RESTOCK') marks.push({ kind: 'RESTOCK', at, text: `Пополнение в СЦ ${hhmm(s.start)}–${hhmm(s.end)}` });
        if (s.type === 'BREAK') marks.push({ kind: 'BREAK', at, text: `Перерыв ${hhmm(s.start)}–${hhmm(s.end)}` });
      }
      return { route: r, pts, marks, visits };
    });
  }, [routes, orders, serviceCenters, ordersById]);

  useEffect(() => {
    const box = boxRef.current;
    if (!box) return;
    const mem = memRef.current;
    const map = L.map(box).setView(mem.center ?? EKB, mem.zoom ?? 11);
    map.attributionControl.setPrefix(false);
    map.on('moveend zoomend', () => {
      if (!box.clientWidth || !box.clientHeight) return;
      const c = map.getCenter();
      memRef.current.center = [c.lat, c.lng];
      memRef.current.zoom = map.getZoom();
    });
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(map);
    mapRef.current = map;
    rendererRef.current = L.canvas({ padding: 0.5 });
    // Маршруты и наряды в одном canvas: линии уводим назад bringToBack, точки остаются сверху.
    routeLayerRef.current = L.layerGroup().addTo(map);
    orderLayerRef.current = L.layerGroup().addTo(map);
    selLayerRef.current = L.layerGroup().addTo(map);
    scLayerRef.current = L.layerGroup().addTo(map);
    // Панель меняет размер при перетаскивании границ, докинге и откреплении — подстраиваем карту.
    const ro = new ResizeObserver(() => {
      if (!box.clientWidth || !box.clientHeight) return;
      map.invalidateSize();
      const pending = pendingViewRef.current;
      if (pending) {
        pendingViewRef.current = null;
        pending(map);
      }
    });
    ro.observe(box);
    return () => {
      ro.disconnect();
      pendingViewRef.current = null;
      map.remove();
      mapRef.current = null;
      scLayerRef.current = null;
      orderLayerRef.current = null;
      routeLayerRef.current = null;
      selLayerRef.current = null;
      rendererRef.current = null;
    };
  }, []);

  useEffect(() => {
    const layer = scLayerRef.current;
    if (!layer) return;
    layer.clearLayers();
    for (const sc of serviceCenters) {
      const icon = L.divIcon({
        className: 'map-sc',
        html: '<span class="map-sc__dot"></span>',
        iconSize: [22, 22],
        iconAnchor: [11, 11],
      });
      L.marker([sc.lat, sc.lon], { icon, title: sc.name })
        .bindTooltip(sc.name, { direction: 'top', offset: [0, -10] })
        .bindPopup(scPopup(sc))
        .addTo(layer);
    }
  }, [serviceCenters]);

  // Наряды: перерисовка слоя на каждую версию плана, вид карты не трогаем.
  useEffect(() => {
    const layer = orderLayerRef.current;
    const renderer = rendererRef.current;
    if (!layer || !renderer) return;
    const reopen = openIdRef.current;
    layer.clearLayers();
    markersRef.current.clear();
    const p = palette();
    const byCat = new Map<Cat, UiOrder[]>();
    for (const o of orders) {
      const c = catOf(o);
      if (hidden.has(c)) continue;
      (byCat.get(c) ?? byCat.set(c, []).get(c)!).push(o);
    }
    for (const cat of DRAW_ORDER) {
      for (const o of byCat.get(cat) ?? []) {
        const ll: L.LatLngTuple = [o.lat, o.lon];
        if (cat === 'LATE') {
          L.circleMarker(ll, { renderer, radius: 11, color: p.late, weight: 2, opacity: 0.55, fill: false, interactive: false }).addTo(layer);
        }
        const m = L.circleMarker(ll, { renderer, ...style(cat, p) })
          .bindPopup(() => orderPopup(o, cat), { maxWidth: 280 })
          .on('click', () => cbRef.current.onSelectOrder(o.order_id))
          .on('popupopen', () => (openIdRef.current = o.order_id))
          .on('popupclose', () => {
            if (openIdRef.current === o.order_id) openIdRef.current = null;
          })
          .addTo(layer);
        markersRef.current.set(o.order_id, m);
        if (o.order_id === reopen) m.openPopup();
      }
    }
  }, [orders, hidden]);

  // Маршруты: линия на сотрудника, выбранный — поверх, с нумерацией визитов и стрелками.
  useEffect(() => {
    const layer = routeLayerRef.current;
    const renderer = rendererRef.current;
    if (!layer || !renderer) return;
    layer.clearLayers();
    if (routeMode === 'none') return;
    const colors = routeColorValues();
    const p = palette();
    const visible = routeGeo.filter((g) => routeMode === 'all' || g.route.staff_id === selectedStaff);
    const isFocus = (id: string) => !selectedStaff || id === selectedStaff;
    // bringToBack: первым уводим назад выбранный, затем остальные — они окажутся под ним.
    const ordered = [...visible].sort((a, b) => Number(isFocus(b.route.staff_id)) - Number(isFocus(a.route.staff_id)));
    for (const g of ordered) {
      if (g.pts.length < 2) continue;
      const id = g.route.staff_id;
      const info = staff.get(id);
      const color = colors[(info?.color ?? 0) % colors.length] || p.accent;
      const solo = id === selectedStaff;
      const focus = isFocus(id);
      const off = g.route.status === 'UNAVAILABLE';
      const line = L.polyline(g.pts, {
        renderer,
        color,
        weight: solo ? 4 : focus ? 2.5 : 2,
        opacity: solo ? 0.95 : focus ? 0.7 : 0.14,
        dashArray: off ? '6 6' : undefined,
        lineJoin: 'round',
      })
        .bindTooltip(
          `${esc(info?.name ?? id)}${info?.profiles.length ? ` · ${info.profiles.map((x) => PROFILE_SHORT[x]).join(', ')}` : ''}` +
            `<br>Визитов: ${g.visits.length}${off ? ' · недоступен' : ''}`,
          { sticky: true },
        )
        .on('click', () => cbRef.current.onSelectStaff(id, false))
        .addTo(layer);
      line.bringToBack();
      if (solo) {
        L.polyline(g.pts, { renderer, color: p.halo, weight: 8, opacity: 0.9, interactive: false }).addTo(layer).bringToBack();
        for (let i = 1; i < g.pts.length; i++) {
          const a = g.pts[i - 1];
          const b = g.pts[i];
          const mid: LatLon = [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2];
          L.marker(mid, {
            interactive: false,
            keyboard: false,
            icon: L.divIcon({
              className: 'map-arrow',
              html: `<span style="transform:rotate(${bearingDeg(a, b).toFixed(1)}deg);color:${color}"></span>`,
              iconSize: [14, 14],
              iconAnchor: [7, 7],
            }),
          }).addTo(layer);
        }
        for (const v of g.visits) {
          L.marker(v.at, {
            interactive: false,
            keyboard: false,
            zIndexOffset: 200,
            icon: L.divIcon({
              className: 'map-num',
              html: `<span style="background:${color}">${v.n}</span>`,
              iconSize: [18, 18],
              iconAnchor: [-3, 21],
            }),
          }).addTo(layer);
        }
      }
      if (focus) {
        for (const mk of g.marks) {
          L.marker(mk.at, {
            keyboard: false,
            zIndexOffset: 300,
            icon: L.divIcon({
              className: `map-stop map-stop--${mk.kind.toLowerCase()}`,
              html: `<span style="color:${color}">${mk.kind === 'RESTOCK' ? ICON_RESTOCK : ICON_BREAK}</span>`,
              iconSize: [20, 20],
              iconAnchor: mk.kind === 'RESTOCK' ? [24, 22] : [22, -2],
            }),
          })
            .bindTooltip(`${esc(info?.name ?? id)}<br>${mk.text}`, { direction: 'top' })
            .on('click', () => cbRef.current.onSelectStaff(id, false))
            .addTo(layer);
        }
      }
    }
  }, [routeGeo, routeMode, selectedStaff, staff]);

  // Выбранный наряд: кольцо; при выборе из таблицы/Ганта — центрируем и открываем попап.
  useEffect(() => {
    const map = mapRef.current;
    const layer = selLayerRef.current;
    const renderer = rendererRef.current;
    if (!map || !layer || !renderer) return;
    layer.clearLayers();
    const o = orderSel ? ordersById.get(orderSel.id) : undefined;
    if (!o) return;
    const p = palette();
    L.circleMarker([o.lat, o.lon], { renderer, radius: 14, color: p.ink, weight: 2.5, fill: false, interactive: false })
      .addTo(layer)
      .bringToFront();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderSel?.id, ordersById]);

  // fitBounds только при первой загрузке дня (новая дата или новый справочник после «Загрузить день»).
  // Память в App помнит, для чего вписывали: перемонтирование панели масштаб не сбрасывает.
  // Эффект объявлен раньше эффектов выбора, чтобы при открытии панели выбор применился последним.
  useEffect(() => {
    const mem = memRef.current;
    if (mem.fittedDay === day && mem.fittedSc === serviceCenters) return;
    const pts: L.LatLngTuple[] = [
      ...ordersRef.current.map((o) => [o.lat, o.lon] as L.LatLngTuple),
      ...serviceCenters.map((sc) => [sc.lat, sc.lon] as L.LatLngTuple),
    ];
    if (!pts.length) return;
    mem.fittedDay = day;
    mem.fittedSc = serviceCenters;
    withView((map) => {
      const legendH = legendRef.current?.offsetHeight ?? 0;
      map.fitBounds(L.latLngBounds(pts), {
        paddingTopLeft: [32, 32],
        paddingBottomRight: [32, legendH + 24],
        maxZoom: 13,
      });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [day, serviceCenters]);

  useEffect(() => {
    const mem = memRef.current;
    if (!orderSel || orderSel.nonce === mem.orderNonce) return;
    mem.orderNonce = orderSel.nonce;
    if (orderSel.source === 'map') return;
    const o = ordersRef.current.find((x) => x.order_id === orderSel.id);
    if (!o) return;
    withView((map) => {
      map.setView([o.lat, o.lon], Math.max(map.getZoom(), 14), { animate: false });
      const m = markersRef.current.get(o.order_id);
      if (m) m.openPopup();
      else L.popup({ maxWidth: 280 }).setLatLng([o.lat, o.lon]).setContent(orderPopup(o, catOf(o))).openOn(map);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderSel?.nonce]);

  // Вписываем маршрут, когда сотрудника выбрали в списке над картой или в Ганте.
  useEffect(() => {
    const mem = memRef.current;
    if (!staffSel || staffSel.nonce === mem.staffNonce) return;
    mem.staffNonce = staffSel.nonce;
    if (!staffSel.fit) return;
    const g = routeGeo.find((x) => x.route.staff_id === staffSel.id);
    if (!g || !g.pts.length) return;
    withView((map) => {
      const legendH = legendRef.current?.offsetHeight ?? 0;
      map.fitBounds(L.latLngBounds(g.pts), { paddingTopLeft: [40, 40], paddingBottomRight: [40, legendH + 32], maxZoom: 14 });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [staffSel?.nonce]);

  const counts = useMemo(() => {
    const c: Record<Cat, number> = { LATE: 0, ROUTED: 0, UNASSIGNED: 0, PGP: 0, CANCELLED: 0 };
    for (const o of orders) c[catOf(o)]++;
    return c;
  }, [orders]);

  /** Выпадающий список: сотрудники с маршрутами, сгруппированные по СЦ. */
  const groups = useMemo(() => {
    const scName = new Map(serviceCenters.map((s) => [s.service_center_id, s.name]));
    const by = new Map<string, { info: StaffInfo; visits: number; off: boolean }[]>();
    for (const r of routes) {
      const info = staff.get(r.staff_id) ?? { id: r.staff_id, name: r.staff_id, scId: r.service_center_id, profiles: [], color: 0 };
      const key = r.service_center_id;
      const visits = r.stops.filter((s) => s.type === 'VISIT').length;
      (by.get(key) ?? by.set(key, []).get(key)!).push({ info, visits, off: r.status === 'UNAVAILABLE' });
    }
    return [...by.entries()]
      .map(([sc, list]) => ({ sc, name: scName.get(sc) ?? sc, list: list.sort((a, b) => a.info.name.localeCompare(b.info.name, 'ru')) }))
      .sort((a, b) => a.name.localeCompare(b.name, 'ru'));
  }, [routes, staff, serviceCenters]);

  const toggle = (c: Cat) =>
    setHidden((h) => {
      const n = new Set(h);
      if (!n.delete(c)) n.add(c);
      memRef.current.hidden = [...n];
      return n;
    });

  const selInfo = selectedStaff ? staff.get(selectedStaff) : undefined;

  return (
    <>
      <div className="map-tools">
        <label className="map-tools__staff">
          <span
            className="map-tools__swatch"
            style={selInfo ? { background: routeVar(selInfo.color) } : undefined}
            aria-hidden
          />
          <select
            className="select"
            value={selectedStaff ?? ''}
            onChange={(e) => onSelectStaff(e.target.value || null, true)}
            aria-label="Сотрудник, маршрут которого выделить"
            disabled={!routes.length}
          >
            <option value="">Все сотрудники</option>
            {groups.map((g) => (
              <optgroup key={g.sc} label={g.name}>
                {g.list.map(({ info, visits, off }) => (
                  <option key={info.id} value={info.id}>
                    {info.name} — {visits} виз.{off ? ', недоступен' : ''}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
        </label>
        <span className="map-tools__label">Маршруты</span>
        <SegmentedControl
          size="s"
          variant="secondary"
          value={routeMode}
          onChange={(v) => onRouteMode(v as RouteMode)}
          disabledAll={!routes.length}
          items={[
            { index: 'all', label: 'Все' },
            { index: 'selected', label: 'Выбранный' },
            { index: 'none', label: 'Скрыть' },
          ]}
        />
      </div>
      <div className="dispatch-map-wrap">
        <div ref={boxRef} className="dispatch-map" />
        {routeMode === 'selected' && !selectedStaff && routes.length > 0 && (
          <p className="map-hint">Выберите сотрудника в списке, на линии маршрута или в диаграмме Ганта</p>
        )}
        {orders.length > 0 && (
          <div ref={legendRef} className="map-legend" role="group" aria-label="Наряды на карте">
            {CATS.map(({ key, label }) => (
              <button
                key={key}
                type="button"
                className="map-legend__item"
                aria-pressed={!hidden.has(key)}
                title={hidden.has(key) ? 'Показать на карте' : 'Скрыть с карты'}
                onClick={() => toggle(key)}
              >
                <span className={`map-legend__dot map-legend__dot--${key.toLowerCase()}`} />
                {label}
                <span className="map-legend__count">{counts[key]}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
```

## File: frontend/src/components/GanttChart.tsx
```typescript
import { useEffect, useLayoutEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react';
import { createPortal } from 'react-dom';
import type { Plan, Route, ServiceCenter, Stop, UiOrder } from '../types/api';
import { WORK_TYPE_SHORT, hhmm, window2 } from '../format';
import { PROFILE_SHORT, jointMainOf, jointPairs, minutesOf, minutesOfHm, routeVar, type Selection, type StaffInfo } from '../planModel';

/** Высота строки и ширина колонки с именем, px. */
const ROW = 26;
const LABEL = 184;
const OVERSCAN = 6;
const SHIFT_FROM = 9 * 60;
const SHIFT_TO = 21 * 60;

type Row =
  | { kind: 'group'; key: string; label: string; count: number }
  | { kind: 'staff'; key: string; info: StaffInfo; route?: Route };

interface Tip {
  x: number;
  y: number;
  title: string;
  lines: string[];
  warn?: string;
}

interface Props {
  plan: Plan;
  orders: UiOrder[];
  staff: Map<string, StaffInfo>;
  serviceCenters: ServiceCenter[];
  staffSel: Selection | null;
  orderSel: Selection | null;
  onSelectStaff: (id: string | null, fit: boolean) => void;
  onSelectOrder: (id: string) => void;
}

const STOP_STATUS: Record<string, string> = { PLANNED: 'запланирован', IN_PROGRESS: 'в работе', DONE: 'выполнен' };

export function GanttChart({ plan, orders, staff, serviceCenters, staffSel, orderSel, onSelectStaff, onSelectOrder }: Props) {
  const bodyRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [top, setTop] = useState(0);
  const [tip, setTip] = useState<Tip | null>(null);

  const ordersById = useMemo(() => new Map(orders.map((o) => [o.order_id, o])), [orders]);
  const pairs = useMemo(() => jointPairs(plan), [plan]);

  const rows = useMemo<Row[]>(() => {
    const routes = new Map(plan.routes.map((r) => [r.staff_id, r]));
    const scName = new Map(serviceCenters.map((s) => [s.service_center_id, s.name]));
    const ids = new Set([...staff.keys(), ...routes.keys()]);
    const by = new Map<string, Row[]>();
    for (const id of ids) {
      const route = routes.get(id);
      const info = staff.get(id) ?? { id, name: id, scId: route?.service_center_id ?? '', profiles: [], color: 0 };
      const sc = route?.service_center_id ?? info.scId;
      (by.get(sc) ?? by.set(sc, []).get(sc)!).push({ kind: 'staff', key: id, info, route });
    }
    const res: Row[] = [];
    [...by.entries()]
      .sort((a, b) => (scName.get(a[0]) ?? a[0]).localeCompare(scName.get(b[0]) ?? b[0], 'ru'))
      .forEach(([sc, list]) => {
        res.push({ kind: 'group', key: `g-${sc}`, label: scName.get(sc) ?? sc, count: list.length });
        list.sort((a, b) => (a.kind === 'staff' && b.kind === 'staff' ? a.info.name.localeCompare(b.info.name, 'ru') : 0));
        res.push(...list);
      });
    return res;
  }, [plan.routes, staff, serviceCenters]);

  // Шкала смены 09:00–21:00; расширяем до целого часа, если план выходит за неё.
  const [t0, t1] = useMemo(() => {
    let lo = SHIFT_FROM;
    let hi = SHIFT_TO;
    for (const r of plan.routes)
      for (const s of r.stops) {
        const a = minutesOf(s.arrival) - (s.travel_sec ?? 0) / 60;
        const e = minutesOf(s.end);
        if (a < lo) lo = Math.floor(a / 60) * 60;
        if (e > hi) hi = Math.ceil(e / 60) * 60;
      }
    return [Math.max(0, lo), Math.min(24 * 60, hi)];
  }, [plan.routes]);

  useLayoutEffect(() => {
    const el = bodyRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setSize({ w: el.clientWidth, h: el.clientHeight }));
    ro.observe(el);
    setSize({ w: el.clientWidth, h: el.clientHeight });
    return () => ro.disconnect();
  }, []);

  const trackW = Math.max(240, size.w - LABEL - 12);
  const span = t1 - t0;
  const x = (m: number) => ((Math.min(Math.max(m, t0), t1) - t0) / span) * trackW;
  const hours = Array.from({ length: span / 60 + 1 }, (_, i) => t0 + i * 60);
  const gridLines = (
    <g key="grid" className="g-grid" aria-hidden>
      {hours.map((h) => (
        <line key={h} x1={x(h)} x2={x(h)} y1={0} y2={ROW} />
      ))}
    </g>
  );
  const now = minutesOf(plan.as_of);
  const nowX = now >= t0 && now <= t1 ? x(now) : null;

  // Прокрутка к строке выбранного сотрудника (выбор пришёл из таблицы, списков или карты).
  const scrollToStaff = (staffId: string | null | undefined) => {
    const el = bodyRef.current;
    if (!el || !staffId) return;
    const i = rows.findIndex((r) => r.kind === 'staff' && r.info.id === staffId);
    if (i < 0) return;
    const y = i * ROW;
    if (y < el.scrollTop + ROW || y + ROW > el.scrollTop + el.clientHeight - ROW) {
      el.scrollTop = Math.max(0, y - el.clientHeight / 2 + ROW / 2);
    }
  };
  useEffect(() => {
    if (orderSel && orderSel.source !== 'gantt') scrollToStaff(ordersById.get(orderSel.id)?.staff_id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderSel?.nonce]);
  useEffect(() => {
    if (staffSel && staffSel.source !== 'gantt') scrollToStaff(staffSel.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [staffSel?.nonce]);

  const first = Math.max(0, Math.floor(top / ROW) - OVERSCAN);
  const last = Math.min(rows.length, Math.ceil((top + size.h) / ROW) + OVERSCAN);

  const showTip = (e: ReactMouseEvent, t: Omit<Tip, 'x' | 'y'>) => setTip({ ...t, x: e.clientX, y: e.clientY });
  const moveTip = (e: ReactMouseEvent) => setTip((t) => (t ? { ...t, x: e.clientX, y: e.clientY } : t));
  const hideTip = () => setTip(null);

  const selectedStaff = staffSel?.id ?? null;
  const selectedOrder = orderSel?.id ?? null;
  const visits = plan.routes.reduce((n, r) => n + r.stops.filter((s) => s.type === 'VISIT' && !jointMainOf(s.order_id)).length, 0);

  function renderStops(r: Route, info: StaffInfo) {
    const out: JSX.Element[] = [];
    const mid = 11;
    const barY = 4;
    const barH = 15;
    const shiftFrom = minutesOfHm(info.shift?.start);
    const shiftTo = minutesOfHm(info.shift?.end);
    if (!Number.isNaN(shiftFrom) && !Number.isNaN(shiftTo)) {
      out.push(<rect key="shift" className="g-shift" x={x(shiftFrom)} y={1} width={Math.max(0, x(shiftTo) - x(shiftFrom))} height={ROW - 2} />);
    }
    out.push(gridLines);
    if (r.status === 'UNAVAILABLE' || r.unavailable_from) {
      const from = minutesOf(r.unavailable_from);
      const until = minutesOf(r.unavailable_until);
      const a = Number.isNaN(from) ? t0 : from;
      const b = Number.isNaN(until) ? (Number.isNaN(shiftTo) ? t1 : shiftTo) : until;
      out.push(
        <rect
          key="off"
          className="g-off"
          x={x(a)}
          y={2}
          width={Math.max(2, x(b) - x(a))}
          height={ROW - 4}
          onMouseEnter={(e) =>
            showTip(e, {
              title: `${info.name}: недоступен`,
              lines: [`С ${hhmm(r.unavailable_from)}${r.unavailable_until ? ` до ${hhmm(r.unavailable_until)}` : ' до конца смены'}`],
            })
          }
          onMouseMove={moveTip}
          onMouseLeave={hideTip}
        />,
      );
    }
    r.stops.forEach((s: Stop) => {
      const k = `${s.seq}-${s.type}`;
      const arr = minutesOf(s.arrival);
      const st = minutesOf(s.start);
      const en = minutesOf(s.end);
      if (s.travel_sec && s.travel_sec > 0 && s.type !== 'DEPOT_START') {
        const a = arr - s.travel_sec / 60;
        out.push(
          <rect
            key={`t${k}`}
            className="g-travel"
            x={x(a)}
            y={mid - 2.5}
            width={Math.max(1, x(arr) - x(a))}
            height={5}
            onMouseEnter={(e) =>
              showTip(e, {
                title: 'Дорога',
                lines: [
                  `${Math.round(s.travel_sec! / 60)} мин${s.distance_m ? `, ${(s.distance_m / 1000).toFixed(1)} км` : ''}`,
                  `Прибытие ${hhmm(s.arrival)}`,
                ],
              })
            }
            onMouseMove={moveTip}
            onMouseLeave={hideTip}
          />,
        );
      }
      if (st - arr > 0.5 && (s.type === 'VISIT' || s.type === 'RESTOCK')) {
        out.push(<line key={`w${k}`} className="g-wait" x1={x(arr)} x2={x(st)} y1={mid} y2={mid} />);
      }
      if (s.type === 'VISIT') {
        // визит напарника совместного наряда (ORD-…-B) показывает основной наряд и выбирает его
        const partnerOf = jointMainOf(s.order_id);
        const id = partnerOf ?? s.order_id;
        const pair = id ? pairs.get(id) : undefined;
        const o = id ? ordersById.get(id) : undefined;
        if (o) {
          const sa = x(minutesOf(o.slot.start));
          const sb = x(minutesOf(o.slot.end));
          out.push(
            <path key={`s${k}`} className="g-slot" d={`M${sa} ${ROW - 5}v3H${sb}v-3`} />,
          );
        }
        const late = !!o?.late_risk;
        const status = (s.status ?? 'PLANNED').toLowerCase().replace('_', '-');
        const w = Math.max(3, x(en) - x(st));
        const cls = `g-visit g-visit--${status}${late ? ' g-visit--late' : ''}${pair ? ' g-visit--joint' : ''}${id === selectedOrder ? ' is-selected' : ''}`;
        out.push(
          <g
            key={`v${k}`}
            className={cls}
            onClick={() => id && onSelectOrder(id)}
            onMouseEnter={(e) =>
              showTip(e, {
                title: `${id ?? 'Визит'}${o ? ` · ${WORK_TYPE_SHORT[o.work_type_code]}` : ''}${pair ? ' · совместный' : ''}`,
                lines: [
                  ...(o ? [o.address.replace(/^г\. Екатеринбург,\s*/, ''), `Слот ${window2(o.slot)}`] : []),
                  ...(pair
                    ? [partnerOf ? `Напарник; основной исполнитель ${pair.main ?? '—'}` : `Напарник ${pair.partner ?? '—'} в то же время`]
                    : []),
                  `Прибытие ${hhmm(s.arrival)}, работа ${hhmm(s.start)}–${hhmm(s.end)}`,
                  `Статус: ${STOP_STATUS[s.status ?? 'PLANNED'] ?? s.status}`,
                ],
                warn: late
                  ? `Риск опоздания${o?.late_risk?.lateness_min != null ? `: +${Math.round(o.late_risk.lateness_min)} мин` : ''}`
                  : undefined,
              })
            }
            onMouseMove={moveTip}
            onMouseLeave={hideTip}
          >
            <rect x={x(st)} y={barY} width={w} height={barH} rx={3} />
            {w > 34 && s.order_id && (
              <text x={x(st) + 4} y={barY + barH / 2} dy="0.35em">
                {(id ?? s.order_id).slice(-4)}
                {pair ? ' ×2' : ''}
              </text>
            )}
          </g>,
        );
      }
      if (s.type === 'BREAK' || s.type === 'RESTOCK') {
        const isBreak = s.type === 'BREAK';
        out.push(
          <rect
            key={`b${k}`}
            className={isBreak ? 'g-break' : 'g-restock'}
            x={x(st)}
            y={barY + 2}
            width={Math.max(3, x(en) - x(st))}
            height={barH - 4}
            rx={2}
            onMouseEnter={(e) =>
              showTip(e, {
                title: isBreak ? 'Перерыв' : 'Пополнение оборудования в СЦ',
                lines: [`${hhmm(s.start)}–${hhmm(s.end)}`, ...(s.explanation ? [s.explanation] : [])],
              })
            }
            onMouseMove={moveTip}
            onMouseLeave={hideTip}
          />,
        );
      }
    });
    return out;
  }

  return (
    <section className="panel gantt" aria-label="Диаграмма загрузки сотрудников">
      <svg width="0" height="0" className="gantt__defs" aria-hidden>
        <defs>
          <pattern id="g-hatch-road" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <rect width="5" height="5" className="g-hatch-road-bg" />
            <line x1="0" y1="0" x2="0" y2="5" className="g-hatch-road-line" />
          </pattern>
          <pattern id="g-hatch-off" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(-45)">
            <rect width="6" height="6" className="g-hatch-off-bg" />
            <line x1="0" y1="0" x2="0" y2="6" className="g-hatch-off-line" />
          </pattern>
        </defs>
      </svg>

      <div className="gantt__head">
        <span className="gantt__meta num">
          {plan.routes.length} маршрутов, {visits} визитов
        </span>
        <ul className="gantt__legend" aria-label="Обозначения">
          <li><i className="lg lg--visit" />Визит</li>
          <li><i className="lg lg--progress" />В работе</li>
          <li><i className="lg lg--done" />Выполнен</li>
          <li><i className="lg lg--late" />Риск опоздания</li>
          <li><i className="lg lg--break" />Перерыв</li>
          <li><i className="lg lg--restock" />Пополнение в СЦ</li>
          <li><i className="lg lg--road" />Дорога</li>
          <li><i className="lg lg--slot" />Слот</li>
          <li><i className="lg lg--off" />Недоступен</li>
        </ul>
      </div>

      <div className="gantt__axis" aria-hidden>
        <div className="gantt__axis-label" style={{ width: LABEL }}>
          Сотрудник
        </div>
        <svg width={trackW} height={22} className="gantt__axis-svg">
          {hours.map((h) => (
            <g key={h} transform={`translate(${x(h)},0)`}>
              <line y1={16} y2={22} className="g-tick" />
              <text y={11} textAnchor={h === t0 ? 'start' : h === t1 ? 'end' : 'middle'}>
                {String(Math.floor(h / 60)).padStart(2, '0')}:00
              </text>
            </g>
          ))}
          {nowX != null && (
            <g transform={`translate(${nowX},0)`} className="g-now-label">
              <rect x={-21} y={0} width={42} height={15} rx={3} />
              <text y={11} textAnchor="middle">
                {hhmm(plan.as_of)}
              </text>
            </g>
          )}
        </svg>
      </div>

      <div ref={bodyRef} className="gantt__body" onScroll={(e) => setTop(e.currentTarget.scrollTop)} onMouseLeave={hideTip}>
        {!rows.length && <p className="empty">В плане нет сотрудников.</p>}
        <div className="gantt__canvas" style={{ height: rows.length * ROW }}>
          {rows.slice(first, last).map((row, j) => {
            const i = first + j;
            if (row.kind === 'group') {
              return (
                <div key={row.key} className="gantt__group" style={{ top: i * ROW, height: ROW }}>
                  {row.label}
                  <span className="num">{row.count}</span>
                </div>
              );
            }
            const { info, route } = row;
            const sel = info.id === selectedStaff;
            const off = route?.status === 'UNAVAILABLE';
            return (
              <div
                key={row.key}
                className={`gantt__row${sel ? ' is-selected' : ''}`}
                style={{ top: i * ROW, height: ROW }}
              >
                <button
                  type="button"
                  className="gantt__who"
                  style={{ width: LABEL }}
                  aria-pressed={sel}
                  title={sel ? 'Снять выбор маршрута' : 'Показать маршрут на карте'}
                  onClick={() => onSelectStaff(sel ? null : info.id, !sel)}
                >
                  <span className="gantt__swatch" style={{ background: routeVar(info.color) }} />
                  <span className="gantt__name">{info.name}</span>
                  <span className="gantt__profile">
                    {off ? 'недоступен' : info.profiles.map((p) => PROFILE_SHORT[p]).join(', ')}
                  </span>
                </button>
                <svg width={trackW} height={ROW} className="gantt__track">
                  {route ? renderStops(route, info) : gridLines}
                </svg>
              </div>
            );
          })}
          {nowX != null && <div className="gantt__now" style={{ left: LABEL + nowX }} aria-hidden />}
        </div>
      </div>

      {/* Портал в body: контейнер панели dockview создаёт свой контекст (transform/contain),
          внутри него position: fixed считался бы от панели и обрезался. */}
      {tip &&
        createPortal(
        <div
          className="gantt-tip"
          role="tooltip"
          style={{
            left: Math.min(tip.x + 14, window.innerWidth - 300),
            top: tip.y > window.innerHeight - 170 ? tip.y - 12 : tip.y + 16,
            transform: tip.y > window.innerHeight - 170 ? 'translateY(-100%)' : undefined,
          }}
        >
          <strong>{tip.title}</strong>
          {tip.lines.map((l, i) => (
            <span key={i}>{l}</span>
          ))}
          {tip.warn && <span className="gantt-tip__warn">{tip.warn}</span>}
        </div>,
          document.body,
        )}
    </section>
  );
}
```

## File: frontend/src/components/KpiStrip.tsx
```typescript
import { duration } from '../format';
import type { Plan, UiOrder } from '../types/api';

export function KpiStrip({ plan, orders }: { plan: Plan | null; orders: UiOrder[] }) {
  const count = (s: UiOrder['status']) => orders.filter((o) => o.status === s).length;
  const late = plan?.alerts.filter((a) => a.type === 'LATE_RISK' && a.status === 'ACTIVE').length ?? 0;
  const travel =
    plan?.summary?.travel_sec_total ?? plan?.routes.reduce((acc, r) => acc + (r.totals?.travel_sec ?? 0), 0);
  const staffOnShift = plan?.routes.filter((r) => r.status === 'ACTIVE').length ?? 0;
  const v = (n: number) => (plan ? n : '—');

  const cells: { label: string; value: string | number; tone?: 'ok' | 'bad' | 'warn' | 'pgp'; hint?: string }[] = [
    { label: 'Нарядов всего', value: v(orders.length), hint: `Сотрудников на смене: ${staffOnShift}` },
    { label: 'В маршрутах', value: v(count('ROUTED')), tone: 'ok' },
    { label: 'Не распределено', value: v(count('UNASSIGNED')), tone: count('UNASSIGNED') ? 'bad' : undefined },
    { label: 'ПГП', value: v(count('PGP')), tone: count('PGP') ? 'pgp' : undefined },
    { label: 'Отменено', value: v(count('CANCELLED')) },
    { label: 'Риск опоздания', value: v(late), tone: late ? 'warn' : undefined },
    { label: 'Время в пути', value: plan ? duration(travel) : '—', hint: 'Сумма по всем маршрутам' },
  ];

  return (
    <section className="kpi" aria-label="Показатели плана">
      {cells.map((c) => (
        <div key={c.label} className={`kpi__cell${c.tone ? ` kpi__cell--${c.tone}` : ''}`} title={c.hint}>
          <span className="kpi__value num">{c.value}</span>
          <span className="kpi__label">{c.label}</span>
        </div>
      ))}
    </section>
  );
}
```

## File: frontend/src/components/LateRiskList.tsx
```typescript
import { useRef } from 'react';
import { Badge } from './ui';
import { ACTION, hhmm, windowWithDay } from '../format';
import type { Plan, UiOrder } from '../types/api';
import type { Selection } from '../planModel';
import { useScrollToSelected } from '../hooks/useScrollToSelected';
import { useStaffName } from '../hooks/useStaffDirectory';

interface Props {
  plan: Plan;
  orders: UiOrder[];
  selected: Selection | null;
  onSelect: (id: string) => void;
}

export function LateRiskList({ plan, orders, selected, onSelect }: Props) {
  const boxRef = useRef<HTMLDivElement>(null);
  const staffName = useStaffName();
  useScrollToSelected(boxRef, selected, 'late');
  const byId = new Map(orders.map((o) => [o.order_id, o]));
  const all = plan.alerts.filter((a) => a.type === 'LATE_RISK');
  const active = all
    .filter((a) => a.status === 'ACTIVE')
    .sort((a, b) => (b.lateness_min ?? 0) - (a.lateness_min ?? 0));
  const resolved = all.length - active.length;

  return (
    <div className="list">
      <div className="toolbar toolbar--note">
        Активных: <b className="num">{active.length}</b>, закрыто за день: <span className="num">{resolved}</span>
      </div>
      <div className="scroll" ref={boxRef}>
        {!active.length && (
          <p className="empty">Опозданий не прогнозируется. Риски появятся после событий: задержек визитов и выбытия сотрудников.</p>
        )}
        <ul className="cards">
          {active.map((a) => {
            const o = a.order_id ? byId.get(a.order_id) : undefined;
            return (
              <li
                key={a.alert_id}
                data-order={a.order_id ?? undefined}
                className={`card card--warn card--select${a.order_id && selected?.id === a.order_id ? ' is-selected' : ''}`}
                onClick={() => a.order_id && onSelect(a.order_id)}
              >
                <div className="card__head">
                  <span className="mono-id">{a.order_id}</span>
                  <span className="late num">+{a.lateness_min ?? '?'} мин</span>
                </div>
                <div className="card__grid">
                  <span>Исполнитель</span>
                  <b title={a.staff_id ?? undefined}>{staffName(a.staff_id ?? o?.staff_id, o?.staff_name)}</b>
                  <span>Прогноз начала</span>
                  <b className="num">{hhmm(a.forecast_start)}</b>
                  <span>Начать не позже</span>
                  <b className="num">{hhmm(o?.latest_start)}</b>
                  <span>Слот</span>
                  <b className="num">{o ? windowWithDay(o.slot, plan.date) : '—'}</b>
                </div>
                <div className="card__action">
                  <Badge variant="secondary" size="s" colorScheme="warning" label={ACTION[a.suggested_action]} />
                  {a.suggested_action === 'REASSIGN' && a.suggested_staff_id && <span title={a.suggested_staff_id}>→ {staffName(a.suggested_staff_id)}</span>}
                  {a.suggested_action === 'RESCHEDULE' && a.proposed_slot && (
                    <span className="num">на {windowWithDay(a.proposed_slot, plan.date)}</span>
                  )}
                  {a.applied && <Badge variant="secondary" size="2xs" colorScheme="success" label="применено" />}
                </div>
                <p className="card__text">{a.message}</p>
                <p className="card__meta num">Выявлено в {hhmm(a.created_at)}</p>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
```

## File: frontend/src/components/OrdersTable.tsx
```typescript
import { useEffect, useMemo, useRef, useState } from 'react';
import { Badge, SegmentedControl } from './ui';
import { STATUS, WORK_TYPE_SHORT, hhmm, window2 } from '../format';
import type { UiOrder, UiOrderStatus, WorkTypeCode } from '../types/api';
import type { Selection } from '../planModel';
import { useScrollToSelected } from '../hooks/useScrollToSelected';

type Filter = 'ALL' | UiOrderStatus;

interface Props {
  orders: UiOrder[];
  selected: Selection | null;
  onSelect: (id: string) => void;
}

export function OrdersTable({ orders, selected, onSelect }: Props) {
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<Filter>('ALL');
  const [type, setType] = useState<'ALL' | WorkTypeCode>('ALL');
  const boxRef = useRef<HTMLDivElement>(null);

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return orders
      .filter((o) => status === 'ALL' || o.status === status)
      .filter((o) => type === 'ALL' || o.work_type_code === type)
      .filter(
        (o) =>
          !q ||
          o.order_id.toLowerCase().includes(q) ||
          o.address.toLowerCase().includes(q) ||
          (o.staff_id ?? '').toLowerCase().includes(q) ||
          (o.staff_name ?? '').toLowerCase().includes(q),
      )
      .sort((a, b) => a.slot.start.localeCompare(b.slot.start) || a.order_id.localeCompare(b.order_id));
  }, [orders, query, status, type]);

  // Наряд выбран на карте или в Ганте, но скрыт фильтром — сбрасываем фильтры, чтобы показать строку.
  useEffect(() => {
    if (!selected || selected.source === 'table') return;
    if (!rows.some((o) => o.order_id === selected.id) && orders.some((o) => o.order_id === selected.id)) {
      setQuery('');
      setStatus('ALL');
      setType('ALL');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.nonce]);
  useScrollToSelected(boxRef, selected, 'table');

  const n = (s: UiOrderStatus) => orders.filter((o) => o.status === s).length;

  return (
    <div className="list">
      <div className="toolbar">
        <input
          className="input"
          type="search"
          placeholder="Наряд, адрес или исполнитель"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Поиск нарядов"
        />
        <SegmentedControl
          size="s"
          value={status}
          onChange={(v) => setStatus(v as Filter)}
          items={[
            { index: 'ALL', label: `Все ${orders.length}` },
            { index: 'ROUTED', label: `В маршруте ${n('ROUTED')}` },
            { index: 'UNASSIGNED', label: `Не распр. ${n('UNASSIGNED')}` },
            { index: 'PGP', label: `ПГП ${n('PGP')}` },
            { index: 'CANCELLED', label: `Отменены ${n('CANCELLED')}` },
          ]}
        />
        <select
          className="select"
          value={type}
          onChange={(e) => setType(e.target.value as 'ALL' | WorkTypeCode)}
          aria-label="Тип работ"
        >
          <option value="ALL">Все типы работ</option>
          {(Object.keys(WORK_TYPE_SHORT) as WorkTypeCode[]).map((c) => (
            <option key={c} value={c}>
              {WORK_TYPE_SHORT[c]}
            </option>
          ))}
        </select>
      </div>

      <div className="scroll" ref={boxRef}>
        <table className="table table--select">
          <thead>
            <tr>
              <th>Наряд</th>
              <th>Тип работ</th>
              <th>Адрес</th>
              <th className="num">Слот</th>
              <th>Статус</th>
              <th>Исполнитель</th>
              <th className="num">Плановое время</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((o) => {
              const cls = [o.status === 'CANCELLED' && 'is-muted', selected?.id === o.order_id && 'is-selected']
                .filter(Boolean)
                .join(' ');
              return (
                <tr
                  key={o.order_id}
                  data-order={o.order_id}
                  className={cls || undefined}
                  aria-selected={selected?.id === o.order_id}
                  onClick={() => onSelect(o.order_id)}
                >
                  <td className="mono-id">
                    {o.order_id.slice(-4)}
                    {o.is_repeat && (
                      <span className="flag" title="Повторное обращение, вес 2">
                        повт.
                      </span>
                    )}
                  </td>
                  <td title={o.work_type_name}>
                    {WORK_TYPE_SHORT[o.work_type_code]}
                    <span className="sub">{o.technology}</span>
                  </td>
                  <td className="addr" title={o.address}>
                    {o.address.replace(/^г\. Екатеринбург,\s*/, '')}
                  </td>
                  <td className="num">{window2(o.slot)}</td>
                  <td>
                    <Badge variant="secondary" size="2xs" colorScheme={STATUS[o.status].scheme} label={STATUS[o.status].label} />
                    {o.partner_staff_id && (
                      <span className="flag" title={`Совместный наряд: напарник ${o.partner_staff_name ?? o.partner_staff_id} (${o.partner_staff_id}) в то же время`}>
                        <Badge variant="secondary" size="2xs" colorScheme="status-01" label="совместный" />
                      </span>
                    )}
                    {o.late_risk && (
                      <span className="flag flag--warn" title="Активный риск опоздания">
                        +{o.late_risk.lateness_min ?? '?'} мин
                      </span>
                    )}
                  </td>
                  <td title={o.explanation ?? o.reason_text ?? undefined}>
                    {o.staff_id ? (
                      <>
                        {o.staff_name ?? o.staff_id}
                        {o.staff_name && <span className="sub">{o.staff_id}</span>}
                        {o.partner_staff_id && <span className="sub">+ напарник {o.partner_staff_id}</span>}
                      </>
                    ) : (
                      (o.group_id ?? '—')
                    )}
                  </td>
                  <td className="num">
                    {o.planned_start ? (
                      <>
                        {hhmm(o.planned_start)}–{hhmm(o.planned_end)}
                        <span className="sub">прибытие {hhmm(o.planned_arrival)}</span>
                      </>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {!rows.length && <p className="empty">Нарядов по этим условиям нет. Сбросьте поиск или фильтр.</p>}
      </div>
    </div>
  );
}
```

## File: frontend/src/components/scenarios.css
```css
/* Панель «Сценарии». Значения — через токены theme/tokens.css. */

.sc {
  padding: var(--atmr-spacing-3x) var(--atmr-spacing-4x) var(--atmr-spacing-4x);
  display: grid;
  align-content: start;
  gap: var(--atmr-spacing-4x);
}
.sc-note,
.sc-hint,
.sc-foot,
.sc-empty {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
}
.sc-hint {
  flex: 1 1 220px;
  max-width: 52ch;
}
.sc-foot {
  color: var(--atmr-text-tertiary);
}

/* ---- секция */
.sc-sec {
  display: grid;
  gap: var(--atmr-spacing-3x);
  padding-top: var(--atmr-spacing-3x);
  border-top: var(--atmr-border-width-s) solid var(--atmr-border-default);
}
.sc-sec__head {
  display: grid;
  gap: 2px;
}
.sc-sec__head h3 {
  font: var(--atmr-font-heading-h5);
}
.sc-sec__q {
  font: var(--atmr-font-body-s);
  color: var(--atmr-text-secondary);
}

/* ---- управление */
.sc-controls {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: var(--atmr-spacing-3x);
}
.sc-field {
  display: grid;
  gap: var(--atmr-spacing-1x);
}
.sc-field .select {
  min-width: 200px;
  max-width: 260px;
}
.sc-label {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
}
.sc-stepper {
  display: inline-flex;
  height: 32px;
  border: var(--atmr-border-width-s) solid var(--atmr-border-default);
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-bg-surface1);
  overflow: hidden;
}
.sc-stepper button {
  width: 30px;
  border: 0;
  background: var(--atmr-bg-surface2);
  color: var(--atmr-accent);
  font: 600 16px/1 var(--atmr-font-family);
  cursor: pointer;
}
.sc-stepper button:hover:not(:disabled) {
  background: var(--atmr-accent-muted);
}
.sc-stepper button:disabled {
  color: var(--atmr-text-tertiary);
  cursor: default;
}
.sc-stepper input {
  width: 40px;
  border: 0;
  border-inline: var(--atmr-border-width-s) solid var(--atmr-border-default);
  text-align: center;
  font-weight: 600;
  -moz-appearance: textfield;
  appearance: textfield;
}
.sc-stepper input::-webkit-inner-spin-button,
.sc-stepper input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

/* ---- индикатор расчёта */
.sc-progress {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-3x);
}
.sc-progress__bar {
  flex: 1 1 auto;
  height: 4px;
  border-radius: var(--atmr-border-radius-full);
  background: var(--atmr-bg-surface3);
  overflow: hidden;
}
.sc-progress__bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--atmr-accent);
  transition: width 0.2s linear;
}
.sc-progress__text {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
  white-space: nowrap;
}
.sc-error {
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-status-error-muted);
  color: var(--atmr-status-error);
  font: var(--atmr-font-body-s);
}

/* ---- результат */
.sc-result {
  display: grid;
  gap: var(--atmr-spacing-2x);
  transition: opacity 0.15s;
}
.sc-result.is-stale {
  opacity: 0.45;
}
.sc-meta {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--atmr-spacing-2x);
  font: var(--atmr-font-body-s);
  color: var(--atmr-text-secondary);
}
.sc-meta b {
  color: var(--atmr-text-primary);
  font-weight: 600;
}
.sc-removed li {
  display: inline-flex;
  align-items: baseline;
  gap: var(--atmr-spacing-1x);
  background: var(--atmr-status-neutral-muted);
}
.sc-removed .sub {
  display: inline;
}
.sc-h4 {
  margin: var(--atmr-spacing-2x) 0 0;
  font: 600 13px/18px var(--atmr-font-family);
}
.sc-h4 .num {
  color: var(--atmr-text-tertiary);
  font-weight: 500;
}

/* ---- таблицы «база → сценарий» */
.sc-table {
  width: 100%;
  max-width: 560px;
  border-collapse: collapse;
  font: var(--atmr-font-body-s);
}
.sc-table th {
  padding: 4px var(--atmr-spacing-2x);
  font: 500 11px/14px var(--atmr-font-family);
  color: var(--atmr-text-tertiary);
  text-align: left;
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
}
.sc-table td {
  padding: 4px var(--atmr-spacing-2x);
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-bg-surface3);
}
.sc-table th:first-child,
.sc-table td:first-child {
  padding-left: 0;
}
.sc-num {
  text-align: right !important;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.sc-strong {
  font-weight: 600;
}
.sc-muted {
  color: var(--atmr-text-tertiary);
}
.sc-sub td:first-child {
  padding-left: var(--atmr-spacing-4x);
  color: var(--atmr-text-secondary);
}
.sc-sub td {
  font: var(--atmr-font-description-m);
}
.sc-d {
  font-weight: 600;
}
.sc-d--good {
  color: var(--atmr-status-success);
}
.sc-d--bad {
  color: var(--atmr-status-error);
}
.sc-d--zero {
  color: var(--atmr-text-tertiary);
  font-weight: 400;
}
.sc-group td {
  padding-top: var(--atmr-spacing-2x);
  font-weight: 600;
  border-bottom-color: var(--atmr-border-default);
}
.sc-group .sub {
  display: inline;
  margin-left: var(--atmr-spacing-2x);
  font-weight: 400;
}
.sc-over {
  color: var(--atmr-status-error);
}

/* ---- пострадавшие наряды */
.sc-affected {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  border-top: var(--atmr-border-width-s) solid var(--atmr-bg-surface3);
}
.sc-affected li {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr) max-content;
  align-items: center;
  gap: var(--atmr-spacing-3x);
  padding: 6px var(--atmr-spacing-2x);
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-bg-surface3);
  border-left: 3px solid transparent;
  font: var(--atmr-font-body-s);
  cursor: pointer;
}
.sc-affected li:hover {
  background: var(--atmr-bg-surface2);
}
.sc-affected li.is-selected {
  background: var(--atmr-accent-muted);
  border-left-color: var(--atmr-accent);
}
.sc-affected__what {
  min-width: 0;
}
.sc-affected__what .sub {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
```

## File: frontend/src/components/ScenariosPanel.tsx
```typescript
/*
 * Сценарный анализ (/ui/scenario/*): «что будет, если выйдет меньше сотрудников» и
 * «какой запас АО нужен в рюкзаке, чтобы не заезжать в СЦ», «что дают пробки по часам». Расчёт идёт на копии дня,
 * живой план не меняется. Пострадавшие наряды кликабельны — выбор на карте как в других списках.
 */
import { useEffect, useRef, useState } from 'react';
import { Badge, Button, SegmentedControl } from './ui';
import { api, ApiFailure } from '../api/client';
import { REASON, WORK_TYPE_SHORT, window2 } from '../format';
import type { Selection } from '../planModel';
import type {
  EquipmentResult,
  ScenarioMetrics,
  StaffCutResult,
  StaffCutStrategy,
  TrafficMetrics,
  TrafficResult,
  UiReference,
  UnassignedReason,
} from '../types/api';
import './scenarios.css';

const BUDGET_SEC = 15;
const MAX_REMOVE = 15;
const KINDS = ['ONT', 'ROUTER', 'STB'] as const;
const REASONS = Object.keys(REASON) as UnassignedReason[];

type Better = 'up' | 'down' | null;
interface Row<M = ScenarioMetrics> {
  key: string;
  label: string;
  get: (m: M) => number;
  fmt: (v: number) => string;
  better: Better;
  sub?: boolean;
}

const int = (v: number) => String(Math.round(v));
const pct = (v: number) => `${(v * 100).toFixed(1)} %`;
const hours = (v: number) => `${v.toFixed(1)} ч`;
const ratio = (v: number) => v.toFixed(3);
const secs = (v: number) => `${v.toFixed(1)} с`;

const TRAFFIC_ROWS: Row<TrafficMetrics>[] = [
  { key: 'visits', label: 'Размещено', get: (m) => m.visits, fmt: int, better: 'up' },
  { key: 'unassigned', label: 'Не распределено', get: (m) => m.unassigned, fmt: int, better: 'down' },
  { key: 'share', label: 'Доля нарядов в слот (по плану)', get: (m) => m.in_slot_share, fmt: pct, better: 'up' },
  { key: 'share_tr', label: 'Доля в слот при пробках', get: (m) => m.in_slot_share_traffic, fmt: pct, better: 'up' },
  { key: 'travel', label: 'Время в пути с пробками', get: (m) => m.travel_traffic_h, fmt: hours, better: 'down' },
  { key: 'late', label: 'Поздних прибытий (LATE_RISK)', get: (m) => m.late_visits, fmt: int, better: 'down' },
  { key: 'viol', label: 'Нарушений валидатора', get: (m) => m.violations, fmt: int, better: 'down' },
  { key: 'time', label: 'Время расчёта', get: (m) => m.time_sec, fmt: secs, better: null },
];

function rowsFor(base: ScenarioMetrics, other: ScenarioMetrics): Row[] {
  const rows: Row[] = [
    { key: 'share', label: 'Доля нарядов в слот', get: (m) => m.in_slot_share, fmt: pct, better: 'up' },
    { key: 'visits', label: 'Размещено', get: (m) => m.visits, fmt: int, better: 'up' },
    { key: 'unassigned', label: 'Не распределено', get: (m) => m.unassigned, fmt: int, better: 'down' },
  ];
  for (const r of REASONS) {
    if (!base.unassigned_by_reason[r] && !other.unassigned_by_reason[r]) continue;
    rows.push({ key: r, label: REASON[r], get: (m) => m.unassigned_by_reason[r] ?? 0, fmt: int, better: 'down', sub: true });
  }
  rows.push(
    { key: 'travel', label: 'Время в пути', get: (m) => m.travel_h, fmt: hours, better: 'down' },
    { key: 'cv', label: 'Неравномерность загрузки (CV)', get: (m) => m.util_cv, fmt: ratio, better: 'down' },
    { key: 'restocks', label: 'Заездов в СЦ за АО', get: (m) => m.restocks, fmt: int, better: 'down' },
    { key: 'staff', label: 'Сотрудников с визитами', get: (m) => m.staff_used, fmt: int, better: null },
  );
  return rows;
}

function deltaText<M>(row: Row<M>, d: number) {
  if (Math.abs(d) < 1e-9) return '0';
  const sign = d > 0 ? '+' : '−';
  if (row.key.startsWith('share')) return `${sign}${Math.abs(d * 100).toFixed(1)} п.п.`;
  return sign + row.fmt(Math.abs(d)).replace(' ч', '');
}

function deltaTone<M>(row: Row<M>, d: number) {
  if (Math.abs(d) < 1e-9 || !row.better) return 'sc-d--zero';
  return (d > 0) === (row.better === 'up') ? 'sc-d--good' : 'sc-d--bad';
}

function Compare<M extends ScenarioMetrics>({
  base,
  other,
  labels,
  rows,
}: {
  base: M;
  other: M;
  labels: [string, string];
  rows?: Row<M>[];
}) {
  return (
    <table className="sc-table">
      <thead>
        <tr>
          <th />
          <th className="sc-num">{labels[0]}</th>
          <th className="sc-num">{labels[1]}</th>
          <th className="sc-num">Δ</th>
        </tr>
      </thead>
      <tbody>
        {(rows ?? (rowsFor(base, other) as Row<M>[])).map((row) => {
          const a = row.get(base);
          const b = row.get(other);
          const d = b - a;
          return (
            <tr key={row.key} className={row.sub ? 'sc-sub' : undefined}>
              <td>{row.label}</td>
              <td className="sc-num">{row.fmt(a)}</td>
              <td className="sc-num sc-strong">{row.fmt(b)}</td>
              <td className={`sc-num sc-d ${deltaTone(row, d)}`}>{deltaText(row, d)}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

/** Секунды с начала расчёта; полоса заполняется к ожидаемому времени, но не до конца. */
function Progress({ startedAt, expected }: { startedAt: number; expected: number }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 200);
    return () => clearInterval(t);
  }, []);
  const sec = (now - startedAt) / 1000;
  const share = Math.min(0.95, sec / expected);
  return (
    <div className="sc-progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(share * 100)}>
      <div className="sc-progress__bar">
        <span style={{ width: `${share * 100}%` }} />
      </div>
      <span className="sc-progress__text num">
        {sec.toFixed(0)} с из ≈{expected} с
      </span>
    </div>
  );
}

const STRATEGY_LABEL: Record<string, string> = {
  random: 'Случайные',
  least_loaded: 'Наименее загруженные',
  'by_profile:INSTALL': 'Только инсталляторы',
  'by_profile:LTP3': 'Только ЗЛТП',
};

type Kind = 'cut' | 'eq' | 'traffic';
type Running = { kind: Kind; startedAt: number; expected: number } | null;

export function ScenariosPanel({
  date,
  planVersion,
  reference,
  selected,
  onSelect,
}: {
  date?: string;
  planVersion?: number;
  reference: UiReference | null;
  selected: Selection | null;
  onSelect: (id: string) => void;
}) {
  const [count, setCount] = useState(3);
  const [strategy, setStrategy] = useState<StaffCutStrategy>('random');
  const [seed, setSeed] = useState(1);
  const [cut, setCut] = useState<StaffCutResult | null>(null);
  const [eq, setEq] = useState<EquipmentResult | null>(null);
  const [traffic, setTraffic] = useState<TrafficResult | null>(null);
  const [running, setRunning] = useState<Running>(null);
  const [error, setError] = useState<{ kind: Kind; text: string } | null>(null);
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  // Без событий база — текущий план; после событий база строится заново, расчётов два.
  const expected = BUDGET_SEC * ((planVersion ?? 1) > 1 ? 2 : 1) + 2;

  async function run<T>(kind: Kind, call: () => Promise<T>, done: (r: T) => void, expectedSec = expected) {
    if (running) return;
    setRunning({ kind, startedAt: Date.now(), expected: expectedSec });
    setError(null);
    try {
      const r = await call();
      if (alive.current) done(r);
    } catch (e) {
      if (alive.current) setError({ kind, text: e instanceof ApiFailure || e instanceof Error ? e.message : String(e) });
    } finally {
      if (alive.current) setRunning(null);
    }
  }

  const calcCut = () =>
    run('cut', () => api.scenarioStaffCut({ date, remove_count: count, strategy, seed, budget_sec: BUDGET_SEC }), setCut);
  const calcEq = () => run('eq', () => api.scenarioEquipment({ date, mode: 'no_restock', budget_sec: BUDGET_SEC }), setEq);
  // два плана на копии: без коэффициентов и с ними
  const calcTraffic = () =>
    run('traffic', () => api.scenarioTraffic({ date, budget_sec: BUDGET_SEC }), setTraffic, BUDGET_SEC * 2 + 2);

  const centers = reference?.service_centers ?? [];
  const disabled = !date || !!running;

  return (
    <div className="list">
      <div className="scroll sc">
        <p className="sc-note">
          Расчёт на копии дня {date ?? '—'}: живой план и маршруты не меняются. База —{' '}
          {(planVersion ?? 1) > 1 ? 'первоначальный план, пересчитанный без событий' : 'текущий план дня (версия 1)'}.
        </p>

        {/* ------------------------------------------------ меньше сотрудников */}
        <section className="sc-sec">
          <header className="sc-sec__head">
            <h3>Меньше сотрудников</h3>
            <span className="sc-sec__q">Что будет, если на смену выйдет на N человек меньше?</span>
          </header>

          <div className="sc-controls">
            <div className="sc-field">
              <span className="sc-label">Убрать</span>
              <div className="sc-stepper">
                <button type="button" aria-label="Меньше" disabled={disabled || count <= 0} onClick={() => setCount((c) => c - 1)}>
                  −
                </button>
                <input
                  className="num"
                  type="number"
                  min={0}
                  max={MAX_REMOVE}
                  value={count}
                  disabled={!!running}
                  aria-label="Сколько сотрудников убрать"
                  onChange={(e) => setCount(Math.max(0, Math.min(MAX_REMOVE, Number(e.target.value) || 0)))}
                />
                <button
                  type="button"
                  aria-label="Больше"
                  disabled={disabled || count >= MAX_REMOVE}
                  onClick={() => setCount((c) => c + 1)}
                >
                  +
                </button>
              </div>
            </div>
            <label className="sc-field">
              <span className="sc-label">Кого</span>
              <select
                className="select"
                value={strategy}
                disabled={!!running}
                onChange={(e) => setStrategy(e.target.value as StaffCutStrategy)}
              >
                {Object.entries(STRATEGY_LABEL).map(([v, l]) => (
                  <option key={v} value={v}>
                    {l}
                  </option>
                ))}
                {centers.length > 0 && (
                  <optgroup label="Из одного СЦ">
                    {centers.map((c) => (
                      <option key={c.service_center_id} value={`by_service_center:${c.service_center_id}`}>
                        {c.service_center_id} · {c.name}
                      </option>
                    ))}
                  </optgroup>
                )}
              </select>
            </label>
            {strategy !== 'least_loaded' && (
              <div className="sc-field" title="Другое зерно — другой случайный выбор сотрудников">
                <span className="sc-label">Выборка</span>
                <SegmentedControl
                  size="s"
                  variant="primary"
                  value={String(seed)}
                  disabledAll={!!running}
                  onChange={(v) => setSeed(Number(v))}
                  items={[1, 2, 3].map((n) => ({ index: String(n), label: `#${n}` }))}
                />
              </div>
            )}
            <Button size="s" label="Рассчитать" disabled={disabled} onClick={calcCut} />
          </div>

          {running?.kind === 'cut' && <Progress startedAt={running.startedAt} expected={running.expected} />}
          {error?.kind === 'cut' && <p className="sc-error">{error.text}</p>}

          {cut && (
            <div className={`sc-result${running?.kind === 'cut' ? ' is-stale' : ''}`}>
              <div className="sc-meta">
                <span>
                  {STRATEGY_LABEL[cut.strategy] ?? cut.strategy.replace('by_service_center:', 'СЦ ')}, −{cut.remove_count}
                </span>
                <span className="num">расчёт {cut.time_sec.toFixed(1)} с</span>
              </div>
              {cut.removed.length > 0 && (
                <ul className="chips sc-removed">
                  {cut.removed.map((r) => (
                    <li key={r.staff_id} title={`${r.service_center_id}, в базе визитов: ${r.base_visits}`}>
                      {r.display_name ?? r.staff_id}
                      <span className="sub">
                        {r.profiles.join('/')} · {r.service_center_id} · {r.base_visits} виз.
                      </span>
                    </li>
                  ))}
                </ul>
              )}
              <Compare base={cut.base} other={cut.scenario} labels={['База', 'Сценарий']} />
              <p className="sc-foot num">
                Передано другим: {cut.reassigned} · встали заново: {cut.newly_routed.length}
              </p>

              <h4 className="sc-h4">
                Пострадавшие наряды <span className="num">{cut.affected_orders.length}</span>
              </h4>
              {cut.affected_orders.length === 0 ? (
                <p className="sc-empty">Все наряды базы остались в маршрутах.</p>
              ) : (
                <ul className="sc-affected">
                  {cut.affected_orders.map((a) => (
                    <li
                      key={a.order_id}
                      className={selected?.id === a.order_id ? 'is-selected' : undefined}
                      onClick={() => onSelect(a.order_id)}
                      title={a.reason_text ?? a.address ?? undefined}
                    >
                      <span className="mono-id">{a.order_id}</span>
                      <span className="sc-affected__what">
                        {a.work_type_code ? WORK_TYPE_SHORT[a.work_type_code] : '—'}
                        {a.is_repeat && <span className="flag">повт.</span>}
                        <span className="sub num">
                          слот {window2(a.slot)} · был у {a.base_staff_id}
                          {a.base_staff_removed ? ' (убран)' : ''}
                        </span>
                      </span>
                      {a.reason_code && (
                        <Badge
                          size="2xs"
                          variant="secondary"
                          colorScheme={a.reason_code === 'PGP' ? 'status-01' : 'error'}
                          label={a.reason_code === 'PGP' ? 'ПГП' : REASON[a.reason_code]}
                        />
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </section>

        {/* ------------------------------------------------ запас АО */}
        <section className="sc-sec">
          <header className="sc-sec__head">
            <h3>Запас АО в рюкзаке</h3>
            <span className="sc-sec__q">Сколько ONT, роутеров и приставок взять с утра, чтобы не заезжать в СЦ?</span>
          </header>
          <div className="sc-controls">
            <p className="sc-hint">
              План строится с «бездонным» рюкзаком, по маршрутам считается расход АО каждого сотрудника за смену.
            </p>
            <Button size="s" label="Оценить" disabled={disabled} onClick={calcEq} />
          </div>

          {running?.kind === 'eq' && <Progress startedAt={running.startedAt} expected={running.expected} />}
          {error?.kind === 'eq' && <p className="sc-error">{error.text}</p>}

          {eq && (
            <div className={`sc-result${running?.kind === 'eq' ? ' is-stale' : ''}`}>
              <div className="sc-meta">
                <span>
                  Выиграно нарядов: <b className="num">{eq.visits_gained > 0 ? `+${eq.visits_gained}` : eq.visits_gained}</b>
                  {' · '}в пути {eq.travel_saved_h >= 0 ? 'меньше' : 'больше'} на{' '}
                  <b className="num">{Math.abs(eq.travel_saved_h).toFixed(1)} ч</b>
                </span>
                <span className="num">расчёт {eq.time_sec.toFixed(1)} с</span>
              </div>
              <Compare base={eq.current} other={eq.no_restock} labels={['Как сейчас', 'Без заездов']} />

              <h4 className="sc-h4">Рекомендуемый запас на сотрудника</h4>
              <table className="sc-table sc-eq">
                <thead>
                  <tr>
                    <th />
                    <th className="sc-num">p50</th>
                    <th className="sc-num">p90</th>
                    <th className="sc-num">макс.</th>
                    <th className="sc-num">сейчас, ср.</th>
                    <th className="sc-num">ёмкость</th>
                  </tr>
                </thead>
                {eq.recommended.map((r) => (
                  <tbody key={r.profile}>
                    <tr className="sc-group">
                      <td colSpan={6}>
                        {r.profile === 'INSTALL' ? 'Инсталляторы' : 'ЗЛТП'}
                        <span className="sub num">
                          {r.staff_with_visits} из {r.staff_on_shift} с визитами · не хватило бы утреннего запаса:{' '}
                          {r.staff_exceeding_current_start}
                        </span>
                      </td>
                    </tr>
                    {KINDS.map((k) => {
                      const st = r[k];
                      return (
                        <tr key={k}>
                          <td>{k === 'STB' ? 'STB (приставка)' : k === 'ROUTER' ? 'Роутер' : 'ONT'}</td>
                          <td className="sc-num">{st.p50}</td>
                          <td className={`sc-num sc-strong${st.p90 > st.capacity ? ' sc-over' : ''}`}>{st.p90}</td>
                          <td className={`sc-num${st.max > st.capacity ? ' sc-over' : ''}`}>{st.max}</td>
                          <td className="sc-num sc-muted">{st.current_start_avg.toFixed(1)}</td>
                          <td className="sc-num sc-muted">{st.capacity}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                ))}
              </table>
              <p className="sc-foot">
                Рекомендация — p90: девять из десяти сотрудников профиля обойдутся без заезда. Красным — больше ёмкости
                рюкзака, такой запас физически не взять.
              </p>
            </div>
          )}
        </section>

        {/* ------------------------------------------------ пробки по часам */}
        <section className="sc-sec">
          <header className="sc-sec__head">
            <h3>Пробки по часам</h3>
            <span className="sc-sec__q">Что изменится, если планировать с учётом загруженности дорог по часам суток?</span>
          </header>
          <div className="sc-controls">
            <p className="sc-hint">
              Время в пути умножается на коэффициент часа выезда из матрицы (не меньше базового). Оба плана исполняются в
              пробках: видно, сколько визитов плана без коэффициентов опоздало бы. В живом планировании режим по умолчанию
              выключен и включается переменной <code>TRAFFIC_FACTORS=on</code>.
            </p>
            <Button size="s" label="Сравнить" disabled={disabled} onClick={calcTraffic} />
          </div>

          {running?.kind === 'traffic' && <Progress startedAt={running.startedAt} expected={running.expected} />}
          {error?.kind === 'traffic' && <p className="sc-error">{error.text}</p>}

          {traffic && (
            <div className={`sc-result${running?.kind === 'traffic' ? ' is-stale' : ''}`}>
              <div className="sc-meta">
                <span>
                  Коэффициенты:{' '}
                  {traffic.factors.length === 0 ? (
                    'в матрице нет'
                  ) : (
                    <span className="num">
                      {traffic.factors.map((f) => `${String(f.hour).padStart(2, '0')}:00 ×${f.factor}`).join(', ')}
                    </span>
                  )}
                  {' · '}сейчас {traffic.traffic_env ? 'включено' : 'выключено'}
                </span>
                <span className="num">расчёт {traffic.time_sec.toFixed(1)} с</span>
              </div>
              <Compare base={traffic.off} other={traffic.on} labels={['Без пробок', 'С пробками']} rows={TRAFFIC_ROWS} />
              <p className="sc-foot">
                Валидатор организаторов проверяет время в пути по базовой матрице, поэтому план с пробками ему тоже
                удовлетворяет: переезды в нём только длиннее.
              </p>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
```

## File: frontend/src/components/stats.css
```css
/* Окно «Статистика»: три компактные секции в одной прокрутке. Цвета — только токены. */
.stats {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x) var(--atmr-spacing-4x);
  display: grid;
  align-content: start;
  gap: var(--atmr-spacing-3x);
  color: var(--atmr-text-primary);
}

.st-sec {
  min-width: 0;
  display: grid;
  gap: var(--atmr-spacing-2x);
  padding-bottom: var(--atmr-spacing-3x);
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
}
.st-sec:last-of-type {
  border-bottom: 0;
}
.st-sec__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--atmr-spacing-1x) var(--atmr-spacing-2x);
  min-height: 28px;
}
.st-sec__title {
  margin: 0;
  font: 600 13px/18px var(--atmr-font-family);
}
.st-sec__meta {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
}
.st-sec__aside {
  margin-left: auto;
}
.st-note {
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-secondary);
  padding: 1px var(--atmr-spacing-2x);
  border-radius: var(--atmr-border-radius-full);
  background: var(--atmr-bg-surface3);
  cursor: help;
}
.st-hint {
  margin: 0;
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
}

/* ---- 1. малые множители по версиям */
.st-multi {
  display: grid;
  gap: var(--atmr-spacing-2x);
  min-width: 0;
}
.st-card {
  min-width: 0;
  padding: var(--atmr-spacing-2x) 0 var(--atmr-spacing-1x);
}
.st-card__head {
  display: flex;
  align-items: baseline;
  gap: var(--atmr-spacing-2x);
  margin-bottom: 2px;
}
.st-card__label {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.st-card__value {
  margin-left: auto;
  font: 600 16px/20px var(--atmr-font-family);
}
.st-card__delta {
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-tertiary);
  white-space: nowrap;
}
.st-card__range {
  display: flex;
  justify-content: space-between;
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-tertiary);
}
.st-spark {
  display: block;
  cursor: crosshair;
}
.st-spark__base { stroke: var(--atmr-border-default); stroke-width: 1; }
.st-spark__cross { stroke: var(--atmr-border-strong); stroke-width: 1; stroke-dasharray: 2 2; }
.st-spark__line { fill: none; stroke: var(--atmr-stat-line); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
.st-spark__dot { fill: var(--atmr-stat-line); opacity: 0.55; }
.st-spark__last { fill: var(--atmr-stat-line); stroke: var(--atmr-bg-surface1); stroke-width: 2; }
.st-spark__hover { fill: var(--atmr-bg-surface1); stroke: var(--atmr-stat-line); stroke-width: 2; }

/* ---- KPI и легенда загрузки */
.st-kpis {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: var(--atmr-spacing-2x) var(--atmr-spacing-5x);
}
.st-kpi {
  display: grid;
  cursor: help;
}
.st-kpi__value,
.st-hero__value {
  font: var(--atmr-font-display-s);
  font-size: 22px;
  line-height: 26px;
}
.st-kpi__label,
.st-hero__label {
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-secondary);
}
.st-legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--atmr-spacing-1x) var(--atmr-spacing-3x);
  margin: 0 0 0 auto;
  padding: 0;
  list-style: none;
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-secondary);
}
.st-legend li {
  display: inline-flex;
  align-items: center;
  gap: var(--atmr-spacing-1x);
}
.st-sw {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}
.st-sw--work { background: var(--atmr-stat-work); }
.st-sw--road { background: var(--atmr-stat-road); }
.st-sw--break { background: var(--atmr-stat-break); }
.st-sw--idle { background: var(--atmr-stat-idle); box-shadow: inset 0 0 0 1px var(--atmr-border-default); }

/* ---- строки баров (сотрудники и причины) */
.st-load,
.st-reasons__bars {
  display: grid;
  min-width: 0;
}
.st-row {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  width: 100%;
  min-height: 22px;
  padding: 2px var(--atmr-spacing-1x);
  border: 0;
  border-radius: var(--atmr-border-radius-xs);
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.st-row:hover {
  background: var(--atmr-bg-surface2);
}
.st-row.is-selected {
  background: var(--atmr-accent-muted);
  box-shadow: inset 3px 0 0 var(--atmr-accent);
}
.st-row:focus-visible {
  outline: 2px solid var(--atmr-accent);
  outline-offset: -2px;
}
.st-row.is-off .st-row__name {
  color: var(--atmr-text-tertiary);
}
.st-row__name {
  flex: none;
  font: var(--atmr-font-description-m);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.st-row__bar {
  flex: none;
  display: block;
}
.st-row__pct {
  flex: none;
  min-width: 38px;
  margin-left: auto;
  text-align: right;
  font: 600 12px/16px var(--atmr-font-family);
}
.st-row__pct small {
  font-weight: 400;
  color: var(--atmr-text-tertiary);
}
.st-row--reason .st-row__pct {
  min-width: 62px;
}
.st-bar--idle { fill: var(--atmr-stat-idle); }
.st-bar--work { fill: var(--atmr-stat-work); }
.st-bar--road { fill: var(--atmr-stat-road); }
.st-bar--break { fill: var(--atmr-stat-break); }
.st-bar--reason { fill: var(--atmr-stat-line); }
.st-row--reason:not(.is-selected) .st-bar--reason { opacity: 0.85; }

/* ---- 3. причины: итог слева, бары справа; в узком окне — друг под другом */
.st-reasons {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: var(--atmr-spacing-2x) var(--atmr-spacing-4x);
}
.st-hero {
  display: grid;
  flex: none;
  min-width: 84px;
}
.st-reasons__bars {
  flex: 1 1 260px;
}
```

## File: frontend/src/components/StatsPanel.tsx
```typescript
/*
 * Окно «Статистика»: динамика плана по версиям, загрузка сотрудников, нераспределённые по причинам.
 * Собственный SVG без библиотек: цвета — токены --atmr-stat-*, текст — текстовые токены, тултипы — .gantt-tip.
 */
import { useEffect, useLayoutEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../api/client';
import { REASON, hhmm } from '../format';
import { PROFILE_SHORT, minutesOf, minutesOfHm, type MetricPoint, type Selection, type StaffInfo } from '../planModel';
import type { HistoryVersion, Plan, Profile, UiOrder, UnassignedReason } from '../types/api';
import { SegmentedControl } from './ui';
import './stats.css';

/* ---------------------------------------------------------------- общие мелочи */

interface Tip {
  x: number;
  y: number;
  title: string;
  lines: string[];
}
type ShowTip = (e: ReactMouseEvent, title: string, lines: string[]) => void;

function TipView({ tip }: { tip: Tip | null }) {
  if (!tip) return null;
  const below = tip.y > window.innerHeight - 170;
  return createPortal(
    <div
      className="gantt-tip"
      role="tooltip"
      style={{
        left: Math.max(8, Math.min(tip.x + 14, window.innerWidth - 300)),
        top: below ? tip.y - 12 : tip.y + 16,
        transform: below ? 'translateY(-100%)' : undefined,
      }}
    >
      <strong>{tip.title}</strong>
      {tip.lines.map((l, i) => (
        <span key={i}>{l}</span>
      ))}
    </div>,
    document.body,
  );
}

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [w, setW] = useState(0);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setW(el.clientWidth));
    ro.observe(el);
    setW(el.clientWidth);
    return () => ro.disconnect();
  }, []);
  return [ref, w] as const;
}

const pct = (v: number) => `${Math.round(v * 100)}%`;
const num = (v: number) => v.toLocaleString('ru-RU', { maximumFractionDigits: 1 });
const hm = (min: number) => {
  const m = Math.round(min);
  return m >= 60 ? `${Math.floor(m / 60)} ч ${m % 60} мин` : `${m} мин`;
};

function Section({ title, meta, aside, children }: { title: string; meta?: ReactNode; aside?: ReactNode; children: ReactNode }) {
  return (
    <section className="st-sec">
      <header className="st-sec__head">
        <h3 className="st-sec__title">{title}</h3>
        {meta && <span className="st-sec__meta">{meta}</span>}
        {aside && <span className="st-sec__aside">{aside}</span>}
      </header>
      {children}
    </section>
  );
}

/* ---------------------------------------------------------------- 1. динамика по версиям */

type SeriesKey = 'visits' | 'unassigned' | 'lateRisk' | 'travelH';
interface Point {
  version: number;
  asOf: string | null;
  visits: number;
  unassigned: number;
  lateRisk: number;
  travelH: number;
}
const SERIES: { key: SeriesKey; label: string; fmt: (v: number) => string }[] = [
  { key: 'visits', label: 'Размещено визитов', fmt: (v) => String(Math.round(v)) },
  { key: 'unassigned', label: 'Не распределено', fmt: (v) => String(Math.round(v)) },
  { key: 'lateRisk', label: 'Риск опоздания', fmt: (v) => String(Math.round(v)) },
  { key: 'travelH', label: 'Пробег, ч', fmt: (v) => num(v) },
];

const fromSession = (m: MetricPoint): Point => ({
  version: m.version,
  asOf: m.asOf,
  visits: m.visits,
  unassigned: m.unassigned,
  lateRisk: m.lateRisk,
  travelH: m.travelSec / 3600,
});
const fromJournal = (v: HistoryVersion): Point => ({
  version: v.version,
  asOf: v.as_of,
  visits: v.visits_planned ?? NaN,
  unassigned: v.unassigned ?? NaN,
  lateRisk: v.late_risk_active ?? NaN,
  travelH: v.travel_sec_total != null ? v.travel_sec_total / 3600 : NaN,
});

/** Журнал БД по дню; null — журнал недоступен (503 без Postgres) или пуст. */
function useJournal(date: string, version: number): HistoryVersion[] | null {
  const [rows, setRows] = useState<{ date: string; rows: HistoryVersion[] | null } | null>(null);
  useEffect(() => {
    let alive = true;
    api
      .historyVersions(date)
      .then((r) => alive && setRows({ date, rows: r.versions.length ? r.versions : null }))
      .catch(() => alive && setRows({ date, rows: null }));
    return () => {
      alive = false;
    };
  }, [date, version]);
  return rows?.date === date ? rows.rows : null;
}

function Dynamics({ plan, session, showTip, hideTip }: { plan: Plan; session: MetricPoint[]; showTip: ShowTip; hideTip: () => void }) {
  const journal = useJournal(plan.date, plan.plan_version);
  const [boxRef, w] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);

  const points = useMemo(() => {
    const m = new Map<number, Point>();
    for (const s of session) if (s.date === plan.date) m.set(s.version, fromSession(s));
    // Журнал важнее: в нём все версии дня, а не только увиденные этой вкладкой.
    for (const v of journal ?? []) m.set(v.version, fromJournal(v));
    return [...m.values()].sort((a, b) => a.version - b.version).slice(-200);
  }, [session, journal, plan.date]);

  const cols = w >= 520 ? 2 : 1;
  const cardW = Math.max(120, Math.floor((w - (cols - 1) * 8) / cols) - 2);
  const H = 54;
  const PAD = 5;
  const v0 = points[0]?.version ?? 0;
  const v1 = points[points.length - 1]?.version ?? 0;
  const x = (v: number) => (v1 === v0 ? cardW / 2 : PAD + ((v - v0) / (v1 - v0)) * (cardW - 2 * PAD));

  const hovered = hover != null ? points.find((p) => p.version === hover) : undefined;

  const onMove = (e: ReactMouseEvent<SVGSVGElement>) => {
    if (!points.length) return;
    const r = e.currentTarget.getBoundingClientRect();
    const mx = e.clientX - r.left;
    let best = points[0];
    for (const p of points) if (Math.abs(x(p.version) - mx) < Math.abs(x(best.version) - mx)) best = p;
    setHover(best.version);
    showTip(e, `Версия ${best.version}${best.asOf ? ` · ${hhmm(best.asOf)}` : ''}`, SERIES.map((s) => `${s.label}: ${Number.isNaN(best[s.key]) ? '—' : s.fmt(best[s.key])}`));
  };
  const onLeave = () => {
    setHover(null);
    hideTip();
  };

  const sourceNote = journal ? 'журнал плана' : 'история сессии';
  return (
    <Section
      title="Динамика плана по версиям"
      meta={points.length ? `версии ${v0}–${v1}` : undefined}
      aside={
        <span className="st-note" title={journal ? 'Метрики из журнала PostgreSQL (/ui/history)' : 'Журнал недоступен: точки накоплены этой вкладкой с момента загрузки дня'}>
          {sourceNote}
        </span>
      }
    >
      <div ref={boxRef} className="st-multi" style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}>
        {w > 0 &&
          SERIES.map((s) => {
            const vals = points.map((p) => p[s.key]).filter((v) => !Number.isNaN(v));
            const last = points.length ? points[points.length - 1][s.key] : NaN;
            const prev = points.length > 1 ? points[points.length - 2][s.key] : NaN;
            const delta = last - prev;
            let lo = vals.length ? Math.min(...vals) : 0;
            let hi = vals.length ? Math.max(...vals) : 1;
            if (hi === lo) {
              lo -= 1;
              hi += 1;
            }
            const y = (v: number) => PAD + (1 - (v - lo) / (hi - lo)) * (H - 2 * PAD);
            const valid = points.filter((p) => !Number.isNaN(p[s.key]));
            const d = valid.map((p, i) => `${i ? 'L' : 'M'}${x(p.version).toFixed(1)} ${y(p[s.key]).toFixed(1)}`).join('');
            const hv = hovered && !Number.isNaN(hovered[s.key]) ? hovered : undefined;
            return (
              <div key={s.key} className="st-card">
                <div className="st-card__head">
                  <span className="st-card__label">{s.label}</span>
                  <span className="st-card__value num">{Number.isNaN(last) ? '—' : s.fmt(last)}</span>
                  {!Number.isNaN(delta) && delta !== 0 && (
                    <span className="st-card__delta num" title="Изменение к предыдущей версии">
                      {delta > 0 ? '▲' : '▼'} {s.fmt(Math.abs(delta))}
                    </span>
                  )}
                </div>
                <svg width={cardW} height={H} className="st-spark" onMouseMove={onMove} onMouseLeave={onLeave} role="img" aria-label={`${s.label} по версиям плана`}>
                  <line className="st-spark__base" x1={0} x2={cardW} y1={H - PAD} y2={H - PAD} />
                  {hv && <line className="st-spark__cross" x1={x(hv.version)} x2={x(hv.version)} y1={0} y2={H} />}
                  {valid.length > 1 && <path className="st-spark__line" d={d} />}
                  {valid.length <= 40 &&
                    valid.map((p) => <circle key={p.version} className="st-spark__dot" cx={x(p.version)} cy={y(p[s.key])} r={2} />)}
                  {valid.length > 0 && (
                    <circle className="st-spark__last" cx={x(valid[valid.length - 1].version)} cy={y(valid[valid.length - 1][s.key])} r={3.5} />
                  )}
                  {hv && <circle className="st-spark__hover" cx={x(hv.version)} cy={y(hv[s.key])} r={4} />}
                </svg>
                <div className="st-card__range num">
                  <span>мин {s.fmt(vals.length ? Math.min(...vals) : 0)}</span>
                  <span>макс {s.fmt(vals.length ? Math.max(...vals) : 0)}</span>
                </div>
              </div>
            );
          })}
      </div>
      {points.length < 2 && <p className="st-hint">Линии появятся после новых версий плана: «+1», «+5» или событий из панели «Данные».</p>}
    </Section>
  );
}

/* ---------------------------------------------------------------- 2. загрузка сотрудников */

interface LoadRow {
  id: string;
  name: string;
  profiles: Profile[];
  shift: number;
  work: number;
  restock: number;
  road: number;
  brk: number;
  idle: number;
  load: number;
  visits: number;
  off: boolean;
}

function loadRows(plan: Plan, staff: Map<string, StaffInfo>): LoadRow[] {
  const routes = new Map(plan.routes.map((r) => [r.staff_id, r]));
  const ids = new Set([...staff.keys(), ...routes.keys()]);
  const rows: LoadRow[] = [];
  for (const id of ids) {
    const info = staff.get(id);
    const route = routes.get(id);
    let work = 0;
    let restock = 0;
    let road = 0;
    let brk = 0;
    let visits = 0;
    let first = NaN;
    let last = NaN;
    for (const s of route?.stops ?? []) {
      const a = minutesOf(s.start);
      const b = minutesOf(s.end);
      if (Number.isNaN(first) || a < first) first = a;
      if (Number.isNaN(last) || b > last) last = b;
      if (s.type !== 'DEPOT_START') road += (s.travel_sec ?? 0) / 60;
      if (s.type === 'VISIT') {
        work += Math.max(0, b - a);
        visits++;
      } else if (s.type === 'RESTOCK') restock += Math.max(0, b - a);
      else if (s.type === 'BREAK') brk += Math.max(0, b - a);
    }
    let shift = minutesOfHm(info?.shift?.end) - minutesOfHm(info?.shift?.start);
    if (!(shift > 0)) shift = last - first > 0 ? last - first : 12 * 60;
    const used = work + restock + road + brk;
    // Как utilization в плане: (работа + дорога) / чистая смена (без перерыва); берём из totals, если есть.
    const load = route?.totals?.utilization ?? (visits ? (work + road) / Math.max(1, shift - brk) : 0);
    rows.push({
      id,
      name: info?.name ?? id,
      profiles: info?.profiles ?? [],
      shift,
      work,
      restock,
      road,
      brk,
      idle: Math.max(0, shift - used),
      load,
      visits,
      off: route?.status === 'UNAVAILABLE',
    });
  }
  return rows.sort((a, b) => Number(a.off) - Number(b.off) || b.load - a.load || a.name.localeCompare(b.name, 'ru'));
}

type ProfileFilter = 'ALL' | Profile;

function StaffLoad({
  plan,
  staff,
  staffSel,
  onSelectStaff,
  showTip,
  hideTip,
}: {
  plan: Plan;
  staff: Map<string, StaffInfo>;
  staffSel: Selection | null;
  onSelectStaff: (id: string | null) => void;
  showTip: ShowTip;
  hideTip: () => void;
}) {
  const [profile, setProfile] = useState<ProfileFilter>('ALL');
  const [boxRef, w] = useWidth<HTMLDivElement>();
  const all = useMemo(() => loadRows(plan, staff), [plan, staff]);
  const rows = profile === 'ALL' ? all : all.filter((r) => r.profiles.includes(profile));

  // Разброс загрузки — коэффициент вариации по сотрудникам с визитами (как utilization_cv в сводке плана).
  const used = rows.filter((r) => r.visits > 0);
  const mean = used.length ? used.reduce((n, r) => n + r.load, 0) / used.length : 0;
  const sd = used.length ? Math.sqrt(used.reduce((n, r) => n + (r.load - mean) ** 2, 0) / used.length) : 0;
  const cv = used.length >= 2 && mean > 0 ? sd / mean : 0;

  const labelW = Math.round(Math.min(190, Math.max(96, w * 0.34)));
  const trackW = Math.max(60, w - labelW - 46 - 12);
  const scale = Math.max(1, ...rows.map((r) => r.shift));
  const sx = (m: number) => (m / scale) * trackW;
  const sel = staffSel?.id ?? null;

  return (
    <Section
      title="Загрузка сотрудников"
      meta={`${rows.length} чел.`}
      aside={
        <SegmentedControl
          size="s"
          variant="secondary"
          value={profile}
          onChange={(v) => setProfile(v as ProfileFilter)}
          items={[
            { index: 'ALL', label: 'Все' },
            { index: 'LTP3', label: 'ЛТП' },
            { index: 'INSTALL', label: 'Инсталл.' },
          ]}
        />
      }
    >
      <div className="st-kpis">
        <div className="st-kpi" title="Средняя загрузка сотрудников с визитами: (работа + дорога) / смена без перерыва">
          <span className="st-kpi__value num">{used.length ? pct(mean) : '—'}</span>
          <span className="st-kpi__label">средняя загрузка</span>
        </div>
        <div className="st-kpi" title="Коэффициент вариации загрузки (σ / среднее): чем меньше, тем ровнее распределены наряды">
          <span className="st-kpi__value num">{used.length >= 2 ? cv.toFixed(2) : '—'}</span>
          <span className="st-kpi__label">разброс загрузки</span>
        </div>
        <ul className="st-legend" aria-label="Обозначения">
          <li><i className="st-sw st-sw--work" />Работа</li>
          <li><i className="st-sw st-sw--road" />Дорога</li>
          <li><i className="st-sw st-sw--break" />Перерыв</li>
          <li><i className="st-sw st-sw--idle" />Простой</li>
        </ul>
      </div>
      <div ref={boxRef} className="st-load" onMouseLeave={hideTip}>
        {!rows.length && <p className="st-hint">Нет сотрудников с таким профилем.</p>}
        {w > 0 &&
          rows.map((r) => {
            const isSel = r.id === sel;
            const segs: { cls: string; m: number }[] = [
              { cls: 'work', m: r.work + r.restock },
              { cls: 'road', m: r.road },
              { cls: 'break', m: r.brk },
            ];
            let at = 0;
            return (
              <button
                key={r.id}
                type="button"
                className={`st-row${isSel ? ' is-selected' : ''}${r.off ? ' is-off' : ''}`}
                aria-pressed={isSel}
                onClick={() => onSelectStaff(isSel ? null : r.id)}
                onMouseEnter={(e) =>
                  showTip(e, r.name, [
                    `${r.profiles.map((p) => PROFILE_SHORT[p]).join(', ') || 'профиль не указан'}${r.off ? ' · недоступен' : ''}`,
                    `Загрузка ${pct(r.load)}, визитов ${r.visits}`,
                    `Работа ${hm(r.work)}${r.restock ? `, пополнение в СЦ ${hm(r.restock)}` : ''}`,
                    `Дорога ${hm(r.road)} · перерыв ${hm(r.brk)}`,
                    `Простой ${hm(r.idle)} из смены ${hm(r.shift)}`,
                  ])
                }
                onMouseMove={(e) => showTip(e, r.name, [])}
              >
                <span className="st-row__name" style={{ width: labelW }}>
                  {r.name}
                </span>
                <svg width={trackW} height={14} className="st-row__bar" aria-hidden>
                  <rect className="st-bar--idle" x={0} y={1} width={Math.max(0, sx(r.shift))} height={12} rx={3} />
                  {segs.map((s) => {
                    const wpx = sx(s.m);
                    const x0 = sx(at);
                    at += s.m;
                    // 2px разрыв между сегментами (линия поверхности), чтобы границы читались без контура.
                    return wpx >= 1 ? <rect key={s.cls} className={`st-bar--${s.cls}`} x={x0} y={1} width={Math.max(1, wpx - 2)} height={12} rx={2} /> : null;
                  })}
                </svg>
                <span className="st-row__pct num">{r.off ? '—' : pct(r.load)}</span>
              </button>
            );
          })}
      </div>
    </Section>
  );
}

/* ---------------------------------------------------------------- 3. нераспределённые по причинам */

function Reasons({
  plan,
  orders,
  orderSel,
  onSelectOrder,
  showTip,
  hideTip,
}: {
  plan: Plan;
  orders: UiOrder[];
  orderSel: Selection | null;
  onSelectOrder: (id: string) => void;
  showTip: ShowTip;
  hideTip: () => void;
}) {
  const [boxRef, w] = useWidth<HTMLDivElement>();
  const groups = useMemo(() => {
    const slot = new Map(orders.map((o) => [o.order_id, o.slot.start]));
    const m = new Map<UnassignedReason, string[]>();
    for (const u of plan.unassigned) (m.get(u.reason_code) ?? m.set(u.reason_code, []).get(u.reason_code)!).push(u.order_id);
    for (const ids of m.values()) ids.sort((a, b) => (slot.get(a) ?? '').localeCompare(slot.get(b) ?? ''));
    return [...m.entries()].sort((a, b) => b[1].length - a[1].length);
  }, [plan.unassigned, orders]);
  const total = plan.unassigned.length;
  const pgp = plan.pgp_groups.reduce((n, g) => n + g.order_ids.length, 0);
  const max = Math.max(1, ...groups.map(([, ids]) => ids.length));
  const labelW = Math.round(Math.min(210, Math.max(110, w * 0.42)));
  const trackW = Math.max(40, w - labelW - 70 - 12);
  const sel = orderSel?.id ?? null;

  return (
    <Section title="Нераспределённые по причинам" meta={pgp ? `ещё ${pgp} в группах ПГП` : undefined}>
      <div className="st-reasons">
        <div className="st-hero">
          <span className="st-hero__value num">{total}</span>
          <span className="st-hero__label">{total ? 'не распределено' : 'все наряды распределены'}</span>
        </div>
        <div ref={boxRef} className="st-reasons__bars" onMouseLeave={hideTip}>
          {w > 0 &&
            groups.map(([code, ids]) => {
              const active = !!sel && ids.includes(sel);
              const label = REASON[code] ?? code;
              const select = () => {
                // Повторный клик проходит по нарядам причины по порядку слотов.
                const i = sel ? ids.indexOf(sel) : -1;
                onSelectOrder(ids[(i + 1) % ids.length]);
              };
              return (
                <button
                  key={code}
                  type="button"
                  className={`st-row st-row--reason${active ? ' is-selected' : ''}`}
                  aria-pressed={active}
                  onClick={select}
                  onMouseEnter={(e) =>
                    showTip(e, label, [
                      `${ids.length} из ${total} (${pct(ids.length / Math.max(1, total))})`,
                      ids.slice(0, 4).join(', ') + (ids.length > 4 ? ` и ещё ${ids.length - 4}` : ''),
                      'Клик — выбрать наряд в списке, повторный — следующий',
                    ])
                  }
                  onMouseMove={(e) => showTip(e, label, [])}
                >
                  <span className="st-row__name" style={{ width: labelW }}>
                    {label}
                  </span>
                  <svg width={trackW} height={14} className="st-row__bar" aria-hidden>
                    <rect className="st-bar--reason" x={0} y={1} width={Math.max(2, (ids.length / max) * trackW)} height={12} rx={2} />
                  </svg>
                  <span className="st-row__pct num">
                    {ids.length} <small>{pct(ids.length / Math.max(1, total))}</small>
                  </span>
                </button>
              );
            })}
        </div>
      </div>
    </Section>
  );
}

/* ---------------------------------------------------------------- окно */

export function StatsPanel({
  plan,
  orders,
  staff,
  session,
  staffSel,
  orderSel,
  onSelectStaff,
  onSelectOrder,
}: {
  plan: Plan;
  orders: UiOrder[];
  staff: Map<string, StaffInfo>;
  session: MetricPoint[];
  staffSel: Selection | null;
  orderSel: Selection | null;
  onSelectStaff: (id: string | null) => void;
  onSelectOrder: (id: string) => void;
}) {
  const [tip, setTip] = useState<Tip | null>(null);
  // Пустые lines при движении мыши — только сдвиг уже показанного тултипа.
  const showTip: ShowTip = (e, title, lines) =>
    setTip((t) => (lines.length || !t ? { x: e.clientX, y: e.clientY, title, lines } : { ...t, x: e.clientX, y: e.clientY }));
  const hideTip = () => setTip(null);

  return (
    <div className="stats" onScroll={hideTip}>
      <Dynamics plan={plan} session={session} showTip={showTip} hideTip={hideTip} />
      <Reasons plan={plan} orders={orders} orderSel={orderSel} onSelectOrder={onSelectOrder} showTip={showTip} hideTip={hideTip} />
      <StaffLoad plan={plan} staff={staff} staffSel={staffSel} onSelectStaff={onSelectStaff} showTip={showTip} hideTip={hideTip} />
      <TipView tip={tip} />
    </div>
  );
}
```

## File: frontend/src/components/TopBar.tsx
```typescript
import type { ReactNode } from 'react';
import { Button, SegmentedControl } from './ui';
import { dayLabel, EVENT_TYPE, hhmm } from '../format';
import type { Busy } from '../hooks/usePlan';
import type { DemoStatus, DemoStepItem, Plan, Scenario } from '../types/api';

/** Кнопки демо (выбор дня, «Загрузить день», «+1/+5 событий»). Скрыты: день грузится сам при открытии сайта. */
const SHOW_DEMO_CONTROLS = false;

interface Props {
  demo: DemoStatus | null;
  plan: Plan | null;
  busy: Busy;
  scenario: Scenario;
  onScenario: (s: Scenario) => void;
  onLoad: () => void;
  onStep: (n: number) => void;
  lastStep: DemoStepItem[];
  /** Меню «Окна» (показать/скрыть панели, сброс раскладки). */
  windows?: ReactNode;
  /** Открыть боковую панель «Данные» (справочник сотрудников). */
  onData?: () => void;
  dataOpen?: boolean;
}

export function TopBar({ demo, plan, busy, scenario, onScenario, onLoad, onStep, lastStep, windows, onData, dataOpen }: Props) {
  const remaining = (demo?.remaining_events ?? 0) + (demo?.remaining_triggers ?? 0);
  const canStep = !!demo?.loaded && !!demo.scenario && remaining > 0 && !busy;
  const last = lastStep[lastStep.length - 1];

  return (
    <header className="topbar">
      <div className="topbar__brand">
        <img className="topbar__mark" src="/logo-mark.png" alt="" aria-hidden />
        <h1 className="topbar__title">Ростелеком Диспетчер</h1>
      </div>

      <dl className="topbar__facts">
        <div>
          <dt>День</dt>
          <dd>{dayLabel(demo?.date)}</dd>
        </div>
        <div>
          <dt>Модельное время</dt>
          <dd className="num">{hhmm(plan?.as_of ?? demo?.as_of)}</dd>
        </div>
        <div>
          <dt>Версия плана</dt>
          <dd className="num">{plan ? plan.plan_version : '—'}</dd>
        </div>
        <div>
          <dt>Осталось событий</dt>
          <dd className="num">{demo?.loaded ? remaining : '—'}</dd>
        </div>
      </dl>

      <div className="topbar__last" title={last?.message ?? undefined}>
        {busy === 'step' && 'Пересчитываем план…'}
        {busy !== 'step' && last && (
          <>
            <span className="num">{hhmm(last.timestamp)}</span> {EVENT_TYPE[last.type] ?? last.type}
            {last.accepted ? (last.message ? `: ${last.message}` : '') : ': событие отклонено'}
          </>
        )}
      </div>

      <div className="topbar__actions">
        {SHOW_DEMO_CONTROLS && (
          <>
            <SegmentedControl
              size="s"
              variant="secondary"
              value={scenario}
              onChange={(v) => onScenario(v as Scenario)}
              disabledAll={!!busy}
              items={[
                { index: 'dense', label: '8 окт · плотный' },
                { index: 'example', label: '5 окт · пример' },
              ]}
            />
            <Button
              size="s"
              label={busy === 'load' ? 'Строим план…' : 'Загрузить день'}
              onClick={onLoad}
              disabled={!!busy}
            />
            <Button size="s" variant="outline" label="+1 событие" onClick={() => onStep(1)} disabled={!canStep} />
            <Button size="s" variant="outline" label="+5 событий" onClick={() => onStep(5)} disabled={!canStep} />
          </>
        )}
        {busy === 'init' && !SHOW_DEMO_CONTROLS && <span className="topbar__loading">Строим план…</span>}
        {onData && (
          <Button
            size="s"
            variant={dataOpen ? 'secondary' : 'ghost'}
            label="Данные"
            title="Справочник сотрудников: ФИО, телефоны, табельные номера"
            onClick={onData}
          />
        )}
        <Button
          size="s"
          variant="ghost"
          label="Мобильный вид"
          title="Экран сотрудника: маршрут на день и отметки (откроется в новой вкладке)"
          onClick={() => window.open(`${window.location.pathname}#/m`, '_blank', 'noopener')}
        />
        {windows}
      </div>
    </header>
  );
}
```

## File: frontend/src/components/ui.tsx
```typescript
/*
 * Временные компоненты с API, повторяющим @atomaro/ui-kit (пакет из закрытого реестра).
 * Пропсы взяты из документации Storybook Atomaro (MCP):
 *  Button: size s|m|l|xl, variant primary|secondary|outline|ghost, colorScheme neutral|accent,
 *          label, iconPrefix, iconSuffix, disabled, href
 *  Badge:  label, colorScheme error|info|warning|success|neutral|status-01..06, size s|2xs,
 *          variant primary|secondary|outline|ghost, dot
 *  TabsGroup/TabsItem: value, onChange, size s|m, border; TabsItem — label, index
 *  SegmentedControl/Segment: value, onChange, size s|m|l, variant primary|secondary; Segment — label, index
 * Замена на настоящие компоненты — заменой импорта; лишних пропсов здесь нет.
 */
import type { ReactNode } from 'react';

type Cls = (string | false | null | undefined)[];
const cx = (...c: Cls) => c.filter(Boolean).join(' ');

export interface ButtonProps {
  label?: ReactNode;
  size?: 's' | 'm' | 'l' | 'xl';
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost';
  colorScheme?: 'neutral' | 'accent';
  iconPrefix?: ReactNode;
  iconSuffix?: ReactNode;
  disabled?: boolean;
  onClick?: () => void;
  title?: string;
}

export function Button({
  label,
  size = 'm',
  variant = 'primary',
  colorScheme = 'accent',
  iconPrefix,
  iconSuffix,
  disabled,
  onClick,
  title,
}: ButtonProps) {
  return (
    <button
      type="button"
      className={cx('btn', `btn--${size}`, `btn--${variant}`, `btn--${colorScheme}`)}
      disabled={disabled}
      onClick={onClick}
      title={title}
    >
      {iconPrefix}
      {label}
      {iconSuffix}
    </button>
  );
}

export type BadgeScheme =
  | 'error'
  | 'info'
  | 'warning'
  | 'success'
  | 'neutral'
  | 'status-01'
  | 'status-02'
  | 'status-03'
  | 'status-04'
  | 'status-05'
  | 'status-06';

export interface BadgeProps {
  label?: ReactNode;
  colorScheme?: BadgeScheme;
  size?: 's' | '2xs';
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost';
  dot?: boolean;
}

export function Badge({ label, colorScheme = 'info', size = 's', variant = 'primary', dot }: BadgeProps) {
  return (
    <span className={cx('badge', `badge--${colorScheme}`, `badge--${size}`, `badge--${variant}`)}>
      {dot && <span className="badge__dot" />}
      {label}
    </span>
  );
}

export interface TabsItemProps {
  index: string;
  label: ReactNode;
}

export function TabsGroup({
  value,
  onChange,
  items,
  size = 'm',
  border = true,
}: {
  value: string;
  onChange?: (index: string) => void;
  items: TabsItemProps[];
  size?: 's' | 'm';
  border?: boolean;
}) {
  return (
    <div className={cx('tabs', `tabs--${size}`, border && 'tabs--border')} role="tablist">
      {items.map((it) => (
        <button
          key={it.index}
          type="button"
          role="tab"
          aria-selected={it.index === value}
          className={cx('tabs__item', it.index === value && 'tabs__item--active')}
          onClick={() => onChange?.(it.index)}
        >
          {it.label}
        </button>
      ))}
    </div>
  );
}

export function SegmentedControl({
  value,
  onChange,
  items,
  size = 'm',
  variant = 'primary',
  disabledAll,
}: {
  value: string;
  onChange?: (index: string) => void;
  items: { index: string; label: ReactNode }[];
  size?: 's' | 'm' | 'l';
  variant?: 'primary' | 'secondary';
  disabledAll?: boolean;
}) {
  return (
    <div className={cx('seg', `seg--${size}`, `seg--${variant}`)} role="radiogroup">
      {items.map((it) => (
        <button
          key={it.index}
          type="button"
          role="radio"
          aria-checked={it.index === value}
          disabled={disabledAll}
          className={cx('seg__item', it.index === value && 'seg__item--active')}
          onClick={() => onChange?.(it.index)}
        >
          {it.label}
        </button>
      ))}
    </div>
  );
}
```

## File: frontend/src/components/UnassignedList.tsx
```typescript
import { useRef } from 'react';
import { Badge } from './ui';
import { ACTION, REASON, WORK_TYPE_SHORT, hhmm, window2, windowWithDay } from '../format';
import type { Plan, UiOrder, UiReference } from '../types/api';
import type { Selection } from '../planModel';
import { useScrollToSelected } from '../hooks/useScrollToSelected';

export function UnassignedList({
  plan,
  orders,
  reference,
  selected,
  onSelect,
}: {
  plan: Plan;
  orders: UiOrder[];
  reference: UiReference | null;
  selected: Selection | null;
  onSelect: (id: string) => void;
}) {
  const boxRef = useRef<HTMLDivElement>(null);
  useScrollToSelected(boxRef, selected, 'unassigned');
  const byId = new Map(orders.map((o) => [o.order_id, o]));
  const nodes = new Map((reference?.access_nodes ?? []).map((n) => [n.access_node_id, n]));
  const unassigned = [...plan.unassigned].sort((a, b) =>
    (byId.get(a.order_id)?.slot.start ?? '').localeCompare(byId.get(b.order_id)?.slot.start ?? ''),
  );

  return (
    <div className="list">
      <div className="split scroll" ref={boxRef}>
        <section className="split__col">
          <h3 className="section-title">
            Группы ПГП <span className="num">{plan.pgp_groups.length}</span>
          </h3>
          {!plan.pgp_groups.length && (
            <p className="empty">Групповых проблем не выявлено: нет трёх неисправностей с одного узла за час.</p>
          )}
          <ul className="cards">
            {plan.pgp_groups.map((g) => {
              const node = nodes.get(g.access_node_id);
              return (
                <li key={g.group_id} className="card card--pgp">
                  <div className="card__head">
                    <span className="mono-id">{g.group_id}</span>
                    <Badge variant="secondary" size="2xs" colorScheme="status-01" label={ACTION[g.action]} />
                  </div>
                  <div className="card__grid">
                    <span>Узел доступа</span>
                    <b>
                      {g.access_node_id}
                      {node && <span className="sub">{node.name}</span>}
                    </b>
                    <span>Выявлено</span>
                    <b className="num">{hhmm(g.detected_at)}</b>
                    <span>Нарядов</span>
                    <b className="num">{g.order_ids.length}</b>
                  </div>
                  <ul className="chips">
                    {g.order_ids.map((id) => {
                      const o = byId.get(id);
                      return (
                        <li
                          key={id}
                          data-order={id}
                          title={o?.address}
                          className={selected?.id === id ? 'is-selected' : undefined}
                          onClick={() => onSelect(id)}
                        >
                          {id.slice(-4)}
                          {o && <span className="sub num">{hhmm(o.registered_at)}</span>}
                        </li>
                      );
                    })}
                  </ul>
                  {g.comment && <p className="card__text">{g.comment}</p>}
                </li>
              );
            })}
          </ul>
        </section>

        <section className="split__col">
          <h3 className="section-title">
            Не распределено <span className="num">{unassigned.length}</span>
          </h3>
          {!unassigned.length && <p className="empty">Все наряды дня распределены по маршрутам.</p>}
          <ul className="cards">
            {unassigned.map((u) => {
              const o = byId.get(u.order_id);
              return (
                <li
                  key={u.order_id}
                  data-order={u.order_id}
                  className={`card card--select${selected?.id === u.order_id ? ' is-selected' : ''}`}
                  onClick={() => onSelect(u.order_id)}
                >
                  <div className="card__head">
                    <span className="mono-id">{u.order_id}</span>
                    <Badge variant="secondary" size="2xs" colorScheme="error" label={REASON[u.reason_code]} />
                  </div>
                  {o && (
                    <div className="card__line">
                      {WORK_TYPE_SHORT[o.work_type_code]}, {o.technology}
                      {o.is_repeat && <span className="flag">повт.</span>}
                      <span className="num"> · слот {window2(o.slot)}</span>
                    </div>
                  )}
                  {o && <div className="card__line card__line--muted">{o.address}</div>}
                  <p className="card__text">{u.reason_text}</p>
                  <div className="card__action">
                    <Badge variant="secondary" size="s" colorScheme="info" label={ACTION[u.suggested_action]} />
                    {u.proposed_slot && <span className="num">на {windowWithDay(u.proposed_slot, plan.date)}</span>}
                    {u.since && <span className="card__meta num">с {hhmm(u.since)}</span>}
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      </div>
    </div>
  );
}
```

## File: frontend/src/components/WindowsMenu.tsx
```typescript
import { useEffect, useRef, useState } from 'react';
import { PANELS, type PanelId } from '../dock/layout';

interface Props {
  open: ReadonlySet<PanelId>;
  onToggle: (id: PanelId, show: boolean) => void;
  onReset: () => void;
}

/** Меню «Окна»: чекбоксы показа панелей и сброс раскладки. */
export function WindowsMenu({ open, onToggle, onReset }: Props) {
  const [shown, setShown] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!shown) return;
    const onDown = (e: PointerEvent) => {
      if (!boxRef.current?.contains(e.target as Node)) setShown(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setShown(false);
    };
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [shown]);

  return (
    <div className="winmenu" ref={boxRef}>
      <button
        type="button"
        className="btn btn--s btn--ghost btn--neutral winmenu__trigger"
        aria-haspopup="true"
        aria-expanded={shown}
        aria-label="Окна"
        title="Окна: показать или скрыть панели"
        onClick={() => setShown((v) => !v)}
      >
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
          <rect x="2" y="2.5" width="12" height="11" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.4" />
          <path d="M2 6h12M7.5 6v7.5" stroke="currentColor" strokeWidth="1.4" />
        </svg>
        <span className="winmenu__label">Окна</span>
      </button>
      {shown && (
        <div className="winmenu__pop" role="group" aria-label="Панели экрана">
          <p className="winmenu__head">Показывать панели</p>
          {PANELS.map((p) => (
            <label key={p.id} className="winmenu__item">
              <input type="checkbox" checked={open.has(p.id)} onChange={(e) => onToggle(p.id, e.target.checked)} />
              <span className="winmenu__box" aria-hidden />
              {p.title}
            </label>
          ))}
          <div className="winmenu__sep" />
          <button
            type="button"
            className="winmenu__reset"
            onClick={() => {
              onReset();
              setShown(false);
            }}
          >
            Сбросить раскладку
          </button>
          <p className="winmenu__hint">
            Вкладки перетаскиваются к краям и в другие группы, границы тянутся мышью. Shift при перетаскивании вкладки — плавающее окно.
          </p>
        </div>
      )}
    </div>
  );
}
```

## File: frontend/src/dock/dock.css
```css
/*
 * Тема dockview в стиле Атомаро (светлая, акцент фиолетовый).
 * Все цвета, радиусы и шрифты — из theme/tokens.css; переменные --dv-* переопределяют
 * стандартный вид библиотеки. Класс .dockview-theme-atomaro идёт после .dockview-theme-light.
 */

.dock {
  flex: 1 1 0;
  min-height: 0;
  width: 100%;
}

.dockview-theme-light.dockview-theme-atomaro {
  color-scheme: light;
  font-family: var(--atmr-font-family);

  --dv-group-view-background-color: var(--atmr-bg-surface1);
  --dv-tabs-and-actions-container-background-color: var(--atmr-bg-surface1);
  --dv-tabs-and-actions-container-height: 40px;
  --dv-tabs-and-actions-container-font-size: 14px;
  --dv-tab-font-size: 14px;

  --dv-activegroup-visiblepanel-tab-background-color: transparent;
  --dv-activegroup-hiddenpanel-tab-background-color: transparent;
  --dv-inactivegroup-visiblepanel-tab-background-color: transparent;
  --dv-inactivegroup-hiddenpanel-tab-background-color: transparent;
  --dv-activegroup-visiblepanel-tab-color: var(--atmr-text-primary);
  --dv-activegroup-hiddenpanel-tab-color: var(--atmr-text-secondary);
  --dv-inactivegroup-visiblepanel-tab-color: var(--atmr-text-primary);
  --dv-inactivegroup-hiddenpanel-tab-color: var(--atmr-text-tertiary);
  --dv-tab-divider-color: transparent;
  --dv-tab-margin: 0;
  --dv-tab-border-radius: 0;
  --dv-tab-close-icon-size: 12px;

  --dv-separator-border: transparent;
  --dv-paneview-header-border-color: var(--atmr-border-default);
  --dv-paneview-active-outline-color: var(--atmr-accent);

  --dv-sash-color: transparent;
  --dv-active-sash-color: var(--atmr-accent);
  --dv-active-sash-transition-delay: 0.12s;
  --dv-active-sash-transition-duration: 0.12s;
  --dv-sash-border-radius: var(--atmr-border-radius-full);

  --dv-drag-over-background-color: color-mix(in srgb, var(--atmr-accent) 12%, transparent);
  --dv-drag-over-border-color: var(--atmr-accent);
  --dv-drag-over-border: 2px solid var(--atmr-accent);
  --dv-edge-dock-indicator-color: var(--atmr-accent);
  --dv-dnd-compass-color: var(--atmr-accent);
  --dv-dnd-compass-cell-color: var(--atmr-accent-muted);
  --dv-dnd-compass-active-cell-color: var(--atmr-accent);
  --dv-dnd-compass-edge-cell-color: var(--atmr-accent-muted);
  --dv-smart-guides-color: var(--atmr-accent);
  --dv-smart-guides-preview-color: color-mix(in srgb, var(--atmr-accent) 12%, transparent);

  --dv-border-radius: var(--atmr-border-radius-m);
  --dv-dropdown-border-radius: var(--atmr-border-radius-s);
  --dv-icon-hover-background-color: var(--atmr-bg-surface3);
  --dv-scrollbar-background-color: var(--atmr-border-strong);
  --dv-tabs-container-scrollbar-color: var(--atmr-border-strong);

  --dv-floating-box-shadow: var(--atmr-shadow-m), 0 0 0 1px var(--atmr-border-default);
  --dv-floating-border: 1px solid var(--atmr-border-default);
  --dv-floating-group-border: none;
  --dv-floating-titlebar-background-color: var(--atmr-bg-surface2);
  --dv-floating-titlebar-border-bottom: 1px solid var(--atmr-border-default);

  --dv-context-menu-background-color: var(--atmr-bg-surface1);
  --dv-context-menu-color: var(--atmr-text-primary);
}

/* Промежутки между окнами — цвет страницы, как у прежней сетки карточек */
.dockview-theme-atomaro .dv-dockview {
  background-color: transparent;
}

/* ---------------------------------------------------------------- группа = карточка */
.dockview-theme-atomaro .dv-groupview {
  border-radius: var(--atmr-border-radius-m);
}
.dockview-theme-atomaro .dv-groupview > .dv-tabs-and-actions-container {
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
  padding: 0 var(--atmr-spacing-1x) 0 var(--atmr-spacing-2x);
  border-top-left-radius: var(--atmr-border-radius-m);
  border-top-right-radius: var(--atmr-border-radius-m);
}
.dockview-theme-atomaro .dv-groupview > .dv-content-container {
  border-bottom-left-radius: var(--atmr-border-radius-m);
  border-bottom-right-radius: var(--atmr-border-radius-m);
  overflow: hidden;
}
/* Контент с renderer="always" рисуется в оверлее над группой — скругляем и его */
.dockview-theme-atomaro .dv-render-overlay {
  border-bottom-left-radius: var(--atmr-border-radius-m);
  border-bottom-right-radius: var(--atmr-border-radius-m);
  overflow: hidden;
}

/* ---------------------------------------------------------------- вкладки (как TabsItem Атомаро) */
.dockview-theme-atomaro .dv-tabs-container .dv-tab {
  flex-grow: 0;
  position: relative;
  padding: 0 var(--atmr-spacing-1x) 0 var(--atmr-spacing-2x);
  margin-right: var(--atmr-spacing-2x);
  cursor: pointer;
}
.dockview-theme-atomaro .dv-tabs-container .dv-tab::before {
  content: '';
  position: absolute;
  left: var(--atmr-spacing-2x);
  right: var(--atmr-spacing-1x);
  bottom: 0;
  height: 2px;
  border-radius: 2px;
  background: transparent;
  pointer-events: none;
}
.dockview-theme-atomaro .dv-groupview.dv-active-group .dv-tab.dv-active-tab::before {
  background: var(--atmr-accent);
}
.dockview-theme-atomaro .dv-groupview.dv-inactive-group .dv-tab.dv-active-tab::before {
  background: var(--atmr-border-strong);
}
.dockview-theme-atomaro .dv-tab:hover {
  color: var(--atmr-text-primary);
}
.dockview-theme-atomaro .dv-tab.dv-tab-dragging {
  opacity: 0.6;
}

.dk-tab {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  height: 100%;
  font: 500 14px/20px var(--atmr-font-family);
  white-space: nowrap;
  user-select: none;
}
.dk-tab__title {
  overflow: hidden;
  text-overflow: ellipsis;
}
.dk-tab__close {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: var(--atmr-border-radius-xs);
  background: none;
  color: var(--atmr-text-tertiary);
  cursor: pointer;
  visibility: hidden;
}
.dv-tab.dv-active-tab .dk-tab__close,
.dv-tab:hover .dk-tab__close,
.dk-tab__close:focus-visible {
  visibility: visible;
}
.dk-tab__close:hover {
  background: var(--atmr-bg-surface3);
  color: var(--atmr-text-primary);
}
@media (hover: none) {
  .dk-tab__close {
    visibility: visible;
  }
}

/* пустое место в заголовке группы тоже тянет группу */
.dockview-theme-atomaro .dv-void-container.dv-draggable {
  cursor: grab;
}

/* ---------------------------------------------------------------- кнопки группы */
.dk-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 100%;
  padding-right: var(--atmr-spacing-1x);
}
.dk-action {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: var(--atmr-border-radius-s);
  background: none;
  color: var(--atmr-text-tertiary);
  cursor: pointer;
}
.dk-action:hover {
  background: var(--atmr-accent-muted);
  color: var(--atmr-accent);
}

/* ---------------------------------------------------------------- разделители */
.dockview-theme-atomaro .dv-sash {
  border-radius: var(--dv-sash-border-radius);
}
.dockview-theme-atomaro .dv-split-view-container.dv-horizontal > .dv-sash-container > .dv-sash.dv-enabled:hover,
.dockview-theme-atomaro .dv-split-view-container.dv-vertical > .dv-sash-container > .dv-sash.dv-enabled:hover {
  background-color: var(--atmr-accent);
}

/* ---------------------------------------------------------------- плавающие окна */
.dockview-theme-atomaro .dv-resize-container {
  border-radius: var(--atmr-border-radius-m);
  border: 0;
  box-shadow: var(--dv-floating-box-shadow);
  /* НЕ overflow:hidden: рукоятки ресайза dockview выступают за край и обрезались бы.
     Скругление содержимого даёт .dv-groupview (overflow: hidden ниже). */
  overflow: visible;
  box-sizing: border-box;
  min-width: 240px;
  min-height: 160px;
  /* рамка 4px: контент (в т.ч. .dv-render-overlay карты/Ганта) смещается внутрь и не перекрывает
     внутреннюю половину рукояток ресайза */
  padding: 4px;
  background-color: var(--atmr-bg-surface2);
}
.dockview-theme-atomaro .dv-resize-container .dv-groupview {
  overflow: hidden;
}
/* Рукоятки ресайза dockview (штатно 4px, top:-2px). Контент групп с renderer="always" рисуется
   в .dv-render-overlay (z-index выше контейнера окна) и перекрывает рукоятки, а штатный z-index
   рукояток невалиден (циклическая var(--dv-overlay-z-index)) -> auto. Делаем 8px (±4 от края,
   dockview считает размер от положения курсора относительно края) и 14x14 по углам. */
.dockview-theme-atomaro .dv-resize-container [class*='dv-resize-handle-'] {
  z-index: 50;
}
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-top,
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-bottom {
  height: 8px;
  width: calc(100% - 16px);
  left: 8px;
}
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-top { top: -4px; }
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-bottom { bottom: -4px; }
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-left,
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-right {
  width: 8px;
  height: calc(100% - 16px);
  top: 8px;
}
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-left { left: -4px; }
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-right { right: -4px; }
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-topleft,
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-topright,
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-bottomleft,
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-bottomright {
  width: 14px;
  height: 14px;
}
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-topleft { top: -5px; left: -5px; }
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-topright { top: -5px; right: -5px; }
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-bottomleft { bottom: -5px; left: -5px; }
.dockview-theme-atomaro .dv-resize-container .dv-resize-handle-bottomright { bottom: -5px; right: -5px; }

/* ---------------------------------------------------------------- подсветка места при перетаскивании */
.dockview-theme-atomaro .dv-drop-target-anchor {
  border-radius: var(--atmr-border-radius-s);
}

/* ---------------------------------------------------------------- выпадающий список скрытых вкладок */
.dockview-theme-atomaro .dv-tabs-overflow-container {
  border-radius: var(--atmr-border-radius-s);
  box-shadow: var(--atmr-shadow-m), 0 0 0 1px var(--atmr-border-default);
  background: var(--atmr-bg-surface1);
}

/* ---------------------------------------------------------------- содержимое панелей */
.dock-panel {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--atmr-bg-surface1);
  color: var(--atmr-text-primary);
  font: var(--atmr-font-body-m);
}
.dock-panel > .list,
.dock-panel > .panel {
  flex: 1 1 auto;
  min-height: 0;
}
.dock-panel > .panel {
  border-radius: 0;
  box-shadow: none;
}
.dock-panel .gantt__head {
  padding-top: var(--atmr-spacing-2x);
}

.dk-watermark {
  height: 100%;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: var(--atmr-spacing-2x);
  padding: var(--atmr-spacing-6x);
  text-align: center;
  color: var(--atmr-text-secondary);
  font: var(--atmr-font-body-s);
}
.dk-watermark h2 {
  font: var(--atmr-font-heading-h4);
  color: var(--atmr-text-primary);
}
.dk-watermark__actions {
  display: flex;
  gap: var(--atmr-spacing-2x);
  margin-top: var(--atmr-spacing-2x);
}

/* ---------------------------------------------------------------- меню «Окна» в шапке */
.winmenu {
  position: relative;
}
.winmenu__trigger svg {
  flex: none;
}
/* 1366: в шапке тесно — остаётся только иконка (подпись в title/aria-label) */
@media (max-width: 1440px) {
  .winmenu__label {
    display: none;
  }
  .winmenu__trigger.btn--s {
    padding: 0 var(--atmr-spacing-2x);
  }
}
.btn--ghost.btn--neutral {
  background: transparent;
  color: var(--atmr-text-primary);
}
.btn--ghost.btn--neutral:hover,
.winmenu__trigger[aria-expanded='true'] {
  background: var(--atmr-bg-surface3);
}
.winmenu__pop {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 3000;
  width: 280px;
  padding: var(--atmr-spacing-2x);
  border-radius: var(--atmr-border-radius-m);
  background: var(--atmr-bg-surface1);
  box-shadow: var(--atmr-shadow-m), 0 0 0 1px var(--atmr-border-default);
  display: grid;
  gap: 2px;
}
.winmenu__head {
  padding: var(--atmr-spacing-1x) var(--atmr-spacing-2x);
  font: 500 12px/16px var(--atmr-font-family);
  color: var(--atmr-text-tertiary);
}
.winmenu__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  min-height: 36px;
  padding: 0 var(--atmr-spacing-2x);
  border-radius: var(--atmr-border-radius-s);
  font: var(--atmr-font-body-s);
  cursor: pointer;
}
.winmenu__item:hover {
  background: var(--atmr-bg-surface2);
}
.winmenu__item input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.winmenu__box {
  flex: none;
  width: 18px;
  height: 18px;
  border: 1.5px solid var(--atmr-border-strong);
  border-radius: var(--atmr-border-radius-xs);
  background: var(--atmr-bg-surface1);
  display: grid;
  place-items: center;
}
.winmenu__item input:checked + .winmenu__box {
  border-color: var(--atmr-accent);
  background: var(--atmr-accent);
}
.winmenu__item input:checked + .winmenu__box::after {
  content: '';
  width: 9px;
  height: 5px;
  margin-top: -2px;
  border-left: 2px solid var(--atmr-accent-on);
  border-bottom: 2px solid var(--atmr-accent-on);
  transform: rotate(-45deg);
}
.winmenu__item input:focus-visible + .winmenu__box {
  outline: 2px solid var(--atmr-accent);
  outline-offset: 2px;
}
.winmenu__sep {
  height: 1px;
  margin: var(--atmr-spacing-1x) 0;
  background: var(--atmr-border-default);
}
.winmenu__reset {
  height: 36px;
  padding: 0 var(--atmr-spacing-2x);
  border: 0;
  border-radius: var(--atmr-border-radius-s);
  background: none;
  color: var(--atmr-accent);
  font: 500 13px/18px var(--atmr-font-family);
  text-align: left;
  cursor: pointer;
}
.winmenu__reset:hover {
  background: var(--atmr-accent-muted);
}
.winmenu__hint {
  padding: var(--atmr-spacing-1x) var(--atmr-spacing-2x) var(--atmr-spacing-1x);
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
}
```

## File: frontend/src/dock/DockPanels.tsx
```typescript
/*
 * Панели док-раскладки. Данные и выбор живут в App и приходят через контекст:
 * панель можно закрыть, перенести или открепить — выбор и масштаб карты не теряются.
 */
import { createContext, useContext, useEffect, useState, type FunctionComponent, type PointerEvent } from 'react';
import type { IDockviewHeaderActionsProps, IDockviewPanelHeaderProps, IDockviewPanelProps, IWatermarkPanelProps } from 'dockview-react';
import { OrdersTable } from '../components/OrdersTable';
import { LateRiskList } from '../components/LateRiskList';
import { UnassignedList } from '../components/UnassignedList';
import { DispatchMap, type MapMemory } from '../components/DispatchMap';
import { GanttChart } from '../components/GanttChart';
import { ScenariosPanel as ScenariosView } from '../components/ScenariosPanel';
import { StatsPanel as StatsView } from '../components/StatsPanel';
import type { usePlan } from '../hooks/usePlan';
import type { RouteMode, Selection, Source, StaffInfo } from '../planModel';
import type { Route, ServiceCenter } from '../types/api';
import { PANEL_TITLE, dockPositionFor, type PanelId } from './layout';

const EMPTY_SC: ServiceCenter[] = [];
const EMPTY_ROUTES: Route[] = [];

export interface DockData {
  s: ReturnType<typeof usePlan>;
  staff: Map<string, StaffInfo>;
  orderSel: Selection | null;
  staffSel: Selection | null;
  routeMode: RouteMode;
  setRouteMode: (m: RouteMode) => void;
  selectOrder: (id: string, source: Source) => void;
  selectStaff: (id: string | null, source: Source, fit: boolean) => void;
  mapMemory: MapMemory;
  counts: Partial<Record<PanelId, { n: number; warn?: boolean }>>;
  openPanel: (id: PanelId) => void;
}

export const DockDataContext = createContext<DockData | null>(null);
function useDock(): DockData {
  const d = useContext(DockDataContext);
  if (!d) throw new Error('DockDataContext missing');
  return d;
}

/* ---------------------------------------------------------------- содержимое панелей */

function Welcome() {
  const { s } = useDock();
  return (
    <div className="welcome">
      {s.busy === 'load' ? (
        <>
          <h2>Строим план на день</h2>
          <p>Распределяем наряды, зарегистрированные к 08:30, по маршрутам сотрудников. Обычно около 15 секунд.</p>
        </>
      ) : s.busy === 'init' ? (
        <p>Подключаемся к сервису планирования…</p>
      ) : (
        <>
          <h2>День не загружен</h2>
          <p>
            Выберите демо-день и нажмите «Загрузить день»: сервис возьмёт наряды на 08:30, построит маршруты, а кнопки
            «+1» и «+5» будут проигрывать события дня — новые наряды, отмены, задержки и выбытие сотрудников.
          </p>
        </>
      )}
    </div>
  );
}

function EmptyNote({ text }: { text: string }) {
  return <p className="empty">{text}</p>;
}

function OrdersPanel() {
  const { s, orderSel, selectOrder } = useDock();
  return (
    <div className="dock-panel">
      {s.plan ? <OrdersTable orders={s.orders} selected={orderSel} onSelect={(id) => selectOrder(id, 'table')} /> : <Welcome />}
    </div>
  );
}

function LatePanel() {
  const { s, orderSel, selectOrder } = useDock();
  return (
    <div className="dock-panel">
      {s.plan ? (
        <LateRiskList plan={s.plan} orders={s.orders} selected={orderSel} onSelect={(id) => selectOrder(id, 'late')} />
      ) : (
        <EmptyNote text="Наряды с риском опоздания появятся после загрузки дня." />
      )}
    </div>
  );
}

function UnassignedPanel() {
  const { s, orderSel, selectOrder } = useDock();
  return (
    <div className="dock-panel">
      {s.plan ? (
        <UnassignedList
          plan={s.plan}
          orders={s.orders}
          reference={s.reference}
          selected={orderSel}
          onSelect={(id) => selectOrder(id, 'unassigned')}
        />
      ) : (
        <EmptyNote text="Нераспределённые наряды и группы ПГП появятся после загрузки дня." />
      )}
    </div>
  );
}

function ScenariosPanel() {
  const { s, orderSel, selectOrder } = useDock();
  return (
    <div className="dock-panel">
      {s.plan ? (
        <ScenariosView
          date={s.demo?.date ?? s.plan.date}
          planVersion={s.plan.plan_version}
          reference={s.reference}
          selected={orderSel}
          onSelect={(id) => selectOrder(id, 'scenarios')}
        />
      ) : (
        <EmptyNote text="Сценарии «меньше сотрудников» и «запас АО» считаются по загруженному дню." />
      )}
    </div>
  );
}

function MapPanel() {
  const { s, staff, staffSel, orderSel, routeMode, setRouteMode, selectOrder, selectStaff, mapMemory } = useDock();
  return (
    <div className="dock-panel map-panel">
      <DispatchMap
        memory={mapMemory}
        serviceCenters={s.reference?.service_centers ?? EMPTY_SC}
        orders={s.orders}
        routes={s.plan?.routes ?? EMPTY_ROUTES}
        staff={staff}
        day={s.plan?.date}
        staffSel={staffSel}
        orderSel={orderSel}
        routeMode={routeMode}
        onRouteMode={setRouteMode}
        onSelectStaff={(id, fit) => selectStaff(id, 'map', fit)}
        onSelectOrder={(id) => selectOrder(id, 'map')}
      />
    </div>
  );
}

function GanttPanel() {
  const { s, staff, staffSel, orderSel, selectOrder, selectStaff } = useDock();
  return (
    <div className="dock-panel">
      {s.plan ? (
        <GanttChart
          plan={s.plan}
          orders={s.orders}
          staff={staff}
          serviceCenters={s.reference?.service_centers ?? EMPTY_SC}
          staffSel={staffSel}
          orderSel={orderSel}
          onSelectStaff={(id, fit) => selectStaff(id, 'gantt', fit)}
          onSelectOrder={(id) => selectOrder(id, 'gantt')}
        />
      ) : (
        <section className="panel gantt gantt--empty">
          <p>Визиты, перерывы и заезды в СЦ по сотрудникам на шкале смены появятся после загрузки дня.</p>
        </section>
      )}
    </div>
  );
}

function StatsPanel() {
  const { s, staff, staffSel, orderSel, selectOrder, selectStaff } = useDock();
  return (
    <div className="dock-panel">
      {s.plan ? (
        <StatsView
          plan={s.plan}
          orders={s.orders}
          staff={staff}
          session={s.sessionMetrics}
          staffSel={staffSel}
          orderSel={orderSel}
          onSelectStaff={(id) => selectStaff(id, 'stats', !!id)}
          onSelectOrder={(id) => selectOrder(id, 'stats')}
        />
      ) : s.busy === 'load' || s.busy === 'init' ? (
        <Welcome />
      ) : (
        <EmptyNote text="Загрузите день: здесь появятся динамика плана по версиям, загрузка сотрудников и причины нераспределённых нарядов." />
      )}
    </div>
  );
}

export const DOCK_COMPONENTS: Record<PanelId, FunctionComponent<IDockviewPanelProps>> = {
  orders: OrdersPanel,
  late: LatePanel,
  unassigned: UnassignedPanel,
  scenarios: ScenariosPanel,
  map: MapPanel,
  gantt: GanttPanel,
  stats: StatsPanel,
};

/* ---------------------------------------------------------------- вкладка */

const CloseIcon = () => (
  <svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
    <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
  </svg>
);

/** Вкладка в стиле TabsItem Атомаро: название, счётчик, крестик; средняя кнопка мыши тоже закрывает. */
export function DockTab({ api }: IDockviewPanelHeaderProps) {
  const { counts } = useDock();
  const id = api.id as PanelId;
  const count = counts[id];
  const title = PANEL_TITLE[id] ?? api.title ?? id;
  const close = () => api.close();
  const onPointerUp = (e: PointerEvent) => {
    if (e.button === 1) {
      e.preventDefault();
      close();
    }
  };
  return (
    <div className="dk-tab" title={title} onPointerUp={onPointerUp}>
      <span className="dk-tab__title">{title}</span>
      {count && <span className={`tabs__count${count.warn ? ' tabs__count--warn' : ''}`}>{count.n}</span>}
      <button
        type="button"
        className="dk-tab__close"
        aria-label={`Закрыть панель «${title}»`}
        title="Закрыть панель"
        onPointerDown={(e) => e.preventDefault()}
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          close();
        }}
      >
        <CloseIcon />
      </button>
    </div>
  );
}

/* ---------------------------------------------------------------- действия группы */

const FloatIcon = () => (
  <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
    <rect x="2.5" y="5.5" width="8" height="8" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.4" />
    <path d="M6 3.5V3a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 .5.5v7a.5.5 0 0 1-.5.5H13" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
  </svg>
);
const DockIcon = () => (
  <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
    <rect x="2.5" y="2.5" width="11" height="11" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.4" />
    <path d="M9.5 2.5v11" stroke="currentColor" strokeWidth="1.4" />
  </svg>
);
const MaxIcon = () => (
  <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
    <path d="M9.5 2.5h4v4M6.5 13.5h-4v-4M13.5 2.5 9 7M2.5 13.5 7 9" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);
const RestoreIcon = () => (
  <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
    <path d="M13.5 6.5h-4v-4M2.5 9.5h4v4M9.5 6.5 14 2M6.5 9.5 2 14" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

/** Справа в заголовке группы: открепить в плавающее окно / закрепить обратно, развернуть / вернуть. */
export function GroupActions({ api, containerApi, group, panels }: IDockviewHeaderActionsProps) {
  const [loc, setLoc] = useState(api.location.type);
  const [max, setMax] = useState(() => api.isMaximized());
  useEffect(() => {
    const a = api.onDidLocationChange((e) => setLoc(e.location.type));
    const b = containerApi.onDidMaximizedGroupChange(() => setMax(api.isMaximized()));
    setLoc(api.location.type);
    return () => {
      a.dispose();
      b.dispose();
    };
  }, [api, containerApi]);

  const floating = loc === 'floating';
  const float = () => {
    const w = Math.min(720, Math.round(containerApi.width * 0.5));
    const h = Math.min(520, Math.round(containerApi.height * 0.6));
    containerApi.addFloatingGroup(group, {
      x: Math.max(16, Math.round((containerApi.width - w) / 2)),
      y: Math.max(16, Math.round((containerApi.height - h) / 3)),
      width: w,
      height: h,
    });
  };
  const dock = () => api.moveTo({ position: dockPositionFor(panels.map((p) => p.id)) });

  return (
    <div className="dk-actions">
      {loc === 'grid' && (
        <button
          type="button"
          className="dk-action"
          title={max ? 'Вернуть раскладку' : 'Развернуть на весь экран'}
          aria-label={max ? 'Вернуть раскладку' : 'Развернуть на весь экран'}
          onClick={() => (max ? api.exitMaximized() : api.maximize())}
        >
          {max ? <RestoreIcon /> : <MaxIcon />}
        </button>
      )}
      {(loc === 'grid' || floating) && !max && (
        <button
          type="button"
          className="dk-action"
          title={floating ? 'Закрепить в раскладке' : 'Открепить в плавающее окно'}
          aria-label={floating ? 'Закрепить в раскладке' : 'Открепить в плавающее окно'}
          onClick={floating ? dock : float}
        >
          {floating ? <DockIcon /> : <FloatIcon />}
        </button>
      )}
    </div>
  );
}

/** Все панели закрыты — подсказываем, как вернуть. */
export function DockWatermark(_: IWatermarkPanelProps) {
  const { openPanel } = useDock();
  return (
    <div className="dk-watermark">
      <h2>Все окна закрыты</h2>
      <p>Откройте нужные панели через меню «Окна» в шапке или верните раскладку по умолчанию.</p>
      <div className="dk-watermark__actions">
        <button type="button" className="btn btn--s btn--secondary btn--accent" onClick={() => openPanel('orders')}>
          Открыть «Наряды»
        </button>
        <button type="button" className="btn btn--s btn--secondary btn--accent" onClick={() => openPanel('map')}>
          Открыть «Карту»
        </button>
      </div>
    </div>
  );
}
```

## File: frontend/src/dock/layout.ts
```typescript
/*
 * Раскладка окон диспетчера на dockview: состав панелей, раскладка по умолчанию,
 * сохранение в localStorage и показ закрытых панелей.
 */
import type { AddPanelOptions, DockviewApi, IDockviewPanel, Position, SerializedDockview } from 'dockview-react';

export type PanelId = 'orders' | 'late' | 'unassigned' | 'scenarios' | 'map' | 'gantt' | 'stats';
export const LIST_PANELS: PanelId[] = ['orders', 'late', 'unassigned', 'scenarios'];
export const PANELS: { id: PanelId; title: string }[] = [
  { id: 'orders', title: 'Наряды' },
  { id: 'late', title: 'Риск опоздания' },
  { id: 'unassigned', title: 'Не распределено / ПГП' },
  { id: 'scenarios', title: 'Сценарии' },
  { id: 'map', title: 'Карта' },
  { id: 'gantt', title: 'Загрузка по времени' },
  // В раскладке по умолчанию нет: открывается из меню «Окна», чтобы не перегружать экран.
  { id: 'stats', title: 'Статистика' },
];
export const PANEL_TITLE = Object.fromEntries(PANELS.map((p) => [p.id, p.title])) as Record<PanelId, string>;
const isPanelId = (v: unknown): v is PanelId => typeof v === 'string' && v in PANEL_TITLE;

/** Версия в ключе: меняем при смене состава панелей, старые раскладки тогда игнорируются. */
const STORAGE_KEY = 'rtk-dispatch.layout.v3';

function panelOptions(id: PanelId, position?: AddPanelOptions['position'], extra?: Partial<AddPanelOptions>): AddPanelOptions {
  return { id, component: id, title: PANEL_TITLE[id], ...(position ? { position } : {}), ...extra } as AddPanelOptions;
}

/** Слева списки и «Сценарии» во вкладках, справа карта, снизу на всю ширину Гант (пропорции прежней сетки). */
export function buildDefaultLayout(api: DockviewApi) {
  api.clear();
  const w = api.width;
  const h = api.height;
  api.addPanel(panelOptions('orders'));
  api.addPanel(panelOptions('late', { referencePanel: 'orders', direction: 'within' }, { inactive: true }));
  api.addPanel(panelOptions('unassigned', { referencePanel: 'orders', direction: 'within' }, { inactive: true }));
  api.addPanel(panelOptions('scenarios', { referencePanel: 'orders', direction: 'within' }, { inactive: true }));
  api.addPanel(
    panelOptions('map', { referencePanel: 'orders', direction: 'right' }, w > 0 ? { initialWidth: Math.round(w * 0.4) } : {}),
  );
  api.addPanel(panelOptions('gantt', { direction: 'below' }, h > 0 ? { initialHeight: Math.round(h * 0.43) } : {}));
  api.getPanel('orders')?.api.setActive();
}

export function saveLayout(api: DockviewApi) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(api.toJSON()));
  } catch {
    /* приватный режим или переполнение — раскладка просто не запомнится */
  }
}

export function forgetLayout() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* нет доступа к хранилищу */
  }
}

/** Восстанавливает сохранённую раскладку; false — сохранённой нет или она битая. */
export function restoreLayout(api: DockviewApi): boolean {
  let raw: string | null = null;
  try {
    raw = localStorage.getItem(STORAGE_KEY);
  } catch {
    return false;
  }
  if (!raw) return false;
  try {
    const data = JSON.parse(raw) as SerializedDockview;
    const panels = Object.values(data?.panels ?? {});
    if (!data?.grid || !panels.length) throw new Error('empty layout');
    // Незнакомые компоненты (раскладка от другой версии экрана) — не восстанавливаем.
    if (!panels.every((p) => isPanelId(p.id) && (p.contentComponent ?? p.id) === p.id)) throw new Error('unknown panel');
    api.fromJSON(data);
    if (!api.panels.length) throw new Error('nothing restored');
    return true;
  } catch {
    try {
      api.clear();
    } catch {
      /* ignore */
    }
    forgetLayout();
    return false;
  }
}

/** Куда вернуть закрытую панель: к «своим» панелям, иначе к краю экрана. */
function defaultPosition(api: DockviewApi, id: PanelId): AddPanelOptions['position'] | undefined {
  const has = (p: PanelId) => !!api.getPanel(p);
  if (!api.panels.length) return undefined;
  if (LIST_PANELS.includes(id)) {
    const sibling = LIST_PANELS.find((p) => p !== id && has(p));
    if (sibling) return { referencePanel: sibling, direction: 'within' };
    if (has('map')) return { referencePanel: 'map', direction: 'left' };
    return { direction: 'left' };
  }
  if (id === 'stats') {
    if (has('map')) return { referencePanel: 'map', direction: 'right' };
    return { direction: 'right' };
  }
  if (id === 'map') {
    const list = LIST_PANELS.find(has);
    if (list) return { referencePanel: list, direction: 'right' };
    return { direction: 'right' };
  }
  return { direction: 'below' };
}

export function openPanel(api: DockviewApi, id: PanelId): IDockviewPanel {
  const existing = api.getPanel(id);
  if (existing) {
    existing.api.setActive();
    return existing;
  }
  const extra: Partial<AddPanelOptions> = {};
  if (id === 'gantt' && api.height > 0) extra.initialHeight = Math.round(api.height * 0.4);
  if (id === 'map' && api.width > 0) extra.initialWidth = Math.round(api.width * 0.4);
  if (id === 'stats' && api.width > 0) extra.initialWidth = Math.round(Math.min(560, Math.max(340, api.width * 0.3)));
  if (LIST_PANELS.includes(id) && api.width > 0 && !LIST_PANELS.some((p) => p !== id && api.getPanel(p))) {
    extra.initialWidth = Math.round(api.width * 0.55);
  }
  return api.addPanel(panelOptions(id, defaultPosition(api, id), extra));
}

/** Виден ли контент панели: она открыта, активна в своей группе и не скрыта развёрнутой чужой группой. */
export function isPanelShown(api: DockviewApi, id: PanelId): boolean {
  const p = api.getPanel(id);
  if (!p) return false;
  if (p.group.activePanel?.id !== id) return false;
  if (api.hasMaximizedGroup() && !p.group.api.isMaximized()) return false;
  return true;
}

/**
 * Показывает панель, на которую ведёт связка выбора: закрытую открывает, скрытую во вкладке активирует.
 * Не переключает вкладку в группе панели-источника — иначе пользователь потерял бы то, где кликнул.
 */
export function revealPanel(api: DockviewApi, id: PanelId, source?: PanelId) {
  const p = api.getPanel(id);
  if (!p) {
    openPanel(api, id);
    return;
  }
  if (p.group.activePanel?.id === id) return;
  const src = source ? api.getPanel(source) : undefined;
  if (src && src.group === p.group) return;
  p.api.setActive();
}

/** Куда закрепить плавающую группу: по самой «главной» панели в ней. */
export function dockPositionFor(ids: string[]): Position {
  if (ids.includes('gantt')) return 'bottom';
  if (ids.includes('map') || ids.includes('stats')) return 'right';
  return 'left';
}
```

## File: frontend/src/hooks/usePlan.ts
```typescript
import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiFailure } from '../api/client';
import type { DemoStatus, DemoStepItem, Plan, Scenario, UiOrder, UiReference } from '../types/api';
import { pushMetric, type MetricPoint } from '../planModel';

export type Busy = null | 'init' | 'load' | 'step' | 'refresh';

export interface PlanState {
  demo: DemoStatus | null;
  plan: Plan | null;
  orders: UiOrder[];
  reference: UiReference | null;
  busy: Busy;
  error: string | null;
  lastStep: DemoStepItem[];
  /** Метрики версий плана, увиденных в этой сессии (запасной источник графика динамики без журнала БД). */
  sessionMetrics: MetricPoint[];
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** План дня + наряды со статусами. После шага событий опрашивает план, пока версия
 *  не дойдёт до той, что вернул сервер (до 6 попыток). */
export function usePlan() {
  const [state, setState] = useState<PlanState>({
    demo: null,
    plan: null,
    orders: [],
    reference: null,
    busy: 'init',
    error: null,
    lastStep: [],
    sessionMetrics: [],
  });
  const busyRef = useRef(false);

  const patch = (p: Partial<PlanState>) => setState((s) => ({ ...s, ...p }));
  const fail = (e: unknown) => patch({ error: e instanceof ApiFailure || e instanceof Error ? e.message : String(e) });

  const fetchDay = useCallback(async (date: string, minVersion = 0, withReference = false) => {
    for (let attempt = 0; attempt < 6; attempt++) {
      const [plan, orders, reference] = await Promise.all([
        api.getPlan(date),
        api.getOrders(date),
        withReference ? api.getReference() : Promise.resolve(null),
      ]);
      // План и наряды должны быть одной версии: иначе вкладка «Риск опоздания» (alerts плана)
      // и флаги late_risk в списке нарядов разойдутся после «+N событий».
      const fresh = plan.plan_version >= minVersion && orders.plan_version === plan.plan_version;
      if (fresh || attempt === 5) {
        setState((s) => ({
          ...s,
          plan,
          orders: orders.orders,
          reference: reference ?? s.reference,
          sessionMetrics: pushMetric(s.sessionMetrics, plan),
        }));
        return;
      }
      await sleep(500);
    }
  }, []);

  /** true — fn выполнилась без ошибки (ошибка уходит в state.error). */
  const run = useCallback(async (busy: Busy, fn: () => Promise<void>): Promise<boolean> => {
    if (busyRef.current) return false;
    busyRef.current = true;
    patch({ busy, error: null });
    try {
      await fn();
      return true;
    } catch (e) {
      fail(e);
      return false;
    } finally {
      busyRef.current = false;
      patch({ busy: null });
    }
  }, []);

  useEffect(() => {
    void run('init', async () => {
      const demo = await api.demoStatus();
      patch({ demo });
      if (demo.loaded && demo.date) {
        await fetchDay(demo.date, 0, true);
      } else {
        // Демо-день ещё не загружен: грузим плотный сценарий автоматически при открытии сайта.
        const res = await api.demoLoad('dense');
        patch({ demo: res.demo, lastStep: [] });
        await fetchDay(res.date, res.plan_version, true);
      }
    });
  }, [run, fetchDay]);

  const loadDay = useCallback(
    (scenario: Scenario) =>
      run('load', async () => {
        const res = await api.demoLoad(scenario);
        // Повторная загрузка начинает день заново: версии прежней сессии больше не относятся к плану.
        patch({ demo: res.demo, lastStep: [], sessionMetrics: [] });
        await fetchDay(res.date, res.plan_version, true);
      }),
    [run, fetchDay],
  );

  const step = useCallback(
    (count: number) =>
      run('step', async () => {
        const res = await api.demoStep(count);
        patch({ demo: res.demo, lastStep: res.sent });
        if (res.demo.date) await fetchDay(res.demo.date, res.plan_version);
      }),
    [run, fetchDay],
  );

  const refresh = useCallback(
    () =>
      run('refresh', async () => {
        if (state.demo?.date) await fetchDay(state.demo.date);
      }),
    [run, fetchDay, state.demo?.date],
  );

  /** Событие уже отправлено сервером (наряд создан/отменён из панели «Данные»):
   *  показываем итог в верхней строке и подтягиваем план нужной версии. */
  const applyEvent = useCallback(
    (item: DemoStepItem) =>
      run('refresh', async () => {
        patch({ lastStep: [item] });
        if (state.demo?.date) await fetchDay(state.demo.date, item.plan_version ?? 0);
      }),
    [run, fetchDay, state.demo?.date],
  );

  return { ...state, loadDay, step, refresh, applyEvent };
}
```

## File: frontend/src/hooks/useScrollToSelected.ts
```typescript
import { useEffect, useRef, type RefObject } from 'react';
import type { Selection } from '../planModel';

/** Прокручивает только сам контейнер так, чтобы элемент оказался по центру, если он вне видимой области.
 *  Не scrollIntoView: тот двигает и всех предков (.app, вкладки dockview), а у скрытой во вкладке
 *  панели — сдвигает всю страницу (шапка уезжает вверх). */
function scrollWithin(box: HTMLElement, node: HTMLElement) {
  if (!box.clientHeight) return;
  const b = box.getBoundingClientRect();
  const n = node.getBoundingClientRect();
  if (n.top >= b.top && n.bottom <= b.bottom) return;
  const target = box.scrollTop + (n.top - b.top) - (box.clientHeight - n.height) / 2;
  box.scrollTop = Math.max(0, Math.min(target, box.scrollHeight - box.clientHeight));
}

/** Прокручивает контейнер к элементу [data-order=id], когда наряд выбран в другом месте экрана.
 *  Если элемента пока нет (фильтр сбрасывается), пробует после следующих рендеров. */
export function useScrollToSelected(box: RefObject<HTMLElement>, sel: Selection | null, own: Selection['source']) {
  const pending = useRef<string | null>(null);
  useEffect(() => {
    if (sel && sel.source !== own) pending.current = sel.id;
  }, [sel?.nonce]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    const id = pending.current;
    const el = box.current;
    if (!id || !el) return;
    const node = el.querySelector<HTMLElement>(`[data-order="${CSS.escape(id)}"]`);
    if (node) {
      scrollWithin(el, node);
      pending.current = null;
    }
  });
}
```

## File: frontend/src/hooks/useStaffDirectory.ts
```typescript
// Справочник сотрудников с ФИО (/ui/directory/staff): кэш на всё приложение,
// map staff_id → «Фамилия И. О.», перезагрузка после правки.
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, ApiFailure } from '../api/client';
import type {
  EquipmentBulkInput,
  EquipmentBulkResult,
  EquipmentScope,
  RecommendedBackpack,
  StaffProfile,
  StaffProfileInput,
  StaffProfileSaved,
} from '../types/api';

export interface StaffDirectoryState {
  items: StaffProfile[];
  /** staff_id → «Фамилия И. О.»; пустая, пока справочник не загружен. */
  names: Map<string, string>;
  loading: boolean;
  error: string | null;
  /** false — сервер не смог записать файл, правки живут до перезапуска. */
  persisted: boolean;
  reload: () => Promise<void>;
  update: (staffId: string, body: StaffProfileInput) => Promise<StaffProfileSaved>;
  create: (body: StaffProfileInput) => Promise<StaffProfileSaved>;
  /** Подсказка «рекомендуемый рюкзак» из последнего сценария «Рюкзак АО»; null — сценарий не запускали. */
  recommended: RecommendedBackpack | null;
  /** Рюкзаки АО изменены после последней загрузки дня: нужна пересборка. */
  equipmentPending: boolean;
  clearEquipmentPending: () => void;
  resetEquipment: (staffId: string) => Promise<StaffProfileSaved>;
  bulkEquipment: (body: EquipmentBulkInput) => Promise<EquipmentBulkResult>;
  bulkResetEquipment: (scope: EquipmentScope) => Promise<EquipmentBulkResult>;
}

type Loaded = { items: StaffProfile[]; persisted: boolean; recommended: RecommendedBackpack | null };

/** Кэш модуля: экраны, смонтированные позже, не ждут повторной загрузки. */
let cache: Loaded | null = null;
let inflight: Promise<Loaded> | null = null;

function fetchDirectory(force = false) {
  if (cache && !force) return Promise.resolve(cache);
  if (!inflight || force) {
    inflight = api
      .directoryStaff()
      .then((r) => (cache = { items: r.staff, persisted: r.persisted, recommended: r.recommended_backpack ?? null }))
      .finally(() => (inflight = null));
  }
  return inflight;
}

const message = (e: unknown) => (e instanceof ApiFailure || e instanceof Error ? e.message : String(e));

export function useStaffDirectory(): StaffDirectoryState {
  const [items, setItems] = useState<StaffProfile[]>(cache?.items ?? []);
  const [persisted, setPersisted] = useState(cache?.persisted ?? true);
  const [recommended, setRecommended] = useState<RecommendedBackpack | null>(cache?.recommended ?? null);
  const [equipmentPending, setEquipmentPending] = useState(false);
  const [loading, setLoading] = useState(!cache);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (force: boolean) => {
    setLoading(true);
    try {
      const r = await fetchDirectory(force);
      setItems(r.items);
      setPersisted(r.persisted);
      setRecommended(r.recommended);
      setError(null);
    } catch (e) {
      setError(message(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(false);
  }, [load]);

  const reload = useCallback(() => load(true), [load]);

  const update = useCallback(
    async (staffId: string, body: StaffProfileInput) => {
      const saved = await api.directoryUpdate(staffId, body);
      if (saved.applies_from_next_replan) setEquipmentPending(true);
      await reload();
      return saved;
    },
    [reload],
  );

  const create = useCallback(
    async (body: StaffProfileInput) => {
      const saved = await api.directoryCreate(body);
      await reload();
      return saved;
    },
    [reload],
  );

  const resetEquipment = useCallback(
    async (staffId: string) => {
      const saved = await api.directoryEquipmentReset(staffId);
      setEquipmentPending(true);
      await reload();
      return saved;
    },
    [reload],
  );

  const bulkEquipment = useCallback(
    async (body: EquipmentBulkInput) => {
      const res = await api.directoryEquipmentBulk(body);
      setEquipmentPending(true);
      await reload();
      return res;
    },
    [reload],
  );

  const bulkResetEquipment = useCallback(
    async (scope: EquipmentScope) => {
      const res = await api.directoryEquipmentBulkReset(scope);
      if (res.reset) setEquipmentPending(true);
      await reload();
      return res;
    },
    [reload],
  );

  const clearEquipmentPending = useCallback(() => setEquipmentPending(false), []);

  const names = useMemo(() => new Map(items.map((p) => [p.staff_id, p.display_name])), [items]);

  return {
    items,
    names,
    loading,
    error,
    persisted,
    reload,
    update,
    create,
    recommended,
    equipmentPending,
    clearEquipmentPending,
    resetEquipment,
    bulkEquipment,
    bulkResetEquipment,
  };
}

/** Один справочник на дерево (App и мобильный экран кладут сюда результат useStaffDirectory). */
export const StaffDirectoryContext = createContext<StaffDirectoryState | null>(null);

export function useStaffDirectoryContext(): StaffDirectoryState | null {
  return useContext(StaffDirectoryContext);
}

const EMPTY = new Map<string, string>();

/** «Фамилия И. О.» по staff_id; fallback — то, что пришло с сервером, иначе сам staff_id. */
export function useStaffName(): (staffId: string | null | undefined, fallback?: string | null) => string {
  const names = useContext(StaffDirectoryContext)?.names ?? EMPTY;
  return useCallback(
    (staffId, fallback) => (staffId ? (names.get(staffId) ?? fallback ?? staffId) : (fallback ?? '—')),
    [names],
  );
}
```

## File: frontend/src/mobile/mobile.css
```css
/* Мобильный экран сотрудника (#/m). Одна колонка 360–430 px, крупные цели касания,
   safe-area. Цвета и шрифт — из theme/tokens.css. */

.m {
  --m-gutter: 16px;
  --m-touch: 56px;
  min-height: 100vh;
  min-height: 100dvh;
  max-width: 480px;
  margin: 0 auto;
  padding: calc(env(safe-area-inset-top, 0px) + 12px) calc(env(safe-area-inset-right, 0px) + var(--m-gutter))
    calc(env(safe-area-inset-bottom, 0px) + 24px) calc(env(safe-area-inset-left, 0px) + var(--m-gutter));
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-x: clip;
  font: 400 16px/22px var(--atmr-font-family);
  color: var(--atmr-text-primary);
  background: var(--atmr-bg-page);
  -webkit-tap-highlight-color: transparent;
}
.m b {
  font-weight: 600;
}

/* ---------------------------------------------------------------- общие */
.m-link {
  flex: none;
  min-height: 44px;
  padding: 0 4px;
  border: 0;
  background: none;
  color: var(--atmr-accent);
  font-weight: 500;
  cursor: pointer;
}
.m-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: var(--m-touch);
  padding: 0 20px;
  border: 0;
  border-radius: var(--atmr-border-radius-m);
  font: 600 17px/22px var(--atmr-font-family);
  cursor: pointer;
}
.m-btn--ghost {
  background: var(--atmr-accent-muted);
  color: var(--atmr-accent);
}
.m-alert {
  margin: 0;
  padding: 12px 14px;
  border-radius: var(--atmr-border-radius-s);
  font-size: 15px;
  line-height: 20px;
}
.m-alert--error {
  background: var(--atmr-status-error-muted);
  color: var(--atmr-status-error);
}
.m-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  text-align: center;
}
.m-empty__title {
  font: var(--atmr-font-heading-h4);
}
.m-empty__text {
  color: var(--atmr-text-secondary);
  text-align: center;
}
.m-tag {
  display: inline-block;
  margin-left: 8px;
  padding: 1px 8px;
  border-radius: var(--atmr-border-radius-xs);
  background: var(--atmr-bg-surface3);
  color: var(--atmr-text-secondary);
  font: 500 13px/20px var(--atmr-font-family);
  vertical-align: 1px;
  white-space: nowrap;
}
.m-tag--warn {
  background: var(--atmr-status-warning-muted);
  color: var(--atmr-status-warning);
}

/* ---------------------------------------------------------------- выбор сотрудника */
.m-pick__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.m-pick__logo {
  width: 40px;
  height: 40px;
  flex: none;
}
.m-pick__titles {
  flex: 1;
  min-width: 0;
}
.m-pick__title {
  font: 700 26px/32px var(--atmr-font-family);
}
.m-pick__sub {
  color: var(--atmr-text-secondary);
  font-size: 15px;
}
.m-search {
  width: 100%;
  min-height: 52px;
  padding: 0 16px;
  border: var(--atmr-border-width-s) solid var(--atmr-border-strong);
  border-radius: var(--atmr-border-radius-m);
  background: var(--atmr-bg-surface1);
  font-size: 17px;
}
.m-search:focus {
  border-color: var(--atmr-accent);
  outline: none;
}
.m-staff {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.m-staff__item {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-areas: 'name count' 'meta count';
  align-items: center;
  column-gap: 12px;
  width: 100%;
  min-height: 64px;
  padding: 10px 16px;
  border: var(--atmr-border-width-s) solid transparent;
  border-radius: var(--atmr-border-radius-m);
  background: var(--atmr-bg-surface1);
  text-align: left;
  cursor: pointer;
}
.m-staff__item.is-current {
  border-color: var(--atmr-accent);
}
.m-staff__name {
  grid-area: name;
  font-weight: 600;
  font-size: 17px;
}
.m-staff__meta {
  grid-area: meta;
  color: var(--atmr-text-secondary);
  font-size: 14px;
  line-height: 18px;
}
.m-staff__count {
  grid-area: count;
  color: var(--atmr-text-secondary);
  font-size: 14px;
}

/* ---------------------------------------------------------------- шапка */
.m-head {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.m-head__row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.m-head__who {
  flex: 1;
  min-width: 0;
}
.m-head__name {
  font: 700 22px/28px var(--atmr-font-family);
  overflow-wrap: anywhere;
}
.m-head__meta {
  color: var(--atmr-text-secondary);
  font-size: 15px;
  line-height: 20px;
}
.m-head__progress-text {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 15px;
}
.m-head__clock {
  color: var(--atmr-text-secondary);
}
.m-head__clock b {
  color: var(--atmr-text-primary);
}
.m-bar {
  height: 6px;
  border-radius: var(--atmr-border-radius-full);
  background: var(--atmr-bg-surface3);
  overflow: hidden;
}
.m-bar__fill {
  height: 100%;
  border-radius: inherit;
  background: var(--atmr-status-success);
  transition: width 0.4s ease;
}

/* ---------------------------------------------------------------- карточки */
.m-card {
  padding: 20px 16px;
  border-radius: 16px;
  background: var(--atmr-bg-surface1);
  box-shadow: var(--atmr-shadow-m);
}
.m-card--calm {
  box-shadow: var(--atmr-shadow-s);
}
.m-card__title {
  font: var(--atmr-font-heading-h4);
}
.m-card__text {
  margin-top: 4px;
  color: var(--atmr-text-secondary);
}

/* ---------------------------------------------------------------- следующий адрес */
.m-next {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-top: 4px solid var(--atmr-accent);
}
.m-next__when {
  display: flex;
  gap: 24px;
}
.m-next__when > div {
  display: flex;
  flex-direction: column;
}
.m-next__k {
  color: var(--atmr-text-tertiary);
  font-size: 13px;
  line-height: 16px;
}
.m-next__v {
  font: 600 22px/28px var(--atmr-font-family);
}
.m-next__address {
  font: 700 26px/31px var(--atmr-font-family);
  letter-spacing: -0.01em;
  overflow-wrap: anywhere;
}
.m-next__work {
  margin: 0;
  font-size: 16px;
  line-height: 26px;
}
.m-next__work .m-tag:first-child {
  margin-left: 0;
}
.m-next__eq {
  margin: 0;
  color: var(--atmr-text-secondary);
}
.m-next__eq b {
  color: var(--atmr-text-primary);
}
.m-next__late {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-status-warning-muted);
  color: var(--atmr-status-warning);
  font-size: 15px;
  line-height: 20px;
}
.m-next__note {
  color: var(--atmr-text-tertiary);
  font-size: 13px;
  line-height: 18px;
}

/* ---------------------------------------------------------------- окно звонка */
.m-call {
  padding: 12px 14px 14px;
  border-radius: var(--atmr-border-radius-m);
  background: var(--atmr-bg-surface2);
  border-left: 4px solid var(--atmr-border-strong);
}
.m-call.is-open {
  background: var(--atmr-brand-orange-muted);
  border-left-color: var(--atmr-brand-orange);
}
.m-call.is-late {
  background: var(--atmr-status-error-muted);
  border-left-color: var(--atmr-status-error);
}
.m-call.is-ok {
  background: var(--atmr-status-success-muted);
  border-left-color: var(--atmr-status-success);
}
.m-call__head {
  font-weight: 600;
}
.m-call__text {
  margin-top: 4px;
  font-size: 15px;
  line-height: 20px;
}
.m-call.is-open .m-call__text {
  color: var(--atmr-brand-orange);
  font-weight: 600;
}
.m-call.is-late .m-call__text {
  color: var(--atmr-status-error);
  font-weight: 600;
}
.m-call__track {
  position: relative;
  height: 8px;
  margin-top: 10px;
  border-radius: var(--atmr-border-radius-full);
  background: var(--atmr-bg-surface3);
}
.m-call__win {
  height: 100%;
  border-radius: inherit;
  background: var(--atmr-brand-orange);
  opacity: 0.55;
}
.m-call__now {
  position: absolute;
  top: -4px;
  width: 4px;
  height: 16px;
  margin-left: -2px;
  border-radius: 2px;
  background: var(--atmr-text-primary);
}
.m-call__scale {
  position: relative;
  height: 16px;
  margin-top: 4px;
  color: var(--atmr-text-tertiary);
  font-size: 12px;
  line-height: 16px;
}
.m-call__scale span {
  position: absolute;
  top: 0;
}
.m-call__scale span:first-child {
  left: 0;
}
.m-call__scale span:nth-child(2) {
  transform: translateX(-50%);
}
.m-call__scale span:nth-child(3) {
  right: 0;
}

/* ---------------------------------------------------------------- навигация */
.m-nav {
  position: relative;
}
.m-btn--nav {
  background: var(--atmr-accent-muted);
  color: var(--atmr-accent);
}
.m-nav__chev {
  transition: transform 0.15s;
}
.m-nav__chev.is-open {
  transform: rotate(180deg);
}
.m-nav__menu {
  position: absolute;
  z-index: 5;
  left: 0;
  right: 0;
  top: calc(100% + 6px);
  margin: 0;
  padding: 6px;
  list-style: none;
  border-radius: var(--atmr-border-radius-m);
  background: var(--atmr-bg-surface1);
  box-shadow: 0 8px 28px rgba(31, 29, 43, 0.18), 0 0 0 1px var(--atmr-border-default);
}
.m-nav__item {
  display: flex;
  align-items: center;
  min-height: 52px;
  padding: 0 14px;
  border-radius: var(--atmr-border-radius-s);
  color: var(--atmr-text-primary);
  font-weight: 500;
  font-size: 17px;
  text-decoration: none;
}
.m-nav__item:active,
.m-nav__item:hover {
  background: var(--atmr-accent-muted);
}

/* ---------------------------------------------------------------- этапы визита */
.m-steps {
  position: relative;
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
/* Сквозная линия через номера этапов: порядок CALLED → ARRIVED → DONE. */
.m-steps::before {
  content: '';
  position: absolute;
  left: 31px;
  top: 28px;
  bottom: 28px;
  width: 2px;
  background: var(--atmr-border-default);
}
.m-step__btn {
  position: relative;
  display: grid;
  grid-template-columns: 32px 1fr;
  grid-template-areas: 'n label' 'n time';
  align-items: center;
  column-gap: 14px;
  width: 100%;
  min-height: 64px;
  padding: 10px 16px;
  border: 2px solid transparent;
  border-radius: var(--atmr-border-radius-m);
  background: var(--atmr-bg-surface2);
  text-align: left;
  cursor: pointer;
}
.m-step__n {
  grid-area: n;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--atmr-bg-surface1);
  border: 2px solid var(--atmr-border-strong);
  color: var(--atmr-text-tertiary);
  font-weight: 700;
  font-size: 15px;
}
.m-step__label {
  grid-area: label;
  font: 600 18px/24px var(--atmr-font-family);
}
.m-step__time {
  grid-area: time;
  font-size: 14px;
  line-height: 18px;
}
.m-step.is-wait .m-step__btn {
  color: var(--atmr-text-tertiary);
  cursor: default;
}
.m-step.is-active .m-step__btn {
  min-height: 72px;
  background: var(--atmr-accent);
  color: var(--atmr-accent-on);
  box-shadow: 0 6px 18px rgba(119, 0, 255, 0.28);
}
.m-step.is-active .m-step__btn:active:not(:disabled) {
  background: var(--atmr-accent-hover);
  transform: translateY(1px);
}
.m-step.is-active .m-step__label {
  font-size: 20px;
}
.m-step.is-active .m-step__n {
  background: var(--atmr-accent-on);
  border-color: var(--atmr-accent-on);
  color: var(--atmr-accent);
}
.m-step.is-active .m-step__btn:disabled {
  opacity: 0.75;
}
.m-step.is-done .m-step__btn {
  background: var(--atmr-status-success-muted);
  cursor: default;
}
.m-step.is-done .m-step__n {
  background: var(--atmr-status-success);
  border-color: var(--atmr-status-success);
  color: #fff;
}
.m-step.is-done .m-step__time {
  color: var(--atmr-status-success);
  font-weight: 500;
}

/* ---------------------------------------------------------------- маршрут на день */
.m-day__title {
  margin-bottom: 8px;
  font: var(--atmr-font-heading-h5);
  font-size: 18px;
}
.m-day__list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
  border-radius: 16px;
  background: var(--atmr-bg-surface1);
  box-shadow: var(--atmr-shadow-s);
}
.m-stop {
  position: relative;
  display: grid;
  grid-template-columns: 48px 16px 1fr auto;
  align-items: start;
  column-gap: 10px;
  padding: 12px 14px;
}
/* линия маршрута между точками */
.m-stop::before {
  content: '';
  position: absolute;
  left: calc(14px + 48px + 10px + 7px);
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--atmr-border-default);
}
.m-stop:first-child::before {
  top: 20px;
}
.m-stop:last-child::before {
  bottom: calc(100% - 20px);
}
.m-stop__time {
  padding-top: 1px;
  color: var(--atmr-text-secondary);
  font-weight: 600;
  font-size: 15px;
}
.m-stop__dot {
  position: relative;
  z-index: 1;
  width: 16px;
  height: 16px;
  margin-top: 3px;
  border-radius: 50%;
  border: 2px solid var(--atmr-border-strong);
  background: var(--atmr-bg-surface1);
}
.m-stop__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.m-stop__title {
  font-size: 15px;
  line-height: 20px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.m-stop__sub {
  color: var(--atmr-text-secondary);
  font-size: 13px;
  line-height: 18px;
}
.m-stop__state {
  padding-top: 1px;
  color: var(--atmr-text-tertiary);
  font-size: 13px;
  white-space: nowrap;
}
.m-stop--break .m-stop__title,
.m-stop--restock .m-stop__title,
.m-stop--depot_start .m-stop__title,
.m-stop--depot_end .m-stop__title {
  color: var(--atmr-text-secondary);
  font-weight: 400;
}
.m-stop--restock .m-stop__dot {
  border-radius: 4px;
  border-color: var(--atmr-gantt-restock);
}
.m-stop--break .m-stop__dot {
  border-style: dashed;
}
.m-stop.is-done .m-stop__dot {
  border-color: var(--atmr-status-success);
  background: var(--atmr-status-success);
}
.m-stop.is-done .m-stop__title,
.m-stop.is-done .m-stop__time {
  color: var(--atmr-text-tertiary);
}
.m-stop.is-done .m-stop__state {
  color: var(--atmr-status-success);
}
.m-stop.is-in-progress .m-stop__dot {
  border-color: var(--atmr-brand-orange);
  background: var(--atmr-brand-orange);
  box-shadow: 0 0 0 4px var(--atmr-brand-orange-muted);
}
.m-stop.is-in-progress .m-stop__state {
  color: var(--atmr-brand-orange);
  font-weight: 600;
}
.m-stop.is-next {
  background: var(--atmr-accent-muted);
}
.m-stop.is-next .m-stop__dot {
  border-color: var(--atmr-accent);
  border-width: 5px;
}
.m-stop.is-next .m-stop__time,
.m-stop.is-next .m-stop__state {
  color: var(--atmr-accent);
  font-weight: 600;
}

.m-foot {
  color: var(--atmr-text-tertiary);
  font-size: 13px;
  text-align: center;
}

@media (prefers-reduced-motion: reduce) {
  .m-bar__fill,
  .m-nav__chev {
    transition: none;
  }
}

/* ---------------------------------------------------------------- результат и причины */
.m-alert--ok {
  background: var(--atmr-status-success-muted);
  color: var(--atmr-status-success);
}
.m-btn--danger {
  color: var(--atmr-status-error);
  background: var(--atmr-status-error-muted);
}
.m-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.m-sheet {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.45);
}
.m-sheet__panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
  padding: 20px var(--m-gutter) calc(20px + env(safe-area-inset-bottom));
  border-radius: 20px 20px 0 0;
  background: var(--atmr-bg-surface1);
}
.m-sheet__title {
  margin: 0;
  font: 700 20px/26px var(--atmr-font-family);
}
.m-sheet__hint {
  margin: 0;
  color: var(--atmr-text-secondary);
  font-size: 15px;
  line-height: 20px;
}
.m-sheet__opts {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.m-sheet__opt {
  min-height: var(--m-touch);
  padding: 0 16px;
  border: 2px solid transparent;
  border-radius: var(--atmr-border-radius-m);
  background: var(--atmr-bg-surface2);
  font: 600 17px/22px var(--atmr-font-family);
  text-align: left;
  cursor: pointer;
}
.m-sheet__opt.is-on {
  border-color: var(--atmr-accent);
  background: var(--atmr-accent-muted);
  color: var(--atmr-accent);
}
```

## File: frontend/src/mobile/MobileApp.tsx
```typescript
/*
 * Мобильный экран выездного сотрудника (#/m, #/m/ST-001): маршрут на день, навигация
 * к следующему адресу и отметки «позвонил → прибыл → завершил».
 * Данные — /ui/employee/*; план опрашивается раз в POLL_MS, состояние меняется только
 * при изменении ответа (без мерцания). Время — модельное время плана (as_of).
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiFailure } from '../api/client';
import { hhmm } from '../format';
import type { EmployeeMarkResult, EmployeeRoute, EmployeeStaffItem, EmployeeStop, MarkKind } from '../types/api';
import { StaffDirectoryContext, useStaffDirectory, useStaffDirectoryContext, useStaffName } from '../hooks/useStaffDirectory';
import './mobile.css';

const POLL_MS = 5000;
const STORAGE_KEY = 'rtk.mobile.staff';
const NOT_READY = new Set(['PLAN_NOT_BUILT', 'DAY_NOT_FOUND', 'REFERENCE_NOT_LOADED', 'PLAN_NOT_FOUND']);

const STEPS: { kind: MarkKind; label: string; done: string }[] = [
  { kind: 'CALLED', label: 'Позвонил абоненту', done: 'Звонок' },
  { kind: 'ARRIVED', label: 'Прибыл на адрес', done: 'Прибытие' },
  { kind: 'DONE', label: 'Завершил работу', done: 'Завершено' },
];

const TRANSFER_REASONS = [
  { value: 'STATION_EQUIPMENT', label: 'Станционное оборудование' },
  { value: 'TRUNK_CABLE', label: 'Магистральный кабель' },
];
const UNAVAILABLE_REASONS = [
  { value: 'VEHICLE_BREAKDOWN', label: 'Поломка машины' },
  { value: 'ILLNESS', label: 'Болезнь' },
  { value: 'OTHER', label: 'Другая причина' },
];
const EVENT_LABEL: Record<string, string> = {
  VISIT_DELAYED: 'задержка визита',
  ORDER_TRANSFERRED: 'передача наряда',
  STAFF_UNAVAILABLE: 'недоступность сотрудника',
};

const PROFILE: Record<string, string> = { INSTALL: 'Инсталлятор', LTP3: 'ЗЛТП' };

// ---------------------------------------------------------------- helpers

function readStored(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStored(id: string | null) {
  try {
    if (id) window.localStorage.setItem(STORAGE_KEY, id);
    else window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* хранилище недоступно — выбор просто не запомнится */
  }
}

function staffFromHash(): string | null {
  const m = /^#\/m\/(ST-\d{3})\b/.exec(window.location.hash);
  return m ? m[1] : null;
}

function setHash(id: string | null) {
  const next = id ? `#/m/${id}` : '#/m';
  if (window.location.hash !== next) window.history.replaceState(null, '', next);
}

const errText = (e: unknown) => (e instanceof Error ? e.message : String(e));
const minutesBetween = (a: string, b: string) => Math.round((Date.parse(a) - Date.parse(b)) / 60000);

function plural(n: number, one: string, few: string, many: string) {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
}
const mins = (n: number) => `${n} ${plural(n, 'минуту', 'минуты', 'минут')}`;

/** Ссылки навигации из координат: Яндекс.Карты и 2ГИС строят маршрут от текущего места. */
function navLinks(lat: number, lon: number, label: string) {
  return [
    { id: 'yandex', title: 'Яндекс.Карты', href: `https://yandex.ru/maps/?rtext=~${lat},${lon}&rtt=auto` },
    { id: '2gis', title: '2ГИС', href: `https://2gis.ru/routeSearch/rsType/car/to/${lon},${lat}` },
    {
      id: 'geo',
      title: 'Системная навигация',
      href: `geo:${lat},${lon}?q=${lat},${lon}(${encodeURIComponent(label)})`,
    },
  ];
}

/** Готовность плана: если день не загружен (сайт открыт сразу с телефона) — грузим демо-день. */
async function ensureDay() {
  try {
    return await api.employeeStaff();
  } catch (e) {
    if (!(e instanceof ApiFailure) || !NOT_READY.has(e.code)) throw e;
    const st = await api.demoStatus();
    if (!st.loaded) await api.demoLoad('dense');
    return api.employeeStaff();
  }
}

/** Короткий итог отметки для сотрудника: что отправлено в план и пересчитан ли маршрут. */
function resultText(r: EmployeeMarkResult): string {
  if (r.duplicate) return 'Такая отметка уже была, план не менялся.';
  if (!r.event_sent || !r.event) return 'Отметка записана.';
  const what = EVENT_LABEL[r.event.type] ?? r.event.type;
  return `Отправлено событие: ${what}. План обновлён до версии ${r.plan_version}${
    r.route_changed ? ', маршрут пересчитан.' : ', ваш маршрут не изменился.'
  }`;
}

/** Выбор причины в нижнем листе (передача наряда, недоступность). */
function ReasonSheet({
  title,
  hint,
  options,
  submitLabel,
  busy,
  error,
  onSubmit,
  onClose,
}: {
  title: string;
  hint: string;
  options: { value: string; label: string }[];
  submitLabel: string;
  busy: boolean;
  error: string | null;
  onSubmit: (reason: string) => void;
  onClose: () => void;
}) {
  const [reason, setReason] = useState<string | null>(null);
  return (
    <div className="m-sheet" role="dialog" aria-modal="true" aria-label={title}>
      <div className="m-sheet__panel">
        <h2 className="m-sheet__title">{title}</h2>
        <p className="m-sheet__hint">{hint}</p>
        <div className="m-sheet__opts" role="radiogroup" aria-label="Причина">
          {options.map((o) => (
            <button
              key={o.value}
              type="button"
              role="radio"
              aria-checked={reason === o.value}
              className={'m-sheet__opt' + (reason === o.value ? ' is-on' : '')}
              onClick={() => setReason(o.value)}
            >
              {o.label}
            </button>
          ))}
        </div>
        {error && <p className="m-alert m-alert--error">{error}</p>}
        <button type="button" className="m-btn" disabled={!reason || busy} onClick={() => reason && onSubmit(reason)}>
          {busy ? 'Отправляем…' : submitLabel}
        </button>
        <button type="button" className="m-btn m-btn--ghost" disabled={busy} onClick={onClose}>
          Отмена
        </button>
      </div>
    </div>
  );
}

function stopTitle(s: EmployeeStop, scName?: string) {
  switch (s.type) {
    case 'VISIT':
      return s.order?.address ?? s.location?.address ?? 'Адрес не найден';
    case 'BREAK':
      return 'Перерыв';
    case 'RESTOCK':
      return `Заезд в ${s.location?.name ?? scName ?? 'СЦ'} за оборудованием`;
    case 'DEPOT_START':
      return `Начало смены, ${s.location?.name ?? scName ?? 'СЦ'}`;
    case 'DEPOT_END':
      return `Возврат в ${s.location?.name ?? scName ?? 'СЦ'}`;
  }
}

function equipmentText(eq: { ONT: number; ROUTER: number; STB: number } | undefined) {
  if (!eq) return null;
  const parts = (['ONT', 'ROUTER', 'STB'] as const).filter((k) => eq[k] > 0).map((k) => `${k} × ${eq[k]}`);
  return parts.length ? parts : null;
}

// ---------------------------------------------------------------- data hook

function useEmployeeRoute(staffId: string | null) {
  const [route, setRoute] = useState<EmployeeRoute | null>(null);
  const [error, setError] = useState<string | null>(null);
  const lastJson = useRef('');

  const load = useCallback(async () => {
    if (!staffId) return;
    try {
      const r = await api.employeeRoute(staffId);
      const json = JSON.stringify(r);
      if (json !== lastJson.current) {
        lastJson.current = json;
        setRoute(r);
      }
      setError(null);
    } catch (e) {
      setError(errText(e));
    }
  }, [staffId]);

  useEffect(() => {
    lastJson.current = '';
    setRoute(null);
    setError(null);
    if (!staffId) return;
    void load();
    const t = window.setInterval(() => {
      if (!document.hidden) void load();
    }, POLL_MS);
    const onVisible = () => {
      if (!document.hidden) void load();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(t);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [staffId, load]);

  return { route, error, reload: load };
}

// ---------------------------------------------------------------- screen

export default function MobileApp() {
  const directory = useStaffDirectory();
  return (
    <StaffDirectoryContext.Provider value={directory}>
      <MobileScreen />
    </StaffDirectoryContext.Provider>
  );
}

function MobileScreen() {
  const [staffId, setStaffId] = useState<string | null>(() => staffFromHash() ?? readStored());
  const [picking, setPicking] = useState(false);
  const [staffList, setStaffList] = useState<EmployeeStaffItem[] | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const { route, error, reload } = useEmployeeRoute(picking ? null : staffId);

  useEffect(() => {
    document.title = 'Ростелеком: маршрут сотрудника';
  }, []);

  // Синхронизация с адресом: #/m/ST-xxx выбирает сотрудника.
  useEffect(() => {
    const on = () => {
      const id = staffFromHash();
      if (id) {
        setStaffId(id);
        setPicking(false);
      }
    };
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);

  useEffect(() => {
    if (staffId && !picking) setHash(staffId);
  }, [staffId, picking]);

  const needList = picking || !staffId;
  useEffect(() => {
    if (!needList) return;
    let alive = true;
    setListError(null);
    ensureDay()
      .then((r) => alive && setStaffList(r.staff))
      .catch((e) => alive && setListError(errText(e)));
    return () => {
      alive = false;
    };
  }, [needList]);

  // Маршрут ещё не построен (открыли сразу ссылку #/m/ST-xxx) — догружаем день и повторяем.
  const recovering = useRef(false);
  useEffect(() => {
    if (!error || needList || recovering.current) return;
    recovering.current = true;
    ensureDay()
      .then(() => reload())
      .catch(() => undefined)
      .finally(() => {
        recovering.current = false;
      });
  }, [error, needList, reload]);

  const choose = (id: string) => {
    writeStored(id);
    setStaffId(id);
    setPicking(false);
  };

  if (needList) {
    return (
      <StaffPicker
        list={staffList}
        error={listError}
        current={staffId}
        onPick={choose}
        onCancel={staffId ? () => setPicking(false) : undefined}
      />
    );
  }

  return (
    <div className="m">
      {route ? (
        <RouteScreen route={route} onSwitch={() => setPicking(true)} onChanged={reload} />
      ) : (
        <div className="m-empty">
          {error ? (
            <>
              <p className="m-empty__title">Маршрут не загрузился</p>
              <p className="m-empty__text">{error}</p>
              <button type="button" className="m-btn m-btn--ghost" onClick={() => setPicking(true)}>
                Выбрать другого сотрудника
              </button>
            </>
          ) : (
            <p className="m-empty__text">Загружаем маршрут…</p>
          )}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- staff picker

function StaffPicker({
  list,
  error,
  current,
  onPick,
  onCancel,
}: {
  list: EmployeeStaffItem[] | null;
  error: string | null;
  current: string | null;
  onPick: (id: string) => void;
  onCancel?: () => void;
}) {
  const [query, setQuery] = useState('');
  const staffName = useStaffName();
  const profiles = useStaffDirectoryContext()?.items;
  const byId = new Map((profiles ?? []).map((p) => [p.staff_id, p]));
  const q = query.trim().toLowerCase();
  const items = (list ?? []).filter((s) => {
    if (!q) return true;
    const p = byId.get(s.staff_id);
    return [s.staff_id, s.display_name, p?.full_name, p?.personnel_number].some((v) => (v ?? '').toLowerCase().includes(q));
  });
  return (
    <div className="m">
      <header className="m-pick__head">
        <img className="m-pick__logo" src="/logo-mark.png" alt="" aria-hidden />
        <div className="m-pick__titles">
          <h1 className="m-pick__title">Кто вы?</h1>
          <p className="m-pick__sub">Выберите себя, чтобы открыть маршрут на день</p>
        </div>
        {onCancel && (
          <button type="button" className="m-link" onClick={onCancel}>
            Отмена
          </button>
        )}
      </header>
      <input
        className="m-search"
        type="search"
        inputMode="search"
        placeholder="Имя или табельный номер"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        aria-label="Поиск сотрудника"
      />
      {error && <p className="m-alert m-alert--error">{error}</p>}
      {!list && !error && <p className="m-empty__text">Загружаем список смены…</p>}
      <ul className="m-staff">
        {items.map((s) => (
          <li key={s.staff_id}>
            <button
              type="button"
              className={'m-staff__item' + (s.staff_id === current ? ' is-current' : '')}
              onClick={() => onPick(s.staff_id)}
            >
              <span className="m-staff__name">{staffName(s.staff_id, s.display_name)}</span>
              <span className="m-staff__meta">
                {s.staff_id}, {s.profiles.map((p) => PROFILE[p] ?? p).join(' и ')}, {s.service_center_name ?? s.service_center_id}
              </span>
              <span className="m-staff__count num">
                {s.route_status === 'UNAVAILABLE'
                  ? 'Снят со смены'
                  : s.visits
                    ? `${s.visits_done}/${s.visits}`
                    : 'Нет визитов'}
              </span>
            </button>
          </li>
        ))}
      </ul>
      {list && !items.length && <p className="m-empty__text">Никого не нашли</p>}
    </div>
  );
}

// ---------------------------------------------------------------- route screen

function RouteScreen({
  route,
  onSwitch,
  onChanged,
}: {
  route: EmployeeRoute;
  onSwitch: () => void;
  onChanged: () => Promise<void>;
}) {
  const { staff, progress } = route;
  const staffName = useStaffName();
  const [notice, setNotice] = useState<string | null>(null);
  const [sheet, setSheet] = useState(false);
  const [busy, setBusy] = useState(false);
  const [fail, setFail] = useState<string | null>(null);
  const canUnavailable = route.allowed_actions.includes('UNAVAILABLE');

  const goUnavailable = async (reason: string) => {
    setBusy(true);
    setFail(null);
    try {
      const r = await api.employeeMark(staff.staff_id, { kind: 'UNAVAILABLE', reason });
      setNotice(resultText(r));
      setSheet(false);
      await onChanged();
    } catch (e) {
      setFail(errText(e));
    } finally {
      setBusy(false);
    }
  };
  const next = route.next ? route.stops.find((s) => s.seq === route.next!.seq) ?? null : null;
  const depotEnd = route.stops.find((s) => s.type === 'DEPOT_END');
  const pct = progress.visits_total ? Math.round((progress.visits_done / progress.visits_total) * 100) : 0;

  return (
    <>
      <header className="m-head">
        <div className="m-head__row">
          <div className="m-head__who">
            <h1 className="m-head__name">{staffName(staff.staff_id, staff.display_name)}</h1>
            <p className="m-head__meta">
              {staff.service_center?.name ?? '—'}, смена {staff.shift.start}–{staff.shift.end}
            </p>
          </div>
          <button type="button" className="m-link" onClick={onSwitch}>
            Сменить
          </button>
        </div>
        <div className="m-head__progress">
          <div className="m-head__progress-text">
            <span>
              Выполнено <b className="num">{progress.visits_done}</b> из <b className="num">{progress.visits_total}</b>
            </span>
            <span className="m-head__clock" title="Модельное время плана">
              Сейчас <b className="num">{hhmm(route.as_of)}</b>
            </span>
          </div>
          <div className="m-bar" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
            <div className="m-bar__fill" style={{ width: `${pct}%` }} />
          </div>
        </div>
      </header>

      {notice && (
        <p className="m-alert m-alert--ok" role="status">
          {notice}
        </p>
      )}

      {route.route_status === 'UNAVAILABLE' && (
        <p className="m-alert m-alert--error">
          Вы сняты со смены
          {route.unavailable_from ? ` с ${hhmm(route.unavailable_from)}` : ''}
          {route.unavailable_until ? ` до ${hhmm(route.unavailable_until)}` : ''}. Оставшиеся наряды передаются другим
          сотрудникам.
        </p>
      )}

      {next ? (
        <NextCard key={next.order_id} stop={next} route={route} onChanged={onChanged} onResult={setNotice} />
      ) : (
        <section className="m-card m-card--calm">
          <h2 className="m-card__title">
            {progress.visits_total ? 'Все визиты на сегодня выполнены' : 'На сегодня визитов нет'}
          </h2>
          {depotEnd && (
            <p className="m-card__text">
              Возврат в {depotEnd.location?.name ?? 'СЦ'} к <span className="num">{hhmm(depotEnd.arrival)}</span>
            </p>
          )}
        </section>
      )}

      <section className="m-day" aria-label="Маршрут на день">
        <h2 className="m-day__title">Маршрут на день</h2>
        <ol className="m-day__list">
          {route.stops.map((s) => (
            <StopRow key={s.seq} stop={s} isNext={s.seq === next?.seq} scName={staff.service_center?.name} />
          ))}
        </ol>
      </section>
      {canUnavailable && (
        <button type="button" className="m-btn m-btn--ghost m-btn--danger" onClick={() => setSheet(true)}>
          Недоступен
        </button>
      )}
      <p className="m-foot">
        План версии <span className="num">{route.plan_version}</span>, обновляется автоматически
      </p>
      {sheet && (
        <ReasonSheet
          title="Вы недоступны"
          hint="Оставшиеся наряды диспетчер передаст другим сотрудникам."
          options={UNAVAILABLE_REASONS}
          submitLabel="Сообщить"
          busy={busy}
          error={fail}
          onSubmit={(r) => void goUnavailable(r)}
          onClose={() => setSheet(false)}
        />
      )}
    </>
  );
}

// ---------------------------------------------------------------- next stop

function NextCard({
  stop,
  route,
  onChanged,
  onResult,
}: {
  stop: EmployeeStop;
  route: EmployeeRoute;
  onChanged: () => Promise<void>;
  onResult: (text: string) => void;
}) {
  const order = stop.order;
  const marks = stop.marks ?? { CALLED: null, ARRIVED: null, DONE: null, TRANSFER: null };
  const [busy, setBusy] = useState<MarkKind | null>(null);
  const [fail, setFail] = useState<string | null>(null);
  const [transfer, setTransfer] = useState(false);
  const eq = equipmentText(order?.required_equipment);
  const lat = order?.lat ?? stop.location?.lat;
  const lon = order?.lon ?? stop.location?.lon;
  const late = order ? minutesBetween(stop.start, order.slot.end) : 0;

  const mark = async (kind: MarkKind, reason?: string) => {
    if (!stop.order_id || busy) return;
    setBusy(kind);
    setFail(null);
    try {
      const r = await api.employeeMark(route.staff.staff_id, { order_id: stop.order_id, kind, reason });
      onResult(resultText(r));
      setTransfer(false);
      await onChanged();
    } catch (e) {
      setFail(errText(e));
    } finally {
      setBusy(null);
    }
  };

  return (
    <section className="m-card m-next" aria-label="Следующий адрес">
      <div className="m-next__when">
        <div>
          <span className="m-next__k">Слот</span>
          <span className="m-next__v num">
            {order ? `${hhmm(order.slot.start)}–${hhmm(order.slot.end)}` : '—'}
          </span>
        </div>
        <div>
          <span className="m-next__k">Прибытие по плану</span>
          <span className="m-next__v num">{hhmm(stop.arrival)}</span>
        </div>
      </div>

      <h2 className="m-next__address">{stopTitle(stop)}</h2>
      <p className="m-next__work">
        {order?.work_type_name ?? 'Визит'}
        {order && <span className="m-tag">{order.technology}</span>}
        {order?.requires_height && <span className="m-tag m-tag--warn">Работа на высоте</span>}
        {order?.is_repeat && <span className="m-tag m-tag--warn">Повторный</span>}
      </p>
      <p className="m-next__eq">
        {eq ? (
          <>
            Взять АО: <b>{eq.join(', ')}</b>
          </>
        ) : (
          'Абонентское оборудование не требуется'
        )}
      </p>
      {order && late > 0 && (
        <p className="m-next__late">
          Начало по плану {hhmm(stop.start)}, позже слота на {mins(late)}. Предупредите абонента.
        </p>
      )}

      {stop.call_window && (
        <CallWindow
          window={stop.call_window}
          asOf={route.as_of}
          calledAt={marks.CALLED}
          slotStart={order?.slot.start}
        />
      )}

      {lat != null && lon != null && <NavButton lat={lat} lon={lon} label={stopTitle(stop)} />}

      <ol className="m-steps" aria-label="Отметки по визиту">
        {STEPS.map((st, i) => {
          const at = marks[st.kind];
          const active = !at && stop.next_mark === st.kind;
          const cls = at ? 'is-done' : active ? 'is-active' : 'is-wait';
          return (
            <li key={st.kind} className={`m-step ${cls}`}>
              <button
                type="button"
                className="m-step__btn"
                disabled={!active || !!busy}
                aria-pressed={!!at}
                onClick={() => void mark(st.kind)}
              >
                <span className="m-step__n" aria-hidden>
                  {at ? '✓' : i + 1}
                </span>
                <span className="m-step__label">{busy === st.kind ? 'Отмечаем…' : st.label}</span>
                {at && (
                  <span className="m-step__time num">
                    {st.done} в {hhmm(at)}
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ol>
      {stop.allowed_actions.includes('TRANSFER') && (
        <button type="button" className="m-btn m-btn--ghost" disabled={!!busy} onClick={() => setTransfer(true)}>
          Не могу выполнить, передать
        </button>
      )}
      {fail && !transfer && <p className="m-alert m-alert--error">{fail}</p>}
      {transfer && (
        <ReasonSheet
          title="Передать наряд"
          hint="Повреждение вне зоны ответственности: наряд уйдёт в профильное подразделение, вы освободитесь."
          options={TRANSFER_REASONS}
          submitLabel="Передать"
          busy={busy === 'TRANSFER'}
          error={fail}
          onSubmit={(r) => void mark('TRANSFER', r)}
          onClose={() => setTransfer(false)}
        />
      )}
      <p className="m-next__note">Номера абонента в данных нет: кнопка звонка только ставит отметку.</p>
    </section>
  );
}

function CallWindow({
  window: w,
  asOf,
  calledAt,
  slotStart,
}: {
  window: { start: string; end: string };
  asOf: string;
  calledAt: string | null;
  slotStart?: string;
}) {
  if (calledAt) {
    return (
      <div className="m-call is-ok">
        <p className="m-call__head">Абоненту позвонили в {hhmm(calledAt)}</p>
      </div>
    );
  }
  const toStart = minutesBetween(w.start, asOf);
  const toEnd = minutesBetween(w.end, asOf);
  let tone = 'is-wait';
  let text: string;
  if (toStart > 0) text = `Окно откроется через ${mins(toStart)}`;
  else if (toEnd >= 0) {
    tone = 'is-open';
    text = toEnd ? `Пора звонить: осталось ${mins(toEnd)}` : 'Пора звонить: окно закрывается';
  } else {
    tone = 'is-late';
    text = `Окно прошло ${mins(-toEnd)} назад, позвоните сейчас`;
  }
  // Шкала от начала окна до начала слота: окно звонка — первая половина, маркер — модельное время.
  const span = slotStart ? Math.max(1, minutesBetween(slotStart, w.start)) : 60;
  const pos = Math.min(100, Math.max(0, ((-toStart) / span) * 100));
  const winPct = Math.min(100, (minutesBetween(w.end, w.start) / span) * 100);
  return (
    <div className={`m-call ${tone}`}>
      <p className="m-call__head">
        Позвонить абоненту с <span className="num">{hhmm(w.start)}</span> до <span className="num">{hhmm(w.end)}</span>
      </p>
      <div className="m-call__track" aria-hidden>
        <div className="m-call__win" style={{ width: `${winPct}%` }} />
        <div className="m-call__now" style={{ left: `${pos}%` }} />
      </div>
      <div className="m-call__scale num" aria-hidden>
        <span>{hhmm(w.start)}</span>
        <span style={{ left: `${winPct}%` }}>{hhmm(w.end)}</span>
        {slotStart && <span>{hhmm(slotStart)}</span>}
      </div>
      <p className="m-call__text">{text}</p>
    </div>
  );
}

function NavButton({ lat, lon, label }: { lat: number; lon: number; label: string }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);
  return (
    <div className="m-nav" ref={ref}>
      <button
        type="button"
        className="m-btn m-btn--nav"
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((v) => !v)}
      >
        Построить маршрут
        <svg className={'m-nav__chev' + (open ? ' is-open' : '')} viewBox="0 0 16 16" width="16" height="16" aria-hidden>
          <path d="M4 6l4 4 4-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </button>
      {open && (
        <ul className="m-nav__menu" role="menu">
          {navLinks(lat, lon, label).map((l) => (
            <li key={l.id} role="none">
              <a
                role="menuitem"
                className="m-nav__item"
                href={l.href}
                target={l.id === 'geo' ? undefined : '_blank'}
                rel="noopener noreferrer"
                onClick={() => setOpen(false)}
              >
                {l.title}
              </a>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- day list

const STATE_LABEL: Record<string, string> = { DONE: 'Сделано', IN_PROGRESS: 'В работе', PLANNED: 'Впереди' };

function StopRow({ stop, isNext, scName }: { stop: EmployeeStop; isNext: boolean; scName?: string }) {
  const o = stop.order;
  const state = isNext && stop.state === 'PLANNED' ? 'NEXT' : stop.state;
  const restock = stop.type === 'RESTOCK' ? equipmentText(stop.equipment_after ?? undefined) : null;
  const sub =
    stop.type === 'VISIT' && o
      ? `${o.work_type_name}, слот ${hhmm(o.slot.start)}–${hhmm(o.slot.end)}`
      : stop.type === 'BREAK'
        ? `до ${hhmm(stop.end)}`
        : restock
          ? `С собой после заезда: ${restock.join(', ')}`
          : null;
  return (
    <li className={`m-stop m-stop--${stop.type.toLowerCase()} is-${state.toLowerCase().replace('_', '-')}`}>
      <span className="m-stop__time num">{hhmm(stop.type === 'VISIT' ? stop.arrival : stop.start)}</span>
      <span className="m-stop__dot" aria-hidden />
      <span className="m-stop__body">
        <span className="m-stop__title">{stopTitle(stop, scName)}</span>
        {sub && <span className="m-stop__sub">{sub}</span>}
      </span>
      <span className="m-stop__state">{state === 'NEXT' ? 'Следующий' : STATE_LABEL[stop.state]}</span>
    </li>
  );
}
```

## File: frontend/src/theme/tokens.css
```css
/*
 * Токены интерфейса в именах дизайн-системы Атомаро (--atmr-*).
 * Тема: rostelecom-purple-light (класс .Theme_root_rtk_purple_light, см. Storybook «Welcome/Themes»).
 *
 * Имена, подтверждённые документацией Atomaro (MCP): --atmr-bg-surface1, --atmr-bg-surface3,
 * --atmr-accent-muted, --atmr-border-radius-s, --atmr-border-radius-xs, --atmr-spacing-2x;
 * варианты типографики heading-h1..h5, body-s/m/l, description-s/m/l; шрифт Rostelecom Basis.
 * ЗНАЧЕНИЯ в документации не опубликованы (страницы токенов рендерят их из пакета @atomaro/themes),
 * поэтому значения ниже — приближение к фирменному стилю. При подключении
 * @atomaro/themes/rostelecom-purple-light.css этот файл удаляется: имена совпадают
 * там, где они подтверждены; прочие (помечены «≈») надо сверить с пакетом.
 */

.Theme_root_rtk_purple_light,
:root {
  /* ---- цвет: акцент (фиолетовый) и бренд-второй (оранжевый) */
  --atmr-accent: #7700ff;              /* ≈ фирменный фиолетовый Ростелекома */
  --atmr-accent-hover: #6200d4;        /* ≈ */
  --atmr-accent-muted: #efe5ff;        /* имя из документации, значение ≈ */
  --atmr-accent-on: #ffffff;           /* ≈ текст на акценте */
  --atmr-brand-orange: #ff4f12;        /* ≈ фирменный оранжевый Ростелекома */
  --atmr-brand-orange-muted: #ffeadf;  /* ≈ */

  /* ---- поверхности */
  --atmr-bg-page: #f3f2f7;             /* ≈ */
  --atmr-bg-surface1: #ffffff;         /* имя из документации, значение ≈ */
  --atmr-bg-surface2: #f7f6fb;         /* ≈ */
  --atmr-bg-surface3: #ecebf2;         /* имя из документации, значение ≈ */

  /* ---- текст */
  --atmr-text-primary: #1f1d2b;        /* ≈ */
  --atmr-text-secondary: #5f5b73;      /* ≈ */
  --atmr-text-tertiary: #8e8aa3;       /* ≈ */

  /* ---- линии */
  --atmr-border-default: #dedbe8;      /* ≈ */
  --atmr-border-strong: #c3bfd3;       /* ≈ */
  --atmr-border-width-s: 1px;          /* ≈ категория Border-width есть в документации */

  /* ---- статусы (Badge colorScheme: error | info | warning | success | neutral) */
  --atmr-status-error: #e0263c;        /* ≈ */
  --atmr-status-error-muted: #fde7ea;  /* ≈ */
  --atmr-status-warning: #e06a00;      /* ≈ */
  --atmr-status-warning-muted: #fff0e0;/* ≈ */
  --atmr-status-success: #12925b;      /* ≈ */
  --atmr-status-success-muted: #e2f5ec;/* ≈ */
  --atmr-status-info: #2f6fe0;         /* ≈ */
  --atmr-status-info-muted: #e6eefc;   /* ≈ */
  --atmr-status-neutral: #6b6780;      /* ≈ */
  --atmr-status-neutral-muted: #eeedf3;/* ≈ */

  /* ---- маркеры нарядов на карте (в Атомаро нет, свои) */
  --atmr-map-pgp: #f0a000;             /* ≈ янтарный ПГП, отличим от оранжевого риска */
  --atmr-map-late: #ff3b1f;            /* ≈ красно-оранжевый риск опоздания */

  /* ---- маршруты сотрудников на карте и метка в Ганте (в Атомаро нет, свои).
         12 различимых на подложке OSM цветов; фиолетовый и оранжевый — фирменные,
         янтарный ПГП и красный риск опоздания не используются, чтобы не путать с точками. */
  --atmr-route-01: #7700ff;            /* ≈ фирменный фиолетовый */
  --atmr-route-02: #0a8fd6;            /* ≈ голубой */
  --atmr-route-03: #12925b;            /* ≈ зелёный */
  --atmr-route-04: #d4148c;            /* ≈ маджента */
  --atmr-route-05: #ff4f12;            /* ≈ фирменный оранжевый */
  --atmr-route-06: #00a3a3;            /* ≈ бирюзовый */
  --atmr-route-07: #4b3bd6;            /* ≈ индиго */
  --atmr-route-08: #8a5a00;            /* ≈ коричневый */
  --atmr-route-09: #7f9c00;            /* ≈ оливковый */
  --atmr-route-10: #b05cff;            /* ≈ лавандовый */
  --atmr-route-11: #5c6f84;            /* ≈ сланцевый */
  --atmr-route-12: #1f3f8f;            /* ≈ тёмно-синий */

  /* ---- диаграмма Ганта (свои, производные от статусных) */
  --atmr-gantt-shift: #faf9fd;         /* ≈ фон рабочего времени смены */
  --atmr-gantt-restock: #2f6fe0;       /* ≈ = status-info */
  --atmr-gantt-restock-muted: #d6e3fb; /* ≈ */

  /* ---- окно «Статистика» (свои). Работа/дорога/перерыв проверены валидатором палитры
         (CVD ΔE ≥ 13 между соседями); простой — пустой трек, без цвета. */
  --atmr-stat-work: #7700ff;           /* ≈ = accent */
  --atmr-stat-road: #0a8fd6;           /* ≈ = route-02 */
  --atmr-stat-break: #c08a1e;          /* ≈ охра, не статусный оранжевый */
  --atmr-stat-idle: #ecebf2;           /* ≈ = bg-surface3 */
  --atmr-stat-line: #7700ff;           /* ≈ линии динамики */

  /* ---- отступы: шаг 4px (имя --atmr-spacing-2x подтверждено) */
  --atmr-spacing-0_5x: 2px;
  --atmr-spacing-1x: 4px;
  --atmr-spacing-2x: 8px;
  --atmr-spacing-3x: 12px;
  --atmr-spacing-4x: 16px;
  --atmr-spacing-5x: 20px;
  --atmr-spacing-6x: 24px;

  /* ---- радиусы (имена -xs и -s подтверждены) */
  --atmr-border-radius-xs: 4px;
  --atmr-border-radius-s: 8px;
  --atmr-border-radius-m: 12px;        /* ≈ */
  --atmr-border-radius-full: 999px;    /* ≈ */

  /* ---- тени (категория Shadow есть, имена не опубликованы) */
  --atmr-shadow-s: 0 1px 2px rgba(31, 29, 43, 0.06), 0 0 0 1px rgba(31, 29, 43, 0.04); /* ≈ */
  --atmr-shadow-m: 0 4px 16px rgba(56, 20, 120, 0.10);                                  /* ≈ */

  /* ---- типографика: Rostelecom Basis в документации; запасной — Golos Text (Google Fonts) */
  --atmr-font-family: 'Rostelecom Basis', 'Golos Text', 'Segoe UI', system-ui, sans-serif;
  --atmr-font-heading-h4: 600 20px/28px var(--atmr-font-family);  /* ≈ */
  --atmr-font-heading-h5: 600 16px/24px var(--atmr-font-family);  /* ≈ */
  --atmr-font-body-m: 400 14px/20px var(--atmr-font-family);      /* ≈ */
  --atmr-font-body-s: 400 13px/18px var(--atmr-font-family);      /* ≈ */
  --atmr-font-description-m: 400 12px/16px var(--atmr-font-family); /* ≈ */
  --atmr-font-description-s: 400 11px/14px var(--atmr-font-family); /* ≈ */
  --atmr-font-display-s: 600 26px/30px var(--atmr-font-family);   /* ≈ для цифр KPI */
}
```

## File: frontend/src/types/api.ts
```typescript
// Типы по data/api_spec.yaml (/api/v1) и вспомогательному /ui (backend/app/ui_api.py).

export type DateTime = string;
export type Technology = 'PON' | 'FTTB' | 'XDSL' | 'DOCSIS';
export type Profile = 'INSTALL' | 'LTP3';
export type WorkTypeCode =
  | 'INST_PON_MKD'
  | 'INST_FTTB_MKD'
  | 'INST_PRIVATE_VLS'
  | 'ADD_SERVICE'
  | 'SURVEY_PRIVATE'
  | 'REPAIR'
  | 'DEGRADATION'
  | 'DISMANTLE';
export type BuildingType = 'MKD' | 'PRIVATE';

export interface TimeWindow {
  start: DateTime;
  end: DateTime;
}
export interface Equipment {
  ONT: number;
  ROUTER: number;
  STB: number;
}

export interface Order {
  order_id: string;
  work_type_code: WorkTypeCode;
  technology: Technology;
  building_id: string;
  address: string;
  lat: number;
  lon: number;
  building_type: BuildingType;
  requires_height: boolean;
  access_node_id: string;
  registered_at: DateTime;
  slot: TimeWindow;
  latest_start: DateTime;
  deadline: DateTime;
  required_equipment: Equipment;
  is_repeat: boolean;
  previous_order_id?: string | null;
}

export type StopType = 'DEPOT_START' | 'VISIT' | 'BREAK' | 'RESTOCK' | 'DEPOT_END';
export type StopStatus = 'PLANNED' | 'IN_PROGRESS' | 'DONE';

export interface Stop {
  seq: number;
  type: StopType;
  location_id: string;
  order_id?: string | null;
  arrival: DateTime;
  start: DateTime;
  end: DateTime;
  travel_sec?: number;
  distance_m?: number;
  status?: StopStatus;
  equipment_after?: Equipment;
  explanation?: string;
}

export interface RouteTotals {
  visits?: number;
  travel_sec?: number;
  distance_m?: number;
  work_min?: number;
  utilization?: number;
}

export interface Route {
  staff_id: string;
  service_center_id: string;
  status: 'ACTIVE' | 'UNAVAILABLE';
  unavailable_from?: DateTime | null;
  unavailable_until?: DateTime | null;
  stops: Stop[];
  totals?: RouteTotals;
}

export type UnassignedReason =
  | 'NO_QUALIFIED_STAFF'
  | 'NO_SHIFT_TIME'
  | 'SLOT_UNREACHABLE'
  | 'NO_EQUIPMENT'
  | 'STAFF_UNAVAILABLE'
  | 'OTHER';

export interface UnassignedOrder {
  order_id: string;
  reason_code: UnassignedReason;
  reason_text: string;
  suggested_action: 'RESCHEDULE' | 'REASSIGN' | 'ESCALATE';
  proposed_slot?: TimeWindow | null;
  since?: DateTime | null;
}

export interface PgpGroup {
  group_id: string;
  access_node_id: string;
  order_ids: string[];
  detected_at: DateTime;
  action: 'TRANSFER_TO_STATION';
  comment?: string;
}

export interface CancelledOrder {
  order_id: string;
  cancelled_at: DateTime;
  event_id?: string | null;
}

export type AlertType = 'LATE_RISK' | 'PGP_DETECTED' | 'UNASSIGNED';
export type AlertAction = 'REASSIGN' | 'RESCHEDULE' | 'TRANSFER_TO_STATION' | 'NONE';

export interface Alert {
  alert_id: string;
  created_at: DateTime;
  type: AlertType;
  status: 'ACTIVE' | 'RESOLVED';
  resolved_at?: DateTime | null;
  order_id?: string | null;
  group_id?: string | null;
  staff_id?: string | null;
  forecast_start?: DateTime | null;
  lateness_min?: number | null;
  suggested_action: AlertAction;
  suggested_staff_id?: string | null;
  proposed_slot?: TimeWindow | null;
  applied?: boolean;
  message: string;
}

export interface PlanSummary {
  orders_total?: number;
  visits_planned?: number;
  unassigned?: number;
  pgp_orders?: number;
  cancelled?: number;
  travel_sec_total?: number;
  utilization_cv?: number;
}

export interface Plan {
  date: string;
  plan_version: number;
  based_on_event_id?: string | null;
  as_of: DateTime;
  computation_ms?: number;
  routes: Route[];
  unassigned: UnassignedOrder[];
  pgp_groups: PgpGroup[];
  cancelled: CancelledOrder[];
  alerts: Alert[];
  summary?: PlanSummary;
}

export interface ErrorResponse {
  error: { code: string; message: string; details?: { field?: string | null; message: string }[] };
}

// ---------------------------------------------------------------- справочники (/ui/reference)

export interface ServiceCenter {
  service_center_id: string;
  name: string;
  address: string;
  lat: number;
  lon: number;
  opening_hours: { open: string; close: string };
  restock_duration_min: number;
}

export interface Staff {
  staff_id: string;
  display_name?: string;
  service_center_id: string;
  profiles: Profile[];
  technologies: Technology[];
  height_permit: boolean;
  shift: {
    start: string;
    end: string;
    break_duration_min: number;
    break_window: { from: string; to: string };
  };
  equipment_start: Equipment;
  equipment_capacity: Equipment;
  experience_years?: number;
}

export interface WorkType {
  code: WorkTypeCode;
  name: string;
  profiles: Profile[];
  duration_min: number;
  max_start_delay_min: number | null;
  deadline_kind: 'SLOT' | 'HOURS' | 'WORKING_DAYS';
  deadline_value: number | null;
  repeat_deadline_value: number | null;
  pgp_relevant: boolean;
}

export interface AccessNodeInfo {
  access_node_id: string;
  node_type: 'OLT' | 'SWITCH' | 'DSLAM' | 'CMTS';
  technology: Technology;
  name: string;
  address?: string;
  lat: number;
  lon: number;
  buildings_count: number;
}

export interface UiReference {
  service_centers: ServiceCenter[];
  staff: Staff[];
  work_types: WorkType[];
  access_nodes: AccessNodeInfo[];
}

// ---------------------------------------------------------------- /ui

export type UiOrderStatus = 'ROUTED' | 'UNASSIGNED' | 'PGP' | 'CANCELLED' | 'UNKNOWN';

export interface UiOrder extends Order {
  work_type_name: string;
  status: UiOrderStatus;
  staff_id: string | null;
  staff_name: string | null;
  planned_arrival: DateTime | null;
  planned_start: DateTime | null;
  planned_end: DateTime | null;
  stop_status: StopStatus | null;
  explanation: string | null;
  group_id: string | null;
  reason_code: UnassignedReason | null;
  reason_text: string | null;
  suggested_action: UnassignedOrder['suggested_action'] | null;
  cancelled_at: DateTime | null;
  late_risk: { alert_id: string; lateness_min: number | null; suggested_action: AlertAction } | null;
  /** Совместный наряд (JOINT_ORDERS=on): напарник с визитом ORD-…-B в то же время. */
  partner_staff_id?: string | null;
  partner_staff_name?: string | null;
}

export interface UiDayOrders {
  date: string;
  plan_version: number;
  as_of: DateTime;
  orders: UiOrder[];
}

export type Scenario = 'dense' | 'example';

export interface DemoStatus {
  loaded: boolean;
  date?: string;
  scenario?: Scenario | null;
  now?: DateTime;
  as_of?: DateTime;
  plan_version?: number;
  remaining_events?: number;
  remaining_triggers?: number;
  skipped_triggers?: number;
}

export interface DemoLoadResult {
  date: string;
  now: DateTime;
  status: string;
  plan_version: number;
  orders: number;
  computation_ms?: number;
  demo: DemoStatus;
}

export interface DemoStepItem {
  event_id: string;
  type: string;
  timestamp: DateTime;
  accepted: boolean;
  plan_version?: number;
  processing_ms?: number;
  message?: string | null;
  changed_staff_ids?: string[];
  error?: ErrorResponse;
}

export interface DemoStepResult {
  sent: DemoStepItem[];
  plan_version: number;
  as_of: DateTime;
  demo: DemoStatus;
}

/* ---------------------------------------------------------------- /ui/scenario (сценарный анализ) */

export type StaffCutStrategy =
  | 'random'
  | 'least_loaded'
  | 'by_profile:INSTALL'
  | 'by_profile:LTP3'
  | `by_service_center:${string}`;

export interface ScenarioMetrics {
  orders_total: number;
  visits: number;
  unassigned: number;
  unassigned_by_reason: Partial<Record<UnassignedReason, number>>;
  pgp_orders: number;
  in_slot_share: number;
  travel_h: number;
  staff_used: number;
  util_mean: number;
  util_cv: number;
  restocks: number;
}

export interface ScenarioAffectedOrder {
  order_id: string;
  base_staff_id: string;
  base_staff_removed: boolean;
  work_type_code: WorkTypeCode | null;
  is_repeat: boolean;
  slot: { start: DateTime; end: DateTime } | null;
  address: string | null;
  reason_code: UnassignedReason | 'PGP' | null;
  reason_text: string | null;
}

export interface StaffCutResult {
  date: string;
  strategy: string;
  remove_count: number;
  seed: number;
  budget_sec: number;
  base_source: 'current_plan_v1' | 'rebuilt_initial';
  removed: { staff_id: string; display_name: string | null; profiles: string[]; service_center_id: string; base_visits: number }[];
  base: ScenarioMetrics;
  scenario: ScenarioMetrics;
  delta: Record<string, number>;
  affected_orders: ScenarioAffectedOrder[];
  newly_routed: string[];
  reassigned: number;
  time_sec: number;
}

export interface EquipmentStat {
  p50: number;
  p90: number;
  max: number;
  current_start_avg: number;
  capacity: number;
}

export interface EquipmentRecommendation {
  profile: 'INSTALL' | 'LTP3';
  staff_on_shift: number;
  staff_with_visits: number;
  ONT: EquipmentStat;
  ROUTER: EquipmentStat;
  STB: EquipmentStat;
  staff_exceeding_current_start: number;
}

export interface EquipmentResult {
  date: string;
  mode: 'no_restock';
  budget_sec: number;
  base_source: 'current_plan_v1' | 'rebuilt_initial';
  current: ScenarioMetrics;
  no_restock: ScenarioMetrics;
  delta: Record<string, number>;
  visits_gained: number;
  travel_saved_h: number;
  recommended: EquipmentRecommendation[];
  time_sec: number;
}

/** Метрики плана в сценарии «пробки по часам»: travel_h — по базовой матрице,
 *  travel_traffic_h и опоздания — при исполнении плана с коэффициентами по часам. */
export interface TrafficMetrics extends ScenarioMetrics {
  travel_traffic_h: number;
  late_visits: number;
  late_min: number;
  on_time_visits: number;
  in_slot_share_traffic: number;
  violations: number;
  time_sec: number;
}

export interface TrafficResult {
  date: string;
  budget_sec: number;
  traffic_env: boolean;
  factors: { hour: number; factor: number }[];
  off: TrafficMetrics;
  on: TrafficMetrics;
  delta: Record<string, number>;
  time_sec: number;
}

// ---------------------------------------------------------------- /ui/employee (мобильный экран сотрудника)

export type MarkKind = 'CALLED' | 'ARRIVED' | 'DONE' | 'TRANSFER';
export type MobileAction = MarkKind | 'UNAVAILABLE';
export type StopState = 'PLANNED' | 'IN_PROGRESS' | 'DONE';

export interface EmployeeStaffItem {
  staff_id: string;
  display_name: string | null;
  profiles: Profile[];
  service_center_id: string;
  service_center_name: string | null;
  shift: { start: string; end: string };
  route_status: 'ACTIVE' | 'UNAVAILABLE';
  visits: number;
  visits_done: number;
}

export interface EmployeeStaffList {
  date: string;
  plan_version: number;
  as_of: DateTime;
  staff: EmployeeStaffItem[];
}

export interface EmployeeOrder {
  order_id: string;
  work_type_code: WorkTypeCode;
  work_type_name: string;
  technology: Technology;
  address: string;
  lat: number;
  lon: number;
  building_type: BuildingType;
  requires_height: boolean;
  slot: TimeWindow;
  latest_start: DateTime;
  required_equipment: Equipment;
  is_repeat: boolean;
}

export interface EmployeeStop {
  seq: number;
  type: StopType;
  location_id: string;
  location: { address: string; lat: number; lon: number; name?: string } | null;
  order_id: string | null;
  order: EmployeeOrder | null;
  arrival: DateTime;
  start: DateTime;
  end: DateTime;
  travel_sec: number | null;
  distance_m: number | null;
  plan_status: StopStatus | null;
  state: StopState;
  equipment_after: Equipment | null;
  explanation: string | null;
  marks: Record<MarkKind, DateTime | null> | null;
  next_mark: MarkKind | null;
  allowed_actions: MobileAction[];
  call_window: TimeWindow | null;
}

export interface EmployeeMark {
  order_id: string;
  kind: MarkKind;
  at: DateTime;
  recorded_at: DateTime;
  staff_id: string;
}

export interface EmployeeRoute {
  date: string;
  plan_version: number;
  as_of: DateTime;
  staff: {
    staff_id: string;
    display_name: string | null;
    profiles: Profile[];
    technologies: Technology[];
    service_center: { service_center_id: string; name: string; address: string; lat: number; lon: number } | null;
    shift: Staff['shift'];
  };
  on_shift: boolean;
  route_status: 'ACTIVE' | 'UNAVAILABLE' | null;
  unavailable_from: DateTime | null;
  unavailable_until: DateTime | null;
  allowed_actions: MobileAction[];
  progress: { visits_total: number; visits_done: number };
  next: {
    seq: number;
    order_id: string;
    call_window: TimeWindow | null;
    call_hint: string | null;
    next_mark: MarkKind | null;
  } | null;
  stops: EmployeeStop[];
  marks: EmployeeMark[];
}

export interface EmployeeMarkEvent {
  event_id: string;
  type: string;
  accepted: boolean;
  duplicate: boolean;
  message: string | null;
}

export interface EmployeeMarkResult {
  accepted: boolean;
  duplicate: boolean;
  kind: MobileAction;
  order_id: string | null;
  staff_id: string;
  at: DateTime;
  recorded_at: DateTime;
  event_sent: boolean;
  event: EmployeeMarkEvent | null;
  event_note: string | null;
  plan_version: number;
  route_changed: boolean;
  added_order_ids: string[];
  removed_order_ids: string[];
  changed_staff_ids: string[];
  marks: Record<MarkKind, DateTime | null>;
  next_mark: MarkKind | null;
  allowed_actions: MobileAction[];
}

// ---------------------------------------------------------------- справочник сотрудников (/ui/directory/staff)

export interface StaffProfile {
  staff_id: string;
  /** «Фамилия И. О.» */
  display_name: string;
  full_name: string;
  first_name: string;
  last_name: string;
  middle_name: string | null;
  phone: string | null;
  position: string | null;
  personnel_number: string | null;
  note: string | null;
  profile: Profile | null;
  profiles: Profile[];
  service_center_id: string | null;
  /** false — сотрудник есть только в справочнике, в план попадёт при пересборке дня. */
  in_planner: boolean;
  /** Рюкзак АО из staff.json (синтетика); null — сотрудника нет в планировщике. */
  equipment_start_data: Equipment | null;
  equipment_capacity_data: Equipment | null;
  /** Эффективный рюкзак (с переопределением диспетчера), им грузится демо-день. */
  equipment_start: Equipment | null;
  equipment_capacity: Equipment | null;
  equipment_override: boolean;
}

/** Подсказка: p90 расхода АО за смену из последнего сценария «Рюкзак АО». */
export interface RecommendedBackpack {
  date: string;
  percentile: number;
  by_profile: Partial<Record<Profile, Partial<Equipment>>>;
}

export interface StaffDirectoryList {
  staff: StaffProfile[];
  count: number;
  persisted: boolean;
  recommended_backpack: RecommendedBackpack | null;
}

export type EquipmentScope = 'ALL' | Profile;

export interface EquipmentBulkInput {
  start?: Partial<Equipment>;
  capacity?: Partial<Equipment>;
  profile?: EquipmentScope;
}

export interface EquipmentBulkResult {
  updated?: number;
  reset?: number;
  profile: EquipmentScope;
  applies_from_next_replan: boolean;
  message: string;
  persisted: boolean;
}

export interface StaffProfileInput {
  first_name: string;
  last_name: string;
  middle_name?: string | null;
  phone?: string | null;
  position?: string | null;
  personnel_number?: string | null;
  note?: string | null;
  profile?: Profile;
  service_center_id?: string;
  equipment_start?: Partial<Equipment>;
  equipment_capacity?: Partial<Equipment>;
}

export interface StaffProfileSaved extends StaffProfile {
  persisted: boolean;
  applies_from_next_replan?: boolean;
  message?: string;
}

/* ---------------------------------------------------------------- /ui/orders (наряды из панели «Данные») */

export interface OrderCatalogWorkType {
  code: WorkTypeCode;
  name: string;
  profiles: Profile[];
  duration_min: number;
  pgp_relevant: boolean;
  /** Есть отдельный срок для повторного обращения (REPAIR). */
  repeat_allowed: boolean;
}

export interface OrderCatalogBuilding {
  building_id: string;
  address: string;
  lat: number;
  lon: number;
  building_type: BuildingType;
  overhead_line: boolean;
  technologies: Technology[];
  /** Типы работ, которые выполняются по этому адресу. */
  work_types: WorkTypeCode[];
}

export interface OrderCatalogStaff {
  staff_id: string;
  display_name: string | null;
  profiles: Profile[];
  technologies: Technology[];
  height_permit: boolean;
  on_shift: boolean;
}

export interface OrderCatalog {
  work_types: OrderCatalogWorkType[];
  buildings: OrderCatalogBuilding[];
  buildings_total: number;
  buildings_count: number;
  staff: OrderCatalogStaff[];
}

export interface CreateOrderInput {
  building_id: string;
  work_type: WorkTypeCode;
  /** null — ближайшее стандартное окно; иначе ЧЧ:ММ. */
  slot?: { start: string; end: string } | null;
  is_repeat?: boolean;
  preferred_staff_id?: string | null;
}

export interface CreateOrderResult {
  accepted: boolean;
  order_id: string;
  order: Order;
  work_type_name: string;
  outcome: 'ASSIGNED' | 'UNASSIGNED' | 'PGP';
  assignment: { staff_id: string; staff_name: string | null; eta: DateTime; planned_start: DateTime; planned_end: DateTime } | null;
  unassigned: UnassignedOrder | null;
  pgp: { group_id: string; access_node_id: string; comment: string | null; order_ids: string[]; message: string } | null;
  preferred: { staff_id: string; staff_name: string; honored: boolean; message: string } | null;
  plan_version: number;
  as_of: DateTime;
  warnings: string[];
  event: DemoStepItem;
}

export interface CancelOrderResult {
  accepted: boolean;
  cancelled: boolean;
  order_id: string;
  summary: string;
  freed: { staff_id: string; staff_name: string | null } | null;
  plan_version: number;
  as_of: DateTime;
  changed_staff_ids: string[];
  message: string | null;
  warnings: string[];
  event: DemoStepItem;
}

// ---------------------------------------------------------------- журнал истории (/ui/history, без БД — 503 JOURNAL_UNAVAILABLE)

export interface HistoryVersion {
  version: number;
  as_of: DateTime | null;
  based_on_event_id: string | null;
  computation_ms: number | null;
  visits_planned: number | null;
  unassigned: number | null;
  pgp_orders: number | null;
  cancelled: number | null;
  late_risk_active: number | null;
  travel_sec_total: number | null;
  utilization_cv: number | null;
}

export interface HistoryVersions {
  date: string;
  versions: HistoryVersion[];
}
```

## File: frontend/src/App.tsx
```typescript
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DockviewReact, type DockviewApi, type DockviewReadyEvent, type DockviewTheme } from 'dockview-react';
import 'dockview-react/dist/styles/dockview.css';
import './dock/dock.css';
import { TopBar } from './components/TopBar';
import { KpiStrip } from './components/KpiStrip';
import { WindowsMenu } from './components/WindowsMenu';
import { createMapMemory } from './components/DispatchMap';
import { usePlan } from './hooks/usePlan';
import { StaffDirectoryContext, useStaffDirectory } from './hooks/useStaffDirectory';
import { DataPanel } from './components/DataPanel';
import { staffDirectory, type RouteMode, type Selection, type Source } from './planModel';
import type { Scenario, UiOrder } from './types/api';
import { DOCK_COMPONENTS, DockDataContext, DockTab, DockWatermark, GroupActions, type DockData } from './dock/DockPanels';
import {
  buildDefaultLayout,
  forgetLayout,
  isPanelShown,
  openPanel,
  restoreLayout,
  revealPanel,
  saveLayout,
  type PanelId,
} from './dock/layout';

/** Тема dockview: переменные — в dock/dock.css из токенов Атомаро; базовый класс light даёт служебные. */
const DOCK_THEME: DockviewTheme = {
  name: 'atomaro',
  className: 'dockview-theme-light dockview-theme-atomaro',
  colorScheme: 'light',
  gap: 12,
  dndOverlayMounting: 'relative',
  dndPanelOverlay: 'group',
  dndTabIndicator: 'line',
};

const SOURCE_PANEL: Record<Source, PanelId> = {
  table: 'orders',
  late: 'late',
  unassigned: 'unassigned',
  scenarios: 'scenarios',
  map: 'map',
  gantt: 'gantt',
  stats: 'stats',
};

/** Список, в котором стоит показать наряд, выбранный на карте или в Ганте. */
function listFor(api: DockviewApi, o: UiOrder | undefined): PanelId | null {
  if (o?.late_risk && isPanelShown(api, 'late')) return null;
  if (o && (o.status === 'UNASSIGNED' || o.status === 'PGP') && isPanelShown(api, 'unassigned')) return null;
  if (isPanelShown(api, 'orders')) return null;
  return 'orders';
}

export function App() {
  const plan = usePlan();
  const directory = useStaffDirectory();
  const [dataOpen, setDataOpen] = useState(false);
  // ФИО из справочника поверх staff_name нарядов: правка в панели «Данные» видна сразу, без пересчёта плана.
  const names = directory.names;
  const orders = useMemo(
    () =>
      names.size
        ? plan.orders.map((o) => (o.staff_id && names.has(o.staff_id) ? { ...o, staff_name: names.get(o.staff_id)! } : o))
        : plan.orders,
    [plan.orders, names],
  );
  const s = useMemo(() => ({ ...plan, orders }), [plan, orders]);
  const [scenario, setScenario] = useState<Scenario>('dense');
  const [orderSel, setOrderSel] = useState<Selection | null>(null);
  const [staffSel, setStaffSel] = useState<Selection | null>(null);
  const [routeMode, setRouteMode] = useState<RouteMode>('all');
  const [openIds, setOpenIds] = useState<ReadonlySet<PanelId>>(() => new Set());
  const nonce = useRef(0);
  const apiRef = useRef<DockviewApi | null>(null);
  const mapMemory = useRef(createMapMemory()).current;

  // dockview 8.8: ресайз плавающего окна запоминает исходную точку на ПЕРВОМ pointermove, а не на
  // pointerdown, и считает, что курсор ровно на краю. Если первое движение уже далеко (быстрый рывок,
  // мало событий), смещение до него теряется: окно сдвигается вместо того, чтобы растягиваться.
  // Сразу после pointerdown по рукоятке шлём pointermove с координатой самого края — отсчёт верный.
  useEffect(() => {
    const onDown = (e: PointerEvent) => {
      const h = e.target instanceof HTMLElement ? e.target : null;
      const dir = h?.className.match(/^dv-resize-handle-(\w+)$/)?.[1];
      const box = h?.parentElement;
      if (!dir || !box?.classList.contains('dv-resize-container')) return;
      const r = box.getBoundingClientRect();
      const x = dir.includes('left') ? r.left : dir.includes('right') ? r.right : e.clientX;
      const y = dir.startsWith('top') ? r.top : dir.startsWith('bottom') ? r.bottom : e.clientY;
      window.dispatchEvent(
        new PointerEvent('pointermove', { clientX: x, clientY: y, pointerId: e.pointerId, buttons: 1, bubbles: true }),
      );
    };
    // фаза всплытия на window: обработчик dockview на рукоятке уже подписался на pointermove
    window.addEventListener('pointerdown', onDown);
    return () => window.removeEventListener('pointerdown', onDown);
  }, []);

  const staff = useMemo(() => staffDirectory(s.reference, s.plan, names), [s.reference, s.plan, names]);
  const ordersRef = useRef(s.orders);
  ordersRef.current = s.orders;

  /** Связка между панелями: если нужная панель закрыта или скрыта во вкладке — показываем её. */
  const revealFor = useCallback((source: Source, o: UiOrder | undefined) => {
    const api = apiRef.current;
    if (!api) return;
    const from = SOURCE_PANEL[source];
    if (source !== 'map') revealPanel(api, 'map', from);
    // Из «Статистики» выбирают нераспределённые наряды — показываем их список.
    if (source === 'stats' && o && (o.status === 'UNASSIGNED' || o.status === 'PGP')) revealPanel(api, 'unassigned', from);
    if (source === 'map' || source === 'gantt') {
      const list = listFor(api, o);
      if (list) revealPanel(api, list, from);
    }
  }, []);

  const selectOrder = useCallback(
    (id: string, source: Source) => {
      const o = ordersRef.current.find((x) => x.order_id === id);
      const n = ++nonce.current;
      setOrderSel({ id, source, nonce: n });
      if (o?.staff_id) setStaffSel({ id: o.staff_id, source, nonce: n, fit: false });
      revealFor(source, o);
    },
    [revealFor],
  );

  const selectStaff = useCallback((id: string | null, source: Source, fit: boolean) => {
    setStaffSel(id ? { id, source, nonce: ++nonce.current, fit } : null);
    if (id) setRouteMode((m) => (m === 'none' ? 'all' : m));
    if (id && fit && (source === 'gantt' || source === 'stats') && apiRef.current) revealPanel(apiRef.current, 'map', source);
  }, []);

  const syncOpen = useCallback(() => {
    const api = apiRef.current;
    if (!api) return;
    setOpenIds(new Set(api.panels.map((p) => p.id as PanelId)));
  }, []);

  const onReady = useCallback(
    (e: DockviewReadyEvent) => {
      const api = e.api;
      apiRef.current = api;
      if (!api.panels.length && !restoreLayout(api)) buildDefaultLayout(api);
      syncOpen();
      api.onDidAddPanel(syncOpen);
      api.onDidRemovePanel(syncOpen);
      api.onDidLayoutFromJSON(syncOpen);
      // Сохраняем с задержкой (перетаскивание границы шлёт много событий). Проверка apiRef:
      // в StrictMode первый экземпляр dockview уничтожается, его таймер не должен затереть раскладку.
      let t: ReturnType<typeof setTimeout> | undefined;
      const flush = () => {
        clearTimeout(t);
        t = undefined;
        if (apiRef.current === api) saveLayout(api);
      };
      // dockview 8.8: минимальный размер плавающего окна — статика Overlay (20x20) и не настраивается.
      // Берём класс у любого плавающего окна и поднимаем минимум до 240x160 (иначе при сжатии
      // за левый/верхний край окно «уезжает», а CSS min-size лишь маскирует размер).
      const patchFloatMin = () => {
        const fg = (api as unknown as { component?: { floatingGroups?: { overlay?: { constructor: unknown } }[] } })
          .component?.floatingGroups;
        const O = fg?.[0]?.overlay?.constructor as { MINIMUM_WIDTH?: number; MINIMUM_HEIGHT?: number } | undefined;
        if (O) {
          O.MINIMUM_WIDTH = 240;
          O.MINIMUM_HEIGHT = 160;
        }
      };
      patchFloatMin();
      api.onDidLayoutChange(() => {
        patchFloatMin();
        clearTimeout(t);
        t = setTimeout(flush, 250);
      });
      window.addEventListener('pagehide', () => {
        if (t !== undefined) flush();
      });
    },
    [syncOpen],
  );

  const togglePanel = useCallback((id: PanelId, show: boolean) => {
    const api = apiRef.current;
    if (!api) return;
    if (show) openPanel(api, id);
    else api.getPanel(id)?.api.close();
  }, []);

  const resetLayout = useCallback(() => {
    const api = apiRef.current;
    if (!api) return;
    forgetLayout();
    buildDefaultLayout(api);
    saveLayout(api);
  }, []);

  const late = s.plan?.alerts.filter((a) => a.type === 'LATE_RISK' && a.status === 'ACTIVE').length ?? 0;
  const unassigned = (s.plan?.unassigned.length ?? 0) + (s.plan?.pgp_groups.length ?? 0);

  const dockData: DockData = {
    s,
    staff,
    orderSel,
    staffSel,
    routeMode,
    setRouteMode,
    selectOrder,
    selectStaff,
    mapMemory,
    counts: {
      orders: { n: s.orders.length },
      late: { n: late, warn: late > 0 },
      unassigned: { n: unassigned },
    },
    openPanel: (id) => togglePanel(id, true),
  };

  return (
    <StaffDirectoryContext.Provider value={directory}>
    <div className="app">
      <TopBar
        demo={s.demo}
        plan={s.plan}
        busy={s.busy}
        scenario={scenario}
        onScenario={setScenario}
        onLoad={() => {
          setOrderSel(null);
          setStaffSel(null);
          // Загрузка дня применяет рюкзаки АО из справочника — баннер «пересоберите день» больше не нужен.
          void s.loadDay(scenario).then((ok) => ok && directory.clearEquipmentPending());
        }}
        onStep={s.step}
        lastStep={s.lastStep}
        windows={<WindowsMenu open={openIds} onToggle={togglePanel} onReset={resetLayout} />}
        onData={() => setDataOpen(true)}
        dataOpen={dataOpen}
      />
      <KpiStrip plan={s.plan} orders={s.orders} />
      {s.error && (
        <div className="error" role="alert">
          {s.error}
        </div>
      )}

      <main className="work">
        <DockDataContext.Provider value={dockData}>
          <DockviewReact
            className="dock"
            theme={DOCK_THEME}
            components={DOCK_COMPONENTS}
            defaultTabComponent={DockTab}
            rightHeaderActionsComponent={GroupActions}
            watermarkComponent={DockWatermark}
            defaultRenderer="always"
            floatingGroupBounds="boundedWithinViewport"
            onReady={onReady}
          />
        </DockDataContext.Provider>
      </main>
      {dataOpen && (
        <DataPanel
          serviceCenters={s.reference?.service_centers ?? []}
          onClose={() => setDataOpen(false)}
          orders={s.orders}
          day={s.demo?.date}
          asOf={s.plan?.as_of ?? s.demo?.as_of}
          onPlanEvent={s.applyEvent}
          replanBusy={s.busy === 'load'}
          onReplan={async () => {
            // Пересборка тем же сценарием, что загружен сейчас (иначе выбранным в шапке).
            setOrderSel(null);
            setStaffSel(null);
            const ok = await s.loadDay(s.demo?.scenario ?? scenario);
            if (ok) {
              directory.clearEquipmentPending();
              await directory.reload();
            }
            return ok;
          }}
        />
      )}
    </div>
    </StaffDirectoryContext.Provider>
  );
}
```

## File: frontend/src/format.ts
```typescript
import type { AlertAction, UiOrderStatus, UnassignedReason, WorkTypeCode } from './types/api';
import type { BadgeScheme } from './components/ui';

/** Время HH:MM в часовом поясе сервера (+05:00, Екатеринбург) — берём из ISO как есть. */
export const hhmm = (iso?: string | null) => (iso ? iso.slice(11, 16) : '—');

const MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
export const dayLabel = (date?: string) => {
  if (!date) return '—';
  const [y, m, d] = date.split('-').map(Number);
  return `${d} ${MONTHS[m - 1]} ${y}`;
};

export const duration = (sec?: number) => {
  if (sec == null) return '—';
  const h = Math.floor(sec / 3600);
  const m = Math.round((sec % 3600) / 60);
  return h ? `${h} ч ${m} мин` : `${m} мин`;
};

export const STATUS: Record<UiOrderStatus, { label: string; scheme: BadgeScheme }> = {
  ROUTED: { label: 'В маршруте', scheme: 'success' },
  UNASSIGNED: { label: 'Не распределён', scheme: 'error' },
  PGP: { label: 'ПГП', scheme: 'status-01' },
  CANCELLED: { label: 'Отменён', scheme: 'neutral' },
  UNKNOWN: { label: 'Нет данных', scheme: 'neutral' },
};

export const WORK_TYPE_SHORT: Record<WorkTypeCode, string> = {
  INST_PON_MKD: 'Инсталляция, МКД PON',
  INST_FTTB_MKD: 'Инсталляция, МКД FTTB',
  INST_PRIVATE_VLS: 'Инсталляция, частный ВЛС',
  ADD_SERVICE: 'Доподключение',
  SURVEY_PRIVATE: 'Обследование',
  REPAIR: 'Неисправность',
  DEGRADATION: 'Деградация',
  DISMANTLE: 'Демонтаж',
};

export const REASON: Record<UnassignedReason, string> = {
  NO_QUALIFIED_STAFF: 'Нет подходящего сотрудника',
  NO_SHIFT_TIME: 'Не хватает времени смены',
  SLOT_UNREACHABLE: 'Не успеть в слот',
  NO_EQUIPMENT: 'Нет оборудования',
  STAFF_UNAVAILABLE: 'Исполнитель выбыл',
  OTHER: 'Другое',
};

export const ACTION: Record<AlertAction | 'ESCALATE', string> = {
  REASSIGN: 'Передать другому',
  RESCHEDULE: 'Перенести визит',
  TRANSFER_TO_STATION: 'Передать на станцию',
  ESCALATE: 'Решение диспетчера',
  NONE: 'Без действия',
};

export const EVENT_TYPE: Record<string, string> = {
  NEW_ORDER: 'Новый наряд',
  ORDER_CANCELLED: 'Отмена',
  VISIT_DELAYED: 'Визит затянулся',
  ORDER_TRANSFERRED: 'Передан на станцию',
  STAFF_UNAVAILABLE: 'Сотрудник выбыл',
  TIME_TICK: 'Ход времени',
};

export const window2 = (w?: { start: string; end: string } | null) => (w ? `${hhmm(w.start)}–${hhmm(w.end)}` : '—');

/** Дата слота, если она не совпадает с днём плана (перенос на другой день). */
export const windowWithDay = (w: { start: string; end: string } | null | undefined, day?: string) => {
  if (!w) return '—';
  const d = w.start.slice(0, 10);
  if (day && d !== day) return `${Number(d.slice(8, 10))}.${d.slice(5, 7)} ${window2(w)}`;
  return window2(w);
};
```

## File: frontend/src/main.tsx
```typescript
import { lazy, StrictMode, Suspense, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './theme/tokens.css';
import './styles.css';

/** Экран диспетчера и мобильный экран — разные чанки: телефону не нужны dockview и карта диспетчера. */
const App = lazy(() => import('./App').then((m) => ({ default: m.App })));
/** Мобильный экран сотрудника — отдельный чанк, грузится только по адресу #/m (или #/m/ST-001). */
const MobileApp = lazy(() => import('./mobile/MobileApp'));

const isMobileHash = () => /^#\/m(\/|$)/.test(window.location.hash);

function Root() {
  const [mobile, setMobile] = useState(isMobileHash);
  useEffect(() => {
    const on = () => setMobile(isMobileHash());
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  return <Suspense fallback={null}>{mobile ? <MobileApp /> : <App />}</Suspense>;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
);
```

## File: frontend/src/planModel.ts
```typescript
// Общие вычисления над планом для карты, диаграммы Ганта и списков.
import type { Plan, Profile, ServiceCenter, Stop, UiOrder, UiReference } from './types/api';

/** Палитра маршрутов: --atmr-route-01..12 в theme/tokens.css, после 12-го цвета повтор. */
export const ROUTE_COLORS = 12;
const routeToken = (i: number) => `--atmr-route-${String((i % ROUTE_COLORS) + 1).padStart(2, '0')}`;
export const routeVar = (i: number) => `var(${routeToken(i)})`;
/** Canvas Leaflet не понимает var(--…): читаем значения из CSS. */
export function routeColorValues(): string[] {
  const cs = getComputedStyle(document.documentElement);
  return Array.from({ length: ROUTE_COLORS }, (_, i) => cs.getPropertyValue(routeToken(i)).trim());
}

/** Минуты от полуночи по локальному времени ISO (+05:00 берём как есть, как и hhmm). */
export const minutesOf = (iso?: string | null): number => {
  if (!iso) return NaN;
  return Number(iso.slice(11, 13)) * 60 + Number(iso.slice(14, 16)) + (Number(iso.slice(17, 19)) || 0) / 60;
};
/** "09:00" → 540 */
export const minutesOfHm = (hm?: string | null): number => {
  if (!hm) return NaN;
  const [h, m] = hm.split(':').map(Number);
  return h * 60 + (m || 0);
};

export const PROFILE_SHORT: Record<Profile, string> = { INSTALL: 'Инсталляция', LTP3: 'ЛТП-3' };

export interface StaffInfo {
  id: string;
  name: string;
  scId: string;
  profiles: Profile[];
  shift?: { start: string; end: string };
  /** Индекс цвета маршрута (стабилен, пока не меняется справочник). */
  color: number;
}

/** Справочник сотрудников + те, кто есть только в плане. Цвет — по порядку staff_id.
 *  names — «Фамилия И. О.» из справочника ФИО; без него остаётся display_name справочника. */
export function staffDirectory(
  reference: UiReference | null,
  plan: Plan | null,
  names?: ReadonlyMap<string, string>,
): Map<string, StaffInfo> {
  const res = new Map<string, StaffInfo>();
  for (const s of reference?.staff ?? []) {
    res.set(s.staff_id, {
      id: s.staff_id,
      name: names?.get(s.staff_id) || s.display_name || s.staff_id,
      scId: s.service_center_id,
      profiles: s.profiles,
      shift: { start: s.shift.start, end: s.shift.end },
      color: 0,
    });
  }
  for (const r of plan?.routes ?? []) {
    if (!res.has(r.staff_id)) {
      res.set(r.staff_id, { id: r.staff_id, name: names?.get(r.staff_id) ?? r.staff_id, scId: r.service_center_id, profiles: [], color: 0 });
    }
  }
  [...res.keys()].sort().forEach((id, i) => (res.get(id)!.color = i));
  return res;
}

export type LatLon = [number, number];

/** Координаты точек плана: дома (building_id) и СЦ (service_center_id). */
export function locationIndex(orders: UiOrder[], scs: ServiceCenter[]): Map<string, LatLon> {
  const m = new Map<string, LatLon>();
  for (const o of orders) m.set(o.building_id, [o.lat, o.lon]);
  for (const sc of scs) m.set(sc.service_center_id, [sc.lat, sc.lon]);
  return m;
}

export function stopLatLon(
  s: Stop,
  scId: string,
  loc: Map<string, LatLon>,
  ordersById: Map<string, UiOrder>,
): LatLon | undefined {
  if (s.type === 'VISIT' && s.order_id) {
    const o = ordersById.get(s.order_id);
    if (o) return [o.lat, o.lon];
  }
  return loc.get(s.location_id) ?? (s.type !== 'VISIT' && s.type !== 'BREAK' ? loc.get(scId) : undefined);
}

/** Совместные наряды (JOINT_ORDERS=on): визит напарника идёт в плане как ORD-…-B. */
export const PARTNER_SUFFIX = '-B';
/** Основной наряд для визита напарника, иначе null. */
export const jointMainOf = (orderId?: string | null): string | null =>
  orderId && orderId.endsWith(PARTNER_SUFFIX) ? orderId.slice(0, -PARTNER_SUFFIX.length) : null;

/** Пары плана: основной наряд → { основной сотрудник, напарник }. */
export function jointPairs(plan: Plan | null): Map<string, { main?: string; partner?: string }> {
  const res = new Map<string, { main?: string; partner?: string }>();
  const visits = new Map<string, string>();
  for (const r of plan?.routes ?? []) for (const s of r.stops) if (s.type === 'VISIT' && s.order_id) visits.set(s.order_id, r.staff_id);
  for (const [id, staff] of visits) {
    const main = jointMainOf(id);
    if (main) res.set(main, { main: visits.get(main), partner: staff });
  }
  return res;
}

export type Source = 'table' | 'late' | 'unassigned' | 'scenarios' | 'map' | 'gantt' | 'stats';

/** Метрики одной версии плана для графика динамики (как в /ui/history/{date}/versions). */
export interface MetricPoint {
  date: string;
  version: number;
  asOf: string | null;
  visits: number;
  unassigned: number;
  lateRisk: number;
  travelSec: number;
}

export function planMetrics(plan: Plan): MetricPoint {
  const sm = plan.summary;
  return {
    date: plan.date,
    version: plan.plan_version,
    asOf: plan.as_of,
    // визит напарника (ORD-…-B) не считается: совместная пара — один наряд
    visits:
      sm?.visits_planned ??
      plan.routes.reduce((n, r) => n + r.stops.filter((s) => s.type === 'VISIT' && !jointMainOf(s.order_id)).length, 0),
    unassigned: sm?.unassigned ?? plan.unassigned.length,
    lateRisk: plan.alerts.filter((a) => a.type === 'LATE_RISK' && a.status === 'ACTIVE').length,
    travelSec: sm?.travel_sec_total ?? plan.routes.reduce((n, r) => n + (r.totals?.travel_sec ?? 0), 0),
  };
}

/** Накопитель метрик сессии: одна точка на версию (повтор версии заменяет точку), не больше limit. */
export function pushMetric(list: MetricPoint[], plan: Plan, limit = 200): MetricPoint[] {
  const p = planMetrics(plan);
  const rest = list.filter((x) => x.date === p.date && x.version !== p.version);
  rest.push(p);
  rest.sort((a, b) => a.version - b.version);
  return rest.length > limit ? rest.slice(rest.length - limit) : rest;
}
export interface Selection {
  id: string;
  source: Source;
  /** Растёт на каждый явный выбор: повторный клик по тому же объекту снова центрирует/прокручивает. */
  nonce: number;
  /** Для сотрудника: вписать его маршрут в карту. */
  fit?: boolean;
}
export type RouteMode = 'all' | 'selected' | 'none';
```

## File: frontend/src/styles.css
```css
/* Раскладка экрана диспетчера. Все значения — через токены из theme/tokens.css. */

*,
*::before,
*::after {
  box-sizing: border-box;
}
html,
body,
#root {
  height: 100%;
  margin: 0;
}
body {
  font: var(--atmr-font-body-m);
  color: var(--atmr-text-primary);
  background: var(--atmr-bg-page);
  -webkit-font-smoothing: antialiased;
}
button,
input,
select {
  font: inherit;
  color: inherit;
}
:focus-visible {
  outline: 2px solid var(--atmr-accent);
  outline-offset: 2px;
}
.num {
  font-variant-numeric: tabular-nums;
}
h1,
h2,
h3,
p {
  margin: 0;
}

/* ---------------------------------------------------------------- каркас */
.app {
  height: 100vh;
  display: flex;
  flex-direction: column;
  /* clip, а не только hidden: hidden-контейнер можно прокрутить программно (scrollIntoView, фокус),
     и шапка с KPI уезжает вверх. clip запрещает любую прокрутку каркаса. */
  overflow: hidden;
  overflow: clip;
}
.app > .topbar,
.app > .kpi,
.app > .error {
  flex: none;
}
/* Док-раскладка (dockview) занимает остаток высоты окна; страница не скроллится.
   Панели и их тема — в dock/dock.css. */
.work {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0 var(--atmr-spacing-4x) var(--atmr-spacing-4x);
}
.panel {
  background: var(--atmr-bg-surface1);
  border-radius: var(--atmr-border-radius-m);
  box-shadow: var(--atmr-shadow-s);
  min-height: 0;
}
.panel--lists {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ---------------------------------------------------------------- верхняя панель */
.topbar {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-5x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-4x);
  background: var(--atmr-bg-surface1);
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
  min-height: 56px;
}
.topbar__brand {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  flex-shrink: 0;
}
.topbar__mark {
  height: 32px;
  width: auto;
  display: block;
}
.topbar__title {
  font: var(--atmr-font-heading-h5);
  white-space: nowrap;
}
.topbar__facts {
  display: flex;
  gap: var(--atmr-spacing-5x);
  margin: 0;
  flex-shrink: 0;
}
.topbar__facts div {
  display: flex;
  flex-direction: column;
}
.topbar__facts dt {
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-tertiary);
}
.topbar__facts dd {
  margin: 0;
  font-weight: 600;
  white-space: nowrap;
}
.topbar__last {
  flex: 1 1 auto;
  min-width: 0;
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.topbar__actions {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  flex-shrink: 0;
}

/* ---------------------------------------------------------------- KPI */
.kpi {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  margin: var(--atmr-spacing-3x) var(--atmr-spacing-4x);
  background: var(--atmr-bg-surface1);
  border-radius: var(--atmr-border-radius-m);
  box-shadow: var(--atmr-shadow-s);
  overflow: hidden;
}
.kpi__cell {
  display: flex;
  flex-direction: column;
  gap: var(--atmr-spacing-0_5x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-4x);
  border-left: var(--atmr-border-width-s) solid var(--atmr-border-default);
  border-top: 3px solid transparent;
}
.kpi__cell:first-child {
  border-left: 0;
}
.kpi__value {
  font: var(--atmr-font-display-s);
  white-space: nowrap;
}
.kpi__label {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
}
.kpi__cell--ok {
  border-top-color: var(--atmr-status-success);
}
.kpi__cell--bad {
  border-top-color: var(--atmr-status-error);
}
.kpi__cell--bad .kpi__value {
  color: var(--atmr-status-error);
}
.kpi__cell--pgp {
  border-top-color: var(--atmr-accent);
}
.kpi__cell--warn {
  border-top-color: var(--atmr-brand-orange);
  background: var(--atmr-brand-orange-muted);
}
.kpi__cell--warn .kpi__value {
  color: var(--atmr-brand-orange);
}

.error {
  margin: 0 var(--atmr-spacing-4x) var(--atmr-spacing-3x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-status-error-muted);
  color: var(--atmr-status-error);
  font: var(--atmr-font-body-s);
}

/* ---------------------------------------------------------------- Button */
.btn {
  display: inline-flex;
  align-items: center;
  gap: var(--atmr-spacing-1x);
  border: var(--atmr-border-width-s) solid transparent;
  border-radius: var(--atmr-border-radius-s);
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.12s, border-color 0.12s;
}
.btn--s {
  height: 32px;
  padding: 0 var(--atmr-spacing-3x);
  font: 500 13px/18px var(--atmr-font-family);
}
.btn--m {
  height: 40px;
  padding: 0 var(--atmr-spacing-4x);
}
.btn--l {
  height: 48px;
  padding: 0 var(--atmr-spacing-5x);
}
.btn--xl {
  height: 56px;
  padding: 0 var(--atmr-spacing-6x);
}
.btn--primary.btn--accent {
  background: var(--atmr-accent);
  color: var(--atmr-accent-on);
}
.btn--primary.btn--accent:hover:not(:disabled) {
  background: var(--atmr-accent-hover);
}
.btn--secondary.btn--accent {
  background: var(--atmr-accent-muted);
  color: var(--atmr-accent);
}
.btn--outline.btn--accent {
  border-color: var(--atmr-accent);
  background: transparent;
  color: var(--atmr-accent);
}
.btn--outline.btn--accent:hover:not(:disabled),
.btn--ghost.btn--accent:hover:not(:disabled) {
  background: var(--atmr-accent-muted);
}
.btn--ghost {
  background: transparent;
  color: var(--atmr-accent);
}
.btn--neutral {
  color: var(--atmr-text-primary);
  background: var(--atmr-bg-surface3);
}
.btn:disabled {
  cursor: default;
  opacity: 0.45;
}

/* ---------------------------------------------------------------- Badge */
.badge {
  display: inline-flex;
  align-items: center;
  gap: var(--atmr-spacing-1x);
  border-radius: var(--atmr-border-radius-xs);
  font-weight: 500;
  white-space: nowrap;
  --b-fg: var(--atmr-status-info);
  --b-bg: var(--atmr-status-info-muted);
}
.badge--s {
  padding: 2px var(--atmr-spacing-2x);
  font: 500 12px/16px var(--atmr-font-family);
}
.badge--2xs {
  padding: 1px var(--atmr-spacing-1x);
  font: 500 11px/14px var(--atmr-font-family);
}
.badge--error {
  --b-fg: var(--atmr-status-error);
  --b-bg: var(--atmr-status-error-muted);
}
.badge--warning {
  --b-fg: var(--atmr-brand-orange);
  --b-bg: var(--atmr-brand-orange-muted);
}
.badge--success {
  --b-fg: var(--atmr-status-success);
  --b-bg: var(--atmr-status-success-muted);
}
.badge--neutral {
  --b-fg: var(--atmr-status-neutral);
  --b-bg: var(--atmr-status-neutral-muted);
}
.badge--status-01 {
  --b-fg: var(--atmr-accent);
  --b-bg: var(--atmr-accent-muted);
}
.badge--primary {
  background: var(--b-fg);
  color: var(--atmr-accent-on);
}
.badge--secondary {
  background: var(--b-bg);
  color: var(--b-fg);
}
.badge--outline {
  border: var(--atmr-border-width-s) solid var(--b-fg);
  color: var(--b-fg);
}
.badge--ghost {
  color: var(--b-fg);
}
.badge__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

/* ---------------------------------------------------------------- Tabs */
.tabs {
  display: flex;
  gap: var(--atmr-spacing-5x);
  padding: 0 var(--atmr-spacing-4x);
  flex-shrink: 0;
}
.tabs--border {
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
}
.tabs__item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  padding: var(--atmr-spacing-3x) 0;
  border: 0;
  background: none;
  color: var(--atmr-text-secondary);
  font-weight: 500;
  cursor: pointer;
}
.tabs--s .tabs__item {
  padding: var(--atmr-spacing-2x) 0;
}
.tabs__item:hover {
  color: var(--atmr-text-primary);
}
.tabs__item--active {
  color: var(--atmr-text-primary);
}
.tabs__item--active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--atmr-accent);
}
.tabs__count {
  min-width: 20px;
  padding: 0 6px;
  border-radius: var(--atmr-border-radius-full);
  background: var(--atmr-bg-surface3);
  color: var(--atmr-text-secondary);
  font: 500 12px/18px var(--atmr-font-family);
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.tabs__count--warn {
  background: var(--atmr-brand-orange);
  color: var(--atmr-accent-on);
}

/* ---------------------------------------------------------------- SegmentedControl */
.seg {
  display: inline-flex;
  padding: 2px;
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-bg-surface3);
  flex-shrink: 0;
}
.seg__item {
  border: 0;
  background: none;
  border-radius: 6px;
  color: var(--atmr-text-secondary);
  white-space: nowrap;
  cursor: pointer;
}
.seg--s .seg__item {
  height: 28px;
  padding: 0 var(--atmr-spacing-2x);
  font: 500 12px/16px var(--atmr-font-family);
}
.seg--m .seg__item {
  height: 36px;
  padding: 0 var(--atmr-spacing-3x);
}
.seg__item--active {
  background: var(--atmr-bg-surface1);
  color: var(--atmr-text-primary);
  box-shadow: var(--atmr-shadow-s);
}
.seg--primary .seg__item--active {
  color: var(--atmr-accent);
}
.seg__item:disabled {
  cursor: default;
  opacity: 0.5;
}

/* ---------------------------------------------------------------- списки */
.list {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  padding: var(--atmr-spacing-3x) var(--atmr-spacing-4x);
  flex-wrap: wrap;
  flex-shrink: 0;
}
.toolbar--note {
  font: var(--atmr-font-body-s);
  color: var(--atmr-text-secondary);
}
.input,
.select {
  height: 32px;
  border: var(--atmr-border-width-s) solid var(--atmr-border-default);
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-bg-surface1);
  padding: 0 var(--atmr-spacing-3x);
  font: var(--atmr-font-body-s);
}
.input {
  flex: 1 1 200px;
  min-width: 160px;
}
.input:hover,
.select:hover {
  border-color: var(--atmr-border-strong);
}
.scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font: var(--atmr-font-body-s);
}
.table th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  background: var(--atmr-bg-surface2);
  color: var(--atmr-text-tertiary);
  font: 500 12px/16px var(--atmr-font-family);
  text-align: left;
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
  white-space: nowrap;
}
.table td {
  padding: 6px var(--atmr-spacing-3x);
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-bg-surface3);
  vertical-align: top;
  white-space: nowrap;
}
.table th:first-child,
.table td:first-child {
  padding-left: var(--atmr-spacing-4x);
}
.table tbody tr:hover {
  background: var(--atmr-bg-surface2);
}
.table .addr {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.table .is-muted {
  color: var(--atmr-text-tertiary);
}
.sub {
  display: block;
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-tertiary);
}
.mono-id {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.flag {
  margin-left: var(--atmr-spacing-1x);
  padding: 0 4px;
  border-radius: var(--atmr-border-radius-xs);
  background: var(--atmr-accent-muted);
  color: var(--atmr-accent);
  font: 500 11px/16px var(--atmr-font-family);
}
.flag--warn {
  background: var(--atmr-brand-orange-muted);
  color: var(--atmr-brand-orange);
}
.empty {
  padding: var(--atmr-spacing-6x) var(--atmr-spacing-4x);
  color: var(--atmr-text-secondary);
  font: var(--atmr-font-body-s);
  max-width: 60ch;
}

/* ---------------------------------------------------------------- карточки */
.cards {
  list-style: none;
  margin: 0;
  padding: 0 var(--atmr-spacing-4x) var(--atmr-spacing-4x);
  display: grid;
  gap: var(--atmr-spacing-2x);
}
.card {
  padding: var(--atmr-spacing-3x);
  border: var(--atmr-border-width-s) solid var(--atmr-border-default);
  border-left-width: 3px;
  border-left-color: var(--atmr-status-error);
  border-radius: var(--atmr-border-radius-s);
  display: grid;
  gap: var(--atmr-spacing-2x);
  font: var(--atmr-font-body-s);
}
.card--warn {
  border-left-color: var(--atmr-brand-orange);
}
.card--pgp {
  border-left-color: var(--atmr-accent);
}
.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--atmr-spacing-2x);
}
.card__grid {
  display: grid;
  grid-template-columns: max-content 1fr max-content 1fr;
  gap: 2px var(--atmr-spacing-3x);
}
.card__grid > span {
  color: var(--atmr-text-tertiary);
}
.card__grid > b {
  font-weight: 500;
}
.card__line--muted {
  color: var(--atmr-text-secondary);
}
.card__action {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--atmr-spacing-2x);
}
.card__text {
  color: var(--atmr-text-secondary);
}
.card__meta {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
}
.late {
  font: 600 16px/20px var(--atmr-font-family);
  color: var(--atmr-brand-orange);
}
.chips {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--atmr-spacing-1x);
}
.chips li {
  padding: 2px var(--atmr-spacing-2x);
  border-radius: var(--atmr-border-radius-xs);
  background: var(--atmr-bg-surface3);
  font-weight: 500;
}
.chips .sub {
  display: inline;
  margin-left: 4px;
}

.split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr);
  align-items: start;
}
.split__col {
  min-width: 0;
}
.section-title {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: var(--atmr-spacing-3x) var(--atmr-spacing-4x) var(--atmr-spacing-2x);
  background: var(--atmr-bg-surface1);
  font: var(--atmr-font-heading-h5);
}
.section-title .num {
  color: var(--atmr-text-tertiary);
  font-weight: 500;
}

/* ---------------------------------------------------------------- заглушки и пустые состояния */
.welcome {
  padding: var(--atmr-spacing-6x) var(--atmr-spacing-4x);
  display: grid;
  gap: var(--atmr-spacing-2x);
  max-width: 64ch;
  color: var(--atmr-text-secondary);
}
.welcome h2 {
  font: var(--atmr-font-heading-h4);
  color: var(--atmr-text-primary);
}
.placeholder {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: var(--atmr-spacing-1x);
  padding: var(--atmr-spacing-4x);
  color: var(--atmr-text-tertiary);
  font: var(--atmr-font-body-s);
  background:
    repeating-linear-gradient(0deg, transparent 0 31px, var(--atmr-bg-surface3) 31px 32px),
    repeating-linear-gradient(90deg, transparent 0 31px, var(--atmr-bg-surface3) 31px 32px),
    var(--atmr-bg-surface1);
}
.placeholder__title {
  font: var(--atmr-font-heading-h5);
  color: var(--atmr-text-secondary);
}

/* 1366 и уже: уплотняем верхнюю панель и KPI */
@media (max-width: 1440px) {
  .topbar {
    gap: var(--atmr-spacing-3x);
  }
  .topbar__facts {
    gap: var(--atmr-spacing-3x);
  }
  .topbar__last {
    display: none;
  }
  .kpi__cell {
    padding: 6px var(--atmr-spacing-3x);
  }
  .kpi__value {
    font-size: 22px;
    line-height: 26px;
  }
  .split {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (prefers-reduced-motion: reduce) {
  * {
    transition: none !important;
  }
}

/* ---------------------------------------------------------------- карта */
.map-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.map-panel__title {
  font: var(--atmr-font-heading-h5);
  color: var(--atmr-text-secondary);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-4x);
}
.dispatch-map-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  /* z-index слоёв Leaflet (до 1000) не должен перекрывать плавающие окна и меню */
  isolation: isolate;
}
.dispatch-map {
  position: absolute;
  inset: 0;
  font-family: inherit;
}
.map-legend {
  position: absolute;
  left: var(--atmr-spacing-3x);
  bottom: var(--atmr-spacing-3x);
  right: 120px; /* не заходим под атрибуцию OSM */
  z-index: 1000;
  display: flex;
  flex-wrap: wrap;
  gap: var(--atmr-spacing-1x);
  pointer-events: none;
}
.map-legend__item {
  pointer-events: auto;
  display: inline-flex;
  align-items: center;
  gap: var(--atmr-spacing-1x);
  padding: var(--atmr-spacing-0_5x) var(--atmr-spacing-2x) var(--atmr-spacing-0_5x) var(--atmr-spacing-1x);
  border: var(--atmr-border-width-s) solid var(--atmr-border-default);
  border-radius: var(--atmr-border-radius-full);
  background: var(--atmr-bg-surface1);
  box-shadow: var(--atmr-shadow-s);
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-primary);
  cursor: pointer;
}
.map-legend__item[aria-pressed='false'] {
  color: var(--atmr-text-tertiary);
  background: var(--atmr-bg-surface2);
  box-shadow: none;
}
.map-legend__item[aria-pressed='false'] .map-legend__dot {
  opacity: 0.35;
}
.map-legend__item:focus-visible {
  outline: 2px solid var(--atmr-accent);
  outline-offset: 1px;
}
.map-legend__count {
  font-variant-numeric: tabular-nums;
  color: var(--atmr-text-secondary);
  font-weight: 600;
}
.map-legend__dot {
  width: 10px;
  height: 10px;
  margin: 0 2px;
  border-radius: 50%;
  box-sizing: border-box;
  flex: none;
}
.map-legend__dot--late {
  background: var(--atmr-map-late);
  box-shadow: 0 0 0 2px var(--atmr-bg-surface1), 0 0 0 3.5px var(--atmr-map-late);
}
.map-legend__dot--routed { background: var(--atmr-accent); }
.map-legend__dot--unassigned { background: var(--atmr-bg-surface1); border: 2px solid var(--atmr-status-neutral); }
.map-legend__dot--pgp { background: var(--atmr-map-pgp); }
.map-legend__dot--cancelled { background: var(--atmr-border-strong); opacity: 0.6; }
.map-popup__warn {
  color: var(--atmr-map-late);
  font-weight: 600;
}
.map-sc {
  background: none;
  border: 0;
}
.map-sc__dot {
  display: block;
  width: 18px;
  height: 18px;
  margin: 2px;
  box-sizing: border-box;
  border-radius: 50%;
  background: var(--atmr-accent);
  border: 3px solid var(--atmr-accent-on);
  box-shadow: 0 0 0 2px var(--atmr-accent), var(--atmr-shadow-s);
}
.map-popup {
  font: var(--atmr-font-body-s);
  line-height: 1.4;
}

/* ---------------------------------------------------------------- выбор в списках */
.table--select tbody tr {
  cursor: pointer;
}
.table tbody tr.is-selected,
.table tbody tr.is-selected:hover {
  background: var(--atmr-accent-muted);
  box-shadow: inset 3px 0 0 var(--atmr-accent);
}
.card--select {
  cursor: pointer;
}
.card--select:hover {
  border-color: var(--atmr-border-strong);
}
.card.is-selected {
  border-color: var(--atmr-accent);
  box-shadow: 0 0 0 1px var(--atmr-accent);
}
.chips li {
  cursor: pointer;
}
.chips li.is-selected {
  background: var(--atmr-accent);
  color: var(--atmr-accent-on);
}
.chips li.is-selected .sub {
  color: inherit;
}

/* ---------------------------------------------------------------- панель над картой */
.map-tools {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x) var(--atmr-spacing-2x) var(--atmr-spacing-4x);
  flex-shrink: 0;
  min-width: 0;
}
.map-tools__title {
  font: var(--atmr-font-heading-h5);
  color: var(--atmr-text-secondary);
  margin-right: auto;
}
.map-tools__staff {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 0 1 260px;
  margin-right: auto;
}
.map-tools__staff .select {
  width: 100%;
  min-width: 0;
  padding-left: 26px;
}
.map-tools__swatch {
  position: absolute;
  left: 10px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--atmr-border-strong);
  pointer-events: none;
}
.map-tools__label {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
}
.map-hint {
  position: absolute;
  top: var(--atmr-spacing-3x);
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  padding: var(--atmr-spacing-1x) var(--atmr-spacing-3x);
  border-radius: var(--atmr-border-radius-full);
  background: var(--atmr-bg-surface1);
  box-shadow: var(--atmr-shadow-m);
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
  white-space: nowrap;
  pointer-events: none;
}
.map-arrow,
.map-num {
  background: none;
  border: 0;
  pointer-events: none !important;
}
.map-arrow span {
  position: relative;
  display: block;
  width: 14px;
  height: 14px;
}
.map-arrow span::before {
  content: "";
  position: absolute;
  left: 3px;
  top: 2px;
  border-style: solid;
  border-width: 5px 0 5px 9px;
  border-color: transparent transparent transparent currentColor;
  filter: drop-shadow(0 0 1px var(--atmr-bg-surface1)) drop-shadow(0 0 1px var(--atmr-bg-surface1));
}
.map-num span {
  display: block;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: var(--atmr-border-radius-full);
  border: 1.5px solid var(--atmr-bg-surface1);
  color: var(--atmr-accent-on);
  font: 600 11px/15px var(--atmr-font-family);
  text-align: center;
  font-variant-numeric: tabular-nums;
  box-shadow: var(--atmr-shadow-s);
}
.map-stop {
  background: none;
  border: 0;
}
.map-stop span {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: var(--atmr-border-radius-xs);
  background: var(--atmr-bg-surface1);
  box-shadow: 0 0 0 1.5px currentColor, var(--atmr-shadow-s);
}
.map-stop svg {
  width: 14px;
  height: 14px;
}

/* ---------------------------------------------------------------- диаграмма Ганта */
.gantt {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}
.gantt__defs {
  position: absolute;
}
.gantt--empty {
  justify-content: center;
  gap: var(--atmr-spacing-1x);
  padding: var(--atmr-spacing-4x);
  color: var(--atmr-text-tertiary);
  font: var(--atmr-font-body-s);
}
.gantt__head {
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-3x);
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-4x);
  flex-shrink: 0;
  min-width: 0;
}
.gantt__title {
  font: var(--atmr-font-heading-h5);
  color: var(--atmr-text-secondary);
  white-space: nowrap;
}
.gantt__meta {
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-tertiary);
  white-space: nowrap;
}
.gantt__legend {
  list-style: none;
  margin: 0 0 0 auto;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 2px var(--atmr-spacing-3x);
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
  min-width: 0;
}
.gantt__legend li {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}
.lg {
  display: inline-block;
  width: 16px;
  height: 10px;
  border-radius: 2px;
}
.lg--visit { background: var(--atmr-accent-muted); box-shadow: inset 0 0 0 1px var(--atmr-accent); }
.lg--progress { background: var(--atmr-accent); }
.lg--done { background: var(--atmr-status-neutral-muted); box-shadow: inset 0 0 0 1px var(--atmr-status-neutral); }
.lg--late { background: var(--atmr-map-late); }
.lg--break { background: var(--atmr-bg-surface3); box-shadow: inset 0 0 0 1px var(--atmr-border-strong); }
.lg--restock { background: var(--atmr-gantt-restock-muted); box-shadow: inset 0 0 0 1px var(--atmr-gantt-restock); }
.lg--road {
  height: 5px;
  background: repeating-linear-gradient(45deg, var(--atmr-text-tertiary) 0 1.5px, var(--atmr-bg-surface3) 1.5px 3.5px);
}
.lg--slot {
  height: 4px;
  border: 1.5px solid var(--atmr-text-tertiary);
  border-top: 0;
  border-radius: 0;
}
.lg--off {
  background: repeating-linear-gradient(-45deg, var(--atmr-status-neutral) 0 1.5px, var(--atmr-status-neutral-muted) 1.5px 4px);
}

.gantt__axis {
  display: flex;
  flex-shrink: 0;
  border-top: var(--atmr-border-width-s) solid var(--atmr-border-default);
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
  background: var(--atmr-bg-surface2);
}
.gantt__axis-label {
  flex: none;
  padding-left: var(--atmr-spacing-4x);
  font: 500 12px/22px var(--atmr-font-family);
  color: var(--atmr-text-tertiary);
}
.gantt__axis-svg {
  display: block;
  overflow: visible;
}
.gantt__axis-svg text {
  font: 500 11px/1 var(--atmr-font-family);
  fill: var(--atmr-text-tertiary);
  font-variant-numeric: tabular-nums;
}
.g-tick {
  stroke: var(--atmr-border-strong);
}
.g-now-label rect {
  fill: var(--atmr-text-primary);
}
.gantt__axis-svg .g-now-label text {
  fill: var(--atmr-bg-surface1);
  font-weight: 600;
}

.gantt__body {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.gantt__canvas {
  position: relative;
}
.g-grid line {
  stroke: var(--atmr-bg-surface3);
  stroke-width: 1;
  shape-rendering: crispEdges;
}
.gantt__now {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 0;
  border-left: 1.5px solid var(--atmr-text-primary);
  z-index: 2;
  pointer-events: none;
}
.gantt__group {
  position: absolute;
  left: 0;
  right: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: var(--atmr-spacing-2x);
  padding: 0 var(--atmr-spacing-4x);
  background: var(--atmr-bg-surface2);
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-border-default);
  font: 600 12px/16px var(--atmr-font-family);
  color: var(--atmr-text-secondary);
}
.gantt__group .num {
  font-weight: 500;
  color: var(--atmr-text-tertiary);
}
.gantt__row {
  position: absolute;
  left: 0;
  right: 0;
  display: flex;
  border-bottom: var(--atmr-border-width-s) solid var(--atmr-bg-surface3);
}
.gantt__row:hover {
  background: var(--atmr-bg-surface2);
}
.gantt__row.is-selected {
  background: var(--atmr-accent-muted);
}
.gantt__who {
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 0 var(--atmr-spacing-2x) 0 var(--atmr-spacing-4x);
  border: 0;
  background: none;
  text-align: left;
  cursor: pointer;
}
.gantt__swatch {
  flex: none;
  width: 4px;
  height: 14px;
  border-radius: 2px;
}
.gantt__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font: 500 12px/16px var(--atmr-font-family);
}
.gantt__profile {
  flex: none;
  margin-left: auto;
  font: var(--atmr-font-description-s);
  color: var(--atmr-text-tertiary);
}
.gantt__row.is-selected .gantt__name {
  color: var(--atmr-accent);
  font-weight: 600;
}
.gantt__track {
  display: block;
  flex: none;
}

/* блоки SVG */
.g-shift { fill: var(--atmr-gantt-shift); }
.g-off { fill: url(#g-hatch-off); }
.g-hatch-off-bg { fill: var(--atmr-status-neutral-muted); }
.g-hatch-off-line { stroke: var(--atmr-status-neutral); stroke-width: 1.5; opacity: 0.6; }
.g-travel { fill: url(#g-hatch-road); }
.g-hatch-road-bg { fill: var(--atmr-bg-surface3); }
.g-hatch-road-line { stroke: var(--atmr-text-tertiary); stroke-width: 1.5; }
.g-wait { stroke: var(--atmr-border-strong); stroke-width: 1; stroke-dasharray: 2 2; }
.g-slot { fill: none; stroke: var(--atmr-text-tertiary); stroke-width: 1; opacity: 0.8; pointer-events: none; }
.g-break { fill: var(--atmr-bg-surface3); stroke: var(--atmr-border-strong); }
.g-restock { fill: var(--atmr-gantt-restock-muted); stroke: var(--atmr-gantt-restock); }
.g-visit { cursor: pointer; }
.g-visit rect { fill: var(--atmr-accent-muted); stroke: var(--atmr-accent); stroke-width: 1; }
.g-visit text {
  font: 600 10px/1 var(--atmr-font-family);
  fill: var(--atmr-accent);
  font-variant-numeric: tabular-nums;
  pointer-events: none;
}
.g-visit--in-progress rect { fill: var(--atmr-accent); }
.g-visit--in-progress text { fill: var(--atmr-accent-on); }
.g-visit--done rect { fill: var(--atmr-status-neutral-muted); stroke: var(--atmr-status-neutral); }
.g-visit--done text { fill: var(--atmr-status-neutral); }
.g-visit--late rect { fill: var(--atmr-map-late); stroke: var(--atmr-map-late); }
.g-visit--late text { fill: var(--atmr-accent-on); }
.g-visit:hover rect { stroke-width: 2; }
/* совместный наряд (JOINT_ORDERS): оба визита пары — с пунктирной обводкой */
.g-visit--joint rect { stroke-dasharray: 4 2; stroke-width: 1.5; }
.g-visit.is-selected rect { stroke: var(--atmr-text-primary); stroke-width: 2.5; }

.gantt-tip {
  position: fixed;
  z-index: 2000;
  display: grid;
  gap: 2px;
  max-width: 290px;
  padding: var(--atmr-spacing-2x) var(--atmr-spacing-3x);
  border-radius: var(--atmr-border-radius-s);
  background: var(--atmr-bg-surface1);
  box-shadow: var(--atmr-shadow-m), 0 0 0 1px var(--atmr-border-default);
  font: var(--atmr-font-description-m);
  color: var(--atmr-text-secondary);
  pointer-events: none;
}
.gantt-tip strong {
  font: 600 13px/18px var(--atmr-font-family);
  color: var(--atmr-text-primary);
}
.gantt-tip__warn {
  color: var(--atmr-map-late);
  font-weight: 600;
}

@media (max-width: 1440px) {
  .gantt__legend {
    gap: 2px var(--atmr-spacing-2x);
  }
  .map-tools__label {
    display: none;
  }
}
```

## File: frontend/index.html
```html
<!doctype html>
<html lang="ru">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />
    <title>Ростелеком Диспетчер</title>
    <link rel="icon" type="image/png" href="/favicon.png" />
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link
      href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;500;600;700&display=swap"
      rel="stylesheet"
    />
  </head>
  <body class="Theme_root_rtk_purple_light">
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

## File: frontend/package.json
```json
{
  "name": "rostelecom-dispatch-ui",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc --noEmit && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "dockview": "8.8.3",
    "dockview-core": "8.8.3",
    "dockview-react": "8.8.3",
    "leaflet": "^1.9.4",
    "react": "18.3.1",
    "react-dom": "18.3.1"
  },
  "devDependencies": {
    "@types/leaflet": "^1.9.22",
    "@types/react": "18.3.12",
    "@types/react-dom": "18.3.1",
    "@vitejs/plugin-react": "4.3.4",
    "typescript": "5.6.3",
    "vite": "5.4.11"
  }
}
```

## File: frontend/tsconfig.json
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "isolatedModules": true,
    "resolveJsonModule": true,
    "skipLibCheck": true,
    "noEmit": true,
    "types": ["vite/client"]
  },
  "include": ["src", "vite.config.ts"]
}
```

## File: frontend/vite.config.ts
```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backend = 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/ui': { target: backend, changeOrigin: true },
    },
  },
});
```
