/**
 * Numeric investigation, ticket 11: loading and retryable error states, stale responses ignored,
 * odd columns (all blank, huge, non-finite) and a renamed or removed column under an open card.
 * cy.intercept delays or fails the numeric health endpoint. Fixture values import as strings, so
 * numeric columns are converted with "To number" first.
 */
const fixture = [
  ['name', 'amount'],
  ['Alpha', '0'],
  ['alpha', '1'],
  ['ALPHA', '-1'],
  ['Beta', null],
  ['Gamma', '2.5'],
  ['Delta', '0'],
];

const ENDPOINT = '**/command/core/get-numeric-health*';
const idle = () => cy.get('body[ajax_in_progress="false"]');
const card = (col = 'amount') => cy.get(`#investigation-card-strip .investigation-card[data-column="${col}"]`);
const bar = (col = 'amount') => cy.get(`.health-bar[data-column="${col}"]`);
const stat = (name, col) => card(col).find('[data-stat="' + name + '"] dd');

// Classifies a request to the endpoint: which kind of caller, which column, and whether it carries
// a list facet selection on "name".
const describeRequest = (req) => {
  const p = new URLSearchParams(req.body);
  const engine = JSON.parse(p.get('engine'));
  return {
    kind: req.url.includes('project=') ? 'health' : p.get('bins') === '20' ? 'card' : 'step',
    column: p.get('columnName'),
    filtered: engine.facets.some((f) => f.type === 'list'),
  };
};

const toNumber = (col = 'amount') => {
  idle();
  cy.columnActionClick(col, ['Edit cells', 'Common transforms', 'To number']);
  idle();
};

const load = (data = fixture, cols = ['amount']) => {
  cy.loadAndVisitProject(data);
  cols.forEach((c) => toNumber(c));
};

const openCard = (col = 'amount') => {
  cy.columnActionClick(col, ['Facet', 'Investigate numbers']);
  card(col).find('.investigation-histogram-bar').should('have.length', 20);
};

const textFacetToggle = (label) => {
  if (!Cypress.$('#refine-tabs-facets .facets-container .facet-container').length) {
    cy.columnActionClick('name', ['Facet', 'Text facet']);
  }
  cy.getFacetContainer('name')
    .contains('.facet-choice-label', new RegExp('^' + label + '$'))
    .click();
  idle();
};

const setRange = (col, from, to) => {
  card(col).find('.investigation-range-from').invoke('val', from).trigger('change');
  idle();
  card(col).find('.investigation-range-to').invoke('val', to).trigger('change');
  idle();
};

// Requests matching `match` are answered `delayMs` late; the rest pass through at once.
const delayWhen = (match, delayMs) =>
  cy.intercept('POST', ENDPOINT, (req) => {
    if (match(describeRequest(req))) {
      req.on('response', (res) => res.setDelay(delayMs));
    }
  });

const failWhen = (flag, match) =>
  cy.intercept('POST', ENDPOINT, (req) => {
    if (flag.on && match(describeRequest(req))) {
      req.reply({ statusCode: 500, body: 'boom' });
    }
  });

describe(__filename, function () {
  describe('loading and retryable errors (story 34)', function () {
    it('the header bar shows a loading state until counts arrive', function () {
      delayWhen((r) => r.kind === 'health', 1500);
      cy.loadAndVisitProject(fixture);
      cy.get('.health-bar-loading[data-column="amount"]').should('exist');
      cy.get('.health-bar-loading').should('not.exist');
    });

    it('a failed header request shows a Retry control and Retry recovers the bar', function () {
      const flag = { on: false };
      failWhen(flag, (r) => r.kind === 'health' && r.column === 'amount');
      load();
      bar().should('exist');
      cy.then(() => { flag.on = true; });
      cy.columnActionClick('amount', ['Edit cells', 'Common transforms', 'To text']);
      cy.get('.health-bar-retry[data-column="amount"]').should('be.visible');
      cy.then(() => { flag.on = false; });
      cy.get('.health-bar-retry[data-column="amount"]').click();
      cy.get('.health-bar-retry').should('not.exist');
      cy.get('.health-bar-loading').should('not.exist');
    });

    it('a card whose first load fails shows an error with Retry, and Retry loads it', function () {
      const flag = { on: false };
      failWhen(flag, (r) => r.kind === 'card');
      load();
      cy.then(() => { flag.on = true; });
      cy.columnActionClick('amount', ['Facet', 'Investigate numbers']);
      card().find('.investigation-card-error').should('be.visible');
      cy.then(() => { flag.on = false; });
      card().find('.investigation-card-retry').click();
      card().find('.investigation-histogram-bar').should('have.length', 20);
      stat('count').should('have.text', '5');
      card().find('.investigation-card-error').should('not.exist');
    });

    it('a failed card refresh keeps the data on screen, says so, and Retry refreshes', function () {
      const flag = { on: false };
      failWhen(flag, (r) => r.kind === 'card');
      load();
      openCard();
      cy.then(() => { flag.on = true; });
      textFacetToggle('Alpha');
      card().find('.investigation-card-refresh-error').should('be.visible');
      stat('count').should('have.text', '5');
      cy.then(() => { flag.on = false; });
      card().find('.investigation-card-retry').click();
      stat('count').should('have.text', '1');
      card().find('.investigation-card-refresh-error').should('not.exist');
    });

    it('a failed breadcrumb count shows an error with Retry, and Retry restores the numbers', function () {
      const flag = { on: false };
      failWhen(flag, (r) => r.kind === 'step');
      load();
      openCard();
      setRange('amount', -1, 1);
      cy.get('#investigation-breadcrumb .investigation-breadcrumb-shown').should('have.text', '4 shown');
      cy.then(() => { flag.on = true; });
      textFacetToggle('Alpha');
      cy.get('#investigation-breadcrumb .investigation-breadcrumb-error').should('be.visible');
      cy.then(() => { flag.on = false; });
      cy.get('#investigation-breadcrumb .investigation-breadcrumb-retry').click();
      cy.get('#investigation-breadcrumb .investigation-breadcrumb-shown').should('have.text', '1 shown');
      cy.get('#investigation-breadcrumb .investigation-breadcrumb-error').should('not.exist');
    });
  });

  describe('late responses never overwrite newer ones (story 54)', function () {
    // The facet is switched on (its requests answered late) and straight off again (answered at once).
    // The late answers describe the filtered state and must not win.
    it('header bar', function () {
      delayWhen((r) => r.kind === 'health' && r.filtered, 2000);
      load();
      bar().should('have.attr', 'data-total', '6');
      textFacetToggle('Alpha');
      textFacetToggle('Alpha');
      bar().should('have.attr', 'data-total', '6');
      cy.wait(2800);
      bar().should('have.attr', 'data-total', '6');
    });

    it('card', function () {
      delayWhen((r) => r.kind === 'card' && r.filtered, 2000);
      load();
      openCard();
      textFacetToggle('Alpha');
      textFacetToggle('Alpha');
      stat('count').should('have.text', '5');
      cy.wait(2800);
      stat('count').should('have.text', '5');
    });

    it('breadcrumb', function () {
      delayWhen((r) => r.kind === 'step' && r.filtered, 2000);
      load();
      openCard();
      setRange('amount', -1, 1);
      cy.get('#investigation-breadcrumb .investigation-breadcrumb-shown').should('have.text', '4 shown');
      textFacetToggle('Alpha');
      textFacetToggle('Alpha');
      cy.get('#investigation-breadcrumb .investigation-breadcrumb-shown').should('have.text', '4 shown');
      cy.wait(2800);
      cy.get('#investigation-breadcrumb .investigation-breadcrumb-shown').should('have.text', '4 shown');
    });
  });

  describe('odd columns (stories 50, 51)', function () {
    it('an all-blank column: card says there are no numbers and that every cell is blank, no histogram, no crash', function () {
      cy.loadAndVisitProject([
        ['name', 'empty'],
        ['a', null],
        ['b', null],
        ['c', ''],
      ]);
      idle();
      cy.columnActionClick('empty', ['Facet', 'Investigate numbers']);
      card('empty').find('.investigation-card-empty').should('contain.text', 'No numeric values');
      card('empty').find('.investigation-card-breakdown').should('contain.text', '3 blank');
      card('empty').find('.investigation-histogram-bar').should('not.exist');
      card('empty').find('.investigation-card-error').should('not.exist');
      bar('empty').should('not.exist');
    });

    it('huge, scientific and non-finite values: the card loads and shows the huge range', function () {
      cy.loadAndVisitProject([
        ['v'],
        ['1e300'],
        ['-1e300'],
        ['1e5'],
        ['1.7976931348623157E308'],
        ['NaN'],
        ['Infinity'],
        ['abc'],
      ]);
      toNumber('v');
      cy.columnActionClick('v', ['Facet', 'Investigate numbers']);
      card('v').find('.investigation-histogram-bar').should('have.length', 20);
      card('v').find('.investigation-card-error').should('not.exist');
      stat('min', 'v').should('have.text', '-1e+300');
      stat('max', 'v').should('have.text', '1.7976931348623157e+308');
      bar('v').find('.health-bar-numeric').should('exist');
    });
  });

  describe('a renamed or removed column under an open card (story 52)', function () {
    const setUp = () => {
      load();
      bar().click();
      cy.get('.health-popover-row[data-class="blank"]').click();
      cy.get('td.investigation-highlight').should('have.length', 1);
      openCard();
      setRange('amount', -1, 1);
      cy.get('table.data-table tbody tr').should('have.length', 4);
      cy.get('#refine-tabs-facets .facets-container .facet-container').should('have.length', 1);
    };

    const assertCleared = () => {
      cy.get('#investigation-card-strip .investigation-card').should('not.exist');
      cy.get('td.investigation-highlight').should('not.exist');
      cy.get('#refine-tabs-facets .facets-container .facet-container').should('have.length', 0);
      cy.get('table.data-table tbody tr').should('have.length', 6);
      cy.get('#investigation-breadcrumb .investigation-breadcrumb-chip').should('not.exist');
    };

    it('renaming closes the card with a message, clears the highlight and the range facet', function () {
      setUp();
      cy.columnActionClick('amount', ['Edit column', 'Rename this column']);
      cy.waitForDialogPanel();
      cy.get('.dialog-container .dialog-body input').clear();
      cy.get('.dialog-container .dialog-body input').type('price');
      cy.get('.dialog-container .dialog-footer button').contains('OK').click();
      idle();
      cy.get('.investigation-notice[data-column="amount"]')
        .should('be.visible')
        .and('contain.text', 'amount')
        .and('contain.text', 'price');
      assertCleared();
    });

    it('removing closes the card with a message, clears the highlight and the range facet', function () {
      setUp();
      cy.columnActionClick('amount', ['Edit column', 'Remove this column']);
      idle();
      cy.get('.investigation-notice[data-column="amount"]').should('be.visible').and('contain.text', 'amount');
      assertCleared();
    });

    it('the message can be dismissed', function () {
      setUp();
      cy.columnActionClick('amount', ['Edit column', 'Remove this column']);
      idle();
      cy.get('.investigation-notice .investigation-notice-dismiss').click();
      cy.get('.investigation-notice').should('not.exist');
    });
  });
});
