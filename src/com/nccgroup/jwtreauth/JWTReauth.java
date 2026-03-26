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

import burp.IBurpExtender;
import burp.IBurpExtenderCallbacks;
import com.nccgroup.jwtreauth.ui.scope.ScopeController;

import javax.swing.*;

public class JWTReauth implements IBurpExtender {
    private static final String VERSION = "1.0.1";

    private IBurpExtenderCallbacks callbacks;
    private ScopeController scopeController;
    private ProfileManager profileManager;
    private TokenListener tokenListener;
    private MainViewController mainViewController;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;

        scopeController = new ScopeController();
        profileManager = new ProfileManager(callbacks, scopeController);
        tokenListener = new TokenListener(callbacks, profileManager);
        mainViewController = new MainViewController(profileManager, scopeController);
        profileManager.setMainViewController(mainViewController);

        // create a default profile
        profileManager.createProfile("Profile 1");

        callbacks.setExtensionName("JWT re-auth");
        callbacks.registerHttpListener(tokenListener);
        callbacks.registerContextMenuFactory(
                new JWTReauthContextMenuFactory(callbacks.getHelpers(), profileManager, scopeController)
        );
        callbacks.registerExtensionStateListener(tokenListener);

        SwingUtilities.invokeLater(() -> {
            callbacks.addSuiteTab(mainViewController);
            callbacks.printOutput("Loaded JWT re-auth. v" + VERSION);
        });
    }

    public IBurpExtenderCallbacks getCallbacks() {
        return callbacks;
    }

    public ScopeController getScopeController() {
        return scopeController;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }
}
