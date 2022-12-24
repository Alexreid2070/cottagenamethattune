package com.android.isrbet.cottagenamethattune

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.isrbet.cottagenamethattune.MyApplication.Companion.trackSearchText
import com.android.isrbet.cottagenamethattune.databinding.FragmentViewAllSongsBinding

class ViewAllSongsFragment : Fragment() {
    private var _binding: FragmentViewAllSongsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewAllSongsBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        val adapter = TrackRecyclerAdapter(requireContext(), TrackViewModel.getTracks()) { uri ->
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

        binding.trackSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val lAdapter: TrackRecyclerAdapter =
                    binding.trackListView.adapter as TrackRecyclerAdapter
                lAdapter.filter.filter(newText)
                trackSearchText = newText.toString()
                setTitle()
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
            val adapter = binding.trackListView.adapter as TrackRecyclerAdapter
            adapter.updateList(SortOrder.BY_SONG_NAME)
//            adapter.sort(SortOrder.by_SONG_NAME)
//            adapter.notifyDataSetChanged()
            setTitle()
        }
        binding.artistNameHeading.setOnClickListener {
            val adapter = binding.trackListView.adapter as TrackRecyclerAdapter
            adapter.updateList(SortOrder.BY_ARTIST_NAME)
//            adapter.sort(SortOrder.by_ARTIST_NAME)
//            adapter.notifyDataSetChanged()
            setTitle()
        }
        if (TrackViewModel.getDataHasChanged()) {
            val adapter = binding.trackListView.adapter as TrackRecyclerAdapter
            adapter.updateList(TrackViewModel.getSortOrder(), true)
            adapter.notifyDataSetChanged()
            TrackViewModel.setDataHasChanged(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        for (i in 0 until menu.size()) {
            when (menu.getItem(i).itemId) {
                R.id.sort_by_artist,
                R.id.sort_by_song,
                R.id.sort_by_date_added,
                R.id.sort_by_play_order,
                R.id.search_for_song -> menu.getItem(i).isVisible = true
                else -> menu.getItem(i).isVisible = false
            }
        }
        super.onCreateOptionsMenu(menu, inflater)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.search_for_song -> {
                if (binding.trackSearch.visibility == View.GONE)
                    binding.trackSearch.visibility = View.VISIBLE
                else {
                    binding.trackSearch.setQuery("", true)
                    binding.trackSearch.visibility = View.GONE
                    setTitle()
                }
            }
            R.id.sort_by_song -> {
                val myAdapter = binding.trackListView.adapter as TrackRecyclerAdapter
//                TrackViewModel.setViewOrder(SortOrder.by_SONG_NAME)
                myAdapter.updateList(SortOrder.BY_SONG_NAME)
//                myAdapter.sort(SortOrder.by_SONG_NAME)
//                myAdapter.notifyDataSetChanged()
                setTitle()
            }
            R.id.sort_by_artist -> {
                val myAdapter = binding.trackListView.adapter as TrackRecyclerAdapter
                myAdapter.updateList(SortOrder.BY_ARTIST_NAME)
//                myAdapter.sort(SortOrder.by_ARTIST_NAME)
  //              myAdapter.notifyDataSetChanged()
                setTitle()
            }
            R.id.sort_by_play_order -> {
                val myAdapter = binding.trackListView.adapter as TrackRecyclerAdapter
                myAdapter.updateList(SortOrder.BY_PLAY_ORDER)
//                myAdapter.sort(SortOrder.by_PLAY_ORDER)
  //              myAdapter.notifyDataSetChanged()
                setTitle()
            }
            R.id.sort_by_date_added -> {
                val myAdapter = binding.trackListView.adapter as TrackRecyclerAdapter
                myAdapter.updateList(SortOrder.BY_DATE_ADDED)
//                myAdapter.sort(SortOrder.by_DATE_ADDED)
//                myAdapter.notifyDataSetChanged()
                setTitle()
            }
        }
        return super.onOptionsItemSelected(item)
    }
    private fun setTitle() {
        if (binding.trackSearch.visibility == View.GONE ||
            TrackViewModel.getCount() == TrackViewModel.getCount())
            (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View All Songs (${TrackViewModel.getCount()})"
        else
            (activity as AppCompatActivity?)!!.supportActionBar!!.title = "View All Songs (${TrackViewModel.getCount()}/${TrackViewModel.getCount()})"
    }
}

class TrackRecyclerAdapter(
    private val context: Context, private var list: MutableList<MyTrack>,
    private val listener: (String) -> Unit = {}
) : Filterable, RecyclerView.Adapter<TrackRecyclerAdapter.ViewHolder>() {

    var filteredList: MutableList<MyTrack> = mutableListOf()
    private var searchText = ""

    init {
        filterTheList(searchText)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vhSongName: TextView = view.findViewById(R.id.row_song_name)
        val vhArtistName: TextView = view.findViewById(R.id.row_artist_name)
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
        holder.itemView.setOnClickListener { listener(data.uri) }
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
                filteredList = results?.values as MutableList<MyTrack>
                notifyDataSetChanged()
            }
        }
    }

    fun filterTheList(iConstraint: String) {
        if (iConstraint.isEmpty()) {
            filteredList = list
        } else {
            val resultList: MutableList<MyTrack> = mutableListOf()
            val splitSearchTerms: List<String> = iConstraint.split(" ")
            for (track in list) {
//                val myTrack = TrackViewModel.getTrack(row.playOrder)
                var found = true
                for (r in splitSearchTerms) {
                    found = found && track.contains(r) == true
                }
                if (found) {
                    resultList.add(track)
                }
            }
            filteredList = resultList
        }
    }
    fun updateList(iSortOrder: SortOrder, iRefresh: Boolean = false) {
//        if (iRefresh)
  //          TrackViewModel.refreshViewList()
    //    else
            TrackViewModel.sortList(iSortOrder)
        list = TrackViewModel.getTracks()
        notifyDataSetChanged()
    }
}