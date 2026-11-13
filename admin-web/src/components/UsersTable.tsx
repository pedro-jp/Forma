import React, { useState, useMemo } from 'react';
import {
  Users,
  Mail,
  Apple,
  Globe,
  Search,
  UserPlus,
  RefreshCw,
  PauseCircle,
  PlayCircle,
  RotateCcw,
  Trash2,
  Sliders,
  ShieldAlert,
  Sparkles,
  Check,
  AlertCircle
} from 'lucide-react';
import { UserSummary } from '../types';
import {
  CreateUserModal,
  PauseUserModal,
  ResetUserModal,
  DeleteUserModal,
  UserDetailModal
} from './UserModals';

interface UsersTableProps {
  users: UserSummary[];
  onRefresh: () => void;
}

type FilterMode = 'ALL' | 'PRO' | 'FREE' | 'ACTIVE' | 'PAUSED' | 'AI_RESTRICTED' | 'GOOGLE' | 'APPLE' | 'EMAIL';

const isProTier = (tier?: string | null) => (tier || 'FREE').toUpperCase() === 'PRO';

export const UsersTable: React.FC<UsersTableProps> = ({ users, onRefresh }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [filterMode, setFilterMode] = useState<FilterMode>('ALL');
  const [banner, setBanner] = useState<{ text: string; isError?: boolean } | null>(null);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);

  // Active modals
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [pauseTarget, setPauseTarget] = useState<UserSummary | null>(null);
  const [resetTarget, setResetTarget] = useState<UserSummary | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<UserSummary | null>(null);
  const [detailTarget, setDetailTarget] = useState<UserSummary | null>(null);

  const showToast = (text: string, isError = false) => {
    setBanner({ text, isError });
    onRefresh();
    setTimeout(() => {
      setBanner((prev) => (prev?.text === text ? null : prev));
    }, 5500);
  };

  // Contadores rápidos de governança
  const metrics = useMemo(() => {
    const total = users.length;
    const paused = users.filter((u) => u.status === 'PAUSED').length;
    const active = total - paused;
    const proCount = users.filter((u) => isProTier(u.accountTier)).length;
    const freeCount = total - proCount;
    const aiRestricted = users.filter((u) => u.aiBlocked || (u.dailyAiLimit && u.dailyAiLimit > 0)).length;
    return { total, active, paused, proCount, freeCount, aiRestricted };
  }, [users]);

  const filteredUsers = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return users.filter((u) => {
      if (filterMode === 'PRO' && !isProTier(u.accountTier)) return false;
      if (filterMode === 'FREE' && isProTier(u.accountTier)) return false;
      if (filterMode === 'ACTIVE' && u.status === 'PAUSED') return false;
      if (filterMode === 'PAUSED' && u.status !== 'PAUSED') return false;
      if (filterMode === 'AI_RESTRICTED' && !u.aiBlocked && !(u.dailyAiLimit && u.dailyAiLimit > 0)) return false;
      if (filterMode === 'GOOGLE' && u.authProvider !== 'GOOGLE') return false;
      if (filterMode === 'APPLE' && u.authProvider !== 'APPLE') return false;
      if (filterMode === 'EMAIL' && u.authProvider !== 'EMAIL') return false;

      if (!q) return true;
      return (
        (u.name || '').toLowerCase().includes(q) ||
        (u.email || '').toLowerCase().includes(q) ||
        (u.id || '').toLowerCase().includes(q)
      );
    });
  }, [users, searchQuery, filterMode]);

  const handleQuickResume = async (user: UserSummary) => {
    setBusyUserId(user.id);
    try {
      const resp = await fetch(`/api/admin/users/${user.id}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ paused: false, reason: null })
      });
      if (resp.ok) {
        showToast(`Conta de ${user.name || user.email} reativada com sucesso!`);
      } else {
        showToast('Não foi possível reativar a conta.', true);
      }
    } catch {
      showToast('Erro de conexão ao reativar conta.', true);
    } finally {
      setBusyUserId(null);
    }
  };

  const handleQuickToggleAi = async (user: UserSummary) => {
    setBusyUserId(user.id);
    const nextBlocked = !user.aiBlocked;
    try {
      const resp = await fetch(`/api/admin/users/${user.id}/ai-access`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ aiBlocked: nextBlocked })
      });
      if (resp.ok) {
        showToast(
          nextBlocked
            ? `Acesso à IA bloqueado para ${user.name || user.email}.`
            : `Acesso à IA liberado para ${user.name || user.email}!`
        );
      } else {
        showToast('Falha ao alternar acesso de IA do usuário.', true);
      }
    } catch {
      showToast('Erro de conexão ao atualizar acesso de IA.', true);
    } finally {
      setBusyUserId(null);
    }
  };

  const handleQuickToggleTier = async (user: UserSummary) => {
    setBusyUserId(user.id);
    const nextTier = isProTier(user.accountTier) ? 'FREE' : 'PRO';
    try {
      const resp = await fetch(`/api/admin/users/${user.id}/tier`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ accountTier: nextTier })
      });
      if (resp.ok) {
        showToast(
          nextTier === 'PRO'
            ? `Conta de ${user.name || user.email} alterada para Forma PRO (IA completa liberada)!`
            : `Conta de ${user.name || user.email} alterada para FREE (camada visual essencial).`
        );
      } else {
        showToast('Falha ao alterar a camada da conta.', true);
      }
    } catch {
      showToast('Erro de conexão ao alterar camada da conta.', true);
    } finally {
      setBusyUserId(null);
    }
  };

  const getTierBadge = (tier?: string | null) => {
    if (isProTier(tier)) {
      return (
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: '4px',
          padding: '2px 9px', borderRadius: '999px', fontSize: '11px', fontWeight: 800,
          background: 'linear-gradient(135deg, rgba(139, 92, 246, 0.28), rgba(16, 185, 129, 0.24))',
          color: '#ddd6fe',
          border: '1px solid rgba(167, 139, 250, 0.45)'
        }}>
          <Sparkles size={11} /> PRO
        </span>
      );
    }
    return (
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: '4px',
        padding: '2px 9px', borderRadius: '999px', fontSize: '11px', fontWeight: 700,
        backgroundColor: 'rgba(148, 163, 184, 0.14)',
        color: '#cbd5e1',
        border: '1px solid rgba(148, 163, 184, 0.28)'
      }}>
        FREE • Essencial
      </span>
    );
  };

  const getProviderBadge = (provider: string) => {
    switch (provider) {
      case 'APPLE':
        return (
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: '5px',
            padding: '2px 9px', borderRadius: '999px', fontSize: '11px', fontWeight: 700,
            backgroundColor: 'rgba(255, 255, 255, 0.1)', color: '#fff',
            border: '1px solid rgba(255, 255, 255, 0.2)'
          }}>
            <Apple size={11} /> Apple
          </span>
        );
      case 'GOOGLE':
        return (
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: '5px',
            padding: '2px 9px', borderRadius: '999px', fontSize: '11px', fontWeight: 700,
            backgroundColor: 'rgba(59, 130, 246, 0.15)', color: '#60a5fa',
            border: '1px solid rgba(59, 130, 246, 0.3)'
          }}>
            <Globe size={11} /> Google
          </span>
        );
      default:
        return (
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: '5px',
            padding: '2px 9px', borderRadius: '999px', fontSize: '11px', fontWeight: 700,
            backgroundColor: 'rgba(16, 185, 129, 0.15)', color: '#34d399',
            border: '1px solid rgba(16, 185, 129, 0.3)'
          }}>
            <Mail size={11} /> E-mail
          </span>
        );
    }
  };

  const actionBtnStyle = (color: string, bg: string, border: string): React.CSSProperties => ({
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '5px',
    padding: '6px 10px',
    borderRadius: '8px',
    fontSize: '11px',
    fontWeight: 700,
    color,
    backgroundColor: bg,
    border: `1px solid ${border}`,
    whiteSpace: 'nowrap'
  });

  const formatShortDate = (iso?: string | null, fallback = '--') => {
    if (!iso) return fallback;
    try {
      return new Date(iso).toLocaleString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return fallback;
    }
  };

  return (
    <div className="glass-card" style={{ padding: '24px' }}>
      {/* Header */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '14px',
        marginBottom: '18px',
        paddingBottom: '16px',
        borderBottom: '1px solid var(--border-color)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '44px',
            height: '44px',
            borderRadius: '12px',
            backgroundColor: 'rgba(59, 130, 246, 0.15)',
            color: '#60a5fa',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <Users size={22} />
          </div>
          <div>
            <h3 style={{ fontSize: '18px', fontWeight: 800 }}>Central de Controle de Usuários &amp; Camadas</h3>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
              Defina a camada de cada conta (Free Essencial ou Forma Pro com IA), pause acessos, ajuste cotas ou gerencie dados sincronizados.
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
          <button
            onClick={() => setShowCreateModal(true)}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '7px',
              backgroundColor: '#10b981',
              border: 'none',
              color: '#042f1a',
              padding: '9px 15px',
              borderRadius: '10px',
              fontSize: '12px',
              fontWeight: 800,
              boxShadow: '0 4px 14px rgba(16, 185, 129, 0.3)'
            }}
          >
            <UserPlus size={15} />
            <span>Novo Usuário</span>
          </button>

          <button
            onClick={onRefresh}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              background: 'rgba(255, 255, 255, 0.04)',
              border: '1px solid var(--border-color)',
              color: 'var(--text-secondary)',
              padding: '9px 14px',
              borderRadius: '10px',
              fontSize: '12px',
              fontWeight: 600
            }}
          >
            <RefreshCw size={14} />
            <span>Atualizar</span>
          </button>
        </div>
      </div>

      {/* Feedback Banner */}
      {banner && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          padding: '12px 16px',
          borderRadius: '12px',
          marginBottom: '18px',
          fontSize: '13px',
          fontWeight: 600,
          backgroundColor: banner.isError ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
          border: `1px solid ${banner.isError ? 'rgba(239, 68, 68, 0.35)' : 'rgba(16, 185, 129, 0.35)'}`,
          color: banner.isError ? '#f87171' : '#34d399'
        }}>
          {banner.isError ? <AlertCircle size={17} /> : <Check size={17} />}
          <span>{banner.text}</span>
        </div>
      )}

      {/* Mini Summary Strip */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
        gap: '12px',
        marginBottom: '18px'
      }}>
        <div style={{
          padding: '12px 16px', borderRadius: '12px',
          backgroundColor: 'rgba(15, 23, 42, 0.65)', border: '1px solid var(--border-color)'
        }}>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>TOTAL DE CONTAS</div>
          <div style={{ fontSize: '22px', fontWeight: 800, marginTop: '2px' }}>{metrics.total}</div>
        </div>
        <div style={{
          padding: '12px 16px', borderRadius: '12px',
          background: 'linear-gradient(135deg, rgba(139, 92, 246, 0.16), rgba(16, 185, 129, 0.1))',
          border: '1px solid rgba(167, 139, 250, 0.32)'
        }}>
          <div style={{ fontSize: '11px', color: '#ddd6fe', fontWeight: 800 }}>✦ CAMADA PRO (IA)</div>
          <div style={{ fontSize: '22px', fontWeight: 800, color: '#f5f3ff', marginTop: '2px' }}>{metrics.proCount}</div>
        </div>
        <div style={{
          padding: '12px 16px', borderRadius: '12px',
          backgroundColor: 'rgba(148, 163, 184, 0.08)', border: '1px solid rgba(148, 163, 184, 0.24)'
        }}>
          <div style={{ fontSize: '11px', color: '#cbd5e1', fontWeight: 700 }}>🌿 CAMADA FREE</div>
          <div style={{ fontSize: '22px', fontWeight: 800, color: '#f1f5f9', marginTop: '2px' }}>{metrics.freeCount}</div>
        </div>
        <div style={{
          padding: '12px 16px', borderRadius: '12px',
          backgroundColor: 'rgba(16, 185, 129, 0.08)', border: '1px solid rgba(16, 185, 129, 0.22)'
        }}>
          <div style={{ fontSize: '11px', color: '#34d399', fontWeight: 700 }}>🟢 CONTAS ATIVAS</div>
          <div style={{ fontSize: '22px', fontWeight: 800, color: '#ecfdf5', marginTop: '2px' }}>{metrics.active}</div>
        </div>
        <div style={{
          padding: '12px 16px', borderRadius: '12px',
          backgroundColor: 'rgba(245, 158, 11, 0.08)', border: '1px solid rgba(245, 158, 11, 0.22)'
        }}>
          <div style={{ fontSize: '11px', color: '#fbbf24', fontWeight: 700 }}>⏸️ CONTAS PAUSADAS</div>
          <div style={{ fontSize: '22px', fontWeight: 800, color: '#fef3c7', marginTop: '2px' }}>{metrics.paused}</div>
        </div>
      </div>

      {/* Search & Filter Toolbar */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '12px',
        marginBottom: '18px'
      }}>
        <div style={{ position: 'relative', flex: '1 1 280px', maxWidth: '420px' }}>
          <Search
            size={15}
            style={{
              position: 'absolute',
              left: '12px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: 'var(--text-muted)'
            }}
          />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Buscar por nome, e-mail ou ID..."
            style={{
              width: '100%',
              padding: '9px 12px 9px 36px',
              borderRadius: '10px',
              backgroundColor: 'rgba(15, 23, 42, 0.85)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              color: '#fff',
              fontSize: '13px'
            }}
          />
        </div>

        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
          {[
            { id: 'ALL', label: 'Todos' },
            { id: 'PRO', label: '✦ Forma Pro' },
            { id: 'FREE', label: 'Free (Essencial)' },
            { id: 'ACTIVE', label: 'Ativos' },
            { id: 'PAUSED', label: 'Pausados' },
            { id: 'AI_RESTRICTED', label: 'IA Restrita' },
            { id: 'GOOGLE', label: 'Google' },
            { id: 'APPLE', label: 'Apple' },
            { id: 'EMAIL', label: 'E-mail' }
          ].map((pill) => {
            const active = filterMode === pill.id;
            return (
              <button
                key={pill.id}
                type="button"
                onClick={() => setFilterMode(pill.id as FilterMode)}
                style={{
                  padding: '6px 12px',
                  borderRadius: '999px',
                  fontSize: '11px',
                  fontWeight: 700,
                  backgroundColor: active ? 'rgba(59, 130, 246, 0.22)' : 'rgba(255, 255, 255, 0.05)',
                  color: active ? '#60a5fa' : 'var(--text-secondary)',
                  border: `1px solid ${active ? 'rgba(59, 130, 246, 0.4)' : 'rgba(255, 255, 255, 0.08)'}`
                }}
              >
                {pill.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Rich Users Table */}
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', textAlign: 'left' }}>
          <thead>
            <tr style={{ color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)', fontSize: '12px' }}>
              <th style={{ padding: '12px 12px', fontWeight: 700 }}>Usuário / Conta</th>
              <th style={{ padding: '12px 12px', fontWeight: 700 }}>Camada, Status &amp; IA</th>
              <th style={{ padding: '12px 12px', fontWeight: 700 }}>Dados no Supabase</th>
              <th style={{ padding: '12px 12px', fontWeight: 700 }}>Atividade</th>
              <th style={{ padding: '12px 12px', fontWeight: 700, textAlign: 'right' }}>Controles Administrativos</th>
            </tr>
          </thead>
          <tbody>
            {filteredUsers.length === 0 ? (
              <tr>
                <td colSpan={5} style={{ padding: '32px', textAlign: 'center', color: 'var(--text-muted)' }}>
                  Nenhuma conta encontrada para os filtros atuais.
                </td>
              </tr>
            ) : (
              filteredUsers.map((u) => {
                const isPaused = u.status === 'PAUSED';
                const isPro = isProTier(u.accountTier);
                const isBusy = busyUserId === u.id;

                return (
                  <tr
                    key={u.id}
                    style={{
                      borderBottom: '1px solid rgba(255, 255, 255, 0.06)',
                      backgroundColor: isPaused ? 'rgba(245, 158, 11, 0.04)' : 'transparent',
                      opacity: isBusy ? 0.65 : 1
                    }}
                  >
                    {/* Col 1: Identity & Provider */}
                    <td style={{ padding: '14px 12px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '11px' }}>
                        <div style={{
                          width: '38px',
                          height: '38px',
                          borderRadius: '11px',
                          flexShrink: 0,
                          background: isPaused
                            ? 'rgba(245, 158, 11, 0.2)'
                            : 'linear-gradient(135deg, rgba(16, 185, 129, 0.22), rgba(59, 130, 246, 0.22))',
                          border: '1px solid rgba(255, 255, 255, 0.12)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: 800,
                          fontSize: '14px',
                          color: isPaused ? '#fbbf24' : '#f8fafc'
                        }}>
                          {(u.name || u.email || 'U').charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '7px', flexWrap: 'wrap' }}>
                            <span style={{ fontWeight: 700, color: '#f8fafc', fontSize: '14px' }}>
                              {u.name || 'Atleta'}
                            </span>
                            {getTierBadge(u.accountTier)}
                            {getProviderBadge(u.authProvider)}
                          </div>
                          <div style={{ color: 'var(--text-secondary)', fontSize: '12px', marginTop: '2px' }}>
                            {u.email}
                          </div>
                          <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                            {u.goal || 'Meta padrão'} • {u.currentWeight ?? 72}kg → {u.targetWeight ?? 80}kg ({Math.round(u.targetCalorieIntake ?? 2800)} kcal)
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Col 2: Account Status & AI Governance */}
                    <td style={{ padding: '14px 12px' }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-start' }}>
                        <span
                          title={u.pausedReason || undefined}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '5px',
                            padding: '3px 9px',
                            borderRadius: '999px',
                            fontSize: '11px',
                            fontWeight: 700,
                            backgroundColor: isPaused ? 'rgba(245, 158, 11, 0.18)' : 'rgba(16, 185, 129, 0.15)',
                            color: isPaused ? '#fbbf24' : '#34d399',
                            border: `1px solid ${isPaused ? 'rgba(245, 158, 11, 0.35)' : 'rgba(16, 185, 129, 0.3)'}`
                          }}
                        >
                          {isPaused ? '⏸️ Pausada' : '🟢 Ativa'}
                        </span>

                        {u.pausedReason && isPaused && (
                          <span style={{ fontSize: '11px', color: '#fcd34d', maxWidth: '180px' }}>
                            “{u.pausedReason}”
                          </span>
                        )}

                        {!isPro ? (
                          <span style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '5px',
                            padding: '2px 8px',
                            borderRadius: '6px',
                            fontSize: '11px',
                            fontWeight: 600,
                            backgroundColor: 'rgba(148, 163, 184, 0.12)',
                            color: '#cbd5e1'
                          }}>
                            🌿 Modo Essencial (TACO + Manual)
                          </span>
                        ) : (
                          <span style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '5px',
                            padding: '2px 8px',
                            borderRadius: '6px',
                            fontSize: '11px',
                            fontWeight: 600,
                            backgroundColor: u.aiBlocked ? 'rgba(239, 68, 68, 0.15)' : 'rgba(139, 92, 246, 0.14)',
                            color: u.aiBlocked ? '#f87171' : '#c4b5fd'
                          }}>
                            {u.aiBlocked ? (
                              <>🚫 IA Bloqueada</>
                            ) : (
                              <>
                                <Sparkles size={11} />
                                IA hoje: {u.todayAiRequests ?? 0}
                                {u.dailyAiLimit ? ` / ${u.dailyAiLimit}` : ''}
                              </>
                            )}
                          </span>
                        )}
                      </div>
                    </td>

                    {/* Col 3: Cloud Data Volume */}
                    <td style={{ padding: '14px 12px' }}>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', maxWidth: '220px' }}>
                        <span style={{
                          padding: '2px 8px', borderRadius: '6px', fontSize: '11px',
                          backgroundColor: 'rgba(255,255,255,0.05)', color: '#e2e8f0'
                        }}>
                          🍎 {u.foodsCount ?? 0} ref.
                        </span>
                        <span style={{
                          padding: '2px 8px', borderRadius: '6px', fontSize: '11px',
                          backgroundColor: 'rgba(255,255,255,0.05)', color: '#e2e8f0'
                        }}>
                          🏋️ {u.workoutsCount ?? 0} treinos
                        </span>
                        <span style={{
                          padding: '2px 8px', borderRadius: '6px', fontSize: '11px',
                          backgroundColor: 'rgba(255,255,255,0.05)', color: 'var(--text-secondary)'
                        }}>
                          📋 {u.routinesCount ?? 0} fichas
                        </span>
                        <span style={{
                          padding: '2px 8px', borderRadius: '6px', fontSize: '11px',
                          backgroundColor: 'rgba(255,255,255,0.05)', color: 'var(--text-secondary)'
                        }}>
                          🤖 {u.totalAiRequests ?? 0} IA
                        </span>
                      </div>
                    </td>

                    {/* Col 4: Timestamps */}
                    <td style={{ padding: '14px 12px', fontSize: '11px', color: 'var(--text-muted)', whiteSpace: 'nowrap' }} className="font-mono">
                      <div>Login: <span style={{ color: 'var(--text-secondary)' }}>{formatShortDate(u.lastLoginAt, '--')}</span></div>
                      <div style={{ marginTop: '3px' }}>Sync: <span style={{ color: 'var(--text-secondary)' }}>{formatShortDate(u.lastSyncedAt, 'Nunca')}</span></div>
                      {u.forceResetAt && (
                        <div style={{ marginTop: '3px', color: '#fbbf24' }} title={`Último reset administrativo: ${new Date(u.forceResetAt).toLocaleString('pt-BR')}`}>
                          ⚡ Reset: {formatShortDate(u.forceResetAt)}
                        </div>
                      )}
                    </td>

                    {/* Col 5: Quick Action Buttons */}
                    <td style={{ padding: '14px 12px', textAlign: 'right' }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '6px', flexWrap: 'wrap' }}>
                        {/* Alternar Camada FREE <-> PRO */}
                        <button
                          type="button"
                          disabled={isBusy}
                          onClick={() => handleQuickToggleTier(u)}
                          title={
                            isPro
                              ? 'Mudar esta conta para a camada FREE (visual essencial simplificado sem IA)'
                              : 'Ativar camada Forma PRO (libera todas as funções de IA para esta conta)'
                          }
                          style={
                            isPro
                              ? actionBtnStyle('#cbd5e1', 'rgba(148, 163, 184, 0.14)', 'rgba(148, 163, 184, 0.3)')
                              : actionBtnStyle('#ddd6fe', 'rgba(139, 92, 246, 0.22)', 'rgba(167, 139, 250, 0.48)')
                          }
                        >
                          <Sparkles size={13} />
                          <span>{isPro ? 'Mudar p/ FREE' : 'Ativar PRO'}</span>
                        </button>

                        {/* Editar / Inspecionar */}
                        <button
                          type="button"
                          onClick={() => setDetailTarget(u)}
                          title="Inspecionar dados, camada, metas, senha e cota de IA"
                          style={actionBtnStyle('#60a5fa', 'rgba(59, 130, 246, 0.14)', 'rgba(59, 130, 246, 0.3)')}
                        >
                          <Sliders size={13} />
                          <span>Gerenciar</span>
                        </button>

                        {/* Pausar / Reativar */}
                        {isPaused ? (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => handleQuickResume(u)}
                            title="Reativar conta imediatamente"
                            style={actionBtnStyle('#34d399', 'rgba(16, 185, 129, 0.16)', 'rgba(16, 185, 129, 0.35)')}
                          >
                            <PlayCircle size={13} />
                            <span>Reativar</span>
                          </button>
                        ) : (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => setPauseTarget(u)}
                            title="Pausar conta temporariamente (bloqueia login, sync e IA)"
                            style={actionBtnStyle('#fbbf24', 'rgba(245, 158, 11, 0.14)', 'rgba(245, 158, 11, 0.32)')}
                          >
                            <PauseCircle size={13} />
                            <span>Pausar</span>
                          </button>
                        )}

                        {/* Bloquear / Liberar IA (relevante para contas PRO) */}
                        {isPro && (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => handleQuickToggleAi(u)}
                            title={u.aiBlocked ? 'Liberar uso do Gemini AI para esta conta' : 'Bloquear apenas o Gemini AI para esta conta'}
                            style={
                              u.aiBlocked
                                ? actionBtnStyle('#c4b5fd', 'rgba(139, 92, 246, 0.18)', 'rgba(139, 92, 246, 0.38)')
                                : actionBtnStyle('#cbd5e1', 'rgba(255, 255, 255, 0.06)', 'rgba(255, 255, 255, 0.14)')
                            }
                          >
                            <ShieldAlert size={13} />
                            <span>{u.aiBlocked ? 'Liberar IA' : 'Bloquear IA'}</span>
                          </button>
                        )}

                        {/* Zerar Conta */}
                        <button
                          type="button"
                          disabled={isBusy}
                          onClick={() => setResetTarget(u)}
                          title="Zerar dados da conta (total ou seletivo) e limpar cache do celular no próximo sync"
                          style={actionBtnStyle('#fdba74', 'rgba(249, 115, 22, 0.14)', 'rgba(249, 115, 22, 0.32)')}
                        >
                          <RotateCcw size={13} />
                          <span>Zerar</span>
                        </button>

                        {/* Excluir Conta */}
                        <button
                          type="button"
                          disabled={isBusy}
                          onClick={() => setDeleteTarget(u)}
                          title="Excluir usuário e todos os seus dados permanentemente"
                          style={actionBtnStyle('#f87171', 'rgba(239, 68, 68, 0.14)', 'rgba(239, 68, 68, 0.32)')}
                        >
                          <Trash2 size={13} />
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

      {/* Modals */}
      {showCreateModal && (
        <CreateUserModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={(msg) => showToast(msg)}
        />
      )}

      {pauseTarget && (
        <PauseUserModal
          user={pauseTarget}
          onClose={() => setPauseTarget(null)}
          onSuccess={(msg) => showToast(msg)}
        />
      )}

      {resetTarget && (
        <ResetUserModal
          user={resetTarget}
          onClose={() => setResetTarget(null)}
          onSuccess={(msg) => showToast(msg)}
        />
      )}

      {deleteTarget && (
        <DeleteUserModal
          user={deleteTarget}
          onClose={() => setDeleteTarget(null)}
          onSuccess={(msg) => showToast(msg)}
        />
      )}

      {detailTarget && (
        <UserDetailModal
          userId={detailTarget.id}
          initialUser={detailTarget}
          onClose={() => setDetailTarget(null)}
          onUpdated={(msg) => {
            if (msg) showToast(msg);
            else onRefresh();
          }}
        />
      )}
    </div>
  );
};
