package com.osuchat.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.gson.JsonParser
import com.osuchat.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.math.roundToInt

@Singleton
class ImageUploadRepository internal constructor(
    private val context: Context?,
    okHttpClient: OkHttpClient = buildStandaloneUploadClient(),
    private val litterboxUrl: String = LITTERBOX_UPLOAD_URL,
    private val catboxUrl: String = CATBOX_UPLOAD_URL,
    private val uguuUrl: String = UGUU_UPLOAD_URL,
    private val tmpfilesUrl: String = TMPFILES_UPLOAD_URL,
    private val fallbackUrl: String = ZERO_X_ZERO_UPLOAD_URL
) {
    internal constructor(
        context: Context?,
        okHttpClient: OkHttpClient,
        primaryUrl: String,
        fallbackUrl: String
    ) : this(
        context = context,
        okHttpClient = okHttpClient,
        litterboxUrl = primaryUrl,
        catboxUrl = "",
        uguuUrl = "",
        tmpfilesUrl = "",
        fallbackUrl = fallbackUrl
    )

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @Suppress("UNUSED_PARAMETER") okHttpClient: OkHttpClient
    ) : this(
        context = context,
        okHttpClient = buildStandaloneUploadClient(),
        litterboxUrl = LITTERBOX_UPLOAD_URL,
        catboxUrl = CATBOX_UPLOAD_URL,
        uguuUrl = UGUU_UPLOAD_URL,
        tmpfilesUrl = TMPFILES_UPLOAD_URL,
        fallbackUrl = ZERO_X_ZERO_UPLOAD_URL
    )

    private val uploadClient: OkHttpClient = okHttpClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun compressAndUpload(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = compressImageToTempCache(uri)
            uploadCompressedFile(tempFile)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            logError("compressAndUpload failed (${t::class.java.simpleName}: ${t.message})", t)
            Result.failure(t)
        } finally {
            runCatching { tempFile?.delete() }
        }
    }

    suspend fun compressImageToTempCache(uri: Uri): File = withContext(Dispatchers.IO) {
        val appContext = requireNotNull(context) { "Application context is required to read image Uri" }
        val resolver = appContext.contentResolver
        val rawTempFile = File(
            appContext.cacheDir,
            "raw_upload_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.tmp"
        )

        try {
            var streamErr: Throwable? = null
            var pfdErr: Throwable? = null

            val copiedViaStream = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    rawTempFile.outputStream().use { out ->
                        input.copyTo(out)
                    }
                    true
                } ?: false
            }.onFailure { err ->
                if (err is CancellationException) throw err
                streamErr = err
            }.getOrDefault(false)

            if (!copiedViaStream || !rawTempFile.exists() || rawTempFile.length() <= 0L) {
                runCatching {
                    resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        FileInputStream(pfd.fileDescriptor).use { input ->
                            rawTempFile.outputStream().use { out ->
                                input.copyTo(out)
                            }
                        }
                        true
                    } ?: false
                }.onFailure { err ->
                    if (err is CancellationException) throw err
                    pfdErr = err
                }
            }

            if (!rawTempFile.exists() || rawTempFile.length() <= 0L) {
                val streamInfo = streamErr?.let { "${it::class.java.simpleName}: ${it.message}" } ?: "null/empty stream"
                val pfdInfo = pfdErr?.let { "${it::class.java.simpleName}: ${it.message}" } ?: "null/empty pfd"
                logError("Failed to copy selected image uri=$uri (stream=$streamInfo, pfd=$pfdInfo)", pfdErr ?: streamErr)
                throw IOException("Could not open selected image", pfdErr ?: streamErr)
            }

            val workingBitmap = decodeScaledBitmap(rawTempFile)
            try {
                val outFile = File(
                    appContext.cacheDir,
                    "upload_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
                )
                val compressed = outFile.outputStream().use { out ->
                    workingBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                if (!compressed || !outFile.exists() || outFile.length() <= 0L) {
                    outFile.delete()
                    logError("Bitmap.compress returned $compressed (size=${outFile.length()})")
                    throw IOException("Failed to compress image")
                }
                outFile
            } finally {
                if (!workingBitmap.isRecycled) {
                    workingBitmap.recycle()
                }
            }
        } finally {
            runCatching { rawTempFile.delete() }
        }
    }

    private fun decodeScaledBitmap(rawFile: File): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(rawFile)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val srcW = info.size.width
                    val srcH = info.size.height
                    val longest = maxOf(srcW, srcH)
                    if (longest > MAX_DIMENSION_PX) {
                        val scale = MAX_DIMENSION_PX.toFloat() / longest.toFloat()
                        val targetW = (srcW * scale).roundToInt().coerceAtLeast(1)
                        val targetH = (srcH * scale).roundToInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                logError("ImageDecoder failed (${e::class.java.simpleName}: ${e.message})", e)
            }
        }
        return decodeWithBitmapFactory(rawFile)
    }

    private fun decodeWithBitmapFactory(rawFile: File): Bitmap {
        val path = rawFile.absolutePath
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            logError("BitmapFactory bounds decode failed (${boundsOpts.outWidth}x${boundsOpts.outHeight})")
            throw IOException("Unsupported or unreadable image format")
        }

        val exifOrientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        var inSampleSize = 1
        val maxOrig = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        while (maxOrig / (inSampleSize * 2) >= MAX_DIMENSION_PX) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, decodeOpts) ?: run {
            logError("BitmapFactory.decodeFile returned null (inSampleSize=$inSampleSize)")
            throw IOException("Could not decode selected image")
        }

        var workingBitmap: Bitmap = decoded
        try {
            val oriented = applyExifOrientation(workingBitmap, exifOrientation)
            if (oriented !== workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = oriented
            }

            val longestEdge = maxOf(workingBitmap.width, workingBitmap.height)
            if (longestEdge > MAX_DIMENSION_PX) {
                val scale = MAX_DIMENSION_PX.toFloat() / longestEdge.toFloat()
                val targetW = (workingBitmap.width * scale).roundToInt().coerceAtLeast(1)
                val targetH = (workingBitmap.height * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(workingBitmap, targetW, targetH, true)
                if (scaled !== workingBitmap) {
                    workingBitmap.recycle()
                    workingBitmap = scaled
                }
            }
            return workingBitmap
        } catch (t: Throwable) {
            if (!workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
            throw t
        }
    }

    private fun logError(msg: String, t: Throwable? = null) {
        runCatching { Log.e(TAG, msg, t) }
    }

    private class UploadHttpException(val code: Int) : IOException("HTTP $code")
    private class UploadInvalidResponseException(message: String) : IOException(message)

    private data class UploadHostAttempt(
        val label: String,
        val upload: (File) -> String
    )

    suspend fun uploadCompressedFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext Result.failure(IOException("Compressed image file is missing"))
        }

        val attempts = buildList {
            if (litterboxUrl.isNotBlank()) add(UploadHostAttempt("litterbox", ::uploadToLitterbox))
            if (catboxUrl.isNotBlank()) add(UploadHostAttempt("catbox", ::uploadToCatbox))
            if (uguuUrl.isNotBlank()) add(UploadHostAttempt("uguu", ::uploadToUguu))
            if (tmpfilesUrl.isNotBlank()) add(UploadHostAttempt("tmpfiles", ::uploadToTmpfiles))
            if (fallbackUrl.isNotBlank()) add(UploadHostAttempt("0x0.st", ::uploadToZeroXZero))
        }

        if (attempts.isEmpty()) {
            return@withContext Result.failure(IOException("No image upload hosts configured"))
        }

        val failures = mutableListOf<String>()
        for (attempt in attempts) {
            try {
                val uploadedUrl = attempt.upload(file)
                return@withContext Result.success(uploadedUrl)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                logError("Upload host ${attempt.label} failed: ${t::class.java.simpleName}: ${t.message}", t)
                failures.add("${attempt.label}: ${shortFailureReason(t)}")
            }
        }

        Result.failure(IOException("Upload failed — ${failures.joinToString(", ")}"))
    }

    private fun uploadToLitterbox(file: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("time", "72h")
            .addFormDataPart("fileToUpload", file.name, file.asRequestBody(JPEG_MEDIA_TYPE))
            .build()
        val rawBody = executeRawUploadRequest(litterboxUrl, body)
        return extractPlainTextUrl(rawBody, allowHttpLocal = true)
    }

    private fun uploadToCatbox(file: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("fileToUpload", file.name, file.asRequestBody(JPEG_MEDIA_TYPE))
            .build()
        val rawBody = executeRawUploadRequest(catboxUrl, body)
        return extractPlainTextUrl(rawBody, allowHttpLocal = true)
    }

    private fun uploadToUguu(file: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("files[]", file.name, file.asRequestBody(JPEG_MEDIA_TYPE))
            .build()
        val rawBody = executeRawUploadRequest(uguuUrl, body)
        rejectHtmlOrBlank(rawBody)
        if (rawBody.startsWith("{")) {
            val jsonUrl = runCatching {
                JsonParser.parseString(rawBody).asJsonObject
                    .getAsJsonArray("files")
                    ?.firstOrNull()
                    ?.asJsonObject
                    ?.get("url")
                    ?.asString
            }.getOrNull()
            if (!jsonUrl.isNullOrBlank()) {
                return validateDirectUrl(jsonUrl, allowHttpLocal = true)
            }
        }
        return extractPlainTextUrl(rawBody, allowHttpLocal = true)
    }

    private fun uploadToTmpfiles(file: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(JPEG_MEDIA_TYPE))
            .build()
        val rawBody = executeRawUploadRequest(tmpfilesUrl, body)
        rejectHtmlOrBlank(rawBody)
        val rawUrl = runCatching {
            JsonParser.parseString(rawBody).asJsonObject
                .getAsJsonObject("data")
                ?.get("url")
                ?.asString
        }.getOrNull()?.trim().orEmpty()
        if (rawUrl.isEmpty()) {
            throw UploadInvalidResponseException("invalid")
        }
        val directUrl = rewriteTmpfilesDirectUrl(rawUrl)
        return validateDirectUrl(directUrl, allowHttpLocal = false)
    }

    private fun uploadToZeroXZero(file: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(JPEG_MEDIA_TYPE))
            .build()
        val rawBody = executeRawUploadRequest(fallbackUrl, body)
        return extractPlainTextUrl(rawBody, allowHttpLocal = true)
    }

    private fun executeRawUploadRequest(url: String, body: MultipartBody): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UPLOAD_USER_AGENT)
            .post(body)
            .build()

        uploadClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string()?.trim().orEmpty()
            if (!response.isSuccessful) {
                throw UploadHttpException(response.code)
            }
            return rawBody
        }
    }

    private fun rejectHtmlOrBlank(rawBody: String) {
        if (rawBody.isBlank()) {
            throw UploadInvalidResponseException("invalid")
        }
        val lower = rawBody.lowercase()
        if (rawBody.startsWith("<") || lower.contains("<!doctype") || lower.contains("<html")) {
            throw UploadInvalidResponseException("invalid")
        }
    }

    private fun extractPlainTextUrl(rawBody: String, allowHttpLocal: Boolean): String {
        rejectHtmlOrBlank(rawBody)
        val candidate = rawBody.lineSequence()
            .map { sanitizeUrlString(it) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        return validateDirectUrl(candidate, allowHttpLocal)
    }

    private fun validateDirectUrl(rawUrl: String, allowHttpLocal: Boolean): String {
        val trimmed = sanitizeUrlString(rawUrl)
        val isHttps = trimmed.startsWith("https://", ignoreCase = true)
        val isHttp = trimmed.startsWith("http://", ignoreCase = true)
        if (!isHttps && !isHttp) {
            throw UploadInvalidResponseException("invalid")
        }
        val withoutScheme = trimmed.substringAfter("://", "")
        val slashIdx = withoutScheme.indexOf('/')
        if (slashIdx <= 0 || withoutScheme.substring(slashIdx).trim('/').isEmpty()) {
            throw UploadInvalidResponseException("invalid")
        }
        if (isHttp) {
            val host = withoutScheme.substring(0, slashIdx).substringBefore(':').lowercase()
            val isLocalhost = host == "127.0.0.1" || host == "localhost" || host == "::1"
            if (!allowHttpLocal && !isLocalhost) {
                return "https://${trimmed.substring(7)}"
            }
        }
        return trimmed
    }

    private fun sanitizeUrlString(raw: String): String {
        return raw.replace("\r", "")
            .replace("\n", "")
            .filterNot { it.isISOControl() }
            .trim()
    }

    private fun rewriteTmpfilesDirectUrl(rawUrl: String): String {
        val httpsUrl = if (rawUrl.startsWith("http://", ignoreCase = true)) {
            "https://${rawUrl.substring(7)}"
        } else {
            rawUrl
        }
        val schemeSep = httpsUrl.indexOf("://")
        if (schemeSep <= 0) return httpsUrl
        val hostStart = schemeSep + 3
        val pathStart = httpsUrl.indexOf('/', hostStart)
        if (pathStart < 0) return httpsUrl
        val authority = httpsUrl.substring(0, pathStart)
        val path = httpsUrl.substring(pathStart)
        return if (path.startsWith("/dl/", ignoreCase = true)) {
            httpsUrl
        } else {
            "$authority/dl$path"
        }
    }

    private fun shortFailureReason(err: Throwable): String {
        if (err is UploadHttpException) return err.code.toString()
        if (err is SocketTimeoutException ||
            err.message?.contains("timeout", ignoreCase = true) == true ||
            err.message?.contains("timed out", ignoreCase = true) == true
        ) {
            return "timeout"
        }
        if (err is UnknownHostException) return "dns"
        if (err is ConnectException) return "connect"
        if (err is SSLException) return "tls"
        if (err is UploadInvalidResponseException) return "invalid"
        return "error"
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val TAG = "ImageUploadRepository"
        const val LITTERBOX_UPLOAD_URL = "https://litterbox.catbox.moe/resources/internals/api.php"
        const val CATBOX_UPLOAD_URL = "https://catbox.moe/user/api.php"
        const val UGUU_UPLOAD_URL = "https://uguu.se/upload?output=text"
        const val TMPFILES_UPLOAD_URL = "https://tmpfiles.org/api/v1/upload"
        const val ZERO_X_ZERO_UPLOAD_URL = "https://0x0.st"
        const val PRIMARY_UPLOAD_URL = LITTERBOX_UPLOAD_URL
        const val FALLBACK_UPLOAD_URL = ZERO_X_ZERO_UPLOAD_URL
        private const val MAX_DIMENSION_PX = 1440
        private const val JPEG_QUALITY = 80
        val UPLOAD_USER_AGENT: String = "OsuCompanion/${BuildConfig.VERSION_NAME}"
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

        private fun buildStandaloneUploadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
