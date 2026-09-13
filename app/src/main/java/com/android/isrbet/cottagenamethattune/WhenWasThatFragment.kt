package com.android.isrbet.cottagenamethattune

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.android.isrbet.cottagenamethattune.databinding.FragmentWhenWasThatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size
import timber.log.Timber
import kotlin.collections.MutableList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.util.TypedValue
import android.widget.ProgressBar
import androidx.core.view.children
import androidx.core.view.isEmpty
import androidx.core.widget.TextViewCompat
import java.time.Year
import kotlin.text.toInt

enum class WhenWasThatGameState {
    NO_GAME_POSSIBLE, NO_GAME_STARTED, BEGINNING_OF_TURN, NEED_TO_COMMIT, OPPORTUNITY_FOR_CHALLENGE, EARNING_COINS, GAME_OVER
}

enum class PlayMode {
    FromStart,
    Random
}

var gTimeToPlay = 10
var gTracksToWin = 10
var gPlayMode = PlayMode.FromStart
var gYearRangeMinimum = 1930
var gYearRangeMaximum = Year.now().value
var gAllowDuplicates = true

class WhenWasThatFragment : Fragment() {
    private var _binding: FragmentWhenWasThatBinding? = null
    private val binding get() = _binding!!
    private var gameState: WhenWasThatGameState = WhenWasThatGameState.NO_GAME_STARTED
    private lateinit var gameTrack: MyTrack
    private var team1Coins: Int = 0
    private var team1Tracks: MutableList<MyTrack> = ArrayList()
    private var bankPulser: ObjectAnimator? = null
    lateinit var frontDeck: TextView
    lateinit var backDeck: TextView
    private var whoGetsCard = 0

    private var team2Coins: Int = 0
    private var team2Tracks: MutableList<MyTrack> = ArrayList()

    private var currentTeam = 1
    private var currentSlot = -1
    private var currentSlotView: ImageView? = null
    private var nextTrack: Int = 0
    var deckBackIsOnTop = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle back press or backward swipe gesture
        activity?.onBackPressedDispatcher?.addCallback(this) {
            // in here you can do logic when backPress is clicked
            Timber.tag("Alex").d("onBackPressed")
            if (gGameUnderway) { // this means a game is underway
                val builder = AlertDialog.Builder(requireContext())

                builder.setTitle("Confirm Action")
                builder.setMessage("Are you sure you want to quit this game?")

                // Set positive/confirmation button
                builder.setPositiveButton("Confirm") { dialog, _ ->
                    // Execute your confirmation logic here
                    dialog.dismiss()
                    parentFragmentManager.popBackStack()
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
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        activity?.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        _binding = FragmentWhenWasThatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.yearRangeText.text =
            "${String.format(getString(R.string.year_range))}$gYearRangeMinimum to $gYearRangeMaximum"
        binding.yearRangeSlider.setValues(gYearRangeMinimum.toFloat(), gYearRangeMaximum.toFloat())
        binding.yearRangeSlider.addOnChangeListener { slider, _, _ ->
            val selectedValues = slider.values
            gYearRangeMinimum = selectedValues[0].toInt()
            gYearRangeMaximum = selectedValues[1].toInt()
            if (gYearRangeMaximum < gYearRangeMinimum + gTracksToWin) {
                if (gYearRangeMinimum + gTracksToWin > Year.now().value) {
                    gYearRangeMaximum = Year.now().value
                    gYearRangeMinimum = gYearRangeMaximum - gTracksToWin + 1
                } else
                    gYearRangeMaximum = gYearRangeMinimum + gTracksToWin
            }
            binding.yearRangeText.text =
                "${String.format(getString(R.string.year_range))}$gYearRangeMinimum to $gYearRangeMaximum"
        }

        TrackViewModel.setMyFilter(TracksFilter.WhenWasThat)
        val colorp = ContextCompat.getColor(requireContext(), R.color.purple_500)
        binding.deckBack.backgroundTintList = ColorStateList.valueOf(colorp)
        binding.deckBack.setTextColor(ContextCompat.getColor(requireContext(),R.color.white))
        binding.deckBack.text = "START NEW GAME"

        (activity as AppCompatActivity?)!!.supportActionBar!!.title =
            getString(R.string.title_when_was_that)

        binding.durationOfGame.text = "10"
        binding.gameDurationAddButton.setOnClickListener {
            val current = binding.durationOfGame.text.toString().toInt()
            if (current < 30) {
                binding.durationOfGame.text = (current + 1).toString()
            }
        }

        binding.gameDurationSubtractButton.setOnClickListener {
            val current = binding.durationOfGame.text.toString().toInt()
            if (current > 2) {
                binding.durationOfGame.text = (current - 1).toString()
            }
        }

        binding.durationOfSong.text = "10"
        binding.songDurationAddButton.setOnClickListener {
            val current = binding.durationOfSong.text.toString().toInt()
            if (current < 60) {
                binding.durationOfSong.text = (current + 1).toString()
            }
        }

        binding.songDurationSubtractButton.setOnClickListener {
            val current = binding.durationOfSong.text.toString().toInt()
            if (current > 2) {
                binding.durationOfSong.text = (current - 1).toString()
            }
        }

        binding.yesCoin1.setOnClickListener {
            clickedYesCoin()
        }
        binding.yesCoin2.setOnClickListener {
            clickedYesCoin()
        }

        binding.noCoin1.setOnClickListener {
            clickedNoCoin()
        }
        binding.noCoin2.setOnClickListener {
            clickedNoCoin()
        }
        binding.buyACardButton1.setOnClickListener {
            clickedBuyACard()
        }
        binding.buyACardButton2.setOnClickListener {
            clickedBuyACard()
        }
        binding.commitButton1.setOnClickListener {
            clickedCommit()
        }
        binding.commitButton2.setOnClickListener {
            clickedCommit()
        }
        binding.passButton1.setOnClickListener {
            clickedPass()
        }
        binding.passButton2.setOnClickListener {
            clickedPass()
        }
        binding.challengeButton1.setOnClickListener {
            clickedChallenge()
        }
        binding.challengeButton2.setOnClickListener {
            clickedChallenge()
        }
        binding.noChallengeButton1.setOnClickListener {
            clickedNoChallenge()
        }
        binding.noChallengeButton2.setOnClickListener {
            clickedNoChallenge()
        }
        binding.deckFront.setOnClickListener {
            if (gameState == WhenWasThatGameState.BEGINNING_OF_TURN)
                clickedPlay()
            else if (gameState == WhenWasThatGameState.NEED_TO_COMMIT)
                SpotifyService.resume()
        }
        binding.startButton.setOnClickListener {
            clickedStartNewGame()
        }
        binding.deckBack.setOnClickListener {
            when (gameState) {
                WhenWasThatGameState.NO_GAME_STARTED -> clickedStartNewGame()
                WhenWasThatGameState.BEGINNING_OF_TURN -> clickedPlay()
                WhenWasThatGameState.NEED_TO_COMMIT -> SpotifyService.resume()
                else -> null
            }
        }

        if (!(activity as MainActivity).isSpotifyInstalled()) {
            binding.userMessage1.visibility = View.VISIBLE
            binding.userMessage1.text = "There is no Spotify service available"
            gameState = WhenWasThatGameState.NO_GAME_POSSIBLE
        }
        updateButtons()
    }

    private fun clickedYesCoin() {
        if (currentTeam == 1) {
            binding.yesCoin1.isEnabled = false
            binding.noCoin1.visibility = View.INVISIBLE
            binding.yesCoin1.visibility = View.INVISIBLE
        } else {
            binding.yesCoin2.isEnabled = false
            binding.noCoin2.visibility = View.INVISIBLE
            binding.yesCoin2.visibility = View.INVISIBLE
        }
        earnCoin(currentTeam, false)
        deliverCard(whoGetsCard, true) {
            gameState = WhenWasThatGameState.BEGINNING_OF_TURN
            currentTeam = if (currentTeam == 1)
                2
            else
                1
            refreshScreen()
//            setCardFrames()
        }
    }

    private fun clickedNoCoin() {
        if (currentTeam == 1) {
            binding.noCoin1.isEnabled = false
        } else {
            binding.noCoin2.isEnabled = false
        }
        Timber.tag("Alex").d("activateCoinBank 1")
        activateCoinBank(false)
        deliverCard(whoGetsCard, true) {
            gameState = WhenWasThatGameState.BEGINNING_OF_TURN
            currentTeam = if (currentTeam == 1)
                2
            else
                1
            refreshScreen()
//            setCardFrames()
        }
    }
    private fun updateButtons() {
        Timber.tag("Alex").d("gameState is $gameState")
        if (gameState == WhenWasThatGameState.NO_GAME_POSSIBLE) {
            binding.deckBack.isEnabled = false
            binding.deckFront.isEnabled = false
            binding.buyACardButton1.visibility = View.GONE
            binding.buyACardButton2.visibility = View.GONE
            binding.passButton1.visibility = View.INVISIBLE
            binding.passButton2.visibility = View.INVISIBLE
            binding.challengeButton1.visibility = View.GONE
            binding.challengeButton2.visibility = View.GONE
            binding.noChallengeButton1.visibility = View.GONE
            binding.noChallengeButton2.visibility = View.GONE
            binding.progressCommitLayout1.visibility = View.GONE
            binding.progressCommitLayout2.visibility = View.GONE
//            binding.commitButton1.visibility = View.GONE
  //          binding.commitButton2.visibility = View.GONE
    //        binding.progressBar1.visibility = View.GONE
      //      binding.progressBar2.visibility = View.GONE
        } else if (gameState == WhenWasThatGameState.NO_GAME_STARTED) {
            binding.deckBack.isEnabled = true
            binding.deckFront.isEnabled = false
            binding.buyACardButton1.visibility = View.GONE
            binding.buyACardButton2.visibility = View.GONE
            binding.passButton1.visibility = View.INVISIBLE
            binding.passButton2.visibility = View.INVISIBLE
            binding.challengeButton1.visibility = View.GONE
            binding.challengeButton2.visibility = View.GONE
            binding.noChallengeButton1.visibility = View.GONE
            binding.noChallengeButton2.visibility = View.GONE
            binding.progressCommitLayout1.visibility = View.GONE
            binding.progressCommitLayout2.visibility = View.GONE
//            binding.commitButton1.visibility = View.GONE
  //          binding.commitButton2.visibility = View.GONE
    //        binding.progressBar1.visibility = View.GONE
      //      binding.progressBar2.visibility = View.GONE
        } else if (gameState == WhenWasThatGameState.BEGINNING_OF_TURN) {
            val colorp = ContextCompat.getColor(requireContext(), R.color.purple_500)
            val coloro = ContextCompat.getColor(requireContext(), R.color.orange)
            if (deckBackIsOnTop) {
                binding.deckBack.backgroundTintList = ColorStateList.valueOf(colorp)
//                binding.deckBack.setTextColor(resources.getColor(R.color.white))
                binding.deckBack.text = ""
//                binding.deckBack.text = "PLAY SONGa"
                binding.deckBack.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.outline_music_note_2_24
                )
            } else {
                binding.deckFront.backgroundTintList = ColorStateList.valueOf(colorp)
                binding.deckFront.setTextColor(ContextCompat.getColor(requireContext(),R.color.white))
//                binding.deckFront.text = "PLAY SONGb"
                binding.deckFront.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.outline_music_note_2_24
                )
                binding.deckBack.backgroundTintList = ColorStateList.valueOf(coloro)
                binding.deckBack.setTextColor(ContextCompat.getColor(requireContext(),R.color.black))
            }
            binding.passButton1.visibility = View.INVISIBLE
            binding.passButton2.visibility = View.INVISIBLE
            binding.progressCommitLayout1.visibility = View.GONE
            binding.progressCommitLayout2.visibility = View.GONE
//            binding.commitButton1.visibility = View.GONE
  //          binding.commitButton2.visibility = View.GONE
//            binding.progressBar1.visibility = View.GONE
  //          binding.progressBar2.visibility = View.GONE
            binding.challengeButton1.visibility = View.GONE
            binding.challengeButton2.visibility = View.GONE
            binding.noChallengeButton1.visibility = View.GONE
            binding.noChallengeButton2.visibility = View.GONE
            binding.deckBack.isEnabled = true
            binding.deckFront.isEnabled = true
            if (currentTeam == 1) {
                if (team1Coins >= 3)
                    binding.buyACardButton1.visibility = View.VISIBLE
                else
                    binding.buyACardButton1.visibility = View.GONE
            } else {
                if (team2Coins >= 3)
                    binding.buyACardButton2.visibility = View.VISIBLE
                else
                    binding.buyACardButton2.visibility = View.GONE
            }
        } else if (gameState == WhenWasThatGameState.NEED_TO_COMMIT) {
            val coloro = ContextCompat.getColor(requireContext(), R.color.orange)
            if (deckBackIsOnTop) {
                binding.deckBack.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.card
                )
                binding.deckBack.backgroundTintList = ColorStateList.valueOf(coloro)
                binding.deckBack.setTextColor(ContextCompat.getColor(requireContext(),R.color.black))
                binding.deckBack.text = "???"
                binding.deckFront.backgroundTintList = ColorStateList.valueOf(coloro)
                binding.deckFront.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                binding.deckFront.text = "song"
                Timber.tag("Alex")
                    .d("d $deckBackIsOnTop frontDeck text is now ${binding.deckFront.text} and back is ${binding.deckBack.text}")
            } else {
                binding.deckFront.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.card
                )
                binding.deckFront.backgroundTintList = ColorStateList.valueOf(coloro)
                binding.deckFront.setTextColor(ContextCompat.getColor(requireContext(),R.color.black))
                binding.deckFront.text = "???"
                binding.deckBack.backgroundTintList = ColorStateList.valueOf(coloro)
                binding.deckBack.setTextColor(ContextCompat.getColor(requireContext(),R.color.black))
                binding.deckBack.text = "song"
            }

            binding.buyACardButton1.visibility = View.GONE
            binding.buyACardButton2.visibility = View.GONE
            if (currentTeam == 1) {
                binding.passButton1.visibility = View.VISIBLE
                binding.progressCommitLayout1.visibility = View.VISIBLE
                binding.commitButton1.visibility = View.VISIBLE
                val colorp = if (currentSlot == -1)
                    ContextCompat.getColor(requireContext(), R.color.disabled_color)
                else
                    ContextCompat.getColor(requireContext(), R.color.purple_500)
                binding.commitButton1.backgroundTintList = ColorStateList.valueOf(colorp)
                binding.commitButton1.isEnabled = (currentSlot != -1)
                binding.passButton1.isEnabled = team1Coins >= 1
            } else {
                binding.passButton2.visibility = View.VISIBLE
                binding.progressCommitLayout2.visibility = View.VISIBLE
                binding.commitButton2.visibility = View.VISIBLE
                val colorp = if (currentSlot == -1)
                    ContextCompat.getColor(requireContext(), R.color.disabled_color)
                else
                    ContextCompat.getColor(requireContext(), R.color.purple_500)
                binding.commitButton2.backgroundTintList = ColorStateList.valueOf(colorp)
                binding.commitButton2.isEnabled = (currentSlot != -1)
                binding.passButton2.isEnabled = team2Coins >= 1
            }
        } else if (gameState == WhenWasThatGameState.OPPORTUNITY_FOR_CHALLENGE) {
            binding.deckBack.isEnabled = false
            binding.deckFront.isEnabled = false
            binding.progressCommitLayout1.visibility = View.GONE
            binding.progressCommitLayout2.visibility = View.GONE
//            binding.progressBar1.visibility = View.GONE
  //          binding.progressBar2.visibility = View.GONE
    //        binding.commitButton1.visibility = View.GONE
      //      binding.commitButton2.visibility = View.GONE
            binding.passButton1.visibility = View.INVISIBLE
            binding.passButton2.visibility = View.INVISIBLE
            binding.buyACardButton1.visibility = View.GONE
            binding.buyACardButton2.visibility = View.GONE
            if (currentTeam == 1) {
                Timber.tag("Alex").d("if...number of coins is $team2Coins for team 2")
                if (team2Coins > 0) {
                    binding.challengeButton2.visibility = View.VISIBLE
                    binding.noChallengeButton2.visibility = View.VISIBLE
                } else {
                    clickedNoChallenge()
                }
            } else {
                Timber.tag("Alex").d("else...number of coins is $team1Coins for team 1")
                if (team1Coins > 0) {
                    binding.challengeButton1.visibility = View.VISIBLE
                    binding.noChallengeButton1.visibility = View.VISIBLE
                } else {
                    clickedNoChallenge()
                }
            }
        } else if (gameState == WhenWasThatGameState.EARNING_COINS) {
            binding.deckBack.isEnabled = false
            binding.deckFront.isEnabled = false
            binding.progressCommitLayout1.visibility = View.GONE
            binding.progressCommitLayout1.visibility = View.GONE
//            binding.commitButton1.visibility = View.GONE
  //          binding.commitButton2.visibility = View.GONE
    //        binding.progressBar1.visibility = View.GONE
      //      binding.progressBar2.visibility = View.GONE
            binding.passButton1.visibility = View.INVISIBLE
            binding.passButton2.visibility = View.INVISIBLE
            binding.buyACardButton1.visibility = View.GONE
            binding.buyACardButton2.visibility = View.GONE
        } else {
            binding.deckBack.isEnabled = false
            binding.deckFront.isEnabled = false
            binding.buyACardButton1.visibility = View.GONE
            binding.buyACardButton2.visibility = View.GONE
            binding.passButton1.visibility = View.INVISIBLE
            binding.passButton2.visibility = View.INVISIBLE
            binding.challengeButton1.visibility = View.GONE
            binding.challengeButton2.visibility = View.GONE
            binding.noChallengeButton1.visibility = View.GONE
            binding.noChallengeButton2.visibility = View.GONE
            binding.progressCommitLayout1.visibility = View.GONE
            binding.progressCommitLayout1.visibility = View.GONE
//            binding.commitButton1.visibility = View.GONE
  //          binding.commitButton2.visibility = View.GONE
//            binding.progressBar1.visibility = View.GONE
  //          binding.progressBar2.visibility = View.GONE
            binding.deckFront.visibility = View.GONE
            binding.deckBack.visibility = View.GONE
            binding.view1.visibility = View.GONE
            binding.view2.visibility = View.GONE
            val um = if (team1Tracks.size >= gTracksToWin) 1 else 2
            if (um == 1) {
                binding.userMessage1.visibility = View.VISIBLE
                binding.userMessage1.textSize = 20f
                binding.userMessage1.text =
                    "Game Over. Congratulations Team 1!!"
            } else {
                binding.userMessage2.visibility = View.VISIBLE
                binding.userMessage2.textSize = 20f
                binding.userMessage2.text =
                    "Game Over. Congratulations Team 2!!"
            }
            MyApplication.playSound(SoundAction.GAME_OVER)
            binding.konfettiView.bringToFront()
            binding.konfettiView.build()
                .addColors(Color.RED, Color.CYAN, Color.GREEN, Color.YELLOW)
                .setDirection(0.0, 359.0)
                .setSpeed(1f, 6f)
                .setFadeOutEnabled(true)
                .setTimeToLive(5000L)
                .addShapes(Shape.Square, Shape.Circle)
                .addSizes(Size(12))
                .setPosition(
                    -50f,
                    binding.konfettiView.width + 150f,
                    -50f,
                    binding.konfettiView.height + 150f
                )
                .streamFor(300, 2000L)
        }
    }

    private fun deactivateSlots(iMakeInvisible: Boolean = true) {
        val parentLayout = if (currentTeam == 1) binding.cardLayout1 else binding.cardLayout2
        var c = 0
        for (child in parentLayout.children) {
            if (c % 2 == 0) // every second View is a slot
                deactivateSpecificSlot(child as ImageView, iMakeInvisible)
            c += 1
        }

        currentSlotView?.let { it.imageTintList = ColorStateList.valueOf(Color.GREEN) }
    }

    private fun deactivateSpecificSlot(slot: ImageView, iMakeInvisible: Boolean = true) {
        if (iMakeInvisible) {
            slot.visibility = View.INVISIBLE
            slot.imageTintList = ColorStateList.valueOf(Color.GRAY)
        }
        slot.isEnabled = false
    }

    private fun refreshSlots() {
        if (gameState != WhenWasThatGameState.NEED_TO_COMMIT) {
            deactivateSlots()
            return
        }

        val parentLayout = if (currentTeam == 1) binding.cardLayout1 else binding.cardLayout2
        var c = 0
        for (child in parentLayout.children) {
            if (c % 2 == 0) // every second View is a slot
                activateSpecificSlot(child as ImageView)
            c += 1
        }
    }

    private fun activateSpecificSlot(slot: ImageView) {
        slot.visibility = View.VISIBLE
        slot.isEnabled = true
    }

    private fun showCoins(iTeamNumber: Int) {
        Timber.tag("Alex").d("in ShowCoints with $iTeamNumber")
        if (iTeamNumber == 1) {
            setSpecificCoinVisibility(binding.coin11, team1Coins >= 1)
            setSpecificCoinVisibility(binding.coin12, team1Coins >= 2)
            setSpecificCoinVisibility(binding.coin13, team1Coins >= 3)
            setSpecificCoinVisibility(binding.coin14, team1Coins >= 4)
            setSpecificCoinVisibility(binding.coin15, team1Coins >= 5)
        } else {
            setSpecificCoinVisibility(binding.coin21, team2Coins >= 1)
            setSpecificCoinVisibility(binding.coin22, team2Coins >= 2)
            setSpecificCoinVisibility(binding.coin23, team2Coins >= 3)
            setSpecificCoinVisibility(binding.coin24, team2Coins >= 4)
            setSpecificCoinVisibility(binding.coin25, team2Coins >= 5)
        }
    }

    private fun setSpecificCoinVisibility(iCoin: ImageView, iVisible: Boolean) {
        if (iVisible)
            iCoin.visibility = View.VISIBLE
        else
            iCoin.visibility = View.GONE
    }

    private fun refreshCards(iTeam: Int) {
        val parentLayout = if (iTeam == 1) binding.cardLayout1 else binding.cardLayout2
        var c = 0
        for (child in parentLayout.children) {
            if (c % 2 != 0) // every second View is a card
                child.visibility = View.VISIBLE
            c += 1
        }
    }

    private fun flipCard(
        deliverCard: Boolean,
        whoGetsIt: Int,
        slideDeck: Boolean,
        nextAction: () -> Unit
    ) {
        val frontAnimation: AnimatorSet =
            AnimatorInflater.loadAnimator(context, R.animator.front_animator) as AnimatorSet
        val backAnimation: AnimatorSet =
            AnimatorInflater.loadAnimator(context, R.animator.back_animator) as AnimatorSet

        val scale = context?.resources?.displayMetrics?.density
        if (scale != null) {
            binding.deckFront.cameraDistance = 8000 * scale
            binding.deckBack.cameraDistance = 8000 * scale
        }

        lateinit var firstAnimation: AnimatorSet
        lateinit var secondAnimation: AnimatorSet

        binding.deckFront.visibility = View.VISIBLE
        if (deckBackIsOnTop) {
            firstAnimation = backAnimation
            secondAnimation = frontAnimation
            frontDeck = binding.deckFront
            backDeck = binding.deckBack
            deckBackIsOnTop = false
        } else {
            firstAnimation = backAnimation
            secondAnimation = frontAnimation
            frontDeck = binding.deckBack
            backDeck = binding.deckFront
            deckBackIsOnTop = true
        }

        frontDeck.text = "${gameTrack.releaseYear}\n${gameTrack.artistName}\n${gameTrack.songName}"
        // Enable uniform auto-sizing with default settings (Min: 12sp, Max: 112sp, Granularity: 1px)
   //     TextViewCompat.setAutoSizeTextTypeWithDefaults(frontDeck, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            frontDeck,
            12,          // minSize
            30,          // maxSize
            2,           // stepGranularity
            TypedValue.COMPLEX_UNIT_SP // text unit type
        )
        firstAnimation.setTarget(frontDeck)
        secondAnimation.setTarget(backDeck)

        firstAnimation.start()
        secondAnimation.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                if (deliverCard) {
                    deliverCard(whoGetsIt, slideDeck, nextAction)
                } else {
                    nextAction()
                }
            }
        })
        secondAnimation.start()
    }

    private fun deliverCard(whoGetsIt: Int, slideDeck: Boolean, nextAction: () -> Unit) {
        val deckx = binding.deckFront.x
        val decky = binding.deckFront.y
        val targetLocation =
            when (whoGetsIt) {
                1 -> binding.team1Title
                2 -> binding.team2Title
                else -> binding.trash
            }
//        setTeamTitles()
        // Step 2: Clear listener and move to new location
        frontDeck.animate()
            .scaleX( 0.6F)
            .scaleY(0.6F)
            .x(targetLocation.x)
            .y(targetLocation.y)
            .setDuration(3500L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)

                    // Step 2: Clear listener and move back to original location
                    val colorp =
                        ContextCompat.getColor(requireContext(), R.color.purple_500)
                    frontDeck.backgroundTintList = ColorStateList.valueOf(colorp)
                    frontDeck.setTextColor(ContextCompat.getColor(requireContext(),R.color.white))
//                            frontDeck.text = "PLAY SONGc"
                    frontDeck.text = ""
                    frontDeck.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.outline_music_note_2_24
                    )
                    if (whoGetsIt == 0) {
                        val colorr = ContextCompat.getColor(requireContext(), R.color.red)
                        binding.trash.background = ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.card
                        )
                        binding.trash.backgroundTintList = ColorStateList.valueOf(colorr)
                        binding.trash.setTextColor(ContextCompat.getColor(requireContext(),R.color.black))
                        binding.trash.text = "${gameTrack.releaseYear}"
                        binding.trash.tag = gameTrack.uri
                        binding.trash.setOnClickListener {
                            showSongPopupDialog(0, binding.trash.tag.toString())
                        }
                    }
                    frontDeck.animate()
                        .x(deckx)
                        .y(decky)
                        .scaleX(1.0F)
                        .scaleY(1.0F)
                        .setDuration(0L)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                super.onAnimationEnd(animation)

                                // Step 3: Nudge up or down
                                frontDeck.animate()
                                    .x(deckx)
                                    .y(if (!slideDeck) frontDeck.y else if (currentTeam == 1) binding.team2Title.y+10 else binding.team1Title.y) //frontDeck.y - gDeckSlide else frontDeck.y + gDeckSlide)
                                    .setDuration(if (slideDeck) 500L else 0L)
                                    .setListener(null) // Clear listener to avoid loops
                                    .start()
                                backDeck.animate()
                                    .x(deckx)
                                    .y(if (!slideDeck) frontDeck.y else if (currentTeam == 1) binding.team2Title.y+10 else binding.team1Title.y) //frontDeck.y - gDeckSlide else frontDeck.y + gDeckSlide)
                                    .setDuration(0L)
                                    .setListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            super.onAnimationEnd(animation)
                                            if (whoGetsIt > 0) {
                                                if (whoGetsIt == 1) {
//                                                    if (binding.cardLayout1.childCount == 0)
                                                    //                                                      addSlot(1)
                                                    team1Tracks.add(gameTrack)
                                                    team1Tracks.sortBy { it.releaseYear }
                                                    var p = 0
                                                    while (team1Tracks[p].uri != gameTrack.uri)
                                                        p += 1
                                                    addCard(1, p)
                                                    //                                                addSlot(1)
                                                } else {
                                                    //                                              if (binding.cardLayout2.childCount == 0)
                                                    //                                                addSlot(2)
                                                    team2Tracks.add(gameTrack)
                                                    team2Tracks.sortBy { it.releaseYear }
                                                    var p = 0
                                                    while (team2Tracks[p].uri != gameTrack.uri)
                                                        p += 1
                                                    addCard(2, p)
                                                    //                                          addSlot(2)
                                                }
                                            }
                                            if (whoGetsIt > 0)
                                                refreshCards(whoGetsIt)
                                            refreshScreen()
                                            gameTrack = TrackViewModel.getNextTrackForWhenWasThat(gYearRangeMinimum,
                                                gYearRangeMaximum,
                                                gAllowDuplicates,
                                                getYearList(currentTeam))
                                            nextAction()
                                        }
                                    })
                                    .start()
                            }
                        })
                        .start()
                }
            })
            .start()
    }

    private fun clickedOnSlot(iSlot: Int, iSlotView: ImageView) {
        Timber.tag("Alex").d("clickedOnSlot $iSlot")
        iSlotView.imageTintList = ColorStateList.valueOf(Color.GREEN)
        currentSlotView?.let {
            if (iSlotView != currentSlotView) it.imageTintList = ColorStateList.valueOf(Color.GRAY)
        }
        currentSlotView = iSlotView
        currentSlot = iSlot
        gameState = WhenWasThatGameState.NEED_TO_COMMIT
        updateButtons()
    }

    private fun clickedCommit() {
        SpotifyService.pause()
        deactivateSlots(false)
        gameState = WhenWasThatGameState.OPPORTUNITY_FOR_CHALLENGE
        Timber.tag("Alex").d("calling UpdateButtons from clickedCommit")
        updateButtons()
    }

    private fun clickedBuyACard() {
        binding.buyACardButton1.visibility = View.GONE
        binding.buyACardButton2.visibility = View.GONE
        spendCoin(currentTeam)
        spendCoin(currentTeam)
        spendCoin(currentTeam)
        /*        if (currentTeam == 1)
                    team1Coins -= 3
                else
                    team2Coins -= 3 */
        MyApplication.playSound(SoundAction.SPEND_COIN)
        flipCard(true, currentTeam, false) {
            showCoins(currentTeam)
        }
    }

    private fun clickedPass() {
        SpotifyService.pause()
        flipCard(true, 0, false) {
            afterPass()
        }
    }

    private fun afterPass() {
        gameTrack = TrackViewModel.getNextTrackForWhenWasThat(gYearRangeMinimum,
            gYearRangeMaximum,
            gAllowDuplicates,
            getYearList(currentTeam))
        MyApplication.playSound(SoundAction.SPEND_COIN)
        spendCoin(currentTeam)
        gameState = WhenWasThatGameState.BEGINNING_OF_TURN
        currentSlot = -1
        currentSlotView = null
        refreshSlots()
        Timber.tag("Alex").d("calling UpdateButtons from afterPass")
        updateButtons()
    }

    private fun clickedChallenge() {
        binding.challengeButton1.visibility = View.GONE
        binding.noChallengeButton1.visibility = View.GONE
        binding.challengeButton2.visibility = View.GONE
        binding.noChallengeButton2.visibility = View.GONE
        MyApplication.playSound(SoundAction.SPEND_COIN)
        gameState = WhenWasThatGameState.EARNING_COINS
        if (isGuessCorrect()) {
            if (currentTeam == 1) {
                Timber.tag("Alex")
                    .d("Team 1 was correct! ")
                team2Coins -= 1
                whoGetsCard = 1
                showCoins(2)
            } else {
                Timber.tag("Alex")
                    .d("Team 2 was correct!")
                team1Coins -= 1
                whoGetsCard = 2
                showCoins(1)
            }
            flipCard(false, 0, false) {
                Timber.tag("Alex").d("activateCoinBank 2")
                activateCoinBank(true)
            }
        } else {
            Timber.tag("Alex")
                .d("Team $currentTeam was NOT correct!")
            whoGetsCard = if (currentTeam == 1) 2 else 1
            setCardFrames(whoGetsCard)
            flipCard(false, 0, false) {
                Timber.tag("Alex").d("activateCoinBank 3")
                activateCoinBank(true)
            }
        }
    }

    private fun clickedNoChallenge() {
        Timber.tag("Alex").d("clickedNoChallenge")
        binding.challengeButton1.visibility = View.GONE
        binding.noChallengeButton1.visibility = View.GONE
        binding.challengeButton2.visibility = View.GONE
        binding.noChallengeButton2.visibility = View.GONE
        gameState = WhenWasThatGameState.EARNING_COINS
        if (isGuessCorrect()) {
            Timber.tag("Alex")
                .d("Team $currentTeam was correct!")
            whoGetsCard = currentTeam
            flipCard(false, 0, false) {
                Timber.tag("Alex").d("activateCoinBank 4")
                activateCoinBank(true)
            }
        } else {
            Timber.tag("Alex")
                .d("Team $currentTeam was NOT correct! ")
            whoGetsCard = 0
            flipCard(false, 0, true) {
                Timber.tag("Alex").d("activateCoinBank 5")
                activateCoinBank(true)
            }
        }
    }

    private fun isGuessCorrect(): Boolean {
        Timber.tag("Alex")
            .d("isGuessCorrect currentTeam $currentTeam currentSlot $currentSlot trackSize1Size ${team1Tracks.size} trackSize2Size ${team2Tracks.size}")
        if (currentTeam == 1) {
            return if (currentSlot == 0) {
                gameTrack.releaseYear <= team1Tracks[0].releaseYear
            } else if (currentSlot >= team1Tracks.size * 2)
                gameTrack.releaseYear >= team1Tracks[team1Tracks.size - 1].releaseYear
            else
                gameTrack.releaseYear >= team1Tracks[currentSlot / 2 - 1].releaseYear &&
                        gameTrack.releaseYear <= team1Tracks[currentSlot / 2].releaseYear
        } else {
            return if (currentSlot == 0) {
                gameTrack.releaseYear <= team2Tracks[0].releaseYear
            } else if (currentSlot >= team2Tracks.size * 2)
                gameTrack.releaseYear >= team2Tracks[team2Tracks.size - 1].releaseYear
            else
                gameTrack.releaseYear >= team2Tracks[currentSlot / 2 - 1].releaseYear &&
                        gameTrack.releaseYear <= team2Tracks[currentSlot / 2].releaseYear
        }
    }

    private fun clickedPlay() {
        // reset Trash
        setCardFrames(currentTeam)
        val colorr = ContextCompat.getColor(requireContext(), R.color.orange)
        binding.trash.backgroundTintList = ColorStateList.valueOf(colorr)
        binding.trash.background = ContextCompat.getDrawable(
            requireContext(),
            R.drawable.outline_delete_24
        )
        binding.trash.text = ""

        currentSlot = -1
        currentSlotView = null
        nextTrack = 0
        gameState = WhenWasThatGameState.NEED_TO_COMMIT
        refreshSlots()
        Timber.tag("Alex").d("calling UpdateButtons from clickedPlay")
        updateButtons()
        CoroutineScope(Dispatchers.Main).launch {
            if (currentTeam == 1) {
                binding.progressBar1.visibility = View.VISIBLE
                binding.progressBar1.elevation = 40F
                startCountdownTimer(gTimeToPlay, binding.progressBar1)
            } else {
                binding.progressBar2.visibility = View.VISIBLE
                binding.progressBar2.elevation = 40F
                startCountdownTimer(gTimeToPlay, binding.progressBar2)
            }
            if (gPlayMode == PlayMode.FromStart) {
                Timber.tag("Alex").d("Playing from start")
                SpotifyService.play(gameTrack.uri)
            } else {
                Timber.tag("Alex").d("Playing from random place")
                SpotifyService.playAndSeek(gameTrack.uri, 30)
            }
            delay(gTimeToPlay * 1000L) // Wait for 10,000 milliseconds (10 seconds)
            if (gameState == WhenWasThatGameState.NEED_TO_COMMIT || gameState == WhenWasThatGameState.OPPORTUNITY_FOR_CHALLENGE)
                SpotifyService.pause()
        }
//        binding.userMessage.visibility = View.VISIBLE
//        binding.userMessage.setText(gameTracks[0].releaseYear.toString())
    }

    private fun startCountdownTimer(iTimeToPlay: Int, iProgressBar: ProgressBar) {
        val totalMillis = (iTimeToPlay + 1) * 1000L // allows for the delay in starting the song
        iProgressBar.max = iTimeToPlay

        val countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()

                // Update text readout
//                timerText.text = secondsRemaining.toString()
                // Update graphical progress bar
                iProgressBar.progress = secondsRemaining
            }

            override fun onFinish() {
  //              timerText.text = "0"
                iProgressBar.progress = 0
                iProgressBar.visibility = View.GONE
            }
        }.start()
    }

    private fun clickedStartNewGame() {
        binding.configurationLayout.visibility = View.GONE
        gTimeToPlay = binding.durationOfSong.text.toString().toInt()
        gTracksToWin = binding.durationOfGame.text.toString().toInt()
        gPlayMode = if (binding.radioStart.isChecked)
            PlayMode.FromStart
        else
            PlayMode.Random
        gAllowDuplicates = binding.radioAllowDuplicates.isChecked

        TrackViewModel.shuffleForWhenWasThat()
        gameTrack = TrackViewModel.getNextTrackForWhenWasThat(gYearRangeMinimum,
            gYearRangeMaximum,
            gAllowDuplicates,
            getYearList(currentTeam))
        gGameUnderway = true
        gameState = WhenWasThatGameState.BEGINNING_OF_TURN
        updateButtons()
        binding.startButton.visibility = View.GONE
        startupStep1()
    }

    private fun startupStep1() {
        binding.horizontalLine.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(1500L)
                .setListener(null)
                .start()
        }
        binding.verticalLine.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(1500L)
                .setListener(null)
                .start()
        }
        binding.team1Title.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(1500L)
                .setListener(null)
                .start()
        }
        binding.team2Title.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(1500L)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        startupStep2()
                    }
                })
                .start()
        }
    }

    private fun startupStep2() {
        binding.bank.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(1500L)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        startupStep3()
                    }
                })
        }
    }

    private fun startupStep3() {
//        setTeamTitles()
        //      initSlots()
        activateCoinBank(true, showYesNoOptions = false)
        earnCoin(1, true)
        startupStep4()
    }

    private fun startupStep4() {
//        MyApplication.playSound(SoundAction.START_GAME)
        binding.cardScroll1.visibility = View.VISIBLE
        binding.cardScroll2.visibility = View.VISIBLE
        binding.deckBack.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(1500L)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        startupStep5()
                    }
                })
                .start()
        }
    }

    private fun startupStep5() {
        currentTeam = 1
        flipCard(true, currentTeam, false) {
            startupStep6()
        }
    }

    private fun startupStep6() {
        currentTeam = 2
        flipCard(true, currentTeam, false) {
            currentTeam = 1
            startupStep7()
        }
    }

    private fun startupStep7() {
        binding.trash.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(1500L)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        refreshScreen()
//                        setCardFrames()
                    }
                })
        }
    }

    private fun refreshScreen() {
        if (team1Tracks.size >= gTracksToWin || team2Tracks.size >= gTracksToWin)
            gameState = WhenWasThatGameState.GAME_OVER
        setTeamTitles()
        refreshSlots()
        Timber.tag("Alex").d("calling UpdateButtons from refreshScreen")
        updateButtons()
    }

    private fun setTeamTitles() {
        if (currentTeam == 1) {
            binding.team1Title.text = "Team 1 (${team1Tracks.size} / ${gTracksToWin})"
            binding.team1Title.setTextColor(Color.BLACK)
            binding.team2Title.setTextColor(Color.GRAY)
        } else {
            binding.team2Title.text = "Team 2 (${team2Tracks.size} / ${gTracksToWin})"
            binding.team2Title.setTextColor(Color.BLACK)
            binding.team1Title.setTextColor(Color.GRAY)
        }
    }

    private fun setCardFrames(iTeam: Int) {
        if (iTeam == 1) {
            var parentLayout = binding.cardLayout1
            var c = 0
            for (child in parentLayout.children) {
                if (c % 2 != 0) {// every second View is a slot
                    (child as TextView).setTextColor(Color.BLACK)
                    setFrame(child, false)
                }
                c += 1
            }
            parentLayout = binding.cardLayout2
            c = 0
            for (child in parentLayout.children) {
                if (c % 2 != 0) { // every second View is a slot
                    (child as TextView).setTextColor(Color.GRAY)
                }
                c += 1
            }
        } else {
            var parentLayout = binding.cardLayout1
            var c = 0
            for (child in parentLayout.children) {
                if (c % 2 != 0) { // every second View is a slot
                    (child as TextView).setTextColor(Color.GRAY)
                }
                c += 1
            }
            parentLayout = binding.cardLayout2
            c = 0
            for (child in parentLayout.children) {
                if (c % 2 != 0) {// every second View is a slot
                    (child as TextView).setTextColor(Color.BLACK)
                    setFrame(child, false)
                }
                c += 1
            }

        }
    }

    private fun addSlot(iTeam: Int, iPlacement: Int) {
        val parentLayout = if (iTeam == 1) binding.cardLayout1 else binding.cardLayout2
        val newSlot = ImageView(context)

        val layoutParams = LinearLayout.LayoutParams(
            resources.getDimension(R.dimen.slot_dimension).toInt(),
            resources.getDimension(R.dimen.slot_dimension).toInt()
        )

        newSlot.layoutParams = layoutParams
        newSlot.setPadding(
            resources.getDimension(R.dimen.slot_padding).toInt(),
            0,
            resources.getDimension(R.dimen.slot_padding).toInt(),
            0
        )
        newSlot.setImageResource(R.drawable.circle)
        newSlot.isClickable = true
        newSlot.tag = (if (iTeam == 1) team1Tracks.size + 1 else team2Tracks.size + 1).toString()
        newSlot.setOnClickListener {
            val d = if (iTeam == 1) team1Tracks.size + 1 else team2Tracks.size + 1
            Timber.tag("Alex").d("clicked on new slot teamTracks.size is $d")
            clickedOnSlot(
                newSlot.tag.toString().toInt(), newSlot
            )
        }

        parentLayout.addView(newSlot, iPlacement)
        // renumber tags of slots
        var s = 0
        for (child in parentLayout.children) {
            if (s % 2 == 0) // every second View is a slot
                child.tag = s.toString()
            s += 1
        }
    }

    private fun addCard(iTeam: Int, iTrackNumber: Int) {
        val parentLayout = if (iTeam == 1) binding.cardLayout1 else binding.cardLayout2
//        val currentViews =
  //          if (iTeam == 1) binding.cardLayout1.childCount else binding.cardLayout2.childCount
        val placement =
            iTrackNumber * 2 + 1 // this is where the new Track should be placed in the horizontal view

        if (parentLayout.isEmpty())
            addSlot(iTeam, 0)

        val newCard = TextView(context)

        val layoutParams = LinearLayout.LayoutParams(
            resources.getDimension(R.dimen.card_width).toInt(),
            resources.getDimension(R.dimen.card_height).toInt()
        )

        newCard.layoutParams = layoutParams
        newCard.setBackgroundResource(R.drawable.card)
        newCard.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        newCard.text =
            if (iTeam == 1) team1Tracks[iTrackNumber].releaseYear.toString() else team2Tracks[iTrackNumber].releaseYear.toString()
        newCard.textSize = 20F

        setFrame(newCard, true)
        newCard.setTypeface(null, Typeface.BOLD)
        newCard.isClickable = true
        newCard.tag = if (iTeam == 1) team1Tracks[iTrackNumber].uri else team2Tracks[iTrackNumber].uri
        newCard.setOnClickListener {
            showSongPopupDialog(iTeam, it.tag.toString())
        }

        parentLayout.addView(newCard, placement)
        addSlot(iTeam, placement + 1)
    }

    private fun setFrame(iTextView: TextView, new: Boolean) {
        if (new) {
            val borderDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(ContextCompat.getColor(requireContext(), R.color.orange)) // Background color of the TextView
                setStroke(5, Color.GREEN) // Set border width in pixels and border color
                cornerRadius = 8f // Optional: add rounded corners (radius in pixels)
            }
            iTextView.background = borderDrawable
        } else {
            val borderDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(ContextCompat.getColor(requireContext(), R.color.orange)) // Background color of the TextView
                setStroke(5, Color.BLACK) // Set border width in pixels and border color
                cornerRadius = 8f // Optional: add rounded corners (radius in pixels)
            }
            iTextView.background = borderDrawable
        }
    }

    private fun showSongPopupDialog(
        iTeamNumber: Int,
        iURI: String
    ) {
        val builder = AlertDialog.Builder(requireContext())

        val track = when (iTeamNumber) {
            1 -> {
                team1Tracks.find { it.uri == iURI }
            }
            2 -> {
                team2Tracks.find { it.uri == iURI }
            }
            else -> { // it went to Trash
                TrackViewModel.getTrack(iURI)
            }
        }
        if (track == null)
            return

        builder.setTitle("Song")
        if (iTeamNumber == 1) {
            builder.setMessage("${track.songName}\n${track.artistName}\n${track.releaseYear}")
        } else {
            builder.setMessage("${track.songName}\n${track.artistName}\n${track.releaseYear}")
        }

        // Prevent dismissal when tapping outside the dialog window
        builder.setCancelable(false)

        builder.setNegativeButton("Cancel") { dialog, _ ->
            SpotifyService.pause()
            dialog.dismiss() // Closes the window
        }
        // positive button is setup in 2 steps in order to ensure the dialog isn't dismissed when Play is clicked
        builder.setPositiveButton("Play", null)

        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()

        val positiveButton: Button = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.setOnClickListener {
            SpotifyService.play(track.uri)
//            dialog.dismiss() // Closes the window
        }

    }

    fun earnCoin(iTeam: Int, iStartUp: Boolean) {
        val tLocation: ImageView =
            if (iTeam == 1) {
                when (team1Coins) {
                    0 -> binding.coin11
                    1 -> binding.coin12
                    2 -> binding.coin13
                    3 -> binding.coin14
                    else -> binding.coin15
                }
            } else {
                when (team2Coins) {
                    0 -> binding.coin21
                    1 -> binding.coin22
                    2 -> binding.coin23
                    3 -> binding.coin24
                    else -> binding.coin25
                }
            }
        if (iTeam == 1)
            team1Coins += 1
        else
            team2Coins += 1

        binding.spareCoin.visibility = View.VISIBLE
        MyApplication.playSound(SoundAction.ADD_COIN)
        binding.spareCoin.animate()
            .x(tLocation.x)
            .y(tLocation.y)
            .setDuration(1500L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    makeCoinGlow(tLocation)
                    if (iStartUp) {
                        binding.spareCoin.translationX = 0f
                        binding.spareCoin.translationY = 0f
                        if (team1Coins == 1) {
                            earnCoin(1, true)
                        } else if (team2Coins == 0) {
                            earnCoin(2, true)
                        } else if (team2Coins == 1)
                            earnCoin(2, true)
                        else {
                            binding.spareCoin.visibility = View.INVISIBLE
                            Timber.tag("Alex").d("activateCoinBank 6")
                            activateCoinBank(iTurnOn = false, showYesNoOptions = false)
                        }
                    } else {
                        binding.spareCoin.visibility = View.INVISIBLE
                        binding.spareCoin.translationX = 0f
                        binding.spareCoin.translationY = 0f
                        Timber.tag("Alex").d("activateCoinBank 7")
                        activateCoinBank(iTurnOn = false, showYesNoOptions = false)
                    }
                }
            })
            .start()
    }

    fun spendCoin(iTeam: Int) {
        val tFromLocation: ImageView =
            if (iTeam == 1) {
                when (team1Coins) {
                    1 -> binding.coin11
                    2 -> binding.coin12
                    3 -> binding.coin13
                    4 -> binding.coin14
                    else -> binding.coin15
                }
            } else {
                when (team2Coins) {
                    1 -> binding.coin21
                    2 -> binding.coin22
                    3 -> binding.coin23
                    4 -> binding.coin24
                    else -> binding.coin25
                }
            }
        if (iTeam == 1)
            team1Coins -= 1
        else
            team2Coins -= 1

        tFromLocation.animate()
            .x(binding.bank.x)
            .y(binding.bank.y)
            .setDuration(1500L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    tFromLocation.visibility = View.INVISIBLE
                    tFromLocation.translationX = 0f
                    tFromLocation.translationY = 0f
                    Timber.tag("Alex").d("activateCoinBank 8")
                    activateCoinBank(false, showYesNoOptions = false)
                }
            })
            .start()
    }

    private fun makeCoinGlow(iCoin: ImageView) {
        iCoin.alpha = 0F
        iCoin.visibility = View.VISIBLE
        iCoin.animate()
            .alpha(1F)
            .setDuration(50)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    val animator = ObjectAnimator.ofPropertyValuesHolder(
                        iCoin,
                        PropertyValuesHolder.ofFloat("scaleX", 2.0f), // Grow X
                        PropertyValuesHolder.ofFloat("scaleY", 2.0f)  // Grow Y
                    ).apply {
                        duration = 1000 // 1 second
                        repeatMode = ValueAnimator.REVERSE
                        repeatCount = 3 // Repeat
                        doOnEnd {
                            // what to do?
                        }
                    }
                    animator.start()
                }
            })
    }

    fun activateCoinBank(iTurnOn: Boolean, showYesNoOptions: Boolean = true) {
        bankPulser?.pause()

        Timber.tag("Alex").d("in activateCoinBank iTurnOn $iTurnOn showYesNoOptions $showYesNoOptions")
        if (!iTurnOn) {
            if (currentTeam == 1) {
                binding.yesCoin1.visibility = View.GONE
                binding.noCoin1.visibility = View.GONE
            } else {
                binding.yesCoin2.visibility = View.GONE
                binding.noCoin2.visibility = View.GONE
            }
            return
        }

        if (showYesNoOptions) {
            if (currentTeam == 1) {
                binding.yesCoin1.visibility = View.VISIBLE
                binding.noCoin1.visibility = View.VISIBLE
                binding.yesCoin1.isEnabled = true
                binding.noCoin1.isEnabled = true
            } else {
                binding.yesCoin2.visibility = View.VISIBLE
                binding.noCoin2.visibility = View.VISIBLE
                binding.yesCoin2.isEnabled = true
                binding.noCoin2.isEnabled = true
            }
        }

        // Hold both X and Y scaling properties
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.5f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.5f)

        // Configure the animator
        bankPulser = ObjectAnimator.ofPropertyValuesHolder(binding.bank, scaleX, scaleY).apply {
            duration = 1000                      // Time taken to grow from 1.0 to 1.5
            repeatCount = ValueAnimator.INFINITE // Loop forever
            repeatMode = ValueAnimator.REVERSE   // Shrink back down instead of restarting abruptly
            start()
        }
    }

    private fun getYearList(iTeam: Int): MutableList<Int> {
        val tList= mutableListOf<Int>()
        val tTracks = if (iTeam == 1) team1Tracks else team2Tracks

        for (track in tTracks) {
            Timber.tag("Alex").d("adding ${track.releaseYear}")
            tList.add(track.releaseYear)
        }

        return tList
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        SpotifyService.pause()
        _binding = null
    }
}
