signingConfigs {
    create("release") {
        val keystorePath =
            System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"

        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}

buildTypes {
    release {
        isCrunchPngs = false
        isMinifyEnabled = false

        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )

        signingConfig = signingConfigs.getByName("release")
    }

    debug {
        // Gunakan debug signing bawaan Android
    }
}
