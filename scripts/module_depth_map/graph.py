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
# object and gave every module an `interface` and an `excludedBy`: a v1 reader looking
# for what it was promised finds none of them. The tool refuses a *configuration* whose
# schema it does not know, so versioning what it writes as well is the same promise kept
# in the other direction.
SCHEMA = "module-depth-map/2"

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

    modules.sort(key=lambda module: (module["package"], module["name"]))
    unparsed.sort(key=lambda entry: (entry["root"], entry["path"]))
    _refuse_duplicate_ids(modules)

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
    return document


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
