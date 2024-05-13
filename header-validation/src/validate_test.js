const test = require('node:test')
const assert = require('assert')
const {validateSource} = require('./validate_source')
const {sourceTestCases} = require('./source_tests')
const {validateTrigger} = require('./validate_trigger')
const {triggerTestCases} = require('./trigger_tests')

test('Source Header Validation Tests', async (t) => {
    for (testCase of sourceTestCases) {
        await t.test(testCase.name, (t) => {
            // Setup
            type = testCase.type
            flags = testCase.flags
            json = testCase.json

            // Test
            result = validateSource(json, type, flags)

            // Assert
            isValid = (result.errors.length === 0 && result.warnings.length === 0)
            assert.equal(/* actual */ isValid, /* expected */ testCase.result.valid)
            assert.deepEqual(/* actual */ result.errors.map(error => error.formattedError), /* expected */ testCase.result.errors)
            assert.deepEqual(/* actual */ result.warnings, /* expected */ testCase.result.warnings)
        })
    }
})

test('Trigger Header Validation Tests', async (t) => {
    for (testCase of triggerTestCases) {
        await t.test(testCase.name, (t) => {
            // Setup
            type = testCase.type
            flags = testCase.flags
            json = testCase.json

            // Test
            result = validateTrigger(json, type, flags)

            // Assert
            isValid = (result.errors.length === 0 && result.warnings.length === 0)
            assert.equal(/* actual */ isValid, /* expected */ testCase.result.valid)
            assert.deepEqual(/* actual */ result.errors.map(error => error.formattedError), /* expected */ testCase.result.errors)
            assert.deepEqual(/* actual */ result.warnings, /* expected */ testCase.result.warnings)
        })
    }
})