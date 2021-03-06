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

import java.util.Hashtable;

/**
 * This saves the sessions in a hash table in memory
 */
public class SessionHandlerMemory implements SessionHandler {

    Hashtable<String, AuthSession> allSessions = new Hashtable<String, AuthSession>();
    Hashtable<String, Long> allTimes = new Hashtable<String, Long>();

    public AuthSession getAuthSession(String sessionId) throws Exception {
        // this represents the earliest time value where the entry could still be valid
        long oneHourAgo = System.currentTimeMillis() - 3600000;
        Long thisTime = allTimes.get(sessionId);
        AuthSession thisSession = null;
        if (thisTime != null && thisTime.longValue() > oneHourAgo) {
            thisSession = allSessions.get(sessionId);
        }

        if (thisSession == null) {
            thisSession = new AuthSession();
            saveAuthSession(sessionId, thisSession);
        }
        // return a copy of this to make sure that update is being done properly
        // and so this memory version is a valid test of the file version
        return thisSession.copy();
    }

    public void saveAuthSession(String sessionId, AuthSession thisSession) throws Exception {
        allSessions.put(sessionId, thisSession);
        allTimes.put(sessionId, new Long(System.currentTimeMillis()));
    }

    /**
     * In this implementation, all we need to do is to save the new timestamp
     */
    public void markSessionTime(String sessionId) throws Exception {
        allTimes.put(sessionId, new Long(System.currentTimeMillis()));
    }

}
