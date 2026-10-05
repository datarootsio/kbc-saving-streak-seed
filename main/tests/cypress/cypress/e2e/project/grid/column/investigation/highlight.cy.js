/**
 * Numeric investigation, ticket 04: clicking a class row in the health popover
 * highlights those cells in the grid (client-side, highlight only).
 * Fixture values are imported as strings and "amount" is converted with "To number".
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
const row = (cls) => cy.get(`.health-popover .health-popover-row[data-class="${cls}"]`);
const highlighted = () => cy.get('td.investigation-highlight');

const toNumber = (column) => {
  cy.get('body[ajax_in_progress="false"]');
  cy.columnActionClick(column, ['Edit cells', 'Common transforms', 'To number']);
  cy.get('body[ajax_in_progress="false"]');
  bar(column).should('exist');
};

const loadAmount = (rows = fixture) => {
  cy.loadAndVisitProject(rows);
  toNumber('amount');
};

const openPopover = () => {
  cy.get('.health-popover').then(($p) => {
    if (!$p.length) {
      bar('amount').click();
    }
  });
  cy.get('.health-popover').should('be.visible');
};

describe(__filename, function () {
  it('story 14: clicking Blank on amount highlights exactly the Beta cell and nothing else', function () {
    loadAmount();
    highlighted().should('not.exist');
    bar('amount').click();
    row('blank').click();
    highlighted().should('have.length', 1);
    highlighted().parent('tr').should('contain.text', 'Beta');
    highlighted().should('have.attr', 'data-highlight-class', 'blank');
    row('blank').should('have.attr', 'aria-pressed', 'true');
    row('numeric').should('have.attr', 'aria-pressed', 'false');
  });

  it('story 15: clicking Blank again clears the highlight', function () {
    loadAmount();
    bar('amount').click();
    row('blank').click();
    highlighted().should('have.length', 1);
    row('blank').click();
    highlighted().should('not.exist');
    row('blank').should('have.attr', 'aria-pressed', 'false');
  });

  it('story 16: selecting another class swaps the highlight', function () {
    loadAmount();
    bar('amount').click();
    row('blank').click();
    highlighted().parent('tr').should('contain.text', 'Beta');
    row('numeric').click();
    highlighted().should('have.length', 5);
    highlighted().parent('tr').should('not.contain.text', 'Beta');
    row('blank').should('have.attr', 'aria-pressed', 'false');
    row('numeric').should('have.attr', 'aria-pressed', 'true');
  });

  it('story 17: highlight is limited to the cells of the chosen column', function () {
    loadAmount();
    bar('amount').click();
    row('numeric').click();
    cy.get('td.investigation-highlight').each(($td) => {
      expect($td.text()).to.match(/^\s*-?[0-9.]+\s*(edit)?\s*$/i);
    });
  });

  it('story 18: pagination re-applies the highlight to newly rendered rows', function () {
    const rows = [['name', 'amount']];
    for (let i = 1; i <= 15; i++) {
      rows.push(['n' + i, i === 3 || i === 13 ? null : String(i)]);
    }
    loadAmount(rows);
    bar('amount').click();
    row('blank').click();
    highlighted().should('have.length', 1).parent('tr').should('contain.text', 'n3');
    cy.get('.viewpanel-paging').find('a').contains('next').click();
    cy.get('#viewpanel-paging-current-min-row').should('have.value', 11);
    highlighted().should('have.length', 1).parent('tr').should('contain.text', 'n13');
    cy.get('.viewpanel-paging').find('a').contains('previous').click();
    highlighted().should('have.length', 1).parent('tr').should('contain.text', 'n3');
  });

  it('story 18: the highlight survives an edit that re-renders the grid', function () {
    loadAmount();
    bar('amount').click();
    row('blank').click();
    cy.get('body[ajax_in_progress="false"]');
    cy.columnActionClick('name', ['Edit cells', 'Common transforms', 'To uppercase']);
    cy.get('body[ajax_in_progress="false"]');
    highlighted().should('have.length', 1).parent('tr').should('contain.text', 'BETA');
  });

  it('story 19: highlighted cells are distinguishable without colour (underline)', function () {
    loadAmount();
    bar('amount').click();
    row('blank').click();
    highlighted().find('.data-table-cell-content').should('have.css', 'text-decoration-line', 'underline');
  });

  it('highlight is only a highlight: no filtering, no facets, all rows stay', function () {
    loadAmount();
    bar('amount').click();
    row('blank').click();
    cy.get('#refine-tabs-facets .facet-container').should('not.exist');
    cy.get('.viewpanel-pagingcount').should('contain', '6');
    cy.get('#right-panel table.data-table tbody tr').should('have.length', 6);
  });

  it('story 23: class rows are keyboard operable (Enter and Space toggle)', function () {
    loadAmount();
    bar('amount').focus().type('{enter}');
    row('blank').should('have.attr', 'tabindex', '0').and('have.attr', 'role', 'button');
    row('blank').focus().type('{enter}');
    highlighted().should('have.length', 1);
    row('blank').should('have.attr', 'aria-pressed', 'true');
    row('blank').focus().type(' ');
    highlighted().should('not.exist');
  });

  it('closing the popover keeps the highlight; reopening shows the class as active', function () {
    loadAmount();
    bar('amount').click();
    row('blank').click();
    cy.get('body').type('{esc}');
    cy.get('.health-popover').should('not.exist');
    highlighted().should('have.length', 1);
    bar('amount').click();
    row('blank').should('have.attr', 'aria-pressed', 'true');
  });

  it('story 19: a reload clears the highlight', function () {
    loadAmount();
    bar('amount').click();
    row('blank').click();
    highlighted().should('have.length', 1);
    cy.reload();
    cy.get('body[ajax_in_progress="false"]');
    bar('amount').should('exist');
    highlighted().should('not.exist');
  });
});
