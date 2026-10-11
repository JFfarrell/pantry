#!/usr/bin/env python3
"""Fetch recipe pages (the pages listed below plus every source_url already in F3's corpus), trim them per design AD17 and write F5's harvest snapshots.

Run from the repository root on a machine with internet access:

    python3 scripts/harvest_snapshots.py

Standard library only. It writes:
  app/src/test/resources/recipeimport/harvest/<host>/<slug>.html   one trimmed snapshot per URL
  app/src/test/resources/recipeimport/harvest/snapshots.tsv        source_url, retrieved, path
  harvest_candidate_lines.tsv                                      ingredient lines found in each page's JSON-LD
  harvest_report.tsv                                               one row per page: OK, FAILED or TOO_BIG

The candidate lines are a convenience for filling in the corpus rows by hand. Expected keys and quantities in
parse_fidelity_corpus.tsv must still be written by you from the source line, never from parser output.
"""

import datetime
import html
import json
import re
import sys
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

CORPUS = Path("app/src/test/resources/ingredient/parse_fidelity_corpus.tsv")

# Pages added by hand, on top of every distinct source_url already in the corpus.
EXTRA_URLS = [
    "https://www.bbcgoodfood.com/recipes/quick-easy-chickpea-coconut-dhal",
    "https://www.bbcgoodfood.com/recipes/halloumi-traybake",
    "https://thefirstmess.com/2013/09/26/moroccan-style-vegan-vegetable-chickpea-stew-recipe/",
    "https://thefirstmess.com/2018/04/04/quick-smoky-red-lentil-stew-vegan-recipe/",
    "https://pinchofyum.com/miso-peanut-ramen-bowls",
    "https://pinchofyum.com/roasted-corn-and-fried-egg-tacos",
]



def corpus_urls():
    """Every distinct source_url in F3's corpus, in file order."""
    seen, urls = set(), []
    with open(CORPUS, encoding="utf-8") as corpus:
        next(corpus)  # header
        for row in corpus:
            fields = row.rstrip("\n").split("\t")
            if len(fields) > 1 and fields[1].startswith("http") and fields[1] not in seen:
                seen.add(fields[1])
                urls.append(fields[1])
    return urls


ROOT = Path("app/src/test/resources/recipeimport/harvest")
MAX_BYTES = 1024 * 1024
TRIM_COMMENT = (
    "<!-- F5 harvest snapshot, trimmed: removed script elements other than application/ld+json, "
    "style, svg and noscript elements, and HTML comments. Source: {url} Retrieved: {date} -->\n"
)

SCRIPT = re.compile(r"<script\b([^>]*)>.*?</script\s*>", re.IGNORECASE | re.DOTALL)
OTHER = re.compile(r"<(style|svg|noscript)\b[^>]*>.*?</\1\s*>", re.IGNORECASE | re.DOTALL)
COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
LD_JSON = re.compile(r"""type\s*=\s*["']?\s*application/ld\+json""", re.IGNORECASE)


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (F5 harvest script)", "Accept-Encoding": "identity"})
    with urllib.request.urlopen(request, timeout=30) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def trim(text):
    text = COMMENT.sub("", text)
    text = SCRIPT.sub(lambda m: m.group(0) if LD_JSON.search(m.group(1)) else "", text)
    text = OTHER.sub("", text)
    return re.sub(r"\n\s*\n+", "\n", text)


def snapshot_path(url, taken):
    parsed = urlparse(url)
    host = parsed.netloc.lower().removeprefix("www.")
    segments = [s for s in parsed.path.split("/") if s]
    slug = re.sub(r"[^a-z0-9_-]", "-", segments[-1].lower()) if segments else "index"
    path, n = f"{host}/{slug}.html", 2
    while path in taken:
        path, n = f"{host}/{slug}-{n}.html", n + 1
    taken.add(path)
    return path


def normalise(line):
    return re.sub(r"[ \t\n\x0b\f\r]+", " ", html.unescape(line).replace(" ", " ")).strip()


def ingredient_lines(text):
    lines = []
    for block in re.finditer(r"<script\b[^>]*ld\+json[^>]*>(.*?)</script\s*>", text, re.IGNORECASE | re.DOTALL):
        try:
            data = json.loads(block.group(1))
        except ValueError:
            continue
        stack = [data]
        while stack:
            node = stack.pop()
            if isinstance(node, list):
                stack.extend(reversed(node))
            elif isinstance(node, dict):
                kind = node.get("@type")
                kinds = kind if isinstance(kind, list) else [kind]
                if "Recipe" in kinds:
                    items = node.get("recipeIngredient") or node.get("ingredients") or []
                    lines.extend(normalise(i) for i in items if isinstance(i, str) and normalise(i))
                    return lines
                stack.extend(reversed(list(node.get("@graph", []))) if isinstance(node.get("@graph"), list) else [])
    return lines


def main():
    if not Path("settings.gradle.kts").is_file():
        sys.exit("run this from the repository root (the folder holding settings.gradle.kts)")
    today = datetime.date.today().isoformat()
    ROOT.mkdir(parents=True, exist_ok=True)
    taken, index, candidates, report = set(), [], [], []
    urls = list(dict.fromkeys(EXTRA_URLS + corpus_urls()))
    print(f"{len(urls)} pages to fetch\n")
    for url in urls:
        try:
            raw = fetch(url)
        except Exception as error:  # report and carry on with the other pages
            print(f"FAILED  {url}: {error}")
            report.append((url, "FAILED", "", "", str(error).replace("\t", " ")))
            continue
        path = snapshot_path(url, taken)
        trimmed = TRIM_COMMENT.format(url=url, date=today) + trim(raw)
        if len(trimmed.encode("utf-8")) > MAX_BYTES:
            print(f"TOO BIG {url}: {len(trimmed.encode('utf-8'))} bytes after trimming, skipped")
            report.append((url, "TOO_BIG", "", "", f"{len(trimmed.encode('utf-8'))} bytes after trimming"))
            continue
        target = ROOT / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(trimmed, encoding="utf-8", newline="\n")
        index.append((url, today, path))
        found = ingredient_lines(raw)
        candidates.extend((url, today, line) for line in found)
        print(f"OK      {url} -> {path} ({len(found)} JSON-LD ingredient lines)")
        report.append((url, "OK", path, str(len(found)), ""))
    with open(ROOT / "snapshots.tsv", "w", encoding="utf-8", newline="\n") as out:
        out.write("source_url\tretrieved\tpath\n")
        for row in index:
            out.write("\t".join(row) + "\n")
    with open("harvest_candidate_lines.tsv", "w", encoding="utf-8", newline="\n") as out:
        out.write("source_url\tretrieved\tline\n")
        for row in candidates:
            out.write("\t".join(row) + "\n")
    with open("harvest_report.tsv", "w", encoding="utf-8", newline="\n") as out:
        out.write("source_url\tstatus\tpath\tjsonld_ingredient_lines\tnote\n")
        for row in report:
            out.write("\t".join(row) + "\n")
    print(f"\nWrote {len(index)} snapshots, {len(candidates)} candidate lines to harvest_candidate_lines.tsv")
    print("Per-page results are in harvest_report.tsv: send that file and harvest_candidate_lines.tsv back.")


if __name__ == "__main__":
    main()
