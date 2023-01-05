package com.example.a5gms_mediastreamhandler

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ui.StyledPlayerView



class MainActivity : AppCompatActivity() {

    var exoPlayerAdapter = ExoplayerAdapter();
    private lateinit var exoPlayerView : StyledPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        exoPlayerView = findViewById(R.id.idExoPlayerVIew)

        try {
            exoPlayerAdapter.initialize(exoPlayerView, this)
            exoPlayerAdapter.attach("https://dash.akamaized.net/envivio/EnvivioDash3/manifest.mpd")
            exoPlayerAdapter.preload()
            exoPlayerAdapter.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}