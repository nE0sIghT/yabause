package org.uoyabause.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LeaderBoardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: 後でレイアウトを作成し setContentView する
        // とりあえずタイトルのみ表示
        title = "リーダーボード"
    }
}
