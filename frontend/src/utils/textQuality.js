// Mirror of the backend's TextQuality (common/.../validation/TextQuality.java) so free-text fields
// get the error under the field before submitting; the backend's @CleanText still enforces it.
// KEEP THE WORD LISTS IN SYNC with common/src/main/resources/text-filter/*.txt.
import { useCallback } from "react";
import { useTranslation } from "react-i18next";

// Matched WITH diacritics, whole words: their unaccented form is an ordinary word
// ("lồn" → lon bia, "cặc" → các, "đụ" → du lịch, "đéo" → đeo kính).
const PROFANITY_ACCENTED = [
  "lồn", "cặc", "buồi", "địt", "đụ", "đéo", "đếch", "đĩ", "điếm",
  "óc chó", "chó đẻ", "con đĩ", "thằng chó", "đồ chó",
];

// Matched without diacritics, whole words: unambiguous abbreviations and English.
const PROFANITY_PLAIN = [
  "dmm", "dcm", "dkm", "djt", "dit me", "dit con me", "vcl", "vkl", "vcc", "clgt", "cmm", "loz",
  "fuck", "fuk", "fck", "fucking", "fucker", "fucked", "motherfucker", "shit", "bullshit", "bitch",
  "cunt", "pussy", "asshole", "bastard", "wtf", "stfu",
];

// Rejected only when they are the WHOLE value ("test" yes, "Phí test COVID" no).
const JUNK_VALUES = [
  "test", "testing", "test1", "test123", "tes", "tess", "x", "xx", "xyz", "abc", "abcd", "abcxyz", "asd",
  "asdf", "asdfgh", "qwe", "qwer", "qwerty", "zxc", "zxcv", "123", "1234", "12345", "123456", "hihi",
  "haha", "hehe", "hjhj", "lorem", "lorem ipsum", "dummy", "sample",
];

const IN_WORD_SEPARATORS = /(?<=\p{L})[._*~+\-|/\\'`^]+(?=\p{L})/gu;
const NON_WORD = /[^\p{L}\p{N}]+/u;
const ONLY_PUNCTUATION = /^[\p{P}\s]+$/u;
const ONE_CHAR_REPEATED = /^(.)\1{2,}$/u;

const lower = (s) => s.normalize("NFC").toLowerCase();
const collapse = (w) => w.replace(/(.)\1+/gu, "$1");
const stripMarks = (s) => s.replace(/đ/g, "d").replace(/Đ/g, "D").normalize("NFD").replace(/\p{M}+/gu, "");

// Lower-cased words with "l.ồ.n" / "l ồ n" dodges undone (letters not collapsed).
function words(value) {
  const joined = lower(value).replace(IN_WORD_SEPARATORS, "");
  const out = [];
  let spacedOut = "";
  for (const token of joined.split(NON_WORD)) {
    if (!token) continue;
    if ([...token].length === 1) {
      spacedOut += token;
      continue;
    }
    if (spacedOut) out.push(spacedOut);
    spacedOut = "";
    out.push(token);
  }
  if (spacedOut) out.push(spacedOut);
  return out;
}

const ACCENTED = new Set(PROFANITY_ACCENTED.map((e) => words(e).join(" ")));
const PLAIN = new Set(PROFANITY_PLAIN.map((e) => words(stripMarks(e)).join(" ")));
const JUNK = new Set(JUNK_VALUES.map((e) => words(stripMarks(e)).join(" ")));

function containsAny(entries, ws) {
  const text = ` ${ws.join(" ")} `;
  for (const entry of entries) if (text.includes(` ${entry} `)) return true;
  return false;
}

function isProfane(value) {
  const ws = words(value);
  const collapsed = ws.map(collapse);
  return (
    containsAny(ACCENTED, ws) ||
    containsAny(ACCENTED, collapsed) ||
    containsAny(PLAIN, ws.map(stripMarks)) ||
    containsAny(PLAIN, collapsed.map(stripMarks))
  );
}

function isJunk(value) {
  const trimmed = value.trim();
  const compact = trimmed.replace(/\s+/g, "");
  if (ONLY_PUNCTUATION.test(trimmed) || ONE_CHAR_REPEATED.test(compact)) return true;
  const normalized = stripMarks(lower(trimmed)).split(NON_WORD).join(" ").trim();
  return JUNK.has(normalized) || JUNK.has(collapse(normalized));
}

/** "PROFANITY" | "JUNK" | null (blank is fine — required-ness is the field's own `required`). */
export function checkText(value) {
  if (!value || !value.trim()) return null;
  if (isProfane(value)) return "PROFANITY";
  if (isJunk(value)) return "JUNK";
  return null;
}

/**
 * A `validate` function for <Input validate={...}> on free-text fields (names, notes). `{ junk: false }`
 * checks profanity only — for short codes like a category icon ("AI", "DX"), mirroring @CleanText(junk = false).
 */
export function useCleanText({ junk = true } = {}) {
  const { t } = useTranslation("validation");
  return useCallback(
    (value) => {
      const problem = checkText(value);
      if (problem === "PROFANITY") return t("textProfanity");
      if (problem === "JUNK" && junk) return t("textJunk");
      return "";
    },
    [t, junk]
  );
}
