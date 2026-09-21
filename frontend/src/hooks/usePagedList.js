import { useCallback, useEffect, useState } from "react";
import client from "../api/client";

/** Common page size for every paginated list in the app — see Pagination.jsx. */
export const PAGE_SIZE = 5;

const emptyPage = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

/**
 * Shared fetcher for the pages backed by a `PageResponse<T>` endpoint (paired with
 * `<Pagination>` for the prev/next UI). Handles the one recurring edge case: deleting
 * the last row on a page beyond the first would otherwise leave a stranded "no
 * results" screen, so it steps back a page instead.
 *
 * `reload()` is exposed for callers to re-fetch the *current* page after a mutation
 * (create/update/delete) — same as calling nothing, since it's the same function
 * `useEffect` already re-runs on `page`/`url` change.
 */
export function usePagedList(url, params) {
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(emptyPage);
  const paramsKey = JSON.stringify(params ?? {});
  const [lastParamsKey, setLastParamsKey] = useState(paramsKey);
  if (lastParamsKey !== paramsKey) {
    // A new search/filter must start from the first page, and React re-renders right away so no fetch runs with the stale page.
    setLastParamsKey(paramsKey);
    setPage(0);
  }

  const load = useCallback(() => {
    client.get(url, { params: { ...JSON.parse(paramsKey), page, size: PAGE_SIZE } }).then((res) => {
      const data = res.data.data;
      if (data.content.length === 0 && data.page > 0 && data.totalElements > 0) {
        setPage(data.page - 1);
      } else {
        setPageData(data);
      }
    });
  }, [url, page, paramsKey]);

  useEffect(load, [load]);

  return { pageData, page, setPage, reload: load };
}
