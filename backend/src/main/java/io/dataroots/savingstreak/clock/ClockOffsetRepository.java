package io.dataroots.savingstreak.clock;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: where the clock is standing is {@link ClockService}'s to say. */
interface ClockOffsetRepository extends JpaRepository<ClockOffset, Long> {
}
