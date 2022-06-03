/**
 * Copyright (C) 2013 - 2021 the enviroCar community
 *
 * This file is part of the enviroCar app.
 *
 * The enviroCar app is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The enviroCar app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with the enviroCar app. If not, see http://www.gnu.org/licenses/.
 */
package org.envirocar.app

import android.annotation.TargetApi
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.justai.aimybox.Aimybox
import com.justai.aimybox.components.AimyboxProvider
import com.justai.aimybox.core.Config
import com.justai.aimybox.speechkit.google.platform.GooglePlatformSpeechToText
import com.justai.aimybox.speechkit.google.platform.GooglePlatformTextToSpeech
import com.mapbox.mapboxsdk.Mapbox
import com.squareup.otto.Bus
import io.reactivex.disposables.CompositeDisposable
import org.acra.ACRA
import org.acra.BuildConfig
import org.acra.annotation.AcraCore
import org.envirocar.app.handler.ApplicationSettings
import org.envirocar.app.handler.LocationHandler
import org.envirocar.app.handler.userstatistics.UserStatisticsProcessor
import com.justai.aimybox.speechkit.kaldi.KaldiVoiceTrigger
import com.justai.aimybox.speechkit.kaldi.KaldiAssets
import org.envirocar.app.notifications.AutomaticUploadNotificationHandler
import org.envirocar.app.notifications.NotificationHandler
import org.envirocar.app.rxutils.RxBroadcastReceiver
import org.envirocar.core.injection.InjectApplicationScope
import org.envirocar.core.logging.ACRASenderFactory
import org.envirocar.core.logging.Logger
import org.envirocar.core.util.Util
import org.envirocar.remote.service.*
import java.util.*
import javax.inject.Inject

/**
 * @author dewall
 */
@AcraCore(
    buildConfigClass = BuildConfig::class,
    reportSenderFactoryClasses = [ACRASenderFactory::class]
)
class BaseApplication : Application(), AimyboxProvider {

    lateinit var baseApplicationComponent: BaseApplicationComponent

    private var mScreenReceiver: BroadcastReceiver? = null

    @Inject
    lateinit var userService: UserService

    @Inject
    lateinit var carService: CarService

    @Inject
    lateinit var trackService: TrackService

    @Inject
    lateinit var termsOfUseService: TermsOfUseService

    @Inject
    lateinit var fuelingService: FuelingService

    @Inject
    lateinit var announcementsService: AnnouncementsService

    @InjectApplicationScope
    @Inject
    lateinit var context: Context

    @Inject
    lateinit var statisticsProcessor: UserStatisticsProcessor

    @Inject
    lateinit var locationHandler: LocationHandler

    @Inject
    lateinit var mBus: Bus

    @Inject
    lateinit var automaticUploadHandler: AutomaticUploadNotificationHandler
    val disposables: CompositeDisposable = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()
        // hack
        Logger.addFileHandlerLocation(filesDir.absolutePath)
        Mapbox.getInstance(this, "")
        baseApplicationComponent = DaggerBaseApplicationComponent
            .builder()
            .baseApplicationModule(BaseApplicationModule(this))
            .build()
        baseApplicationComponent.inject(this)
        EnviroCarService.setCarService(carService)
        EnviroCarService.setAnnouncementsService(announcementsService)
        EnviroCarService.setFuelingService(fuelingService)
        EnviroCarService.setTermsOfUseService(termsOfUseService)
        EnviroCarService.setTrackService(trackService)
        EnviroCarService.setUserService(userService)
        NotificationHandler.context = context

        // Initialize ACRA
        ACRA.init(this)

        // debug logging setting listener
        disposables.add(
            ApplicationSettings.getDebugLoggingObservable(this)
                .doOnNext { isDebugLoggingEnabled: Boolean ->
                    setDebugLogging(
                        isDebugLoggingEnabled
                    )
                }
                .doOnError { e: Throwable? ->
                    LOG.error(
                        e
                    )
                }
                .subscribe())

        // obfuscation setting changed listener
        disposables.add(
            ApplicationSettings.getObfuscationObservable(this)
                .doOnNext { bool: Boolean ->
                    LOG.info(
                        "Obfuscation enabled: %s",
                        bool.toString()
                    )
                }
                .doOnError { e: Throwable? ->
                    LOG.error(
                        e
                    )
                }
                .subscribe())

        // register Intentfilter for logging screen changes
        val screenIntentFilter = IntentFilter()
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON)
        disposables.add(
            RxBroadcastReceiver.create(this, screenIntentFilter)
                .doOnNext { intent: Intent ->
                    if (intent.action == Intent.ACTION_SCREEN_OFF) {
                        // do whatever you need to do here
                        LOG.info("SCREEN IS OFF")
                    } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                        // and do whatever you need to do here
                        LOG.info("SCREEN IS ON")
                    }
                }
                .doOnError { e: Throwable? ->
                    LOG.error(
                        e
                    )
                }
                .subscribe())
    }

    override fun onTerminate() {
        super.onTerminate()
        if (mScreenReceiver != null) {
            unregisterReceiver(mScreenReceiver)
        }
        disposables.clear()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        LOG.info("onLowMemory called")
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        LOG.info("onTrimMemory called")
        LOG.info("maxMemory: " + Runtime.getRuntime().maxMemory())
        LOG.info("totalMemory: " + Runtime.getRuntime().totalMemory())
        LOG.info("freeMemory: " + Runtime.getRuntime().freeMemory())
    }

    private fun setDebugLogging(isDebugLoggingEnabled: Boolean) {
        LOG.info(
            "Received change in debug log level. Is enabled=",
            isDebugLoggingEnabled.toString()
        )
        Logger.initialize(Util.getVersionString(this@BaseApplication), isDebugLoggingEnabled)
    }


    override val aimybox by lazy { createAimybox(this) }

    private fun createAimybox(context: Context): Aimybox {
        // change model based on language
        val assets = KaldiAssets.fromApkAssets(this, "model/en")

        // trigger words
        val voiceTrigger = KaldiVoiceTrigger(assets, listOf("listen", "envirocar"))

        val unitId = UUID.randomUUID().toString()

        val textToSpeech = GooglePlatformTextToSpeech(context, Locale.getDefault())
        val speechToText = GooglePlatformSpeechToText(context, Locale.getDefault())

//        val dialogApi = AimyboxDialogApi(
//            AIMYBOX_API_KEY, unitId, customSkills = linkedSetOf(MyCustomSkill(context))
//        )
        val dialogApi = TestDialogApi(dummyCustomSkill = TestCustomSkill(mBus))

        return Aimybox(Config.create(speechToText, textToSpeech, dialogApi) {
            this.voiceTrigger = voiceTrigger
        }, context)
    }

    companion object {
        private val LOG = Logger.getLogger(
            BaseApplication::class.java
        )

//        private const val AIMYBOX_API_KEY = ""

        operator fun get(context: Context): BaseApplication {
            return context.applicationContext as BaseApplication
        }
    }
}