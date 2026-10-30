package com.bandary.di

import android.content.Context
import androidx.room.Room
import com.bandary.data.local.BandaryDatabase
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.local.dao.ConcertDao
import com.bandary.data.local.dao.DailySongDao
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
    fun provideDatabase(@ApplicationContext ctx: Context): BandaryDatabase =
        Room.databaseBuilder(ctx, BandaryDatabase::class.java, BandaryDatabase.DB_NAME)
            .addMigrations(BandaryDatabase.MIGRATION_12_13, BandaryDatabase.MIGRATION_13_14, BandaryDatabase.MIGRATION_14_15, BandaryDatabase.MIGRATION_15_16, BandaryDatabase.MIGRATION_16_17, BandaryDatabase.MIGRATION_17_18, BandaryDatabase.MIGRATION_18_19, BandaryDatabase.MIGRATION_19_20, BandaryDatabase.MIGRATION_20_21, BandaryDatabase.MIGRATION_21_22, BandaryDatabase.MIGRATION_22_23, BandaryDatabase.MIGRATION_23_24)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideConcertDao(db: BandaryDatabase): ConcertDao = db.concertDao()

    @Provides
    fun provideDailySongDao(db: BandaryDatabase): DailySongDao = db.dailySongDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext ctx: Context): SettingsDataStore =
        SettingsDataStore(ctx)
}
