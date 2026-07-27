package com.g3ck0.seriestracker.fake

import app.cash.turbine.ReceiveTurbine

/**
 * Waits for the first emission matching [predicate].
 *
 * State flows here emit several times per action (data, then message, then flag), and
 * asserting on a fixed emission index makes tests fail for the wrong reason.
 */
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
