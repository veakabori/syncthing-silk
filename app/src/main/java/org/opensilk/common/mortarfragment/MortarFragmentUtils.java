/*
 * Copyright (C) 2015 OpenSilk Productions LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opensilk.common.mortarfragment;

import mortar.MortarScope;
import mortar.dagger2support.DaggerService;

import static java.lang.String.format;

/**
 * Created by drew on 3/10/15.
 */
public class MortarFragmentUtils {

    /**
     * Caller is required to know the type of the component for this scope.
     *
     * @throws IllegalArgumentException if there is no DaggerService attached to this scope
     * @return The Component associated with this scope
     */
    @SuppressWarnings("unchecked") //
    public static <T> T getDaggerComponent(MortarScope scope) {
        if (scope.hasService(DaggerService.SERVICE_NAME)) {
            return (T) scope.getService(DaggerService.SERVICE_NAME);
        }
        throw new IllegalArgumentException(format("No dagger service found in scope " + scope.getName()));
    }

}