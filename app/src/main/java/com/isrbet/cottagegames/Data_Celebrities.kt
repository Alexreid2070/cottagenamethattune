package com.isrbet.cottagegames

import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import timber.log.Timber
import kotlin.math.min

data class MyCelebrity(
    var celebrityName: String,
    var numberOfTimesUsed: Int,
    var mykey: String) {

    fun getKey() : String {
        return makeKeySafe(celebrityName)
    }
    fun contains(iSubString: String): Boolean {
        val lc = iSubString.lowercase()
        val contains = celebrityName.lowercase().contains(lc)

        return contains
    }
}

class CelebrityViewModel : ViewModel() {
    private var celebrityListener: ValueEventListener? = null
    private val celebrities: MutableList<MyCelebrity> = ArrayList()
    private var dataUpdatedCallback: DataUpdatedCallback? = null
    private var loaded:Boolean = false
    private var dataHasChanged: Boolean = false

    companion object {
        lateinit var singleInstance: CelebrityViewModel // used to track static single instance of self

        fun getCelebrities() : MutableList<MyCelebrity> {
            singleInstance.celebrities.sortBy { it.celebrityName.lowercase() }
            return singleInstance.celebrities
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

        fun getCount() : Int {
            return if (::singleInstance.isInitialized)
                singleInstance.celebrities.size
            else
                0
        }
        fun getCelebrity(i: Int) : MyCelebrity? {
            return if (::singleInstance.isInitialized)
                if (singleInstance.celebrities.size >= i)
                    singleInstance.celebrities[i]
                else
                    null
            else
                null
        }

        fun getCelebrity(index: String) : MyCelebrity? {
            for (i in 0 until singleInstance.celebrities.size) {
                if (singleInstance.celebrities[i].mykey == index) {
                    return singleInstance.celebrities[i]
                }
            }
            return null
        }
        fun getCelebrityIndex(cName: String) : Int {
            for (i in 0 until singleInstance.celebrities.size) {
                if (singleInstance.celebrities[i].celebrityName == cName) {
                    return i
                }
            }
            return -1
        }

        fun getListForGame(iNumToGet: Int) : MutableList<MyCelebrity> {
            val tList: MutableList<MyCelebrity> = ArrayList()
            singleInstance.celebrities.shuffle()

            val numToGet = min(iNumToGet, singleInstance.celebrities.size)
            for (i in 0 until numToGet) {
                Timber.tag("Alex").d("Getting $i of $numToGet size is ${singleInstance.celebrities.size}")
                tList.add(MyCelebrity(singleInstance.celebrities[i].celebrityName, 0, ""))
            }

            return tList
        }

        fun addCelebrity(celebrity: MyCelebrity) : Boolean {
            val key = MyApplication.database.getReference("Celebrity/Celebrities").push().key.toString()
            MyApplication.database.getReference("Celebrity/Celebrities")
                .child(key)
                .child("celebrityName")
                .setValue(celebrity.celebrityName)
//            singleInstance.celebrities.add(celebrity)
            return true
        }

        fun editCelebrity(newCelebrity: MyCelebrity) : Boolean {
            MyApplication.database.getReference("Celebrity/Celebrities")
                .child(newCelebrity.mykey)
                .child("celebrityName")
                .setValue(newCelebrity.celebrityName)
            return true
        }
        fun deleteCelebrity(index: Int) {
            if (index > -1 && index < singleInstance.celebrities.size) {
                if (singleInstance.celebrities[index].mykey != "") { // a blank key deletes the entire db!
                    MyApplication.database.getReference("Celebrity/Celebrities")
                        .child(singleInstance.celebrities[index].mykey)
                        .removeValue()
                } else
                    Timber.tag("Alex").d("Delete failed 1")
            } else
                Timber.tag("Alex").d("Delete failed 2")
        }
        fun reshufflePlayOrder() {
            singleInstance.celebrities.shuffle()
        }
    }
    init {
        singleInstance = this
    }

    override fun onCleared() {
        if (celebrityListener != null) {
            MyApplication.databaseRef.child("Celebrity/Celebrities")
                .removeEventListener(celebrityListener!!)
            celebrityListener = null
        }
    }

    fun setCallback(iCallback: DataUpdatedCallback?) {
        singleInstance.dataUpdatedCallback = iCallback
    }

    fun clearCallback() {
        dataUpdatedCallback = null
    }

    fun loadCelebrities() {
        celebrityListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                celebrities.clear()
                dataSnapshot.children.forEach()
                {
                    var celebrityName = ""
                    var numberOfTimesUsed = 0
                    for (child in it.children) {
                        when (child.key.toString()) {
                            "celebrityName" -> celebrityName = child.value.toString().trim()
                        }
                    }
                    celebrities.add(MyCelebrity(celebrityName, numberOfTimesUsed, it.key!!))
                }
                singleInstance.loaded = true
                dataUpdatedCallback?.onDataUpdate()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                Toast.makeText(MyApplication.myMainActivity, "user authorization failed", Toast.LENGTH_SHORT).show()
            }
        }
        MyApplication.database.getReference("Celebrity/Celebrities").addValueEventListener(
            celebrityListener as ValueEventListener
        )
    }
}