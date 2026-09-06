"""One command: source in, a graph document and a page out.

Both outputs are written from the same document in the same run, so the page can never
describe a codebase the graph does not. The run says out loud how much of the source it
actually read, because a picture drawn from half the files deserves half the trust.

One argument has no default and cannot have one: the day the run is an observation of.
Every other value here can fall back on something written down, and this one could only
fall back on the machine's clock — which would move the bytes between two runs over
unchanged source, and would date the page by when somebody pressed a key rather than by
what it is a picture of. So a run with no `--snapshot-date` is refused with the reason
said out loud rather than dated quietly.
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


# Both halves of the application, because a page drawn from one of them is a picture of
# half an application that does not say which half. A root is a directory rather than a
# language: what each file under it is read as is decided by its own ending, so pointing
# this at a directory holding Java and TypeScript together needs nothing said here.
#
# Where each points is the whole of how test code and build output stay out of the graph
# by default — `src/main/java` is not `src/test/java`, and `frontend/src` is not
# `frontend/node_modules` — and `sourcesNotRead` in the scoring file is what holds when
# somebody points this somewhere wider.
DEFAULT_SOURCES = (
    os.path.join("backend", "src", "main", "java"),
    os.path.join("frontend", "src"),
)
DEFAULT_GRAPH = os.path.join("docs", "module-depth-map.json")
DEFAULT_PAGE = os.path.join("docs", "module-depth-map.html")


def parser():
    it = argparse.ArgumentParser(
        prog="module-depth-map",
        description="Write a graph document of this repository's modules, and a page rendering it.",
        # Written out rather than generated. argparse brackets everything it has not been
        # told is `required=True`, and `--snapshot-date` is required without being
        # argparse's kind of required — see `main`, which refuses it in words rather than
        # letting argparse end the process from inside a function documented to answer an
        # exit code. The generated line read `[--snapshot-date YYYY-MM-DD]` four lines
        # above help text reading "Required", which is this tool's usage line disagreeing
        # with its own help about the one argument it will not run without. Written here,
        # the required one is first and unbracketed, and `TheUsageLineSaysWhatTheHelpSays`
        # holds every option added below to appearing on this line.
        usage="%(prog)s --snapshot-date " + graph.SNAPSHOT_DATE + " [-h] [--source DIR]\n"
              "                        [--graph FILE] [--page FILE] [--scoring FILE]\n"
              "                        [--log-level {DEBUG,INFO,WARNING,ERROR}]",
    )
    it.add_argument(
        "--source",
        action="append",
        metavar="DIR",
        help="a directory of source to read, in any language this tool knows the file "
             "endings of (repeatable; default %s)" % ", ".join(DEFAULT_SOURCES),
    )
    # Not defaulted, and deliberately not defaultable. The only default available is the
    # machine's clock, and reading it would put a value in the output that changes
    # between two runs over unchanged source — and would date the page by when it was
    # generated rather than by what it is an observation of. So whoever runs the tool
    # says which day this is a picture of, or there is no picture.
    it.add_argument(
        "--snapshot-date",
        metavar=graph.SNAPSHOT_DATE,
        help="the day this run is an observation of, written %s. Required: no clock is "
             "read anywhere in this tool, so a page is dated by whoever ran it or not at "
             "all" % graph.SNAPSHOT_DATE,
    )
    it.add_argument("--graph", default=DEFAULT_GRAPH, metavar="FILE", help="where to write the graph document")
    it.add_argument("--page", default=DEFAULT_PAGE, metavar="FILE", help="where to write the page")
    it.add_argument(
        "--scoring",
        default=scoring.DEFAULT_CONFIGURATION,
        metavar="FILE",
        help="the scoring weights, exclusion rules and flows to apply (default the file beside the tool)",
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

    # Before the source is walked, because a run that cannot be dated is not a run and
    # there is no reason to read seventy files to find that out. Refused in words rather
    # than by argparse's own `required=True`, which ends the process from inside a
    # function documented to answer an exit code, and says "the following arguments are
    # required" where the reason a reader needs is why this tool will not date a page
    # itself.
    if arguments.snapshot_date is None:
        log.warning(
            "refused to run: no snapshot date given, so there is nothing to date this "
            "page with. Nothing here reads a clock — a date read off the machine would "
            "change the output between two runs over unchanged source, and would say "
            "when the page was generated rather than what it is an observation of. Pass "
            "--snapshot-date %s",
            graph.SNAPSHOT_DATE,
        )
        return 6
    try:
        snapshot = graph.a_snapshot_date(arguments.snapshot_date)
    except graph.SnapshotNotADate as refused:
        log.warning(
            "refused to run: %s. A page dated by something that is not a day is a dated "
            "page that is not dated, and every other number on it would be read as "
            "though it were",
            refused.reason,
        )
        return 6

    chosen = arguments.source is not None
    sources = arguments.source or list(DEFAULT_SOURCES)
    missing = [directory for directory in sources if not os.path.isdir(directory)]
    if missing and (chosen or len(missing) == len(sources)):
        # A directory an operator typed is worth refusing for: they said to read it, and
        # drawing a page from what was left would answer a question nobody asked. A
        # directory this tool chose is not, and refusing for one cost every run in a
        # repository with only a backend in it — both outputs unwritten, over a frontend
        # nobody said was there. The refusal stands when *every* default is missing,
        # because then there is no source at all and a page of nothing is not an answer
        # either.
        log.warning("refused to run: no such source directory %s", ", ".join(missing))
        return 2
    for directory in missing:
        log.info(
            "default source not read path=%s reason=%s",
            directory,
            "this tool reads it when it is there and says so when it is not, because "
            "nobody asked for it on the command line",
        )
    skipped = [
        {"root": directory.replace(os.sep, "/"),
         "reason": "one of the directories this tool reads when no --source is given, "
                   "and there is none of that name in this repository"}
        for directory in missing
    ]
    sources = [directory for directory in sources if directory not in missing]

    log.info(
        "run started snapshotDate=%s sources=%s graph=%s page=%s scoring=%s",
        snapshot,
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
        document = graph.build(
            [graph.source_root(directory) for directory in sources], rules, snapshot,
            skipped,
        )
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
        written = write_together((arguments.graph, serialised), (arguments.page, rendered))
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
        "run finished snapshotDate=%s graphBytes=%d pageBytes=%d languages=%s filesParsed=%d "
        "filesUnparsed=%d pathsNotRead=%d modules=%d scored=%d neverScored=%d flows=%d "
        "flowsTraced=%d",
        document["snapshot"]["date"],
        written[arguments.graph],
        written[arguments.page],
        ",".join(document["source"]["languages"]) or "none, nothing was read",
        document["source"]["filesParsed"],
        document["source"]["filesUnparsed"],
        len(document["source"]["notRead"]["paths"]),
        len(document["modules"]),
        document["scoring"]["modulesScored"],
        document["scoring"]["modulesNeverScored"],
        len(document["flows"]),
        sum(1 for flow in document["flows"] if flow["resolved"]),
    )

    # The flows the configuration asked for and this graph could not walk. Said again
    # here, after the counts, because a flow that no longer resolves is the one finding
    # in this document that is about the tool's own configuration having gone stale
    # against the source — and the run that produced it is where somebody is standing.
    for flow in document["flows"]:
        if flow["resolved"]:
            continue
        log.warning(
            "the flow %s is drawn with no path through the modules: %s",
            flow["flow"],
            flow["couldNotResolve"],
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
    """Write every one of these (path, bytes) where they belong, and say what happened.

    Answers how many bytes landed at each path, keyed by the path asked for rather than
    by position. Unpacking the answer into one name per output made a third output an
    end to the run with a `ValueError` — a traceback, out of the one function written to
    make a traceback impossible.

    Each is written beside where it belongs and then moved into place. Rendering both to
    bytes before writing either is not enough on its own: the second `open` fails on a
    read-only directory or a full disk just as readily as the render does, and a graph
    that had already landed would then sit beside the page it no longer describes — the
    stale one being the file a reader opens.

    The moves are what is left, and two moves are not one step: there is no way to rename
    two files at once, so "both or neither" cannot be promised outright and is not
    promised here. What is promised is that no failure is a silent, a partial or a
    misattributed one:

    - every way a move can fail that this tool can see coming is checked before a single
      byte is written. A destination that is already a directory is one such way, and the
      only one the `open` below does not catch first: `page.html.writing` opens perfectly
      well next to a directory called `page.html`, and the move then fails after the
      graph has landed. Two outputs sent to one destination is the other, and it is worse
      than a failure: both stage to the same `.writing` file, so the second's bytes are
      the first's by the time anything is moved, the previous run's file is replaced by a
      document that is not the one named, and the run then reports the wrong output as
      the one that landed.
    - a failure while staging names the output that failed, taken from the arguments
      rather than from what had been staged when it happened. Reading the path out of
      `staged` said the wrong file when a later output was the one that could not be
      written, and had nothing to read at all — an `IndexError` out of the run, which is
      the traceback the paragraph below promises never to end on — when it was the first.
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

    # `realpath`, so that two spellings of one file — `docs/g.json` and `./docs/g.json`,
    # or a path through a symlinked directory — are the same destination here as they
    # are to the filesystem.
    destinations = {}
    for path, _ in outputs:
        whole = os.path.realpath(path)
        if whole in destinations:
            raise OutputsUnwritten(
                "%s and %s are the same file, and the run has two different documents to "
                "put there" % (destinations[whole], path)
            )
        destinations[whole] = path

    staged = []
    for path, content in outputs:
        try:
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
                "%s could not be written: %s" % (path, unwritable)
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
    return {path: size for _, path, size in staged}


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
