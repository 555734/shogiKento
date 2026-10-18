import XCTest
final class FlowTests:XCTestCase {
    override func setUp(){super.setUp();continueAfterFailure=false}
    func shot(_ name:String){let image=XCTAttachment(screenshot:XCUIApplication().screenshot());image.name=name;image.lifetime = .keepAlways;add(image)}
    func tap(_ id:String,_ app:XCUIApplication,timeout:Double=15){let button=app.buttons[id];XCTAssertTrue(button.waitForExistence(timeout:timeout),id);button.tap()}
    func testNativeHomeSettingsMatchAndQuestStudy()throws {
        let app=XCUIApplication();app.launch();tap("settings",app);XCTAssertTrue(app.sliders["volume"].exists);shot("02-settings")
        app.sliders["volume"].adjust(toNormalizedSliderPosition:0.37);let sound=app.switches["sound"],vibration=app.switches["vibration"]
        sound.tap();vibration.tap();let soundValue=sound.value as? String,vibrationValue=vibration.value as? String,volumeValue=app.sliders["volume"].value as? String;tap("閉じる",app)
        app.terminate();app.launch();tap("settings",app);XCTAssertEqual(app.switches["sound"].value as? String,soundValue);XCTAssertEqual(app.switches["vibration"].value as? String,vibrationValue);XCTAssertEqual(app.sliders["volume"].value as? String,volumeValue);tap("閉じる",app);shot("01-home")
        tap("mode_match",app);tap("start_match",app);tap("cell_6_0",app);XCTAssertEqual(app.buttons["cell_5_0"].value as? String,"移動可能");tap("cell_4_0",app);XCTAssertNotEqual(app.buttons["cell_6_0"].value as? String,"選択");tap("cell_6_0",app);tap("cell_5_0",app)
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
    }
}
