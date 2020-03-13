package com.mapbox.navigation.core.routerefresh

import android.util.Log
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.navigation.core.trip.session.TripSession
import com.mapbox.navigation.utils.timer.MapboxTimer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job

/**
 * This class is responsible for refreshing the current direction route's traffic at a
 * specified [intervalSeconds]. This does not support alternative routes.
 *
 * If the route is route is successfully refreshed, this class will update the [TripSession.route]
 *
 * [start] and [stop] are attached to the application lifecycle. Observing routes that
 * can be refreshed are handled by this class. Calling [start] will restart the refresh timer.
 */
internal class RouteRefreshController(
    private var accessToken: String,
    private val tripSession: TripSession
) {
    private val routerRefreshTimer = MapboxTimer()
    private val routeRefreshRetrofit = RouteRefreshRetrofit()
    private val routeRefreshApi = RouteRefreshApi(routeRefreshRetrofit)

    var intervalSeconds: Long = TimeUnit.MILLISECONDS.toSeconds(routerRefreshTimer.restartAfterMillis)
        set(value) {
            routerRefreshTimer.restartAfterMillis = TimeUnit.SECONDS.toMillis(value)
            field = value
        }

    fun start(): Job {
        stop()
        return routerRefreshTimer.startTimer {
            if (routeRefreshApi.supportsRefresh(tripSession.route)) {
                routeRefreshApi.refreshRoute(
                    accessToken,
                    tripSession.route,
                    tripSession.getRouteProgress(),
                    routeRefreshCallback)
            }
        }
    }

    fun stop() {
        routerRefreshTimer.stopJobs()
    }

    private val routeRefreshCallback = object : RouteRefreshCallback {

        override fun onRefresh(directionsRoute: DirectionsRoute) {
            Log.i("RouteRefresh", "Successful refresh")
            tripSession.route = directionsRoute
        }

        override fun onError(error: RouteRefreshError) {
            if (error.throwable != null) {
                Log.e("RouteRefresh", error.message, error.throwable)
            } else {
                Log.e("RouteRefresh", error.message)
            }
        }
    }
}
