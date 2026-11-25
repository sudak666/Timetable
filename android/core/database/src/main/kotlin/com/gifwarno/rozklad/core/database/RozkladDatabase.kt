package com.gifwarno.rozklad.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Версію піднімати при кожній зміні схеми й додавати AutoMigration або Migration.
 * Схеми зберігаються в core/database/schemas — перевіряються в CI.
 */
@Database(
    entities = [
        FamilyEntity::class,
        MemberEntity::class,
        GradeEntity::class,
        HomeworkEntity::class,
        LedgerEntity::class,
        ChallengeEntity::class,
        TimetableEntity::class,
        AirAlertEntity::class,
        OutboxEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class RozkladDatabase : RoomDatabase() {
    abstract fun familyDao(): FamilyDao

    abstract fun outboxDao(): OutboxDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RozkladDatabase =
        Room.databaseBuilder(context, RozkladDatabase::class.java, "rozklad.db").build()

    @Provides
    fun familyDao(db: RozkladDatabase): FamilyDao = db.familyDao()

    @Provides
    fun outboxDao(db: RozkladDatabase): OutboxDao = db.outboxDao()
}
