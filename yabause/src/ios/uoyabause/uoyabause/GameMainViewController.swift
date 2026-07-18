//
//  GameMainViewController.swift
//  YabaSnashiro
//
//  Created by Shinya Miyamoto on 2024/07/21.
//  Copyright © 2024 devMiyax. All rights reserved.
//

import Foundation
import UIKit
import FirebaseAuth
import FirebaseFirestore



class GameMainViewController: UIViewController
{
    enum MenuState {
        case opened
        case closed
    }

    private var menuState : MenuState = .closed

    var selectedFile = ""
    var productNumber: String?
    var gameVC: GameViewController?
    let menuVC = MenuViewController()

     override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
         if let gameVC = segue.destination as? GameViewController {
             self.gameVC = gameVC
             gameVC.selectedFile = self.selectedFile
             gameVC.productNumber = self.productNumber // 追加
             self.gameVC?.gdelegate = self
         }
     }


    override func viewDidLoad() {
        super.viewDidLoad()
        menuVC.delegate = self
        view.backgroundColor = .black
        addChild(menuVC)
        view.addSubview(menuVC.view)
        menuVC.didMove(toParent: self)
        view.sendSubviewToBack(menuVC.view)
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        // landscapeフラグに応じて画面の向きを設定
        let ud = UserDefaults.standard
        let landscape = ud.bool(forKey: "landscape")

        if landscape {
            return .landscape
        } else {
            return .all
        }
    }

    override var prefersHomeIndicatorAutoHidden: Bool {
        return true
    }

    //@available(iOS 11, *)
    override var childForHomeIndicatorAutoHidden: UIViewController? {
        return nil
    }
}

extension GameMainViewController: GameViewControllerDelegate {

    func didTapMenuButton() {
        // Animate the menu
        // print("tap the menu")

        if( menuState == .opened ){
            self.gameVC?.isPaused = false
        }
        toggleMenu( completion: nil )
    }

    func toggleMenu( completion: (() -> Void)? ){
        switch menuState {
        case .closed:

            self.gameVC?.isPaused = true

            // open it
            UIView.animate(withDuration: 0.5, delay: 0, usingSpringWithDamping: 0.8, initialSpringVelocity: 0, options: .curveEaseInOut) {

                self.gameVC?.view.frame.origin.x = self.menuVC.optimalWidth

            }completion:{ [weak self] done in
                if done {
                    self?.menuState = .opened
                    completion?()
                }
            }

        case .opened:


            // close it
            UIView.animate(withDuration: 0.5, delay: 0, usingSpringWithDamping: 0.8, initialSpringVelocity: 0, options: .curveEaseInOut) {

                self.gameVC?.view.frame.origin.x = 0

            }completion:{ [weak self] done in
                if done {
                    self?.menuState = .closed
                    completion?()
                }
            }
        }
    }
}


extension GameMainViewController: MenuViewControllerDelegate {
    func didSelect(menuItem: MenuViewController.MenuOptions) {
        toggleMenu { [weak self] in

            var doNotPause = false
            switch menuItem {
            case .exit:

                // アラートコントローラの作成
                let alertController = UIAlertController(
                    title: NSLocalizedString("Exit Confirmation", comment: "Title for exit confirmation alert"),
                    message: NSLocalizedString("Are you sure you want to exit?", comment: "Message asking if the user is sure about exiting"),
                    preferredStyle: .alert
                )

                // "Yes"ボタンの追加
                let yesAction = UIAlertAction(
                    title: NSLocalizedString("Yes", comment: "Confirm action to exit the application"),
                    style: .default
                ) { action in

                    enterBackGround()
                    exit(0)
                }
                alertController.addAction(yesAction)

                // "No"ボタンの追加
                let noAction = UIAlertAction(
                    title: NSLocalizedString("No", comment: "Cancel the exit action"),
                    style: .default
                ) { action in
                    self?.gameVC?.isPaused = false
                }
                alertController.addAction(noAction)


                self?.present(alertController, animated: true, completion: nil)
                doNotPause = true
                break
            case .reset:
                self?.gameVC?.reset()
                break
            case .changeDisk:
                self?.gameVC?.presentFileSelectViewController()
                doNotPause = true
                break
            case .saveState:
                self?.gameVC?.saveState()
                break
            case .loadState:
                self?.gameVC?.loadState()
                break
            case .analogMode:
                break
            case .controllerSetting:
                self?.gameVC?.toggleControllSetting()
                break
            case .backupManager:
                self?.gameVC?.presentBackupFileListViewController()
                doNotPause = true
                break
            case .cheat:
                // ログイン状態を確認
                if let user = Auth.auth().currentUser {
                    // ログイン済みの場合、チート画面を表示
                    self?.gameVC?.presentCheatViewController()
                    doNotPause = true
                } else {
                    // 未ログインの場合、ログイン画面を表示
                    let loginVC = LoginViewController()
                    let navController = UINavigationController(rootViewController: loginVC)
                    navController.modalPresentationStyle = .pageSheet
                    loginVC.modalPresentationStyle = .fullScreen
                    loginVC.completionHandler = { [weak self] success in
                        if success {
                            // ログイン成功後にチート画面を表示
                            self?.gameVC?.presentCheatViewController()

                        }else{
                            self?.gameVC?.isPaused = false
                        }
                    }
                    self?.present(navController, animated: true, completion: nil)
                    doNotPause = true

                }
                break
            case .report:
                // ゲームコードを取得
                let productionNumber = YSGetCurrentGameCode() ?? ""

                // ReportDialogを表示
                let reportDialog = ReportDialog(productionNumber: productionNumber)
                reportDialog.completionHandler = { [weak self] (rating, message, screenshot) in
                    self?.gameVC?.isPaused = false
                }

                let navController = UINavigationController(rootViewController: reportDialog)
                navController.modalPresentationStyle = .pageSheet
                self?.present(navController, animated: true, completion: nil)
                doNotPause = true
                break
            case .leaderBoard:
                // ログイン状態を確認
                if let user = Auth.auth().currentUser {
                    // ログイン済みの場合、リーダーボード画面を表示
                    self?.gameVC?.presentLeaderBoardViewController()
                    doNotPause = true
                } else {
                    // 未ログインの場合、ログイン画面を表示
                    let loginVC = LoginViewController()
                    let navController = UINavigationController(rootViewController: loginVC)
                    navController.modalPresentationStyle = .pageSheet
                    loginVC.modalPresentationStyle = .fullScreen
                    loginVC.completionHandler = { [weak self] success in
                        if success {
                            // ログイン成功後にリーダーボード画面を表示
                            self?.gameVC?.presentLeaderBoardViewController()
                        } else {
                            self?.gameVC?.isPaused = false
                        }
                    }
                    self?.present(navController, animated: true, completion: nil)
                    doNotPause = true
                }
                break
            }

            if doNotPause == false{
                self?.gameVC?.isPaused = false
            }
        }
    }

    func didChangeAnalogMode(to: Bool){

        toggleMenu { [weak self] in
            let plist = SettingsViewController.getSettingPlist();
            plist.setObject(to, forKey: "analog mode" as NSCopying)
            plist.write(toFile: SettingsViewController.getSettingFilname(), atomically: true)
            self?.gameVC?.setAnalogMode(to: to)
            self?.gameVC?.isPaused = false
        }
    }

}
