import Foundation
import FirebaseFirestore

/// リーダーボードの情報を保持するクラス
class LeaderBoard {
    let title: String
    let id: String

    init(title: String, id: String) {
        self.title = title
        self.id = id
    }
}

/// ゲームUIイベントを処理するプロトコル
protocol GameUiEvent {
    func onNewRecord(leaderBoardId: String)
}

/// ゲームの基本クラス
class BaseGame {

    var leaderBoards: [LeaderBoard]?

    var uiEvent: GameUiEvent?

    func setUiEvent(_ uiEvent: GameUiEvent) {
        self.uiEvent = uiEvent
    }

    /// バックアップデータが更新されたときに呼ばれるメソッド
    /// - Parameters:
    ///   - fname: ファイル名
    ///   - before: 更新前のバイナリデータ
    ///   - after: 更新後のバイナリデータ
    func onBackUpUpdated(fname: String, before: Data, after: Data) {
        // サブクラスでオーバーライドする
    }
}
