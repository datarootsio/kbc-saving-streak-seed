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


class SourceTreeTest(unittest.TestCase):
    """A test with a scratch directory it can write source files into."""

    def setUp(self):
        self.scratch = tempfile.mkdtemp(prefix="module-depth-map-")
        self.addCleanup(shutil.rmtree, self.scratch, True)

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

    def raw(self, relative, text):
        """Write a file exactly as given, package declaration and all."""
        path = os.path.join(self.root, relative)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)
        return path
