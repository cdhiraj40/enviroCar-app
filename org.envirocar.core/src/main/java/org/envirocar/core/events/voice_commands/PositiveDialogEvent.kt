package org.envirocar.core.events.voice_commands

import com.justai.aimybox.Aimybox

data class PositiveDialogEvent(
    var aimybox: Aimybox,
    var message: String
)