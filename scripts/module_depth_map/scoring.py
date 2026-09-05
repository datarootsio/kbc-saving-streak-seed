"""What an interface costs a caller, and what is never scored at all — read from a file.

The rules live in `scoring.json` beside this file rather than in it, because the whole
claim of the page is that a score can be argued with. A reader who thinks a module has
been treated unfairly changes a weight or a rule there and runs the tool again; nothing
in the analyser has to be edited for the output to change.

Interface cost is everything a caller has to learn before they can use a module
correctly: each method they can reach, each parameter of each of those methods, and each
distinct type that crosses the seam in a parameter or a return. Types are counted once
per module however many methods hand them over — learning `RecordedDeposit` twice is
still learning it once — and a type the file lists under `typesEveryCallerAlreadyKnows`
is weighted apart from one it does not, which is why a method handing back a domain type
costs more than one handing back a primitive.

That list is the whole of the distinction, and `mustBeLearned` says no more than "this
name is not on it". It is not a claim about where the type was declared: the tool reads
one source tree and never resolves a name, so `ProblemDetail` is charged at
`typeToLearn` for the same reason `Receipt` is — nobody told it otherwise. Wording that
said "a type this application invented" made the graph assert something about the source
that a reader could check and find wrong.

What a module reaches is the other half, and the numerator of depth: the distinct things
it coordinates that its caller therefore does not — collaborating modules it calls,
adapters it drives, persistent records it keeps, and the transaction it establishes.
Depth is that count over interface cost, and never implementation lines over interface
lines: the line-count measure pays a module for padding, and under it the largest file in
a repository scores as its deepest module. Reach counts distinct names, so there is
nothing a keyboard can do to it. What an adapter is, what a persistent record is and what
establishes a transaction are three more rules in the file rather than three judgements
written down here.

Every refusal a module can answer with is interface too, and is priced with the rest of
it — a caller who does not know a module can answer with `WithdrawalRefused` has not
learned the module. It is then carried as a band of its own, `refusalCost` beside
`costWithoutRefusals`, so that a module whose interface is wide because it is honest about
how it can fail can be told apart from one that is merely wide. Both sides are read from
the source's own words: the `@throws` written over a member a caller can reach, and what
the body throws. Where the two disagree the module carries a finding naming both of them,
under one of two names the file gives — because a stale comment caught by a machine is
only worth as much as the rule a reader can point at behind it.

A constructor a caller can reach is one of those members. Of the two ways to make the
sides of a refusal symmetrical — read a constructor's `@throws` as the module documenting
itself, or stop reading a constructor's `throw` into what it raises — this is the first,
because the second says something false: `new AmountOfMoney(-1)` refuses, and a caller who
does not know that has not learned the module. Validating in a constructor is the
sanctioned way to give a Java value an invariant, and reading only the throw would accuse
every module that does it of raising something nobody wrote down.

Where a promise could not be held against an implementation, no finding is made and the
reason is logged: a `@throws` on a method with no body is a promise to whoever implements
it, and a body carrying a throw this tool could not name may be raising exactly what was
promised. A machine that accuses a module which kept its word stops being read, which
would cost more than the stale comments it catches are worth.

The deletion test is the verdict those two halves add up to: would deleting this module
concentrate complexity, or merely move it to its callers? A module coordinating no more
things than the methods it presents concentrates nothing, and when two or more modules go
through such a module, deleting it moves the same coordination to them — a pass-through.
A module coordinating more than it presents is concentrating it, and earns its keep. Both
thresholds live in the file with the rest, because "this is a pass-through" is only a
finding rather than an opinion if the rule behind it is one a reader can point at.

A flow is the last thing the file decides, and the only one that is about the whole graph
rather than about one module. Each flow names the business event it is, the reason it is
worth tracing, and the one call a caller makes to enter it — a module and a method on it,
and nothing else. The modules it passes through are never written down: they are walked
out of the reach every module already has, in the order the walk enters them, so a flow
cannot describe an application the source no longer holds. Where the walk cannot start —
no module of that id in the graph, no such method a caller can reach on it, nothing
reached from it at all — the flow carries no path rather than a shorter one, and says
which of the three it was.

Three kinds of thing are drawn but never scored, each by a rule the file names: values
that only carry data across a seam, repository interfaces whose implementation is
generated rather than written, and the application's entry point. They are shallow by
construction, and scoring them would bury the modules that are not. Nothing is ever
excluded without a rule saying so: the graph records the rule and the fact that matched
it, so "why was this ignored?" always has an answer a reader can point at.
"""

import collections
import json
import logging
import os
import re

from . import javasource

log = logging.getLogger("module_depth_map.scoring")

# The shape of the file this reads. It moved to /2 when the rules gained a `reach`
# section: a /1 file names no rule for what an adapter is, what a persistent record is or
# what establishes a transaction, and running it would score depth with three rules
# nobody wrote. Refused instead, which is the same promise the rest of this file keeps.
# It moved to /3 when they gained a `deletionTest`: a /2 file draws the line between a
# pass-through and a module that earns its keep nowhere at all, and a verdict rendered
# from a threshold nobody wrote is the one thing a mechanical test may not hand anybody.
# It moved to /4 when they gained a `refusals` section and the `refusal` weight beside the
# others: a /3 file prices a refusal at nothing and names neither disagreement a refusal
# can be in, so every module would read as costing a caller less than it does and every
# stale `@throws` in the source would go unreported under rules nobody wrote.
# It moved to /5 when they gained a `flows` section: a /4 file names no business event at
# all, so the page would draw no flow and say nothing about why — which reads as an
# application that does nothing worth tracing rather than as a file that was never asked.
SCHEMA = "module-depth-map-scoring/5"

DEFAULT_CONFIGURATION = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scoring.json")

WEIGHTS = ("method", "parameter", "typeToLearn", "typeEveryCallerAlreadyKnows", "refusal")
VISIBILITIES = ("public", "protected", "package-private", "private")

# What a reached thing can be, and in what order two readings of one thing settle. A
# module that builds a persistent record and also calls it is coordinating the record: the
# stronger reading wins, so that one thing reached is one line in the fan whichever way it
# was found. `transaction` is last because nothing else can ever be one.
_REACH_KINDS = ("record", "adapter", "module", "transaction")

# What a module is handed by whatever it is built on: the names declared above it, the
# types nested above it, and whether any of it was out of this graph's sight.
_Inherited = collections.namedtuple("_Inherited", "declares nested opaque")


def _module_named(name, package, imports, modules):
    """The module a simple name means in a file written with these imports, or None.

    The same order Java settles it in, and the same refusal to guess: a name that matches
    no module in this source tree is a name from outside it. Used for a supertype, which
    is written where nothing this module declares can shadow it, so nothing shadows it
    here either.
    """
    for candidate in javasource.candidate_ids(name, package, imports):
        if candidate in modules:
            return candidate
    return None


class ConfigurationRefused(Exception):
    """The file that decides the scores could not be used, and the run must not guess.

    Falling back to built-in defaults would be the worst of both: the output would look
    like a score somebody chose, while the file they chose it in was being ignored.
    """

    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


class Exclusion:
    """One named reason a module is drawn but never scored."""

    def __init__(self, rule, because, when):
        self.rule = rule
        self.because = because
        self.when = when

    def matches(self, declared):
        """The evidence for excluding this module, or None if this rule says nothing about it."""
        return evidence_for(self.when, declared)


def evidence_for(when, declared):
    """The facts about this module that satisfy every condition, or None if one does not.

    Every condition in the rule has to hold, and the evidence names each of them by the
    fact that satisfied it, so the graph can say "covered by *this* rule for *this*
    reason" rather than only that something matched. The conditions are read in one fixed
    order rather than the file's, so that two rules written with their conditions in
    different orders still read the same way on the page.

    Written once and used by every rule the file holds — the exclusions, and what makes a
    reached thing an adapter or a persistent record — because a second reading of the same
    four conditions would be a second set of words for one rule a reader is invited to
    argue with.
    """
    evidence = []
    for condition in [name for name in CONDITIONS if name in when]:
        met, because = CONDITIONS[condition].met(when[condition], declared)
        if not met:
            return None
        evidence.append(because)
    return ", ".join(evidence)


# What a value written in the file has to be before any of it is applied. Each check
# refuses rather than repairing: a name this tool cannot match, a list with nothing in
# it where a rule needs a name, a shape it does not read at all — every one of them
# would otherwise be a rule that looks like a judgement and does nothing.

def _known_kinds(kinds, where):
    unknown = [kind for kind in kinds if kind not in javasource.KINDS]
    if unknown:
        raise ConfigurationRefused(
            "%s names %s, and a module is %s: a condition on any other kind could never "
            "hold, and a rule that can never fire is not one anybody could argue with"
            % (where, ", ".join(sorted(unknown)), " or ".join(javasource.KINDS))
        )
    return kinds


def _strings(value, where, may_be_empty=False):
    """A list of names, refused by what is actually wrong with it.

    The faults here are separate messages. One name written on its own where the list
    belongs is not an empty list, and answering it with "it has to be a list with
    something in it" — on the one field where nothing in it is allowed — sends its author
    to fix the half this tool is perfectly happy with. A name written twice is the same
    hazard one step smaller: the second entry reads like a decision somebody made and is
    a no-op, because every one of these lists is matched as a set.
    """
    if not isinstance(value, list):
        raise ConfigurationRefused(
            "%s is %s, and it has to be a list of names" % (where, _shape(value))
        )
    if not value and not may_be_empty:
        raise ConfigurationRefused(
            "%s is empty, and a rule with no name to match on could never fire" % where
        )
    for entry in value:
        if not isinstance(entry, str) or not entry.strip():
            raise ConfigurationRefused("%s holds %r, and every entry has to be a name" % (where, entry))
    repeated = sorted({entry for entry in value if value.count(entry) > 1})
    if repeated:
        raise ConfigurationRefused(
            "%s holds %s more than once, and every list here is matched as a set: the "
            "second one reads like a decision and does nothing"
            % (where, ", ".join(repeated))
        )
    return list(value)


# What the parser actually recorded, and therefore the only spelling a rule can be matched
# against. `names_in` hands back one simple name at a time — a package prefix taken off,
# generics and arrays taken apart — and a module, an annotation and a supertype are each
# recorded the same way, so these two patterns are the mirror of that and not a second
# opinion about how Java spells a name.
_A_SIMPLE_NAME = re.compile(r"(?:[^\W\d]|\$)[\w$]*\Z")
_THE_END_OF_A_NAME = re.compile(r"[\w$]+\Z")
# What a module id looks like: the package the graph recorded, a dot, and the module's own
# name. A flow's entry point is the one place in this file that names a module rather than
# matching a fact about one, so it is the one place an id is written — and it is written in
# full, because two packages can hold a module of the same name and a flow pointing at
# whichever one was found first is a flow about nothing anybody chose.
_A_MODULE_ID = re.compile(r"(?:(?:[^\W\d]|\$)[\w$]*\.)+(?:[^\W\d]|\$)[\w$]*\Z")


def _the_name_in(written):
    """The simple name an unmatchable entry was most likely meant to be, or itself.

    Every spelling stripped here is one the *source* uses and this tool does not record:
    the `@` an annotation is written with, the arguments a supertype is parameterised
    with, the package a type is qualified by. Recovering it is what lets the refusal say
    "write JpaRepository instead" rather than only that something is wrong.
    """
    name = written.strip()
    if name.startswith("@"):
        name = name[1:].strip()
    name = name.split("<", 1)[0].strip()
    return name.rsplit(".", 1)[-1].strip()


def _matchable(names, where, shape, against):
    """Names refused unless the source could actually be spelled with one of them.

    A dot was once the whole of this check, and everything else a reader is likely to
    reach for went through: `@SpringBootApplication` and `JpaRepository<Deposit, Long>`
    are how the *source* writes the two rules this file ships with, so copying from the
    source is the natural mistake exactly as the qualified name was. Accepted quietly it
    is the worst kind of rule — the file names an exclusion, the graph reports it
    excluding nothing, the module it was written for is scored like anything else, and
    the run exits zero with nothing said.
    """
    unmatchable = [name for name in names if not shape.match(name)]
    if not unmatchable:
        return names
    meant = [_the_name_in(name) for name in unmatchable]
    advice = (
        "write %s instead" % ", ".join(sorted(set(meant)))
        if all(shape.match(name) for name in meant)
        else "one written here is letters, digits, _ and $, and nothing around them"
    )
    raise ConfigurationRefused(
        "%s holds %s, and every name here is matched against %s: %s"
        % (where, ", ".join(sorted(set(unmatchable))), against, advice)
    )


def _simple_names(value, where, may_be_empty=False):
    """Names matched against a whole name the parser read from the source."""
    return _matchable(
        _strings(value, where, may_be_empty),
        where,
        _A_SIMPLE_NAME,
        "a simple name the source was read with",
    )


def _name_endings(value, where):
    """Suffixes matched against the end of a module's own name.

    A suffix is not a whole name — `Service` is the point of writing one — so it is
    checked against what a name may end in rather than against what a name may be. It is
    still the same refusal: `Controller ` with a space after it ends no module's name.
    """
    return _matchable(
        _strings(value, where),
        where,
        _THE_END_OF_A_NAME,
        "the end of a simple name the source was read with",
    )


def _shape(value):
    return "missing" if value is None else "%r" % (value,)


def _is_of_kind(wanted, declared):
    return declared.kind in wanted, "kind is %s" % declared.kind


def _is_annotated_with(wanted, declared):
    hit = [name for name in wanted if name in declared.annotations]
    return bool(hit), "annotated with %s" % (hit[0] if hit else "")


def _extends_or_implements(wanted, declared):
    hit = [name for name in wanted if name in declared.supertypes]
    return bool(hit), "extends or implements %s" % (hit[0] if hit else "")


def _name_ends_with(wanted, declared):
    hit = [suffix for suffix in wanted if declared.name.endswith(suffix)]
    return bool(hit), "name ends with %s" % (hit[0] if hit else "")


class Condition:
    """One condition a rule can be written with: how it is checked, and how it is read.

    Both halves live in one entry because both are what a fifth condition has to arrive
    with. A table of readers with the checking done in a chain beside it is the same
    fallthrough one step along: a condition with no branch of its own would be validated
    by whatever the chain ended on, and every legal value written for it refused in a
    message about something else.
    """

    def __init__(self, met, valid):
        self.met = met
        self.valid = valid


def _java_kinds(value, where):
    return _known_kinds(_strings(value, where), where)


# Each condition a rule can be written with, in the order the evidence for an exclusion
# reads. A dictionary rather than a chain of comparisons, because the last branch of a
# chain is whatever fell through it: a fifth condition added without an entry of its own
# would silently become the fourth one, and every rule written with it would quietly
# match on suffixes instead.
#
# `_simple_names` is the check for every condition matched against a name the parser read
# from the source: it records each of those by simple name, so anything the source spells
# a name with and this tool does not record — a package prefix, an `@`, a supertype's type
# arguments — could never match anything. `nameEndsWith` matches part of such a name, so
# it is checked against what a name may end in instead.
CONDITIONS = {
    "kind": Condition(_is_of_kind, _java_kinds),
    "annotatedWith": Condition(_is_annotated_with, _simple_names),
    "extendsOrImplements": Condition(_extends_or_implements, _simple_names),
    "nameEndsWith": Condition(_name_ends_with, _name_endings),
}


class WhatIsReached:
    """What the file says an adapter is, what a persistent record is, and what a transaction is.

    Three rules rather than three judgements written into the analyser, for the same
    reason the exclusions are: a reader who thinks a repository is not an adapter, or that
    this application's records are marked with something else, edits them and runs the tool
    again. Each carries the sentence it is argued for with, and the page prints it.
    """

    def __init__(self, adapter, persistent_record, transaction, transaction_annotations):
        self.adapter = adapter
        self.persistent_record = persistent_record
        self.transaction = transaction
        self.transaction_annotations = transaction_annotations


class Reason:
    """One rule about what a reached thing is: the sentence for it, and what it matches."""

    def __init__(self, because, when):
        self.because = because
        self.when = when

    def matches(self, declared):
        """The evidence that this rule covers the module, or None if it says nothing about it."""
        return evidence_for(self.when, declared)


class Verdict:
    """One answer the deletion test can give, and the sentence it is argued for."""

    def __init__(self, verdict, because):
        self.verdict = verdict
        self.because = because


class DeletionTest:
    """Where the line is drawn between moving complexity and removing it.

    Three verdicts and the two thresholds that pick between them, every one of them read
    from the file. A reader who thinks a module has been judged unfairly moves
    `reachAtMost` or `callersAtLeast` there and runs the tool again; there is no number
    here to argue with, because there is no number here.
    """

    def __init__(self, pass_through, earns_its_keep, no_finding, per_method, never_below,
                 callers_at_least):
        self.pass_through = pass_through
        self.earns_its_keep = earns_its_keep
        self.no_finding = no_finding
        self.per_method = per_method
        self.never_below = never_below
        self.callers_at_least = callers_at_least

    def allowance_for(self, methods):
        """The most a module can coordinate behind this many methods and still concentrate nothing.

        A method apiece, and never less than what the file says one thing reached is worth
        on its own. That floor is the whole of what "coordinating one thing is coordinating
        nothing" means: a module presenting no method a caller can reach — every one of
        them private, its transaction the only thing it reaches — would otherwise be
        allowed nothing at all and read as concentrating something by reaching once.
        """
        return max(self.never_below, self.per_method * methods)


class Refusals:
    """What a refusal is worth to a caller, and what the two disagreements are called.

    A refusal is interface: a caller who does not know a module can answer with
    `WithdrawalRefused` has not learned the module. So it is priced with everything else a
    caller must learn — and then reported as a band of its own, because a module whose
    interface is wide because it is honest about how it can fail should be distinguishable
    from one that is merely wide. Folded into a single number it is not, and a module is
    then paid for saying nothing about its failure modes.

    Both findings are named and argued for in the file rather than here, for the same
    reason the deletion test's verdicts are: a machine's finding is worth exactly as much
    as the rule a reader can point at behind it.
    """

    def __init__(self, because, documented_never_raised, raised_never_documented):
        self.because = because
        self.documented_never_raised = documented_never_raised
        self.raised_never_documented = raised_never_documented


class Finding:
    """One disagreement the file names, and the sentence it is argued for."""

    def __init__(self, finding, because):
        self.finding = finding
        self.because = because


class Flow:
    """One business event, named by the single call a caller makes to enter it.

    An entry point and nothing else. The modules a flow passes through are not written
    here and cannot be: they are walked out of the graph's own reach, which is what makes
    a flow a reading of the code rather than a second description of it sitting beside
    the code and going stale against it. The only thing this file decides is where the
    walk starts.

    The method is part of the entry point rather than decoration on it. Three of this
    application's flows are entered through modules a single caller holds, and a flow
    named by its module alone would be a claim about everything that module does — while
    a method that has been renamed out from under the flow is exactly the drift a flow is
    supposed to fail on rather than quietly survive.
    """

    def __init__(self, flow, because, module, method):
        self.flow = flow
        self.because = because
        self.module = module
        self.method = method


class Rules:
    """The scoring rules one configuration file holds, ready to be applied to a module."""

    def __init__(self, path, weights, reachable_from_outside, already_known, exclusions,
                 reached, deletion_test, refusals, flows):
        self.path = path
        self.weights = weights
        self.reachable_from_outside = reachable_from_outside
        self.already_known = already_known
        self.exclusions = exclusions
        self.reached = reached
        self.deletion_test = deletion_test
        self.refusals = refusals
        self.flows = flows

    def excluded_by(self, declared):
        """The first rule that says this module is never scored, or None if it is scored.

        First in the file's own order, so two rules that both cover a module always name
        the same one of them, and a reader can see which by reading down the file.

        The rule is named here and its wording is not, because the wording belongs to the
        rule rather than to the module: written out against all thirty-six of them, one
        reworded sentence would be thirty-six lines of diff saying nothing about the code.
        """
        for exclusion in self.exclusions:
            evidence = exclusion.matches(declared)
            if evidence is not None:
                log.debug(
                    "module excluded from scoring name=%s rule=%s matched=%s",
                    declared.name,
                    exclusion.rule,
                    evidence,
                )
                return {"rule": exclusion.rule, "matched": evidence}
        return None

    def interface_of(self, declared, scored):
        """Everything a caller must learn to use this module, and what that costs.

        The methods, the types and the refusals are recorded whether or not the module is
        scored — an excluded module is drawn with its interface visible, so a reader can
        see what the rule decided not to measure — but the cost of an unscored one is
        absent rather than zero, because it was never counted, not counted to nothing.
        Absent all the way down: a per-method cost published under a module whose own cost
        is `null` is a score for a module the same document says has none, and an agent
        reading the graph could add those parts up into a number nobody ever decided to
        give it.

        The refusals are counted into `cost` and reported beside it as `refusalCost`, with
        the rest of the total as `costWithoutRefusals`. Both, rather than either: a refusal
        is something a caller must learn, so leaving it out of the total would pay a module
        for saying nothing about how it fails — and folding it in without saying how much
        of the total it is would make an honestly-wide interface indistinguishable from a
        merely wide one, which is the whole reason the band exists.
        """
        methods = sorted(
            (method for method in declared.methods if method.visibility in self.reachable_from_outside),
            key=lambda method: (method.name, method.parameters),
        )
        crossing = self._types_crossing_the_seam(methods, declared.type_parameters)
        refusals = self._refusals_of(declared, methods)
        cost = None
        refusal_cost = None
        without_refusals = None
        if scored:
            without_refusals = sum(self._cost_of(method) for method in methods) + sum(
                self.weights["typeToLearn" if type_["mustBeLearned"] else "typeEveryCallerAlreadyKnows"]
                for type_ in crossing
            )
            refusal_cost = self.weights["refusal"] * len(refusals)
            cost = without_refusals + refusal_cost
        # Logged for a module that is never scored too, and that is the case worth having
        # it for: the page shows such a module the name of the rule and nothing else, so
        # this is the only place a reader can check that the rule declined something real
        # rather than something the parser lost. Three of the misread interfaces found on
        # this branch were on records and enums, which the committed rules never price.
        log.debug(
            "interface read name=%s methods=%d parameters=%d typesToLearn=%d "
            "typesEveryCallerAlreadyKnows=%d refusals=%d cost=%s refusalCost=%s",
            declared.name,
            len(methods),
            sum(len(method.parameters) for method in methods),
            sum(1 for type_ in crossing if type_["mustBeLearned"]),
            sum(1 for type_ in crossing if not type_["mustBeLearned"]),
            len(refusals),
            cost if scored else "none, never scored",
            refusal_cost if scored else "none, never scored",
        )
        return {
            "methods": [
                {
                    "name": method.name,
                    "visibility": method.visibility,
                    "parameters": list(method.parameters),
                    "returns": method.returns,
                    "documentedRefusals": list(method.documented_refusals),
                    "cost": self._cost_of(method) if scored else None,
                }
                for method in methods
            ],
            "typesCrossingTheSeam": crossing,
            "refusals": refusals,
            "refusalCost": refusal_cost,
            "costWithoutRefusals": without_refusals,
            "cost": cost,
        }

    def _refusals_of(self, declared, methods):
        """Every refusal this module can answer with, and which side of the seam named it.

        The union of the two sides on purpose. A refusal the documentation promises is one
        a caller writes a `catch` for whether or not the code can still raise it, and a
        refusal the code raises is one they meet whether or not anybody wrote it down — so
        both are things a caller must learn, and both are priced. Which side named it is
        carried on the entry rather than resolved into a single answer here, because that
        is what a disagreement is made of, and because the page and the findings below both
        read it.

        Documented is read off the members a caller can reach, and off nothing else: a
        `@throws` on a private helper documents that helper to whoever maintains the
        module, not the module to whoever calls it. A constructor a caller can reach is one
        of those members. `new AmountOfMoney(-1)` refuses, the refusal is one the caller
        has to know about, and the constructor's own body is already read into what the
        module raises — so reading its `@throws` too is what keeps the two sides of one
        refusal symmetrical.

        `checked` is whether the two sides could be held against each other at all. A
        refusal the body raises is checked by that fact. A refusal only the documentation
        promises is checked only where there is an implementation here to read and all of
        it was read: a `@throws` on a method with no body — an interface's, an abstract
        one's — is a promise made to whoever implements it, and a body carrying a throw
        this parser could not name may well be raising exactly what was promised. Saying
        "never raised" in either case is a machine accusing a module that kept its word,
        which is the one thing this feature cannot afford.
        """
        documented_by = {}
        an_implementation_to_read = set()
        for member in self._documenters_of(declared, methods):
            for name in member.documented_refusals:
                documented_by.setdefault(name, []).append(member.name)
                if member.has_a_body:
                    an_implementation_to_read.add(name)
        refusals = []
        for name in sorted(set(documented_by) | set(declared.raises)):
            raised = name in declared.raises
            checked = raised or (
                not declared.throws_not_read and name in an_implementation_to_read
            )
            if not checked:
                # The one decision here that ends in silence — no finding, and a refusal
                # that looks on the card like one whose two sides agree. So it says which
                # of the two reasons it was, at the line where it was decided.
                log.debug(
                    "refusal not held against the implementation module=%s refusal=%s "
                    "documentedBy=%s reason=%s",
                    declared.name,
                    name,
                    ",".join(sorted(set(documented_by.get(name, ())))),
                    "%d throw(s) in this body name a type only javac could resolve, so "
                    "what it raises was not read whole" % declared.throws_not_read
                    if declared.throws_not_read
                    else "every member promising it is a signature with no body of its "
                    "own, so the promise is to whoever implements it",
                )
            refusals.append(
                {
                    "name": name,
                    "documented": name in documented_by,
                    "documentedBy": sorted(set(documented_by.get(name, ()))),
                    "raised": raised,
                    "checked": checked,
                }
            )
        return refusals

    def _documenters_of(self, declared, methods):
        """Every member of this module whose javadoc documents the module to a caller.

        The methods arrive already narrowed to the ones a caller can reach; the
        constructors are narrowed here, by the same rule and for the same reason. A
        private constructor is a factory's business and nobody else's, and what it
        promises is a note to whoever maintains the module.
        """
        return list(methods) + [
            constructor
            for constructor in declared.constructors
            if constructor.visibility in self.reachable_from_outside
        ]

    def findings_of(self, name, refusals):
        """Where this module's documentation and its implementation disagree, both sides named.

        Two disagreements, one in each direction, and each finding carries the whole of
        both sides: the refusal, whether the documentation promises it and which methods
        promise it, and whether the implementation raises it. A finding that named only the
        side it was unhappy with would be a machine saying "wrong" without saying against
        what.

        Read for every module, scored or not. A finding is not a score: it is two things in
        the source disagreeing, and a rule that declines to *price* a record has said
        nothing about whether that record's javadoc tells the truth.

        A refusal this tool could not check is not a disagreement and gets no finding. The
        whole argument for this feature is that a machine catches a stale comment, and a
        machine that accuses a module which kept its word stops being read — so where the
        implementation was not there to read, or not read whole, the module is left alone
        and the reason is logged. Both sides are still printed on the card, so a reader
        can see the promise and go and check it themselves.
        """
        findings = []
        for refusal in refusals:
            if not refusal["checked"]:
                # Logged with its reason where the reading was taken, in `_refusals_of`,
                # rather than said twice.
                continue
            if refusal["documented"] and not refusal["raised"]:
                named = self.refusals.documented_never_raised
            elif refusal["raised"] and not refusal["documented"]:
                named = self.refusals.raised_never_documented
            else:
                continue
            findings.append(
                {
                    "finding": named.finding,
                    "because": named.because,
                    "refusal": refusal["name"],
                    "documented": refusal["documented"],
                    "documentedBy": list(refusal["documentedBy"]),
                    "raised": refusal["raised"],
                }
            )
            log.debug(
                "refusals disagree module=%s refusal=%s finding=%s documented=%s "
                "documentedBy=%s raised=%s",
                name,
                refusal["name"],
                named.finding,
                refusal["documented"],
                ",".join(refusal["documentedBy"]) or "nothing a caller can reach",
                refusal["raised"],
            )
        return findings

    def reach_of(self, declared, module_id, package, imports, modules, nested=(), above=None):
        """Everything this module coordinates on its caller's behalf, one entry apiece.

        Reach is the numerator of depth, and it is a count of *distinct things* rather
        than of anything a keyboard produces: the collaborating modules it calls, the
        adapters it drives, the persistent records it writes, and the transaction it
        establishes. Writing the same call ten more times, or a hundred more lines around
        it, moves none of them — which is precisely why reach replaced the line count that
        the first design measured depth with.

        Nothing is reached that this graph does not hold. A name is followed to a module
        through the file's own imports and its package, exactly as the compiler would
        follow it, and a name that resolves to nothing here — `Clock`, `BigDecimal`, a
        type from a dependency — is not counted at all. Reading it as reached would put a
        line in the fan pointing at something a reader could not click, and would let a
        module's score rise by importing more of the JDK.

        Almost everything left out is left out in the same direction, so reach reads as a
        floor: a collaborator handed in as a parameter rather than held as a field, and a
        record this module loads and mutates rather than creates, are coordination this
        tool cannot see and does not guess at. So are the spellings of a call named on the
        page — one written out in full, one through something itself reached through
        something else, and a statically imported member whose name the module's own body
        also declares.

        Some readings err the other way, and they are the reason this paragraph says
        "almost". A call is followed through a field by the *name* it is written against,
        and an inner scope can borrow a field's name: `void go(Other repo)` in a module
        holding a `Repo repo` puts `repo.ping()` down as a call on the field's type, and a
        local, a `catch`'s variable and a nested class's own field do it the same way. An
        enum constant carrying arguments is written the way a call is, so a file that
        statically imports a member of that spelling is read as calling it. And a supertype
        this graph does not hold cannot be read at all, so a member type it would have
        shadowed a name with is not seen — where it is a *method* that would have been
        shadowed, the static-import reading is declined outright instead.

        Each needs something this reading does not have: which declaration was in scope
        where a call was written, or the body of a type outside this source tree. So they
        are named on the page instead, beside the omissions above and marked as the ones
        that can overstate. They are named without a count in front of them, deliberately:
        naming is what makes the floor's edge visible, while a count is a claim about every
        reading nobody has found yet, and three counts written here have each been
        falsified by the next person to look. A floor whose edge a reader cannot see is not
        one they can trust, and neither is a page that promises a floor while holding a
        reading that is not one.
        """
        reached = {}

        def note(target_id, kind, matched):
            here = reached.get(target_id)
            if here is not None and _REACH_KINDS.index(here["kind"]) <= _REACH_KINDS.index(kind):
                return
            reached[target_id] = {
                "kind": kind,
                "name": modules[target_id].name,
                "moduleId": target_id,
                "matched": matched,
            }

        inherited = self._inherited_by(module_id, modules, above or {})

        # Every name that means something other than a module here, whatever an import or
        # the package would otherwise offer for it: a type this module declares inside
        # itself, a type it inherits from a module above it, and one of its own type
        # parameters. `class Till<Receipt>` holding a `Receipt held` holds one of
        # whatever its caller filled the hole with, not the `Receipt` next door — the
        # tool already knows a type variable is not a type to follow when it counts what
        # crosses the seam, and forgetting it here drew a line to a card the source names
        # nowhere.
        shadowed = (
            {name.rsplit(".", 1)[-1] for name in nested}
            | set(declared.type_parameters)
            | inherited.nested
        )

        def resolve(name):
            # A name written out in full names one thing and nothing else: the module of
            # that id, if this source tree holds one. `new other.Receipt()` is `other`'s
            # `Receipt` and never this package's, and cutting the package off to look the
            # rest up here is how it became this package's — a different module, of a
            # different kind, with an evidence string a reader could check and find false.
            if "." in name:
                return name if name in modules and name != module_id else None
            # A type this module declares inside itself shadows every name an import or
            # the package could offer, which is how Java reads it: `Kind.of(x)` written in
            # a module that nests a `Kind` means that one, not the top-level `Kind` next
            # door. A nested type is not a module, so the name reaches nothing. A member
            # type is inherited as surely as a method is, so a `Kind` nested in a module
            # this one extends shadows the same way, and so does this module's own type
            # parameter.
            #
            # Every nested type is read as shadowing, however deep it sits, though one
            # declared two levels down is only in scope in part of the body. That drops a
            # reach the source has rather than inventing one it does not, which is the
            # direction every reading here is willing to be wrong in.
            if name in shadowed:
                log.debug(
                    "name not followed name=%s in=%s, because a type of that name is "
                    "declared inside this module or inherited by it, or is one of its "
                    "type parameters, and none of those is a module",
                    name, declared.name,
                )
                return None
            for candidate in javasource.candidate_ids(name, package, imports):
                if candidate in modules and candidate != module_id:
                    return candidate
            return None

        held = {field.name: field for field in declared.fields}
        for receiver in declared.receivers:
            if receiver in held:
                # Every name the field's type is spelled with, because a module keeps a
                # collaborator in whatever shape it needs it in: `List<AScheduledJob>` is
                # held to reach the jobs, and reading only the `List` would report a module
                # driving a collection and nothing else.
                #
                # Spelled as the source spelled them, package or enclosing type and all.
                # `names_in` answers with the simple name of each, which is what a caller
                # reading an interface has to learn and the wrong thing entirely here: cut
                # back that way, `private final other.Receipt receipt` resolved against
                # this file's own package, and the fan drew a line to `shop.Receipt` under
                # an evidence string that said in the same breath that the field held an
                # `other.Receipt` — the page contradicting itself inside one sentence.
                written = held[receiver].written
                found = [
                    (name, "called through the field %s, which holds a %s" % (receiver, written))
                    for name in javasource.written_names_in(written)
                ]
            else:
                found = [(receiver, "called on %s" % receiver)]
            for name, matched in found:
                target = resolve(name)
                if target is not None:
                    note(target, self._what_is_reached(modules[target]), matched)

        # A member imported statically is written with no receiver in front of it, so the
        # only thing tying `asMoney(...)` to the module that declares it is the import.
        #
        # A declaration is written the same way: `long of(long cents)` puts a name in
        # front of brackets having called nothing at all. Read straight, that credits a
        # module which merely imports a member and happens to declare something of the
        # same name with reaching the module the import came from — a fan line to a card
        # it never calls, and an evidence string that is a false statement about the
        # source. So a name this module's body declares is never read as a call to the
        # import that shares its spelling.
        #
        # `declares` is taken over the *whole* body, one brace deeper included, because
        # `called` is: reading only the top-level type's own methods left the same false
        # statement coming out of a module whose declaration sat in a nested record or a
        # helper class, which is everyday Java rather than an exotic shape.
        #
        # For the module's own methods this is what Java does — a method shadows a static
        # import of its name, so `of(id)` there really is this module's own `of` and
        # really does reach nothing. For a declaration further in it is a floor: Java
        # would read the call as the import, and this drops it. Telling the two apart
        # means knowing which brace every call was written under as well as every
        # declaration, and half of that is a guess.
        #
        # The cost of that is a module which both declares and calls one name, whose real
        # call goes uncounted. Reach is a floor and this keeps it one: it errs towards
        # saying less about the source than the source says, never towards saying
        # something the source does not.
        #
        # A method a module inherits shadows a static import of the same name exactly as
        # one it writes does, so the names declared by every module above it are read
        # alongside its own, and so are the names of the types it declares inside itself:
        # a nested type's constructor is written like a call, and its own name is what it
        # is written with.
        #
        # A supertype this graph does not hold — a framework class, a JDK interface — is a
        # body of declarations that cannot be read at all, so this reading is not made for
        # a module that has one. The cost is a real statically imported call going
        # uncounted on such a module; the alternative is a fan line to a card it never
        # calls, whenever a name it inherits happens to be spelled like a member it
        # imports.
        declares = (
            set(declared.declares)
            | {declared.name}
            | {name.rsplit(".", 1)[-1] for name in nested}
            | inherited.declares
        )
        for imported in imports:
            if imported.member not in declared.called:
                continue
            if inherited.opaque:
                log.debug(
                    "static import not read as a call name=%s member=%s from=%s, because "
                    "this module is built on a type this graph does not hold and what "
                    "that type declares cannot be read here",
                    declared.name, imported.member, imported.type,
                )
                continue
            if imported.member in declares:
                log.debug(
                    "static import not read as a call name=%s member=%s from=%s, because "
                    "this module's body declares that name itself and a declaration is "
                    "not a call",
                    declared.name, imported.member, imported.type,
                )
                continue
            # `imported.type` is already the whole id of the type the member was imported
            # from, so it is handed over whole. Cut back to its last word it was resolved
            # again from scratch — against this file's imports and its package — which is
            # the mistake `resolve`'s dotted branch exists to prevent: `import static
            # com.external.Money.of` credited a module in a package this tree does not
            # even hold to a same-named `shop.Money` sitting next to the caller.
            target = resolve(imported.type)
            if target is not None:
                note(target, self._what_is_reached(modules[target]),
                     "calls %s, imported statically from it" % imported.member)

        # Building a collaborator is coordinating it. `new B(a)` and `B.of(a)` are the
        # same module reached, spelled two ways, and counting only the second made a
        # module's reach — and the leverage the page invites a reader to check — turn on
        # which spelling somebody happened to prefer. `SavingsAccountController` was the
        # specimen of it: three of its reaches are response types reached through a
        # static factory, while the one it builds with `new` in the same file counted for
        # nothing.
        #
        # A record says how it was found, because "builds one" is the only evidence a
        # record can be reached by that a reader cannot check by looking for a call.
        for built in declared.constructed:
            target = resolve(built)
            if target is None:
                continue
            evidence = self.reached.persistent_record.matches(modules[target])
            if evidence is not None:
                note(target, "record", "builds one: %s" % evidence)
            else:
                note(target, self._what_is_reached(modules[target]), "builds one")

        entries = sorted(reached.values(), key=lambda entry: (entry["kind"], entry["name"]))
        establishes = self._transaction_in(declared)
        if establishes is not None:
            entries.append(
                {
                    "kind": "transaction",
                    "name": "a transaction",
                    "moduleId": None,
                    "matched": establishes,
                }
            )
        log.debug(
            "reach read name=%s modules=%d adapters=%d records=%d transaction=%s",
            declared.name,
            sum(1 for entry in entries if entry["kind"] == "module"),
            sum(1 for entry in entries if entry["kind"] == "adapter"),
            sum(1 for entry in entries if entry["kind"] == "record"),
            "yes" if establishes else "no",
        )
        return {"count": len(entries), "reaches": entries}

    def _inherited_by(self, module_id, modules, above, seen=None):
        """What a module is handed by the types it is built on, and whether all of it was read.

        Java gives a subclass its supertypes' methods and their member types, and each of
        those shadows whatever an import or the file's own package would otherwise offer
        for that name. Read from the one file in front of it, this reading credited a
        module with reaching the type a static import came from for calling a method it
        inherits, and followed `Row.of(1)` to a top-level `Row` where javac binds it to
        the `Row` nested in the class above.

        `opaque` is the honest half of it. A supertype this graph does not hold — a Spring
        class, a JDK interface — is a body of declarations that cannot be read here at
        all, and the one reading with nothing else to stand on when a name is inherited is
        the static import, which follows a name written with no receiver in front of it.
        So it says so, and `reach_of` declines that reading rather than guessing.

        A supertype named by a simple name is resolved against the file that names it, the
        same way any other name is, and a cycle in the source is walked once and left.
        """
        seen = set() if seen is None else seen
        declares, nested, opaque = set(), set(), False
        if module_id in seen or module_id not in modules:
            return _Inherited(declares, nested, opaque)
        seen.add(module_id)
        package, imports, _ = above.get(module_id, ("", (), ()))
        for supertype in modules[module_id].supertypes:
            target = _module_named(supertype, package, imports, modules)
            if target is None or target == module_id:
                log.debug(
                    "supertype not read name=%s extends=%s, because this graph holds no "
                    "module of that name and what it declares cannot be read from here",
                    modules[module_id].name, supertype,
                )
                opaque = True
                continue
            higher = modules[target]
            declares |= set(higher.declares) | {method.name for method in higher.methods}
            declares.add(higher.name)
            nested |= {
                name.rsplit(".", 1)[-1] for name in above.get(target, ("", (), ()))[2]
            }
            further = self._inherited_by(target, modules, above, seen)
            declares |= further.declares
            nested |= further.nested
            opaque = opaque or further.opaque
        return _Inherited(declares, nested, opaque)

    def _what_is_reached(self, declared):
        """What a thing this module reaches is: a persistent record, an adapter, or a module.

        Read off the rules in the file, in the order they settle: an entity reached at all
        is a row this module is handling — whether it built one or asked the type to — and
        an interface whose implementation is generated is the storage it is handling it
        through. Everything else is a module it calls. Each answer only names what the
        thing is; how it was found is recorded beside it, so a reader can see the rule and
        the evidence for it separately.
        """
        for kind, rule in (
            ("record", self.reached.persistent_record),
            ("adapter", self.reached.adapter),
        ):
            if rule.matches(declared) is not None:
                return kind
        return "module"

    def _transaction_in(self, declared):
        """How this module establishes a transaction, or None when it establishes none.

        The module's own mark or any one of its methods', because either is how the one
        transaction a caller gets is established, and a caller who does not have to open
        one is a caller who does not have to know there is one.
        """
        for name in sorted(self.reached.transaction_annotations):
            if name in declared.annotations:
                return "the module is annotated with %s" % name
            on = sorted(
                method.name for method in declared.methods if name in method.annotations
            )
            if on:
                return "%s is annotated with %s" % (on[0], name)
        return None

    def depth_of(self, reach, interface):
        """Leverage: what a caller can set in motion per unit of interface they must learn.

        Reported with both numbers it came from rather than as a bare ratio, so a reader
        can check it and argue with it. Never lines over lines: that measure rewards
        padding, and under it the largest file in a repository is its deepest module.

        A module nobody scores has no denominator, and neither has one whose interface
        costs nothing this tool can count — dividing by that would report the leverage of
        a module as infinite on the strength of a bar that says only that there was
        nothing on it to count. The reach and the cost are still both reported.
        """
        cost = interface["cost"]
        return {
            "reach": reach["count"],
            "interfaceCost": cost,
            "leverage": round(reach["count"] / cost, 2) if cost else None,
        }

    def deletion_test_of(self, name, reach, callers, interface, scored):
        """Would deleting this module concentrate complexity, or merely move it to its callers?

        Mechanical, from three counts a reader can check on the same card: how much the
        module reaches, how many methods it presents to be reached through, and how many
        other modules go through it. A module that coordinates no more things than the
        methods it presents has concentrated nothing — one call learned per thing the
        caller could have reached themselves — and when two or more modules go through
        such a module, deleting it moves that coordination to them rather than removing
        it. That is the pass-through. A module that coordinates more than it presents is
        concentrating, and deleting it would put all of it back into every caller.

        The third answer is the honest one, and it is why there are three rather than the
        two the shape suggests: a module that concentrates nothing and has fewer than two
        callers has nowhere for its complexity to move to, and calling it earned would be
        a claim about callers that do not exist.

        A module no rule scores gets no verdict at all, for the same reason it gets no
        cost: it was never measured, and a mechanical judgement on something the rules
        declined to price would be the score they declined to give, wearing a word. The
        three counts are still reported — they are facts about the source rather than
        judgements, and the rule that produced them is public, so a reader can apply it
        themselves and see what it would have said.

        Every threshold comes from the file. Nothing here decides how much reach is
        enough, which is the point: "this is a pass-through" is a finding a reader can
        argue with by editing a number they can point at.
        """
        methods = len(interface["methods"])
        allowance = self.deletion_test.allowance_for(methods)
        concentrates = reach["count"] > allowance
        gone_through = callers["count"] >= self.deletion_test.callers_at_least
        if not scored:
            answer = None
        elif concentrates:
            answer = self.deletion_test.earns_its_keep
        elif gone_through:
            answer = self.deletion_test.pass_through
        else:
            answer = self.deletion_test.no_finding
        log.debug(
            "deletion test read name=%s reach=%d methods=%d allowance=%d callers=%d verdict=%s",
            name,
            reach["count"],
            methods,
            allowance,
            callers["count"],
            answer.verdict if answer else "none, never scored",
        )
        return {
            "verdict": answer.verdict if answer else None,
            "because": answer.because if answer else None,
            "reach": reach["count"],
            "methods": methods,
            "callers": callers["count"],
        }

    def _cost_of(self, method):
        return self.weights["method"] + len(method.parameters) * self.weights["parameter"]

    def _types_crossing_the_seam(self, methods, of_the_module):
        """Every distinct type a caller meets in a parameter or a return, counted once.

        A type is named by the simple name it is written with and stripped of the shapes
        it is carried in: `List<Optional<Customer>>` is `List`, `Optional` and `Customer`,
        because a caller who has to unwrap the first two still has to learn the third.

        A type variable is not one of them. `<T> T first(List<T> of)` hands back whatever
        the caller passed in, and charging them for learning `T` would price the letter
        rather than a type — the module's own `<T>` and each method's are both left out.

        Neither is `void`, and not because of a weight: a method that hands nothing back
        has nothing crossing the seam on the way out, so there is no type there for any
        weight to price. Java writes it where a return type goes, which is how it got
        counted — nine modules on the committed page carried it, and a caller of
        `public void f()` was charged for one type where they meet none.
        """
        names = set()
        for method in methods:
            variables = set(of_the_module) | set(method.type_parameters)
            crossing = list(method.parameters)
            if method.returns != javasource.NOTHING_RETURNED:
                crossing.append(method.returns)
            for written in crossing:
                names.update(
                    name for name in javasource.names_in(written) if name not in variables
                )
        return [
            {"name": name, "mustBeLearned": name not in self.already_known}
            for name in sorted(names)
        ]


def load(path=None):
    """The rules in this file, or a refusal naming what is wrong with it.

    Every check here exists because the alternative is a silent one: a misspelled weight
    that quietly scores nothing, a rule with no name that excludes a module the graph
    then cannot explain, a condition this tool does not understand that reads as "always
    true" and takes half the application off the scored list.
    """
    # `is None` rather than falsy: `--scoring ""` is a path this tool cannot read, and
    # answering it with the built-in file is the reading `ConfigurationRefused` calls the
    # worst of both — an output that looks like a score somebody chose, while the file
    # they chose it in was never opened.
    path = DEFAULT_CONFIGURATION if path is None else path
    try:
        with open(path, "rb") as handle:
            document = json.loads(handle.read().decode("utf-8"))
    except OSError as unreadable:
        raise ConfigurationRefused(
            "could not be opened: %s" % (unreadable.strerror or unreadable.__class__.__name__)
        )
    except UnicodeDecodeError as broken:
        raise ConfigurationRefused("not valid UTF-8: %s" % broken.reason)
    except ValueError as broken:
        raise ConfigurationRefused("not valid JSON: %s" % broken)

    _an_object(document, "the configuration")
    # The schema first, and then what the document names. A file written for a later
    # schema will hold keys this tool has never heard of, and answering it with "names X,
    # which this tool does not read" sends its author to delete a key their own tool
    # needs, when what they have to be told is that this tool is the old one.
    if document.get("schema") != SCHEMA:
        raise ConfigurationRefused(
            "schema is %r, and this tool reads %r" % (document.get("schema"), SCHEMA)
        )
    _only(
        document,
        ("schema", "interfaceCost", "refusals", "reach", "deletionTest", "flows",
         "exclusions"),
        "the configuration",
    )

    cost = document.get("interfaceCost")
    _an_object(cost, "interfaceCost")
    _only(cost, ("weights", "reachableFromOutside", "typesEveryCallerAlreadyKnows"), "interfaceCost")

    weights = cost.get("weights")
    _an_object(weights, "interfaceCost.weights")
    _only(weights, WEIGHTS, "interfaceCost.weights")
    for name in WEIGHTS:
        if not isinstance(weights.get(name), int) or isinstance(weights.get(name), bool):
            raise ConfigurationRefused(
                "interfaceCost.weights.%s is %r, and a weight is a whole number"
                % (name, weights.get(name))
            )
        if weights[name] < 0:
            raise ConfigurationRefused(
                "interfaceCost.weights.%s is %d, and a negative weight would pay a module "
                "for widening its interface" % (name, weights[name])
            )

    # Its own two refusals rather than the ones a list of names gets: a visibility is not
    # a rule, so "a rule with no name to match on could never fire" answers an empty list
    # here with a sentence about something else.
    reachable = _strings(
        cost.get("reachableFromOutside"), "interfaceCost.reachableFromOutside", may_be_empty=True
    )
    if not reachable:
        raise ConfigurationRefused(
            "interfaceCost.reachableFromOutside is empty, and a method no caller can reach "
            "is a method nobody has to learn: every interface would read as empty and every "
            "bar on the page would be nothing"
        )
    unknown = [visibility for visibility in reachable if visibility not in VISIBILITIES]
    if unknown:
        raise ConfigurationRefused(
            "interfaceCost.reachableFromOutside names %s, and a method is %s"
            % (", ".join(sorted(unknown)), " or ".join(VISIBILITIES))
        )
    # Empty is allowed here and nowhere else: "charge a caller for every type they
    # meet" is a position somebody can hold and argue for, while an empty list anywhere a
    # rule is matched would be a condition that could never hold.
    known = _simple_names(
        cost.get("typesEveryCallerAlreadyKnows"),
        "interfaceCost.typesEveryCallerAlreadyKnows",
        may_be_empty=True,
    )

    refusals = _refusals(document.get("refusals"))
    reached = _reach(document.get("reach"))
    deletion_test = _deletion_test(document.get("deletionTest"))
    flows = _flows(document.get("flows"))
    exclusions = _exclusions(document.get("exclusions"))
    rules = Rules(
        path, dict(weights), frozenset(reachable), frozenset(known), exclusions, reached,
        deletion_test, refusals, flows,
    )
    log.debug(
        "scoring rules read weights=%s reachableFromOutside=%s typesAlreadyKnown=%d "
        "transaction=%s rules=%s",
        ",".join("%s=%d" % (name, weights[name]) for name in WEIGHTS),
        ",".join(sorted(reachable)),
        len(known),
        ",".join(sorted(reached.transaction_annotations)),
        ",".join(exclusion.rule for exclusion in exclusions),
    )
    log.debug(
        "deletion test rules read verdicts=%s reachAtMostPerMethod=%d "
        "reachAtMostNeverBelow=%d callersAtLeast=%d",
        ",".join(
            verdict.verdict
            for verdict in (
                deletion_test.pass_through,
                deletion_test.earns_its_keep,
                deletion_test.no_finding,
            )
        ),
        deletion_test.per_method,
        deletion_test.never_below,
        deletion_test.callers_at_least,
    )
    log.debug(
        "refusal rules read weight=%d findings=%s,%s",
        weights["refusal"],
        refusals.documented_never_raised.finding,
        refusals.raised_never_documented.finding,
    )
    for flow in flows:
        log.debug(
            "flow read flow=%s entryPoint=%s.%s",
            flow.flow,
            flow.module,
            flow.method,
        )
    return rules


def _flows(listed):
    """The business events this file asks to be traced, each by its entry point alone.

    A list rather than an object, because the order the file writes them in is the order
    they are offered in: which flow a reader is shown first is a decision somebody made,
    and sorting them here would take it away from them.

    Allowed to be empty and required to be present, which are different answers. A file
    holding `"flows": []` is one whose author decided this application has no event worth
    tracing — a position somebody can hold and argue for. A file with no `flows` key at
    all has decided nothing, and reading that as "no flows" would put a page with no flows
    on it in front of a reader with nothing anywhere saying why.

    Nothing here is resolved against any source: this function only knows what a flow
    *says*. Whether the module exists, whether the method is one a caller can reach and
    what the flow passes through are all questions about a graph, and they are answered
    where the graph is.
    """
    if not isinstance(listed, list):
        raise ConfigurationRefused("flows is %s, and it has to be a list" % _shape(listed))
    flows = []
    named = set()
    for index, entry in enumerate(listed):
        where = "flows[%d]" % index
        _an_object(entry, where)
        _only(entry, ("flow", "because", "entryPoint"), where)
        for field in ("flow", "because"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                raise ConfigurationRefused(
                    "%s.%s is %r, and a flow nobody can name is one nobody can choose"
                    % (where, field, entry.get(field))
                )
        if entry["flow"] in named:
            raise ConfigurationRefused(
                "%s.flow is %r, which another flow is already called: a reader choosing "
                "one of them could not tell which they had chosen" % (where, entry["flow"])
            )
        named.add(entry["flow"])
        entry_point = entry.get("entryPoint")
        _an_object(entry_point, "%s.entryPoint" % where)
        _only(entry_point, ("module", "method"), "%s.entryPoint" % where)
        module = entry_point.get("module")
        if not isinstance(module, str) or not _A_MODULE_ID.match(module.strip()):
            raise ConfigurationRefused(
                "%s.entryPoint.module is %r, and a flow starts at one module written in "
                "full — its package, a dot, and its name — because two packages can hold "
                "a module of the same name" % (where, module)
            )
        method = entry_point.get("method")
        if not isinstance(method, str) or not _A_SIMPLE_NAME.match(method.strip()):
            raise ConfigurationRefused(
                "%s.entryPoint.method is %r, and a flow is entered by calling one method "
                "on that module: without one the flow would be a claim about everything "
                "the module does" % (where, method)
            )
        flows.append(Flow(entry["flow"], entry["because"], module.strip(), method.strip()))
    return flows


def _refusals(entry):
    """What a refusal costs a caller to learn, and what each disagreement is called.

    Required rather than defaulted, like everything else here. A missing section would not
    read as "say nothing about refusals": it would read as two findings with no words on
    them, reported against modules whose authors could not go and read the rule that named
    them.
    """
    _an_object(entry, "refusals")
    _only(entry, ("because", "documentedNeverRaised", "raisedNeverDocumented"), "refusals")
    because = _a_sentence(entry, "refusals")

    findings = {}
    for name in ("documentedNeverRaised", "raisedNeverDocumented"):
        where = "refusals.%s" % name
        answer = entry.get(name)
        _an_object(answer, where)
        _only(answer, ("finding", "because"), where)
        if not isinstance(answer.get("finding"), str) or not answer["finding"].strip():
            raise ConfigurationRefused(
                "%s.finding is %r, and a finding with nothing written on it is one no "
                "reader could act on" % (where, answer.get("finding"))
            )
        findings[name] = Finding(answer["finding"], _a_sentence(answer, where))

    if findings["documentedNeverRaised"].finding == findings["raisedNeverDocumented"].finding:
        raise ConfigurationRefused(
            "refusals.raisedNeverDocumented.finding is %r, which the other finding is "
            "already called: a module given one of them could not be told from the other, "
            "and the two say opposite things about the same refusal"
            % findings["raisedNeverDocumented"].finding
        )
    return Refusals(
        because,
        documented_never_raised=findings["documentedNeverRaised"],
        raised_never_documented=findings["raisedNeverDocumented"],
    )


def _deletion_test(entry):
    """The three verdicts the file renders, and the two thresholds that pick between them.

    Required rather than defaulted, like everything else here. A missing threshold would
    not read as "judge nothing": it would read as a line drawn at zero, and every module
    in the graph would carry a verdict nobody wrote — which is worse than no verdict at
    all, because a mechanical finding is trusted exactly as far as the rule behind it can
    be pointed at.
    """
    _an_object(entry, "deletionTest")
    _only(entry, ("passThrough", "earnsItsKeep", "noFinding"), "deletionTest")

    pass_through = entry.get("passThrough")
    _an_object(pass_through, "deletionTest.passThrough")
    _only(
        pass_through,
        ("verdict", "because", "reachAtMost", "callersAtLeast"),
        "deletionTest.passThrough",
    )
    at_most = pass_through.get("reachAtMost")
    _an_object(at_most, "deletionTest.passThrough.reachAtMost")
    _only(at_most, ("perMethod", "neverBelow"), "deletionTest.passThrough.reachAtMost")
    per_method = _a_count(
        at_most.get("perMethod"), "deletionTest.passThrough.reachAtMost.perMethod"
    )
    never_below = _a_count(
        at_most.get("neverBelow"), "deletionTest.passThrough.reachAtMost.neverBelow"
    )
    callers_at_least = _a_count(
        pass_through.get("callersAtLeast"), "deletionTest.passThrough.callersAtLeast"
    )
    # A pass-through is a claim about the callers a deletion would move complexity to, so
    # there has to be more than one of them for the claim to say anything. At zero, every
    # module that concentrates nothing — including every one nobody calls at all — would
    # be reported as a pass-through, over callers that do not exist.
    if callers_at_least < 2:
        raise ConfigurationRefused(
            "deletionTest.passThrough.callersAtLeast is %d, and complexity that moves to "
            "one caller, or to none, has not moved anywhere a reader can see"
            % callers_at_least
        )

    verdicts = {}
    for name in ("passThrough", "earnsItsKeep", "noFinding"):
        where = "deletionTest.%s" % name
        answer = entry.get(name)
        _an_object(answer, where)
        if name != "passThrough":
            _only(answer, ("verdict", "because"), where)
        if not isinstance(answer.get("verdict"), str) or not answer["verdict"].strip():
            raise ConfigurationRefused(
                "%s.verdict is %r, and a verdict with nothing written on it is one no "
                "reader could act on" % (where, answer.get("verdict"))
            )
        verdicts[name] = Verdict(answer["verdict"], _a_sentence(answer, where))

    written = [verdict.verdict for verdict in verdicts.values()]
    for name, verdict in sorted(verdicts.items()):
        if written.count(verdict.verdict) > 1:
            raise ConfigurationRefused(
                "deletionTest.%s.verdict is %r, which another verdict is already called: "
                "a module given one of them could not be told from the other"
                % (name, verdict.verdict)
            )
    return DeletionTest(
        pass_through=verdicts["passThrough"],
        earns_its_keep=verdicts["earnsItsKeep"],
        no_finding=verdicts["noFinding"],
        per_method=per_method,
        never_below=never_below,
        callers_at_least=callers_at_least,
    )


def _a_count(value, where):
    """A whole number of things, refused when it is anything a count cannot be.

    `bool` is excluded by hand because Python calls `True` an `int`, and a threshold of
    `true` would silently be a threshold of one.
    """
    if not isinstance(value, int) or isinstance(value, bool):
        raise ConfigurationRefused(
            "%s is %r, and it has to be a whole number of things" % (where, value)
        )
    if value < 0:
        raise ConfigurationRefused(
            "%s is %d, and there is no such thing as less than none of them" % (where, value)
        )
    return value


def _reach(reach):
    """What the file says an adapter, a persistent record and a transaction are.

    All three are required rather than defaulted. A missing rule would not read as "count
    none of those": it would read as an adapter being nothing, a record being nothing and
    a transaction being nothing, and every module in the graph would come back coordinating
    less than it does — a low score for three rules nobody wrote, which is the one thing
    this file refuses to hand anybody.
    """
    _an_object(reach, "reach")
    _only(reach, ("adapter", "persistentRecord", "transaction"), "reach")
    transaction = reach.get("transaction")
    _an_object(transaction, "reach.transaction")
    _only(transaction, ("because", "annotatedWith"), "reach.transaction")
    return WhatIsReached(
        adapter=_a_reason(reach.get("adapter"), "reach.adapter"),
        persistent_record=_a_reason(reach.get("persistentRecord"), "reach.persistentRecord"),
        transaction=_a_sentence(transaction, "reach.transaction"),
        transaction_annotations=frozenset(
            _simple_names(transaction.get("annotatedWith"), "reach.transaction.annotatedWith")
        ),
    )


def _a_reason(entry, where):
    """One rule about what a reached thing is: the sentence for it, and the facts it matches."""
    _an_object(entry, where)
    _only(entry, ("because", "when"), where)
    because = _a_sentence(entry, where)
    when = entry.get("when")
    _an_object(when, "%s.when" % where)
    if not when:
        raise ConfigurationRefused(
            "%s.when says nothing, so every module in the graph would be one" % where
        )
    _only(when, CONDITIONS, "%s.when" % where)
    for condition in sorted(when):
        CONDITIONS[condition].valid(when[condition], "%s.when.%s" % (where, condition))
    return Reason(because, dict(when))


def _a_sentence(entry, where):
    """The `because` an entry carries, refused when there is nothing anybody could argue with."""
    if not isinstance(entry.get("because"), str) or not entry["because"].strip():
        raise ConfigurationRefused(
            "%s.because is %r, and a rule nobody can read the reason for is one nobody "
            "can argue with" % (where, entry.get("because"))
        )
    return entry["because"]


def _exclusions(listed):
    if not isinstance(listed, list):
        raise ConfigurationRefused("exclusions is %s, and it has to be a list" % _shape(listed))
    exclusions = []
    named = set()
    for index, entry in enumerate(listed):
        where = "exclusions[%d]" % index
        _an_object(entry, where)
        _only(entry, ("rule", "because", "when"), where)
        for field in ("rule", "because"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                raise ConfigurationRefused(
                    "%s.%s is %r, and an exclusion nobody can name is one nobody can argue "
                    "with" % (where, field, entry.get(field))
                )
        if entry["rule"] in named:
            raise ConfigurationRefused(
                "%s.rule is %r, which another rule is already called: a module excluded by "
                "one of them could not be told from the other" % (where, entry["rule"])
            )
        named.add(entry["rule"])
        when = entry.get("when")
        _an_object(when, "%s.when" % where)
        if not when:
            raise ConfigurationRefused(
                "%s.when says nothing, so the rule would exclude every module" % where
            )
        _only(when, CONDITIONS, "%s.when" % where)
        for condition in sorted(when):
            CONDITIONS[condition].valid(when[condition], "%s.when.%s" % (where, condition))
        exclusions.append(Exclusion(entry["rule"], entry["because"], dict(when)))
    return exclusions


def _an_object(value, where):
    if not isinstance(value, dict):
        raise ConfigurationRefused("%s is %s, and it has to be an object" % (where, _shape(value)))


def _only(document, allowed, where):
    unknown = sorted(set(document) - set(allowed))
    if unknown:
        raise ConfigurationRefused(
            "%s names %s, which this tool does not read: it would look like a rule and do "
            "nothing" % (where, ", ".join(unknown))
        )
