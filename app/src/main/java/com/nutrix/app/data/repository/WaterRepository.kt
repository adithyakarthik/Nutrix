package com.nutrix.app.data.repository

import com.nutrix.app.data.local.dao.WaterDao
import com.nutrix.app.data.local.entity.WaterLogEntity
import com.nutrix.app.data.prefs.UserPreferencesRepository
import com.nutrix.app.model.WaterLogEntry
import com.nutrix.app.model.WaterSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class WaterRepository(
    private val dao: WaterDao,
    private val preferences: UserPreferencesRepository,
) {

    val settings: Flow<WaterSettings> = preferences.waterSettings

    fun observeDay(date: LocalDate): Flow<List<WaterLogEntry>> =
        dao.observeDay(date.toEpochDay()).map { rows -> rows.map { it.toModel() } }

    fun observeDayTotal(date: LocalDate): Flow<Int> = dao.observeDayTotal(date.toEpochDay())

    suspend fun dayTotal(date: LocalDate): Int = dao.dayTotal(date.toEpochDay())

    suspend fun currentSettings(): WaterSettings = preferences.waterSettings.first()

    suspend fun log(amountMl: Int, date: LocalDate = LocalDate.now()): Long =
        dao.insert(
            WaterLogEntity(
                epochDay = date.toEpochDay(),
                amountMl = amountMl,
                loggedAtEpochMs = System.currentTimeMillis(),
            ),
        )

    suspend fun undoLast(date: LocalDate = LocalDate.now()) {
        dao.lastOfDay(date.toEpochDay())?.let { dao.deleteById(it.id) }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun updateSettings(settings: WaterSettings) = preferences.setWaterSettings(settings)
}
