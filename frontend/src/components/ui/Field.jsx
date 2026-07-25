import './Field.css';

export function FieldGroup({ label, error, children }) {
  return (
    <label className="field-group">
      <span className="field-label">{label}</span>
      {children}
      {error && <span className="field-error">{error}</span>}
    </label>
  );
}

export function Input(props) {
  return <input className="field-input" {...props} />;
}

export function Textarea(props) {
  return <textarea className="field-input field-textarea" {...props} />;
}

export function Select({ children, ...props }) {
  return (
    <select className="field-input field-select" {...props}>
      {children}
    </select>
  );
}
