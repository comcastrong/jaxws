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

package com.sun.xml.ws.api.model.wsdl;

import com.sun.xml.ws.api.model.wsdl.WSDLExtensible;
import com.sun.xml.ws.api.model.wsdl.WSDLOperation;

import javax.xml.namespace.QName;

/**
 * Abstraction of wsdl:portType.
 *
 * @author Vivek Pandey
 */
public interface WSDLPortType extends WSDLObject, WSDLExtensible {
    /**
     * Gets the name of the wsdl:portType@name attribute value as local name and wsdl:definitions@targetNamespace
     * as the namespace uri.
     */
    public QName getName();

    /**
     * Gets the {@link WSDLOperation} for a given operation name
     *
     * @param operationName non-null operationName
     * @return null if a {@link WSDLOperation} is not found
     */
    public WSDLOperation get(String operationName);
}
