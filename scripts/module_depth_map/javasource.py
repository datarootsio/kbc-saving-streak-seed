"""Reading a Java source file well enough to name the modules in it.

Parsing is targeted pattern matching over the source rather than a full grammar: enough
for the conventional Java in this repository, and the reason a file that does not match
is reported by name instead of being scored as empty.

The unit is the top-level type declaration. Types declared inside one are named as that
module's nested types rather than becoming modules of their own, so nothing in a file is
silently dropped.

"Silently" is the load-bearing word, and every rule here exists to keep it true. Pattern
matching will always meet Java it does not understand, so the file is failed by name
whenever this parser can tell it is no longer reading what the compiler would read:

- a comment, text block or literal that is never closed, because everything after the
  opener is blanked out and the declarations inside it would vanish without a word;
- braces that do not balance, in either direction — a closing brace with nothing open is
  as much a broken file as one left open at the end, and it moves declarations onto a
  module that does not hold them;
- a type whose place this parser cannot explain, because a nested type it cannot attribute
  to anything is a type it has stopped tracking;
- a reserved declaration keyword the patterns walked past, which is a module the page
  would otherwise lose with nobody noticing.
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
    """One type declaration found in a file, and where in the file it sits.

    `owner` is the top-level type whose body holds it, or None when it is that top-level
    type itself. `qualified` names it relative to that owner — `Kind`, `Body.Kind` — so
    two same-named types declared in different corners of one file can be told apart on
    the page instead of arriving as the same word twice.
    """

    def __init__(self, name, kind, depth, ends_at, owner, qualified):
        self.name = name
        self.kind = kind
        self.depth = depth
        self.ends_at = ends_at
        self.owner = owner
        self.qualified = qualified


class ParsedFile:
    """What one Java file turned out to contain."""

    def __init__(self, package, types, lines):
        self.package = package
        self.types = types
        self.lines = lines

    @property
    def top_level(self):
        return [declared for declared in self.types if declared.owner is None]

    def nested_names(self, top_level_type):
        """The names of the types declared inside this one, sorted, each named once.

        Two declarations can still land on the same qualified name — two classes called
        `Row` in two different method bodies of the same module — and listing that name
        twice reads as a rendering fault rather than as the source it came from, so the
        repeat is logged and the name is carried once.
        """
        names = []
        for declared in self.types:
            if declared.owner is not top_level_type:
                continue
            if declared.qualified in names:
                log.debug(
                    "nested name declared more than once name=%s in=%s, naming it once",
                    declared.qualified,
                    top_level_type.name,
                )
                continue
            names.append(declared.qualified)
        return sorted(names)


def mask_comments_and_literals(text):
    """The same text with comments and literal contents blanked out, offsets preserved.

    Brace counting and declaration matching both run over this rather than over the
    source, so a brace in a string or the word "class" in a comment cannot move them.

    Every branch blanks exactly as many characters as it consumed and keeps every newline
    where it was, so the result is the same length as the source and an offset — or a line
    number — in one is the same in the other. The closing delimiter of each form is looked
    for strictly after the opening one, because the `/` in `/*` is not allowed to close the
    comment it opened: reading `/*/` as a finished comment would let whatever follows it on
    the line be drawn as real source.

    A form that is never closed ends the parse instead of blanking the rest of the file.
    Blanking it is what a reader would never see: the declarations after the opener would
    be gone from the page with no warning, and even the keyword cross-check below cannot
    miss them, because there is nothing left in the masked text to count.
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
            opened = i
            out.append("  ")
            i += 2
            while i < n and text[i:i + 2] != "*/":
                out.append(_blank(text[i]))
                i += 1
            if i >= n:
                raise ParseFailure(_never_closed("block comment", text, opened))
            out.append("  ")
            i += 2
        elif text[i:i + 3] == '"""':
            opened = i
            out.append("   ")
            i += 3
            while i < n and text[i:i + 3] != '"""':
                i = _blank_one(text, out, i)
            if i >= n:
                raise ParseFailure(_never_closed("text block", text, opened))
            out.append("   ")
            i += 3
        elif ch in ('"', "'"):
            quote = ch
            opened = i
            out.append(" ")
            i += 1
            while i < n and text[i] != quote and text[i] != "\n":
                i = _blank_one(text, out, i)
            if i >= n or text[i] == "\n":
                raise ParseFailure(
                    _never_closed(
                        "string literal" if quote == '"' else "character literal", text, opened
                    )
                )
            out.append(" ")
            i += 1
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def _blank_one(text, out, i):
    """Blank the character at `i`, or the whole escape sequence that starts there."""
    out.append(_blank(text[i]))
    if text[i] == "\\" and i + 1 < len(text):
        out.append(_blank(text[i + 1]))
        return i + 2
    return i + 1


def _blank(character):
    return "\n" if character == "\n" else " "


def _never_closed(form, text, opened):
    return "%s is never closed: opened on line %d" % (form, _line_of(text, opened))


def _line_of(text, position):
    return text.count("\n", 0, position) + 1


def _brace_depths(masked):
    """(offset, depth-after-this-brace) for every brace, cheap to binary-search.

    The offset of the first brace that closes something never opened comes back too. That
    file is broken, and it is broken in a way that lies rather than merely failing: every
    later declaration is counted at a depth the source does not have, so a top-level type
    is drawn as a nested one — or as nothing at all — while the totals still add up.
    """
    depths = []
    depth = 0
    closed_too_many_at = None
    for match in re.finditer(r"[{}]", masked):
        if match.group(0) == "{":
            depth += 1
        else:
            depth -= 1
            if depth < 0 and closed_too_many_at is None:
                closed_too_many_at = match.start()
        depths.append((match.start(), depth))
    return depths, depth, closed_too_many_at


def _first_brace_after(offsets, position):
    """The index of the first brace past this offset, from a prefix scan done once."""
    lo, hi = 0, len(offsets)
    while lo < hi:
        mid = (lo + hi) // 2
        if offsets[mid][0] <= position:
            lo = mid + 1
        else:
            hi = mid
    return lo


def _depth_at(offsets, position):
    """How many braces are open at the given offset."""
    after = _first_brace_after(offsets, position)
    return offsets[after - 1][1] if after else 0


def _body_ends_at(offsets, position, depth):
    """Where the body opened after `position` closes again, or None at the end of file."""
    for offset, after in offsets[_first_brace_after(offsets, position):]:
        if after == depth:
            return offset
    return None


def parse(text, path):
    """What this Java file contains, or a ParseFailure naming why it could not be read."""
    masked = mask_comments_and_literals(text)
    lines = len(text.splitlines())

    depths, final_depth, closed_too_many_at = _brace_depths(masked)
    if closed_too_many_at is not None:
        raise ParseFailure(
            "braces do not balance: a closing brace with nothing open on line %d"
            % _line_of(text, closed_too_many_at)
        )
    if final_depth != 0:
        raise ParseFailure("braces do not balance: %d unclosed at end of file" % final_depth)

    types = _declared_types(masked, depths)
    package = _PACKAGE.search(masked)

    if path.rsplit("/", 1)[-1] in _DESCRIPTORS:
        log.debug("read package descriptor path=%s declaring no module", path)
        return ParsedFile(package.group(1) if package else "", types, lines)

    if package is None:
        raise ParseFailure("no package declaration")
    if not any(declared.owner is None for declared in types):
        raise ParseFailure("no top-level type declaration")

    log.debug(
        "parsed file path=%s package=%s lines=%d topLevel=%d nested=%d",
        path,
        package.group(1),
        lines,
        sum(1 for declared in types if declared.owner is None),
        sum(1 for declared in types if declared.owner is not None),
    )
    return ParsedFile(package.group(1), types, lines)


def _declared_types(masked, depths):
    """Every type declared in the masked text, or a ParseFailure if one cannot be placed.

    Two things are established here, and both are about not losing a module quietly. The
    first is where each declaration sits: a stack of the types whose bodies are still open
    says which type holds this one, and a declaration that is inside no open body while the
    braces say it is inside something fails the file, because the alternative is hanging it
    off whichever module happened to come before it.

    The second is the cross-check. A pattern that misses a declaration would cost the page
    a module without a word being said about it, so the reserved declaration keywords are
    counted against the declarations found, and a keyword left over fails the file by name.
    """
    types = []
    declared_at = set()
    open_types = []

    for match in _TYPE.finditer(masked):
        start = match.start(1)
        declared_at.add(start)
        depth = _depth_at(depths, start)
        while open_types and (
            open_types[-1].depth >= depth or open_types[-1].ends_at <= start
        ):
            open_types.pop()
        if bool(open_types) != (depth > 0):
            raise ParseFailure(
                "a type this parser cannot place: %s on line %d sits %d brace(s) deep in "
                "nothing it can name" % (match.group(2), _line_of(masked, start), depth)
            )
        ends_at = _body_ends_at(depths, start, depth)
        declared = DeclaredType(
            name=match.group(2),
            kind=_KINDS[match.group(1)],
            depth=depth,
            ends_at=len(masked) if ends_at is None else ends_at,
            owner=open_types[0] if open_types else None,
            qualified=".".join(
                [holder.name for holder in open_types[1:]] + [match.group(2)]
            ),
        )
        types.append(declared)
        open_types.append(declared)

    for keyword in _RESERVED_DECLARATION.finditer(masked):
        if keyword.start() not in declared_at:
            raise ParseFailure(
                "a type declaration this parser cannot read: %s on line %d"
                % (keyword.group(0), _line_of(masked, keyword.start()))
            )
    return types
