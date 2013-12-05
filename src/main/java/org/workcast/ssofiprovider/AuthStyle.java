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

/**
 * An interface to represent the various possible authentication options: LDAP,
 * LocalFile, others.
 */
public interface AuthStyle {

    /**
     * Retrieve and return the information about the specified user
     * UserInformation has a 'exists' flag saying whether a profile exists or
     * not. This method should never return null. Instead, return empty record
     * with exist=false;
     */
    public UserInformation getUserInfo(String userId) throws Exception;

    /**
     * Either update or create a user profile for specified user. If the
     * password parameter is non-null, then reset the password to the specified
     * value. Used in password reset/recovery situations.
     */
    public void updateUserInfo(UserInformation user, String newPassword) throws Exception;

    /**
     * Verify that the supplied password is correct for the given user id
     */
    public boolean authenticateUser(String userNetId, String userPwd) throws Exception;

    /**
     * Verify that the supplied old password is correct for the given user id,
     * and if so set it to the supplied new password.
     */
    public void changePassword(String userId, String oldPwd, String newPwd) throws Exception;

    /**
     * Forcefully reset the password for the given user id
     */
    public void setPassword(String userId, String newPwd) throws Exception;

    /**
     * Specify whether the supplied user id is an administrator or not. Used for
     * displaying admin options that require special priviledge.
     */
    public boolean isAdmin(String userId);

    /**
     * Get a small string that can uniquely identify resources for this auth
     * style a resouce might have the name "InputScreen.htm" and
     * "InputScreen.xxx.htm" for the version specialized for the 'xxx' auth
     * style. This method returns the 'xxx' in that case.
     */
    public String getStyleIndicator();

    /**
     * Search for closest ID
     */
    public String searchForID(String searchTerm) throws Exception;
}
