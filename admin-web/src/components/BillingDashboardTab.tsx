import React, { useMemo, useState } from 'react';
import { Download, Plus } from 'lucide-react';
import { BillingDashboardData, PaymentEntryItem, SubscriptionPlanItem, UserSummary } from '../types';
import { entryStatusMeta, formatBRLFromCents, formatDateTimeBR, paymentMethodLabel } from './billingFormat';

interface BillingDashboardTabProps {
  data: BillingDashboardData;
  users: UserSummary[];
  onRefresh: () => void;
}

export const BillingDashboardTab: React.FC<BillingDashboardTabProps> = ({ data, users, onRefresh }) => {
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [methodFilter, setMethodFilter] = useState<string>('ALL');
  const [showNewModal, setShowNewModal] = useState(false);
  const [editingPlan, setEditingPlan] = useState<SubscriptionPlanItem | null>(null);
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  // Novo lançamento manual
  const [userEmail, setUserEmail] = useState('');
  const [description, setDescription] = useState('');
  const [amountBRL, setAmountBRL] = useState('29.90');
  const [discountBRL, setDiscountBRL] = useState('0.00');
  const [paymentMethod, setPaymentMethod] = useState('PIX');
  const [entryType, setEntryType] = useState('MANUAL_INCOME');
  const [entryStatus, setEntryStatus] = useState('PAID');
  const [couponCode, setCouponCode] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Edição rápida de preço & prazo grátis de plano
  const [planNameInput, setPlanNameInput] = useState('');
  const [planDescInput, setPlanDescInput] = useState('');
  const [planPriceBRL, setPlanPriceBRL] = useState('29.90');
  const [planTrialDays, setPlanTrialDays] = useState('0');
  const [planActive, setPlanActive] = useState(true);
  const [savingPlan, setSavingPlan] = useState(false);

  const filteredEntries = useMemo(() => {
    return data.recentEntries.filter((e) => {
      if (statusFilter !== 'ALL' && e.status.toUpperCase() !== statusFilter) return false;
      if (methodFilter !== 'ALL' && e.paymentMethod.toUpperCase() !== methodFilter) return false;
      return true;
    });
  }, [data.recentEntries, statusFilter, methodFilter]);

  const maxDailyRevenue = useMemo(() => {
    const vals = data.dailyRevenueLast14Days.map((p) => p.revenueCents);
    const rawMax = Math.max(2500, ...vals);
    const step = 2500;
    return Math.ceil(rawMax / step) * step;
  }, [data.dailyRevenueLast14Days]);

  const totalPastDue = data.pastDueDay1Count + data.pastDueDay2Count + data.pastDueDay3Count;

  const openPlanEditor = (plan: SubscriptionPlanItem) => {
    setEditingPlan(plan);
    setPlanNameInput(plan.name || '');
    setPlanDescInput(plan.description || '');
    setPlanPriceBRL((plan.priceCents / 100).toFixed(2));
    setPlanTrialDays(String(plan.defaultTrialDays || 0));
    setPlanActive(plan.active);
    setFeedback(null);
  };

  const handleSavePlan = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!editingPlan) return;
    setSavingPlan(true);
    setFeedback(null);
    const parsedPrice = parseFloat(planPriceBRL.replace(',', '.'));
    const priceCents = Math.max(0, Math.round((Number.isNaN(parsedPrice) ? 0 : parsedPrice) * 100));
    const trialDays = Math.max(0, parseInt(planTrialDays, 10) || 0);

    try {
      const resp = await fetch(`/api/admin/billing/plans/${editingPlan.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          name: planNameInput.trim() || editingPlan.name,
          description: planDescInput.trim(),
          priceCents,
          defaultTrialDays: trialDays,
          active: planActive
        })
      });
      if (resp.ok) {
        setEditingPlan(null);
        setFeedback({
          text: `Plano atualizado para ${formatBRLFromCents(priceCents)} (${trialDays} dias grátis).`,
          isError: false
        });
        onRefresh();
      } else {
        const err = await resp.json().catch(() => ({}));
        setFeedback({ text: err.error || 'Erro ao atualizar plano.', isError: true });
      }
    } catch {
      setFeedback({ text: 'Falha de conexão ao salvar plano.', isError: true });
    } finally {
      setSavingPlan(false);
    }
  };

  const handleCreateEntry = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setSubmitting(true);
    setFeedback(null);
    const grossCents = Math.max(0, Math.round(parseFloat(amountBRL.replace(',', '.')) * 100) || 0);
    const discCents = Math.max(0, Math.round(parseFloat(discountBRL.replace(',', '.')) * 100) || 0);

    try {
      const resp = await fetch('/api/admin/billing/entries', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          userEmail: userEmail.trim() || null,
          entryType,
          status: entryStatus,
          grossAmountCents: grossCents,
          discountCents: discCents,
          paymentMethod,
          couponCode: couponCode.trim().toUpperCase() || null,
          description: description.trim()
        })
      });
      if (!resp.ok) {
        const err = await resp.json().catch(() => ({}));
        setFeedback({ text: err.error || 'Erro ao criar entrada financeira.', isError: true });
      } else {
        setShowNewModal(false);
        setDescription('');
        setCouponCode('');
        setFeedback({ text: 'Entrada financeira registrada no livro caixa.', isError: false });
        onRefresh();
      }
    } catch {
      setFeedback({ text: 'Erro de conexão ao salvar entrada financeira.', isError: true });
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpdateStatus = async (entry: PaymentEntryItem, nextStatus: string) => {
    setBusyId(entry.id);
    setFeedback(null);
    try {
      const resp = await fetch(`/api/admin/billing/entries/${entry.id}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ status: nextStatus })
      });
      if (resp.ok) {
        setFeedback({ text: `Entrada ${entry.referenceCode || ''} atualizada para ${nextStatus}.`, isError: false });
        onRefresh();
      } else {
        let errMsg = 'Falha ao atualizar status da entrada.';
        try {
          const errData = await resp.json();
          errMsg = errData?.message || errData?.error || errMsg;
        } catch {}
        setFeedback({ text: errMsg, isError: true });
      }
    } finally {
      setBusyId(null);
    }
  };

  const handleDeleteEntry = async (entry: PaymentEntryItem) => {
    if (!window.confirm(`Remover definitivamente a entrada "${entry.description}"?`)) return;
    setBusyId(entry.id);
    try {
      const resp = await fetch(`/api/admin/billing/entries/${entry.id}`, {
        method: 'DELETE',
        credentials: 'include'
      });
      if (resp.ok) {
        setFeedback({ text: 'Entrada removida com sucesso.', isError: false });
        onRefresh();
      }
    } finally {
      setBusyId(null);
    }
  };

  const handleExportCsv = () => {
    const header = ['Data', 'Referencia', 'Email', 'Tipo', 'Metodo', 'Cupom', 'Status', 'Bruto(BRL)', 'Desconto(BRL)', 'Liquido(BRL)', 'Descricao'];
    const rows = filteredEntries.map((e) => [
      e.createdAtUtc,
      e.referenceCode || '',
      e.userEmail || '',
      e.entryType,
      e.paymentMethod,
      e.couponCode || '',
      e.status,
      (e.grossAmountCents / 100).toFixed(2),
      (e.discountCents / 100).toFixed(2),
      (e.netAmountCents / 100).toFixed(2),
      `"${(e.description || '').replace(/"/g, '""')}"`
    ]);
    const csv = [header.join(';'), ...rows.map((r) => r.join(';'))].join('\n');
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `forma-entradas-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const chartDays = data.dailyRevenueLast14Days;
  const parsedEditCents = Math.max(0, Math.round((parseFloat(planPriceBRL.replace(',', '.')) || 0) * 100));

  return (
    <div className="view-stack">
      <div className="title-row">
        <div>
          <h1 className="page-title">Receita e caixa</h1>
          <p className="page-subtitle">
            MRR confirmado de {formatBRLFromCents(data.mrrCents)}/mês · {totalPastDue > 0 ? `${totalPastDue} assinatura(s) em atraso` : 'Nenhuma pendência na régua de cobrança'}
          </p>
        </div>

        <div className="actions-row">
          <button type="button" className="btn btn-secondary" onClick={handleExportCsv}>
            <Download size={14} />
            <span>Exportar CSV</span>
          </button>
          <button type="button" className="btn btn-primary" onClick={() => setShowNewModal(true)}>
            <Plus size={14} />
            <span>Nova entrada</span>
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

      {/* Divided Typographic KPI Strip */}
      <section className="kpis" aria-label="Indicadores financeiros">
        <div className="kpi">
          <div className="kpi-label">MRR mensal</div>
          <div className="kpi-val is-pos">{formatBRLFromCents(data.mrrCents)}</div>
          <div className="kpi-sub">ARR projetado {formatBRLFromCents(data.arrCents)}</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Receita últimos 30 dias</div>
          <div className="kpi-val">{formatBRLFromCents(data.paidRevenue30dCents)}</div>
          <div className="kpi-sub">Líquida acumulada {formatBRLFromCents(data.totalNetRevenueCents)}</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Assinaturas Pro</div>
          <div className="kpi-val">{data.activeSubscribersCount + data.trialingCount}</div>
          <div className="kpi-sub">{data.activeSubscribersCount} pagantes · {data.trialingCount} período grátis</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">A receber / vencidas</div>
          <div className={`kpi-val ${data.pendingRevenueCents > 0 ? 'is-warn' : ''}`}>
            {formatBRLFromCents(data.pendingRevenueCents)}
          </div>
          <div className="kpi-sub">Estornos {formatBRLFromCents(data.refundedTotalCents)}</div>
        </div>
      </section>

      {/* Catálogo de Planos & Preços em Tempo Real */}
      <section className="panel" aria-label="Catálogo de planos sincronizado">
        <div className="panel-head">
          <div>
            <h2 className="panel-title">Planos e preços publicados no app</h2>
            <p className="panel-subtitle">
              Alterações publicadas aqui são aplicadas imediatamente no aplicativo.
            </p>
          </div>
          <span className="pill pill-pos">Catálogo ativo</span>
        </div>

        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Plano</th>
                <th>Código do produto</th>
                <th>Ciclo</th>
                <th className="num">Período grátis</th>
                <th className="num">Valor publicado</th>
                <th>Status</th>
                <th style={{ textAlign: 'right' }}>Ação</th>
              </tr>
            </thead>
            <tbody>
              {data.plans.length === 0 ? (
                <tr>
                  <td colSpan={7} className="table-empty">Nenhum plano encontrado.</td>
                </tr>
              ) : (
                data.plans.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <div style={{ fontWeight: 600 }}>{p.name}</div>
                      {p.description && (
                        <div style={{ fontSize: '12px', color: 'var(--faint)', maxWidth: '340px' }}>{p.description}</div>
                      )}
                    </td>
                    <td><span className="code-chip">{p.storekitProductId || p.id}</span></td>
                    <td style={{ color: 'var(--muted)' }}>{p.billingInterval === 'YEARLY' ? 'Anual' : 'Mensal'}</td>
                    <td className="num">{p.defaultTrialDays} dia(s)</td>
                    <td className="num" style={{ fontWeight: 600 }}>{formatBRLFromCents(p.priceCents)}</td>
                    <td>
                      <span className={`pill ${p.active ? 'pill-pos' : 'pill-neutral'}`}>
                        {p.active ? 'Ativo' : 'Inativo'}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => openPlanEditor(p)}>
                        Editar preço
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Gráfico SVG limpo (14 dias) + Composição do Caixa */}
      <div className="split-2">
        <section className="panel">
          <div className="panel-head">
            <div>
              <h2 className="panel-title">Entradas liquidadas (14 dias)</h2>
              <p className="panel-subtitle">Receita diária líquida confirmada por cartão, Pix ou App Store.</p>
            </div>
            <span className="mono" style={{ fontSize: '12px', color: 'var(--faint)' }}>
              Topo da escala {formatBRLFromCents(maxDailyRevenue)}
            </span>
          </div>
          <div className="panel-body">
            {chartDays.length === 0 ? (
              <div className="table-empty">Sem dados suficientes para exibir os últimos 14 dias.</div>
            ) : (
              <svg
                viewBox="0 0 640 190"
                style={{ width: '100%', height: 'auto', display: 'block' }}
                role="img"
                aria-label="Gráfico de barras de receita líquida dos últimos 14 dias"
              >
                {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
                  const y = 148 - ratio * 124;
                  const tickVal = Math.round((maxDailyRevenue * ratio) / 100);
                  return (
                    <g key={ratio}>
                      <line x1="56" y1={y} x2="632" y2={y} stroke="#eff1f2" strokeWidth="1" />
                      <text
                        x="48"
                        y={y + 4}
                        textAnchor="end"
                        fontSize="10"
                        fill="#676d73"
                        fontFamily="IBM Plex Mono, monospace"
                      >
                        {`R$ ${tickVal}`}
                      </text>
                    </g>
                  );
                })}
                {chartDays.map((pt, idx) => {
                  const slotWidth = (632 - 64) / chartDays.length;
                  const barWidth = Math.max(12, Math.min(26, slotWidth - 8));
                  const x = 64 + idx * slotWidth + (slotWidth - barWidth) / 2;
                  const rawHeight = Math.round((pt.revenueCents / maxDailyRevenue) * 124);
                  const height = pt.revenueCents > 0 ? Math.max(3, rawHeight) : 1;
                  const y = 148 - height;
                  const showTickLabel = idx % 2 === 0 || idx === chartDays.length - 1;
                  return (
                    <g key={pt.dateIso}>
                      <title>{`${pt.label}: ${formatBRLFromCents(pt.revenueCents)} (${pt.entriesCount} lançamento(s))`}</title>
                      <rect
                        x={x}
                        y={y}
                        width={barWidth}
                        height={height}
                        rx="2"
                        fill={pt.revenueCents > 0 ? '#17694f' : '#e5e7e8'}
                      />
                      {showTickLabel && (
                        <text
                          x={x + barWidth / 2}
                          y="170"
                          textAnchor="middle"
                          fontSize="10"
                          fill="#676d73"
                          fontFamily="IBM Plex Mono, monospace"
                        >
                          {pt.label}
                        </text>
                      )}
                    </g>
                  );
                })}
              </svg>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <div>
              <h2 className="panel-title">Resumo da carteira</h2>
              <p className="panel-subtitle">Consolidado das assinaturas vigentes.</p>
            </div>
          </div>
          <div className="panel-body" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div className="well" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '13px', color: 'var(--muted)' }}>Assinantes em atraso (régua)</span>
              <strong className="tabular-nums" style={{ fontSize: '15px', color: totalPastDue > 0 ? 'var(--warn-ink)' : 'var(--text)' }}>
                {totalPastDue}
              </strong>
            </div>
            <div className="well" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '13px', color: 'var(--muted)' }}>Pausadas / canceladas</span>
              <strong className="tabular-nums" style={{ fontSize: '15px' }}>
                {data.pausedCount + data.canceledCount}
              </strong>
            </div>
            <div className="well" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '13px', color: 'var(--muted)' }}>Cupons resgatados</span>
              <strong className="tabular-nums" style={{ fontSize: '15px' }}>
                {data.couponRedemptionsCount}
              </strong>
            </div>
            <div className="well" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '13px', color: 'var(--muted)' }}>Regra após {data.graceDays} dias de atraso</span>
              <span className={`pill ${data.overdueActionPolicy === 'CANCEL' ? 'pill-neg' : 'pill-warn'}`}>
                {data.overdueActionPolicy === 'CANCEL' ? 'Cancelar' : 'Pausar'}
              </span>
            </div>
          </div>
        </section>
      </div>

      {/* Livro Caixa */}
      <section className="panel" aria-label="Livro caixa de entradas">
        <div className="panel-head">
          <div>
            <h2 className="panel-title">Lançamentos financeiros</h2>
            <p className="panel-subtitle">Entradas da App Store, Pix, cartão, cortesias e ajustes manuais.</p>
          </div>
          <div className="actions-row">
            <select
              className="select"
              style={{ width: 'auto', minHeight: '32px', fontSize: '12.5px' }}
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="ALL">Todos os status</option>
              <option value="PAID">Pago</option>
              <option value="PENDING">Pendente</option>
              <option value="OVERDUE">Vencida</option>
              <option value="REFUNDED">Estornada</option>
              <option value="CANCELED">Cancelada</option>
            </select>
            <select
              className="select"
              style={{ width: 'auto', minHeight: '32px', fontSize: '12.5px' }}
              value={methodFilter}
              onChange={(e) => setMethodFilter(e.target.value)}
            >
              <option value="ALL">Todos os métodos</option>
              <option value="APPLE_IAP">App Store</option>
              <option value="PIX">Pix</option>
              <option value="CARD">Cartão</option>
              <option value="COUPON">Cupom</option>
              <option value="COURTESY">Cortesia</option>
            </select>
          </div>
        </div>

        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Data</th>
                <th>Referência</th>
                <th>Usuário / Descrição</th>
                <th>Método</th>
                <th>Status</th>
                <th className="num">Líquido</th>
                <th style={{ textAlign: 'right' }}>Ações</th>
              </tr>
            </thead>
            <tbody>
              {filteredEntries.length === 0 ? (
                <tr>
                  <td colSpan={7} className="table-empty">
                    Nenhuma entrada financeira encontrada para o filtro selecionado.
                  </td>
                </tr>
              ) : (
                filteredEntries.map((entry) => {
                  const meta = entryStatusMeta(entry.status);
                  return (
                    <tr key={entry.id}>
                      <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)', whiteSpace: 'nowrap' }}>
                        {formatDateTimeBR(entry.paidAtUtc || entry.createdAtUtc)}
                      </td>
                      <td>
                        <span className="code-chip">{entry.referenceCode || entry.entryType}</span>
                      </td>
                      <td>
                        <div style={{ fontWeight: 500 }}>{entry.description}</div>
                        <div style={{ fontSize: '12px', color: 'var(--faint)' }}>
                          {entry.userEmail || 'Receita avulsa'}
                          {entry.couponCode ? ` · Cupom ${entry.couponCode}` : ''}
                        </div>
                      </td>
                      <td style={{ color: 'var(--muted)' }}>{paymentMethodLabel(entry.paymentMethod)}</td>
                      <td>
                        <span className={`pill pill-${meta.tone}`}>{meta.label}</span>
                      </td>
                      <td className="num" style={{ fontWeight: 600 }}>
                        {formatBRLFromCents(entry.netAmountCents)}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <div className="actions-row" style={{ justifyContent: 'flex-end' }}>
                          {entry.status !== 'PAID' && (
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              disabled={busyId === entry.id}
                              onClick={() => handleUpdateStatus(entry, 'PAID')}
                            >
                              Baixar
                            </button>
                          )}
                          {entry.status === 'PAID' &&
                            (entry.netAmountCents ?? 0) > 0 &&
                            entry.paymentMethod !== 'COURTESY' &&
                            entry.paymentMethod !== 'COUPON' &&
                            entry.entryType !== 'COUPON_TRIAL_GRANT' && (
                              <button
                                type="button"
                                className="btn btn-ghost btn-sm"
                                disabled={busyId === entry.id}
                                onClick={() => handleUpdateStatus(entry, 'REFUNDED')}
                              >
                                Estornar
                              </button>
                            )}
                          {entry.status === 'PAID' &&
                            ((entry.netAmountCents ?? 0) <= 0 ||
                              entry.paymentMethod === 'COURTESY' ||
                              entry.paymentMethod === 'COUPON' ||
                              entry.entryType === 'COUPON_TRIAL_GRANT') && (
                              <span style={{ fontSize: '11.5px', color: 'var(--faint)', padding: '0 6px' }}>
                                Cortesia
                              </span>
                            )}
                          <button
                            type="button"
                            className="btn btn-ghost btn-sm"
                            style={{ color: 'var(--neg-ink)' }}
                            disabled={busyId === entry.id}
                            onClick={() => handleDeleteEntry(entry)}
                          >
                            Excluir
                          </button>
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

      {/* Modal Editar Preço e Prazo de Plano */}
      {editingPlan && (
        <div className="modal-overlay" onClick={() => setEditingPlan(null)}>
          <form className="modal-dialog" onSubmit={handleSavePlan} onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <h2 className="modal-title">Editar plano: {editingPlan.id}</h2>
                <p className="modal-sub">
                  As alterações refletem imediatamente no catálogo de planos do aplicativo.
                </p>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEditingPlan(null)}>
                Fechar
              </button>
            </div>

            <div className="field">
              <label className="field-label">Nome exibido</label>
              <input
                className="input"
                value={planNameInput}
                onChange={(e) => setPlanNameInput(e.target.value)}
                required
              />
            </div>

            <div className="field">
              <label className="field-label">Descrição comercial</label>
              <textarea
                className="textarea"
                value={planDescInput}
                onChange={(e) => setPlanDescInput(e.target.value)}
              />
            </div>

            <div className="form-grid-2">
              <div className="field">
                <label className="field-label">Preço publicado (BRL)</label>
                <input
                  className="input mono"
                  value={planPriceBRL}
                  onChange={(e) => setPlanPriceBRL(e.target.value)}
                  placeholder="29.90"
                  required
                />
                <span className="field-hint">
                  Prévia exibida no app: <strong>{formatBRLFromCents(parsedEditCents)}</strong>
                </span>
              </div>
              <div className="field">
                <label className="field-label">Prazo grátis na adesão (dias)</label>
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

            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={planActive}
                onChange={(e) => setPlanActive(e.target.checked)}
              />
              Plano disponível para venda no aplicativo
            </label>

            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={() => setEditingPlan(null)}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-primary" disabled={savingPlan}>
                {savingPlan ? 'Publicando...' : 'Publicar no catálogo'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Modal Nova Entrada Financeira */}
      {showNewModal && (
        <div className="modal-overlay" onClick={() => setShowNewModal(false)}>
          <form className="modal-dialog" onSubmit={handleCreateEntry} onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <h2 className="modal-title">Registrar entrada financeira</h2>
                <p className="modal-sub">Adiciona lançamento auditado ao livro caixa do console.</p>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowNewModal(false)}>
                Fechar
              </button>
            </div>

            <div className="field">
              <label className="field-label">E-mail do usuário (opcional)</label>
              <input
                list="forma-users-emails"
                className="input"
                value={userEmail}
                onChange={(e) => setUserEmail(e.target.value)}
                placeholder="assinante@exemplo.com"
              />
              <datalist id="forma-users-emails">
                {users.map((u) => (
                  <option key={u.id} value={u.email} />
                ))}
              </datalist>
            </div>

            <div className="field">
              <label className="field-label">Descrição do lançamento</label>
              <input
                className="input"
                required
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Ex: Renovação mensal Pix"
              />
            </div>

            <div className="form-grid-2">
              <div className="field">
                <label className="field-label">Valor bruto (BRL)</label>
                <input
                  className="input mono"
                  required
                  value={amountBRL}
                  onChange={(e) => setAmountBRL(e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label">Desconto (BRL)</label>
                <input
                  className="input mono"
                  value={discountBRL}
                  onChange={(e) => setDiscountBRL(e.target.value)}
                />
              </div>
            </div>

            <div className="form-grid-3">
              <div className="field">
                <label className="field-label">Tipo</label>
                <select className="select" value={entryType} onChange={(e) => setEntryType(e.target.value)}>
                  <option value="MANUAL_INCOME">Entrada manual</option>
                  <option value="SUBSCRIPTION_CHARGE">Cobrança plano</option>
                  <option value="ADJUSTMENT">Ajuste</option>
                </select>
              </div>
              <div className="field">
                <label className="field-label">Método</label>
                <select className="select" value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}>
                  <option value="PIX">Pix</option>
                  <option value="CARD">Cartão</option>
                  <option value="APPLE_IAP">App Store</option>
                  <option value="COURTESY">Cortesia</option>
                </select>
              </div>
              <div className="field">
                <label className="field-label">Status</label>
                <select className="select" value={entryStatus} onChange={(e) => setEntryStatus(e.target.value)}>
                  <option value="PAID">Pago</option>
                  <option value="PENDING">Pendente</option>
                  <option value="OVERDUE">Vencida</option>
                </select>
              </div>
            </div>

            <div className="field">
              <label className="field-label">Cupom vinculado (opcional)</label>
              <input
                className="input mono"
                value={couponCode}
                onChange={(e) => setCouponCode(e.target.value.toUpperCase())}
                placeholder="VIP30DIAS"
              />
            </div>

            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={() => setShowNewModal(false)}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Salvando...' : 'Registrar entrada'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
