package com.isrbet.cottagegames

import android.os.Bundle
import android.util.TypedValue
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import com.isrbet.cottagegames.databinding.FragmentNameThatTuneBinding
import com.spotify.protocol.types.ImageUri
import timber.log.Timber


class NameThatTuneFragment : Fragment() {
    private var _binding: FragmentNameThatTuneBinding? = null
    private val binding get() = _binding!!
    private val args: NameThatTuneFragmentArgs by navArgs()
    private var startFromBeginning = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNameThatTuneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (args.trackURI != "") {
//            TrackViewModel.setIndOfLastPlayed(args.trackOrderInd)
            MyApplication.currentGameURI = args.trackURI
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        TrackViewModel.setMyFilter(TracksFilter.NameThatTune)
        (activity as AppCompatActivity?)!!.supportActionBar!!.title = getString(R.string.title_name_that_tune)

        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menu.clear()
                menuInflater.inflate(R.menu.name_that_tune_options_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                Timber.tag("Alex").d("in onMenuItemSelected")
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.decrease_text_size -> {
                        changeTextSize(-2)
                        true
                    }

                    R.id.increase_text_size -> {
                        changeTextSize(2)
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

//        pausePlay()
        binding.playButton.setOnClickListener {
            clickedPlay()
        }
        binding.replayButton.setOnClickListener {
            replay()
        }
        binding.playNextButton.setOnClickListener {
            playNext()
        }
        binding.startGameButton.setOnClickListener{
            startNewGame()
        }
        Timber.tag("Alex").d("currentGameURI is '${MyApplication.currentGameURI}'")
        if (MyApplication.currentGameURI == "") { // first time here
            binding.trackTitle.text = ""
            binding.trackArtist.text = ""
            binding.lyrics.text = ""
            binding.playButton.visibility = View.GONE
            binding.playNextButton.visibility = View.GONE
            binding.replayButton.visibility = View.GONE
        } else {
            binding.startGameButton.visibility = View.GONE
            val track = TrackViewModel.getTrack(MyApplication.currentGameURI)
            if (track == null)
                pausePlay()
            else {
                binding.trackTitle.text = track.songName
                binding.trackArtist.text = track.artistName
                binding.lyrics.text = track.getSpannedLyrics()
                val imageUri = ImageUri(track.imageUri)
                if (SpotifyService.isConnected()) {
                    SpotifyService.getImage(requireContext(), imageUri) { bitmap ->
                        binding.trackImage.setImageBitmap(bitmap)
                    }
                }
                if (!SpotifyService.amPlayingThis(MyApplication.currentGameURI)) {
                    pausePlay()
                    play()
                }
            }
        }
        if (!(activity as MainActivity).isSpotifyInstalled()) {
            binding.trackArtist.text = getString(R.string.there_is_no_spotify_service_available)
            binding.buttonLayout.visibility = View.GONE
        }
    }

    private fun pausePlay() {
        startFromBeginning = false
        setPlayButtonImage(false)
        SpotifyService.pause()
    }

    private fun resumePlay() {
        startFromBeginning = false
        setPlayButtonImage(true)
        SpotifyService.resume()
    }

    private fun replay() {
        startFromBeginning = true
        setPlayButtonImage(true)
        SpotifyService.replay()
    }

    private fun play() {
        setPlayButtonImage(true)
        if (MyApplication.currentGameURI == "")
            playNext()
        SpotifyService.play(MyApplication.currentGameURI)
    }
    private fun clickedPlay() {
        if (gGameUnderway) {
            SpotifyService.getPlayingState(requireContext()) {
                when (it) {
                    PlayingState.PLAYING -> pausePlay()
                    PlayingState.STOPPED -> playNext()
                    PlayingState.PAUSED -> {
                        val myTrack = TrackViewModel.getTrack(MyApplication.currentGameURI)
                        binding.trackTitle.text = myTrack?.songName
                        binding.trackArtist.text = myTrack?.artistName
                        if (myTrack == null)
                            binding.lyrics.text = ""
                        else {
                            binding.lyrics.text = myTrack.getSpannedLyrics()
                        }
                        if (SpotifyService.amPlayingThis(MyApplication.currentGameURI) && !startFromBeginning)
                            resumePlay()
                        else {
                            setPlayButtonImage(true)
                            SpotifyService.play(MyApplication.currentGameURI)
                        }
                    }
                }
            }
        } else {
            play()
            gGameUnderway = true
            setPlayButtonImage(true)
        }
    }

    private fun playNext() {
        val myTrack = TrackViewModel.getNextTrackForNameThatTune()
        Timber.tag("Alex").d("in playNext got $myTrack")
        pausePlay()
        if (myTrack == null)
            MyApplication.currentGameURI = ""
        else
            MyApplication.currentGameURI = myTrack.uri
        startFromBeginning = true
        binding.trackTitle.text = myTrack?.songName
        binding.trackArtist.text = myTrack?.artistName
        if (myTrack == null)
            binding.lyrics.text = ""
        else {
            binding.lyrics.text = myTrack.getSpannedLyrics()
        }
        val imageUri = ImageUri(myTrack.imageUri)
        if (SpotifyService.isConnected()) {

            SpotifyService.getImage(requireContext(), imageUri) { bitmap ->
                binding.trackImage.setImageBitmap(bitmap)
            }
        }
        //        MyApplication.playSound(context, SoundAction.NEXT_SONG)
    }

    private fun setPlayButtonImage(iPlaying: Boolean) {
        if (iPlaying) {
            binding.playButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_pause_24))
            binding.playButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
        } else {
            binding.playButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_play_arrow_24))
            binding.playButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green))
        }
    }

    private fun changeTextSize(iDirection: Int) {
        val currentTextSize = binding.lyrics.textSize
        binding.lyrics.setTextSize(TypedValue.COMPLEX_UNIT_PX, currentTextSize + iDirection.toFloat())
    }

    private fun startNewGame() {
        gGameUnderway = true
        pausePlay()
        binding.trackTitle.text = ""
        binding.trackArtist.text = ""
        binding.lyrics.text = ""
        binding.playButton.visibility = View.VISIBLE
        binding.playNextButton.visibility = View.VISIBLE
        binding.replayButton.visibility = View.VISIBLE
        binding.startGameButton.visibility = View.GONE
        binding.trackImage.setImageBitmap(null)
        MyApplication.currentGameURI = ""
//        MyApplication.gameHasStarted = false
        TrackViewModel.shuffleForNameThatTune()
        playNext()
        MyApplication.playSound(SoundAction.START_GAME)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        MyApplication.currentGameURI = ""
        _binding = null
    }
}