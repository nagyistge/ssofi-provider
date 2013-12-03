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

import java.io.Writer;

/**
 * If you wish to use the TemplateStreamer, you have to supply a
 * TemplateTokenRetriever which returns values for the specific tokens.
 * Implement a class that implements this interface, and pass an instance into
 * the TemplateStreamer.
 *
 * There is only one method to implement, writeTokenValue()
 *
 */
public interface TemplateTokenRetriever {

    /**
     * The token passed will be all text between the double curly brace
     * delimiter. This method must determine what associated value is, and write
     * it out.
     *
     * The token can be as complicated an expressions as you want, but it can
     * not have any curly brace characters in it anywhere. You might have a
     * token with multiple parameters as long as you parse out the parameters
     * and recognize their values. If your implementation recognizes the token,
     * then write the value, properly encoded, to the output stream.
     *
     * If the template is HTML, then remember to encode the value using HTML
     * encoding, perhaps by using the TemplateStreamer.writeHtml() function. If
     * you are not sure that the value needs encoding, then encode anyway,
     * because it will eliminate many forms of hacking attacks.
     *
     * Note: throwing an exception from this will cause the streaming of the
     * rest of the template to stop.
     */
    public void writeTokenValue(Writer out, String token) throws Exception;

}
