package com.courier.android.utils

import com.courier.android.core.Logging
import com.courier.android.models.CourierServerError
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.Strictness
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


internal fun String.toGraphQuery(): String {
    val value = this
        .replace("\n", "")
        .replace("\r", "")
        .trim()
    return "{\"query\": \"${value}\"}"
}

internal fun Request.toPrettyJson(): String? {

    if (body == null) {
        return null
    }

    return try {
        val buffer = Buffer()
        body?.writeTo(buffer)
        val body = buffer.readUtf8()
        if (body.isEmpty()) return null
        return body.toPrettyJson()
    } catch (e: Exception) {
        null
    }

}

internal fun String.toPrettyJson(): String? {
    return try {
        val gson = GsonBuilder().setStrictness(Strictness.LENIENT).setPrettyPrinting().create()
        val jsonObject = JsonParser.parseString(this).asJsonObject
        "\n${gson.toJson(jsonObject)}"
    } catch (e: Exception) {
        null
    }
}

internal suspend inline fun <reified T>Call.dispatch(showLogs: Boolean = true, validCodes: List<Int> = listOf(200)) = suspendCoroutine<T> { continuation ->

    enqueue(object : Callback {

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {

            if (showLogs) {
                val request = request()
                Logging.log("📡 New Courier API Request")
                Logging.log("URL: ${request.url}")
                Logging.log("Method: ${request.method}")
                Logging.log("Body: ${request.toPrettyJson() ?: "Empty"}")
            }

            val gson = Gson()

            if (!validCodes.contains(response.code)) {

                try {
                    val error = gson.fromJson(response.body?.string(), CourierServerError::class.java).toException
                    continuation.resumeWithException(error)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }

            } else {

                val body = response.body?.string()
                Logging.log("Response: ${body?.toPrettyJson() ?: "Empty"}")

                try {

                    if (T::class == Any::class) {
                        continuation.resume(T::class.java.newInstance())
                        return
                    }

                    val res = gson.fromJson(body, T::class.java)
                    continuation.resume(res)

                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }

            }

        }

    })


}