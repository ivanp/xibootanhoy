package org.xiboplayer.player.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around OkHttp for XMDS SOAP calls.
 */
class OkHttpClientWrapper(
    private val connectTimeout: Long = 30,
    private val readTimeout: Long = 60
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeout, TimeUnit.SECONDS)
        .readTimeout(readTimeout, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val xmlMediaType = "text/xml; charset=utf-8".toMediaType()

    fun post(url: String, body: String, headers: Map<String, String>): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(xmlMediaType))

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: throw XmdsException("Empty response from $url")

        if (!response.isSuccessful) {
            throw XmdsException("HTTP ${response.code}: $responseBody")
        }

        return responseBody
    }

    fun getBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw XmdsException("HTTP ${response.code} downloading $url")
        }
        val body = response.body ?: throw XmdsException("Empty body from $url")
        return body.bytes()
    }

    /**
     * Download a file directly to disk via streaming — avoids OOM on large files.
     * Returns the MD5 hex digest of the downloaded content for verification.
     */
    fun downloadToFile(url: String, destPath: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw XmdsException("HTTP ${response.code} downloading $url")
        }
        val body = response.body ?: throw XmdsException("Empty body from $url")
        val digest = java.security.MessageDigest.getInstance("MD5")
        body.byteStream().use { input ->
            java.io.FileOutputStream(destPath).use { output ->
                val buf = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buf).also { bytesRead = it } != -1) {
                    output.write(buf, 0, bytesRead)
                    digest.update(buf, 0, bytesRead)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
