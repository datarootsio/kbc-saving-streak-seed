(function () {
  'use strict';

  angular.module('reminderApp', []).directive('reminderMotion', ['$window', function ($window) {
    return {
      restrict: 'A',
      link: function (scope, element) {
        var positions = new Map();
        var animations = new Set();
        var motionPreference = $window.matchMedia('(prefers-reduced-motion: reduce)');
        var frame;
        var firstRender = true;
        function arrange() {
          var next = new Map();
          var reducedMotion = motionPreference.matches;
          element[0].querySelectorAll('[data-reminder-id]').forEach(function (card, index) {
            var id = card.dataset.reminderId;
            var position = { top: card.offsetTop, left: card.offsetLeft };
            var previous = positions.get(id);
            next.set(id, position);
            if (reducedMotion || !card.animate) return;
            var keyframes;
            if (!previous) {
              keyframes = [{ opacity: 0, transform: 'translateY(14px) scale(.98)' }, { opacity: 1, transform: 'none' }];
            } else if (previous.top !== position.top || previous.left !== position.left) {
              keyframes = [{ transform: 'translate(' + (previous.left - position.left) + 'px, ' + (previous.top - position.top) + 'px)' }, { transform: 'none' }];
            }
            if (!keyframes) return;
            var animation = card.animate(keyframes, {
              duration: previous ? 460 : 520, easing: 'cubic-bezier(.22, 1, .36, 1)',
              delay: firstRender ? Math.min(index, 5) * 55 : 0, fill: 'backwards'
            });
            animations.add(animation);
            animation.onfinish = function () { animations.delete(animation); };
          });
          positions = next;
          if (next.size) firstRender = false;
        }
        // Animate DOM additions and moves without rebuilding the list or delaying data changes.
        var observer = new MutationObserver(function () {
          $window.cancelAnimationFrame(frame);
          frame = $window.requestAnimationFrame(arrange);
        });
        observer.observe(element[0], { childList: true, subtree: true });
        function cancelMotion() { animations.forEach(function (animation) { animation.cancel(); }); animations.clear(); }
        function motionChanged() { if (motionPreference.matches) cancelMotion(); }
        function resized() {
          cancelMotion();
          element[0].querySelectorAll('[data-reminder-id]').forEach(function (card) {
            positions.set(card.dataset.reminderId, { top: card.offsetTop, left: card.offsetLeft });
          });
        }
        motionPreference.addEventListener('change', motionChanged);
        $window.addEventListener('resize', resized);
        frame = $window.requestAnimationFrame(arrange);
        scope.$on('$destroy', function () {
          observer.disconnect();
          $window.cancelAnimationFrame(frame);
          motionPreference.removeEventListener('change', motionChanged);
          $window.removeEventListener('resize', resized);
          cancelMotion();
        });
      }
    };
  }]).controller('ReminderController', ['$http', '$interval', '$window', '$timeout', '$scope',
    function ($http, $interval, $window, $timeout, $scope) {
      var vm = this;
      var endpoint = 'api/reminders';
      var originalSchedule = null;
      var noticeTimeout;
      var highlightTimeout;
      vm.reminders = [];
      vm.filter = 'active';
      vm.timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      vm.now = new Date();
      vm.weekdays = [
        { value: 'SUNDAY', label: 'Sunday' }, { value: 'MONDAY', label: 'Monday' },
        { value: 'TUESDAY', label: 'Tuesday' }, { value: 'WEDNESDAY', label: 'Wednesday' },
        { value: 'THURSDAY', label: 'Thursday' }, { value: 'FRIDAY', label: 'Friday' },
        { value: 'SATURDAY', label: 'Saturday' }
      ];
      vm.monthDays = [];
      for (var day = 1; day <= 31; day++) vm.monthDays.push(day);
      vm.today = vm.now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });

      vm.repeatLabel = function (reminder) {
        if (reminder.repeat === 'DAILY') return 'Every day';
        if (reminder.repeat === 'WEEKLY') {
          return 'Every ' + vm.weekdays.filter(function (day) { return day.value === reminder.dayOfWeek; })[0].label;
        }
        return 'Monthly · day ' + reminder.dayOfMonth;
      };
      vm.changeRepeat = function () {
        if (!vm.form.dueAt) return;
        vm.form.dayOfWeek = vm.weekdays[vm.form.dueAt.getDay()].value;
        vm.form.dayOfMonth = vm.form.dueAt.getDate();
      };
      vm.isActive = function (reminder) {
        return !reminder.completed && (!reminder.visibleFrom || new Date(reminder.visibleFrom) <= vm.now);
      };
      vm.count = function (filter) {
        return vm.reminders.filter(function (r) { return filter === 'completed' ? r.completed : vm.isActive(r); }).length;
      };
      vm.visibleReminders = function () {
        return vm.reminders.filter(function (r) { return vm.filter === 'completed' ? r.completed : vm.isActive(r); });
      };
      vm.isDue = function (reminder) {
        return vm.isActive(reminder) && new Date(reminder.dueAt) <= vm.now;
      };
      vm.dueCount = function () { return vm.reminders.filter(vm.isDue).length; };
      vm.formatWhen = function (value) {
        var date = new Date(value);
        var tomorrow = new Date(vm.now);
        tomorrow.setDate(tomorrow.getDate() + 1);
        var day = date.toDateString() === vm.now.toDateString() ? 'Today'
          : date.toDateString() === tomorrow.toDateString() ? 'Tomorrow'
          : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: date.getFullYear() !== vm.now.getFullYear() ? 'numeric' : undefined });
        return day + ' at ' + date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
      };
      function showError(response) {
        vm.error = response.data && response.data.message || 'Could not reach the app. Check that the server is running, then try again.';
      }
      function notify(message) {
        vm.message = message;
        vm.noticeVisible = true;
        $timeout.cancel(noticeTimeout);
        noticeTimeout = $timeout(function () { vm.noticeVisible = false; }, 4500);
      }
      function highlight(id) {
        vm.highlightedId = id;
        $timeout.cancel(highlightTimeout);
        highlightTimeout = $timeout(function () { vm.highlightedId = null; }, 1800);
      }
      vm.load = function () {
        vm.loading = true;
        return $http.get(endpoint).then(function (response) {
          vm.reminders = response.data;
          vm.loaded = true;
          vm.error = '';
        }, showError).finally(function () { vm.loading = false; });
      };
      vm.resetForm = function (form) {
        var due = new Date();
        due.setMinutes(due.getMinutes() + 30, 0, 0);
        vm.form = { title: '', dueAt: due, repeat: 'ONCE',
          dayOfWeek: vm.weekdays[due.getDay()].value, dayOfMonth: due.getDate() };
        vm.editingId = null;
        originalSchedule = null;
        if (form) { form.$setPristine(); form.$setUntouched(); }
      };
      vm.edit = function (reminder) {
        vm.editingId = reminder.id;
        var due = new Date(reminder.dueAt);
        vm.form = { title: reminder.title, dueAt: due, repeat: reminder.repeat,
          dayOfWeek: reminder.dayOfWeek || vm.weekdays[due.getDay()].value,
          dayOfMonth: reminder.dayOfMonth || due.getDate() };
        originalSchedule = { dueAt: vm.form.dueAt.getTime(), repeat: reminder.repeat, timeZone: reminder.timeZone,
          dayOfWeek: reminder.dayOfWeek || null, dayOfMonth: reminder.dayOfMonth || null };
        focusComposer();
      };
      function focusComposer() {
        $timeout(function () {
          var composer = document.querySelector('.composer');
          if ($window.innerWidth <= 800) {
            composer.scrollIntoView({ behavior: $window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'start' });
          }
          document.getElementById('title').focus({ preventScroll: true });
        });
      }
      vm.newReminder = function (form) { vm.resetForm(form); focusComposer(); };
      vm.save = function (form) {
        if (form.$invalid || vm.busy || vm.loading) return;
        vm.busy = true;
        vm.error = '';
        var editing = !!vm.editingId;
        var weekday = vm.form.repeat === 'WEEKLY' ? vm.form.dayOfWeek : null;
        var monthDay = vm.form.repeat === 'MONTHLY' ? vm.form.dayOfMonth : null;
        // Renaming keeps the saved schedule; changing its date, frequency, or day uses the browser zone.
        var sameSchedule = originalSchedule && vm.form.dueAt.getTime() === originalSchedule.dueAt
          && vm.form.repeat === originalSchedule.repeat && weekday === originalSchedule.dayOfWeek
          && monthDay === originalSchedule.dayOfMonth;
        var input = { title: vm.form.title, dueAt: vm.form.dueAt.toISOString(), repeat: vm.form.repeat,
          timeZone: sameSchedule ? originalSchedule.timeZone : vm.timeZone, dayOfWeek: weekday, dayOfMonth: monthDay };
        var request = editing ? $http.put(endpoint + '/' + vm.editingId, input) : $http.post(endpoint, input);
        request.then(function (response) {
          notify(editing ? 'Reminder updated' : 'Reminder added');
          vm.filter = 'active';
          vm.resetForm(form);
          return vm.load().then(function () { highlight(response.data.id); });
        }, showError).finally(function () { vm.busy = false; });
      };
      vm.complete = function (reminder) {
        if (vm.busy || vm.loading) return;
        vm.busy = true;
        vm.error = '';
        $http.post(endpoint + '/' + reminder.id + '/complete', { dueAt: reminder.dueAt }).then(function (response) {
          notify(reminder.repeat !== 'ONCE' ? 'Done · Next: ' + vm.formatWhen(response.data.dueAt) : 'Reminder completed');
          if (vm.editingId === reminder.id) vm.resetForm();
          // Show completion only after the server confirms it; reduced motion skips the visual pause.
          vm.completingId = reminder.id;
          vm.leaving = true;
          var delay = $window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 440;
          return $timeout(angular.noop, delay).then(vm.load);
        }, showError).finally(function () { vm.busy = false; vm.completingId = null; vm.leaving = false; });
      };
      vm.remove = function (reminder) {
        if (vm.busy || vm.loading || !$window.confirm('Delete “' + reminder.title + '”?')) return;
        vm.busy = true;
        vm.error = '';
        $http.delete(endpoint + '/' + reminder.id).then(function () {
          if (vm.editingId === reminder.id) vm.resetForm();
          notify('Reminder deleted');
          return vm.load();
        }, showError).finally(function () { vm.busy = false; });
      };
      // The app shows due reminders in-page; no browser notification permission is required.
      function clockAngle(previous, target) {
        if (previous === undefined) return target;
        // Cross midnight and full rotations along the shortest arc, rather than rewinding.
        var difference = (target - previous) % 360;
        if (difference < -180) difference += 360;
        if (difference > 180) difference -= 360;
        return previous + difference;
      }
      function updateClock() {
        vm.now = new Date();
        vm.today = vm.now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
        vm.hourAngle = clockAngle(vm.hourAngle, (vm.now.getHours() % 12) * 30
          + vm.now.getMinutes() / 2 + vm.now.getSeconds() / 120);
        vm.minuteAngle = clockAngle(vm.minuteAngle, vm.now.getMinutes() * 6 + vm.now.getSeconds() / 10);
      }
      var clockInterval = $interval(updateClock, 1000);
      updateClock();
      $scope.$on('$destroy', function () {
        $interval.cancel(clockInterval);
        $timeout.cancel(noticeTimeout);
        $timeout.cancel(highlightTimeout);
      });
      vm.resetForm();
      vm.load();
    }]);
}());
