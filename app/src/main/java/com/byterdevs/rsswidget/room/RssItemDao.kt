package com.byterdevs.rsswidget.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RssItemDao {
    @Query("SELECT * FROM rss_items WHERE appWidgetId = :appWidgetId ORDER BY date DESC")
    suspend fun getItemsForWidget(appWidgetId: Int): List<RssItemEntity>

    @Insert
    suspend fun insertAll(items: List<RssItemEntity>)

    @Query("DELETE FROM rss_items WHERE appWidgetId = :appWidgetId")
    suspend fun clearItemsForWidget(appWidgetId: Int)
}
