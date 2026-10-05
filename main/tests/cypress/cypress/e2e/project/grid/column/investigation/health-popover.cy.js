/**
 * Numeric investigation, ticket 03: health popover opened from the health bar.
 * Fixture values are imported as strings, so "amount" starts as text and is
 * converted with "To number" to get a numeric column (and so a bar).
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

const bar = (column) => cy.get(`.health-bar[data-column="${column}"]`);
const popover = () => cy.get('.health-popover');
const row = (cls) => cy.get(`.health-popover .health-popover-row[data-class="${cls}"]`);

const toNumber = (column) => {
  cy.get('body[ajax_in_progress="false"]');
  cy.columnActionClick(column, ['Edit cells', 'Common transforms', 'To number']);
  cy.get('body[ajax_in_progress="false"]');
  bar(column).should('exist');
};

const loadAmount = () => {
  cy.loadAndVisitProject(fixture);
  toNumber('amount');
};

describe(__filename, function () {
  it('story 9: no popover until the bar is activated, and clicking the bar opens it', function () {
    loadAmount();
    popover().should('not.exist');
    bar('amount').click();
    popover().should('be.visible').and('have.attr', 'data-column', 'amount');
    popover().should('contain.text', 'amount');
  });

  it('stories 10, 12: total and one row per class with count, percentage and explanation', function () {
    loadAmount();
    bar('amount').click();
    popover().find('.health-popover-total').should('contain.text', '6');
    cy.get('.health-popover .health-popover-row').should('have.length', 4);
    row('numeric').find('.health-popover-count').should('have.text', '5');
    row('numeric').find('.health-popover-pct').should('have.text', '83%');
    row('blank').find('.health-popover-count').should('have.text', '1');
    row('blank').find('.health-popover-pct').should('have.text', '17%');
    ['numeric', 'blank', 'wrongType', 'error'].forEach((cls) => {
      row(cls)
        .find('.health-popover-name')
        .invoke('text')
        .should('match', /\S/)
        .and('not.contain', 'core-investigation/');
      row(cls)
        .find('.health-popover-explain')
        .invoke('text')
        .should('match', /\S/)
        .and('not.contain', 'core-investigation/');
    });
  });

  it('story 11: zero-count classes are shown greyed with 0 and 0%', function () {
    loadAmount();
    bar('amount').click();
    row('wrongType').should('have.class', 'is-zero');
    row('error').should('have.class', 'is-zero');
    row('wrongType').find('.health-popover-count').should('have.text', '0');
    row('error').find('.health-popover-pct').should('have.text', '0%');
    row('numeric').should('not.have.class', 'is-zero');
    row('blank').should('not.have.class', 'is-zero');
  });

  it('story 13: percentages always total 100 (three equal classes show 34/33/33)', function () {
    cy.loadAndVisitProject([['v'], ['1'], ['2'], ['a'], ['b'], [null], [null]]);
    toNumber('v');
    bar('v').click();
    row('numeric').find('.health-popover-pct').should('have.text', '34%');
    row('blank').find('.health-popover-pct').should('have.text', '33%');
    row('wrongType').find('.health-popover-pct').should('have.text', '33%');
    row('error').find('.health-popover-pct').should('have.text', '0%');
  });

  it('story 23: the bar is keyboard focusable, Enter and Space open, Escape closes and returns focus', function () {
    loadAmount();
    bar('amount').should('have.attr', 'tabindex', '0').and('have.attr', 'aria-expanded', 'false');
    bar('amount').focus().type('{enter}');
    popover().should('be.visible');
    bar('amount').should('have.attr', 'aria-expanded', 'true');
    cy.get('body').type('{esc}');
    popover().should('not.exist');
    bar('amount').should('have.attr', 'aria-expanded', 'false');
    cy.focused().should('have.class', 'health-bar');

    cy.focused().type(' ');
    popover().should('be.visible');
    cy.focused().type('{esc}');
    popover().should('not.exist');
    cy.focused().should('have.class', 'health-bar');
  });

  it('story 23: Escape returns focus to the bar even when focus had moved off it', function () {
    loadAmount();
    bar('amount').click();
    popover().find('.health-popover-title').click();
    cy.get('.health-bar:focus').should('not.exist');
    cy.get('body').type('{esc}');
    popover().should('not.exist');
    cy.focused().should('have.class', 'health-bar');
  });

  it('closes on an outside click, and not on a click inside the popover', function () {
    loadAmount();
    bar('amount').click();
    popover().find('.health-popover-title').click();
    popover().should('be.visible');
    cy.get('#summary-bar').click();
    popover().should('not.exist');
  });

  it('clicking the bar again toggles it closed, and only one popover is open at a time', function () {
    cy.loadAndVisitProject([
      ['a', 'b'],
      ['1', '2'],
      [null, '3'],
    ]);
    toNumber('a');
    toNumber('b');
    bar('a').click();
    popover().should('have.attr', 'data-column', 'a');
    bar('b').click();
    cy.get('.health-popover').should('have.length', 1).and('have.attr', 'data-column', 'b');
    bar('b').click();
    popover().should('not.exist');
  });
});
