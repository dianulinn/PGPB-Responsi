package com.example.spotlyapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.spotlyapp.databinding.ActivityMainBinding
import org.maplibre.android.MapLibre

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.mainContainer.id,
                    MapFragment.newInstance())
                .commit()
        }

        binding.btnOpenDataList.setOnClickListener {
            DataListActivity.start(this)
        }
    }
}