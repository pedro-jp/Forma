import React, { useState, useEffect } from 'react';
import { RefreshCw } from 'lucide-react';
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
  const [selectedModel, setSelectedModel] = useState(currentModel || 'gemini-3.1-flash-lite-preview');
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
              : 'Salve uma chave Gemini API válida para listar os modelos disponíveis.',
            isError: !hasValidKey
          });
        }
      } else if (showFeedback) {
        setMessage({ text: 'Não foi possível atualizar a lista de modelos.', isError: true });
      }
    } catch {
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
      return !id.includes('tts')
        && !id.includes('audio')
        && !id.includes('image')
        && !id.includes('embedding')
        && !id.includes('hydrogen')
        && !id.includes('robotics');
    });
    if (candidates.length === 0) {
      return 'gemini-3.1-flash-lite-preview';
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

    const payload: { model: string; apiKey?: string } = { model: modelToSave };
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
        setMessage({ text: data.message || 'Configuração salva com sucesso.', isError: false });
        setApiKeyInput('');
        onConfigSaved();
        fetchModels(false);
      } else {
        setMessage({ text: data.error || 'Erro ao salvar configuração.', isError: true });
      }
    } catch {
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

    const payload: { model: string; apiKey?: string } = { model: selectedModel };
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
        setMessage({ text: data.message || 'Conexão com Google Gemini confirmada.', isError: false });
      } else {
        setMessage({ text: data.message || data.error || 'Falha ao validar a credencial.', isError: true });
      }
    } catch {
      setMessage({ text: 'Tempo limite ou erro de conexão ao testar com a Google.', isError: true });
    } finally {
      setTesting(false);
    }
  };

  return (
    <section className="panel" aria-label="Configuração da API Google Gemini">
      <div className="panel-head">
        <div>
          <h2 className="panel-title">Credencial e modelo Gemini</h2>
          <p className="panel-subtitle">
            Credencial mantida com segurança no servidor para todas as operações do aplicativo.
          </p>
        </div>
        <span className={`pill ${hasValidKey ? 'pill-pos' : 'pill-warn'}`}>
          {hasValidKey ? 'Chave operacional' : 'Chave pendente'}
        </span>
      </div>

      {message && (
        <div style={{ padding: '14px 18px 0' }}>
          <div className={`notice-banner ${message.isError ? 'notice-neg' : 'notice-pos'}`}>
            <span>{message.text}</span>
          </div>
        </div>
      )}

      <div className="settings-list">
        <div className="settings-row">
          <div>
            <div className="settings-row-title">Configuração ativa</div>
            <div className="settings-row-desc">
              Credencial e identificador de modelo atualmente roteados para o aplicativo.
            </div>
          </div>
          <div className="settings-row-control">
            <div className="well" style={{ display: 'flex', flexWrap: 'wrap', gap: '18px', alignItems: 'center' }}>
              <div>
                <div className="field-label" style={{ marginBottom: '3px' }}>Chave ativa</div>
                <span className="code-chip">{activeKeyMasked}</span>
              </div>
              <div>
                <div className="field-label" style={{ marginBottom: '3px' }}>Modelo em uso</div>
                <span className="code-chip">{currentModel}</span>
              </div>
            </div>
          </div>
        </div>

        <div className="settings-row">
          <div>
            <div className="settings-row-title">Rotacionar chave da API</div>
            <div className="settings-row-desc">
              Deixe em branco para manter a chave atual ao trocar apenas o modelo. Aceita chaves do Google AI Studio (`AIzaSy...` ou `AQ...`).
            </div>
          </div>
          <div className="settings-row-control">
            <input
              type="password"
              className="input mono"
              value={apiKeyInput}
              onChange={(e) => setApiKeyInput(e.target.value)}
              placeholder="Cole a nova chave (opcional)"
              autoComplete="off"
            />
          </div>
        </div>

        <div className="settings-row">
          <div>
            <div className="settings-row-title">Seleção de modelo</div>
            <div className="settings-row-desc">
              Consulte os modelos disponíveis na conta configurada ou selecione automaticamente a opção mais econômica.
            </div>
          </div>
          <div className="settings-row-control">
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
              <select
                className="select"
                style={{ flex: '1 1 240px' }}
                value={selectedModel}
                onChange={(e) => setSelectedModel(e.target.value)}
              >
                {availableModels.length > 0 ? (
                  availableModels.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.displayName} ({m.id})
                    </option>
                  ))
                ) : (
                  <option value={selectedModel}>{selectedModel}</option>
                )}
              </select>

              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => fetchModels(true)}
                disabled={loadingModels}
              >
                <RefreshCw size={13} />
                <span>{loadingModels ? 'Atualizando...' : 'Atualizar lista'}</span>
              </button>
            </div>

            <div className="actions-row" style={{ marginTop: '4px' }}>
              <button
                type="button"
                className="btn btn-primary"
                onClick={handleSave}
                disabled={saving}
              >
                {saving ? 'Salvando...' : 'Salvar configuração'}
              </button>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={handleUseCheapest}
                disabled={saving}
              >
                Usar mais barato
              </button>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={handleTest}
                disabled={testing}
              >
                {testing ? 'Validando...' : 'Validar conexão'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
