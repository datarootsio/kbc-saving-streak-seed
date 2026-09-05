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

The other side of the seam is read the same way and weighed just as little: the fields a
module holds, the names it writes a call against, the types it builds one of, and the
imports that say which module each of those names means. All four are sets of names rather
than counts of occurrences, because what they feed — a module's reach — must not be
movable by writing the same call again.

One decision cannot fail loudly, and it is where every fault found on this branch got in:
deciding that a member which reads like a method is not one. A field, a constructor and a
nested record all read like one and legitimately are not, so there is nothing to fail on —
and a method wrongly declined leaves an interface quietly cheaper than the source makes
it. So it is logged instead: `grep "member not read as a method"` at DEBUG lists every
member declined, its line, and why, and that list is short enough to read.
"""

import bisect
import logging
import re

log = logging.getLogger("module_depth_map.javasource")

_PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)

# What a file says it is allowed to spell by simple name. `import static a.b.C.m` names a
# member of a type; `import a.b.*` names no type at all and is carried as the package it
# opens, so a name used in the body can be tried against it.
_IMPORT = re.compile(r"^\s*import\s+(static\s+)?([\w.]+(?:\.\*)?)\s*;", re.M)

# A call written against something the source names: `deposits.save(...)`, `Money.of(...)`.
# Only the name in front of the dot is taken, because that is the thing being reached; what
# is called on it is its own business.
#
# `new Holder.Row()` writes those same characters — a name, a dot, a name, a bracket — and
# calls nothing on `Holder`. It builds a type nested inside it, which `_CONSTRUCTED` below
# already reports as `Holder.Row` and which whoever resolves it declines, a nested type
# being no module. Read as a receiver as well, the enclosing name went into the fan under
# the evidence string `called on Holder` for a file that calls nothing on `Holder`
# anywhere. `_PRECEDED_BY_NEW` is asked about every match, so the ones declined can be
# said out loud rather than dropped in silence.
_A_RECEIVER = re.compile(r"(?<![\w.$])([A-Za-z_$][\w$]*)\s*\.\s*[A-Za-z_$][\w$]*\s*\(")

# The same call read for a *flow* rather than for reach, which needs both halves of it:
# what it was written against and what it called. Reach is a set of distinct things and
# has no use for the method's name; a flow is a path and cannot be walked without it,
# because the next module along is decided by which method was called and not merely by
# which module holds it.
_A_RECEIVER_CALL = re.compile(
    r"(?<![\w.$])([A-Za-z_$][\w$]*)\s*\.\s*([A-Za-z_$][\w$]*)\s*\("
)

# The `new` in front of `new Holder.Row()`, looked for behind a receiver rather than
# folded into the pattern above so that a declined match can be logged with its name.
_PRECEDED_BY_NEW = re.compile(r"(?<![\w.$])new$")

# `new Deposit(...)`, `new java.util.ArrayList<>()`: the type a body builds one of, kept
# exactly as it was written. A name written out in full is carried with its package on it
# rather than cut back to `ArrayList` — cutting it back is how `new other.Receipt()` was
# read as building this package's own `Receipt`, a different module, of a different kind,
# with an evidence string saying so. Whoever resolves it decides what a dotted spelling
# can mean; this file only reports what the source wrote.
#
# `new Receipt[10]` is not one of these. It writes the same three tokens and builds zero
# `Receipt`s — an array of that many nulls — so reading it as a construction credited a
# module with writing a record nothing had written. What follows the name is what tells
# the two apart, and `_AN_ARRAY_CREATION` below is asked about every match rather than
# folded into this pattern, so the ones declined can be said out loud.
_CONSTRUCTED = re.compile(r"(?<![\w.$])new[ \t\r\n]+([A-Za-z_$][\w$.]*)")

# What stands between the type of an array creation and its size: nothing but space, and
# then the bracket. `new Receipt[10]`, `new Receipt[] {a, b}`, `new int[n][m]`.
_AN_ARRAY_CREATION = re.compile(r"\s*\[")

# A name with a call's brackets after it and nothing in front of the name. Read only so
# that a member imported statically — `asMoney(amount)` — can be followed back to the type
# it was imported from; everything else it matches is a name nothing ever asks about.
#
# It matches a declaration too. `long of(long cents)` puts brackets after a name with
# nothing in front of the name either, and so does every constructor, every nested
# record's header and every method of an anonymous class. Which is which is decided by
# `_declares_rather_than_calls` below, because reading a declaration as a call is how a
# module gets credited with reaching something it never called.
_A_CALL = re.compile(r"(?<![\w.$])([A-Za-z_$][\w$]*)\s*\(")

# What a declaration writes in front of the name and a call never does: a type. So the
# thing immediately before the name decides it — a word that is not one of the words below
# is the return type or the modifier of a declaration, and `>` or `]` is the end of one
# spelled `Map<String, Long>` or `int[]`. Everything else — `;`, `{`, `=`, `(`, `,`, an
# operator, `->` — is punctuation only an expression can follow, so the name after it is
# a call.
#
# Where the two readings are genuinely ambiguous this answers "declaration", because the
# only thing that answer costs is a static import going uncounted, and the other answer
# costs a fan line to a card the module never calls.
_ENDS_A_TYPE = ("]", ">")
_A_LAMBDA_ARROW = "->"

# The words Java writes brackets after that are not calls. Listed so that `if`, `while`
# and their kind are not carried as things a module called.
_NOT_A_CALL = frozenset(
    ["assert", "case", "catch", "do", "else", "for", "if", "new", "return", "super",
     "switch", "synchronized", "this", "throw", "while", "yield"]
)

# `this.deposits.save(...)` reaches exactly what `deposits.save(...)` reaches. Taken off
# before a receiver or a construction is read, so one module writing both spellings is not
# two collaborators. Never before a *bare* call is read: `this.of(1)` calls a method this
# module has, declared or inherited, and stripping the `this.` first made it look like a
# call to a statically imported `of` — a fan line to the type the import came from, which
# is the one thing `this.of(1)` cannot mean.
_THROUGH_THIS = re.compile(r"(?<![\w.$])this[ \t\r\n]*\.[ \t\r\n]*")

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
# `@interface` may be written `@ interface`, which the JLS allows and which the pattern
# read as a plain interface: the module arrived as kind `interface`, so a rule written
# about annotations could never fire on it and the page called it one.
_TYPE = re.compile(
    r"(?<![\w.$])(class|interface|enum|record|@[ \t\r\n]*interface)"
    r"[ \t\r\n]+([A-Za-z_$][\w$]*)"
)

# `class`, `interface` and `enum` are reserved words: outside a class literal they can
# only ever open a declaration. Any one of them the pattern above did not match is Java
# this parser cannot read, and the file is failed by name rather than quietly shrunk.
# `record` is left out on purpose — it is a contextual keyword and a legal identifier, so
# an unmatched `record` is usually a variable rather than a missed module.
_RESERVED_DECLARATION = re.compile(
    r"(?<![\w.$])(?:@[ \t\r\n]*)?(?:class|interface|enum)(?![\w$])"
)

# A file that declares no type but is still perfectly well formed. Failing these as
# unreadable would paint the page's alarm band over a file with nothing wrong with it,
# and an alarm that cries wolf stops being read. Named here rather than written into the
# two places that care, because "read and yet not a module" is a third answer beside
# parsed and reported, and anything counting source files has to know all three.
DECLARES_NO_TYPE = ("package-info.java", "module-info.java")

# What Java writes where a return type goes when a method hands nothing back. It is not
# a type and there is nothing to learn about it: a caller of `public void f()` meets one
# method and no types at all.
NOTHING_RETURNED = "void"

# Every `throw` a body writes, whatever it goes on to throw. Found first and read second,
# so that a throw this parser cannot name is *counted* rather than passed over: a floor
# nobody knows the height of is not one anything can be argued from, and the one thing
# built on this reading — saying a module never raises what it documented — is only sound
# where every throw in the body was read.
#
# `throws IOException` on a signature is not one of these: the word is `throws`, and the
# boundary after `throw` is what tells them apart.
_A_THROW = re.compile(r"(?<![\w.$])throw(?![\w$])")

# A refusal the implementation actually raises: `throw new WithdrawalRefused(...)`. Read
# over the module's whole body, nested types and all, because that whole body is the
# module — `DepositsService.deposit` documents a `DepositRefused` that three of its own
# private helpers are the ones to throw, and a reading taken method by method would call
# that a disagreement when it is the module keeping its word.
#
# `throw thrown;` is not one of them and cannot be: the name of a variable says nothing
# about the type it holds, and following it would mean reading Java the way javac does.
# So what is read here is a floor on what a module raises, in the same direction every
# other floor in this tool leans.
_THROWN = re.compile(r"[ \t\r\n]+new[ \t\r\n]+([A-Za-z_$][\w$.]*)")

# A refusal thrown through a method of this module's own: `throw refusing(reason)`, and
# `throw this.refusing(reason)`, which is the same call wearing the prefix Java lets a
# writer put on it. The name is followed to a declaration in the same file and to nothing
# else — a call on `this`, spelled either way, is the one shape whose declaration is
# guaranteed to be here to read.
#
# The `this.` is written into this pattern rather than taken off the body first, because
# the two readings of a body want different text: `called` has to see `this.of(1)` as
# written, or a module calling its own `of` is credited with reaching whatever a static
# import of that name came from. Stripping it for one reading and not the other, in one
# place, is what keeps the two apart while `throw refusing(why)` and
# `throw this.refusing(why)` give the same answer — and they have to, because a whole
# module's promises go unchecked on the strength of a single throw this file could not
# name.
_THROWN_THROUGH = re.compile(
    r"[ \t\r\n]+(?:this[ \t\r\n]*\.[ \t\r\n]*)?([A-Za-z_$][\w$]*)[ \t\r\n]*\("
)

# A refusal the documentation promises, as Java's own syntax for saying so. The tag has
# to stand where a javadoc *block* tag stands: at the start of a line, or after one of the
# asterisks a block is written down the side with — which is also the `**` a one-line
# `/** @throws Shut ... */` opens with. Written any looser it would read the
# `{@link IllegalStateException}` an explanation is built out of as a second promise, and
# a module would be reported as documenting a refusal it had only mentioned. Written any
# tighter it reads no one-line javadoc at all, which is how a good many of them are
# written.
#
# `@exception` is Java's own synonym for `@throws` and is read as one: leaving it out
# would report a module as undocumented on the strength of which of two spellings its
# author chose, which is a finding about a keyboard.
_DOCUMENTED_REFUSAL = re.compile(
    r"(?:^|[\n*])[ \t]*@(?:throws|exception)[ \t]+([A-Za-z_$][\w$.]*)", re.M
)

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

# What this reading is called in the graph, and which files it is the reading of. Both are
# read from here rather than written anywhere else, because there are now two of these
# modules and a language named in two places is one that can be renamed in one of them.
NAME = "java"
SUFFIXES = (".java",)

# What a module of this language is, and why, in one sentence a page can print. It lives
# here because the grain is this reading's own decision: written into the renderer instead,
# a page rendered a sentence about Java classes on a run that read no Java at all.
GRAIN = "A Java module is a class, because that is where a Java interface is written."

# How a fan line says that a module was reached by calling a member it imported. The
# sentence lives beside the reading that produces it, because Java and TypeScript reach
# each other's modules by different mechanisms and one sentence for both would describe
# neither: `import static a.b.C.of` introduces the member `of` and never the name `C`.
IMPORTED_EVIDENCE = "calls %s, imported statically from it"


class ParseFailure(Exception):
    """A source file the tool could not read. Carries the reason, never just the fact."""

    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


class CallSite:
    """One call a method's body writes, as the source wrote it.

    Read for one reason and used for one thing: a flow is a path through modules, and a
    path cannot be walked out of a set. Reach answers *what* a module coordinates and is
    deliberately a set of distinct names — a module writing the same call ten more times
    coordinates nothing new. A flow answers *in what order one call goes through them*,
    which needs the opposite: every call site, kept where the source put it, and the
    method each one names so the walk knows which body to read next.

    `receiver` is what the call was written against — `deposits` in `deposits.save(...)`,
    `AmountOfMoney` in `AmountOfMoney.whyItIsNotOne(...)` — or None when nothing was
    written in front of it. Whether that name is a field, a type, a local or a member
    imported statically is not this file's business to decide, exactly as it is not for
    reach. `this.deposits.save(...)` writes `deposits`, because `this.` is a prefix a
    writer may put on a call and nothing more.

    `name` is the method called, or the type built when `builds` is set: `new Deposit(...)`
    is coordination the same way a call is, and the walk steps into the module either way.

    The order is the order the source *evaluates* them, not the order it writes them.
    Java evaluates a call's arguments before the call, so `deposits.save(new Deposit(...))`
    builds the deposit and then saves it, and reading it the other way round numbered the
    repository ahead of the record it was handed. A site written inside another's argument
    list therefore comes first, and siblings keep the order they were written in.
    """

    def __init__(self, receiver, name, builds=False):
        self.receiver = receiver
        self.name = name
        self.builds = builds

    def __repr__(self):
        written = "new " + self.name if self.builds else (
            self.name if self.receiver is None else self.receiver + "." + self.name
        )
        return "CallSite(%r)" % written


class Method:
    """One method a module declares, as a caller meets it.

    The types are the words the source wrote them with — `Optional<Customer>`, `long` —
    rather than anything resolved: this parser reads one file at a time, and a name it
    cannot follow to a declaration is still the name a caller has to learn.

    `type_parameters` is the exception: the names this method's own `<T>` introduces are
    holes the caller fills with a type they already hold, so they are recorded here for a
    rule outside this file to tell apart from the types it has to go and read.

    `documented_refusals` is what the javadoc immediately above the method promises it can
    answer with, by simple name and in the order it was written. Read rather than guessed
    at: nothing here infers a refusal from a name that ends in "Refused", from a `throws`
    clause the compiler would have forced, or from anything but the source's own
    `@throws`.

    `has_a_body` says whether the source wrote an implementation under this signature or
    ended it at a semicolon. An interface method, an abstract one and a native one are the
    three that do not, and the distinction is a refusal's: a `@throws` on a signature with
    no body is a promise made to whoever implements it, and holding it against *this*
    module's body would accuse every interface in the source of breaking a word it never
    gave.

    `calls` is what *this* method's body calls, in the order it evaluates them, and it is
    the only reading in this file taken at method grain rather than over the whole type.
    Everything else here is either what a caller must learn or what the module as a whole
    reaches, and neither needs to know which method wrote which call. A flow does: it is
    a path through modules entered by one named call, and read off the type's reach
    instead it becomes a claim about everything the type does, which is a different and
    much larger claim.
    """

    def __init__(self, name, visibility, parameters, returns, type_parameters=(),
                 annotations=(), documented_refusals=(), has_a_body=True, calls=()):
        self.name = name
        self.visibility = visibility
        self.parameters = tuple(parameters)
        self.returns = returns
        self.type_parameters = tuple(type_parameters)
        self.annotations = tuple(annotations)
        self.documented_refusals = tuple(documented_refusals)
        self.has_a_body = has_a_body
        self.calls = tuple(calls)


class Constructor:
    """One constructor a module declares, and the refusals its javadoc promises.

    Kept apart from the methods rather than among them, because a constructor is not a
    method: it says how a module is built, which in this application is the framework's
    business, and counting it would charge every module for being injectable. That was
    already true before this class existed and is why `_method_in` answers None for one.

    What it is kept *for* is the one thing a constructor does say to a caller — how
    building one can refuse. `new AmountOfMoney(-1)` is a refusal a caller meets, and the
    body of a constructor is already read into what the module raises, so a constructor's
    `@throws` had to be read too or the two sides of one refusal would be read
    asymmetrically: the throw counted, the promise invisible, and the commonest validation
    idiom in Java accused of raising something nobody documented.

    `parameters` is not read. A compact constructor writes none, a canonical one writes
    the record's own, and nothing here prices either — only the refusals are wanted.
    """

    def __init__(self, name, visibility, documented_refusals=()):
        self.name = name
        self.visibility = visibility
        self.documented_refusals = tuple(documented_refusals)
        # A constructor is never abstract and never native: whatever it promises, the
        # implementation that has to keep it is right here. Written down rather than left
        # to be assumed, so that whatever reads a documenter can read every documenter the
        # same way.
        self.has_a_body = True


class Field:
    """One value a module holds, by the name it calls it and the type it declared it with.

    Read for one reason only: a field is how a module keeps hold of a collaborator, so the
    name is what a call through it is written against — `deposits.save(...)` — and the type
    is what that call reaches. Nothing here says whether either is worth counting.

    A field a module never calls anything on is still recorded. Deciding what a held value
    amounts to is a rule, and rules do not live in this file.
    """

    def __init__(self, name, written):
        self.name = name
        self.written = written


class Imported:
    """One import, as the type it names and whether it was written `import static`.

    A static import names a member and the type holding it — `AmountOfMoney.asMoney` — so
    `type` is the part that could be a module and `member` is what the source may then
    call with no receiver in front of it. An ordinary import has no member.

    `local` is the name a body writes for what was imported, and in Java it is always the
    member: an import binds a member under its own name and the language offers no way to
    write another. It is here because the reading that follows a name in a body has to ask
    for one or the other, and TypeScript's `import { fetchDeposits as fd }` is where the
    two come apart — matched against `member` there, a body that calls `fd` reaches
    nothing at all.

    Imports are read so that a name used in a body can be followed to the module it means
    rather than guessed at by matching simple names across the whole graph, which would
    hand one module the collaborators of another that happened to share a name.
    """

    def __init__(self, type_, member, on_demand):
        self.type = type_
        self.member = member
        self.local = member
        self.on_demand = on_demand


class DeclaredType:
    """One type declaration found in a file, and where in the file it sits.

    `owner` is the top-level type whose body holds it, or None when it is that top-level
    type itself. `qualified` names it relative to that owner — `Kind`, `Body.Kind` — so
    two same-named types declared in different corners of one file can be told apart on
    the page instead of arriving as the same word twice.

    `annotations`, `supertypes` and `methods` are what a rule outside this file gets to
    reason about: what this type is marked with, what it is built on, and what it offers
    anybody holding one. Nothing here decides what any of that is worth.

    `fields`, `receivers`, `constructed`, `called` and `declares` are the same again for
    the other side of the seam — what the implementation reaches for, and which of the
    names written like a call it declares instead. They are read for a module only,
    because a type declared inside one is named on it rather than measured, and they are
    counts of *names*, never of lines: writing the same call ten more times adds nothing
    to any of them. All five are taken over the whole body, nested types included, since
    that whole body is the module.

    `raises` is the sixth and is read the same way: the refusals this module's body throws,
    by simple name, over that whole body. It is the implementation's half of a refusal, and
    the half a `@throws` on a method is checked against. `throws_not_read` is how much of
    that half is missing — the number of `throw` statements in the body whose type only
    javac could have resolved — so that a rule can decline to say "never raised" about a
    body it knows it did not read whole.

    `constructors` are kept beside the methods and never among them: they are read for
    their javadoc's refusals alone, because a constructor's body is already read into
    `raises` and reading only one side of it would accuse the commonest validation idiom
    in Java of raising something nobody wrote down.
    """

    def __init__(self, name, kind, depth, ends_at, owner, qualified,
                 annotations=(), supertypes=(), methods=(), type_parameters=(),
                 fields=(), receivers=(), constructed=(), called=(), declares=(),
                 raises=(), throws_not_read=0, constructors=()):
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
        self.fields = tuple(fields)
        self.receivers = tuple(receivers)
        self.constructed = tuple(constructed)
        self.called = tuple(called)
        self.declares = tuple(declares)
        self.raises = tuple(raises)
        self.throws_not_read = throws_not_read
        self.constructors = tuple(constructors)


class ParsedFile:
    """What one Java file turned out to contain."""

    def __init__(self, package, types, lines, imports=()):
        self.package = package
        self.types = types
        self.lines = lines
        self.imports = tuple(imports)

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


def _masked(text):
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

    Where each javadoc block sat is reported alongside, because one thing in this file does
    have to be read out of a comment: what a module documents itself as refusing. It is
    answered here rather than by a second scan over the source, so that "what is a comment"
    is decided in exactly one place — a second scanner would have its own idea of whether
    the `/**` inside a string literal opened one.
    """
    out = []
    documentation = []
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
            if text[opened:opened + 3] == "/**":
                documentation.append((opened, i))
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
    return "".join(out), documentation


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
    return "%s is never closed: opened on line %d" % (form, line_of(text, opened))


def line_of(text, position):
    """Which line of this text the offset sits on, counting from one.

    Public, and read by the TypeScript side too, for the reason `after_balanced` is: a
    line number is about newlines rather than about Java, and every reason either reading
    prints names one.
    """
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


def parse(text, path, root=None):
    """What this Java file contains, or a ParseFailure naming why it could not be read.

    `root` is the source root the file was found under, and Java does not use it: a Java
    file says which module it declares in its own `package` line, so where the root sits
    changes nothing about the answer. It is in the signature because the TypeScript side's
    answer is a path, and whoever reads a file has to be able to ask either of them the
    same question.
    """
    masked, documentation = _masked(text)
    documented = _refusals_documented_in(text, documentation)
    lines = len(text.splitlines())

    depths, final_depth, closed_too_many_at = _brace_depths(masked)
    if closed_too_many_at is not None:
        raise ParseFailure(
            "braces do not balance: a closing brace with nothing open on line %d"
            % line_of(text, closed_too_many_at)
        )
    if final_depth != 0:
        raise ParseFailure("braces do not balance: %d unclosed at end of file" % final_depth)

    types = _declared_types(masked, depths, documented)
    package = _PACKAGE.search(masked)
    imports = _imports_in(masked)

    if path.rsplit("/", 1)[-1] in DECLARES_NO_TYPE:
        log.debug("read package descriptor path=%s declaring no module", path)
        return ParsedFile(package.group(1) if package else "", types, lines, imports)

    if package is None:
        raise ParseFailure("no package declaration")
    if not any(declared.owner is None for declared in types):
        raise ParseFailure("no top-level type declaration")

    log.debug(
        "parsed file path=%s package=%s lines=%d topLevel=%d nested=%d imports=%d",
        path,
        package.group(1),
        lines,
        sum(1 for declared in types if declared.owner is None),
        sum(1 for declared in types if declared.owner is not None),
        len(imports),
    )
    return ParsedFile(package.group(1), types, lines, imports)


def _refusals_documented_in(text, documentation):
    """The refusals each javadoc block promises, and the offset each block ends at.

    Two lists rather than a list of pairs, because the offsets are what is searched and
    the names are only what is then read off.

    Tied to where it ends because that is how a block is tied to what it documents: the
    member it belongs to is the next thing written after it. Keying by the member instead
    would mean deciding here what a member is, which is the one thing this function has no
    business knowing.

    A block promising nothing is left out rather than carried as an empty entry, so that
    "this member documents no refusal" and "this member has no javadoc" are the same
    answer — which they are. The offsets come out sorted, because `documentation` arrives
    in the order the blocks were written, which is what lets the search below be a search
    rather than a scan of every block in the file.
    """
    ends_at_of = []
    names_of = []
    for opened, ends_at in documentation:
        names = _DOCUMENTED_REFUSAL.findall(text[opened:ends_at])
        if names:
            ends_at_of.append(ends_at)
            names_of.append(tuple(_simple(name) for name in names))
    return ends_at_of, names_of


def _documented_before(masked, at, documented):
    """The refusals the javadoc immediately above this offset promises, if there is one.

    Immediately means that nothing but whitespace and other comments stands between the
    block's `*/` and the first thing the member writes — annotations included, since
    `@Transactional` is written under the javadoc rather than over it, and a walk that
    stopped at one would find no documentation on the very methods this repository
    documents best.

    The nearest block above is the only one asked, so a javadoc two members up documents
    that member rather than this one. It is looked for by offset rather than by walking
    back over whitespace, because a comment is *blanked* in the masked text: walking back
    over "whitespace" would step straight over the block being looked for and land on
    whatever came before it.

    The search is over the offsets alone. Searching a list of pairs instead would compare
    the names when two offsets were equal, and a block ending exactly where the member
    begins — `/** @throws Shut */public void go()`, legal and ugly — would then be passed
    over for whatever block came before it.
    """
    ends_at_of, names_of = documented
    at_or_before = bisect.bisect_right(ends_at_of, at)
    if not at_or_before:
        return ()
    ends_at = ends_at_of[at_or_before - 1]
    return names_of[at_or_before - 1] if not masked[ends_at:at].strip() else ()


def _simple(written):
    """A type by the name it is matched on: the last part of it, however it was qualified.

    `@throws java.lang.IllegalArgumentException` and `throw new IllegalArgumentException`
    are one refusal written two ways, and a comparison that told them apart would report a
    disagreement about a package prefix. Simple names are also the only names every rule in
    this tool matches on, for the same reason: they are what the parser records.
    """
    return written.rpartition(".")[2]


def _imports_in(masked):
    """Every import this file wrote, as the type it names and the member it may call.

    A single-type import names the type outright. A static import names a member and the
    type holding it, so both halves are kept: the member is what the body then writes with
    no receiver in front of it. An on-demand import — `a.b.*`, `static a.b.C.*` — names no
    one type, so what is carried is the prefix a name can be tried under.
    """
    imports = []
    for found in _IMPORT.finditer(masked):
        static = bool(found.group(1))
        written = found.group(2)
        if written.endswith(".*"):
            imports.append(Imported(written[:-2], None, on_demand=True))
        elif static:
            holder, _, member = written.rpartition(".")
            imports.append(Imported(holder, member, on_demand=False))
        else:
            imports.append(Imported(written, None, on_demand=False))
    return tuple(imports)


def _declared_types(masked, depths, documented):
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
                "nothing it can name" % (match.group(2), line_of(masked, start), depth)
            )
        # Every one of the five kinds of type Java declares has a body, so a declaration
        # whose body this parser cannot find is a declaration it is no longer reading.
        # Carrying on with an empty header would read the type's whole interface as
        # nothing and price it at zero, which is a finding-shaped answer to a parse
        # failure — exactly what this file refuses to hand anybody.
        line = line_of(masked, start)
        body_starts_at = _body_starts_at(depths, start, depth)
        if body_starts_at is None:
            raise ParseFailure(
                "a type declaration whose body this parser cannot find: %s on line %d"
                % (match.group(2), line)
            )
        # Never None here: `parse` has already refused a file whose braces do not
        # balance, and the body was just found opening at `depth + 1`, so the depth has
        # to come back down through `depth` before the end of the text.
        body_ends_at = _body_ends_at(depths, start, depth)
        kind = _KINDS["".join(match.group(1).split())]
        # Between the name and the body: `extends`, `implements`, and a record's own
        # components. Everything a caller learns about this type without opening it.
        header = masked[match.end(2):body_starts_at]
        owner = open_types[0] if open_types else None
        # Only for a module — a type declared inside one is named on it rather than
        # scored, so reading its members would be work nothing asks for.
        methods = () if owner is not None else _methods_of(
            masked, kind, header, body_starts_at, body_ends_at, line, documented
        )
        # Read once and handed to both, because the two want it for different reasons:
        # the type this module hands a caller records the holes they fill, and the
        # reading of what its body throws has to know that a `T` a helper hands back is
        # one of those holes rather than a refusal anybody could catch by name.
        type_parameters = _type_parameters_in(header)
        reached = {} if owner is not None else _reached_in(
            masked[body_starts_at:body_ends_at], methods, type_parameters
        )
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
            type_parameters=type_parameters,
            methods=methods,
            constructors=() if owner is not None else _constructors_of(
                masked, kind, match.group(2), body_starts_at, body_ends_at, documented
            ),
            fields=() if owner is not None
            else _fields_of(masked, kind, header, body_starts_at, body_ends_at, line),
            **reached,
        )
        types.append(declared)
        open_types.append(declared)

    for keyword in _RESERVED_DECLARATION.finditer(masked):
        if keyword.start() not in declared_at:
            raise ParseFailure(
                "a type declaration this parser cannot read: %s on line %d"
                % (keyword.group(0), line_of(masked, keyword.start()))
            )
    return types


# What a caller of this type has to learn: what it is marked with, what it is built on,
# and what it offers. Read here and weighed nowhere: this file says what the source says,
# and the rules that decide what any of it costs live in a file of their own.

# The three words written between a type's name and its body. Two of them say what it
# is built on; `permits` says the opposite — which types are allowed to build on *it* —
# so it is matched to be stopped at rather than read. Leaving it out of the pattern
# altogether would be worse than reading it: the names after it would run on into the
# `implements` clause in front of them.
_INHERITANCE = re.compile(r"(?<![\w.$])(extends|implements|permits)(?![\w$])")

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

# A word at the front of a declaration, with whatever follows it left where it is. The
# boundary is looked at rather than eaten, because a modifier may be written flush
# against a type-parameter list — `static<T> T k(T t)`, which javac compiles — and a
# pattern that insisted on whitespace after the word left `static` on the front of the
# return type, which reads as no type at all and failed the whole file by name.
_LEADING_WORD = re.compile(r"([A-Za-z_$][\w$-]*)(?=[\s<])")

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
    """What this type is built on, by simple name: `extends X`, `implements Y, Z`.

    What a `sealed` type permits is not one of them. `sealed interface Payment extends
    Comparable<Payment> permits CardPayment, Repository` is built on `Comparable` and on
    nothing else — the rest are the types allowed to build on it — and reading them here
    excluded `Payment` as a generated repository, naming a rule for a fact the source
    says the opposite of. That is criterion seven inside out: the graph explaining an
    exclusion with evidence a reader can check and find wrong.
    """
    found = []
    parts = _INHERITANCE.split(_without_groups(header))
    for keyword, clause in zip(parts[1::2], parts[2::2]):
        if keyword == "permits":
            continue
        for written in clause.split(","):
            name = written.strip().split(".")[-1]
            if name:
                found.append(name)
    return tuple(found)


def _methods_of(masked, kind, header, body_starts_at, body_ends_at, line, documented):
    """Every method this type offers, including the ones a record never writes down.

    A record's components compile to an accessor apiece, and a caller learns each of them
    the way they learn a method somebody typed. Leaving them out would say that a record
    carrying six values asks nothing of anybody, which is the opposite of what it does.

    A record may also write one of those accessors out itself — defensive copying and
    normalising are the sanctioned way to give a record an invariant, and this repository
    already has records with bodies. Java compiles exactly one `cents()` either way, so a
    written zero-argument method named after a component *is* that component's accessor,
    and the synthesised one gives way to what the source actually says. Counting both
    charges a caller twice for one method they can only call once, says nothing about
    having done so, and moves the scale every other bar on the page is drawn against.

    A method that merely shares a component's name — `cents(int scale)` — is not that
    accessor and is counted beside it, because a caller has both to learn.

    `line` is where the declaration was written, so that a member this parser cannot read
    is named by a line a reader can go and open.
    """
    written_here = []
    for member, at, body in _member_headers(
        masked, kind, body_starts_at, body_ends_at
    ):
        method = _method_in(
            member,
            kind,
            line_of(masked, at),
            _documented_before(masked, at, documented),
            body,
        )
        if method is not None:
            written_here.append(method)
    if kind != "record":
        return tuple(written_here)

    accessors_written = {method.name for method in written_here if not method.parameters}
    accessors = []
    for spelled, name, dots in _components_in(header, line):
        if name in accessors_written:
            log.debug(
                "record writes its own accessor for a component name=%s line=%d, so the "
                "one it would otherwise be given is not counted a second time",
                name,
                line,
            )
            continue
        # `record R(int... more)` accepts any number of ints and hands the caller back the
        # array they arrived in, so the accessor's return is the array rather than the
        # element: the dots say how many on the way in, and nothing at all on the way out.
        accessors.append(Method(name, "public", (), spelled + ("[]" if dots else "")))
    return tuple(accessors + written_here)


def _constructors_of(masked, kind, name, body_starts_at, body_ends_at, documented):
    """Every constructor this type writes, and the refusals each one's javadoc promises.

    Walked separately from the methods, the way the fields are, because a constructor is
    not a method and the two lists are wanted for different reasons: one is what a caller
    has to learn and is priced, the other is read for its `@throws` alone.

    A synthesised one is not among them. A record with no constructor written down still
    has a canonical one, and a class with none has a default — but neither was written, so
    neither carries a javadoc, and a list of what documents this module has no use for a
    constructor nobody typed.
    """
    found = []
    for member, at, _body in _member_headers(masked, kind, body_starts_at, body_ends_at):
        constructor = _constructor_in(
            member, kind, name, _documented_before(masked, at, documented)
        )
        if constructor is not None:
            found.append(constructor)
    return tuple(found)


def _components_in(header, line):
    """A record's components, as (type, name, varargs), from the header declaring them."""
    opened = header.find("(")
    if opened < 0:
        return []
    return _declared_parameters(
        header[opened + 1:after_balanced(header, opened) - 1], line
    )


def _fields_of(masked, kind, header, body_starts_at, body_ends_at, line):
    """Every value this type holds, by the name it holds it under.

    A record's components are among them. They are written in its header rather than in
    its body, and reading only the body said that a record holds nothing at all: a
    `record Basket(Register register, long items)` whose `total()` writes
    `register.tally(items)` was calling something the module was not holding, so the name
    was resolved as a type instead, found nothing, and the fan lost a collaborator the
    source names in its first line. A component is held, exactly the way a field is.

    Nothing is failed on here, and that is the difference from reading a method. A method
    this parser declines leaves an interface cheaper than the source makes it — a claim
    about a caller — while a field it declines leaves a module reaching for one thing
    fewer, which understates it. Both are logged; only the first is worth stopping a run
    for, and stopping on an initialiser block would fail files that are perfectly
    readable.
    """
    found = []
    if kind == "record":
        for spelled, name, dots in _components_in(header, line):
            found.append(Field(name, spelled + ("[]" if dots else "")))
    for member, at, _body in _member_headers(masked, kind, body_starts_at, body_ends_at):
        field = _field_in(member, line_of(masked, at))
        if field is not None:
            found.append(field)
    return tuple(found)


def _field_in(member, line):
    """The field this member declares, or None when the member is not one.

    A member with a parameter list is a method, a constructor or a nested record, and a
    member holding a declaration keyword is a type. What is left has a type and a name,
    and anything that does not read that way — an initialiser block, several names
    declared at once — is declined with a line, never repaired into a guess.

    `private Repo a = null, b = null;` is the second of those, and it used to be read as
    the single field `a`: everything from the first `=` onwards was cut off before the
    name was looked for, so the second declarator went past without a word in the one
    file whose whole promise is that nothing is dropped in silence. The names are
    counted first now, and a member declaring more than one is declined the way the
    uninitialised spelling `private Repo a, b;` already was. That costs a call through
    either name reaching nothing, which leaves a fan shorter than the source — the
    direction this reading errs in — and it costs it out loud.
    """
    text = _without_annotations(member)
    before = text.split("=", 1)[0]
    if "(" in before or _TYPE.search(before):
        return None
    if _declares_several_names(text):
        log.debug(
            "member not read as a field line=%d reason=%s member=%s",
            line,
            "it declares several names at once, and which type each of them holds is "
            "not read from a header written that way",
            _one_line(member),
        )
        return None
    _, rest = _modifiers_in(before)
    rest = _without_type_parameters(rest)
    # `int xs[]` declares the array after the name rather than on the type, and holds the
    # same thing `int[] xs` does. The brackets come off before the name is looked for,
    # because a header ending in `]` has no name at its end for `_TRAILING_NAME` to find:
    # read the other way round, `private DepositRepository deposits[];` was declined as
    # having no name — silently, in the one file whose promise is that nothing is dropped
    # without a word — and a call through that field then reached nothing.
    rest, brackets = _brackets_after_the_name(rest)
    name = _TRAILING_NAME.search(rest)
    if name is None:
        log.debug(
            "member not read as a field line=%d reason=%s member=%s",
            line,
            "nothing at the end of it reads as a name",
            _one_line(member),
        )
        return None
    written = normalised(rest[:name.start()]) + brackets
    if not written or not _reads_as_a_type(written):
        log.debug(
            "member not read as a field line=%d reason=%s member=%s",
            line,
            "nothing before the name reads as a type" if written
            else "there is nothing before the name",
            _one_line(member),
        )
        return None
    return Field(name.group(1), written)


def _without_a_type_argument_list(before):
    """What is in front of a name, a type-argument list written on the name taken off.

    `List.<String>of()` and `this.<T>go()` write their type arguments between the dot
    and the name, so what stands immediately in front of the name is `>` rather than the
    `.` that says a member is being accessed. Taking the list off puts the dot back where
    it can be seen. Only a list that closes is taken off: a `>` with no `<` to match it
    is a shift or a comparison, and what is in front of it is left exactly as it was.
    """
    if not before.endswith(">"):
        return before
    depth = 0
    for index in range(len(before) - 1, -1, -1):
        if before[index] == ">":
            depth += 1
        elif before[index] == "<":
            depth -= 1
            if depth == 0:
                return before[:index].rstrip()
    return before


def _declares_several_names(text):
    """Whether this member declares more than one name: `private Repo a = null, b;`.

    A comma written at the top of a member is the separator between its declarators.
    Every other comma a field can write sits inside brackets of one kind or another —
    the type arguments of `Map<String, Long>`, the arguments of an initialiser's call,
    the elements of an array initialiser — so only depth zero is looked at and only the
    separator is found. Comments and literals are already blanked out by the time this
    reads a member, so a comma inside either is not one of these.
    """
    depth = 0
    for character in text:
        if character in "([{<":
            depth += 1
        elif character in ")]}>":
            depth = max(0, depth - 1)
        elif character == "," and depth == 0:
            return True
    return False


def _reached_in(body, methods, type_parameters=()):
    """What this module's implementation reaches for, by name and never by volume.

    Five readings, each a set of names rather than a count of occurrences, which is the
    whole point: a module that writes the same call ten more times reaches for nothing
    new, and a measure built on these cannot be moved by adding lines.

    - `receivers`: what a call was written against, `deposits` in `deposits.save(...)`
      and `AmountOfMoney` in `AmountOfMoney.whyItIsNotOne(...)`. Whether that name is a
      field, a type or a local is not this file's business to decide. The one spelling
      held out is a qualified `new`: `new Holder.Row()` writes the same characters and
      calls nothing on `Holder`, so it is reported under `constructed` alone.
    - `constructed`: the types a `new` builds one of, spelled the way the source spelled
      them — `Deposit`, `other.Receipt`, `java.util.ArrayList`. An array creation is not
      one of them: `new Receipt[10]` builds no `Receipt`.
    - `called`: a name with a call's brackets after it and nothing in front of it — no
      receiver, and no dot — so that a member imported statically can be followed back to
      the type it came from. `this.of(1)` is therefore not one: it calls a method this
      module has, declared or inherited, which is the one thing a static import of that
      name cannot be.
    - `raises`: the refusals thrown in it, by simple name — `WithdrawalRefused` for a
      `throw new WithdrawalRefused(kind, reason)`, wherever in the body it was written.
      A refusal thrown through a method the module itself declares — `throw refusing(why)`
      — is that method's declared return type, which is the one spelling of a throw that
      can be followed without reading Java the way javac does: the declaration is in this
      file, and its return type is already read. Following it is not a nicety. This
      repository writes exactly that spelling in two modules, and left unfollowed it
      reports both of them as documenting a refusal they never raise — a machine crying
      wolf about the two seams it was built to check.
    - `declares`: the names in that same shape that this body *declares* rather than
      calls — every method, constructor and nested record header in it, however deep,
      the ones inside a nested class and an anonymous class included. It is read
      alongside `called` and not subtracted from it, so that whoever drops a name can say
      which reading it was dropped for; on its own, `called` cannot tell a module's own
      `long of(long cents)` from a call to a statically imported `of`.

    `called` and `declares` are both taken over the whole body, nested types and all,
    because that is the body the module is: a declaration one brace deeper is still not
    a call.

    `type_parameters` are the names this module's own `<...>` introduces, and they are
    wanted for `raises` alone: inside `class Box<T extends RuntimeException>` a helper
    declared `private T make()` hands back a hole rather than a type, and a hole is not
    a refusal any caller could write a catch for.

    Everything a body writes inside a comment or a literal is already blanked out by the
    time this reads it, so a call in a javadoc example is not a collaborator.
    """
    raises, throws_not_read = _raised_in(body, methods, type_parameters)
    plain = _THROUGH_THIS.sub("", body)
    called = []
    declares = []
    # Over the body as it was written, `this.` and all, because `this.of(1)` is a call on
    # this object — its own method, or one it inherits — and never the statically imported
    # `of` that `called` exists to find. Read over `plain` instead, the `this.` came off
    # first and the call arrived here as a bare `of(1)`, which is how a module was credited
    # with reaching the type an import came from for calling its own method.
    for found in _A_CALL.finditer(body):
        name = found.group(1)
        if name in _NOT_A_CALL:
            continue
        if _without_a_type_argument_list(body[:found.start(1)].rstrip()).endswith("."):
            # `this . of(1)` and `a . of(1)`: a member access with space around the dot,
            # which the pattern's lookbehind cannot see past. Neither a bare call nor a
            # declaration, so it belongs in neither list.
            #
            # `List.<String>of()` is the same member access with the type arguments
            # written out, and the lookbehind cannot see past those either. Read without
            # them it reached `_declares_rather_than_calls`, which answers "declaration"
            # for anything behind a `>` — so a file writing that spelling was told at
            # DEBUG that it declares a member it does not declare, and a statically
            # imported member of the same name went uncounted on the strength of it.
            continue
        (declares if _declares_rather_than_calls(body, found.start(1)) else called).append(name)
    return {
        "receivers": sorted(_receivers_in(plain)),
        "constructed": sorted(_constructed_in(plain)),
        "called": sorted(set(called)),
        "declares": sorted(set(declares)),
        "raises": sorted(raises),
        "throws_not_read": throws_not_read,
    }


def _raised_in(body, methods, type_parameters=()):
    """Every refusal this body throws, and how many throws in it could not be read at all.

    `throw new X(...)` says the type outright. `throw x(...)`, where `x` is a method this
    module declares, says it through a declaration this file has already read, so the
    refusal is that method's return type. `throw this.x(...)` is that same call: `this.` is
    a prefix a writer may put on a call to their own method and nothing more, so the two
    spellings are read as one. They have to be, and not only for tidiness — the count
    below is module-wide, so one spelling read as unreadable withdraws the *documented but
    never raised* check from every refusal in the module, on the strength of a throw this
    file can name perfectly well. Any other spelling — a throw of a variable, of a
    field, of a call on something else — names a type only javac could resolve, and is not
    guessed at: what is counted here is a floor on what a module raises, which is the
    direction every other reading in this file errs in.

    A name a module declares twice is two methods, and which of them a call means is
    settled by the arguments and their types — which is javac's job, not this file's. So a
    throw is followed only where every declaration of that name hands back the same type:
    then it is that type whichever one the call meant, and nothing has been guessed. Where
    two of them hand back different types, nothing is read at all. Keying on the name and
    taking whichever declaration came last made `throw refusing(why)` mean the wrong
    `refusing` — putting a `String` on a module's interface as a refusal, and losing the
    refusal it really raises. That is not a floor: it is a wrong answer in both directions
    at once, and it is one line away from the two seams this reading exists to serve, both
    of which write exactly that spelling.

    A hole is not a refusal either. `type_parameters` are the names this module's own
    `<...>` introduces, and a helper handing one of them back — `private T make()` inside
    `class Box<T extends RuntimeException>` — throws whatever the caller filled `T` with,
    which is a type this file cannot name and nobody can write a catch for. Reading the
    letter as a refusal put a `T` on the band, priced it, and left the refusal the module
    genuinely raises through that helper reported as never raised: a wrong answer in both
    directions at once, on a module whose two sides agree. A method's own `<...>` says
    the same thing about the same letter, so the two lists are read as one.

    The throws that could not be read are counted rather than shrugged off, because what
    is built on this reading is the claim that a module never raises what it documented,
    and that claim is only sound over a body every throw of which was read. The count is
    what lets a caller of this decline to make it. Each one is logged with its reason.
    """
    raised = set()
    could_not_be_read = 0
    declared = {}
    for method in methods:
        declared.setdefault(method.name, []).append(method)
    for throw in _A_THROW.finditer(body):
        built = _THROWN.match(body, throw.end())
        if built is not None:
            raised.add(_simple(built.group(1)))
            continue
        through = _THROWN_THROUGH.match(body, throw.end())
        refusal = (
            _thrown_through(through.group(1), declared, type_parameters)
            if through else None
        )
        if refusal is None:
            could_not_be_read += 1
            log.debug(
                "throw not read as a refusal reason=%s thrown=%s",
                "it throws neither a type it builds nor a call whose declarations in "
                "this module all hand back one type that is not a type variable, so "
                "what it throws is a type only javac could resolve",
                _one_line(body[throw.start():throw.start() + 80]),
            )
            continue
        raised.add(refusal)
    return raised, could_not_be_read


def _thrown_through(name, declared, type_parameters=()):
    """What `throw f(...)` throws, when every `f` this module declares hands back the same.

    The arguments are not what settles it, and they cannot be: this reads the *masked*
    text, where a literal has been blanked out, so `f("shut")` and `f()` are the same
    characters by the time they arrive here. Counting them would be guessing at an arity,
    and one wrong arity is a refusal invented or a refusal lost. What the declarations
    agree on needs no arity at all.

    Three ways to answer nothing, and each of them is a name this file will not guess at:
    no `f` is declared here — a static import, or something inherited, whose declaration is
    in a file this parser is not reading; the declarations of `f` hand back different
    types, so only javac could say which was called; or they agree on a type variable or on
    nothing, neither of which is a refusal a caller could ever catch by name.

    A type variable is a letter, and where it was introduced does not change that. `<T>` on
    the method is one place it can be written and `<T>` on the enclosing type is the other,
    so `type_parameters` carries the type's own names down here and the two lists are asked
    as one. Reading only the method's own is how `private T make()` inside
    `class Box<T extends RuntimeException>` came to look like an ordinary method handing
    back a refusal named `T`.
    """
    candidates = declared.get(name, ())
    if not candidates:
        return None
    refusals = {_simple(method.returns) for method in candidates}
    if len(refusals) != 1:
        return None
    refusal = refusals.pop()
    holes = set(type_parameters).union(
        *[method.type_parameters for method in candidates]
    )
    return None if refusal == NOTHING_RETURNED or refusal in holes else refusal


def _calls_in(body):
    """Every call this one method's body makes, in the order the source evaluates them.

    The reading a flow is walked out of, and the only one in this file that keeps order.
    Reach cannot: it is a set of distinct things, deliberately, so that a module writing
    the same call ten more times reaches nothing new. A path needs every site where it
    was written and needs the method each one names, because which module the flow enters
    next is decided by the call and not by whoever happens to hold the field.

    Three spellings are read, and they are the three that can name a module:

    - `deposits.save(...)` and `AmountOfMoney.whyItIsNotOne(...)`: something written in
      front of a dot, and the method after it. `this.deposits.save(...)` writes the field
      as its receiver and reads that way here, since `this.` sits in front of the whole
      access rather than between the two names; `this.of(1)` writes `this` as the
      receiver, which is a call to a method this module has and is read as a bare one. A
      qualified `new` — `new Row.Of()` — writes the same characters as a static call and
      calls nothing on `Row`, so it is left to the third reading, exactly as
      `_receivers_in` leaves it.
    - `refuseUnlessAnAmountOfMoney(amount)`: a name with a call's brackets after it and
      nothing in front of it. That is either a method this module declares or a member
      imported statically, and both are followed — by whoever resolves these, since which
      of the two it is depends on what the file imported and what the module declares,
      and neither is in front of this function. A declaration is written the same way and
      is not a call, so `_declares_rather_than_calls` holds those out here as it does
      there.
    - `new Deposit(...)`: building a collaborator is coordinating it, the same way calling
      one is — the reasoning `reach_of` already makes about `new B(a)` and `B.of(a)`. An
      array creation builds none of its element type and is not one.

    The order is evaluation order rather than the order the characters were typed, and
    they differ in one shape that this repository writes constantly:
    `deposits.save(new Deposit(...))` writes the repository first and builds the deposit
    first. So a site written inside another's argument list is emitted before the site
    that encloses it, and sites that enclose nothing keep the order they were written in.
    Nothing else about evaluation is modelled: a branch not taken is still a call this
    method can make, and a flow that showed only the branch that happened to be taken
    would be a trace of one run rather than a reading of the source.

    Everything inside a comment or a literal is already blanked out by the time this reads
    it, so a call written in a javadoc example is not a step on any flow.
    """
    if body is None:
        return ()
    found = []
    for match in _A_RECEIVER_CALL.finditer(body):
        receiver, name = match.group(1), match.group(2)
        if _PRECEDED_BY_NEW.search(body[:match.start(1)].rstrip()):
            # `new Holder.Row()` builds the type `Holder` nests and calls nothing at all
            # on `Holder`. The construction reading below reports it whole.
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
        before = _without_a_type_argument_list(body[:match.start(1)].rstrip())
        if before.endswith("."):
            # A member access, whose receiver the reading above has already had, or one
            # written on the result of another call, whose receiver is not a name at all.
            continue
        if _PRECEDED_BY_NEW.search(before):
            # `new Deposit(...)` puts a name in front of brackets with nothing but `new`
            # before it, so it reads as a bare call to something spelled `Deposit`. It is
            # a construction, reported once by the reading below; counted here as well it
            # was a second step on every flow that builds anything.
            continue
        if _declares_rather_than_calls(body, match.start(1)):
            continue
        opened = match.end() - 1
        found.append((match.start(1), after_balanced(body, opened), CallSite(None, name)))
    for match in _CONSTRUCTED.finditer(body):
        built = match.group(1)
        if _AN_ARRAY_CREATION.match(body, match.end(1)):
            continue
        found.append(
            (match.start(), _after_the_arguments(body, match.end(1)),
             CallSite(None, built, builds=True))
        )
    return in_evaluation_order(found)


def _after_the_arguments(body, after_the_name):
    """Just past a construction's argument list, or the end of its name when it has none.

    The span is wanted for one thing only — telling a site written inside another's
    arguments from one written after it — so a spelling this cannot follow costs the
    nesting and never a call: the site is still read, it merely keeps the place the
    characters put it.
    """
    position = after_the_name
    while position < len(body) and body[position] in " \t\r\n":
        position += 1
    if position < len(body) and body[position] == "<":
        depth = 0
        while position < len(body):
            if body[position] == "<":
                depth += 1
            elif body[position] == ">":
                depth -= 1
                if depth == 0:
                    position += 1
                    break
            position += 1
        while position < len(body) and body[position] in " \t\r\n":
            position += 1
    if position < len(body) and body[position] == "(":
        return after_balanced(body, position)
    return after_the_name


def in_evaluation_order(found):
    """The sites sorted so that each one comes after everything written inside its arguments.

    Java evaluates a call's arguments before the call itself, so the deposit in
    `deposits.save(new Deposit(...))` is built before it is saved. Sorting on where each
    site starts says the opposite, and put a repository on a flow one step ahead of the
    record it was handed.

    Public, and read by the TypeScript side as well, because this is about brackets and
    the order arguments are evaluated in rather than about Java: JavaScript evaluates a
    call's arguments first too, and spells both the same way. A second account of it
    would be one that could drift.
    """
    ordered = []
    open_sites = []
    for start, ends, site in sorted(found, key=lambda entry: (entry[0], -entry[1])):
        while open_sites and open_sites[-1][0] <= start:
            ordered.append(open_sites.pop()[1])
        open_sites.append((ends, site))
    while open_sites:
        ordered.append(open_sites.pop()[1])
    return tuple(ordered)


def _receivers_in(plain):
    """What calls in this body were written against, a qualified `new` left out.

    `new Holder.Row()` is spelled exactly the way a static call on `Holder` is, and is
    neither: it builds the type `Holder` nests, and nothing at all is called on `Holder`.
    Read as a receiver it put the enclosing module in the fan with an evidence string a
    reader could check against the file and find wrong — the one failure this file exists
    to make impossible. The construction reading already reports `Holder.Row`, and
    whoever resolves it declines a nested name, so leaving it out here loses nothing.
    """
    found = set()
    for match in _A_RECEIVER.finditer(plain):
        if _PRECEDED_BY_NEW.search(plain[:match.start(1)].rstrip()):
            log.debug(
                "name not read as a receiver name=%s reason=%s",
                match.group(1),
                "new is written in front of it, so it qualifies the type being built "
                "rather than holding something a call was written on",
            )
            continue
        found.add(match.group(1))
    return found


def _constructed_in(plain):
    """The types this body builds one of, array creations left out and said out loud.

    `new Receipt[10]` builds no `Receipt` at all — it builds an array of ten nulls — and
    reading it as a construction put a persistent record into a module's fan under an
    evidence string saying the module built one. What follows the type name is the whole
    of the difference, so it is looked at here rather than guessed at, and the ones
    declined are logged: this file drops nothing without a word.
    """
    found = set()
    for match in _CONSTRUCTED.finditer(plain):
        if _AN_ARRAY_CREATION.match(plain, match.end(1)):
            log.debug(
                "new not read as building one type=%s reason=%s",
                match.group(1),
                "it creates an array of them, and an array of a type holds none of it",
            )
            continue
        found.add(match.group(1))
    return found


def _declares_rather_than_calls(text, at):
    """Whether the name at this offset is being declared rather than called.

    What is written immediately in front of it decides, because a declaration writes a
    type there and a call cannot: `long of(...)` and `Map<String, Long> of(...)` and
    `int[] of(...)` are declarations, while `of(...)`, `= of(...)`, `return of(...)` and
    `x -> of(...)` are calls. A word in front is a type or a modifier unless it is one of
    the words Java lets a statement begin with, which `_NOT_A_CALL` already lists.

    The one `>` that ends no type never arrives here. `List.<String>of()` writes its type
    arguments between the dot and the name, so the character in front of the name is `>`
    while the thing being written is plainly a call; the caller looks past the list to
    the dot behind it, and a name behind a dot goes in neither list. Read here instead,
    it was answered "declaration", and the file was told at DEBUG that it declares a
    member it does not declare.

    Nothing here fails and nothing here is repaired into a guess: the two readings are
    the same handful of characters apart, and where they cannot be told apart this
    answers "declaration". That costs a statically imported call going uncounted, which
    leaves a fan shorter than the source. The other answer draws a line to a card the
    module never calls.

    Two declarations do write punctuation in front of themselves and so are read as calls:
    an enum constant carrying arguments — `RED(1),` — and a constructor with no modifiers
    on it, which a nested type can have. Neither can reach anything on its own; both can
    only be followed through the static-import reading, and only when a static import in
    the same file names a member of that exact spelling. Whoever makes that reading holds
    out the module's own name and the names of every type it declares inside itself, which
    covers both constructors. The enum constant is the one shape left over, and it is named
    on the page as a reading that can overstate rather than left here to be found.
    """
    before = text[:at].rstrip()
    if before.endswith(_A_LAMBDA_ARROW):
        return False
    if before.endswith(_ENDS_A_TYPE):
        return True
    word = _TRAILING_NAME.search(before)
    return word is not None and word.group(1) not in _NOT_A_CALL


def _member_headers(masked, kind, body_starts_at, body_ends_at):
    """Each member of a type body, as (its text, its offset, whether a body followed).

    Every member's body is stepped over whole, so nothing written inside a method — a
    call that reads like a declaration, a local class, a lambda — is ever taken for part
    of the type's interface.

    Whether a body followed is read here and nowhere else, because here is the only place
    that knows: the member's text stops at the `{` or the `;` that ended it, and which of
    the two it was is the whole of the difference between a method that has an
    implementation to read and one that only promises somebody else will write it. An
    interface method, an abstract one and a native one all end at a semicolon, and each of
    them can carry a `@throws` this module's own body could never be asked to keep.

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
            ends = after_balanced(masked, position, "{", "}")
            headers.append(_header(masked, start, position, masked[position:ends]))
            position = ends
            start = position
            continue
        elif parens == 0 and character == ";":
            headers.append(_header(masked, start, position, None))
            start = position + 1
        position += 1
    return headers


def _header(masked, start, position, body):
    """One member's text, the offset of its first word, and the body written under it.

    The offset is where the member's own first word is rather than where the member
    before it ended, because it is only ever used to name a line to a reader, and the
    line after the previous member's semicolon is not the line they need to open.

    The body is the masked text between its braces, or None when the member ended at a
    semicolon instead. Handed back rather than stepped over and forgotten, because a flow
    is walked one method at a time: read only over the whole type, every method of a
    module reaches everything every other method of it reaches, and a flow through the
    entry *method* becomes a claim about everything the entry *module* does.
    """
    while start < position and masked[start] in " \t\r\n":
        start += 1
    return masked[start:position], start, body


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
            position = after_balanced(masked, position, "{", "}")
            continue
        elif parens == 0 and character == ";":
            return position + 1
        position += 1
    return body_ends_at


def _method_in(member, holder_kind, line, documented_refusals=(), body=None):
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

    `body` is the masked text between this member's braces, or None when it ended at a
    semicolon instead. It answers two things at once: whether an implementation was
    written under the signature at all, and what that implementation calls.
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
    closed = after_balanced(text, opened)
    returns = normalised(signature[:name.start()])
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
        _annotations_on(member),
        documented_refusals,
        # An annotation's members are abstract, all of them, always. One of them is also
        # the single shape whose header holds a brace that opens nothing —
        # `String[] value() default {"a"};` — so the brace a body is recognised by is
        # there without a body under it. Answered by the kind rather than by the brace,
        # because the kind is the fact and the brace is the thing that misleads.
        body is not None and holder_kind != "annotation",
        _calls_in(body if holder_kind != "annotation" else None),
    )


def _constructor_in(member, holder_kind, holder_name, documented_refusals=()):
    """The constructor this member declares, or None when the member is not one.

    A constructor is the one member written with the type's own name and no return type in
    front of it, in both of the shapes Java allows: `public Coin(long cents)`, and a
    record's compact `public Coin` with no parameter list at all. The second is why the
    parameter list cannot be what this is recognised by — and why `_method_in` never sees
    it, since it looks for a bracket the compact form does not write.

    Told apart by the name *and* by there being nothing in front of it: `public Coin
    make()` declares a method named `make`, and `private final Coin coin` holds a field
    named `coin`. Both write the type's name where a return type or a field's type goes,
    and neither is a constructor.
    """
    text = _without_annotations(member)
    opened = text.find("(")
    head = text if opened < 0 else text[:opened]
    if "=" in head or _TYPE.search(head):
        return None
    modifiers, rest = _modifiers_in(head)
    signature = _without_type_parameters(rest)
    name = _TRAILING_NAME.search(signature)
    if name is None or name.group(1) != holder_name or signature[:name.start()].strip():
        return None
    return Constructor(
        holder_name,
        _visibility(modifiers, holder_kind, of_a_constructor=True),
        documented_refusals,
    )


def _annotations_on(member):
    """The annotations written on this member, by simple name.

    Read by walking the front of the member over exactly what Java allows to precede a
    type there — annotations, what they were given, and modifiers — and stopping at the
    first thing that is neither, the same way a declaration's own annotations are read.
    Reading every `@` in the member instead would collect the ones written on its
    parameters, and `@Transactional` on the method would then be indistinguishable from
    `@RequestBody` on an argument.
    """
    found = []
    rest = member.lstrip()
    while rest:
        if rest.startswith("@"):
            annotation = _ANNOTATION.match(rest)
            if annotation is None or annotation.group(1) == "interface":
                break
            found.append(annotation.group(1).rsplit(".", 1)[-1])
            rest = rest[annotation.end():].lstrip()
            if rest.startswith("("):
                rest = rest[after_balanced(rest, 0):].lstrip()
            continue
        word = _LEADING_WORD.match(rest)
        if word is None or word.group(1) not in _MODIFIERS:
            break
        rest = rest[word.end():].lstrip()
    return tuple(found)


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
    for part in split_on_commas(text):
        if not part.strip():
            continue
        _, rest = _modifiers_in(_without_annotations(part))
        # `int xs[]` declares the array on the name rather than on the type. The brackets
        # belong to what the caller has to hand over either way.
        rest, brackets = _brackets_after_the_name(rest)
        name = _TRAILING_NAME.search(rest)
        # `String... names` hands over a String: the dots say how many, not what.
        spelled = normalised(rest[:name.start()] if name else "")
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


def _visibility(modifiers, holder_kind, of_a_constructor=False):
    """How far outside this type the member can be reached from.

    An interface's members carry no access modifier and are public anyway, which is the
    one place where saying nothing means the widest thing rather than the narrowest.

    An enum's constructor written without one is the opposite: the JLS makes it private,
    and no other access modifier is even legal on it, so nobody outside the enum can reach
    it. Reading it as package-private put what such a constructor promises on the module's
    interface and let a `@throws` on it carry a finding — a machine arguing about a promise
    made to nobody, which is the rule `_documenters_of` already states from the other side:
    a constructor a caller cannot reach is a note to whoever maintains the module.
    """
    for access in _ACCESS:
        if access in modifiers:
            return access
    if holder_kind in ("interface", "annotation"):
        return "public"
    return "private" if of_a_constructor and holder_kind == "enum" else "package-private"


def _without_type_parameters(rest):
    """`<T extends Comparable<T>> T largest` is `T largest`: the bounds are not the type."""
    rest = rest.lstrip()
    return rest[after_balanced(rest, 0, "<", ">"):] if rest.startswith("<") else rest


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
    inside = text[1:after_balanced(text, 0, "<", ">") - 1]
    names = []
    for part in split_on_commas(inside):
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
                    position = after_balanced(text, position)
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


def split_on_commas(text):
    """Split on the commas between things, never on one inside a type or a bracket group.

    Public, and read by the TypeScript side as well, for the reason `after_balanced` and
    `in_evaluation_order` are: splitting a list on the commas that separate it is about
    brackets rather than about Java. It was hand-copied there once and the copy drifted —
    it dropped the clamp below, so one stray `>` put every comma after it at a depth that
    was never zero, and `ring(m: Map<string, () => string>, n: number)` came back as one
    parameter instead of two. A second account of it would be one that could drift again.

    A brace is a bracket here. Java writes one inside an annotation's arguments —
    `@Values({1, 2}) int each` — and TypeScript writes one around a destructured
    parameter, `{ customer, onSignOut }: Props`, which is one parameter however many names
    the caller's object is taken apart into.
    """
    parts = []
    depth = 0
    start = 0
    for position, character in enumerate(text):
        if character in "<([{":
            depth += 1
        elif character in ">)]}" and not ends_an_arrow(text, position):
            depth = max(0, depth - 1)
        elif character == "," and depth == 0:
            parts.append(text[start:position])
            start = position + 1
    parts.append(text[start:])
    return parts


def ends_an_arrow(text, position):
    """Whether the `>` here is the second half of an `=>`, and so closes nothing at all.

    Public, and the one thing every scanner on either side has to agree about, because
    getting it wrong is silent: TypeScript writes a function type `(a: A) => B`, and a `>`
    counted as a bracket closing there drops the depth below what the source has. The
    comma between two parameters is then seen at a depth that is not zero and never splits
    them, and the brace that opens a body is never found at all — an interface cheaper
    than the source makes it, and a whole file failed for a return type "nothing closes".

    Java writes `->` for a lambda and never `=>`, so the rule costs the Java side nothing
    and one scanner can be read by both.
    """
    return text[position] == ">" and text[position - 1: position] == "="


def after_balanced(text, position, opening="(", closing=")"):
    """Just past the bracket that closes the one at `position`, or the end of the text.

    Public, and read by the TypeScript side too, for the same reason `in_evaluation_order`
    is: matching a bracket to its partner is about brackets and not about Java.
    """
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

    The mirror of `after_balanced`, for reading backwards: the only way to find the front
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


def normalised(written):
    """A type as the graph carries it: one space where it needs one, none where it does not.

    Every bracket is closed up against what it holds and every comma between two type
    arguments is followed by exactly one space, whether or not the source wrote one. The
    comma is the one that matters: while the space after it was carried through, one
    document held both `Map<String, Long>` and `Map<String,Long>`, which reads as two
    types a caller has to learn where there is one.

    Public, and read by the TypeScript side as well. It was hand-copied there once and the
    copy left the comma out, so `Record<string,number>` and `Record<string, number>` stayed
    two strings on one page — the exact hazard this docstring already named. Spelling is
    about brackets and spaces rather than about Java, so there is one account of it.
    """
    tidy = re.sub(r"\s+", " ", written).strip()
    for bracket in ("<", ">", ",", "[", "]"):
        tidy = tidy.replace(" " + bracket, bracket)
    tidy = tidy.replace("< ", "<").replace("[ ", "[")
    return re.sub(r",\s*", ", ", tidy).strip()


def candidate_ids(name, package, imports):
    """Every module id this simple name could mean in a file written with these imports.

    Best first, in the order Java itself settles the question: a single-type import wins
    over the package the file sits in, and an on-demand import — `a.b.*` — is tried last
    because it names no one type. Whoever asks tries them against the modules it actually
    holds and takes the first that is one; a name that matches none of them is a name from
    outside this source tree, and answering it with a guess is how one module would end up
    with another's collaborators for having shared a simple name with it.

    A single-type import does not merely win, it *binds*: `import shop.Holder.Row` makes
    `Row` mean `Holder`'s `Row` everywhere in the file, and there is no falling back to
    the package when that is not a module here. Offered as a list to try in order, the
    fall-back happened anyway — a file importing a nested `SavingsAccountResponse` was
    credited with calling the top-level one next door, which this repository's own
    `CustomerController` is one line away from doing — so the binding is answered as the
    only candidate rather than as the first of several.

    A static import binds nothing of the sort. `import static q.Helper.of` introduces the
    member `of`, never the name `Helper`, so its holder must not be tried here at all,
    let alone ahead of the file's own package: read as a single-type import it took
    `Helper.build()` in package `p` away from `p.Helper` and gave it to `q.Helper`. The
    member it does introduce is followed where reach reads the imports themselves.
    """
    for imported in imports:
        if imported.member is not None or imported.on_demand:
            continue
        if imported.type.rsplit(".", 1)[-1] == name:
            return [imported.type]
    found = [package + "." + name if package else name]
    for imported in imports:
        if imported.on_demand:
            found.append(imported.type + "." + name)
    return found


def module_id(package, name):
    """The id a module of this name in this package is known by: its fully qualified name.

    One line, and it lives here rather than in whoever builds the graph, because the two
    languages this tool reads spell a module's identity differently — Java by the package
    it declares, TypeScript by the path the file sits at — and a graph that composed one
    of them itself would be a second account of what a module is called.
    """
    return package + "." + name if package else name


def followed(name, package, imports):
    """Every module id a name written in a body could mean, best first, or none at all.

    Java's own order, and its own refusal to guess: a name written out in full means the
    module of that id and nothing else, while a simple name is settled by the file's
    imports and then by its package. Whoever asks tries them against the modules the
    graph actually holds and takes the first that is one.

    The wrapper around `candidate_ids` earns its place on the qualified branch: the
    package in front of `new other.Receipt()` is the answer rather than something to cut
    off and look up again, and cutting it off is how that construction was once read as
    building this package's own `Receipt` — a different module, of a different kind, under
    an evidence string a reader could check and find false.
    """
    if "." in name:
        return [name]
    return candidate_ids(name, package, imports)


def crosses_the_seam(written):
    """Every type a caller meets in this parameter or return, by the simple name of each.

    `void` is answered with nothing at all, and not because of a weight: a method that
    hands nothing back puts nothing across the seam on the way out, so there is no type
    there for any weight to price. Java writes the word where a return type goes, which is
    how it came to be counted — nine modules on the committed page carried it, and a
    caller of `public void f()` was charged for one type where they meet none.
    """
    if written == NOTHING_RETURNED:
        return []
    return names_in(written)


def written_names_in(written):
    """Every type name inside a type as it was written, qualifiers and all.

    `List<Optional<Customer>>` is `List`, `Optional` and `Customer`; `other.Receipt` is
    `other.Receipt` and `Holder.Row` is `Holder.Row`. Generics and arrays are taken
    apart, and a wildcard is not a type at all.

    The qualifier is kept because a name is only worth following to a module when it is
    followed to the one the compiler would pick, and what stands in front of the dot is
    the whole of that answer: cut back to its last word, a field written
    `private final other.Receipt receipt` was resolved against this file's own package
    and the fan drew a line to `shop.Receipt` under an evidence string that said, in the
    same breath, that the field held an `other.Receipt`.

    It lives here rather than beside whatever wants the names, because it is the mirror
    of `_A_TYPE` — the punctuation split on is exactly the punctuation that pattern lets
    a type be spelled with, and the words skipped are the only ones `<? extends Receipt>`
    can put where a name goes. Anywhere else it would be a second, drifting account of
    how Java spells a type.
    """
    found = []
    for word in re.split(_INSIDE_A_TYPE, written):
        spelled = word.rstrip(".")
        if spelled and spelled not in _NOT_A_NAME:
            found.append(spelled)
    return found


def names_in(written):
    """Every type name inside a type as it was written, by the simple name of each.

    `java.util.List` is `List`: for a caller reading an interface, a package prefix is
    not a second thing to learn. That is the right reading for what crosses a seam and
    the wrong one for following a name to a module, which is why the two are separate
    functions rather than one used twice — see `written_names_in`, which this is the
    unqualified reading of.
    """
    return [spelled.split(".")[-1] for spelled in written_names_in(written)]


# The punctuation a type is carried in: `_A_TYPE` allows exactly these around the names,
# so splitting on them leaves the names and nothing else.
_INSIDE_A_TYPE = re.compile(r"[<>,\[\]\s]+")

# The words a type can hold that do not name one. A modifier is not among them: they are
# off long before a type reaches here, so listing one would be a line nothing could ever
# reach and a reader could not tell from a rule that mattered.
_NOT_A_NAME = ("?", "extends", "super")
