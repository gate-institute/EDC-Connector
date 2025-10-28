// These tasks take a lot of time and make the gradle build cumbersome
val tasksToSkip = listOf(
    "test",
    //"openapi",
    "javadoc",
    "autodoc",
    "checkstyle",
    "lint"
)

tasks.matching { it.name in tasksToSkip }
     .configureEach {
         onlyIf { false }
     }

subprojects {
    tasks.matching { it.name in tasksToSkip }
         .configureEach {
             onlyIf { false }
         }
}
