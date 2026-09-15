package com.isrbet.cottagegames

import android.accounts.Account
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.createAttributionContext
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.navigation.fragment.NavHostFragment
import com.isrbet.cottagegames.databinding.ActivityMainBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.database
import com.google.firebase.Firebase
import timber.log.Timber
import java.text.Normalizer

enum class SoundAction {
    START_GAME,
    ADD_ITEM,
    EDIT_ITEM,
    DELETE_ITEM,
    NEXT_ITEM,
    PREV_ITEM,
    ADD_COIN,
    SPEND_COIN,
    GAME_OVER
}

enum class SortOrder {
    BY_SONG_NAME,
    BY_ARTIST_NAME,
    BY_RELEASE_YEAR
}

var gGameUnderway = false

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val tracksModel: TrackViewModel by viewModels()
    private val celebritiesModel: CelebrityViewModel by viewModels()
    private var isLoggedIn = false

    fun setIsLoggedIn(iValue: Boolean) {
        isLoggedIn = iValue
        Timber.tag("Alex").d("setIsLoggedIn is now $isLoggedIn")
    }

    fun getIsLoggedIn(): Boolean {
        return isLoggedIn
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menu.clear()
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                if (menuItem.itemId == android.R.id.home) {
                    binding.drawerLayout.openDrawer(GravityCompat.START)
                    return true
                } else {
                    return false
                }
            }
        })

        Timber.plant(Timber.DebugTree())

        tracksModel.clearCallback()
        celebritiesModel.clearCallback()

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
//        binding.navView.inflateMenu(R.menu.drawer_nav_menu)
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            if (true) { //getIsLoggedIn()) {
                val navHostFragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                val navController = navHostFragment.navController
                when (menuItem.itemId) {
                    R.id.nav_menu_home -> {
                        if (gGameUnderway) { // this means a game is underway
                            val builder = AlertDialog.Builder(this)

                            builder.setTitle("Confirm Action")
                            builder.setMessage("Are you sure you want to quit this game?")

                            // Set positive/confirmation button
                            builder.setPositiveButton("Confirm") { dialog, _ ->
                                // Execute your confirmation logic here
                                dialog.dismiss()
                                gGameUnderway = false
                                navController.navigate(R.id.navigation_home)
                            }

                            // Set negative/cancel button
                            builder.setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                                Timber.tag("Alex").d("Cancelled")
                            }

                            // Optional: Prevent dismissing when clicking outside the dialog window
                            builder.setCancelable(false)

                            // Create and display the dialog
                            val alertDialog = builder.create()
                            alertDialog.show()
                        } else {
                            navController.navigate(R.id.navigation_home)
                        }
                    }

                    R.id.nav_menu_view_all_songs -> {
                        if (gGameUnderway) { // this means a game is underway
                            val builder = AlertDialog.Builder(this)

                            builder.setTitle("Confirm Action")
                            builder.setMessage("Are you sure you want to quit this game?")

                            // Set positive/confirmation button
                            builder.setPositiveButton("Confirm") { dialog, _ ->
                                // Execute your confirmation logic here
                                dialog.dismiss()
                                gGameUnderway = false
                                navController.navigate(R.id.navigation_view_all_songs)
                            }

                            // Set negative/cancel button
                            builder.setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                                Timber.tag("Alex").d("Cancelled")
                            }

                            // Optional: Prevent dismissing when clicking outside the dialog window
                            builder.setCancelable(false)

                            // Create and display the dialog
                            val alertDialog = builder.create()
                            alertDialog.show()
                        } else {
                            navController.navigate(R.id.navigation_view_all_songs)
                        }
                    }

                    R.id.nav_menu_add_song -> {
                        /*                        if (!SpotifyService.isConnected()) {
                                                    Timber.tag("Alex").d("Spotify not available so not going there")
                                                    Toast.makeText(
                                                        MyApplication.myMainActivity,
                                                        "Spotify is not currently available.",
                                                        Toast.LENGTH_LONG
                                                    )
                                                } else { */
                        navController.navigate(R.id.navigation_song)
//                        }
                    }

                    R.id.nav_menu_view_all_celebrities -> {
                        navController.navigate(R.id.navigation_view_all_celebrities)
                    }

                    R.id.nav_menu_celebrity -> {
                        navController.navigate(R.id.navigation_celebrity)
                    }
                }
            } else
                Toast.makeText(this, "You must Sign In before playing", Toast.LENGTH_LONG).show()
            binding.drawerLayout.closeDrawers()
            true
        }

        // Listen for fragment changes to update the menu
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
//        val navView: NavigationView = findViewById(R.id.drawerLayout)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home -> {
                    binding.navView.menu.findItem(R.id.nav_menu_home)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_songs)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_add_song)?.isVisible = MyApplication.adminMode
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_celebrities)?.isVisible =
                        true
                    binding.navView.menu.findItem(R.id.nav_menu_celebrity)?.isVisible = false
                }

                R.id.navigation_view_all_songs -> {
                    binding.navView.menu.findItem(R.id.nav_menu_home)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_songs)?.isVisible = false
                    binding.navView.menu.findItem(R.id.nav_menu_add_song)?.isVisible = MyApplication.adminMode
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_celebrities)?.isVisible =
                        false
                    binding.navView.menu.findItem(R.id.nav_menu_celebrity)?.isVisible = false
                }

                R.id.navigation_song -> {
                    binding.navView.menu.findItem(R.id.nav_menu_home)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_songs)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_add_song)?.isVisible = false
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_celebrities)?.isVisible =
                        false
                    binding.navView.menu.findItem(R.id.nav_menu_celebrity)?.isVisible = false
                }

                R.id.navigation_name_that_tune_game -> {
                    binding.navView.menu.findItem(R.id.nav_menu_home)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_songs)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_add_song)?.isVisible = MyApplication.adminMode
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_celebrities)?.isVisible =
                        false
                    binding.navView.menu.findItem(R.id.nav_menu_celebrity)?.isVisible = false
                }

                R.id.navigation_when_was_that_game -> {
                    binding.navView.menu.findItem(R.id.nav_menu_home)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_songs)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_add_song)?.isVisible = MyApplication.adminMode
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_celebrities)?.isVisible =
                        false
                    binding.navView.menu.findItem(R.id.nav_menu_celebrity)?.isVisible = false
                }

                R.id.navigation_view_all_celebrities -> {
                    binding.navView.menu.findItem(R.id.nav_menu_home)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_songs)?.isVisible = false
                    binding.navView.menu.findItem(R.id.nav_menu_add_song)?.isVisible = false
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_celebrities)?.isVisible =
                        false
                    binding.navView.menu.findItem(R.id.nav_menu_celebrity)?.isVisible =
                        MyApplication.adminMode
                }

                R.id.navigation_celebrity_game -> {
                    binding.navView.menu.findItem(R.id.nav_menu_home)?.isVisible = true
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_songs)?.isVisible = false
                    binding.navView.menu.findItem(R.id.nav_menu_add_song)?.isVisible = false
                    binding.navView.menu.findItem(R.id.nav_menu_view_all_celebrities)?.isVisible =
                        true
                    binding.navView.menu.findItem(R.id.nav_menu_celebrity)?.isVisible =
                        MyApplication.adminMode
                }

                R.id.navigation_celebrity -> {
                    Timber.tag("Alex").d("in addOnDestinationChangedListener navigation_celebrity")
                }

                else -> {
                    Timber.tag("Alex").d("in addOnDestinationChangedListener else")
                }
            }
        }


        if (isSpotifyInstalled()) {
            SpotifyService.connect(this) { result ->
                Timber.tag("Alex").d("Spotify has been started result is $result")
            }
        } else {
            Timber.tag("Alex").d("Spotify is not installed")
        }
        if (!TrackViewModel.isLoaded())
            tracksModel.loadTracks()
        if (!CelebrityViewModel.isLoaded())
            celebritiesModel.loadCelebrities()
    }

    fun isSpotifyInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.spotify.music", PackageManager.GET_META_DATA)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onStop() {
        super.onStop()
//        SpotifyAppRemote.disconnect(gSpotifyAppRemote)
        SpotifyService.disconnect()
    }

    /*    override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId == android.R.id.home) {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                return true
            }
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            return item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
        } */
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
        var currentGameURI = ""
        var trackSearchText = ""
        var celebritySearchText = ""
        var versionCode = ""
        var versionName = ""
        var adminMode = false
        private var mediaPlayer: MediaPlayer? = null

        fun playSound(iAction: SoundAction) {
            mediaPlayer?.release()
            val sound = when (iAction) {
                SoundAction.START_GAME -> R.raw.shuffling_cards_4
                SoundAction.ADD_ITEM -> R.raw.swish_nice_and_clean
                SoundAction.EDIT_ITEM -> R.raw.whoosh_land_speeder
                SoundAction.DELETE_ITEM -> R.raw.swish_gulpy_noise
                SoundAction.NEXT_ITEM -> R.raw.short_swish
                SoundAction.PREV_ITEM -> R.raw.short_ploppy_plop
                SoundAction.ADD_COIN -> R.raw.cash_register
                SoundAction.SPEND_COIN -> R.raw.coin_drop
                SoundAction.GAME_OVER -> R.raw.summer_beach_party
            }
            mediaPlayer = MediaPlayer.create(mContext, sound).apply {
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                }
                start()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // initialization code here
        mContext = createAttributionContext(this, "audioPlayback")
        Firebase.database.setPersistenceEnabled(true)
        database = FirebaseDatabase.getInstance()
        databaseRef = database.reference
        prefs = applicationContext.getSharedPreferences("Prefs", 0)
        prefEditor = prefs.edit()
    }

    override fun onTerminate() {
        super.onTerminate()
        mediaPlayer?.release()
        mediaPlayer = null
        SpotifyService.disconnect()
    }
}

fun focusAndOpenSoftKeyboard(context: Context, view: View) {
    view.requestFocus()
    // open the soft keyboard
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

fun giveMeDate(): String {
    val cal = Calendar.getInstance()
    var tempString: String = cal.get(Calendar.YEAR).toString() + "-"
    if (cal.get(Calendar.MONTH) + 1 < 10)
        tempString += "0"
    tempString = tempString + (cal.get(Calendar.MONTH) + 1).toString() + "-"
    if (cal.get(Calendar.DATE) < 10)
        tempString += "0"
    tempString += cal.get(Calendar.DATE).toString()
    return tempString
}

fun makeKeySafe(iText: String): String {
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

fun String.unaccent(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return "\\p{InCombiningDiacriticalMarks}+".toRegex().replace(normalized, "")
}