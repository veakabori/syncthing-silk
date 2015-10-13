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
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.opensilk.common.core.mortar.DaggerService;

import java.util.Collections;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import rx.Subscription;
import syncthing.android.R;
import syncthing.android.ui.common.ExpandableCardViewWrapper;
import syncthing.api.model.DeviceConfig;

/**
 * Created by drew on 3/6/15.
 */
public class NotifCardRejDeviceView extends ExpandableCardViewWrapper<NotifCardRejDevice> {

    @InjectView(R.id.identicon) ImageView identicon;
    @InjectView(R.id.header) ViewGroup header;
    @InjectView(R.id.expand) ViewGroup expand;
    @InjectView(R.id.time) TextView time;
    @InjectView(R.id.message) TextView message;

    @Inject SessionPresenter mPresenter;

    Subscription identiconSubscription;

    public NotifCardRejDeviceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            SessionComponent cmp = DaggerService.getDaggerComponent(getContext());
            cmp.inject(this);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ButterKnife.inject(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unsubscribe();
    }

    @OnClick(R.id.btn_add)
    void addDevice() {
        DeviceConfig device = new DeviceConfig();
        device.deviceID = getCard().id;
        mPresenter.showSavingDialog();
        //TODO fix
        mPresenter.controller.editDevice(device, Collections.emptyMap(),
                t -> {
                    mPresenter.showError("Save failed", t.getMessage());
                },
                () -> {
                    mPresenter.dismissSavingDialog();
                    mPresenter.showSuccessMsg();
                    dismissDevice();
                }
        );
    }

    @OnClick(R.id.btn_ignore)
    void ignoreDevice() {
        mPresenter.showSavingDialog();
        //TODO fix
        mPresenter.controller.ignoreDevice(getCard().id,
                t -> {
                    mPresenter.showError("Ignore failed", t.getMessage());
                },
                () -> {
                    mPresenter.dismissSavingDialog();
                    mPresenter.showSuccessMsg();
                    dismissDevice();
                }
        );
    }

    @OnClick(R.id.btn_later)
    void dismissDevice() {
        mPresenter.controller.removeDeviceRejection(getCard().id);
    }

    @Override
    public void onBind(NotifCardRejDevice card) {
        identiconSubscription = mPresenter.identiconGenerator.generateAsync(card.id)
                .subscribe(identicon::setImageBitmap);
    }

    @Override
    public void reset() {
        super.reset();
        unsubscribe();
    }

    @Override
    public View getExpandView() {
        return expand;
    }

    void unsubscribe() {
        if (identiconSubscription != null) {
            identiconSubscription.unsubscribe();
        }
    }

}
