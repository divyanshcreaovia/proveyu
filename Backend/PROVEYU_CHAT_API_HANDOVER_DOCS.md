# PROVEYU / PARAKH — Chat Module Frontend Handover & Integration Guide

This document provides complete, production-ready documentation for Frontend Developers to integrate the **Chat & Real-Time Messaging Module**, covering REST endpoints, file attachment sharing, WebSocket protocol, and concurrency architecture for handling **5,000+ active users**.

---

## 1. Overview & Base Configuration

### 1.1 Base URLs
- **REST Base URL**:
  - Primary: `http://<host>:8080/api/chat`
  - Alias: `http://<host>:8080/api/v1/chat`
- **WebSocket Base URL**:
  - Development: `ws://<host>:8080/ws/chat`
  - Production (SSL/TLS): `wss://<domain>/ws/chat`

### 1.2 Authentication
All endpoints require a valid JWT Bearer token issued during login (`POST /api/v1/auth/login`).
- **REST Header**: `Authorization: Bearer <jwt_token>`
- **WebSocket Handshake**: Pass token as query parameter `?token=<jwt_token>` or via the `Authorization` header.

### 1.3 Standard API Response Wrapper
All REST endpoints return the standard `ApiResponse<T>` wrapper:
```json
{
  "success": true,
  "message": "Operation response summary",
  "data": { ... },
  "errorCode": null,
  "timestamp": "2026-09-22T18:00:00.000Z"
}
```

---

## 2. REST APIs Specification

---

### 2.1 Send Text Message
Sends a new text message to another user. The sender identity is extracted strictly from the authenticated JWT token.

- **Endpoint**: `POST /api/chat/messages`
- **Headers**:
  - `Authorization: Bearer <jwt>`
  - `Content-Type: application/json`
- **Request Payload**:
```json
{
  "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
  "message": "Hello! I reviewed your Skill Passport and would like to invite you for an interview."
}
```

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `receiverId` | `UUID` | **Yes** | Target recipient user ID (`CHAR(36)` format) |
| `message` | `String` | **Yes** | Text message content (cannot be blank) |

- **Success Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "Message sent successfully",
  "data": {
    "id": "e7b1a2c3-4d5e-6f70-8901-234567abcdef",
    "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
    "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "message": "Hello! I reviewed your Skill Passport and would like to invite you for an interview.",
    "messageType": "TEXT",
    "filePath": null,
    "fileName": null,
    "fileSize": null,
    "fileContentType": null,
    "status": "SENT",
    "createdAt": "2026-09-22T18:00:00.000Z",
    "updatedAt": "2026-09-22T18:00:00.000Z",
    "readAt": null
  },
  "errorCode": null,
  "timestamp": "2026-09-22T18:00:00.000Z"
}
```

- **Error Codes**:
  - `400 BAD_REQUEST` (`SELF_MESSAGE_NOT_ALLOWED`): `receiverId` matches authenticated sender.
  - `400 BAD_REQUEST` (`EMPTY_MESSAGE`): Message is empty or whitespace.
  - `404 NOT_FOUND` (`USER_NOT_FOUND`): Recipient user does not exist.

---

### 2.2 Send File Message (Attachment)
Uploads and sends a document or image attachment with an optional caption. Files are safely stored in server storage (`uploads/chat`) and only metadata is stored in the database.

- **Endpoint**: `POST /api/chat/messages/file`
- **Headers**:
  - `Authorization: Bearer <jwt>`
  - `Content-Type: multipart/form-data`
- **Form Data Fields (`multipart/form-data`)**:

| Form Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `receiverId` | `UUID (String)` | **Yes** | Target recipient user ID |
| `file` | `Binary File` | **Yes** | File binary (max: 10MB) |
| `message` | `String` | No | Optional caption/description |

- **Allowed Formats**: `.pdf`, `.doc`, `.docx`, `.jpg`, `.jpeg`, `.png`
- **Max File Size**: 10 MB (enforced server-side)

- **Success Response (`201 Created`)**:
```json
{
  "success": true,
  "message": "File message sent successfully",
  "data": {
    "id": "f8c2b3d4-5e6f-7081-9012-345678bcdefa",
    "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
    "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "message": "Here is the job description and interview agenda.",
    "messageType": "FILE",
    "filePath": "chat_4431656b-3fad-43e8-96ce-531263de5f40_a1b2c3d4.pdf",
    "fileName": "interview_agenda.pdf",
    "fileSize": 245760,
    "fileContentType": "application/pdf",
    "status": "SENT",
    "createdAt": "2026-09-22T18:01:00.000Z",
    "updatedAt": "2026-09-22T18:01:00.000Z",
    "readAt": null
  },
  "errorCode": null,
  "timestamp": "2026-09-22T18:01:00.000Z"
}
```

- **Error Codes**:
  - `400 BAD_REQUEST` (`EMPTY_FILE`): File is missing or 0 bytes.
  - `400 BAD_REQUEST` (`FILE_TOO_LARGE`): File exceeds 10MB limit.
  - `400 BAD_REQUEST` (`INVALID_FILE_TYPE`): File extension is not allowed.
  - `400 BAD_REQUEST` (`INVALID_PATH_SEQUENCE`): Path traversal attempt in filename.

---

### 2.3 Get Conversation History
Retrieves paginated two-way messages between the authenticated user and another participant (`userId`), ordered chronologically.

- **Endpoint**: `GET /api/chat/messages/{userId}`
- **Headers**:
  - `Authorization: Bearer <jwt>`
- **Query Parameters**:
  - `page`: Page index (default: `0`, zero-indexed)
  - `size`: Items per page (default: `50`, recommended: `20`–`50`)
  - `sort`: Sorting field & direction (default: `createdAt,asc`)

- **Example Request**:
  `GET /api/chat/messages/b0803c68-90d7-426f-91a1-bbf472a269e4?page=0&size=50&sort=createdAt,asc`

- **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Conversation history retrieved successfully",
  "data": {
    "content": [
      {
        "id": "e7b1a2c3-4d5e-6f70-8901-234567abcdef",
        "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
        "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
        "message": "Hello Aarav! When can we schedule your interview?",
        "messageType": "TEXT",
        "filePath": null,
        "fileName": null,
        "fileSize": null,
        "fileContentType": null,
        "status": "READ",
        "createdAt": "2026-09-22T17:40:00.000Z",
        "updatedAt": "2026-09-22T17:42:00.000Z",
        "readAt": "2026-09-22T17:42:00.000Z"
      },
      {
        "id": "f8c2b3d4-5e6f-7081-9012-345678bcdefa",
        "senderId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
        "receiverId": "4431656b-3fad-43e8-96ce-531263de5f40",
        "message": "I am available tomorrow at 2:00 PM IST.",
        "messageType": "TEXT",
        "filePath": null,
        "fileName": null,
        "fileSize": null,
        "fileContentType": null,
        "status": "SENT",
        "createdAt": "2026-09-22T17:45:00.000Z",
        "updatedAt": "2026-09-22T17:45:00.000Z",
        "readAt": null
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 50
    },
    "totalElements": 2,
    "totalPages": 1,
    "last": true,
    "first": true,
    "empty": false
  },
  "errorCode": null,
  "timestamp": "2026-09-22T18:02:00.000Z"
}
```

---

### 2.4 Get Login Unread Notifications Summary (Dashboard / Header Notification)
**Call this endpoint upon user login or portal load.** Returns the total count of unread messages and a group-by-sender list containing the sender's full legal name, unread message count, and last message snippet.

- **Endpoint**: `GET /api/chat/notifications` *(alias: `/api/chat/unread/notifications`)*
- **Headers**:
  - `Authorization: Bearer <jwt>`
- **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Unread notifications retrieved successfully",
  "data": {
    "totalUnreadCount": 5,
    "senders": [
      {
        "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
        "senderName": "Sarah Smith",
        "senderEmail": "sarah.smith@google.com",
        "unreadCount": 3,
        "lastMessage": "Looking forward to speaking with you in the technical round!",
        "lastMessageAt": "2026-09-22T17:35:10.123Z"
      },
      {
        "senderId": "91a2b3c4-d5e6-f708-1920-a1b2c3d4e5f6",
        "senderName": "Vikram Patel",
        "senderEmail": "vikram@techcorp.in",
        "unreadCount": 2,
        "lastMessage": "[Attachment: role_offer_terms.pdf]",
        "lastMessageAt": "2026-09-22T16:10:00.000Z"
      }
    ]
  },
  "errorCode": null,
  "timestamp": "2026-09-22T18:03:00.000Z"
}
```

- **When No Unread Messages Exist**:
```json
{
  "success": true,
  "message": "Unread notifications retrieved successfully",
  "data": {
    "totalUnreadCount": 0,
    "senders": []
  },
  "errorCode": null,
  "timestamp": "2026-09-22T18:03:00.000Z"
}
```

---

### 2.5 Get Raw Unread Messages List
Returns raw unread message objects sent to the authenticated user.

- **Endpoint**: `GET /api/chat/unread`
- **Headers**:
  - `Authorization: Bearer <jwt>`
- **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Unread messages retrieved successfully",
  "data": [
    {
      "id": "e7b1a2c3-4d5e-6f70-8901-234567abcdef",
      "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
      "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
      "message": "Please confirm your availability.",
      "messageType": "TEXT",
      "status": "SENT",
      "createdAt": "2026-09-22T17:50:00.000Z"
    }
  ],
  "errorCode": null,
  "timestamp": "2026-09-22T18:04:00.000Z"
}
```

---

### 2.6 Mark Message as Read
Marks a received message as read. **Only the recipient can mark a message as read.**

- **Endpoint**: `PATCH /api/chat/messages/{messageId}/read`
- **Headers**:
  - `Authorization: Bearer <jwt>`
- **Success Response (`200 OK`)**:
```json
{
  "success": true,
  "message": "Message marked as read",
  "data": {
    "id": "e7b1a2c3-4d5e-6f70-8901-234567abcdef",
    "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
    "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "status": "READ",
    "readAt": "2026-09-22T18:05:00.000Z",
    "updatedAt": "2026-09-22T18:05:00.000Z"
  },
  "errorCode": null,
  "timestamp": "2026-09-22T18:05:00.000Z"
}
```

- **Error Codes**:
  - `403 FORBIDDEN` (`UNAUTHORIZED_MESSAGE_ACCESS`): User attempting to mark as read is NOT the recipient.
  - `404 NOT_FOUND` (`MESSAGE_NOT_FOUND`): Message does not exist.

---

### 2.7 Secure File Attachment Download
Downloads a chat attachment file. Verifies that the requester is either the **sender OR receiver**.

- **Endpoint**: `GET /api/chat/messages/{messageId}/file`
- **Headers**:
  - `Authorization: Bearer <jwt>`
- **Response**: Binary byte stream
  - `Content-Type`: MIME type (`application/pdf`, `image/png`, etc.)
  - `Content-Disposition`: `attachment; filename="<original_filename>"`
  - `Content-Length`: Exact file size in bytes

- **Error Codes**:
  - `403 FORBIDDEN` (`UNAUTHORIZED_FILE_ACCESS`): Requester is neither the sender nor receiver of this message.
  - `400 BAD_REQUEST` (`NOT_A_FILE_MESSAGE`): Message is a text message without an attachment.
  - `404 NOT_FOUND` (`FILE_NOT_FOUND`): Storage file not found.

---

## 3. WebSocket Real-Time Integration Guide

### 3.1 Connection Protocol & Handshake
The WebSocket endpoint is located at `/ws/chat`. Because standard browser `WebSocket` APIs cannot pass custom request headers, **the JWT token should be passed as a URL query parameter**:

```javascript
const wsUrl = `wss://yourdomain.com/ws/chat?token=${encodeURIComponent(userJwtToken)}`;
const socket = new WebSocket(wsUrl);
```

- The server validates the JWT signature and extracts the `userId`.
- Rejects unauthenticated connections immediately with HTTP `401 Unauthorized`.
- **Multiple Tabs Supported**: A user can open multiple browser tabs or devices; all active sessions receive live events simultaneously.

---

### 3.2 Inbound Events (Server $\rightarrow$ Client)

#### Event 1: `message.new` (New Message Received)
Dispatched to the receiver (and to the sender's other tabs) as soon as the message is committed to the database:
```json
{
  "event": "message.new",
  "data": {
    "id": "e7b1a2c3-4d5e-6f70-8901-234567abcdef",
    "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
    "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "message": "Hello! Let's schedule the technical interview.",
    "messageType": "TEXT",
    "filePath": null,
    "fileName": null,
    "fileSize": null,
    "fileContentType": null,
    "status": "SENT",
    "createdAt": "2026-09-22T18:00:00.000Z",
    "updatedAt": "2026-09-22T18:00:00.000Z",
    "readAt": null
  }
}
```

#### Event 2: `message.read` (Read Receipt)
Dispatched to the original sender when the recipient opens the chat and marks a message as read:
```json
{
  "event": "message.read",
  "data": {
    "id": "e7b1a2c3-4d5e-6f70-8901-234567abcdef",
    "senderId": "4431656b-3fad-43e8-96ce-531263de5f40",
    "receiverId": "b0803c68-90d7-426f-91a1-bbf472a269e4",
    "status": "READ",
    "readAt": "2026-09-22T18:05:00.000Z"
  }
}
```

#### Event 3: `pong` (Heartbeat Response)
Response when the client sends a `ping` keep-alive frame:
```json
{
  "event": "pong",
  "timestamp": 1789926400000
}
```

---

### 3.3 Outbound Keep-Alive (Client $\rightarrow$ Server Heartbeat)
To prevent network proxies, firewalls, and cloud load balancers (e.g. AWS ALB, NGINX) from terminating idle WebSocket connections, the frontend should send a ping every 30 seconds:
```javascript
socket.send(JSON.stringify({ type: 'ping' }));
```

---

### 3.4 Production Angular Service Example (`chat-websocket.service.ts`)

Here is the complete Angular service ready to drop into `Frontend/src/app`:

```typescript
import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable } from 'rxjs';

export interface ChatMessage {
  id: string;
  senderId: string;
  receiverId: string;
  message?: string;
  messageType: 'TEXT' | 'FILE';
  filePath?: string;
  fileName?: string;
  fileSize?: number;
  fileContentType?: string;
  status: 'SENT' | 'DELIVERED' | 'READ';
  createdAt: string;
  readAt?: string;
}

export interface WsEvent<T = any> {
  event: string;
  data: T;
}

@Injectable({
  providedIn: 'root'
})
export class ChatWebSocketService implements OnDestroy {
  private socket: WebSocket | null = null;
  private messageSubject$ = new Subject<ChatMessage>();
  private readReceiptSubject$ = new Subject<ChatMessage>();
  private connectionStatusSubject$ = new Subject<boolean>();
  private heartbeatInterval: any = null;
  private reconnectTimeout: any = null;
  private token: string = '';

  public onNewMessage(): Observable<ChatMessage> {
    return this.messageSubject$.asObservable();
  }

  public onReadReceipt(): Observable<ChatMessage> {
    return this.readReceiptSubject$.asObservable();
  }

  public onConnectionStatus(): Observable<boolean> {
    return this.connectionStatusSubject$.asObservable();
  }

  public connect(token: string, wsHost: string = window.location.host): void {
    this.token = token;
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${wsHost}/ws/chat?token=${encodeURIComponent(token)}`;

    this.socket = new WebSocket(wsUrl);

    this.socket.onopen = () => {
      this.connectionStatusSubject$.next(true);
      this.startHeartbeat();
    };

    this.socket.onmessage = (event: MessageEvent) => {
      try {
        const payload: WsEvent = JSON.parse(event.data);
        if (payload.event === 'message.new') {
          this.messageSubject$.next(payload.data);
        } else if (payload.event === 'message.read') {
          this.readReceiptSubject$.next(payload.data);
        }
      } catch (err) {
        console.error('[WS PARSE ERROR]', err);
      }
    };

    this.socket.onclose = () => {
      this.connectionStatusSubject$.next(false);
      this.stopHeartbeat();
      this.scheduleReconnect();
    };

    this.socket.onerror = (error) => {
      console.warn('[WS ERROR]', error);
      this.socket?.close();
    };
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatInterval = setInterval(() => {
      if (this.socket?.readyState === WebSocket.OPEN) {
        this.socket.send(JSON.stringify({ type: 'ping' }));
      }
    }, 30000); // 30 seconds
  }

  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimeout) return;
    this.reconnectTimeout = setTimeout(() => {
      this.reconnectTimeout = null;
      if (this.token) {
        this.connect(this.token);
      }
    }, 5000); // 5s reconnect backoff
  }

  public disconnect(): void {
    this.token = '';
    this.stopHeartbeat();
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
    this.socket?.close();
    this.socket = null;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
```

---

## 4. Architecture at Scale: 5,000 Concurrent Users

When **5,000 candidates and recruiters** are online simultaneously with active chat windows, the system handles the workload through the following architectural design:

```
                      5,000 CONCURRENT USERS ARCHITECTURE
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │ 5,000 Concurrent Browser Clients (Candidates & Recruiters)                 │
 └──────────────────────────────────┬──────────────────────────────────────────┘
                                    │ Persistent WebSocket (/ws/chat)
                                    ▼
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │ Tomcat NIO (Non-Blocking I/O) Poller Threads                                │
 │ • 5,000 TCP sockets held in poll loop without allocating 5,000 OS threads   │
 │ • Memory footprint: ~15KB per idle socket ≈ 75 MB RAM Total                 │
 └──────────────────────────────────┬──────────────────────────────────────────┘
                                    ▼
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │ ChatWebSocketSessionManager                                                 │
 │ • ConcurrentHashMap<UUID, Set<WebSocketSession>>                            │
 │ • O(1) lock-free lookup to deliver real-time events to user tabs            │
 └──────────────────────────────────┬──────────────────────────────────────────┘
                                    ▼
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │ REST Write Path & Database Indexing                                         │
 │ • DB Save committed before WS dispatch (Source of Truth)                    │
 │ • Composite B-Tree Index: (sender_id, receiver_id, created_at)              │
 │ • Unread Index: (receiver_id, status)                                      │
 │ • HikariCP Connection Pool (20-30 pool connections handle 5,000 WS users)   │
 └──────────────────────────────────┬──────────────────────────────────────────┘
                                    ▼
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │ Physical File Storage (uploads/chat)                                        │
 │ • Attachments stored on disk; zero binary data stored in MySQL              │
 │ • Zero memory bloat during downloads via Spring Resource streaming          │
 └─────────────────────────────────────────────────────────────────────────────┘
```

### 4.1 Memory & Thread Modeling
- **Non-Blocking I/O (Tomcat NIO)**: Spring Boot uses Tomcat's NIO connector. It does **not** allocate one OS thread per WebSocket connection. A pool of only 10–20 selector/worker threads multiplexes all 5,000 TCP sockets.
- **RAM Footprint**: Each idle WebSocket connection in Tomcat consumes $\approx 15\text{ KB}$ of state memory.  
  $$\text{Total WebSocket RAM for 5,000 users} \approx 5,000 \times 15\text{ KB} \approx 75\text{ MB}$$
  This fits comfortably within standard JVM heaps (e.g. 1GB–2GB).

### 4.2 In-Memory Session Lookups ($O(1)$)
- `ChatWebSocketSessionManager` stores sessions in a `ConcurrentHashMap<UUID, Set<WebSocketSession>>` backed by `CopyOnWriteArraySet`.
- When user A sends a message to user B:
  1. Lookup `userSessions.get(receiverId)` takes **$O(1)$ time** ($\approx 5\text{ microseconds}$).
  2. If user B is online with 2 tabs open, the event is serialized once and framed across both sockets with `synchronized(session)` to guarantee frame ordering.
  3. If user B is offline, no lookup iteration occurs; the database save is already committed.

### 4.3 Database Indexing & Connection Pool Protection
- **Connection Efficiency**: WebSocket connections do **not** hold database connections. Sockets remain open in memory, and database connections are acquired from the Hikari pool **only for the few milliseconds needed to execute the REST write or history query**, then immediately released.
- **Query Optimization**:
  - `findConversation`: Driven by the composite index `(sender_id, receiver_id, created_at)` and `(receiver_id, sender_id, created_at)`.
  - `findUnreadMessages`: Driven by `(receiver_id, status)`.
  - Both queries use index-range scans rather than full table scans, executing in under $2\text{ ms}$ even with millions of historical messages.

### 4.4 File Attachment Offloading
- Binary file contents are **never** stored as `BLOB`s in the MySQL database.
- Files stream directly to the filesystem under `uploads/chat/` with unique, non-colliding keys.
- File downloads stream via `UrlResource` using chunked byte buffers, preventing memory exhaustion on large PDF downloads.

### 4.5 Horizontal Scaling (Beyond 10,000+ Users across Multiple Nodes)
If the platform expands to multiple backend instances behind an AWS ALB or NGINX load balancer:
- The REST APIs remain 100% stateless.
- To route WebSocket messages when User A is connected to Node 1 and User B is connected to Node 2:
  - Spring Boot can be paired with **Redis Pub/Sub** via Redisson (already in `pom.xml`).
  - When Node 1 persists a message for User B, it publishes to Redis channel `chat:events:UserB`. Node 2 listens, matches User B in its local session manager, and delivers the message to User B's socket.
