package com.nepalindrom.fielddispatch.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nepalindrom.fielddispatch.data.model.Stop
import com.nepalindrom.fielddispatch.data.model.StopStatus
import com.nepalindrom.fielddispatch.data.model.StopType
import com.nepalindrom.fielddispatch.util.YekaterinburgTime

private const val TAG = "RouteMapWebView"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouteMapWebView(
    stops: List<Stop>,
    selectedStop: Stop?,
    onStopSelected: (Stop) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentStops by rememberUpdatedState(stops)
    val currentOnStopSelected by rememberUpdatedState(onStopSelected)

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 FieldDispatch/1.0"
            setBackgroundColor(android.graphics.Color.parseColor("#F4F5FB"))

            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    Log.e(TAG, "Map webview error: ${error?.description} on url ${request?.url}")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d(TAG, "JS: ${consoleMessage?.message()} [line ${consoleMessage?.lineNumber()}]")
                    return true
                }
            }

            addJavascriptInterface(object {
                @JavascriptInterface
                fun onMarkerClick(seq: Int) {
                    val found = currentStops.firstOrNull { it.seq == seq }
                    if (found != null) {
                        currentOnStopSelected(found)
                    }
                }
            }, "AndroidMapBridge")
        }
    }

    // Load or reload map when stops change
    LaunchedEffect(stops) {
        if (stops.isNotEmpty()) {
            val html = generateLeafletHtml(stops, selectedStop)
            webView.loadDataWithBaseURL(
                "https://tile.openstreetmap.org/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    // Center map on selected stop when changed dynamically
    LaunchedEffect(selectedStop) {
        if (selectedStop != null) {
            val lat = selectedStop.lat?.takeIf { it != 0.0 } ?: 56.8389
            val lon = selectedStop.lon?.takeIf { it != 0.0 } ?: 60.6057
            webView.evaluateJavascript(
                "if (window.centerOnStop) { window.centerOnStop($lat, $lon, ${selectedStop.seq}); }",
                null
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}

private fun generateLeafletHtml(stops: List<Stop>, selectedStop: Stop?): String {
    val stopsJson = stops.joinToString(",") { s ->
        val lat = s.lat?.takeIf { it != 0.0 } ?: 56.8389
        val lon = s.lon?.takeIf { it != 0.0 } ?: 60.6057
        val color = when (s.type) {
            StopType.DEPOT_START, StopType.RESTOCK, StopType.DEPOT_END -> "#6D14B9"
            StopType.BREAK -> "#6B7280"
            StopType.VISIT -> when (s.status) {
                StopStatus.DONE -> "#10B981"
                StopStatus.IN_PROGRESS -> "#F59E0B"
                else -> "#3B82F6"
            }
        }
        val label = escapeJs(s.address ?: s.location_id)
        val time = "${YekaterinburgTime.formatTime(s.start)} - ${YekaterinburgTime.formatTime(s.end)}"
        "{seq:${s.seq}, lat:$lat, lon:$lon, color:'$color', label:'$label', time:'$time', orderId:'${s.order_id ?: ""}'}"
    }

    val centerStop = selectedStop ?: stops.firstOrNull()
    val centerLat = centerStop?.lat?.takeIf { it != 0.0 } ?: 56.8389
    val centerLon = centerStop?.lon?.takeIf { it != 0.0 } ?: 60.6057
    val initialSeq = centerStop?.seq ?: 1

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
            if (typeof L === 'undefined') {
                document.write('<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css">');
                document.write('<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js"><\/script>');
            }
            </script>
            <style>
                * { box-sizing: border-box; }
                html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background: #F4F5FB; overflow: hidden; }
                .leaflet-popup-content-wrapper { background: #232542; color: #FFFFFF; font-family: sans-serif; font-size: 12px; border-radius: 8px; }
                .leaflet-popup-tip { background: #232542; }
                .stop-marker {
                    display: flex; align-items: center; justify-content: center;
                    color: white; font-weight: bold; font-size: 11px; font-family: sans-serif;
                    border-radius: 50%; border: 2px solid white; box-shadow: 0 2px 6px rgba(0,0,0,0.4);
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                function initLeafletMap() {
                    if (typeof L === 'undefined') {
                        setTimeout(initLeafletMap, 150);
                        return;
                    }

                    try {
                        var map = L.map('map', { zoomControl: false }).setView([$centerLat, $centerLon], 13);
                        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            maxZoom: 19,
                            attribution: '© OpenStreetMap'
                        }).addTo(map);

                        var stops = [$stopsJson];
                        var latLngs = [];
                        var markers = {};

                        stops.forEach(function(s) {
                            var latLng = [s.lat, s.lon];
                            latLngs.push(latLng);

                            var icon = L.divIcon({
                                className: 'custom-div-icon',
                                html: "<div class='stop-marker' style='background:" + s.color + "; width:24px; height:24px;'>" + s.seq + "</div>",
                                iconSize: [24, 24],
                                iconAnchor: [12, 12]
                            });

                            var popupHtml = "<b>#" + s.seq + " " + s.label + "</b><br>" + s.time + (s.orderId ? "<br>Наряд: " + s.orderId : "");
                            var m = L.marker(latLng, { icon: icon }).addTo(map).bindPopup(popupHtml);
                            
                            m.on('click', function() {
                                if (window.AndroidMapBridge && window.AndroidMapBridge.onMarkerClick) {
                                    window.AndroidMapBridge.onMarkerClick(s.seq);
                                }
                            });

                            markers[s.seq] = m;
                        });

                        if (latLngs.length > 1) {
                            var polyline = L.polyline(latLngs, { color: '#6D14B9', weight: 4, opacity: 0.8, dashArray: '6, 6' }).addTo(map);
                            map.fitBounds(polyline.getBounds(), { padding: [30, 30] });
                        } else if (latLngs.length === 1) {
                            map.setView(latLngs[0], 14);
                        }

                        if (markers[$initialSeq]) {
                            markers[$initialSeq].openPopup();
                        }

                        window.centerOnStop = function(lat, lon, seq) {
                            map.flyTo([lat, lon], 15, { duration: 0.8 });
                            if (markers[seq]) {
                                markers[seq].openPopup();
                            }
                        };

                        // Invalidate size to guarantee tiles render properly once layout is ready
                        setTimeout(function() { map.invalidateSize(); }, 200);
                        setTimeout(function() { map.invalidateSize(); }, 600);
                        setTimeout(function() { map.invalidateSize(); }, 1200);
                    } catch (e) {
                        console.error('Error initializing Leaflet map:', e);
                    }
                }

                if (document.readyState === 'complete' || document.readyState === 'interactive') {
                    initLeafletMap();
                } else {
                    document.addEventListener('DOMContentLoaded', initLeafletMap);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeJs(text: String): String {
    return text.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\r", " ")
        .replace("\n", " ")
}
