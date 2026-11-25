# PICS AR Android

カメラ映像に高度化PICSの歩行信号を重ねる、Androidネイティブ版です。WebViewではなくCameraXとAndroid BLE Scannerを使用します。

## 機能

- 背面カメラを使ったAR HUD
- Manufacturer ID 0x01CE のBLEアドバタイズ受信
- PICS Type 2のS1 / S2、残り値、交差点IDを解析
- 横断許可／切替注意／横断停止／受信停止を状態から判定
- 5秒以上Type 2を受信しない場合は値を失効
- 実信号がなくても確認できるデモ送信

## ビルド

Android SDKの場所を local.properties の sdk.dir に設定して、次を実行します。

    ./gradlew test assembleDebug

生成物は app/build/outputs/apk/debug/app-debug.apk です。

## 権限

- カメラ
- 周辺デバイス（Android 12以降）
- 位置情報（Android 11以前のBLEスキャン要件）

受信専用です。信号機への送信・接続・制御は行いません。
