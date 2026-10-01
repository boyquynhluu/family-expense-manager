import { useState } from "react";
import { PAGE_SIZE } from "./usePagedList";

/**
 * Pages a list that is already fully loaded (e.g. the wallets, which the selects and totals need in
 * full anyway) with the same `pageData` shape as usePagedList, so it plugs into the same <Pagination>.
 * When the list shrinks (delete, other month) past the current page, it falls back to the last page.
 */
export function useClientPage(items, size = PAGE_SIZE) {
  const [page, setPage] = useState(0);
  const totalElements = items.length;
  const totalPages = Math.ceil(totalElements / size);
  const current = Math.min(page, Math.max(totalPages - 1, 0));

  return {
    rows: items.slice(current * size, (current + 1) * size),
    pageData: { content: [], page: current, size, totalElements, totalPages },
    setPage,
  };
}
