import { useState } from 'react';
import { useAddComment, useComments } from '../../api/comments';
import { useToast } from '../ui/ToastProvider';
import { Textarea } from '../ui/Field';
import Button from '../ui/Button';
import Avatar from '../ui/Avatar';
import EmptyState from '../ui/EmptyState';
import { SkeletonRows } from '../ui/Skeleton';
import './TicketComments.css';

function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function TicketComments({ ticketId }) {
  const { data, isLoading } = useComments(ticketId);
  const addComment = useAddComment(ticketId);
  const toast = useToast();
  const [content, setContent] = useState('');

  function handleSubmit(e) {
    e.preventDefault();
    if (!content.trim()) return;
    addComment.mutate({ content }, {
      onSuccess: () => setContent(''),
      onError: (err) => toast.error(err.message),
    });
  }

  return (
    <div>
      {isLoading ? (
        <SkeletonRows rows={2} />
      ) : data?.length ? (
        <ul className="comment-list">
          {data.map((c) => (
            <li key={c.id} className="comment-item">
              <Avatar name={c.authorUserId} size={30} />
              <div className="comment-body">
                <div className="comment-meta">
                  <span className="comment-author">{c.authorUserId}</span>
                  <span className="comment-date">{formatDate(c.createdAt)}</span>
                </div>
                <div className="comment-content">{c.content}</div>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState icon="💬" title="Aucun commentaire" />
      )}

      <form onSubmit={handleSubmit} className="comment-form">
        <Textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Ajouter un commentaire…"
        />
        <Button type="submit" variant="ghost" loading={addComment.isPending}>Publier</Button>
      </form>
    </div>
  );
}
