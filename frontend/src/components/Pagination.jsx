import { useTranslation } from "react-i18next";

/**
 * Shared prev/next pager for every paginated list in the app (backend page size is a
 * fixed 5 — see PAGE_SIZE in usePagedList.js). `pageData` is the raw `PageResponse<T>`
 * shape the backend returns (`{ content, page, size, totalElements, totalPages }`).
 * Renders nothing when there's only one page, matching the original Transactions.jsx
 * behavior this was extracted from.
 */
export default function Pagination({ pageData, onPageChange }) {
  const { t } = useTranslation("common");

  if (!pageData || pageData.totalPages <= 1) {
    return null;
  }

  return (
    <div className="pagination">
      <span className="pagination-info">
        {t("paginationInfo", {
          total: pageData.totalElements,
          page: pageData.page + 1,
          totalPages: pageData.totalPages,
        })}
      </span>
      <div className="pagination-controls">
        <button
          type="button"
          className="btn-secondary"
          onClick={() => onPageChange(Math.max(0, pageData.page - 1))}
          disabled={pageData.page === 0}
        >
          {t("prevPage")}
        </button>
        <button
          type="button"
          className="btn-secondary"
          onClick={() => onPageChange(Math.min(pageData.totalPages - 1, pageData.page + 1))}
          disabled={pageData.page >= pageData.totalPages - 1}
        >
          {t("nextPage")}
        </button>
      </div>
    </div>
  );
}
