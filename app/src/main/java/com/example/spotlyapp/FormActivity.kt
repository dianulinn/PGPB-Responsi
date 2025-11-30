package com.example.spotlyapp

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.spotlyapp.data.DataStore
import com.example.spotlyapp.databinding.ActivityFormBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FormActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ID = "sample_id"
        private const val EXTRA_NAME = "sample_name"

        fun start(context: Context, id: Long, name: String) {
            val intent = Intent(context, FormActivity::class.java).apply {
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_NAME, name)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityFormBinding

    private var sampleId = -1L
    private var sampleName = ""
    private var fotoUri: Uri? = null

    // ====== IZIN KAMERA ======
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera()
            else Toast.makeText(this, "Izin kamera ditolak!", Toast.LENGTH_SHORT).show()
        }

    // ====== LAUNCHER AMBIL FOTO ======
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                binding.imgPreviewFoto.setImageURI(fotoUri)
                Toast.makeText(this, "Foto berhasil diambil", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Gagal mengambil foto", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sampleId = intent.getLongExtra(EXTRA_ID, -1L)
        sampleName = intent.getStringExtra(EXTRA_NAME) ?: ""

        setupUi()
    }

    private fun setupUi() {
        binding.txtSampleName.text = "Titik: $sampleName"

        val penggunaanList = listOf(
            "Permukiman", "Jalan", "Sungai", "Perairan lain", "Sawah",
            "Perkebunan", "Hutan", "Perdagangan & Jasa", "Perkantoran",
            "Bangunan Non Permukiman Lain", "Lainnya"
        )
        val kesesuaianList = listOf("Sesuai", "Tidak Sesuai")

        binding.spinnerPenggunaan.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            penggunaanList
        )

        binding.spinnerKesesuaian.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            kesesuaianList
        )

        binding.btnAmbilFoto.setOnClickListener { checkCameraPermission() }
        binding.btnSimpan.setOnClickListener { saveForm() }
    }

    // ================== KAMERA ==================

    private fun checkCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) openCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        val uri = createImageUri()
        if (uri == null) {
            Toast.makeText(this, "Gagal menyiapkan media penyimpanan foto", Toast.LENGTH_SHORT)
                .show()
            return
        }

        fotoUri = uri
        takePictureLauncher.launch(uri)
    }

    /**
     * Bikin Uri di MediaStore (folder Pictures/SpotlyApp) supaya
     * hasil foto otomatis muncul di Galeri.
     */
    private fun createImageUri(): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SPOTLY_$timestamp.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SpotlyApp"
                )
            }
        }

        return contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
    }

    // ================== SIMPAN FORM ==================

    private fun saveForm() {
        val kode = binding.edtKodeSampel.text.toString().trim()
        val penggunaan = binding.spinnerPenggunaan.selectedItem.toString()
        val kesesuaian = binding.spinnerKesesuaian.selectedItem.toString()
        val catatan = binding.edtCatatan.text.toString().trim()

        if (kode.isEmpty()) {
            Toast.makeText(this, "Kode sampel wajib diisi!", Toast.LENGTH_SHORT).show()
            return
        }

        DataStore.addSurveyResponse(
            samplePointId = sampleId,
            kodeSampel = kode,
            penggunaanLahan = penggunaan,
            kesesuaian = kesesuaian,
            catatan = if (catatan.isEmpty()) null else catatan,
            // ini sekarang Uri ke MediaStore (bisa dibuka Galeri)
            fotoUri = fotoUri?.toString()
        )

        Toast.makeText(this, "Data tersimpan!", Toast.LENGTH_SHORT).show()
        finish()
    }
}