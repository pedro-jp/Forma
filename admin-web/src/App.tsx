import React, { useState, useEffect, useRef } from 'react';
import {
  Sparkles,
  LogOut,
  Wifi,
  WifiOff,
  RefreshCw,
  LayoutDashboard,
  Wallet,
  CreditCard,
  Ticket,
  Users,
  ShieldCheck
} from 'lucide-react';
import {
  AdminAuditItem,
  AdminOverview,
  AiLogSummary,
  BillingDashboardData,
  SecurityIncidentItem,
  WebSocketEvent
} from './types';
import { LoginModal } from './components/LoginModal';
import { KillSwitchCard } from './components/KillSwitchCard';
import { StatsCards } from './components/StatsCards';
import { GeminiConfigCard } from './components/GeminiConfigCard';
import { LiveActivityFeed } from './components/LiveActivityFeed';
import { UsersTable } from './components/UsersTable';
import { BillingDashboardTab } from './components/BillingDashboardTab';
import { SubscriptionsManagerTab } from './components/SubscriptionsManagerTab';
import { CouponsManagerTab } from './components/CouponsManagerTab';
import { WebhooksSecurityTab } from './components/WebhooksSecurityTab';

type AdminTabId = 'overview' | 'finance' | 'subscriptions' | 'coupons' | 'users' | 'webhooks';

const EMPTY_BILLING: BillingDashboardData = {
  mrrCents: 0,
  arrCents: 0,
  paidRevenue30dCents: 0,
  totalNetRevenueCents: 0,
  pendingRevenueCents: 0,
  refundedTotalCents: 0,
  activeSubscribersCount: 0,
  trialingCount: 0,
  pastDueDay1Count: 0,
  pastDueDay2Count: 0,
  pastDueDay3Count: 0,
  pausedCount: 0,
  canceledCount: 0,
  couponRedemptionsCount: 0,
  overdueActionPolicy: 'PAUSE',
  graceDays: 3,
  dailyRevenueLast14Days: [],
  plans: [],
  subscriptions: [],
  coupons: [],
  recentRedemptions: [],
  recentEntries: [],
  recentDunningNotices: [],
  recentWebhooks: [],
  webhookConfig: {
    endpointPath: '/api/webhooks/billing',
    signatureHeader: 'X-Forma-Webhook-Signature',
    maskedSecret: 'Carregando...'
  }
};

export const App: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);
  const [adminUsername, setAdminUsername] = useState('admin');
  const [wsConnected, setWsConnected] = useState(false);
  const [activeTab, setActiveTab] = useState<AdminTabId>('overview');
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
  const [billing, setBilling] = useState<BillingDashboardData>(EMPTY_BILLING);
  const [auditLogs, setAuditLogs] = useState<AdminAuditItem[]>([]);
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
      const [resp, billingResp, auditResp, incResp] = await Promise.all([
        fetch('/api/admin/overview', { credentials: 'include' }),
        fetch('/api/admin/billing/dashboard', { credentials: 'include' }),
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
      if (billingResp.ok) {
        const bData = await billingResp.json();
        setBilling(bData);
      }
      if (auditResp.ok) {
        const auditData = await auditResp.json();
        if (Array.isArray(auditData)) setAuditLogs(auditData.slice(0, 15));
      }
      if (incResp.ok) {
        const incData = await incResp.json();
        if (Array.isArray(incData)) setIncidents(incData.slice(0, 10));
      }
    } catch (e) {
      console.warn('Erro ao carregar dados do painel:', e);
    }
  };

  const connectWebSocket = () => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) return;

    let wsHost = window.location.host;
    if (wsHost.includes('5173')) {
      wsHost = window.location.hostname + ':8085';
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${wsHost}/ws/admin`;

    try {
      const socket = new WebSocket(wsUrl);
      wsRef.current = socket;

      socket.onopen = () => setWsConnected(true);

      socket.onmessage = (event) => {
        try {
          if (event.data === 'pong') return;
          const msg: WebSocketEvent = JSON.parse(event.data);
          handleWebSocketMessage(msg);
        } catch (e) {}
      };

      socket.onclose = () => {
        setWsConnected(false);
        wsRef.current = null;
        reconnectTimeoutRef.current = setTimeout(() => {
          if (isAuthenticated) connectWebSocket();
        }, 3000);
      };

      socket.onerror = () => socket.close();
    } catch (err) {}
  };

  const handleWebSocketMessage = (event: WebSocketEvent) => {
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
          setOverview((prev) => ({ ...prev, aiBlocked: event.data.isBlocked }));
        }
        break;
      }

      case 'USER_ACTIVITY': {
        if (event.data && typeof event.data.totalUsers === 'number') {
          setOverview((prev) => ({ ...prev, totalUsers: event.data.totalUsers }));
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

      case 'BILLING_UPDATED': {
        loadOverview();
        break;
      }
    }
  };

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
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', color: 'var(--text-secondary)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <RefreshCw size={18} className="live-pulse" />
          <span>Verificando sessão segura...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <LoginModal
        onLoginSuccess={(user) => {
          setAdminUsername(user);
          setIsAuthenticated(true);
        }}
      />
    );
  }

  const overdueTotal = billing.pastDueDay1Count + billing.pastDueDay2Count + billing.pastDueDay3Count;

  const navItems: { id: AdminTabId; label: string; icon: React.ReactNode; badge?: number; badgeColor?: string }[] = [
    { id: 'overview', label: 'Visão Geral & IA', icon: <LayoutDashboard size={15} /> },
    { id: 'finance', label: 'Receita & Entradas', icon: <Wallet size={15} /> },
    {
      id: 'subscriptions',
      label: 'Inscrições & 3 Dias',
      icon: <CreditCard size={15} />,
      badge: overdueTotal > 0 ? overdueTotal : undefined,
      badgeColor: '#fbbf24'
    },
    { id: 'coupons', label: 'Cupons & Período Grátis', icon: <Ticket size={15} />, badge: billing.coupons.filter((c) => c.active).length || undefined },
    { id: 'users', label: 'Usuários', icon: <Users size={15} />, badge: overview.totalUsers || undefined },
    { id: 'webhooks', label: 'Webhooks & Segurança', icon: <ShieldCheck size={15} /> }
  ];

  return (
    <div style={{ minHeight: '100vh', padding: '20px 24px 48px' }}>
      <div style={{ maxWidth: '1280px', margin: '0 auto' }}>
        {/* Top Navbar */}
        <header style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '16px',
          marginBottom: '20px',
          paddingBottom: '16px',
          borderBottom: '1px solid var(--border-color)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              width: '40px',
              height: '40px',
              borderRadius: '11px',
              backgroundColor: '#10b981',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#042f1a'
            }}>
              <Sparkles size={21} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <h1 style={{ fontSize: '19px', fontWeight: 800, letterSpacing: '-0.4px' }}>Forma Live Ops</h1>
                <span style={{
                  backgroundColor: 'rgba(16, 185, 129, 0.12)',
                  border: '1px solid rgba(16, 185, 129, 0.25)',
                  padding: '2px 7px',
                  borderRadius: '6px',
                  fontSize: '10px',
                  fontWeight: 700,
                  color: '#34d399'
                }}>PRODUÇÃO BLINDADA</span>
              </div>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Operações, Faturamento, Régua de Cobrança &amp; Segurança
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '12px', fontWeight: 600 }}>
              <a href="/" style={{ color: 'var(--text-secondary)', textDecoration: 'none' }}>Portal</a>
              <a href="/docs" style={{ color: '#34d399', textDecoration: 'none' }}>Docs</a>
              <a href="/privacy" style={{ color: 'var(--text-secondary)', textDecoration: 'none' }}>LGPD</a>
              <a href="/terms" style={{ color: 'var(--text-secondary)', textDecoration: 'none' }}>Termos</a>
            </div>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '7px',
              padding: '6px 12px',
              borderRadius: '999px',
              fontSize: '12px',
              fontWeight: 700,
              backgroundColor: wsConnected ? 'rgba(16, 185, 129, 0.12)' : 'rgba(239, 68, 68, 0.12)',
              color: wsConnected ? '#34d399' : '#f87171',
              border: `1px solid ${wsConnected ? 'rgba(16, 185, 129, 0.25)' : 'rgba(239, 68, 68, 0.25)'}`
            }}>
              {wsConnected ? <Wifi size={13} /> : <WifiOff size={13} />}
              <span>{wsConnected ? 'Tempo Real' : 'Reconectando...'}</span>
            </div>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '6px 12px',
              borderRadius: '10px',
              backgroundColor: 'rgba(255, 255, 255, 0.05)',
              border: '1px solid var(--border-color)',
              fontSize: '13px',
              fontWeight: 600
            }}>
              <span>{adminUsername}</span>
              <button
                onClick={handleLogout}
                title="Sair do Painel"
                style={{ background: 'none', border: 'none', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', padding: '2px' }}
              >
                <LogOut size={15} />
              </button>
            </div>
          </div>
        </header>

        {/* Command Navigation Bar */}
        <nav className="nav-tabs" aria-label="Navegação administrativa">
          {navItems.map((item) => {
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`nav-tab-btn ${isActive ? 'is-active' : ''}`}
              >
                {item.icon}
                <span>{item.label}</span>
                {item.badge !== undefined && (
                  <span
                    className="tabular-nums"
                    style={{
                      padding: '1px 6px',
                      borderRadius: '999px',
                      fontSize: '11px',
                      fontWeight: 700,
                      backgroundColor: item.badgeColor ? 'rgba(245, 158, 11, 0.2)' : 'rgba(255,255,255,0.08)',
                      color: item.badgeColor || 'var(--text-secondary)'
                    }}
                  >
                    {item.badge}
                  </span>
                )}
              </button>
            );
          })}
        </nav>

        {/* Active Tab View */}
        {activeTab === 'overview' && (
          <>
            <KillSwitchCard isBlocked={overview.aiBlocked} onToggle={handleToggleKillSwitch} />
            <StatsCards
              totalUsers={overview.totalUsers}
              totalFoods={overview.totalFoods}
              totalWorkouts={overview.totalWorkouts}
              totalAiRequests={overview.totalAiRequests}
              lastUpdatedKey={lastUpdatedCard}
            />
            <GeminiConfigCard
              activeKeyMasked={overview.activeApiKeyMasked}
              hasValidKey={overview.hasValidApiKey}
              currentModel={overview.geminiModel}
              onConfigSaved={loadOverview}
            />
            <LiveActivityFeed logs={overview.recentLogs} connectedClientsCount={wsConnected ? 1 : 0} />
          </>
        )}

        {activeTab === 'finance' && (
          <BillingDashboardTab data={billing} users={overview.recentUsers} onRefresh={loadOverview} />
        )}

        {activeTab === 'subscriptions' && (
          <SubscriptionsManagerTab data={billing} users={overview.recentUsers} onRefresh={loadOverview} />
        )}

        {activeTab === 'coupons' && (
          <CouponsManagerTab data={billing} onRefresh={loadOverview} />
        )}

        {activeTab === 'users' && (
          <UsersTable users={overview.recentUsers} onRefresh={loadOverview} />
        )}

        {activeTab === 'webhooks' && (
          <WebhooksSecurityTab
            data={billing}
            users={overview.recentUsers}
            auditLogs={auditLogs}
            incidents={incidents}
            onRefresh={loadOverview}
          />
        )}
      </div>
    </div>
  );
};
