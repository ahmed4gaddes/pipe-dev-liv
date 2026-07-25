import { useState } from 'react';
import { FieldGroup, Input, Select, Textarea } from '../ui/Field';
import Button from '../ui/Button';
import { TICKET_PRIORITIES } from '../../constants/ticket';

export default function TicketForm({ onSubmit, submitting }) {
  const [form, setForm] = useState({
    title: '',
    description: '',
    priority: 'MEDIUM',
    targetEnvironment: 'DEV',
    gitBranch: '',
    gitCommitSha: '',
  });

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function handleSubmit(e) {
    e.preventDefault();
    onSubmit(form);
  }

  return (
    <form onSubmit={handleSubmit}>
      <FieldGroup label="Titre">
        <Input required value={form.title} onChange={(e) => update('title', e.target.value)} placeholder="Ex. Corriger le calcul des frais de tenue de compte" />
      </FieldGroup>

      <FieldGroup label="Description">
        <Textarea value={form.description} onChange={(e) => update('description', e.target.value)} placeholder="Contexte, changements, impact attendu…" />
      </FieldGroup>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <FieldGroup label="Priorité">
          <Select value={form.priority} onChange={(e) => update('priority', e.target.value)}>
            {TICKET_PRIORITIES.map((p) => (
              <option key={p} value={p}>{p}</option>
            ))}
          </Select>
        </FieldGroup>

        <FieldGroup label="Environnement cible">
          <Select value={form.targetEnvironment} onChange={(e) => update('targetEnvironment', e.target.value)}>
            <option value="DEV">DEV</option>
            <option value="TEST">TEST</option>
            <option value="PROD">PROD</option>
          </Select>
        </FieldGroup>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <FieldGroup label="Branche Git">
          <Input value={form.gitBranch} onChange={(e) => update('gitBranch', e.target.value)} placeholder="main" />
        </FieldGroup>

        <FieldGroup label="Commit SHA">
          <Input value={form.gitCommitSha} onChange={(e) => update('gitCommitSha', e.target.value)} placeholder="abc1234" />
        </FieldGroup>
      </div>

      <Button type="submit" variant="primary" loading={submitting}>
        Créer le ticket
      </Button>
    </form>
  );
}
