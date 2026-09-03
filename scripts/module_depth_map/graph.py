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

SCHEMA = "module-depth-map/1"


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
    """Where this directory sits inside its repository, or its own name if it has none.

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
            return os.path.relpath(absolute, walk).replace(os.sep, "/")
        parent = os.path.dirname(walk)
        if parent == walk:
            return os.path.basename(absolute)
        walk = parent


def java_root(path):
    return SourceRoot(path, label_for(path), "java", (".java",))


def _files_under(root):
    found = []
    for directory, subdirectories, names in os.walk(root.path):
        subdirectories.sort()
        for name in sorted(names):
            if name.endswith(root.suffixes):
                whole = os.path.join(directory, name)
                found.append((whole, os.path.relpath(whole, root.path).replace(os.sep, "/")))
    found.sort(key=lambda pair: pair[1])
    return found


def build(roots):
    """Read every source file under these roots and return the graph document.

    A file that cannot be read is named in the document and logged, never counted as a
    module with nothing in it: a parse failure that looked like an empty module would be
    indistinguishable from a real finding.
    """
    modules = []
    unparsed = []
    seen = 0

    for root in roots:
        log.debug("reading source root label=%s language=%s", root.label, root.language)
        for whole, relative in _files_under(root):
            seen += 1
            try:
                text = _read(whole)
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
                        "nested": sorted(n.name for n in parsed.nested_under(declared.name)),
                    }
                )

    modules.sort(key=lambda module: (module["package"], module["name"]))
    unparsed.sort(key=lambda entry: (entry["root"], entry["path"]))

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
        "modules": modules,
    }

    log.info(
        "graph built roots=%s filesSeen=%d filesParsed=%d filesUnparsed=%d packages=%d modules=%d",
        ",".join(document["source"]["roots"]),
        seen,
        document["source"]["filesParsed"],
        len(unparsed),
        len(document["packages"]),
        len(modules),
    )
    return document


def _read(whole):
    try:
        with open(whole, "rb") as handle:
            return handle.read().decode("utf-8")
    except UnicodeDecodeError as broken:
        raise javasource.ParseFailure("not valid UTF-8: %s" % broken.reason)


def serialise(document):
    """The document as bytes, written the same way every time.

    Keys sorted, one fixed separator, ASCII only and a closing newline: two runs over the
    same source have to produce the same bytes, and a serialiser that varies would be the
    first thing to break that.
    """
    return (json.dumps(document, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("utf-8")
