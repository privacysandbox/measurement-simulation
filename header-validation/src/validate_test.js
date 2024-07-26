const test = require('node:test');
const assert = require('assert');
const {validateSource} = require('./validate_source');
const {validateTrigger} = require('./validate_trigger');
const {validateRedirect} = require('./validate_redirect');
const {sourceTestCases} = require('./source_tests');
const {triggerTestCases} = require('./trigger_tests');
const {redirectTestCases} = require('./redirect_tests');

test('Source Header Validation Tests', async (t) => {
    for (testCase of sourceTestCases) {
        await t.test(testCase.name, (t) => {
            // Setup
            flags = testCase.flags;
            json = testCase.json;

            // Test
            result = validateSource(json, flags);

            // Assert
            isValid = (result.errors.length === 0 && result.warnings.length === 0);
            assert.equal(/* actual */ isValid, /* expected */ testCase.result.valid);
            assert.deepEqual(/* actual */ result.errors.map(error => error.formattedError), /* expected */ testCase.result.errors);
            assert.deepEqual(/* actual */ result.warnings.map(warning => warning.formattedWarning), /* expected */ testCase.result.warnings);
        })
    }
})

test('Trigger Header Validation Tests', async (t) => {
    for (testCase of triggerTestCases) {
        await t.test(testCase.name, (t) => {
            // Setup
            flags = testCase.flags;
            json = testCase.json;

            // Test
            result = validateTrigger(json, flags);

            // Assert
            isValid = (result.errors.length === 0 && result.warnings.length === 0);
            assert.equal(/* actual */ isValid, /* expected */ testCase.result.valid);
            assert.deepEqual(/* actual */ result.errors.map(error => error.formattedError), /* expected */ testCase.result.errors);
            assert.deepEqual(/* actual */ result.warnings.map(warning => warning.formattedWarning), /* expected */ testCase.result.warnings);
        })
    }
})

test('Redirect Headers Validation Tests', async (t) => {
    for (testCase of redirectTestCases) {
        await t.test(testCase.name, (t) => {
            // Setup
            flags = testCase.flags;
            json = testCase.json;

            // Test
            result = validateRedirect(json, flags);

            // Assert
            isValid = (result.errors.length === 0);
            assert.equal(/* actual */ isValid, /* expected */ testCase.result.valid);
            assert.deepEqual(/* actual */ result.errors.map(error => error.formattedError), /* expected */ testCase.result.errors);
            assert.deepEqual(/* actual */ result.warnings.map(warning => warning.formattedWarning), /* expected */ testCase.result.warnings);
        })
    }
})