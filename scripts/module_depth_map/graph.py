"""The graph document: every module the application is made of, and how well it was read.

This is the tool's real interface. The page is a rendering of this document and nothing
else, and every later measurement hangs off it, so its shape is deliberately boring:
plain data, every collection sorted, no value that could differ between two machines.

Determinism is enforced here rather than hoped for. Paths are recorded relative to the
source root they were found under, the source root is recorded relative to the repository
that holds it, and nothing is read from the clock.
"""

import json
import logging
import os

from . import javasource

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
# the total is split into.
SCHEMA = "module-depth-map/5"

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
    """A path the operating system would not hand over: not Java this parser cannot read.

    Kept apart from `javasource.ParseFailure` on purpose. "This file could not be opened"
    and "this Java could not be parsed" are different findings for the reader — one is
    about the checkout, the other about the source — and folding them together would
    also make the parser answer for a second language back-end's file handling.
    """

    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


class SourceRoot:
    """A directory of source, and the name the graph will know it by.

    The label is what keeps an absolute path out of the output: a root is named by where
    it sits inside its repository, never by where the repository sits on this machine.
    """

    def __init__(self, path, label, language, suffixes):
        self.path = path
        self.label = label
        self.language = language
        self.suffixes = suffixes


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


def java_root(path):
    return SourceRoot(path, label_for(path), "java", (".java",))


def _relative(whole, base):
    """`whole` as a forward-slash path under `base`, or "" when it is `base` itself."""
    relative = os.path.relpath(whole, base).replace(os.sep, "/")
    return "" if relative == "." else relative


def _files_under(root):
    """Every source file under this root, and every directory that would not open.

    Nothing under a root is passed over in silence, because a directory the walk skipped
    takes every module in it off the page while every count still adds up. A directory
    that cannot be listed comes back as a named failure, and a symlinked one is followed
    rather than stepped over — once, so that a link pointing back up the tree cannot make
    the same file arrive twice under two names.
    """
    found = []
    unreadable = []
    read_already = {}

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
        for name in names:
            if name.endswith(root.suffixes):
                whole = os.path.join(directory, name)
                found.append((whole, _relative(whole, root.path)))

    found.sort(key=lambda pair: pair[1])
    unreadable.sort()
    return found, unreadable


def build(roots, rules):
    """Read every source file under these roots and return the graph document.

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
    read = []
    seen = 0

    for root in roots:
        log.debug("reading source root label=%s language=%s", root.label, root.language)
        files, unreadable_directories = _files_under(root)

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
            try:
                parsed = javasource.parse(text, relative)
            except javasource.ParseFailure as failure:
                log.warning(
                    "could not parse source file root=%s path=%s reason=%s",
                    root.label,
                    relative,
                    failure.reason,
                )
                unparsed.append({"root": root.label, "path": relative, "reason": failure.reason})
                continue
            for declared in parsed.top_level:
                excluded = rules.excluded_by(declared)
                modules.append(
                    {
                        "id": parsed.package + "." + declared.name,
                        "name": declared.name,
                        "package": parsed.package,
                        "kind": declared.kind,
                        "language": root.language,
                        "root": root.label,
                        "path": relative,
                        "lines": parsed.lines,
                        "nested": parsed.nested_names(declared),
                        "interface": rules.interface_of(declared, scored=excluded is None),
                        "excludedBy": excluded,
                    }
                )
                read.append((modules[-1], declared, parsed))

    modules.sort(key=lambda module: (module["package"], module["name"]))
    unparsed.sort(key=lambda entry: (entry["root"], entry["path"]))
    _refuse_duplicate_ids(modules)
    _check_the_refusals(modules, rules)
    _measure_depth(read, rules)
    _run_the_deletion_test(modules, rules)

    packages = {}
    for module in modules:
        packages.setdefault(module["package"], []).append(module["id"])

    document = {
        "schema": SCHEMA,
        "source": {
            "roots": sorted(root.label for root in roots),
            "filesSeen": seen,
            "filesParsed": seen - len(unparsed),
            "filesUnparsed": len(unparsed),
            "unparsed": unparsed,
        },
        "packages": [
            {"name": name, "moduleIds": sorted(ids)} for name, ids in sorted(packages.items())
        ],
        "scoring": _scoring(rules, modules),
        "modules": modules,
    }

    log.info(
        "graph built roots=%s filesSeen=%d filesParsed=%d filesUnparsed=%d packages=%d "
        "modules=%d scored=%d neverScored=%d",
        ",".join(document["source"]["roots"]),
        seen,
        document["source"]["filesParsed"],
        len(unparsed),
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
        "refusals checked read=%d modulesWithFindings=%d findings=%d",
        document["scoring"]["refusals"]["refusalsRead"],
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
            "refusals read module=%s refusals=%s findings=%d",
            module["id"],
            ",".join(refusal["name"] for refusal in module["interface"]["refusals"])
            or "none this tool can read",
            len(module["findings"]),
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
    declared_by_id = {module["id"]: declared for module, declared, _ in read}
    # What each module was written among, by id: the package it sits in, the imports of
    # its file, and the types it nests. Reach needs this for the modules *above* the one
    # it is reading as well as for that one — a supertype's method shadows a static import
    # of its name, and a supertype's nested type shadows the package — and which module a
    # supertype's own name means is settled by the file that names it rather than by the
    # file being read.
    written_among = {
        module["id"]: (parsed.package, parsed.imports, module["nested"])
        for module, _, parsed in read
    }
    for module, declared, parsed in read:
        module["reach"] = rules.reach_of(
            declared, module["id"], parsed.package, parsed.imports, declared_by_id,
            module["nested"], written_among,
        )
        module["depth"] = rules.depth_of(module["reach"], module["interface"])


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
        # are of findings rather than of modules, because one module can be in both
        # disagreements at once — this repository's `ScheduledJobs` was, on two different
        # refusals — and a count of modules would hide one of them behind the other.
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
        "reachableFromOutside": sorted(rules.reachable_from_outside),
        "typesEveryCallerAlreadyKnows": sorted(rules.already_known),
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
