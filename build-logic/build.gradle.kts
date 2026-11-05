plugins {
    java
}

group = "com.xpdustry"

sourceSets.main {
    java {
        srcDir("../src/main/java")
        include("com/xpdustry/nohorny/natives/NoHornyNativeLookup.java")
    }
}
