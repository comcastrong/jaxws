/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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

package bugs.jaxws881.client;

/**
 * TODO: Write some description here ...
 *
 * @author Miroslav Kos (miroslav.kos at oracle.com)
 */

import com.sun.istack.NotNull;
import com.sun.xml.bind.api.JAXBRIContext;
import com.sun.xml.bind.api.TypeReference;
import com.sun.xml.ws.api.model.SEIModel;
import com.sun.xml.ws.developer.JAXBContextFactory;

import javax.xml.bind.JAXBException;
import java.util.List;

/**

 This implementation should only be used on one service whose definition does
 not change. It caches the JAXBRIContext
 created the first time it is called and always returns that.
 */
public class CachingJAXBContextFactory implements JAXBContextFactory {

    private JAXBRIContext context = null;

    @Override
    public JAXBRIContext createJAXBContext(@NotNull SEIModel sei, @NotNull List<Class> classesToBind, @NotNull List<TypeReference> typeReferences) throws JAXBException {
        if (this.context == null) {
            this.context = JAXBContextFactory.DEFAULT.createJAXBContext(sei, classesToBind, typeReferences);
        }

        return this.context;
    }
}

