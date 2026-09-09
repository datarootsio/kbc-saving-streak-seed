package io.dataroots.savingstreak.gifting;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link GiftingService}. */
interface GiftRepository extends JpaRepository<Gift, Long> {
}
