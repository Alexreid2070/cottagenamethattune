package com.android.isrbet.cottagenamethattune

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.android.isrbet.cottagenamethattune.databinding.FragmentHomeBinding
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
                .setNegativeButton(android.R.string.cancel) { _, _ -> }  // nothing should happen, other than dialog closes
                .show()
        }
        signOutButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.are_you_sure))
                .setMessage("Are you sure that you want to sign out?")
                .setPositiveButton("Sign out") { _, _ -> signOut() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }  // nothing should happen, other than dialog closes
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
    SongToUpload("Itsy Bitsy Teenie Weenie Yellow Polka Dot Bikini", "Brian Hyland", 1960),
    SongToUpload("Jack and Diane", "John Cougar Mellencamp", 1982),
    SongToUpload("Jackie Blue", "The Ozark Mountain Daredevils", 1975),
    SongToUpload("Jacob's Ladder", "Huey Lewis & The News", 1986),
    SongToUpload("Jealous", "Chromeo", 2014),
    SongToUpload("Jeopardy", "Greg Kihn Band", 1983),
    SongToUpload("Jessie's Girl", "Rick Springfield", 1981),
    SongToUpload("Jet Lag", "Simple Plan", 2011),
    SongToUpload("Joanna", "Kool & The Gang", 1983),
    SongToUpload("Johnny Angel", "Shelley Fabares", 1962),
    SongToUpload("Joyride", "Roxette", 1991),
    SongToUpload("Judy in Disguise (With Glasses)", "John Fred & His Playboy Band", 1967),
    SongToUpload("Juice", "Lizzo", 2019),
    SongToUpload("Jump", "Kris Kross", 1992)
)