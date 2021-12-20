/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021 tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.sources.hitomi

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.compose.rememberInstance
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.R
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.sources.composable.*
import xyz.quaver.pupil.sources.hitomi.composable.DetailedSearchResult
import xyz.quaver.pupil.sources.hitomi.lib.getGalleryInfo
import xyz.quaver.pupil.sources.hitomi.lib.getReferer
import xyz.quaver.pupil.sources.hitomi.lib.imageUrlFromImage

class Hitomi(app: Application) : Source(), DIAware {
    override val di by closestDI(app)

    private val client: HttpClient by instance()

    private val logger = newLogger(LoggerFactory.default)

    private val database: AppDatabase by instance()
    private val bookmarkDao = database.bookmarkDao()

    override val name: String = "hitomi.la"
    override val iconResID: Int = R.drawable.hitomi

    override fun NavGraphBuilder.navGraph(navController: NavController) {
        navigation(startDestination = "hitomi.la/search", route = name) {
            composable("hitomi.la/search") { Search(navController) }
            composable("hitomi.la/reader/{itemID}") { Reader(navController) }
        }
    }

    @Composable
    fun Search(navController: NavController) {
        val model: HitomiSearchResultViewModel = viewModel()
        val database: AppDatabase by rememberInstance()
        val bookmarkDao = remember { database.bookmarkDao() }
        val coroutineScope = rememberCoroutineScope()

        val bookmarks by bookmarkDao.getAll(name).observeAsState()
        val bookmarkSet by derivedStateOf {
            bookmarks?.toSet() ?: emptySet()
        }

        var sourceSelectDialog by remember { mutableStateOf(false) }

        if (sourceSelectDialog)
            SourceSelectDialog(navController, name) { sourceSelectDialog = false }

        LaunchedEffect(model.currentPage, model.sortByPopularity) {
            model.search()
        }

        SearchBase(
            model,
            fabSubMenu = listOf(
                SubFabItem(
                    painterResource(R.drawable.ic_jump),
                    stringResource(R.string.main_jump_title)
                ),
                SubFabItem(
                    Icons.Default.Shuffle,
                    stringResource(R.string.main_fab_random)
                ),
                SubFabItem(
                    painterResource(R.drawable.numeric),
                    stringResource(R.string.main_open_gallery_by_id)
                )
            ),
            actions = {
                var expanded by remember { mutableStateOf(false) }

                IconButton(onClick = { sourceSelectDialog = true }) {
                    Image(
                        painter = painterResource(id = R.drawable.hitomi),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.Sort, contentDescription = null)
                }

                IconButton(onClick = { navController.navigate("settings") }) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                }

                val onClick: (Boolean?) -> Unit = {
                    expanded = false

                    it?.let {
                        model.sortByPopularity = it
                    }
                }
                DropdownMenu(expanded, onDismissRequest = { onClick(null) }) {
                    DropdownMenuItem(onClick = { onClick(false) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(stringResource(R.string.main_menu_sort_newest))
                            RadioButton(selected = !model.sortByPopularity, onClick = { onClick(false) })
                        }
                    }

                    Divider()

                    DropdownMenuItem(onClick = { onClick(true) }){
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(stringResource(R.string.main_menu_sort_popular))
                            RadioButton(selected = model.sortByPopularity, onClick = { onClick(true) })
                        }
                    }
                }
            },
            onSearch = { model.search() }
        ) { contentPadding ->
            ListSearchResult(model.searchResults, contentPadding = contentPadding) {
                DetailedSearchResult(
                    it,
                    bookmarks = bookmarkSet,
                    onBookmarkToggle = {
                        coroutineScope.launch {
                            if (it in bookmarkSet) bookmarkDao.delete(name, it)
                            else bookmarkDao.insert(name, it)
                        }
                    }
                ) { result ->
                    logger.info {
                        result.toString()
                    }
                    navController.navigate("hitomi.la/reader/${result.itemID}")
                }
            }
        }
    }

    @Composable
    fun Reader(navController: NavController) {
        val model: ReaderBaseViewModel = viewModel()

        val database: AppDatabase by rememberInstance()
        val bookmarkDao = database.bookmarkDao()

        val coroutineScope = rememberCoroutineScope()

        val itemID = navController.currentBackStackEntry?.arguments?.getString("itemID") ?: ""

        if (itemID.isEmpty()) model.error = true

        val bookmark by bookmarkDao.contains(name, itemID).observeAsState(false)

        LaunchedEffect(itemID) {
            runCatching {
                val galleryID = itemID.toInt()

                val galleryInfo = getGalleryInfo(client, galleryID)

                model.title = galleryInfo.title

                model.load(galleryInfo.files.map { imageUrlFromImage(galleryID, it, false) }) {
                    append("Referer", getReferer(galleryID))
                }
            }.onFailure {
                model.error = true
            }
        }

        ReaderBase(
            model,
            icon = {
                Image(
                    painter = painterResource(R.drawable.hitomi),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            },
            bookmark = bookmark,
            onToggleBookmark = {
                coroutineScope.launch {
                    if (itemID.isEmpty() || bookmark) bookmarkDao.delete(name, itemID)
                    else                              bookmarkDao.insert(name, itemID)
                }
            }
        )
    }
}