package me.rerere.zeroagent.data.favorite

import me.rerere.zeroagent.data.db.entity.FavoriteEntity
import me.rerere.zeroagent.data.model.FavoriteType

interface FavoriteAdapter<T> {
    val type: FavoriteType

    fun buildRefKey(target: T): String

    fun buildFavoriteEntity(
        target: T,
        existing: FavoriteEntity? = null,
        now: Long = System.currentTimeMillis()
    ): FavoriteEntity
}
