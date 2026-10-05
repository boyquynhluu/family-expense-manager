import { Fragment } from "react";
import { Table, TBody, Td, Th, THead } from "./ui/Table";

/**
 * A small Markdown renderer for our own content (the user guide): headings, paragraphs, bullet / numbered lists
 * (one nested level), tables, blockquotes, horizontal rules, **bold**, *italic*, `code` and bare https:// links.
 * It builds React elements — no HTML string is ever injected — so it needs no sanitising and no extra dependency.
 */

// **bold**, *italic*, `code`, https://link — in that order of precedence.
const INLINE = /(\*\*[^*]+\*\*|\*[^*\s][^*]*\*|`[^`]+`|https?:\/\/[^\s)|]+)/g;

function renderInline(text, keyPrefix = "i") {
  const parts = text.replace(/\\\|/g, "|").split(INLINE);
  return parts.map((part, i) => {
    const key = `${keyPrefix}-${i}`;
    if (!part) return null;
    if (part.startsWith("**") && part.endsWith("**")) return <strong key={key}>{renderInline(part.slice(2, -2), key)}</strong>;
    if (part.startsWith("`") && part.endsWith("`")) return <code key={key}>{part.slice(1, -1)}</code>;
    if (part.startsWith("*") && part.endsWith("*") && part.length > 2) return <em key={key}>{part.slice(1, -1)}</em>;
    if (/^https?:\/\//.test(part)) {
      return (
        <a key={key} href={part} target="_blank" rel="noreferrer">
          {part}
        </a>
      );
    }
    return <Fragment key={key}>{part}</Fragment>;
  });
}

export default function SimpleMarkdown({ blocks }) {
  return (
    <div className="guide-content">
      {blocks.map((block, index) => {
        const key = `b-${index}`;
        switch (block.type) {
          case "heading": {
            const Tag = `h${Math.min(block.level + 1, 4)}`;
            return (
              <Tag key={key} id={block.id}>
                {renderInline(block.text, key)}
              </Tag>
            );
          }
          case "hr":
            return <hr key={key} />;
          case "quote":
            return (
              <blockquote key={key} className="guide-note">
                {renderInline(block.text, key)}
              </blockquote>
            );
          case "table":
            return (
              <Table key={key}>
                <THead>
                  <tr>
                    {block.header.map((cell, c) => (
                      <Th key={c}>{renderInline(cell, `${key}-h${c}`)}</Th>
                    ))}
                  </tr>
                </THead>
                <TBody>
                  {block.rows.map((row, r) => (
                    <tr key={r}>
                      {row.map((cell, c) => (
                        <Td key={c} data-label={block.header[c]}>
                          {renderInline(cell, `${key}-${r}-${c}`)}
                        </Td>
                      ))}
                    </tr>
                  ))}
                </TBody>
              </Table>
            );
          case "list": {
            const List = block.ordered ? "ol" : "ul";
            return (
              <List key={key}>
                {block.items.map((item, n) => (
                  <li key={n}>
                    {renderInline(item.text, `${key}-${n}`)}
                    {item.children.length > 0 && (
                      <ul>
                        {item.children.map((child, m) => (
                          <li key={m}>{renderInline(child, `${key}-${n}-${m}`)}</li>
                        ))}
                      </ul>
                    )}
                  </li>
                ))}
              </List>
            );
          }
          default:
            return <p key={key}>{renderInline(block.text, key)}</p>;
        }
      })}
    </div>
  );
}
