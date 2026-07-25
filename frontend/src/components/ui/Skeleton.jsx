import './Skeleton.css';

export default function Skeleton({ width = '100%', height = '16px', radius = 'var(--radius-sm)', className = '' }) {
  return <div className={`skeleton ${className}`} style={{ width, height, borderRadius: radius }} />;
}

export function SkeletonCard() {
  return (
    <div className="skeleton-card">
      <Skeleton width="40%" height="12px" />
      <Skeleton width="70%" height="24px" />
    </div>
  );
}

export function SkeletonRows({ rows = 4 }) {
  return (
    <div className="skeleton-rows">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} height="44px" radius="var(--radius-md)" />
      ))}
    </div>
  );
}
