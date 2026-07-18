import UIKit

// Discord連携の通知名を定義
extension Notification.Name {
    static let discordLinkSuccess = Notification.Name("discordLinkSuccess")
    static let discordLinkFailure = Notification.Name("discordLinkFailure")
}

/**
 * Handler for Discord OAuth redirects
 * This class should be used in AppDelegate to handle Discord OAuth redirects
 */
class DiscordOAuthRedirectHandler {
    private let TAG = "DiscordOAuthRedirectHandler"
    private let discordAuthManager = DiscordAuthManager()

    /**
     * Handle a URL opened by the app
     * @param url The URL to handle
     * @return Boolean true if the URL was handled, false otherwise
     */
    func handleUrl(_ url: URL) -> Bool {
        // Check if this is a Discord OAuth redirect
        guard url.absoluteString.starts(with: "yabasanshiro://discord-auth") else {
            return false
        }

        print("\(TAG): Received Discord OAuth redirect: \(url)")

        // Process the redirect asynchronously
        Task {
            let success = await discordAuthManager.handleRedirectAndSignIn(url: url)

            DispatchQueue.main.async {
                if success {
                    self.showSuccessAlert()
                } else {
                    self.showErrorAlert()
                }
            }
        }

        return true
    }

    /**
     * Show a success alert
     */
    private func showSuccessAlert() {
        let alert = UIAlertController(
            title: NSLocalizedString("Discord連携成功", comment: "Title for successful Discord link"),
            message: NSLocalizedString("Discordアカウントの連携に成功しました", comment: "Message for successful Discord link"),
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
            // アラートが閉じられた後に通知を送信
            NotificationCenter.default.post(name: .discordLinkSuccess, object: nil)
        })

        // Find the top-most view controller to present the alert
        if let topController = UIApplication.shared.windows.first?.rootViewController {
            var presentedVC = topController
            while let presented = presentedVC.presentedViewController {
                presentedVC = presented
            }
            presentedVC.present(alert, animated: true)
        }
    }

    /**
     * Show an error alert
     */
    private func showErrorAlert() {
        let alert = UIAlertController(
            title: NSLocalizedString("Discord連携失敗", comment: "Title for Discord link error"),
            message: NSLocalizedString("Discordアカウントの連携に失敗しました", comment: "Message for Discord link error"),
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
            // アラートが閉じられた後に通知を送信
            NotificationCenter.default.post(name: .discordLinkFailure, object: nil)
        })

        // Find the top-most view controller to present the alert
        if let topController = UIApplication.shared.windows.first?.rootViewController {
            var presentedVC = topController
            while let presented = presentedVC.presentedViewController {
                presentedVC = presented
            }
            presentedVC.present(alert, animated: true)
        }
    }
}
