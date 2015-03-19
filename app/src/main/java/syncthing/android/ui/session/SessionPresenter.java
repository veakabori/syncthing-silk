/*
 * Copyright (c) 2015 OpenSilk Productions LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package syncthing.android.ui.session;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;

import org.opensilk.common.dagger2.ForApplication;
import org.opensilk.common.mortar.ActivityResultsController;
import org.opensilk.common.mortarfragment.FragmentManagerOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import mortar.MortarScope;
import mortar.ViewPresenter;
import rx.Subscription;
import syncthing.android.identicon.IdenticonGenerator;
import syncthing.android.model.Credentials;
import syncthing.android.ui.common.ActivityRequestCodes;
import syncthing.android.ui.common.Card;
import syncthing.android.ui.login.LoginActivity;
import syncthing.android.ui.session.edit.EditFragment;
import syncthing.api.SessionController;
import syncthing.api.SessionScope;
import syncthing.api.model.ConnectionInfo;
import syncthing.api.model.DeviceConfig;
import syncthing.api.model.DeviceStats;
import syncthing.api.model.Event;
import syncthing.api.model.FolderConfig;
import syncthing.api.model.GuiError;
import syncthing.api.model.Model;
import timber.log.Timber;

/**
* Created by drew on 3/11/15.
*/
@SessionScope
public class SessionPresenter extends ViewPresenter<SessionScreenView> {

    final Context appContext;
    final Credentials credentials;
    final SessionController controller;
    final FragmentManagerOwner fragmentManagerOwner;
    final IdenticonGenerator identiconGenerator;
    final ActivityResultsController activityResultsController;
    final SessionFragmentPresenter fragmentPresenter;

    Subscription changeSubscription;

    @Inject
    public SessionPresenter(
            @ForApplication Context appContext,
            Credentials credentials,
            SessionController controller,
            FragmentManagerOwner fragmentManagerOwner,
            IdenticonGenerator identiconGenerator,
            ActivityResultsController activityResultsController,
            SessionFragmentPresenter fragmentPresenter
    ) {
        this.appContext = appContext;
        this.credentials = credentials;
        this.controller = controller;
        this.fragmentManagerOwner = fragmentManagerOwner;
        this.identiconGenerator = identiconGenerator;
        this.activityResultsController = activityResultsController;
        this.fragmentPresenter = fragmentPresenter;
    }

    @Override
    protected void onEnterScope(MortarScope scope) {
        Timber.d("onEnterScope");
        super.onEnterScope(scope);
        changeSubscription = controller.subscribeChanges(this::onChange);
    }

    @Override
    protected void onExitScope() {
        Timber.d("onExitScope");
        super.onExitScope();
        if (changeSubscription != null) {
            changeSubscription.unsubscribe();
        }
        controller.kill();
    }

    @Override
    protected void onLoad(Bundle savedInstanceState) {
        super.onLoad(savedInstanceState);
        if (controller.isOnline()) {
            initializeView();
            getView().setListShown(true, false);
            dismissRestartingDialog();
        } else if (controller.isRestarting()) {
            showRestartingDialog();
        } else {
            getView().setLoading(true);
        }
    }

    @Override
    protected void onSave(Bundle outState) {
        super.onSave(outState);
    }

    void onChange(SessionController.Change change) {
        Timber.d("onChange(%s)", change.toString());
        switch (change) {
            case ONLINE:
                if (hasView()) {
                    initializeView();
                    getView().setListShown(true, true);
                    dismissRestartingDialog();
                }
                break;
            case OFFLINE:
                if (controller.isRestarting()) {
                    showRestartingDialog();
                } //else TODO
                break;
            case DEVICE_REJECTED:
            case FOLDER_REJECTED:
            case NOTICE:
                if (hasView()) {
                    getView().refreshNotifications(getNotifications());
                }
                break;
            case CONFIG_UPDATE:
                if (hasView()) {
                    if (!controller.isConfigInSync()) {
                        getView().refreshNotifications(getNotifications());
                    }
                    getView().refreshFolders(getFolders());
                    getView().refreshDevices(getDevices());
                }
                break;
            case NEED_LOGIN:
                activityResultsController.startActivityForResult(
                        new Intent(appContext, LoginActivity.class)
                                .putExtra(LoginActivity.EXTRA_CREDENTIALS, (Parcelable) credentials),
                        ActivityRequestCodes.LOGIN_ACTIVITY,
                        null
                );
                break;
            case COMPLETION:
            case CONNECTIONS:
            case DEVICE_STATS:
                if (hasView()) {
                    getView().refreshThisDevice(getThisDevice());
                    getView().refreshDevices(getDevices());
                }
                break;
            case SYSTEM:
                if (hasView()) {
                    getView().refreshThisDevice(getThisDevice());
                }
                break;
            case MODEL:
            case FOLDER_STATS:
                if (hasView()) {
                    getView().refreshFolders(getFolders());
                }
            default:
                break;
        }
    }

    void initializeView() {
        if (!hasView()) throw new IllegalStateException("initialize called without view");
        getView().initialize(
                getNotifications(),
                getFolders(),
                getThisDevice(),
                getDevices()
        );
    }

    List<Card> getNotifications() {
        List<Card> notifs = new ArrayList<>();
        if (!controller.isConfigInSync()) {
            notifs.add(NotifCardRestart.INSTANCE);
        }
        GuiError guiError = controller.getLatestError();
        if (guiError != null) {
            notifs.add(new NotifCardError(guiError));
        }
        for (Map.Entry<String, Event> e : controller.getDeviceRejections()) {
            notifs.add(new NotifCardDeviceRej(e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, Event> e : controller.getFolderRejections()) {
            notifs.add(new NotifCardFolderRej(e.getKey(), e.getValue()));
        }
        return notifs;
    }

    List<FolderCard> getFolders() {
        List<FolderCard> folderCards = new ArrayList<>();
        List<String> needsUpdate = new ArrayList<>();
        for (FolderConfig folder : controller.getFolders()) {
            Model model = controller.getModel(folder.id);
            if (model == null) {
                needsUpdate.add(folder.id);
            }
            folderCards.add(new FolderCard(folder, model));
        }
        if (!needsUpdate.isEmpty()) {
            controller.refreshFolders(needsUpdate);
        }
        return folderCards;
    }

    MyDeviceCard getThisDevice() {
        return new MyDeviceCard(
                controller.getThisDevice(),
                controller.getConnection("total"),
                controller.getSystemInfo(),
                controller.getVersion()
        );
    }

    List<DeviceCard> getDevices() {
        List<DeviceCard> deviceCards = new ArrayList<>();
        for (DeviceConfig device : controller.getRemoteDevices()) {
            ConnectionInfo connection = controller.getConnection(device.deviceID);
            DeviceStats stats = controller.getDeviceStats(device.deviceID);
            int completion = controller.getCompletionTotal(device.deviceID);
            deviceCards.add(new DeviceCard(device, connection, stats, completion));
        }
        return deviceCards;
    }

    void showSavingDialog() {
        if (hasView()) getView().showSavingDialog();
    }

    void dismissSavingDialog() {
        if (hasView()) getView().dismissSavingDialog();
    }

    void showRestartingDialog() {
        if (hasView()) getView().showRestartDialog();
    }

    void dismissRestartingDialog() {
        if (hasView()) getView().dismissRestartDialog();
    }

    void showError(String title, String msg) {
        if (hasView()) getView().showErrorDialog(title, msg);
    }

    void dismissError() {
        if (hasView()) getView().dismissErrorDialog();
    }

    void showSuccessMsg() {
        if (hasView()) getView().showConfigSaved();
    }

    public String getMyDeviceId() {
        return controller.getMyID();
    }

    void openAddDeviceScreen() {
        doOpenScreen(EditFragment.newDeviceInstance(), "deviceadd");
    }

    void openEditDeviceScreen(String deviceId) {
        doOpenScreen(EditFragment.newDeviceInstance(deviceId), "deviceedit" + deviceId);
    }

    void openAddFolderScreen() {
        doOpenScreen(EditFragment.newFolderInstance(), "folderedit");
    }

    void openEditFolderScreen(String folderId) {
        doOpenScreen(EditFragment.newFolderInstance(folderId), "folderedit" + folderId);
    }

    void openEditFolderScreen(String folderId, String deviceId) {
        doOpenScreen(EditFragment.newFolderInstance(folderId, deviceId), "folderedit"+folderId);
    }

    void openSettingsScreen() {
        doOpenScreen(EditFragment.newSettingsInstance(), "settings");
    }

    void doOpenScreen(DialogFragment fragment, String tag) {
        FragmentTransaction ft = fragmentPresenter.newTransaction();
        //fragmentPresenter.decorateTrasaction(ft, fragment);
        fragment.show(ft, tag);
    }


}