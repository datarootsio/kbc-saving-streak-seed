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
   that page works out per module is the one a reader has to be able to check against the
   document, and a fixed swatch beside it would be a second thing to read past. */
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
    "Every module this application is made of, at class grain, grouped by the package it "
    + "lives in, each drawn as what its interface costs a caller over a fan of everything "
    + "it coordinates on that caller's behalf. An observation of the source it was "
    + "generated from, and nothing more: nothing here is ranked, and no module here is "
    + "proposed for change.");

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
    + document_.scoring.refusals.refusalsRead + " are read across this page.");
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
    + "neither: what a module documents is the @throws written over a method a caller can "
    + "reach, and what it raises is what its body throws. Where the two disagree the card "
    + "says so, naming the refusal and both sides of it:");
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
    + "somethingElse.build() \u2014 needs a type this tool never resolves, so it is not "
    + "read and not guessed at; a refusal one module raises by calling another is the "
    + "second module's, drawn there; and a prose sentence about when something fails is "
    + "part of the interface and is measured nowhere. So a band at nothing says only that "
    + "there was nothing here to read, and a documented but never raised finding can be "
    + "a spelling this tool cannot follow rather than a promise the code broke. Both "
    + "sides are printed on the card for exactly that reason.");

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
    + "declaration it also is. Each leaves a fan shorter than the source, which is the "
    + "direction this page is willing to be wrong in.");
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
    + "imported reading is refused outright rather than guessed at"
  ].forEach(function (reading) { add(overstating, "li", null, reading); });

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
      var named_ = add(line, "span", finding ? "refusal found" : "refusal", refusal.name);
      named_.title = finding ? finding.finding + " \u2014 " + finding.because
        : "documented by " + refusal.documentedBy.join(", ") + ", and raised by this module";
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
      // A zero read on its own says a caller has nothing to learn here, which is a
      // stronger claim than this bar ever makes. A module whose only member is a
      // constructor reads zero: a caller writing `new` against it has that constructor
      // and every type crossing it to learn, and a bar counts none of it. Nothing else
      // on the card tells that zero from a module that declares nothing at all.
      reading.appendChild(document.createTextNode(
        " \u2014 nothing this bar counts, which is not the same as nothing to learn"));
    }
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
