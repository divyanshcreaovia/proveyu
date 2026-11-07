# PROVEYU System Architecture & Security Documentation

This document outlines the architecture, design patterns, security model, and component interactions of the **PROVEYU** Enterprise Skills Assessment & Recruitment Backend Platform.

---

## Table of Contents
1. [1. High-Level Architectural Pattern](#1-high-level-architectural-pattern)
2. [2. Security Architecture & Rate Limiting](#2-security-architecture--rate-limiting)
   * [2.1 Sliding-Window IP Rate Limiting Filter (`RateLimitingFilter.java`)](#21-sliding-window-ip-rate-limiting-filter-ratelimitingfilterjava)
   * [2.2 JWT Authentication & Role-Based Access Control (`JwtAuthenticationFilter.java`)](#22-jwt-authentication--role-based-access-control-jwtauthenticationfilterjava)
3. [3. Specialized Engines & Document Generation](#3-specialized-engines--document-generation)
   * [3.1 Apache PDFBox & ZXing Binary Admit Card Engine (`AdmitCardGeneratorService.java`)](#31-apache-pdfbox--zxing-binary-admit-card-engine-admitcardgeneratorservicejava)
   * [3.2 GoDaddy Asynchronous Email Delivery Engine (`info@proveyu.com`)](#32-godaddy-asynchronous-email-delivery-engine-infoproveyucom)
   * [3.3 Redisson Distributed Locking & MySQL Fallback (`SlotLockService.java`)](#33-redisson-distributed-locking--mysql-fallback-slotlockservicejava)
   * [3.4 UAN (EPFO) Candidate Experience Verification Engine](#34-uan-epfo-candidate-experience-verification-engine)
   * [3.5 Real-Time Chat & File Messaging Engine (WebSocket / STOMP)](#35-real-time-chat--file-messaging-engine-websocket--stomp)
   * [3.6 Candidate Settings & Multi-Channel Notification Engine](#36-candidate-settings--multi-channel-notification-engine)
4. [4. Environment & Migration Strategy](#4-environment--migration-strategy)
   * [4.1 Environment Parameterization (`application.yml`)](#41-environment-parameterization-applicationyml)
   * [4.2 Database Migrations (Flyway V1 - V17)](#42-database-migrations-flyway-v1---v17)
5. [5. College Placement Management Subsystem & Dynamic Custom Roles RBAC](#5-college-placement-management-subsystem--dynamic-custom-roles-rbac)
   * [5.1 Dynamic Custom Roles (Elimination of Rigid Enum Roles)](#51-dynamic-custom-roles-elimination-of-rigid-enum-roles)
   * [5.2 Branch-Scoped Access Enforcement (`OrgUserContext`) & Context API](#52-branch-scoped-access-enforcement-orgusercontext--context-api)
   * [5.3 High-Throughput Bulk Student Onboarding Pipeline](#53-high-throughput-bulk-student-onboarding-pipeline)
   * [5.4 Placement Tracking & Analytics Engine](#54-placement-tracking--analytics-engine)
   * [5.5 Frontend Role Adaptation Architecture (Dynamic View Resolution)](#55-frontend-role-adaptation-architecture-dynamic-view-resolution)

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
- **Claims Extracted**: Subject (User ID), Email, Role (`CANDIDATE`, `RECRUITER`, `ORGANIZATION`, `ADMIN`).
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

### 3.5 Real-Time Chat & File Messaging Engine (WebSocket / STOMP)
- **Protocol & Configuration**: Spring WebSocket with STOMP sub-protocol and SockJS fallback enabled on `/ws/chat`.
- **Message Broker**: In-memory simple broker routing private user channels:
  - User queues: `/user/queue/messages`
  - Group/Topic broadcasts: `/topic/messages/{userId}`
- **Persistence & Delivery Guarantees**:
  - Entity: `ChatMessage` stored in `chat_messages` table.
  - Delivery lifecycle: `SENT` -> `DELIVERED` -> `READ` with exact `read_at` timestamps.
- **Attachment Pipeline**:
  - Multipart upload handler supporting documents (`.pdf`, `.doc`, `.docx`) and images (`.png`, `.jpg`) up to 10MB.
  - Attachments saved in `/uploads/chat` with UUID-obfuscated filenames and served through secure streaming endpoints with MIME-type headers.
- **Badge & Unread Counter Aggregation**:
  - Optimized database queries aggregate unread counts grouped by sender for real-time notification badges.

---

### 3.6 Candidate Settings & Multi-Channel Notification Engine
- **Settings Subsystem (`CandidateSettingsService`)**:
  - Entity: `candidate_settings` linked 1-to-1 with `users`.
  - Configurable notification channels: Email, SMS, Push alerts.
  - Granular event toggles: Test reminders, interview invitations, application updates, job offers, marketing announcements, newsletter.
- **Notification Subsystem (`NotificationService`)**:
  - Entity: `notifications` table storing in-app notifications.
  - Event triggers: Automatic notification creation on booking confirmation, interview invite dispatch, campus drive announcement, or placement offer issuance.
  - Read management: Mark individual notification as read (`is_read = true`) and fetch paginated user notification feed.

---

## 4. Environment & Migration Strategy

### 4.1 Environment Parameterization (`application.yml`)
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`
- `JWT_SECRET`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `UAN_VERIFICATION_URL`

### 4.2 Database Migrations (Flyway V1 - V17)
- **V1**: Initial core schema setup (`users`, `exams`, `test_centers`, `rooms`, `date_slots`, `bookings`, `payments`, `skills`, `candidate_scores`, `candidate_search_index`).
- **V2**: Organization & recruiter verification schema (`companies`, `organization_profiles`, `organization_candidates`, `interview_invites`).
- **V3**: Cleaned legacy proctoring and question bank schema.
- **V4**: Consolidated role management.
- **V5**: 6-digit OTP fields on `users` table.
- **V6**: Exam sections, candidate profile tracks (`FRESHER`/`EXPERIENCED`), and recruiter salary fields.
- **V7**: Schema cleanup & MySQL database performance indexing.
- **V8**: Real-time chat & file attachment schema (`chat_messages`).
- **V9**: Candidate notification preferences & in-app alerts schema (`candidate_settings`, `notifications`).
- **V10**: Personal info & contact extensions on candidate profiles.
- **V11**: Nullable company mapping on recruiter profiles for independent agency recruiters.
- **V12**: Extended corporate profile details (tagline, about, website, avatar).
- **V13**: Recruiter hiring volume & target placement city preferences.
- **V17**: Campus Placement Management Subsystem (`college_custom_roles`, `college_team_members`, `student_profiles`, `placement_drives`, `placement_records`).

---

## 5. College Placement Management Subsystem & Dynamic Custom Roles RBAC

### 5.1 Dynamic Custom Roles (Elimination of Rigid Enum Roles)
To support diverse organizational structures across colleges, polytechnics, and universities without rigid/hardcoded enum restrictions:
- **Clean Auth Model**: System users maintain clean core auth roles (`CANDIDATE`, `RECRUITER`, `ORGANIZATION`, `ADMIN`).
- **Zero Hardcoded Roles**: Predefined roles (`COLLEGE_TPO`, `COLLEGE_COORDINATOR`, `COLLEGE_HOD`, `COLLEGE_TEACHER`) are eliminated in favor of dynamic entries in `college_custom_roles`.
- **Custom Capability Matrix**: Each organization creates custom roles with custom names and granular permission flags:
  ```json
  {
    "roleName": "Department Placement Coordinator - CS",
    "assignedBranch": "Computer Science & Engineering",
    "canViewStudents": true,
    "canEditStudents": true,
    "canManagePlacements": true,
    "canInviteTeam": false,
    "canExportReports": true,
    "accessPercentage": "85% Access"
  }
  ```

### 5.2 Branch-Scoped Access Enforcement (`OrgUserContext`) & Context API
Security and branch scoping are resolved on every college API request via `PlacementManagementService.resolveContext(userId)`:
1. **Organization Owner / Super Admin**: Users in `organizations.user_id` possess full administrative placement control (`assignedBranch = 'ALL'`, `isHeadTpo = true`).
2. **Custom Team Member**: Looks up `college_team_members` linked to `college_custom_roles`. Permissions and branch restrictions are dynamically enforced.
3. **Cross-Branch Protection**: Coordinators or HODs scoped to a specific branch (e.g. `Information Technology`) are strictly blocked from viewing or onboarding students belonging to different branches (e.g. `Mechanical Engineering`).
4. **Context API**: `GET /api/v1/college/team/context` delivers the resolved `OrgUserContext` to frontend clients immediately post-login.

```
                ┌─────────────────────────────────┐
                │ Authenticated User Token (JWT)  │
                └────────────────┬────────────────┘
                                 │
                                 ▼
                ┌─────────────────────────────────┐
                │ OrgUserContext.resolveContext() │
                └────────────────┬────────────────┘
                                 │
        ┌────────────────────────┴────────────────────────┐
        ▼                                                 ▼
[ Organization Owner ]                         [ College Team Member ]
   - Branch: "ALL"                                - Assigned: Custom Role ID
   - Full TPO Admin Privileges                    - Branch Scoped (e.g. "CSE")
   - Can Manage Team & Roles                      - Dynamic Capability Flags
```

### 5.3 High-Throughput Bulk Student Onboarding Pipeline
- **Dual Engine Parser (`ExcelStudentParser.java`)**:
  - **Apache POI (`5.2.5`)**: Parses binary `.xlsx` and `.xls` workbooks with cell type auto-detection (String, Numeric, Formula, Blank).
  - **Apache Commons CSV (`1.10.0`)**: Ingests comma-delimited rosters with automatic UTF-8 BOM removal and case-insensitive header mapping.
- **Roster Ingestion Workflow**:
  1. Header normalization (e.g. `Roll Number` -> `rollNumber`, `Mobile Phone` -> `phone`).
  2. Batch verification: Validates academic branches against the user's branch scoping permissions.
  3. Automatic Aggregate CGPA Calculation: Computes average CGPA across active Semester 1-8 SGPAs.
  4. Automatic Tier Classification:
     - `Tier 1`: Aggregate CGPA >= 8.5
     - `Tier 2`: Aggregate CGPA >= 7.0 and < 8.5
     - `Tier 3`: Aggregate CGPA < 7.0
  5. Atomic Persistence: Creates ProveyU user account, encrypts temporary password, links `organization_candidates`, and saves `student_profiles`.

### 5.4 Placement Tracking & Analytics Engine
- **Bulk Placement Recording**: Institutional officers select multiple student profiles and apply placement offers in bulk with offer date, CTC package, package category, and bond duration.
- **KPI Dashboard Calculations**: Aggregates total students, placed count, unplaced count, overall placement percentage, average CTC LPA, highest package LPA, and branch-wise placement breakdown.

---

### 5.5 Frontend Role Adaptation Architecture (Dynamic View Resolution)
How the frontend dynamically adapts its user interface for Head TPO vs Department Coordinators vs Student Coordinators:

```
[ User Logs In (/api/v1/auth/login) ]
                 │
                 ├── role === 'CANDIDATE'    ──► Redirect /candidate/dashboard
                 ├── role === 'RECRUITER'    ──► Redirect /recruiter/dashboard
                 └── role === 'ORGANIZATION' ──► Redirect /college/dashboard
                                                       │
                                                       ▼
                                      [ Call GET /api/v1/college/team/context ]
                                                       │
                                    ┌──────────────────┴──────────────────┐
                                    ▼                                     ▼
                        { isHeadTpo: true }                  { isHeadTpo: false, assignedBranch: "IT" }
                                    │                                     │
                 ┌──────────────────┴──────────────────┐      ┌───────────┴───────────┐
                 ▼                                     ▼      ▼                       ▼
          [ Show Team Tab ]                   [ Show Roles Tab ] [ Lock Branch to IT ]   [ Hide Admin Tabs ]
          (canInviteTeam: true)                (TPO Full Access) (canAccessBranch)       (canInviteTeam: false)
```

1. **Top-Level Redirection**:
   On authentication, `res.data.role === 'ORGANIZATION'` routes the user to the College Placement Portal.
2. **Context Hydration**:
   Upon loading the portal, the frontend invokes `GET /api/v1/college/team/context`. The response populates an Angular Signal or RxJS state store containing:
   - `isHeadTpo` (boolean)
   - `assignedBranch` (string, e.g. "Computer Science & Engineering" or "ALL")
   - `canViewStudents`, `canEditStudents`, `canManagePlacements`, `canInviteTeam`, `canExportReports`
3. **Conditional UI Rendering**:
   - `*ngIf="ctx.isHeadTpo || ctx.canInviteTeam"` displays the "Team Management" and "Custom Roles" configuration tabs.
   - `*ngIf="ctx.canManagePlacements"` renders drive scheduling and offer recording action buttons.
4. **Branch Filter Locking**:
   - For **Head TPO**: The branch dropdown renders all departments (`ALL`, `CSE`, `IT`, `Mechanical`, `Civil`, `ECE`).
   - For **Branch Placement Coordinator**: The branch dropdown is locked and pre-filtered to their assigned branch (`ctx.assignedBranch`), restricting data views to authorized student records.
5. **Route Protection**:
   Angular functional guards (`canActivate: [headTpoGuard]`) guard admin-only routes against direct URL tampering, backed by zero-trust backend authorization returning HTTP 403 Forbidden.
