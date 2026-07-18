import UIKit
import FirebaseAuth
import FirebaseFirestore

protocol LeaderBoardControllerDelegate: AnyObject {
    func onLeaderboardClose()
}

class LeaderBoardController: UIViewController {
    // MARK: - Properties
    private var viewModel: LeaderBoardViewModel!
    private var tableView: UITableView!
    private var leaderboardButton: UIButton!
    private var loadingView: UIView!
    private var activityIndicator: UIActivityIndicatorView!
    private var emptyView: UILabel!
    private var rankLabel: UILabel!
    private var currentLeaderboardName: String = ""

    // Delegate for handling close events
    weak var closeDelegate: LeaderBoardControllerDelegate?

    // Game code for the leaderboard
    private var gameCode: String = "SATURNAPP"

    // MARK: - Lifecycle

    static func newInstance(gameCode: String) -> LeaderBoardController {
        let controller = LeaderBoardController()
        controller.gameCode = gameCode
        return controller
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        setupUI()
        setupViewModel()
        setupObservers()

        // Load initial data
        viewModel.loadLeaderboards(gameCode: gameCode)
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)

        // ダークモード/ライトモードが変更された場合にテーブルビューを更新
        if previousTraitCollection?.userInterfaceStyle != traitCollection.userInterfaceStyle {
            tableView.reloadData()
        }
    }

    // MARK: - UI Setup

    private func setupUI() {
        view.backgroundColor = .defaultBackground

        // Setup navigation bar
        title = NSLocalizedString("Leaderboard", comment: "Leaderboard title")
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .close,
            target: self,
            action: #selector(closeButtonTapped)
        )

        // Setup leaderboard popup button
        leaderboardButton = UIButton(type: .system)
        leaderboardButton.setTitle(NSLocalizedString("Select Leaderboard", comment: "Leaderboard selection button"), for: .normal)
        leaderboardButton.titleLabel?.font = UIFont.systemFont(ofSize: 18, weight: .medium)
        leaderboardButton.backgroundColor = .defaultBackground
        leaderboardButton.layer.cornerRadius = 8
        leaderboardButton.layer.borderWidth = 1
        leaderboardButton.layer.borderColor = UIColor.colorAccent.cgColor
        leaderboardButton.contentHorizontalAlignment = .center
        leaderboardButton.contentEdgeInsets = UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)
        leaderboardButton.addTarget(self, action: #selector(leaderboardButtonTapped), for: .touchUpInside)
        leaderboardButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(leaderboardButton)

        // Setup rank label
        rankLabel = UILabel()
        rankLabel.textAlignment = .center
        rankLabel.font = UIFont.systemFont(ofSize: 14)
        rankLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(rankLabel)

        // Setup table view
        tableView = UITableView()
        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(LeaderBoardCell.self, forCellReuseIdentifier: LeaderBoardCell.identifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        // Setup empty view
        emptyView = UILabel()
        emptyView.text = NSLocalizedString("No scores available", comment: "Empty leaderboard message")
        emptyView.textAlignment = .center
        emptyView.isHidden = true
        emptyView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(emptyView)

        // Setup loading view
        loadingView = UIView()
        loadingView.backgroundColor = UIColor.blackOpaque
        loadingView.isHidden = true
        loadingView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(loadingView)

        activityIndicator = UIActivityIndicatorView(style: .large)
        activityIndicator.color = .appWhite
        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        loadingView.addSubview(activityIndicator)

        // Setup constraints
        NSLayoutConstraint.activate([
            // Leaderboard button constraints
            leaderboardButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            leaderboardButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            leaderboardButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            // Rank label constraints
            rankLabel.topAnchor.constraint(equalTo: leaderboardButton.bottomAnchor, constant: 8),
            rankLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            rankLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            // Table view constraints
            tableView.topAnchor.constraint(equalTo: rankLabel.bottomAnchor, constant: 4),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),

            // Empty view constraints
            emptyView.centerXAnchor.constraint(equalTo: tableView.centerXAnchor),
            emptyView.centerYAnchor.constraint(equalTo: tableView.centerYAnchor),
            emptyView.leadingAnchor.constraint(equalTo: tableView.leadingAnchor, constant: 20),
            emptyView.trailingAnchor.constraint(equalTo: tableView.trailingAnchor, constant: -20),

            // Loading view constraints
            loadingView.topAnchor.constraint(equalTo: view.topAnchor),
            loadingView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            loadingView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            loadingView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            // Activity indicator constraints
            activityIndicator.centerXAnchor.constraint(equalTo: loadingView.centerXAnchor),
            activityIndicator.centerYAnchor.constraint(equalTo: loadingView.centerYAnchor)
        ])
    }

    private func setupViewModel() {
        viewModel = LeaderBoardViewModel()
    }

    // MARK: - Observers

    private func setupObservers() {
        // Observe leaderboards list
        viewModel.onLeaderboardsChanged = { [weak self] leaderboards in
            guard let self = self, !leaderboards.isEmpty else { return }
            DispatchQueue.main.async {
                // Select first leaderboard by default
                if let firstLeaderboard = leaderboards.first {
                    self.viewModel.selectLeaderboard(leaderboardId: firstLeaderboard.id)
                    self.currentLeaderboardName = firstLeaderboard.name ?? "Unknown"
                    self.updateLeaderboardButtonTitle()
                }
            }
        }

        // Observe scores list
        viewModel.onScoresChanged = { [weak self] scores in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.tableView.reloadData()

                // Show/hide empty view
                self.emptyView.isHidden = !scores.isEmpty
                self.tableView.isHidden = scores.isEmpty
            }
        }

        // Observe user position
        viewModel.onUserPositionChanged = { [weak self] position in
            guard let self = self else { return }
            DispatchQueue.main.async {
                // Highlight user's score in the table
                self.tableView.reloadData()

                // Scroll to user's position if needed
                if self.viewModel.scrollToMyRank, let position = position {
                    self.scrollToUserPosition(position)
                    self.viewModel.resetScrollFlag()
                }
            }
        }

        // Observe loading state
        viewModel.onLoadingStateChanged = { [weak self] isLoading in
            guard let self = self else { return }
            // Ensure UI updates happen on the main thread
            DispatchQueue.main.async {
                self.loadingView.isHidden = !isLoading
                if isLoading {
                    self.activityIndicator.startAnimating()
                } else {
                    self.activityIndicator.stopAnimating()
                }
            }
        }

        // Observe error messages
        viewModel.onErrorReceived = { [weak self] errorMessage in
            guard let self = self, let errorMessage = errorMessage else { return }
            DispatchQueue.main.async {
                let alert = UIAlertController(
                    title: NSLocalizedString("Error", comment: "Error alert title"),
                    message: errorMessage,
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(
                    title: NSLocalizedString("OK", comment: "OK button"),
                    style: .default
                ))
                self.present(alert, animated: true)
                self.viewModel.clearError()
            }
        }

        // Observe close fragment event
        viewModel.onCloseRequested = { [weak self] shouldClose in
            guard let self = self, shouldClose else { return }
            // Delay closing to allow messages to be shown
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                self.close()
                self.viewModel.resetCloseFragment()
            }
        }

        // Observe rank updates
        viewModel.onMyRankChanged = { [weak self] myRank in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.updateRankText(myRank: myRank, totalCount: self.viewModel.totalScoreCount)
            }
        }

        viewModel.onTotalScoreCountChanged = { [weak self] totalCount in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.updateRankText(myRank: self.viewModel.myRank, totalCount: totalCount)
            }
        }
    }

    // MARK: - Actions

    @objc private func closeButtonTapped() {
        close()
    }

    @objc private func leaderboardButtonTapped() {
        guard let leaderboards = viewModel.leaderboards, !leaderboards.isEmpty else { return }

        // Create menu actions for each leaderboard
        var actions = [UIAction]()

        for leaderboard in leaderboards {
            let leaderboardName = leaderboard.name ?? "Unknown"
            let isSelected = leaderboard.id == viewModel.currentLeaderboardId

            let action = UIAction(
                title: leaderboardName,
                image: isSelected ? UIImage(systemName: "checkmark") : nil,
                state: isSelected ? .on : .off
            ) { [weak self] _ in
                guard let self = self else { return }
                self.viewModel.selectLeaderboard(leaderboardId: leaderboard.id)
                self.currentLeaderboardName = leaderboardName
                self.updateLeaderboardButtonTitle()
            }

            actions.append(action)
        }

        // Create and show the menu
        let menu = UIMenu(title: "", children: actions)
        leaderboardButton.menu = menu
        leaderboardButton.showsMenuAsPrimaryAction = true
    }

    private func updateLeaderboardButtonTitle() {
        if currentLeaderboardName.isEmpty {
            leaderboardButton.setTitle(NSLocalizedString("Select Leaderboard", comment: "Leaderboard selection button"), for: .normal)
        } else {
            leaderboardButton.setTitle(currentLeaderboardName, for: .normal)
        }
    }

    func close() {
        closeDelegate?.onLeaderboardClose()
        dismiss(animated: true)
    }

    // MARK: - Helper Methods

    private func scrollToUserPosition(_ position: LeaderBoardRepository.UserPosition) {
        guard let scores = viewModel.scores else { return }

        // Find the index of the user's score in the list
        if let index = scores.firstIndex(where: { $0.rank == position.rank }) {
            let indexPath = IndexPath(row: index, section: 0)
            tableView.scrollToRow(at: indexPath, at: .middle, animated: true)
        }
    }

    private func updateRankText(myRank: Int, totalCount: Int) {
        if myRank > 0 && totalCount > 0 {
            // Show "myRank / totalCount" format
            rankLabel.text = "\(myRank) / \(totalCount)"
            rankLabel.isHidden = false
        } else if totalCount > 0 {
            // Show "- / totalCount" format
            rankLabel.text = "- / \(totalCount)"
            rankLabel.isHidden = false
        } else {
            // Hide the label if no valid data
            rankLabel.isHidden = true
        }
    }
}

// MARK: - UITableViewDelegate, UITableViewDataSource

extension LeaderBoardController: UITableViewDelegate, UITableViewDataSource {
    // セクションヘッダー追加
    func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let headerView = UIView()
        headerView.backgroundColor = .colorPrimary.withAlphaComponent(1.0) // ダークモード対応

        let totalWidth = tableView.frame.width
        let noWidth: CGFloat = 40
        let nameWidth: CGFloat = totalWidth * 0.3 // 0.4から0.3に減らす
        let remain = totalWidth - (noWidth + nameWidth + 16*2 + 8*3) // 16: 左右余白, 8: ラベル間
        let timeWidth: CGFloat = remain / 2
        let diffWidth: CGFloat = remain / 2

        let labels = [
            NSLocalizedString("Pos", comment: "Leaderboard header for rank number"),
            NSLocalizedString("Name", comment: "Leaderboard header for user name"),
            NSLocalizedString("Time", comment: "Leaderboard header for score/time"),
            NSLocalizedString("DIFF", comment: "Leaderboard header for difference from top score")
        ]
        let widths: [CGFloat] = [noWidth, nameWidth, timeWidth, diffWidth]
        var x: CGFloat = 16

        for (i, text) in labels.enumerated() {
            let label = UILabel()
            label.text = text
            label.font = UIFont.boldSystemFont(ofSize: 14)
            // ヘッダーテキスト色を使用
            label.textColor = .headerTextColor
            label.textAlignment = .left // ← これはUI属性のため国際化不要
            label.frame = CGRect(x: x, y: 0, width: widths[i], height: 32)
            headerView.addSubview(label)
            x += widths[i] + 8
        }
        return headerView // ← ヘッダーViewの返却
    }

    func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return 32 // ← 固定値のため国際化不要
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return viewModel.scores?.count ?? 0
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        guard let cell = tableView.dequeueReusableCell(withIdentifier: LeaderBoardCell.identifier, for: indexPath) as? LeaderBoardCell,
              let scores = viewModel.scores,
              indexPath.row < scores.count else {
            return UITableViewCell()
        }

        let score = scores[indexPath.row]
        let isCurrentUser = score.userId == viewModel.userPosition?.userId
        cell.configure(with: score, isCurrentUser: isCurrentUser, repository: viewModel.repository)

        return cell
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 60
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        let offsetY = scrollView.contentOffset.y
        let contentHeight = scrollView.contentSize.height
        let height = scrollView.frame.size.height

        // Load next page when scrolling down near the bottom
        if offsetY > contentHeight - height - 100 {
            if viewModel.hasMoreAfter && !viewModel.isLoading {
                viewModel.loadNextPage()
            }
        }

        // Load previous page when scrolling up near the top
        if offsetY < 100 {
            if viewModel.hasMoreBefore && !viewModel.isLoading {
                viewModel.loadPreviousPage()
            }
        }
    }
}



// MARK: - LeaderBoardCell

class LeaderBoardCell: UITableViewCell {
    static let identifier = "LeaderBoardCell"

    private let rankLabel = UILabel()
    private let nameLabel = UILabel()
    private let scoreLabel = UILabel()
    private let diffLabel = UILabel()
    private let userAvatarImageView = UIImageView()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setupUI()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setupUI() {
        // Configure labels
        rankLabel.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        nameLabel.font = UIFont.systemFont(ofSize: 16)
        scoreLabel.font = UIFont.systemFont(ofSize: 16)
        diffLabel.font = UIFont.systemFont(ofSize: 14)
        diffLabel.textColor = .secondaryLabel

        // Configure user avatar
        userAvatarImageView.contentMode = .scaleAspectFit
        userAvatarImageView.clipsToBounds = true
        userAvatarImageView.layer.cornerRadius = 12 // 丸いアバター画像にする

        // Add subviews
        contentView.addSubview(rankLabel)
        contentView.addSubview(userAvatarImageView)
        contentView.addSubview(nameLabel)
        contentView.addSubview(scoreLabel)
        contentView.addSubview(diffLabel)

        // Configure constraints
        rankLabel.translatesAutoresizingMaskIntoConstraints = false
        userAvatarImageView.translatesAutoresizingMaskIntoConstraints = false
        nameLabel.translatesAutoresizingMaskIntoConstraints = false
        scoreLabel.translatesAutoresizingMaskIntoConstraints = false
        diffLabel.translatesAutoresizingMaskIntoConstraints = false

        NSLayoutConstraint.activate([
            rankLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            rankLabel.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            rankLabel.widthAnchor.constraint(equalToConstant: 40),

            userAvatarImageView.leadingAnchor.constraint(equalTo: rankLabel.trailingAnchor, constant: 8),
            userAvatarImageView.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            userAvatarImageView.widthAnchor.constraint(equalToConstant: 24),
            userAvatarImageView.heightAnchor.constraint(equalToConstant: 24),

            nameLabel.leadingAnchor.constraint(equalTo: userAvatarImageView.trailingAnchor, constant: 8),
            nameLabel.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            nameLabel.widthAnchor.constraint(equalTo: contentView.widthAnchor, multiplier: 0.25), // 0.35から0.25に減らす

            scoreLabel.leadingAnchor.constraint(equalTo: nameLabel.trailingAnchor, constant: 8),
            scoreLabel.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),

            diffLabel.leadingAnchor.constraint(equalTo: scoreLabel.trailingAnchor, constant: 8),
            diffLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            diffLabel.centerYAnchor.constraint(equalTo: contentView.centerYAnchor)
        ])
    }

    func configure(with score: LeaderBoardRepository.ScoreEntry, isCurrentUser: Bool, repository: LeaderBoardRepository) {
        rankLabel.text = "\(score.rank)"
        nameLabel.text = score.name
        scoreLabel.text = repository.formatTime(score.score)
        diffLabel.text = repository.formatTime(score.diff)

        // ユーザーアバターを表示
        if let photoURL = score.photoURL, let url = URL(string: photoURL) {
            // URLから画像を読み込む
            DispatchQueue.global().async {
                if let data = try? Data(contentsOf: url), let image = UIImage(data: data) {
                    DispatchQueue.main.async { [weak self] in
                        self?.userAvatarImageView.image = image
                    }
                } else {
                    // 画像の読み込みに失敗した場合はデフォルト画像を設定
                    DispatchQueue.main.async { [weak self] in
                        self?.userAvatarImageView.image = UIImage(systemName: "person.circle.fill")
                        self?.userAvatarImageView.tintColor = .systemBlue
                    }
                }
            }
        } else {
            // プロフィール画像がない場合はデフォルト画像を設定
            userAvatarImageView.image = UIImage(systemName: "person.circle.fill")
            userAvatarImageView.tintColor = .systemBlue
        }

        // Highlight current user's score
        if isCurrentUser {
            contentView.backgroundColor = UIColor.secondary.withAlphaComponent(0.2)
        } else {
            contentView.backgroundColor = UIColor.clear
        }
    }
}
