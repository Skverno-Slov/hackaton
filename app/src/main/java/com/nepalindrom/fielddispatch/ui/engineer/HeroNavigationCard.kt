package com.nepalindrom.fielddispatch.ui.engineer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nepalindrom.fielddispatch.data.model.Stop
import com.nepalindrom.fielddispatch.data.model.StopStatus
import com.nepalindrom.fielddispatch.data.model.StopType
import com.nepalindrom.fielddispatch.ui.components.EquipmentChip
import com.nepalindrom.fielddispatch.ui.components.StopStatusBadge
import com.nepalindrom.fielddispatch.ui.components.StopTypeBadge
import com.nepalindrom.fielddispatch.ui.theme.RostelecomOrange
import com.nepalindrom.fielddispatch.ui.theme.RostelecomPurple
import com.nepalindrom.fielddispatch.ui.theme.StatusGreen
import com.nepalindrom.fielddispatch.ui.theme.StatusWarningOrange
import com.nepalindrom.fielddispatch.util.YekaterinburgTime

@Composable
fun HeroNavigationCard(
    stop: Stop,
    onCallClick: () -> Unit,
    onArrivalClick: () -> Unit,
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lat = stop.lat ?: 56.8389
    val lon = stop.lon ?: 60.6057
    val addressStr = stop.address ?: stop.location_id
    val isVisit = stop.type == StopType.VISIT

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Banner Label & Status
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
                            .background(RostelecomOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (stop.status == StopStatus.IN_PROGRESS) "Текущий визит в работе" else "Следующая точка маршрута",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RostelecomOrange
                    )
                }

                StopStatusBadge(stop.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Address & Work Type
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = RostelecomPurple,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = addressStr,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (stop.work_type_name != null) {
                        Text(
                            text = stop.work_type_name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = RostelecomPurple
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Meta Info: Time window, type badge, order id
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StopTypeBadge(stop.type)
                    if (stop.order_id != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stop.order_id,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "${YekaterinburgTime.formatTime(stop.start)} — ${YekaterinburgTime.formatTime(stop.end)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (stop.travel_sec != null && stop.travel_sec > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "В пути: ${YekaterinburgTime.formatDurationMin(stop.travel_sec)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Расстояние: ${YekaterinburgTime.formatDistanceKm(stop.distance_m)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Equipment required (for VISIT)
            if (isVisit && stop.equipment_after != null) {
                Spacer(modifier = Modifier.height(6.dp))
                EquipmentChip(equipment = stop.equipment_after, labelPrefix = "Остаток АО:")
            }

            // Subscriber Call Status Log (only for VISIT)
            if (isVisit) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (stop.called_at != null) StatusGreen.copy(alpha = 0.12f) else StatusWarningOrange.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (stop.called_at != null) Icons.Default.CheckCircle else Icons.Default.Phone,
                                contentDescription = null,
                                tint = if (stop.called_at != null) StatusGreen else StatusWarningOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (stop.called_at != null)
                                    "Абонент предупреждён (${YekaterinburgTime.formatTime(stop.called_at)})"
                                else
                                    "Абонент не предупреждён о визите",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (stop.called_at != null) StatusGreen else StatusWarningOrange
                            )
                        }

                        if (stop.customer_phone != null) {
                            Text(stop.customer_phone, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Navigation Intent Button
                Button(
                    onClick = { launchNavigationIntent(context, lat, lon, addressStr) },
                    colors = ButtonDefaults.buttonColors(containerColor = RostelecomPurple),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Навигация", fontSize = 11.sp, maxLines = 1, softWrap = false)
                }

                // Call Intent / Mark Call Button (only for VISIT)
                if (isVisit) {
                    OutlinedButton(
                        onClick = {
                            if (stop.customer_phone != null) {
                                try {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${stop.customer_phone}"))
                                    context.startActivity(dialIntent)
                                } catch (_: Exception) {}
                            }
                            onCallClick()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (stop.called_at != null) "Позвонить ✓" else "Звонок клиенту",
                            fontSize = 11.sp, maxLines = 1, softWrap = false
                        )
                    }
                }

                // Arrival / Complete buttons (only for VISIT)
                if (isVisit) {
                    if (stop.status == StopStatus.IN_PROGRESS) {
                        Button(
                            onClick = onCompleteClick,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Завершить", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                    } else if (stop.status != StopStatus.DONE) {
                        Button(
                            onClick = onArrivalClick,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Я на месте", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

private fun launchNavigationIntent(context: Context, lat: Double, lon: Double, addressLabel: String) {
    val geoUriStr = "geo:$lat,$lon?q=$lat,$lon(${Uri.encode(addressLabel)})"
    val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUriStr))
    try {
        context.startActivity(geoIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            val webUriStr = "https://yandex.ru/maps/?pt=$lon,$lat&z=16&l=map"
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUriStr))
            context.startActivity(webIntent)
        } catch (_: Exception) {}
    }
}
