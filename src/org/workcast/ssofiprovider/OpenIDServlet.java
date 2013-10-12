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

import java.io.PrintWriter;
import java.io.Writer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.workcast.streams.HTMLWriter;

/**
 * Implements an HTTPServlet for an OpenID provider
 *
 */
@SuppressWarnings("serial")
public class OpenIDServlet extends HttpServlet {

    /**
     * Servlet spec says that this can be called by several threads at the same
     * time. Don't use any member variables.
     */
    public void doGet(HttpServletRequest httpReq, HttpServletResponse resp) {
        OpenIDHandler iodh = new OpenIDHandler(httpReq, resp);
        iodh.doGet();
    }

    public void doPost(HttpServletRequest httpReq, HttpServletResponse resp) {
        doGet(httpReq, resp);
    }

    public void doPut(HttpServletRequest req, HttpServletResponse resp) {
        handleException(new Exception("Put operation not allowed on the OpenIDServlet,"), req, resp);
    }

    public void doDelete(HttpServletRequest req, HttpServletResponse resp) {
        handleException(new Exception("Delete operation not allowed on the OpenIDServlet,"), req,
                resp);
    }

    public void init(ServletConfig config) throws ServletException {

        // called method must not throw any exception, and must
        // rememboer any error encoutnered with the OpenIDHandler class
        OpenIDHandler.init(config);
    }

    private void handleException(Exception e, HttpServletRequest req, HttpServletResponse resp) {
        try {
            Writer out = resp.getWriter();
            resp.setContentType("text/html;charset=UTF-8");
            out.write("<html><body><ul><li>Exception: ");
            writeHtml(out, e.toString());
            out.write("</li></ul>\n");
            out.write("<hr/>\n");
            out.write("<a href=\"../main.jsp\" title=\"Access the main page\">Main</a>\n");
            out.write("<hr/>\n<pre>");
            e.printStackTrace(new PrintWriter(new HTMLWriter(out)));
            e.printStackTrace(new PrintWriter(System.out));
            out.write("</pre></body></html>\n");
            out.flush();
        }
        catch (Exception eeeee) {
            // nothing we can do here...
        }
    }

    public static void writeHtml(Writer w, String t) throws Exception {
        TemplateStreamer.writeHtml(w, t);
    }

}
