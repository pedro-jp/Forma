import React, { useState, useEffect } from 'react';
import { Key, Cpu, Check, AlertCircle, RefreshCw, Send, Zap } from 'lucide-react';
import { GeminiModelItem } from '../types';

interface GeminiConfigCardProps {
  activeKeyMasked: string;
  hasValidKey: boolean;
  currentModel: string;
  onConfigSaved: () => void;
}

export const GeminiConfigCard: React.FC<GeminiConfigCardProps> = ({
  activeKeyMasked,
  hasValidKey,
  currentModel,
  onConfigSaved
}) => {
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [selectedModel, setSelectedModel] = useState(currentModel || 'gemini-3.1-flash-lite');
  const [availableModels, setAvailableModels] = useState<GeminiModelItem[]>([]);
  const [loadingModels, setLoadingModels] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [message, setMessage] = useState<{ text: string; isError: boolean } | null>(null);

  useEffect(() => {
    if (currentModel) {
      setSelectedModel(currentModel);
    }
    fetchModels();
  }, [currentModel]);

  const fetchModels = async (showFeedback = false) => {
    setLoadingModels(true);
    try {
      const resp = await fetch('/api/admin/models', { credentials: 'include' });
      if (resp.ok) {
        const data = await resp.json();
        if (data.models && data.models.length > 0) {
          setAvailableModels(data.models);
          if (data.currentModel) {
            setSelectedModel(data.currentModel);
          }
          if (showFeedback) {
            setMessage({ text: `Lista atualizada (${data.models.length} modelos disponíveis).`, isError: false });
          }
        } else if (showFeedback) {
          setMessage({
            text: hasValidKey
              ? 'Nenhum modelo retornado pela Google no momento.'
              : 'Salve uma chave Gemini API válida para carregar os modelos ao vivo.',
            isError: !hasValidKey
          });
        }
      } else if (showFeedback) {
        setMessage({ text: 'Não foi possível atualizar a lista de modelos.', isError: true });
      }
    } catch (err) {
      console.warn('Falha ao buscar modelos:', err);
      if (showFeedback) {
        setMessage({ text: 'Erro de conexão ao atualizar a lista de modelos.', isError: true });
      }
    } finally {
      setLoadingModels(false);
    }
  };

  const pickCheapestModel = (models: GeminiModelItem[]): string => {
    const candidates = models.filter((m) => {
      const id = m.id.toLowerCase();
      return !id.includes('tts') && !id.includes('audio') && !id.includes('image') && !id.includes('embedding');
    });
    if (candidates.length === 0) {
      return 'gemini-3.1-flash-lite';
    }
    const costRank = (id: string): number => {
      const lower = id.toLowerCase();
      if (lower.includes('flash-lite')) return 1;
      if (lower.includes('flash-8b')) return 2;
      if (lower.includes('flash')) return 3;
      return 4;
    };
    const sorted = [...candidates].sort((a, b) => costRank(a.id) - costRank(b.id));
    return sorted[0].id;
  };

  const saveModelConfig = async (modelToSave: string) => {
    setSaving(true);
    setMessage(null);

    const payload: { model: string; apiKey?: string } = {
      model: modelToSave
    };
    if (apiKeyInput.trim()) {
      payload.apiKey = apiKeyInput.trim();
    }

    try {
      const resp = await fetch('/api/admin/key', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload)
      });

      const data = await resp.json();

      if (resp.ok) {
        setMessage({ text: data.message || 'Configuração salva com sucesso!', isError: false });
        setApiKeyInput('');
        onConfigSaved();
        fetchModels(false);
      } else {
        setMessage({ text: data.error || 'Erro ao salvar configuração.', isError: true });
      }
    } catch (err: any) {
      setMessage({ text: 'Erro de conexão ao salvar configuração.', isError: true });
    } finally {
      setSaving(false);
    }
  };

  const handleSave = async () => {
    await saveModelConfig(selectedModel);
  };

  const handleUseCheapest = async () => {
    const cheapest = pickCheapestModel(availableModels);
    setSelectedModel(cheapest);
    await saveModelConfig(cheapest);
  };

  const handleTest = async () => {
    setTesting(true);
    setMessage(null);

    const payload: { model: string; apiKey?: string } = {
      model: selectedModel
    };
    if (apiKeyInput.trim()) {
      payload.apiKey = apiKeyInput.trim();
    }

    try {
      const resp = await fetch('/api/admin/test-key', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload)
      });

      const data = await resp.json();

      if (resp.ok) {
        setMessage({ text: data.message || 'Ping realizado com sucesso!', isError: false });
      } else {
        setMessage({ text: data.message || data.error || 'Falha ao testar chave.', isError: true });
      }
    } catch (err: any) {
      setMessage({ text: 'Tempo limite ou erro de conexão ao testar com a Google.', isError: true });
    } finally {
      setTesting(false);
    }
  };

  return (
    <div className="glass-card" style={{ padding: '24px', marginBottom: '28px' }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '12px',
        marginBottom: '20px',
        paddingBottom: '16px',
        borderBottom: '1px solid var(--border-color)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '42px',
            height: '42px',
            borderRadius: '12px',
            backgroundColor: 'rgba(245, 158, 11, 0.15)',
            color: '#f59e0b',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <Key size={22} />
          </div>
          <div>
            <h3 style={{ fontSize: '18px', fontWeight: 800 }}>Configuração da Chave & Modelo Gemini AI</h3>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
              Centralizada no backend. O app iOS nunca armazena nem expõe essa credencial.
            </p>
          </div>
        </div>

        <span style={{
          padding: '4px 12px',
          borderRadius: '999px',
          fontSize: '12px',
          fontWeight: 700,
          backgroundColor: hasValidKey ? 'rgba(16, 185, 129, 0.15)' : 'rgba(245, 158, 11, 0.15)',
          color: hasValidKey ? '#34d399' : '#fbbf24',
          border: `1px solid ${hasValidKey ? 'rgba(16, 185, 129, 0.3)' : 'rgba(245, 158, 11, 0.3)'}`
        }}>
          {hasValidKey ? '🟢 Chave Operacional' : '🟡 Chave Não Configurada'}
        </span>
      </div>

      {message && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          padding: '12px 16px',
          borderRadius: '12px',
          marginBottom: '20px',
          fontSize: '13px',
          backgroundColor: message.isError ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
          border: `1px solid ${message.isError ? 'rgba(239, 68, 68, 0.3)' : 'rgba(16, 185, 129, 0.3)'}`,
          color: message.isError ? '#f87171' : '#34d399'
        }}>
          {message.isError ? <AlertCircle size={18} /> : <Check size={18} />}
          <span>{message.text}</span>
        </div>
      )}

      {/* Current values banner */}
      <div style={{
        backgroundColor: 'rgba(15, 23, 42, 0.6)',
        borderRadius: '12px',
        padding: '14px 18px',
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '24px',
        marginBottom: '20px',
        border: '1px solid var(--border-color)',
        fontSize: '13px'
      }}>
        <div>
          <span style={{ color: 'var(--text-muted)', marginRight: '8px' }}>Chave Ativa:</span>
          <code style={{
            backgroundColor: 'rgba(0, 0, 0, 0.4)',
            padding: '3px 8px',
            borderRadius: '6px',
            color: '#38bdf8',
            fontSize: '12px'
          }}>
            {activeKeyMasked}
          </code>
        </div>
        <div>
          <span style={{ color: 'var(--text-muted)', marginRight: '8px' }}>Modelo Atual:</span>
          <code style={{
            backgroundColor: 'rgba(0, 0, 0, 0.4)',
            padding: '3px 8px',
            borderRadius: '6px',
            color: '#a78bfa',
            fontSize: '12px'
          }}>
            {currentModel || 'gemini-3.1-flash-lite'}
          </code>
        </div>
      </div>

      {/* Inputs Form */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: '18px',
        marginBottom: '20px'
      }}>
        <div>
          <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
            Nova Chave Gemini API (deixe vazio para manter atual):
          </label>
          <input
            type="password"
            value={apiKeyInput}
            onChange={(e) => setApiKeyInput(e.target.value)}
            placeholder="Cole sua chave AIzaSy..."
            style={{
              width: '100%',
              padding: '12px 14px',
              borderRadius: '12px',
              backgroundColor: 'rgba(15, 23, 42, 0.8)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              color: '#fff',
              fontSize: '14px'
            }}
          />
        </div>

        <div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
            <label style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)' }}>
              Modelo Gemini:
            </label>
            <button
              onClick={() => fetchModels(true)}
              disabled={loadingModels}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--accent-blue)',
                fontSize: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '4px'
              }}
            >
              <RefreshCw size={12} className={loadingModels ? 'live-pulse' : ''} />
              <span>Atualizar Lista</span>
            </button>
          </div>
          <select
            value={selectedModel}
            onChange={(e) => setSelectedModel(e.target.value)}
            style={{
              width: '100%',
              padding: '12px 14px',
              borderRadius: '12px',
              backgroundColor: 'rgba(15, 23, 42, 0.8)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              color: '#fff',
              fontSize: '14px'
            }}
          >
            {availableModels.length > 0 ? (
              availableModels.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.displayName || m.id}
                </option>
              ))
            ) : (
              <>
                <option value="gemini-3.1-flash-lite">Gemini 3.1 Flash Lite (Padrão Recomendado - Menor Latência)</option>
                <option value="gemini-3.6-flash">Gemini 3.6 Flash (Equilíbrio de Velocidade &amp; Capacidade)</option>
                <option value="gemini-3.8-flash">Gemini 3.8 Flash (Geração Flash Mais Recente)</option>
                <option value="gemini-3.8-pro">Gemini 3.8 Pro (Máxima Precisão e Raciocínio Profundo)</option>
              </>
            )}
          </select>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
        <button
          onClick={handleSave}
          disabled={saving}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '12px 20px',
            borderRadius: '12px',
            backgroundColor: '#10b981',
            color: '#042f1a',
            fontWeight: 700,
            fontSize: '14px',
            border: 'none',
            boxShadow: '0 4px 14px rgba(16, 185, 129, 0.3)'
          }}
        >
          <Check size={16} />
          <span>{saving ? 'Salvando...' : 'Salvar Configuração'}</span>
        </button>

        <button
          onClick={handleUseCheapest}
          disabled={saving}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '12px 18px',
            borderRadius: '12px',
            backgroundColor: 'rgba(59, 130, 246, 0.16)',
            color: '#93c5fd',
            fontWeight: 700,
            fontSize: '14px',
            border: '1px solid rgba(59, 130, 246, 0.35)'
          }}
        >
          <Zap size={16} />
          <span>Usar Mais Barato</span>
        </button>

        <button
          onClick={handleTest}
          disabled={testing}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '12px 20px',
            borderRadius: '12px',
            backgroundColor: 'transparent',
            color: '#fff',
            fontWeight: 600,
            fontSize: '14px',
            border: '1px solid rgba(255, 255, 255, 0.2)'
          }}
        >
          <Send size={16} />
          <span>{testing ? 'Testando Ping...' : 'Testar Conexão com Gemini'}</span>
        </button>
      </div>
    </div>
  );
};
