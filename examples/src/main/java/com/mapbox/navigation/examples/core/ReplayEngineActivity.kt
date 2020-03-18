package com.mapbox.navigation.examples.core

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.navigation.base.extensions.applyDefaultParams
import com.mapbox.navigation.base.extensions.coordinates
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.location.ReplayRouteLocationEngine
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.examples.R
import com.mapbox.navigation.examples.utils.Utils
import com.mapbox.navigation.examples.utils.extensions.toPoint
import com.mapbox.navigation.ui.camera.NavigationCamera
import com.mapbox.navigation.ui.map.NavigationMapboxMap
import com.mapbox.navigation.ui.map.NavigationMapboxMapInstanceState
import kotlinx.android.synthetic.main.activity_trip_service.mapView
import kotlinx.android.synthetic.main.replay_engine_example_activity_layout.*
import timber.log.Timber

class ReplayEngineActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener {

    companion object {
        const val DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L
        const val DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5
    }

    private var permissionsManager: PermissionsManager? = null
    private var locationEngine: LocationEngine? = null
    private var mapboxNavigation: MapboxNavigation? = null
    private var navigationMapboxMap: NavigationMapboxMap? = null
    private var mapInstanceState: NavigationMapboxMapInstanceState? = null
    private val replayRouteLocationEngine = ReplayRouteLocationEngine()
    private var locationComponent: LocationComponent? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.replay_engine_example_activity_layout)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        val mapboxNavigationOptions = MapboxNavigation.defaultNavigationOptions(
                this,
                Utils.getMapboxAccessToken(this)
        )

        mapboxNavigation = MapboxNavigation(
                applicationContext,
                Utils.getMapboxAccessToken(this),
                mapboxNavigationOptions,
                locationEngine = replayRouteLocationEngine
        )

        initListeners()
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        mapboxMap.setStyle(Style.MAPBOX_STREETS) { style ->
            locationEngine = LocationEngineProvider.getBestLocationEngine(this)
            initLocationComponent(style, mapboxMap)
            navigationMapboxMap = NavigationMapboxMap(mapView, mapboxMap).also {
                it.addProgressChangeListener(mapboxNavigation!!)
                mapInstanceState?.let { state ->
                    it.restoreFrom(state)
                }
            }
        }
        mapboxMap.addOnMapLongClickListener { latLng ->
            mapboxMap.locationComponent.lastKnownLocation?.let { originLocation ->
                mapboxNavigation?.requestRoutes(
                        RouteOptions.builder().applyDefaultParams()
                                .accessToken(Utils.getMapboxAccessToken(applicationContext))
                                .coordinates(originLocation.toPoint(), null, latLng.toPoint())
                                .alternatives(true)
                                .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
                                .build(),
                        routesReqCallback
                )
            }
            true
        }
        locationComponent = mapboxMap.locationComponent
    }

    @SuppressLint("RestrictedApi")
    fun initLocationComponent(loadedMapStyle: Style, mapboxMap: MapboxMap) {
        when (PermissionsManager.areLocationPermissionsGranted(this)) {
            true -> {
                mapboxMap.moveCamera(CameraUpdateFactory.zoomTo(15.0))
                mapboxMap.locationComponent.let { locationComponent ->
                    val locationComponentActivationOptions =
                            LocationComponentActivationOptions.builder(this, loadedMapStyle)
                                    // .useDefaultLocationEngine(true)
                                    .build()

                    locationComponent.activateLocationComponent(locationComponentActivationOptions)
                    locationComponent.isLocationComponentEnabled = true
                    locationComponent.cameraMode = CameraMode.TRACKING
                    locationComponent.renderMode = RenderMode.COMPASS

                    initLocationEngine()
                }
            }
            false -> {
                permissionsManager =
                        PermissionsManager(this).also { it.requestLocationPermissions(this) }
            }
        }
    }

    fun initLocationEngine() {
        val requestLocationUpdateRequest =
                LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                        .setPriority(LocationEngineRequest.PRIORITY_NO_POWER)
                        .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME)
                        .build()

        locationEngine?.requestLocationUpdates(
                requestLocationUpdateRequest,
                locationListenerCallback,
                mainLooper
        )
        locationEngine?.getLastLocation(locationListenerCallback)
    }

    private val routesReqCallback = object : RoutesRequestCallback {
        override fun onRoutesReady(routes: List<DirectionsRoute>) {
            Timber.d("route request success %s", routes.toString())
            if (routes.isNotEmpty()) {
                navigationMapboxMap?.drawRoute(routes[0])
                replayRouteLocationEngine.assign(routes[0])
                startNavigation.visibility = View.VISIBLE
            } else {
                startNavigation.visibility = View.GONE
            }
        }

        override fun onRoutesRequestFailure(throwable: Throwable, routeOptions: RouteOptions) {
            Timber.e("route request failure %s", throwable.toString())
        }

        override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
            Timber.d("route request canceled")
        }
    }

    @SuppressLint("MissingPermission")
    fun initListeners() {
        startNavigation.setOnClickListener {
            if (mapboxNavigation?.getRoutes()?.isNotEmpty() == true) {
                navigationMapboxMap?.updateLocationLayerRenderMode(RenderMode.GPS)
                navigationMapboxMap?.updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_GPS)
                navigationMapboxMap?.startCamera(mapboxNavigation?.getRoutes()!![0])
            }
            mapboxNavigation?.startTripSession()
            startNavigation.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        mapboxNavigation?.registerLocationObserver(locationObserver)
        Snackbar.make(container, R.string.msg_long_press_for_destination, Snackbar.LENGTH_SHORT).show()
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapboxNavigation?.unregisterLocationObserver(locationObserver)
        stopLocationUpdates()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapboxNavigation?.stopTripSession()
        mapboxNavigation?.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onPermissionResult(granted: Boolean) {
        when (granted) {
            true -> {
                // todo
            }
            false -> {
                // todo
            }
        }
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Toast.makeText(this, "todo needs doing", Toast.LENGTH_LONG).show()
    }

    private val locationListenerCallback: LocationEngineCallback<LocationEngineResult> =
            object : LocationEngineCallback<LocationEngineResult> {
                override fun onSuccess(result: LocationEngineResult) {
                    // todo
                }

                override fun onFailure(exception: Exception) {
                    Timber.i(exception)
                }
            }

    private fun stopLocationUpdates() {
        locationEngine?.removeLocationUpdates(locationListenerCallback)
    }

    private val locationObserver = object : LocationObserver {
        override fun onRawLocationChanged(rawLocation: Location) {
            Timber.d("raw location %s", rawLocation.toString())
        }

        override fun onEnhancedLocationChanged(
            enhancedLocation: Location,
            keyPoints: List<Location>
        ) {
            if (keyPoints.isNotEmpty()) {
                locationComponent?.forceLocationUpdate(keyPoints, true)
            } else {
                locationComponent?.forceLocationUpdate(enhancedLocation)
            }
        }
    }
}
