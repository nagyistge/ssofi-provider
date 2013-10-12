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
 * This is the interface that a session handler must implement
 */
public interface SessionHandler {

    /**
     * pass in the session id, and get the session information back
     */
    public AuthSession getAuthSession(String sessionId) throws Exception;

    /**
     * if the session values are changed in any way, use this method to update
     * the persisted session record.
     */
    public void saveAuthSession(String sessionId, AuthSession thisSession) throws Exception;

    /**
     * call this to indicate that the session has been accessed, and to set the
     * timestamp to the current time.
     */
    public void markSessionTime(String sessionId) throws Exception;

}
