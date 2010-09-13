/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.xml.ws.transport.http.servlet;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.server.PortAddressResolver;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.api.server.WebServiceContextDelegate;
import com.sun.xml.ws.resources.WsservletMessages;
import com.sun.xml.ws.transport.Headers;
import com.sun.xml.ws.transport.http.HttpAdapter;
import com.sun.xml.ws.transport.http.WSHTTPConnection;
import com.sun.xml.ws.developer.JAXWSProperties;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 *{@link WSHTTPConnection} implemented for {@link HttpServlet}.
 *
 * @author WS Development Team
 */
final class ServletConnectionImpl extends WSHTTPConnection implements WebServiceContextDelegate {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final ServletContext context;
    private int status;
    private Headers requestHeaders;
    private final HttpAdapter adapter;
    private Headers responseHeaders;

    public ServletConnectionImpl(@NotNull HttpAdapter adapter, ServletContext context, HttpServletRequest request, HttpServletResponse response) {
        this.adapter = adapter;
        this.context = context;
        this.request = request;
        this.response = response;
    }

    @Override
    @Property({MessageContext.HTTP_REQUEST_HEADERS, Packet.INBOUND_TRANSPORT_HEADERS})
    public @NotNull Map<String,List<String>> getRequestHeaders() {
        if (requestHeaders == null) {
            requestHeaders = new Headers();
            Enumeration enums = request.getHeaderNames();
            while (enums.hasMoreElements()) {
                String headerName = (String) enums.nextElement();
                String headerValue = request.getHeader(headerName);
                List<String> values = requestHeaders.get(headerName);
                if (values == null) {
                    values = new ArrayList<String>();
                    requestHeaders.put(headerName, values);
                }
                values.add(headerValue);
            }
        }
        return requestHeaders;
    }

    /**
     * sets response headers.
     */
    @Override
    public void setResponseHeaders(Map<String,List<String>> headers) {
        response.reset();       // clear the status code & headers
        if (status != 0) {
            response.setStatus(status);
        }
        if (headers == null) {
            responseHeaders = null;
            return;
        } else {
            if (responseHeaders == null) {
                responseHeaders = new Headers();
            } else {
                responseHeaders.clear();
            }
        }

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if(name.equalsIgnoreCase("Content-Type") || name.equalsIgnoreCase("Content-Length"))
                continue;   // ignore headers that interfere with the operation
            for (String value : entry.getValue()) {
                response.addHeader(name, value);
            }
        }

    }
    @Override
    @Property({MessageContext.HTTP_RESPONSE_HEADERS, Packet.OUTBOUND_TRANSPORT_HEADERS})
    public Map<String,List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
        // Servlet containers don't seem to like setting of the value multiple times
        // Moving the following to getOutput()
        //response.setStatus(status);
    }

    @Override
    @Property(MessageContext.HTTP_RESPONSE_CODE)
    public int getStatus() {
        return status;
    }

    @Override
    public void setContentTypeResponseHeader(@NotNull String value) {
        response.setContentType(value);
    }

    @Override
    public @NotNull InputStream getInput() throws IOException {
        return request.getInputStream();
    }

    @Override
    public @NotNull OutputStream getOutput() throws IOException {
        response.setStatus(status);
        return response.getOutputStream();
    }

    public @NotNull WebServiceContextDelegate getWebServiceContextDelegate() {
        return this;
    }

    public Principal getUserPrincipal(Packet p) {
        return request.getUserPrincipal();
    }

    public boolean isUserInRole(Packet p,String role) {
        return request.isUserInRole(role);
    }

    public @NotNull String getEPRAddress(Packet p, WSEndpoint endpoint) {
        PortAddressResolver resolver = adapter.owner.createPortAddressResolver(getBaseAddress());
        String address = resolver.getAddressFor(endpoint.getServiceName(), endpoint.getPortName().getLocalPart());
        if(address==null)
            throw new WebServiceException(WsservletMessages.SERVLET_NO_ADDRESS_AVAILABLE(endpoint.getPortName()));
        return address;
    }


    public String getWSDLAddress(@NotNull Packet request, @NotNull WSEndpoint endpoint) {
        String eprAddress = getEPRAddress(request,endpoint);
        if(adapter.getEndpoint().getPort() != null)
            return eprAddress+"?wsdl";
        else
            return null;
    }

    @Override
    @Property(MessageContext.HTTP_REQUEST_METHOD)
    public @NotNull String getRequestMethod() {
        return request.getMethod();
    }

    @Override
    public boolean isSecure() {
        return request.getScheme().equals("https");
    }

    @Override
    public String getRequestHeader(String headerName) {
        return request.getHeader(headerName);
    }

    @Override
    @Property(MessageContext.QUERY_STRING)
    public String getQueryString() {
        return request.getQueryString();
    }

    @Override
    @Property(MessageContext.PATH_INFO)
    public @NotNull String getPathInfo() {
        return request.getPathInfo();
    }

    public @NotNull String getBaseAddress() {
        return getBaseAddress(request);
    }

    static @NotNull String getBaseAddress(HttpServletRequest request) {
        StringBuilder buf = new StringBuilder();
        buf.append(request.getScheme());
        buf.append("://");
        buf.append(request.getServerName());
        buf.append(':');
        buf.append(request.getServerPort());
        buf.append(request.getContextPath());

        return buf.toString();
    }

    @Property(MessageContext.SERVLET_CONTEXT)
    public ServletContext getContext() {
        return context;
    }

    @Property(MessageContext.SERVLET_RESPONSE)
    public HttpServletResponse getResponse() {
        return response;
    }

    @Property(MessageContext.SERVLET_REQUEST)
    public HttpServletRequest getRequest() {
        return request;
    }

    @Property(JAXWSProperties.HTTP_REQUEST_URL)
    public String getRequestURL() {
        return request.getRequestURL().toString();
    }

    @Override
    public String getProtocol() {
        return request.getProtocol();
    }

    @Override
    public void setContentLengthResponseHeader(int value) {
        response.setContentLength(value);
    }    

    protected PropertyMap getPropertyMap() {
        return model;
    }

    private static final PropertyMap model;

    static {
        model = parse(ServletConnectionImpl.class);
    }
}
