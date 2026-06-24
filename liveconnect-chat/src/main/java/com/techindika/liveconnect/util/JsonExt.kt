package com.techindika.liveconnect.util

import org.json.JSONObject

/**
 * Null-safe [JSONObject.optString] — returns null when the key is missing, the
 * value is explicit JSON `null`, or the value is empty.
 *
 * Plain `optString(key, "")` is NOT safe here: org.json renders an explicit
 * JSON `null` as the literal string `"null"`, which is how the chat showed a
 * "null" line above messages. Guarding with [isNull] avoids that.
 */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").ifEmpty { null }
