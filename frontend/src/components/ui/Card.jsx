import './Card.css';

export default function Card({ className = '', accent, children, ...rest }) {
  return (
    <div className={`card ${accent ? `card-accent-${accent}` : ''} ${className}`} {...rest}>
      {children}
    </div>
  );
}
