import Foundation
import Testing
@testable import SmartStorage

@Suite("Cloud backup state")
struct CloudBackupTests {
    @Test func googleDriveUsesNativeIOSOAuthConfiguration() {
        #expect(CloudConfiguration.redirectURI(for: .googleDrive) == "com.googleusercontent.apps.704189605605-aaosn5etn4m6peil1dttaffdb0c20jtk:/oauthredirect")
        #expect(CloudConfiguration.scope(for: .googleDrive).contains("https://www.googleapis.com/auth/drive.file"))
    }

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

    @Test func libraryCloudStateUsesLatestJobForCurrentAssetVersion() {
        let modified = Date(timeIntervalSince1970: 1_700_000_000)
        let item = MediaItem(id: "asset-1", kind: .photo, creationDate: modified, modifiedAt: modified,
                             bytes: 42, pixelWidth: 100, pixelHeight: 200, duration: 0, isFavorite: false)
        var old = CloudUploadItem(provider: .googleDrive, scope: .photos, assetID: item.id,
                                  sourceName: "photo.jpg", destinationFolder: "Photos", bytes: 42,
                                  sourceModifiedAt: modified.addingTimeInterval(-60))
        old.status = .backedUp
        var current = CloudUploadItem(provider: .oneDrive, scope: .photos, assetID: item.id,
                                      sourceName: "photo.jpg", destinationFolder: "Photos", bytes: 42,
                                      sourceModifiedAt: modified)
        current.status = .verifying
        current.progress = 92

        let state = AssetCloudState.resolve(for: item, uploads: [old, current])
        #expect(state.status == .verifying)
        #expect(state.provider == .oneDrive)
        #expect(state.progress == 92)
    }
}
