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

package syncthing.android.ui.welcome;

import android.support.v4.app.FragmentManager;

import org.opensilk.common.core.dagger2.ScreenScope;

import java.util.concurrent.Executor;

import dagger.Module;
import dagger.Provides;
import retrofit.Endpoint;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.converter.Converter;
import syncthing.api.OkClient;
import syncthing.api.SyncthingApi;

@Module
public class WelcomeModule {

    FragmentManager fragmentManager;

    public WelcomeModule(WelcomeScreen screen) {
        this.fragmentManager = screen.fragmentManager;
    }

    @Provides @ScreenScope
    public FragmentManager provideFragmentManager() {
        return fragmentManager;
    }
}
