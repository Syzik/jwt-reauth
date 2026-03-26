/*
Copyright 2022 NCC Group
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    https://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.nccgroup.jwtreauth;

import burp.IBurpExtenderCallbacks;
import com.nccgroup.jwtreauth.ui.scope.ScopeController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProfileManager {
    private final CopyOnWriteArrayList<TokenProfile> profiles = new CopyOnWriteArrayList<>();
    private final IBurpExtenderCallbacks callbacks;
    private final ScopeController scopeController;

    private MainViewController mainViewController;

    public ProfileManager(IBurpExtenderCallbacks callbacks, ScopeController scopeController) {
        this.callbacks = callbacks;
        this.scopeController = scopeController;
    }

    public void setMainViewController(MainViewController mainViewController) {
        this.mainViewController = mainViewController;
    }

    public TokenProfile createProfile(String name) {
        var profile = new TokenProfile(name, callbacks, scopeController);
        profiles.add(profile);

        if (mainViewController != null) {
            mainViewController.addProfileTab(profile);
        }

        scopeController.updateProfileComboBox(getProfileNames());

        return profile;
    }

    public void removeProfile(TokenProfile profile) {
        if (profiles.size() <= 1) return;

        profile.shutdown();
        profiles.remove(profile);

        scopeController.removeProfileEntries(profile.getName());

        if (mainViewController != null) {
            mainViewController.removeProfileTab(profile);
        }

        scopeController.updateProfileComboBox(getProfileNames());
    }

    public void renameProfile(TokenProfile profile, String newName) {
        var oldName = profile.getName();
        profile.setName(newName);
        scopeController.renameProfileEntries(oldName, newName);

        if (mainViewController != null) {
            mainViewController.renameProfileTab(profile, newName);
        }

        scopeController.updateProfileComboBox(getProfileNames());
    }

    public List<TokenProfile> getProfiles() {
        return Collections.unmodifiableList(new ArrayList<>(profiles));
    }

    public TokenProfile getProfileById(String id) {
        return profiles.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public TokenProfile getProfileByName(String name) {
        return profiles.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public List<String> getProfileNames() {
        var names = new ArrayList<String>();
        for (var profile : profiles) {
            names.add(profile.getName());
        }
        return names;
    }

    public String getProfileIdByName(String name) {
        var profile = getProfileByName(name);
        return profile != null ? profile.getId() : "";
    }

    public String getProfileNameById(String id) {
        var profile = getProfileById(id);
        return profile != null ? profile.getName() : "";
    }

    public void shutdownAll() {
        for (var profile : profiles) {
            profile.shutdown();
        }
    }
}
