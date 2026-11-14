import React, { useState } from 'react';
import { Copy, Plus, Power, Trash2, Ticket } from 'lucide-react';
import { BillingDashboardData, CouponItem } from '../types';
import { formatBRLFromCents, formatDateTimeBR } from './billingFormat';

interface CouponsManagerTabProps {
  data: BillingDashboardData;
  onRefresh: () => void;
}

export const CouponsManagerTab: React.FC<CouponsManagerTabProps> = ({ data, onRefresh }) => {
  const [showModal, setShowModal] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [code, setCode] = useState('');
  const [description, setDescription] = useState('');
  const [couponType, setCouponType] = useState<'FREE_PERIOD_DAYS' | 'PERCENT_DISCOUNT' | 'FIXED_CENTS_DISCOUNT'>('FREE_PERIOD_DAYS');
  const [freeDays, setFreeDays] = useState('30');
  const [discountPercent, setDiscountPercent] = useState('50');
  const [discountBRL, setDiscountBRL] = useState('10.00');
  const [applicablePlanId, setApplicablePlanId] = useState('');
  const [maxRedemptions, setMaxRedemptions] = useState('');
  const [perUserLimit, setPerUserLimit] = useState('1');
  const [expiresDate, setExpiresDate] = useState('');

  const openPreset = (preset: 'FREE_7' | 'FREE_30' | 'OFF_50' | 'CUSTOM') => {
    setFeedback(null);
    if (preset === 'FREE_7') {
      setCode('FORMA7DIAS');
      setDescription('7 dias grátis no Forma Pro');
      setCouponType('FREE_PERIOD_DAYS');
      setFreeDays('7');
    } else if (preset === 'FREE_30') {
      setCode('FORMA30DIAS');
      setDescription('1 mês grátis no Forma Pro');
      setCouponType('FREE_PERIOD_DAYS');
      setFreeDays('30');
    } else if (preset === 'OFF_50') {
      setCode('FORMA50OFF');
      setDescription('50% de desconto na assinatura');
      setCouponType('PERCENT_DISCOUNT');
      setDiscountPercent('50');
    } else {
      setCode('');
      setDescription('');
      setCouponType('FREE_PERIOD_DAYS');
      setFreeDays('14');
    }
    setShowModal(true);
  };

  const handleCreateCoupon = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setSubmitting(true);
    setFeedback(null);
    try {
      const resp = await fetch('/api/admin/billing/coupons', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          code: code.trim().toUpperCase(),
          description: description.trim(),
          couponType,
          freePeriodDays: couponType === 'FREE_PERIOD_DAYS' ? Math.max(1, parseInt(freeDays, 10) || 7) : null,
          discountPercent: couponType === 'PERCENT_DISCOUNT' ? Math.max(1, Math.min(100, parseInt(discountPercent, 10) || 10)) : null,
          discountCents: couponType === 'FIXED_CENTS_DISCOUNT' ? Math.max(100, Math.round(parseFloat(discountBRL.replace(',', '.')) * 100) || 500) : null,
          applicablePlanId: applicablePlanId || null,
          maxRedemptions: maxRedemptions ? Math.max(1, parseInt(maxRedemptions, 10)) : null,
          perUserLimit: Math.max(1, parseInt(perUserLimit, 10) || 1),
          expiresAtUtc: expiresDate ? new Date(`${expiresDate}T23:59:59Z`).toISOString() : null,
          active: true
        })
      });
      const resData = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        setFeedback({ text: resData.error || 'Erro ao criar cupom.', isError: true });
      } else {
        setShowModal(false);
        setFeedback({ text: `Cupom ${resData.code} criado e pronto para resgate no app iOS.`, isError: false });
        onRefresh();
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggle = async (c: CouponItem) => {
    const resp = await fetch(`/api/admin/billing/coupons/${c.id}/toggle`, {
      method: 'POST',
      credentials: 'include'
    });
    if (resp.ok) {
      onRefresh();
    }
  };

  const handleDelete = async (c: CouponItem) => {
    if (!window.confirm(`Excluir o cupom ${c.code}?`)) return;
    const resp = await fetch(`/api/admin/billing/coupons/${c.id}`, {
      method: 'DELETE',
      credentials: 'include'
    });
    if (resp.ok) {
      onRefresh();
    }
  };

  const describeBenefit = (c: CouponItem) => {
    if (c.couponType === 'FREE_PERIOD_DAYS') return `${c.freePeriodDays || 7} dias grátis Pro`;
    if (c.couponType === 'PERCENT_DISCOUNT') return `${c.discountPercent || 0}% OFF`;
    return `${formatBRLFromCents(c.discountCents)} OFF`;
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '-0.3px' }}>Cupons &amp; Configuração de Período Grátis</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>
            Crie códigos promocionais com dias grátis automáticos no Forma Pro ou descontos percentuais/fixos com limite de uso.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <button onClick={() => openPreset('FREE_7')} style={{ padding: '8px 12px', borderRadius: '8px', border: '1px solid rgba(56,189,248,0.3)', backgroundColor: 'rgba(56,189,248,0.12)', color: '#38bdf8', fontSize: '12px', fontWeight: 700 }}>+ 7 Dias Grátis</button>
          <button onClick={() => openPreset('FREE_30')} style={{ padding: '8px 12px', borderRadius: '8px', border: '1px solid rgba(56,189,248,0.3)', backgroundColor: 'rgba(56,189,248,0.12)', color: '#38bdf8', fontSize: '12px', fontWeight: 700 }}>+ 30 Dias Grátis</button>
          <button onClick={() => openPreset('OFF_50')} style={{ padding: '8px 12px', borderRadius: '8px', border: '1px solid var(--border-color)', backgroundColor: 'rgba(255,255,255,0.05)', color: 'var(--text-primary)', fontSize: '12px', fontWeight: 700 }}>+ 50% OFF</button>
          <button onClick={() => openPreset('CUSTOM')} style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '8px 14px', borderRadius: '8px', border: 'none', backgroundColor: '#10b981', color: '#042f1a', fontSize: '13px', fontWeight: 800 }}>
            <Plus size={15} /> Novo Cupom
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

      {/* Coupons Table */}
      <section className="surface-panel" style={{ padding: '18px 20px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>Cupons Configurados ({data.coupons.length})</h3>
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Benefício</th>
                <th>Restrição / Validade</th>
                <th className="num">Resgates</th>
                <th>Status</th>
                <th style={{ textAlign: 'right' }}>Ações</th>
              </tr>
            </thead>
            <tbody>
              {data.coupons.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', padding: '28px', color: 'var(--text-muted)' }}>
                    Nenhum cupom cadastrado. Use os atalhos acima para criar um cupom de período grátis agora.
                  </td>
                </tr>
              ) : (
                data.coupons.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <code className="font-mono" style={{ padding: '3px 8px', borderRadius: '6px', backgroundColor: 'rgba(255,255,255,0.06)', color: '#f1f5f9', fontWeight: 700 }}>{c.code}</code>
                        <button
                          onClick={() => {
                            navigator.clipboard?.writeText(c.code);
                            setFeedback({ text: `Código ${c.code} copiado.`, isError: false });
                          }}
                          title="Copiar código"
                          style={{ background: 'none', border: 'none', color: 'var(--text-muted)', padding: '2px' }}
                        >
                          <Copy size={13} />
                        </button>
                      </div>
                      {c.description && <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '3px' }}>{c.description}</div>}
                    </td>
                    <td>
                      <span style={{ padding: '3px 9px', borderRadius: '999px', fontSize: '12px', fontWeight: 700, backgroundColor: c.couponType === 'FREE_PERIOD_DAYS' ? 'rgba(56,189,248,0.14)' : 'rgba(16,185,129,0.14)', color: c.couponType === 'FREE_PERIOD_DAYS' ? '#38bdf8' : '#34d399' }}>
                        {describeBenefit(c)}
                      </span>
                    </td>
                    <td style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                      <div>{c.applicablePlanId ? `Plano: ${c.applicablePlanId}` : 'Todos os planos'} • {c.perUserLimit}/usuário</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Expira: {c.expiresAtUtc ? formatDateTimeBR(c.expiresAtUtc) : 'Sem expiração'}</div>
                    </td>
                    <td className="num" style={{ fontWeight: 700 }}>
                      {c.currentRedemptions}{c.maxRedemptions ? ` / ${c.maxRedemptions}` : ''}
                    </td>
                    <td>
                      <span style={{ padding: '3px 8px', borderRadius: '999px', fontSize: '11px', fontWeight: 700, backgroundColor: c.active ? 'rgba(16,185,129,0.14)' : 'rgba(148,163,184,0.14)', color: c.active ? '#34d399' : '#94a3b8' }}>
                        {c.active ? 'Ativo' : 'Pausado'}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <div style={{ display: 'inline-flex', gap: '5px' }}>
                        <button onClick={() => handleToggle(c)} title={c.active ? 'Desativar' : 'Ativar'} style={{ padding: '5px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(255,255,255,0.04)', color: c.active ? '#fbbf24' : '#34d399' }}>
                          <Power size={14} />
                        </button>
                        <button onClick={() => handleDelete(c)} title="Excluir" style={{ padding: '5px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'rgba(239,68,68,0.1)', color: '#f87171' }}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Redemptions History */}
      <section className="surface-panel" style={{ padding: '18px 20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
          <Ticket size={16} color="#38bdf8" />
          <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Resgates Realizados ({data.recentRedemptions.length})</h3>
        </div>
        {data.recentRedemptions.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>Nenhum cupom resgatado pelos usuários ainda.</p>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '10px' }}>
            {data.recentRedemptions.map((r) => (
              <div key={r.id} className="surface-inset" style={{ padding: '10px 12px', fontSize: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                  <code className="font-mono" style={{ color: '#38bdf8' }}>{r.couponCode}</code>
                  <span className="tabular-nums" style={{ color: 'var(--text-muted)', fontWeight: 500 }}>{formatDateTimeBR(r.redeemedAtUtc)}</span>
                </div>
                <div style={{ color: 'var(--text-primary)', marginTop: '3px' }}>{r.userEmail}</div>
                <div style={{ color: 'var(--text-secondary)', fontSize: '11px' }}>{r.benefitSummary}</div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Modal Criar Cupom */}
      {showModal && (
        <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.72)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px', zIndex: 50 }}>
          <form onSubmit={handleCreateCoupon} className="surface-panel" style={{ width: '100%', maxWidth: '480px', padding: '22px' }}>
            <h3 style={{ fontSize: '17px', fontWeight: 800, marginBottom: '14px' }}>Configurar Novo Cupom</h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Código do Cupom *</label>
                  <input required value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="FORMA30DIAS" style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }} />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Modalidade</label>
                  <select value={couponType} onChange={(e) => setCouponType(e.target.value as any)} style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}>
                    <option value="FREE_PERIOD_DAYS">Período Grátis (Dias)</option>
                    <option value="PERCENT_DISCOUNT">Desconto Percentual (%)</option>
                    <option value="FIXED_CENTS_DISCOUNT">Desconto Fixo (R$)</option>
                  </select>
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Descrição</label>
                <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Ex: Acesso gratuito de boas-vindas" style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }} />
              </div>

              {couponType === 'FREE_PERIOD_DAYS' && (
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Quantidade de Dias Grátis *</label>
                  <input type="number" min="1" max="3650" required value={freeDays} onChange={(e) => setFreeDays(e.target.value)} style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: '#38bdf8', fontWeight: 700 }} />
                </div>
              )}

              {couponType === 'PERCENT_DISCOUNT' && (
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Percentual de Desconto (1–100%)</label>
                  <input type="number" min="1" max="100" required value={discountPercent} onChange={(e) => setDiscountPercent(e.target.value)} style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: '#34d399', fontWeight: 700 }} />
                </div>
              )}

              {couponType === 'FIXED_CENTS_DISCOUNT' && (
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Desconto Fixo (R$)</label>
                  <input required value={discountBRL} onChange={(e) => setDiscountBRL(e.target.value)} style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: '#34d399', fontWeight: 700 }} />
                </div>
              )}

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Limite Global (vazio = ilimitado)</label>
                  <input type="number" min="1" value={maxRedemptions} onChange={(e) => setMaxRedemptions(e.target.value)} placeholder="Ilimitado" style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)' }} />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Limite por Usuário</label>
                  <input type="number" min="1" value={perUserLimit} onChange={(e) => setPerUserLimit(e.target.value)} style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)' }} />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Plano Elegível</label>
                  <select value={applicablePlanId} onChange={(e) => setApplicablePlanId(e.target.value)} style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }}>
                    <option value="">Qualquer plano</option>
                    {data.plans.map((p) => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>Data de Expiração (opcional)</label>
                  <input type="date" value={expiresDate} onChange={(e) => setExpiresDate(e.target.value)} style={{ width: '100%', padding: '9px 10px', borderRadius: '8px', backgroundColor: 'var(--bg-primary)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', fontSize: '13px' }} />
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '18px' }}>
              <button type="button" onClick={() => setShowModal(false)} style={{ padding: '9px 14px', borderRadius: '8px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-secondary)' }}>Cancelar</button>
              <button type="submit" disabled={submitting} style={{ padding: '9px 16px', borderRadius: '8px', border: 'none', backgroundColor: '#10b981', color: '#042f1a', fontWeight: 800 }}>
                {submitting ? 'Salvando...' : 'Criar Cupom'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
