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

package com.sun.xml.ws.sandbox.fault;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.namespace.QName;

/**
 * <pre>
 *  &lt;env:Code>
 *       &lt;env:Value>env:Sender&lt;/env:Value>
 *       &lt;env:Subcode>
 *           &lt;env:Value>m:MessageTimeout1&lt;/env:Value>
 *           &lt;env:Subcode>
 *               &lt;env:Value>m:MessageTimeout2&lt;/env:Value>
 *           &lt;/env:Subcode>
 *       &lt;/env:Subcode>
 *  &lt;/env:Code>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CodeType", namespace = "http://www.w3.org/2003/05/soap-envelope", propOrder = {
    "Value",
    "Subcode"
})
class CodeType {
    @XmlTransient
    private static final String ns="http://www.w3.org/2003/05/soap-envelope";

    /**
     * mandatory, minOccurs=1
     */
    @XmlElement(namespace = ns)
    private QName Value;

    /**
     * optional, minOcccurs=0, maxOccurs="1"
     */
    @XmlElement(namespace = ns)
    private SubcodeType Subcode;

    CodeType(QName value) {
        Value = value;
    }

    CodeType() {
    }

    QName getValue(){
        return Value;
    }

    SubcodeType getSubcode(){
        return Subcode;
    }

    void setSubcode(SubcodeType subcode) {
        Subcode = subcode;
    }
}
