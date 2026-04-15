# Custom HTTP Web Server (Java)

A **multithreaded HTTP/1.1 web server built from scratch in Java** using low-level socket programming — without using any frameworks like Spring or Node.js.

This project demonstrates **core backend engineering concepts**, including networking, concurrency, request handling, and database logging.

---

## 🔥 Features

### 🌐 Core Server

* Built using **Java Sockets (TCP)**
* Supports **HTTP/1.1 protocol**
* Handles **GET & POST requests**
* Serves static files from `/static` directory
* Default routing (`/ → index.html`)

---

### ⚡ Concurrency

* Uses **ExecutorService (Thread Pool)**
* Handles **10 concurrent client requests**
* Efficient request queuing and processing

---

### 🧭 Routing Engine

* Custom routing logic implemented manually
* Supports:

  * `/` → index.html
  * `/page1.html`
  * `/logs` → logs dashboard
* Query parameter handling (`?key=value`)

---

### 📄 MIME Type Handling

* Automatically detects content type:

  * HTML, CSS, JS
  * Images (PNG, JPG)
  * Fallback: `application/octet-stream`

---

### 📝 HTTP Features

* Status Codes:

  * `200 OK`
  * `404 Not Found`
  * `403 Forbidden`
  * `405 Method Not Allowed`
* Proper HTTP headers:

  * Content-Type
  * Content-Length
  * Connection

---

### 📥 POST Request Handling

* Parses request body using `Content-Length`
* Handles form submissions
* Returns dynamic HTML response

---

### 🗄️ Database Logging (MySQL)

* Integrated with **MySQL using JDBC**
* Logs every request:

  * Method (GET/POST)
  * Path
  * Status code
  * Client IP
  * Timestamp

---

### 📊 Dynamic Log Tables

* Automatically creates **daily log tables**

  ```
  logs_YYYY_MM_DD
  ```
* Improves scalability and organization

---

### 📈 Logs Dashboard (`/logs`)

* Built-in web UI to view logs
* Displays:

  * Request history
  * Status codes (color-coded)
  * IP and timestamp
* Data fetched directly from database

---

### 🔒 Security

* Prevents directory traversal attacks (`../`)
* Validates file paths inside static directory

---

## 📁 Project Structure

```
CUSTOM HTTP WEB SERVER/
├── src/
│   ├── SimpleWebServer.java
│   ├── DBLogger.java
│   └── static/
│       ├── index.html
│       ├── page1.html
├── mysql-connector-j-9.x.x.jar
```

---

## ⚙️ How to Run

### 1️⃣ Compile

```bash
cd src
javac -cp ".;../mysql-connector-j-9.x.x.jar" *.java
```

### 2️⃣ Run

```bash
java -cp ".;../mysql-connector-j-9.x.x.jar" SimpleWebServer
```

### 3️⃣ Open Browser

```
http://localhost:7000/
http://localhost:7000/page1.html
http://localhost:7000/logs
```

---

## 🗄️ Database Setup

1. Open MySQL
2. Run:

```sql
CREATE DATABASE http_server;
```

Tables are created automatically when the server starts.

---

## 🧠 Key Concepts Learned

* How HTTP works over raw TCP sockets
* Parsing HTTP requests manually
* Multithreading using thread pools
* Handling concurrency in backend systems
* Building a routing system from scratch
* JDBC integration with MySQL
* Dynamic table creation and logging systems
* Serving static files securely
* Building a basic monitoring dashboard

---

## 💥 Highlights

* Built a **fully functional backend server without frameworks**
* Implemented **real-time request logging system**
* Designed **custom routing + dashboard UI**
* Demonstrates **system-level understanding (not just APIs)**

---

## 🚀 Future Improvements

* REST API support (`/api/logs` → JSON)
* Log filtering (200 / 404 / errors)
* Authentication system
* Performance metrics (response time tracking)
* Convert to **Spring Boot microservice**

---

## 👨‍💻 Author

Hemanth Giduthuri
Backend Developer | Java | Systems | Networking

---
