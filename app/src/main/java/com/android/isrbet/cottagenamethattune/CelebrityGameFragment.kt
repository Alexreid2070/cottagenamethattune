package com.android.isrbet.cottagenamethattune

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.TypedValue
import android.view.*
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.android.isrbet.cottagenamethattune.databinding.FragmentCelebrityGameBinding
import timber.log.Timber
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size

var gNumberOfCelebrities = 2
var gLengthOfTurn = 30
var gNumberOfTeams = 2
var gNumberOfPassesAllowed = 1

class CelebrityGameFragment : Fragment() {
    private var _binding: FragmentCelebrityGameBinding? = null
    private val binding get() = _binding!!

    //    private var indexToPlay = -1
    private var numCorrect = 0
    private var numPassed = 0
    private var currentTeam = 2
    private var team1Correct = 0
    private var team2Correct = 0
    private var currentRound = 1
    val gameRound = GameRound(1)

    //    var gameList: MutableList<MyCelebrity> = ArrayList()
    private var countDownTimer: CountDownTimer? = null

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
                builder.setPositiveButton("Confirm") { dialog, which ->
                    // Execute your confirmation logic here
                    dialog.dismiss()
                    parentFragmentManager.popBackStack()
                }

                // Set negative/cancel button
                builder.setNegativeButton("Cancel") { dialog, which ->
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
        _binding = FragmentCelebrityGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost = requireActivity()
        // Add menu items without using the Fragment Menu APIs
        // Note how we can tie the MenuProvider to the viewLifecycleOwner
        // and an optional Lifecycle.State (here, RESUMED) to indicate when
        // the menu should be visible
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menu.clear()
                menuInflater.inflate(R.menu.celebrity_options_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                Timber.tag("Alex").d("handled menu item $menuItem in CelebrityGame")
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


        (activity as AppCompatActivity?)!!.supportActionBar!!.title =
            getString(R.string.title_celebrity)
        binding.correctButton.setOnClickListener {
            gameRound.setCurrentAsCorrect()
//            gameList[indexToPlay].numberOfTimesUsed += 1
            numCorrect += 1
            if (currentTeam == 1) {
                team1Correct += 1
                binding.team1NumberOfCorrect.text = "$team1Correct"
            } else {
                team2Correct += 1
                binding.team2NumberOfCorrect.text = "$team2Correct"
            }
            playNext()
        }
        binding.passButton.setOnClickListener {
            val passOK =  (gNumberOfPassesAllowed > numPassed)
            numPassed += 1
            val numPassesRemaining = if (gNumberOfPassesAllowed > numPassed)
                gNumberOfPassesAllowed-numPassed
            else
                0
            if (numPassesRemaining == 1)
                binding.userMessage.text = "1 pass remaining"
            else
                binding.userMessage.text = "$numPassesRemaining passes remaining"
            if (passOK) {
                playNext()
            } else {
//                binding.passButton.isEnabled = false
                val currentTextSize = binding.userMessage.textSize
                binding.userMessage.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    (currentTextSize + 8.0).toFloat()
                )
            }
        }
        binding.startGameButton.setOnClickListener {
            gGameUnderway = true
            val tLengthOfTurn = binding.lengthOfTurn.text.toString()
            if (tLengthOfTurn == "") {
                binding.userMessage.visibility = View.VISIBLE
                binding.userMessage.text = "Length of Turn may not be blank"
            } else
                gLengthOfTurn = tLengthOfTurn.toInt()
            val tNumberOfCelebrities = binding.numberOfCelebrities.text.toString()
            if (tNumberOfCelebrities == "") {
                binding.userMessage.visibility = View.VISIBLE
                binding.userMessage.text = "Number of Celebrities may not be blank"
            } else
                gNumberOfCelebrities = tNumberOfCelebrities.toInt()
            val tNumberOfPasses = binding.numberOfPasses.text.toString()
            if (tNumberOfPasses == "")
                gNumberOfPassesAllowed = 0
            else gNumberOfPassesAllowed = tNumberOfPasses.toInt()
            if (tLengthOfTurn != "" && tNumberOfCelebrities != "") {
                gameRound.initialize()
                startNewGame()
            }
        }
        /*        if (MyApplication.currentGameURI == "") { // first time here
                    binding.celebrityName.text = ""
                } else {
                    binding.startGameButton.visibility = View.GONE
                    val track = TrackViewModel.getTrack(MyApplication.currentGameURI)
                    binding.celebrityName.text = track.songName
                } */
/*        if (gameRound.size() == 0) {
            binding.startGameButton.visibility = View.GONE
            binding.userMessage.text =
                "Data has not yet loaded. Please exit game and then try again."
        } */
    }

    private fun changeTextSize(iDirection: Int) {
        val currentTextSize = binding.celebrityName.textSize
        binding.celebrityName.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            currentTextSize + iDirection.toFloat()
        )
    }

    private fun startNewGame() {
        binding.nextUpText.visibility = View.GONE
        binding.teamProgressLayout.visibility = View.VISIBLE
        binding.configurationLayout.visibility = View.GONE
        binding.celebrityName.text = ""
        binding.correctButton.visibility = View.VISIBLE
        binding.passButton.visibility = View.VISIBLE
        binding.passButton.isEnabled = true
        binding.startGameButton.visibility = View.GONE
        binding.teamRoundProgressLayout.visibility = View.VISIBLE
        binding.teamName.text = "Team $currentTeam"
        binding.roundNumber.text = "Round $currentRound"
        numPassed = 0
        numCorrect = 0

        if (gNumberOfPassesAllowed - numPassed == 1)
            binding.userMessage.text = "${gNumberOfPassesAllowed - numPassed} pass remaining"
        else
            binding.userMessage.text = "${gNumberOfPassesAllowed - numPassed} passes remaining"
        MyApplication.currentGameURI = ""
        /*
                if (gameList.isEmpty()) {
                    gameList = CelebrityViewModel.getListForGame(gNumberOfCelebrities)
                    currentTeam = 2
                } */
        currentTeam = if (currentTeam == 1)
            2
        else
            1
        binding.teamName.text = "Team $currentTeam"

        CelebrityViewModel.reshufflePlayOrder()
        playNext()
        MyApplication.playSound(SoundAction.START_GAME)
        binding.countdownTimerLayout.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer((gLengthOfTurn * 1000).toLong(), 1000) {
            // Callback function, fired on regular interval
            override fun onTick(millisUntilFinished: Long) {
                binding.countdownTimer.text = (millisUntilFinished / 1000).toString()
            }

            // Callback function, fired
            // when the time is up
            override fun onFinish() {
                binding.userMessage.text = "You are done - next player is up!"
                binding.countdownTimerLayout.visibility = View.GONE
                binding.nextUpText.visibility = View.VISIBLE
                binding.userMessage.textSize = 20.0F
                binding.celebrityName.text = ""
                binding.correctButton.visibility = View.GONE
                binding.passButton.visibility = View.GONE
                binding.startGameButton.visibility = View.VISIBLE
                binding.startGameButton.text = "Start the next player..."
                if (currentTeam == 1) {
                    binding.teamName.text = "Team 2"
                } else {
                    binding.teamName.text = "Team 1"
                }
            }
        }.start()
    }

    private fun playNext() {
//        indexToPlay = getIndexOfNextCelebrity()
        val tNextCelebrityName = gameRound.getNextCelebrity()
        if (tNextCelebrityName != "") {
//        if (indexToPlay < gameList.size) {
//            val myCelebrity = gameList[indexToPlay]
            binding.countdownTimerLayout.visibility = View.VISIBLE
            binding.celebrityName.text = tNextCelebrityName //myCelebrity.celebrityName
        } else {
            countDownTimer?.cancel()
            binding.countdownTimerLayout.visibility = View.GONE
            binding.userMessage.text = "End of Round $currentRound!"
            if (currentTeam == 1) {
                binding.teamName.text = "Team 2"
            } else {
                binding.teamName.text = "Team 1"
            }
            currentRound += 1
            binding.roundNumber.text = "Round $currentRound"
            binding.nextUpText.visibility = View.VISIBLE
            binding.userMessage.textSize = 20.0F
            binding.celebrityName.text = ""
            binding.correctButton.visibility = View.GONE
            binding.passButton.visibility = View.GONE
/*            if (currentTeam == 1) {
                team1Correct += numCorrect
            } else {
                team2Correct += numCorrect
            }
            binding.team1NumberOfCorrect.text = "$team1Correct"
            binding.team2NumberOfCorrect.text = "$team2Correct" */

//            indexToPlay = -1
            gameRound.resetForNextRound()
            if (currentRound > 3) {
                val winner = if (team1Correct > team2Correct) "Congratulation Team 1!"
                else if (team2Correct > team1Correct) "Congratulations Team 2!"
                else "It's a tie!!"
                binding.userMessage.text = "Game Over. $winner"
                binding.startGameButton.visibility = View.GONE
                binding.teamRoundProgressLayout.visibility = View.GONE
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
            } else {
                binding.startGameButton.visibility = View.VISIBLE
                binding.startGameButton.text = "Start Next Round"
            }
        }
        //        MyApplication.playSound(context, SoundAction.NEXT_SONG)
    }

    /*    private fun getIndexOfNextCelebrity() : Int {
            var tIndex = indexToPlay + 1
            while (indexToPlay < gameList.size - 1) {
                if (gameList[tIndex].numberOfTimesUsed < currentRound)
                    return tIndex
                else
                    tIndex += 1
            }
            tIndex = 0
            while (indexToPlay < gameList.size - 1) {
                if (gameList[tIndex].numberOfTimesUsed < currentRound)
                    return tIndex
                else
                    tIndex += 1
            }
            return gameList.size // which means nothing found in this round
        } */

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}

data class GameRound(var currentRound: Int) {
    var gameList: MutableList<MyCelebrity> = ArrayList()
    var currentIndex: Int = -1

    fun initialize() {
        if (gameList.isEmpty())
            gameList = CelebrityViewModel.getListForGame(gNumberOfCelebrities)
    }

    fun getNextCelebrity(): String {
//        var tIndex = currentIndex + 1
        currentIndex += 1
        while (currentIndex < gameList.size) {
            if (gameList[currentIndex].numberOfTimesUsed < currentRound) {
                return gameList[currentIndex].celebrityName
            } else
                currentIndex += 1
        }
        currentIndex = 0
        while (currentIndex < gameList.size) {
            if (gameList[currentIndex].numberOfTimesUsed < currentRound) {
                return gameList[currentIndex].celebrityName
            } else
                currentIndex += 1
        }
        return "" // which means nothing found in this round
    }

    fun setCurrentAsCorrect() {
        gameList[currentIndex].numberOfTimesUsed += 1
    }

    fun resetForNextRound() {
        gameList.shuffle()
        currentIndex = -1
        currentRound += 1
    }

    fun size(): Int {
        return gameList.size
    }
}