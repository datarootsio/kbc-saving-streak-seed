"""Reading a Java source file well enough to name the modules in it.

Parsing is targeted pattern matching over the source rather than a full grammar: enough
for the conventional Java in this repository, and the reason a file that does not match
is reported by name instead of being scored as empty.

The unit is the top-level type declaration. Types declared inside one are named as that
module's nested types rather than becoming modules of their own, so nothing in a file is
silently dropped.
"""

import logging
import re

log = logging.getLogger("module_depth_map.javasource")

_PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)

# A declaration in this repository always opens a line, after its modifiers and under
# any annotations. Matching from the start of a line keeps the word "record", which is a
# contextual keyword, from being read out of the middle of an expression.
_TYPE = re.compile(
    r"^[ \t]*"
    r"(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)[ \t]+)*"
    r"(class|interface|enum|record|@interface)[ \t]+"
    r"(\w+)",
    re.M,
)

_KINDS = {
    "class": "class",
    "interface": "interface",
    "enum": "enum",
    "record": "record",
    "@interface": "annotation",
}


class ParseFailure(Exception):
    """A source file the tool could not read. Carries the reason, never just the fact."""

    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


class DeclaredType:
    """One type declaration found in a file, and how deeply it was nested."""

    def __init__(self, name, kind, depth):
        self.name = name
        self.kind = kind
        self.depth = depth


class ParsedFile:
    """What one Java file turned out to contain."""

    def __init__(self, package, types, lines):
        self.package = package
        self.types = types
        self.lines = lines

    @property
    def top_level(self):
        return [t for t in self.types if t.depth == 0]

    def nested_under(self, top_level_name):
        """The types declared inside the given top-level type, in source order."""
        inside = []
        seen_it = False
        for declared in self.types:
            if declared.depth == 0:
                seen_it = declared.name == top_level_name
            elif seen_it:
                inside.append(declared)
        return inside


def mask_comments_and_literals(text):
    """The same text with comments and literal contents blanked out, offsets preserved.

    Brace counting and declaration matching both run over this rather than over the
    source, so a brace in a string or the word "class" in a comment cannot move them.
    """
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        two = text[i:i + 2]
        if two == "//":
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
        elif two == "/*":
            while i < n and text[i:i + 2] != "*/":
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append("  ")
            i = min(i + 2, n)
        elif text[i:i + 3] == '"""':
            out.append("   ")
            i += 3
            while i < n and text[i:i + 3] != '"""':
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append("   ")
            i = min(i + 3, n)
        elif ch in ('"', "'"):
            quote = ch
            out.append(" ")
            i += 1
            while i < n and text[i] != quote:
                if text[i] == "\\":
                    out.append(" ")
                    i += 1
                    if i < n:
                        out.append(" ")
                        i += 1
                    continue
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append(" ")
            i = min(i + 1, n)
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def _depth_at(masked, offsets, position):
    """How many braces are open at the given offset, from a prefix scan done once."""
    lo, hi = 0, len(offsets)
    while lo < hi:
        mid = (lo + hi) // 2
        if offsets[mid][0] <= position:
            lo = mid + 1
        else:
            hi = mid
    return offsets[lo - 1][1] if lo else 0


def _brace_depths(masked):
    """(offset, depth-after-this-brace) for every brace, cheap to binary-search."""
    depths = []
    depth = 0
    for match in re.finditer(r"[{}]", masked):
        if match.group(0) == "{":
            depth += 1
        else:
            depth -= 1
        depths.append((match.start(), depth))
    return depths, depth


def parse(text, path):
    """What this Java file contains, or a ParseFailure naming why it could not be read."""
    masked = mask_comments_and_literals(text)

    depths, final_depth = _brace_depths(masked)
    if final_depth != 0:
        raise ParseFailure("braces do not balance: %d unclosed at end of file" % final_depth)

    package = _PACKAGE.search(masked)
    if package is None:
        raise ParseFailure("no package declaration")

    types = []
    for match in _TYPE.finditer(masked):
        start = match.start(1)
        depth = _depth_at(masked, depths, start)
        types.append(DeclaredType(match.group(2), _KINDS[match.group(1)], depth))

    if not any(t.depth == 0 for t in types):
        raise ParseFailure("no top-level type declaration")

    log.debug(
        "parsed file path=%s package=%s topLevel=%d nested=%d",
        path,
        package.group(1),
        sum(1 for t in types if t.depth == 0),
        sum(1 for t in types if t.depth > 0),
    )
    return ParsedFile(package.group(1), types, text.count("\n") + 1)
