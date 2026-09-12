package be.kbc.savingstreak;

import static org.assertj.core.api.Assertions.assertThat;

import be.kbc.savingstreak.service.PointsRules;
import org.junit.jupiter.api.Test;

class PointsRulesTest {

    private final PointsRules rules = new PointsRules();

    @Test
    void onePointPerEuroWithoutStreakBonus() {
        assertThat(rules.pointsFor(5_000, 1)).isEqualTo(50);
    }

    @Test
    void everyExtraWeekAddsTenPercent() {
        assertThat(rules.multiplierBasisPoints(1)).isEqualTo(10_000);
        assertThat(rules.multiplierBasisPoints(4)).isEqualTo(13_000);
        assertThat(rules.pointsFor(5_000, 4)).isEqualTo(65);
    }

    @Test
    void multiplierStopsAtFiftyPercent() {
        assertThat(rules.multiplierBasisPoints(6)).isEqualTo(15_000);
        assertThat(rules.multiplierBasisPoints(40)).isEqualTo(15_000);
        assertThat(rules.pointsFor(10_000, 40)).isEqualTo(150);
    }

    @Test
    void onlyWholeEurosEarnAPoint() {
        assertThat(rules.pointsFor(1_099, 1)).isEqualTo(10);
        assertThat(rules.pointsFor(1_100, 1)).isEqualTo(11);
    }
}
