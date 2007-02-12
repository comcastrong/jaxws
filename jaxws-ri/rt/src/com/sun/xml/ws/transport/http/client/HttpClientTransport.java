/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems Inc. All Rights Reserved
 */

package com.sun.xml.ws.transport.http.client;

import com.sun.xml.ws.api.EndpointAddress;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.client.BindingProviderProperties;
import static com.sun.xml.ws.client.BindingProviderProperties.*;
import com.sun.xml.ws.client.ClientTransportException;
import com.sun.xml.ws.resources.ClientMessages;
import com.sun.xml.ws.transport.Headers;
import com.sun.xml.ws.util.ByteArrayBuffer;
import com.sun.xml.ws.developer.JAXWSProperties;
import com.sun.istack.Nullable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.ws.BindingProvider;
import static javax.xml.ws.BindingProvider.SESSION_MAINTAIN_PROPERTY;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TODO: this class seems to be pointless. Just merge it with {@link HttpTransportPipe}.
 *
 * @author WS Development Team
 */
final class HttpClientTransport {
    
    // Need to use JAXB first to register DatatypeConverter
    static {
        try {
            JAXBContext.newInstance().createUnmarshaller();
        } catch(JAXBException je) {
            // Nothing much can be done. Intentionally left empty
        }
    }
    private static String LAST_ENDPOINT = "";
    private static boolean redirect = true;
    private static final int START_REDIRECT_COUNT = 3;
    private static int redirectCount = START_REDIRECT_COUNT;

    /*package*/ int statusCode;
    private final Map<String, List<String>> reqHeaders;
    private Map<String, List<String>> respHeaders = null;

    private OutputStream outputStream;
    private boolean https;
    private HttpURLConnection httpConnection = null;
    private EndpointAddress endpoint = null;
    private Packet context = null;
    private CookieJar cookieJar = null;
    private boolean isFailure = false;


    public HttpClientTransport(Packet packet, Map<String,List<String>> reqHeaders) {
        endpoint = packet.endpointAddress;
        context = packet;
        this.reqHeaders = reqHeaders;
    }

    /**
     * Prepare the stream for HTTP request
     */
    public OutputStream getOutput() {
        try {
            createHttpConnection();
            sendCookieAsNeeded();

            // for "GET" request no need to get outputStream
            if (requiresOutputStream()) {
                outputStream = httpConnection.getOutputStream();
            }

            httpConnection.connect();

        } catch (Exception ex) {
            throw new ClientTransportException(
                ClientMessages.localizableHTTP_CLIENT_FAILED(ex),ex);
        }

        return outputStream;
    }

    public void closeOutput() throws IOException {
        if (outputStream != null) {
            outputStream.flush();
            outputStream.close();
        }
    }

    /**
     * Get the response from HTTP connection and prepare the input stream for response
     */
    public InputStream getInput() {
        // response processing

        InputStream in;
        try {
            in = readResponse();
        } catch (IOException e) {
            if (statusCode == HttpURLConnection.HTTP_NO_CONTENT
                || (isFailure
                && statusCode != HttpURLConnection.HTTP_INTERNAL_ERROR)) {
                try {
                    throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(
                        statusCode, httpConnection.getResponseMessage()));
                } catch (IOException ex) {
                    throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(
                        statusCode, ex));
                }
            }
            throw new ClientTransportException(ClientMessages.localizableHTTP_CLIENT_FAILED(e),e);
        }
        return in;
    }

    public Map<String, List<String>> getHeaders() {
        if (respHeaders != null) {
            return respHeaders;
        }
        respHeaders = new Headers();
        respHeaders.putAll(httpConnection.getHeaderFields());
        return respHeaders;
    }

    protected InputStream readResponse()
        throws IOException {
        InputStream contentIn =
            (isFailure
                ? httpConnection.getErrorStream()
                : httpConnection.getInputStream());

        ByteArrayBuffer bab = new ByteArrayBuffer();
        if (contentIn != null) { // is this really possible?
            bab.write(contentIn);
            bab.close();
        }

        int length =
            httpConnection.getContentLength() == -1
                ? bab.size()
                : httpConnection.getContentLength();

        return bab.newInputStream(0, length);
    }

    /*
     * Will throw an exception instead of returning 'false' if there is no
     * return message to be processed (i.e., in the case of an UNAUTHORIZED
     * response from the servlet or 404 not found)
     */
    /*package*/void checkResponseCode() {
        try {

            statusCode = httpConnection.getResponseCode();

            if ((httpConnection.getResponseCode()
                == HttpURLConnection.HTTP_INTERNAL_ERROR)) {
                isFailure = true;
                //added HTTP_ACCEPT for 1-way operations
            } else if (
                httpConnection.getResponseCode()
                    == HttpURLConnection.HTTP_UNAUTHORIZED) {

                // no soap message returned, so skip reading message and throw exception
                throw new ClientTransportException(
                    ClientMessages.localizableHTTP_CLIENT_UNAUTHORIZED(httpConnection.getResponseMessage()));
            } else if (
                httpConnection.getResponseCode()
                    == HttpURLConnection.HTTP_NOT_FOUND) {

                // no message returned, so skip reading message and throw exception
                throw new ClientTransportException(
                    ClientMessages.localizableHTTP_NOT_FOUND(httpConnection.getResponseMessage()));
            } else if (
                (statusCode == HttpURLConnection.HTTP_MOVED_TEMP) ||
                    (statusCode == HttpURLConnection.HTTP_MOVED_PERM)) {
                isFailure = true;

                if (!redirect || (redirectCount <= 0)) {
                    throw new ClientTransportException(
                        ClientMessages.localizableHTTP_STATUS_CODE(statusCode,getStatusMessage()));
                }
            } else if (
                statusCode < 200 || (statusCode >= 303 && statusCode < 500)) {
                throw new ClientTransportException(
                    ClientMessages.localizableHTTP_STATUS_CODE(statusCode,getStatusMessage()));
            } else if (statusCode >= 500) {
                isFailure = true;
            }
        } catch (IOException e) {
            throw new WebServiceException(e);
        }
        // Hack for now
        saveCookieAsNeeded();
    }

    private String getStatusMessage() throws IOException {
        int statusCode = httpConnection.getResponseCode();
        String message = httpConnection.getResponseMessage();
        if (statusCode == HttpURLConnection.HTTP_CREATED
            || (statusCode >= HttpURLConnection.HTTP_MULT_CHOICE
            && statusCode != HttpURLConnection.HTTP_NOT_MODIFIED
            && statusCode < HttpURLConnection.HTTP_BAD_REQUEST)) {
            String location = httpConnection.getHeaderField("Location");
            if (location != null)
                message += " - Location: " + location;
        }
        return message;
    }

    protected void sendCookieAsNeeded() {
        Boolean shouldMaintainSessionProperty =
            (Boolean) context.invocationProperties.get(SESSION_MAINTAIN_PROPERTY);
        if (shouldMaintainSessionProperty != null && shouldMaintainSessionProperty) {
            cookieJar = (CookieJar) context.invocationProperties.get(HTTP_COOKIE_JAR);
            if (cookieJar == null) {
                cookieJar = new CookieJar();

                // need to store in binding's context so it is not lost
                context.proxy.getRequestContext().put(HTTP_COOKIE_JAR, cookieJar);
            }
            cookieJar.applyRelevantCookies(httpConnection);
        }
    }

    private void saveCookieAsNeeded() {
        if (cookieJar != null) {
            cookieJar.recordAnyCookies(httpConnection);
        }
    }


    private void createHttpConnection() throws IOException {

        boolean verification = false;
        // does the client want client hostname verification by the service
        String verificationProperty =
            (String) context.invocationProperties.get(HOSTNAME_VERIFICATION_PROPERTY);
        if (verificationProperty != null) {
            if (verificationProperty.equalsIgnoreCase("true"))
                verification = true;
        }

        // does the client want request redirection to occur
        String redirectProperty =
            (String) context.invocationProperties.get(REDIRECT_REQUEST_PROPERTY);
        if (redirectProperty != null) {
            if (redirectProperty.equalsIgnoreCase("false"))
                redirect = false;
        }

        checkEndpoints();

        httpConnection = (HttpURLConnection) endpoint.openConnection();
        if (httpConnection instanceof HttpsURLConnection) {
            https = true;
        }

        if (!verification) {
            // for https hostname verification  - turn off by default
            if (httpConnection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) httpConnection).setHostnameVerifier(new HttpClientVerifier());
            }
        }

        writeBasicAuthAsNeeded(context, reqHeaders);

        // allow interaction with the web page - user may have to supply
        // username, password id web page is accessed from web browser
        httpConnection.setAllowUserInteraction(true);

        // enable input, output streams
        httpConnection.setDoOutput(true);
        httpConnection.setDoInput(true);

        String requestMethod = (String) context.invocationProperties.get(MessageContext.HTTP_REQUEST_METHOD);
        String method = (requestMethod != null) ? requestMethod : "POST";
        httpConnection.setRequestMethod(method);

        //this code or something similiar needs t be moved elsewhere for error checking
        /*if (context.invocationProperties.get(BindingProviderProperties.BINDING_ID_PROPERTY).equals(HTTPBinding.HTTP_BINDING)){
            method = (requestMethod != null)?requestMethod:method;            
        } else if
            (context.invocationProperties.get(BindingProviderProperties.BINDING_ID_PROPERTY).equals(SOAPBinding.SOAP12HTTP_BINDING) &&
            "GET".equalsIgnoreCase(requestMethod)) {
        }
       */     

        Integer reqTimeout = (Integer)context.invocationProperties.get(BindingProviderProperties.REQUEST_TIMEOUT);
        if (reqTimeout != null) {
            httpConnection.setReadTimeout(reqTimeout);
        }

        Boolean streaming = (Boolean)context.invocationProperties.get(JAXWSProperties.HTTP_CLIENT_STREAMING);
        if (streaming != null && streaming) {
            httpConnection.setChunkedStreamingMode(4096);
        }

        // set the properties on HttpURLConnection
        for (Map.Entry<String, List<String>> entry : reqHeaders.entrySet()) {
            httpConnection.addRequestProperty(entry.getKey(), entry.getValue().get(0));
        }
    }

    public boolean isSecure() {
        return https;
    }

    //    private void redirectRequest(HttpURLConnection httpConnection, SOAPMessageContext context) {
//        String redirectEndpoint = httpConnection.getHeaderField("Location");
//        if (redirectEndpoint != null) {
//            httpConnection.disconnect();
//            invoke(redirectEndpoint, context);
//        } else
//            System.out.println("redirection Failed");
//    }

    private boolean checkForRedirect(int statusCode) {
        return (((statusCode == 301) || (statusCode == 302)) && redirect && (redirectCount-- > 0));
    }

    private void checkEndpoints() {
        if (!LAST_ENDPOINT.equalsIgnoreCase(endpoint.toString())) {
            redirectCount = START_REDIRECT_COUNT;
            LAST_ENDPOINT = endpoint.toString();
        }
    }


    private void writeBasicAuthAsNeeded(Packet context, Map<String, List<String>> reqHeaders) {
        String user = (String) context.invocationProperties.get(BindingProvider.USERNAME_PROPERTY);
        if (user != null) {
            String pw = (String) context.invocationProperties.get(BindingProvider.PASSWORD_PROPERTY);
            if (pw != null) {
                StringBuffer buf = new StringBuffer(user);
                buf.append(":");
                buf.append(pw);
                String creds = printBase64Binary(buf.toString().getBytes());
                reqHeaders.put("Authorization", Collections.singletonList("Basic "+creds));
            }
        }
    }

    private boolean requiresOutputStream() {
        return !(httpConnection.getRequestMethod().equalsIgnoreCase("GET") ||
                httpConnection.getRequestMethod().equalsIgnoreCase("HEAD") ||
                httpConnection.getRequestMethod().equalsIgnoreCase("DELETE"));
    }

    public @Nullable String getContentType() {
        return httpConnection.getContentType();
    }

    // overide default SSL HttpClientVerifier to always return true
    // effectively overiding Hostname client verification when using SSL
    private static class HttpClientVerifier implements HostnameVerifier {
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }

}

