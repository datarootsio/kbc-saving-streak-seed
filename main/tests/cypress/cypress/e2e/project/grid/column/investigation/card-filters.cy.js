/**
 * Numeric investigation, ticket 08: the card follows the other facets and edits, ignores its own
 * range facet for its histogram and counts, and closing the card removes that facet.
 * Fixture values import as strings, so the numeric column is converted with "To number" first.
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

const groupFixture = [
  ['grp', 'amount'],
  ['g1', '0'],
  ['g1', '1'],
  ['g1', '-1'],
  ['g2', '5'],
  ['g2', '6'],
  ['g2', '7'],
];

const recordFixture = [
  ['key', 'amount'],
  ['A', '1'],
  [null, '3'],
  ['B', '10'],
];

const card = () => cy.get('#investigation-card-strip .investigation-card[data-column="amount"]');
const idle = () => cy.get('body[ajax_in_progress="false"]');

const openCard = (data = fixture) => {
  cy.loadAndVisitProject(data);
  idle();
  cy.columnActionClick('amount', ['Edit cells', 'Common transforms', 'To number']);
  idle();
  cy.columnActionClick('amount', ['Facet', 'Investigate numbers']);
  card().find('.investigation-histogram-bar').should('have.length', 20);
};

const setRange = (from, to) => {
  card().find('.investigation-range-from').invoke('val', from).trigger('change');
  idle();
  card().find('.investigation-range-to').invoke('val', to).trigger('change');
  idle();
};

const histogramSum = () =>
  card()
    .find('.investigation-histogram-bar')
    .then(($bars) => Cypress.$.makeArray($bars).reduce((a, b) => a + Number(b.getAttribute('data-count')), 0));

const stat = (name) => card().find('[data-stat="' + name + '"] dd');

const textFacetChoice = (column, label) => {
  cy.columnActionClick(column, ['Facet', 'Text facet']);
  cy.getFacetContainer(column)
    .contains('.facet-choice-label', new RegExp('^' + label + '$'))
    .click();
  idle();
};

describe(__filename, function () {
  it('a text facet name = Alpha shrinks the histogram and the counts to those rows', function () {
    openCard();
    stat('count').should('have.text', '5');
    textFacetChoice('name', 'Alpha');
    cy.get('table.data-table tbody tr').should('have.length', 1);
    stat('count').should('have.text', '1');
    stat('min').should('have.text', '0');
    stat('max').should('have.text', '0');
    histogramSum().should('equal', 1);
  });

  it('removing that facet restores the full histogram', function () {
    openCard();
    textFacetChoice('name', 'Alpha');
    stat('count').should('have.text', '1');
    cy.getFacetContainer('name').find('a[bind="removeButton"]').click();
    stat('count').should('have.text', '5');
    histogramSum().should('equal', 5);
  });

  it('dragging the card range does not shrink its own histogram or counts', function () {
    openCard();
    setRange(0, 1);
    cy.get('table.data-table tbody tr').should('have.length', 3);
    stat('count').should('have.text', '5');
    histogramSum().should('equal', 5);
    card().find('.investigation-kept').should('have.text', 'Keeps 3 of 6 rows (50%)');
  });

  it('with a text facet and a range the histogram follows the text facet only', function () {
    openCard(groupFixture);
    textFacetChoice('grp', 'g1');
    setRange(0, 1);
    cy.get('table.data-table tbody tr').should('have.length', 2);
    stat('count').should('have.text', '3');
    stat('min').should('have.text', '-1');
    stat('max').should('have.text', '1');
    histogramSum().should('equal', 3);
    card().find('.investigation-kept').should('have.text', 'Keeps 2 of 6 rows (33%)');
  });

  it('the card refreshes after a cell edit', function () {
    openCard();
    stat('count').should('have.text', '5');
    cy.columnActionClick('amount', ['Edit cells', 'Transform']);
    cy.typeExpression('value + 10');
    cy.get('.dialog-footer .button-primary').click();
    idle();
    stat('min').should('have.text', '9');
    stat('max').should('have.text', '12.5');
  });

  it('the card refreshes on undo', function () {
    openCard();
    cy.columnActionClick('amount', ['Edit cells', 'Transform']);
    cy.typeExpression('value + 10');
    cy.get('.dialog-footer .button-primary').click();
    idle();
    stat('min').should('have.text', '9');
    cy.get('.notification-action a').contains('Undo').click();
    idle();
    stat('min').should('have.text', '-1');
  });

  it('closing the card removes its range facet and the grid returns', function () {
    openCard();
    setRange(0, 1);
    cy.get('table.data-table tbody tr').should('have.length', 3);
    card().find('.investigation-card-close').click();
    cy.get('#investigation-card-strip .investigation-card').should('not.exist');
    cy.get('table.data-table tbody tr').should('have.length', 6);
    cy.wait(600); // a removed facet's element is only detached after 300ms
    cy.get('#refine-tabs-facets .facets-container .facet-container').should('have.length', 0);
  });

  it('closing the card keeps a range facet the user made in the left panel, without its selection', function () {
    cy.loadAndVisitProject(fixture);
    idle();
    cy.columnActionClick('amount', ['Edit cells', 'Common transforms', 'To number']);
    idle();
    cy.columnActionClick('amount', ['Facet', 'Numeric facet']);
    idle();
    cy.columnActionClick('amount', ['Facet', 'Investigate numbers']);
    card().find('.investigation-histogram-bar').should('have.length', 20);
    setRange(0, 1);
    cy.get('table.data-table tbody tr').should('have.length', 3);
    card().find('.investigation-card-close').click();
    cy.get('table.data-table tbody tr').should('have.length', 6);
    cy.wait(600);
    cy.get('#refine-tabs-facets .facets-container .facet-container').should('have.length', 1);
  });

  describe('record mode', function () {
    const toRecords = () => {
      cy.get('span[bind="modeSelectors"]').contains('records').click();
      idle();
    };

    it('a facet on another column counts the cells of whole records', function () {
      openCard(recordFixture);
      toRecords();
      stat('count').should('have.text', '3');
      textFacetChoice('key', 'A');
      cy.get('table.data-table tbody tr').should('have.length', 2);
      stat('count').should('have.text', '2');
      stat('max').should('have.text', '3');
      histogramSum().should('equal', 2);
    });

    it('the card range keeps whole records and still does not shrink its own histogram', function () {
      openCard(recordFixture);
      toRecords();
      setRange(0, 2);
      cy.get('table.data-table tbody tr').should('have.length', 2);
      stat('count').should('have.text', '3');
      histogramSum().should('equal', 3);
      card().find('.investigation-kept').should('have.text', 'Keeps 2 of 3 rows (67%)');
    });
  });
});
