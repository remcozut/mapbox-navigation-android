package com.mapbox.navigation.metrics.extensions

import com.mapbox.android.telemetry.Event
import com.mapbox.navigation.base.internal.metrics.DirectionsMetrics
import com.mapbox.navigation.base.internal.metrics.NavigationMetrics
import com.mapbox.navigation.base.metrics.MetricEvent

fun MetricEvent.toTelemetryEvent(): Event? =
    when (metric) {
        DirectionsMetrics.ROUTE_RETRIEVAL,
        NavigationMetrics.ARRIVE,
        NavigationMetrics.CANCEL_SESSION,
        NavigationMetrics.DEPART,
        NavigationMetrics.REROUTE,
        NavigationMetrics.FEEDBACK,
        NavigationMetrics.INITIAL_GPS,
        NavigationMetrics.APP_USER_TURNSTILE -> this as Event
        else -> null
    }
