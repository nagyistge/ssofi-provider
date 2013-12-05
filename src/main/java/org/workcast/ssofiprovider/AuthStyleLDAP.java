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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

/**
 * Collect all the LDAP specific functionality into this class
 */
public class AuthStyleLDAP implements AuthStyle {

    String factoryInitial;
    String providerUrl;
    String securityAuthentication;
    String securityPrincipal;
    String securityCredentials;

    String queryBase;
    String queryPrefix;
    String queryPostfix;
    Hashtable<String, String> htLDAP;
    String adminGroup;

    /**
     * this is the list of IDS that are administrators
     */
    private List<String> adminList;

    /**
     * OpenID is very "bursty" meaning that a single user tends to make multiple
     * requests right in a row. So saving the last user info will probably cut
     * the LDAP requests in half or thirds. Should probably keep a cache of 10
     * users, and should time them out at time rate.
     */
    private UserInformation lastUserLookedUp;

    public AuthStyleLDAP(Properties configSettings) throws Exception {

        factoryInitial = getRequiredConfigProperty(configSettings, "java.naming.factory.initial");
        providerUrl = getRequiredConfigProperty(configSettings, "java.naming.provider.url");
        securityAuthentication = getRequiredConfigProperty(configSettings,
                "java.naming.security.authentication");
        securityPrincipal = getRequiredConfigProperty(configSettings,
                "java.naming.security.principal");
        securityCredentials = getRequiredConfigProperty(configSettings,
                "java.naming.security.credentials");
        adminGroup = getRequiredConfigProperty(configSettings, "adminGroup");
        queryBase = getRequiredConfigProperty(configSettings, "queryBase");
        String queryFilter = getRequiredConfigProperty(configSettings, "queryFilter");

        int idLoc = queryFilter.indexOf("{id}");
        if (idLoc < 0) {
            throw new Exception("The queryFilter parameter must have a token, {id}, to indicate "
                    + "the position that the currently searched for id willbe substituted in.  "
                    + "Value for queryPart received was (" + queryFilter + ")");
        }
        queryPrefix = queryFilter.substring(0, idLoc);
        queryPostfix = queryFilter.substring(idLoc + 4);

        htLDAP = new Hashtable<String, String>();
        htLDAP.put("java.naming.factory.initial", factoryInitial);
        htLDAP.put("java.naming.provider.url", providerUrl);
        htLDAP.put("java.naming.security.authentication", securityAuthentication);
        htLDAP.put("java.naming.security.principal", securityPrincipal);
        htLDAP.put("java.naming.security.credentials", securityCredentials);

        adminList = initAdminUserList();
    }

    public String getStyleIndicator() {
        return "ldap";
    }

    public boolean authenticateUser(String userNetId, String userPwd) throws Exception {
        try {

            Hashtable<String, String> envht = new Hashtable<String, String>();

            envht.put("java.naming.factory.initial", factoryInitial);
            envht.put("java.naming.provider.url", providerUrl);
            envht.put("java.naming.security.authentication", securityAuthentication);
            envht.put("java.naming.security.principal", securityPrincipal);
            envht.put("java.naming.security.credentials", securityCredentials);

            String filter = queryPrefix + userNetId + queryPostfix;
            String base = queryBase;

            InitialDirContext dirctx = new InitialDirContext(envht);
            SearchControls sctrl = new SearchControls();
            sctrl.setSearchScope(2);

            NamingEnumeration<?> results = dirctx.search(base, filter, sctrl);

            if (!results.hasMore()) {
                return false;
            }

            SearchResult searchResult = (SearchResult) results.next();

            String userDN = searchResult.getName() + "," + base;

            envht.put("java.naming.security.principal", userDN);
            envht.put("java.naming.security.credentials", userPwd);

            dirctx = new InitialDirContext(envht);
            return true;
        }
        catch (Exception e) {
            String msg = e.toString();
            if (msg.contains("Invalid Credentials")) {
                return false;
            }
            else {
                throw new Exception("Unable to authenticate user '" + userNetId + "'", e);
            }
        }
    }

    private static String getRequiredConfigProperty(Properties configSettings, String key)
            throws Exception {
        String val = configSettings.getProperty(key);
        if (val == null) {
            throw new Exception("Must have a setting for '" + key
                    + "' in the configuration file for OpenIDServlet");
        }
        return val;
    }

    public UserInformation getUserInfo(String userNetId) throws Exception {
        // return the last cached value if it is the same id
        if (lastUserLookedUp != null && lastUserLookedUp.id.equals(userNetId)) {
            return lastUserLookedUp;
        }

        UserInformation uret = new UserInformation();

        String filter = queryPrefix + userNetId + queryPostfix;
        String base = queryBase;

        InitialDirContext dirctx = new InitialDirContext(htLDAP);
        SearchControls sctrl = new SearchControls();
        sctrl.setSearchScope(2);

        NamingEnumeration<SearchResult> results = dirctx.search(base, filter, sctrl);

        if (!results.hasMore()) {
            return uret;
        }

        SearchResult searchResult = results.next();
        if (searchResult.getNameInNamespace() != null) {
            uret.directoryName = searchResult.getNameInNamespace();
        }

        Attributes attrs = searchResult.getAttributes();
        if (attrs.get("uid") != null) {
            uret.id = (String) attrs.get("uid").get();
        }
        String firstName = "";
        String lastName = "";

        if (attrs.get("givenname") != null) {
            firstName = (String) attrs.get("givenname").get();
        }
        if (attrs.get("sn") != null) {
            lastName = (String) attrs.get("sn").get();
        }
        if (attrs.get("mail") != null) {
            uret.emailAddress = (String) attrs.get("mail").get();
        }
        uret.fullName = firstName + " " + lastName;

        if (!userNetId.equals(uret.id)) {
            throw new Exception("Ooops, don't understand we were looking up user (" + userNetId
                    + ") but got user (" + uret.id + ")");
        }

        lastUserLookedUp = uret;
        return uret;
    }

    public void setPassword(String userId, String newPwd) throws Exception {

        DirContext ctx = new InitialDirContext(htLDAP);
        ModificationItem[] mods = new ModificationItem[1];
        mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(
                "userpassword", newPwd));
        UserInformation userInfo = getUserInfo(userId);
        ctx.modifyAttributes(userInfo.directoryName, mods);
        ctx.close();
    }

    public void changePassword(String userId, String oldPwd, String newPwd) throws Exception {
        setPassword(userId, newPwd);

    }

    public boolean isAdmin(String userId) {
        // UserInformation userInfo = getUserInfo(userId);
        for (String adminId : adminList) {
            if (adminId.equals(userId)) {
                return true;
            }
        }
        return false;
    }

    private List<String> initAdminUserList() throws Exception {

        String filter = queryPrefix + adminGroup + queryPostfix;
        String base = queryBase;

        InitialDirContext dirctx = new InitialDirContext(htLDAP);
        SearchControls sctrl = new SearchControls();
        sctrl.setSearchScope(2);

        NamingEnumeration<SearchResult> results = dirctx.search(base, filter, sctrl);

        List<String> list = new ArrayList<String>();
        if (!results.hasMore()) {
            return list;
        }

        SearchResult sr = results.next();
        Attributes att = sr.getAttributes();
        Attribute uniqueMember = att.get("uniquemember");
        int last = uniqueMember.size() - 1;
        for (int i = 0; i <= last; i++) {
            list.add((String) uniqueMember.get(i));
        }
        return list;
    }

    /*
     * private boolean createNewUser(String emailId, String firstName, String
     * lastName, String pwd) throws Exception { DirContext ctx = new
     * InitialDirContext(htLDAP); //String commonName = firstName + " " +
     * lastName; String uid = firstName.charAt(0)+""+lastName; String dn =
     * "uid="+uid+ "," +queryBase;
     *
     * Attributes userAttributes = new BasicAttributes(true); BasicAttribute
     * basicattribute = new BasicAttribute("objectclass","top");
     * basicattribute.add(1, "person"); basicattribute.add(1,
     * "organizationalPerson"); basicattribute.add(2, "inetorgperson");
     * userAttributes.put(basicattribute);
     *
     * //This depends upon your LDAP tree structure userAttributes.put(new
     * BasicAttribute("givenname",firstName)); userAttributes.put(new
     * BasicAttribute("cn",uid)); userAttributes.put(new
     * BasicAttribute("userpassword",pwd)); userAttributes.put(new
     * BasicAttribute("mail",emailId)); userAttributes.put(new
     * BasicAttribute("sn",lastName)); ctx.createSubcontext(dn, userAttributes);
     * return true; }
     */

    public void updateUserInfo(UserInformation user, String password) throws Exception {
        // not sure what to do here for LDAP
    }

    public String searchForID(String searchTerm) throws Exception {

        // this is a very lame search ... it only does an EXACT match
        // must consider a better way to search for users in the future
        UserInformation ui = getUserInfo(searchTerm);
        return ui.id;
    }

}
