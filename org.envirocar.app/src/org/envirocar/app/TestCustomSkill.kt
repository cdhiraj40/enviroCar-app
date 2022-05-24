package org.envirocar.app

import android.annotation.SuppressLint
import com.justai.aimybox.Aimybox
import com.justai.aimybox.assistant.api.DummyResponse
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.dialogapi.dummy.DummyRequest
import com.justai.aimybox.model.Response
import com.justai.aimybox.model.TextSpeech
import org.envirocar.core.events.voice_commands.*
import org.greenrobot.eventbus.EventBus


class TestCustomSkill : CustomSkill<DummyRequest, DummyResponse> {

    override fun canHandle(response: DummyResponse) = true

    @SuppressLint("MissingPermission")
    override suspend fun onResponse(
        response: DummyResponse,
        aimybox: Aimybox,
        defaultHandler: suspend (Response) -> Unit
    ) {
        if (response.query == "start") {
            EventBus.getDefault().post(
                StartTrackEvent(
                    aimybox = aimybox,
                )
            )
        } else if (response.query == "stop") {
            EventBus.getDefault().post(StopTrackEvent(aimybox = aimybox))
            aimybox.startRecognition()
        } else if (response.query == "yes do it" || response.query == "close") {
            EventBus.getDefault()
                .post(PositiveDialogEvent(aimybox = aimybox, message = "STOP_TRACK_DIALOG"))
        } else if (response.query == "change view" || response.query == "change look") {
            EventBus.getDefault()
                .post(
                    RecordingTrackEvent(
                        aimybox = aimybox,
                        message = Enums.Recording.CHANGE_VIEW,
                        nextAction = Aimybox.NextAction.RECOGNITION
                    )
                )
        } else if (response.query == "tell distance" || response.query == "can you tell me the distance") {
            EventBus.getDefault()
                .post(
                    RecordingTrackEvent(
                        aimybox = aimybox,
                        message = Enums.Recording.DISTANCE,
                        nextAction = Aimybox.NextAction.RECOGNITION
                    )
                )
        } else if (response.query == "tell current time" || response.query == "can you tell me current time") {
            EventBus.getDefault()
                .post(
                    RecordingTrackEvent(
                        aimybox = aimybox,
                        message = Enums.Recording.TIME,
                        nextAction = Aimybox.NextAction.RECOGNITION
                    )
                )
        } else if (response.query == "tell travel time" || response.query == "can you tell me travel time") {
            EventBus.getDefault()
                .post(
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