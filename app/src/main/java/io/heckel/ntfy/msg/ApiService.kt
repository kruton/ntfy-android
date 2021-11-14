package io.heckel.ntfy.msg

import android.util.Log
import com.google.gson.Gson
import io.heckel.ntfy.data.Notification
import io.heckel.ntfy.data.topicUrl
import io.heckel.ntfy.data.topicUrlJson
import io.heckel.ntfy.data.topicUrlJsonPoll
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiService {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS) // Total timeout for entire request
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val subscriberClient = OkHttpClient.Builder()
        .readTimeout(77, TimeUnit.SECONDS) // Assuming that keepalive messages are more frequent than this
        .build()

    fun publish(baseUrl: String, topic: String, message: String) {
        val url = topicUrl(baseUrl, topic)
        Log.d(TAG, "Publishing to $url")

        val request = Request.Builder().url(url).put(message.toRequestBody()).build();
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Unexpected response ${response.code} when publishing to $url")
            }
            Log.d(TAG, "Successfully published to $url")
        }
    }

    fun poll(subscriptionId: Long, baseUrl: String, topic: String): List<Notification> {
        val url = topicUrlJsonPoll(baseUrl, topic)
        Log.d(TAG, "Polling topic $url")

        val request = Request.Builder().url(url).build();
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Unexpected response ${response.code} when polling topic $url")
            }
            val body = response.body?.string()?.trim()
            if (body == null || body.isEmpty()) return emptyList()
            val notifications = body.lines().map { line ->
                fromString(subscriptionId, line)
            }
            Log.d(TAG, "Notifications: $notifications")
            return notifications
        }
    }

    fun subscribe(subscriptionId: Long, baseUrl: String, topic: String, since: Long, notify: (Notification) -> Unit, fail: (Exception) -> Unit): Call {
        val sinceVal = if (since == 0L) "all" else since.toString()
        val url = topicUrlJson(baseUrl, topic, sinceVal)
        Log.d(TAG, "Opening subscription connection to $url")

        val request = Request.Builder().url(url).build()
        val call = subscriberClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        throw Exception("Unexpected response ${response.code} when subscribing to topic $url")
                    }
                    val source = response.body?.source() ?: throw Exception("Unexpected response for $url: body is empty")
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: throw Exception("Unexpected response for $url: line is null")
                        val message = gson.fromJson(line, Message::class.java)
                        if (message.event == EVENT_MESSAGE) {
                            val notification = Notification(message.id, subscriptionId, message.time, message.message, false)
                            notify(notification)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connection to $url failed (1): ${e.message}", e)
                    fail(e)
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Connection to $url failed (2): ${e.message}", e)
                fail(e)
            }
        })
        return call
    }

    private fun fromString(subscriptionId: Long, s: String): Notification {
        val n = gson.fromJson(s, Message::class.java)
        return Notification(n.id, subscriptionId, n.time, n.message, false)
    }

    private data class Message(
        val id: String,
        val time: Long,
        val event: String,
        val message: String
    )

    companion object {
        private const val TAG = "NtfyApiService"
        private const val EVENT_MESSAGE = "message"
    }
}
