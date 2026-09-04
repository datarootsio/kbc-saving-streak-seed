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
.module .bar {
  height: .5rem;
  background: var(--edge);
  border-radius: .25rem;
  overflow: hidden;
}
.module .bar span { display: block; height: 100%; margin: 0 auto; background: var(--accent); }
.module .bar.unscored { background: none; border: 1px dashed var(--edge); }
.module .fan { display: block; width: 100%; }
.fan line { stroke: var(--accent); stroke-width: 1.1; }
.fan circle { fill: var(--accent); }
.fan .adapter { stroke: var(--adapter); fill: var(--adapter); }
.fan .record { stroke: var(--record); fill: var(--record); }
.fan .transaction { stroke: var(--transaction); fill: var(--transaction); stroke-dasharray: 3 2.5; }
.module .cost { margin: 0; font-size: .8rem; color: var(--ink-soft); font-variant-numeric: tabular-nums; }
.module .reach { margin: .2rem 0 0; font-size: .8rem; color: var(--ink-soft); font-variant-numeric: tabular-nums; }
.key { display: flex; flex-wrap: wrap; gap: .25rem 1rem; margin: .6rem 0 0; padding: 0; list-style: none; }
.key li { display: flex; align-items: center; gap: .35rem; font-size: .85rem; color: var(--ink-soft); }
.key svg { flex: none; }
.module .unscored { margin: .5rem 0 0; font-size: .8rem; color: var(--ink-soft); font-style: italic; }
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
    "Everything else a module calls is a module it calls. A transaction is established by "
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
    + "coordination this tool can see, and it does not guess at them.");

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
  var reads = ["method", "parameter", "typeToLearn", "typeEveryCallerAlreadyKnows"];
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
  var SHAPE = {width: 200, height: 46, apex: 2, feet: 42};

  function drawShape(item, module) {
    var shape = add(item, "div", "shape");
    var scored = module.interface.cost !== null;
    var track = add(shape, "div", scored ? "bar" : "bar unscored");
    if (scored) {
      track.title = "interface cost " + module.interface.cost;
      add(track, "span").style.width = (widest > 0 ? 100 * module.interface.cost / widest : 0) + "%";
    } else {
      track.title = "never scored \u2014 " + module.excludedBy.rule;
    }
    drawFan(shape, module);
  }

  // One line from under the middle of the bar out to each thing the module coordinates,
  // the whole fan as wide a share of the card as its reach is of the widest reach in the
  // document. Two encodings of one number on purpose: the lines can be counted, and the
  // spread can be compared across a page at a glance.
  function drawFan(shape, module) {
    var fan = draw(shape, "svg", "fan", {
      viewBox: "0 0 " + SHAPE.width + " " + SHAPE.height,
      preserveAspectRatio: "xMidYMid meet"
    });
    var reaches = module.reach.reaches;
    var span = furthest > 0 ? SHAPE.width * module.reach.count / furthest : 0;
    reaches.forEach(function (reached, index) {
      var foot = reaches.length === 1
        ? SHAPE.width / 2
        : (SHAPE.width - span) / 2 + span * index / (reaches.length - 1);
      var says = reached.kind + " " + reached.name + " \u2014 " + reached.matched;
      titled(draw(fan, "line", reached.kind, {
        x1: SHAPE.width / 2, y1: SHAPE.apex, x2: foot, y2: SHAPE.feet
      }), says);
      titled(draw(fan, "circle", reached.kind, {cx: foot, cy: SHAPE.feet, r: 2}), says);
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
      drawReach(item, module);
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
