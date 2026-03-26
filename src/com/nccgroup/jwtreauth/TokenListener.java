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
import com.nccgroup.jwtreauth.utils.UrlComparison;

public class TokenListener implements IHttpListener, IExtensionStateListener {
    private final IExtensionHelpers helpers;
    private final ProfileManager profileManager;

    public TokenListener(IBurpExtenderCallbacks callbacks, ProfileManager profileManager) {
        this.helpers = callbacks.getHelpers();
        this.profileManager = profileManager;
    }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse currentRequest) {
        var requestInfo = helpers.analyzeRequest(currentRequest);

        for (TokenProfile profile : profileManager.getProfiles()) {
            if (!profile.isListening()) continue;

            boolean isAuthUrl = UrlComparison.compareEqual(requestInfo.getUrl(), profile.getAuthorizeURL());

            if (messageIsRequest && !isAuthUrl) {
                profile.processRequest(currentRequest, requestInfo);
            } else if (!messageIsRequest && isAuthUrl
                    && toolFlag != IBurpExtenderCallbacks.TOOL_EXTENDER) {
                // Only process auth responses from user tools (proxy, repeater, etc.)
                // Extension-initiated refresh responses are handled inline in refreshToken()
                // to prevent cross-contamination between profiles sharing the same auth URL.
                profile.processAuthResponse(currentRequest);
            }
        }
    }

    @Override
    public void extensionUnloaded() {
        profileManager.shutdownAll();
    }
}
