package com.example.a5gms_mediastreamhandler

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.example.a5gms_mediastreamhandler.models.M8Model
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.InputStream
import kotlin.collections.ArrayList

const val SERVICE_ACCESS_INFORMATION_INDEX = "serviceAccessInformation/index.json"

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private val mediaSessionHandlerAdapter = MediaSessionHandlerAdapter()
    private val exoPlayerAdapter = ExoPlayerAdapter();
    private val m8Data = mutableListOf<M8Model>()
    private lateinit var exoPlayerView: StyledPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        exoPlayerView = findViewById(R.id.idExoPlayerVIew)

        try {
            setM8Data()
            populateSpinner()
            mediaSessionHandlerAdapter.initialize(this, exoPlayerAdapter)
            updateMediaSessionHandlerLookupTable()
            exoPlayerAdapter.initialize(exoPlayerView, this, mediaSessionHandlerAdapter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from the service
        mediaSessionHandlerAdapter.reset(this)
    }

    private fun setM8Data() {
        val json: String?
        try {
            val inputStream: InputStream = assets.open(SERVICE_ACCESS_INFORMATION_INDEX)
            json = inputStream.bufferedReader().use { it.readText() }
            val entries = Json.parseToJsonElement(json).jsonObject.get("entries")?.jsonArray
            if (entries != null) {
                for (item in entries) {
                    var mediaPlayerEntry =
                        Json.parseToJsonElement(item.toString()).jsonObject["mediaPlayerEntry"].toString()
                    var provisioningSessionId =
                        Json.parseToJsonElement(item.toString()).jsonObject["provisioningSessionId"].toString()
                    mediaPlayerEntry = mediaPlayerEntry.replace("\"", "");
                    provisioningSessionId = provisioningSessionId.replace("\"", "");
                    val entry = M8Model(mediaPlayerEntry, provisioningSessionId)
                    m8Data.add(entry)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMediaSessionHandlerLookupTable() {
        mediaSessionHandlerAdapter.updateLookupTable(m8Data)
    }

    private fun populateSpinner() {
        try {
            val spinner: Spinner = findViewById(R.id.idSaiSpinner)
            val spinnerOptions: ArrayList<String> = ArrayList()

            val iterator = m8Data.iterator()
            while (iterator.hasNext()) {
                spinnerOptions.add(iterator.next().mediaPlayerEntry)
            }
            val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item, spinnerOptions
            )
            spinner.adapter = adapter
            spinner.onItemSelectedListener = this
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        var mediaPlayerEntry: String = parent?.getItemAtPosition(position) as String
        exoPlayerAdapter.stop()
        mediaSessionHandlerAdapter.initializePlaybackByMediaPlayerEntry(mediaPlayerEntry)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        TODO("Not yet implemented")
    }
}