// All the calls to the backend live here, so the UI code stays simple.

async function call(url, options) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new Event("vigilstack-session-expired"));
    // The backend sends errors as {"error": "message"}
    throw new Error(data?.error || "Something went wrong.");
  }
  return data;
}

export const listRequests = () => call("/api/requests");

export const createRequest = (system, reason) =>
  call("/api/requests", {
    method: "POST",
    body: JSON.stringify({ system, reason }),
  });

export const approveRequest = (id) =>
  call(`/api/requests/${id}/approve`, {
    method: "POST",
    body: JSON.stringify({}),
  });

export const rejectRequest = (id, reason) =>
  call(`/api/requests/${id}/reject`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });

export const login = (fullName, email, password, role) =>
  call("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ fullName, email, password, role }),
  });

export const register = (fullName, email, mobileNumber, password, role, managerInviteCode) =>
  call("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ fullName, email, mobileNumber, password, role, managerInviteCode }),
  });

export const logout = () => call("/api/auth/logout", { method: "POST" });

export const updateProfile = (id, role, profile) =>
  call(`/api/auth/profile/${id}?role=${encodeURIComponent(role)}`, {
    method: "PUT",
    body: JSON.stringify(profile),
  });
