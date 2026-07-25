import { labelForStatus, toneForStatus } from '../../styles/status';
import './StatusPill.css';

export default function StatusPill({ status, label }) {
  const tone = toneForStatus(status);
  return <span className={`status-pill tone-${tone}`}>{label ?? labelForStatus(status)}</span>;
}
