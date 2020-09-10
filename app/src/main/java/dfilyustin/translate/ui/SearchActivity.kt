package dfilyustin.translate.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.badoo.mvicore.android.AndroidBindings
import com.bumptech.glide.Glide
import dfilyustin.translate.App
import dfilyustin.translate.R
import dfilyustin.translate.model.Translation
import dfilyustin.translate.mvi.TranslateAction
import dfilyustin.translate.mvi.TranslateFeature
import dfilyustin.translate.mvi.TranslateState
import dfilyustin.translate.utils.RequestState
import io.reactivex.ObservableSource
import io.reactivex.Observer
import io.reactivex.functions.Consumer
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_search.*


class SearchActivity : AppCompatActivity(), ObservableSource<TranslateAction>,
    Consumer<TranslateState> {

    private lateinit var feature: TranslateFeature
    private val actions = PublishSubject.create<TranslateAction>()
    private val binding by lazy { Binding(this, feature) }

    override fun onCreate(savedInstanceState: Bundle?) {
        feature = (applicationContext as App).feature
        super.onCreate(savedInstanceState)
        binding.setup(this)
        setContentView(R.layout.activity_search)
        listView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        listView.adapter = Adapter(LayoutInflater.from(this), onMeaningClick = {
            startActivity(MeaningActivity.intent(this, it))
        })

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)

        (menu.findItem(R.id.search).actionView as SearchView).apply {
            findViewById<ImageView>(R.id.search_close_btn).setOnClickListener {
                setQuery("", false)
                actions.onNext(TranslateAction.Clear)
            }
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean = false

                override fun onQueryTextChange(newText: String): Boolean {
                    actions.onNext(TranslateAction.UpdateQuery(newText))
                    return true
                }
            })
        }
        return true
    }

    override fun subscribe(observer: Observer<in TranslateAction>) {
        actions.subscribe(observer)
    }

    override fun accept(state: TranslateState) {
        when (state.requestState) {
            RequestState.Idle -> {
                (listView.adapter as Adapter).update(state.translations?.flatMap {
                    mutableListOf<Any>().apply {
                        add(it)
                        addAll(it.meanings)
                    }
                } ?: emptyList())

                errorView.visibility = View.GONE
                progressView.visibility = View.GONE
            }
            RequestState.Running -> {
                errorView.visibility = View.GONE
                progressView.visibility = View.VISIBLE
            }
            is RequestState.Failed -> {
                errorView.visibility = View.VISIBLE
                progressView.visibility = View.GONE
            }
        }
    }

    class OriginalTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val originalTextView = itemView as TextView

        fun bind(originalText: String) {
            originalTextView.text = originalText
        }

    }

    class MeaningViewHolder(itemView: View, onClick: (position: Int) -> Unit) : RecyclerView.ViewHolder(itemView) {

        init {
            itemView.setOnClickListener { onClick(adapterPosition) }
        }

        private val meaningTextView = itemView.findViewById<TextView>(R.id.meaningTextView)
        private val meaningImageView = itemView.findViewById<ImageView>(R.id.meaningImageView)

        fun bind(meaningText: String, imageUrl: String) {
            meaningTextView.text = meaningText
            Glide.with(meaningImageView).load(imageUrl).into(meaningImageView)
        }
    }

    class Adapter(
        private val inflater: LayoutInflater,
        private val onMeaningClick: (meaning: Translation.Meaning) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val originalTextType = 0
            private const val meaningTextType = 1
        }

        private val items = mutableListOf<Any>()

        fun update(items: List<Any>) {
            this.items.apply {
                clear()
                addAll(items)
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            when (viewType) {
                originalTextType -> OriginalTextViewHolder(
                    inflater.inflate(
                        R.layout.item_original_text,
                        parent,
                        false
                    )
                )
                meaningTextType -> MeaningViewHolder(
                    inflater.inflate(
                        R.layout.item_meaning,
                        parent,
                        false
                    ),
                    onClick = { onMeaningClick(items[it] as Translation.Meaning) }
                )
                else -> throw IllegalArgumentException("unknown option")
            }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
            when (getItemViewType(position)) {
                originalTextType -> (holder as OriginalTextViewHolder).bind((items[position] as Translation).originalText)
                meaningTextType -> (holder as MeaningViewHolder).bind(
                    (items[position] as Translation.Meaning).translatedText,
                    (items[position] as Translation.Meaning).imageUrl
                )
                else -> throw IllegalArgumentException("unknown option")
            }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is Translation -> originalTextType
            is Translation.Meaning -> meaningTextType
            else -> throw IllegalArgumentException("unknown option")
        }

    }

    class Binding(
        lifecycleOwner: LifecycleOwner,
        private val feature: TranslateFeature
    ) : AndroidBindings<SearchActivity>(lifecycleOwner) {
        override fun setup(view: SearchActivity) {
            binder.bind(view to feature)
            binder.bind(feature to view)
        }
    }

}