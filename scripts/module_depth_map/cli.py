"""One command: source in, a graph document and a page out.

Both outputs are written from the same document in the same run, so the page can never
describe a codebase the graph does not. The run says out loud how much of the source it
actually read, because a picture drawn from half the files deserves half the trust.
"""

import argparse
import logging
import os
import sys

from . import graph, page

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
        "--log-level",
        default="INFO",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="how much the run says about what it did (default INFO)",
    )
    return it


def main(argv=None):
    arguments = parser().parse_args(argv)
    logging.basicConfig(
        stream=sys.stderr,
        level=getattr(logging, arguments.log_level),
        format="%(levelname)-5s %(name)s %(message)s",
    )

    sources = arguments.source or [DEFAULT_SOURCE]
    missing = [directory for directory in sources if not os.path.isdir(directory)]
    if missing:
        log.error("refused to run: no such source directory %s", ", ".join(missing))
        return 2

    log.info("run started sources=%s graph=%s page=%s", ",".join(sources), arguments.graph, arguments.page)
    try:
        document = graph.build([graph.java_root(directory) for directory in sources])
    except graph.DuplicateModules as clash:
        log.error(
            "refused to run: %d module id(s) are declared more than once (%s), and a page "
            "drawn from them would show one of each pair twice and the other not at all",
            len(clash.clashes),
            ", ".join(clash.clashes),
        )
        return 3

    written_graph = write(arguments.graph, graph.serialise(document))
    written_page = write(arguments.page, page.render(document))
    log.info(
        "run finished graphBytes=%d pageBytes=%d filesParsed=%d filesUnparsed=%d modules=%d",
        written_graph,
        written_page,
        document["source"]["filesParsed"],
        document["source"]["filesUnparsed"],
        len(document["modules"]),
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


def write(path, content):
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(content)
    log.debug("wrote path=%s bytes=%d", path, len(content))
    return len(content)
