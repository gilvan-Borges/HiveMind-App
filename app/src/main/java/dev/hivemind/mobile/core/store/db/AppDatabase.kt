package dev.hivemind.mobile.core.store.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Cache offline do HiveMind-App: último snapshot de projetos, lanes,
 * mensagens e diffs. É cache, não fonte da verdade — o bridge manda o
 * estado vivo e o Room só sobrevive ao processo morrer.
 */
@Database(
    entities = [
        ProjectEntity::class,
        LaneEntity::class,
        LaneMessageEntity::class,
        DiffEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
