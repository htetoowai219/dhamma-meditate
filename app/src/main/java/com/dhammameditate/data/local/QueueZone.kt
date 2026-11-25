package com.dhammameditate.data.local

enum class QueueZone {
    INTRO,
    MAIN;

    fun toDbValue(): String = name
}
