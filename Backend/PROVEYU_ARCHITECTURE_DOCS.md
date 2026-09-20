# PROVEYU System Architecture & Security Documentation

This document outlines the architecture, design patterns, security model, and component interactions of the **PROVEYU** Enterprise Skills Assessment & Recruitment Backend Platform.

---

## 1. High-Level Architectural Pattern

The PROVEYU system strictly enforces a **Clean Layered Architecture** coupled with the **Lightweight Controller Pattern**:

```
[ HTTP Clients / Web & Mobile Frontend ]
          │
          ▼
[ Security & Rate Limiting Filter Chain ] ── RateLimitingFilter (IP-Bucket DDoS Protection) & JwtAuthenticationFilter
          │
          ▼
[ Controller Layer (Lightweight) ]        ── Handles HTTP Request Validation & Response Wrapping ONLY
          │
          ▼
[ Application Service Layer ]           ── 100% Business Logic, Transaction Boundaries, Orchestration
          │
          ├──► [ External Services ]        ── GoDaddy SMTP EmailService (@Async), Apache PDFBox & ZXing PDF Engine
          │
          ▼
[ Infrastructure / Repositories ]       ── Spring Data JPA, Redisson Lock Service, MySQL Database Access
```

---

## 2. Security Architecture & Rate Limiting

### 2.1 Sliding-Window IP Rate Limiting Filter (`RateLimitingFilter.java`)
To protect authentication and sensitive gateway endpoints against Brute-Force, Credential Stuffing, and Denial of Service (DDoS) attacks:
- **Filter**: `com.proveyu.shared.security.RateLimitingFilter`
- **Mechanism**: Implements an in-memory sliding-window bucket counter (`ConcurrentHashMap<String, RequestCounter>`) tracking request counts per client IP (`X-Forwarded-For` aware).
- **Rate Limit Window**: 60 requests per minute per IP on authentication routes (`/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/forgot-password`, `/api/v1/auth/verify-otp`).
- **Over-Limit Handling**: Returns HTTP `429 TOO_MANY_REQUESTS` with standard structured payload:
  ```json
  {
    "success": false,
    "message": "Too many requests. Please slow down and try again after 1 minute.",
    "errorCode": "RATE_LIMIT_EXCEEDED"
  }
  ```
- **Bucket Eviction**: Automatically purges expired minute buckets when active client count exceeds threshold to prevent memory leaks.

### 2.2 JWT Authentication & Role-Based Access Control (`JwtAuthenticationFilter.java`)
- **Stateless Session Management**: `SecurityContextHolder` is populated on every request via `JwtAuthenticationFilter`.
- **Claims Extracted**: Subject (User ID), Email, Role (`CANDIDATE`, `RECRUITER`, `ADMIN`).
- **Signature Security**: HMAC-SHA key signing with parameterized secret (`JWT_SECRET`).

---

## 3. Specialized Engines & Document Generation

### 3.1 Apache PDFBox & ZXing Binary Admit Card Engine (`AdmitCardGeneratorService.java`)
- **Engine**: Apache PDFBox (`3.0.1`) + ZXing (`3.5.3`) matrix barcode engine.
- **Output Format**: Native binary PDF (`%PDF-1.6`) with high-resolution graphics.
- **Key Features**:
  1. **Real Scannable QR Code**: Dynamically generates a 2D QR Code image (`https://proveyu.com/verify-admit-card?token=...`) embedded into PDF streams via `LosslessFactory.createFromImage`.
  2. **Code128 Barcode**: Renders linear Code128 barcode graphics across top header for gate scanner authorization.
  3. **Official Examination Layout**:
     - **Header Banner**: Dark Navy theme (`#0f172a`) with official certification branding.
     - **Candidate Information**: Roll Number (`PNRE-2026-XXXXX`), Full Name, Email, Phone, Category, and Passport Photo Box placeholder (`VERIFIED`).
     - **Test Center & Venue**: Complete venue address (`APMOSYS Tech Park, Building A, 4th Floor, Sector 11, Mahape, Navi Mumbai - 400710`), Exam Date, Reporting Time, and Gate Closure timing.
     - **Board Seal & Signatures**: Controller of Examinations seal and Candidate/Invigilator signature blocks.
     - **Rules & Conduct**: 5 mandatory examination conduct guidelines.

---

### 3.2 GoDaddy Asynchronous Email Delivery Engine (`info@proveyu.com`)
- **Service**: `com.proveyu.shared.email.EmailService`
- **Execution Mode**: Non-blocking `@Async` thread pool.
- **Templates**:
  1. **Welcome Email**: Sent on user account registration.
  2. **Password Reset OTP Email**: Delivers a 6-digit OTP token with 15-minute expiration.
  3. **Slot Booking Confirmation Email**: Includes human-readable exam slot date and time (`Monday, 21 September 2026 (10:00 AM - 11:30 AM IST)`).
  4. **Admit Card Issued Email**: Sent post-payment verification with direct PDF download link.
  5. **Recruiter Interview Invite Email**: Sent when a candidate receives an interview invitation.

---

### 3.3 Redisson Distributed Locking & MySQL Fallback (`SlotLockService.java`)
- **Primary Mechanism**: In-memory Redis distributed lock via Redisson (`slot_lock:<slotId>`) to prevent overbooking during high-concurrency booking spikes.
- **Resilient Fallback**: If Redis is offline, `SlotLockService` automatically falls back to **MySQL Pessimistic Database Locking** (`SELECT ... FOR UPDATE`).

---

### 3.4 UAN (EPFO) Candidate Experience Verification Engine
- **Service**: `com.proveyu.candidate.infrastructure.UanVerificationService`
- **Workflow**: Validates candidate UAN numbers against EPFO records and stores verified service months in `verified_backend_exp_months`.
- **Read-Optimized Index**: Indexed into `candidate_search_index` for high-throughput recruiter candidate discovery.

---

## 4. Environment & Migration Strategy

### 4.1 Environment Parameterization (`application.yml`)
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`
- `JWT_SECRET`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `UAN_VERIFICATION_URL`

### 4.2 Database Migrations (Flyway V1 - V7)
- **V1**: Initial core schema setup (`users`, `exams`, `test_centers`, `bookings`, `payments`).
- **V2**: Organization & recruiter verification schema.
- **V3**: Cleaned legacy proctoring schema.
- **V4**: Consolidated role management.
- **V5**: 6-digit OTP fields on `users`.
- **V6**: Exam sections, candidate profile tracks (`FRESHER`/`EXPERIENCED`), and recruiter salary fields.
- **V7**: Schema cleanup & MySQL database performance indexing.
