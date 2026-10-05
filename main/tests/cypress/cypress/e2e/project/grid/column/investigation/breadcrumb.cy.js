/**
 * Numeric investigation, ticket 10: breadcrumb above the cards.
 * "N rows -> amount 0 to 5 -> k -> qty ... -> j -> M shown". Step counts ignore non-numeric facets;
 * only the last number ("shown") follows them. Fixture values import as strings, so the numeric
 * columns are converted with "To number" first.
 */
const fixture = [
  ['name', 'amount', 'qty'],
  ['Alpha', '0', '1'],
  ['alpha', '1', '2'],
  ['ALPHA', '3', '9'],
  ['Beta', '4', '3'],
  ['Gamma', '8', '2'],
  ['Delta', '9', '8'],
];

const breadcrumb = () => cy.get('#investigation-breadcrumb');
const chips = () => breadcrumb().find('.investigation-breadcrumb-chip');
const card = (col) => cy.get(`#investigation-card-strip .investigation-card[data-column="${col}"]`);
const idle = () => cy.get('body[ajax_in_progress="false"]');

const setRange = (col, from, to) => {
  card(col).find('.investigation-range-from').invoke('val', from).trigger('change');
  idle();
  card(col).find('.investigation-range-to').invoke('val', to).trigger('change');
  idle();
};

const open = (col) => {
  cy.columnActionClick(col, ['Facet', 'Investigate numbers']);
  card(col).find('.investigation-histogram-bar').should('have.length', 20);
};

// amount 0..5 keeps 4 of 6 rows (0,1,3,4); then qty 1..3 keeps 3 of them (rows with qty 1,2,3)
const twoFilters = () => {
  cy.loadAndVisitProject(fixture);
  idle();
  ['amount', 'qty'].forEach((c) => {
    cy.columnActionClick(c, ['Edit cells', 'Common transforms', 'To number']);
    idle();
  });
  open('amount');
  open('qty');
  setRange('amount', 0, 5);
  setRange('qty', 1, 3);
};

describe(__filename, function () {
  it('is hidden when there are no numeric filters', function () {
    cy.loadAndVisitProject(fixture);
    idle();
    cy.columnActionClick('amount', ['Edit cells', 'Common transforms', 'To number']);
    idle();
    open('amount');
    breadcrumb().should('not.be.visible');
    breadcrumb().find('.investigation-breadcrumb-chip').should('not.exist');
  });

  it('two filters show two chips with decreasing counts, ordered by creation', function () {
    twoFilters();
    breadcrumb().find('.investigation-breadcrumb-total').should('have.text', '6 rows');
    chips().should('have.length', 2);
    chips().eq(0).should('have.attr', 'data-column', 'amount').and('contain.text', 'amount 0 to 5');
    chips().eq(1).should('have.attr', 'data-column', 'qty').and('contain.text', 'qty 1 to 3');
    breadcrumb().find('.investigation-breadcrumb-count').then(($c) => {
      expect(Cypress.$.makeArray($c).map((e) => e.textContent)).to.deep.equal(['4', '3']);
    });
    breadcrumb().find('.investigation-breadcrumb-shown').should('have.text', '3 shown');
    cy.get('table.data-table tbody tr').should('have.length', 3);
  });

  it('chips follow the order the filters were created, not the card order', function () {
    cy.loadAndVisitProject(fixture);
    idle();
    ['amount', 'qty'].forEach((c) => {
      cy.columnActionClick(c, ['Edit cells', 'Common transforms', 'To number']);
      idle();
    });
    open('amount');
    open('qty');
    setRange('qty', 1, 3);
    setRange('amount', 0, 5);
    chips().eq(0).should('have.attr', 'data-column', 'qty');
    chips().eq(1).should('have.attr', 'data-column', 'amount');
  });

  it('removing chip 1 keeps chip 2 and recomputes the counts', function () {
    twoFilters();
    chips().should('have.length', 2);
    chips().eq(0).find('.investigation-breadcrumb-remove').click();
    idle();
    chips().should('have.length', 1);
    chips().eq(0).should('have.attr', 'data-column', 'qty');
    // qty 1..3 alone keeps rows with qty 1, 2, 3, 2 = 4 rows
    breadcrumb().find('.investigation-breadcrumb-count').should('have.length', 1).and('have.text', '4');
    breadcrumb().find('.investigation-breadcrumb-shown').should('have.text', '4 shown');
    card('amount').find('.investigation-kept').should('not.exist');
    card('qty').find('.investigation-kept').should('exist');
  });

  it('removing the last chip hides the breadcrumb', function () {
    twoFilters();
    chips().eq(0).find('.investigation-breadcrumb-remove').click();
    idle();
    chips().eq(0).find('.investigation-breadcrumb-remove').click();
    idle();
    chips().should('not.exist');
    breadcrumb().should('not.be.visible');
    cy.get('table.data-table tbody tr').should('have.length', 6);
  });

  it('a text facet changes only the final shown count, not the chips or step counts', function () {
    twoFilters();
    breadcrumb().find('.investigation-breadcrumb-shown').should('have.text', '3 shown');
    cy.columnActionClick('name', ['Facet', 'Text facet']);
    cy.getFacetContainer('name').contains('.facet-choice-label', /^Beta$/).click();
    idle();
    breadcrumb().find('.investigation-breadcrumb-shown').should('have.text', '1 shown');
    chips().should('have.length', 2);
    breadcrumb().find('.investigation-breadcrumb-count').then(($c) => {
      expect(Cypress.$.makeArray($c).map((e) => e.textContent)).to.deep.equal(['4', '3']);
    });
  });

  it('a filter removed in the left panel disappears from the breadcrumb', function () {
    twoFilters();
    cy.getNumericFacetContainer('qty').find('a[bind="removeButton"]').click();
    idle();
    chips().should('have.length', 1).and('have.attr', 'data-column', 'amount');
  });
});
