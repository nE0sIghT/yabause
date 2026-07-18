//
//  MainScreenController.swift
//  uoyabause
//
//  Created by MiyamotoShinya on 2016/09/04.
//  Copyright © 2016年 devMiyax. All rights reserved.
//

import Foundation
import UIKit
import UniformTypeIdentifiers
import FirebaseAuth

class MainScreenController :UIViewController, UIDocumentPickerDelegate  {

    var activityIndicator: UIActivityIndicatorView!
    var blurEffectView: UIVisualEffectView!
    var selected_file_path: String = ""
    @IBOutlet weak var settingButton: UIButton!
    private var authButton: UIBarButtonItem!
    private var authIconView: UIImageView!

    override func viewDidLoad() {
        super.viewDidLoad()

        // 背景色を設定（システムのモードに応じて）
        view.backgroundColor = .defaultBackground

        // Blur Effect Viewの設定
        let blurEffect = UIBlurEffect(style: .dark)
        blurEffectView = UIVisualEffectView(effect: blurEffect)
        blurEffectView.frame = self.view.bounds
        blurEffectView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        blurEffectView.isHidden = true
        self.view.addSubview(blurEffectView)

        // Activity Indicatorの設定
        activityIndicator = UIActivityIndicatorView(style: .large)
        activityIndicator.center = self.view.center
        activityIndicator.hidesWhenStopped = true
        self.view.addSubview(activityIndicator)

        // Activity Indicatorをビュー階層の一番上に持ってくる
        self.view.bringSubviewToFront(activityIndicator)
        #if FREE_VERSION
        self.navigationItem.title = "Yaba Sanshiro 2 Lite"
        #endif

        settingButton.accessibilityIdentifier = "settingButton"

        // Auth buttonの追加
        setupAuthButton()

        // Auth状態の監視を開始
        observeAuthState()
    }

    private func setupAuthButton() {
        // コンテナビューの作成（固定サイズ）
        let containerView = UIView(frame: CGRect(x: 0, y: 0, width: 30, height: 30))

        // アイコンビューの作成
        authIconView = UIImageView(frame: containerView.bounds)
        authIconView.contentMode = .scaleAspectFill
        authIconView.clipsToBounds = true
        authIconView.layer.cornerRadius = 15
        authIconView.layer.masksToBounds = true
        authIconView.isUserInteractionEnabled = true
        authIconView.backgroundColor = .clear
        authIconView.autoresizingMask = [.flexibleWidth, .flexibleHeight]

        // コンテナビューにアイコンビューを追加
        containerView.addSubview(authIconView)

        // タップジェスチャーの追加
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(authButtonTapped))
        containerView.addGestureRecognizer(tapGesture)
        containerView.isUserInteractionEnabled = true

        // UIBarButtonItemの作成
        authButton = UIBarButtonItem(customView: containerView)
        navigationItem.rightBarButtonItems = [navigationItem.rightBarButtonItem, authButton].compactMap { $0 }

        // 初期状態の更新
        updateAuthButtonState()
    }

    private func observeAuthState() {
        Auth.auth().addStateDidChangeListener { [weak self] (auth, user) in
            self?.updateAuthButtonState()
        }
    }

    private func updateAuthButtonState() {
        // アイコンの丸みを確保（念のため）
        authIconView.layer.cornerRadius = 15
        authIconView.clipsToBounds = true
        authIconView.layer.masksToBounds = true

        if let user = Auth.auth().currentUser {
            // ログイン済みの場合
            if let photoURL = user.photoURL {
                // ユーザーのプロフィール画像がある場合は読み込む
                DispatchQueue.global().async {
                    if let data = try? Data(contentsOf: photoURL), let image = UIImage(data: data) {
                        DispatchQueue.main.async {
                            // 画像を設定
                            self.authIconView.image = image

                            // 画像設定後も丸みを確保（念のため）
                            self.authIconView.layer.cornerRadius = 15
                            self.authIconView.clipsToBounds = true
                            self.authIconView.layer.masksToBounds = true
                        }
                    } else {
                        // 画像の読み込みに失敗した場合はデフォルト画像を設定
                        DispatchQueue.main.async {
                            self.authIconView.image = UIImage(systemName: "person.circle.fill")
                            self.authIconView.tintColor = .systemBlue
                        }
                    }
                }
            } else {
                // プロフィール画像がない場合はデフォルト画像を設定
                authIconView.image = UIImage(systemName: "person.circle.fill")
                authIconView.tintColor = .systemBlue
            }
        } else {
            // 未ログインの場合
            authIconView.image = UIImage(systemName: "person.circle")
            authIconView.tintColor = .systemGray
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)

        // 背景色を設定
        view.backgroundColor = .defaultBackground

        // 子ビューコントローラーの背景色も設定（ビューがロードされている場合のみ）
        for child in children {
            if let fileSelectController = child as? FileSelectController, fileSelectController.isViewLoaded {
                fileSelectController.view.backgroundColor = .defaultBackground
            }
        }

        // アイコンの丸みを確保（念のため）
        authIconView.layer.cornerRadius = 15
        authIconView.clipsToBounds = true
        authIconView.layer.masksToBounds = true

        // 認証状態を更新（アイコンの表示を更新）
        updateAuthButtonState()
    }

    @objc private func authButtonTapped() {
        if Auth.auth().currentUser != nil {
            // ログイン済みの場合はユーザープロフィール画面を表示
            let profileVC = UserProfileViewController()
            let navController = UINavigationController(rootViewController: profileVC)
            present(navController, animated: true)
        } else {
            // 未ログインの場合はサインイン画面を表示
            let loginVC = LoginViewController()
            let navController = UINavigationController(rootViewController: loginVC)
            present(navController, animated: true)
        }
    }

    @IBAction func onAddFile(_ sender: Any) {

        for child in self.children {
            if let fc = child as? FileSelectController {
                if fc.checkLimitation() == false {
                    return
                }
            }
        }

        var documentPicker: UIDocumentPickerViewController!
            // iOS 14 & later
            let supportedTypes: [UTType] = [
                UTType(filenameExtension: "bin")!,
                UTType(filenameExtension: "cue")!,
                UTType(filenameExtension: "chd")!,
                UTType(filenameExtension: "ccd")!,
                UTType(filenameExtension: "img")!,
                UTType(filenameExtension: "mds")!,
                UTType(filenameExtension: "mdf")!,
            ]

        documentPicker = UIDocumentPickerViewController(forOpeningContentTypes: supportedTypes)
        documentPicker.delegate = self
        documentPicker.allowsMultipleSelection = true
        self.present(documentPicker, animated:true, completion: nil)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]){

        self.view.bringSubviewToFront(activityIndicator)
        blurEffectView.isHidden = false
        activityIndicator.startAnimating()

        DispatchQueue.global(qos: .userInitiated).async {
                // 選択されたファイルのURLを処理
            for url in urls {
                // ここで各ファイルのURLを処理します
                print("Selected file URL: \(url)")
                // 例: ファイルを解凍して処理する
                self.processFile(at: url)
            }

            DispatchQueue.main.async {
                self.children.forEach{
                    let fc = $0 as? FileSelectController
                    if fc != nil {
                        fc?.updateDoc()
                    }
                }
                self.blurEffectView.isHidden = true
                self.activityIndicator.stopAnimating()
            }
        }

    }

    func processFile(at fileURL: URL) {
        print(fileURL)

        guard fileURL.startAccessingSecurityScopedResource() else {
            // エラー処理
            return
        }

        var documentsUrl = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]


        let theFileName = fileURL.lastPathComponent

        if theFileName.lowercased().contains(".cue") ||
            theFileName.lowercased().contains(".bin") ||
            theFileName.lowercased().contains(".chd") ||
            theFileName.lowercased().contains(".ccd") ||
            theFileName.lowercased().contains(".img") ||
            theFileName.lowercased().contains(".mdf") ||
            theFileName.lowercased().contains(".mds")
        {
            documentsUrl.appendPathComponent(theFileName)

            if( documentsUrl != fileURL ){

                let fileManager = FileManager.default
                do {
                    if fileManager.fileExists(atPath: documentsUrl.path) {
                        try fileManager.removeItem(at: documentsUrl)
                    }
                    try fileManager.copyItem(at: fileURL, to: documentsUrl)
                } catch let error as NSError {
                    print("Fail to copy \(error.localizedDescription)")
                    return
                }
            }

        } else{
            let alert: UIAlertController = UIAlertController(
                title: NSLocalizedString("Fail to open", comment: "Title for the alert when a file fails to open"),
                message: NSLocalizedString("You can select chd or bin or cue", comment: "Message indicating the supported file formats"),
                preferredStyle: UIAlertController.Style.alert
            )

            let defaultAction: UIAlertAction = UIAlertAction(
                title: NSLocalizedString("OK", comment: "Default action button title"),
                style: UIAlertAction.Style.default,
                handler: { (action: UIAlertAction!) -> Void in
                    print("OK")
                }
            )

            alert.addAction(defaultAction)
            present(alert, animated: true, completion: nil)
            return
        }

        fileURL.stopAccessingSecurityScopedResource()
    }

    // ダークモード変更時の処理
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)

        if #available(iOS 13.0, *) {
            if traitCollection.hasDifferentColorAppearance(comparedTo: previousTraitCollection) {
                // 背景色を更新
                view.backgroundColor = .defaultBackground

                // 子ビューコントローラーにも通知（ビューがロードされている場合のみ）
                for child in children {
                    if let fileSelectController = child as? FileSelectController, fileSelectController.isViewLoaded {
                        fileSelectController.view.backgroundColor = .defaultBackground
                    }
                }
            }
        }
    }
}
