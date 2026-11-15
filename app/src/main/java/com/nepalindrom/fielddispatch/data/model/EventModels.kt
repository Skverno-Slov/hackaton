package com.nepalindrom.fielddispatch.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Event {
    val event_id: String
    val timestamp: String
}

@Serializable
@SerialName("NEW_ORDER")
data class NewOrderEvent(
    override val event_id: String,
    override val timestamp: String,
    val order: Order
) : Event

@Serializable
@SerialName("ORDER_CANCELLED")
data class OrderCancelledEvent(
    override val event_id: String,
    override val timestamp: String,
    val order_id: String,
    val reason: CancelReason
) : Event

@Serializable
@SerialName("VISIT_DELAYED")
data class VisitDelayedEvent(
    override val event_id: String,
    override val timestamp: String,
    val staff_id: String,
    val order_id: String,
    val expected_end: String
) : Event

@Serializable
@SerialName("ORDER_TRANSFERRED")
data class OrderTransferredEvent(
    override val event_id: String,
    override val timestamp: String,
    val staff_id: String,
    val order_id: String,
    val actual_end: String,
    val transferred_to: TransferredTo
) : Event

@Serializable
@SerialName("STAFF_UNAVAILABLE")
data class StaffUnavailableEvent(
    override val event_id: String,
    override val timestamp: String,
    val staff_id: String,
    val until: String? = null,
    val reason: UnavailableReason
) : Event

@Serializable
@SerialName("TIME_TICK")
data class TimeTickEvent(
    override val event_id: String,
    override val timestamp: String
) : Event

@Serializable
data class EventResult(
    val event_id: String,
    val accepted: Boolean,
    val duplicate: Boolean,
    val plan_version: Int,
    val as_of: String,
    val processing_ms: Int? = null,
    val changed_staff_ids: List<String> = emptyList(),
    val new_alerts: List<Alert> = emptyList(),
    val message: String? = null
)
