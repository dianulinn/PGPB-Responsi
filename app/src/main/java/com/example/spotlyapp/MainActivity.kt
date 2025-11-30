package com.example.spotlyapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.spotlyapp.databinding.ActivityMainBinding
import org.maplibre.android.MapLibre   // <-- tambah ini

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // WAJIB: init MapLibre sebelum pakai MapView
        MapLibre.getInstance(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.mainContainer.id, MapFragment.newInstance())
                .commit()
        }
    }
}
