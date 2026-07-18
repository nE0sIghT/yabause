//
//  MenuViewController.swift
//  YabaSnashiro
//
//  Created by Shinya Miyamoto on 2024/07/20.
//  Copyright © 2024 devMiyax. All rights reserved.
//

import Foundation

protocol MenuViewControllerDelegate: AnyObject {
    func didSelect(menuItem: MenuViewController.MenuOptions)
    func didChangeAnalogMode(to: Bool)
}

class SwitchTableViewCell: UITableViewCell {

    let toggleSwitch: UISwitch = {
        let toggleSwitch = UISwitch()
        toggleSwitch.translatesAutoresizingMaskIntoConstraints = false
        return toggleSwitch
    }()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setupLayout()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupLayout()
    }

    private func setupLayout() {
        contentView.addSubview(toggleSwitch)

        // スイッチを左端に配置
        NSLayoutConstraint.activate([
            toggleSwitch.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            toggleSwitch.centerYAnchor.constraint(equalTo: contentView.centerYAnchor)
        ])
    }
}


class MenuViewController: UIViewController, UITableViewDelegate, UITableViewDataSource {

    weak var delegate: MenuViewControllerDelegate?

    // メニューの最適な幅を外部から取得できるようにするプロパティ
    private(set) var optimalWidth: CGFloat = 0

    enum MenuOptions: String, CaseIterable {
        case exit = "Exit"
        case reset = "Reset"
        case changeDisk = "Change Disk"
        case cheat = "Action Replay Code"
        case backupManager = "Backup Manager"
        case saveState = "Save State"
        case loadState = "Load State"
        case controllerSetting = "Game Controller"
        case analogMode = "Analog Mode"
        case report = "Report Game"
        case leaderBoard = "Leader Board"

        var imageName: String {
            switch self {
            case .exit:
                return "power"
            case .reset:
                return "gobackward"
            case .changeDisk:
                return "opticaldiscdrive"
            case .saveState:
                return "square.and.arrow.up"
            case .loadState:
                return "square.and.arrow.down"
            case .analogMode:
                return "switch.2"
            case .controllerSetting:
                return "gamecontroller"
            case .backupManager:
                return "lock.square"
            case .cheat:
                return "hammer"
            case .report:
                return "star.bubble"
            case .leaderBoard:
                return "trophy"
            }
        }

        var localizedTitle: String {
            switch self {
            case .exit:
                return NSLocalizedString("Exit", comment: "Exit the application")
            case .reset:
                return NSLocalizedString("Reset", comment: "Reset the game")
            case .changeDisk:
                return NSLocalizedString("Change Disk", comment: "Change the game disk")
            case .saveState:
                return NSLocalizedString("Save State", comment: "Save the current state of the game")
            case .loadState:
                return NSLocalizedString("Load State", comment: "Load a previously saved game state")
            case .analogMode:
                return NSLocalizedString("Analog Mode", comment: "Switch to analog mode")
            case .report:
                return NSLocalizedString("Report Game", comment: "Title for the report dialog")
            case .controllerSetting:
                return NSLocalizedString("Game Controller", comment: "Game controller settings")
            case .backupManager:
                return NSLocalizedString("Backup Manager", comment: "Backup Manager settings")
            case .cheat:
                return NSLocalizedString("Action Replay Code", comment: "Action Replay Code menu")
            case .leaderBoard:
                return NSLocalizedString("Leader Board", comment: "Leader Board menu")
            }
        }
    }

    private let tableView: UITableView = {
        let table = UITableView()
        table.register(UITableViewCell.self, forCellReuseIdentifier: "cell")
        table.register(UITableViewCell.self, forCellReuseIdentifier: "switchCell")
        table.separatorInset = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 0)
        return table
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.addSubview(tableView)
        tableView.delegate = self
        tableView.dataSource = self
    }

    private func calculateOptimalWidth() -> CGFloat {
        let padding: CGFloat = 16  // セルの左右の余白
        let iconWidth: CGFloat = 30 + 16 // システムアイコンの幅

        let defaultFontSize: CGFloat = 16

        // 全てのメニュー項目の中で最も長いテキスト幅を計算
        let maxWidth = MenuOptions.allCases.map { option -> CGFloat in
            let text = option.localizedTitle
            let label = UILabel()
            label.text = text
            label.font = .systemFont(ofSize: defaultFontSize) // デフォルトのセルのフォントサイズ
            let size = label.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude))
            return size.width
        }.max() ?? 0

        let safeAreaOffset = self.view.safeAreaInsets.left

        // アイコン幅 + テキスト幅 + パディング
        return iconWidth + maxWidth + padding + safeAreaOffset
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        optimalWidth = calculateOptimalWidth()
        tableView.frame = CGRect(x: 0, y: view.safeAreaInsets.top, width: optimalWidth, height: view.bounds.size.height)
    }

    // UITableViewDataSourceメソッド
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return MenuOptions.allCases.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let option = MenuOptions.allCases[indexPath.row]

        if option == .analogMode {
            let cell = tableView.dequeueReusableCell(withIdentifier: "switchCell", for: indexPath)
            cell.textLabel?.text = option.localizedTitle
            cell.textLabel?.font = .systemFont(ofSize: 16)
            let toggleSwitch = UISwitch()
            cell.accessoryView = toggleSwitch
            let path = SettingsViewController.getSettingFilname()
            if let dic = NSDictionary(contentsOfFile: path) {
                toggleSwitch.isOn = (dic["analog mode"] as? Bool) ?? false
            }
            toggleSwitch.addTarget(self, action: #selector(didChangeAnalogModeSwitch(_:)), for: .valueChanged)
            return cell
        } else {
            let cell = tableView.dequeueReusableCell(withIdentifier: "cell", for: indexPath)
            // SFSymbolのサイズを固定
            let configuration = UIImage.SymbolConfiguration(pointSize: 20)
            cell.imageView?.image = UIImage(systemName: option.imageName, withConfiguration: configuration)
            cell.imageView?.preferredSymbolConfiguration = configuration
            cell.imageView?.contentMode = .center

            // セル内のレイアウト調整
            cell.imageView?.translatesAutoresizingMaskIntoConstraints = false
            cell.textLabel?.translatesAutoresizingMaskIntoConstraints = false

            if let imageView = cell.imageView, let textLabel = cell.textLabel {
                textLabel.text = option.localizedTitle
                textLabel.font = .systemFont(ofSize: 16)

                NSLayoutConstraint.activate([
                    // イメージビューの制約
                    imageView.widthAnchor.constraint(equalToConstant: 30),
                    imageView.heightAnchor.constraint(equalToConstant: 30),
                    imageView.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor, constant: 8),
                    imageView.centerYAnchor.constraint(equalTo: cell.contentView.centerYAnchor),

                    // テキストラベルの制約
                    textLabel.leadingAnchor.constraint(equalTo: imageView.trailingAnchor, constant: 8),
                    textLabel.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor, constant: -8),
                    textLabel.centerYAnchor.constraint(equalTo: cell.contentView.centerYAnchor)
                ])
            }
            return cell
        }
    }

    // トグルスイッチが変更されたときに呼ばれるメソッド
    @objc func didChangeAnalogModeSwitch(_ sender: UISwitch) {
        // Analog Modeの状態が変更されたときの処理をここに記述します
        let isAnalogModeOn = sender.isOn
        delegate?.didChangeAnalogMode(to: isAnalogModeOn)
    }

    // UITableViewDelegateメソッド
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let item = MenuOptions.allCases[indexPath.row]
        delegate?.didSelect(menuItem: item)
    }
}
