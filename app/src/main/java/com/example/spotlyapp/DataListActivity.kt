package com.example.spotlyapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spotlyapp.data.DataStore
import com.example.spotlyapp.databinding.ActivityDataListBinding

class DataListActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context,
                DataListActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityDataListBinding
    private lateinit var adapter: SurveyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDataListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecycler()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupRecycler() {
        adapter = SurveyAdapter(
            onEditClick = { item ->
                FormActivity.startForEdit(this, item.surveyId)
            },
            onDeleteClick = { item ->
                confirmDelete(item)
            }
        )

        binding.rvSurvey.layoutManager = LinearLayoutManager(this)
        binding.rvSurvey.adapter = adapter
    }

    private fun loadData() {
        val items = DataStore.surveyResponses.mapNotNull { res ->
            val sp = DataStore.samplePoints.find { it.id == res.samplePointId }
            if (sp == null) null else {
                SurveyItem(
                    surveyId = res.id,
                    samplePointId = sp.id,
                    pointName = sp.name,
                    kodeSampel = res.kodeSampel,
                    penggunaanLahan = res.penggunaanLahan,
                    kesesuaian = res.kesesuaian,
                    fotoUri = res.fotoUri
                )
            }
        }
        adapter.submitList(items)
    }

    private fun confirmDelete(item: SurveyItem) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Data")
            .setMessage(
                "Yakin mau hapus data kode \"${item.kodeSampel}\" di titik \"${item.pointName}\"?"
            )
            .setPositiveButton("Hapus") { _, _ ->
                val success = DataStore.deleteSurveyResponseById(item.surveyId)
                if (success) {
                    Toast.makeText(this, "Data dihapus",
                        Toast.LENGTH_SHORT).show()
                    loadData()
                } else {
                    Toast.makeText(this, "Gagal menghapus data",
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    data class SurveyItem(
        val surveyId: Long,
        val samplePointId: Long,
        val pointName: String,
        val kodeSampel: String,
        val penggunaanLahan: String,
        val kesesuaian: String,
        val fotoUri: String?
    )

    class SurveyAdapter(
        private val onEditClick: (SurveyItem) -> Unit,
        private val onDeleteClick: (SurveyItem) -> Unit
    ) : RecyclerView.Adapter<SurveyAdapter.SurveyViewHolder>() {

        private val items = mutableListOf<SurveyItem>()

        fun submitList(newItems: List<SurveyItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_survey_row, parent,
                    false)
            return SurveyViewHolder(view)
        }

        override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, onEditClick, onDeleteClick)
        }

        override fun getItemCount(): Int = items.size

        class SurveyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imgFotoIcon: ImageView = itemView.findViewById(R.id.imgFotoIcon)
            private val txtPointName: TextView = itemView.findViewById(R.id.txtPointName)
            private val txtKode: TextView = itemView.findViewById(R.id.txtKode)
            private val txtPenggunaan: TextView = itemView.findViewById(R.id.txtPenggunaan)
            private val txtKesesuaian: TextView = itemView.findViewById(R.id.txtKesesuaian)
            private val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
            private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

            fun bind(
                item: SurveyItem,
                onEditClick: (SurveyItem) -> Unit,
                onDeleteClick: (SurveyItem) -> Unit
            ) {
                txtPointName.text = item.pointName
                txtKode.text = "Kode: ${item.kodeSampel}"
                txtPenggunaan.text = "Penggunaan: ${item.penggunaanLahan}"
                txtKesesuaian.text = "Kesesuaian: ${item.kesesuaian}"

                if (item.fotoUri.isNullOrEmpty()) {
                    imgFotoIcon.alpha = 0.3f
                } else {
                    imgFotoIcon.alpha = 1.0f
                    imgFotoIcon.setOnClickListener {
                        val ctx = it.context
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(item.fotoUri),
                                    "image/*")
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            ctx.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(ctx, "Tidak bisa membuka foto",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                btnEdit.setOnClickListener { onEditClick(item) }
                btnDelete.setOnClickListener { onDeleteClick(item) }
            }
        }
    }
}