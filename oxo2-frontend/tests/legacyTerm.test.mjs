import assert from 'node:assert/strict';
import { test } from 'node:test';
import { legacyTermToCurie } from '../src/util/legacyTerm.ts';

test('an OBO short form becomes the CURIE search understands', () => {
    assert.equal(legacyTermToCurie('EFO_0007837'), 'EFO:0007837');
    assert.equal(legacyTermToCurie('MONDO_0005180'), 'MONDO:0005180');
});

test('only the first underscore is the prefix boundary', () => {
    assert.equal(legacyTermToCurie('NCBITaxon_9606'), 'NCBITaxon:9606');
    assert.equal(legacyTermToCurie('PREFIX_LOCAL_PART'), 'PREFIX:LOCAL_PART');
});

test('a CURIE or an IRI passes through untouched', () => {
    assert.equal(legacyTermToCurie('EFO:0007837'), 'EFO:0007837');
    assert.equal(legacyTermToCurie('http://www.ebi.ac.uk/efo/EFO_0007837'), 'http://www.ebi.ac.uk/efo/EFO_0007837');
});

test('surrounding whitespace is dropped before classifying', () => {
    assert.equal(legacyTermToCurie('  EFO_0007837 '), 'EFO:0007837');
});

test('ids with nothing to split pass through for the label search', () => {
    assert.equal(legacyTermToCurie('cataract'), 'cataract');
    assert.equal(legacyTermToCurie('_leading'), '_leading');
    assert.equal(legacyTermToCurie('trailing_'), 'trailing_');
});

test('a blank id yields null', () => {
    assert.equal(legacyTermToCurie(''), null);
    assert.equal(legacyTermToCurie('   '), null);
    assert.equal(legacyTermToCurie(undefined), null);
});
