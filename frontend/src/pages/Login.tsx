import { useState } from "react";
import type { FormEvent } from "react";
import {
  ArrowRight,
  GitBranch,
  ShieldCheck,
  RotateCcw,
  Layers3,
} from "lucide-react";
import { api, errorMessage } from "../api";
import { useAuth } from "../auth";
import { ErrorBox } from "../components/ui";
import type { Session } from "../types";
export default function Login() {
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      login(
        (await api.post<Session>("/auth/login", { username, password })).data,
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login-page">
      <section className="login-story">
        <a className="brand" href="/">
          <span className="brand-mark">
            <GitBranch size={23} />
          </span>
          flowlab<span className="brand-dot">.</span>
        </a>
        <div className="login-story-body">
          <div className="eyebrow">BUILT TO KEEP GOING</div>
          <h1>
            Every step.
            <br />
            Safely in motion.
          </h1>
          <p>
            Design, run, and understand durable workflows.
            <br />
            Pick up exactly where your system left off.
          </p>
          <div className="login-flow">
            <div>
              <ShieldCheck />
              <span>Validate</span>
              <small>Completed</small>
            </div>
            <i />
            <div>
              <Layers3 />
              <span>Process</span>
              <small>Persisted</small>
            </div>
            <i />
            <div>
              <RotateCcw />
              <span>Recover</span>
              <small>Resilient</small>
            </div>
          </div>
        </div>
        <span className="login-footer">
          FLOWLAB / WORKFLOW ORCHESTRATION ENGINE
        </span>
      </section>
      <section className="login-form">
        <div>
          <div className="eyebrow">YOUR OPERATIONS, IN FOCUS</div>
          <h2>Welcome to FlowLab</h2>
          <p className="muted">Sign in to your workflow workspace.</p>
          <form onSubmit={submit}>
            <label>
              Username
              <input
                autoComplete="username"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Your username"
              />
            </label>
            <label>
              Password
              <input
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter your password"
              />
            </label>
            <ErrorBox message={error} />
            <button disabled={busy} className="button primary wide">
              {busy ? "Signing in…" : "Open workspace"}
              <ArrowRight size={17} />
            </button>
          </form>
          <p className="login-help">
            Use the account configured by your administrator.
            <br />
            Credentials are never prefilled or stored in the browser.
          </p>
        </div>
      </section>
    </div>
  );
}
