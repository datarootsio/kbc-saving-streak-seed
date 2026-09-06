"""Purpose-built source trees to run the tool over.

Fixtures rather than the application, wherever a test is about a rule: a fixture states
the shape it was built to have, and it does not change when a feature lands.
"""

import os
import shutil
import tempfile
import unittest

REPOSITORY = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
BACKEND_SOURCE = os.path.join(REPOSITORY, "backend", "src", "main", "java")
FRONTEND_SOURCE = os.path.join(REPOSITORY, "frontend", "src")

# The day every fixture here is an observation of. Every run needs one — the tool refuses
# to date a page itself — and one written down beside the fixtures keeps the suite saying
# the same thing on every machine and on every day it is run.
#
# Deliberately nothing like today: a run that read a clock instead of its argument would
# still write a date, and a fixture dated near today could not tell the two apart.
A_SNAPSHOT = "2001-02-03"


class SourceTreeTest(unittest.TestCase):
    """A test with a scratch directory it can write source files into."""

    def setUp(self):
        self.scratch = tempfile.mkdtemp(prefix="module-depth-map-")
        self.addCleanup(shutil.rmtree, self.scratch, True)
        # The scratch directory is a repository of its own, because a source root is
        # named by where it sits inside the nearest one. Without this marker the walk
        # leaves the scratch directory and finds whatever repository TMPDIR happens to
        # sit inside, and every fixture is then named by its path under that instead of
        # by itself — a suite that goes red for a reason that has nothing to do with the
        # code under test. This repository's own agents run inside git worktrees, so
        # that is not a hypothetical arrangement.
        os.makedirs(os.path.join(self.scratch, ".git"))

    def tree(self, name):
        """A new, empty source root named `name`, which is how the graph will label it."""
        root = os.path.join(self.scratch, name)
        os.makedirs(root)
        return SourceTree(root)


class SourceTree:
    def __init__(self, root):
        self.root = root

    def java(self, package, name, body):
        """Write one Java file under the package's directories and return its path."""
        directory = os.path.join(self.root, *package.split("."))
        os.makedirs(directory, exist_ok=True)
        path = os.path.join(directory, name + ".java")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write("package %s;\n\n%s\n" % (package, body))
        return path

    def typescript(self, directory, name, body):
        """Write one TypeScript file under this directory and return its path.

        The name carries its own extension, because `.ts` and `.tsx` are read differently
        — JSX is legal in one of them and not in the other — and a fixture about JSX has
        to be able to say which it is writing.
        """
        under = os.path.join(self.root, *directory.split("/")) if directory else self.root
        os.makedirs(under, exist_ok=True)
        path = os.path.join(under, name)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(body if body.endswith("\n") else body + "\n")
        return path

    def raw(self, relative, text):
        """Write a file exactly as given, package declaration and all."""
        path = os.path.join(self.root, relative)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)
        return path


def bytes_of(path):
    """A file as bytes, for the tests that compare two outputs byte for byte."""
    with open(path, "rb") as handle:
        return handle.read()
