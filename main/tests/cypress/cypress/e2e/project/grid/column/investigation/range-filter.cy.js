/**
 * Numeric investigation, ticket 07: the card's range slider filters the grid through a
 * real core/range facet, shown (and editable) in the left panel.
 * Fixture values import as strings, so "amount" is converted with "To number" first.
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

const card = () => cy.get('#investigation-card-strip .investigation-card[data-column="amount"]');

const idle = () => cy.get('body[ajax_in_progress="false"]');

const openCard = () => {
  cy.loadAndVisitProject(fixture);
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

const amounts = ($rows) =>
  Cypress.$.makeArray($rows).map((tr) => Cypress.$(tr).find('td').eq(4).text().trim().replace(/edit$/, ''));

describe(__filename, function () {
  it('range 0..1 leaves rows 0, 1, 0 and the card reads "Keeps 3 of 6 rows (50%)"', function () {
    openCard();
    setRange(0, 1);
    cy.get('table.data-table tbody tr').should('have.length', 3);
    cy.get('table.data-table tbody tr').should(($rows) => {
      expect(amounts($rows)).to.deep.equal(['0', '1', '0']);
    });
    card().find('.investigation-kept').should('have.text', 'Keeps 3 of 6 rows (50%)');
  });

  it('bins outside the range are greyed and bins inside are not', function () {
    openCard();
    setRange(0, 1);
    card()
      .find('.investigation-histogram-bar')
      .should(($bars) => {
        const out = Cypress.$.makeArray($bars).map((b) => b.classList.contains('investigation-histogram-bar-out'));
        expect(out[0], 'bin of -1').to.equal(true);
        expect(out[5], 'bin of 0').to.equal(false);
        expect(out[11], 'bin of 1').to.equal(false);
        expect(out[19], 'bin of 2.5').to.equal(true);
      });
  });

  it('the histogram itself does not shrink while the range is set', function () {
    openCard();
    setRange(0, 1);
    card()
      .find('.investigation-histogram-bar')
      .should(($bars) => {
        const sum = Cypress.$.makeArray($bars).reduce((a, b) => a + Number(b.getAttribute('data-count')), 0);
        expect(sum).to.equal(5);
      });
  });

  it('the left panel shows the equivalent range facet', function () {
    openCard();
    setRange(0, 1);
    cy.get('#refine-tabs-facets .facets-container .facet-container').should('have.length', 1).and('contain', 'amount');
  });

  it('editing the left-panel facet updates the card (include blanks)', function () {
    openCard();
    setRange(0, 1);
    cy.getNumericFacetContainer('amount')
      .find('input[type="checkbox"]')
      .filter('[bind="blankCheck"], [id*="blank"]')
      .first()
      .check({ force: true });
    card().find('.investigation-kept').should('have.text', 'Keeps 4 of 6 rows (67%)');
  });

  it('removing the left-panel facet clears the card selection and restores the grid', function () {
    openCard();
    setRange(0, 1);
    cy.getNumericFacetContainer('amount').find('a[bind="removeButton"]').click();
    cy.get('table.data-table tbody tr').should('have.length', 6);
    card().find('.investigation-kept').should('not.exist');
    card().find('.investigation-range-reset').should('not.exist');
  });

  it('Reset restores all rows', function () {
    openCard();
    setRange(0, 1);
    card().find('.investigation-range-reset').click();
    cy.get('table.data-table tbody tr').should('have.length', 6);
    card().find('.investigation-kept').should('not.exist');
    card().find('.investigation-histogram-bar-out').should('not.exist');
  });

  it('an existing range facet on the column is adopted, not duplicated', function () {
    cy.loadAndVisitProject(fixture);
    idle();
    cy.columnActionClick('amount', ['Edit cells', 'Common transforms', 'To number']);
    idle();
    cy.columnActionClick('amount', ['Facet', 'Numeric facet']);
    idle();
    cy.columnActionClick('amount', ['Facet', 'Investigate numbers']);
    card().find('.investigation-histogram-bar').should('have.length', 20);
    setRange(0, 1);
    cy.get('#refine-tabs-facets .facets-container .facet-container').should('have.length', 1);
    cy.get('table.data-table tbody tr').should('have.length', 3);
    card().find('.investigation-kept').should('have.text', 'Keeps 3 of 6 rows (50%)');
  });

  it('combines with another facet by AND', function () {
    openCard();
    setRange(0, 1);
    cy.columnActionClick('name', ['Facet', 'Text facet']);
    cy.getFacetContainer('name')
      .contains('.facet-choice-label', /^Alpha$/)
      .click();
    cy.get('table.data-table tbody tr').should('have.length', 1);
    card().find('.investigation-kept').should('have.text', 'Keeps 1 of 6 rows (17%)');
  });
});
