import React, { useState } from 'react';

interface KillSwitchCardProps {
  isBlocked: boolean;
  onToggle: (newState: boolean) => Promise<void>;
}

export const KillSwitchCard: React.FC<KillSwitchCardProps> = ({ isBlocked, onToggle }) => {
  const [loading, setLoading] = useState(false);

  const handleToggleClick = async () => {
    setLoading(true);
    try {
      await onToggle(!isBlocked);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="panel" aria-label="Controle global do proxy de IA">
      <div className="panel-head">
        <div>
          <h2 className="panel-title">Disponibilidade da Inteligência Artificial</h2>
          <p className="panel-subtitle">
            Controle global para habilitar ou suspender os recursos de IA no aplicativo.
          </p>
        </div>
        <span className={`pill ${isBlocked ? 'pill-neg' : 'pill-pos'}`}>
          {isBlocked ? 'Suspenso' : 'Ativo'}
        </span>
      </div>

      <div className="settings-list">
        <div className="settings-row">
          <div>
            <div className="settings-row-title">Estado do gateway Gemini</div>
            <div className="settings-row-desc">
              {isBlocked
                ? 'Todas as requisições de análise por foto, voz ou treino são recusadas no backend. A tabela TACO local continua funcionando offline no iPhone.'
                : 'O proxy está liberado para usuários Forma Pro com consentimento LGPD ativo e cota diária disponível.'}
            </div>
          </div>

          <div className="settings-row-control" style={{ alignItems: 'flex-start' }}>
            <button
              type="button"
              onClick={handleToggleClick}
              disabled={loading}
              className={isBlocked ? 'btn btn-primary' : 'btn btn-danger-outline'}
            >
              {loading
                ? 'Aplicando...'
                : isBlocked
                  ? 'Liberar requisições à API'
                  : 'Suspender uso de IA'}
            </button>
          </div>
        </div>
      </div>
    </section>
  );
};
