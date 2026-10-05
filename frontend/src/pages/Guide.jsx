import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import guideText from "../content/huong-dan-su-dung.md?raw";
import SimpleMarkdown from "../components/SimpleMarkdown";
import { parseMarkdown } from "../utils/markdown";
import { Input } from "../components/ui/Input";

/** Every readable string of a section, lower-cased — what the search box matches against. */
function textOf(blocks) {
  return blocks
    .flatMap((b) => [
      b.text ?? "",
      ...(b.header ?? []),
      ...(b.rows ?? []).flat(),
      ...(b.items ?? []).flatMap((item) => [item.text, ...item.children]),
    ])
    .join(" ")
    .toLowerCase();
}

/**
 * "Hướng dẫn sử dụng": the user guide (src/content/huong-dan-su-dung.md — the single source, also linked from the
 * repo README) rendered in the app, with a table of contents and a search that keeps only the matching sections.
 */
export default function Guide() {
  const { t, i18n } = useTranslation("guide");
  const [query, setQuery] = useState("");

  // Sections = the "## " chapters; the title and intro before the first one stay on top.
  const { intro, sections } = useMemo(() => {
    const blocks = parseMarkdown(guideText).filter((b) => !(b.type === "heading" && b.level === 1));
    const result = [];
    const head = [];
    for (const block of blocks) {
      if (block.type === "heading" && block.level === 2) {
        result.push({ heading: block, blocks: [block] });
      } else if (result.length > 0) {
        result[result.length - 1].blocks.push(block);
      } else if (block.type !== "hr") {
        head.push(block);
      }
    }
    return { intro: head, sections: result };
  }, []);

  const needle = query.trim().toLowerCase();
  const visible = needle ? sections.filter((s) => textOf(s.blocks).includes(needle)) : sections;

  function jumpTo(id) {
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  return (
    <div className="guide-page">
      <div className="page-header">
        <div>
          <h1>{t("title")}</h1>
          <p className="page-header-subtitle">{t("subtitle")}</p>
        </div>
      </div>

      {i18n.language !== "vi" && <div className="viewer-banner">{t("vietnameseOnly")}</div>}

      <div className="section-card no-print">
        <Input type="search" value={query} placeholder={t("searchPlaceholder")} maxLength={100}
          onChange={(e) => setQuery(e.target.value)} />
        <nav className="guide-toc" aria-label={t("tocLabel")}>
          {visible.map((s) => (
            <button key={s.heading.id} type="button" onClick={() => jumpTo(s.heading.id)}>
              {s.heading.text}
            </button>
          ))}
        </nav>
      </div>

      {!needle && intro.length > 0 && (
        <div className="section-card">
          <SimpleMarkdown blocks={intro} />
        </div>
      )}

      {visible.length === 0 ? (
        <div className="section-card">
          <p className="empty-state">{t("noResults")}</p>
        </div>
      ) : (
        visible.map((s) => (
          <div className="section-card" key={s.heading.id}>
            <SimpleMarkdown blocks={s.blocks} />
          </div>
        ))
      )}
    </div>
  );
}
