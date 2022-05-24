package org.envirocar.app

import android.annotation.SuppressLint
import com.justai.aimybox.Aimybox
import com.justai.aimybox.assistant.api.DummyResponse
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.dialogapi.dummy.DummyRequest
import com.justai.aimybox.model.Response
import com.justai.aimybox.model.TextSpeech
import com.squareup.otto.Bus
import org.envirocar.core.events.voice_commands.*

class TestCustomSkill(private val mBus: Bus?) :
    CustomSkill<DummyRequest, DummyResponse> {

    override fun canHandle(response: DummyResponse) = true

    @SuppressLint("MissingPermission")
    override suspend fun onResponse(
        response: DummyResponse,
        aimybox: Aimybox,
        defaultHandler: suspend (Response) -> Unit
    ) {
        if (response.query == "start") {
            mBus?.post(
                StartTrackEvent(
                    aimybox = aimybox,
                )
            )
        } else if (response.query == "stop") {
            mBus?.post(StopTrackEvent(aimybox = aimybox))
            aimybox.startRecognition()
        } else if (response.query == "yes do it" || response.query == "close") {
            mBus?.post(PositiveDialogEvent(aimybox = aimybox, message = "STOP_TRACK_DIALOG"))
        } else if (response.query == "change view" || response.query == "change look") {
            mBus?.post(
                RecordingTrackEvent(
                    aimybox = aimybox,
                    message = Enums.Recording.CHANGE_VIEW,
                    nextAction = Aimybox.NextAction.RECOGNITION
                )
            )
        } else if (response.query == "tell distance" || response.query == "can you tell me the distance") {
            mBus?.post(
                RecordingTrackEvent(
                    aimybox = aimybox,
                    message = Enums.Recording.DISTANCE,
                    nextAction = Aimybox.NextAction.RECOGNITION
                )
            )
        } else if (response.query == "tell current time" || response.query == "can you tell me current time") {
            mBus?.post(
                RecordingTrackEvent(
                    aimybox = aimybox,
                    message = Enums.Recording.TIME,
                    nextAction = Aimybox.NextAction.RECOGNITION
                )
            )
        } else if (response.query == "tell travel time" || response.query == "can you tell me travel time") {
            mBus?.post(
                RecordingTrackEvent(
                    aimybox = aimybox,
                    message = Enums.Recording.TRAVEL_TIME,
                    nextAction = Aimybox.NextAction.RECOGNITION
                )
            )
        } else {
            aimybox.speak(
                TextSpeech("Sorry, I did not understand that. Please try again!"),
                nextAction = Aimybox.NextAction.RECOGNITION
            )
        }
    }
}