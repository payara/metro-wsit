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

package com.sun.xml.ws.transport.tcp.server.glassfish;

import com.sun.enterprise.webservice.JAXWSAdapterRegistry;
import com.sun.enterprise.webservice.EjbRuntimeEndpointInfo;
import com.sun.enterprise.webservice.WebServiceEjbEndpointRegistry;
import com.sun.istack.NotNull;
import com.sun.xml.ws.api.server.Adapter;
import com.sun.xml.ws.transport.tcp.resources.MessagesMessages;
import com.sun.xml.ws.transport.tcp.util.WSTCPURI;
import com.sun.xml.ws.transport.tcp.server.TCPAdapter;
import com.sun.xml.ws.transport.tcp.server.WSTCPAdapterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Alexey Stashok
 */
public final class WSTCPAdapterRegistryImpl implements WSTCPAdapterRegistry {
    private static final Logger logger = Logger.getLogger(
            com.sun.xml.ws.transport.tcp.util.TCPConstants.LoggingDomain + ".server");
    
    /**
     * Registry holds correspondents between service name and adapter
     */
    final Map<String, TCPAdapter> registry = new ConcurrentHashMap<String, TCPAdapter>();
    private static final WSTCPAdapterRegistryImpl instance = new WSTCPAdapterRegistryImpl();
    
    private WSTCPAdapterRegistryImpl() {}
    
    public static @NotNull WSTCPAdapterRegistryImpl getInstance() {
        return instance;
    }
    
    public TCPAdapter getTarget(@NotNull final WSTCPURI requestURI) {
        // path should have format like "/context-root/url-pattern", where context-root and url-pattern could be /xxxx/yyyy/zzzz
        
        WSEndpointDescriptor wsEndpointDescriptor = null;
        String contextRoot = "/";
        String urlPattern = "/";
        // check if URI path is not empty
        if (requestURI.path.length() > 0 && !requestURI.path.equals("/")) {
            
            // Try to check for most common situation "/context-root/url-pattern"
            int nextSlashIndex = requestURI.path.indexOf('/', 1);
            if (nextSlashIndex != -1) {
                contextRoot = requestURI.path.substring(0, nextSlashIndex);
                urlPattern = requestURI.path.substring(nextSlashIndex, requestURI.path.length());
                wsEndpointDescriptor = AppServWSRegistry.getInstance().get(contextRoot, urlPattern);
            }
            
            if (wsEndpointDescriptor == null) {
                // Try to combine different context-root and url-pattern from given path
                nextSlashIndex = -1;
                do {
                    nextSlashIndex = requestURI.path.indexOf('/', nextSlashIndex + 1);
                    int delim = nextSlashIndex != -1 ? nextSlashIndex : requestURI.path.length();
                    contextRoot = delim > 0 ? requestURI.path.substring(0, delim) : "/";
                    urlPattern = delim < requestURI.path.length() ? requestURI.path.substring(delim, requestURI.path.length()) : "/";
                    wsEndpointDescriptor = AppServWSRegistry.getInstance().get(contextRoot, urlPattern);
                } while (nextSlashIndex != -1 && wsEndpointDescriptor == null);
            }
        } else {
            wsEndpointDescriptor = AppServWSRegistry.getInstance().get(contextRoot, urlPattern);
        }
        
        if (wsEndpointDescriptor != null) {
            TCPAdapter adapter = registry.get(requestURI.path);
            if (adapter == null) {
                try {
                    adapter = createWSAdapter(wsEndpointDescriptor);
                    registry.put(requestURI.path, adapter);
                    logger.log(Level.FINE, "WSTCPAdapterRegistryImpl. Register adapter. Path: {0}", requestURI.path);
                } catch (Exception e) {
                    // This common exception is thrown from ejbEndPtInfo.prepareInvocation(true)
                    logger.log(Level.SEVERE, "WSTCPAdapterRegistryImpl. " +
                            MessagesMessages.WSTCP_0008_ERROR_TCP_ADAPTER_CREATE(
                            wsEndpointDescriptor.getWSServiceName()), e);
                }
            }
            return adapter;
        }
        
        return null;
    }
    
    
    public void deleteTargetFor(@NotNull final String path) {
        logger.log(Level.FINE, "WSTCPAdapterRegistryImpl. DeRegister adapter for {0}", path);
        registry.remove(path);
    }
    
    private TCPAdapter createWSAdapter(@NotNull final WSEndpointDescriptor wsEndpointDescriptor) throws Exception {
        Adapter adapter;
        if (wsEndpointDescriptor.isEJB()) {
            final EjbRuntimeEndpointInfo ejbEndPtInfo = (EjbRuntimeEndpointInfo) WebServiceEjbEndpointRegistry.getRegistry().
                    getEjbWebServiceEndpoint(wsEndpointDescriptor.getURI(), "POST", null);
            adapter = (Adapter) ejbEndPtInfo.prepareInvocation(true);
        } else {
            final String uri = wsEndpointDescriptor.getURI();
            adapter = JAXWSAdapterRegistry.getInstance().getAdapter(wsEndpointDescriptor.getContextRoot(), uri, uri);
        }
        
//@TODO implement checkAdapterSupportsTCP
//        checkAdapterSupportsTCP(adapter);
        final TCPAdapter tcpAdapter = new TCP109Adapter(wsEndpointDescriptor.getWSServiceName().toString(),
                wsEndpointDescriptor.getContextRoot(),
                wsEndpointDescriptor.getUrlPattern(),
                adapter.getEndpoint(),
                new ServletFakeArtifactSet(wsEndpointDescriptor.getRequestURL(), wsEndpointDescriptor.getUrlPattern()),
                wsEndpointDescriptor.isEJB());
        
        return tcpAdapter;
    }
}
