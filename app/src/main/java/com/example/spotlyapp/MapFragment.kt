package com.example.spotlyapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.spotlyapp.data.DataStore
import com.example.spotlyapp.data.model.SamplePoint
import com.example.spotlyapp.databinding.FragmentMapBinding
import org.json.JSONObject
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.ImageSource
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MapFragment : Fragment() {

    companion object {
        fun newInstance() = MapFragment()
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapView: MapView
    private var mapLibre: MapLibreMap? = null
    private var mapStyle: Style? = null

    private val pdfSourceId = "pdf-image-source"
    private val pdfLayerId = "pdf-image-layer"

    private var selectedPoint: SamplePoint? = null
    private var currentPdfUri: String? = null

    // ===== lokasi & navigasi dalam aplikasi =====
    private var locationManager: LocationManager? = null
    private var userLocation: LatLng? = null
    private var userMarker: Marker? = null
    private var navLine: Polyline? = null

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            val granted = (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                    (perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
            if (granted) {
                startLocationUpdates()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Izin lokasi ditolak, navigasi dalam aplikasi tidak bisa dipakai",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val latLng = LatLng(location.latitude, location.longitude)
            userLocation = latLng

            val map = mapLibre ?: return

            // hapus marker lama, buat baru
            userMarker?.let { old ->
                map.removeAnnotation(old)
            }
            userMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Posisi saya")
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    // ================== FILE PICKER ==================

    // pilih PDF / KML
    private val pickData = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleDataSelected(it) }
    }

    // buat CSV
    private val createCsv = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { exportCsv(it) }
    }

    // ================== LIFECYCLE FRAGMENT ==================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // tombol tambah data
        binding.btnAddData.setOnClickListener {
            pickData.launch(
                arrayOf(
                    "application/pdf",
                    "application/vnd.google-earth.kml+xml",
                    "application/xml",
                    "text/xml"
                )
            )
        }

        // export CSV
        binding.btnExportCsv.setOnClickListener {
            createCsv.launch("survey_kesesuaian.csv")
        }

        // tombol Lokasi → zoom ke posisi user
        binding.btnMyLocation.setOnClickListener {
            val loc = userLocation
            val map = mapLibre
            if (loc == null || map == null) {
                Toast.makeText(
                    requireContext(),
                    "Lokasi belum terbaca, tunggu GPS",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(loc, 18.0)
                )
            }
        }

        // setup map
        mapView.getMapAsync { map ->
            mapLibre = map

            map.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                mapStyle = style
                loadExistingPoints()
                ensureLocationPermission()
            }

            // long click → tambah titik manual
            map.addOnMapLongClickListener { latLng: LatLng ->
                addSamplePointAt(latLng)
                true
            }

            // klik marker → pilih titik
            map.setOnMarkerClickListener { marker ->
                handleMarkerClick(marker)
                true
            }
        }

        // buka form kesesuaian → SEKARANG buka Activity
        binding.btnOpenForm.setOnClickListener {
            val point = selectedPoint ?: return@setOnClickListener

            FormActivity.start(
                requireContext(),
                point.id,
                point.name
            )
        }

        // navigasi dalam app → rute jaringan jalan (OSRM)
        binding.btnNavigate.setOnClickListener {
            val point = selectedPoint
            if (point == null) {
                Toast.makeText(
                    requireContext(),
                    "Pilih titik sampel dulu",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            navigateInsideApp(point)
        }
    }

    // ================== HANDLE DATA (PDF / KML) ==================

    private fun handleDataSelected(uri: Uri) {
        val displayName = queryFileName(uri) ?: ""
        val rawName = uri.lastPathSegment ?: ""
        val name = if (displayName.isNotEmpty()) displayName else rawName
        val lower = name.lowercase()

        when {
            lower.endsWith(".pdf") -> {
                handlePdfSelected(uri, name.ifEmpty { "Peta PDF" })
                return
            }
            lower.endsWith(".kml") -> {
                handleKmlSelected(uri, name.ifEmpty { "Layer KML" })
                return
            }
        }

        val mime = requireContext().contentResolver.getType(uri) ?: ""
        when {
            mime.contains("pdf") ->
                handlePdfSelected(uri, name.ifEmpty { "Peta PDF" })

            mime.contains("kml") || mime.contains("xml") ->
                handleKmlSelected(uri, name.ifEmpty { "Layer KML" })

            else -> Toast.makeText(
                requireContext(),
                "Format tidak didukung (hanya PDF / KML)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handlePdfSelected(uri: Uri, name: String) {
        currentPdfUri = uri.toString()

        val style = mapStyle ?: run {
            Toast.makeText(requireContext(), "Map belum siap", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. buka file
        val pfd: ParcelFileDescriptor = try {
            requireContext().contentResolver.openFileDescriptor(uri, "r")
                ?: run {
                    Toast.makeText(requireContext(), "Gagal membuka PDF", Toast.LENGTH_SHORT).show()
                    return
                }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. render halaman pertama
        val renderer = PdfRenderer(pfd)
        if (renderer.pageCount == 0) {
            renderer.close()
            pfd.close()
            Toast.makeText(requireContext(), "PDF kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val page = renderer.openPage(0)
        val width = page.width
        val height = page.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pfd.close()

        // 3. bounding box geospasial PDF
        val latNorth = -7.748432
        val latSouth = -7.829432
        val lonWest  = 110.370894
        val lonEast  = 110.475918

        val quad = LatLngQuad(
            LatLng(latNorth, lonWest),
            LatLng(latNorth, lonEast),
            LatLng(latSouth, lonEast),
            LatLng(latSouth, lonWest)
        )

        // 4. hapus layer/source lama
        style.getLayer(pdfLayerId)?.let { style.removeLayer(it) }
        style.getSource(pdfSourceId)?.let { style.removeSource(it) }

        // 5. tambah sumber & layer
        val imgSource = ImageSource(pdfSourceId, quad, bitmap)
        style.addSource(imgSource)

        val layer = RasterLayer(pdfLayerId, pdfSourceId)
        style.addLayer(layer)

        layer.setProperties(
            PropertyFactory.rasterOpacity(0.9f)
        )

        // 6. fokus kamera ke area PDF
        val bounds = LatLngBounds.Builder()
            .include(LatLng(latNorth, lonWest))
            .include(LatLng(latSouth, lonEast))
            .build()

        mapLibre?.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 50)
        )

        Toast.makeText(
            requireContext(),
            "Peta PDF \"$name\" ditampilkan",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleKmlSelected(uri: Uri, name: String) {
        val input = requireContext().contentResolver.openInputStream(uri)
        if (input == null) {
            Toast.makeText(requireContext(), "Gagal membuka KML", Toast.LENGTH_SHORT).show()
            return
        }

        val newPoints = parseKmlPoints(input)

        val map = mapLibre
        if (map == null) {
            Toast.makeText(requireContext(), "Map belum siap", Toast.LENGTH_SHORT).show()
            return
        }

        newPoints.forEach { p ->
            val stored = DataStore.addSamplePoint(
                name = p.name,
                lat = p.latitude,
                lon = p.longitude,
                pdfUri = currentPdfUri
            )

            map.addMarker(
                MarkerOptions()
                    .position(LatLng(stored.latitude, stored.longitude))
                    .title(stored.name)
                    .snippet("${stored.id}")
            )
        }

        Toast.makeText(
            requireContext(),
            "Layer titik KML ditambahkan: ${newPoints.size} titik",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun parseKmlPoints(input: InputStream): List<SamplePoint> {
        val result = mutableListOf<SamplePoint>()

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        var event = parser.eventType
        var inPlacemark = false
        var currentName: String? = null
        var currentCoords: String? = null
        var tempId = 1L

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "Placemark" -> {
                            inPlacemark = true
                            currentName = null
                            currentCoords = null
                        }
                        "name" -> if (inPlacemark) {
                            currentName = parser.nextText()
                        }
                        "coordinates" -> if (inPlacemark) {
                            currentCoords = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "Placemark" && inPlacemark) {
                        inPlacemark = false
                        val coords = currentCoords
                        if (!coords.isNullOrBlank()) {
                            val parts = coords.trim().split(",")
                            if (parts.size >= 2) {
                                val lon = parts[0].toDoubleOrNull()
                                val lat = parts[1].toDoubleOrNull()
                                if (lat != null && lon != null) {
                                    val nm = currentName ?: "Titik_$tempId"
                                    result.add(
                                        SamplePoint(
                                            id = tempId++,
                                            name = nm,
                                            latitude = lat,
                                            longitude = lon,
                                            pdfUri = null
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        return result
    }

    private fun queryFileName(uri: Uri): String? {
        val cr = requireContext().contentResolver
        val cursor = cr.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return null
    }

    // ================== TITIK & MARKER ==================

    private fun loadExistingPoints() {
        val map = mapLibre ?: return
        DataStore.samplePoints.forEach { p ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(p.latitude, p.longitude))
                    .title(p.name)
                    .snippet("${p.id}")
            )
        }
    }

    private fun addSamplePointAt(latLng: LatLng) {
        val name = "Titik_${System.currentTimeMillis() % 10000}"

        val point = DataStore.addSamplePoint(
            name = name,
            lat = latLng.latitude,
            lon = latLng.longitude,
            pdfUri = currentPdfUri
        )

        mapLibre?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(point.name)
                .snippet("${point.id}")
        )

        Toast.makeText(
            requireContext(),
            "Titik $name dibuat",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleMarkerClick(marker: Marker) {
        val id = marker.snippet?.toLongOrNull() ?: return
        val point = DataStore.samplePoints.find { it.id == id } ?: return
        onPointSelected(point)
    }

    private fun onPointSelected(p: SamplePoint) {
        selectedPoint = p
        binding.txtPointName.text = p.name
        binding.txtPointCoords.text = "Lat: ${p.latitude}, Lon: ${p.longitude}"
        binding.btnOpenForm.isEnabled = true
        binding.btnNavigate.isEnabled = true

        mapLibre?.animateCamera(
            CameraUpdateFactory.newLatLng(
                LatLng(p.latitude, p.longitude)
            )
        )
    }

    // ================== IZIN & UPDATE LOKASI ==================

    private fun ensureLocationPermission() {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fine == PackageManager.PERMISSION_GRANTED ||
            coarse == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationUpdates() {
        val ctx = requireContext()
        locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val hasFine = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return
        }

        val gpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val netEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

        if (!gpsEnabled && !netEnabled) {
            Toast.makeText(
                ctx,
                "Provider lokasi (GPS / Network) mati. Aktifkan Location di pengaturan.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            // pakai last known location dulu
            val lastGps = if (gpsEnabled)
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            else null

            val lastNet = if (netEnabled)
                locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            else null

            val bestLast = when {
                lastGps != null && lastNet != null ->
                    if (lastGps.time >= lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }

            bestLast?.let { loc ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                userLocation = latLng

                val map = mapLibre
                if (map != null) {
                    userMarker?.let { old -> map.removeAnnotation(old) }
                    userMarker = map.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Posisi saya")
                    )
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(latLng, 17.0)
                    )
                }
            }

            // update berkala
            if (gpsEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    1f,
                    locationListener
                )
            }
            if (netEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    1f,
                    locationListener
                )
            }

        } catch (_: SecurityException) {
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: SecurityException) {
        }
    }

    // ================== NAVIGASI DALAM APLIKASI (ROUTE JARINGAN JALAN) ==================

    private fun navigateInsideApp(target: SamplePoint) {
        val start = userLocation
        val map = mapLibre

        if (start == null || map == null) {
            Toast.makeText(
                requireContext(),
                "Lokasi kamu belum terbaca, tunggu sinyal GPS",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val end = LatLng(target.latitude, target.longitude)

        // hapus garis lama
        navLine?.let { old ->
            map.removeAnnotation(old)
        }

        val urlStr =
            "https://router.project-osrm.org/route/v1/driving/" +
                    "${start.longitude},${start.latitude};" +
                    "${end.longitude},${end.latitude}" +
                    "?overview=full&geometries=geojson"

        Thread {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    showToastOnUi("Gagal ambil rute (kode $code)")
                    return@Thread
                }

                val text = BufferedReader(
                    InputStreamReader(conn.inputStream)
                ).use { it.readText() }
                conn.disconnect()

                val json = JSONObject(text)
                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) {
                    showToastOnUi("Rute tidak ditemukan")
                    return@Thread
                }

                val geometry = routes
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONArray("coordinates")

                val routePoints = mutableListOf<LatLng>()
                for (i in 0 until geometry.length()) {
                    val coord = geometry.getJSONArray(i)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    routePoints.add(LatLng(lat, lon))
                }

                requireActivity().runOnUiThread {
                    navLine = map.addPolyline(
                        PolylineOptions()
                            .addAll(routePoints)
                            .color(Color.RED)
                            .width(4f)
                    )

                    val b = LatLngBounds.Builder()
                    routePoints.forEach { p -> b.include(p) }
                    val bounds = b.build()
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, 80)
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showToastOnUi("Error routing: ${e.message}")
            }
        }.start()
    }

    private fun showToastOnUi(msg: String) {
        try {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        } catch (_: IllegalStateException) {
            // fragment sudah tidak attached
        }
    }

    // ================== EXPORT CSV ==================

    private fun exportCsv(uri: Uri) {
        val resolver = requireContext().contentResolver
        resolver.openOutputStream(uri)?.use { os ->
            val writer = OutputStreamWriter(os)

            writer.appendLine("id;kode;p_lahan;kesesuaian;catatan;lat;lon;pdf_uri;foto_uri")

            DataStore.surveyResponses.forEach { res ->
                val sp = DataStore.samplePoints.find { it.id == res.samplePointId }

                val fotoName = getFileNameFromUri(res.fotoUri)

                writer.appendLine(
                    listOf(
                        res.id,
                        res.kodeSampel,
                        res.penggunaanLahan,
                        res.kesesuaian,
                        res.catatan ?: "",
                        sp?.latitude ?: "",
                        sp?.longitude ?: "",
                        sp?.pdfUri ?: "",
                        fotoName ?: ""
                    ).joinToString(";")
                )
            }

            writer.flush()
        }

        Toast.makeText(requireContext(), "CSV diekspor", Toast.LENGTH_SHORT).show()
    }

    private fun getFileNameFromUri(uriString: String?): String? {
        if (uriString == null) return null
        val uri = Uri.parse(uriString)
        return uri.lastPathSegment
    }

    // ================== LIFECYCLE MAPVIEW ==================

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
        _binding = null
    }
}