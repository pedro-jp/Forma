import React from 'react';

interface StatsCardsProps {
  totalUsers: number;
  totalFoods: number;
  totalWorkouts: number;
  totalAiRequests: number;
  lastUpdatedKey?: string;
}

export const StatsCards: React.FC<StatsCardsProps> = ({
  totalUsers,
  totalFoods,
  totalWorkouts,
  totalAiRequests,
  lastUpdatedKey
}) => {
  const items = [
    {
      key: 'users',
      label: 'Usuários cadastrados',
      value: totalUsers.toLocaleString('pt-BR'),
      sub: 'Contas cadastradas na plataforma'
    },
    {
      key: 'foods',
      label: 'Refeições registradas',
      value: totalFoods.toLocaleString('pt-BR'),
      sub: 'Diário alimentar em nuvem'
    },
    {
      key: 'workouts',
      label: 'Treinos realizados',
      value: totalWorkouts.toLocaleString('pt-BR'),
      sub: 'Musculação e aeróbicos'
    },
    {
      key: 'ai',
      label: 'Requisições de IA',
      value: totalAiRequests.toLocaleString('pt-BR'),
      sub: 'Solicitações processadas'
    }
  ];

  return (
    <section className="kpis" aria-label="Indicadores operacionais">
      {items.map((item) => (
        <div
          key={item.key}
          className={`kpi ${lastUpdatedKey === item.key ? 'is-highlighted' : ''}`}
        >
          <div className="kpi-label">{item.label}</div>
          <div className="kpi-val">{item.value}</div>
          <div className="kpi-sub">{item.sub}</div>
        </div>
      ))}
    </section>
  );
};
