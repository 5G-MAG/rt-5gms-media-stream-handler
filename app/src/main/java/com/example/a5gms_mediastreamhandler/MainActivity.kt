package com.example.a5gms_mediastreamhandler

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var exoPlayerView: StyledPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        exoPlayerView = findViewById(R.id.idExoPlayerVIew)

        try {
            populateSpinner()
            mediaSessionHandlerAdapter.initialize(this, exoPlayerAdapter)
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

    private fun populateSpinner() {
        val json: String?
        val spinner: Spinner = findViewById(R.id.idSaiSpinner)
        val spinnerOptions: ArrayList<String> = ArrayList()
        try {
            val inputStream: InputStream = assets.open(SERVICE_ACCESS_INFORMATION_INDEX)
            json = inputStream.bufferedReader().use { it.readText() }
            val entries = Json.parseToJsonElement(json).jsonObject.get("entries")?.jsonArray
            if (entries != null) {
                for (item in entries) {
                    val provisioningSessionId =
                        Json.parseToJsonElement(item.toString()).jsonObject["provisioningSessionId"]
                    spinnerOptions.add(provisioningSessionId.toString())
                }
            }


        } catch (e: Exception) {
            e.printStackTrace()
        }


        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item, spinnerOptions
        )
        spinner.adapter = adapter
        spinner.onItemSelectedListener = this

    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        var provisioningSessionId: String = parent?.getItemAtPosition(position) as String
        provisioningSessionId = provisioningSessionId.replace("\"", "");
        mediaSessionHandlerAdapter.initializePlaybackByProvisioningSessionId(provisioningSessionId)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        TODO("Not yet implemented")
    }
}