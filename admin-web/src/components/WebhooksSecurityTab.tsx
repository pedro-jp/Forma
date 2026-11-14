import React, { useState } from 'react';
import { Copy, RefreshCw } from 'lucide-react';
import { AdminAuditItem, BillingDashboardData, SecurityIncidentItem, UserSummary } from '../types';
import { formatDateTimeBR } from './billingFormat';

interface WebhooksSecurityTabProps {
  data: BillingDashboardData;
  users?: UserSummary[];
  auditLogs: AdminAuditItem[];
  incidents: SecurityIncidentItem[];
  onRefresh: () => void;
}

export const WebhooksSecurityTab: React.FC<WebhooksSecurityTabProps> = ({
  data,
  auditLogs,
  incidents,
  onRefresh
}) => {
  const [subView, setSubView] = useState<'webhooks' | 'audit'>('webhooks');
  const [rotatedSecret, setRotatedSecret] = useState<string | null>(data.webhookConfig.rotatedPlaintextSecret || null);
  const [rotating, setRotating] = useState(false);
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
        setFeedback({
          text: 'Novo segredo de assinatura gerado com sucesso.',
          isError: false
        });
        onRefresh();
      }
    } finally {
      setRotating(false);
    }
  };

  return (
    <div className="view-stack">
      <div className="title-row">
        <div>
          <h1 className="page-title">Webhooks e segurança</h1>
          <p className="page-subtitle">
            Receptores assinados com HMAC-SHA256, rotação de credenciais, trilha administrativa e governança LGPD/ANPD.
          </p>
        </div>

        <div className="segmented" role="tablist" aria-label="Subseção de segurança">
          <button
            type="button"
            className={`seg-btn ${subView === 'webhooks' ? 'is-active' : ''}`}
            onClick={() => setSubView('webhooks')}
          >
            Webhooks HMAC ({data.recentWebhooks.length})
          </button>
          <button
            type="button"
            className={`seg-btn ${subView === 'audit' ? 'is-active' : ''}`}
            onClick={() => setSubView('audit')}
          >
            Auditoria e incidentes ({auditLogs.length + incidents.length})
          </button>
        </div>
      </div>

      {feedback && (
        <div className={`notice-banner ${feedback.isError ? 'notice-neg' : 'notice-pos'}`} role="status">
          <span>{feedback.text}</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setFeedback(null)}>
            Dispensar
          </button>
        </div>
      )}

      {subView === 'webhooks' ? (
        <>
          <div className="split-even">
            <section className="panel">
              <div className="panel-head">
                <div>
                  <h2 className="panel-title">Configuração do endpoint</h2>
                  <p className="panel-subtitle">Validação HMAC-SHA256 com prevenção de duplicidade por eventId.</p>
                </div>
              </div>
              <div className="panel-body" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div className="well">
                  <div className="field-label" style={{ marginBottom: '4px' }}>Endpoint POST</div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '8px' }}>
                    <code className="mono" style={{ fontSize: '12px', wordBreak: 'break-all' }}>{fullWebhookUrl}</code>
                    <button
                      type="button"
                      className="btn-icon"
                      title="Copiar URL"
                      onClick={() => {
                        navigator.clipboard?.writeText(fullWebhookUrl);
                        setFeedback({ text: 'URL do webhook copiada.', isError: false });
                      }}
                    >
                      <Copy size={12} />
                    </button>
                  </div>
                </div>

                <div className="well">
                  <div className="field-label" style={{ marginBottom: '4px' }}>Cabeçalho de assinatura</div>
                  <code className="mono" style={{ fontSize: '12px' }}>
                    {data.webhookConfig.signatureHeader}: sha256=&lt;hmac_hex&gt;
                  </code>
                </div>

                <div className="well" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px' }}>
                  <div>
                    <div className="field-label" style={{ marginBottom: '3px' }}>Chave de assinatura ativa</div>
                    <span className="code-chip">{data.webhookConfig.maskedSecret}</span>
                  </div>
                  <button type="button" className="btn btn-secondary btn-sm" onClick={handleRotateSecret} disabled={rotating}>
                    <RefreshCw size={12} />
                    <span>{rotating ? 'Gerando...' : 'Rotacionar segredo'}</span>
                  </button>
                </div>

                {rotatedSecret && (
                  <div className="notice-banner notice-pos" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: '4px' }}>
                    <strong>Novo segredo HMAC (exibido uma única vez):</strong>
                    <code className="mono" style={{ wordBreak: 'break-all', fontSize: '12px' }}>{rotatedSecret}</code>
                  </div>
                )}
              </div>
            </section>

            <section className="panel">
              <div className="panel-head">
                <div>
                  <h2 className="panel-title">Eventos reconhecidos pelo endpoint</h2>
                  <p className="panel-subtitle">Mapeamento automático entre notificações do gateway e o status da assinatura.</p>
                </div>
                <span className="pill pill-pos">Ativo</span>
              </div>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Evento</th>
                      <th>Ação aplicada</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td><span className="code-chip">PAYMENT_SUCCEEDED</span></td>
                      <td style={{ color: 'var(--muted)' }}>Ativa ou renova o acesso Forma Pro e registra o pagamento</td>
                    </tr>
                    <tr>
                      <td><span className="code-chip">PAYMENT_FAILED</span></td>
                      <td style={{ color: 'var(--muted)' }}>Inicia a janela de regularização de 3 dias</td>
                    </tr>
                    <tr>
                      <td><span className="code-chip">SUBSCRIPTION_PAUSED</span></td>
                      <td style={{ color: 'var(--muted)' }}>Suspende temporariamente os benefícios Pro</td>
                    </tr>
                    <tr>
                      <td><span className="code-chip">SUBSCRIPTION_CANCELED</span></td>
                      <td style={{ color: 'var(--muted)' }}>Encerra a assinatura mantendo o histórico da conta</td>
                    </tr>
                    <tr>
                      <td><span className="code-chip">REFUNDED</span></td>
                      <td style={{ color: 'var(--muted)' }}>Registra o estorno e reverte a conta para o plano gratuito</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          </div>

          <section className="panel">
            <div className="panel-head">
              <div>
                <h2 className="panel-title">Entregas recentes de webhooks</h2>
                <p className="panel-subtitle">Histórico de notificações assinadas recebidas dos gateways de pagamento.</p>
              </div>
            </div>

            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Recebido em</th>
                    <th>Event ID</th>
                    <th>Provedor e evento</th>
                    <th>Usuário alvo</th>
                    <th>Assinatura</th>
                    <th>Resultado</th>
                  </tr>
                </thead>
                <tbody>
                  {data.recentWebhooks.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="table-empty">Nenhum webhook recebido até o momento.</td>
                    </tr>
                  ) : (
                    data.recentWebhooks.map((w) => (
                      <tr key={w.id}>
                        <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)' }}>{formatDateTimeBR(w.receivedAtUtc)}</td>
                        <td><span className="code-chip">{w.eventId}</span></td>
                        <td style={{ fontWeight: 500 }}>{w.provider} · {w.eventType}</td>
                        <td style={{ color: 'var(--muted)' }}>{w.targetUserEmail || '—'}</td>
                        <td>
                          <span className={`pill ${w.signatureValid ? 'pill-pos' : 'pill-neg'}`}>
                            {w.signatureValid ? 'HMAC válido' : 'Rejeitado'}
                          </span>
                        </td>
                        <td className="mono" style={{ fontSize: '12px' }}>{w.processingStatus}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </>
      ) : (
        <div className="split-even">
          <section className="panel">
            <div className="panel-head">
              <div>
                <h2 className="panel-title">Trilha de auditoria administrativa</h2>
                <p className="panel-subtitle">Registros imutáveis de alterações no painel.</p>
              </div>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Horário</th>
                    <th>Ação</th>
                    <th>Detalhes</th>
                  </tr>
                </thead>
                <tbody>
                  {auditLogs.length === 0 ? (
                    <tr>
                      <td colSpan={3} className="table-empty">Sem eventos de auditoria recentes.</td>
                    </tr>
                  ) : (
                    auditLogs.map((a) => (
                      <tr key={a.id}>
                        <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)', whiteSpace: 'nowrap' }}>
                          {formatDateTimeBR(a.createdAtUtc)}
                        </td>
                        <td><span className="code-chip">{a.actionType}</span></td>
                        <td style={{ color: 'var(--muted)' }}>{a.detailSummary || a.targetRef || '—'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>

          <section className="panel">
            <div className="panel-head">
              <div>
                <h2 className="panel-title">Incidentes de segurança e LGPD</h2>
                <p className="panel-subtitle">Alertas de força bruta, CSRF e eventos monitorados.</p>
              </div>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Detectado em</th>
                    <th>Tipo</th>
                    <th>Severidade</th>
                    <th>Descrição</th>
                  </tr>
                </thead>
                <tbody>
                  {incidents.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="table-empty">Nenhum incidente de segurança registrado.</td>
                    </tr>
                  ) : (
                    incidents.map((inc) => (
                      <tr key={inc.id}>
                        <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)', whiteSpace: 'nowrap' }}>
                          {formatDateTimeBR(inc.detectedAtUtc)}
                        </td>
                        <td><span className="code-chip">{inc.incidentType}</span></td>
                        <td>
                          <span className={`pill ${inc.severity === 'HIGH' || inc.severity === 'CRITICAL' ? 'pill-neg' : 'pill-warn'}`}>
                            {inc.severity}
                          </span>
                        </td>
                        <td style={{ color: 'var(--muted)' }}>{inc.description}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      )}
    </div>
  );
};
