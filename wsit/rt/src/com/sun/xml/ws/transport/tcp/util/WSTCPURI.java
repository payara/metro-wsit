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

package com.sun.xml.ws.transport.tcp.util;

import com.sun.xml.ws.transport.tcp.client.WSConnectionManager;
import com.sun.xml.ws.transport.tcp.resources.MessagesMessages;
import com.sun.xml.ws.transport.tcp.servicechannel.ServiceChannelException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * @author Alexey Stashok
 */
public final class WSTCPURI implements com.sun.xml.ws.transport.tcp.connectioncache.spi.transport.ContactInfo<ConnectionSession> {
    public String host;
    public int port;
    public String path;

    private String uri2string;
    private Map<String, String> params;
    
    /**
     * This constructor should be used just by JAXB runtime
     */
    public WSTCPURI() {}
    
    private WSTCPURI(String host, int port, String path, Map<String, String> params, String uri2string) {
        this.host = host;
        this.port = port;
        this.path = path;
        this.params = params;
        this.uri2string = uri2string;
    }
    
    public String getParameter(final String name) {
        if (params != null) {
            return params.get(name);
        }
        
        return null;
    }
    
    public static WSTCPURI parse(final String uri) {
        try {
            return parse(new URI(uri));
        } catch (URISyntaxException ex) {
            return null;
        }
    }
    
    public static WSTCPURI parse(final URI uri) {
        final String path = uri.getPath();
        final String query = uri.getQuery();
        Map<String, String> params = null;
        
        if (query != null && query.length() > 0) {
            final String[] paramsStr = query.split(";");
            params = new HashMap<String, String>(paramsStr.length);
            for(String paramStr : paramsStr) {
                if (paramStr.length() > 0) {
                    final String[] paramAsgn = paramStr.split("=");
                    if (paramAsgn != null && paramAsgn.length == 2 && paramAsgn[0].length() > 0 && paramAsgn[1].length() > 0) {
                        params.put(paramAsgn[0], paramAsgn[1]);
                    }
                }
            }
        }
        
        return new WSTCPURI(uri.getHost(), uri.getPort(), path, params, uri.toASCIIString());
    }
    
    public String toString() {
        return uri2string;
    }

    public boolean equals(Object o) {
        if (o instanceof WSTCPURI) {
            WSTCPURI toCmp = (WSTCPURI) o;
            return port == toCmp.port && host.equals(toCmp.host);
        }
        
        return false;
    }
    
    public int hashCode() {
        return host.hashCode() + port;
    }

    public ConnectionSession createConnection() throws IOException {
        try {
            return WSConnectionManager.getInstance().createConnectionSession(this);
        } catch (VersionMismatchException e) {
            throw new IOException(e.getMessage());
        } catch (ServiceChannelException e) {
            throw new IOException(MessagesMessages.WSTCP_0024_SERVICE_CHANNEL_EXCEPTION(e.getFaultInfo().getId(), e.getMessage()));
        }
    }
    
    /**
     * Class is used to translate WSTCPURI to String and vice versa
     * This is used in JAXB serialization/deserialization
     */
    public static final class WSTCPURI2StringJAXBAdapter extends XmlAdapter<String, WSTCPURI> {
        public String marshal(final WSTCPURI tcpURI) throws Exception {
            return tcpURI.toString();
        }

        public WSTCPURI unmarshal(final String uri) throws Exception {
            return WSTCPURI.parse(uri);
        }
        
    }
}
