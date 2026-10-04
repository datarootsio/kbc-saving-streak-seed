const amountProject = [
  ['name', 'amount', 'score'],
  ['Alpha', '0', '10'],
  ['alpha', '1', '20'],
  ['ALPHA', '-1', '30'],
  ['Beta', '', '40'],
  ['Gamma', '2.5', '50'],
  ['Delta', '0', '60'],
];

// Imported cells are text: convert to numbers first, as health-bar.cy.js does.
const toNumber = (column) => {
  cy.get('body[ajax_in_progress="false"]');
  cy.columnActionClick(column, ['Edit cells', 'Common transforms', 'To number']);
  cy.get('body[ajax_in_progress="false"]');
};

const cardFor = (columnName) => cy.get('#investigation-card-strip .investigation-card[data-column="' + columnName + '"]');

describe(__filename, function () {
  it('Investigate numbers on amount shows range and stats (stories 20, 21, 24, 26)', function () {
    cy.loadAndVisitProject(amountProject);
    toNumber('amount');
    cy.columnActionClick('amount', ['Facet', 'Investigate numbers']);
    cardFor('amount').should('be.visible');
    cardFor('amount').find('.investigation-card-title').should('contain', 'amount');
    cardFor('amount').find('.investigation-card-range').should('contain', '-1 to 2.5');
    cardFor('amount').find('[data-stat="count"]').should('contain', '5');
    cardFor('amount').find('[data-stat="min"]').should('contain', '-1');
    cardFor('amount').find('[data-stat="max"]').should('contain', '2.5');
    cardFor('amount').find('[data-stat="mean"]').should('contain', '0.5');
    cardFor('amount').find('[data-stat="median"]').should('contain', '0');
  });

  it('A text column shows "no numeric values" instead of stats (story 22)', function () {
    cy.loadAndVisitProject(amountProject);
    cy.columnActionClick('name', ['Facet', 'Investigate numbers']);
    cardFor('name').find('.investigation-card-empty').should('contain', 'No numeric values');
    cardFor('name').find('[data-stat="count"]').should('not.exist');
  });

  it('Several cards are independent and each has a close button (story 36)', function () {
    cy.loadAndVisitProject(amountProject);
    toNumber('amount');
    toNumber('score');
    cy.columnActionClick('amount', ['Facet', 'Investigate numbers']);
    cardFor('amount').should('be.visible');
    cy.columnActionClick('score', ['Facet', 'Investigate numbers']);
    cardFor('score').should('be.visible');
    cardFor('amount').should('be.visible');

    cardFor('amount').find('.investigation-card-close').click();
    cardFor('amount').should('not.exist');
    cardFor('score').should('be.visible');
  });
});
