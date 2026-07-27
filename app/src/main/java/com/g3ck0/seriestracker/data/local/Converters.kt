package com.g3ck0.seriestracker.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun mediaTypeToString(value: MediaType): String = value.name

    @TypeConverter
    fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun statusToString(value: WatchStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): WatchStatus =
        runCatching { WatchStatus.valueOf(value) }.getOrDefault(WatchStatus.WATCHING)
}
