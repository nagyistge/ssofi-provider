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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.openid4java.message.AuthFailure;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.DirectError;
import org.openid4java.message.Message;
import org.openid4java.message.MessageExtension;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchRequest;
import org.openid4java.message.ax.FetchResponse;
import org.openid4java.server.ServerManager;
import org.workcast.streams.SSLPatch;

/**
 * Implements an OpenID provider
 *
 * Note there are two (2) users potentially on every request. Those can be
 * different. A user "joe" might display info of user "Larry".
 *
 * One is the "displayUser" which is the user information being displayed.
 * displayUser might be a null string value if the base address is accessed. The
 * display user is determined by the web address being accessed.
 *
 * The other is the "loggedUser" which is the user logged in. loggedUser might
 * be null if nobody is logged in. The logged in user is based on a cookie that
 * is set upon successful login. Note that the openId protocol does not require
 * that the display user be the user logging in.
 */
public class OpenIDHandler implements TemplateTokenRetriever {

    public static ServerManager manager = null;
    public static boolean initialized = false;
    public static Exception initFailure = null;

    // this is the reconfigurable base address of the application (for proxy
    // cases)
    public static String baseURL;

    // this is internal address of the root of application (on this server)
    // use this for decomposing request URLs
    public static String rootURL;
    public static String knownAssetPath;

    private static AuthStyle authStyle = null;
    private static SessionHandler sHand = null;
    private static EmailHandler emailHandler = null;
    private static SecurityHandler securityHandler = null;

    HttpServletRequest request;
    HttpServletResponse response;
    HttpSession session;

    AuthSession aSession;
    boolean saveSession = false;

    private String paramGo = "";

    private String loggedOpenId;

    private boolean isDisplaying = false;
    private UserInformation displayInfo = null;

    private String addressedUserId;
    private String assoc_handle;

    private AddressParser requestedIdentity = null;

    static boolean isLDAPMode = false;

    /**
     * Handler static variables must be set up before handling any request. This
     * matches the servlet initialization protocol.
     */
    public static void init(ServletConfig config) {

        ServletContext sc = config.getServletContext();

        try {
            // disable Java certificate validation in the SSL level
            // necessary so that bytes can be read reliably over SSL
            SSLPatch.disableSSLCertValidation();

            String webInfPath = sc.getRealPath("WEB-INF");
            File configFile = new File(webInfPath, "config.txt");
            if (!configFile.exists()) {
                throw new Exception(
                        "Server needs to be configured.  No configuration file found: ("
                                + configFile.toString() + ")");
            }

            FileInputStream fis = new FileInputStream(configFile);
            Properties tprop = new Properties();
            tprop.load(fis);
            fis.close();
            Properties configSettings = tprop;

            File bipfile = new File(webInfPath, "blockedIp.txt");
            if (!bipfile.exists()) {
                bipfile.createNewFile();
            }
            String blockedIpListFilePath = bipfile.getPath();

            String sessionFolder = configSettings.getProperty("sessionFolder");
            if (sessionFolder == null) {
                sHand = new SessionHandlerMemory();
            }
            else {
                sHand = new SessionHandlerFile(new File(sessionFolder));
            }
            isLDAPMode = "LDAP".equalsIgnoreCase(configSettings.getProperty("authStyle"));

            if (isLDAPMode) {
                authStyle = new AuthStyleLDAP(configSettings);
            }
            else {
                // NOTE: local mode must be the DEFAULT if no setting is
                // supplied
                authStyle = new AuthStyleLocal(sc, configSettings);
            }

            baseURL = getRequiredConfigProperty(configSettings, "baseURL").toLowerCase();

            if (!baseURL.endsWith("/")) {
                baseURL = baseURL + "/";
            }
            rootURL = getRequiredConfigProperty(configSettings, "rootURL").toLowerCase();

            if (!rootURL.endsWith("/")) {
                rootURL = rootURL + "/";
            }
            knownAssetPath = rootURL + "$/";

            String captchaPrivateKey = configSettings.getProperty("captchaPrivateKey");
            String captchaPublicKey = configSettings.getProperty("captchaPublicKey");
            securityHandler = new SecurityHandler(captchaPrivateKey, captchaPublicKey,
                    blockedIpListFilePath);

            File emailConfigFile = new File(webInfPath, "EmailNotification.properties");
            if (!emailConfigFile.exists()) {
                throw new Exception(
                        "Server needs to be configured.  No configuration file found: ("
                                + emailConfigFile.toString() + ")");
            }

            FileInputStream fisEmail = new FileInputStream(emailConfigFile);
            Properties propEmail = new Properties();
            propEmail.load(fisEmail);
            fisEmail.close();
            Properties emailConfigSettings = propEmail;
            emailHandler = new EmailHandler(sc, emailConfigSettings);

            manager = new ServerManager();
            // configure the OpenID Provider's endpoint URL

            String pattern = getRequiredConfigProperty(configSettings, "pattern").toLowerCase();
            AddressParser.initialize(pattern);

            int idPos = pattern.indexOf("{id}");
            if (idPos < 0) {
                throw new Exception(
                        "The pattern setting value MUST have the token, {id}, within it "
                                + "to indicate the location that the id will be in the URL");
            }

            manager.setOPEndpointUrl(baseURL);
            // for a working demo, not enforcing RP realm discovery
            // since this new feature is not deployed
            // manager.getRealmVerifier().setEnforceRpId(false);

            initialized = true;
        }
        catch (Exception e) {
            initialized = false;
            initFailure = e;
            // get something into the log as well in case nobody accesses the
            // server
            e.printStackTrace();
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

    /**
     * Create a new instance for every request then the member functions don't
     * need to pass these all over the place Use an instance ONLY ONCE
     */
    public OpenIDHandler(HttpServletRequest httpReq, HttpServletResponse resp) {
        request = httpReq;
        response = resp;
        session = request.getSession();
    }

    /**
     * an instance of this is created and called on a single thread
     */
    public void doGet() {
        try {
            String sessionId = getSSOFISessionId();

            if (sHand==null) {
                streamTemplate("configErrScreen");
                return;
            }
            aSession = sHand.getAuthSession(sessionId);
            doGetWithSession();
            // doGetWithSession never throws an exception, which means that this
            // is being saved whether an error occurs or not! That is the right
            // thing because the session object holds the error message for the
            // next page
            if (saveSession) {
                sHand.saveAuthSession(sessionId, aSession);
            }
        }
        catch (Exception e) {
            try {
                System.out.println("!!! Error getting or saving session information !!!");
                e.printStackTrace();
            }
            catch (Exception eeeee) {
                eeeee.printStackTrace();
            }
        }
    }

    /**
     * Handles the request with the assumption that the session object has been
     * fetched, and will be saved afterwards.
     */
    public void doGetWithSession() {

        // check and see if this is the very first access in an attempt stream
        // initialize this object if there is not one already
        try {

            if (!initialized) {
                streamTemplate("configErrScreen");
                return;
            }

            paramGo = getCompleteURL();
            String requestURL = request.getRequestURL().toString();

            if (baseURL == null) {
                // if not set at initialization time, set it here on first
                // request
                baseURL = request.getRequestURL().toString();
            }

            if (!requestURL.startsWith(rootURL)) {
                throw new Exception("sorry, request must start with (" + rootURL + "):  ("
                        + requestURL + ")");
            }

            if (requestURL.startsWith(knownAssetPath)) {
                serveUpAsset(requestURL.substring(knownAssetPath.length()));
                return;
            }

            // set up loggedUserId and loggedOpenId
            determineLoggedUser();

            addressedUserId = requestURL.substring(rootURL.length());
            assoc_handle = request.getParameter("openid.assoc_handle");

            if (addressedUserId.length() > 0) {
                displayInfo = authStyle.getUserInfo(addressedUserId);
                isDisplaying = true;
            }

            // anything below here is LIKELY to change the session
            saveSession = true;
            String mode = defParam("openid.mode", "display");
            System.out.println("OpenIdLDAP: " + request.getRequestURL().toString().trim() + " @"
                    + mode);

            if ("lookup".equals(mode)) {
                redirectToIdentityPage(authStyle.searchForID(reqParam("entered-id")));
            }
            else if ("loginView".equals(mode)) {
                // this is the mode that displays a login prompt
                modeLoginView();
            }
            else if ("changeIdView".equals(mode)) {
                // this is the mode that displays prompt to change id
                modeChangeIdView();
            }
            else if ("passwordView".equals(mode)) {
                // this is the mode that displays prompt to change password
                streamTemplate("changePassword");
            }
            else if ("register".equals(mode)) {
                // this is the mode that displays prompt to register new user
                // which then posts to 'registerNewAction'
                streamTemplate("userRegistration");
            }
            else if ("registerNewAction".equals(mode)) {
                modeRegisterNewAction();
            }
            else if ("confirmationKey".equals(mode)) {
                // this is the mode that displays prompt to change id
                // which then posts to 'validateKeyAction'
                displayInfo = authStyle.getUserInfo(aSession.regEmail);
                streamTemplate("enterConfirmationKey");
            }
            else if ("validateKeyAction".equals(mode)) {
                modeValidateKeyAction();
            }
            else if ("registrationForm".equals(mode)) {
                // this is the mode that displays prompt for user details
                // which then posts to 'createNewUserAction'
                streamTemplate("registrationForm");
            }
            else if ("createNewUserAction".equals(mode)) {
                modeCreateNewUserAction();
            }
            else if ("login".equals(mode)) {
                // this takes the action of logging the user in, and returning
                // if all OK
                String enteredId = reqParam("entered-id");
                String password = reqParam("password");
                boolean flag = authStyle.authenticateUser(enteredId, password);
                if (flag) {
                    setLogin(enteredId);
                }
                else {
                    aSession.errMsg = new Exception("Unable to log you in to user id (" + enteredId
                            + ") with that password.  Please try again.");
                }
                redirectToIdentityPage(defParam("display-id", ""));
            }
            else if ("loginAction".equals(mode)) {
                modeLoginAction();
            }
            else if ("cancelAction".equals(mode)) {
                returnLoginFailure();
            }
            else if ("passwordAction".equals(mode)) {
                modePasswordAction();
            }
            else if ("resetPasswordAction".equals(mode)) {
                modeResetPasswordAction();
            }
            else if ("acceptPreviousLogin".equals(mode)) {
                returnLoginSuccess();
            }
            else if ("relogin".equals(mode)) {
                setLogin(null);
                response.sendRedirect("?openid.mode=loginView");
            }
            else if ("logout".equals(mode)) {
                String go = reqParam("go");
                setLogin(null);
                response.sendRedirect(go);
            }
            else if ("display".equals(mode)) {
                // just need to display the user information
                if (isDisplaying) {
                    displayUserPage();
                }
                else {
                    displayRootPage();
                }
            }
            else {
                modeOfficialOpenIDRequest(mode);
            }
        }
        catch (Exception e) {
            try {
                aSession.errMsg = e;
                System.out.println("--- OpenIDHandler error --- " + (new Date()).toString());
                e.printStackTrace();
                OutputStreamWriter errOut = new OutputStreamWriter(System.out);
                writeHtmlException(errOut, e);
                System.out.println("--- ------------------  --- ");
                response.sendRedirect(baseURL);
                return;

            }
            catch (Exception eeeee) {
                eeeee.printStackTrace();
            }
        }
    }

    /**
     * get the value directly from the current request object
     */
    String reqParam(String name) throws Exception {

        String val = request.getParameter(name);
        if (val == null || val.length() == 0) {
            throw new Exception("Got a request without a required '" + name + "' parameter");
        }
        return val;
    }

    /**
     * get the value directly from the current request object
     */
    String defParam(String name, String defaultVal) throws Exception {

        String val = request.getParameter(name);
        if (val == null || val.length() == 0) {
            return defaultVal;
        }
        return val;
    }

    /**
     * get the value from the original OpenID request
     */
    String getRequiredOpenIDParameter(String name) throws Exception {
        if (aSession.paramlist == null) {
            throw new Exception(
                    "Requesting a parameter when the paramter list has not be constructed yet.");
        }
        String ret = aSession.paramlist.getParameterValue(name);
        if (ret == null || ret.length() == 0) {
            throw new Exception("Got a request without a required '" + name + "' parameter");
        }
        return ret;
    }

    /**
     * Root page is the page that is displayed when no user is specified. This
     * is the place where you can enter a user id
     */
    private void displayRootPage() throws Exception {
        if (!aSession.loggedIn()) {
            streamTemplate("justAnonymous");
        }
        else {
            streamTemplate("justLoggedIn");
        }

    }

    private void displayUserPage() throws Exception {
        if (!aSession.loggedIn()) {
            streamTemplate("displayAnonymous");
        }
        else {
            streamTemplate("displayLoggedIn");
        }

    }

    private void modeLoginView() throws Exception {
        // whoever they logged in last time as...
        // requestedIdentity = findCookieValue("SSOFIUser");
        streamTemplate("promptedLogin");
        aSession.clearError();
    }

    private void modeChangeIdView() throws Exception {
        requestedIdentity = new AddressParser(aSession.identity);
        streamTemplate("promptedChangeId");
    }

    /**
     * this receives a post from a form with user profile detail infor it it
     * this will either create or update the user profile. It will save
     * regardless of whether there was a profile there before.
     */
    private void modeCreateNewUserAction() throws Exception {
        try {
            String option = reqParam("option");
            if (option.equals("Cancel")) {
                response.sendRedirect("?openid.mode=display");
                return;
            }
            if (!aSession.regEmailConfirmed) {
                throw new Exception(
                        "Illegal state!  Attempt to create a user profile when the email has not been confirmed.  Is this a hacker???");
            }

            String emailId = reqParam("emailId");

            String fullName = defParam("fullName", "");

            UserInformation user = authStyle.getUserInfo(emailId);

            String pwd = reqParam("password");
            String confirmPwd = reqParam("confirmPwd");
            if (pwd.length() < 6) {
                throw new Exception("New password must be 6 or more characters long.");
            }

            if (!pwd.equals(confirmPwd)) {
                throw new Exception("The new password values supplied do not match.  Try again");
            }

            if (user.fullName == null || user.fullName == "") {
                user.fullName = fullName;
            }

            authStyle.updateUserInfo(user, pwd);

            boolean loginFlag = authStyle.authenticateUser(emailId, pwd);
            if (loginFlag) {
                setLogin(emailId);
            }
            else {
                throw new Exception("Unable to log you in to user id (" + emailId
                        + ") with that password.  Please try again.");
            }
            if ((aSession.return_to == null) || (aSession.return_to.length() <= 0)) {
                redirectToIdentityPage(defParam("display-id", ""));
            }
            else {
                returnLoginSuccess();
            }
            return;
        }
        catch (Exception e) {
            aSession.errMsg = e;
            response.sendRedirect("?openid.mode=registrationForm");
            return;
        }
    }

    /**
     * create the authentication response message with true == all authenticated
     * and OK
     */
    private void returnLoginSuccess() throws Exception {

        // it could be that the user have been sitting there for a long time,
        // and the
        // session has completely timed out. If so, handle gracefully as
        // possible
        // by just redirecting to the root of the application.
        if (aSession.paramlist == null) {
            aSession.errMsg = new Exception(
                    "Session time out... too much time to login in and no longer have information about where to return to.");
            response.sendRedirect(baseURL);
            return;
        }

        AuthRequest authReq = AuthRequest.createAuthRequest(aSession.paramlist,
                manager.getRealmVerifier());

        Message oidResp = manager.authResponse(authReq, loggedOpenId, loggedOpenId, true, false);
        if (oidResp instanceof DirectError) {
            ServletOutputStream os = response.getOutputStream();
            try {
                String respString = oidResp.keyValueFormEncoding();
                System.out.println("OpenIDHandler: DirectError = " + respString);
                if (respString != null) {
                    os.write(respString.getBytes());
                }
            }
            finally {
                os.close();
            }
        }
        else if (oidResp instanceof AuthFailure) {
            System.out.println("OpenIDHandler: AuthFailure = " + oidResp.keyValueFormEncoding());
            response.sendRedirect(oidResp.getDestinationUrl(true));
        }
        else {
            addExtension(authReq, oidResp);
            manager.sign((AuthSuccess) oidResp);
            aSession.return_to = "";

            String destUrl = oidResp.getDestinationUrl(true);
            System.out.println("OpenIDHandler: SUCCESS RETURN = " + destUrl);
            response.sendRedirect(destUrl);

        }

    }

    /**
     * Add extension to the response message if there is a request for
     * Attribute.
     */
    private void addExtension(final AuthRequest authRequest, final Message oidResp)
            throws Exception {
        if (authRequest.hasExtension(AxMessage.OPENID_NS_AX)) {
            MessageExtension ext = authRequest.getExtension(AxMessage.OPENID_NS_AX);
            if (ext instanceof FetchRequest) {
                FetchRequest fetchReq = (FetchRequest) ext;
                @SuppressWarnings("unchecked")
                Map<String, ?> required = fetchReq.getAttributes(true);
                @SuppressWarnings("unchecked")
                Map<String, ?> optional = fetchReq.getAttributes(false);
                if (required.containsKey("email") || optional.containsKey("email")) {
                    Map<String, String> userDataExt = new HashMap<String, String>();
                    String email = authStyle.getUserInfo(aSession.loggedUser()).emailAddress;
                    userDataExt.put("email", email);

                    FetchResponse fetchResp = FetchResponse.createFetchResponse(fetchReq,
                            userDataExt);
                    oidResp.addExtension(fetchResp);
                }
            }
            else {
                throw new UnsupportedOperationException("Not Supported");
            }
        }
    }

    private void modePasswordAction() throws Exception {
        // this takes the action of logging the user in, and returning if all OK
        // first see if they pressed the Cancel key
        String op = reqParam("op");
        if (op.equals("Cancel")) {
            response.sendRedirect("?openid.mode=display");
            return;
        }
        String oldPwd = reqParam("oldPwd");
        String newPwd1 = reqParam("newPwd1");
        String newPwd2 = reqParam("newPwd2");
        boolean flag = authStyle.authenticateUser(aSession.loggedUser(), oldPwd);
        if (!flag) {
            aSession.errMsg = new Exception(
                    "Doesn't look like you gave the correct old password.  Required in order to change passwords.");
            response.sendRedirect("?openid.mode=passwordView");
            return;
        }
        if (newPwd1.length() < 6) {
            aSession.errMsg = new Exception("New password must be 6 or more characters long.");
            response.sendRedirect("?openid.mode=passwordView");
            return;
        }
        if (!newPwd1.equals(newPwd2)) {
            aSession.errMsg = new Exception(
                    "The new password values supplied do not match.  Try again");
            response.sendRedirect("?openid.mode=passwordView");
            return;
        }

        authStyle.changePassword(aSession.loggedUser(), oldPwd, newPwd1);
        response.sendRedirect("?openid.mode=display");
    }

    private void modeResetPasswordAction() throws Exception {
        // this takes the action of logging the user in, and returning if all OK
        // first see if they pressed the Cancel key
        String op = reqParam("op");
        if (op.equals("Cancel")) {
            response.sendRedirect("?openid.mode=display");
            return;
        }
        String userId = reqParam("userId");
        String newPwd = reqParam("newPwd");

        if (newPwd.length() < 6) {
            throw new Exception("New password must be 6 or more characters long.");
        }

        authStyle.setPassword(userId, newPwd);
        response.sendRedirect("?openid.mode=display");
    }

    private void modeLoginAction() throws Exception {
        // this takes the action of logging the user in, and returning if all OK
        // first see if they pressed the Cancel key
        String enteredId = "";
        String password = "";

        String op = reqParam("op");
        if (op.equals("Cancel")) {
            returnLoginFailure();
            return;
        }
        enteredId = reqParam("entered-id");
        password = reqParam("password");
        boolean flag = authStyle.authenticateUser(enteredId, password);
        if (flag) {
            setLogin(enteredId);
            returnLoginSuccess();
            return;
        }
        aSession.errMsg = new Exception("Unable to log you in to user id (" + enteredId
                + ") with that password.  Please try again");
        response.sendRedirect("?openid.mode=loginView");
    }

    private void modeRegisterNewAction() throws Exception {
        String userId = reqParam("registerEmail");
        if (!emailHandler.validate(userId)) {
            aSession.errMsg = new Exception("The id supplied (" + userId
                    + ") does not appear to be a valid email address.");
            response.sendRedirect("?openid.mode=register");
            return;
        }

        // Security check
        aSession.saveParameterList(request);
        Properties secProp = new Properties();
        secProp.put(SecurityHandler.REGIS_REQ_REMOTE_IP, request.getRemoteAddr());
        secProp.put(SecurityHandler.REGIS_REQ_EMAILID, defParam("registerEmail", ""));
        secProp.put(SecurityHandler.CAPTCHA_CHALLANGE_REQ,
                defParam(SecurityHandler.CAPTCHA_CHALLANGE_REQ, ""));
        secProp.put(SecurityHandler.CAPTCHA_CHALLANGE_RESP,
                defParam(SecurityHandler.CAPTCHA_CHALLANGE_RESP, ""));

        try {
            securityHandler.validate(secProp);
        }
        catch (Exception e) {
            aSession.errMsg = e;
            response.sendRedirect("?openid.mode=register");
            return;
        }

        aSession.savedParams.clear();
        aSession.startRegistration(userId);
        System.out.println("Email Registration regMagicNo :" + aSession.regMagicNo + ":");
        emailHandler.sendEmail(userId, 2, aSession.regMagicNo);
        response.sendRedirect("?openid.mode=confirmationKey");
    }

    private void modeValidateKeyAction() throws Exception {
        String registerEmail = reqParam("registerEmail");
        if (!registerEmail.equals(aSession.regEmail)) {
            aSession.errMsg = new Exception(
                    "Something is wrong, please start over.  Current implementation requires that you keep the browser open, that you enter the security key into the same browser requested from, and that you do this before requesting again.");
            response.sendRedirect("?openid.mode=confirmationKey");
            return;
        }
        String confirmKey = reqParam("registeredEmailKey");
        if (!confirmKey.equals(aSession.regMagicNo)) {
            aSession.errMsg = new Exception(
                    "Confirmation Key entered is incorrect for the current attempt.  Make sure you are using the correct email message.");
            response.sendRedirect("?openid.mode=confirmationKey");
            return;
        }
        aSession.regEmailConfirmed = true;
        response.sendRedirect("?openid.mode=registrationForm");
    }

    private void modeOfficialOpenIDRequest(String mode) throws Exception {
        // here start the 'official OpenID' start requests, and so this
        // signifies the starting of a new request ... even if there is a
        // request in progress, this will override it.
        aSession.reinit(request);

        if ("checkid_setup".equals(mode)) {
            if (!aSession.loggedIn()) {
                response.sendRedirect("?openid.mode=loginView");
                return;
            }

            requestedIdentity = new AddressParser(aSession.identity);

            if (!aSession.identity.equals(loggedOpenId) && !requestedIdentity.isRoot()) {
                response.sendRedirect("?openid.mode=changeIdView");
                return;
            }

            returnLoginSuccess();
            return;
        }

        if ("checkid_immediate".equals(mode)) {
            if (!aSession.loggedIn()) {
                returnLoginFailure();
            }
            else {
                returnLoginSuccess();
            }
            return;
        }

        Message resMsg;
        String responseText;

        if ("associate".equals(mode)) {
            // --- process an association request ---
            resMsg = manager.associationResponse(aSession.paramlist);
            responseText = resMsg.keyValueFormEncoding();
        }
        else if ("check_authentication".equals(mode)) {
            // --- processing a verification request ---
            resMsg = manager.verify(aSession.paramlist);
            responseText = resMsg.keyValueFormEncoding();
        }
        else {
            throw new Exception("Unable to handle request for mode: " + mode);
        }

        // return the result to the user
        Writer out = response.getWriter();
        out.write(responseText);
        out.flush();
    }

    /**
     * create the authentication response message with false == not
     * authenticated
     */
    private void returnLoginFailure() throws Exception {

        // it could be that the user have been sitting there for a long time,
        // and the
        // session has completely timed out. If so, handle gracefully as
        // possible
        // by just redirecting to the root of the application.
        if (aSession.paramlist == null) {
            aSession.errMsg = new Exception(
                    "Session time out... too much time to login in and no longer have information about where to return to.");
            response.sendRedirect(baseURL);
            return;
        }

        Message resMsg = manager.authResponse(aSession.paramlist, aSession.identity,
                aSession.identity, false);

        String urlTail = resMsg.wwwFormEncoding();
        int questionPos = aSession.return_to.indexOf("?");
        String dest;
        if (questionPos < 0) {
            dest = aSession.return_to + "?" + urlTail;
        }
        else {
            dest = aSession.return_to + "&" + urlTail;
        }
        System.out.println("OpenIdLDAP: FAILURE RETURN = " + dest);
        response.sendRedirect(dest);
    }

    private void redirectToIdentityPage(String gotoId) throws Exception {
        if (gotoId == null) {
            gotoId = "";
        }
        String dest = baseURL + gotoId;
        response.sendRedirect(dest);
    }

    /**
     * Set to null to clear the login
     */
    private void setLogin(String loggedId) throws Exception {
        if (loggedId == null) {
            aSession.logout();
            loggedOpenId = "";
        }
        else {
            aSession.login(loggedId);
            loggedOpenId = AddressParser.composeOpenId(loggedId);

            Cookie userName = new Cookie("SSOFIUser", loggedId);
            userName.setMaxAge(30000000); // about 1 year
            userName.setPath("/"); // everything on the server
            response.addCookie(userName);
        }
    }

    /**
     * returns null if not logged in checks the session. If no session, it
     * checks the cookies. if no cookies, or cookies invalid, then not logged
     * in.
     */
    private void determineLoggedUser() throws Exception {
        if (aSession.loggedIn()) {
            loggedOpenId = AddressParser.composeOpenId(aSession.loggedUser());
        }
        else {
            loggedOpenId = "";
        }
    }

    private String getCompleteURL() throws Exception {
        String method = request.getMethod();
        String qstr = null;
        if ("GET".equals(method)) {
            qstr = request.getQueryString();
        }
        else {
            StringBuffer queryString = new StringBuffer();
            addValueIfPresent(queryString, "openid.mode");
            addValueIfPresent(queryString, "openid.identity");
            addValueIfPresent(queryString, "openid.return_to");
            addValueIfPresent(queryString, "openid.trust_root");
            addValueIfPresent(queryString, "openid.assoc_handle");
            if (queryString.length() > 0) {
                qstr = queryString.toString();
            }
        }
        if (qstr == null) {
            return request.getRequestURL().toString();
        }
        else {
            return request.getRequestURL().toString() + "?" + qstr;
        }
    }

    private void addValueIfPresent(StringBuffer res, String key) throws Exception {
        String val = request.getParameter(key);
        if (val != null) {
            if (res.length() > 0) {
                res.append("&");
            }
            res.append(key);
            res.append("=");
            res.append(URLEncoder.encode(val, "UTF-8"));
        }
    }

    public void serveUpAsset(String resourceName) throws Exception {
        ServletContext sc = session.getServletContext();
        String path = sc.getRealPath("/$/" + resourceName);

        TemplateStreamer.streamRawFile(response.getOutputStream(), new File(path));
    }

    private void streamTemplate(String fileName) throws Exception {
        javax.servlet.ServletContext sc2 = session.getServletContext();
        File ctxPath = new File(sc2.getRealPath("/"));

        // fist check to see if a special auth style specific version exists
        if (authStyle != null) {
            String testName = fileName + "." + authStyle.getStyleIndicator() + ".htm";
            File templateFile = new File(ctxPath, testName);
            if (templateFile.exists()) {
                streamTemplateCore(templateFile);
                return;
            }
        }

        // if not, use the generic one
        String testName2 = fileName + ".htm";
        File templateFile2 = new File(ctxPath, testName2);
        if (templateFile2.exists()) {
            streamTemplateCore(templateFile2);
            return;
        }
        throw new Exception("Can't find the template file: " + templateFile2.toString());
    }

    private void streamTemplateCore(File templateFile) throws Exception {
        try {
            response.setContentType("text/html;charset=UTF-8");
            Writer out = response.getWriter();
            InputStream is = new FileInputStream(templateFile);
            Reader isr = new InputStreamReader(is, "UTF-8");
            TemplateStreamer.streamTemplate(out, isr, this);
            isr.close();
            out.flush();

            // clear out any recorded error now that it has been displayed
            aSession.clearError();
        }
        catch (Exception e) {
            throw new Exception("Error streaming template file (" + templateFile.toString() + ").",
                    e);
        }
    }

    /**
     * Given a named token, write out the corresponding value
     */
    public void writeTokenValue(Writer out, String tokenName) throws Exception {
        if ("thisPage".equals(tokenName)) {
            writeHtml(out, baseURL);
        }
        else if ("fullName".equals(tokenName)) {
            if (displayInfo != null) {
                writeHtml(out, displayInfo.fullName);
            }
        }
        else if ("emailAddress".equals(tokenName)) {
            if (displayInfo != null) {
                writeHtml(out, displayInfo.emailAddress);
            }
        }
        else if ("id".equals(tokenName)) {
            if (displayInfo != null) {
                writeHtml(out, displayInfo.id);
            }
        }
        else if ("loggedUserId".equals(tokenName)) {
            if (aSession.loggedIn()) {
                writeHtml(out, aSession.loggedUser());
            }
        }
        else if ("loggedOpenId".equals(tokenName)) {
            writeHtml(out, loggedOpenId);
        }
        else if ("reqUserId".equals(tokenName)) {
            if (requestedIdentity != null) {
                writeHtml(out, requestedIdentity.getUserId());
            }
        }
        else if ("reqOpenId".equals(tokenName)) {
            if (requestedIdentity != null) {
                writeHtml(out, requestedIdentity.getOpenId());
            }
        }
        else if ("addrOpenId".equals(tokenName)) {
            if (addressedUserId != null) {
                writeHtml(out, addressedUserId);
            }
        }
        else if ("addrId".equals(tokenName)) {
            if (addressedUserId != null) {
                writeHtml(out, addressedUserId);
            }
        }
        else if ("registeredEmailId".equals(tokenName)) {
            if (aSession != null) {
                writeHtml(out, aSession.regEmail);
            }
        }
        else if ("root".equals(tokenName)) {
            writeHtml(out, baseURL);
        }
        else if ("Note".equals(tokenName)) {
            String note = "";
            if (isLDAPMode) {
                note = "The user name and password that you "
                        + "enter above will be checked against a "
                        + "directory server using LDAP protocol.";
            }
            else {
                note = "Enter your email address and password. "
                        + "If you have never set up a password for your email address then use \"Register Here\" link for a new value "
                        + ", or if you have forgotten your password, you should use the \"Forgot Your Password\" link "
                        + "to reset your password.";
            }
            writeHtml(out, note);
        }
        else if ("go".equals(tokenName)) {
            writeHtml(out, paramGo);
        }
        else if ("return_to".equals(tokenName)) {

            writeHtml(out, aSession.return_to);
        }
        else if ("return_to_app_name".equals(tokenName)) {
            String return_to_app_name = aSession.return_to.substring(aSession.return_to
                    .lastIndexOf("/") + 1);
            writeHtml(out, return_to_app_name);
        }
        else if ("assoc_handle".equals(tokenName)) {

            writeHtml(out, assoc_handle);
        }
        else if ("serverError".equals(tokenName)) {
            writeHtmlException(out, initFailure);
        }
        else if ("userError".equals(tokenName)) {
            writeHtmlException(out, aSession.errMsg);
        }
        else if ("captcha".equals(tokenName)) {
            String cerr = null;
            if (aSession.errMsg != null) {
                cerr = aSession.errMsg.getMessage();
            }
            out.write(securityHandler.getCaptchaHtML(cerr));
        }
        else if ("inputEmail".equals(tokenName)) {
            String pinputEmail = aSession.getSavedParameter("registerEmail");
            if (pinputEmail != null) {
                String tokValue = "value=" + pinputEmail;
                writeHtml(out, tokValue);
            }
        }
        else {
            writeHtml(out, "<" + tokenName + ">");
        }
    }

    public static void writeHtml(Writer w, String t) throws Exception {
        TemplateStreamer.writeHtml(w, t);
    }

    private void writeHtmlException(Writer out, Exception anException) throws Exception {
        if (anException == null) {
            return; // nothing to write
        }
        Throwable t = anException;
        boolean needBreak = false;
        while (t != null) {
            String msg = t.toString();
            if (msg.startsWith("java.lang.Exception")) {
                msg = msg.substring(21);
            }
            if (needBreak) {
                out.write("<br/> \n ");
            }
            writeHtml(out, msg);
            t = t.getCause();
            needBreak = true;
        }

        out.write("\n<!--");
        anException.printStackTrace(new PrintWriter(out));
        out.write("\n-->");
    }

    // quite simply: add space characters before and after every slash so that
    // URL wraps nicely. Only when URL used for display.
    public void writeFriendlyURL(Writer out, String urlValue) throws Exception {
        // ignore null values
        if (urlValue == null) {
            return;
        }
        int startPos = 0;
        int slashPos = urlValue.indexOf("/");
        while (slashPos >= 0) {

            writeHtml(out, urlValue.substring(startPos, slashPos));
            out.write(" / ");
            startPos = slashPos + 1;
            slashPos = urlValue.indexOf("/", startPos);
        }
        writeHtml(out, urlValue.substring(startPos));
    }

    public String getSSOFISessionId() {
        String sessionId = findCookieValue("SSOFISession");
        if (sessionId == null || sessionId.length() < 10) {
            // if our cookie does not have a value, then just take the current
            // session
            // id and use that. The value does not matter so much, just needs to
            // be unique.
            // However, this cookie will have a wider scope than normal Tomcat
            // cookies.
            sessionId = "S" + session.getId();
        }
        Cookie previousId = new Cookie("SSOFISession", sessionId);
        previousId.setMaxAge(30000); // about 6 hours
        previousId.setPath("/"); // everything on the server
        response.addCookie(previousId);
        return sessionId;
    }

    /*
     * private static void appendLetters(StringBuffer sb, long value) { while
     * (value>0) { int letterVal = (int) (value % 36); if (letterVal>25) {
     * sb.append((char)('A'+letterVal)); } else {
     * sb.append((char)('0'+letterVal-26)); } value = value / 36; } }
     */

    public String findCookieValue(String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie oneCookie : cookies) {
                if (oneCookie != null) {
                    String cName = oneCookie.getName();
                    if (cName != null && cookieName.equals(cName)) {
                        return oneCookie.getValue();
                    }
                }
            }
        }
        return null;
    }

	public static String getBaseURL() {
		return baseURL;
	}

}
