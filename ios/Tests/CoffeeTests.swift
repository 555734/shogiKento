import XCTest
import StoreKit
import StoreKitTest
@testable import BeniShogi

final class CoffeeTests:XCTestCase {
    @MainActor func testStoreKitPurchaseRestoreRefundAndLevelGate()async throws {
        executionTimeAllowance=180
        let session=try SKTestSession(configurationFileNamed:"CoffeeSupport")
        session.disableDialogs=true;session.clearTransactions()
        defer{session.clearTransactions()}
        let defaults=UserDefaults(suiteName:UUID().uuidString)!,coffee=CoffeeStore(defaults:defaults,start:false)
        coffee.observeTransactions()
        await coffee.refresh();XCTAssertFalse(coffee.owned);XCTAssertEqual(coffee.effective(MatchOptions(minutes:3,humanSide:1,level:10)).level,9)
        await coffee.loadProduct();XCTAssertNotNil(coffee.product);XCTAssertEqual(coffee.product?.price,200);print("Coffee StoreKit: local product loaded")
        await coffee.buy();XCTAssertTrue(coffee.owned);XCTAssertEqual(coffee.effective(MatchOptions(minutes:3,humanSide:1,level:10)).level,10);print("Coffee StoreKit: purchase completed")
        let restored=CoffeeStore(defaults:defaults,start:false);await restored.refresh();XCTAssertTrue(restored.owned)
        await restored.restore();XCTAssertTrue(restored.owned);XCTAssertEqual(restored.message,"購入情報を復元しました。");print("Coffee StoreKit: explicit restore completed")
        let transaction=try XCTUnwrap(session.allTransactions().first)
        // SKTestSession waits for the app to handle the refund. Keep the main actor
        // free so the production Transaction.updates listener can finish it.
        try await Task.detached {try session.refundTransaction(identifier:transaction.identifier)}.value
        print("Coffee StoreKit: refund simulation completed")
        let deadline=Date().addingTimeInterval(10)
        while coffee.owned && Date()<deadline {try await Task.sleep(nanoseconds:100_000_000)}
        XCTAssertFalse(coffee.owned,"Verified refund update must revoke the purchase")
        await coffee.refresh();XCTAssertFalse(coffee.owned);XCTAssertEqual(coffee.effective(MatchOptions(minutes:3,humanSide:1,level:10)).level,9)
        let proof=XCTAttachment(string:"PASS local Apple StoreKit Test: ¥200 non-consumable, Product.purchase, verified entitlement, finish, currentEntitlements restart, explicit AppStore.sync restore, refund relock. No production charge or App Store product registration.");proof.name="coffee-storekit-result";proof.lifetime = .keepAlways;add(proof)
    }
    @MainActor func testOfferFrequencyAndPermanentHide()throws {
        let name=UUID().uuidString,defaults=UserDefaults(suiteName:name)!
        defer{defaults.removePersistentDomain(forName:name)}
        let first=CoffeeStore(defaults:defaults,start:false);first.foreground();XCTAssertFalse(first.offerPending);first.foreground();XCTAssertEqual(defaults.integer(forKey:"coffee_launches"),1)
        first.background(offerVisible:false);first.foreground();XCTAssertTrue(first.offerPending);XCTAssertEqual(defaults.integer(forKey:"coffee_launches"),2)
        first.hideOffer=true
        let restarted=CoffeeStore(defaults:defaults,start:false);restarted.foreground();XCTAssertFalse(restarted.offerPending);XCTAssertTrue(restarted.hideOffer)
        let count=defaults.integer(forKey:"coffee_launches");restarted.background(offerVisible:true);restarted.foreground();XCTAssertEqual(defaults.integer(forKey:"coffee_launches"),count)
    }
}
