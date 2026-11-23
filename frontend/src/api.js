// All the calls to the backend live here, so the UI code stays simple.

async function call(url, options) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    // The backend sends errors as {"error": "message"}
    throw new Error(data?.error || "Something went wrong.");
  }
  return data;
}

export const listRequests = () => call("/api/requests");

export const createRequest = (employeeName, system, reason) =>
  call("/api/requests", {
    method: "POST",
    body: JSON.stringify({ employeeName, system, reason }),
  });

export const approveRequest = (id, managerName) =>
  call(`/api/requests/${id}/approve`, {
    method: "POST",
    body: JSON.stringify({ managerName }),
  });

export const rejectRequest = (id, managerName, reason) =>
  call(`/api/requests/${id}/reject`, {
    method: "POST",
    body: JSON.stringify({ managerName, reason }),
  });

export const login = (fullName, email, password, role) =>
  call("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ fullName, email, password, role }),
  });

export const register = (fullName, email, mobileNumber, password, role) =>
  call("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ fullName, email, mobileNumber, password, role }),
  });

export const updateProfile = (id, role, profile) =>
  call(`/api/auth/profile/${id}?role=${encodeURIComponent(role)}`, {
    method: "PUT",
    body: JSON.stringify(profile),
  });
