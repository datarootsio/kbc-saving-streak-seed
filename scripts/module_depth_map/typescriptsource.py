"""Reading one TypeScript file well enough to name the module it is.

The unit is the file. That is not a shortcut around the class grain the Java side reads
at — it is where a TypeScript interface actually lives. A caller of this frontend writes
`import { fetchDeposits } from './api'`, and what they get is whatever that file exports:
the file is the thing with an interface and an implementation, so the file is the module,
and the card says `file` where a Java card says `class` so that a reader can see which
grain they are looking at.

Everything else is deliberately the same as the Java side, because the page's whole claim
is that one measure was applied to both halves of the application. What a caller must
learn is what the file exports; what the file coordinates is what it imports and uses;
depth is the second over the first. Nothing here scores a module by how long it is, and
the largest file in this repository is the reason that matters: it presents one export
and coordinates one thing, which is a shape a reader can see and would never guess from
its length.

Parsing is targeted pattern matching over the source rather than a full grammar, exactly
as it is for Java, and the same rule follows from that: a file this cannot read is failed
by name rather than scored as empty, because a file read as empty is indistinguishable
from a file that presents nothing — which is the one finding this page exists to make.
So the file is failed whenever this reading can tell it has stopped reading what `tsc`
would read:

- a block comment, a template literal or a regular expression that is never closed, which
  would otherwise blank out the rest of the file and take every declaration in it along;
- braces that do not balance, in either direction;
- an `export` this reading cannot make sense of — the one reserved word that must open
  something readable, since it is the whole of what a caller can reach;
- a parameter of an exported function that cannot be read as one, or a return type that
  cannot be told from the body that follows it. These are the two places where a misread
  shape would leave an interface *cheaper* than the source makes it, and a cheap interface
  over a wide fan is what this page calls deep.

Two readings are looser than the Java side's and are named rather than left to be found,
because JSX is prose and source in the same file and this reading does not tell them
apart:

- **JSX text is read as source.** `<p>Deposits</p>` is scanned for calls like anything
  else. It can only *add* a name to what a module reaches, and only when that name is one
  the file also imports, so the risk is a fan line to a card the source calls nothing on.
  Masking it instead would need a JSX parser, and a JSX parser that got it wrong would
  blank real code — the failure that costs a module rather than adding to one.
- **A quote in prose is not a string.** A `'` or `"` opens a string only when a matching
  one follows it on the same line, because JavaScript strings do not span lines and JSX
  prose is full of apostrophes. The rule cannot be wrong about legal source — a real
  string always closes on its line — and without it a single `account's` in prose blanked
  out the rest of the file.
"""

import logging
import posixpath
import re

from .javasource import (
    CallSite,
    DeclaredType,
    Field,
    Method,
    ParsedFile,
    ParseFailure,
    after_balanced,
    in_evaluation_order,
)

log = logging.getLogger("module_depth_map.typescriptsource")

# What this reading is called in the graph, and which files it is the reading of.
NAME = "typescript"
SUFFIXES = (".ts", ".tsx")

# What a module of this language is, and why, in one sentence a page can print — the
# reading's own answer rather than the renderer's, for the reason the Java side gives.
GRAIN = (
    "A TypeScript module is a file, because that is what an import names and so is the "
    "whole of what a caller of one gets."
)

# What a module is here, and the only kind this reading reports. One kind rather than
# several because there is only one thing to be at file grain: a file with an interface
# and an implementation. The word is on every card next to the Java cards' `class` and
# `record`, so the grain each module was read at is visible rather than inferred.
KINDS = ("file",)

# What a fan line says when a module was reached by calling something the file imported
# from it. TypeScript has no package scope at all: an import is the *only* way one file
# names another, which is why the sentence differs from Java's.
IMPORTED_EVIDENCE = "calls %s, which this file imports from it"

# What is written where a type is not. TypeScript infers a type the source did not write,
# and this reading resolves nothing — so a parameter written `(a = 1)` and a function
# written `function App() {` each cross the seam with a type this tool cannot name. Saying
# so is the honest answer: the parameter still costs a caller a parameter, and no type is
# charged for, because none was read. Writing `void` there instead would be a claim about
# the source, and `unknown` would be a claim about the type.
INFERRED = "(inferred)"

# What TypeScript writes where a return type goes when a function hands nothing back.
NOTHING_RETURNED = "void"

# The extensions an import specifier may be written with and this reading takes off, so
# that `./api` and `./api.ts` are the one module they are. A specifier ending in anything
# else — `./index.css` — keeps it, and then names no module here, which is the answer:
# a stylesheet has no interface for anybody to learn.
_MODULE_SUFFIXES = (".ts", ".tsx", ".js", ".jsx", ".mts", ".cts")

# `import App from './App'`, `import { a, b as c } from './api'`, `import * as api from
# './api'`, `import type { T } from './api'`. Read against the *masked* text, where the
# specifier's characters are blanked and its quotes are not, so the offsets of the
# specifier are known and the original text can be sliced for it. Reading it out of the
# raw text instead would read an `import` written inside a comment or a string.
_IMPORT_FROM = re.compile(
    r"(?<![\w$.])import[ \t\r\n]+(?:type[ \t\r\n]+)?([^;]*?)[ \t\r\n]+from[ \t\r\n]+"
    r"(['\"])([^'\"\n]*)\2"
)

# `import './index.css'`: a file brought in for what loading it does, naming nothing.
_IMPORT_ONLY = re.compile(r"(?<![\w$.])import[ \t\r\n]+(['\"])([^'\"\n]*)\1")

# Every `import` the two patterns above have to account for between them. `import(...)`
# is a dynamic import — an expression, not a statement naming anything at read time — and
# `import.meta` is not an import at all, so both are held out by what follows the word.
_AN_IMPORT = re.compile(r"(?<![\w$.])import(?![\w$])(?![ \t\r\n]*[.(])")

# `export`, which is the whole of what a caller of this module can reach. It is a reserved
# word: outside a string or a comment it can only open a declaration, so one this reading
# cannot make sense of is TypeScript it has stopped reading, and the file is failed by name.
_AN_EXPORT = re.compile(r"(?<![\w$.])export(?![\w$])")

# Every way TypeScript introduces a name. Read at every depth for `declares` — a name the
# body declares is not a call to an import that shares its spelling — and at depth 0 for
# what the module itself is made of.
_DECLARES = re.compile(
    r"(?<![\w$.])(function|class|type|interface|enum|const|let|var)[ \t\r\n]+"
    r"(?:\*[ \t\r\n]*)?([A-Za-z_$][\w$]*)"
)

# The type arguments a call may be written with, between the name and the brackets:
# `hold<Receipt>()`. Optional, and part of both call patterns rather than a second pair of
# them, because a call spelled this way is the same call — read without it, `useState<T>()`
# put no name in front of any bracket and the module it was imported from went unreached.
# TypeScript writes them after the name where Java writes them before it, which is why the
# two sides deal with them in different places. Brackets, braces and semicolons are held
# out of the group so that it cannot span a `<` and a `>` that are two comparisons.
_TYPE_ARGUMENTS = r"(?:[ \t\r\n]*<[^<>;{}()]*>)?"

# A call written against something the source names: `response.json()`, `api.fetch(...)`.
_A_RECEIVER_CALL = re.compile(
    r"(?<![\w$.])([A-Za-z_$][\w$]*)[ \t\r\n]*\.[ \t\r\n]*([A-Za-z_$][\w$]*)"
    + _TYPE_ARGUMENTS + r"[ \t\r\n]*\("
)

# A name with a call's brackets after it and nothing in front of the name, which is how a
# name the file imported is called: `fetchDeposits(id)`.
_A_CALL = re.compile(
    r"(?<![\w$.])([A-Za-z_$][\w$]*)" + _TYPE_ARGUMENTS + r"[ \t\r\n]*\("
)

# `new SignInFailed(...)`: building a collaborator is coordinating it.
_CONSTRUCTED = re.compile(r"(?<![\w$.])new[ \t\r\n]+([A-Za-z_$][\w$.]*)")

# `<App />`, `<StrictMode>`: a JSX element naming a component is that component being
# used, which is what it compiles to — `createElement(App)` — and is coordination the
# same way `new` is. Only a name starting with a capital can be one: JSX reads a lowercase
# tag as an HTML element and never as a name in scope, which is React's own rule and the
# whole of what tells `<div>` from `<Deposits>`.
_A_JSX_ELEMENT = re.compile(r"<[ \t\r\n]*([A-Z][\w$]*)(?=[ \t\r\n/>])")

# The name a `<` that opens a type argument list stands behind.
_A_NAME_BEHIND_IT = re.compile(r"[A-Za-z_$][\w$]*$")

# `throw new SignInFailed(...)`: the refusals the implementation raises, by name.
_THROWN = re.compile(r"(?<![\w$.])throw[ \t\r\n]+new[ \t\r\n]+([A-Za-z_$][\w$.]*)")

# Every `throw` in the body, whatever it goes on to throw, so that one this reading cannot
# name is counted rather than passed over. `throw refused` and `throw somethingElse.of()`
# each need a type this tool never resolves, and a body known to have been read short is a
# body nothing should say "never raises that" about.
_A_THROW = re.compile(r"(?<![\w$.])throw(?![\w$])")

# A refusal the documentation promises, in either of the two spellings a JSDoc block uses:
# `@throws SignInFailed` and `@throws {SignInFailed}`. It has to stand where a block tag
# stands — at the start of a line, or after one of the asterisks a block is written down
# the side with — or the `{@link Error}` an explanation is built out of would be read as a
# second promise.
_DOCUMENTED_REFUSAL = re.compile(
    r"(?:^|[\n*])[ \t]*@(?:throws|exception)[ \t]+\{?([A-Za-z_$][\w$.]*)\}?", re.M
)

# The words JavaScript and TypeScript write brackets after that are not calls. `of` is
# deliberately not among them: it is a contextual keyword in `for (const each of list)`
# and a perfectly ordinary method name everywhere else — this repository's own
# `AmountOfMoney.of` and `prices.of` are two — and holding it out took every call to one
# of them off every fan.
_NOT_A_CALL = frozenset(
    ["as", "await", "case", "catch", "class", "delete", "do", "else", "for", "function",
     "if", "import", "in", "infer", "instanceof", "keyof", "new", "return",
     "satisfies", "super", "switch", "this", "throw", "typeof", "void", "while", "with",
     "yield"]
)

# The words a written type can hold that do not name one. `void` is among them, and for
# the reason the Java side answers a bare `void` with nothing: it is TypeScript's word for
# "no value here" rather than a type anybody goes and learns, and that is as true inside
# `Promise<void>` as it is on its own. It is not on the configuration's list of types every
# caller already knows, because putting it there would say it is a type and that a reader
# could argue it off the list — and if they did, a caller of a function handing nothing
# back would be charged for a type they never meet.
_NOT_A_TYPE_NAME = frozenset(
    ["as", "asserts", "extends", "import", "in", "infer", "is", "keyof", "new", "out",
     "readonly", "satisfies", "typeof", "unique", NOTHING_RETURNED]
)

# `this.addressRejected` reaches exactly what `addressRejected` reaches: the prefix is one
# a writer may put on an access to their own object and nothing more. Taken off before a
# receiver is read, so that `this.name` is not a collaborator called `this`.
_THROUGH_THIS = re.compile(r"(?<![\w$.])this[ \t\r\n]*\.[ \t\r\n]*")

# What a `/` can follow and still open a regular expression. `<` and `>` are deliberately
# not among them: `</div>` writes a `<` and then a `/`, and read as a regex it blanked the
# JSX tags after it on the same line. Nothing writes `a < /re/` on purpose.
_AFTER_WHICH_A_REGEX_CAN_START = frozenset("(,=:[!&|?;{}+-*%~^")
_BEFORE_A_REGEX = frozenset(
    ["await", "case", "delete", "do", "else", "in", "instanceof", "new", "of", "return",
     "throw", "typeof", "void", "yield"]
)

# What may stand between a JSDoc block and the declaration it documents: the keywords an
# export is written with, and nothing else. Anything with punctuation in it is a statement
# standing in between, and a block above that documents that one.
_NOTHING_BUT_WORDS = re.compile(r"[\s\w$]*")

# Where a declaration is written that this reading has no account of. Named rather than
# guessed at, because `declare` and `namespace` describe types that live somewhere else
# entirely and reading them as this module's own interface would be a claim about a file
# nobody pointed the tool at.
_NOT_READ_HERE = ("declare", "namespace", "module", "abstract")

# The words that can only open a declaration, and therefore cannot be part of a type. A
# return type ends where one of them starts, which is the whole of how an overload
# signature that ends at a newline instead of a `;` is told from a type that carries on.
# `type` and `interface` are deliberately not among them: both are contextual keywords in
# TypeScript, so a type really can be called either. Neither is `import`, which a type
# really can hold — `import('./api').Customer` is how a type is reached without importing
# the module — and cutting a type there would charge a caller nothing for a return they
# have to learn whole.
_OPENS_A_DECLARATION = re.compile(
    r"(?<![\w$.])(?:export|function|class|const|let|var|declare|enum|async)(?![\w$])"
)

# What tells `export default App` from `export default connect(App)`: whether the name is
# the whole of what was exported or the front of an expression. The first is read as the
# declaration it points at, and the second is a value this reading prices nowhere — so
# reading it as a declaration of that name would price `connect` and call the answer
# `App`.
_AN_EXPRESSION_CARRIES_ON_WITH = ("(", ".", "[", "?", "+", "-", "*", "/", "%", "&", "|", "^")


def module_id(package, name):
    """The id a module of this name in this place is known by: the path it sits at.

    A TypeScript module is a file, so its identity is where the file is — the source root
    it was found under, then the directories under that, then its own name without the
    extension. `frontend/src/App` is what `import './App'` written next door resolves to,
    which is the point: the id a caller writes and the id the graph holds are the same
    string, so a fan line is checkable against an import statement.
    """
    return package + "/" + name if package else name


def followed(name, package, imports):
    """Every module id a name written in this body could mean, or none at all.

    Nothing like Java's answer, and deliberately: TypeScript has no package scope. A name
    means what an import bound it to, or it is declared in this file, or it is a global —
    `document`, `fetch`, `Error` — and none of the last three is a module here. So an
    import that bound this name answers the question outright and there is no fallback
    behind it, where Java falls back to the file's own package.

    Two candidates come back for one import, and only for a specifier that could be a
    directory: `./components` means `components/index` when there is no `components.ts`,
    and which of the two it is depends on the files on disk rather than on anything
    written in this one. Whoever asks tries them against the modules the graph holds.

    A name with a slash in it is a module written out in full, which is TypeScript's
    equivalent of Java's `new other.Receipt()` and is answered the same way: that module
    and no other. It is how a resolved import specifier is handed back for a second look
    — `frontend/src/api`, once the static-import reading has decided that a bare call went
    to something imported — and it cannot collide with a name written in a body, because
    a slash inside an identifier is not something JavaScript spells.
    """
    for imported in imports:
        if imported.local == name:
            return [imported.type, imported.type + "/index"]
    if "/" in name:
        return [name, name + "/index"]
    return []


def crosses_the_seam(written):
    """Every type a caller meets in this parameter or return, by the simple name of each.

    Three things are answered with nothing at all. `void` is one, for the reason the Java
    side gives: a function that hands nothing back puts nothing across the seam on the way
    out. A type the source did not write is the second — this reading resolves nothing, so
    it can say that a caller meets *a* type there and not which one, and naming a type it
    did not read would be worse than counting none. A literal type is the third: `'nl-BE'`
    is a value, and its characters are blanked before this ever sees them.

    A property's name is not a type, and this is where that gets decided: in
    `{ id: number }` and `(signal: AbortSignal) => void` the word in front of a colon is
    the name of the thing rather than the type of it, so it is dropped. Read without that
    rule a caller of a function taking `{ id: number }` was charged for learning a type
    called `id`.
    """
    if written in (NOTHING_RETURNED, INFERRED):
        return []
    return [name.rsplit(".", 1)[-1] for name in written_names_in(written)]


def written_names_in(written):
    """Every type name inside a type as it was written, qualifiers and all.

    `Promise<Customer>` is `Promise` and `Customer`; `RecordedDeposit[]` is
    `RecordedDeposit`; `string | null` is both of them; `React.ReactNode` is kept whole,
    because what stands in front of the dot is part of the answer for anybody following
    the name rather than merely counting it.
    """
    if written in (NOTHING_RETURNED, INFERRED):
        return []
    found = []
    for match in re.finditer(r"[A-Za-z_$][\w$.]*", written):
        name = match.group(0).strip(".")
        if not name or name.split(".")[-1] in _NOT_A_TYPE_NAME:
            continue
        rest = written[match.end():].lstrip()
        if rest.startswith("?"):
            rest = rest[1:].lstrip()
        if rest.startswith(":"):
            # `id` in `{ id: number }`, `signal` in `(signal: AbortSignal) => void`: the
            # name of a property or of a parameter inside a written type, never a type.
            continue
        found.append(name)
    return found


def parse(text, path, root=None):
    """What this TypeScript file contains, or a ParseFailure naming why it could not be read.

    `root` is the source root the file was found under, and unlike the Java side this
    reading needs it: a TypeScript module is a file, so which module this file *is* is a
    question about where it sits, and the root is the half of that answer this file cannot
    see for itself.
    """
    masked, documentation = _masked(text)
    documented = _refusals_documented_in(text, documentation)
    lines = len(text.splitlines())
    matching, depth_of = _braces(masked, text)

    where, name = _place_of(path, root)
    # JSX is legal in a `.tsx` file and nowhere else, and that is what decides whether
    # `<Customer>` is an element or a generic's type arguments. The extension is the
    # whole of the rule, exactly as it is for `tsc`.
    jsx = path.endswith(".tsx")
    _refuse_unreadable_imports(masked, text)
    imports = _imports_in(masked, text, where)
    exports = _exports_in(masked, text, matching, depth_of, documented, name, jsx)

    declared = DeclaredType(
        name=name,
        kind=KINDS[0],
        depth=0,
        ends_at=len(masked),
        owner=None,
        qualified=name,
        methods=exports.methods,
        fields=_fields_in(masked, depth_of),
        constructors=(),
        **_reached_in(masked, jsx),
    )
    types = [declared] + [
        DeclaredType(
            name=each,
            kind="type",
            depth=1,
            ends_at=len(masked),
            owner=declared,
            qualified=each,
        )
        for each in exports.types
    ]
    log.debug(
        "parsed file path=%s module=%s lines=%d exports=%d typesDeclared=%d imports=%d",
        path,
        module_id(where, name),
        lines,
        len(exports.methods),
        len(exports.types),
        len(imports),
    )
    return ParsedFile(where, types, lines, imports)


class Imported:
    """One import, as the module it names and the name this file then writes for it.

    `type` is the module id the specifier resolved to. `local` is the name the body writes
    — `App` for a default import, `fetchDeposits` for a named one, `fd` for
    `{ fetchDeposits as fd }` — and it is the whole of how a name in a body is followed
    back to a module here, because TypeScript has no package scope to fall back on.

    `member` is what the module was asked for under that name, which is what a bare call
    in the body is matched against: the two are the same word unless the import renamed
    it. A namespace import — `import * as api` — asks for no one member and carries None,
    so `api.fetchDeposits()` is followed as a call written against a name instead.

    `on_demand` is always false and is here because the scoring rules read it. Java's
    `a.b.*` opens a package for a name to be tried under; TypeScript has nothing of the
    kind — `export *` re-exports out of a module rather than opening one — so there is
    never a prefix here to try a name against.
    """

    def __init__(self, type_, member, local):
        self.type = type_
        self.member = member
        self.local = local
        self.on_demand = False


class _Exports:
    """What a file offers a caller: the functions, and the types it declares."""

    def __init__(self, methods, types):
        self.methods = tuple(methods)
        self.types = tuple(types)


def _place_of(path, root):
    """The package this file belongs to and the name it is known by.

    The package is where the file sits — the root's own label, and the directories under
    it — because that is what groups modules on the page, and for a language whose modules
    are files the directory *is* the package a reader navigates. The name is the file's,
    without the extension, so that the id a caller's import resolves to and the id the
    graph holds are one string.
    """
    if root is None:
        raise ParseFailure(
            "this file was read with no source root, and a TypeScript module is the path "
            "it sits at rather than anything written inside it"
        )
    directory, _, base = path.rpartition("/")
    name = base
    for suffix in SUFFIXES:
        if name.endswith(suffix):
            name = name[: -len(suffix)]
            break
    where = root.label + ("/" + directory if directory else "")
    return where, name


def _target_of(specifier, where):
    """The module id an import specifier names, or a string no module here answers to.

    A relative specifier is a path, resolved against the directory the importing file sits
    in exactly as a bundler resolves it, and its extension comes off so that `./api` and
    `./api.ts` are one module. Anything else — `react`, `react-dom/client` — is a
    dependency: somebody else's source, which this graph does not hold and does not read,
    so it is carried as written and matches no module. That is the answer rather than a
    gap in it — a fan line to a package nobody here wrote would be a line to a card that
    cannot exist.
    """
    if not specifier.startswith("."):
        return specifier
    resolved = posixpath.normpath(posixpath.join(where, specifier))
    for suffix in _MODULE_SUFFIXES:
        if resolved.endswith(suffix):
            return resolved[: -len(suffix)]
    return resolved


def _imports_in(masked, text, where):
    """Every import this file wrote, as the module it names and the names it binds."""
    imports = []
    for found in _IMPORT_FROM.finditer(masked):
        specifier = text[found.start(3):found.end(3)]
        target = _target_of(specifier, where)
        for member, local in _bindings_in(found.group(1)):
            imports.append(Imported(target, member, local))
    for found in _IMPORT_ONLY.finditer(masked):
        # `import './index.css'` binds no name at all. Recorded anyway, and with no name
        # to write for it, because loading a file is a thing this file does and a reader
        # of the graph should not have to notice its absence.
        specifier = text[found.start(2):found.end(2)]
        imports.append(Imported(_target_of(specifier, where), None, None))
    return tuple(imports)


def _bindings_in(clause):
    """The names an import clause binds, each as the member asked for and the local name.

    Three forms, and each binds differently. `App` is the module's default export under
    whatever name this file chose for it. `{ a, b as c }` asks for members by name. `* as
    api` asks for no member and binds the whole module, which is what carrying None for
    the member says.
    """
    found = []
    rest = clause.strip()
    while rest:
        if rest.startswith("{"):
            closed = rest.find("}")
            if closed < 0:
                return found
            for part in rest[1:closed].split(","):
                member, local = _renamed(part)
                if member:
                    found.append((member, local))
            rest = rest[closed + 1:].lstrip()
        elif rest.startswith("*"):
            _, local = _renamed(rest)
            if local:
                found.append((None, local))
            rest = ""
        else:
            word = re.match(r"[A-Za-z_$][\w$]*", rest)
            if word is None:
                return found
            found.append(("default", word.group(0)))
            rest = rest[word.end():].lstrip()
        rest = rest[1:].lstrip() if rest.startswith(",") else rest
    return found


def _renamed(part):
    """One binding written `a`, `a as b`, `type a`, or `* as b`, as (asked for, written)."""
    words = re.findall(r"[A-Za-z_$][\w$]*|\*", part)
    words = [word for word in words if word != "type"]
    if not words:
        return None, None
    if len(words) >= 3 and words[-2] == "as":
        return words[0], words[-1]
    return words[0], words[0]


def _refuse_unreadable_imports(masked, text):
    """Fail the file if it wrote an import neither pattern above accounts for.

    `import` is reserved: outside a string or a comment it opens a statement naming a
    module, and both spellings of that are read. One that matched neither is a form this
    reading has no account of, and passing over it would take a module's whole reach with
    it — silently, since a module reaching nothing is exactly what a shallow module looks
    like.

    Two things wearing the word are held out by `_AN_IMPORT` itself rather than being
    read: `import.meta` is not an import at all, and `import('./api')` is an expression
    evaluated when the code runs. The second is a floor and is named as one — a module
    this graph holds, reached only through a dynamic import, is a module reached nowhere
    on the page — and it is a floor rather than a failure because refusing the file would
    take everything else the file reaches with it.
    """
    accounted = set()
    for pattern in (_IMPORT_FROM, _IMPORT_ONLY):
        for found in pattern.finditer(masked):
            accounted.add(found.start())
    for found in _AN_IMPORT.finditer(masked):
        if found.start() not in accounted:
            raise ParseFailure(
                "the import on line %d could not be read: it names no module in either "
                "of the two forms this tool reads" % _line_of(text, found.start())
            )


def _exports_in(masked, text, matching, depth_of, documented, module, jsx):
    """What a caller of this file can reach, and the types the file declares.

    Read from the `export` keyword outwards, because that is the whole of a TypeScript
    module's interface: nothing else in the file is reachable from another one, however
    public it looks. An `export` this reading cannot make sense of fails the file rather
    than being passed over — a missed export is an interface quietly cheaper than the
    source makes it, and a cheap interface over a fan is what this page calls deep.

    An exported *value* that is not a function, and an exported *class*, are named and not
    measured, which is the same answer the Java side gives to a public field and to a type
    declared inside a module. Both are logged with the reason, so the floor's edge can be
    read rather than discovered.
    """
    methods = []
    types = []
    # Where every name this file declares at module scope is written, so that an export
    # naming one — `export default App`, `export { ring }` — can be read as the
    # declaration it points at. Without this the two commonest ways a React file offers
    # its component both arrive as no export at all, and a file whose whole interface is
    # written that way reads as presenting nothing, which is the finding this page exists
    # to make and would then be making about nothing.
    declared_at = {}
    for found in _DECLARES.finditer(masked):
        if depth_of[found.start()] == 0 and found.group(2) not in declared_at:
            declared_at[found.group(2)] = (found.group(1), found.end(1))
    for found in _AN_EXPORT.finditer(masked):
        if depth_of[found.start()] != 0:
            continue
        _read_one_export(
            masked, text, matching, documented, module, found.end(), methods, types, jsx,
            declared_at,
        )
    for found in _DECLARES.finditer(masked):
        # A type the file declares but does not export is still a type declared inside
        # this module, and is named on it exactly as a nested Java type is named on the
        # class that holds it.
        if depth_of[found.start()] == 0 and found.group(1) in ("type", "interface", "enum", "class"):
            if found.group(2) not in types:
                types.append(found.group(2))
    return _Exports(methods, sorted(set(types)))


def _read_one_export(masked, text, matching, documented, module, position, methods, types,
                     jsx, declared_at):
    """Read whatever this one `export` opens, or fail the file naming what stopped it."""
    token, start, after = _token_at(masked, position)
    default = token == "default"
    if default:
        token, start, after = _token_at(masked, after)
    if token == "async":
        token, start, after = _token_at(masked, after)
    if token in _NOT_READ_HERE:
        raise ParseFailure(
            "the export on line %d is written `export %s`, which this tool has no reading "
            "of: what it describes is declared somewhere this tool was not pointed at"
            % (_line_of(text, start), token)
        )
    if token == "function":
        method = _function_from(masked, text, after, matching, documented, module, jsx)
        if method is not None:
            methods.append(method)
        return
    if token == "class":
        named, _, _ = _token_at(masked, after)
        _decline(text, start, named, "an exported class is named on this module rather "
                 "than measured, exactly as a type declared inside a Java module is: what "
                 "it costs a caller is its members, and its members are read nowhere")
        if named and named not in types:
            types.append(named)
        return
    if token in ("type", "interface", "enum"):
        named, _, _ = _token_at(masked, after)
        if named and named not in types:
            types.append(named)
        return
    if token in ("const", "let", "var"):
        method = _binding_from(masked, text, after, matching, documented, module, jsx)
        if method is not None:
            methods.append(method)
        return
    if token == "{":
        # `export { ring, of as price }`, and `export { ring } from './till'`. The first
        # names things this file declares and is read as those declarations; the second
        # hands on what another file declares, whose signature is written there.
        closed = masked.find("}", after)
        if closed < 0:
            raise ParseFailure(
                "the export on line %d could not be read: nothing closes the list of "
                "names it opens" % _line_of(text, start)
            )
        elsewhere = _token_at(masked, closed + 1)[0] == "from"
        for part in _split_on_commas(masked[after:closed]):
            named, called = _renamed(part)
            if named is None:
                continue
            if elsewhere:
                _decline(text, start, named, "a re-export hands on what another file "
                         "declares, and the signature a caller would learn is written in "
                         "that file rather than in this one")
                continue
            _read_a_name_this_file_declares(
                masked, text, matching, documented, module, jsx, declared_at, named,
                called, start, methods,
            )
        return
    if token == "*":
        _decline(text, start, token, "a re-export hands on what another file declares, "
                 "and the signature a caller would learn is written in that file rather "
                 "than in this one")
        return
    if default and re.match(r"[A-Za-z_$][\w$]*$", token or "") and (
        _token_at(masked, after)[0] not in _AN_EXPRESSION_CARRIES_ON_WITH
    ):
        # `export default App`, where `App` is declared elsewhere in this file. Read as
        # the declaration it points at, under the name the source declared it with rather
        # than the word `default`.
        _read_a_name_this_file_declares(
            masked, text, matching, documented, module, jsx, declared_at, token, token,
            start, methods,
        )
        return
    if default and token in ("(", "<") and _opens_an_arrow(masked, start):
        # `export default () => { ... }`, and `export default <T,>(each: T) => each`. A
        # function a caller calls, offered under the one name a caller of this module can
        # import it by, which is what `export default function () {}` is already read as.
        methods.append(
            _arrow_from(masked, text, start, matching, documented, module, "default", jsx,
                        position)
        )
        return
    if default:
        # `export default 42`, `export default connect(App)`, `export default <div />`.
        # Legal, reachable, and not a signature this reading can price: what it costs a
        # caller is whatever the expression evaluates to, and nothing here evaluates it.
        # So it is named and skipped, the way an exported class is — failing the file
        # instead took every module written this way off the page along with every fan
        # line into it, for source `tsc` compiles without a word.
        _decline(text, start, token, "a default export of an expression hands a caller a "
                 "value rather than a signature: what it costs them is whatever the "
                 "expression evaluates to, and nothing here evaluates it")
        return
    raise ParseFailure(
        "the export on line %d could not be read: it is followed by %r, which this tool "
        "has no reading of" % (_line_of(text, start), token)
    )


def _read_a_name_this_file_declares(masked, text, matching, documented, module, jsx,
                                    declared_at, named, called, position, methods):
    """Read the declaration an export points at, under the name the export binds it to."""
    where = declared_at.get(named)
    if where is None:
        _decline(text, position, named, "this file exports a name, and this reading found "
                 "no declaration of it at module scope to read a signature from")
        return
    kind, after = where
    if kind == "function":
        method = _function_from(masked, text, after, matching, documented, module, jsx)
    elif kind in ("const", "let", "var"):
        method = _binding_from(masked, text, after, matching, documented, module, jsx)
    else:
        _decline(text, position, named, "what this file exports under that name is a %s, "
                 "which is named on this module rather than measured" % kind)
        return
    if method is not None:
        methods.append(_rename(method, called))


def _decline(text, position, what, why):
    """Say out loud that something a caller can reach was read and then not measured.

    The one decision here that cannot fail loudly: an export left out leaves an interface
    cheaper than the source makes it, and there is nothing to fail on because the source is
    perfectly legal. So every one is logged with its line and its reason, and the list is
    short enough to read: `--log-level DEBUG` and grep for `export not read as a method`.
    """
    log.debug(
        "export not read as a method line=%d export=%s reason=%s",
        _line_of(text, position),
        what,
        why,
    )


def _function_from(masked, text, position, matching, documented, module, jsx,
                   documented_at=None, annotated=False):
    """One `function` declaration, from just after the keyword, as a caller meets it.

    `documented_at` is where the declaration a JSDoc block would be written above starts,
    for the one shape where that is not the keyword this reads from: `const f = function
    () {}` puts an `=` between the block and the word `function`, and a block is looked
    for back from the keyword the declaration opens with.
    """
    token, start, after = _token_at(masked, position)
    if token == "*":
        token, start, after = _token_at(masked, after)
    name = "default"
    if token and re.match(r"[A-Za-z_$][\w$]*$", token):
        # `export default function () {}` is the one that writes no name, and `default` is
        # what a caller of it imports the module for. Everything else names itself.
        name = token
        token, start, after = _token_at(masked, after)
    opened = start
    variables = ()
    if token == "<":
        opened, variables = _past_type_parameters(masked, text, start)
        token, opened, after = _token_at(masked, opened)
    if token != "(":
        raise ParseFailure(
            "the function %s on line %d could not be read: %r stands where its parameters "
            "should" % (name, _line_of(text, opened), token)
        )
    closed = after_balanced(masked, opened)
    parameters = _parameters_in(masked[opened + 1:closed - 1], text, opened, name, annotated)
    returns, body = _returns_and_body(masked, text, closed, matching, name)
    return Method(
        name=name,
        visibility="public",
        parameters=parameters,
        returns=returns,
        type_parameters=variables,
        annotations=(),
        documented_refusals=_documented_before(
            masked, position if documented_at is None else documented_at, documented, module
        ),
        has_a_body=body is not None,
        calls=_calls_in(masked[body[0]:body[1]], jsx) if body is not None else (),
    )


def _binding_from(masked, text, position, matching, documented, module, jsx):
    """`const name = (a: A): R => ...`, or a value this reading declines to call a method.

    An exported arrow function and an exported `function` expression are functions a
    caller calls, so they are read as methods. An exported value that is not one is a
    thing a caller reads rather than calls, and is left out for the same reason the Java
    side leaves out a public field: what it costs a caller is a type and a name, and this
    tool prices methods. It is logged rather than dropped.

    A bracket after the `=` is asked whether it opens an arrow before it is read as one,
    because `(1 + 2)` opens no function and reading its brackets as a parameter list
    failed the file for a parameter nobody wrote. `position` is carried down to whichever
    reading follows, so that a JSDoc block above the whole declaration is found from the
    keyword rather than from the far side of the `=`.
    """
    name, start, after = _token_at(masked, position)
    if not name or not re.match(r"[A-Za-z_$][\w$]*$", name):
        _decline(text, start, name, "this reading found no name after the keyword")
        return None
    token, at, after = _token_at(masked, after)
    annotated = token == ":"
    if annotated:
        # A written type on the binding itself. It is the type of the value, not of what
        # calling it hands back, so the signature below is where the interface is read —
        # and it is what a parameter written with no type of its own is typed by, which is
        # why whichever reading follows is told about it.
        while token is not None and token != "=":
            token, at, after = _token_at(masked, after)
        if token is None:
            _decline(text, start, name, "nothing is assigned to it here")
            return None
    if token != "=":
        _decline(text, start, name, "nothing is assigned to it here")
        return None
    token, at, after = _token_at(masked, after)
    if token == "async":
        token, at, after = _token_at(masked, after)
    if token == "function":
        return _rename(
            _function_from(masked, text, after, matching, documented, module, jsx, position,
                           annotated),
            name,
        )
    if token in ("(", "<") and _opens_an_arrow(masked, at):
        return _arrow_from(masked, text, at, matching, documented, module, name, jsx,
                           position, annotated)
    if token and re.match(r"[A-Za-z_$][\w$]*$", token):
        after_the_name = _token_at(masked, after)[0]
        if after_the_name == "=>":
            # `const of = value => ...`: one parameter with no brackets around it and no
            # type written on it.
            return Method(
                name=name,
                visibility="public",
                parameters=(INFERRED,),
                returns=INFERRED,
                documented_refusals=_documented_before(masked, position, documented, module),
                has_a_body=True,
                calls=(),
            )
    _decline(text, start, name, "what is assigned to it is a value rather than a "
             "function, and what a caller reads off a module is priced nowhere here — "
             "the same answer the Java side gives a public field")
    return None


def _arrow_from(masked, text, position, matching, documented, module, name, jsx,
                documented_at=None, annotated=False):
    """`(a: A): R => ...` bound to `name`, as a caller meets it.

    `documented_at` is where the declaration this arrow belongs to starts, and it is a
    separate argument because a JSDoc block is written above the whole declaration while
    an arrow starts after the `=`. Handed the arrow's own position, `_documented_before`
    looked back across `export const viaConst = ` and refused the `=` in it — so a module
    that documented its refusal was accused of raising one it never promised, which is the
    one accusation this page must never make.
    """
    opened = position
    variables = ()
    if masked[opened] == "<":
        opened, variables = _past_type_parameters(masked, text, opened)
        token, opened, _ = _token_at(masked, opened)
        if token != "(":
            raise ParseFailure(
                "the function %s on line %d could not be read: %r stands where its "
                "parameters should" % (name, _line_of(text, opened), token)
            )
    closed = after_balanced(masked, opened)
    parameters = _parameters_in(masked[opened + 1:closed - 1], text, opened, name, annotated)
    returns = INFERRED
    token, at, after = _token_at(masked, closed)
    if token == ":":
        written, at = _up_to_the_arrow(masked, after)
        returns = _normalised(written)
        token, at, after = _token_at(masked, at)
    if token != "=>":
        raise ParseFailure(
            "the function %s on line %d could not be read: %r stands where its arrow "
            "should" % (name, _line_of(text, at), token)
        )
    token, body_at, _ = _token_at(masked, after)
    body = None
    if token == "{" and body_at in matching:
        body = (body_at + 1, matching[body_at])
    return Method(
        name=name,
        visibility="public",
        parameters=parameters,
        returns=returns,
        type_parameters=variables,
        documented_refusals=_documented_before(
            masked, position if documented_at is None else documented_at, documented, module
        ),
        has_a_body=True,
        calls=_calls_in(masked[body[0]:body[1]], jsx) if body is not None else (),
    )


def _opens_an_arrow(masked, position):
    """Whether what is written from this bracket onwards is an arrow function.

    A bracket opens an arrow function's parameter list, and it also opens a parenthesised
    expression: `const total = (1 + 2)` and `const ring = (a: A) => a` are told apart by
    the arrow after the group and by nothing else. Asked nothing, the first was read as a
    parameter list and its file was failed for a parameter called `1 + 2` — a reason that
    is not true of the source, which is a worse answer than no reading at all.

    A `<` is the same question one step earlier: it opens a generic's type parameters in
    front of an arrow's brackets, and in a `.tsx` file it also opens an element. So the
    group is stepped over and the bracket after it has to be there.
    """
    at = position
    if masked[at:at + 1] == "<":
        closed = _after_angles(masked, at)
        if closed is None:
            return False
        token, at, _ = _token_at(masked, closed)
        if token != "(":
            return False
    if masked[at:at + 1] != "(":
        return False
    token, at, after = _token_at(masked, after_balanced(masked, at))
    if token == "=>":
        return True
    if token != ":":
        return False
    _, arrow = _up_to_the_arrow(masked, after)
    return masked.startswith("=>", arrow)


def _up_to_the_arrow(masked, position):
    """An arrow function's written return type, which ends at the arrow the body follows.

    The arrow is the boundary, and `=>` inside the type — `(): () => void => {...}` — is
    counted through its brackets like everything else, so the one that ends the type is
    the one written outside them all.
    """
    depth = 0
    at = position
    while at < len(masked):
        if masked.startswith("=>", at):
            # An arrow, and never a bracket closing: counted as one, the `>` in a return
            # type of `(() => void)` closed the group its own `(` had opened, every depth
            # after it was one too few, and the arrow that ends the type was never found.
            if depth == 0:
                return masked[position:at], at
            at += 2
            continue
        character = masked[at]
        if character in "(<[{":
            depth += 1
        elif character in ")>]}":
            depth -= 1
        at += 1
    return masked[position:], at


def _rename(method, name):
    """The same method under the name the export binds it to, or None when there was none."""
    if method is None:
        return None
    return Method(
        name=name,
        visibility=method.visibility,
        parameters=method.parameters,
        returns=method.returns,
        type_parameters=method.type_parameters,
        annotations=method.annotations,
        documented_refusals=method.documented_refusals,
        has_a_body=method.has_a_body,
        calls=method.calls,
    )


def _past_type_parameters(masked, text, position):
    """Just past the `<...>` a generic function writes before its parameters, and the names in it.

    The names are why this hands back two things rather than one. A generic's own `<T>` is
    a hole the caller fills with a type they already hold, so charging them for learning a
    type called `T` prices the letter rather than a type — and the card then names a type
    a reader can go looking for and will never find. The Java side records them for
    exactly that reason, and read without them `export function first<T>(items: T[]): T`
    cost 4 units where the same shape in Java costs 2.
    """
    closed = _after_angles(masked, position)
    if closed is None:
        raise ParseFailure(
            "a type parameter list opened on line %d is never closed"
            % _line_of(text, position)
        )
    return closed, _type_parameters_in(masked[position + 1:closed - 1])


def _after_angles(masked, position):
    """Just past the `>` that closes the `<` here, or None when nothing closes it.

    Non-raising, because it is asked the question as well as answered with it: whether a
    `<` opens a generic's type parameters at all is settled by what stands after the
    group, and a `<` that closes nowhere is a `<` that opens none.
    """
    depth = 0
    at = position
    while at < len(masked):
        if masked[at] == "<":
            depth += 1
        elif masked[at] == ">":
            depth -= 1
            if depth == 0:
                return at + 1
        at += 1
    return None


def _type_parameters_in(inside):
    """The names a type parameter list introduces, by the first word of each part.

    `<T, U extends Keyed<T>, K = string>` introduces `T`, `U` and `K`. Everything after
    the first word of a part is a bound or a default — a type the caller really does meet,
    and one this reading charges for wherever it is written into a signature — so it is
    not one of them. A variance annotation and a `const` modifier stand in front of the
    name and are not one either.
    """
    found = []
    for part in _split_on_commas(inside):
        word = re.match(
            r"[\s]*(?:const[ \t\r\n]+)?(?:(?:in|out)[ \t\r\n]+)*([A-Za-z_$][\w$]*)", part
        )
        if word is not None:
            found.append(word.group(1))
    return tuple(found)


def _returns_and_body(masked, text, position, matching, name):
    """What a function hands back and where its body is, or a failure naming which is unread.

    The two are read together because telling them apart is the whole difficulty: a return
    type may itself be written with braces — `: { id: number }` — and a brace is also how
    a body opens. Two rules settle it, and the type is what starts at the colon in both
    cases:

    - a brace group with nothing readable in front of it is the type, because `f(): { ... }`
      cannot be a body — a colon with a body straight after it is a return type nobody
      wrote, which TypeScript does not compile;
    - a brace group with another brace after it is the type too, and the group after it is
      the body.

    Read with the scan position mistaken for the start of the type, `f(): { id: number }
    { ... }` came out returning the empty string: the type was stepped over on the way to
    the body and never written down, and a caller was charged nothing for a return they
    have to learn whole — the one direction this reading must never be wrong in.

    A signature with no body under it is an overload or an ambient declaration. It is
    reported as such rather than failed, because the refusals it documents are a promise
    to whoever implements it rather than something this file was ever going to keep. A
    `;` reached before any brace is what says so, which is why it is asked about first:
    looked for afterwards, `f(): X;` ran on to the next declaration's body and read
    everything in between as one type.
    """
    token, at, after = _token_at(masked, position)
    if token == "{" and at in matching:
        return INFERRED, (at + 1, matching[at])
    if token in (";", "}", None):
        # An overload signature, or a method header inside a declaration with no body of
        # its own. What it promises is a promise to whoever implements it, which is the
        # distinction `has_a_body` carries.
        return INFERRED, None
    if token != ":":
        raise ParseFailure(
            "the return of %s on line %d could not be read: %r stands where its type or "
            "its body should" % (name, _line_of(text, at), token)
        )
    written = after
    scan = after
    while True:
        opened = _first_brace_at_depth_zero(masked, scan)
        finished = _where_the_signature_ends(masked, scan)
        if finished is not None and (opened is None or finished < opened):
            return _normalised(masked[written:finished]), None
        if opened is None:
            raise ParseFailure(
                "the return type of %s on line %d could not be read: nothing closes it"
                % (name, _line_of(text, at))
            )
        if opened not in matching:
            raise ParseFailure(
                "the return type of %s on line %d could not be read: a brace in it is "
                "never closed" % (name, _line_of(text, at))
            )
        following = _token_at(masked, matching[opened] + 1)[0]
        if masked[written:opened].strip() and following != "{":
            return _normalised(masked[written:opened]), (opened + 1, matching[opened])
        scan = matching[opened] + 1


def _where_the_signature_ends(masked, position):
    """Where a signature with no body under it ends, or None when a body may still follow.

    Two things end one. A `;` outside every bracket is the written one. The other is a
    word no type can hold: TypeScript lets an overload signature end at a newline, so
    `export function ring(id: number): string` followed on the next line by the
    implementation of the same name has nothing at all between the type and the next
    declaration. Read without that, the first signature came out returning
    `string export function ring(id: number): string` — every word of the next
    declaration's header charged to a caller as a type to learn.
    """
    ended = _first_at_depth_zero(masked, position, ";")
    starts = _OPENS_A_DECLARATION.search(masked, position)
    if starts is None:
        return ended
    if ended is None:
        return starts.start()
    return min(ended, starts.start())


def _first_brace_at_depth_zero(masked, position):
    return _first_at_depth_zero(masked, position, "{")


def _first_at_depth_zero(masked, position, wanted):
    """Where this character is first written outside every bracket, from `position` on."""
    depth = 0
    at = position
    while at < len(masked):
        character = masked[at]
        if character == wanted and depth == 0:
            return at
        if character in "(<[":
            depth += 1
        elif character in ")>]":
            depth -= 1
            if depth < 0:
                return None
        elif character == "{":
            if depth == 0:
                return at if wanted == "{" else None
            depth += 1
        elif character == "}":
            depth -= 1
        at += 1
    return None


def _parameters_in(inside, text, position, name, annotated=False):
    """Every parameter of one function, by the type the source wrote for it.

    A destructured parameter is one parameter: `{ customer, onSignOut }: Props` hands over
    one thing however many names the caller's object is taken apart into, and the type is
    written after the pattern exactly as it is after a plain name.

    A parameter written with no type at all fails the file. TypeScript is compiled here
    under `strict`, which forbids one — so a parameter this reading cannot find a type on
    is a shape it has misread, and a misread parameter leaves an interface cheaper than
    the source makes it.

    Two shapes are legal without one, and neither fails. A parameter with a default value
    has its type inferred from the default. And `annotated` says the declaration itself
    carried a written type — `const ring: Ring = (a) => a` — where the parameter's type is
    written on the binding and TypeScript reads it from there: strict compiles it, so
    failing the file would be an alarm over source with nothing the matter with it. Both
    are recorded as unwritten, which is the honest answer this reading can give: a
    parameter crosses the seam there, and no type was read to name.
    """
    parameters = []
    for part in _split_on_commas(inside):
        written = part.strip()
        if not written:
            continue
        if written.startswith("..."):
            written = written[3:].lstrip()
        colon = _first_in_the_open(written, ":")
        equals = _first_in_the_open(written, "=")
        if colon is None or (equals is not None and equals < colon):
            if equals is not None or annotated:
                parameters.append(INFERRED)
                continue
            raise ParseFailure(
                "a parameter of %s on line %d could not be read: %r has no type written "
                "on it" % (name, _line_of(text, position), written[:40])
            )
        called = written[:colon].strip().rstrip("?").strip()
        if called == "this":
            # The receiver a method may name. Not a parameter a caller passes at all.
            continue
        rest = written[colon + 1:]
        default = _first_in_the_open(rest, "=")
        parameters.append(_normalised(rest if default is None else rest[:default]))
    return tuple(parameters)


def _first_in_the_open(written, wanted, position=0):
    """Where this character is first written outside every bracket, braces included.

    Not `_first_at_depth_zero`, and the brace is the whole of the difference. That one
    stops dead at a `{`, because it is reading a signature and there a brace is either
    the body or the type the body follows. Here a brace is a pattern a parameter is taken
    apart into — `{ customer, onSignOut }: Props` hands over one thing, with its type
    written after the pattern exactly as it is after a plain name — so it is a bracket
    like any other. Read the other way round, every destructured parameter in this
    repository's own frontend failed its file for having no type written on it, with the
    type written plainly three characters further along.

    An `=` that is part of an operator is not an assignment: `(cb: (a: A) => void = noop)`
    writes three of them and only the last is the default.
    """
    depth = 0
    at = position
    while at < len(written):
        character = written[at]
        if depth == 0 and character == wanted and not _part_of_an_operator(written, at, wanted):
            return at
        if character in "([{":
            depth += 1
        elif character in ")]}":
            depth -= 1
            if depth < 0:
                return None
        at += 1
    return None


def _part_of_an_operator(written, at, wanted):
    """Whether the `=` here is half of `=>`, `==`, `<=` or another operator spelled with one."""
    if wanted != "=":
        return False
    return written[at + 1:at + 2] in ("=", ">") or written[max(at - 1, 0):at] in (
        "=", "!", "<", ">", "+", "-", "*", "/", "%", "&", "|", "^"
    )


def _fields_in(masked, depth_of):
    """The values this file holds at module scope, by the name and the type written for it.

    Read for one reason, the same one the Java side reads a field for: a held value is what
    a call through it is written against, and the type it was declared with is what that
    call reaches. A value with no written type is not one this tool can follow, so it is
    left out rather than guessed at — which is most of them, since TypeScript infers a
    type the source did not write.
    """
    found = []
    for match in _DECLARES.finditer(masked):
        if depth_of[match.start()] != 0 or match.group(1) not in ("const", "let", "var"):
            continue
        token, _, after = _token_at(masked, match.end())
        if token != ":":
            continue
        written, _ = _up_to_the_assignment(masked, after)
        if written.strip():
            found.append(Field(match.group(2), _normalised(written)))
    return tuple(found)


def _up_to_the_assignment(masked, position):
    """A written type that ends where the value assigned to it begins."""
    depth = 0
    at = position
    while at < len(masked):
        if masked.startswith("=>", at):
            # The arrow inside a written function type, which assigns nothing and closes
            # nothing: `const ring: () => void = ...` writes one before the `=` that does.
            at += 2
            continue
        character = masked[at]
        if character in "(<[{":
            depth += 1
        elif character in ")>]}":
            depth -= 1
        elif depth == 0 and character in "=;\n":
            return masked[position:at], at
        at += 1
    return masked[position:], at


def _jsx_elements_in(masked, jsx):
    """Every JSX element in this text that names a component, with where each was written.

    Two things have to be true before a `<` opens one, and each is a rule rather than a
    guess. The file has to be one JSX is legal in — TypeScript reads `<X>` in a `.ts` file
    as a type argument list or a type assertion, never as an element — and the `<` has to
    stand somewhere a value can be written rather than behind the name of a generic.
    """
    if not jsx:
        return []
    found = []
    for match in _A_JSX_ELEMENT.finditer(masked):
        if _opens_type_arguments(masked[:match.start()]):
            log.debug(
                "angle brackets not read as an element name=%s reason=%s",
                match.group(1),
                "a name stands in front of them, so they are the type arguments of that "
                "name rather than a tag",
            )
            continue
        found.append(match)
    return found


def _opens_type_arguments(before):
    """Whether the `<` after this text opens a generic's type arguments rather than a tag.

    What stands in front of it is the whole of the answer, the same way it is for a slash
    that might open a regular expression. A name means type arguments —
    `useState<Customer | null>(null)` writes the same six characters as `<Customer ...>`
    and builds nothing at all — unless the name is a word a value can follow, because
    `return <Card />` is a name in front of a tag and nothing else. A closing bracket
    means type arguments for the same reason a name does. Everything else is punctuation
    an expression can follow, `>` included: a `>` ends a JSX opening tag as surely as it
    ends a type argument list, and `<div><Card /></div>` is how the nesting is written.
    """
    trimmed = before.rstrip()
    if not trimmed:
        return False
    name = _A_NAME_BEHIND_IT.search(trimmed)
    if name is not None:
        return name.group(0) not in _BEFORE_A_REGEX
    return trimmed[-1] in ")]"


def _reached_in(masked, jsx):
    """What this file's implementation reaches for, by name and never by volume.

    The same five readings the Java side takes, over the whole file rather than over one
    type's body, because the whole file is the module. Each is a set of names rather than
    a count of occurrences, which is the property depth rests on: writing the same call
    ten more times reaches nothing new.

    A JSX element is read as a construction, because that is what it is: `<App />`
    compiles to a call building an `App`, and a component used in a template is
    coordinated exactly as one built with `new` is. Only a capitalised name can be one —
    JSX reads a lowercase tag as HTML and never as a name in scope.
    """
    raises = sorted({name.rsplit(".", 1)[-1] for name in _THROWN.findall(masked)})
    throws = len(_A_THROW.findall(masked))
    read = len(_THROWN.findall(masked))
    plain = _THROUGH_THIS.sub("", masked)
    receivers = {found.group(1) for found in _A_RECEIVER_CALL.finditer(plain)}
    called = {
        found.group(1)
        for found in _A_CALL.finditer(masked)
        if found.group(1) not in _NOT_A_CALL
        and not masked[:found.start(1)].rstrip().endswith(".")
    }
    constructed = set(_CONSTRUCTED.findall(plain)) | {
        match.group(1) for match in _jsx_elements_in(masked, jsx)
    }
    declares = {found.group(2) for found in _DECLARES.finditer(masked)}
    log.debug(
        "reached read receivers=%d called=%d constructed=%d declares=%d raises=%s "
        "throwsNotRead=%d",
        len(receivers),
        len(called),
        len(constructed),
        len(declares),
        ",".join(raises) or "none this tool can read",
        throws - read,
    )
    return {
        "annotations": (),
        "supertypes": (),
        "type_parameters": (),
        "receivers": sorted(receivers),
        "constructed": sorted(constructed),
        "called": sorted(called),
        "declares": sorted(declares),
        "raises": raises,
        "throws_not_read": throws - read,
    }


def _calls_in(body, jsx):
    """Every call this one function's body makes, in the order the source evaluates them.

    The reading a flow is walked out of, and the only one here that keeps order: reach is
    a set on purpose, and a path cannot be walked out of a set. Three spellings are read —
    a call written against a name, a call with nothing in front of it, and a construction
    — for the reasons the Java side spells out, and a JSX element is one of the third,
    because that is what it compiles to.

    The order is evaluation order rather than the order the characters were typed:
    JavaScript evaluates a call's arguments before the call, so a site written inside
    another's argument list comes first.
    """
    found = []
    for match in _A_RECEIVER_CALL.finditer(body):
        receiver, name = match.group(1), match.group(2)
        if re.search(r"(?<![\w$.])new$", body[:match.start(1)].rstrip()):
            continue
        opened = match.end() - 1
        found.append(
            (match.start(1), after_balanced(body, opened),
             CallSite(None if receiver == "this" else receiver, name))
        )
    for match in _A_CALL.finditer(body):
        name = match.group(1)
        if name in _NOT_A_CALL:
            continue
        before = body[:match.start(1)].rstrip()
        if before.endswith(".") or re.search(r"(?<![\w$.])new$", before):
            continue
        if re.search(r"(?<![\w$.])(?:function|class)$", before):
            continue
        opened = match.end() - 1
        found.append((match.start(1), after_balanced(body, opened), CallSite(None, name)))
    for match in _CONSTRUCTED.finditer(body):
        found.append(
            (match.start(), after_balanced(body, match.end(1)),
             CallSite(None, match.group(1), builds=True))
        )
    for match in _jsx_elements_in(body, jsx):
        found.append((match.start(), match.end(), CallSite(None, match.group(1), builds=True)))
    return in_evaluation_order(found)


def _refusals_documented_in(text, documentation):
    """The refusals each JSDoc block promises, and the offset each block ends at."""
    ends_at_of = []
    names_of = []
    for opened, ends_at in documentation:
        names = _DOCUMENTED_REFUSAL.findall(text[opened:ends_at])
        if names:
            ends_at_of.append(ends_at)
            names_of.append(tuple(name.rsplit(".", 1)[-1] for name in names))
    return ends_at_of, names_of


def _documented_before(masked, at, documented, module):
    """The refusals the JSDoc immediately above this declaration promises, if there is one.

    Immediately means nothing but whitespace, `export`, `default` and `async` between the
    block's `*/` and the declaration — a JSDoc is written above the whole export rather
    than between the keyword and the function — so the search runs from the end of the
    block to the keyword rather than to the name.
    """
    ends_at_of, names_of = documented
    best = None
    for index, ends_at in enumerate(ends_at_of):
        # Nothing but words and whitespace between the block and the declaration, which is
        # what `export default async function ` is and what a second declaration is not:
        # a bracket, a brace, an `=` or a quote is a whole statement standing in between,
        # and a block above that documents that rather than this. Another comment in
        # between is already blanked to spaces by the time this reads, so a JSDoc with a
        # plain note under it still documents the declaration below both.
        if ends_at <= at and _NOTHING_BUT_WORDS.fullmatch(masked[ends_at:at]):
            best = index
    if best is None:
        return ()
    log.debug(
        "refusals documented module=%s refusals=%s", module, ",".join(names_of[best])
    )
    return names_of[best]


def _masked(text):
    """The same text with everything that must not be read as source blanked out.

    Offsets are preserved throughout, so every line number and every brace this reading
    goes on to find is the one the file has. Four things are blanked — a comment, a
    string, a template literal's text and a regular expression — and one deliberately is
    not: JSX text, which this reading has no way to tell from source and which is named on
    the page as a reading that can overstate rather than blanked by a guess.

    A `/** */` block is recorded as it is blanked, because that is where a documented
    refusal is written and the tag has to be read out of the original text.
    """
    out = list(text)
    documentation = []
    ended = _mask_code(text, out, 0, documentation, stop_at_close=False)
    if ended < len(text):
        raise ParseFailure(
            "braces do not balance: a closing brace with nothing open on line %d"
            % _line_of(text, ended)
        )
    return "".join(out), documentation


def _mask_code(text, out, position, documentation, stop_at_close):
    """Blank every literal and comment from here on, stopping at an unmatched `}`.

    `stop_at_close` is what makes a template literal's `${...}` readable: the substitution
    holds real code, and a walk that blanked it along with the text around it would drop
    every call written inside one. It returns where it stopped, which the caller checks —
    a substitution that runs off the end of the file is a template nobody closed.
    """
    length = len(text)
    depth = 0
    previous = ""
    previous_word = ""
    while position < length:
        character = text[position]
        if character in " \t\r\n":
            position += 1
            continue
        if character == "/" and text.startswith("//", position):
            while position < length and text[position] != "\n":
                out[position] = " "
                position += 1
            continue
        if character == "/" and text.startswith("/*", position):
            position = _mask_block_comment(text, out, position, documentation)
            previous, previous_word = "", ""
            continue
        if character in "'\"":
            position = _mask_quoted(text, out, position)
            previous, previous_word = "'", ""
            continue
        if character == "`":
            position = _mask_template(text, out, position, documentation)
            previous, previous_word = "`", ""
            continue
        if character == "/" and _a_regex_here(previous, previous_word):
            closed = _mask_regex(text, out, position)
            if closed is not None:
                position = closed
                previous, previous_word = "/", ""
                continue
        if character == "{":
            depth += 1
        elif character == "}":
            if stop_at_close and depth == 0:
                return position
            depth -= 1
            if depth < 0 and not stop_at_close:
                return position
        if character.isalnum() or character in "_$":
            start = position
            while position < length and (text[position].isalnum() or text[position] in "_$"):
                position += 1
            previous_word = text[start:position]
            previous = previous_word[-1]
            continue
        previous, previous_word = character, ""
        position += 1
    return length


def _mask_block_comment(text, out, position, documentation):
    ends_at = text.find("*/", position + 2)
    if ends_at < 0:
        raise ParseFailure(
            "a block comment opened on line %d is never closed" % _line_of(text, position)
        )
    if text.startswith("/**", position):
        documentation.append((position, ends_at + 2))
    _blank(text, out, position, ends_at + 2)
    return ends_at + 2


def _mask_quoted(text, out, position):
    """The string starting here, blanked, or the lone quote blanked when it opens none.

    A quote with no partner on its line opens no string. JavaScript strings do not span
    lines, so the rule cannot be wrong about legal source — and without it a single
    apostrophe in JSX prose blanked out the rest of the file and took every declaration
    after it along.
    """
    quote = text[position]
    at = position + 1
    length = len(text)
    while at < length:
        character = text[at]
        if character == "\\":
            at += 2
            continue
        if character == quote:
            _blank(text, out, position + 1, at)
            return at + 1
        if character == "\n":
            break
        at += 1
    out[position] = " "
    return position + 1


def _mask_template(text, out, position, documentation):
    """The template literal starting here: its text blanked, its substitutions left readable."""
    at = position + 1
    length = len(text)
    while at < length:
        character = text[at]
        if character == "\\":
            _blank(text, out, at, at + 2)
            at += 2
            continue
        if character == "`":
            return at + 1
        if character == "$" and text.startswith("${", at):
            out[at] = " "
            out[at + 1] = " "
            closed = _mask_code(text, out, at + 2, documentation, stop_at_close=True)
            if closed >= length:
                raise ParseFailure(
                    "a template substitution opened on line %d is never closed"
                    % _line_of(text, at)
                )
            out[closed] = " "
            at = closed + 1
            continue
        if character != "\n":
            out[at] = " "
        at += 1
    raise ParseFailure(
        "a template literal opened on line %d is never closed" % _line_of(text, position)
    )


def _mask_regex(text, out, position):
    """The regular expression starting at this slash, blanked, or None when it opens none.

    A regular expression cannot span a line, so a slash with no partner before the newline
    is a division rather than a literal nobody closed. Blanked whole, delimiters and all:
    `/(.{4})/g` writes braces that would otherwise be counted as a body's.
    """
    at = position + 1
    length = len(text)
    inside_a_class = False
    while at < length:
        character = text[at]
        if character == "\\":
            at += 2
            continue
        if character == "\n":
            return None
        if inside_a_class:
            if character == "]":
                inside_a_class = False
        elif character == "[":
            inside_a_class = True
        elif character == "/":
            at += 1
            while at < length and text[at].isalpha():
                at += 1
            _blank(text, out, position, at)
            return at
        at += 1
    return None


def _a_regex_here(previous, previous_word):
    """Whether a slash at this point opens a regular expression rather than dividing.

    What stands in front of it is the whole of the answer, because a regular expression is
    written where a value can be and a division where one has just been. A word decides it
    outright — `return /x/` is a literal, `total / count` is not — and otherwise the
    punctuation does.
    """
    if previous_word:
        return previous_word in _BEFORE_A_REGEX
    return previous == "" or previous in _AFTER_WHICH_A_REGEX_CAN_START


def _blank(text, out, start, ends_at):
    for at in range(start, min(ends_at, len(text))):
        if text[at] != "\n":
            out[at] = " "


def _braces(masked, text):
    """Which brace closes which, and the depth every character sits at.

    Both are wanted for the same thing: a declaration is this module's own when it is
    written at depth 0, and a function's body runs from its brace to the one that closes
    it. Braces that do not balance fail the file, in either direction — a closing brace
    with nothing open moves every declaration after it to a depth the source does not
    have, and one left open at the end swallows the rest of the file.
    """
    matching = {}
    opened = []
    depth_of = []
    depth = 0
    for position, character in enumerate(masked):
        if character == "{":
            depth_of.append(depth)
            opened.append(position)
            depth += 1
            continue
        if character == "}":
            depth -= 1
            if depth < 0:
                raise ParseFailure(
                    "braces do not balance: a closing brace with nothing open on line %d"
                    % _line_of(text, position)
                )
            matching[opened.pop()] = position
        depth_of.append(depth)
    if depth != 0:
        raise ParseFailure("braces do not balance: %d unclosed at end of file" % depth)
    return matching, depth_of


def _token_at(masked, position):
    """The next word or single character from here, and where it starts and ends.

    Comments are already blanked to spaces by the time this reads, so skipping whitespace
    is the whole of skipping what stands between two tokens. `=>` is the one pair of
    characters answered as one token, because it is what tells an arrow function's body
    from its return type.
    """
    length = len(masked)
    while position < length and masked[position] in " \t\r\n":
        position += 1
    if position >= length:
        return None, position, position
    character = masked[position]
    if character.isalpha() or character in "_$":
        at = position
        while at < length and (masked[at].isalnum() or masked[at] in "_$"):
            at += 1
        return masked[position:at], position, at
    if masked.startswith("=>", position):
        return "=>", position, position + 2
    return character, position, position + 1


def _split_on_commas(text):
    """The parts this text is written in, split on the commas outside every bracket."""
    parts = []
    depth = 0
    start = 0
    for position, character in enumerate(text):
        if character in "(<[{":
            depth += 1
        elif character in ")>]}":
            depth -= 1
        elif character == "," and depth == 0:
            parts.append(text[start:position])
            start = position + 1
    parts.append(text[start:])
    return parts


def _normalised(written):
    """A written type with its whitespace made uniform, so two spellings of one are one."""
    tidy = re.sub(r"\s+", " ", written).strip()
    return tidy.replace("< ", "<").replace(" >", ">").replace("[ ", "[").replace(" ]", "]")


def _line_of(text, position):
    return text.count("\n", 0, position) + 1
