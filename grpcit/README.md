# gRPC It

Tests the use of HTTP/1.1 proxies with gRPC which uses HTTP/2

## Basics

- Swiped mostly from an online tutorial (Baeldung I think to give props)
- Main contains a client and server, exchanges one message and exits
- ProxitSelector class uses Java mechanisms for providing proxy to use when called
- Forces the use of a proxy running on port 2580

## Purpose

See if it works - and it did
