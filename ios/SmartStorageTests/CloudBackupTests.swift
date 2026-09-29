import Foundation
import Testing
@testable import SmartStorage

@Suite("Cloud backup state")
struct CloudBackupTests {
    @Test func oneDriveUsesPersonalMicrosoftAccountAuthority() {
        let endpoints = CloudConfiguration.endpoints(for: .oneDrive)
        #expect(endpoints.authorize.host == "login.microsoftonline.com")
        #expect(endpoints.authorize.path.hasPrefix("/consumers/"))
        #expect(endpoints.token.path.hasPrefix("/consumers/"))
    }

    @Test func queueRoundTripsEveryStatus() throws {
        for status in [CloudUploadStatus.waiting, .uploading, .verifying, .backedUp, .failed, .cancelled] {
            var item = CloudUploadItem(provider: .googleDrive, scope: .screenshots, assetID: "asset-1",
                                       sourceName: "shot.png", destinationFolder: "Screenshots/2026", bytes: 42)
            item.status = status
            let decoded = try JSONDecoder().decode(CloudUploadItem.self, from: JSONEncoder().encode(item))
            #expect(decoded.status == status)
            #expect(decoded.assetID == "asset-1")
            #expect(decoded.destinationFolder == "Screenshots/2026")
        }
    }

    @Test func v11AlwaysKeepsLocalCopies() {
        let preferences = UploadPreferences()
        #expect(preferences.keepLocalCopies)
        #expect(preferences.wifiOnly)
        #expect(preferences.renameAutomatically)
    }
}
