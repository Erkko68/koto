import buildlogic.apigen.ApiGenConfig
import buildlogic.apigen.CBindings
import buildlogic.apigen.CObjects
import buildlogic.apigen.KotlinBindings
import buildlogic.apigen.Naming
import buildlogic.apigen.Scalar
import buildlogic.apigen.TypeTables
import buildlogic.apigen.CppSources
import buildlogic.apigen.gradle.registerApiGenTasks
import buildlogic.emsdk.SetupEmsdkTask

// Everything the generator is told about miniaudio; buildlogic.apigen itself knows no library.
registerApiGenTasks(
    ApiGenConfig(
        name = "Miniaudio",
        sources = CppSources(
            includeDir = "miniaudio",
            astFilters = listOf("ma_"),
        ),
        c = CBindings(
            prefix = "Koto",
            helpers = "koto",
        ),
        naming = Naming(
            memberPrefix = "^pp?(?=[A-Z])",
            parameterPrefix = "^pp?(?=[A-Z])",
            // The pointers miniaudio writes one value through: a Kotlin function returns those.
            outParameter = "^p(Major|Minor|Revision|Format|Channels|SampleRate|Cursor|Length|Available|FramesRead|FramesWritten|FramesProcessed|Volume|GainDB|CursorInSeconds|LengthInSeconds)$",
            // Encoded audio in memory, which Kotlin passes as a ByteArray.
            byteCount = "^(dataSize|sizeInBytes)$",
        ),
        types = TypeTables(
            scalars = mapOf("ma_bool32" to Scalar("bool", "({}) ? MA_TRUE : MA_FALSE", "({}) != MA_FALSE")),
        ),
        kotlin = KotlinBindings(
            packages = mapOf("koto" to "io.github.erkko68.koto.capi"),
            interop = "io.github.erkko68.koto.interop",
            wasmRuntime = "koto",
            wasmPackage = "io.github.erkko68.koto.wasm",
            objects = CObjects(prefix = "ma_", status = "ma_result"),
        ),
    ),
)

tasks.register<SetupEmsdkTask>("setupEmsdk") {
    group = "build setup"
    description = "Installs the pinned Emscripten SDK (emsdkVersion in gradle.properties) into .emsdk/."
    emsdkVersion.set(providers.gradleProperty("emsdkVersion"))
    installDir.set(layout.projectDirectory.dir(".emsdk"))
}
