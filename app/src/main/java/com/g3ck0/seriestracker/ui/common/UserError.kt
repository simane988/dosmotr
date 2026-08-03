package com.g3ck0.seriestracker.ui.common

import android.util.Log
import com.g3ck0.seriestracker.BuildConfig
import retrofit2.HttpException
import java.io.IOException

/**
 * A failure as the user is allowed to see it: a short Russian headline and an optional hint.
 *
 * Never the exception's own text — that is English and carries the backend host name, which
 * has no business being on screen or in a screenshot.
 */
data class UserError(val title: String, val body: String? = null) {
    /** One-line form, for a snackbar that has no room for a second paragraph. */
    val combined: String get() = if (body == null) title else "$title. $body"
}

private const val TAG = "Dosmotr"

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_SERVER_ERROR = 500
private const val HTTP_MAX = 599

/**
 * Maps a throwable to UI text. The original message goes to logcat in a debug build and
 * nowhere else.
 *
 * [fallback] is what an unrecognised failure says, so a call site can name its own action
 * ("Не удалось обновить") instead of the generic load failure.
 */
fun Throwable.toUserError(fallback: String = "Не удалось загрузить данные"): UserError {
    if (BuildConfig.DEBUG) Log.w(TAG, "request failed", this)
    return when {
        // UnknownHostException, ConnectException and SocketTimeoutException are all
        // IOException, and so is every other transport failure OkHttp throws: from the
        // user's side they are one and the same situation.
        this is IOException -> UserError(
            title = "Нет соединения с интернетом",
            body = "Проверь сеть и попробуй ещё раз",
        )

        this is HttpException && code() in setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN) ->
            UserError("Сервер отклонил запрос")

        this is HttpException && code() in HTTP_SERVER_ERROR..HTTP_MAX ->
            UserError("Сервер недоступен, попробуй позже")

        else -> UserError(fallback)
    }
}
