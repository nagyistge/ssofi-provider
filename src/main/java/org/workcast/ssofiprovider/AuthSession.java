/*
 * Copyright 2013 Keith D Swenson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors Include: Shamim Quader, Sameer Pradhan, Kumar Raja, Jim Farris,
 * Sandia Yang, CY Chen, Rajiv Onat, Neal Wang, Dennis Tam, Shikha Srivastava,
 * Anamika Chaudhari, Ajay Kakkar, Rajeev Rastogi
 */
package org.workcast.ssofiprovider;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;

import org.openid4java.message.ParameterList;

/**
 * Holds all the information that we need for a session
 */
public class AuthSession implements Serializable {

    private static final long serialVersionUID = 1L;

    ParameterList paramlist = null;

    // if something goes wrong, note it here for display next time
    Exception errMsg = null;

    // this is where the entire exchange will return to once done logging in
    String return_to;

    // this is the originally passed identity to VERIFY
    String identity;

    // this is the verified identity that the user is logged in as
    // null when use not logged in.
    String authIdentity;

    // FOLLOWING THREE FIELDS FOR REGISTRATION OR RESETTING PASSWORD
    // This is the email address supplied by the user and tested
    // for registering a new email address.
    String regEmail;

    // This is the generated magic number which the user must receive
    // in the email address, and supply to prove that it was received
    String regMagicNo;

    // When the user has successfully confirmed receipt of the magic
    // number at the specified email address and entered it.
    // then this will be marked true so we don't have to do it again.
    boolean regEmailConfirmed = false;

    Properties savedParams = new Properties();

    public boolean loggedIn() {
        return authIdentity != null;
    }

    public void login(String authenticated) {
        authIdentity = authenticated;
    }

    public void logout() {
        authIdentity = null;
    }

    public String loggedUser() {
        return authIdentity;
    }

    public void clearError() {
        errMsg = null;
        savedParams.clear();
    }

    public void reinit(HttpServletRequest request) {
        paramlist = new ParameterList(request.getParameterMap());
        return_to = request.getParameter("openid.return_to");
        identity = request.getParameter("openid.identity");
        errMsg = null;
    }

    public String startRegistration(String email) {
        regEmail = email;
        regMagicNo = createMagicNumber();
        regEmailConfirmed = false;
        return regMagicNo;
    }

    public void saveParameterList(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Enumeration<String> penum = request.getParameterNames();
        while (penum.hasMoreElements()) {
            String name = penum.nextElement();
            String val = request.getParameter(name);
            savedParams.put(name, val);
        }
    }

    public String getSavedParameter(String name) {
        return savedParams.getProperty(name);
    }

    private static String createMagicNumber() {
        Random rand = new Random();
        String nineLetters = IdGenerator.generateKey();
        StringBuffer betterNumber = new StringBuffer(20);
        betterNumber.append(nineLetters.substring(0, 3));
        betterNumber.append("-");
        betterNumber.append((char) ('A' + rand.nextInt(26)));
        betterNumber.append((char) ('A' + rand.nextInt(26)));
        betterNumber.append("-");
        betterNumber.append(nineLetters.substring(3, 6));
        betterNumber.append("-");
        betterNumber.append((char) ('A' + rand.nextInt(26)));
        betterNumber.append((char) ('A' + rand.nextInt(26)));
        betterNumber.append("-");
        betterNumber.append(nineLetters.substring(6, 9));
        return betterNumber.toString();
    }

    /**
     * return a copy of this object
     */
    public AuthSession copy() {
        AuthSession myCopy = new AuthSession();
        myCopy.errMsg = this.errMsg;
        myCopy.authIdentity = this.authIdentity;
        myCopy.identity = this.identity;
        myCopy.paramlist = this.paramlist;
        myCopy.regEmail = this.regEmail;
        myCopy.regEmailConfirmed = this.regEmailConfirmed;
        myCopy.regMagicNo = this.regMagicNo;
        myCopy.return_to = this.return_to;
        myCopy.savedParams = this.savedParams;
        return myCopy;
    }
}
