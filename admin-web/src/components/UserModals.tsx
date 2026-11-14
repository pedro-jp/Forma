import React, { useEffect, useState } from 'react';
import { UserDetailResponse, UserSummary } from '../types';
import { formatDateTimeBR } from './billingFormat';

// ==========================================
// 1. Criar Novo Usuário
// ==========================================
interface CreateUserModalProps {
  onClose: () => void;
  onSuccess: (msg: string) => void;
}

export const CreateUserModal: React.FC<CreateUserModalProps> = ({ onClose, onSuccess }) => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [goal, setGoal] = useState('Ganhar peso');
  const [accountTier, setAccountTier] = useState<'FREE' | 'PRO'>('FREE');
  const [dailyAiLimit, setDailyAiLimit] = useState('');
  const [aiBlocked, setAiBlocked] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaving(true);

    try {
      const payload: Record<string, any> = {
        name: name.trim(),
        email: email.trim(),
        password: password.trim(),
        goal,
        accountTier,
        aiBlocked
      };
      if (dailyAiLimit.trim() !== '') {
        payload.dailyAiLimit = Number(dailyAiLimit);
      }

      const resp = await fetch('/api/admin/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload)
      });

      const data = await resp.json();
      if (!resp.ok) {
        setError(data.error || 'Erro ao criar conta de usuário.');
        return;
      }

      onSuccess(`Conta criada para ${data.name} (${data.email}).`);
    } catch {
      setError('Falha de rede ao criar usuário.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-dialog" onSubmit={handleSubmit} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h2 className="modal-title">Criar conta de usuário</h2>
            <p className="modal-sub">Cria conta autenticada pronta para login imediato no aplicativo.</p>
          </div>
          <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
            Fechar
          </button>
        </div>

        {error && <div className="notice-banner notice-neg">{error}</div>}

        <div className="field">
          <label className="field-label">Nome completo</label>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Ex: Maria Clara" required />
        </div>

        <div className="field">
          <label className="field-label">E-mail de acesso</label>
          <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="usuario@exemplo.com" required />
        </div>

        <div className="form-grid-2">
          <div className="field">
            <label className="field-label">Senha provisória</label>
            <input className="input" type="text" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Mínimo 6 caracteres" minLength={6} required />
          </div>
          <div className="field">
            <label className="field-label">Objetivo inicial</label>
            <select className="select" value={goal} onChange={(e) => setGoal(e.target.value)}>
              <option value="Perder peso">Perder peso</option>
              <option value="Manter peso">Manter peso</option>
              <option value="Ganhar peso">Ganhar peso</option>
            </select>
          </div>
        </div>

        <div className="field">
          <label className="field-label">Camada inicial</label>
          <div className="segmented">
            <button
              type="button"
              className={`seg-btn ${accountTier === 'FREE' ? 'is-active' : ''}`}
              onClick={() => setAccountTier('FREE')}
            >
              Essencial (Free)
            </button>
            <button
              type="button"
              className={`seg-btn ${accountTier === 'PRO' ? 'is-active' : ''}`}
              onClick={() => setAccountTier('PRO')}
            >
              Forma Pro
            </button>
          </div>
        </div>

        <div className="form-grid-2">
          <div className="field">
            <label className="field-label">Limite diário de IA</label>
            <input className="input mono" type="number" min={0} placeholder="Ilimitado" value={dailyAiLimit} onChange={(e) => setDailyAiLimit(e.target.value)} />
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', marginTop: '22px', cursor: 'pointer' }}>
            <input type="checkbox" checked={aiBlocked} onChange={(e) => setAiBlocked(e.target.checked)} />
            Bloquear IA para esta conta
          </label>
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Criando...' : 'Criar conta'}
          </button>
        </div>
      </form>
    </div>
  );
};

// ==========================================
// 2. Pausar Conta
// ==========================================
interface PauseUserModalProps {
  user: UserSummary;
  onClose: () => void;
  onSuccess: (msg: string) => void;
}

export const PauseUserModal: React.FC<PauseUserModalProps> = ({ user, onClose, onSuccess }) => {
  const [reason, setReason] = useState(
    user.pausedReason || 'Conta temporariamente suspensa pelo administrador do sistema.'
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleConfirm = async () => {
    setSaving(true);
    setError(null);
    try {
      const resp = await fetch(`/api/admin/users/${user.id}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ paused: true, reason: reason.trim() })
      });
      if (!resp.ok) {
        const data = await resp.json().catch(() => ({}));
        setError(data.error || 'Erro ao pausar conta.');
        return;
      }
      onSuccess(`Conta de ${user.name || user.email} pausada em tempo real.`);
    } catch {
      setError('Falha de rede ao pausar conta.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h2 className="modal-title">Pausar acesso da conta</h2>
            <p className="modal-sub">A sessão no aplicativo iOS é bloqueada instantaneamente sem apagar dados.</p>
          </div>
          <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
            Fechar
          </button>
        </div>

        {error && <div className="notice-banner notice-neg">{error}</div>}

        <div className="well">
          <div style={{ fontWeight: 600 }}>{user.name || 'Sem nome'}</div>
          <div className="mono" style={{ fontSize: '12px', color: 'var(--muted)' }}>{user.email}</div>
        </div>

        <div className="field">
          <label className="field-label">Motivo exibido no dispositivo</label>
          <textarea
            className="textarea"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancelar
          </button>
          <button type="button" className="btn btn-danger" disabled={saving} onClick={handleConfirm}>
            {saving ? 'Pausando...' : 'Confirmar pausa'}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==========================================
// 3. Resetar Dados do App
// ==========================================
interface ResetUserModalProps {
  user: UserSummary;
  onClose: () => void;
  onSuccess: (msg: string) => void;
}

export const ResetUserModal: React.FC<ResetUserModalProps> = ({ user, onClose, onSuccess }) => {
  const [scope, setScope] = useState<'DATA_ONLY' | 'FULL_ACCOUNT'>('DATA_ONLY');
  const [resetProfileGoals, setResetProfileGoals] = useState(true);
  const [keepAuthAccount, setKeepAuthAccount] = useState(true);
  const [clearAiLogs, setClearAiLogs] = useState(false);
  const [confirmText, setConfirmText] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleReset = async () => {
    if (confirmText.trim().toUpperCase() !== 'RESETAR') {
      setError('Digite RESETAR para confirmar.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const resp = await fetch(`/api/admin/users/${user.id}/reset`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          scope,
          resetProfileGoals,
          keepAuthAccount: scope === 'DATA_ONLY' ? true : keepAuthAccount,
          clearAiLogs
        })
      });
      if (!resp.ok) {
        const data = await resp.json().catch(() => ({}));
        setError(data.error || 'Falha ao executar reset.');
        return;
      }
      onSuccess(`Reset (${scope}) executado para ${user.email}.`);
    } catch {
      setError('Erro de conexão ao executar reset.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h2 className="modal-title">Resetar dados de {user.name || user.email}</h2>
            <p className="modal-sub">Limpa registros na nuvem e dispara força de reset remoto no iPhone conectado.</p>
          </div>
          <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
            Fechar
          </button>
        </div>

        {error && <div className="notice-banner notice-neg">{error}</div>}

        <div className="field">
          <label className="field-label">Escopo do reset</label>
          <div className="segmented">
            <button
              type="button"
              className={`seg-btn ${scope === 'DATA_ONLY' ? 'is-active' : ''}`}
              onClick={() => setScope('DATA_ONLY')}
            >
              Apenas diário (refeições/treinos)
            </button>
            <button
              type="button"
              className={`seg-btn ${scope === 'FULL_ACCOUNT' ? 'is-active' : ''}`}
              onClick={() => setScope('FULL_ACCOUNT')}
            >
              Conta completa e onboarding
            </button>
          </div>
        </div>

        <div className="well" style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '13px' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
            <input type="checkbox" checked={resetProfileGoals} onChange={(e) => setResetProfileGoals(e.target.checked)} />
            Reiniciar metas calóricas e macros
          </label>
          {scope === 'FULL_ACCOUNT' && (
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
              <input type="checkbox" checked={keepAuthAccount} onChange={(e) => setKeepAuthAccount(e.target.checked)} />
              Manter login ativo (exigir novo onboarding)
            </label>
          )}
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
            <input type="checkbox" checked={clearAiLogs} onChange={(e) => setClearAiLogs(e.target.checked)} />
            Limpar logs de IA vinculados ao usuário
          </label>
        </div>

        <div className="field">
          <label className="field-label">Digite RESETAR para confirmar</label>
          <input className="input mono" value={confirmText} onChange={(e) => setConfirmText(e.target.value)} placeholder="RESETAR" />
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancelar
          </button>
          <button type="button" className="btn btn-danger" disabled={saving} onClick={handleReset}>
            {saving ? 'Executando...' : 'Executar reset'}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==========================================
// 4. Excluir Conta Permanentemente
// ==========================================
interface DeleteUserModalProps {
  user: UserSummary;
  onClose: () => void;
  onSuccess: (msg: string) => void;
}

export const DeleteUserModal: React.FC<DeleteUserModalProps> = ({ user, onClose, onSuccess }) => {
  const [confirmEmail, setConfirmEmail] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canConfirm = confirmEmail.trim().toLowerCase() === (user.email || '').trim().toLowerCase();

  const handleDelete = async () => {
    if (!canConfirm) return;
    setDeleting(true);
    setError(null);
    try {
      const resp = await fetch(`/api/admin/users/${user.id}`, {
        method: 'DELETE',
        credentials: 'include'
      });
      if (!resp.ok) {
        const data = await resp.json().catch(() => ({}));
        setError(data.error || 'Não foi possível excluir a conta.');
        return;
      }
      onSuccess(`Conta ${user.email} excluída definitivamente.`);
    } catch {
      setError('Erro de conexão ao excluir conta.');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h2 className="modal-title">Excluir conta permanentemente</h2>
            <p className="modal-sub">Ação irreversível em conformidade com a LGPD (tombstone e remoção em cascata).</p>
          </div>
          <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
            Fechar
          </button>
        </div>

        {error && <div className="notice-banner notice-neg">{error}</div>}

        <div className="notice-banner notice-neg">
          Todos os dados de <strong>{user.email}</strong> serão removidos permanentemente do banco e o app será desconectado.
        </div>

        <div className="field">
          <label className="field-label">Digite o e-mail exatamente como cadastrado para confirmar</label>
          <input
            className="input mono"
            value={confirmEmail}
            onChange={(e) => setConfirmEmail(e.target.value)}
            placeholder={user.email}
          />
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancelar
          </button>
          <button type="button" className="btn btn-danger" disabled={!canConfirm || deleting} onClick={handleDelete}>
            {deleting ? 'Excluindo...' : 'Excluir definitivamente'}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==========================================
// 5. Detalhes e Edição Completa
// ==========================================
interface UserDetailModalProps {
  userId: string;
  initialUser: UserSummary;
  onClose: () => void;
  onUpdated: (msg?: string) => void;
}

export const UserDetailModal: React.FC<UserDetailModalProps> = ({
  userId,
  initialUser,
  onClose,
  onUpdated
}) => {
  const [tab, setTab] = useState<'profile' | 'ai' | 'activity'>('profile');
  const [detail, setDetail] = useState<UserDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);

  const [name, setName] = useState(initialUser.name || '');
  const [email, setEmail] = useState(initialUser.email || '');
  const [newPassword, setNewPassword] = useState('');
  const [goal, setGoal] = useState(initialUser.goal || 'Ganhar peso');
  const [currentWeight, setCurrentWeight] = useState(String(initialUser.currentWeight ?? 72));
  const [targetWeight, setTargetWeight] = useState(String(initialUser.targetWeight ?? 80));
  const [heightMeters, setHeightMeters] = useState('1.75');
  const [targetCalories, setTargetCalories] = useState(String(initialUser.targetCalorieIntake ?? 2800));
  const [targetBurn, setTargetBurn] = useState('450');
  const [targetWater, setTargetWater] = useState('2500');

  const [accountTier, setAccountTier] = useState<'FREE' | 'PRO'>(
    (initialUser.accountTier || 'FREE').toUpperCase() === 'PRO' ? 'PRO' : 'FREE'
  );
  const [aiBlocked, setAiBlocked] = useState(initialUser.aiBlocked);
  const [dailyAiLimit, setDailyAiLimit] = useState(
    initialUser.dailyAiLimit ? String(initialUser.dailyAiLimit) : ''
  );
  const [isPaused, setIsPaused] = useState(initialUser.status === 'PAUSED');
  const [pausedReason, setPausedReason] = useState(initialUser.pausedReason || '');

  useEffect(() => {
    loadUserDetail();
  }, [userId]);

  const loadUserDetail = async () => {
    setLoading(true);
    try {
      const resp = await fetch(`/api/admin/users/${userId}`, { credentials: 'include' });
      if (resp.ok) {
        const data: UserDetailResponse = await resp.json();
        setDetail(data);
        setName(data.user.name || '');
        setEmail(data.user.email || '');
        setAccountTier((data.user.accountTier || 'FREE').toUpperCase() === 'PRO' ? 'PRO' : 'FREE');
        setAiBlocked(data.user.aiBlocked);
        setDailyAiLimit(data.user.dailyAiLimit ? String(data.user.dailyAiLimit) : '');
        setIsPaused(data.user.status === 'PAUSED');
        setPausedReason(data.user.pausedReason || '');

        if (data.profile) {
          setGoal(data.profile.goal || 'Ganhar peso');
          if (data.profile.currentWeight != null) setCurrentWeight(String(data.profile.currentWeight));
          if (data.profile.targetWeight != null) setTargetWeight(String(data.profile.targetWeight));
          if (data.profile.heightMeters != null) setHeightMeters(String(data.profile.heightMeters));
          if (data.profile.targetCalorieIntake != null) setTargetCalories(String(data.profile.targetCalorieIntake));
          if (data.profile.targetCalorieBurn != null) setTargetBurn(String(data.profile.targetCalorieBurn));
          if (data.profile.targetWaterMilliliters != null) setTargetWater(String(data.profile.targetWaterMilliliters));
        }
      }
    } catch {
      setFeedback({ text: 'Não foi possível carregar detalhes completos da conta.', isError: true });
    } finally {
      setLoading(false);
    }
  };

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setFeedback(null);
    try {
      const payload: Record<string, any> = {
        name: name.trim(),
        email: email.trim(),
        accountTier,
        goal,
        currentWeight: Number(currentWeight) || undefined,
        targetWeight: Number(targetWeight) || undefined,
        heightMeters: Number(heightMeters) || undefined,
        targetCalorieIntake: Number(targetCalories) || undefined,
        targetCalorieBurn: Number(targetBurn) || undefined,
        targetWaterMilliliters: Number(targetWater) || undefined
      };
      if (newPassword.trim()) {
        payload.newPassword = newPassword.trim();
      }

      const resp = await fetch(`/api/admin/users/${userId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload)
      });
      const data = await resp.json();
      if (!resp.ok) {
        setFeedback({ text: data.error || 'Falha ao salvar perfil.', isError: true });
        return;
      }
      setNewPassword('');
      setFeedback({ text: 'Dados cadastrais e metas atualizados.', isError: false });
      onUpdated(`Perfil de ${data.name || data.email} atualizado.`);
      loadUserDetail();
    } catch {
      setFeedback({ text: 'Erro de conexão ao salvar perfil.', isError: true });
    } finally {
      setSaving(false);
    }
  };

  const handleSaveGovernance = async () => {
    setSaving(true);
    setFeedback(null);
    try {
      const limitValue = dailyAiLimit.trim() === '' ? 0 : Number(dailyAiLimit);
      const [tierResp, aiResp, statusResp] = await Promise.all([
        fetch(`/api/admin/users/${userId}/tier`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ accountTier })
        }),
        fetch(`/api/admin/users/${userId}/ai-access`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ aiBlocked, dailyAiLimit: limitValue })
        }),
        fetch(`/api/admin/users/${userId}/status`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ paused: isPaused, reason: isPaused ? pausedReason.trim() : null })
        })
      ]);

      if (!tierResp.ok || !aiResp.ok || !statusResp.ok) {
        setFeedback({ text: 'Falha ao atualizar camada, permissões de IA ou status.', isError: true });
        return;
      }

      setFeedback({ text: 'Permissões de conta, camada e IA atualizadas.', isError: false });
      onUpdated(`Governança de ${name || email} atualizada.`);
      loadUserDetail();
    } catch {
      setFeedback({ text: 'Erro de rede ao salvar permissões.', isError: true });
    } finally {
      setSaving(false);
    }
  };

  const summary = detail?.user || initialUser;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-dialog modal-lg" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h2 className="modal-title">{summary.name || summary.email}</h2>
            <p className="modal-sub">
              <span className="mono">{summary.email}</span> · Provedor {summary.authProvider} · ID <span className="mono">{summary.id}</span>
            </p>
          </div>
          <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
            Fechar
          </button>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
          <div className="segmented" role="tablist">
            <button type="button" className={`seg-btn ${tab === 'profile' ? 'is-active' : ''}`} onClick={() => setTab('profile')}>
              Perfil e metas
            </button>
            <button type="button" className={`seg-btn ${tab === 'ai' ? 'is-active' : ''}`} onClick={() => setTab('ai')}>
              Camada e acesso de IA
            </button>
            <button type="button" className={`seg-btn ${tab === 'activity' ? 'is-active' : ''}`} onClick={() => setTab('activity')}>
              Registros recentes
            </button>
          </div>

          <span className={`pill ${summary.status === 'PAUSED' ? 'pill-neg' : 'pill-pos'}`}>
            {summary.status === 'PAUSED' ? 'Conta pausada' : 'Conta ativa'}
          </span>
        </div>

        {feedback && (
          <div className={`notice-banner ${feedback.isError ? 'notice-neg' : 'notice-pos'}`}>
            {feedback.text}
          </div>
        )}

        {loading && !detail ? (
          <div className="table-empty">Carregando dados da conta...</div>
        ) : (
          <>
            {tab === 'profile' && (
              <form onSubmit={handleSaveProfile} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div className="form-grid-2">
                  <div className="field">
                    <label className="field-label">Nome</label>
                    <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
                  </div>
                  <div className="field">
                    <label className="field-label">E-mail</label>
                    <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
                  </div>
                </div>

                <div className="form-grid-2">
                  <div className="field">
                    <label className="field-label">Objetivo</label>
                    <select className="select" value={goal} onChange={(e) => setGoal(e.target.value)}>
                      <option value="Perder peso">Perder peso</option>
                      <option value="Manter peso">Manter peso</option>
                      <option value="Ganhar peso">Ganhar peso</option>
                    </select>
                  </div>
                  <div className="field">
                    <label className="field-label">Nova senha (opcional)</label>
                    <input className="input" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="Deixe vazio para manter" />
                  </div>
                </div>

                <div className="form-grid-3">
                  <div className="field">
                    <label className="field-label">Peso atual (kg)</label>
                    <input className="input mono" type="number" step="0.1" value={currentWeight} onChange={(e) => setCurrentWeight(e.target.value)} />
                  </div>
                  <div className="field">
                    <label className="field-label">Meta de peso (kg)</label>
                    <input className="input mono" type="number" step="0.1" value={targetWeight} onChange={(e) => setTargetWeight(e.target.value)} />
                  </div>
                  <div className="field">
                    <label className="field-label">Altura (m)</label>
                    <input className="input mono" type="number" step="0.01" value={heightMeters} onChange={(e) => setHeightMeters(e.target.value)} />
                  </div>
                </div>

                <div className="form-grid-3">
                  <div className="field">
                    <label className="field-label">Meta calórica (kcal)</label>
                    <input className="input mono" type="number" value={targetCalories} onChange={(e) => setTargetCalories(e.target.value)} />
                  </div>
                  <div className="field">
                    <label className="field-label">Queima diária (kcal)</label>
                    <input className="input mono" type="number" value={targetBurn} onChange={(e) => setTargetBurn(e.target.value)} />
                  </div>
                  <div className="field">
                    <label className="field-label">Água diária (ml)</label>
                    <input className="input mono" type="number" value={targetWater} onChange={(e) => setTargetWater(e.target.value)} />
                  </div>
                </div>

                <div className="modal-footer">
                  <button type="button" className="btn btn-secondary" onClick={onClose}>
                    Fechar
                  </button>
                  <button type="submit" className="btn btn-primary" disabled={saving}>
                    {saving ? 'Salvando...' : 'Salvar perfil'}
                  </button>
                </div>
              </form>
            )}

            {tab === 'ai' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div className="field">
                  <label className="field-label">Camada da conta</label>
                  <div className="segmented">
                    <button
                      type="button"
                      className={`seg-btn ${accountTier === 'FREE' ? 'is-active' : ''}`}
                      onClick={() => setAccountTier('FREE')}
                    >
                      Essencial (Free)
                    </button>
                    <button
                      type="button"
                      className={`seg-btn ${accountTier === 'PRO' ? 'is-active' : ''}`}
                      onClick={() => setAccountTier('PRO')}
                    >
                      Forma Pro
                    </button>
                  </div>
                </div>

                <div className="form-grid-2">
                  <div className="field">
                    <label className="field-label">Limite diário de chamadas IA</label>
                    <input
                      className="input mono"
                      type="number"
                      min={0}
                      placeholder="0 ou vazio = ilimitado"
                      value={dailyAiLimit}
                      onChange={(e) => setDailyAiLimit(e.target.value)}
                    />
                  </div>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', marginTop: '22px', cursor: 'pointer' }}>
                    <input type="checkbox" checked={aiBlocked} onChange={(e) => setAiBlocked(e.target.checked)} />
                    Bloquear acesso à IA individualmente
                  </label>
                </div>

                <div className="well" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 500, cursor: 'pointer' }}>
                    <input type="checkbox" checked={isPaused} onChange={(e) => setIsPaused(e.target.checked)} />
                    Suspender acesso do aplicativo (conta pausada)
                  </label>
                  {isPaused && (
                    <input
                      className="input"
                      value={pausedReason}
                      onChange={(e) => setPausedReason(e.target.value)}
                      placeholder="Motivo exibido no app..."
                    />
                  )}
                </div>

                <div className="modal-footer">
                  <button type="button" className="btn btn-secondary" onClick={onClose}>
                    Fechar
                  </button>
                  <button type="button" className="btn btn-primary" disabled={saving} onClick={handleSaveGovernance}>
                    {saving ? 'Salvando...' : 'Aplicar permissões'}
                  </button>
                </div>
              </div>
            )}

            {tab === 'activity' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div className="panel">
                  <div className="panel-head">
                    <h3 className="panel-title">Refeições recentes ({detail?.recentFoods.length || 0})</h3>
                  </div>
                  <div className="table-wrap">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Alimento</th>
                          <th className="num">Calorias</th>
                          <th>Registrado</th>
                        </tr>
                      </thead>
                      <tbody>
                        {!detail?.recentFoods?.length ? (
                          <tr><td colSpan={3} className="table-empty">Sem refeições registradas.</td></tr>
                        ) : (
                          detail.recentFoods.slice(0, 6).map((f) => (
                            <tr key={f.id}>
                              <td>{f.name}</td>
                              <td className="num">{Math.round(f.calories || 0)} kcal</td>
                              <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)' }}>{formatDateTimeBR(f.consumedAt)}</td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>

                <div className="panel">
                  <div className="panel-head">
                    <h3 className="panel-title">Treinos recentes ({detail?.recentWorkouts.length || 0})</h3>
                  </div>
                  <div className="table-wrap">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Treino</th>
                          <th className="num">Duração</th>
                          <th>Data</th>
                        </tr>
                      </thead>
                      <tbody>
                        {!detail?.recentWorkouts?.length ? (
                          <tr><td colSpan={3} className="table-empty">Sem treinos registrados.</td></tr>
                        ) : (
                          detail.recentWorkouts.slice(0, 6).map((w) => (
                            <tr key={w.id}>
                              <td>{w.title}</td>
                              <td className="num">{w.durationMinutes || 0} min</td>
                              <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)' }}>{formatDateTimeBR(w.workoutDate)}</td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>

                <div className="panel">
                  <div className="panel-head">
                    <h3 className="panel-title">Requisições de IA recentes ({detail?.recentAiLogs?.length || 0})</h3>
                  </div>
                  <div className="table-wrap">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Data e hora</th>
                          <th>Serviço</th>
                          <th>Status</th>
                          <th className="num">Tempo</th>
                        </tr>
                      </thead>
                      <tbody>
                        {!detail?.recentAiLogs?.length ? (
                          <tr><td colSpan={4} className="table-empty">Sem requisições de IA registradas.</td></tr>
                        ) : (
                          detail.recentAiLogs.slice(0, 8).map((log) => (
                            <tr key={log.id}>
                              <td className="mono" style={{ fontSize: '12px', color: 'var(--muted)', whiteSpace: 'nowrap' }}>
                                {formatDateTimeBR(log.createdAt)}
                              </td>
                              <td className="mono" style={{ fontSize: '12px' }}>{log.endpoint}</td>
                              <td>
                                <span className={`pill pill-${log.status === 'SUCCESS' ? 'ok' : 'neg'}`}>
                                  {log.status === 'SUCCESS' ? 'OK' : log.status}
                                </span>
                              </td>
                              <td className="num mono" style={{ fontSize: '12px', color: 'var(--muted)' }}>
                                {log.durationMs ?? 0} ms
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};
