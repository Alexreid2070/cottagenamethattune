package com.android.isrbet.cottagenamethattune

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.Track

enum class PlayingState {
    PAUSED, PLAYING, STOPPED
}
object SpotifyService {
    private const val CLIENT_ID = "0e89c49178bf4deeaaaeb11e78e4eb87"
    private const val REDIRECT_URI = "http://com.android.isrbet.cottagenamethattune/callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var currentURI = ""
    private var connectionParams: ConnectionParams = ConnectionParams.Builder(CLIENT_ID)
        .setRedirectUri(REDIRECT_URI)
        .showAuthView(true)
        .build()
    fun connect(context: Context, handler: (connected: Boolean) -> Unit) {
        if (spotifyAppRemote?.isConnected == true) {
            handler(true)
            return
        }
        val connectionListener = object : Connector.ConnectionListener {
            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                this@SpotifyService.spotifyAppRemote = spotifyAppRemote
                handler(true)
            }
            override fun onFailure(throwable: Throwable) {
                Log.e("SpotifyService", throwable.message, throwable)
                handler(false)
            }
        }
        SpotifyAppRemote.connect(context, connectionParams, connectionListener)
    }
    fun amPlayingThis(uri: String) : Boolean {
        return uri == currentURI
    }
    fun play(uri: String) {
        currentURI = uri
        spotifyAppRemote?.playerApi?.play(uri)
    }

    fun replay() {
        spotifyAppRemote?.playerApi?.play(currentURI)
    }

    fun resume() {
        spotifyAppRemote?.playerApi?.resume()
    }

    fun pause() {
        spotifyAppRemote?.playerApi?.pause()
    }

    fun getPlayingState(context: Context, handler: (PlayingState) -> Unit) {
        if (!spotifyAppRemote?.isConnected!!) {
            connect(context) { result ->
                subGetPlayingState(handler)
            }
        } else {
            subGetPlayingState(handler)
        }
    }
    private fun subGetPlayingState(handler: (PlayingState) -> Unit) {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { result ->
            if (result.track.uri == null) {
                handler(PlayingState.STOPPED)
            } else if (result.isPaused) {
                handler(PlayingState.PAUSED)
            } else {
                handler(PlayingState.PLAYING)
            }
        }
    }
    fun getCurrentTrack(context: Context, handler: (track: Track) -> Unit) {
        if (!spotifyAppRemote?.isConnected!!) { // need this check, because for some reason the launch of the browser (search) disconnects from Spotify
            connect(context) { result ->
                subGetCurrentTrack(handler)
            }
        } else {
            subGetCurrentTrack(handler)
        }
    }
    private fun subGetCurrentTrack(handler: (Track) -> Unit) {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { result ->
            handler(result.track)
        }
    }

    fun getImage(context: Context, imageUri: ImageUri, handler: (Bitmap) -> Unit) {
        if (!spotifyAppRemote?.isConnected!!) {
            connect(context) { result ->
                subGetImage(imageUri, handler)
            }
        } else {
            subGetImage(imageUri, handler)
        }
    }
    private fun subGetImage(imageUri: ImageUri, handler: (Bitmap) -> Unit) {
        spotifyAppRemote?.imagesApi?.getImage(imageUri)?.setResultCallback {
            handler(it)
        }
    }
    fun disconnect() {
        SpotifyAppRemote.disconnect(spotifyAppRemote)
    }
}