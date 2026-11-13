import React, { useState, useEffect } from 'react';
import {
  X,
  UserPlus,
  PauseCircle,
  PlayCircle,
  RotateCcw,
  Trash2,
  ShieldAlert,
  Sparkles,
  Check,
  AlertTriangle,
  Sliders,
  Activity,
  Utensils,
  Dumbbell,
  Cpu,
  Lock,
  Save
} from 'lucide-react';
import { UserSummary, UserDetailResponse } from '../types';

const overlayStyle: React.CSSProperties = {
  position: 'fixed',
  inset: 0,
  backgroundColor: 'rgba(4, 8, 16, 0.82)',
  backdropFilter: 'blur(10px)',
  WebkitBackdropFilter: 'blur(10px)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: '20px',
  zIndex: 1000
};

const modalBoxStyle = (maxWidth = '560px'): React.CSSProperties => ({
  width: '100%',
  maxWidth,
  maxHeight: '90vh',
  overflowY: 'auto',
  backgroundColor: '#111827',
  border: '1px solid rgba(255, 255, 255, 0.12)',
  borderRadius: '20px',
  padding: '26px',
  boxShadow: '0 24px 64px rgba(0, 0, 0, 0.65)'
});

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '11px 14px',
  borderRadius: '10px',
  backgroundColor: 'rgba(15, 23, 42, 0.9)',
  border: '1px solid rgba(255, 255, 255, 0.14)',
  color: '#f8fafc',
  fontSize: '13px'
};

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontSize: '12px',
  fontWeight: 600,
  color: 'var(--text-secondary)',
  marginBottom: '6px'
};

// ==========================================
// 1. MODAL: CRIAR NOVO USUÁRIO
// ==========================================
interface CreateUserModalProps {
  onClose: () => void;
  onSuccess: (msg: string) => void;
}

export const CreateUserModal: React.FC<CreateUserModalProps> = ({ onClose, onSuccess }) => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [accountTier, setAccountTier] = useState<'FREE' | 'PRO'>('FREE');
  const [dailyAiLimit, setDailyAiLimit] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const payload: Record<string, any> = {
        name: name.trim() || 'Atleta',
        email: email.trim(),
        password,
        accountTier
      };
      if (accountTier === 'PRO' && dailyAiLimit.trim() && Number(dailyAiLimit) > 0) {
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
        setError(data.error || 'Falha ao criar conta.');
        return;
      }
      onSuccess(`Conta ${accountTier} criada com sucesso para ${data.email}!`);
      onClose();
    } catch {
      setError('Erro de conexão ao criar usuário.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalBoxStyle('480px')} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '38px', height: '38px', borderRadius: '10px',
              backgroundColor: 'rgba(16, 185, 129, 0.15)', color: '#34d399',
              display: 'flex', alignItems: 'center', justifyContent: 'center'
            }}>
              <UserPlus size={20} />
            </div>
            <div>
              <h3 style={{ fontSize: '17px', fontWeight: 800 }}>Criar Conta de Usuário</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Cadastro direto no Supabase com perfil inicial</p>
            </div>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)' }}>
            <X size={20} />
          </button>
        </div>

        {error && (
          <div style={{
            padding: '10px 14px', borderRadius: '10px', marginBottom: '16px', fontSize: '12px',
            backgroundColor: 'rgba(239, 68, 68, 0.15)', border: '1px solid rgba(239, 68, 68, 0.35)', color: '#f87171'
          }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div>
            <label style={labelStyle}>Nome do Atleta</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ex: João Silva"
              style={inputStyle}
              required
            />
          </div>
          <div>
            <label style={labelStyle}>E-mail de Login</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="atleta@email.com"
              style={inputStyle}
              required
            />
          </div>
          <div>
            <label style={labelStyle}>Senha Inicial (mín. 4 caracteres)</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              style={inputStyle}
              minLength={4}
              required
            />
          </div>
          <div>
            <label style={labelStyle}>Camada da Conta (definida pelo Admin • sem cobrança)</label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <button
                type="button"
                onClick={() => setAccountTier('FREE')}
                style={{
                  textAlign: 'left',
                  padding: '12px',
                  borderRadius: '12px',
                  backgroundColor: accountTier === 'FREE' ? 'rgba(16, 185, 129, 0.14)' : 'rgba(15, 23, 42, 0.7)',
                  border: `1.5px solid ${accountTier === 'FREE' ? '#10b981' : 'rgba(255, 255, 255, 0.1)'}`,
                  color: '#f8fafc'
                }}
              >
                <div style={{ fontSize: '13px', fontWeight: 800, color: accountTier === 'FREE' ? '#34d399' : '#e2e8f0' }}>
                  🌿 Free • Essencial
                </div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginTop: '4px', lineHeight: 1.35 }}>
                  Tabela TACO, treinos manuais, Ilha Dinâmica e visual limpo sem IA.
                </div>
              </button>

              <button
                type="button"
                onClick={() => setAccountTier('PRO')}
                style={{
                  textAlign: 'left',
                  padding: '12px',
                  borderRadius: '12px',
                  background: accountTier === 'PRO'
                    ? 'linear-gradient(135deg, rgba(139, 92, 246, 0.24), rgba(16, 185, 129, 0.14))'
                    : 'rgba(15, 23, 42, 0.7)',
                  border: `1.5px solid ${accountTier === 'PRO' ? '#a78bfa' : 'rgba(255, 255, 255, 0.1)'}`,
                  color: '#f8fafc'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '13px', fontWeight: 800, color: accountTier === 'PRO' ? '#ddd6fe' : '#e2e8f0' }}>
                  <Sparkles size={13} /> Forma Pro
                </div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginTop: '4px', lineHeight: 1.35 }}>
                  Todas as funções de IA liberadas (Foto, Voz, Treinos e Coach).
                </div>
              </button>
            </div>
          </div>

          {accountTier === 'PRO' && (
            <div>
              <label style={labelStyle}>Cota Diária de IA (opcional — vazio = sem limite)</label>
              <input
                type="number"
                min={0}
                value={dailyAiLimit}
                onChange={(e) => setDailyAiLimit(e.target.value)}
                placeholder="Ex: 25 requisições por dia"
                style={inputStyle}
              />
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: '10px 16px', borderRadius: '10px', background: 'transparent',
                border: '1px solid var(--border-color)', color: 'var(--text-secondary)', fontWeight: 600, fontSize: '13px'
              }}
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={loading}
              style={{
                padding: '10px 18px', borderRadius: '10px', backgroundColor: '#10b981',
                border: 'none', color: '#042f1a', fontWeight: 700, fontSize: '13px'
              }}
            >
              {loading ? 'Criando...' : 'Criar Usuário'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ==========================================
// 2. MODAL: PAUSAR CONTA DE USUÁRIO
// ==========================================
interface PauseUserModalProps {
  user: UserSummary;
  onClose: () => void;
  onSuccess: (msg: string) => void;
}

export const PauseUserModal: React.FC<PauseUserModalProps> = ({ user, onClose, onSuccess }) => {
  const [reason, setReason] = useState(user.pausedReason || '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const presets = [
    'Assinatura pendente / expirada',
    'Uso excessivo de requisições de IA',
    'Manutenção temporária na conta',
    'Solicitação do próprio usuário'
  ];

  const handleConfirm = async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await fetch(`/api/admin/users/${user.id}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ paused: true, reason: reason.trim() || null })
      });
      const data = await resp.json();
      if (!resp.ok) {
        setError(data.error || 'Erro ao pausar conta.');
        return;
      }
      onSuccess(`Conta de ${user.name || user.email} pausada imediatamente.`);
      onClose();
    } catch {
      setError('Falha de rede ao pausar usuário.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalBoxStyle('500px')} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '38px', height: '38px', borderRadius: '10px',
              backgroundColor: 'rgba(245, 158, 11, 0.18)', color: '#fbbf24',
              display: 'flex', alignItems: 'center', justifyContent: 'center'
            }}>
              <PauseCircle size={20} />
            </div>
            <div>
              <h3 style={{ fontSize: '17px', fontWeight: 800 }}>Pausar Conta de {user.name || 'Atleta'}</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{user.email}</p>
            </div>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)' }}>
            <X size={20} />
          </button>
        </div>

        <div style={{
          padding: '12px 14px', borderRadius: '12px', marginBottom: '16px', fontSize: '12px',
          backgroundColor: 'rgba(245, 158, 11, 0.1)', border: '1px solid rgba(245, 158, 11, 0.28)', color: '#fde68a'
        }}>
          Ao pausar esta conta, <strong>novos logins, sincronizações na nuvem e chamadas de IA</strong> serão bloqueados imediatamente (HTTP 403), preservando todos os dados salvos até você reativá-la.
        </div>

        {error && (
          <div style={{
            padding: '10px 14px', borderRadius: '10px', marginBottom: '14px', fontSize: '12px',
            backgroundColor: 'rgba(239, 68, 68, 0.15)', color: '#f87171'
          }}>
            {error}
          </div>
        )}

        <label style={labelStyle}>Motivo da pausa (exibido ao usuário ao tentar logar):</label>
        <input
          type="text"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Opcional — ex: Assinatura pendente"
          style={{ ...inputStyle, marginBottom: '10px' }}
        />

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '20px' }}>
          {presets.map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => setReason(p)}
              style={{
                padding: '5px 10px', borderRadius: '999px', fontSize: '11px',
                backgroundColor: reason === p ? 'rgba(245, 158, 11, 0.25)' : 'rgba(255, 255, 255, 0.06)',
                border: '1px solid rgba(255, 255, 255, 0.12)',
                color: reason === p ? '#fbbf24' : 'var(--text-secondary)'
              }}
            >
              {p}
            </button>
          ))}
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <button
            type="button"
            onClick={onClose}
            style={{
              padding: '10px 16px', borderRadius: '10px', background: 'transparent',
              border: '1px solid var(--border-color)', color: 'var(--text-secondary)', fontWeight: 600, fontSize: '13px'
            }}
          >
            Cancelar
          </button>
          <button
            type="button"
            disabled={loading}
            onClick={handleConfirm}
            style={{
              padding: '10px 18px', borderRadius: '10px', backgroundColor: '#f59e0b',
              border: 'none', color: '#1e1302', fontWeight: 700, fontSize: '13px'
            }}
          >
            {loading ? 'Pausando...' : 'Confirmar Pausa'}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==========================================
// 3. MODAL: ZERAR CONTA (TOTAL OU SELETIVO)
// ==========================================
interface ResetUserModalProps {
  user: UserSummary;
  onClose: () => void;
  onSuccess: (msg: string) => void;
}

export const ResetUserModal: React.FC<ResetUserModalProps> = ({ user, onClose, onSuccess }) => {
  const [clearMealsAndWorkouts, setClearMealsAndWorkouts] = useState(true);
  const [clearHydrationAndWeights, setClearHydrationAndWeights] = useState(true);
  const [clearRoutines, setClearRoutines] = useState(true);
  const [clearAnalysesAndLogs, setClearAnalysesAndLogs] = useState(true);
  const [resetProfileGoals, setResetProfileGoals] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const anySelected =
    clearMealsAndWorkouts ||
    clearHydrationAndWeights ||
    clearRoutines ||
    clearAnalysesAndLogs ||
    resetProfileGoals;

  const selectAll = () => {
    setClearMealsAndWorkouts(true);
    setClearHydrationAndWeights(true);
    setClearRoutines(true);
    setClearAnalysesAndLogs(true);
    setResetProfileGoals(true);
  };

  const selectHistoryOnly = () => {
    setClearMealsAndWorkouts(true);
    setClearHydrationAndWeights(true);
    setClearAnalysesAndLogs(true);
    setClearRoutines(false);
    setResetProfileGoals(false);
  };

  const handleReset = async () => {
    if (!anySelected) return;
    setLoading(true);
    setError(null);
    try {
      const resp = await fetch(`/api/admin/users/${user.id}/reset`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          clearMealsAndWorkouts,
          clearHydrationAndWeights,
          clearRoutines,
          clearAnalysesAndLogs,
          resetProfileGoals
        })
      });
      const data = await resp.json();
      if (!resp.ok) {
        setError(data.error || 'Falha ao zerar dados da conta.');
        return;
      }
      onSuccess(data.message || `Conta de ${user.name || user.email} zerada com sucesso!`);
      onClose();
    } catch {
      setError('Erro de conexão ao zerar conta.');
    } finally {
      setLoading(false);
    }
  };

  const checkItemStyle = (checked: boolean): React.CSSProperties => ({
    display: 'flex',
    alignItems: 'flex-start',
    gap: '12px',
    padding: '11px 14px',
    borderRadius: '12px',
    cursor: 'pointer',
    backgroundColor: checked ? 'rgba(245, 158, 11, 0.1)' : 'rgba(255, 255, 255, 0.03)',
    border: `1px solid ${checked ? 'rgba(245, 158, 11, 0.35)' : 'rgba(255, 255, 255, 0.08)'}`
  });

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalBoxStyle('560px')} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '38px', height: '38px', borderRadius: '10px',
              backgroundColor: 'rgba(245, 158, 11, 0.18)', color: '#fbbf24',
              display: 'flex', alignItems: 'center', justifyContent: 'center'
            }}>
              <RotateCcw size={20} />
            </div>
            <div>
              <h3 style={{ fontSize: '17px', fontWeight: 800 }}>Zerar Conta de {user.name || 'Atleta'}</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}> Mantém o login ({user.email}), mas limpa os dados selecionados</p>
            </div>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)' }}>
            <X size={20} />
          </button>
        </div>

        <div style={{
          padding: '12px 14px', borderRadius: '12px', marginBottom: '14px', fontSize: '12px',
          backgroundColor: 'rgba(59, 130, 246, 0.12)', border: '1px solid rgba(59, 130, 246, 0.28)', color: '#93c5fd'
        }}>
          <strong>Sincronização Anti-Conflito:</strong> O servidor grava um carimbo <code>forceResetAt</code>. Quando o celular do usuário sincronizar novamente, o app apagará o banco local automaticamente em vez de reenviar dados antigos para o Supabase.
        </div>

        <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', flexWrap: 'wrap' }}>
          <button
            type="button"
            onClick={selectAll}
            style={{
              padding: '6px 12px', borderRadius: '8px', fontSize: '11px', fontWeight: 700,
              backgroundColor: 'rgba(255, 255, 255, 0.08)', border: '1px solid rgba(255, 255, 255, 0.14)', color: '#f8fafc'
            }}
          >
            Reset Completo (Tudo)
          </button>
          <button
            type="button"
            onClick={selectHistoryOnly}
            style={{
              padding: '6px 12px', borderRadius: '8px', fontSize: '11px', fontWeight: 700,
              backgroundColor: 'rgba(255, 255, 255, 0.05)', border: '1px solid rgba(255, 255, 255, 0.12)', color: 'var(--text-secondary)'
            }}
          >
            Apenas Histórico Diário (Manter Fichas e Metas)
          </button>
        </div>

        {error && (
          <div style={{
            padding: '10px 14px', borderRadius: '10px', marginBottom: '12px', fontSize: '12px',
            backgroundColor: 'rgba(239, 68, 68, 0.15)', color: '#f87171'
          }}>
            {error}
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '20px' }}>
          <label style={checkItemStyle(clearMealsAndWorkouts)}>
            <input
              type="checkbox"
              checked={clearMealsAndWorkouts}
              onChange={(e) => setClearMealsAndWorkouts(e.target.checked)}
              style={{ marginTop: '3px' }}
            />
            <div>
              <div style={{ fontSize: '13px', fontWeight: 700 }}>Refeições e Treinos Realizados</div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                Remove {user.foodsCount || 0} alimentos e {user.workoutsCount || 0} sessões de treino registradas.
              </div>
            </div>
          </label>

          <label style={checkItemStyle(clearHydrationAndWeights)}>
            <input
              type="checkbox"
              checked={clearHydrationAndWeights}
              onChange={(e) => setClearHydrationAndWeights(e.target.checked)}
              style={{ marginTop: '3px' }}
            />
            <div>
              <div style={{ fontSize: '13px', fontWeight: 700 }}>Hidratação e Histórico de Peso</div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                Remove {user.watersCount || 0} registros de água e {user.weightsCount || 0} pesagens corporais.
              </div>
            </div>
          </label>

          <label style={checkItemStyle(clearRoutines)}>
            <input
              type="checkbox"
              checked={clearRoutines}
              onChange={(e) => setClearRoutines(e.target.checked)}
              style={{ marginTop: '3px' }}
            />
            <div>
              <div style={{ fontSize: '13px', fontWeight: 700 }}>Fichas de Treino Salvas</div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                Remove {user.routinesCount || 0} fichas personalizadas / geradas por IA.
              </div>
            </div>
          </label>

          <label style={checkItemStyle(clearAnalysesAndLogs)}>
            <input
              type="checkbox"
              checked={clearAnalysesAndLogs}
              onChange={(e) => setClearAnalysesAndLogs(e.target.checked)}
              style={{ marginTop: '3px' }}
            />
            <div>
              <div style={{ fontSize: '13px', fontWeight: 700 }}>Análises de Evolução IA e Logs de Atividade</div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                Remove {user.analysesCount || 0} diagnósticos de evolução e histórico da linha do tempo.
              </div>
            </div>
          </label>

          <label style={checkItemStyle(resetProfileGoals)}>
            <input
              type="checkbox"
              checked={resetProfileGoals}
              onChange={(e) => setResetProfileGoals(e.target.checked)}
              style={{ marginTop: '3px' }}
            />
            <div>
              <div style={{ fontSize: '13px', fontWeight: 700 }}>Restaurar Perfil e Metas Diárias para o Padrão</div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                Volta calorias, macros, meta de água e peso alvo para a configuração inicial.
              </div>
            </div>
          </label>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <button
            type="button"
            onClick={onClose}
            style={{
              padding: '10px 16px', borderRadius: '10px', background: 'transparent',
              border: '1px solid var(--border-color)', color: 'var(--text-secondary)', fontWeight: 600, fontSize: '13px'
            }}
          >
            Cancelar
          </button>
          <button
            type="button"
            disabled={loading || !anySelected}
            onClick={handleReset}
            style={{
              padding: '10px 18px', borderRadius: '10px',
              backgroundColor: anySelected ? '#f59e0b' : 'rgba(255,255,255,0.1)',
              border: 'none', color: anySelected ? '#1e1302' : 'var(--text-muted)',
              fontWeight: 800, fontSize: '13px'
            }}
          >
            {loading ? 'Zerando Conta...' : 'Zerar Dados Selecionados'}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==========================================
// 4. MODAL: EXCLUIR CONTA PERMANENTEMENTE
// ==========================================
interface DeleteUserModalProps {
  user: UserSummary;
  onClose: () => void;
  onSuccess: (msg: string) => void;
}

export const DeleteUserModal: React.FC<DeleteUserModalProps> = ({ user, onClose, onSuccess }) => {
  const [confirmText, setConfirmText] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canDelete =
    confirmText.trim().toUpperCase() === 'EXCLUIR' ||
    confirmText.trim().toLowerCase() === user.email.toLowerCase();

  const handleDelete = async () => {
    if (!canDelete) return;
    setLoading(true);
    setError(null);
    try {
      const resp = await fetch(`/api/admin/users/${user.id}`, {
        method: 'DELETE',
        credentials: 'include'
      });
      const data = await resp.json();
      if (!resp.ok) {
        setError(data.error || 'Falha ao excluir usuário.');
        return;
      }
      onSuccess(data.message || `Conta ${user.email} excluída permanentemente.`);
      onClose();
    } catch {
      setError('Erro de conexão ao excluir conta.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalBoxStyle('500px')} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '38px', height: '38px', borderRadius: '10px',
              backgroundColor: 'rgba(239, 68, 68, 0.2)', color: '#f87171',
              display: 'flex', alignItems: 'center', justifyContent: 'center'
            }}>
              <Trash2 size={20} />
            </div>
            <div>
              <h3 style={{ fontSize: '17px', fontWeight: 800, color: '#fca5a5' }}>Excluir Conta Permanentemente</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{user.name} • {user.email}</p>
            </div>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)' }}>
            <X size={20} />
          </button>
        </div>

        <div style={{
          padding: '14px', borderRadius: '12px', marginBottom: '16px', fontSize: '12px',
          backgroundColor: 'rgba(239, 68, 68, 0.12)', border: '1px solid rgba(239, 68, 68, 0.35)', color: '#fecaca'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 800, marginBottom: '6px' }}>
            <AlertTriangle size={16} /> Ação irreversível no Supabase
          </div>
          Esta operação removerá definitivamente o cadastro do usuário, perfil,{' '}
          <strong>{user.foodsCount || 0} refeições</strong>, <strong>{user.workoutsCount || 0} treinos</strong>,{' '}
          <strong>{user.routinesCount || 0} fichas</strong>, pesagens, hidratação e logs de IA. Aparelhos logados serão desconectados automaticamente na próxima sincronização.
        </div>

        {error && (
          <div style={{
            padding: '10px 14px', borderRadius: '10px', marginBottom: '14px', fontSize: '12px',
            backgroundColor: 'rgba(239, 68, 68, 0.15)', color: '#f87171'
          }}>
            {error}
          </div>
        )}

        <label style={labelStyle}>
          Digite <code style={{ color: '#f87171' }}>EXCLUIR</code> ou o e-mail <code style={{ color: '#f87171' }}>{user.email}</code> para confirmar:
        </label>
        <input
          type="text"
          value={confirmText}
          onChange={(e) => setConfirmText(e.target.value)}
          placeholder="EXCLUIR"
          style={{ ...inputStyle, marginBottom: '20px', borderColor: 'rgba(239, 68, 68, 0.35)' }}
        />

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <button
            type="button"
            onClick={onClose}
            style={{
              padding: '10px 16px', borderRadius: '10px', background: 'transparent',
              border: '1px solid var(--border-color)', color: 'var(--text-secondary)', fontWeight: 600, fontSize: '13px'
            }}
          >
            Cancelar
          </button>
          <button
            type="button"
            disabled={!canDelete || loading}
            onClick={handleDelete}
            style={{
              padding: '10px 18px', borderRadius: '10px',
              backgroundColor: canDelete ? '#ef4444' : 'rgba(239, 68, 68, 0.2)',
              border: 'none', color: canDelete ? '#fff' : 'rgba(255,255,255,0.4)',
              fontWeight: 800, fontSize: '13px'
            }}
          >
            {loading ? 'Excluindo...' : 'Excluir Permanentemente'}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==========================================
// 5. MODAL: INSPECIONAR & EDITAR USUÁRIO
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

  // Form states - Profile & Account
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

  // Form states - AI, Tier & Status
  const [accountTier, setAccountTier] = useState<'FREE' | 'PRO'>(
    (initialUser.accountTier || 'FREE').toUpperCase() === 'PRO' ? 'PRO' : 'FREE'
  );
  const [tierSaving, setTierSaving] = useState(false);
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
      setFeedback({ text: 'Dados cadastrais e metas salvos no Supabase!', isError: false });
      onUpdated(`Perfil de ${data.name || data.email} atualizado!`);
      loadUserDetail();
    } catch {
      setFeedback({ text: 'Erro de conexão ao salvar perfil.', isError: true });
    } finally {
      setSaving(false);
    }
  };

  const handleSelectTierImmediate = async (nextTier: 'FREE' | 'PRO') => {
    if (nextTier === accountTier || tierSaving) return;
    setTierSaving(true);
    setFeedback(null);
    try {
      const resp = await fetch(`/api/admin/users/${userId}/tier`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ accountTier: nextTier })
      });
      if (!resp.ok) {
        setFeedback({ text: 'Não foi possível alterar a camada da conta.', isError: true });
        return;
      }
      setAccountTier(nextTier);
      const msg =
        nextTier === 'PRO'
          ? `Conta atualizada para Forma PRO (todas as funções de IA liberadas)!`
          : `Conta atualizada para FREE (camada visual essencial simplificada)!`;
      setFeedback({ text: msg, isError: false });
      onUpdated(msg);
      loadUserDetail();
    } catch {
      setFeedback({ text: 'Erro de rede ao alterar camada da conta.', isError: true });
    } finally {
      setTierSaving(false);
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
          body: JSON.stringify({
            aiBlocked,
            dailyAiLimit: limitValue
          })
        }),
        fetch(`/api/admin/users/${userId}/status`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            paused: isPaused,
            reason: isPaused ? pausedReason.trim() : null
          })
        })
      ]);

      if (!tierResp.ok || !aiResp.ok || !statusResp.ok) {
        setFeedback({ text: 'Falha ao atualizar camada, permissões de IA ou status da conta.', isError: true });
        return;
      }

      setFeedback({ text: 'Permissões de IA, cota diária e status atualizados!', isError: false });
      onUpdated(`Governança de ${name || email} atualizada!`);
      loadUserDetail();
    } catch {
      setFeedback({ text: 'Erro de rede ao salvar permissões.', isError: true });
    } finally {
      setSaving(false);
    }
  };

  const summary = detail?.user || initialUser;

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalBoxStyle('760px')} onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
          paddingBottom: '16px', marginBottom: '18px', borderBottom: '1px solid var(--border-color)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
            <div style={{
              width: '46px', height: '46px', borderRadius: '14px',
              background: summary.status === 'PAUSED'
                ? 'rgba(245, 158, 11, 0.2)'
                : 'linear-gradient(135deg, rgba(16, 185, 129, 0.25), rgba(59, 130, 246, 0.25))',
              border: '1px solid rgba(255, 255, 255, 0.14)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: '18px', fontWeight: 800, color: '#fff'
            }}>
              {(summary.name || summary.email || 'U').charAt(0).toUpperCase()}
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                <h3 style={{ fontSize: '18px', fontWeight: 800 }}>{summary.name || 'Atleta'}</h3>
                <span style={{
                  padding: '2px 9px', borderRadius: '999px', fontSize: '11px', fontWeight: 700,
                  backgroundColor: summary.status === 'PAUSED' ? 'rgba(245, 158, 11, 0.2)' : 'rgba(16, 185, 129, 0.18)',
                  color: summary.status === 'PAUSED' ? '#fbbf24' : '#34d399'
                }}>
                  {summary.status === 'PAUSED' ? '⏸️ Pausada' : '🟢 Ativa'}
                </span>
                <span style={{
                  display: 'inline-flex', alignItems: 'center', gap: '4px',
                  padding: '2px 10px', borderRadius: '999px', fontSize: '11px', fontWeight: 800,
                  background: accountTier === 'PRO'
                    ? 'linear-gradient(135deg, rgba(139, 92, 246, 0.28), rgba(16, 185, 129, 0.22))'
                    : 'rgba(148, 163, 184, 0.16)',
                  color: accountTier === 'PRO' ? '#ddd6fe' : '#cbd5e1',
                  border: `1px solid ${accountTier === 'PRO' ? 'rgba(167, 139, 250, 0.45)' : 'rgba(148, 163, 184, 0.28)'}`
                }}>
                  {accountTier === 'PRO' ? <><Sparkles size={11} /> FORMA PRO</> : 'FREE • Essencial'}
                </span>
                <span className="font-mono" style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                  ID: {summary.id.slice(0, 8)}...
                </span>
              </div>
              <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                {summary.email} • Provedor: <strong>{summary.authProvider}</strong> •{' '}
                {accountTier === 'PRO' ? (
                  <>
                    IA hoje: <strong>{summary.todayAiRequests}</strong>
                    {summary.dailyAiLimit ? ` / ${summary.dailyAiLimit}` : ' (ilimitado)'}
                  </>
                ) : (
                  <span>Camada Essencial Simplificada (Sem IA)</span>
                )}
              </p>
            </div>
          </div>

          <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)' }}>
            <X size={22} />
          </button>
        </div>

        {/* Seletor Direto de Camada da Conta (Sem Pagamento) */}
        <div style={{
          padding: '14px 16px',
          borderRadius: '14px',
          marginBottom: '16px',
          backgroundColor: 'rgba(15, 23, 42, 0.72)',
          border: '1px solid rgba(255, 255, 255, 0.1)'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px', gap: '8px', flexWrap: 'wrap' }}>
            <span style={{ fontSize: '12px', fontWeight: 800, color: '#f8fafc' }}>
              Camada da Conta (seleção administrativa • sem gateway de pagamento)
            </span>
            {tierSaving && (
              <span style={{ fontSize: '11px', color: '#a78bfa', fontWeight: 700 }}>Atualizando camada...</span>
            )}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(230px, 1fr))', gap: '10px' }}>
            <button
              type="button"
              disabled={tierSaving}
              onClick={() => handleSelectTierImmediate('FREE')}
              style={{
                textAlign: 'left',
                padding: '11px 14px',
                borderRadius: '11px',
                backgroundColor: accountTier === 'FREE' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(255, 255, 255, 0.03)',
                border: `1.5px solid ${accountTier === 'FREE' ? '#10b981' : 'rgba(255, 255, 255, 0.08)'}`,
                color: '#f8fafc'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: '13px', fontWeight: 800, color: accountTier === 'FREE' ? '#34d399' : '#cbd5e1' }}>
                  🌿 Camada Free (Essencial)
                </span>
                {accountTier === 'FREE' && <Check size={14} color="#34d399" />}
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginTop: '3px' }}>
                Interface limpa e direta: Tabela TACO, fichas manuais, Ilha Dinâmica e gráficos.
              </div>
            </button>

            <button
              type="button"
              disabled={tierSaving}
              onClick={() => handleSelectTierImmediate('PRO')}
              style={{
                textAlign: 'left',
                padding: '11px 14px',
                borderRadius: '11px',
                background: accountTier === 'PRO'
                  ? 'linear-gradient(135deg, rgba(139, 92, 246, 0.24), rgba(16, 185, 129, 0.14))'
                  : 'rgba(255, 255, 255, 0.03)',
                border: `1.5px solid ${accountTier === 'PRO' ? '#a78bfa' : 'rgba(255, 255, 255, 0.08)'}`,
                color: '#f8fafc'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', fontSize: '13px', fontWeight: 800, color: accountTier === 'PRO' ? '#ddd6fe' : '#cbd5e1' }}>
                  <Sparkles size={13} /> Camada Forma Pro (IA Completa)
                </span>
                {accountTier === 'PRO' && <Check size={14} color="#c4b5fd" />}
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginTop: '3px' }}>
                Libera Foto IA, Voz IA, Criador/Calculadora de Treino IA e Diagnóstico de Evolução.
              </div>
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div style={{ display: 'flex', gap: '8px', marginBottom: '18px', flexWrap: 'wrap' }}>
          {[
            { id: 'profile', label: 'Perfil, Metas & Senha', icon: <Sliders size={14} /> },
            { id: 'ai', label: 'Acesso IA, Cota & Status', icon: <Sparkles size={14} /> },
            { id: 'activity', label: 'Dados Sincronizados na Nuvem', icon: <Activity size={14} /> }
          ].map((t) => {
            const active = tab === t.id;
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => { setTab(t.id as any); setFeedback(null); }}
                style={{
                  display: 'flex', alignItems: 'center', gap: '7px',
                  padding: '9px 14px', borderRadius: '10px', fontSize: '12px', fontWeight: 700,
                  backgroundColor: active ? 'rgba(16, 185, 129, 0.18)' : 'rgba(255, 255, 255, 0.05)',
                  color: active ? '#34d399' : 'var(--text-secondary)',
                  border: `1px solid ${active ? 'rgba(16, 185, 129, 0.35)' : 'rgba(255, 255, 255, 0.08)'}`
                }}
              >
                {t.icon}
                <span>{t.label}</span>
              </button>
            );
          })}
        </div>

        {feedback && (
          <div style={{
            display: 'flex', alignItems: 'center', gap: '8px',
            padding: '10px 14px', borderRadius: '10px', marginBottom: '16px', fontSize: '12px',
            backgroundColor: feedback.isError ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
            border: `1px solid ${feedback.isError ? 'rgba(239, 68, 68, 0.35)' : 'rgba(16, 185, 129, 0.35)'}`,
            color: feedback.isError ? '#f87171' : '#34d399'
          }}>
            {feedback.isError ? <AlertTriangle size={15} /> : <Check size={15} />}
            <span>{feedback.text}</span>
          </div>
        )}

        {/* TAB 1: PROFILE & SECURITY */}
        {tab === 'profile' && (
          <form onSubmit={handleSaveProfile}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
              gap: '14px',
              marginBottom: '18px'
            }}>
              <div>
                <label style={labelStyle}>Nome</label>
                <input type="text" value={name} onChange={(e) => setName(e.target.value)} style={inputStyle} required />
              </div>
              <div>
                <label style={labelStyle}>E-mail</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} style={inputStyle} required />
              </div>
              <div>
                <label style={labelStyle}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                    <Lock size={12} /> Redefinir Senha (opcional)
                  </span>
                </label>
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="Deixe vazio para não alterar"
                  style={inputStyle}
                />
              </div>
              <div>
                <label style={labelStyle}>Objetivo Nutricional</label>
                <select value={goal} onChange={(e) => setGoal(e.target.value)} style={inputStyle}>
                  <option value="Ganhar peso">Ganhar peso</option>
                  <option value="Perder peso">Perder peso</option>
                  <option value="Manter peso">Manter peso</option>
                  <option value="Ganhar massa magra">Ganhar massa magra</option>
                </select>
              </div>
              <div>
                <label style={labelStyle}>Peso Atual (kg)</label>
                <input type="number" step="0.1" value={currentWeight} onChange={(e) => setCurrentWeight(e.target.value)} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Peso Alvo (kg)</label>
                <input type="number" step="0.1" value={targetWeight} onChange={(e) => setTargetWeight(e.target.value)} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Altura (m)</label>
                <input type="number" step="0.01" value={heightMeters} onChange={(e) => setHeightMeters(e.target.value)} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Meta de Calorias Diárias (kcal)</label>
                <input type="number" value={targetCalories} onChange={(e) => setTargetCalories(e.target.value)} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Meta de Queima Ativa (kcal)</label>
                <input type="number" value={targetBurn} onChange={(e) => setTargetBurn(e.target.value)} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Meta de Água (ml)</label>
                <input type="number" step="100" value={targetWater} onChange={(e) => setTargetWater(e.target.value)} style={inputStyle} />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button
                type="submit"
                disabled={saving}
                style={{
                  display: 'flex', alignItems: 'center', gap: '8px',
                  padding: '10px 20px', borderRadius: '10px', backgroundColor: '#10b981',
                  border: 'none', color: '#042f1a', fontWeight: 800, fontSize: '13px'
                }}
              >
                <Save size={15} />
                <span>{saving ? 'Salvando...' : 'Salvar Cadastro & Metas'}</span>
              </button>
            </div>
          </form>
        )}

        {/* TAB 2: AI QUOTA & ACCOUNT STATUS */}
        {tab === 'ai' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {/* Status Box */}
            <div style={{
              padding: '16px', borderRadius: '14px',
              backgroundColor: isPaused ? 'rgba(245, 158, 11, 0.1)' : 'rgba(16, 185, 129, 0.08)',
              border: `1px solid ${isPaused ? 'rgba(245, 158, 11, 0.3)' : 'rgba(16, 185, 129, 0.25)'}`
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
                <div>
                  <div style={{ fontSize: '14px', fontWeight: 800 }}>
                    Status de Operação da Conta: {isPaused ? '⏸️ Conta Pausada' : '🟢 Conta Ativa'}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                    Contas pausadas ficam impedidas de autenticar, sincronizar e usar IA até reativação.
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setIsPaused(!isPaused)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '6px',
                    padding: '8px 14px', borderRadius: '10px', fontSize: '12px', fontWeight: 700,
                    backgroundColor: isPaused ? '#10b981' : 'rgba(245, 158, 11, 0.2)',
                    color: isPaused ? '#042f1a' : '#fbbf24',
                    border: 'none'
                  }}
                >
                  {isPaused ? <><PlayCircle size={15} /> Marcar como Ativa</> : <><PauseCircle size={15} /> Marcar como Pausada</>}
                </button>
              </div>
              {isPaused && (
                <div style={{ marginTop: '12px' }}>
                  <label style={labelStyle}>Motivo da Pausa:</label>
                  <input
                    type="text"
                    value={pausedReason}
                    onChange={(e) => setPausedReason(e.target.value)}
                    placeholder="Ex: Pagamento pendente ou revisão administrativa"
                    style={inputStyle}
                  />
                </div>
              )}
            </div>

            {/* Individual AI Kill Switch & Quota */}
            <div style={{
              padding: '16px', borderRadius: '14px',
              backgroundColor: 'rgba(15, 23, 42, 0.75)',
              border: '1px solid var(--border-color)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap', marginBottom: '14px' }}>
                <div>
                  <div style={{ fontSize: '14px', fontWeight: 800 }}>
                    Acesso Individual ao Gemini AI: {aiBlocked ? '🚫 Bloqueado para este usuário' : '✨ Liberado'}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                    Bloqueia somente as chamadas de IA deste usuário (ele continua usando TACO offline e sync normal).
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setAiBlocked(!aiBlocked)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '6px',
                    padding: '8px 14px', borderRadius: '10px', fontSize: '12px', fontWeight: 700,
                    backgroundColor: aiBlocked ? 'rgba(16, 185, 129, 0.2)' : 'rgba(239, 68, 68, 0.2)',
                    color: aiBlocked ? '#34d399' : '#f87171',
                    border: '1px solid rgba(255,255,255,0.1)'
                  }}
                >
                  <ShieldAlert size={15} />
                  {aiBlocked ? 'Liberar IA para Usuário' : 'Bloquear IA deste Usuário'}
                </button>
              </div>

              <div>
                <label style={labelStyle}>
                  Cota Máxima Diária de Chamadas IA (hoje: {summary.todayAiRequests} realizadas • total histórico: {summary.totalAiRequests})
                </label>
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                  <input
                    type="number"
                    min={0}
                    value={dailyAiLimit}
                    onChange={(e) => setDailyAiLimit(e.target.value)}
                    placeholder="Ilimitado (0 ou vazio)"
                    style={{ ...inputStyle, maxWidth: '220px' }}
                  />
                  {[
                    { label: 'Ilimitado', val: '' },
                    { label: '10 / dia', val: '10' },
                    { label: '25 / dia', val: '25' },
                    { label: '50 / dia', val: '50' },
                    { label: '100 / dia', val: '100' }
                  ].map((preset) => (
                    <button
                      key={preset.label}
                      type="button"
                      onClick={() => setDailyAiLimit(preset.val)}
                      style={{
                        padding: '7px 11px', borderRadius: '8px', fontSize: '11px', fontWeight: 700,
                        backgroundColor: dailyAiLimit === preset.val ? 'rgba(59, 130, 246, 0.25)' : 'rgba(255, 255, 255, 0.06)',
                        color: dailyAiLimit === preset.val ? '#60a5fa' : 'var(--text-secondary)',
                        border: '1px solid rgba(255, 255, 255, 0.1)'
                      }}
                    >
                      {preset.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button
                type="button"
                disabled={saving}
                onClick={handleSaveGovernance}
                style={{
                  display: 'flex', alignItems: 'center', gap: '8px',
                  padding: '10px 20px', borderRadius: '10px', backgroundColor: '#3b82f6',
                  border: 'none', color: '#fff', fontWeight: 800, fontSize: '13px'
                }}
              >
                <Save size={15} />
                <span>{saving ? 'Aplicando...' : 'Aplicar Regras de Governança'}</span>
              </button>
            </div>
          </div>
        )}

        {/* TAB 3: CLOUD ACTIVITY INSPECTOR */}
        {tab === 'activity' && (
          <div>
            {/* Counters Grid */}
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))',
              gap: '10px',
              marginBottom: '18px'
            }}>
              {[
                { label: 'Refeições', val: summary.foodsCount },
                { label: 'Treinos', val: summary.workoutsCount },
                { label: 'Fichas', val: summary.routinesCount },
                { label: 'Água', val: summary.watersCount },
                { label: 'Pesagens', val: summary.weightsCount },
                { label: 'Análises IA', val: summary.analysesCount }
              ].map((item) => (
                <div key={item.label} style={{
                  padding: '10px 12px', borderRadius: '10px', textAlign: 'center',
                  backgroundColor: 'rgba(15, 23, 42, 0.7)', border: '1px solid var(--border-color)'
                }}>
                  <div style={{ fontSize: '18px', fontWeight: 800 }}>{item.val ?? 0}</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{item.label}</div>
                </div>
              ))}
            </div>

            {loading ? (
              <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                Carregando registros recentes do Supabase...
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '14px' }}>
                {/* Recent Meals */}
                <div style={{
                  padding: '14px', borderRadius: '12px',
                  backgroundColor: 'rgba(15, 23, 42, 0.65)', border: '1px solid var(--border-color)'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: 800, marginBottom: '10px', color: '#34d399' }}>
                    <Utensils size={14} /> Últimas Refeições ({detail?.recentFoods.length || 0})
                  </div>
                  {!detail?.recentFoods.length ? (
                    <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Nenhuma refeição sincronizada.</p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '7px', maxHeight: '210px', overflowY: 'auto' }}>
                      {detail.recentFoods.map((f) => (
                        <div key={f.id} style={{
                          display: 'flex', justifyContent: 'space-between', fontSize: '12px',
                          paddingBottom: '6px', borderBottom: '1px solid rgba(255,255,255,0.05)'
                        }}>
                          <span style={{ fontWeight: 600 }}>{f.name}</span>
                          <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>
                            {Math.round(f.calories || 0)} kcal
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Recent Workouts & AI Prompts */}
                <div style={{
                  padding: '14px', borderRadius: '12px',
                  backgroundColor: 'rgba(15, 23, 42, 0.65)', border: '1px solid var(--border-color)'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: 800, marginBottom: '10px', color: '#60a5fa' }}>
                    <Dumbbell size={14} /> Últimos Treinos ({detail?.recentWorkouts.length || 0})
                  </div>
                  {!detail?.recentWorkouts.length ? (
                    <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '12px' }}>Nenhum treino sincronizado.</p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '7px', maxHeight: '95px', overflowY: 'auto', marginBottom: '12px' }}>
                      {detail.recentWorkouts.map((w) => (
                        <div key={w.id} style={{
                          display: 'flex', justifyContent: 'space-between', fontSize: '12px',
                          paddingBottom: '5px', borderBottom: '1px solid rgba(255,255,255,0.05)'
                        }}>
                          <span style={{ fontWeight: 600 }}>{w.title}</span>
                          <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>
                            {w.durationMinutes || 0} min • {Math.round(w.caloriesBurned || 0)} kcal
                          </span>
                        </div>
                      ))}
                    </div>
                  )}

                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: 800, marginBottom: '8px', color: '#a78bfa' }}>
                    <Cpu size={14} /> Últimas Chamadas IA ({detail?.recentAiLogs.length || 0})
                  </div>
                  {!detail?.recentAiLogs.length ? (
                    <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Nenhuma chamada à IA registrada.</p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', maxHeight: '95px', overflowY: 'auto' }}>
                      {detail.recentAiLogs.map((l) => (
                        <div key={l.id} style={{
                          fontSize: '11px', paddingBottom: '5px', borderBottom: '1px solid rgba(255,255,255,0.05)'
                        }}>
                          <span className="font-mono" style={{ color: '#c4b5fd' }}>{l.endpoint}</span>:{' '}
                          <span style={{ color: 'var(--text-secondary)' }}>{l.promptSummary}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
