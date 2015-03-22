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

package syncthing.android.ui.session.edit;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensilk.common.mortar.ActivityResultsController;
import org.opensilk.common.mortar.ActivityResultsListener;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import mortar.MortarScope;
import mortar.ViewPresenter;
import rx.Subscription;
import syncthing.android.R;
import syncthing.android.ui.common.ActivityRequestCodes;
import syncthing.api.SessionController;
import syncthing.api.model.DeviceConfig;

/**
 * Created by drew on 3/16/15.
 */
@EditScope
public class EditDevicePresenter extends ViewPresenter<EditDeviceScreenView> implements ActivityResultsListener {

    final String deviceId;
    final boolean isAdd;
    final SessionController controller;
    final EditFragmentPresenter editFragmentPresenter;
    final ActivityResultsController activityResultsController;

    DeviceConfig originalDevice;

    Subscription saveSubscription;
    Subscription deleteSubscription;

    @Inject
    public EditDevicePresenter(
            @Named("deviceid") String deviceId,
            @Named("isadd") boolean isAdd,
            SessionController controller,
            EditFragmentPresenter editFragmentPresenter,
            ActivityResultsController activityResultsController
    ) {
        this.deviceId = deviceId;
        this.isAdd = isAdd;
        this.controller = controller;
        this.editFragmentPresenter = editFragmentPresenter;
        this.activityResultsController = activityResultsController;
    }

    @Override
    protected void onEnterScope(MortarScope scope) {
        super.onEnterScope(scope);
        activityResultsController.register(scope, this);
    }

    @Override
    protected void onExitScope() {
        super.onExitScope();
        if (saveSubscription != null) {
            saveSubscription.unsubscribe();
        }
        if (deleteSubscription != null) {
            deleteSubscription.unsubscribe();
        }
    }

    @Override
    protected void onLoad(Bundle savedInstanceState) {
        super.onLoad(savedInstanceState);
        if (savedInstanceState == null) {
            if (isAdd) {
                originalDevice = DeviceConfig.withDefaults();
            } else {
                originalDevice = SerializationUtils.clone(controller.getDevice(deviceId));
            }
        } else {
            originalDevice = (DeviceConfig) savedInstanceState.getSerializable("device");
        }
        getView().initialize(isAdd, originalDevice, controller.getFolders(), savedInstanceState != null);
    }

    @Override
    protected void onSave(Bundle outState) {
        super.onSave(outState);
        outState.putSerializable("device", originalDevice);
    }

    boolean validateDeviceId(CharSequence text, boolean strict) {
        if (StringUtils.isEmpty(text)) {
            getView().notifyDeviceIdEmpty();
            return false;
        } else if (strict && !text.toString().matches("^[- \\w\\s]{50,64}$")) {
            getView().notifyDeviceIdInvalid();
            return false;
        }
        return true;
    }

    boolean validateAddresses(CharSequence text) {
        return true;//TODO
    }

    void dismissDialog() {
        editFragmentPresenter.dismissDialog();
    }

    void saveDevice(Map<String, Boolean> folders) {
        if (saveSubscription != null) {
            saveSubscription.unsubscribe();
        }
        saveSubscription = controller.editDevice(originalDevice, folders,
                (t) -> {
                    if (hasView()) {
                        getView().showError(t.getMessage());
                    }
                },
                () -> {
                    if (hasView()) {
                        getView().showConfigSaved();
                    }
                    dismissDialog();
                }
                );
    }

    void deleteDevice() {
        if (deleteSubscription != null) {
            deleteSubscription.unsubscribe();
        }
        deleteSubscription = controller.deleteDevice(originalDevice,
                t -> {
                    if (hasView()) {
                        getView().showError(t.getMessage());
                    }
                },
                () -> {
                    if (hasView()) {
                        getView().showConfigSaved();
                    }
                    dismissDialog();
                }
                );
    }

    void startQRScannerActivity() {
        Intent intentScan = new Intent("com.google.zxing.client.android.SCAN");
        intentScan.addCategory(Intent.CATEGORY_DEFAULT);
        intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        try {
            activityResultsController.startActivityForResult(intentScan, ActivityRequestCodes.SCAN_QR, null);
        } catch (ActivityNotFoundException e) {
            if (hasView()) {
                getView().showError(getView().getResources().getString(R.string.no_qr_scanner_installed));
            }
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ActivityRequestCodes.SCAN_QR) {
            if (resultCode == Activity.RESULT_OK && hasView()) {
                String id = data.getStringExtra("SCAN_RESULT");
                if (hasView()) {
                    getView().editDeviceId.setText(id);
                }
            }
            return true;
        }
        return false;
    }
}
