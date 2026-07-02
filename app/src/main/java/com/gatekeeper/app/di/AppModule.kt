package com.gatekeeper.app.di

import android.content.Context
import androidx.room.Room
import com.gatekeeper.app.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "gatekeeper.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun blockedAppDao(db: AppDatabase) = db.blockedAppDao()
    @Provides fun gateConfigDao(db: AppDatabase) = db.gateConfigDao()
    @Provides fun taskDao(db: AppDatabase) = db.taskDao()
    @Provides fun exerciseDao(db: AppDatabase) = db.exerciseDao()
    @Provides fun bookDao(db: AppDatabase) = db.bookDao()
    @Provides fun sessionGrantDao(db: AppDatabase) = db.sessionGrantDao()
    @Provides fun completionLogDao(db: AppDatabase) = db.completionLogDao()
}
