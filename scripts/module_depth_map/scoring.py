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

Three kinds of thing are drawn but never scored, each by a rule the file names: values
that only carry data across a seam, repository interfaces whose implementation is
generated rather than written, and the application's entry point. They are shallow by
construction, and scoring them would bury the modules that are not. Nothing is ever
excluded without a rule saying so: the graph records the rule and the fact that matched
it, so "why was this ignored?" always has an answer a reader can point at.
"""

import json
import logging
import os
import re

from . import javasource

log = logging.getLogger("module_depth_map.scoring")

SCHEMA = "module-depth-map-scoring/1"

DEFAULT_CONFIGURATION = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scoring.json")

WEIGHTS = ("method", "parameter", "typeToLearn", "typeEveryCallerAlreadyKnows")
VISIBILITIES = ("public", "protected", "package-private", "private")


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
        """The evidence for excluding this module, or None if this rule says nothing about it.

        Every condition in the rule has to hold, and the evidence names each of them by
        the fact that satisfied it, so the graph can say "excluded by *this* rule for
        *this* reason" rather than only that something matched. The conditions are read
        in one fixed order rather than the file's, so that two rules written with their
        conditions in different orders still read the same way on the page.
        """
        evidence = []
        for condition in [name for name in CONDITIONS if name in self.when]:
            met, because = CONDITIONS[condition].met(self.when[condition], declared)
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


class Rules:
    """The scoring rules one configuration file holds, ready to be applied to a module."""

    def __init__(self, path, weights, reachable_from_outside, already_known, exclusions):
        self.path = path
        self.weights = weights
        self.reachable_from_outside = reachable_from_outside
        self.already_known = already_known
        self.exclusions = exclusions

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

        The methods and the types are recorded whether or not the module is scored — an
        excluded module is drawn with its interface visible, so a reader can see what the
        rule decided not to measure — but the cost of an unscored one is absent rather
        than zero, because it was never counted, not counted to nothing. Absent all the
        way down: a per-method cost published under a module whose own cost is `null` is
        a score for a module the same document says has none, and an agent reading the
        graph could add those parts up into a number nobody ever decided to give it.
        """
        methods = sorted(
            (method for method in declared.methods if method.visibility in self.reachable_from_outside),
            key=lambda method: (method.name, method.parameters),
        )
        crossing = self._types_crossing_the_seam(methods, declared.type_parameters)
        cost = None
        if scored:
            cost = sum(self._cost_of(method) for method in methods) + sum(
                self.weights["typeToLearn" if type_["mustBeLearned"] else "typeEveryCallerAlreadyKnows"]
                for type_ in crossing
            )
        # Logged for a module that is never scored too, and that is the case worth having
        # it for: the page shows such a module the name of the rule and nothing else, so
        # this is the only place a reader can check that the rule declined something real
        # rather than something the parser lost. Three of the misread interfaces found on
        # this branch were on records and enums, which the committed rules never price.
        log.debug(
            "interface read name=%s methods=%d parameters=%d typesToLearn=%d "
            "typesEveryCallerAlreadyKnows=%d cost=%s",
            declared.name,
            len(methods),
            sum(len(method.parameters) for method in methods),
            sum(1 for type_ in crossing if type_["mustBeLearned"]),
            sum(1 for type_ in crossing if not type_["mustBeLearned"]),
            cost if scored else "none, never scored",
        )
        return {
            "methods": [
                {
                    "name": method.name,
                    "visibility": method.visibility,
                    "parameters": list(method.parameters),
                    "returns": method.returns,
                    "cost": self._cost_of(method) if scored else None,
                }
                for method in methods
            ],
            "typesCrossingTheSeam": crossing,
            "cost": cost,
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
    _only(document, ("schema", "interfaceCost", "exclusions"), "the configuration")

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

    exclusions = _exclusions(document.get("exclusions"))
    rules = Rules(path, dict(weights), frozenset(reachable), frozenset(known), exclusions)
    log.debug(
        "scoring rules read weights=%s reachableFromOutside=%s typesAlreadyKnown=%d rules=%s",
        ",".join("%s=%d" % (name, weights[name]) for name in WEIGHTS),
        ",".join(sorted(reachable)),
        len(known),
        ",".join(exclusion.rule for exclusion in exclusions),
    )
    return rules


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
