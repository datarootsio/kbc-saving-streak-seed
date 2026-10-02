import { test, expect } from '@playwright/test';

test.beforeEach(async ({ request }) => {
  const response = await request.get('/api/reminders');
  expect(response.ok()).toBeTruthy();
  for (const reminder of await response.json()) {
    const deleted = await request.delete('/api/reminders/' + reminder.id);
    expect(deleted.status()).toBe(204);
  }
});

test('create, edit, and complete a reminder, keeping each change after refresh', async ({ page, request }) => {
  const year = new Date().getUTCFullYear() + 1;
  await page.goto('/');
  await page.getByLabel('Remind me to').fill('Send the agenda');
  await page.getByLabel('When', { exact: true }).fill(year + '-06-15T14:30');
  await page.getByRole('button', { name: 'Add reminder' }).click();
  await expect(page.getByRole('heading', { name: 'Send the agenda', exact: true })).toBeVisible();
  await page.reload();

  await page.getByRole('button', { name: 'Edit Send the agenda', exact: true }).click();
  await expect(page.getByLabel('When', { exact: true })).toHaveValue(year + '-06-15T14:30');
  await page.getByLabel('Remind me to').fill('Send the revised agenda');
  await page.getByLabel('When', { exact: true }).fill(year + '-06-16T16:00');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('heading', { name: 'Send the revised agenda', exact: true })).toBeVisible();
  await page.reload();

  const savedResponse = await request.get('/api/reminders');
  expect(savedResponse.ok()).toBeTruthy();
  const [saved] = await savedResponse.json();
  expect(saved.title).toBe('Send the revised agenda');
  expect(new Date(saved.dueAt).toISOString()).toBe(year + '-06-16T16:00:00.000Z');
  expect(saved.timeZone).toBe('UTC');

  await page.getByRole('button', { name: 'Complete Send the revised agenda', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Send the revised agenda', exact: true })).toHaveCount(0);
  await page.reload();
  await page.getByRole('button', { name: /^Completed / }).click();
  await expect(page.getByRole('heading', { name: 'Send the revised agenda', exact: true })).toBeVisible();
  const completed = await request.get('/api/reminders/' + saved.id);
  expect(completed.ok()).toBeTruthy();
  expect((await completed.json()).completed).toBe(true);
});

test('renaming a daily reminder keeps its time zone across daylight saving', async ({ page, request }) => {
  // The last Sunday in March next year is in the future regardless of today's date.
  // Brussels changes from UTC+1 to UTC+2 that morning.
  const transition = new Date(Date.UTC(new Date().getUTCFullYear() + 1, 2, 31, 7));
  transition.setUTCDate(transition.getUTCDate() - transition.getUTCDay());
  const previousDay = new Date(transition);
  previousDay.setUTCDate(previousDay.getUTCDate() - 1);
  previousDay.setUTCHours(8);

  const created = await request.post('/api/reminders', { data: {
    title: 'Brussels morning reminder', dueAt: previousDay.toISOString(),
    repeat: 'DAILY', timeZone: 'Europe/Brussels'
  } });
  expect(created.status()).toBe(201);
  const reminder = await created.json();

  await page.goto('/');
  await page.getByRole('button', { name: 'Edit Brussels morning reminder', exact: true }).click();
  await page.getByLabel('Remind me to').fill('Renamed Brussels reminder');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('heading', { name: 'Renamed Brussels reminder', exact: true })).toBeVisible();
  await page.reload();

  const completed = page.waitForResponse(response => response.url().endsWith('/' + reminder.id + '/complete'));
  await page.getByRole('button', { name: 'Complete Renamed Brussels reminder', exact: true }).click();
  expect((await completed).ok()).toBeTruthy();

  const nextResponse = await request.get('/api/reminders/' + reminder.id);
  expect(nextResponse.ok()).toBeTruthy();
  const next = await nextResponse.json();
  expect(new Date(next.dueAt).toISOString()).toBe(transition.toISOString());
  expect(next.timeZone).toBe('Europe/Brussels');
  expect(next.dailyTime).toBe('09:00:00');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Renamed Brussels reminder', exact: true })).toHaveCount(0);
  await page.clock.setFixedTime(new Date(next.visibleFrom));
  const card = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'Renamed Brussels reminder', exact: true }) });
  await expect(card.locator('time')).toHaveAttribute('datetime', next.dueAt);
});

for (const field of ['When', 'Repeat']) {
  test('changing ' + field + ' adopts the browser time zone for the new schedule', async ({ page, request }) => {
    const year = new Date().getUTCFullYear() + 1;
    const created = await request.post('/api/reminders', { data: {
      title: 'Change my schedule', dueAt: year + '-01-15T08:00:00Z',
      repeat: 'DAILY', timeZone: 'Europe/Brussels'
    } });
    expect(created.status()).toBe(201);
    const reminder = await created.json();

    await page.goto('/');
    await page.getByRole('button', { name: 'Edit Change my schedule', exact: true }).click();
    if (field === 'When') {
      await page.getByLabel('When', { exact: true }).fill(year + '-01-15T10:30');
    } else {
      await page.getByLabel('Repeat', { exact: true }).selectOption('ONCE');
    }
    const updatedResponse = page.waitForResponse(response =>
      response.url().endsWith('/' + reminder.id) && response.request().method() === 'PUT');
    await page.getByRole('button', { name: 'Save changes' }).click();
    expect((await updatedResponse).ok()).toBeTruthy();
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Change my schedule', exact: true })).toBeVisible();

    const savedResponse = await request.get('/api/reminders/' + reminder.id);
    expect(savedResponse.ok()).toBeTruthy();
    const saved = await savedResponse.json();
    expect(saved.timeZone).toBe('UTC');
    expect(saved.repeat).toBe(field === 'When' ? 'DAILY' : 'ONCE');
    expect(saved.dailyTime).toBe(field === 'When' ? '10:30:00' : null);
    expect(new Date(saved.dueAt).toISOString()).toBe(year + (field === 'When' ? '-01-15T10:30:00.000Z' : '-01-15T08:00:00.000Z'));
  });
}

test('weekly reminders keep the chosen weekday after editing and completing', async ({ page, request }) => {
  const year = new Date().getUTCFullYear() + 1;
  const first = new Date(Date.UTC(year, 5, 15, 14, 30));
  first.setUTCDate(first.getUTCDate() + (5 - first.getUTCDay() + 7) % 7);
  await page.goto('/');
  await page.getByLabel('Remind me to').fill('Weekly review');
  await page.getByLabel('When', { exact: true }).fill(year + '-06-15T14:30');
  await page.getByLabel('Repeat', { exact: true }).selectOption('WEEKLY');
  await page.getByLabel('Day of the week', { exact: true }).selectOption({ label: 'Friday' });
  await expect(page.getByLabel('Day of the month', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Add reminder' }).click();
  await expect(page.getByText('↻ Every Friday', { exact: true })).toBeVisible();
  const [reminder] = await (await request.get('/api/reminders')).json();
  expect(new Date(reminder.dueAt).toISOString()).toBe(first.toISOString());
  expect(reminder.dayOfWeek).toBe('FRIDAY');
  expect(reminder.dayOfMonth).toBeNull();

  await page.reload();
  await page.getByRole('button', { name: 'Edit Weekly review', exact: true }).click();
  await expect(page.getByLabel('Day of the week', { exact: true })).toHaveValue('string:FRIDAY');
  await page.getByLabel('Remind me to').fill('Weekly team review');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('heading', { name: 'Weekly team review', exact: true })).toBeVisible();
  const completed = page.waitForResponse(response => response.url().endsWith('/' + reminder.id + '/complete'));
  await page.getByRole('button', { name: 'Complete Weekly team review', exact: true }).click();
  expect((await completed).ok()).toBeTruthy();
  first.setUTCDate(first.getUTCDate() + 7);
  const next = await (await request.get('/api/reminders/' + reminder.id)).json();
  expect(new Date(next.dueAt).toISOString()).toBe(first.toISOString());
  expect(next.completed).toBe(false);
  await page.reload();
  await expect(page.getByText('↻ Every Friday', { exact: true })).toHaveCount(0);
  await page.clock.setFixedTime(new Date(next.visibleFrom));
  await expect(page.getByText('↻ Every Friday', { exact: true })).toBeVisible();
});

test('monthly reminders return to day 31 after a shorter month and a rename', async ({ page, request }) => {
  const year = new Date().getUTCFullYear() + 1;
  await page.goto('/');
  await page.getByLabel('Remind me to').fill('Monthly review');
  await page.getByLabel('When', { exact: true }).fill(year + '-01-31T09:00');
  await page.getByLabel('Repeat', { exact: true }).selectOption('MONTHLY');
  await page.getByLabel('Day of the month', { exact: true }).selectOption({ label: '31' });
  await expect(page.getByLabel('Day of the week', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Add reminder' }).click();
  await expect(page.getByText('↻ Monthly · day 31', { exact: true })).toBeVisible();
  const [reminder] = await (await request.get('/api/reminders')).json();
  expect(reminder.dayOfMonth).toBe(31);
  expect(reminder.dayOfWeek).toBeNull();

  const completed = page.waitForResponse(response => response.url().endsWith('/' + reminder.id + '/complete'));
  await page.getByRole('button', { name: 'Complete Monthly review', exact: true }).click();
  expect((await completed).ok()).toBeTruthy();
  const february = await (await request.get('/api/reminders/' + reminder.id)).json();
  expect(new Date(february.dueAt).toISOString()).toBe(new Date(Date.UTC(year, 2, 0, 9)).toISOString());
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Monthly review', exact: true })).toHaveCount(0);
  await page.clock.setFixedTime(new Date(february.visibleFrom));
  await expect(page.getByRole('heading', { name: 'Monthly review', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Edit Monthly review', exact: true }).click();
  await expect(page.getByLabel('Day of the month', { exact: true })).toHaveValue('number:31');
  await page.getByLabel('Remind me to').fill('Monthly team review');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('heading', { name: 'Monthly team review', exact: true })).toBeVisible();
  const completedAgain = page.waitForResponse(response => response.url().endsWith('/' + reminder.id + '/complete'));
  await page.getByRole('button', { name: 'Complete Monthly team review', exact: true }).click();
  expect((await completedAgain).ok()).toBeTruthy();
  const march = await (await request.get('/api/reminders/' + reminder.id)).json();
  expect(new Date(march.dueAt).toISOString()).toBe(year + '-03-31T09:00:00.000Z');
  expect(march.dayOfMonth).toBe(31);
  expect(march.completed).toBe(false);
  await page.reload();
  await expect(page.getByText('↻ Monthly · day 31', { exact: true })).toHaveCount(0);
  await page.clock.setFixedTime(new Date(march.visibleFrom));
  await expect(page.getByText('↻ Monthly · day 31', { exact: true })).toBeVisible();
});

for (const repeat of ['DAILY', 'WEEKLY', 'MONTHLY']) {
  test(repeat + ' hides its next occurrence until midnight in the saved time zone', async ({ page, request }) => {
    const year = new Date().getUTCFullYear() + 1;
    const start = new Date(Date.UTC(year, 0, 31, 8));
    const days = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
    const created = await request.post('/api/reminders', { data: {
      title: 'Recurring reminder', dueAt: start.toISOString(), repeat, timeZone: 'Europe/Brussels',
      dayOfWeek: repeat === 'WEEKLY' ? days[start.getUTCDay()] : null,
      dayOfMonth: repeat === 'MONTHLY' ? 31 : null
    } });
    expect(created.status()).toBe(201);
    const reminder = await created.json();
    expect(reminder.visibleFrom).toBeNull();
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Recurring reminder', exact: true })).toBeVisible();
    const completed = page.waitForResponse(response => response.url().endsWith('/' + reminder.id + '/complete'));
    await page.getByRole('button', { name: 'Complete Recurring reminder', exact: true }).click();
    const next = await (await completed).json();
    await expect(page.getByRole('heading', { name: 'Recurring reminder', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Active 0', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Completed 0', exact: true })).toBeVisible();

    // The browser is in UTC. Reappearance follows Brussels midnight, which is 23:00 UTC in winter.
    const midnight = new Date(next.visibleFrom);
    expect(midnight.getUTCHours()).toBe(23);
    expect(midnight.getUTCMinutes()).toBe(0);
    expect(new Date(next.dueAt).getTime() - midnight.getTime()).toBe(9 * 60 * 60 * 1000);
    await page.clock.setFixedTime(new Date(midnight.getTime() - 1));
    await page.reload();
    await expect(page.getByRole('heading', { name: 'All clear.', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Recurring reminder', exact: true })).toHaveCount(0);
    await page.clock.setFixedTime(midnight);
    // No reload: the app's clock brings the occurrence back automatically.
    await expect(page.getByRole('heading', { name: 'Recurring reminder', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Active 1', exact: true })).toBeVisible();
    await expect(page.getByText('Due now', { exact: true })).toHaveCount(0);
    const saved = await (await request.get('/api/reminders/' + reminder.id)).json();
    expect(saved.dueAt).toBe(next.dueAt);
    expect(saved.completed).toBe(false);
  });
}
