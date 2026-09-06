"""The graph document: every module the application is made of, and how well it was read.

This is the tool's real interface. The page is a rendering of this document and nothing
else, and every later measurement hangs off it, so its shape is deliberately boring:
plain data, every collection sorted, no value that could differ between two machines.

Determinism is enforced here rather than hoped for. Paths are recorded relative to the
source root they were found under, the source root is recorded relative to the repository
that holds it, and nothing is read from the clock.
"""

import collections
import json
import logging
import os

from . import languages

log = logging.getLogger("module_depth_map.graph")

# The shape this document promises to have, and the version a reader checks before
# trusting a key is there. It moved to /2 when the document grew a top-level `scoring`
# object and gave every module an `interface` and an `excludedBy`, to /3 when every
# module gained a `reach` and a `depth`, and to /4 when every module gained its `callers`
# and the `deletionTest` verdict read off them: a reader of any older shape looking for
# what it was promised finds none of them. The tool refuses a *configuration* whose schema
# it does not know, so versioning what it writes as well is the same promise kept in the
# other direction. It moved to /5 when every module gained its `findings` and its
# `interface` gained a `refusals` band with the `refusalCost` and `costWithoutRefusals`
# the total is split into — each refusal saying which side of the seam named it, and
# whether the two could be held against each other at all. It moved to /6 when the
# document grew a top-level `flows` list: the business events the configuration asks to be
# traced, each with the path through the modules that was walked for it, or the reason
# there is none. It moved to /7 when that walk became a walk of the *calls* the entry
# method makes rather than of the entry module's reach, so every step gained the
# `calledFrom` and the `call` that put the flow there — the method the call was written
# in, and the call as the source wrote it. It moved to /8 when the document stopped being
# a document about the backend: `source` grew the paths a named rule declined to read at
# all, `scoring` grew the `largest` module and the numbers it was measured at, and a
# module's `language` became a thing a reader has to look at rather than a constant. It
# moved to /9 when the two facts a page of two languages cannot render without went in:
# `source.readAt` says, for each language read, what a module of it is and why — the
# sentence the page's prose was hardcoded with, which was a page claiming two grains on a
# run that read one — and `scoring.typesEveryCallerAlreadyKnows` became one list per
# language, because a caller of each language does not already know the other's types.
SCHEMA = "module-depth-map/9"

# What a source root that is its own repository is called. `os.path.relpath` answers "."
# for that, which reads as a path on the page ("Source read: .", "./shop/Till.java") and
# says nothing; the repository's directory name would say something, but it is a different
# word in every clone and worktree, which is exactly the machine-specific value the output
# may not carry.
REPOSITORY_ROOT = "<repository root>"


class DuplicateModules(Exception):
    """Two source files claiming the same module id, which the graph cannot hold.

    An id is how the page joins a package to its modules, so a repeated one draws one
    card twice and drops the other file entirely. Reachable as soon as a second root is
    read, so it is refused with both paths named rather than left to look like a module
    that moved.
    """

    def __init__(self, clashes):
        super().__init__("duplicate module ids: %s" % ", ".join(sorted(clashes)))
        self.clashes = sorted(clashes)


class SourceUnreadable(Exception):
    """A path the operating system would not hand over, not source a reading could not parse.

    Kept apart from `languages.ParseFailure` on purpose. "This file could not be opened"
    and "this source could not be parsed" are different findings for the reader — one is
    about the checkout, the other about the source — and folding them together would
    also make a language's reading answer for this file's handling of a filesystem.
    """

    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


# One module as it was read: the entry in the document, the declaration behind it, the
# file it came out of, and the language all three were read with. A tuple of four, named,
# because three of the passes below want a different one of them and unpacking by
# position made the language look like an afterthought hung off the end.
_Read = collections.namedtuple("_Read", "module declared parsed language")


class SourceRoot:
    """A directory of source, and the name the graph will know it by.

    The label is what keeps an absolute path out of the output: a root is named by where
    it sits inside its repository, never by where the repository sits on this machine.

    A root is a directory rather than a language. Which language a file is read with is
    decided by its own ending, the way `.java` already decided it when there was only one:
    nothing has to be said on a command line, a directory holding both is read as both,
    and adding a third language is a file in `languages` rather than an argument here.
    """

    def __init__(self, path, label):
        self.path = path
        self.label = label
        self.suffixes = languages.SUFFIXES

    def language_of(self, path):
        """The language this file under the root is read with."""
        return languages.of(path)


def label_for(path):
    """Where this file or directory sits inside its repository, or its own name if it has none.

    A repository is anything carrying a `.git`, whichever shape it takes: an ordinary
    clone has a directory there, while a worktree or a submodule has a plain file
    pointing at the real one. Only the presence is asked about, because a walk that
    insisted on a directory would miss a worktree and label the root by its own basename
    — a checkout-specific value in an output that has to be the same on every machine.
    """
    absolute = os.path.abspath(path)
    walk = absolute
    while True:
        if os.path.exists(os.path.join(walk, ".git")):
            return _relative(absolute, walk) or REPOSITORY_ROOT
        parent = os.path.dirname(walk)
        if parent == walk:
            return os.path.basename(absolute)
        walk = parent


def source_root(path):
    """A directory of source, read in whatever languages this tool knows the endings of."""
    return SourceRoot(path, label_for(path))


def _relative(whole, base):
    """`whole` as a forward-slash path under `base`, or "" when it is `base` itself."""
    relative = os.path.relpath(whole, base).replace(os.sep, "/")
    return "" if relative == "." else relative


def _files_under(root, declined):
    """Every source file under this root, what would not open, and what a rule declined.

    Nothing under a root is passed over in silence, because a directory the walk skipped
    takes every module in it off the page while every count still adds up. A directory
    that cannot be listed comes back as a named failure, and a symlinked one is followed
    rather than stepped over — once, so that a link pointing back up the tree cannot make
    the same file arrive twice under two names.

    `declined` is the third answer, and the one this graph is a graph of the application
    because of: test code, build output and installed dependencies are not the
    application's own source, and reading them would put modules on the page that nobody
    here wrote. It is a rule in the scoring file rather than a list in this one, so
    "why was this not read?" has a name and a sentence behind it exactly as every other
    exclusion here does — and so that a `node_modules` is skipped as one path rather than
    walked into and reported as forty thousand.
    """
    found = []
    unreadable = []
    not_read = []
    read_already = {}

    # The root's own name is matched by the rule before anything under it is, because
    # `--source frontend/node_modules` is the same directory the rule holds out when the
    # walk meets it one level up. Checked only here and the walk read straight into it:
    # twenty-six dependency modules on the page, scored, drawn and reported as this
    # application's own source, with the rule that exists to stop exactly that reporting
    # nothing at all.
    here = os.path.basename(os.path.abspath(root.path))
    if here in declined.directories:
        return found, unreadable, [("", "a directory named %s" % here)]

    def refuse(error):
        unreadable.append(
            (
                _relative(error.filename, root.path),
                "directory could not be read: %s"
                % (error.strerror or error.__class__.__name__),
            )
        )

    for directory, subdirectories, names in os.walk(root.path, onerror=refuse, followlinks=True):
        here = os.path.realpath(directory)
        if here in read_already:
            log.warning(
                "not reading source directory root=%s path=%s twice: it is the same "
                "directory as %s",
                root.label,
                _relative(directory, root.path),
                read_already[here] or root.label,
            )
            subdirectories[:] = []
            continue
        read_already[here] = _relative(directory, root.path)
        # The walk order decides which of two names for the same directory is the one
        # read, so it is fixed here rather than left to the order the filesystem hands
        # back. The files themselves are sorted once, at the end.
        subdirectories.sort()
        walk_into = []
        for name in subdirectories:
            if name in declined.directories:
                whole = os.path.join(directory, name)
                not_read.append(
                    (_relative(whole, root.path), "a directory named %s" % name)
                )
                continue
            walk_into.append(name)
        subdirectories[:] = walk_into
        for name in names:
            if not name.endswith(root.suffixes):
                continue
            whole = os.path.join(directory, name)
            ending = declined.ending_of(name)
            if ending is not None:
                not_read.append((_relative(whole, root.path), "a name ending in %s" % ending))
                continue
            found.append((whole, _relative(whole, root.path)))

    found.sort(key=lambda pair: pair[1])
    unreadable.sort()
    not_read.sort()
    return found, unreadable, not_read


def build(roots, rules, roots_not_read=()):
    """Read every source file under these roots and return the graph document.

    `roots_not_read` is what the caller decided not to hand over and why, each as a
    `{"root", "reason"}` pair. It exists because a page has to be able to say that half an
    application was left out: a run in a repository with no `frontend/` at all is a
    perfectly good run, and one that said nothing about the directory it looked for and
    did not find would be a picture of half an application that does not say which half.

    A file that cannot be read is named in the document and logged, never counted as a
    module with nothing in it: a parse failure that looked like an empty module would be
    indistinguishable from a real finding.

    What each module's interface costs a caller, and which modules are drawn but never
    scored, are decided by `rules` — read from a file beside the tool rather than written
    into it, so that changing what counts is a diff on that file and not on this one.

    `rules` is asked for rather than defaulted. Falling back to `scoring.load()` here put
    a second home for the `--scoring` default behind the one the command line already
    has, and made a `ConfigurationRefused` come out of the call the caller guards for
    duplicate module ids — the one failure this function is documented to raise.
    """
    modules = []
    unparsed = []
    declined = []
    read = []
    seen = 0

    for root in roots:
        log.debug("reading source root label=%s", root.label)
        files, unreadable_directories, not_read = _files_under(root, rules.sources_not_read)

        for relative, matched in not_read:
            log.info(
                "source not read root=%s path=%s rule=%s matched=%s",
                root.label,
                # A path of nothing is the root itself, which the rule matches by its own
                # name. Logged in words rather than as an empty value, because a line
                # ending `path= rule=...` reads as a line this tool failed to fill in.
                relative or "the root itself",
                rules.sources_not_read.rule,
                matched,
            )
            declined.append({"root": root.label, "path": relative, "matched": matched})

        for relative, reason in unreadable_directories:
            seen += 1
            log.warning(
                "could not read source directory root=%s path=%s reason=%s",
                root.label,
                relative,
                reason,
            )
            unparsed.append({"root": root.label, "path": relative, "reason": reason})

        for whole, relative in files:
            seen += 1
            try:
                text = _read(whole)
            except SourceUnreadable as unreadable:
                log.warning(
                    "could not read source file root=%s path=%s reason=%s",
                    root.label,
                    relative,
                    unreadable.reason,
                )
                unparsed.append(
                    {"root": root.label, "path": relative, "reason": unreadable.reason}
                )
                continue
            language = root.language_of(relative)
            try:
                parsed = language.parse(text, relative, root)
            except languages.ParseFailure as failure:
                log.warning(
                    "could not parse source file root=%s language=%s path=%s reason=%s",
                    root.label,
                    language.NAME,
                    relative,
                    failure.reason,
                )
                unparsed.append({"root": root.label, "path": relative, "reason": failure.reason})
                continue
            for declared in parsed.top_level:
                excluded = rules.excluded_by(declared)
                modules.append(
                    {
                        "id": language.module_id(parsed.package, declared.name),
                        "name": declared.name,
                        "package": parsed.package,
                        "kind": declared.kind,
                        "language": language.NAME,
                        "root": root.label,
                        "path": relative,
                        "lines": parsed.lines,
                        "nested": parsed.nested_names(declared),
                        "interface": rules.interface_of(declared, excluded is None, language),
                        "excludedBy": excluded,
                    }
                )
                read.append(_Read(modules[-1], declared, parsed, language))

    modules.sort(key=lambda module: (module["package"], module["name"]))
    unparsed.sort(key=lambda entry: (entry["root"], entry["path"]))
    declined.sort(key=lambda entry: (entry["root"], entry["path"]))
    _refuse_duplicate_ids(modules)
    _check_the_refusals(modules, rules)
    _measure_depth(read, rules)
    _run_the_deletion_test(modules, rules)
    flows = _trace_the_flows(read, modules, rules)

    packages = {}
    for module in modules:
        packages.setdefault(module["package"], []).append(module["id"])

    document = {
        "schema": SCHEMA,
        "source": {
            "roots": sorted(root.label for root in roots),
            # Kept apart from `notRead` below, which is the scoring file's rule about
            # names: "a rule declined to read this" and "there is no such directory" are
            # different findings, and printing the second under the first's sentence would
            # say a rule nobody wrote had matched.
            "rootsNotRead": [dict(entry) for entry in roots_not_read],
            "languages": sorted({module["language"] for module in modules}),
            # What a module of each language read *is*, in the words of the reading that
            # read it. Here because a page cannot say it otherwise: the prose was written
            # into the renderer, and a run of one root then rendered a sentence about
            # both halves of an application whichever half it had been pointed at. Only
            # the languages something was actually read in, so the page describes this
            # run rather than this tool.
            "readAt": [
                {"language": language.NAME, "says": language.GRAIN}
                for language in languages.ALL
                if language.NAME in {module["language"] for module in modules}
            ],
            "filesSeen": seen,
            "filesParsed": seen - len(unparsed),
            "filesUnparsed": len(unparsed),
            "unparsed": unparsed,
            # The paths a named rule declined to read at all, kept apart from the ones
            # that could not be read: "this is not the application's own source" and
            # "this file would not parse" are different findings, and a page that mixed
            # them would paint its alarm band over a `node_modules` nobody wrote.
            "notRead": {
                "rule": rules.sources_not_read.rule,
                "because": rules.sources_not_read.because,
                "paths": declined,
            },
        },
        "packages": [
            {"name": name, "moduleIds": sorted(ids)} for name, ids in sorted(packages.items())
        ],
        "scoring": _scoring(rules, modules),
        "flows": flows,
        "modules": modules,
    }

    for entry in document["source"]["rootsNotRead"]:
        log.info("source root not read root=%s reason=%s", entry["root"], entry["reason"])
    log.info(
        "graph built roots=%s languages=%s filesSeen=%d filesParsed=%d filesUnparsed=%d "
        "pathsNotRead=%d packages=%d modules=%d scored=%d neverScored=%d",
        ",".join(document["source"]["roots"]),
        ",".join(document["source"]["languages"]) or "none, nothing was read",
        seen,
        document["source"]["filesParsed"],
        len(unparsed),
        len(declined),
        len(document["packages"]),
        len(modules),
        document["scoring"]["modulesScored"],
        document["scoring"]["modulesNeverScored"],
    )
    for entry in document["scoring"]["exclusions"]:
        log.info(
            "modules never scored rule=%s modules=%d",
            entry["rule"],
            entry["modulesExcluded"],
        )
    scored = [module for module in modules if module["interface"]["cost"] is not None]
    if scored:
        dearest = max(scored, key=lambda module: (module["interface"]["cost"], module["id"]))
        log.info(
            "widest interface module=%s cost=%d methods=%d typesToLearn=%d",
            dearest["id"],
            dearest["interface"]["cost"],
            len(dearest["interface"]["methods"]),
            sum(1 for type_ in dearest["interface"]["typesCrossingTheSeam"] if type_["mustBeLearned"]),
        )
    furthest = max(modules, key=lambda module: (module["reach"]["count"], module["id"]), default=None)
    if furthest is not None:
        log.info(
            "furthest reach module=%s reach=%d over interfaceCost=%s leverage=%s",
            furthest["id"],
            furthest["reach"]["count"],
            furthest["depth"]["interfaceCost"],
            furthest["depth"]["leverage"],
        )
    log.info(
        "refusals checked read=%d notChecked=%d modulesWithFindings=%d findings=%d",
        document["scoring"]["refusals"]["refusalsRead"],
        document["scoring"]["refusals"]["refusalsNotChecked"],
        document["scoring"]["refusals"]["modulesWithFindings"],
        sum(
            entry["findings"] for entry in document["scoring"]["refusals"]["findingsByKind"]
        ),
    )
    for entry in document["scoring"]["refusals"]["findingsByKind"]:
        log.info("refusal finding=%s findings=%d", entry["finding"], entry["findings"])
    for entry in document["scoring"]["deletionTest"]["modulesByVerdict"]:
        log.info(
            "deletion test verdict=%s modules=%d", entry["verdict"], entry["modules"]
        )
    # One line per module the test actually finds something about, because that is the
    # finding: a reader who has run the tool should not have to open the page to learn
    # which modules were named, or on what numbers. Only the pass-through verdict is
    # written out this way — the other two are the absence of a finding, and would be
    # sixty lines saying nothing — and the word logged is the file's own, because the
    # file is where it is decided and a line that spelled it here would say the wrong
    # thing the moment somebody reworded the rule.
    finding = document["scoring"]["deletionTest"]["passThrough"]["verdict"]
    for module in modules:
        if module["deletionTest"]["verdict"] == finding:
            log.info(
                "deletion test finding module=%s verdict=%s reach=%d methods=%d callers=%d "
                "through=%s",
                module["id"],
                finding,
                module["deletionTest"]["reach"],
                module["deletionTest"]["methods"],
                module["deletionTest"]["callers"],
                ",".join(module["callers"]["moduleIds"]),
            )
    with_leverage = [module for module in modules if module["depth"]["leverage"] is not None]
    if with_leverage:
        deepest = max(with_leverage, key=lambda module: (module["depth"]["leverage"], module["id"]))
        shallowest = min(with_leverage, key=lambda module: (module["depth"]["leverage"], module["id"]))
        log.info(
            "deepest module=%s leverage=%s reach=%d interfaceCost=%d",
            deepest["id"],
            deepest["depth"]["leverage"],
            deepest["depth"]["reach"],
            deepest["depth"]["interfaceCost"],
        )
        log.info(
            "shallowest module=%s leverage=%s reach=%d interfaceCost=%d",
            shallowest["id"],
            shallowest["depth"]["leverage"],
            shallowest["depth"]["reach"],
            shallowest["depth"]["interfaceCost"],
        )
    return document


def _check_the_refusals(modules, rules):
    """Hold every module's documented refusals against the ones it raises, and say so.

    Read for every module, scored or not: a rule that declines to price a record has said
    nothing about whether that record's javadoc tells the truth, and a stale `@throws` is
    a stale `@throws` wherever it is written.

    Logged at INFO one line per disagreement, because that is the finding. A run that
    produced it should not need the page to be opened before anybody knows, and the line
    carries both sides so that it can be acted on without the graph being read either.

    A refusal the tool declined to hold against anything is counted on the module's DEBUG
    line rather than left invisible. It is the difference between "these agree" and "this
    was never checked", and a reader who cannot see which of the two they are looking at
    would have to trust the silence.
    """
    for module in modules:
        module["findings"] = rules.findings_of(module["id"], module["interface"]["refusals"])
        for finding in module["findings"]:
            log.info(
                "refusal finding module=%s finding=%s refusal=%s documentedBy=%s raised=%s",
                module["id"],
                finding["finding"],
                finding["refusal"],
                ",".join(finding["documentedBy"]) or "nothing a caller can reach",
                finding["raised"],
            )
        log.debug(
            "refusals read module=%s refusals=%s findings=%d notChecked=%d",
            module["id"],
            ",".join(refusal["name"] for refusal in module["interface"]["refusals"])
            or "none this tool can read",
            len(module["findings"]),
            sum(
                1
                for refusal in module["interface"]["refusals"]
                if not refusal["checked"]
            ),
        )


def _run_the_deletion_test(modules, rules):
    """Count who goes through each module, and render the verdict that follows.

    A third pass, after reach, because who calls a module is the one thing a module's own
    file cannot say: it is the whole graph read backwards. Every line in every fan that
    names a module is one caller of that module — reaching it *is* calling it here, and
    building one is calling it too — and reach already holds one entry per thing reached,
    so a collaborator called ten times counts as the one caller it is.

    The callers are named as well as counted. A count nobody can check is the kind of
    number this page exists not to print, and the ids cost nothing: they are already the
    ids of the fan lines that produced them.
    """
    called_by = {module["id"]: set() for module in modules}
    for module in modules:
        for entry in module["reach"]["reaches"]:
            target = entry["moduleId"]
            if target is None:
                # The transaction, which is reached and is not a module: nothing answers
                # to it, so nothing can be called through it.
                continue
            if target not in called_by:
                log.warning(
                    "not counted as a caller module=%s reaches=%s, which this graph does "
                    "not hold, so that module's caller count is short by one",
                    module["id"],
                    target,
                )
                continue
            called_by[target].add(module["id"])

    for module in modules:
        goes_through = sorted(called_by[module["id"]])
        module["callers"] = {"count": len(goes_through), "moduleIds": goes_through}
        module["deletionTest"] = rules.deletion_test_of(
            module["name"], module["reach"], module["callers"], module["interface"],
            scored=module["excludedBy"] is None,
        )
        log.debug(
            "callers counted module=%s callers=%d through=%s",
            module["id"],
            len(goes_through),
            ",".join(goes_through) or "nothing in this graph",
        )


def _trace_the_flows(read, modules, rules):
    """Walk each business event the configuration names out of the calls the source writes.

    A fourth pass, and the only one about the whole application rather than about one
    module. The configuration says where a flow starts — one module, and one method on it
    a caller can call — and says nothing else. Everything below that is read out of the
    source: the calls that method's body makes, in the order Java evaluates them, then
    the calls the body of each *called* method makes, and so on until the walk runs out of
    bodies this source tree holds.

    The grain is the method, and that is the whole of what makes a flow a flow. Walked
    over the reach on each card instead, a flow is the entry module's entire transitive
    reach: `DepositsService.deposit` never goes near the customers table, but
    `AccountsService.accountsOf` does, so a deposit walked that way passes through
    `CustomerRepository` and `CustomerAccounts` — two modules a deposit does not touch —
    and two flows differing only in their method come back byte-identical. Reach is right
    for what it is for. It is a set of distinct things, so that a module cannot raise its
    depth by writing more calls; a set has no order and no idea which method wrote it, and
    a path needs both.

    The order is the order the calls are written and evaluated. A module already entered
    keeps the step it was first entered at — the graph has cycles in it and a flow is a
    path through modules, not a transcript of calls — but the *method* is followed anyway,
    because `pairingFor` and `withdrawFrom` on one module go different places.

    A call on the module's own method is followed and is not a step: the flow is already
    in that module. This is not a nicety either — `deposit` does all three of its refusals
    through private helpers, and a walk that only read the public method would report a
    deposit as reaching nothing at all.

    Nothing is followed that this graph does not hold, and nothing is guessed at.
    `clock.instant()`, `log.debug(...)`, a call on the result of another call: each names
    something outside this source or something only javac could resolve, and is dropped
    rather than turned into a step nobody can check.

    Four things can stop a walk, and each of them ends with the flow carrying no path at
    all rather than a shorter one. A flow whose modules are half walked is the one output
    worse than no flow: it draws a path a reader can follow, every module on it is real,
    and the event it claims to trace stopped happening that way some commits ago. So the
    reason is recorded on the flow, logged as a warning, and printed on the page in place
    of the path.
    """
    by_id = {module["id"]: module for module in modules}
    declared_by_id = {each.module["id"]: each.declared for each in read}
    written_among = _written_among(read)
    calls_of = _calls_reader(read, rules, declared_by_id, written_among)
    flows = []
    for flow in rules.flows:
        entry = {
            "flow": flow.flow,
            "because": flow.because,
            "entryPoint": {"moduleId": flow.module, "method": flow.method},
            "resolved": False,
            "couldNotResolve": None,
            "modules": 0,
            "path": [],
        }
        flows.append(entry)
        unresolved = _why_the_flow_cannot_start(flow, by_id, declared_by_id)
        path = [] if unresolved is not None else _walked_from(flow, by_id, calls_of)
        if unresolved is None and len(path) < 2:
            unresolved = (
                "calling %s on %s reaches no other module this graph holds, so this flow "
                "passes through the one module it starts at and is not a path through the "
                "application" % (flow.method, flow.module)
            )
        if unresolved is not None:
            entry["couldNotResolve"] = unresolved
            log.warning(
                "flow not traced flow=%s entryPoint=%s.%s reason=%s. It is drawn with no "
                "path rather than a shorter one",
                flow.flow,
                flow.module,
                flow.method,
                unresolved,
            )
            continue
        entry["path"] = path
        entry["resolved"] = True
        entry["modules"] = len(path)
        log.info(
            "flow traced flow=%s entryPoint=%s.%s modules=%d through=%s",
            flow.flow,
            flow.module,
            flow.method,
            entry["modules"],
            ",".join(step["moduleId"] for step in path),
        )
    log.info(
        "flows read flows=%d traced=%d notTraced=%d",
        len(flows),
        sum(1 for entry in flows if entry["resolved"]),
        sum(1 for entry in flows if not entry["resolved"]),
    )
    return flows


def _calls_reader(read, rules, declared_by_id, written_among):
    """A reader answering what one method of one module calls, remembering what it read.

    Remembered because a walk asks the same question more than once — two flows enter
    `AccountsService.pairingFor`, and one flow enters `AmountOfMoney.whyItIsNotOne` from
    three different helpers — and re-reading a body to get the same answer is work the
    tool can see is pointless. The answers are a pure function of the source, so a
    remembered one and a fresh one cannot differ.
    """
    read_by_id = {each.module["id"]: each for each in read}
    answered = {}

    def calls_of(module_id, method_name):
        here = answered.get((module_id, method_name))
        if here is None:
            each = read_by_id[module_id]
            here = rules.calls_from(
                method_name,
                declared_by_id[module_id],
                module_id,
                each.parsed.package,
                each.parsed.imports,
                declared_by_id,
                each.module["nested"],
                written_among,
                each.language,
            )
            answered[(module_id, method_name)] = here
        return here

    return calls_of


def _why_the_flow_cannot_start(flow, by_id, declared_by_id):
    """What stops this flow being walked, in a sentence, or None when nothing does.

    Each answer names the thing that was looked for and the thing that was found instead,
    because "this flow no longer resolves" on its own sends whoever reads it back to the
    source to work out which half moved.
    """
    module = by_id.get(flow.module)
    if module is None:
        return (
            "no module in this graph is called %s, so there is nowhere for this flow to "
            "start" % flow.module
        )
    reachable = [method["name"] for method in module["interface"]["methods"]]
    if flow.method not in reachable:
        return (
            "%s presents no method called %s that a caller can reach, so nothing here is "
            "the call this flow is entered by — it presents %s"
            % (
                flow.module,
                flow.method,
                ", ".join(sorted(set(reachable))) or "no such method at all",
            )
        )
    # A method with no body under it is a promise that somebody else will write one, and
    # whoever does is in a file this walk was not pointed at. Named as its own failure
    # rather than left to come out as "reaches nothing", because the two are different
    # faults and send a reader to different places: one method emptied out, or a flow
    # entered through an interface instead of the module that implements it.
    written = [
        method for method in declared_by_id[flow.module].methods
        if method.name == flow.method and method.has_a_body
    ]
    if not written:
        return (
            "%s declares %s but writes no body for it here, so there are no calls to "
            "follow — a flow is entered through the module that implements the call, not "
            "through one that only promises it" % (flow.module, flow.method)
        )
    return None


def _walked_from(flow, by_id, calls_of):
    """The modules this flow passes through, in the order the calls enter them."""
    path = []
    entered = set()
    followed = set()

    def enter(module_id, reached_from, called_from, call, matched):
        module = by_id[module_id]
        entered.add(module_id)
        path.append(
            {
                "step": len(path) + 1,
                "moduleId": module_id,
                "name": module["name"],
                "package": module["package"],
                "reachedFrom": reached_from,
                "calledFrom": called_from,
                "call": call,
                "matched": matched,
            }
        )

    def go(module_id, method_name):
        # One (module, method) pair is read once. The pair rather than the module, because
        # two methods of one module go two places; and read once rather than every time it
        # is called, because this application's modules call each other in circles and a
        # flow is a path through them, not a transcript that never ends.
        if (module_id, method_name) in followed:
            return
        followed.add((module_id, method_name))
        for call in calls_of(module_id, method_name):
            if call["own"]:
                go(module_id, call["method"])
                continue
            target = call["moduleId"]
            if target not in by_id:
                continue
            if target not in entered:
                enter(target, module_id, method_name, call["call"], call["matched"])
            if call["method"] is not None:
                go(target, call["method"])

    enter(
        flow.module,
        None,
        None,
        None,
        "the flow is entered here, by calling %s" % flow.method,
    )
    go(flow.module, flow.method)
    return path


def _written_among(read):
    """What each module was written among, by id: its package, its imports, its nested types.

    Wanted for the modules *above* the one being read as well as for that one — a
    supertype's method shadows a static import of its name, and a supertype's nested type
    shadows the package — and which module a supertype's own name means is settled by the
    file that names it rather than by the file being read. Built here rather than twice,
    because reach and the flows follow a name the same way and have to: a fan drawn from
    one reading and a path drawn from another would leave a reader unable to say which of
    the two the page was showing them.
    """
    return {
        each.module["id"]: languages.WrittenAmong(
            each.parsed.package, each.parsed.imports, each.module["nested"], each.language
        )
        for each in read
    }


def _measure_depth(read, rules):
    """Give every module its reach and its depth, once the whole graph is known.

    A second pass rather than part of the first, because reach is the one measurement
    that cannot be taken from a file on its own: a name in a body means a module, and
    which module it means is settled by the imports of the file it was written in and by
    what the rest of the source turned out to hold. Reading it early would mean either
    resolving against half a graph or guessing.

    Every module gets both, including the ones no rule scores. An excluded module still
    coordinates whatever it coordinates, and drawing its fan is what lets a reader see
    that the rule declined to price something real. Its depth carries the same two numbers
    with no ratio between them, because there is no interface cost to divide by.

    The nested names already on the module are handed over with the rest, because they are
    part of how a name is followed: a type a module declares inside itself shadows the
    package and every import above it, exactly as it does for the compiler. So is what
    every *other* module was written among, since a module inherits the declarations and
    the member types of whatever it is built on, and those shadow the same way.
    """
    declared_by_id = {each.module["id"]: each.declared for each in read}
    written_among = _written_among(read)
    for each in read:
        each.module["reach"] = rules.reach_of(
            each.declared, each.module["id"], each.parsed.package, each.parsed.imports,
            declared_by_id, each.module["nested"], written_among, each.language,
        )
        each.module["depth"] = rules.depth_of(each.module["reach"], each.module["interface"])


def _scoring(rules, modules):
    """The rules that produced these scores, carried in the document they produced.

    The page draws its own explanation from this rather than from anything written into
    it, so a reader who disagrees with a score is reading the rule that made it — and the
    rule they would edit — rather than a description of one.

    The exclusions keep the order the file gives them, because that is the order they are
    applied in: the first rule that covers a module is the one recorded against it.

    `widestInterface` is the one number every bar on the page is drawn relative to. It is
    worked out here rather than in the browser so that the scale two bars are compared on
    is in the document as well — a bar the page drew is then checkable against the graph
    it came from, instead of against arithmetic only the page can do.
    """
    excluded = {}
    for module in modules:
        if module["excludedBy"]:
            excluded[module["excludedBy"]["rule"]] = excluded.get(module["excludedBy"]["rule"], 0) + 1
    costs = [
        module["interface"]["cost"]
        for module in modules
        if module["interface"]["cost"] is not None
    ]
    return {
        "configuration": label_for(rules.path),
        "weights": dict(rules.weights),
        "widestInterface": max(costs) if costs else 0,
        # The other scale on the page, for the same reason as the first: a fan is drawn
        # against the widest fan in the document, so two shapes compared by eye are being
        # compared against a number a reader can find in the graph rather than against
        # arithmetic only the page can do. Every module counts towards it, scored or not,
        # because every module is drawn with a fan.
        "widestReach": max([module["reach"]["count"] for module in modules] or [0]),
        "reach": {
            "adapter": rules.reached.adapter.because,
            "persistentRecord": rules.reached.persistent_record.because,
            "transaction": rules.reached.transaction,
            "transactionAnnotations": sorted(rules.reached.transaction_annotations),
        },
        # The rule behind every verdict on the page, carried in the document the verdicts
        # were rendered into. A mechanical finding is worth exactly as much as the rule a
        # reader can point at behind it, so the thresholds travel with the answers they
        # produced rather than living only in the file that produced them.
        #
        # The counts do not add up to the number of modules, and are not meant to: a
        # module no rule scores is given no verdict, and `modulesNeverScored` above is
        # where the rest of them are.
        "deletionTest": {
            "passThrough": {
                "verdict": rules.deletion_test.pass_through.verdict,
                "because": rules.deletion_test.pass_through.because,
                "reachAtMost": {
                    "perMethod": rules.deletion_test.per_method,
                    "neverBelow": rules.deletion_test.never_below,
                },
                "callersAtLeast": rules.deletion_test.callers_at_least,
            },
            "earnsItsKeep": {
                "verdict": rules.deletion_test.earns_its_keep.verdict,
                "because": rules.deletion_test.earns_its_keep.because,
            },
            "noFinding": {
                "verdict": rules.deletion_test.no_finding.verdict,
                "because": rules.deletion_test.no_finding.because,
            },
            "modulesByVerdict": [
                {
                    "verdict": answer.verdict,
                    "modules": sum(
                        1
                        for module in modules
                        if module["deletionTest"]["verdict"] == answer.verdict
                    ),
                }
                for answer in (
                    rules.deletion_test.pass_through,
                    rules.deletion_test.earns_its_keep,
                    rules.deletion_test.no_finding,
                )
            ],
        },
        # The refusal band's own rule, and what holding the two sides against each other
        # turned up, carried in the document the findings were rendered into. The counts
        # are of findings rather than of modules, because nothing stops one module being in
        # both disagreements at once — a stale `@throws` on one refusal and a silent throw
        # of another are independent, and a count of modules would hide one of them behind
        # the other. `refusalsNotChecked` is the third number and the honest one: refusals
        # this tool read and then declined to hold against anything, which are neither a
        # finding nor a module keeping its word.
        "refusals": {
            "because": rules.refusals.because,
            "documentedNeverRaised": {
                "finding": rules.refusals.documented_never_raised.finding,
                "because": rules.refusals.documented_never_raised.because,
            },
            "raisedNeverDocumented": {
                "finding": rules.refusals.raised_never_documented.finding,
                "because": rules.refusals.raised_never_documented.because,
            },
            "refusalsRead": sum(
                len(module["interface"]["refusals"]) for module in modules
            ),
            "refusalsNotChecked": sum(
                1
                for module in modules
                for refusal in module["interface"]["refusals"]
                if not refusal["checked"]
            ),
            "modulesWithFindings": sum(1 for module in modules if module["findings"]),
            "findingsByKind": [
                {
                    "finding": named.finding,
                    "findings": sum(
                        1
                        for module in modules
                        for finding in module["findings"]
                        if finding["finding"] == named.finding
                    ),
                }
                for named in (
                    rules.refusals.documented_never_raised,
                    rules.refusals.raised_never_documented,
                )
            ],
        },
        # The largest module in this document, and what it was measured at. It is here
        # because the one thing this page can be misread as saying is that a big module is
        # a deep one, and the plainest answer to that is the biggest module's own numbers
        # printed beside its line count. Not a ranking and not a finding: the line count
        # enters no measurement here, which is exactly what naming it demonstrates.
        # Settled by the id where two files are the same length, so two runs agree.
        "largest": _largest(modules),
        "reachableFromOutside": sorted(rules.reachable_from_outside),
        # One list per language, as the file writes it: what a caller already knows is
        # a fact about the language they are calling from, and a reader checking a card's
        # types against this has to be able to see which list decided it.
        "typesEveryCallerAlreadyKnows": {
            name: sorted(names) for name, names in sorted(rules.already_known.items())
        },
        "exclusions": [
            {
                "rule": exclusion.rule,
                "because": exclusion.because,
                "modulesExcluded": excluded.get(exclusion.rule, 0),
            }
            for exclusion in rules.exclusions
        ],
        "modulesScored": sum(1 for module in modules if not module["excludedBy"]),
        "modulesNeverScored": sum(1 for module in modules if module["excludedBy"]),
    }


def _largest(modules):
    """The module with the most lines in it, and what this page measured it at.

    Reported so that size and depth can be seen not to be the same thing, on the one
    module where the difference is largest. Nothing here ranks it and nothing here
    proposes anything about it: `lines` is a fact about the file, and the numbers beside
    it were read without it.
    """
    if not modules:
        return None
    biggest = max(modules, key=lambda module: (module["lines"], module["id"]))
    log.info(
        "largest module=%s language=%s lines=%d interfaceCost=%s reach=%d leverage=%s",
        biggest["id"],
        biggest["language"],
        biggest["lines"],
        biggest["interface"]["cost"],
        biggest["reach"]["count"],
        biggest["depth"]["leverage"],
    )
    return {
        "moduleId": biggest["id"],
        "name": biggest["name"],
        "package": biggest["package"],
        "language": biggest["language"],
        "lines": biggest["lines"],
        "interfaceCost": biggest["interface"]["cost"],
        "methods": len(biggest["interface"]["methods"]),
        "reach": biggest["reach"]["count"],
        "leverage": biggest["depth"]["leverage"],
    }


def _refuse_duplicate_ids(modules):
    """Stop the run if two files declare the same module, naming both of them."""
    holders = {}
    for module in modules:
        holders.setdefault(module["id"], []).append(
            "/".join(part for part in (module["root"], module["path"]) if part)
        )

    clashes = {id_: paths for id_, paths in holders.items() if len(paths) > 1}
    for id_, paths in sorted(clashes.items()):
        log.warning(
            "refusing to build the graph: module id=%s is declared %d times in %s",
            id_,
            len(paths),
            ", ".join(sorted(paths)),
        )
    if clashes:
        raise DuplicateModules(clashes)


def _read(whole):
    """The file as text, or a named failure saying why it could not be read.

    Every way a file can refuse to be read ends here as one named failure: a dangling
    symlink, a file with no read permission and a file deleted since the walk are all
    OSError, and letting one of those out would end the run with a traceback and write
    neither output — one unreadable file costing the whole page instead of one card.
    The reason is the errno's own text, never the path, which would be machine-specific.

    Bytes that are not text arrive here too, and they are the same kind of finding: the
    parser was never handed any Java to fail on.
    """
    try:
        with open(whole, "rb") as handle:
            return handle.read().decode("utf-8")
    except UnicodeDecodeError as broken:
        raise SourceUnreadable("not valid UTF-8: %s" % broken.reason)
    except OSError as unreadable:
        raise SourceUnreadable(
            "could not be opened: %s" % (unreadable.strerror or unreadable.__class__.__name__)
        )


def serialise(document):
    """The document as bytes, written the same way every time.

    Keys sorted, one fixed separator, ASCII only and a closing newline: two runs over the
    same source have to produce the same bytes, and a serialiser that varies would be the
    first thing to break that.
    """
    return (json.dumps(document, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("utf-8")
