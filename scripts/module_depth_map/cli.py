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


class OutputsUnwritten(Exception):
    """The run had both documents and could not put them where they belong.

    `landed` names the outputs that were moved into place before the failure. Empty is the
    ordinary case and the good one — nothing was touched, and the files a reader opens are
    the ones the last run left. Anything in it is the case worth shouting about: the two
    files no longer describe the same source.
    """

    def __init__(self, reason, landed=()):
        super().__init__(reason)
        self.reason = reason
        self.landed = tuple(landed)


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
    # landed would leave a fresh document beside a stale picture of it — the failure
    # `write_together` exists to prevent, and to name out loud if it ever happens anyway.
    # Nothing in here is allowed to end the run with a traceback: a stack of Python names
    # a line of this file, where a reader needs the two paths and which of them changed.
    try:
        serialised = graph.serialise(document)
        rendered = page.render(document, serialised)
        written_graph, written_page = write_together(
            (arguments.graph, serialised), (arguments.page, rendered)
        )
    except OSError as failed:
        log.error(
            "the run could not turn the graph it built into files: %s. Nothing was "
            "written, and both files are the previous run's",
            failed,
            exc_info=True,
        )
        return 5
    except OutputsUnwritten as unwritten:
        if unwritten.landed:
            log.error(
                "the run wrote %s and then could not write the rest: %s. Those files no "
                "longer describe the same source, and the ones that did not land are the "
                "previous run's",
                ", ".join(unwritten.landed),
                unwritten.reason,
                exc_info=True,
            )
        else:
            log.warning(
                "refused to write: %s. Nothing was written, and both files are the "
                "previous run's",
                unwritten.reason,
            )
        return 5
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
    """Write every one of these (path, bytes) where they belong, or write none of them.

    Each is written beside where it belongs and then moved into place. Rendering both to
    bytes before writing either is not enough on its own: the second `open` fails on a
    read-only directory or a full disk just as readily as the render does, and a graph
    that had already landed would then sit beside the page it no longer describes — the
    stale one being the file a reader opens.

    The moves are what is left, and two moves are not one step: there is no way to rename
    two files at once, so "both or neither" cannot be promised outright and is not
    promised here. What is promised is that no failure is a silent or a partial one:

    - every way a move can fail that this tool can see coming is checked before a single
      byte is written. A destination that is already a directory is the one such way, and
      the only one the `open` below does not catch first: `page.html.writing` opens
      perfectly well next to a directory called `page.html`, and the move then fails
      after the graph has landed.
    - a move that fails anyway raises with the paths that did land, so the run can say
      which of the two files a reader is now looking at and which one is the old one.
      Silence there is the failure this whole function exists to prevent, and a traceback
      is a kind of silence: it names a line of Python rather than the two files.

    A staged file is registered before it is opened, so a `.writing` file whose own write
    failed is discarded with the rest rather than left behind for the next run to puzzle
    over.
    """
    for path, _ in outputs:
        if os.path.isdir(path):
            raise OutputsUnwritten(
                "%s is a directory, and a file cannot be moved onto one" % path
            )

    staged = []
    try:
        for path, content in outputs:
            directory = os.path.dirname(path)
            if directory:
                os.makedirs(directory, exist_ok=True)
            beside = path + ".writing"
            staged.append((beside, path, len(content)))
            with open(beside, "wb") as handle:
                handle.write(content)
    except OSError as unwritable:
        _discard_all(staged)
        raise OutputsUnwritten(
            "%s could not be written: %s" % (staged[-1][1], unwritable)
        ) from unwritable

    landed = []
    for index, (beside, path, size) in enumerate(staged):
        try:
            os.replace(beside, path)
        except OSError as unmovable:
            _discard_all(staged[index:])
            raise OutputsUnwritten(
                "%s could not be moved into place: %s" % (path, unmovable), landed
            ) from unmovable
        landed.append(path)
        log.debug("wrote path=%s bytes=%d", path, size)
    return [size for _, _, size in staged]


def _discard_all(staged):
    for beside, path, _ in staged:
        log.debug("discarding path=%s: nothing was written for %s", beside, path)
        _discard(beside)


def _discard(path):
    try:
        os.remove(path)
    except FileNotFoundError:
        pass
    except OSError as stuck:
        log.warning("could not remove the part-written file %s: %s", path, stuck)
