package xyz.quaver.pupil.adapters

import android.graphics.BitmapFactory
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.item_galleryblock.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.ReaderItem
import xyz.quaver.pupil.R
import xyz.quaver.pupil.types.Tag
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule

class GalleryBlockAdapter(private val galleries: List<Pair<GalleryBlock, Deferred<String>>>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class ViewType {
        NEXT,
        GALLERY,
        PREV
    }

    inner class GalleryViewHolder(private val view: CardView) : RecyclerView.ViewHolder(view) {
        fun bind(item: Pair<GalleryBlock, Deferred<String>>) {
            with(view) {
                val resources = context.resources
                val languages = resources.getStringArray(R.array.languages).map {
                    it.split("|").let { split ->
                        Pair(split[0], split[1])
                    }
                }.toMap()

                val (gallery: GalleryBlock, thumbnail: Deferred<String>) = item

                val artists = gallery.artists
                val series = gallery.series

                CoroutineScope(Dispatchers.Default).launch {
                    val cache = thumbnail.await()

                    if (!File(cache).exists())
                        return@launch

                    val bitmap = BitmapFactory.decodeFile(thumbnail.await())

                    post {
                        galleryblock_thumbnail.setImageBitmap(bitmap)
                    }
                }

                //Check cache
                val readerCache = {
                    File(ContextCompat.getDataDir(context), "images/${gallery.id}/reader.json").let {
                        when {
                            it.exists() -> it
                            else -> File(context.cacheDir, "imageCache/${gallery.id}/reader.json")
                        }
                    }
                }
                val imageCache = {
                    File(ContextCompat.getDataDir(context), "images/${gallery.id}/images").let {
                        when {
                            it.exists() -> it
                            else -> File(context.cacheDir, "imageCache/${gallery.id}/images")
                        }
                    }
                }

                if (readerCache.invoke().exists()) {
                    val reader = Json(JsonConfiguration.Stable)
                        .parse(ReaderItem.serializer().list, readerCache.invoke().readText())

                    with(galleryblock_progressbar) {
                        max = reader.size
                        progress = imageCache.invoke().list()?.size ?: 0

                        visibility = View.VISIBLE
                    }
                } else {
                    galleryblock_progressbar.visibility = View.GONE
                }

                if (refreshTasks[this@GalleryViewHolder] == null) {
                    val refresh = Timer(false).schedule(0, 1000) {
                        post {
                            with(view.galleryblock_progressbar) {
                                progress = imageCache.invoke().list()?.size ?: 0

                                if (!readerCache.invoke().exists()) {
                                    visibility = View.GONE
                                    max = 0
                                    progress = 0

                                    view.galleryblock_progress_complete.visibility = View.INVISIBLE
                                } else {
                                    if (visibility == View.GONE) {
                                        val reader = Json(JsonConfiguration.Stable)
                                            .parse(ReaderItem.serializer().list, readerCache.invoke().readText())
                                        max = reader.size
                                        visibility = View.VISIBLE
                                    }

                                    if (progress == max) {
                                        if (completeFlag.get(gallery.id, false)) {
                                            with(view.galleryblock_progress_complete) {
                                                setImageResource(R.drawable.ic_progressbar)
                                                visibility = View.VISIBLE
                                            }
                                        } else {
                                            val drawable = AnimatedVectorDrawableCompat.create(context, R.drawable.ic_progressbar_complete)
                                            with(view.galleryblock_progress_complete) {
                                                setImageDrawable(drawable)
                                                visibility = View.VISIBLE
                                            }
                                            drawable?.start()
                                            completeFlag.put(gallery.id, true)
                                        }
                                    } else
                                        view.galleryblock_progress_complete.visibility = View.INVISIBLE

                                    null
                                }
                            }
                        }
                    }

                    refreshTasks[this@GalleryViewHolder] = refresh
                }

                galleryblock_title.text = gallery.title
                with(galleryblock_artist) {
                    text = artists.joinToString(", ") { it.wordCapitalize() }
                    visibility = when {
                        artists.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }
                with(galleryblock_series) {
                    text =
                        resources.getString(
                            R.string.galleryblock_series,
                            series.joinToString(", ") { it.wordCapitalize() })
                    visibility = when {
                        series.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }
                galleryblock_type.text = resources.getString(R.string.galleryblock_type, gallery.type).wordCapitalize()
                with(galleryblock_language) {
                    text =
                        resources.getString(R.string.galleryblock_language, languages[gallery.language])
                    visibility = when {
                        gallery.language.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }

                galleryblock_tag_group.removeAllViews()
                gallery.relatedTags.forEach {
                    val tag = Tag.parse(it)

                    val chip = LayoutInflater.from(context)
                        .inflate(R.layout.tag_chip, this, false) as Chip

                    val icon = when(tag.area) {
                        "male" -> {
                            chip.setChipBackgroundColorResource(R.color.material_blue_700)
                            chip.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                            ContextCompat.getDrawable(context, R.drawable.ic_gender_male_white)
                        }
                        "female" -> {
                            chip.setChipBackgroundColorResource(R.color.material_pink_600)
                            chip.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                            ContextCompat.getDrawable(context, R.drawable.ic_gender_female_white)
                        }
                        else -> null
                    }

                    chip.chipIcon = icon
                    chip.text = tag.tag.wordCapitalize()
                    chip.setOnClickListener {
                        for (callback in onChipClickedHandler)
                            callback.invoke(tag)
                    }

                    galleryblock_tag_group.addView(chip)
                }
            }
        }
    }
    class NextViewHolder(view: LinearLayout) : RecyclerView.ViewHolder(view)
    class PrevViewHolder(view: LinearLayout) : RecyclerView.ViewHolder(view)

    class ViewHolderFactory {
        companion object {
            fun getLayoutID(type: Int): Int {
                return when(ViewType.values()[type]) {
                    ViewType.NEXT -> R.layout.item_next
                    ViewType.PREV -> R.layout.item_prev
                    ViewType.GALLERY -> R.layout.item_galleryblock
                }
            }
        }
    }

    private fun String.wordCapitalize() : String {
        val result = ArrayList<String>()

        for (word in this.split(" "))
            result.add(word.capitalize())

        return result.joinToString(" ")
    }

    private val refreshTasks = HashMap<GalleryViewHolder, TimerTask>()
    val completeFlag = SparseBooleanArray()

    val onChipClickedHandler = ArrayList<((Tag) -> Unit)>()

    var showNext = false
    var showPrev = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        fun getViewHolder(type: Int, view: View): RecyclerView.ViewHolder {
            return when(ViewType.values()[type]) {
                ViewType.NEXT -> NextViewHolder(view as LinearLayout)
                ViewType.PREV -> PrevViewHolder(view as LinearLayout)
                ViewType.GALLERY -> GalleryViewHolder(view as CardView)
            }
        }

        return getViewHolder(
            viewType,
            LayoutInflater.from(parent.context).inflate(
                ViewHolderFactory.getLayoutID(viewType),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is GalleryViewHolder)
            holder.bind(galleries[position-(if (showPrev) 1 else 0)])
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)

        if (holder is GalleryViewHolder) {
            val task = refreshTasks[holder] ?: return

            task.cancel()
            refreshTasks.remove(holder)
        }
    }

    override fun getItemCount() =
        (if (galleries.isEmpty()) 0 else galleries.size)+
        (if (showNext) 1 else 0)+
        (if (showPrev) 1 else 0)

    override fun getItemViewType(position: Int): Int {
        return when {
            showPrev && position == 0 -> ViewType.PREV
            showNext && position == galleries.size+(if (showPrev) 1 else 0) -> ViewType.NEXT
            else -> ViewType.GALLERY
        }.ordinal
    }
}