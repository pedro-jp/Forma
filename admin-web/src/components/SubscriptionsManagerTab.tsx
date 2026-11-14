import React, { useState } from 'react';
import { Play, Plus, PauseCircle, XCircle, CheckCircle2, AlertTriangle, Clock } from 'lucide-react';
import { BillingDashboardData, SubscriptionPlanItem, UserSubscriptionItem, UserSummary } from '../types';
import { formatBRLFromCents, formatDateTimeBR, subscriptionStatusMeta } from './billingFormat';

interface SubscriptionsManagerTabProps {
  data: BillingDashboardData;
  users: UserSummary[];
  onRefresh: () => void;
}

export const SubscriptionsManagerTab: React.FC<SubscriptionsManagerTabProps> = ({ data, users, onRefresh }) => {
  const [busyId, setBusyId] = useState<string | null>(null);
  const [runningDunning, setRunningDunning] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);
  const [showGrantModal, setShowGrantModal] = useState(false);
  const [editingPlan, setEditingPlan] = useState<SubscriptionPlanItem | null>(null);

  // Grant form state
  const [grantUserId, setGrantUserId] = useState<string>(users[0]?.id || '');
  const [grantPlanId, setGrantPlanId] = useState<string>('PRO_MONTHLY');
  const [grantDays, setGrantDays] = useState<string>('30');
  const [grantIsTrial, setGrantIsTrial] = useState<boolean>(false);
  const [grantMethod, setGrantMethod] = useState<string>('PIX');
  const [grantNote, setGrantNote] = useState<string>('');
  const [submittingGrant, setSubmittingGrant] = useState(false);

  // Edit plan state
  const [planPriceBRL, setPlanPriceBRL] = useState('');
  const [planTrialDays, setPlanTrialDays] = useState('0');

  const handlePolicyChange = async (nextPolicy: 'PAUSE' | 'CANCEL') => {
    setFeedback(null);
    const resp = await fetch('/api/admin/billing/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ overdueAction: nextPolicy })
    });
    if (resp.ok) {
      setFeedback({
        text: `Política após 3 dias de atraso definida para: ${nextPolicy === 'CANCEL' ? 'Cancelar Assinatura' : 'Pausar Assinatura'}.`,
        isError: false
      });
      onRefresh();
    }
  };

  const handleRunDunningNow = async () => {
    setRunningDunning(true);
    setFeedback(null);
    try {
      const resp = await fetch('/api/admin/billing/run-dunning-check', {
        method: 'POST',
        credentials: 'include'
      });
      if (resp.ok) {
        const res = await resp.json();
        setFeedback({
          text: `Varredura concluída: ${res.evaluatedCount} analisada(s), ${res.warningsSentCount} aviso(s) emitido(s), ${res.autoPausedCount} pausada(s), ${res.autoCanceledCount} cancelada(s).`,
          isError: false
        });
        onRefresh();
      }
    } finally {
      setRunningDunning(false);
    }
  };

  const handleSubAction = async (sub: UserSubscriptionItem, action: string) => {
    if (!sub.id) return;
    setBusyId(sub.id);
    setFeedback(null);
    try {
      const resp = await fetch(`/api/admin/billing/subscriptions/${sub.id}/action`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ action })
      });
      if (resp.ok) {
        setFeedback({ text: `Ação ${action} aplicada na assinatura de ${sub.userEmail}.`, isError: false });
        onRefresh();
      } else {
        setFeedback({ text: 'Não foi possível aplicar a ação na assinatura.', isError: true });
      }
    } finally {
      setBusyId(null);
    }
  };

  const handleSimulateOverdue = async (sub: UserSubscriptionItem, dayStage: number) => {
    if (!sub.id) return;
    setBusyId(sub.id);
    setFeedback(null);
    try {
      const resp = await fetch(`/api/admin/billing/subscriptions/${sub.id}/simulate-overdue`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ dayStage })
      });
      if (resp.ok) {
        const label = dayStage <= 3 ? `Dia ${dayStage} de 3 (Aviso enviado)` : 'Pós-3 Dias (Pausa/Cancelamento aplicado)';
        setFeedback({ text: `Simulação executada para ${sub.userEmail}: ${label}.`, isError: false });
        onRefresh();
      }
    } finally {
      setBusyId(null);
    }
  };

  const handleGrantSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!grantUserId) return;
    setSubmittingGrant(true);
    try {
      const resp = await fetch('/api/admin/billing/subscriptions/grant', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          userId: grantUserId,
          planId: grantPlanId,
          periodDays: Math.max(1, parseInt(grantDays, 10) || 30),
          isTrial: grantIsTrial,
          paymentMethod: grantMethod,
          autoRenew: !grantIsTrial,
          note: grantNote.trim() || null
        })
      });
      if (resp.ok) {
        setShowGrantModal(false);
        setGrantNote('');
        setFeedback({ text: 'Assinatura concedida e sincronizada com o app do usuário!', isError: false });
        onRefresh();
      } else {
        const err = await resp.json().catch(() => ({}));
        setFeedback({ text: err.error || 'Erro ao conceder assinatura.', isError: true });
      }
    } finally {
      setSubmittingGrant(false);
    }
  };

  const handleSavePlan = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!editingPlan) return;
    const priceCents = Math.max(0, Math.round(parseFloat(planPriceBRL.replace(',', '.')) * 100) || 0);
    const trialDays = Math.max(0, parseInt(planTrialDays, 10) || 0);
    const resp = await fetch(`/api/admin/billing/plans/${editingPlan.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        priceCents,
        defaultTrialDays: trialDays,
        active: editingPlan.active
      })
    });
    if (resp.ok) {
      setEditingPlan(null);
      setFeedback({ text: `Plano ${editingPlan.name} atualizado.`, isError: false });
      onRefresh();
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '-0.3px' }}>Inscrições &amp; Régua de Inadimplência (3 Dias)</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>
            Monitoramento automático de atrasos: 3 avisos diários em tempo real + ação automática após 72h sem pagamento.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          <button
            onClick={handleRunDunningNow}
            disabled={runningDunning}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '7px',
              padding: '9px 14px',
              borderRadius: '10px',
              backgroundColor: 'rgba(245, 158, 11, 0.14)',
              border: '1px solid rgba(245, 158, 11, 0.3)',
              color: '#fbbf24',
              fontSize: '13px',
              fontWeight: 700
            }}
          >
            <Play size={14} />
            {runningDunning ? 'Analisando...' : 'Analisar Atrasos Agora'}
          </button>
          <button
            onClick={() => {
              if (!grantUserId && users.length > 0) setGrantUserId(users[0].id);
              setShowGrantModal(true);
            }}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '7px',
              padding: '9px 16px',
              borderRadius: '10px',
              backgroundColor: '#10b981',
              border: 'none',
              color: '#042f1a',
              fontSize: '13px',
              fontWeight: 800
            }}
          >
            <Plus size={16} />
            Liberar Assinatura
          </button>
        </div>
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

      {/* Signature 3-Day Dunning Pipeline Strip + Policy Switch */}
      <section className="surface-panel" style={{ padding: '18px 20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '14px' }}>
          <div style={{ fontSize: '14px', fontWeight: 700 }}>Régua Automática de 3 Dias</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Após 3 dias de atraso:</span>
            <div style={{ display: 'inline-flex', padding: '3px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)' }}>
              <button
                onClick={() => handlePolicyChange('PAUSE')}
                style={{
                  padding: '5px 12px',
                  borderRadius: '6px',
                  border: 'none',
                  fontSize: '12px',
                  fontWeight: 700,
                  backgroundColor: data.overdueActionPolicy === 'PAUSE' ? 'rgba(249, 115, 22, 0.22)' : 'transparent',
                  color: data.overdueActionPolicy === 'PAUSE' ? '#fb923c' : 'var(--text-muted)'
                }}
              >
                Pausar Inscrição
              </button>
              <button
                onClick={() => handlePolicyChange('CANCEL')}
                style={{
                  padding: '5px 12px',
                  borderRadius: '6px',
                  border: 'none',
                  fontSize: '12px',
                  fontWeight: 700,
                  backgroundColor: data.overdueActionPolicy === 'CANCEL' ? 'rgba(239, 68, 68, 0.22)' : 'transparent',
                  color: data.overdueActionPolicy === 'CANCEL' ? '#f87171' : 'var(--text-muted)'
                }}
              >
                Cancelar Inscrição
              </button>
            </div>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '10px' }}>
          <div className="surface-inset" style={{ padding: '12px 14px' }}>
            <div style={{ fontSize: '11px', color: '#34d399', fontWeight: 700 }}>EM DIA / GRÁTIS</div>
            <div className="tabular-nums" style={{ fontSize: '22px', fontWeight: 800, marginTop: '4px' }}>{data.activeSubscribersCount + data.trialingCount}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Acesso Pro liberado</div>
          </div>
          <div className="surface-inset" style={{ padding: '12px 14px' }}>
            <div style={{ fontSize: '11px', color: '#fbbf24', fontWeight: 700 }}>DIA 1 • 1º AVISO</div>
            <div className="tabular-nums" style={{ fontSize: '22px', fontWeight: 800, marginTop: '4px' }}>{data.pastDueDay1Count}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>0–24h em atraso</div>
          </div>
          <div className="surface-inset" style={{ padding: '12px 14px' }}>
            <div style={{ fontSize: '11px', color: '#fbbf24', fontWeight: 700 }}>DIA 2 • 2º AVISO</div>
            <div className="tabular-nums" style={{ fontSize: '22px', fontWeight: 800, marginTop: '4px' }}>{data.pastDueDay2Count}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>24–48h em atraso</div>
          </div>
          <div className="surface-inset" style={{ padding: '12px 14px' }}>
            <div style={{ fontSize: '11px', color: '#fb923c', fontWeight: 700 }}>DIA 3 • ÚLTIMO AVISO</div>
            <div className="tabular-nums" style={{ fontSize: '22px', fontWeight: 800, marginTop: '4px' }}>{data.pastDueDay3Count}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>48–72h em atraso</div>
          </div>
          <div className="surface-inset" style={{ padding: '12px 14px' }}>
            <div style={{ fontSize: '11px', color: '#f87171', fontWeight: 700 }}>&gt; 72H • BLOQUEADAS</div>
            <div className="tabular-nums" style={{ fontSize: '22px', fontWeight: 800, marginTop: '4px' }}>{data.pausedCount + data.canceledCount}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Pausadas ou canceladas</div>
          </div>
        </div>
      </section>

      {/* Plans Strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '12px' }}>
        {data.plans.map((p) => (
          <div key={p.id} className="surface-panel" style={{ padding: '16px 18px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontWeight: 700, fontSize: '14px' }}>{p.name}</span>
                <span className="font-mono" style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{p.storekitProductId}</span>
              </div>
              <div className="tabular-nums" style={{ fontSize: '18px', fontWeight: 800, color: '#34d399', marginTop: '4px' }}>
                {formatBRLFromCents(p.priceCents)} <span style={{ fontSize: '12px', color: 'var(--text-secondary)', fontWeight: 500 }}>/ {p.billingInterval === 'YEARLY' ? 'ano' : 'mês'}</span>
              </div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
                Trial padrão: {p.defaultTrialDays} dia(s) • {p.active ? 'Ativo' : 'Oculto'}
              </div>
            </div>

            <button
              onClick={() => {
                setEditingPlan(p);
                setPlanPriceBRL((p.priceCents / 100).toFixed(2));
                setPlanTrialDays(String(p.defaultTrialDays));
              }}
              style={{ padding: '7px 12px', borderRadius: '8px', border: '1px solid var(--border-color)', background: 'rgba(255,255,255,0.04)', color: 'var(--text-primary)', fontSize: '12px', fontWeight: 600 }}
            >
              Editar Preço
            </button>
          </div>
        ))}
      </div>

      {/* Subscriptions Table */}
      <section className="surface-panel" style={{ padding: '18px 20px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>Inscrições Cadastradas ({data.subscriptions.length})</h3>
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Usuário</th>
                <th>Plano &amp; Origem</th>
                <th>Status</th>
                <th>Vencimento / Carência</th>
                <th className="num">Valor</th>
                <th style={{ textAlign: 'right' }}>Controle &amp; Simulação 3 Dias</th>
              </tr>
            </thead>
            <tbody>
              {data.subscriptions.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', padding: '28px', color: 'var(--text-muted)' }}>
                    Nenhuma inscrição registrada ainda. Libere uma assinatura acima ou resgate um cupom no app.
                  </td>
                </tr>
              ) : (
                data.subscriptions.map((sub) => {
                  const st = subscriptionStatusMeta(sub.status);
                  const disabled = !sub.id || busyId === sub.id;
                  return (
                    <tr key={sub.id || sub.userId}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{sub.userEmail}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                          Tier atual: <strong>{sub.accountTier}</strong>
                          {sub.appliedCouponCode ? ` • Cupom: ${sub.appliedCouponCode}` : ''}
                        </div>
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{sub.planName}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{sub.source}</div>
                      </td>
                      <td>
                        <span style={{ padding: '3px 9px', borderRadius: '999px', fontSize: '11px', fontWeight: 700, color: st.color, backgroundColor: st.bg }}>
                          {st.label}{sub.pastDue && sub.overdueDay ? ` (Dia ${sub.overdueDay}/3)` : ''}
                        </span>
                      </td>
                      <td style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                        <div>Fim: {formatDateTimeBR(sub.currentPeriodEndUtc)}</div>
                        {sub.graceEndsAtUtc && (
                          <div style={{ color: '#fbbf24', fontSize: '11px', display: 'flex', alignItems: 'center', gap: '4px', marginTop: '2px' }}>
                            <Clock size={11} /> Prazo 3d: {formatDateTimeBR(sub.graceEndsAtUtc)}
                          </div>
                        )}
                      </td>
                      <td className="num" style={{ fontWeight: 700 }}>{formatBRLFromCents(sub.priceCents)}</td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <div style={{ display: 'inline-flex', gap: '4px', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                          <button disabled={disabled} onClick={() => handleSimulateOverdue(sub, 1)} title="Simular Dia 1 de atraso" style={{ padding: '4px 7px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(245,158,11,0.1)', color: '#fbbf24', fontSize: '11px', fontWeight: 700 }}>D1</button>
                          <button disabled={disabled} onClick={() => handleSimulateOverdue(sub, 2)} title="Simular Dia 2 de atraso" style={{ padding: '4px 7px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(245,158,11,0.14)', color: '#fbbf24', fontSize: '11px', fontWeight: 700 }}>D2</button>
                          <button disabled={disabled} onClick={() => handleSimulateOverdue(sub, 3)} title="Simular Dia 3 de atraso" style={{ padding: '4px 7px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(249,115,22,0.16)', color: '#fb923c', fontSize: '11px', fontWeight: 700 }}>D3</button>
                          <button disabled={disabled} onClick={() => handleSimulateOverdue(sub, 4)} title="Simular >3 dias (executa pausa/cancelamento)" style={{ padding: '4px 7px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(239,68,68,0.14)', color: '#f87171', fontSize: '11px', fontWeight: 700 }}>&gt;3D</button>
                          <button disabled={disabled} onClick={() => handleSubAction(sub, 'ACTIVATE_RENEW')} title="Regularizar / Renovar +30 dias" style={{ padding: '5px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(16,185,129,0.12)', color: '#34d399' }}><CheckCircle2 size={14} /></button>
                          <button disabled={disabled} onClick={() => handleSubAction(sub, 'PAUSE')} title="Pausar inscrição" style={{ padding: '5px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(249,115,22,0.12)', color: '#fb923c' }}><PauseCircle size={14} /></button>
                          <button disabled={disabled} onClick={() => handleSubAction(sub, 'CANCEL')} title="Cancelar inscrição" style={{ padding: '5px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(239,68,68,0.12)', color: '#f87171' }}><XCircle size={14} /></button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Dunning Notices Ledger */}
      <section className="surface-panel" style={{ padding: '18px 20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
          <AlertTriangle size={16} color="#fbbf24" />
          <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Histórico de Avisos &amp; Ações Automáticas (Régua 3 Dias)</h3>
        </div>
        {data.recentDunningNotices.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>Nenhum aviso de atraso disparado até o momento.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '260px', overflowY: 'auto' }}>
            {data.recentDunningNotices.map((n) => (
              <div key={n.id} className="surface-inset" style={{ padding: '10px 12px', fontSize: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                  <span>{n.title} • {n.userEmail}</span>
                  <span className="tabular-nums" style={{ color: 'var(--text-muted)', fontWeight: 500 }}>{formatDateTimeBR(n.createdAtUtc)}</span>
                </div>
                <div style={{ color: 'var(--text-secondary)', marginTop: '3px' }}>{n.message}</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px' }}>
                  WebSocket: {n.websocketDelivered ? 'Entregue ao iOS' : 'Pendente/Offline'} • E-mail: {n.emailDelivered ? 'Enviado' : 'Não configurado'}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Modal Liberar Assinatura */}
      {showGrantModal && (
        <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.72)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px', zIndex: 50 }}>
          <form onSubmit={handleGrantSubmit} className="surface-panel" style={{ width: '100%', maxWidth: '460px', padding: '22px' }}>
            <h3 style={{ fontSize: '17px', fontWeight: 800, marginBottom: '14px' }}>Liberar / Conceder Assinatura</h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Usuário</label>
                <select
                  required
                  value={grantUserId}
                  onChange={(e) => setGrantUserId(e.target.value)}
                  style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                >
                  {users.map((u) => (
                    <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Plano</label>
                  <select
                    value={grantPlanId}
                    onChange={(e) => setGrantPlanId(e.target.value)}
                    style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                  >
                    {data.plans.map((p) => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Duração (dias)</label>
                  <input
                    type="number"
                    min="1"
                    value={grantDays}
                    onChange={(e) => setGrantDays(e.target.value)}
                    style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', alignItems: 'center' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Meio de Pagamento</label>
                  <select
                    value={grantMethod}
                    onChange={(e) => setGrantMethod(e.target.value)}
                    style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                  >
                    <option value="PIX">PIX</option>
                    <option value="CARD">Cartão</option>
                    <option value="MANUAL">Cortesia / Manual</option>
                  </select>
                </div>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', marginTop: '18px', cursor: 'pointer' }}>
                  <input type="checkbox" checked={grantIsTrial} onChange={(e) => setGrantIsTrial(e.target.checked)} />
                  Período Grátis (R$ 0,00)
                </label>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Observação</label>
                <input
                  value={grantNote}
                  onChange={(e) => setGrantNote(e.target.value)}
                  placeholder="Ex: Parceria / Teste de 30 dias"
                  style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '18px' }}>
              <button type="button" onClick={() => setShowGrantModal(false)} style={{ padding: '9px 14px', borderRadius: '8px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-secondary)', fontSize: '13px' }}>
                Cancelar
              </button>
              <button type="submit" disabled={submittingGrant} style={{ padding: '9px 16px', borderRadius: '8px', border: 'none', backgroundColor: '#10b981', color: '#042f1a', fontWeight: 800, fontSize: '13px' }}>
                {submittingGrant ? 'Liberando...' : 'Ativar Assinatura'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Modal Editar Plano */}
      {editingPlan && (
        <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.72)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px', zIndex: 50 }}>
          <form onSubmit={handleSavePlan} className="surface-panel" style={{ width: '100%', maxWidth: '400px', padding: '22px' }}>
            <h3 style={{ fontSize: '16px', fontWeight: 800, marginBottom: '12px' }}>Editar {editingPlan.name}</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Preço (R$)</label>
                <input value={planPriceBRL} onChange={(e) => setPlanPriceBRL(e.target.value)} style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Dias Grátis Padrão (Trial)</label>
                <input type="number" min="0" value={planTrialDays} onChange={(e) => setPlanTrialDays(e.target.value)} style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)' }} />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '16px' }}>
              <button type="button" onClick={() => setEditingPlan(null)} style={{ padding: '8px 12px', borderRadius: '8px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-secondary)' }}>Cancelar</button>
              <button type="submit" style={{ padding: '8px 14px', borderRadius: '8px', border: 'none', backgroundColor: '#10b981', color: '#042f1a', fontWeight: 800 }}>Salvar</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
