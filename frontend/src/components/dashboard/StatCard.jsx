import { motion } from 'framer-motion';
import Card from '../ui/Card';
import Icon from '../ui/Icon';
import './StatCard.css';

export default function StatCard({ label, value, icon, accent = 'navy', index = 0 }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, delay: index * 0.06, ease: 'easeOut' }}
    >
      <Card accent={accent} className="stat-card">
        <div className={`stat-icon stat-icon-${accent}`}>
          <Icon name={icon} size={20} />
        </div>
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </Card>
    </motion.div>
  );
}
