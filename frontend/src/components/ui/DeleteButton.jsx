import { useState } from 'react';
import { motion } from 'framer-motion';
import Modal from './Modal';
import Button from './Button';
import Icon from './Icon';
import { useToast } from './ToastProvider';
import './DeleteButton.css';

// Bouton icône rond, animé (framer-motion), avec confirmation modale avant suppression.
// `onDelete` doit renvoyer une Promise (typiquement mutateAsync d'une mutation React Query) ;
// le composant gère lui-même le spinner, le toast de résultat et la fermeture de la modale.
export default function DeleteButton({ onDelete, title, message, label = 'Supprimer' }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  async function confirm() {
    setBusy(true);
    try {
      await onDelete();
      toast.success('Supprimé avec succès');
      setOpen(false);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <motion.button
        type="button"
        className="delete-btn"
        aria-label={label}
        title={label}
        onClick={(e) => {
          e.stopPropagation();
          setOpen(true);
        }}
        whileHover={{ scale: 1.12, rotate: -6 }}
        whileTap={{ scale: 0.92 }}
        transition={{ type: 'spring', stiffness: 420, damping: 18 }}
      >
        <Icon name="trash" size={16} />
      </motion.button>

      <Modal
        open={open}
        title={title ?? 'Confirmer la suppression'}
        onClose={() => !busy && setOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)} disabled={busy}>
              Annuler
            </Button>
            <Button variant="danger" onClick={confirm} loading={busy}>
              Supprimer
            </Button>
          </>
        }
      >
        <p>{message ?? 'Cette action est définitive et ne peut pas être annulée.'}</p>
      </Modal>
    </>
  );
}
