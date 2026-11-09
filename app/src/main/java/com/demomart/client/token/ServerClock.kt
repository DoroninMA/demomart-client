package com.demomart.client.token

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface OffsetStore {
    suspend fun load(): Long
    suspend fun save(offsetSeconds: Long)
}

@Singleton
class ServerClock @Inject constructor(private val clock: Clock, private val store: OffsetStore) {
    private val mutex = Mutex()
    private var restored = false
    @Volatile private var offset = 0L

    fun now(): Long = clock.instant().epochSecond + offset

    fun onServerDate(httpDate: String?, deviceAtResponse: Instant) {
        if (httpDate == null) return
        val serverTime = try {
            ZonedDateTime.parse(httpDate, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
        } catch (_: DateTimeParseException) {
            return
        }
        val difference = serverTime - deviceAtResponse.epochSecond
        val corrected = difference
        // runs on an OkHttp thread
        runBlocking {
            mutex.withLock {
                if (!restored || corrected != offset) {
                    try {
                        store.save(corrected)
                    } catch (_: IOException) {
                        // keep the offset in memory
                    }
                    offset = corrected
                    restored = true
                }
            }
        }
    }

    suspend fun restore() = mutex.withLock {
        if (!restored) {
            offset = try {
                store.load()
            } catch (_: IOException) {
                0L
            }
            restored = true
        }
    }
}
