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

package com.nccgroup.jwtreauth.ui.settings;

import com.nccgroup.jwtreauth.TokenProfile;
import com.nccgroup.jwtreauth.ui.base.GridColumnPanel;
import com.nccgroup.jwtreauth.ui.logging.LogController;
import com.nccgroup.jwtreauth.ui.logging.LogLevel;
import com.nccgroup.jwtreauth.ui.logging.LogTableModel;
import com.nccgroup.jwtreauth.ui.misc.OnOffButton;
import com.nccgroup.jwtreauth.ui.misc.StatusLabel;
import com.nccgroup.jwtreauth.ui.state.TokenListenerStatePanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SettingsViewPanel extends JPanel {
    private final LogController logController;
    private final TokenProfile profile;
    private final TokenListenerStatePanel tokenListenerStatePanel;

    private final GridColumnPanel settingsGrid;

    final HashMap<String, RowUpdateHandler> updateHandlers;

    public SettingsViewPanel(TokenProfile profile) {
        this.profile = profile;
        logController = profile.getLogController();
        tokenListenerStatePanel = profile.getTokenListenerStatePanel();

        updateHandlers = new HashMap<>();

        settingsGrid = new GridColumnPanel("Settings", 3, false, 0);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        addAuthRequestPanel();
        addSettingsAndRefreshRow();
        addGridComponents();
    }

    private void addAuthRequestPanel() {
        var panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 5, 0, 5),
                BorderFactory.createTitledBorder(
                        null,
                        "Authorization request",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Font.getFont(Font.MONOSPACED)
                )
        ));

        var authRequestArea = new JTextArea("<not set — use the context menu to set>");
        authRequestArea.setFont(Font.decode("MONOSPACED"));
        authRequestArea.setEditable(false);
        authRequestArea.setLineWrap(true);
        authRequestArea.setRows(6);
        authRequestArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        var scrollPane = new JScrollPane(authRequestArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scrollPane, BorderLayout.CENTER);

        var clearAuthButton = new JButton("Clear");
        clearAuthButton.addActionListener(e -> {
            profile.clearAuthorizeRequest();
            authRequestArea.setText("<not set — use the context menu to set>");
        });
        panel.add(clearAuthButton, BorderLayout.EAST);

        updateHandlers.put("authRequest", newData -> {
            if (newData instanceof String) {
                SwingUtilities.invokeLater(() -> {
                    authRequestArea.setText((String) newData);
                    authRequestArea.setCaretPosition(0);
                });
            }
        });

        add(panel);
    }

    /**
     * Creates a horizontal row with the Settings grid on the left
     * and the optional Refresh Token panel on the right.
     */
    private void addSettingsAndRefreshRow() {
        var row = new JPanel(new GridLayout(1, 2, 0, 0));

        row.add(settingsGrid);
        row.add(buildRefreshPanel());

        add(row);
    }

    private JPanel buildRefreshPanel() {
        var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                BorderFactory.createTitledBorder(
                        null,
                        "Refresh Token (optional)",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Font.getFont(Font.MONOSPACED)
                )
        ));

        // Container for all refresh content (will be enabled/disabled)
        var contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Refresh request text area
        var refreshRequestArea = new JTextArea("<not set — use the context menu to set>");
        refreshRequestArea.setFont(Font.decode("MONOSPACED"));
        refreshRequestArea.setEditable(false);
        refreshRequestArea.setLineWrap(true);
        refreshRequestArea.setRows(5);
        refreshRequestArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        var scrollPane = new JScrollPane(refreshRequestArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        var requestLabel = new JLabel("Refresh request:");
        requestLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 2, 5));
        requestLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(requestLabel);

        var refreshRequestPanel = new JPanel(new BorderLayout(5, 0));
        refreshRequestPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshRequestPanel.add(scrollPane, BorderLayout.CENTER);

        var clearRefreshButton = new JButton("Clear");
        clearRefreshButton.addActionListener(e -> {
            profile.clearRefreshRequest();
            refreshRequestArea.setText("<not set — use the context menu to set>");
        });
        refreshRequestPanel.add(clearRefreshButton, BorderLayout.EAST);
        contentPanel.add(refreshRequestPanel);

        updateHandlers.put("refreshRequest", newData -> {
            if (newData instanceof String) {
                SwingUtilities.invokeLater(() -> {
                    refreshRequestArea.setText((String) newData);
                    refreshRequestArea.setCaretPosition(0);
                });
            }
        });

        // Refresh token regex field
        var regexPanel = new JPanel(new BorderLayout(5, 0));
        regexPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        regexPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        regexPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        var regexLabel = new JLabel("Refresh token regex: ");
        var regexField = new JTextField(TokenProfile.DEFAULT_TOKEN_REGEX);
        var regexStatus = new StatusLabel();
        regexField.addKeyListener(new com.nccgroup.jwtreauth.ui.misc.KeyReleasedListener(e -> {
            var regex = regexField.getText();
            try {
                var pattern = Pattern.compile(regex);
                regexStatus.setStatus(StatusLabel.Status.OK);
                profile.setRefreshTokenPattern(pattern);
            } catch (PatternSyntaxException ex) {
                regexStatus.setStatus(StatusLabel.Status.ERROR);
            }
        }));

        regexPanel.add(regexLabel, BorderLayout.WEST);
        regexPanel.add(regexField, BorderLayout.CENTER);
        regexPanel.add(regexStatus, BorderLayout.EAST);
        contentPanel.add(regexPanel);

        // Enable checkbox at top
        var enableCheckbox = new JCheckBox("Enable refresh token");
        enableCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        enableCheckbox.setSelected(false);
        enableCheckbox.addActionListener(e -> {
            var enabled = enableCheckbox.isSelected();
            setRefreshContentEnabled(contentPanel, enabled);
            profile.setRefreshEnabled(enabled);
        });

        panel.add(enableCheckbox);
        panel.add(contentPanel);

        // Start disabled
        setRefreshContentEnabled(contentPanel, false);

        return panel;
    }

    private void setRefreshContentEnabled(Container container, boolean enabled) {
        for (var comp : container.getComponents()) {
            comp.setEnabled(enabled);
            if (comp instanceof Container) {
                setRefreshContentEnabled((Container) comp, enabled);
            }
        }
    }

    private void addGridComponents() {
        // Create the auth request delay row
        var delayLabel = new JLabel("Authorization Request Delay (seconds): ");
        var delaySpinnerModel = new SpinnerNumberModel(TokenProfile.DEFAULT_AUTH_REQ_DELAY, 5, null, 5);
        var delaySpinner = new JSpinner(delaySpinnerModel);
        delaySpinner.addChangeListener(e -> {
            var delay = delaySpinnerModel.getNumber().longValue();

            logController.debug("Set delay = %d", delay);

            tokenListenerStatePanel.setTokenRefreshDuration(Duration.ofSeconds(delay));
        });
        updateHandlers.put("delay", delaySpinnerModel::setValue);
        settingsGrid.addRow(delayLabel, delaySpinner);

        // Create the header name row
        var headerNameRow = new RowBuilder(this, "headerName")
                .setLabelText("Header name: ")
                .setFieldText(TokenProfile.DEFAULT_HEADER_NAME)
                .addStatusLabel()
                .setKeyReleasedStatusHandler((field, status) -> {
                    var headerName = field.getText();

                    if (headerName.isBlank()) return;

                    if (headerName.contains(":")) {
                        status.setStatus(StatusLabel.Status.ERROR);

                        logController.error(
                                "Failed to set headerName, header cannot contain \":\""
                        );
                    } else {
                        status.setStatus(StatusLabel.Status.OK);
                        profile.setHeaderName(headerName);

                        logController.debug(
                                "Set header name = %s", headerName
                        );
                    }
                })
                .build();
        settingsGrid.addRow(headerNameRow);

        // Create the header value prefix row
        var headerValuePrefixRow = new RowBuilder(this, "headerValuePrefix")
                .setLabelText("Header value prefix: ")
                .setFieldText(TokenProfile.DEFAULT_HEADER_VALUE_PREFIX)
                .setKeyReleasedHandler((field) -> {
                    var prefix = field.getText();

                    if (!prefix.endsWith(" ")) {
                        logController.info(
                                "Header value prefix does not end with a space, this might be a mistake."
                        );
                    }

                    profile.setHeaderValuePrefix(prefix);

                    logController.debug(
                            "Set header value prefix = %s", prefix
                    );
                })
                .build();
        settingsGrid.addRow(headerValuePrefixRow);

        // Create the token regex row
        var tokenRegexRow = new RowBuilder(this, "tokenRegex")
                .setLabelText("Token regex: ")
                .setFieldText(TokenProfile.DEFAULT_TOKEN_REGEX)
                .addStatusLabel()
                .setKeyReleasedStatusHandler((field, status) -> {
                    var regex = field.getText();
                    final Pattern tokenPattern;

                    try {
                        tokenPattern = Pattern.compile(regex);
                    } catch (PatternSyntaxException e) {
                        status.setStatus(StatusLabel.Status.ERROR);

                        logController.error(
                                "Failed to set new Token Regex: %s - %s",
                                regex, e
                        );

                        return;
                    }

                    status.setStatus(StatusLabel.Status.OK);
                    profile.setTokenPattern(tokenPattern);

                    logController.debug(
                            "Set token regex = %s", tokenPattern
                    );
                })
                .build();
        settingsGrid.addRow(tokenRegexRow);

        // create the "listening" row
        var listeningLabel = new JLabel("Listening: ");
        var listeningButton = new OnOffButton("listening", "not listening", TokenProfile.DEFAULT_IS_LISTENING);
        listeningButton.addStateChangeListener(profile::setIsListening);
        updateHandlers.put(
                "isListening",
                newData -> {
                    if (newData instanceof Boolean) {
                        SwingUtilities.invokeLater(() -> {
                            listeningButton.setState((Boolean) newData);

                            logController.debug(
                                    "Set listening = %b", newData
                            );
                        });
                    }
                }
        );
        settingsGrid.addRow(listeningLabel, listeningButton);

        // create the log level row
        var logLevelLabel = new JLabel("Log Level: ");
        var logLevelBox = new JComboBox<>(LogLevel.values());
        logLevelBox.setFont(Font.decode("MONOSPACED"));
        logLevelBox.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        logLevelBox.setSelectedItem(LogTableModel.DEFAULT_LOG_LEVEL);
        logLevelBox.addActionListener(_event -> SwingUtilities.invokeLater(() -> {
            logController.setLogLevel((LogLevel) logLevelBox.getSelectedItem());

            logController.debug(
                    "Set log level = %s", logLevelBox.getSelectedItem()
            );
        }));
        updateHandlers.put("logLevel", logLevelBox::setSelectedItem);
        settingsGrid.addRow(logLevelLabel, logLevelBox);

        // Create the max log length row
        var maxLogLengthLabel = new JLabel("Max number of log entries: ");
        var maxLogLengthSpinnerModel = new SpinnerNumberModel(LogTableModel.DEFAULT_MAX_LOG_LENGTH, 0, null, 1000);
        var maxLogLengthSpinner = new JSpinner(maxLogLengthSpinnerModel);
        maxLogLengthSpinner.addChangeListener(e -> {
            var maxLogLength = maxLogLengthSpinnerModel.getNumber().intValue();

            logController.debug("Set maxLogLength = %d", maxLogLength);

            logController.setMaxLogLength(maxLogLength);
        });
        updateHandlers.put("maxLogLength", maxLogLengthSpinnerModel::setValue);
        settingsGrid.addRow(maxLogLengthLabel, maxLogLengthSpinner);
    }

    void updateRow(String rowID, Object newData) {
        updateHandlers.get(rowID).update(newData);
    }
}
