package org.envirocar.core.events.voice_commands

import com.justai.aimybox.Aimybox

data class RecordingTrackEvent(
    var aimybox: Aimybox,
    var message: Enums.Recording,
    var nextAction: Aimybox.NextAction ?= null
)