import { useEffect, useState, Suspense, lazy } from "react";
import {
  BrowserRouter,
  NavLink,
  Route,
  Routes,
  Navigate,
  useLocation,
} from "react-router-dom";
import {
  Activity,
  ArrowUpRight,
  Boxes,
  CalendarClock,
  ChevronDown,
  GitBranch,
  LayoutDashboard,
  LogOut,
  Menu,
  Settings2,
  X,
} from "lucide-react";
import { AuthContext, useAuth } from "./auth";
import { setToken } from "./api";
import type { Session } from "./types";
import { Loading } from "./components/ui";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Workflows from "./pages/Workflows";
import Executions from "./pages/Executions";
import ExecutionDetail from "./pages/ExecutionDetail";
import Operations from "./pages/Operations";
const Designer = lazy(() => import("./pages/Designer"));
const links = [
  { path: "/", label: "Overview", icon: LayoutDashboard },
  { path: "/workflows", label: "Workflows", icon: GitBranch },
  { path: "/executions", label: "Executions", icon: Activity },
  { path: "/tasks", label: "Tasks", icon: Boxes },
  { path: "/schedules", label: "Schedules", icon: CalendarClock },
  { path: "/settings", label: "Settings", icon: Settings2 },
];
function Shell() {
  const { session, logout } = useAuth();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  useEffect(() => setOpen(false), [location.pathname]);
  return (
    <div className="app-shell">
      <aside className={`sidebar ${open ? "open" : ""}`}>
        <NavLink to="/" className="brand">
          <span className="brand-mark">
            <GitBranch size={23} />
          </span>
          flowlab<span className="brand-dot">.</span>
        </NavLink>
        <button
          className="mobile-close icon-button"
          aria-label="Close menu"
          onClick={() => setOpen(false)}
        >
          <X />
        </button>
        <div className="workspace">
          <span className="workspace-avatar">F</span>
          <div>
            FlowLab workspace<small>Local environment</small>
          </div>
          <ChevronDown size={14} />
        </div>
        <div className="nav-label">WORKSPACE</div>
        <nav>
          {links.map(({ path, label, icon: Icon }) => (
            <NavLink key={path} to={path} end={path === "/"}>
              <Icon size={18} />
              {label}
              {path === "/" && <span className="nav-shortcut">⌘ 1</span>}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="durability-note">
            <span className="tiny-dot" />
            Durable by design
            <p>
              Your workflow state survives
              <br />
              the unexpected.
            </p>
            <a href="/swagger-ui/index.html" target="_blank" rel="noreferrer">
              Explore API docs
              <ArrowUpRight size={13} />
            </a>
          </div>
          <button className="profile" onClick={logout} title="Sign out">
            <span className="avatar">
              {session?.username.slice(0, 2).toUpperCase()}
            </span>
            <span>
              {session?.username}
              <small>{session?.role.toLowerCase()} account</small>
            </span>
            <LogOut size={16} />
          </button>
        </div>
      </aside>
      {open && (
        <button
          aria-label="Close navigation"
          className="mobile-scrim"
          onClick={() => setOpen(false)}
        />
      )}
      <div className="main-column">
        <header className="topbar">
          <button
            className="mobile-menu icon-button"
            aria-label="Open menu"
            onClick={() => setOpen(true)}
          >
            <Menu size={20} />
          </button>
          <div className="breadcrumb">
            Workspace <span>/</span>{" "}
            <strong>
              {links.find(
                (l) => l.path !== "/" && location.pathname.startsWith(l.path),
              )?.label ?? "Overview"}
            </strong>
          </div>
          <div className="topbar-right">
            <span className="env-tag">
              <span className="tiny-dot" /> Local
            </span>
            <span className="version-label">v1.0</span>
          </div>
        </header>
        <main>
          <Suspense fallback={<Loading />}>
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/workflows" element={<Workflows />} />
              <Route path="/workflows/new" element={<Designer />} />
              <Route path="/workflows/:id" element={<Designer />} />
              <Route path="/executions" element={<Executions />} />
              <Route path="/executions/:id" element={<ExecutionDetail />} />
              <Route path="/tasks" element={<Operations section="tasks" />} />
              <Route
                path="/schedules"
                element={<Operations section="schedules" />}
              />
              <Route
                path="/settings"
                element={<Operations section="settings" />}
              />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Suspense>
        </main>
        <footer className="app-footer">
          <span>FlowLab / Built for the long run.</span>
          <span>PostgreSQL · Durable state</span>
        </footer>
      </div>
    </div>
  );
}
export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const logout = () => {
    setToken(null);
    setSession(null);
  };
  useEffect(() => {
    window.addEventListener("flowlab:unauthorized", logout);
    return () => window.removeEventListener("flowlab:unauthorized", logout);
  }, []);
  return (
    <AuthContext.Provider
      value={{
        session,
        login: (s) => {
          setToken(s.accessToken);
          setSession(s);
        },
        logout,
      }}
    >
      <BrowserRouter>{session ? <Shell /> : <Login />}</BrowserRouter>
    </AuthContext.Provider>
  );
}
