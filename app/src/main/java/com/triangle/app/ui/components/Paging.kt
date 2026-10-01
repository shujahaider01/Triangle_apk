package com.triangle.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Shows a long list a page at a time: the first [pageSize] items, then — when the user scrolls near the end —
 * a short loading spinner and the next page. [visible] is how many items to show; [hasMore] says whether a
 * footer spinner is still due (see [LoadMoreFooter]). Use [ensure] to make sure a specific index is shown
 * (e.g. before scrolling to a highlighted item).
 */
@Stable
class Paging internal constructor(private val pageSize: Int) {
    var visible by mutableIntStateOf(pageSize)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var total by mutableIntStateOf(0)
        internal set

    val hasMore: Boolean get() = visible < total

    fun ensure(count: Int) {
        if (count > visible) visible = count
    }

    internal fun nextPage() {
        visible = (visible + pageSize).coerceAtMost(total.coerceAtLeast(pageSize))
    }
}

private const val LOAD_DELAY_MS = 350L

/** Paging for a LazyColumn/LazyRow: loads the next page when the last visible row gets within a few items of the end. */
@Composable
fun rememberLazyPaging(listState: LazyListState, total: Int, pageSize: Int = 15, resetKey: Any? = null): Paging {
    val paging = remember(resetKey) { Paging(pageSize) }
    paging.total = total
    LaunchedEffect(listState, paging) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (paging.hasMore && last >= paging.visible - 2) {
                    paging.loading = true
                    delay(LOAD_DELAY_MS)
                    paging.nextPage()
                    paging.loading = false
                }
            }
    }
    return paging
}

/** Paging for a plain verticalScroll column: loads the next page when scrolled within ~400px of the bottom. */
@Composable
fun rememberScrollPaging(scrollState: ScrollState, total: Int, pageSize: Int = 10, resetKey: Any? = null): Paging {
    val paging = remember(resetKey) { Paging(pageSize) }
    paging.total = total
    LaunchedEffect(scrollState, paging) {
        snapshotFlow { scrollState.maxValue - scrollState.value }
            .collect { remaining ->
                if (paging.hasMore && remaining < 400) {
                    paging.loading = true
                    delay(LOAD_DELAY_MS)
                    paging.nextPage()
                    paging.loading = false
                }
            }
    }
    return paging
}

/** Small centred spinner shown under the last loaded item while more are coming. */
@Composable
fun LoadMoreFooter(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
    }
}
