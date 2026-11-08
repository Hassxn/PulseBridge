# PulseProxyBridge 1.1.0

Existing inventory bridge for PulseProxy, now with optional bidirectional network-event transport and PulseHCF timer publication. It does not create or replace player scoreboards.

Build: mvn -f bridge/pom.xml verify (Java 8 bytecode). Install on backends; pair with PulseProxy 1.3.0 and the coordinated core updates. The separate PulseHCFEvents adapter is retired.

[Upgrade, configuration and staging tests](NETWORK-SCOREBOARDS.md)
