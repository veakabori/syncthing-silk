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

import android.content.res.Resources;

import org.opensilk.common.mortar.ComponentFactory;
import org.opensilk.common.mortarfragment.MortarFragmentUtils;

import mortar.MortarScope;
import mortar.dagger2support.DaggerService;
import syncthing.android.ui.LauncherActivityComponent;
import syncthing.api.SyncthingApiModule;

/**
* Created by drew on 3/11/15.
*/
public class SesssionComponentFactory extends ComponentFactory<SessionScreen> {
    @Override
    protected Object createDaggerComponent(Resources resources, MortarScope parentScope, SessionScreen screen) {
        return DaggerService.createComponent(SessionComponent.class,
                MortarFragmentUtils.<LauncherActivityComponent>getDaggerComponent(parentScope),
                new SessionModule(screen.credentials),
                new SyncthingApiModule()
        );
    }
}