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
  one follows it on the same line, and only when it is not written against the end of a
  word: JavaScript strings do not span lines, nothing JavaScript compiles puts a string
  against an identifier, and JSX prose is full of apostrophes. Neither half can be wrong
  about legal source. Without the first, a single `account's` in prose blanked out the
  rest of the file; without the second, the same apostrophe on a line that also carried a
  real string reached forward to that string's quote and blanked the code in between,
  braces included.
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
    after_the_arguments,
    ends_an_arrow,
    in_evaluation_order,
    line_of,
    normalised,
    spans_between_commas,
    split_on_commas,
)

# Fourteen names come from the Java reading and none of them is about Java. Six are the
# shapes a parsed file is reported in, which both readings fill in and nothing outside
# either has to tell apart. The other eight are punctuation: a bracket's partner, where a
# construction's arguments end, the order a call's arguments are evaluated in, where a
# comma splits a list and what lies between two of them, how a type is spelled, which line
# an offset sits on and whether a `>` closes anything. Both readings have to give the same
# answer to every one of those or "measured by the same rules" is not true of the page.
# `split_on_commas`, `normalised` and `line_of` were hand-copies here once; the copies
# drifted, and two review findings — a parameter list that collapsed around an `=>` and
# one type left spelled two ways — are what the drift cost. `after_the_arguments` is the
# newest of them and arrived the same way: `after_balanced` was called here in its place,
# and a `new Thing` written with no brackets swallowed the next call's and reversed the
# order a flow walks. They are shared for the same reason `after_balanced` always was: a
# second account of one of them is one that can drift.
log = logging.getLogger("module_depth_map.typescriptsource")

# What this reading is called in the graph, and which files it is the reading of.
NAME = "typescript"
SUFFIXES = (".ts", ".tsx")

# Whether the name a module goes by is also a name bound inside its own source. It is not,
# here, and that is a real difference between the two grains rather than a detail: a
# TypeScript module is a file, and the name it is known by is the file's basename, which
# is not a binding anywhere in it. Answered the Java way — where a class's own name really
# is in scope — a file called `format.ts` was taken to declare `format` for itself, so
# `import { format } from './util'` was never followed and the module reached nothing,
# while the identical file under any other name reached `util`. Naming a file after the
# thing it is about is the ordinary way to write a frontend.
THE_NAME_IS_A_BINDING = False

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
#
# The clause holds no quote, and that is what stops it spanning the statement after it. A
# clause may run over several lines — `import {\n  a,\n  b,\n} from './api'` is how a long
# one is written — so newlines cannot end it, and a side-effect import written above
# another one then swallowed it: `import './index.css'` third of the stock Vite four, with
# no semicolons anywhere, failed the file for an import on the next line "naming no
# module". No import clause TypeScript compiles carries a quote, so this cannot be wrong
# about legal source, and reordering two imports is about as ordinary an edit as there is.
_IMPORT_FROM = re.compile(
    r"(?<![\w$.])import[ \t\r\n]+(?:type[ \t\r\n]+)?([^;'\"]*?)[ \t\r\n]*"
    r"(?<![\w$.])from(?![\w$])[ \t\r\n]*(['\"])([^'\"\n]*)\2"
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
# The `*` is a generator's and belongs to `function` alone, which the lookbehind says: it
# was written for `function* load()` and, offered to every keyword, made `export type *
# from './other'` read as a declaration of a type called `from`, which the module then
# named on its card as a type a reader could go looking for and never find.
_DECLARES = re.compile(
    r"(?<![\w$.])(function|class|type|interface|enum|const|let|var)"
    r"(?:(?<=function)[ \t\r\n]*\*)?[ \t\r\n]+"
    r"(?!(?:extends|implements)(?![\w$]))([A-Za-z_$][\w$]*)"
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

# What stands in front of a name that is being built rather than called on. `new api.Thing()`
# writes exactly the characters a call on `api` writes, and is neither: it builds the thing
# the name qualifies, and nothing at all is called on `api`.
_PRECEDED_BY_NEW = re.compile(r"(?<![\w$.])new$")

# `<App />`, `<StrictMode>`: a JSX element naming a component is that component being
# used, which is what it compiles to — `createElement(App)` — and is coordination the
# same way `new` is. Only a name starting with a capital can be one: JSX reads a lowercase
# tag as an HTML element and never as a name in scope, which is React's own rule and the
# whole of what tells `<div>` from `<Deposits>`.
#
# A dotted name is one too. `<Icons.Chevron />` is how a component is taken out of a
# namespace import or off a compound component, and matched without the dot the name read
# was `Icons` — a construction of the namespace itself, which is not what the source says.
# Read whole it is `Icons.Chevron`, which names no module here and so reaches nothing, the
# same answer `new api.Thing()` gets and for the same reason. That is a floor rather than
# a fan line nobody can check, and it is named on the page beside the other floors.
_A_JSX_ELEMENT = re.compile(r"<[ \t\r\n]*([A-Z][\w$]*(?:\.[A-Za-z_$][\w$]*)*)(?=[ \t\r\n/>])")

# The name a `<` that opens a type argument list stands behind.
_A_NAME_BEHIND_IT = re.compile(r"[A-Za-z_$][\w$]*$")

# What a value can end with, other than a name: a closing bracket, a digit, or the quote
# that closes a literal. A `<` written after one of them is an operator on that value
# rather than a tag opening, which is what tells `if (0 < Max)` from `return <Max />`.
_A_VALUE_CAN_END_WITH = frozenset(")]0123456789'\"`")

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
    ["as", "async", "await", "case", "catch", "class", "delete", "do", "else", "for",
     "function", "if", "import", "in", "infer", "instanceof", "keyof", "new", "return",
     "satisfies", "super", "switch", "this", "throw", "typeof", "void", "while", "with",
     "yield"]
)

# The words a call cannot be written *against*, because none of them holds anything. A
# receiver is read as the name in front of a dot, and a construct this reading masks
# leaves the characters it stood for blank — so `return /x/.test(s)` put `return` in front
# of a dot with nothing but spaces in between and reported a call on a collaborator called
# `return`. Harmless only while nothing can be imported under that name; the same shape
# after any other masked construct attaches a real one to a call the source never wrote on
# it. `this` and `super` are held out because both really do hold something: `this.` is
# taken off before a receiver is read at all, and `super.pay()` is a call on what this was
# built on.
_NOT_A_RECEIVER = _NOT_A_CALL - frozenset(["this", "super"])

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

# What a `/` can follow and still open a regular expression. Three characters are
# deliberately not among them, and every one was a whole file off the page.
#
# `<` and `>`: `</div>` writes a `<` and then a `/`, and read as a regex it blanked the
# JSX tags after it on the same line. Nothing writes `a < /re/` on purpose.
#
# `}`: a JSX expression container is closed with one, and what follows it is very often a
# slash. `<li key={i} />` and `<span>{done} / {total}</span>` both write `} /`, and read as
# a regex the scan ran to the next slash on the line — the one in `</ul>` — blanking the
# `{` that opened the enclosing container and leaving the `}` that closes it. The file was
# then failed for braces that do not balance, which is not true of the source: rendering a
# list with `.map()` and a self-closing child carrying `key={...}` is the single most
# common line in React and `tsc --strict` compiles it without a word. What it costs is a
# regular expression written as the first thing after a block — `if (a) { b() }` and then
# `/x/.test(s)` on the next line — which is read as a division and left unblanked. That
# one is legal too, and rare enough that nothing in this repository or in the sweep of
# third-party source this reading was tried against writes it; a regex after a `(`, a `,`,
# an `=` or a `return`, which is where all of them are actually written, is unaffected.
_AFTER_WHICH_A_REGEX_CAN_START = frozenset("(,=:[!&|?;{+-*%~^")
_BEFORE_A_REGEX = frozenset(
    ["await", "case", "delete", "do", "else", "in", "instanceof", "new", "of", "return",
     "throw", "typeof", "void", "yield"]
)

# The words a string may be written straight up against, so that a quote touching one of
# them still opens one: `return'x'`, `typeof'x'`, `from'./api'`. Every other quote written
# against the end of a word is an apostrophe inside it — `don't`, `a customer's account`,
# `the 1970's` — because JavaScript has no syntax at all that puts a string against an
# identifier. So the rule cannot be wrong about legal source, and without it one
# apostrophe in JSX prose paired with the quote that opened a real string later on the
# same line and blanked every character between them, braces included: `<p>Don't miss
# it</p> && {label('key')}` lost its `{` and kept its `}`, and the file was failed for
# braces that do not balance — a reason that is not true of the source.
_A_STRING_CAN_FOLLOW = _BEFORE_A_REGEX | frozenset(
    ["as", "default", "export", "extends", "from", "import", "keyof", "satisfies"]
)

# What may stand between a JSDoc block and the declaration it documents: the keywords an
# export is written with, and nothing else. Anything with punctuation in it is a statement
# standing in between, and a block above that documents that one.
_NOTHING_BUT_WORDS = re.compile(r"[\s\w$]*")

# Where a declaration is written that this reading has no account of. Named rather than
# guessed at, because `declare` and `namespace` describe types that live somewhere else
# entirely and reading them as this module's own interface would be a claim about a file
# nobody pointed the tool at.
#
# `abstract` was on this list and did not belong: what `export abstract class Shape {}`
# describes is written right there in the file, and a plain `export class Shape {}` is
# merely named on the module and skipped. So the reason printed was untrue of the source
# and one abstract class anywhere took the whole file off the page.
_NOT_READ_HERE = ("declare", "namespace", "module")

# The words that stand between `export` and the declaration they modify, and say nothing
# about what a caller of it must learn. Stepped over so the declaration behind them is
# read on its own terms.
_MODIFIES_A_DECLARATION = ("async", "abstract")

# The words a declaration can carry where its own name would otherwise be written, and
# which are therefore not one. `export default class extends Base {}` writes one of them,
# and read as a name it put a type called `extends` on the module's card.
_STANDS_WHERE_A_NAME_WOULD = ("extends", "implements")

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

# The words that can only open a statement of their own, and so can never be written
# inside the value a `const` is assigned. They are where a declaration list ends in a file
# with no semicolons in it, so that `export const a = 1, b = () => {}` stops before the
# next declaration rather than reading a comma written further down the file as one of its
# own. `function`, `class` and `async` are deliberately not among them: `const f =
# function () {}` and `const g = async () => {}` each write one inside a value.
_OPENS_A_STATEMENT = re.compile(
    r"(?<![\w$.])(?:export|import|const|let|var|type|interface|enum|declare|return"
    r"|throw)(?![\w$])"
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

    A type variable a function type written *inside* this one introduces is not a type
    either, for the reason a generic method's own `<T>` is not: `pick: <T>(of: T[]) => T`
    asks the caller for a type they already hold, and `T` is a letter standing in for it.
    The Java side has the same rule one level up, where the declaration's own variables
    are filtered out by what read them.
    """
    if written in (NOTHING_RETURNED, INFERRED):
        return []
    holes = _type_variables_declared_in(written)
    return [
        name.rsplit(".", 1)[-1]
        for name in written_names_in(written)
        if name.rsplit(".", 1)[-1] not in holes
    ]


def _type_variables_declared_in(written):
    """The names a function type written inside this one opens for itself.

    Told from `Array<T>`, where `T` really is a type the caller meets, by what stands
    after the group and nothing else: a function type's type parameters are followed by
    the brackets its own parameters go in.
    """
    holes = set()
    for at, character in enumerate(written):
        if character != "<":
            continue
        closed = _after_angles(written, at)
        if closed is None or written[closed:].lstrip()[:1] != "(":
            continue
        holes.update(_type_parameters_in(written[at + 1:closed - 1]))
    return holes


def written_names_in(written):
    """Every type name inside a type as it was written, qualifiers and all.

    `Promise<Customer>` is `Promise` and `Customer`; `RecordedDeposit[]` is
    `RecordedDeposit`; `string | null` is both of them; `React.ReactNode` is kept whole,
    because what stands in front of the dot is part of the answer for anybody following
    the name rather than merely counting it.

    A parameter's name is not a type either, and there are two places it is written into
    one. In front of a colon — `{ id: number }`, `(signal: AbortSignal) => void` — and in
    front of an `is`: `asserts x is number` and `x is Customer` are type predicates, where
    `x` names the parameter the promise is about. Read without that rule the card named a
    type `x` a reader can go looking for and will never find, and the invented type was
    charged into the cost that leverage is divided by.
    """
    if written in (NOTHING_RETURNED, INFERRED):
        return []
    # A literal type is a value rather than a type, and this is where that gets decided
    # now that a type is printed as the source spelled it: `'one' | 'two'` names nothing a
    # caller goes and learns, and read with its characters in place it put two types on
    # the card that no reader could ever find.
    written = _without_literals(written)
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
        if re.match(r"is(?![\w$])", rest):
            # `x` in `x is Customer` and in `asserts x is number`: the parameter a type
            # predicate is about, which is a name in the signature and not a type in it.
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
        fields=_fields_in(masked, text, depth_of),
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
                "of the two forms this tool reads" % line_of(text, found.start())
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
    # Where each method's `export` was written, kept beside the methods themselves so that
    # the one reading taken over the whole list — an overload set's implementation
    # signature, which is only knowable once every export has been read — can name a line.
    written_at = []
    for found in _AN_EXPORT.finditer(masked):
        if depth_of[found.start()] != 0:
            continue
        _read_one_export(
            masked, text, matching, documented, module, found.end(), methods, types, jsx,
            declared_at,
        )
        written_at.extend([found.start()] * (len(methods) - len(written_at)))
    for found in _DECLARES.finditer(masked):
        # A type the file declares but does not export is still a type declared inside
        # this module, and is named on it exactly as a nested Java type is named on the
        # class that holds it.
        if depth_of[found.start()] == 0 and found.group(1) in ("type", "interface", "enum", "class"):
            if found.group(2) not in types:
                types.append(found.group(2))
    return _Exports(
        _without_overload_implementations(methods, written_at, text),
        sorted(set(types)),
    )


# What is written on the one signature of an overload set a caller can never call. It is a
# visibility because that is the question being answered — whether a caller can reach it —
# and `private` is the word the scoring rules already read for "they cannot". The method
# stays on the module under it: a flow is walked through the body, and the body is here.
NOT_A_CALLER_S_TO_CALL = "private"


def _without_overload_implementations(methods, written_at, text):
    """The same methods, with an overload set's implementation signature hidden from callers.

    TypeScript never exposes it. `export function ring(id: string): string` and `export
    function ring(id: number): string` written above `export function ring(id: string |
    number): string { ... }` give a caller two calls they can make, not three, and the
    third is the one the implementation is written under — a signature `tsc` refuses to
    let anybody call. Read as a third method it was charged a method and a parameter, the
    card named a call a reader can go looking for and will never be able to make, and the
    inflated cost was the denominator leverage is divided by.

    This is a place where measuring both languages by the same rules means reading two
    shapes differently rather than the same, and the reason is in the languages: every
    Java overload is genuinely callable, and one TypeScript overload in every set is not.

    What tells them apart is already read — `has_a_body` — and it is asked only where a
    name is exported more than once, which in TypeScript can be nothing but an overload
    set. The implementation is kept among the methods rather than dropped, marked as one
    no caller can reach: a flow is a path through bodies, and dropping it would take the
    only body this function has off every flow that runs through it.

    Its `@throws` goes on the signatures that carry none of their own, which is what the
    language service does with it — an overload with no documentation of its own shows the
    implementation's. Left behind instead, a module that documented a refusal exactly once,
    where TypeScript wants it written, would be accused of raising one it never promised.
    """
    grouped = {}
    for at, method in enumerate(methods):
        grouped.setdefault(method.name, []).append(at)
    hidden = set()
    promised = {}
    for name, group in grouped.items():
        implementing = [at for at in group if methods[at].has_a_body]
        if len(group) < 2 or not implementing or len(implementing) == len(group):
            continue
        hidden.update(implementing)
        promised[name] = tuple(
            refusal
            for at in implementing
            for refusal in methods[at].documented_refusals
        )
        for at in implementing:
            _decline(
                text, written_at[at] if at < len(written_at) else 0, name,
                "it is the implementation signature of an overload set, and TypeScript "
                "never lets a caller call one: the %d signature(s) written above it are "
                "what a caller can reach, and this one stays on the module unpriced so "
                "that a flow still has a body to walk through"
                % (len(group) - len(implementing)),
            )
    if not hidden:
        return methods
    return [
        _hidden_from_callers(method) if at in hidden
        else _also_documenting(method, promised.get(method.name, ()))
        for at, method in enumerate(methods)
    ]


def _hidden_from_callers(method):
    """The same method, marked as one no caller of this module can reach."""
    return Method(
        name=method.name,
        visibility=NOT_A_CALLER_S_TO_CALL,
        parameters=method.parameters,
        returns=method.returns,
        type_parameters=method.type_parameters,
        annotations=method.annotations,
        documented_refusals=method.documented_refusals,
        has_a_body=method.has_a_body,
        calls=method.calls,
    )


def _also_documenting(method, refusals):
    """The same method, carrying these refusals where it documents none of its own."""
    if method.documented_refusals or not refusals:
        return method
    return Method(
        name=method.name,
        visibility=method.visibility,
        parameters=method.parameters,
        returns=method.returns,
        type_parameters=method.type_parameters,
        annotations=method.annotations,
        documented_refusals=refusals,
        has_a_body=method.has_a_body,
        calls=method.calls,
    )


def _read_one_export(masked, text, matching, documented, module, position, methods, types,
                     jsx, declared_at):
    """Read whatever this one `export` opens, or fail the file naming what stopped it."""
    token, start, after = _token_at(masked, position)
    default = token == "default"
    if default:
        token, start, after = _token_at(masked, after)
    while token in _MODIFIES_A_DECLARATION:
        token, start, after = _token_at(masked, after)
    if token in _NOT_READ_HERE:
        raise ParseFailure(
            "the export on line %d is written `export %s`, which this tool has no reading "
            "of: what it describes is declared somewhere this tool was not pointed at"
            % (line_of(text, start), token)
        )
    if token == "function":
        method = _function_from(masked, text, after, matching, documented, module, jsx)
        if method is not None:
            methods.append(method)
        return
    if token == "class":
        named, _, _ = _token_at(masked, after)
        if not _a_declared_name(named):
            # `export default class { }` and `export default class extends Base { }`.
            # Legal, and there is no name for the card to carry: read without asking, the
            # word after the keyword was recorded as the type this module declares, so the
            # page named a type called `{` that a reader can go looking for and will never
            # find.
            _decline(text, start, named, "a class exported with no name of its own "
                     "leaves nothing for a reader to go and look up, so nothing is named "
                     "on this module for it")
            return
        _decline(text, start, named, "an exported class is named on this module rather "
                 "than measured, exactly as a type declared inside a Java module is: what "
                 "it costs a caller is its members, and its members are read nowhere")
        if named not in types:
            types.append(named)
        return
    if token in ("type", "interface", "enum"):
        named, _, _ = _token_at(masked, after)
        if not _a_declared_name(named):
            # `export type Deposit` is a declaration; `export type { Deposit } from
            # './api'` and `export type * from './other'` are re-exports that happen to
            # start with the same word. Read without asking, the word after the keyword
            # was taken for a name whatever it was, and `{`, `*` and `from` each went onto
            # the card as a type this module declares.
            _decline(text, start, named, "a type-only export list hands on declarations "
                     "written somewhere else — in another file where a `from` follows it, "
                     "and above in this one otherwise, where each is already named on "
                     "this module by the declaration itself")
            return
        if named not in types:
            types.append(named)
        return
    if token in ("const", "let", "var"):
        if token == "const" and _token_at(masked, after)[0] == "enum":
            # `export const enum Direction { Up }` declares a type, not a binding. Read as
            # one, the name after the keyword was the word `enum` and the declined log
            # carried a line about an export nobody wrote.
            token, start, after = _token_at(masked, after)
            named, _, _ = _token_at(masked, after)
            if _a_declared_name(named) and named not in types:
                types.append(named)
            return
        methods.extend(
            _bindings_from(masked, text, after, matching, documented, module, jsx)
        )
        return
    if token == "{":
        # `export { ring, of as price }`, and `export { ring } from './till'`. The first
        # names things this file declares and is read as those declarations; the second
        # hands on what another file declares, whose signature is written there.
        closed = masked.find("}", after)
        if closed < 0:
            raise ParseFailure(
                "the export on line %d could not be read: nothing closes the list of "
                "names it opens" % line_of(text, start)
            )
        elsewhere = _token_at(masked, closed + 1)[0] == "from"
        for part in split_on_commas(masked[after:closed]):
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
        "has no reading of" % (line_of(text, start), token)
    )


def _a_declared_name(token):
    """Whether this word is a name a declaration was given, rather than what stands where one would.

    Both `class` and `type` can be followed by something that is not a name at all —
    `export default class { }`, `export type { Deposit } from './api'` — and one of them
    can be followed by a word that is a keyword rather than a name, in `export default
    class extends Base { }`. Asked, each of those is declined by name and the module
    carries no type nobody can look up; not asked, `{`, `*` and `extends` were each
    recorded as a type this module declares and drawn on its card.
    """
    return bool(token) and re.match(r"[A-Za-z_$][\w$]*$", token) is not None and (
        token not in _STANDS_WHERE_A_NAME_WOULD
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
        line_of(text, position),
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
            "should" % (name, line_of(text, opened), token)
        )
    closed = after_balanced(masked, opened)
    parameters = _parameters_in(masked, text, opened + 1, closed - 1, name, annotated)
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


def _bindings_from(masked, text, position, matching, documented, module, jsx):
    """Every declarator one exported `const`, `let` or `var` writes, read one at a time.

    `export const a = 1, b = () => {}` exports two names, and `b` is a function anybody can
    import. Read only as far as the first declarator, `b` was in `methods`, in `types` and
    in the declined log alike — nowhere at all, which is the one thing this reading must
    never do with something a caller can reach: an export left out is an interface cheaper
    than the source makes it, and there is nothing to fail on because the source is legal.
    """
    methods = []
    at = position
    while at is not None:
        method = _binding_from(masked, text, at, matching, documented, module, jsx)
        if method is not None:
            methods.append(method)
        at = _next_declarator(masked, at)
    return methods


def _next_declarator(masked, position):
    """Where the declarator after this one starts, or None when this was the last of them.

    A comma written outside every bracket is what separates two of them, and in a
    declaration list it can be nothing else: the comma operator needs brackets of its own
    — `const a = (1, 2)` — and a comma inside a type, a destructuring pattern, an argument
    list or a body is inside one.

    Three things end the list. The `;` that closes the statement is the written one. Where
    the source writes no `;` at all, a word that can only open a statement of its own does
    it instead: `function`, `class` and `async` are deliberately not among them, because
    `const f = function () {}` and `const g = async () => {}` each write one inside a
    value. And a bracket closing something this list never opened is the brace holding the
    whole declaration, which ends it whatever else is written after.

    A `<` is a bracket only where a *name* stands in front of it and a `>` closes it before
    this declaration ends, and that is the whole of how `const a = new Map<string,
    number>(), b = 2` is told from `const flag = 1 < 2, b = (x: number): number => x`.
    Counted as a bracket outright, the comparison in the second left the depth above zero
    for the rest of the scan, the comma that separates the two declarators was never seen,
    and `b` — a function a caller can import — was left out of the interface with no line
    in any log saying so. Read the other way round, as no bracket at all, the comma inside
    the first one's type arguments would split a declarator the source never wrote.

    The name in front is what settles it, and it is the same question `_opens_type_arguments`
    asks about a `<` in a `.tsx` file: type arguments are written against something, and a
    comparison is written against a value that very often is not a name — `1`, `0`,
    `items.length`. The end of the declaration is the bound rather than the end of the
    line, because a long type argument list is wrapped over several of them, and bounding
    it at the newline read the comma inside a wrapped `new Map<\n  string,\n  number\n>()`
    as a declarator separator: `q` beside it was still measured and no number moved, but
    the log `_decline` calls the complete list of what was skipped carried a line about an
    export called `number` that this file does not contain.
    """
    depth = 0
    at = position
    ends_at = _end_of_the_declaration(masked, position)
    while at < len(masked):
        character = masked[at]
        if character == "<":
            closed = (
                _after_angles(masked, at, ends_at)
                if _A_NAME_BEHIND_IT.search(masked[:at].rstrip()) is not None
                else None
            )
            if closed is not None:
                at = closed
                continue
            at += 1
            continue
        if character in "([{":
            depth += 1
        elif character in ")]}":
            if depth == 0:
                return None
            depth -= 1
        elif depth == 0:
            if character == ",":
                return at + 1
            if character == ";":
                return None
            if (character.isalpha() or character in "_$") and _OPENS_A_STATEMENT.match(
                masked, at
            ):
                return None
        at += 1
    return None


def _end_of_the_declaration(masked, position):
    """How far a `<` written inside this declaration list may be looked for a partner.

    A type argument list never spans a statement, so the statement is the bound: the `;`
    that closes this one, the bracket that closes whatever holds it, or the word that can
    only open the next one. Without a bound at all a `<` written as a comparison pairs
    happily with the `>` of some generic further down the file and steps over everything
    in between, which is the whole reason `_after_angles` asks for one.
    """
    depth = 0
    at = position
    while at < len(masked):
        character = masked[at]
        if character in "([{":
            depth += 1
        elif character in ")]}":
            if depth == 0:
                return at
            depth -= 1
        elif depth == 0:
            if character == ";":
                return at
            if (character.isalpha() or character in "_$") and _OPENS_A_STATEMENT.match(
                masked, at
            ):
                return at
        at += 1
    return len(masked)


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
                "parameters should" % (name, line_of(text, opened), token)
            )
    closed = after_balanced(masked, opened)
    parameters = _parameters_in(masked, text, opened + 1, closed - 1, name, annotated)
    returns = INFERRED
    token, at, after = _token_at(masked, closed)
    if token == ":":
        _, at = _up_to_the_arrow(masked, after)
        returns = normalised(_as_written(text, masked, after, at))
        token, at, after = _token_at(masked, at)
    if token != "=>":
        raise ParseFailure(
            "the function %s on line %d could not be read: %r stands where its arrow "
            "should" % (name, line_of(text, at), token)
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
    the one written outside them all. An arrow's `>` closes nothing, by the one rule every
    scanner here reads: counted as a bracket, the `>` in a return type of `(() => void)`
    closed the group its own `(` had opened, and the arrow that ends the type was never
    found.
    """
    depth = 0
    at = position
    while at < len(masked):
        if depth == 0 and masked.startswith("=>", at):
            return masked[position:at], at
        character = masked[at]
        if character in "(<[{":
            depth += 1
        elif character in ")>]}" and not ends_an_arrow(masked, at):
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
            % line_of(text, position)
        )
    return closed, _type_parameters_in(masked[position + 1:closed - 1])


def _after_angles(masked, position, limit=None):
    """Just past the `>` that closes the `<` here, or None when nothing closes it.

    Non-raising, because it is asked the question as well as answered with it: whether a
    `<` opens a generic's type parameters at all is settled by what stands after the
    group, and a `<` that closes nowhere is a `<` that opens none. The `>` of an `=>`
    closes none either — `<T = () => void>` writes a default that is a function type —
    which is the same rule every other scanner here reads.

    `limit` is how far the partner may be looked for, and a caller reading a `<` written
    inside a *value* has to give one: `const flag = 1 < 2` writes a comparison, and asked
    to search the whole file this would happily pair it with the `>` of some generic
    fifty lines further down and step over everything in between.
    """
    depth = 0
    at = position
    end = len(masked) if limit is None else min(limit, len(masked))
    while at < end:
        if masked[at] == "<":
            depth += 1
        elif masked[at] == ">" and not ends_an_arrow(masked, at):
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
    for part in split_on_commas(inside):
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
            "its body should" % (name, line_of(text, at), token)
        )
    written = after
    scan = after
    while True:
        opened = _first_brace_at_depth_zero(masked, scan)
        finished = _where_the_signature_ends(masked, scan)
        if finished is not None and (opened is None or finished < opened):
            return normalised(_as_written(text, masked, written, finished)), None
        if opened is None:
            raise ParseFailure(
                "the return type of %s on line %d could not be read: nothing closes it"
                % (name, line_of(text, at))
            )
        if opened not in matching:
            raise ParseFailure(
                "the return type of %s on line %d could not be read: a brace in it is "
                "never closed" % (name, line_of(text, at))
            )
        following = _token_at(masked, matching[opened] + 1)[0]
        if masked[written:opened].strip() and following != "{":
            return (
                normalised(_as_written(text, masked, written, opened)),
                (opened + 1, matching[opened]),
            )
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
    """Where this character is first written outside every bracket, from `position` on.

    The `>` of an `=>` closes nothing, and that is the whole of what stands between this
    scanner and every `function` whose return type is written as one. Counted as a
    bracket, the `>` in `function useToggle(): [boolean, () => void]` took the depth below
    zero, this returned None, and `_returns_and_body` failed the file for a return type
    "nothing closes" — 1545 lines off the page for source `tsc` compiles without a word.
    """
    depth = 0
    at = position
    while at < len(masked):
        character = masked[at]
        if character == wanted and depth == 0:
            return at
        if character in "(<[":
            depth += 1
        elif character in ")>]" and not ends_an_arrow(masked, at):
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


def _parameters_in(masked, text, begin, ends_at, name, annotated=False):
    """Every parameter of one function, by the type the source wrote for it.

    Read off the masked text and spelled off the original, which is why this takes two
    offsets rather than the slice between them: a comma inside a string literal must not
    split a parameter list, and the type printed on the card must be the one the file
    wrote. `spans_between_commas` gives the same cuts for both.

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
    inside = masked[begin:ends_at]
    parameters = []
    for span in spans_between_commas(inside):
        start, stops = _trimmed(inside, *span)
        written = inside[start:stops]
        if not written:
            continue
        if written.startswith("..."):
            start, stops = _trimmed(inside, start + 3, stops)
            written = inside[start:stops]
        colon = _first_in_the_open(written, ":")
        equals = _first_in_the_open(written, "=")
        if colon is None or (equals is not None and equals < colon):
            if equals is not None or annotated:
                parameters.append(INFERRED)
                continue
            raise ParseFailure(
                "a parameter of %s on line %d could not be read: %r has no type written "
                "on it" % (name, line_of(text, begin), written[:40])
            )
        called = written[:colon].strip().rstrip("?").strip()
        if called == "this":
            # The receiver a method may name. Not a parameter a caller passes at all.
            continue
        default = _first_in_the_open(written[colon + 1:], "=")
        ends = stops if default is None else start + colon + 1 + default
        parameters.append(
            normalised(_as_written(text, masked, begin + start + colon + 1, begin + ends))
        )
    return tuple(parameters)


def _trimmed(inside, start, stops):
    """The same span with the whitespace at either end of it left out."""
    start += len(inside[start:stops]) - len(inside[start:stops].lstrip())
    return start, start + len(inside[start:stops].rstrip())


# A string literal, written or blanked. `_mask_quoted` leaves the two quotes exactly where
# the source wrote them and blanks only what is between them, so one pattern finds a
# literal in the masked text and in the original alike — which is the whole of how a type
# is measured off the masked source and then spelled off the real one.
_A_STRING_LITERAL = re.compile(r"'[^'\n]*'|\"[^\"\n]*\"")


def _as_written(text, masked, start, ends_at):
    """This much of the file as the source spelled it, minus everything masking took out.

    Only the string literals come back, and only the ones the mask left its quotes around.
    A comment stays blanked, because a comment written inside a type is not part of the
    type; a template literal and a regular expression stay blanked because neither is
    legal in one.

    It exists for what a card prints. A type is *measured* off the masked text — a comma
    or a brace inside a string must never split a parameter list or open a body — and then
    printed, and printed off the masked text a parameter written `a: 'one' | 'two'` came
    out as `' ' | ' '`. Nothing moved and no file failed, so the only thing wrong with it
    was that a reader checking the card against the source would find a type the file does
    not contain.
    """
    return _A_STRING_LITERAL.sub(
        lambda found: text[start + found.start():start + found.end()],
        masked[start:ends_at],
    )


def _without_literals(written):
    """The same written type with every string literal in it replaced by a space.

    For the two readings that want the names in a type rather than its spelling. A literal
    type names no type at all — `'one' | 'two'` is two values — and read after
    `_as_written` has put its characters back, `one` and `two` would each be charged to a
    caller as a type to go and learn.
    """
    return _A_STRING_LITERAL.sub(" ", written)


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


def _fields_in(masked, text, depth_of):
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
        written, ends_at = _up_to_the_assignment(masked, after)
        if written.strip():
            found.append(
                Field(match.group(2), normalised(_as_written(text, masked, after, ends_at)))
            )
    return tuple(found)


def _up_to_the_assignment(masked, position):
    """A written type that ends where the value assigned to it begins.

    A newline ends it, because a `const` with no `=` on it at all is one this reading has
    to stop somewhere. But a type written over two lines is joined by an operator, and
    then the newline ends nothing: `const held: Till |\n  Receipt = make()` came back as
    the type `Till |`, so a call written through `held` drew a fan line to one of the two
    names the source wrote and the evidence printed on the card was the string `Till |`.
    So the newline is stepped over whenever a joiner stands on either side of it, which
    are the only two ways a type can be continued onto the next line at bracket depth
    zero. The `=` and the `;` still end it, so a type that runs on cannot run away.
    """
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
        elif character in ")>]}" and not ends_an_arrow(masked, at):
            depth -= 1
        elif depth == 0 and character == "\n" and _a_type_carries_on_over(masked, at):
            at += 1
            continue
        elif depth == 0 and character in "=;\n":
            return masked[position:at], at
        at += 1
    return masked[position:], at


# What joins the two halves of a type written over more than one line: a union, an
# intersection, and the comma between two type arguments. Written at the end of the first
# line or at the start of the second, which are the two places a formatter puts it.
_JOINS_TWO_HALVES_OF_A_TYPE = ("|", "&", ",")


def _a_type_carries_on_over(masked, newline):
    """Whether the type being read continues past this newline rather than ending at it."""
    return (
        _the_character_before(masked, newline) in _JOINS_TWO_HALVES_OF_A_TYPE
        or _the_character_after(masked, newline) in _JOINS_TWO_HALVES_OF_A_TYPE
    )


def _the_character_before(masked, position):
    """The last character written before this one that is not whitespace, or the empty string."""
    at = position - 1
    while at >= 0 and masked[at] in " \t\r\n":
        at -= 1
    return masked[at] if at >= 0 else ""


def _the_character_after(masked, position):
    """The first character written after this one that is not whitespace, or the empty string."""
    at = position + 1
    while at < len(masked) and masked[at] in " \t\r\n":
        at += 1
    return masked[at] if at < len(masked) else ""


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
                "a value stands in front of them, so the < is an operator on it — that "
                "name's type arguments, or a comparison against it — rather than a tag",
            )
            continue
        found.append(match)
    return found


def _opens_type_arguments(before):
    """Whether the `<` after this text is anything other than a tag opening.

    What stands in front of it is the whole of the answer, the same way it is for a slash
    that might open a regular expression, and the question it really answers is whether a
    *value* has just been written. A value in front means the `<` is an operator on it —
    either a generic's type arguments, `useState<Customer | null>(null)`, or a comparison,
    `if (0 < Max)` — and neither builds anything. Nothing in front of it means a value is
    about to be written, and `<Card />` is one.

    Four things count as a value ending here. A name is one, unless it is a word a value
    can follow, because `return <Card />` is a name in front of a tag and nothing else. A
    closing bracket is one. A digit is one, and it is the one this reading missed: `if (0
    < Max && n > 1)` against an imported `Max` put a construction of `Max` in the fan of a
    file that builds nothing, because a number is not a name and `0` is not a bracket. A
    quote is the fourth, for the same reason a digit is — the masking leaves a literal's
    own quotes in place, so `'a' < b` ends in one.

    Everything else is punctuation an expression can follow, `>` included: a `>` ends a JSX
    opening tag as surely as it ends a type argument list, and `<div><Card /></div>` is how
    the nesting is written.
    """
    trimmed = before.rstrip()
    if not trimmed:
        return False
    name = _A_NAME_BEHIND_IT.search(trimmed)
    if name is not None:
        return name.group(0) not in _BEFORE_A_REGEX
    return trimmed[-1] in _A_VALUE_CAN_END_WITH


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
    receivers = _receivers_in(plain)
    called = set()
    declares = {found.group(2) for found in _DECLARES.finditer(masked)}
    for found in _A_CALL.finditer(masked):
        name = found.group(1)
        if name in _NOT_A_CALL:
            continue
        before = masked[:found.start(1)].rstrip()
        if before.endswith("."):
            continue
        if _PRECEDED_BY_NEW.search(before):
            # `new SignInFailed()` puts a name in front of brackets with nothing but
            # `new` in front of the name, so it reads as a bare call to something spelled
            # `SignInFailed`. It is a construction, reported as one below, and read as a
            # call as well it drew a fan line whose evidence said "calls SignInFailed,
            # which this file imports from it" over a file that calls nothing — a
            # sentence a reader can check against the source and find false. The Java
            # side has refused this reading since it was written.
            continue
        (declares if _declares_rather_than_calls(masked, found) else called).add(name)
    constructed = set(_CONSTRUCTED.findall(plain)) | {
        match.group(1) for match in _jsx_elements_in(masked, jsx)
    }
    qualified = sorted(name for name in constructed if "." in name)
    if qualified:
        # The floor this reading takes on a dotted name, said out loud once per file
        # rather than once per element. `<Icons.Chevron />` and `new api.Thing()` each
        # name a member of something the file holds, and the name written is not one this
        # graph can resolve to a module — so the fan is shorter than the source here, and
        # a reader checking one against the file can see where.
        log.debug(
            "qualified names read as reaching no module here names=%s reason=%s",
            ",".join(qualified),
            "each names a member of something this file holds rather than a module, so "
            "there is nothing to follow it to",
        )
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


def _declares_rather_than_calls(text, match):
    """Whether the name this call pattern matched is being declared rather than called.

    The same question the Java side asks, answered off the other end of the brackets
    because TypeScript writes the answer there. A declaration says what it hands back
    after its parameter list — `save(id: string): void` in an `interface`, in a `type`, in
    an object type written straight into an annotation, or on a class — and a call says
    nothing there. A method written with a body and no return type is the second spelling:
    `save(id) { ... }` opens a brace where a call would have finished.

    Read without asking, every member signature in every type declaration in a file was a
    call. `export interface Api { save(id: string): void }` in a file that also writes
    `import { save } from './repo'` drew a fan line reading "calls save, which this file
    imports from it" over a file whose truthful reach is nothing at all — the fan line
    invented outright rather than merely misattributed.

    Two spellings of a real call are answered "declaration" and go uncounted: a call
    inside a conditional's first arm, `ok ? save(id) : none`, and a `case save():` in a
    switch. Both leave a fan shorter than the source, which is the direction this tool is
    willing to be wrong in and the same trade the Java side names — where the two cannot
    be told apart, this answers "declaration", because the other answer draws a line to a
    card the module never calls.

    A `function` declaration is answered by the same rule off the same end — `function
    f(): void` writes a return type and `function f() {` writes a body — which is why the
    word in front of it no longer has to be looked at separately.
    """
    rest = text[after_balanced(text, match.end() - 1):].lstrip()
    return bool(rest) and rest[0] in ":{"


def _receivers_in(plain):
    """What calls in this file were written against, a qualified `new` left out.

    `new api.Thing()` writes exactly the characters a call on `api` writes, and is
    neither: it builds the thing the name qualifies, and nothing at all is called on
    `api`. Read as a receiver it drew a fan line whose evidence said "called on api" over
    a file that calls nothing on it — a sentence a reader can check against the source and
    find false, which the Java side calls the one failure it exists to make impossible and
    which this reading reintroduced by building receivers with no guard. The ones declined
    are logged: nothing is dropped here without a word.

    A reserved word is not a receiver either, and that one is the masking showing through:
    a construct blanked out leaves the characters it stood for as spaces, so `return
    /x/.test(s)` reads as the word `return`, a gap, and `.test(`, and was reported as a
    call on a collaborator called `return`. Nothing can be imported under that name, so
    this one drew no line — but the same shape after any other masked construct attaches a
    real name to a call the source never wrote on it.
    """
    found = set()
    for match in _A_RECEIVER_CALL.finditer(plain):
        if _PRECEDED_BY_NEW.search(plain[:match.start(1)].rstrip()):
            log.debug(
                "name not read as a receiver name=%s reason=%s",
                match.group(1),
                "new is written in front of it, so it qualifies the thing being built "
                "rather than holding something a call was written on",
            )
            continue
        if match.group(1) in _NOT_A_RECEIVER:
            log.debug(
                "name not read as a receiver name=%s reason=%s",
                match.group(1),
                "it is a reserved word, which holds nothing for a call to be written on: "
                "what stood between it and the dot is a construct this reading masked",
            )
            continue
        found.add(match.group(1))
    return found


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
        if _PRECEDED_BY_NEW.search(body[:match.start(1)].rstrip()):
            continue
        if receiver in _NOT_A_RECEIVER:
            # The same reading `_receivers_in` declines, declined here too so that a flow
            # and a fan cannot disagree about one line of source.
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
        if before.endswith(".") or _PRECEDED_BY_NEW.search(before):
            continue
        if _declares_rather_than_calls(body, match):
            continue
        opened = match.end() - 1
        found.append((match.start(1), after_balanced(body, opened), CallSite(None, name)))
    for match in _CONSTRUCTED.finditer(body):
        found.append(
            (match.start(), after_the_arguments(body, match.end(1)),
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
            % line_of(text, ended)
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
            "a block comment opened on line %d is never closed" % line_of(text, position)
        )
    if text.startswith("/**", position):
        documentation.append((position, ends_at + 2))
    _blank(text, out, position, ends_at + 2)
    return ends_at + 2


def _mask_quoted(text, out, position):
    """The string starting here, blanked, or the lone quote blanked when it opens none.

    Two things say a quote opens no string, and neither can be wrong about source `tsc`
    compiles. One is a quote with no partner on its line, because JavaScript strings do
    not span lines — without it a single apostrophe in JSX prose blanked out the rest of
    the file and took every declaration after it along. The other is a quote written
    against the end of a word, because nothing JavaScript compiles puts a string there:
    `Don't` is a word with an apostrophe in it, and read as a string it reached forward to
    the quote that opened a real one later on the line and blanked the code in between.
    A handful of reserved words really can be written against a string — `return'x'`,
    `from'./api'` — and they are named rather than guessed at.
    """
    quote = text[position]
    if _an_apostrophe_inside_a_word(text, position):
        out[position] = " "
        return position + 1
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


def _an_apostrophe_inside_a_word(text, position):
    """Whether the quote here stands at the end of a word rather than opening a string.

    What is written immediately in front of it is the whole of the answer, the same way it
    is for a slash that might open a regular expression. An identifier or a number means
    prose, because `total'x'` is not something JavaScript compiles; one of the few
    reserved words a string may be written straight after means a string.
    """
    at = position
    while at > 0 and (text[at - 1].isalnum() or text[at - 1] in "_$"):
        at -= 1
    return at < position and text[at:position] not in _A_STRING_CAN_FOLLOW


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
                    % line_of(text, at)
                )
            out[closed] = " "
            at = closed + 1
            continue
        if character != "\n":
            out[at] = " "
        at += 1
    raise ParseFailure(
        "a template literal opened on line %d is never closed" % line_of(text, position)
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
                    % line_of(text, position)
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
