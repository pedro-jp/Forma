import React, { useMemo, useState } from 'react';
import { Download, Plus, CheckCircle2, RotateCcw, XCircle, Trash2, AlertCircle } from 'lucide-react';
import { BillingDashboardData, PaymentEntryItem, UserSummary } from '../types';
import { entryStatusMeta, formatBRLFromCents, formatDateTimeBR } from './billingFormat';

interface BillingDashboardTabProps {
  data: BillingDashboardData;
  users: UserSummary[];
  onRefresh: () => void;
}

export const BillingDashboardTab: React.FC<BillingDashboardTabProps> = ({ data, users, onRefresh }) => {
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [methodFilter, setMethodFilter] = useState<string>('ALL');
  const [showNewModal, setShowNewModal] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  // Novo lançamento manual
  const [userEmail, setUserEmail] = useState('');
  const [description, setDescription] = useState('');
  const [amountBRL, setAmountBRL] = useState('24.90');
  const [discountBRL, setDiscountBRL] = useState('0.00');
  const [paymentMethod, setPaymentMethod] = useState('PIX');
  const [entryType, setEntryType] = useState('MANUAL_INCOME');
  const [entryStatus, setEntryStatus] = useState('PAID');
  const [couponCode, setCouponCode] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const filteredEntries = useMemo(() => {
    return data.recentEntries.filter((e) => {
      if (statusFilter !== 'ALL' && e.status.toUpperCase() !== statusFilter) return false;
      if (methodFilter !== 'ALL' && e.paymentMethod.toUpperCase() !== methodFilter) return false;
      return true;
    });
  }, [data.recentEntries, statusFilter, methodFilter]);

  const maxDailyRevenue = useMemo(() => {
    const vals = data.dailyRevenueLast14Days.map((p) => p.revenueCents);
    return Math.max(2500, ...vals);
  }, [data.dailyRevenueLast14Days]);

  const totalPastDue = data.pastDueDay1Count + data.pastDueDay2Count + data.pastDueDay3Count;

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
        setFeedback({ text: 'Falha ao atualizar status da entrada.', isError: true });
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header Row + Primary Action */}
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '-0.3px' }}>Receita &amp; Controle de Entradas</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>
            Receita recorrente confirmada de {formatBRLFromCents(data.mrrCents)}/mês • {totalPastDue > 0 ? `${totalPastDue} assinatura(s) na régua de atraso` : 'Nenhuma assinatura em atraso hoje'}.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          <button
            onClick={handleExportCsv}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '7px',
              padding: '9px 14px',
              borderRadius: '10px',
              backgroundColor: 'rgba(255,255,255,0.05)',
              border: '1px solid var(--border-color)',
              color: 'var(--text-primary)',
              fontSize: '13px',
              fontWeight: 600
            }}
          >
            <Download size={15} />
            Exportar CSV
          </button>
          <button
            onClick={() => setShowNewModal(true)}
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
            Nova Entrada
          </button>
        </div>
      </div>

      {feedback && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: '10px 14px',
          borderRadius: '10px',
          fontSize: '13px',
          backgroundColor: feedback.isError ? 'rgba(239,68,68,0.12)' : 'rgba(16,185,129,0.12)',
          border: `1px solid ${feedback.isError ? 'rgba(239,68,68,0.3)' : 'rgba(16,185,129,0.3)'}`,
          color: feedback.isError ? '#f87171' : '#34d399'
        }}>
          {feedback.isError ? <AlertCircle size={16} /> : <CheckCircle2 size={16} />}
          <span>{feedback.text}</span>
        </div>
      )}

      {/* Clean Typographic KPI Strip (app-screens.md §5: no icon circles, tabular numerals) */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(185px, 1fr))', gap: '12px' }}>
        <div className="surface-panel" style={{ padding: '16px 18px' }}>
          <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>MRR Mensal</div>
          <div className="tabular-nums" style={{ fontSize: '26px', fontWeight: 800, color: '#34d399', marginTop: '6px' }}>{formatBRLFromCents(data.mrrCents)}</div>
          <div className="tabular-nums" style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>ARR proj. {formatBRLFromCents(data.arrCents)}</div>
        </div>

        <div className="surface-panel" style={{ padding: '16px 18px' }}>
          <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Receita 30 Dias</div>
          <div className="tabular-nums" style={{ fontSize: '26px', fontWeight: 800, marginTop: '6px' }}>{formatBRLFromCents(data.paidRevenue30dCents)}</div>
          <div className="tabular-nums" style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>Líquida total: {formatBRLFromCents(data.totalNetRevenueCents)}</div>
        </div>

        <div className="surface-panel" style={{ padding: '16px 18px' }}>
          <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Assinaturas Pro</div>
          <div className="tabular-nums" style={{ fontSize: '26px', fontWeight: 800, marginTop: '6px' }}>{data.activeSubscribersCount + data.trialingCount}</div>
          <div className="tabular-nums" style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>{data.activeSubscribersCount} pagas • {data.trialingCount} período grátis</div>
        </div>

        <div className="surface-panel" style={{ padding: '16px 18px' }}>
          <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>A Receber / Vencidas</div>
          <div className="tabular-nums" style={{ fontSize: '26px', fontWeight: 800, color: data.pendingRevenueCents > 0 ? '#fbbf24' : 'var(--text-primary)', marginTop: '6px' }}>
            {formatBRLFromCents(data.pendingRevenueCents)}
          </div>
          <div className="tabular-nums" style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>Estornos: {formatBRLFromCents(data.refundedTotalCents)}</div>
        </div>
      </div>

      {/* 14-Day Revenue Chart + Breakdown */}
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) minmax(260px, 1fr)', gap: '16px' }}>
        <section className="surface-panel" style={{ padding: '18px 20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '14px' }}>
            <div>
              <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Entradas Liquidadas (Últimos 14 Dias)</h3>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Valores diários em R$ conciliados (StoreKit, Webhook e Lançamentos Manuais)</span>
            </div>
            <span className="tabular-nums" style={{ fontSize: '12px', color: '#34d399', fontWeight: 700 }}>
              {formatBRLFromCents(data.dailyRevenueLast14Days.reduce((acc, p) => acc + p.revenueCents, 0))}
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'flex-end', gap: '6px', height: '140px', paddingTop: '12px', borderBottom: '1px solid var(--border-color)' }}>
            {data.dailyRevenueLast14Days.map((pt) => {
              const pct = Math.max(pt.revenueCents > 0 ? 8 : 2, Math.round((pt.revenueCents / maxDailyRevenue) * 100));
              return (
                <div
                  key={pt.dateIso}
                  title={`${pt.label}: ${formatBRLFromCents(pt.revenueCents)} (${pt.entriesCount} entradas)`}
                  style={{ flex: 1, height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', alignItems: 'center', gap: '4px' }}
                >
                  <div
                    style={{
                      width: '100%',
                      maxWidth: '28px',
                      height: `${pct}%`,
                      borderRadius: '4px 4px 0 0',
                      backgroundColor: pt.revenueCents > 0 ? '#10b981' : 'rgba(255,255,255,0.08)',
                      transition: 'height 220ms var(--ease-out)'
                    }}
                  />
                </div>
              );
            })}
          </div>
          <div style={{ display: 'flex', gap: '6px', marginTop: '6px' }}>
            {data.dailyRevenueLast14Days.map((pt, i) => (
              <div key={pt.dateIso} className="tabular-nums" style={{ flex: 1, textAlign: 'center', fontSize: '10px', color: i % 2 === 0 ? 'var(--text-muted)' : 'transparent' }}>
                {pt.label}
              </div>
            ))}
          </div>
        </section>

        <section className="surface-panel" style={{ padding: '18px 20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Resumo de Saúde das Inscrições</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '13px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Ativas Pagas</span>
                <span className="tabular-nums" style={{ fontWeight: 700, color: '#34d399' }}>{data.activeSubscribersCount}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Período Grátis / Cupom</span>
                <span className="tabular-nums" style={{ fontWeight: 700, color: '#38bdf8' }}>{data.trialingCount}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Atraso 1–3 Dias (Avisos)</span>
                <span className="tabular-nums" style={{ fontWeight: 700, color: totalPastDue > 0 ? '#fbbf24' : 'var(--text-muted)' }}>{totalPastDue}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Pausadas / Canceladas</span>
                <span className="tabular-nums" style={{ fontWeight: 700, color: '#f87171' }}>{data.pausedCount + data.canceledCount}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Cupons Resgatados</span>
                <span className="tabular-nums" style={{ fontWeight: 700 }}>{data.couponRedemptionsCount}</span>
              </div>
            </div>
          </div>

          <div style={{ marginTop: '14px', paddingTop: '12px', borderTop: '1px solid var(--border-color)', fontSize: '12px', color: 'var(--text-muted)' }}>
            Política pós-3 dias: <strong style={{ color: 'var(--text-primary)' }}>{data.overdueActionPolicy === 'CANCEL' ? 'Cancelar' : 'Pausar'}</strong> automaticamente.
          </div>
        </section>
      </div>

      {/* Entradas Financeiras Table */}
      <section className="surface-panel" style={{ padding: '18px 20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '14px' }}>
          <div>
            <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Livro de Entradas &amp; Lançamentos</h3>
            <span className="tabular-nums" style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{filteredEntries.length} movimentações listadas</span>
          </div>

          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ padding: '7px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '12px' }}
            >
              <option value="ALL">Todos os status</option>
              <option value="PAID">Pago</option>
              <option value="PENDING">Pendente</option>
              <option value="OVERDUE">Vencida</option>
              <option value="REFUNDED">Estornada</option>
              <option value="CANCELED">Cancelada</option>
            </select>

            <select
              value={methodFilter}
              onChange={(e) => setMethodFilter(e.target.value)}
              style={{ padding: '7px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '12px' }}
            >
              <option value="ALL">Todos os meios</option>
              <option value="APPLE_IAP">Apple StoreKit</option>
              <option value="PIX">PIX</option>
              <option value="CARD">Cartão</option>
              <option value="COUPON">Cupom</option>
              <option value="WEBHOOK">Webhook</option>
              <option value="MANUAL">Manual</option>
            </select>
          </div>
        </div>

        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Data</th>
                <th>Descrição &amp; Usuário</th>
                <th>Meio / Ref</th>
                <th>Status</th>
                <th className="num">Bruto</th>
                <th className="num">Desc.</th>
                <th className="num">Líquido</th>
                <th style={{ textAlign: 'right' }}>Ações</th>
              </tr>
            </thead>
            <tbody>
              {filteredEntries.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '28px', color: 'var(--text-muted)' }}>
                    Nenhuma entrada financeira encontrada neste filtro.
                  </td>
                </tr>
              ) : (
                filteredEntries.map((e) => {
                  const st = entryStatusMeta(e.status);
                  return (
                    <tr key={e.id}>
                      <td className="tabular-nums" style={{ color: 'var(--text-secondary)', fontSize: '12px', whiteSpace: 'nowrap' }}>
                        {formatDateTimeBR(e.createdAtUtc)}
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{e.description}</div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                          {e.userEmail || 'Receita avulsa'}
                          {e.couponCode ? ` • Cupom ${e.couponCode}` : ''}
                        </div>
                      </td>
                      <td>
                        <div style={{ fontSize: '12px', fontWeight: 600 }}>{e.paymentMethod}</div>
                        <div className="font-mono" style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{e.referenceCode || '—'}</div>
                      </td>
                      <td>
                        <span style={{ padding: '3px 9px', borderRadius: '999px', fontSize: '11px', fontWeight: 700, color: st.color, backgroundColor: st.bg }}>
                          {st.label}
                        </span>
                      </td>
                      <td className="num" style={{ color: 'var(--text-secondary)' }}>{formatBRLFromCents(e.grossAmountCents)}</td>
                      <td className="num" style={{ color: e.discountCents > 0 ? '#38bdf8' : 'var(--text-muted)' }}>
                        {e.discountCents > 0 ? `-${formatBRLFromCents(e.discountCents)}` : '—'}
                      </td>
                      <td className="num" style={{ fontWeight: 700, color: e.status === 'REFUNDED' ? '#c084fc' : 'var(--text-primary)' }}>
                        {formatBRLFromCents(e.netAmountCents)}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <div style={{ display: 'inline-flex', gap: '4px' }}>
                          {e.status !== 'PAID' && (
                            <button
                              disabled={busyId === e.id}
                              onClick={() => handleUpdateStatus(e, 'PAID')}
                              title="Marcar como Pago"
                              style={{ padding: '5px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(16,185,129,0.1)', color: '#34d399' }}
                            >
                              <CheckCircle2 size={14} />
                            </button>
                          )}
                          {e.status === 'PAID' && (
                            <button
                              disabled={busyId === e.id}
                              onClick={() => handleUpdateStatus(e, 'REFUNDED')}
                              title="Estornar Entrada"
                              style={{ padding: '5px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(139,92,246,0.12)', color: '#c084fc' }}
                            >
                              <RotateCcw size={14} />
                            </button>
                          )}
                          {e.status !== 'CANCELED' && e.status !== 'REFUNDED' && (
                            <button
                              disabled={busyId === e.id}
                              onClick={() => handleUpdateStatus(e, 'CANCELED')}
                              title="Cancelar Entrada"
                              style={{ padding: '5px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(245,158,11,0.1)', color: '#fbbf24' }}
                            >
                              <XCircle size={14} />
                            </button>
                          )}
                          <button
                            disabled={busyId === e.id}
                            onClick={() => handleDeleteEntry(e)}
                            title="Excluir Entrada"
                            style={{ padding: '5px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(239,68,68,0.1)', color: '#f87171' }}
                          >
                            <Trash2 size={14} />
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

      {/* Modal Nova Entrada */}
      {showNewModal && (
        <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px', zIndex: 50 }}>
          <form onSubmit={handleCreateEntry} className="surface-panel" style={{ width: '100%', maxWidth: '480px', padding: '22px' }}>
            <h3 style={{ fontSize: '17px', fontWeight: 800, marginBottom: '4px' }}>Registrar Entrada Financeira</h3>
            <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '16px' }}>Lance pagamentos PIX, cartão, ajustes ou receitas manuais no painel.</p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Usuário vinculado (opcional)</label>
                <select
                  value={userEmail}
                  onChange={(e) => setUserEmail(e.target.value)}
                  style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                >
                  <option value="">Entrada avulsa / sem usuário</option>
                  {users.map((u) => (
                    <option key={u.id} value={u.email}>{u.name} ({u.email})</option>
                  ))}
                </select>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Descrição *</label>
                <input
                  required
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Ex: Renovação PIX Forma Pro Mensal"
                  style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Valor Bruto (R$)</label>
                  <input
                    required
                    value={amountBRL}
                    onChange={(e) => setAmountBRL(e.target.value)}
                    style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Desconto (R$)</label>
                  <input
                    value={discountBRL}
                    onChange={(e) => setDiscountBRL(e.target.value)}
                    style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Meio de Pagamento</label>
                  <select
                    value={paymentMethod}
                    onChange={(e) => setPaymentMethod(e.target.value)}
                    style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                  >
                    <option value="PIX">PIX</option>
                    <option value="CARD">Cartão</option>
                    <option value="APPLE_IAP">Apple StoreKit</option>
                    <option value="MANUAL">Manual / Transferência</option>
                    <option value="COUPON">Cupom</option>
                  </select>
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Status Inicial</label>
                  <select
                    value={entryStatus}
                    onChange={(e) => setEntryStatus(e.target.value)}
                    style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                  >
                    <option value="PAID">Pago</option>
                    <option value="PENDING">Pendente</option>
                    <option value="OVERDUE">Em Atraso</option>
                  </select>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Tipo</label>
                  <select
                    value={entryType}
                    onChange={(e) => setEntryType(e.target.value)}
                    style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                  >
                    <option value="MANUAL_INCOME">Receita Manual</option>
                    <option value="SUBSCRIPTION_CHARGE">Cobrança de Assinatura</option>
                    <option value="ADJUSTMENT">Ajuste</option>
                  </select>
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Cupom (opcional)</label>
                  <input
                    value={couponCode}
                    onChange={(e) => setCouponCode(e.target.value)}
                    placeholder="EX: VIP30"
                    style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}
                  />
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '18px' }}>
              <button
                type="button"
                onClick={() => setShowNewModal(false)}
                style={{ padding: '9px 14px', borderRadius: '8px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-secondary)', fontSize: '13px' }}
              >
                Cancelar
              </button>
              <button
                type="submit"
                disabled={submitting}
                style={{ padding: '9px 16px', borderRadius: '8px', border: 'none', backgroundColor: '#10b981', color: '#042f1a', fontWeight: 800, fontSize: '13px' }}
              >
                {submitting ? 'Salvando...' : 'Salvar Entrada'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
