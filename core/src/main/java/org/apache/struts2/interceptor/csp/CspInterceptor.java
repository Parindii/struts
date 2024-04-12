/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.struts2.interceptor.csp;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.action.CspSettingsAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Optional;

/**
 * Interceptor that implements Content Security Policy on incoming requests used to protect against
 * common XSS and data injection attacks. Uses {@link CspSettings} to add appropriate Content Security Policy header
 * to the response. These headers determine what the browser will consider a policy violation and the browser's behavior
 * when a violation occurs. A detailed explanation of CSP can be found <a href="https://csp.withgoogle.com/docs/index.html">here</a>.
 *
 * @see <a href="https://csp.withgoogle.com/docs/index.html">https://csp.withgoogle.com/docs/index.html/</a>
 * @see CspSettings
 * @see DefaultCspSettings
 **/
public final class CspInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LogManager.getLogger(CspInterceptor.class);

    private boolean prependServletContext = true;
    private boolean enforcingMode;
    private String reportUri;
    private String reportTo;

    private String defaultCspSettingsClassName = DefaultCspSettings.class.getName();

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        Object action = invocation.getAction();
        if (action instanceof CspSettingsAware) {
            LOG.trace("Using CspSettings provided by the action: {}", action);
            applySettings(invocation, ((CspSettingsAware) action).getCspSettings());
        } else {
            LOG.trace("Using {} with action: {}", defaultCspSettingsClassName, action);

            // if the defaultCspSettingsClassName is not a real class, throw an exception
            try {
                Class.forName(defaultCspSettingsClassName, false, Thread.currentThread().getContextClassLoader());
            }
            catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("The defaultCspSettingsClassName must be a real class.");
            }

            // if defaultCspSettingsClassName does not implement CspSettings, throw an exception
            if (!CspSettings.class.isAssignableFrom(Class.forName(defaultCspSettingsClassName))) {
                throw new IllegalArgumentException("The defaultCspSettingsClassName must implement CspSettings.");
            }

            CspSettings cspSettings = (CspSettings) Class.forName(defaultCspSettingsClassName)
                    .getDeclaredConstructor().newInstance();
            applySettings(invocation, cspSettings);
        }
        return invocation.invoke();
    }

    private void applySettings(ActionInvocation invocation, CspSettings cspSettings) {
        HttpServletRequest request = invocation.getInvocationContext().getServletRequest();
        HttpServletResponse response = invocation.getInvocationContext().getServletResponse();

        LOG.trace("Applying: {} to enforcingMode", enforcingMode);
        cspSettings.setEnforcingMode(enforcingMode);

        if (reportUri != null) {
            LOG.trace("Applying: {} to reportUri", reportUri);
            String finalReportUri = reportUri;

            if (prependServletContext && (request.getContextPath() != null) && (!request.getContextPath().isEmpty())) {
                finalReportUri = request.getContextPath() + finalReportUri;
            }

            cspSettings.setReportUri(finalReportUri);

            // apply reportTo if set
            if (reportTo != null) {
                LOG.trace("Applying: {} to reportTo", reportTo);
                cspSettings.setReportTo(reportTo);
            }
        }

        invocation.addPreResultListener((actionInvocation, resultCode) -> {
            LOG.trace("Applying CSP header: {} to the request", cspSettings);
            cspSettings.addCspHeaders(request, response);
        });
    }

    public void setReportUri(String reportUri) {
        Optional<URI> uri = buildUri(reportUri);
        if (!uri.isPresent()) {
            throw new IllegalArgumentException("Could not parse configured report URI for CSP interceptor: " + reportUri);
        }

        if (!uri.get().isAbsolute() && !reportUri.startsWith("/")) {
            throw new IllegalArgumentException("Illegal configuration: report URI is not relative to the root. Please set a report URI that starts with /");
        }

        this.reportUri = reportUri;
    }

    /**
     * Sets the report group where csp violation reports will be sent. This will
     * only be used if the reportUri is set.
     *
     * @param reportTo the report group where csp violation reports will be sent
     *
     * @since Struts 6.5.0
     */
    public void setReportTo(String reportTo) {
        this.reportTo = reportTo;
    }

    private Optional<URI> buildUri(String reportUri) {
        try {
            return Optional.of(URI.create(reportUri));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Enables enforcing mode, by default all exceptions are only reported
     *
     * @param enforcingMode {@code true} to enable enforcing mode, {@code false} to keep reporting mode.
     */
    public void setEnforcingMode(boolean enforcingMode) {
        this.enforcingMode = enforcingMode;
    }

    /**
     * Sets whether to prepend the servlet context path to the {@link #reportUri}.
     *
     * @param prependServletContext {@code true} to prepend the location with the servlet context path,
     *                              {@code false} otherwise.
     */
    public void setPrependServletContext(boolean prependServletContext) {
        this.prependServletContext = prependServletContext;
    }

    /**
     * Sets the class name of the default {@link CspSettings} implementation to use when the action does not
     * set its own values. If not set, the default is {@link DefaultCspSettings}.
     *
     * @since Struts 6.5.0
     */
    public void setDefaultCspSettingsClassName(String defaultCspSettingsClassName) {
        this.defaultCspSettingsClassName = defaultCspSettingsClassName;
    }
}