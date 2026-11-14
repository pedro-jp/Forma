import React, { useMemo, useState } from 'react';
import { Search, UserPlus } from 'lucide-react';
import { UserSummary } from '../types';
import {
  CreateUserModal,
  DeleteUserModal,
  PauseUserModal,
  ResetUserModal,
  UserDetailModal
} from './UserModals';
import { formatDateTimeBR } from './billingFormat';

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

  const metrics = useMemo(() => {
    const total = users.length;
    const paused = users.filter((u) => u.status === 'PAUSED').length;
    const active = total - paused;
    const proCount = users.filter((u) => isProTier(u.accountTier)).length;
    const aiRestricted = users.filter((u) => u.aiBlocked || (u.dailyAiLimit && u.dailyAiLimit > 0)).length;
    return { total, active, paused, proCount, aiRestricted };
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
        showToast(`Conta de ${user.name || user.email} reativada.`);
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
            : `Acesso à IA liberado para ${user.name || user.email}.`
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
            ? `Conta alterada para Forma Pro (${user.email}).`
            : `Conta alterada para Essencial Free (${user.email}).`
        );
      } else {
        showToast('Falha ao alterar camada da conta.', true);
      }
    } catch {
      showToast('Erro de conexão ao alterar camada da conta.', true);
    } finally {
      setBusyUserId(null);
    }
  };

  return (
    <div className="view-stack">
      <div className="title-row">
        <div>
          <h1 className="page-title">Usuários e acessos</h1>
          <p className="page-subtitle">
            Gestão de contas, camada Pro/Essencial, cotas individuais de IA e sincronização com o aplicativo iOS.
          </p>
        </div>

        <button type="button" className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
          <UserPlus size={14} />
          <span>Criar usuário</span>
        </button>
      </div>

      {banner && (
        <div className={`notice-banner ${banner.isError ? 'notice-neg' : 'notice-pos'}`} role="status">
          <span>{banner.text}</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setBanner(null)}>
            Dispensar
          </button>
        </div>
      )}

      <section className="kpis" aria-label="Indicadores de usuários">
        <div className="kpi">
          <div className="kpi-label">Total de contas</div>
          <div className="kpi-val">{metrics.total}</div>
          <div className="kpi-sub">Total na base de usuários</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Contas ativas</div>
          <div className="kpi-val is-pos">{metrics.active}</div>
          <div className="kpi-sub">Sem bloqueio de login</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Forma Pro</div>
          <div className="kpi-val">{metrics.proCount}</div>
          <div className="kpi-sub">{metrics.total - metrics.proCount} na camada Essencial</div>
        </div>
        <div className="kpi">
          <div className="kpi-label">Restritas ou pausadas</div>
          <div className={`kpi-val ${metrics.paused + metrics.aiRestricted > 0 ? 'is-warn' : ''}`}>
            {metrics.paused} / {metrics.aiRestricted}
          </div>
          <div className="kpi-sub">Pausadas / limite IA</div>
        </div>
      </section>

      <section className="panel" aria-label="Diretório de usuários">
        <div className="panel-head">
          <div style={{ position: 'relative', flex: '1 1 260px', maxWidth: '360px' }}>
            <Search
              size={14}
              style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--faint)' }}
            />
            <input
              className="input"
              style={{ paddingLeft: '32px', minHeight: '34px' }}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Buscar por nome, e-mail ou UUID..."
            />
          </div>

          <div className="segmented" role="group" aria-label="Filtro de usuários">
            {(
              [
                ['ALL', 'Todos'],
                ['PRO', 'Pro'],
                ['FREE', 'Essencial'],
                ['ACTIVE', 'Ativos'],
                ['PAUSED', 'Pausados'],
                ['AI_RESTRICTED', 'IA restrita'],
                ['APPLE', 'Apple'],
                ['GOOGLE', 'Google']
              ] as [FilterMode, string][]
            ).map(([mode, label]) => (
              <button
                key={mode}
                type="button"
                className={`seg-btn ${filterMode === mode ? 'is-active' : ''}`}
                onClick={() => setFilterMode(mode)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Usuário</th>
                <th>Camada</th>
                <th>Provedor</th>
                <th>Status</th>
                <th className="num">Uso de IA</th>
                <th className="num">Registros</th>
                <th>Último sync</th>
                <th style={{ textAlign: 'right' }}>Ações</th>
              </tr>
            </thead>
            <tbody>
              {filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan={8} className="table-empty">
                    Nenhum usuário corresponde ao filtro atual.
                  </td>
                </tr>
              ) : (
                filteredUsers.map((u) => {
                  const pro = isProTier(u.accountTier);
                  const paused = u.status === 'PAUSED';
                  const initial = (u.name || u.email || 'U').trim().charAt(0).toUpperCase();
                  const busy = busyUserId === u.id;

                  return (
                    <tr key={u.id}>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <div
                            style={{
                              width: '30px',
                              height: '30px',
                              borderRadius: '999px',
                              backgroundColor: pro ? 'var(--accent-tint)' : 'var(--raised)',
                              border: '1px solid var(--border)',
                              color: pro ? 'var(--accent)' : 'var(--muted)',
                              display: 'inline-flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontSize: '12px',
                              fontWeight: 600,
                              flexShrink: 0
                            }}
                          >
                            {initial}
                          </div>
                          <div>
                            <div style={{ fontWeight: 500 }}>{u.name || 'Sem nome'}</div>
                            <div className="mono" style={{ fontSize: '12px', color: 'var(--faint)' }}>
                              {u.email}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <button
                          type="button"
                          onClick={() => handleQuickToggleTier(u)}
                          disabled={busy}
                          className={`pill ${pro ? 'pill-pos' : 'pill-neutral'}`}
                          style={{ border: 'none' }}
                          title="Alternar camada Pro / Essencial"
                        >
                          {pro ? 'Forma Pro' : 'Essencial'}
                        </button>
                      </td>
                      <td>
                        <span className="code-chip">{u.authProvider || 'EMAIL'}</span>
                      </td>
                      <td>
                        <span className={`pill ${paused ? 'pill-neg' : 'pill-pos'}`}>
                          {paused ? 'Pausada' : 'Ativa'}
                        </span>
                      </td>
                      <td className="num">
                        <div>
                          {u.todayAiRequests} hoje
                          {u.dailyAiLimit ? ` / ${u.dailyAiLimit}` : ''}
                        </div>
                        <button
                          type="button"
                          onClick={() => handleQuickToggleAi(u)}
                          disabled={busy}
                          style={{
                            background: 'none',
                            border: 'none',
                            fontSize: '11.5px',
                            color: u.aiBlocked ? 'var(--neg-ink)' : 'var(--faint)',
                            padding: 0,
                            marginTop: '2px'
                          }}
                        >
                          {u.aiBlocked ? 'IA bloqueada' : `${u.totalAiRequests} totais`}
                        </button>
                      </td>
                      <td className="num" style={{ color: 'var(--muted)' }}>
                        {u.foodsCount} ref. · {u.workoutsCount} tr.
                      </td>
                      <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)', whiteSpace: 'nowrap' }}>
                        {formatDateTimeBR(u.lastSyncedAt || u.lastLoginAt || u.createdAt)}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <div className="actions-row" style={{ justifyContent: 'flex-end' }}>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => setDetailTarget(u)}
                          >
                            Gerenciar
                          </button>
                          {paused ? (
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              disabled={busy}
                              onClick={() => handleQuickResume(u)}
                            >
                              Ativar
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="btn btn-ghost btn-sm"
                              disabled={busy}
                              onClick={() => setPauseTarget(u)}
                            >
                              Pausar
                            </button>
                          )}
                          <button
                            type="button"
                            className="btn btn-ghost btn-sm"
                            onClick={() => setResetTarget(u)}
                          >
                            Resetar
                          </button>
                          <button
                            type="button"
                            className="btn btn-ghost btn-sm"
                            style={{ color: 'var(--neg-ink)' }}
                            onClick={() => setDeleteTarget(u)}
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

      {showCreateModal && (
        <CreateUserModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={(msg) => {
            setShowCreateModal(false);
            showToast(msg);
          }}
        />
      )}

      {pauseTarget && (
        <PauseUserModal
          user={pauseTarget}
          onClose={() => setPauseTarget(null)}
          onSuccess={(msg) => {
            setPauseTarget(null);
            showToast(msg);
          }}
        />
      )}

      {resetTarget && (
        <ResetUserModal
          user={resetTarget}
          onClose={() => setResetTarget(null)}
          onSuccess={(msg) => {
            setResetTarget(null);
            showToast(msg);
          }}
        />
      )}

      {deleteTarget && (
        <DeleteUserModal
          user={deleteTarget}
          onClose={() => setDeleteTarget(null)}
          onSuccess={(msg) => {
            setDeleteTarget(null);
            showToast(msg);
          }}
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
