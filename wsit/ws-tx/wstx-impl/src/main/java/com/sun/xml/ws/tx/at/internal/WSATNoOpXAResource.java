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

package com.sun.xml.ws.tx.at.internal;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 *
 * @author paulparkinson
 */
/**
 * NoOp XAResource implemented in order to insure onePhase optimization is not
 *  used for WS-AT transactions
 * @author paulparkinson
 */
class WSATNoOpXAResource implements XAResource {

    public WSATNoOpXAResource() {
    }

    public void commit(Xid xid, boolean bln) throws XAException {
        debug("commit");
    }

    public void end(Xid xid, int i) throws XAException {
        debug("end");
    }

    public void forget(Xid xid) throws XAException {
    }

    public int getTransactionTimeout() throws XAException {
        return 30000; //todo make -1
    }

    public boolean isSameRM(XAResource xar) throws XAException {
        return false;
    }

    public int prepare(Xid xid) throws XAException {
        debug("prepare");
        return XAResource.XA_OK;
    }

    public Xid[] recover(int i) throws XAException {
        return new Xid[]{};
    }

    public void rollback(Xid xid) throws XAException {
        debug("rollback");
    }

    public boolean setTransactionTimeout(int i) throws XAException {
        return true;
    }

    public void start(Xid xid, int i) throws XAException {
        debug("start");
    }


  private void debug(String msg) {
    //  System.out.println("wsatnoopxaresource debug:"+msg);
    }

}