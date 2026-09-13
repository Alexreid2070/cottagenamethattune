package com.android.isrbet.cottagenamethattune

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import timber.log.Timber
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int
)

@Serializable
data class SpotifyTrackSearchResponse(
    val tracks: SpotifyTracksPaging
)

data class SpotifySearchResponse(
    val artists: SpotifyTracksPaging
)

@Serializable
data class SpotifyArtistsPaging (
    val items: List<SpotifyArtist>

)

@Serializable
data class SpotifyArtist(
    val id: String? = null,
    val name: String? = null
)

@Serializable
data class SpotifyTracksPaging(
    val items: List<SpotifyTrack>
)

@Serializable
data class SpotifyTrack(
    val id: String,
    val name: String,
    val uri: String,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("external_urls") val externalUrls: Map<String, String>,
    val album: SpotifyAlbum,
    val artists: List<SpotifyArtist>
)

@Serializable
data class SpotifyAlbum(
    val id: String,
    val name: String,
    @SerialName("release_date") val releaseDate: String,
    val images: List<SpotifyImage>
)

@Serializable
data class SpotifyImage(
    val url: String,
    val height: Int?,
    val width: Int?
)

enum class PlayingState {
    PAUSED, PLAYING, STOPPED
}
object SpotifyService {
    private const val CLIENT_ID = "0e89c49178bf4deeaaaeb11e78e4eb87"
    private const val REDIRECT_URI = "https://com.android.isrbet.cottagenamethattune/callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var currentURI = ""
    private var connectionParams: ConnectionParams = ConnectionParams.Builder(CLIENT_ID)
        .setRedirectUri(REDIRECT_URI)
        .showAuthView(true)
        .build()

    fun isConnected() : Boolean {
        return if (spotifyAppRemote != null)
            spotifyAppRemote!!.isConnected
        else
            false
    }

    fun connect(context: Context, handler: (connected: Boolean) -> Unit) {
        if (spotifyAppRemote?.isConnected == true) {
            handler(true)
            return
        }
        val connectionListener = object : Connector.ConnectionListener {
            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                Timber.tag("Alex").d("Reset spotifyAppRemote to $spotifyAppRemote")
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
    fun playSeek(uri: String, iSeek: Long) { // this one isn't working, use playAndSeek
        Timber.tag("Alex").d("playSeek")
        spotifyAppRemote?.playerApi?.play(uri)?.setResultCallback {
            Timber.tag("Alex").d("pausing")
            spotifyAppRemote?.playerApi?.pause()?.setResultCallback {
                Timber.tag("Alex").d("seeking")
//                spotifyAppRemote?.playerApi?.seekTo(iSeek*1000)
                spotifyAppRemote?.let { remote ->
                    remote.playerApi
                        .seekTo(iSeek*1000) // Seeks to the specific time in milliseconds
                        .setResultCallback {
                            Timber.tag("Alex").d("Successfully seeked to $iSeek s")
                        }
                        .setErrorCallback { throwable ->
                            Timber.tag("Alex").d("Failed to seek: ${throwable.message}")
                        }
                } ?: Timber.tag("Alex").d("Spotify App Remote is not connected.")
                Timber.tag("Alex").d("resuming")
                spotifyAppRemote?.playerApi?.resume()
            }
        }
    }

    fun playAndSeek(uri: String, iSeek: Long) {
        var seekPaging = 30L
        var playerStateSubscription: Subscription<PlayerState>? = null

        CoroutineScope(Dispatchers.Main).launch {
            spotifyAppRemote?.playerApi?.play(uri)
            delay(500) // Give the track half a second to buffer and start playing
            playerStateSubscription = spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
                playerStateSubscription?.cancel()
                playerStateSubscription = null

                val track = playerState.track
                if (track != null) {
                    // track.duration gives the song length in milliseconds
                    val songLengthMs = track.duration
                    val songLengthSec = songLengthMs / 1000

                    Timber.tag("Alex").d("Song: ${track.name}, Length: $songLengthSec seconds")
                    val seekRange = 0..(songLengthSec-gTimeToPlay)
                    seekPaging = seekRange.random()
                    Timber.tag("Alex").d("Starting song at $seekPaging")
                } else {
                    Timber.tag("Alex").d("got null")
                }
                spotifyAppRemote?.playerApi?.seekTo(seekPaging*1000)
            }
        }
    }

    fun play(uri: String) {
        currentURI = uri
        spotifyAppRemote?.playerApi?.play(uri)
    }

    suspend fun playAndThenDo(song: SongToUpload, uri: String, context: Context) {
        lateinit var mytrack: Track
        Timber.tag("Alex").d( "now playing ${song.songName}")
        spotifyAppRemote?.playerApi?.play(uri)?.setResultCallback {
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000L)
                getCurrentTrack(context) { track ->
                    Timber.tag("Alex").d("Got ${track.name} ")
                    mytrack = track
                }
            }
        }
        delay(5000L)
//        Timber.tag("Alex").d("setting image for ${mytrack.name}")
        val myTrack = MyTrack(
            song.songName,
            song.artistName,
            uri,
            mytrack.imageUri.raw.toString(),
            song.releaseYear,
            mutableListOf(),
            mutableListOf()
        )

        TrackViewModel.addTrack(myTrack)
    }

    fun returnImageURI(context: Context) : String? {
        var toReturn: String? = ""
        getCurrentTrack(context) { track ->
            Timber.tag("Alex").d("track is $track")
            toReturn = track.imageUri.raw
            Timber.tag("Alex").d("in sub imageuri is '${track.imageUri}'")
            Timber.tag("Alex").d("in sub imageuri is '${track.imageUri.raw}'")
        }
        Timber.tag("Alex").d("returning '$toReturn'")
        return toReturn
    }

    fun replay() {
        spotifyAppRemote?.playerApi?.play(currentURI)
    }

    fun resume() {
//        spotifyAppRemote?.playerApi?.seekTo(30000)
        spotifyAppRemote?.playerApi?.resume()
    }

    fun pause() {
        spotifyAppRemote?.playerApi?.pause()
    }

    fun getPlayingState(context: Context, handler: (PlayingState) -> Unit) {
        Timber.tag("Alex").d("spotifyAppRemote is $spotifyAppRemote")
        if (!isConnected()) {
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
/*    fun search() {
        val results = spotifyAppRemote?.userApi?.("Song Title", listOf(SearchType.TRACK))
            .await() // or synchronous execution depending on configuration

        for (track in results.tracks?.items.orEmpty()) {
            println("${track.name} by ${track.artists.firstOrNull()?.name}")
        }
    } */

    suspend fun getSpotifyAccessToken(): String {
        val client = HttpClient(CIO)
        val credentials = "$CLIENT_ID:4bc4bfd34be24d5c92ff909470887bd5"
        val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())

        val response: HttpResponse = client.post("https://accounts.spotify.com/api/token") {
            header(HttpHeaders.Authorization, "Basic $encodedCredentials")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("grant_type=client_credentials")
        }

        val responseBody = response.bodyAsText()
        Timber.tag("Alex").d("ResponseBody is '$responseBody'")
        val tokenResponse = Json { ignoreUnknownKeys = true }.decodeFromString<SpotifyTokenResponse>(responseBody)

        client.close()
        return tokenResponse.accessToken
    }

    suspend fun getArtistID(accessToken: String, searchQuery: String) : String { // e.g. "artist:Eagles"
        var artistID = ""

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                    register(ContentType.Text.Html, KotlinxSerializationConverter(Json))
                })
            }
        }
        val response: SpotifySearchResponse = client.get("https://api.spotify.com/v1/search") {
            parameter("q", "artist:$searchQuery")
            parameter("type", "artist")
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()

//        Timber.tag("Alex").d("Response $response")

        if (response.artists.items.isNotEmpty()) {
            artistID = response.artists.items[0].id
        }
        return artistID
    }
    suspend fun getTrackURI(accessToken: String, artistName: String, songName: String, iReleaseYear: Int) : String { // e.g. "artist:Eagles"
        var songURI = ""

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                    register(ContentType.Text.Html, KotlinxSerializationConverter(Json))
                })
            }
        }
        val rawQuery = "track:$songName artist:$artistName"
        val response: SpotifyTrackSearchResponse = client.get("https://api.spotify.com/v1/search") {
            parameter("q", rawQuery)
            parameter("type", "track")
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()

//        Timber.tag("Alex").d("Response $response")

        if (response.tracks.items.isNotEmpty()) {
/*            Timber.tag("Alex").d("there are ${response.tracks.items.size} options for $artistName - $songName")
            for (track in response.tracks.items) {
                Timber.tag("Alex").d("track ${track.name} ${track.album.name} ${track.album.releaseDate}")
            }*/
            val newList = response.tracks.items.sortedBy { it.album.releaseDate }
/*            for (track in newList) {
                Timber.tag("Alex").d("track ${track.name} ${track.album.name} ${track.album.releaseDate}")
            } */
            songURI = newList[0].uri
            Timber.tag("Alex").d("\nAlbum release date ${newList[0].album.releaseDate}, wanted $iReleaseYear")
        }
        return songURI
    }

    fun disconnect() {
        SpotifyAppRemote.disconnect(spotifyAppRemote)
    }
}