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
package com.sun.xml.ws.client;

import com.sun.xml.ws.client.WSServiceDelegate;
import com.sun.xml.ws.util.HandlerAnnotationInfo;
import com.sun.xml.ws.util.HandlerAnnotationProcessor;
import com.sun.xml.ws.handler.HandlerChainsModel;

import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.ws.handler.PortInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Implementation class of HandlerResolver. This class is a simple
 * map of PortInfo objects to handler chains. It is used by a
 * {@link com.sun.xml.ws.client.WSServiceDelegate} object, and can
 * be replaced by user code with a different class implementing
 * HandlerResolver. This class is only used on the client side, and
 * it includes a lot of logging to help when there are issues since
 * it deals with port names, service names, and bindings. All three
 * must match when getting a handler chain from the map.
 *
 * <p>It is created by the {@link com.sun.xml.ws.client.WSServiceDelegate}
 * class , which uses {@link com.sun.xml.ws.util.HandlerAnnotationProcessor} to create
 * a handler chain and then it sets the chains on this class and they
 * are put into the map. The ServiceContext uses the map to set handler
 * chains on bindings when they are created.
 *
 * @see com.sun.xml.ws.handler.PortInfoImpl
 *
 * @author WS Development Team
 */
public class HandlerResolverImpl implements HandlerResolver {
    private HandlerChainsModel handlerModel;
    private Map<PortInfo, List<Handler>> chainMap;
    private WSServiceDelegate delegate;
    private static final Logger logger = Logger.getLogger(
        com.sun.xml.ws.util.Constants.LoggingDomain + ".handler");

    public HandlerResolverImpl(WSServiceDelegate delegate) {
        this.delegate = delegate;
        handlerModel = HandlerAnnotationProcessor.buildHandlerChainsModel(delegate.getServiceClass());
        chainMap = new HashMap<PortInfo, List<Handler>>();
    }

    /**
     * API method to return the correct handler chain for a given
     * PortInfo class.
     *
     * @param info A PortInfo object.
     * @return A list of handler objects. If there is no handler chain
     * found, it will return an empty list rather than null.
     */
    public List<Handler> getHandlerChain(PortInfo info) {
        //Check in cache first
        List<Handler> chain = chainMap.get(info);

        if(chain != null)
            return chain;
        if(handlerModel != null) {
            HandlerAnnotationInfo chainInfo = handlerModel.getHandlersForPortInfo(info);
            if(chainInfo != null) {
                chain = chainInfo.getHandlers();
                delegate.setRoles(info.getPortName(),chainInfo.getRoles());
            }
        }
        if (chain == null) {
            if (logger.isLoggable(Level.FINE)) {
                logGetChain(info);
            }
            chain = new ArrayList<Handler>();
        }
        // Put it in cache
        chainMap.put(info,chain);
        return chain;
    }

    /**
     * Sets the handler chain for a given PortInfo object into
     * the map.
     *
     * @param info The PortInfo that represents the port.
     * @param chain The list of handlers.
     */
    public void setHandlerChain(PortInfo info, List<Handler> chain) {
        if (logger.isLoggable(Level.FINER)) {
            logSetChain(info, chain);
        }
        chainMap.put(info, chain);
    }

    // logged at finer level
    private void logSetChain(PortInfo info, List<Handler> chain) {
        logger.finer("Setting chain of length " + chain.size() +
            " for port info");
        logPortInfo(info, Level.FINER);
    }

    // logged at fine level
    private void logGetChain(PortInfo info) {
        logger.fine("No handler chain found for port info:");
        logPortInfo(info, Level.FINE);
        logger.fine("Existing handler chains:");
        if (chainMap.isEmpty()) {
            logger.fine("none");
        } else {
            for (PortInfo key : chainMap.keySet()) {
                logger.fine(chainMap.get(key).size() +
                    " handlers for port info ");
                logPortInfo(key, Level.FINE);
            }
        }
    }

    private void logPortInfo(PortInfo info, Level level) {
        logger.log(level, "binding: " + info.getBindingID() +
            "\nservice: " + info.getServiceName() +
            "\nport: " + info.getPortName());
    }
}