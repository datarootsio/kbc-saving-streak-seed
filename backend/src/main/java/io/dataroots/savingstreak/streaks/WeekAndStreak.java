package io.dataroots.savingstreak.streaks;

/**
 * How one customer's saving is going: the week they are part-way through, the run of consecutive
 * secured weeks behind that week, and which version of the scheme judged the two of them.
 *
 * <p>The first two together rather than one at a time, because they are two readings of the same
 * derivation and are only true of the same moment. Asked separately they would each read the clock
 * for themselves, and a pair of reads either side of midnight on a Monday would have the screen say
 * "€ 0,00 of € 50,00 this week" beside a run that counted the week just gone as the current one —
 * two answers about two different weeks, neither of them wrong on its own.
 *
 * <p>It is also what stops the week being counted twice: the streak's derivation takes this week's
 * gross total from {@link NewSavingsThisWeek} rather than summing it again.
 *
 * <p><strong>And the version, which is a name rather than a figure.</strong> Every number the scheme
 * decided is already carried by the record it decided something about — the week carries the
 * threshold it was judged against and the run carries the ladder it is paid on — so this is
 * deliberately not a second copy of any of them. It is the row those figures came out of, so that a
 * customer asking "why is it 1,30×" can be answered with something they can go and read, and so that
 * a reviewer chasing a surprising rate has the version number on the answer rather than having to
 * infer it from a date. A record here that carried the whole published version would be the second
 * place the threshold and the ladder live, which is the precise shape of bug this whole feature
 * exists to remove.
 *
 * <p>This week's version, and only this week's. The weeks behind it were each judged under their own
 * Monday's version — that is what makes a run stand still when the scheme is repriced — and no
 * reading reports those, because a run of six weeks spanning two versions has no single version to
 * name and inventing one would be a worse answer than the walk's own log line, which names every one
 * of them.
 */
public record WeekAndStreak(NewSavingsThisWeek week, StreakOfSecuredWeeks streak,
                            int theVersionOfTheSchemeThisWeekWasJudgedUnder) {
}
