package org.envirocar.app.kaldi_speech_kit

import org.json.JSONObject

fun String.parseResult(): String {
    return JSONObject(this).optString("text")
}

fun String.parsePartial(): String {
    return JSONObject(this).optString("partial")
}