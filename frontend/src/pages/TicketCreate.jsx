import { useNavigate } from 'react-router-dom';
import Card from '../components/ui/Card';
import TicketForm from '../components/tickets/TicketForm';
import { useCreateTicket } from '../api/tickets';
import { useToast } from '../components/ui/ToastProvider';

export default function TicketCreate() {
  const navigate = useNavigate();
  const toast = useToast();
  const createTicket = useCreateTicket();

  function handleSubmit(dto) {
    createTicket.mutate(dto, {
      onSuccess: (ticket) => {
        toast.success('Ticket créé');
        navigate(`/tickets/${ticket.id}`);
      },
      onError: (err) => toast.error(err.message),
    });
  }

  return (
    <div style={{ maxWidth: 640 }}>
      <Card>
        <h3 style={{ marginBottom: 20 }}>Nouveau ticket</h3>
        <TicketForm onSubmit={handleSubmit} submitting={createTicket.isPending} />
      </Card>
    </div>
  );
}
