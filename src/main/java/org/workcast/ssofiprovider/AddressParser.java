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
 * Parses the address to determine the OpenId and/or the resource
 *
 * is driven by a pattern of one of these two forms:
 *
 * (1) subdomain type: http://{id}.example.com/xx (2) folder type:
 * http://example.com/xx/{id}
 *
 * There is a root address that displays introduction information Root for both
 * above: http://example.com/xx
 *
 *
 */
public class AddressParser {

    // The OpenID is constructed in this way:
    // {valueBeforeId}{id}
    //
    // User must specify a baseURL which is always the beginning of an OpenId
    // followed by the user id.
    // {baseURL}{userid}
    //
    // Entire pattern MUST be all lowercase.
    private static String valueBeforeId;

    // this is the address for future reference
    private String userId;

    // root page is the page where there is no id, the root of the servlet
    private boolean isRootAddr = false;

    public static void initialize(String pattern) throws Exception {
        valueBeforeId = OpenIDHandler.getBaseURL().toLowerCase();
    }

    /**
     * There are two major patterns, id early and id late here is an example of
     * id early:
     *
     * http://{id}.example.com/
     *
     * In this case there is always a clear before text and after text. The root
     * case is http://example.com/ An example id is http://jsmith.example.com/
     * in this case there must always be a slash on the end (because machine
     * name must have following slash) Here is a resource:
     * http://example.com/$/color.gif The resource path is:
     * http://example.com/$/
     *
     * The second case is late id, and here is an example:
     *
     * http://example.com/{id}
     *
     * In this case, there might be nothing after the id The root case is
     * http://example.com/ An example id is http://example.com/jsmith There is
     * no slash on the end Here is a resource: http://example.com/$/color.gif
     * The resource path is: http://example.com/$/
     *
     * There is a special asset path that must be recognized first, before it
     * attempts to identify an address. This can be configurable.
     */

    public AddressParser(String address) throws Exception {
        if (valueBeforeId == null) {
            throw new RuntimeException("Address Parser class has not been initialized properly, "
                    + "valueBeforeId must be set to appropriate values before "
                    + "creating any instances of the class");
        }

        // normalize to all loweracse, giving case insensitivity
        String addr = address.toLowerCase();

        if (!addr.startsWith(valueBeforeId)) {
            throw new Exception(
                    "Address Parser only works with requested ID, and that ID must start with ("
                            + valueBeforeId + "), got this instead: " + addr);
        }

        userId = addr.substring(valueBeforeId.length());

        // if this is exactly the root page, then return null string
        if (userId.length() == 0) {
            isRootAddr = true;
        }

    }

    public boolean isRoot() {
        return isRootAddr;
    }

    public String getUserId() throws Exception {
        return userId;
    }

    public String getOpenId() throws Exception {
        return valueBeforeId + userId;
    }

    public static String composeOpenId(String newUser) throws Exception {
        return valueBeforeId + newUser;
    }
}
