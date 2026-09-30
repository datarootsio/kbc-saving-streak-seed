(function () {
  'use strict';

  angular.module('reminderApp', []).controller('ReminderController', ['$http', '$interval', '$window', '$timeout',
    function ($http, $interval, $window, $timeout) {
      var vm = this;
      var endpoint = 'api/reminders';
      var originalSchedule = null;
      var noticeTimeout;
      var highlightTimeout;
      vm.reminders = [];
      vm.filter = 'active';
      vm.timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      vm.now = new Date();
      vm.today = vm.now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });

      vm.count = function (filter) {
        return vm.reminders.filter(function (r) { return r.completed === (filter === 'completed'); }).length;
      };
      vm.visibleReminders = function () {
        return vm.reminders.filter(function (r) { return r.completed === (vm.filter === 'completed'); });
      };
      vm.isDue = function (reminder) {
        return !reminder.completed && new Date(reminder.dueAt) <= vm.now;
      };
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
        noticeTimeout = $timeout(function () { vm.noticeVisible = false; }, 6500);
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
        vm.form = { title: '', dueAt: due, repeat: 'ONCE' };
        vm.editingId = null;
        originalSchedule = null;
        if (form) { form.$setPristine(); form.$setUntouched(); }
      };
      vm.edit = function (reminder) {
        vm.editingId = reminder.id;
        vm.form = { title: reminder.title, dueAt: new Date(reminder.dueAt), repeat: reminder.repeat };
        originalSchedule = { dueAt: vm.form.dueAt.getTime(), repeat: reminder.repeat, timeZone: reminder.timeZone };
        $timeout(function () { document.getElementById('title').focus(); });
      };
      vm.save = function (form) {
        if (form.$invalid || vm.busy || vm.loading) return;
        vm.busy = true;
        vm.error = '';
        var editing = !!vm.editingId;
        // Renaming keeps the saved schedule; changing When or Repeat uses the displayed time zone.
        var sameSchedule = originalSchedule && vm.form.dueAt.getTime() === originalSchedule.dueAt && vm.form.repeat === originalSchedule.repeat;
        var input = { title: vm.form.title, dueAt: vm.form.dueAt.toISOString(), repeat: vm.form.repeat, timeZone: sameSchedule ? originalSchedule.timeZone : vm.timeZone };
        var request = editing ? $http.put(endpoint + '/' + vm.editingId, input) : $http.post(endpoint, input);
        request.then(function (response) {
          notify(editing ? 'Reminder updated. All set.' : 'Reminder added. One less thing to keep in your head.');
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
          notify(reminder.repeat === 'DAILY' ? 'Done for now. Next reminder: ' + vm.formatWhen(response.data.dueAt) + '.' : 'Done. A little weight off your mind.');
          if (vm.editingId === reminder.id) vm.resetForm();
          // Show completion only after the server confirms it; reduced motion skips the visual pause.
          vm.completingId = reminder.id;
          var delay = $window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 220;
          return $timeout(angular.noop, delay).then(vm.load);
        }, showError).finally(function () { vm.busy = false; vm.completingId = null; });
      };
      vm.remove = function (reminder) {
        if (vm.busy || vm.loading || !$window.confirm('Delete “' + reminder.title + '”?')) return;
        vm.busy = true;
        vm.error = '';
        $http.delete(endpoint + '/' + reminder.id).then(function () {
          if (vm.editingId === reminder.id) vm.resetForm();
          notify('Reminder deleted.');
          return vm.load();
        }, showError).finally(function () { vm.busy = false; });
      };
      // The app shows due reminders in-page; no browser notification permission is required.
      $interval(function () {
        vm.now = new Date();
        vm.today = vm.now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
      }, 1000);
      vm.resetForm();
      vm.load();
    }]);
}());
