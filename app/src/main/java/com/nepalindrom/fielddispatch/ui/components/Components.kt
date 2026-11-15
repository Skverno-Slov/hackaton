package com.nepalindrom.fielddispatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nepalindrom.fielddispatch.data.model.AlertAction
import com.nepalindrom.fielddispatch.data.model.AlertStatus
import com.nepalindrom.fielddispatch.data.model.AlertType
import com.nepalindrom.fielddispatch.data.model.Equipment
import com.nepalindrom.fielddispatch.data.model.ReasonCode
import com.nepalindrom.fielddispatch.data.model.RouteStatus
import com.nepalindrom.fielddispatch.data.model.StopStatus
import com.nepalindrom.fielddispatch.data.model.StopType
import com.nepalindrom.fielddispatch.data.model.UnassignedAction
import com.nepalindrom.fielddispatch.ui.theme.RostelecomPurple
import com.nepalindrom.fielddispatch.ui.theme.StatusBlue
import com.nepalindrom.fielddispatch.ui.theme.StatusDangerRed
import com.nepalindrom.fielddispatch.ui.theme.StatusGreen
import com.nepalindrom.fielddispatch.ui.theme.StatusNeutralGray
import com.nepalindrom.fielddispatch.ui.theme.StatusWarningOrange

@Composable
fun StatusBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color = Color.White,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor.copy(alpha = 0.15f),
        contentColor = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = backgroundColor,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 4.dp)
                )
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = backgroundColor
            )
        }
    }
}

@Composable
fun StopStatusBadge(status: StopStatus?) {
    when (status) {
        StopStatus.PLANNED -> StatusBadge(
            text = "Запланирован",
            backgroundColor = StatusBlue,
            icon = Icons.Default.Info
        )
        StopStatus.IN_PROGRESS -> StatusBadge(
            text = "В работе",
            backgroundColor = StatusWarningOrange,
            icon = Icons.Default.Warning
        )
        StopStatus.DONE -> StatusBadge(
            text = "Завершён",
            backgroundColor = StatusGreen,
            icon = Icons.Default.CheckCircle
        )
        null -> StatusBadge(text = "План", backgroundColor = StatusNeutralGray)
    }
}

@Composable
fun StopTypeBadge(type: StopType) {
    val (label, color) = when (type) {
        StopType.DEPOT_START -> "Выезд из СЦ" to RostelecomPurple
        StopType.VISIT -> "Визит к клиенту" to StatusBlue
        StopType.BREAK -> "Обед / Перерыв" to StatusWarningOrange
        StopType.RESTOCK -> "Пополнение АО" to StatusGreen
        StopType.DEPOT_END -> "Возврат в СЦ" to StatusNeutralGray
    }
    StatusBadge(text = label, backgroundColor = color)
}

@Composable
fun RouteStatusBadge(status: RouteStatus) {
    when (status) {
        RouteStatus.ACTIVE -> StatusBadge(
            text = "На смене",
            backgroundColor = StatusGreen,
            icon = Icons.Default.CheckCircle
        )
        RouteStatus.UNAVAILABLE -> StatusBadge(
            text = "Недоступен",
            backgroundColor = StatusDangerRed,
            icon = Icons.Default.Warning
        )
    }
}

@Composable
fun AlertTypeBadge(type: AlertType) {
    val (label, color) = when (type) {
        AlertType.LATE_RISK -> "Риск опоздания" to StatusWarningOrange
        AlertType.PGP_DETECTED -> "Групповая проблема" to StatusDangerRed
        AlertType.UNASSIGNED -> "Нераспределён" to StatusBlue
    }
    StatusBadge(text = label, backgroundColor = color)
}

@Composable
fun AlertStatusBadge(status: AlertStatus) {
    when (status) {
        AlertStatus.ACTIVE -> StatusBadge(text = "АКТИВНО", backgroundColor = StatusDangerRed)
        AlertStatus.RESOLVED -> StatusBadge(text = "УСТРАНЕНО", backgroundColor = StatusGreen)
    }
}

@Composable
fun ReasonCodeBadge(code: ReasonCode) {
    val (label, color) = when (code) {
        ReasonCode.NO_QUALIFIED_STAFF -> "Нет кваллифицированного персонала" to StatusDangerRed
        ReasonCode.NO_SHIFT_TIME -> "Нехватка времени смены" to StatusWarningOrange
        ReasonCode.SLOT_UNREACHABLE -> "Слот недостижим" to StatusWarningOrange
        ReasonCode.NO_EQUIPMENT -> "Нехватка оборудования" to StatusWarningOrange
        ReasonCode.STAFF_UNAVAILABLE -> "Исполнитель недоступен" to StatusDangerRed
        ReasonCode.OTHER -> "Прочее" to StatusNeutralGray
    }
    StatusBadge(text = label, backgroundColor = color)
}

@Composable
fun ActionBadge(action: AlertAction) {
    val label = when (action) {
        AlertAction.REASSIGN -> "Переназначить"
        AlertAction.RESCHEDULE -> "Перенести слот"
        AlertAction.TRANSFER_TO_STATION -> "В станцию"
        AlertAction.NONE -> "Информация"
    }
    StatusBadge(text = label, backgroundColor = RostelecomPurple)
}

@Composable
fun EquipmentChip(equipment: Equipment, labelPrefix: String = "Баланс АО:") {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = labelPrefix,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EquipmentItem(icon = Icons.Default.Router, name = "ONT", count = equipment.ONT)
            EquipmentItem(icon = Icons.Default.Memory, name = "ROUTER", count = equipment.ROUTER)
            EquipmentItem(icon = Icons.Default.Computer, name = "STB", count = equipment.STB)
        }
    }
}

@Composable
private fun EquipmentItem(icon: ImageVector, name: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            tint = RostelecomPurple,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "$name: $count",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String? = null,
    badgeColor: Color = RostelecomPurple,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ServerConfigDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки сервера API") },
        text = {
            Column {
                Text(
                    text = "Укажите IP адрес и порт сервера:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("http://26.24.226.0:8081/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { urlText = "http://26.24.226.0:8081/" }) {
                        Text("26.24.226.0:8081")
                    }
                    TextButton(onClick = { urlText = "http://10.0.2.2:8081/" }) {
                        Text("Emulator 10.0.2.2")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(urlText) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
