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

package syncthing.android.ui.settings;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import org.opensilk.common.core.mortar.DaggerService;

import java.util.List;
import java.util.UUID;

import mortar.MortarScope;
import syncthing.android.AppComponent;
import syncthing.android.R;
import syncthing.android.service.SyncthingUtils;

/**
 * Created by drew on 3/21/15.
 */
public class SettingsActivity extends PreferenceActivity {

    private MortarScope mActivityScope;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppComponent app= DaggerService.getDaggerComponent(getApplicationContext());
        mActivityScope = MortarScope.buildChild(getApplicationContext())
                .withService(DaggerService.DAGGER_SERVICE,
                        DaggerSettingsActivityComponent.builder().appComponent(app).build())
                .build(UUID.randomUUID().toString());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mActivityScope != null) {
            mActivityScope.destroy();
            mActivityScope = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SyncthingUtils.notifyForegroundStateChanged(this, true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        SyncthingUtils.notifyForegroundStateChanged(this, false);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.prefs_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Object getSystemService(String name) {
        //Note we dont create the scope here since this is usually called
        //before onCreate and we need to be able to fetch our scope name
        //on configuration changes
        return (mActivityScope != null && mActivityScope.hasService(name))
                ? mActivityScope.getService(name) : super.getSystemService(name);
    }

}
