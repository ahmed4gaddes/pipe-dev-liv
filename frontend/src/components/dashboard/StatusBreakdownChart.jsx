import { Bar, BarChart, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { hexForStatus, labelForStatus } from '../../styles/status';
import EmptyState from '../ui/EmptyState';
import './StatusBreakdownChart.css';

// Comparaison de magnitude par catégorie -> bar chart horizontal, une couleur par statut
// (encodage "status", pas "series identity" : chaque ticket appartient à un seul statut à
// la fois, ce n'est pas 8 séries indépendantes). Étiquette directe sur chaque barre —
// requis ici puisque sky/gold passent sous le seuil de contraste 3:1 vs le fond blanc.
export default function StatusBreakdownChart({ countByStatus }) {
  const data = Object.entries(countByStatus ?? {})
    .map(([status, count]) => ({ status, label: labelForStatus(status), count }))
    .sort((a, b) => b.count - a.count);

  if (data.length === 0) {
    return <EmptyState icon="📊" title="Aucun ticket pour le moment" />;
  }

  const rowHeight = 34;

  return (
    <div className="status-chart" style={{ height: Math.max(data.length * rowHeight, 120) }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          layout="vertical"
          data={data}
          margin={{ top: 4, right: 36, bottom: 4, left: 8 }}
          barSize={20}
        >
          <XAxis type="number" hide />
          <YAxis
            type="category"
            dataKey="label"
            width={170}
            tickLine={false}
            axisLine={false}
            tick={{ fontSize: 12, fill: 'var(--color-gray-600)' }}
          />
          <Tooltip
            cursor={{ fill: 'var(--color-navy-50)' }}
            formatter={(value) => [value, 'Tickets']}
            labelStyle={{ fontWeight: 700, color: 'var(--color-navy-900)' }}
          />
          <Bar dataKey="count" radius={[0, 4, 4, 0]} isAnimationActive={false}>
            {data.map((entry) => (
              <Cell key={entry.status} fill={hexForStatus(entry.status)} />
            ))}
            <LabelList dataKey="count" position="right" style={{ fill: 'var(--color-gray-700)', fontWeight: 700, fontSize: 12 }} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
