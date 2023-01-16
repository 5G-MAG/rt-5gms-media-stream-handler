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
    private val exoPlayerAdapter = ExoPlayerAdapter(mediaSessionHandlerAdapter);
    private lateinit var exoPlayerView: StyledPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        exoPlayerView = findViewById(R.id.idExoPlayerVIew)

        try {
            populateSpinner()
            mediaSessionHandlerAdapter.initialize(this)
            exoPlayerAdapter.initialize(exoPlayerView, this)
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
                    val parsed =
                        Json.parseToJsonElement(item.toString()).jsonObject.get("streamingAccess")
                    val mediaPlayerEntry = parsed?.jsonObject?.get("mediaPlayerEntry")
                    spinnerOptions.add(mediaPlayerEntry.toString())
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
        var url: String = parent?.getItemAtPosition(position) as String
        url = url.replace("\"", "");
        exoPlayerAdapter.attach(url as String)
        exoPlayerAdapter.preload()
        exoPlayerAdapter.play()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        TODO("Not yet implemented")
    }
}