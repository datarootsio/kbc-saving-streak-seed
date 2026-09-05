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
  --adapter: #7a5195;
  --record: #1f7a5a;
  --transaction: #a05a1f;
  --refusal: #8a6d1f;
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
    --adapter: #c39ae0;
    --record: #6ec8a4;
    --transaction: #e0a86e;
    --refusal: #d9be6e;
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
.module .shape { margin: .5rem 0 .35rem; }
/* Written unqualified, because the same bar is drawn twice: under every module, and once
   more as the swatch in the key that says which band is which. Scoped to `.module` it was
   drawn in the key with no height and no ground, which renders as nothing at all — a key
   with two labels and no colours beside them. */
.bar {
  height: .5rem;
  background: var(--edge);
  border-radius: .25rem;
  overflow: hidden;
  /* Centred as one run of bands rather than one centred block, so that the refusal band
     sits against the rest of the bar it is part of. Left as `margin: 0 auto` on each
     band, the two stacked and only the second was visible. */
  display: flex;
  justify-content: center;
}
.bar span { display: block; flex: none; height: 100%; background: var(--accent); }
.bar .refuse { background: var(--refusal); }
.bar.unscored { background: none; border: 1px dashed var(--edge); }
.module .fan { display: block; width: 100%; }
.fan line { stroke: var(--accent); stroke-width: 1.1; }
.fan circle { fill: var(--accent); }
.fan .adapter { stroke: var(--adapter); fill: var(--adapter); }
.fan .record { stroke: var(--record); fill: var(--record); }
.fan .transaction { stroke: var(--transaction); fill: var(--transaction); stroke-dasharray: 3 2.5; }
.module .cost { margin: 0; font-size: .8rem; color: var(--ink-soft); font-variant-numeric: tabular-nums; }
.module .reach { margin: .2rem 0 0; font-size: .8rem; color: var(--ink-soft); font-variant-numeric: tabular-nums; }
/* The verdict sits under a rule of its own, because it is the one line on a card that is
   a judgement rather than a measurement, and a reader should be able to see which is
   which without reading either. */
.module .verdict {
  margin: .45rem 0 0;
  padding-top: .4rem;
  border-top: 1px solid var(--edge);
  font-size: .8rem;
  color: var(--ink-soft);
  font-variant-numeric: tabular-nums;
}
.module .verdict strong { color: var(--ink); font-weight: 650; }
/* The refusals a module can answer with, in the band's own ink, so that the names under
   the bar and the coloured stretch of the bar itself read as one statement. */
.module .refusals { margin: .2rem 0 0; font-size: .8rem; color: var(--ink-soft); }
.module .refusals .refusal { color: var(--refusal); font-weight: 650; }
.module .refusals .refusal.found { color: var(--alarm-ink); }
/* A promise this tool never held against an implementation. Marked, because it is
   neither a finding nor an agreement — and underlined rather than recoloured, since
   alarm ink is for what the tool actually found. */
.module .refusals .refusal.unchecked { text-decoration: underline dotted; }
/* A disagreement between what a module documents and what it raises. Under its own rule,
   like the verdict, because it is a finding rather than a measurement — and in alarm ink
   on the finding's own words only, so the card is not washed in a colour that would read
   as a ranking of the module. */
.module .finding {
  margin: .45rem 0 0;
  padding-top: .4rem;
  border-top: 1px solid var(--edge);
  font-size: .8rem;
  color: var(--ink-soft);
}
.module .finding strong { color: var(--alarm-ink); font-weight: 650; }
/* The one verdict that is a finding. Marked on the word rather than on the whole card:
   the shape above it is the argument, and a card washed in a colour would be read as a
   ranking of the module rather than as an answer about deleting it. */
.module .verdict.found strong { color: var(--alarm-ink); }
.key { display: flex; flex-wrap: wrap; gap: .25rem 1rem; margin: .6rem 0 0; padding: 0; list-style: none; }
.key li { display: flex; align-items: center; gap: .35rem; font-size: .85rem; color: var(--ink-soft); }
.key svg { flex: none; }
/* The swatch for a band on a bar, sized here rather than in the renderer: the one width
   the page works out per module is the one a reader has to be able to check against the
   document, and a fixed swatch worked out beside it would be a second thing to read past. */
.key .bar { flex: none; width: 24px; }
.key .bar span { width: 100%; }
/* The element as well as the class, because the interface bar of a module nobody scored
   carries `unscored` too: written `.module .unscored` this rule reached the bar and set a
   margin, a font size and an italic on a strip of dashed border, where nothing meant it
   to. Nothing showed, the margin collapsing against the one above it — which is exactly
   the kind of collision that shows the first time either rule is touched. */
.module p.unscored { margin: .5rem 0 0; font-size: .8rem; color: var(--ink-soft); font-style: italic; }
.module .nested { margin: .4rem 0 0; font-size: .8rem; color: var(--ink-soft); }
.rules {
  border: 1px solid var(--edge);
  background: var(--raised);
  border-radius: .5rem;
  padding: .85rem 1rem;
  margin: 1.5rem 0;
}
.rules h2 { font-family: inherit; margin-bottom: .4rem; }
.rules p { margin: 0 0 .6rem; color: var(--ink-soft); font-size: .9rem; }
.rules ul { margin: 0; padding-left: 1.1rem; }
.rules li { color: var(--ink-soft); font-size: .9rem; margin-bottom: .3rem; }
.rules strong { color: var(--ink); }
/* Behind the shape: the card as the control that opens a panel, and the panel itself.
   The card is clickable everywhere and the name inside it is a real button, so a
   keyboard reaches the same panel by tabbing that a pointer reaches by clicking. */
.module { cursor: pointer; }
.module:hover, .module:focus-within { border-color: var(--accent); }
.module button.name {
  display: block;
  width: 100%;
  margin: 0;
  padding: 0;
  border: 0;
  background: none;
  color: var(--accent);
  font: inherit;
  font-weight: 650;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  text-align: left;
  cursor: pointer;
  /* A module's name is the string a reader most wants off this page — into a search, an
     import, a message to whoever owns it — and a browser will not select the text inside
     a button unless it is told to: chromium computes `user-select: auto` here and still
     selects nothing, by double-click or by drag. Everywhere else on a card the words come
     out already, so without this the name is the word a reader cannot take. */
  user-select: text;
  -webkit-user-select: text;
}
.module button.name:hover { text-decoration: underline; }
.module button.name:focus-visible,
.behind button:focus-visible,
.behindBody:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
/* A dialog rather than a div, because Escape, the focus that goes into it and the focus
   that comes back out of it are then the browser's own. A hand-rolled panel has to trap
   the tab key itself, and one that traps it wrong is a page a keyboard cannot leave. */
dialog.behind {
  padding: 0;
  border: 1px solid var(--edge);
  border-radius: .6rem;
  background: var(--raised);
  color: var(--ink);
  width: min(46rem, calc(100vw - 2rem));
  max-width: none;
}
/* What a browser's own stylesheet says about a dialog it knows, said here for one it does
   not. Where there is no modal dialog, `<dialog>` is an unknown element and the `open`
   attribute the script falls back to means nothing to it: without these two rules the
   empty panel shell sits in the page from load and no close ever removes it. Both halves,
   because a browser's own sheet supplies both: an unknown element defaults to `display:
   inline`, so hiding it without saying how to show it leaves the panel an inline run
   spliced into the page. Where `<dialog>` is known these say what the browser was going
   to say anyway — the closed rule is the more specific of the two, so it still wins. */
dialog.behind { display: block; }
dialog.behind:not([open]) { display: none; }
dialog.behind::backdrop { background: rgba(12, 13, 15, .6); }
.behindBody { max-height: calc(100vh - 4rem); overflow: auto; padding: 0 1.1rem 1.3rem; }
.behindHead {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  gap: 1rem;
  align-items: flex-start;
  justify-content: space-between;
  background: var(--raised);
  border-bottom: 1px solid var(--edge);
  padding: 1rem 0 .6rem;
  margin-bottom: .9rem;
}
.behindHead h3 {
  margin: 0;
  font-size: 1.15rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.behindHead .where { margin: .2rem 0 0; font-size: .8rem; color: var(--ink-soft); }
.behind button.close {
  flex: none;
  padding: .25rem .7rem;
  border: 1px solid var(--edge);
  border-radius: .35rem;
  background: var(--ground);
  color: var(--ink);
  font: inherit;
  font-size: .85rem;
  cursor: pointer;
}
.behindPart { margin: 0 0 1.1rem; }
.behindPart h4 {
  margin: 0 0 .35rem;
  font-size: .78rem;
  letter-spacing: .05em;
  text-transform: uppercase;
  font-weight: 650;
  color: var(--ink-soft);
}
.behindPart p { margin: 0 0 .4rem; font-size: .88rem; }
.behindPart ul { margin: 0 0 .6rem; padding-left: 1.05rem; }
.behindPart li { font-size: .88rem; margin-bottom: .3rem; }
.behindPart code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: .85em; }
.behindPart .says { color: var(--ink-soft); }
.behindPart .id { display: block; font-size: .8em; }
/* Said plainly rather than left out. A section that vanished when it was empty reads as
   a panel that forgot to draw it, and "there are none" is the answer a reader came for. */
.behindPart .none { color: var(--ink-soft); font-style: italic; }
.behindPart .because { margin: .15rem 0 .5rem; font-size: .82rem; color: var(--ink-soft); }
.behindPart .unscored { font-style: italic; color: var(--ink-soft); }
/* The kind of each thing reached, in the same ink as the line the fan draws to it, so
   that the key above the cards and the words in this panel read as one statement. */
.behindPart .kind {
  font-size: .72rem;
  letter-spacing: .04em;
  text-transform: uppercase;
  color: var(--ink-soft);
}
.behindPart .kind-module { color: var(--accent); }
.behindPart .kind-adapter { color: var(--adapter); }
.behindPart .kind-record { color: var(--record); }
.behindPart .kind-transaction { color: var(--transaction); }
.behind .verdict strong { color: var(--ink); font-weight: 650; }
.behind .verdict.found strong { color: var(--alarm-ink); }
.behind .finding strong { color: var(--alarm-ink); font-weight: 650; }
/* Flows: the three business events, and what choosing one does to the cards below. The
   chooser is a set of buttons rather than a select, because the state a reader has to be
   able to see is which flow is chosen — and `aria-pressed` says it to a screen reader in
   the same breath that the colour says it to everybody else. */
.flows .chooser { display: flex; flex-wrap: wrap; gap: .5rem; margin: .6rem 0 .9rem; padding: 0; list-style: none; }
.flows button {
  padding: .3rem .8rem;
  border: 1px solid var(--edge);
  border-radius: .35rem;
  background: var(--ground);
  color: var(--ink);
  font: inherit;
  font-size: .9rem;
  cursor: pointer;
}
.flows button:hover { border-color: var(--accent); }
.flows button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.flows button[aria-pressed="true"] {
  background: var(--accent);
  border-color: var(--accent);
  /* The raised ink rather than the ground, so the chosen button reads the same way in
     both themes: the accent is dark on light and light on dark, and one fixed text
     colour would be invisible in one of the two. */
  color: var(--raised);
  font-weight: 650;
}
/* A flow this graph could not walk. Not a button a reader can press: pressing it could
   only highlight a path that is not there, and a shorter path is the one output worse
   than none. */
.flows button[disabled] {
  cursor: not-allowed;
  border-style: dashed;
  color: var(--ink-soft);
}
.flows .untraced {
  border: 1px solid var(--alarm-edge);
  background: var(--alarm-ground);
  color: var(--alarm-ink);
  border-radius: .5rem;
  padding: .6rem .8rem;
  margin: 0 0 .8rem;
  font-size: .88rem;
}
.flows .untraced strong { font-weight: 650; }
.flows ol.path { margin: .2rem 0 .6rem; padding-left: 1.6rem; }
.flows ol.path li { font-size: .88rem; color: var(--ink-soft); margin-bottom: .2rem; }
.flows ol.path code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: .9em; }
.flows ol.path button.at {
  padding: 0;
  border: 0;
  background: none;
  color: var(--accent);
  font: inherit;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  cursor: pointer;
}
.flows ol.path button.at:hover { text-decoration: underline; }
/* A module the chosen flow passes through, and one it does not. Nothing is hidden: a
   module off the flow is drawn faded rather than removed, so the shape of the whole
   application stays on the page and a reader can see how much of it one event touches. */
.module.onTheFlow { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent); }
.module.aside { opacity: .3; }
.module .step {
  float: right;
  margin-left: .4rem;
  padding: 0 .4rem;
  border-radius: .8rem;
  background: var(--accent);
  color: var(--raised);
  font-size: .72rem;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
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

  // The shape is drawn rather than written, so it is SVG, and SVG elements have to be
  // made in their own namespace: `createElement("svg")` produces an unknown HTML element
  // that renders as nothing at all, with no error anywhere for a reader to go on.
  //
  // The namespace is asked of the browser's own parser rather than written down here,
  // because writing it down would put a URL in a file whose whole promise is that it
  // fetches nothing. This one would not be fetched either, but a reader checking that
  // promise by grepping the page cannot tell those two apart, and a page that has to be
  // explained is not one that can be checked.
  var svgNamespace = (function () {
    var carrier = document.createElement("template");
    carrier.innerHTML = "<svg></svg>";
    return carrier.content.firstChild.namespaceURI;
  }());

  function draw(parent, tag, className, attributes) {
    var element = document.createElementNS(svgNamespace, tag);
    if (className) { element.setAttribute("class", className); }
    for (var name in attributes || {}) {
      if (Object.prototype.hasOwnProperty.call(attributes, name)) {
        element.setAttribute(name, String(attributes[name]));
      }
    }
    parent.appendChild(element);
    return element;
  }

  function titled(element, text) {
    draw(element, "title", null, {}).textContent = text;
    return element;
  }

  // The shape this renderer reads, checked before anything is read out of it. A document
  // of an older shape has no `scoring` object, and reaching into one would throw halfway
  // down a single pass — which a reader sees as a page that stopped early or never
  // started, not as a page that could not be drawn. Said out loud instead.
  if (document_.schema !== "GRAPH_SCHEMA") {
    var wrong = add(root, "div", "unread");
    add(wrong, "h2", null, "This page cannot draw the document it carries");
    add(wrong, "p", null,
      "The document carried inside this file says its shape is "
      + document_.schema + ", and this page draws GRAPH_SCHEMA. Nothing below is drawn: "
      + "half a document, drawn as though it were whole, is the one thing a reader "
      + "cannot check.");
    return;
  }

  var head = add(root, "header");
  add(head, "h1", null, "Module depth map");
  add(head, "p", "lede",
    "Every module this application is made of \u2014 both halves of it, "
    + document_.source.languages.join(" and ") + " \u2014 grouped by the package it lives "
    + "in, each drawn as what its interface costs a caller over a fan of everything it "
    + "coordinates on that caller's behalf. An observation of the source it was generated "
    + "from, and nothing more: nothing here is ranked, and no module here is proposed for "
    + "change.");
  add(head, "p", "lede",
    "A module is anything with an interface and an implementation, and where that sits "
    + "differs by language rather than by anybody's preference. A Java module is a class, "
    + "because that is where a Java interface is written. A TypeScript module is a file, "
    + "because that is what an import names and so is the whole of what a caller of one "
    + "gets. Each card says which it is, and every card was measured by the same rules: "
    + "one measure over the whole application, or the picture is of half of it.");
  add(head, "p", "lede",
    "Click any module \u2014 or tab to its name and press Enter \u2014 for everything "
    + "standing behind its shape: every method with what it costs a caller, everything it "
    + "coordinates, which modules go through it, its deletion-test verdict, and any "
    + "finding against it. Every value there is read from the document carried inside "
    + "this file and nothing in it is worked out while the page is drawn, so what the "
    + "panel says and what a later tool reads out of the graph are the same facts.");

  var read = add(root, "div", "read");
  var list = add(read, "dl");
  function count(n, one, many) { return n + " " + (n === 1 ? one : many); }

  function fact(term, value) {
    var pair = add(list, "div");
    add(pair, "dt", null, term);
    add(pair, "dd", null, value);
  }
  fact("Source read", document_.source.roots.join(", "));
  fact("Languages", document_.source.languages.join(", "));
  fact("Files parsed", document_.source.filesParsed + " of " + document_.source.filesSeen);
  fact("Files not parsed", document_.source.filesUnparsed);
  fact("Paths not read", document_.source.notRead.paths.length + " \u2014 "
    + document_.source.notRead.rule);
  fact("Packages", document_.packages.length);
  fact("Modules", document_.modules.length);
  fact("Modules scored", document_.scoring.modulesScored + " of " + document_.modules.length);

  if (document_.source.unparsed.length > 0) {
    var alarm = add(root, "div", "unread");
    add(alarm, "h2", null,
      count(document_.source.unparsed.length, "source path", "source paths")
      + " could not be read; nothing declared inside is drawn below");
    var failures = add(alarm, "ul");
    document_.source.unparsed.forEach(function (entry) {
      var item = add(failures, "li");
      add(item, "code", null, entry.root + "/" + entry.path);
      add(item, "span", null, " \\u2014 " + entry.reason);
    });
  }

  // Not the alarm band above: a path a rule declined is not a failure, and painting the
  // two the same colour would have a `node_modules` reading as source this tool could not
  // cope with. It is here at all because "what was left out, and under which rule" is a
  // question a reader of a picture of an application is entitled to an answer to.
  var declined = add(root, "section", "rules");
  add(declined, "h2", null, "What was not read at all");
  add(declined, "p", null, document_.source.notRead.because);
  if (document_.source.notRead.paths.length > 0) {
    add(declined, "p", null,
      count(document_.source.notRead.paths.length, "path", "paths") + " under the source "
      + "read above matched that rule, and nothing inside them is drawn here:");
    var skipped = add(declined, "ul");
    document_.source.notRead.paths.forEach(function (entry) {
      var item = add(skipped, "li");
      add(item, "code", null, entry.root + "/" + entry.path);
      add(item, "span", null, " \u2014 " + entry.matched);
    });
  } else {
    add(declined, "p", null,
      "Nothing under the source read above matched that rule: the directories this tool "
      + "was pointed at hold the application's own source and nothing else.");
  }

  var rules = add(root, "section", "rules");
  add(rules, "h2", null, "What the bars measure");
  add(rules, "p", null,
    "Each bar is the cost of a module's interface: everything a caller has to learn before "
    + "they can use it correctly. A method they can reach counts "
    + document_.scoring.weights.method + ", each of its parameters counts "
    + document_.scoring.weights.parameter + ", and each distinct type crossing the seam "
    + "counts " + document_.scoring.weights.typeToLearn + " \u2014 or "
    + document_.scoring.weights.typeEveryCallerAlreadyKnows + ", when "
    + document_.scoring.configuration + " lists its name under "
    + "typesEveryCallerAlreadyKnows. That list is the whole of "
    + "the difference: this tool reads one source tree and never resolves a name, so a "
    + "type is one to learn because the list does not hold it and for no other reason \u2014 "
    + "not because anything here found out where it was declared. Reachable means "
    + document_.scoring.reachableFromOutside.join(", ") + ". Every bar is drawn to the same "
    + "scale, so two of them can be compared by eye.");
  add(rules, "p", null,
    "What a bar leaves out is part of the interface too, and is not measured: the "
    + "invariants a module states in prose, the order its calls have to be made in, "
    + "what a type variable has to be, the constructor a caller writes new against "
    + "\\u2014 how a module is built is this framework's business rather than a "
    + "caller's \\u2014 the methods a module inherits rather than declares \\u2014 this "
    + "tool reads one file at a time and never opens a supertype it does not hold "
    + "\\u2014 and the members of a type declared inside a module, which are named "
    + "below it rather than counted. A bar is a floor on what a caller must learn "
    + "rather than the whole of it, and a bar at nothing says only that there was "
    + "nothing on it to count.");
  add(rules, "p", null,
    document_.scoring.modulesNeverScored + " of " + document_.modules.length
    + " modules are drawn but never scored, each by a named rule in "
    + document_.scoring.configuration + ". They are shallow by construction, and ranking "
    + "them beside the modules that are not would bury the finding.");
  var band = add(root, "section", "rules");
  add(band, "h2", null, "What the band on each bar measures");
  add(band, "p", null, document_.scoring.refusals.because);
  add(band, "p", null,
    "So each bar is drawn in two bands: what a caller must learn that is not a refusal, "
    + "and the refusals, at " + document_.scoring.weights.refusal + " apiece. The two "
    + "come to the bar's whole width, and the graph carries them apart as "
    + "costWithoutRefusals and refusalCost as well as together as cost \u2014 so a module "
    + "with a wide bar and a wide band is wide for a reason a reader can see, and one "
    + "with a wide bar and no band at all is wide for some other reason. "
    + count(document_.scoring.refusals.refusalsRead, "refusal is", "refusals are")
    + " read across this page.");
  var bands = add(band, "ul", "key");
  [
    {of: "learn", says: "what a caller must learn besides the refusals"},
    {of: "refuse", says: "the refusals it can answer with"}
  ].forEach(function (part) {
    var item = add(bands, "li");
    add(add(item, "div", "bar"), "span", part.of);
    add(item, "span", null, part.says);
  });
  add(band, "p", null,
    "A refusal is read from the source's own words on both sides and guessed at on "
    + "neither: what a module documents is the @throws written over a method or a "
    + "constructor a caller can reach \u2014 in a javadoc block on the Java side and in a "
    + "JSDoc block on the TypeScript side, which spell the tag the same way \u2014 and "
    + "what it raises is what its body throws, a constructor's body included, which is why "
    + "a constructor's @throws is read as well. Where the two disagree the card says so, "
    + "naming the refusal and both sides of it:");
  var disagreements = add(band, "ul");
  [
    document_.scoring.refusals.documentedNeverRaised,
    document_.scoring.refusals.raisedNeverDocumented
  ].forEach(function (named_) {
    var item = add(disagreements, "li");
    add(item, "strong", null, named_.finding);
    add(item, "span", null, " \u2014 " + named_.because);
  });
  var findingsCounted = [];
  document_.scoring.refusals.findingsByKind.forEach(function (entry) {
    findingsCounted.push(entry.finding + ": " + entry.findings);
  });
  add(band, "p", null,
    "On this page \u2014 " + findingsCounted.join(", ") + ", across "
    + count(document_.scoring.refusals.modulesWithFindings, "module", "modules") + ". A "
    + "module can be in both disagreements at once, on two different refusals, which is "
    + "why those are counts of findings rather than of modules.");
  add(band, "p", null,
    "What this band leaves out is a floor in the same direction as everything else here. "
    + "A refusal thrown by a name rather than by a type \u2014 throw thrown, throw "
    + "somethingElse.build(), throw refusing(why) where two methods are called refusing "
    + "and they hand back different things, throw make() where make hands back a type "
    + "variable and the caller decides what that is \u2014 needs a type this tool never "
    + "resolves, so it is not read and not guessed at; a refusal one module raises by "
    + "calling another is the second module's, drawn there; and a prose sentence about when "
    + "something fails is part of the interface and is measured nowhere. So a band at "
    + "nothing says only that there was nothing here to read.");
  add(band, "p", null,
    "Where the implementation could not be read, no finding is made either, and the "
    + "refusal is drawn underlined instead: a @throws on a method with no body \u2014 an "
    + "interface's, an abstract one's \u2014 is a promise to whoever implements it rather "
    + "than something this module's own body was ever going to keep, and a body carrying "
    + "a throw this tool could not name may be raising exactly what was promised. "
    + count(document_.scoring.refusals.refusalsNotChecked, "refusal is", "refusals are")
    + " left unchecked on this page for one of those two reasons. A machine that accused "
    + "a module which kept its word would stop being read, which is worth more than the "
    + "stale comments it would catch \u2014 so both sides are printed on the card, and a "
    + "reader can go and check what the tool would not.");

  var fans = add(root, "section", "rules");
  add(fans, "h2", null, "What the fans measure");
  add(fans, "p", null,
    "Under each bar is one line out to every distinct thing that module coordinates on "
    + "its caller's behalf: another module it calls, an adapter it drives, a persistent "
    + "record it keeps, and the transaction it establishes. That count is its reach, and "
    + "reach is what depth is read as \u2014 the behaviour a caller sets in motion per unit "
    + "of interface they have to learn, printed under every card as "
    + "reach \u00f7 interface cost. Never lines over lines: that measure pays a module for "
    + "padding its implementation, and under it the largest file in a repository is its "
    + "deepest module. Writing the same call again, or a hundred more lines around it, "
    + "moves no fan on this page. Every fan is drawn to one scale, the widest reach in "
    + "this document, which is " + document_.scoring.widestReach + ".");
  add(fans, "p", null,
    "So a deep module reads as a short bar over a wide fan, and a module that coordinates "
    + "one thing per method reads as a bar as wide as its fan. The shape is the whole "
    + "argument; the words under it are there to be checked against it.");
  var kindsNamed = add(fans, "ul");
  [
    {rule: "an adapter", because: document_.scoring.reach.adapter},
    {rule: "a persistent record", because: document_.scoring.reach.persistentRecord},
    {rule: "a transaction", because: document_.scoring.reach.transaction}
  ].forEach(function (named_) {
    var item = add(kindsNamed, "li");
    add(item, "strong", null, named_.rule);
    add(item, "span", null, " \u2014 " + named_.because);
  });
  add(fans, "p", null,
    "Everything else a module calls is a module it calls, and building one is calling it: "
    + "new B(a) and B.of(a) are the same collaborator coordinated, spelled two ways, so "
    + "they count the same. A transaction is established by "
    + document_.scoring.reach.transactionAnnotations.join(", ") + " on the module or on one "
    + "of its methods. All three rules live in " + document_.scoring.configuration
    + ", beside the weights: change one and the fans change with it.");
  var key = add(fans, "ul", "key");
  ["module", "adapter", "record", "transaction"].forEach(function (kind) {
    var item = add(key, "li");
    var swatch = draw(item, "svg", "fan", {viewBox: "0 0 24 10", width: 24, height: 10});
    draw(swatch, "line", kind, {x1: 0, y1: 5, x2: 18, y2: 5});
    draw(swatch, "circle", kind, {cx: 20, cy: 5, r: 2});
    add(item, "span", null, kind);
  });
  add(fans, "p", null,
    "A fan is a floor on what a module coordinates, the way a bar is a floor on what a "
    + "caller must learn. Only names this graph holds are counted, so a module reaching "
    + "for the JDK or for a framework reaches nothing here and cannot raise its own score "
    + "by importing more of either. Nor is a collaborator handed in as an argument rather "
    + "than held, or a record this module loads and changes rather than keeps: neither is "
    + "coordination this tool can see, and it does not guess at them. Spellings of a call "
    + "go missed the same way, and the ones known are named here rather than left to be "
    + "found: a call written out in full, package and all (a.b.C.d()), a call through "
    + "something reached through something else (a.b.c()), and a call to a statically "
    + "imported member whose name the module's own body declares, which is read as the "
    + "declaration it also is. On the TypeScript side there are two more of the same kind: "
    + "an import of a directory rather than of a file (./components, where the module is "
    + "the index inside it), and a value a caller reads rather than calls, which is named "
    + "on its module and priced nowhere \u2014 the same answer the Java side gives a public "
    + "field. Each leaves a fan shorter than the source, which is the direction this page "
    + "is willing to be wrong in.");
  add(fans, "p", null,
    "Readings that go the other way \u2014 the ones known, named here rather than left "
    + "to be found. Each one can draw a line to a card the source calls nothing on, and "
    + "each is a name this tool cannot settle without reading Java the way javac reads "
    + "it \u2014 which declaration was in scope where a call was written, and what a type "
    + "outside this source tree declares. A floor whose edge a reader cannot see is not "
    + "one they can trust, and neither is a page that promises a floor while holding a "
    + "reading that is not one:");
  var overstating = add(fans, "ul");
  [
    "a name in an inner scope that borrows a field's name \u2014 a call is followed "
    + "through a field by the name it is written against, and a parameter, a local, a "
    + "catch's variable and a nested class's own field can each be spelled the way a "
    + "field here is. So void go(Other repo) in a module holding a Repo repo puts "
    + "repo.ping() down as a call on the Repo, and can leave the Other undrawn",
    "an enum constant written with arguments \u2014 RED(1) declares a constant and is "
    + "spelled the way a call to RED(1) is. It can only be followed anywhere when the "
    + "same file statically imports a member of that exact name, and then it is read as "
    + "a call to it",
    "a name something outside this source tree declares \u2014 a module built on a "
    + "framework class inherits its member types, and a type this graph does not hold "
    + "cannot be read, so a name javac binds to one of them is followed to a module of "
    + "that name here instead. Where the same is true of a method, the statically "
    + "imported reading is refused outright rather than guessed at",
    "text inside a JSX element, which is read as source \u2014 a TypeScript file is prose "
    + "and code in the same braces, and telling them apart needs a JSX parser whose "
    + "mistakes would blank real code rather than merely add to a fan. So a word in a "
    + "paragraph with a bracket after it can be read as a call, and it draws a line only "
    + "when the file also imports something of exactly that name"
  ].forEach(function (reading) { add(overstating, "li", null, reading); });

  // The one section on this page about a single module, and it is here because the one
  // thing this page can be misread as saying is that a big module is a deep one. Every
  // number in it is the document's own: nothing is counted here, and the line count is
  // printed precisely because it went into none of the others.
  if (document_.scoring.largest !== null) {
    var largest = document_.scoring.largest;
    var size = add(root, "section", "rules");
    add(size, "h2", null, "What a line count is worth here");
    add(size, "p", null,
      "Nothing. No measure on this page reads how long a module is: an interface costs "
      + "what a caller must learn, a fan counts what a module coordinates, and neither "
      + "can be moved by writing more lines. That is not a detail of the implementation "
      + "\u2014 it is the whole reason this page draws shapes rather than sizes, because "
      + "under a measure of implementation lines over interface lines the longest file in "
      + "a repository is always its deepest module.");
    add(size, "p", null,
      "The longest module here is " + largest.moduleId + ", at "
      + count(largest.lines, "line", "lines") + " of " + largest.language + ". It presents "
      + count(largest.methods, "method", "methods") + ", costing "
      + count(largest.interfaceCost, "unit", "units") + " of interface, over a fan of "
      + count(largest.reach, "thing", "things")
      + (largest.leverage === null
         ? ", and no leverage is printed for it because there is no interface cost to "
           + "divide by"
         : " \u2014 leverage " + largest.leverage)
      + ". Its card is below, in " + largest.package + ", drawn to the same scale as every "
      + "other card and read by the same rules. Whether that is a good shape or a bad one "
      + "is a question for whoever reads the file; this page only says what the shape is.");
  }

  var deletion = add(root, "section", "rules");
  add(deletion, "h2", null, "What the verdict says");
  add(deletion, "p", null,
    "Beside each scored module is the verdict of a mechanical deletion test: would "
    + "deleting this module concentrate complexity, or merely move it to the modules that "
    + "were going through it? It is read off three counts printed with it and nothing "
    + "else — how much the module coordinates, how many methods a caller can reach it "
    + "through, and how many modules go through it — so it can be checked rather than "
    + "taken on trust. Reaching a module is going through it, and building one is "
    + "reaching it, so a caller here is any module with a line in its fan to this one.");
  var verdicts = add(deletion, "ul");
  [
    document_.scoring.deletionTest.passThrough,
    document_.scoring.deletionTest.earnsItsKeep,
    document_.scoring.deletionTest.noFinding
  ].forEach(function (answer) {
    var item = add(verdicts, "li");
    add(item, "strong", null, answer.verdict);
    add(item, "span", null, " \u2014 " + answer.because);
  });
  add(deletion, "p", null,
    "The line between them is two numbers in " + document_.scoring.configuration
    + ", beside the weights and the rules for what is reached: a module is "
    + document_.scoring.deletionTest.passThrough.verdict + " when it coordinates no more "
    + "than " + count(document_.scoring.deletionTest.passThrough.reachAtMost.perMethod,
      "thing", "things")
    + " per method it presents — and never fewer than "
    + count(document_.scoring.deletionTest.passThrough.reachAtMost.neverBelow, "thing",
      "things")
    + ", because coordinating one thing is coordinating nothing however few methods that "
    + "is — while "
    + count(document_.scoring.deletionTest.passThrough.callersAtLeast, "module", "modules")
    + " or more go through it. Move either number and every verdict on this page moves "
    + "with it.");
  // Each verdict with its count behind it rather than in front, because the words are the
  // file's and this page cannot conjugate them: "9 earns its keep" is what writing the
  // count first produces, out of a sentence nobody can fix without editing the rule.
  var counted = [];
  document_.scoring.deletionTest.modulesByVerdict.forEach(function (entry) {
    counted.push(entry.verdict + ": " + entry.modules);
  });
  add(deletion, "p", null,
    "Of the " + count(document_.modules.length, "module", "modules") + " drawn here \u2014 "
    + counted.join(", ") + ". The remaining "
    + count(document_.scoring.modulesNeverScored, "module is", "modules are")
    + " never scored, and given no verdict at all: a mechanical judgement on something "
    + "the rules declined to price would be the score they declined to give, wearing a "
    + "word.");
  add(deletion, "p", null,
    "A verdict is only as good as the fan it is read from, and that fan is a floor. A "
    + "module coordinating things this graph does not hold — the JDK, the framework, "
    + "anything outside the source read above — reaches nothing here and reads as "
    + "coordinating nothing, so it can be reported as "
    + document_.scoring.deletionTest.passThrough.verdict + " on a count that is short. "
    + "The numbers are printed beside every verdict for exactly that reason: they are "
    + "what makes one arguable. And a verdict is an observation about deleting a module, "
    + "not a proposal to delete it — nothing here is ranked, and no module here is "
    + "proposed for change.");

  var named = add(rules, "ul");
  var because = {};
  document_.scoring.exclusions.forEach(function (exclusion) {
    because[exclusion.rule] = exclusion.because;
    var item = add(named, "li");
    add(item, "strong", null, exclusion.rule);
    add(item, "span", null,
      " \\u2014 " + count(exclusion.modulesExcluded, "module", "modules") + " \\u2014 "
      + exclusion.because);
  });

  // The scale every bar shares, taken from the document rather than worked out here, so
  // that two bars are comparable against a number a reader can find in the graph.
  var widest = document_.scoring.widestInterface;

  // One term per weight, taken from the weights the document holds rather than from a
  // list written here. A cost is the weighted sum of exactly these counts, so a weight
  // with no term would be part of every total with nothing under it to account for: a
  // module costing 6 read "1 method, 2 parameters, 0 types" while three of the six came
  // from a type the breakdown said there were none of, on the same screen as the
  // paragraph saying what that kind of type costs. Reading the weights out of the
  // document is what makes that impossible rather than merely tested for: a weight this
  // page has no term for arrives on the card as a term saying so.
  //
  // "A type to learn" is all `mustBeLearned` means, and all it can mean: the flag says
  // only that the name is not on the configuration's list of types every caller already
  // knows. These two terms used to claim the type was one this application invented,
  // which is a statement about the source a reader can check — and four of the names it
  // was printed over (ProblemDetail, ResponseEntity, ApplicationListener,
  // ApplicationStartedEvent) are declared nowhere in the source this tool read.
  var terms = [
    {
      weight: "method",
      one: "method",
      many: "methods",
      of: function (interface_) { return interface_.methods.length; }
    },
    {
      weight: "parameter",
      one: "parameter",
      many: "parameters",
      of: function (interface_) {
        var counted = 0;
        interface_.methods.forEach(function (method) { counted += method.parameters.length; });
        return counted;
      }
    },
    {
      weight: "typeToLearn",
      one: "type to learn",
      many: "types to learn",
      of: function (interface_) { return crossing(interface_, true); }
    },
    {
      weight: "typeEveryCallerAlreadyKnows",
      one: "type every caller already knows",
      many: "types every caller already knows",
      of: function (interface_) { return crossing(interface_, false); }
    },
    {
      weight: "refusal",
      one: "refusal",
      many: "refusals",
      of: function (interface_) { return interface_.refusals.length; }
    }
  ];

  function crossing(interface_, mustBeLearned) {
    var counted = 0;
    interface_.typesCrossingTheSeam.forEach(function (type) {
      if (type.mustBeLearned === mustBeLearned) { counted += 1; }
    });
    return counted;
  }

  var termFor = {};
  terms.forEach(function (term) { termFor[term.weight] = term; });

  // The order the counts read in, which is not the order the document happens to list
  // the weights in. A weight with no place here goes last rather than nowhere.
  var reads = ["method", "parameter", "typeToLearn", "typeEveryCallerAlreadyKnows", "refusal"];
  function place(weight) {
    var at = reads.indexOf(weight);
    return at < 0 ? reads.length : at;
  }

  var breakdown = Object.keys(document_.scoring.weights)
    .sort(function (left, right) {
      return place(left) - place(right) || (left < right ? -1 : left > right ? 1 : 0);
    })
    .map(function (weight) {
      return termFor[weight] || {
        weight: weight,
        one: "part costed as " + weight + ", which this page cannot count",
        many: "parts costed as " + weight + ", which this page cannot count",
        of: function () { return 0; }
      };
    });

  // The scale the fans share, from the document for the same reason as the first: two
  // shapes compared by eye are compared against a number a reader can find in the graph.
  var furthest = document_.scoring.widestReach;

  // The shape, drawn to one geometry every card shares: an interface bar as wide as what
  // the module asks of a caller, and under it one line out to each thing it coordinates
  // on that caller's behalf, spread as wide as it reaches. A deep module is a short bar
  // over a wide fan; a bar as wide as its fan is a module coordinating a thing per method.
  // Both halves are drawn from the same document the numbers under them come from.
  //
  // `foot` is the radius of the circle a fan line ends in and `ink` the width that circle
  // is outlined with, so a foot takes up `foot + ink / 2` either side of where it is put.
  // The outermost feet of the widest fan land on the ends of its span, so a span of the
  // whole width has the browser slice them in half against the edges of the viewBox — and
  // it does it on the one card the scale is anchored to, the specimen every other fan on
  // the page is read against. The span is inset by exactly what a foot takes up instead.
  var SHAPE = {width: 200, height: 46, apex: 2, feet: 42, foot: 2, ink: 1};

  // Branching on the fact that carries the exclusion, not on the absent cost that follows
  // from it, for the reason spelled out over `drawInterface` below: a module with one and
  // not the other would throw here, and one throw ends the whole page.
  function drawShape(item, module) {
    var shape = add(item, "div", "shape");
    var excluded = module.excludedBy;
    var track = add(shape, "div", excluded ? "bar unscored" : "bar");
    if (excluded) {
      track.title = "never scored \u2014 " + excluded.rule;
    } else {
      track.title = "interface cost " + module.interface.cost + ", of which "
        + module.interface.refusalCost + " is refusals";
      bandsOf(module).forEach(function (part) {
        var drawn = add(track, "span", part.band);
        drawn.title = part.says;
        drawn.style.width = (widest > 0 ? 100 * part.cost / widest : 0) + "%";
      });
    }
    drawFan(shape, module);
  }

  // The two bands one bar is drawn in, each with the number out of the document it is
  // drawn from. Refusals are the second and not the first, so that the band a reader is
  // looking for is always at the same end of every bar on the page — and both are drawn
  // to the one scale above, so a band is comparable across cards the way a bar is.
  function bandsOf(module) {
    return [
      {
        band: "learn",
        cost: module.interface.costWithoutRefusals,
        says: module.interface.costWithoutRefusals
          + " of this interface is what a caller must learn besides the refusals"
      },
      {
        band: "refuse",
        cost: module.interface.refusalCost,
        says: module.interface.refusalCost + " of this interface is "
          + count(module.interface.refusals.length, "refusal", "refusals")
          + " it can answer with"
      }
    ];
  }

  // One line from under the middle of the bar out to each thing the module coordinates,
  // the whole fan as wide a share of the card as its reach is of the widest reach in the
  // document — the widest one spanning the card less a foot at either end, so that the
  // fan the scale comes from is drawn whole rather than clipped. Two encodings of one
  // number on purpose: the lines can be counted, and the spread can be compared across a
  // page at a glance.
  function drawFan(shape, module) {
    var fan = draw(shape, "svg", "fan", {
      viewBox: "0 0 " + SHAPE.width + " " + SHAPE.height,
      preserveAspectRatio: "xMidYMid meet"
    });
    var reaches = module.reach.reaches;
    var span = furthest > 0 ? (SHAPE.width - 2 * SHAPE.foot - SHAPE.ink) * module.reach.count / furthest : 0;
    reaches.forEach(function (reached, index) {
      var foot = reaches.length === 1
        ? SHAPE.width / 2
        : (SHAPE.width - span) / 2 + span * index / (reaches.length - 1);
      var says = reached.kind + " " + reached.name + " \u2014 " + reached.matched;
      titled(draw(fan, "line", reached.kind, {
        x1: SHAPE.width / 2, y1: SHAPE.apex, x2: foot, y2: SHAPE.feet
      }), says);
      titled(draw(fan, "circle", reached.kind, {
        cx: foot, cy: SHAPE.feet, r: SHAPE.foot
      }), says);
    });
  }

  // What the fan under this module comes to, in words, under the shape that drew it. The
  // kinds read in a fixed order rather than the document's, which sorts them by name.
  var kinds = [
    {kind: "module", one: "module called", many: "modules called"},
    {kind: "adapter", one: "adapter driven", many: "adapters driven"},
    {kind: "record", one: "persistent record kept", many: "persistent records kept"},
    {kind: "transaction", one: "transaction established", many: "transactions established"}
  ];

  function drawReach(item, module) {
    var parts = [];
    var accounted = 0;
    kinds.forEach(function (term) {
      var howMany = 0;
      module.reach.reaches.forEach(function (reached) {
        if (reached.kind === term.kind) { howMany += 1; }
      });
      accounted += howMany;
      if (howMany > 0) { parts.push(count(howMany, term.one, term.many)); }
    });
    if (accounted !== module.reach.count) {
      parts.push(count(module.reach.count - accounted, "thing this page cannot name",
        "things this page cannot name"));
    }
    var reading = add(item, "p", "reach", module.reach.count === 0
      ? "reaches nothing this graph holds"
      : "reaches " + module.reach.count + ": " + parts.join(", "));
    // The ratio and the two numbers it came from, so that a reader can do the division
    // themselves. A depth whose reach is not the reach drawn above it says so: the shape
    // and the number under it are the same claim, and a reader cannot argue with two.
    if (module.depth.reach !== module.reach.count) {
      reading.appendChild(document.createTextNode(
        " \u2014 but this module's depth was taken over a reach of " + module.depth.reach));
    } else if (module.depth.leverage !== null) {
      reading.appendChild(document.createTextNode(
        " \u2014 " + module.depth.leverage + " reached per unit of interface"));
    } else if (module.interface.cost === null) {
      reading.appendChild(document.createTextNode(
        " \u2014 never scored, so there is no interface cost to read it against"));
    } else {
      reading.appendChild(document.createTextNode(
        " \u2014 nothing on the bar to read it against"));
    }
  }

  // The verdict, beside the module it judges, in the document's own words and over the
  // numbers it was read off. Which module is a pass-through, and why, is decided in the
  // configuration and rendered into the graph before this page is written, so nothing
  // here can disagree with the document it carries: the page has no rule in it.
  //
  // Branching on the verdict rather than on `excludedBy`, for the reason spelled out
  // over `drawInterface`: the verdict is the fact that decides whether there is anything
  // to draw, and one throw ends the whole page.
  var passThrough = document_.scoring.deletionTest.passThrough.verdict;

  function drawVerdict(item, module) {
    var test = module.deletionTest;
    if (test.verdict === null) { return; }
    var line = add(item, "p", test.verdict === passThrough ? "verdict found" : "verdict");
    add(line, "strong", null, test.verdict);
    add(line, "span", null,
      " \\u2014 coordinates " + count(test.reach, "thing", "things") + " behind "
      + count(test.methods, "method", "methods") + " a caller can reach, with "
      + count(test.callers, "module", "modules") + " going through it");
    line.title = test.because;
    // The three counts are the argument for the word in front of them, so they have to be
    // the counts the rest of the card was drawn from. A verdict taken over some other
    // reach, or over some other number of callers, is a second claim on one card, and a
    // reader cannot argue with two.
    if (test.reach !== module.reach.count || test.callers !== module.callers.count) {
      add(line, "span", null,
        " \\u2014 but the fan above it draws " + module.reach.count + " and this module's "
        + "callers number " + module.callers.count + ", so this verdict was read off "
        + "counts the rest of this card was not drawn from");
    }
  }

  // The refusals this module can answer with, named under the shape that drew their band,
  // and each of them coloured by whether the two sides of it agree. Drawn for a module no
  // rule scores too: the names are facts about the source rather than a score, and the
  // band above them is the only thing on such a card that is absent.
  function drawRefusals(item, module) {
    var refusals = module.interface.refusals;
    if (refusals.length === 0) { return; }
    var disagreeing = {};
    module.findings.forEach(function (finding) { disagreeing[finding.refusal] = finding; });
    var line = add(item, "p", "refusals",
      "refuses with " + refusals.length + ": ");
    refusals.forEach(function (refusal, index) {
      if (index > 0) { line.appendChild(document.createTextNode(", ")); }
      var finding = disagreeing[refusal.name];
      var marked = "refusal";
      var says = "documented by " + refusal.documentedBy.join(", ")
        + ", and raised by this module";
      if (finding) {
        marked = "refusal found";
        says = finding.finding + " \\u2014 " + finding.because;
      } else if (!refusal.checked) {
        // Neither a finding nor a module keeping its word: a promise this tool never
        // held against anything. Marked apart from both, because reading it as
        // agreement is exactly the false confidence the band exists to avoid.
        marked = "refusal unchecked";
        says = "documented by " + refusal.documentedBy.join(", ")
          + ", and whether this module raises it is not something this tool could read";
      }
      var named_ = add(line, "span", marked, refusal.name);
      named_.title = says;
    });
  }

  // A disagreement, with both sides of it in the sentence rather than only the side the
  // finding is unhappy with: which methods promise the refusal, and whether anything in
  // the module throws it. A machine saying "wrong" without saying against what is not a
  // finding anybody can check.
  function drawFindings(item, module) {
    module.findings.forEach(function (finding) {
      var line = add(item, "p", "finding");
      add(line, "strong", null, finding.finding);
      add(line, "span", null, " \u2014 " + finding.refusal + " is "
        + (finding.documented
          ? "documented by " + finding.documentedBy.join(", ")
          : "documented by no method a caller can reach")
        + ", and "
        + (finding.raised
          ? "raised in this module's body"
          : "raised nowhere this tool can read in it"));
      line.title = finding.because;
    });
  }

  // A zero read on its own says a caller has nothing to learn here, which is a stronger
  // claim than this bar ever makes. A module whose only member is a constructor reads
  // zero: a caller writing `new` against it has that constructor and every type crossing
  // it to learn, and a bar counts none of it. One string, because a zero is printed in
  // two places — the card, and the panel that opens from it — and a caveat the card
  // carries while the panel drops it is worse than no caveat at all: the panel is where
  // a reader goes to check the card, so it is the last place that should claim more.
  var NOT_THE_SAME_AS_NOTHING_TO_LEARN =
    " — nothing this bar counts, which is not the same as nothing to learn";

  // Branching on the fact that carries the exclusion, not on the absent cost that
  // follows from it. Reading the rule off `excludedBy` after deciding on `cost === null`
  // would throw for a module that had one without the other, and the renderer is one
  // pass: a throw abandons every package section after it, which reads as a page that
  // ends early rather than as a page that failed.
  function drawInterface(item, module) {
    if (module.excludedBy) {
      var never = add(item, "p", "unscored", "never scored \\u2014 " + module.excludedBy.rule);
      never.title = module.excludedBy.matched + ". " + because[module.excludedBy.rule];
      return;
    }
    var parts = [];
    var added = 0;
    breakdown.forEach(function (term) {
      var howMany = term.of(module.interface);
      parts.push(count(howMany, term.one, term.many));
      added += howMany * document_.scoring.weights[term.weight];
    });
    var reading = add(item, "p", "cost",
      module.interface.cost + " to learn: " + parts.join(", "));
    // The breakdown is the argument for the number above it, so it has to come to that
    // number. A term counting the wrong thing is invisible in a total and obvious here,
    // and the whole claim of the page is that a score can be argued with — which a
    // reader cannot do with parts that do not add up and nothing saying so.
    if (added !== module.interface.cost) {
      reading.appendChild(document.createTextNode(
        " \u2014 but these counts come to " + added
        + ", so this page is counting something the score did not"));
    } else if (module.interface.cost === 0) {
      // Nothing else on the card tells that zero from a module that declares nothing
      // at all, so the caveat is said here rather than left to be inferred.
      reading.appendChild(
        document.createTextNode(NOT_THE_SAME_AS_NOTHING_TO_LEARN));
    }
  }

  // Behind the shape: everything this document holds about one module, opened from its
  // card. The shape above makes the claim; this is where a reader checks it.
  //
  // Nothing in here counts anything. Every number printed below is one the document
  // already carries — a method's own cost, the verdict's own three counts, the reach and
  // caller counts beside them — and a list is asked only whether it is empty. That is the
  // whole rule, and it is what makes the panel and a later tool reading the graph the same
  // facts: a count worked out at render time would be a second measurement standing beside
  // the one in the file, and a reader could not tell which of the two the shape was drawn
  // from.
  var BEHIND_NAME = "behind-the-shape";
  // The one button that opens and closes things: `mousedown` and `mouseup` fire for every
  // button on the mouse, and neither of this page's gestures is meant for the one that
  // opens a context menu.
  var PRIMARY_BUTTON = 0;

  // Asking for a context menu, whichever way the reader asks. The secondary button is the
  // half that is easy to see; the other is Ctrl+click, which is how macOS asks and which
  // arrives as the primary button with `ctrlKey` set, so a rule reading `button` alone
  // lets it straight through. Neither is a gesture about this panel: acting on one shuts
  // the panel under the menu the reader just asked for, and the browser already leaves a
  // context menu inside the panel alone, so the two ends of the page would disagree about
  // the same gesture. The card reads it too, so that a press which was never going to
  // open anything is not recorded as the start of a click.
  function opensAContextMenu(event) {
    return event.button !== PRIMARY_BUTTON || event.ctrlKey;
  }
  var panel = add(root, "dialog", "behind");
  panel.setAttribute("aria-labelledby", BEHIND_NAME);
  var behindBody = add(panel, "div", "behindBody");
  // Focusable, so that a keyboard can scroll a panel longer than the screen. It is also
  // what `showModal` puts the focus on, which is where a reader wants it: the panel's own
  // text, rather than the button that dismisses it.
  behindBody.setAttribute("tabindex", "0");

  // The card a panel opened from, held only where the browser will not hold it for us.
  // A dialog opened with `showModal` records where focus came from and hands it back on
  // close by itself, and two of us doing it is worse than one: the `close` event is a
  // queued task rather than a call, so a second restoration lands *after* the browser's
  // and can take focus off whatever the reader moved to in between.
  var openedFrom = null;

  function restoreFocus() {
    if (openedFrom) {
      openedFrom.focus();
      openedFrom = null;
    }
  }

  panel.addEventListener("close", restoreFocus);
  // A click on the backdrop lands on the dialog itself and never on anything inside it,
  // which is the only way to tell the two apart. Where the click came up is not enough
  // on its own, though: a click's target is the nearest common ancestor of where the
  // pointer went down and where it came up, so a selection dragged from inside the panel
  // and released past its edge reports the dialog as well. This panel is full of
  // fully-qualified caller ids and method signatures a reader will want to copy, and
  // closing there takes the selection with it. So the press and the release are read
  // separately and both have to have landed on the backdrop — which is the same rule the
  // browser's own light dismiss uses, and it also leaves a press that began on the
  // backdrop and ended inside the panel alone, the way a button ignores a release
  // dragged off it.
  //
  // Nothing that asks for a context menu, because that is the other half of the rule
  // light dismiss uses: `mousedown` and `mouseup` fire for every button and for macOS's
  // Ctrl+click, so without the guard a press beside the panel meant for the menu
  // dismisses it — and a context menu opening over the page the panel just left is a menu
  // about nothing. Such a press inside the panel already leaves it alone, so the guard is
  // also what stops the two ends disagreeing.
  var pressedOn = null;
  panel.addEventListener("mousedown", function (event) {
    pressedOn = opensAContextMenu(event) ? null : event.target;
  });
  panel.addEventListener("mouseup", function (event) {
    if (event.target === panel && pressedOn === panel) { shutBehind(); }
  });
  // Escape closes a modal dialog without this, and closing a closed one does nothing, so
  // this costs nothing there. It is for the fallback below: a panel opened as an ordinary
  // dialog would otherwise have no way out of it but the mouse.
  panel.addEventListener("keydown", function (event) {
    if (event.key === "Escape") { shutBehind(); }
  });

  function openBehind(module, from) {
    while (behindBody.firstChild) { behindBody.removeChild(behindBody.firstChild); }
    drawBehind(behindBody, module);
    // The card takes the focus before the panel opens, because that is what the browser
    // writes down as the place to hand focus back to when the panel closes. Left out, a
    // click on the card body focused nothing at all, so closing dropped the keyboard on a
    // hidden element and a reader pressing Tab started again from the top of the page.
    from.focus();
    if (panel.showModal) {
      panel.showModal();
    } else {
      // A browser with no modal dialog in it. Nothing records where focus came from and
      // nothing hands it back, so this page does both.
      openedFrom = from;
      panel.setAttribute("open", "open");
    }
    // Open at the top, every time. `behindBody` outlives every open — it is emptied and
    // refilled, never rebuilt — and a browser keeps the scroll offset of an element it
    // has hidden, so without this the place a reader left one module's panel is where the
    // next module's panel opens, with the whole interface section scrolled off above the
    // fold. The sticky heading is what makes it worth a line: the panel does not look
    // wrong, it is just showing the wrong part of itself. Set once the panel is open,
    // because an element with no layout box has no scroll position to move.
    behindBody.scrollTop = 0;
    behindBody.focus();
  }

  function shutBehind() {
    if (panel.close) {
      panel.close();
      return;
    }
    panel.removeAttribute("open");
    restoreFocus();
  }

  function behindPart(into, heading) {
    var part = add(into, "section", "behindPart");
    add(part, "h4", null, heading);
    return part;
  }

  function drawBehind(into, module) {
    var head = add(into, "header", "behindHead");
    var says = add(head, "div");
    add(says, "h3", null, module.name).id = BEHIND_NAME;
    add(says, "p", "where",
      module.kind + " · " + module.language + " · " + module.package);
    add(says, "p", "where",
      module.root + "/" + module.path + " · " + count(module.lines, "line", "lines"));
    var shut = add(head, "button", "close", "Close");
    shut.setAttribute("type", "button");
    shut.addEventListener("click", shutBehind);

    drawBehindInterface(into, module);
    drawBehindReach(into, module);
    drawBehindCallers(into, module);
    drawBehindVerdict(into, module);
    drawBehindFindings(into, module);
  }

  // What a caller has to learn, method by method. A module no rule scores gets the rule
  // that excluded it here instead of a cost: printing one anyway — even a zero — would be
  // the score the rules declined to give, wearing a number.
  function drawBehindInterface(into, module) {
    var part = behindPart(into, "What it costs a caller");
    if (module.excludedBy) {
      add(part, "p", "unscored", "never scored — " + module.excludedBy.rule);
      add(part, "p", "because",
        module.excludedBy.matched + ". " + because[module.excludedBy.rule]);
    } else {
      var split = add(part, "p", null,
        "interface cost " + module.interface.cost + " — "
        + module.interface.costWithoutRefusals + " of it is what a caller must learn "
        + "besides the refusals, and " + module.interface.refusalCost + " of it is the "
        + "refusals it can answer with");
      // The same caveat the card carries, in the same words, because this is where a
      // reader comes to check that card. Splitting a zero into two zeroes says how the
      // bar got there and still not what it left out.
      if (module.interface.cost === 0) {
        split.appendChild(
          document.createTextNode(NOT_THE_SAME_AS_NOTHING_TO_LEARN));
      }
      var over = add(part, "p", null,
        "reach " + module.depth.reach + " over interface cost " + module.depth.interfaceCost
        + (module.depth.leverage === null
          ? " — nothing on the bar to read it against"
          : " — " + module.depth.leverage + " reached per unit of interface"));
      // The same guard the card carries over its fan, said here because this panel prints
      // the depth's reach in one section and lists the reach itself in the next. The card
      // can be argued with by opening the panel; the panel is where that argument stops,
      // so a disagreement it printed in silence is one nothing else would catch. Both
      // numbers are the document's own — neither is counted here — and all this adds is
      // which of the two the reader is looking at.
      if (module.depth.reach !== module.reach.count) {
        over.appendChild(document.createTextNode(
          " — but this panel lists " + module.reach.count + " below, so the depth above "
          + "was taken over a reach this panel does not show"));
      }
    }
    drawBehindMethods(part, module);
    drawBehindTypes(part, module);
    drawBehindRefusals(part, module);
  }

  function drawBehindMethods(part, module) {
    var methods = module.interface.methods;
    if (methods.length === 0) {
      add(part, "p", "none", "No method here is reachable from outside this module.");
      return;
    }
    add(part, "p", null,
      count(module.deletionTest.methods, "method a caller can reach",
        "methods a caller can reach") + ":");
    var listed = add(part, "ul", "methods");
    methods.forEach(function (method) {
      var item = add(listed, "li");
      add(item, "code", null,
        method.visibility + " " + method.returns + " " + method.name
        + "(" + method.parameters.join(", ") + ")");
      add(item, "span", "says", method.cost === null
        ? " — never priced"
        : " — costs " + method.cost);
      if (method.documentedRefusals.length === 0) { return; }
      add(item, "span", "says",
        ", and documents " + method.documentedRefusals.join(", "));
    });
  }

  function drawBehindTypes(part, module) {
    var types = module.interface.typesCrossingTheSeam;
    if (types.length === 0) {
      add(part, "p", "none", "No type crosses this seam.");
      return;
    }
    var line = add(part, "p", null, "types crossing the seam: ");
    types.forEach(function (type, index) {
      if (index > 0) { line.appendChild(document.createTextNode(", ")); }
      add(line, "code", null, type.name);
      // Which side of the configuration's list a name falls on is part of what the
      // module was priced at, so it is left off a module nothing priced.
      if (module.excludedBy) { return; }
      add(line, "span", "says", type.mustBeLearned
        ? " (a type to learn)"
        : " (a type every caller already knows)");
    });
  }

  function drawBehindRefusals(part, module) {
    var refusals = module.interface.refusals;
    if (refusals.length === 0) {
      add(part, "p", "none", "This module answers with no refusal this tool could read.");
      return;
    }
    add(part, "p", null, "refusals it can answer with:");
    var listed = add(part, "ul", "refusals");
    refusals.forEach(function (refusal) {
      var item = add(listed, "li");
      add(item, "code", null, refusal.name);
      add(item, "span", "says", " — " + documentedBy(refusal) + ", and "
        + (refusal.checked
          ? raisedOrNot(refusal)
          : "whether this module raises it is not something this tool could read"));
    });
  }

  // Both sides of a refusal, in the words the card under the shape uses for them, so that
  // a reader who opened the panel to check the card is reading the same sentence twice.
  function documentedBy(refusal) {
    return refusal.documented
      ? "documented by " + refusal.documentedBy.join(", ")
      : "documented by no method a caller can reach";
  }

  function raisedOrNot(refusal) {
    return refusal.raised
      ? "raised in this module's body"
      : "raised nowhere this tool can read in it";
  }

  function drawBehindReach(into, module) {
    var part = behindPart(into, "What it coordinates on a caller's behalf");
    var reaches = module.reach.reaches;
    if (reaches.length === 0) {
      add(part, "p", "none", "This module reaches nothing this graph holds.");
      return;
    }
    add(part, "p", null, "reaches " + module.reach.count + ":");
    var listed = add(part, "ul", "reaches");
    reaches.forEach(function (reached) {
      var item = add(listed, "li");
      // Prefixed rather than written as the bare kind, because one of the four words the
      // document uses for a kind is `module`, and `.module` is the card: a span wearing
      // that word was drawn inside the panel with the card's own border, background and
      // padding, as a little box around the word MODULE and around nothing else.
      add(item, "span", "kind kind-" + reached.kind, reached.kind);
      item.appendChild(document.createTextNode(" "));
      add(item, "code", null, reached.name);
      add(item, "span", "says", " — " + reached.matched);
      // The id as well as the name, where the document holds one: two modules in two
      // packages can be called the same thing, and a name on its own would send a reader
      // to whichever card they found first. On a line of its own, because run into the
      // sentence above it the id doubled the length of every entry in the fan.
      if (reached.moduleId === null) { return; }
      add(item, "code", "says id", reached.moduleId);
    });
  }

  function drawBehindCallers(into, module) {
    var part = behindPart(into, "Which modules go through it");
    var callers = module.callers.moduleIds;
    if (callers.length === 0) {
      add(part, "p", "none", "No module in this graph calls this one.");
      return;
    }
    add(part, "p", null,
      count(module.callers.count, "module goes", "modules go") + " through it:");
    var listed = add(part, "ul", "callers");
    callers.forEach(function (id) {
      add(add(listed, "li"), "code", null, id);
    });
  }

  // The verdict with the three counts it was read off and the rule that produced it, all
  // four out of the document. A module no rule scores gets no verdict at all, and the
  // panel says that rather than leaving the section out.
  function drawBehindVerdict(into, module) {
    var part = behindPart(into, "The deletion test");
    var test = module.deletionTest;
    if (test.verdict === null) {
      add(part, "p", "none",
        "This module is never scored, so the deletion test gives it no verdict.");
      return;
    }
    var line = add(part, "p", test.verdict === passThrough ? "verdict found" : "verdict");
    add(line, "strong", null, test.verdict);
    add(line, "span", "says",
      " — coordinates " + count(test.reach, "thing", "things") + " behind "
      + count(test.methods, "method", "methods") + " a caller can reach, with "
      + count(test.callers, "module", "modules") + " going through it");
    // The three counts are the argument for the word in front of them, and two of the
    // three are listed out in full further up this same panel. A verdict read off some
    // other reach, or some other number of callers, is a second claim inside one panel —
    // and a reader who opened the panel to check the card has nowhere further to go.
    //
    // In the panel's ordinary ink rather than the softer one the sentence before it is
    // drawn in, and the same way the guard over the depth is drawn: `.says` is for the
    // prose supporting a reading, and a reading contradicting itself is not that.
    if (test.reach !== module.reach.count || test.callers !== module.callers.count) {
      line.appendChild(document.createTextNode(
        " — but this panel lists " + module.reach.count + " reached and "
        + module.callers.count + " going through it, so this verdict was read off counts "
        + "the rest of this panel was not drawn from"));
    }
    add(part, "p", "because", test.because);
  }

  function drawBehindFindings(into, module) {
    var part = behindPart(into, "Findings against it");
    if (module.findings.length === 0) {
      add(part, "p", "none", "There is no finding against this module.");
      return;
    }
    var listed = add(part, "ul", "findings");
    module.findings.forEach(function (finding) {
      var item = add(listed, "li", "finding");
      add(item, "strong", null, finding.finding);
      add(item, "span", "says",
        " — " + finding.refusal + " is " + documentedBy(finding) + ", and "
        + raisedOrNot(finding));
      add(item, "p", "because", finding.because);
    });
  }

  // Where the pointer went down on a card, so that the click which follows can tell a
  // click from a drag. The whole card is the control, and a card is text a reader wants:
  // module names, the cost line, `pass-through — coordinates 5 things behind 11 methods`.
  // A click's target is the nearest common ancestor of the press and the release, so a
  // drag across a card's own text reports the card — and a handler reading only the click
  // throws a modal over the page and takes the half-made selection with it, because
  // opening a panel moves the focus and a focus move collapses a selection. This is the
  // rule the backdrop one element up already uses, said for a control whose two ends are
  // both inside itself: there, press and release have to have landed on the same element;
  // here, in the same place. The gesture no single click can see — the double-click, and
  // the triple-click behind it — is the other half of the same problem, and is waited out
  // rather than measured.
  var pressedAt = null;
  // A trackpad click drifts a pixel or two under a steady hand, so an exact match would
  // make the cards feel broken. Wider than the drift and narrower than a word.
  var A_STEADY_HAND = 3;
  // How long a second click has to arrive in and still be the same gesture. Windows,
  // macOS and the engines that follow them all default to half a second, and there is no
  // way to ask a browser what its own figure is. This is what the opening waits out; see
  // `openWhenNoSecondClickFollows` for why it has to wait at all.
  var A_SECOND_CLICK = 500;
  // The opening a first click asked for, while it is still waiting to find out whether a
  // second one is coming.
  var opening = null;

  function pressedOnACard(event) {
    // A second press of one gesture, arriving before the first click's panel has opened:
    // the reader is taking a word out of the card, not asking for the panel. Called off
    // here rather than on the click that follows, which is a whole click later, and this
    // is the moment the browser itself selects the word.
    if (event.detail > 1) { holdTheOpening(); }
    pressedAt = opensAContextMenu(event)
      ? null
      : {x: event.clientX, y: event.clientY};
  }

  // Read once and cleared, because a press that never became a click — dragged off the
  // card and released on the page — must not still be standing behind the next one.
  function aDragRatherThanAClick(event, card) {
    var at = pressedAt;
    pressedAt = null;
    // A click with no mouse behind it at all: Enter on the name button, which is how a
    // keyboard opens the panel and which has to keep working. `detail` counts the clicks
    // of the pointer that raised the event, and for the keyboard's there were none.
    if (event.detail === 0) { return false; }
    if (at === null) { return true; }
    if (Math.abs(event.clientX - at.x) > A_STEADY_HAND) { return true; }
    if (Math.abs(event.clientY - at.y) > A_STEADY_HAND) { return true; }
    // A press and a release in the same place that still left text selected: a drag of a
    // pixel or two across a word, which is a selection at the one size the distance above
    // cannot see. Taking a *whole* word out of a card is the double-click, and that one
    // no single click can recognise; it is waited out below rather than measured here.
    return selectionInside(card);
  }

  // A click cannot tell whether it is the whole gesture or the first half of a
  // double-click: `detail` is 1 either way, nothing has been selected yet and the pointer
  // has not moved, so every guard above says "a click". Opening there is what threw a
  // modal over a reader double-clicking a module name — the ordinary way to take one off
  // a card — and left them with the panel's own text selected instead of the name, or,
  // for a card in an outer column, with the second click landing on the backdrop and
  // dismissing the panel again: neither the word nor the panel.
  //
  // So the opening waits out the interval a second click has to arrive in, and the press
  // of that second click calls it off. The cost is that a card opens half a beat after
  // the click rather than under it; the alternative is that the browser's own way of
  // taking a word off a page does not work on this one.
  //
  // The keyboard's Enter is not a gesture that can grow: `detail` is 0, there is no
  // second Enter to wait for, and it opens straight away.
  function openWhenNoSecondClickFollows(event, open) {
    holdTheOpening();
    if (event.detail === 0) {
      open();
      return;
    }
    if (event.detail > 1) { return; }
    opening = window.setTimeout(function () {
      opening = null;
      open();
    }, A_SECOND_CLICK);
  }

  function holdTheOpening() {
    if (opening !== null) {
      window.clearTimeout(opening);
      opening = null;
    }
  }

  function selectionInside(card) {
    var selected = window.getSelection ? window.getSelection() : null;
    if (!selected || selected.isCollapsed || selected.rangeCount === 0) { return false; }
    return card.contains(selected.getRangeAt(0).commonAncestorContainer);
  }

  // Flows through the modules: three business events, each traceable across everything it
  // passes through. The path is the document's own — walked out of the graph's reach
  // before the page was written — and everything below reads it rather than working it
  // out, for the same reason the panel does: a page that walked the fans itself would be
  // a second tracing beside the document's, and a reader could not tell which of the two
  // the highlighting on the cards came from.

  // The flow a reader has chosen, and the buttons that choose one. Held, because choosing
  // a second flow has to un-choose the first: two flows highlighted at once would be two
  // orders drawn over one set of cards, and neither readable.
  var flowButtons = [];
  // The step numbers put on the cards of the chosen flow, so that choosing another can
  // take them off again. A card carries at most one, because a flow passes through a
  // module once however many times it is called.
  var flowBadges = [];

  function drawFlows(section) {
    add(section, "h2", null, "Flows through the modules");
    add(section, "p", null,
      "Each of these is one thing this application does, end to end. Choosing one "
      + "highlights every module it passes through and numbers them in the order it "
      + "passes through them, while everything else fades \u2014 so what one business "
      + "event sets in motion can be told from what it does not. Choosing it again, or "
      + "pressing Clear, puts every module back on an equal footing.");
    add(section, "p", null,
      "No flow is a path anybody wrote down. Each is defined in "
      + document_.scoring.configuration + " by its entry point alone \u2014 one module, "
      + "and one method a caller calls on it \u2014 and the path is walked out of the "
      + "source from there: the calls that method's body makes, in the order Java "
      + "evaluates them, then the calls each called body makes, until the walk runs out "
      + "of bodies this source holds. A call on the module's own method is followed and "
      + "is no step of its own, since the flow is already there; a module called twice "
      + "keeps the step it was first entered at, since a flow is a path through modules "
      + "rather than a transcript of calls.");
    add(section, "p", null,
      "The grain is the method, and that is the whole of what makes this a flow rather "
      + "than a claim about a class. Walked over the fans on the cards instead, a flow is "
      + "its entry module's entire transitive reach, in the order the fan happens to be "
      + "sorted in, and two flows differing only in the method they name come out "
      + "identical \u2014 a fan is deliberately a set, so that no module can raise its "
      + "depth by writing more calls, and a set has neither an order nor any idea which "
      + "method wrote it.");
    add(section, "p", null,
      "A path is a floor, for the same reasons a fan is, and in the same direction. Only "
      + "names this graph holds are followed, so a call into the JDK or into a framework "
      + "is no step. Nor is a call this reading cannot follow to a module: a call on the "
      + "result of another call (a.b().c()), a call on a parameter or a local rather than "
      + "on a field, a method inherited from a type outside this source. Nothing about "
      + "which branch runs is modelled either \u2014 a refusal a call can answer with is "
      + "on the flow whether or not a given deposit trips it, because this is a reading "
      + "of the source and not a trace of one run. So a flow cannot go on describing an "
      + "application this source no longer holds: where the walk cannot be made, the flow "
      + "is drawn with no path at all rather than with a shorter one, and says which of "
      + "the four ways it failed.");
    if (document_.flows.length === 0) {
      add(section, "p", "none",
        "This configuration names no flow, so there is nothing here to trace.");
      return;
    }
    // A flow this graph could not walk, named where a reader will look for it rather than
    // left as a button that does nothing. Drawn above the chooser, because the reason is
    // the answer to the question the greyed-out button raises.
    document_.flows.forEach(function (flow) {
      if (flow.resolved) { return; }
      var untraced = add(section, "div", "untraced");
      add(untraced, "strong", null, flow.flow);
      add(untraced, "span", null,
        " cannot be traced through this graph: " + flow.couldNotResolve
        + ". It is drawn with no path rather than with a shorter one, because a flow half "
        + "walked is the one output worse than no flow at all \u2014 every module on it "
        + "real, the path followable, and the event it claims to trace no longer "
        + "happening that way.");
    });
    var chooser = add(section, "ul", "chooser");
    var says = add(section, "div", "chosen");
    document_.flows.forEach(function (flow) {
      var chooses = add(add(chooser, "li"), "button", "flow", flow.flow);
      chooses.setAttribute("type", "button");
      if (!flow.resolved) {
        // Not a control at all. Pressing it could only highlight a path that is not
        // there, and `aria-pressed` on something nobody can press says a state about a
        // flow that has none.
        chooses.disabled = true;
        chooses.title = flow.couldNotResolve;
        return;
      }
      chooses.setAttribute("aria-pressed", "false");
      chooses.title = flow.because;
      flowButtons.push({flow: flow, button: chooses});
      // A toggle, because `aria-pressed` says it is one. Pressing the chosen flow again
      // used to re-apply the state it already had, so a screen-reader user pressing it to
      // un-choose was told nothing had changed and had to go and find Clear.
      chooses.addEventListener("click", function () {
        if (chooses.getAttribute("aria-pressed") === "true") { clearFlow(says); }
        else { chooseFlow(flow, says); }
      });
    });
    var clears = add(add(chooser, "li"), "button", "clear", "Clear");
    clears.setAttribute("type", "button");
    clears.title = "Show every module equally again";
    clears.addEventListener("click", function () { clearFlow(says); });
  }

  function chooseFlow(flow, says) {
    var onIt = {};
    flow.path.forEach(function (step) { onIt[step.moduleId] = step; });
    flowButtons.forEach(function (each) {
      each.button.setAttribute("aria-pressed", each.flow === flow ? "true" : "false");
    });
    takeTheStepsOff();
    eachCard(function (card, id) {
      var step = onIt[id];
      // The class written whole rather than added to, because it is the whole of what a
      // card's class says: a card is on the chosen flow, beside it, or on no flow at all.
      // Adding and removing two names left a card wearing both the first time a reader
      // chose a second flow.
      card.className = step ? "module onTheFlow" : "module aside";
      if (step) { putTheStepOn(card, step); }
    });
    drawTheChosenFlow(says, flow);
  }

  function clearFlow(says) {
    flowButtons.forEach(function (each) {
      each.button.setAttribute("aria-pressed", "false");
    });
    takeTheStepsOff();
    eachCard(function (card) { card.className = "module"; });
    empty(says);
  }

  function eachCard(does) {
    for (var id in cardFor) {
      if (Object.prototype.hasOwnProperty.call(cardFor, id)) { does(cardFor[id], id); }
    }
  }

  function putTheStepOn(card, step) {
    var badge = add(card, "span", "step", step.step);
    badge.title = "step " + step.step + " of this flow \u2014 " + step.matched
      + (step.calledFrom === null ? "" : ", written in " + step.calledFrom);
    // First on the card, so the number sits beside the module's name rather than under
    // whichever part of the shape happened to be drawn last.
    card.insertBefore(badge, card.firstChild);
    flowBadges.push(badge);
  }

  function takeTheStepsOff() {
    flowBadges.forEach(function (badge) { badge.parentNode.removeChild(badge); });
    flowBadges = [];
  }

  function empty(element) {
    while (element.firstChild) { element.removeChild(element.firstChild); }
  }

  // The chosen flow written out in order, under the chooser, because a highlight spread
  // over a page taller than the screen is not an order anybody can read. Each module is a
  // button that scrolls to its card: the list says what the order is, the cards say what
  // each module in it looks like, and a reader moves between the two.
  function drawTheChosenFlow(says, flow) {
    empty(says);
    add(says, "p", null, flow.because);
    add(says, "p", null,
      "Entered by calling " + flow.entryPoint.method + " on "
      + flow.entryPoint.moduleId + ", and traced from there through "
      + count(flow.modules, "module", "modules") + ", in this order:");
    var listed = add(says, "ol", "path");
    flow.path.forEach(function (step) {
      var item = add(listed, "li");
      var at = add(item, "button", "at", step.name);
      at.setAttribute("type", "button");
      at.title = step.moduleId;
      at.addEventListener("click", function () { cardFor[step.moduleId].scrollIntoView(); });
      add(item, "span", null,
        " \u2014 " + (step.reachedFrom === null
          ? step.matched
          : byId[step.reachedFrom].name + "." + step.calledFrom + " writes "
            + step.call + ", " + step.matched));
    });
  }

  var byId = {};
  document_.modules.forEach(function (module) { byId[module.id] = module; });

  // Flows through the modules: the business events this application actually performs,
  // each traceable across everything it passes through. The section is put on the page
  // here — above the cards, because that is what choosing a flow changes — and filled in
  // once those cards exist, since choosing a flow is a thing done to them.
  //
  // Nothing about a path is worked out here. Which modules a flow passes through, and in
  // what order, is walked out of the graph's own reach before the page is written and
  // arrives in the document as a numbered list; this reads that list. A page that walked
  // the fans itself would be a second tracing standing beside the document's, and a
  // reader could not tell which of the two the highlighting came from.
  var flows = add(root, "section", "rules flows");
  // The card each module is drawn on, by id, so that choosing a flow can reach the cards
  // it passes through without going looking for them in the page.
  var cardFor = {};

  document_.packages.forEach(function (package_) {
    var section = add(root, "section", "package");
    var header = add(section, "header");
    add(header, "h2", null, package_.name);
    add(header, "span", null, count(package_.moduleIds.length, "module", "modules"));
    var modules = add(section, "ul", "modules");
    package_.moduleIds.forEach(function (id) {
      var module = byId[id];
      var item = add(modules, "li", "module");
      cardFor[id] = item;
      // The whole card is the control, and the name inside it is a real button: a
      // pointer opens the panel by clicking anywhere on the card, and a keyboard opens
      // the same panel by tabbing to the name and pressing Enter, whose click bubbles up
      // to the one opening handler here. Written with a second one on the button, a click
      // on the name would have opened the panel twice. The press beside it arms nothing
      // and opens nothing: it records where the gesture began, so that the click can tell
      // a reader who meant to open this card from one who meant to select from it, and it
      // calls off an opening if a second press shows the gesture was a double-click. The
      // click asks for the panel rather than opening it, for the same reason.
      var opens = add(item, "button", "name", module.name);
      opens.setAttribute("type", "button");
      opens.setAttribute("aria-haspopup", "dialog");
      item.addEventListener("mousedown", pressedOnACard);
      item.addEventListener("click", function (event) {
        if (aDragRatherThanAClick(event, item)) { return; }
        openWhenNoSecondClickFollows(event, function () {
          openBehind(module, opens);
        });
      });
      add(item, "div", "kind", module.kind);
      drawShape(item, module);
      drawInterface(item, module);
      drawRefusals(item, module);
      drawReach(item, module);
      drawVerdict(item, module);
      drawFindings(item, module);
      if (module.nested.length > 0) {
        add(item, "p", "nested", "nested: " + module.nested.join(", "));
      }
    });
  });

  drawFlows(flows);

  add(root, "footer", null,
    "Rendered from the graph document carried inside this file. Nothing is fetched, and "
    + "nothing is shown that the document does not contain.");
}());
"""


def _embeddable(text):
    """JSON safe to sit inside a script element: no sequence a parser could end it on."""
    return text.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")


def render(document, serialised):
    """The whole page as bytes: markup, style, the graph document, and the renderer.

    The graph arrives already serialised, as the exact bytes the graph file is written
    from, rather than being serialised a second time here. That is what makes "the page
    carries the document the graph file holds" structural: there is one serialisation in
    the run, so the two outputs cannot drift apart even in principle.
    """
    embedded = _embeddable(serialised.decode("utf-8").rstrip("\n"))
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
            "<script>"
            + _SCRIPT.replace("GRAPH_ELEMENT_ID", GRAPH_ELEMENT_ID).replace(
                "GRAPH_SCHEMA", graph.SCHEMA
            )
            + "</script>",
            "</body>",
            "</html>",
            "",
        ]
    )
    log.debug("page rendered bytes=%d modules=%d", len(html.encode("utf-8")), len(document["modules"]))
    return html.encode("utf-8")
