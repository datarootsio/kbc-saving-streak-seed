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
