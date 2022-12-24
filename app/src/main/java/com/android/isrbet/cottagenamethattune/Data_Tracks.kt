package com.android.isrbet.cottagenamethattune

import android.graphics.Color
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

data class MyTrack(
    var songName: String,
    var artistName: String,
    var uri: String,
    var imageUri: String,
    var dateAdded: String,
    var playOrder: Int,
    var lyrics: MutableList<String>,
    val forbiddenWords: MutableList<String>) {

    fun getKey() : String {
        return makeKeySafe("$songName - $artistName")
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
    fun getForbiddenLyrics() : String {
        var tString = ""
        forbiddenWords.forEach {
            tString = tString + it + "\n"
        }
        return tString
    }
}
/*
data class MyTrackSimple (
    var songName: String,
    var artistName: String,
    var uri: String,
    var dateAdded: String,
    var playOrder: Int) {
    constructor(myTrack: MyTrack, iPlayOrder: Int) :
            this(myTrack.songName, myTrack.artistName, myTrack.uri, myTrack.dateAdded, iPlayOrder)
} */

class TrackViewModel : ViewModel() {
    private var trackListener: ValueEventListener? = null
    private val tracks: MutableList<MyTrack> = ArrayList()
    private var indOfLastPlayed: Int = -1
//    private val viewTracks: MutableList<MyTrackSimple> = ArrayList()
//    private var indOfLastViewed: Int = -1
//    private var viewSortOrder:SortOrder = SortOrder.by_PLAY_ORDER
    private var sortOrder:SortOrder = SortOrder.BY_PLAY_ORDER
    private var dataUpdatedCallback: DataUpdatedCallback? = null
    private var loaded:Boolean = false
    private var dataHasChanged: Boolean = false

    companion object {
        lateinit var singleInstance: TrackViewModel // used to track static single instance of self

        fun getTracks() : MutableList<MyTrack> {
            return singleInstance.tracks
        }
        fun getSortOrder() : SortOrder {
//            return singleInstance.viewSortOrder
            return singleInstance.sortOrder
        }
        fun getDataHasChanged() : Boolean {
            return singleInstance.dataHasChanged
        }
        fun setDataHasChanged(iNewValue: Boolean) {
            singleInstance.dataHasChanged = iNewValue
        }
/*        fun getIndOfLastPlayed(): Int {
            return singleInstance.indOfLastPlayed
        }
        fun setIndOfLastPlayed(indIn: Int) {
            singleInstance.indOfLastPlayed = indIn
        } */
        fun isLoaded():Boolean {
            return singleInstance.loaded
        }
        fun reshufflePlayOrder() {
            singleInstance.indOfLastPlayed = -1
            singleInstance.tracks.shuffle()
            for (i in 0 until singleInstance.tracks.size)
                singleInstance.tracks[i].playOrder = i
        }

        fun getCount() : Int {
            return if (::singleInstance.isInitialized)
                singleInstance.tracks.size
            else
                0
        }
/*        fun getPrevTrackIndex() : Int {
            if (singleInstance.indOfLastPlayed > 0)
                singleInstance.indOfLastPlayed -= 1
            else
                singleInstance.indOfLastPlayed = singleInstance.tracks.size-1
            return singleInstance.indOfLastPlayed
        }
        fun getNextTrackIndex() : Int {
            if (singleInstance.indOfLastPlayed < singleInstance.tracks.size-1)
                singleInstance.indOfLastPlayed += 1
            else
                singleInstance.indOfLastPlayed = 0
            return  singleInstance.indOfLastPlayed
        } */
        fun getTrack(ind: Int) : MyTrack? {
            return if (ind >= 0 && ind < singleInstance.tracks.size) {
                singleInstance.tracks[ind]
            } else {
                null
            }
        }
        fun getTrack(uri: String, songName: String = "", artistName: String = ""): MyTrack? {
            var myTrack = singleInstance.tracks.find { it.uri == uri }
            if (myTrack == null &&
                (songName != "" && artistName != "")) {
                myTrack = singleInstance.tracks.find {it.songName == songName && it.artistName == artistName }
            }
            return  myTrack
        }

        fun getTrackInd(uri: String): Int {
            for (i in 0 until singleInstance.tracks.size) {
                if (singleInstance.tracks[i].uri == uri) {
                    return i
                }
            }
            return -1
        }
        fun addTrack(track: MyTrack) : Boolean {
            // save backup song list
            val key = MyApplication.database.getReference("Songs").push().key.toString()
            MyApplication.database.getReference("Songs")
                .child(key)
                .child("songName")
                .setValue(track.songName)
            MyApplication.database.getReference("Songs")
                .child(key)
                .child("artistName")
                .setValue(track.artistName)

            singleInstance.tracks.add(track)
            MyApplication.database.getReference("Tracks")
                .child(track.getKey())
                .child("songName")
                .setValue(track.songName)
            MyApplication.database.getReference("Tracks")
                .child(track.getKey())
                .child("artistName")
                .setValue(track.artistName)
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
                .child("dateAdded")
                .setValue(track.dateAdded)
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

        fun editTrack(oldTrack: MyTrack?, newTrack: MyTrack) : Boolean {
            if (oldTrack?.uri != newTrack.uri) {
                Log.d("Alex", "THESE AREN'T THE SAME SONG, ABORTING")
                Toast.makeText(MyApplication.myMainActivity, "Editing, but NOT the same song!!!!", Toast.LENGTH_SHORT).show()
                return false
            }
            if (oldTrack.getKey() == newTrack.getKey()) {
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
                    .child("dateAdded")
                    .setValue(newTrack.dateAdded)
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
        fun sortList(iSortOrder: SortOrder) {
            when (iSortOrder) {
                SortOrder.BY_PLAY_ORDER -> singleInstance.tracks.sortBy { it.playOrder }
                SortOrder.BY_DATE_ADDED -> singleInstance.tracks.sortWith(compareBy({ it.dateAdded.lowercase() }, { it.songName.lowercase() }))
                SortOrder.BY_SONG_NAME -> singleInstance.tracks.sortBy { it.songName.lowercase() }
                SortOrder.BY_ARTIST_NAME -> singleInstance.tracks.sortBy { it.artistName.lowercase() }
            }
            singleInstance.sortOrder = iSortOrder
        }
/*        fun getViewList() : MutableList<MyTrackSimple> {
            if (singleInstance.viewTracks.size == 0)
                refreshViewList()
            return singleInstance.viewTracks
        }
        fun refreshViewList(){
            singleInstance.viewTracks.clear()
            singleInstance.tracks.forEach {
                singleInstance.viewTracks.add(MyTrackSimple(it, singleInstance.viewTracks.size))
            }
            sortViewList(singleInstance.viewSortOrder)
        }
        fun sortViewList(iSortOrder: SortOrder) {
            when (iSortOrder) {
                SortOrder.by_PLAY_ORDER -> singleInstance.viewTracks.sortBy { it.playOrder }
                SortOrder.by_DATE_ADDED -> singleInstance.viewTracks.sortWith(compareBy({ it.dateAdded.lowercase() }, { it.songName.lowercase() }))
                SortOrder.by_SONG_NAME -> singleInstance.viewTracks.sortBy { it.songName.lowercase() }
                SortOrder.by_ARTIST_NAME -> singleInstance.viewTracks.sortBy { it.artistName.lowercase() }
            }
            singleInstance.viewSortOrder = iSortOrder
        }
        fun getPrevIndToView() : Int {
            singleInstance.indOfLastViewed = if (singleInstance.indOfLastViewed > 0)
                singleInstance.indOfLastViewed - 1
            else
                singleInstance.viewTracks.size-1
            return singleInstance.indOfLastViewed
        }
        fun getNextIndToView() : Int {
            singleInstance.indOfLastViewed = if (singleInstance.indOfLastViewed < singleInstance.viewTracks.size-1)
                singleInstance.indOfLastViewed + 1
            else
                0
            return  singleInstance.indOfLastViewed
        }
        fun getTrackToView(ind: Int) : MyTrack? {
            Log.d("Alex", "ind in is $ind, size is ${singleInstance.viewTracks.size}")
            return if (ind >= 0 && ind < singleInstance.viewTracks.size) {
                getTrack(singleInstance.viewTracks[ind].uri)
            } else {
                null
            }
        }
        fun setIndOfLastViewed(indIn: Int) {
            singleInstance.indOfLastViewed = indIn
        }
        fun getViewTrackInd(uri: String): Int {
            for (i in 0 until singleInstance.viewTracks.size) {
                if (singleInstance.viewTracks[i].uri == uri) {
                    return i
                }
            }
            return -1
        } */
        fun afterSave(iUri: String) : Int {
            sortList(singleInstance.sortOrder)
            setDataHasChanged(true)
            return getTrackInd(iUri)
        }
    }
    init {
        singleInstance = this
    }

    override fun onCleared() {
        super.onCleared()
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
                    var dateAdded = ""
                    val lyrics: MutableList<String> = ArrayList()
                    val forbiddenWords: MutableList<String> = ArrayList()
                    for (child in it.children) {
                        when (child.key.toString()) {
                            "songName" -> songName = child.value.toString().trim()
                            "artistName" -> artistName = child.value.toString().trim()
                            "uri" -> uRI = child.value.toString().trim()
                            "trackUri", "imageUri" -> imageURI = child.value.toString().trim()
                            "dateAdded" -> dateAdded = child.value.toString().trim()
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
                    if (forbiddenWords.size == 0)
                        forbiddenWords.add(songName)
                    tracks.add(MyTrack(songName, artistName, uRI, imageURI, dateAdded, getCount(), lyrics, forbiddenWords))
//                    Log.d("Songs", "$songName-$artistName")
                }
                singleInstance.loaded = true
                dataUpdatedCallback?.onDataUpdate()
//                tracks.sortWith(compareBy ({ it.dateAdded.lowercase() }, {it.songName.lowercase()}))
//                reshufflePlayOrder()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                Toast.makeText(MyApplication.myMainActivity, "user authorization failed", Toast.LENGTH_SHORT).show()
            }
        }
        MyApplication.database.getReference("Tracks").addValueEventListener(
            trackListener as ValueEventListener
        )
    }
}

interface DataUpdatedCallback  {
    fun onDataUpdate()
}
