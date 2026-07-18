import UIKit
import FirebaseCore
import GoogleSignIn
#if FREE_VERSION
import GoogleMobileAds
#endif
import FirebaseAuth

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?
    private let discordRedirectHandler = DiscordOAuthRedirectHandler()

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

        // ダークモード対応の設定
        setupAppearance()

        var filePath: String?

        #if DEBUG
        filePath = Bundle.main.path(forResource: "GoogleService-Info-Debug", ofType: "plist")
        #else
        filePath = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist")
        #endif

        if let filePath = filePath, let options = FirebaseOptions(contentsOfFile: filePath) {
            FirebaseApp.configure(options: options)
        } else {
            fatalError("Couldn't find correct GoogleService-Info.plist file.")
        }

        // Initialize the Google Mobile Ads SDK.FirebaseCoreDiagnostics
#if FREE_VERSION
        GADMobileAds.sharedInstance().start(completionHandler: nil)
#endif
        return true
    }

    // アプリ全体の外観設定
    private func setupAppearance() {
        
        window?.tintColor = .tint
        
        if #available(iOS 13.0, *) {
            // iOS 13以降はシステムのダークモード設定に従う
            window?.overrideUserInterfaceStyle = .unspecified

            // ナビゲーションバーの外観設定
            let navBarAppearance = UINavigationBarAppearance()
            navBarAppearance.configureWithOpaqueBackground()
            navBarAppearance.backgroundColor = UIColor.colorPrimary
            navBarAppearance.titleTextAttributes = [.foregroundColor: UIColor.tint]
            navBarAppearance.largeTitleTextAttributes = [.foregroundColor: UIColor.tint]

            UINavigationBar.appearance().standardAppearance = navBarAppearance
            UINavigationBar.appearance().scrollEdgeAppearance = navBarAppearance
            UINavigationBar.appearance().compactAppearance = navBarAppearance

            // タブバーの外観設定
            let tabBarAppearance = UITabBarAppearance()
            tabBarAppearance.configureWithOpaqueBackground()
            tabBarAppearance.backgroundColor = UIColor.colorPrimary

            UITabBar.appearance().standardAppearance = tabBarAppearance
            if #available(iOS 15.0, *) {
                UITabBar.appearance().scrollEdgeAppearance = tabBarAppearance
            }

            // テーブルビューの外観設定
            UITableView.appearance().backgroundColor = UIColor.defaultBackground

            // テーブルビューセルの外観設定
            UITableViewCell.appearance().backgroundColor = UIColor.defaultBackground

            // コレクションビューの外観設定
            UICollectionView.appearance().backgroundColor = UIColor.defaultBackground
        } else {
            // iOS 13未満の場合は独自のダークテーマを適用
            UINavigationBar.appearance().barTintColor = UIColor.colorPrimary
            UINavigationBar.appearance().tintColor = UIColor.appWhite
            UINavigationBar.appearance().titleTextAttributes = [.foregroundColor: UIColor.appWhite]

            UITabBar.appearance().barTintColor = UIColor.colorPrimary
            UITabBar.appearance().tintColor = UIColor.appWhite

            UITableView.appearance().backgroundColor = UIColor.defaultBackground
            UITableViewCell.appearance().backgroundColor = UIColor.defaultBackground
            UICollectionView.appearance().backgroundColor = UIColor.defaultBackground
        }
    }

    func applicationWillResignActive(_ application: UIApplication) {
        // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
        // Use this method to pause ongoing tasks, disable timers, and throttle down OpenGL ES frame rates. Games should use this method to pause the game.
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
        // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        // Called as part of the transition from the background to the inactive state; here you can undo many of the changes made on entering the background.
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
        //StoreReviewHelper.shared.incrementAppLaunchCount()
        //StoreReviewHelper.shared.requestReviewIfAppropriate()
    }

    func applicationWillTerminate(_ application: UIApplication) {
        // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
    }

    // URLハンドリング
    func application(_ app: UIApplication,
                    open url: URL,
                    options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        // Google SignInのURLハンドリング
        if GIDSignIn.sharedInstance.handle(url) {
            return true
        }

        // Discord OAuth リダイレクトのハンドリング
        if discordRedirectHandler.handleUrl(url) {
            return true
        }

        // 他のアプリからファイルを開く処理
        openMainScreenController(withFileAt: url)
        return true
    }

    // MainScreenControllerを起動してファイルを処理するためのカスタムメソッド
    func openMainScreenController(withFileAt url: URL) {
    }
}
