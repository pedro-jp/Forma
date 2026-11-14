import React, { useEffect, useRef, useState } from 'react';
import {
  Activity,
  CreditCard,
  LogOut,
  Menu,
  RefreshCw,
  ShieldCheck,
  Sliders,
  Ticket,
  Users,
  Wallet
} from 'lucide-react';
import {
  AdminAuditItem,
  AdminBootstrapResponse,
  AdminOverview,
  AiLogSummary,
  BillingDashboardData,
  SecurityIncidentItem,
  WebSocketEvent
} from './types';
import { formatTimestampMsBR } from './components/billingFormat';
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

type AdminTabId = 'overview' | 'finance' | 'subscriptions' | 'coupons' | 'users' | 'ai_settings' | 'webhooks';

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

const BOOTSTRAP_CACHE_KEY = 'forma_admin_bootstrap_v1';
const BROADCAST_CHANNEL_NAME = 'forma_admin_realtime_v1';

function readCachedBootstrap(): AdminBootstrapResponse | null {
  try {
    const raw = window.sessionStorage.getItem(BOOTSTRAP_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AdminBootstrapResponse;
    if (!parsed || !parsed.overview) return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeCachedBootstrap(data: AdminBootstrapResponse) {
  try {
    window.sessionStorage.setItem(BOOTSTRAP_CACHE_KEY, JSON.stringify(data));
  } catch {}
}

function clearCachedBootstrap() {
  try {
    window.sessionStorage.removeItem(BOOTSTRAP_CACHE_KEY);
  } catch {}
}

const TAB_TITLES: Record<AdminTabId, string> = {
  overview: 'Visão geral',
  finance: 'Receita e caixa',
  subscriptions: 'Assinaturas e régua',
  coupons: 'Cupons e períodos grátis',
  users: 'Usuários e acessos',
  ai_settings: 'IA e modelo Gemini',
  webhooks: 'Webhooks e segurança'
};

export const App: React.FC = () => {
  const initialCache = useRef<AdminBootstrapResponse | null>(readCachedBootstrap());
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(initialCache.current ? true : null);
  const [adminUsername, setAdminUsername] = useState(initialCache.current?.username || 'Administrador');
  const [wsConnected, setWsConnected] = useState(false);
  const [activeTab, setActiveTab] = useState<AdminTabId>('overview');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [lastUpdatedCard, setLastUpdatedCard] = useState<string | undefined>(undefined);
  const [lastSyncTime, setLastSyncTime] = useState<string>(() =>
    initialCache.current?.serverTimestampUtc ? formatTimestampMsBR(initialCache.current.serverTimestampUtc) : '—'
  );
  const [refreshing, setRefreshing] = useState(false);

  const [overview, setOverview] = useState<AdminOverview>(
    initialCache.current?.overview || {
      totalUsers: 0,
      totalFoods: 0,
      totalWorkouts: 0,
      totalAiRequests: 0,
      activeApiKeyMasked: 'Carregando...',
      hasValidApiKey: false,
      geminiModel: 'gemini-3.1-flash-lite-preview',
      aiBlocked: false,
      recentUsers: [],
      recentLogs: []
    }
  );
  const [billing, setBilling] = useState<BillingDashboardData>(initialCache.current?.billing || EMPTY_BILLING);
  const [auditLogs, setAuditLogs] = useState<AdminAuditItem[]>(initialCache.current?.auditLogs || []);
  const [incidents, setIncidents] = useState<SecurityIncidentItem[]>(initialCache.current?.incidents || []);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pingIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const debounceRefreshRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const broadcastRef = useRef<BroadcastChannel | null>(null);
  const isAuthRef = useRef<boolean | null>(isAuthenticated);
  const wsHandlerRef = useRef<(event: WebSocketEvent) => void>(() => {});

  useEffect(() => {
    isAuthRef.current = isAuthenticated;
  }, [isAuthenticated]);

  const applyBootstrapPayload = (data: AdminBootstrapResponse, broadcastToTabs = false) => {
    if (data.username) setAdminUsername(data.username);
    if (data.overview) setOverview(data.overview);
    if (data.billing) setBilling(data.billing);
    if (Array.isArray(data.auditLogs)) setAuditLogs(data.auditLogs.slice(0, 25));
    if (Array.isArray(data.incidents)) setIncidents(data.incidents.slice(0, 15));
    writeCachedBootstrap(data);
    stampSync();
    if (broadcastToTabs) {
      broadcastRef.current?.postMessage({ type: 'BOOTSTRAP_SYNC', payload: data });
    }
  };

  useEffect(() => {
    if (typeof BroadcastChannel !== 'undefined') {
      const channel = new BroadcastChannel(BROADCAST_CHANNEL_NAME);
      broadcastRef.current = channel;
      channel.onmessage = (ev) => {
        if (!ev?.data) return;
        if (ev.data.type === 'BOOTSTRAP_SYNC' && ev.data.payload) {
          applyBootstrapPayload(ev.data.payload as AdminBootstrapResponse, false);
        } else if (ev.data.type === 'WS_EVENT' && ev.data.payload) {
          wsHandlerRef.current(ev.data.payload as WebSocketEvent);
        }
      };
    }

    fetchBootstrap(Boolean(initialCache.current));

    return () => {
      if (wsRef.current) wsRef.current.close();
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (pingIntervalRef.current) clearInterval(pingIntervalRef.current);
      if (debounceRefreshRef.current) clearTimeout(debounceRefreshRef.current);
      broadcastRef.current?.close();
    };
  }, []);

  useEffect(() => {
    if (isAuthenticated) {
      connectWebSocket();
    } else if (isAuthenticated === false && wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
  }, [isAuthenticated]);

  const stampSync = () => {
    setLastSyncTime(formatTimestampMsBR(new Date().toISOString()));
  };

  const fetchBootstrap = async (silent = false, broadcastToTabs = false) => {
    if (!silent) setRefreshing(true);
    try {
      const resp = await fetch('/api/admin/bootstrap', {
        credentials: 'include',
        cache: 'no-store'
      });
      if (resp.status === 401) {
        clearCachedBootstrap();
        setIsAuthenticated(false);
        return;
      }
      if (resp.ok) {
        const data: AdminBootstrapResponse = await resp.json();
        setIsAuthenticated(true);
        applyBootstrapPayload(data, broadcastToTabs);
        return;
      }
      await loadOverviewFallback();
    } catch {
      if (!isAuthRef.current) {
        setIsAuthenticated(false);
      }
    } finally {
      if (!silent) setRefreshing(false);
    }
  };

  const loadOverviewFallback = async () => {
    const [resp, billingResp, auditResp, incResp] = await Promise.all([
      fetch('/api/admin/overview', { credentials: 'include', cache: 'no-store' }),
      fetch('/api/admin/billing/dashboard', { credentials: 'include', cache: 'no-store' }),
      fetch('/api/admin/audit-logs', { credentials: 'include', cache: 'no-store' }),
      fetch('/api/admin/incidents', { credentials: 'include', cache: 'no-store' })
    ]);
    if (resp.status === 401) {
      clearCachedBootstrap();
      setIsAuthenticated(false);
      return;
    }
    if (resp.ok) {
      const data = await resp.json();
      setIsAuthenticated(true);
      setOverview(data);
    }
    if (billingResp.ok) setBilling(await billingResp.json());
    if (auditResp.ok) {
      const auditData = await auditResp.json();
      if (Array.isArray(auditData)) setAuditLogs(auditData.slice(0, 25));
    }
    if (incResp.ok) {
      const incData = await incResp.json();
      if (Array.isArray(incData)) setIncidents(incData.slice(0, 15));
    }
    stampSync();
  };

  const loadOverview = async () => {
    await fetchBootstrap(false, true);
  };

  const scheduleRealtimeRefresh = () => {
    if (debounceRefreshRef.current) clearTimeout(debounceRefreshRef.current);
    debounceRefreshRef.current = setTimeout(() => {
      fetchBootstrap(true, true);
    }, 60);
  };

  const handleLogout = async () => {
    clearCachedBootstrap();
    try {
      await fetch('/api/admin/logout', { method: 'POST', credentials: 'include' });
    } catch {}
    setIsAuthenticated(false);
    if (wsRef.current) {
      wsRef.current.close();
    }
  };

  const connectWebSocket = () => {
    if (wsRef.current && (wsRef.current.readyState === WebSocket.OPEN || wsRef.current.readyState === WebSocket.CONNECTING)) {
      return;
    }

    let wsHost = window.location.host;
    if (wsHost.includes('5173')) {
      wsHost = window.location.hostname + ':8085';
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${wsHost}/ws/admin`;

    try {
      const socket = new WebSocket(wsUrl);
      wsRef.current = socket;

      socket.onopen = () => {
        setWsConnected(true);
        if (pingIntervalRef.current) clearInterval(pingIntervalRef.current);
        pingIntervalRef.current = setInterval(() => {
          if (socket.readyState === WebSocket.OPEN) {
            socket.send('ping');
          }
        }, 25000);
      };

      socket.onmessage = (event) => {
        try {
          if (event.data === 'pong') return;
          const msg: WebSocketEvent = JSON.parse(event.data);
          wsHandlerRef.current(msg);
          broadcastRef.current?.postMessage({ type: 'WS_EVENT', payload: msg });
        } catch {}
      };

      socket.onclose = () => {
        setWsConnected(false);
        wsRef.current = null;
        if (pingIntervalRef.current) {
          clearInterval(pingIntervalRef.current);
          pingIntervalRef.current = null;
        }
        reconnectTimeoutRef.current = setTimeout(() => {
          if (isAuthRef.current) connectWebSocket();
        }, 800);
      };

      socket.onerror = () => socket.close();
    } catch {}
  };

  const handleWebSocketMessage = (event: WebSocketEvent) => {
    stampSync();
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
          recentLogs: [newLog, ...prev.recentLogs.filter((l) => l.id !== newLog.id).slice(0, 49)],
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
        }
        scheduleRealtimeRefresh();
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
          scheduleRealtimeRefresh();
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
        scheduleRealtimeRefresh();
        break;
      }

      case 'AUDIT_LOG_CREATED': {
        const auditData = event.data as AdminAuditItem | undefined;
        if (auditData && auditData.id) {
          setAuditLogs((prev) => [auditData, ...prev.filter((a) => a.id !== auditData.id)].slice(0, 25));
        }
        break;
      }

      case 'SECURITY_INCIDENT_CREATED': {
        const incData = event.data as SecurityIncidentItem | undefined;
        if (incData && incData.id) {
          setIncidents((prev) => [incData, ...prev.filter((i) => i.id !== incData.id)].slice(0, 15));
        }
        break;
      }
    }
  };

  wsHandlerRef.current = handleWebSocketMessage;

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
        stampSync();
      }
    } catch {}
  };

  if (isAuthenticated === null) {
    return (
      <main className="auth-viewport">
        <div style={{ fontSize: '13.5px', color: 'var(--muted)' }}>
          Verificando sessão administrativa...
        </div>
      </main>
    );
  }

  if (!isAuthenticated) {
    return (
      <LoginModal
        onLoginSuccess={(user) => {
          setAdminUsername(user);
          setIsAuthenticated(true);
          fetchBootstrap(false, true);
        }}
      />
    );
  }

  const overdueTotal = billing.pastDueDay1Count + billing.pastDueDay2Count + billing.pastDueDay3Count;
  const activeCouponsCount = billing.coupons.filter((c) => c.active && !c.exhausted && !c.expired).length;

  const navGroups: {
    groupLabel: string;
    items: { id: AdminTabId; label: string; icon: React.ReactNode; badge?: number; warn?: boolean }[];
  }[] = [
    {
      groupLabel: 'Operação',
      items: [
        { id: 'overview', label: 'Visão geral', icon: <Activity size={15} /> },
        { id: 'users', label: 'Usuários e acessos', icon: <Users size={15} />, badge: overview.totalUsers || undefined }
      ]
    },
    {
      groupLabel: 'Comercial e cobrança',
      items: [
        { id: 'finance', label: 'Receita e caixa', icon: <Wallet size={15} /> },
        {
          id: 'subscriptions',
          label: 'Assinaturas e régua',
          icon: <CreditCard size={15} />,
          badge: overdueTotal > 0 ? overdueTotal : undefined,
          warn: overdueTotal > 0
        },
        {
          id: 'coupons',
          label: 'Cupons e períodos',
          icon: <Ticket size={15} />,
          badge: activeCouponsCount || undefined
        }
      ]
    },
    {
      groupLabel: 'Governança',
      items: [
        { id: 'ai_settings', label: 'IA e modelo Gemini', icon: <Sliders size={15} /> },
        { id: 'webhooks', label: 'Webhooks e segurança', icon: <ShieldCheck size={15} /> }
      ]
    }
  ];

  const selectTab = (tabId: AdminTabId) => {
    setActiveTab(tabId);
    setSidebarOpen(false);
  };

  return (
    <div className="admin-shell">
      {sidebarOpen && <div className="drawer-backdrop" onClick={() => setSidebarOpen(false)} />}

      <aside className={`sidebar ${sidebarOpen ? 'is-open' : ''}`} aria-label="Menu do console">
        <div className="brand-lockup">
          <div className="brand-mark" aria-hidden="true">
            <svg width="15" height="15" viewBox="0 0 16 16" fill="none">
              <rect x="2" y="2" width="12" height="2.6" rx="1" fill="currentColor" />
              <rect x="2" y="6.7" width="8.5" height="2.6" rx="1" fill="currentColor" />
              <rect x="2" y="11.4" width="3.2" height="2.6" rx="1" fill="currentColor" />
            </svg>
          </div>
          <div>
            <div className="brand-title">Forma</div>
            <div className="brand-scope">Console Administrativo</div>
          </div>
        </div>

        <nav aria-label="Navegação principal">
          {navGroups.map((group) => (
            <div key={group.groupLabel} className="nav-section">
              <div className="nav-section-label">{group.groupLabel}</div>
              {group.items.map((item) => {
                const active = activeTab === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    className={`nav-item ${active ? 'is-active' : ''}`}
                    onClick={() => selectTab(item.id)}
                  >
                    <span className="nav-item-main">
                      {item.icon}
                      <span className="nav-item-label">{item.label}</span>
                    </span>
                    {item.badge !== undefined && (
                      <span className={`nav-badge ${item.warn ? 'is-warn' : ''}`}>{item.badge}</span>
                    )}
                  </button>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-links">
            <a href="/">Portal</a>
            <a href="/docs">Docs</a>
            <a href="/privacy">LGPD</a>
            <a href="/terms">Termos</a>
          </div>

          <div className="session-card">
            <div>
              <div className="session-name">{adminUsername}</div>
              <div className="session-role">Administrador</div>
            </div>
            <button
              type="button"
              className="btn-icon"
              onClick={handleLogout}
              title="Encerrar sessão"
              aria-label="Encerrar sessão"
            >
              <LogOut size={14} />
            </button>
          </div>
        </div>
      </aside>

      <div className="sheet-column">
        <header className="topbar">
          <div className="topbar-left">
            <button
              type="button"
              className="btn-icon mobile-menu-btn"
              onClick={() => setSidebarOpen((prev) => !prev)}
              aria-label="Abrir navegação"
            >
              <Menu size={16} />
            </button>
            <div className="breadcrumb">
              <span>Console</span>
              <span className="breadcrumb-sep">/</span>
              <span className="breadcrumb-current">{TAB_TITLES[activeTab]}</span>
            </div>
          </div>

          <div className="topbar-right">
            {!wsConnected && (
              <div className="ws-indicator">
                <span className="ws-dot is-offline" />
                <span>Reconectando...</span>
              </div>
            )}
            <span className="freshness-label">Sincronizado às {lastSyncTime}</span>
            <button type="button" className="btn btn-secondary btn-sm" onClick={loadOverview} disabled={refreshing}>
              <RefreshCw size={12} />
              <span>{refreshing ? 'Atualizando' : 'Atualizar'}</span>
            </button>
          </div>
        </header>

        <main className="work-sheet">
          {activeTab === 'overview' && (
            <div className="view-stack">
              <div className="title-row">
                <div>
                  <h1 className="page-title">Visão geral da operação</h1>
                  <p className="page-subtitle">
                    Acompanhamento operacional de contas, registros sincronizados e disponibilidade do sistema.
                  </p>
                </div>
                <div className="actions-row">
                  <button type="button" className="btn btn-secondary" onClick={() => selectTab('finance')}>
                    Editar preços de planos
                  </button>
                  <button type="button" className="btn btn-primary" onClick={() => selectTab('subscriptions')}>
                    Assinaturas e régua
                  </button>
                </div>
              </div>

              <StatsCards
                totalUsers={overview.totalUsers}
                totalFoods={overview.totalFoods}
                totalWorkouts={overview.totalWorkouts}
                totalAiRequests={overview.totalAiRequests}
                lastUpdatedKey={lastUpdatedCard}
              />

              <KillSwitchCard isBlocked={overview.aiBlocked} onToggle={handleToggleKillSwitch} />

              <LiveActivityFeed logs={overview.recentLogs} />
            </div>
          )}

          {activeTab === 'finance' && (
            <BillingDashboardTab data={billing} users={overview.recentUsers} onRefresh={loadOverview} />
          )}

          {activeTab === 'subscriptions' && (
            <SubscriptionsManagerTab data={billing} users={overview.recentUsers} onRefresh={loadOverview} />
          )}

          {activeTab === 'coupons' && (
            <CouponsManagerTab data={billing} users={overview.recentUsers} onRefresh={loadOverview} />
          )}

          {activeTab === 'users' && (
            <UsersTable users={overview.recentUsers} onRefresh={loadOverview} />
          )}

          {activeTab === 'ai_settings' && (
            <div className="view-stack">
              <div className="title-row">
                <div>
                  <h1 className="page-title">IA e modelo Gemini</h1>
                  <p className="page-subtitle">
                    Gestão da credencial de inteligência artificial, escolha do modelo ativo e disponibilidade do serviço.
                  </p>
                </div>
              </div>

              <KillSwitchCard isBlocked={overview.aiBlocked} onToggle={handleToggleKillSwitch} />

              <GeminiConfigCard
                activeKeyMasked={overview.activeApiKeyMasked}
                hasValidKey={overview.hasValidApiKey}
                currentModel={overview.geminiModel}
                onConfigSaved={loadOverview}
              />
            </div>
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
        </main>
      </div>
    </div>
  );
};
