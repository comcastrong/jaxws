/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.xml.ws.api.ha;

import org.glassfish.ha.store.api.BackingStore;
import com.sun.xml.ws.api.message.Packet;

/**
 * ReplicaInfo helps in storing related data to the same {@link BackingStore}.
 * This would help a loadbalancer to put the request(in case of a fail-over)
 * on a replica instance that has all the related data. Even if there is no
 * loadbalancer, a backing store could locate the information by directly
 * going to the correct replica instance.
 *
 * <p>
 * To achieve this functionality, it carries two pieces of information:
 * <ol>
 * <li>key - Related {@link BackingStore} keys can use this info for their
 * HashableKey impl. First store creates this object, and subsequent related
 * stores use the same key.
 * <li>replicaInstance - where the related info is replicated
 * </ol>
 *
 * <p>
 * This can be accessed from {@link Packet} using {@link Packet#REPLICA_INFO}
 * property by the runtime. This object is created typically
 * <ul>
 * <li> When a store happens for the first time
 * <li> A subsequent inbound transport creates from cookies
 * <li> A fail-over request stores the data to a different replica
 * </ul>
 *
 * @author Jitendra Kotamraju
 * @since JAX-WS RI 2.2.2
 */
public class ReplicaInfo {
    private final String replicaInstance;
    private final String key;

    public ReplicaInfo(String key, String replicaInstance) {
        this.key = key;
        this.replicaInstance = replicaInstance;
    }

    public String getReplicaInstance() {
        return replicaInstance;
    }

    public String getKey() {
        return key;
    }
}