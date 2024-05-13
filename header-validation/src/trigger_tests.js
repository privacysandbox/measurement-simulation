const triggerTestCases = [
    {
        name: "App Destination Present | Valid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) App Destination | Invalid",
        type: "app",
        flags: {},
        json: "{\"destination\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `destination`"],
            warnings: []
        }
    },
    {
        name: "Web Destination Present | Valid",
        type: "web",
        flags: {},
        json: "{\"destination\":\"https://web-destination.test\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "Web Destination Invalid Path | Invalid",
        type: "web",
        flags: {},
        json: "{\"destination\":\"web-destination.test\"}",
        result: {
            valid: false,
            errors: ["URI is not valid: `destination`"],
            warnings: []
        }
    },
    {
        name: "Web Destination Missing Host | Invalid",
        type: "web",
        flags: {},
        json: "{\"destination\":\"/web-destination\"}",
        result: {
            valid: false,
            errors: ["URI is not valid: `destination`"],
            warnings: []
        }
    }
]

module.exports = {
    triggerTestCases
};