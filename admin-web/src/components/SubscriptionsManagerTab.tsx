import React, { useMemo, useState } from 'react';
import { Percent, Play, Plus, X } from 'lucide-react';
import { BillingDashboardData, SubscriptionPlanItem, UserSubscriptionItem, UserSummary } from '../types';
import { formatBRLFromCents, formatDateTimeBR, subscriptionSourceLabel, subscriptionStatusMeta } from './billingFormat';

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

  // Next billing discount modal state
  const [showDiscountModal, setShowDiscountModal] = useState(false);
  const [discountTargetSubId, setDiscountTargetSubId] = useState<string | null>(null);
  const [discountUserId, setDiscountUserId] = useState<string>(users[0]?.id || '');
  const [discountCustomEmail, setDiscountCustomEmail] = useState<string>('');
  const [discountPlanId, setDiscountPlanId] = useState<string>('PRO_MONTHLY');
  const [discountMode, setDiscountMode] = useState<'PERCENT' | 'FIXED_CENTS' | 'COUPON'>('PERCENT');
  const [discountPercentVal, setDiscountPercentVal] = useState<string>('25');
  const [discountFixedBRL, setDiscountFixedBRL] = useState<string>('10.00');
  const [discountCouponCode, setDiscountCouponCode] = useState<string>('');
  const [discountNote, setDiscountNote] = useState<string>('');
  const [savingDiscount, setSavingDiscount] = useState(false);

  const activeCoupons = useMemo(
    () => data.coupons.filter((c) => c.active && !c.exhausted && !c.expired),
    [data.coupons]
  );

  const previewDiscountSummary = useMemo(() => {
    const plan = data.plans.find((p) => p.id === discountPlanId) || data.plans[0];
    const baseCents = plan ? plan.priceCents : 2990;
    let nextCents = baseCents;
    if (discountMode === 'PERCENT') {
      const pct = Math.max(1, Math.min(100, parseInt(discountPercentVal, 10) || 0));
      nextCents = Math.round(baseCents * ((100 - pct) / 100));
    } else if (discountMode === 'FIXED_CENTS') {
      const offCents = Math.max(0, Math.round((parseFloat(discountFixedBRL.replace(',', '.')) || 0) * 100));
      nextCents = Math.max(0, baseCents - offCents);
    } else if (discountMode === 'COUPON') {
      const c = activeCoupons.find((item) => item.code === discountCouponCode);
      if (c) {
        if (c.couponType === 'FREE_PERIOD_DAYS') nextCents = 0;
        else if (c.couponType === 'PERCENT_DISCOUNT') {
          nextCents = Math.round(baseCents * ((100 - (c.discountPercent || 0)) / 100));
        } else {
          nextCents = Math.max(0, baseCents - (c.discountCents || 0));
        }
      }
    }
    return { planName: plan?.name || 'Forma Pro', baseCents, nextCents };
  }, [data.plans, discountPlanId, discountMode, discountPercentVal, discountFixedBRL, discountCouponCode, activeCoupons]);

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
  const [savingPlan, setSavingPlan] = useState(false);

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
        text: `Política após 3 dias de atraso definida para: ${nextPolicy === 'CANCEL' ? 'Cancelar assinatura' : 'Pausar assinatura'}.`,
        isError: false
      });
      onRefresh();
    } else {
      setFeedback({ text: 'Não foi possível alterar a política global.', isError: true });
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
          text: `Varredura concluída: ${res.evaluatedCount} analisada(s), ${res.warningsSentCount} aviso(s), ${res.autoPausedCount} pausada(s), ${res.autoCanceledCount} cancelada(s).`,
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
          isTrial: grantIsTrial || grantMethod === 'COURTESY',
          customPriceCents: grantIsTrial || grantMethod === 'COURTESY' ? 0 : undefined,
          paymentMethod: grantMethod,
          autoRenew: !grantIsTrial && grantMethod !== 'COURTESY',
          note: grantNote.trim() || null
        })
      });
      if (resp.ok) {
        setShowGrantModal(false);
        setGrantNote('');
        setFeedback({ text: 'Assinatura liberada e sincronizada com o aplicativo.', isError: false });
        onRefresh();
      } else {
        const err = await resp.json().catch(() => ({}));
        setFeedback({ text: err.error || 'Erro ao liberar assinatura.', isError: true });
      }
    } finally {
      setSubmittingGrant(false);
    }
  };

  const handleSavePlan = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!editingPlan) return;
    setSavingPlan(true);
    const priceCents = Math.max(0, Math.round(parseFloat(planPriceBRL.replace(',', '.')) * 100) || 0);
    const trialDays = Math.max(0, parseInt(planTrialDays, 10) || 0);
    try {
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
        setFeedback({ text: `Plano ${editingPlan.name} atualizado com sucesso.`, isError: false });
        onRefresh();
      }
    } finally {
      setSavingPlan(false);
    }
  };

  const openNextDiscountModal = (sub?: UserSubscriptionItem) => {
    setFeedback(null);
    if (sub) {
      setDiscountTargetSubId(sub.id || null);
      setDiscountUserId(sub.userId);
      setDiscountCustomEmail(sub.userEmail);
      setDiscountPlanId(sub.planId && sub.planId !== 'FREE' ? sub.planId : (data.plans[0]?.id || 'PRO_MONTHLY'));
    } else {
      setDiscountTargetSubId(null);
      setDiscountUserId(users[0]?.id || '');
      setDiscountCustomEmail('');
      setDiscountPlanId(data.plans[0]?.id || 'PRO_MONTHLY');
    }
    setDiscountMode('PERCENT');
    setDiscountPercentVal('25');
    setDiscountFixedBRL('10.00');
    setDiscountCouponCode(activeCoupons[0]?.code || '');
    setDiscountNote('');
    setShowDiscountModal(true);
  };

  const handleApplyNextDiscountSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    const trimmedEmail = discountCustomEmail.trim();
    if (!discountTargetSubId && !trimmedEmail && !discountUserId) {
      setFeedback({ text: 'Selecione um usuário ou informe o e-mail do assinante.', isError: true });
      return;
    }
    if (discountMode === 'COUPON' && !discountCouponCode) {
      setFeedback({ text: 'Selecione um cupom ativo ou use desconto percentual/fixo.', isError: true });
      return;
    }

    setSavingDiscount(true);
    setFeedback(null);
    try {
      const payload: Record<string, unknown> = {
        subscriptionId: discountTargetSubId || null,
        userId: discountTargetSubId || trimmedEmail ? null : discountUserId,
        userEmail: trimmedEmail || null,
        planId: discountPlanId,
        discountType: discountMode,
        note: discountNote.trim() || null
      };
      if (discountMode === 'PERCENT') {
        payload.discountPercent = Math.max(1, Math.min(100, parseInt(discountPercentVal, 10) || 10));
      } else if (discountMode === 'FIXED_CENTS') {
        payload.discountCents = Math.max(100, Math.round((parseFloat(discountFixedBRL.replace(',', '.')) || 5) * 100));
      } else {
        payload.couponCode = discountCouponCode;
      }

      const resp = await fetch('/api/admin/billing/subscriptions/next-discount', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload)
      });
      const resData = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        setFeedback({ text: resData.error || 'Erro ao aplicar desconto na próxima cobrança.', isError: true });
        return;
      }
      setShowDiscountModal(false);
      setFeedback({
        text: `Desconto aplicado em ${resData.userEmail}: ${resData.nextBillingDiscountLabel || 'atualizado'}.`,
        isError: false
      });
      onRefresh();
    } finally {
      setSavingDiscount(false);
    }
  };

  const handleClearNextDiscount = async (sub: UserSubscriptionItem) => {
    if (!sub.id) return;
    setBusyId(sub.id);
    setFeedback(null);
    try {
      const resp = await fetch(`/api/admin/billing/subscriptions/${sub.id}/next-discount`, {
        method: 'DELETE',
        credentials: 'include'
      });
      if (resp.ok) {
        setFeedback({ text: `Desconto da próxima cobrança removido para ${sub.userEmail}.`, isError: false });
        onRefresh();
      } else {
        setFeedback({ text: 'Não foi possível remover o desconto agendado.', isError: true });
      }
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="view-stack">
      <div className="title-row">
        <div>
          <h1 className="page-title">Assinaturas e régua</h1>
          <p className="page-subtitle">
            Monitoramento contínuo de vencimentos: avisos nos dias 1, 2 e 3 com ação automática após 72 horas.
          </p>
        </div>

        <div className="actions-row">
          <button type="button" className="btn btn-secondary" onClick={handleRunDunningNow} disabled={runningDunning}>
            <Play size={13} />
            <span>{runningDunning ? 'Analisando...' : 'Analisar atrasos agora'}</span>
          </button>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => openNextDiscountModal()}
          >
            <Percent size={13} />
            <span>Desconto próx. cobrança</span>
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              if (!grantUserId && users.length > 0) setGrantUserId(users[0].id);
              setShowGrantModal(true);
            }}
          >
            <Plus size={14} />
            <span>Liberar assinatura</span>
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

      {/* 5-Stage Dunning Pipeline Divided Strip */}
      <section className="kpis" aria-label="Estágios da régua de inadimplência">
        <div className="kpi">
          <div className="kpi-label">Assinaturas ativas</div>
          <div className="kpi-val is-pos">{data.activeSubscribersCount + data.trialingCount}</div>
          <div className="kpi-sub">{data.activeSubscribersCount} pagas · {data.trialingCount} período grátis</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Aviso 1 (0–24h)</div>
          <div className={`kpi-val ${data.pastDueDay1Count > 0 ? 'is-warn' : ''}`}>{data.pastDueDay1Count}</div>
          <div className="kpi-sub">Primeiro lembrete</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Aviso 2 (24–48h)</div>
          <div className={`kpi-val ${data.pastDueDay2Count > 0 ? 'is-warn' : ''}`}>{data.pastDueDay2Count}</div>
          <div className="kpi-sub">Segunda notificação</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Aviso 3 (48–72h)</div>
          <div className={`kpi-val ${data.pastDueDay3Count > 0 ? 'is-neg' : ''}`}>{data.pastDueDay3Count}</div>
          <div className="kpi-sub">Última janela de regularização</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Encerradas (&gt;72h)</div>
          <div className="kpi-val">{data.pausedCount + data.canceledCount}</div>
          <div className="kpi-sub">{data.pausedCount} pausadas · {data.canceledCount} canceladas</div>
        </div>
      </section>

      {/* Política Global de Atraso + Configuração dos Planos */}
      <section className="panel" aria-label="Política automática de inadimplência">
        <div className="settings-list">
          <div className="settings-row">
            <div>
              <div className="settings-row-title">Ação após {data.graceDays} dias de atraso</div>
              <div className="settings-row-desc">
                Define o comportamento automático ao término das 72 horas da régua de avisos e informa a regra atual nos aplicativos.
              </div>
            </div>
            <div className="settings-row-control" style={{ alignItems: 'flex-start' }}>
              <div className="segmented" role="group" aria-label="Política de atraso">
                <button
                  type="button"
                  onClick={() => handlePolicyChange('PAUSE')}
                  className={`seg-btn ${data.overdueActionPolicy === 'PAUSE' ? 'is-active' : ''}`}
                >
                  Pausar inscrição
                </button>
                <button
                  type="button"
                  onClick={() => handlePolicyChange('CANCEL')}
                  className={`seg-btn ${data.overdueActionPolicy === 'CANCEL' ? 'is-active' : ''}`}
                >
                  Cancelar inscrição
                </button>
              </div>
            </div>
          </div>

          <div className="settings-row">
            <div>
              <div className="settings-row-title">Preços e prazos dos planos</div>
              <div className="settings-row-desc">
                Clique em editar para ajustar o valor em reais ou os dias de período grátis exibidos no aplicativo.
              </div>
            </div>
            <div className="settings-row-control">
              <div className="actions-row">
                {data.plans.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => {
                      setEditingPlan(p);
                      setPlanPriceBRL((p.priceCents / 100).toFixed(2));
                      setPlanTrialDays(String(p.defaultTrialDays));
                    }}
                  >
                    <span>{p.name}:</span>
                    <strong className="tabular-nums">{formatBRLFromCents(p.priceCents)}</strong>
                    <span style={{ color: 'var(--faint)', fontSize: '12px' }}>
                      ({p.defaultTrialDays}d grátis)
                    </span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Tabela de Assinaturas */}
      <section className="panel" aria-label="Lista de assinaturas">
        <div className="panel-head">
          <div>
            <h2 className="panel-title">Assinaturas monitoradas ({data.subscriptions.length})</h2>
            <p className="panel-subtitle">Gerencie renovações, períodos vigentes e status de cada assinante.</p>
          </div>
        </div>

        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Assinante</th>
                <th>Plano e origem</th>
                <th>Status</th>
                <th>Vencimento / Carência</th>
                <th className="num">Avisos</th>
                <th className="num">Valor</th>
                <th style={{ textAlign: 'right' }}>Controles</th>
              </tr>
            </thead>
            <tbody>
              {data.subscriptions.length === 0 ? (
                <tr>
                  <td colSpan={7} className="table-empty">
                    Nenhuma assinatura criada. Utilize "Liberar assinatura" ou resgate um cupom no app.
                  </td>
                </tr>
              ) : (
                data.subscriptions.map((sub) => {
                  const meta = subscriptionStatusMeta(sub.status);
                  const rowId = sub.id || sub.userId;
                  return (
                    <tr key={rowId}>
                      <td>
                        <div style={{ fontWeight: 500 }}>{sub.userEmail}</div>
                        <div style={{ fontSize: '12px', color: 'var(--faint)' }}>
                          Camada {sub.accountTier}
                          {sub.appliedCouponCode ? ` · Cupom ${sub.appliedCouponCode}` : ''}
                        </div>
                      </td>
                      <td>
                        <div>{sub.planName}</div>
                        <div style={{ fontSize: '12px', color: 'var(--faint)' }}>{subscriptionSourceLabel(sub.source)}</div>
                      </td>
                      <td>
                        <span className={`pill pill-${meta.tone}`}>{meta.label}</span>
                        {sub.overdueDay ? (
                          <div style={{ fontSize: '11.5px', color: 'var(--warn-ink)', marginTop: '3px' }}>
                            Dia {sub.overdueDay} de 3
                          </div>
                        ) : null}
                      </td>
                      <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)' }}>
                        <div>{formatDateTimeBR(sub.graceEndsAtUtc || sub.currentPeriodEndUtc || sub.trialEndsAtUtc)}</div>
                        {sub.statusReason && (
                          <div style={{ fontFamily: 'var(--font-sans)', fontSize: '11.5px', color: 'var(--faint)', maxWidth: '220px' }}>
                            {sub.statusReason}
                          </div>
                        )}
                      </td>
                      <td className="num">{sub.dunningWarningsSent}/3</td>
                      <td className="num" style={{ fontWeight: 600 }}>
                        <div>{formatBRLFromCents(sub.priceCents)}</div>
                        {sub.nextBillingPriceCents != null && (
                          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', marginTop: '4px' }}>
                            <span
                              className="pill pill-pos"
                              title={sub.nextBillingDiscountLabel || 'Desconto agendado para a próxima cobrança'}
                              style={{ fontSize: '11px', padding: '2px 7px' }}
                            >
                              Próx.: {formatBRLFromCents(sub.nextBillingPriceCents)}
                            </span>
                            {sub.id && (
                              <button
                                type="button"
                                className="btn-icon"
                                title="Remover desconto da próxima cobrança"
                                disabled={busyId === sub.id}
                                onClick={() => handleClearNextDiscount(sub)}
                              >
                                <X size={11} />
                              </button>
                            )}
                          </div>
                        )}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        {sub.id && (
                          <div className="actions-row" style={{ justifyContent: 'flex-end' }}>
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              disabled={busyId === sub.id}
                              onClick={() => openNextDiscountModal(sub)}
                              title="Aplicar desconto na próxima cobrança"
                            >
                              Desconto próx.
                            </button>
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              disabled={busyId === sub.id}
                              onClick={() => handleSubAction(sub, 'EXTEND_30D')}
                            >
                              +30d
                            </button>
                            {sub.status !== 'PAUSED' ? (
                              <button
                                type="button"
                                className="btn btn-ghost btn-sm"
                                style={{ color: 'var(--warn-ink)' }}
                                disabled={busyId === sub.id}
                                onClick={() => handleSubAction(sub, 'PAUSE')}
                              >
                                Pausar
                              </button>
                            ) : (
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                disabled={busyId === sub.id}
                                onClick={() => handleSubAction(sub, 'REACTIVATE')}
                              >
                                Reativar
                              </button>
                            )}
                            <button
                              type="button"
                              className="btn btn-ghost btn-sm"
                              style={{ color: 'var(--neg-ink)' }}
                              disabled={busyId === sub.id}
                              onClick={() => handleSubAction(sub, 'CANCEL')}
                            >
                              Cancelar
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Histórico de Avisos Enviados pela Régua */}
      <section className="panel" aria-label="Avisos recentes da régua">
        <div className="panel-head">
          <div>
            <h2 className="panel-title">Últimos avisos disparados ({data.recentDunningNotices.length})</h2>
            <p className="panel-subtitle">Notificações entregues no aplicativo e por e-mail.</p>
          </div>
        </div>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Enviado em</th>
                <th>Usuário</th>
                <th>Estágio</th>
                <th>Mensagem</th>
                <th>Entrega</th>
              </tr>
            </thead>
            <tbody>
              {data.recentDunningNotices.length === 0 ? (
                <tr>
                  <td colSpan={5} className="table-empty">Nenhum aviso da régua de atraso disparado até o momento.</td>
                </tr>
              ) : (
                data.recentDunningNotices.slice(0, 15).map((n) => (
                  <tr key={n.id}>
                    <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)' }}>
                      {formatDateTimeBR(n.createdAtUtc)}
                    </td>
                    <td>{n.userEmail || n.userId}</td>
                    <td>
                      <span className={`pill ${n.dayNumber >= 4 ? 'pill-neg' : 'pill-warn'}`}>
                        {n.dayNumber >= 4 ? 'Encerramento (>72h)' : `Dia ${n.dayNumber}/3`}
                      </span>
                    </td>
                    <td style={{ maxWidth: '380px', color: 'var(--muted)' }}>{n.message}</td>
                    <td>
                      <span className={`pill ${n.websocketDelivered || n.emailDelivered ? 'pill-pos' : 'pill-neutral'}`}>
                        {n.websocketDelivered ? 'Notificado no app' : n.emailDelivered ? 'E-mail' : 'Registrado'}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Modal Conceder Assinatura */}
      {showGrantModal && (
        <div className="modal-overlay" onClick={() => setShowGrantModal(false)}>
          <form className="modal-dialog" onSubmit={handleGrantSubmit} onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <h2 className="modal-title">Liberar assinatura</h2>
                <p className="modal-sub">Concede acesso Pro pago ou período grátis com sincronização imediata.</p>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowGrantModal(false)}>
                Fechar
              </button>
            </div>

            <div className="field">
              <label className="field-label">Usuário</label>
              <select className="select" value={grantUserId} onChange={(e) => setGrantUserId(e.target.value)} required>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name} ({u.email})
                  </option>
                ))}
              </select>
            </div>

            <div className="form-grid-2">
              <div className="field">
                <label className="field-label">Plano</label>
                <select className="select" value={grantPlanId} onChange={(e) => setGrantPlanId(e.target.value)}>
                  {data.plans.map((p) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label">Duração (dias)</label>
                <input
                  className="input mono"
                  type="number"
                  min={1}
                  max={3650}
                  value={grantDays}
                  onChange={(e) => setGrantDays(e.target.value)}
                />
              </div>
            </div>

            <div className="form-grid-2">
              <div className="field">
                <label className="field-label">Método de registro</label>
                <select className="select" value={grantMethod} onChange={(e) => setGrantMethod(e.target.value)}>
                  <option value="PIX">Pix</option>
                  <option value="CARD">Cartão</option>
                  <option value="COURTESY">Cortesia</option>
                </select>
              </div>
              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', marginTop: '22px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={grantIsTrial}
                  onChange={(e) => setGrantIsTrial(e.target.checked)}
                />
                Período grátis sem cobrança
              </label>
            </div>

            <div className="field">
              <label className="field-label">Observação (opcional)</label>
              <input
                className="input"
                value={grantNote}
                onChange={(e) => setGrantNote(e.target.value)}
                placeholder="Ex: Parceria ou cortesia"
              />
            </div>

            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={() => setShowGrantModal(false)}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-primary" disabled={submittingGrant}>
                {submittingGrant ? 'Liberando...' : 'Confirmar liberação'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Modal Editar Preço e Prazo do Plano */}
      {editingPlan && (
        <div className="modal-overlay" onClick={() => setEditingPlan(null)}>
          <form className="modal-dialog" onSubmit={handleSavePlan} onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <h2 className="modal-title">Editar plano: {editingPlan.name}</h2>
                <p className="modal-sub">Atualiza preço e período grátis publicados no aplicativo.</p>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEditingPlan(null)}>
                Fechar
              </button>
            </div>

            <div className="form-grid-2">
              <div className="field">
                <label className="field-label">Valor em BRL</label>
                <input
                  className="input mono"
                  value={planPriceBRL}
                  onChange={(e) => setPlanPriceBRL(e.target.value)}
                  required
                />
              </div>
              <div className="field">
                <label className="field-label">Período grátis (dias)</label>
                <input
                  className="input mono"
                  type="number"
                  min={0}
                  max={90}
                  value={planTrialDays}
                  onChange={(e) => setPlanTrialDays(e.target.value)}
                />
              </div>
            </div>

            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={() => setEditingPlan(null)}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-primary" disabled={savingPlan}>
                {savingPlan ? 'Salvando...' : 'Salvar plano'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Modal Desconto na Próxima Cobrança */}
      {showDiscountModal && (
        <div className="modal-overlay" onClick={() => setShowDiscountModal(false)}>
          <form className="modal-dialog" onSubmit={handleApplyNextDiscountSubmit} onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <h2 className="modal-title">Desconto na próxima cobrança</h2>
                <p className="modal-sub">
                  Válido exclusivamente para o próximo ciclo de renovação/cobrança do usuário, sem expor cupons no app.
                </p>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowDiscountModal(false)}>
                Fechar
              </button>
            </div>

            {discountTargetSubId ? (
              <div className="field">
                <label className="field-label">Assinante selecionado</label>
                <input className="input" value={discountCustomEmail} disabled />
              </div>
            ) : (
              <>
                {users.length > 0 && (
                  <div className="field">
                    <label className="field-label">Usuário</label>
                    <select className="select" value={discountUserId} onChange={(e) => setDiscountUserId(e.target.value)}>
                      {users.map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.name} ({u.email})
                        </option>
                      ))}
                    </select>
                  </div>
                )}
                <div className="field">
                  <label className="field-label">Ou e-mail exato (opcional)</label>
                  <input
                    className="input"
                    type="email"
                    value={discountCustomEmail}
                    onChange={(e) => setDiscountCustomEmail(e.target.value)}
                    placeholder="cliente@exemplo.com"
                  />
                </div>
              </>
            )}

            <div className="form-grid-2">
              <div className="field">
                <label className="field-label">Plano de referência</label>
                <select className="select" value={discountPlanId} onChange={(e) => setDiscountPlanId(e.target.value)}>
                  {data.plans.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({formatBRLFromCents(p.priceCents)})
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label">Tipo de desconto</label>
                <select className="select" value={discountMode} onChange={(e) => setDiscountMode(e.target.value as any)}>
                  <option value="PERCENT">Percentual (%)</option>
                  <option value="FIXED_CENTS">Valor fixo (R$)</option>
                  <option value="COUPON">Cupom cadastrado</option>
                </select>
              </div>
            </div>

            {discountMode === 'PERCENT' && (
              <div className="field">
                <label className="field-label">Percentual de desconto (%)</label>
                <input
                  className="input mono"
                  type="number"
                  min={1}
                  max={100}
                  value={discountPercentVal}
                  onChange={(e) => setDiscountPercentVal(e.target.value)}
                  required
                />
              </div>
            )}

            {discountMode === 'FIXED_CENTS' && (
              <div className="field">
                <label className="field-label">Desconto em R$</label>
                <input
                  className="input mono"
                  value={discountFixedBRL}
                  onChange={(e) => setDiscountFixedBRL(e.target.value)}
                  placeholder="10.00"
                  required
                />
              </div>
            )}

            {discountMode === 'COUPON' && (
              <div className="field">
                <label className="field-label">Cupom ativo</label>
                {activeCoupons.length === 0 ? (
                  <div style={{ fontSize: '12.5px', color: 'var(--warn-ink)' }}>
                    Nenhum cupom ativo cadastrado. Crie um na aba Cupons ou escolha Percentual / Valor fixo.
                  </div>
                ) : (
                  <select className="select" value={discountCouponCode} onChange={(e) => setDiscountCouponCode(e.target.value)}>
                    {activeCoupons.map((c) => (
                      <option key={c.id} value={c.code}>
                        {c.code} — {c.couponType === 'PERCENT_DISCOUNT' ? `${c.discountPercent}%` : c.couponType === 'FREE_PERIOD_DAYS' ? `${c.freePeriodDays}d grátis` : formatBRLFromCents(c.discountCents)}
                      </option>
                    ))}
                  </select>
                )}
              </div>
            )}

            <div className="notice-banner notice-pos" style={{ marginTop: '4px' }}>
              <span>
                Prévia ({previewDiscountSummary.planName}):{' '}
                <strong style={{ textDecoration: 'line-through', opacity: 0.7 }}>
                  {formatBRLFromCents(previewDiscountSummary.baseCents)}
                </strong>{' '}
                → <strong className="tabular-nums">{formatBRLFromCents(previewDiscountSummary.nextCents)}</strong> na próxima cobrança
              </span>
            </div>

            <div className="field">
              <label className="field-label">Motivo / observação (opcional)</label>
              <input
                className="input"
                value={discountNote}
                onChange={(e) => setDiscountNote(e.target.value)}
                placeholder="Ex: Retenção mensal ou compensação"
              />
            </div>

            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={() => setShowDiscountModal(false)}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-primary" disabled={savingDiscount || (discountMode === 'COUPON' && activeCoupons.length === 0)}>
                {savingDiscount ? 'Aplicando...' : 'Aplicar na próxima cobrança'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
