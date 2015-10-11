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

package syncthing.android.ui.sessionsettings;

import org.opensilk.common.core.dagger2.ScreenScope;

import dagger.Component;
import rx.functions.Func1;
import syncthing.android.ui.ManageActivityComponent;
import syncthing.android.ui.session.SessionComponent;
import syncthing.api.SessionManagerComponent;

/**
 * Created by drew on 3/17/15.
 */
@ScreenScope
@Component (
        dependencies = ManageActivityComponent.class
)
public interface SettingsComponent extends EditFragmentComponent {
    Func1<ManageActivityComponent, SettingsComponent> FACTORY =
            new Func1<ManageActivityComponent, SettingsComponent>() {
                @Override
                public SettingsComponent call(ManageActivityComponent sessionComponent) {
                    return DaggerSettingsComponent.builder()
                            .manageActivityComponent(sessionComponent)
                            .build();
                }
            };
    SettingsPresenter presenter();
}