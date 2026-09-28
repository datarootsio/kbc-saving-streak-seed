package io.dataroots.savingstreak.challenges;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link ChallengesService}. */
interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /**
     * Every season the bank has ever run, in the order it was written.
     *
     * <p>All of them rather than only the open ones, because a season that is over is still a thing
     * a customer's card points at — an expired enrolment has to be able to say which campaign ran
     * out from under it — and because the listing showing what is finished beside what is running is
     * how somebody tells "I missed it" from "there has never been one".
     *
     * <p>By identifier rather than by the day it opens, for the reason the definitions are: the
     * order rows were written in is a decision somebody made, and sorting by a date would reorder
     * the listing every time the bank added a season that had already started.
     */
    List<Campaign> findAllByOrderByIdAsc();

    /** One season by the code a definition names it with, or nothing if the bank runs no such thing. */
    Optional<Campaign> findByCode(String code);

    /** Whether the seed has already written this one, which is what makes seeding idempotent. */
    boolean existsByCode(String code);
}
