# PROVEYU API Specification & Payload Documentation

This document provides a complete, module-by-module specification of all REST APIs in the **PROVEYU** backend platform, including URI paths, HTTP methods, headers, request payloads, and response JSON formats.

---

## Standard API Response Format (`ApiResponse<T>`)

All APIs return a generic JSON wrapper structure:

```json
{
  "success": true,
  "message": "Operation response summary message",
  "data": { ... },
  "errorCode": null,
  "timestamp": "2026-09-20T20:50:00.000Z"
}
```

---

## 1. Authentication Module (`/api/v1/auth`)

### 1.1 User Registration
* **Endpoint**: `POST /api/v1/auth/register`
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
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "userId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "email": "candidate_proveyu@example.com",
    "fullName": "Aarav Sharma",
    "role": "CANDIDATE"
  },
  "errorCode": null,
  "timestamp": "2026-09-20T20:50:00Z"
}
```

---

### 1.2 User Login
* **Endpoint**: `POST /api/v1/auth/login`
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
  "message": "Authentication successful",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "userId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "email": "candidate_proveyu@example.com",
    "fullName": "Aarav Sharma",
    "role": "CANDIDATE"
  },
  "errorCode": null,
  "timestamp": "2026-09-20T20:50:05Z"
}
```

---

### 1.3 Forgot Password (Request OTP Email)
* **Endpoint**: `POST /api/v1/auth/forgot-password`
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
    "resetToken": "741638",
    "message": "Password reset token generated. Valid for 15 minutes."
  },
  "errorCode": null,
  "timestamp": "2026-09-20T20:50:10Z"
}
```

---

### 1.4 Verify Reset OTP Token
* **Endpoint**: `POST /api/v1/auth/verify-otp`
* **Headers**: `Content-Type: application/json`
* **Request Payload**:
```json
{
  "email": "candidate_proveyu@example.com",
  "otp": "741638"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "OTP verification successful",
  "data": true,
  "errorCode": null,
  "timestamp": "2026-09-20T20:50:15Z"
}
```

---

### 1.5 Reset Password
* **Endpoint**: `POST /api/v1/auth/reset-password`
* **Headers**: `Content-Type: application/json`
* **Request Payload**:
```json
{
  "email": "candidate_proveyu@example.com",
  "otp": "741638",
  "newPassword": "NewSecurePassword123!"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Password has been reset successfully",
  "data": null,
  "errorCode": null,
  "timestamp": "2026-09-20T20:50:20Z"
}
```

---

### 2.4 Upload Candidate PDF Resume (`MultipartFile`)
* **Endpoint**: `POST /api/v1/candidates/resume/upload`
* **Headers**: `Authorization: Bearer <token>`
* **Content-Type**: `multipart/form-data`
* **Form Field**: `file` (File binary: `.pdf`, `.doc`, or `.docx`, max size: 5MB)
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "PDF Resume uploaded and linked successfully",
  "data": {
    "id": "c54b7323-0f1e-46f2-aff8-f0da2631b86e",
    "userId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "experienceTrack": "EXPERIENCED",
    "yearsOfExperience": 4,
    "uanNumber": "100918273645",
    "skillsList": "Java, Spring Boot, MySQL, Microservices",
    "resumeUrl": "/api/v1/candidates/resume/download/resume_b0803c68-90d7-426f-91a1-bbf472a269e4_1789925847709.pdf",
    "updatedAt": "2026-09-20T21:30:00Z"
  },
  "errorCode": null,
  "timestamp": "2026-09-20T21:30:00Z"
}
```

---

### 2.5 View / Download Candidate Resume PDF
* **Endpoint**: `GET /api/v1/candidates/resume/download/{filename}`
* **Headers**: Public access (No Auth required for viewing/downloading)
* **Response Headers**: `Content-Type: application/pdf`, `Content-Disposition: inline; filename="..."`
* **Body**: Raw PDF / Document byte stream.

---

## 2. Candidate Profile & Performance Passport Module (`/api/v1/candidates`)

### 2.1 Upsert Candidate Profile (Experienced Track with UAN Verification)
* **Endpoint**: `POST /api/v1/candidates/profile`
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "experienceTrack": "EXPERIENCED",
  "yearsOfExperience": 4,
  "uanNumber": "100918273645",
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
    "experienceTrack": "EXPERIENCED",
    "yearsOfExperience": 4,
    "uanNumber": "100918273645",
    "collegeName": null,
    "degreeBranch": null,
    "passoutYear": null,
    "skillsList": "Java, Spring Boot, MySQL, Microservices",
    "resumeUrl": "https://s3.amazonaws.com/proveyu-resumes/aarav.pdf",
    "updatedAt": "2026-09-20T20:51:00Z"
  },
  "errorCode": null,
  "timestamp": "2026-09-20T20:51:00Z"
}
```

---

### 2.2 Get Candidate Profile
* **Endpoint**: `GET /api/v1/candidates/profile`
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Candidate profile retrieved successfully",
  "data": {
    "id": "c54b7323-0f1e-46f2-aff8-f0da2631b86e",
    "userId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "experienceTrack": "EXPERIENCED",
    "yearsOfExperience": 4,
    "uanNumber": "100918273645",
    "collegeName": null,
    "degreeBranch": null,
    "passoutYear": null,
    "skillsList": "Java, Spring Boot, MySQL, Microservices",
    "resumeUrl": "https://s3.amazonaws.com/proveyu-resumes/aarav.pdf",
    "updatedAt": "2026-09-20T20:51:00Z"
  },
  "errorCode": null,
  "timestamp": "2026-09-20T20:51:05Z"
}
```

---

### 2.3 Get Candidate Performance Passport
* **Endpoint**: `GET /api/v1/candidates/passport`
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Candidate performance passport retrieved successfully",
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
  "timestamp": "2026-09-20T20:51:10Z"
}
```

---

## 3. Recruiter Search & Interview Invitations Module (`/api/v1/recruiter`, `/api/v1/interviews`)

### 3.1 Send Interview Invitation (with Salary Range Offer)
* **Endpoint**: `POST /api/v1/interviews/invite`
* **Headers**: `Authorization: Bearer <recruiter_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "candidateId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
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
    "minSalaryLpa": 18.0,
    "maxSalaryLpa": 24.0,
    "packageRange": "18.0 - 24.0 LPA",
    "status": "PENDING",
    "placementStatus": "IN_PROCESS",
    "createdAt": "2026-09-20T20:52:00Z"
  },
  "errorCode": null,
  "timestamp": "2026-09-20T20:52:00Z"
}
```

---

### 3.2 Candidate View Received Interview Invitations
* **Endpoint**: `GET /api/v1/interviews/candidate-invites`
* **Headers**: `Authorization: Bearer <candidate_token>`
* **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Candidate interview invitations retrieved successfully",
  "data": [
    {
      "id": "5207a672-7fed-40b5-b832-2ae6f9326e92",
      "jobTitle": "Lead Backend Engineer",
      "packageRange": "18.0 - 24.0 LPA",
      "status": "PENDING"
    }
  ],
  "errorCode": null,
  "timestamp": "2026-09-20T20:52:05Z"
}
```

---

## 4. GST & Company Verification Module (`/api/v1/recruiters/companies`)

### 4.1 Verify Company GSTIN
* **Endpoint**: `POST /api/v1/recruiters/companies/verify-gst`
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
    "verifiedAt": "2026-09-20T20:52:30Z"
  },
  "errorCode": null,
  "timestamp": "2026-09-20T20:52:30Z"
}
```

---

## 5. Exam Slot Booking Module (`/api/v1/bookings`)

---

### 5.2 Verify Payment & Issue Admit Card PDF
* **Endpoint**: `POST /api/v1/payments/verify`
* **Headers**: `Authorization: Bearer <candidate_token>`, `Content-Type: application/json`
* **Request Payload**:
```json
{
  "orderId": "order_mock_cf4a8cc051da46",
  "paymentId": "pay_mock_123",
  "signature": "mock_signature"
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
    "issuedAt": "2026-09-20T23:17:59Z",
    "pdfUrl": "/api/v1/admit-cards/download/admit_card_bf24140d-5b09-40b3-963d-7a7544952a99.pdf"
  },
  "errorCode": null,
  "timestamp": "2026-09-20T23:18:00Z"
}
```

---

### 5.3 View / Download Admit Card PDF
* **Endpoint**: `GET /api/v1/admit-cards/download/{filename}`
* **Headers**: Public access (No Auth required for viewing/downloading Admit Card PDF)
* **Response Headers**: `Content-Type: application/pdf`, `Content-Disposition: inline; filename="admit_card_{bookingId}.pdf"`
* **Body**: PDF Document containing Official PROVEYU Hall Ticket details, candidate name, booking status (`CONFIRMED`), QR verification code, and examination instructions.

---

### 5.4 Razorpay Asynchronous Payment Webhook
* **Endpoint**: `POST /api/v1/payments/webhook`
* **Headers**: `X-Razorpay-Signature: <hmac_sha256_signature>`
* **Request Payload Example (`order.paid`)**:
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
  "timestamp": "2026-09-20T23:29:00Z"
}
```


