"""The languages this tool reads, and the one seam every reading is asked through.

There are two of them now, and this file exists so that neither the graph nor the scoring
rules ever name one. A module is a Java class or a TypeScript file; a name in a body is
followed by Java's scoping or by TypeScript's imports; a type crossing a seam is spelled
with `<>` in both and with `|` in one. All of that is the language's business, and none of
it is the measure's — which is the point the page makes about the frontend, and it would
be a weaker point if the analyser had a special case for it.

What a language module has to offer:

- `NAME`, the word the graph writes on every module read with it;
- `SUFFIXES`, the file endings it is the reading of, which is also how one directory of
  source can hold both languages without anybody having to say which is which;
- `KINDS`, every kind of module it can report, so a rule written about a kind that no
  language reports can be refused rather than never firing;
- `parse(text, path, root)`, the file read into modules, or a `ParseFailure` naming why
  it could not be;
- `module_id(package, name)`, the id a module is known by;
- `followed(name, package, imports)`, every module id a name written in a body could
  mean, best first;
- `written_names_in(written)` and `crosses_the_seam(written)`, the type names inside a
  written type — the first for following one to a module, the second for what a caller
  has to learn;
- `IMPORTED_EVIDENCE`, how a fan line says a module was reached through an import.
"""

import collections

from . import javasource, typescriptsource

ALL = (javasource, typescriptsource)

# Every kind of module any of these can report. A rule in the scoring file is checked
# against this rather than against one language's list, because the file scores both and a
# rule refused for naming `file` would be a rule refused for being about the frontend.
KINDS = tuple(sorted({kind for language in ALL for kind in language.KINDS}))

# Every file ending any of these reads, and which language reads it. A source root is a
# directory rather than a language, so the suffix decides — the same way `.java` already
# did when there was only one — and pointing the tool at a directory holding both is not a
# question anybody has to answer.
BY_SUFFIX = {
    suffix: language for language in ALL for suffix in language.SUFFIXES
}
SUFFIXES = tuple(sorted(BY_SUFFIX))


def of(path):
    """The language this file is written in, or None when it is not one this tool reads."""
    for suffix in SUFFIXES:
        if path.endswith(suffix):
            return BY_SUFFIX[suffix]
    return None


# What one module was written among, for whoever has to read a name the way the file that
# wrote it would: the package it sits in, the imports above it, the types it declares
# inside itself, and the language all three are read by. The language is on it because
# there are two now, and which module a supertype's name means is settled by the file that
# wrote the word rather than by the file reading it.
WrittenAmong = collections.namedtuple("WrittenAmong", "package imports nested language")

# What a module the graph was never given a reading of was written among: nothing, read by
# the language that came first. Nothing is ever resolved against it — every lookup it
# answers is a lookup for a module this graph does not hold — and it is here so that the
# answer is an empty scope rather than an exception out of a dictionary.
NOTHING_AROUND_IT = WrittenAmong("", (), (), javasource)
