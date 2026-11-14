export function formatBRLFromCents(cents: number | null | undefined): string {
  const safe = typeof cents === 'number' && !Number.isNaN(cents) ? cents : 0;
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(safe / 100);
}

export function formatTimestampMsBR(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const base = d.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
  const ms = d.getMilliseconds().toString().padStart(3, '0');
  return `${base.replace(',', '')}.${ms}`;
}

export function formatDateTimeBR(iso?: string | null): string {
  return formatTimestampMsBR(iso);
}

export function formatShortDateBR(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit'
  });
}

export type PillTone = 'pos' | 'warn' | 'neg' | 'neutral';

export function subscriptionStatusMeta(status: string): { label: string; tone: PillTone } {
  const upper = (status || '').toUpperCase();
  switch (upper) {
    case 'ACTIVE':
      return { label: 'Ativa', tone: 'pos' };
    case 'TRIALING':
      return { label: 'Período grátis', tone: 'pos' };
    case 'PAST_DUE':
      return { label: 'Em atraso', tone: 'warn' };
    case 'PAUSED':
      return { label: 'Pausada', tone: 'warn' };
    case 'CANCELED':
      return { label: 'Cancelada', tone: 'neg' };
    case 'EXPIRED':
      return { label: 'Expirada', tone: 'neutral' };
    default:
      return { label: status || 'Free', tone: 'neutral' };
  }
}

export function entryStatusMeta(status: string): { label: string; tone: PillTone } {
  const upper = (status || '').toUpperCase();
  switch (upper) {
    case 'PAID':
      return { label: 'Pago', tone: 'pos' };
    case 'PENDING':
      return { label: 'Pendente', tone: 'warn' };
    case 'OVERDUE':
      return { label: 'Vencida', tone: 'warn' };
    case 'REFUNDED':
      return { label: 'Estornada', tone: 'neutral' };
    case 'CANCELED':
      return { label: 'Cancelada', tone: 'neg' };
    default:
      return { label: upper || '—', tone: 'neutral' };
  }
}

export function couponTypeLabel(couponType: string): string {
  switch ((couponType || '').toUpperCase()) {
    case 'FREE_PERIOD_DAYS':
      return 'Período grátis';
    case 'PERCENT_DISCOUNT':
      return 'Desconto percentual';
    case 'FIXED_CENTS_DISCOUNT':
      return 'Desconto fixo';
    default:
      return couponType;
  }
}

export function subscriptionSourceLabel(source: string): string {
  switch ((source || '').toUpperCase()) {
    case 'APPLE_STOREKIT':
      return 'App Store';
    case 'COUPON_FREE_PERIOD':
      return 'Cupom promocional';
    case 'EXTERNAL_GATEWAY':
      return 'Gateway externo';
    case 'ADMIN_MANUAL':
      return 'Ativação manual';
    case 'NONE':
      return 'Sem assinatura';
    default:
      return source || '—';
  }
}

export function paymentMethodLabel(method: string): string {
  switch ((method || '').toUpperCase()) {
    case 'APPLE_IAP':
      return 'App Store';
    case 'PIX':
      return 'Pix';
    case 'CARD':
      return 'Cartão';
    case 'COUPON':
      return 'Cupom';
    case 'COURTESY':
      return 'Cortesia';
    default:
      return method || '—';
  }
}
