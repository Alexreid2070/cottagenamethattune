package com.android.isrbet.cottagenamethattune

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.android.isrbet.cottagenamethattune.databinding.FragmentHomeBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.spotify.protocol.types.ImageUri
import com.squareup.picasso.Picasso


class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val args: HomeFragmentArgs by navArgs()
    private var startFromBeginning = true
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)

        val mainActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data
                    val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                    try {
                        // Google Sign In was successful, authenticate with Firebase
                        val account = task.getResult(ApiException::class.java)!!
                        MyApplication.userName = account.displayName ?: ""
                        MyApplication.userEmail = account.email ?: ""
                        MyApplication.userPhotoURL = account.photoUrl.toString()
                        MyApplication.userAccount = account.account
                        firebaseAuthWithGoogle(account.idToken!!)
                    } catch (e: ApiException) {
                        // Google Sign In failed, update UI appropriately
                        Log.w("Alex", "Google sign in failed", e)
                    }
                } else
                    Log.d(
                        "Alex",
                        "in registerForActivityResult, result was not OK " + result.resultCode
                    )
            }

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
        binding.signInButton.setOnClickListener {
            onSignIn(mainActivityResultLauncher)
        }
        auth = Firebase.auth

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
        (activity as AppCompatActivity?)!!.supportActionBar!!.title = "Name That Tune!"
//        pausePlay()
        binding.playButton.setOnClickListener {
            clickedPlay()
        }
        binding.replayButton.setOnClickListener {
            replay()
        }
        binding.playPrevButton.setOnClickListener {
            playPrev()
        }
        binding.playNextButton.setOnClickListener {
            playNext()
        }
        binding.startGameButton.setOnClickListener{
            startNewGame()
        }
        if (MyApplication.currentGameURI == "") { // first time here
            binding.trackTitle.text = ""
            binding.trackArtist.text = ""
            binding.lyrics.text = ""
            binding.playButton.visibility = View.GONE
            binding.playNextButton.visibility = View.GONE
            binding.playPrevButton.visibility = View.GONE
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
                SpotifyService.getImage(requireContext(), imageUri) { bitmap ->
                    binding.trackImage.setImageBitmap(bitmap)
                }
                if (!SpotifyService.amPlayingThis(MyApplication.currentGameURI)) {
                    pausePlay()
                    play()
                }
            }
        }
        if (!(activity as MainActivity).isSpotifyInstalled()) {
            binding.trackArtist.text = "There is no Spotify service available"
            binding.buttonLayout.visibility = View.GONE
/*            binding.playButton.isEnabled = false
            binding.playButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.replayButton.isEnabled = false
            binding.playNextButton.isEnabled = false
            binding.playPrevButton.isEnabled = false */
        }

        // Check for existing Google Sign In account, if the user is already signed in
        // the GoogleSignInAccount will be non-null.
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account?.email == null) {
            // user is logged out
            (activity as MainActivity).setIsLoggedIn(false)
            binding.signInButton.visibility = View.VISIBLE
            binding.signInButton.setSize(SignInButton.SIZE_WIDE)
            binding.playButton.visibility = View.GONE
            binding.startGameButton.visibility = View.GONE
        } else {
            (activity as MainActivity).setIsLoggedIn(true)
            binding.signInButton.visibility = View.GONE
            binding.signInButton.visibility = View.VISIBLE
            if (!MyApplication.gameHasStarted) {
                binding.startGameButton.visibility = View.VISIBLE
            }
        }
        MyApplication.userAccount = account?.account
        if (account != null) {
            MyApplication.userPhotoURL = account.photoUrl.toString()
            setUserNameAndPhoto(MyApplication.userName, MyApplication.userEmail, MyApplication.userPhotoURL)
        }
        val currentUser = auth.currentUser
        signIn(currentUser)
        val mNavigationView = requireActivity().findViewById(R.id.navView) as NavigationView
        val mHeaderView = mNavigationView.getHeaderView(0)
        val signOutButton = mHeaderView.findViewById(R.id.signoutButton) as Button
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
        val mNavigationView = requireActivity().findViewById(R.id.navView) as NavigationView
        val mHeaderView = mNavigationView.getHeaderView(0)
        val textViewUsername = mHeaderView.findViewById(R.id.user_name) as TextView
        val textViewEmail = mHeaderView.findViewById(R.id.user_email) as TextView
        val userImage = mHeaderView.findViewById(R.id.user_image) as ImageView
        textViewUsername.text = iUserName
        textViewEmail.text = iUserEmail
        Picasso.with(requireContext()).load(iPhotoURL).into(userImage)
    }

    private fun onSignIn(mainActivityResultLauncher: ActivityResultLauncher<Intent>) {
        val signInIntent: Intent = mGoogleSignInClient.signInIntent
        mainActivityResultLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user = auth.currentUser
                    // this code is only hit when a user signs in successfully.
                    signIn(user)
                } else {
                    // If sign in fails, display a message to the user.
                    signIn(null)
                }
            }
    }

    private fun signIn(account: FirebaseUser?) {
        MyApplication.userEmail = account?.email.toString()
        if (account != null) {
            MyApplication.userEmail = account.email ?: ""
            MyApplication.userName = account.displayName ?: ""
            MyApplication.userPhotoURL = account.photoUrl.toString()
            setUserNameAndPhoto(MyApplication.userName, MyApplication.userEmail, MyApplication.userPhotoURL)
        }
        if (account == null) {
            (activity as MainActivity).setIsLoggedIn(false)
            binding.signInButton.visibility = View.VISIBLE
            binding.signInButton.setSize(SignInButton.SIZE_WIDE)
            binding.playButton.visibility = View.GONE
            binding.startGameButton.visibility = View.GONE
        } else {
            (activity as MainActivity).setIsLoggedIn(true)
            binding.signInButton.visibility = View.GONE
//            binding.playButton.visibility = View.VISIBLE
            if (!MyApplication.gameHasStarted) {
                binding.startGameButton.visibility = View.VISIBLE
            }
        }
    }

    private fun signOut() {
        Firebase.auth.signOut()
        mGoogleSignInClient.signOut()
        MyApplication.userName = ""
        MyApplication.userEmail = ""
        MyApplication.userPhotoURL = ""
        requireActivity().finishAffinity()
/*        binding.signInButton.visibility = View.VISIBLE
        binding.signInButton.setSize(SignInButton.SIZE_WIDE)
        binding.playButton.isEnabled = false
        val mNavigationView = requireActivity().findViewById(R.id.navView) as NavigationView
        val mHeaderView = mNavigationView.getHeaderView(0)
        (activity as MainActivity).closeDrawer() */
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
        if (MyApplication.gameHasStarted) {
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
            MyApplication.gameHasStarted = true
            setPlayButtonImage(true)
        }
    }

    private fun playPrev() {
//        setPlayButtonImage(true)
        var myTrackIndex = TrackViewModel.getTrackInd(MyApplication.currentGameURI)
        if (myTrackIndex == 0)
            myTrackIndex = TrackViewModel.getCount()-1
        else
            myTrackIndex -= 1
        val myTrack = TrackViewModel.getTrack(myTrackIndex)
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
        val imageUri = ImageUri(myTrack?.imageUri)
        SpotifyService.getImage(requireContext(), imageUri) { bitmap ->
            binding.trackImage.setImageBitmap(bitmap)
        }
//        MyApplication.playSound(context, SoundAction.PREV_SONG)
    }
    private fun playNext() {
//        setPlayButtonImage(true)
        var myTrackIndex = TrackViewModel.getTrackInd(MyApplication.currentGameURI)
        if (myTrackIndex == TrackViewModel.getCount()-1)
            myTrackIndex = 0
        else
            myTrackIndex += 1
        val myTrack = TrackViewModel.getTrack(myTrackIndex)
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
        val imageUri = ImageUri(myTrack?.imageUri)
        SpotifyService.getImage(requireContext(), imageUri) { bitmap ->
            binding.trackImage.setImageBitmap(bitmap)
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        for (i in 0 until menu.size()) {
            when (menu.getItem(i).itemId) {
                R.id.start_new_game,
                R.id.increase_text_size,
                R.id.decrease_text_size -> menu.getItem(i).isVisible = true
                else -> menu.getItem(i).isVisible = false
            }
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if ((activity as MainActivity).getIsLoggedIn()) {
            when (item.itemId) {
                R.id.start_new_game -> startNewGame()
                R.id.increase_text_size -> changeTextSize(2)
                R.id.decrease_text_size -> changeTextSize(-2)
            }
        } else
            Toast.makeText(activity, "You must Sign In before playing", Toast.LENGTH_LONG).show()
        return super.onOptionsItemSelected(item)
    }
    private fun changeTextSize(iDirection: Int) {
        val currentTextSize = binding.lyrics.textSize
        binding.lyrics.setTextSize(TypedValue.COMPLEX_UNIT_PX, currentTextSize + iDirection.toFloat())
    }

    private fun startNewGame() {
        pausePlay()
        binding.trackTitle.text = ""
        binding.trackArtist.text = ""
        binding.lyrics.text = ""
        binding.playButton.visibility = View.VISIBLE
        binding.playNextButton.visibility = View.VISIBLE
        binding.playPrevButton.visibility = View.VISIBLE
        binding.replayButton.visibility = View.VISIBLE
        binding.startGameButton.visibility = View.GONE
        binding.trackImage.setImageBitmap(null)
        MyApplication.currentGameURI = ""
//        MyApplication.gameHasStarted = false
        TrackViewModel.reshufflePlayOrder()
        playNext()
        MyApplication.playSound(context, SoundAction.START_GAME)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}