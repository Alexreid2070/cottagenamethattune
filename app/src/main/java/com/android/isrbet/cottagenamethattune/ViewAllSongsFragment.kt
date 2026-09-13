package com.android.isrbet.cottagenamethattune

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
import com.android.isrbet.cottagenamethattune.MyApplication.Companion.trackSearchText
import com.android.isrbet.cottagenamethattune.databinding.FragmentViewAllSongsBinding
import androidx.core.view.isGone
import timber.log.Timber

class ViewAllSongsFragment : Fragment() {
    private var _binding: FragmentViewAllSongsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewAllSongsBinding.inflate(inflater, container, false)
        val adapter = TrackRecyclerAdapter(requireContext(), TrackViewModel.getTracks(TrackViewModel.getSortOrder(),)) { uri ->
            val action =
                ViewAllSongsFragmentDirections.actionViewAllFragmentToAddSongFragment()
                    .setTrackURI(uri)
            this@ViewAllSongsFragment.findNavController().navigate(action)
        }
        binding.trackListView.adapter = adapter
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle()

        val menuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menu.clear()
                menuInflater.inflate(R.menu.view_all_songs, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.search_for_song -> {
                        if (binding.trackSearch.isGone)
                            binding.trackSearch.visibility = View.VISIBLE
                        else {
                            binding.trackSearch.setQuery("", true)
                            binding.trackSearch.visibility = View.GONE
                            setTitle()
                        }
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.trackSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val lAdapter: TrackRecyclerAdapter =
                    binding.trackListView.adapter as TrackRecyclerAdapter
                lAdapter.filter.filter(newText) {
                   setTitle()
                }
                trackSearchText = newText.toString()
                return true
            }
        })
        if (trackSearchText == "")
            binding.trackSearch.visibility = View.GONE
        else {
            binding.trackSearch.visibility = View.VISIBLE
            binding.trackSearch.setQuery(trackSearchText, false)
            setTitle()
        }
        binding.songNameHeading.setOnClickListener {
            if (TrackViewModel.getSortOrder() == SortOrder.BY_SONG_NAME)
                TrackViewModel.toggleSortAscending()
            val adapter = binding.trackListView.adapter as TrackRecyclerAdapter
            adapter.updateList(SortOrder.BY_SONG_NAME)
//            adapter.sort(SortOrder.by_SONG_NAME)
//            adapter.notifyDataSetChanged()
            setTitle()
        }
        binding.artistNameHeading.setOnClickListener {
            if (TrackViewModel.getSortOrder() == SortOrder.BY_ARTIST_NAME)
                TrackViewModel.toggleSortAscending()
            val adapter = binding.trackListView.adapter as TrackRecyclerAdapter
            adapter.updateList(SortOrder.BY_ARTIST_NAME)
//            adapter.sort(SortOrder.by_ARTIST_NAME)
//            adapter.notifyDataSetChanged()
            setTitle()
        }
        binding.releaseYearHeading.setOnClickListener {
            if (TrackViewModel.getSortOrder() == SortOrder.BY_RELEASE_YEAR)
                TrackViewModel.toggleSortAscending()
            val adapter = binding.trackListView.adapter as TrackRecyclerAdapter
            adapter.updateList(SortOrder.BY_RELEASE_YEAR)
//            adapter.sort(SortOrder.by_ARTIST_NAME)
//            adapter.notifyDataSetChanged()
            setTitle()
        }
        if (TrackViewModel.getDataHasChanged()) {
            val adapter = binding.trackListView.adapter as TrackRecyclerAdapter
            adapter.updateList(TrackViewModel.getSortOrder())
            adapter.notifyDataSetChanged()
            TrackViewModel.setDataHasChanged(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setTitle() {
        val myAdapter = binding.trackListView.adapter as TrackRecyclerAdapter
        val numInList = myAdapter.filteredList.size

        if (numInList == TrackViewModel.getCount())
            (activity as AppCompatActivity?)!!.supportActionBar!!.title =
                "View All Songs ($numInList)"
        else
            (activity as AppCompatActivity?)!!.supportActionBar!!.title =
                "View All Songs ($numInList/${TrackViewModel.getCount()})"
    }
}

class TrackRecyclerAdapter(
    private val context: Context, private var list: MutableList<MyTrack>,
    private val onClickListener: (String) -> Unit = {}
) : Filterable, RecyclerView.Adapter<TrackRecyclerAdapter.ViewHolder>() {

    var filteredList: MutableList<MyTrack> = mutableListOf()
    private var searchText = ""

    init {
        filterTheList(searchText)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vhSongName: TextView = view.findViewById(R.id.row_song_name)
        val vhArtistName: TextView = view.findViewById(R.id.row_artist_name)
        val vhReleaseYear: TextView = view.findViewById(R.id.row_release_year)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(context).inflate(R.layout.row_song, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = filteredList[position]
        holder.vhSongName.text = data.songName
        holder.vhArtistName.text = data.artistName
        holder.vhReleaseYear.text = data.releaseYear.toString()
        holder.itemView.setOnClickListener { onClickListener(data.uri) }
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
                Timber.tag("Alex").d("performFiltering")
                return filterResults
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results?.values == null)
                    filteredList = mutableListOf()
                else
                    filteredList = results.values as MutableList<MyTrack>
                Timber.tag("Alex").d("FilteredList size is now ${filteredList.size}")
                notifyDataSetChanged()
            }
        }
    }

    fun filterTheList(iConstraint: String) {
        if (iConstraint.isEmpty()) {
            filteredList = list
            Timber.tag("Alex").d("FilteredList b size is now ${filteredList.size}")
        } else {
            val resultList: MutableList<MyTrack> = mutableListOf()
            val splitSearchTerms: List<String> = iConstraint.lowercase().split(" ")
            Timber.tag("Alex").d("list.size is ${list.size} and iConstraint is $iConstraint")
            for (track in list) {
//                val myTrack = TrackViewModel.getTrack(row.playOrder)
                var found = true
                for (r in splitSearchTerms) {
                    found = found && (track.artistName.lowercase().unaccent().contains(r)
                            || track.songName.lowercase().unaccent().contains(r)) == true
                }
                if (found) {
                    resultList.add(track)
                }
            }
            filteredList = resultList
            Timber.tag("Alex").d("FilteredList c size is now ${filteredList.size}")
        }
    }

    fun updateList(iSortOrder: SortOrder, iRefresh: Boolean = false) {
//        if (iRefresh)
        //          TrackViewModel.refreshViewList()
        //    else
//        TrackViewModel.sortList(iSortOrder)
        Timber.tag("Alex").d("updateList with sort $iSortOrder")
        list = TrackViewModel.getTracks(iSortOrder)
        Timber.tag("Alex").d("inUpdateList first is ${list[0].songName}")
        filterTheList(searchText)
        notifyDataSetChanged()
    }
}