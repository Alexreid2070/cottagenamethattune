package com.isrbet.cottagegames

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.isrbet.cottagegames.databinding.FragmentHomeBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MyApplication.trackSearchText = ""
        if (!MyApplication.adminMode)
            binding.doSomethingButton.visibility = View.GONE
        TrackViewModel.setMyFilter(TracksFilter.All)
        gGameUnderway = false
        (activity as AppCompatActivity?)!!.supportActionBar!!.title =
            getString(R.string.cottage_games)
//        pausePlay()
        binding.startNameThatTuneButton.setOnClickListener {
            Timber.tag("Alex").d("starting Name That Tune")
            findNavController().navigate(R.id.navigation_name_that_tune_game)
        }
        binding.startCelebrityButton.setOnClickListener {
            Timber.tag("Alex").d("starting Celebrity")
            findNavController().navigate(R.id.navigation_celebrity_game)
        }
        binding.startWhenWasThatButton.setOnClickListener {
            findNavController().navigate(R.id.navigation_when_was_that_game)
        }
        binding.doSomethingButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                doSomething()
            }
        }
        if (!gGameUnderway) {
            binding.startNameThatTuneButton.visibility = View.VISIBLE
            binding.startCelebrityButton.visibility = View.VISIBLE
            binding.startWhenWasThatButton.visibility = View.VISIBLE
        }
        setUserNameAndPhoto(
            MyApplication.userName,
            MyApplication.userEmail,
            MyApplication.userPhotoURL
        )

        val mNavigationView: NavigationView = requireActivity().findViewById(R.id.navView)
        val mHeaderView = mNavigationView.getHeaderView(0)
        val signOutButton: Button = mHeaderView.findViewById(R.id.signoutButton)
        signOutButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.are_you_sure))
                .setMessage("Are you sure that you want to sign out?")
                .setPositiveButton("Sign out") { _, _ -> signOut() }
                .setNegativeButton(R.string.cancel) { _, _ -> }  // nothing should happen, other than dialog closes
                .show()
        }
        signOutButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.are_you_sure))
                .setMessage("Are you sure that you want to sign out?")
                .setPositiveButton("Sign out") { _, _ -> signOut() }
                .setNegativeButton(R.string.cancel) { _, _ -> }  // nothing should happen, other than dialog closes
                .show()
        }
    }

    private fun setUserNameAndPhoto(iUserName: String, iUserEmail: String, iPhotoURL: String) {
        val mNavigationView: NavigationView = requireActivity().findViewById(R.id.navView)
        val mHeaderView = mNavigationView.getHeaderView(0)
        val textViewUsername: TextView = mHeaderView.findViewById(R.id.user_name)
        val textViewEmail: TextView = mHeaderView.findViewById(R.id.user_email)
        val userImage: ImageView = mHeaderView.findViewById(R.id.user_image)
        textViewUsername.text = iUserName
        textViewEmail.text = iUserEmail

        Glide.with(requireContext()).load(MyApplication.userPhotoURL)
            .thumbnail(0.5f)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(userImage)

    }

    /*    suspend fun searchSong(token: String, query: String): List<Track> {
            val results = SpotifyService.search("Song Title", listOf(SearchType.TRACK))
                .await() // or synchronous execution depending on configuration

            for (track in results.tracks?.items.orEmpty()) {
                println("${track.name} by ${track.artists.firstOrNull()?.name}")
            }
        }
    */
    private suspend fun doSomething() {
        val accessToken = SpotifyService.getSpotifyAccessToken()
        Timber.tag("Alex").d("Access token is '$accessToken'")
        for (song in songsToUpload) {
            val songURI = SpotifyService.getTrackURI(accessToken, song.artistName, song.songName, song.releaseYear)
//            Timber.tag("Alex").d("songID is '$songURI'")
            SpotifyService.playAndThenDo(song, songURI, requireContext())
        }
    }

    private fun signOut() {
        Firebase.auth.signOut()
        MyApplication.userName = ""
        MyApplication.userEmail = ""
        MyApplication.userPhotoURL = ""
        MyApplication.userAccount = null
        requireActivity().finishAffinity()
        findNavController().navigate(R.id.SignInFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class SongToUpload(val songName: String, val artistName: String, val releaseYear: Int)

val songsToUpload = arrayOf(
    SongToUpload("Want Ads", "The Honey Cone", 1971),
    SongToUpload("Wanted", "Perry Como", 1954),
    SongToUpload("Wasting My Time", "Default", 2001),
    SongToUpload("We Are Stars", "Virginia to Vegas", 2014),
    SongToUpload("We Belong", "Pat Benatar", 1984),
    SongToUpload("We Built This City", "Starship", 1985),
    SongToUpload("We Can Work It Out", "The Beatles", 1965),
    SongToUpload("We Didn't Start The Fire", "Billy Joel", 1989),
    SongToUpload("We Got The Beat", "Go-Go's", 1982),
    SongToUpload("We Run", "Strange Advance", 1985),
    SongToUpload("We’re Here For A Good Time", "Trooper", 1977),
    SongToUpload("We've Got Tonight", "Bob Seger", 1978),
    SongToUpload("Wedding Bell Blues", "The 5th Dimension", 1969),
    SongToUpload("Weekend", "The Dictators", 1975),
    SongToUpload("Welcome Back", "John Sebastian", 1976),
    SongToUpload("What a Fool Believes", "The Doobie Brothers", 1979),
    SongToUpload("What About Us", "Pink", 2017),
    SongToUpload("What I Wouldn’t Do", "Serena Ryder", 2012),
    SongToUpload("What Is Love", "Haddaway", 1992),
    SongToUpload("What You Need", "INXS", 1986),
    SongToUpload("What's On Your Mind (Pure Energy)", "Information Society", 1988),
    SongToUpload("Whatever Gets You Thru the Night", "John Lennon", 1974),
    SongToUpload("Whatever It Takes", "Imagine Dragons", 2017),
    SongToUpload("Wheel in the Sky", "Journey", 1978),
    SongToUpload("Wheel Of Fortune", "Kay Starr", 1952),
    SongToUpload("When A Man Loves A Woman", "Michael Bolton", 1991),
    SongToUpload("When A Man Loves A Woman", "Percy Sledge", 1966),
    SongToUpload("When I Need You", "Leo Sayer", 1977),
    SongToUpload("When I See U", "Fantasia", 2006),
    SongToUpload("When I See You Smile", "Bad English", 1989),
    SongToUpload("When I Think Of You", "Janet Jackson", 1986),
    SongToUpload("When I’m Up (I Can’t Get Down)", "Great Big Sea", 1997),
    SongToUpload("When I'm With You", "Sheriff", 1983),
    SongToUpload("When The Going Gets Tough, The Tough Get Going", "Billy Ocean", 1985),
    SongToUpload("When the Night Feels My Song", "Bedouin Soundclash", 2004),
    SongToUpload("When You're Gone", "Avril Lavigne", 2007),
    SongToUpload("Where Do Broken Hearts Go", "Whitney Houston", 1988),
    SongToUpload("Where Were You (When the World Stopped Turning)", "Alan Jackson", 2003),
    SongToUpload("Wherever You Will Go", "The Calling", 2001),
    SongToUpload("Who Can It Be Now?", "Men At Work", 1981),
    SongToUpload("Who Knew", "Pink", 2006)
)