package com.udacity.project4.locationreminders.savereminder.selectreminderlocation

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.lang.Exception
import java.util.*

class SelectLocationFragment : BaseFragment() , OnMapReadyCallback {

    private var locationPermissionGranted = false

    //Use Koin to get the view model of the SaveReminder
    override val viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding

    private lateinit var mapFragment : SupportMapFragment
    private lateinit var map: GoogleMap

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var selectedPOI : PointOfInterest? = null
    private var selectedLatLong : LatLng? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_select_location, container, false)

        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        checkLocationPermission()

        binding.buttonSave.setOnClickListener {
            if (selectedLatLong == null) {
                viewModel.showErrorMessage.postValue(getString(R.string.select_poi))
            } else {
                onLocationSelected()
            }
        }

        return binding.root
    }

    private fun onLocationSelected() {
        if (selectedPOI == null) {
            viewModel.reminderSelectedLocationStr.value = getString(R.string.dropped_pin)
        } else {
            viewModel.selectedPOI.value = selectedPOI
            viewModel.reminderSelectedLocationStr.value = selectedPOI?.name
        }
        viewModel.latitude.value = selectedLatLong?.latitude
        viewModel.longitude.value = selectedLatLong?.longitude
        viewModel.navigationCommand.postValue(NavigationCommand.Back)
    }

    private fun setMapStyle(map: GoogleMap) {
        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    context,
                    R.raw.map_style
                )
            )
            if (!success) {
                Timber.e("Style parsing failed.")
            }
        } catch (exc: Exception) {
            Timber.e("Exception getting the file")
        }
    }

    private fun setMapClick(map:GoogleMap) {
        map.setOnMapClickListener { latLng ->
            map.clear()
            selectedLatLong = latLng
            selectedPOI = null
            val snippet = String.format(
                Locale.getDefault(),
                getString(R.string.lat_long_snippet),
                latLng.latitude,
                latLng.longitude
            )
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.dropped_pin))
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
            )
        }
    }

    private fun setPoiClick(map: GoogleMap) {
        map.setOnPoiClickListener { poi ->
            map.clear()
            selectedPOI = poi
            selectedLatLong = poi.latLng
            val poiMarker = map.addMarker(
                MarkerOptions()
                    .position(poi.latLng)
                    .title(poi.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
            )
            poiMarker.showInfoWindow()
        }
    }

    @SuppressLint("MissingPermission")
    private fun zoomUser() {
        fusedLocationProviderClient
            .lastLocation.addOnSuccessListener { lastKnownLocation ->
                if (lastKnownLocation != null) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(lastKnownLocation.latitude,
                            lastKnownLocation.longitude), 20f))
                } else {
                    Timber.e("lastKnownLocation is null!")
                }
            }
    }

    private fun isPermissionGranted() : Boolean {
        return ActivityCompat.checkSelfPermission(requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkLocationPermission() {
        if (isPermissionGranted()) {
            locationPermissionGranted = true
            mapFragment = childFragmentManager.findFragmentById(R.id.gmap) as SupportMapFragment
            mapFragment.getMapAsync(this)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        setMapStyle(map)

        if (locationPermissionGranted) {
            setMapClick(map)
            setPoiClick(map)
            map.isMyLocationEnabled = true
            zoomUser()
            viewModel.showToast.postValue(getString(R.string.select_poi))
        }
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true
                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult?) {
                        super.onLocationResult(locationResult)
                    }
                }
                with(LocationRequest()) {
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                    interval = 0
                    fastestInterval = 0
                    fusedLocationProviderClient.requestLocationUpdates(this, locationCallback, Looper.myLooper())
                }
            }else {
                viewModel.showErrorMessage.postValue(getString(R.string.permission_denied_explanation))
            }
            mapFragment = childFragmentManager.findFragmentById(R.id.gmap) as SupportMapFragment
            mapFragment.getMapAsync(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object{
        private const val REQUEST_LOCATION_PERMISSION = 1
    }

}
