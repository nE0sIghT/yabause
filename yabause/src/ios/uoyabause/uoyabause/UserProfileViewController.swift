import UIKit
import FirebaseAuth
import FirebaseFirestore
import FirebaseCore
import FirebaseDatabase
import FirebaseStorage
import NotificationCenter
import GoogleSignIn

// DiscordOAuthRedirectHandlerからの通知を使用するためのimport
@_exported import class UIKit.UIViewController
@_exported import struct Foundation.Notification

class UserProfileViewController: UIViewController {

    // MARK: - UI Components
    private let stackView: UIStackView = {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 20
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        return stack
    }()

    private let userImageView: UIImageView = {
        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.layer.cornerRadius = 50
        imageView.backgroundColor = .systemGray5
        imageView.translatesAutoresizingMaskIntoConstraints = false
        return imageView
    }()

    private let userNameLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 20, weight: .bold)
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private let logoutButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle(NSLocalizedString("Sign out", comment: "Button title for sign out"), for: .normal)
        button.backgroundColor = .systemGray
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 8
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }()

    private let deleteAccountButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle(NSLocalizedString("Delete Account", comment: "Button title for account deletion"), for: .normal)
        button.backgroundColor = .systemRed
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 8
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }()

    private let discordStatusLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 16)
        label.textAlignment = .center
        label.textColor = .secondaryLabel
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private let discordIconView: UIImageView = {
        let imageView = UIImageView()
        imageView.image = UIImage(named: "Discord-Symbol-Blurple")
        imageView.contentMode = .scaleAspectFit
        imageView.translatesAutoresizingMaskIntoConstraints = false
        return imageView
    }()

    private let discordLinkButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle(NSLocalizedString("Link Discord Account", comment: "Button title for Discord linking"), for: .normal)
        button.backgroundColor = UIColor(red: 88/255.0, green: 101/255.0, blue: 242/255.0, alpha: 1.0)
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 8
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }()

    private let discordUnlinkButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle(NSLocalizedString("Unlink Discord Account", comment: "Button title for Discord unlinking"), for: .normal)
        button.backgroundColor = .systemGray
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 8
        button.translatesAutoresizingMaskIntoConstraints = false
        button.isHidden = true
        return button
    }()

    private lazy var discordContainerView: UIView = {
        let containerView = UIView()
        containerView.translatesAutoresizingMaskIntoConstraints = false

        // Discordアイコンとステータスラベルを水平方向に配置
        containerView.addSubview(discordIconView)
        containerView.addSubview(discordStatusLabel)

        NSLayoutConstraint.activate([
            discordIconView.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
            discordIconView.centerYAnchor.constraint(equalTo: containerView.centerYAnchor),
            discordIconView.widthAnchor.constraint(equalToConstant: 24),
            discordIconView.heightAnchor.constraint(equalToConstant: 24),

            discordStatusLabel.leadingAnchor.constraint(equalTo: discordIconView.trailingAnchor, constant: 8),
            discordStatusLabel.centerYAnchor.constraint(equalTo: containerView.centerYAnchor),
            discordStatusLabel.trailingAnchor.constraint(equalTo: containerView.trailingAnchor)
        ])

        return containerView
    }()

    // MARK: - Lifecycle
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupNavigationBar()
        updateUserInfo()

        // Discord連携の通知を購読
        setupNotifications()
    }

    deinit {
        // 通知の購読を解除
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Notifications
    private func setupNotifications() {
        // Discord連携成功の通知を購読
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleDiscordLinkSuccess),
            name: Notification.Name("discordLinkSuccess"),
            object: nil
        )

        // Discord連携失敗の通知を購読
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleDiscordLinkFailure),
            name: Notification.Name("discordLinkFailure"),
            object: nil
        )
    }

    @objc private func handleDiscordLinkSuccess() {
        // ユーザー情報を更新して、Discord連携状態を反映
        if let userId = Auth.auth().currentUser?.uid {
            checkDiscordLinkStatus(userId: userId)
        }
    }

    @objc private func handleDiscordLinkFailure() {
        // 失敗時は特に何もしない（アラートは既に表示されている）
        // 必要に応じて追加の処理を実装
    }

    // MARK: - UI Setup
    private func setupUI() {
        view.backgroundColor = .systemBackground
        title = NSLocalizedString("Profile", comment: "Title for profile screen")

        view.addSubview(stackView)

        stackView.addArrangedSubview(userImageView)
        stackView.addArrangedSubview(userNameLabel)
        stackView.addArrangedSubview(discordContainerView)
        stackView.addArrangedSubview(discordLinkButton)
        stackView.addArrangedSubview(discordUnlinkButton)
        stackView.addArrangedSubview(logoutButton)
        stackView.addArrangedSubview(deleteAccountButton)

        NSLayoutConstraint.activate([
            stackView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            stackView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 40),
            stackView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            stackView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),

            userImageView.widthAnchor.constraint(equalToConstant: 100),
            userImageView.heightAnchor.constraint(equalToConstant: 100),

            discordContainerView.widthAnchor.constraint(equalToConstant: 200),
            discordContainerView.heightAnchor.constraint(equalToConstant: 30),

            discordLinkButton.heightAnchor.constraint(equalToConstant: 44),
            discordLinkButton.widthAnchor.constraint(equalToConstant: 200),

            discordUnlinkButton.heightAnchor.constraint(equalToConstant: 44),
            discordUnlinkButton.widthAnchor.constraint(equalToConstant: 200),

            logoutButton.heightAnchor.constraint(equalToConstant: 44),
            logoutButton.widthAnchor.constraint(equalToConstant: 200),

            deleteAccountButton.heightAnchor.constraint(equalToConstant: 44),
            deleteAccountButton.widthAnchor.constraint(equalToConstant: 200)
        ])

        logoutButton.addTarget(self, action: #selector(logoutButtonTapped), for: .touchUpInside)
        discordLinkButton.addTarget(self, action: #selector(discordLinkButtonTapped), for: .touchUpInside)
        discordUnlinkButton.addTarget(self, action: #selector(discordUnlinkButtonTapped), for: .touchUpInside)
        deleteAccountButton.addTarget(self, action: #selector(deleteAccountButtonTapped), for: .touchUpInside)
    }

    private func setupNavigationBar() {
        let closeButton = UIBarButtonItem(title: NSLocalizedString("Close", comment: "Close Dialog"), style: .plain, target: self, action: #selector(closeButtonTapped))
        navigationItem.leftBarButtonItem = closeButton
    }

    // MARK: - User Info
    private func updateUserInfo() {
        // Firebaseが初期化されているか確認
        if FirebaseApp.app() == nil {
            // GoogleService-Info.plistからFirebaseを初期化
            var filePath: String?

            #if DEBUG
            filePath = Bundle.main.path(forResource: "GoogleService-Info-Debug", ofType: "plist")
            #else
            filePath = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist")
            #endif

            if let filePath = filePath, let options = FirebaseOptions(contentsOfFile: filePath) {
                FirebaseApp.configure(options: options)
            } else {
                print("Error: Couldn't find correct GoogleService-Info.plist file.")
                dismiss(animated: true)
                return
            }
        }

        guard let user = Auth.auth().currentUser else {
            dismiss(animated: true)
            return
        }

        // ユーザー名の設定
        userNameLabel.text = user.displayName ?? NSLocalizedString("Anonymous User", comment: "Default name for users without a display name")

        // プロフィール画像の設定
        if let photoURL = user.photoURL {
            // URLから画像を読み込む（実際のアプリではKingfisherなどのライブラリを使うことが推奨されます）
            DispatchQueue.global().async {
                if let data = try? Data(contentsOf: photoURL), let image = UIImage(data: data) {
                    DispatchQueue.main.async {
                        self.userImageView.image = image
                    }
                } else {
                    // 画像の読み込みに失敗した場合はデフォルト画像を設定
                    DispatchQueue.main.async {
                        self.userImageView.image = UIImage(systemName: "person.circle.fill")
                        self.userImageView.tintColor = .systemBlue
                    }
                }
            }
        } else {
            // プロフィール画像がない場合はデフォルト画像を設定
            userImageView.image = UIImage(systemName: "person.circle.fill")
            userImageView.tintColor = .systemBlue
        }

        // Discordアカウント連携状態の確認
        checkDiscordLinkStatus(userId: user.uid)
    }

    private func checkDiscordLinkStatus(userId: String) {
        // Firestoreからユーザーのディスコード連携情報を取得
        let db = Firestore.firestore()
        db.collection("discord_links").document(userId).getDocument { [weak self] (document, error) in
            guard let self = self else { return }

            if let document = document, document.exists, let _ = document.data()?["discord_id"] as? String {
                // ディスコードアカウントが連携されている場合
                DispatchQueue.main.async {
                    self.discordStatusLabel.text = NSLocalizedString("Linked", comment: "Status for linked Discord account")
                    self.discordLinkButton.isHidden = true
                    self.discordUnlinkButton.isHidden = false
                }
            } else {
                // ディスコードアカウントが連携されていない場合
                DispatchQueue.main.async {
                    self.discordStatusLabel.text = NSLocalizedString("Not Linked", comment: "Status for unlinked Discord account")
                    self.discordLinkButton.isHidden = false
                    self.discordUnlinkButton.isHidden = true
                }
            }
        }
    }

    // MARK: - Actions
    @objc private func closeButtonTapped() {
        dismiss(animated: true)
    }

    @objc private func logoutButtonTapped() {
        // サインアウト処理
        do {
            try Auth.auth().signOut()
            let alert = UIAlertController(
                title: NSLocalizedString("ログアウト成功", comment: "Title for successful logout"),
                message: NSLocalizedString("ログアウトしました", comment: "Message for successful logout"),
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in
                self?.dismiss(animated: true)
            })
            present(alert, animated: true)
        } catch {
            print("Error signing out: \(error.localizedDescription)")

            // エラー時のアラート表示
            let alert = UIAlertController(
                title: NSLocalizedString("ログアウト失敗", comment: "Title for logout error"),
                message: error.localizedDescription,
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            present(alert, animated: true)
        }
    }

    @objc private func discordLinkButtonTapped() {
        // Discordアカウント連携処理
        let discordAuthManager = DiscordAuthManager()
        discordAuthManager.startDiscordLogin()
    }

    @objc private func discordUnlinkButtonTapped() {
        // Discordアカウント連携解除の確認ダイアログ
        let alert = UIAlertController(
            title: NSLocalizedString("Discord連携解除", comment: "Title for Discord unlink confirmation"),
            message: NSLocalizedString("Discordアカウントの連携を解除しますか？", comment: "Message for Discord unlink confirmation"),
            preferredStyle: .alert
        )

        alert.addAction(UIAlertAction(title: NSLocalizedString("キャンセル", comment: "Cancel button"), style: .cancel))
        alert.addAction(UIAlertAction(title: NSLocalizedString("解除する", comment: "Confirm unlink button"), style: .destructive) { [weak self] _ in
            self?.unlinkDiscordAccount()
        })

        present(alert, animated: true)
    }

    private func unlinkDiscordAccount() {
        guard let userId = Auth.auth().currentUser?.uid else { return }

        // Firestoreからディスコード連携情報を削除
        let db = Firestore.firestore()
        db.collection("discord_links").document(userId).delete { [weak self] error in
            guard let self = self else { return }

            if let error = error {
                print("Error unlinking Discord account: \(error.localizedDescription)")

                // エラー時のアラート表示
                let alert = UIAlertController(
                    title: NSLocalizedString("連携解除失敗", comment: "Title for Discord unlink error"),
                    message: error.localizedDescription,
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(title: "OK", style: .default))
                self.present(alert, animated: true)
            } else {
                // 成功時のアラート表示
                let alert = UIAlertController(
                    title: NSLocalizedString("連携解除成功", comment: "Title for successful Discord unlink"),
                    message: NSLocalizedString("Discordアカウントの連携を解除しました", comment: "Message for successful Discord unlink"),
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(title: "OK", style: .default))
                self.present(alert, animated: true)

                // UI更新
                self.discordStatusLabel.text = NSLocalizedString("Not Linked", comment: "Status for unlinked Discord account")
                self.discordLinkButton.isHidden = false
                self.discordUnlinkButton.isHidden = true
            }
        }
    }

    // MARK: - Account Deletion
    @objc private func deleteAccountButtonTapped() {
        // アカウント削除の確認ダイアログ
        let alert = UIAlertController(
            title: NSLocalizedString("アカウント削除", comment: "Title for account deletion confirmation"),
            message: NSLocalizedString("アカウントとすべての関連データを削除します。この操作は元に戻せません。続行しますか？", comment: "Message for account deletion confirmation"),
            preferredStyle: .alert
        )

        alert.addAction(UIAlertAction(title: NSLocalizedString("キャンセル", comment: "Cancel button"), style: .cancel))
        alert.addAction(UIAlertAction(title: NSLocalizedString("削除する", comment: "Confirm deletion button"), style: .destructive) { [weak self] _ in
            self?.promptForReauthentication()
        })

        present(alert, animated: true)
    }

    private func promptForReauthentication() {
        guard let currentUser = Auth.auth().currentUser else { return }

        // ユーザーの認証プロバイダを確認
        let providers = currentUser.providerData.map { $0.providerID }

        if providers.contains("google.com") {
            // Googleでの再認証
            reauthenticateWithGoogle()
        } else if providers.contains("password") {
            // メール/パスワードでの再認証
            promptForEmailPassword()
        } else {
            // その他のプロバイダまたは匿名認証の場合
            let alert = UIAlertController(
                title: NSLocalizedString("再認証が必要", comment: "Title for reauthentication required"),
                message: NSLocalizedString("セキュリティ上の理由から、アカウントを削除するには再度ログインする必要があります。一度ログアウトして再度ログインした後、再試行してください。", comment: "Message for reauthentication required"),
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            present(alert, animated: true)
        }
    }

    private func reauthenticateWithGoogle() {
        // GoogleSignInの設定
        guard let clientID = FirebaseApp.app()?.options.clientID else { return }
        let config = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.configuration = config

        // 現在のViewControllerからGoogleサインインを開始
        GIDSignIn.sharedInstance.signIn(withPresenting: self) { [weak self] result, error in
            guard let self = self else { return }

            if let error = error {
                print("Google Sign-In error: \(error.localizedDescription)")
                self.showReauthenticationError(error)
                return
            }

            guard let user = result?.user,
                  let idToken = user.idToken?.tokenString else {
                self.showReauthenticationError(NSError(domain: "GoogleSignIn", code: -1, userInfo: [NSLocalizedDescriptionKey: "ID token missing"]))
                return
            }

            // Googleの認証情報を作成
            let credential = GoogleAuthProvider.credential(withIDToken: idToken,
                                                         accessToken: user.accessToken.tokenString)

            // 再認証を実行
            self.reauthenticateAndDeleteAccount(with: credential)
        }
    }

    private func promptForEmailPassword() {
        let alert = UIAlertController(
            title: NSLocalizedString("再認証", comment: "Title for reauthentication"),
            message: NSLocalizedString("セキュリティ上の理由から、アカウントを削除するにはパスワードを再入力してください。", comment: "Message for reauthentication"),
            preferredStyle: .alert
        )

        alert.addTextField { textField in
            textField.placeholder = NSLocalizedString("パスワード", comment: "Password placeholder")
            textField.isSecureTextEntry = true
        }

        alert.addAction(UIAlertAction(title: NSLocalizedString("キャンセル", comment: "Cancel button"), style: .cancel))
        alert.addAction(UIAlertAction(title: NSLocalizedString("確認", comment: "Confirm button"), style: .default) { [weak self, weak alert] _ in
            guard let self = self,
                  let textFields = alert?.textFields,
                  let passwordField = textFields.first,
                  let password = passwordField.text,
                  !password.isEmpty,
                  let email = Auth.auth().currentUser?.email else {
                self?.showReauthenticationError(NSError(domain: "Reauthentication", code: -1, userInfo: [NSLocalizedDescriptionKey: "パスワードが入力されていません"]))
                return
            }

            // メール/パスワードの認証情報を作成
            let credential = EmailAuthProvider.credential(withEmail: email, password: password)

            // 再認証を実行
            self.reauthenticateAndDeleteAccount(with: credential)
        })

        present(alert, animated: true)
    }

    private func reauthenticateAndDeleteAccount(with credential: AuthCredential) {
        guard let currentUser = Auth.auth().currentUser else { return }

        // 再認証を実行
        currentUser.reauthenticate(with: credential) { [weak self] _, error in
            guard let self = self else { return }

            if let error = error {
                print("Reauthentication error: \(error.localizedDescription)")
                self.showReauthenticationError(error)
                return
            }

            // 再認証成功後、アカウント削除処理を実行
            self.deleteUserAccount()
        }
    }

    private func showReauthenticationError(_ error: Error) {
        let alert = UIAlertController(
            title: NSLocalizedString("再認証失敗", comment: "Title for reauthentication failure"),
            message: error.localizedDescription,
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    private func deleteUserAccount() {
        guard let currentUser = Auth.auth().currentUser else { return }
        let userId = currentUser.uid

        // ローディングインジケータを表示するなどの処理があれば追加

        // 非同期処理を開始
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            do {
                // 1. Realtime Databaseからユーザーデータを削除
                try self?.deleteUserDataFromDatabase(userId: userId)

                // 2. Firestoreからユーザードキュメントを削除
                let db = Firestore.firestore()
                let userDocRef = db.collection("users").document(userId)
                let semaphoreUser = DispatchSemaphore(value: 0)
                var userError: Error?

                userDocRef.delete { error in
                    userError = error
                    semaphoreUser.signal()
                }
                semaphoreUser.wait()

                if let error = userError {
                    throw error
                }
                print("Attempted Firestore document deletion: users/\(userId)")

                // 3. Firestoreからdiscord_linksドキュメントを削除
                let discordLinkDocRef = db.collection("discord_links").document(userId)
                let semaphoreDiscord = DispatchSemaphore(value: 0)
                var discordError: Error?

                discordLinkDocRef.delete { error in
                    discordError = error
                    semaphoreDiscord.signal()
                }
                semaphoreDiscord.wait()

                if let error = discordError {
                    throw error
                }
                print("Attempted Firestore document deletion: discord_links/\(userId)")

                // 4. Firebase Storageからユーザーファイルを削除
                try self?.deleteUserFilesFromStorage(userId: userId)

                // 5. ユーザーアカウントを削除
                let semaphoreAccount = DispatchSemaphore(value: 0)
                var accountError: Error?

                currentUser.delete { error in
                    accountError = error
                    semaphoreAccount.signal()
                }
                semaphoreAccount.wait()

                if let error = accountError {
                    throw error
                }

                // 6. 成功時の処理をメインスレッドで実行
                DispatchQueue.main.async {
                    // 成功時のアラート表示
                    let alert = UIAlertController(
                        title: NSLocalizedString("アカウント削除成功", comment: "Title for successful account deletion"),
                        message: NSLocalizedString("アカウントとすべての関連データが削除されました", comment: "Message for successful account deletion"),
                        preferredStyle: .alert
                    )
                    alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
                        // ViewControllerを閉じる
                        self?.dismiss(animated: true)
                    })
                    self?.present(alert, animated: true)
                }
            } catch {
                // エラー時の処理をメインスレッドで実行
                DispatchQueue.main.async {
                    print("Error deleting user account: \(error.localizedDescription)")

                    // エラー時のアラート表示
                    let alert = UIAlertController(
                        title: NSLocalizedString("アカウント削除失敗", comment: "Title for account deletion error"),
                        message: "\(NSLocalizedString("アカウント削除に失敗しました", comment: "Message for account deletion error")): \(error.localizedDescription)",
                        preferredStyle: .alert
                    )
                    alert.addAction(UIAlertAction(title: "OK", style: .default))
                    self?.present(alert, animated: true)
                }
            }
        }
    }

    private func deleteUserDataFromDatabase(userId: String) throws {
        // Realtime Databaseにある "/user-posts/{userId}" にある全データを削除
        let baseurl = "/user-posts/\(userId)"
        let database = Database.database()
        let userPostsRef = database.reference(withPath: baseurl)

        // 同期的に処理するためのセマフォを作成
        let semaphore = DispatchSemaphore(value: 0)
        var databaseError: Error?

        userPostsRef.removeValue { error, _ in
            databaseError = error
            semaphore.signal()
        }

        // 処理が完了するまで待機
        semaphore.wait()

        // エラーがあれば例外をスロー
        if let error = databaseError {
            print("Error deleting data for user: \(userId) at path \(baseurl): \(error.localizedDescription)")
            throw error
        }

        print("Successfully deleted data for user: \(userId) at path \(baseurl)")
    }

    private func deleteUserFilesFromStorage(userId: String) throws {
        let storage = Storage.storage()
        let storageRef = storage.reference()
        let userRef = storageRef.child(userId)

        // 同期的に処理するためのセマフォを作成
        let semaphore = DispatchSemaphore(value: 0)
        var storageError: Error?

        // ユーザーディレクトリ内のすべてのファイルを取得
        userRef.listAll { result, error in
            // ユーザーデータが存在しない場合（オブジェクトが見つからないエラー）は正常終了とする
            if let error = error as NSError?, error.domain == StorageErrorDomain,
               error.code == StorageErrorCode.objectNotFound.rawValue {
                print("No user data found in Storage for user: \(userId). Skipping deletion.")
                semaphore.signal()
                return
            } else if let error = error {
                // その他のエラーは通常通り処理
                storageError = error
                semaphore.signal()
                return
            }

            let group = DispatchGroup()

            // 各ファイルを削除
            if let items = result?.items {
                if items.isEmpty {
                    print("No files found in Storage for user: \(userId)")
                }

                for item in items {
                    group.enter()
                    item.delete { error in
                        if let error = error {
                            print("Error deleting file \(item.name): \(error.localizedDescription)")
                        } else {
                            print("Successfully deleted file: \(item.name)")
                        }
                        group.leave()
                    }
                }
            }

            // 各サブディレクトリを再帰的に削除
            if let prefixes = result?.prefixes {
                if prefixes.isEmpty {
                    print("No directories found in Storage for user: \(userId)")
                }

                for prefix in prefixes {
                    group.enter()
                    self.deleteStorageDirectory(prefix) { error in
                        if let error = error {
                            // エラーがあっても処理を続行する（ベストエフォート）
                            print("Error deleting directory \(prefix.name): \(error.localizedDescription)")
                        } else {
                            print("Successfully deleted directory: \(prefix.name)")
                        }
                        group.leave()
                    }
                }
            }

            // すべての削除処理が完了するのを待つ
            group.notify(queue: .global()) {
                semaphore.signal()
            }
        }

        // 処理が完了するまで待機
        semaphore.wait()

        // エラーがあれば例外をスロー（ただしオブジェクトが見つからないエラーは除く）
        if let error = storageError as NSError?,
           !(error.domain == StorageErrorDomain && error.code == StorageErrorCode.objectNotFound.rawValue) {
            throw error
        }

        print("Storage cleanup for user \(userId) completed")
    }

    private func deleteStorageDirectory(_ reference: StorageReference, completion: @escaping (Error?) -> Void) {
        reference.listAll { result, error in
            // ディレクトリが存在しない場合は正常終了とする
            if let error = error as NSError?, error.domain == StorageErrorDomain,
               error.code == StorageErrorCode.objectNotFound.rawValue {
                print("Directory not found: \(reference.fullPath). Skipping deletion.")
                completion(nil)
                return
            } else if let error = error {
                // その他のエラーは通常通り処理
                print("Error listing directory \(reference.fullPath): \(error.localizedDescription)")
                completion(error)
                return
            }

            let group = DispatchGroup()
            var lastError: Error?

            // 各ファイルを削除
            if let items = result?.items {
                if items.isEmpty {
                    print("No files found in directory: \(reference.fullPath)")
                }

                for item in items {
                    group.enter()
                    item.delete { error in
                        if let error = error {
                            // エラーを記録するが、処理は続行する
                            lastError = error
                            print("Error deleting file \(item.name): \(error.localizedDescription)")
                        } else {
                            print("Successfully deleted file: \(item.fullPath)")
                        }
                        group.leave()
                    }
                }
            }

            // 各サブディレクトリを再帰的に削除
            if let prefixes = result?.prefixes {
                if prefixes.isEmpty {
                    print("No subdirectories found in directory: \(reference.fullPath)")
                }

                for prefix in prefixes {
                    group.enter()
                    self.deleteStorageDirectory(prefix) { error in
                        if let error = error {
                            // エラーを記録するが、処理は続行する
                            lastError = error
                            print("Error in subdirectory deletion: \(error.localizedDescription)")
                        } else {
                            print("Successfully deleted subdirectory: \(prefix.fullPath)")
                        }
                        group.leave()
                    }
                }
            }

            // すべての削除処理が完了したらコールバックを呼び出す
            group.notify(queue: .global()) {
                // オブジェクトが見つからないエラーは無視する
                if let error = lastError as NSError?,
                   error.domain == StorageErrorDomain && error.code == StorageErrorCode.objectNotFound.rawValue {
                    completion(nil)
                } else {
                    completion(lastError)
                }
            }
        }
    }
}
