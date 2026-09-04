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
  would otherwise lose with nobody noticing;
- a type declaration whose body cannot be found, since every kind of type Java declares
  has one, and reading a type with no body would price its whole interface at zero;
- a parameter, or the type a member hands back, that cannot be read as one — the two
  places where a shape this parser does not understand would leave an interface quietly
  cheaper than the source makes it.

The last three are the ones a future edit is most likely to reach: a legal construct the
patterns have not met yet arrives as a named failure on the page, at a line, rather than
as a module that looks shallow.

One decision cannot fail loudly, and it is where every fault found on this branch got in:
deciding that a member which reads like a method is not one. A field, a constructor and a
nested record all read like one and legitimately are not, so there is nothing to fail on —
and a method wrongly declined leaves an interface quietly cheaper than the source makes
it. So it is logged instead: `grep "member not read as a method"` at DEBUG lists every
member declined, its line, and why, and that list is short enough to read.
"""

import logging
import re

log = logging.getLogger("module_depth_map.javasource")

_PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)

# An annotation, wherever one can be written: on a declaration, on a member, on a
# parameter, or inside another annotation's arguments.
_ANNOTATION = re.compile(r"@\s*([A-Za-z_$][\w$.]*)")

# A declaration is its keyword followed by the name it declares, wherever it sits: after
# annotations on the same line, inside another type's braces, or after any modifiers.
# Anchoring to the start of a line instead would drop `@Deprecated public class Foo` and
# `class Outer { class Inner {} }` without a word, which is the one failure this file
# exists to make impossible. What the anchor used to buy is bought by the lookbehind
# instead: `Thing.class` is a class literal, not a declaration.
#
# "Followed by the name it declares" is what also tells a nested record from a method
# named `record`, which is why `_method_in` asks this pattern rather than a second one
# looking for the keyword on its own.
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

# Every kind of module this parser can report, for a rule outside this file to be checked
# against: a rule written about a kind that is not one of these could never match, and a
# condition that can never hold is a rule nobody can tell from a rule that never fired.
KINDS = tuple(sorted(set(_KINDS.values())))


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


def masked_source(text):
    """The same text with everything that must not be read as source blanked out.

    Three things are blanked, and offsets are preserved through all of them: the contents
    of comments, the contents of literals, and the braces inside an annotation's
    arguments. Brace counting and declaration matching both run over the result rather
    than over the source, so a brace in a string, the word "class" in a comment, and the
    `{` of `@Values({"a"})` are all unable to move them.

    That last one is a brace Java does not open a body with, and every scan in this file
    that looks for "the first brace after the name" would otherwise take it for one:
    `record R(@Values({"a"}) String s)` would have its body read as the annotation's array
    argument, and the whole interface would arrive as nothing at all — the one failure
    this file exists not to have. Blanking it here fixes every one of those scans at once
    rather than each of them separately.

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
    # One entry per annotation argument list still open, holding how deeply the brackets
    # inside it nest. An annotation's argument can be another annotation, so the answer
    # is a stack rather than a flag.
    annotation_arguments = []
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
        elif ch == "@" and _marks_a_declaration(text, i):
            marked = _ANNOTATION.match(text, i)
            out.append(text[i:marked.end()])
            i = marked.end()
            while i < n and text[i] in " \t\r\n":
                out.append(text[i])
                i += 1
            if i < n and text[i] == "(":
                annotation_arguments.append(0)
                out.append("(")
                i += 1
        elif annotation_arguments:
            if ch == "(":
                annotation_arguments[-1] += 1
            elif ch == ")":
                if annotation_arguments[-1]:
                    annotation_arguments[-1] -= 1
                else:
                    annotation_arguments.pop()
            out.append(" " if ch in "{}" else ch)
            i += 1
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def _marks_a_declaration(text, position):
    """Whether an annotation is written at this offset, `@interface` excepted.

    `@interface` opens a declaration rather than marking one, and its body is a body: the
    braces inside it are the ones every scan here is looking for.
    """
    marked = _ANNOTATION.match(text, position)
    return marked is not None and marked.group(1) != "interface"


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
    """Where the body opened after `position` closes again, or None if it never does."""
    for offset, after in offsets[_first_brace_after(offsets, position):]:
        if after == depth:
            return offset
    return None


def _body_starts_at(offsets, position, depth):
    """Where this declaration's body opens, or None when no brace after it opens one."""
    after = _first_brace_after(offsets, position)
    if after >= len(offsets) or offsets[after][1] != depth + 1:
        return None
    return offsets[after][0]


def parse(text, path):
    """What this Java file contains, or a ParseFailure naming why it could not be read."""
    masked = masked_source(text)
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
        # Every one of the five kinds of type Java declares has a body, so a declaration
        # whose body this parser cannot find is a declaration it is no longer reading.
        # Carrying on with an empty header would read the type's whole interface as
        # nothing and price it at zero, which is a finding-shaped answer to a parse
        # failure — exactly what this file refuses to hand anybody.
        line = _line_of(masked, start)
        body_starts_at = _body_starts_at(depths, start, depth)
        if body_starts_at is None:
            raise ParseFailure(
                "a type declaration whose body this parser cannot find: %s on line %d"
                % (match.group(2), line)
            )
        body_ends_at = _body_ends_at(depths, start, depth)
        if body_ends_at is None:
            raise ParseFailure(
                "a type declaration whose body never closes: %s on line %d"
                % (match.group(2), line)
            )
        kind = _KINDS[match.group(1)]
        # Between the name and the body: `extends`, `implements`, and a record's own
        # components. Everything a caller learns about this type without opening it.
        header = masked[match.end(2):body_starts_at]
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
            else _methods_of(masked, kind, header, body_starts_at, body_ends_at, line),
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

_INHERITANCE = re.compile(r"(?<![\w.$])(?:extends|implements|permits)(?![\w$])")

_MODIFIERS = frozenset(
    [
        "public", "protected", "private", "static", "final", "abstract", "default",
        "synchronized", "native", "strictfp", "transient", "volatile", "sealed", "non-sealed",
    ]
)

_ACCESS = ("public", "protected", "private")

_TRAILING_NAME = re.compile(r"([A-Za-z_$][\w$]*)\s*$")

# The `[]` pairs Java lets a method write after its parameter list rather than on its
# return type: `int f()[]` hands back the same array `int[] f()` does.
_LEADING_BRACKETS = re.compile(r"\s*(?:\[\s*\]\s*)+")

# How a type is spelled, once annotations are off it and its spacing is normalised: a
# name, and then the shapes a name can be carried in. Nothing else belongs where a type
# belongs, so anything else there is a member this parser has misread.
_A_TYPE = re.compile(r"[A-Za-z_$][\w$.<>,\[\] ?]*$")

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

    Looking back to the nearest `;` or brace instead, and then reading the annotations in
    what that leaves, would say the wrong thing rather than nothing: an annotation's
    argument can be another annotation, and `@JsonSubTypes({@Type(A.class)})` would then
    report `Type` as marking the declaration. A rule in the configuration file that names
    `Type` would exclude a module nobody wrote it on, and the graph would name a rule for
    an exclusion the source does not support. Stepping over the arguments is what keeps
    this list to the annotations the declaration actually carries.
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


def _methods_of(masked, kind, header, body_starts_at, body_ends_at, line):
    """Every method this type offers, including the ones a record never writes down.

    A record's components compile to an accessor apiece, and a caller learns each of them
    the way they learn a method somebody typed. Leaving them out would say that a record
    carrying six values asks nothing of anybody, which is the opposite of what it does.

    `line` is where the declaration was written, so that a member this parser cannot read
    is named by a line a reader can go and open.
    """
    methods = [
        # `record R(int... more)` accepts any number of ints and hands the caller back the
        # array they arrived in, so the accessor's return is the array rather than the
        # element: the dots say how many on the way in, and nothing at all on the way out.
        Method(name, "public", (), written + ("[]" if dots else ""))
        for written, name, dots in _components_in(header, line)
    ] if kind == "record" else []

    for member, at in _member_headers(masked, kind, body_starts_at, body_ends_at):
        method = _method_in(member, kind, _line_of(masked, at))
        if method is not None:
            methods.append(method)
    return tuple(methods)


def _components_in(header, line):
    """A record's components, as (type, name, varargs), from the header declaring them."""
    opened = header.find("(")
    if opened < 0:
        return []
    return _declared_parameters(
        header[opened + 1:_after_balanced(header, opened) - 1], line
    )


def _member_headers(masked, kind, body_starts_at, body_ends_at):
    """Each member of a type body, as (the text before its body or semicolon, its offset).

    Every member's body is stepped over whole, so nothing written inside a method — a
    call that reads like a declaration, a local class, a lambda — is ever taken for part
    of the type's interface.

    An enum's constants are not members, and are skipped as the block they are. They are
    written in the same place as members, they may carry arguments and a body of their
    own, and reading one as a member is how `B("y")` became a package-private method
    whose return type was the comma in front of it.
    """
    headers = []
    start = body_starts_at + 1
    if kind == "enum":
        start = _after_enum_constants(masked, start, body_ends_at)
    position = start
    parens = 0
    while position < body_ends_at:
        character = masked[position]
        if character == "(":
            parens += 1
        elif character == ")":
            parens = max(0, parens - 1)
        elif parens == 0 and character == "{":
            headers.append(_header(masked, start, position))
            position = _after_balanced(masked, position, "{", "}")
            start = position
            continue
        elif parens == 0 and character == ";":
            headers.append(_header(masked, start, position))
            start = position + 1
        position += 1
    return headers


def _header(masked, start, position):
    """One member's text, and the offset of the first thing written in it.

    The offset is where the member's own first word is rather than where the member
    before it ended, because it is only ever used to name a line to a reader, and the
    line after the previous member's semicolon is not the line they need to open.
    """
    while start < position and masked[start] in " \t\r\n":
        start += 1
    return masked[start:position], start


def _after_enum_constants(masked, start, body_ends_at):
    """Just past the semicolon that ends an enum's constants, or the end of its body.

    An enum with nothing but constants writes no semicolon at all, and then every member
    there is to read is a constant.
    """
    position = start
    parens = 0
    while position < body_ends_at:
        character = masked[position]
        if character == "(":
            parens += 1
        elif character == ")":
            parens = max(0, parens - 1)
        elif parens == 0 and character == "{":
            position = _after_balanced(masked, position, "{", "}")
            continue
        elif parens == 0 and character == ";":
            return position + 1
        position += 1
    return body_ends_at


def _method_in(member, holder_kind, line):
    """The method this member declares, or None when the member is not one.

    Fields, initialisers, enum constants, nested types and constructors all arrive here
    and all answer None. The constructor is left out deliberately: it says how a module
    is built, which in this application is the framework's business rather than a
    caller's, and counting it would charge every module for being injectable.

    A member that reads as a method but hands back something that is not a type spelling
    fails the file by name. Answering None there would be the same silence as scoring an
    unreadable file at zero, one member at a time: the shape this parser cannot read
    would leave no trace, and the next one would be found by a reader rather than by the
    tool.

    The one member with a parameter list that is not a method is a nested record, and it
    is told apart by the keyword *and the name it declares* — `record Row(...)` — rather
    than by the keyword alone. `record` is a contextual keyword and a legal method name,
    so looking only for the word dropped `void record(Deposit deposit)` from the interface
    without a word said: a method this application could plausibly write, gone from the
    page, and the module cheaper than the source makes it.

    Every such decision leaves a line, because it is the one the page cannot show being
    wrong: a member declined here is a method missing from an interface, and a missing
    method reads as a module that asks less of its caller. `_declined` says which member
    and why.
    """
    text = _without_annotations(member)
    opened = text.find("(")
    if opened < 0:
        return None
    before = text[:opened]
    if "=" in before:
        return _declined(member, line, "a value is assigned to it, so it is a field")
    if _TYPE.search(before):
        return _declined(member, line, "a declaration keyword names a type in it")
    modifiers, rest = _modifiers_in(before)
    signature = _without_type_parameters(rest)
    name = _TRAILING_NAME.search(signature)
    if name is None:
        return _declined(member, line, "nothing before the brackets reads as a name")
    closed = _after_balanced(text, opened)
    returns = _normalised(signature[:name.start()])
    if not returns:
        return _declined(member, line, "it hands nothing back, so it is a constructor")
    # `int f()[]` declares the array after the parameters rather than on the type, the way
    # `int xs[]` declares one after the name. Both spellings hand a caller the same array,
    # and dropping the brackets here would hand them back the element type instead.
    returns += _brackets_after(text[closed:])
    if not _reads_as_a_type(returns):
        raise ParseFailure(
            "a member this parser cannot read: %s on line %d hands back %r, which is not a "
            "type" % (name.group(1), line, returns)
        )
    return Method(
        name.group(1),
        _visibility(modifiers, holder_kind),
        [written for written, _, _ in _declared_parameters(
            text[opened + 1:closed - 1], line
        )],
        returns,
        _type_parameters_in(rest),
    )


def _declined(member, line, reason):
    """Say that a member with a parameter list is not a method, and why, and answer None.

    This is the one decision in the file that cannot fail loudly. A member that reads
    like a method and is not one might be a field, a constructor or a nested record —
    or it might be a method this parser has just lost, which is how an interface arrives
    quietly cheaper than the source makes it, and how both shapes found on this branch
    got in. Nothing here can tell the difference, so it leaves a line instead: grep
    "member not read as a method" at DEBUG and every one of these is there to be argued
    with, at a line a reader can go and open.

    Only members with a parameter list reach here. A field or an initialiser has none, is
    the commonest thing in a body, and was never a candidate for being a method.
    """
    log.debug(
        "member not read as a method line=%d reason=%s member=%s",
        line,
        reason,
        _one_line(member),
    )
    return None


def _one_line(member):
    """A member's own text on one line, short enough to sit in a log line."""
    tidy = " ".join(member.split())
    return tidy if len(tidy) <= 120 else tidy[:117] + "..."


def _brackets_after(text):
    """The `[]` pairs written at the front of this text, as one string."""
    found = _LEADING_BRACKETS.match(text)
    return "[]" * (found.group(0).count("[") if found else 0)


def _declared_parameters(text, line):
    """The parameters written between two brackets, as (type, name, varargs).

    A parameter this parser cannot read fails the file rather than being dropped. Two
    spellings of one parameter list — `take(int xs[])` and `take(int[] xs)` — used to cost
    a caller different amounts because the first was discarded here without a word, which
    is the parser losing part of an interface while the score still added up.

    `varargs` says the parameter was written `int...` rather than `int`. It makes no
    difference to what a caller hands over one at a time, and all the difference to a
    record's accessor for it, which hands the array back.
    """
    declared = []
    for part in _split_on_commas(text):
        if not part.strip():
            continue
        _, rest = _modifiers_in(_without_annotations(part))
        # `int xs[]` declares the array on the name rather than on the type. The brackets
        # belong to what the caller has to hand over either way.
        rest, brackets = _brackets_after_the_name(rest)
        name = _TRAILING_NAME.search(rest)
        # `String... names` hands over a String: the dots say how many, not what.
        spelled = _normalised(rest[:name.start()] if name else "")
        written = spelled.rstrip(". ")
        if name is None or not written or not _reads_as_a_type(written + brackets):
            raise ParseFailure(
                "a parameter this parser cannot read: %r on line %d" % (part.strip(), line)
            )
        if name.group(1) == "this":
            # `void f(Foo this, int x)` names the receiver rather than a parameter: a
            # caller passes nothing for it, so charging them for it would be an invented
            # parameter on every method written that way.
            log.debug("receiver parameter read on line %d, which a caller never passes", line)
            continue
        declared.append((written + brackets, name.group(1), spelled.endswith("...")))
    return declared


def _brackets_after_the_name(rest):
    """The text with any trailing `[]` pairs taken off it, and the pairs, as one string."""
    brackets = ""
    while True:
        trimmed = rest.rstrip()
        if not trimmed.endswith("]"):
            return rest, brackets
        opened = _before_balanced(trimmed, "[", "]")
        if opened is None:
            return rest, brackets
        rest = trimmed[:opened]
        brackets += "[]"


def _reads_as_a_type(written):
    """Whether this is spelled the way a Java type is, brackets balanced and all.

    Not "names a type this parser could resolve" — it reads one file at a time and cannot
    — but "could be one at all". Anything else in the place a type belongs means the
    scan has lost the shape of what it is reading, and the file is failed rather than
    scored on the strength of it.

    Once the spacing is normalised, everything a type can be spelled with is a name, a
    bracket, or one of the words inside `<? extends Receipt>`. So a type is a name whose
    brackets balance and which holds a space, a comma or a `?` only inside a `<...>`: a
    space anywhere else is two words, and two words in the place of one type means a
    member header was cut in the wrong place.
    """
    if _A_TYPE.match(written) is None:
        return False
    angles = brackets = 0
    for character in written:
        if character == "<":
            angles += 1
        elif character == ">":
            angles -= 1
        elif character == "[":
            brackets += 1
        elif character == "]":
            brackets -= 1
        elif character in " ,?" and angles == 0:
            return False
        if angles < 0 or brackets < 0:
            return False
    return angles == 0 and brackets == 0


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
    """A type as the graph carries it: one space where Java needs one, none where it does not.

    Every bracket is closed up against what it holds and every comma between two type
    arguments is followed by exactly one space, whether or not the source wrote one. The
    comma is the one that matters: while the space after it was carried through, one
    document held both `Map<String, Long>` and `Map<String,Long>`, which reads as two
    types a caller has to learn where there is one.
    """
    tidy = re.sub(r"\s+", " ", written).strip()
    for bracket in ("<", ">", ",", "[", "]"):
        tidy = tidy.replace(" " + bracket, bracket)
    tidy = tidy.replace("< ", "<").replace("[ ", "[")
    return re.sub(r",\s*", ", ", tidy).strip()
