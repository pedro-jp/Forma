import React, { useMemo, useState } from 'react';
import { Copy, Pencil, Plus, RefreshCw, Send } from 'lucide-react';
import { BillingDashboardData, CouponItem, UserSummary } from '../types';
import { formatBRLFromCents, formatDateTimeBR } from './billingFormat';

const PROMO_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

const generateSecurePromoCode = (prefix: string): string => {
  const arr = new Uint32Array(8);
  if (typeof window !== 'undefined' && window.crypto?.getRandomValues) {
    window.crypto.getRandomValues(arr);
  } else {
    for (let i = 0; i < arr.length; i++) {
      arr[i] = Math.floor(Math.random() * PROMO_ALPHABET.length);
    }
  }
  let suffix = '';
  for (let i = 0; i < arr.length; i++) {
    suffix += PROMO_ALPHABET[arr[i] % PROMO_ALPHABET.length];
  }
  return `FRM-${prefix}-${suffix}`;
};

const defaultExpiryDatePlusDays = (days: number): string => {
  const dt = new Date(Date.now() + days * 86400_000);
  return dt.toISOString().slice(0, 10);
};

interface CouponsManagerTabProps {
  data: BillingDashboardData;
  users: UserSummary[];
  onRefresh: () => void;
}

export const CouponsManagerTab: React.FC<CouponsManagerTabProps> = ({ data, users, onRefresh }) => {
  const [showModal, setShowModal] = useState(false);
  const [editingCoupon, setEditingCoupon] = useState<CouponItem | null>(null);
  const [reactivateOnSave, setReactivateOnSave] = useState(true);
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Modal aplicar cupom diretamente a usuário
  const [applyCouponTarget, setApplyCouponTarget] = useState<CouponItem | null>(null);
  const [applyUserId, setApplyUserId] = useState<string>(users[0]?.id || '');
  const [applyCustomEmail, setApplyCustomEmail] = useState<string>('');
  const [applyFreeMode, setApplyFreeMode] = useState<'IMMEDIATE' | 'NEXT_BILLING'>('IMMEDIATE');
  const [applyNote, setApplyNote] = useState<string>('');
  const [applyingCoupon, setApplyingCoupon] = useState(false);

  const [code, setCode] = useState('');
  const [description, setDescription] = useState('');
  const [couponType, setCouponType] = useState<'FREE_PERIOD_DAYS' | 'PERCENT_DISCOUNT' | 'FIXED_CENTS_DISCOUNT'>('FREE_PERIOD_DAYS');
  const [freeDays, setFreeDays] = useState('30');
  const [discountPercent, setDiscountPercent] = useState('50');
  const [discountBRL, setDiscountBRL] = useState('10.00');
  const [applicablePlanId, setApplicablePlanId] = useState('');
  const [maxRedemptions, setMaxRedemptions] = useState('1');
  const [perUserLimit, setPerUserLimit] = useState('1');
  const [restrictedToEmail, setRestrictedToEmail] = useState('');
  const [firstTimeOnly, setFirstTimeOnly] = useState(false);
  const [expiresDate, setExpiresDate] = useState(() => defaultExpiryDatePlusDays(30));

  const metrics = useMemo(() => {
    const active = data.coupons.filter((c) => c.active && !c.exhausted && !c.expired).length;
    const remainingTotal = data.coupons
      .filter((c) => c.active && !c.exhausted && !c.expired)
      .reduce((acc, c) => acc + Math.max(0, (c.maxRedemptions || 1) - (c.currentRedemptions || 0)), 0);
    const closedCount = data.coupons.filter((c) => c.exhausted || c.expired).length;
    const redemptions = data.coupons.reduce((acc, c) => acc + (c.currentRedemptions || 0), 0);
    return { active, remainingTotal, closedCount, redemptions };
  }, [data.coupons]);

  const openPreset = (preset: 'FREE_7' | 'FREE_30' | 'OFF_50' | 'CUSTOM') => {
    setFeedback(null);
    setEditingCoupon(null);
    setReactivateOnSave(true);
    setApplicablePlanId('');
    setMaxRedemptions('1');
    setPerUserLimit('1');
    setRestrictedToEmail('');
    setFirstTimeOnly(false);
    setExpiresDate(defaultExpiryDatePlusDays(30));
    if (preset === 'FREE_7') {
      setCode(generateSecurePromoCode('7D'));
      setDescription('Cortesia de 7 dias grátis no Forma Pro');
      setCouponType('FREE_PERIOD_DAYS');
      setFreeDays('7');
    } else if (preset === 'FREE_30') {
      setCode(generateSecurePromoCode('30D'));
      setDescription('Cortesia de 30 dias grátis no Forma Pro');
      setCouponType('FREE_PERIOD_DAYS');
      setFreeDays('30');
    } else if (preset === 'OFF_50') {
      setCode(generateSecurePromoCode('50P'));
      setDescription('Desconto de 50% na próxima cobrança');
      setCouponType('PERCENT_DISCOUNT');
      setDiscountPercent('50');
    } else {
      setCode(generateSecurePromoCode('VIP'));
      setDescription('');
      setCouponType('FREE_PERIOD_DAYS');
      setFreeDays('14');
    }
    setShowModal(true);
  };

  const openEditCoupon = (c: CouponItem) => {
    setFeedback(null);
    setEditingCoupon(c);
    setCode(c.code);
    setDescription(c.description || '');
    setCouponType((c.couponType as any) || 'FREE_PERIOD_DAYS');
    setFreeDays(String(c.freePeriodDays || 7));
    setDiscountPercent(String(c.discountPercent || 25));
    setDiscountBRL(c.discountCents ? (c.discountCents / 100).toFixed(2) : '10.00');
    setApplicablePlanId(c.applicablePlanId || '');
    setMaxRedemptions(String(Math.max(1, c.maxRedemptions || 1)));
    setPerUserLimit(String(Math.max(1, c.perUserLimit || 1)));
    setRestrictedToEmail(c.restrictedToEmail || '');
    setFirstTimeOnly(Boolean(c.firstTimeOnly));
    setExpiresDate(c.expiresAtUtc ? c.expiresAtUtc.slice(0, 10) : '');
    setReactivateOnSave(true);
    setShowModal(true);
  };

  const handleSaveCoupon = async (ev: React.FormEvent) => {
    ev.preventDefault();
    const minQuota = editingCoupon ? Math.max(1, editingCoupon.currentRedemptions || 0) : 1;
    const parsedMax = parseInt(maxRedemptions, 10);
    if (!Number.isFinite(parsedMax) || parsedMax < minQuota || parsedMax > 10000) {
      setFeedback({
        text: `Informe uma cota finita entre ${minQuota} e 10.000 resgates.`,
        isError: true
      });
      return;
    }
    const parsedPerUser = Math.max(1, parseInt(perUserLimit, 10) || 1);
    if (parsedPerUser > parsedMax) {
      setFeedback({
        text: `O limite por usuário (${parsedPerUser}) não pode ultrapassar o limite total (${parsedMax}).`,
        isError: true
      });
      return;
    }

    setSubmitting(true);
    setFeedback(null);
    try {
      const endpoint = editingCoupon
        ? `/api/admin/billing/coupons/${editingCoupon.id}`
        : '/api/admin/billing/coupons';
      const method = editingCoupon ? 'PUT' : 'POST';
      const shouldActivate = editingCoupon
        ? (reactivateOnSave && parsedMax > (editingCoupon.currentRedemptions || 0) ? true : editingCoupon.active)
        : true;

      const resp = await fetch(endpoint, {
        method,
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
          maxRedemptions: parsedMax,
          perUserLimit: parsedPerUser,
          restrictedToEmail: restrictedToEmail.trim() || '',
          firstTimeOnly,
          expiresAtUtc: expiresDate ? new Date(`${expiresDate}T23:59:59Z`).toISOString() : '',
          active: shouldActivate
        })
      });
      const resData = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        setFeedback({ text: resData.error || 'Erro ao salvar cupom.', isError: true });
      } else {
        setShowModal(false);
        setEditingCoupon(null);
        setFeedback({
          text: editingCoupon
            ? `Cota do cupom ${resData.code} atualizada (${resData.currentRedemptions}/${resData.maxRedemptions}).`
            : `Cupom ${resData.code} criado com limite de ${resData.maxRedemptions} uso(s).`,
          isError: false
        });
        onRefresh();
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openApplyModal = (c: CouponItem) => {
    setFeedback(null);
    setApplyCouponTarget(c);
    setApplyUserId(users[0]?.id || '');
    setApplyCustomEmail('');
    setApplyFreeMode(c.couponType === 'FREE_PERIOD_DAYS' ? 'IMMEDIATE' : 'NEXT_BILLING');
    setApplyNote('');
  };

  const handleApplyCouponToUser = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!applyCouponTarget) return;
    const trimmedEmail = applyCustomEmail.trim();
    if (!trimmedEmail && !applyUserId) {
      setFeedback({ text: 'Selecione um usuário ou informe o e-mail cadastrado.', isError: true });
      return;
    }
    setApplyingCoupon(true);
    setFeedback(null);
    try {
      const useImmediateGrant = applyCouponTarget.couponType === 'FREE_PERIOD_DAYS' && applyFreeMode === 'IMMEDIATE';
      if (useImmediateGrant) {
        const resp = await fetch('/api/admin/billing/subscriptions/grant', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            userId: trimmedEmail ? null : applyUserId,
            userEmail: trimmedEmail || null,
            planId: applyCouponTarget.applicablePlanId || 'PRO_MONTHLY',
            periodDays: Math.max(1, applyCouponTarget.freePeriodDays || 7),
            isTrial: true,
            customPriceCents: 0,
            paymentMethod: 'COURTESY',
            couponCode: applyCouponTarget.code,
            autoRenew: false,
            note: applyNote.trim() || `Cupom ${applyCouponTarget.code} aplicado diretamente pelo admin`
          })
        });
        const resData = await resp.json().catch(() => ({}));
        if (!resp.ok) {
          setFeedback({ text: resData.error || 'Não foi possível conceder o período do cupom.', isError: true });
          return;
        }
        setApplyCouponTarget(null);
        setFeedback({
          text: `Cupom ${applyCouponTarget.code} aplicado com sucesso em ${resData.userEmail || 'usuário selecionado'}.`,
          isError: false
        });
        onRefresh();
      } else {
        const resp = await fetch('/api/admin/billing/subscriptions/next-discount', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            userId: trimmedEmail ? null : applyUserId,
            userEmail: trimmedEmail || null,
            couponCode: applyCouponTarget.code,
            discountType: 'COUPON',
            planId: applyCouponTarget.applicablePlanId || undefined,
            note: applyNote.trim() || null
          })
        });
        const resData = await resp.json().catch(() => ({}));
        if (!resp.ok) {
          setFeedback({ text: resData.error || 'Não foi possível agendar o desconto na próxima cobrança.', isError: true });
          return;
        }
        setApplyCouponTarget(null);
        setFeedback({
          text: `${resData.nextBillingDiscountLabel || `Cupom ${applyCouponTarget.code} aplicado`} para ${resData.userEmail}.`,
          isError: false
        });
        onRefresh();
      }
    } finally {
      setApplyingCoupon(false);
    }
  };

  const handleToggle = async (c: CouponItem) => {
    setFeedback(null);
    const resp = await fetch(`/api/admin/billing/coupons/${c.id}/toggle`, {
      method: 'POST',
      credentials: 'include'
    });
    if (resp.ok) {
      onRefresh();
    } else {
      const errData = await resp.json().catch(() => ({}));
      setFeedback({ text: errData.error || 'Não foi possível alterar o status deste cupom.', isError: true });
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
    if (c.couponType === 'PERCENT_DISCOUNT') return `${c.discountPercent || 0}% de desconto`;
    return `${formatBRLFromCents(c.discountCents)} de desconto`;
  };

  return (
    <div className="view-stack">
      <div className="title-row">
        <div>
          <h1 className="page-title">Cupons e períodos grátis</h1>
          <p className="page-subtitle">
            Gerencie códigos promocionais com limite de uso, validade e aplicação direta em assinantes.
          </p>
        </div>

        <div className="actions-row">
          <button type="button" className="btn btn-secondary" onClick={() => openPreset('FREE_7')}>
            + Voucher 7d (1 uso)
          </button>
          <button type="button" className="btn btn-secondary" onClick={() => openPreset('FREE_30')}>
            + Voucher 30d (1 uso)
          </button>
          <button type="button" className="btn btn-secondary" onClick={() => openPreset('OFF_50')}>
            + Voucher 50% (1 uso)
          </button>
          <button type="button" className="btn btn-primary" onClick={() => openPreset('CUSTOM')}>
            <Plus size={14} />
            <span>Novo cupom</span>
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

      <section className="kpis" aria-label="Resumo de cupons">
        <div className="kpi">
          <div className="kpi-label">Cupons ativos</div>
          <div className="kpi-val is-pos">{metrics.active}</div>
          <div className="kpi-sub">De {data.coupons.length} configurado(s)</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Saldo disponível</div>
          <div className="kpi-val">{metrics.remainingTotal}</div>
          <div className="kpi-sub">Resgates restantes em cupons ativos</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Esgotados / expirados</div>
          <div className="kpi-val">{metrics.closedCount}</div>
          <div className="kpi-sub">Bloqueados automaticamente</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Total de resgates</div>
          <div className="kpi-val">{metrics.redemptions}</div>
          <div className="kpi-sub">Auditados com limite finito</div>
        </div>
      </section>

      <section className="panel" aria-label="Tabela de cupons">
        <div className="panel-head">
          <div>
            <h2 className="panel-title">Cupons cadastrados ({data.coupons.length})</h2>
            <p className="panel-subtitle">Todos os cupons possuem cota finita obrigatória e são desativados automaticamente ao atingir o limite de resgates.</p>
          </div>
        </div>

        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Benefício</th>
                <th>Elegibilidade e prazo</th>
                <th className="num">Cota de usos</th>
                <th>Status</th>
                <th style={{ textAlign: 'right' }}>Ações</th>
              </tr>
            </thead>
            <tbody>
              {data.coupons.length === 0 ? (
                <tr>
                  <td colSpan={6} className="table-empty">
                    Nenhum cupom cadastrado. Crie um cupom com limite de uso acima.
                  </td>
                </tr>
              ) : (
                data.coupons.map((c) => {
                  const maxQuota = Math.max(1, c.maxRedemptions || 1);
                  const remaining = c.remainingRedemptions != null ? c.remainingRedemptions : Math.max(0, maxQuota - (c.currentRedemptions || 0));
                  const isExhausted = Boolean(c.exhausted) || remaining <= 0;
                  const isExpired = Boolean(c.expired);
                  const isEligibleActive = c.active && !isExhausted && !isExpired;
                  return (
                  <tr key={c.id}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span className="code-chip">{c.code}</span>
                        <button
                          type="button"
                          className="btn-icon"
                          title="Copiar código"
                          onClick={() => {
                            navigator.clipboard?.writeText(c.code);
                            setFeedback({ text: `Código ${c.code} copiado.`, isError: false });
                          }}
                        >
                          <Copy size={12} />
                        </button>
                      </div>
                      {c.description && (
                        <div style={{ fontSize: '12px', color: 'var(--faint)', marginTop: '3px' }}>{c.description}</div>
                      )}
                    </td>
                    <td>
                      <span className={`pill ${c.couponType === 'FREE_PERIOD_DAYS' ? 'pill-pos' : 'pill-neutral'}`}>
                        {describeBenefit(c)}
                      </span>
                    </td>
                    <td style={{ fontSize: '12.5px', color: 'var(--muted)' }}>
                      <div>{c.applicablePlanId ? `Plano ${c.applicablePlanId}` : 'Todos os planos'} · {c.perUserLimit}/usuário</div>
                      {c.restrictedToEmail && (
                        <div style={{ fontSize: '11.5px', color: 'var(--text)', fontWeight: 500, marginTop: '2px' }}>
                          Exclusivo: {c.restrictedToEmail}
                        </div>
                      )}
                      {c.firstTimeOnly && (
                        <div style={{ fontSize: '11px', color: 'var(--warn-ink)', marginTop: '2px' }}>
                          Somente 1ª assinatura paga
                        </div>
                      )}
                      <div style={{ fontSize: '11.5px', color: 'var(--faint)', marginTop: '2px' }}>
                        Validade: {c.expiresAtUtc ? formatDateTimeBR(c.expiresAtUtc) : 'Sem expiração programada'}
                      </div>
                    </td>
                    <td className="num" style={{ fontWeight: 600 }}>
                      <div>{c.currentRedemptions} / {maxQuota}</div>
                      <div style={{ fontSize: '11px', color: remaining === 0 ? 'var(--neg-ink)' : 'var(--faint)', fontWeight: 500 }}>
                        {remaining === 0 ? '0 restantes' : `Restam ${remaining}`}
                      </div>
                    </td>
                    <td>
                      {isExhausted ? (
                        <span className="pill pill-warn">Esgotado</span>
                      ) : isExpired ? (
                        <span className="pill pill-neg">Expirado</span>
                      ) : c.active ? (
                        <span className="pill pill-pos">Ativo</span>
                      ) : (
                        <span className="pill pill-neutral">Pausado</span>
                      )}
                    </td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <div className="actions-row" style={{ justifyContent: 'flex-end' }}>
                        {isEligibleActive && (
                          <button
                            type="button"
                            className="btn btn-primary btn-sm"
                            onClick={() => openApplyModal(c)}
                            title="Aplicar este cupom a um usuário ou na próxima cobrança"
                          >
                            <Send size={12} />
                            <span>Aplicar a usuário</span>
                          </button>
                        )}
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          onClick={() => openEditCoupon(c)}
                          title="Editar limite de usos, validade ou restrições"
                        >
                          <Pencil size={12} />
                          <span>Editar cota</span>
                        </button>
                        {!isExhausted && !isExpired && (
                          <button type="button" className="btn btn-secondary btn-sm" onClick={() => handleToggle(c)}>
                            {c.active ? 'Pausar' : 'Ativar'}
                          </button>
                        )}
                        <button
                          type="button"
                          className="btn btn-ghost btn-sm"
                          style={{ color: 'var(--neg-ink)' }}
                          onClick={() => handleDelete(c)}
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

      <section className="panel" aria-label="Resgates recentes">
        <div className="panel-head">
          <div>
            <h2 className="panel-title">Resgates realizados ({data.recentRedemptions.length})</h2>
            <p className="panel-subtitle">Histórico auditado de aplicações feitas pelos assinantes.</p>
          </div>
        </div>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Data do resgate</th>
                <th>Cupom</th>
                <th>Usuário</th>
                <th>Benefício concedido</th>
              </tr>
            </thead>
            <tbody>
              {data.recentRedemptions.length === 0 ? (
                <tr>
                  <td colSpan={4} className="table-empty">Nenhum cupom resgatado pelos usuários ainda.</td>
                </tr>
              ) : (
                data.recentRedemptions.map((r) => (
                  <tr key={r.id}>
                    <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)' }}>
                      {formatDateTimeBR(r.redeemedAtUtc)}
                    </td>
                    <td><span className="code-chip">{r.couponCode}</span></td>
                    <td>{r.userEmail || r.userId}</td>
                    <td style={{ color: 'var(--muted)' }}>{r.benefitSummary}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Modal Criar / Editar Cupom */}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <form className="modal-dialog" onSubmit={handleSaveCoupon} onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <h2 className="modal-title">
                  {editingCoupon ? `Editar cota · ${editingCoupon.code}` : 'Novo cupom promocional'}
                </h2>
                <p className="modal-sub">
                  Defina o benefício, o limite obrigatório de resgates e as regras de elegibilidade.
                </p>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowModal(false)}>
                Fechar
              </button>
            </div>

            <div className="form-grid-2">
              <div className="field">
                <label className="field-label">Código do cupom</label>
                <div style={{ display: 'flex', gap: '6px' }}>
                  <input
                    className="input mono"
                    required
                    disabled={Boolean(editingCoupon)}
                    value={code}
                    onChange={(e) => setCode(e.target.value.toUpperCase())}
                    placeholder="FRM-VIP-K8X4M9P2"
                  />
                  {!editingCoupon && (
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      title="Gerar código aleatório"
                      onClick={() => setCode(generateSecurePromoCode('VIP'))}
                    >
                      <RefreshCw size={12} />
                    </button>
                  )}
                </div>
              </div>
              <div className="field">
                <label className="field-label">Tipo de benefício</label>
                <select className="select" value={couponType} onChange={(e) => setCouponType(e.target.value as any)}>
                  <option value="FREE_PERIOD_DAYS">Período grátis (dias)</option>
                  <option value="PERCENT_DISCOUNT">Desconto percentual (%)</option>
                  <option value="FIXED_CENTS_DISCOUNT">Desconto fixo (BRL)</option>
                </select>
              </div>
            </div>

            <div className="field">
              <label className="field-label">Descrição interna / contexto</label>
              <input
                className="input"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Ex: Cortesia para cliente VIP ou compensação"
              />
            </div>

            <div className="form-grid-2">
              {couponType === 'FREE_PERIOD_DAYS' && (
                <div className="field">
                  <label className="field-label">Quantidade de dias grátis</label>
                  <input className="input mono" type="number" min={1} max={365} value={freeDays} onChange={(e) => setFreeDays(e.target.value)} />
                </div>
              )}
              {couponType === 'PERCENT_DISCOUNT' && (
                <div className="field">
                  <label className="field-label">Percentual de desconto (%)</label>
                  <input className="input mono" type="number" min={1} max={100} value={discountPercent} onChange={(e) => setDiscountPercent(e.target.value)} />
                </div>
              )}
              {couponType === 'FIXED_CENTS_DISCOUNT' && (
                <div className="field">
                  <label className="field-label">Desconto fixo (BRL)</label>
                  <input className="input mono" value={discountBRL} onChange={(e) => setDiscountBRL(e.target.value)} />
                </div>
              )}

              <div className="field">
                <label className="field-label">Plano elegível</label>
                <select className="select" value={applicablePlanId} onChange={(e) => setApplicablePlanId(e.target.value)}>
                  <option value="">Qualquer plano</option>
                  {data.plans.map((p) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="form-grid-3">
              <div className="field">
                <label className="field-label">Limite total de usos (obrigatório)</label>
                <input
                  className="input mono"
                  type="number"
                  required
                  min={editingCoupon ? Math.max(1, editingCoupon.currentRedemptions || 0) : 1}
                  max={10000}
                  value={maxRedemptions}
                  onChange={(e) => setMaxRedemptions(e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label">Máx. por usuário</label>
                <input
                  className="input mono"
                  type="number"
                  required
                  min={1}
                  max={Math.max(1, parseInt(maxRedemptions, 10) || 1)}
                  value={perUserLimit}
                  onChange={(e) => setPerUserLimit(e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label">Expira em</label>
                <input className="input" type="date" value={expiresDate} onChange={(e) => setExpiresDate(e.target.value)} />
              </div>
            </div>

            <div className="actions-row" style={{ gap: '6px', marginBottom: '12px' }}>
              {[1, 5, 10, 25, 100].map((presetQty) => (
                <button
                  key={presetQty}
                  type="button"
                  className={`btn btn-sm ${parseInt(maxRedemptions, 10) === presetQty ? 'btn-primary' : 'btn-secondary'}`}
                  disabled={Boolean(editingCoupon && presetQty < (editingCoupon.currentRedemptions || 0))}
                  onClick={() => setMaxRedemptions(String(presetQty))}
                >
                  {presetQty === 1 ? '1 uso' : `${presetQty} usos`}
                </button>
              ))}
            </div>

            <div className="form-grid-2">
              <div className="field">
                <label className="field-label">E-mail exclusivo (opcional)</label>
                <input
                  className="input"
                  type="email"
                  value={restrictedToEmail}
                  onChange={(e) => setRestrictedToEmail(e.target.value)}
                  placeholder="cliente@dominio.com (vazio = qualquer portador)"
                />
              </div>
              <div className="field" style={{ justifyContent: 'flex-end' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12.5px', color: 'var(--text)', cursor: 'pointer', padding: '10px 0' }}>
                  <input
                    type="checkbox"
                    checked={firstTimeOnly}
                    onChange={(e) => setFirstTimeOnly(e.target.checked)}
                  />
                  <span>Válido apenas na 1ª assinatura paga</span>
                </label>
              </div>
            </div>

            {editingCoupon && (editingCoupon.exhausted || !editingCoupon.active) && (
              <div style={{ marginTop: '6px', fontSize: '12.5px', color: 'var(--muted)' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={reactivateOnSave}
                    onChange={(e) => setReactivateOnSave(e.target.checked)}
                  />
                  <span>Reativar cupom automaticamente se o novo limite superar os resgates atuais ({editingCoupon.currentRedemptions})</span>
                </label>
              </div>
            )}

            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Salvando...' : (editingCoupon ? 'Salvar alterações' : 'Criar cupom')}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Modal Aplicar Cupom a Usuário */}
      {applyCouponTarget && (
        <div className="modal-overlay" onClick={() => setApplyCouponTarget(null)}>
          <form className="modal-dialog" onSubmit={handleApplyCouponToUser} onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <h2 className="modal-title">Aplicar cupom <span className="code-chip">{applyCouponTarget.code}</span></h2>
                <p className="modal-sub">{describeBenefit(applyCouponTarget)} · Concessão direta na conta ou na próxima cobrança do assinante.</p>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setApplyCouponTarget(null)}>
                Fechar
              </button>
            </div>

            {users.length > 0 && (
              <div className="field">
                <label className="field-label">Selecionar usuário</label>
                <select className="select" value={applyUserId} onChange={(e) => setApplyUserId(e.target.value)}>
                  {users.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.name} ({u.email})
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="field">
              <label className="field-label">Ou buscar por e-mail exato (opcional)</label>
              <input
                className="input"
                type="email"
                value={applyCustomEmail}
                onChange={(e) => setApplyCustomEmail(e.target.value)}
                placeholder="usuario@exemplo.com"
              />
            </div>

            {applyCouponTarget.couponType === 'FREE_PERIOD_DAYS' && (
              <div className="field">
                <label className="field-label">Modo de aplicação</label>
                <div className="segmented" role="group">
                  <button
                    type="button"
                    className={`seg-btn ${applyFreeMode === 'IMMEDIATE' ? 'is-active' : ''}`}
                    onClick={() => setApplyFreeMode('IMMEDIATE')}
                  >
                    Liberar {applyCouponTarget.freePeriodDays || 7}d agora
                  </button>
                  <button
                    type="button"
                    className={`seg-btn ${applyFreeMode === 'NEXT_BILLING' ? 'is-active' : ''}`}
                    onClick={() => setApplyFreeMode('NEXT_BILLING')}
                  >
                    Zerar próxima cobrança
                  </button>
                </div>
              </div>
            )}

            <div className="field">
              <label className="field-label">Observação interna (opcional)</label>
              <input
                className="input"
                value={applyNote}
                onChange={(e) => setApplyNote(e.target.value)}
                placeholder="Ex: Desconto comercial ou retenção"
              />
            </div>

            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={() => setApplyCouponTarget(null)}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-primary" disabled={applyingCoupon}>
                {applyingCoupon ? 'Aplicando...' : 'Aplicar agora'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
