"""The page: one file, no server, no network, and nothing on it that is not in the graph.

The page carries the graph document verbatim and draws itself from it in the browser.
That is not a shortcut — it is how "nothing appears on the page that is not in the graph
document" is made true rather than merely intended: the markup below names no module, no
package and no count, so everything a reader sees came out of the document.
"""

import logging

from . import graph

log = logging.getLogger("module_depth_map.page")

GRAPH_ELEMENT_ID = "module-depth-map-graph"

_STYLE = """
:root {
  color-scheme: light dark;
  --ground: #f7f7f5;
  --raised: #ffffff;
  --ink: #1b1c1e;
  --ink-soft: #5c6067;
  --edge: #d9dade;
  --accent: #2f5d8a;
  --alarm-ground: #fdf1ec;
  --alarm-edge: #d98a6a;
  --alarm-ink: #8a3b16;
}
@media (prefers-color-scheme: dark) {
  :root {
    --ground: #16181b;
    --raised: #1f2226;
    --ink: #e9eaec;
    --ink-soft: #a2a8b0;
    --edge: #343841;
    --accent: #8fb8e0;
    --alarm-ground: #2e1d16;
    --alarm-edge: #8a5236;
    --alarm-ink: #f0b295;
  }
}
* { box-sizing: border-box; }
body {
  margin: 0;
  padding: 2rem 1.25rem 4rem;
  background: var(--ground);
  color: var(--ink);
  font: 16px/1.55 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  overflow-wrap: anywhere;
}
main { max-width: 68rem; margin: 0 auto; }
h1 { font-size: 1.6rem; margin: 0 0 .35rem; letter-spacing: -.01em; }
h2 { font-size: 1.05rem; margin: 0 0 .75rem; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
p { margin: 0 0 1rem; }
.lede { color: var(--ink-soft); max-width: 46rem; }
.read {
  border: 1px solid var(--edge);
  background: var(--raised);
  border-radius: .5rem;
  padding: .85rem 1rem;
  margin: 1.5rem 0;
}
.read dl { display: flex; flex-wrap: wrap; gap: .35rem 2rem; margin: 0; }
.read div { display: flex; gap: .4rem; align-items: baseline; }
.read dt { color: var(--ink-soft); font-size: .85rem; }
.read dd { margin: 0; font-variant-numeric: tabular-nums; font-weight: 600; }
.unread {
  border: 1px solid var(--alarm-edge);
  background: var(--alarm-ground);
  color: var(--alarm-ink);
  border-radius: .5rem;
  padding: .85rem 1rem;
  margin: 1.5rem 0;
}
.unread h2 { font-family: inherit; margin-bottom: .4rem; }
.unread ul { margin: 0; padding-left: 1.1rem; }
.unread code { font-size: .9em; }
section.package { margin: 2rem 0 0; }
.package header { display: flex; align-items: baseline; gap: .6rem; flex-wrap: wrap; margin-bottom: .75rem; }
.package header span { color: var(--ink-soft); font-size: .85rem; }
.package h2 { margin: 0; color: var(--accent); }
.modules {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: .75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}
.module {
  border: 1px solid var(--edge);
  background: var(--raised);
  border-radius: .5rem;
  padding: .7rem .8rem;
}
.module .name { font-weight: 650; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.module .kind {
  display: inline-block;
  margin-top: .25rem;
  font-size: .72rem;
  letter-spacing: .04em;
  text-transform: uppercase;
  color: var(--ink-soft);
}
.module .nested { margin: .4rem 0 0; font-size: .8rem; color: var(--ink-soft); }
footer { margin-top: 3rem; color: var(--ink-soft); font-size: .85rem; }
"""

_SCRIPT = """
(function () {
  var document_ = JSON.parse(document.getElementById("GRAPH_ELEMENT_ID").textContent);
  var root = document.getElementById("module-depth-map");

  function add(parent, tag, className, text) {
    var element = document.createElement(tag);
    if (className) { element.className = className; }
    if (text !== undefined && text !== null) { element.textContent = String(text); }
    parent.appendChild(element);
    return element;
  }

  var head = add(root, "header");
  add(head, "h1", null, "Module depth map");
  add(head, "p", "lede",
    "Every module this application is made of, at class grain, grouped by the package it "
    + "lives in. An observation of the source it was generated from, and nothing more: "
    + "no module here is scored, ranked, or proposed for change.");

  var read = add(root, "div", "read");
  var list = add(read, "dl");
  function count(n, one, many) { return n + " " + (n === 1 ? one : many); }

  function fact(term, value) {
    var pair = add(list, "div");
    add(pair, "dt", null, term);
    add(pair, "dd", null, value);
  }
  fact("Source read", document_.source.roots.join(", "));
  fact("Files parsed", document_.source.filesParsed + " of " + document_.source.filesSeen);
  fact("Files not parsed", document_.source.filesUnparsed);
  fact("Packages", document_.packages.length);
  fact("Modules", document_.modules.length);

  if (document_.source.unparsed.length > 0) {
    var alarm = add(root, "div", "unread");
    add(alarm, "h2", null,
      count(document_.source.unparsed.length, "source file", "source files")
      + " could not be read; nothing declared inside is drawn below");
    var failures = add(alarm, "ul");
    document_.source.unparsed.forEach(function (entry) {
      var item = add(failures, "li");
      add(item, "code", null, entry.root + "/" + entry.path);
      add(item, "span", null, " \\u2014 " + entry.reason);
    });
  }

  var byId = {};
  document_.modules.forEach(function (module) { byId[module.id] = module; });

  document_.packages.forEach(function (package_) {
    var section = add(root, "section", "package");
    var header = add(section, "header");
    add(header, "h2", null, package_.name);
    add(header, "span", null, count(package_.moduleIds.length, "module", "modules"));
    var modules = add(section, "ul", "modules");
    package_.moduleIds.forEach(function (id) {
      var module = byId[id];
      var item = add(modules, "li", "module");
      add(item, "div", "name", module.name);
      add(item, "div", "kind", module.kind);
      if (module.nested.length > 0) {
        add(item, "p", "nested", "nested: " + module.nested.join(", "));
      }
    });
  });

  add(root, "footer", null,
    "Rendered from the graph document carried inside this file. Nothing is fetched, and "
    + "nothing is shown that the document does not contain.");
}());
"""


def _embeddable(text):
    """JSON safe to sit inside a script element: no sequence a parser could end it on."""
    return text.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")


def render(document):
    """The whole page as bytes: markup, style, the graph document, and the renderer."""
    embedded = _embeddable(graph.serialise(document).decode("utf-8").rstrip("\n"))
    html = "\n".join(
        [
            "<!doctype html>",
            '<html lang="en">',
            "<head>",
            '<meta charset="utf-8">',
            '<meta name="viewport" content="width=device-width, initial-scale=1">',
            "<title>Module depth map</title>",
            "<style>" + _STYLE + "</style>",
            "</head>",
            "<body>",
            '<main id="module-depth-map"></main>',
            '<script id="' + GRAPH_ELEMENT_ID + '" type="application/json">',
            embedded,
            "</script>",
            "<script>" + _SCRIPT.replace("GRAPH_ELEMENT_ID", GRAPH_ELEMENT_ID) + "</script>",
            "</body>",
            "</html>",
            "",
        ]
    )
    log.debug("page rendered bytes=%d modules=%d", len(html.encode("utf-8")), len(document["modules"]))
    return html.encode("utf-8")
