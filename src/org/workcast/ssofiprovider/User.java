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

import java.util.Vector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.workcast.mendocino.Mel;

/**
 * An XML element that represents a user
 */
public class User extends Mel {

    public User(Document doc, Element ele) {
        super(doc, ele);
    }

    public boolean hasEmail(String specAddr) {
        for (String addr : getVector("address")) {
            if (addr.equalsIgnoreCase(specAddr)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasEmailMatchingSearchTerm(String searchTerm) {
        for (String addr : getVector("address")) {
            if (addr.indexOf(searchTerm) >= 0) {
                return true;
            }
        }
        return false;
    }

    public String getEmailMatchingSearchTerm(String searchTerm) {
        for (String addr : getVector("address")) {
            // for surity changing email id and searchterm both to lower case
            if (addr.toLowerCase().indexOf(searchTerm.toLowerCase()) >= 0) {
                return addr;
            }

        }
        return null;
    }

    public Vector<String> getAddresses() {
        return getVector("address");
    }

    public void addAddress(String newAddress) {
        addVectorValue("address", newAddress);
    }

    public void removeAddress(String oldAddress) throws Exception {
        for (Mel oneAddr : getChildren("address")) {
            if (oldAddress.equalsIgnoreCase(oneAddr.getDataValue())) {
                removeChild(oneAddr);
                return;
            }
        }
    }

    public String getPassword() {
        return getScalar("password");
    }

    public void setPassword(String password) {
        setScalar("password", password);
    }

    public boolean getAdmin() {
        return "true".equals(getScalar("admin"));
    }

    public void setAdmin(boolean isTrue) {
        if (isTrue) {
            setScalar("admin", "true");
        }
        else {
            setScalar("admin", "false");
        }
    }

    public String getFullName() {
        return getScalar("fullname");
    }

    public void setFullName(String newName) {
        setScalar("fullname", newName);
    }
}
