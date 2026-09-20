# PROVEYU — Enterprise Skills Assessment & Recruitment Platform

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-19.0-blue.svg)](https://www.oracle.com/java/)
[![Angular](https://img.shields.io/badge/Angular-Framework-red.svg)](https://angular.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-orange.svg)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-Proprietary-darkgray.svg)]()

**PROVEYU** is an enterprise-grade National Eligibility, Skills Assessment, and Automated Recruitment Platform. It provides end-to-end slot booking, automated proctoring verification, Razorpay payment gateway integration, dynamic binary PDF Admit Card issuance with real scannable QR codes, EPFO UAN candidate experience verification, and multi-template HTML email notifications.

---

## 📁 Repository Structure

```text
proveyu/
├── Backend/                 # Spring Boot 3.2.3 Java 19 Backend Service
│   ├── src/                 # Enterprise Clean-Layered Source Code
│   ├── uploads/             # Generated Admit Cards & Candidate Resumes Storage
│   ├── pom.xml              # Maven Dependencies (PDFBox, ZXing, Razorpay, Flyway)
│   ├── PROVEYU_ARCHITECTURE_DOCS.md
│   └── PROVEYU_API_SPECIFICATION_DOCS.md
│
└── Frontend/                # Angular Web Frontend Portal
    ├── src/                 # Candidate & Recruiter Dashboards, UI Components
    ├── angular.json         # Angular Build & CLI Configuration
    └── package.json         # Node Dependencies & Scripts
```

---

## 🛠️ Tech Stack & Key Modules

### ⚙️ Backend Core (`/Backend`)
* **Framework**: Spring Boot `v3.2.3` with Java `19`
* **Database**: MySQL `8.0` (Flyway Database Migrations `V1`–`V7`)
* **Security & Rate Limiting**:
  * **JWT Authentication**: Stateless Bearer Token Authorization with Role-Based Access (`CANDIDATE`, `RECRUITER`, `ADMIN`).
  * **Hybrid Rate Limiting (`RateLimitingFilter`)**: IP-bucket sliding-window rate limiting (60 req/min) on auth routes + JWT User ID rate limiting (120 req/min) on booking/payment routes.
* **Document Engine**:
  * **Apache PDFBox (`3.0.1`)**: Generates 100% binary-compliant `%PDF-1.6` Admit Cards with official examination headers, candidate details, venue addresses, and board seals.
  * **ZXing (`3.5.3`)**: Dynamically generates real scannable **2D QR Code images** (`https://proveyu.com/verify-admit-card?token=...`) and **Code128 Barcode images** embedded into PDF streams.
* **Notification System**:
  * **GoDaddy SMTP (`info@proveyu.com`)**: Asynchronous `@Async` HTML email dispatches for Registration, Password Reset OTP, Slot Booking Confirmations, Admit Card Issuance, and Recruiter Interview Invitations.
* **Concurrency & Locking**:
  * **Redisson Redis Locking (`SlotLockService`)**: High-speed distributed locks (`slot_lock:<id>`) with automated fallback to **MySQL Pessimistic Database Locking** (`SELECT ... FOR UPDATE`) if Redis is offline.

### 🌐 Frontend Core (`/Frontend`)
* **Framework**: Angular Framework
* **Components**: Candidate Portal, Exam Slot Reservation, Payment Integration, Admit Card Download Viewer, Recruiter Search Dashboard.

---

## 🚀 Quick Start Guide

### Prerequisites
* **Java**: JDK 17 or Java 19+
* **Maven**: 3.8+
* **Node.js**: v18+ & npm
* **MySQL**: 8.0+ running on port 3306/3307

---

### 1️⃣ Setting Up & Running Backend

1. **Configure Environment Variables / MySQL Credentials** (`Backend/src/main/resources/application.yml`):
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/PROVEYU?createDatabaseIfNotExist=true
       username: root
       password: YourPassword123!
     mail:
       host: smtp.godaddy.com
       port: 587
       username: info@proveyu.com
       password: YourEmailPassword
   ```

2. **Build and Run Backend**:
   ```bash
   cd Backend
   mvn clean compile
   mvn spring-boot:run
   ```
   *Backend server starts at `http://localhost:8080`*

---

### 2️⃣ Setting Up & Running Frontend

1. **Install Dependencies**:
   ```bash
   cd Frontend
   npm install
   ```

2. **Launch Angular Development Server**:
   ```bash
   npm start
   # Or using Angular CLI:
   ng serve
   ```
   *Frontend portal available at `http://localhost:4200`*

---

## 📖 Key Workflows & API Endpoints

### 1. Candidate Registration & Auth
* `POST /api/v1/auth/register` — Registers Candidate/Recruiter accounts.
* `POST /api/v1/auth/login` — Returns JWT Access Token.
* `POST /api/v1/auth/forgot-password` — Dispatches 6-digit OTP email.

### 2. Slot Reservation & Booking
* `POST /api/v1/bookings` — Places a **15-minute temporary hold (`HELD`)** on an exam slot to prevent overbooking.

### 3. Payment Verification & Admit Card Issuance
* `POST /api/v1/payments/create-order` — Creates Razorpay order.
* `POST /api/v1/payments/verify` — Verifies payment signature, confirms booking (`CONFIRMED`), generates binary PDF Admit Card with real ZXing QR code, and sends confirmation email.
* `GET /api/v1/admit-cards/download/{fileName}` — Streams official Admit Card PDF.

---

## 📑 Detailed Documentation Files

* 📜 **[Architecture & Security Guide](file:///home/rakesh.patel@apmosys.mahape/PROVEYU/proveyu/Backend/PROVEYU_ARCHITECTURE_DOCS.md)**
* 🔌 **[API Specification Docs](file:///home/rakesh.patel@apmosys.mahape/PROVEYU/proveyu/Backend/PROVEYU_API_SPECIFICATION_DOCS.md)**
* 🗄️ **[Database Schema Docs](file:///home/rakesh.patel@apmosys.mahape/PROVEYU/proveyu/Backend/PROVEYU_DATABASE_SCHEMA_DOCS.md)**

---

## 🔒 Security & Best Practices
- **Controller-Light Architecture**: Zero business logic inside controller endpoints.
- **Strict DTO Separation**: Entity models are isolated from external API responses.
- **Zero Double-Booking Guarantee**: Redisson Redis locks + MySQL Row-Level Locking prevent race conditions under peak traffic.
- **DDoS & Brute-Force Shield**: Sliding-window rate limiting on public and authenticated routes.
