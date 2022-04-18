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
package org.envirocar.app.views.dashboard

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.transition.*
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.ViewModelProvider
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnCheckedChanged
import butterknife.OnClick
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.Task
import com.jakewharton.rxbinding3.appcompat.itemClicks
import com.justai.aimybox.Aimybox
import com.justai.aimybox.components.AimyboxAssistantViewModel
import com.justai.aimybox.components.AimyboxProvider
import com.squareup.otto.Subscribe
import info.hoang8f.android.segmented.SegmentedGroup
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableCompletableObserver
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.envirocar.app.BaseApplicationComponent
import org.envirocar.app.R
import org.envirocar.app.handler.ApplicationSettings
import org.envirocar.app.handler.BluetoothHandler
import org.envirocar.app.handler.preferences.UserPreferenceHandler
import org.envirocar.app.handler.userstatistics.UserStatisticsUpdateEvent
import org.envirocar.app.injection.BaseInjectorFragment
import org.envirocar.app.recording.RecordingService
import org.envirocar.app.recording.RecordingState
import org.envirocar.app.recording.RecordingType
import org.envirocar.app.recording.events.EngineNotRunningEvent
import org.envirocar.app.recording.events.RecordingStateEvent
import org.envirocar.app.views.carselection.CarSelectionActivity
import org.envirocar.app.views.login.SigninActivity
import org.envirocar.app.views.obdselection.OBDSelectionActivity
import org.envirocar.app.views.recordingscreen.RecordingScreenActivity
import org.envirocar.app.views.utils.DialogUtils
import org.envirocar.app.views.utils.SizeSyncTextView
import org.envirocar.app.views.utils.SizeSyncTextView.OnTextSizeChangedListener
import org.envirocar.core.entity.User
import org.envirocar.core.events.NewCarTypeSelectedEvent
import org.envirocar.core.events.NewUserSettingsEvent
import org.envirocar.core.events.bluetooth.BluetoothDeviceSelectedEvent
import org.envirocar.core.events.bluetooth.BluetoothStateChangedEvent
import org.envirocar.core.events.gps.GpsStateChangedEvent
import org.envirocar.core.logging.Logger
import org.envirocar.core.utils.PermissionUtils
import org.envirocar.core.utils.ServiceUtils
import org.envirocar.obd.events.TrackRecordingServiceStateChangedEvent
import org.envirocar.obd.service.BluetoothServiceState
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

/**
 * @author dewall
 */
class DashboardFragment : BaseInjectorFragment(), CoroutineScope {
    // View Injections
    @BindView(R.id.fragment_dashboard_toolbar)
    lateinit var toolbar: Toolbar

    @BindView(R.id.fragment_dashboard_username)
    lateinit var textView: TextView

    @BindView(R.id.fragment_dashboard_logged_in_layout)
    lateinit var loggedInLayout: View

    @BindView(R.id.fragment_dashboard_user_tracks_layout)
    lateinit var userTracksLayout: View

    @BindView(R.id.fragment_dashboard_user_tracks_textview)
    lateinit var userTracksTextView: TextView

    @BindView(R.id.fragment_dashboard_user_distance_layout)
    lateinit var userDistanceLayout: View

    @BindView(R.id.fragment_dashboard_user_distance_textview)
    lateinit var userDistanceTextView: TextView

    @BindView(R.id.fragment_dashboard_user_duration_layout)
    lateinit var userDurationLayout: View

    @BindView(R.id.fragment_dashboard_user_duration_textview)
    lateinit var userDurationTextView: TextView

    @BindView(R.id.fragment_dashboard_user_statistics_progress)
    lateinit var userStatProgressBar: ProgressBar

    @BindView(R.id.fragment_dashboard_indicator_view)
    lateinit var indicatorView: ViewGroup

    @BindView(R.id.fragment_dashboard_indicator_bluetooth_layout)
    lateinit var bluetoothIndicatorLayout: View

    @BindView(R.id.fragment_dashboard_indicator_bluetooth)
    lateinit var bluetoothIndicator: ImageView

    @BindView(R.id.fragment_dashboard_indicator_obd_layout)
    lateinit var obdIndicatorLayout: View

    @BindView(R.id.fragment_dashboard_indicator_obd)
    lateinit var obdIndicator: ImageView

    @BindView(R.id.fragment_dashboard_indicator_gps)
    lateinit var gpsIndicator: ImageView

    @BindView(R.id.fragment_dashboard_indicator_car)
    lateinit var carIndicator: ImageView

    @BindView(R.id.fragment_dashboard_indicator_bluetooth_text)
    lateinit var bluetoothIndicatorText: SizeSyncTextView

    @BindView(R.id.fragment_dashboard_indicator_obd_text)
    lateinit var obdIndicatorText: SizeSyncTextView

    @BindView(R.id.fragment_dashboard_indicator_gps_text)
    lateinit var gpsIndicatorText: SizeSyncTextView

    @BindView(R.id.fragment_dashboard_indicator_car_text)
    lateinit var carIndicatorText: SizeSyncTextView

    @BindView(R.id.fragment_dashboard_mode_selector)
    lateinit var modeSegmentedGroup: SegmentedGroup

    @BindView(R.id.fragment_dashboard_obd_mode_button)
    lateinit var obdModeRadioButton: RadioButton

    @BindView(R.id.fragment_dashboard_gps_mode_button)
    lateinit var gpsModeRadioButton: RadioButton

    @BindView(R.id.fragment_dashboard_obdselection_layout)
    lateinit var bluetoothSelectionView: ViewGroup

    @BindView(R.id.fragment_dashboard_obdselection_text_primary)
    lateinit var bluetoothSelectionTextPrimary: TextView

    @BindView(R.id.fragment_dashboard_obdselection_text_secondary)
    lateinit var bluetoothSelectionTextSecondary: TextView

    @BindView(R.id.fragment_dashboard_carselection_text_primary)
    lateinit var carSelectionTextPrimary: TextView

    @BindView(R.id.fragment_dashboard_carselection_text_secondary)
    lateinit var carSelectionTextSecondary: TextView

    @BindView(R.id.fragment_dashboard_banner)
    lateinit var bannerLayout: FrameLayout

    @BindView(R.id.fragment_dashboard_main_layout)
    lateinit var mainLayout: ConstraintLayout

    @BindView(R.id.fragment_dashboard_start_track_button)
    lateinit var startTrackButton: View

    @BindView(R.id.fragment_dashboard_start_track_button_text)
    lateinit var startTrackButtonText: TextView

    // injected lateinit variables
    @Inject
    lateinit var userHandler: UserPreferenceHandler

    @Inject
    lateinit var bluetoothHandler: BluetoothHandler
    lateinit var disposables: CompositeDisposable
    private var statisticsKnown = false

    // some private variables
    private var connectingDialog: AlertDialog? = null
    private lateinit var deviceDiscoveryTimer: CountDownTimer
    private lateinit var indicatorSyncGroup: MutableList<SizeSyncTextView?>
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var appUpdateInfoTask: Task<AppUpdateInfo>


    private lateinit var viewModel: AimyboxAssistantViewModel
    private var revealTimeMs = 0L

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val aimyboxProvider = requireNotNull(findAimyboxProvider()) {
            "Parent Activity or Application must implement AimyboxProvider interface"
        }

        if (!::viewModel.isInitialized) {
            viewModel =
                ViewModelProvider(requireActivity(), aimyboxProvider.getViewModelFactory())
                    .get(AimyboxAssistantViewModel::class.java)

            val initialPhrase = arguments?.getString(ARGUMENTS_KEY)
                ?: context.getString(R.string.initial_phrase)

            viewModel.setInitialPhrase(initialPhrase)
        }

        revealTimeMs = context.resources.getInteger(R.integer.assistant_reveal_time_ms).toLong()
    }

    private var welcomeMessageShown = false
    override fun injectDependencies(baseApplicationComponent: BaseApplicationComponent) {
        baseApplicationComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // for the login/register button
        setHasOptionsMenu(true)
        disposables = CompositeDisposable()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate view first
        val contentView = inflater.inflate(R.layout.fragment_dashboard_view_new, container, false)

        // Bind views
        ButterKnife.bind(this, contentView)

        // inflate menus and init toolbar clicks
        toolbar.inflateMenu(R.menu.menu_dashboard_logged_out)
        toolbar.overflowIcon!!.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
        toolbar.itemClicks().subscribe { menuItem: MenuItem ->
            onToolbarItemClicked(
                menuItem
            )
        }
        appUpdateManager = AppUpdateManagerFactory.create(requireContext())
        appUpdateInfoTask = appUpdateManager.appUpdateInfo

        //
        updateUserLogin(userHandler.user)

        // init the text size synchronization
        initTextSynchronization()

        // set recording state
        ApplicationSettings.getSelectedRecordingTypeObservable(context)
            .doOnNext { selectedRT: RecordingType ->
                setRecordingMode(
                    selectedRT
                )
            }
            .doOnError { e: Throwable? ->
                LOG.error(
                    e
                )
            }
            .blockingFirst()

        if(!PermissionUtils.hasAudioPermission(context)) {
            checkAndRequestPermissions()
        }
        return contentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.aimyboxState.observe(viewLifecycleOwner) { state ->
            if (state == Aimybox.State.LISTENING) {
                Toast.makeText(requireContext(), "STARTED LISTENING", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun findAimyboxProvider(): AimyboxProvider? {
        val activity = requireActivity()
        val application = activity.application
        return when {
            activity is AimyboxProvider -> activity
            application is AimyboxProvider -> application
            else -> null
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatisticsVisibility(statisticsKnown)
        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                if (appUpdateInfo.updateAvailability()
                    == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo, AppUpdateType.IMMEDIATE,
                            requireActivity(), 121
                        )
                    } catch (e: SendIntentException) {
                        e.printStackTrace()
                    }
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {

            when (requestCode) {
                LOCATION_PERMISSION_REQUEST_CODE -> {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        LOG.info("Location permission has been granted")
                        Snackbar.make(
                            requireView(), "Location Permission granted.",
                            BaseTransientBottomBar.LENGTH_SHORT
                        ).show()
                        onStartTrackButtonClicked()
                    } else {
                        LOG.info("Location permission has been denied")
                        Snackbar.make(
                            requireView(), "Location Permission denied.",
                            BaseTransientBottomBar.LENGTH_LONG
                        ).show()
                    }
                }
                AUDIO_PERMISSION_REQUEST_CODE -> {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        LOG.info("Audio permission has been granted")
                        Snackbar.make(
                            requireView(), getString(R.string.audio_permission_granted),
                            BaseTransientBottomBar.LENGTH_SHORT
                        ).show()
                        if(!PermissionUtils.hasLocationPermission(context)){
                            requestPermissions(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                LOCATION_PERMISSION_REQUEST_CODE
                            )
                        }
                    } else {
                        LOG.info("audio permission has been denied")
                        Snackbar.make(
                            requireView(), getString(R.string.audio_permission_denied),
                            BaseTransientBottomBar.LENGTH_LONG
                        ).show()
                    }
                }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun onToolbarItemClicked(menuItem: MenuItem) {
        LOG.info(String.format("Toolbar - Clicked on %s", menuItem.title))
        if (menuItem.itemId == R.id.dashboard_action_login) {
            // starting the login activity
            SigninActivity.startActivity(context)
        } else if (menuItem.itemId == R.id.dashboard_action_logout) {
            // show a logout dialog
            MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialDialog)
                .setTitle(R.string.menu_logout_envirocar_title)
                .setMessage(R.string.menu_logout_envirocar_content)
                .setIcon(R.drawable.ic_logout_white_24dp)
                .setPositiveButton(
                    R.string.menu_logout_envirocar_positive
                ) { dialog, which -> userHandler.logOut().subscribe(onLogoutSubscriber()) }
                .setNegativeButton(R.string.menu_logout_envirocar_negative, null)
                .show()
        }
    }

    private fun onLogoutSubscriber(): DisposableCompletableObserver {
        return object : DisposableCompletableObserver() {
            private var dialog: MaterialDialog? = null
            private var userTemp: User? = null
            public override fun onStart() {
                userTemp = userHandler.user
                // show progress dialog for the deletion
                dialog = MaterialDialog.Builder(requireContext())
                    .title(R.string.activity_login_logout_progress_dialog_title)
                    .content(R.string.activity_login_logout_progress_dialog_content)
                    .progress(true, 0)
                    .cancelable(false)
                    .show()
            }

            override fun onComplete() {
                // Show a snackbar that indicates the finished logout
                Snackbar.make(
                    requireActivity().findViewById(R.id.snackbar_placeholder),
                    String.format(getString(R.string.goodbye_message), userTemp!!.username),
                    Snackbar.LENGTH_LONG
                ).show()
                dialog!!.dismiss()
            }

            override fun onError(e: Throwable) {
                LOG.error(e.message, e)
                dialog!!.dismiss()
            }
        }
    }

    @OnCheckedChanged(
        R.id.fragment_dashboard_obd_mode_button,
        R.id.fragment_dashboard_gps_mode_button
    )
    fun onModeChangedClicked(button: CompoundButton, checked: Boolean) {
        if (!checked) return
        var selectedRT =
            if (button.id == R.id.fragment_dashboard_obd_mode_button) RecordingType.OBD_ADAPTER_BASED else RecordingType.ACTIVITY_RECOGNITION_BASED

        // if the GPS tracking is not enabled then set recording type to OBD.
        if (!ApplicationSettings.isGPSBasedTrackingEnabled(context)) {
            selectedRT = RecordingType.OBD_ADAPTER_BASED
        }
        LOG.info("Mode selected " + button.text)

        // adjust the ui
        setRecordingMode(selectedRT)

        // update the selected recording type
        ApplicationSettings.setSelectedRecordingType(context, selectedRT)
        // update button
        var setEnabled = false
        when (button.id) {
            R.id.fragment_dashboard_gps_mode_button -> setEnabled = (carIndicator.isActivated
                    && gpsIndicator.isActivated)
            R.id.fragment_dashboard_obd_mode_button -> setEnabled =
                (bluetoothIndicator.isActivated
                        && gpsIndicator.isActivated
                        && obdIndicator.isActivated
                        && carIndicator.isActivated)
        }
        startTrackButtonText.setText(R.string.dashboard_start_track)
        startTrackButton.isEnabled = setEnabled
    }

    private fun setRecordingMode(selectedRT: RecordingType) {
        if (!ApplicationSettings.isGPSBasedTrackingEnabled(context)) {
            modeSegmentedGroup.visibility = View.GONE
        }

        // check whether OBD is visible or not.
        val visibility =
            if (selectedRT == RecordingType.OBD_ADAPTER_BASED) View.VISIBLE else View.GONE
        if (visibility == View.GONE) {
            gpsModeRadioButton.isChecked = true
            obdModeRadioButton.isChecked = false
        }

        // shared transition set
        val transitionSet = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(AutoTransition())
            .addTransition(Slide(Gravity.LEFT))

        // animate transition
        TransitionManager.beginDelayedTransition(modeSegmentedGroup)
        TransitionManager.beginDelayedTransition(bluetoothSelectionView, transitionSet)
        bluetoothSelectionView.visibility = visibility

        // indicator transition
        TransitionManager.beginDelayedTransition(indicatorView, transitionSet)
        bluetoothIndicatorLayout.visibility = visibility
        obdIndicatorLayout.visibility = visibility
    }

    // OnClick Handler
    @OnClick(R.id.fragment_dashboard_carselection_layout)
    fun onCarSelectionClicked() {
        LOG.info("Clicked on Carselection.")
        val intent = Intent(activity, CarSelectionActivity::class.java)
        requireActivity().startActivity(intent)
    }

    @OnClick(R.id.fragment_dashboard_obdselection_layout)
    fun onBluetoothSelectionClicked() {
        LOG.info("Clicked on Bluetoothselection.")
        val intent = Intent(activity, OBDSelectionActivity::class.java)
        requireActivity().startActivity(intent)
    }

    @OnClick(R.id.fragment_dashboard_start_track_button)
    fun onStartTrackButtonClicked() {
        LOG.info("Clicked on Start Track Button")
        if (RecordingService.RECORDING_STATE == RecordingState.RECORDING_RUNNING) {
            RecordingScreenActivity.navigate(context)
            return
        } else if (!PermissionUtils.hasLocationPermission(context)) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            when (modeSegmentedGroup.checkedRadioButtonId) {
                R.id.fragment_dashboard_obd_mode_button -> if (gpsIndicator.isActivated
                    && carIndicator.isActivated
                    && bluetoothIndicator.isActivated
                    && obdIndicator.isActivated
                ) {
                    val device = bluetoothHandler.selectedBluetoothDevice
                    val obdRecordingIntent = Intent(activity, RecordingService::class.java)
                    connectingDialog = DialogUtils.createProgressBarDialogBuilder(
                        context,
                        R.string.dashboard_connecting,
                        R.drawable.ic_bluetooth_white_24dp,
                        String.format(
                            getString(R.string.dashboard_connecting_find_template),
                            device.name
                        )
                    )
                        .setNegativeButton(R.string.cancel) { dialog, which ->
                            ServiceUtils.stopService(
                                activity,
                                obdRecordingIntent
                            )
                        }
                        .show()

                    // If the device is not found to start the track, dismiss the Dialog in 30 sec
                    deviceDiscoveryTimer = object : CountDownTimer(60000, 1000) {
                        override fun onTick(millisUntilFinished: Long) {}
                        override fun onFinish() {
                            LOG.warn("Device discovery timeout. Stop recording.")
                            if (connectingDialog != null) {
                                connectingDialog!!.dismiss()
                            }
                            ServiceUtils.stopService(activity, obdRecordingIntent)
                            Snackbar.make(
                                view!!, String.format(
                                    getString(R.string.dashboard_connecting_not_found_template),
                                    device.name
                                ), Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }.start()
                    ServiceUtils.startService(activity, obdRecordingIntent)
                }
                R.id.fragment_dashboard_gps_mode_button -> {
                    val gpsOnlyIntent = Intent(activity, RecordingService::class.java)
                    ServiceUtils.startService(activity, gpsOnlyIntent)
                }
                else -> {}
            }
        }
    }

    @OnClick(R.id.fragment_dashboard_indicator_car)
    fun onCarIndicatorClicked() {
        LOG.info("Car Indicator clicked")
        val intent = Intent(activity, CarSelectionActivity::class.java)
        requireActivity().startActivity(intent)
    }

    @OnClick(R.id.fragment_dashboard_indicator_obd)
    fun onObdIndicatorClicked() {
        LOG.info("OBD indicator clicked")
        val intent = Intent(activity, OBDSelectionActivity::class.java)
        requireActivity().startActivity(intent)
    }

    @OnClick(R.id.fragment_dashboard_indicator_bluetooth)
    fun onBluetoothIndicatorClicked() {
        LOG.info("Bluetooth indicator clicked")
        val intent = Intent(activity, OBDSelectionActivity::class.java)
        requireActivity().startActivity(intent)
    }

    @OnClick(R.id.fragment_dashboard_indicator_gps)
    fun onGPSIndicatorClicked() {
        LOG.info("GPS indicator clicked")
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        requireActivity().startActivity(intent)
    }

    @OnClick(R.id.user_statistics_card_view)
    fun onUserStatsClicked() {
        val bottomView: BottomNavigationView = requireActivity().findViewById(R.id.navigation)
        bottomView.selectedItemId = R.id.navigation_my_tracks
    }

    @Subscribe
    fun onReceiveRecordingStateChangedEvent(event: TrackRecordingServiceStateChangedEvent) {
        LOG.info("Recieved Recording State Changed event")
        Observable.just(event.mState)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { state: BluetoothServiceState ->
                if (state == BluetoothServiceState.SERVICE_STARTED) {
                    RecordingScreenActivity.navigate(context)
                }
                state
            }
            .subscribe(
                { state: BluetoothServiceState ->
                    this.updateStartTrackButton(
                        state
                    )
                }
            ) { e: Throwable? ->
                LOG.error(
                    e
                )
            }
    }

    @Subscribe
    fun onRecordingStateEvent(event: RecordingStateEvent) {
        LOG.info("Retrieve Recording State Event: $event")
        Observable.just(event.recordingState)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { state: RecordingState ->
                    updateByRecordingState(
                        state
                    )
                }
            ) { e: Throwable? ->
                LOG.error(
                    e
                )
            }
    }

    @Subscribe
    fun onEngineNotRunningEvent(event: EngineNotRunningEvent) {
        LOG.info("Retrieved Engine not running event")
        if (connectingDialog != null) {
            connectingDialog!!.dismiss()
            deviceDiscoveryTimer.cancel()
            connectingDialog = null
        }
        Observable.just(event)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe {
                MaterialDialog.Builder(
                    requireContext()
                )
                    .title(R.string.dashboard_engine_not_running_dialog_title)
                    .content(R.string.dashboard_engine_not_running_dialog_content)
                    .iconRes(R.drawable.ic_error_black_24dp)
                    .positiveText(R.string.ok)
                    .cancelable(true)
                    .show()
            }
    }

    /**
     * Receiver method for bluetooth activation events.
     *
     * @param event
     */
    @Subscribe
    fun receiveBluetoothStateChanged(event: BluetoothStateChangedEvent) {
        // post on decor view to ensure that it gets executed when view has been inflated.
        runAfterInflation {
            bluetoothIndicator.isActivated = event.isBluetoothEnabled
            updateOBDState(event.selectedDevice)
            this.updateStartTrackButton()
        }
    }

    /**
     * Receiver method for new Car selected events.
     */
    @Subscribe
    fun onReceiveNewCarTypeSelectedEvent(event: NewCarTypeSelectedEvent) {
        LOG.info("Received NewCarTypeSelected event. Updating views.")
        // post on decor view to ensure that it gets executed when view has been inflated.
        runAfterInflation {
            if (event.mCar != null) {
                carSelectionTextPrimary.text = String.format(
                    "%s %s",
                    event.mCar.manufacturer, event.mCar.model
                )
                carSelectionTextSecondary.text = String.format(
                    "%s, %s cm³, %s",
                    "" + event.mCar.constructionYear,
                    "" + event.mCar.engineDisplacement,
                    "" + getString(event.mCar.fuelType.stringResource)
                )

                // set indicator color accordingly
                carIndicator.isActivated = true
            } else {
                carSelectionTextPrimary.text = String.format(
                    "%s",
                    resources.getString(R.string.dashboard_carselection_no_car_selected)
                )
                carSelectionTextSecondary.text = String.format(
                    "%s",
                    resources.getString(R.string.dashboard_carselection_no_car_selected_advise)
                )

                // set warning indicator color to red
                carIndicator.isActivated = false
            }
            this.updateStartTrackButton()
        }
    }

    /**
     * Receiver method for bluetooth device selected events.
     *
     * @param event
     */
    @Subscribe
    fun onOBDAdapterSelectedEvent(event: BluetoothDeviceSelectedEvent) {
        // post on decor view to ensure that it gets executed when view has been inflated.
        runAfterInflation { updateOBDState(event.mDevice) }
    }

    /**
     * Receiver method for GPS activation events.
     *
     * @param event
     */
    @Subscribe
    fun onGpsStateChangedEvent(event: GpsStateChangedEvent) {
        // post on decor view to ensure that it gets executed when view has been inflated.
        runAfterInflation {
            gpsIndicator.isActivated = event.mIsGPSEnabled
            this.updateStartTrackButton()
        }
    }

    @Subscribe
    fun onNewUserSettingsEvent(event: NewUserSettingsEvent) {
        runAfterInflation {
            statisticsKnown = false
            updateUserLogin(event.mUser)
        }
    }

    @Subscribe
    fun onUserStatisticsUpdateEvent(event: UserStatisticsUpdateEvent) {
        runAfterInflation {
            statisticsKnown = true
            updateStatisticsVisibility(true)
            userTracksTextView.text = String.format("%s", event.numTracks)
            userDistanceTextView.text = String.format(
                "%s km",
                event.totalDistance.roundToInt()
            )
            userDurationTextView.text = formatTimeForDashboard(event.totalDuration)
        }
    }

    private fun updateUserLogin(user: User?) {
        if (user != null) {
            // show progress bar
            updateStatisticsVisibility(statisticsKnown)
            loggedInLayout.visibility = View.VISIBLE
            toolbar.menu.clear()
            toolbar.inflateMenu(R.menu.menu_dashboard_logged_in)
            textView.text = user.username

            // Welcome message as user logged in successfully
            if (!welcomeMessageShown) {
                Snackbar.make(
                    requireActivity().findViewById(R.id.snackbar_placeholder),
                    String.format(getString(R.string.welcome_message), user.username),
                    Snackbar.LENGTH_LONG
                ).show()
                welcomeMessageShown = true
            }
            val set = ConstraintSet()
            set.constrainPercentHeight(bannerLayout.id, 0.25f)
            set.connect(
                bannerLayout.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                0
            )
            set.connect(
                bannerLayout.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
                0
            )
            set.connect(bannerLayout.id, ConstraintSet.TOP, toolbar.id, ConstraintSet.BOTTOM, 0)
            set.applyTo(mainLayout)
        } else {
            // show progress bar
            updateStatisticsVisibility(statisticsKnown)
            loggedInLayout.visibility = View.GONE
            toolbar.menu.clear()
            toolbar.inflateMenu(R.menu.menu_dashboard_logged_out)
            val set = ConstraintSet()
            set.constrainPercentHeight(bannerLayout.id, 0.115f)
            set.connect(
                bannerLayout.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                0
            )
            set.connect(
                bannerLayout.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
                0
            )
            set.connect(bannerLayout.id, ConstraintSet.TOP, toolbar.id, ConstraintSet.BOTTOM, 0)
            set.applyTo(mainLayout)
        }
    }

    private fun updateStatisticsVisibility(statisticsKnown: Boolean) {
        // update progress bar visibility
        val progressBarVisibility = if (statisticsKnown) View.GONE else View.VISIBLE
        userStatProgressBar.visibility = progressBarVisibility

        // update statistics visibility
        val statisticsVisibility = if (statisticsKnown) View.VISIBLE else View.INVISIBLE
        userTracksLayout.visibility = statisticsVisibility
        userDistanceLayout.visibility = statisticsVisibility
        userDurationLayout.visibility = statisticsVisibility
    }

    private fun formatTimeForDashboard(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val formatString = if (hours > 99) "%03d:%02d h" else "%02d:%02d h"
        return String.format(
            formatString, hours, TimeUnit.MILLISECONDS.toMinutes(millis)
                    - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis))
        )
    }

    private fun updateOBDState(device: BluetoothDevice?) {
        if (device != null) {
            bluetoothSelectionTextPrimary.text = device.name
            bluetoothSelectionTextSecondary.text = device.address

            // set indicator color
            obdIndicator.isActivated = true
        } else {
            bluetoothSelectionTextPrimary.text =
                resources.getText(R.string.dashboard_obd_not_selected)
            bluetoothSelectionTextSecondary.text =
                resources.getText(R.string.dashboard_obd_not_selected_advise)
            obdIndicator.isActivated = false
        }
        this.updateStartTrackButton()
    }

    private fun updateStartTrackButton() {
        var setEnabled = false
        when (RecordingService.RECORDING_STATE) {
            RecordingState.RECORDING_RUNNING -> {
                startTrackButtonText.setText(R.string.dashboard_goto_track)
                startTrackButton.isEnabled = true
            }
            RecordingState.RECORDING_INIT -> {
                startTrackButtonText.setText(R.string.dashboard_track_is_starting)
                startTrackButton.isEnabled = true
            }
            RecordingState.RECORDING_STOPPED -> {
                when (modeSegmentedGroup.checkedRadioButtonId) {
                    R.id.fragment_dashboard_gps_mode_button -> setEnabled =
                        (carIndicator.isActivated
                                && gpsIndicator.isActivated)
                    R.id.fragment_dashboard_obd_mode_button -> setEnabled =
                        (bluetoothIndicator.isActivated
                                && gpsIndicator.isActivated
                                && obdIndicator.isActivated
                                && carIndicator.isActivated)
                }
                startTrackButtonText.setText(R.string.dashboard_start_track)
                startTrackButton.isEnabled = setEnabled
            }
        }
    }

    private fun updateByRecordingState(state: RecordingState) {
        when (state) {
            RecordingState.RECORDING_INIT -> {}
            RecordingState.RECORDING_RUNNING -> when (modeSegmentedGroup.checkedRadioButtonId) {
                R.id.fragment_dashboard_gps_mode_button -> RecordingScreenActivity.navigate(
                    context
                )
                R.id.fragment_dashboard_obd_mode_button -> if (connectingDialog != null) {
                    connectingDialog!!.dismiss()
                    deviceDiscoveryTimer.cancel()
                    connectingDialog = null
                    RecordingScreenActivity.navigate(context)
                }
            }
            RecordingState.RECORDING_STOPPED -> if (connectingDialog != null) {
                connectingDialog!!.dismiss()
                deviceDiscoveryTimer.cancel()
                connectingDialog = null
            }
        }
        updateStartTrackButton()
    }

    private fun updateStartTrackButton(state: BluetoothServiceState) {
        when (state) {
            BluetoothServiceState.SERVICE_STOPPED -> startTrackButton.isEnabled =
                true
            BluetoothServiceState.SERVICE_STARTED -> {}
            BluetoothServiceState.SERVICE_STARTING -> {}
            BluetoothServiceState.SERVICE_STOPPING -> {}
            else -> {}
        }
    }
//apprentatish
    private fun initTextSynchronization() {
        // text size synchonization grp for indicators
        indicatorSyncGroup = ArrayList()
        indicatorSyncGroup.add(bluetoothIndicatorText)
        indicatorSyncGroup.add(obdIndicatorText)
        indicatorSyncGroup.add(gpsIndicatorText)
        indicatorSyncGroup.add(carIndicatorText)
        val listener =
            OnTextSizeChangedListener { view, size ->
                for (textView in indicatorSyncGroup) {
                    if (textView != view && textView!!.text !== view.text) {
                        textView!!.setAutoSizeTextTypeUniformWithPresetSizes(
                            intArrayOf(size.toInt()),
                            TypedValue.COMPLEX_UNIT_PX
                        )
                    }
                }
            }
        for (textView in indicatorSyncGroup) {
            textView!!.setOnTextSizeChangedListener(listener)
        }
    }

    private fun appUpdateCheck() {
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(
                    AppUpdateType.IMMEDIATE
                )
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo, AppUpdateType.IMMEDIATE,
                        requireActivity(), 121
                    )
                } catch (e: SendIntentException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST_CODE,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 121 && resultCode != Activity.RESULT_OK) {
            appUpdateCheck()
        }
    }

    companion object {
        private val LOG = Logger.getLogger(
            DashboardFragment::class.java
        )
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1203
        private const val REQUEST_PERMISSION_CODE = 100

        private const val ARGUMENTS_KEY = "arguments"
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1
    }

    override val coroutineContext: CoroutineContext = Dispatchers.Main + Job()
}