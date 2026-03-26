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

import burp.ITab;
import com.nccgroup.jwtreauth.ui.ProfileViewPane;
import com.nccgroup.jwtreauth.ui.scope.ScopeController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

public class MainViewController implements ITab {
    private final JTabbedPane tabbedPane;
    private final ProfileManager profileManager;
    private final ScopeController scopeController;
    private final Map<String, Integer> profileTabIndices = new HashMap<>();

    private boolean suppressTabChange = false;

    public MainViewController(ProfileManager profileManager, ScopeController scopeController) {
        this.profileManager = profileManager;
        this.scopeController = scopeController;

        tabbedPane = new JTabbedPane();

        // add the Scope tab
        tabbedPane.addTab("Scope", scopeController.getScopePanel());

        // add the "+" tab for creating new profiles
        tabbedPane.addTab("+", new JPanel());

        // intercept clicks on the "+" tab
        tabbedPane.addChangeListener(e -> {
            if (suppressTabChange) return;

            int selectedIndex = tabbedPane.getSelectedIndex();
            int plusTabIndex = tabbedPane.getTabCount() - 1;

            if (selectedIndex == plusTabIndex) {
                // switch away from "+" tab before showing dialog
                suppressTabChange = true;
                if (tabbedPane.getTabCount() > 2) {
                    tabbedPane.setSelectedIndex(plusTabIndex - 1);
                }
                suppressTabChange = false;

                var defaultName = "Profile " + (profileManager.getProfiles().size() + 1);
                String name = JOptionPane.showInputDialog(
                        tabbedPane,
                        "Profile name:",
                        defaultName
                );

                if (name != null && !name.isBlank()) {
                    var profile = profileManager.createProfile(name.trim());
                    // select the newly created tab
                    int newTabIndex = tabbedPane.getTabCount() - 3; // before Scope and +
                    suppressTabChange = true;
                    tabbedPane.setSelectedIndex(newTabIndex);
                    suppressTabChange = false;
                }
            }
        });
    }

    public void addProfileTab(TokenProfile profile) {
        int insertIndex = tabbedPane.getTabCount() - 2; // before Scope and +

        var profilePane = new ProfileViewPane(profile);
        tabbedPane.insertTab(profile.getName(), null, profilePane, null, insertIndex);

        // add close button (only if more than one profile)
        tabbedPane.setTabComponentAt(insertIndex, createTabComponent(profile));

        profileTabIndices.put(profile.getId(), insertIndex);
        rebuildIndices();
    }

    public void removeProfileTab(TokenProfile profile) {
        Integer index = profileTabIndices.get(profile.getId());
        if (index != null) {
            tabbedPane.removeTabAt(index);
            profileTabIndices.remove(profile.getId());
            rebuildIndices();
        }
    }

    public void renameProfileTab(TokenProfile profile, String newName) {
        Integer index = profileTabIndices.get(profile.getId());
        if (index != null) {
            tabbedPane.setTitleAt(index, newName);
            tabbedPane.setTabComponentAt(index, createTabComponent(profile));
        }
    }

    private void rebuildIndices() {
        profileTabIndices.clear();
        for (var profile : profileManager.getProfiles()) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                var component = tabbedPane.getComponentAt(i);
                if (component instanceof ProfileViewPane) {
                    // match by tab title
                    if (tabbedPane.getTitleAt(i).equals(profile.getName())) {
                        profileTabIndices.put(profile.getId(), i);
                        break;
                    }
                }
            }
        }
    }

    private JPanel createTabComponent(TokenProfile profile) {
        var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);

        var label = new JLabel(profile.getName());
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Single click: select the tab
                int index = tabbedPane.indexOfTabComponent(panel);
                if (index >= 0) {
                    tabbedPane.setSelectedIndex(index);
                }
                // Double click: rename
                if (e.getClickCount() == 2) {
                    String newName = JOptionPane.showInputDialog(
                            tabbedPane,
                            "Rename profile:",
                            profile.getName()
                    );
                    if (newName != null && !newName.isBlank()) {
                        profileManager.renameProfile(profile, newName.trim());
                    }
                }
            }
        });
        panel.add(label);

        var closeButton = new JButton("x");
        closeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        closeButton.setMargin(new Insets(0, 2, 0, 2));
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusable(false);
        closeButton.addActionListener(e -> {
            if (profileManager.getProfiles().size() <= 1) {
                JOptionPane.showMessageDialog(
                        tabbedPane,
                        "Cannot remove the last profile.",
                        "Warning",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            int result = JOptionPane.showConfirmDialog(
                    tabbedPane,
                    String.format("Remove profile \"%s\"? Associated scope entries will also be removed.", profile.getName()),
                    "Remove Profile",
                    JOptionPane.YES_NO_OPTION
            );

            if (result == JOptionPane.YES_OPTION) {
                profileManager.removeProfile(profile);
            }
        });
        panel.add(closeButton);

        return panel;
    }

    @Override
    public String getTabCaption() {
        return "JWT reauth";
    }

    @Override
    public Component getUiComponent() {
        return tabbedPane;
    }
}
