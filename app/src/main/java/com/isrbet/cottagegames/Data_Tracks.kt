package com.isrbet.cottagegames

import android.graphics.Color
import android.icu.text.Collator
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import timber.log.Timber
import java.util.Collections.reverseOrder
import java.util.Locale

enum class TracksFilter {
    All, NameThatTune, WhenWasThat
}

data class MyTrack(
    var songName: String,
    var artistName: String,
    var uri: String,
    var imageUri: String,
    var releaseYear: Int,
    var lyrics: MutableList<String>,
    val forbiddenWords: MutableList<String>
) {

    fun getKey(): String {
        return makeKeySafe("${songName.trim()} - ${artistName.trim()}")
    }

    fun contains(iSubString: String): Boolean {
        val lc = iSubString.lowercase()
        var contains = songName.lowercase().contains(lc) ||
                artistName.lowercase().contains(lc)
        if (!contains) {
            lyrics.forEach {
                contains = contains || it.lowercase().contains(lc)
            }
        }
        return contains
    }

    fun getSpannedLyrics(): SpannableString {
        var lyricsText = ""
        for (i in 0 until lyrics.size) {
            lyricsText = lyricsText + lyrics[i] + "\n"
        }
        val spannable = SpannableString(lyricsText)
        lyricsText = lyricsText.lowercase()
        val myForbiddenWords = forbiddenWords.toMutableList()
        myForbiddenWords.add(songName)
        myForbiddenWords.forEach { forbidden ->
            var index = 0
            while (index != -1) {
                index = lyricsText.indexOf(forbidden.lowercase(), index)
                index = if (index == -1) {
                    -1
                } else {
                    spannable.setSpan(
                        ForegroundColorSpan(Color.DKGRAY),
                        index,
                        index + forbidden.length,
                        0
                    )
                    spannable.setSpan(
                        BackgroundColorSpan(Color.RED),
                        index,
                        index + forbidden.length,
                        0
                    )
                    index + forbidden.length
                }
            }
        }
        return spannable
    }

    fun getForbiddenLyrics(): String {
        var tString = ""
        forbiddenWords.forEach {
            tString = tString + it + "\n"
        }
        return tString
    }
}

class TrackViewModel : ViewModel() {
    private var trackListener: ValueEventListener? = null
    private val tracks: MutableList<MyTrack> = ArrayList()
    private var sortOrder: SortOrder = SortOrder.BY_SONG_NAME
    private var currentlyAscending = true
    private var dataUpdatedCallback: DataUpdatedCallback? = null
    var loaded: Boolean = false
    var dataHasChanged: Boolean = false
    private var myFilter: TracksFilter = TracksFilter.All
    private var whenWasThatIndex = -1
    private var nameThatTuneIndex = -1

    companion object {
        lateinit var singleInstance: TrackViewModel // used to track static single instance of self

        fun getTracks(iSortOrder: SortOrder = getSortOrder()): MutableList<MyTrack> {
            if (getMyFilter() == TracksFilter.All) {
                sortList(iSortOrder, singleInstance.currentlyAscending, singleInstance.tracks)
                return singleInstance.tracks
            } else if (getMyFilter() == TracksFilter.NameThatTune) {
                val tTracks: MutableList<MyTrack> = ArrayList()
                var mIndex = 0

                while (mIndex < singleInstance.tracks.size) {
                    if (singleInstance.tracks[mIndex].lyrics.isNotEmpty())
                        tTracks.add(singleInstance.tracks[mIndex])
                    mIndex += 1
                }
                sortList(iSortOrder, singleInstance.currentlyAscending, tTracks)
                return tTracks
            } else {
                val tTracks: MutableList<MyTrack> = ArrayList()
                var mIndex = 0

                while (mIndex < singleInstance.tracks.size) {
                    if (singleInstance.tracks[mIndex].releaseYear > 0)
                        tTracks.add(singleInstance.tracks[mIndex])
                    mIndex += 1
                }
                sortList(iSortOrder, singleInstance.currentlyAscending, tTracks)
                return tTracks
            }
        }

        fun getMyFilter(): TracksFilter {
            return singleInstance.myFilter
        }

        fun setMyFilter(iFilter: TracksFilter) {
            singleInstance.myFilter = iFilter
        }

        fun getSortOrder(): SortOrder {
//            return singleInstance.viewSortOrder
            return singleInstance.sortOrder
        }

        fun toggleSortAscending() {
            singleInstance.currentlyAscending = !singleInstance.currentlyAscending
        }

        fun getDataHasChanged(): Boolean {
            return singleInstance.dataHasChanged
        }

        fun setDataHasChanged(iNewValue: Boolean) {
            singleInstance.dataHasChanged = iNewValue
        }

        fun isLoaded(): Boolean {
            return singleInstance.loaded
        }

        fun shuffleForNameThatTune() {
            singleInstance.nameThatTuneIndex = -1
            singleInstance.tracks.shuffle()
        }

        fun getNextTrackForNameThatTune(): MyTrack {
            singleInstance.nameThatTuneIndex += 1

            while (singleInstance.tracks[singleInstance.nameThatTuneIndex].lyrics.size <= 1) {
                Timber.tag("Alex").d("Ignoring ${singleInstance.tracks[singleInstance.nameThatTuneIndex].songName}")
                singleInstance.nameThatTuneIndex += 1
            }

            return singleInstance.tracks[singleInstance.nameThatTuneIndex]
        }

        fun getCount(): Int {
            return if (::singleInstance.isInitialized)
                singleInstance.tracks.size
            else
                0
        }

        fun getTrack(ind: Int): MyTrack? {
            return if (ind >= 0 && ind < singleInstance.tracks.size) {
                singleInstance.tracks[ind]
            } else {
                null
            }
        }

        fun getTrack(uri: String, songName: String = "", artistName: String = ""): MyTrack? {
            var myTrack = singleInstance.tracks.find { it.uri == uri }
            if (myTrack == null && (songName != "" && artistName != "")) {
                myTrack =
                    singleInstance.tracks.find { it.songName == songName && it.artistName == artistName }
            }
            return myTrack
        }

        fun getTrackInd(uri: String): Int {
            for (i in 0 until singleInstance.tracks.size) {
                if (singleInstance.tracks[i].uri == uri) {
                    return i
                }
            }
            return -1
        }

        fun addTrack(track: MyTrack): Boolean {
            // save backup song list
            val key = MyApplication.database.getReference("Songs").push().key.toString()
            MyApplication.database.getReference("Songs")
                .child(key)
                .child("songName")
                .setValue(track.songName.trim())
            MyApplication.database.getReference("Songs")
                .child(key)
                .child("artistName")
                .setValue(track.artistName.trim())

            singleInstance.tracks.add(track)
            MyApplication.database.getReference("Tracks")
                .child(track.getKey())
                .child("songName")
                .setValue(track.songName.trim())
            MyApplication.database.getReference("Tracks")
                .child(track.getKey())
                .child("artistName")
                .setValue(track.artistName.trim())
            MyApplication.database.getReference("Tracks")
                .child(track.getKey())
                .child("uri")
                .setValue(track.uri)
            MyApplication.database.getReference("Tracks")
                .child(track.getKey())
                .child("imageUri")
                .setValue(track.imageUri)
            MyApplication.database.getReference("Tracks")
                .child(track.getKey())
                .child("releaseYear")
                .setValue(track.releaseYear)
            MyApplication.database.getReference("Tracks")
                .child(track.getKey())
                .child("lyrics")
                .setValue(track.lyrics)
            MyApplication.database.getReference("Tracks")
                .child(track.getKey())
                .child("forbiddenWords")
                .setValue(track.forbiddenWords)
            return true
        }

        fun editTrack(oldTrack: MyTrack?, newTrack: MyTrack): Boolean {
            if (oldTrack?.uri != newTrack.uri) {
                Timber.tag("Alex").d("THESE AREN'T THE SAME SONG, ABORTING")
                Toast.makeText(
                    MyApplication.myMainActivity,
                    "Editing, but NOT the same song!!!!",
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
            if (oldTrack.getKey() == newTrack.getKey()) {
                MyApplication.database.getReference("Tracks")
                    .child(newTrack.getKey())
                    .child("songName")
                    .setValue(newTrack.songName)
                MyApplication.database.getReference("Tracks")
                    .child(newTrack.getKey())
                    .child("artistName")
                    .setValue(newTrack.artistName)
                MyApplication.database.getReference("Tracks")
                    .child(newTrack.getKey())
                    .child("uri")
                    .setValue(newTrack.uri)
                MyApplication.database.getReference("Tracks")
                    .child(newTrack.getKey())
                    .child("imageUri")
                    .setValue(newTrack.imageUri)
                MyApplication.database.getReference("Tracks")
                    .child(newTrack.getKey())
                    .child("releaseYear")
                    .setValue(newTrack.releaseYear)
                MyApplication.database.getReference("Tracks")
                    .child(newTrack.getKey())
                    .child("lyrics")
                    .setValue(newTrack.lyrics)
                MyApplication.database.getReference("Tracks")
                    .child(newTrack.getKey())
                    .child("forbiddenWords")
                    .setValue(newTrack.forbiddenWords)
            } else {
                deleteTrack(oldTrack)
                addTrack(newTrack)
            }
            return true
        }

        fun deleteTrack(oldTrack: MyTrack) {
            if (oldTrack.getKey() != "") { // a blank key deletes the entire db!
                MyApplication.database.getReference("Tracks")
                    .child(oldTrack.getKey())
                    .removeValue()
            }
        }

        fun sortList(
            iSortOrder: SortOrder,
            iCurrentlyAscending: Boolean,
            iList: MutableList<MyTrack>
        ) {
            val collator = Collator.getInstance(Locale.getDefault()).apply {
                strength = Collator.PRIMARY
            }
            if (iCurrentlyAscending) {
                singleInstance.currentlyAscending = true
                when (iSortOrder) {
                    SortOrder.BY_SONG_NAME -> iList.sortWith(compareBy(collator) { it.songName.lowercase() })
                    SortOrder.BY_ARTIST_NAME -> iList.sortWith(compareBy(collator) { it.artistName.lowercase() })
                    SortOrder.BY_RELEASE_YEAR -> iList.sortWith(
                        compareBy(
                            { it.releaseYear },
                            { it.songName.lowercase() })
                    )
                }
            } else {
                singleInstance.currentlyAscending = false
                when (iSortOrder) {
                    SortOrder.BY_SONG_NAME -> iList.sortWith(reverseOrder(compareBy(collator) { it.songName.lowercase() }))
                    SortOrder.BY_ARTIST_NAME -> iList.sortWith(reverseOrder(compareBy(collator) { it.artistName.lowercase() }))
                    SortOrder.BY_RELEASE_YEAR -> iList.sortWith(
                        compareByDescending<MyTrack> { it.releaseYear }
                            .thenByDescending { it.songName.lowercase() }
                    )
                }
            }
            singleInstance.sortOrder = iSortOrder
        }

        fun afterSave(iUri: String): Int {
            sortList(
                singleInstance.sortOrder,
                singleInstance.currentlyAscending,
                singleInstance.tracks
            )
            setDataHasChanged(true)
            return getTrackInd(iUri)
        }

        fun shuffleForWhenWasThat() {
            singleInstance.tracks.shuffle()
        }

        fun getNextTrackForWhenWasThat(
            iMinYear: Int,
            iMaxYear: Int,
            iAllowDuplicates: Boolean,
            iYears: MutableList<Int>
        ): MyTrack {
            var yearOK = false
            var dupOK = false

            singleInstance.whenWasThatIndex += 1

            while (!yearOK || !dupOK) {
                if (iAllowDuplicates)
                    dupOK = true

                if (singleInstance.tracks[singleInstance.whenWasThatIndex].releaseYear in iMinYear..iMaxYear) {
// xxx                if (singleInstance.tracks[singleInstance.whenWasThatIndex].releaseYear in 1979..1979) {
                    yearOK = true
                }
                if (yearOK && !dupOK) { // i.e. don't do this check if it didn't match the year
                    if (singleInstance.tracks[singleInstance.whenWasThatIndex].releaseYear !in iYears) {
                        dupOK = true
                    }
                }
                if (!yearOK || !dupOK) {
                    yearOK = false
                    dupOK = false
                    singleInstance.whenWasThatIndex += 1
                } else
                    Timber.tag("Alex")
                        .d("Found ${singleInstance.tracks[singleInstance.whenWasThatIndex].releaseYear}")
            }
            Timber.tag("Alex")
                .d("returned ${singleInstance.tracks[singleInstance.whenWasThatIndex].songName} for When Was That?")
            return singleInstance.tracks[singleInstance.whenWasThatIndex]
        }
    }

    init {
        singleInstance = this
    }

    override fun onCleared() {
        if (trackListener != null) {
            MyApplication.databaseRef.child("Tracks/")
                .removeEventListener(trackListener!!)
            trackListener = null
        }
    }

    /*    fun setCallback(iCallback: DataUpdatedCallback?) {
            dataUpdatedCallback = iCallback
        } */

    fun clearCallback() {
        dataUpdatedCallback = null
    }

    fun loadTracks() {
        // Do an asynchronous operation to fetch categories and subcategories
        trackListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                tracks.clear()
                dataSnapshot.children.forEach()
                {
                    var songName = ""
                    var artistName = ""
                    var uRI = ""
                    var imageURI = ""
                    var releaseYear = 0
                    val lyrics: MutableList<String> = ArrayList()
                    val forbiddenWords: MutableList<String> = ArrayList()
                    var key = ""
                    for (child in it.children) {
                        key = it.key.toString()
                        when (child.key.toString()) {
                            "songName" -> songName = child.value.toString().trim()
                            "artistName" -> artistName = child.value.toString().trim()
                            "uri" -> uRI = child.value.toString().trim()
                            "trackUri", "imageUri" -> imageURI = child.value.toString().trim()
                            "releaseYear" -> releaseYear = child.value.toString().toInt()
                            "lyrics" -> {
                                for (lyric in child.children) {
                                    lyrics.add(lyric.value.toString().trim())
                                }
                            }

                            "forbiddenWords" -> {
                                for (fWord in child.children) {
                                    forbiddenWords.add(fWord.value.toString().trim())
                                }
                            }
                        }
                    }
                    if (forbiddenWords.isEmpty())
                        forbiddenWords.add(songName)
                    if (uRI == "")
                        Timber.tag("Alex").d("ATTENTION has no URI: $songName $artistName")
                    tracks.add(
                        MyTrack(
                            songName,
                            artistName,
                            uRI,
                            imageURI,
                            releaseYear,
                            lyrics,
                            forbiddenWords
                        )
                    )
                    if (songName.trim() == "")
                        Timber.tag("Alex").d("song $key has no name")
                }
                singleInstance.loaded = true
                dataUpdatedCallback?.onDataUpdate()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                Toast.makeText(
                    MyApplication.myMainActivity,
                    "user authorization failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        MyApplication.database.getReference("Tracks").addValueEventListener(
            trackListener as ValueEventListener
        )
    }
}

interface DataUpdatedCallback {
    fun onDataUpdate()
}
