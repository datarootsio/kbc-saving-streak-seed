"""One command: source in, a graph document and a page out.

Both outputs are written from the same document in the same run, so the page can never
describe a codebase the graph does not. The run says out loud how much of the source it
actually read, because a picture drawn from half the files deserves half the trust.
"""

import argparse
import logging
import os
import sys

from . import graph, page, scoring

log = logging.getLogger("module_depth_map.cli")

DEFAULT_SOURCE = os.path.join("backend", "src", "main", "java")
DEFAULT_GRAPH = os.path.join("docs", "module-depth-map.json")
DEFAULT_PAGE = os.path.join("docs", "module-depth-map.html")


def parser():
    it = argparse.ArgumentParser(
        prog="module-depth-map",
        description="Write a graph document of this repository's modules, and a page rendering it.",
    )
    it.add_argument(
        "--source",
        action="append",
        metavar="DIR",
        help="a directory of source to read (repeatable; default %s)" % DEFAULT_SOURCE,
    )
    it.add_argument("--graph", default=DEFAULT_GRAPH, metavar="FILE", help="where to write the graph document")
    it.add_argument("--page", default=DEFAULT_PAGE, metavar="FILE", help="where to write the page")
    it.add_argument(
        "--scoring",
        default=scoring.DEFAULT_CONFIGURATION,
        metavar="FILE",
        help="the scoring weights and exclusion rules to apply (default the file beside the tool)",
    )
    it.add_argument(
        "--log-level",
        default="INFO",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="how much the run says about what it did (default INFO)",
    )
    return it


def main(argv=None):
    arguments = parser().parse_args(argv)
    # force, because basicConfig is otherwise a no-op once anything has configured the
    # root logger: without it a second run in the same process — the suite, or any caller
    # that imports this module — silently keeps the first run's --log-level.
    logging.basicConfig(
        stream=sys.stderr,
        level=getattr(logging, arguments.log_level),
        format="%(levelname)-5s %(name)s %(message)s",
        force=True,
    )

    sources = arguments.source or [DEFAULT_SOURCE]
    missing = [directory for directory in sources if not os.path.isdir(directory)]
    if missing:
        log.warning("refused to run: no such source directory %s", ", ".join(missing))
        return 2

    log.info(
        "run started sources=%s graph=%s page=%s scoring=%s",
        ",".join(sources),
        arguments.graph,
        arguments.page,
        arguments.scoring,
    )
    try:
        rules = scoring.load(arguments.scoring)
    except scoring.ConfigurationRefused as refused:
        log.warning(
            "refused to run: the scoring rules in %s cannot be used: %s. Nothing is scored "
            "with a rule nobody wrote",
            arguments.scoring,
            refused.reason,
        )
        return 4

    try:
        document = graph.build([graph.java_root(directory) for directory in sources], rules)
    except graph.DuplicateModules as clash:
        log.warning(
            "refused to run: %d module id(s) are declared more than once (%s), and a page "
            "drawn from them would show one of each pair twice and the other not at all",
            len(clash.clashes),
            ", ".join(clash.clashes),
        )
        return 3

    # Both outputs are turned into bytes before either is written, and then written
    # together. A page that fails to render, or fails to be written, after the graph has
    # landed would leave a fresh document beside a stale picture of it, which is the one
    # thing this command promises cannot happen.
    serialised = graph.serialise(document)
    rendered = page.render(document, serialised)
    written_graph, written_page = write_together(
        (arguments.graph, serialised), (arguments.page, rendered)
    )
    log.info(
        "run finished graphBytes=%d pageBytes=%d filesParsed=%d filesUnparsed=%d modules=%d "
        "scored=%d neverScored=%d",
        written_graph,
        written_page,
        document["source"]["filesParsed"],
        document["source"]["filesUnparsed"],
        len(document["modules"]),
        document["scoring"]["modulesScored"],
        document["scoring"]["modulesNeverScored"],
    )

    if document["source"]["filesUnparsed"]:
        log.warning(
            "the page is drawn from %d of %d source files: %d could not be read, and every "
            "module in them is missing from it",
            document["source"]["filesParsed"],
            document["source"]["filesSeen"],
            document["source"]["filesUnparsed"],
        )
    return 0


def write_together(*outputs):
    """Write every one of these (path, bytes), or leave all of them as they were.

    Each is written beside where it belongs and then moved into place. Rendering both to
    bytes before writing either is not enough on its own: the second `open` fails on a
    read-only directory or a full disk just as readily as the render does, and a graph
    that had already landed would then sit beside the page it no longer describes — the
    stale one being the file a reader opens.

    The two moves are not one step, and nothing here pretends otherwise. They are metadata
    operations on files already written whole, so what is left to fail between them is
    what would have failed at the `open` above; the window this closes is the wide one.
    """
    staged = []
    try:
        for path, content in outputs:
            directory = os.path.dirname(path)
            if directory:
                os.makedirs(directory, exist_ok=True)
            beside = path + ".writing"
            with open(beside, "wb") as handle:
                handle.write(content)
            staged.append((beside, path, len(content)))
    except OSError:
        for beside, path, _ in staged:
            log.debug("discarding path=%s: nothing was written for %s", beside, path)
            _discard(beside)
        raise
    for beside, path, size in staged:
        os.replace(beside, path)
        log.debug("wrote path=%s bytes=%d", path, size)
    return [size for _, _, size in staged]


def _discard(path):
    try:
        os.remove(path)
    except OSError as stuck:
        log.warning("could not remove the part-written file %s: %s", path, stuck)
