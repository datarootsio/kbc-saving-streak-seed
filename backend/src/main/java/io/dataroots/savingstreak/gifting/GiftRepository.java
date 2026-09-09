package io.dataroots.savingstreak.gifting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link GiftingService}. */
interface GiftRepository extends JpaRepository<Gift, Long> {

    /**
     * Every gift this customer was part of, whichever end of it they were on, newest first.
     *
     * <p>One query over both columns rather than two reads stitched together, because the answer is
     * one list read chronologically: which end somebody was on is what the direction on each row
     * says, and it is not a reason to fetch them separately.
     *
     * <p>Ordered by the moment and then by the identifier, the idiom every other newest-first read in
     * this application uses. Two gifts made in the same millisecond are otherwise in whatever order
     * the database felt like, and the later identifier is the later gift.
     */
    List<Gift> findBySenderCustomerIdOrRecipientCustomerIdOrderByGivenAtDescIdDesc(
            long senderCustomerId, long recipientCustomerId);
}
