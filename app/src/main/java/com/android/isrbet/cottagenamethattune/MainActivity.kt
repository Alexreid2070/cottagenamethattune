package com.android.isrbet.cottagenamethattune

import android.accounts.Account
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.onNavDestinationSelected
import com.android.isrbet.cottagenamethattune.databinding.ActivityMainBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

enum class SoundAction(val code: Int) {
    START_GAME(0),
    ADD_SONG(1),
    EDIT_SONG(2),
    DELETE_SONG(3),
    NEXT_SONG(4),
    PREV_SONG(5)
}

enum class SortOrder(val code: Int) {
    BY_SONG_NAME(0),
    BY_ARTIST_NAME(1),
    BY_DATE_ADDED(2),
    BY_PLAY_ORDER(3)
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tracksModel: TrackViewModel by viewModels()
    private var isLoggedIn = false

    fun setIsLoggedIn(iValue: Boolean) {
        isLoggedIn = iValue
    }
    fun getIsLoggedIn() : Boolean {
        return isLoggedIn
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        tracksModel.clearCallback()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MyApplication.myMainActivity = this
        setSupportActionBar(findViewById(R.id.toolbar))
        val actionBarToggle =
            ActionBarDrawerToggle(this, binding.drawerLayout, 0, 0)
        binding.drawerLayout.addDrawerListener(actionBarToggle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        actionBarToggle.syncState()
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            if (isLoggedIn) {
                val navHostFragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                val navController = navHostFragment.navController
                when (menuItem.itemId) {
                    R.id.navigation_home -> {
                        navController.navigate(R.id.navigation_home)
                    }
                    R.id.navigation_view_all_songs -> {
                        navController.navigate(R.id.navigation_view_all_songs)
                    }
                    R.id.navigation_song -> {
                        navController.navigate(R.id.navigation_song)
                    }
                }
            } else
                Toast.makeText(this, "You must Sign In before playing", Toast.LENGTH_LONG).show()
            binding.drawerLayout.closeDrawers()
            true
        }
        if (isSpotifyInstalled()) {
            SpotifyService.connect(this) { result ->
                Log.d("Alex", "Spotify has been started result is $result")
            }
        } else {
            Log.d("Alex", "Spotify is not installed")
        }
        if (!TrackViewModel.isLoaded())
            tracksModel.loadTracks()
    }

    fun isSpotifyInstalled(): Boolean {
        return try {
            val info = packageManager.getPackageInfo("com.spotify.music", PackageManager.GET_META_DATA)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onStop() {
        super.onStop()
//        SpotifyAppRemote.disconnect(gSpotifyAppRemote)
        SpotifyService.disconnect()
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.options_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            binding.drawerLayout.openDrawer(GravityCompat.START)
            return true
        }
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
    }
}

class MyApplication : Application() {
    companion object {
        lateinit var mContext: Context
        lateinit var database: FirebaseDatabase
        lateinit var databaseRef: DatabaseReference
        lateinit var myMainActivity: MainActivity
        lateinit var prefs: SharedPreferences
        lateinit var prefEditor: SharedPreferences.Editor
        var userEmail: String = ""
        var userName: String = ""
        var userPhotoURL: String = ""
        var userAccount: Account? = null
        var gameHasStarted = false
        var currentGameURI = ""
        var trackSearchText = ""
//        var sortOrder = SortOrder.by_PLAY_ORDER

        fun playSound(context: Context?, iAction: SoundAction) {
            val sound = when (iAction) {
                SoundAction.START_GAME -> R.raw.braam_braamamma
                SoundAction.ADD_SONG -> R.raw.swish_nice_and_clean
                SoundAction.EDIT_SONG -> R.raw.whoosh_land_speeder
                SoundAction.DELETE_SONG -> R.raw.swish_gulpy_noise
                SoundAction.NEXT_SONG -> R.raw.short_swish
                SoundAction.PREV_SONG -> R.raw.short_ploppy_plop
            }
            val mediaPlayer = MediaPlayer.create(context, sound)
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener {
                it.release()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // initialization code here
        mContext = this
        Firebase.database.setPersistenceEnabled(true)
        database = FirebaseDatabase.getInstance()
        databaseRef = database.reference
        prefs = applicationContext.getSharedPreferences("Prefs", 0)
        prefEditor = prefs.edit()
    }

    override fun onTerminate() {
        super.onTerminate()
        SpotifyService.disconnect()
    }
}

fun focusAndOpenSoftKeyboard(context: Context, view: View) {
    view.requestFocus()
    // open the soft keyboard
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

fun giveMeDate() : String {
    val cal = Calendar.getInstance()
    var tempString: String = cal.get(Calendar.YEAR).toString() + "-"
    if (cal.get(Calendar.MONTH)+1 < 10)
        tempString += "0"
    tempString = tempString + (cal.get(Calendar.MONTH)+1).toString() + "-"
    if (cal.get(Calendar.DATE) < 10)
        tempString += "0"
    tempString += cal.get(Calendar.DATE).toString()
    return tempString
}

fun makeKeySafe(iText: String) : String {
    var tText = iText
    tText = tText.replace(".", " ")
    tText = tText.replace("#", " ")
    tText = tText.replace("/", " ")
    tText = tText.replace("\\", " ")
    tText = tText.replace("[", " ")
    tText = tText.replace("]", " ")
    tText = tText.replace("+", " ")
    tText = tText.replace("$", " ")
    return tText
}

