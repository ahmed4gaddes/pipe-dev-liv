import { useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { TICKET_ACTIONS } from '../../constants/ticket';
import { useApproveTicket, useChangeStatus, useDeployTicket, useRejectTicket } from '../../api/tickets';
import { useToast } from '../ui/ToastProvider';
import Button from '../ui/Button';
import Modal from '../ui/Modal';
import { FieldGroup, Textarea } from '../ui/Field';
import './TicketActionBar.css';

export default function TicketActionBar({ ticket }) {
  const { hasRole, userId } = useAuth();
  const toast = useToast();
  const [pendingAction, setPendingAction] = useState(null);
  const [comment, setComment] = useState('');

  const changeStatus = useChangeStatus(ticket.id);
  const approve = useApproveTicket(ticket.id);
  const reject = useRejectTicket(ticket.id);
  const deploy = useDeployTicket(ticket.id);

  const isOwner = ticket.createdByUserId === userId;
  const actions = (TICKET_ACTIONS[ticket.status] ?? []).filter(
    (a) => hasRole('ROLE_TECH_LEAD') || (a.allowOwner && isOwner)
  );

  if (actions.length === 0) return null;

  function run(action, extraComment) {
    const onSettled = {
      onSuccess: () => toast.success('Action effectuée'),
      onError: (err) => toast.error(err.message),
    };
    if (action.kind === 'status') {
      changeStatus.mutate({ newStatus: action.target, comment: extraComment || undefined }, onSettled);
    } else if (action.kind === 'approve') {
      approve.mutate(undefined, onSettled);
    } else if (action.kind === 'reject') {
      reject.mutate(extraComment, onSettled);
    } else if (action.kind === 'deploy') {
      deploy.mutate(action.env, onSettled);
    }
  }

  function handleClick(action) {
    if (action.confirm) {
      setPendingAction(action);
      setComment('');
    } else {
      run(action);
    }
  }

  function confirmPending() {
    run(pendingAction, comment);
    setPendingAction(null);
  }

  const busy = changeStatus.isPending || approve.isPending || reject.isPending || deploy.isPending;

  return (
    <>
      <div className="ticket-action-bar">
        {actions.map((action) => (
          <Button key={action.key} variant={action.variant} loading={busy} onClick={() => handleClick(action)}>
            {action.label}
          </Button>
        ))}
      </div>

      <Modal
        open={!!pendingAction}
        title={pendingAction?.label}
        onClose={() => setPendingAction(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setPendingAction(null)}>Annuler</Button>
            <Button variant={pendingAction?.variant === 'danger' ? 'danger' : 'primary'} onClick={confirmPending} loading={busy}>
              Confirmer
            </Button>
          </>
        }
      >
        {pendingAction?.withComment ? (
          <FieldGroup label="Commentaire (optionnel)">
            <Textarea value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Motif…" />
          </FieldGroup>
        ) : (
          <p>Confirmer « {pendingAction?.label} » pour le ticket #{ticket.id} ?</p>
        )}
      </Modal>
    </>
  );
}
