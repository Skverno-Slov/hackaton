package com.nepalindrom.fielddispatch.ui.engineer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nepalindrom.fielddispatch.data.model.Stop
import com.nepalindrom.fielddispatch.data.model.StopType
import com.nepalindrom.fielddispatch.data.model.TransferredTo
import com.nepalindrom.fielddispatch.data.model.UnavailableReason
import com.nepalindrom.fielddispatch.ui.theme.RostelecomPurple
import com.nepalindrom.fielddispatch.ui.theme.StatusDangerRed
import com.nepalindrom.fielddispatch.util.YekaterinburgTime
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitDelayedDialog(
    stops: List<Stop>,
    initialOrder: Stop? = null,
    onDismiss: () -> Unit,
    onSubmit: (orderId: String, expectedEndIso: String) -> Unit
) {
    val visitStops = stops.filter { it.type == StopType.VISIT && it.order_id != null }
    var selectedStop by remember { mutableStateOf(initialOrder ?: visitStops.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    // Default expected end + 30 mins
    var endMinutesAdd by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сообщить о задержке визита") },
        text = {
            Column {
                Text("Выберите наряд:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedStop?.let { "${it.order_id} (${YekaterinburgTime.formatTime(it.start)})" } ?: "Нет визитов",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        visitStops.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.order_id} — ${s.location_id}") },
                                onClick = {
                                    selectedStop = s
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Дополнительное время выполнения (мин):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = endMinutesAdd,
                    onValueChange = { endMinutesAdd = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                val addMin = endMinutesAdd.toIntOrNull() ?: 30
                val calculatedEnd = try {
                    val base = selectedStop?.end?.let { YekaterinburgTime.parseOffsetDateTime(it) }
                        ?: selectedStop?.start?.let { YekaterinburgTime.parseOffsetDateTime(it)?.plusMinutes(45) }
                    base?.plusMinutes(addMin.toLong())?.let { YekaterinburgTime.formatTime(YekaterinburgTime.formatIso(it)) } ?: "--:--"
                } catch (_: Exception) {
                    "--:--"
                }

                Text(
                    text = "Ожидаемое завершение: $calculatedEnd (UTC+5)",
                    fontSize = 12.sp,
                    color = RostelecomPurple
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val orderId = selectedStop?.order_id ?: return@Button
                    val addMin = endMinutesAdd.toIntOrNull() ?: 30
                    val base = selectedStop?.end?.let { YekaterinburgTime.parseOffsetDateTime(it) }
                        ?: selectedStop?.start?.let { YekaterinburgTime.parseOffsetDateTime(it)?.plusMinutes(45) }
                    val isoEnd = if (base != null) {
                        YekaterinburgTime.formatIso(base.plusMinutes(addMin.toLong()))
                    } else ""
                    onSubmit(orderId, isoEnd)
                },
                enabled = selectedStop != null
            ) {
                Text("Отправить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTransferredDialog(
    stops: List<Stop>,
    initialOrder: Stop? = null,
    onDismiss: () -> Unit,
    onSubmit: (orderId: String, actualEndIso: String, transferredTo: TransferredTo) -> Unit
) {
    val visitStops = stops.filter { it.type == StopType.VISIT && it.order_id != null }
    var selectedStop by remember { mutableStateOf(initialOrder ?: visitStops.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    var selectedTarget by remember { mutableStateOf(TransferredTo.STATION_EQUIPMENT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Передать наряд") },
        text = {
            Column {
                Text("Выберите наряд:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedStop?.let { "${it.order_id} (${it.location_id})" } ?: "Нет визитов",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        visitStops.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.order_id} — ${s.location_id}") },
                                onClick = {
                                    selectedStop = s
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Подразделение передачи:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (selectedTarget == TransferredTo.STATION_EQUIPMENT),
                        onClick = { selectedTarget = TransferredTo.STATION_EQUIPMENT }
                    )
                    Text("Станционное АО", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (selectedTarget == TransferredTo.TRUNK_CABLE),
                        onClick = { selectedTarget = TransferredTo.TRUNK_CABLE }
                    )
                    Text("Магистральный кабель", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val orderId = selectedStop?.order_id ?: return@Button
                    val actualEndIso = selectedStop?.start ?: ""
                    onSubmit(orderId, actualEndIso, selectedTarget)
                },
                enabled = selectedStop != null
            ) {
                Text("Передать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun StaffUnavailableDialog(
    staffId: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: UnavailableReason, untilIso: String?) -> Unit
) {
    var reason by remember { mutableStateOf(UnavailableReason.VEHICLE_BREAKDOWN) }
    var hasUntil by remember { mutableStateOf(false) }
    var hoursUntil by remember { mutableStateOf("2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заявить о недоступности ($staffId)") },
        text = {
            Column {
                Text("Причина выбытия из смены:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))

                UnavailableReason.entries.forEach { r ->
                    val label = when (r) {
                        UnavailableReason.VEHICLE_BREAKDOWN -> "Поломка авто"
                        UnavailableReason.ILLNESS -> "Болезнь"
                        UnavailableReason.OTHER -> "Иное событие"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = (reason == r),
                            onClick = { reason = r }
                        )
                        Text(label, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !hasUntil, onClick = { hasUntil = false })
                    Text("До конца смены", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = hasUntil, onClick = { hasUntil = true })
                    Text("Временно на (часов):", fontSize = 13.sp)
                    OutlinedTextField(
                        value = hoursUntil,
                        onValueChange = { hoursUntil = it.filter { c -> c.isDigit() } },
                        enabled = hasUntil,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                onClick = {
                    val untilIso = if (hasUntil) {
                        hoursUntil.ifBlank { "2" }
                    } else null
                    onSubmit(reason, untilIso)
                }
            ) {
                Text("Зафиксировать выбытие")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
