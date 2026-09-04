"""What an interface costs a caller, and what is never scored at all — read from a file.

The rules live in `scoring.json` beside this file rather than in it, because the whole
claim of the page is that a score can be argued with. A reader who thinks a module has
been treated unfairly changes a weight or a rule there and runs the tool again; nothing
in the analyser has to be edited for the output to change.

Interface cost is everything a caller has to learn before they can use a module
correctly: each method they can reach, each parameter of each of those methods, and each
distinct type that crosses the seam in a parameter or a return. Types are counted once
per module however many methods hand them over — learning `RecordedDeposit` twice is
still learning it once — and a type every Java caller already knows is weighted apart
from one this application invented, which is why a method handing back a domain type
costs more than one handing back a primitive.

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

log = logging.getLogger("module_depth_map.scoring")

SCHEMA = "module-depth-map-scoring/1"

DEFAULT_CONFIGURATION = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scoring.json")

WEIGHTS = ("method", "parameter", "typeToLearn", "typeEveryCallerAlreadyKnows")
VISIBILITIES = ("public", "protected", "package-private", "private")
CONDITIONS = ("kind", "annotatedWith", "extendsOrImplements", "nameEndsWith")


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
            met, because = _condition_met(condition, self.when[condition], declared)
            if not met:
                return None
            evidence.append(because)
        return ", ".join(evidence)


def _condition_met(condition, wanted, declared):
    if condition == "kind":
        return declared.kind in wanted, "kind is %s" % declared.kind
    if condition == "annotatedWith":
        hit = [name for name in wanted if name in declared.annotations]
        return bool(hit), "annotated with %s" % (hit[0] if hit else "")
    if condition == "extendsOrImplements":
        hit = [name for name in wanted if name in declared.supertypes]
        return bool(hit), "extends or implements %s" % (hit[0] if hit else "")
    hit = [suffix for suffix in wanted if declared.name.endswith(suffix)]
    return bool(hit), "name ends with %s" % (hit[0] if hit else "")


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
        than zero, because it was never counted, not counted to nothing.
        """
        methods = sorted(
            (method for method in declared.methods if method.visibility in self.reachable_from_outside),
            key=lambda method: (method.name, method.parameters),
        )
        crossing = self._types_crossing_the_seam(methods)
        cost = None
        if scored:
            cost = sum(self._cost_of(method) for method in methods) + sum(
                self.weights["typeToLearn" if type_["mustBeLearned"] else "typeEveryCallerAlreadyKnows"]
                for type_ in crossing
            )
            log.debug(
                "interface read name=%s methods=%d parameters=%d typesToLearn=%d cost=%d",
                declared.name,
                len(methods),
                sum(len(method.parameters) for method in methods),
                sum(1 for type_ in crossing if type_["mustBeLearned"]),
                cost,
            )
        return {
            "methods": [
                {
                    "name": method.name,
                    "visibility": method.visibility,
                    "parameters": list(method.parameters),
                    "returns": method.returns,
                    "cost": self._cost_of(method),
                }
                for method in methods
            ],
            "typesCrossingTheSeam": crossing,
            "cost": cost,
        }

    def _cost_of(self, method):
        return self.weights["method"] + len(method.parameters) * self.weights["parameter"]

    def _types_crossing_the_seam(self, methods):
        """Every distinct type a caller meets in a parameter or a return, counted once.

        A type is named by the simple name it is written with and stripped of the shapes
        it is carried in: `List<Optional<Customer>>` is `List`, `Optional` and `Customer`,
        because a caller who has to unwrap the first two still has to learn the third.
        """
        names = set()
        for method in methods:
            for written in list(method.parameters) + [method.returns]:
                names.update(_named_types_in(written))
        return [
            {"name": name, "mustBeLearned": name not in self.already_known}
            for name in sorted(names)
        ]


def _named_types_in(written):
    """The type names inside a type as it was written, generics and arrays taken apart.

    `List<Optional<Customer>>` is `List`, `Optional` and `Customer`, and
    `java.util.List` is `List`: a package prefix is not a second thing to learn, and a
    wildcard is not a type at all.
    """
    found = []
    for word in re.split(r"[<>,\[\]\s]+", written):
        simple = word.rstrip(".").split(".")[-1]
        if not simple or simple in ("?", "extends", "super", "final"):
            continue
        found.append(simple)
    return found


def load(path=None):
    """The rules in this file, or a refusal naming what is wrong with it.

    Every check here exists because the alternative is a silent one: a misspelled weight
    that quietly scores nothing, a rule with no name that excludes a module the graph
    then cannot explain, a condition this tool does not understand that reads as "always
    true" and takes half the application off the scored list.
    """
    path = path or DEFAULT_CONFIGURATION
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
    _only(document, ("schema", "interfaceCost", "exclusions"), "the configuration")
    if document.get("schema") != SCHEMA:
        raise ConfigurationRefused(
            "schema is %r, and this tool reads %r" % (document.get("schema"), SCHEMA)
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

    reachable = _strings(cost.get("reachableFromOutside"), "interfaceCost.reachableFromOutside")
    unknown = [visibility for visibility in reachable if visibility not in VISIBILITIES]
    if unknown:
        raise ConfigurationRefused(
            "interfaceCost.reachableFromOutside names %s, and a method is %s"
            % (", ".join(sorted(unknown)), " or ".join(VISIBILITIES))
        )
    known = _strings(
        cost.get("typesEveryCallerAlreadyKnows"), "interfaceCost.typesEveryCallerAlreadyKnows"
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
            _strings(when[condition], "%s.when.%s" % (where, condition))
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


def _strings(value, where):
    if not isinstance(value, list) or not value:
        raise ConfigurationRefused(
            "%s is %s, and it has to be a list with something in it" % (where, _shape(value))
        )
    for entry in value:
        if not isinstance(entry, str) or not entry.strip():
            raise ConfigurationRefused("%s holds %r, and every entry has to be a name" % (where, entry))
    return list(value)


def _shape(value):
    return "missing" if value is None else "%r" % (value,)
