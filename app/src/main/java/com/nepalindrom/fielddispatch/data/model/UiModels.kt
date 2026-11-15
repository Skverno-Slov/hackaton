package com.nepalindrom.fielddispatch.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UiReference(
    val service_centers: List<ServiceCenter> = emptyList(),
    val staff: List<Staff> = emptyList(),
    val access_nodes: List<AccessNode> = emptyList(),
    val work_types: List<WorkType> = emptyList()
)

@Serializable
data class UiOrder(
    val order_id: String,
    val work_type_code: WorkTypeCode,
    val work_type_name: String = "",
    val technology: Technology,
    val building_id: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val building_type: BuildingType,
    val requires_height: Boolean = false,
    val access_node_id: String = "",
    val slot: TimeWindow,
    val customer_phone: String? = null,
    val status: String? = null,
    val planned_start: String? = null,
    val planned_end: String? = null,
    val staff_id: String? = null
)

@Serializable
data class UiDayOrders(
    val date: String,
    val orders: List<UiOrder> = emptyList()
)

@Serializable
data class UiEmployeeShift(
    val start: String = "",
    val end: String = ""
)

@Serializable
data class UiEmployeeStaffItem(
    val staff_id: String,
    val display_name: String? = null,
    val profiles: List<String> = emptyList(),
    val service_center_id: String = "",
    val service_center_name: String? = null,
    val shift: UiEmployeeShift? = null,
    val route_status: RouteStatus? = null,
    val visits: Int = 0,
    val visits_done: Int = 0
)

@Serializable
data class UiEmployeeStaffResponse(
    val date: String,
    val plan_version: Int? = null,
    val as_of: String? = null,
    val staff: List<UiEmployeeStaffItem> = emptyList()
)
