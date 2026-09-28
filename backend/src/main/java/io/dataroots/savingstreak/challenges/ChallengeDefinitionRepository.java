package io.dataroots.savingstreak.challenges;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link ChallengesService}. */
interface ChallengeDefinitionRepository extends JpaRepository<ChallengeDefinition, Long> {

    /**
     * Every challenge the bank offers, in the order it was seeded.
     *
     * <p>By identifier rather than by name or by what a rung pays, because the seed writes them in
     * the order it means them to be read — easiest first, the way the rewards catalogue is served
     * cheapest first — and that order is a decision somebody made rather than an accident of the
     * alphabet.
     */
    List<ChallengeDefinition> findAllByOrderByIdAsc();

    /** One challenge by the code an enrolment names it with, or nothing if the bank offers no such thing. */
    Optional<ChallengeDefinition> findByCode(String code);

    /** Whether the seed has already written this one, which is what makes seeding idempotent. */
    boolean existsByCode(String code);
}
