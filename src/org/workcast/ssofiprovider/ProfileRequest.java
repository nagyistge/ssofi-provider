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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.workcast.mendocino.Mel;

/**
 * An XML element that represents a user
 */
public class ProfileRequest extends Mel {

    public ProfileRequest(Document doc, Element ele) {
        super(doc, ele);
    }

    /**
     * Must be unique within the context that this request is being stored.
     */
    public String getId() {
        return getScalar("id");
    }

    public void setId(String id) {
        setScalar("id", id);
    }

    /**
     * This is a global unique id designed simply to be hard to guess This must
     * be kept secret, never displayed in the user interface, but sent in an
     * email message in order to prove that they got the email message.
     */
    public String getSecurityToken() {
        return getScalar("token");
    }

    public void setSecurityToken(String token) {
        setScalar("token", token);
    }

    public String getEmail() {
        return getScalar("email");
    }

    public void setEmail(String email) {
        setScalar("email", email);
    }

    /**
     * The time that the request was created, for use in timing out the request
     * after a period of time (24 hours).
     */
    public long getTimestamp() {
        // return safeConvertLong(getScalar("timestamp"));
        throw new RuntimeException("should not be calling getTimestamp");
    }

    /**
     * Tells what kind of request it is RESET_PASSWORD = 1; REGISTER_PROFILE =
     * 2;
     */
    public int getReqType() {
        return safeConvertInt(getScalar("type"));
    }

    public void setReqType(int newType) {
        setScalar("type", Integer.toString(newType));
    }

    /**
     * designed primarily for returning date long values works only for positive
     * integer (long) values considers all numeral, ignores all letter and
     * punctuation never throws an exception if you give this something that is
     * not a number, you get surprising result. Zero if no numerals at all.
     */
    public static int safeConvertInt(String val) {
        if (val == null) {
            return 0;
        }
        int res = 0;
        int last = val.length();
        for (int i = 0; i < last; i++) {
            char ch = val.charAt(i);
            if (ch >= '0' && ch <= '9') {
                res = res * 10 + ch - '0';
            }
        }
        return res;
    }

    public static Document createDocument(String rootNodeName) throws Exception {
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        dfactory.setNamespaceAware(true);
        dfactory.setValidating(false);
        DocumentBuilder bldr = dfactory.newDocumentBuilder();
        Document doc = bldr.newDocument();
        Element rootEle = doc.createElement(rootNodeName);
        doc.appendChild(rootEle);
        return doc;
    }

}
