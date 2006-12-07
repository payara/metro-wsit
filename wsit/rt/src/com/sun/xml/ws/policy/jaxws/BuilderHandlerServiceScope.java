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

package com.sun.xml.ws.policy.jaxws;

import com.sun.xml.ws.policy.PolicyException;
import com.sun.xml.ws.policy.PolicyMap;
import com.sun.xml.ws.policy.PolicyMapExtender;
import com.sun.xml.ws.policy.PolicyMapKey;
import com.sun.xml.ws.policy.PolicySubject;
import com.sun.xml.ws.policy.jaxws.privateutil.LocalizationMessages;
import com.sun.xml.ws.policy.sourcemodel.PolicySourceModel;
import java.util.Collection;
import java.util.Map;
import javax.xml.namespace.QName;

/**
 *
 * @author japod
 */
class BuilderHandlerServiceScope extends BuilderHandler{
    
    QName service;
    
    /**
     * Creates a new instance of BuilderHandlerServiceScope
     */
    BuilderHandlerServiceScope(
            Collection<String> policyURIs, Map<String,PolicySourceModel> policyStore, Object policySubject, QName service) {
        
        super(policyURIs, policyStore, policySubject);
        this.service = service;
    }
    
    void populate(PolicyMapExtender policyMapExtender) throws PolicyException{
        if (null == policyMapExtender) {
            throw new PolicyException(LocalizationMessages.POLICY_MAP_EXTENDER_CAN_NOT_BE_NULL());
        }
        
        PolicyMapKey mapKey = PolicyMap.createWsdlServiceScopeKey(service);
        for (PolicySubject subject:getPolicySubjects()) {
            policyMapExtender.putServiceSubject(mapKey, subject);
        }
    }
    
    public String toString() {
        return service.toString();
    }
    
}
