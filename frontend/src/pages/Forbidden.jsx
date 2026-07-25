import { Link } from 'react-router-dom';
import EmptyState from '../components/ui/EmptyState';
import Button from '../components/ui/Button';

export default function Forbidden() {
  return (
    <div style={{ padding: '64px 0' }}>
      <EmptyState
        icon="🔒"
        title="Accès refusé"
        description="Votre rôle ne permet pas d'accéder à cette page."
        action={
          <Link to="/">
            <Button variant="primary">Retour au tableau de bord</Button>
          </Link>
        }
      />
    </div>
  );
}
