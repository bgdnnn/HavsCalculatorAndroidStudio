package com.homeclouds.havs.data

object HavsMath {

    // points = 2 * a^2 * (minutes/60)
    fun pointsForMinutes(aMs2: Double, minutes: Int): Double {
        val m = minutes.coerceAtLeast(0)
        val tHours = m / 60.0
        return 2.0 * aMs2 * aMs2 * tHours
    }

    fun pointsPerMinute(aMs2: Double): Double {
        if (aMs2 <= 0) return 0.0
        return (aMs2 * aMs2) / 30.0
    }

}
