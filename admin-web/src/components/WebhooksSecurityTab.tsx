import React, { useState } from 'react';
import { RefreshCw, Send, ShieldCheck, ShieldAlert, FileText, Copy } from 'lucide-react';
import { AdminAuditItem, BillingDashboardData, SecurityIncidentItem, UserSummary } from '../types';
import { formatDateTimeBR } from './billingFormat';

interface WebhooksSecurityTabProps {
  data: BillingDashboardData;
  users: UserSummary[];
  auditLogs: AdminAuditItem[];
  incidents: SecurityIncidentItem[];
  onRefresh: () => void;
}

export const WebhooksSecurityTab: React.FC<WebhooksSecurityTabProps> = ({
  data,
  users,
  auditLogs,
  incidents,
  onRefresh
}) => {
  const [rotatedSecret, setRotatedSecret] = useState<string | null>(data.webhookConfig.rotatedPlaintextSecret || null);
  const [rotating, setRotating] = useState(false);
  const [simulating, setSimulating] = useState(false);
  const [simEmail, setSimEmail] = useState(users[0]?.email || '');
  const [simEventType, setSimEventType] = useState('PAYMENT_SUCCEEDED');
  const [simPlanId, setSimPlanId] = useState('PRO_MONTHLY');
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);

  const origin = typeof window !== 'undefined' ? window.location.origin : 'https://forma-94vp.onrender.com';
  const fullWebhookUrl = `${origin}${data.webhookConfig.endpointPath}`;

  const handleRotateSecret = async () => {
    if (!window.confirm('Gerar novo segredo HMAC-SHA256 invalidará assinaturas com o segredo anterior. Deseja continuar?')) {
      return;
    }
    setRotating(true);
    setFeedback(null);
    try {
      const resp = await fetch('/api/admin/billing/webhooks/rotate-secret', {
        method: 'POST',
        credentials: 'include'
      });
      if (resp.ok) {
        const cfg = await resp.json();
        setRotatedSecret(cfg.rotatedPlaintextSecret || null);
        setFeedback({ text: 'Novo segredo HMAC-SHA256 gerado e salvo cifrado em AES-256-GCM. Copie-o agora.', isError: false });
        onRefresh();
      }
    } finally {
      setRotating(false);
    }
  };

  const handleSimulateWebhook = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setSimulating(true);
    setFeedback(null);
    try {
      const resp = await fetch('/api/admin/billing/webhooks/simulate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          provider: 'ADMIN_SIMULATOR',
          eventType: simEventType,
          userEmail: simEmail,
          planId: simPlanId
        })
      });
      if (resp.ok) {
        const res = await resp.json();
        setFeedback({
          text: `Webhook assinado processado (${res.eventType} -> ${res.processingStatus}).`,
          isError: false
        });
        onRefresh();
      } else {
        const err = await resp.json().catch(() => ({}));
        setFeedback({ text: err.error || 'Falha ao simular webhook.', isError: true });
      }
    } finally {
      setSimulating(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div>
        <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '-0.3px' }}>Webhooks Assinados, Segurança &amp; Auditoria</h2>
        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>
          Receba eventos de cobrança via HMAC-SHA256 com proteção anti-replay e acompanhe as trilhas de auditoria do painel.
        </p>
      </div>

      {feedback && (
        <div style={{
          padding: '10px 14px',
          borderRadius: '10px',
          fontSize: '13px',
          backgroundColor: feedback.isError ? 'rgba(239,68,68,0.12)' : 'rgba(16,185,129,0.12)',
          border: `1px solid ${feedback.isError ? 'rgba(239,68,68,0.3)' : 'rgba(16,185,129,0.3)'}`,
          color: feedback.isError ? '#f87171' : '#34d399'
        }}>
          {feedback.text}
        </div>
      )}

      {/* Webhook Endpoint & Security Hardening Checklist */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '16px' }}>
        <section className="surface-panel" style={{ padding: '18px 20px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Configuração do Webhook de Cobrança</h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '12px' }}>
            <div className="surface-inset" style={{ padding: '10px 12px' }}>
              <div style={{ color: 'var(--text-muted)', marginBottom: '3px' }}>Endpoint POST</div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '8px' }}>
                <code className="font-mono" style={{ color: '#38bdf8', wordBreak: 'break-all' }}>{fullWebhookUrl}</code>
                <button
                  onClick={() => navigator.clipboard?.writeText(fullWebhookUrl)}
                  style={{ background: 'none', border: 'none', color: 'var(--text-muted)', flexShrink: 0 }}
                  title="Copiar URL"
                >
                  <Copy size={14} />
                </button>
              </div>
            </div>

            <div className="surface-inset" style={{ padding: '10px 12px' }}>
              <div style={{ color: 'var(--text-muted)', marginBottom: '3px' }}>Header HMAC-SHA256</div>
              <code className="font-mono">{data.webhookConfig.signatureHeader}: sha256=&lt;hmac_hex&gt;</code>
            </div>

            <div className="surface-inset" style={{ padding: '10px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '8px' }}>
              <div>
                <div style={{ color: 'var(--text-muted)', marginBottom: '2px' }}>Segredo Ativo (AES-256-GCM)</div>
                <code className="font-mono" style={{ color: '#34d399' }}>{data.webhookConfig.maskedSecret}</code>
              </div>
              <button
                onClick={handleRotateSecret}
                disabled={rotating}
                style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '7px 11px', borderRadius: '8px', border: '1px solid var(--border-color)', backgroundColor: 'rgba(255,255,255,0.06)', color: 'var(--text-primary)', fontSize: '12px', fontWeight: 700 }}
              >
                <RefreshCw size={13} /> Rotacionar
              </button>
            </div>

            {rotatedSecret && (
              <div style={{ padding: '10px 12px', borderRadius: '8px', backgroundColor: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.3)' }}>
                <div style={{ color: '#34d399', fontWeight: 700, marginBottom: '4px' }}>Novo segredo gerado (copie agora):</div>
                <code className="font-mono" style={{ wordBreak: 'break-all', color: '#f1f5f9' }}>{rotatedSecret}</code>
              </div>
            )}
          </div>
        </section>

        <section className="surface-panel" style={{ padding: '18px 20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <ShieldCheck size={16} color="#34d399" />
            <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Blindagem Ativa do Painel Admin</h3>
          </div>
          <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '12px', color: 'var(--text-secondary)' }}>
            <li>• Cookie administrativo <code className="font-mono">HttpOnly</code> + <code className="font-mono">SameSite=Strict</code> + <code className="font-mono">Secure</code></li>
            <li>• Proteção anti-CSRF em todas as mutações (<code className="font-mono">Sec-Fetch-Site</code> + <code className="font-mono">Origin</code>)</li>
            <li>• Bloqueio anti-força bruta no login (5 falhas em 10 min gera incidente ANPD)</li>
            <li>• Webhooks validados com HMAC-SHA256 em tempo constante + idempotência</li>
            <li>• Segredos (Gemini, Resend, Webhook) cifrados em AES-256-GCM + Supabase RLS</li>
          </ul>
        </section>
      </div>

      {/* Simulator + Webhook Log */}
      <section className="surface-panel" style={{ padding: '18px 20px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Simulador de Webhook Assinado (Teste ao Vivo)</h3>
        <form onSubmit={handleSimulateWebhook} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px', alignItems: 'end', marginBottom: '16px' }}>
          <div>
            <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-muted)', marginBottom: '4px' }}>Evento</label>
            <select value={simEventType} onChange={(e) => setSimEventType(e.target.value)} style={{ width: '100%', padding: '8px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '12px' }}>
              <option value="PAYMENT_SUCCEEDED">PAYMENT_SUCCEEDED (Renova/Ativa)</option>
              <option value="PAYMENT_FAILED">PAYMENT_FAILED (Inicia Dia 1 Atraso)</option>
              <option value="SUBSCRIPTION_PAUSED">SUBSCRIPTION_PAUSED</option>
              <option value="SUBSCRIPTION_CANCELED">SUBSCRIPTION_CANCELED</option>
              <option value="REFUNDED">REFUNDED (Estorna &amp; Volta Free)</option>
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-muted)', marginBottom: '4px' }}>Usuário Alvo</label>
            <select value={simEmail} onChange={(e) => setSimEmail(e.target.value)} style={{ width: '100%', padding: '8px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '12px' }}>
              {users.map((u) => (
                <option key={u.id} value={u.email}>{u.name} ({u.email})</option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-muted)', marginBottom: '4px' }}>Plano</label>
            <select value={simPlanId} onChange={(e) => setSimPlanId(e.target.value)} style={{ width: '100%', padding: '8px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '12px' }}>
              {data.plans.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>

          <button type="submit" disabled={simulating || !simEmail} style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '7px', padding: '9px 14px', borderRadius: '8px', border: 'none', backgroundColor: '#10b981', color: '#042f1a', fontSize: '12px', fontWeight: 800 }}>
            <Send size={13} /> {simulating ? 'Enviando...' : 'Disparar Webhook Assinado'}
          </button>
        </form>

        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Recebido em</th>
                <th>Event ID</th>
                <th>Provedor / Tipo</th>
                <th>Usuário</th>
                <th>Assinatura</th>
                <th>Resultado</th>
              </tr>
            </thead>
            <tbody>
              {data.recentWebhooks.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', padding: '22px', color: 'var(--text-muted)' }}>
                    Nenhum evento de webhook recebido até o momento.
                  </td>
                </tr>
              ) : (
                data.recentWebhooks.map((w) => (
                  <tr key={w.id}>
                    <td className="tabular-nums" style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>{formatDateTimeBR(w.receivedAtUtc)}</td>
                    <td><code className="font-mono" style={{ fontSize: '11px' }}>{w.eventId}</code></td>
                    <td style={{ fontSize: '12px', fontWeight: 600 }}>{w.provider} • {w.eventType}</td>
                    <td style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>{w.targetUserEmail || '—'}</td>
                    <td>
                      <span style={{ color: w.signatureValid ? '#34d399' : '#f87171', fontSize: '12px', fontWeight: 700 }}>
                        {w.signatureValid ? 'HMAC Válido' : 'Assinatura Inválida'}
                      </span>
                    </td>
                    <td style={{ fontSize: '12px' }}>{w.processingStatus}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Auditoria LGPD & Incidentes ANPD */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '16px' }}>
        <section className="surface-panel" style={{ padding: '18px 20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <FileText size={16} color="#34d399" />
            <h3 style={{ margin: 0, fontSize: '14px', fontWeight: 700 }}>Auditoria Administrativa (LGPD Art. 37)</h3>
          </div>
          {auditLogs.length === 0 ? (
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>Sem eventos administrativos recentes.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '240px', overflowY: 'auto' }}>
              {auditLogs.map((item) => (
                <div key={item.id} className="surface-inset" style={{ padding: '8px 10px', fontSize: '12px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                    <span>{item.actionType}</span>
                    <span className="tabular-nums" style={{ color: 'var(--text-muted)', fontWeight: 500 }}>{formatDateTimeBR(item.createdAtUtc)}</span>
                  </div>
                  <div style={{ color: 'var(--text-secondary)', marginTop: '2px' }}>
                    Ator: {item.adminActor}{item.detailSummary ? ` — ${item.detailSummary}` : ''}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="surface-panel" style={{ padding: '18px 20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <ShieldAlert size={16} color="#fbbf24" />
            <h3 style={{ margin: 0, fontSize: '14px', fontWeight: 700 }}>Incidentes de Segurança (ANPD Res. 15/2024)</h3>
          </div>
          {incidents.length === 0 ? (
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>Nenhum incidente de segurança registrado.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '240px', overflowY: 'auto' }}>
              {incidents.map((inc) => (
                <div key={inc.id} style={{ padding: '8px 10px', borderRadius: '10px', backgroundColor: 'rgba(251,191,36,0.06)', border: '1px solid rgba(251,191,36,0.2)', fontSize: '12px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                    <span>[{inc.severity}] {inc.incidentType}</span>
                    <span style={{ color: inc.anpdNotified ? '#34d399' : '#fbbf24' }}>{inc.anpdNotified ? 'ANPD Notificada' : 'Em Triagem'}</span>
                  </div>
                  <div style={{ color: 'var(--text-secondary)', marginTop: '2px' }}>{inc.description}</div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
};
