/**
 * Numeric investigation, ticket 09: toggles that keep blank, wrong-type and error rows
 * visible under the card's range selection (range facet selectBlank / selectNonNumeric / selectError).
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

const withText = fixture.concat([['Zeta', 'oops']]);

const card = () => cy.get('#investigation-card-strip .investigation-card[data-column="amount"]');
const toggle = (kind) => card().find(`.investigation-toggle[data-kind="${kind}"]`);
const idle = () => cy.get('body[ajax_in_progress="false"]');

const openCard = (rows = fixture) => {
  cy.loadAndVisitProject(rows);
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

const names = ($rows) => Cypress.$.makeArray($rows).map((tr) => Cypress.$(tr).find('td').eq(3).text().trim().replace(/edit$/, ''));

describe(__filename, function () {
  it('shows three toggles with the counts from the health endpoint, all off by default', function () {
    openCard(withText);
    toggle('blank').find('.investigation-toggle-count').should('have.text', '1');
    toggle('wrongType').find('.investigation-toggle-count').should('have.text', '1');
    toggle('error').find('.investigation-toggle-count').should('have.text', '0');
    card().find('.investigation-toggle input[type="checkbox"]').should('have.length', 3).and('not.be.checked');
  });

  it('range 0..1 with blank off drops Beta; turning blank on keeps it ("Keeps 4 of 6 rows (67%)")', function () {
    openCard();
    setRange(0, 1);
    cy.get('table.data-table tbody tr').should('have.length', 3);
    toggle('blank').find('input').check();
    idle();
    cy.get('table.data-table tbody tr').should('have.length', 4);
    cy.get('table.data-table tbody tr').should(($rows) => {
      expect(names($rows)).to.include('Beta');
    });
    card().find('.investigation-kept').should('have.text', 'Keeps 4 of 6 rows (67%)');
    toggle('blank').find('input').uncheck();
    idle();
    cy.get('table.data-table tbody tr').should('have.length', 3);
    card().find('.investigation-kept').should('have.text', 'Keeps 3 of 6 rows (50%)');
  });

  it('the wrong-type toggle keeps the text row independently of blank', function () {
    openCard(withText);
    setRange(0, 1);
    cy.get('table.data-table tbody tr').should('have.length', 3);
    toggle('wrongType').find('input').check();
    idle();
    cy.get('table.data-table tbody tr').should('have.length', 4);
    cy.get('table.data-table tbody tr').should(($rows) => {
      expect(names($rows)).to.include('Zeta');
      expect(names($rows)).to.not.include('Beta');
    });
  });

  it('a toggle chosen before any range is used when the range is set', function () {
    openCard();
    toggle('blank').find('input').check();
    setRange(0, 1);
    cy.get('table.data-table tbody tr').should('have.length', 4);
  });

  it('ticking blank in the left-panel facet updates the toggle, and unticking it clears it', function () {
    openCard();
    setRange(0, 1);
    toggle('blank').find('input').should('not.be.checked');
    cy.getNumericFacetContainer('amount').find('input[type="checkbox"]').filter('[id*="blank"]').first().check({ force: true });
    toggle('blank').find('input').should('be.checked');
    cy.getNumericFacetContainer('amount').find('input[type="checkbox"]').filter('[id*="blank"]').first().uncheck({ force: true });
    toggle('blank').find('input').should('not.be.checked');
  });

  it('toggling on the card ticks the left-panel facet checkbox', function () {
    openCard();
    setRange(0, 1);
    toggle('blank').find('input').check();
    cy.getNumericFacetContainer('amount').find('input[type="checkbox"]').filter('[id*="blank"]').first().should('be.checked');
  });

  it('is keyboard accessible: Space on the focused checkbox toggles it and it has an accessible name', function () {
    openCard();
    setRange(0, 1);
    toggle('blank').find('input').focus().type(' ');
    idle();
    toggle('blank').find('input').should('be.checked');
    cy.get('table.data-table tbody tr').should('have.length', 4);
    toggle('blank').should('have.prop', 'tagName', 'LABEL').and('contain.text', 'Blank');
  });
});
