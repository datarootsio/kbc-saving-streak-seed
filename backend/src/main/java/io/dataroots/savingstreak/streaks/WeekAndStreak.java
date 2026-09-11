package io.dataroots.savingstreak.streaks;

/**
 * How one customer's saving is going: the week they are part-way through, and the run of consecutive
 * secured weeks behind that week.
 *
 * <p>The two together rather than one at a time, because they are two readings of the same
 * derivation and are only true of the same moment. Asked separately they would each read the clock
 * for themselves, and a pair of reads either side of midnight on a Monday would have the screen say
 * "€ 0,00 of € 50,00 this week" beside a run that counted the week just gone as the current one —
 * two answers about two different weeks, neither of them wrong on its own.
 *
 * <p>It is also what stops the week being counted twice: the streak's derivation takes this week's
 * gross total from {@link NewSavingsThisWeek} rather than summing it again.
 */
public record WeekAndStreak(NewSavingsThisWeek week, StreakOfSecuredWeeks streak) {
}
