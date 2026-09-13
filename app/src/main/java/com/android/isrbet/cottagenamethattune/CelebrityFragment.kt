package com.android.isrbet.cottagenamethattune

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import com.android.isrbet.cottagenamethattune.databinding.FragmentCelebrityBinding
import timber.log.Timber

class CelebrityFragment : Fragment() {
    private var _binding: FragmentCelebrityBinding? = null
    private val binding get() = _binding!!
    private val args: CelebrityFragmentArgs by navArgs()
    private var addCelebrityMode: Boolean = true
    private var currentlyViewing = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addCelebrityMode = args.celebrityKey == ""
        val tempCelebrity = CelebrityViewModel.getCelebrity(args.celebrityKey)
        currentlyViewing = if (tempCelebrity == null)
            -1
        else
            CelebrityViewModel.getCelebrityIndex(tempCelebrity.celebrityName)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCelebrityBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity?)!!.supportActionBar!!.title = getString(R.string.celebrity)

        val menuHost = requireActivity()

        // Add menu items without using the Fragment Menu APIs
        // Note how we can tie the MenuProvider to the viewLifecycleOwner
        // and an optional Lifecycle.State (here, RESUMED) to indicate when
        // the menu should be visible
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menu.clear()
                if (!addCelebrityMode) {
                    if (MyApplication.adminMode)
                        menuInflater.inflate(R.menu.edit_options_menu, menu)
                    else
                        menuInflater.inflate(R.menu.empty_options_menu, menu)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                Timber.tag("Alex").d("in onMenuItemSelected")
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.edit -> {
                        editCelebrity()
                        true
                    }

                    R.id.delete -> {
                        deleteCelebrity()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.cancelButton.setOnClickListener {
            activity?.onBackPressed()
//            setToViewMode()
        }
        binding.saveButton.setOnClickListener {
            saveCelebrity()
        }
        binding.prevButton.setOnClickListener {
            prevCelebrity()
        }
        binding.nextButton.setOnClickListener {
            nextCelebrity()
        }
        if (addCelebrityMode) {
            (activity as AppCompatActivity?)!!.supportActionBar!!.title = "Add Celebrity"
            binding.celebrityName.setText("")
            binding.cancelButton.visibility = View.VISIBLE
            binding.saveButton.visibility = View.VISIBLE
            binding.saveButton.isEnabled = true
            binding.prevButton.visibility = View.GONE
            binding.nextButton.visibility = View.GONE
        } else {
            (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View Celebrity"
            val myCelebrity = CelebrityViewModel.getCelebrity(currentlyViewing)
            if (myCelebrity != null) {
                binding.celebrityName.setText(myCelebrity.celebrityName)
            }
            setToViewMode()
        }
    }

    private fun setToEditMode() {
        Timber.tag("Alex").d("setToEditMode")
        binding.celebrityName.isEnabled = true
        binding.saveButton.visibility = View.VISIBLE
        binding.saveButton.isEnabled = true
        binding.cancelButton.visibility = View.VISIBLE
        binding.cancelButton.isEnabled = true
        binding.prevButton.visibility = View.GONE
        binding.nextButton.visibility = View.GONE
    }

    private fun setToViewMode() {
        binding.cancelButton.visibility = View.GONE
        binding.saveButton.visibility = View.GONE
        binding.prevButton.visibility = View.VISIBLE
        binding.nextButton.visibility = View.VISIBLE
        binding.prevButton.isEnabled = true
        binding.nextButton.isEnabled = true
        binding.celebrityName.isEnabled = false
    }

    private fun saveCelebrity() {
        if (addCelebrityMode) {
            val tCelebrityName: String = binding.celebrityName.text.toString()
            if (tCelebrityName != "") {
                val myCelebrity = MyCelebrity(binding.celebrityName.text.toString(), 0, "")
                if (CelebrityViewModel.addCelebrity(myCelebrity)) {
                    Toast.makeText(
                        MyApplication.myMainActivity,
                        "Celebrity added!",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    MyApplication.playSound(SoundAction.ADD_ITEM)
                    binding.celebrityName.setText("")
                    /*                setToViewMode()

                    addCelebrityMode = false
                    activity?.invalidateOptionsMenu()
                    currentlyViewing = CelebrityViewModel.getCelebrityIndex(myCelebrity.celebrityName) */
                } else
                    Toast.makeText(
                        MyApplication.myMainActivity,
                        "Celebrity already exists in database, not added.",
                        Toast.LENGTH_SHORT
                    ).show()
            }
        } else {
//            currentlyViewing =
            //              CelebrityViewModel.getCelebrityIndex(binding.celebrityName.text.toString())
            Timber.tag("Alex").d("currentlyViewing $currentlyViewing")
            val currentCelebrity = CelebrityViewModel.getCelebrity(currentlyViewing)
            Timber.tag("Alex").d("looking at $currentCelebrity")
            if (currentCelebrity != null) {
                val newCelebrity = MyCelebrity(
                    binding.celebrityName.text.toString(), currentCelebrity.numberOfTimesUsed,
                    currentCelebrity.mykey
                )
                CelebrityViewModel.editCelebrity(newCelebrity)
                MyApplication.playSound(SoundAction.EDIT_ITEM)
                //                activity?.onBackPressed()
            }
            setToViewMode()
            currentlyViewing =
                CelebrityViewModel.getCelebrityIndex(binding.celebrityName.text.toString())
        }
    }

    private fun prevCelebrity() {
        if (currentlyViewing > 0)
            currentlyViewing -= 1
        else
            currentlyViewing = CelebrityViewModel.getCount() - 1
        val myCelebrity = CelebrityViewModel.getCelebrity(currentlyViewing)
        if (myCelebrity != null) {
            binding.celebrityName.setText(myCelebrity.celebrityName)
        }
//        MyApplication.playSound(context, SoundAction.PREV_SONG)
    }

    private fun nextCelebrity() {
        if (currentlyViewing < CelebrityViewModel.getCount() - 1)
            currentlyViewing += 1
        else
            currentlyViewing = 0
        val myCelebrity = CelebrityViewModel.getCelebrity(currentlyViewing)
        if (myCelebrity != null) {
            binding.celebrityName.setText(myCelebrity.celebrityName)
        }
//        MyApplication.playSound(context, SoundAction.NEXT_SONG)
    }

    private fun editCelebrity() {
        setToEditMode()
    }

    private fun deleteCelebrity() {
        fun yesClicked() {
            val cIndex = CelebrityViewModel.getCelebrityIndex(binding.celebrityName.text.toString())
                ?: return
            val myCelebrity = CelebrityViewModel.getCelebrity(cIndex)
            CelebrityViewModel.deleteCelebrity(currentlyViewing)
            Toast.makeText(activity, getString(R.string.celebrity_deleted), Toast.LENGTH_SHORT)
                .show()
            MyApplication.playSound(SoundAction.DELETE_ITEM)
            requireActivity().onBackPressed()
            currentlyViewing = -1
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}