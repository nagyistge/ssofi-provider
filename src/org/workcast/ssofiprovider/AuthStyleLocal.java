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
import java.util.Properties;
import java.util.Vector;

import javax.servlet.ServletContext;

import org.workcast.mendocino.Mel;

/**
 * Collect all the LDAP specific functionality into this class
 */
public class AuthStyleLocal implements AuthStyle {

    Mel users = null;
    File userFile;
    Vector<User> userList;
    long timestampLastRead = 0;
    String[] overridePasswords;
    boolean makeUpUsers = false;

    public AuthStyleLocal(ServletContext sc, Properties configSettings) throws Exception {

        File webInfPath = null;
        String sessionFolder = configSettings.getProperty("sessionFolder");
        if (sessionFolder != null) {
            // if sessionFolder is set, then look for the users file in that
            // folder
            webInfPath = new File(sessionFolder);
            if (!webInfPath.exists()) {
                // if it does not exist, ignore the setting
                webInfPath = null;
            }
        }
        if (webInfPath == null) {
            webInfPath = new File(sc.getRealPath("WEB-INF"));
        }

        userFile = new File(webInfPath, "users.xml");

        // handle override passwords, if any. You can specify any number
        // of passwords separated by semicolons. The passwords themselves
        // can not have a semicolon in them. e.g.
        // overridePassword=pass1;pass2;pass3
        String opass = configSettings.getProperty("overridePassword");
        if (opass == null) {
            overridePasswords = new String[0];
        }
        else {
            overridePasswords = opass.trim().split(";");
            makeUpUsers = true;
        }

        refreshUserInfo();
    }

    public void refreshUserInfo() throws Exception {

        if (userFile.exists()) {
            // if the file is no newer than last time we read it, then there
            // is no reason to read it. We already have the current info.
            if (timestampLastRead >= userFile.lastModified()) {
                return;
            }
            users = Mel.readFile(userFile, Mel.class);
        }
        else {
            users = Mel.createEmpty("users", Mel.class);
            users.writeToFile(userFile);
        }

        timestampLastRead = userFile.lastModified();
        userList = new Vector<User>();
        for (User u : users.getChildren("user", User.class)) {
            userList.add(u);
        }

    }

    public String getStyleIndicator() {
        return "local";
    }

    public boolean authenticateUser(String userNetId, String userPwd) throws Exception {

        // handle override (dummy) case
        for (String possible : overridePasswords) {
            if (possible.equals(userPwd)) {
                return true;
            }
        }

        // handle real, encrypted case
        User foundUser = findUserOrNull(userNetId);
        if (foundUser != null) {
            String storedHash = foundUser.getPassword();

            // transition hack ... the encrypted versions are long, but use it
            // as a non encrypted  password if it is short. This allows a tester
            // to set up a file for testing.
            // But in practice no short passwords will be created by the system
            if (storedHash.length() < 24) {
                return userPwd.equals(storedHash);
            }
            return PasswordEncrypter.check(userPwd, storedHash);
        }
        return false;
    }

    public UserInformation getUserInfo(String userNetId) throws Exception {
        UserInformation uret = new UserInformation();

        User foundUser = findUserOrNull(userNetId);
        uret.id = userNetId;

        if (foundUser == null) {
            if (makeUpUsers) {
                // generates a user record for any email address, just based on
                // email address
                uret.exists = true;
                uret.fullName = userNetId;
                uret.emailAddress = userNetId;
            }
            else {
                uret.exists = false;
            }
        }
        else {
            uret.exists = true;
            uret.fullName = foundUser.getFullName();
            uret.emailAddress = foundUser.getEmailMatchingSearchTerm(userNetId);
        }
        return uret;
    }

    public void setPassword(String userId, String newPwd) throws Exception {
        User foundUser = findUserOrNull(userId);
        if (foundUser == null) {
            throw new Exception("Internal consistency error: unable to find user record for: "
                    + userId);
        }
        foundUser.setPassword(PasswordEncrypter.getSaltedHash(newPwd));
        saveUserFile();
    }

    private User findUserOrNull(String userNetId) {

        for (User oneUser : userList) {
            if (oneUser.hasEmail(userNetId)) {
                return oneUser;
            }
        }

        return null;
    }

    private void saveUserFile() throws Exception {

        users.reformatXML();
        users.writeToFile(userFile);
        timestampLastRead = userFile.lastModified();
    }

    public void changePassword(String userId, String oldPwd, String newPwd) throws Exception {
        User foundUser = findUserOrNull(userId);
        if (foundUser == null) {
            throw new Exception("Internal consistency error: unable to find user record for: "
                    + userId);
        }
        String storedHash = foundUser.getPassword();
        // transition hack ... the encrypted versions are long, but use it as a
        // non encrypted
        // password if it is short. This allows a tester to set up a file for
        // testing.
        // But in practice no short passwords will be created by the system
        if (storedHash.length() < 24) {
            if (!oldPwd.equals(storedHash)) {
                throw new Exception(
                        "Unable to change password to new value, because old password value did not match our records.");
            }
        }
        else if (!PasswordEncrypter.check(oldPwd, storedHash)) {
            throw new Exception(
                    "Unable to change password to new value, because old password value did not match our records.");
        }
        foundUser.setPassword(PasswordEncrypter.getSaltedHash(newPwd));
        saveUserFile();
    }

    public boolean isAdmin(String userId) {
        User foundUser = findUserOrNull(userId);
        return foundUser.getAdmin();
    }

    public void updateUserInfo(UserInformation userInfo, String newPwd) throws Exception {
        if (!userInfo.id.equals(userInfo.id)) {
            throw new Exception(
                    "Local user authentication REQUIRES that the id and the email always be the same!");
        }
        User newUser = findUserOrNull(userInfo.id);
        if (newUser == null) {
            if (userInfo.exists) {
                throw new Exception(
                        "Don't understand attempt to update a profile that does not exist.  Clear the exist flag to false when you want to create a new profile.");
            }
            newUser = users.addChild("user", User.class);
        }
        else if (!userInfo.exists) {
            throw new Exception(
                    "Don't understand attempt to create a new profile when one already exists.  Set the exist flag to update existing profile.");
        }
        newUser.setFullName(userInfo.fullName);
        if (!newUser.hasEmail(userInfo.id)) {
            newUser.addAddress(userInfo.id);
        }
        if (newPwd != null) {
            newUser.setPassword(PasswordEncrypter.getSaltedHash(newPwd));
        }
        saveUserFile();
        userList.removeAllElements();
        userList.addAll(users.getChildren("user", User.class));
    }

    public String searchForID(String searchTerm) throws Exception {

        // first check if there is a user with an exact match
        for (User oneUser : userList) {
            if (oneUser.hasEmail(searchTerm)) {
                return searchTerm;
            }
        }

        // did not find an exact match, then search for partial strings
        for (User oneUser : userList) {
            if (oneUser.hasEmailMatchingSearchTerm(searchTerm)) {
                return oneUser.getEmailMatchingSearchTerm(searchTerm);
            }
        }

        return null;
    }

}
