import React, { useState, useEffect, useRef } from 'react';
import { Sparkles, LogOut, Wifi, WifiOff, RefreshCw, ShieldAlert, FileText } from 'lucide-react';
import { AdminOverview, WebSocketEvent, AiLogSummary } from './types';

interface AdminAuditLogItem {
  id: string;
  adminActor: string;
  actionType: string;
  targetRef?: string;
  detailSummary?: string;
  createdAtUtc: string;
}

interface SecurityIncidentItem {
  id: string;
  severity: string;
  incidentType: string;
  description: string;
  anpdNotified: boolean;
  detectedAtUtc: string;
}
import { LoginModal } from './components/LoginModal';
import { KillSwitchCard } from './components/KillSwitchCard';
import { StatsCards } from './components/StatsCards';
import { GeminiConfigCard } from './components/GeminiConfigCard';
import { LiveActivityFeed } from './components/LiveActivityFeed';
import { UsersTable } from './components/UsersTable';

export const App: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);
  const [adminUsername, setAdminUsername] = useState('admin');
  const [wsConnected, setWsConnected] = useState(false);
  const [lastUpdatedCard, setLastUpdatedCard] = useState<string | undefined>(undefined);

  const [overview, setOverview] = useState<AdminOverview>({
    totalUsers: 0,
    totalFoods: 0,
    totalWorkouts: 0,
    totalAiRequests: 0,
    activeApiKeyMasked: 'Carregando...',
    hasValidApiKey: false,
    geminiModel: 'gemini-3.1-flash-lite',
    aiBlocked: false,
    recentUsers: [],
    recentLogs: []
  });
  const [auditLogs, setAuditLogs] = useState<AdminAuditLogItem[]>([]);
  const [incidents, setIncidents] = useState<SecurityIncidentItem[]>([]);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    checkAuth();
  }, []);

  useEffect(() => {
    if (isAuthenticated) {
      loadOverview();
      connectWebSocket();
    }
    return () => {
      if (wsRef.current) {
        wsRef.current.close();
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [isAuthenticated]);

  const checkAuth = async () => {
    try {
      const resp = await fetch('/api/admin/auth-check', { credentials: 'include' });
      if (resp.ok) {
        const data = await resp.json();
        setAdminUsername(data.username || 'admin');
        setIsAuthenticated(true);
      } else {
        setIsAuthenticated(false);
      }
    } catch (e) {
      setIsAuthenticated(false);
    }
  };

  const handleLogout = async () => {
    try {
      await fetch('/api/admin/logout', { method: 'POST', credentials: 'include' });
    } catch (e) {}
    setIsAuthenticated(false);
    if (wsRef.current) {
      wsRef.current.close();
    }
  };

  const loadOverview = async () => {
    try {
      const [resp, auditResp, incResp] = await Promise.all([
        fetch('/api/admin/overview', { credentials: 'include' }),
        fetch('/api/admin/audit-logs', { credentials: 'include' }),
        fetch('/api/admin/incidents', { credentials: 'include' })
      ]);
      if (resp.ok) {
        const data = await resp.json();
        setOverview(data);
      } else if (resp.status === 401) {
        setIsAuthenticated(false);
        return;
      }
      if (auditResp.ok) {
        const auditData = await auditResp.json();
        if (Array.isArray(auditData)) setAuditLogs(auditData.slice(0, 12));
      }
      if (incResp.ok) {
        const incData = await incResp.json();
        if (Array.isArray(incData)) setIncidents(incData.slice(0, 8));
      }
    } catch (e) {
      console.warn('Erro ao carregar overview:', e);
    }
  };

  // Conexão WebSocket em Tempo Real
  const connectWebSocket = () => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) return;

    // Detecta host do backend: se estiver rodando no Vite (5173), aponta para 8085
    let wsHost = window.location.host;
    if (wsHost.includes('5173')) {
      wsHost = window.location.hostname + ':8085';
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${wsHost}/ws/admin`;

    console.log('[WebSocket] Conectando a:', wsUrl);

    try {
      const socket = new WebSocket(wsUrl);
      wsRef.current = socket;

      socket.onopen = () => {
        console.log('[WebSocket] Conexão estabelecida com sucesso!');
        setWsConnected(true);
      };

      socket.onmessage = (event) => {
        try {
          if (event.data === 'pong') return;
          const msg: WebSocketEvent = JSON.parse(event.data);
          handleWebSocketMessage(msg);
        } catch (e) {
          console.warn('[WebSocket] Mensagem não-JSON:', event.data);
        }
      };

      socket.onclose = () => {
        console.warn('[WebSocket] Conexão encerrada. Reconectando em 3s...');
        setWsConnected(false);
        wsRef.current = null;
        reconnectTimeoutRef.current = setTimeout(() => {
          if (isAuthenticated) {
            connectWebSocket();
          }
        }, 3000);
      };

      socket.onerror = (err) => {
        console.error('[WebSocket] Erro de transporte:', err);
        socket.close();
      };
    } catch (err) {
      console.error('[WebSocket] Erro ao instanciar socket:', err);
    }
  };

  // Processa eventos em tempo real transmitidos pelo backend
  const handleWebSocketMessage = (event: WebSocketEvent) => {
    console.log('[WebSocket Event]', event.type, event.data);

    switch (event.type) {
      case 'AI_REQUEST': {
        const reqData = event.data;
        if (!reqData) break;
        const newLog: AiLogSummary = {
          id: reqData.id,
          userId: reqData.userId,
          endpoint: reqData.endpoint,
          promptSummary: reqData.promptSummary,
          status: reqData.status,
          durationMs: reqData.durationMs,
          createdAt: reqData.createdAt
        };

        setOverview((prev) => ({
          ...prev,
          totalAiRequests: reqData.totalAiRequests || prev.totalAiRequests + 1,
          recentLogs: [newLog, ...prev.recentLogs.slice(0, 49)],
          recentUsers: prev.recentUsers.map((u) =>
            reqData.userId && u.id === reqData.userId
              ? {
                  ...u,
                  todayAiRequests: (u.todayAiRequests || 0) + 1,
                  totalAiRequests: (u.totalAiRequests || 0) + 1
                }
              : u
          )
        }));
        setLastUpdatedCard('ai');
        break;
      }

      case 'KILL_SWITCH_CHANGED': {
        if (event.data && typeof event.data.isBlocked === 'boolean') {
          setOverview((prev) => ({
            ...prev,
            aiBlocked: event.data.isBlocked
          }));
        }
        break;
      }

      case 'USER_ACTIVITY': {
        if (event.data && typeof event.data.totalUsers === 'number') {
          setOverview((prev) => ({
            ...prev,
            totalUsers: event.data.totalUsers
          }));
          setLastUpdatedCard('users');
          loadOverview();
        }
        break;
      }

      case 'SYNC_ACTIVITY': {
        if (event.data) {
          setOverview((prev) => ({
            ...prev,
            totalFoods: event.data.totalFoods ?? prev.totalFoods,
            totalWorkouts: event.data.totalWorkouts ?? prev.totalWorkouts
          }));
          setLastUpdatedCard(event.data.entityType === 'WORKOUT' ? 'workouts' : 'foods');
          if (
            event.data.entityType === 'ACCOUNT_RESET' ||
            event.data.entityType === 'ACCOUNT_DELETED' ||
            event.data.entityType === 'FULL_PUSH'
          ) {
            loadOverview();
          }
        }
        break;
      }

      case 'CONFIG_UPDATED': {
        if (event.data) {
          setOverview((prev) => ({
            ...prev,
            activeApiKeyMasked: event.data.activeApiKeyMasked || prev.activeApiKeyMasked,
            geminiModel: event.data.geminiModel || prev.geminiModel
          }));
        }
        break;
      }
    }
  };

  // Toggle do Kill-Switch
  const handleToggleKillSwitch = async (newState: boolean) => {
    try {
      const resp = await fetch('/api/admin/killswitch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ blocked: newState })
      });

      if (resp.ok) {
        const data = await resp.json();
        setOverview((prev) => ({ ...prev, aiBlocked: data.isBlocked }));
      }
    } catch (e) {
      console.error('Falha ao alternar kill-switch:', e);
    }
  };

  if (isAuthenticated === null) {
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        color: 'var(--text-secondary)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <RefreshCw size={20} className="live-pulse" />
          <span>Verificando autenticação...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <LoginModal onLoginSuccess={(user) => {
      setAdminUsername(user);
      setIsAuthenticated(true);
    }} />;
  }

  return (
    <div style={{ minHeight: '100vh', padding: '24px' }}>
      <div style={{ maxWidth: '1240px', margin: '0 auto' }}>
        
        {/* Top Navbar */}
        <header style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '16px',
          marginBottom: '32px',
          paddingBottom: '20px',
          borderBottom: '1px solid var(--border-color)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
            <div style={{
              width: '44px',
              height: '44px',
              borderRadius: '14px',
              backgroundColor: '#10b981',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#042f1a',
              boxShadow: '0 4px 18px rgba(16, 185, 129, 0.4)'
            }}>
              <Sparkles size={24} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <h1 style={{ fontSize: '22px', fontWeight: 800, letterSpacing: '-0.5px' }}>Forma Live Ops</h1>
                <span style={{
                  backgroundColor: 'rgba(255, 255, 255, 0.08)',
                  padding: '2px 8px',
                  borderRadius: '6px',
                  fontSize: '11px',
                  fontWeight: 700,
                  color: 'var(--text-secondary)'
                }}>Vite + React</span>
              </div>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Painel Administrativo &amp; Monitoramento em Tempo Real
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '12px', fontWeight: 600 }}>
              <a href="/" style={{ color: 'var(--text-secondary)', textDecoration: 'none' }}>Portal</a>
              <a href="/docs" style={{ color: '#34d399', textDecoration: 'none' }}>Docs</a>
              <a href="/privacy" style={{ color: 'var(--text-secondary)', textDecoration: 'none' }}>LGPD</a>
              <a href="/terms" style={{ color: 'var(--text-secondary)', textDecoration: 'none' }}>Termos</a>
            </div>
            {/* WebSocket Status Pill */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '6px 14px',
              borderRadius: '999px',
              fontSize: '12px',
              fontWeight: 700,
              backgroundColor: wsConnected ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
              color: wsConnected ? '#34d399' : '#f87171',
              border: `1px solid ${wsConnected ? 'rgba(16, 185, 129, 0.3)' : 'rgba(239, 68, 68, 0.3)'}`
            }}>
              {wsConnected ? <Wifi size={14} /> : <WifiOff size={14} />}
              <span>{wsConnected ? '🟢 WebSocket em Tempo Real' : '🔴 Reconectando WebSocket...'}</span>
            </div>

            {/* Admin Profile & Logout */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '6px 12px',
              borderRadius: '12px',
              backgroundColor: 'rgba(255, 255, 255, 0.06)',
              border: '1px solid var(--border-color)',
              fontSize: '13px',
              fontWeight: 600
            }}>
              <span>👤 {adminUsername}</span>
              <button
                onClick={handleLogout}
                title="Sair do Painel"
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-muted)',
                  display: 'flex',
                  alignItems: 'center',
                  padding: '2px'
                }}
              >
                <LogOut size={16} />
              </button>
            </div>
          </div>
        </header>

        {/* Kill Switch Card */}
        <KillSwitchCard
          isBlocked={overview.aiBlocked}
          onToggle={handleToggleKillSwitch}
        />

        {/* Stats Cards */}
        <StatsCards
          totalUsers={overview.totalUsers}
          totalFoods={overview.totalFoods}
          totalWorkouts={overview.totalWorkouts}
          totalAiRequests={overview.totalAiRequests}
          lastUpdatedKey={lastUpdatedCard}
        />

        {/* Gemini Config Card */}
        <GeminiConfigCard
          activeKeyMasked={overview.activeApiKeyMasked}
          hasValidKey={overview.hasValidApiKey}
          currentModel={overview.geminiModel}
          onConfigSaved={loadOverview}
        />

        {/* Live Activity Feed */}
        <LiveActivityFeed
          logs={overview.recentLogs}
          connectedClientsCount={wsConnected ? 1 : 0}
        />

        {/* Registered Users */}
        <UsersTable
          users={overview.recentUsers}
          onRefresh={loadOverview}
        />

        {/* LGPD Audit & Security Incidents */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: '20px',
          marginTop: '24px'
        }}>
          <section className="glass-card" style={{ padding: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
              <FileText size={18} color="#34d399" />
              <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 700 }}>Auditoria Administrativa (LGPD Art. 37)</h3>
            </div>
            {auditLogs.length === 0 ? (
              <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>Sem eventos administrativos recentes.</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '240px', overflowY: 'auto' }}>
                {auditLogs.map((item) => (
                  <div key={item.id} style={{ padding: '8px 10px', borderRadius: '10px', backgroundColor: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-color)', fontSize: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                      <span>{item.actionType}</span>
                      <span style={{ color: 'var(--text-muted)', fontWeight: 500 }}>{new Date(item.createdAtUtc).toLocaleString('pt-BR')}</span>
                    </div>
                    <div style={{ color: 'var(--text-secondary)', marginTop: '2px' }}>
                      Ator: {item.adminActor}{item.detailSummary ? ` — ${item.detailSummary}` : ''}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="glass-card" style={{ padding: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
              <ShieldAlert size={18} color="#fbbf24" />
              <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 700 }}>Incidentes de Segurança (ANPD Res. 15/2024)</h3>
            </div>
            {incidents.length === 0 ? (
              <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>Nenhum incidente de segurança registrado.</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '240px', overflowY: 'auto' }}>
                {incidents.map((inc) => (
                  <div key={inc.id} style={{ padding: '8px 10px', borderRadius: '10px', backgroundColor: 'rgba(251,191,36,0.06)', border: '1px solid rgba(251,191,36,0.2)', fontSize: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                      <span>[{inc.severity}] {inc.incidentType}</span>
                      <span style={{ color: inc.anpdNotified ? '#34d399' : '#fbbf24' }}>{inc.anpdNotified ? 'ANPD Notificada' : 'Em Triagem'}</span>
                    </div>
                    <div style={{ color: 'var(--text-secondary)', marginTop: '2px' }}>{inc.description}</div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
};
