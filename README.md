# Access Request App

A small app where an employee asks for access to a tool (GitHub, AWS, Slack or
Google Workspace), a manager approves or rejects it, and every step is recorded
with who did it and when, so it can be shown to an auditor later.

- **Backend:** Java 17, Spring Boot 3, Spring Data JPA, persistent H2 database
- **Frontend:** React 18 with Vite
- **Accounts:** employees and managers register with their name, email, mobile number, and password, then sign in with their name, email, and password

## How to run

You need Java 17+, Maven, and Node.js 18+.

**1. Start the backend** (port 8080)

```bash
cd backend
mvn spring-boot:run
```

**2. Start the frontend** (port 5173), in a second terminal

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.

Choose the account type on the registration screen. Passwords are stored as BCrypt hashes; email addresses and normalized mobile numbers must be unique.

**Run the tests**

```bash
cd backend
mvn test
```

The backend stores accounts, requests, and their audit history in a local H2
database at `backend/data/accessdb`, so history is preserved when the backend
restarts. You can inspect it while the app runs at http://localhost:8080/h2-console
(JDBC URL: `jdbc:h2:file:./data/accessdb`, user `sa`, empty password).

## API

| Method | URL | Body | What it does |
|--------|-----|------|--------------|
| POST | `/api/requests` | `{employeeName, system, reason}` | Create a request (status Pending) |
| GET | `/api/requests` | | List all requests, newest first, with history |
| POST | `/api/requests/{id}/approve` | `{managerName}` | Approve a pending request |
| POST | `/api/requests/{id}/reject` | `{managerName, reason}` | Reject a pending request; reason is required |

`system` is one of `GITHUB`, `AWS`, `SLACK`, `GOOGLE_WORKSPACE`.

Errors come back as `{"error": "message"}`: 400 for bad input, 404 for an unknown
request, 409 if the request was already approved or rejected.

## How the code is organised

```
backend/src/main/java/com/vigilstack/accessrequest/
  model/    AccessRequest and HistoryEntry (the database tables), plus enums
  repo/     AccessRequestRepository (reads and writes the database)
  service/  AccessRequestService (the logic: create, list, approve, reject)
  web/      AccessRequestController (the URLs) and ApiExceptionHandler (errors)
frontend/src/
  api.js    the calls to the backend
  App.jsx   the page: role dropdown, new request form, request list, history
```

Design choices:

- **History is part of the request.** Each create, approve or reject adds a
  `HistoryEntry` (action, person, time, comment) and entries are never edited or
  deleted, which is what an auditor wants to see.
- **The rules live in `AccessRequest`.** A request can be decided only while it is
  Pending, so approving or rejecting twice is blocked (409).
- **Rejecting needs a reason.** Checked on the backend, not only in the page.
- **Timestamps are set by the server**, not typed by the user.

## Tests

`AccessRequestApiTest` has 10 tests that start the app and exercise request
creation and decisions, input validation, registration and sign-in, and profile
updates.

## Known gaps (honest list)

- Manager accounts can self-register, and the API does not enforce an
  authenticated session or authorization on request decisions. A production
  deployment should use trusted identity/role provisioning and server-side
  authorization.
- The H2 database is local to this machine; production use should configure a
  managed database and backups.
- The request list has no paging, and there are no automated frontend tests.
- Optional extras (AI risk rating, FastAPI version) are not done.

## AI tools I used

> TODO: write this yourself, honestly. Say which tool(s) you used and for what,
> for example: "Claude generated the first version of the backend and frontend.
> I read every file, ran the app and tests, and changed ___."
