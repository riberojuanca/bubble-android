#!/usr/bin/env python3
"""Build the website into _site/: the static files of site/ plus the user manual as HTML pages.

The manual is written once, in docs/manual/*.md (English) and docs/manual/es/*.md (Spanish); this
script turns each chapter into a page with the site's look, a chapter menu, previous/next links and
a switch to the same chapter in the other language. Links to other repository files point to
GitHub. It handles only the Markdown the manual uses (headings, paragraphs, lists, tables, quotes,
code, emphasis, links) and needs nothing beyond Python's standard library.

Usage: python3 tools/build-site.py [output-dir]
"""
import html
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO_URL = "https://github.com/riberojuanca/bubble-android"
BLOB = REPO_URL + "/blob/main/"
LANGS = {
    "en": {"dir": ROOT / "docs/manual", "name": "English", "short": "EN", "manual": "Manual",
           "contents": "Contents", "prev": "Previous", "next": "Next", "edit": "Edit this page on GitHub",
           "home": "Home", "menu": "Chapters"},
    "es": {"dir": ROOT / "docs/manual/es", "name": "Español", "short": "ES", "manual": "Manual",
           "contents": "Índice", "prev": "Anterior", "next": "Siguiente", "edit": "Editar esta página en GitHub",
           "home": "Inicio", "menu": "Capítulos"},
}


def slugify(text):
    """GitHub-style heading id: lowercase, punctuation dropped, spaces to hyphens."""
    text = re.sub(r"<[^>]+>", "", text).lower()
    text = re.sub(r"[^\w\- ]", "", text, flags=re.UNICODE)
    return text.strip().replace(" ", "-")


def page_name(md_name):
    return "index.html" if md_name == "README.md" else md_name[:-3] + ".html"


def rewrite_link(target, src):
    """Map a Markdown link to the built site: manual chapters to pages, other repo files to GitHub."""
    if re.match(r"^[a-z]+:", target) or target.startswith("#"):
        return target
    path, _, anchor = target.partition("#")
    anchor = "#" + anchor if anchor else ""
    resolved = (src.parent / path).resolve()
    for lang, info in LANGS.items():
        if resolved.parent == info["dir"].resolve() and resolved.suffix == ".md":
            here = "en" if src.parent.resolve() == LANGS["en"]["dir"].resolve() else "es"
            prefix = "" if lang == here else "../" + lang + "/"
            return prefix + page_name(resolved.name) + anchor
    rel = resolved.relative_to(ROOT).as_posix()
    return BLOB + rel + anchor


def inline(text, src):
    """Inline Markdown: code spans first (kept literal), then links, bold and italics."""
    parts = re.split(r"(`[^`]+`)", text)
    out = []
    for part in parts:
        if part.startswith("`") and part.endswith("`") and len(part) > 1:
            out.append("<code>" + html.escape(part[1:-1]) + "</code>")
            continue
        s = html.escape(part, quote=False)
        s = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)",
                   lambda m: '<a href="%s">%s</a>' % (html.escape(rewrite_link(m.group(2), src)), m.group(1)), s)
        s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
        s = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", s)
        out.append(s)
    return "".join(out)


def markdown(text, src):
    """Convert the manual's Markdown to HTML. Returns (title, html, headings)."""
    lines = text.split("\n")
    out, headings, title = [], [], ""
    i = 0
    para = []

    def flush():
        if para:
            out.append("<p>" + " ".join(inline(l, src) for l in para) + "</p>")
            para.clear()

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if not stripped:
            flush(); i += 1; continue
        if stripped.startswith("```"):
            flush()
            code = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("```"):
                code.append(lines[i]); i += 1
            out.append("<pre><code>" + html.escape("\n".join(code)) + "</code></pre>")
            i += 1; continue
        m = re.match(r"^(#{1,4})\s+(.*)$", stripped)
        if m:
            flush()
            level, content = len(m.group(1)), inline(m.group(2), src)
            hid = slugify(content)
            if level == 1 and not title:
                title = re.sub(r"<[^>]+>", "", content)
            if level == 2:
                headings.append((hid, re.sub(r"<[^>]+>", "", content)))
            out.append('<h%d id="%s">%s</h%d>' % (level, hid, content, level))
            i += 1; continue
        if stripped.startswith("|"):
            flush()
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")]); i += 1
            head, body = rows[0], [r for r in rows[1:] if not all(re.match(r"^:?-+:?$", c) for c in r)]
            t = ['<div class="table"><table><thead><tr>']
            t += ["<th>%s</th>" % inline(c, src) for c in head]
            t.append("</tr></thead><tbody>")
            for r in body:
                t.append("<tr>" + "".join("<td>%s</td>" % inline(c, src) for c in r) + "</tr>")
            t.append("</tbody></table></div>")
            out.append("".join(t)); continue
        if stripped.startswith(">"):
            flush()
            quote = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                quote.append(lines[i].strip()[1:].strip()); i += 1
            out.append("<blockquote><p>" + " ".join(inline(q, src) for q in quote) + "</p></blockquote>")
            continue
        if re.match(r"^([-*]|\d+\.)\s+", stripped):
            flush()
            ordered = bool(re.match(r"^\d+\.", stripped))
            items = []
            while i < len(lines):
                s = lines[i].strip()
                mm = re.match(r"^([-*]|\d+\.)\s+(.*)$", s)
                if mm:
                    items.append(mm.group(2))
                elif s and items and lines[i].startswith((" ", "\t")):
                    items[-1] += " " + s
                else:
                    break
                i += 1
            tag = "ol" if ordered else "ul"
            out.append("<%s>%s</%s>" % (tag, "".join("<li>%s</li>" % inline(it, src) for it in items), tag))
            continue
        # A line that is only bold text (a question or a label) stands on its own.
        if re.match(r"^\*\*.+\*\*$", stripped) and stripped.count("**") == 2:
            flush()
            out.append('<p class="lead-in">' + inline(stripped, src) + "</p>")
            i += 1; continue
        para.append(stripped)
        i += 1
    flush()
    return title, "\n".join(out), headings


def chapters(lang):
    d = LANGS[lang]["dir"]
    files = sorted(p for p in d.glob("*.md") if p.name != "README.md")
    return [d / "README.md"] + files


def counterpart(src, lang):
    """The same chapter in the other language, matched by its number."""
    other = "es" if lang == "en" else "en"
    if src.name == "README.md":
        return other, "index.html"
    num = src.name.split("-", 1)[0]
    for p in chapters(other):
        if p.name.startswith(num + "-"):
            return other, page_name(p.name)
    return other, "index.html"


HEAD = """<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title} · Bubble</title>
<link rel="icon" href="{root}logo-light.svg" type="image/svg+xml" media="(prefers-color-scheme: light)">
<link rel="icon" href="{root}logo-dark.svg" type="image/svg+xml" media="(prefers-color-scheme: dark)">
<link rel="stylesheet" href="{root}style.css">
</head>
<body class="doc">
<header class="bar">
  <a class="brand" href="{root}index.html">
    <picture><source srcset="{root}logo-dark.svg" media="(prefers-color-scheme: dark)"><img src="{root}logo-light.svg" alt="" width="28" height="28"></picture>
    Bubble
  </a>
  <nav>
    <a href="index.html">{manual}</a>
    <a href="{repo}">GitHub</a>
    <a class="lang" href="{switch}" data-set="{other}" aria-label="{other_name}"><span{en_b}>EN</span><span{es_b}>ES</span></a>
  </nav>
</header>
"""

FOOT = """
<script>
  // Remember the language chosen here, so the home page opens in it too.
  document.querySelector('.lang').addEventListener('click', function () {{
    try {{ localStorage.setItem('lang', this.dataset.set); }} catch (e) {{}}
  }});
</script>
</body>
</html>
"""


def build_page(src, lang, pages, out_dir):
    info = LANGS[lang]
    title, body, headings = markdown(src.read_text(encoding="utf-8"), src)
    names = [p for p, _ in pages]
    idx = names.index(src)
    other, other_page = counterpart(src, lang)
    menu = "".join(
        '<li><a href="%s"%s>%s</a></li>' % (page_name(p.name), ' aria-current="page"' if p == src else "", html.escape(t))
        for p, t in pages)
    toc = ""
    if headings and src.name != "README.md":
        toc = '<nav class="toc"><p>%s</p><ul>%s</ul></nav>' % (
            info["contents"], "".join('<li><a href="#%s">%s</a></li>' % (h, html.escape(t)) for h, t in headings))
    pager = []
    if idx > 0:
        p, t = pages[idx - 1]
        pager.append('<a class="prev" href="%s"><span>%s</span>%s</a>' % (page_name(p.name), info["prev"], html.escape(t)))
    if idx < len(pages) - 1:
        p, t = pages[idx + 1]
        pager.append('<a class="next" href="%s"><span>%s</span>%s</a>' % (page_name(p.name), info["next"], html.escape(t)))
    page = HEAD.format(
        lang=lang, title=html.escape(title), root="../../", manual=info["manual"], repo=REPO_URL,
        switch="../%s/%s" % (other, other_page), other=other, other_name=LANGS[other]["name"],
        en_b=' class="on"' if lang == "en" else "", es_b=' class="on"' if lang == "es" else "")
    page += (
        '<div class="doc-layout">\n'
        '<aside class="chapters"><details open><summary>%s</summary><ul>%s</ul></details></aside>\n'
        '<article>%s%s<div class="pager">%s</div>'
        '<p class="edit"><a href="%s">%s</a></p></article>\n</div>'
    ) % (info["menu"], menu, toc, body, "".join(pager),
         BLOB + src.relative_to(ROOT).as_posix(), info["edit"])
    page += FOOT.format()
    (out_dir / page_name(src.name)).write_text(page, encoding="utf-8")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "_site"
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(ROOT / "site", out)
    for lang in LANGS:
        srcs = chapters(lang)
        pages = [(p, markdown(p.read_text(encoding="utf-8"), p)[0]) for p in srcs]
        d = out / "manual" / lang
        d.mkdir(parents=True)
        for p in srcs:
            build_page(p, lang, pages, d)
    print("Built", out)


if __name__ == "__main__":
    main()
