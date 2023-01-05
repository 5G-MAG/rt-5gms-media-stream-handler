package com.example.a5gms_mediastreamhandler

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.InputStream


class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private val exoPlayerAdapter = ExoPlayerAdapter();
    private lateinit var exoPlayerView: StyledPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        exoPlayerView = findViewById(R.id.idExoPlayerVIew)

        try {
            populateSpinner()
            exoPlayerAdapter.initialize(exoPlayerView, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun populateSpinner() {
        val serviceAccessInformation = arrayOf("live-1.json", "live-2.json", "vod-1.json")
        var json: String?
        val spinner : Spinner = findViewById(R.id.idSaiSpinner)
        val spinnerOptions: ArrayList<String> = ArrayList()
        for (entry in serviceAccessInformation) {
            try {
                val inputStream: InputStream = assets.open("serviceAccessInformation/$entry")
                json = inputStream.bufferedReader().use { it.readText() }
                val parsed  = Json.parseToJsonElement(json).jsonObject.get("streamingAccess")
                val mediaPlayerEntry = parsed?.jsonObject?.get("mediaPlayerEntry")
                spinnerOptions.add(mediaPlayerEntry.toString())
            }
            catch(e: Exception) {
                e.printStackTrace()
            }
        }

        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item, spinnerOptions
        )
        spinner.adapter = adapter
        spinner.onItemSelectedListener = this

    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        var url : String = parent?.getItemAtPosition(position) as String
        url = url.replace("\"", "");
        exoPlayerAdapter.attach(url as String)
        exoPlayerAdapter.preload()
        exoPlayerAdapter.play()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        TODO("Not yet implemented")
    }
}