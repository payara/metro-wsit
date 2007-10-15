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

package com.sun.xml.wss.jaxws.impl;

import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Messages;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.security.impl.policy.Constants;

import com.sun.xml.ws.api.message.stream.InputStreamMessage;
import com.sun.xml.ws.api.message.stream.XMLStreamReaderMessage;
import com.sun.xml.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.ws.api.model.wsdl.WSDLFault;
import com.sun.xml.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.ws.api.pipe.Pipe;
import com.sun.xml.ws.api.pipe.PipeCloner;
import com.sun.xml.ws.policy.Policy;
import com.sun.xml.ws.policy.PolicyAssertion;
import com.sun.xml.ws.policy.PolicyException;
import com.sun.xml.ws.assembler.ServerPipeConfiguration;
import com.sun.xml.ws.runtime.util.Session;
import com.sun.xml.ws.runtime.util.SessionManager;
import com.sun.xml.ws.security.impl.policyconv.SecurityPolicyHolder;
import com.sun.xml.ws.security.opt.impl.JAXBFilterProcessingContext;
import com.sun.xml.ws.security.policy.SecureConversationToken;
import com.sun.xml.ws.security.trust.WSTrustConstants;

import com.sun.xml.wss.impl.MessageConstants;
import com.sun.xml.wss.impl.ProcessingContextImpl;
import com.sun.xml.wss.impl.XWSSecurityRuntimeException;
import com.sun.xml.wss.impl.policy.mls.MessagePolicy;
import com.sun.xml.wss.ProcessingContext;
import com.sun.xml.wss.XWSSecurityException;
import com.sun.xml.wss.impl.WssSoapFaultException;

import com.sun.xml.ws.security.IssuedTokenContext;
import com.sun.xml.ws.security.SecurityContextToken;
import com.sun.xml.ws.security.impl.IssuedTokenContextImpl;
import com.sun.xml.ws.security.secconv.WSSecureConversationException;
import com.sun.xml.wss.impl.misc.DefaultSecurityEnvironmentImpl;

import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConstants;

import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.WebServiceException;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;

import java.util.Properties;
import java.util.Iterator;
import java.util.Set;

import java.net.URI;
import com.sun.xml.ws.security.policy.Token;

import com.sun.xml.ws.security.secconv.WSSCContract;
import com.sun.xml.ws.security.secconv.WSSCConstants;
import com.sun.xml.ws.security.secconv.WSSCElementFactory;
import com.sun.xml.ws.security.secconv.WSSCFactory;
import com.sun.xml.ws.security.trust.elements.RequestSecurityToken;
import com.sun.xml.ws.security.trust.elements.RequestSecurityTokenResponse;
import com.sun.xml.wss.SubjectAccessor;
import com.sun.xml.wss.RealmAuthenticationAdapter;
import com.sun.xml.wss.impl.NewSecurityRecipient;

import com.sun.xml.wss.impl.misc.DefaultCallbackHandler;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.sun.xml.wss.jaxws.impl.Constants.SC_ASSERTION;
import static com.sun.xml.wss.jaxws.impl.Constants.OPERATION_SCOPE;
import static com.sun.xml.wss.jaxws.impl.Constants.EMPTY_LIST;
import static com.sun.xml.wss.jaxws.impl.Constants.SUN_WSS_SECURITY_SERVER_POLICY_NS;
import static com.sun.xml.wss.jaxws.impl.Constants.SUN_WSS_SECURITY_CLIENT_POLICY_NS;
import java.security.AccessController;
import java.security.PrivilegedAction;


import java.util.logging.Level;
import com.sun.xml.wss.jaxws.impl.logging.LogStringsMessages;

/**
 * @author K.Venugopal@sun.com
 * @author Vbkumar.Jayanti@Sun.COM
 */
public class SecurityServerPipe extends SecurityPipeBase {
    
    private SessionManager sessionManager =
            SessionManager.getSessionManager();
    //private WSDLBoundOperation cachedOperation = null;
    private Set trustConfig = null;
    private CallbackHandler handler = null;
    
    // Creates a new instance of SecurityServerPipe
    public SecurityServerPipe(ServerPipeConfiguration config,Pipe nextPipe) {
        super(config,nextPipe);
        
        try {
            Iterator it = inMessagePolicyMap.values().iterator();
            SecurityPolicyHolder holder = (SecurityPolicyHolder)it.next();
            Set configAssertions = holder.getConfigAssertions(SUN_WSS_SECURITY_SERVER_POLICY_NS);
            trustConfig = holder.getConfigAssertions(Constants.SUN_TRUST_SERVER_SECURITY_POLICY_NS);
            Properties props = new Properties();
            handler = configureServerHandler(configAssertions, props);
            secEnv = new DefaultSecurityEnvironmentImpl(handler, props);
        } catch (Exception e) {            
            log.log(Level.SEVERE, 
                    LogStringsMessages.WSSPIPE_0028_ERROR_CREATING_NEW_INSTANCE_SEC_SERVER_PIPE(), e);            
            throw new RuntimeException(
                    LogStringsMessages.WSSPIPE_0028_ERROR_CREATING_NEW_INSTANCE_SEC_SERVER_PIPE(), e);            
        }
    }
    
    // copy constructor
    protected SecurityServerPipe(SecurityServerPipe that) {
        super(that);
        sessionManager = that.sessionManager;
        trustConfig = that.trustConfig;
        handler = that.handler;
    }
    
    
    //Note: There is an Assumption that the STS is distinct from the WebService in case of
    // WS-Trust and the STS and WebService are the same entity for SecureConversation
    public Packet process(Packet packet) {
        
        if (!optimized) {
            cacheMessage(packet);
        }
        
        Message msg = packet.getMessage();
        
        boolean isSCIssueMessage = false;
        boolean isSCCancelMessage = false;
        boolean isTrustMessage = false;
        String msgId = null;
        String action = null;
        
        boolean thereWasAFault = false;
        
        //Do Security Processing for Incoming Message
        //---------------INBOUND SECURITY VERIFICATION----------
        ProcessingContext ctx = initializeInboundProcessingContext(packet/*, isSCIssueMessage, isTrustMessage*/);
        if(hasKerberosTokenPolicy()){
            ((ProcessingContextImpl)ctx).setKerberosContextMap(kerberosTokenContextMap);
        }
        ctx.setExtraneousProperty(ctx.OPERATION_RESOLVER, new PolicyResolverImpl(inMessagePolicyMap,inProtocolPM,cachedOperation,pipeConfig,addVer,false));
        try{
            if(!optimized) {
                SOAPMessage soapMessage = msg.readAsSOAPMessage();
                soapMessage = verifyInboundMessage(soapMessage, ctx);
                msg = Messages.create(soapMessage);
            }else{
                msg = verifyInboundMessage(msg, ctx);
            }
        } catch (WssSoapFaultException ex) {
            thereWasAFault = true;            
            msg = Messages.create(ex, pipeConfig.getBinding().getSOAPVersion());
        } catch (XWSSecurityException xwse) {
            thereWasAFault = true;            
            msg = Messages.create(xwse, pipeConfig.getBinding().getSOAPVersion());
          
        } catch (XWSSecurityRuntimeException xwse) {
            thereWasAFault = true;            
            msg = Messages.create(xwse, pipeConfig.getBinding().getSOAPVersion());
            
        } catch (WebServiceException xwse) {
            thereWasAFault = true;            
            msg = Messages.create(xwse, pipeConfig.getBinding().getSOAPVersion());
          
        } catch(SOAPException se){
            // internal error
            // Log here because this catch is an internal error not logger by the callee
            log.log(Level.SEVERE, 
                    LogStringsMessages.WSSPIPE_0025_ERROR_VERIFY_INBOUND_MSG(), se);
            thereWasAFault = true;            
            msg = Messages.create(se, pipeConfig.getBinding().getSOAPVersion());
            //throw new WebServiceException(LogStringsMessages.WSSPIPE_0025_ERROR_VERIFY_INBOUND_MSG(), se);
        }
        
        Packet retPacket = null;
         if (thereWasAFault) {
            //retPacket = packet;
            if (this.isAddressingEnabled()) {
                if (optimized) {
                    packet.setMessage(((JAXBFilterProcessingContext)ctx).getPVMessage());
                }
                retPacket = packet.createServerResponse(
                        msg, this.addVer, this.soapVersion, this.addVer.getDefaultFaultAction());
            } else {
                packet.setMessage(msg);
                retPacket = packet;
            }
        }
        
        packet.setMessage(msg);
        
        if (isAddressingEnabled()) {
            action = getAction(packet);
            if (WSSCConstants.REQUEST_SECURITY_CONTEXT_TOKEN_ACTION.equals(action)) {
                isSCIssueMessage = true;
            } else if (WSSCConstants.CANCEL_SECURITY_CONTEXT_TOKEN_ACTION.equals(action)) {
                isSCCancelMessage = true;
            } else if (WSTrustConstants.REQUEST_SECURITY_TOKEN_ISSUE_ACTION.equals(action)) {
                isTrustMessage = true;
                packet.getMessage().getHeaders().getTo(addVer, pipeConfig.getBinding().getSOAPVersion());
                
                if(trustConfig != null){
                    packet.invocationProperties.put(Constants.SUN_TRUST_SERVER_SECURITY_POLICY_NS,trustConfig.iterator());

                }
                
                //set the callbackhandler
                packet.invocationProperties.put(WSTrustConstants.SECURITY_ENVIRONMENT, secEnv);

            }
            
            if (isSCIssueMessage){
                List<PolicyAssertion> policies = getInBoundSCP(packet.getMessage());
                if(!policies.isEmpty()) {
                    packet.invocationProperties.put(SC_ASSERTION, (PolicyAssertion)policies.get(0));
                }
            }
        }
        
        if(!isSCIssueMessage ){
            cachedOperation = msg.getOperation(pipeConfig.getWSDLModel());
            if(cachedOperation == null){
                if(addVer != null)
                    cachedOperation = getWSDLOpFromAction(packet, true);
            }
        }
        
        
        
        if (!thereWasAFault) {
            
            if (isSCIssueMessage || isSCCancelMessage) {
                //-------put application message on hold and invoke SC contract--------
                
                retPacket = invokeSecureConversationContract(
                        packet, ctx, isSCIssueMessage, action);
                
            } else {
                //--------INVOKE NEXT PIPE------------
                // Put the addressing headers as unread
                // packet.invocationProperties.put(JAXWSAConstants.SERVER_ADDRESSING_PROPERTIES_INBOUND, null);
                updateSCBootstrapCredentials(packet, ctx);
                if (nextPipe != null) {
                    retPacket = nextPipe.process(packet);
                    
                    // Add addrsssing headers to trust message
                    if (isTrustMessage){
                        retPacket = addAddressingHeaders(packet, retPacket.getMessage(), WSTrustConstants.REQUEST_SECURITY_TOKEN_RESPONSE_ISSUE_ACTION);
                    }
                }else {
                    retPacket = packet;
                }
                
             
            }
        }
        
        if(retPacket.getMessage() == null){
            return retPacket;
        }
        /* TODO:this piece of code present since payload should be read once*/
        if (!optimized) {
            try{
                SOAPMessage sm = retPacket.getMessage().readAsSOAPMessage();
                Message newMsg = Messages.create(sm);
                retPacket.setMessage(newMsg);
            }catch(SOAPException ex){
                log.log(Level.SEVERE, 
                        LogStringsMessages.WSSPIPE_0005_PROBLEM_PROC_SOAP_MESSAGE(), ex);                
                throw new WebServiceException(
                        LogStringsMessages.WSSPIPE_0005_PROBLEM_PROC_SOAP_MESSAGE(), ex);                
            }
        }
        
        //---------------OUTBOUND SECURITY PROCESSING----------
        ctx = initializeOutgoingProcessingContext(retPacket, isSCIssueMessage, isTrustMessage /*, thereWasAFault*/);
        if(hasKerberosTokenPolicy()){
            ((ProcessingContextImpl)ctx).setKerberosContextMap(kerberosTokenContextMap);
        }
        try{
            msg = retPacket.getMessage();
            if (ctx.getSecurityPolicy() != null && ((MessagePolicy)ctx.getSecurityPolicy()).size() >0) {
                if(!optimized || msg.isFault()) {
                    SOAPMessage soapMessage = msg.readAsSOAPMessage();
                    soapMessage = secureOutboundMessage(soapMessage, ctx);
                    msg = Messages.create(soapMessage);
                }else{
                    msg = secureOutboundMessage(msg, ctx);
                }
            }
        } catch (WssSoapFaultException ex) {
            msg = Messages.create(getSOAPFault(ex));
        } catch(SOAPException se) {
            // internal error
            log.log(Level.SEVERE, 
                    LogStringsMessages.WSSPIPE_0024_ERROR_SECURING_OUTBOUND_MSG(), se);                        
            throw new WebServiceException(
                    LogStringsMessages.WSSPIPE_0024_ERROR_SECURING_OUTBOUND_MSG(), se);
        } finally{
            if (isSCCancel(retPacket)){
                removeContext(packet);
            }
        }
        resetCachedOperation();
        retPacket.setMessage(msg);
        return retPacket;
        
    }
    
    private void removeContext(final Packet packet) {
        SecurityContextToken sct = (SecurityContextToken)packet.invocationProperties.get(MessageConstants.INCOMING_SCT);
        if (sct != null){
            String strId = sct.getIdentifier().toString();
            if(strId!=null){
                issuedTokenContextMap.remove(strId);
                sessionManager.terminateSession(strId);
            }
        }
    }
    
    public void preDestroy() {
        if (nextPipe != null) {
            nextPipe.preDestroy();
        }
        issuedTokenContextMap.clear();
        kerberosTokenContextMap.clear();
    }
    
    public Pipe copy(PipeCloner cloner) {
        Pipe clonedNextPipe = null;
        if (nextPipe != null) {
            clonedNextPipe = cloner.copy(nextPipe);
        }
        Pipe copied = new SecurityServerPipe(this);
        ((SecurityServerPipe)copied).setNextPipe(clonedNextPipe);
        cloner.add(this, copied);
        return copied;
    }
    
    public Packet processMessage(XMLStreamReaderMessage msg) {
        //TODO:Optimized security
        throw new UnsupportedOperationException();
    }
    
    public InputStreamMessage processInputStream(XMLStreamReaderMessage msg) {
        //TODO:Optimized security
        throw new UnsupportedOperationException();
    }
    
    public InputStreamMessage processInputStream(Message msg) {
        //TODO:Optimized security
        throw new UnsupportedOperationException();
    }    
    
    protected ProcessingContext initializeOutgoingProcessingContext(
            Packet packet, boolean isSCMessage, boolean isTrustMessage /*, boolean thereWasAFault*/) {
        ProcessingContext ctx = initializeOutgoingProcessingContext(packet, isSCMessage/*, thereWasAFault*/);
        return ctx;
    }
    
    protected ProcessingContext initializeOutgoingProcessingContext(
            Packet packet, boolean isSCMessage /*, boolean thereWasAFault*/) {
        
        ProcessingContextImpl ctx = null;
        if(optimized){
            ctx = new JAXBFilterProcessingContext(packet.invocationProperties);
            ((JAXBFilterProcessingContext)ctx).setAddressingVersion(addVer);
            ((JAXBFilterProcessingContext)ctx).setSOAPVersion(soapVersion);
            ((JAXBFilterProcessingContext)ctx).setBSP(bsp10);
        }else{
            ctx = new ProcessingContextImpl( packet.invocationProperties);
        }
        
        try {
            MessagePolicy policy = null;
            if (packet.getMessage().isFault()) {
                policy =  getOutgoingFaultPolicy(packet);
                if(optimized){
                    ctx = new ProcessingContextImpl( packet.invocationProperties);
                }
            } else if (isRMMessage(packet)) {
                SecurityPolicyHolder holder = outProtocolPM.get("RM");
                policy = holder.getMessagePolicy();
            } else if(isSCCancel(packet)){
                SecurityPolicyHolder holder = outProtocolPM.get("SC");
                policy = holder.getMessagePolicy();
            }else {
                policy = getOutgoingXWSSecurityPolicy(packet, isSCMessage);
            }
            
            if (debug && policy != null) {
                policy.dumpMessages(true);
            }
            //this might mislead if there is a bug in code above
            //but we are doing this check for cases such as no-fault-security-policy
            if (policy != null) {
                ctx.setSecurityPolicy(policy);
            }
            // set the policy, issued-token-map, and extraneous properties
            //ctx.setIssuedTokenContextMap(issuedTokenContextMap);
            ctx.setAlgorithmSuite(getAlgoSuite(getBindingAlgorithmSuite(packet)));
            ctx.setSecurityEnvironment(secEnv);
            ctx.isInboundMessage(false);
        } catch (XWSSecurityException e) {
            log.log(
                    Level.SEVERE, LogStringsMessages.WSSPIPE_0006_PROBLEM_INIT_OUT_PROC_CONTEXT(), e);
            throw new RuntimeException(
                    LogStringsMessages.WSSPIPE_0006_PROBLEM_INIT_OUT_PROC_CONTEXT(), e);
        }
        return ctx;
    }
    
    protected MessagePolicy getOutgoingXWSSecurityPolicy(
            Packet packet, boolean isSCMessage) {
        if (isSCMessage) {
            Token scToken = (Token)packet.invocationProperties.get(SC_ASSERTION);
            return getOutgoingXWSBootstrapPolicy(scToken);
        }
       
        MessagePolicy mp = null;
     
        if (outMessagePolicyMap == null) {
            //empty message policy
            return new MessagePolicy();
        }
        
        if(isTrustMessage(packet)){
            cachedOperation = getWSDLOpFromAction(packet,false);
        }
        
        SecurityPolicyHolder sph = (SecurityPolicyHolder) outMessagePolicyMap.get(cachedOperation);
        if(sph == null){
            return new MessagePolicy();
        }
        mp = sph.getMessagePolicy();
        return mp;
    }
    
    protected MessagePolicy getOutgoingFaultPolicy(Packet packet) {
        
        if(cachedOperation != null){
            WSDLOperation operation = cachedOperation.getOperation();
            try{
                SOAPBody body = packet.getMessage().readAsSOAPMessage().getSOAPBody();
                NodeList nodes = body.getElementsByTagName("detail");
                if(nodes.getLength() == 0){
                    nodes = body.getElementsByTagNameNS(SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE,"Detail");
                }
                if(nodes.getLength() >0){
                    Node node = nodes.item(0);
                    Node faultNode = node.getFirstChild();
                    if(faultNode == null){
                        return null;
                    }
                    String uri = faultNode.getNamespaceURI();
                    QName faultDetail = null;
                    if(uri != null && uri.length() >0){
                        faultDetail = new QName(faultNode.getNamespaceURI(),faultNode.getLocalName());
                    }else{
                        faultDetail = new QName(faultNode.getNodeName());
                    }
                    WSDLFault fault = operation.getFault(faultDetail);
                    SecurityPolicyHolder sph = outMessagePolicyMap.get(cachedOperation);
                    SecurityPolicyHolder faultPolicyHolder = sph.getFaultPolicy(fault);
                    MessagePolicy faultPolicy = (faultPolicyHolder == null) ? new MessagePolicy() : faultPolicyHolder.getMessagePolicy();
                    return faultPolicy;
                    
                }
            }catch(SOAPException sx){
                //sx.printStackTrace();
                //log error
            }
        }
        return null;
        
    }
    
    
    
    
    protected SOAPMessage verifyInboundMessage(SOAPMessage message, ProcessingContext ctx)
    throws WssSoapFaultException, XWSSecurityException {
        ctx.setSOAPMessage(message);
        NewSecurityRecipient.validateMessage(ctx);
        return ctx.getSOAPMessage();
    }
    
    // The packet has the Message with RST/SCT inside it
    // TODO: Need to inspect if it is really a Issue or a Cancel
    @SuppressWarnings("unchecked")
    private Packet invokeSecureConversationContract(
            Packet packet, ProcessingContext ctx, boolean isSCTIssue, String action) {
        
        IssuedTokenContext ictx = new IssuedTokenContextImpl();
        
        Message msg = packet.getMessage();
        Message retMsg = null;
        String retAction = null;
        
        try {
            // Set the requestor authenticated Subject in the IssuedTokenContext
            Subject subject = SubjectAccessor.getRequesterSubject(ctx);
            
            ictx.setRequestorSubject(subject);
            
            WSSCElementFactory eleFac = WSSCElementFactory.newInstance();
            JAXBElement rstEle = msg.readPayloadAsJAXB(jaxbContext.createUnmarshaller());
            RequestSecurityToken rst = eleFac.createRSTFrom(rstEle);
            URI requestType = rst.getRequestType();
            RequestSecurityTokenResponse rstr = null;
            WSSCContract scContract = WSSCFactory.newWSSCContract(null);
            if (requestType.toString().equals(WSTrustConstants.ISSUE_REQUEST)) {
                List<PolicyAssertion> policies = getOutBoundSCP(packet.getMessage());
                rstr =  scContract.issue(rst, ictx, (SecureConversationToken)policies.get(0));
                retAction = WSSCConstants.REQUEST_SECURITY_CONTEXT_TOKEN_RESPONSE_ACTION;
                SecurityContextToken sct = (SecurityContextToken)ictx.getSecurityToken();
                String sctId = sct.getIdentifier().toString();
                
                Session session = sessionManager.getSession(sctId);
                if (session == null) {
                    log.log(Level.SEVERE, LogStringsMessages.WSSPIPE_0029_ERROR_SESSION_CREATION());                   
                    throw new WSSecureConversationException(
                            LogStringsMessages.WSSPIPE_0029_ERROR_SESSION_CREATION());
                }
                
                // Put it here for RM to pick up
                packet.invocationProperties.put(
                        Session.SESSION_ID_KEY, sctId);
                
                packet.invocationProperties.put(
                        Session.SESSION_KEY, session.getUserData());
           
                //((ProcessingContextImpl)ctx).getIssuedTokenContextMap().put(sctId, ictx);                
                
            } else if (requestType.toString().equals(WSTrustConstants.CANCEL_REQUEST)) {
                retAction = WSSCConstants.CANCEL_SECURITY_CONTEXT_TOKEN_RESPONSE_ACTION;
                rstr =  scContract.cancel(rst, ictx);
            } else {
                log.log(Level.SEVERE, 
                        LogStringsMessages.WSSPIPE_0030_UNSUPPORTED_OPERATION_EXCEPTION(requestType));                
                throw new UnsupportedOperationException(
                        LogStringsMessages.WSSPIPE_0030_UNSUPPORTED_OPERATION_EXCEPTION(requestType)); 
            }
            
            // construct the complete message here containing the RSTR and the
            // correct Action headers if any and return the message.
            retMsg = Messages.create(jaxbContext.createMarshaller(), eleFac.toJAXBElement(rstr), soapVersion);
        } catch (com.sun.xml.wss.XWSSecurityException ex) {
            log.log(Level.SEVERE, LogStringsMessages.WSSPIPE_0031_ERROR_INVOKE_SC_CONTRACT(), ex);  
            throw new RuntimeException(LogStringsMessages.WSSPIPE_0031_ERROR_INVOKE_SC_CONTRACT(), ex);
        } catch (javax.xml.bind.JAXBException ex) {
            log.log(Level.SEVERE, LogStringsMessages.WSSPIPE_0001_PROBLEM_MAR_UNMAR(), ex);
            throw new RuntimeException(LogStringsMessages.WSSPIPE_0001_PROBLEM_MAR_UNMAR(), ex);
        } catch (WSSecureConversationException ex){
            log.log(Level.SEVERE, LogStringsMessages.WSSPIPE_0031_ERROR_INVOKE_SC_CONTRACT(), ex);
            throw new RuntimeException(LogStringsMessages.WSSPIPE_0031_ERROR_INVOKE_SC_CONTRACT(), ex);
        }
        
      
        Packet retPacket = addAddressingHeaders(packet, retMsg, retAction);
        if (isSCTIssue){
            List<PolicyAssertion> policies = getOutBoundSCP(packet.getMessage());
            
            if(!policies.isEmpty()) {
                retPacket.invocationProperties.put(SC_ASSERTION, (PolicyAssertion)policies.get(0));
            }
        }
        
        return retPacket;
    }
    
    public InputStreamMessage processInputStream(Packet packet) {
        //TODO:Optimized security
        throw new UnsupportedOperationException("Will be supported for optimized path");
    }
    
    /** private Packet addAddressingHeaders(Packet packet, String relatesTo, String action){
     * AddressingBuilder builder = AddressingBuilder.newInstance();
     * AddressingProperties ap = builder.newAddressingProperties();
     *
     * try{
     * // Action
     * ap.setAction(builder.newURI(new URI(action)));
     *
     * // RelatesTo
     * Relationship[] rs = new Relationship[]{builder.newRelationship(new URI(relatesTo))};
     * ap.setRelatesTo(rs);
     *
     * // To
     * ap.setTo(builder.newURI(new URI(builder.newAddressingConstants().getAnonymousURI())));
     *
     * } catch (URISyntaxException e) {
     * throw new RuntimeException("Exception when adding Addressing Headers");
     * }
     *
     * WsaRuntimeFactory fac = WsaRuntimeFactory.newInstance(ap.getNamespaceURI(), pipeConfig.getWSDLModel(), pipeConfig.getBinding());
     * fac.writeHeaders(packet, ap);
     * packet.invocationProperties
     * .put(JAXWSAConstants.SERVER_ADDRESSING_PROPERTIES_OUTBOUND, ap);
     *
     * return packet;
     * }*/
    
    protected SecurityPolicyHolder addOutgoingMP(WSDLBoundOperation operation,Policy policy)throws PolicyException{

        SecurityPolicyHolder sph = constructPolicyHolder(policy,true,true);
        inMessagePolicyMap.put(operation,sph);
        return sph;
    }
    
    protected SecurityPolicyHolder addIncomingMP(WSDLBoundOperation operation,Policy policy)throws PolicyException{

        SecurityPolicyHolder sph = constructPolicyHolder(policy,true,false);
        outMessagePolicyMap.put(operation,sph);
        return sph;
    }
    
    protected void addIncomingProtocolPolicy(Policy effectivePolicy,String protocol)throws PolicyException{
        outProtocolPM.put(protocol,constructPolicyHolder(effectivePolicy,true,false,true));
    }
    
    protected void addOutgoingProtocolPolicy(Policy effectivePolicy,String protocol)throws PolicyException{
        inProtocolPM.put(protocol,constructPolicyHolder(effectivePolicy,true,true,true));
    }
    
    protected void addIncomingFaultPolicy(Policy effectivePolicy,SecurityPolicyHolder sph,WSDLFault fault)throws PolicyException{
        SecurityPolicyHolder faultPH = constructPolicyHolder(effectivePolicy,true,false);
        sph.addFaultPolicy(fault,faultPH);
    }
    
    protected void addOutgoingFaultPolicy(Policy effectivePolicy,SecurityPolicyHolder sph,WSDLFault fault)throws PolicyException{
        SecurityPolicyHolder faultPH = constructPolicyHolder(effectivePolicy,true,true);
        sph.addFaultPolicy(fault,faultPH);
    }
    
    protected String getAction(WSDLOperation operation,boolean inComming){
        if(inComming){
            return operation.getInput().getAction();
        }else{
            return operation.getOutput().getAction();
        }
    }
    
    private Packet addAddressingHeaders(Packet packet, Message retMsg, String action){
        Packet retPacket = packet.createServerResponse(retMsg, addVer, soapVersion, action);
        
        retPacket.proxy = packet.proxy;
        retPacket.invocationProperties.putAll(packet.invocationProperties);
        
        return retPacket;
    }       
    
    private CallbackHandler configureServerHandler(Set configAssertions, Properties props) {
        //Properties props = new Properties();
        String ret = populateConfigProperties(configAssertions, props);
        try {
            if (ret != null) {
                Class handler = loadClass(ret);
                Object obj = handler.newInstance();
                if (!(obj instanceof CallbackHandler)) {
                    log.log(Level.SEVERE, 
                            LogStringsMessages.WSSPIPE_0033_INVALID_CALLBACK_HANDLER_CLASS(ret));
                    throw new RuntimeException(
                            LogStringsMessages.WSSPIPE_0033_INVALID_CALLBACK_HANDLER_CLASS(ret));                                        
                }
                return (CallbackHandler)obj;
            }
            // ServletContext context =
            //         ((ServerPipeConfiguration)pipeConfig).getEndpoint().getContainer().getSPI(ServletContext.class);
            RealmAuthenticationAdapter adapter = getRealmAuthenticationAdapter(((ServerPipeConfiguration)pipeConfig).getEndpoint());
            return new DefaultCallbackHandler("server", props, adapter);
            //return new DefaultCallbackHandler("server", props);
        } catch (Exception e) {
            log.log(Level.SEVERE, 
                    LogStringsMessages.WSSPIPE_0032_ERROR_CONFIGURE_SERVER_HANDLER(), e);                 
            throw new RuntimeException(LogStringsMessages.WSSPIPE_0032_ERROR_CONFIGURE_SERVER_HANDLER(), e);
        }
    }    
    
    @SuppressWarnings("unchecked")
    private RealmAuthenticationAdapter getRealmAuthenticationAdapter(WSEndpoint wSEndpoint) {
        String className = "javax.servlet.ServletContext";
        Class ret = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            try {
                ret = loader.loadClass(className);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        if (ret == null) {
            // if context classloader didnt work, try this
            loader = this.getClass().getClassLoader();
            try {
                ret = loader.loadClass(className);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        if (ret != null) {
            Object obj = wSEndpoint.getContainer().getSPI(ret);
            if (obj != null) {
                return RealmAuthenticationAdapter.newInstance(obj);
            }
        }
        return null;
    }
    
    //doing this here becuase doing inside keyselector of optimized security would
    //mean doing it twice (if SCT was used for sign and encrypt) which can impact performance
    @SuppressWarnings("unchecked")
    private void updateSCBootstrapCredentials(Packet packet, ProcessingContext ctx) {
        SecurityContextToken sct =
                (SecurityContextToken)packet.invocationProperties.get(MessageConstants.INCOMING_SCT);
        if (sct != null) {
            //Session session = this.sessionManager.getSession(sct.getIdentifier().toString());
            //IssuedTokenContext ctx = session.getSecurityInfo().getIssuedTokenContext();
            //IssuedTokenContext itctx = (IssuedTokenContext)((ProcessingContextImpl)ctx).getIssuedTokenContextMap().get(sct.getIdentifier().toString());
            IssuedTokenContext itctx = (IssuedTokenContext)sessionManager.getSecurityContext(sct.getIdentifier().toString());
            if (itctx != null) {
                Subject from = itctx.getRequestorSubject();
                Subject to = DefaultSecurityEnvironmentImpl.getSubject(packet.invocationProperties);
                copySubject(from,to);
            }
        }
    }

     @SuppressWarnings("unchecked")
    private static void copySubject(final Subject from, final Subject to) {
        if (from == null || to == null) {
            return;
        }
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                to.getPrincipals().addAll(from.getPrincipals());
                to.getPublicCredentials().addAll(from.getPublicCredentials());
                to.getPrivateCredentials().addAll(from.getPrivateCredentials());
                return null; // nothing to return
            }
        });
    }
}
