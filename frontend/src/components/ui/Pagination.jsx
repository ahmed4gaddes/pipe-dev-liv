import './Pagination.css';

export default function Pagination({ page, totalPages, onPageChange }) {
  if (!totalPages || totalPages <= 1) return null;

  return (
    <div className="pagination">
      <button className="page-btn" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
        ← Précédent
      </button>
      <span className="page-info">
        Page {page + 1} / {totalPages}
      </span>
      <button className="page-btn" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
        Suivant →
      </button>
    </div>
  );
}
