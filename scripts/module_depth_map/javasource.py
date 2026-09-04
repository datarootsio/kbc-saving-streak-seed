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


class Method:
    """One method a module declares, as a caller meets it.

    The types are the words the source wrote them with — `Optional<Customer>`, `long` —
    rather than anything resolved: this parser reads one file at a time, and a name it
    cannot follow to a declaration is still the name a caller has to learn.

    `type_parameters` is the exception: the names this method's own `<T>` introduces are
    holes the caller fills with a type they already hold, so they are recorded here for a
    rule outside this file to tell apart from the types it has to go and read.
    """

    def __init__(self, name, visibility, parameters, returns, type_parameters=()):
        self.name = name
        self.visibility = visibility
        self.parameters = tuple(parameters)
        self.returns = returns
        self.type_parameters = tuple(type_parameters)


class DeclaredType:
    """One type declaration found in a file, and where in the file it sits.

    `owner` is the top-level type whose body holds it, or None when it is that top-level
    type itself. `qualified` names it relative to that owner — `Kind`, `Body.Kind` — so
    two same-named types declared in different corners of one file can be told apart on
    the page instead of arriving as the same word twice.

    `annotations`, `supertypes` and `methods` are what a rule outside this file gets to
    reason about: what this type is marked with, what it is built on, and what it offers
    anybody holding one. Nothing here decides what any of that is worth.
    """

    def __init__(self, name, kind, depth, ends_at, owner, qualified,
                 annotations=(), supertypes=(), methods=(), type_parameters=()):
        self.name = name
        self.kind = kind
        self.depth = depth
        self.ends_at = ends_at
        self.owner = owner
        self.qualified = qualified
        self.annotations = tuple(annotations)
        self.supertypes = tuple(supertypes)
        self.methods = tuple(methods)
        self.type_parameters = tuple(type_parameters)


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


def _body_starts_at(offsets, position, depth):
    """Where this declaration's body opens, or None when it has no body at all."""
    after = _first_brace_after(offsets, position)
    if after >= len(offsets) or offsets[after][1] != depth + 1:
        return None
    return offsets[after][0]


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
        body_ends_at = len(masked) if ends_at is None else ends_at
        body_starts_at = _body_starts_at(depths, start, depth)
        kind = _KINDS[match.group(1)]
        # Between the name and the body: `extends`, `implements`, and a record's own
        # components. Everything a caller learns about this type without opening it.
        header = masked[match.end(2):body_starts_at] if body_starts_at is not None else ""
        owner = open_types[0] if open_types else None
        declared = DeclaredType(
            name=match.group(2),
            kind=kind,
            depth=depth,
            ends_at=body_ends_at,
            owner=owner,
            qualified=".".join(
                [holder.name for holder in open_types[1:]] + [match.group(2)]
            ),
            annotations=_annotations_before(masked, start),
            supertypes=_supertypes_in(header),
            type_parameters=_type_parameters_in(header),
            # Only for a module — a type declared inside one is named on it rather than
            # scored, so reading its members would be work nothing asks for.
            methods=() if owner is not None
            else _methods_of(masked, kind, header, body_starts_at, body_ends_at),
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


# What a caller of this type has to learn: what it is marked with, what it is built on,
# and what it offers. Read here and weighed nowhere: this file says what the source says,
# and the rules that decide what any of it costs live in a file of their own.

_ANNOTATION = re.compile(r"@\s*([A-Za-z_$][\w$.]*)")

_INHERITANCE = re.compile(r"(?<![\w.$])(?:extends|implements|permits)(?![\w$])")

_MODIFIERS = frozenset(
    [
        "public", "protected", "private", "static", "final", "abstract", "default",
        "synchronized", "native", "strictfp", "transient", "volatile", "sealed", "non-sealed",
    ]
)

_ACCESS = ("public", "protected", "private")

# The keywords that make a member a type rather than a method. A nested record has a
# parameter list that reads exactly like one and is not one.
_TYPE_KEYWORD = re.compile(r"(?<![\w.$])(?:class|interface|enum|record)(?![\w$])")

_TRAILING_NAME = re.compile(r"([A-Za-z_$][\w$]*)\s*$")

_LEADING_WORD = re.compile(r"([A-Za-z_$][\w$-]*)\s")

_TRAILING_ANNOTATION = re.compile(r"@\s*([A-Za-z_$][\w$.]*)\s*$")

_TRAILING_WORD = re.compile(r"([A-Za-z_$][\w$-]*)\s*$")

_LEADING_TYPE_PARAMETER = re.compile(r"\s*([A-Za-z_$][\w$]*)")


def _annotations_before(masked, start):
    """The annotations written on this declaration, by simple name.

    Read by walking back over exactly what Java allows between an annotation and the
    keyword it marks — further annotations, the arguments they were given, and
    modifiers — and stopping at the first thing that is none of those, so a declaration
    never inherits the annotations of whatever was written above it.

    Looking back to the nearest `;` or brace instead is what this replaces, and it loses
    the whole annotation whenever an argument holds a brace of its own:
    `@SpringBootApplication(scanBasePackages = {"shop"})` is the ordinary way to write
    the entry point, and the search stopped inside it, so the entry point arrived as a
    module no rule had excluded and was scored like anything else.
    """
    found = []
    head = masked[:start]
    while head:
        head = head.rstrip()
        if head.endswith(")"):
            opened = _before_balanced(head)
            if opened is None:
                break
            head = head[:opened]
            continue
        marked = _TRAILING_ANNOTATION.search(head)
        if marked is not None:
            found.append(marked.group(1).split(".")[-1])
            head = head[:marked.start()]
            continue
        word = _TRAILING_WORD.search(head)
        if word is None or word.group(1) not in _MODIFIERS:
            break
        head = head[:word.start()]
    return tuple(reversed(found))


def _supertypes_in(header):
    """What this type is built on, by simple name: `extends X`, `implements Y, Z`."""
    found = []
    for clause in _INHERITANCE.split(_without_groups(header))[1:]:
        for written in clause.split(","):
            name = written.strip().split(".")[-1]
            if name:
                found.append(name)
    return tuple(found)


def _methods_of(masked, kind, header, body_starts_at, body_ends_at):
    """Every method this type offers, including the ones a record never writes down.

    A record's components compile to an accessor apiece, and a caller learns each of them
    the way they learn a method somebody typed. Leaving them out would say that a record
    carrying six values asks nothing of anybody, which is the opposite of what it does.
    """
    methods = [
        Method(name, "public", (), written) for written, name in _components_in(header)
    ] if kind == "record" else []

    for member in _member_headers(masked, body_starts_at, body_ends_at):
        method = _method_in(member, kind)
        if method is not None:
            methods.append(method)
    return tuple(methods)


def _components_in(header):
    """A record's components, as (type, name), from the header it declares them in."""
    opened = header.find("(")
    if opened < 0:
        return []
    return _declared_parameters(header[opened + 1:_after_balanced(header, opened) - 1])


def _member_headers(masked, body_starts_at, body_ends_at):
    """Each member of a type body, as the text before its own body or its semicolon.

    Every member's body is stepped over whole, so nothing written inside a method — a
    call that reads like a declaration, a local class, a lambda — is ever taken for part
    of the type's interface.
    """
    if body_starts_at is None:
        return []
    headers = []
    start = body_starts_at + 1
    position = start
    parens = 0
    while position < body_ends_at:
        character = masked[position]
        if character == "(":
            parens += 1
        elif character == ")":
            parens = max(0, parens - 1)
        elif parens == 0 and character == "{":
            headers.append(masked[start:position])
            position = _after_balanced(masked, position, "{", "}")
            start = position
            continue
        elif parens == 0 and character == ";":
            headers.append(masked[start:position])
            start = position + 1
        position += 1
    return headers


def _method_in(member, holder_kind):
    """The method this member declares, or None when the member is not one.

    Fields, initialisers, enum constants, nested types and constructors all arrive here
    and all answer None. The constructor is left out deliberately: it says how a module
    is built, which in this application is the framework's business rather than a
    caller's, and counting it would charge every module for being injectable.
    """
    text = _without_annotations(member)
    opened = text.find("(")
    if opened < 0:
        return None
    before = text[:opened]
    if "=" in before or _TYPE_KEYWORD.search(before):
        return None
    modifiers, rest = _modifiers_in(before)
    signature = _without_type_parameters(rest)
    name = _TRAILING_NAME.search(signature)
    if name is None:
        return None
    returns = _normalised(signature[:name.start()])
    if not returns:
        return None
    return Method(
        name.group(1),
        _visibility(modifiers, holder_kind),
        [written for written, _ in _declared_parameters(
            text[opened + 1:_after_balanced(text, opened) - 1]
        )],
        returns,
        _type_parameters_in(rest),
    )


def _declared_parameters(text):
    """The parameters written between two brackets, as (type, name)."""
    declared = []
    for part in _split_on_commas(text):
        _, rest = _modifiers_in(_without_annotations(part))
        name = _TRAILING_NAME.search(rest)
        if name is None:
            continue
        # `String... names` hands over a String: the dots say how many, not what.
        written = _normalised(rest[:name.start()]).rstrip(". ")
        if written:
            declared.append((written, name.group(1)))
    return declared


def _modifiers_in(before):
    """The modifiers this declaration opens with, and everything after the last of them."""
    found = []
    rest = before.lstrip()
    while True:
        word = _LEADING_WORD.match(rest)
        if word is None or word.group(1) not in _MODIFIERS:
            return found, rest
        found.append(word.group(1))
        rest = rest[word.end():].lstrip()


def _visibility(modifiers, holder_kind):
    """How far outside this type the member can be reached from.

    An interface's members carry no access modifier and are public anyway, which is the
    one place where saying nothing means the widest thing rather than the narrowest.
    """
    for access in _ACCESS:
        if access in modifiers:
            return access
    return "public" if holder_kind in ("interface", "annotation") else "package-private"


def _without_type_parameters(rest):
    """`<T extends Comparable<T>> T largest` is `T largest`: the bounds are not the type."""
    rest = rest.lstrip()
    return rest[_after_balanced(rest, 0, "<", ">"):] if rest.startswith("<") else rest


def _type_parameters_in(text):
    """The names a `<...>` at the front of this text introduces: `<T>`, `<K, V>`.

    A type variable is a hole rather than a type: `<T> T first(List<T> of)` asks its
    caller for a type they already hold, and `T` is a letter standing in for it, not
    something anybody goes and reads.

    Only the names are taken, not their bounds: what an interface costs is counted over
    parameters and returns, and `<T extends Receipt>` is neither. A caller does have to
    honour that bound, which makes this one more thing the score does not measure — the
    page's business to admit rather than this file's to guess at.
    """
    text = text.lstrip()
    if not text.startswith("<"):
        return ()
    inside = text[1:_after_balanced(text, 0, "<", ">") - 1]
    names = []
    for part in _split_on_commas(inside):
        found = _LEADING_TYPE_PARAMETER.match(_without_annotations(part))
        if found is not None:
            names.append(found.group(1))
    return tuple(names)


def _without_annotations(text):
    """The same text with every annotation, and everything it was given, taken out.

    `@interface` is left where it is: it opens a declaration rather than marking one.
    """
    out = []
    position = 0
    while position < len(text):
        if text[position] == "@":
            found = _ANNOTATION.match(text, position)
            if found is not None and found.group(1) != "interface":
                position = found.end()
                while position < len(text) and text[position] in " \t\r\n":
                    position += 1
                if position < len(text) and text[position] == "(":
                    position = _after_balanced(text, position)
                continue
        out.append(text[position])
        position += 1
    return "".join(out)


def _without_groups(text):
    """The same text with everything inside `<...>` and `(...)` taken out.

    A supertype's type arguments are not supertypes, a record's components are not either,
    and a comma inside one of those is not the comma between two of them.
    """
    out = []
    angles = parens = 0
    for character in text:
        if character == "<":
            angles += 1
        elif character == ">":
            angles = max(0, angles - 1)
        elif character == "(":
            parens += 1
        elif character == ")":
            parens = max(0, parens - 1)
        elif angles == 0 and parens == 0:
            out.append(character)
    return "".join(out)


def _split_on_commas(text):
    """Split on the commas between things, never on one inside a type or an annotation."""
    parts = []
    depth = 0
    start = 0
    for position, character in enumerate(text):
        if character in "<([":
            depth += 1
        elif character in ">)]":
            depth = max(0, depth - 1)
        elif character == "," and depth == 0:
            parts.append(text[start:position])
            start = position + 1
    parts.append(text[start:])
    return parts


def _after_balanced(text, position, opening="(", closing=")"):
    """Just past the bracket that closes the one at `position`, or the end of the text."""
    depth = 0
    while position < len(text):
        if text[position] == opening:
            depth += 1
        elif text[position] == closing:
            depth -= 1
            if depth == 0:
                return position + 1
        position += 1
    return len(text)


def _before_balanced(text, opening="(", closing=")"):
    """Where the bracket that closes this text opens, or None when nothing opens it.

    The mirror of `_after_balanced`, for reading backwards: the only way to find the front
    of an annotation's argument list from the keyword it sits in front of.
    """
    depth = 0
    position = len(text) - 1
    while position >= 0:
        if text[position] == closing:
            depth += 1
        elif text[position] == opening:
            depth -= 1
            if depth == 0:
                return position
        position -= 1
    return None


def _normalised(written):
    """A type as the graph carries it: one space where Java needs one, none where it does not."""
    tidy = re.sub(r"\s+", " ", written).strip()
    for bracket in ("<", ">", ",", "[", "]"):
        tidy = tidy.replace(" " + bracket, bracket)
    return tidy.replace("< ", "<").replace("[ ", "[")
