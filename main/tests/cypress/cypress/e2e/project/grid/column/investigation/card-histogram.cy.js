const amountProject = [
  ['name', 'amount'],
  ['Alpha', '0'],
  ['alpha', '1'],
  ['ALPHA', '-1'],
  ['Beta', ''],
  ['Gamma', '2.5'],
  ['Delta', '0'],
];

const cardFor = (columnName) => cy.get('#investigation-card-strip .investigation-card[data-column="' + columnName + '"]');

const barCounts = ($bars) => Cypress.$.makeArray($bars).map((bar) => Number(bar.getAttribute('data-count')));

describe(__filename, function () {
  it('Amount histogram has 20 bars summing to 5 with -1, 0, 0, 1, 2.5 in the expected bins (stories 25, 33)', function () {
    cy.loadAndVisitProject(amountProject);
    cy.columnActionClick('amount', ['Facet', 'Investigate numbers']);
    cardFor('amount').find('.investigation-histogram-bar').should('have.length', 20);
    cardFor('amount')
      .find('.investigation-histogram-bar')
      .should(($bars) => {
        const counts = barCounts($bars);
        expect(counts.reduce((a, b) => a + b, 0)).to.equal(5);
        expect(counts[0]).to.equal(1); // -1
        expect(counts[5]).to.equal(2); // 0, 0
        expect(counts[11]).to.equal(1); // 1
        expect(counts[19]).to.equal(1); // 2.5, the max, falls in the last bin
      });
  });

  it('Bar heights are proportional to the counts', function () {
    cy.loadAndVisitProject(amountProject);
    cy.columnActionClick('amount', ['Facet', 'Investigate numbers']);
    cardFor('amount').find('.investigation-histogram-bar[data-count="2"] .investigation-histogram-fill').should('have.attr', 'style').and('include', 'height: 100%');
    cardFor('amount').find('.investigation-histogram-bar[data-count="1"] .investigation-histogram-fill').first().should('have.attr', 'style').and('include', 'height: 50%');
  });

  it('A column with one distinct value renders one bin of 3 (story 32)', function () {
    cy.loadAndVisitProject([['v'], ['7'], ['7'], ['7']]);
    cy.columnActionClick('v', ['Facet', 'Investigate numbers']);
    cardFor('v').find('.investigation-histogram-bar').should('have.length', 1);
    cardFor('v').find('.investigation-histogram-bar').should('have.attr', 'data-count', '3');
    cardFor('v').find('.investigation-histogram-single').should('contain', 'All numeric values are the same');
  });

  it('A text column has no histogram (story 22)', function () {
    cy.loadAndVisitProject(amountProject);
    cy.columnActionClick('name', ['Facet', 'Investigate numbers']);
    cardFor('name').find('.investigation-card-empty').should('contain', 'No numeric values');
    cardFor('name').find('.investigation-histogram-bar').should('not.exist');
  });

  it('A project with no rows shows the empty-project message, not an error (story 35)', function () {
    cy.loadAndVisitProject([['v', 'w']]);
    cy.columnActionClick('v', ['Facet', 'Investigate numbers']);
    cardFor('v').find('.investigation-card-empty-project').should('contain', 'Nothing to investigate');
    cardFor('v').find('.investigation-card-error').should('not.exist');
    cardFor('v').find('.investigation-histogram-bar').should('not.exist');
  });
});
