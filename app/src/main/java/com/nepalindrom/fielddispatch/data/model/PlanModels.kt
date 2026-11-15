package com.nepalindrom.fielddispatch.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Stop(
    val seq: Int,
    val type: StopType,
    val location_id: String,
    val order_id: String? = null,
    val arrival: String,
    val start: String,
    val end: String,
    val travel_sec: Int? = null,
    val distance_m: Int? = null,
    val status: StopStatus? = null,
    val equipment_after: Equipment? = null,
    val explanation: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val work_type_name: String? = null,
    val technology: Technology? = null,
    val requires_height: Boolean = false,
    val customer_phone: String? = null,
    val called_at: String? = null,
    val arrived_at: String? = null
)

@Serializable
data class RouteTotals(
    val visits: Int = 0,
    val travel_sec: Int = 0,
    val distance_m: Int = 0,
    val work_min: Int = 0,
    val utilization: Double = 0.0
)

@Serializable
data class Route(
    val staff_id: String,
    val service_center_id: String,
    val status: RouteStatus,
    val unavailable_from: String? = null,
    val unavailable_until: String? = null,
    val stops: List<Stop> = emptyList(),
    val totals: RouteTotals? = null
)

@Serializable
data class UnassignedOrder(
    val order_id: String,
    val reason_code: ReasonCode,
    val reason_text: String,
    val suggested_action: UnassignedAction,
    val proposed_slot: TimeWindow? = null,
    val since: String? = null
)

@Serializable
data class PgpGroup(
    val group_id: String,
    val access_node_id: String,
    val order_ids: List<String>,
    val detected_at: String,
    val action: PgpAction,
    val comment: String? = null
)

@Serializable
data class CancelledOrder(
    val order_id: String,
    val cancelled_at: String,
    val event_id: String? = null
)

@Serializable
data class Alert(
    val alert_id: String,
    val created_at: String,
    val type: AlertType,
    val status: AlertStatus,
    val resolved_at: String? = null,
    val order_id: String? = null,
    val group_id: String? = null,
    val staff_id: String? = null,
    val forecast_start: String? = null,
    val lateness_min: Int? = null,
    val suggested_action: AlertAction,
    val suggested_staff_id: String? = null,
    val proposed_slot: TimeWindow? = null,
    val applied: Boolean = false,
    val message: String
)

@Serializable
data class PlanSummary(
    val orders_total: Int? = null,
    val visits_planned: Int? = null,
    val unassigned: Int? = null,
    val pgp_orders: Int? = null,
    val cancelled: Int? = null,
    val travel_sec_total: Int? = null,
    val utilization_cv: Double? = null
)

@Serializable
data class Plan(
    val date: String,
    val plan_version: Int,
    val based_on_event_id: String? = null,
    val as_of: String,
    val computation_ms: Int? = null,
    val routes: List<Route> = emptyList(),
    val unassigned: List<UnassignedOrder> = emptyList(),
    val pgp_groups: List<PgpGroup> = emptyList(),
    val cancelled: List<CancelledOrder> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val summary: PlanSummary? = null
)
