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

package com.nccgroup.jwtreauth.ui.misc;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class OnOffButton extends JButton {
    private final static Color DEFAULT_ON_COLOR = new Color(0x2E7D32);
    private final static Color DEFAULT_OFF_COLOR = new Color(0xC62828);
    private final Color onColor;
    private final Color offColor;
    private final String onText;
    private final String offText;
    private final List<StateChangeListener> stateChangeListeners;
    private boolean state;

    /**
     * Delegating default constructor which sets the background colour as a nice light blue,
     * as well as the text for on/off being the same.
     *
     * @param text     the text to display in the button
     * @param startsOn whether the button should start in the "on" or "off" state
     */
    public OnOffButton(String text, boolean startsOn) {
        this(text, text, startsOn, DEFAULT_ON_COLOR, DEFAULT_OFF_COLOR);
    }

    /**
     * Delegating default constructor which sets the background colour as a nice light blue.
     *
     * @param onText   the text to display in the button in the "on" state
     * @param offText  the text to display in the button in the "off" state
     * @param startsOn whether the button should start in the "on" or "off" state
     */
    public OnOffButton(String onText, String offText, boolean startsOn) {
        this(onText, offText, startsOn, DEFAULT_ON_COLOR, DEFAULT_OFF_COLOR);
    }

    /**
     * Constructor which gives the user the option to provide their own colour to show when set.
     *
     * @param onText   the text to display in the button in the "on" state
     * @param offText  the text to display in the button in the "off" state
     * @param startsOn whether the button should start in the "on" or "off" state
     * @param bgColor  the background colour for when the button is in the "on" state
     */
    public OnOffButton(String onText, String offText, boolean startsOn, Color onColor, Color offColor) {
        super(startsOn ? onText : offText);

        this.onColor = onColor;
        this.offColor = offColor;
        this.onText = onText;
        this.offText = offText;
        this.state = startsOn;
        this.stateChangeListeners = new ArrayList<>();

        updateGUI();

        addActionListener(e -> {
            // every time the button is pressed flip the state and update the background
            state = !state;
            SwingUtilities.invokeLater(this::updateGUI);

            // notify all the listeners
            for (var scl : stateChangeListeners) {
                scl.onStateChange(state);
            }
        });
    }

    public void addStateChangeListener(StateChangeListener onStateChange) {
        stateChangeListeners.add(onStateChange);
    }

    public void removeStateChangeListener(StateChangeListener onStateChange) {
        stateChangeListeners.remove(onStateChange);
    }

    /**
     * API for setting the state of the button without
     * triggering any of the state change listeners.
     *
     * @param newState the new state of the button
     */
    public void setState(boolean newState) {
        this.state = newState;

        SwingUtilities.invokeLater(this::updateGUI);
    }

    /**
     * Helper method to update the background colour based on the current state
     */
    private void updateGUI() {
        if (state) {
            setBackground(this.onColor);
            setForeground(Color.WHITE);
            setText(this.onText);
        } else {
            setBackground(this.offColor);
            // Use white text on dark backgrounds, default text on light backgrounds
            var brightness = (offColor.getRed() + offColor.getGreen() + offColor.getBlue()) / 3;
            setForeground(brightness < 128 ? Color.WHITE : UIManager.getColor("Button.foreground"));
            setText(this.offText);
        }
    }

    public boolean isOn() {
        return this.state;
    }

    public interface StateChangeListener {
        void onStateChange(boolean newState);
    }
}
