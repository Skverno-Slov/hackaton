package com.nepalindrom.fielddispatch.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Equipment(
    val ONT: Int = 0,
    val ROUTER: Int = 0,
    val STB: Int = 0
)

@Serializable
data class TimeWindow(
    val start: String,
    val end: String
)

@Serializable
data class OpeningHours(
    val open: String,
    val close: String
)

@Serializable
data class ServiceCenter(
    val service_center_id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val opening_hours: OpeningHours,
    val restock_duration_min: Int
)

@Serializable
data class BreakWindow(
    @SerialName("from") val from: String,
    val to: String
)

@Serializable
data class Shift(
    val start: String,
    val end: String,
    val break_duration_min: Int,
    val break_window: BreakWindow
)

@Serializable
data class Staff(
    val staff_id: String,
    val display_name: String? = null,
    val service_center_id: String,
    val profiles: List<Profile>,
    val technologies: List<Technology>,
    val height_permit: Boolean,
    val shift: Shift,
    val equipment_start: Equipment,
    val equipment_capacity: Equipment,
    val experience_years: Double? = null
)

@Serializable
data class Building(
    val building_id: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val building_type: BuildingType,
    val overhead_line: Boolean,
    val zone_service_center_id: String? = null
)

@Serializable
data class AccessNode(
    val access_node_id: String,
    val node_type: NodeType,
    val technology: Technology,
    val name: String,
    val address: String? = null,
    val lat: Double,
    val lon: Double,
    val buildings: List<Building>
)

@Serializable
data class WorkType(
    val code: WorkTypeCode,
    val name: String,
    val profiles: List<Profile>,
    val duration_min: Int,
    val max_start_delay_min: Int? = null,
    val deadline_kind: DeadlineKind,
    val deadline_value: Int? = null,
    val repeat_deadline_value: Int? = null,
    val pgp_relevant: Boolean
)

@Serializable
data class HourlyFactor(
    val hour: Int,
    val factor: Double
)

@Serializable
data class TravelMatrix(
    val matrix_id: String,
    val points: List<String>,
    val durations_sec: List<List<Int>>,
    val distances_m: List<List<Int>>,
    val hourly_factors: List<HourlyFactor> = emptyList()
)

@Serializable
data class ReferenceData(
    val service_centers: List<ServiceCenter>,
    val staff: List<Staff>,
    val access_nodes: List<AccessNode>,
    val work_types: List<WorkType>
)

@Serializable
data class OrderFact(
    val outcome: OrderOutcome? = null,
    val staff_id: String? = null,
    val actual_start: String? = null,
    val actual_duration_min: Int? = null,
    val pgp_incident_id: String? = null
)

@Serializable
data class Order(
    val order_id: String,
    val work_type_code: WorkTypeCode,
    val technology: Technology,
    val building_id: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val building_type: BuildingType,
    val requires_height: Boolean,
    val access_node_id: String,
    val registered_at: String,
    val slot: TimeWindow,
    val latest_start: String,
    val deadline: String,
    val required_equipment: Equipment,
    val is_repeat: Boolean,
    val previous_order_id: String? = null,
    val fact: OrderFact? = null
)

@Serializable
data class DayInput(
    val date: String,
    val now: String,
    val orders: List<Order>,
    val absent_staff_ids: List<String> = emptyList()
)

@Serializable
data class PlanRequest(
    val time_limit_sec: Int = 300
)
