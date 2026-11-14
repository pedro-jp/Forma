import React from 'react';
import { AiLogSummary } from '../types';
import { formatTimestampMsBR } from './billingFormat';

interface LiveActivityFeedProps {
  logs: AiLogSummary[];
}

export const LiveActivityFeed: React.FC<LiveActivityFeedProps> = ({ logs }) => {
  return (
    <section className="panel" aria-label="Telemetria de requisições de IA">
      <div className="panel-head">
        <div>
          <h2 className="panel-title">Telemetria recente de IA</h2>
          <p className="panel-subtitle">
            Registro recente de requisições processadas pelo serviço de IA.
          </p>
        </div>
        <span className="pill pill-neutral tabular-nums">{logs.length} registro(s)</span>
      </div>

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Horário</th>
              <th>Endpoint</th>
              <th>Resumo protegido (LGPD)</th>
              <th>Status</th>
              <th className="num">Latência</th>
            </tr>
          </thead>
          <tbody>
            {logs.length === 0 ? (
              <tr>
                <td colSpan={5} className="table-empty">
                  Nenhuma chamada recente registrada. As próximas análises do aplicativo aparecem aqui automaticamente.
                </td>
              </tr>
            ) : (
              logs.slice(0, 25).map((log, index) => {
                const isSuccess = log.status === 'SUCCESS';
                const isBlocked = log.status?.includes('BLOQUEADAS') || log.status?.includes('BLOCKED');
                const timeStr = formatTimestampMsBR(log.createdAt);
                const pillClass = isSuccess ? 'pill-pos' : isBlocked ? 'pill-warn' : 'pill-neg';

                return (
                  <tr key={log.id || index}>
                    <td className="mono" style={{ color: 'var(--muted)', whiteSpace: 'nowrap' }}>
                      {timeStr}
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      <span className="code-chip">{log.endpoint || '/api/ai/*'}</span>
                    </td>
                    <td style={{ maxWidth: '340px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--muted)' }}>
                      <span title={log.promptSummary}>
                        {log.promptSummary || 'Solicitação autenticada'}
                      </span>
                    </td>
                    <td>
                      <span className={`pill ${pillClass}`}>{log.status}</span>
                    </td>
                    <td className="num mono" style={{ color: 'var(--muted)' }}>
                      {log.durationMs != null ? `${log.durationMs} ms` : '—'}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
};
