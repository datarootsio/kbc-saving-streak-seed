/* ============================================================
   KBC Saving Streak — single page frontend.
   Talks to /api, keeps the previous payload so every number can
   animate from its old value to the new one.
   ============================================================ */
(function () {
  'use strict';

  var state = {
    overview: null,
    previous: null,
    rewardFilter: 'ALL',
    view: 'overview',
    sheetAccountId: null,
    busy: false
  };

  var ICONS = {
    coffee: 'i-coffee', snack: 'i-snack', heart: 'i-heart',
    ticket: 'i-ticket', museum: 'i-museum', family: 'i-family'
  };

  var CATEGORY_CLASS = {
    FOOD_DRINK: '', ENTERTAINMENT: 'reward--entertainment',
    FAMILY: 'reward--family', DONATION: 'reward--donation'
  };

  var $ = function (selector, root) { return (root || document).querySelector(selector); };
  var $$ = function (selector, root) { return Array.prototype.slice.call((root || document).querySelectorAll(selector)); };

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (character) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character];
    });
  }

  function icon(id, className) {
    return '<svg class="' + (className || '') + '" aria-hidden="true"><use href="#' + id + '"></use></svg>';
  }

  /* ── API ─────────────────────────────────────────────── */

  function api(path, options) {
    return fetch('/api' + path, Object.assign({
      headers: { 'Content-Type': 'application/json' }
    }, options)).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (!response.ok) {
          throw new Error(body.message || 'Something went wrong. Please try again.');
        }
        return body;
      });
    });
  }

  /* ── Views / tabs ────────────────────────────────────── */

  function moveGlider() {
    var active = $('.tab.is-active');
    var glider = $('#tabsGlider');
    if (!active || !glider) return;
    glider.style.width = active.offsetWidth + 'px';
    glider.style.transform = 'translateX(' + active.offsetLeft + 'px)';
  }

  function showView(name) {
    state.view = name;
    $$('.tab').forEach(function (tab) { tab.classList.toggle('is-active', tab.dataset.view === name); });
    $$('.view').forEach(function (view) { view.classList.toggle('is-active', view.id === 'view-' + name); });
    moveGlider();
    // On a phone the tab strip scrolls, so keep the active tab in view.
    var active = $('.tab.is-active');
    if (active && active.scrollIntoView) {
      active.scrollIntoView({ block: 'nearest', inline: 'center' });
    }
    if (history.replaceState) history.replaceState(null, '', '#' + name);
  }

  /* ── Rendering ───────────────────────────────────────── */

  function heroLine(streakWeeks) {
    if (streakWeeks === 0) return 'save €50 this week to start a streak.';
    if (streakWeeks === 1) return "you've saved a week in a row.";
    return "you've saved " + FX.formatNumber(streakWeeks) + ' weeks in a row.';
  }

  function render(options) {
    var opts = options || {};
    var data = state.overview;
    var previous = state.previous;
    var member = data.member;

    $('#avatar').textContent = member.initials;
    $('#heroName').textContent = member.firstName;
    $('#streakWeeksLabel').textContent = member.streakWeeks === 1 ? 'week in a row' : 'weeks in a row';
    $('#heroStreakLine').textContent = heroLine(member.streakWeeks);

    var animate = opts.animate !== false;
    var prevMember = previous ? previous.member : null;

    FX.countTo($('#pointsPill strong'), member.pointsBalance, {
      from: prevMember && animate ? prevMember.pointsBalance : member.pointsBalance
    });
    FX.countTo($('#pointsBalance'), member.pointsBalance, {
      from: prevMember && animate ? prevMember.pointsBalance : member.pointsBalance,
      duration: 1100
    });
    FX.countTo($('#streakWeeks'), member.streakWeeks, {
      from: prevMember && animate ? prevMember.streakWeeks : member.streakWeeks, duration: 600
    });
    FX.countMoney($('#totalBalance'), Number(data.totalBalance), {
      from: previous && animate ? Number(previous.totalBalance) : Number(data.totalBalance), duration: 1000
    });
    FX.countMoney($('#totalSaved'), Number(data.totalSaved), {
      from: previous && animate ? Number(previous.totalSaved) : Number(data.totalSaved), duration: 1000
    });

    var current = data.accounts.filter(function (account) { return account.type === 'CURRENT'; })[0];
    var prevCurrent = previous && previous.accounts.filter(function (account) { return account.type === 'CURRENT'; })[0];
    if (current) {
      FX.countMoney($('#currentBalance'), Number(current.balance), {
        from: prevCurrent && animate ? Number(prevCurrent.balance) : Number(current.balance), duration: 1000
      });
    }

    $('#multiplier').textContent = FX.formatDecimal(Number(member.multiplier)) + '×';
    $('#nextMultiplier').textContent = FX.formatDecimal(Number(member.nextMultiplier)) + '×';
    $('#bestStreak').textContent = member.bestStreakWeeks + (member.bestStreakWeeks === 1 ? ' week' : ' weeks');
    $('#pointsTotal').textContent = FX.formatNumber(member.pointsEarnedTotal) + ' points';
    renderExpiry(member);
    $('#rewardsPoints').textContent = FX.formatNumber(member.pointsBalance);

    $('#weeklyGoal').textContent = FX.formatEuro(Number(member.weeklyGoal));
    $('#savingsPeak').textContent = FX.formatEuro(Number(member.savingsPeak));
    var goalState = $('#weeklyGoalState');
    var missing = Number(member.weeklyGoal) - Number(member.newSavingsThisWeek);
    if (member.streakSafeThisWeek) {
      goalState.textContent = 'This week is secured';
      goalState.className = 'weekbar__state is-safe';
    } else if (Number(member.newSavingsThisWeek) > 0) {
      goalState.textContent = FX.formatEuro(missing) + ' to go this week';
      goalState.className = 'weekbar__state is-open';
    } else {
      goalState.textContent = 'Nothing saved this week yet';
      goalState.className = 'weekbar__state is-open';
    }
    requestAnimationFrame(function () {
      $('#weeklyGoalFill').style.width = Math.max(2, member.weeklyGoalPercent) + '%';
    });

    renderNotifications();
    renderGifts();
    if (state.sheetAccountId && !$('#accountSheet').hidden) {
      openAccountSheet(state.sheetAccountId);
    }
    renderStreakDots(member);
    renderAccounts(data.accounts, previous);
    renderRewards();
    renderVouchers(data.redemptions);
    renderTimeline(data.transfers);
    fillAccountSelects(data.accounts);
    updatePointsPreview();
  }

  /** Shows which batch of points lapses next, and flags it when that is close. */
  function renderExpiry(member) {
    var line = $('#pointsExpiry');
    if (!member.pointsExpiringNext || !member.pointsExpiringOn) {
      line.hidden = true;
    } else {
      var days = daysUntil(member.pointsExpiringOn);
      var soon = days <= 60;
      line.hidden = false;
      line.className = 'points-expiry' + (soon ? ' is-soon' : '');
      line.textContent = FX.formatNumber(member.pointsExpiringNext) + ' points expire ' +
        (days <= 0 ? 'today' : days === 1 ? 'tomorrow' : soon ? 'in ' + days + ' days' : 'on ' + formatDay(member.pointsExpiringOn));
    }

    var loyaltyRow = $('#loyaltyRow');
    loyaltyRow.hidden = !member.loyaltyPointsEarned;
    if (member.loyaltyPointsEarned) {
      $('#loyaltyEarned').textContent = FX.formatNumber(member.loyaltyPointsEarned) + ' points';
    }

    fact('#giftedToYouRow', '#giftedToYou', member.giftedToYouPoints);
    fact('#giftedAwayRow', '#giftedAway', member.giftedAwayPoints);

    var lapsedRow = $('#lapsedRow');
    lapsedRow.hidden = !member.pointsLapsed;
    if (member.pointsLapsed) {
      $('#pointsLapsed').textContent = FX.formatNumber(member.pointsLapsed) + ' points';
    }
  }

  /** Shows an optional points fact row only when it has something to say. */
  function fact(rowSelector, valueSelector, points) {
    $(rowSelector).hidden = !points;
    if (points) {
      $(valueSelector).textContent = FX.formatNumber(points) + ' points';
    }
  }

  function daysUntil(isoDate) {
    var target = new Date(isoDate + 'T00:00:00');
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.round((target - today) / 86400000);
  }

  function formatDay(isoDate) {
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
      .format(new Date(isoDate + 'T00:00:00'));
  }

  function renderStreakDots(member) {
    var total = 6;
    var on = Math.min(total, member.streakWeeks);
    $('#streakDots').innerHTML = Array.apply(null, Array(total)).map(function (ignored, index) {
      return '<span class="streak__dot' + (index < on ? ' is-on' : '') +
        '" style="animation-delay:' + (index * 70) + 'ms"></span>';
    }).join('');
  }

  function renderAccounts(accounts, previous) {
    $('#accountList').innerHTML = accounts.map(function (account, index) {
      var isCurrent = account.type === 'CURRENT';
      var goal = account.goal ? '<div class="acc__goal"><span>Goal ' + esc(FX.formatEuro(Number(account.goal))) +
        '</span><span>' + account.goalProgressPercent + '%</span></div>' +
        '<div class="progress"><span class="progress__fill" style="width:' + account.goalProgressPercent + '%"></span></div>' : '';
      var badge = isCurrent
        ? '<span class="acc__badge">Current account</span>'
        : '<span class="acc__badge">Savings' + (account.interestRate ? ' · ' + FX.formatDecimal(Number(account.interestRate)) + '%' : '') + '</span>';

      var timeline = timelineMarkup(account);
      return '<div class="acc ' + (isCurrent ? 'is-current' : '') + '" data-account="' + account.id +
        '" style="--delay:' + (index * 60) + 'ms" role="button" tabindex="0">' +
        '<span class="acc__delta" data-delta></span>' +
        '<div class="acc__top">' +
          '<span class="acc__icon">' + icon(isCurrent ? 'i-card' : 'i-piggy') + '</span>' +
          '<div><div class="acc__name">' + esc(account.name) + '</div>' +
          '<div class="acc__sub">' + esc(account.subtitle || '') + '</div></div>' +
        '</div>' +
        '<div class="acc__amount" data-balance>' + esc(FX.formatEuro(Number(account.balance))) + '</div>' +
        '<div class="acc__iban">' + esc(account.iban) + '</div>' +
        badge + goal + timeline +
        '</div>';
    }).join('');

    // Animate each balance from its previous value.
    accounts.forEach(function (account) {
      var card = $('.acc[data-account="' + account.id + '"]');
      if (!card) return;
      var before = previous && previous.accounts.filter(function (other) { return other.id === account.id; })[0];
      if (before && Number(before.balance) !== Number(account.balance)) {
        FX.countMoney($('[data-balance]', card), Number(account.balance), { from: Number(before.balance), duration: 1000 });
      }
      var open = function () {
        if (account.type === 'SAVINGS') openAccountSheet(account.id); else startTransferFrom(account);
      };
      card.addEventListener('click', open);
      card.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(); }
      });
    });
  }

  var TIMELINE_DOTS = {
    BONUS_PAID: 'tline__dot--bonus',
    BONUS_DUE: 'tline__dot--bonus-due',
    POINTS_EXPIRING: 'tline__dot--expiring',
    POINTS_LAPSED: 'tline__dot--lapsed'
  };

  /**
   * A year behind and a year ahead of a savings account: money in and out on the top lane,
   * points events on the rail below. Both halves are needed because every expiry and every
   * loyalty anniversary falls exactly twelve months after the deposit that caused it.
   */
  function timelineMarkup(account, options) {
    var timeline = account.timeline;
    if (!timeline) return '';
    var large = options && options.large;

    var largest = Number(timeline.largestMovement) || 1;
    // Inset by half a marker so events on day one and day 730 stay fully visible.
    var at = function (position) {
      return 'left:calc(6px + (100% - 12px) * ' + (position / 100).toFixed(4) + ');';
    };

    /**
     * Spreads markers that would otherwise sit on top of each other. A deposit's expiry and
     * its loyalty anniversary fall on the very same day, and a year's worth of them can
     * cluster into a few weeks, so crowded runs are pushed apart symmetrically rather than
     * piled against the edge. The exact date stays in the tooltip.
     */
    var spread = function (positions, minimumGap) {
      var placed = positions.slice();
      for (var pass = 0; pass < 24; pass++) {
        var moved = false;
        for (var i = 1; i < placed.length; i++) {
          var gap = placed[i] - placed[i - 1];
          if (gap < minimumGap) {
            var push = (minimumGap - gap) / 2;
            placed[i - 1] = Math.max(0, placed[i - 1] - push);
            placed[i] = Math.min(100, placed[i] + push);
            moved = true;
          }
        }
        if (!moved) break;
      }
      return placed;
    };

    var isMoney = function (event) { return event.kind === 'MONEY_IN' || event.kind === 'MONEY_OUT'; };
    var moneyEvents = timeline.events.filter(isMoney);
    var pointEvents = timeline.events.filter(function (event) { return !isMoney(event); });
    var moneyAt = spread(moneyEvents.map(function (event) { return event.position; }), 2.2);
    var pointAt = spread(pointEvents.map(function (event) { return event.position; }), 3.6);

    var bars = '';
    var dots = '';

    moneyEvents.forEach(function (event, index) {
      var share = Math.max(0.12, Math.min(1, Number(event.amount) / largest));
      bars += '<span class="tline__bar ' + (event.kind === 'MONEY_IN' ? 'tline__bar--in' : 'tline__bar--out') +
        '" style="' + at(moneyAt[index]) + 'height:' + (share * 45).toFixed(1) + '%;' +
        'animation-delay:' + Math.min(index * 40, 400) + 'ms;' +
        '" title="' + esc(event.label + ' · ' + formatDay(event.on)) + '"></span>';
    });

    pointEvents.forEach(function (event, index) {
      dots += '<span class="tline__dot ' + TIMELINE_DOTS[event.kind] +
        '" style="' + at(pointAt[index]) + 'animation-delay:' + Math.min(index * 40, 400) + 'ms;' +
        '" title="' + esc(event.label + ' · ' + formatDay(event.on)) + '"></span>';
    });

    var quarters = [0, 25, 50, 75, 100];
    var grid = large
      ? '<span class="tline__grid">' + quarters.map(function (position) {
          return '<span class="tline__tick" style="' + at(position) + '"></span>';
        }).join('') + '</span>'
      : '';
    var ticks = large ? monthTicks(timeline, at, quarters) : '';

    return '<div class="tline' + (large ? ' tline--large' : '') + '">' +
      '<div class="tline__lanes">' + grid +
        '<div class="tline__money">' + bars + '</div>' +
        '<div class="tline__points">' + dots + '</div>' +
        '<span class="tline__now" style="' + at(timeline.nowPosition) + '"></span>' +
      '</div>' +
      (large
        ? ticks
        : '<div class="tline__scale"><span>' + esc(formatMonth(timeline.from)) + '</span>' +
          (timeline.earlierMovements ? '<span>+' + timeline.earlierMovements + ' earlier</span>' : '') +
          '<span>' + esc(formatMonth(timeline.to)) + '</span></div>') +
      timelineChips(timeline) +
      '</div>';
  }

  /** Labels every six months across the window, with "now" in the middle. */
  function monthTicks(timeline, at, quarters) {
    var from = new Date(timeline.from + 'T00:00:00');
    return '<div class="tline__ticks">' + quarters.map(function (position) {
      if (position === 50) {
        return '<span class="is-now" style="' + at(position) + '">now</span>';
      }
      var month = new Date(from.getTime());
      month.setMonth(month.getMonth() + Math.round(position / 100 * 24));
      return '<span style="' + at(position) + '">' +
        esc(new Intl.DateTimeFormat('en-GB', { month: 'short', year: '2-digit' }).format(month)) + '</span>';
    }).join('') + '</div>';
  }

  function timelineChips(timeline) {
    var chips = '';
    if (timeline.expiringNextOn) {
      var expiryDays = daysUntil(timeline.expiringNextOn);
      chips += '<span class="tline__chip ' + (expiryDays <= 60 ? 'tline__chip--expiry' : 'tline__chip--quiet') +
        '">' + FX.formatNumber(timeline.expiringNextPoints) + ' pts expire ' + whenText(expiryDays, timeline.expiringNextOn) +
        '</span>';
    }
    if (timeline.nextBonusOn) {
      var bonusDays = daysUntil(timeline.nextBonusOn);
      chips += '<span class="tline__chip ' + (bonusDays <= 60 ? 'tline__chip--bonus' : 'tline__chip--quiet') +
        '">↻ +' + FX.formatNumber(timeline.nextBonusPoints) + ' bonus ' + whenText(bonusDays, timeline.nextBonusOn) +
        '</span>';
    }
    if (!chips) {
      chips = '<span class="tline__chip tline__chip--quiet">No points riding on this account</span>';
    }
    return '<div class="tline__chips">' + chips + '</div>';
  }

  function whenText(days, isoDate) {
    if (days <= 0) return 'today';
    if (days === 1) return 'tomorrow';
    if (days <= 60) return 'in ' + days + ' days';
    return formatMonth(isoDate);
  }

  function formatMonth(isoDate) {
    return new Intl.DateTimeFormat('en-GB', { month: 'short', year: 'numeric' })
      .format(new Date(isoDate + 'T00:00:00'));
  }

  function renderRewards() {
    var rewards = state.overview.rewards;
    var filtered = state.rewardFilter === 'ALL'
      ? rewards
      : rewards.filter(function (reward) { return reward.category === state.rewardFilter; });

    $('#rewardList').innerHTML = filtered.map(function (reward, index) { return rewardCard(reward, index); }).join('') ||
      '<p class="empty">No rewards in this category.</p>';

    var teaser = rewards.slice().sort(function (a, b) {
      return (b.affordable - a.affordable) || (a.pointsShort - b.pointsShort);
    }).slice(0, 3);
    $('#rewardTeaser').innerHTML = teaser.map(function (reward, index) { return rewardCard(reward, index); }).join('');

    $$('[data-redeem]').forEach(function (button) {
      button.addEventListener('click', function (event) {
        FX.ripple(event);
        redeem(Number(button.dataset.redeem), button);
      });
    });
  }

  function rewardCard(reward, index) {
    return '<div class="reward ' + (CATEGORY_CLASS[reward.category] || '') + (reward.affordable ? '' : ' is-locked') +
      '" data-reward="' + reward.id + '" style="--delay:' + (index * 55) + 'ms">' +
      '<span class="reward__icon">' + icon(ICONS[reward.icon] || 'i-gift') + '</span>' +
      '<div><div class="reward__partner">' + esc(reward.partner) + '</div>' +
      '<div class="reward__title">' + esc(reward.title) + '</div></div>' +
      '<p class="reward__desc">' + esc(reward.description) + '</p>' +
      '<div class="reward__foot">' +
        '<div><div class="reward__cost">' + FX.formatNumber(reward.pointsCost) + ' <small>points</small></div>' +
        '<div class="reward__value">worth ' + esc(FX.formatEuro(Number(reward.value))) + '</div></div>' +
        (reward.affordable
          ? '<button class="btn btn--ghost btn--small" type="button" data-redeem="' + reward.id + '">Redeem</button>'
          : '<span class="reward__lock">' + FX.formatNumber(reward.pointsShort) + ' points to go</span>') +
      '</div>' +
      (reward.affordable ? '' : '<div class="progress"><span class="progress__fill" style="width:' + reward.progressPercent + '%"></span></div>') +
      '</div>';
  }

  function renderVouchers(redemptions) {
    if (!redemptions.length) {
      $('#voucherList').innerHTML = '<p class="empty">No vouchers yet. Redeem some points to get your first one.</p>';
      return;
    }
    $('#voucherList').innerHTML = redemptions.map(function (redemption, index) {
      return '<div class="voucher" style="--delay:' + (index * 50) + 'ms">' +
        '<div class="reward__partner">' + esc(formatDate(redemption.createdAt)) + '</div>' +
        '<div class="reward__title">' + esc(redemption.rewardTitle) + '</div>' +
        '<div class="voucher__code">' + esc(redemption.voucherCode) + '</div>' +
        '<div class="voucher__meta">' + FX.formatNumber(redemption.pointsSpent) + ' points redeemed</div>' +
        '</div>';
    }).join('');
  }

  function renderTimeline(transfers) {
    if (!transfers.length) {
      $('#transferList').innerHTML = '<li class="empty">No transactions yet.</li>';
      return;
    }
    $('#transferList').innerHTML = transfers.map(function (transfer, index) {
      var isDeposit = transfer.direction === 'DEPOSIT';
      var isRebalance = transfer.direction === 'REBALANCE';
      var iconName = isRebalance ? 'i-swap' : (isDeposit ? 'i-arrow-up' : 'i-arrow-down');
      var iconClass = isRebalance ? 'tl__icon--move' : (isDeposit ? 'tl__icon--in' : 'tl__icon--out');
      return '<li style="--delay:' + Math.min(index * 45, 500) + 'ms">' +
        '<span class="tl__icon ' + iconClass + '">' + icon(iconName) + '</span>' +
        '<div class="tl__body"><div class="tl__title">' + esc(transfer.description) + '</div>' +
        '<div class="tl__meta">' + esc(transfer.fromAccountName) + ' → ' + esc(transfer.toAccountName) +
        ' · ' + esc(formatDate(transfer.createdAt)) + '</div>' +
        tagRow(transfer) + '</div>' +
        '<div class="tl__right"><div class="tl__amount">' + (isDeposit || isRebalance ? '' : '−') +
        esc(FX.formatEuro(Number(transfer.amount))) + '</div>' +
        '<div class="tl__points' + (transfer.pointsEarned ? '' : ' tl__points--none') + '">' +
        (transfer.pointsEarned ? '+' + FX.formatNumber(transfer.pointsEarned) + ' points' : 'no points') +
        '</div></div></li>';
    }).join('');
  }

  /** The small line under a history row's points saying what became of them. */
  function expiryTag(transfer) {
    if (!transfer.pointsEarned || !transfer.pointsExpireOn) return '';

    if (transfer.pointsHaveExpired) {
      return tag('is-expired', 'expired ' + formatDay(transfer.pointsExpireOn));
    }
    if (transfer.pointsLeft === 0) {
      return tag('is-spent', 'spent');
    }

    var days = daysUntil(transfer.pointsExpireOn);
    var left = transfer.pointsLeft < transfer.pointsEarned
        ? FX.formatNumber(transfer.pointsLeft) + ' left · '
        : '';
    if (days <= 60) {
      return tag('is-soon', left + (days <= 0 ? 'expires today'
          : days === 1 ? 'expires tomorrow' : 'expires in ' + days + ' days'));
    }
    return tag('', left + 'expires ' + formatDay(transfer.pointsExpireOn));
  }

  /** The chips under a row: what happened to its points, and its loyalty clock. */
  function tagRow(transfer) {
    var tags = expiryTag(transfer) + loyaltyTag(transfer);
    return tags ? '<div class="tl__tags">' + tags + '</div>' : '';
  }

  /** The deposit's loyalty clock: what it has paid, and what the next anniversary brings. */
  function loyaltyTag(transfer) {
    if (transfer.direction !== 'DEPOSIT' || !transfer.pointsEarned) return '';

    var paid = transfer.loyaltyPaid
        ? 'loyalty +' + FX.formatNumber(transfer.loyaltyPaid)
        : '';

    if (!transfer.loyaltyNextOn) {
      return small('tl__loyalty is-stopped', paid ? paid + ' · stopped' : 'loyalty stopped');
    }

    var days = daysUntil(transfer.loyaltyNextOn);
    var next = '+' + FX.formatNumber(transfer.loyaltyNextPoints) + ' · ' +
      (days <= 60 ? (days <= 0 ? 'due today' : days === 1 ? 'tomorrow' : 'in ' + days + ' days')
                  : formatDay(transfer.loyaltyNextOn));
    // A partly withdrawn deposit earns on what is left, so say how much that is.
    var partial = Number(transfer.loyaltyStillSaved) < Number(transfer.amount)
        ? FX.formatEuro(Number(transfer.loyaltyStillSaved)) + ' of this deposit is still saved'
        : '';
    return small('tl__loyalty' + (days <= 60 ? ' is-due-soon' : ''),
      (paid ? paid + ' · next ' : 'loyalty ') + next, partial);
  }

  function small(className, text, title) {
    return '<div class="' + className + '"' + (title ? ' title="' + esc(title) + '"' : '') + '>' +
      esc(text) + '</div>';
  }

  function tag(modifier, text) {
    return '<div class="tl__expiry ' + modifier + '">' + esc(text) + '</div>';
  }

  function formatDate(iso) {
    var date = new Date(iso);
    var today = new Date();
    var days = Math.floor((today.setHours(0, 0, 0, 0) - new Date(date).setHours(0, 0, 0, 0)) / 86400000);
    if (days === 0) return 'Today';
    if (days === 1) return 'Yesterday';
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
  }

  /* ── Account detail ──────────────────────────────────── */

  /**
   * How each timeline event reads as a statement row. The value column carries the figure, so
   * the description does not repeat it.
   */
  var EVENT_ROW = {
    MONEY_IN: { swatch: 'legend__bar legend__bar--in', text: 'Money in', sign: '+', tone: 'in' },
    MONEY_OUT: { swatch: 'legend__bar legend__bar--out', text: 'Money out', sign: '−', tone: 'out' },
    BONUS_PAID: { swatch: 'tline__dot tline__dot--bonus', text: 'Loyalty bonus paid', sign: '+', tone: 'bonus' },
    BONUS_DUE: { swatch: 'tline__dot tline__dot--bonus-due', text: 'Loyalty bonus due', sign: '+', tone: 'bonus' },
    POINTS_EXPIRING: { swatch: 'tline__dot tline__dot--expiring', text: 'Points expire', sign: '', tone: 'expiring' },
    POINTS_LAPSED: { swatch: 'tline__dot tline__dot--lapsed', text: 'Points lapsed unused', sign: '−', tone: 'lapsed' }
  };

  function openAccountSheet(accountId) {
    var account = accountsById(accountId);
    if (!account || !account.timeline) return;
    state.sheetAccountId = accountId;

    var goal = account.goal
      ? '<span class="tline__chip tline__chip--quiet">goal ' + esc(FX.formatEuro(Number(account.goal))) +
        ' · ' + account.goalProgressPercent + '%</span>'
      : '';
    var rate = account.interestRate
      ? '<span class="tline__chip tline__chip--quiet">' + FX.formatDecimal(Number(account.interestRate)) + '%</span>'
      : '';

    $('#sheetBody').innerHTML =
      '<div class="sheet__head">' +
        '<span class="acc__icon">' + icon('i-piggy') + '</span>' +
        '<div><div class="sheet__name" id="sheetName">' + esc(account.name) + '</div>' +
        '<div class="sheet__sub">' + esc(account.subtitle || '') + ' · ' + esc(account.iban) + '</div></div>' +
      '</div>' +
      '<div class="sheet__amount">' + esc(FX.formatEuro(Number(account.balance))) + '</div>' +
      '<div class="sheet__facts">' + goal + rate + '</div>' +
      '<div class="sheet__section"><h3>The year behind and the year ahead</h3>' +
        timelineMarkup(account, { large: true }) + '</div>' +
      '<div class="sheet__section"><h3>Everything on that bar</h3>' +
        eventList(account.timeline) + '</div>' +
      '<div class="sheet__foot">' +
        '<button class="btn btn--primary" type="button" id="sheetTopUp">Top up this account</button>' +
      '</div>';

    $('#sheetTopUp').addEventListener('click', function (event) {
      FX.ripple(event);
      closeAccountSheet();
      startTransferFrom(account);
    });

    $('#accountSheet').hidden = false;
  }

  function eventList(timeline) {
    if (!timeline.events.length) {
      return '<p class="empty">Nothing has moved in or out of this account in the last year.</p>';
    }
    var rows = timeline.events.map(function (event) {
      var row = EVENT_ROW[event.kind];
      var money = event.kind === 'MONEY_IN' || event.kind === 'MONEY_OUT';
      var figure = money
        ? FX.formatEuro(Number(event.amount))
        : FX.formatNumber(event.points) + ' pts';
      return '<li><span class="event__when">' + esc(formatDay(event.on)) + '</span>' +
        '<span class="event__what"><span class="' + row.swatch + '"></span>' + esc(row.text) + '</span>' +
        '<span class="event__value event__value--' + row.tone + '">' + row.sign + esc(figure) + '</span></li>';
    }).join('');
    var earlier = timeline.earlierMovements
      ? '<p class="events__earlier">' + timeline.earlierMovements +
        (timeline.earlierMovements === 1 ? ' movement' : ' movements') + ' before this window.</p>'
      : '';
    return '<ul class="events">' + rows + '</ul>' + earlier;
  }

  function closeAccountSheet() {
    $('#accountSheet').hidden = true;
    state.sheetAccountId = null;
  }

  /* ── Notifications ───────────────────────────────────── */

  var NOTE_STYLE = {
    BONUS_VESTING_SOON: { klass: 'note--vesting', icon: 'i-flame' },
    BONUS_FORFEITED: { klass: 'note--forfeited', icon: 'i-warning' },
    BALANCE_BELOW: { klass: 'note--below', icon: 'i-warning' },
    BALANCE_ABOVE: { klass: 'note--above', icon: 'i-piggy' }
  };

  function renderNotifications() {
    var data = state.overview;
    var count = $('#bellCount');
    count.hidden = !data.unreadNotifications;
    if (data.unreadNotifications) {
      count.textContent = data.unreadNotifications > 9 ? '9+' : String(data.unreadNotifications);
    }

    $('#noteList').innerHTML = data.notifications.length
      ? data.notifications.map(function (note, index) {
          var style = NOTE_STYLE[note.kind] || { klass: '', icon: 'i-bell' };
          return '<li class="' + style.klass + (note.unread ? ' is-unread' : '') +
            '" style="--delay:' + Math.min(index * 40, 320) + 'ms">' +
            '<span class="note__icon">' + icon(style.icon) + '</span>' +
            '<div class="note__body">' +
              '<div class="note__title">' + esc(note.title) + '</div>' +
              '<div class="note__text">' + esc(note.body) + '</div>' +
              '<div class="note__when">' + esc(formatDate(note.createdAt)) + '</div>' +
            '</div>' +
            (note.unread ? '<span class="note__unread"></span>' : '') +
            '</li>';
        }).join('')
      : '<li class="empty">Nothing to report right now.</li>';

    renderAlertSettings(data.accounts);
  }

  /** One row per account: warn me below for the current account, tell me above for savings. */
  function renderAlertSettings(accounts) {
    $('#alertSettings').innerHTML = accounts.map(function (account) {
      var above = account.type === 'SAVINGS';
      var value = above ? account.alertAbove : account.alertBelow;
      return '<div class="alert-row">' +
        '<span class="alert-row__name">' + esc(account.name) + '</span>' +
        '<span class="alert-row__label">' + (above ? 'above €' : 'below €') + '</span>' +
        '<input type="text" inputmode="decimal" data-alert="' + account.id +
          '" data-bound="' + (above ? 'above' : 'below') + '" placeholder="none" value="' +
          (value === null ? '' : esc(FX.formatDecimal(Number(value)))) + '">' +
        '</div>';
    }).join('');

    $$('#alertSettings input').forEach(function (input) {
      input.addEventListener('keydown', function (event) {
        if (event.key === 'Enter') { event.preventDefault(); input.blur(); }
      });
      input.addEventListener('blur', function () { saveAlert(input); });
    });
  }

  function saveAlert(input) {
    var raw = input.value.trim();
    var amount = raw === '' ? null : parseAmount(raw);
    if (raw !== '' && !(amount > 0)) {
      FX.toast('That alert level looks wrong', 'Enter an amount, or clear the field to switch it off.', 'bad');
      return;
    }
    var body = {};
    body[input.dataset.bound] = amount === null ? null : amount.toFixed(2);

    api('/accounts/' + input.dataset.alert + '/alerts', { method: 'PUT', body: JSON.stringify(body) })
      .then(function (overview) {
        state.previous = state.overview;
        state.overview = overview;
        render({ animate: false });
        FX.toast(amount === null ? 'Alert switched off' : 'Alert saved',
          amount === null ? 'You will not be warned about that account.'
                          : 'We will tell you when it crosses ' + FX.formatEuro(amount) + '.');
      })
      .catch(function (error) { FX.toast('Could not save that alert', error.message, 'bad'); });
  }

  function toggleBell(open) {
    $('#bellPanel').hidden = !open;
    $('#bellToggle').setAttribute('aria-expanded', String(open));
    if (!open) {
      $('#alertSettings').hidden = true;
      $('#alertsToggle').setAttribute('aria-expanded', 'false');
    }
  }

  /* ── Gifts ───────────────────────────────────────────── */

  function renderGifts() {
    var data = state.overview;

    var select = $('#giftTo');
    var keep = Number(select.value) || null;
    select.innerHTML = data.contacts.map(function (contact) {
      return '<option value="' + contact.id + '">' + esc(contact.name) + '</option>';
    }).join('');
    if (data.contacts.length) {
      select.value = String(keep && data.contacts.some(function (c) { return c.id === keep; })
        ? keep : data.contacts[0].id);
    }

    $('#giftBalance').textContent = FX.formatNumber(data.member.pointsBalance);
    updateGiftHint();

    $('#giftList').innerHTML = data.gifts.length
      ? data.gifts.map(function (gift, index) {
          var sent = gift.direction === 'SENT';
          return '<li class="' + (sent ? 'gift--sent' : 'gift--received') +
            '" style="--delay:' + Math.min(index * 45, 400) + 'ms">' +
            '<span class="gift__who">' + esc(gift.counterpartInitials) + '</span>' +
            '<div class="gift__body">' +
              '<div class="gift__name">' + (sent ? 'To ' : 'From ') + esc(gift.counterpartName) + '</div>' +
              '<div class="gift__meta">' + esc(formatDate(gift.createdAt)) +
              (gift.message ? ' · “' + esc(gift.message) + '”' : '') + '</div>' +
            '</div>' +
            '<div class="gift__points">' + (sent ? '−' : '+') + FX.formatNumber(gift.points) + '</div>' +
            '</li>';
        }).join('')
      : '<li class="empty">No gifts yet. Send someone points, or simulate one coming your way.</li>';
  }

  function toggleAddContact(open) {
    var panel = $('#addContact');
    panel.hidden = !open;
    $('#addContactToggle').setAttribute('aria-expanded', String(open));
    $('#contactError').hidden = true;
    if (open) {
      $('#contactFirst').focus();
    } else {
      $('#contactFirst').value = '';
      $('#contactLast').value = '';
    }
  }

  function saveContact() {
    if (state.busy) return;
    var errorEl = $('#contactError');
    errorEl.hidden = true;

    var firstName = $('#contactFirst').value.trim();
    var lastName = $('#contactLast').value.trim();
    if (!firstName || !lastName) {
      showError(errorEl, 'Fill in both a first and a last name.');
      return;
    }

    state.busy = true;
    var button = $('#contactSave');
    button.disabled = true;

    api('/contacts', {
      method: 'POST',
      body: JSON.stringify({ firstName: firstName, lastName: lastName })
    }).then(function (result) {
      state.previous = state.overview;
      state.overview = result.overview;
      render({ animate: false });
      // Pick the person who was just added, so sending to them is the next step.
      $('#giftTo').value = String(result.contact.id);
      updateGiftHint();
      toggleAddContact(false);
      FX.toast(result.contact.name + ' added', 'You can send them points right away.', 'good');
    }).catch(function (error) {
      showError(errorEl, error.message);
    }).finally(function () {
      state.busy = false;
      button.disabled = false;
    });
  }

  function giftPoints() {
    var raw = String($('#giftPoints').value).replace(/[^\d]/g, '');
    return raw ? parseInt(raw, 10) : 0;
  }

  function updateGiftHint() {
    if (!state.overview) return;
    var balance = state.overview.member.pointsBalance;
    var points = giftPoints();
    var hint = $('#giftHint');
    var preview = $('#giftPreview');
    var contact = $('#giftTo').selectedOptions[0];

    if (!contact) {
      hint.textContent = 'There is nobody to send points to.';
      return;
    }
    if (points > balance) {
      preview.classList.add('is-zero');
      hint.textContent = 'That is ' + FX.formatNumber(points - balance) + ' more than you have.';
      return;
    }
    preview.classList.remove('is-zero');
    hint.textContent = points > 0
      ? FX.formatNumber(balance - points) + ' points left after sending ' +
        FX.formatNumber(points) + ' to ' + contact.textContent.split(' ')[0]
      : 'Enter how many points to send.';
  }

  function submitGift(event) {
    event.preventDefault();
    if (state.busy) return;

    var errorEl = $('#giftError');
    errorEl.hidden = true;
    var points = giftPoints();
    if (points <= 0) {
      showError(errorEl, 'Send at least one point.');
      return;
    }
    if (points > state.overview.member.pointsBalance) {
      showError(errorEl, 'You only have ' + FX.formatNumber(state.overview.member.pointsBalance) + ' points.');
      return;
    }

    var button = $('#giftSubmit');
    state.busy = true;
    button.classList.add('is-busy');
    button.disabled = true;

    api('/gifts', {
      method: 'POST',
      body: JSON.stringify({
        toMemberId: Number($('#giftTo').value),
        points: points,
        message: $('#giftMessage').value || null
      })
    }).then(function (result) {
      FX.flyCoins(button, $('#pointsPill'), 6);
      state.previous = state.overview;
      state.overview = result.overview;
      render({ animate: true });
      $('#giftMessage').value = '';
      FX.toast('Sent ' + FX.formatNumber(result.gift.points) + ' points',
        result.gift.counterpartName + ' can spend them right away.', 'good');
    }).catch(function (error) {
      showError(errorEl, error.message);
    }).finally(function () {
      state.busy = false;
      button.classList.remove('is-busy');
      button.disabled = false;
    });
  }

  function simulateIncomingGift() {
    var contact = state.overview.contacts[0];
    if (!contact) return;
    api('/demo/gifts/incoming', {
      method: 'POST',
      body: JSON.stringify({ toMemberId: contact.id, points: 60 })
    }).then(function (result) {
      state.previous = state.overview;
      state.overview = result.overview;
      render({ animate: true });
      FX.pulse($('#pointsPill'), 'is-bumped', 700);
      var pill = $('#pointsPill').getBoundingClientRect();
      FX.confetti(pill.left + pill.width / 2, pill.top + pill.height, 60);
      FX.toast('+' + FX.formatNumber(result.gift.points) + ' points from ' + result.gift.counterpartName,
        'They keep the expiry date they had in their wallet.', 'good');
    }).catch(function (error) {
      FX.toast('That did not work', error.message, 'bad');
    });
  }

  /* ── Transfer form ───────────────────────────────────── */

  function accountsById(id) {
    return state.overview.accounts.filter(function (account) { return account.id === id; })[0];
  }

  function fillAccountSelects(accounts) {
    var from = $('#fromAccount');
    var to = $('#toAccount');
    var keepFrom = Number(from.value) || null;
    var keepTo = Number(to.value) || null;

    from.innerHTML = accounts.map(function (account) { return option(account); }).join('');
    from.value = String(keepFrom && accountsById(keepFrom) ? keepFrom : accounts[0].id);
    syncToOptions(keepTo);
  }

  function option(account) {
    return '<option value="' + account.id + '">' + esc(account.name) + ' — ' +
      esc(FX.formatEuro(Number(account.balance))) + '</option>';
  }

  function syncToOptions(preferredId) {
    var fromId = Number($('#fromAccount').value);
    var to = $('#toAccount');
    var candidates = state.overview.accounts.filter(function (account) { return account.id !== fromId; });
    to.innerHTML = candidates.map(function (account) { return option(account); }).join('');
    var wanted = candidates.filter(function (account) { return account.id === preferredId; })[0];
    to.value = String(wanted ? wanted.id : candidates[0].id);
  }

  function parseAmount(raw) {
    var cleaned = String(raw).replace(/[^\d,.-]/g, '')
      .replace(/,(?=\d{3}(\D|$))/g, '')  // thousands separator
      .replace(',', '.');                  // a lone comma is meant as a decimal point
    var value = parseFloat(cleaned);
    return isNaN(value) ? 0 : Math.round(value * 100) / 100;
  }

  function currentAmount() {
    return parseAmount($('#amount').value);
  }

  function setAmount(value, options) {
    var opts = options || {};
    var rounded = Math.max(0, Math.round(value * 100) / 100);
    $('#amount').value = FX.formatDecimal(rounded);
    if (opts.syncRange !== false) {
      var range = $('#amountRange');
      range.value = String(Math.min(Number(range.max), Math.max(Number(range.min), rounded)));
      paintRange();
    }
    markActiveChip(rounded);
    updatePointsPreview();
  }

  function paintRange() {
    var range = $('#amountRange');
    var percent = ((range.value - range.min) / (range.max - range.min)) * 100;
    range.style.setProperty('--fill', percent + '%');
  }

  function markActiveChip(value) {
    $$('#amountChips .chip').forEach(function (chip) {
      chip.classList.toggle('is-active', Number(chip.dataset.amount) === value);
    });
  }

  /**
   * The share of a deposit that lands above the savings peak. Mirrors BankingService: only
   * that part earns points and counts towards the weekly minimum.
   */
  function newSavingsIn(amount) {
    var data = state.overview;
    if (!growsSavings()) return 0;
    var peak = Math.max(Number(data.member.savingsPeak), Number(data.totalSaved));
    return Math.max(0, Math.min(amount, Number(data.totalSaved) + amount - peak));
  }

  /** Whether the selected pair actually increases the total saved. */
  function growsSavings() {
    var from = accountsById(Number($('#fromAccount').value));
    var to = accountsById(Number($('#toAccount').value));
    return !!from && !!to && from.type === 'CURRENT' && to.type === 'SAVINGS';
  }

  /** Whether this deposit takes the week's new savings over the weekly minimum. */
  function willSecureTheWeek(amount) {
    var member = state.overview.member;
    if (member.streakSafeThisWeek) return false;
    return Number(member.newSavingsThisWeek) + newSavingsIn(amount) >= Number(member.weeklyGoal);
  }

  function plannedMultiplier(amount) {
    var member = state.overview.member;
    return willSecureTheWeek(amount) ? Number(member.nextMultiplier) : Number(member.multiplier);
  }

  function plusPoints(value) {
    return '+' + FX.formatNumber(Math.round(value));
  }

  function updatePointsPreview() {
    if (!state.overview) return;
    var from = accountsById(Number($('#fromAccount').value));
    var to = accountsById(Number($('#toAccount').value));
    var amount = currentAmount();
    var preview = $('#pointsPreview');
    var pointsEl = $('#previewPoints');
    var hint = $('#previewHint');

    if (!growsSavings()) {
      preview.classList.add('is-zero');
      // Tween to zero rather than overwriting the text: a running tween would
      // otherwise keep painting the old target over it.
      FX.countTo(pointsEl, 0, { from: Number(pointsEl.dataset.value || 0), duration: 300, format: plusPoints });
      hint.textContent = from && to && from.type === 'SAVINGS' && to.type === 'SAVINGS'
          ? 'Moving money between savings accounts leaves your total saved unchanged, so it earns no points.'
          : 'Moving money back to your current account earns no points.' + bonusAtRiskNote(from, amount);
      return;
    }

    var multiplier = plannedMultiplier(amount);
    var newSavings = newSavingsIn(amount);
    var base = Math.floor(Math.round(newSavings * 100) / 100);
    var points = Math.floor(base * Math.round(multiplier * 100) / 100);
    preview.classList.toggle('is-zero', points === 0);
    var before = Number(pointsEl.dataset.value || 0);
    FX.countTo(pointsEl, points, { from: before, duration: 420, format: plusPoints });
    if (points !== before) FX.pulse(preview, 'is-bumped', 320);
    if (amount > 0 && newSavings === 0) {
      hint.textContent = 'This only puts back savings you withdrew earlier, so it earns no points.';
      return;
    }
    var restored = amount - newSavings;
    hint.textContent = (restored > 0
        ? FX.formatEuro(newSavings) + ' of this is new savings × ' + FX.formatDecimal(multiplier) + ' multiplier'
        : '1 point per euro × ' + FX.formatDecimal(multiplier) + ' multiplier') +
      streakNote(amount);
  }

  /** Explains what this deposit does to the streak, if anything. */
  function streakNote(amount) {
    var member = state.overview.member;
    if (member.streakSafeThisWeek) return '';
    if (willSecureTheWeek(amount)) return ' (this deposit secures your week)';
    var missing = Number(member.weeklyGoal) - Number(member.newSavingsThisWeek) - newSavingsIn(amount);
    return ' — ' + FX.formatEuro(missing) + ' more new savings this week to secure it';
  }

  /**
   * Flags a withdrawal that could cost a bonus about to vest. It says "may" on purpose: the
   * oldest principal goes first, so whether this particular deposit is touched depends on how
   * much sits in front of it.
   */
  function bonusAtRiskNote(from, amount) {
    if (!from || from.type !== 'SAVINGS' || amount <= 0 || !from.timeline) return '';
    var due = from.timeline.nextBonusOn;
    if (!due || !from.timeline.nextBonusPoints) return '';
    var days = daysUntil(due);
    if (days < 0 || days > 30) return '';
    return ' It may also give up the +' + FX.formatNumber(from.timeline.nextBonusPoints) +
      ' bonus due ' + (days === 0 ? 'today' : days === 1 ? 'tomorrow' : 'in ' + days + ' days') + '.';
  }

  function startTransferFrom(account) {
    showView('transfer');
    if (account.type === 'SAVINGS') {
      $('#fromAccount').value = String(state.overview.accounts.filter(function (other) {
        return other.type === 'CURRENT';
      })[0].id);
      syncToOptions(account.id);
    } else {
      $('#fromAccount').value = String(account.id);
      syncToOptions(null);
    }
    updatePointsPreview();
    $('#amount').focus();
    $('#amount').select();
  }

  function submitTransfer(event) {
    event.preventDefault();
    if (state.busy) return;

    var amount = currentAmount();
    var errorEl = $('#transferError');
    errorEl.hidden = true;

    if (amount <= 0) {
      showTransferError('Enter an amount of at least €0.01.');
      return;
    }

    var fromId = Number($('#fromAccount').value);
    var toId = Number($('#toAccount').value);
    var from = accountsById(fromId);
    if (Number(from.balance) < amount) {
      showTransferError("There isn't enough money in " + from.name + '.');
      return;
    }

    var button = $('#transferSubmit');
    state.busy = true;
    button.classList.add('is-busy');
    button.disabled = true;

    api('/transfers', {
      method: 'POST',
      body: JSON.stringify({ fromAccountId: fromId, toAccountId: toId, amount: amount.toFixed(2) })
    }).then(function (result) {
      FX.flyCoins(button, $('#pointsPill'), result.pointsEarned > 0 ? 8 : 4);
      state.previous = state.overview;
      state.overview = result.overview;

      setTimeout(function () {
        showView('overview');
        render({ animate: true });
        flashAccount(toId);
        if (result.pointsEarned > 0) {
          FX.pulse($('#pointsPill'), 'is-bumped', 700);
          var pill = $('#pointsPill').getBoundingClientRect();
          FX.confetti(pill.left + pill.width / 2, pill.top + pill.height, 70);
          FX.toast('+' + FX.formatNumber(result.pointsEarned) + ' points',
            FX.formatDecimal(Number(result.multiplier)) + '× multiplier · ' +
            FX.formatEuro(Number(result.transfer.amount)) + ' saved', 'good');
        } else if (result.transfer.direction === 'REBALANCE') {
          FX.toast('Moved', FX.formatEuro(Number(result.transfer.amount)) + ' to ' +
            result.transfer.toAccountName + '. Your total saved is unchanged.');
        } else {
          FX.toast('Transferred', FX.formatEuro(Number(result.transfer.amount)) + ' to your current account.');
        }
        if (result.streakExtended) {
          setTimeout(function () {
            FX.toast(result.streakWeeks + '-week streak!', 'Your multiplier rises to ' +
              FX.formatDecimal(Number(state.overview.member.nextMultiplier)) + '× next week.', 'good');
          }, 900);
        }
      }, 620);
    }).catch(function (error) {
      showTransferError(error.message);
    }).finally(function () {
      state.busy = false;
      button.classList.remove('is-busy');
      button.disabled = false;
    });
  }

  function showTransferError(message) {
    showError($('#transferError'), message);
  }

  function showError(element, message) {
    element.textContent = message;
    element.hidden = false;
    FX.pulse(element, 'is-shake', 600);
  }

  function flashAccount(accountId) {
    var card = $('.acc[data-account="' + accountId + '"]');
    if (!card) return;
    card.classList.add('is-flashing');
    setTimeout(function () { card.classList.remove('is-flashing'); }, 900);

    var before = state.previous && state.previous.accounts.filter(function (a) { return a.id === accountId; })[0];
    var after = accountsById(accountId);
    if (before && after) {
      var delta = Number(after.balance) - Number(before.balance);
      var deltaEl = $('[data-delta]', card);
      deltaEl.textContent = (delta > 0 ? '+' : '−') + FX.formatEuro(Math.abs(delta));
      deltaEl.className = 'acc__delta ' + (delta > 0 ? 'is-up' : 'is-down') + ' is-shown';
    }
  }

  /* ── Rewards ─────────────────────────────────────────── */

  function redeem(rewardId, button) {
    if (state.busy) return;
    state.busy = true;
    button.disabled = true;

    api('/rewards/' + rewardId + '/redeem', { method: 'POST' }).then(function (result) {
      var card = $('.reward[data-reward="' + rewardId + '"]');
      if (card) FX.pulse(card, 'is-spent', 700);

      state.previous = state.overview;
      state.overview = result.overview;
      render({ animate: true });
      openVoucher(result.redemption);
      FX.confetti(window.innerWidth / 2, window.innerHeight / 2.4, 120);
      FX.toast('Voucher created', result.redemption.rewardTitle + ' · ' +
        FX.formatNumber(result.redemption.pointsSpent) + ' points', 'good');
    }).catch(function (error) {
      FX.toast('Cannot redeem', error.message, 'bad');
    }).finally(function () {
      state.busy = false;
      button.disabled = false;
    });
  }

  function openVoucher(redemption) {
    $('#voucherTitle').textContent = redemption.rewardTitle;
    $('#voucherCode').textContent = redemption.voucherCode;
    $('#voucherMeta').textContent = FX.formatNumber(redemption.pointsSpent) +
      ' points · voucher valid for 12 months';
    var modal = $('#voucherModal');
    modal.hidden = false;
    $('#copyCode').textContent = 'Copy code';
    document.addEventListener('keydown', onEscape);
  }

  function closeVoucher() {
    $('#voucherModal').hidden = true;
    document.removeEventListener('keydown', onEscape);
  }

  function onEscape(event) {
    if (event.key === 'Escape') closeVoucher();
  }

  /* ── Wiring ──────────────────────────────────────────── */

  function bind() {
    $$('.tab').forEach(function (tab) {
      tab.addEventListener('click', function () { showView(tab.dataset.view); });
    });
    $$('[data-goto]').forEach(function (element) {
      element.addEventListener('click', function (event) {
        if (element.classList.contains('btn')) FX.ripple(event);
        showView(element.dataset.goto);
      });
    });
    $$('.btn').forEach(function (button) { button.addEventListener('click', FX.ripple); });

    $('#fromAccount').addEventListener('change', function () { syncToOptions(null); updatePointsPreview(); });
    $('#toAccount').addEventListener('change', updatePointsPreview);

    $('#swapBtn').addEventListener('click', function () {
      var from = $('#fromAccount');
      var to = $('#toAccount');
      var wantedFrom = Number(to.value);
      var wantedTo = Number(from.value);
      from.value = String(wantedFrom);
      syncToOptions(wantedTo);
      FX.pulse($('#swapBtn'), 'is-spun', 500);
      updatePointsPreview();
    });

    $('#amount').addEventListener('input', function () {
      markActiveChip(currentAmount());
      var range = $('#amountRange');
      var value = currentAmount();
      if (value >= Number(range.min) && value <= Number(range.max)) {
        range.value = String(value);
        paintRange();
      }
      updatePointsPreview();
    });
    $('#amount').addEventListener('blur', function () { setAmount(currentAmount()); });
    $('#amount').addEventListener('focus', function () { $('#amount').select(); });

    $('#amountRange').addEventListener('input', function () {
      setAmount(Number($('#amountRange').value), { syncRange: false });
      paintRange();
    });

    $$('#amountChips .chip').forEach(function (chip) {
      chip.addEventListener('click', function (event) {
        FX.ripple(event);
        setAmount(Number(chip.dataset.amount));
      });
    });

    $('#transferForm').addEventListener('submit', submitTransfer);
    $('#giftForm').addEventListener('submit', submitGift);
    $('#giftTo').addEventListener('change', updateGiftHint);
    $('#giftPoints').addEventListener('input', updateGiftHint);
    $('#demoIncoming').addEventListener('click', simulateIncomingGift);

    $('#bellToggle').addEventListener('click', function (event) {
      event.stopPropagation();
      toggleBell($('#bellPanel').hidden);
    });
    $('#bellPanel').addEventListener('click', function (event) { event.stopPropagation(); });
    document.addEventListener('click', function () { toggleBell(false); });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') toggleBell(false);
    });
    $('#alertsToggle').addEventListener('click', function () {
      var open = $('#alertSettings').hidden;
      $('#alertSettings').hidden = !open;
      $('#alertsToggle').setAttribute('aria-expanded', String(open));
    });
    $('#markRead').addEventListener('click', function () {
      api('/notifications/read', { method: 'POST' }).then(function (overview) {
        state.previous = state.overview;
        state.overview = overview;
        render({ animate: false });
      }).catch(function (error) { FX.toast('Could not mark those read', error.message, 'bad'); });
    });

    $('#addContactToggle').addEventListener('click', function () {
      toggleAddContact($('#addContact').hidden);
    });
    $('#contactCancel').addEventListener('click', function () { toggleAddContact(false); });
    $('#contactSave').addEventListener('click', function (event) { FX.ripple(event); saveContact(); });
    $$('#contactFirst, #contactLast').forEach(function (input) {
      input.addEventListener('keydown', function (event) {
        if (event.key === 'Enter') { event.preventDefault(); saveContact(); }
      });
    });

    $$('#giftChips .chip').forEach(function (chip) {
      chip.addEventListener('click', function (event) {
        FX.ripple(event);
        $('#giftPoints').value = chip.dataset.points === 'all'
          ? String(state.overview.member.pointsBalance)
          : chip.dataset.points;
        $$('#giftChips .chip').forEach(function (other) { other.classList.toggle('is-active', other === chip); });
        updateGiftHint();
      });
    });

    $$('#rewardFilters .chip').forEach(function (chip) {
      chip.addEventListener('click', function (event) {
        FX.ripple(event);
        state.rewardFilter = chip.dataset.filter;
        $$('#rewardFilters .chip').forEach(function (other) { other.classList.toggle('is-active', other === chip); });
        renderRewards();
      });
    });

    $$('#voucherModal [data-close]').forEach(function (element) {
      element.addEventListener('click', closeVoucher);
    });
    $$('#accountSheet [data-close-sheet]').forEach(function (element) {
      element.addEventListener('click', closeAccountSheet);
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') closeAccountSheet();
    });

    $('#copyCode').addEventListener('click', function () {
      var code = $('#voucherCode').textContent;
      var done = function () {
        $('#copyCode').textContent = 'Copied ✓';
        FX.toast('Code copied', code);
      };
      if (navigator.clipboard) {
        navigator.clipboard.writeText(code).then(done).catch(done);
      } else {
        done();
      }
    });

    $('#resetDemo').addEventListener('click', function () {
      api('/demo/reset', { method: 'POST' }).then(function (overview) {
        state.previous = state.overview;
        state.overview = overview;
        render({ animate: true });
        FX.toast('Demo reset', 'Accounts and points are back to their starting values.');
      }).catch(function (error) { FX.toast('Reset failed', error.message, 'bad'); });
    });

    window.addEventListener('resize', moveGlider);
    window.addEventListener('hashchange', function () {
      var name = location.hash.replace('#', '');
      if (name && $('#view-' + name)) showView(name);
    });
  }

  function showSkeletons() {
    $('#accountList').innerHTML = '<div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div>';
    $('#rewardTeaser').innerHTML = '<div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div>';
  }

  function boot() {
    bind();
    paintRange();
    showSkeletons();
    var initial = location.hash.replace('#', '');
    if (initial && $('#view-' + initial)) showView(initial); else moveGlider();

    api('/overview').then(function (overview) {
      state.overview = overview;
      render({ animate: false });
      setAmount(50);
      if (overview.loyaltyJustPaid > 0) {
        FX.toast('+' + FX.formatNumber(overview.loyaltyJustPaid) + ' loyalty points',
          'Paid for savings that stayed put for another year.', 'good');
      }
      document.fonts && document.fonts.ready.then(moveGlider);
    }).catch(function (error) {
      FX.toast('Loading failed', error.message, 'bad');
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot, { once: true });
  } else {
    boot();
  }
})();
