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

import burp.*;
import com.nccgroup.jwtreauth.ui.logging.LogController;
import com.nccgroup.jwtreauth.ui.scope.ScopeController;
import com.nccgroup.jwtreauth.ui.settings.SettingsController;
import com.nccgroup.jwtreauth.ui.state.TokenListenerStatePanel;
import com.nccgroup.jwtreauth.utils.UrlComparison;
import javax.validation.constraints.NotNull;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class TokenProfile {
    public static final String DEFAULT_AUTH_URL = "https://domain.sld.tld:443/path";
    public static final int DEFAULT_AUTH_REQ_DELAY = 300;
    public static final String DEFAULT_HEADER_NAME = "Authorization";
    public static final String DEFAULT_HEADER_VALUE_PREFIX = "Bearer ";
    public static final String DEFAULT_TOKEN_REGEX = "\"access_token\":\\s?\"([^\"]*)\"";
    public static final String DEFAULT_TOKEN_MISSING = "<no token found yet>";
    public static final String DEFAULT_HEADER_MISSING = "<no header made yet>";
    public static final boolean DEFAULT_IS_LISTENING = false;

    private final String id;
    private String name;

    private final AtomicInteger lastRefreshStamp = new AtomicInteger(0);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;
    private final ScopeController scopeController;

    private final LogController logController;
    private final TokenListenerStatePanel tokenListenerStatePanel;
    private SettingsController settingsController;

    private URL authorizeURL;
    private String headerName;
    private String headerValuePrefix;
    private Pattern tokenPattern;
    private boolean isListening;
    private boolean tokenSetManually;
    private Optional<String> token;
    private Optional<String> header;
    private Optional<IHttpRequestResponse> authorizeRequest;
    private Optional<IHttpRequestResponse> refreshRequest;
    private Pattern refreshTokenPattern;
    private boolean refreshEnabled;

    public TokenProfile(String name, IBurpExtenderCallbacks callbacks, ScopeController scopeController) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        this.scopeController = scopeController;

        this.logController = new LogController();

        initDefaults();

        this.tokenListenerStatePanel = new TokenListenerStatePanel(this);
        this.settingsController = new SettingsController(this);
    }

    private void initDefaults() {
        try {
            authorizeURL = new URL(DEFAULT_AUTH_URL);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        isListening = DEFAULT_IS_LISTENING;
        headerName = DEFAULT_HEADER_NAME;
        headerValuePrefix = DEFAULT_HEADER_VALUE_PREFIX;

        tokenPattern = Pattern.compile(DEFAULT_TOKEN_REGEX);
        token = Optional.empty();
        header = Optional.empty();

        authorizeRequest = Optional.empty();
        refreshRequest = Optional.empty();
        refreshTokenPattern = tokenPattern;
        refreshEnabled = false;
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URL getAuthorizeURL() {
        return authorizeURL;
    }

    public LogController getLogController() {
        return logController;
    }

    public SettingsController getSettingsController() {
        return settingsController;
    }

    public TokenListenerStatePanel getTokenListenerStatePanel() {
        return tokenListenerStatePanel;
    }

    public boolean isListening() {
        return isListening;
    }

    public boolean isTokenSetManually() {
        return tokenSetManually;
    }

    // --- Request processing ---

    public void processRequest(IHttpRequestResponse currentRequest, IRequestInfo requestInfo) {
        if (token.isPresent() && scopeController.inScopeForProfile(requestInfo.getUrl(), this.name)) {
            logController.debug(
                    "URL: %s matches scope for profile \"%s\", adding header.%n",
                    requestInfo.getUrl(), name
            );

            var updatedRequest = replaceHeaders(currentRequest);
            currentRequest.setRequest(updatedRequest);
        }
    }

    public void processAuthResponse(@NotNull IHttpRequestResponse currentRequest) {
        processAuthResponse(currentRequest, false);
    }

    public void processAuthResponse(@NotNull IHttpRequestResponse currentRequest, boolean fromContextMenu) {
        if (fromContextMenu) {
            tokenSetManually = false;
        }

        if (tokenSetManually) {
            logController.debug("Token set manually, ignoring auth response.");
            return;
        }

        var m = tokenPattern.matcher(
                helpers.bytesToString(currentRequest.getResponse())
        );

        if (m.find()) {
            var token = m.group(1);
            updateToken(token);

            logController.info(
                    "Parsed token \"%s\" from response to authorization URL.", token
            );
        } else {
            logController.info("Failed to parse token from response to authorization URL.");
        }
    }

    // --- Token refresh ---

    public void scheduleTokenRefresh() {
        logController.debug("Token refresh scheduled.");
        var stamp = this.lastRefreshStamp.incrementAndGet();

        executor.schedule(
                () -> {
                    int currStamp = this.lastRefreshStamp.get();
                    if (stamp == currStamp) {
                        logController.debug("Stamps match - refreshing token.");
                        this.refreshToken();
                    } else {
                        logController.debug("Stamps don't match - ignoring refresh.");
                    }
                },
                2,
                TimeUnit.SECONDS);
    }

    private void refreshToken() {
        IHttpRequestResponse resp = null;
        boolean usedRefreshRequest = false;

        try {
            if (this.refreshEnabled && this.refreshRequest.isPresent()) {
                // Use the dedicated refresh request if enabled and set
                var req = this.refreshRequest.get();
                resp = callbacks.makeHttpRequest(
                        req.getHttpService(), req.getRequest()
                );
                usedRefreshRequest = true;
            } else if (this.authorizeRequest.isPresent()) {
                var req = this.authorizeRequest.get();
                resp = callbacks.makeHttpRequest(
                        req.getHttpService(), req.getRequest()
                );
            } else {
                if (this.authorizeURL.toString().equals(DEFAULT_AUTH_URL)) return;

                var service = helpers.buildHttpService(
                        this.authorizeURL.getHost(),
                        this.authorizeURL.getPort(),
                        this.authorizeURL.getProtocol()
                );

                var request = helpers.buildHttpRequest(this.authorizeURL);

                resp = callbacks.makeHttpRequest(service, request);
            }
        } catch (RuntimeException e) {
            SwingUtilities.invokeLater(
                    () -> logController.error(
                            "Caught exception while refreshing token: %s", e
                    )
            );
        }

        // Always process inline — don't rely on the listener.
        if (resp != null) {
            if (usedRefreshRequest) {
                processRefreshResponse(resp);
            } else {
                processAuthResponse(resp);
            }
        }
    }

    private void processRefreshResponse(@NotNull IHttpRequestResponse currentRequest) {
        var m = refreshTokenPattern.matcher(
                helpers.bytesToString(currentRequest.getResponse())
        );

        if (m.find()) {
            var token = m.group(1);
            updateToken(token);

            logController.info(
                    "Parsed token \"%s\" from refresh token response.", token
            );
        } else {
            logController.info("Failed to parse token from refresh token response.");
        }
    }

    // --- Header management ---

    private byte[] replaceHeaders(IHttpRequestResponse currentRequest) {
        var requestInfo = helpers.analyzeRequest(currentRequest);

        var headers = (ArrayList<String>) requestInfo.getHeaders();
        headers.removeIf(h -> h.startsWith(this.makeHeaderPrefix()));
        headers.add(this.makeHeader());

        return helpers.buildHttpMessage(headers,
                Arrays.copyOfRange(currentRequest.getRequest(),
                        requestInfo.getBodyOffset(),
                        currentRequest.getRequest().length));
    }

    @NotNull
    private String makeHeaderPrefix() {
        return headerName + ": " + headerValuePrefix;
    }

    @NotNull
    private String makeHeader() {
        if (this.header.isEmpty()) {
            this.header = Optional.of(this.makeHeaderPrefix() + token.orElse("<no token>"));

            tokenListenerStatePanel.setHeaderFieldText(this.header.get());
        }

        return this.header.get();
    }

    // --- Invalidation ---

    private void invalidateCachedRequest() {
        setIsListening(false);

        this.authorizeRequest = Optional.empty();

        this.invalidateCachedToken();
    }

    private void invalidateCachedToken() {
        setIsListening(false);

        this.token = Optional.empty();

        this.invalidateCachedHeader(false);

        tokenListenerStatePanel.updateToken(DEFAULT_TOKEN_MISSING, false);
    }

    private void invalidateCachedHeader(boolean setIsListeningFalse) {
        if (setIsListeningFalse) setIsListening(false);

        this.header = Optional.empty();

        tokenListenerStatePanel.setHeaderFieldText(DEFAULT_HEADER_MISSING);
    }

    private void updateToken(@NotNull String newToken) {
        this.invalidateCachedHeader(false);

        this.token = Optional.of(newToken);

        tokenListenerStatePanel.updateToken(newToken, false);
    }

    public void setTokenManual(@NotNull String newToken) {
        this.invalidateCachedHeader(false);

        this.token = Optional.of(newToken);
        this.tokenSetManually = true;

        logController.debug("Token set manually: token = \"%s\"", this.token);

        tokenListenerStatePanel.updateToken(newToken, true);
    }

    // --- Setters ---

    public void setAuthorizeURL(@NotNull URL newAuthorizeURL) {
        if (!UrlComparison.compareEqual(authorizeURL, newAuthorizeURL)) {
            this.invalidateCachedRequest();

            this.authorizeURL = newAuthorizeURL;

            logController.debug(
                    "Set new Authorization request: %s", newAuthorizeURL
            );

            this.scheduleTokenRefresh();
        }
    }

    public void setAuthorizeRequest(IHttpRequestResponse authorizeRequest) {
        invalidateCachedRequest();

        authorizeURL = helpers.analyzeRequest(authorizeRequest).getUrl();

        // Send the full raw request to the settings panel
        var rawRequest = helpers.bytesToString(authorizeRequest.getRequest());
        settingsController.updateRow("authRequest", rawRequest);

        this.tokenSetManually = false;
        this.authorizeRequest = Optional.of(authorizeRequest);

        logController.debug("Set new Authorization Request.");

        this.scheduleTokenRefresh();
    }

    public void clearAuthorizeRequest() {
        this.authorizeRequest = Optional.empty();
        logController.debug("Cleared Authorization Request.");
    }

    public void clearRefreshRequest() {
        this.refreshRequest = Optional.empty();
        logController.debug("Cleared Refresh Request.");
    }

    public void setRefreshEnabled(boolean enabled) {
        this.refreshEnabled = enabled;
        logController.debug("Refresh token %s.", enabled ? "enabled" : "disabled");
    }

    public void setHeaderName(String newHeaderName) {
        if (!this.headerName.equals(newHeaderName)) {
            this.invalidateCachedHeader(true);

            this.headerName = newHeaderName;

            logController.debug(
                    "Set new Header Name: %s", newHeaderName
            );
        }
    }

    public void setHeaderValuePrefix(String newHeaderValuePrefix) {
        if (!this.headerValuePrefix.equals(newHeaderValuePrefix)) {
            this.invalidateCachedHeader(true);

            this.headerValuePrefix = newHeaderValuePrefix;

            logController.debug(
                    "Set new Header Value Prefix: %s", newHeaderValuePrefix
            );
        }
    }

    public void setTokenPattern(Pattern newTokenPattern) {
        if (!this.tokenPattern.equals(newTokenPattern)) {
            this.invalidateCachedToken();

            this.tokenPattern = newTokenPattern;

            logController.debug(
                    "Set new Token Regex: %s", newTokenPattern
            );
        }
    }

    public void setRefreshRequest(IHttpRequestResponse refreshRequest) {
        var rawRequest = helpers.bytesToString(refreshRequest.getRequest());
        settingsController.updateRow("refreshRequest", rawRequest);

        this.refreshRequest = Optional.of(refreshRequest);

        logController.debug("Set new Refresh Request.");
    }

    public void setRefreshTokenPattern(Pattern newPattern) {
        this.refreshTokenPattern = newPattern;
        logController.debug("Set new Refresh Token Regex: %s", newPattern);
    }

    public void setIsListening(boolean isListening) {
        if (this.isListening == isListening) return;

        this.isListening = isListening;
        settingsController.updateRow("isListening", isListening);

        if (this.isListening) {
            this.scheduleTokenRefresh();
        } else {
            tokenListenerStatePanel.stopTimer();
            invalidateCachedToken();
        }
    }

    // --- Lifecycle ---

    public void shutdown() {
        tokenListenerStatePanel.stopTimer();
        executor.shutdown();
        executor.shutdownNow();
    }
}
