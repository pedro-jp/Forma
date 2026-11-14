import React, { useState } from 'react';
import { AlertCircle, ArrowRight, Eye, EyeOff } from 'lucide-react';

interface LoginModalProps {
  onLoginSuccess: (username: string) => void;
}

export const LoginModal: React.FC<LoginModalProps> = ({ onLoginSuccess }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const resp = await fetch('/api/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ username: username.trim(), password: password.trim() })
      });

      const data = await resp.json();

      if (resp.ok) {
        onLoginSuccess(data.username || username);
      } else {
        setError(data.error || data.message || 'Credenciais inválidas. Verifique usuário e senha.');
      }
    } catch {
      setError('Não foi possível conectar ao backend Forma. Verifique se o servidor está ativo.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="auth-viewport">
      <section className="auth-card" aria-labelledby="login-heading">
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '24px' }}>
          <div className="brand-mark" aria-hidden="true">
            <svg width="15" height="15" viewBox="0 0 16 16" fill="none">
              <rect x="2" y="2" width="12" height="2.6" rx="1" fill="currentColor" />
              <rect x="2" y="6.7" width="8.5" height="2.6" rx="1" fill="currentColor" />
              <rect x="2" y="11.4" width="3.2" height="2.6" rx="1" fill="currentColor" />
            </svg>
          </div>
          <div>
            <div className="brand-title">Forma</div>
            <div className="brand-scope">Console Administrativo</div>
          </div>
        </div>

        <h1 id="login-heading" className="page-title" style={{ marginBottom: '4px' }}>
          Entrar no console
        </h1>
        <p className="page-subtitle" style={{ marginBottom: '22px' }}>
          Gerenciamento de planos, preços, assinaturas, cupons e proxy Gemini.
        </p>

        {error && (
          <div className="notice-banner notice-neg" role="alert" style={{ marginBottom: '18px' }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
              <AlertCircle size={15} style={{ flexShrink: 0 }} />
              {error}
            </span>
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div className="field">
            <label htmlFor="admin-username" className="field-label">
              Usuário administrador
            </label>
            <input
              id="admin-username"
              className="input"
              type="text"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Seu usuário"
              required
            />
          </div>

          <div className="field">
            <label htmlFor="admin-password" className="field-label">
              Senha de acesso
            </label>
            <div style={{ position: 'relative' }}>
              <input
                id="admin-password"
                className="input"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Digite sua senha"
                required
                style={{ paddingRight: '38px' }}
              />
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                style={{
                  position: 'absolute',
                  right: '8px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  color: 'var(--faint)',
                  padding: '4px',
                  display: 'inline-flex'
                }}
              >
                {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn btn-primary"
            style={{ width: '100%', height: '38px', marginTop: '6px' }}
          >
            <span>{loading ? 'Autenticando...' : 'Acessar console'}</span>
            {!loading && <ArrowRight size={15} />}
          </button>
        </form>

        <p style={{ marginTop: '20px', fontSize: '12px', color: 'var(--faint)', lineHeight: 1.5 }}>
          Acesso restrito a administradores autorizados.
        </p>
      </section>
    </main>
  );
};
