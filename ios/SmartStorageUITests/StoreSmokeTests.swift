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

        // Filters update the grid and must never be interpreted as a tile tap that opens preview.
        app.buttons["library.filter.photos"].tap()
        XCTAssertTrue(app.buttons["library.filter.photos"].isSelected)
        XCTAssertFalse(app.buttons["Close"].waitForExistence(timeout: 1))
        app.buttons["library.filter.videos"].tap()
        XCTAssertTrue(app.buttons["library.filter.videos"].isSelected)
        XCTAssertFalse(app.buttons["Close"].waitForExistence(timeout: 1))
        app.buttons["library.filter.backedUp"].tap()
        XCTAssertTrue(app.buttons["library.filter.backedUp"].isSelected)
        XCTAssertFalse(app.buttons["Close"].waitForExistence(timeout: 1))
        app.buttons["library.filter.all"].tap()
        XCTAssertTrue(app.buttons["library.filter.all"].isSelected)
        XCTAssertFalse(app.buttons["Close"].waitForExistence(timeout: 1))

        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["On-device AI only"].waitForExistence(timeout: 5))
        let cloudButton = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Cloud")).firstMatch
        XCTAssertTrue(cloudButton.waitForExistence(timeout: 5))
        cloudButton.tap()
        XCTAssertTrue(app.staticTexts["Cloud Backup"].waitForExistence(timeout: 5))
    }

    /// Uses the device's actual photo library rather than fixtures. This catches touch forwarding
    /// caused by real thumbnails rebuilding and moving under the filter bar.
    @MainActor
    func testRealLibraryFiltersDoNotOpenPreview() {
        let app = XCUIApplication()
        app.launchArguments = ["-hasCompletedOnboarding", "YES"]
        app.launch()

        XCTAssertTrue(app.tabBars.buttons["Library"].waitForExistence(timeout: 10))
        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["Your full photo library, with cloud backup status."].waitForExistence(timeout: 10))

        for filter in ["photos", "videos", "backedUp", "all"] {
            XCTContext.runActivity(named: "Filter: \(filter)") { _ in
                let button = app.buttons["library.filter.\(filter)"]
                button.tap()
                XCTAssertFalse(app.buttons["Close"].waitForExistence(timeout: 1), "\(filter) opened preview")
                XCTAssertTrue(app.buttons["library.filter.\(filter)"].isSelected, "\(filter) was not selected")
            }
        }
    }

    /// Regression for the full-screen photo pager: a photo's zoom recognizers must not
    /// consume one-finger horizontal swipes while the image is at its normal scale.
    @MainActor
    func testDemoLibraryPhotosPageLeftAndRight() {
        let app = XCUIApplication()
        app.launchArguments = ["-hasCompletedOnboarding", "YES", "-demoData", "YES"]
        app.launch()

        XCTAssertTrue(app.tabBars.buttons["Library"].waitForExistence(timeout: 10))
        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["Your full photo library, with cloud backup status."].waitForExistence(timeout: 10))
        app.buttons["library.filter.photos"].tap()

        // The grid intentionally ignores the filter tap for a short debounce window so the
        // same touch cannot fall through to a thumbnail that moves under the filter control.
        // Wait for that guard to expire before exercising an intentional thumbnail tap.
        let tileTapReady = expectation(description: "Library tile tap guard expired")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { tileTapReady.fulfill() }
        wait(for: [tileTapReady], timeout: 1)

        let tiles = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "library.tile."))
        XCTAssertGreaterThanOrEqual(tiles.count, 2)
        let photoCount = tiles.count

        tiles.element(boundBy: 1).tap()
        XCTAssertTrue(app.buttons["Close"].waitForExistence(timeout: 5))

        let position = app.staticTexts["library.preview.position"]
        XCTAssertTrue(position.waitForExistence(timeout: 5))
        XCTAssertEqual(position.label, "2 / \(photoCount)")

        app.swipeLeft()
        XCTAssertEqual(position.label, "1 / \(photoCount)")

        app.swipeRight()
        XCTAssertEqual(position.label, "2 / \(photoCount)")
    }
}
