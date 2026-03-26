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

import burp.IContextMenuFactory;
import burp.IContextMenuInvocation;
import burp.IExtensionHelpers;
import com.nccgroup.jwtreauth.ui.scope.ScopeController;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class JWTReauthContextMenuFactory implements IContextMenuFactory {
    private final IExtensionHelpers helpers;
    private final ProfileManager profileManager;
    private final ScopeController scopeController;

    public JWTReauthContextMenuFactory(IExtensionHelpers helpers, ProfileManager profileManager, ScopeController scopeController) {
        this.helpers = helpers;
        this.profileManager = profileManager;
        this.scopeController = scopeController;
    }

    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        switch (invocation.getInvocationContext()) {
            case IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST:
            case IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST:
            case IContextMenuInvocation.CONTEXT_PROXY_HISTORY:
            case IContextMenuInvocation.CONTEXT_TARGET_SITE_MAP_TREE:
            case IContextMenuInvocation.CONTEXT_TARGET_SITE_MAP_TABLE:
            case IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_RESPONSE:
            case IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_RESPONSE:
                break;

            default:
                return null;
        }

        var profiles = profileManager.getProfiles();
        final List<JMenuItem> menuItems = new ArrayList<>();

        if (profiles.size() == 1) {
            // single profile: flat menu items (no sub-menus)
            var profile = profiles.get(0);

            var authItem = new JMenuItem("Send to JWT re-auth (set auth request)");
            authItem.addActionListener(e -> {
                var messages = invocation.getSelectedMessages();
                if (messages.length == 1) {
                    profile.setAuthorizeRequest(messages[0]);
                }
            });
            menuItems.add(authItem);

            var tokenItem = new JMenuItem("Send to JWT re-auth (set auth token)");
            tokenItem.addActionListener(e -> {
                var messages = invocation.getSelectedMessages();
                if (messages.length == 1) {
                    profile.processAuthResponse(messages[0], true);
                }
            });
            menuItems.add(tokenItem);

            var refreshItem = new JMenuItem("Send to JWT re-auth (set refresh request)");
            refreshItem.addActionListener(e -> {
                var messages = invocation.getSelectedMessages();
                if (messages.length == 1) {
                    profile.setRefreshRequest(messages[0]);
                }
            });
            menuItems.add(refreshItem);

            var scopeItem = new JMenuItem("Send to JWT re-auth (add to scope)");
            scopeItem.addActionListener(e -> {
                var messages = invocation.getSelectedMessages();
                for (var request : messages) {
                    var url = helpers.analyzeRequest(request).getUrl();
                    if (!scopeController.contains(url)) {
                        scopeController.addToScope(url, profile.getName());
                    }
                }
            });
            menuItems.add(scopeItem);
        } else {
            // multiple profiles: sub-menus
            var authMenu = new JMenu("JWT re-auth: set auth request");
            var tokenMenu = new JMenu("JWT re-auth: set auth token");
            var refreshMenu = new JMenu("JWT re-auth: set refresh request");
            var scopeMenu = new JMenu("JWT re-auth: add to scope");

            for (var profile : profiles) {
                var authItem = new JMenuItem(profile.getName());
                authItem.addActionListener(e -> {
                    var messages = invocation.getSelectedMessages();
                    if (messages.length == 1) {
                        profile.setAuthorizeRequest(messages[0]);
                    }
                });
                authMenu.add(authItem);

                var tokenItem = new JMenuItem(profile.getName());
                tokenItem.addActionListener(e -> {
                    var messages = invocation.getSelectedMessages();
                    if (messages.length == 1) {
                        profile.processAuthResponse(messages[0], true);
                    }
                });
                tokenMenu.add(tokenItem);

                var refreshItem = new JMenuItem(profile.getName());
                refreshItem.addActionListener(e -> {
                    var messages = invocation.getSelectedMessages();
                    if (messages.length == 1) {
                        profile.setRefreshRequest(messages[0]);
                    }
                });
                refreshMenu.add(refreshItem);

                var scopeItem = new JMenuItem(profile.getName());
                scopeItem.addActionListener(e -> {
                    var messages = invocation.getSelectedMessages();
                    for (var request : messages) {
                        var url = helpers.analyzeRequest(request).getUrl();
                        if (!scopeController.contains(url)) {
                            scopeController.addToScope(url, profile.getName());
                        }
                    }
                });
                scopeMenu.add(scopeItem);
            }

            menuItems.add(authMenu);
            menuItems.add(tokenMenu);
            menuItems.add(refreshMenu);
            menuItems.add(scopeMenu);
        }

        return menuItems;
    }
}
