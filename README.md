ssofi-provider
==============

This is project is outdated.  OpenID has been diminished as a standard
for sharing identity, largely supplanted by OAuth2.  This old version
is no longer being maintained.

==============

OpenID based SSO provider as a Java Servlet suitable for Tomcat or other appserver.

To build a WAR simply run:

    ./gradlew

Locate the war under build/libs/ssofi-provider-<version>.war

To run with Jetty:

    ./gradlew jettyRun
