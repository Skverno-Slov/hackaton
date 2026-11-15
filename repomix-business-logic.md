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
- Only files matching these patterns are included: backend/app/**/*.py, docs/formalization.md, docs/planner-notes.md, docs/mobile-api.md, CLAUDE.md, data/api_spec.yaml
- Files matching these patterns are excluded: backend/tests/**, **/__pycache__/**, **/.venv/**, **/venv/**, data/synth/**, docs/repomix-*.md, **/node_modules/**
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)

# Directory Structure
````
backend/
  app/
    api/
      __init__.py
      routes.py
    planner/
      __init__.py
      assemble.py
      base.py
      context.py
      decoder.py
      events.py
      joint.py
      pgp.py
      pyvrp_planner.py
      stub.py
      validator.py
      vrp_model.py
    __init__.py
    employee_api.py
    errors.py
    history_api.py
    journal.py
    main.py
    models.py
    orders_ui.py
    scenarios.py
    service.py
    staff_directory.py
    state.py
    store.py
    ui_api.py
data/
  api_spec.yaml
docs/
  formalization.md
  mobile-api.md
  planner-notes.md
CLAUDE.md
````

# Files

## File: backend/app/api/__init__.py
````python
"""HTTP-слой: маршруты /api/v1."""

from .routes import router

__all__ = ["router"]
````

## File: backend/app/api/routes.py
````python
"""HTTP-маршруты /api/v1 по спецификации. Тела читаются сырыми и разбираются в сервисе,
чтобы любая ошибка JSON или схемы превращалась в ErrorResponse 400."""

from __future__ import annotations

import os

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from starlette.concurrency import run_in_threadpool

from ..models import Health

API_VERSION = "0.1.0"

router = APIRouter(prefix="/api/v1")


def _service(request: Request):
    return request.app.state.service


def _json(model: BaseModel, status: int = 200) -> JSONResponse:
    return JSONResponse(status_code=status, content=model.model_dump(mode="json", by_alias=True))


@router.get("/health", tags=["service"])
def health() -> JSONResponse:
    return _json(
        Health(
            status="ok",
            team=os.environ.get("TEAM_NAME", "Команда"),
            version=API_VERSION,
        )
    )


@router.put("/reference", tags=["reference"])
async def put_reference(request: Request) -> JSONResponse:
    raw = await request.body()
    result = await run_in_threadpool(_service(request).put_reference, raw)
    return _json(result)


@router.put("/reference/travel-matrix", tags=["reference"])
async def put_travel_matrix(request: Request) -> JSONResponse:
    raw = await request.body()
    result = await run_in_threadpool(_service(request).put_travel_matrix, raw)
    return _json(result)


@router.put("/days/{date}", tags=["day"])
async def put_day(date: str, request: Request) -> JSONResponse:
    raw = await request.body()
    result = await run_in_threadpool(_service(request).put_day, date, raw)
    return _json(result)


@router.post("/days/{date}/plan", tags=["plan"])
async def build_plan(date: str, request: Request) -> JSONResponse:
    raw = await request.body()
    plan = await run_in_threadpool(_service(request).build_plan, date, raw or None)
    return _json(plan)


@router.get("/days/{date}/plan", tags=["plan"])
async def get_plan(date: str, request: Request) -> JSONResponse:
    plan = await run_in_threadpool(_service(request).get_plan, date)
    return _json(plan)


@router.post("/days/{date}/events", tags=["events"])
async def post_event(date: str, request: Request) -> JSONResponse:
    raw = await request.body()
    result = await run_in_threadpool(_service(request).post_event, date, raw)
    return _json(result)
````

## File: backend/app/planner/__init__.py
````python
"""Планировщики: PyVrpPlanner (по умолчанию) и заглушка StubPlanner.

Выбор — переменная окружения PLANNER=pyvrp|stub.
"""

from __future__ import annotations

import os

from .base import Planner, PlanUpdate
from .stub import StubPlanner


def make_planner(name: str | None = None) -> Planner:
    name = (name or os.environ.get("PLANNER", "pyvrp")).strip().lower()
    if name == "stub":
        return StubPlanner()
    if name == "pyvrp":
        from .pyvrp_planner import PyVrpPlanner

        return PyVrpPlanner()
    raise ValueError(f"Неизвестный планировщик PLANNER={name!r}, ожидается pyvrp или stub")


__all__ = ["Planner", "PlanUpdate", "StubPlanner", "make_planner"]
````

## File: backend/app/planner/assemble.py
````python
"""Сборка модели Plan из расписаний декодера; причины unassigned; предупреждения."""

from __future__ import annotations

import datetime as dt
import statistics

from ..models import (
    Alert,
    AlertAction,
    AlertStatus,
    AlertType,
    Equipment,
    PgpAction,
    PgpGroup,
    Plan,
    PlanSummary,
    ReasonCode,
    Route,
    RouteStatus,
    RouteTotals,
    Stop,
    StopStatus,
    StopType,
    TimeWindow,
    UnassignedAction,
    UnassignedOrder,
)
from .context import TZ, DayContext, OrderCtx
from .decoder import VISIT, RouteSchedule, best_insertion, decode
from .joint import is_joint, main_of, partner_id, unassigned_text
from .pgp import PgpCluster


def _eq(inv: tuple[int, int, int]) -> Equipment:
    return Equipment(ONT=inv[0], ROUTER=inv[1], STB=inv[2])


def _fmt(ctx: DayContext, sec: int) -> str:
    return ctx.to_dt(sec).strftime("%H:%M")


def explanation(ctx: DayContext, sched: RouteSchedule, stop_idx: int) -> str:
    stop = sched.stops[stop_idx]
    o = stop.order
    assert o is not None
    k = sched.staff
    parts = [
        f"{k.staff_id} ({'/'.join(k.model.profiles)}, {o.model.technology}"
        + (", допуск к высоте" if o.model.requires_height else "")
        + ")",
        f"в пути {round(stop.travel / 60)} мин",
        f"запас до крайнего начала {max(0, (o.b - stop.st) // 60)} мин",
        f"допустимых исполнителей на смене: {len(o.eligible)}",
    ]
    if stop_idx >= 2 and sched.stops[stop_idx - 1].kind == "RESTOCK":
        parts.append("АО пополнено перед визитом")
    if ctx.joint_staff:
        parts.extend(joint_note(ctx.joint_staff, o.order_id))
    return "; ".join(parts)


def joint_note(pairs: dict[str, tuple[str, str]], order_id: str) -> list[str]:
    """Пояснение к визиту совместного наряда (основной или напарник)."""
    main = main_of(order_id)
    if main is not None and main in pairs:
        return [f"совместный наряд: напарник по {main}, основной исполнитель {pairs[main][0]}, без списания АО"]
    if order_id in pairs:
        return [f"совместный наряд: напарник {pairs[order_id][1]} ({partner_id(order_id)}) с тем же началом"]
    return []


def route_model(ctx: DayContext, sched: RouteSchedule) -> Route:
    k = sched.staff
    stops: list[Stop] = []
    prev_point: int | None = None
    for i, s in enumerate(sched.stops):
        dist = 0 if prev_point is None else ctx.dist[prev_point][s.point]
        if s.kind == "BREAK":
            dist = 0
        stops.append(
            Stop(
                seq=i + 1,
                type=StopType(s.kind),
                location_id=ctx.point_ids[s.point],
                order_id=s.order.order_id if s.order is not None else None,
                arrival=ctx.to_dt(s.arr),
                start=ctx.to_dt(s.st),
                end=ctx.to_dt(s.en),
                travel_sec=s.travel,
                distance_m=dist,
                status=StopStatus.PLANNED,
                equipment_after=_eq(s.inv),
                explanation=explanation(ctx, sched, i) if s.kind == VISIT else None,
            )
        )
        prev_point = s.point
    totals = RouteTotals(
        visits=len(sched.orders),
        travel_sec=sched.travel if sched.orders else 0,
        distance_m=sum(st.distance_m or 0 for st in stops),
        work_min=sched.work // 60,
        utilization=round(sched.utilization, 4),
    )
    return Route(
        staff_id=k.staff_id,
        service_center_id=k.sc.sc_id,
        status=RouteStatus.ACTIVE,
        stops=stops,
        totals=totals,
    )


# ---------------------------------------------------------------- unassigned

def next_working_day(day: dt.date) -> dt.date:
    nxt = day + dt.timedelta(days=1)
    while nxt.weekday() >= 5:
        nxt += dt.timedelta(days=1)
    return nxt


def proposed_slot(ctx: DayContext, o: OrderCtx) -> TimeWindow | None:
    """Те же часы следующего рабочего дня, если начало + 10 мин + норматив ≤ deadline."""
    slot = o.model.slot
    shift = dt.datetime.combine(next_working_day(ctx.date), dt.time(0), tzinfo=TZ) - ctx.midnight
    start, end = slot.start + shift, slot.end + shift
    if start + dt.timedelta(minutes=10) + dt.timedelta(seconds=o.p) > o.model.deadline:
        return None
    return TimeWindow(start=start.astimezone(TZ), end=end.astimezone(TZ))


def classify_unassigned(
    ctx: DayContext,
    o: OrderCtx,
    schedules: dict[int, RouteSchedule],
    budget_exhausted: bool = False,
) -> UnassignedOrder:
    """Каскад причин docs/formalization.md, раздел 7."""
    model = o.model
    since = ctx.to_dt(ctx.now)
    if not o.eligible:
        need = f"профилем {'/'.join(o.work_type.profiles)}, технологией {model.technology}"
        if model.requires_height:
            need += " и допуском к работам на высоте"
        return UnassignedOrder(
            order_id=o.order_id,
            reason_code=ReasonCode.NO_QUALIFIED_STAFF,
            reason_text=f"На смене нет сотрудника с {need}",
            suggested_action=UnassignedAction.REASSIGN,
            proposed_slot=None,
            since=since,
        )

    if ctx.joint and is_joint(o) and len(o.eligible) < 2:
        # совместный наряд (JOINT_ORDERS=on), второго допустимого нет — существующий код причины
        return UnassignedOrder(
            order_id=o.order_id, reason_code=ReasonCode.NO_QUALIFIED_STAFF,
            reason_text=unassigned_text(o, len(o.eligible))[1],
            suggested_action=UnassignedAction.REASSIGN, proposed_slot=None, since=since,
        )

    staff = [ctx.staff[i] for i in o.eligible]
    alone = any(decode(ctx, k, [o]) is not None for k in staff)
    alone_no_eq = alone or any(decode(ctx, k, [o], ignore_eq=True) is not None for k in staff)
    n = len(staff)
    window = f"{_fmt(ctx, o.a)}–{_fmt(ctx, o.b)}"
    if not alone_no_eq:
        code = ReasonCode.SLOT_UNREACHABLE
        text = f"Ни один из {n} допустимых сотрудников не успевает начать визит в окне {window} в пределах смены"
    elif not alone:
        code = ReasonCode.NO_EQUIPMENT
        text = (
            f"Нужно АО ONT {o.dem[0]}, ROUTER {o.dem[1]}, STB {o.dem[2]}: заезд в СЦ на пополнение "
            f"не помещается в маршрут ни одного из {n} допустимых сотрудников"
        )
    else:
        free_staff = [k for k in staff if not schedules.get(k.idx) or not schedules[k.idx].orders]
        if ctx.joint and is_joint(o) and not budget_exhausted:
            # совместный наряд: одиночного свободного сотрудника мало, пара не нашлась
            code = ReasonCode.NO_SHIFT_TIME
            text = ctx.joint_fail.get(o.order_id) or unassigned_text(o, n)[1]
        elif budget_exhausted or any(decode(ctx, k, [o]) is not None for k in free_staff):
            code = ReasonCode.OTHER
            text = "Исчерпан бюджет расчёта до вставки наряда"
        elif any(
            best_insertion(ctx, schedules[k.idx], o, ignore_eq=True) is not None
            for k in staff
            if k.idx in schedules
        ):
            code = ReasonCode.NO_EQUIPMENT
            text = (
                f"Без учёта АО вставка возможна, но для АО ONT {o.dem[0]}, ROUTER {o.dem[1]}, STB {o.dem[2]} "
                f"заезд в СЦ не помещается в маршруты {n} допустимых сотрудников"
            )
        else:
            code = ReasonCode.NO_SHIFT_TIME
            text = (
                f"У {n} допустимых сотрудников не хватает времени смены: визит в окне {window} "
                f"не встаёт без срыва уже назначенных визитов"
            )
    if code == ReasonCode.OTHER:
        return UnassignedOrder(
            order_id=o.order_id, reason_code=code, reason_text=text,
            suggested_action=UnassignedAction.ESCALATE, proposed_slot=None, since=since,
        )
    slot = proposed_slot(ctx, o)
    return UnassignedOrder(
        order_id=o.order_id,
        reason_code=code,
        reason_text=text,
        suggested_action=UnassignedAction.RESCHEDULE if slot is not None else UnassignedAction.ESCALATE,
        proposed_slot=slot,
        since=since,
    )


# ---------------------------------------------------------------- alerts / pgp

class AlertIds:
    def __init__(self, day: dt.date, start: int = 0) -> None:
        self.day = day
        self.n = start

    def next(self) -> str:
        self.n += 1
        return f"ALR-{self.day:%Y%m%d}-{self.n:04d}"


def pgp_groups(ctx_date: dt.date, clusters: list[PgpCluster], now: dt.datetime, node_names: dict[str, str]) -> list[PgpGroup]:
    groups = []
    for i, c in enumerate(clusters, start=1):
        name = node_names.get(c.access_node_id, c.access_node_id)
        groups.append(
            PgpGroup(
                group_id=f"PGP-{ctx_date:%Y%m%d}-{i:02d}",
                access_node_id=c.access_node_id,
                order_ids=[o.order_id for o in c.orders],
                detected_at=now,
                action=PgpAction.TRANSFER_TO_STATION,
                comment=f"{len(c.orders)} обращения с {name} за {c.span_min} мин",
            )
        )
    return groups


def pgp_alert(ids: AlertIds, group: PgpGroup, now: dt.datetime) -> Alert:
    return Alert(
        alert_id=ids.next(),
        created_at=now,
        type=AlertType.PGP_DETECTED,
        status=AlertStatus.ACTIVE,
        group_id=group.group_id,
        suggested_action=AlertAction.TRANSFER_TO_STATION,
        applied=True,
        message=f"ПГП на {group.access_node_id}: {len(group.order_ids)} наряда переданы в станционное подразделение",
    )


def unassigned_alert(ids: AlertIds, u: UnassignedOrder, now: dt.datetime) -> Alert:
    action = {
        UnassignedAction.RESCHEDULE: AlertAction.RESCHEDULE,
        UnassignedAction.REASSIGN: AlertAction.REASSIGN,
    }.get(u.suggested_action, AlertAction.NONE)
    return Alert(
        alert_id=ids.next(),
        created_at=now,
        type=AlertType.UNASSIGNED,
        status=AlertStatus.ACTIVE,
        order_id=u.order_id,
        suggested_action=action,
        proposed_slot=u.proposed_slot if action == AlertAction.RESCHEDULE else None,
        applied=False,
        message=f"{u.order_id} не распределён ({u.reason_code.value}): {u.reason_text}",
    )


def summary(plan: Plan, orders_total: int) -> PlanSummary:
    utils = [r.totals.utilization for r in plan.routes if r.totals and r.totals.visits > 0]
    cv = 0.0
    if len(utils) >= 2 and statistics.fmean(utils) > 0:
        cv = statistics.pstdev(utils) / statistics.fmean(utils)
    return PlanSummary(
        orders_total=orders_total,
        # визит напарника совместного наряда (ORD-…-B) не считается: пара — один размещённый наряд
        visits_planned=sum(
            1 for r in plan.routes for s in r.stops if s.type == StopType.VISIT and main_of(s.order_id) is None
        ),
        unassigned=len(plan.unassigned),
        pgp_orders=sum(len(g.order_ids) for g in plan.pgp_groups),
        cancelled=len(plan.cancelled),
        travel_sec_total=sum((r.totals.travel_sec if r.totals else 0) for r in plan.routes),
        utilization_cv=round(cv, 4),
    )
````

## File: backend/app/planner/base.py
````python
"""Интерфейс планировщика.

Реальный планировщик подключается через реализацию Protocol `Planner`.
Сервис сам проставляет `plan_version`, `as_of` и `based_on_event_id`;
планировщик отвечает за содержимое маршрутов, нераспределённых, отменённых,
ПГП-групп и предупреждений.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Protocol

from ..models import Alert, Event, Plan
from ..state import DayState, MatrixState, ReferenceState


@dataclass
class PlanUpdate:
    plan: Plan
    changed_staff_ids: list[str] = field(default_factory=list)
    new_alerts: list[Alert] = field(default_factory=list)
    message: str | None = None  # EventResult.message (справочно)
    stats: dict = field(default_factory=dict)  # диагностика планировщика, в API не выходит


class Planner(Protocol):
    def build_plan(
        self,
        ref: ReferenceState,
        matrix: MatrixState,
        day: DayState,
        time_limit_sec: int | None = None,
    ) -> Plan:
        """Строит первоначальный план на `day.now`. `day` не меняется.

        `time_limit_sec` — лимит из PlanRequest; планировщик укладывается в него с запасом.
        """
        ...

    def apply_event(
        self,
        ref: ReferenceState,
        matrix: MatrixState,
        day: DayState,
        event: Event,
    ) -> PlanUpdate:
        """Перепланирует по событию.

        `day` — клон состояния: планировщик может его менять (наряды, отмены,
        время появления). Если метод поднимает ApiError, состояние не сохраняется.
        """
        ...
````

## File: backend/app/planner/context.py
````python
"""Контекст дня для планировщика: целочисленное время, компактная матрица, допуски.

Время — целые секунды от 00:00 местного дня (UTC+5, Екатеринбург), как в
docs/formalization.md. Матрица сжимается до точек дня (СЦ + дома нарядов) и
хранится списками Python: декодер обращается к ней миллионы раз.
"""

from __future__ import annotations

import datetime as dt
import math
import os
from dataclasses import dataclass, field

import numpy as np

from ..models import Order, ServiceCenter, Staff, WorkType
from ..state import DayState, MatrixState, ReferenceState

TZ = dt.timezone(dt.timedelta(hours=5))
EQUIPMENT_KINDS = ("ONT", "ROUTER", "STB")


def hhmm_to_sec(value: str) -> int:
    hours, minutes = value.split(":")
    return int(hours) * 3600 + int(minutes) * 60


def eq_tuple(eq) -> tuple[int, int, int]:
    return (eq.ONT, eq.ROUTER, eq.STB)


@dataclass
class ScCtx:
    sc_id: str
    point: int  # индекс в компактной матрице
    open: int
    close: int
    rho: int  # restock_duration, с
    model: ServiceCenter


@dataclass
class StaffCtx:
    staff_id: str
    idx: int
    sc: ScCtx
    start: int  # S_k
    end: int  # F_k
    beta: int  # длительность перерыва, с
    bf: int  # окно начала перерыва
    bt: int
    q0: tuple[int, int, int]
    cap: tuple[int, int, int]
    model: Staff

    @property
    def net_shift(self) -> int:
        return max(1, self.end - self.start - self.beta)


@dataclass
class OrderCtx:
    order_id: str
    idx: int
    point: int
    a: int  # slot.start
    b: int  # latest_start
    p: int  # норматив, с
    deadline: int
    reg: int  # registered_at
    dem: tuple[int, int, int]
    weight: int  # 2 для повторных
    work_type: WorkType
    model: Order
    eligible: list[int] = field(default_factory=list)  # индексы StaffCtx (H1)


def qualifies(staff: Staff, order: Order, wt: WorkType) -> bool:
    """H1: профиль, технология, допуск к высоте."""
    if not set(staff.profiles) & set(wt.profiles):
        return False
    if order.technology not in staff.technologies:
        return False
    if order.requires_height and not staff.height_permit:
        return False
    return True


def traffic_enabled() -> bool:
    """Переключатель TRAFFIC_FACTORS=on|off (по умолчанию off): учёт загруженности дорог.

    Читается при создании контекста (не при импорте), поэтому тесты и сценарии могут
    передать режим явно через DayContext(traffic=...)."""
    return os.environ.get("TRAFFIC_FACTORS", "off").strip().lower() in ("on", "1", "true", "yes")


def hour_factors(matrix: MatrixState) -> list[float]:
    """24 коэффициента по часу отправления, не меньше 1 (часы без записи — 1)."""
    out = [1.0] * 24
    for hf in matrix.matrix.hourly_factors:
        if 0 <= hf.hour <= 23:
            out[hf.hour] = max(1.0, float(hf.factor))
    return out


def matrix_arrays(matrix: MatrixState) -> tuple[np.ndarray, np.ndarray]:
    """numpy-копии матриц (кэшируются на объекте MatrixState)."""
    cached = getattr(matrix, "_np_cache", None)
    if cached is not None and cached[0] is matrix.matrix:
        return cached[1], cached[2]
    dur = np.asarray(matrix.matrix.durations_sec, dtype=np.int64)
    dist = np.asarray(matrix.matrix.distances_m, dtype=np.int64)
    matrix._np_cache = (matrix.matrix, dur, dist)  # type: ignore[attr-defined]
    return dur, dist


class DayContext:
    """Все данные дня в форме, удобной для декодера и солвера."""

    def __init__(
        self,
        ref: ReferenceState,
        matrix: MatrixState,
        day: DayState,
        orders: list[Order],
        now: dt.datetime,
        traffic: bool | None = None,
        joint: bool | None = None,
    ) -> None:
        self.ref = ref
        # Совместные наряды (JOINT_ORDERS, planner/joint.py): при выключенном режиме код идёт
        # по прежней ветке. joint_fail — основные наряды без пары (причина для unassigned),
        # joint_staff — main_id -> (основной сотрудник, напарник) для пояснений.
        if joint is None:
            from .joint import joint_enabled

            joint = joint_enabled()
        self.joint = bool(joint)
        self.joint_fail: dict[str, str] = {}
        self.joint_staff: dict[str, tuple[str, str]] = {}
        # Пробки по часам: travel = ceil(τ · max(1, f[час отправления])) ≥ τ, поэтому H6 по
        # базовой матрице выполняется всегда. Без коэффициентов в матрице — no-op.
        self.hour_factor = hour_factors(matrix)
        if traffic is None:
            traffic = traffic_enabled()
        self.traffic = bool(traffic) and any(f > 1.0 for f in self.hour_factor)
        self.day = day
        self.date = day.date
        self.midnight = dt.datetime.combine(day.date, dt.time(0), tzinfo=TZ)
        self.now = self.to_sec(now)

        # ---- точки дня: сначала все СЦ, затем дома нарядов
        point_ids: list[str] = list(ref.service_centers)
        point_of: dict[str, int] = {p: i for i, p in enumerate(point_ids)}
        for o in orders:
            if o.building_id not in point_of:
                point_of[o.building_id] = len(point_ids)
                point_ids.append(o.building_id)
        self.point_ids = point_ids
        self.point_of = point_of
        full_dur, full_dist = matrix_arrays(matrix)
        sel = np.array([matrix.index[p] for p in point_ids], dtype=np.int64)
        self.dur_np = full_dur[np.ix_(sel, sel)]
        self.dist_np = full_dist[np.ix_(sel, sel)]
        self.tau: list[list[int]] = self.dur_np.tolist()
        self.dist: list[list[int]] = self.dist_np.tolist()
        self.tau_max = int(full_dur.max()) if full_dur.size else 0

        # ---- СЦ
        self.scs: list[ScCtx] = []
        self.sc_by_id: dict[str, ScCtx] = {}
        for sc in ref.service_centers.values():
            ctx = ScCtx(
                sc_id=sc.service_center_id,
                point=point_of[sc.service_center_id],
                open=hhmm_to_sec(sc.opening_hours.open),
                close=hhmm_to_sec(sc.opening_hours.close),
                rho=sc.restock_duration_min * 60,
                model=sc,
            )
            self.scs.append(ctx)
            self.sc_by_id[ctx.sc_id] = ctx

        # ---- сотрудники на смене
        self.staff: list[StaffCtx] = []
        for s in ref.staff.values():
            if s.staff_id in day.absent_staff_ids:
                continue
            self.staff.append(
                StaffCtx(
                    staff_id=s.staff_id,
                    idx=len(self.staff),
                    sc=self.sc_by_id[s.service_center_id],
                    start=hhmm_to_sec(s.shift.start),
                    end=hhmm_to_sec(s.shift.end),
                    beta=s.shift.break_duration_min * 60,
                    bf=hhmm_to_sec(s.shift.break_window.from_),
                    bt=hhmm_to_sec(s.shift.break_window.to),
                    q0=eq_tuple(s.equipment_start),
                    cap=eq_tuple(s.equipment_capacity),
                    model=s,
                )
            )
        self.staff_by_id = {s.staff_id: s for s in self.staff}

        # ---- наряды
        self.orders: list[OrderCtx] = []
        for o in orders:
            wt = ref.work_types[o.work_type_code]
            octx = OrderCtx(
                order_id=o.order_id,
                idx=len(self.orders),
                point=point_of[o.building_id],
                a=self.to_sec(o.slot.start),
                b=self.to_sec(o.latest_start),
                p=wt.duration_min * 60,
                deadline=self.to_sec(o.deadline),
                reg=self.to_sec(o.registered_at),
                dem=eq_tuple(o.required_equipment),
                weight=2 if o.is_repeat else 1,
                work_type=wt,
                model=o,
            )
            octx.eligible = [k.idx for k in self.staff if qualifies(k.model, o, wt)]
            self.orders.append(octx)
        self.order_by_id = {o.order_id: o for o in self.orders}

    # ------------------------------------------------------------ travel
    def tt(self, i: int, j: int, t: int) -> int:
        """Время в пути i→j при отправлении в момент t (с от полуночи, местное время)."""
        base = self.tau[i][j]
        if not self.traffic or base == 0:
            return base
        f = self.hour_factor[(int(t) // 3600) % 24]
        if f <= 1.0:
            return base
        return max(base, math.ceil(base * f - 1e-9))

    # ------------------------------------------------------------ time
    def to_sec(self, moment: dt.datetime) -> int:
        return int(round((moment - self.midnight).total_seconds()))

    def to_dt(self, seconds: int) -> dt.datetime:
        return self.midnight + dt.timedelta(seconds=int(seconds))
````

## File: backend/app/planner/decoder.py
````python
"""Декодер маршрута: последовательность нарядов сотрудника -> точное расписание остановок.

Расписание строится «как можно раньше» по компактной матрице дня (H6), с проверкой
окон начала визита (H4), смены (H2), АО с пополнением в открытом СЦ (H7, H8) и ровно
одного перерыва внутри break_window (H3). Перерыв ставится на месте предыдущей
остановки, время в пути до него 0.

Пополнение (RESTOCK) вставляется непосредственно перед визитом, на котором остаток
стал бы отрицательным, в СЦ с минимальным крюком среди открытых на всём интервале
пополнения. Позиция перерыва перебирается от самой поздней допустимой к ранней.

Отступления от «как можно раньше», допустимые по H6 (там неравенство):
DEPOT_START ставится к выезду на первый визит, DEPOT_END.arrival — конец смены.

Пробки (TRAFFIC_FACTORS=on, ctx.traffic): время каждого переезда берётся ctx.tt(i, j, t)
по часу отправления t, не меньше базового τ — H6 по базовой матрице сохраняется.
Грубые отсечки вставки ниже идут по базовой τ: это нижняя граница, отсечка остаётся верной.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field

from .context import DayContext, OrderCtx, ScCtx, StaffCtx

# docs/formalization.md, раздел 4: DEPOT_END.arrival = F_k, чтобы при событиях
# незамороженный DEPOT_END не «запирал» освободившегося сотрудника.
DEPOT_END_AT_SHIFT_END = True


def break_slack_sec() -> int:
    """Запас между началом перерыва и break_window.to (этап B, docs/planner-notes.md).

    Перерыв ставится сразу после визита; если этот визит затянется (VISIT_DELAYED), перерыв
    сдвинется. Поэтому сначала ищется самая поздняя позиция перерыва с началом не позже
    `to − запас`, и только если такой нет — самая поздняя допустимая.
    """
    try:
        return int(float(os.environ.get("PLANNER_BREAK_SLACK_MIN", "60")) * 60)
    except ValueError:
        return 3600

DEPOT_START = "DEPOT_START"
VISIT = "VISIT"
BREAK = "BREAK"
RESTOCK = "RESTOCK"
DEPOT_END = "DEPOT_END"


@dataclass
class SchedStop:
    kind: str
    point: int
    arr: int
    st: int
    en: int
    travel: int
    inv: tuple[int, int, int]
    order: OrderCtx | None = None
    sc: ScCtx | None = None


@dataclass
class RouteSchedule:
    staff: StaffCtx
    orders: list[OrderCtx]
    stops: list[SchedStop] = field(default_factory=list)
    travel: int = 0  # с, по матрице, включая пополнения и возврат

    @property
    def work(self) -> int:
        return sum(o.p for o in self.orders)

    @property
    def utilization(self) -> float:
        if not self.orders:
            return 0.0
        return (self.work + self.travel) / self.staff.net_shift


def _forward(
    ctx: DayContext,
    k: StaffCtx,
    seq: list[OrderCtx],
    brk: int | None,
    ignore_eq: bool,
    t0: int,
    bt_limit: int | None = None,
) -> RouteSchedule | None:
    """Один проход. brk — после какого визита перерыв (-1 — сразу после выезда, None — без перерыва).

    bt_limit — крайнее начало перерыва (по умолчанию break_window.to)."""
    bt = k.bt if bt_limit is None else bt_limit
    tau = ctx.tau
    traffic = ctx.traffic
    tt = ctx.tt
    loc = k.sc.point
    t = t0
    inv = k.q0
    travel = 0
    stops = [SchedStop(DEPOT_START, loc, t, t, t, 0, inv, sc=k.sc)]

    def take_break() -> bool:
        nonlocal t
        st = max(t, k.bf)
        if st > bt:
            return False
        en = st + k.beta
        stops.append(SchedStop(BREAK, loc, t, st, en, 0, inv))
        t = en
        return True

    if brk == -1 and not take_break():
        return None

    for j, o in enumerate(seq):
        d = o.dem
        if not ignore_eq and (inv[0] < d[0] or inv[1] < d[1] or inv[2] < d[2]):
            cap = k.cap
            if cap[0] < d[0] or cap[1] < d[1] or cap[2] < d[2]:
                return None
            best = None
            for sc in ctx.scs:
                to_sc = tt(loc, sc.point, t) if traffic else tau[loc][sc.point]
                arr = t + to_sc
                st = max(arr, sc.open)
                en = st + sc.rho
                if en > sc.close:
                    continue
                from_sc = tt(sc.point, o.point, en) if traffic else tau[sc.point][o.point]
                if max(en + from_sc, o.a) > o.b:
                    continue
                detour = to_sc + from_sc
                if best is None or detour < best[0]:
                    best = (detour, sc, arr, st, en, to_sc)
            if best is None:
                return None
            _, sc, arr, st, en, to_sc = best
            inv = cap
            stops.append(SchedStop(RESTOCK, sc.point, arr, st, en, to_sc, inv, sc=sc))
            travel += to_sc
            t = en
            loc = sc.point
        tr = tt(loc, o.point, t) if traffic else tau[loc][o.point]
        arr = t + tr
        st = arr if arr > o.a else o.a
        if st > o.b:
            return None
        en = st + o.p
        if not ignore_eq:
            inv = (inv[0] - d[0], inv[1] - d[1], inv[2] - d[2])
        stops.append(SchedStop(VISIT, o.point, arr, st, en, tr, inv, order=o))
        travel += tr
        t = en
        loc = o.point
        if brk == j and not take_break():
            return None

    tr = tt(loc, k.sc.point, t) if traffic else tau[loc][k.sc.point]
    arr = t + tr
    if arr > k.end:
        return None
    travel += tr
    stops.append(SchedStop(DEPOT_END, k.sc.point, arr, arr, arr, tr, inv, sc=k.sc))
    return RouteSchedule(staff=k, orders=list(seq), stops=stops, travel=travel)


def shift_departure(ctx: DayContext, s0: SchedStop, s1: SchedStop) -> int:
    """Сдвиг DEPOT_START к выезду на первую остановку s1; возвращает изменение пути.

    С пробками время в пути зависит от часа выезда: позднее отправление может попасть в
    другой час, поэтому выезд подбирается так, чтобы приезд был не позже s1.st (иначе
    DEPOT_START остаётся на месте)."""
    if not ctx.traffic:
        depart = max(s0.st, s1.st - s1.travel)
        s0.arr = s0.st = s0.en = depart
        s1.arr = depart + s1.travel
        return 0
    depart, tr = s0.st, s1.travel
    cand = s1.st - s1.travel
    for _ in range(3):
        if cand <= s0.st:
            break
        tr_c = ctx.tt(s0.point, s1.point, cand)
        if cand + tr_c <= s1.st:
            depart, tr = cand, tr_c
            break
        cand = s1.st - tr_c
    delta = tr - s1.travel
    s0.arr = s0.st = s0.en = depart
    s1.travel = tr
    s1.arr = depart + tr
    return delta


def _finalize(sched: RouteSchedule, ctx: DayContext) -> RouteSchedule:
    stops = sched.stops
    first = stops[1]
    if first.kind == VISIT:
        sched.travel += shift_departure(ctx, stops[0], first)
    if DEPOT_END_AT_SHIFT_END:
        last = stops[-1]
        last.arr = last.st = last.en = max(last.arr, sched.staff.end)
    return sched


def decode(
    ctx: DayContext,
    k: StaffCtx,
    seq: list[OrderCtx],
    ignore_eq: bool = False,
    t0: int | None = None,
) -> RouteSchedule | None:
    """Точное расписание для порядка визитов `seq` или None, если он недопустим."""
    if not seq:
        return RouteSchedule(staff=k, orders=[], stops=[], travel=0)
    if t0 is None:
        t0 = max(k.start, ctx.now)
    base = _forward(ctx, k, seq, None, ignore_eq, t0)
    if base is None:
        return None  # перерыв только задерживает: без него тоже недопустимо
    # концы остановок-кандидатов (DEPOT_START и визиты) в проходе без перерыва
    ends = [base.stops[0].en] + [s.en for s in base.stops if s.kind == VISIT]
    slack = break_slack_sec()
    limits = [k.bt - slack, k.bt] if slack > 0 and k.bt - slack >= k.bf else [k.bt]
    for limit in limits:
        for j in range(len(seq) - 1, -2, -1):
            if ends[j + 1] > limit:
                continue
            res = _forward(ctx, k, seq, j, ignore_eq, t0, bt_limit=limit)
            if res is not None:
                return _finalize(res, ctx)
    return None


# ---------------------------------------------------------------- вставка

def insertion_candidates(ctx: DayContext, sched: RouteSchedule, o: OrderCtx) -> list[tuple[int, int]]:
    """Позиции вставки `o` в маршрут, упорядоченные по приросту пути: [(delta, pos)].

    Грубый фильтр по времени: конец предыдущего визита + дорога не позже latest_start.
    """
    k = sched.staff
    tau = ctx.tau
    seq = sched.orders
    if not seq:
        start = max(k.start, ctx.now)
        if start + tau[k.sc.point][o.point] > o.b:
            return []
        return [(tau[k.sc.point][o.point] + tau[o.point][k.sc.point], 0)]
    visit_ends = [s.en for s in sched.stops if s.kind == VISIT]
    out = []
    for pos in range(len(seq) + 1):
        prev_pt = k.sc.point if pos == 0 else seq[pos - 1].point
        next_pt = k.sc.point if pos == len(seq) else seq[pos].point
        prev_end = max(k.start, ctx.now) if pos == 0 else visit_ends[pos - 1]
        if prev_end + tau[prev_pt][o.point] > o.b:
            break  # дальше концы только позже
        # следующий визит должен начаться не раньше нашего окна: грубая отсечка
        if pos < len(seq) and o.a + o.p + tau[o.point][next_pt] > seq[pos].b:
            continue
        delta = tau[prev_pt][o.point] + tau[o.point][next_pt] - tau[prev_pt][next_pt]
        out.append((delta, pos))
    out.sort()
    return out


def best_insertion(
    ctx: DayContext,
    sched: RouteSchedule,
    o: OrderCtx,
    max_tries: int = 6,
    ignore_eq: bool = False,
) -> tuple[int, RouteSchedule] | None:
    """Лучшая допустимая вставка по приросту пути (проверка точным декодером)."""
    best: tuple[int, RouteSchedule] | None = None
    tries = 0
    for _, pos in insertion_candidates(ctx, sched, o):
        seq = sched.orders[:pos] + [o] + sched.orders[pos:]
        res = decode(ctx, sched.staff, seq, ignore_eq=ignore_eq)
        tries += 1
        if res is not None:
            cost = res.travel - sched.travel
            if best is None or cost < best[0]:
                best = (cost, res)
        if tries >= max_tries and best is not None:
            break
        if tries >= 3 * max_tries:
            break
    return best
````

## File: backend/app/planner/events.py
````python
"""Перепланирование по событиям (этап B): docs/formalization.md, разделы 5–7.

Модель исполнения (data/api_spec.yaml, раздел 3): сотрудники выполняют текущую версию
плана в точности, отклонения приходят событиями. Поэтому маршрут, которого событие не
коснулось, переносится в новую версию без изменений, а пересчитываются только
«затронутые» маршруты.

Маршрут затронутого сотрудника делится на замороженный префикс (остановки со
`start < t`; по VISIT_DELAYED / ORDER_TRANSFERRED — ещё и визит из события с
`start ≤ t`) и хвост — последовательность нарядов, которую заново расписывает
декодер хвоста `decode_tail`:

* первая остановка хвоста, совпадающая с прежней (тот же тип/наряд и тот же
  предшественник), уезжает в `end` последней замороженной остановки; любая другая —
  не раньше `max(end, t)` (H10, правило нового предшественника);
* все остановки хвоста начинаются не раньше `t` (и не раньше `until` у выбывшего);
* перерыв (H3) ставится ровно один, если его нет в префиксе; позиция перебирается
  от поздней к ранней; у выбывшего перерыв не нужен, если остаток окна перерыва
  целиком попал в недоступность (docs/planner-notes.md, «Этап B: допущения»);
* RESTOCK — перед визитом, где остаток АО ушёл бы в минус (как в decoder.py);
* DEPOT_END — в конце смены (как в decoder.py).

Алгоритм на событие (внутренний дедлайн PLANNER_EVENT_DEADLINE_SEC = 7 с):
1. детерминированная правка по типу события, проверка противоречий (422);
2. прогноз «без вмешательства» по затронутым маршрутам (ослабленный проход
   декодера без верхних границ окон) — источник LATE_RISK;
3. ремонт затронутых маршрутов: перестановка внутри маршрута, иначе снятие наряда
   (сначала с меньшим весом, затем с запасом ≥ 30 мин до слота, затем более поздний);
4. вставка снятых, нового и (в освободившееся время) нераспределённых нарядов в
   хвосты допустимых сотрудников по минимальному приросту пути, для снятых — цепочка
   выталкивания длины 2 (выталкивается наряд с запасом ≥ 30 мин до слота);
5. предупреждения: LATE_RISK на каждый наряд, сменивший исполнителя или снятый из
   маршрута, и на каждый наряд с прогнозом опоздания; UNASSIGNED на новый
   нераспределённый наряд; закрытие предупреждений по нарядам, вернувшимся в маршрут;
6. самопроверка validate_plan + validate_transition; наряды с нарушением в хвосте
   снимаются в unassigned (не должно срабатывать).
"""

from __future__ import annotations

import datetime as dt
import math
import os
import time
from dataclasses import dataclass, field

from ..errors import ApiError
from ..models import (
    Alert,
    AlertAction,
    AlertStatus,
    AlertType,
    CancelledOrder,
    Equipment,
    Event,
    PgpAction,
    PgpGroup,
    Plan,
    ReasonCode,
    Route,
    RouteStatus,
    RouteTotals,
    Stop,
    StopStatus,
    StopType,
    UnassignedAction,
    UnassignedOrder,
)
from ..state import DayState, MatrixState, ReferenceState
from . import assemble
from .base import PlanUpdate
from .context import DayContext, OrderCtx, StaffCtx
from .decoder import BREAK, DEPOT_END, DEPOT_START, RESTOCK, VISIT, SchedStop, break_slack_sec, shift_departure
from .pgp import PGP_MIN_ORDERS, PGP_WINDOW
from . import joint as jnt
from .validator import validate_plan, validate_transition

TIMELY_SEC = 30 * 60  # LATE_RISK своевременен, если создан не позднее slot.start − 30 мин
EJECT_WINDOW_SEC = 3 * 3600


def event_deadline_sec() -> float:
    try:
        return float(os.environ.get("PLANNER_EVENT_DEADLINE_SEC", "7"))
    except ValueError:
        return 7.0


def _key(stop: Stop) -> tuple:
    """Идентичность остановки для сравнения версий (H10: «тот же предшественник»)."""
    if stop.type == StopType.VISIT:
        return (VISIT, stop.order_id)
    if stop.type == StopType.RESTOCK:
        return (RESTOCK, stop.location_id)
    return (stop.type.value,)


# ---------------------------------------------------------------- состояние сотрудника

@dataclass
class TailSched:
    stops: list[SchedStop]
    travel: int
    starts: list[int]  # начало визитов seq
    ends: list[int]


@dataclass
class Anchor:
    k: StaffCtx
    prev_route: Route | None
    prefix: list[Stop]
    loc: int  # точка последней замороженной остановки (или СЦ при пустом префиксе)
    en: int  # её конец, с
    inv: tuple[int, int, int]
    break_done: bool
    prefix_visits: int
    first_key: tuple | None  # прежняя первая остановка хвоста
    status: RouteStatus
    unav_from: int | None = None
    until: int | None = None  # конец недоступности, если он ещё впереди (сотрудник продолжает смену из СЦ)
    unav_until: int | None = None  # конец недоступности из события (None — до конца смены)
    blocked: bool = False  # недоступен до конца смены
    closed: bool = False  # DEPOT_END уже заморожен
    seq: list[OrderCtx] = field(default_factory=list)
    prev_seq_ids: list[str] = field(default_factory=list)
    prev_break_before: set[str] | None = None  # наряды хвоста до перерыва в прежнем плане
    sched: TailSched | None = None
    sched_ok: bool | None = None  # None — ещё не декодирован
    touched: bool = False

    @property
    def available(self) -> bool:
        return not (self.blocked or self.closed)

    def break_exempt(self) -> bool:
        """Допущение этапа B: перерыв не нужен, если остаток окна перерыва (после выбытия)
        целиком попал в недоступность."""
        if self.status != RouteStatus.UNAVAILABLE:
            return False
        return self.unav_until is None or self.unav_until > self.k.bt

    def needs_break(self, seq: list[OrderCtx]) -> bool:
        if self.break_done or self.break_exempt():
            return False
        return self.prefix_visits > 0 or bool(seq)

    def needs_depot_end(self, seq: list[OrderCtx], need_break: bool) -> bool:
        if self.blocked or self.closed:
            return False
        if self.status == RouteStatus.ACTIVE:
            return bool(self.prefix) or bool(seq) or need_break
        return bool(seq) or need_break


def _pass(
    ctx: DayContext,
    A: Anchor,
    seq: list[OrderCtx],
    brk: int | None,
    t: int,
    relaxed: bool = False,
    bt_limit: int | None = None,
) -> TailSched | None:
    """Один проход декодера хвоста; brk — после какого визита seq перерыв (-1 — сразу)."""
    tau = ctx.tau
    traffic = ctx.traffic
    k = A.k
    bt = k.bt if bt_limit is None else bt_limit
    until = A.until
    stops: list[SchedStop] = []
    starts: list[int] = []
    ends: list[int] = []
    travel = 0
    if A.prefix:
        loc, cur, inv = A.loc, A.en, A.inv
        first = True
    else:
        t0 = max(k.start, t, until or 0)
        loc, cur, inv = k.sc.point, t0, k.q0
        stops.append(SchedStop(DEPOT_START, loc, t0, t0, t0, 0, inv, sc=k.sc))
        first = False

    last_tr = 0  # время в пути последнего переезда из arrival (с пробками — по часу выезда)

    def leg(i: int, j: int, at: int) -> int:
        return ctx.tt(i, j, at) if traffic else tau[i][j]

    def arrival(key: tuple, dest: int) -> int:
        nonlocal last_tr
        if first:
            base = cur if key == A.first_key else max(cur, t)
            last_tr = leg(loc, dest, base)
            arr = base + last_tr
            if until is not None:
                arr = max(arr, until + leg(k.sc.point, dest, until))
            return arr
        last_tr = leg(loc, dest, cur)
        return cur + last_tr

    def take_break() -> bool:
        nonlocal cur, first
        arr = (cur if (BREAK,) == A.first_key else max(cur, t)) if first else cur
        first = False
        st = max(arr, k.bf, t, until or 0)
        if st > bt and not relaxed:
            return False
        en = st + k.beta
        stops.append(SchedStop(BREAK, loc, arr, st, en, 0, inv))
        cur = en
        return True

    if brk == -1 and not take_break():
        return None

    for j, o in enumerate(seq):
        d = o.dem
        if not relaxed and (inv[0] < d[0] or inv[1] < d[1] or inv[2] < d[2]):
            cap = k.cap
            if cap[0] < d[0] or cap[1] < d[1] or cap[2] < d[2]:
                return None
            best = None
            for sc in ctx.scs:
                arr = arrival((RESTOCK, sc.sc_id), sc.point)
                to_sc = last_tr
                st = max(arr, sc.open, t)
                en = st + sc.rho
                if en > sc.close:
                    continue
                from_sc = leg(sc.point, o.point, en)
                if max(en + from_sc, o.a) > o.b:
                    continue
                detour = to_sc + from_sc
                if best is None or detour < best[0]:
                    best = (detour, sc, arr, st, en, to_sc)
            if best is None:
                return None
            _, sc, arr, st, en, to_sc = best
            first = False
            inv = cap
            stops.append(SchedStop(RESTOCK, sc.point, arr, st, en, to_sc, inv, sc=sc))
            travel += to_sc
            cur, loc = en, sc.point
        arr = arrival((VISIT, o.order_id), o.point)
        tr = last_tr
        first = False
        st = max(arr, o.a, t)
        if st > o.b and not relaxed:
            return None
        en = st + o.p
        inv = (inv[0] - d[0], inv[1] - d[1], inv[2] - d[2])
        stops.append(SchedStop(VISIT, o.point, arr, st, en, tr, inv, order=o))
        starts.append(st)
        ends.append(en)
        travel += tr
        cur, loc = en, o.point
        if brk == j and not take_break():
            return None

    need_break = A.needs_break(seq)
    if A.needs_depot_end(seq, need_break):
        arr = arrival((DEPOT_END,), k.sc.point)
        tr = last_tr
        if arr > k.end and not relaxed:
            return None
        at = max(arr, k.end, t)
        stops.append(SchedStop(DEPOT_END, k.sc.point, at, at, at, tr, inv, sc=k.sc))
        travel += tr
    return TailSched(stops=stops, travel=travel, starts=starts, ends=ends)


def decode_tail(ctx: DayContext, A: Anchor, seq: list[OrderCtx], t: int) -> TailSched | None:
    """Точное расписание хвоста для порядка seq или None."""
    if not A.available:
        return TailSched([], 0, [], []) if not seq else None
    if not A.needs_break(seq):
        res = _pass(ctx, A, seq, None, t)
    else:
        base = _pass(ctx, A, seq, None, t)
        if base is None:
            return None
        start0 = max(A.en, t) if A.prefix else max(A.k.start, t, A.until or 0)
        ends = [start0] + base.ends
        res = None
        k = A.k
        slack = break_slack_sec()
        limits = [k.bt - slack, k.bt] if slack > 0 and k.bt - slack >= max(k.bf, t) else [k.bt]
        for limit in limits:
            for j in range(len(seq) - 1, -2, -1):
                if ends[j + 1] > limit:
                    continue
                res = _pass(ctx, A, seq, j, t, bt_limit=limit)
                if res is not None:
                    break
            if res is not None:
                break
    if res is None:
        return None
    if not A.prefix and len(res.stops) >= 2 and res.stops[1].kind == VISIT:
        # DEPOT_START — к выезду на первый визит (как decoder._finalize)
        res.travel += shift_departure(ctx, res.stops[0], res.stops[1])
    return res


def forecast(ctx: DayContext, A: Anchor, seq: list[OrderCtx], t: int) -> dict[str, int]:
    """Прогноз начала визитов «без вмешательства»: прежний порядок и позиция перерыва,
    без верхних границ окон и без пополнений."""
    if not A.available:
        return {}
    brk = None
    if A.needs_break(seq):
        before = A.prev_break_before
        if before is None:
            brk = -1
        else:
            brk = sum(1 for o in seq if o.order_id in before) - 1
    res = _pass(ctx, A, seq, brk, t, relaxed=True)
    if res is None:
        return {}
    return {o.order_id: st for o, st in zip(seq, res.starts)}


# ---------------------------------------------------------------- планировщик события

class EventPlanner:
    def __init__(self, ref: ReferenceState, matrix: MatrixState, day: DayState, event: Event,
                 traffic: bool | None = None, joint: bool | None = None) -> None:
        self.started = time.perf_counter()
        self.deadline = self.started + event_deadline_sec()
        self.ref, self.matrix, self.day, self.event = ref, matrix, day, event
        assert day.plan is not None
        self.prev: Plan = day.plan
        if event.type == "NEW_ORDER":
            day.orders[event.order.order_id] = event.order
            day.arrived_at[event.order.order_id] = event.timestamp
        self.ctx = DayContext(ref, matrix, day, list(day.orders.values()), event.timestamp, traffic=traffic,
                              joint=joint)
        self.t = self.ctx.to_sec(event.timestamp)
        # совместные наряды (planner/joint.py): main_id -> закреплённый основной
        self.pin: dict[str, OrderCtx] = {}
        self.joint_fail: dict[str, str] = {}
        self.joint_done: set[str] = set()
        self._pairs: dict[str, tuple[str, str]] = {}
        self._joint_init()
        self.ts = self.ctx.to_dt(self.t)
        self.message: list[str] = []
        self.stats: dict = {}

        # учёт: где наряды прежней версии
        self.prev_staff: dict[str, str] = {}
        for r in self.prev.routes:
            for s in r.stops:
                if s.type == StopType.VISIT and s.order_id:
                    self.prev_staff[s.order_id] = r.staff_id
        self.unassigned: dict[str, UnassignedOrder] = {u.order_id: u.model_copy() for u in self.prev.unassigned}
        self.groups: list[PgpGroup] = [g.model_copy(update={"order_ids": list(g.order_ids)}) for g in self.prev.pgp_groups]
        self.alerts: list[Alert] = [a.model_copy() for a in self.prev.alerts]
        self.ids = assemble.AlertIds(day.date, self._max_alert_no())
        self.n_prev_alerts = len(self.alerts)
        self.pool_new: list[OrderCtx] = []
        self.unplaced: dict[str, tuple[OrderCtx, str | None, bool]] = {}
        self.budget_exhausted: set[str] = set()
        self.ejected_unplaced: set[str] = set()

        self.anchors: dict[str, Anchor] = {}
        force: set[tuple[str, str]] = set()
        if event.type in ("VISIT_DELAYED", "ORDER_TRANSFERRED"):
            force.add((event.staff_id, event.order_id))
            # напарник начал визит одновременно с основным — он тоже начат
            pid = jnt.partner_id(event.order_id)
            for r in self.prev.routes:
                if any(s.type == StopType.VISIT and s.order_id == pid for s in r.stops):
                    force.add((r.staff_id, pid))
        prev_routes = {r.staff_id: r for r in self.prev.routes}
        for k in self.ctx.staff:
            self.anchors[k.staff_id] = self._anchor(k, prev_routes.get(k.staff_id), force)
        if self.pin:
            for A in self.anchors.values():
                A.seq = [self.pin.get(o.order_id, o) for o in A.seq]

        self.late: dict[str, int] = {}  # прогноз опоздания: order_id -> ŝ
        self.displaced: list[tuple[OrderCtx, str, bool]] = []  # (наряд, прежний сотрудник, выбыл ли)
        self.inserted_from_unassigned: set[str] = set()
        self.unav_staff: str | None = None

    # ------------------------------------------------------------ совместные наряды
    def _joint_init(self) -> None:
        """Пары из прежней версии: визит напарника ORD-…-B получает OrderCtx с окном [s, s],
        основной наряд в хвосте — закреплённую копию (planner/joint.py)."""
        ctx = self.ctx
        for r in self.prev.routes:
            for s in r.stops:
                m = jnt.main_of(s.order_id, self.day.orders) if s.type == StopType.VISIT else None
                if m is None or m not in ctx.order_by_id:
                    continue
                st = ctx.to_sec(s.start)
                main = ctx.order_by_id[m]
                ctx.order_by_id[s.order_id] = jnt.partner_ctx(main, st)
                self.pin[m] = jnt.pinned(main, st)
        # пары прежней версии сопровождаются и при выключенном режиме (новые не создаются)
        self.joint = ctx.joint or bool(self.pin)

    def _drop_partner(self, main_id: str) -> None:
        """Снять не начатый визит напарника (основной снят или отменён)."""
        pid = jnt.partner_id(main_id)
        hit = self.find_visit(pid)
        if hit is None:
            return
        A, frozen = hit
        if frozen:
            return
        A.seq = [o for o in A.seq if o.order_id != pid]
        self.touch(A)
        self.message.append(f"напарник {pid} снят у {A.k.staff_id}")

    def joint_place(self, main: OrderCtx, src: str | None, unav: bool) -> str | None:
        """Пара (основной, напарник, общее начало) по минимальному приросту пути; иначе unplaced."""
        m = main.order_id
        if m in self.day.cancelled:
            return None

        def try_insert(kidx: int, x: OrderCtx):
            A = self.anchors[self.ctx.staff[kidx].staff_id]
            r = self.insertion(A, x, max_tries=4)
            return None if r is None else (r[0], (A, r[1], r[2]))

        found = None
        if self.time_left() and self.ctx.joint:
            found = jnt.search(main, jnt.start_grid(main, self.t), main.eligible, try_insert, deadline=self.deadline)
        if found is None:
            n = sum(1 for i in main.eligible if self.anchors[self.ctx.staff[i].staff_id].available)
            self.joint_fail[m] = jnt.unassigned_text(main, n)[1]
            self.unplaced[m] = (main, src, unav)
            return None
        _, _, (A1, sch1, seq1), pm, _, (A2, sch2, seq2), pp = found
        A1.seq, A1.sched, A1.sched_ok, A1.touched = seq1, sch1, True, True
        A2.seq, A2.sched, A2.sched_ok, A2.touched = seq2, sch2, True, True
        self.pin[m] = pm
        self.ctx.order_by_id[pp.order_id] = pp
        self.message.append(f"совместный {m}: {A1.k.staff_id} + {A2.k.staff_id} в {self.ctx.to_dt(pm.a):%H:%M}")
        return A1.k.staff_id

    def joint_redo(self, o: OrderCtx, src: str | None, unav: bool) -> None:
        """Снят основной визит или визит напарника: пересобрать пару."""
        m = jnt.main_of(o.order_id) or o.order_id
        if m in self.joint_done or m not in self.ctx.order_by_id:
            return
        self.joint_done.add(m)
        main = self.ctx.order_by_id[m]
        if o.order_id != m:
            hit = self.find_visit(m)
            if hit is None or hit[1]:
                return  # основного нет в маршруте (отменён) или он уже начат
            A = hit[0]
            # другой напарник к тому же началу
            if self.time_left() and self.place(o, exclude=A.k.staff_id) is not None:
                return
            A.seq = [x for x in A.seq if x.order_id != m]
            self.touch(A)
            sched, removed = self.repair(A)
            A.sched, A.sched_ok = sched, True
            for x in removed:
                if self.place(x) is None:
                    self.unplaced[x.order_id] = (x, A.k.staff_id, False)
            src = A.k.staff_id
        else:
            self._drop_partner(m)
        self.joint_place(main, src, unav)

    def _joint_pairs_now(self) -> dict[str, tuple[str, str]]:
        main_staff: dict[str, str] = {}
        partner_staff: dict[str, str] = {}
        for A in self.anchors.values():
            ids = [s.order_id for s in A.prefix if s.type == StopType.VISIT and s.order_id]
            ids += [o.order_id for o in A.seq]
            for oid in ids:
                m = jnt.main_of(oid, self.day.orders)
                if m is not None:
                    partner_staff[m] = A.k.staff_id
                else:
                    main_staff[oid] = A.k.staff_id
        return {m: (main_staff[m], p) for m, p in partner_staff.items() if m in main_staff}

    # ------------------------------------------------------------ helpers
    def _max_alert_no(self) -> int:
        n = 0
        for a in self.prev.alerts:
            try:
                n = max(n, int(a.alert_id.rsplit("-", 1)[1]))
            except (IndexError, ValueError):
                pass
        return n

    def time_left(self) -> bool:
        return time.perf_counter() < self.deadline

    def _anchor(self, k: StaffCtx, route: Route | None, force: set[tuple[str, str]]) -> Anchor:
        ctx, t = self.ctx, self.t
        stops = route.stops if route is not None else []
        n_frozen = 0
        for i, s in enumerate(stops):
            if ctx.to_sec(s.start) < t:
                n_frozen = i + 1
        for i, s in enumerate(stops):
            if (s.type == StopType.VISIT and (k.staff_id, s.order_id) in force
                    and ctx.to_sec(s.start) <= t):
                n_frozen = max(n_frozen, i + 1)
        prefix = [s.model_copy() for s in stops[:n_frozen]]
        tail = stops[n_frozen:]
        inv = k.q0
        break_done = False
        visits = 0
        for s in prefix:
            if s.type == StopType.VISIT and s.order_id in self.day.orders:
                d = self.day.orders[s.order_id].required_equipment
                inv = (inv[0] - d.ONT, inv[1] - d.ROUTER, inv[2] - d.STB)
                visits += 1
            elif s.type == StopType.VISIT and jnt.main_of(s.order_id, self.day.orders) is not None:
                visits += 1  # визит напарника: АО не списывается, но перерыв нужен (H3)
            elif s.type == StopType.RESTOCK:
                inv = k.cap
            elif s.type == StopType.BREAK:
                break_done = True
        if prefix:
            loc = ctx.point_of[prefix[-1].location_id]
            en = ctx.to_sec(prefix[-1].end)
        else:
            loc, en = k.sc.point, k.start
        seq = [ctx.order_by_id[s.order_id] for s in tail if s.type == StopType.VISIT and s.order_id in ctx.order_by_id]
        before: set[str] | None = None
        if not break_done:
            acc: set[str] = set()
            for s in tail:
                if s.type == StopType.BREAK:
                    before = set(acc)
                    break
                if s.type == StopType.VISIT and s.order_id:
                    acc.add(s.order_id)
        A = Anchor(
            k=k,
            prev_route=route,
            prefix=prefix,
            loc=loc,
            en=en,
            inv=inv,
            break_done=break_done,
            prefix_visits=visits,
            first_key=_key(tail[0]) if (tail and prefix) else None,
            status=route.status if route is not None else RouteStatus.ACTIVE,
            seq=seq,
            prev_seq_ids=[o.order_id for o in seq],
            prev_break_before=before,
        )
        if route is not None and route.status == RouteStatus.UNAVAILABLE:
            A.unav_from = ctx.to_sec(route.unavailable_from) if route.unavailable_from else None
            if route.unavailable_until is None:
                A.blocked = True
            else:
                u = ctx.to_sec(route.unavailable_until)
                A.unav_until = u
                if u > t:
                    A.until = u
                if u >= k.end:
                    A.blocked = True
        if prefix and prefix[-1].type == StopType.DEPOT_END:
            A.closed = True
        return A

    def sched(self, A: Anchor) -> TailSched | None:
        if A.sched_ok is None:
            A.sched = decode_tail(self.ctx, A, A.seq, self.t)
            A.sched_ok = A.sched is not None
        return A.sched

    def touch(self, A: Anchor) -> None:
        A.touched = True
        A.sched_ok = None

    def find_visit(self, order_id: str) -> tuple[Anchor, bool] | None:
        """(сотрудник, в префиксе ли) для наряда в маршруте."""
        for A in self.anchors.values():
            if any(s.type == StopType.VISIT and s.order_id == order_id for s in A.prefix):
                return A, True
            if any(o.order_id == order_id for o in A.seq):
                return A, False
        return None

    def resolve_alerts(self, order_id: str | None = None, group_id: str | None = None) -> None:
        for a in self.alerts:
            if a.status != AlertStatus.ACTIVE:
                continue
            if (order_id and a.order_id == order_id) or (group_id and a.group_id == group_id):
                a.status = AlertStatus.RESOLVED
                a.resolved_at = self.ts

    def add_alert(self, alert: Alert) -> None:
        self.alerts.append(alert)

    # ------------------------------------------------------------ шаг 1: событие
    def apply(self) -> None:
        ev = self.event
        kind = ev.type
        if kind == "NEW_ORDER":
            o = self.ctx.order_by_id[ev.order.order_id]
            if not self.pgp_new_order(o):
                self.pool_new = [o]
            return
        if kind == "ORDER_CANCELLED":
            self.cancel(ev.order_id)
            return
        if kind in ("VISIT_DELAYED", "ORDER_TRANSFERRED"):
            self.visit_end(ev)
            return
        if kind == "STAFF_UNAVAILABLE":
            self.unavailable(ev)
            return
        # TIME_TICK: план исполняется в точности, прогнозы не меняются

    def cancel(self, oid: str) -> None:
        cancelled = CancelledOrder(order_id=oid, cancelled_at=self.ts, event_id=self.event.event_id)
        hit = self.find_visit(oid)
        if hit is not None:
            A, frozen = hit
            if frozen:
                self.message.append(f"Визит по {oid} уже начат — остаётся в маршруте как выполненный")
                return
            A.seq = [o for o in A.seq if o.order_id != oid]
            self.touch(A)
            self.day.cancelled[oid] = cancelled
            self.message.append(f"{oid} отменён, маршрут {A.k.staff_id} пересчитан")
            if self.joint:
                self._drop_partner(oid)
            return
        self.day.cancelled[oid] = cancelled
        if oid in self.unassigned:
            del self.unassigned[oid]
            self.resolve_alerts(order_id=oid)
        for g in list(self.groups):
            if oid in g.order_ids:
                g.order_ids = [x for x in g.order_ids if x != oid]
                if len(g.order_ids) >= 2:
                    g.comment = self._pgp_comment(g)
                else:
                    # группа из одного наряда невозможна (minItems 2): остаток — снова в распределение
                    self.groups.remove(g)
                    self.resolve_alerts(group_id=g.group_id)
                    for x in g.order_ids:
                        self.pool_new = self.pool_new + [self.ctx.order_by_id[x]]
        self.message.append(f"{oid} отменён")

    def visit_end(self, ev) -> None:
        A = self.anchors.get(ev.staff_id)
        idx = None
        if A is not None:
            for i, s in enumerate(A.prefix):
                if s.type == StopType.VISIT and s.order_id == ev.order_id:
                    idx = i
        if idx is None:
            raise ApiError(422, "ORDER_NOT_IN_ROUTE",
                           f"Визит по {ev.order_id} не начат у сотрудника {ev.staff_id}",
                           [{"field": "order_id", "message": "визит не начат у этого сотрудника"}])
        stop = A.prefix[idx]
        new_end = ev.expected_end if ev.type == "VISIT_DELAYED" else ev.actual_end
        new_end_sec = self.ctx.to_sec(new_end)
        if new_end_sec < self.ctx.to_sec(stop.start):
            raise ApiError(422, "INVALID_VISIT_END", "Новый конец визита раньше его начала",
                           [{"field": "expected_end" if ev.type == "VISIT_DELAYED" else "actual_end",
                             "message": "раньше начала визита"}])
        if idx != len(A.prefix) - 1 and new_end_sec > self.ctx.to_sec(A.prefix[idx + 1].start):
            raise ApiError(422, "EVENT_CONFLICTS_PLAN",
                           f"Следующая остановка {A.k.staff_id} уже начата до нового конца визита {ev.order_id}",
                           [{"field": "order_id", "message": "следующая остановка уже начата"}])
        A.prefix[idx] = stop.model_copy(update={"end": self.ctx.to_dt(new_end_sec)})
        self.day.visit_end_overrides[ev.order_id] = self.ctx.to_dt(new_end_sec)
        if idx == len(A.prefix) - 1:
            A.en = new_end_sec
        self.touch(A)
        what = "задерживается до" if ev.type == "VISIT_DELAYED" else "передан, визит окончен в"
        self.message.append(f"{ev.order_id} у {A.k.staff_id} {what} {self.ctx.to_dt(new_end_sec):%H:%M}")
        if self.joint:
            self._partner_end(ev.order_id, new_end_sec)

    def _partner_end(self, main_id: str, new_end_sec: int) -> None:
        """Напарник заканчивает вместе с основным: тот же новый конец, хвост пересчитывается."""
        pid = jnt.partner_id(main_id)
        for B in self.anchors.values():
            for i, s in enumerate(B.prefix):
                if s.type != StopType.VISIT or s.order_id != pid:
                    continue
                if i != len(B.prefix) - 1 and new_end_sec > self.ctx.to_sec(B.prefix[i + 1].start):
                    self.message.append(f"напарник {B.k.staff_id} уже начал следующую остановку — уходит по плану")
                    return
                old, old_en = B.prefix[i], B.en
                B.prefix[i] = s.model_copy(update={"end": self.ctx.to_dt(new_end_sec)})
                if i == len(B.prefix) - 1:
                    B.en = new_end_sec
                if decode_tail(self.ctx, B, [], self.t) is None:
                    # с продлением у напарника не остаётся допустимого хвоста (перерыв/конец смены):
                    # он уходит по плану, основной заканчивает один
                    B.prefix[i], B.en = old, old_en
                    self.message.append(f"напарник {B.k.staff_id} уходит по плану: продление нарушило бы его смену")
                    return
                self.day.visit_end_overrides[pid] = self.ctx.to_dt(new_end_sec)
                self.touch(B)
                self.message.append(f"напарник {B.k.staff_id} остаётся до {self.ctx.to_dt(new_end_sec):%H:%M}")
                return

    def unavailable(self, ev) -> None:
        A = self.anchors.get(ev.staff_id)
        if A is None:
            raise ApiError(422, "STAFF_NOT_ON_SHIFT", f"Сотрудник {ev.staff_id} не на смене в этот день",
                           [{"field": "staff_id", "message": "сотрудник не на смене"}])
        A.status = RouteStatus.UNAVAILABLE
        A.unav_from = self.t
        A.until = A.unav_until = None
        A.blocked = ev.until is None
        if ev.until is not None:
            u = self.ctx.to_sec(ev.until)
            A.until = A.unav_until = u
            if u >= A.k.end:
                A.blocked = True
        self.unav_staff = A.k.staff_id
        self.touch(A)

    # ------------------------------------------------------------ ПГП
    def _reg(self, oid: str) -> dt.datetime:
        return self.day.orders[oid].registered_at

    def pgp_new_order(self, o: OrderCtx) -> bool:
        """ПГП при NEW_ORDER (formalization §6). True — наряд ушёл в группу."""
        if not o.work_type.pgp_relevant:
            return False
        node = o.model.access_node_id
        r = o.model.registered_at
        for g in self.groups:
            if g.access_node_id != node:
                continue
            last = max(self._reg(x) for x in g.order_ids)
            if dt.timedelta(0) <= r - last <= PGP_WINDOW or (r < last and last - r <= PGP_WINDOW):
                g.order_ids.append(o.order_id)
                g.comment = self._pgp_comment(g)
                self.message.append(f"{o.order_id} добавлен в группу ПГП {g.group_id}")
                return True
        window = [
            x for x in self.day.orders.values()
            if x.access_node_id == node
            and x.order_id not in self.day.cancelled
            and self.ref.work_types[x.work_type_code].pgp_relevant
            and r - PGP_WINDOW <= x.registered_at <= r
        ]
        if len(window) < PGP_MIN_ORDERS:
            return False
        in_groups = {x for g in self.groups for x in g.order_ids}
        members = []
        for x in sorted(window, key=lambda x: (x.registered_at, x.order_id)):
            if x.order_id in in_groups:
                continue
            hit = self.find_visit(x.order_id)
            if hit is not None and hit[1]:
                continue  # визит начат: остаётся в маршруте, но учитывается в пороге
            members.append(x.order_id)
        if len(members) < 2 or o.order_id not in members:
            return False
        gid = self._next_group_id()
        group = PgpGroup(group_id=gid, access_node_id=node, order_ids=members, detected_at=self.ts,
                         action=PgpAction.TRANSFER_TO_STATION)
        group.comment = self._pgp_comment(group)
        self.groups.append(group)
        for x in members:
            if x == o.order_id:
                continue
            hit = self.find_visit(x)
            if hit is not None:
                A = hit[0]
                A.seq = [y for y in A.seq if y.order_id != x]
                self.touch(A)
            if x in self.unassigned:
                del self.unassigned[x]
                self.resolve_alerts(order_id=x)
        self.add_alert(assemble.pgp_alert(self.ids, group, self.ts))
        self.message.append(f"Выявлена ПГП {gid} на {node}: {len(members)} наряда")
        return True

    def _next_group_id(self) -> str:
        n = 0
        for gid in [g.group_id for g in self.groups] + [a.group_id for a in self.alerts if a.group_id]:
            try:
                n = max(n, int(gid.rsplit("-", 1)[1]))
            except (IndexError, ValueError):
                pass
        return f"PGP-{self.day.date:%Y%m%d}-{n + 1:02d}"

    def _pgp_comment(self, g: PgpGroup) -> str:
        regs = [self._reg(x) for x in g.order_ids]
        span = int(round((max(regs) - min(regs)).total_seconds() / 60))
        node = self.ref.access_nodes.get(g.access_node_id)
        name = node.name if node is not None else g.access_node_id
        return f"{len(g.order_ids)} обращения с {name} за {span} мин"

    # ------------------------------------------------------------ шаги 2–3: прогноз и ремонт
    def forecast_and_repair(self) -> None:
        t = self.t
        if self.event.type == "TIME_TICK":
            # прогноз по всем маршрутам: при точном исполнении плана опозданий нет, но если
            # хвост перестал укладываться в окна (например, после сбоя бюджета), чиним сразу
            for A in self.anchors.values():
                if A.available and not A.touched and A.seq:
                    fc = forecast(self.ctx, A, A.seq, t)
                    if any(s > self.ctx.order_by_id[oid].b for oid, s in fc.items()):
                        self.touch(A)
            self.stats["tick_touched"] = sum(1 for A in self.anchors.values() if A.touched)
        for A in self.anchors.values():
            if not A.touched:
                continue
            for oid, s in forecast(self.ctx, A, A.seq, t).items():
                if s > self.ctx.order_by_id[oid].b:
                    self.late[oid] = s
            sched, removed = self.repair(A)
            A.sched, A.sched_ok = sched, True
            unav = A.status == RouteStatus.UNAVAILABLE and A.k.staff_id == self.unav_staff
            for o in removed:
                self.displaced.append((o, A.k.staff_id, unav))

    def _removal_key(self, o: OrderCtx) -> tuple:
        untimely = 0 if o.a - self.t >= TIMELY_SEC else 1
        return (o.weight, untimely, -o.a)

    def repair(self, A: Anchor) -> tuple[TailSched, list[OrderCtx]]:
        ctx, t = self.ctx, self.t
        seq = list(A.seq)
        removed: list[OrderCtx] = []
        if not A.available:
            A.seq = []
            return TailSched([], 0, [], []), seq
        while True:
            res = decode_tail(ctx, A, seq, t)
            if res is not None:
                A.seq = seq
                return res, removed
            # перестановка одного наряда внутри маршрута
            if len(seq) <= 14 and self.time_left():
                moved = None
                for i in range(len(seq)):
                    rest = seq[:i] + seq[i + 1:]
                    for j in range(len(seq)):
                        if j == i:
                            continue
                        cand = rest[:j] + [seq[i]] + rest[j:]
                        r2 = decode_tail(ctx, A, cand, t)
                        if r2 is not None and (moved is None or r2.travel < moved[0].travel):
                            moved = (r2, cand)
                if moved is not None:
                    A.seq = moved[1]
                    return moved[0], removed
            # снятие одного наряда
            best = None
            for i in range(len(seq)):
                cand = seq[:i] + seq[i + 1:]
                r2 = decode_tail(ctx, A, cand, t)
                if r2 is not None:
                    key = (self._removal_key(seq[i]), r2.travel)
                    if best is None or key < best[0]:
                        best = (key, i, r2, cand)
            if best is not None:
                removed.append(seq[best[1]])
                A.seq = best[3]
                return best[2], removed
            if not seq:
                # даже пустой хвост недопустим (например, задержка визита съела окно перерыва):
                # неустранимо, ставим перерыв как можно раньше и возвращаемся в СЦ
                A.seq = []
                brk = -1 if A.needs_break([]) else None
                res = _pass(ctx, A, [], brk, t, relaxed=True) or TailSched([], 0, [], [])
                self.stats["unavoidable"] = self.stats.get("unavoidable", 0) + 1
                self.message.append(f"неустранимо: {A.k.staff_id} — после изменения начатого визита "
                                    f"нельзя соблюсти перерыв или конец смены")
                return res, removed
            # одиночное снятие не помогло: снимаем наряд, на котором ломается префикс
            cut = len(seq) - 1
            for n in range(1, len(seq) + 1):
                if decode_tail(ctx, A, seq[:n], t) is None:
                    cut = n - 1
                    break
            removed.append(seq.pop(cut))

    # ------------------------------------------------------------ шаг 4: вставка
    def insertion(self, A: Anchor, o: OrderCtx, seq: list[OrderCtx] | None = None,
                  base: TailSched | None = None, max_tries: int = 6) -> tuple[int, TailSched, list[OrderCtx]] | None:
        if not A.available or self.t >= A.k.end:
            return None
        if seq is None:
            base = self.sched(A)
            if base is None:
                return None
            seq = A.seq
        assert base is not None
        tau, k, t = self.ctx.tau, A.k, self.t
        cands = []
        for pos in range(len(seq) + 1):
            if pos == 0:
                prev_pt = A.loc if A.prefix else k.sc.point
                prev_end = A.en if A.prefix else max(k.start, t, A.until or 0)
            else:
                prev_pt, prev_end = seq[pos - 1].point, base.ends[pos - 1]
            if max(prev_end, t) + tau[prev_pt][o.point] > o.b:
                break
            next_pt = seq[pos].point if pos < len(seq) else k.sc.point
            if pos < len(seq) and o.a + o.p + tau[o.point][next_pt] > seq[pos].b:
                continue
            delta = tau[prev_pt][o.point] + tau[o.point][next_pt] - tau[prev_pt][next_pt]
            cands.append((delta, pos))
        cands.sort()
        best = None
        tries = 0
        for _, pos in cands:
            cand = seq[:pos] + [o] + seq[pos:]
            res = decode_tail(self.ctx, A, cand, t)
            tries += 1
            if res is not None:
                cost = res.travel - base.travel
                if best is None or cost < best[0]:
                    best = (cost, res, cand)
            if (tries >= max_tries and best is not None) or tries >= 3 * max_tries:
                break
        return best

    def best_staff(self, o: OrderCtx, exclude: str | None = None) -> tuple[int, Anchor, TailSched, list[OrderCtx]] | None:
        best = None
        for kidx in o.eligible:
            A = self.anchors[self.ctx.staff[kidx].staff_id]
            if A.k.staff_id == exclude:
                continue
            res = self.insertion(A, o)
            if res is not None and (best is None or res[0] < best[0]):
                best = (res[0], A, res[1], res[2])
        return best

    def place(self, o: OrderCtx, exclude: str | None = None) -> str | None:
        best = self.best_staff(o, exclude)
        if best is None:
            return None
        _, A, sched, seq = best
        A.seq, A.sched, A.sched_ok, A.touched = seq, sched, True, True
        return A.k.staff_id

    def eject_place(self, o: OrderCtx) -> str | None:
        """Цепочка длины 2: o встаёт на место x (запас x ≥ 30 мин до слота), x переезжает
        к другому сотруднику или снимается, если его вес меньше."""
        if self.joint and jnt.is_joint_code(o.work_type.code):
            return None
        for kidx in o.eligible:
            if not self.time_left():
                return None
            A = self.anchors[self.ctx.staff[kidx].staff_id]
            if not A.available or self.sched(A) is None:
                continue
            for x in list(A.seq):
                if (x.weight > o.weight or x.a - self.t < TIMELY_SEC or abs(x.a - o.a) > EJECT_WINDOW_SEC
                        or x.order_id not in self.prev_staff
                        or (self.joint and jnt.is_joint_code(x.work_type.code))):
                    continue
                rest = [y for y in A.seq if y is not x]
                base = decode_tail(self.ctx, A, rest, self.t)
                if base is None:
                    continue
                ins = self.insertion(A, o, rest, base, max_tries=3)
                if ins is None:
                    continue
                moved = self.best_staff(x, exclude=A.k.staff_id)
                if moved is None and x.weight >= o.weight:
                    continue
                A.seq, A.sched, A.sched_ok, A.touched = ins[2], ins[1], True, True
                if moved is not None:
                    _, B, sched_b, seq_b = moved
                    B.seq, B.sched, B.sched_ok, B.touched = seq_b, sched_b, True, True
                else:
                    self.displaced.append((x, A.k.staff_id, False))
                    self.ejected_unplaced.add(x.order_id)
                return A.k.staff_id
        return None

    def insert_all(self) -> None:
        disp = sorted(self.displaced, key=lambda d: (-d[0].weight, len(d[0].eligible), d[0].a, d[0].order_id))
        for o, src, unav in disp:
            if self.joint and jnt.is_joint_code(o.work_type.code):
                self.joint_redo(o, src, unav)
                continue
            if not self.time_left():
                self.unplaced[o.order_id] = (o, src, unav)
                self.budget_exhausted.add(o.order_id)
                continue
            if self.place(o) is None and self.eject_place(o) is None:
                self.unplaced[o.order_id] = (o, src, unav)
        # снятые при выталкивании — ещё одна попытка
        for oid in list(self.ejected_unplaced):
            entry = next((d for d in self.displaced if d[0].order_id == oid), None)
            if entry is not None and self.time_left() and self.place(entry[0]) is None:
                self.unplaced[oid] = entry
        for o in self.pool_new:
            if not self.time_left():
                self.unplaced[o.order_id] = (o, None, False)
                self.budget_exhausted.add(o.order_id)
                continue
            if self.joint and jnt.is_joint(o):
                sid = self.joint_place(o, None, False)
            else:
                sid = self.place(o)
                if sid is None:
                    self.unplaced[o.order_id] = (o, None, False)
            if sid is None:
                self.message.append(f"{o.order_id} не распределён")
            else:
                self.message.append(f"{o.order_id} назначен {sid}")
        # освободившееся время: нераспределённые — в затронутые маршруты
        freed = [A for A in self.anchors.values() if A.touched and A.available]
        if freed:
            ids = {A.k.idx for A in freed}
            pool = [self.ctx.order_by_id[oid] for oid in self.unassigned if oid in self.ctx.order_by_id]
            pool = [o for o in pool if o.b >= self.t and ids.intersection(o.eligible)
                    and not (self.joint and jnt.is_joint(o))]
            pool.sort(key=lambda o: (-o.weight, len(o.eligible), o.a, o.order_id))
            for o in pool:
                if not self.time_left():
                    break
                best = None
                for A in freed:
                    if A.k.idx not in o.eligible:
                        continue
                    res = self.insertion(A, o, max_tries=3)
                    if res is not None and (best is None or res[0] < best[0]):
                        best = (res[0], A, res[1], res[2])
                if best is not None:
                    _, A, sched, seq = best
                    A.seq, A.sched, A.sched_ok = seq, sched, True
                    self.inserted_from_unassigned.add(o.order_id)
        self.stats["unassigned_inserted"] = len(self.inserted_from_unassigned)
        if self.inserted_from_unassigned:
            self.message.append(f"в освободившееся время вставлено нераспределённых: {len(self.inserted_from_unassigned)}")
        placed = len(self.displaced) - len([d for d in self.displaced if d[0].order_id in self.unplaced])
        if self.displaced:
            self.message.append(f"снято с маршрутов {len(self.displaced)}, передано другим {placed}")

    # ------------------------------------------------------------ unassigned
    def classify(self, o: OrderCtx, unav: bool, exhausted: bool) -> UnassignedOrder:
        ctx, t = self.ctx, self.t
        slot = assemble.proposed_slot(ctx, o)
        action = UnassignedAction.RESCHEDULE if slot is not None else UnassignedAction.ESCALATE
        window = f"{ctx.to_dt(o.a):%H:%M}–{ctx.to_dt(o.b):%H:%M}"
        if unav:
            return UnassignedOrder(order_id=o.order_id, reason_code=ReasonCode.STAFF_UNAVAILABLE,
                                   reason_text=f"Исполнитель {self.unav_staff} выбыл, замену в окне {window} найти не удалось",
                                   suggested_action=action, proposed_slot=slot, since=self.ts)
        if exhausted:
            return UnassignedOrder(order_id=o.order_id, reason_code=ReasonCode.OTHER,
                                   reason_text="Исчерпан бюджет расчёта события до вставки наряда",
                                   suggested_action=UnassignedAction.ESCALATE, since=self.ts)
        staff = [self.anchors[ctx.staff[i].staff_id] for i in o.eligible]
        staff = [A for A in staff if A.available]
        if self.joint and o.order_id in self.joint_fail:
            if len(staff) < 2:
                return UnassignedOrder(order_id=o.order_id, reason_code=ReasonCode.NO_QUALIFIED_STAFF,
                                       reason_text=self.joint_fail[o.order_id],
                                       suggested_action=UnassignedAction.REASSIGN, since=self.ts)
            return UnassignedOrder(order_id=o.order_id, reason_code=ReasonCode.NO_SHIFT_TIME,
                                   reason_text=self.joint_fail[o.order_id],
                                   suggested_action=action, proposed_slot=slot, since=self.ts)
        if not staff:
            need = f"профилем {'/'.join(o.work_type.profiles)}, технологией {o.model.technology}"
            if o.model.requires_height:
                need += " и допуском к работам на высоте"
            return UnassignedOrder(order_id=o.order_id, reason_code=ReasonCode.NO_QUALIFIED_STAFF,
                                   reason_text=f"На смене нет доступного сотрудника с {need}",
                                   suggested_action=UnassignedAction.REASSIGN, since=self.ts)
        reachable = False
        for A in staff:
            loc = A.loc if A.prefix else A.k.sc.point
            free = max(A.en, t, A.until or 0) if A.prefix else max(A.k.start, t, A.until or 0)
            st = max(free + ctx.tt(loc, o.point, free), o.a)
            if st <= o.b and st + o.p + ctx.tt(o.point, A.k.sc.point, st + o.p) <= A.k.end:
                reachable = True
                break
        if not reachable:
            return UnassignedOrder(order_id=o.order_id, reason_code=ReasonCode.SLOT_UNREACHABLE,
                                   reason_text=f"Ни один из {len(staff)} допустимых сотрудников не успевает начать визит в окне {window}",
                                   suggested_action=action, proposed_slot=slot, since=self.ts)
        return UnassignedOrder(order_id=o.order_id, reason_code=ReasonCode.NO_SHIFT_TIME,
                               reason_text=(f"У {len(staff)} допустимых сотрудников не хватает времени: визит в окне "
                                            f"{window} не встаёт без срыва уже назначенных визитов"),
                               suggested_action=action, proposed_slot=slot, since=self.ts)

    # ------------------------------------------------------------ сборка
    def route_of(self, A: Anchor) -> Route:
        prev = A.prev_route
        if not A.touched and prev is not None and A.status == prev.status:
            r = prev.model_copy(update={"stops": [s.model_copy() for s in prev.stops]})
            return r
        ctx = self.ctx
        sched = A.sched if A.sched_ok else decode_tail(ctx, A, A.seq, self.t)
        if sched is None:  # не должно случаться: ремонт оставляет допустимый хвост
            sched = TailSched([], 0, [], [])
        stops: list[Stop] = [s.model_copy() for s in A.prefix]
        prev_point = A.loc if A.prefix else None
        for s in sched.stops:
            dist = 0 if (prev_point is None or s.kind == BREAK) else ctx.dist[prev_point][s.point]
            o = s.order
            expl = None
            if o is not None:
                expl = (f"{A.k.staff_id} ({'/'.join(A.k.model.profiles)}, {o.model.technology}); "
                        f"в пути {round(s.travel / 60)} мин; запас до крайнего начала {max(0, (o.b - s.st) // 60)} мин; "
                        f"перепланировано в {self.ts:%H:%M}")
                if self._pairs:
                    expl = "; ".join([expl] + assemble.joint_note(self._pairs, o.order_id))
            stops.append(Stop(
                seq=1, type=StopType(s.kind), location_id=ctx.point_ids[s.point],
                order_id=o.order_id if o is not None else None,
                arrival=ctx.to_dt(s.arr), start=ctx.to_dt(s.st), end=ctx.to_dt(s.en),
                travel_sec=s.travel, distance_m=dist, status=StopStatus.PLANNED,
                equipment_after=Equipment(ONT=max(0, s.inv[0]), ROUTER=max(0, s.inv[1]), STB=max(0, s.inv[2])),
                explanation=expl,
            ))
            prev_point = s.point
        if not any(s.type == StopType.VISIT for s in stops) and not A.prefix and A.status == RouteStatus.ACTIVE:
            stops = []
        for i, s in enumerate(stops, start=1):
            s.seq = i
        travel = sum(s.travel_sec or 0 for s in stops)
        work = sum((s.end - s.start).total_seconds() for s in stops if s.type == StopType.VISIT)
        visits = sum(1 for s in stops if s.type == StopType.VISIT)
        totals = RouteTotals(
            visits=visits,
            travel_sec=travel if visits else 0,
            distance_m=sum(s.distance_m or 0 for s in stops),
            work_min=int(work // 60),
            utilization=round((work + travel) / A.k.net_shift, 4) if visits else 0.0,
        )
        unav_from = unav_until = None
        if A.status == RouteStatus.UNAVAILABLE:
            if prev is not None and prev.status == RouteStatus.UNAVAILABLE and A.k.staff_id != self.unav_staff:
                unav_from, unav_until = prev.unavailable_from, prev.unavailable_until
            else:
                unav_from = self.ts
                unav_until = getattr(self.event, "until", None)
        return Route(staff_id=A.k.staff_id, service_center_id=A.k.sc.sc_id, status=A.status,
                     unavailable_from=unav_from, unavailable_until=unav_until, stops=stops, totals=totals)

    def assemble(self) -> Plan:
        self._restore()
        self._pairs = self._joint_pairs_now() if self.joint else {}
        routes = [self.route_of(self.anchors[k.staff_id]) for k in self.ctx.staff]
        new_staff: dict[str, str] = {}
        for r in routes:
            for s in r.stops:
                if s.type == StopType.VISIT and s.order_id:
                    new_staff[s.order_id] = r.staff_id

        # нераспределённые: вставленные — из списка, снятые и новые — в список
        for oid in list(self.unassigned):
            if oid in new_staff:
                del self.unassigned[oid]
                self.resolve_alerts(order_id=oid)
        pgp_ids = {x for g in self.groups for x in g.order_ids}
        for oid, (o, src, unav) in self.unplaced.items():
            if oid in new_staff or oid in pgp_ids or oid in self.day.cancelled:
                continue
            if jnt.main_of(oid, self.day.orders) is not None:
                continue  # визит напарника — не наряд дня, в unassigned не попадает
            u = self.classify(o, unav, oid in self.budget_exhausted)
            self.unassigned[oid] = u
            if src is None:
                self.add_alert(assemble.unassigned_alert(self.ids, u, self.ts))

        # LATE_RISK: каждый наряд, сменивший исполнителя или снятый из маршрута, и прогноз опоздания
        for oid, p in self.prev_staff.items():
            if oid in self.day.cancelled or jnt.main_of(oid, self.day.orders) is not None:
                continue
            q = new_staff.get(oid)
            if q == p and oid not in self.late:
                continue
            o = self.ctx.order_by_id.get(oid)
            if o is None:
                continue
            f = self.late.get(oid)
            if q == p:
                # прогноз опоздания, устранённый у того же исполнителя: предупреждаем, только если
                # наряд переставлен в маршруте (иначе его спасло снятие соседа — оно и предупреждено)
                if self.find_frozen(oid) or not self._reordered(p, oid):
                    continue
                self.resolve_alerts(order_id=oid)
                self.add_alert(self.late_alert(o, p, f, AlertAction.NONE, None, None, True,
                                               f"Прогноз опоздания {oid} у {p}; устранено перестановкой в маршруте"))
            elif q is not None:
                self.resolve_alerts(order_id=oid)
                reason = self._why(p)
                self.add_alert(self.late_alert(o, p, f, AlertAction.REASSIGN, q, None, True,
                                               f"{reason}: {oid} передан {q}"))
            elif oid in pgp_ids:
                self.add_alert(self.late_alert(o, p, f, AlertAction.TRANSFER_TO_STATION, None, None, True,
                                               f"{oid} снят из маршрута {p}: группа ПГП, передан в станционное подразделение"))
            else:
                u = self.unassigned.get(oid)
                slot = u.proposed_slot if u is not None else None
                action = AlertAction.RESCHEDULE if slot is not None else AlertAction.NONE
                reason = self._why(p)
                self.add_alert(self.late_alert(o, p, f, action, None, slot, True,
                                               f"{reason}: {oid} снят из маршрута, замены нет"
                                               + (f" — предложен перенос на {slot.start:%d.%m %H:%M}" if slot else "")))
        cancelled = list(self.day.cancelled.values())
        unassigned = list(self.unassigned.values())
        plan = Plan(date=self.day.date, plan_version=self.prev.plan_version, based_on_event_id=self.event.event_id,
                    as_of=self.ts, routes=routes, unassigned=unassigned, pgp_groups=self.groups,
                    cancelled=cancelled, alerts=self.alerts)
        return plan

    def _reordered(self, staff_id: str, oid: str) -> bool:
        A = self.anchors[staff_id]
        now_ids = [o.order_id for o in A.seq]
        common = set(now_ids) & set(A.prev_seq_ids)
        before = [x for x in A.prev_seq_ids if x in common]
        after = [x for x in now_ids if x in common]
        return oid in common and before.index(oid) != after.index(oid)

    def find_frozen(self, oid: str) -> bool:
        hit = self.find_visit(oid)
        return hit is not None and hit[1]

    def _why(self, staff_id: str) -> str:
        ev = self.event
        if ev.type == "STAFF_UNAVAILABLE" and ev.staff_id == staff_id:
            return f"{staff_id} выбыл"
        if ev.type == "VISIT_DELAYED" and ev.staff_id == staff_id:
            return f"{staff_id} задерживается на {ev.order_id}"
        return f"Перепланирование маршрута {staff_id} ({ev.type})"

    def late_alert(self, o: OrderCtx, staff_id: str, f: int | None, action: AlertAction, new_staff: str | None,
                   slot, applied: bool, message: str) -> Alert:
        resolved = action in (AlertAction.REASSIGN, AlertAction.NONE, AlertAction.TRANSFER_TO_STATION) and applied
        return Alert(
            alert_id=self.ids.next(),
            created_at=self.ts,
            type=AlertType.LATE_RISK,
            status=AlertStatus.RESOLVED if resolved else AlertStatus.ACTIVE,
            resolved_at=self.ts if resolved else None,
            order_id=o.order_id,
            staff_id=staff_id,
            forecast_start=self.ctx.to_dt(f) if f is not None else None,
            lateness_min=math.ceil((f - o.b) / 60) if f is not None and f > o.b else None,
            suggested_action=action,
            suggested_staff_id=new_staff,
            proposed_slot=slot,
            applied=applied,
            message=message,
        )

    # ------------------------------------------------------------ самопроверка
    def violations(self, plan: Plan) -> list:
        viol = validate_plan(plan, self.ref, self.matrix, self.day, joint=self.joint)
        viol += validate_transition(self.prev, plan, self.ref, self.matrix, self.day, self.ts, self.event)
        # нарушения, которые уже были в прежней версии (например, задержка за конец смены), не наши
        prev_viol = {(v.code, v.order_id, v.staff_id)
                     for v in validate_plan(self.prev, self.ref, self.matrix, self.day, joint=self.joint)}
        return [v for v in viol if (v.code, v.order_id, v.staff_id) not in prev_viol]

    def check(self, plan: Plan) -> Plan:
        new = self.violations(plan)
        self.stats["violations"] = len(new)
        if not new:
            return plan
        self.stats["violations_detail"] = [str(v) for v in new[:5]]
        bad = {v.order_id for v in new if v.order_id}
        if self.joint:  # пара снимается целиком
            bad |= {jnt.partner_id(x) for x in bad} | {m for x in bad if (m := jnt.main_of(x))}
        fixed = False
        for A in self.anchors.values():
            drop = [o for o in A.seq if o.order_id in bad]
            if not drop:
                continue
            A.seq = [o for o in A.seq if o.order_id not in bad]
            A.touched, A.sched_ok = True, None
            sched, removed = self.repair(A)
            A.sched, A.sched_ok = sched, True
            for o in drop + removed:
                self.unplaced[o.order_id] = (o, A.k.staff_id, False)
                self.budget_exhausted.add(o.order_id)
            fixed = True
        if not fixed:
            return plan
        plan = self.assemble()
        self.stats["violations_after_fix"] = len(self.violations(plan))
        return plan

    def _snapshot(self) -> None:
        self._snap = ({k: v.model_copy() for k, v in self.unassigned.items()},
                      [a.model_copy() for a in self.alerts], self.ids.n)

    def _restore(self) -> None:
        un, alerts, n = self._snap
        self.unassigned = {k: v.model_copy() for k, v in un.items()}
        self.alerts = [a.model_copy() for a in alerts]
        self.ids.n = n

    @property
    def new_alerts(self) -> list[Alert]:
        return self.alerts[self.n_prev_alerts:]

    def run(self) -> PlanUpdate:
        self.apply()
        self.forecast_and_repair()
        self.insert_all()
        self._snapshot()
        plan = self.check(self.assemble())
        set_statuses(plan, self.ctx, self.t)
        plan.summary = assemble.summary(plan, len(self.day.orders))
        changed = []
        prev_routes = {r.staff_id: r for r in self.prev.routes}
        for r in plan.routes:
            p = prev_routes.get(r.staff_id)
            if p is None or _sig(p) != _sig(r) or p.status != r.status:
                changed.append(r.staff_id)
        late = sum(1 for a in self.new_alerts if a.type == AlertType.LATE_RISK)
        if late:
            self.message.append(f"LATE_RISK: {late}")
        self.stats["ms"] = int((time.perf_counter() - self.started) * 1000)
        return PlanUpdate(plan=plan, changed_staff_ids=sorted(changed), new_alerts=list(self.new_alerts),
                          message="; ".join(self.message) or None, stats=self.stats)


def _sig(route: Route) -> list[tuple]:
    return [(s.type, s.order_id, s.location_id, s.arrival, s.start, s.end) for s in route.stops]


def set_statuses(plan: Plan, ctx: DayContext, t: int) -> None:
    for r in plan.routes:
        for s in r.stops:
            st, en = ctx.to_sec(s.start), ctx.to_sec(s.end)
            if st < t:
                s.status = StopStatus.DONE if en <= t else StopStatus.IN_PROGRESS
            else:
                s.status = StopStatus.PLANNED


def apply_event(ref: ReferenceState, matrix: MatrixState, day: DayState, event: Event,
                traffic: bool | None = None, joint: bool | None = None) -> PlanUpdate:
    """traffic — учёт пробок по часам; None — из TRAFFIC_FACTORS. joint — JOINT_ORDERS."""
    return EventPlanner(ref, matrix, day, event, traffic=traffic, joint=joint).run()
````

## File: backend/app/planner/joint.py
````python
"""Совместные наряды (дополнительное задание ТЗ): инсталляция в частном секторе с ВЛС
(`INST_PRIVATE_VLS`) выполняется двумя сотрудниками одновременно.

Режим `JOINT_ORDERS=on|off`, по умолчанию **off** (docs/planner-notes.md, «Совместные наряды»).

Представление в плане (контракт не меняется): основной исполнитель получает обычный VISIT
по наряду `ORD-…`, напарник — VISIT того же дома, того же начала и длительности по
идентификатору `ORD-…-B` (`partner_id`). Такого наряда нет во входных данных дня, поэтому
валидатор организаторов, скорее всего, посчитает визит напарника визитом по неизвестному
наряду (H9) — отсюда выключенный по умолчанию режим.

Внутри планировщика наряд — один «совместный»: основной визит и визит напарника — это
копии OrderCtx с окном, закреплённым в одну точку `[s, s]` (`pinned`, `partner_ctx`).
Декодер (decoder.py и декодер хвоста events.py) соблюдает окно, поэтому оба визита
начинаются ровно в `s`. АО списывается только у основного исполнителя (у напарника
`dem = (0, 0, 0)`).
"""

from __future__ import annotations

import dataclasses
import os
import time
from typing import Callable

from .context import OrderCtx

JOINT_WORK_TYPE = "INST_PRIVATE_VLS"
PARTNER_SUFFIX = "-B"
GRID_SEC = 5 * 60  # шаг перебора общего начала внутри [slot.start, latest_start]


def joint_enabled() -> bool:
    """Переключатель JOINT_ORDERS=on|off (по умолчанию off). Читается при создании контекста."""
    return os.environ.get("JOINT_ORDERS", "off").strip().lower() in ("on", "1", "true", "yes")


def partner_id(main_id: str) -> str:
    return main_id + PARTNER_SUFFIX


def main_of(order_id: str | None, known=None) -> str | None:
    """Идентификатор основного наряда для визита напарника (`ORD-…-B`), иначе None.

    known — множество/словарь известных нарядов: основной наряд должен в нём быть."""
    if not order_id or not order_id.endswith(PARTNER_SUFFIX):
        return None
    main = order_id[: -len(PARTNER_SUFFIX)]
    if known is not None and main not in known:
        return None
    return main


def is_joint(o: OrderCtx) -> bool:
    """Основной наряд, требующий напарника (по типу работ)."""
    return o.work_type.code == JOINT_WORK_TYPE and not o.order_id.endswith(PARTNER_SUFFIX)


def is_joint_code(work_type_code: str) -> bool:
    return work_type_code == JOINT_WORK_TYPE


def pinned(o: OrderCtx, s: int) -> OrderCtx:
    """Копия наряда с окном начала, закреплённым в момент s."""
    return dataclasses.replace(o, a=s, b=s, eligible=list(o.eligible))


def partner_ctx(o: OrderCtx, s: int) -> OrderCtx:
    """Визит напарника: тот же дом и норматив, начало ровно s, без АО и без веса в целевой."""
    return dataclasses.replace(
        o, order_id=partner_id(o.order_id), a=s, b=s, dem=(0, 0, 0), weight=0, eligible=list(o.eligible)
    )


def start_grid(o: OrderCtx, lo: int) -> list[int]:
    """Кандидаты общего начала: [max(a, lo), b] с шагом GRID_SEC, b включительно."""
    a = max(o.a, lo)
    if a > o.b:
        return []
    out = list(range(a, o.b + 1, GRID_SEC))
    if out[-1] != o.b:
        out.append(o.b)
    return out


def search(
    o: OrderCtx,
    starts: list[int],
    staff: list[int],
    try_insert: Callable[[int, OrderCtx], tuple[int, object] | None],
    deadline: float | None = None,
) -> tuple[int, int, object, OrderCtx, int, object, OrderCtx] | None:
    """Лучшая пара (основной k1, напарник k2 ≠ k1) и общее начало s по сумме прироста пути.

    try_insert(k, наряд) -> (прирост, расписание) или None. Возвращает
    (s, k1, расписание k1, закреплённый основной, k2, расписание k2, визит напарника)."""
    best = None
    for s in starts:
        if deadline is not None and time.perf_counter() > deadline and best is not None:
            break
        m, p = pinned(o, s), partner_ctx(o, s)
        mains = []
        for k in staff:
            r = try_insert(k, m)
            if r is not None:
                mains.append((r[0], k, r[1]))
        if not mains:
            continue
        partners = []
        for k in staff:
            r = try_insert(k, p)
            if r is not None:
                partners.append((r[0], k, r[1]))
        if not partners:
            continue
        mains.sort(key=lambda x: x[0])
        partners.sort(key=lambda x: x[0])
        for c1, k1, r1 in mains[:3]:
            for c2, k2, r2 in partners[:3]:
                if k1 == k2:
                    continue
                if best is None or c1 + c2 < best[0]:
                    best = (c1 + c2, (s, k1, r1, m, k2, r2, p))
                break
    return best[1] if best is not None else None


def unassigned_text(o: OrderCtx, n_staff: int) -> tuple[bool, str]:
    """(нет двух допустимых сотрудников, текст причины) для совместного наряда без пары."""
    window = f"{o.a // 3600:02d}:{o.a % 3600 // 60:02d}–{o.b // 3600:02d}:{o.b % 3600 // 60:02d}"
    if n_staff < 2:
        return True, (
            f"Совместный наряд: нужны два сотрудника с допуском к работам на высоте, профилем "
            f"{'/'.join(o.work_type.profiles)} и технологией {o.model.technology}; на смене таких {n_staff}"
        )
    return False, (
        f"Совместный наряд: среди {n_staff} допустимых сотрудников не нашлось двух, у которых одновременно "
        f"свободно {o.p // 60} мин с началом в окне {window} без срыва уже назначенных визитов"
    )
````

## File: backend/app/planner/pgp.py
````python
"""Выявление ПГП (подозрение на групповую проблему) — docs/formalization.md, раздел 6.

Кандидаты — наряды с `work_type.pgp_relevant = true`. На узле доступа наряды
упорядочиваются по `registered_at`; наряд входит в инцидент, если попадает в
какое-либо 60-минутное окно, где таких нарядов не меньше 3. Соседние помеченные
наряды узла с разрывом не больше 60 минут образуют одну группу.
"""

from __future__ import annotations

import datetime as dt
from dataclasses import dataclass

from ..models import Order, WorkType

PGP_MIN_ORDERS = 3
PGP_WINDOW = dt.timedelta(minutes=60)


@dataclass
class PgpCluster:
    access_node_id: str
    orders: list[Order]

    @property
    def span_min(self) -> int:
        regs = [o.registered_at for o in self.orders]
        return int(round((max(regs) - min(regs)).total_seconds() / 60))


def detect_pgp(orders: list[Order], work_types: dict[str, WorkType]) -> list[PgpCluster]:
    by_node: dict[str, list[Order]] = {}
    for o in orders:
        wt = work_types.get(o.work_type_code)
        if wt is not None and wt.pgp_relevant:
            by_node.setdefault(o.access_node_id, []).append(o)

    clusters: list[PgpCluster] = []
    for node_id in sorted(by_node):
        items = sorted(by_node[node_id], key=lambda o: (o.registered_at, o.order_id))
        n = len(items)
        marked = [False] * n
        hi = 0
        for lo in range(n):
            hi = max(hi, lo)
            while hi + 1 < n and items[hi + 1].registered_at - items[lo].registered_at <= PGP_WINDOW:
                hi += 1
            if hi - lo + 1 >= PGP_MIN_ORDERS:
                for j in range(lo, hi + 1):
                    marked[j] = True
        current: list[Order] = []
        for j in range(n):
            if not marked[j]:
                continue
            if current and items[j].registered_at - current[-1].registered_at > PGP_WINDOW:
                clusters.append(PgpCluster(node_id, current))
                current = []
            current.append(items[j])
        if current:
            clusters.append(PgpCluster(node_id, current))
    clusters.sort(key=lambda c: (min(o.registered_at for o in c.orders), c.access_node_id))
    return clusters
````

## File: backend/app/planner/pyvrp_planner.py
````python
"""Планировщик на PyVRP: первоначальный план (этап A).

Конвейер build_plan (docs/formalization.md, разделы 3–7):
1. ПГП-группы (≥3 наряда pgp_relevant с узла за ≤60 мин) выносятся из распределения.
2. Наряды без допустимых сотрудников (H1) -> NO_QUALIFIED_STAFF.
3. PyVRP выбирает назначение и порядок визитов (vrp_model).
4. Декодер строит точное расписание с перерывом и пополнениями; недопустимые
   маршруты чинятся снятием минимального числа нарядов.
5. Жадная вставка снятых и невыбранных нарядов (по весу, затем по редкости допуска).
6. Перенос нарядов для выравнивания загрузки (если остался бюджет).
7. Причины unassigned, предупреждения, сводка; самопроверка validator.py.

События (этап B) обрабатываются инкрементально в events.py.
"""

from __future__ import annotations

import os
import random
import time

from ..models import Event, Plan
from ..state import DayState, MatrixState, ReferenceState
from . import assemble
from .base import PlanUpdate
from .context import DayContext, OrderCtx, StaffCtx
from .decoder import VISIT, RouteSchedule, best_insertion, decode
from . import joint as jnt
from .pgp import detect_pgp
from .validator import validate_plan
from .vrp_model import Weights, solve_sequences

W4_BALANCE = 3600  # вес Σ(ρ_k − ρ̄)², docs/formalization.md, раздел 3


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.environ.get(name, default))
    except ValueError:
        return default


def solve_budget(time_limit_sec: int | None) -> float:
    """Внутренний бюджет на день: min(PLANNER_DAY_BUDGET_SEC, 80 % лимита − 3 с)."""
    day_budget = _env_float("PLANNER_DAY_BUDGET_SEC", 60.0)
    limit = float(time_limit_sec or 300)
    return max(0.3, min(day_budget, limit * 0.8 - 3.0))


class PyVrpPlanner:
    def __init__(self, seed: int = 1, traffic: bool | None = None, joint: bool | None = None) -> None:
        self.seed = seed
        # учёт пробок по часам в слое расписания; None — из TRAFFIC_FACTORS (context.traffic_enabled)
        self.traffic = traffic
        # совместные наряды; None — из JOINT_ORDERS (planner/joint.py)
        self.joint = joint
        self.last_stats: dict = {}

    # ------------------------------------------------------------ build
    def build_plan(
        self,
        ref: ReferenceState,
        matrix: MatrixState,
        day: DayState,
        time_limit_sec: int | None = None,
    ) -> Plan:
        t0 = time.perf_counter()
        budget = solve_budget(time_limit_sec)
        deadline = t0 + budget - min(1.5, 0.1 * budget)  # запас на сборку и самопроверку
        stats: dict = {"budget_sec": round(budget, 2)}

        pending = day.pending_orders()
        clusters = detect_pgp(pending, ref.work_types)
        pgp_ids = {o.order_id for c in clusters for o in c.orders}
        routable = [o for o in pending if o.order_id not in pgp_ids]

        ctx = DayContext(ref, matrix, day, routable, day.now, traffic=self.traffic, joint=self.joint)
        stats["traffic"] = ctx.traffic
        weights = Weights.from_ctx(ctx)
        candidates = [o for o in ctx.orders if o.eligible]

        # ---- PyVRP
        prep = time.perf_counter() - t0
        share = _env_float("PLANNER_PYVRP_SHARE", 0.7)
        pyvrp_time = max(0.1, (budget - prep) * share)
        t1 = time.perf_counter()
        seqs, info = solve_sequences(ctx, candidates, weights, pyvrp_time, seed=self.seed)
        stats["pyvrp_sec"] = round(time.perf_counter() - t1, 2)
        stats["pyvrp_feasible"] = info.get("feasible")
        stats["pyvrp_iterations"] = info.get("iterations")
        stats["pyvrp_visits"] = sum(len(s) for s in seqs.values())

        # ---- декодер + починка
        schedules: dict[int, RouteSchedule] = {}
        dropped: list[OrderCtx] = []
        for k in ctx.staff:
            sched, removed = self._feasible_schedule(ctx, k, seqs.get(k.idx, []))
            schedules[k.idx] = sched
            dropped.extend(removed)
        stats["decoder_dropped"] = len(dropped)

        # ---- вставка
        routed = {o.order_id for s in schedules.values() for o in s.orders}
        pool = [o for o in candidates if o.order_id not in routed]
        inserted, exhausted = self._insert(ctx, schedules, pool, deadline)
        stats["repair_inserted"] = inserted
        if not exhausted:
            gain = self._eject_insert(ctx, schedules, deadline)
            stats["eject_gain"] = gain
            # LNS на точном декодере в остаток бюджета (оставляем запас на выравнивание)
            lns_deadline = deadline - 0.1 * (deadline - time.perf_counter())
            stats["lns"] = self._lns(ctx, schedules, weights, lns_deadline, self.seed)
            stats["eject_gain_2"] = self._eject_insert(ctx, schedules, deadline)

        # ---- совместные наряды: напарник к каждому размещённому INST_PRIVATE_VLS (JOINT_ORDERS=on)
        if ctx.joint:
            stats["joint"] = self._joint_pairs(ctx, schedules, deadline)

        # ---- выравнивание загрузки
        moves = self._balance(ctx, schedules, deadline)
        stats["balance_moves"] = moves

        plan = self._assemble(ctx, day, schedules, clusters, exhausted)

        # ---- самопроверка: наряды с нарушениями снимаются (не должно срабатывать)
        violations = validate_plan(plan, ref, matrix, day, joint=ctx.joint)
        stats["violations_fixed"] = len(violations)
        if violations:
            bad = {v.order_id for v in violations if v.order_id}
            if ctx.joint:
                # пара снимается целиком: основной наряд и визит напарника
                bad |= {jnt.partner_id(x) for x in bad} | {jnt.main_of(x) for x in bad if jnt.main_of(x)}
                for x in bad:
                    if x in ctx.order_by_id and jnt.is_joint(ctx.order_by_id[x]):
                        ctx.joint_fail.setdefault(x, "Совместный наряд снят самопроверкой плана")
            bad_staff = {v.staff_id for v in violations if v.staff_id and not v.order_id}
            for kidx, sched in list(schedules.items()):
                k = ctx.staff[kidx]
                keep = [o for o in sched.orders if o.order_id not in bad]
                if k.staff_id in bad_staff:
                    keep = []
                if len(keep) != len(sched.orders):
                    schedules[kidx], _ = self._feasible_schedule(ctx, k, keep)
            plan = self._assemble(ctx, day, schedules, clusters, exhausted)
        stats["total_sec"] = round(time.perf_counter() - t0, 2)
        self.last_stats = stats
        return plan

    # ------------------------------------------------------------ steps
    @staticmethod
    def _feasible_schedule(
        ctx: DayContext, k: StaffCtx, seq: list[OrderCtx]
    ) -> tuple[RouteSchedule, list[OrderCtx]]:
        """Расписание для seq; при недопустимости снимаются наряды (минимальный вес первым)."""
        seq = list(seq)
        removed: list[OrderCtx] = []
        while True:
            sched = decode(ctx, k, seq)
            if sched is not None:
                return sched, removed
            # сначала перестановка внутри маршрута (чаще всего мешает перерыв)
            moved: RouteSchedule | None = None
            for i in range(len(seq)):
                rest = seq[:i] + seq[i + 1 :]
                for j in range(len(seq)):
                    if j == i:
                        continue
                    res = decode(ctx, k, rest[:j] + [seq[i]] + rest[j:])
                    if res is not None and (moved is None or res.travel < moved.travel):
                        moved = res
            if moved is not None:
                return moved, removed
            best: tuple[tuple, int, RouteSchedule] | None = None
            for i in range(len(seq)):
                res = decode(ctx, k, seq[:i] + seq[i + 1 :])
                if res is not None:
                    key = (seq[i].weight, res.travel)
                    if best is None or key < best[0]:
                        best = (key, i, res)
            if best is not None:
                removed.append(seq[best[1]])
                return best[2], removed
            # одиночное снятие не помогло: снимаем первый наряд, на котором ломается префикс
            cut = len(seq) - 1
            for n in range(1, len(seq) + 1):
                if decode(ctx, k, seq[:n]) is None:
                    cut = n - 1
                    break
            removed.append(seq.pop(cut))

    @staticmethod
    def _insert(
        ctx: DayContext,
        schedules: dict[int, RouteSchedule],
        pool: list[OrderCtx],
        deadline: float,
    ) -> tuple[int, bool]:
        """Жадная вставка: сначала повторные, затем с меньшим числом допустимых исполнителей."""
        pool = sorted(pool, key=lambda o: (-o.weight, len(o.eligible), o.a, o.order_id))
        inserted = 0
        for _round in range(2):
            rest = []
            for o in pool:
                if time.perf_counter() > deadline:
                    return inserted, True
                best = None
                for kidx in o.eligible:
                    res = best_insertion(ctx, schedules[kidx], o)
                    if res is not None and (best is None or res[0] < best[0]):
                        best = (res[0], kidx, res[1])
                if best is None:
                    rest.append(o)
                    continue
                schedules[best[1]] = best[2]
                inserted += 1
            if len(rest) == len(pool):
                break
            pool = rest
        return inserted, False

    @staticmethod
    def _eject_insert(
        ctx: DayContext,
        schedules: dict[int, RouteSchedule],
        deadline: float,
    ) -> int:
        """Цепочка выталкивания длины 2: наряд o встаёт на место x, x переезжает к другому
        сотруднику (или снимается, если вес x меньше веса o). Возвращает прирост Σw."""
        routed = {o.order_id for s in schedules.values() for o in s.orders}
        pool = sorted(
            (o for o in ctx.orders if o.eligible and o.order_id not in routed),
            key=lambda o: (-o.weight, len(o.eligible), o.a, o.order_id),
        )
        gained = 0
        window = 3 * 3600
        for o in pool:
            if time.perf_counter() > deadline:
                break
            applied = False
            for kidx in o.eligible:
                sched = schedules[kidx]
                for x in sched.orders:
                    if x.weight > o.weight or abs(x.a - o.a) > window:
                        continue
                    base = decode(ctx, sched.staff, [y for y in sched.orders if y is not x])
                    if base is None:
                        continue
                    ins = best_insertion(ctx, base, o, max_tries=3)
                    if ins is None:
                        continue
                    moved = None
                    for k2 in x.eligible:
                        if k2 == kidx:
                            continue
                        res = best_insertion(ctx, schedules[k2], x, max_tries=3)
                        if res is not None and (moved is None or res[0] < moved[0]):
                            moved = (res[0], k2, res[1])
                    if moved is not None:
                        schedules[kidx] = ins[1]
                        schedules[moved[1]] = moved[2]
                        gained += o.weight
                        applied = True
                    elif x.weight < o.weight:
                        schedules[kidx] = ins[1]
                        gained += o.weight - x.weight
                        applied = True
                    if applied:
                        break
                    if time.perf_counter() > deadline:
                        break
                if applied or time.perf_counter() > deadline:
                    break
        return gained

    @staticmethod
    def _lns(
        ctx: DayContext,
        schedules: dict[int, RouteSchedule],
        weights: Weights,
        deadline: float,
        seed: int,
    ) -> dict:
        """Разрушить-и-восстановить: снимаются связанные наряды (близкие по месту и
        времени), затем жадная вставка снятых и нераспределённых. Принимается только
        улучшение W1·Σw − T."""
        rng = random.Random(seed)

        def value(scheds: dict[int, RouteSchedule]) -> int:
            return sum(weights.w1 * o.weight for s in scheds.values() for o in s.orders) - sum(
                s.travel for s in scheds.values() if s.orders
            )

        tau = ctx.tau
        current = value(schedules)
        iters = accepted = 0
        while time.perf_counter() < deadline:
            iters += 1
            routed = [(o, kidx) for kidx, s in schedules.items() for o in s.orders]
            if not routed:
                break
            seed_o, _ = rng.choice(routed)
            related = sorted(
                (x for x in routed if abs(x[0].a - seed_o.a) <= 2 * 3600),
                key=lambda x: tau[seed_o.point][x[0].point] + rng.random() * 600,
            )[: rng.randint(3, 8)]
            removed_ids = {x[0].order_id for x in related}
            trial = dict(schedules)
            ok = True
            for kidx in {x[1] for x in related}:
                s = trial[kidx]
                res = decode(ctx, s.staff, [o for o in s.orders if o.order_id not in removed_ids])
                if res is None:
                    ok = False
                    break
                trial[kidx] = res
            if not ok:
                continue
            in_routes = {o.order_id for s in trial.values() for o in s.orders}
            pool = [
                o
                for o in ctx.orders
                if o.eligible and o.order_id not in in_routes and abs(o.a - seed_o.a) <= 3 * 3600
            ]
            pool.sort(key=lambda o: (-o.weight, len(o.eligible) + rng.random() * 4))
            for o in pool:
                best = None
                for kidx in o.eligible:
                    res = best_insertion(ctx, trial[kidx], o, max_tries=3)
                    if res is not None and (best is None or res[0] < best[0]):
                        best = (res[0], kidx, res[1])
                if best is not None:
                    trial[best[1]] = best[2]
            val = value(trial)
            if val > current:
                schedules.update(trial)
                current = val
                accepted += 1
        return {"iterations": iters, "accepted": accepted}

    @staticmethod
    def _balance(ctx: DayContext, schedules: dict[int, RouteSchedule], deadline: float) -> int:
        """Перенос нарядов с самых загруженных на менее загруженных, если падает
        W3·T + W4·Σ(ρ_k − ρ̄)² (ρ̄ по задействованным)."""

        def objective() -> float:
            used = [s.utilization for s in schedules.values() if s.orders]
            if not used:
                return 0.0
            mean = sum(used) / len(used)
            travel = sum(s.travel for s in schedules.values() if s.orders)
            return travel + W4_BALANCE * sum((u - mean) ** 2 for u in used)

        moves = 0
        current = objective()
        improved = True
        while improved and time.perf_counter() < deadline:
            improved = False
            order_by_util = sorted(
                (s for s in schedules.values() if s.orders), key=lambda s: -s.utilization
            )
            for src in order_by_util[: max(1, len(order_by_util) // 3)]:
                if time.perf_counter() > deadline:
                    break
                for o in list(src.orders):
                    if ctx.joint and jnt.is_joint_code(o.work_type.code):
                        continue  # пара закреплена по времени и исполнителям
                    targets = sorted(
                        (k for k in o.eligible if k != src.staff.idx),
                        key=lambda k: schedules[k].utilization,
                    )
                    rest = [x for x in src.orders if x is not o]
                    new_src = decode(ctx, src.staff, rest)
                    if new_src is None:
                        continue
                    done = False
                    for kidx in targets[:6]:
                        if schedules[kidx].utilization >= src.utilization:
                            break
                        res = best_insertion(ctx, schedules[kidx], o, max_tries=3)
                        if res is None:
                            continue
                        old_src, old_dst = schedules[src.staff.idx], schedules[kidx]
                        schedules[src.staff.idx], schedules[kidx] = new_src, res[1]
                        value = objective()
                        if value < current - 1e-6:
                            current = value
                            moves += 1
                            done = True
                            break
                        schedules[src.staff.idx], schedules[kidx] = old_src, old_dst
                    if done:
                        improved = True
                        break
                if improved:
                    break
        return moves

    @staticmethod
    def _joint_pairs(ctx: DayContext, schedules: dict[int, RouteSchedule], deadline: float) -> dict:
        """Пост-шаг совместных нарядов (docs/planner-notes.md, «Совместные наряды»).

        Для каждого размещённого INST_PRIVATE_VLS: (1) напарник к прежнему началу s — вставка
        закреплённого визита [s, s] в маршрут другого допустимого сотрудника (остальные его
        визиты могут сдвинуться в своих окнах); (2) иначе наряд снимается и ищется лучшая
        тройка (s из сетки окна, основной, напарник); (3) иначе — в unassigned с причиной.
        Освободившееся время добирается жадной вставкой обычных нарядов."""
        stats = {"pairs": 0, "moved": 0, "added": 0, "dropped": 0, "refill": 0}
        routed_kidx = {o.order_id: kidx for kidx, s in schedules.items() for o in s.orders}
        # сначала размещённые (их пару ищем у прежнего начала), затем неразмещённые с ≥ 2 допустимыми
        mains = sorted(
            ((routed_kidx.get(o.order_id), o) for o in ctx.orders
             if jnt.is_joint(o) and (o.order_id in routed_kidx or len(o.eligible) >= 2)),
            key=lambda x: (x[0] is None, x[1].a, x[1].order_id),
        )

        def try_insert(k: int, x: OrderCtx):
            res = best_insertion(ctx, schedules[k], x, max_tries=4)
            return None if res is None else (res[0], res[1])

        for kidx, o in mains:
            if kidx is None and time.perf_counter() > deadline:
                ctx.joint_fail[o.order_id] = jnt.unassigned_text(o, len(o.eligible))[1]
                continue
            sched = schedules[kidx] if kidx is not None else None
            s0 = None
            if sched is not None:
                k = sched.staff
                s0 = next((st.st for st in sched.stops if st.kind == VISIT and st.order is o), None)
            if s0 is None:
                # не размещён (или снят починкой маршрута при подборе другой пары): только поиск пары
                found = jnt.search(o, jnt.start_grid(o, ctx.now), o.eligible, try_insert)
                if found is not None:
                    _, k1, r1, _, k2, r2, _ = found
                    schedules[k1], schedules[k2] = r1, r2
                    stats["pairs"] += 1
                    stats["moved" if kidx is not None else "added"] += 1
                else:
                    ctx.joint_fail[o.order_id] = jnt.unassigned_text(o, len(o.eligible))[1]
                    if kidx is not None:
                        stats["dropped"] += 1
                continue
            # (1) напарник к прежнему началу
            p = jnt.partner_ctx(o, s0)
            best = None
            for k2 in o.eligible:
                if k2 == kidx:
                    continue
                r = try_insert(k2, p)
                if r is not None and (best is None or r[0] < best[0]):
                    best = (r[0], k2, r[1])
            if best is not None:
                m = jnt.pinned(o, s0)
                fixed = decode(ctx, k, [m if x is o else x for x in sched.orders])
                if fixed is not None:
                    schedules[kidx] = fixed
                    schedules[best[1]] = best[2]
                    stats["pairs"] += 1
                    continue
            # (2) снять и подобрать пару заново
            rest = [x for x in sched.orders if x is not o]
            base = decode(ctx, k, rest)
            if base is None:
                base, removed = PyVrpPlanner._feasible_schedule(ctx, k, rest)
            schedules[kidx] = base
            found = jnt.search(o, jnt.start_grid(o, ctx.now), o.eligible, try_insert)
            if found is not None:
                _, k1, r1, _, k2, r2, _ = found
                schedules[k1] = r1
                schedules[k2] = r2
                stats["pairs"] += 1
                stats["moved"] += 1
                continue
            # (3) пары нет
            _, text = jnt.unassigned_text(o, len(o.eligible))
            ctx.joint_fail[o.order_id] = text
            stats["dropped"] += 1

        if stats["dropped"] or stats["moved"]:
            routed = {o.order_id for s in schedules.values() for o in s.orders}
            pool = [o for o in ctx.orders if o.eligible and o.order_id not in routed and not jnt.is_joint(o)]
            before = sum(len(s.orders) for s in schedules.values())
            PyVrpPlanner._insert(ctx, schedules, pool, max(deadline, time.perf_counter() + 1.0))
            stats["refill"] = sum(len(s.orders) for s in schedules.values()) - before
        return stats

    @staticmethod
    def _assemble(ctx, day, schedules, clusters, exhausted) -> Plan:
        now = day.now
        if ctx.joint:
            main_staff = {o.order_id: s.staff.staff_id for s in schedules.values() for o in s.orders}
            ctx.joint_staff = {
                m: (main_staff[m], s.staff.staff_id)
                for s in schedules.values()
                for o in s.orders
                if (m := jnt.main_of(o.order_id)) and m in main_staff
            }
        routes = [assemble.route_model(ctx, schedules[k.idx]) for k in ctx.staff]
        routed = {o.order_id for s in schedules.values() for o in s.orders}
        unassigned = [
            assemble.classify_unassigned(ctx, o, schedules, budget_exhausted=exhausted)
            for o in ctx.orders
            if o.order_id not in routed
        ]
        node_names = {n.access_node_id: n.name for n in ctx.ref.access_nodes.values()}
        groups = assemble.pgp_groups(day.date, clusters, now, node_names)
        ids = assemble.AlertIds(day.date)
        alerts = [assemble.pgp_alert(ids, g, now) for g in groups]
        alerts += [assemble.unassigned_alert(ids, u, now) for u in unassigned]
        plan = Plan(
            date=day.date,
            plan_version=1,
            as_of=now,
            routes=routes,
            unassigned=unassigned,
            pgp_groups=groups,
            cancelled=list(day.cancelled.values()),
            alerts=alerts,
        )
        plan.summary = assemble.summary(plan, len(day.orders))
        return plan

    # ------------------------------------------------------------ events
    def apply_event(
        self,
        ref: ReferenceState,
        matrix: MatrixState,
        day: DayState,
        event: Event,
    ) -> PlanUpdate:
        from .events import apply_event

        update = apply_event(ref, matrix, day, event, traffic=self.traffic, joint=self.joint)
        self.last_event_stats = update.stats
        return update
````

## File: backend/app/planner/stub.py
````python
"""Заглушка планировщика.

Для каждого сотрудника на смене — маршрут DEPOT_START → DEPOT_END в его СЦ
(в границах смены), без визитов. Все наряды дня — в `unassigned` с OTHER.
Отменённые наряды — в `cancelled`. Реальный планировщик здесь не реализован.
"""

from __future__ import annotations

from datetime import datetime, time

from ..errors import ApiError
from ..models import (
    CancelledOrder,
    Event,
    OrderCancelledEvent,
    Plan,
    PlanSummary,
    ReasonCode,
    Route,
    RouteStatus,
    RouteTotals,
    Stop,
    StopType,
    UnassignedAction,
    UnassignedOrder,
)
from ..state import DayState, MatrixState, ReferenceState
from .base import PlanUpdate

STUB_REASON = "Заглушка планировщика: наряд ещё не распределён"


def _local_dt(day: DayState, hhmm: str) -> datetime:
    return datetime.combine(day.date, time.fromisoformat(hhmm), tzinfo=day.now.tzinfo)


def _depot_stop(seq: int, sc_id: str, at: datetime, kind: StopType) -> Stop:
    return Stop(
        seq=seq,
        type=kind,
        location_id=sc_id,
        order_id=None,
        arrival=at,
        start=at,
        end=at,
        travel_sec=0,
        distance_m=0,
        status="PLANNED",
    )


def _renumber(stops: list[Stop]) -> list[Stop]:
    for i, stop in enumerate(stops, start=1):
        stop.seq = i
    return stops


class StubPlanner:
    def build_plan(
        self, ref: ReferenceState, matrix: MatrixState, day: DayState, time_limit_sec: int | None = None
    ) -> Plan:
        routes: list[Route] = []
        for staff in ref.staff.values():
            if staff.staff_id in day.absent_staff_ids:
                continue
            start = _local_dt(day, staff.shift.start)
            end = _local_dt(day, staff.shift.end)
            routes.append(
                Route(
                    staff_id=staff.staff_id,
                    service_center_id=staff.service_center_id,
                    status=RouteStatus.ACTIVE,
                    stops=[
                        _depot_stop(1, staff.service_center_id, start, StopType.DEPOT_START),
                        _depot_stop(2, staff.service_center_id, end, StopType.DEPOT_END),
                    ],
                    totals=RouteTotals(),
                )
            )
        plan = Plan(
            date=day.date,
            plan_version=1,
            as_of=day.model_time,
            routes=routes,
            unassigned=self._unassigned(day),
            cancelled=list(day.cancelled.values()),
        )
        plan.summary = self._summary(day, plan)
        return plan

    def apply_event(
        self,
        ref: ReferenceState,
        matrix: MatrixState,
        day: DayState,
        event: Event,
    ) -> PlanUpdate:
        assert day.plan is not None, "apply_event вызван до build_plan"
        plan = day.plan.model_copy(deep=True)
        changed: list[str] = []

        if event.type == "NEW_ORDER":
            day.orders[event.order.order_id] = event.order
            day.arrived_at[event.order.order_id] = event.timestamp
        elif event.type == "ORDER_CANCELLED":
            day.cancelled[event.order_id] = _cancelled(event)
        elif event.type == "STAFF_UNAVAILABLE":
            changed.append(event.staff_id)
            self._mark_unavailable(plan, event.staff_id, event.timestamp, event.until)
        elif event.type in ("VISIT_DELAYED", "ORDER_TRANSFERRED"):
            # В заглушке визитов нет, поэтому ни один наряд не находится в маршруте.
            raise ApiError(
                422,
                "ORDER_NOT_IN_ROUTE",
                f"Наряд {event.order_id} не назначен сотруднику {event.staff_id}",
                [{"field": "order_id", "message": "наряд не в маршруте"}],
            )
        # TIME_TICK: только сдвиг модельного времени, он делается в сервисе.

        plan.unassigned = self._unassigned(day)
        plan.cancelled = list(day.cancelled.values())
        plan.summary = self._summary(day, plan)
        return PlanUpdate(plan=plan, changed_staff_ids=changed, new_alerts=[])

    # ------------------------------------------------------------ helpers

    @staticmethod
    def _unassigned(day: DayState) -> list[UnassignedOrder]:
        return [
            UnassignedOrder(
                order_id=order.order_id,
                reason_code=ReasonCode.OTHER,
                reason_text=STUB_REASON,
                suggested_action=UnassignedAction.ESCALATE,
                since=day.arrived_at.get(order.order_id, day.now),
            )
            for order in day.pending_orders()
        ]

    @staticmethod
    def _summary(day: DayState, plan: Plan) -> PlanSummary:
        return PlanSummary(
            orders_total=len(day.orders),
            visits_planned=0,
            unassigned=len(plan.unassigned),
            pgp_orders=0,
            cancelled=len(day.cancelled),
            travel_sec_total=0,
            utilization_cv=0.0,
        )

    @staticmethod
    def _mark_unavailable(plan: Plan, staff_id: str, since: datetime, until: datetime | None) -> None:
        for route in plan.routes:
            if route.staff_id != staff_id:
                continue
            route.status = RouteStatus.UNAVAILABLE
            route.unavailable_from = since
            route.unavailable_until = until
            # Остановки депо, начинающиеся в период недоступности, убираем:
            # это разрешено спецификацией (DEPOT_END не обязателен у недоступного).
            route.stops = _renumber(
                [
                    s
                    for s in route.stops
                    if not (
                        s.type in (StopType.DEPOT_START, StopType.DEPOT_END)
                        and s.start >= since
                        and (until is None or s.start < until)
                    )
                ]
            )


def _cancelled(event: OrderCancelledEvent) -> CancelledOrder:
    return CancelledOrder(
        order_id=event.order_id,
        cancelled_at=event.timestamp,
        event_id=event.event_id,
    )
````

## File: backend/app/planner/validator.py
````python
"""Независимая самопроверка плана по жёстким ограничениям H1–H10 (data/api_spec.yaml, п. 3–4).

Не использует декодер и контекст планировщика: время берётся из Stop как есть,
дорога — из единой матрицы по location_id. validate_plan — H1–H9 одной версии,
validate_transition — H10 между последовательными версиями. Допуск сравнения
времени — 1 с, как у валидатора организаторов. Это самопроверка, не часть API.

H-J — наша дополнительная проверка совместных нарядов (JOINT_ORDERS, planner/joint.py):
визит напарника `ORD-…-B` начинается одновременно с основным визитом, у другого
сотрудника, который сам проходит H1 по основному наряду; длительность — норматив
(или момент из события по основному наряду); АО у напарника не списывается. Валидатор
организаторов про такие визиты не знает (см. docs/planner-notes.md, «Совместные наряды»).
"""

from __future__ import annotations

import datetime as dt
from dataclasses import dataclass

from ..models import Plan, StopType
from .joint import is_joint_code, joint_enabled, main_of
from ..state import DayState, MatrixState, ReferenceState

TOL = 1  # с
TZ = dt.timezone(dt.timedelta(hours=5))


@dataclass(frozen=True)
class Violation:
    code: str  # H1..H9
    message: str
    order_id: str | None = None
    staff_id: str | None = None

    def __str__(self) -> str:
        who = " ".join(x for x in (self.staff_id, self.order_id) if x)
        return f"{self.code} [{who}] {self.message}"


def _sec(day: dt.date, moment: dt.datetime) -> float:
    return (moment - dt.datetime.combine(day, dt.time(0), tzinfo=TZ)).total_seconds()


def _hhmm(value: str) -> int:
    h, m = value.split(":")
    return int(h) * 3600 + int(m) * 60


def validate_plan(
    plan: Plan,
    ref: ReferenceState,
    matrix: MatrixState,
    day: DayState,
    joint: bool | None = None,
) -> list[Violation]:
    """joint — режим совместных нарядов (None — из JOINT_ORDERS): при включённом каждый
    основной визит INST_PRIVATE_VLS должен иметь напарника (H-J)."""
    if joint is None:
        joint = joint_enabled()
    out: list[Violation] = []
    add = out.append
    idx = matrix.index
    main_visits: dict[str, list[tuple[str, object]]] = {}
    partner_visits: dict[str, list[tuple[str, object]]] = {}
    dur = matrix.matrix.durations_sec

    def tau(a: str, b: str) -> int | None:
        if a not in idx or b not in idx:
            return None
        return dur[idx[a]][idx[b]]

    on_shift = [s for s in ref.staff if s not in day.absent_staff_ids]
    seen_routes: dict[str, int] = {}
    visit_count: dict[str, int] = {}

    for route in plan.routes:
        sid = route.staff_id
        seen_routes[sid] = seen_routes.get(sid, 0) + 1
        staff = ref.staff.get(sid)
        if staff is None or sid in day.absent_staff_ids:
            add(Violation("H2", "маршрут сотрудника не на смене", staff_id=sid))
            continue
        stops = route.stops
        if not stops:
            continue
        S, F = _hhmm(staff.shift.start), _hhmm(staff.shift.end)
        bf, bt = _hhmm(staff.shift.break_window.from_), _hhmm(staff.shift.break_window.to)
        beta = staff.shift.break_duration_min * 60
        cap = staff.equipment_capacity
        inv = [staff.equipment_start.ONT, staff.equipment_start.ROUTER, staff.equipment_start.STB]

        for i, s in enumerate(stops, start=1):
            if s.seq != i:
                add(Violation("H6", f"seq {s.seq} вместо {i}", s.order_id, sid))
                break

        # --- структура и H8 для депо
        if stops[0].type != StopType.DEPOT_START:
            add(Violation("H8", "первая остановка не DEPOT_START", staff_id=sid))
        if stops[-1].type != StopType.DEPOT_END and route.status == "ACTIVE":
            add(Violation("H8", "последняя остановка не DEPOT_END", staff_id=sid))
        for s in stops:
            if s.type in (StopType.DEPOT_START, StopType.DEPOT_END):
                if s.location_id != staff.service_center_id:
                    add(Violation("H8", f"{s.type.value} не в своём СЦ ({s.location_id})", staff_id=sid))
                if not (s.arrival == s.start == s.end):
                    add(Violation("H6", f"{s.type.value}: arrival, start, end должны совпадать", staff_id=sid))
            if s.type == StopType.DEPOT_START and s is not stops[0]:
                add(Violation("H8", "DEPOT_START не первой остановкой", staff_id=sid))
            if s.type == StopType.DEPOT_END and s is not stops[-1]:
                add(Violation("H8", "DEPOT_END не последней остановкой", staff_id=sid))

        # --- H2
        first, last = stops[0], stops[-1]
        if first.type == StopType.DEPOT_START and _sec(day.date, first.start) < S - TOL:
            add(Violation("H2", "выезд раньше начала смены", staff_id=sid))
        if last.type == StopType.DEPOT_END and _sec(day.date, last.arrival) > F + TOL:
            add(Violation("H2", "возвращение позже конца смены", staff_id=sid))
        for s in stops:
            a, st, en = _sec(day.date, s.arrival), _sec(day.date, s.start), _sec(day.date, s.end)
            if a < S - TOL or en > F + TOL:
                add(Violation("H2", f"{s.type.value} вне смены", s.order_id, sid))

        # --- H6 (дорога, порядок времён)
        for j, s in enumerate(stops):
            a, st, en = _sec(day.date, s.arrival), _sec(day.date, s.start), _sec(day.date, s.end)
            if st < a - TOL or en < st - TOL:
                add(Violation("H6", f"{s.type.value}: нарушен порядок arrival ≤ start ≤ end", s.order_id, sid))
            if j == 0:
                continue
            prev = stops[j - 1]
            t = tau(prev.location_id, s.location_id)
            if t is None:
                add(Violation("H6", f"точка {s.location_id} или {prev.location_id} не в матрице", s.order_id, sid))
                continue
            if s.type == StopType.BREAK:
                if s.location_id != prev.location_id:
                    add(Violation("H3", "BREAK не на месте предыдущей остановки", staff_id=sid))
            if a < _sec(day.date, prev.end) + t - TOL:
                add(Violation("H6", f"{s.type.value}: прибытие раньше, чем позволяет матрица", s.order_id, sid))

        # --- H3
        visits = [s for s in stops if s.type == StopType.VISIT]
        breaks = [s for s in stops if s.type == StopType.BREAK]
        if visits:
            # Допущение этапа B: у выбывшего перерыв не обязателен, если остаток окна перерыва
            # после выбытия целиком попал в недоступность (until = null или until > to).
            exempt = (
                route.status == "UNAVAILABLE"
                and (route.unavailable_until is None or _sec(day.date, route.unavailable_until) > bt)
            )
            if len(breaks) > 1 or (len(breaks) == 0 and not exempt):
                add(Violation("H3", f"перерывов {len(breaks)} вместо 1", staff_id=sid))
            for b in breaks:
                st, en = _sec(day.date, b.start), _sec(day.date, b.end)
                if en - st < beta - TOL:
                    add(Violation("H3", "перерыв короче нормы", staff_id=sid))
                if st < bf - TOL or st > bt + TOL:
                    add(Violation("H3", "начало перерыва вне break_window", staff_id=sid))

        # --- H1, H4, H5, H7, H8 (RESTOCK)
        for s in stops:
            if s.type == StopType.VISIT:
                oid = s.order_id
                order = day.orders.get(oid) if oid else None
                main = main_of(oid, day.orders) if order is None else None
                if main is not None:
                    # визит напарника: проверяется в H-J, АО не списывается
                    partner_visits.setdefault(main, []).append((sid, s))
                    continue
                if order is not None and is_joint_code(order.work_type_code):
                    main_visits.setdefault(oid, []).append((sid, s))
                if order is None:
                    add(Violation("H9", "визит по неизвестному наряду", oid, sid))
                    continue
                visit_count[oid] = visit_count.get(oid, 0) + 1
                wt = ref.work_types[order.work_type_code]
                if s.location_id != order.building_id:
                    add(Violation("H6", "location_id визита не совпадает с building_id", oid, sid))
                if not set(staff.profiles) & set(wt.profiles):
                    add(Violation("H1", "нет допустимого профиля", oid, sid))
                if order.technology not in staff.technologies:
                    add(Violation("H1", f"нет технологии {order.technology}", oid, sid))
                if order.requires_height and not staff.height_permit:
                    add(Violation("H1", "нет допуска к высоте", oid, sid))
                st = _sec(day.date, s.start)
                if st < _sec(day.date, order.slot.start) - TOL or st > _sec(day.date, order.latest_start) + TOL:
                    add(Violation("H4", "начало визита вне [slot.start, latest_start]", oid, sid))
                override = getattr(day, "visit_end_overrides", {}).get(oid)
                if override is not None:
                    if abs((s.end - override).total_seconds()) > TOL:
                        add(Violation("H5", "конец визита не равен моменту из события", oid, sid))
                elif abs((s.end - s.start).total_seconds() - wt.duration_min * 60) > TOL:
                    add(Violation("H5", "длительность визита не равна нормативу", oid, sid))
                req = order.required_equipment
                inv = [inv[0] - req.ONT, inv[1] - req.ROUTER, inv[2] - req.STB]
                if min(inv) < 0:
                    add(Violation("H7", f"остаток АО отрицательный: {inv}", oid, sid))
            elif s.type == StopType.RESTOCK:
                sc = ref.service_centers.get(s.location_id)
                if sc is None:
                    add(Violation("H8", f"RESTOCK не в СЦ ({s.location_id})", staff_id=sid))
                    continue
                st, en = _sec(day.date, s.start), _sec(day.date, s.end)
                if st < _hhmm(sc.opening_hours.open) - TOL or en > _hhmm(sc.opening_hours.close) + TOL:
                    add(Violation("H8", f"RESTOCK в {sc.service_center_id} вне режима работы", staff_id=sid))
                if en - st < sc.restock_duration_min * 60 - TOL:
                    add(Violation("H8", "RESTOCK короче restock_duration_min", staff_id=sid))
                inv = [cap.ONT, cap.ROUTER, cap.STB]
            elif s.order_id:
                add(Violation("H6", f"order_id у остановки {s.type.value}", s.order_id, sid))

    _check_joint(main_visits, partner_visits, ref, day, joint, add)

    for sid in on_shift:
        if seen_routes.get(sid, 0) != 1:
            add(Violation("H2", f"маршрутов у сотрудника на смене: {seen_routes.get(sid, 0)}", staff_id=sid))

    # --- H9: каждый наряд ровно в одном месте
    places: dict[str, list[str]] = {}
    for oid, n in visit_count.items():
        places.setdefault(oid, []).extend(["route"] * n)
    for u in plan.unassigned:
        places.setdefault(u.order_id, []).append("unassigned")
    for g in plan.pgp_groups:
        for oid in g.order_ids:
            places.setdefault(oid, []).append("pgp")
    for c in plan.cancelled:
        places.setdefault(c.order_id, []).append("cancelled")
    for oid in day.orders:
        where = places.get(oid, [])
        if len(where) != 1:
            add(Violation("H9", f"наряд учтён {len(where)} раз: {where}", oid))
    for oid in places:
        if oid not in day.orders:
            add(Violation("H9", "в плане неизвестный наряд", oid))
    return out


def _check_joint(main_visits, partner_visits, ref: ReferenceState, day: DayState, joint: bool, add) -> None:
    """H-J: пары «основной визит — визит напарника»."""
    overrides = getattr(day, "visit_end_overrides", {})
    for main, partners in partner_visits.items():
        order = day.orders[main]
        wt = ref.work_types[order.work_type_code]
        mains = main_visits.get(main, [])
        if not is_joint_code(order.work_type_code):
            add(Violation("H-J", "напарник у наряда, который не выполняется совместно", main))
        if len(partners) != 1:
            add(Violation("H-J", f"визитов напарника {len(partners)} вместо 1", main))
        if len(mains) != 1:
            add(Violation("H-J", "визит напарника без основного визита", main, partners[0][0]))
            continue
        msid, ms = mains[0]
        for psid, ps in partners:
            if psid == msid:
                add(Violation("H-J", "напарник — тот же сотрудник", main, psid))
            staff = ref.staff.get(psid)
            if staff is None:
                continue
            if not set(staff.profiles) & set(wt.profiles) or order.technology not in staff.technologies:
                add(Violation("H-J", "напарник не проходит H1 по профилю/технологии", main, psid))
            if order.requires_height and not staff.height_permit:
                add(Violation("H-J", "у напарника нет допуска к высоте", main, psid))
            if ps.location_id != order.building_id:
                add(Violation("H-J", "напарник не по адресу наряда", main, psid))
            if abs((ps.start - ms.start).total_seconds()) > TOL:
                add(Violation("H-J", "начало визита напарника не совпадает с основным", main, psid))
            pid = ps.order_id
            override = overrides.get(pid)
            if override is not None:
                if abs((ps.end - override).total_seconds()) > TOL:
                    add(Violation("H-J", "конец визита напарника не равен моменту из события", main, psid))
            elif abs((ps.end - ps.start).total_seconds() - wt.duration_min * 60) > TOL:
                add(Violation("H-J", "длительность визита напарника не равна нормативу", main, psid))
    if joint:
        for main, mains in main_visits.items():
            if main not in partner_visits:
                add(Violation("H-J", "совместный наряд без напарника", main, mains[0][0]))


# ---------------------------------------------------------------- H10

def _key(stop) -> tuple:
    if stop.type == StopType.VISIT:
        return ("VISIT", stop.order_id)
    if stop.type == StopType.RESTOCK:
        return ("RESTOCK", stop.location_id)
    return (stop.type.value,)


def validate_transition(
    prev: Plan,
    curr: Plan,
    ref: ReferenceState,
    matrix: MatrixState,
    day: DayState,
    t: dt.datetime,
    event=None,
) -> list[Violation]:
    """H10 между версиями prev -> curr в модельный момент t (раздел 3 спецификации).

    * остановка prev со start < t — та же в curr (сотрудник, позиция, тип, наряд, location_id,
      arrival, start); end меняется только событием VISIT_DELAYED / ORDER_TRANSFERRED по ней;
    * незамороженная остановка curr начинается не раньше t;
    * новая остановка или остановка с изменившимся предшественником:
      arrival ≥ max(end предшественника, t) + время в пути;
    * визит по наряду из NEW_ORDER начинается не раньше момента его поступления;
    * отменённый наряд не посещается; у выбывшего нет остановок, начинающихся в [from, until).
    """
    out: list[Violation] = []
    add = out.append
    idx = matrix.index
    dur = matrix.matrix.durations_sec
    D = day.date
    tt = _sec(D, t)
    end_events: dict[str, dt.datetime] = {}
    if event is not None and getattr(event, "type", None) in ("VISIT_DELAYED", "ORDER_TRANSFERRED"):
        end_events[event.order_id] = event.expected_end if event.type == "VISIT_DELAYED" else event.actual_end
        # визит напарника совместного наряда заканчивается вместе с основным (planner/joint.py)
        end_events[event.order_id + "-B"] = end_events[event.order_id]

    prev_routes = {r.staff_id: r for r in prev.routes}
    curr_routes = {r.staff_id: r for r in curr.routes}
    for sid, pr in prev_routes.items():
        frozen = [s for s in pr.stops if _sec(D, s.start) < tt - TOL]
        if not frozen:
            continue
        cr = curr_routes.get(sid)
        if cr is None or len(cr.stops) < len(frozen):
            add(Violation("H10", "начатые остановки пропали из маршрута", staff_id=sid))
            continue
        for a, b in zip(frozen, cr.stops):
            same = (a.type == b.type and a.order_id == b.order_id and a.location_id == b.location_id
                    and abs((a.arrival - b.arrival).total_seconds()) <= TOL
                    and abs((a.start - b.start).total_seconds()) <= TOL)
            if not same:
                add(Violation("H10", f"изменена начатая остановка {a.type.value}", a.order_id, sid))
                continue
            if abs((a.end - b.end).total_seconds()) > TOL:
                moment = end_events.get(a.order_id) if a.type == StopType.VISIT else None
                if moment is None or abs((b.end - moment).total_seconds()) > TOL:
                    add(Violation("H10", "end начатой остановки изменён без события", a.order_id, sid))

    for sid, cr in curr_routes.items():
        pr = prev_routes.get(sid)
        prev_pred: dict[tuple, tuple | None] = {}
        n_frozen = 0
        if pr is not None:
            for j, s in enumerate(pr.stops):
                prev_pred[_key(s)] = _key(pr.stops[j - 1]) if j else None
            n_frozen = sum(1 for s in pr.stops if _sec(D, s.start) < tt - TOL)
        for j, s in enumerate(cr.stops):
            if j < n_frozen:
                continue
            st = _sec(D, s.start)
            if st < tt - TOL:
                add(Violation("H10", f"{s.type.value} начинается раньше модельного времени", s.order_id, sid))
            k = _key(s)
            pred = cr.stops[j - 1] if j else None
            pk = _key(pred) if pred is not None else None
            if k in prev_pred and prev_pred[k] == pk:
                continue  # прежняя остановка с прежним предшественником
            arr = _sec(D, s.arrival)
            if pred is None:
                if arr < tt - TOL:
                    add(Violation("H10", f"новая {s.type.value}: прибытие раньше t", s.order_id, sid))
                continue
            if pred.location_id in idx and s.location_id in idx:
                tr = dur[idx[pred.location_id]][idx[s.location_id]]
                if arr < max(_sec(D, pred.end), tt) + tr - TOL:
                    add(Violation("H10", f"{s.type.value}: прибытие раньше max(end предшественника, t) + дорога",
                                  s.order_id, sid))

    cancelled = {c.order_id for c in curr.cancelled}
    for cr in curr.routes:
        for s in cr.stops:
            if s.type != StopType.VISIT:
                continue
            if s.order_id in cancelled:
                add(Violation("H10", "визит по отменённому наряду", s.order_id, cr.staff_id))
            came = day.arrived_at.get(s.order_id)
            if came is not None and came > day.now and s.start < came - dt.timedelta(seconds=TOL):
                add(Violation("H10", "визит по NEW_ORDER раньше его поступления", s.order_id, cr.staff_id))
        if cr.status == "UNAVAILABLE" and cr.unavailable_from is not None:
            lo = _sec(D, cr.unavailable_from)
            hi = _sec(D, cr.unavailable_until) if cr.unavailable_until is not None else float("inf")
            for s in cr.stops:
                st = _sec(D, s.start)
                if lo <= st < hi - TOL:
                    add(Violation("H10", f"{s.type.value} в период недоступности", s.order_id, cr.staff_id))
    if event is not None and getattr(event, "type", None) == "STAFF_UNAVAILABLE":
        r = curr_routes.get(event.staff_id)
        if r is not None and r.status != "UNAVAILABLE":
            add(Violation("H10", "выбывший сотрудник не помечен UNAVAILABLE", staff_id=event.staff_id))
    return out
````

## File: backend/app/planner/vrp_model.py
````python
"""Отображение дня на PyVRP 0.14 (docs/formalization.md, раздел 4; проверки — docs/planner-notes.md).

* Наряд -> `Client` со своей `Location` (нужно для запрета дуг по профилям H1):
  окно [slot.start, latest_start], service = норматив, delivery = АО, prize, required=False.
* Сотрудник -> `VehicleType(num_available=1)`: старт/финиш в своём СЦ, окно смены,
  capacity = equipment_capacity, initial_load = capacity - equipment_start,
  reload_depots = все СЦ (отдельные объекты Depot с окном [open, close - restock] и
  service_duration = restock), max_reloads.
* H1 -> профили: сотрудники с одинаковым множеством допустимых нарядов делят профиль,
  дуги в недопустимые наряды получают запретное значение.
* Перерыв в PyVRP точно не выражается, его ставит декодер. PLANNER_BREAK_MODE:
  `shift_duration` (по умолчанию) — длительность маршрута ≤ F − S − β;
  `shorten` — tw_late = F − β; `client` — обязательный клиент-перерыв своего
  сотрудника (по замерам хуже, см. docs/planner-notes.md); `none` — без учёта.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

import numpy as np
from pyvrp import Client, Depot, Location, ProblemData, VehicleType
from pyvrp import solve as pyvrp_solve
from pyvrp.stop import MaxRuntime

from .context import DayContext, OrderCtx

FORBIDDEN = 10**8


@dataclass
class Weights:
    """Веса целевой функции в секундах пути (docs/formalization.md, раздел 3)."""

    w3: int
    w2: int
    w1: int

    @classmethod
    def from_ctx(cls, ctx: DayContext) -> "Weights":
        tau_max = max(ctx.tau_max, 60)
        w2 = 3 * tau_max
        return cls(w3=1, w2=w2, w1=5 * w2)


def break_mode() -> str:
    return os.environ.get("PLANNER_BREAK_MODE", "shift_duration")


def max_reloads() -> int:
    return int(os.environ.get("PLANNER_MAX_RELOADS", "2"))


def build_problem(ctx: DayContext, orders: list[OrderCtx], weights: Weights) -> tuple[ProblemData, list[int]]:
    """ProblemData и список индексов StaffCtx по типам ТС."""
    n_sc = len(ctx.scs)
    locations = [Location(0.0, 0.0, name=sc.sc_id) for sc in ctx.scs]
    loc_points = [sc.point for sc in ctx.scs]
    for o in orders:
        locations.append(Location(0.0, 0.0, name=o.order_id))
        loc_points.append(o.point)

    depots = [Depot(i, tw_early=0, tw_late=48 * 3600, service_duration=0, name=f"{sc.sc_id}") for i, sc in enumerate(ctx.scs)]
    for i, sc in enumerate(ctx.scs):
        depots.append(
            Depot(
                i,
                tw_early=sc.open,
                tw_late=max(sc.open, sc.close - sc.rho),
                service_duration=sc.rho,
                name=f"{sc.sc_id}-restock",
            )
        )
    reload_ids = list(range(n_sc, 2 * n_sc))
    sc_pos = {sc.sc_id: i for i, sc in enumerate(ctx.scs)}

    clients = [
        Client(
            n_sc + j,
            delivery=list(o.dem),
            service_duration=o.p,
            tw_early=max(o.a, ctx.now),
            tw_late=o.b,
            prize=weights.w1 * o.weight,
            required=False,
            name=o.order_id,
        )
        for j, o in enumerate(orders)
    ]

    mode = break_mode()
    n_orders = len(orders)
    n_real = n_sc + n_orders
    # Режим "client": перерыв — обязательный клиент своего сотрудника с окном
    # [break_window.from, to] и service = break_duration. Дуга в него 0, из него в Y —
    # минимальное входящее время в Y (нижняя оценка дуги prev→Y). Позицию перерыва
    # декодер выбирает заново; здесь он только резервирует время внутри окна.
    break_client: dict[int, int] = {}
    if mode == "client":
        for k in ctx.staff:
            if k.beta <= 0:
                continue
            break_client[k.idx] = len(clients)
            locations.append(Location(0.0, 0.0, name=f"break-{k.staff_id}"))
            clients.append(
                Client(
                    n_real + len(break_client) - 1,
                    delivery=[0, 0, 0],
                    service_duration=k.beta,
                    tw_early=k.bf,
                    tw_late=k.bt,
                    required=True,
                    name=f"break-{k.staff_id}",
                )
            )
    n_loc = len(locations)

    # ---- профили по множеству допустимых нарядов (и своему перерыву)
    tmp: dict[int, set[int]] = {k.idx: set() for k in ctx.staff}
    for j, o in enumerate(orders):
        for kidx in o.eligible:
            tmp[kidx].add(j)
    allowed_by_staff: dict[int, tuple] = {
        k: (frozenset(v), break_client.get(k)) for k, v in tmp.items()
    }
    profile_of: dict[tuple, int] = {}
    for k in ctx.staff:
        profile_of.setdefault(allowed_by_staff[k.idx], len(profile_of))

    idx = np.asarray(loc_points, dtype=np.int64)
    base_dur = np.zeros((n_loc, n_loc), dtype=np.int64)
    base_dur[:n_real, :n_real] = ctx.dur_np[np.ix_(idx, idx)]
    if break_client:
        real = base_dur[:n_real, :n_real].astype(np.float64)
        np.fill_diagonal(real, np.inf)
        min_in = real.min(axis=0)
        min_in[~np.isfinite(min_in)] = 0
        base_dur[n_real:, :n_real] = min_in.astype(np.int64)[None, :]
        base_dur[:, n_real:] = 0
    break_cols = list(range(n_real, n_loc))
    dist_mats, dur_mats = [], []
    for (allowed, own_break), _ in sorted(profile_of.items(), key=lambda kv: kv[1]):
        dur = base_dur.copy()
        banned = [n_sc + j for j in range(n_orders) if j not in allowed]
        banned += [c for c in break_cols if own_break is None or c != n_sc + own_break]
        if banned:
            dur[:, banned] = FORBIDDEN
            dur[banned, banned] = 0
        dist_mats.append(dur.copy())
        dur_mats.append(dur)

    vehicle_types = []
    staff_of_type: list[int] = []
    for k in ctx.staff:
        start = max(k.start, ctx.now)
        tw_late = k.end
        shift_duration = np.iinfo(np.int64).max
        if mode == "shorten":
            tw_late = k.end - k.beta
        elif mode == "shift_duration":
            shift_duration = max(0, k.end - k.start - k.beta)
        vehicle_types.append(
            VehicleType(
                num_available=1,
                capacity=list(k.cap),
                start_depot=sc_pos[k.sc.sc_id],
                end_depot=sc_pos[k.sc.sc_id],
                tw_early=start,
                tw_late=max(start, tw_late),
                shift_duration=int(shift_duration),
                unit_distance_cost=1,
                profile=profile_of[allowed_by_staff[k.idx]],
                initial_load=[c - q for c, q in zip(k.cap, k.q0)],
                reload_depots=reload_ids,
                max_reloads=max_reloads(),
                name=k.staff_id,
            )
        )
        staff_of_type.append(k.idx)

    data = ProblemData(
        locations=locations,
        clients=clients,
        depots=depots,
        vehicle_types=vehicle_types,
        distance_matrices=dist_mats,
        duration_matrices=dur_mats,
    )
    return data, staff_of_type


def solve_sequences(
    ctx: DayContext,
    orders: list[OrderCtx],
    weights: Weights,
    runtime_sec: float,
    seed: int = 1,
) -> tuple[dict[int, list[OrderCtx]], dict]:
    """Решает задачу PyVRP и возвращает порядок визитов по сотрудникам (индексы StaffCtx)."""
    if not orders or not ctx.staff:
        return {}, {"feasible": True, "iterations": 0}
    data, staff_of_type = build_problem(ctx, orders, weights)
    result = pyvrp_solve(data, stop=MaxRuntime(max(0.1, runtime_sec)), seed=seed, display=False)
    best = result.best
    seqs: dict[int, list[OrderCtx]] = {}
    for route in best.routes():
        kidx = staff_of_type[route.vehicle_type()]
        # в schedule() у активности клиента idx — номер клиента (0..num_clients-1)
        # (клиенты с номером ≥ len(orders) — перерывы режима "client")
        seqs[kidx] = [
            orders[act.idx] for act in route.schedule() if act.is_client() and act.idx < len(orders)
        ]
    info = {
        "feasible": best.is_feasible(),
        "iterations": result.num_iterations,
        "cost": result.cost(),
        "runtime": result.runtime,
    }
    return seqs, info
````

## File: backend/app/__init__.py
````python

````

## File: backend/app/employee_api.py
````python
"""Мобильный экран и API сотрудника: /ui/employee (вне контракта /api/v1, наше расширение).

Маршрут сотрудника на день из текущей версии плана и отметки по визитам. Отметки хранятся
в памяти процесса под той же блокировкой, что и остальное состояние (сбрасываются при
перезагрузке справочников или дня). Часть отметок порождает события контракта через
`svc.post_event` (единственный путь изменения плана):

| отметка            | событие контракта                                                    |
|--------------------|----------------------------------------------------------------------|
| CALLED             | нет (только запись)                                                  |
| ARRIVED            | VISIT_DELAYED, если прибыл позже планового начала на >= 5 мин        |
| DONE (COMPLETED)   | VISIT_DELAYED, если завершил позже планового конца; раньше — без события |
| TRANSFER           | ORDER_TRANSFERRED (actual_end = время отметки)                       |
| UNAVAILABLE        | STAFF_UNAVAILABLE                                                    |

Время отметки берётся из тела (`timestamp`, модельное время), иначе — текущее модельное время дня.
"""

from __future__ import annotations

import datetime as dt
import json
from typing import Literal, Optional

from fastapi import APIRouter, Query, Request
from pydantic import AwareDatetime, BaseModel, ConfigDict, Field
from starlette.concurrency import run_in_threadpool

from .errors import ApiError
from .service import _parse
from .staff_directory import display_name

router = APIRouter(prefix="/ui/employee", tags=["ui-employee"])

KINDS = ("CALLED", "ARRIVED", "DONE")
ALL_KINDS = KINDS + ("TRANSFER", "UNAVAILABLE")
# Правило ТЗ: позвонить абоненту за 30–60 минут до начала слота.
CALL_BEFORE_MAX_MIN = 60
CALL_BEFORE_MIN_MIN = 30
# Прибытие позже планового начала на столько минут и больше → VISIT_DELAYED.
LATE_ARRIVAL_THRESHOLD_MIN = 5
STAFF_MARK_PREFIX = "@staff:"  # ключ отметок сотрудника (не наряда) в хранилище отметок

_ERRORS = {
    400: {"description": "Некорректное тело или дата (BAD_REQUEST)"},
    404: {"description": "Сотрудник не найден (STAFF_NOT_FOUND) или день не загружен (DAY_NOT_FOUND)"},
    409: {"description": "Справочники или план не готовы (REFERENCE_NOT_LOADED, PLAN_NOT_BUILT)"},
    422: {"description": "Отметка невозможна: ORDER_NOT_IN_ROUTE, MARK_OUT_OF_ORDER, VISIT_NOT_STARTED, "
                         "либо событие контракта отклонено (EVENT_CONFLICTS_PLAN, EVENT_OUT_OF_ORDER, "
                         "INVALID_PERIOD, STAFF_NOT_ON_SHIFT, ...) — код события сохраняется"},
}


class MarkIn(BaseModel):
    """Отметка сотрудника. `kind`: CALLED | ARRIVED | DONE (синоним COMPLETED) | TRANSFER | UNAVAILABLE."""

    model_config = ConfigDict(json_schema_extra={"examples": [
        {"order_id": "ORD-1", "kind": "CALLED"},
        {"order_id": "ORD-1", "kind": "ARRIVED", "timestamp": "2026-10-08T10:12:00+05:00"},
        {"order_id": "ORD-1", "kind": "COMPLETED", "timestamp": "2026-10-08T11:20:00+05:00"},
        {"order_id": "ORD-1", "kind": "TRANSFER", "reason": "TRUNK_CABLE", "timestamp": "2026-10-08T10:40:00+05:00"},
        {"kind": "UNAVAILABLE", "reason": "VEHICLE_BREAKDOWN", "timestamp": "2026-10-08T12:00:00+05:00",
         "until": "2026-10-08T14:00:00+05:00"},
    ]})

    kind: Literal["CALLED", "ARRIVED", "DONE", "COMPLETED", "TRANSFER", "UNAVAILABLE"]
    order_id: Optional[str] = Field(None, description="Наряд; обязателен для всех видов, кроме UNAVAILABLE")
    timestamp: Optional[AwareDatetime] = Field(
        None, description="Время отметки (ISO 8601 с поясом, модельное время). По умолчанию — текущее модельное время дня")
    at: Optional[AwareDatetime] = Field(None, description="Устаревший синоним `timestamp`")
    reason: Optional[str] = Field(
        None, description="TRANSFER: STATION_EQUIPMENT | TRUNK_CABLE; UNAVAILABLE: VEHICLE_BREAKDOWN | ILLNESS | OTHER")
    until: Optional[AwareDatetime] = Field(
        None, description="UNAVAILABLE: до какого времени недоступен; не задано — до конца смены")


class MarkEventInfo(BaseModel):
    event_id: str
    type: str
    accepted: bool
    duplicate: bool
    message: Optional[str] = None


class MarkOut(BaseModel):
    """Результат отметки. `event_sent` — было ли отправлено событие контракта в план."""

    accepted: bool = Field(description="Отметка принята (иначе вернётся ошибка 4xx)")
    duplicate: bool = Field(description="Такая отметка уже была: состояние и план не изменены")
    kind: str
    order_id: Optional[str] = None
    staff_id: str
    at: str
    recorded_at: str
    event_sent: bool
    event: Optional[MarkEventInfo] = Field(None, description="Событие контракта (или ранее отправленное, при duplicate)")
    event_note: Optional[str] = Field(None, description="Почему событие не отправлялось")
    plan_version: int
    route_changed: bool = Field(description="Маршрут сотрудника изменился: перечитайте GET /ui/employee/{id}/route")
    added_order_ids: list[str] = []
    removed_order_ids: list[str] = []
    changed_staff_ids: list[str] = Field([], description="Сотрудники, чьи маршруты пересчитаны событием")
    marks: dict[str, Optional[str]]
    next_mark: Optional[str] = None
    allowed_actions: list[str] = []


# ---------------------------------------------------------------- helpers

def _svc(request: Request):
    return request.app.state.service


def _iso(value) -> str | None:
    return value.isoformat() if value is not None else None


def _resolve_date(request: Request, date: str | None) -> dt.date:
    """Дата из параметра, иначе день демо, иначе последний день с планом."""
    if date:
        try:
            return dt.date.fromisoformat(date)
        except ValueError:
            raise ApiError(400, "BAD_REQUEST", f"Некорректная дата '{date}', ожидается YYYY-MM-DD",
                           [{"field": "date", "message": "ожидается YYYY-MM-DD"}]) from None
    demo = getattr(request.app.state, "ui_demo", None)
    if demo:
        return dt.date.fromisoformat(demo["date"])
    svc = _svc(request)
    with svc.store.lock:
        days = sorted(d for d, s in svc.store.days.items() if s.plan is not None)
    if not days:
        raise ApiError(409, "PLAN_NOT_BUILT", "План дня ещё не построен: откройте экран диспетчера или GET /ui/demo/load")
    return days[-1]


def _day_marks(request: Request, svc, day) -> dict[str, dict[str, dict]]:
    """Отметки дня: order_id -> kind -> {at, recorded_at, staff_id}. Вызывать под lock."""
    box = getattr(request.app.state, "employee_marks", None)
    ref = svc.store.reference
    if box is None or box["ref"] is not ref:
        box = {"ref": ref, "days": {}}
        request.app.state.employee_marks = box
    key = (day.date, day.now)
    return box["days"].setdefault(key, {})


def _snapshot(request: Request, date: dt.date):
    """Согласованный срез под lock: справочники, день (копия плана), отметки дня."""
    svc = _svc(request)
    with svc.store.lock:
        ref = svc.store.reference
        if ref is None:
            raise ApiError(409, "REFERENCE_NOT_LOADED", "Справочники не загружены: GET /ui/demo/load")
        day = svc._day(date)
        if day.plan is None:
            raise ApiError(409, "PLAN_NOT_BUILT", f"План на {date} ещё не построен")
        plan = day.plan.model_copy(deep=True)
        orders = dict(day.orders)
        marks = {oid: dict(v) for oid, v in _day_marks(request, svc, day).items()}
    return ref, day, plan, orders, marks


def _route(plan, staff_id: str):
    return next((r for r in plan.routes if r.staff_id == staff_id), None)


def _staff_or_404(ref, staff_id: str):
    staff = ref.staff.get(staff_id)
    if staff is None:
        raise ApiError(404, "STAFF_NOT_FOUND", f"Сотрудник {staff_id} не найден")
    return staff


def _sc_brief(ref, sc_id: str) -> dict | None:
    sc = ref.service_centers.get(sc_id)
    if sc is None:
        return None
    return {"service_center_id": sc.service_center_id, "name": sc.name, "address": sc.address,
            "lat": sc.lat, "lon": sc.lon}


def _location(ref, location_id: str, order) -> dict | None:
    if order is not None:
        return {"address": order.address, "lat": order.lat, "lon": order.lon}
    sc = ref.service_centers.get(location_id)
    if sc is not None:
        return {"address": sc.address, "lat": sc.lat, "lon": sc.lon, "name": sc.name}
    b = ref.buildings.get(location_id)
    if b is not None:
        return {"address": b.address, "lat": b.lat, "lon": b.lon}
    return None


def _stop_state(stop, order_marks: dict, as_of: dt.datetime) -> str:
    """DONE | IN_PROGRESS | PLANNED: отметки сотрудника важнее модельного статуса плана."""
    if stop.type == "VISIT":
        if "DONE" in order_marks or "TRANSFER" in order_marks:
            return "DONE"
        if "ARRIVED" in order_marks:
            return "IN_PROGRESS"
    if stop.status is not None:
        return str(stop.status)
    if stop.end <= as_of:
        return "DONE"
    if stop.start <= as_of:
        return "IN_PROGRESS"
    return "PLANNED"


def _order_dump(ref, order) -> dict:
    wt = ref.work_types.get(order.work_type_code)
    return {
        "order_id": order.order_id,
        "work_type_code": order.work_type_code,
        "work_type_name": wt.name if wt else order.work_type_code,
        "technology": order.technology,
        "address": order.address,
        "lat": order.lat,
        "lon": order.lon,
        "building_type": order.building_type,
        "requires_height": order.requires_height,
        "slot": order.slot.model_dump(mode="json"),
        "latest_start": _iso(order.latest_start),
        "required_equipment": order.required_equipment.model_dump(),
        "is_repeat": order.is_repeat,
        # В данных организаторов телефона абонента нет; поле зарезервировано для клиента.
        "customer_phone": getattr(order, "customer_phone", None),
    }


def _call_window(order) -> dict:
    start = order.slot.start - dt.timedelta(minutes=CALL_BEFORE_MAX_MIN)
    end = order.slot.start - dt.timedelta(minutes=CALL_BEFORE_MIN_MIN)
    return {"start": _iso(start), "end": _iso(end)}


def _marks_view(order_marks: dict) -> dict:
    return {k: (order_marks[k]["at"] if k in order_marks else None) for k in KINDS + ("TRANSFER",)}


def _next_kind(order_marks: dict) -> str | None:
    if "TRANSFER" in order_marks:
        return None
    return next((k for k in KINDS if k not in order_marks), None)


def _allowed_visit_actions(order_marks: dict) -> list[str]:
    """Действия по визиту сейчас: следующая отметка по порядку и, после прибытия, TRANSFER."""
    nxt = _next_kind(order_marks)
    if nxt is None:
        return []
    out = [nxt]
    if "ARRIVED" in order_marks and "DONE" not in order_marks:
        out.append("TRANSFER")
    return out


def _route_actions(route, staff_id: str, day, marks: dict) -> list[str]:
    if route is None or staff_id in day.absent_staff_ids or route.status == "UNAVAILABLE":
        return []
    return [] if STAFF_MARK_PREFIX + staff_id in marks else ["UNAVAILABLE"]


def _route_sig(route) -> list[tuple]:
    return [(str(s.type), s.order_id, s.start, s.end) for s in (route.stops if route else [])]


def _norm_kind(kind: str) -> str:
    return "DONE" if kind == "COMPLETED" else kind


# ---------------------------------------------------------------- routes

@router.get("/staff", summary="Список сотрудников смены",
            description="Сотрудники с маршрутом в текущем плане дня (без отсутствующих). "
                        "Параметр `date` (YYYY-MM-DD) необязателен: по умолчанию день демо или последний день с планом.",
            responses={k: _ERRORS[k] for k in (400, 409)})
def employee_staff(request: Request, date: str | None = None) -> dict:
    day_date = _resolve_date(request, date)
    ref, day, plan, _orders, marks = _snapshot(request, day_date)
    items = []
    for route in plan.routes:
        if route.staff_id in day.absent_staff_ids:
            continue
        s = ref.staff.get(route.staff_id)
        if s is None:
            continue
        visit_ids = [st.order_id for st in route.stops if st.type == "VISIT"]
        done = sum(1 for st in route.stops if st.type == "VISIT"
                   and _stop_state(st, marks.get(st.order_id, {}), plan.as_of) == "DONE")
        sc = ref.service_centers.get(s.service_center_id)
        items.append({
            "staff_id": s.staff_id,
            "display_name": display_name(s.staff_id, s.display_name),
            "profiles": list(s.profiles),
            "service_center_id": s.service_center_id,
            "service_center_name": sc.name if sc else None,
            "shift": {"start": s.shift.start, "end": s.shift.end},
            "route_status": route.status,
            "visits": len(visit_ids),
            "visits_done": done,
        })
    items.sort(key=lambda x: x["staff_id"])
    return {"date": day_date.isoformat(), "plan_version": plan.plan_version, "as_of": _iso(plan.as_of),
            "staff": items}


@router.get("/{staff_id}/route", summary="Маршрут сотрудника на день",
            description="Остановки текущей версии плана с состоянием (`state`), отметками (`marks`), "
                        "окном звонка (`call_window`), координатами, телефоном абонента (`customer_phone`, "
                        "в данных организаторов его нет, поэтому null) и допустимыми действиями (`allowed_actions` "
                        "у визита и у маршрута). `next` — ближайший невыполненный визит.",
            responses={k: _ERRORS[k] for k in (400, 404, 409)})
def employee_route(staff_id: str, request: Request, date: str | None = None) -> dict:
    day_date = _resolve_date(request, date)
    ref, day, plan, orders, marks = _snapshot(request, day_date)
    staff = _staff_or_404(ref, staff_id)
    route = _route(plan, staff_id)
    as_of = plan.as_of

    stops = []
    for st in (route.stops if route else []):
        order = orders.get(st.order_id) if st.order_id else None
        om = marks.get(st.order_id, {}) if st.order_id else {}
        is_visit = st.type == "VISIT"
        item = {
            "seq": st.seq,
            "type": st.type,
            "location_id": st.location_id,
            "location": _location(ref, st.location_id, order),
            "order_id": st.order_id,
            "order": _order_dump(ref, order) if order is not None else None,
            "arrival": _iso(st.arrival),
            "start": _iso(st.start),
            "end": _iso(st.end),
            "travel_sec": st.travel_sec,
            "distance_m": st.distance_m,
            "plan_status": st.status,
            "state": _stop_state(st, om, as_of),
            "equipment_after": st.equipment_after.model_dump() if st.equipment_after else None,
            "explanation": st.explanation,
            "marks": _marks_view(om) if is_visit else None,
            "next_mark": _next_kind(om) if is_visit else None,
            "allowed_actions": _allowed_visit_actions(om) if is_visit else [],
            "call_window": _call_window(order) if order is not None and is_visit else None,
        }
        stops.append(item)

    visits = [s for s in stops if s["type"] == "VISIT"]
    nxt = next((s for s in visits if s["state"] != "DONE"), None)
    next_info = None
    if nxt is not None:
        cw = nxt["call_window"]
        o = nxt["order"] or {}
        loc = nxt["location"] or {}
        next_info = {
            "seq": nxt["seq"],
            "order_id": nxt["order_id"],
            "address": o.get("address") or loc.get("address"),
            "lat": o.get("lat", loc.get("lat")),
            "lon": o.get("lon", loc.get("lon")),
            "customer_phone": o.get("customer_phone"),
            "slot": o.get("slot"),
            "arrival": nxt["arrival"],
            "start": nxt["start"],
            "end": nxt["end"],
            "state": nxt["state"],
            "call_window": cw,
            "call_hint": f"Позвонить абоненту до {cw['end'][11:16]}" if cw else None,
            "next_mark": nxt["next_mark"],
            "allowed_actions": nxt["allowed_actions"],
        }

    route_ids = {s["order_id"] for s in visits}
    mark_list = sorted(
        ({"order_id": oid, "kind": k, **v} for oid, kinds in marks.items() if oid in route_ids
         for k, v in kinds.items()),
        key=lambda m: (m["recorded_at"], ALL_KINDS.index(m["kind"])),
    )
    sc = _sc_brief(ref, staff.service_center_id)
    return {
        "date": day_date.isoformat(),
        "plan_version": plan.plan_version,
        "as_of": _iso(as_of),
        "staff": {
            "staff_id": staff.staff_id,
            "display_name": display_name(staff.staff_id, staff.display_name),
            "profiles": list(staff.profiles),
            "technologies": list(staff.technologies),
            "service_center": sc,
            "shift": staff.shift.model_dump(mode="json", by_alias=True),
        },
        "on_shift": route is not None and staff_id not in day.absent_staff_ids,
        "route_status": route.status if route else None,
        "unavailable_from": _iso(route.unavailable_from) if route else None,
        "unavailable_until": _iso(route.unavailable_until) if route else None,
        "allowed_actions": _route_actions(route, staff_id, day, marks),
        "progress": {"visits_total": len(visits), "visits_done": sum(1 for s in visits if s["state"] == "DONE")},
        "next": next_info,
        "stops": stops,
        "marks": mark_list,
    }


# ---------------------------------------------------------------- marks

def _event_error(exc: ApiError, ev_type: str) -> ApiError:
    """Любой отказ события контракта — 422 с исходным кодом и понятным текстом."""
    return ApiError(422, exc.code, f"Событие {ev_type} отклонено планом ({exc.code}): {exc.message}", exc.details)


def _mark_response(*, staff_id, kind, order_id, mark, order_marks, day, duplicate, event_sent, event,
                   event_note, route_changed=False, added=(), removed=(), changed=()) -> dict:
    return {
        "accepted": True,
        "duplicate": duplicate,
        "kind": kind,
        "order_id": order_id,
        "staff_id": staff_id,
        "at": mark["at"],
        "recorded_at": mark["recorded_at"],
        "event_sent": event_sent,
        "event": event,
        "event_note": event_note,
        "plan_version": day.plan_version,
        "route_changed": route_changed,
        "added_order_ids": list(added),
        "removed_order_ids": list(removed),
        "changed_staff_ids": list(changed),
        "marks": _marks_view(order_marks),
        "next_mark": _next_kind(order_marks),
        "allowed_actions": _allowed_visit_actions(order_marks) if order_id else [],
    }


def _post(svc, day_date: dt.date, payload: dict):
    try:
        return svc.post_event(day_date.isoformat(), json.dumps(payload).encode("utf-8"))
    except ApiError as exc:
        raise _event_error(exc, payload["type"]) from None


def _do_mark(request: Request, staff_id: str, body: MarkIn, day_date: dt.date) -> dict:
    svc = _svc(request)
    kind = _norm_kind(body.kind)
    with svc.store.lock:
        ref = svc.store.reference
        if ref is None:
            raise ApiError(409, "REFERENCE_NOT_LOADED", "Справочники не загружены: GET /ui/demo/load")
        _staff_or_404(ref, staff_id)
        day = svc._day(day_date)
        if day.plan is None:
            raise ApiError(409, "PLAN_NOT_BUILT", f"План на {day_date} ещё не построен")
        route = _route(day.plan, staff_id)
        before = _route_sig(route)
        before_orders = {s.order_id for s in (route.stops if route else []) if s.type == "VISIT"}
        at = body.timestamp or body.at or day.model_time
        marks_day = _day_marks(request, svc, day)

        # ---- недоступность сотрудника (не привязана к наряду)
        if kind == "UNAVAILABLE":
            if body.reason not in ("VEHICLE_BREAKDOWN", "ILLNESS", "OTHER"):
                raise ApiError(422, "REASON_REQUIRED",
                               "Для UNAVAILABLE нужна причина: VEHICLE_BREAKDOWN | ILLNESS | OTHER",
                               [{"field": "reason", "message": "VEHICLE_BREAKDOWN | ILLNESS | OTHER"}])
            om = marks_day.get(STAFF_MARK_PREFIX + staff_id, {})
            if "UNAVAILABLE" in om:
                return _mark_response(staff_id=staff_id, kind=kind, order_id=None, mark=om["UNAVAILABLE"],
                                      order_marks={}, day=day, duplicate=True, event_sent=False,
                                      event=om["UNAVAILABLE"].get("event"),
                                      event_note="Такая отметка уже была, событие не повторяется")
            payload = {"event_id": f"EV-MOB-{day_date:%Y%m%d}-UNAV-{staff_id}", "type": "STAFF_UNAVAILABLE",
                       "timestamp": at.isoformat(), "staff_id": staff_id, "reason": body.reason}
            if body.until is not None:
                payload["until"] = body.until.isoformat()
            return _finish(svc, request, staff_id, kind, None, day_date, payload, at, before, before_orders)

        # ---- отметки по наряду
        if not body.order_id:
            raise ApiError(422, "ORDER_ID_REQUIRED", f"Для {body.kind} нужен order_id",
                           [{"field": "order_id", "message": "обязательное поле"}])
        stop = next((s for s in (route.stops if route else [])
                     if s.type == "VISIT" and s.order_id == body.order_id), None)
        if stop is None:
            raise ApiError(422, "ORDER_NOT_IN_ROUTE",
                           f"Наряда {body.order_id} нет в маршруте сотрудника {staff_id}",
                           [{"field": "order_id", "message": "наряд не в маршруте сотрудника"}])
        order_marks = marks_day.get(body.order_id, {})
        if kind in order_marks:  # идемпотентно по (order_id, kind): событие не повторяется
            ex = order_marks[kind]
            return _mark_response(staff_id=staff_id, kind=kind, order_id=body.order_id, mark=ex,
                                  order_marks=order_marks, day=day, duplicate=True, event_sent=False,
                                  event=ex.get("event"), event_note="Такая отметка уже была, событие не повторяется")

        if kind == "TRANSFER":
            if body.reason not in ("STATION_EQUIPMENT", "TRUNK_CABLE"):
                raise ApiError(422, "REASON_REQUIRED",
                               "Для TRANSFER нужна причина: STATION_EQUIPMENT | TRUNK_CABLE",
                               [{"field": "reason", "message": "STATION_EQUIPMENT | TRUNK_CABLE"}])
            if "DONE" in order_marks:
                raise ApiError(422, "MARK_OUT_OF_ORDER", "Визит уже завершён, передать наряд нельзя",
                               [{"field": "kind", "message": "после DONE"}])
            if "ARRIVED" not in order_marks:
                raise ApiError(422, "MARK_OUT_OF_ORDER",
                               "Передать наряд можно только после отметки ARRIVED (визит должен быть начат)",
                               [{"field": "kind", "message": "сначала ARRIVED"}])
        else:
            if "TRANSFER" in order_marks:
                raise ApiError(422, "MARK_OUT_OF_ORDER", f"Наряд уже передан (TRANSFER), отметка {kind} невозможна",
                               [{"field": "kind", "message": "после TRANSFER"}])
            expected = _next_kind(order_marks)
            if kind != expected:
                raise ApiError(422, "MARK_OUT_OF_ORDER",
                               f"Отметка {kind} раньше {expected}: порядок CALLED → ARRIVED → DONE",
                               [{"field": "kind", "message": f"ожидается {expected}"}])
        prev = {"ARRIVED": "CALLED", "DONE": "ARRIVED", "TRANSFER": "ARRIVED"}.get(kind)
        if prev and at < dt.datetime.fromisoformat(order_marks[prev]["at"]):
            raise ApiError(422, "MARK_OUT_OF_ORDER", f"Время отметки {kind} раньше отметки {prev}",
                           [{"field": "timestamp", "message": "раньше предыдущей отметки"}])

        payload = None
        note = None
        eid = f"EV-MOB-{day_date:%Y%m%d}-{body.order_id}-{kind}"
        if kind == "CALLED":
            note = "Звонок: только запись отметки, событий контракта для него нет"
        elif kind == "ARRIVED":
            late = at - stop.start
            if late >= dt.timedelta(minutes=LATE_ARRIVAL_THRESHOLD_MIN):
                payload = {"event_id": eid, "type": "VISIT_DELAYED", "timestamp": at.isoformat(),
                           "staff_id": staff_id, "order_id": body.order_id,
                           "expected_end": (stop.end + late).isoformat()}
            else:
                note = (f"Прибытие позже планового начала менее чем на {LATE_ARRIVAL_THRESHOLD_MIN} мин "
                        "или вовремя: только отметка")
        elif kind == "DONE":
            if at > stop.end:
                payload = {"event_id": eid, "type": "VISIT_DELAYED", "timestamp": at.isoformat(),
                           "staff_id": staff_id, "order_id": body.order_id, "expected_end": at.isoformat()}
            else:
                note = ("Завершено не позже планового конца: только отметка (досрочное завершение "
                        "событием контракта не выражается)")
        else:  # TRANSFER
            if at <= stop.start:
                raise ApiError(422, "VISIT_NOT_STARTED",
                               f"Визит по {body.order_id} начинается по плану в {stop.start.isoformat()}: "
                               "ORDER_TRANSFERRED допустим только для начатого визита (timestamp позже начала)",
                               [{"field": "timestamp", "message": "не позже планового начала визита"}])
            payload = {"event_id": eid, "type": "ORDER_TRANSFERRED", "timestamp": at.isoformat(),
                       "staff_id": staff_id, "order_id": body.order_id, "actual_end": at.isoformat(),
                       "transferred_to": body.reason}
        return _finish(svc, request, staff_id, kind, body.order_id, day_date, payload, at, before, before_orders,
                       note)


def _finish(svc, request, staff_id, kind, order_id, day_date, payload, at, before, before_orders,
            note=None) -> dict:
    """Шлёт событие (если есть), записывает отметку, считает изменения маршрута. Под lock вызывающего."""
    event = None
    changed: list[str] = []
    if payload is not None:
        res = _post(svc, day_date, payload)  # при отказе поднимает 422: отметка не записывается
        event = {"event_id": res.event_id, "type": payload["type"], "accepted": res.accepted,
                 "duplicate": res.duplicate, "message": res.message}
        changed = list(res.changed_staff_ids)
    day = svc._day(day_date)
    marks_day = _day_marks(request, svc, day)
    key = order_id if order_id is not None else STAFF_MARK_PREFIX + staff_id
    om = marks_day.setdefault(key, {})
    mark = {"at": _iso(at), "recorded_at": dt.datetime.now(dt.timezone.utc).isoformat(), "staff_id": staff_id}
    if event is not None:
        mark["event"] = event
    om[kind] = mark
    route = _route(day.plan, staff_id)
    after_orders = {s.order_id for s in (route.stops if route else []) if s.type == "VISIT"}
    return _mark_response(
        staff_id=staff_id, kind=kind, order_id=order_id, mark=mark,
        order_marks=om if order_id else {}, day=day, duplicate=False, event_sent=event is not None,
        event=event, event_note=note, route_changed=_route_sig(route) != before,
        added=sorted(after_orders - before_orders), removed=sorted(before_orders - after_orders),
        changed=changed)


@router.post(
    "/{staff_id}/marks", summary="Отметка сотрудника (звонок, прибытие, завершение, передача, недоступность)",
    description="Записывает отметку и, если контракт это допускает, отправляет событие в план "
                "(VISIT_DELAYED / ORDER_TRANSFERRED / STAFF_UNAVAILABLE тем же путём, что POST /api/v1/days/{date}/events). "
                "Повтор отметки того же вида на тот же наряд: `duplicate: true`, без нового события. "
                "Если план событие отклонил, ответ 422 с кодом события, а отметка не записывается.",
    response_model=MarkOut,
    responses={k: _ERRORS[k] for k in (400, 404, 409, 422)},
    openapi_extra={"requestBody": {"required": True, "content": {"application/json": {
        "schema": MarkIn.model_json_schema(), "examples": {
            "called": {"summary": "Звонок абоненту", "value": {"order_id": "ORD-1", "kind": "CALLED"}},
            "arrived": {"summary": "Прибытие (с фактическим временем)",
                        "value": {"order_id": "ORD-1", "kind": "ARRIVED", "timestamp": "2026-10-08T10:12:00+05:00"}},
            "transfer": {"summary": "Передача наряда",
                         "value": {"order_id": "ORD-1", "kind": "TRANSFER", "reason": "TRUNK_CABLE",
                                   "timestamp": "2026-10-08T10:40:00+05:00"}},
            "unavailable": {"summary": "Недоступен", "value": {
                "kind": "UNAVAILABLE", "reason": "ILLNESS", "timestamp": "2026-10-08T12:00:00+05:00"}},
        }}}}},
)
async def employee_mark(staff_id: str, request: Request, date: str | None = Query(
        None, description="Дата дня YYYY-MM-DD; по умолчанию день демо или последний день с планом")) -> dict:
    body: MarkIn = _parse(MarkIn.model_validate_json, await request.body())
    day_date = _resolve_date(request, date)
    return await run_in_threadpool(_do_mark, request, staff_id, body, day_date)
````

## File: backend/app/errors.py
````python
"""Ошибки API в формате ErrorResponse."""

from __future__ import annotations

from typing import Any


class ApiError(Exception):
    """Ошибка, которую видит клиент: HTTP-статус, машинный код, текст и детали."""

    def __init__(
        self,
        status: int,
        code: str,
        message: str,
        details: list[dict[str, Any]] | None = None,
    ) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
        self.details = details or []

    def body(self) -> dict[str, Any]:
        return {
            "error": {
                "code": self.code,
                "message": self.message,
                "details": self.details,
            }
        }
````

## File: backend/app/history_api.py
````python
"""Чтение истории из журнала PostgreSQL: /ui/history (вне контракта /api/v1).

Без БД эндпоинты отвечают 503 в формате ErrorResponse; остальной сервис не затрагивается.
"""

from __future__ import annotations

import datetime as dt

from fastapi import APIRouter, Query

from .errors import ApiError
from .journal import JournalUnavailable, journal

router = APIRouter(prefix="/ui/history", tags=["ui-history"])


def _query(sql: str, params: tuple = ()) -> list[dict]:
    try:
        return journal.query(sql, params)
    except JournalUnavailable as exc:
        raise ApiError(503, "JOURNAL_UNAVAILABLE", str(exc)) from None


def _date(value: str) -> dt.date:
    try:
        return dt.date.fromisoformat(value)
    except ValueError:
        raise ApiError(400, "BAD_REQUEST", f"Некорректная дата '{value}', ожидается YYYY-MM-DD") from None


@router.get("/days")
def history_days() -> dict:
    rows = _query(
        'SELECT d.date, d."now", d.loaded_at, d.orders_total, '
        "(SELECT max(version) FROM plan_versions p WHERE p.date = d.date) AS last_version "
        "FROM days d ORDER BY d.date DESC"
    )
    return {"days": rows}


@router.get("/{date}/versions")
def history_versions(date: str) -> dict:
    day = _date(date)
    rows = _query(
        "SELECT version, as_of, based_on_event_id, computation_ms, visits_planned, unassigned, "
        "pgp_orders, cancelled, late_risk_active, travel_sec_total, utilization_cv "
        "FROM plan_versions WHERE date = %s ORDER BY version",
        (day,),
    )
    return {"date": date, "versions": rows}


@router.get("/{date}/events")
def history_events(date: str, limit: int = Query(1000, ge=1, le=10000), offset: int = Query(0, ge=0)) -> dict:
    day = _date(date)
    rows = _query(
        "SELECT event_id, ts, type, payload, accepted, duplicate, processing_ms, plan_version "
        "FROM events WHERE date = %s ORDER BY plan_version, ts LIMIT %s OFFSET %s",
        (day, limit, offset),
    )
    return {"date": date, "events": rows}


@router.get("/{date}/plan/{version}")
def history_plan(date: str, version: int) -> dict:
    day = _date(date)
    rows = _query("SELECT plan FROM plan_versions WHERE date = %s AND version = %s", (day, version))
    if not rows:
        raise ApiError(404, "PLAN_NOT_FOUND", f"Версия {version} плана на {date} не найдена в журнале")
    return rows[0]["plan"]
````

## File: backend/app/journal.py
````python
"""Журнал истории в PostgreSQL (необязательный, вне горячего пути).

Сервис считает всё в памяти; сюда только дописываются факты для графиков и разбора.
Запись идёт через ограниченную очередь в одном фоновом потоке: вызовы record_* не
блокируются и не бросают исключений. Нет DATABASE_URL, БД недоступна или очередь
переполнена — запись пропускается (в лог пишется предупреждение), API работает как обычно.
Без БД поток переподключается с нарастающей паузой.
"""

from __future__ import annotations

import datetime as dt
import logging
import os
import queue
import threading
import time
from typing import Any

log = logging.getLogger("journal")

QUEUE_MAX = 10_000
CONNECT_TIMEOUT_SEC = 3
MAX_BACKOFF_SEC = 30

SCHEMA = """
CREATE TABLE IF NOT EXISTS days (
    date         date PRIMARY KEY,
    "now"        timestamptz,
    loaded_at    timestamptz NOT NULL DEFAULT now(),
    orders_total integer
);
CREATE TABLE IF NOT EXISTS plan_versions (
    date               date    NOT NULL,
    version            integer NOT NULL,
    as_of              timestamptz,
    based_on_event_id  text,
    computation_ms     integer,
    visits_planned     integer,
    unassigned         integer,
    pgp_orders         integer,
    cancelled          integer,
    late_risk_active   integer,
    travel_sec_total   integer,
    utilization_cv     double precision,
    plan               jsonb NOT NULL,
    PRIMARY KEY (date, version)
);
CREATE TABLE IF NOT EXISTS events (
    date          date NOT NULL,
    event_id      text NOT NULL,
    ts            timestamptz,
    type          text,
    payload       jsonb,
    accepted      boolean,
    duplicate     boolean,
    processing_ms integer,
    plan_version  integer,
    PRIMARY KEY (date, event_id)
);
CREATE TABLE IF NOT EXISTS alerts (
    date       date NOT NULL,
    alert_id   text NOT NULL,
    created_at timestamptz,
    type       text,
    status     text,
    order_id   text,
    payload    jsonb,
    PRIMARY KEY (date, alert_id)
);
"""


class JournalUnavailable(Exception):
    """БД не настроена или недоступна (читающие эндпоинты отвечают 503)."""


def _metrics(plan) -> dict[str, Any]:
    s = plan.summary
    visits = sum(1 for r in plan.routes for st in r.stops if st.type == "VISIT")
    pgp = sum(len(g.order_ids) for g in plan.pgp_groups)
    late = sum(1 for a in plan.alerts if a.type == "LATE_RISK" and a.status == "ACTIVE")

    def pick(value, fallback):
        return value if value is not None else fallback

    return {
        "visits_planned": pick(s.visits_planned if s else None, visits),
        "unassigned": pick(s.unassigned if s else None, len(plan.unassigned)),
        "pgp_orders": pick(s.pgp_orders if s else None, pgp),
        "cancelled": pick(s.cancelled if s else None, len(plan.cancelled)),
        "late_risk_active": late,
        "travel_sec_total": pick(s.travel_sec_total if s else None,
                                 sum(r.totals.travel_sec for r in plan.routes if r.totals)),
        "utilization_cv": s.utilization_cv if s else None,
    }


class Journal:
    def __init__(self, url: str | None) -> None:
        self.url = (url or "").strip() or None
        self._q: queue.Queue = queue.Queue(maxsize=QUEUE_MAX)
        self._thread: threading.Thread | None = None
        self._lock = threading.Lock()
        self._ready = threading.Event()  # соединение есть, схема создана
        self._pool = None
        self._dropped = 0
        self._last_warn = 0.0

    @property
    def enabled(self) -> bool:
        return self.url is not None

    # ------------------------------------------------ жизненный цикл

    def start(self) -> None:
        if not self.enabled:
            log.warning("DATABASE_URL не задан: журнал истории отключён")
            return
        with self._lock:
            if self._thread is None:
                self._thread = threading.Thread(target=self._run, name="journal", daemon=True)
                self._thread.start()

    def _warn(self, msg: str, *args) -> None:
        """Предупреждения не чаще раза в 60 с, чтобы недоступная БД не засоряла лог."""
        now = time.monotonic()
        if now - self._last_warn >= 60 or self._last_warn == 0.0:
            self._last_warn = now
            log.warning(msg, *args)

    def _connect(self):
        import psycopg

        conn = psycopg.connect(self.url, connect_timeout=CONNECT_TIMEOUT_SEC, autocommit=True)
        conn.execute(SCHEMA)
        if self._pool is None:
            from psycopg_pool import ConnectionPool

            self._pool = ConnectionPool(
                self.url, min_size=1, max_size=3, open=False, timeout=CONNECT_TIMEOUT_SEC,
                kwargs={"connect_timeout": CONNECT_TIMEOUT_SEC, "autocommit": True},
            )
            self._pool.open()
        self._ready.set()
        log.info("Журнал истории: подключено к PostgreSQL")
        return conn

    def _run(self) -> None:
        try:
            import psycopg
            import psycopg_pool  # noqa: F401
        except ImportError as exc:
            log.warning("Журнал истории отключён: нет пакета psycopg (%s)", exc)
            return

        conn = None
        backoff = 1.0
        item = None
        while True:
            try:
                if conn is None or conn.closed:
                    self._ready.clear()
                    conn = self._connect()
                    backoff = 1.0
                if item is None:
                    item = self._q.get(timeout=10)
                self._handle(conn, item)
                item = None
            except queue.Empty:
                try:  # заодно замечаем обрыв соединения в простое
                    conn.execute("SELECT 1")
                except Exception:
                    conn = None
            except (psycopg.OperationalError, psycopg.InterfaceError) as exc:
                self._ready.clear()
                self._warn("Журнал истории: БД недоступна (%s); повтор через %.0f с", exc, backoff)
                try:
                    if conn is not None:
                        conn.close()
                except Exception:
                    pass
                conn = None
                time.sleep(backoff)
                backoff = min(backoff * 2, MAX_BACKOFF_SEC)
            except Exception:
                log.exception("Журнал истории: запись пропущена из-за ошибки")
                item = None

    # ------------------------------------------------ постановка в очередь

    def _put(self, item: tuple) -> None:
        if not self.enabled:
            return
        try:
            self._q.put_nowait(item)
        except queue.Full:
            self._dropped += 1
            self._warn("Журнал истории: очередь переполнена, записи отбрасываются (всего %d)", self._dropped)

    def record_day(self, date: dt.date, now: dt.datetime, orders_total: int) -> None:
        self._put(("day", date, now, orders_total, dt.datetime.now(dt.timezone.utc)))

    def record_plan(self, plan) -> None:
        """Первая версия плана (после POST /plan)."""
        self._put(("plan", plan))

    def record_event(self, date: dt.date, event, plan, processing_ms: int) -> None:
        """Принятое (не дубликат) событие вместе с порождённой версией плана."""
        self._put(("event", date, event, plan, processing_ms))

    # ------------------------------------------------ запись (в потоке worker)

    def _handle(self, conn, item: tuple) -> None:
        from psycopg.types.json import Jsonb

        kind = item[0]
        if kind == "day":
            _, date, now, total, loaded_at = item
            with conn.transaction():
                # День в памяти заменён целиком — прежняя история этой даты неактуальна.
                for table in ("plan_versions", "events", "alerts"):
                    conn.execute(f"DELETE FROM {table} WHERE date = %s", (date,))
                conn.execute(
                    'INSERT INTO days (date, "now", loaded_at, orders_total) VALUES (%s, %s, %s, %s) '
                    'ON CONFLICT (date) DO UPDATE SET "now" = EXCLUDED."now", '
                    "loaded_at = EXCLUDED.loaded_at, orders_total = EXCLUDED.orders_total",
                    (date, now, loaded_at, total),
                )
            return

        if kind == "plan":
            plan, event = item[1], None
        else:  # "event"
            _, date, event, plan, processing_ms = item

        m = _metrics(plan)
        with conn.transaction():
            conn.execute(
                "INSERT INTO plan_versions (date, version, as_of, based_on_event_id, computation_ms, "
                "visits_planned, unassigned, pgp_orders, cancelled, late_risk_active, "
                "travel_sec_total, utilization_cv, plan) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) "
                "ON CONFLICT (date, version) DO UPDATE SET as_of=EXCLUDED.as_of, "
                "based_on_event_id=EXCLUDED.based_on_event_id, computation_ms=EXCLUDED.computation_ms, "
                "visits_planned=EXCLUDED.visits_planned, unassigned=EXCLUDED.unassigned, "
                "pgp_orders=EXCLUDED.pgp_orders, cancelled=EXCLUDED.cancelled, "
                "late_risk_active=EXCLUDED.late_risk_active, travel_sec_total=EXCLUDED.travel_sec_total, "
                "utilization_cv=EXCLUDED.utilization_cv, plan=EXCLUDED.plan",
                (plan.date, plan.plan_version, plan.as_of, plan.based_on_event_id, plan.computation_ms,
                 m["visits_planned"], m["unassigned"], m["pgp_orders"], m["cancelled"],
                 m["late_risk_active"], m["travel_sec_total"], m["utilization_cv"],
                 Jsonb(plan.model_dump(mode="json"))),
            )
            if event is not None:
                conn.execute(
                    "INSERT INTO events (date, event_id, ts, type, payload, accepted, duplicate, "
                    "processing_ms, plan_version) VALUES (%s,%s,%s,%s,%s,true,false,%s,%s) "
                    "ON CONFLICT (date, event_id) DO NOTHING",
                    (date, event.event_id, event.timestamp, event.type,
                     Jsonb(event.model_dump(mode="json")), processing_ms, plan.plan_version),
                )
            rows = [
                (plan.date, a.alert_id, a.created_at, a.type.value if hasattr(a.type, "value") else a.type,
                 a.status.value if hasattr(a.status, "value") else a.status, a.order_id,
                 Jsonb(a.model_dump(mode="json")))
                for a in plan.alerts
            ]
            if rows:
                with conn.cursor() as cur:
                    cur.executemany(
                        "INSERT INTO alerts (date, alert_id, created_at, type, status, order_id, payload) "
                        "VALUES (%s,%s,%s,%s,%s,%s,%s) ON CONFLICT (date, alert_id) DO UPDATE SET "
                        "status=EXCLUDED.status, payload=EXCLUDED.payload",
                        rows,
                    )

    # ------------------------------------------------ чтение (для /ui/history)

    def query(self, sql: str, params: tuple = ()) -> list[dict]:
        if not self.enabled or not self._ready.is_set() or self._pool is None:
            raise JournalUnavailable("БД журнала недоступна")
        from psycopg.rows import dict_row

        try:
            with self._pool.connection(timeout=CONNECT_TIMEOUT_SEC) as conn:
                with conn.cursor(row_factory=dict_row) as cur:
                    cur.execute(sql, params)
                    return cur.fetchall()
        except Exception as exc:
            raise JournalUnavailable(f"Ошибка чтения журнала: {exc.__class__.__name__}") from exc


journal = Journal(os.environ.get("DATABASE_URL"))
````

## File: backend/app/main.py
````python
"""Точка входа FastAPI: uvicorn app.main:app --port 8080."""

from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from .api import router
from .errors import ApiError
from .journal import journal
from .planner import Planner, make_planner
from .service import MAX_BODY_BYTES, DispatchService
from .store import Store


def create_app(planner: Planner | None = None, store: Store | None = None) -> FastAPI:
    app = FastAPI(
        title="Умная диспетчеризация выездных бригад — API решения",
        version="0.1.0",
    )
    app.state.service = DispatchService(store or Store(), planner or make_planner())
    app.include_router(router)
    journal.start()  # фоновая запись истории в PostgreSQL; без DATABASE_URL ничего не делает
    from .ui_api import install_ui; install_ui(app)  # noqa: E702 — /ui для интерфейса диспетчера

    @app.middleware("http")
    async def limit_body(request: Request, call_next):
        length = request.headers.get("content-length")
        if length and length.isdigit() and int(length) > MAX_BODY_BYTES:
            return JSONResponse(
                status_code=400,
                content=ApiError(400, "BAD_REQUEST", "Тело запроса больше 100 МБ").body(),
            )
        return await call_next(request)

    @app.exception_handler(ApiError)
    async def handle_api_error(_: Request, exc: ApiError) -> JSONResponse:
        return JSONResponse(status_code=exc.status, content=exc.body())

    @app.exception_handler(Exception)
    async def handle_unexpected(_: Request, exc: Exception) -> JSONResponse:
        return JSONResponse(
            status_code=500,
            content=ApiError(500, "INTERNAL_ERROR", f"Внутренняя ошибка: {exc.__class__.__name__}").body(),
        )

    return app


app = create_app()
````

## File: backend/app/models.py
````python
"""Pydantic-модели контракта API (data/api_spec.yaml, OpenAPI 3.0.3).

Имена полей, типы и enum-значения совпадают со спецификацией. Неизвестные входные
поля игнорируются (поведение pydantic по умолчанию). Все моменты времени — с
часовым поясом (AwareDatetime).
"""

from __future__ import annotations

import datetime as dt
from enum import StrEnum
from typing import Annotated, Literal, Optional, Union

from pydantic import AfterValidator, AwareDatetime, BaseModel, ConfigDict, Field


# ---------------------------------------------------------------- helpers

def _unique(values: list):
    if len(set(values)) != len(values):
        raise ValueError("элементы списка должны быть уникальны")
    return values


LocalTime = Annotated[str, Field(pattern=r"^([01][0-9]|2[0-3]):[0-5][0-9]$")]
ServiceCenterId = Annotated[str, Field(pattern=r"^SC-[0-9]{2}$")]
StaffId = Annotated[str, Field(pattern=r"^ST-[0-9]{3}$")]
BuildingId = Annotated[str, Field(pattern=r"^B-[0-9]{5}$")]
AccessNodeId = Annotated[str, Field(pattern=r"^AN-[0-9]{4}$")]
OrderId = Annotated[str, Field(pattern=r"^ORD-[0-9]{8}-[0-9]{4}$")]


# ---------------------------------------------------------------- enums

class Technology(StrEnum):
    PON = "PON"
    FTTB = "FTTB"
    XDSL = "XDSL"
    DOCSIS = "DOCSIS"


class Profile(StrEnum):
    INSTALL = "INSTALL"
    LTP3 = "LTP3"


class WorkTypeCode(StrEnum):
    INST_PON_MKD = "INST_PON_MKD"
    INST_FTTB_MKD = "INST_FTTB_MKD"
    INST_PRIVATE_VLS = "INST_PRIVATE_VLS"
    ADD_SERVICE = "ADD_SERVICE"
    SURVEY_PRIVATE = "SURVEY_PRIVATE"
    REPAIR = "REPAIR"
    DEGRADATION = "DEGRADATION"
    DISMANTLE = "DISMANTLE"


class BuildingType(StrEnum):
    MKD = "MKD"
    PRIVATE = "PRIVATE"


class NodeType(StrEnum):
    OLT = "OLT"
    SWITCH = "SWITCH"
    DSLAM = "DSLAM"
    CMTS = "CMTS"


class DeadlineKind(StrEnum):
    SLOT = "SLOT"
    HOURS = "HOURS"
    WORKING_DAYS = "WORKING_DAYS"


class OrderOutcome(StrEnum):
    COMPLETED = "COMPLETED"
    TRANSFERRED_TO_STATION = "TRANSFERRED_TO_STATION"
    CANCELLED = "CANCELLED"
    RESCHEDULED = "RESCHEDULED"
    PGP_GROUPED = "PGP_GROUPED"


class RouteStatus(StrEnum):
    ACTIVE = "ACTIVE"
    UNAVAILABLE = "UNAVAILABLE"


class StopType(StrEnum):
    DEPOT_START = "DEPOT_START"
    VISIT = "VISIT"
    BREAK = "BREAK"
    RESTOCK = "RESTOCK"
    DEPOT_END = "DEPOT_END"


class StopStatus(StrEnum):
    PLANNED = "PLANNED"
    IN_PROGRESS = "IN_PROGRESS"
    DONE = "DONE"


class ReasonCode(StrEnum):
    NO_QUALIFIED_STAFF = "NO_QUALIFIED_STAFF"
    NO_SHIFT_TIME = "NO_SHIFT_TIME"
    SLOT_UNREACHABLE = "SLOT_UNREACHABLE"
    NO_EQUIPMENT = "NO_EQUIPMENT"
    STAFF_UNAVAILABLE = "STAFF_UNAVAILABLE"
    OTHER = "OTHER"


class UnassignedAction(StrEnum):
    RESCHEDULE = "RESCHEDULE"
    REASSIGN = "REASSIGN"
    ESCALATE = "ESCALATE"


class AlertType(StrEnum):
    LATE_RISK = "LATE_RISK"
    PGP_DETECTED = "PGP_DETECTED"
    UNASSIGNED = "UNASSIGNED"


class AlertStatus(StrEnum):
    ACTIVE = "ACTIVE"
    RESOLVED = "RESOLVED"


class AlertAction(StrEnum):
    REASSIGN = "REASSIGN"
    RESCHEDULE = "RESCHEDULE"
    TRANSFER_TO_STATION = "TRANSFER_TO_STATION"
    NONE = "NONE"


class PgpAction(StrEnum):
    TRANSFER_TO_STATION = "TRANSFER_TO_STATION"


class EventType(StrEnum):
    NEW_ORDER = "NEW_ORDER"
    ORDER_CANCELLED = "ORDER_CANCELLED"
    VISIT_DELAYED = "VISIT_DELAYED"
    ORDER_TRANSFERRED = "ORDER_TRANSFERRED"
    STAFF_UNAVAILABLE = "STAFF_UNAVAILABLE"
    TIME_TICK = "TIME_TICK"


class CancelReason(StrEnum):
    NOT_CONFIRMED = "NOT_CONFIRMED"
    CUSTOMER_REQUEST = "CUSTOMER_REQUEST"


class TransferredTo(StrEnum):
    STATION_EQUIPMENT = "STATION_EQUIPMENT"
    TRUNK_CABLE = "TRUNK_CABLE"


class UnavailableReason(StrEnum):
    VEHICLE_BREAKDOWN = "VEHICLE_BREAKDOWN"
    ILLNESS = "ILLNESS"
    OTHER = "OTHER"


class ScenarioTriggerType(StrEnum):
    VISIT_DELAYED = "VISIT_DELAYED"
    ORDER_TRANSFERRED = "ORDER_TRANSFERRED"


# ---------------------------------------------------------------- basic

class Equipment(BaseModel):
    """Количество абонентского оборудования (АО) по видам."""

    ONT: int = Field(ge=0)
    ROUTER: int = Field(ge=0)
    STB: int = Field(ge=0)


class TimeWindow(BaseModel):
    start: AwareDatetime
    end: AwareDatetime


# ---------------------------------------------------------------- reference

class OpeningHours(BaseModel):
    open: LocalTime
    close: LocalTime


class ServiceCenter(BaseModel):
    service_center_id: ServiceCenterId
    name: str
    address: str
    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)
    opening_hours: OpeningHours
    restock_duration_min: int = Field(ge=0)


class BreakWindow(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    from_: LocalTime = Field(alias="from")
    to: LocalTime


class Shift(BaseModel):
    start: LocalTime
    end: LocalTime
    break_duration_min: int = Field(ge=0)
    break_window: BreakWindow


class Staff(BaseModel):
    staff_id: StaffId
    display_name: Optional[str] = None
    service_center_id: ServiceCenterId
    profiles: Annotated[list[Profile], Field(min_length=1), AfterValidator(_unique)]
    technologies: Annotated[list[Technology], Field(min_length=1), AfterValidator(_unique)]
    height_permit: bool
    shift: Shift
    equipment_start: Equipment
    equipment_capacity: Equipment
    experience_years: Optional[float] = Field(default=None, ge=0)


class Building(BaseModel):
    building_id: BuildingId
    address: str
    lat: float
    lon: float
    building_type: BuildingType
    overhead_line: bool
    zone_service_center_id: Optional[ServiceCenterId] = None


class AccessNode(BaseModel):
    access_node_id: AccessNodeId
    node_type: NodeType
    technology: Technology
    name: str
    address: Optional[str] = None
    lat: float
    lon: float
    buildings: Annotated[list[Building], Field(min_length=1)]


class WorkType(BaseModel):
    """Строка справочника work_types.csv."""

    code: WorkTypeCode
    name: str
    profiles: Annotated[list[Profile], Field(min_length=1), AfterValidator(_unique)]
    duration_min: int = Field(ge=1)
    max_start_delay_min: Optional[int] = Field(default=None, ge=0)
    deadline_kind: DeadlineKind
    deadline_value: Optional[int] = Field(default=None, ge=1)
    repeat_deadline_value: Optional[int] = Field(default=None, ge=1)
    pgp_relevant: bool


class HourlyFactor(BaseModel):
    hour: int = Field(ge=0, le=23)
    factor: float = Field(ge=0)


class TravelMatrix(BaseModel):
    matrix_id: str
    points: Annotated[list[str], Field(min_length=2), AfterValidator(_unique)]
    durations_sec: list[list[Annotated[int, Field(ge=0)]]]
    distances_m: list[list[Annotated[int, Field(ge=0)]]]
    hourly_factors: list[HourlyFactor] = Field(default_factory=list)


class ReferenceData(BaseModel):
    service_centers: Annotated[list[ServiceCenter], Field(min_length=1)]
    staff: Annotated[list[Staff], Field(min_length=1)]
    access_nodes: Annotated[list[AccessNode], Field(min_length=1)]
    work_types: Annotated[list[WorkType], Field(min_length=1)]


# ---------------------------------------------------------------- orders

class OrderFact(BaseModel):
    """Фактическое исполнение — только в orders_train.json."""

    outcome: Optional[OrderOutcome] = None
    staff_id: Optional[str] = None
    actual_start: Optional[AwareDatetime] = None
    actual_duration_min: Optional[int] = Field(default=None, ge=0)
    pgp_incident_id: Optional[str] = None


class Order(BaseModel):
    order_id: OrderId
    work_type_code: WorkTypeCode
    technology: Technology
    building_id: BuildingId
    address: str
    lat: float
    lon: float
    building_type: BuildingType
    requires_height: bool
    access_node_id: AccessNodeId
    registered_at: AwareDatetime
    slot: TimeWindow
    latest_start: AwareDatetime
    deadline: AwareDatetime
    required_equipment: Equipment
    is_repeat: bool
    previous_order_id: Optional[str] = None
    fact: Optional[OrderFact] = None


class DayInput(BaseModel):
    date: dt.date
    now: AwareDatetime
    orders: list[Order]
    absent_staff_ids: Annotated[list[str], AfterValidator(_unique)] = Field(default_factory=list)


class PlanRequest(BaseModel):
    time_limit_sec: int = Field(default=300, ge=1, le=300)


# ---------------------------------------------------------------- plan

class Stop(BaseModel):
    seq: int = Field(ge=1)
    type: StopType
    location_id: str
    order_id: Optional[str] = None
    arrival: AwareDatetime
    start: AwareDatetime
    end: AwareDatetime
    travel_sec: Optional[int] = Field(default=None, ge=0)
    distance_m: Optional[int] = Field(default=None, ge=0)
    status: Optional[StopStatus] = None
    equipment_after: Optional[Equipment] = None
    explanation: Optional[str] = None


class RouteTotals(BaseModel):
    visits: int = Field(default=0, ge=0)
    travel_sec: int = Field(default=0, ge=0)
    distance_m: int = Field(default=0, ge=0)
    work_min: int = Field(default=0, ge=0)
    utilization: float = Field(default=0, ge=0)


class Route(BaseModel):
    staff_id: str
    service_center_id: str
    status: RouteStatus
    unavailable_from: Optional[AwareDatetime] = None
    unavailable_until: Optional[AwareDatetime] = None
    stops: list[Stop] = Field(default_factory=list)
    totals: Optional[RouteTotals] = None


class UnassignedOrder(BaseModel):
    order_id: str
    reason_code: ReasonCode
    reason_text: str
    suggested_action: UnassignedAction
    proposed_slot: Optional[TimeWindow] = None
    since: Optional[AwareDatetime] = None


class PgpGroup(BaseModel):
    group_id: str
    access_node_id: str
    order_ids: Annotated[list[str], Field(min_length=2), AfterValidator(_unique)]
    detected_at: AwareDatetime
    action: PgpAction
    comment: Optional[str] = None


class CancelledOrder(BaseModel):
    order_id: str
    cancelled_at: AwareDatetime
    event_id: Optional[str] = None


class Alert(BaseModel):
    alert_id: str
    created_at: AwareDatetime
    type: AlertType
    status: AlertStatus
    resolved_at: Optional[AwareDatetime] = None
    order_id: Optional[str] = None
    group_id: Optional[str] = None
    staff_id: Optional[str] = None
    forecast_start: Optional[AwareDatetime] = None
    lateness_min: Optional[int] = None
    suggested_action: AlertAction
    suggested_staff_id: Optional[str] = None
    proposed_slot: Optional[TimeWindow] = None
    applied: bool = False
    message: str


class PlanSummary(BaseModel):
    orders_total: Optional[int] = None
    visits_planned: Optional[int] = None
    unassigned: Optional[int] = None
    pgp_orders: Optional[int] = None
    cancelled: Optional[int] = None
    travel_sec_total: Optional[int] = None
    utilization_cv: Optional[float] = None


class Plan(BaseModel):
    date: dt.date
    plan_version: int = Field(ge=1)
    based_on_event_id: Optional[str] = None
    as_of: AwareDatetime
    computation_ms: Optional[int] = Field(default=None, ge=0)
    routes: list[Route] = Field(default_factory=list)
    unassigned: list[UnassignedOrder] = Field(default_factory=list)
    pgp_groups: list[PgpGroup] = Field(default_factory=list)
    cancelled: list[CancelledOrder] = Field(default_factory=list)
    alerts: list[Alert] = Field(default_factory=list)
    summary: Optional[PlanSummary] = None


# ---------------------------------------------------------------- events

class EventBase(BaseModel):
    event_id: str
    timestamp: AwareDatetime


class NewOrderEvent(EventBase):
    type: Literal["NEW_ORDER"]
    order: Order


class OrderCancelledEvent(EventBase):
    type: Literal["ORDER_CANCELLED"]
    order_id: str
    reason: CancelReason


class VisitDelayedEvent(EventBase):
    type: Literal["VISIT_DELAYED"]
    staff_id: str
    order_id: str
    expected_end: AwareDatetime


class OrderTransferredEvent(EventBase):
    type: Literal["ORDER_TRANSFERRED"]
    staff_id: str
    order_id: str
    actual_end: AwareDatetime
    transferred_to: TransferredTo


class StaffUnavailableEvent(EventBase):
    type: Literal["STAFF_UNAVAILABLE"]
    staff_id: str
    until: Optional[AwareDatetime] = None
    reason: UnavailableReason


class TimeTickEvent(EventBase):
    type: Literal["TIME_TICK"]


Event = Annotated[
    Union[
        NewOrderEvent,
        OrderCancelledEvent,
        VisitDelayedEvent,
        OrderTransferredEvent,
        StaffUnavailableEvent,
        TimeTickEvent,
    ],
    Field(discriminator="type"),
]


class EventResult(BaseModel):
    event_id: str
    accepted: bool
    duplicate: bool
    plan_version: int = Field(ge=1)
    as_of: AwareDatetime
    processing_ms: Optional[int] = Field(default=None, ge=0)
    changed_staff_ids: list[str] = Field(default_factory=list)
    new_alerts: list[Alert] = Field(default_factory=list)
    message: Optional[str] = None


class ScenarioTrigger(BaseModel):
    trigger_id: str
    type: ScenarioTriggerType
    order_id: str
    fire_after_start_min: int = Field(ge=0)
    extra_duration_min: Optional[int] = Field(default=None, ge=1)
    transferred_to: Optional[TransferredTo] = None


class EventScenarioFile(BaseModel):
    """Сценарий дня (events_example.json). Файл, не API."""

    date: dt.date
    now: AwareDatetime
    events: list[Event]
    triggers: list[ScenarioTrigger]


# ---------------------------------------------------------------- service

class Health(BaseModel):
    status: Literal["ok"]
    team: Optional[str] = None
    version: Optional[str] = None


class LoadResult(BaseModel):
    status: Literal["loaded"]
    counts: dict[str, Annotated[int, Field(ge=0)]]


class ErrorDetail(BaseModel):
    field: Optional[str] = None
    message: str


class ErrorBody(BaseModel):
    code: str
    message: str
    details: list[ErrorDetail] = Field(default_factory=list)


class ErrorResponse(BaseModel):
    error: ErrorBody
````

## File: backend/app/orders_ui.py
````python
"""Наряды дня из интерфейса диспетчера (/ui/orders/*), вне контракта /api/v1.

* GET  /ui/orders/catalog       — типы работ, поиск домов (подстрока адреса, ≤ 20), сотрудники;
* POST /ui/orders/create        — сервер собирает полный Order по правилам контракта (как
  tools/synth/generate.py) и отправляет его событием NEW_ORDER в текущий план демо-дня;
* POST /ui/orders/{id}/cancel   — событие ORDER_CANCELLED (reason = CUSTOMER_REQUEST).

Время события — модельное время дня (day.model_time, оно же plan.as_of).

Пожелание по исполнителю (preferred_staff_id) — мягкое. Ядро перепланирования не меняется:
событие NEW_ORDER обрабатывает подкласс EventPlanner, у которого переопределён только
выбор исполнителя для нового наряда (place): сначала обычная оценка автоназначения, затем
пробная вставка в хвост маршрута желаемого сотрудника теми же insertion/decode_tail
(все окна, перерыв, конец смены, рюкзак АО — жёстко). Удалась — наряд у него, иначе
автоназначение и причина. Итоговый план, как и всегда, проходит validate_plan +
validate_transition внутри EventPlanner.check; фактический результат сверяется по плану.
"""

from __future__ import annotations

import datetime as dt
import json
import re
from typing import Optional

from fastapi import APIRouter, Query, Request
from pydantic import BaseModel, Field

from .errors import ApiError
from .models import AlertType, StopType
from .planner.context import qualifies
from .planner.events import EventPlanner
from .ui_api import TZ, _demo, _iso, _service, _staff_name

router = APIRouter(prefix="/ui/orders", tags=["ui"])

SLOT_HOURS = (9, 11, 13, 15, 17, 19)  # стандартные двухчасовые окна, как в генераторе
SLOT_LEN = dt.timedelta(hours=2)
NEAREST_LEAD = dt.timedelta(minutes=30)  # генератор: slot.start − registered_at ≥ 30 мин
MIN_LEAD = dt.timedelta(minutes=15)  # ручное окно: до latest_start должно оставаться ≥ 15 мин
SEARCH_LIMIT = 20


# ---------------------------------------------------------------- правила генератора

def _compatible(code: str, b, techs: set[str]) -> bool:
    """Какие дома подходят типу работ (tools/synth/generate.py, gen_orders.pairs_by_type)."""
    if code == "INST_PON_MKD":
        return b.building_type == "MKD" and "PON" in techs
    if code == "INST_FTTB_MKD":
        return b.building_type == "MKD" and "FTTB" in techs
    if code == "INST_PRIVATE_VLS":
        return b.building_type == "PRIVATE" and b.overhead_line
    if code == "SURVEY_PRIVATE":
        return b.building_type == "PRIVATE"
    return True


def _required_tech(code: str) -> str | None:
    return {"INST_PON_MKD": "PON", "INST_FTTB_MKD": "FTTB"}.get(code)


def _equipment(code: str, tech: str) -> dict:
    """Детерминированный вариант Gen.equipment_for (генератор добавляет STB/роутер случайно)."""
    eq = {"ONT": 0, "ROUTER": 0, "STB": 0}
    if code == "INST_PON_MKD":
        eq.update(ONT=1, ROUTER=1)
    elif code == "INST_FTTB_MKD":
        eq.update(ROUTER=1)
    elif code == "INST_PRIVATE_VLS":
        eq.update(ONT=1 if tech == "PON" else 0, ROUTER=1)
    elif code == "ADD_SERVICE":
        eq.update(ROUTER=1)
    elif code in ("REPAIR", "DEGRADATION"):
        eq.update(ONT=1 if tech == "PON" else 0)
    return eq


def _add_working_days(moment: dt.datetime, n: int) -> dt.datetime:
    result, left = moment, n
    while left > 0:
        result += dt.timedelta(days=1)
        if result.weekday() < 5:
            left -= 1
    return result


def _deadline(wt, registered: dt.datetime, latest: dt.datetime, is_repeat: bool) -> dt.datetime:
    """Контрольный срок (compute_deadline генератора, data/api_spec.yaml Order.deadline)."""
    kind = getattr(wt.deadline_kind, "value", wt.deadline_kind)
    if kind == "SLOT":
        return latest + dt.timedelta(minutes=wt.duration_min)
    value = wt.repeat_deadline_value if (is_repeat and wt.repeat_deadline_value) else wt.deadline_value
    if kind == "HOURS":
        return registered + dt.timedelta(hours=value)
    return _add_working_days(registered, value)


def _latest_start(start: dt.datetime, end: dt.datetime, max_delay: int | None) -> dt.datetime:
    return end if max_delay is None else min(end, start + dt.timedelta(minutes=max_delay))


# ---------------------------------------------------------------- состояние демо-дня

def _require_day(request: Request):
    """(svc, demo, date) или 409, если демо-день не загружен."""
    svc, demo = _service(request), _demo(request)
    if demo is None or svc.store.reference is None:
        raise ApiError(409, "PLAN_NOT_BUILT", "День не загружен: откройте приложение или GET /ui/demo/load")
    day = svc.store.days.get(dt.date.fromisoformat(demo["date"]))
    if day is None or day.plan is None:
        raise ApiError(409, "PLAN_NOT_BUILT", "План дня ещё не построен: загрузите день заново")
    return svc, demo, demo["date"]


def _node_techs(ref, building_id: str) -> dict[str, str]:
    """access_node_id -> технология для узлов, обслуживающих дом."""
    return {nid: ref.access_nodes[nid].technology for nid in sorted(ref.building_nodes.get(building_id, ()))}


def _next_order_id(day, demo: dict) -> str:
    prefix = f"ORD-{day.date:%Y%m%d}-"
    n = 0
    for oid in list(day.orders) + list(demo.get("order_info", {})):
        if oid.startswith(prefix):
            try:
                n = max(n, int(oid[len(prefix):]))
            except ValueError:
                pass
    if n >= 9999:
        raise ApiError(422, "ORDER_ID_EXHAUSTED", "Номера нарядов на этот день закончились")
    return f"{prefix}{n + 1:04d}"


def _next_event_id(day) -> str:
    prefix = f"EV-UI-{day.date:%Y%m%d}-"
    n = sum(1 for e in day.events if e.startswith(prefix))
    while f"{prefix}{n + 1:04d}" in day.events:
        n += 1
    return f"{prefix}{n + 1:04d}"


def _event_item(res, body: dict) -> dict:
    """Элемент в формате /ui/demo/step (sent[]) — для строки итога в шапке."""
    return {"event_id": res.event_id, "type": body["type"], "timestamp": body["timestamp"], "accepted": res.accepted,
            "plan_version": res.plan_version, "processing_ms": res.processing_ms, "message": res.message,
            "changed_staff_ids": res.changed_staff_ids}


def _warnings(res, skip_order: str | None = None) -> list[str]:
    out = []
    for a in res.new_alerts:
        if skip_order and a.order_id == skip_order and a.type == AlertType.UNASSIGNED:
            continue  # причина нового наряда и так в карточке
        if a.message:
            out.append(a.message)
    return out[:8]


# ---------------------------------------------------------------- пожелание по исполнителю

class _PreferredEventPlanner(EventPlanner):
    """EventPlanner, у которого для нового наряда сначала пробуется желаемый сотрудник."""

    def __init__(self, *args, preferred: str, order_id: str, report: dict, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self.pref_id, self.pref_order, self.report = preferred, order_id, report

    def _pref_reason(self, o) -> str | None:
        """Почему желаемый сотрудник не может взять наряд в принципе (до пробной вставки)."""
        ref, sid = self.ref, self.pref_id
        staff = ref.staff[sid]
        name = _staff_name(sid, staff.display_name) or sid
        wt, order = o.work_type, o.model
        if sid in self.day.absent_staff_ids or sid not in self.anchors:
            return f"{name} сегодня не на смене"
        A = self.anchors[sid]
        if A.blocked:
            return f"{name} недоступен до конца смены"
        if A.closed or self.t >= A.k.end:
            return f"{name} уже закончил смену"
        if not qualifies(staff, order, wt):
            if not set(staff.profiles) & set(wt.profiles):
                return f"{name} не выполняет «{wt.name}» (нужен профиль {'/'.join(wt.profiles)})"
            if order.technology not in staff.technologies:
                return f"{name} не работает с технологией {order.technology}"
            return f"{name} не имеет допуска к работам на высоте"
        cap, dem = A.k.cap, o.dem
        if any(c < d for c, d in zip(cap, dem)):
            return f"У {name} нет нужного оборудования: в рюкзак не помещается требуемое АО"
        return None

    def place(self, o, exclude=None):
        if o.order_id != self.pref_order or self.report.get("tried"):
            return super().place(o, exclude)
        self.report["tried"] = True
        auto = self.best_staff(o, exclude)
        if auto is not None and auto[1].k.staff_id == self.pref_id:
            self.report["auto_same"] = True
            return super().place(o, exclude)
        reason = self._pref_reason(o)
        if reason is None:
            A = self.anchors[self.pref_id]
            res = self.insertion(A, o, max_tries=12)
            if res is None:
                name = _staff_name(self.pref_id, A.k.model.display_name) or self.pref_id
                win = f"{self.ctx.to_dt(o.a):%H:%M}–{self.ctx.to_dt(o.b):%H:%M}"
                reason = (f"{name} не успевает начать визит в окне {win} без срыва своих визитов, "
                          f"перерыва или конца смены")
            else:
                cost, sched, seq = res
                A.seq, A.sched, A.sched_ok, A.touched = seq, sched, True, True
                self.report["placed"] = True
                if auto is not None:
                    self.report["extra_travel_min"] = max(0, round((cost - auto[0]) / 60))
                return self.pref_id
        self.report["reason"] = reason
        if auto is None:
            return None
        _, A, sched, seq = auto
        A.seq, A.sched, A.sched_ok, A.touched = seq, sched, True, True
        return A.k.staff_id


class _PlannerProxy:
    """Подменяет apply_event только для одного NEW_ORDER; остальное — исходный планировщик."""

    def __init__(self, base, preferred: str, order_id: str, report: dict) -> None:
        self.base, self.preferred, self.order_id, self.report = base, preferred, order_id, report

    def __getattr__(self, name):
        return getattr(self.base, name)

    def apply_event(self, ref, matrix, day, event):
        if event.type != "NEW_ORDER" or not hasattr(self.base, "traffic"):
            return self.base.apply_event(ref, matrix, day, event)  # заглушка: пожелание не применяется
        update = _PreferredEventPlanner(ref, matrix, day, event, traffic=self.base.traffic,
                                        preferred=self.preferred, order_id=self.order_id, report=self.report).run()
        self.base.last_event_stats = update.stats
        return update


# ---------------------------------------------------------------- запросы

class SlotIn(BaseModel):
    start: str = Field(description="HH:MM (местное время дня)")
    end: str = Field(description="HH:MM")


class CreateOrderRequest(BaseModel):
    building_id: str
    work_type: str
    slot: Optional[SlotIn] = None  # None — ближайшее стандартное окно
    is_repeat: bool = False
    preferred_staff_id: Optional[str] = None


def _hhmm(day: dt.date, value: str, field: str) -> dt.datetime:
    m = re.fullmatch(r"([01]?\d|2[0-3]):([0-5]\d)", value.strip())
    if not m:
        raise ApiError(400, "BAD_REQUEST", f"Поле slot.{field}: ожидается ЧЧ:ММ",
                       [{"field": f"slot.{field}", "message": "ожидается ЧЧ:ММ"}])
    return dt.datetime.combine(day, dt.time(int(m.group(1)), int(m.group(2))), tzinfo=TZ)


def _pick_slot(day: dt.date, now: dt.datetime, slot: SlotIn | None, wt) -> tuple[dt.datetime, dt.datetime]:
    if slot is None:
        for h in SLOT_HOURS:
            start = dt.datetime.combine(day, dt.time(h), tzinfo=TZ)
            if start - now >= NEAREST_LEAD:
                return start, start + SLOT_LEN
        raise ApiError(422, "NO_SLOT_LEFT",
                       f"На сегодня стандартных окон не осталось (модельное время {now:%H:%M}); задайте окно вручную",
                       [{"field": "slot", "message": "нет ближайшего окна"}])
    start, end = _hhmm(day, slot.start, "start"), _hhmm(day, slot.end, "end")
    if end <= start:
        raise ApiError(422, "INVALID_SLOT", "Конец окна должен быть позже начала",
                       [{"field": "slot.end", "message": "раньше начала"}])
    latest = _latest_start(start, end, wt.max_start_delay_min)
    if latest - now < MIN_LEAD:
        raise ApiError(422, "SLOT_IN_PAST",
                       f"Окно {start:%H:%M}–{end:%H:%M} уже не успеть: крайнее начало {latest:%H:%M}, "
                       f"модельное время {now:%H:%M}",
                       [{"field": "slot.start", "message": "окно уже прошло"}])
    return start, end


# ---------------------------------------------------------------- routes

@router.get("/catalog")
def catalog(
    request: Request,
    q: str = Query("", max_length=100),
    work_type: str | None = None,
    limit: int = Query(SEARCH_LIMIT, ge=1, le=SEARCH_LIMIT),
) -> dict:
    """Справочники для формы нового наряда: типы работ, дома по подстроке адреса, сотрудники."""
    svc = _service(request)
    ref = svc.store.reference
    if ref is None:
        raise ApiError(409, "REFERENCE_NOT_LOADED", "Справочники не загружены: откройте приложение или GET /ui/demo/load")
    demo = _demo(request)
    absent: set[str] = set()
    if demo is not None:
        day = svc.store.days.get(dt.date.fromisoformat(demo["date"]))
        absent = set(day.absent_staff_ids) if day is not None else set()

    if work_type is not None and work_type not in ref.work_types:
        raise ApiError(422, "UNKNOWN_WORK_TYPE", f"Тип работ {work_type} не найден",
                       [{"field": "work_type", "message": "нет в справочнике"}])
    needle = " ".join(q.lower().split())
    found, total = [], 0
    for b in sorted(ref.buildings.values(), key=lambda x: x.address):
        if needle and needle not in " ".join(b.address.lower().split()) and needle != b.building_id.lower():
            continue
        techs = set(_node_techs(ref, b.building_id).values())
        compatible = [c for c in ref.work_types if _compatible(c, b, techs)]
        if work_type is not None and work_type not in compatible:
            continue
        total += 1
        if len(found) < limit:
            found.append({"building_id": b.building_id, "address": b.address, "lat": b.lat, "lon": b.lon,
                          "building_type": b.building_type, "overhead_line": b.overhead_line,
                          "technologies": sorted(techs), "work_types": compatible})
    staff = [{"staff_id": s.staff_id, "display_name": _staff_name(s.staff_id, s.display_name),
              "profiles": list(s.profiles), "technologies": list(s.technologies),
              "height_permit": s.height_permit, "on_shift": s.staff_id not in absent}
             for s in ref.staff.values()]
    work_types = [{"code": w.code, "name": w.name, "profiles": list(w.profiles), "duration_min": w.duration_min,
                   "pgp_relevant": w.pgp_relevant, "repeat_allowed": w.repeat_deadline_value is not None}
                  for w in ref.work_types.values()]
    return {"work_types": work_types, "buildings": found, "buildings_total": total,
            "buildings_count": len(ref.buildings), "staff": staff}


@router.post("/create")
def create_order(body: CreateOrderRequest, request: Request) -> dict:
    svc, demo, date = _require_day(request)
    report: dict = {}
    with svc.store.lock:
        ref = svc.store.reference
        day = svc._day(dt.date.fromisoformat(date))
        now = day.model_time
        b = ref.buildings.get(body.building_id)
        if b is None:
            raise ApiError(422, "UNKNOWN_BUILDING", f"Дом {body.building_id} не найден",
                           [{"field": "building_id", "message": "нет в справочнике"}])
        wt = ref.work_types.get(body.work_type)
        if wt is None:
            raise ApiError(422, "UNKNOWN_WORK_TYPE", f"Тип работ {body.work_type} не найден",
                           [{"field": "work_type", "message": "нет в справочнике"}])
        nodes = _node_techs(ref, b.building_id)
        if not nodes:
            raise ApiError(422, "BUILDING_NOT_ON_NODE", f"Дом {b.address} не подключён ни к одному узлу доступа")
        if not _compatible(wt.code, b, set(nodes.values())):
            raise ApiError(422, "WORK_TYPE_NOT_APPLICABLE",
                           f"«{wt.name}» не выполняется по адресу {b.address} "
                           f"({'МКД' if b.building_type == 'MKD' else 'частный дом'}, {'/'.join(sorted(set(nodes.values())))})",
                           [{"field": "work_type", "message": "не подходит дому"}])
        if body.preferred_staff_id is not None and body.preferred_staff_id not in ref.staff:
            raise ApiError(422, "UNKNOWN_STAFF", f"Неизвестный сотрудник {body.preferred_staff_id}",
                           [{"field": "preferred_staff_id", "message": "нет в справочнике"}])
        need = _required_tech(wt.code)
        node_id = next((n for n, t in nodes.items() if need is None or t == need), next(iter(nodes)))
        tech = nodes[node_id]
        start, end = _pick_slot(day.date, now, body.slot, wt)
        is_repeat = body.is_repeat and wt.repeat_deadline_value is not None
        latest = _latest_start(start, end, wt.max_start_delay_min)
        deadline = _deadline(wt, now, latest, is_repeat)
        if latest + dt.timedelta(minutes=wt.duration_min) > deadline:
            raise ApiError(422, "DEADLINE_TOO_EARLY", "Контрольный срок наступает раньше конца визита в этом окне")
        order_id = _next_order_id(day, demo)
        order = {
            "order_id": order_id, "work_type_code": wt.code, "technology": tech, "building_id": b.building_id,
            "address": b.address, "lat": b.lat, "lon": b.lon, "building_type": b.building_type,
            "requires_height": b.building_type == "PRIVATE" and b.overhead_line, "access_node_id": node_id,
            "registered_at": now.isoformat(), "slot": {"start": start.isoformat(), "end": end.isoformat()},
            "latest_start": latest.isoformat(), "deadline": deadline.isoformat(),
            "required_equipment": _equipment(wt.code, tech), "is_repeat": is_repeat, "previous_order_id": None,
        }
        event = {"event_id": _next_event_id(day), "type": "NEW_ORDER", "timestamp": now.isoformat(), "order": order}
        raw = json.dumps(event, ensure_ascii=False).encode("utf-8")
        if body.preferred_staff_id:
            base = svc.planner
            svc.planner = _PlannerProxy(base, body.preferred_staff_id, order_id, report)
            try:
                res = svc.post_event(date, raw)
            finally:
                svc.planner = base
        else:
            res = svc.post_event(date, raw)
        demo.setdefault("order_info", {})[order_id] = order
        plan = svc.get_plan(date)

    route = next(((r, s) for r in plan.routes for s in r.stops if s.type == StopType.VISIT and s.order_id == order_id),
                 None)
    unassigned = next((u for u in plan.unassigned if u.order_id == order_id), None)
    group = next((g for g in plan.pgp_groups if order_id in g.order_ids), None)
    out: dict = {
        "accepted": res.accepted, "order_id": order_id, "order": order, "work_type_name": wt.name,
        "plan_version": res.plan_version, "as_of": _iso(res.as_of), "event": _event_item(res, event),
        "warnings": _warnings(res, order_id), "assignment": None, "unassigned": None, "pgp": None, "preferred": None,
    }
    if route is not None:
        r, s = route
        staff = ref.staff.get(r.staff_id)
        out["outcome"] = "ASSIGNED"
        out["assignment"] = {"staff_id": r.staff_id, "staff_name": _staff_name(r.staff_id, staff.display_name if staff else None),
                             "eta": _iso(s.arrival), "planned_start": _iso(s.start), "planned_end": _iso(s.end)}
    elif group is not None:
        out["outcome"] = "PGP"
        out["pgp"] = {"group_id": group.group_id, "access_node_id": group.access_node_id, "comment": group.comment,
                      "order_ids": list(group.order_ids),
                      "message": "Похоже на групповое повреждение (ПГП): наряд передан в станционное подразделение, "
                                 "выезд бригады не планируется"}
    else:
        out["outcome"] = "UNASSIGNED"
        if unassigned is not None:
            out["unassigned"] = unassigned.model_dump(mode="json")
    if body.preferred_staff_id:
        sid = body.preferred_staff_id
        st = ref.staff[sid]
        name = _staff_name(sid, st.display_name) or sid
        honored = out["assignment"] is not None and out["assignment"]["staff_id"] == sid
        if honored:
            extra = report.get("extra_travel_min")
            msg = f"Пожелание учтено: исполнитель — {name}" + (f" (в пути на {extra} мин больше, чем при автоназначении)"
                                                         if extra else "")
        elif group is not None:
            msg = "Пожелание не применяется: наряд ушёл в группу ПГП"
        elif report.get("reason"):
            msg = f"Пожелание не исполнено: {report['reason']}" + (
                "; назначен автоматически" if out["assignment"] else "")
        elif report.get("placed"):
            msg = "Пожелание не исполнено: вставка отклонена проверкой плана"
        else:
            msg = "Пожелание не проверено: не хватило бюджета расчёта события"
        out["preferred"] = {"staff_id": sid, "staff_name": name, "honored": honored, "message": msg}
    return out


@router.post("/{order_id}/cancel")
def cancel_order(order_id: str, request: Request) -> dict:
    svc, _demo_state, date = _require_day(request)
    with svc.store.lock:
        day = svc._day(dt.date.fromisoformat(date))
        if order_id not in day.orders:
            raise ApiError(404, "UNKNOWN_ORDER", f"Наряд {order_id} не найден в дне {date}")
        if order_id in day.cancelled:
            raise ApiError(409, "ORDER_ALREADY_CANCELLED", f"Наряд {order_id} уже отменён")
        now = day.model_time
        before = None
        for r in day.plan.routes:
            for s in r.stops:
                if s.type == StopType.VISIT and s.order_id == order_id:
                    before = (r.staff_id, s)
        if before is not None and before[1].start < now:
            sid, s = before
            ref = svc.store.reference
            name = _staff_name(sid, ref.staff[sid].display_name if sid in ref.staff else None) or sid
            done = s.end <= now
            raise ApiError(409, "VISIT_STARTED",
                           f"Визит уже {'выполнен' if done else 'начат'} ({name}, начало {s.start:%H:%M}): "
                           "по контракту отменить можно только наряд, визит по которому ещё не начат")
        event = {"event_id": _next_event_id(day), "type": "ORDER_CANCELLED", "timestamp": now.isoformat(),
                 "order_id": order_id, "reason": "CUSTOMER_REQUEST"}
        res = svc.post_event(date, json.dumps(event, ensure_ascii=False).encode("utf-8"))
        plan = svc.get_plan(date)
        ref = svc.store.reference
    cancelled = any(c.order_id == order_id for c in plan.cancelled)
    freed = None
    if before is not None:
        sid = before[0]
        freed = {"staff_id": sid, "staff_name": _staff_name(sid, ref.staff[sid].display_name if sid in ref.staff else None)}
    summary = f"{order_id} отменён"
    if freed:
        summary += f", маршрут {freed['staff_name'] or freed['staff_id']} пересчитан"
    if len(res.changed_staff_ids) > 1:
        summary += f"; изменено маршрутов: {len(res.changed_staff_ids)}"
    return {"accepted": res.accepted, "cancelled": cancelled, "order_id": order_id, "summary": summary,
            "freed": freed, "plan_version": res.plan_version, "as_of": _iso(res.as_of),
            "changed_staff_ids": res.changed_staff_ids, "message": res.message,
            "warnings": _warnings(res), "event": _event_item(res, event)}
````

## File: backend/app/scenarios.py
````python
"""Сценарный анализ для интерфейса диспетчера (/ui/scenario/*), вне контракта /api/v1.

Сценарии работают на копии данных загруженного дня и ничего не меняют в Store:
снимок (справочники, матрица, наряды, отсутствующие, текущий план) берётся под
store.lock, расчёт идёт вне блокировки отдельным экземпляром того же планировщика.

База сравнения — первоначальный план дня:
* если план дня ещё версии 1 (событий не было) — это и есть текущий план, он не пересчитывается;
* если события уже были, текущий план отражает их, поэтому база строится заново на копии
  дня «как на момент загрузки» (наряды с arrived_at ≤ now, без отмен и событий) с тем же
  бюджетом, что и сценарий, — так база и сценарий сравнимы.

Сценарий «пробки по часам» (/traffic) базу не использует: строит два плана на копии
(без коэффициентов и с ними) и исполняет оба в пробках (traffic_replay).
"""

from __future__ import annotations

import dataclasses
import math
import random
import statistics
import threading
import time
from typing import Literal, Optional

from fastapi import APIRouter, Request
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, Field

from .errors import ApiError
from .models import Equipment, Plan, Profile, StopType
from .planner.context import DayContext, hour_factors, traffic_enabled
from .planner.validator import validate_plan
from .service import _parse, _parse_date
from .staff_directory import display_name, remember_recommendation
from .state import DayState, MatrixState, ReferenceState

router = APIRouter(prefix="/ui/scenario", tags=["ui"])

EQUIPMENT_KINDS = ("ONT", "ROUTER", "STB")
BIG_BACKPACK = 99  # «бесконечный» рюкзак для оценки запаса
_busy = threading.Lock()  # один сценарий за раз: расчёт занимает CPU на десятки секунд


class StaffCutRequest(BaseModel):
    date: Optional[str] = None
    remove_count: int = Field(ge=0, le=15)
    strategy: str = Field(
        default="random",
        pattern=r"^(random|least_loaded|by_profile:(INSTALL|LTP3)|by_service_center:SC-[0-9A-Za-z_-]+)$",
    )
    seed: Optional[int] = None
    budget_sec: float = Field(default=15.0, ge=1, le=120)


class TrafficRequest(BaseModel):
    date: Optional[str] = None
    budget_sec: float = Field(default=15.0, ge=1, le=120)


class EquipmentRequest(BaseModel):
    date: Optional[str] = None
    mode: Literal["no_restock"] = "no_restock"
    budget_sec: float = Field(default=15.0, ge=1, le=120)


@dataclasses.dataclass
class _Snapshot:
    ref: ReferenceState
    matrix: MatrixState
    day: DayState  # день «как при загрузке», без плана и событий
    base_plan: Optional[Plan]  # текущий план, если он версии 1; иначе None — базу надо строить


# ---------------------------------------------------------------- снимок и расчёт

def _resolve_date(request: Request, value: Optional[str]) -> str:
    if value:
        return value
    demo = getattr(request.app.state, "ui_demo", None)
    if demo:
        return demo["date"]
    days = request.app.state.service.store.days
    if len(days) == 1:
        return next(iter(days)).isoformat()
    raise ApiError(409, "PLAN_NOT_BUILT", "День не загружен: сначала GET /ui/demo/load")


def _snapshot(svc, date_str: str) -> _Snapshot:
    day_date = _parse_date(date_str)
    with svc.store.lock:
        ref, matrix = svc._reference(), svc._matrix()
        day = svc._day(day_date)
        if day.plan is None:
            raise ApiError(409, "PLAN_NOT_BUILT", f"План на {day_date} ещё не построен")
        orders = {oid: o for oid, o in day.orders.items() if day.arrived_at.get(oid, day.now) <= day.now}
        initial = DayState(
            date=day.date,
            now=day.now,
            orders=orders,
            absent_staff_ids=set(day.absent_staff_ids),
            arrived_at={oid: day.now for oid in orders},
        )
        fresh = day.plan_version == 1 and not day.events
        base_plan = day.plan.model_copy(deep=True) if fresh else None
    return _Snapshot(ref=ref, matrix=matrix, day=initial, base_plan=base_plan)


def _time_limit(budget_sec: float) -> int:
    """time_limit_sec, при котором внутренний бюджет планировщика ≈ budget_sec
    (он берёт min(PLANNER_DAY_BUDGET_SEC, 0,8·limit − 3)), как _plan_time_limit в ui_api."""
    return max(1, min(300, math.ceil((budget_sec + 3.0) / 0.8)))


def _solve(svc, ref: ReferenceState, matrix: MatrixState, day: DayState, budget_sec: float,
           traffic: Optional[bool] = None) -> Plan:
    planner = type(svc.planner)()  # отдельный экземпляр: last_stats живого планировщика не трогаем
    if hasattr(planner, "seed") and hasattr(svc.planner, "seed"):
        planner.seed = svc.planner.seed
    if hasattr(planner, "traffic"):
        planner.traffic = traffic if traffic is not None else getattr(svc.planner, "traffic", None)
    return planner.build_plan(ref, matrix, day.clone(), time_limit_sec=_time_limit(budget_sec))


def _base_plan(svc, snap: _Snapshot, budget_sec: float) -> tuple[Plan, str]:
    if snap.base_plan is not None:
        return snap.base_plan, "current_plan_v1"
    return _solve(svc, snap.ref, snap.matrix, snap.day, budget_sec), "rebuilt_initial"


# ---------------------------------------------------------------- метрики

def _visits(plan: Plan) -> dict[str, str]:
    return {s.order_id: r.staff_id for r in plan.routes for s in r.stops if s.type == StopType.VISIT}


def metrics(plan: Plan, orders_total: int) -> dict:
    """Метрики плана, как в tools/bench/run_day.py: доля нарядов в слот = визиты / все наряды дня."""
    visits = len(_visits(plan))
    reasons: dict[str, int] = {}
    for u in plan.unassigned:
        reasons[str(u.reason_code)] = reasons.get(str(u.reason_code), 0) + 1
    used = [r for r in plan.routes if r.totals and r.totals.visits > 0]
    utils = [r.totals.utilization for r in used]
    mean = statistics.fmean(utils) if utils else 0.0
    cv = statistics.pstdev(utils) / mean if len(utils) > 1 and mean > 0 else 0.0
    travel = sum(r.totals.travel_sec for r in plan.routes if r.totals)
    return {
        "orders_total": orders_total,
        "visits": visits,
        "unassigned": len(plan.unassigned),
        "unassigned_by_reason": dict(sorted(reasons.items())),
        "pgp_orders": sum(len(g.order_ids) for g in plan.pgp_groups),
        "in_slot_share": round(visits / orders_total, 4) if orders_total else 0.0,
        "travel_h": round(travel / 3600, 2),
        "staff_used": len(used),
        "util_mean": round(mean, 4),
        "util_cv": round(cv, 4),
        "restocks": sum(1 for r in plan.routes for s in r.stops if s.type == StopType.RESTOCK),
    }


def _delta(base: dict, other: dict) -> dict:
    return {
        k: round(other[k] - v, 4)
        for k, v in base.items()
        if isinstance(v, (int, float)) and not isinstance(v, bool) and k != "orders_total"
    }


# ---------------------------------------------------------------- выбор сотрудников

def _pick_staff(ref: ReferenceState, snap: _Snapshot, base: Plan, req: StaffCutRequest) -> list[str]:
    on_shift = sorted(sid for sid in ref.staff if sid not in snap.day.absent_staff_ids)
    rng = random.Random(req.seed if req.seed is not None else 1)
    strategy = req.strategy
    if strategy == "least_loaded":
        util = {r.staff_id: (r.totals.utilization if r.totals else 0.0) for r in base.routes}
        pool = sorted(on_shift, key=lambda sid: (util.get(sid, 0.0), sid))
        chosen = pool[: req.remove_count]
    else:
        if strategy == "random":
            pool = on_shift
        elif strategy.startswith("by_profile:"):
            profile = Profile(strategy.split(":", 1)[1])
            pool = [sid for sid in on_shift if profile in ref.staff[sid].profiles]
        else:
            sc = strategy.split(":", 1)[1]
            if sc not in ref.service_centers:
                raise ApiError(422, "UNKNOWN_SERVICE_CENTER", f"СЦ {sc} не найден",
                               [{"field": "strategy", "message": f"СЦ {sc} не найден"}])
            pool = [sid for sid in on_shift if ref.staff[sid].service_center_id == sc]
        if len(pool) < req.remove_count:
            chosen = []
        else:
            chosen = rng.sample(pool, req.remove_count)
    if len(chosen) < req.remove_count:
        raise ApiError(422, "NOT_ENOUGH_STAFF",
                       f"По стратегии {strategy} на смене только {len(pool)} сотрудников, "
                       f"а убрать нужно {req.remove_count}",
                       [{"field": "remove_count", "message": f"не больше {len(pool)}"}])
    return sorted(chosen)


# ---------------------------------------------------------------- сценарии

def run_staff_cut(svc, date_str: str, req: StaffCutRequest) -> dict:
    started = time.perf_counter()
    snap = _snapshot(svc, date_str)
    ref = snap.ref
    base, base_source = _base_plan(svc, snap, req.budget_sec)
    removed = _pick_staff(ref, snap, base, req)

    if removed:
        day = snap.day.clone()
        day.absent_staff_ids |= set(removed)
        scenario = _solve(svc, ref, snap.matrix, day, req.budget_sec)
    else:
        scenario = base  # никого не убрали — сценарий совпадает с базой, пересчёт не нужен

    total = len(snap.day.orders)
    m_base, m_scen = metrics(base, total), metrics(scenario, total)
    base_vis, scen_vis = _visits(base), _visits(scenario)
    unassigned = {u.order_id: u for u in scenario.unassigned}
    pgp = {oid for g in scenario.pgp_groups for oid in g.order_ids}
    affected = []
    for oid, sid in base_vis.items():
        if oid in scen_vis:
            continue
        o = snap.day.orders.get(oid)
        u = unassigned.get(oid)
        affected.append({
            "order_id": oid,
            "base_staff_id": sid,
            "base_staff_removed": sid in removed,
            "work_type_code": o.work_type_code if o else None,
            "is_repeat": bool(o and o.is_repeat),
            "slot": o.slot.model_dump(mode="json") if o else None,
            "address": o.address if o else None,
            "reason_code": str(u.reason_code) if u else ("PGP" if oid in pgp else None),
            "reason_text": u.reason_text if u else None,
        })
    affected.sort(key=lambda a: (a["slot"] or {}).get("start", ""))
    gained = sorted(oid for oid in scen_vis if oid not in base_vis)
    reassigned = sum(1 for oid, sid in base_vis.items() if oid in scen_vis and scen_vis[oid] != sid)

    base_routes = {r.staff_id: r for r in base.routes}
    removed_info = []
    for sid in removed:
        s = ref.staff[sid]
        r = base_routes.get(sid)
        removed_info.append({
            "staff_id": sid,
            "display_name": display_name(sid, s.display_name),
            "profiles": [str(p) for p in s.profiles],
            "service_center_id": s.service_center_id,
            "base_visits": r.totals.visits if r and r.totals else 0,
        })

    return {
        "date": date_str,
        "strategy": req.strategy,
        "remove_count": req.remove_count,
        "seed": req.seed if req.seed is not None else 1,
        "budget_sec": req.budget_sec,
        "base_source": base_source,
        "removed": removed_info,
        "base": m_base,
        "scenario": m_scen,
        "delta": _delta(m_base, m_scen),
        "affected_orders": affected,
        "newly_routed": gained,
        "reassigned": reassigned,
        "time_sec": round(time.perf_counter() - started, 2),
    }


def _percentile(values: list[int], p: float) -> int:
    """Перцентиль по ближайшему рангу (целые штуки АО)."""
    if not values:
        return 0
    s = sorted(values)
    return s[max(0, math.ceil(p * len(s)) - 1)]


def run_equipment(svc, date_str: str, req: EquipmentRequest) -> dict:
    started = time.perf_counter()
    snap = _snapshot(svc, date_str)
    ref = snap.ref
    base, base_source = _base_plan(svc, snap, req.budget_sec)

    big = Equipment(ONT=BIG_BACKPACK, ROUTER=BIG_BACKPACK, STB=BIG_BACKPACK)
    big_ref = dataclasses.replace(
        ref,
        staff={sid: s.model_copy(update={"equipment_start": big, "equipment_capacity": big})
               for sid, s in ref.staff.items()},
    )
    free = _solve(svc, big_ref, snap.matrix, snap.day, req.budget_sec)

    # расход АО за смену по каждому сотруднику в плане «без заездов»
    need: dict[str, dict[str, int]] = {}
    for r in free.routes:
        used = {k: 0 for k in EQUIPMENT_KINDS}
        n = 0
        for s in r.stops:
            if s.type != StopType.VISIT:
                continue
            o = snap.day.orders.get(s.order_id)
            if o is None:
                continue
            n += 1
            for k in EQUIPMENT_KINDS:
                used[k] += getattr(o.required_equipment, k)
        if n:
            need[r.staff_id] = used

    recommended = []
    for profile in (Profile.INSTALL, Profile.LTP3):
        staff = [s for s in ref.staff.values() if profile in s.profiles and s.staff_id not in snap.day.absent_staff_ids]
        if not staff:
            continue
        rows = [need[s.staff_id] for s in staff if s.staff_id in need]
        item = {"profile": str(profile), "staff_on_shift": len(staff), "staff_with_visits": len(rows)}
        over = 0
        for k in EQUIPMENT_KINDS:
            vals = [row[k] for row in rows]
            cap = max(getattr(s.equipment_capacity, k) for s in staff)
            item[k] = {
                "p50": _percentile(vals, 0.5),
                "p90": _percentile(vals, 0.9),
                "max": max(vals) if vals else 0,
                "current_start_avg": round(statistics.fmean(getattr(s.equipment_start, k) for s in staff), 1),
                "capacity": cap,
            }
        for s in staff:
            row = need.get(s.staff_id)
            if row and any(row[k] > getattr(s.equipment_start, k) for k in EQUIPMENT_KINDS):
                over += 1
        item["staff_exceeding_current_start"] = over
        recommended.append(item)

    total = len(snap.day.orders)
    m_now, m_free = metrics(base, total), metrics(free, total)
    remember_recommendation(date_str, recommended)  # подсказка «рекомендуемый рюкзак» в справочнике
    return {
        "date": date_str,
        "mode": req.mode,
        "budget_sec": req.budget_sec,
        "base_source": base_source,
        "current": m_now,
        "no_restock": m_free,
        "delta": _delta(m_now, m_free),
        "visits_gained": m_free["visits"] - m_now["visits"],
        "travel_saved_h": round(m_now["travel_h"] - m_free["travel_h"], 2),
        "recommended": recommended,
        "time_sec": round(time.perf_counter() - started, 2),
    }


# ---------------------------------------------------------------- пробки по часам

def traffic_replay(ref: ReferenceState, matrix: MatrixState, day: DayState, plan: Plan) -> dict:
    """Исполнение плана в пробках: тот же порядок остановок, переезды — ctx.tt по часу выезда.

    Остановка начинается не раньше запланированного начала и не раньше приезда; визит,
    начатый позже latest_start, — позднее прибытие (то, о чём предупреждал бы LATE_RISK).
    Для плана, построенного с коэффициентами, расписание совпадает с планом и опозданий нет.
    """
    ctx = DayContext(ref, matrix, day, list(day.orders.values()), day.now, traffic=True)
    travel = late = late_sec = on_time = 0
    for r in plan.routes:
        stops = r.stops
        if not any(s.type == StopType.VISIT for s in stops):
            continue
        t = ctx.to_sec(stops[0].end)
        loc = ctx.point_of[stops[0].location_id]
        for s in stops[1:]:
            st0, en0 = ctx.to_sec(s.start), ctx.to_sec(s.end)
            if s.type == StopType.BREAK:
                t = max(t, st0) + (en0 - st0)
                continue
            pt = ctx.point_of[s.location_id]
            tr = ctx.tt(loc, pt, t)
            travel += tr
            st = max(t + tr, st0)
            if s.type == StopType.VISIT and s.order_id in ctx.order_by_id:
                o = ctx.order_by_id[s.order_id]
                if st > o.b:
                    late += 1
                    late_sec += st - o.b
                else:
                    on_time += 1
            t, loc = st + (en0 - st0), pt
    return {"travel_traffic_h": round(travel / 3600, 2), "late_visits": late,
            "late_min": round(late_sec / 60), "on_time_visits": on_time}


def run_traffic(svc, date_str: str, req: TrafficRequest) -> dict:
    started = time.perf_counter()
    snap = _snapshot(svc, date_str)
    ref, matrix, day = snap.ref, snap.matrix, snap.day
    total = len(day.orders)
    out = {}
    for name, traffic in (("off", False), ("on", True)):
        t0 = time.perf_counter()
        plan = _solve(svc, ref, matrix, day, req.budget_sec, traffic=traffic)
        m = metrics(plan, total)
        m.update(traffic_replay(ref, matrix, day, plan))
        m["in_slot_share_traffic"] = round(m["on_time_visits"] / total, 4) if total else 0.0
        m["violations"] = len(validate_plan(plan, ref, matrix, day))
        m["time_sec"] = round(time.perf_counter() - t0, 2)
        out[name] = m
    return {
        "date": date_str,
        "budget_sec": req.budget_sec,
        "traffic_env": traffic_enabled(),
        "factors": [{"hour": h, "factor": f} for h, f in enumerate(hour_factors(matrix)) if f > 1.0],
        "off": out["off"],
        "on": out["on"],
        "delta": _delta(out["off"], out["on"]),
        "time_sec": round(time.perf_counter() - started, 2),
    }


# ---------------------------------------------------------------- маршруты

async def _run(request: Request, model, fn) -> dict:
    req = _parse(model.model_validate_json, (await request.body()) or b"{}")
    svc = request.app.state.service
    date_str = _resolve_date(request, req.date)
    _parse_date(date_str)
    if not _busy.acquire(blocking=False):
        raise ApiError(409, "SCENARIO_BUSY", "Уже идёт расчёт другого сценария, повторите позже")
    try:
        return await run_in_threadpool(fn, svc, date_str, req)
    finally:
        _busy.release()


@router.post("/staff-cut")
async def staff_cut(request: Request) -> dict:
    return await _run(request, StaffCutRequest, run_staff_cut)


@router.post("/equipment")
async def equipment(request: Request) -> dict:
    return await _run(request, EquipmentRequest, run_equipment)


@router.post("/traffic")
async def traffic(request: Request) -> dict:
    return await _run(request, TrafficRequest, run_traffic)
````

## File: backend/app/service.py
````python
"""Логика API: справочники, дни, планы и события.

Все публичные методы принимают сырое тело запроса (bytes) и возвращают pydantic-модель.
Ошибки поднимаются как ApiError; при ошибке состояние не меняется: изменения
применяются к клону дня и коммитятся только после успешной обработки.
"""

from __future__ import annotations

import time
from datetime import date

from pydantic import TypeAdapter, ValidationError

from .errors import ApiError
from .journal import journal
from .models import (
    Building,
    DayInput,
    Event,
    EventResult,
    LoadResult,
    Order,
    Plan,
    PlanRequest,
    ReferenceData,
    TravelMatrix,
)
from .planner import Planner
from .state import DayState, MatrixState, ReferenceState
from .store import Store

MAX_BODY_BYTES = 100 * 1024 * 1024
EVENT_ADAPTER: TypeAdapter[Event] = TypeAdapter(Event)


# ---------------------------------------------------------------- parsing

def _path_of(loc: tuple) -> str:
    path = ""
    for part in loc:
        if isinstance(part, int):
            path += f"[{part}]"
        else:
            path = f"{path}.{part}" if path else str(part)
    return path


def _details(exc: ValidationError) -> list[dict]:
    result = []
    for err in exc.errors(include_url=False, include_input=False, include_context=False):
        result.append({"field": _path_of(err["loc"]) or None, "message": err["msg"]})
    return result


def _parse(parse, raw: bytes):
    if len(raw) > MAX_BODY_BYTES:
        raise ApiError(400, "BAD_REQUEST", "Тело запроса больше 100 МБ")
    try:
        return parse(raw)
    except ValidationError as exc:
        details = _details(exc)
        first = details[0] if details else {"field": None, "message": "некорректное тело"}
        where = first["field"] or "тело запроса"
        raise ApiError(400, "BAD_REQUEST", f"Поле {where}: {first['message']}", details)


def _parse_date(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError:
        raise ApiError(
            400,
            "BAD_REQUEST",
            f"Некорректная дата '{value}', ожидается YYYY-MM-DD",
            [{"field": "date", "message": "ожидается YYYY-MM-DD"}],
        ) from None


def _unprocessable(code: str, message: str, field: str | None = None) -> ApiError:
    details = [{"field": field, "message": message}] if field else []
    return ApiError(422, code, message, details)


def _conflict(code: str, message: str) -> ApiError:
    return ApiError(409, code, message)


# ---------------------------------------------------------------- reference

def _build_reference(data: ReferenceData) -> ReferenceState:
    def unique(kind: str, ids: list[str]) -> None:
        if len(set(ids)) != len(ids):
            raise _unprocessable("DUPLICATE_ID", f"Повторяющиеся идентификаторы в {kind}")

    unique("service_centers", [sc.service_center_id for sc in data.service_centers])
    unique("staff", [s.staff_id for s in data.staff])
    unique("access_nodes", [n.access_node_id for n in data.access_nodes])
    unique("work_types", [w.code for w in data.work_types])

    sc_ids = {sc.service_center_id for sc in data.service_centers}
    for s in data.staff:
        if s.service_center_id not in sc_ids:
            raise _unprocessable(
                "UNKNOWN_SERVICE_CENTER",
                f"СЦ {s.service_center_id} сотрудника {s.staff_id} не найден",
                "staff.service_center_id",
            )
        for field_name in ("ONT", "ROUTER", "STB"):
            if getattr(s.equipment_capacity, field_name) < getattr(s.equipment_start, field_name):
                raise _unprocessable(
                    "INVALID_EQUIPMENT",
                    f"equipment_capacity меньше equipment_start у {s.staff_id}",
                    "staff.equipment_capacity",
                )

    buildings: dict[str, Building] = {}
    building_nodes: dict[str, set[str]] = {}
    for node in data.access_nodes:
        for b in node.buildings:
            if b.zone_service_center_id and b.zone_service_center_id not in sc_ids:
                raise _unprocessable(
                    "UNKNOWN_SERVICE_CENTER",
                    f"zone_service_center_id {b.zone_service_center_id} дома {b.building_id} не найден",
                )
            known = buildings.get(b.building_id)
            if known is None:
                buildings[b.building_id] = b
            elif known.model_dump() != b.model_dump():
                raise _unprocessable(
                    "BUILDING_CONFLICT",
                    f"Дом {b.building_id} указан в разных узлах с разными атрибутами",
                )
            building_nodes.setdefault(b.building_id, set()).add(node.access_node_id)

    return ReferenceState(
        service_centers={sc.service_center_id: sc for sc in data.service_centers},
        staff={s.staff_id: s for s in data.staff},
        access_nodes={n.access_node_id: n for n in data.access_nodes},
        buildings=buildings,
        work_types={w.code: w for w in data.work_types},
        building_nodes=building_nodes,
    )


# ---------------------------------------------------------------- service

class DispatchService:
    def __init__(self, store: Store, planner: Planner) -> None:
        self.store = store
        self.planner = planner

    # ------------------------------------------------ reference

    def put_reference(self, raw: bytes) -> LoadResult:
        data = _parse(ReferenceData.model_validate_json, raw)
        ref = _build_reference(data)
        with self.store.lock:
            self.store.reference = ref
            # Загрузка справочников удаляет все дни, планы и события.
            self.store.days.clear()
        return LoadResult(
            status="loaded",
            counts={
                "service_centers": len(ref.service_centers),
                "staff": len(ref.staff),
                "access_nodes": len(ref.access_nodes),
                "buildings": len(ref.buildings),
                "work_types": len(ref.work_types),
            },
        )

    def put_travel_matrix(self, raw: bytes) -> LoadResult:
        matrix = _parse(TravelMatrix.model_validate_json, raw)
        n = len(matrix.points)
        if len(matrix.durations_sec) != n or any(len(r) != n for r in matrix.durations_sec):
            raise _unprocessable("MATRIX_SHAPE_MISMATCH", f"durations_sec должна быть {n}x{n}", "durations_sec")
        if len(matrix.distances_m) != n or any(len(r) != n for r in matrix.distances_m):
            raise _unprocessable("MATRIX_SHAPE_MISMATCH", f"distances_m должна быть {n}x{n}", "distances_m")
        state = MatrixState(matrix=matrix, index={p: i for i, p in enumerate(matrix.points)})
        with self.store.lock:
            self.store.matrix = state
        return LoadResult(status="loaded", counts={"points": n})

    # ------------------------------------------------ days

    def put_day(self, date_str: str, raw: bytes) -> LoadResult:
        day_date = _parse_date(date_str)
        body = _parse(DayInput.model_validate_json, raw)
        if body.date != day_date:
            raise ApiError(
                400,
                "BAD_REQUEST",
                f"Дата в теле {body.date} не совпадает с датой пути {day_date}",
                [{"field": "date", "message": "не совпадает с путём"}],
            )

        with self.store.lock:
            ref = self._reference()
            orders: dict[str, Order] = {}
            for i, order in enumerate(body.orders):
                if order.order_id in orders:
                    raise _unprocessable("DUPLICATE_ORDER", f"Повтор наряда {order.order_id}", f"orders[{i}].order_id")
                self._check_order_refs(ref, order, f"orders[{i}]")
                orders[order.order_id] = order
            for staff_id in body.absent_staff_ids:
                if staff_id not in ref.staff:
                    raise _unprocessable("UNKNOWN_STAFF", f"Неизвестный сотрудник {staff_id}", "absent_staff_ids")

            day = DayState(
                date=day_date,
                now=body.now,
                orders=orders,
                absent_staff_ids=set(body.absent_staff_ids),
                arrived_at={oid: body.now for oid in orders},
            )
            self.store.days[day_date] = day

        journal.record_day(day_date, body.now, len(orders))
        return LoadResult(status="loaded", counts={"orders": len(orders)})

    def build_plan(self, date_str: str, raw: bytes | None) -> Plan:
        day_date = _parse_date(date_str)
        request = PlanRequest()
        if raw and raw.strip():
            request = _parse(PlanRequest.model_validate_json, raw)  # проверка time_limit_sec
        started = time.perf_counter()
        with self.store.lock:
            ref, matrix = self._reference(), self._matrix()
            self._check_matrix_coverage(ref, matrix)
            day = self._day(day_date)
            if day.events:
                raise _conflict("PLAN_ALREADY_ADJUSTED", "После первого события план нельзя построить заново")
            plan = self.planner.build_plan(ref, matrix, day, time_limit_sec=request.time_limit_sec)
            plan.plan_version = 1
            plan.based_on_event_id = None
            plan.as_of = day.now
            plan.computation_ms = int((time.perf_counter() - started) * 1000)
            day.plan = plan
            day.plan_version = 1
            day.model_time = day.now
        journal.record_plan(plan)
        return plan

    def get_plan(self, date_str: str) -> Plan:
        day_date = _parse_date(date_str)
        with self.store.lock:
            day = self._day(day_date)
            if day.plan is None:
                raise ApiError(404, "PLAN_NOT_FOUND", f"План на {day_date} ещё не построен")
            return day.plan.model_copy(deep=True)

    def post_event(self, date_str: str, raw: bytes) -> EventResult:
        day_date = _parse_date(date_str)
        event = _parse(EVENT_ADAPTER.validate_json, raw)
        started = time.perf_counter()

        with self.store.lock:
            ref, matrix = self._reference(), self._matrix()
            day = self._day(day_date)
            if day.plan is None:
                raise _conflict("PLAN_NOT_BUILT", "Сначала нужно построить план на день")

            if event.event_id in day.events:
                return EventResult(
                    event_id=event.event_id,
                    accepted=True,
                    duplicate=True,
                    plan_version=day.plan_version,
                    as_of=day.model_time,
                    processing_ms=0,
                    message="Событие уже принято ранее, состояние не изменено",
                )

            if event.timestamp < day.model_time:
                raise _conflict(
                    "EVENT_OUT_OF_ORDER",
                    f"timestamp {event.timestamp.isoformat()} раньше последнего события "
                    f"{day.model_time.isoformat()}",
                )

            candidate = day.clone()
            self._check_event(ref, candidate, event)
            update = self.planner.apply_event(ref, matrix, candidate, event)

            candidate.plan_version += 1
            new_plan = update.plan
            new_plan.plan_version = candidate.plan_version
            new_plan.based_on_event_id = event.event_id
            new_plan.as_of = event.timestamp
            new_plan.computation_ms = int((time.perf_counter() - started) * 1000)
            candidate.plan = new_plan
            candidate.model_time = event.timestamp
            candidate.events[event.event_id] = candidate.plan_version
            self.store.days[day_date] = candidate

        processing_ms = int((time.perf_counter() - started) * 1000)
        journal.record_event(day_date, event, new_plan, processing_ms)
        return EventResult(
            event_id=event.event_id,
            accepted=True,
            duplicate=False,
            plan_version=candidate.plan_version,
            as_of=candidate.model_time,
            processing_ms=processing_ms,
            changed_staff_ids=update.changed_staff_ids,
            new_alerts=update.new_alerts,
            message=getattr(update, "message", None),
        )

    # ------------------------------------------------ checks

    def _reference(self) -> ReferenceState:
        if self.store.reference is None:
            raise _conflict("REFERENCE_NOT_LOADED", "Сначала загрузите справочники (PUT /api/v1/reference)")
        return self.store.reference

    def _matrix(self) -> MatrixState:
        if self.store.matrix is None:
            raise _conflict("REFERENCE_NOT_LOADED", "Сначала загрузите матрицу времени в пути")
        return self.store.matrix

    def _day(self, day_date: date) -> DayState:
        day = self.store.days.get(day_date)
        if day is None:
            raise ApiError(404, "DAY_NOT_FOUND", f"День {day_date} не загружен")
        return day

    @staticmethod
    def _check_matrix_coverage(ref: ReferenceState, matrix: MatrixState) -> None:
        points = set(matrix.index)
        required = set(ref.service_centers) | set(ref.buildings)
        missing = sorted(required - points)
        if missing:
            raise _conflict(
                "REFERENCE_NOT_LOADED",
                f"Матрица не покрывает точки, например: {', '.join(missing[:5])}",
            )

    @staticmethod
    def _check_order_refs(ref: ReferenceState, order: Order, field: str) -> None:
        if order.building_id not in ref.buildings:
            raise _unprocessable("UNKNOWN_BUILDING", f"Дом {order.building_id} не найден", f"{field}.building_id")
        if order.access_node_id not in ref.access_nodes:
            raise _unprocessable(
                "UNKNOWN_ACCESS_NODE", f"Узел доступа {order.access_node_id} не найден", f"{field}.access_node_id"
            )
        if order.access_node_id not in ref.building_nodes.get(order.building_id, set()):
            raise _unprocessable(
                "BUILDING_NOT_ON_NODE",
                f"Дом {order.building_id} не обслуживается узлом {order.access_node_id}",
                f"{field}.access_node_id",
            )
        if order.work_type_code not in ref.work_types:
            raise _unprocessable(
                "UNKNOWN_WORK_TYPE", f"Тип работ {order.work_type_code} не найден", f"{field}.work_type_code"
            )

    def _check_event(self, ref: ReferenceState, day: DayState, event: Event) -> None:
        if event.type == "NEW_ORDER":
            if event.order.order_id in day.orders:
                raise _unprocessable("ORDER_ALREADY_EXISTS", f"Наряд {event.order.order_id} уже есть в дне", "order.order_id")
            self._check_order_refs(ref, event.order, "order")
            return
        if event.type == "TIME_TICK":
            return
        if event.type in ("VISIT_DELAYED", "ORDER_TRANSFERRED", "STAFF_UNAVAILABLE"):
            if event.staff_id not in ref.staff:
                raise _unprocessable("UNKNOWN_STAFF", f"Неизвестный сотрудник {event.staff_id}", "staff_id")
        if event.type == "STAFF_UNAVAILABLE":
            if event.until is not None and event.until <= event.timestamp:
                raise _unprocessable("INVALID_PERIOD", "until должен быть позже timestamp", "until")
            return
        if event.type in ("ORDER_CANCELLED", "VISIT_DELAYED", "ORDER_TRANSFERRED"):
            if event.order_id not in day.orders:
                raise _unprocessable("UNKNOWN_ORDER", f"Наряд {event.order_id} не найден в дне {day.date}", "order_id")
        if event.type == "ORDER_CANCELLED" and event.order_id in day.cancelled:
            raise _unprocessable("ORDER_ALREADY_CANCELLED", f"Наряд {event.order_id} уже отменён", "order_id")
````

## File: backend/app/staff_directory.py
````python
"""Справочник сотрудников с ФИО: /ui/directory/staff (вне контракта /api/v1).

Профиль человека (ФИО, телефон, должность, табельный номер, заметка) хранится на нашей
стороне в JSON-файле рядом с демо-данными (UI_DATA_DIR/staff_directory.json, путь можно
переопределить STAFF_DIRECTORY_FILE). Ключ — staff_id из staff.json. Postgres не нужен.

При первом запуске файла нет: профили генерируются детерминированно (seed по staff_id)
и сохраняются. Запись атомарная (временный файл + os.replace) под собственной блокировкой.
Если каталог только для чтения, справочник работает в памяти процесса (persisted=false).

Новый сотрудник (POST) сохраняется только в справочнике: в планировщик он попадёт при
пересборке дня, сам планировщик здесь не трогается.

«Рюкзак АО» (equipment_start / equipment_capacity) можно переопределить по staff_id: ключ
equipment_overrides в том же файле (старый файл без ключа читается как «без переопределений»).
Переопределения применяются только при загрузке демо-дня (/ui/demo/load) — /api/v1 отдаёт то,
что пришло в PUT reference. Значения в data/synth синтетические (tools/synth/generate.py).
"""

from __future__ import annotations

import json
import os
import random
import re
import threading
from functools import lru_cache
from pathlib import Path
from typing import Any, Literal, Optional

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator

from .errors import ApiError
from .service import _parse
from .ui_api import DATA

router = APIRouter(prefix="/ui/directory", tags=["ui-directory"])

SEED = 20261008
POSITIONS = {"LTP3": "Инженер ЛТП", "INSTALL": "Инсталлятор"}
_STAFF_ID = re.compile(r"^ST-([0-9]{3})$")

# ---------------------------------------------------------------- генерация ФИО

# (мужское имя, женское имя) — для пар не используются; отчества — от имени отца.
_MALE_FIRST = ["Александр", "Алексей", "Андрей", "Антон", "Артём", "Борис", "Вадим", "Василий", "Виктор",
               "Владимир", "Вячеслав", "Геннадий", "Денис", "Дмитрий", "Евгений", "Егор", "Иван", "Игорь",
               "Илья", "Кирилл", "Константин", "Максим", "Михаил", "Никита", "Николай", "Олег", "Павел",
               "Роман", "Руслан", "Сергей", "Станислав", "Юрий"]
_FEMALE_FIRST = ["Анастасия", "Анна", "Валентина", "Виктория", "Галина", "Дарья", "Евгения", "Екатерина",
                 "Елена", "Ирина", "Ксения", "Людмила", "Марина", "Мария", "Наталья", "Ольга", "Светлана",
                 "Татьяна", "Юлия"]
# Имя отца → (мужское отчество, женское отчество)
_PATRONYMIC = {
    "Александр": ("Александрович", "Александровна"), "Алексей": ("Алексеевич", "Алексеевна"),
    "Андрей": ("Андреевич", "Андреевна"), "Борис": ("Борисович", "Борисовна"),
    "Василий": ("Васильевич", "Васильевна"), "Виктор": ("Викторович", "Викторовна"),
    "Владимир": ("Владимирович", "Владимировна"), "Геннадий": ("Геннадьевич", "Геннадьевна"),
    "Дмитрий": ("Дмитриевич", "Дмитриевна"), "Евгений": ("Евгеньевич", "Евгеньевна"),
    "Иван": ("Иванович", "Ивановна"), "Игорь": ("Игоревич", "Игоревна"),
    "Константин": ("Константинович", "Константиновна"), "Михаил": ("Михайлович", "Михайловна"),
    "Николай": ("Николаевич", "Николаевна"), "Олег": ("Олегович", "Олеговна"),
    "Павел": ("Павлович", "Павловна"), "Пётр": ("Петрович", "Петровна"),
    "Сергей": ("Сергеевич", "Сергеевна"), "Юрий": ("Юрьевич", "Юрьевна"),
    "Анатолий": ("Анатольевич", "Анатольевна"), "Валерий": ("Валерьевич", "Валерьевна"),
}
# Мужская фамилия → женская
_SURNAMES = [
    ("Иванов", "Иванова"), ("Смирнов", "Смирнова"), ("Кузнецов", "Кузнецова"), ("Попов", "Попова"),
    ("Васильев", "Васильева"), ("Петров", "Петрова"), ("Соколов", "Соколова"), ("Михайлов", "Михайлова"),
    ("Новиков", "Новикова"), ("Фёдоров", "Фёдорова"), ("Морозов", "Морозова"), ("Волков", "Волкова"),
    ("Алексеев", "Алексеева"), ("Лебедев", "Лебедева"), ("Семёнов", "Семёнова"), ("Егоров", "Егорова"),
    ("Павлов", "Павлова"), ("Козлов", "Козлова"), ("Степанов", "Степанова"), ("Николаев", "Николаева"),
    ("Орлов", "Орлова"), ("Андреев", "Андреева"), ("Макаров", "Макарова"), ("Никитин", "Никитина"),
    ("Захаров", "Захарова"), ("Зайцев", "Зайцева"), ("Соловьёв", "Соловьёва"), ("Борисов", "Борисова"),
    ("Яковлев", "Яковлева"), ("Григорьев", "Григорьева"), ("Романов", "Романова"), ("Воробьёв", "Воробьёва"),
    ("Сергеев", "Сергеева"), ("Кузьмин", "Кузьмина"), ("Фролов", "Фролова"), ("Александров", "Александрова"),
    ("Дмитриев", "Дмитриева"), ("Королёв", "Королёва"), ("Гусев", "Гусева"), ("Киселёв", "Киселёва"),
    ("Ильин", "Ильина"), ("Максимов", "Максимова"), ("Поляков", "Полякова"), ("Сорокин", "Сорокина"),
    ("Виноградов", "Виноградова"), ("Ковалёв", "Ковалёва"), ("Белов", "Белова"), ("Медведев", "Медведева"),
    ("Антонов", "Антонова"), ("Тарасов", "Тарасова"), ("Жуков", "Жукова"), ("Баранов", "Баранова"),
    ("Филиппов", "Филиппова"), ("Комаров", "Комарова"), ("Давыдов", "Давыдова"), ("Беляев", "Беляева"),
    ("Герасимов", "Герасимова"), ("Богданов", "Богданова"), ("Осипов", "Осипова"), ("Сидоров", "Сидорова"),
    ("Матвеев", "Матвеева"), ("Титов", "Титова"), ("Марков", "Маркова"), ("Миронов", "Миронова"),
    ("Крылов", "Крылова"), ("Куликов", "Куликова"), ("Карпов", "Карпова"), ("Власов", "Власова"),
    ("Мельников", "Мельникова"), ("Денисов", "Денисова"), ("Гаврилов", "Гаврилова"), ("Тихонов", "Тихонова"),
    ("Казаков", "Казакова"), ("Афанасьев", "Афанасьева"), ("Ершов", "Ершова"), ("Шестаков", "Шестакова"),
    ("Вершинин", "Вершинина"), ("Чернов", "Чернова"), ("Савельев", "Савельева"), ("Широков", "Широкова"),
    ("Горбунов", "Горбунова"), ("Лукин", "Лукина"), ("Бородин", "Бородина"), ("Пестов", "Пестова"),
    ("Зверев", "Зверева"), ("Ширяев", "Ширяева"), ("Боровский", "Боровская"), ("Ковальский", "Ковальская"),
    ("Красильников", "Красильникова"), ("Уфимцев", "Уфимцева"), ("Пермяков", "Пермякова"),
    ("Чусовитин", "Чусовитина"), ("Бажов", "Бажова"), ("Мамин", "Мамина"),
]


def _generate(staff_id: str, profile: str) -> dict:
    """Правдоподобный профиль, детерминированный по staff_id."""
    num = int(staff_id[3:])
    rng = random.Random(SEED * 1000 + num)
    female = rng.random() < (0.08 if profile == "LTP3" else 0.3)
    surname_m, surname_f = _SURNAMES[rng.randrange(len(_SURNAMES))]
    father = rng.choice(sorted(_PATRONYMIC))
    pat_m, pat_f = _PATRONYMIC[father]
    if female:
        first, last, middle = rng.choice(_FEMALE_FIRST), surname_f, pat_f
    else:
        first, last, middle = rng.choice(_MALE_FIRST), surname_m, pat_m
    code = rng.choice([912, 922, 932, 950, 953, 961, 965, 982, 992, 904, 908])
    digits = f"{rng.randrange(1000000):07d}"
    phone = f"+7 ({code}) {digits[:3]}-{digits[3:5]}-{digits[5:]}"
    return {
        "staff_id": staff_id,
        "first_name": first,
        "last_name": last,
        "middle_name": middle,
        "phone": phone,
        "position": POSITIONS.get(profile, "Инженер"),
        "personnel_number": f"{660000 + num * 17 + rng.randrange(17):06d}",
        "note": None,
    }


def _initials(first: str | None, middle: str | None) -> str:
    return " ".join(f"{p.strip()[0]}." for p in (first, middle) if p and p.strip())


def format_display_name(p: dict) -> str:
    """«Фамилия И. О.»"""
    last = (p.get("last_name") or "").strip()
    ini = _initials(p.get("first_name"), p.get("middle_name"))
    return f"{last} {ini}".strip() or p.get("staff_id", "")


def _full_name(p: dict) -> str:
    return " ".join(x.strip() for x in (p.get("last_name"), p.get("first_name"), p.get("middle_name")) if x and x.strip())


# ---------------------------------------------------------------- исходные данные

EQUIPMENT_KINDS = ("ONT", "ROUTER", "STB")
EQUIPMENT_MAX = 99  # верхняя граница ввода: больше в машину всё равно не влезет
_KIND_RU = {"ONT": "ONT", "ROUTER": "роутер", "STB": "STB"}


def parse_equipment(value, field: str) -> dict[str, int] | None:
    """Частичный рюкзак {ONT?, ROUTER?, STB?}: целые 0…99. Иначе 422 с понятным текстом."""
    if value is None:
        return None
    if not isinstance(value, dict):
        raise ApiError(422, "INVALID_EQUIPMENT", f"Поле {field}: ожидается объект {{ONT, ROUTER, STB}}",
                       [{"field": field, "message": "ожидается объект"}])
    out: dict[str, int] = {}
    for k, v in value.items():
        if k not in EQUIPMENT_KINDS:
            raise ApiError(422, "INVALID_EQUIPMENT", f"Поле {field}: неизвестный вид АО «{k}» (ONT, ROUTER, STB)",
                           [{"field": f"{field}.{k}", "message": "неизвестный вид АО"}])
        if v is None:
            continue
        if isinstance(v, bool) or not isinstance(v, int) or not 0 <= v <= EQUIPMENT_MAX:
            raise ApiError(422, "INVALID_EQUIPMENT",
                           f"Поле {field}.{k}: нужно целое число от 0 до {EQUIPMENT_MAX}, получено {v!r}",
                           [{"field": f"{field}.{k}", "message": f"целое 0…{EQUIPMENT_MAX}"}])
        out[k] = v
    return out or None


def _eq_dict(value) -> dict[str, int] | None:
    if not isinstance(value, dict):
        return None
    try:
        return {k: int(value.get(k, 0)) for k in EQUIPMENT_KINDS}
    except (TypeError, ValueError):
        return None


@lru_cache(maxsize=1)
def _base_staff() -> dict[str, dict]:
    """staff_id → {profiles, service_center_id, equipment_start, equipment_capacity} из staff.json
    (то, что знает планировщик)."""
    try:
        items = json.loads((DATA / "staff.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        items = []
    return {s["staff_id"]: {"profiles": list(s.get("profiles") or []), "service_center_id": s.get("service_center_id"),
                            "equipment_start": _eq_dict(s.get("equipment_start")),
                            "equipment_capacity": _eq_dict(s.get("equipment_capacity"))}
            for s in items if isinstance(s, dict) and s.get("staff_id")}


@lru_cache(maxsize=1)
def _service_center_ids() -> frozenset[str]:
    try:
        items = json.loads((DATA / "service_centers.json").read_text(encoding="utf-8"))
        return frozenset(s["service_center_id"] for s in items)
    except (OSError, ValueError, KeyError, TypeError):
        return frozenset()


def _path() -> Path:
    env = os.environ.get("STAFF_DIRECTORY_FILE")
    return Path(env) if env else DATA / "staff_directory.json"


# ---------------------------------------------------------------- хранилище

class StaffDirectory:
    """Профили по staff_id. Все операции — под self.lock."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.lock = threading.RLock()
        self.persisted = True
        self._profiles: dict[str, dict] | None = None
        # staff_id → {"equipment_start": {...}, "equipment_capacity": {...}} — переопределения «рюкзака АО»
        self._equipment: dict[str, dict] = {}

    def _load(self) -> dict[str, dict]:
        if self._profiles is not None:
            return self._profiles
        profiles: dict[str, dict] = {}
        dirty = corrupt = False
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            items = raw.get("staff", []) if isinstance(raw, dict) else raw
            profiles = {p["staff_id"]: p for p in items if isinstance(p, dict) and _STAFF_ID.match(str(p.get("staff_id")))}
            # старый файл (без equipment_overrides) читается как «переопределений нет»
            overrides = raw.get("equipment_overrides", {}) if isinstance(raw, dict) else {}
            for sid, ov in (overrides.items() if isinstance(overrides, dict) else ()):
                start = _eq_dict(ov.get("equipment_start")) if isinstance(ov, dict) else None
                cap = _eq_dict(ov.get("equipment_capacity")) if isinstance(ov, dict) else None
                if _STAFF_ID.match(str(sid)) and start and cap and all(0 <= start[k] <= cap[k] for k in EQUIPMENT_KINDS):
                    self._equipment[sid] = {"equipment_start": start, "equipment_capacity": cap}
        except FileNotFoundError:
            pass
        except (OSError, ValueError, AttributeError, TypeError):
            corrupt = True  # битый файл: сгенерируем в памяти, поверх не пишем, пока нет правок
        for sid, base in _base_staff().items():
            if sid not in profiles:
                profiles[sid] = _generate(sid, (base["profiles"] or ["INSTALL"])[0])
                dirty = True
        self._profiles = profiles
        if dirty and not corrupt:
            self._save()
        return profiles

    def _save(self) -> None:
        assert self._profiles is not None
        data = {"version": 1, "staff": [self._profiles[k] for k in sorted(self._profiles)],
                "equipment_overrides": {k: self._equipment[k] for k in sorted(self._equipment)}}
        tmp = self.path.with_name(self.path.name + ".tmp")
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
            os.replace(tmp, self.path)
            self.persisted = True
        except OSError:
            self.persisted = False  # каталог только для чтения: храним в памяти процесса

    # ------------------------------------------------------------ чтение

    def _view(self, p: dict) -> dict:
        base = _base_staff().get(p["staff_id"])
        profiles = list(base["profiles"]) if base else ([p["profile"]] if p.get("profile") else [])
        return {
            "staff_id": p["staff_id"],
            "display_name": format_display_name(p),
            "full_name": _full_name(p),
            "first_name": p.get("first_name"),
            "last_name": p.get("last_name"),
            "middle_name": p.get("middle_name"),
            "phone": p.get("phone"),
            "position": p.get("position"),
            "personnel_number": p.get("personnel_number"),
            "note": p.get("note"),
            "profile": profiles[0] if profiles else None,
            "profiles": profiles,
            "service_center_id": base["service_center_id"] if base else p.get("service_center_id"),
            # Сотрудник есть только в справочнике: в план попадёт при пересборке дня.
            "in_planner": base is not None,
            **self._equipment_view(p["staff_id"]),
        }

    # ------------------------------------------------------------ рюкзак АО

    def _equipment_data(self, staff_id: str) -> tuple[dict, dict] | None:
        base = _base_staff().get(staff_id)
        if not base or not base["equipment_start"] or not base["equipment_capacity"]:
            return None
        return dict(base["equipment_start"]), dict(base["equipment_capacity"])

    def _effective(self, staff_id: str) -> tuple[dict, dict] | None:
        data = self._equipment_data(staff_id)
        if data is None:
            return None
        ov = self._equipment.get(staff_id)
        return (dict(ov["equipment_start"]), dict(ov["equipment_capacity"])) if ov else data

    def _equipment_view(self, staff_id: str) -> dict:
        data = self._equipment_data(staff_id)
        eff = self._effective(staff_id)
        return {
            "equipment_start_data": data[0] if data else None,
            "equipment_capacity_data": data[1] if data else None,
            "equipment_start": eff[0] if eff else None,
            "equipment_capacity": eff[1] if eff else None,
            "equipment_override": staff_id in self._equipment,
        }

    def _set_equipment(self, staff_id: str, start: dict | None, capacity: dict | None) -> None:
        """Сливает частичные start/capacity с текущими эффективными, проверяет и запоминает.
        Совпало с данными — переопределение снимается. Без self._save()."""
        eff = self._effective(staff_id)
        if eff is None:
            raise ApiError(422, "EQUIPMENT_NOT_APPLICABLE",
                           f"У {staff_id} нет данных планировщика (staff.json): рюкзак АО задать нельзя",
                           [{"field": "equipment_start", "message": "сотрудник не в планировщике"}])
        new_start = {**eff[0], **(start or {})}
        new_cap = {**eff[1], **(capacity or {})}
        bad = [k for k in EQUIPMENT_KINDS if new_cap[k] < new_start[k]]
        if bad:
            names = ", ".join(f"{_KIND_RU[k]}: старт {new_start[k]} > максимум {new_cap[k]}" for k in bad)
            raise ApiError(422, "EQUIPMENT_CAPACITY_LESS_THAN_START",
                           f"У {staff_id} максимум меньше стартового количества ({names})",
                           [{"field": f"equipment_capacity.{k}", "message": "должно быть не меньше старта"} for k in bad])
        if (new_start, new_cap) == self._equipment_data(staff_id):
            self._equipment.pop(staff_id, None)
        else:
            self._equipment[staff_id] = {"equipment_start": new_start, "equipment_capacity": new_cap}

    def set_equipment(self, staff_id: str, start: dict | None, capacity: dict | None) -> dict:
        with self.lock:
            p = self._load().get(staff_id)
            if p is None:
                raise ApiError(404, "STAFF_NOT_FOUND", f"Сотрудник {staff_id} не найден в справочнике")
            self._set_equipment(staff_id, start, capacity)
            self._save()
            return self._view(p)

    def reset_equipment(self, staff_id: str) -> dict:
        with self.lock:
            p = self._load().get(staff_id)
            if p is None:
                raise ApiError(404, "STAFF_NOT_FOUND", f"Сотрудник {staff_id} не найден в справочнике")
            if self._equipment.pop(staff_id, None) is not None:
                self._save()
            return self._view(p)

    def _bulk_targets(self, profile: str | None) -> list[str]:
        base = _base_staff()
        return [sid for sid in sorted(self._load())
                if sid in base and self._equipment_data(sid) is not None
                and (profile is None or profile in base[sid]["profiles"])]

    def bulk_equipment(self, start: dict | None, capacity: dict | None, profile: str | None) -> int:
        """Задать рюкзак всем (или профилю) атомарно: при ошибке у кого-то не меняется никто."""
        with self.lock:
            targets = self._bulk_targets(profile)
            bad = []
            for sid in targets:
                eff = self._effective(sid)
                s, c = {**eff[0], **(start or {})}, {**eff[1], **(capacity or {})}
                if any(c[k] < s[k] for k in EQUIPMENT_KINDS):
                    bad.append(sid)
            if bad:
                raise ApiError(422, "EQUIPMENT_CAPACITY_LESS_THAN_START",
                               f"У {len(bad)} сотр. максимум получится меньше старта ({', '.join(bad[:5])}"
                               f"{'…' if len(bad) > 5 else ''}): задайте и максимум",
                               [{"field": "capacity", "message": "должно быть не меньше старта"}])
            for sid in targets:
                self._set_equipment(sid, start, capacity)
            self._save()
            return len(targets)

    def bulk_reset_equipment(self, profile: str | None) -> int:
        with self.lock:
            targets = [sid for sid in self._bulk_targets(profile) if sid in self._equipment]
            for sid in targets:
                self._equipment.pop(sid)
            if targets:
                self._save()
            return len(targets)

    def apply_equipment(self, staff: list[dict]) -> list[dict]:
        """Копия staff.json с эффективным рюкзаком (для загрузки демо-дня)."""
        with self.lock:
            self._load()
            out = []
            for s in staff:
                ov = self._equipment.get(s.get("staff_id")) if isinstance(s, dict) else None
                out.append({**s, **{k: dict(v) for k, v in ov.items()}} if ov else s)
            return out

    def list(self) -> list[dict]:
        with self.lock:
            return [self._view(p) for _, p in sorted(self._load().items())]

    def get(self, staff_id: str) -> dict:
        with self.lock:
            p = self._load().get(staff_id)
            if p is None:
                raise ApiError(404, "STAFF_NOT_FOUND", f"Сотрудник {staff_id} не найден в справочнике")
            return self._view(p)

    def names(self) -> dict[str, str]:
        with self.lock:
            return {sid: format_display_name(p) for sid, p in self._load().items()}

    def display_name(self, staff_id: str | None, fallback: str | None = None) -> str | None:
        if not staff_id:
            return fallback
        with self.lock:
            p = self._load().get(staff_id)
        return format_display_name(p) if p else fallback

    # ------------------------------------------------------------ запись

    def update(self, staff_id: str, body: "StaffProfileUpdate") -> dict:
        with self.lock:
            profiles = self._load()
            p = profiles.get(staff_id)
            if p is None:
                raise ApiError(404, "STAFF_NOT_FOUND", f"Сотрудник {staff_id} не найден в справочнике")
            changes = body.model_dump(exclude_unset=True)
            eq_start = parse_equipment(changes.pop("equipment_start", None), "equipment_start")
            eq_cap = parse_equipment(changes.pop("equipment_capacity", None), "equipment_capacity")
            in_planner = staff_id in _base_staff()
            for field in ("profile", "service_center_id"):
                if field in changes and in_planner:
                    current = self._view(p)[field]
                    if changes[field] is not None and changes[field] != current:
                        raise ApiError(422, "READ_ONLY_FIELD",
                                       "Профиль и СЦ сотрудника из справочника планировщика здесь не меняются",
                                       [{"field": field, "message": "только для чтения"}])
                    changes.pop(field)
            if "service_center_id" in changes:
                _check_sc(changes["service_center_id"])
            for field in ("first_name", "last_name", "profile", "service_center_id"):
                if field in changes and changes[field] is None:
                    raise ApiError(400, "BAD_REQUEST", f"Поле {field}: обязательно",
                                   [{"field": field, "message": "обязательно"}])
            if eq_start or eq_cap:
                self._set_equipment(staff_id, eq_start, eq_cap)  # 422 до любых изменений профиля
            updated = {**p, **changes}
            profiles[staff_id] = updated
            self._save()
            return self._view(updated)

    def create(self, body: "StaffProfileCreate") -> dict:
        with self.lock:
            profiles = self._load()
            _check_sc(body.service_center_id)
            used = {int(m.group(1)) for sid in (*profiles, *_base_staff()) if (m := _STAFF_ID.match(sid))}
            num = next((n for n in range(max(used, default=0) + 1, 1000)), None)
            if num is None:
                num = next((n for n in range(1, 1000) if n not in used), None)
            if num is None:
                raise ApiError(409, "STAFF_ID_EXHAUSTED", "Нет свободных табельных идентификаторов ST-001…ST-999")
            sid = f"ST-{num:03d}"
            data = body.model_dump()
            if not data.get("position"):
                data["position"] = POSITIONS.get(data["profile"], "Инженер")
            if not data.get("personnel_number"):
                data["personnel_number"] = f"{660000 + num * 17:06d}"
            record = {"staff_id": sid, **data}
            profiles[sid] = record
            self._save()
            return self._view(record)


def _check_sc(sc_id: str | None) -> None:
    known = _service_center_ids()
    if sc_id and known and sc_id not in known:
        raise ApiError(422, "SERVICE_CENTER_NOT_FOUND", f"Сервисный центр {sc_id} не найден",
                       [{"field": "service_center_id", "message": "нет в справочнике СЦ"}])


_directory: StaffDirectory | None = None
_directory_lock = threading.Lock()


def directory() -> StaffDirectory:
    """Общий справочник процесса (путь берётся при первом обращении)."""
    global _directory
    with _directory_lock:
        if _directory is None or _directory.path != _path():
            _directory = StaffDirectory(_path())
        return _directory


def display_name(staff_id: str | None, fallback: str | None = None) -> str | None:
    """«Фамилия И. О.» для UI-ответов; fallback, если справочник недоступен или id неизвестен."""
    try:
        return directory().display_name(staff_id, fallback)
    except Exception:  # справочник не должен ломать основные UI-ответы
        return fallback


# ---------------------------------------------------------------- модели запросов

def _norm_phone(value: str | None) -> str | None:
    if value is None:
        return None
    value = value.strip()
    if not value:
        return None
    digits = re.sub(r"\D", "", value)
    if len(digits) == 11 and digits[0] in "78":
        digits = digits[1:]
    if len(digits) != 10:
        raise ValueError("ожидается телефон вида +7 (9xx) xxx-xx-xx")
    return f"+7 ({digits[:3]}) {digits[3:6]}-{digits[6:8]}-{digits[8:]}"


def _norm_text(value: str | None) -> str | None:
    if value is None:
        return None
    value = " ".join(value.split())
    return value or None


class _ProfileFields(BaseModel):
    model_config = ConfigDict(extra="ignore")

    middle_name: Optional[str] = Field(default=None, max_length=60)
    phone: Optional[str] = None
    position: Optional[str] = Field(default=None, max_length=80)
    personnel_number: Optional[str] = Field(default=None, max_length=20)
    note: Optional[str] = Field(default=None, max_length=500)

    @field_validator("phone")
    @classmethod
    def _phone(cls, v):
        return _norm_phone(v)

    @field_validator("middle_name", "position", "personnel_number", "note", mode="before")
    @classmethod
    def _text(cls, v):
        return _norm_text(v) if isinstance(v, str) else v


class StaffProfileUpdate(_ProfileFields):
    first_name: Optional[str] = Field(default=None, min_length=1, max_length=60)
    last_name: Optional[str] = Field(default=None, min_length=1, max_length=60)
    profile: Optional[Literal["INSTALL", "LTP3"]] = None
    service_center_id: Optional[str] = Field(default=None, pattern=r"^SC-[0-9]{2}$")
    # Рюкзак АО (частично): проверяется в StaffDirectory.update → 422 с понятным текстом.
    equipment_start: Optional[Any] = None
    equipment_capacity: Optional[Any] = None

    @field_validator("first_name", "last_name", mode="before")
    @classmethod
    def _name(cls, v):
        return _norm_text(v) if isinstance(v, str) else v


class StaffProfileCreate(_ProfileFields):
    first_name: str = Field(min_length=1, max_length=60)
    last_name: str = Field(min_length=1, max_length=60)
    profile: Literal["INSTALL", "LTP3"]
    service_center_id: str = Field(pattern=r"^SC-[0-9]{2}$")

    @field_validator("first_name", "last_name", mode="before")
    @classmethod
    def _name(cls, v):
        return (_norm_text(v) or "") if isinstance(v, str) else v


# ---------------------------------------------------------------- routes

NEXT_REPLAN_MESSAGE = "Новый сотрудник попадёт в план при следующей пересборке дня"
EQUIPMENT_REPLAN_MESSAGE = "Рюкзак АО применится при следующей пересборке дня"

# Последний результат сценария «Рюкзак АО» (/ui/scenario/equipment): p90 расхода за смену
# без заездов на склад по профилю. Подсказка, а не расчёт: без сценария — None.
_recommended: dict | None = None


def remember_recommendation(date: str, recommended: list[dict]) -> None:
    global _recommended
    by_profile = {r["profile"]: {k: r[k]["p90"] for k in EQUIPMENT_KINDS if isinstance(r.get(k), dict)}
                  for r in recommended if r.get("profile")}
    _recommended = {"date": date, "percentile": 90, "by_profile": by_profile} if by_profile else None


class EquipmentBulk(BaseModel):
    model_config = ConfigDict(extra="ignore")

    start: Optional[Any] = None
    capacity: Optional[Any] = None
    profile: Optional[Literal["INSTALL", "LTP3", "ALL"]] = None


class EquipmentBulkReset(BaseModel):
    model_config = ConfigDict(extra="ignore")

    profile: Optional[Literal["INSTALL", "LTP3", "ALL"]] = None


def _profile_filter(value: str | None) -> str | None:
    return None if value in (None, "ALL") else value


@router.get("/staff")
def list_staff() -> dict:
    d = directory()
    items = d.list()
    return {"staff": items, "count": len(items), "persisted": d.persisted, "recommended_backpack": _recommended}


@router.post("/staff/equipment/bulk")
async def bulk_equipment(request: Request) -> dict:
    body: EquipmentBulk = _parse(EquipmentBulk.model_validate_json, (await request.body()) or b"{}")
    start = parse_equipment(body.start, "start")
    capacity = parse_equipment(body.capacity, "capacity")
    if not start and not capacity:
        raise ApiError(422, "INVALID_EQUIPMENT", "Укажите старт или максимум хотя бы по одному виду АО",
                       [{"field": "start", "message": "пусто"}])
    d = directory()
    n = d.bulk_equipment(start, capacity, _profile_filter(body.profile))
    return {"updated": n, "profile": body.profile or "ALL", "applies_from_next_replan": True,
            "message": EQUIPMENT_REPLAN_MESSAGE, "persisted": d.persisted}


@router.post("/staff/equipment/bulk/reset")
async def bulk_reset_equipment(request: Request) -> dict:
    body: EquipmentBulkReset = _parse(EquipmentBulkReset.model_validate_json, (await request.body()) or b"{}")
    d = directory()
    n = d.bulk_reset_equipment(_profile_filter(body.profile))
    return {"reset": n, "profile": body.profile or "ALL", "applies_from_next_replan": True,
            "message": EQUIPMENT_REPLAN_MESSAGE, "persisted": d.persisted}


@router.get("/staff/{staff_id}")
def get_staff(staff_id: str) -> dict:
    return directory().get(staff_id)


@router.put("/staff/{staff_id}")
async def put_staff(staff_id: str, request: Request) -> dict:
    body: StaffProfileUpdate = _parse(StaffProfileUpdate.model_validate_json, await request.body())
    d = directory()
    touches_equipment = bool({"equipment_start", "equipment_capacity"} & body.model_fields_set)
    item = d.update(staff_id, body)
    extra = {"applies_from_next_replan": True, "message": EQUIPMENT_REPLAN_MESSAGE} if touches_equipment else {}
    return {**item, **extra, "persisted": d.persisted}


@router.delete("/staff/{staff_id}/equipment-override")
def delete_equipment_override(staff_id: str) -> dict:
    d = directory()
    item = d.reset_equipment(staff_id)
    return {**item, "applies_from_next_replan": True, "message": EQUIPMENT_REPLAN_MESSAGE, "persisted": d.persisted}


@router.post("/staff", status_code=201)
async def create_staff(request: Request) -> JSONResponse:
    body: StaffProfileCreate = _parse(StaffProfileCreate.model_validate_json, await request.body())
    d = directory()
    item = d.create(body)
    return JSONResponse(status_code=201, content={
        **item,
        "applies_from_next_replan": True,
        "message": NEXT_REPLAN_MESSAGE,
        "persisted": d.persisted,
    })
````

## File: backend/app/state.py
````python
"""Состояние сервиса в памяти: справочники, матрица и дни."""

from __future__ import annotations

from dataclasses import dataclass, field
import datetime as dt

from .models import (
    AccessNode,
    Building,
    CancelledOrder,
    Order,
    Plan,
    ServiceCenter,
    Staff,
    TravelMatrix,
    WorkType,
)


@dataclass
class ReferenceState:
    service_centers: dict[str, ServiceCenter]
    staff: dict[str, Staff]
    access_nodes: dict[str, AccessNode]
    buildings: dict[str, Building]
    work_types: dict[str, WorkType]
    # building_id -> набор access_node_id, к которым он привязан
    building_nodes: dict[str, set[str]] = field(default_factory=dict)


@dataclass
class MatrixState:
    matrix: TravelMatrix
    index: dict[str, int]


@dataclass
class DayState:
    """Состояние одного рабочего дня.

    Планировщик получает клон и может его менять; сервис коммитит клон только
    после успешной обработки, поэтому ошибка не меняет состояние.
    """

    date: dt.date
    now: dt.datetime
    orders: dict[str, Order]
    absent_staff_ids: set[str]
    # order_id -> момент, с которого наряд известен диспетчеру
    arrived_at: dict[str, dt.datetime]
    cancelled: dict[str, CancelledOrder] = field(default_factory=dict)
    plan: Plan | None = None
    plan_version: int = 0
    model_time: dt.datetime | None = None
    # принятые события: event_id -> plan_version, который они породили
    events: dict[str, int] = field(default_factory=dict)
    # order_id -> конец визита из VISIT_DELAYED / ORDER_TRANSFERRED (H5, H10)
    visit_end_overrides: dict[str, dt.datetime] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.model_time is None:
            self.model_time = self.now

    def clone(self) -> "DayState":
        return DayState(
            date=self.date,
            now=self.now,
            orders=dict(self.orders),
            absent_staff_ids=set(self.absent_staff_ids),
            arrived_at=dict(self.arrived_at),
            cancelled=dict(self.cancelled),
            plan=self.plan.model_copy(deep=True) if self.plan is not None else None,
            plan_version=self.plan_version,
            model_time=self.model_time,
            events=dict(self.events),
            visit_end_overrides=dict(self.visit_end_overrides),
        )

    def pending_orders(self) -> list[Order]:
        """Наряды дня, не отменённые на текущий момент (в порядке загрузки)."""
        return [o for oid, o in self.orders.items() if oid not in self.cancelled]
````

## File: backend/app/store.py
````python
"""Потокобезопасное хранилище состояния (в памяти процесса)."""

from __future__ import annotations

import threading
import datetime as dt

from .state import DayState, MatrixState, ReferenceState


class Store:
    def __init__(self) -> None:
        # Все операции над состоянием выполняются под этой блокировкой,
        # поэтому запросы выполняются последовательно и согласованно.
        self.lock = threading.RLock()
        self.reference: ReferenceState | None = None
        self.matrix: MatrixState | None = None
        self.days: dict[dt.date, DayState] = {}
````

## File: backend/app/ui_api.py
````python
"""Вспомогательный API для интерфейса диспетчера (/ui), вне контракта /api/v1.

Загружает демо-день из data/synth, отдаёт наряды со статусами, справочники без матрицы
и по шагам проигрывает сценарий событий (с триггерами, как tools/bench/run_events.py).
Состояние — тот же in-memory Store сервиса; вызовы идут через DispatchService.
"""

from __future__ import annotations

import csv
import datetime as dt
import json
import math
import os
from functools import lru_cache
from pathlib import Path

from fastapi import APIRouter, FastAPI, Query, Request
from fastapi.middleware.cors import CORSMiddleware

from .errors import ApiError

ROOT = Path(__file__).resolve().parents[2]
DATA = Path(os.environ.get("UI_DATA_DIR", ROOT / "data" / "synth"))
TZ = dt.timezone(dt.timedelta(hours=5))
SCENARIOS = {"dense": "events_dense.json", "example": "events_example.json"}

router = APIRouter(prefix="/ui", tags=["ui"])


# ---------------------------------------------------------------- данные

def _csv_value(field: str, value: str):
    if value in ("null", ""):
        return None
    if field == "profiles":
        return value.split("|")
    if field in ("duration_min", "max_start_delay_min", "deadline_value", "repeat_deadline_value"):
        return int(value)
    if field == "pgp_relevant":
        return value == "true"
    return value


@lru_cache(maxsize=1)
def _reference_bytes() -> bytes:
    with open(DATA / "work_types.csv", encoding="utf-8", newline="") as fh:
        work_types = [{k: _csv_value(k, v) for k, v in row.items()} for row in csv.DictReader(fh)]
    payload = {
        "service_centers": json.loads((DATA / "service_centers.json").read_text(encoding="utf-8")),
        "staff": json.loads((DATA / "staff.json").read_text(encoding="utf-8")),
        "access_nodes": json.loads((DATA / "access_nodes.json").read_text(encoding="utf-8")),
        "work_types": work_types,
    }
    return json.dumps(payload, ensure_ascii=False).encode("utf-8")


def _effective_reference_bytes() -> bytes:
    """Справочники демо-дня с рюкзаками АО из справочника сотрудников (переопределения
    диспетчера поверх staff.json). Нет переопределений — те же байты, что из файлов."""
    from .staff_directory import directory  # staff_directory импортирует этот модуль
    raw = _reference_bytes()
    try:
        d = directory()
        with d.lock:
            d._load()
            if not d._equipment:
                return raw
        payload = json.loads(raw)
        payload["staff"] = d.apply_equipment(payload["staff"])
        return json.dumps(payload, ensure_ascii=False).encode("utf-8")
    except Exception:  # справочник не должен ломать загрузку дня
        return raw


@lru_cache(maxsize=1)
def _orders_train() -> list[dict]:
    return json.loads((DATA / "orders_train.json").read_text(encoding="utf-8"))


@lru_cache(maxsize=4)
def _scenario(name: str) -> dict:
    return json.loads((DATA / SCENARIOS[name]).read_text(encoding="utf-8"))


def _ts(value: str) -> dt.datetime:
    return dt.datetime.fromisoformat(value)


def _plan_time_limit() -> int:
    """time_limit_sec для POST /plan так, чтобы внутренний бюджет был ≈ UI_PLAN_BUDGET_SEC
    (планировщик берёт min(PLANNER_DAY_BUDGET_SEC, 0.8·limit − 3))."""
    try:
        budget = float(os.environ.get("UI_PLAN_BUDGET_SEC", "15"))
    except ValueError:
        budget = 15.0
    return max(1, min(300, math.ceil((budget + 3.0) / 0.8)))


# ---------------------------------------------------------------- helpers

def _staff_name(staff_id: str | None, fallback: str | None) -> str | None:
    from .staff_directory import display_name  # staff_directory импортирует этот модуль
    return display_name(staff_id, fallback)


def _service(request: Request):
    return request.app.state.service


def _demo(request: Request) -> dict | None:
    return getattr(request.app.state, "ui_demo", None)


def _visits(plan) -> dict[str, tuple[str, object]]:
    return {s.order_id: (r.staff_id, s) for r in plan.routes for s in r.stops if s.type == "VISIT"}


def _iso(value) -> str | None:
    return value.isoformat() if value is not None else None


def _next_body(svc, demo: dict) -> dict | None:
    """Следующее сообщение сценария: сработавший триггер (если он не позже ближайшего
    события) или само событие; когда события кончились — оставшиеся триггеры."""
    plan = svc.get_plan(demo["date"])
    vis = _visits(plan)
    cands = []
    for tr in demo["triggers"]:
        if tr["order_id"] in vis:
            sid, stop = vis[tr["order_id"]]
            cands.append((stop.start + dt.timedelta(minutes=tr["fire_after_start_min"]), tr, sid, stop))
    next_event = demo["events"][0] if demo["events"] else None
    if cands:
        at, tr, sid, stop = min(cands, key=lambda c: c[0])
        if next_event is None or at <= _ts(next_event["timestamp"]):
            at = max(at, demo["last_ts"])
            body = {"event_id": tr["trigger_id"], "type": tr["type"], "timestamp": at.isoformat(),
                    "staff_id": sid, "order_id": tr["order_id"]}
            if tr["type"] == "VISIT_DELAYED":
                ref = svc.store.reference
                wt = ref.work_types[demo["order_info"][tr["order_id"]]["work_type_code"]]
                end = stop.start + dt.timedelta(minutes=wt.duration_min + (tr.get("extra_duration_min") or 0))
                body["expected_end"] = end.isoformat()
            else:
                body["actual_end"] = at.isoformat()
                body["transferred_to"] = tr.get("transferred_to") or "STATION_EQUIPMENT"
            demo["triggers"].remove(tr)
            demo["last_ts"] = at
            return body
    if next_event is not None:
        demo["events"].pop(0)
        demo["last_ts"] = max(demo["last_ts"], _ts(next_event["timestamp"]))
        return next_event
    if demo["triggers"]:  # триггеры по нарядам, которых нет в маршрутах, пропускаются
        demo["skipped"] += len(demo["triggers"])
        demo["triggers"].clear()
    return None


def _demo_status(svc, demo: dict | None) -> dict:
    if demo is None:
        return {"loaded": False}
    plan = svc.get_plan(demo["date"])
    return {
        "loaded": True,
        "date": demo["date"],
        "scenario": demo["scenario"],
        "now": demo["now"],
        "as_of": _iso(plan.as_of),
        "plan_version": plan.plan_version,
        "remaining_events": len(demo["events"]),
        "remaining_triggers": len(demo["triggers"]),
        "skipped_triggers": demo["skipped"],
    }


# ---------------------------------------------------------------- routes

@router.get("/demo/load")
def demo_load(
    request: Request,
    date: str | None = None,
    scenario: str = Query("dense", pattern="^(dense|example)$"),
) -> dict:
    svc = _service(request)
    sc = _scenario(scenario)
    day = date or sc["date"]
    try:
        dt.date.fromisoformat(day)
    except ValueError:
        raise ApiError(400, "BAD_REQUEST", f"Некорректная дата '{day}', ожидается YYYY-MM-DD") from None
    with_events = day == sc["date"]
    now = _ts(sc["now"]) if with_events else dt.datetime.combine(
        dt.date.fromisoformat(day), dt.time(8, 30), tzinfo=TZ)

    day_orders = [o for o in _orders_train() if o["slot"]["start"][:10] == day]
    loaded = [o for o in day_orders if _ts(o["registered_at"]) <= now]
    if not day_orders:
        raise ApiError(404, "DAY_NOT_FOUND", f"В orders_train.json нет нарядов на {day}")

    svc.put_reference(_effective_reference_bytes())
    svc.put_travel_matrix((DATA / "travel_matrix.json").read_bytes())
    svc.put_day(day, json.dumps({"date": day, "now": now.isoformat(), "orders": loaded},
                                ensure_ascii=False).encode("utf-8"))
    plan = svc.build_plan(day, json.dumps({"time_limit_sec": _plan_time_limit()}).encode())

    order_info = {o["order_id"]: o for o in day_orders}
    events = sorted(sc["events"], key=lambda e: _ts(e["timestamp"])) if with_events else []
    for e in events:
        if e["type"] == "NEW_ORDER":
            order_info[e["order"]["order_id"]] = e["order"]
    request.app.state.ui_demo = {
        "date": day,
        "scenario": scenario if with_events else None,
        "now": now.isoformat(),
        "events": events,
        "triggers": list(sc["triggers"]) if with_events else [],
        "order_info": order_info,
        "last_ts": now,
        "skipped": 0,
    }
    return {
        "date": day,
        "now": now.isoformat(),
        "status": "loaded",
        "plan_version": plan.plan_version,
        "orders": len(loaded),
        "computation_ms": plan.computation_ms,
        "demo": _demo_status(svc, request.app.state.ui_demo),
    }


@router.get("/demo/status")
def demo_status(request: Request) -> dict:
    return _demo_status(_service(request), _demo(request))


@router.post("/demo/step")
def demo_step(request: Request, count: int = Query(1, ge=1, le=50)) -> dict:
    svc = _service(request)
    demo = _demo(request)
    if demo is None:
        raise ApiError(409, "PLAN_NOT_BUILT", "Сначала загрузите день: GET /ui/demo/load")
    sent = []
    for _ in range(count):
        body = _next_body(svc, demo)
        if body is None:
            break
        item = {"event_id": body["event_id"], "type": body["type"], "timestamp": body["timestamp"]}
        try:
            res = svc.post_event(demo["date"], json.dumps(body, ensure_ascii=False).encode("utf-8"))
            item.update(accepted=True, plan_version=res.plan_version, processing_ms=res.processing_ms,
                        message=res.message, changed_staff_ids=res.changed_staff_ids)
        except ApiError as exc:
            item.update(accepted=False, error=exc.body())
        sent.append(item)
    status = _demo_status(svc, demo)
    return {"sent": sent, "plan_version": status["plan_version"], "as_of": status["as_of"], "demo": status}


@router.get("/day/{date}/orders")
def day_orders(date: str, request: Request) -> dict:
    svc = _service(request)
    plan = svc.get_plan(date)
    with svc.store.lock:
        day = svc._day(dt.date.fromisoformat(date))
        orders = list(day.orders.values())
        ref = svc.store.reference
    vis = _visits(plan)
    cancelled = {c.order_id: c for c in plan.cancelled}
    pgp = {oid: g for g in plan.pgp_groups for oid in g.order_ids}
    unassigned = {u.order_id: u for u in plan.unassigned}
    late = {a.order_id: a for a in plan.alerts if a.type == "LATE_RISK" and a.status == "ACTIVE"}

    rows = []
    for o in orders:
        row = o.model_dump(mode="json", exclude={"fact"})
        wt = ref.work_types.get(o.work_type_code)
        row["work_type_name"] = wt.name if wt else o.work_type_code
        row.update(status="UNKNOWN", staff_id=None, staff_name=None, planned_arrival=None,
                   planned_start=None, planned_end=None, stop_status=None, explanation=None,
                   group_id=None, reason_code=None, reason_text=None, suggested_action=None,
                   cancelled_at=None, late_risk=None, partner_staff_id=None, partner_staff_name=None)
        if o.order_id in cancelled:
            row.update(status="CANCELLED", cancelled_at=_iso(cancelled[o.order_id].cancelled_at))
        elif o.order_id in vis:
            sid, stop = vis[o.order_id]
            staff = ref.staff.get(sid)
            row.update(status="ROUTED", staff_id=sid,
                       staff_name=_staff_name(sid, staff.display_name if staff else None),
                       planned_arrival=_iso(stop.arrival), planned_start=_iso(stop.start),
                       planned_end=_iso(stop.end), stop_status=stop.status, explanation=stop.explanation)
            # совместный наряд (JOINT_ORDERS): визит напарника — ORD-…-B у другого сотрудника
            partner = vis.get(o.order_id + "-B")
            if partner is not None:
                pst = ref.staff.get(partner[0])
                row.update(partner_staff_id=partner[0],
                           partner_staff_name=_staff_name(partner[0], pst.display_name if pst else None))
        elif o.order_id in pgp:
            row.update(status="PGP", group_id=pgp[o.order_id].group_id)
        elif o.order_id in unassigned:
            u = unassigned[o.order_id]
            row.update(status="UNASSIGNED", reason_code=u.reason_code, reason_text=u.reason_text,
                       suggested_action=u.suggested_action)
        if o.order_id in late:
            a = late[o.order_id]
            row["late_risk"] = {"alert_id": a.alert_id, "lateness_min": a.lateness_min,
                                "suggested_action": a.suggested_action}
        rows.append(row)
    return {"date": date, "plan_version": plan.plan_version, "as_of": _iso(plan.as_of), "orders": rows}


@router.get("/reference")
def reference(request: Request) -> dict:
    ref = _service(request).store.reference
    if ref is None:
        raise ApiError(409, "REFERENCE_NOT_LOADED", "Справочники не загружены: GET /ui/demo/load")

    def dump(items):
        return [x.model_dump(mode="json", by_alias=True) for x in items]

    nodes = []
    for n in ref.access_nodes.values():
        d = n.model_dump(mode="json", by_alias=True, exclude={"buildings"})
        d["buildings_count"] = len(n.buildings)
        nodes.append(d)
    staff = dump(ref.staff.values())
    for d in staff:  # «Фамилия И. О.» из справочника сотрудников (только /ui, не /api/v1)
        d["display_name"] = _staff_name(d["staff_id"], d.get("display_name"))
    return {
        "service_centers": dump(ref.service_centers.values()),
        "staff": staff,
        "work_types": dump(ref.work_types.values()),
        "access_nodes": nodes,
    }


DEFAULT_CORS_ORIGINS = ["http://localhost:5173", "http://127.0.0.1:5173", "http://26.123.45.67:5173",
                        "http://0.0.0.0:5173"]  # dev-фронт, в т.ч. адрес в Radmin VPN


def cors_origins() -> list[str]:
    """Разрешённые origin: env CORS_ORIGINS (через запятую), иначе список по умолчанию.
    "*" разрешает всех, но только если задан явно."""
    raw = os.environ.get("CORS_ORIGINS", "")
    items = [x.strip() for x in raw.split(",") if x.strip()]
    return items or list(DEFAULT_CORS_ORIGINS)


def install_ui(app: FastAPI) -> None:
    """Подключает /ui и CORS для dev-сервера фронтенда."""
    app.include_router(router)
    from .scenarios import router as scenario_router; app.include_router(scenario_router)  # noqa: E702
    from .history_api import router as history_router; app.include_router(history_router)  # noqa: E702
    from .employee_api import router as employee_router; app.include_router(employee_router)  # noqa: E702
    from .staff_directory import router as directory_router; app.include_router(directory_router)  # noqa: E702
    from .orders_ui import router as orders_router; app.include_router(orders_router)  # noqa: E702
    app.add_middleware(
        CORSMiddleware,
        allow_origins=cors_origins(),
        allow_methods=["*"],
        allow_headers=["*"],
    )
````

## File: data/api_spec.yaml
````yaml
openapi: 3.0.3
info:
  title: Умная диспетчеризация выездных бригад — API решения
  version: 1.0.0
  description: |
    Единый формат REST API, который реализует каждая команда в кейсе «Умная диспетчеризация
    выездных бригад» (Всероссийский хакатон связи 2026, ПАО «Ростелеком»).

    По этому API **оркестратор** организаторов загружает данные, получает план смены, передаёт
    события в течение дня и забирает актуальный план. **Валидатор** организаторов пересчитывает
    каждый план по единой матрице времени в пути и считает метрики. Отклонение от формата
    (имена полей, типы, коды HTTP) приводит к тому, что план не будет оценён.

    ## 1. Сценарий оценки одного дня

    | Шаг | Вызов | Лимит |
    |---|---|---|
    | 1 | `GET /api/v1/health` — ждём `200` после `docker compose up` | 120 с с момента старта |
    | 2 | `PUT /api/v1/reference` — СЦ, сотрудники, узлы доступа с домами, типы работ | 60 с |
    | 3 | `PUT /api/v1/reference/travel-matrix` — единая матрица времени в пути | 60 с |
    | 4 | `PUT /api/v1/days/{date}` — наряды дня, известные на момент `now` | 30 с |
    | 5 | `POST /api/v1/days/{date}/plan` — построение плана | **300 с** |
    | 6 | для каждого события по возрастанию `timestamp`: `POST /api/v1/days/{date}/events`, затем `GET /api/v1/days/{date}/plan` | **10 с** на событие |
    | 7 | итоговый `GET /api/v1/days/{date}/plan` — по нему считаются метрики | 30 с |

    Шаги 2–3 выполняются один раз за прогон, шаги 4–7 — для каждого из оцениваемых дней.
    Загрузка нового дня может удалять состояние предыдущего: сервис обязан держать как минимум
    один активный день.

    ## 2. Модельное время

    Сервис **не использует системные часы**. Текущее модельное время — это `now` из загрузки дня,
    а после событий — `timestamp` последнего принятого события.

    * Моменты времени — ISO 8601 со смещением: `2026-10-26T09:00:00+05:00` (Екатеринбург, UTC+5).
      Сервис принимает любое корректное смещение, в ответах рекомендуется `+05:00`.
    * Время суток в справочниках (смены, режим работы СЦ) — местное, формат `HH:MM`.
    * Точность — секунды. Допуск валидатора при сравнении времени — 1 с.
    * Длительности — в минутах (`*_min`) или секундах (`*_sec`), расстояния — в метрах (`*_m`).

    ## 3. Модель исполнения плана

    Оркестратор считает, что сотрудники выполняют **текущую версию плана в точности**, а все
    отклонения приходят событиями. Поэтому выполненную часть плана менять нельзя.
    В момент модельного времени `t`:

    * остановка с `start < t` считается начатой: в следующих версиях плана у неё нельзя менять
      сотрудника, наряд, `location_id`, `arrival` и `start`; `end` меняется только по событиям
      `VISIT_DELAYED` и `ORDER_TRANSFERRED`;
    * если у остановки изменился предшественник в маршруте или она новая, то
      `arrival ≥ max(end предшественника, t) + время в пути` — сотрудник не может оказаться
      у нового адреса раньше, чем узнал о нём;
    * визит по наряду из события `NEW_ORDER` начинается не раньше `timestamp` события;
    * отменённый наряд (`ORDER_CANCELLED`) не посещается, если визит ещё не начат;
    * у сотрудника из события `STAFF_UNAVAILABLE` не может быть остановок, начинающихся в период
      недоступности. Визит, начатый до `timestamp` события, считается выполненным по плану.

    ## 4. Жёсткие ограничения

    Валидатор проверяет каждую версию плана. Наряд, на котором нарушено ограничение, не
    засчитывается и даёт штраф (п. 6 кейса).

    | Код | Ограничение |
    |---|---|
    | H1 Допуск | хотя бы один профиль сотрудника входит в `work_type.profiles`; `order.technology` входит в `staff.technologies`; при `order.requires_height = true` нужен `staff.height_permit = true` |
    | H2 Смена | `DEPOT_START.start ≥ shift.start`; `DEPOT_END.arrival ≤ shift.end`; все остановки — внутри смены |
    | H3 Перерыв | если в маршруте есть визиты — ровно одна остановка `BREAK` длительностью не меньше `shift.break_duration_min`, `start` внутри `[break_window.from, break_window.to]`; перерыв не пересекается с другими остановками |
    | H4 Слот | `order.slot.start ≤ VISIT.start ≤ order.latest_start` |
    | H5 Норматив | `VISIT.end − VISIT.start = work_type.duration_min` (или момент из события `VISIT_DELAYED` / `ORDER_TRANSFERRED`) |
    | H6 Дорога | `arrival(k) ≥ end(k−1) + travel(location(k−1), location(k))` по единой матрице; `start ≥ arrival`; `end ≥ start` |
    | H7 АО | остаток в «рюкзаке» моделируется по маршруту: старт — `staff.equipment_start`; визит списывает `order.required_equipment` в момент `start`, остаток не может стать отрицательным; `RESTOCK` доводит остаток до `staff.equipment_capacity` |
    | H8 СЦ | `DEPOT_START` и `DEPOT_END` — в СЦ сотрудника (`staff.service_center_id`); `RESTOCK` — в любом СЦ, открытом на всём интервале `[start, end]`, длительность не меньше `restock_duration_min` |
    | H9 Учёт | каждый наряд дня (из загрузки и из `NEW_ORDER`) находится **ровно в одном** месте: визит в маршруте, `unassigned`, `pgp_groups[].order_ids` или `cancelled` |
    | H10 Заморозка | правила раздела 3 между последовательными версиями плана |

    Ограничения по типам остановок: первая остановка маршрута — `DEPOT_START`, последняя —
    `DEPOT_END` (у недоступного сотрудника `DEPOT_END` не обязателен); у `VISIT`
    `location_id = order.building_id`; у `BREAK` `location_id` совпадает с предыдущей остановкой,
    время в пути до перерыва равно 0.

    ## 5. Как план используется в оценке

    * Наряд выполнен успешно, если визит начат в окне `[slot.start, latest_start]`, завершён не
      позднее `deadline` и не нарушает H1–H10. Повторные обращения (`is_repeat = true`) идут с весом 2.
      Генератор данных гарантирует, что `latest_start + duration_min ≤ deadline`.
    * Время в пути и загрузка считаются по итоговой версии плана и единой матрице. Значения
      `travel_sec`, `distance_m`, `totals`, `summary` в ответе — справочные, валидатор их
      пересчитывает.
    * Предупреждение о риске опоздания своевременно, если `Alert` типа `LATE_RISK` по наряду создан
      (`created_at`) не позднее чем за 30 минут до `slot.start`. Оценивается по нарядам, которые
      после событий были переназначены другому сотруднику или сняты из маршрута.
    * ПГП: группы `pgp_groups` сравниваются с эталонными инцидентами по составу нарядов
      (точность и полнота) и по моменту `detected_at`.
    * Время ответа: план дольше 300 с — день не оценивается автоматически; событие дольше 10 с
      считается необработанным в срок.

    ## 6. Файлы исходных данных

    Файлы из п. 4 кейса описаны схемами `*File`. Все JSON — UTF-8, корневой элемент — массив
    (кроме `travel_matrix.json` и файлов сценария событий).

    | Файл | Схема | Куда передаётся |
    |---|---|---|
    | `service_centers.json` | `ServiceCentersFile` | `PUT /reference` → `service_centers` |
    | `staff.json` | `StaffFile` | `PUT /reference` → `staff` |
    | `access_nodes.json` | `AccessNodesFile` | `PUT /reference` → `access_nodes` |
    | `work_types.csv` | `WorkType` (строка CSV) | `PUT /reference` → `work_types` |
    | `travel_matrix.json` | `TravelMatrix` | `PUT /reference/travel-matrix` |
    | `orders_train.json` | `OrdersFile` | `PUT /days/{date}` → `orders` (наряды со слотом в этот день и `registered_at ≤ now`) |
    | `events_example.json` | `EventScenarioFile` | `events` → `POST /events`; `triggers` разворачивает оркестратор |

    `work_types.csv` — RFC 4180, разделитель запятая, UTF-8 без BOM, первая строка — заголовок с
    именами полей схемы `WorkType`; списки (`profiles`) записываются через `|`, пустое значение — `null`.

    ## 7. Общие соглашения

    * Базовый путь `/api/v1`, порт по умолчанию `8080`, тело запросов и ответов — `application/json`.
      Сервис принимает тела до 100 МБ (матрица времени в пути — около 20 МБ).
    * Идентификаторы — строки, регистр значим. Значения перечислений — строго как в спецификации.
    * Сервис игнорирует неизвестные поля во входных данных (например, `fact` в обучающих нарядах).
      В ответах допускаются дополнительные поля, валидатор их не читает; обязательные поля и типы — строго.
    * Ошибки возвращаются в формате `ErrorResponse`; при ошибке состояние сервиса не меняется.
    * Дополнительные эндпоинты (UI, сценарный анализ, объяснения) допускаются под любыми другими путями.
    * Авторизация не требуется.
  contact:
    name: Организаторы кейса «Умная диспетчеризация выездных бригад»
servers:
  - url: http://localhost:8080
    description: Сервис команды в Docker Compose
tags:
  - name: service
    description: Служебные методы
  - name: reference
    description: Справочные данные — один раз за прогон
  - name: day
    description: Наряды дня
  - name: plan
    description: Построение и получение плана
  - name: events
    description: События в течение смены и перепланирование
security: []

paths:
  /api/v1/health:
    get:
      tags: [service]
      operationId: getHealth
      summary: Проверка готовности сервиса
      description: Оркестратор опрашивает метод до первого ответа `200`, но не дольше 120 с после старта.
      responses:
        '200':
          description: Сервис готов принимать данные
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Health'
              example:
                status: ok
                team: Команда 17
                version: 1.0.3

  /api/v1/reference:
    put:
      tags: [reference]
      operationId: putReference
      summary: Загрузить справочники
      description: |
        Полностью заменяет справочные данные. Все загруженные ранее дни, планы и события удаляются.
        Сотрудники в `staff` работают в каждый загружаемый день, если не указаны в
        `DayInput.absent_staff_ids`; «рюкзак» в начале каждой смены — `equipment_start`.
      x-time-limit-sec: 60
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ReferenceData'
            examples:
              reference:
                $ref: '#/components/examples/ReferenceDataExample'
      responses:
        '200':
          description: Справочники загружены
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoadResult'
              example:
                status: loaded
                counts:
                  service_centers: 5
                  staff: 40
                  access_nodes: 142
                  buildings: 1260
                  work_types: 8
        '400':
          $ref: '#/components/responses/BadRequest'
        '422':
          $ref: '#/components/responses/Unprocessable'

  /api/v1/reference/travel-matrix:
    put:
      tags: [reference]
      operationId: putTravelMatrix
      summary: Загрузить единую матрицу времени в пути
      description: |
        Матрица покрывает все СЦ и все дома из `access_nodes[].buildings[]`. Её же использует
        валидатор, поэтому плановое время прибытия нужно считать по ней. Можно вызывать до или
        после `PUT /reference`; повторный вызов заменяет матрицу.
      x-time-limit-sec: 60
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/TravelMatrix'
            examples:
              matrix:
                $ref: '#/components/examples/TravelMatrixExample'
      responses:
        '200':
          description: Матрица загружена
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoadResult'
              example:
                status: loaded
                counts:
                  points: 1265
        '400':
          $ref: '#/components/responses/BadRequest'
        '422':
          $ref: '#/components/responses/Unprocessable'

  /api/v1/days/{date}:
    parameters:
      - $ref: '#/components/parameters/DateParam'
    put:
      tags: [day]
      operationId: putDay
      summary: Загрузить наряды дня
      description: |
        Загружает пул нарядов на дату `date` и задаёт модельное время `now`, на которое строится
        первоначальный план. Повторный вызов для той же даты сбрасывает план и журнал событий дня.
        Наряды, зарегистрированные позже `now`, приходят событиями `NEW_ORDER`.
      x-time-limit-sec: 30
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/DayInput'
            examples:
              day:
                $ref: '#/components/examples/DayInputExample'
      responses:
        '200':
          description: День загружен
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoadResult'
              example:
                status: loaded
                counts:
                  orders: 384
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          $ref: '#/components/responses/Conflict'
        '422':
          $ref: '#/components/responses/Unprocessable'

  /api/v1/days/{date}/plan:
    parameters:
      - $ref: '#/components/parameters/DateParam'
    post:
      tags: [plan]
      operationId: buildPlan
      summary: Построить план на день
      description: |
        Синхронно строит первоначальный план на момент `now` и возвращает его (`plan_version = 1`).
        Ответ — не позднее `time_limit_sec` и не позднее 300 с; таймауты HTTP-сервера и прокси
        должны быть не меньше 330 с. Повторный вызов до первого события перестраивает план заново;
        после первого принятого события — `409`.
      x-time-limit-sec: 300
      requestBody:
        required: false
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PlanRequest'
            example:
              time_limit_sec: 300
      responses:
        '200':
          description: План построен
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Plan'
              examples:
                plan:
                  $ref: '#/components/examples/PlanExample'
        '404':
          $ref: '#/components/responses/NotFound'
        '409':
          $ref: '#/components/responses/Conflict'
    get:
      tags: [plan]
      operationId: getPlan
      summary: Получить актуальный план
      description: |
        Возвращает текущую версию плана с учётом всех принятых событий. Результат должен
        отражать последнее событие сразу после ответа на `POST /events`.
      x-time-limit-sec: 30
      responses:
        '200':
          description: Актуальная версия плана
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Plan'
              examples:
                plan:
                  $ref: '#/components/examples/PlanExample'
        '404':
          $ref: '#/components/responses/NotFound'

  /api/v1/days/{date}/events:
    parameters:
      - $ref: '#/components/parameters/DateParam'
    post:
      tags: [events]
      operationId: postEvent
      summary: Передать событие и перепланировать
      description: |
        Принимает одно событие, переводит модельное время на `timestamp`, перестраивает
        невыполненную часть плана и обновляет предупреждения. Ответ — не позднее 10 с.

        * События приходят в порядке неубывания `timestamp`; одинаковый `timestamp` допустим.
        * Повтор события с уже принятым `event_id` не меняет состояние: ответ `200`
          с `duplicate = true` и тем же `plan_version`.
        * Каждое принятое (не повторное) событие увеличивает `plan_version` на 1, даже если план
          не изменился.
        * Событие, которое ссылается на неизвестный наряд или сотрудника либо противоречит
          состоянию плана, отклоняется с `422`; состояние не меняется.
      x-time-limit-sec: 10
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Event'
            examples:
              new_order:
                $ref: '#/components/examples/NewOrderEventExample'
              order_cancelled:
                $ref: '#/components/examples/OrderCancelledEventExample'
              visit_delayed:
                $ref: '#/components/examples/VisitDelayedEventExample'
              order_transferred:
                $ref: '#/components/examples/OrderTransferredEventExample'
              staff_unavailable:
                $ref: '#/components/examples/StaffUnavailableEventExample'
              time_tick:
                $ref: '#/components/examples/TimeTickEventExample'
      responses:
        '200':
          description: Событие обработано (или уже было обработано ранее)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/EventResult'
              examples:
                result:
                  $ref: '#/components/examples/EventResultExample'
        '400':
          $ref: '#/components/responses/BadRequest'
        '404':
          $ref: '#/components/responses/NotFound'
        '409':
          $ref: '#/components/responses/Conflict'
        '422':
          $ref: '#/components/responses/Unprocessable'

components:
  parameters:
    DateParam:
      name: date
      in: path
      required: true
      description: Дата рабочего дня (местная), `YYYY-MM-DD`
      schema:
        type: string
        format: date
        example: '2026-10-26'

  responses:
    BadRequest:
      description: Некорректный JSON или нарушение схемы
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
          example:
            error:
              code: BAD_REQUEST
              message: 'Поле slot.end: ожидается date-time'
              details:
                - field: orders[12].slot.end
                  message: ожидается date-time
    NotFound:
      description: День не загружен или план ещё не построен
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
          example:
            error:
              code: PLAN_NOT_FOUND
              message: План на 2026-10-26 ещё не построен
              details: []
    Conflict:
      description: Нарушен порядок вызовов (нет справочников, событие раньше предыдущего, план уже перепланировался)
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
          example:
            error:
              code: EVENT_OUT_OF_ORDER
              message: timestamp 2026-10-26T10:00:00+05:00 раньше последнего события 2026-10-26T10:05:00+05:00
              details: []
    Unprocessable:
      description: Данные корректны по схеме, но противоречат справочникам или состоянию
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
          example:
            error:
              code: UNKNOWN_ORDER
              message: Наряд ORD-20261026-9999 не найден в дне 2026-10-26
              details:
                - field: order_id
                  message: неизвестный идентификатор

  schemas:
    # ---------- базовые типы ----------
    DateTime:
      type: string
      format: date-time
      description: Момент времени ISO 8601 со смещением
      example: '2026-10-26T09:00:00+05:00'
    LocalTime:
      type: string
      pattern: '^([01][0-9]|2[0-3]):[0-5][0-9]$'
      description: Местное время суток `HH:MM`
      example: '09:00'
    TimeWindow:
      type: object
      required: [start, end]
      additionalProperties: false
      properties:
        start:
          $ref: '#/components/schemas/DateTime'
        end:
          $ref: '#/components/schemas/DateTime'
    Technology:
      type: string
      enum: [PON, FTTB, XDSL, DOCSIS]
      description: Технология подключения абонента
    Profile:
      type: string
      enum: [INSTALL, LTP3]
      description: '`INSTALL` — инсталляция (подключение услуг), `LTP3` — третья линия технической поддержки (восстановление услуг)'
    WorkTypeCode:
      type: string
      enum:
        - INST_PON_MKD
        - INST_FTTB_MKD
        - INST_PRIVATE_VLS
        - ADD_SERVICE
        - SURVEY_PRIVATE
        - REPAIR
        - DEGRADATION
        - DISMANTLE
      description: |
        | Код | Тип работ |
        |---|---|
        | `INST_PON_MKD` | Инсталляция ШПД/IPTV, МКД, PON |
        | `INST_FTTB_MKD` | Инсталляция ШПД/IPTV, МКД, FTTB |
        | `INST_PRIVATE_VLS` | Инсталляция, частный сектор, с ВЛС |
        | `ADD_SERVICE` | Доподключение услуги (IPTV, Wi-Fi-роутер) |
        | `SURVEY_PRIVATE` | Обследование (частный сектор) |
        | `REPAIR` | Устранение неисправности (обращение абонента) |
        | `DEGRADATION` | Устранение деградации, выявленной системой мониторинга качества |
        | `DISMANTLE` | Демонтаж АО |
    BuildingType:
      type: string
      enum: [MKD, PRIVATE]
      description: '`MKD` — многоквартирный дом, `PRIVATE` — частный дом'
    Equipment:
      type: object
      description: Количество абонентского оборудования (АО) по видам
      required: [ONT, ROUTER, STB]
      additionalProperties: false
      properties:
        ONT:
          type: integer
          minimum: 0
          description: Оптический терминал (PON)
        ROUTER:
          type: integer
          minimum: 0
          description: Роутер (в т.ч. Wi-Fi, xDSL- и DOCSIS-модем)
        STB:
          type: integer
          minimum: 0
          description: ТВ-приставка
      example:
        ONT: 1
        ROUTER: 1
        STB: 0

    # ---------- справочники ----------
    ServiceCenter:
      type: object
      required: [service_center_id, name, address, lat, lon, opening_hours, restock_duration_min]
      properties:
        service_center_id:
          type: string
          pattern: '^SC-[0-9]{2}$'
          example: SC-01
        name:
          type: string
          example: СЦ «Север»
        address:
          type: string
          example: г. Екатеринбург, ул. Победы, 30
        lat:
          type: number
          format: double
          minimum: -90
          maximum: 90
          example: 56.8941
        lon:
          type: number
          format: double
          minimum: -180
          maximum: 180
          example: 60.6105
        opening_hours:
          type: object
          description: Режим работы, в котором доступно пополнение АО
          required: [open, close]
          additionalProperties: false
          properties:
            open:
              $ref: '#/components/schemas/LocalTime'
            close:
              $ref: '#/components/schemas/LocalTime'
        restock_duration_min:
          type: integer
          minimum: 0
          description: Время на пополнение «рюкзака» в СЦ, мин
          example: 15
    ServiceCentersFile:
      type: array
      description: Содержимое `service_centers.json`
      items:
        $ref: '#/components/schemas/ServiceCenter'

    Shift:
      type: object
      required: [start, end, break_duration_min, break_window]
      additionalProperties: false
      properties:
        start:
          $ref: '#/components/schemas/LocalTime'
        end:
          $ref: '#/components/schemas/LocalTime'
        break_duration_min:
          type: integer
          minimum: 0
          example: 60
        break_window:
          type: object
          description: Интервал, в котором должен **начаться** обеденный перерыв
          required: [from, to]
          additionalProperties: false
          properties:
            from:
              $ref: '#/components/schemas/LocalTime'
            to:
              $ref: '#/components/schemas/LocalTime'
      example:
        start: '09:00'
        end: '18:00'
        break_duration_min: 60
        break_window:
          from: '12:00'
          to: '15:00'
    Staff:
      type: object
      required:
        - staff_id
        - service_center_id
        - profiles
        - technologies
        - height_permit
        - shift
        - equipment_start
        - equipment_capacity
      properties:
        staff_id:
          type: string
          pattern: '^ST-[0-9]{3}$'
          example: ST-001
        display_name:
          type: string
          description: Условное имя для интерфейса (данные синтетические)
          example: Монтажник 001
        service_center_id:
          type: string
          description: СЦ, к которому прикреплён сотрудник; маршрут начинается и заканчивается здесь
          example: SC-01
        profiles:
          type: array
          minItems: 1
          uniqueItems: true
          items:
            $ref: '#/components/schemas/Profile'
        technologies:
          type: array
          minItems: 1
          uniqueItems: true
          items:
            $ref: '#/components/schemas/Technology'
        height_permit:
          type: boolean
          description: Допуск к работам на высоте (нужен для нарядов с `requires_height = true`)
        shift:
          $ref: '#/components/schemas/Shift'
        equipment_start:
          allOf:
            - $ref: '#/components/schemas/Equipment'
          description: Остаток АО в «рюкзаке» в начале каждой смены
        equipment_capacity:
          allOf:
            - $ref: '#/components/schemas/Equipment'
          description: Остаток после пополнения в СЦ (`RESTOCK`); не меньше `equipment_start`
        experience_years:
          type: number
          minimum: 0
          description: Стаж, лет; справочно — для задачи прогноза длительности работ
          example: 4.5
    StaffFile:
      type: array
      description: Содержимое `staff.json`
      items:
        $ref: '#/components/schemas/Staff'

    Building:
      type: object
      required: [building_id, address, lat, lon, building_type, overhead_line]
      properties:
        building_id:
          type: string
          pattern: '^B-[0-9]{5}$'
          example: B-00412
        address:
          type: string
          example: г. Екатеринбург, ул. Учителей, 18
        lat:
          type: number
          format: double
          example: 56.8712
        lon:
          type: number
          format: double
          example: 60.6531
        building_type:
          $ref: '#/components/schemas/BuildingType'
        overhead_line:
          type: boolean
          description: Подключение по воздушной линии связи (ВЛС) — работы на высоте
        zone_service_center_id:
          type: string
          description: СЦ, в зону которого входит дом; справочно, назначать сотрудников из других СЦ можно
          example: SC-01
    AccessNode:
      type: object
      required: [access_node_id, node_type, technology, name, lat, lon, buildings]
      properties:
        access_node_id:
          type: string
          pattern: '^AN-[0-9]{4}$'
          example: AN-0031
        node_type:
          type: string
          enum: [OLT, SWITCH, DSLAM, CMTS]
          description: '`OLT` — PON, `SWITCH` — FTTB, `DSLAM` — xDSL, `CMTS` — DOCSIS'
        technology:
          $ref: '#/components/schemas/Technology'
        name:
          type: string
          example: OLT Пионерский-2
        address:
          type: string
          example: г. Екатеринбург, ул. Учителей, 22
        lat:
          type: number
          format: double
          example: 56.8698
        lon:
          type: number
          format: double
          example: 60.6510
        buildings:
          type: array
          minItems: 1
          description: Дома, которые обслуживает узел. Дом с несколькими технологиями встречается в нескольких узлах с одинаковыми атрибутами
          items:
            $ref: '#/components/schemas/Building'
    AccessNodesFile:
      type: array
      description: Содержимое `access_nodes.json`
      items:
        $ref: '#/components/schemas/AccessNode'

    WorkType:
      type: object
      description: Строка справочника `work_types.csv`
      required:
        - code
        - name
        - profiles
        - duration_min
        - max_start_delay_min
        - deadline_kind
        - deadline_value
        - repeat_deadline_value
        - pgp_relevant
      properties:
        code:
          $ref: '#/components/schemas/WorkTypeCode'
        name:
          type: string
          example: Устранение неисправности (обращение абонента)
        profiles:
          type: array
          minItems: 1
          uniqueItems: true
          items:
            $ref: '#/components/schemas/Profile'
          description: Профили, допускаемые к работам; в CSV — через `|`, например `INSTALL|LTP3`
        duration_min:
          type: integer
          minimum: 1
          description: Норматив длительности работ, мин
          example: 60
        max_start_delay_min:
          type: integer
          nullable: true
          minimum: 0
          description: |
            Максимальная задержка начала визита от начала слота, мин. Для инсталляции — 10
            («не позднее 10 минут после начала слота»), `null` — визит можно начать в любой момент слота
          example: 10
        deadline_kind:
          type: string
          enum: [SLOT, HOURS, WORKING_DAYS]
          description: |
            Как задан контрольный срок: `SLOT` — визит в слот; `HOURS` — часов с регистрации;
            `WORKING_DAYS` — рабочих дней с регистрации (производственный календарь РФ).
            Конкретный срок уже рассчитан в `Order.deadline`
        deadline_value:
          type: integer
          nullable: true
          minimum: 1
          description: Значение срока в единицах `deadline_kind`; `null` для `SLOT`
          example: 24
        repeat_deadline_value:
          type: integer
          nullable: true
          minimum: 1
          description: Срок для повторного обращения в тех же единицах (`REPAIR` — 12 ч); `null` — как у обычного
          example: 12
        pgp_relevant:
          type: boolean
          description: Наряды этого типа учитываются при выявлении ПГП (`REPAIR`, `DEGRADATION`)

    TravelMatrix:
      type: object
      description: |
        Единая матрица времени в пути по дорожной сети (содержимое `travel_matrix.json`).
        Время из точки `points[i]` в точку `points[j]` — `durations_sec[i][j]`. Значение — полное
        время от остановки до остановки, включая парковку и подход к подъезду; из точки в саму
        себя — 0 (два наряда в одном доме). Матрица может быть несимметричной.
      required: [matrix_id, points, durations_sec, distances_m]
      properties:
        matrix_id:
          type: string
          description: Версия матрицы
          example: ekb-2026-10-v1
        points:
          type: array
          description: Идентификаторы точек — все `service_center_id` и все `building_id`
          minItems: 2
          uniqueItems: true
          items:
            type: string
        durations_sec:
          type: array
          description: Квадратная матрица N×N, секунды
          items:
            type: array
            items:
              type: integer
              minimum: 0
        distances_m:
          type: array
          description: Квадратная матрица N×N, метры
          items:
            type: array
            items:
              type: integer
              minimum: 0
        hourly_factors:
          type: array
          description: |
            Коэффициенты загруженности дорог по часу отправления (дополнительное задание).
            Базовая оценка их **не применяет**: проверка H6 идёт по `durations_sec` без коэффициентов
          items:
            type: object
            required: [hour, factor]
            additionalProperties: false
            properties:
              hour:
                type: integer
                minimum: 0
                maximum: 23
              factor:
                type: number
                minimum: 0
                example: 1.35

    ReferenceData:
      type: object
      required: [service_centers, staff, access_nodes, work_types]
      properties:
        service_centers:
          type: array
          minItems: 1
          items:
            $ref: '#/components/schemas/ServiceCenter'
        staff:
          type: array
          minItems: 1
          items:
            $ref: '#/components/schemas/Staff'
        access_nodes:
          type: array
          minItems: 1
          items:
            $ref: '#/components/schemas/AccessNode'
        work_types:
          type: array
          minItems: 1
          items:
            $ref: '#/components/schemas/WorkType'

    # ---------- наряды ----------
    Order:
      type: object
      required:
        - order_id
        - work_type_code
        - technology
        - building_id
        - address
        - lat
        - lon
        - building_type
        - requires_height
        - access_node_id
        - registered_at
        - slot
        - latest_start
        - deadline
        - required_equipment
        - is_repeat
      properties:
        order_id:
          type: string
          pattern: '^ORD-[0-9]{8}-[0-9]{4}$'
          example: ORD-20261026-0001
        work_type_code:
          $ref: '#/components/schemas/WorkTypeCode'
        technology:
          $ref: '#/components/schemas/Technology'
        building_id:
          type: string
          description: Дом из реестра `access_nodes[].buildings[]`; по нему берётся время в пути из матрицы
          example: B-00412
        address:
          type: string
          example: г. Екатеринбург, ул. Учителей, 18
        lat:
          type: number
          format: double
          example: 56.8712
        lon:
          type: number
          format: double
          example: 60.6531
        building_type:
          $ref: '#/components/schemas/BuildingType'
        requires_height:
          type: boolean
          description: Частный сектор с ВЛС — нужен допуск к работам на высоте
        access_node_id:
          type: string
          description: Узел доступа, через который подключён абонент; используется для выявления ПГП
          example: AN-0031
        registered_at:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          description: Момент регистрации наряда
        slot:
          allOf:
            - $ref: '#/components/schemas/TimeWindow'
          description: Согласованный с абонентом временной слот, 2 часа
        latest_start:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          description: |
            Последний допустимый момент начала визита:
            `min(slot.end, slot.start + max_start_delay_min)`; для инсталляции — `slot.start + 10 мин`
        deadline:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          description: |
            Контрольный срок — момент, до которого работы должны быть завершены. Уже рассчитан по
            типу работ, `registered_at`, признаку повторности и производственному календарю.
            Для типов с контрольным сроком `SLOT` — `latest_start + duration_min`
        required_equipment:
          $ref: '#/components/schemas/Equipment'
        is_repeat:
          type: boolean
          description: Повторное обращение — тот же абонент и услуга в течение 7 дней после закрытия предыдущего наряда; вес в оценке — 2
        previous_order_id:
          type: string
          nullable: true
          description: Предыдущий наряд абонента для повторного обращения (может отсутствовать в наборе)
        fact:
          $ref: '#/components/schemas/OrderFact'
    OrderFact:
      type: object
      nullable: true
      description: |
        Фактическое исполнение — только в обучающем наборе `orders_train.json` (история для
        аналитики и прогноза длительности). В скрытом наборе отсутствует, сервис поле игнорирует
      properties:
        outcome:
          type: string
          enum: [COMPLETED, TRANSFERRED_TO_STATION, CANCELLED, RESCHEDULED, PGP_GROUPED]
        staff_id:
          type: string
          nullable: true
        actual_start:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          nullable: true
        actual_duration_min:
          type: integer
          nullable: true
          minimum: 0
        pgp_incident_id:
          type: string
          nullable: true
          description: Эталонный инцидент ПГП, к которому относится наряд
    OrdersFile:
      type: array
      description: Содержимое `orders_train.json` — наряды за все обучающие дни; день наряда — дата `slot.start`
      items:
        $ref: '#/components/schemas/Order'

    DayInput:
      type: object
      required: [date, now, orders]
      properties:
        date:
          type: string
          format: date
          example: '2026-10-26'
        now:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          description: Модельное время построения первоначального плана (обычно 08:30 этого дня)
        orders:
          type: array
          description: Наряды со слотом в день `date`, зарегистрированные не позднее `now`
          items:
            $ref: '#/components/schemas/Order'
        absent_staff_ids:
          type: array
          description: Сотрудники, которые в этот день не выходят на смену
          default: []
          uniqueItems: true
          items:
            type: string
    PlanRequest:
      type: object
      properties:
        time_limit_sec:
          type: integer
          minimum: 1
          maximum: 300
          default: 300
          description: Лимит времени расчёта; сервис обязан ответить не позже

    # ---------- план ----------
    Plan:
      type: object
      required:
        - date
        - plan_version
        - as_of
        - routes
        - unassigned
        - pgp_groups
        - cancelled
        - alerts
      properties:
        date:
          type: string
          format: date
          example: '2026-10-26'
        plan_version:
          type: integer
          minimum: 1
          description: 1 — первоначальный план; +1 за каждое принятое событие
          example: 1
        based_on_event_id:
          type: string
          nullable: true
          description: Последнее учтённое событие; `null` для первоначального плана
        as_of:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          description: Модельное время, на которое актуален план
        computation_ms:
          type: integer
          minimum: 0
          description: Время расчёта этой версии, мс (справочно)
        routes:
          type: array
          description: По одному маршруту на каждого сотрудника на смене, в том числе без визитов
          items:
            $ref: '#/components/schemas/Route'
        unassigned:
          type: array
          description: Наряды, не включённые в маршруты, с причиной (кроме ПГП и отменённых)
          items:
            $ref: '#/components/schemas/UnassignedOrder'
        pgp_groups:
          type: array
          items:
            $ref: '#/components/schemas/PgpGroup'
        cancelled:
          type: array
          items:
            $ref: '#/components/schemas/CancelledOrder'
        alerts:
          type: array
          description: Все предупреждения за день, включая закрытые. Список только пополняется — удалять предупреждения нельзя
          items:
            $ref: '#/components/schemas/Alert'
        summary:
          $ref: '#/components/schemas/PlanSummary'

    Route:
      type: object
      required: [staff_id, service_center_id, status, stops]
      properties:
        staff_id:
          type: string
          example: ST-001
        service_center_id:
          type: string
          example: SC-01
        status:
          type: string
          enum: [ACTIVE, UNAVAILABLE]
          description: '`UNAVAILABLE` — сотрудник выбыл по событию `STAFF_UNAVAILABLE`'
        unavailable_from:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          nullable: true
        unavailable_until:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          nullable: true
          description: '`null` — до конца смены'
        stops:
          type: array
          description: Остановки в порядке выполнения. Пустой список — сотрудник не задействован
          items:
            $ref: '#/components/schemas/Stop'
        totals:
          $ref: '#/components/schemas/RouteTotals'

    Stop:
      type: object
      required: [seq, type, location_id, arrival, start, end]
      properties:
        seq:
          type: integer
          minimum: 1
          description: Порядковый номер в маршруте, 1, 2, 3… без пропусков
        type:
          type: string
          enum: [DEPOT_START, VISIT, BREAK, RESTOCK, DEPOT_END]
          description: |
            | Тип | Смысл | Время |
            |---|---|---|
            | `DEPOT_START` | выезд из своего СЦ | `arrival = start = end` — момент выезда |
            | `VISIT` | работы по наряду | `arrival` — прибытие, `start`…`end` — работы по нормативу |
            | `BREAK` | обеденный перерыв на месте предыдущей остановки | `start`…`end` — перерыв |
            | `RESTOCK` | пополнение АО в любом открытом СЦ | `start`…`end` — не меньше `restock_duration_min` |
            | `DEPOT_END` | возвращение в свой СЦ | `arrival = start = end` — момент прибытия |
        location_id:
          type: string
          description: '`building_id` для `VISIT`, `service_center_id` для `DEPOT_*` и `RESTOCK`, точка предыдущей остановки для `BREAK`'
          example: B-00412
        order_id:
          type: string
          nullable: true
          description: Обязателен для `VISIT`, для остальных типов — `null` или отсутствует
          example: ORD-20261026-0001
        arrival:
          $ref: '#/components/schemas/DateTime'
        start:
          $ref: '#/components/schemas/DateTime'
        end:
          $ref: '#/components/schemas/DateTime'
        travel_sec:
          type: integer
          minimum: 0
          description: Время в пути от предыдущей остановки по матрице (справочно)
        distance_m:
          type: integer
          minimum: 0
          description: Расстояние от предыдущей остановки по матрице (справочно)
        status:
          type: string
          enum: [PLANNED, IN_PROGRESS, DONE]
          description: Состояние на момент `as_of` (справочно)
        equipment_after:
          allOf:
            - $ref: '#/components/schemas/Equipment'
          description: Остаток АО после остановки (справочно)
        explanation:
          type: string
          description: Почему наряд назначен этому сотруднику (дополнительное задание)
          example: Ближайший свободный сотрудник с PON; в запасе 25 мин до конца слота
    RouteTotals:
      type: object
      description: Итоги маршрута (справочно, валидатор пересчитывает)
      properties:
        visits:
          type: integer
          minimum: 0
        travel_sec:
          type: integer
          minimum: 0
        distance_m:
          type: integer
          minimum: 0
        work_min:
          type: integer
          minimum: 0
        utilization:
          type: number
          minimum: 0
          description: (работы + дорога) / (смена − перерыв)

    UnassignedOrder:
      type: object
      required: [order_id, reason_code, reason_text, suggested_action]
      properties:
        order_id:
          type: string
          example: ORD-20261026-0233
        reason_code:
          type: string
          enum:
            - NO_QUALIFIED_STAFF
            - NO_SHIFT_TIME
            - SLOT_UNREACHABLE
            - NO_EQUIPMENT
            - STAFF_UNAVAILABLE
            - OTHER
          description: |
            | Код | Причина |
            |---|---|
            | `NO_QUALIFIED_STAFF` | на смене нет сотрудника с нужным профилем, технологией или допуском к высоте |
            | `NO_SHIFT_TIME` | у подходящих сотрудников не хватает времени смены |
            | `SLOT_UNREACHABLE` | ни один подходящий сотрудник не успевает начать визит в слот |
            | `NO_EQUIPMENT` | нет требуемого АО, а заезд в СЦ не помещается в маршрут |
            | `STAFF_UNAVAILABLE` | исполнитель выбыл, замену найти не удалось |
            | `OTHER` | другое — пояснить в `reason_text` |
        reason_text:
          type: string
          example: На смене нет свободного сотрудника с допуском к работам на высоте и технологией PON
        suggested_action:
          type: string
          enum: [RESCHEDULE, REASSIGN, ESCALATE]
          description: '`RESCHEDULE` — перенос визита, `REASSIGN` — передать другому подразделению/смене, `ESCALATE` — решение диспетчера'
        proposed_slot:
          allOf:
            - $ref: '#/components/schemas/TimeWindow'
          nullable: true
          description: Предлагаемый новый слот при переносе
        since:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          nullable: true
          description: С какого модельного времени наряд не распределён
    PgpGroup:
      type: object
      description: |
        Подозрение на групповую проблему: наряды на неисправность (`work_type.pgp_relevant = true`)
        с одного узла доступа за короткое время. Такие наряды не распределяются по отдельности и
        передаются в станционное подразделение. Наряд ПГП, визит по которому уже начат, остаётся
        в маршруте и в группу не включается.

        Эталонная разметка организаторов: ПГП — не менее 3 таких нарядов с одного узла доступа,
        зарегистрированных в пределах 60 минут. Случайные совпадения в данных не превышают
        2 нарядов с узла за 60 минут.
      required: [group_id, access_node_id, order_ids, detected_at, action]
      properties:
        group_id:
          type: string
          example: PGP-20261026-01
        access_node_id:
          type: string
          example: AN-0057
        order_ids:
          type: array
          minItems: 2
          uniqueItems: true
          items:
            type: string
        detected_at:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          description: Модельное время выявления (не меняется при пополнении группы)
        action:
          type: string
          enum: [TRANSFER_TO_STATION]
        comment:
          type: string
    CancelledOrder:
      type: object
      required: [order_id, cancelled_at]
      properties:
        order_id:
          type: string
        cancelled_at:
          $ref: '#/components/schemas/DateTime'
        event_id:
          type: string
          nullable: true
    Alert:
      type: object
      description: |
        Предупреждение для диспетчера. `LATE_RISK` создаётся, как только по текущему прогнозу
        сотрудник не успевает начать визит в слот, вместе с предложением: передать наряд другому
        сотруднику (`REASSIGN`) или перенести визит (`RESCHEDULE`). Если сервис сам применил
        предложение в плане, `applied = true`.
      required: [alert_id, created_at, type, status, suggested_action, message]
      properties:
        alert_id:
          type: string
          description: Уникален в пределах дня
          example: ALR-20261026-0007
        created_at:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          description: Модельное время создания; по нему оценивается своевременность
        type:
          type: string
          enum: [LATE_RISK, PGP_DETECTED, UNASSIGNED]
        status:
          type: string
          enum: [ACTIVE, RESOLVED]
        resolved_at:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          nullable: true
        order_id:
          type: string
          nullable: true
          description: Обязателен для `LATE_RISK` и `UNASSIGNED`
        group_id:
          type: string
          nullable: true
          description: Обязателен для `PGP_DETECTED`
        staff_id:
          type: string
          nullable: true
          description: Текущий исполнитель на момент создания
        forecast_start:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          nullable: true
          description: Прогноз начала визита без вмешательства
        lateness_min:
          type: integer
          nullable: true
          description: Прогноз опоздания относительно `latest_start`, мин
        suggested_action:
          type: string
          enum: [REASSIGN, RESCHEDULE, TRANSFER_TO_STATION, NONE]
        suggested_staff_id:
          type: string
          nullable: true
          description: Для `REASSIGN`
        proposed_slot:
          allOf:
            - $ref: '#/components/schemas/TimeWindow'
          nullable: true
          description: Для `RESCHEDULE`
        applied:
          type: boolean
          default: false
          description: Предложение уже применено в текущем плане
        message:
          type: string
          example: ST-014 выбыл, визит по ORD-20261026-0188 не начнётся до 15:00 — передан ST-003
    PlanSummary:
      type: object
      description: Сводка (справочно, валидатор пересчитывает)
      properties:
        orders_total:
          type: integer
        visits_planned:
          type: integer
        unassigned:
          type: integer
        pgp_orders:
          type: integer
        cancelled:
          type: integer
        travel_sec_total:
          type: integer
        utilization_cv:
          type: number
          description: Коэффициент вариации загрузки сотрудников

    # ---------- события ----------
    EventType:
      type: string
      enum:
        - NEW_ORDER
        - ORDER_CANCELLED
        - VISIT_DELAYED
        - ORDER_TRANSFERRED
        - STAFF_UNAVAILABLE
        - TIME_TICK
      description: |
        | Тип | Что произошло |
        |---|---|
        | `NEW_ORDER` | зарегистрирован новый наряд со слотом сегодня |
        | `ORDER_CANCELLED` | абонент не подтвердил визит при звонке или отказался |
        | `VISIT_DELAYED` | работы на объекте затягиваются, визит закончится в `expected_end` |
        | `ORDER_TRANSFERRED` | повреждение вне зоны ответственности 3ЛТП: наряд передан в профильное подразделение, визит закончился в `actual_end` |
        | `STAFF_UNAVAILABLE` | сотрудник недоступен с `timestamp` до `until` |
        | `TIME_TICK` | ход модельного времени без событий: сервис обновляет прогнозы и предупреждения |
    EventBase:
      type: object
      required: [event_id, type, timestamp]
      properties:
        event_id:
          type: string
          description: Уникален в пределах дня; повтор — идемпотентен
          example: EV-20261026-0001
        type:
          $ref: '#/components/schemas/EventType'
        timestamp:
          allOf:
            - $ref: '#/components/schemas/DateTime'
          description: Модельное время события
    NewOrderEvent:
      allOf:
        - $ref: '#/components/schemas/EventBase'
        - type: object
          required: [type, order]
          properties:
            type:
              type: string
              enum: [NEW_ORDER]
            order:
              $ref: '#/components/schemas/Order'
    OrderCancelledEvent:
      allOf:
        - $ref: '#/components/schemas/EventBase'
        - type: object
          required: [type, order_id, reason]
          properties:
            type:
              type: string
              enum: [ORDER_CANCELLED]
            order_id:
              type: string
            reason:
              type: string
              enum: [NOT_CONFIRMED, CUSTOMER_REQUEST]
    VisitDelayedEvent:
      allOf:
        - $ref: '#/components/schemas/EventBase'
        - type: object
          required: [type, staff_id, order_id, expected_end]
          description: Приходит, когда визит уже начат (`start ≤ timestamp`)
          properties:
            type:
              type: string
              enum: [VISIT_DELAYED]
            staff_id:
              type: string
            order_id:
              type: string
            expected_end:
              allOf:
                - $ref: '#/components/schemas/DateTime'
              description: Новый момент окончания работ, позже планового
    OrderTransferredEvent:
      allOf:
        - $ref: '#/components/schemas/EventBase'
        - type: object
          required: [type, staff_id, order_id, actual_end, transferred_to]
          description: Приходит, когда визит уже начат; наряд считается обслуженным на месте, время сотрудника освобождается раньше
          properties:
            type:
              type: string
              enum: [ORDER_TRANSFERRED]
            staff_id:
              type: string
            order_id:
              type: string
            actual_end:
              $ref: '#/components/schemas/DateTime'
            transferred_to:
              type: string
              enum: [STATION_EQUIPMENT, TRUNK_CABLE]
              description: Станционное оборудование или магистральный кабель
    StaffUnavailableEvent:
      allOf:
        - $ref: '#/components/schemas/EventBase'
        - type: object
          required: [type, staff_id, reason]
          properties:
            type:
              type: string
              enum: [STAFF_UNAVAILABLE]
            staff_id:
              type: string
            until:
              allOf:
                - $ref: '#/components/schemas/DateTime'
              nullable: true
              description: Конец недоступности; `null` или отсутствует — до конца смены. После `until` сотрудник продолжает смену из своего СЦ
            reason:
              type: string
              enum: [VEHICLE_BREAKDOWN, ILLNESS, OTHER]
    TimeTickEvent:
      allOf:
        - $ref: '#/components/schemas/EventBase'
        - type: object
          required: [type]
          properties:
            type:
              type: string
              enum: [TIME_TICK]
    Event:
      oneOf:
        - $ref: '#/components/schemas/NewOrderEvent'
        - $ref: '#/components/schemas/OrderCancelledEvent'
        - $ref: '#/components/schemas/VisitDelayedEvent'
        - $ref: '#/components/schemas/OrderTransferredEvent'
        - $ref: '#/components/schemas/StaffUnavailableEvent'
        - $ref: '#/components/schemas/TimeTickEvent'
      discriminator:
        propertyName: type
        mapping:
          NEW_ORDER: '#/components/schemas/NewOrderEvent'
          ORDER_CANCELLED: '#/components/schemas/OrderCancelledEvent'
          VISIT_DELAYED: '#/components/schemas/VisitDelayedEvent'
          ORDER_TRANSFERRED: '#/components/schemas/OrderTransferredEvent'
          STAFF_UNAVAILABLE: '#/components/schemas/StaffUnavailableEvent'
          TIME_TICK: '#/components/schemas/TimeTickEvent'

    EventResult:
      type: object
      required: [event_id, accepted, duplicate, plan_version, as_of]
      properties:
        event_id:
          type: string
        accepted:
          type: boolean
        duplicate:
          type: boolean
          description: Событие с таким `event_id` уже было принято, состояние не изменилось
        plan_version:
          type: integer
          minimum: 1
        as_of:
          $ref: '#/components/schemas/DateTime'
        processing_ms:
          type: integer
          minimum: 0
        changed_staff_ids:
          type: array
          description: Сотрудники, чьи маршруты изменились (справочно)
          items:
            type: string
        new_alerts:
          type: array
          description: Предупреждения, созданные при обработке события
          items:
            $ref: '#/components/schemas/Alert'
        message:
          type: string

    # ---------- сценарий событий (файл, не API) ----------
    EventScenarioFile:
      type: object
      description: |
        Сценарий дня для оркестратора (`events_example.json` и скрытые сценарии).
        `events` отправляются в `POST /events` как есть, в порядке `timestamp`.
        `triggers` привязаны к визиту по наряду, а исполнитель и время визита у каждой команды
        свои, поэтому оркестратор превращает триггер в событие по текущему плану команды:
        `timestamp = VISIT.start + fire_after_start_min`, `staff_id` — исполнитель из плана.
        Перед отправкой каждого события из `events` оркестратор отправляет сработавшие к этому
        моменту триггеры. Если наряд к моменту срабатывания не в маршруте, триггер пропускается.
      required: [date, now, events, triggers]
      properties:
        date:
          type: string
          format: date
        now:
          $ref: '#/components/schemas/DateTime'
        events:
          type: array
          items:
            $ref: '#/components/schemas/Event'
        triggers:
          type: array
          items:
            $ref: '#/components/schemas/ScenarioTrigger'
    ScenarioTrigger:
      type: object
      required: [trigger_id, type, order_id, fire_after_start_min]
      properties:
        trigger_id:
          type: string
          example: TR-20261026-01
        type:
          type: string
          enum: [VISIT_DELAYED, ORDER_TRANSFERRED]
        order_id:
          type: string
        fire_after_start_min:
          type: integer
          minimum: 0
          description: Через сколько минут после начала визита наступает событие
        extra_duration_min:
          type: integer
          minimum: 1
          nullable: true
          description: Для `VISIT_DELAYED` — `expected_end = VISIT.start + duration_min + extra_duration_min`
        transferred_to:
          type: string
          enum: [STATION_EQUIPMENT, TRUNK_CABLE]
          nullable: true
          description: Для `ORDER_TRANSFERRED` — `actual_end = timestamp`

    # ---------- служебные ----------
    Health:
      type: object
      required: [status]
      properties:
        status:
          type: string
          enum: [ok]
        team:
          type: string
        version:
          type: string
    LoadResult:
      type: object
      required: [status]
      properties:
        status:
          type: string
          enum: [loaded]
        counts:
          type: object
          description: Сколько объектов принято
          additionalProperties:
            type: integer
            minimum: 0
    ErrorResponse:
      type: object
      required: [error]
      properties:
        error:
          type: object
          required: [code, message]
          properties:
            code:
              type: string
              description: Машинный код ошибки, например `BAD_REQUEST`, `REFERENCE_NOT_LOADED`, `EVENT_OUT_OF_ORDER`, `UNKNOWN_ORDER`
            message:
              type: string
            details:
              type: array
              items:
                type: object
                required: [message]
                properties:
                  field:
                    type: string
                  message:
                    type: string

  examples:
    ReferenceDataExample:
      summary: Справочники (сокращённо)
      value:
        service_centers:
          - service_center_id: SC-01
            name: СЦ «Север»
            address: г. Екатеринбург, ул. Победы, 30
            lat: 56.8941
            lon: 60.6105
            opening_hours:
              open: '08:00'
              close: '21:00'
            restock_duration_min: 15
        staff:
          - staff_id: ST-001
            display_name: Монтажник 001
            service_center_id: SC-01
            profiles: [LTP3]
            technologies: [PON, FTTB, XDSL]
            height_permit: false
            shift:
              start: '09:00'
              end: '18:00'
              break_duration_min: 60
              break_window:
                from: '12:00'
                to: '15:00'
            equipment_start:
              ONT: 3
              ROUTER: 4
              STB: 2
            equipment_capacity:
              ONT: 6
              ROUTER: 6
              STB: 4
            experience_years: 4.5
          - staff_id: ST-029
            display_name: Инсталлятор 029
            service_center_id: SC-01
            profiles: [INSTALL]
            technologies: [PON, FTTB]
            height_permit: true
            shift:
              start: '12:00'
              end: '21:00'
              break_duration_min: 60
              break_window:
                from: '15:00'
                to: '18:00'
            equipment_start:
              ONT: 4
              ROUTER: 5
              STB: 3
            equipment_capacity:
              ONT: 6
              ROUTER: 6
              STB: 4
        access_nodes:
          - access_node_id: AN-0031
            node_type: OLT
            technology: PON
            name: OLT Пионерский-2
            address: г. Екатеринбург, ул. Учителей, 22
            lat: 56.8698
            lon: 60.651
            buildings:
              - building_id: B-00412
                address: г. Екатеринбург, ул. Учителей, 18
                lat: 56.8712
                lon: 60.6531
                building_type: MKD
                overhead_line: false
                zone_service_center_id: SC-01
              - building_id: B-00415
                address: г. Екатеринбург, ул. Уральская, 61
                lat: 56.8655
                lon: 60.6493
                building_type: MKD
                overhead_line: false
                zone_service_center_id: SC-01
        work_types:
          - code: REPAIR
            name: Устранение неисправности (обращение абонента)
            profiles: [LTP3]
            duration_min: 60
            max_start_delay_min: null
            deadline_kind: HOURS
            deadline_value: 24
            repeat_deadline_value: 12
            pgp_relevant: true
          - code: INST_PON_MKD
            name: Инсталляция ШПД/IPTV, МКД, PON
            profiles: [INSTALL]
            duration_min: 90
            max_start_delay_min: 10
            deadline_kind: WORKING_DAYS
            deadline_value: 3
            repeat_deadline_value: null
            pgp_relevant: false

    TravelMatrixExample:
      summary: Матрица на три точки
      value:
        matrix_id: ekb-2026-10-v1
        points: [SC-01, B-00412, B-00415]
        durations_sec:
          - [0, 1080, 1200]
          - [1110, 0, 240]
          - [1230, 260, 0]
        distances_m:
          - [0, 4620, 5100]
          - [4700, 0, 780]
          - [5150, 820, 0]
        hourly_factors:
          - hour: 8
            factor: 1.35
          - hour: 13
            factor: 1.1

    DayInputExample:
      summary: Наряды дня (сокращённо)
      value:
        date: '2026-10-26'
        now: '2026-10-26T08:30:00+05:00'
        absent_staff_ids: [ST-017]
        orders:
          - order_id: ORD-20261026-0001
            work_type_code: REPAIR
            technology: PON
            building_id: B-00412
            address: г. Екатеринбург, ул. Учителей, 18
            lat: 56.8712
            lon: 60.6531
            building_type: MKD
            requires_height: false
            access_node_id: AN-0031
            registered_at: '2026-10-25T18:40:00+05:00'
            slot:
              start: '2026-10-26T09:00:00+05:00'
              end: '2026-10-26T11:00:00+05:00'
            latest_start: '2026-10-26T11:00:00+05:00'
            deadline: '2026-10-26T18:40:00+05:00'
            required_equipment:
              ONT: 1
              ROUTER: 0
              STB: 0
            is_repeat: false
            previous_order_id: null
          - order_id: ORD-20261026-0042
            work_type_code: INST_PON_MKD
            technology: PON
            building_id: B-00415
            address: г. Екатеринбург, ул. Уральская, 61
            lat: 56.8655
            lon: 60.6493
            building_type: MKD
            requires_height: false
            access_node_id: AN-0031
            registered_at: '2026-10-22T14:12:00+05:00'
            slot:
              start: '2026-10-26T13:00:00+05:00'
              end: '2026-10-26T15:00:00+05:00'
            latest_start: '2026-10-26T13:10:00+05:00'
            deadline: '2026-10-27T14:12:00+05:00'
            required_equipment:
              ONT: 1
              ROUTER: 1
              STB: 1
            is_repeat: false
            previous_order_id: null

    PlanExample:
      summary: План с одним маршрутом (сокращённо)
      value:
        date: '2026-10-26'
        plan_version: 1
        based_on_event_id: null
        as_of: '2026-10-26T08:30:00+05:00'
        computation_ms: 41250
        routes:
          - staff_id: ST-001
            service_center_id: SC-01
            status: ACTIVE
            unavailable_from: null
            unavailable_until: null
            stops:
              - seq: 1
                type: DEPOT_START
                location_id: SC-01
                order_id: null
                arrival: '2026-10-26T09:00:00+05:00'
                start: '2026-10-26T09:00:00+05:00'
                end: '2026-10-26T09:00:00+05:00'
                travel_sec: 0
                status: PLANNED
                equipment_after:
                  ONT: 3
                  ROUTER: 4
                  STB: 2
              - seq: 2
                type: VISIT
                location_id: B-00412
                order_id: ORD-20261026-0001
                arrival: '2026-10-26T09:18:00+05:00'
                start: '2026-10-26T09:18:00+05:00'
                end: '2026-10-26T10:18:00+05:00'
                travel_sec: 1080
                distance_m: 4620
                status: PLANNED
                equipment_after:
                  ONT: 2
                  ROUTER: 4
                  STB: 2
                explanation: Ближайший сотрудник 3ЛТП с PON; запас до конца слота 102 мин
              - seq: 3
                type: VISIT
                location_id: B-00415
                order_id: ORD-20261026-0007
                arrival: '2026-10-26T10:22:00+05:00'
                start: '2026-10-26T11:00:00+05:00'
                end: '2026-10-26T11:45:00+05:00'
                travel_sec: 240
                distance_m: 780
                status: PLANNED
                equipment_after:
                  ONT: 2
                  ROUTER: 3
                  STB: 2
              - seq: 4
                type: BREAK
                location_id: B-00415
                order_id: null
                arrival: '2026-10-26T11:45:00+05:00'
                start: '2026-10-26T12:00:00+05:00'
                end: '2026-10-26T13:00:00+05:00'
                travel_sec: 0
                status: PLANNED
              - seq: 5
                type: RESTOCK
                location_id: SC-01
                order_id: null
                arrival: '2026-10-26T13:20:30+05:00'
                start: '2026-10-26T13:20:30+05:00'
                end: '2026-10-26T13:35:30+05:00'
                travel_sec: 1230
                distance_m: 5150
                status: PLANNED
                equipment_after:
                  ONT: 6
                  ROUTER: 6
                  STB: 4
              - seq: 6
                type: VISIT
                location_id: B-00412
                order_id: ORD-20261026-0019
                arrival: '2026-10-26T13:53:30+05:00'
                start: '2026-10-26T13:53:30+05:00'
                end: '2026-10-26T14:53:30+05:00'
                travel_sec: 1080
                distance_m: 4620
                status: PLANNED
                equipment_after:
                  ONT: 5
                  ROUTER: 5
                  STB: 4
              - seq: 7
                type: DEPOT_END
                location_id: SC-01
                order_id: null
                arrival: '2026-10-26T15:12:00+05:00'
                start: '2026-10-26T15:12:00+05:00'
                end: '2026-10-26T15:12:00+05:00'
                travel_sec: 1110
                distance_m: 4700
                status: PLANNED
            totals:
              visits: 3
              travel_sec: 4740
              distance_m: 19870
              work_min: 165
              utilization: 0.508
        unassigned:
          - order_id: ORD-20261026-0233
            reason_code: NO_QUALIFIED_STAFF
            reason_text: На смене нет свободного сотрудника с допуском к работам на высоте и технологией PON
            suggested_action: RESCHEDULE
            proposed_slot:
              start: '2026-10-27T09:00:00+05:00'
              end: '2026-10-27T11:00:00+05:00'
            since: '2026-10-26T08:30:00+05:00'
        pgp_groups:
          - group_id: PGP-20261026-01
            access_node_id: AN-0057
            order_ids: [ORD-20261026-0390, ORD-20261026-0391, ORD-20261026-0393]
            detected_at: '2026-10-26T08:30:00+05:00'
            action: TRANSFER_TO_STATION
            comment: 3 обращения с OLT Втузгородок-1 за 35 минут
        cancelled: []
        alerts:
          - alert_id: ALR-20261026-0001
            created_at: '2026-10-26T08:30:00+05:00'
            type: PGP_DETECTED
            status: ACTIVE
            resolved_at: null
            order_id: null
            group_id: PGP-20261026-01
            staff_id: null
            forecast_start: null
            lateness_min: null
            suggested_action: TRANSFER_TO_STATION
            suggested_staff_id: null
            proposed_slot: null
            applied: true
            message: ПГП на AN-0057, 3 наряда переданы в станционное подразделение
        summary:
          orders_total: 384
          visits_planned: 351
          unassigned: 21
          pgp_orders: 12
          cancelled: 0
          travel_sec_total: 512340
          utilization_cv: 0.14

    NewOrderEventExample:
      summary: Новый повторный наряд на неисправность
      value:
        event_id: EV-20261026-0012
        type: NEW_ORDER
        timestamp: '2026-10-26T10:05:00+05:00'
        order:
          order_id: ORD-20261026-0417
          work_type_code: REPAIR
          technology: FTTB
          building_id: B-00877
          address: г. Екатеринбург, ул. Кировградская, 44
          lat: 56.9052
          lon: 60.5874
          building_type: MKD
          requires_height: false
          access_node_id: AN-0102
          registered_at: '2026-10-26T10:05:00+05:00'
          slot:
            start: '2026-10-26T13:00:00+05:00'
            end: '2026-10-26T15:00:00+05:00'
          latest_start: '2026-10-26T15:00:00+05:00'
          deadline: '2026-10-26T22:05:00+05:00'
          required_equipment:
            ONT: 0
            ROUTER: 1
            STB: 0
          is_repeat: true
          previous_order_id: ORD-20261021-0233
    OrderCancelledEventExample:
      summary: Абонент не подтвердил визит
      value:
        event_id: EV-20261026-0021
        type: ORDER_CANCELLED
        timestamp: '2026-10-26T12:15:00+05:00'
        order_id: ORD-20261026-0145
        reason: NOT_CONFIRMED
    VisitDelayedEventExample:
      summary: Задержка на объекте
      value:
        event_id: EV-20261026-0004
        type: VISIT_DELAYED
        timestamp: '2026-10-26T09:50:00+05:00'
        staff_id: ST-001
        order_id: ORD-20261026-0001
        expected_end: '2026-10-26T10:45:00+05:00'
    OrderTransferredEventExample:
      summary: Повреждение на станционном оборудовании
      value:
        event_id: EV-20261026-0027
        type: ORDER_TRANSFERRED
        timestamp: '2026-10-26T13:25:00+05:00'
        staff_id: ST-007
        order_id: ORD-20261026-0123
        actual_end: '2026-10-26T13:25:00+05:00'
        transferred_to: STATION_EQUIPMENT
    StaffUnavailableEventExample:
      summary: Поломка автомобиля
      value:
        event_id: EV-20261026-0016
        type: STAFF_UNAVAILABLE
        timestamp: '2026-10-26T11:10:00+05:00'
        staff_id: ST-014
        until: null
        reason: VEHICLE_BREAKDOWN
    TimeTickEventExample:
      summary: Ход модельного времени
      value:
        event_id: EV-20261026-0017
        type: TIME_TICK
        timestamp: '2026-10-26T11:15:00+05:00'
    EventResultExample:
      summary: Ответ на STAFF_UNAVAILABLE
      value:
        event_id: EV-20261026-0016
        accepted: true
        duplicate: false
        plan_version: 17
        as_of: '2026-10-26T11:10:00+05:00'
        processing_ms: 2140
        changed_staff_ids: [ST-014, ST-003, ST-011]
        new_alerts:
          - alert_id: ALR-20261026-0007
            created_at: '2026-10-26T11:10:00+05:00'
            type: LATE_RISK
            status: RESOLVED
            resolved_at: '2026-10-26T11:10:00+05:00'
            order_id: ORD-20261026-0188
            group_id: null
            staff_id: ST-014
            forecast_start: null
            lateness_min: null
            suggested_action: REASSIGN
            suggested_staff_id: ST-003
            proposed_slot: null
            applied: true
            message: ST-014 выбыл, визит по ORD-20261026-0188 передан ST-003
        message: Перераспределено 4 наряда ST-014
````

## File: docs/formalization.md
````markdown
# Формализация задачи диспетчеризации выездных бригад

Источник истины: `data/api_spec.yaml` (разделы 2–5 описания) и `ТЗ.docx`. Время — целые секунды от 00:00 местного дня (UTC+5), системные часы не используются. Справочные числа из ТЗ: 40 сотрудников (28 ЗЛТП, 12 инсталляторов), 5 СЦ, порядка 350–400 нарядов в день.

## 1. Множества и параметры

| Обозначение | Смысл |
|---|---|
| `K` | сотрудники на смене (справочник минус `absent_staff_ids`) |
| `D` | сервисные центры; `d(k)` — СЦ сотрудника; `[o_d, c_d]` — режим работы; `ρ_d` — `restock_duration_min` |
| `N` | наряды дня (загрузка + `NEW_ORDER`); `N = N_route ∪ N_un ∪ N_pgp ∪ N_canc`, попарно не пересекаются (H9) |
| `E = {ONT, ROUTER, STB}` | виды АО |
| `τ(a,b)` | время в пути по единой матрице, может быть несимметричным, `τ(a,a) = 0` |
| `P_k, T_k, h_k` | профили, технологии, допуск к высоте сотрудника |
| `[S_k, F_k]` | смена; `β_k` — длительность перерыва; `[bf_k, bt_k]` — окно начала перерыва |
| `q⁰_k[e], Q_k[e]` | `equipment_start`, `equipment_capacity` |
| `W_i, g_i, η_i` | профили типа работ, технология, `requires_height` наряда |
| `[a_i, b_i]` | `slot.start`, `latest_start`; `p_i` — норматив; `dl_i` — `deadline`; `r_i` — `registered_at` |
| `u_i[e]` | `required_equipment`; `w_i = 2` при `is_repeat`, иначе 1 |
| `n_i` | узел доступа; `π_i` — `pgp_relevant` типа работ |
| `t` | модельное время (`now` или `timestamp` последнего события) |

Переменные. Маршрут сотрудника `R_k = (s_1, …, s_m)`, остановка `s = (type, loc, order, arr, st, en)`. `y_i ∈ {0,1}` — наряд стоит визитом в маршруте; `x_ik` — у сотрудника `k`. `C(i) = {k ∈ K : H1}` — допустимые исполнители.

## 2. Жёсткие ограничения

- **H1 Допуск.** `x_ik = 1 ⇒ P_k ∩ W_i ≠ ∅ ∧ g_i ∈ T_k ∧ (η_i ⇒ h_k)`.
- **H2 Смена.** `st(DEPOT_START) ≥ S_k`; `arr(DEPOT_END) ≤ F_k`; для любой остановки `S_k ≤ arr ≤ st ≤ en ≤ F_k`.
- **H3 Перерыв.** Если в `R_k` есть визит: ровно одна `BREAK`, `en − st ≥ β_k`, `bf_k ≤ st ≤ bt_k`, `loc(BREAK) = loc(предыдущей)`, время в пути до неё 0, интервалы остановок не пересекаются.
- **H4 Слот.** `a_i ≤ st_i ≤ b_i`.
- **H5 Норматив.** `en_i − st_i = p_i`; исключение — `en_i` из `VISIT_DELAYED.expected_end` / `ORDER_TRANSFERRED.actual_end`.
- **H6 Дорога.** `arr(s_j) ≥ en(s_{j−1}) + τ(loc_{j−1}, loc_j)`; `st ≥ arr`; `en ≥ st`. Неравенство: приехать позже и ждать можно.
- **H7 АО.** Остаток `z_0 = q⁰_k`; после визита `z_j = z_{j−1} − u_i`, `z_j[e] ≥ 0` для всех `e`; после `RESTOCK` `z_j = Q_k`.
- **H8 СЦ.** `loc(DEPOT_START) = loc(DEPOT_END) = d(k)`; `RESTOCK` в любом `d ∈ D` при `o_d ≤ st`, `en ≤ c_d`, `en − st ≥ ρ_d`.
- **H9 Учёт.** Каждый `i ∈ N` ровно в одном из: визит, `unassigned`, `pgp_groups[].order_ids`, `cancelled`.
- **H10 Заморозка** между версиями `v−1 → v` в момент `t`:
  - остановка с `st < t` начата: сотрудник, наряд, `loc`, `arr`, `st` неизменны; `en` меняется только событиями `VISIT_DELAYED` / `ORDER_TRANSFERRED`;
  - остановка новая или с изменившимся предшественником: `arr ≥ max(en(предшественника), t) + τ`;
  - визит по наряду из `NEW_ORDER`: `st ≥ timestamp`;
  - отменённый наряд без начатого визита в маршрутах отсутствует;
  - у недоступного сотрудника нет остановок с `st ∈ [timestamp, until)`.

Структура: первая остановка `DEPOT_START` (`arr = st = en`), последняя `DEPOT_END` (у недоступного не обязательна), `seq` без пропусков, `loc(VISIT) = building_id`.

## 3. Целевая функция

Наряд засчитан: `ok_i = y_i ∧ (a_i ≤ st_i ≤ b_i) ∧ (en_i ≤ dl_i) ∧ H1–H10`. Генератор гарантирует `b_i + p_i ≤ dl_i`, поэтому для размещённого визита с нормативной длительностью срок выполняется автоматически; H4 делаем жёстким, а «не успеваем» выражаем через `y_i = 0`. Нарушение на размещённом наряде хуже отказа от него (не засчитан плюс штраф), поэтому план с нарушениями не публикуется никогда.

Минимизируем (лексикографический порядок ТЗ, свёрнутый весами):

```
F = W1·Σ_i w_i·(1 − ok_i) + W2·R + W3·T + W4·Σ_k (ρ_k − ρ̄)²
```

- `R` — переносы: наряды, стоявшие в маршруте версии `v−1` и снятые в `unassigned`; смена исполнителя считается за 0,1 переноса. В первоначальном плане `R = 0`, роль переносов играет первое слагаемое.
- `T = Σ τ` по всем маршрутам, секунды.
- `ρ_k = (работы + дорога) / (F_k − S_k − β_k)` — загрузка, как в `RouteTotals.utilization`; `ρ̄` — среднее по задействованным.

Предлагаемые веса (в секундах пути): `τ_max = max τ` по матрице; `W3 = 1`; `W2 = 3·τ_max` (порядка 10 000); `W1 = 5·W2` (порядка 50 000); `W4 = 3600`. Тогда один засчитанный наряд дороже любого переноса, перенос дороже любого объезда (вставка наряда удлиняет маршрут не более чем на `2·τ_max`), а равномерность никогда не стоит дороже примерно часа суммарной дороги. Веса вынесены в конфигурацию и калибруются на 10 обучающих днях.

При перепланировании добавляется штраф поздней перестановки `W5 = W2` за переназначение или снятие наряда с `a_i − t < 30 мин` (см. раздел 6).

## 4. Отображение на PyVRP

Архитектура: интерфейс `Planner` (реализации `PyVrpPlanner`, запасная `OrToolsPlanner`) выдаёт назначение и порядок визитов. Точное расписание всегда строит собственный **декодер маршрута**, корректность проверяет собственный **валидатор H1–H10**. Единица времени в PyVRP — секунда, расстояние в целевой функции — `τ`.

| Сущность задачи | PyVRP |
|---|---|
| Наряд | `Client`: `tw_early = a_i`, `tw_late = b_i`, `service_duration = p_i`, `delivery = [u_ONT, u_ROUTER, u_STB]`, `required = False`, `prize = W1·w_i` |
| Сотрудник | отдельный `VehicleType` с `num_available = 1`: `start_depot = end_depot = d(k)`, `tw_early = S_k`, `tw_late = F_k`, `capacity = Q_k` (3 измерения) |
| H1 | профили маршрутизации: сотрудники группируются в классы по `(P_k, T_k, h_k)`, на класс один `profile`; в матрицах класса дуги в недопустимые наряды получают запретное значение. Ожидаемо 6–12 классов |
| H4 | жёсткое окно клиента; ожидание до `a_i` допустимо нативно |
| H2, H8 (депо) | окно типа ТС и депо старта/финиша; мульти-депо нативно |
| H7 и `RESTOCK` | многорейсовость: `reload_depots = D`, `max_reloads = 2`; у депо `service_duration = ρ_d`, окно `[o_d, c_d − ρ_d]`. Ёмкость рейса равна `Q_k`, что совпадает с «довести до `Q_k`». Первый рейс ограничен `q⁰_k` через `initial_load = Q_k − q⁰_k` |
| Цель | `Σ τ` минус собранные призы; невыгодные и невместимые наряды остаются неназначенными |

Что не ложится нативно и как обходим:

1. **Перерыв (H3).** В PyVRP нет перерыва «на месте предыдущей остановки»: фиктивный клиент исказил бы дорогу. Решение: солверу даётся смена, укороченная на `β_k` (`tw_late = F_k − β_k`), после чего декодер перебирает позиции перерыва в маршруте (их не больше `m`) и выбирает допустимую с минимальным сдвигом. Если ни одна не подходит, срабатывает ремонт (п. 5).
2. **Точная семантика H7.** Декодер моделирует остаток по маршруту сам и вставляет `RESTOCK` там, где это дешевле всего (перебор позиции и СЦ, проверка H8), не полагаясь на рейсы PyVRP. `initial_load`, окна и время обслуживания депо зависят от версии PyVRP; если в закреплённой версии их нет, пополнение целиком уходит в декодер, а в PyVRP остаётся ёмкость `q⁰_k`.
3. **Равномерность.** В PyVRP не выражается. После основного решения выполняется локальный поиск по полной `F` (перенос и обмен нарядов между соседними по загрузке сотрудниками) на точном декодере.
4. **Переносы и стабильность.** При перепланировании приз уже стоящего наряда увеличивается на `W2`; смена исполнителя штрафуется на уровне нашего локального поиска.
5. **Ремонт.** Наряды, которые декодер не смог расставить, идут в жадную вставку с сожалением (regret-2) по всем допустимым сотрудникам и позициям, затем короткий LNS (разрушить 5–15 % нарядов и вставить заново) на точном декодере. Невставленные получают причину (раздел 7).
6. **Валидатор.** Независимая от планировщика реализация H1–H10 (включая сравнение с предыдущей версией для H10) запускается перед публикацией каждой версии. При нарушении наряд снимается в `unassigned`, маршрут пересчитывается; если не помогло, остаётся предыдущая версия с минимальной правкой.

Декодер выдаёт расписание «как можно раньше» с двумя сознательными отступлениями, допустимыми по H6 (там неравенство): `DEPOT_START` ставится точно к выезду на первую остановку, а `DEPOT_END.arrival = F_k`. Иначе `DEPOT_END` с `st < t` заморозится, и освободившийся сотрудник будет потерян для новых нарядов до конца дня. Сотрудник без визитов получает `stops = []`.

Бюджет первоначального плана (лимит 300 с, берём `min(time_limit_sec, 300) − 40`): PyVRP около 60 %, декодирование и LNS около 30 %, валидация и сериализация — остаток. Запасной `OrToolsPlanner` использует нативные перерывы, списки допустимых ТС и штрафы за пропуск узла; декодер и валидатор общие.

## 5. Перепланирование по событиям

**Состояние в момент `t`.** У каждого сотрудника маршрут делится на начатый префикс (`st < t`, неизменен) и свободный хвост. Якорь сотрудника — последняя начатая остановка: точка `ℓ_k`, момент освобождения `f_k = max(en, t)`. Остановка, к которой сотрудник уже едет (предшественник закончен, `st ≥ t`), считается закреплённой: она сохраняет предшественника и прежнее `arr`, а снимается только при отмене, выбытии сотрудника или недостижимости. Любая новая остановка или остановка с новым предшественником получает `arr ≥ max(en_pred, t) + τ`; остальные времена хвоста пересчитываются по H6 без этого правила.

**Алгоритм на событие (anytime, цель 1–3 с, внутренний дедлайн 7 с):**

1. Проверка события: неизвестный наряд или сотрудник, противоречие состоянию → `422`, состояние не меняется; повтор `event_id` → `duplicate = true`; `timestamp` меньше текущего `t` → `409`.
2. Детерминированная правка, специфичная для типа (миллисекунды).
3. Пересчёт времён хвостов декодером; выявление сломанных визитов.
4. Ремонт: вставка освободившихся и нераспределённых нарядов (regret-2), при необходимости выталкивание наряда с меньшим весом.
5. LNS по `F` в остаток бюджета. Тёплый старт — текущий план: PyVRP на событие не запускается. Исключение — массовый сбой (у выбывшего снято больше 8 нарядов): сокращённая задача на 3–4 с, где якоря заданы как стартовые депо с `tw_early = f_k`, префиксы исключены, начальное решение — текущие хвосты.
6. Валидатор H1–H10, обновление предупреждений, `plan_version += 1`.

| Событие | Действие |
|---|---|
| `NEW_ORDER` | Добавить наряд; проверка ПГП (раздел 6); иначе лучшая вставка в хвосты допустимых сотрудников с вариантами `RESTOCK`; `st ≥ timestamp` выполняется автоматически. Чужие назначения по возможности не трогаем. Не вставился → `unassigned` с причиной и предупреждением `UNASSIGNED` |
| `ORDER_CANCELLED` | Визит не начат: убрать из маршрута, пула или группы ПГП в `cancelled`; у следующей остановки сменился предшественник → правило `max(en_pred, t) + τ`; в освободившееся время вставить нераспределённые. Визит начат: остаётся в маршруте как выполненный, в `cancelled` не попадает (H9) |
| `VISIT_DELAYED` | Проверить, что визит у `staff_id` и `st ≤ timestamp`; `en := expected_end`; сдвинуть хвост. Визиты, вышедшие за `b_i`: перестановка в маршруте → передача другому → снятие; на каждый — `LATE_RISK` |
| `ORDER_TRANSFERRED` | `en := actual_end`, визит остаётся в маршруте, АО списано; хвост подтягивается раньше (не раньше `a_i`), в освободившееся время вставляются нераспределённые |
| `STAFF_UNAVAILABLE` | `status = UNAVAILABLE`, `unavailable_from/until`. Начатые остановки остаются; все остановки с `st ∈ [timestamp, until)` снимаются. `until = null`: хвост пуст, `DEPOT_END` не ставится. Иначе сотрудник продолжает из своего СЦ с `until`: якорь `(d(k), until)`, для следующей остановки берём максимум из оценок от СЦ и от последней остановки. Снятые наряды: `LATE_RISK`, затем ремонт; без замены → `unassigned` с `STAFF_UNAVAILABLE` |
| `TIME_TICK` | Сдвиг `t`, пересчёт статусов остановок и прогнозов, обновление предупреждений, попытка вставить нераспределённые. Косметические перестановки запрещены для нарядов с `a_i − t < 30 мин` |

## 6. Раннее предупреждение и ПГП

**LATE_RISK.** Оценка считает предупреждение своевременным, если `created_at ≤ slot.start − 30 мин`, и смотрит на наряды, которые после событий сменили исполнителя или были сняты из маршрута. Отсюда правила:

- После каждого события декодер даёт прогноз `ŝ_i` начала каждого незамороженного визита «без вмешательства» (исполнитель и порядок прежние). Если `ŝ_i > b_i`, создаётся `LATE_RISK` с `forecast_start = ŝ_i`, `lateness_min = ⌈(ŝ_i − b_i)/60⌉`, `staff_id` текущего исполнителя.
- Предложение: `REASSIGN` с `suggested_staff_id`, если есть допустимая вставка к другому сотруднику; иначе `RESCHEDULE` с `proposed_slot` (ближайший слот, укладывающийся в `dl_i`). Применили сами → `applied = true`, `status = RESOLVED`, `resolved_at = t`.
- **Любое** переназначение или снятие наряда сопровождается `LATE_RISK` в той же версии плана, в том числе при выбытии сотрудника и при оптимизационных перестановках.
- Чтобы предупреждения были своевременными, ремонт предпочитает трогать наряды с `a_i − t ≥ 30 мин` (штраф `W5`); из двух способов разрешить конфликт выбирается тот, что перемещает более дальний по времени наряд.
- Упреждающий режим: если запас `b_i − ŝ_i` меньше 10 мин, а `a_i − t` попадает в 30–90 мин, наряд заранее передаётся сотруднику с большим запасом, если такой есть. Порог калибруется на обучающих днях.
- Список `alerts` только пополняется; у закрытых меняются `status` и `resolved_at`.

**ПГП.** Кандидаты — наряды с `π_i = true`. Для узла `n` рассматриваем наряды, упорядоченные по `r_i`; инцидент — максимальная цепочка, в которой есть не менее 3 нарядов с `max r − min r ≤ 60 мин`.

- Первоначальный план: проверка по всем загруженным нарядам, `detected_at = now`.
- `NEW_ORDER`: если новый наряд доводит окно `[r − 60 мин, r]` на узле до 3, создаётся группа с `detected_at = timestamp` и предупреждение `PGP_DETECTED` (`TRANSFER_TO_STATION`, `applied = true`). В группу входят все наряды окна без начатого визита; они снимаются из маршрутов и из `unassigned`, освободившееся время идёт в ремонт.
- Пополнение: следующий наряд того же узла с `r` не позже 60 мин после последнего наряда группы добавляется в неё, в маршруты не попадает; `detected_at` и `group_id` не меняются.
- Наряд с начатым визитом (`st < t`) остаётся в маршруте и в группу не входит, но учитывается при подсчёте порога. Отменённые в группу не входят.
- `group_id = PGP-<дата>-<nn>`; при 2 нарядах с узла группа не создаётся (в данных случайные совпадения — до 2).

## 7. Причины unassigned

Причина определяется каскадом проверок для наряда `i`, первая сработавшая и есть ответ:

| Шаг | Условие | Код | `suggested_action` |
|---|---|---|---|
| 1 | Наряд снят у выбывшего сотрудника, вставка к остальным не найдена | `STAFF_UNAVAILABLE` | `RESCHEDULE` |
| 2 | `C(i) = ∅` среди сотрудников на смене | `NO_QUALIFIED_STAFF` | `REASSIGN` |
| 3 | Одиночный тест: ни один `k ∈ C(i)` даже с пустым хвостом не начинает визит в `[a_i, b_i]` (смена не пересекает окно, либо `f_k + τ(ℓ_k, i) > b_i`) | `SLOT_UNREACHABLE` | `RESCHEDULE` |
| 4 | Вставка без учёта H7 существует, но с учётом H7 нет ни напрямую, ни с `RESTOCK` | `NO_EQUIPMENT` | `RESCHEDULE` |
| 5 | Одиночный тест проходит, но любая вставка ломает H2–H4 у уже стоящих нарядов с не меньшим весом | `NO_SHIFT_TIME` | `RESCHEDULE` |
| 6 | Прочее: дома нет в матрице, исчерпан бюджет расчёта | `OTHER` | `ESCALATE` |

`proposed_slot` — те же часы следующего рабочего дня, если `начало + 10 мин + p_i ≤ dl_i`; иначе `ESCALATE`. `since` — модельное время, с которого наряд не распределён. Причина пересчитывается при каждом событии; `reason_text` формируется из фактов проверки (сколько допустимых сотрудников, чего не хватило).

## 8. Обоснование подхода и риски

**Почему так.** Задача — VRPTW с необязательными клиентами, разнородным парком, несколькими депо и пополнением. PyVRP (гибридный генетический поиск) на таких размерах даёт решения, близкие к лучшим известным, за десятки секунд и нативно покрывает окна, призы, мульти-депо, многомерную ёмкость и дозагрузку. Специфика кейса (перерыв на месте, заморозка, правило `max(en_pred, t)`, равномерность, предупреждения) не выражается ни в одном готовом солвере полностью, поэтому вынесена в собственный слой «декодер + локальный поиск + валидатор», общий для обеих реализаций `Planner`. События обрабатываются инкрементально: 10 с недостаточно для надёжного полного пересчёта, а локальная правка минимизирует переносы и смену исполнителей, что прямо входит в цель.

**Риски и меры.**

| Риск | Мера |
|---|---|
| Возможности PyVRP (`reload_depots`, `initial_load`, окна и время обслуживания депо, начальное решение) зависят от версии | Закрепить версию, проверить прототипом в первый день; запасной путь — `RESTOCK` целиком в декодере |
| Перерыв вне солвера ухудшает качество в плотные часы | Укороченная смена в PyVRP, LNS на точном декодере; сравнение с OR-Tools на обучающих днях |
| Расхождение нашего валидатора с валидатором организаторов (допуск 1 с, трактовки) | Целые секунды, консервативные трактовки, прогон по примерам из спецификации |
| Превышение 10 с на событие | Anytime-алгоритм, внутренний дедлайн 7 с, расчёт в отдельном процессе, матрица в памяти (numpy) |
| Много классов допуска раздувают матрицы профилей | Матрицы только по точкам дня (до 500), группировка сотрудников в классы |
| Ложные или запоздалые `LATE_RISK` | Пороги калибруются на `events_example.json`; запрет поздних косметических перестановок |
````

## File: docs/mobile-api.md
````markdown
# API для мобильного приложения сотрудника

Расширение решения (не часть контракта `/api/v1`): префикс `/ui/employee`. Интерактивная схема — `http://<host>:8080/docs` (раздел `ui-employee`).
Контракт организаторов (`/api/v1`) не менялся; отметки сотрудника превращаются в его события внутри сервера.

- Базовый URL: `http://<host>:8080` (локально `http://localhost:8080`).
- Формат: JSON, UTF-8. Ошибки: `{"error": {"code": "...", "message": "...", "details": [...]}}`.
- CORS: разрешены origin из env `CORS_ORIGINS` (через запятую); по умолчанию dev-адреса фронта (`:5173`). `*` — только если задан явно.
- Дата дня `date=YYYY-MM-DD` необязательна: по умолчанию день демо, иначе последний день с построенным планом. Работает и для плана, загруженного через `PUT /api/v1/days/{date}` + `POST .../plan`.

## Модельное время

Система живёт в модельном времени плана (`as_of`), а не по часам телефона. Время отметки передавайте в `timestamp` (ISO 8601 с поясом, например `2026-10-08T10:12:00+05:00`); без него берётся текущее модельное время дня. Время событий не может быть раньше последнего принятого события (иначе 422 `EVENT_OUT_OF_ORDER`).

## 1. Список сотрудников

```
curl "http://localhost:8080/ui/employee/staff"
```
```json
{"date":"2026-10-08","plan_version":1,"as_of":"2026-10-08T08:30:00+05:00",
 "staff":[{"staff_id":"ST-002","display_name":"Иванов И. И.","profiles":["INSTALL"],
   "service_center_id":"SC-1","service_center_name":"СЦ Центр","shift":{"start":"09:00","end":"18:00"},
   "route_status":"ACTIVE","visits":8,"visits_done":0}]}
```

## 2. Маршрут сотрудника

```
curl "http://localhost:8080/ui/employee/ST-002/route"
```
Основные поля ответа:

- `plan_version`, `as_of`, `on_shift`, `route_status` (`ACTIVE|UNAVAILABLE`), `progress {visits_total, visits_done}`.
- `allowed_actions` (уровень маршрута): `["UNAVAILABLE"]`, пока сотрудник на смене и ещё не заявлял недоступность.
- `next` — ближайший невыполненный визит: `order_id`, `address`, `lat`, `lon`, `customer_phone` (в данных организаторов телефона нет, всегда `null`), `slot`, плановые `arrival/start/end`, `call_window {start,end}` (звонить за 60–30 минут до слота), `next_mark`, `allowed_actions`.
- `stops[]` — все остановки: `type` (`VISIT|BREAK|RESTOCK|DEPOT_START|DEPOT_END`), `location {address,lat,lon}`, `order`, `arrival/start/end`, `state` (`PLANNED|IN_PROGRESS|DONE`; отметки сотрудника важнее плана), `marks {CALLED,ARRIVED,DONE,TRANSFER}` (время или `null`), `allowed_actions` визита.
- `marks[]` — журнал отметок с `event` (какое событие было отправлено).

Перечитывайте маршрут после каждой отметки, где `route_changed=true`, и периодически (раз в 5–10 с): диспетчер и события других сотрудников тоже меняют план. План целиком — напрямую `GET /api/v1/days/{date}/plan` (контракт организаторов); отдельные вызовы `/api/v1` для действий сотрудника не нужны.

## 3. Отметка

`POST /ui/employee/{staff_id}/marks[?date=YYYY-MM-DD]`

| `kind` | Нужно в теле | Событие контракта |
|---|---|---|
| `CALLED` | `order_id` | нет, только запись |
| `ARRIVED` | `order_id`, `timestamp` | `VISIT_DELAYED`, если `timestamp` позже планового начала визита на 5 минут и больше (`expected_end` = плановый конец + опоздание); иначе только отметка |
| `DONE` (синоним `COMPLETED`) | `order_id`, `timestamp` | `VISIT_DELAYED` с `expected_end = timestamp`, если позже планового конца; раньше или вовремя: только отметка |
| `TRANSFER` | `order_id`, `reason` = `STATION_EQUIPMENT` \| `TRUNK_CABLE`, `timestamp` | `ORDER_TRANSFERRED` (`actual_end = timestamp`, `transferred_to = reason`) |
| `UNAVAILABLE` | `reason` = `VEHICLE_BREAKDOWN` \| `ILLNESS` \| `OTHER`, `timestamp`, `until` (необязательно) | `STAFF_UNAVAILABLE` |

Ограничения контракта:

- `VISIT_DELAYED` и `ORDER_TRANSFERRED` допустимы только для уже начатого визита (`timestamp` позже планового начала) этого сотрудника. Досрочное завершение событием не выражается: освобождение времени есть только у `ORDER_TRANSFERRED`, поэтому ранний `DONE` лишь записывается.
- Порядок отметок: `CALLED` → `ARRIVED` → `DONE`; `TRANSFER` возможен после `ARRIVED` вместо `DONE`.
- Событие отправляется тем же путём, что `POST /api/v1/days/{date}/events`, `event_id` детерминирован: `EV-MOB-<yyyymmdd>-<order_id>-<kind>` (для недоступности `EV-MOB-<yyyymmdd>-UNAV-<staff_id>`).

Пример: прибытие с опозданием.
```
curl -X POST "http://localhost:8080/ui/employee/ST-002/marks" -H "Content-Type: application/json" \
  -d '{"order_id":"ORD-20261008-0148","kind":"ARRIVED","timestamp":"2026-10-08T09:30:49+05:00"}'
```
```json
{"accepted":true,"duplicate":false,"kind":"ARRIVED","order_id":"ORD-20261008-0148","staff_id":"ST-002",
 "at":"2026-10-08T09:30:49+05:00","recorded_at":"2026-11-14T21:39:42+00:00",
 "event_sent":true,
 "event":{"event_id":"EV-MOB-20261008-ORD-20261008-0148-ARRIVED","type":"VISIT_DELAYED","accepted":true,"duplicate":false,"message":"ORD-20261008-0148 у ST-002 задерживается до 10:30"},
 "event_note":null,"plan_version":2,"route_changed":true,"added_order_ids":[],"removed_order_ids":[],
 "changed_staff_ids":["ST-002"],
 "marks":{"CALLED":"2026-10-08T08:30:00+05:00","ARRIVED":"2026-10-08T09:30:49+05:00","DONE":null,"TRANSFER":null},
 "next_mark":"DONE","allowed_actions":["DONE","TRANSFER"]}
```

Пример: передача наряда.
```
curl -X POST "http://localhost:8080/ui/employee/ST-002/marks" -H "Content-Type: application/json" \
  -d '{"order_id":"ORD-20261008-0148","kind":"TRANSFER","reason":"TRUNK_CABLE","timestamp":"2026-10-08T09:38:49+05:00"}'
```
Ответ той же формы: `event.type = ORDER_TRANSFERRED`, `plan_version` +1, `allowed_actions = []`.

Пример: недоступен до 14:00.
```
curl -X POST "http://localhost:8080/ui/employee/ST-002/marks" -H "Content-Type: application/json" \
  -d '{"kind":"UNAVAILABLE","reason":"ILLNESS","timestamp":"2026-10-08T12:00:00+05:00","until":"2026-10-08T14:00:00+05:00"}'
```
`removed_order_ids` покажет наряды, снятые с маршрута сотрудника; после `until` он продолжает смену.

Поля ответа: `accepted` (отметка принята), `duplicate`, `event_sent` и `event` (что отправлено; `event_note` объясняет, почему события нет), `plan_version` (новая версия), `route_changed` (маршрут сотрудника изменился: перечитайте `/route`), `added_order_ids` / `removed_order_ids`, `changed_staff_ids` (чьи маршруты пересчитаны, включая других сотрудников), `marks`, `allowed_actions`.

## Идемпотентность

Повтор отметки того же вида на тот же наряд (для `UNAVAILABLE`: на того же сотрудника) возвращает 200 с `duplicate: true`, `event_sent: false`, прежним `event` и не создаёт события и версии плана. Безопасно повторять запрос при обрыве связи.

## Коды ошибок

Если событие отклонено планом, возвращается 422 с кодом события и отметка не записывается.

| HTTP | `code` | Когда |
|---|---|---|
| 400 | `BAD_REQUEST` | неверный JSON/схема, дата не `YYYY-MM-DD` |
| 404 | `STAFF_NOT_FOUND`, `DAY_NOT_FOUND` | неизвестный сотрудник или день |
| 409 | `REFERENCE_NOT_LOADED`, `PLAN_NOT_BUILT` | справочники или план ещё не загружены |
| 422 | `ORDER_ID_REQUIRED`, `REASON_REQUIRED` | не хватает поля |
| 422 | `ORDER_NOT_IN_ROUTE` | наряда нет в маршруте сотрудника |
| 422 | `MARK_OUT_OF_ORDER` | нарушен порядок отметок, время раньше предыдущей отметки |
| 422 | `VISIT_NOT_STARTED` | `TRANSFER` до планового начала визита |
| 422 | `EVENT_OUT_OF_ORDER`, `EVENT_CONFLICTS_PLAN`, `INVALID_VISIT_END`, `INVALID_PERIOD`, `STAFF_NOT_ON_SHIFT` | событие контракта отклонено (время раньше последнего события, следующая остановка уже начата, `until` не позже `timestamp`, сотрудник не на смене и т. д.) |

## Подключение по Radmin VPN

1. Узнать свой Radmin-IP (адаптер «Radmin VPN»): `ipconfig` или `Get-NetIPAddress -InterfaceAlias "Radmin VPN" -AddressFamily IPv4` (обычно `26.x.x.x`).
2. Запустить backend на машине-сервере: `tools\run-backend-lan.bat` (без Docker) или `docker compose up --build` (порты уже опубликованы на все интерфейсы).
3. Базовый URL для напарника: `http://<radmin-ip>:8080`. Проверка: `curl http://<radmin-ip>:8080/api/v1/health`.
4. CORS нужен только веб-клиенту в браузере: задайте `CORS_ORIGINS` (через запятую, например `http://<radmin-ip>:3000,http://<radmin-ip>:5173`); `*` допустим только для локальной демонстрации. Нативное мобильное приложение CORS не требует.
5. Брандмауэр Windows на машине-сервере должен пропускать входящий TCP 8080 (и 3000 для веба). Команда для PowerShell от администратора (выполнять вручную, только текст):
   `New-NetFirewallRule -DisplayName "Rostelecom API 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Any`
````

## File: docs/planner-notes.md
````markdown
# Заметки планировщика (этап A: первоначальный план; этап B: события)

## Проверка PyVRP 0.14.1 (прототип на установленной версии)

1. `reload_depots` + `max_reloads` работают: маршрут делится на рейсы, ёмкость рейса = `capacity` (это и есть «довести до `Q_k`»).
2. `initial_load` работает и действует только на первый рейс: при `capacity=3`, `initial_load=2` первый рейс берёт 1 единицу, после пополнения ограничение снимается.
3. Окно и `service_duration` задаются у `Depot`; с 0.14 точка (`Location`) отделена от депо, поэтому у одного СЦ два объекта `Depot`: старт/финиш (service 0) и пополнение (service = `restock_duration`, окно `[open, close − restock]`). Окно пополнения соблюдается.
4. Необязательные клиенты с призом (`required=False`, `prize`), 40 типов ТС с `num_available=1`, ёмкость по 3 измерениям, профили матриц — работают; тёплый старт — `solve(initial_solution=Solution(...))`.
5. Отличия API от описания по памяти: `Client(location=...)` ссылается на `Location`, координат у клиента нет; в `Route.schedule()` у клиента `idx` — номер клиента (без сдвига на депо); у `Result` есть `num_iterations`, `runtime`.
6. Перерыв нативно не выражается. Сравнение режимов на 2026-10-05 (20 с): `shift_duration = F−S−β` — 200 визитов, `tw_late = F−β` — 201, без учёта — 198, перерыв как обязательный клиент своего сотрудника (`client`, 40 профилей) — 194; на 3 днях по 60 с `client` хуже на 5–6 визитов (итераций PyVRP в 3 раза меньше). По умолчанию `shift_duration` (`PLANNER_BREAK_MODE`).
7. 13 из 14 маршрутов PyVRP, которые декодер не смог расписать, ломаются именно на перерыве (окно начала 2 ч), а не на АО. Поэтому после PyVRP идут перестановка внутри маршрута, жадная вставка, выталкивание длины 2 и LNS на точном декодере.
8. RESTOCK ставит декодер (перед визитом, где остаток ушёл бы в минус, в СЦ с минимальным крюком, открытом на всём интервале); рейсы PyVRP служат только для оценки вместимости.
9. Постобработка быстрая (выталкивание ≈0,2 с), поэтому PyVRP получает 70 % бюджета, остальное уходит на LNS.

## Правки к formalization.md (сам документ не менялся)

- §4, таблица: у каждого наряда своя `Location`; H1 через профили по *множеству допустимых нарядов* (у одинаковых множеств один профиль), а не по классам `(P,T,h)`.
- §4, п. 1: вместо `tw_late = F−β` используется `shift_duration = F−S−β`, точный перерыв ставит декодер; «перерыв-клиент» проверен и отклонён (п. 6 выше).
- §4, пополнение: отдельные объекты `Depot` для пополнения в тех же точках СЦ.
- §4, бюджет: `min(PLANNER_DAY_BUDGET_SEC=60, 0,8·time_limit_sec − 3)`, PyVRP 70 % (`PLANNER_PYVRP_SHARE`), запас `min(1,5 с; 10 %)` на сборку и самопроверку. Вариант `min(time_limit,300) − 40` не нужен.
- §4, п. 5: ремонт — перестановка внутри маршрута, затем снятие наряда с минимальным весом; потом жадная вставка (сначала повторные, затем с меньшим числом исполнителей), выталкивание длины 2, LNS; принимается только рост `W1·Σw − T`. Выравнивание загрузки — переносом по `T + W4·Σ(ρ−ρ̄)²`, если остался бюджет.
- §7: в каскад добавлен `OTHER`, если бюджет кончился раньше вставки (или наряд встаёт к свободному сотруднику).
- §6, ПГП: в обучающих данных все эталонные инциденты зарегистрированы после 08:30, поэтому в первоначальных планах групп нет. По всем нарядам дня детектор совпадает с эталоном: 15 из 15 нарядов, ложных 0.
- Данные: спрос на INSTALL (инсталляции, ADD_SERVICE, часть обследований) примерно вдвое больше возможностей 12 инсталляторов. Большая часть unassigned — `NO_SHIFT_TIME` у них, а часть ЗЛТП недогружена; это свойство данных, а не солвера.

## Этап B: перепланирование по событиям (`planner/events.py`)

Схема. Маршрут, которого событие не коснулось, переходит в новую версию без изменений (оркестратор считает, что план исполняется в точности, поэтому прогноз по нему не меняется). У затронутых сотрудников маршрут делится на замороженный префикс и хвост; хвост расписывает декодер хвоста, затем ремонт (перестановка внутри маршрута → снятие наряда), вставка снятых/нового/нераспределённых, предупреждения, самопроверка `validate_plan` + `validate_transition`. PyVRP на событие не запускается: жадной вставки хватает по времени (замеры — в отчёте бенча `tools/bench/run_events.py`).

### Допущения

1. **Префикс.** Заморожены остановки со `start < t`; по `VISIT_DELAYED` / `ORDER_TRANSFERRED` — ещё и визит из события, если его `start ≤ t` (спецификация: событие приходит, когда визит уже начат). Все остановки хвоста начинаются не раньше `t`.
2. **Первая остановка хвоста.** Если это та же остановка, что в прежней версии (тот же тип/наряд/СЦ и тот же предшественник), сотрудник выезжает в `end` последней замороженной остановки (в том числе в новый `end` из события — так хвост «подтягивается» после `ORDER_TRANSFERRED`). Любая другая первая остановка — не раньше `max(end, t) + дорога`. Остальные остановки хвоста идут за предшественником из хвоста, у которого `end ≥ t`, поэтому правило раздела 3 выполняется автоматически.
3. **VISIT_DELAYED / ORDER_TRANSFERRED → 422**, если визит не начат у этого сотрудника (`start > timestamp` или визит у другого), если новый конец раньше начала визита, или если следующая остановка уже начата раньше нового конца. Новый конец хранится в `DayState.visit_end_overrides` (state.py) — по нему H5 проверяет длительность визита из события. `expected_end` раньше планового конца принимается как есть.
4. **ORDER_CANCELLED по начатому визиту** принимается (200, версия +1), визит остаётся в маршруте как выполненный, в `cancelled` наряд не попадает (H9). Отмена наряда из ПГП-группы убирает его из группы; если в группе осталось меньше 2 нарядов (`minItems: 2`), группа распускается, её `PGP_DETECTED` закрывается, остаток возвращается в распределение.
5. **STAFF_UNAVAILABLE.** `unavailable_from = timestamp`. `until = null` или `until ≥ конец смены` — хвост пуст, `DEPOT_END` не ставится. Иначе после `until` сотрудник продолжает смену из своего СЦ: первая остановка хвоста — `arrival ≥ max(max(end, t) + дорога от последней остановки, until + дорога от СЦ)`, все остановки хвоста начинаются не раньше `until`; наряды, которые не помещаются, снимаются и передаются другим. Если остановка после `until` — перерыв, он ставится на месте предыдущей остановки маршрута (правило `BREAK` спецификации), хотя физически сотрудник в СЦ. Повторное событие по тому же сотруднику заменяет период. Сотрудник не на смене (`absent_staff_ids`) — 422 `STAFF_NOT_ON_SHIFT`. `DEPOT_END` у выбывшего с `until` ставится, только если после `until` есть визиты или нужен перерыв.
6. **H3 у выбывшего** (и в планировщике, и в validator.py): перерыв не обязателен, если остаток окна перерыва после выбытия целиком попал в недоступность, то есть `until = null` или `until > break_window.to`. Формулировка «окно целиком в недоступности» расширена на случай, когда окно началось до выбытия, а перерыв ещё не взят: поставить его всё равно нельзя (остановка начиналась бы в периоде недоступности). Если `until ≤ break_window.to`, перерыв обязателен и начинается в `[max(from, until), to]`.
7. **Запас перед концом окна перерыва.** Перерыв ставится сразу после визита; если этот визит затянется, перерыв выйдет за `break_window.to`. Поэтому декодеры (decoder.py и декодер хвоста) сначала ищут самую позднюю позицию перерыва с началом не позже `to − 60 мин` (`PLANNER_BREAK_SLACK_MIN`), и только если её нет — самую позднюю допустимую. На этапе A это не меняет результат (2026-10-05 и 2026-10-08, бюджет 30 с: визиты 208/192 при запасе 0 и 60).
8. **Нарушения, вынужденные событием.** Если задержка начатого визита уводит его конец за `break_window.to`, а перерыв ещё не взят, или за конец смены (с учётом дороги в СЦ), допустимого хвоста нет даже пустого. Тогда перерыв ставится сразу после визита (вне окна), `DEPOT_END` — по прибытии, в `EventResult.message` пишется «неустранимо: ST-…». Бенч считает такие нарушения отдельно («вынуждено событием»).
9. **LATE_RISK.** Прогноз «без вмешательства» считается по затронутым маршрутам (прежний порядок и прежняя позиция перерыва, без верхних границ окон и без пополнений), на `TIME_TICK` — по всем маршрутам. Предупреждение создаётся на каждый наряд, который в новой версии сменил исполнителя (`REASSIGN`, `suggested_staff_id`, `applied = true`, `RESOLVED`), снят в `unassigned` (`RESCHEDULE` с `proposed_slot`, `applied = true` — план уже изменён, но `status = ACTIVE`: перенос согласует диспетчер; без допустимого слота — `NONE`), снят в ПГП-группу (`TRANSFER_TO_STATION`), или имел прогноз опоздания и остался у того же сотрудника после перестановки в маршруте (`NONE`, `applied = true`). Прогноз опоздания, устранённый снятием соседнего наряда, отдельного предупреждения не получает. При новом решении по наряду его прежние `ACTIVE`-предупреждения закрываются (`RESOLVED`, `resolved_at = t`). `forecast_start`/`lateness_min` заполняются, только если прогноз опоздания был.
10. **Что снимать при ремонте.** Из нарядов, снятие одного из которых делает хвост допустимым, снимается наряд с меньшим весом, затем с запасом `a − t ≥ 30 мин` (предупреждение успеет быть своевременным), затем более поздний по слоту. Снятые вставляются к другим сотрудникам по минимальному приросту пути, иначе — цепочка выталкивания длины 2: выталкивается наряд из прежнего плана с запасом ≥ 30 мин до слота и весом не больше, если его можно передать третьему сотруднику (или его вес меньше).
11. **Без косметических перестановок.** Наряды между сотрудниками переносятся только при ремонте (снятые) и вставке; межмаршрутный LNS и «упреждающий режим» formalization §6 (передача при запасе < 10 мин) на событиях не делаются: при точном исполнении плана прогноз по незатронутым маршрутам не меняется.
12. **Нераспределённые** пробуют встать только в маршруты, затронутые событием (освободилось время); в остальные они не встали и раньше, а со временем ограничения только жёстче. Причина для новых нераспределённых — упрощённый каскад §7: `STAFF_UNAVAILABLE` (снят у выбывшего), `NO_QUALIFIED_STAFF` (нет доступного допустимого), `SLOT_UNREACHABLE` (никто не успевает к `latest_start` от своей точки освобождения), `NO_SHIFT_TIME`, `OTHER` (исчерпан бюджет 7 с). `NO_EQUIPMENT` на событиях не выделяется. Причины у прежних нераспределённых не пересчитываются.
13. **ПГП в течение дня.** На `NEW_ORDER` с `pgp_relevant`: если на узле есть группа и `|r − r(последнего наряда группы)| ≤ 60 мин`, наряд добавляется в неё (`group_id`, `detected_at` не меняются, обновляется только `comment`). Иначе считается окно `[r − 60 мин, r]` по всем неотменённым нарядам узла (в маршруте, начатым, нераспределённым); при ≥ 3 создаётся группа из нарядов окна без начатого визита, `detected_at = timestamp`, `PGP_DETECTED`. Группа из 1 наряда невозможна (`minItems: 2`), поэтому если без начатых остаётся меньше 2 — группа не создаётся. «Последний наряд группы» считается по её `order_ids` (начатые визиты, учтённые в пороге, в группу не входят). Риск правила пополнения formalization: фоновый наряд узла в пределах 60 мин после инцидента попадёт в группу, хотя эталон «≥ 3 за 60 мин» его бы не включил; на events_dense ложных нарядов в группах не было.
14. **Учёт и версия.** `plan_version`, `based_on_event_id`, `as_of`, `computation_ms` по-прежнему ставит service.py; `EventResult.message` — краткий итог события от планировщика (`PlanUpdate.message`). `alert_id` продолжает нумерацию дня, `group_id` — максимум из групп и предупреждений + 1.

## Пробки по часам (дополнительное задание, `TRAFFIC_FACTORS`)

Переключатель `TRAFFIC_FACTORS=on|off`, по умолчанию `off`. Читается при создании `DayContext` (`planner/context.py`, `traffic_enabled()`), явно передаётся флагом `PyVrpPlanner(traffic=...)` / `apply_event(..., traffic=...)` / `DayContext(traffic=...)` — так сценарий `/ui/scenario/traffic` строит оба варианта, не трогая окружение. Источник — `matrix.hourly_factors`; если их нет или все ≤ 1, режим — no-op.

* Время в пути: `travel(i→j, t) = ceil(τ_ij · max(1, f[час t]))`, час — по местному времени отправления (`DayContext.tt`). Время не меньше базового, поэтому H6 по `durations_sec` (как проверяет организаторский валидатор) выполняется всегда; `validator.py` намеренно остаётся на базовой матрице.
* Коэффициенты применяет **слой расписания**: декодер (`decoder._forward`, сдвиг выезда `shift_departure`), всё, что через него идёт (починка, жадная вставка, выталкивание, LNS, выравнивание, причины unassigned), декодер хвоста событий (`events._pass`), прогноз LATE_RISK (`forecast`) и проверка достижимости в `classify`. `travel_sec`/`totals` в плане — с коэффициентами.
* PyVRP решает по базовой матрице: он только предлагает назначение и порядок, точное время считает декодер, а недопустимое при пробках снимает починка и добирает вставка/LNS. Грубые отсечки вставки (`insertion_candidates`, `EventPlanner.insertion`) идут по базовой τ — это нижняя граница, отсечка остаётся корректной.
* Ограничения: коэффициент часа выезда применяется ко всему переезду (без деления по часам на длинных переездах); функция не FIFO — выезд в 08:59 (×1,35) может приехать позже выезда в 09:00 (×1,2), ожидание ради более быстрого переезда не ищется; PyVRP не видит пробок, поэтому часть его маршрутов приходится чинить, и на том же бюджете LNS делает меньше итераций; при выключенном режиме код идёт по прежней ветке (`ctx.traffic = False`), планы те же.

Замеры (бюджет 20 с, `hourly_factors` синтетической матрицы: 07–09 ч ×1,2–1,35, 12–13 ч ×1,1, 16–18 ч ×1,15–1,3; оба плана исполнены с коэффициентами, `traffic_replay` в scenarios.py):

| День | Размещено без → с | Доля в слот по плану | В пути с пробками, ч | Поздних прибытий | Нарушений |
|---|---|---|---|---|---|
| 2026-10-08 | 194 → 190 | 0,688 → 0,674 | 56,2 → 53,3 | 10 (44 мин) → 0 | 0 / 0 |
| 2026-10-05 | 208 → 206 | 0,751 → 0,744 | 59,4 → 62,0 | 6 (14 мин) → 0 | 0 / 0 |

План без коэффициентов в пробках опоздал бы на 6–10 визитах; план с ними честно размещает на 2–4 наряда меньше. `run_events.py` на events_dense при `TRAFFIC_FACTORS=on`: 113/113 событий, 0 нарушений H1–H10, макс. задержка 156 мс.

## Совместные наряды (дополнительное задание, `JOINT_ORDERS`)

ТЗ: инсталляция в частном секторе с ВЛС (`INST_PRIVATE_VLS`, норматив 180 мин, окно начала 10 мин, нужен допуск к высоте) выполняется двумя сотрудниками одновременно. Переключатель `JOINT_ORDERS=on|off`, **по умолчанию `off`**; при `off` код идёт по прежней ветке (`ctx.joint = False`), планы прежние. Код — `planner/joint.py`, флаг явно передаётся `PyVrpPlanner(joint=...)` / `apply_event(..., joint=...)` / `DayContext(joint=...)`.

### Как представить напарника в контракте

Контракт не знает совместных работ: у `Stop` один `order_id`, H9 требует, чтобы каждый наряд дня был ровно в одном месте. Рассмотрены варианты:

1. **Напарник — отдельный VISIT по `ORD-…-B` (выбран).** Тот же `location_id`, `start`, `end`, что у основного визита; `ORD-…-B` однозначно указывает на основной наряд. Основной визит сам по себе полностью проходит H1–H10, напарник только занимает время другого сотрудника. Минус: такого наряда нет во входных данных.
2. **Парные наряды во входных данных** (`ORD-x` и `ORD-x-B` загружены в день). Для валидатора безупречно, но данные дают организаторы, пар в них нет (в `orders_train.json` их нет) — вариант не реализуем без изменения входа.
3. **«Окно» в маршруте напарника без остановки.** H6 — неравенство, поэтому пропуск времени не нарушает ничего, и это самый безопасный для валидатора вариант. Но напарник не виден ни в плане, ни оркестратору, ни в оценке загрузки (его время выглядит простоем), а при событиях окно неотличимо от свободного времени. Отклонён: совместная работа должна быть видна в плане.

**Риск для валидатора организаторов.** Наш `validator.py` до этой задачи считал VISIT по неизвестному `order_id` нарушением H9 («визит по неизвестному наряду»); у организаторов, скорее всего, так же — плюс для такого визита нельзя проверить H1/H4/H5. Возможные последствия: штраф за «наряд с нарушением» (сам `ORD-…-B` не засчитывается, и это не страшно), в худшем случае — отказ в оценке версии. Поэтому режим выключен по умолчанию и включается только осознанно (демо, обсуждение с организаторами). Если организаторы подтвердят, что неизвестные `order_id` недопустимы, переключать представление на вариант 3 (меняется только сборка `Stop`).

### Правила

* **Синхронность.** Основной визит и визит напарника — копии `OrderCtx` с окном, закреплённым в точку `[s, s]` (`joint.pinned`, `joint.partner_ctx`). Декодеры (decoder.py, декодер хвоста events.py) соблюдают окно, поэтому оба начинаются ровно в `s` с той же длительностью; для каждого сотрудника отдельно выполняются H2–H8 (смена, перерыв, дорога). `s` перебирается по сетке 5 мин в `[slot.start, latest_start]`.
* **Допуски.** Напарник — другой сотрудник из того же списка допустимых (H1 по основному наряду: профиль, технология, `height_permit`).
* **АО.** Списывается только у основного исполнителя; у напарника `dem = (0, 0, 0)`, `equipment_after` не меняется.
* **Подбор (этап A).** PyVRP и вся оптимизация видят наряд одним визитом основного исполнителя. После LNS идёт пост-шаг `PyVrpPlanner._joint_pairs`: (1) напарник вставляется к прежнему началу `s` в маршрут другого допустимого сотрудника (его остальные визиты могут сдвинуться в своих окнах — декодер проверяет всё точно); (2) если не вышло — наряд снимается и ищется лучшая тройка «`s`, основной, напарник» по приросту пути; (3) затем то же для неразмещённых ВЛС-нарядов с ≥ 2 допустимыми; (4) пары нет — наряд в `unassigned`. Освободившееся время добирается жадной вставкой обычных нарядов. Выравнивание загрузки пары не трогает.
* **Причины unassigned** — без новых кодов: меньше двух допустимых сотрудников — `NO_QUALIFIED_STAFF`, иначе `NO_SHIFT_TIME` («не нашлось двух… одновременно»), `SLOT_UNREACHABLE`/`NO_EQUIPMENT` — как раньше по основному исполнителю.
* **События.** Пары восстанавливаются из прежней версии (`EventPlanner._joint_init`). `ORDER_CANCELLED` основного — снимается не начатый визит напарника. `VISIT_DELAYED`/`ORDER_TRANSFERRED` основного — напарник тоже считается начатым и заканчивает в тот же момент (`visit_end_overrides` для `ORD-…-B`, его хвост пересчитывается); если с продлением у напарника нет допустимого хвоста (перерыв/конец смены), он уходит по плану, основной заканчивает один. Ремонт снял основной визит — снимается напарник и пара подбирается заново (`joint_place`, сетка от `t`); снят напарник (выбыл, задержка) — сначала другой напарник к тому же `s`, иначе пара пересобирается целиком, иначе основной — в `unassigned`. `NEW_ORDER` ВЛС ставится сразу парой. Выталкивание и вставка нераспределённых в освободившееся время совместные наряды не трогают. Визит напарника никогда не попадает в `unassigned` и в LATE_RISK.
* **H-J** (наша проверка в `validator.py`): у каждого `ORD-…-B` есть ровно один основной визит, начало совпадает (допуск 1 с), сотрудник другой и проходит H1 (профиль/технология/допуск к высоте), адрес — дом наряда, длительность — норматив или момент из события по основному наряду; при включённом режиме у каждого основного ВЛС-визита есть напарник. Самопроверка снимает пару целиком.
* **KPI.** Пара — один наряд: `summary.visits_planned`, «размещено» в `tools/bench/run_day.py`, счётчики UI (`planMetrics`, заголовок Ганта) не считают визит напарника; бенч печатает число пар отдельно. В `totals` маршрута напарника визит учитывается (это реальные работа и загрузка сотрудника).
* **UI.** Таблица нарядов — бейдж «совместный» и «+ напарник ST-…» (поле `partner_staff_id` в `/ui/day/{date}/orders`, не в контракте); Гант — пунктирная обводка обоих визитов, метка `×2`, клик по визиту напарника выбирает основной наряд; карта — строка «напарник» в карточке наряда; пояснение `explanation` у обоих визитов.

Замеры (бюджет 60 с, `tools/bench/run_day.py 2026-10-08`): `off` — 194 размещено, 0 нарушений, 57,0 с; `on` — 194 размещено, **0 пар**, 0 нарушений H1–H9 и H-J, 57,0 с. На полных днях оптимизатор не берёт ни одного ВЛС-наряда и без пары (180 мин при весе 1 — дороже двух-трёх коротких нарядов, а 12 инсталляторов и так перегружены), поэтому режим меняет только причины: у 4 ВЛС с единственным допустимым сотрудником — `NO_QUALIFIED_STAFF` вместо `SLOT_UNREACHABLE`. Механизм проверен на облегчённом дне (ВЛС + наряды без профиля INSTALL, бюджет 15 с): 2026-10-14 — 93 → 89 визитов, 2 пары, 4 ВЛС без напарника сняты, 0 нарушений; 2026-10-08 (8 с) — 82 → 80, 1 пара. Цепочка событий на 2026-10-14 (задержка основного, отмена, выбытие напарника, NEW_ORDER ВЛС): 0 нарушений H1–H10 и H-J, 10–40 мс на событие. `run_events.py` на events_dense при `JOINT_ORDERS=on`: 112/112 событий, 0 нарушений, макс. 170 мс.
````

## File: CLAUDE.md
````markdown
# Rostelecom: умная диспетчеризация выездных бригад

Контракт API: `data/api_spec.yaml` (OpenAPI 3.0.3, строго соблюдать имена полей, типы и коды HTTP). Регион: только Екатеринбург. Стек: Python + FastAPI + PyVRP (запасной OR-Tools), OSRM/единая матрица из API, Postgres, React + TypeScript + Vite. План: `C:\Users\pavlo\.claude\plans\f-projects-rostelecom-docx-f-projects-p-kind-backus.md`.

## Агенты
- Правила распределения работы: `F:\Projects\ProjectCourse\TypeMaster\.claude\skills\agent-workflow\SKILL.md`.
- Сильная модель — последний Opus (`claude-opus-5-5`), Fable не используем из-за лимитов (решение владельца). Фронт, архитектура солвера и перепланирования, сложные баги — Opus. Когда проект будет примерно готов, финальный просмотр/ревью можно сделать Fable 5.2 (`claude-fable-5-2`) разово.
- Презентацию делает владелец.

## UI
Дизайн в стиле Ростелекома, скилл `frontend-design`. Компоненты — библиотека Атомаро (`@atomaro/ui-kit`) через MCP-сервер `Atomaro`.

When working on UI components, always use the `Atomaro` MCP tools to access Storybook's component and documentation knowledge before answering or taking any action.

- **CRITICAL: Never hallucinate component properties!** Before using ANY property on a component from a design system (including common-sounding ones like `shadow`, etc.), you MUST use the MCP tools to check if the property is actually documented for that component.
- Query `list-all-documentation` to get a list of all components
- Query `get-documentation` for that component to see all available properties and examples
- Only use properties that are explicitly documented or shown in example stories
- If a property isn't documented, do not assume properties based on naming conventions or common patterns from other libraries. Check back with the user in these cases.

Remember: A story name might not reflect the property name correctly, so always verify properties through documentation or example stories before using them.
````
