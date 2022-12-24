package com.android.isrbet.cottagenamethattune

import android.app.AlertDialog
import android.app.SearchManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.isrbet.cottagenamethattune.databinding.FragmentSongBinding
import com.spotify.protocol.types.ImageUri


class SongFragment : Fragment() {
    private var _binding: FragmentSongBinding? = null
    private val binding get() = _binding!!
    private val args: SongFragmentArgs by navArgs()
    private var addTrackMode: Boolean = true
    private var currentlyViewing = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        addTrackMode = args.trackURI == ""
        currentlyViewing = TrackViewModel.getTrackInd(args.trackURI)
//        TrackViewModel.setIndOfLastViewed(currentlyViewing)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongBinding.inflate(inflater, container, false)

        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.loadFromSpotifyButton.setOnClickListener {
            loadFromSpotify()
        }
        binding.searchForLyricsButton.setOnClickListener {
            searchForLyrics()
        }
        binding.cancelButton.setOnClickListener {
//            activity?.onBackPressed()
            setToViewMode()
        }
        binding.saveButton.setOnClickListener {
            saveTrack()
        }
        binding.prevButton.setOnClickListener {
            prevTrack()
        }
        binding.nextButton.setOnClickListener {
            nextTrack()
        }
        binding.playButton.setOnClickListener {
            playTrack()
        }
        if (addTrackMode) {
            (activity as AppCompatActivity?)!!.supportActionBar!!.title = "Add Song"
            binding.lyrics.setText("")
            binding.lyrics.visibility = View.GONE
            binding.forbiddenLyrics.setText("")
            binding.forbiddenLyricsTitle.visibility = View.GONE
            binding.forbiddenLyrics.visibility = View.GONE
            binding.loadFromSpotifyButton.visibility = View.VISIBLE
            binding.searchForLyricsButton.visibility = View.VISIBLE
            binding.searchForLyricsButton.isEnabled = false
            binding.cancelButton.visibility = View.GONE
            binding.saveButton.visibility = View.GONE
            binding.saveButton.isEnabled = false
            binding.prevButton.visibility = View.GONE
            binding.nextButton.visibility = View.GONE
            binding.playButton.visibility = View.GONE
            if (!(activity as MainActivity).isSpotifyInstalled()) {
                binding.trackArtist.setText("There is no Spotify service available")
                binding.buttonLayout.visibility = View.GONE
            } else {
                loadFromSpotify()
            }
        } else {
            (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View Song (${currentlyViewing+1}/${TrackViewModel.getCount()})"
            val myTrack = TrackViewModel.getTrack(currentlyViewing)
            if (myTrack != null) {
                displayTrack(myTrack)
            }
            setToViewMode()
        }
    }
    private fun displayTrack(myTrack: MyTrack) {
        binding.trackTitle.setText(myTrack.songName)
        binding.trackArtist.setText(myTrack.artistName)
        binding.uri.text = myTrack.uri
        binding.imageuri.text = myTrack.imageUri
        binding.lyrics.setText(myTrack.getSpannedLyrics())
        binding.forbiddenLyrics.setText(myTrack.getForbiddenLyrics())
        if (myTrack.imageUri == "") {
            binding.trackImage.setImageBitmap(null)
        } else {
            val imageUri = ImageUri(myTrack.imageUri)
            SpotifyService.getImage(requireContext(), imageUri) { bitmap ->
                binding.trackImage.setImageBitmap(bitmap)
            }
        }
        binding.playButton.visibility = View.VISIBLE
    }
    private fun setToEditMode() {
        binding.trackArtist.isEnabled = true
        binding.trackTitle.isEnabled = true
        binding.lyrics.isEnabled = true
        binding.forbiddenLyrics.isEnabled = true
        binding.saveButton.visibility = View.VISIBLE
        binding.saveButton.isEnabled = true
        binding.cancelButton.visibility = View.VISIBLE
        binding.cancelButton.isEnabled = true
        binding.prevButton.visibility = View.GONE
        binding.nextButton.visibility = View.GONE
        binding.searchForLyricsButton.visibility = View.GONE
        binding.playButton.visibility = View.GONE
        binding.trackTitle.setBackgroundColor(Color.YELLOW)
        binding.trackArtist.setBackgroundColor(Color.YELLOW)
        binding.lyrics.setBackgroundColor(Color.YELLOW)
        binding.forbiddenLyrics.setBackgroundColor(Color.YELLOW)
    }
    private fun setToViewMode() {
        binding.loadFromSpotifyButton.visibility = View.GONE
        binding.searchForLyricsButton.visibility = View.GONE
        binding.cancelButton.visibility = View.GONE
        binding.saveButton.visibility = View.GONE
        binding.prevButton.visibility = View.VISIBLE
        binding.nextButton.visibility = View.VISIBLE
        binding.playButton.visibility = View.VISIBLE
        binding.prevButton.isEnabled = true
        binding.nextButton.isEnabled = true
        binding.trackArtist.isEnabled = false
        binding.trackTitle.isEnabled = false
        binding.lyrics.visibility = View.VISIBLE
        binding.lyrics.isEnabled = false
        binding.forbiddenLyricsTitle.visibility = View.VISIBLE
        binding.forbiddenLyrics.visibility = View.VISIBLE
        binding.forbiddenLyrics.isEnabled = false
        binding.trackTitle.setBackgroundColor(Color.TRANSPARENT)
        binding.trackArtist.setBackgroundColor(Color.TRANSPARENT)
        binding.lyrics.setBackgroundColor(Color.TRANSPARENT)
        binding.forbiddenLyrics.setBackgroundColor(Color.TRANSPARENT)
    }
    private fun loadFromSpotify() {
        SpotifyService.getCurrentTrack(requireContext()) { track ->
            val myTrack = TrackViewModel.getTrack(track.uri, track.name, track.artist.name)
            if (myTrack != null) {
                displayTrack(myTrack)
                setToViewMode()
//                binding.lyrics.setText(myTrack.getSpannedLyrics())
  //              binding.forbiddenLyrics.setText(myTrack.getForbiddenLyrics())
                addTrackMode = false
                activity?.invalidateOptionsMenu()
                currentlyViewing = TrackViewModel.getTrackInd(myTrack.uri)
//                TrackViewModel.setIndOfLastViewed(currentlyViewing)
                (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View Song (${currentlyViewing+1}/${TrackViewModel.getCount()})"
            } else {
                binding.trackTitle.setText(track.name)
                binding.trackArtist.setText(track.artist.name)
                binding.uri.text = track.uri
                binding.imageuri.text = track.imageUri.raw
                SpotifyService.getImage(requireContext(), track.imageUri) { bitmap ->
                    binding.trackImage.setImageBitmap(bitmap)
                }
                binding.searchForLyricsButton.isEnabled = true
//                setToEditMode()
                binding.lyrics.setText("")
                binding.forbiddenLyrics.setText(track.name)
                (activity as AppCompatActivity?)!!.supportActionBar!!.title = "Add Song"
                binding.loadFromSpotifyButton.visibility = View.GONE
                searchForLyrics()
            }
        }
    }
    private fun searchForLyrics() {
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
        val term = "lyrics " + binding.trackTitle.text + " " + binding.trackArtist.text
        intent.putExtra(SearchManager.QUERY, term)
        startActivity(intent)
        binding.lyrics.visibility = View.VISIBLE
        binding.forbiddenLyricsTitle.visibility = View.VISIBLE
        binding.forbiddenLyrics.visibility = View.VISIBLE
        binding.saveButton.isEnabled = true
        setToEditMode()
    }
    private fun saveTrack() {
        var findString = "Overview\n\n"
        var prefix = binding.lyrics.text.toString().indexOf(findString)
        if (prefix != -1) {
            binding.lyrics.setText(binding.lyrics.text.toString().replace(findString,"", true))
        }
        findString = "Lyrics\n\n"
        prefix = binding.lyrics.text.toString().indexOf(findString)
        if (prefix != -1) {
            binding.lyrics.setText(binding.lyrics.text.toString().replace(findString,"", true))
        }
        findString = "Videos\n\n"
        prefix = binding.lyrics.text.toString().indexOf(findString)
        if (prefix != -1) {
            binding.lyrics.setText(binding.lyrics.text.toString().replace(findString,"", true))
        }
        findString = "Listen\n\n"
        prefix = binding.lyrics.text.toString().indexOf(findString)
        if (prefix != -1) {
            binding.lyrics.setText(binding.lyrics.text.toString().replace(findString,"", true))
        }
        findString = "Artists\n\n"
        prefix = binding.lyrics.text.toString().indexOf(findString)
        if (prefix != -1) {
            binding.lyrics.setText(binding.lyrics.text.toString().replace(findString,"", true))
        }
        findString = "Analysis\n\n"
        prefix = binding.lyrics.text.toString().indexOf(findString)
        if (prefix != -1) {
            binding.lyrics.setText(binding.lyrics.text.toString().replace(findString,"", true))
        }
        findString = "Main Results\n\n"
        prefix = binding.lyrics.text.toString().indexOf(findString)
        if (prefix != -1) {
            binding.lyrics.setText(binding.lyrics.text.toString().replace(findString,"", true))
        }
        findString = "Other recordings\n\n"
        prefix = binding.lyrics.text.toString().indexOf(findString)
        if (prefix != -1) {
            binding.lyrics.setText(binding.lyrics.text.toString().replace(findString,"", true))
        }
        if (binding.trackTitle.text.toString() == "") {
            binding.trackTitle.error = getString(R.string.field_is_required)
            focusAndOpenSoftKeyboard(requireContext(), binding.trackTitle)
            return
        }
        if (binding.trackArtist.text.toString() == "") {
            binding.trackArtist.error = getString(R.string.field_is_required)
            focusAndOpenSoftKeyboard(requireContext(), binding.trackArtist)
            return
        }
        if (binding.lyrics.text.toString() == "") {
            binding.lyrics.error = getString(R.string.field_is_required)
            focusAndOpenSoftKeyboard(requireContext(), binding.lyrics)
            return
        }
        if (binding.forbiddenLyrics.text.toString() == "") {
            binding.forbiddenLyrics.error = getString(R.string.field_is_required)
            focusAndOpenSoftKeyboard(requireContext(), binding.forbiddenLyrics)
            return
        }
        val lyrics: List<String> = binding.lyrics.text.trim().toString().split("\n").map { it.trim() }
        val lyricsML:MutableList<String> = ArrayList(lyrics)
        val forbiddenLyrics: List<String> = binding.forbiddenLyrics.text.trim().toString().split("\n").map { it.trim() }
        val forbiddenLyricsML:MutableList<String> = ArrayList(forbiddenLyrics)
        if (addTrackMode) {
            val myTrack = MyTrack(binding.trackTitle.text.toString(),
                binding.trackArtist.text.toString(),
                binding.uri.text.toString(),
                binding.imageuri.text.toString(),
                giveMeDate(),
                TrackViewModel.getCount(),
                lyricsML,
                forbiddenLyricsML)
            if (TrackViewModel.addTrack(myTrack)) {
                Toast.makeText(MyApplication.myMainActivity, "Track added!", Toast.LENGTH_SHORT).show()
//                binding.trackImage.setImageBitmap(null)
                MyApplication.playSound(context, SoundAction.ADD_SONG)
//                activity?.onBackPressed()
                setToViewMode()
                binding.lyrics.setText(myTrack.getSpannedLyrics())

                addTrackMode = false
                activity?.invalidateOptionsMenu()
                currentlyViewing = TrackViewModel.getTrackInd(myTrack.uri)
//                TrackViewModel.setIndOfLastViewed(currentlyViewing)
                (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View Song (${currentlyViewing+1}/${TrackViewModel.getCount()})"
            } else
                Toast.makeText(MyApplication.myMainActivity, "Track already exists in database, not added.", Toast.LENGTH_SHORT).show()
        } else {
            currentlyViewing = TrackViewModel.getTrackInd(binding.uri.text.toString())
            val currentTrack = TrackViewModel.getTrack(currentlyViewing)
            val newTrack = MyTrack(binding.trackTitle.text.toString(),
                binding.trackArtist.text.toString(),
                binding.uri.text.toString(),
                binding.imageuri.text.toString(),
                giveMeDate(),
                TrackViewModel.getCount(),
                lyricsML,
                forbiddenLyricsML)
            TrackViewModel.editTrack(currentTrack, newTrack)
            MyApplication.playSound(context, SoundAction.EDIT_SONG)
//                activity?.onBackPressed()
            setToViewMode()
            binding.lyrics.setText(newTrack.getSpannedLyrics())
            currentlyViewing = TrackViewModel.afterSave(newTrack.uri)
        }
    }
    private fun prevTrack() {
        if (currentlyViewing > 0)
            currentlyViewing -= 1
        else
            currentlyViewing = TrackViewModel.getCount()-1
        val myTrack = TrackViewModel.getTrack(currentlyViewing)
        if (myTrack != null) {
            displayTrack(myTrack)
        }
        (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View Song (${currentlyViewing+1}/${TrackViewModel.getCount()})"
//        MyApplication.playSound(context, SoundAction.PREV_SONG)
    }
    private fun nextTrack() {
        if (currentlyViewing < TrackViewModel.getCount()-1)
            currentlyViewing += 1
        else
            currentlyViewing = 0
        val myTrack = TrackViewModel.getTrack(currentlyViewing)
        if (myTrack != null) {
            displayTrack(myTrack)
        }
        (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View Song (${currentlyViewing+1}/${TrackViewModel.getCount()})"
//        MyApplication.playSound(context, SoundAction.NEXT_SONG)
    }
    private fun playTrack() {
//        val ind = TrackViewModel.getTrackInd(binding.uri.text.toString())
        val action =
            SongFragmentDirections.actionSongFragmentToHomeFragment()
                .setTrackURI(binding.uri.text.toString())
        this@SongFragment.findNavController().navigate(action)
    }
    private fun editTrack() {
        setToEditMode()
        if (binding.forbiddenLyrics.text.toString() == "")
            binding.forbiddenLyrics.text = binding.trackTitle.text
    }
    private fun deleteTrack() {
        fun yesClicked() {
            val myTrack = TrackViewModel.getTrack(binding.uri.text.toString()) ?: return
            TrackViewModel.deleteTrack(myTrack)
            Toast.makeText(activity, getString(R.string.song_deleted), Toast.LENGTH_SHORT).show()
            MyApplication.playSound(context, SoundAction.DELETE_SONG)
            requireActivity().onBackPressed()
            currentlyViewing = TrackViewModel.afterSave(myTrack.uri)
        }
        fun noClicked() {
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.are_you_sure))
            .setMessage(getString(R.string.are_you_sure_that_you_want_to_delete_this_item_NP))
            .setPositiveButton(android.R.string.ok) { _, _ -> yesClicked() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> noClicked() }
            .show()

    }
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        for (i in 0 until menu.size()) {
            if (addTrackMode) {
                menu.getItem(i).isVisible = false
            } else {
                when (menu.getItem(i).itemId) {
                    R.id.edit, R.id.delete -> menu.getItem(i).isVisible = true
                    else -> menu.getItem(i).isVisible = false
                }
            }
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.edit -> editTrack()
            R.id.delete -> deleteTrack()
        }
        return super.onOptionsItemSelected(item)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}