import XCTest
final class FlowTests:XCTestCase {
    override func setUp(){super.setUp();continueAfterFailure=false}
    func testCandidateArrowsAndBoardInput()throws {
        let app=XCUIApplication();app.launchArguments=["-coffee-test-suppress","-ads-fixture"];app.launch();tap("mode_study",app)
        // Other UI flows deliberately persist their study record. Seek using the
        // real replay controls so this test always starts at its initial position.
        for _ in 0..<3{if app.buttons["ply_0"].isHittable{break};edgeScroll(app)};tap("ply_0",app)
        for _ in 0..<4{if app.buttons["cell_6_0"].isHittable{break};edgeScroll(app,up:false)}
        let arrows=app.descendants(matching:.any)["candidate_arrows"]
        XCTAssertTrue(arrows.waitForExistence(timeout:25));XCTAssertEqual(arrows.value as? String,"3件");shot("candidate-arrows-initial")
        let before=app.buttons["cell_5_0"].frame;tap("cell_6_0",app);XCTAssertEqual(app.buttons["cell_6_0"].value as? String,"選択");tap("cell_5_0",app)
        XCTAssertTrue(arrows.waitForExistence(timeout:25));XCTAssertEqual(arrows.value as? String,"3件");let after=app.buttons["cell_5_0"].frame;XCTAssertEqual(after.width,before.width,accuracy:0.001);XCTAssertEqual(after.height,before.height,accuracy:0.001);shot("candidate-arrows-gote")
        tap("home",app);tap("mode_match",app);XCTAssertFalse(arrows.exists)
    }
    func testStoreScreenshots()throws {
        let app=XCUIApplication();app.launchArguments=["-coffee-test-suppress","-ads-fixture"];app.launch()
        XCTAssertTrue(app.buttons["mode_study"].waitForExistence(timeout:15));shot("store-01-home")
        tap("mode_study",app);tap("cell_6_4",app);shot("store-02-legal-moves")
        tap("cell_5_4",app);XCTAssertTrue(app.staticTexts["analysis_status"].waitForExistence(timeout:10))
        let candidate=app.descendants(matching:.any)["candidate_0"];for _ in 0..<3{if candidate.isHittable{break};edgeScroll(app)};XCTAssertTrue(candidate.waitForExistence(timeout:25));shot("store-03-analysis")
        tap("home",app);tap("mode_match",app);shot("store-04-match")
        if !app.buttons["match_settings"].isHittable{edgeScroll(app)}
        tap("match_settings",app);shot("store-05-match-settings")
    }
    /// A real online game on the simulator against the Android client (CI starts a local relay and NetplayBot).
    func testOnlineMatchAgainstAndroidBot()throws {
        let env=ProcessInfo.processInfo.environment
        guard let relay=env["ONLINE_RELAY"],!relay.isEmpty,let lobby=env["ONLINE_LOBBY"] else{throw XCTSkip("needs the CI local relay and Android bot")}
        let app=XCUIApplication();app.launchArguments=["-coffee-test-suppress","-ads-fixture","-online-reset-account"];app.launchEnvironment["ONLINE_RELAY"]=relay;app.launchEnvironment["ONLINE_LOBBY"]=lobby;app.launch()
        for _ in 0..<3{if app.buttons["mode_online"].isHittable{break};edgeScroll(app)};tap("mode_online",app)
        XCTAssertTrue(app.staticTexts["online_relays"].waitForExistence(timeout:10));expectation(for:NSPredicate(format:"label CONTAINS %@","接続 1/1"),evaluatedWith:app.staticTexts["online_relays"]);waitForExpectations(timeout:15);shot("20-online-lobby")
        // Account: a fresh device account starts at 1500 (provisional); renaming updates the card shown to opponents.
        tap("online_account",app);XCTAssertTrue(app.staticTexts["account_rating"].waitForExistence(timeout:5));XCTAssertEqual(app.staticTexts["account_rating"].label,"1500")
        tap("account_rename",app);// SwiftUI does not pass identifiers to text fields inside alerts; the rename alert has exactly one.
        let field=app.alerts.textFields.firstMatch;XCTAssertTrue(field.waitForExistence(timeout:5));field.tap()
        if let old=field.value as? String,!old.isEmpty{field.typeText(String(repeating:XCUIKeyboardKey.delete.rawValue,count:old.count))};field.typeText("iPhone棋士");app.alerts.buttons["変更"].tap()
        XCTAssertTrue(app.staticTexts["account_name"].waitForExistence(timeout:5));XCTAssertEqual(app.staticTexts["account_name"].label,"iPhone棋士");shot("23-online-account");tap("account_close",app)
        tap("online_quick",app);let status=app.staticTexts["online_status"],ply=app.staticTexts["online_ply"];XCTAssertTrue(status.waitForExistence(timeout:40),"matched with the Android bot")
        let sente=app.staticTexts["online_side"].label.contains("あなた先手")
        // The bot answers within about 0.3 s, so a ply count may already be one further when it is first read.
        func waitPly(_ n:Int){expectation(for:NSPredicate(format:"label IN %@",["\(n)手目","\(n+1)手目"]),evaluatedWith:ply);waitForExpectations(timeout:20)}
        if sente{expectation(for:NSPredicate(format:"label CONTAINS %@","あなたの手番"),evaluatedWith:status);waitForExpectations(timeout:15);tap("cell_6_2",app);tap("cell_5_2",app);waitPly(1);waitPly(2)}
        else{waitPly(1);tap("cell_2_6",app);tap("cell_3_6",app);waitPly(2);waitPly(3)}
        let opponent=app.staticTexts["online_opponent"];expectation(for:NSPredicate(format:"label CONTAINS %@","AndroidBot"),evaluatedWith:opponent);waitForExpectations(timeout:10);XCTAssertTrue(opponent.label.contains("レート戦"))
        shot("21-online-match")
        tap("online_resign",app);let confirm=app.alerts.buttons["投了"];XCTAssertTrue(confirm.waitForExistence(timeout:5));confirm.tap()
        expectation(for:NSPredicate(format:"label CONTAINS %@","負け・投了"),evaluatedWith:status);waitForExpectations(timeout:10);XCTAssertTrue(app.buttons["online_rematch"].exists)
        let change=app.staticTexts["online_rating_change"];XCTAssertTrue(change.waitForExistence(timeout:5));XCTAssertTrue(change.label.hasPrefix("レート 1500 → "),change.label);shot("22-online-result")
        tap("home",app);XCTAssertTrue(app.buttons["mode_study"].waitForExistence(timeout:10))
    }
    func shot(_ name:String){let image=XCTAttachment(screenshot:XCUIApplication().screenshot());image.name=name;image.lifetime = .keepAlways;add(image)}
    func tap(_ id:String,_ app:XCUIApplication,timeout:Double=15){let button=app.buttons[id];XCTAssertTrue(button.waitForExistence(timeout:timeout),id);button.tap()}
    func setOfferHidden(_ app:XCUIApplication){let control=app.switches["coffee_hide_offer"];if (control.value as? String) != "1"{control.coordinate(withNormalizedOffset:CGVector(dx:0.93,dy:0.5)).tap()};XCTAssertEqual(control.value as? String,"1")}
    func edgeScroll(_ app:XCUIApplication,up:Bool=true){let start=app.coordinate(withNormalizedOffset:CGVector(dx:0.01,dy:up ? 0.8:0.2)),end=app.coordinate(withNormalizedOffset:CGVector(dx:0.01,dy:up ? 0.2:0.8));start.press(forDuration:0.05,thenDragTo:end)}
    func testAdsStudyGeometryAndOfficialSDK()throws {
        let app=XCUIApplication();app.launchArguments=["-ads-fixture","-coffee-test-suppress"];app.launch();tap("mode_study",app)
        let square=app.buttons["cell_6_0"],initial=square.frame
        XCTAssertGreaterThan(initial.width,20);XCTAssertFalse(app.otherElements["ad_study"].isHittable);shot("11-ads-study-initial")
        for _ in 0..<4{if app.staticTexts["ad_status_study"].isHittable{break};edgeScroll(app)}
        shot("12-ads-study-scrolled");XCTAssertTrue(app.staticTexts["ad_status_study"].isHittable);XCTAssertEqual(app.staticTexts["ad_status_study"].value as? String,"fixture")
        for _ in 0..<5{edgeScroll(app,up:false)}
        XCTAssertEqual(square.frame.width,initial.width,accuracy:0.1);XCTAssertEqual(square.frame.height,initial.height,accuracy:0.1);XCTAssertEqual(square.frame.minY,initial.minY,accuracy:0.1)
        tap("cell_6_0",app);tap("cell_5_0",app);XCTAssertEqual(app.staticTexts["study_ply"].label,"1手目")
        tap("home",app);for _ in 0..<4{if app.staticTexts["ad_status_home"].isHittable{break};edgeScroll(app)};XCTAssertTrue(app.staticTexts["ad_status_home"].isHittable);shot("13-ads-home")
        app.terminate();app.launchArguments=["-ads-fixture","-ads-failure","-coffee-test-suppress"];app.launch();tap("mode_study",app)
        XCTAssertEqual(app.buttons["cell_6_0"].frame,initial);for _ in 0..<4{if app.staticTexts["ad_status_study"].isHittable{break};edgeScroll(app)};XCTAssertEqual(app.staticTexts["ad_status_study"].value as? String,"failed");shot("15-ads-failure")
        app.terminate();app.launchArguments=["-coffee-test-suppress"];app.launch()
        for _ in 0..<4{if app.staticTexts["ad_status_home"].isHittable{break};edgeScroll(app)}
        let status=app.staticTexts["ad_status_home"];XCTAssertTrue(status.waitForExistence(timeout:5))
        let completed=NSPredicate(format:"value == %@ OR value == %@","loaded","failed");expectation(for:completed,evaluatedWith:status);waitForExpectations(timeout:65);shot("14-ads-official-sdk")
        let evidence=XCTAttachment(string:"official_sdk_state=\(status.value as? String ?? "unknown")\nmessage=\(status.label)\nGoogle sample ID only; no production ads.");evidence.name="official-test-ad-result";evidence.lifetime = .keepAlways;add(evidence)
    }
    func testCoffeeOpeningPromptAndPermanentHide()throws {
        let app=XCUIApplication();app.launchArguments=["-ads-fixture","-coffee-fixture","-coffee-reset-offer"];app.launch();XCTAssertFalse(app.buttons["coffee_buy"].waitForExistence(timeout:3))
        app.terminate();app.launchArguments=["-ads-fixture","-coffee-fixture"];app.launch();XCTAssertTrue(app.buttons["coffee_buy"].waitForExistence(timeout:15));shot("19-coffee-second-opening")
        setOfferHidden(app);tap("coffee_close",app);app.terminate();app.launch();XCTAssertFalse(app.buttons["coffee_buy"].waitForExistence(timeout:3))
        tap("settings",app);if !app.buttons["coffee_settings"].isHittable{app.swipeUp()};tap("coffee_settings",app);XCTAssertTrue(app.buttons["coffee_restore"].exists);tap("coffee_close",app);tap("settings_close",app)
    }
    func testCoffeeSettingsBuyRestoreAndHide()throws {
        let app=XCUIApplication();app.launchArguments=["-ads-fixture","-coffee-fixture","-coffee-test-suppress"];app.launch()
        tap("settings",app);if !app.buttons["coffee_settings"].isHittable{app.swipeUp()};tap("coffee_settings",app);XCTAssertTrue(app.buttons["coffee_buy"].isEnabled);XCTAssertTrue(app.buttons["coffee_buy"].label.contains("200"));shot("16-coffee-offer")
        setOfferHidden(app)
        tap("coffee_buy",app);XCTAssertTrue(app.staticTexts["coffee_status"].label.contains("購入済み"));shot("17-coffee-purchased");tap("coffee_close",app);tap("settings_close",app)
        tap("mode_study",app);for _ in 0..<3{edgeScroll(app)};XCTAssertFalse(app.staticTexts["ad_status_study"].exists)
        tap("home",app);app.terminate();app.launch();tap("settings",app);if !app.buttons["coffee_settings"].isHittable{app.swipeUp()};tap("coffee_settings",app);XCTAssertEqual(app.switches["coffee_hide_offer"].value as? String,"1")
        tap("coffee_restore",app);XCTAssertTrue(app.staticTexts["coffee_status"].label.contains("購入済み"));shot("18-coffee-restored");tap("coffee_close",app);tap("settings_close",app)
        tap("mode_match",app);if !app.buttons["match_settings"].isHittable{edgeScroll(app)};tap("match_settings",app);let increment=app.buttons["match_level-Increment"];for _ in 0..<10{if !increment.isEnabled{break};increment.tap()};XCTAssertEqual(app.buttons["apply_match_settings"].label,"設定を適用")
    }
    func testNativeHomeSettingsMatchAndQuestStudy()throws {
        let app=XCUIApplication();app.launchArguments=["-ads-fixture","-coffee-test-suppress","-coffee-test-owned"];app.launch();tap("settings",app);XCTAssertTrue(app.sliders["volume"].exists);shot("02-settings")
        app.sliders["volume"].adjust(toNormalizedSliderPosition:0.37);let sound=app.switches["sound"],vibration=app.switches["vibration"]
        sound.tap();vibration.tap();let soundValue=sound.value as? String,vibrationValue=vibration.value as? String,volumeValue=app.sliders["volume"].value as? String;tap("閉じる",app)
        app.terminate();app.launch();tap("settings",app);XCTAssertEqual(app.switches["sound"].value as? String,soundValue);XCTAssertEqual(app.switches["vibration"].value as? String,vibrationValue);XCTAssertEqual(app.sliders["volume"].value as? String,volumeValue);tap("閉じる",app);shot("01-home")
        tap("mode_match",app);tap("start_match",app);tap("cell_6_0",app);XCTAssertEqual(app.buttons["cell_5_0"].value as? String,"移動可能");tap("cell_4_0",app);XCTAssertNotEqual(app.buttons["cell_6_0"].value as? String,"選択");tap("cell_6_0",app);tap("cell_6_1",app);XCTAssertEqual(app.buttons["cell_6_1"].value as? String,"選択");XCTAssertNotEqual(app.buttons["cell_6_0"].value as? String,"選択");XCTAssertEqual(app.buttons["cell_5_1"].value as? String,"移動可能");tap("cell_6_0",app);tap("cell_5_0",app)
        let two=app.staticTexts["match_ply"];let played=NSPredicate(format:"label == %@","2手目");expectation(for:played,evaluatedWith:two);waitForExpectations(timeout:25);shot("03-match-real-cpu")
        tap("投了",app);app.alerts.buttons["投了"].tap();tap("home",app);tap("open_archive",app);XCTAssertTrue(app.buttons.matching(NSPredicate(format:"label CONTAINS %@","投了")).firstMatch.waitForExistence(timeout:5));shot("04-archive-match");tap("home",app)
        // The workflow sets the real simulator pasteboard before this test.
        tap("open_paste",app);tap("system_paste",app);let editor=app.textViews["kif_text"];XCTAssertTrue(editor.waitForExistence(timeout:5));XCTAssertTrue((editor.value as? String ?? "").contains("８三金打"));tap("load_kif",app)
        XCTAssertTrue(app.alerts.buttons["検討する"].waitForExistence(timeout:15));app.alerts.buttons["キャンセル"].tap();XCTAssertTrue(app.buttons["mode_study"].exists)
        tap("open_paste",app);tap("system_paste",app);tap("load_kif",app);XCTAssertTrue(app.alerts.buttons["検討する"].waitForExistence(timeout:15));app.alerts.buttons["検討する"].tap()
        XCTAssertTrue(app.staticTexts["study_ply"].waitForExistence(timeout:15));XCTAssertEqual(app.staticTexts["study_ply"].label,"79手目");XCTAssertTrue(app.staticTexts["analysis_status"].label.contains("詰み"));shot("05-quest-final")
        // The analysis panel is below the board on compact phones.
        if !app.buttons["ply_1"].isHittable{app.swipeUp()};tap("ply_1",app);XCTAssertEqual(app.staticTexts["study_ply"].label,"78手目");XCTAssertTrue(app.descendants(matching:.any)["candidate_0"].waitForExistence(timeout:25));shot("06-quest-native-analysis")
        tap("ply_0",app);XCTAssertEqual(app.staticTexts["study_ply"].label,"0手目");tap("ply_3",app);XCTAssertEqual(app.staticTexts["study_ply"].label,"79手目");tap("home",app)
        app.terminate();app.launch();tap("open_archive",app);let quest=app.buttons.matching(NSPredicate(format:"label CONTAINS %@","Shogi Quest")).firstMatch;XCTAssertTrue(quest.waitForExistence(timeout:10));shot("07-archive-persisted");quest.tap();if app.alerts.buttons["開く"].waitForExistence(timeout:3){app.alerts.buttons["開く"].tap()};XCTAssertEqual(app.staticTexts["study_ply"].label,"79手目");shot("08-reopened-quest")
        tap("home",app);tap("mode_match",app)
        if !app.buttons["match_settings"].isHittable{app.swipeUp()};tap("match_settings",app)
        app.buttons["match_level-Increment"].tap();app.buttons["match_level-Increment"].tap()
        tap("match_minutes",app);tap("5分",app)
        app.segmentedControls["match_side"].buttons.element(boundBy:1).tap();shot("09-match-settings")
        tap("apply_match_settings",app)
        XCTAssertTrue(app.buttons["match_settings"].label.contains("5分"));XCTAssertTrue(app.buttons["match_settings"].label.contains("Lv.10"));XCTAssertTrue(app.buttons["match_settings"].label.contains("後手"))
        if !app.buttons["start_match"].isHittable{app.swipeDown()};tap("start_match",app)
        let one=NSPredicate(format:"label == %@","1手目");expectation(for:one,evaluatedWith:app.staticTexts["match_ply"]);waitForExpectations(timeout:25)
        tap("cell_2_0",app);tap("cell_3_0",app)
        let three=NSPredicate(format:"label == %@","3手目");expectation(for:three,evaluatedWith:app.staticTexts["match_ply"]);waitForExpectations(timeout:25);shot("10-human-gote-real-cpu")
        tap("home",app);app.terminate();app.launch();tap("mode_match",app)
        XCTAssertTrue(app.buttons["match_settings"].label.contains("5分"));XCTAssertTrue(app.buttons["match_settings"].label.contains("Lv.10"));XCTAssertTrue(app.buttons["match_settings"].label.contains("後手"))

    }
}
