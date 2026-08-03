package com.g3ck0.seriestracker

import com.g3ck0.seriestracker.ui.common.toUserError
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** The one rule the whole mapper exists for: nothing the exception says reaches the user. */
class UserErrorTest {

    private fun http(code: Int) =
        HttpException(Response.error<Unit>(code, "".toResponseBody("text/plain".toMediaType())))

    @Test
    fun `host lookup failure reads as no connection`() {
        val error = UnknownHostException("""Unable to resolve host "backend.example": nope""")
            .toUserError()

        assertEquals("Нет соединения с интернетом", error.title)
        assertEquals("Проверь сеть и попробуй ещё раз", error.body)
    }

    @Test
    fun `refused connections and timeouts read the same`() {
        val expected = "Нет соединения с интернетом"

        assertEquals(expected, ConnectException("Connection refused").toUserError().title)
        assertEquals(expected, SocketTimeoutException("timeout").toUserError().title)
    }

    @Test
    fun `rejected credentials say so without the status code`() {
        assertEquals("Сервер отклонил запрос", http(401).toUserError().title)
        assertEquals("Сервер отклонил запрос", http(403).toUserError().title)
        assertNull(http(401).toUserError().body)
    }

    @Test
    fun `server errors ask to try later`() {
        assertEquals("Сервер недоступен, попробуй позже", http(500).toUserError().title)
        assertEquals("Сервер недоступен, попробуй позже", http(503).toUserError().title)
    }

    @Test
    fun `an unmapped failure falls back, and the call site can name the action`() {
        assertEquals("Не удалось загрузить данные", IllegalStateException("boom").toUserError().title)
        assertEquals(
            "Не удалось обновить",
            IllegalStateException("boom").toUserError("Не удалось обновить").title,
        )
        // A 404 is not one of the mapped statuses.
        assertEquals("Не удалось загрузить данные", http(404).toUserError().title)
    }

    @Test
    fun `no mapped text repeats the exception`() {
        val host = """Unable to resolve host "dosmotr.example.ru": No address associated"""
        val error = UnknownHostException(host).toUserError()

        assertFalse(error.combined.contains("dosmotr"))
        assertFalse(error.combined.contains("resolve"))
    }

    @Test
    fun `combined joins the hint for a snackbar`() {
        assertEquals(
            "Нет соединения с интернетом. Проверь сеть и попробуй ещё раз",
            ConnectException("refused").toUserError().combined,
        )
        assertEquals("Не удалось обновить", IllegalStateException().toUserError("Не удалось обновить").combined)
    }
}
