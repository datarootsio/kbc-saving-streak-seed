package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The next six weeks of a current account's cash flow as the API sends it, and what that says its
 * holder could save each week.
 *
 * <p>Shared by every test that asks, for the reason {@link MonthAheadView} is shared: copies of a
 * shape drift into disagreeing about it, and these two shapes are asserted against each other.
 *
 * <p>{@code couldSaveWeekly} is boxed for the reason a declared capacity's figure is: a test here
 * has to be able to say what the offer <em>is</em> rather than merely that something came back, and
 * a field the API stopped sending would read as 0.00 through a primitive and pass the test written
 * to catch exactly that.
 */
public record WeeksAheadView(long currentAccountId, LocalDate from, LocalDate until,
                             BigDecimal arriving, BigDecimal committed, BigDecimal claimedByBudgets,
                             BigDecimal leftOver, BigDecimal couldSaveWeekly, Boolean worthOffering,
                             List<WeekAheadView> weeks) {
}
