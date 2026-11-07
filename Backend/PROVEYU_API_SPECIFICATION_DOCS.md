# PROVEYU API Specification & Payload Documentation

This document provides a comprehensive, production-grade specification of every REST API in the **PROVEYU** backend platform, including URI paths, HTTP methods, authorization headers, request parameters, request JSON payloads, and response JSON formats.

---

## Table of Contents
1. [Standard API Response Format (`ApiResponse<T>`)](#standard-api-response-format-apiresponset)
2. [1. Authentication & Session Module (`/api/v1/auth`)](#1-authentication--session-module-apiv1auth)
   * 1.1 User Registration (`POST /register`)
   * 1.2 User Login (`POST /login`)
   * 1.3 Request Password Reset OTP (`POST /forgot-password`)
   * 1.4 Verify Reset OTP Token (`POST /verify-otp`)
   * 1.5 Set New Password (`POST /reset-password`)
   * 1.6 User Logout (`POST /logout`)
3. [2. Candidate Profile & Performance Passport Module (`/api/v1/candidates`)](#2-candidate-profile--performance-passport-module-apiv1candidates)
   * 2.1 Upsert Candidate Profile (`POST /profile`)
   * 2.2 Get Candidate Profile (`GET /profile`)
   * 2.3 Get Candidate Score Passport (`GET /passport`)
   * 2.4 Upload Candidate PDF Resume (`POST /resume/upload`)
   * 2.5 View / Download Candidate Resume File (`GET /resume/download/{filename}`)
4. [3. Candidate Settings & Notifications Module (`/api/v1/candidates/settings`, `/api/v1/notifications`)](#3-candidate-settings--notifications-module)
   * 3.1 Get User Notification Settings (`GET /candidates/settings`)
   * 3.2 Update User Notification Settings (`PUT /candidates/settings`)
   * 3.3 Get In-App Notifications Feed (`GET /notifications`)
   * 3.4 Mark In-App Notification as Read (`PUT /notifications/{id}/read`)
5. [4. Assessment & Examination Module (`/api/v1/exams`)](#4-assessment--examination-module-apiv1exams)
   * 4.1 List Active Assessment Exams (`GET /exams`)
6. [5. Slot Booking, Payments & Admit Card Issuance Module (`/api/v1/bookings`, `/api/v1/payments`, `/api/v1/admit-cards`)](#5-slot-booking-payments--admit-card-issuance-module)
   * 5.1 Reserve Exam Slot (`POST /bookings`)
   * 5.2 Create UPI / Gateway Payment Order (`POST /payments/create-order`)
   * 5.3 Verify Payment & Issue Dynamic PDF Admit Card (`POST /payments/verify`)
   * 5.4 Fetch Booking Admit Card Metadata (`GET /payments/admit-card/{bookingId}`)
   * 5.5 Download Scannable Admit Card PDF (`GET /admit-cards/download/{filename}`)
   * 5.6 Razorpay Asynchronous Payment Webhook (`POST /payments/webhook`)
7. [6. Recruiter Candidate Discovery & Corporate Verification Module (`/api/v1/recruiters`)](#6-recruiter-candidate-discovery--corporate-verification-module)
   * 6.1 Search Verified Candidates (`GET /recruiters/candidates`)
   * 6.2 Get Recruiter Corporate Profile (`GET /recruiters/profile`)
   * 6.3 Update Recruiter Corporate Profile (`PUT /recruiters/profile`)
   * 6.4 Verify Company GSTIN (`POST /recruiters/companies/verify-gst`)
8. [7. Recruiter Interview Invitations Module (`/api/v1/interviews`)](#7-recruiter-interview-invitations-module-apiv1interviews)
   * 7.1 Dispatch Interview Invitation (`POST /interviews/invite`)
   * 7.2 Candidate View Received Interview Invites (`GET /interviews/candidate-invites`)
   * 7.3 Recruiter View Sent Interview Invites (`GET /interviews/sent`)
   * 7.4 Candidate Respond to Interview Invite (`PUT /interviews/{inviteId}/respond`)
   * 7.5 Update Candidate Placement Status (`PUT /interviews/{inviteId}/placement-status`)
9. [8. Real-Time Chat & File Messaging Module (`/api/v1/chat`, `/ws/chat`)](#8-real-time-chat--file-messaging-module-apiv1chat-wschat)
   * 8.1 Send Text Chat Message (`POST /chat/messages`)
   * 8.2 Send File Attachment Message (`POST /chat/messages/file`)
   * 8.3 Get 1-on-1 Conversation History (`GET /chat/messages/{userId}`)
   * 8.4 Get Unread Messages List (`GET /chat/unread`)
   * 8.5 Get Unread Notification Summary (`GET /chat/notifications`)
   * 8.6 Mark Message as Read (`PATCH /chat/messages/{messageId}/read`)
   * 8.7 Download Chat File Attachment (`GET /chat/messages/{messageId}/file`)
10. [9. Institutional Organization Accounts (`/api/v1/organizations`)](#9-institutional-organization-accounts-apiv1organizations)
    * 9.1 Create or Update Organization Profile (`POST /organizations/profile`)
    * 9.2 Onboard Candidate Under Organization (`POST /organizations/candidates`)
    * 9.3 Get Enrolled Candidates (`GET /organizations/candidates`)
11. [10. College Placement Management Subsystem & Dynamic Custom Roles (`/api/v1/college`)](#10-college-placement-management-subsystem--dynamic-custom-roles)
    * 10.1 Resolve Logged-In User Placement Context (`GET /college/team/context`)
    * 10.2 Download Student Import Roster Template (`GET /college/students/template`)
    * 10.3 Bulk Student Roster Upload & Ingestion (`POST /college/students/bulk-upload`)
    * 10.4 Get Paginated Student Roster (`GET /college/students`)
    * 10.5 Get Individual Student Profile (`GET /college/students/{studentId}`)
    * 10.6 Create Student Profile Manually (`POST /college/students`)
    * 10.7 Update Student Profile (`PUT /college/students/{studentId}`)
    * 10.8 Bulk Record Placement Offers (`POST /college/students/bulk-placement`)
    * 10.9 Get Student Self Profile (`GET /college/students/my-profile`)
    * 10.10 Update Student Self Profile (`PUT /college/students/my-profile`)
    * 10.11 Placement KPI Dashboard Analytics (`GET /college/students/dashboard/kpi`)
    * 10.12 List College Placement Drives (`GET /college/placements`)
    * 10.13 Get Placement Drive Details (`GET /college/placements/{driveId}`)
    * 10.14 Schedule New Placement Drive (`POST /college/placements`)
    * 10.15 Update Placement Drive (`PUT /college/placements/{driveId}`)
    * 10.16 Delete Placement Drive (`DELETE /college/placements/{driveId}`)
    * 10.17 List College Placement Team Members (`GET /college/team`)
    * 10.18 Invite Placement Team Member (`POST /college/team/invite`)
    * 10.19 Update Team Member Privileges (`PUT /college/team/{memberId}`)
    * 10.20 Remove Team Member (`DELETE /college/team/{memberId}`)
    * 10.21 List Defined Custom Dynamic Roles (`GET /college/roles`)
    * 10.22 Get Custom Dynamic Role by ID (`GET /college/roles/{roleId}`)
    * 10.23 Create Custom Dynamic Role (`POST /college/roles`)
    * 10.24 Update Custom Dynamic Role (`PUT /college/roles/{roleId}`)
    * 10.25 Delete Custom Dynamic Role (`DELETE /college/roles/{roleId}`)
12. [11. Platform Administration Module (`/api/v1/admin`)](#11-platform-administration-module-apiv1admin)
    * 11.1 Create System Administrator (`POST /admin/users/create-admin`)
    * 11.2 Configure Examination Catalog (`POST /admin/exams`)
    * 11.3 Get Global Platform Statistics (`GET /admin/stats`)

---

## Standard API Response Format (`ApiResponse<T>`)

Every API across the PROVEYU backend delivers responses encapsulated within a standardized JSON envelope:

```json
{
  "success": true,
  "message": "Operation summary message",
  "data": { ... },
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:00Z"
}
```

When an error occurs, `success` evaluates to `false`, `data` is `null`, and `errorCode` identifies the domain error constraint:

```json
{
  "success": false,
  "message": "Too many requests. Please slow down and try again after 1 minute.",
  "data": null,
  "errorCode": "RATE_LIMIT_EXCEEDED",
  "timestamp": "2026-10-20T18:00:00Z"
}
```

---

## 1. Authentication & Session Module (`/api/v1/auth`)

### 1.1 User Registration
* **Endpoint**: `POST /api/v1/auth/register`
* **Security**: Public
* **Headers**: `Content-Type: application/json`
* **Request Payload**:
```json
{
  "email": "candidate_proveyu@example.com",
  "password": "Password123!",
  "fullName": "Aarav Sharma",
  "phone": "+919876543210",
  "role": "CANDIDATE"
}
```
* **Success Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJiMDgwM2M2OC05MGQ3LTQyNmYtOTFhMS1iYmY0NzJhMjY5ZTQiLCJlbWFpbCI6ImNhbmRpZGF0ZV9wcm92ZXl1QGV4YW1wbGUuY29tIiwicm9sZSI6IkNBTkRJREFURSJ9...",
    "userId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "email": "candidate_proveyu@example.com",
    "fullName": "Aarav Sharma",
    "role": "CANDIDATE"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:00Z"
}
```

---

### 1.2 User Login
* **Endpoint**: `POST /api/v1/auth/login`
* **Security**: Public
* **Headers**: `Content-Type: application/json`
* **Request Payload**:
```json
{
  "email": "candidate_proveyu@example.com",
  "password": "Password123!"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "User authenticated successfully",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJiMDgwM2M2OC05MGQ3LTQyNmYtOTFhMS1iYmY0NzJhMjY5ZTQiLCJlbWFpbCI6ImNhbmRpZGF0ZV9wcm92ZXl1QGV4YW1wbGUuY29tIiwicm9sZSI6IkNBTkRJREFURSJ9...",
    "userId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "email": "candidate_proveyu@example.com",
    "fullName": "Aarav Sharma",
    "role": "CANDIDATE"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:05Z"
}
```

---

### 1.3 Request Password Reset OTP
* **Endpoint**: `POST /api/v1/auth/forgot-password`
* **Security**: Public
* **Headers**: `Content-Type: application/json`
* **Request Payload**:
```json
{
  "email": "candidate_proveyu@example.com"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Password reset instructions sent",
  "data": {
    "email": "candidate_proveyu@example.com",
    "resetToken": "829104",
    "message": "Password reset token generated. Valid for 15 minutes."
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:10Z"
}
```

---

### 1.4 Verify Reset OTP Token
* **Endpoint**: `POST /api/v1/auth/verify-otp`
* **Security**: Public
* **Headers**: `Content-Type: application/json`
* **Request Payload**:
```json
{
  "email": "candidate_proveyu@example.com",
  "otp": "829104"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "OTP verified successfully",
  "data": true,
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:15Z"
}
```

---

### 1.5 Set New Password
* **Endpoint**: `POST /api/v1/auth/reset-password`
* **Security**: Public
* **Headers**: `Content-Type: application/json`
* **Request Payload**:
```json
{
  "resetToken": "829104",
  "newPassword": "NewSecurePassword123!"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Password reset successful",
  "data": "Password has been reset successfully",
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:20Z"
}
```

---

### 1.6 User Logout
* **Endpoint**: `POST /api/v1/auth/logout`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null,
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:25Z"
}
```

---

## 2. Candidate Profile & Performance Passport Module (`/api/v1/candidates`)

### 2.1 Upsert Candidate Profile
* **Endpoint**: `POST /api/v1/candidates/profile`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "fullName": "Aarav Patel",
  "phone": "+91 98765 43210",
  "experienceTrack": "EXPERIENCED",
  "yearsOfExperience": 4,
  "uanNumber": "100918273645",
  "collegeName": null,
  "degreeBranch": null,
  "passoutYear": null,
  "location": "Bengaluru, Karnataka",
  "headline": "Senior Software Engineer",
  "linkedinUrl": "https://linkedin.com/in/aarav",
  "githubUrl": "https://github.com/aarav",
  "portfolioUrl": "https://aarav.dev",
  "bio": "Passionate about high-throughput Spring Boot microservices.",
  "skillsList": "Java, Spring Boot, MySQL, Microservices",
  "resumeUrl": "https://s3.amazonaws.com/proveyu-resumes/aarav.pdf"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Candidate profile updated successfully",
  "data": {
    "id": "c54b7323-0f1e-46f2-aff8-f0da2631b86e",
    "userId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "fullName": "Aarav Patel",
    "email": "aarav@example.com",
    "phone": "+91 98765 43210",
    "experienceTrack": "EXPERIENCED",
    "yearsOfExperience": 4,
    "uanNumber": "100918273645",
    "collegeName": null,
    "degreeBranch": null,
    "passoutYear": null,
    "location": "Bengaluru, Karnataka",
    "headline": "Senior Software Engineer",
    "linkedinUrl": "https://linkedin.com/in/aarav",
    "githubUrl": "https://github.com/aarav",
    "portfolioUrl": "https://aarav.dev",
    "bio": "Passionate about high-throughput Spring Boot microservices.",
    "skillsList": "Java, Spring Boot, MySQL, Microservices",
    "resumeUrl": "https://s3.amazonaws.com/proveyu-resumes/aarav.pdf",
    "updatedAt": "2026-10-20T18:00:30Z"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:30Z"
}
```

---

### 2.2 Get Candidate Profile
* **Endpoint**: `GET /api/v1/candidates/profile`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`
* **Success Response (`200 OK`)**: Same data model as `2.1 Upsert Candidate Profile`.

---

### 2.3 Get Candidate Score Passport
* **Endpoint**: `GET /api/v1/candidates/passport`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Candidate score passport retrieved successfully",
  "data": {
    "candidateId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "fullName": "Aarav Sharma",
    "email": "candidate_proveyu@example.com",
    "experienceTrack": "EXPERIENCED",
    "overallScorePercentage": 88.5,
    "verificationStatus": "VERIFIED_PASSED",
    "skillScores": [
      {
        "skillId": "e1122334-5566-7788-9900-aabbccddeeff",
        "skillName": "Java",
        "averagePercentage": 92.0,
        "attemptsCount": 2
      }
    ],
    "examHistory": [
      {
        "scoreId": "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
        "examId": "f9e8d7c6-b5a4-3210-9876-54321fedcba9",
        "examTitle": "Senior Java Architect Exam",
        "score": 92.0,
        "maxScore": 100.0,
        "percentage": 92.0,
        "passed": true
      }
    ]
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:35Z"
}
```

---

### 2.4 Upload Candidate PDF Resume
* **Endpoint**: `POST /api/v1/candidates/resume/upload`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`, `Content-Type: multipart/form-data`
* **Form Field**: `file` (Multipart file binary: `.pdf`, `.doc`, `.docx`, max size: 5MB)
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "PDF Resume uploaded and linked successfully",
  "data": {
    "id": "c54b7323-0f1e-46f2-aff8-f0da2631b86e",
    "userId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "resumeUrl": "/api/v1/candidates/resume/download/resume_b0803c68-90d7-426f-91a1-bbf472a269e4_1789925847709.pdf",
    "updatedAt": "2026-10-20T18:00:40Z"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:40Z"
}
```

---

### 2.5 View / Download Candidate Resume File
* **Endpoint**: `GET /api/v1/candidates/resume/download/{filename}`
* **Security**: Public
* **Response Headers**: `Content-Type: application/pdf`, `Content-Disposition: inline; filename="..."`
* **Body**: Raw document binary stream.

---

## 3. Candidate Settings & Notifications Module

### 3.1 Get User Notification Settings
* **Endpoint**: `GET /api/v1/candidates/settings`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Settings retrieved successfully",
  "data": {
    "emailNotifications": true,
    "smsNotifications": false,
    "pushNotifications": true,
    "testReminders": true,
    "interviewInvites": true,
    "applicationUpdates": true,
    "jobOffers": true,
    "marketingUpdates": false,
    "newsletter": false
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:45Z"
}
```

---

### 3.2 Update User Notification Settings
* **Endpoint**: `PUT /api/v1/candidates/settings`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**: (Same fields as `3.1`)
* **Success Response (`200 OK`)**: Echoes updated settings object.

---

### 3.3 Get In-App Notifications Feed
* **Endpoint**: `GET /api/v1/notifications`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Notifications retrieved successfully",
  "data": [
    {
      "id": "c54b7323-0f1e-46f2-aff8-f0da2631b86e",
      "type": "INTERVIEW_INVITE",
      "message": "You received an interview invite for Lead Backend Engineer from Acme Technologies",
      "isRead": false,
      "createdAt": "2026-10-20T17:30:00Z"
    }
  ],
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:50Z"
}
```

---

### 3.4 Mark In-App Notification as Read
* **Endpoint**: `PUT /api/v1/notifications/{id}/read`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Notification marked as read",
  "data": null,
  "errorCode": null,
  "timestamp": "2026-10-20T18:00:55Z"
}
```

---

## 4. Assessment & Examination Module (`/api/v1/exams`)

### 4.1 List Active Assessment Exams
* **Endpoint**: `GET /api/v1/exams`
* **Security**: Public
* **Success Response (`200 OK`)**:
```json
[
  {
    "id": "f9e8d7c6-b5a4-3210-9876-54321fedcba9",
    "title": "National Graduate Engineering Aptitude & Logic Assessment",
    "description": "Standardized evaluation testing Core Logic, Systems Architecture, and Code Quality",
    "durationMinutes": 90,
    "totalMarks": 100.0,
    "passingScore": 60.0,
    "feeAmount": 499.0,
    "active": true
  }
]
```

---

## 5. Slot Booking, Payments & Admit Card Issuance Module

### 5.1 Reserve Exam Slot
* **Endpoint**: `POST /api/v1/bookings`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "slotId": "a1b2c3d4-e5f6-7890-1234-56789abcdef0"
}
```
* **Success Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "Slot reserved successfully for 15 minutes",
  "data": {
    "id": "bf24140d-5b09-40b3-963d-7a7544952a99",
    "candidateId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "slotId": "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
    "status": "HELD",
    "heldAt": "2026-10-20T18:01:00Z",
    "expiresAt": "2026-10-20T18:16:00Z",
    "confirmedAt": null
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:00Z"
}
```

---

### 5.2 Create UPI / Gateway Payment Order
* **Endpoint**: `POST /api/v1/payments/create-order`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "bookingId": "bf24140d-5b09-40b3-963d-7a7544952a99",
  "amountCents": 49900
}
```
* **Success Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "Payment order created successfully",
  "data": {
    "orderId": "order_mock_cf4a8cc051da46",
    "amountCents": 49900,
    "currency": "INR",
    "keyId": "rzp_test_mockKey123"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:05Z"
}
```

---

### 5.3 Verify Payment & Issue Dynamic PDF Admit Card
* **Endpoint**: `POST /api/v1/payments/verify`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "orderId": "order_mock_cf4a8cc051da46",
  "paymentId": "pay_mock_123",
  "signature": "mock_signature_sha256"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Payment verified and admit card issued successfully",
  "data": {
    "id": "adb646d3-0012-42f5-9a0c-d41c5619aef5",
    "bookingId": "bf24140d-5b09-40b3-963d-7a7544952a99",
    "qrCodeToken": "QR_PROVEYU_d04cd1b6-bd01-44b0-802d-00d4501e3ff7",
    "barcodeValue": "BC1789926479843",
    "issuedAt": "2026-10-20T18:01:10Z",
    "pdfUrl": "/api/v1/admit-cards/download/admit_card_bf24140d-5b09-40b3-963d-7a7544952a99.pdf"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:10Z"
}
```

---

### 5.4 Fetch Booking Admit Card Metadata
* **Endpoint**: `GET /api/v1/payments/admit-card/{bookingId}`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: Returns `AdmitCard` object with QR token, barcode value, and download link.

---

### 5.5 Download Scannable Admit Card PDF
* **Endpoint**: `GET /api/v1/admit-cards/download/{filename}`
* **Security**: Public
* **Response Headers**: `Content-Type: application/pdf`, `Content-Disposition: inline; filename="admit_card_{bookingId}.pdf"`
* **Body**: High-resolution binary `%PDF-1.6` document generated by Apache PDFBox and ZXing.

---

### 5.6 Razorpay Asynchronous Payment Webhook
* **Endpoint**: `POST /api/v1/payments/webhook`
* **Headers**: `X-Razorpay-Signature: <hmac_sha256_signature>`
* **Request Payload**:
```json
{
  "event": "order.paid",
  "payload": {
    "order": {
      "entity": {
        "id": "order_mock_cf4a8cc051da46"
      }
    }
  }
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "WEBHOOK_PROCESSED",
  "data": "Webhook processed successfully",
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:15Z"
}
```

---

## 6. Recruiter Candidate Discovery & Corporate Verification Module

### 6.1 Search Verified Candidates
* **Endpoint**: `GET /api/v1/recruiters/candidates?skillId={uuid}&page=0&size=20`
* **Security**: Authenticated (`Role.RECRUITER`)
* **Headers**: `Authorization: Bearer <recruiter_token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Candidate search completed successfully",
  "data": {
    "content": [
      {
        "id": "e8a1b2c3-4d5e-6f7a-8b9c-0d1e2f3a4b5c",
        "candidateId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
        "candidateName": "Aarav Sharma",
        "experienceTrack": "EXPERIENCED",
        "verifiedExperienceMonths": 48,
        "overallScorePercentage": 91.5,
        "verificationStatus": "VERIFIED_PASSED",
        "skillsIndexed": "Java, Spring Boot, MySQL, Kafka, Docker",
        "lastUpdatedAt": "2026-10-20T18:00:00Z"
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 20 },
    "totalElements": 1,
    "totalPages": 1
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:20Z"
}
```

---

### 6.2 Get Recruiter Corporate Profile
* **Endpoint**: `GET /api/v1/recruiters/profile`
* **Security**: Authenticated (`Role.RECRUITER`)
* **Headers**: `Authorization: Bearer <recruiter_token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Profile fetched",
  "data": {
    "contactName": "Vikram Malhotra",
    "workEmail": "vikram@acmetech.com",
    "phone": "+91 98765 11111",
    "companyName": "Acme Technologies Private Limited",
    "city": "Mumbai",
    "orgType": "corporate",
    "website": "https://acmetech.com",
    "headline": "Scaling Next-Gen Fintech Solutions",
    "about": "Acme Tech is an enterprise cloud engineering firm.",
    "avatar": "https://s3.amazonaws.com/acme/logo.png",
    "linkedin": "https://linkedin.com/company/acmetech",
    "hiringVolume": "50+ hires/quarter",
    "candidateLevel": "Mid to Senior Engineers",
    "primaryTrack": "EXPERIENCED",
    "preferredCities": "Mumbai, Bengaluru, Pune",
    "customNotes": "Prioritize candidates with strong distributed systems design scores."
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:25Z"
}
```

---

### 6.3 Update Recruiter Corporate Profile
* **Endpoint**: `PUT /api/v1/recruiters/profile`
* **Security**: Authenticated (`Role.RECRUITER`)
* **Headers**: `Authorization: Bearer <recruiter_token>`, `Content-Type: application/json`
* **Request Payload**: (Map of profile keys to be updated)
* **Success Response (`200 OK`)**: Echoes updated profile map.

---

### 6.4 Verify Company GSTIN
* **Endpoint**: `POST /api/v1/recruiters/companies/verify-gst`
* **Security**: Authenticated (`Role.RECRUITER`)
* **Headers**: `Content-Type: application/json`
* **Request Payload**:
```json
{
  "legalName": "Acme Technologies Private Limited",
  "tradeName": "Acme Tech",
  "gstin": "27AAAAA0000A1Z5",
  "registeredAddress": "101 Cyber City, Bandra Kurla Complex",
  "state": "Maharashtra",
  "websiteDomain": "acmetech.com"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Company GST verified successfully",
  "data": {
    "id": "c1d2e3f4-5678-9012-3456-7890abcdef12",
    "legalName": "Acme Technologies Private Limited",
    "tradeName": "Acme Tech",
    "gstin": "27AAAAA0000A1Z5",
    "verificationStatus": "VERIFIED",
    "verifiedAt": "2026-10-20T18:01:30Z"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:30Z"
}
```

---

## 7. Recruiter Interview Invitations Module (`/api/v1/interviews`)

### 7.1 Dispatch Interview Invitation
* **Endpoint**: `POST /api/v1/interviews/invite`
* **Security**: Authenticated (`Role.RECRUITER`)
* **Headers**: `Authorization: Bearer <recruiter_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "candidateId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
  "companyId": "c1d2e3f4-5678-9012-3456-7890abcdef12",
  "examId": "f9e8d7c6-b5a4-3210-9876-54321fedcba9",
  "jobTitle": "Lead Backend Engineer",
  "jobDescription": "Designing distributed Spring Boot microservices",
  "minSalaryLpa": 18.0,
  "maxSalaryLpa": 24.0,
  "packageRange": "18.0 - 24.0 LPA"
}
```
* **Success Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "Interview invitation sent successfully",
  "data": {
    "id": "5207a672-7fed-40b5-b832-2ae6f9326e92",
    "recruiterId": "4431656b-3fad-43e8-96ce-531263de5f40",
    "candidateId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "jobTitle": "Lead Backend Engineer",
    "jobDescription": "Designing distributed Spring Boot microservices",
    "packageRange": "18.0 - 24.0 LPA",
    "status": "PENDING",
    "placementStatus": "IN_PROCESS",
    "createdAt": "2026-10-20T18:01:35Z"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:35Z"
}
```

---

### 7.2 Candidate View Received Interview Invites
* **Endpoint**: `GET /api/v1/interviews/candidate-invites`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`
* **Success Response (`200 OK`)**: List of `InterviewInvite` records addressed to logged-in candidate.

---

### 7.3 Recruiter View Sent Interview Invites
* **Endpoint**: `GET /api/v1/interviews/sent`
* **Security**: Authenticated (`Role.RECRUITER`)
* **Headers**: `Authorization: Bearer <recruiter_token>`
* **Success Response (`200 OK`)**: List of `InterviewInvite` records dispatched by recruiter.

---

### 7.4 Candidate Respond to Interview Invite
* **Endpoint**: `PUT /api/v1/interviews/{inviteId}/respond`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "accept": true
}
```
* **Success Response (`200 OK`)**: Returns updated `InterviewInvite` with status `ACCEPTED` or `DECLINED`.

---

### 7.5 Update Candidate Placement Status
* **Endpoint**: `PUT /api/v1/interviews/{inviteId}/placement-status`
* **Security**: Authenticated (`Role.RECRUITER`)
* **Headers**: `Authorization: Bearer <recruiter_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "placementStatus": "HIRED"
}
```
* **Success Response (`200 OK`)**: Returns updated `InterviewInvite` with placement status `HIRED` or `REJECTED`.

---

## 8. Real-Time Chat & File Messaging Module (`/api/v1/chat`, `/ws/chat`)

### 8.1 Send Text Chat Message
* **Endpoint**: `POST /api/v1/chat/messages`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
  "message": "Hello Aarav, your technical evaluation scores are impressive. Let's connect for an interview."
}
```
* **Success Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "Message sent successfully",
  "data": {
    "id": "7a8b9c0d-1e2f-3a4b-5c6d-7e8f9a0b1c2d",
    "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
    "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "message": "Hello Aarav, your technical evaluation scores are impressive. Let's connect for an interview.",
    "messageType": "TEXT",
    "status": "SENT",
    "createdAt": "2026-10-20T18:01:40Z"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:40Z"
}
```

---

### 8.2 Send File Attachment Message
* **Endpoint**: `POST /api/v1/chat/messages/file`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: multipart/form-data`
* **Form Fields**:
  * `receiverId`: UUID
  * `message` (optional): String text caption
  * `file`: Multipart file binary (Max: 10MB)
* **Success Response (`201 Created`)**: Returns `ChatMessageResponse` with `messageType: "FILE"`, file name, size, and download link.

---

### 8.3 Get 1-on-1 Conversation History
* **Endpoint**: `GET /api/v1/chat/messages/{userId}?page=0&size=50`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: Paginated list of chat messages ordered chronologically.

---

### 8.4 Get Unread Messages List
* **Endpoint**: `GET /api/v1/chat/unread`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: List of unread messages for the logged-in user.

---

### 8.5 Get Unread Notification Summary
* **Endpoint**: `GET /api/v1/chat/notifications`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Unread notifications retrieved successfully",
  "data": {
    "totalUnreadCount": 3,
    "senderSummaries": [
      {
        "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
        "senderName": "Acme Technologies Recruiter",
        "unreadCount": 3,
        "lastMessageSnippet": "Let's connect for an interview.",
        "lastMessageTime": "2026-10-20T18:01:40Z"
      }
    ]
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:45Z"
}
```

---

### 8.6 Mark Message as Read
* **Endpoint**: `PATCH /api/v1/chat/messages/{messageId}/read`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: Returns `ChatMessageResponse` with status `READ` and `readAt` timestamp.

---

### 8.7 Download Chat File Attachment
* **Endpoint**: `GET /api/v1/chat/messages/{messageId}/file`
* **Security**: Authenticated
* **Headers**: `Authorization: Bearer <token>`
* **Response Headers**: `Content-Type: <detected_mime_type>`, `Content-Disposition: attachment; filename="..."`
* **Body**: Streamed file binary.

---

## 9. Institutional Organization Accounts (`/api/v1/organizations`)

### 9.1 Create or Update Organization Profile
* **Endpoint**: `POST /api/v1/organizations/profile`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <org_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "legalName": "MGM College of Engineering & Technology",
  "orgType": "COLLEGE",
  "codeOrGstin": "ENG-MGM-001",
  "contactEmail": "tpo@mgmcet.ac.in",
  "contactPhone": "+91 98765 43210",
  "address": "Navi Mumbai, Maharashtra - 410206"
}
```
* **Success Response (`201 Created`)**: Returns `Organization` entity.

---

### 9.2 Onboard Candidate Under Organization
* **Endpoint**: `POST /api/v1/organizations/candidates`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <org_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "email": "student@mgmcet.ac.in",
  "fullName": "Kavita Deshmukh",
  "password": "StudentPassword123!",
  "rollNumberOrId": "2024-CSE-045",
  "batchYear": "2024"
}
```
* **Success Response (`201 Created`)**: Returns created candidate `User` object.

---

### 9.3 Get Enrolled Candidates
* **Endpoint**: `GET /api/v1/organizations/candidates`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <org_token>`
* **Success Response (`200 OK`)**: List of `OrganizationCandidate` mappings.

---

## 10. College Placement Management Subsystem & Dynamic Custom Roles

### 10.1 Resolve Logged-In User Placement Context
* **Endpoint**: `GET /api/v1/college/team/context`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`
* **Description**: Delivers the logged-in user's placement authority context (Head TPO vs Branch Coordinator), assigned department scope, and capability flags for frontend UI configuration.
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "User context resolved successfully",
  "data": {
    "organizationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "roleName": "Department Placement Coordinator",
    "customRoleId": "8b9c0d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e",
    "assignedBranch": "Computer Science & Engineering",
    "isHeadTpo": false,
    "canViewStudents": true,
    "canEditStudents": true,
    "canManagePlacements": true,
    "canInviteTeam": false,
    "canExportReports": true
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:50Z"
}
```

---

### 10.2 Download Student Import Roster Template
* **Endpoint**: `GET /api/v1/college/students/template?sample=true`
* **Security**: Public
* **Query Parameters**:
  * `sample` (boolean, default: `true`): If `true`, downloads CSV populated with representative sample students; if `false`, downloads blank template.
* **Response**: `text/csv` attachment (`Content-Disposition: attachment; filename="proveyu_student_import_sample_template.csv"`).

---

### 10.3 Bulk Student Roster Upload & Ingestion
* **Endpoint**: `POST /api/v1/college/students/bulk-upload`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: multipart/form-data`
* **Form Field**: `file` (Multipart `.xlsx`, `.xls`, or `.csv` roster)
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Bulk student roster processed successfully",
  "data": {
    "totalRows": 25,
    "importedCount": 23,
    "updatedCount": 2,
    "failedCount": 0,
    "errors": [],
    "importedStudents": [
      {
        "id": "fa44534e-965b-42c7-9294-f49acad7a57f",
        "rollNumber": "2024-MECH-001",
        "firstName": "Amit",
        "lastName": "Patil",
        "email": "amit@mgmcet.ac.in",
        "phone": "+91 98765 43212",
        "branch": "Mechanical Engineering",
        "batchYear": "2024",
        "degree": "B.Tech",
        "aggregateCgpa": 8.52,
        "tier": "Tier 1",
        "tierBadge": "badge-tier-1",
        "placementStatus": "ELIGIBLE"
      }
    ]
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:01:55Z"
}
```

---

### 10.4 Get Paginated Student Roster
* **Endpoint**: `GET /api/v1/college/students`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`
* **Query Parameters**:
  * `search` (optional string): Case-insensitive match on roll number, name, email.
  * `branch` (optional string, default: `"All"`): Department branch (locked automatically to `ctx.assignedBranch` for non-TPO coordinators).
  * `tier` (optional string, default: `"All"`): `"Tier 1"`, `"Tier 2"`, `"Tier 3"`.
  * `status` (optional string, default: `"All"`): `"ELIGIBLE"`, `"PLACED"`, `"OPTED_OUT"`, `"INELIGIBLE"`.
  * `minCgpa` (optional double), `maxBacklogs` (optional int), `maxGapYears` (optional int).
  * `page` (default: `0`), `size` (default: `10`), `sortBy` (default: `"createdAt"`), `sortDir` (default: `"desc"`).
* **Success Response (`200 OK`)**: Page object containing array of `StudentProfileDto` items.

---

### 10.5 Get Individual Student Profile
* **Endpoint**: `GET /api/v1/college/students/{studentId}`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: Returns complete `StudentProfileDto`.

---

### 10.6 Create Student Profile Manually
* **Endpoint**: `POST /api/v1/college/students`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "rollNumber": "2024-IT-089",
  "firstName": "Neha",
  "lastName": "Sharma",
  "email": "neha.sharma@mgmcet.ac.in",
  "phone": "+91 98765 22222",
  "dob": "2002-05-14",
  "gender": "Female",
  "branch": "Information Technology",
  "batchYear": "2024",
  "degree": "B.Tech",
  "sem1Sgpa": 8.2,
  "sem2Sgpa": 8.4,
  "sem3Sgpa": 8.6,
  "sem4Sgpa": 8.5,
  "sem5Sgpa": 8.8,
  "sem6Sgpa": 8.9,
  "sem7Sgpa": 9.1,
  "sem8Sgpa": 9.2,
  "activeBacklogs": 0,
  "gapYears": 0,
  "placementStatus": "ELIGIBLE"
}
```
* **Success Response (`201 Created`)**: Returns newly created `StudentProfileDto` with auto-calculated aggregate CGPA and tier.

---

### 10.7 Update Student Profile
* **Endpoint**: `PUT /api/v1/college/students/{studentId}`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**: Same fields as `10.6`.
* **Success Response (`200 OK`)**: Returns updated `StudentProfileDto`.

---

### 10.8 Bulk Record Placement Offers
* **Endpoint**: `POST /api/v1/college/students/bulk-placement`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "studentIds": [
    "fa44534e-965b-42c7-9294-f49acad7a57f",
    "b1c2d3e4-5f6a-7b8c-9d0e-1f2a3b4c5d6e"
  ],
  "company": "Tata Motors Ltd",
  "role": "Graduate Engineer Trainee - Design",
  "ctc": "₹8.5 LPA",
  "packageCategory": "Dream (5-10 LPA)",
  "bondDuration": "1 Year",
  "offerDate": "2026-10-15",
  "joiningDate": "2026-12-01",
  "recruitmentSource": "Campus Drive"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Placement Recorded",
  "data": "Successfully updated placement status for selected candidates",
  "errorCode": null,
  "timestamp": "2026-10-20T18:02:00Z"
}
```

---

### 10.9 Get Student Self Profile
* **Endpoint**: `GET /api/v1/college/students/my-profile`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`
* **Success Response (`200 OK`)**: Returns `StudentProfileDto` linked to the logged-in candidate.

---

### 10.10 Update Student Self Profile
* **Endpoint**: `PUT /api/v1/college/students/my-profile`
* **Security**: Authenticated (`Role.CANDIDATE`)
* **Headers**: `Authorization: Bearer <candidate_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "phone": "+91 98765 99999",
  "residentialAddress": "Flat 402, Green Valley Apartments, Navi Mumbai",
  "linkedinUrl": "https://linkedin.com/in/student-profile",
  "githubUrl": "https://github.com/student-dev",
  "portfolioUrl": "https://studentdev.tech"
}
```
* **Success Response (`200 OK`)**: Echoes updated `StudentProfileDto`.

---

### 10.11 Placement KPI Dashboard Analytics
* **Endpoint**: `GET /api/v1/college/students/dashboard/kpi`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Dashboard KPIs fetched successfully",
  "data": {
    "totalStudents": 450,
    "placedStudents": 320,
    "unplacedStudents": 130,
    "placementPercentage": 71.1,
    "activeDrives": 8,
    "averagePackageLpa": 6.8,
    "highestPackageLpa": 24.0,
    "branchWisePlacements": {
      "Computer Science & Engineering": 140,
      "Information Technology": 110,
      "Mechanical Engineering": 45,
      "Electronics & Telecommunication": 25
    },
    "upcomingDrives": []
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:02:05Z"
}
```

---

### 10.12 List College Placement Drives
* **Endpoint**: `GET /api/v1/college/placements`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: List of `PlacementDriveDto.Response` objects.

---

### 10.13 Get Placement Drive Details
* **Endpoint**: `GET /api/v1/college/placements/{driveId}`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: Individual `PlacementDriveDto.Response` object.

---

### 10.14 Schedule New Placement Drive
* **Endpoint**: `POST /api/v1/college/placements`
* **Security**: Authenticated (`Role.ORGANIZATION` with `canManagePlacements`)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "companyName": "Accenture Solutions",
  "companyWebsite": "https://accenture.com",
  "logoUrl": "https://accenture.com/logo.png",
  "industry": "IT Consulting & Services",
  "roles": "Associate Software Engineer",
  "jobLocation": "Pune / Bengaluru",
  "employmentType": "Full-Time",
  "packageLpa": 4.5,
  "stipendMonthly": "₹25,000",
  "bondDurationMonths": 0,
  "driveCategory": "On-Campus",
  "driveDate": "2026-11-20",
  "driveMode": "Hybrid",
  "venue": "Campus Auditorium & Labs",
  "minCgpa": 6.5,
  "maxBacklogs": 0,
  "maxGapYears": 1,
  "minTenthPercentage": 60.0,
  "minTwelfthPercentage": 60.0,
  "eligibleBranches": "Computer Science & Engineering, Information Technology",
  "selectionRounds": "Online Aptitude, Technical Interview, HR Discussion",
  "hrContactName": "Pooja Hegde",
  "hrContactEmail": "pooja.hegde@accenture.com",
  "hrContactPhone": "+91 98765 88888",
  "status": "Scheduled"
}
```
* **Success Response (`201 Created`)**: Returns created `PlacementDriveDto.Response`.

---

### 10.15 Update Placement Drive
* **Endpoint**: `PUT /api/v1/college/placements/{driveId}`
* **Security**: Authenticated (`Role.ORGANIZATION` with `canManagePlacements`)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**: Same fields as `10.14`.
* **Success Response (`200 OK`)**: Returns updated `PlacementDriveDto.Response`.

---

### 10.16 Delete Placement Drive
* **Endpoint**: `DELETE /api/v1/college/placements/{driveId}`
* **Security**: Authenticated (`Role.ORGANIZATION` with `canManagePlacements`)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Drive Deleted",
  "data": "Placement drive deleted successfully",
  "errorCode": null,
  "timestamp": "2026-10-20T18:02:10Z"
}
```

---

### 10.17 List College Placement Team Members
* **Endpoint**: `GET /api/v1/college/team`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: List of `TeamMemberDto.Response` items.

---

### 10.18 Invite Placement Team Member
* **Endpoint**: `POST /api/v1/college/team/invite`
* **Security**: Authenticated (`Role.ORGANIZATION`, Head TPO Admin)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "name": "Dr. Sunil Mehta",
  "email": "sunil.mehta@mgmcet.ac.in",
  "phone": "+91 98765 77777",
  "customRoleId": "8b9c0d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e",
  "assignedBranch": "Information Technology",
  "designation": "HOD & Placement Coordinator",
  "accessLevel": "80% Access",
  "tempPassword": "CollegeSecurePassword123!"
}
```
* **Success Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "Team member invited successfully",
  "data": {
    "id": "6c7d8e9f-0a1b-2c3d-4e5f-6a7b8c9d0e1f",
    "organizationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "userId": "9a0b1c2d-3e4f-5a6b-7c8d-9e0f1a2b3c4d",
    "name": "Dr. Sunil Mehta",
    "email": "sunil.mehta@mgmcet.ac.in",
    "phone": "+91 98765 77777",
    "roleName": "Department Placement Coordinator",
    "assignedBranch": "Information Technology",
    "designation": "HOD & Placement Coordinator",
    "accessLevel": "80% Access",
    "status": "ACTIVE",
    "lastActive": "Invitation Sent"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:02:15Z"
}
```

---

### 10.19 Update Team Member Privileges
* **Endpoint**: `PUT /api/v1/college/team/{memberId}`
* **Security**: Authenticated (`Role.ORGANIZATION`, Head TPO Admin)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "name": "Dr. Sunil Mehta",
  "phone": "+91 98765 77777",
  "customRoleId": "8b9c0d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e",
  "assignedBranch": "Information Technology",
  "designation": "Professor & Senior Placement Lead",
  "accessLevel": "90% Access",
  "status": "ACTIVE"
}
```
* **Success Response (`200 OK`)**: Returns updated `TeamMemberDto.Response`.

---

### 10.20 Remove Team Member
* **Endpoint**: `DELETE /api/v1/college/team/{memberId}`
* **Security**: Authenticated (`Role.ORGANIZATION`, Head TPO Admin)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Member Deleted",
  "data": "Team member removed successfully",
  "errorCode": null,
  "timestamp": "2026-10-20T18:02:20Z"
}
```

---

### 10.21 List Defined Custom Dynamic Roles
* **Endpoint**: `GET /api/v1/college/roles`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: List of `CustomRoleDto.Response` items.

---

### 10.22 Get Custom Dynamic Role by ID
* **Endpoint**: `GET /api/v1/college/roles/{roleId}`
* **Security**: Authenticated (`Role.ORGANIZATION`)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**: Returns individual `CustomRoleDto.Response`.

---

### 10.23 Create Custom Dynamic Role
* **Endpoint**: `POST /api/v1/college/roles`
* **Security**: Authenticated (`Role.ORGANIZATION`, Head TPO Admin)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "roleName": "Department Placement Coordinator - Mechanical",
  "description": "Manages mechanical engineering batch rosters and technical drive eligibility",
  "assignedBranch": "Mechanical Engineering",
  "canViewStudents": true,
  "canEditStudents": true,
  "canManagePlacements": true,
  "canInviteTeam": false,
  "canExportReports": true,
  "accessPercentage": "80% Access"
}
```
* **Success Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "Custom role created successfully",
  "data": {
    "id": "8b9c0d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e",
    "organizationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "roleName": "Department Placement Coordinator - Mechanical",
    "description": "Manages mechanical engineering batch rosters and technical drive eligibility",
    "assignedBranch": "Mechanical Engineering",
    "canViewStudents": true,
    "canEditStudents": true,
    "canManagePlacements": true,
    "canInviteTeam": false,
    "canExportReports": true,
    "accessPercentage": "80% Access"
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:02:25Z"
}
```

---

### 10.24 Update Custom Dynamic Role
* **Endpoint**: `PUT /api/v1/college/roles/{roleId}`
* **Security**: Authenticated (`Role.ORGANIZATION`, Head TPO Admin)
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**: Same fields as `10.23`.
* **Success Response (`200 OK`)**: Returns updated `CustomRoleDto.Response`.

---

### 10.25 Delete Custom Dynamic Role
* **Endpoint**: `DELETE /api/v1/college/roles/{roleId}`
* **Security**: Authenticated (`Role.ORGANIZATION`, Head TPO Admin)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Role Deleted",
  "data": "Custom role deleted successfully",
  "errorCode": null,
  "timestamp": "2026-10-20T18:02:30Z"
}
```

---

## 11. Platform Administration Module (`/api/v1/admin`)

### 11.1 Create System Administrator
* **Endpoint**: `POST /api/v1/admin/users/create-admin`
* **Security**: Authenticated (`Role.ADMIN`)
* **Headers**: `Authorization: Bearer <admin_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "email": "superadmin@proveyu.com",
  "fullName": "System Controller",
  "password": "AdminMasterPassword123!"
}
```
* **Success Response (`201 Created`)**: Returns created `User` with role `Role.ADMIN`.

---

### 11.2 Configure Examination Catalog
* **Endpoint**: `POST /api/v1/admin/exams`
* **Security**: Authenticated (`Role.ADMIN`)
* **Headers**: `Authorization: Bearer <admin_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "skillId": "e1122334-5566-7788-9900-aabbccddeeff",
  "level": "ADVANCED",
  "title": "National Graduate Engineering Aptitude & Logic Assessment",
  "durationMinutes": 90,
  "totalMarks": 100.0,
  "passingMarks": 60.0,
  "priceCents": 49900,
  "currency": "INR"
}
```
* **Success Response (`201 Created`)**: Returns configured `Exam` entity.

---

### 11.3 Get Global Platform Statistics
* **Endpoint**: `GET /api/v1/admin/stats`
* **Security**: Authenticated (`Role.ADMIN`)
* **Headers**: `Authorization: Bearer <admin_token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Platform statistics retrieved successfully",
  "data": {
    "totalUsers": 12450,
    "totalCandidates": 11200,
    "totalRecruiters": 950,
    "totalOrganizations": 300,
    "confirmedBookings": 8420,
    "verifiedGstCompanies": 680,
    "totalActiveDrives": 42
  },
  "errorCode": null,
  "timestamp": "2026-10-20T18:02:35Z"
}
```
