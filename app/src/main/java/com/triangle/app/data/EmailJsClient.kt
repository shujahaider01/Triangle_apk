package com.triangle.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Native port of script.js's _authSendEmailCode() EmailJS call — same
 * account/service/template (see TriangleConfig), same request shape,
 * including the accessToken (EmailJS "Private Key") workaround needed
 * because neither the WebView's file:// origin nor this app's REST-only
 * client has a real domain EmailJS's Origin/Referer allowlist could match.
 */
object EmailJsClient {
    @Throws(Exception::class)
    suspend fun sendOtpEmail(toEmail: String, code: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("service_id", TriangleConfig.EMAILJS_SERVICE_ID)
            put("template_id", TriangleConfig.EMAILJS_TEMPLATE_ID)
            put("user_id", TriangleConfig.EMAILJS_PUBLIC_KEY)
            put("accessToken", TriangleConfig.EMAILJS_PRIVATE_KEY)
            put("template_params", JSONObject().apply {
                put("to_email", toEmail)
                put("code", code)
                put("app_name", "Triangle")
            })
        }
        send(body)
    }

    /**
     * See EmailInviteRepository — used both for a brand-new invite (no
     * account yet) and as the backup email when the invite matched an
     * existing user. No link/button: the app isn't published anywhere yet
     * to link to, and it isn't needed anyway — auto-connect on signup
     * matches by email address, not by anything in this email being clicked.
     */
    @Throws(Exception::class)
    suspend fun sendInviteEmail(toEmail: String, inviterName: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("service_id", TriangleConfig.EMAILJS_SERVICE_ID)
            put("template_id", TriangleConfig.EMAILJS_INVITE_TEMPLATE_ID)
            put("user_id", TriangleConfig.EMAILJS_PUBLIC_KEY)
            put("accessToken", TriangleConfig.EMAILJS_PRIVATE_KEY)
            put("template_params", JSONObject().apply {
                put("to_email", toEmail)
                put("inviter_name", inviterName)
                put("app_name", "Triangle")
            })
        }
        send(body)
    }

    private fun send(body: JSONObject) {
        val conn = URL("https://api.emailjs.com/api/v1.0/email/send").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code2 = conn.responseCode
            if (code2 !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw Exception("EmailJS rejected the request (HTTP $code2)${if (err.isNotBlank()) " — $err" else ""}")
            }
        } finally {
            conn.disconnect()
        }
    }
}
