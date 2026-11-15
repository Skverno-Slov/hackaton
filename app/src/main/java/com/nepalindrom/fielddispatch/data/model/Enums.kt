package com.nepalindrom.fielddispatch.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Technology {
    @SerialName("PON") PON,
    @SerialName("FTTB") FTTB,
    @SerialName("XDSL") XDSL,
    @SerialName("DOCSIS") DOCSIS
}

@Serializable
enum class Profile {
    @SerialName("INSTALL") INSTALL,
    @SerialName("LTP3") LTP3
}

@Serializable
enum class WorkTypeCode {
    @SerialName("INST_PON_MKD") INST_PON_MKD,
    @SerialName("INST_FTTB_MKD") INST_FTTB_MKD,
    @SerialName("INST_PRIVATE_VLS") INST_PRIVATE_VLS,
    @SerialName("ADD_SERVICE") ADD_SERVICE,
    @SerialName("SURVEY_PRIVATE") SURVEY_PRIVATE,
    @SerialName("REPAIR") REPAIR,
    @SerialName("DEGRADATION") DEGRADATION,
    @SerialName("DISMANTLE") DISMANTLE
}

@Serializable
enum class BuildingType {
    @SerialName("MKD") MKD,
    @SerialName("PRIVATE") PRIVATE
}

@Serializable
enum class NodeType {
    @SerialName("OLT") OLT,
    @SerialName("SWITCH") SWITCH,
    @SerialName("DSLAM") DSLAM,
    @SerialName("CMTS") CMTS
}

@Serializable
enum class DeadlineKind {
    @SerialName("SLOT") SLOT,
    @SerialName("HOURS") HOURS,
    @SerialName("WORKING_DAYS") WORKING_DAYS
}

@Serializable
enum class OrderOutcome {
    @SerialName("COMPLETED") COMPLETED,
    @SerialName("TRANSFERRED_TO_STATION") TRANSFERRED_TO_STATION,
    @SerialName("CANCELLED") CANCELLED,
    @SerialName("RESCHEDULED") RESCHEDULED,
    @SerialName("PGP_GROUPED") PGP_GROUPED
}

@Serializable
enum class RouteStatus {
    @SerialName("ACTIVE") ACTIVE,
    @SerialName("UNAVAILABLE") UNAVAILABLE
}

@Serializable
enum class StopType {
    @SerialName("DEPOT_START") DEPOT_START,
    @SerialName("VISIT") VISIT,
    @SerialName("BREAK") BREAK,
    @SerialName("RESTOCK") RESTOCK,
    @SerialName("DEPOT_END") DEPOT_END
}

@Serializable
enum class StopStatus {
    @SerialName("PLANNED") PLANNED,
    @SerialName("IN_PROGRESS") IN_PROGRESS,
    @SerialName("DONE") DONE
}

@Serializable
enum class ReasonCode {
    @SerialName("NO_QUALIFIED_STAFF") NO_QUALIFIED_STAFF,
    @SerialName("NO_SHIFT_TIME") NO_SHIFT_TIME,
    @SerialName("SLOT_UNREACHABLE") SLOT_UNREACHABLE,
    @SerialName("NO_EQUIPMENT") NO_EQUIPMENT,
    @SerialName("STAFF_UNAVAILABLE") STAFF_UNAVAILABLE,
    @SerialName("OTHER") OTHER
}

@Serializable
enum class UnassignedAction {
    @SerialName("RESCHEDULE") RESCHEDULE,
    @SerialName("REASSIGN") REASSIGN,
    @SerialName("ESCALATE") ESCALATE
}

@Serializable
enum class AlertType {
    @SerialName("LATE_RISK") LATE_RISK,
    @SerialName("PGP_DETECTED") PGP_DETECTED,
    @SerialName("UNASSIGNED") UNASSIGNED
}

@Serializable
enum class AlertStatus {
    @SerialName("ACTIVE") ACTIVE,
    @SerialName("RESOLVED") RESOLVED
}

@Serializable
enum class AlertAction {
    @SerialName("REASSIGN") REASSIGN,
    @SerialName("RESCHEDULE") RESCHEDULE,
    @SerialName("TRANSFER_TO_STATION") TRANSFER_TO_STATION,
    @SerialName("NONE") NONE
}

@Serializable
enum class PgpAction {
    @SerialName("TRANSFER_TO_STATION") TRANSFER_TO_STATION
}

@Serializable
enum class EventType {
    @SerialName("NEW_ORDER") NEW_ORDER,
    @SerialName("ORDER_CANCELLED") ORDER_CANCELLED,
    @SerialName("VISIT_DELAYED") VISIT_DELAYED,
    @SerialName("ORDER_TRANSFERRED") ORDER_TRANSFERRED,
    @SerialName("STAFF_UNAVAILABLE") STAFF_UNAVAILABLE,
    @SerialName("TIME_TICK") TIME_TICK
}

@Serializable
enum class CancelReason {
    @SerialName("NOT_CONFIRMED") NOT_CONFIRMED,
    @SerialName("CUSTOMER_REQUEST") CUSTOMER_REQUEST
}

@Serializable
enum class TransferredTo {
    @SerialName("STATION_EQUIPMENT") STATION_EQUIPMENT,
    @SerialName("TRUNK_CABLE") TRUNK_CABLE
}

@Serializable
enum class UnavailableReason {
    @SerialName("VEHICLE_BREAKDOWN") VEHICLE_BREAKDOWN,
    @SerialName("ILLNESS") ILLNESS,
    @SerialName("OTHER") OTHER
}
