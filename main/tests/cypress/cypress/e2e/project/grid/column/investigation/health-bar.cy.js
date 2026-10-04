/**
 * Numeric investigation, ticket 02: health bar in the column header.
 * Fixture values are imported as strings, so the "amount" column starts as text
 * and is converted with "To number" where a numeric column is needed.
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
const segment = (column, cls) => cy.get(`.health-bar[data-column="${column}"] .health-bar-segment[data-class="${cls}"]`);

const toNumber = (column) => {
  cy.get('body[ajax_in_progress="false"]');
  cy.columnActionClick(column, ['Edit cells', 'Common transforms', 'To number']);
  cy.get('body[ajax_in_progress="false"]');
};

describe(__filename, function () {
  it('story 5: a column with no numeric value has no bar', function () {
    cy.loadAndVisitProject(fixture);
    cy.get('.data-table th[title="amount"]').should('exist');
    cy.get('.health-bar').should('not.exist');
  });

  it('stories 1, 2: after converting to numbers, amount shows numeric and blank segments, name none', function () {
    cy.loadAndVisitProject(fixture);
    toNumber('amount');
    segment('amount', 'numeric').should('have.attr', 'data-count', '5');
    segment('amount', 'blank').should('have.attr', 'data-count', '1');
    segment('amount', 'wrongType').should('not.exist');
    bar('amount').should('have.attr', 'data-total', '6');
    segment('amount', 'numeric').should(($s) => {
      expect(parseFloat($s.attr('data-width-pct'))).to.be.closeTo(83.33, 0.01);
    });
    cy.get('.health-bar[data-column="name"]').should('not.exist');
  });

  it('story 3: a fully numeric column is one full-width numeric segment', function () {
    cy.loadAndVisitProject([['n'], ['1'], ['2'], ['3']]);
    toNumber('n');
    segment('n', 'numeric').should('have.attr', 'data-width-pct', '100.00');
    cy.get('.health-bar[data-column="n"] .health-bar-segment').should('have.length', 1);
  });

  it('story 4: a rare non-numeric class still gets a visible sliver', function () {
    const rows = [['v']];
    for (let i = 0; i < 199; i++) {
      rows.push([String(i)]);
    }
    rows.push(['oops']);
    cy.loadAndVisitProject(rows);
    toNumber('v');
    segment('v', 'wrongType').should('have.attr', 'data-count', '1');
    segment('v', 'wrongType').should(($s) => {
      expect(parseFloat($s.attr('data-width-pct'))).to.be.at.least(2);
      expect($s.width()).to.be.greaterThan(0);
    });
  });

  it('story 7: the bar refreshes after an edit, undo and redo without reload', function () {
    cy.loadAndVisitProject(fixture);
    toNumber('amount');
    segment('amount', 'blank').should('have.attr', 'data-count', '1');

    cy.columnActionClick('amount', ['Edit cells', 'Transform']);
    cy.typeExpression('if(isBlank(value), 0, value)');
    cy.get('.dialog-footer button').contains('OK').click();
    segment('amount', 'numeric').should('have.attr', 'data-count', '6');
    segment('amount', 'blank').should('not.exist');

    cy.get('a[href="#refine-tabs-history"]').click();
    cy.get('.history-panel-body .history-entry').contains('Create project').click();
    cy.get('.health-bar[data-column="amount"]').should('not.exist');

    // redo everything: the last entry is the blank-to-zero transform
    cy.get('.history-panel-body .history-entry').last().click();
    segment('amount', 'numeric').should('have.attr', 'data-count', '6');
  });

  it('story 8: applying a facet makes the bar match the filtered rows', function () {
    cy.loadAndVisitProject(fixture);
    toNumber('amount');
    segment('amount', 'numeric').should('have.attr', 'data-count', '5');

    cy.columnActionClick('name', ['Text filter']);
    cy.get('.input-container > input').should('be.visible').type('alpha');
    cy.get('#summary-bar > span').should('have.text', '3 matching rows (6 total)');
    segment('amount', 'numeric').should('have.attr', 'data-count', '3');
    bar('amount').should('have.attr', 'data-total', '3');
    segment('amount', 'blank').should('not.exist');
  });
});
