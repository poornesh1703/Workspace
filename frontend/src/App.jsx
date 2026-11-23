import { useEffect, useMemo, useState } from "react";
import {
  listRequests,
  createRequest,
  approveRequest,
  rejectRequest,
  login,
  register,
  updateProfile,
} from "./api.js";

const SYSTEMS = [
  { value: "GITHUB", label: "GitHub", initials: "GH", tone: "violet" },
  { value: "AWS", label: "AWS", initials: "AWS", tone: "orange" },
  { value: "SLACK", label: "Slack", initials: "SL", tone: "pink" },
  { value: "GOOGLE_WORKSPACE", label: "Google Workspace", initials: "GW", tone: "blue" },
];

const systemInfo = (value) => SYSTEMS.find((s) => s.value === value) ?? SYSTEMS[0];

const formatTime = (iso) =>
  new Date(iso).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });

function Icon({ name, size = 18 }) {
  const paths = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></>,
    shield: <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    users: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></>,
    plus: <><path d="M12 5v14M5 12h14"/></>,
    search: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
    chevron: <path d="m9 18 6-6-6-6"/>,
    check: <path d="m5 12 4 4L19 6"/>,
    x: <><path d="m7 7 10 10M17 7 7 17"/></>,
    inbox: <><path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11Z"/></>,
    logout: <><path d="M10 17l5-5-5-5"/><path d="M15 12H3"/><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6"/></>,
  };
  return <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>;
}

export default function App() {
  const [session, setSession] = useState(() => {
    try { return JSON.parse(localStorage.getItem("vigilstack-session")) || null; }
    catch { return null; }
  });
  const [role, setRole] = useState(session?.role === "MANAGER" ? "Manager" : "Employee");
  const [managerName, setManagerName] = useState(session?.role === "MANAGER" ? (session.fullName ?? session.username ?? "") : "");
  const [requests, setRequests] = useState([]);
  const [section, setSection] = useState("overview");
  const [activityType, setActivityType] = useState(null);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [peopleSearch, setPeopleSearch] = useState("");
  const [filter, setFilter] = useState("ALL");
  const [showForm, setShowForm] = useState(false);
  const [showProfile, setShowProfile] = useState(false);

  async function refresh() {
    try {
      setRequests(await listRequests());
      setError("");
    } catch (e) {
      setError("Could not load requests. Is the backend running? " + e.message);
    }
  }

  useEffect(() => { refresh(); }, []);

  async function run(action) {
    setError("");
    try {
      await action();
      await refresh();
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    }
  }

  async function saveProfile(profile) {
    const updatedSession = await updateProfile(session.id, session.role, profile);
    localStorage.setItem("vigilstack-session", JSON.stringify(updatedSession));
    setSession(updatedSession);
    setManagerName(updatedSession.role === "MANAGER" ? updatedSession.fullName : "");
    return updatedSession;
  }

  const counts = useMemo(() => ({
    pending: requests.filter((r) => r.status === "PENDING").length,
    approved: requests.filter((r) => r.status === "APPROVED").length,
    rejected: requests.filter((r) => r.status === "REJECTED").length,
  }), [requests]);

  const visibleRequests = requests.filter((r) => {
    const matchesFilter = filter === "ALL" || r.status === filter;
    const term = search.trim().toLowerCase();
    const matchesSearch = !term || `${r.employeeName} ${systemInfo(r.system).label} ${r.reason}`.toLowerCase().includes(term);
    return matchesFilter && matchesSearch;
  });

  const activityEvents = useMemo(() => requests
    .flatMap((request) => (request.history ?? []).map((entry) => ({
      ...entry,
      requestId: request.id,
      employeeName: request.employeeName,
      system: request.system,
      status: request.status,
    })))
    .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp)), [requests]);

  const people = useMemo(() => {
    const directory = new Map();
    const addPerson = (name, role, requestCount = 0, reviewCount = 0) => {
      if (!name) return;
      const key = name.trim().toLocaleLowerCase();
      const person = directory.get(key) ?? { name: name.trim(), roles: new Set(), requests: 0, reviews: 0 };
      person.roles.add(role);
      person.requests += requestCount;
      person.reviews += reviewCount;
      directory.set(key, person);
    };

    requests.forEach((request) => {
      addPerson(request.employeeName, "Employee", 1);
      (request.history ?? []).filter((entry) => entry.action !== "CREATED")
        .forEach((entry) => addPerson(entry.actor, "Manager", 0, 1));
    });
    addPerson(session?.fullName ?? session?.username ?? session?.email, role);

    return [...directory.values()]
      .map((person) => ({ ...person, roles: [...person.roles] }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [requests, session, role]);

  const visiblePeople = people.filter((person) =>
    `${person.name} ${person.roles.join(" ")}`.toLowerCase().includes(peopleSearch.trim().toLowerCase()));

  const pageTitles = {
    overview: { eyebrow: "VIGILSTACK / GOVERNANCE", title: "Access overview" },
    activity: { eyebrow: "VIGILSTACK / AUDIT", title: "Activity log" },
    people: { eyebrow: "VIGILSTACK / DIRECTORY", title: "People" },
  };
  const activeSection = role === "Employee" ? "overview" : section;
  const activityPageTitles = { CREATED: "Requested activity", APPROVED: "Approved requests", REJECTED: "Rejected requests" };
  const currentPageTitle = activeSection === "activity" && activityType
    ? { eyebrow: "VIGILSTACK / AUDIT", title: activityPageTitles[activityType] }
    : pageTitles[activeSection];

  if (!session) {
    return <AuthPage onAuthenticated={(nextSession) => {
      localStorage.setItem("vigilstack-session", JSON.stringify(nextSession));
      setSession(nextSession);
      setRole(nextSession.role === "MANAGER" ? "Manager" : "Employee");
      setManagerName(nextSession.role === "MANAGER" ? (nextSession.fullName ?? nextSession.username ?? "") : "");
      setSection("overview");
    }} />;
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><div className="brand-mark"><Icon name="shield" size={20} /></div><span>Vigil<span>Stack</span></span></div>
        <nav className="nav">
          <button className={`nav-item ${activeSection === "overview" ? "active" : ""}`} onClick={() => setSection("overview")} aria-label="Overview" title="Overview" aria-current={activeSection === "overview" ? "page" : undefined}><Icon name="grid" /><span>Overview</span></button>
          {role === "Manager" && <>
            <button className={`nav-item ${activeSection === "activity" ? "active" : ""}`} onClick={() => { setSection("activity"); setActivityType(null); }} aria-label="Activity" title="Activity" aria-current={activeSection === "activity" ? "page" : undefined}><Icon name="clock" /><span>Activity</span></button>
            <button className={`nav-item ${activeSection === "people" ? "active" : ""}`} onClick={() => setSection("people")} aria-label="People" title="People" aria-current={activeSection === "people" ? "page" : undefined}><Icon name="users" /><span>People</span></button>
          </>}
        </nav>
        <div className="sidebar-footer">
          <button className="profile-trigger" onClick={() => setShowProfile(true)} aria-label="Edit profile" title="Edit profile">
            <Avatar name={session.fullName} photo={session.profilePhoto} role={session.role} />
            <span className="profile-trigger-copy"><strong>{session.fullName ?? session.username ?? session.email}</strong><small>{role} · Edit profile</small></span>
          </button>
          <button className="signout-button" onClick={() => { localStorage.removeItem("vigilstack-session"); setSession(null); setSection("overview"); }} aria-label="Logout" title="Logout"><Icon name="logout" size={17} /><span>Logout</span></button>
        </div>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">{currentPageTitle.eyebrow}</p>
            <h1>{currentPageTitle.title}</h1>
          </div>
          <div className="header-actions">
            <div className="signed-role"><span className="online-dot"/> {role} session</div>
            {activeSection === "overview" && role === "Employee" && <button className="primary-button" onClick={() => setShowForm(true)}><Icon name="plus" size={17}/> New request</button>}
          </div>
        </header>

        {error && <div className="error-banner" role="alert">{error}</div>}

        {activeSection === "overview" && <>
        <section className="stats-grid" aria-label="Request summary">
          <StatCard label="Total requests" value={requests.length} icon="inbox" tone="neutral" />
          <StatCard label="Awaiting review" value={counts.pending} icon="clock" tone="amber" />
          <StatCard label="Approved" value={counts.approved} icon="check" tone="green" />
          <StatCard label="Rejected" value={counts.rejected} icon="x" tone="red" />
        </section>

        {role === "Manager" && (
          <div className="manager-strip">
            <div><span className="online-dot"/><strong>Manager workspace</strong></div>
            <label>Manager name<input value={managerName} onChange={(e) => setManagerName(e.target.value)} placeholder="Required for decisions" /></label>
          </div>
        )}

        <section className="request-section">
          <div className="section-heading">
            <div><p className="eyebrow">REQUEST QUEUE</p><h2>All requests</h2></div>
            <div className="controls">
              <div className="search"><Icon name="search" size={17}/><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search requests" /></div>
              <select className="filter" value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Filter requests">
                <option value="ALL">All status</option><option value="PENDING">Pending</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option>
              </select>
            </div>
          </div>

          {visibleRequests.length === 0 ? (
            <div className="empty-state"><div className="empty-icon"><Icon name="inbox" size={24}/></div><h3>No requests found</h3><p>{requests.length ? "Try changing your search or filter." : "Create the first access request to begin."}</p></div>
          ) : (
            <div className="request-list">
              {visibleRequests.map((request) => <RequestCard key={request.id} request={request} isManager={role === "Manager"} managerName={managerName} onApprove={() => run(() => approveRequest(request.id, managerName))} onReject={(reason) => run(() => rejectRequest(request.id, managerName, reason))} />)}
            </div>
          )}
        </section>
        </>}

        {activeSection === "activity" && (activityType
          ? <ActivityDetail type={activityType} events={activityEvents.filter((event) => event.action === activityType)} onBack={() => setActivityType(null)} onShowRequest={() => { setActivityType(null); setSection("overview"); }} />
          : <ActivityView events={activityEvents} onOpenActivity={setActivityType} onShowRequest={() => setSection("overview")} />)}

        {activeSection === "people" && <PeopleView people={visiblePeople} totalPeople={people.length} search={peopleSearch} onSearch={setPeopleSearch} />}
      </main>

      {showForm && role === "Employee" && (
        <NewRequestModal onClose={() => setShowForm(false)} onSubmit={async (name, system, reason) => {
          const ok = await run(() => createRequest(name, system, reason));
          if (ok) setShowForm(false);
        }} />
      )}

      {showProfile && <ProfileModal session={session} onClose={() => setShowProfile(false)} onSave={saveProfile} />}
    </div>
  );
}

function Avatar({ name, photo, role, size = 38 }) {
  return photo
    ? <img className="avatar avatar-photo" src={photo} alt={`${name}'s profile`} style={{ width: size, height: size }} />
    : <span className="avatar" style={{ width: size, height: size }}>{name?.trim()?.[0]?.toUpperCase() ?? (role === "MANAGER" ? "M" : "E")}</span>;
}

function ProfileModal({ session, onClose, onSave }) {
  const [fullName, setFullName] = useState(session.fullName ?? "");
  const [email, setEmail] = useState(session.email ?? "");
  const [mobileNumber, setMobileNumber] = useState(session.mobileNumber ?? "");
  const [currentPassword, setCurrentPassword] = useState("");
  const [profilePhoto, setProfilePhoto] = useState(session.profilePhoto ?? "");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function choosePhoto(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
      setError("Choose a PNG, JPEG or WebP image.");
      event.target.value = "";
      return;
    }
    if (file.size > 256 * 1024) {
      setError("Profile photo must be 256 KB or smaller.");
      event.target.value = "";
      return;
    }
    const reader = new FileReader();
    reader.onload = () => { setProfilePhoto(String(reader.result)); setError(""); };
    reader.onerror = () => setError("Could not read the selected photo.");
    reader.readAsDataURL(file);
  }

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await onSave({ fullName, email, mobileNumber, currentPassword, profilePhoto: profilePhoto || null });
      onClose();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setBusy(false);
    }
  }

  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <section className="modal profile-modal" role="dialog" aria-modal="true" aria-labelledby="profile-title">
      <div className="modal-header"><div><p className="eyebrow">ACCOUNT SETTINGS</p><h2 id="profile-title">Edit profile</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><Icon name="x" /></button></div>
      <form onSubmit={submit}>
        <div className="profile-photo-picker"><Avatar name={fullName} photo={profilePhoto} role={session.role} size={72}/><label className="text-button photo-upload">{profilePhoto ? "Change photo" : "Add profile photo"}<input type="file" accept="image/png,image/jpeg,image/webp" onChange={choosePhoto} /></label>{profilePhoto && <button className="photo-remove" type="button" onClick={() => setProfilePhoto("")}>Remove photo</button>}<small>PNG, JPEG or WebP · up to 256 KB</small></div>
        <div className="profile-fields">
          <label className="field">Name<input autoComplete="name" maxLength={100} value={fullName} onChange={(event) => setFullName(event.target.value)} required /></label>
          <label className="field">Email<input type="email" autoComplete="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
          <label className="field">Mobile number<input type="tel" autoComplete="tel" maxLength={24} value={mobileNumber} onChange={(event) => setMobileNumber(event.target.value)} required /></label>
          <label className="field">Current password<input type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required /><small>Confirm your password to save profile changes.</small></label>
        </div>
        {error && <div className="auth-error" role="alert">{error}</div>}
        <div className="modal-actions"><button className="text-button" type="button" onClick={onClose}>Cancel</button><button className="primary-button" type="submit" disabled={busy}>{busy ? "Saving..." : "Save changes"}</button></div>
      </form>
    </section>
  </div>;
}

function ActivityView({ events, onOpenActivity, onShowRequest }) {
  const groups = [
    { action: "CREATED", title: "Requested", description: "Every access request submitted", empty: "No requests have been submitted yet." },
    { action: "APPROVED", title: "Approved requests", description: "Every approval recorded in the audit trail", empty: "No requests have been approved yet." },
    { action: "REJECTED", title: "Rejected requests", description: "Every rejection recorded in the audit trail", empty: "No requests have been rejected." },
  ];

  return <section className="content-section">
    <div className="section-heading">
      <div><p className="eyebrow">COMPLETE AUDIT TRAIL</p><h2>Request history</h2></div>
      <span className="section-count">{events.length} {events.length === 1 ? "history entry" : "history entries"}</span>
    </div>
    <div className="activity-groups">
      {groups.map((group) => {
        const groupEvents = events.filter((event) => event.action === group.action);
        return <section className="activity-group" key={group.action}>
          <header className="activity-group-header">
            <button className="activity-group-open" onClick={() => onOpenActivity(group.action)} aria-label={`Open ${group.title.toLowerCase()} activity`}>
              <span><strong>{group.title}</strong><small>{group.description}</small></span>
              <span className="activity-open-label">Open page <Icon name="chevron" size={14}/></span>
            </button>
            <span className="section-count">{groupEvents.length}</span>
          </header>
          {groupEvents.length === 0 ? <p className="activity-group-empty">{group.empty}</p> : <div className="activity-list">
            {groupEvents.slice(0, 3).map((event) => <ActivityEntry key={`${event.requestId}-${event.id ?? event.timestamp}-${event.action}`} event={event} onShowRequest={onShowRequest} />)}
            {groupEvents.length > 3 && <button className="activity-more" onClick={() => onOpenActivity(group.action)}>View all {groupEvents.length} entries</button>}
          </div>}
        </section>;
      })}
    </div>
  </section>;
}

function ActivityDetail({ type, events, onBack, onShowRequest }) {
  const headings = {
    CREATED: { eyebrow: "REQUEST SUBMISSIONS", title: "Requested activity", empty: "No requests have been submitted yet." },
    APPROVED: { eyebrow: "MANAGER DECISIONS", title: "Approved requests", empty: "No requests have been approved yet." },
    REJECTED: { eyebrow: "MANAGER DECISIONS", title: "Rejected requests", empty: "No requests have been rejected." },
  };
  const page = headings[type];

  return <section className="content-section activity-detail">
    <button className="activity-back" onClick={onBack}>← Back to Activity</button>
    <div className="section-heading">
      <div><p className="eyebrow">{page.eyebrow}</p><h2>{page.title}</h2></div>
      <span className="section-count">{events.length} {events.length === 1 ? "entry" : "entries"}</span>
    </div>
    {events.length === 0
      ? <div className="empty-state"><div className="empty-icon"><Icon name="clock" size={24}/></div><h3>No activity yet</h3><p>{page.empty}</p></div>
      : <div className="activity-list">{events.map((event) => <ActivityEntry key={`${event.requestId}-${event.id ?? event.timestamp}-${event.action}`} event={event} onShowRequest={onShowRequest} />)}</div>}
  </section>;
}

function ActivityEntry({ event, onShowRequest }) {
  const actionLabels = { CREATED: "submitted an access request", APPROVED: "approved an access request", REJECTED: "rejected an access request" };
  const icon = event.action === "APPROVED" ? "check" : event.action === "REJECTED" ? "x" : "plus";

  return <article className="activity-item">
    <div className={`activity-icon ${event.action.toLowerCase()}`}><Icon name={icon} size={16}/></div>
    <div className="activity-copy">
      <p><strong>{event.actor}</strong> {actionLabels[event.action] ?? "updated a request"}{event.action !== "CREATED" && <> for <strong>{event.employeeName}</strong></>}</p>
      <span>{systemInfo(event.system).label} · Request #{String(event.requestId).padStart(4, "0")}</span>
      {event.comment && <blockquote>{event.comment}</blockquote>}
    </div>
    <div className="activity-meta"><time dateTime={event.timestamp}>{formatTime(event.timestamp)}</time><button className="history-toggle" onClick={onShowRequest}>View requests<Icon name="chevron" size={14}/></button></div>
  </article>;
}

function PeopleView({ people, totalPeople, search, onSearch }) {
  return <section className="content-section">
    <div className="section-heading">
      <div><p className="eyebrow">REQUEST PARTICIPANTS</p><h2>People directory</h2></div>
      <div className="people-tools"><span className="section-count">{totalPeople} {totalPeople === 1 ? "person" : "people"}</span><div className="search"><Icon name="search" size={17}/><input value={search} onChange={(e) => onSearch(e.target.value)} placeholder="Search people" aria-label="Search people" /></div></div>
    </div>
    <p className="section-description">Each person is identified by their account role.</p>
    {people.length === 0 ? (
      <div className="empty-state"><div className="empty-icon"><Icon name="users" size={24}/></div><h3>{totalPeople ? "No people match" : "No people yet"}</h3><p>{totalPeople ? "Try a different name or role." : "People appear here after they submit or review a request."}</p></div>
    ) : (
      <div className="people-grid">
        {people.map((person) => <article className="person-card" key={person.name.toLowerCase()}>
          <div className="person-avatar">{person.name.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase()}</div>
          <div className="person-info"><h3>{person.name}</h3><div className="person-roles"><span className={`role-tag ${(person.roles.includes("Manager") ? "Manager" : "Employee").toLowerCase()}`}>{person.roles.includes("Manager") ? "Manager" : "Employee"}</span></div></div>
        </article>)}
      </div>
    )}
  </section>;
}

function StatCard({ label, value, icon, tone }) {
  return <article className="stat-card"><div className={`stat-icon ${tone}`}><Icon name={icon} size={19}/></div><div><p>{label}</p><strong>{value}</strong></div></article>;
}

function NewRequestModal({ onClose, onSubmit }) {
  const [name, setName] = useState("");
  const [system, setSystem] = useState("GITHUB");
  const [reason, setReason] = useState("");

  return <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
    <section className="modal" role="dialog" aria-modal="true" aria-labelledby="new-request-title">
      <div className="modal-header"><div><p className="eyebrow">NEW ACCESS REQUEST</p><h2 id="new-request-title">Request system access</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><Icon name="x" /></button></div>
      <div className="form-grid">
        <label className="field">Your name<input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Alex Morgan" /></label>
        <label className="field">System<select value={system} onChange={(e) => setSystem(e.target.value)}>{SYSTEMS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}</select></label>
        <label className="field field-full">Business reason<textarea rows={4} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Describe why this access is needed" /></label>
      </div>
      <div className="modal-actions"><button className="text-button" onClick={onClose}>Cancel</button><button className="primary-button" onClick={() => onSubmit(name, system, reason)}>Submit request</button></div>
    </section>
  </div>;
}

function RequestCard({ request, isManager, managerName, onApprove, onReject }) {
  const [showHistory, setShowHistory] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const info = systemInfo(request.system);
  const canDecide = isManager && request.status === "PENDING";

  async function confirmReject() {
    const ok = await onReject(rejectReason);
    if (ok) { setRejecting(false); setRejectReason(""); }
  }

  return <article className="request-card">
    <div className={`system-logo ${info.tone}`}>{info.initials}</div>
    <div className="request-main">
      <div className="request-title"><div><h3>{info.label}</h3><p><strong>{request.employeeName}</strong> requested access</p></div><span className={`status ${request.status.toLowerCase()}`}><i/> {request.status[0] + request.status.slice(1).toLowerCase()}</span></div>
      <p className="request-reason">“{request.reason}”</p>
      {canDecide && !rejecting && <div className="request-actions"><button className="approve-button" onClick={onApprove}><Icon name="check" size={16}/> Approve</button><button className="reject-button" onClick={() => setRejecting(true)}><Icon name="x" size={16}/> Reject</button></div>}
      {canDecide && rejecting && <div className="reject-panel"><textarea rows={2} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} placeholder="Reason for rejecting (required)"/><div className="reject-actions"><button className="text-button" onClick={() => setRejecting(false)}>Cancel</button><button className="reject-button" onClick={confirmReject}>Confirm rejection</button></div></div>}
    </div>
    <div className="request-meta"><span>REQUEST #{String(request.id).padStart(4, "0")}</span><button className="history-toggle" onClick={() => setShowHistory(!showHistory)}>{showHistory ? "Hide trail" : "View trail"}<Icon name="chevron" size={15}/></button></div>
    {showHistory && <div className="history"><p className="eyebrow">AUDIT TRAIL</p>{request.history.map((h) => <div className="history-item" key={h.id}><div className="history-node"><Icon name={h.action === "APPROVED" ? "check" : h.action === "REJECTED" ? "x" : "plus"} size={13}/></div><div><strong>{h.action.toLowerCase()}</strong> by {h.actor}<span>{formatTime(h.timestamp)}</span>{h.comment && <p>“{h.comment}”</p>}</div></div>)}</div>}
  </article>;
}

function AuthPage({ onAuthenticated }) {
  const [mode, setMode] = useState("login");
  const [role, setRole] = useState("EMPLOYEE");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [mobileNumber, setMobileNumber] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const account = mode === "login"
        ? await login(fullName, email, password, role)
        : await register(fullName, email, mobileNumber, password, role);
      onAuthenticated(account);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return <main className="auth-page">
    <section className="auth-brand">
      <div className="brand auth-logo"><div className="brand-mark"><Icon name="shield" size={21}/></div><span>Vigil<span>Stack</span></span></div>
      <div className="auth-quote"><p className="eyebrow">ACCESS GOVERNANCE</p><h1>Every permission,<br/><em>accounted for.</em></h1><p>Request, review and audit system access from one secure workspace.</p></div>
    </section>
    <section className="auth-panel">
      <div className="auth-card">
        <p className="eyebrow">{mode === "login" ? "WELCOME BACK" : "CREATE ACCOUNT"}</p>
        <h2>{mode === "login" ? "Sign in to your workspace" : "Register your account"}</h2>
        <div className="auth-tabs"><button className={mode === "login" ? "active" : ""} onClick={() => { setMode("login"); setError(""); }}>Sign in</button><button className={mode === "register" ? "active" : ""} onClick={() => { setMode("register"); setError(""); }}>Register</button></div>
        <form onSubmit={submit}>
          <label className="field">Account type<select value={role} onChange={(e) => setRole(e.target.value)}><option value="EMPLOYEE">Employee</option><option value="MANAGER">Manager</option></select></label>
          <label className="field">Name<input autoComplete="name" maxLength={100} value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Enter your full name" required /></label>
          <label className="field">Email<input type="email" autoComplete="email" maxLength={254} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" required /></label>
          {mode === "register" && <>
            <label className="field">Mobile number<input type="tel" autoComplete="tel" maxLength={24} value={mobileNumber} onChange={(e) => setMobileNumber(e.target.value)} placeholder="+1 415 555 0134" required /></label>
          </>}
          <label className="field">Password<input type="password" autoComplete={mode === "login" ? "current-password" : "new-password"} minLength={mode === "register" ? 8 : undefined} maxLength={mode === "register" ? 72 : undefined} value={password} onChange={(e) => setPassword(e.target.value)} placeholder={mode === "register" ? "At least 8 characters" : "Enter your password"} required /></label>
          {error && <div className="auth-error" role="alert">{error}</div>}
          <button className="auth-submit" type="submit" disabled={busy}>{busy ? "Please wait..." : mode === "login" ? "Sign in" : "Create account"}</button>
        </form>
      </div>
    </section>
  </main>;
}
