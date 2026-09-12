package be.kbc.savingstreak;

import static org.assertj.core.api.Assertions.assertThat;

import be.kbc.savingstreak.domain.Member;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MemberStreakTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 9, 7).with(DayOfWeek.MONDAY);

    @Test
    void firstSecuredWeekStartsTheStreak() {
        Member member = new Member("Lotte", "Vermeulen");
        member.secureWeek(WEEK);
        assertThat(member.getStreakWeeks()).isEqualTo(1);
    }

    @Test
    void securingTheSameWeekTwiceDoesNotCountTwice() {
        Member member = new Member("Lotte", "Vermeulen");
        member.secureWeek(WEEK);
        member.secureWeek(WEEK);
        assertThat(member.getStreakWeeks()).isEqualTo(1);
    }

    @Test
    void consecutiveWeeksExtendTheStreak() {
        Member member = new Member("Lotte", "Vermeulen");
        member.secureWeek(WEEK.minusWeeks(2));
        member.secureWeek(WEEK.minusWeeks(1));
        member.secureWeek(WEEK);
        assertThat(member.getStreakWeeks()).isEqualTo(3);
        assertThat(member.getBestStreakWeeks()).isEqualTo(3);
    }

    @Test
    void aMissedWeekResetsTheStreakButKeepsTheRecord() {
        Member member = new Member("Lotte", "Vermeulen");
        member.secureWeek(WEEK.minusWeeks(4));
        member.secureWeek(WEEK.minusWeeks(3));
        member.secureWeek(WEEK);
        assertThat(member.getStreakWeeks()).isEqualTo(1);
        assertThat(member.getBestStreakWeeks()).isEqualTo(2);
    }

    @Test
    void aStreakCountsWhileItsLastWeekIsThisWeekOrLastWeek() {
        Member member = new Member("Lotte", "Vermeulen");
        member.secureWeek(WEEK.minusWeeks(1));
        member.secureWeek(WEEK);

        assertThat(member.effectiveStreakWeeks(WEEK)).isEqualTo(2);
        assertThat(member.effectiveStreakWeeks(WEEK.plusWeeks(1))).isEqualTo(2);
    }

    @Test
    void aStreakStopsCountingOnceAWholeWeekIsMissed() {
        Member member = new Member("Lotte", "Vermeulen");
        member.secureWeek(WEEK.minusWeeks(1));
        member.secureWeek(WEEK);

        assertThat(member.effectiveStreakWeeks(WEEK.plusWeeks(2))).isZero();
        assertThat(member.getBestStreakWeeks()).isEqualTo(2);
    }

    @Test
    void aMemberWhoNeverSecuredAWeekHasNoStreak() {
        assertThat(new Member("Lotte", "Vermeulen").effectiveStreakWeeks(WEEK)).isZero();
    }

    @Test
    void theSavingsPeakOnlyEverRises() {
        Member member = new Member("Lotte", "Vermeulen");
        member.raiseSavingsPeak(100_000);
        member.raiseSavingsPeak(120_000);
        member.raiseSavingsPeak(90_000);

        assertThat(member.savingsPeakAtLeast(0)).isEqualTo(120_000);
    }

    @Test
    void anUnsetPeakNeverRewardsMoreThanTheCurrentTotal() {
        assertThat(new Member("Lotte", "Vermeulen").savingsPeakAtLeast(80_000)).isEqualTo(80_000);
    }
}
