package com.triangle.app.data

/**
 * Same Firebase/EmailJS constants script.js already hardcodes (see
 * assets/config.js and the "AUTH & ORGANIZATION ONBOARDING" section of
 * assets/script.js) — duplicated here, not read from the WebView, so the
 * native Auth/Dashboard screens work before a WebView ever loads.
 *
 * Carried forward as-is for Milestone 1 (native login/dashboard); the plan
 * flags these as worth moving off hardcoded client constants later, but
 * that's not specific to the native rewrite — the WebView build has always
 * shipped them this way.
 */
object TriangleConfig {
    const val FIREBASE_URL = "https://triangle-apk-default-rtdb.firebaseio.com"
    const val FIREBASE_WEB_API_KEY = "AIzaSyBvZeKsYjS-Y9qgHhQxRddsU7qUZmgYoro"

    // EmailJS — same account/service/template as script.js's OTP email step
    // (EMAILJS_SERVICE_ID/EMAILJS_TEMPLATE_ID/EMAILJS_PUBLIC_KEY/EMAILJS_PRIVATE_KEY).
    const val EMAILJS_SERVICE_ID = "service_dm5qnqj"
    const val EMAILJS_TEMPLATE_ID = "template_e1azwbs"
    const val EMAILJS_PUBLIC_KEY = "bRNGLcTaJW2tTsFC1"
    const val EMAILJS_PRIVATE_KEY = "20c9Z-zyjYmWJRMPaoTQw"

    // Invite email template (see emailjs-invite-template.html at the repo
    // root). Template params sent by EmailJsClient.sendInviteEmail():
    // to_email, inviter_name, app_name.
    const val EMAILJS_INVITE_TEMPLATE_ID = "template_lzxf5wq"

    // Fixed shared namespace for DM data — script.js's DM_ORG_ID is the
    // literal constant 'org1' from config.js, NOT the signed-in user's own
    // (dynamic, per-account) orgId. Every account's DM data lives under this
    // one shared path regardless of which org their tasks belong to
    // — see DmRepository's header comment for why this matters.
    const val DM_ORG_ID = "org1"

    // Fixed shared root for the social graph (usernames index, connections,
    // connection requests — see data/UsernameRepository.kt and the
    // Connect->Assign->Complete->Earn model). Same reasoning as DM_ORG_ID
    // above: two connected people live in two different, mutually-invisible
    // per-account orgs, so this state can't live inside either one.
    const val SOCIAL_ROOT = "social"
}
