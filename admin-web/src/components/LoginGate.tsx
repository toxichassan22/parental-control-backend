import { useState } from "react";

interface LoginGateProps {
  loading: boolean;
  error: string | null;
  onLogin: (email: string, password: string) => Promise<void>;
}

export function LoginGate({ loading, error, onLogin }: LoginGateProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <p className="eyebrow">Parental Control SaaS</p>
        <h1>لوحة المشرف</h1>
        <p className="muted">
          سجّل الدخول بحساب Firebase admin لعرض الأجهزة، تعديل السياسات، وإرسال أوامر القفل.
        </p>

        <form
          className="auth-form"
          onSubmit={async (event) => {
            event.preventDefault();
            await onLogin(email, password);
          }}
        >
          <label>
            البريد الإلكتروني
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
          </label>
          <label>
            كلمة المرور
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              required
            />
          </label>
          {error ? <div className="error-banner">{error}</div> : null}
          <button type="submit" disabled={loading}>
            {loading ? "جارٍ التحقق..." : "دخول"}
          </button>
        </form>
      </div>
    </div>
  );
}

