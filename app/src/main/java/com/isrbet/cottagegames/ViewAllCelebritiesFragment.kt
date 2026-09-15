package com.isrbet.cottagegames

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.isrbet.cottagegames.MyApplication.Companion.celebritySearchText
import com.isrbet.cottagegames.databinding.FragmentViewAllCelebritiesBinding
import timber.log.Timber
import androidx.core.view.isGone

class ViewAllCelebritiesFragment : Fragment() {
    private var _binding: FragmentViewAllCelebritiesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewAllCelebritiesBinding.inflate(inflater, container, false)
        val adapter = CelebrityRecyclerAdapter(requireContext(), CelebrityViewModel.getCelebrities()) { item ->
            Timber.tag("Alex").d("clicked on $item")
            val action =
                ViewAllCelebritiesFragmentDirections.actionViewAllFragmentToAddCelebrityFragment()
                    .setCelebrityKey(item.mykey)
            this@ViewAllCelebritiesFragment.findNavController().navigate(action)
        }
        binding.celebrityListView.adapter = adapter
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
                menuInflater.inflate(R.menu.view_all_celebrities, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.search -> {
                        if (binding.celebritySearch.isGone)
                            binding.celebritySearch.visibility = View.VISIBLE
                        else {
                            binding.celebritySearch.visibility = View.GONE
                            celebritySearchText = ""
                            binding.celebritySearch.setQuery(celebritySearchText, true)
                        }
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        setTitle()

        binding.celebritySearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val lAdapter: CelebrityRecyclerAdapter =
                    binding.celebrityListView.adapter as CelebrityRecyclerAdapter
                lAdapter.filter.filter(newText)
                celebritySearchText = newText.toString()
                setTitle()
                return true
            }
        })
        if (celebritySearchText == "")
            binding.celebritySearch.visibility = View.GONE
        else {
            binding.celebritySearch.visibility = View.VISIBLE
            binding.celebritySearch.setQuery(celebritySearchText, false)
            setTitle()
        }
        if (CelebrityViewModel.getDataHasChanged()) {
            val adapter = binding.celebrityListView.adapter as CelebrityRecyclerAdapter
            adapter.updateList()
            adapter.notifyDataSetChanged()
            CelebrityViewModel.setDataHasChanged(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun setTitle() {
        if (binding.celebritySearch.isGone ||
            CelebrityViewModel.getCount() == CelebrityViewModel.getCount())
            (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View All Celebrities (${CelebrityViewModel.getCount()})"
        else
            (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View All Celebrities (${CelebrityViewModel.getCount()}/${CelebrityViewModel.getCount()})"
    }
}

class CelebrityRecyclerAdapter(
    private val context: Context, private var list: MutableList<MyCelebrity>,
    private val listener: (MyCelebrity) -> Unit = {}
) : Filterable, RecyclerView.Adapter<CelebrityRecyclerAdapter.ViewHolder>() {

    var filteredList: MutableList<MyCelebrity> = mutableListOf()
    private var searchText = ""

    init {
        filterTheList(searchText)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vhCelebrityName: TextView = view.findViewById(R.id.row_celebrity_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(context).inflate(R.layout.row_celebrity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = filteredList[position]
        holder.vhCelebrityName.text = data.celebrityName
        holder.itemView.setOnClickListener { listener(data) }
    }

    override fun getItemCount(): Int {
        return filteredList.size
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString()
                filterTheList(charSearch)
                searchText = charSearch
                val filterResults = FilterResults()
                filterResults.values = filteredList
                return filterResults
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as MutableList<MyCelebrity>
                notifyDataSetChanged()
            }
        }
    }

    fun filterTheList(iConstraint: String) {
        if (iConstraint.isEmpty()) {
            filteredList = list
        } else {
            val resultList: MutableList<MyCelebrity> = mutableListOf()
            val splitSearchTerms: List<String> = iConstraint.split(" ")
            for (celebrity in list) {
//                val myCelebrity = CelebrityViewModel.getCelebrity(row.playOrder)
                var found = true
                for (r in splitSearchTerms) {
                    found = found && celebrity.contains(r) == true
                }
                if (found) {
                    resultList.add(celebrity)
                }
            }
            filteredList = resultList
        }
    }
    fun updateList(iRefresh: Boolean = false) {
//        if (iRefresh)
        //          CelebrityViewModel.refreshViewList()
        //    else
        list = CelebrityViewModel.getCelebrities()
        notifyDataSetChanged()
    }
}