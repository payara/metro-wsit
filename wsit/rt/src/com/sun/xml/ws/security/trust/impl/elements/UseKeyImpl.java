/*
 * $Id: UseKeyImpl.java,v 1.7.6.1 2007-05-29 23:57:00 ofung Exp $
 */

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

package com.sun.xml.ws.security.trust.impl.elements;

import java.net.URI;

import com.sun.xml.ws.security.trust.elements.str.SecurityTokenReference;
import com.sun.xml.ws.security.trust.impl.elements.str.SecurityTokenReferenceImpl;
import com.sun.xml.ws.security.secext10.SecurityTokenReferenceType;
import com.sun.xml.ws.security.Token;
import com.sun.xml.ws.security.trust.WSTrustConstants;
import com.sun.xml.ws.security.trust.elements.UseKey;
import com.sun.xml.ws.security.trust.impl.bindings.UseKeyType;
import java.net.URISyntaxException;
import javax.xml.bind.JAXBElement;

import com.sun.istack.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.xml.ws.security.trust.logging.LogDomainConstants;

import com.sun.xml.ws.security.trust.logging.LogStringsMessages;


/**
 * @author Manveen Kaur
 */
public class UseKeyImpl extends UseKeyType implements UseKey {
    
    private static final Logger log =
            Logger.getLogger(
            LogDomainConstants.TRUST_IMPL_DOMAIN,
            LogDomainConstants.TRUST_IMPL_DOMAIN_BUNDLE);

    private String targetType = null;
    
    private SecurityTokenReference str = null;
    private Token token = null;
    
    public UseKeyImpl(SecurityTokenReference str) {
        setSecurityTokenReference(str);
        setTargetType(WSTrustConstants.STR_TYPE);
    }
    
    public UseKeyImpl (@NotNull final UseKeyType ukType){
        final JAXBElement obj = (JAXBElement)ukType.getAny();
        final String local = obj.getName().getLocalPart();
        if ("SecurityTokenReference".equals(local)) {
            final SecurityTokenReference str = 
                        new SecurityTokenReferenceImpl((SecurityTokenReferenceType)obj.getValue());
            setSecurityTokenReference(str);
            setTargetType(WSTrustConstants.STR_TYPE);
        } else {
            //ToDo
        } 
    }
    
    public String getTargetType() {
        return targetType;
    }
    
    public final void setTargetType(final String ttype) {
        targetType = ttype;
    }
    
    public final void setSecurityTokenReference(@NotNull final SecurityTokenReference ref) {
        if (ref != null) {
            str = ref;
            final JAXBElement<SecurityTokenReferenceType> strElement=
                    (new com.sun.xml.ws.security.secext10.ObjectFactory()).createSecurityTokenReference((SecurityTokenReferenceType)ref);
            setAny(strElement);
        }
        setTargetType(WSTrustConstants.STR_TYPE);
        token = null;
    }
    
    public SecurityTokenReference getSecurityTokenReference() {
        return str;
    }
    
    public void setToken(@NotNull final Token token) {
        if (token != null) {
            this.token = token;
            setAny(token);
        }
        setTargetType(WSTrustConstants.TOKEN_TYPE);
        str = null;
    }
    
    public Token getToken() {
        return token;
    }
    
    public void setSignatureID(@NotNull final URI sigID) {
        setSig(sigID.toString());
    }
    
    public URI getSignatureID() {
        return URI.create(getSig());
    }
    
}
