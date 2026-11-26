# Access Request App

A small app where an employee asks for access to a tool (GitHub, AWS, Slack or
Google Workspace), a manager approves or rejects it, and every step is recorded
with who did it and when, so it can be shown to an auditor later.

- **Backend:** Java 17, Spring Boot 3, Spring Data JPA, H2 locally and PostgreSQL on Render
- **Frontend:** React 18 with Vite
- **Accounts:** employees register with their name, email, mobile number, and password. Manager registration requires a private invitation code. Sign-in uses name, email, and password.

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

Choose the account type on the registration screen. Passwords are stored as BCrypt hashes; email addresses and normalized mobile numbers must be unique. Manager invitation codes are configured through the `MANAGER_INVITE_CODE` environment variable. Signed-in sessions are stored in secure HTTP-only cookies. Employees can only view their own requests; only managers can approve or reject requests.

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
| POST | `/api/requests` | `{system, reason}` | Create a request for the signed-in employee (status Pending; account name/ownership come from the server session) |
| GET | `/api/requests` | | Employees see their own requests; managers see all requests, newest first |
| POST | `/api/requests/{id}/approve` | `{}` | Manager-only approval; actor comes from the signed-in session |
| POST | `/api/requests/{id}/reject` | `{reason}` | Manager-only rejection; actor comes from the signed-in session and reason is required |
| POST | `/api/auth/register` | account details | Register; managers must provide the configured invitation code |
| POST | `/api/auth/login` | `{fullName, email, password, role}` | Sign in and start a server-side session |
| POST | `/api/auth/logout` | | End the current session |

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

`AccessRequestApiTest` has 14 tests that start the app and exercise request
creation and decisions, input validation, registration and sign-in, profile
updates, authentication, and employee data boundaries.

## Known gaps (honest list)

- Manager invitations are shared-secret based; production should use an
  organization identity provider and managed manager provisioning.
- Render's free Postgres database is temporary (currently expires after 30 days).
  Upgrade it or use another managed database for retained production data.
- The request list has no paging, and there are no automated frontend tests.

## Publish a public demo on Render

The app is configured as a single Docker web service with a PostgreSQL database
using `Dockerfile` and `render.yaml`:

1. Sign in to Render and choose **New → Blueprint**.
2. Connect `https://github.com/poornesh1703/Workspace` and select the `main` branch.
3. When prompted, set `MANAGER_INVITE_CODE` to a long, unique secret. Do not commit
   the code to GitHub or share it publicly; provide it only to trusted managers.
4. Review the services and costs shown by Render, then confirm deployment. Render
   builds the React UI and Spring Boot API together and provides a public HTTPS URL.

The current free database is intended for a short-lived demo only. A persistent
production deployment requires a paid database plan and backups.

