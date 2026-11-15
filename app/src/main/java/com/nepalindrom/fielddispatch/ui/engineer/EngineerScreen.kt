package com.nepalindrom.fielddispatch.ui.engineer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nepalindrom.fielddispatch.data.model.ApiResult
import com.nepalindrom.fielddispatch.data.model.Route
import com.nepalindrom.fielddispatch.data.model.Stop
import com.nepalindrom.fielddispatch.data.model.StopStatus
import com.nepalindrom.fielddispatch.data.model.StopType
import com.nepalindrom.fielddispatch.ui.components.EquipmentChip
import com.nepalindrom.fielddispatch.ui.components.KpiCard
import com.nepalindrom.fielddispatch.ui.components.RouteStatusBadge
import com.nepalindrom.fielddispatch.ui.components.StopStatusBadge
import com.nepalindrom.fielddispatch.ui.components.StopTypeBadge
import com.nepalindrom.fielddispatch.ui.theme.RostelecomPurple
import com.nepalindrom.fielddispatch.ui.theme.StatusBlue
import com.nepalindrom.fielddispatch.ui.theme.StatusDangerRed
import com.nepalindrom.fielddispatch.ui.theme.StatusGreen
import com.nepalindrom.fielddispatch.ui.theme.StatusWarningOrange
import com.nepalindrom.fielddispatch.util.YekaterinburgTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineerScreen(
    currentDate: String,
    viewModel: EngineerViewModel,
    onSelectDateClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedStaffId by viewModel.selectedStaffId.collectAsState()
    val selectedStop by viewModel.selectedStop.collectAsState()
    val planState by viewModel.planState.collectAsState()
    val eventResult by viewModel.eventResult.collectAsState()

    var showDelayedDialog by remember { mutableStateOf(false) }
    var showTransferredDialog by remember { mutableStateOf(false) }
    var showUnavailableDialog by remember { mutableStateOf(false) }
    var actionTargetStop by remember { mutableStateOf<Stop?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(currentDate) {
        viewModel.loadPlan(currentDate)
    }

    LaunchedEffect(eventResult) {
        val result = eventResult
        if (result is ApiResult.Success) {
            val msg = result.data.message ?: "Событие отправлено на сервер"
            snackbarHostState.showSnackbar(msg)
            viewModel.clearEventResult()
        } else if (result is ApiResult.Error) {
            snackbarHostState.showSnackbar("Ошибка: ${result.rawMessage}")
            viewModel.clearEventResult()
        }
    }

    val currentPlan = (planState as? ApiResult.Success)?.data
    val engineerRoute = currentPlan?.routes?.firstOrNull { it.staff_id == selectedStaffId }

    // Target stop for Hero Navigation — skip DEPOT_START, DEPOT_END, BREAK; target VISIT or RESTOCK
    val actionableTypes = setOf(StopType.VISIT, StopType.RESTOCK)
    val targetStop = selectedStop?.takeIf { it.type in actionableTypes }
        ?: engineerRoute?.stops?.firstOrNull { it.status == StopStatus.IN_PROGRESS && it.type in actionableTypes }
        ?: engineerRoute?.stops?.firstOrNull { it.status == StopStatus.PLANNED && it.type in actionableTypes }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {

            when (planState) {
                is ApiResult.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = RostelecomPurple)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Загрузка маршрута…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                is ApiResult.Error -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        EmptyStateCard(
                            currentDate = currentDate,
                            onRetry = {
                                viewModel.loadReference()
                                viewModel.loadPlan(currentDate)
                            },
                            onSettings = onSelectDateClick
                        )
                    }
                }
                is ApiResult.Success -> {
                    if (engineerRoute == null) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            EmptyStateCard(
                                currentDate = currentDate,
                                title = "Нет маршрута на $currentDate",
                                subtitle = "Сотрудник $selectedStaffId не задействован в маршрутах на выбранную дату.",
                                onRetry = {
                                    viewModel.loadReference()
                                    viewModel.loadPlan(currentDate)
                                },
                                onSettings = onSelectDateClick
                            )
                        }
                    } else {
                        // Personal Route Content + Interactive Map
                        EngineerRouteContent(
                            currentDate = currentDate,
                            route = engineerRoute,
                            targetStop = targetStop,
                            viewModel = viewModel,
                            onDelayedClick = { stop ->
                                actionTargetStop = stop
                                showDelayedDialog = true
                            },
                            onTransferredClick = { stop ->
                                actionTargetStop = stop
                                showTransferredDialog = true
                            },
                            onUnavailableClick = {
                                showUnavailableDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }

    // Event Dialogs
    if (showDelayedDialog) {
        VisitDelayedDialog(
            stops = engineerRoute?.stops ?: emptyList(),
            initialOrder = actionTargetStop,
            onDismiss = { showDelayedDialog = false; actionTargetStop = null },
            onSubmit = { orderId, expectedEnd ->
                viewModel.postVisitDelayed(currentDate, orderId, expectedEnd)
                showDelayedDialog = false
                actionTargetStop = null
            }
        )
    }

    if (showTransferredDialog) {
        OrderTransferredDialog(
            stops = engineerRoute?.stops ?: emptyList(),
            initialOrder = actionTargetStop,
            onDismiss = { showTransferredDialog = false; actionTargetStop = null },
            onSubmit = { orderId, actualEnd, target ->
                viewModel.postOrderTransferred(currentDate, orderId, actualEnd, target)
                showTransferredDialog = false
                actionTargetStop = null
            }
        )
    }

    if (showUnavailableDialog) {
        StaffUnavailableDialog(
            staffId = selectedStaffId,
            onDismiss = { showUnavailableDialog = false },
            onSubmit = { reason, untilIso ->
                viewModel.postStaffUnavailable(currentDate, reason, untilIso)
                showUnavailableDialog = false
            }
        )
    }
}

@Composable
private fun EmptyStateCard(
    currentDate: String,
    title: String = "Маршрут ещё не сформирован",
    subtitle: String = "На выбранную дату план выездов ещё не опубликован диспетчером.",
    onRetry: () -> Unit,
    onSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(StatusWarningOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = StatusWarningOrange,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = RostelecomPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Обновить", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Изменить настройки подключения / дату", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EngineerRouteContent(
    currentDate: String,
    route: Route,
    targetStop: Stop?,
    viewModel: EngineerViewModel,
    onDelayedClick: (Stop) -> Unit,
    onTransferredClick: (Stop) -> Unit,
    onUnavailableClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Hero Navigation Card — only for VISIT / RESTOCK
        if (targetStop != null) {
            HeroNavigationCard(
                stop = targetStop,
                onCallClick = {
                    if (targetStop.order_id != null) {
                        viewModel.markCall(currentDate, targetStop.order_id)
                    }
                },
                onArrivalClick = {
                    if (targetStop.order_id != null) {
                        viewModel.markArrival(currentDate, targetStop.order_id)
                    }
                },
                onCompleteClick = {
                    if (targetStop.order_id != null) {
                        viewModel.completeVisit(currentDate, targetStop.order_id)
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Status & SC Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RouteStatusBadge(route.status)
            Text(
                text = "СЦ: ${route.service_center_id}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Totals KPI
        val totals = route.totals
        if (totals != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KpiCard(
                    title = "Визиты",
                    value = "${totals.visits}",
                    badgeColor = StatusBlue,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "В пути",
                    value = YekaterinburgTime.formatDurationMin(totals.travel_sec),
                    subtitle = YekaterinburgTime.formatDistanceKm(totals.distance_m),
                    badgeColor = StatusWarningOrange,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Загрузка",
                    value = "${(totals.utilization * 100).toInt()}%",
                    subtitle = "${totals.work_min} мин работы",
                    badgeColor = StatusGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Utilization Bar
        val utilPct = (totals?.utilization ?: 0.0).toFloat().coerceIn(0f, 1f)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Утилизация смены", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(utilPct * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RostelecomPurple)
            }
            Spacer(modifier = Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { utilPct },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (utilPct > 0.9f) StatusWarningOrange else RostelecomPurple,
            )
        }

        // Action Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onDelayedClick(route.stops.firstOrNull { it.type == StopType.VISIT } ?: route.stops.first()) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Задержка", fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = { onTransferredClick(route.stops.firstOrNull { it.type == StopType.VISIT } ?: route.stops.first()) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Передать", fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
            Button(
                onClick = onUnavailableClick,
                colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Выбыл", fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Маршрутный лист (${route.stops.size}):",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Interactive Timeline List
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(route.stops, key = { "${it.seq}-${it.type}-${it.order_id}" }) { stop ->
                TimelineStopCard(
                    stop = stop,
                    onStopSelect = { viewModel.setSelectedStop(stop) },
                    onArrivalClick = {
                        if (stop.order_id != null) viewModel.markArrival(currentDate, stop.order_id)
                    },
                    onCompleteClick = {
                        if (stop.order_id != null) viewModel.completeVisit(currentDate, stop.order_id)
                    },
                    onDelayedClick = { onDelayedClick(stop) },
                    onTransferredClick = { onTransferredClick(stop) }
                )
            }
        }
    }
}

@Composable
private fun TimelineStopCard(
    stop: Stop,
    onStopSelect: () -> Unit,
    onArrivalClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onDelayedClick: () -> Unit,
    onTransferredClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (stop.status) {
                StopStatus.IN_PROGRESS -> StatusWarningOrange.copy(alpha = 0.08f)
                StopStatus.DONE -> StatusGreen.copy(alpha = 0.05f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        onClick = onStopSelect
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Seq #, Badges, Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(RostelecomPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${stop.seq}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    StopTypeBadge(stop.type)
                }

                StopStatusBadge(stop.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location & Order ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.address ?: "Точка: ${stop.location_id}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (stop.order_id != null) {
                        Text(
                            text = "Наряд: ${stop.order_id}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = RostelecomPurple
                        )
                    }
                }

                // Time Window
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${YekaterinburgTime.formatTime(stop.start)} — ${YekaterinburgTime.formatTime(stop.end)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (stop.travel_sec != null && stop.travel_sec > 0) {
                        Text(
                            text = "В пути: ${YekaterinburgTime.formatDurationMin(stop.travel_sec)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Equipment After Balance
            if (stop.equipment_after != null) {
                Spacer(modifier = Modifier.height(8.dp))
                EquipmentChip(equipment = stop.equipment_after, labelPrefix = "АО после:")
            }

            // Explanation
            if (!stop.explanation.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = StatusBlue,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stop.explanation,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Step Action Buttons for Visit
            if (stop.type == StopType.VISIT && stop.order_id != null && stop.status != StopStatus.DONE) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (stop.status == StopStatus.IN_PROGRESS) {
                        Button(
                            onClick = onCompleteClick,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Завершить визит", fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = onArrivalClick,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Прибыл на место", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = onDelayedClick) {
                        Text("Задержка", fontSize = 11.sp, color = StatusWarningOrange)
                    }
                    TextButton(onClick = onTransferredClick) {
                        Text("Передать", fontSize = 11.sp, color = StatusBlue)
                    }
                }
            }
        }
    }
}
