export function formatBRLFromCents(cents: number | null | undefined): string {
  const safe = typeof cents === 'number' && !Number.isNaN(cents) ? cents : 0;
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(safe / 100);
}

export function formatDateTimeBR(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
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

export function subscriptionStatusMeta(status: string): { label: string; color: string; bg: string } {
  const upper = (status || '').toUpperCase();
  switch (upper) {
    case 'ACTIVE':
      return { label: 'Ativa', color: '#34d399', bg: 'rgba(16, 185, 129, 0.14)' };
    case 'TRIALING':
      return { label: 'Período Grátis', color: '#38bdf8', bg: 'rgba(56, 189, 248, 0.14)' };
    case 'PAST_DUE':
      return { label: 'Em Atraso', color: '#fbbf24', bg: 'rgba(245, 158, 11, 0.16)' };
    case 'PAUSED':
      return { label: 'Pausada', color: '#fb923c', bg: 'rgba(249, 115, 22, 0.15)' };
    case 'CANCELED':
      return { label: 'Cancelada', color: '#f87171', bg: 'rgba(239, 68, 68, 0.14)' };
    case 'EXPIRED':
      return { label: 'Expirada', color: '#94a3b8', bg: 'rgba(148, 163, 184, 0.14)' };
    default:
      return { label: status || 'Free', color: '#94a3b8', bg: 'rgba(148, 163, 184, 0.12)' };
  }
}

export function entryStatusMeta(status: string): { label: string; color: string; bg: string } {
  const upper = (status || '').toUpperCase();
  switch (upper) {
    case 'PAID':
      return { label: 'Pago', color: '#34d399', bg: 'rgba(16, 185, 129, 0.14)' };
    case 'PENDING':
      return { label: 'Pendente', color: '#38bdf8', bg: 'rgba(56, 189, 248, 0.14)' };
    case 'OVERDUE':
      return { label: 'Vencida', color: '#fbbf24', bg: 'rgba(245, 158, 11, 0.16)' };
    case 'REFUNDED':
      return { label: 'Estornada', color: '#c084fc', bg: 'rgba(139, 92, 246, 0.16)' };
    case 'CANCELED':
      return { label: 'Cancelada', color: '#f87171', bg: 'rgba(239, 68, 68, 0.14)' };
    default:
      return { label: upper || '—', color: '#94a3b8', bg: 'rgba(148, 163, 184, 0.12)' };
  }
}
