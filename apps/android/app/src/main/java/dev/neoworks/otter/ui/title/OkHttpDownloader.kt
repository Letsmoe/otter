package dev.neoworks.otter.ui.title

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

private const val HTTP_TOO_MANY_REQUESTS = 429

/** NewPipeExtractor's HTTP layer on OkHttp. */
class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val okHttpResponse = client.newCall(buildOkHttpRequest(request)).execute()
        if (okHttpResponse.code == HTTP_TOO_MANY_REQUESTS) {
            okHttpResponse.close()
            throw ReCaptchaException("YouTube asked for a captcha", request.url())
        }
        val responseBody = okHttpResponse.body.string()
        return Response(
            okHttpResponse.code,
            okHttpResponse.message,
            okHttpResponse.headers.toMultimap(),
            responseBody,
            okHttpResponse.request.url.toString(),
        )
    }

    private fun buildOkHttpRequest(request: Request): okhttp3.Request {
        val builder = okhttp3.Request.Builder().url(request.url())
        val body = request.dataToSend()
        var requestBody: okhttp3.RequestBody? = null
        if (body != null) {
            requestBody = body.toRequestBody()
        }
        if (requestBody == null && request.httpMethod() == "POST") {
            requestBody = ByteArray(0).toRequestBody()
        }
        builder.method(request.httpMethod(), requestBody)
        builder.header("User-Agent", USER_AGENT)
        for ((headerName, headerValues) in request.headers()) {
            builder.removeHeader(headerName)
            headerValues.forEach { builder.addHeader(headerName, it) }
        }
        return builder.build()
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
