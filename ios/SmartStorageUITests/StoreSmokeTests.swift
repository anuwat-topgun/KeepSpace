import XCTest

final class StoreSmokeTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testDemoLibraryPrimaryNavigation() {
        let app = XCUIApplication()
        app.launchArguments = ["-hasCompletedOnboarding", "YES", "-demoData", "YES"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Your storage, organized intelligently."].waitForExistence(timeout: 10))

        app.tabBars.buttons["Clean"].tap()
        XCTAssertTrue(app.staticTexts["How much space do you need?"].waitForExistence(timeout: 5))

        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["Review and organize your media."].waitForExistence(timeout: 5))

        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["On-device AI only"].waitForExistence(timeout: 5))
        app.buttons["Cloud, Connect Google Drive or OneDrive"].tap()
        XCTAssertTrue(app.staticTexts.matching(identifier: "OAuth setup required").firstMatch.waitForExistence(timeout: 5))
    }
}
