package org.envirocar.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.justai.aimybox.Aimybox
import com.justai.aimybox.api.aimybox.AimyboxRequest
import com.justai.aimybox.api.aimybox.AimyboxResponse
import com.justai.aimybox.core.CustomSkill
import com.justai.aimybox.model.Response
import org.envirocar.app.recording.RecordingService
import org.envirocar.core.utils.ServiceUtils

/**
 * we can create custom skills that enables the voice assistant to perform any actions right on the
 * device from where the user speaks their voice commands.
 * docs: https://help.aimybox.com/en/article/android-custom-skills-1a1j0x0/
 */
class MyCustomSkill(
    private val context: Context
) : CustomSkill<AimyboxRequest, AimyboxResponse> {
    
    /**
     * This method should return true if your custom skill can handle a particular Response
     * from the dialog API.
     */
    override fun canHandle(response: AimyboxResponse) = response.action == "my_action"

    /**
     * This method will be called by Aimybox service right after the user's speech was recognised.
     */
    override suspend fun onRequest(request: AimyboxRequest, aimybox: Aimybox): AimyboxRequest {
        // if user says "hello", show a toast -> just for testing.
        if (request.query == "hello") {
            // we have to show a toast on Ui thread
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "This is really cool", Toast.LENGTH_SHORT).show()
            }
        }

        if (request.query == "start") {
            Handler(Looper.getMainLooper()).post {
                val gpsOnlyIntent = Intent(context, RecordingService::class.java)
                ServiceUtils.startService(context, gpsOnlyIntent)
            }

        }

        request.data?.addProperty("my_string_property", "some string")
        request.data?.addProperty("my_number_property", 10)
        return request
    }

    /**
     * The main method of the custom skill that should perform the actual action for the particular
     * dialog API's Response).
     */
    override suspend fun onResponse(
        response: AimyboxResponse,
        aimybox: Aimybox,
        defaultHandler: suspend (Response) -> Unit
    ) {
        // perform some local action here
        aimybox.standby()
    }
}