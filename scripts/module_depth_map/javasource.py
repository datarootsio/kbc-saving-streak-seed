"""Reading a Java source file well enough to name the modules in it.

Parsing is targeted pattern matching over the source rather than a full grammar: enough
for the conventional Java in this repository, and the reason a file that does not match
is reported by name instead of being scored as empty.

The unit is the top-level type declaration. Types declared inside one are named as that
module's nested types rather than becoming modules of their own, so nothing in a file is
silently dropped.

"Silently" is the load-bearing word. Pattern matching will always meet Java it does not
understand, so every declaration keyword that cannot be reserved for something else is
counted against the declarations actually found: a keyword the patterns walked past ends
the parse with a reason naming the line, rather than costing the page a module nobody
notices is missing.
"""

import logging
import re

log = logging.getLogger("module_depth_map.javasource")

_PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)

# A declaration is its keyword followed by the name it declares, wherever it sits: after
# annotations on the same line, inside another type's braces, or after any modifiers.
# Anchoring to the start of a line instead would drop `@Deprecated public class Foo` and
# `class Outer { class Inner {} }` without a word, which is the one failure this file
# exists to make impossible. What the anchor used to buy is bought by the lookbehind
# instead: `Thing.class` is a class literal, not a declaration.
_TYPE = re.compile(
    r"(?<![\w.$])(class|interface|enum|record|@interface)[ \t\r\n]+([A-Za-z_$][\w$]*)"
)

# `class`, `interface` and `enum` are reserved words: outside a class literal they can
# only ever open a declaration. Any one of them the pattern above did not match is Java
# this parser cannot read, and the file is failed by name rather than quietly shrunk.
# `record` is left out on purpose — it is a contextual keyword and a legal identifier, so
# an unmatched `record` is usually a variable rather than a missed module.
_RESERVED_DECLARATION = re.compile(r"(?<![\w.$])@?(?:class|interface|enum)(?![\w$])")

# A file that declares no type but is still perfectly well formed. Failing these as
# unreadable would paint the page's alarm band over a file with nothing wrong with it,
# and an alarm that cries wolf stops being read.
_DESCRIPTORS = ("package-info.java", "module-info.java")

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

    Every branch blanks exactly as many characters as it consumed, so the result is the
    same length as the source and an offset in one is the same offset in the other. The
    closing delimiter of each form is looked for strictly after the opening one, because
    the `/` in `/*` is not allowed to close the comment it opened: reading `/*/` as a
    finished comment would let whatever follows it on the line be drawn as real source.
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
            out.append("  ")
            i += 2
            while i < n and text[i:i + 2] != "*/":
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            if i < n:
                out.append("  ")
                i += 2
        elif text[i:i + 3] == '"""':
            out.append("   ")
            i += 3
            while i < n and text[i:i + 3] != '"""':
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            if i < n:
                out.append("   ")
                i += 3
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
            if i < n:
                out.append(" ")
                i += 1
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
    lines = len(text.splitlines())

    depths, final_depth = _brace_depths(masked)
    if final_depth != 0:
        raise ParseFailure("braces do not balance: %d unclosed at end of file" % final_depth)

    types = _declared_types(masked, depths)
    package = _PACKAGE.search(masked)

    if path.rsplit("/", 1)[-1] in _DESCRIPTORS:
        log.debug("read package descriptor path=%s declaring no module", path)
        return ParsedFile(package.group(1) if package else "", types, lines)

    if package is None:
        raise ParseFailure("no package declaration")
    if not any(t.depth == 0 for t in types):
        raise ParseFailure("no top-level type declaration")

    log.debug(
        "parsed file path=%s package=%s lines=%d topLevel=%d nested=%d",
        path,
        package.group(1),
        lines,
        sum(1 for t in types if t.depth == 0),
        sum(1 for t in types if t.depth > 0),
    )
    return ParsedFile(package.group(1), types, lines)


def _declared_types(masked, depths):
    """Every type declared in the masked text, or a ParseFailure if one was walked past.

    The second half is the point. A pattern that misses a declaration would cost the page
    a module without a word being said about it, and "the page shows everything" would
    quietly become "the page shows everything the patterns happened to match". So the
    reserved declaration keywords are counted against the declarations found, and a
    keyword left over fails the whole file by name.
    """
    types = []
    declared_at = set()
    for match in _TYPE.finditer(masked):
        start = match.start(1)
        declared_at.add(start)
        depth = _depth_at(masked, depths, start)
        types.append(DeclaredType(match.group(2), _KINDS[match.group(1)], depth))

    for keyword in _RESERVED_DECLARATION.finditer(masked):
        if keyword.start() not in declared_at:
            raise ParseFailure(
                "a type declaration this parser cannot read: %s on line %d"
                % (keyword.group(0), masked.count("\n", 0, keyword.start()) + 1)
            )
    return types
