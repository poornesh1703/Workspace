# Development Progress — 28 November 2026

Repository: `poornesh1703/Workspace`  
Project: VigilStack Access Request App

## Completed today

### Backend request ownership

- Added an employee account ID field to `AccessRequest` so each newly created request can be associated with its authenticated owner.
- Added a repository query to retrieve requests for one employee, ordered newest first.
- Updated `AccessRequestService` with separate methods for listing all requests and listing one employee's requests.
- Updated `AccessRequestController` to derive the employee identity and name from the authenticated server-side session rather than trusting the request payload.
- Employees can create requests and retrieve only their own requests.
- Managers can retrieve all requests and approve or reject requests.
- The manager identity recorded in the audit history is derived from the authenticated account.
- Rejection still requires a reason.
- Existing request rows are retained; the new ownership column is nullable, so older requests without an owner are not automatically assigned to an employee.

### Security configuration

- Added role-based route restrictions for request endpoints.
- Approval and rejection endpoints require the Manager role.
- The request API is restricted to Employee and Manager roles.
- HR API routes remain restricted to the HR role.
- Existing CSRF and session security configuration was retained.

### Frontend API

- Updated the frontend create-request API call to send only `system` and `reason`.
- The backend now supplies the employee name and ownership from the authenticated account.

### Local run and verification

- Java 17 and Maven were configured in the active PowerShell session.
- Maven successfully compiled the test source during `mvn spring-boot:run`.
- Spring Boot started successfully on port 8080 and connected to the existing H2 file database at `backend/data/accessdb`.
- The React/Vite development server was started on port 5173 during the local run.

## Issues observed / follow-up

- The UI displayed **“Logout failed: Something went wrong.”** The logout endpoint exists, but the HTTP response/status has not yet been inspected; the cause is not confirmed.
- One backend startup logged **“Initial HR setup is not configured.”** The `INITIAL_HR_*` environment variables were absent in that PowerShell session. Configure them locally in the same session used to start the backend; never commit credentials or passwords.
- Employee-versus-manager UI behavior still needs end-to-end verification after resolving the logout issue.
- Existing requests created before owner tracking have no reliable owner association and therefore are not included in an employee's personal request list.

## Next steps

1. Inspect the logout request in Chrome DevTools Network and the backend log; fix the confirmed cause.
2. Verify the Employee view shows only the employee's own profile and requests, with no manager controls or other employees' data.
3. Verify the Manager view can see all requests and approve/reject with audit history.
4. Verify HR provisioning and registration using locally configured environment variables.
5. Run the backend test suite and frontend production build, then record the actual results.

## Data and security notes

- Do not delete or reset the existing H2 database.
- Do not commit passwords, HR environment-variable values, SMTP credentials, tokens, or other secrets.
- Access revocation integrations and approval email delivery are not claimed as implemented or verified in this progress update.
