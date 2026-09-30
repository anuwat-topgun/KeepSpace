import XCTest

final class StoreSmokeTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testDemoLibraryPrimaryNavigation() {
        let app = XCUIApplication()
        app.launchArguments = ["-hasCompletedOnboarding", "YES", "-demoData", "YES"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Your storage, organized intelligently."].waitForExistence(timeout: 10))

        app.tabBars.buttons["Clean"].tap()
        XCTAssertTrue(app.staticTexts["How much space do you need?"].waitForExistence(timeout: 5))

        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["Your full photo library, with cloud backup status."].waitForExistence(timeout: 5))

        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["On-device AI only"].waitForExistence(timeout: 5))
        app.buttons["Cloud, Connect Google Drive or OneDrive"].tap()
        XCTAssertTrue(app.staticTexts["Cloud Backup"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Connect Google Drive"].exists)
    }
}
