// swift-tools-version:5.9
// Host-side checks of the iOS online core (ios/Sources/Online) against the Android implementation:
// BIP-340 vectors, NIP-01 serialization, and complete games between this Swift code and the Java client
// through a local relay. Runs on macOS (CryptoKit, URLSession) and Linux (swift-crypto, plain ws:// client).
import PackageDescription
#if os(Linux)
let crypto:[Package.Dependency]=[.package(url:"https://github.com/apple/swift-crypto.git",from:"3.0.0")]
let cryptoProduct:[Target.Dependency]=[.product(name:"Crypto",package:"swift-crypto")]
#else
let crypto:[Package.Dependency]=[]
let cryptoProduct:[Target.Dependency]=[]
#endif
let package=Package(name:"OnlineHostTests",platforms:[.macOS(.v13)],dependencies:crypto,
  targets:[
    .target(name:"OnlineCore",dependencies:cryptoProduct,swiftSettings:[.unsafeFlags(["-swift-version","5","-enable-testing"])]),
    .executableTarget(name:"HostCheck",dependencies:["OnlineCore"],swiftSettings:[.unsafeFlags(["-swift-version","5"])])])
