/**
 * Copyright (C) 2013 - 2019 the enviroCar community
 * <p>
 * This file is part of the enviroCar app.
 * <p>
 * The enviroCar app is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * The enviroCar app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along
 * with the enviroCar app. If not, see http://www.gnu.org/licenses/.
 */
package org.envirocar.app.views;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.mapbox.mapboxsdk.Mapbox;
import com.squareup.otto.Subscribe;

import org.envirocar.app.BaseApplicationComponent;
import org.envirocar.app.R;
import org.envirocar.app.handler.ApplicationSettings;
import org.envirocar.app.handler.BluetoothHandler;
import org.envirocar.app.handler.DAOProvider;
import org.envirocar.app.handler.TemporaryFileManager;
import org.envirocar.app.handler.agreement.AgreementManager;
import org.envirocar.app.handler.preferences.CarPreferenceHandler;
import org.envirocar.app.handler.preferences.UserPreferenceHandler;
import org.envirocar.app.injection.BaseInjectorActivity;
import org.envirocar.app.injection.modules.MainActivityModule;
import org.envirocar.app.services.autoconnect.AutoRecordingService;
import org.envirocar.app.views.dashboard.DashboardFragment;
import org.envirocar.app.views.others.OthersFragment;
import org.envirocar.app.views.others.TroubleshootingFragment;
import org.envirocar.app.views.tracklist.TrackListPagerFragment;
import org.envirocar.core.events.TrackFinishedEvent;
import org.envirocar.core.exception.NoMeasurementsException;
import org.envirocar.core.logging.Logger;
import org.envirocar.core.utils.ServiceUtils;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * @authro dewall
 */
public class BaseMainActivity extends BaseInjectorActivity {
    private static final Logger LOGGER = Logger.getLogger(BaseMainActivity.class);

    private static final String TROUBLESHOOTING_TAG = "TROUBLESHOOTING";

    // Injected variables
    @Inject
    protected UserPreferenceHandler mUserManager;
    @Inject
    protected CarPreferenceHandler mCarManager;
    @Inject
    protected TemporaryFileManager mTemporaryFileManager;
    @Inject
    protected DAOProvider mDAOProvider;
    @Inject
    protected BluetoothHandler mBluetoothHandler;

    // activity scoped
    @Inject
    protected DashboardFragment dashboardFragment;
    @Inject
    protected TrackListPagerFragment trackListPagerFragment;
    @Inject
    protected OthersFragment othersFragment;
    @Inject
    protected Mapbox mapbox;
    @Inject
    protected AgreementManager agreementManager;

    @BindView(R.id.navigation)
    protected BottomNavigationView navigationBottomBar;

    private int selectedMenuItemID = 0;

    private CompositeDisposable subscriptions = new CompositeDisposable();
    private BroadcastReceiver errorInformationReceiver;
    private boolean paused;

    private Scheduler.Worker mMainThreadWorker = AndroidSchedulers.mainThread().createWorker();


    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = item -> {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        switch (item.getItemId()) {
            case R.id.navigation_dashboard:
                if (selectedMenuItemID != 1) {
                    fragmentTransaction.replace(R.id.fragmentContainer, dashboardFragment);
                    fragmentTransaction.commit();
                    selectedMenuItemID = 1;
                }
                return true;
            case R.id.navigation_my_tracks:
                if (selectedMenuItemID != 2) {
                    fragmentTransaction.replace(R.id.fragmentContainer, trackListPagerFragment);
                    fragmentTransaction.commit();
                    selectedMenuItemID = 2;
                }
                return true;
            case R.id.navigation_others:
                if (selectedMenuItemID != 3) {
                    fragmentTransaction.replace(R.id.fragmentContainer, othersFragment);
                    fragmentTransaction.commit();
                    selectedMenuItemID = 3;
                }
                return true;
        }
        return false;
    };

    @Override
    protected void injectDependencies(BaseApplicationComponent baseApplicationComponent) {
        baseApplicationComponent
                .plus(new MainActivityModule(this))
                .inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        LOGGER.info("BaseMainActivity : onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base_main_bottom_bar);
        ButterKnife.bind(this);

        navigationBottomBar.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        navigationBottomBar.setSelectedItemId(R.id.navigation_dashboard);

        // Subscribe for preference subscriptions. In this case, subscribe for changes to the
        // active screen settings.
        // TODO
        addPreferenceSubscriptions();

        errorInformationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (paused) {
                    return;
                }

                Fragment fragment = getSupportFragmentManager().findFragmentByTag
                        (TROUBLESHOOTING_TAG);
                if (fragment == null) {
                    fragment = new TroubleshootingFragment();
                }
                fragment.setArguments(intent.getExtras());
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .commit();
            }
        };

        // Check whether newest TermsOfUse have been accepted.
//        Observable.just(true)
//                .subscribeOn(Schedulers.io())
//                .observeOn(Schedulers.io())


        registerReceiver(errorInformationReceiver, new IntentFilter(TroubleshootingFragment.INTENT));
    }

    @Override
    protected void onPause() {
        LOGGER.info("BaseMainActivity : onPause");
        super.onPause();
        this.paused = false;

        //first init
        firstInit();

//        checkKeepScreenOn();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        LOGGER.info("BaseMainActivity : onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        LOGGER.info("BaseMainActivity : onResume()");
        super.onResume();
        // Check whether the screen is required to keep the screen on.
        checkKeepScreenOn();

        Observable.just(true)
                .filter(bool -> mUserManager.isLoggedIn())
                .compose(new AgreementManager.TermsOfUseValidator<>(agreementManager, this))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .doOnNext(bool -> {
                    LOGGER.info("SUCCESSFUL");
                })
                .doOnError(LOGGER::error)
                .subscribe();
    }

    @Override
    protected void onDestroy() {
        LOGGER.info("BaseMainActivity : onDestroy()");
        super.onDestroy();

        this.unregisterReceiver(errorInformationReceiver);
        mTemporaryFileManager.shutdown();

        if (!subscriptions.isDisposed()) {
            subscriptions.dispose();
        }
    }

    private void firstInit() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!preferences.contains("first_init")) {

            SharedPreferences.Editor e = preferences.edit();
            e.putString("first_init", "seen");
            e.putBoolean("pref_privacy", true);
            e.commit();
        }
    }

    private void addPreferenceSubscriptions() {
        // Keep screen active setting;
        subscriptions.add(
                ApplicationSettings.getDisplayStaysActiveObservable(this)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            checkKeepScreenOn();
                        }));

        // Start Background handler
        subscriptions.add(
                ApplicationSettings.getAutoconnectEnabledObservable(this)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                if (!ServiceUtils.isServiceRunning(this, AutoRecordingService.class))
                                    AutoRecordingService.startService(this);
                            } else {
                                if (ServiceUtils.isServiceRunning(this, AutoRecordingService.class))
                                    AutoRecordingService.stopService(this);
                            }
                        }, LOGGER::error));
    }

    private void checkKeepScreenOn() {
        if (ApplicationSettings.getDisplayStaysActiveObservable(this).blockingFirst()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            this.navigationBottomBar.setKeepScreenOn(true);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            this.navigationBottomBar.setKeepScreenOn(false);
        }
    }

    @Subscribe
    public void onReceiveTrackFinishedEvent(final TrackFinishedEvent event) {
        LOGGER.info(String.format("onReceiveTrackFinishedEvent(): event=%s", event.toString()));

        // Just show a message depending on the event-related track.
        mMainThreadWorker.schedule(() -> {
            if (event.mTrack == null) {
                // Track is null and thus there was an error.
                showSnackbar(R.string.track_finishing_failed);
            } else try {
                if (event.mTrack.getLastMeasurement() != null) {
                    LOGGER.info("last is not null.. " + event.mTrack.getLastMeasurement()
                            .toString());

                    // Track has no measurements
                    showSnackbar(getString(R.string.track_finished).concat(event.mTrack.getName()));
                }
            } catch (NoMeasurementsException e) {
                LOGGER.warn("Track has been finished without measurements", e);
                // Track has no measurements
                showSnackbar(R.string.track_finished_no_measurements);
            }
        });
    }

    private void showSnackbar(int infoRes) {
        showSnackbar(getString(infoRes));
    }

    private void showSnackbar(String info) {
        Snackbar.make(navigationBottomBar, info, Snackbar.LENGTH_LONG).show();
    }
}
