# Custom HTTP Web Server

A multi-threaded HTTP/1.1 web server built from scratch in Java 
using socket programming — no external frameworks.

## Features
- Thread pool (ExecutorService) handling 10 concurrent connections
- URL routing engine (GET /index, /about, /contact)
- MIME type detection (HTML, CSS, JS, JSON)
- HTTP status codes: 200 OK, 404 Not Found, 500 Internal Server Error
- POST request body parsing
- Request logging with timestamps and client IP

## How to Run
```bash
javac SimpleWebServer.java
java SimpleWebServer
# Visit http://localhost:7000
```

## What I Learned
- How browsers and servers communicate over TCP
- Why thread pools are better than new Thread() per request
- How HTTP headers control content rendering
